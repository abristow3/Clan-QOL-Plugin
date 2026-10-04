package com.clanqol;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ClanQolConfig.CONFIG_GROUP)
public interface ClanQolConfig extends Config
{
    String CONFIG_GROUP = "betterclanbroadcasts";

	@ConfigItem(
			keyName = "enabled",
			name = "Broadcast Ranks",
            position = 1,
			description = "Prepend clan rank icons to clan broadcast messages"
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showNoteTooltip",
			name = "Show note tooltip",
            position = 2,
			description = "Show a tooltip with your note when hovering a clan member who has one"
	)
	default boolean showNoteTooltip()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showIcons",
			name = "Show note icons",
            position = 3,
			description = "Show an icon in the clan member list next to members who have a note"
	)
	default boolean showIcons()
	{
		return true;
	}

    @ConfigItem(
            keyName = "showFlagIcons",
            name = "Show country flags",
            position = 4,
            description = "Show a flag icon in the clan member list next to members who have a country set"
    )
    default boolean showFlagIcons()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showTimezoneText",
            name = "Show timezone text",
            position = 5,
            description = "Show live local time in the clan member list next to members who have a timezone set"
    )
    default boolean showTimezoneText()
    {
        return true;
    }

    @ConfigItem(
            keyName = "csvData",
            name = "Member data",
            position = 6,
            description = "One line per member: name,flag,timezone,note. Click out of the box to apply. Clearing the box or hitting reset deletes all entries"    )
    default String csvData()
    {
        return "";
    }
}