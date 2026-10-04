package com.clanqol.clanpanel;

import com.clanqol.ClanQolConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClanPlayerListSorter
{
	private static final int CLAN_ROW_HEIGHT = 15;
	private static final String SORT_MODE_CONFIG_KEY = "sortMode";

	private final Client client;
	private final ConfigManager configManager;

	private final Map<Widget, Integer> nativeWidgetYs = new IdentityHashMap<>();

	public enum SortMode
	{
		NONE,
		WORLD_ASCENDING,
		WORLD_DESCENDING,
		NAME_ASCENDING,
		NAME_DESCENDING,
		RANK_THEN_SPRITE_ASCENDING,
		RANK_THEN_SPRITE_DESCENDING
	}

	private SortMode sortMode;

	public ClanPlayerListSorter(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
		this.sortMode = loadSortMode();
	}

	public void setAscending()
	{
		sortMode = SortMode.WORLD_ASCENDING;
		persistSortMode();
	}

	public void setDescending()
	{
		sortMode = SortMode.WORLD_DESCENDING;
		persistSortMode();
	}

	public void setNameAscending()
	{
		sortMode = SortMode.NAME_ASCENDING;
		persistSortMode();
	}

	public void setNameDescending()
	{
		sortMode = SortMode.NAME_DESCENDING;
		persistSortMode();
	}

	public void setRankThenSpriteAscending()
	{
		sortMode = SortMode.RANK_THEN_SPRITE_ASCENDING;
		persistSortMode();
	}

	public void setRankThenSpriteDescending()
	{
		sortMode = SortMode.RANK_THEN_SPRITE_DESCENDING;
		persistSortMode();
	}

	public void clear()
	{
		sortMode = SortMode.NONE;
		persistSortMode();
		restore();
	}

	public boolean isActive()
	{
		return sortMode != SortMode.NONE;
	}

	public SortMode getSortMode()
	{
		return sortMode;
	}

	// call from onGameTick. list gets rebuilt by the client on its own so we
	// gotta keep re-sorting every tick to keep it stable
	public void onGameTick()
	{
		if (sortMode != SortMode.NONE)
		{
			sort();
		}
	}

	// puts widgets back to their native positions, call from shutDown
	public void reset()
	{
		restore();
	}

	private SortMode loadSortMode()
	{
		String saved = configManager.getConfiguration(ClanQolConfig.CONFIG_GROUP, SORT_MODE_CONFIG_KEY);
		if (saved == null)
		{
			return SortMode.NAME_ASCENDING;
		}

		try
		{
			return SortMode.valueOf(saved);
		}
		catch (IllegalArgumentException e)
		{
			// saved value doesnt match a current enum constant, e.g. after a
			// plugin update removed/renamed a mode. fall back to the default
			// sort rather than crash
			return SortMode.NAME_ASCENDING;
		}
	}

	private void persistSortMode()
	{
		configManager.setConfiguration(ClanQolConfig.CONFIG_GROUP, SORT_MODE_CONFIG_KEY, sortMode.name());
	}

	private void sort()
	{
		ClanChannel clanChannel = client.getClanChannel();
		Widget playerList = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (clanChannel == null || playerList == null)
		{
			return;
		}

		Widget[] children = playerList.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		Map<String, ClanChannelMember> membersByName = new HashMap<>();
		for (ClanChannelMember member : clanChannel.getMembers())
		{
			membersByName.put(normalizeName(member.getName()), member);
		}

		// group by row index instead of exact y match. the rank icon widget's y
		// can be off by 1px from the name/world widgets in the same row, so an
		// exact match splits it into its own row with nothing to identify a
		// member, gets dropped, and stays behind when the rest of the row moves
		Map<Integer, ClanPlayerRow> rowsByIndex = new LinkedHashMap<>();
		Map<Widget, Integer> currentNativeWidgetYs = new IdentityHashMap<>();
		for (Widget child : children)
		{
			if (child == null || child.isHidden())
			{
				continue;
			}

			currentNativeWidgetYs.put(child,
					nativeWidgetYs.getOrDefault(child, child.getOriginalY()));
			int rowIndex = Math.round(child.getOriginalY() / (float) CLAN_ROW_HEIGHT);
			rowsByIndex.computeIfAbsent(rowIndex, ClanPlayerRow::new)
					.widgets.add(child);
		}

		nativeWidgetYs.clear();
		nativeWidgetYs.putAll(currentNativeWidgetYs);

		List<ClanPlayerRow> rows = new ArrayList<>();
		for (ClanPlayerRow row : rowsByIndex.values())
		{
			row.member = findMember(row, membersByName);
			if (row.member != null)
			{
				rows.add(row);
			}
		}

		switch (sortMode)
		{
			case WORLD_ASCENDING:
				rows.sort(Comparator
						.comparingInt((ClanPlayerRow row) -> row.member.getWorld())
						.thenComparing(row -> row.member.getName(), String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(row -> row.rowIndex));
				break;
			case WORLD_DESCENDING:
				rows.sort(Comparator
						.comparingInt((ClanPlayerRow row) -> row.member.getWorld()).reversed()
						.thenComparing(row -> row.member.getName(), String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(row -> row.rowIndex));
				break;
			case NAME_ASCENDING:
				rows.sort(Comparator
						.comparing((ClanPlayerRow row) -> row.member.getName(), String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(row -> row.member.getWorld())
						.thenComparingInt(row -> row.rowIndex));
				break;
			case NAME_DESCENDING:
				rows.sort(Comparator
						.comparing((ClanPlayerRow row) -> row.member.getName(), String.CASE_INSENSITIVE_ORDER).reversed()
						.thenComparingInt(row -> row.member.getWorld())
						.thenComparingInt(row -> row.rowIndex));
				break;
			case RANK_THEN_SPRITE_ASCENDING:
				rows.sort(buildRankThenSpriteComparator());
				break;
			case RANK_THEN_SPRITE_DESCENDING:
				rows.sort(buildRankThenSpriteComparator().reversed());
				break;
			case NONE:
			default:
				break;
		}

		for (int index = 0; index < rows.size(); index++)
		{
			int newY = index * CLAN_ROW_HEIGHT;
			for (Widget widget : rows.get(index).widgets)
			{
				widget.setOriginalY(newY);
				widget.revalidate();
			}
		}
	}

	private Comparator<ClanPlayerRow> buildRankThenSpriteComparator()
	{
		// rank is -1 (guest) to 127 (jmod), reversed so highest rank comes first as the base order
		return Comparator
				.comparingInt((ClanPlayerRow row) -> row.member.getRank().getRank()).reversed()
				.thenComparingInt(this::findSpriteId)
				.thenComparing(row -> row.member.getName(), String.CASE_INSENSITIVE_ORDER)
				.thenComparingInt(row -> row.rowIndex);
	}

	private int findSpriteId(ClanPlayerRow row)
	{
		for (Widget widget : row.widgets)
		{
			if (widget.getSpriteId() > -1)
			{
				return widget.getSpriteId();
			}
		}

		// no icon widget in this row, probably unranked/guest. sort it last
		// instead of throwing off the whole comparator
		return Integer.MAX_VALUE;
	}

	private ClanChannelMember findMember(ClanPlayerRow row,
										 Map<String, ClanChannelMember> membersByName)
	{
		for (Widget widget : row.widgets)
		{
			// some widgets carry the rsn in getName() with a col tag instead of
			// getText(), which can be blank. check both so we dont silently drop
			// the whole row
			ClanChannelMember member = membersByName.get(normalizeName(widget.getText()));
			if (member != null)
			{
				return member;
			}

			member = membersByName.get(normalizeName(widget.getName()));
			if (member != null)
			{
				return member;
			}
		}

		return null;
	}

	private static String normalizeName(String name)
	{
		return name == null ? "" : Text.toJagexName(Text.removeTags(name)).toLowerCase();
	}

	private void restore()
	{
		for (Map.Entry<Widget, Integer> entry : nativeWidgetYs.entrySet())
		{
			Widget widget = entry.getKey();
			widget.setOriginalY(entry.getValue());
			widget.revalidate();
		}
		nativeWidgetYs.clear();
	}

	private static class ClanPlayerRow
	{
		private final int rowIndex;
		private final List<Widget> widgets = new ArrayList<>();
		private ClanChannelMember member;

		private ClanPlayerRow(int rowIndex)
		{
			this.rowIndex = rowIndex;
		}
	}
}