package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot.Territory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class MapText {
    private MapText() {}

    static String plain(String value, int limit) {
        StringBuilder result = new StringBuilder();
        String input = Objects.requireNonNullElse(value, "");
        int length = 0;
        for (int offset = 0; offset < input.length() && length < limit;) {
            int point = input.codePointAt(offset);
            offset += Character.charCount(point);
            int type = Character.getType(point);
            if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                    length++;
                }
            } else if (!Character.isISOControl(point) && type != Character.FORMAT
                    && type != Character.SURROGATE && point != '\u00a7') {
                result.appendCodePoint(point);
                length++;
            }
        }
        return result.toString().strip();
    }

    static String waypointName(String value) {
        String clean = plain(value, 64);
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < clean.length();) {
            int point = clean.codePointAt(offset);
            offset += Character.charCount(point);
            boolean allowed = point >= 'a' && point <= 'z' || point >= 'A' && point <= 'Z'
                    || point >= '0' && point <= '9' || " -_.()'".indexOf(point) >= 0;
            result.append(allowed ? (char) point : '_');
        }
        return result.isEmpty() ? "City" : result.toString();
    }

    static String label(Territory territory) {
        for (String candidate : new String[]{territory.cityName(), territory.stateName(), territory.nationName()}) {
            String label = plain(candidate, 96);
            if (!label.isBlank()) {
                return label;
            }
        }
        return "Territory";
    }

    static String description(Territory territory) {
        return "Nation: " + plain(territory.nationName(), 96)
                + (territory.stateName() == null || territory.stateName().isBlank() ? "" : "\nState: " + plain(territory.stateName(), 96))
                + (territory.cityName() == null || territory.cityName().isBlank() ? "" : "\nCity: " + plain(territory.cityName(), 96))
                + "\nOwner: " + plain(territory.ownerName(), 128);
    }

    static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = Objects.requireNonNullElse(part, "").getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java must provide SHA-256", exception);
        }
    }

    static String dimensionFileToken(String dimension) {
        String readable = dimension.replaceAll("[^a-z0-9_-]", "_");
        return readable.substring(0, Math.min(readable.length(), 40)) + "-" + fingerprint(dimension);
    }
}
