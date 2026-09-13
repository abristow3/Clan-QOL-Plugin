package com.betterclanbroadcasts;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
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
	private static final Set<String> NOTE_ANCHOR_OPTIONS = Set.of("Add ignore", "Remove friend");
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

	private ClanRankPrefixer clanRankPrefixer;
	private ClanPlayerListSorter clanPlayerListSorter;
	private ClanCaIconMaintainer clanCaIconMaintainer;

	private static final int SORT_BUTTON_Y = 33;
	private static final int WORLD_SORT_BUTTON_X = 140;
	private static final int NAME_SORT_BUTTON_X = 40;
	private static final int RANK_SORT_BUTTON_X = 13;

	private ClanSortToggleButton worldSortButton;
	private ClanSortToggleButton nameSortButton;
	private ClanSortToggleButton rankSortButton;

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
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		int groupId = WidgetUtil.componentToInterface(event.getActionParam1());

		if (groupId == CLAN_GROUP_ID && NOTE_ANCHOR_OPTIONS.contains(event.getOption()))
		{
			// clan member names carry color tags same as friends do
			setHoveredClanMember(Text.toJagexName(Text.removeTags(event.getTarget())));

			client.createMenuEntry(-1)
					.setOption(hoveredClanMember == null || hoveredClanMember.getNote() == null ? ADD_NOTE : EDIT_NOTE)
					.setType(MenuAction.RUNELITE)
					.setTarget(event.getTarget())
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

            String flagMenuTarget = Text.toJagexName(Text.removeTags(event.getTarget()));
            String existingFlagCode = getClanMemberFlag(flagMenuTarget);

            client.createMenuEntry(-1)
                    .setOption(existingFlagCode == null ? ADD_FLAG : EDIT_FLAG)
                    .setType(MenuAction.RUNELITE)
                    .setTarget(event.getTarget())
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
        }
        else if (hoveredClanMember != null)
        {
            hoveredClanMember = null;
        }
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