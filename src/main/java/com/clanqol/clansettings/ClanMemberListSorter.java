package com.clanqol.clanpanel;

import com.clanqol.ClanQolConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

// sorts the clan settings > members list management interface. uses ClanSettings since this list
// has everyone, not just whos online
@Slf4j
public class ClanMemberListSorter
{
	private static final int[] ROW_LAYERS = {
			InterfaceID.ClansMembers.NAME,
			InterfaceID.ClansMembers.COLUMN1,
			InterfaceID.ClansMembers.COLUMN2,
	};

	private static final int ROW_HEIGHT = 25;
	private static final String SORT_MODE_CONFIG_KEY = "memberListSortMode";

	public enum SortMode
	{
		NONE,
		NAME_DESCENDING,
		RANK_ASCENDING,
		RANK_DESCENDING
	}

	private final Client client;
	private final ConfigManager configManager;
	private final Map<Widget, WidgetState> states = new IdentityHashMap<>();

	private SortMode sortMode;

	private static final class WidgetState
	{
		int nativeY;
		int appliedY;
	}

	private static final class Row
	{
		final int index;
		final List<Widget> widgets = new ArrayList<>();
		ClanMember member;

		Row(int index)
		{
			this.index = index;
		}
	}

	public ClanMemberListSorter(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
		this.sortMode = loadSortMode();
	}

	public SortMode getSortMode()
	{
		return sortMode;
	}

	public void setSortMode(SortMode mode)
	{
		sortMode = mode;
		configManager.setConfiguration(ClanQolConfig.CONFIG_GROUP, SORT_MODE_CONFIG_KEY, mode.name());
	}

	// call from onClientTick. client redraws the list on its own so we gotta
	// keep reapplying, otherwise it flashes unsorted
	public void onClientTick()
	{
		if (client.getWidget(InterfaceID.ClansMembers.UNIVERSE) == null)
		{
			states.clear();
			return;
		}

		apply(sortMode);
	}

	// call from shutDown on the client thread
	public void reset()
	{
		if (client.getWidget(InterfaceID.ClansMembers.UNIVERSE) != null)
		{
			apply(SortMode.NONE);
		}
		states.clear();
	}

	private SortMode loadSortMode()
	{
		String saved = configManager.getConfiguration(ClanQolConfig.CONFIG_GROUP, SORT_MODE_CONFIG_KEY);
		if (saved == null)
		{
			return SortMode.NONE;
		}

		try
		{
			return SortMode.valueOf(saved);
		}
		catch (IllegalArgumentException e)
		{
			// old/renamed mode, just fall back to native
			return SortMode.NONE;
		}
	}

	private void apply(SortMode mode)
	{
		// if a widget is new or its y isnt what we set, the client redrew it
		// so treat its current y as native
		Map<Widget, WidgetState> current = new IdentityHashMap<>();
		for (Widget widget : collectRowWidgets())
		{
			WidgetState state = states.get(widget);
			if (state == null || widget.getOriginalY() != state.appliedY)
			{
				state = new WidgetState();
				state.nativeY = state.appliedY = widget.getOriginalY();
			}
			current.put(widget, state);
		}

		states.clear();
		states.putAll(current);
		if (states.isEmpty())
		{
			return;
		}

		TreeMap<Integer, Row> rowsByIndex = new TreeMap<>();
		for (Map.Entry<Widget, WidgetState> entry : states.entrySet())
		{
			int rowIndex = Math.round(entry.getValue().nativeY / (float) ROW_HEIGHT);
			rowsByIndex.computeIfAbsent(rowIndex, Row::new).widgets.add(entry.getKey());
		}

		List<Row> nativeOrder = new ArrayList<>(rowsByIndex.values());
		List<Row> sorted = new ArrayList<>(nativeOrder);

		ClanSettings settings = client.getClanSettings();
		if (mode != SortMode.NONE && settings != null)
		{
			Map<String, ClanMember> members = mapMembers(settings);
			for (Row row : sorted)
			{
				row.member = findMember(row.widgets, members);
			}
			sorted.sort(comparatorFor(mode));
		}

		// sorted rows take over the native row slots in order
		for (int i = 0; i < sorted.size(); i++)
		{
			Row row = sorted.get(i);
			int slotTop = nativeOrder.get(i).index * ROW_HEIGHT;
			int nativeTop = row.index * ROW_HEIGHT;

			for (Widget widget : row.widgets)
			{
				WidgetState state = states.get(widget);
				int targetY = slotTop + (state.nativeY - nativeTop);
				if (widget.getOriginalY() != targetY)
				{
					widget.setOriginalY(targetY);
					widget.revalidate();
				}
				state.appliedY = targetY;
			}
		}
	}

	private static Comparator<Row> comparatorFor(SortMode mode)
	{
		Comparator<Row> unmatchedLast = Comparator.comparing(row -> row.member == null);
		Comparator<Row> byName = Comparator.comparing(
				row -> row.member == null ? "" : row.member.getName(), String.CASE_INSENSITIVE_ORDER);
		Comparator<Row> byRank = Comparator.comparingInt(
				row -> row.member == null ? Integer.MIN_VALUE : row.member.getRank().getRank());
		Comparator<Row> byNativeIndex = Comparator.comparingInt(row -> row.index);

		switch (mode)
		{
			case NAME_DESCENDING:
				return unmatchedLast.thenComparing(byName.reversed()).thenComparing(byNativeIndex);
			case RANK_ASCENDING:
				// highest rank first, same as the sidepanel sorter
				return unmatchedLast.thenComparing(byRank.reversed()).thenComparing(byName).thenComparing(byNativeIndex);
			case RANK_DESCENDING:
				return unmatchedLast.thenComparing(byRank).thenComparing(byName).thenComparing(byNativeIndex);
			case NONE:
			default:
				return byNativeIndex;
		}
	}

	private List<Widget> collectRowWidgets()
	{
		List<Widget> widgets = new ArrayList<>();
		for (int layerId : ROW_LAYERS)
		{
			Widget layer = client.getWidget(layerId);
			Widget[] children = layer == null ? null : layer.getDynamicChildren();
			if (children == null)
			{
				continue;
			}

			for (Widget child : children)
			{
				if (child != null && !child.isSelfHidden())
				{
					widgets.add(child);
				}
			}
		}
		return widgets;
	}

	private static Map<String, ClanMember> mapMembers(ClanSettings settings)
	{
		Map<String, ClanMember> members = new HashMap<>();
		for (ClanMember member : settings.getMembers())
		{
			if (member != null && member.getName() != null)
			{
				members.put(normalize(member.getName()), member);
			}
		}
		return members;
	}

	private static ClanMember findMember(List<Widget> rowWidgets, Map<String, ClanMember> members)
	{
		for (Widget widget : rowWidgets)
		{
			ClanMember member = members.get(normalize(widget.getText()));
			if (member == null)
			{
				member = members.get(normalize(widget.getName()));
			}
			if (member != null)
			{
				return member;
			}
		}
		return null;
	}

	private static String normalize(String name)
	{
		return name == null ? "" : Text.toJagexName(Text.removeTags(name)).toLowerCase();
	}
}