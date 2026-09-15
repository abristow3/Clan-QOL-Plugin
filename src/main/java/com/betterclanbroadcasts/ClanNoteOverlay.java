package com.betterclanbroadcasts;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
class ClanNoteOverlay extends Overlay
{
	private static final int NOTE_ICON_WIDTH = 14;
	private static final int NOTE_ICON_HEIGHT = 12;

	private static final int NOTE_ICON_X = 103;
	private static final int NOTE_ICON_Y_OFFSET = 1;

	private static final int FLAG_ICON_WIDTH = 16;
	private static final int FLAG_ICON_GAP = 2;
	private final Client client;
	private final BetterClanBroadcastsConfig config;
	private final ConfigManager configManager;
	private final TooltipManager tooltipManager;

	private BufferedImage noteIcon;
	private int lastLoggedTick = -1;

	@Inject
	private ClanNoteOverlay(Client client, BetterClanBroadcastsConfig config, ConfigManager configManager,
							TooltipManager tooltipManager)
	{
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.tooltipManager = tooltipManager;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/noteicon.png");
		if (icon == null)
		{
			log.warn("noteicon.png not found, note icons will not be drawn");
		}
		else
		{
			noteIcon = ImageUtil.resizeImage(icon, NOTE_ICON_WIDTH, NOTE_ICON_HEIGHT);
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ClanChannel clanChannel = client.getClanChannel();
		Widget playerList = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (clanChannel == null || playerList == null)
		{
			return null;
		}

		Widget[] children = playerList.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return null;
		}

		Map<String, ClanChannelMember> membersByName = ClanPlayerListRows.mapMembersByName(clanChannel);
		Map<Integer, List<Widget>> rowsByIndex = ClanPlayerListRows.groupByRow(children);

		Point playerListCanvasLocation = playerList.getCanvasLocation();
		Rectangle playerListBounds = playerList.getBounds();
		boolean menuOpen = client.isMenuOpen();
		Point mouse = client.getMouseCanvasPosition();

        Rectangle originalClip = graphics.getClipBounds();
        if (playerListBounds != null)
        {
            graphics.setClip(playerListBounds);
        }

		for (Map.Entry<Integer, List<Widget>> entry : rowsByIndex.entrySet())
		{
			List<Widget> rowWidgets = entry.getValue();
			ClanChannelMember member = ClanPlayerListRows.findMember(rowWidgets, membersByName);
			if (member == null)
			{
				continue;
			}

			String displayName = Text.toJagexName(Text.removeTags(member.getName()));
			String note = configManager.getConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, "note_" + displayName);
            String flagCode = configManager.getConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, "flag_" + displayName);
            BufferedImage flagImage = flagCode != null ? ClanCountryFlags.getFlag(flagCode) : null;

            if (note == null && flagImage == null)
            {
                continue;
            }

            Point anchorCanvasLocation = rowWidgets.get(0).getCanvasLocation();

            if (anchorCanvasLocation != null && playerListCanvasLocation != null)
            {
                if (note != null && config.showIcons() && noteIcon != null)
                {
                    int noteX = playerListCanvasLocation.getX() + NOTE_ICON_X;
                    int noteY = anchorCanvasLocation.getY() + NOTE_ICON_Y_OFFSET;
                    graphics.drawImage(noteIcon, noteX, noteY, null);
                }

                if (flagImage != null && config.showFlagIcons())
                {
                    int flagX = playerListCanvasLocation.getX() + NOTE_ICON_X - FLAG_ICON_WIDTH - FLAG_ICON_GAP;
                    int flagY = anchorCanvasLocation.getY() + NOTE_ICON_Y_OFFSET;
                    graphics.drawImage(flagImage, flagX, flagY, null);
                }
            }

			if (!menuOpen && config.showNoteTooltip() && ClanPlayerListRows.isRowAtPoint(rowWidgets, mouse))
            {
                String tooltipText = flagCode != null && note != null
                        ? flagCode + " - " + note
                        : flagCode != null ? flagCode : note;
                tooltipManager.add(new Tooltip(tooltipText));
            }
        }

        graphics.setClip(originalClip);

		return null;
	}
}