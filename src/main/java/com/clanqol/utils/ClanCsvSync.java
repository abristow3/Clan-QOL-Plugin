package com.clanqol.utils;

import com.clanqol.ClanQolConfig;
import com.clanqol.clanpanel.ClanCountryFlags;
import com.clanqol.clanpanel.ClanTimezones;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

// keeps the csvData config box in sync with username/note/flag/timezone format
public final class ClanCsvSync
{
    public static final String CSV_KEY = "csvData";

    private static final String NOTE_PREFIX = "note_";
    private static final String FLAG_PREFIX = "flag_";
    private static final String TIMEZONE_PREFIX = "timezone_";

    private ClanCsvSync()
    {
    }

    public static final class Result
    {
        public int applied;
        public final List<String> errors = new ArrayList<>();
    }

    public static String build(ConfigManager configManager)
    {
        List<String> names = new ArrayList<>(collectAllNames(configManager));
        names.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> lines = new ArrayList<>();
        for (String name : names)
        {
            lines.add(encodeLine(name,
                    read(configManager, FLAG_PREFIX, name),
                    read(configManager, TIMEZONE_PREFIX, name),
                    read(configManager, NOTE_PREFIX, name)));
        }

        return String.join("\n", lines);
    }

    public static Result apply(ConfigManager configManager, String csv, boolean replace)
    {
        Result result = new Result();
        Set<String> seen = new HashSet<>();

        for (String rawLine : csv.split("\\r?\\n"))
        {
            String line = rawLine.trim();
            if (line.isEmpty())
            {
                continue;
            }

            List<String> fields = decodeLine(line);
            String name = fields.get(0).trim();
            if (name.isEmpty())
            {
                continue;
            }

            seen.add(name);
            String flag = fields.size() > 1 ? fields.get(1).trim().toUpperCase() : "";
            String timezone = fields.size() > 2 ? fields.get(2).trim() : "";
            String note = fields.size() > 3 ? fields.get(3).trim() : "";

            if (flag.isEmpty())
            {
                if (replace)
                {
                    remove(configManager, FLAG_PREFIX + name);
                }
            }
            else if (ClanCountryFlags.isValidCode(flag))
            {
                put(configManager, FLAG_PREFIX + name, flag);
            }
            else
            {
                result.errors.add("invalid flag '" + flag + "' for " + name);
            }

            if (timezone.isEmpty())
            {
                if (replace)
                {
                    remove(configManager, TIMEZONE_PREFIX + name);
                }
            }
            else
            {
                String normalized = ClanTimezones.normalize(timezone);
                if (normalized != null)
                {
                    put(configManager, TIMEZONE_PREFIX + name, normalized);
                }
                else
                {
                    result.errors.add("invalid timezone '" + timezone + "' for " + name);
                }
            }

            if (note.isEmpty())
            {
                if (replace)
                {
                    remove(configManager, NOTE_PREFIX + name);
                }
            }
            else
            {
                put(configManager, NOTE_PREFIX + name, note);
            }

            result.applied++;
        }

        if (replace)
        {
            for (String name : collectAllNames(configManager))
            {
                if (!seen.contains(name))
                {
                    remove(configManager, NOTE_PREFIX + name);
                    remove(configManager, FLAG_PREFIX + name);
                    remove(configManager, TIMEZONE_PREFIX + name);
                }
            }
        }

        return result;
    }

    // rewrites the config box from stored data or doing nothing if completly matches
    public static void syncField(ConfigManager configManager)
    {
        String built = build(configManager);
        String current = Strings.nullToEmpty(configManager.getConfiguration(ClanQolConfig.CONFIG_GROUP, CSV_KEY));
        if (!built.equals(current))
        {
            configManager.setConfiguration(ClanQolConfig.CONFIG_GROUP, CSV_KEY, built);
        }
    }

    private static Set<String> collectAllNames(ConfigManager configManager)
    {
        Set<String> names = new HashSet<>();
        collectNames(configManager, names, NOTE_PREFIX);
        collectNames(configManager, names, FLAG_PREFIX);
        collectNames(configManager, names, TIMEZONE_PREFIX);
        return names;
    }

    private static void collectNames(ConfigManager configManager, Set<String> names, String keyPrefix)
    {
        String fullPrefix = ClanQolConfig.CONFIG_GROUP + "." + keyPrefix;
        for (String fullKey : configManager.getConfigurationKeys(fullPrefix))
        {
            names.add(fullKey.substring(fullPrefix.length()));
        }
    }

    private static String read(ConfigManager configManager, String keyPrefix, String name)
    {
        return Strings.nullToEmpty(configManager.getConfiguration(ClanQolConfig.CONFIG_GROUP, keyPrefix + name));
    }

    private static void put(ConfigManager configManager, String key, String value)
    {
        if (!value.equals(configManager.getConfiguration(ClanQolConfig.CONFIG_GROUP, key)))
        {
            configManager.setConfiguration(ClanQolConfig.CONFIG_GROUP, key, value);
        }
    }

    private static void remove(ConfigManager configManager, String key)
    {
        if (configManager.getConfiguration(ClanQolConfig.CONFIG_GROUP, key) != null)
        {
            configManager.unsetConfiguration(ClanQolConfig.CONFIG_GROUP, key);
        }
    }

    private static String encodeLine(String... fields)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append(encodeField(fields[i]));
        }
        return sb.toString();
    }

    // fields with commas or quotes get wrapped in quotes, inner quotes doubled
    private static String encodeField(String field)
    {
        if (field == null || field.isEmpty())
        {
            return "";
        }

        boolean needsQuoting = field.contains(",") || field.contains("\"");
        return needsQuoting ? "\"" + field.replace("\"", "\"\"") + "\"" : field;
    }

    private static List<String> decodeLine(String line)
    {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);

            if (inQuotes)
            {
                if (c == '"')
                {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"')
                    {
                        current.append('"');
                        i++;
                    }
                    else
                    {
                        inQuotes = false;
                    }
                }
                else
                {
                    current.append(c);
                }
            }
            else if (c == '"')
            {
                inQuotes = true;
            }
            else if (c == ',')
            {
                fields.add(current.toString());
                current.setLength(0);
            }
            else
            {
                current.append(c);
            }
        }

        fields.add(current.toString());
        return fields;
    }
}