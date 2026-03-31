package com.shortlink.shortlink.service;

import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Device;
import ua_parser.OS;
import ua_parser.Parser;
import ua_parser.UserAgent;

import java.util.Locale;
import java.util.Set;

@Service
public class UserAgentParser {

    private static final Set<String> DESKTOP_OS_FAMILIES = Set.of(
            "Windows",
            "Mac OS X",
            "Linux",
            "Chrome OS",
            "Ubuntu"
    );

    private final Parser parser = new Parser();

    public ParsedUserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUserAgent(null, null, null);
        }

        try {
            Client client = parser.parse(userAgent);
            return new ParsedUserAgent(
                    resolveDeviceType(client.device, client.os, userAgent),
                    normalizeFamily(client.os),
                    normalizeFamily(client.userAgent)
            );
        } catch (Exception exception) {
            return new ParsedUserAgent(null, null, null);
        }
    }

    private String resolveDeviceType(Device device, OS os, String rawUserAgent) {
        String family = normalizeFamily(device);
        String normalizedFamily = family == null ? null : family.toLowerCase(Locale.ROOT);
        String normalizedUserAgent = rawUserAgent == null ? "" : rawUserAgent.toLowerCase(Locale.ROOT);

        if (containsAny(normalizedFamily, normalizedUserAgent, "ipad", "tablet")) {
            return "tablet";
        }
        if (containsAny(normalizedFamily, normalizedUserAgent, "iphone", "android", "mobile")) {
            return "mobile";
        }
        if (isDesktopOs(os) && !containsAny(normalizedFamily, normalizedUserAgent, "bot", "crawler", "spider")) {
            return "desktop";
        }
        return "other";
    }

    private String normalizeFamily(UserAgent userAgent) {
        if (userAgent == null || userAgent.family == null || userAgent.family.isBlank()) {
            return null;
        }
        return normalizeOther(userAgent.family);
    }

    private String normalizeFamily(OS os) {
        if (os == null || os.family == null || os.family.isBlank()) {
            return null;
        }
        return normalizeOther(os.family);
    }

    private String normalizeFamily(Device device) {
        if (device == null || device.family == null || device.family.isBlank()) {
            return null;
        }
        return normalizeOther(device.family);
    }

    private boolean isDesktopOs(OS os) {
        String family = normalizeFamily(os);
        return family != null && DESKTOP_OS_FAMILIES.contains(family);
    }

    private boolean containsAny(String family, String rawUserAgent, String... needles) {
        for (String needle : needles) {
            if ((family != null && family.contains(needle)) || rawUserAgent.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeOther(String family) {
        if ("Other".equalsIgnoreCase(family)) {
            return "other";
        }
        return family;
    }

    public record ParsedUserAgent(String deviceType, String os, String browser) {
    }
}
