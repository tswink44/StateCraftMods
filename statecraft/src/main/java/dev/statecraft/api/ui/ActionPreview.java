package dev.statecraft.api.ui;

import dev.statecraft.api.CommandLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record ActionPreview(UiText title, List<Line> lines, UiText warning, String stateKey) {
    public static final int MAX_LINES = 40;

    public record Line(UiText label, UiText value, boolean material) {
        public Line {
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
            if (label.fallback().length() > 128 || value.fallback().length() > 4096) {
                throw new IllegalArgumentException("Invalid review line.");
            }
        }

        public Line(String label, String value, boolean material) {
            this(UiText.literal(label), UiText.literal(value), material);
        }
    }

    public ActionPreview {
        Objects.requireNonNull(title);
        Objects.requireNonNull(warning);
        Objects.requireNonNull(stateKey);
        lines = List.copyOf(lines);
        if (title.fallback().length() > 128 || lines.size() > MAX_LINES
                || warning.fallback().length() > 2048 || stateKey.length() > 4096) {
            throw new IllegalArgumentException("Invalid action review.");
        }
        long characters = title.characters() + warning.characters() + stateKey.length();
        for (Line line : lines) characters += line.label().characters() + line.value().characters();
        if (characters > 50_000) throw new IllegalArgumentException("Action review exceeds the aggregate text budget.");
    }

    public String fingerprint() {
        StringBuilder material = new StringBuilder(CommandLine.quote(stateKey));
        for (Line line : lines) {
            if (line.material()) {
                material.append(CommandLine.quote(line.label().fallback()))
                        .append(CommandLine.quote(line.value().fallback()));
            }
        }
        return digest(material.toString());
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible);
        }
    }
}
