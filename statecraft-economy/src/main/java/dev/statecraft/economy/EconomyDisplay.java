package dev.statecraft.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.statecraft.api.Actor;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.DisplayText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.statecraft.economy.EconomyPresentation.shorten;
import static dev.statecraft.economy.EconomyPresentation.t;

/** Display-only projections; identifiers and serialized item data never leave their records. */
final class EconomyDisplay {
    private static final Pattern UUID = Pattern.compile("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final Pattern ORIGINAL_AUTOPAY = Pattern.compile("(?:^|; )automatic payments=(true|false)(?=;|$)");
    private static final Set<String> TAX_KINDS = Set.of("incomeTaxBps", "salesTaxBps", "propertyTaxBps", "corporateTaxBps", "tariffBps", "companyFee");
    private final EconomyEngine e;

    EconomyDisplay(EconomyEngine engine) { e = engine; }

    String player(String id) { return name(e.data.playerNames.get(id), "Former player"); }
    String bank(String id) {
        EconomyData.Bank bank = e.data.banks.get(id);
        return name(bank == null ? null : bank.name, "Former bank");
    }
    String company(String id) {
        return name(e.governance.company(id).filter(c -> c.id().equals(id)).map(GovernanceAccess.CompanyView::name).orElse(null),
                "Former company");
    }
    String government(String id) {
        if (id == null || id.isEmpty()) return "No nation";
        return name(e.governance.government(id).filter(g -> g.id().equals(id)).map(GovernanceAccess.GovernmentView::name).orElse(null),
                "Former government");
    }

    String claimTitle(GovernanceAccess.ClaimView claim) {
        if (claim == null) return "Unclaimed land";
        String region = government(claim.nationId());
        if (claim.stateId() != null) region = government(claim.stateId()) + " / " + region;
        return claim.cityId() != null ? "[" + government(claim.cityId()) + "] (" + region + ")"
                : (claim.stateId() == null ? "Unassigned region" : "Unassigned city") + " (" + region + ")";
    }

    String account(Actor actor, String account) {
        return account.equals(actor.account()) ? name(e.data.playerNames.get(actor.id().toString()),
                DisplayText.name(actor.name(), "Former player")) + " - Personal account" : account(account);
    }

    String account(String account) {
        if (account == null) return "Unavailable account";
        int colon = account.indexOf(':');
        if (colon < 0) return "Unavailable account";
        String id = account.substring(colon + 1);
        return switch (account.substring(0, colon)) {
            case "player" -> player(id) + " - Personal account";
            case "company" -> company(id) + " - Company treasury";
            case "bank" -> bank(id) + " - Bank assets";
            case "nation", "state", "city" -> {
                String kind = account.substring(0, colon);
                String owner = e.governance.government(id).filter(g -> g.account().equals(account))
                        .map(GovernanceAccess.GovernmentView::name).orElse(null);
                yield name(owner, "Former " + kind) + " - " + DisplayText.words(kind) + " treasury";
            }
            case "system" -> switch (id) {
                case "fees", "statecraft-fees" -> "Public fee fund";
                case "hub" -> "Trading Hub";
                case "cash" -> "Physical currency exchange";
                case "admin" -> "Operator adjustments";
                case "initial" -> "Starting balance grant";
                default -> "Public economy fund";
            };
            case "escrow" -> "Reserved funds";
            default -> "Unavailable account";
        };
    }

    String loan(EconomyData.Loan loan) { return bank(loan.bank) + " - " + player(loan.borrower) + "'s loan"; }

    String subject(EconomyData.Arrear debt) { return subject(debt.kind, debt.subject); }

    String subject(String kind, String original) {
        String subject = original == null ? "" : original;
        String suffix = "";
        var full = Pattern.compile(" \\[full integer-cent assessment: ([0-9]+)]$").matcher(subject);
        if (full.find()) {
            suffix = "; full assessment: " + EconomyPresentation.money(new java.math.BigInteger(full.group(1)));
            subject = subject.substring(0, full.start());
        }
        var periods = Pattern.compile(" \\[([0-9]+) overdue periods]$").matcher(subject);
        if (periods.find()) {
            suffix = "; " + periods.group(1) + " overdue periods" + suffix;
            subject = subject.substring(0, periods.start());
        }
        String label = subject;
        if ("companyFee".equals(kind)) label = company(subject);
        else if (subject.startsWith("dividend:")) label = company(subject.substring(9)) + " dividends";
        else if (subject.matches("(player|company|bank|nation|state|city|system|escrow):.+")) label = account(subject);
        else if (subject.contains("|")) {
            try { label = DisplayText.chunk(subject); } catch (UserError invalid) { }
        } else if (e.data.market.containsKey(subject)) label = item(e.data.market.get(subject).item);
        else if (e.data.stocks.containsKey(subject)) label = company(e.data.stocks.get(subject).company) + " shares";
        else if (!subject.isEmpty() && e.governance.company(subject).map(GovernanceAccess.CompanyView::id).orElse("").equals(subject)) label = company(subject);
        else if (subject.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) label = registry(subject);
        else if (UUID.matcher(subject).matches()) label = "Previous transaction";
        return label + suffix;
    }

    static String tax(String kind) {
        return DisplayText.words(kind != null && kind.endsWith("Bps") ? kind.substring(0, kind.length() - 3) : kind);
    }

    static String deliveryReason(String reason) {
        for (String prefix : List.of("Marketplace purchase ", "Listing cancelled: ", "Listing expired: ")) {
            if (reason.startsWith(prefix) && UUID.matcher(reason.substring(prefix.length())).matches()) {
                return prefix.equals("Marketplace purchase ") ? "Marketplace purchase"
                        : prefix.equals("Listing cancelled: ") ? "Cancelled listing return" : "Expired listing return";
            }
        }
        return reason;
    }

    String setting(String key, String value) {
        try {
            if (key.endsWith("Bps")) return DisplayText.percent(Integer.parseInt(value));
            if (key.equals("baseChunkValue") || key.endsWith("Cents")) return Money.format(Long.parseLong(value));
            if (key.endsWith("Millis")) return DisplayText.duration(Long.parseLong(value));
            if (value.equals("true") || value.equals("false")) return DisplayText.yesNo(Boolean.parseBoolean(value));
        } catch (NumberFormatException | UserError invalid) { }
        return value;
    }

    String transactionReason(Actor actor, String reason) {
        for (String prefix : List.of("Bank deposit at ", "Bank withdrawal at ")) {
            if (reason.startsWith(prefix)) {
                String id = reason.substring(prefix.length());
                if (UUID.matcher(id).matches() || e.data.banks.containsKey(id)) return prefix + bank(id);
            }
        }
        if (reason.startsWith("Dividend from ")) {
            String id = reason.substring("Dividend from ".length());
            if (UUID.matcher(id).matches() || e.governance.company(id).filter(c -> c.id().equals(id)).isPresent()) return "Dividend from " + company(id);
        }
        if (reason.startsWith("Trading Hub: ")) {
            String item = reason.substring("Trading Hub: ".length());
            if (item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return "Trading Hub: " + registry(item);
        }
        for (String prefix : List.of("Trading Hub tax: ", "Dividend corporate tax: ", "Arrears: ")) {
            if (reason.startsWith(prefix) && TAX_KINDS.contains(reason.substring(prefix.length()))) return prefix + tax(reason.substring(prefix.length()));
        }
        for (String prefix : List.of("Loan repayment ", "Full loan repayment ", "Loan disbursement ",
                "Consented scheduled loan payment ", "Origination-consented default balance recovery ", "Collateral equity surplus for ")) {
            if (reason.startsWith(prefix) && UUID.matcher(reason.substring(prefix.length())).matches()) {
                return prefix + loanReference(actor, reason.substring(prefix.length()));
            }
        }
        var purchase = Pattern.compile("^(Marketplace purchase|Property purchase|Stock purchase) (" + UUID.pattern()
                + ")( commission|: [A-Za-z]+)?$").matcher(reason);
        if (purchase.matches()) {
            String type = purchase.group(1), id = purchase.group(2), suffix = purchase.group(3);
            String label = type;
            if (type.equals("Marketplace purchase") && e.data.market.containsKey(id)) label += " — " + item(e.data.market.get(id).item);
            if (type.equals("Stock purchase") && e.data.stocks.containsKey(id)) label += " — " + company(e.data.stocks.get(id).company);
            if (suffix == null) return label;
            if (suffix.equals(" commission")) return label + " commission";
            if (TAX_KINDS.contains(suffix.substring(2))) return label + " — " + tax(suffix.substring(2));
        }
        return reason;
    }

    String bankEventDetail(Actor actor, String kind, String detail) {
        if (kind.equals("REPAYMENT")) return transactionReason(actor, detail);
        if ((kind.equals("LOAN_CANCELLED") || kind.equals("DEFAULTED")) && UUID.matcher(detail).matches()) return loanReference(actor, detail);
        if (kind.equals("LOAN_REQUEST")) {
            int separator = detail.indexOf(": ");
            if (separator > 0 && UUID.matcher(detail.substring(0, separator)).matches()) {
                String agreement = agreementSnapshot(detail.substring(separator + 2));
                if (agreement != null) return loanReference(actor, detail.substring(0, separator)) + "\n" + agreement;
            }
        }
        if (kind.equals("TERMS")) {
            var terms = Pattern.compile("^Future deposits/loans: ([0-9]+)/([0-9]+) bps; origination=([0-9]+) bps\\. Existing interest contracts are unchanged\\.$").matcher(detail);
            if (terms.matches()) return t("query.bank_terms_event", "Future deposit rate %s; loan rate %s; origination fee rate %s. Existing interest contracts are unchanged.",
                    DisplayText.percent(Integer.parseInt(terms.group(1))), DisplayText.percent(Integer.parseInt(terms.group(2))),
                    DisplayText.percent(Integer.parseInt(terms.group(3)))).fallback();
        }
        if (kind.equals("LOAN_ISSUED") || kind.equals("MISSED_PAYMENT")) {
            var event = Pattern.compile("^(" + UUID.pattern() + "); (fee retained \\$[0-9,]+\\.[0-9]{2}|missed periods=([0-9]+))$").matcher(detail);
            if (event.matches()) return loanReference(actor, event.group(1)) + "; "
                    + (event.group(3) == null ? event.group(2) : "missed periods: " + event.group(3));
        }
        if (kind.equals("COLLATERAL_RECOVERY")) {
            var recovery = Pattern.compile("^(" + UUID.pattern() + "); chunk=([^;]+); valuation=(\\$[0-9,]+\\.[0-9]{2}); equity paid=(\\$[0-9,]+\\.[0-9]{2})$").matcher(detail);
            if (recovery.matches()) return t("query.collateral_recovery", "%s; %s; valuation: %s; equity paid: %s.",
                    loanReference(actor, recovery.group(1)), DisplayText.chunk(recovery.group(2)), recovery.group(3), recovery.group(4)).fallback();
        }
        return detail;
    }

    private String loanReference(Actor actor, String id) {
        EconomyData.Loan loan = e.data.loans.get(id);
        if (loan == null) return "Former loan";
        try { e.banking.requireLoanViewer(actor, loan); return loan(loan); }
        catch (UserError denied) { return "Loan"; }
    }

    private String agreementSnapshot(String text) {
        var terms = Pattern.compile("^Principal (\\$[0-9,]+\\.[0-9]{2}); ([0-9]+) bps simple interest per ([0-9]+)ms on outstanding principal; ([0-9]+) equal-principal periods; lifetime interest cap (\\$[0-9,]+\\.[0-9]{2}); origination fee (\\$[0-9,]+\\.[0-9]{2}); balance seizure consent=(true|false); specific collateral=([^;]+); repossession consent=(true|false); automatic payments=(true|false); default after ([0-9]+) missed periods plus ([0-9]+)ms grace\\.$").matcher(text);
        if (!terms.matches()) return null;
        EconomyData.Loan original = new EconomyData.Loan();
        original.originalPrincipal = Money.parse(terms.group(1).substring(1).replace(",", ""));
        original.rateBps = Integer.parseInt(terms.group(2));
        original.periodMillis = Long.parseLong(terms.group(3));
        original.periods = Integer.parseInt(terms.group(4));
        original.interestCap = Money.parse(terms.group(5).substring(1).replace(",", ""));
        original.originationFee = Money.parse(terms.group(6).substring(1).replace(",", ""));
        original.consentBalanceSeizure = Boolean.parseBoolean(terms.group(7));
        original.collateral = terms.group(8).equals("null") ? null : terms.group(8);
        original.consentRepossession = Boolean.parseBoolean(terms.group(9));
        original.autoPay = Boolean.parseBoolean(terms.group(10));
        original.defaultMissedPayments = Integer.parseInt(terms.group(11));
        original.graceMillis = Long.parseLong(terms.group(12));
        original.terms = text;
        return agreement(original);
    }

    String failure(String reason) {
        for (String prefix : List.of("Insufficient funds in ", "You do not have treasury access to ")) {
            if (reason.startsWith(prefix) && reason.endsWith(".")) {
                return prefix + account(reason.substring(prefix.length(), reason.length() - 1)) + ".";
            }
        }
        String prefix = "The daily spending limit for ", suffix = " would be exceeded.";
        if (reason.startsWith(prefix) && reason.endsWith(suffix)) {
            return prefix + account(reason.substring(prefix.length(), reason.length() - suffix.length())) + suffix;
        }
        return reason;
    }

    String agreement(EconomyData.Loan loan) {
        var recorded = ORIGINAL_AUTOPAY.matcher(loan.terms == null ? "" : loan.terms);
        String originalAuto = recorded.find() ? DisplayText.yesNo(Boolean.parseBoolean(recorded.group(1))) : "Not recorded";
        return t("agreement.loan",
                "Principal %s; %s simple interest per %s on outstanding principal; %s equal-principal repayment periods; lifetime interest cap %s; origination fee %s; net disbursement if funded %s.\n"
                        + "Default after %s missed periods plus %s grace. Original automatic payments: %s.\n"
                        + "Personal-wallet recovery consent: %s. Specific collateral: %s. Repossession consent: %s.\n"
                        + "After default, only consented recovery may be used, subject to server recovery settings: wallet recovery is limited to the remaining debt; repossession is limited to the pledged property. Its valuation is credited against debt, surplus equity is returned, and any shortfall remains owed. Later bank rates do not change this agreement.",
                Money.format(loan.originalPrincipal), DisplayText.percent(loan.rateBps), DisplayText.duration(loan.periodMillis),
                "" + loan.periods, Money.format(loan.interestCap), Money.format(loan.originationFee),
                Money.format(loan.originalPrincipal - loan.originationFee), "" + loan.defaultMissedPayments,
                loan.graceMillis == 0 ? "no" : DisplayText.duration(loan.graceMillis), originalAuto,
                DisplayText.yesNo(loan.consentBalanceSeizure), loan.collateral == null ? "None - unsecured" : DisplayText.chunk(loan.collateral),
                DisplayText.yesNo(loan.consentRepossession)).fallback();
    }

    static String registry(String id) {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return DisplayText.words(id.replace('.', ' '));
        String item = DisplayText.words(parts[1].replace('/', ' ').replace('.', ' '));
        return parts[0].equals("minecraft") ? item : item + " (" + DisplayText.words(parts[0]) + ")";
    }

    String item(ItemLot lot) {
        String base = registry(lot.item());
        try {
            JsonObject data = tags(lot);
            JsonObject tag = lot.fullStackData() && data.has("tag") ? data.getAsJsonObject("tag") : data;
            if (tag.has("display") && tag.getAsJsonObject("display").has("Name")) {
                String custom = component(tag.getAsJsonObject("display").get("Name").getAsString());
                if (!custom.isBlank()) return shorten(custom, 120) + " (" + shorten(base, 96) + ")";
            }
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException ignored) { }
        return shorten(base, 220);
    }

    String itemDetails(ItemLot lot) {
        if (lot.snbt().isEmpty()) return "No custom item data. Stack limit: " + lot.maxStackSize() + ".";
        try {
            String summary = attributes(tags(lot), 0);
            return shorten((summary.isEmpty() ? "Custom item data preserved" : summary)
                    + "; stack limit: " + lot.maxStackSize() + ". All custom data is preserved.", 1800);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException invalid) {
            return "Custom item data preserved; attributes could not be summarized. Inspect the item before purchase. Stack limit: "
                    + lot.maxStackSize() + ".";
        }
    }

    private static JsonObject tags(ItemLot lot) {
        return lot.snbt().isEmpty() ? new JsonObject() : new Snbt(lot.snbt()).read().getAsJsonObject();
    }

    private String attributes(JsonElement value, int depth) {
        if (depth > 12) return "Additional nested item data preserved";
        if (value.isJsonArray()) {
            List<String> entries = new ArrayList<>();
            for (JsonElement entry : value.getAsJsonArray()) entries.add(attributes(entry, depth + 1));
            return String.join("; ", entries);
        }
        if (value.isJsonObject()) {
            List<String> entries = new ArrayList<>();
            for (var entry : value.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if (key.toLowerCase(Locale.ROOT).contains("uuid")) continue;
                String label = switch (key) {
                    case "tag", "display" -> "";
                    case "ForgeCaps" -> "Mod attributes";
                    case "lvl" -> "Level";
                    case "id" -> "Type";
                    case "Damage" -> "Durability used";
                    case "HideFlags" -> "Tooltip settings";
                    default -> registry(key);
                };
                String detail = attributes(entry.getValue(), depth + 1);
                if (!detail.isBlank()) entries.add(label.isEmpty() ? detail : label + ": " + detail);
            }
            return String.join("; ", entries);
        }
        String text = value.getAsString();
        if (text.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?[bBsSlLfFdD]")) return text.substring(0, text.length() - 1);
        if (UUID.matcher(text).matches()) return "Stored reference";
        if (text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            return text.matches("(player|company|bank|nation|state|city):.+") ? account(text) : registry(text);
        }
        return component(text);
    }

    private static String component(String text) {
        String stripped = text.strip();
        if (!stripped.startsWith("{") && !stripped.startsWith("[") && !stripped.startsWith("\"")) return text;
        try { return component(JsonParser.parseString(stripped), 0); }
        catch (JsonParseException | IllegalArgumentException | IllegalStateException invalid) { return "Custom text"; }
    }

    private static String component(JsonElement element, int depth) {
        if (depth > 12 || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        StringBuilder text = new StringBuilder();
        if (element.isJsonArray()) {
            for (JsonElement part : element.getAsJsonArray()) text.append(component(part, depth + 1));
        } else {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) text.append(object.get("text").getAsString());
            else if (object.has("translate")) text.append(registry(object.get("translate").getAsString()));
            if (object.has("with")) text.append(" ").append(component(object.get("with"), depth + 1));
            if (object.has("extra")) text.append(component(object.get("extra"), depth + 1));
        }
        return text.toString();
    }

    private static String name(String value, String unavailable) { return shorten(DisplayText.name(value, unavailable), 96); }

    /** Bounded, read-only SNBT projection; it is never used to decode or rewrite escrow. */
    private static final class Snbt {
        private final String text;
        private int position;
        Snbt(String text) { this.text = text; }
        JsonElement read() {
            JsonElement value = value(0);
            whitespace();
            if (position != text.length()) throw new IllegalArgumentException("Trailing item data");
            return value;
        }
        private JsonElement value(int depth) {
            whitespace();
            if (depth > 24 || position >= text.length()) throw new IllegalArgumentException("Unsupported item data");
            char next = text.charAt(position++);
            if (next == '{') {
                JsonObject result = new JsonObject();
                whitespace();
                while (!take('}')) {
                    String key = token(true);
                    if (!take(':')) throw new IllegalArgumentException("Missing item attribute");
                    result.add(key, value(depth + 1));
                    if (take('}')) return result;
                    if (!take(',')) throw new IllegalArgumentException("Missing item separator");
                }
                return result;
            }
            if (next == '[') {
                JsonArray result = new JsonArray();
                whitespace();
                if (position + 1 < text.length() && "BIL".indexOf(text.charAt(position)) >= 0 && text.charAt(position + 1) == ';') position += 2;
                while (!take(']')) {
                    result.add(value(depth + 1));
                    if (take(']')) return result;
                    if (!take(',')) throw new IllegalArgumentException("Missing item separator");
                }
                return result;
            }
            position--;
            return new JsonPrimitive(token(false));
        }
        private String token(boolean key) {
            whitespace();
            if (position >= text.length()) throw new IllegalArgumentException("Missing item value");
            char quote = text.charAt(position);
            if (quote == '"' || quote == '\'') {
                position++;
                StringBuilder value = new StringBuilder();
                while (position < text.length()) {
                    char next = text.charAt(position++);
                    if (next == quote) return value.toString();
                    if (next == '\\' && position < text.length()) next = text.charAt(position++);
                    value.append(next);
                }
                throw new IllegalArgumentException("Unterminated item text");
            }
            int start = position;
            while (position < text.length()) {
                char next = text.charAt(position);
                if (Character.isWhitespace(next) || next == ',' || next == '}' || next == ']' || key && next == ':') break;
                position++;
            }
            if (start == position) throw new IllegalArgumentException("Missing item token");
            return text.substring(start, position);
        }
        private boolean take(char expected) {
            whitespace();
            if (position >= text.length() || text.charAt(position) != expected) return false;
            position++;
            return true;
        }
        private void whitespace() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
    }
}
