package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiRow;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.Election;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.Mail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.statecraft.domain.GovernanceEngine.check;

/** A read-only projection: no login, ticks, command execution, or preview mutations. */
public final class GovernmentOverviewService {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final Comparator<PersonalDashboard.Entry> BY_NAME =
            Comparator.comparing((PersonalDashboard.Entry entry) -> entry.name().fallback().toLowerCase(Locale.ROOT))
                    .thenComparing(entry -> entry.target().entity().id());
    private final GovernanceEngine engine;
    private final EconomyAccess economy;

    public GovernmentOverviewService(GovernanceEngine engine, EconomyAccess economy) {
        this.engine = Objects.requireNonNull(engine);
        this.economy = economy == null ? EconomyAccess.UNAVAILABLE : economy;
        CoreMenus.register();
    }

    public GovernmentOverview describe(Actor actor, GovernmentOverview.Request request) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(request);
        synchronized (engine) {
            check(GovernanceEngine.validUuid(request.governmentId()), "Invalid government reference.");
            Government government = engine.data.governments.get(request.governmentId());
            check(engine.viewableGovernment(government), "This government is missing or needs an operator's repair.");
            Government parent = government.parentId == null ? null : engine.data.governments.get(government.parentId);
            return new GovernmentOverview(government.id, government.kind, governmentName(government),
                    clean(government.flag, 128), clean(government.description, 2000),
                    person(government.leader, leadership(government.kind)), parent == null ? null : governmentEntry(parent),
                    treasury(actor, government), PersonalDashboard.Page.of(children(government), request.children()),
                    PersonalDashboard.Page.of(officers(government), request.officers()),
                    new Actions(actor, government).groups(), engine.canManage(actor, government)
                    ? new UiQuery("statecraft:official_mail", new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, government.id), "", 0)
                    : null);
        }
    }

    public UiView officialMailbox(Actor actor, String governmentId, String search, int offset) {
        Objects.requireNonNull(actor);
        check(GovernanceEngine.validUuid(governmentId), "Invalid government reference.");
        check(search != null && search.length() <= 80 && offset >= 0 && offset <= 100_000, "Invalid official mailbox page.");
        synchronized (engine) {
            Government government = engine.data.governments.get(governmentId);
            check(engine.viewableGovernment(government), "This government is missing or needs an operator's repair.");
            engine.manage(actor, government);
            String owner = engine.account(government);
            var copies = new ArrayList<MailboxCopy>();
            collectMail(copies, owner, government.inbox, false);
            collectMail(copies, owner, government.sent, true);
            String term = search.strip().toLowerCase(Locale.ROOT);
            List<UiRow> rows = copies.stream().sorted(Comparator.comparingLong((MailboxCopy copy) -> copy.mail().sentAt).reversed()
                            .thenComparing(copy -> copy.mail().id).thenComparing(MailboxCopy::sent))
                    .map(copy -> mailRow(owner, copy))
                    .filter(row -> (row.title().fallback() + " " + row.detail().fallback()).toLowerCase(Locale.ROOT).contains(term))
                    .toList();
            int last = rows.isEmpty() ? 0 : (rows.size() - 1) / UiView.PAGE_SIZE * UiView.PAGE_SIZE;
            int start = Math.min(offset, last);
            int end = Math.min(rows.size(), start + UiView.PAGE_SIZE);
            return new UiView(text("mail.title", "%s — Official inbox", governmentName(government)),
                    text("mail.body", "Inbox and sent mail for %s. Open a message to read or reply; this list does not mark messages read.",
                            governmentName(government)),
                    rows.subList(start, end), new Actions(actor, government).mailboxActions(), start, end < rows.size(),
                    term.isEmpty() ? text("mail.empty", "This government has no official messages.")
                            : text("mail.no_matches", "No official messages match this filter."));
        }
    }

    private record MailboxCopy(Mail mail, boolean sent) {}

    private static void collectMail(List<MailboxCopy> copies, String owner, List<Mail> messages, boolean sent) {
        if (messages == null) return;
        var seen = new LinkedHashSet<String>();
        messages.stream().filter(Objects::nonNull).filter(mail -> GovernanceEngine.validUuid(mail.id))
                .filter(mail -> owner.equals(sent ? mail.sender : mail.recipient))
                .filter(mail -> seen.add(mail.id))
                .map(mail -> new MailboxCopy(mail, sent)).forEach(copies::add);
    }

    private UiRow mailRow(String owner, MailboxCopy copy) {
        Mail mail = copy.mail();
        String sender = cleanName(GovernancePresentation.account(engine, mail.sender), "Unknown sender", 256);
        UiText detail = copy.sent()
                ? text("mail.sent_row", "Sent · From %s to %s · %s", sender,
                        cleanName(GovernancePresentation.account(engine, mail.recipient), "Unknown recipient", 256),
                        DisplayText.date(mail.sentAt))
                : text("mail.inbox_row", "Inbox · From %s · %s · %s", sender, DisplayText.date(mail.sentAt),
                        (mail.read ? text("mail.read", "Read") : text("mail.unread", "Unread")).fallback());
        return new UiRow(UiText.literal(cleanName(mail.subject, "Untitled official message", 128)), detail,
                new EntityRef("statecraft", EntityRef.Kind.MAIL, GovernancePresentation.mailId(owner, copy.sent(), mail)));
    }

    private UiText treasury(Actor actor, Government government) {
        if (!economy.available()) return text("economy_missing", "Economy not installed");
        String account = engine.account(government);
        if (!actor.admin() && !engine.mayAccessAccount(actor.id(), account)) {
            return text("treasury_private", "Officials only");
        }
        try { return UiText.literal(Money.format(economy.balance(account))); }
        catch (UserError unavailable) { return text("treasury_unavailable", "Balance unavailable"); }
    }

    private List<PersonalDashboard.Entry> children(Government government) {
        if (government.kind == Kind.CITY) {
            return engine.data.claims.values().stream().filter(engine::viewableClaim)
                    .filter(claim -> government.id.equals(claim.cityId))
                    .sorted(Comparator.comparing((Claim claim) -> ChunkKey.parse(claim.key).dimension())
                            .thenComparingInt(claim -> ChunkKey.parse(claim.key).x())
                            .thenComparingInt(claim -> ChunkKey.parse(claim.key).z()))
                    .map(claim -> {
                        check(claim.key.length() <= 256, "A claim reference is too long for this overview. Ask an operator to inspect it.");
                        return new PersonalDashboard.Entry(UiText.literal(clean(DisplayText.chunk(claim.key), 256)),
                                text("political_claim", "Assigned territory"), target(EntityRef.Kind.CLAIM, claim.key));
                    }).toList();
        }
        Kind childKind = government.kind == Kind.NATION ? Kind.STATE : Kind.CITY;
        return engine.data.governments.values().stream().filter(engine::viewableGovernment)
                .filter(child -> child.kind == childKind && government.id.equals(child.parentId))
                .map(this::governmentEntry).sorted(BY_NAME).toList();
    }

    private List<PersonalDashboard.Entry> officers(Government government) {
        List<PersonalDashboard.Entry> entries = new ArrayList<>();
        if (government.leader != null) entries.add(person(government.leader, leadership(government.kind)));
        government.officers.stream().filter(GovernanceEngine::validUuid)
                .filter(id -> !id.equals(government.leader) && engine.member(id, government))
                .map(id -> person(id, text("officer", "Appointed officer"))).sorted(BY_NAME).forEach(entries::add);
        return List.copyOf(entries);
    }

    private PersonalDashboard.Entry person(String id, UiText role) {
        return id == null ? null : new PersonalDashboard.Entry(
                UiText.literal(cleanName(GovernancePresentation.person(engine, id), "Former player", 64)),
                role, target(EntityRef.Kind.PLAYER, id));
    }

    private PersonalDashboard.Entry governmentEntry(Government government) {
        String label = government.kind == Kind.CITY ? cityName(government) : governmentName(government);
        return new PersonalDashboard.Entry(UiText.literal(label), kind(government.kind),
                target(EntityRef.Kind.GOVERNMENT, government.id));
    }

    private String cityName(Government city) {
        Government state = engine.data.governments.get(city.parentId);
        Government nation = state == null ? null : engine.data.governments.get(state.parentId);
        return "[" + governmentName(city) + "] (" + (state == null ? "Former state" : governmentName(state))
                + "/" + (nation == null ? "Former nation" : governmentName(nation)) + ")";
    }

    private static String governmentName(Government government) {
        return cleanName(government.name, "Former government", 64);
    }

    private static String cleanName(String name, String missing, int limit) {
        String result = clean(DisplayText.name(name, missing), limit);
        return result.isBlank() ? missing : result;
    }

    private static String clean(String value, int limit) {
        String result = UUID_TEXT.matcher(Objects.toString(value, "")).replaceAll("[reference]")
                .replaceAll("\\p{Cntrl}", " ").strip();
        return GovernancePresentation.clip(result, limit);
    }

    private static UiQuery target(EntityRef.Kind kind, String id) {
        return UiQuery.detail(new EntityRef("statecraft", kind, id));
    }

    private static UiText leadership(Kind kind) {
        return switch (kind) {
            case NATION -> text("leader", "National leader");
            case STATE -> text("governor", "Governor");
            case CITY -> text("mayor", "Mayor");
        };
    }

    private static UiText kind(Kind kind) {
        return switch (kind) {
            case NATION -> text("nation", "Nation");
            case STATE -> text("state", "State");
            case CITY -> text("city", "City");
        };
    }

    private static UiText text(String key, String template, String... arguments) {
        return UiText.tr("ui.statecraft.overview." + key,
                String.format(Locale.ROOT, template, (Object[]) arguments), arguments);
    }

    private final class Actions {
        private final Actor actor;
        private final Government government;
        private final String player;
        private final String family;
        private final String governmentPage;
        private final Map<String, String> own;
        private final Map<String, String> scope;
        private final List<GovernmentOverview.Group> groups = new ArrayList<>();
        private final Set<String> used = new LinkedHashSet<>();

        Actions(Actor actor, Government government) {
            this.actor = actor;
            this.government = government;
            player = actor.id().toString();
            family = government.kind.name().toLowerCase(Locale.ROOT);
            governmentPage = switch (government.kind) { case NATION -> "nations"; case STATE -> "states"; case CITY -> "cities"; };
            own = Map.of(family, government.id);
            scope = Map.of("government", government.id);
        }

        List<GovernmentOverview.Group> groups() {
            if (government.kind == Kind.NATION) {
                diplomacy();
                executive();
                legislature();
                elections();
            } else children();
            settings();
            contracts();
            return List.copyOf(groups);
        }

        private void diplomacy() {
            var actions = new ArrayList<UiAction>();
            add(actions, "diplomacy", "diplomacy status <nation> <page>", own, this::publicAction);
            add(actions, "diplomacy", "diplomacy proposals <nation> <page>", own, this::publicAction);
            Map<String, String> from = Map.of("from_nation", government.id);
            add(actions, "diplomacy", "diplomacy alliance <from_nation> <to_nation> <message>", from, this::lead);
            add(actions, "diplomacy", "diplomacy break <nation> <ally>", own, this::lead);
            add(actions, "diplomacy", "diplomacy war <from_nation> <to_nation> <reason>", from, this::lead);
            add(actions, "diplomacy", "diplomacy peace <from_nation> <to_nation> <offer_amount> <demand_amount> <chunk_terms_or_dash> <message>",
                    Map.of("from_nation", government.id, "chunk_terms_or_dash", "-", "offer_amount", "0", "demand_amount", "0"), this::lead);
            add(actions, "diplomacy", "diplomacy truce <from_nation> <to_nation>", from, this::lead);
            group("diplomacy", "Diplomacy", actions);
        }

        private void executive() {
            var actions = new ArrayList<UiAction>();
            add(actions, "executive", "executive status <nation>", own, this::publicAction);
            add(actions, "executive", "executive emergency <nation> <policy> <value> <reason>", own, () -> {
                lead();
                check(!engine.data.emergencies.containsKey(government.id), "An emergency order is already active.");
                check(government.lastEmergencyAt < 0 || engine.now() >= government.lastEmergencyAt
                        && engine.now() - government.lastEmergencyAt >= engine.config.emergencyCooldownMillis,
                        "Emergency executive power is on cooldown.");
            });
            add(actions, "executive", "executive rescind <nation>", own, () -> {
                lead();
                check(engine.data.emergencies.containsKey(government.id), "No emergency order is active.");
            });
            add(actions, "states", "state create <nation> <name> <governor>", own, "Create state", () -> {
                manage();
                engine.checkCreationCapacity(Kind.STATE, government);
            });
            group("executive", "Executive Actions", actions);
        }

        private void legislature() {
            var actions = new ArrayList<UiAction>();
            add(actions, "legislature", "bill list <nation> <page>", own, this::publicAction);
            add(actions, "legislature", "bill propose <nation> <policy> <value> <title> <text>", own, this::propose);
            add(actions, "legislature", "bill propose <nation> roleplay - <title> <text>", own, this::propose);
            add(actions, "legislature", "bill amend <nation> <policy> <value> <title> <text>", own, this::propose);
            add(actions, "laws", "law list <nation> <page>", own, this::publicAction);
            add(actions, "laws", "law read <nation> <law>", own, this::publicAction);
            group("legislature", "Legislature & Laws", actions);
        }

        private void elections() {
            var actions = new ArrayList<UiAction>();
            add(actions, "elections", "election status <nation>", own, this::publicAction);
            add(actions, "elections", "election candidates <nation> <page>", own, this::publicAction);
            add(actions, "elections", "election candidate <nation>", own, () -> {
                Election election = election();
                check(engine.member(player, government), "Candidates must be current citizens.");
                check(!election.voting && !election.scheduleExhausted, "Candidate registration is closed.");
                check(!election.candidates.contains(player), "You are already registered.");
                check(election.candidates.size() < engine.config.maxMembersPerNation, "Candidate limit reached.");
            });
            add(actions, "elections", "election withdraw <nation>", own, () -> {
                Election election = election();
                check(!election.voting, "Candidates cannot withdraw after voting opens.");
                check(election.candidates.contains(player), "You are not registered.");
            });
            add(actions, "elections", "election vote <nation> <candidate>", own, () -> {
                Election election = election();
                check(election.voting && engine.now() < election.endsAt, "The election is not accepting votes.");
                check(election.electorate.contains(player) && engine.member(player, government),
                        "Only citizens eligible when voting opened may vote.");
                check(!election.votes.containsKey(player), "You have already voted in this election.");
            });
            add(actions, "elections", "election history <nation> <page>", own, this::publicAction);
            group("elections", "Elections", actions);
        }

        private void children() {
            var actions = new ArrayList<UiAction>();
            if (government.kind == Kind.STATE) {
                add(actions, "cities", "city create <state> <name> <mayor>", own, "Create city", () -> {
                    manage();
                    engine.checkCreationCapacity(Kind.CITY, government);
                });
            }
            add(actions, "claims", "chunk list <government> <page>", scope, this::publicAction);
            group("children", government.kind == Kind.STATE ? "Cities & Territory" : "Assigned territory", actions);
        }

        private void settings() {
            if (!engine.canManage(actor, government)) return;
            var actions = new ArrayList<UiAction>();
            add(actions, governmentPage, family + " settings <" + family + ">", own, this::manage);
            add(actions, governmentPage, family + " setting <" + family + "> <key> <value>", own, this::lead);
            add(actions, governmentPage, family + " rename <" + family + "> <name>", own, this::manage);
            add(actions, governmentPage, family + " description <" + family + "> <description>", own, this::manage);
            add(actions, governmentPage, family + " tag <" + family + "> <tag>", own, this::manage);
            add(actions, governmentPage, family + " flag <" + family + "> <flag>", own, this::manage);
            add(actions, "members", "government leader <government> <player>", scope, this::lead);
            add(actions, "officers", "government officer <government> <player> add", scope, "Appoint officer", () -> {
                lead();
                check(government.officers.size() < engine.config.maxOfficers, "Officer limit reached.");
            });
            add(actions, "officers", "government officer <government> <player> remove", scope, "Remove officer", this::lead);
            add(actions, "invitations", "government invite <government> <player>", scope, "Invite player", this::manage);
            add(actions, "invitations", "government invitations <government> <page>", scope, "Manage invitations", this::manage);
            add(actions, "invitations", "government revoke <government> <player>", scope, "Revoke invitation", this::manage);
            add(actions, governmentPage, family + " disband <" + family + ">", own, this::lead);
            add(actions, governmentPage, family + " disband <" + family + "> cascade", own, this::lead);
            group("settings", "Settings", actions);
        }

        private void contracts() {
            var actions = new ArrayList<UiAction>();
            add(actions, "contracts", "contract list <government> <page>", scope, this::publicAction);
            add(actions, "contracts", "contract create <government> <title> <description> <chunks>", scope, "Create contract", this::manage);
            group("contracts", "Contracts", actions);
        }

        private List<UiAction> mailboxActions() {
            var actions = new ArrayList<UiAction>();
            add(actions, "official_mail", "mail official inbox <government> <page>", scope, this::manage);
            add(actions, "official_mail", "mail official sent <government> <page>", scope, this::manage);
            add(actions, "official_mail", "mail official read <government> <message>", scope, this::manage);
            add(actions, "official_mail", "mail official send <government> <recipient> <subject> <body>", scope, this::manage);
            add(actions, "official_mail", "mail official reply <government> <message> <body>", scope, this::manage);
            add(actions, "official_mail", "mail official delete <government> <message>", scope, this::manage);
            return List.copyOf(actions);
        }

        private void group(String id, String label, List<UiAction> actions) {
            groups.add(new GovernmentOverview.Group(id, text("group." + id + ("children".equals(id) ? "." + family : ""), label), actions));
        }

        private void add(List<UiAction> actions, String page, String template, Map<String, String> values, Runnable permission) {
            add(actions, page, template, values, null, permission);
        }

        private void add(List<UiAction> actions, String page, String template, Map<String, String> values,
                         String label, Runnable permission) {
            String pageId = "statecraft:" + page;
            MenuPage.Action registered = MenuRegistry.get(pageId).actions().stream()
                    .filter(action -> template.equals(action.command())).findFirst().orElse(null);
            if (registered == null) return;
            if (!used.add(pageId + "\n" + template)) throw new IllegalStateException("Duplicate government menu action.");
            var seeded = new java.util.LinkedHashMap<>(values);
            if (new dev.statecraft.api.CommandTemplate(template).fields().contains("page")) seeded.put("page", "1");
            String caption = label == null ? registered.label() : label;
            UiAction action = new UiAction(pageId, template,
                    text("action." + GovernancePresentation.actionKey(caption), caption), seeded);
            try {
                if (registered.intent() != ActionIntent.QUERY) {
                    check(actor.admin() || !engine.requiresRepair(), "Governance data requires operator repair.");
                }
                permission.run();
            } catch (UserError unavailable) {
                action = action.disabled(text("unavailable", "%s", clean(unavailable.getMessage(), 512)));
            }
            actions.add(action);
        }

        private void publicAction() {}
        private void manage() { engine.manage(actor, government); }
        private void lead() { engine.executive(actor, government); }
        private void propose() {
            check(actor.admin() || engine.member(player, government)
                    && (engine.config.allowCitizenBills || engine.politics.legislator(player, government)),
                    "Only eligible national citizens may introduce a bill.");
            long active = engine.data.bills.values().stream().filter(Objects::nonNull)
                    .filter(bill -> government.id.equals(bill.nationId))
                    .filter(bill -> bill.status != null
                            && Set.of("DEBATE", "VOTING", "PASSED", "VETOED", "OVERRIDE_VOTING").contains(bill.status)).count();
            check(active < engine.config.maxActiveBillsPerNation, "Active bill limit reached.");
        }
        private Election election() {
            Election election = engine.data.elections.get(government.id);
            check(election != null, "No election is scheduled.");
            return election;
        }
    }
}
