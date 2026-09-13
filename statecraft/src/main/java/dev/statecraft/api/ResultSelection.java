package dev.statecraft.api;

import java.util.List;
import java.util.regex.Pattern;

public final class ResultSelection {
    private static final String UUID = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";
    private static final List<Pattern> IDENTIFIERS = List.of(
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+\\|-?[0-9]+\\|-?[0-9]+"),
            Pattern.compile("escrow:contract:" + UUID),
            Pattern.compile("(?:player|nation|state|city|company|bank|escrow):" + UUID),
            Pattern.compile(UUID),
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+"));

    private ResultSelection() {}

    public static String identifier(String row) {
        int earliest = Integer.MAX_VALUE;
        String result = row.strip();
        for (Pattern pattern : IDENTIFIERS) {
            var match = pattern.matcher(row);
            if (match.find() && match.start() < earliest) {
                earliest = match.start();
                result = match.group();
            }
        }
        return result;
    }
}
