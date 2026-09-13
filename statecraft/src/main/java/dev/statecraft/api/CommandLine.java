package dev.statecraft.api;

import java.util.ArrayList;
import java.util.List;

public final class CommandLine {
    public static final int MAX_LENGTH = 4096;

    private CommandLine() {}

    public static List<String> split(String input) {
        if (input == null || input.length() > MAX_LENGTH) {
            throw new UserError("Commands are limited to " + MAX_LENGTH + " characters.");
        }
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean started = false;
        for (char ch : input.toCharArray()) {
            if (Character.isISOControl(ch) && ch != '\t') {
                throw new UserError("Control characters are not permitted.");
            }
            if (escaped) {
                token.append(quote != 0 && ch == 'n' ? '\n' : quote != 0 && ch == 't' ? '\t' : ch);
                escaped = false;
                started = true;
            } else if (ch == '\\') {
                escaped = true;
                started = true;
            } else if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    token.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
                started = true;
            } else if (Character.isWhitespace(ch)) {
                if (started) {
                    result.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(ch);
                started = true;
            }
        }
        if (quote != 0 || escaped) {
            throw new UserError("Close quoted text and do not end a command with an escape.");
        }
        if (started) {
            result.add(token.toString());
        }
        if (result.size() > 64) {
            throw new UserError("Too many command arguments.");
        }
        return List.copyOf(result);
    }

    public static String tail(List<String> args, int start) {
        return String.join(" ", args.subList(Math.min(start, args.size()), args.size()));
    }

    public static String quote(String text) {
        return "\"" + text.replace("\r\n", "\n").replace("\r", "\n")
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
