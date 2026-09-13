package dev.statecraft.client.state;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import dev.statecraft.api.MenuCategory;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UiPresentation {
    public enum Tone { NORMAL, MUTED, ACCENT, POSITIVE, NEGATIVE, WARNING }
    public enum Icon { NONE, GOVERNMENT, COMPANY, CLAIM, BALLOT, PAPER, BOOK, HANDSHAKE, MAIL, ACCOUNT, BANK, LOAN, TRADE, DELIVERY, OPERATION, PLAYER }
    private static final String MONEY = "\\$[0-9,]+\\.[0-9]{2}";
    private static final Pattern TAX_ENTRY = Pattern.compile("^(Paid|Arrears paid|Assessed|Outstanding) — [^;\\r\\n]+; "
            + MONEY + "; payer: [^\\r\\n]+; recipient: [^\\r\\n]+; .*", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTSTANDING = Pattern.compile("^Outstanding: " + MONEY + "$");
    private static final Pattern ARREAR = Pattern.compile("^[^\\r\\n]+ → [^\\r\\n]+; " + MONEY + " \\(.*\\)$");

    private UiPresentation() {}

    public static boolean needsAttention(int pendingActions, boolean recoveryStorageProblem) {
        return pendingActions > 0 || recoveryStorageProblem;
    }

    public static boolean includePageActions(UiQuery query, UiView context) {
        return !query.entity().present() || context == null;
    }

    public static Icon icon(EntityRef.Kind kind) {
        return switch (kind) {
            case NONE -> Icon.NONE;
            case GOVERNMENT -> Icon.GOVERNMENT;
            case COMPANY, COMPANY_PROPOSAL -> Icon.COMPANY;
            case CLAIM -> Icon.CLAIM;
            case ELECTION -> Icon.BALLOT;
            case BILL, ARREARS -> Icon.PAPER;
            case LAW -> Icon.BOOK;
            case DIPLOMACY, CONTRACT, INVITATION -> Icon.HANDSHAKE;
            case MAIL -> Icon.MAIL;
            case ACCOUNT -> Icon.ACCOUNT;
            case BANK -> Icon.BANK;
            case LOAN -> Icon.LOAN;
            case MARKET_LISTING, STOCK_LISTING -> Icon.TRADE;
            case DELIVERY -> Icon.DELIVERY;
            case OPERATION -> Icon.OPERATION;
            case DASHBOARD, PLAYER -> Icon.PLAYER;
        };
    }

    public static Icon categoryIcon(MenuCategory category) {
        return switch (category) {
            case OVERVIEW -> Icon.BOOK;
            case GOVERNMENTS -> Icon.GOVERNMENT;
            case TERRITORY -> Icon.CLAIM;
            case POLITICS -> Icon.BALLOT;
            case BUSINESS -> Icon.COMPANY;
            case ECONOMY -> Icon.ACCOUNT;
            case COMMUNICATIONS -> Icon.MAIL;
            case ADMINISTRATION -> Icon.OPERATION;
            case OTHER -> Icon.PAPER;
        };
    }

    public static Tone entityTone(String page, EntityRef entity) {
        return entity.namespace().equals("economy") && entity.kind() == EntityRef.Kind.ARREARS
                && (page.equals("economy:tax") || page.equals("economy:detail")) ? Tone.NEGATIVE : Tone.NORMAL;
    }

    public static Tone detailTone(EntityRef entity, UiText body, int lineIndex, int lineCount, String line) {
        return entity.namespace().equals("economy") && entity.kind() == EntityRef.Kind.ARREARS
                && body.key().equals("ui.statecraft.economy.detail.arrear") && body.arguments().size() == 6
                && lineIndex == lineCount - 2 && line.equals("Outstanding: " + body.arguments().get(4))
                ? Tone.NEGATIVE : Tone.NORMAL;
    }

    // Only labeled server tax-query rows have semantic coloring; user subjects and other screens do not.
    public static Tone taxTone(String page, String template, String line) {
        if (!page.equals("economy:tax")) return Tone.NORMAL;
        boolean report = template.equals("tax report <account> <page>");
        boolean arrears = template.equals("tax arrears <account>");
        if (arrears && OUTSTANDING.matcher(line).matches()) return line.equals("Outstanding: $0.00") ? Tone.ACCENT : Tone.NEGATIVE;
        if (arrears && ARREAR.matcher(line).matches()) return Tone.NEGATIVE;
        if (!report) return Tone.NORMAL;
        var match = TAX_ENTRY.matcher(line);
        if (!match.matches()) return Tone.NORMAL;
        return switch (match.group(1).toLowerCase(Locale.ROOT)) {
            case "paid", "arrears paid" -> Tone.POSITIVE;
            case "assessed" -> Tone.WARNING;
            case "outstanding" -> Tone.NEGATIVE;
            default -> Tone.NORMAL;
        };
    }

    public static Tone reviewTone(String labelKey, String label, boolean material) {
        String text = label.toLowerCase(Locale.ROOT);
        if (labelKey.endsWith(".total") || labelKey.endsWith(".fee") || labelKey.endsWith(".tax")
                || text.equals("total debit") || text.equals("total credit") || text.equals("amount")
                || text.equals("principal") || text.endsWith(" fee") || text.endsWith(" tax")) return Tone.ACCENT;
        return material ? Tone.NORMAL : Tone.MUTED;
    }
}
