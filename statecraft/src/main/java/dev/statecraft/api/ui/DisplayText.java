package dev.statecraft.api.ui;

import dev.statecraft.api.ChunkKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DisplayText {
    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu 'at' HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private DisplayText() {}

    public static String name(String recorded, String unavailable) {
        return recorded == null || recorded.isBlank() || UUID.matcher(recorded).matches() ? unavailable : recorded;
    }

    public static String words(String token) {
        if (token == null || token.isBlank()) return "None";
        String spaced = token.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ').strip();
        StringBuilder result = new StringBuilder();
        for (String word : spaced.split("\\s+")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    public static String percent(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
    }

    public static String yesNo(boolean value) { return value ? "Yes" : "No"; }

    public static String date(long milliseconds) {
        return milliseconds <= 0 ? "Not scheduled" : DATE.format(Instant.ofEpochMilli(milliseconds));
    }

    public static String duration(long milliseconds) {
        if (milliseconds <= 0) return "Immediately";
        long[] lengths = {86_400_000, 3_600_000, 60_000, 1000, 1};
        String[] names = {"day", "hour", "minute", "second", "millisecond"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lengths.length; i++) {
            long count = milliseconds / lengths[i];
            milliseconds %= lengths[i];
            if (count > 0) {
                if (!result.isEmpty()) result.append(' ');
                result.append(units(count, names[i]));
            }
        }
        return result.toString();
    }

    public static String dimension(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> {
                String[] parts = dimension.split(":", 2);
                yield parts.length == 2 ? words(parts[1].replace('/', ' ')) + " (" + words(parts[0]) + ")"
                        : words(dimension);
            }
        };
    }

    public static String chunk(String key) {
        if (key.equals("here")) return "Current chunk";
        ChunkKey chunk = ChunkKey.parse(key);
        return "Chunk " + chunk.x() + ", " + chunk.z() + " (" + dimension(chunk.dimension()) + ")";
    }

    private static String units(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
