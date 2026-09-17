package com.betterclanbroadcasts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
	private static final int NOTE_ICON_WIDTH = 12;
	private static final int NOTE_ICON_HEIGHT = 12;

    private static final int NOTE_ICON_X = 104;

    private static final int FLAG_ICON_WIDTH = 13;
    private static final int FLAG_ICON_HEIGHT = 12;
    private static final int FLAG_ICON_GAP = 4;
    private static final int TIME_TEXT_X = 116;
    private static final float TIME_FONT_SIZE = 14f;
    private static final int ROW_HEIGHT = 15;
	private final Client client;
	private final BetterClanBroadcastsConfig config;
	private final ConfigManager configManager;
	private final TooltipManager tooltipManager;
    private final ClanDisplayModeState displayModeState;

	private BufferedImage noteIcon;
	private int lastLoggedTick = -1;

	@Inject
	private ClanNoteOverlay(Client client, BetterClanBroadcastsConfig config, ConfigManager configManager,
							TooltipManager tooltipManager, ClanDisplayModeState displayModeState)
	{
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.tooltipManager = tooltipManager;
        this.displayModeState = displayModeState;

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
        boolean showTimezones = displayModeState.isShowTimezones();

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
            String timezoneId = configManager.getConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, "timezone_" + displayName);
            BufferedImage flagImage = !showTimezones && flagCode != null ? ClanCountryFlags.getFlag(flagCode) : null;
            String timeText = showTimezones && timezoneId != null ? ClanTimezones.currentTime(timezoneId) : null;

            if (note == null && flagImage == null && timeText == null)
            {
                continue;
            }

            Point anchorCanvasLocation = rowWidgets.get(0).getCanvasLocation();

            if (anchorCanvasLocation != null && playerListCanvasLocation != null)
            {
                if (!showTimezones && note != null && config.showIcons() && noteIcon != null)
                {
                    int noteX = playerListCanvasLocation.getX() + NOTE_ICON_X;
                    int noteY = anchorCanvasLocation.getY() + (ROW_HEIGHT - NOTE_ICON_HEIGHT) / 2;
                    graphics.drawImage(noteIcon, noteX, noteY, null);
                }

                int flagSlotRightEdge = playerListCanvasLocation.getX() + NOTE_ICON_X - FLAG_ICON_GAP;
                int timeSlotRightEdge = playerListCanvasLocation.getX() + TIME_TEXT_X;
                int rowTop = anchorCanvasLocation.getY();

                if (flagImage != null && config.showFlagIcons())
                {
                    int flagX = flagSlotRightEdge - FLAG_ICON_WIDTH;
                    int flagY = rowTop + (ROW_HEIGHT - FLAG_ICON_HEIGHT) / 2;
                    graphics.drawImage(flagImage, flagX, flagY, null);
                }

                if (timeText != null && config.showTimezoneText())
                {
                    Font originalFont = graphics.getFont();
                    Font timeFont = originalFont.deriveFont(TIME_FONT_SIZE);
                    graphics.setFont(timeFont);

                    FontMetrics metrics = graphics.getFontMetrics(timeFont);
                    int textHeight = metrics.getAscent() + metrics.getDescent();
                    int textX = timeSlotRightEdge - metrics.stringWidth(timeText);
                    int textY = rowTop + (ROW_HEIGHT - textHeight) / 2 + metrics.getAscent();
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(timeText, textX, textY);

                    graphics.setFont(originalFont);
                }
            }

			if (!menuOpen && config.showNoteTooltip() && ClanPlayerListRows.isRowAtPoint(rowWidgets, mouse))
            {
                String prefix = showTimezones ? timeText : flagCode;
                String tooltipText = prefix != null && note != null
                        ? prefix + " - " + note
                        : prefix != null ? prefix : note;
                if (tooltipText != null)
                {
                    tooltipManager.add(new Tooltip(tooltipText));
                }
            }
        }

        graphics.setClip(originalClip);

		return null;
	}
}