package com.betterclanbroadcasts;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "Clan QOL",
		description = "Prepends clan rank icons to clan broadcast messages",
		tags = {"clan", "broadcast", "rank", "icon", "chat", "clanchat", "qol", "filtering", "filter", "notes"}
)
public class BetterClanBroadcastsPlugin extends Plugin
{
	private static final String ADD_NOTE = "Add Note";
	private static final String EDIT_NOTE = "Edit Note";
	private static final String NOTE_KEY_PREFIX = "note_";
	private static final int NOTE_CHARACTER_LIMIT = 128;
	private static final String NOTE_PROMPT_FORMAT = "%s's Notes<br>" +
			ColorUtil.prependColorTag("(Limit %s Characters)", new Color(0, 0, 170));

    private static final String ADD_FLAG = "Add Flag";
    private static final String EDIT_FLAG = "Edit Flag";
    private static final String FLAG_KEY_PREFIX = "flag_";
    private static final String FLAG_PROMPT_FORMAT = "%s's Country Code<br>" +
            ColorUtil.prependColorTag("(e.g. US, GB, GB-SCT, JP)", new Color(0, 0, 170));

    private static final String ADD_TIMEZONE = "Add Timezone";
    private static final String EDIT_TIMEZONE = "Edit Timezone";
    private static final String TIMEZONE_KEY_PREFIX = "timezone_";
    private static final String TIMEZONE_PROMPT_FORMAT = "%s's Timezone<br>" +
            ColorUtil.prependColorTag("(e.g. America/New_York, EST, +05:00)", new Color(0, 0, 170));

    private static final String SHOW_TIME_ALL = "Show Time for All";
    private static final String SHOW_FLAGS_ALL = "Show Flags for All";

    private static final int CLAN_GROUP_ID = WidgetUtil.componentToInterface(InterfaceID.ClansSidepanel.PLAYERLIST);

	@Inject
	private Client client;
	@Inject
	private BetterClanBroadcastsConfig config;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ChatIconManager chatIconManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ChatboxPanelManager chatboxPanelManager;
	@Inject
	private ClanNoteOverlay clanNoteOverlay;
    @Inject
    private ClanDisplayModeState displayModeState;

	private ClanRankPrefixer clanRankPrefixer;
	private ClanPlayerListSorter clanPlayerListSorter;
	private ClanCaIconMaintainer clanCaIconMaintainer;

	private static final int SORT_BUTTON_Y = 33;
	private static final int WORLD_SORT_BUTTON_X = 140;
    private static final int FLAG_TIME_TOGGLE_X = 108;
	private static final int NAME_SORT_BUTTON_X = 40;
	private static final int RANK_SORT_BUTTON_X = 13;

	private ClanSortToggleButton worldSortButton;
	private ClanSortToggleButton nameSortButton;
	private ClanSortToggleButton rankSortButton;
    private ClanSortToggleButton flagTimeToggleButton;

	@Getter
	private HoveredClanMember hoveredClanMember = null;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(clanNoteOverlay);

		clanRankPrefixer = new ClanRankPrefixer(client, clientThread, chatIconManager);
		clanPlayerListSorter = new ClanPlayerListSorter(client, configManager);

		clanCaIconMaintainer = new ClanCaIconMaintainer(config, clanRankPrefixer);
		overlayManager.add(clanCaIconMaintainer);

		worldSortButton = new ClanSortToggleButton(client, clientThread,
				WORLD_SORT_BUTTON_X, SORT_BUTTON_Y,
				"Sort ascending", "Sort descending",
				() -> clanPlayerListSorter.getSortMode() == ClanPlayerListSorter.SortMode.WORLD_ASCENDING,
				clanPlayerListSorter::setAscending,
				clanPlayerListSorter::setDescending);
		worldSortButton.startUp();

		nameSortButton = new ClanSortToggleButton(client, clientThread,
				NAME_SORT_BUTTON_X, SORT_BUTTON_Y,
				"Sort A-Z", "Sort Z-A",
				() -> clanPlayerListSorter.getSortMode() == ClanPlayerListSorter.SortMode.NAME_ASCENDING,
				clanPlayerListSorter::setNameAscending,
				clanPlayerListSorter::setNameDescending);
		nameSortButton.startUp();

		rankSortButton = new ClanSortToggleButton(client, clientThread,
				RANK_SORT_BUTTON_X, SORT_BUTTON_Y,
				"Sort rank ascending", "Sort rank descending",
				() -> clanPlayerListSorter.getSortMode() == ClanPlayerListSorter.SortMode.RANK_THEN_SPRITE_ASCENDING,
				clanPlayerListSorter::setRankThenSpriteAscending,
				clanPlayerListSorter::setRankThenSpriteDescending);
		rankSortButton.startUp();

        flagTimeToggleButton = new ClanSortToggleButton(client, clientThread,
                FLAG_TIME_TOGGLE_X, SORT_BUTTON_Y,
                "Show flags", "Show timezones",
                () -> !displayModeState.isShowTimezones(),
                () -> displayModeState.setShowTimezones(false),
                () -> displayModeState.setShowTimezones(true));
        flagTimeToggleButton.startUp();
    }

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(clanNoteOverlay);
		overlayManager.remove(clanCaIconMaintainer);

		clanRankPrefixer = null;
		clanCaIconMaintainer = null;

		clanPlayerListSorter.reset();
		clanPlayerListSorter = null;

		worldSortButton.reset();
		worldSortButton = null;

		nameSortButton.reset();
		nameSortButton = null;

		rankSortButton.reset();
		rankSortButton = null;

        flagTimeToggleButton.reset();
        flagTimeToggleButton = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.enabled())
		{
			return;
		}
		clanRankPrefixer.onChatMessage(event);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enabled())
		{
			return;
		}
		clanRankPrefixer.onGameTick(event);
		clanPlayerListSorter.onGameTick();
		worldSortButton.onGameTick();
		nameSortButton.onGameTick();
		rankSortButton.onGameTick();
        flagTimeToggleButton.onGameTick();
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry clanEntry = findClanMenuEntry(event.getMenuEntries());
		String target = clanEntry != null ? clanEntry.getTarget() : resolveClanMemberUnderMouse();

		if (target == null)
		{
			if (hoveredClanMember != null)
			{
				hoveredClanMember = null;
			}
			return;
		}

		// clan member names carry color tags same as friends do
		setHoveredClanMember(Text.toJagexName(Text.removeTags(target)));

			client.createMenuEntry(-1)
					.setOption(hoveredClanMember == null || hoveredClanMember.getNote() == null ? ADD_NOTE : EDIT_NOTE)
					.setType(MenuAction.RUNELITE)
					.setTarget(target)
					.onClick(e ->
					{
						String sanitizedTarget = Text.toJagexName(Text.removeTags(e.getTarget()));
						String note = getClanMemberNote(sanitizedTarget);

						chatboxPanelManager.openTextInput(String.format(NOTE_PROMPT_FORMAT, sanitizedTarget, NOTE_CHARACTER_LIMIT))
								.value(Strings.nullToEmpty(note))
								.onDone((content) ->
								{
									if (content == null)
									{
										return;
									}

								content = Text.removeTags(content).trim();
								log.debug("Set clan note for '{}': '{}'", sanitizedTarget, content);
								setClanMemberNote(sanitizedTarget, content);
							}).build();
				});

		String existingFlagCode = getClanMemberFlag(Text.toJagexName(Text.removeTags(target)));

            client.createMenuEntry(-1)
                    .setOption(existingFlagCode == null ? ADD_FLAG : EDIT_FLAG)
                    .setType(MenuAction.RUNELITE)
                    .setTarget(target)
                    .onClick(e ->
                    {
                        String sanitizedTarget = Text.toJagexName(Text.removeTags(e.getTarget()));
                        String currentCode = getClanMemberFlag(sanitizedTarget);

                        chatboxPanelManager.openTextInput(String.format(FLAG_PROMPT_FORMAT, sanitizedTarget))
                                .value(Strings.nullToEmpty(currentCode))
                                .onDone((content) ->
                                {
                                    if (content == null)
                                    {
                                        return;
                                    }

                                    String code = Text.removeTags(content).trim().toUpperCase();

                                    if (code.isEmpty())
                                    {
                                        log.debug("Cleared clan flag for '{}'", sanitizedTarget);
                                        setClanMemberFlag(sanitizedTarget, null);
                                        return;
                                    }

                                    if (!ClanCountryFlags.isValidCode(code))
                                    {
                                        clientThread.invoke(() -> client.addChatMessage(ChatMessageType.CONSOLE, "",
                                                ColorUtil.wrapWithColorTag("Invalid country code: " + code, Color.RED), null));
                                        return;
                                    }

								log.debug("Set clan flag for '{}': '{}'", sanitizedTarget, code);
								setClanMemberFlag(sanitizedTarget, code);
							}).build();
				});

        String existingTimezoneId = getClanMemberTimezone(Text.toJagexName(Text.removeTags(target)));

        client.createMenuEntry(-1)
                .setOption(existingTimezoneId == null ? ADD_TIMEZONE : EDIT_TIMEZONE)
                .setType(MenuAction.RUNELITE)
                .setTarget(target)
                .onClick(e ->
                {
                    String sanitizedTarget = Text.toJagexName(Text.removeTags(e.getTarget()));
                    String currentTimezone = getClanMemberTimezone(sanitizedTarget);

                    chatboxPanelManager.openTextInput(String.format(TIMEZONE_PROMPT_FORMAT, sanitizedTarget))
                            .value(Strings.nullToEmpty(currentTimezone))
                            .onDone((content) ->
                            {
                                if (content == null)
                                {
                                    return;
                                }

                                String rawInput = Text.removeTags(content).trim();

                                if (rawInput.isEmpty())
                                {
                                    log.debug("Cleared clan timezone for '{}'", sanitizedTarget);
                                    setClanMemberTimezone(sanitizedTarget, null);
                                    return;
                                }

                                String normalized = ClanTimezones.normalize(rawInput);

                                if (normalized == null)
                                {
                                    clientThread.invoke(() -> client.addChatMessage(ChatMessageType.CONSOLE, "",
                                            ColorUtil.wrapWithColorTag("Invalid timezone: " + rawInput, Color.RED), null));
                                    return;
                                }

                                log.debug("Set clan timezone for '{}': '{}'", sanitizedTarget, normalized);
                                setClanMemberTimezone(sanitizedTarget, normalized);
                            }).build();
                });

        // mass toggle, also reachable via the header button
        boolean currentlyShowingTimezones = displayModeState.isShowTimezones();

        client.createMenuEntry(-1)
                .setOption(currentlyShowingTimezones ? SHOW_FLAGS_ALL : SHOW_TIME_ALL)
                .setType(MenuAction.RUNELITE)
                .setTarget("")
                .onClick(e -> displayModeState.setShowTimezones(!currentlyShowingTimezones));
	}

	private static MenuEntry findClanMenuEntry(MenuEntry[] menuEntries)
	{
		for (MenuEntry entry : menuEntries)
		{
			int groupId = WidgetUtil.componentToInterface(entry.getParam1());
			if (groupId == CLAN_GROUP_ID)
			{
				return entry;
			}
		}

		return null;
	}

	private String resolveClanMemberUnderMouse()
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
		Point mouse = client.getMouseCanvasPosition();

		for (List<Widget> rowWidgets : rowsByIndex.values())
		{
			if (!ClanPlayerListRows.isRowAtPoint(rowWidgets, mouse))
			{
				continue;
			}

			ClanChannelMember member = ClanPlayerListRows.findMember(rowWidgets, membersByName);
			if (member != null)
			{
				return member.getName();
			}
		}

		return null;
	}

	private void setClanMemberNote(String displayName, String note)
	{
		if (Strings.isNullOrEmpty(note))
		{
			configManager.unsetConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, NOTE_KEY_PREFIX + displayName);
		}
		else
		{
			configManager.setConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, NOTE_KEY_PREFIX + displayName, note);
		}
	}

	@Nullable
	private String getClanMemberNote(String displayName)
	{
		return configManager.getConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, NOTE_KEY_PREFIX + displayName);
	}

    private void setClanMemberFlag(String displayName, @Nullable String countryCode)
    {
        if (Strings.isNullOrEmpty(countryCode))
        {
            configManager.unsetConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, FLAG_KEY_PREFIX + displayName);
        }
        else
        {
            configManager.setConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, FLAG_KEY_PREFIX + displayName, countryCode);
        }
    }

    @Nullable
    private String getClanMemberFlag(String displayName)
    {
        return configManager.getConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, FLAG_KEY_PREFIX + displayName);
    }

    private void setClanMemberTimezone(String displayName, @Nullable String zoneId)
    {
        if (Strings.isNullOrEmpty(zoneId))
        {
            configManager.unsetConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, TIMEZONE_KEY_PREFIX + displayName);
        }
        else
        {
            configManager.setConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, TIMEZONE_KEY_PREFIX + displayName, zoneId);
        }
    }

    @Nullable
    private String getClanMemberTimezone(String displayName)
    {
        return configManager.getConfiguration(BetterClanBroadcastsConfig.CONFIG_GROUP, TIMEZONE_KEY_PREFIX + displayName);
    }

    private void setHoveredClanMember(String displayName)
    {
        hoveredClanMember = null;

		if (!config.showNoteTooltip() || Strings.isNullOrEmpty(displayName))
		{
			return;
		}

		String note = getClanMemberNote(displayName);
		if (note != null)
		{
			hoveredClanMember = new HoveredClanMember(displayName, note);
		}
	}

	@Provides
	BetterClanBroadcastsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterClanBroadcastsConfig.class);
	}
}