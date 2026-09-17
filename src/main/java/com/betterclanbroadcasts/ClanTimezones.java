package com.betterclanbroadcasts;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// validates/normalizes timezone input and formats the current time for a stored zone id
final class ClanTimezones
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, String> IANA_IDS_LOWER = buildIanaIndex();
    private static final Map<String, String> SHORT_CODES = buildShortCodes();

    private ClanTimezones()
    {
    }

    static String normalize(String input)
    {
        if (input == null || input.trim().isEmpty())
        {
            return null;
        }

        String trimmed = input.trim();
        String upper = trimmed.toUpperCase();

        String curated = SHORT_CODES.get(upper);
        if (curated != null)
        {
            return curated;
        }

        String iana = IANA_IDS_LOWER.get(trimmed.toLowerCase());
        if (iana != null)
        {
            return iana;
        }

        if (ZoneId.SHORT_IDS.containsKey(upper))
        {
            try
            {
                return ZoneId.of(upper, ZoneId.SHORT_IDS).getId();
            }
            catch (DateTimeException ignored)
            {
            }
        }

        try
        {
            return ZoneId.of(normalizeOffsetCasing(trimmed)).getId();
        }
        catch (DateTimeException ignored)
        {
            return null;
        }
    }

    static boolean isValid(String input)
    {
        return normalize(input) != null;
    }

    // formats the current time for zone id like "14:32"
    static String currentTime(String zoneId)
    {
        try
        {
            return ZonedDateTime.now(ZoneId.of(zoneId)).format(TIME_FORMAT);
        }
        catch (DateTimeException e)
        {
            return null;
        }
    }

    // only uppercases the UTC/GMT/Z
    private static String normalizeOffsetCasing(String input)
    {
        String upper = input.toUpperCase();
        if (upper.startsWith("UTC") || upper.startsWith("GMT") || upper.equals("UT") || upper.equals("Z"))
        {
            return upper;
        }

        return input;
    }

    private static Map<String, String> buildIanaIndex()
    {
        Map<String, String> index = new HashMap<>();
        for (String id : ZoneId.getAvailableZoneIds())
        {
            index.put(id.toLowerCase(), id);
        }

        return Collections.unmodifiableMap(index);
    }

    private static Map<String, String> buildShortCodes()
    {
        Map<String, String> map = new HashMap<>();

        // US
        map.put("EST", "America/New_York");
        map.put("EDT", "America/New_York");
        map.put("CST", "America/Chicago");
        map.put("CDT", "America/Chicago");
        map.put("MST", "America/Denver");
        map.put("MDT", "America/Denver");
        map.put("PST", "America/Los_Angeles");
        map.put("PDT", "America/Los_Angeles");
        map.put("AKST", "America/Anchorage");
        map.put("AKDT", "America/Anchorage");
        map.put("HST", "Pacific/Honolulu");

        // Australia
        map.put("AEST", "Australia/Sydney");
        map.put("AEDT", "Australia/Sydney");
        map.put("ACST", "Australia/Adelaide");
        map.put("ACDT", "Australia/Adelaide");
        map.put("AWST", "Australia/Perth");

        // Europe - covers the EU's three DST-observing zones plus UK and Russia
        map.put("GMT", "Europe/London");
        map.put("BST", "Europe/London");
        map.put("WET", "Europe/Lisbon");
        map.put("WEST", "Europe/Lisbon");
        map.put("CET", "Europe/Paris");
        map.put("CEST", "Europe/Paris");
        map.put("EET", "Europe/Helsinki");
        map.put("EEST", "Europe/Helsinki");
        map.put("MSK", "Europe/Moscow");

        return Collections.unmodifiableMap(map);
    }
}