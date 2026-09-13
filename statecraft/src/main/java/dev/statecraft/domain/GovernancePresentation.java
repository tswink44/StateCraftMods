package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiProvider;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiRow;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import dev.statecraft.domain.GovernanceData.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.statecraft.domain.GovernanceEngine.check;

/** Bounded server-side projections. Only opening an authorized mail copy marks it read. */
public final class GovernancePresentation implements UiProvider {
    private static final Set<String> LIVE_CONTRACTS = Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED");
    private static final Set<String> LIVE_BILLS = Set.of("DEBATE", "VOTING", "PASSED", "VETOED", "OVERRIDE_VOTING");
    private static final Set<String> LIVE_DIPLOMACY = Set.of("PROPOSED", "AWAITING_RATIFICATION", "READY");
    private final GovernanceEngine e;

    public GovernancePresentation(GovernanceEngine engine) {
        e = Objects.requireNonNull(engine, "engine");
        CoreMenus.register();
    }

    @Override
    public UiView view(UiContext context) {
        Objects.requireNonNull(context, "context");
        synchronized (e) {
            check("statecraft".equals(context.query().namespace()), "This is not a governance view.");
            MenuRegistry.get(context.query().page());
            return new Request(context.actor(), context.query()).view();
        }
    }

    @Override
    public UiView dashboard(Actor actor, String search, int offset) {
        return view(new UiContext(actor, new UiQuery("statecraft:dashboard", EntityRef.NONE, search, offset)));
    }

    @Override
    public String queryText(Actor actor, ActionSelection selection, String commandResult) {
        if (selection.template().isEmpty() || !"statecraft".equals(selection.namespace())
                || selection.intent() != ActionIntent.QUERY) return commandResult;
        synchronized (e) {
            try {
                return new QueryRequest(actor, new Arguments(CommandLine.split(selection.rendered())), commandResult).text();
            } catch (UserError unavailable) {
                return commandResult;
            }
        }
    }

    public ActionPreview preview(Actor actor, ActionSelection selection) {
        synchronized (e) {
            return new GovernancePreviews(e).preview(actor, selection);
        }
    }

    static UiText t(String key, String template, Object... arguments) {
        String[] values = Stream.of(arguments).map(value -> clip(Objects.toString(value, ""), 2048)).toArray(String[]::new);
        return UiText.tr("ui.statecraft.gov." + key, String.format(Locale.ROOT, template, (Object[]) values), values);
    }

    static String clip(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 3)) + "...";
    }

    static String label(String value) {
        return clip(value, 128).replaceAll("\\p{Cntrl}", " ");
    }

    static String actionKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    static String recordedName(String name, String unavailable) {
        return name == null || name.isBlank() ? unavailable : name;
    }

    static String person(GovernanceEngine engine, String id) {
        if (id == null) return "None";
        Player person = engine.data.players.get(id);
        return recordedName(person == null || id.equals(person.name) ? null : person.name, "Former player");
    }

    static String governmentName(GovernanceEngine engine, String id, Kind kind) {
        if (id == null) return "None";
        Government government = engine.data.governments.get(id);
        return recordedName(government == null ? null : government.name,
                "Former " + (kind == null ? "government" : kind.name().toLowerCase(Locale.ROOT)));
    }

    static String governmentLabel(GovernanceEngine engine, String id, Kind kind) {
        if (id == null) return "None";
        Government government = engine.data.governments.get(id);
        if (government == null) return governmentName(engine, id, kind);
        String level = government.kind == null ? "Government" : DisplayText.words(government.kind.name());
        String parent = government.parentId == null ? "" : "; "
                + governmentName(engine, government.parentId, government.kind == Kind.CITY ? Kind.STATE : Kind.NATION);
        return governmentName(engine, id, kind) + " (" + level + parent + ")";
    }

    static String companyName(GovernanceEngine engine, String id) {
        if (id == null) return "None";
        Company company = engine.data.companies.get(id);
        return recordedName(company == null ? null : company.name, "Former company");
    }

    static String account(GovernanceEngine engine, String value) {
        if (value == null || value.isBlank()) return "None";
        if ("system".equals(value)) return "StateCraft";
        if ("shareholders:at-execution".equals(value)) return "Shareholders at the time of payment";
        if (value.startsWith("escrow:contract:")) {
            Contract contract = engine.data.contracts.get(value.substring("escrow:contract:".length()));
            return contract == null ? "Former contract escrow"
                    : recordedName(contract.title, "Contract") + " (contract escrow)";
        }
        if (value.startsWith("system:")) return "Server fees";
        String[] parts = value.split(":", 2);
        if (parts.length != 2) return "Unavailable account";
        return switch (parts[0]) {
            case "player" -> person(engine, parts[1]) + " (player)";
            case "company" -> companyName(engine, parts[1]) + " (company)";
            case "nation" -> governmentLabel(engine, parts[1], Kind.NATION);
            case "state" -> governmentLabel(engine, parts[1], Kind.STATE);
            case "city" -> governmentLabel(engine, parts[1], Kind.CITY);
            case "government" -> governmentLabel(engine, parts[1], null);
            default -> "Unavailable account";
        };
    }

    static String chunkName(String key) {
        if (key == null) return "Unavailable territory";
        try { return DisplayText.chunk(key); }
        catch (UserError | IllegalArgumentException invalid) { return "Unavailable territory"; }
    }

    static String territoryScope(GovernanceEngine engine, String nationId, String stateId, String cityId) {
        return (nationId == null ? "Unavailable nation" : governmentName(engine, nationId, Kind.NATION))
                + "; state: " + (stateId == null ? "Unassigned" : governmentName(engine, stateId, Kind.STATE))
                + "; city: " + (cityId == null ? "Unassigned" : governmentName(engine, cityId, Kind.CITY));
    }

    static String policyName(String key) {
        if (key == null) return "Unavailable policy";
        return switch (key) {
            case "incomeTaxBps" -> "Income tax";
            case "salesTaxBps" -> "Sales tax";
            case "propertyTaxBps" -> "Property tax";
            case "tariffBps" -> "Tariff";
            case "corporateTaxBps" -> "Corporate tax";
            case "baseChunkValue" -> "Base land value";
            case "foreignAccess" -> "Foreign access";
            case "alliedAccess" -> "Allied access";
            case "enemyAccess" -> "Enemy access";
            case "memberAccess" -> "Member access";
            case "pvp" -> "Player combat (PvP)";
            case "explosions" -> "Explosions";
            case "foreignProperty" -> "Foreign property ownership";
            case "open" -> "Open membership";
            case "friendlyFire" -> "Friendly fire";
            case "citizenLegislature" -> "Citizen legislature";
            case "peaceRatification" -> "Peace treaty ratification";
            case "roleplay" -> "Roleplay only";
            case "treaty" -> "Treaty ratification";
            default -> "Unavailable policy";
        };
    }

    static String policyValue(GovernanceEngine engine, String key, String value) {
        if (value == null) return "Unavailable";
        if ("inherit".equals(value)) return "Inherited / server default";
        if ("roleplay".equals(key)) return "Text only";
        if ("treaty".equals(key)) {
            DiplomaticProposal treaty = engine.data.diplomacy.get(value);
            return treaty == null ? "Former treaty" : governmentName(engine, treaty.fromNation, Kind.NATION)
                    + " → " + governmentName(engine, treaty.toNation, Kind.NATION);
        }
        try {
            if (key != null && GovernanceSettings.RATES.contains(key)) return DisplayText.percent(Integer.parseInt(value));
            if ("baseChunkValue".equals(key)) return Money.format(Long.parseLong(value));
        } catch (IllegalArgumentException invalid) {
            return "Unavailable";
        }
        if ("true".equals(value)) return "On";
        if ("false".equals(value)) return "Off";
        return "Unavailable";
    }

    static String decisionName(String type) {
        if (type == null) return "Unavailable decision";
        return switch (type) {
            case "owner" -> "Change manager";
            case "dividend" -> "Pay dividend";
            case "roleplay" -> "Roleplay only";
            case "dissolve" -> "Dissolve company";
            default -> "Unavailable decision";
        };
    }

    static String permissionName(String action) {
        return switch (Objects.toString(action, "").toUpperCase(Locale.ROOT)) {
            case "ALL" -> "All non-player-combat actions";
            case "NONE" -> "Remove permit";
            case "BREAK" -> "Break blocks";
            case "PLACE" -> "Place blocks";
            case "BLOCK_INTERACT" -> "Use blocks and containers";
            case "ENTITY_INTERACT" -> "Interact with entities";
            case "ATTACK" -> "Attack non-player entities";
            default -> "Unavailable permission";
        };
    }

    static String decisionValue(GovernanceEngine engine, String type, String value) {
        if ("owner".equals(type)) return person(engine, value);
        if ("dividend".equals(type)) {
            try { return Money.format(Long.parseLong(value)); }
            catch (IllegalArgumentException invalid) { return "Unavailable amount"; }
        }
        if ("roleplay".equals(type)) return "Text only";
        if ("dissolve".equals(type)) return "Company and its invitations";
        return "Unavailable";
    }

    static String policyTitle(GovernanceEngine engine, String policy, String value, String title) {
        return "treaty".equals(policy) && GovernanceEngine.validUuid(value) && ("Ratify peace " + value.substring(0, 8)).equals(title)
                ? "Ratify peace: " + policyValue(engine, policy, value) : title;
    }

    static String policyBody(GovernanceEngine engine, String policy, String value, String body) {
        return "treaty".equals(policy) && ("Ratify the immutable terms of treaty " + value + ".").equals(body)
                ? "Ratify the agreed peace terms: " + policyValue(engine, policy, value) + "." : body;
    }

    static String mailBody(GovernanceEngine engine, Mail message) {
        String body = Objects.toString(message.body, "");
        if (!"system".equals(message.sender)) return body;
        String uuid = "([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})";
        // Match only known notification slots; names, titles, reasons and player-written mail stay untouched.
        return switch (Objects.toString(message.subject, "")) {
            case "Government invitation" -> notice(body, "(You are invited to .+)\\. Use (?:nation|state|city) accept \"?" + uuid + "\"?\\. Expires ([0-9]+)\\.",
                    m -> m.group(1) + ". Open Invitations to respond. Expires " + dateText(m.group(3)) + ".");
            case "Company invitation" -> notice(body, "(Join .+) using company accept " + uuid + "\\.",
                    m -> m.group(1) + ". Open Invitations to respond.");
            case "Election voting" -> notice(body, "Voting is open until ([0-9]+)\\.",
                    m -> "Voting is open until " + dateText(m.group(1)) + ".");
            case "Election results" -> notice(body, "Winner: (.*) \\[" + uuid + "\\], votes=([0-9]+)/([0-9]+)\\. Ties use ascending candidate UUID\\.",
                    m -> "Winner: " + m.group(1) + ", votes: " + m.group(3) + " of " + m.group(4)
                            + ". Ties use the election's fixed candidate order.");
            case "Bill introduced" -> notice(body, "(.*) \\[" + uuid + "\\]\\. Debate ends at ([0-9]+)\\.",
                    m -> noticeBillTitle(engine, m.group(2), m.group(1)) + ". Debate ends " + dateText(m.group(3)) + ".");
            case "Bill passed" -> notice(body, "(.*) \\[" + uuid + "\\] awaits signature before ([0-9]+)\\.",
                    m -> noticeBillTitle(engine, m.group(2), m.group(1)) + " awaits signature before " + dateText(m.group(3)) + ".");
            case "Bill vetoed" -> notice(body, "(.*)\\. An override may be moved before ([0-9]+)\\.",
                    m -> m.group(1) + ". An override may be moved before " + dateText(m.group(2)) + ".");
            case "Emergency order" -> notice(body, "([A-Za-z]+)=([^ ]+) until ([0-9]+)\\. (.*)",
                    m -> policyName(m.group(1)) + ": " + policyValue(engine, m.group(1), m.group(2))
                            + " until " + dateText(m.group(3)) + ". " + m.group(4));
            case "Law enacted" -> notice(body, "(.*) \\(([A-Za-z]+)=([^)]*)\\)\\.",
                    m -> m.group(1) + " (" + policyName(m.group(2)) + ": " + policyValue(engine, m.group(2), m.group(3)) + ").");
            case "Contract opened" -> notice(body, "(.*) \\[" + uuid + "\\]\\. Bidding ends ([0-9]+)\\.",
                    m -> m.group(1) + ". Bidding ends " + dateText(m.group(3)) + ".");
            case "Contract awarded" -> {
                String personal = notice(body, "(.*) \\[" + uuid + "\\] was awarded to you\\. Submit work for independent review\\.",
                        m -> m.group(1) + " was awarded to you. Submit work for independent review.");
                yield personal.equals(body) ? notice(body, uuid + " awarded to (.*)",
                        m -> contractName(engine, m.group(1)) + " awarded to " + m.group(2)) : personal;
            }
            case "Contract work submitted" -> notice(body, "(.*) \\[" + uuid + "\\] awaits independent completion approval\\.",
                    m -> m.group(1) + " awaits independent completion approval.");
            case "Contract completed" -> notice(body, "(.*) \\[" + uuid + "\\] paid (.*)",
                    m -> m.group(1) + " paid " + m.group(3));
            case "Alliance proposal" -> notice(body, "(.* proposes an alliance\\.) Proposal " + uuid + "\\. (.*)",
                    m -> m.group(1) + " Review the proposal in Diplomacy. " + m.group(3));
            case "Peace proposal" -> notice(body, "(.* proposes peace): " + uuid + "\\. Offered ([^,]+), demanded ([^,]+), chunks=([0-9]+)\\. (.*)",
                    m -> m.group(1) + ". Offered " + m.group(3) + ", demanded " + m.group(4)
                            + ", territory: " + m.group(5) + " chunks. " + m.group(6));
            case "Alliance formed" -> notice(body, "Alliance proposal " + uuid + " was accepted\\.",
                    m -> "The alliance proposal was accepted.");
            case "Peace awaits ratification" -> notice(body, "Proposal " + uuid + " was accepted by the executives\\. Both legislatures must now ratify before expiry\\.",
                    m -> "The peace proposal was accepted by the executives. Both legislatures must now ratify before expiry.");
            case "Peace enacted" -> notice(body, "Treaty " + uuid + " is settled\\. A binding truce lasts until ([0-9]+)\\.",
                    m -> "The treaty is settled. A binding truce lasts until " + dateText(m.group(2)) + ".");
            case "Treaty ratification" -> notice(body, "(.*) ratified treaty " + uuid + "\\.",
                    m -> m.group(1) + " ratified the treaty.");
            case "Treaty settlement blocked" -> notice(body, "Treaty " + uuid + " is ratified but not enacted: (.*)\\. An executive may retry with diplomacy execute before expiry\\.",
                    m -> "The treaty is ratified but not enacted: " + issue(m.group(2)) + " An executive may retry settlement in Diplomacy before expiry.");
            case "Diplomatic proposal cancelled", "Diplomatic proposal rejected", "Diplomatic proposal expired", "Diplomatic proposal failed" -> {
                String initiated = notice(body, uuid + ": By " + uuid,
                        m -> "Proposal concluded by " + person(engine, m.group(2)) + ".");
                yield initiated.equals(body) ? notice(body, uuid + ": (.*)",
                        m -> "Proposal concluded: " + issue(m.group(2))) : initiated;
            }
            default -> body;
        };
    }

    private static String notice(String body, String pattern, Function<Matcher, String> display) {
        Matcher match = Pattern.compile(pattern, Pattern.DOTALL).matcher(body);
        return match.matches() ? display.apply(match) : body;
    }

    private static String dateText(String value) {
        try { return DisplayText.date(Long.parseLong(value)); }
        catch (NumberFormatException invalid) { return "Date unavailable"; }
    }

    private static String contractName(GovernanceEngine engine, String id) {
        Contract contract = engine.data.contracts.get(id);
        return recordedName(contract == null ? null : contract.title, "Former contract");
    }

    private static String noticeBillTitle(GovernanceEngine engine, String id, String title) {
        Bill bill = engine.data.bills.get(id);
        return bill == null ? title : policyTitle(engine, bill.policy, bill.value, title);
    }

    static String issue(String message) {
        if (message == null || message.isBlank()) return "None";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("insufficient") || lower.contains("not enough funds")) return "There are not enough funds in the required account.";
        if (lower.contains("economy") && lower.contains("unavailable")) return "The economy is currently unavailable.";
        if (lower.contains("must be true or false") || lower.contains("basis points") || lower.contains("unknown policy"))
            return "Choose a supported policy and a value within its allowed range.";
        if (message.matches("(?s).*(?i:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|(?:player|company|nation|state|city|escrow|system):|\\|[-0-9]+\\|[-0-9]+).*"))
            return "This action is blocked by the current records or commitments. Ask an operator to review it.";
        return message;
    }

    static EntityRef ref(EntityRef.Kind kind, String id) {
        if (kind == EntityRef.Kind.CLAIM && (id == null || id.length() > 256)) return EntityRef.NONE;
        return new EntityRef("statecraft", kind, id);
    }

    static void identity(String id, String recorded, String type) {
        check(GovernanceEngine.validUuid(id) && id.equals(recorded), "Unknown or invalid " + type + " reference.");
    }

    static String mailId(String owner, boolean sent, Mail message) {
        return owner + "|" + (sent ? "sent" : "inbox") + "|" + message.id;
    }

    record MailEntry(String owner, boolean sent, Mail message, Government government) {}

    static MailEntry mail(GovernanceEngine engine, Actor actor, String id) {
        String[] parts = id.split("\\|", -1);
        check(parts.length == 3 && Set.of("inbox", "sent").contains(parts[1])
                && GovernanceEngine.validUuid(parts[2]), "Invalid mailbox reference.");
        String owner = parts[0];
        boolean sent = "sent".equals(parts[1]);
        List<Mail> box;
        Government government = null;
        if (owner.startsWith("player:")) {
            check(owner.equals(actor.account()), "Personal mail is private, including from operators.");
            Player player = engine.requirePlayer(actor.id());
            box = sent ? player.sent : player.inbox;
        } else {
            String[] account = owner.split(":", -1);
            check(account.length == 2 && GovernanceEngine.validUuid(account[1]), "Invalid official mailbox.");
            government = engine.gov(account[1]);
            check(owner.equals(engine.account(government)), "Invalid official mailbox account.");
            engine.manage(actor, government);
            box = sent ? government.sent : government.inbox;
        }
        Mail message = box.stream().filter(Objects::nonNull)
                .filter(m -> parts[2].equals(m.id) && owner.equals(sent ? m.sender : m.recipient))
                .findFirst().orElseThrow(() -> new UserError("That copy is not in this authorized mailbox."));
        return new MailEntry(owner, sent, message, government);
    }

    private record QueryRow(String raw, String display) {}

    private final class QueryRequest {
        private final Actor actor;
        private final Arguments args;
        private final String original;

        QueryRequest(Actor actor, Arguments args, String original) {
            this.actor = actor;
            this.args = args;
            this.original = original;
        }

        String text() {
            return switch (args.get(0)) {
                case "nation" -> governments(Kind.NATION);
                case "state" -> governments(Kind.STATE);
                case "city" -> governments(Kind.CITY);
                case "government" -> governments(null);
                case "chunk" -> chunks();
                case "election" -> elections();
                case "bill" -> bills();
                case "law" -> laws();
                case "executive" -> emergency();
                case "company" -> companies();
                case "contract" -> contracts();
                case "diplomacy" -> diplomacy();
                case "mail" -> mailbox();
                case "profile" -> profile();
                default -> original;
            };
        }

        private String governments(Kind kind) {
            String action = args.optional(1, "list");
            if ("list".equals(action)) return page("Governments", e.data.governments.values().stream()
                    .filter(e::viewableGovernment).filter(g -> kind == null || g.kind == kind)
                    .sorted(Comparator.comparing(g -> g.name)).map(g -> new QueryRow(
                            g.kind + " " + g.name + " [" + g.id + "] leader=" + e.playerName(g.leader) + " citizens=" + e.members(g).size(),
                            governmentLabel(e, g.id, g.kind) + "; leader: " + person(e, g.leader) + "; citizens: " + e.members(g).size())).toList(), args.page(2));
            Government g = args.size() > 2 ? e.gov(args.get(2)) : e.ownGovernment(actor, kind);
            return switch (action) {
                case "info" -> scalar(e.governmentInfo(g), governmentLabel(e, g.id, g.kind)
                        + "\nLeader: " + person(e, g.leader) + "\nParent: " + governmentName(e, g.parentId, g.kind == Kind.CITY ? Kind.STATE : Kind.NATION)
                        + "\nCitizens: " + e.members(g).size() + "\nOfficers: " + g.officers.size()
                        + "\nTerritory: " + e.data.claims.values().stream().filter(c -> e.claimInGovernment(c, g)).count() + " chunk(s)"
                        + "\nTag: " + g.tag + "\nFlag: " + g.flag + "\nDescription: " + g.description + "\nTreasury: " + account(e, e.account(g)));
                case "settings" -> {
                    Map<String, String> settings = e.settings(g);
                    String raw = g.name + " effective settings:\n" + settings.entrySet().stream()
                            .map(s -> s.getKey() + "=" + s.getValue() + (g.settings.containsKey(s.getKey()) ? " (local)" : " (default/inherited)"))
                            .collect(Collectors.joining("\n"));
                    yield scalar(raw, g.name + " — current policies\n" + settings.entrySet().stream()
                            .map(s -> policyName(s.getKey()) + ": " + policyValue(e, s.getKey(), s.getValue())
                                    + (g.settings.containsKey(s.getKey()) ? " (local setting recorded)" : " (no local setting)"))
                            .collect(Collectors.joining("\n")));
                }
                case "members", "roles", "officers" -> page(g.name + " members", e.members(g).stream().sorted()
                        .filter(id -> !"officers".equals(action) || !"citizen".equals(e.role(id, g)))
                        .map(id -> new QueryRow(e.playerName(id) + " [" + id + "] " + e.role(id, g),
                                person(e, id) + " — " + DisplayText.words(e.role(id, g)))).toList(), args.page(3));
                case "invitations" -> page("Invitations", e.data.invitations.values().stream().filter(Objects::nonNull)
                        .filter(i -> g.id.equals(i.governmentId) && (e.canManage(actor, g) || actor.id().toString().equals(i.playerId)))
                        .map(i -> new QueryRow(e.playerName(i.playerId) + " [" + i.id + "] expires=" + i.expiresAt,
                                person(e, i.playerId) + " — expires " + DisplayText.date(i.expiresAt))).toList(), args.page(3));
                default -> original;
            };
        }

        private String chunks() {
            String action = args.optional(1, "info");
            if ("list".equals(action)) {
                Government government = "all".equals(args.optional(2, "all")) ? null : e.gov(args.get(2));
                return page("Claims", e.data.claims.values().stream().filter(e::viewableClaim)
                        .filter(c -> government == null || e.claimInGovernment(c, government)).sorted(Comparator.comparing(c -> c.key))
                        .map(c -> new QueryRow(c.key + " nation=" + e.governmentName(c.nationId) + " state=" + e.governmentName(c.stateId)
                                        + " city=" + e.governmentName(c.cityId) + " owner=" + c.ownerAccount + " improvements=" + c.improvements,
                                chunkName(c.key) + "; nation: " + territoryScope(e, c.nationId, c.stateId, c.cityId)
                                        + "; private title: " + account(e, c.ownerAccount) + "; improvements: " + c.improvements)).toList(), args.page(3));
            }
            if ("map".equals(action)) {
                String prefix = "Map " + actor.dimension() + " (north up):";
                return original.startsWith(prefix) ? "Map " + DisplayText.dimension(actor.dimension()) + " (north up):"
                        + original.substring(prefix.length()) : original;
            }
            if (!Set.of("info", "permits", "protection").contains(action)) return original;
            String key = e.chunkKey(actor, args.optional(2, "here"));
            Claim claim = e.data.claims.get(key);
            if ("info".equals(action)) {
                String raw = e.chunkInfo(key);
                if (claim == null && !e.data.claims.containsKey(key)) return scalar(raw, chunkName(key) + ": wilderness.");
                if (!e.viewableClaim(claim)) return scalar(raw, chunkName(key)
                        + ": invalid claim; protections remain enforced. Ask an operator to review it.");
                return scalar(raw, detailText(EntityRef.Kind.CLAIM, key));
            }
            if ("permits".equals(action)) {
                claim = e.requiredClaim(key);
                check(actor.admin() || e.mayAccessAccount(actor.id(), claim.ownerAccount), "Only the owner may list permits.");
                return page("Chunk permits", claim.permits.entrySet().stream().filter(v -> v.getValue() != null)
                        .map(v -> new QueryRow(e.playerName(v.getKey()) + ": " + String.join(",", v.getValue()),
                                person(e, v.getKey()) + ": " + v.getValue().stream().map(GovernancePresentation::permissionName)
                                        .collect(Collectors.joining(", ")))).toList(), args.page(3));
            }
            if ("protection".equals(action)) {
                Player target = args.size() > 3 ? e.resolvePlayer(args.get(3)) : e.requirePlayer(actor.id());
                String prefix = "Protection for " + target.name + " at " + key + ":\n";
                String suffix = "\nPvP diagnostics use the requesting player as the opponent.";
                if (!original.startsWith(prefix) || !original.endsWith(suffix)) return original;
                String[] results = original.substring(prefix.length(), original.length() - suffix.length()).split("\n", -1);
                AccessAction[] actions = AccessAction.values();
                if (results.length != actions.length + 1) return original;
                List<String> lines = new ArrayList<>();
                for (int index = 0; index < results.length; index++) {
                    String flag = index == actions.length ? "explosions" : actions[index].name();
                    if (!results[index].equals(flag + "=true") && !results[index].equals(flag + "=false")) return original;
                    String name = "explosions".equals(flag) ? "Explosions" : "PVP".equals(flag) ? "Player combat" : permissionName(flag);
                    lines.add(name + ": " + (results[index].endsWith("=true") ? "Allowed" : "Not allowed"));
                }
                return "Protection for " + person(e, target.id) + " at " + chunkName(key) + ":\n" + String.join("\n", lines)
                        + "\nPlayer combat uses " + person(e, actor.id().toString()) + " as the opponent.";
            }
            return original;
        }

        private String elections() {
            String action = args.optional(1, "status");
            if ("list".equals(action)) return page("National elections", e.data.governments.values().stream().filter(e::viewableGovernment)
                    .filter(g -> g.kind == Kind.NATION).sorted(Comparator.comparing(g -> g.name)).map(g -> {
                        Election election = e.data.elections.get(g.id);
                        String raw = g.name + " [" + g.id + "] " + (election == null ? "Not scheduled"
                                : (election.voting ? "VOTING until " + election.endsAt : "REGISTRATION until " + election.nextStartAt)
                                + ", candidates=" + election.candidates.size());
                        return new QueryRow(raw, g.name + " — " + (election == null ? "Not scheduled"
                                : (election.voting ? "Voting until " + DisplayText.date(election.endsAt)
                                : "Registration until " + DisplayText.date(election.nextStartAt)) + "; candidates: " + election.candidates.size()));
                    }).toList(), args.page(2));
            Government nation = args.size() > 2 ? e.politics.nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
            Election election = e.data.elections.get(nation.id);
            if (election == null) return original;
            return switch (action) {
                case "status" -> scalar(nation.name + " [" + nation.id + "] election: " + (election.voting ? "VOTING" : "REGISTRATION")
                        + "\nNext start: " + election.nextStartAt + "\nVoting ends: " + election.endsAt
                        + "\nCandidates: " + election.candidates.size() + "\nBallots: " + election.votes.size()
                        + "\nSchedule exhausted: " + election.scheduleExhausted, detailText(EntityRef.Kind.ELECTION, nation.id));
                case "candidates" -> page("Candidates", election.candidates.stream().sorted()
                        .map(id -> new QueryRow(e.playerName(id) + " [" + id + "]", person(e, id))).toList(), args.page(3));
                case "history" -> history("Election history", election.history, true, args.page(3));
                default -> original;
            };
        }

        private String bills() {
            String action = args.optional(1, "list");
            if ("list".equals(action)) {
                Government nation = "all".equals(args.optional(2, "")) ? null
                        : args.size() > 2 ? e.politics.nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
                return page(nation == null ? "All national bills" : "Bills of " + nation.name,
                        e.data.bills.values().stream().filter(Objects::nonNull).filter(b -> nation == null || nation.id.equals(b.nationId))
                                .sorted(Comparator.comparingLong((Bill b) -> b.createdAt).reversed()
                                        .thenComparing(b -> b.id, Comparator.nullsFirst(Comparator.naturalOrder())))
                                .map(b -> new QueryRow(b.id + " " + b.status + " " + b.title + " [" + b.policy + "=" + b.value + "]"
                                        + " nation=" + e.governmentName(b.nationId) + " [" + b.nationId + "]",
                                        policyTitle(e, b.policy, b.value, b.title) + " — " + DisplayText.words(b.status)
                                                + "; nation: " + governmentName(e, b.nationId, Kind.NATION)
                                                + "; " + policyName(b.policy) + ": " + policyValue(e, b.policy, b.value))).toList(), args.page(3));
            }
            Bill bill = e.data.bills.get(args.get(2));
            if (bill == null) return original;
            Government nation = e.politics.nation(bill.nationId);
            if ("votes".equals(action)) return page("Legislative roll call", bill.votes.entrySet().stream()
                    .map(v -> new QueryRow(e.playerName(v.getKey()) + ": " + v.getValue(),
                            person(e, v.getKey()) + ": " + DisplayText.words(v.getValue()))).toList(), args.page(3));
            if ("history".equals(action)) return history("Bill history", bill.history, false, args.page(3));
            if (!Set.of("info", "status").contains(action)) return original;
            long yes = votes(bill, "yes"), no = votes(bill, "no"), abstain = votes(bill, "abstain");
            String raw = bill.id + ": " + bill.title + "\nStatus: " + bill.status + "\nNation: " + nation.name
                    + "\nPolicy: " + bill.policy + "=" + bill.value + "\nConstitutional amendment: " + bill.amendment
                    + "\nDebate ends: " + bill.debateEndsAt + "\nVote ends: " + bill.voteEndsAt + "\nDecision deadline: " + bill.decisionEndsAt
                    + "\nEligible=" + bill.electorate.size() + " yes=" + yes + " no=" + no + " abstain=" + abstain
                    + "\n" + bill.text + (bill.treatyId == null ? "" : "\nTreaty: " + bill.treatyId);
            return scalar(raw, policyTitle(e, bill.policy, bill.value, bill.title) + "\nStatus: " + DisplayText.words(bill.status)
                    + "\nNation: " + nation.name + "\nPolicy: " + policyName(bill.policy) + ": " + policyValue(e, bill.policy, bill.value)
                    + "\nConstitutional amendment: " + DisplayText.yesNo(bill.amendment)
                    + "\nDebate ends: " + DisplayText.date(bill.debateEndsAt) + "\nVoting ends: " + DisplayText.date(bill.voteEndsAt)
                    + "\nDecision deadline: " + DisplayText.date(bill.decisionEndsAt) + "\nEligible voters: " + bill.electorate.size()
                    + "; yes: " + yes + "; no: " + no + "; abstain: " + abstain + "\n" + policyBody(e, bill.policy, bill.value, bill.text));
        }

        private long votes(Bill bill, String choice) {
            return bill.votes.entrySet().stream().filter(v -> bill.electorate.contains(v.getKey()) && choice.equals(v.getValue())).count();
        }

        private String laws() {
            String action = args.optional(1, "list");
            boolean all = "list".equals(action) && "all".equals(args.optional(2, ""));
            Government nation = all ? null : args.size() > 2 ? e.politics.nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
            if ("list".equals(action)) {
                List<QueryRow> rows = new ArrayList<>();
                if (all) e.data.laws.forEach((id, codex) -> lawRows(rows, id, codex, true));
                else lawRows(rows, nation.id, e.data.laws.getOrDefault(nation.id, List.of()), false);
                return page(all ? "All national codices" : nation.name + " codex", rows, args.page(3));
            }
            if (!"read".equals(action)) return original;
            Law law = e.data.laws.getOrDefault(nation.id, List.of()).stream().filter(Objects::nonNull)
                    .filter(l -> args.get(3).equals(l.id)).findFirst().orElseThrow(() -> new UserError("Law unavailable."));
            String effect = law.roleplayOnly ? "Roleplay only; no mechanical effects." : "Mechanically enforced policy.";
            return scalar(law.title + "\nEnacted: " + law.enactedAt + "\n" + law.policy + "=" + law.value + "\n" + effect + "\n" + law.text,
                    policyTitle(e, law.policy, law.value, law.title) + "\nNation: " + nation.name
                            + "\nEnacted: " + DisplayText.date(law.enactedAt) + "\n" + policyName(law.policy) + ": " + policyValue(e, law.policy, law.value)
                            + "\n" + effect + "\n" + policyBody(e, law.policy, law.value, law.text));
        }

        private void lawRows(List<QueryRow> rows, String nation, List<Law> codex, boolean all) {
            List<Law> newest = new ArrayList<>(codex);
            Collections.reverse(newest);
            newest.stream().filter(Objects::nonNull).forEach(law -> {
                String policy = law.roleplayOnly ? "[ROLEPLAY ONLY]" : "[" + law.policy + "=" + law.value + "]";
                String raw = law.id + " " + law.title + (all ? " nation=" + e.governmentName(nation) + " [" + nation + "]" : "")
                        + " " + policy + (law.amendment ? " [AMENDMENT]" : "");
                rows.add(new QueryRow(raw, policyTitle(e, law.policy, law.value, law.title) + "; nation: " + governmentName(e, nation, Kind.NATION)
                        + "; " + policyName(law.policy) + ": " + policyValue(e, law.policy, law.value)
                        + (law.amendment ? "; constitutional amendment" : "")));
            });
        }

        private String emergency() {
            if (!"status".equals(args.get(1))) return original;
            Government nation = e.politics.nation(args.get(2));
            Emergency order = e.data.emergencies.get(nation.id);
            if (order == null) return scalar("No emergency order. Last use: " + nation.lastEmergencyAt,
                    "No emergency order. Last use: " + (nation.lastEmergencyAt < 0 ? "Never" : DisplayText.date(nation.lastEmergencyAt)));
            return scalar("Emergency " + order.policy + "=" + order.value + " expires=" + order.expiresAt + "\n" + order.reason,
                    "Emergency order for " + nation.name + "\n" + policyName(order.policy) + ": " + policyValue(e, order.policy, order.value)
                            + "\nExpires: " + DisplayText.date(order.expiresAt) + "\n" + order.reason);
        }

        private String companies() {
            String action = args.optional(1, "list");
            if ("list".equals(action)) return page("Companies", e.data.companies.values().stream().filter(e::viewableCompany)
                    .sorted(Comparator.comparing(c -> c.name)).map(c -> new QueryRow(c.name + " [" + c.id + "] owner=" + e.playerName(c.owner) + " shares=" + c.totalShares,
                            c.name + "; owner: " + person(e, c.owner) + "; issued shares: " + c.totalShares)).toList(), args.page(2));
            if ("proposals".equals(action)) {
                Company company = "all".equals(args.get(2)) ? null : e.companyRequired(args.get(2));
                return page(company == null ? "All shareholder proposals" : company.name + " proposals",
                        e.data.companyProposals.values().stream().filter(Objects::nonNull).filter(p -> company == null || company.id.equals(p.companyId))
                                .sorted(Comparator.comparingLong((CompanyProposal p) -> p.createdAt).reversed()
                                        .thenComparing(p -> p.id, Comparator.nullsFirst(Comparator.naturalOrder())))
                                .map(p -> new QueryRow(p.id + " " + p.status + " " + p.title + " [" + p.type + "=" + p.value + "]"
                                        + " company=" + e.companyName(p.companyId) + " [" + p.companyId + "]",
                                        p.title + " — " + DisplayText.words(p.status) + "; company: " + companyName(e, p.companyId)
                                                + "; " + decisionName(p.type) + ": " + decisionValue(e, p.type, p.value))).toList(), args.page(3));
            }
            if ("proposal".equals(action)) {
                CompanyProposal p = e.data.companyProposals.get(args.get(2));
                if (p == null) return original;
                long eligible = e.commerce.validateBallot(p), yes = e.commerce.weightedVotes(p, "yes"),
                        no = e.commerce.weightedVotes(p, "no"), abstain = e.commerce.weightedVotes(p, "abstain");
                long deadline = p.executionEndsAt == 0 ? GovernanceEngine.deadline(p.endsAt, e.config.companyVotingMillis) : p.executionEndsAt;
                return scalar(p.title + " [" + p.id + "] " + p.status + "\nCompany: " + e.companyName(p.companyId)
                                + "\nPolicy: " + p.type + "=" + p.value + "\nVoting ends: " + p.endsAt + "\nSettlement ends: " + deadline
                                + "\nEligible shares=" + eligible + " yes=" + yes + " no=" + no + " abstain=" + abstain
                                + "\n" + p.text + (p.lastError.isEmpty() ? "" : "\nBlocked: " + p.lastError),
                        p.title + " — " + DisplayText.words(p.status) + "\nCompany: " + companyName(e, p.companyId)
                                + "\nDecision: " + decisionName(p.type) + ": " + decisionValue(e, p.type, p.value)
                                + "\nVoting ends: " + DisplayText.date(p.endsAt) + "\nSettlement ends: " + DisplayText.date(deadline)
                                + "\nEligible shares: " + eligible + "; yes: " + yes + "; no: " + no + "; abstain: " + abstain
                                + "\n" + p.text + (p.lastError.isEmpty() ? "" : "\nBlocked: " + issue(p.lastError)));
            }
            Company company = e.companyRequired(args.get(2));
            return switch (action) {
                case "info" -> {
                    long owned = e.commerce.sharesOf(company.id, actor.id()), available = e.availableShares(company.id, actor.id());
                    yield scalar(company.name + " [" + company.id + "]\nOwner: " + e.playerName(company.owner)
                                    + "\nMembers: " + company.members.size() + "\nTotal shares: " + company.totalShares
                                    + "\nYour shares: " + owned + " (available: " + available + ")\nTreasury: company:" + company.id
                                    + "\nDescription: " + company.description,
                            company.name + "\nOwner: " + person(e, company.owner) + "\nMembers: " + company.members.size()
                                    + "\nTotal shares: " + company.totalShares + "\nYour shares: " + owned + " (available: " + available + ")"
                                    + "\nTreasury: " + account(e, "company:" + company.id) + "\nDescription: " + company.description);
                }
                case "members" -> page(company.name + " members", company.members.stream().sorted().map(id -> {
                    String role = id.equals(company.owner) ? "owner" : company.officers.contains(id) ? "officer" : "member";
                    return new QueryRow(e.playerName(id) + " [" + id + "] " + role, person(e, id) + " — " + DisplayText.words(role));
                }).toList(), args.page(3));
                case "shareholders" -> page(company.name + " shareholders", company.shares.entrySet().stream()
                        .filter(s -> s.getValue() != null && s.getValue() > 0).sorted(Map.Entry.comparingByKey())
                        .map(s -> new QueryRow(e.playerName(s.getKey()) + ": " + s.getValue(),
                                person(e, s.getKey()) + ": " + s.getValue() + " shares")).toList(), args.page(3));
                default -> original;
            };
        }

        private String contracts() {
            String action = args.optional(1, "list");
            if (Set.of("list", "my").contains(action)) {
                String reference = "my".equals(action) ? "all" : args.optional(2, "all");
                Government government = "all".equals(reference) ? null : e.gov(reference);
                return page("Government contracts", e.data.contracts.values().stream().filter(Objects::nonNull)
                        .filter(c -> government == null || government.id.equals(c.governmentId))
                        .filter(c -> !"my".equals(action) || c.bids.containsKey(actor.id().toString()) || actor.id().toString().equals(c.author))
                        .sorted(Comparator.comparingLong((Contract c) -> c.createdAt).reversed().thenComparing(c -> c.id))
                        .map(c -> new QueryRow(c.id + " " + c.status + " " + c.title + " government=" + e.governmentName(c.governmentId),
                                c.title + " — " + DisplayText.words(c.status) + "; issuer: " + governmentName(e, c.governmentId, null))).toList(),
                        args.page("my".equals(action) ? 2 : 3));
            }
            Contract c = e.data.contracts.get(args.get(2));
            if (c == null) return original;
            if ("bids".equals(action)) {
                e.manage(actor, e.gov(c.governmentId));
                return page("Bid review", c.bids.values().stream().filter(Objects::nonNull)
                        .sorted(Comparator.comparingLong((Bid b) -> b.cents).thenComparing(b -> b.bidder))
                        .map(b -> new QueryRow(e.playerName(b.bidder) + " [" + b.bidder + "] " + Money.format(b.cents) + " to=" + b.account + " " + b.text,
                                person(e, b.bidder) + " — " + Money.format(b.cents) + "; payee: " + account(e, b.account) + "\n" + b.text)).toList(), args.page(3));
            }
            if (!"info".equals(action)) return original;
            List<QueryRow> rows = new ArrayList<>();
            rows.add(new QueryRow(c.id + " " + c.status + " " + c.title, c.title + " — " + DisplayText.words(c.status)));
            rows.add(new QueryRow("Government: " + e.governmentName(c.governmentId) + "; author: " + e.playerName(c.author),
                    "Government: " + governmentName(e, c.governmentId, null) + "; author: " + person(e, c.author)));
            rows.add(new QueryRow(c.description, Objects.toString(c.description, "")));
            rows.add(new QueryRow("Bidding ends=" + c.bidEndsAt + ", review ends=" + c.reviewEndsAt + ", bids=" + c.bids.size(),
                    "Bidding ends: " + DisplayText.date(c.bidEndsAt) + "; review ends: " + DisplayText.date(c.reviewEndsAt) + "; bids: " + c.bids.size()));
            rows.add(new QueryRow("Winner=" + e.playerName(c.winner) + ", payee=" + c.payeeAccount + ", escrow=" + Money.format(c.escrowCents),
                    "Winner: " + person(e, c.winner) + "; payee: " + account(e, c.payeeAccount) + "; escrow: " + Money.format(c.escrowCents)));
            if (!c.completionNote.isEmpty()) rows.add(new QueryRow("Submission: " + c.completionNote, "Submission: " + c.completionNote));
            c.chunks.forEach(key -> rows.add(new QueryRow(key, chunkName(key))));
            return page("Contract details", rows, args.page(3));
        }

        private String diplomacy() {
            String action = args.optional(1, "status");
            if (Set.of("status", "proposals").contains(action)) {
                Government nation = "all".equals(args.optional(2, "")) ? null
                        : args.size() > 2 ? e.politics.nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
                List<QueryRow> rows;
                if ("status".equals(action)) rows = e.data.relations.values().stream().filter(Objects::nonNull)
                        .filter(r -> nation == null || nation.id.equals(r.first) || nation.id.equals(r.second))
                        .map(r -> new QueryRow(e.governmentName(r.first) + " [" + r.first + "] <-> " + e.governmentName(r.second)
                                + " [" + r.second + "]: " + r.status + ", truceUntil=" + r.truceUntil,
                                governmentName(e, r.first, Kind.NATION) + " ↔ " + governmentName(e, r.second, Kind.NATION)
                                        + ": " + DisplayText.words(r.status) + "; truce until: " + DisplayText.date(r.truceUntil))).toList();
                else rows = e.data.diplomacy.values().stream().filter(Objects::nonNull)
                        .filter(p -> nation == null || nation.id.equals(p.fromNation) || nation.id.equals(p.toNation))
                        .sorted(Comparator.comparingLong((DiplomaticProposal p) -> p.createdAt).reversed()
                                .thenComparing(p -> p.id, Comparator.nullsFirst(Comparator.naturalOrder())))
                        .map(p -> new QueryRow(p.id + " " + p.type + " " + p.status + " " + e.governmentName(p.fromNation) + " [" + p.fromNation
                                + "] -> " + e.governmentName(p.toNation) + " [" + p.toNation + "] expires=" + p.expiresAt,
                                DisplayText.words(p.type) + " — " + DisplayText.words(p.status) + "; " + governmentName(e, p.fromNation, Kind.NATION)
                                        + " → " + governmentName(e, p.toNation, Kind.NATION) + "; expires: " + DisplayText.date(p.expiresAt))).toList();
                return page(nation == null ? "Global diplomacy" : nation.name + " diplomacy", rows, args.page(3));
            }
            if (!Set.of("terms", "info").contains(action)) return original;
            DiplomaticProposal p = e.data.diplomacy.get(args.get(2));
            if (p == null) return original;
            List<QueryRow> rows = new ArrayList<>();
            rows.add(new QueryRow(p.id + " " + p.type + " " + p.status + " expires=" + p.expiresAt,
                    DisplayText.words(p.type) + " — " + DisplayText.words(p.status) + "; expires: " + DisplayText.date(p.expiresAt)));
            rows.add(new QueryRow(e.governmentName(p.fromNation) + " pays " + Money.format(p.offeredCents) + "; "
                    + e.governmentName(p.toNation) + " pays " + Money.format(p.demandedCents),
                    governmentName(e, p.fromNation, Kind.NATION) + " pays " + Money.format(p.offeredCents) + "; "
                            + governmentName(e, p.toNation, Kind.NATION) + " pays " + Money.format(p.demandedCents)));
            rows.add(new QueryRow("Ratified: " + p.ratified.stream().map(e::governmentName).collect(Collectors.joining(", ")),
                    "Ratified: " + (p.ratified.isEmpty() ? "None" : p.ratified.stream().map(id -> governmentName(e, id, Kind.NATION)).collect(Collectors.joining(", ")))));
            rows.add(new QueryRow(p.message, p.message));
            if (!p.lastError.isEmpty()) rows.add(new QueryRow("Execution blocked: " + p.lastError, "Execution blocked: " + issue(p.lastError)));
            for (ChunkTerm term : p.chunks) rows.add(term == null
                    ? new QueryRow("Invalid chunk term: operator repair required.", "A territorial transfer requires operator repair.")
                    : new QueryRow(term.key + ": " + e.governmentName(term.fromNation) + " -> " + e.governmentName(term.toNation)
                                    + " (state=" + e.governmentName(term.toState) + ", city=" + e.governmentName(term.toCity) + ")",
                            chunkName(term.key) + ": " + territoryScope(e, term.fromNation, term.fromState, term.fromCity)
                                    + " → " + territoryScope(e, term.toNation, term.toState, term.toCity)));
            e.data.bills.values().stream().filter(Objects::nonNull).filter(b -> p.id.equals(b.treatyId)).forEach(b ->
                    rows.add(new QueryRow("Ratification bill: " + e.governmentName(b.nationId) + " " + b.id + " " + b.status,
                            "Ratification bill: " + governmentName(e, b.nationId, Kind.NATION) + " — "
                                    + policyTitle(e, b.policy, b.value, b.title) + "; " + DisplayText.words(b.status))));
            return page("Diplomatic terms", rows, args.page(3));
        }

        private String mailbox() {
            boolean official = "official".equals(args.optional(1, ""));
            String action = args.optional(official ? 2 : 1, "inbox");
            int argument = official ? 4 : 2;
            Government government = official ? e.gov(args.get(3)) : null;
            if (official) e.manage(actor, government);
            Player player = e.requirePlayer(actor.id());
            String owner = official ? e.account(government) : actor.account();
            List<Mail> inbox = official ? government.inbox : player.inbox, sent = official ? government.sent : player.sent;
            if ("invitations".equals(action) && !official) return page("Your invitations",
                    e.data.invitations.values().stream().filter(Objects::nonNull).filter(i -> player.id.equals(i.playerId) && i.expiresAt > e.now())
                            .map(i -> new QueryRow(i.id + " " + (i.governmentId == null ? "Company " + e.companyName(i.companyId) : e.governmentName(i.governmentId))
                                    + " expires=" + i.expiresAt,
                                    (i.governmentId == null ? companyName(e, i.companyId) : governmentName(e, i.governmentId, null))
                                            + " — expires " + DisplayText.date(i.expiresAt))).toList(), args.page(2));
            if (Set.of("inbox", "sent").contains(action)) {
                boolean outgoing = "sent".equals(action);
                List<Mail> newest = new ArrayList<>(outgoing ? sent : inbox);
                Collections.reverse(newest);
                return page(official ? government.name + " " + action : action, newest.stream().filter(Objects::nonNull)
                        .filter(m -> owner.equals(outgoing ? m.sender : m.recipient))
                        .map(m -> new QueryRow((m.read ? "[read] " : "[unread] ") + m.id + " | " + m.subject
                                + " | from=" + m.sender + " to=" + m.recipient + " at=" + m.sentAt,
                                (m.read ? "Read — " : "Unread — ") + m.subject + "; from: " + account(e, m.sender)
                                        + "; to: " + account(e, m.recipient) + "; sent: " + DisplayText.date(m.sentAt))).toList(), args.page(argument));
            }
            if (!"read".equals(action)) return original;
            String id = args.get(argument);
            Mail message = inbox.stream().filter(Objects::nonNull).filter(m -> owner.equals(m.recipient) && id.equals(m.id)).findFirst()
                    .orElseGet(() -> sent.stream().filter(Objects::nonNull).filter(m -> owner.equals(m.sender) && id.equals(m.id)).findFirst().orElse(null));
            if (message == null) return original;
            return scalar(message.subject + "\nFrom: " + message.sender + "\nTo: " + message.recipient + "\nSent: " + message.sentAt + "\n" + message.body,
                    message.subject + "\nFrom: " + account(e, message.sender) + "\nTo: " + account(e, message.recipient)
                            + "\nSent: " + DisplayText.date(message.sentAt) + "\n" + mailBody(e, message));
        }

        private String profile() {
            Player player = args.size() == 2 ? e.resolvePlayer(args.get(1)) : e.requirePlayer(actor.id());
            List<String> rows = new ArrayList<>();
            rows.add(person(e, player.id));
            for (String id : new String[]{player.nationId, player.stateId, player.cityId}) {
                Government government = e.data.governments.get(id);
                if (e.validHierarchy(government)) rows.add(DisplayText.words(government.kind.name()) + ": " + government.name
                        + " (" + DisplayText.words(e.role(player.id, government)) + ")");
            }
            if (player.nationId == null) rows.add("Unaffiliated citizen");
            List<Company> companies = e.data.companies.values().stream().filter(e::viewableCompany)
                    .filter(c -> c.members.contains(player.id) || c.shares.getOrDefault(player.id, 0L) > 0).toList();
            rows.add("Companies: " + companies.size());
            companies.stream().limit(e.config.pageSize).forEach(c -> rows.add(c.name + (player.id.equals(c.owner) ? " (owner)" : "")
                    + "; shares: " + c.shares.getOrDefault(player.id, 0L)));
            if (player.id.equals(actor.id().toString())) rows.add("Unread personal mail: " + player.inbox.stream()
                    .filter(m -> m != null && ("player:" + player.id).equals(m.recipient) && !m.read).count());
            return scalar(e.communications.profile(actor, args), String.join("\n", rows));
        }

        private String history(String title, List<History> records, boolean election, int page) {
            List<History> newest = new ArrayList<>(records);
            Collections.reverse(newest);
            return page(title, newest.stream().filter(Objects::nonNull).map(h -> new QueryRow(
                    h.at + (election ? ": " : " ") + h.text, DisplayText.date(h.at) + " — " + historyEvent(h.text, election))).toList(), page);
        }

        private String historyEvent(String text, boolean election) {
            String uuid = "([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})";
            if (election) {
                Mail result = new Mail();
                result.sender = "system";
                result.subject = "Election results";
                result.body = text;
                text = mailBody(e, result);
            }
            text = notice(text, "(Candidate registered: |Candidate withdrew: |Introduced by |Veto override voting opened by |Cancelled by |Enacted: Signed by |Election cancelled by operator )"
                    + uuid + "(.*)", m -> m.group(1) + person(e, m.group(2)) + m.group(3));
            text = notice(text, "Vetoed by " + uuid + ": (.*)", m -> "Vetoed by " + person(e, m.group(1)) + ": " + m.group(2));
            text = notice(text, "Missed empty cycles skipped; next registration closes at ([0-9]+)\\.",
                    m -> "Missed empty cycles skipped; next registration closes " + dateText(m.group(1)) + ".");
            text = notice(text, "Voting opened; electorate=([0-9]+)", m -> "Voting opened; eligible voters: " + m.group(1));
            text = notice(text, "Ballot closed: Eligible=([0-9]+) yes=([0-9]+) no=([0-9]+) abstain=([0-9]+), quorum=(true|false)",
                    m -> "Ballot closed: eligible voters: " + m.group(1) + "; yes: " + m.group(2) + "; no: " + m.group(3)
                            + "; abstain: " + m.group(4) + "; quorum reached: " + ("true".equals(m.group(5)) ? "Yes" : "No"));
            text = notice(text, "Legislature ratified treaty " + uuid,
                    m -> "Legislature ratified the treaty: " + policyValue(e, "treaty", m.group(1)));
            text = notice(text, "Treaty concluded without settlement: ([A-Z_]+)",
                    m -> "Treaty concluded without settlement: " + DisplayText.words(m.group(1)));
            return text.startsWith("Invalid/unavailable policy: ")
                    ? "Policy unavailable: " + issue(text.substring("Invalid/unavailable policy: ".length())) : text;
        }

        private String scalar(String expected, String display) {
            return e.bounded(expected).equals(original) ? display : original;
        }

        private String detailText(EntityRef.Kind kind, String id) {
            UiView view = new Request(actor, UiQuery.detail(ref(kind, id))).view();
            return view.title().fallback() + "\n" + view.body().fallback();
        }

        private String page(String title, List<QueryRow> rows, int page) {
            String expected = e.page(title, rows.stream().map(QueryRow::raw).toList(), page);
            if (!e.bounded(expected).equals(original)) return original;
            // Preserve the original row window, including its output-budget-dependent page size.
            int budget = e.config.maxCommandOutput - Math.min(e.config.maxCommandOutput / 2, title.length() + 120);
            int widest = rows.stream().map(QueryRow::raw).filter(Objects::nonNull).mapToInt(String::length).max().orElse(0);
            int perPage = Math.min(e.config.pageSize, (int) Math.max(1, budget / Math.max(1L, (long) widest + 1)));
            int from = Math.min(rows.size(), (page - 1) * perPage), to = Math.min(rows.size(), from + perPage);
            return expected.substring(0, expected.indexOf('\n') + 1)
                    + (rows.isEmpty() ? "(none)" : rows.subList(from, to).stream().map(QueryRow::display).collect(Collectors.joining("\n")));
        }
    }

    private final class Request {
        private final Actor actor;
        private final UiQuery query;
        private final String player;

        Request(Actor actor, UiQuery query) {
            this.actor = Objects.requireNonNull(actor, "actor");
            this.query = query;
            player = actor.id().toString();
        }

        UiView view() {
            if (query.entity().present()) return detail(query.entity());
            String page = query.page().substring("statecraft:".length());
            return switch (page) {
                case "dashboard" -> pending();
                case "main", "detail" -> overview();
                case "nations" -> governments(g -> g.kind == Kind.NATION);
                case "states" -> governments(g -> g.kind == Kind.STATE);
                case "cities" -> governments(g -> g.kind == Kind.CITY);
                case "members", "officers" -> governments(g -> true);
                case "executive" -> governments(g -> g.kind == Kind.NATION);
                case "claims", "map" -> list(section(), t("claims.body", "Only nations claim land. State and city assignments are optional; private title remains separate. Select a claim for permissions and allocation actions."),
                        validClaims().filter(c -> !"map".equals(page) || c.key.startsWith(actor.dimension() + "|")).map(this::claimRow), pageActions());
                case "companies" -> list(section(), t("companies.body", "Employment, management authority and equity are separate."),
                        validCompanies().map(this::companyRow), pageActions());
                case "legislature" -> list(section(), t("bills.body", "National bills and their retained history. Local policies remain local executive actions."),
                        validBills().map(this::billRow), pageActions());
                case "elections" -> list(section(), t("elections.body", "National elections use a frozen electorate and require current citizenship to cast a ballot."),
                        validElections().map(this::electionRow), pageActions());
                case "laws" -> list(section(), t("laws.body", "Enacted laws remain readable even after their nation is dissolved."),
                        e.data.laws.entrySet().stream().flatMap(entry -> entry.getValue().stream().filter(Objects::nonNull)
                                .filter(l -> GovernanceEngine.validUuid(entry.getKey()) && GovernanceEngine.validUuid(l.id))
                                .map(l -> lawRow(entry.getKey(), l))), pageActions());
                case "diplomacy" -> list(section(), t("diplomacy.body", "Proposals, ratification and settlement. Only signatory executives may commit their nation."),
                        validDiplomacy().map(this::diplomacyRow), pageActions());
                case "shareholders" -> list(section(), t("shareholders.body", "Shareholder decisions retain the electorate fixed when voting opened."),
                        validCompanyProposals().map(this::companyProposalRow), pageActions());
                case "contracts" -> list(section(), t("contracts.body", "Contract summaries are public. Detailed bids require current issuing-government authority."),
                        validContracts().map(this::contractRow), pageActions());
                case "invitations" -> list(section(), t("invitations.body", "Invitations addressed to you or managed by an organization you currently represent."),
                        validInvitations().filter(this::canSeeInvitation).map(this::invitationRow), pageActions());
                case "profile" -> list(section(), t("profiles.body", "Public citizenship, offices and company participation. Personal mail counts remain private."),
                        validPlayers().map(this::playerRow), pageActions());
                case "mail" -> {
                    Player known = e.requirePlayer(actor.id());
                    Stream<UiRow> invitations = validInvitations().filter(invitation -> player.equals(invitation.playerId)
                            && invitation.expiresAt > e.now()).map(this::invitationRow);
                    yield list(section(), t("mail.body", "Your invitations, personal inbox and sent copies. Open an invitation to accept or decline."),
                            Stream.concat(invitations, Stream.concat(mailRows(actor.account(), known.inbox, false),
                                    mailRows(actor.account(), known.sent, true))), pageActions());
                }
                case "official_mail" -> list(section(), t("official_mail.body", "Only government mailboxes you currently manage are included."),
                        validGovernments().filter(this::manage).flatMap(g -> Stream.concat(
                                mailRows(e.account(g), g.inbox, false), mailRows(e.account(g), g.sent, true))), pageActions());
                case "admin" -> {
                    e.admin(actor);
                    yield list(section(), t("admin.body", "Audit and repair remain explicit operator decisions. Financial obligations are never guessed away."),
                            e.integrity.audit().stream().map(issue -> new UiRow(t("admin.issue", "Integrity review"),
                                    UiText.literal(clip(issue, 2048)), EntityRef.NONE)), pageActions());
                }
                case "help", "command_help" -> list(section(), t("help.body", "Select a registered help action for command syntax. Commands remain server-authoritative."),
                        Stream.of("overview", "governments", "chunks", "elections", "legislature", "executive", "diplomacy",
                                        "companies", "contracts", "mail", "profiles", "admin")
                                .flatMap(topic -> GovernanceHelp.lines(topic).stream().map(line -> new UiRow(t("help.topic", "Help: %s", topic),
                                        UiText.literal(clip(line, 2048)), EntityRef.NONE))), pageActions());
                default -> throw new UserError("This page is not a governance presentation page.");
            };
        }

        private UiView overview() {
            return list(t("overview.title", "StateCraft"),
                    t("overview.body", "Governments: %s\nClaims: %s\nCompanies: %s\nEconomy available: %s\nRepair required: %s",
                            e.data.governments.size(), e.data.claims.size(), e.data.companies.size(),
                            DisplayText.yesNo(e.economy.available()), DisplayText.yesNo(e.requiresRepair())),
                    validGovernments().filter(g -> e.member(player, g)).map(this::governmentRow), pageActions());
        }

        private UiView governments(java.util.function.Predicate<Government> filter) {
            UiText body = query.page().equals("statecraft:nations") ? UiText.EMPTY
                    : t("governments.body", "Nation → State → City. Authority extends to descendants, never to unrelated siblings.");
            return list(section(), body,
                    validGovernments().filter(filter).map(this::governmentRow), pageActions());
        }

        private UiView pending() {
            Player known = e.requirePlayer(actor.id());
            Stream<UiRow> invitations = validInvitations().filter(i -> player.equals(i.playerId) && i.expiresAt > e.now())
                    .map(this::invitationRow);
            Stream<UiRow> bills = validBills().filter(b -> billVote(b) || billDecision(b)
                    || "VETOED".equals(b.status) && e.now() < b.decisionEndsAt && legislator(b)).map(this::billRow);
            Stream<UiRow> contracts = validContracts().filter(this::contractPending).map(this::contractRow);
            Stream<UiRow> diplomacy = validDiplomacy().filter(p -> e.now() < p.expiresAt
                    && ("PROPOSED".equals(p.status) && executive(e.data.governments.get(p.toNation))
                    || "READY".equals(p.status) && diplomaticExecutive(p))).map(this::diplomacyRow);
            Stream<UiRow> proposals = validCompanyProposals().filter(p -> companyVote(p) || companyExecution(p))
                    .map(this::companyProposalRow);
            Stream<UiRow> personal = mailRows(actor.account(), known.inbox.stream().filter(m -> m != null && !m.read).toList(), false);
            Stream<UiRow> official = validGovernments().filter(this::manage)
                    .flatMap(g -> mailRows(e.account(g), g.inbox.stream().filter(m -> m != null && !m.read).toList(), false));
            Stream<UiRow> work = Stream.of(invitations, bills, contracts, diplomacy, proposals, personal, official).flatMap(s -> s);
            UiText body = e.requiresRepair()
                    ? t("dashboard.paused", "Governance requires operator repair. Read-only details remain available; normal mutations are paused.")
                    : t("dashboard.body", "Only work relevant to your current roles is included. Open an item to inspect prerequisites before acting.");
            return list(t("dashboard.title", "My Dashboard"), body, work, pageActions(),
                    t("dashboard.empty", "No matching pending governance work. Try clearing search or browse the section directories."));
        }

        private boolean contractPending(Contract c) {
            Government issuer = e.data.governments.get(c.governmentId);
            boolean official = manage(issuer);
            return "AWARDED".equals(c.status) && e.commerce.contractor(actor, c)
                    || official && ("OPEN".equals(c.status) && e.now() < c.bidEndsAt && !c.bids.isEmpty()
                    || "REVIEW".equals(c.status) && e.now() < c.reviewEndsAt && c.bids.values().stream()
                    .filter(Objects::nonNull).anyMatch(b -> b.cents == 0 || !e.commerce.conflicted(player, b.bidder, b.account))
                    || "SUBMITTED".equals(c.status) && independent(c) && (c.escrowCents == 0 || !player.equals(c.submittedBy)));
        }

        private UiView detail(EntityRef entity) {
            check("statecraft".equals(entity.namespace()), "Invalid governance entity namespace.");
            String id = entity.id();
            return switch (entity.kind()) {
                case GOVERNMENT -> {
                    Government government = e.data.governments.get(id);
                    identity(id, government == null ? null : government.id, "government");
                    yield government(government);
                }
                case COMPANY -> {
                    Company company = e.data.companies.get(id);
                    identity(id, company == null ? null : company.id, "company");
                    yield company(company);
                }
                case CLAIM -> {
                    check(id.equals(ChunkKey.parse(id).toString()), "Use a canonical chunk reference.");
                    if (!e.data.claims.containsKey(id)) yield wilderness(id);
                    Claim claim = e.data.claims.get(id);
                    check(e.viewableClaim(claim) && id.equals(claim.key), "Unknown or invalid claim reference.");
                    yield claim(claim);
                }
                case BILL -> {
                    Bill bill = e.data.bills.get(id);
                    identity(id, bill == null ? null : bill.id, "bill");
                    yield bill(bill);
                }
                case ELECTION -> {
                    Election election = e.data.elections.get(id);
                    identity(id, election == null ? null : election.nationId, "election");
                    yield election(election);
                }
                case LAW -> law(id);
                case DIPLOMACY -> {
                    DiplomaticProposal proposal = e.data.diplomacy.get(id);
                    identity(id, proposal == null ? null : proposal.id, "diplomatic proposal");
                    yield diplomacy(proposal);
                }
                case COMPANY_PROPOSAL -> {
                    CompanyProposal proposal = e.data.companyProposals.get(id);
                    identity(id, proposal == null ? null : proposal.id, "company proposal");
                    yield companyProposal(proposal);
                }
                case CONTRACT -> {
                    if (id.contains("|")) yield bid(id);
                    Contract contract = e.data.contracts.get(id);
                    identity(id, contract == null ? null : contract.id, "contract");
                    yield contract(contract);
                }
                case INVITATION -> {
                    Invitation invitation = e.data.invitations.get(id);
                    identity(id, invitation == null ? null : invitation.id, "invitation");
                    check(canSeeInvitation(invitation), "You may not inspect this invitation.");
                    yield invitation(invitation);
                }
                case MAIL -> message(mail(e, actor, id));
                case PLAYER -> {
                    Player known = e.data.players.get(id);
                    identity(id, known == null ? null : known.id, "player");
                    yield profile(known);
                }
                default -> throw new UserError("Unsupported governance entity kind.");
            };
        }

        private UiView government(Government g) {
            check(e.viewableGovernment(g), "This government requires operator repair before its details are available.");
            String family = g.kind.name().toLowerCase(Locale.ROOT);
            String page = switch (g.kind) { case NATION -> "nations"; case STATE -> "states"; case CITY -> "cities"; };
            Map<String, String> seed = Map.of(family, g.id);
            List<UiAction> actions = new ArrayList<>();
            actions.add(action(page, family + " settings <" + family + ">", seed, () -> { }));
            actions.add(action(page, family + " join <" + family + ">", seed, () -> {
                Player p = e.requirePlayer(actor.id());
                check(!e.member(player, g), "You are already a citizen.");
                e.checkJoinPath(p, g);
                check(e.members(g).size() < e.memberLimit(g.kind), "Citizenship capacity is full.");
                check(e.invitation(g.id, null, player) != null || e.flag(g, "open"), "An invitation is required.");
            }));
            actions.add(action(page, family + " leave <" + family + ">", seed, () -> {
                check(e.member(player, g), "You are not a citizen.");
                e.membershipRemovalScope(player, g);
            }));
            actions.add(action(page, family + " setting <" + family + "> <key> <value>", seed, () -> e.executive(actor, g)));
            actions.add(action(page, family + " rename <" + family + "> <name>", seed, () -> e.manage(actor, g)));
            actions.add(action(page, family + " description <" + family + "> <description>", seed, () -> e.manage(actor, g)));
            actions.add(action("invitations", "government invite <government> <player>", Map.of("government", g.id), () -> e.manage(actor, g)));
            actions.add(action("members", "government leader <government> <player>", Map.of("government", g.id), () -> e.executive(actor, g)));
            actions.add(action("members", "government kick <government> <player>", Map.of("government", g.id), () -> e.manage(actor, g)));
            actions.add(action("official_mail", "mail official inbox <government> <page>", Map.of("government", g.id, "page", "1"), () -> e.manage(actor, g)));
            actions.add(action("contracts", "contract create <government> <title> <description> <chunks>", Map.of("government", g.id), () -> e.manage(actor, g)));
            if (g.kind == Kind.NATION) {
                actions.add(action("states", "state create <nation> <name> <governor>", Map.of("nation", g.id), () -> e.manage(actor, g)));
                actions.add(action("claims", "chunk claim <nation> <chunks>", Map.of("nation", g.id, "chunks", "here"),
                        () -> e.validateClaim(actor, g, actor.chunkKey())));
                actions.add(action("claims", "chunk assignstate <nation> <chunks> <state_or_none>", Map.of("nation", g.id),
                        () -> e.manage(actor, g)));
                actions.add(action("legislature", "bill propose <nation> <policy> <value> <title> <text>", Map.of("nation", g.id), () ->
                        check(actor.admin() || e.member(player, g) && (e.config.allowCitizenBills || e.politics.legislator(player, g)),
                                "Only eligible national citizens may introduce a bill.")));
            } else if (g.kind == Kind.STATE) {
                actions.add(action("cities", "city create <state> <name> <mayor>", Map.of("state", g.id), () -> e.manage(actor, g)));
                actions.add(action("claims", "chunk assigncity <state> <chunks> <city_or_none>", Map.of("state", g.id),
                        () -> e.manage(actor, g)));
            }
            actions.add(action(page, family + " disband <" + family + "> cascade", seed, () -> {
                e.executive(actor, g);
                e.disbandPlan(g, true, actor.admin());
            }));
            Stream<UiRow> children = validGovernments().filter(child -> g.id.equals(child.parentId)).map(this::governmentRow);
            Stream<UiRow> citizens = validPlayers().filter(p -> e.member(p.id, g)).map(this::playerRow);
            return list(UiText.literal(label(g.name)), t("government.detail",
                            "Level: %s\nLeader: %s\nParent: %s\nCitizens: %s\nTreasury: %s\nTag: %s\nFlag: %s\nDescription: %s\nCurrent policies:\n%s\nNational policy changes require legislation: %s",
                            DisplayText.words(g.kind.name()), person(g.leader), governmentName(g.parentId, g.kind == Kind.CITY ? Kind.STATE : Kind.NATION),
                            e.members(g).size(), account(e.account(g)), g.tag, g.flag, g.description,
                            e.settings(g).entrySet().stream().map(setting -> policyName(setting.getKey()) + ": "
                                    + policyValue(e, setting.getKey(), setting.getValue())).collect(Collectors.joining("\n")),
                            DisplayText.yesNo(e.config.requireLegislationForPolicy && g.kind == Kind.NATION)),
                    Stream.concat(children, citizens), actions);
        }

        private UiView company(Company company) {
            check(e.viewableCompany(company), "This company requires operator repair.");
            String id = company.id;
            Map<String, String> seed = Map.of("company", id);
            List<UiAction> actions = new ArrayList<>();
            actions.add(action("companies", "company shareholders <company> <page>", Map.of("company", id, "page", "1"), () -> { }));
            actions.add(action("companies", "company invite <company> <player>", seed, () -> companyManager(company)));
            actions.add(action("companies", "company owner <company> <player>", seed, () ->
                    check(actor.admin() || player.equals(company.owner), "Only the company owner may transfer management.")));
            actions.add(action("companies", "company transfer <company> <player> <shares>", seed, () ->
                    check(e.availableShares(id, actor.id()) > 0, "You have no unreserved shares to transfer.")));
            actions.add(action("shareholders", "company propose <company> <type> <value> <title> <text>", seed, () ->
                    check(company.shares.getOrDefault(player, 0L) > 0, "Only shareholders may propose decisions.")));
            actions.add(action("companies", "company leave <company>", seed, () ->
                    check(company.members.contains(player) && !player.equals(company.owner), "Only non-owner members may leave.")));
            actions.add(action("companies", "company disband <company>", seed, () -> {
                check(actor.admin() || player.equals(company.owner), "Only the owner may dissolve the company.");
                check(actor.admin() || company.shares.keySet().stream().allMatch(company.owner::equals),
                        "Other shareholders must approve dissolution.");
                e.commerce.assertDisposable(company, null);
            }));
            return list(UiText.literal(label(company.name)), t("company.detail",
                            "Owner: %s\nMembers: %s\nIssued shares: %s\nYour shares: %s\nTreasury: %s\nDescription: %s",
                            person(company.owner), company.members.size(), company.totalShares,
                            company.shares.getOrDefault(player, 0L), account("company:" + id), company.description),
                    validPlayers().filter(p -> company.members.contains(p.id) || company.shares.containsKey(p.id))
                            .map(p -> new UiRow(UiText.literal(label(person(p.id))), t("company.participant", "Shares: %s; employed: %s; officer: %s",
                                    company.shares.getOrDefault(p.id, 0L), DisplayText.yesNo(company.members.contains(p.id)),
                                    DisplayText.yesNo(company.officers.contains(p.id))),
                                    ref(EntityRef.Kind.PLAYER, p.id))), actions);
        }

        private UiView claim(Claim claim) {
            Government jurisdiction = e.claimGovernment(claim);
            Map<String, String> seed = Map.of("chunk", claim.key);
            List<UiAction> actions = new ArrayList<>(List.of(
                    action("claims", "chunk protection <chunk> <player>", seed, () -> { }),
                    action("claims", "chunk permits <chunk> <page>", Map.of("chunk", claim.key, "page", "1"), () -> titleManager(claim)),
                    action("claims", "chunk permit <chunk> <player> <actions>", seed, () -> titleManager(claim)),
                    action("claims", "chunk unclaim <nation> <chunks>", Map.of("nation", claim.nationId, "chunks", claim.key),
                            () -> e.unclaimPlan(actor, claim.key, false)),
                    action("claims", "chunk assignstate <nation> <chunks> <state_or_none>",
                            Map.of("nation", claim.nationId, "chunks", claim.key),
                            () -> e.territory.nationalManager(actor, e.gov(claim.nationId))),
                    action("admin", "admin reassign <chunk> <government>", seed, () -> e.admin(actor))));
            if (claim.stateId != null)
                actions.add(action("claims", "chunk assigncity <state> <chunks> <city_or_none>",
                        Map.of("state", claim.stateId, "chunks", claim.key), () -> e.manage(actor, e.gov(claim.stateId))));
            return list(UiText.literal(label(chunkName(claim.key))), t("claim.detail",
                            "City: %s\nState: %s\nNation: %s\nPrivate title: %s\nImprovements: %s\nClaimed at: %s\nEconomy reservation: %s\nContract reservation: %s\nTreaty reservation: %s",
                            claim.cityId == null ? "Unassigned" : governmentName(claim.cityId, Kind.CITY),
                            claim.stateId == null ? "Unassigned" : governmentName(claim.stateId, Kind.STATE), governmentName(claim.nationId, Kind.NATION),
                            account(claim.ownerAccount), claim.improvements, DisplayText.date(claim.claimedAt),
                            DisplayText.yesNo(e.economy.isClaimEncumbered(claim.key)), DisplayText.yesNo(e.commerce.claimLocked(claim.key)),
                            DisplayText.yesNo(e.politics.claimLocked(claim.key, null))),
                    Stream.of(governmentRow(jurisdiction)), actions);
        }

        private UiView wilderness(String key) {
            Map<String, String> seed = Map.of("chunk", key);
            List<UiAction> actions = List.of(
                    action("claims", "chunk claim <nation> <chunks>", Map.of("chunks", key), () ->
                            check(validGovernments().anyMatch(g -> g.kind == Kind.NATION && manage(g)),
                                    "You must manage a nation before claiming territory.")),
                    action("claims", "chunk info <chunk>", seed, () -> { }));
            return list(t("claim.wilderness_title", "Unclaimed territory"),
                    t("claim.wilderness_body", "Location: %s\nNo nation currently claims this land. Select an eligible nation to review the claim fee, capacity and adjacency requirements. State and city assignments are optional and made later.", chunkName(key)),
                    Stream.empty(), actions,
                    t("claim.wilderness_empty", "Opening this view creates no claim and makes no payment."));
        }

        private UiView bill(Bill bill) {
            Government nation = e.data.governments.get(bill.nationId);
            Map<String, String> seed = Map.of("bill", bill.id);
            List<UiAction> actions = List.of(
                    action("legislature", "bill vote <bill> <yes_no_abstain>", seed, () -> check(billVote(bill), "No eligible uncast ballot is open for you.")),
                    action("legislature", "bill sign <bill>", seed, () -> check(billDecision(bill) && bill.treatyId == null, "A current national executive and a passed ordinary bill are required.")),
                    action("legislature", "bill veto <bill> <reason>", seed, () -> check(billDecision(bill), "Only a current national executive may veto a passed bill.")),
                    action("legislature", "bill override <bill>", seed, () -> check("VETOED".equals(bill.status)
                            && e.now() < bill.decisionEndsAt && legislator(bill), "A current legislator and an unexpired veto are required.")),
                    action("legislature", "bill revise <bill> <value> <title> <text>", seed, () -> check("DEBATE".equals(bill.status)
                            && bill.treatyId == null && player.equals(bill.author), "Only the author may revise an ordinary bill during debate.")),
                    action("legislature", "bill cancel <bill>", seed, () -> check(has(LIVE_BILLS, bill.status)
                            && (actor.admin() || "DEBATE".equals(bill.status) && bill.treatyId == null && player.equals(bill.author)),
                            "Only the author during debate or an operator may cancel.")));
            Stream<UiRow> voters = bill.votes.entrySet().stream().filter(v -> GovernanceEngine.validUuid(v.getKey()))
                    .map(v -> new UiRow(UiText.literal(label(person(v.getKey()))), t("ballot.choice", "Vote: %s", DisplayText.words(v.getValue())),
                            e.data.players.containsKey(v.getKey()) ? ref(EntityRef.Kind.PLAYER, v.getKey()) : EntityRef.NONE));
            return list(UiText.literal(label(policyTitle(e, bill.policy, bill.value, bill.title))), t("bill.detail",
                    "Nation: %s\nStatus: %s\nAuthor: %s\nPolicy: %s: %s\nAmendment: %s\nDebate ends: %s\nVoting ends: %s\nDecision deadline: %s\nEligible voters: %s\nText: %s",
                    governmentName(bill.nationId, Kind.NATION), DisplayText.words(bill.status), person(bill.author), policyName(bill.policy),
                    policyValue(e, bill.policy, bill.value), DisplayText.yesNo(bill.amendment),
                    DisplayText.date(bill.debateEndsAt), DisplayText.date(bill.voteEndsAt), DisplayText.date(bill.decisionEndsAt),
                    bill.electorate.size(), policyBody(e, bill.policy, bill.value, bill.text)),
                    voters, e.validHierarchy(nation) ? actions : disabled(actions, t("missing.parent", "The former parent organization no longer exists; historical information remains readable.")));
        }

        private UiView election(Election election) {
            Government nation = e.data.governments.get(election.nationId);
            check(e.viewableGovernment(nation) && nation.kind == Kind.NATION, "The election's nation requires repair.");
            Map<String, String> seed = Map.of("nation", nation.id);
            List<UiAction> actions = List.of(
                    action("elections", "election candidate <nation>", seed, () -> check(e.member(player, nation)
                            && !election.voting && !election.scheduleExhausted && !election.candidates.contains(player), "Registration is closed or you are not an unregistered citizen.")),
                    action("elections", "election withdraw <nation>", seed, () -> check(!election.voting && election.candidates.contains(player), "Only registered candidates may withdraw before voting.")),
                    action("elections", "election vote <nation> <candidate>", seed, () -> check(election.voting && e.now() < election.endsAt
                            && e.member(player, nation) && election.electorate.contains(player) && !election.votes.containsKey(player), "You have no eligible uncast election ballot.")),
                    action("elections", "election history <nation> <page>", Map.of("nation", nation.id, "page", "1"), () -> { }));
            return list(t("election.title", "Election: %s", nation.name), t("election.detail",
                            "Voting open: %s\nRegistration closes: %s\nVoting ends: %s\nCandidates: %s\nEligible voters: %s\nCast ballots: %s\nFuture elections paused: %s",
                            DisplayText.yesNo(election.voting), DisplayText.date(election.nextStartAt), DisplayText.date(election.endsAt),
                            election.candidates.size(), election.electorate.size(), election.votes.size(), DisplayText.yesNo(election.scheduleExhausted)),
                    validPlayers().filter(p -> election.candidates.contains(p.id)).map(this::playerRow), actions);
        }

        private UiView law(String id) {
            String[] parts = id.split(":", -1);
            check(parts.length == 2 && GovernanceEngine.validUuid(parts[0]) && GovernanceEngine.validUuid(parts[1]), "Invalid law reference.");
            Law law = e.data.laws.getOrDefault(parts[0], List.of()).stream().filter(Objects::nonNull)
                    .filter(l -> parts[1].equals(l.id)).findFirst().orElseThrow(() -> new UserError("This law is not retained in that codex."));
            List<UiAction> actions = List.of(action("laws", "law read <nation> <law>", Map.of("nation", parts[0], "law", law.id),
                    () -> e.politics.nation(parts[0])));
            return list(UiText.literal(label(policyTitle(e, law.policy, law.value, law.title))), t("law.detail",
                            "Nation: %s\nPolicy: %s: %s\nEnacted at: %s\nAmendment: %s\nRoleplay only: %s\nText: %s",
                            governmentName(parts[0], Kind.NATION), policyName(law.policy), policyValue(e, law.policy, law.value),
                            DisplayText.date(law.enactedAt), DisplayText.yesNo(law.amendment), DisplayText.yesNo(law.roleplayOnly),
                            policyBody(e, law.policy, law.value, law.text)),
                    Stream.empty(), actions, t("law.empty", "This retained law has no child records."));
        }

        private UiView diplomacy(DiplomaticProposal proposal) {
            Map<String, String> seed = Map.of("proposal", proposal.id);
            List<UiAction> actions = List.of(
                    action("diplomacy", "diplomacy accept <proposal>", seed, () -> check("PROPOSED".equals(proposal.status)
                            && e.now() < proposal.expiresAt && executive(e.data.governments.get(proposal.toNation)), "Only the receiving nation's executive may accept a live proposal.")),
                    action("diplomacy", "diplomacy ratify <proposal> <yes_no_abstain>", seed, () -> check(validBills()
                            .anyMatch(b -> proposal.id.equals(b.treatyId) && billVote(b)), "Your legislature has no eligible uncast ratification ballot.")),
                    action("diplomacy", "diplomacy execute <proposal>", seed, () -> {
                        check("READY".equals(proposal.status), "Both legislatures must ratify before settlement can be retried.");
                        e.politics.diplomaticExecutive(actor, proposal);
                        e.politics.validateTreaty(proposal);
                    }),
                    action("diplomacy", "diplomacy reject <proposal>", seed, () -> check(has(LIVE_DIPLOMACY, proposal.status)
                            && executive(e.data.governments.get(proposal.toNation)), "Only the receiving executive may reject an active proposal.")),
                    action("diplomacy", "diplomacy cancel <proposal>", seed, () -> check(has(LIVE_DIPLOMACY, proposal.status)
                            && executive(e.data.governments.get(proposal.fromNation)), "Only the proposing executive may cancel an active proposal.")));
            Stream<UiRow> terms = proposal.chunks.stream().filter(Objects::nonNull).map(term ->
                    new UiRow(UiText.literal(label(chunkName(term.key))), t("treaty.chunk", "%s → %s",
                            territoryScope(e, term.fromNation, term.fromState, term.fromCity),
                            territoryScope(e, term.toNation, term.toState, term.toCity)),
                            e.data.claims.containsKey(term.key) ? ref(EntityRef.Kind.CLAIM, term.key) : EntityRef.NONE));
            Stream<UiRow> ballots = validBills().filter(b -> proposal.id.equals(b.treatyId)).map(this::billRow);
            return list(t("diplomacy.title", "%s proposal", DisplayText.words(proposal.type)), t("diplomacy.detail",
                            "From: %s\nTo: %s\nStatus: %s\nExpires: %s\nProposer offers: %s\nCounterparty pays: %s\nRatified nations: %s\nSettlement issue: %s\nMessage: %s",
                            governmentName(proposal.fromNation, Kind.NATION), governmentName(proposal.toNation, Kind.NATION),
                            DisplayText.words(proposal.status), DisplayText.date(proposal.expiresAt),
                            Money.format(proposal.offeredCents), Money.format(proposal.demandedCents),
                            proposal.ratified.isEmpty() ? "None" : proposal.ratified.stream().map(id -> governmentName(id, Kind.NATION))
                                    .collect(Collectors.joining(", ")), issue(proposal.lastError), proposal.message),
                    Stream.concat(ballots, terms), actions);
        }

        private UiView companyProposal(CompanyProposal proposal) {
            Map<String, String> seed = Map.of("proposal", proposal.id);
            Company company = e.data.companies.get(proposal.companyId);
            List<UiAction> actions = List.of(
                    action("shareholders", "company vote <proposal> <yes_no_abstain>", seed, () -> check(companyVote(proposal), "You have no eligible uncast shareholder ballot.")),
                    action("shareholders", "company execute <proposal>", seed, () -> check(companyExecution(proposal), "Only eligible company participants may retry a ready proposal before its settlement deadline.")),
                    action("shareholders", "company cancel <proposal>", seed, () -> check(has(Set.of("VOTING", "READY"), proposal.status)
                            && (actor.admin() || "VOTING".equals(proposal.status) && player.equals(proposal.author) && proposal.votes.isEmpty()),
                            "Only the author before voting, or an operator, may cancel an active proposal.")));
            return list(UiText.literal(label(proposal.title)), t("company_proposal.detail",
                            "Company: %s\nStatus: %s\nDecision: %s: %s\nVoting ends: %s\nSettlement deadline: %s\nBlocked reason: %s\nText: %s",
                            companyName(proposal.companyId), DisplayText.words(proposal.status), decisionName(proposal.type),
                            decisionValue(e, proposal.type, proposal.value), DisplayText.date(proposal.endsAt),
                            DisplayText.date(settlementDeadline(proposal)), issue(proposal.lastError), proposal.text),
                    proposal.electorate.entrySet().stream().filter(holder -> GovernanceEngine.validUuid(holder.getKey()))
                            .map(holder -> new UiRow(UiText.literal(label(person(holder.getKey()))),
                                    t("shareholder.ballot", "Shares at voting start: %s; vote: %s", holder.getValue(),
                                            DisplayText.words(proposal.votes.getOrDefault(holder.getKey(), "Not cast"))),
                                    e.data.players.containsKey(holder.getKey()) ? ref(EntityRef.Kind.PLAYER, holder.getKey()) : EntityRef.NONE)),
                    company == null ? disabled(actions, t("missing.parent", "The former parent organization no longer exists; historical information remains readable.")) : actions);
        }

        private UiView contract(Contract contract) {
            Government issuer = e.data.governments.get(contract.governmentId);
            Map<String, String> seed = Map.of("contract", contract.id);
            List<UiAction> actions = new ArrayList<>();
            actions.add(action("contracts", "contract bids <contract> <page>", Map.of("contract", contract.id, "page", "1"), () ->
                    check(manage(issuer), "Current issuing-government authority is required to inspect detailed bids.")));
            actions.add(action("contracts", "contract bid <contract> <amount> <description>", seed, () ->
                    check("OPEN".equals(contract.status) && e.now() < contract.bidEndsAt, "Bidding is closed.")));
            actions.add(action("contracts", "contract bid <contract> <amount> <description> company:<company>", seed, () -> {
                check("OPEN".equals(contract.status) && e.now() < contract.bidEndsAt, "Bidding is closed.");
                check(validCompanies().anyMatch(c -> e.mayManageCompany(actor.id(), c.id)), "You do not manage a company that can bid.");
            }));
            actions.add(action("contracts", "contract withdraw <contract>", seed, () ->
                    check(has(Set.of("OPEN", "REVIEW"), contract.status) && contract.bids.containsKey(player), "You have no unawarded bid to withdraw.")));
            actions.add(action("contracts", "contract review <contract>", seed, () ->
                    check(manage(issuer) && "OPEN".equals(contract.status) && !contract.bids.isEmpty(), "An issuing official and at least one open bid are required.")));
            actions.add(action("contracts", "contract award <contract> <bidder>", seed, () ->
                    check(manage(issuer) && "REVIEW".equals(contract.status) && e.now() < contract.reviewEndsAt, "An issuing official and an unexpired review are required.")));
            actions.add(action("contracts", "contract submit <contract> <completion_note>", seed, () ->
                    check("AWARDED".equals(contract.status) && e.commerce.contractor(actor, contract), "Only the selected contractor or its managers may submit awarded work.")));
            actions.add(action("contracts", "contract return <contract> <reason>", seed, () ->
                    check(manage(issuer) && "SUBMITTED".equals(contract.status) && independent(contract), "An independent issuing official must review submitted work.")));
            actions.add(action("contracts", "contract complete <contract>", seed, () -> e.commerce.completionPlan(actor, contract)));
            actions.add(action("contracts", "contract cancel <contract> <reason>", seed, () -> e.commerce.cancellationPlan(actor, contract)));
            Stream<UiRow> bids = !manage(issuer) ? Stream.empty() : contract.bids.values().stream().filter(Objects::nonNull)
                    .map(b -> new UiRow(UiText.literal(label(person(b.bidder))), t("contract.bid", "Bid: %s\nPayee: %s\nDescription: %s",
                            Money.format(b.cents), account(b.account), clip(b.text, 1000)),
                            GovernanceEngine.validUuid(b.bidder) ? ref(EntityRef.Kind.CONTRACT, contract.id + "|" + b.bidder) : EntityRef.NONE));
            Stream<UiRow> chunks = contract.chunks.stream().map(key -> new UiRow(UiText.literal(label(chunkName(key))),
                    t("contract.chunk", "Selected contract territory"), e.data.claims.containsKey(key) ? ref(EntityRef.Kind.CLAIM, key) : EntityRef.NONE));
            return list(UiText.literal(label(contract.title)), t("contract.detail",
                            "Issuer: %s\nStatus: %s\nAuthor: %s\nBidding ends: %s\nReview ends: %s\nWinner: %s\nOriginal payee: %s\nEscrow: %s\nDescription: %s\nSubmission: %s\nDetailed bids visible: %s",
                            governmentName(contract.governmentId), DisplayText.words(contract.status), person(contract.author),
                            DisplayText.date(contract.bidEndsAt), DisplayText.date(contract.reviewEndsAt),
                            person(contract.winner), account(contract.payeeAccount), Money.format(contract.escrowCents),
                            contract.description, contract.completionNote, DisplayText.yesNo(manage(issuer))), Stream.concat(bids, chunks), actions);
        }

        private UiView bid(String id) {
            String[] parts = id.split("\\|", -1);
            check(parts.length == 2 && GovernanceEngine.validUuid(parts[0]) && GovernanceEngine.validUuid(parts[1]),
                            "Invalid contract bid reference.");
            Contract contract = e.data.contracts.get(parts[0]);
            identity(parts[0], contract == null ? null : contract.id, "contract");
            e.manage(actor, e.gov(contract.governmentId));
            Bid bid = contract.bids.get(parts[1]);
            check(bid != null && parts[1].equals(bid.bidder), "This bid is not retained on that contract.");
            Map<String, String> award = Map.of("contract", contract.id, "bidder", bid.bidder);
            List<UiAction> actions = List.of(
                            action("contracts", "contract award <contract> <bidder>", award, () -> e.commerce.awardPlan(actor, contract, bid.bidder)),
                            action("contracts", "contract withdraw <contract>", Map.of("contract", contract.id), () ->
                                    check(player.equals(bid.bidder) && has(Set.of("OPEN", "REVIEW"), contract.status), "Only this bidder may withdraw an unawarded bid.")),
                            action("contracts", "contract info <contract> <page>", Map.of("contract", contract.id, "page", "1"), () -> { }));
            return list(t("bid.title", "Bid: %s", label(person(bid.bidder))),
                            t("bid.detail", "Contract: %s\nBidder: %s\nPayee: %s\nAmount: %s\nSubmitted at: %s\nDescription: %s",
                                    contract.title, person(bid.bidder), account(bid.account), Money.format(bid.cents),
                                    DisplayText.date(bid.createdAt), bid.text),
                            Stream.of(contractRow(contract)), actions);
        }

        private UiView invitation(Invitation invitation) {
            check(GovernanceEngine.validUuid(invitation.playerId)
                    && (invitation.governmentId == null) != (invitation.companyId == null), "This invitation requires operator repair.");
            boolean personal = player.equals(invitation.playerId);
            boolean live = invitation.expiresAt > e.now();
            List<UiAction> actions = new ArrayList<>();
            if (invitation.governmentId != null) {
                Government government = e.data.governments.get(invitation.governmentId);
                Map<String, String> seed = Map.of("government", invitation.governmentId);
                actions.add(action("invitations", "government accept <government>", seed, () -> {
                    check(personal && live && e.eligibleInvitation(e.requirePlayer(actor.id()), government), "This is not a live, eligible invitation for you.");
                    check(e.members(government).size() < e.memberLimit(government.kind), "Citizenship capacity is full.");
                }));
                actions.add(action("invitations", "government decline <government>", seed, () -> check(personal && live, "Only the invited player may decline a live invitation.")));
                actions.add(action("invitations", "government revoke <government> <player>", Map.of("government", invitation.governmentId,
                        "player", invitation.playerId), () -> check(live && manage(government), "Only a current official may revoke a live invitation.")));
            } else if (invitation.companyId != null) {
                Company company = e.data.companies.get(invitation.companyId);
                Map<String, String> seed = Map.of("company", invitation.companyId);
                actions.add(action("companies", "company accept <company>", seed, () -> check(personal && live && e.viewableCompany(company)
                        && !company.members.contains(player) && company.members.size() < e.config.maxCompanyMembers, "The invitation or membership capacity is unavailable.")));
                actions.add(action("companies", "company decline <company>", seed, () -> check(personal && live, "Only the invited player may decline a live invitation.")));
                actions.add(action("companies", "company revoke <company> <player>", Map.of("company", invitation.companyId,
                        "player", invitation.playerId), () -> { check(live, "The invitation has expired."); companyManager(company); }));
            }
            return list(t("invitation.title", "Invitation: %s", invitationOrganization(invitation)),
                    t("invitation.detail", "Organization: %s\nInvitee: %s\nInvited by: %s\nExpires: %s\nActive: %s",
                            invitationOrganization(invitation), person(invitation.playerId), person(invitation.invitedBy),
                            DisplayText.date(invitation.expiresAt), DisplayText.yesNo(live)),
                    Stream.empty(), actions, t("invitation.empty", "Review the current parent-membership and capacity requirements before accepting."));
        }

        private UiView message(MailEntry entry) {
            Mail message = entry.message();
            if (!e.requiresRepair() || actor.admin()) e.communications.markRead(message);
            String page = entry.government() == null ? "mail" : "official_mail";
            String prefix = entry.government() == null ? "mail " : "mail official ";
            String scope = entry.government() == null ? "" : "<government> ";
            Map<String, String> seed = entry.government() == null ? Map.of("message", message.id)
                    : Map.of("government", entry.government().id, "message", message.id);
            List<UiAction> actions = List.of(
                    action(page, prefix + "reply " + scope + "<message> <body>", seed, () -> {
                        check(!entry.sent() && !"system".equals(message.sender), "Only received, non-system messages accept replies.");
                        e.communications.validateRecipientAccount(message.sender);
                    }),
                    action(page, prefix + "delete " + scope + "<message>", seed, () -> { }));
            String body = mailBody(e, message);
            return list(UiText.literal(label(message.subject)), t("mail.detail", "From: %s\nTo: %s\nSent at: %s\nCopy: %s\n\n%s%s",
                            account(message.sender), account(message.recipient), DisplayText.date(message.sentAt), entry.sent() ? "Sent" : "Inbox",
                            body.substring(0, Math.min(2048, body.length())), body.length() > 2048 ? body.substring(2048) : ""),
                    Stream.empty(), actions, t("mail.empty", "Deleting this copy does not remove the other party's copy."));
        }

        private UiView profile(Player known) {
            Stream<UiRow> citizenship = validGovernments().filter(g -> e.member(known.id, g)).map(this::governmentRow);
            Stream<UiRow> companies = validCompanies().filter(c -> c.members.contains(known.id) || c.shares.getOrDefault(known.id, 0L) > 0).map(this::companyRow);
            UiText body = player.equals(known.id)
                    ? t("profile.self", "Player: %s\nUnread personal mail: %s\nAutomatic claiming: %s",
                    person(known.id), known.inbox.stream().filter(m -> m != null && actor.account().equals(m.recipient) && !m.read).count(),
                    known.autoClaim ? "On" : "Off")
                    : t("profile.public", "Player: %s\nCitizenship and company participation are public; personal mail is private.", person(known.id));
            return list(UiText.literal(label(person(known.id))), body, Stream.concat(citizenship, companies),
                    List.of(action("mail", "mail send <recipient> <subject> <body>", Map.of("recipient", known.id), () -> { })));
        }

        private boolean manage(Government government) {
            return e.viewableGovernment(government) && e.canManage(actor, government);
        }

        private boolean executive(Government government) {
            return e.viewableGovernment(government) && passes(() -> e.executive(actor, government));
        }

        private void companyManager(Company company) {
            check(e.viewableCompany(company) && (actor.admin() || e.mayManageCompany(actor.id(), company.id)), "Current company management authority is required.");
        }

        private void titleManager(Claim claim) {
            check(actor.admin() || e.mayAccessAccount(actor.id(), claim.ownerAccount), "Only the private title's managers may inspect or change permits.");
        }

        private boolean independent(Contract contract) {
            return contract.escrowCents == 0 || !e.commerce.conflicted(player, contract.winner, contract.payeeAccount);
        }

        private boolean legislator(Bill bill) {
            Government nation = e.data.governments.get(bill.nationId);
            return e.viewableGovernment(nation) && e.politics.legislator(player, nation);
        }

        private boolean billVote(Bill bill) {
            return has(Set.of("VOTING", "OVERRIDE_VOTING"), bill.status) && e.now() < bill.voteEndsAt
                    && bill.electorate.contains(player) && !bill.votes.containsKey(player) && legislator(bill);
        }

        private boolean billDecision(Bill bill) {
            return "PASSED".equals(bill.status) && e.now() < bill.decisionEndsAt && executive(e.data.governments.get(bill.nationId));
        }

        private boolean diplomaticExecutive(DiplomaticProposal proposal) {
            return passes(() -> e.politics.diplomaticExecutive(actor, proposal));
        }

        private boolean companyVote(CompanyProposal proposal) {
            Long weight = proposal.electorate.get(player);
            return e.viewableCompany(e.data.companies.get(proposal.companyId)) && "VOTING".equals(proposal.status)
                    && e.now() < proposal.endsAt && weight != null && weight > 0 && !proposal.votes.containsKey(player);
        }

        private boolean companyExecution(CompanyProposal proposal) {
            Company company = e.data.companies.get(proposal.companyId);
            return e.viewableCompany(company) && "READY".equals(proposal.status) && e.now() < settlementDeadline(proposal)
                    && (actor.admin() || e.mayManageCompany(actor.id(), company.id) || company.shares.getOrDefault(player, 0L) > 0);
        }

        private long settlementDeadline(CompanyProposal proposal) {
            return proposal.executionEndsAt == 0 ? GovernanceEngine.deadline(proposal.endsAt, e.config.companyVotingMillis) : proposal.executionEndsAt;
        }

        private boolean canSeeInvitation(Invitation invitation) {
            return player.equals(invitation.playerId) || manage(e.data.governments.get(invitation.governmentId))
                    || invitation.companyId != null && passes(() -> companyManager(e.data.companies.get(invitation.companyId)));
        }

        private Stream<Government> validGovernments() { return e.data.governments.values().stream().filter(e::viewableGovernment); }
        private Stream<Company> validCompanies() { return e.data.companies.values().stream().filter(e::viewableCompany); }
        private Stream<Claim> validClaims() { return e.data.claims.values().stream().filter(e::viewableClaim); }
        private Stream<Player> validPlayers() {
            return e.data.players.values().stream().filter(Objects::nonNull).filter(p -> GovernanceEngine.validUuid(p.id) && e.data.players.get(p.id) == p);
        }
        private Stream<Bill> validBills() {
            return e.data.bills.values().stream().filter(Objects::nonNull).filter(b -> GovernanceEngine.validUuid(b.id) && e.data.bills.get(b.id) == b);
        }
        private Stream<Election> validElections() {
            return e.data.elections.values().stream().filter(Objects::nonNull).filter(v -> GovernanceEngine.validUuid(v.nationId)
                    && e.data.elections.get(v.nationId) == v && e.viewableGovernment(e.data.governments.get(v.nationId)));
        }
        private Stream<Contract> validContracts() {
            return e.data.contracts.values().stream().filter(Objects::nonNull).filter(c -> GovernanceEngine.validUuid(c.id) && e.data.contracts.get(c.id) == c);
        }
        private Stream<DiplomaticProposal> validDiplomacy() {
            return e.data.diplomacy.values().stream().filter(Objects::nonNull).filter(p -> GovernanceEngine.validUuid(p.id) && e.data.diplomacy.get(p.id) == p);
        }
        private Stream<CompanyProposal> validCompanyProposals() {
            return e.data.companyProposals.values().stream().filter(Objects::nonNull).filter(p -> GovernanceEngine.validUuid(p.id) && e.data.companyProposals.get(p.id) == p);
        }
        private Stream<Invitation> validInvitations() {
            return e.data.invitations.values().stream().filter(Objects::nonNull).filter(i -> GovernanceEngine.validUuid(i.id)
                    && e.data.invitations.get(i.id) == i && GovernanceEngine.validUuid(i.playerId));
        }

        private UiRow governmentRow(Government government) {
            return new UiRow(UiText.literal(label(government.name)), t("government.row", "%s; leader: %s; citizens: %s",
                    DisplayText.words(government.kind.name()), person(government.leader), e.members(government).size()),
                    ref(EntityRef.Kind.GOVERNMENT, government.id));
        }
        private UiRow companyRow(Company company) {
            return new UiRow(UiText.literal(label(company.name)), t("company.row", "Owner: %s; issued shares: %s",
                    person(company.owner), company.totalShares), ref(EntityRef.Kind.COMPANY, company.id));
        }
        private UiRow claimRow(Claim claim) {
            EntityRef reference = ref(EntityRef.Kind.CLAIM, claim.key);
            UiText detail = reference.present() ? t("claim.row", "Nation: %s; state: %s; city: %s; title: %s",
                    governmentName(claim.nationId, Kind.NATION),
                    claim.stateId == null ? "Unassigned" : governmentName(claim.stateId, Kind.STATE),
                    claim.cityId == null ? "Unassigned" : governmentName(claim.cityId, Kind.CITY), account(claim.ownerAccount))
                    : t("claim.long_key", "This territory is unavailable in this view. Ask an operator to inspect it.");
            return new UiRow(UiText.literal(label(chunkName(claim.key))), detail, reference);
        }
        private UiRow playerRow(Player known) {
            return new UiRow(UiText.literal(label(person(known.id))), t("player.row", "Nation: %s; state: %s; city: %s",
                    governmentName(known.nationId, Kind.NATION), governmentName(known.stateId, Kind.STATE),
                    governmentName(known.cityId, Kind.CITY)), ref(EntityRef.Kind.PLAYER, known.id));
        }
        private UiRow billRow(Bill bill) {
            return new UiRow(UiText.literal(label(policyTitle(e, bill.policy, bill.value, bill.title))), t("bill.row", "%s; nation: %s; policy: %s: %s",
                    DisplayText.words(bill.status), governmentName(bill.nationId, Kind.NATION),
                    policyName(bill.policy), policyValue(e, bill.policy, bill.value)), ref(EntityRef.Kind.BILL, bill.id));
        }
        private UiRow electionRow(Election election) {
            return new UiRow(UiText.literal(label(governmentName(election.nationId, Kind.NATION))),
                    t("election.row", "Voting open: %s; registration closes: %s; voting ends: %s",
                    DisplayText.yesNo(election.voting), DisplayText.date(election.nextStartAt), DisplayText.date(election.endsAt)),
                    ref(EntityRef.Kind.ELECTION, election.nationId));
        }
        private UiRow lawRow(String nation, Law law) {
            return new UiRow(UiText.literal(label(policyTitle(e, law.policy, law.value, law.title))), t("law.row", "Nation: %s; policy: %s: %s; roleplay only: %s",
                    governmentName(nation, Kind.NATION), policyName(law.policy), policyValue(e, law.policy, law.value),
                    DisplayText.yesNo(law.roleplayOnly)), ref(EntityRef.Kind.LAW, nation + ":" + law.id));
        }
        private UiRow contractRow(Contract contract) {
            return new UiRow(UiText.literal(label(contract.title)), t("contract.row", "%s; issuer: %s; payee: %s; escrow: %s",
                    DisplayText.words(contract.status), governmentName(contract.governmentId), account(contract.payeeAccount), Money.format(contract.escrowCents)),
                    ref(EntityRef.Kind.CONTRACT, contract.id));
        }
        private UiRow diplomacyRow(DiplomaticProposal proposal) {
            return new UiRow(t("diplomacy.row_title", "%s: %s → %s", DisplayText.words(proposal.type),
                            label(governmentName(proposal.fromNation, Kind.NATION)), label(governmentName(proposal.toNation, Kind.NATION))),
                    t("diplomacy.row", "%s; expires: %s; blocked: %s", DisplayText.words(proposal.status),
                            DisplayText.date(proposal.expiresAt), issue(proposal.lastError)),
                    ref(EntityRef.Kind.DIPLOMACY, proposal.id));
        }
        private UiRow companyProposalRow(CompanyProposal proposal) {
            return new UiRow(UiText.literal(label(proposal.title)), t("company_proposal.row", "%s; company: %s; decision: %s; blocked: %s",
                    DisplayText.words(proposal.status), companyName(proposal.companyId), decisionName(proposal.type),
                    issue(proposal.lastError)), ref(EntityRef.Kind.COMPANY_PROPOSAL, proposal.id));
        }
        private UiRow invitationRow(Invitation invitation) {
            return new UiRow(t("invitation.row_title", "Invitation: %s", label(invitationOrganization(invitation))),
                    t("invitation.row", "Invitee: %s; expires: %s", person(invitation.playerId), DisplayText.date(invitation.expiresAt)),
                    ref(EntityRef.Kind.INVITATION, invitation.id));
        }
        private Stream<UiRow> mailRows(String owner, Collection<Mail> messages, boolean sent) {
            return messages.stream().filter(Objects::nonNull).filter(m -> GovernanceEngine.validUuid(m.id) && owner.equals(sent ? m.sender : m.recipient))
                    .map(m -> new UiRow(UiText.literal(label(m.subject)), t("mail.row", "Read: %s; mailbox: %s; copy: %s; sent at: %s",
                            DisplayText.yesNo(m.read), account(owner), sent ? "Sent" : "Inbox", DisplayText.date(m.sentAt)),
                            ref(EntityRef.Kind.MAIL, mailId(owner, sent, m))));
        }
        private String invitationOrganization(Invitation invitation) {
            return invitation.governmentId == null ? companyName(invitation.companyId) : governmentName(invitation.governmentId);
        }
        private String person(String id) {
            return GovernancePresentation.person(e, id);
        }
        private String governmentName(String id) {
            return governmentName(id, null);
        }
        private String governmentName(String id, Kind kind) {
            return GovernancePresentation.governmentName(e, id, kind);
        }
        private String companyName(String id) {
            return GovernancePresentation.companyName(e, id);
        }
        private String account(String value) {
            return GovernancePresentation.account(e, value);
        }

        private UiText section() {
            MenuPage page = MenuRegistry.get(query.page());
            return t("section." + page.id().substring("statecraft:".length()), page.title());
        }

        private List<UiAction> pageActions() {
            return MenuRegistry.get(query.page()).actions().stream().limit(UiView.MAX_ACTIONS)
                    .map(a -> action(query.page().substring("statecraft:".length()), a.command(), Map.of(), () -> {
                        if ("statecraft:admin".equals(query.page())) e.admin(actor);
                    })).toList();
        }

        private UiAction action(String page, String template, Map<String, String> values, Runnable availability) {
            String pageId = "statecraft:" + page;
            MenuPage.Action definition = MenuRegistry.get(pageId).actions().stream().filter(a -> a.command().equals(template))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing registered context action: " + pageId + " " + template));
            UiAction action = new UiAction(pageId, template, t("action." + actionKey(definition.label()), definition.label()), values);
            try {
                if (definition.intent() != ActionIntent.NAVIGATION && e.requiresRepair() && !actor.admin()
                        && !"info".equals(template) && !template.startsWith("help "))
                    throw new UserError("Governance requires operator repair before this command is permitted.");
                availability.run();
                return action;
            } catch (UserError unavailable) {
                return action.disabled(t("action.unavailable", "Unavailable: %s", clip(issue(unavailable.getMessage()), 450)));
            }
        }

        private List<UiAction> disabled(List<UiAction> actions, UiText reason) {
            return actions.stream().map(a -> a.disabled(reason)).toList();
        }

        private UiView list(UiText title, UiText body, Stream<UiRow> rows, List<UiAction> actions) {
            return list(title, body, rows, actions, t("list.empty", "No matching records. Clear search, return to the first page, or use an available creation action."));
        }

        private UiView list(UiText title, UiText body, Stream<UiRow> rows, List<UiAction> actions, UiText empty) {
            String search = query.search().strip().toLowerCase(Locale.ROOT);
            List<UiRow> slice = rows.filter(row -> search.isEmpty() || (row.title().fallback() + " "
                            + row.detail().fallback() + " " + row.entity().id()).toLowerCase(Locale.ROOT).contains(search))
                    .skip(query.offset()).limit(UiView.PAGE_SIZE + 1L).toList();
            boolean more = slice.size() > UiView.PAGE_SIZE;
            return new UiView(title, body, more ? slice.subList(0, UiView.PAGE_SIZE) : slice,
                    actions.stream().limit(UiView.MAX_ACTIONS).toList(), query.offset(), more, empty);
        }
    }

    private static boolean passes(Runnable check) {
        try { check.run(); return true; }
        catch (UserError unavailable) { return false; }
    }

    private static boolean has(Set<String> choices, String value) {
        return value != null && choices.contains(value);
    }
}
