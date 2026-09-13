package com.betterclanbroadcasts;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

@Slf4j
public final class ClanCountryFlags
{
    private static final int FLAG_WIDTH = 16;
    private static final int FLAG_HEIGHT = 12;

    // alpha-2 code -> resource file name
    private static final Map<String, String> CODE_TO_RESOURCE = buildCodeMap();

    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    private ClanCountryFlags()
    {
    }

    public static boolean isValidCode(String code)
    {
        return code != null && CODE_TO_RESOURCE.containsKey(code.toUpperCase());
    }

    // returns null if the code is unrecognized or the resource is missing
    public static BufferedImage getFlag(String code)
    {
        if (code == null)
        {
            return null;
        }

        String normalized = code.toUpperCase();
        String resource = CODE_TO_RESOURCE.get(normalized);
        if (resource == null)
        {
            return null;
        }

        return IMAGE_CACHE.computeIfAbsent(normalized, k ->
        {
            BufferedImage img = ImageUtil.loadImageResource(ClanCountryFlags.class, "/flags/" + resource + ".png");
            if (img == null)
            {
                log.warn("Missing flag resource for country code {} (expected /flags/{}.png)", normalized, resource);
                return null;
            }
            return ImageUtil.resizeImage(img, FLAG_WIDTH, FLAG_HEIGHT);
        });
    }

    private static Map<String, String> buildCodeMap()
    {
        Map<String, String> map = new HashMap<>();
        // full ISO 3166-1 alpha-2 list plus GB home nations ABS order by line
        String[] codes = {
                "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ",
                "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS",
                "BT", "BV", "BW", "BY", "BZ",
                "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW",
                "CX", "CY", "CZ",
                "DE", "DJ", "DK", "DM", "DO", "DZ",
                "EC", "EE", "EG", "EH", "ER", "ES", "ET",
                "FI", "FJ", "FK", "FM", "FO", "FR",
                "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL", "GM", "GN", "GP", "GQ", "GR", "GS", "GT",
                "GU", "GW", "GY",
                "HK", "HM", "HN", "HR", "HT", "HU",
                "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR", "IS", "IT",
                "JE", "JM", "JO", "JP",
                "KE", "KG", "KH", "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ",
                "LA", "LB", "LC", "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY",
                "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK", "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS",
                "MT", "MU", "MV", "MW", "MX", "MY", "MZ",
                "NA", "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ",
                "OM",
                "PA", "PE", "PF", "PG", "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT", "PW", "PY",
                "QA",
                "RE", "RO", "RS", "RU", "RW",
                "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS",
                "ST", "SV", "SX", "SY", "SZ",
                "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR", "TT", "TV", "TW", "TZ",
                "UA", "UG", "UM", "US", "UY", "UZ",
                "VA", "VC", "VE", "VG", "VI", "VN", "VU",
                "WF", "WS",
                "YE", "YT",
                "ZA", "ZM", "ZW",
        };

        for (String code : codes)
        {
            map.put(code, code.toLowerCase());
        }

        // ISO 3166-2:GB home nations, not covered by alpha-2
        map.put("GB-ENG", "gb-eng");
        map.put("GB-SCT", "gb-sct");
        map.put("GB-WLS", "gb-wls");
        map.put("GB-NIR", "gb-nir");

        return Collections.unmodifiableMap(map);
    }
}