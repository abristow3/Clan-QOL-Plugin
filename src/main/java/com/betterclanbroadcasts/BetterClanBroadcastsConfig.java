package com.betterclanbroadcasts;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BetterClanBroadcastsConfig.CONFIG_GROUP)
public interface BetterClanBroadcastsConfig extends Config
{
	String CONFIG_GROUP = "betterclanbroadcasts";

	@ConfigItem(
			keyName = "enabled",
			name = "Broadcast Ranks",
			description = "Prepend clan rank icons to clan broadcast messages"
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showNoteTooltip",
			name = "Show note tooltip",
			description = "Show a tooltip with your note when hovering a clan member who has one"
	)
	default boolean showNoteTooltip()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showIcons",
			name = "Show note icons",
			description = "Show an icon in the clan member list next to members who have a note"
	)
	default boolean showIcons()
	{
		return true;
	}

    @ConfigItem(
            keyName = "showFlagIcons",
            name = "Show country flags",
            description = "Show a flag icon in the clan member list next to members who have a country set"
    )
    default boolean showFlagIcons()
    {
        return true;
    }
}