package com.betterclanbroadcasts;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Point;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

// shared row-matching helpers for the clan player list widget, used by both the note/flag
// overlay and the right-click menu resolver so the grouping/matching logic only lives once
final class ClanPlayerListRows
{
	private static final int ROW_HEIGHT = 15;

	private ClanPlayerListRows()
	{
	}

	static Map<Integer, List<Widget>> groupByRow(Widget[] children)
	{
		Map<Integer, List<Widget>> rowsByIndex = new HashMap<>();
		for (Widget child : children)
		{
			if (child == null || child.isHidden())
			{
				continue;
			}

			int rowIndex = Math.round(child.getOriginalY() / (float) ROW_HEIGHT);
			rowsByIndex.computeIfAbsent(rowIndex, k -> new ArrayList<>()).add(child);
		}

		return rowsByIndex;
	}

	static Map<String, ClanChannelMember> mapMembersByName(ClanChannel clanChannel)
	{
		Map<String, ClanChannelMember> membersByName = new HashMap<>();
		for (ClanChannelMember member : clanChannel.getMembers())
		{
			membersByName.put(normalize(member.getName()), member);
		}

		return membersByName;
	}

	static ClanChannelMember findMember(List<Widget> rowWidgets, Map<String, ClanChannelMember> membersByName)
	{
		for (Widget widget : rowWidgets)
		{
			ClanChannelMember member = membersByName.get(normalize(widget.getText()));
			if (member != null)
			{
				return member;
			}

			member = membersByName.get(normalize(widget.getName()));
			if (member != null)
			{
				return member;
			}
		}

		return null;
	}

	static boolean isRowAtPoint(List<Widget> rowWidgets, Point point)
	{
		for (Widget widget : rowWidgets)
		{
			Rectangle bounds = widget.getBounds();
			if (bounds != null && bounds.contains(point.getX(), point.getY()))
			{
				return true;
			}
		}

		return false;
	}

	static String normalize(String name)
	{
		return name == null ? "" : Text.toJagexName(Text.removeTags(name)).toLowerCase();
	}
}