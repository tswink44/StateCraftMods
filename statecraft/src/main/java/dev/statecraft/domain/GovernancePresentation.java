package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
                case "claims", "map" -> list(section(), t("claims.body", "Political jurisdiction and private title are separate. Select a claim for permissions and actions."),
                        validClaims().filter(c -> !"map".equals(page) || c.key.startsWith(actor.dimension() + "|")).map(this::claimRow), pageActions());
                case "companies" -> list(section(), t("companies.body", "Employment, management authority and equity are separate."),
                        validCompanies().map(this::companyRow), pageActions());
                case "legislature" -> list(section(), t("bills.body", "National bills and their retained history. Local policies remain local executive actions."),
                        validBills().map(this::billRow), pageActions());
                case "elections" -> list(section(), t("elections.body", "National elections use a frozen electorate and require current citizenship to cast a ballot."),
                        validElections().map(this::electionRow), pageActions());
                case "laws" -> list(section(), t("laws.body", "Retained enacted laws. Their historical parent references may outlive an organization."),
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
                    yield list(section(), t("mail.body", "Your personal inbox and sent copies. Opening a message marks only that authorized copy read."),
                            Stream.concat(mailRows(actor.account(), known.inbox, false), mailRows(actor.account(), known.sent, true)), pageActions());
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
                case "help" -> list(section(), t("help.body", "Select a registered help action for command syntax. Commands remain server-authoritative."),
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
                            e.economy.available(), e.requiresRepair()),
                    validGovernments().filter(g -> e.member(player, g)).map(this::governmentRow), pageActions());
        }

        private UiView governments(java.util.function.Predicate<Government> filter) {
            return list(section(), t("governments.body", "Nation → State → City. Authority extends to descendants, never to unrelated siblings."),
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
            return list(t("dashboard.title", "Pending governance work"), body, work, pageActions(),
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
                actions.add(action("legislature", "bill propose <nation> <policy> <value> <title> <text>", Map.of("nation", g.id), () ->
                        check(actor.admin() || e.member(player, g) && (e.config.allowCitizenBills || e.politics.legislator(player, g)),
                                "Only eligible national citizens may introduce a bill.")));
            } else if (g.kind == Kind.STATE) {
                actions.add(action("cities", "city create <state> <name> <mayor>", Map.of("state", g.id), () -> e.manage(actor, g)));
            } else {
                actions.add(action("claims", "chunk claim <city> here", Map.of("city", g.id), () -> e.validateClaim(actor, g, actor.chunkKey())));
            }
            actions.add(action(page, family + " disband <" + family + "> cascade", seed, () -> {
                e.executive(actor, g);
                e.disbandPlan(g, true, actor.admin());
            }));
            Stream<UiRow> children = validGovernments().filter(child -> g.id.equals(child.parentId)).map(this::governmentRow);
            Stream<UiRow> citizens = validPlayers().filter(p -> e.member(p.id, g)).map(this::playerRow);
            return list(UiText.literal(label(g.name)), t("government.detail",
                            "Level: %s\nLeader: %s\nParent: %s\nCitizens: %s\nTreasury: %s\nTag: %s\nFlag: %s\nDescription: %s\nEffective settings: %s\nLegislation-only restriction: %s",
                            g.kind, person(g.leader), governmentName(g.parentId), e.members(g).size(), e.account(g), g.tag,
                            g.flag, g.description, e.settings(g), e.config.requireLegislationForPolicy && g.kind == Kind.NATION),
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
                            company.shares.getOrDefault(player, 0L), "company:" + id, company.description),
                    validPlayers().filter(p -> company.members.contains(p.id) || company.shares.containsKey(p.id))
                            .map(p -> new UiRow(UiText.literal(label(p.name)), t("company.participant", "Shares: %s; employed: %s; officer: %s",
                                    company.shares.getOrDefault(p.id, 0L), company.members.contains(p.id), company.officers.contains(p.id)),
                                    ref(EntityRef.Kind.PLAYER, p.id))), actions);
        }

        private UiView claim(Claim claim) {
            Government city = e.data.governments.get(claim.cityId);
            Map<String, String> seed = Map.of("chunk", claim.key);
            List<UiAction> actions = List.of(
                    action("claims", "chunk protection <chunk> <player>", seed, () -> { }),
                    action("claims", "chunk permits <chunk> <page>", Map.of("chunk", claim.key, "page", "1"), () -> titleManager(claim)),
                    action("claims", "chunk permit <chunk> <player> <actions>", seed, () -> titleManager(claim)),
                    action("claims", "chunk unclaim <chunk>", seed, () -> e.unclaimPlan(actor, claim.key, false)),
                    action("admin", "admin reassign <chunk> <city>", seed, () -> e.admin(actor)));
            return list(UiText.literal(label(claim.key)), t("claim.detail",
                            "City: %s\nState: %s\nNation: %s\nPrivate title: %s\nImprovements: %s\nClaimed at: %s\nEconomy reservation: %s\nContract reservation: %s\nTreaty reservation: %s",
                            governmentName(claim.cityId), governmentName(city.parentId), governmentName(e.nationId(city)),
                            account(claim.ownerAccount), claim.improvements, claim.claimedAt,
                            e.economy.isClaimEncumbered(claim.key), e.commerce.claimLocked(claim.key), e.politics.claimLocked(claim.key, null)),
                    Stream.of(governmentRow(city)), actions);
        }

        private UiView wilderness(String key) {
            Map<String, String> seed = Map.of("chunk", key);
            List<UiAction> actions = List.of(
                    action("claims", "chunk claim <city> <chunk>", seed, () ->
                            check(validGovernments().anyMatch(g -> g.kind == Kind.CITY && manage(g)),
                                    "You must manage an existing city before claiming territory.")),
                    action("claims", "chunk info <chunk>", seed, () -> { }));
            return list(t("claim.wilderness_title", "Unclaimed territory"),
                    t("claim.wilderness_body", "Chunk: %s\nNo government currently claims this land. Select an eligible city to review its claim fee, capacity and adjacency requirements.", key),
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
                    .map(v -> new UiRow(UiText.literal(label(person(v.getKey()))), t("ballot.choice", "Vote: %s", v.getValue()),
                            e.data.players.containsKey(v.getKey()) ? ref(EntityRef.Kind.PLAYER, v.getKey()) : EntityRef.NONE));
            return list(UiText.literal(label(bill.title)), t("bill.detail",
                    "Nation: %s\nStatus: %s\nAuthor: %s\nPolicy: %s = %s\nAmendment: %s\nDebate ends: %s\nVoting ends: %s\nDecision deadline: %s\nEligible voters: %s\nText: %s",
                    governmentName(bill.nationId), bill.status, person(bill.author), bill.policy, bill.value, bill.amendment,
                    bill.debateEndsAt, bill.voteEndsAt, bill.decisionEndsAt, bill.electorate.size(), bill.text),
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
                            "Voting: %s\nRegistration closes: %s\nVoting ends: %s\nCandidates: %s\nEligible voters: %s\nCast ballots: %s\nSchedule exhausted: %s",
                            election.voting, election.nextStartAt, election.endsAt, election.candidates.size(), election.electorate.size(),
                            election.votes.size(), election.scheduleExhausted),
                    validPlayers().filter(p -> election.candidates.contains(p.id)).map(this::playerRow), actions);
        }

        private UiView law(String id) {
            String[] parts = id.split(":", -1);
            check(parts.length == 2 && GovernanceEngine.validUuid(parts[0]) && GovernanceEngine.validUuid(parts[1]), "Invalid law reference.");
            Law law = e.data.laws.getOrDefault(parts[0], List.of()).stream().filter(Objects::nonNull)
                    .filter(l -> parts[1].equals(l.id)).findFirst().orElseThrow(() -> new UserError("This law is not retained in that codex."));
            List<UiAction> actions = List.of(action("laws", "law read <nation> <law>", Map.of("nation", parts[0], "law", law.id),
                    () -> e.politics.nation(parts[0])));
            return list(UiText.literal(label(law.title)), t("law.detail",
                            "Nation: %s\nPolicy: %s = %s\nEnacted at: %s\nAmendment: %s\nRoleplay only: %s\nText: %s",
                            governmentName(parts[0]), law.policy, law.value, law.enactedAt, law.amendment, law.roleplayOnly, law.text),
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
                    new UiRow(UiText.literal(label(term.key)), t("treaty.chunk", "%s → %s", governmentName(term.fromCity), governmentName(term.toCity)),
                            e.data.claims.containsKey(term.key) ? ref(EntityRef.Kind.CLAIM, term.key) : EntityRef.NONE));
            Stream<UiRow> ballots = validBills().filter(b -> proposal.id.equals(b.treatyId)).map(this::billRow);
            return list(t("diplomacy.title", "%s proposal", proposal.type), t("diplomacy.detail",
                            "From: %s\nTo: %s\nStatus: %s\nExpires: %s\nProposer offers: %s\nCounterparty pays: %s\nRatified nations: %s\nSettlement issue: %s\nMessage: %s",
                            governmentName(proposal.fromNation), governmentName(proposal.toNation), proposal.status,
                            proposal.expiresAt, Money.format(proposal.offeredCents), Money.format(proposal.demandedCents),
                            proposal.ratified.stream().map(this::governmentName).toList(), proposal.lastError, proposal.message),
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
                            "Company: %s\nStatus: %s\nDecision: %s = %s\nVoting ends: %s\nSettlement deadline: %s\nBlocked reason: %s\nText: %s",
                            companyName(proposal.companyId), proposal.status, proposal.type, proposal.value, proposal.endsAt,
                            settlementDeadline(proposal), proposal.lastError, proposal.text),
                    proposal.electorate.entrySet().stream().filter(holder -> GovernanceEngine.validUuid(holder.getKey()))
                            .map(holder -> new UiRow(UiText.literal(label(person(holder.getKey()))),
                                    t("shareholder.ballot", "Frozen weight: %s; vote: %s", holder.getValue(), proposal.votes.getOrDefault(holder.getKey(), "—")),
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
            Stream<UiRow> chunks = contract.chunks.stream().map(key -> new UiRow(UiText.literal(label(key)),
                    t("contract.chunk", "Selected contract territory"), e.data.claims.containsKey(key) ? ref(EntityRef.Kind.CLAIM, key) : EntityRef.NONE));
            return list(UiText.literal(label(contract.title)), t("contract.detail",
                            "Issuer: %s\nStatus: %s\nAuthor: %s\nBidding ends: %s\nReview ends: %s\nWinner: %s\nOriginal payee: %s\nEscrow: %s\nDescription: %s\nSubmission: %s\nDetailed bids visible: %s",
                            governmentName(contract.governmentId), contract.status, person(contract.author), contract.bidEndsAt,
                            contract.reviewEndsAt, person(contract.winner), account(contract.payeeAccount), Money.format(contract.escrowCents),
                            contract.description, contract.completionNote, manage(issuer)), Stream.concat(bids, chunks), actions);
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
            return list(t("bid.title", "Bid: %s", label(e.playerName(bid.bidder))),
                            t("bid.detail", "Contract: %s\nBidder: %s\nPayee: %s\nAmount: %s\nSubmitted at: %s\nDescription: %s",
                                    contract.title, person(bid.bidder), account(bid.account), Money.format(bid.cents), bid.createdAt, bid.text),
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
                    t("invitation.detail", "Organization: %s\nInvitee: %s\nInvited by: %s\nExpires: %s\nLive: %s",
                            invitationOrganization(invitation), person(invitation.playerId), person(invitation.invitedBy), invitation.expiresAt, live),
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
            String body = message.body == null ? "" : message.body;
            return list(UiText.literal(label(message.subject)), t("mail.detail", "From: %s\nTo: %s\nSent at: %s\nCopy: %s\n\n%s%s",
                            account(message.sender), account(message.recipient), message.sentAt, entry.sent() ? "sent" : "inbox",
                            body.substring(0, Math.min(2048, body.length())), body.length() > 2048 ? body.substring(2048) : ""),
                    Stream.empty(), actions, t("mail.empty", "Deleting this copy does not remove the other party's copy."));
        }

        private UiView profile(Player known) {
            Stream<UiRow> citizenship = validGovernments().filter(g -> e.member(known.id, g)).map(this::governmentRow);
            Stream<UiRow> companies = validCompanies().filter(c -> c.members.contains(known.id) || c.shares.getOrDefault(known.id, 0L) > 0).map(this::companyRow);
            UiText body = player.equals(known.id)
                    ? t("profile.self", "Player: %s\nID: %s\nUnread personal mail: %s\nAutomatic claiming: %s",
                    known.name, known.id, known.inbox.stream().filter(m -> m != null && actor.account().equals(m.recipient) && !m.read).count(), known.autoClaim)
                    : t("profile.public", "Player: %s\nID: %s\nCitizenship and company participation are public; personal mail is private.", known.name, known.id);
            return list(UiText.literal(label(known.name)), body, Stream.concat(citizenship, companies),
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
                    government.kind, person(government.leader), e.members(government).size()), ref(EntityRef.Kind.GOVERNMENT, government.id));
        }
        private UiRow companyRow(Company company) {
            return new UiRow(UiText.literal(label(company.name)), t("company.row", "Owner: %s; issued shares: %s",
                    person(company.owner), company.totalShares), ref(EntityRef.Kind.COMPANY, company.id));
        }
        private UiRow claimRow(Claim claim) {
            EntityRef reference = ref(EntityRef.Kind.CLAIM, claim.key);
            UiText detail = reference.present() ? t("claim.row", "City: %s; private title: %s",
                    governmentName(claim.cityId), account(claim.ownerAccount))
                    : t("claim.long_key", "This existing chunk key exceeds the UI identity limit. Use the claim commands to inspect it.");
            return new UiRow(UiText.literal(label(claim.key)), detail, reference);
        }
        private UiRow playerRow(Player known) {
            return new UiRow(UiText.literal(label(known.name)), t("player.row", "Nation: %s; state: %s; city: %s",
                    governmentName(known.nationId), governmentName(known.stateId), governmentName(known.cityId)), ref(EntityRef.Kind.PLAYER, known.id));
        }
        private UiRow billRow(Bill bill) {
            return new UiRow(UiText.literal(label(bill.title)), t("bill.row", "%s; nation: %s; policy: %s = %s",
                    bill.status, governmentName(bill.nationId), bill.policy, bill.value), ref(EntityRef.Kind.BILL, bill.id));
        }
        private UiRow electionRow(Election election) {
            return new UiRow(UiText.literal(label(governmentName(election.nationId))), t("election.row", "Voting: %s; registration closes: %s; voting ends: %s",
                    election.voting, election.nextStartAt, election.endsAt), ref(EntityRef.Kind.ELECTION, election.nationId));
        }
        private UiRow lawRow(String nation, Law law) {
            return new UiRow(UiText.literal(label(law.title)), t("law.row", "Nation: %s; policy: %s = %s; roleplay only: %s",
                    governmentName(nation), law.policy, law.value, law.roleplayOnly), ref(EntityRef.Kind.LAW, nation + ":" + law.id));
        }
        private UiRow contractRow(Contract contract) {
            return new UiRow(UiText.literal(label(contract.title)), t("contract.row", "%s; issuer: %s; payee: %s; escrow: %s",
                    contract.status, governmentName(contract.governmentId), account(contract.payeeAccount), Money.format(contract.escrowCents)),
                    ref(EntityRef.Kind.CONTRACT, contract.id));
        }
        private UiRow diplomacyRow(DiplomaticProposal proposal) {
            return new UiRow(t("diplomacy.row_title", "%s: %s → %s", proposal.type, label(e.governmentName(proposal.fromNation)), label(e.governmentName(proposal.toNation))),
                    t("diplomacy.row", "%s; expires: %s; blocked: %s", proposal.status, proposal.expiresAt, proposal.lastError),
                    ref(EntityRef.Kind.DIPLOMACY, proposal.id));
        }
        private UiRow companyProposalRow(CompanyProposal proposal) {
            return new UiRow(UiText.literal(label(proposal.title)), t("company_proposal.row", "%s; company: %s; decision: %s; blocked: %s",
                    proposal.status, companyName(proposal.companyId), proposal.type, proposal.lastError), ref(EntityRef.Kind.COMPANY_PROPOSAL, proposal.id));
        }
        private UiRow invitationRow(Invitation invitation) {
            return new UiRow(t("invitation.row_title", "Invitation: %s", label(invitationOrganization(invitation))),
                    t("invitation.row", "Invitee: %s; expires: %s", person(invitation.playerId), invitation.expiresAt), ref(EntityRef.Kind.INVITATION, invitation.id));
        }
        private Stream<UiRow> mailRows(String owner, Collection<Mail> messages, boolean sent) {
            return messages.stream().filter(Objects::nonNull).filter(m -> GovernanceEngine.validUuid(m.id) && owner.equals(sent ? m.sender : m.recipient))
                    .map(m -> new UiRow(UiText.literal(label(m.subject)), t("mail.row", "Read: %s; mailbox: %s; copy: %s; sent at: %s",
                            m.read, account(owner), sent ? "sent" : "inbox", m.sentAt), ref(EntityRef.Kind.MAIL, mailId(owner, sent, m))));
        }
        private String invitationOrganization(Invitation invitation) {
            return invitation.governmentId == null ? companyName(invitation.companyId) : governmentName(invitation.governmentId);
        }
        private String person(String id) {
            return id == null ? "—" : e.playerName(id) + " [" + id + "]";
        }
        private String governmentName(String id) {
            return id == null ? "—" : e.governmentName(id) + " [" + id + "]";
        }
        private String companyName(String id) {
            return id == null ? "—" : e.companyName(id) + " [" + id + "]";
        }
        private String account(String value) {
            if (value == null) return "—";
            String[] parts = value.split(":", 2);
            if (parts.length != 2) return value;
            if ("player".equals(parts[0])) return e.playerName(parts[1]) + " [" + value + "]";
            if ("company".equals(parts[0])) return e.data.companies.containsKey(parts[1])
                    ? e.companyName(parts[1]) + " [" + value + "]"
                    : t("account.former_company", "Former company [%s]", value).fallback();
            Government government = e.data.governments.get(parts[1]);
            return government != null ? government.name + " [" + value + "]" : value;
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
                return action.disabled(t("action.unavailable", "Unavailable: %s", clip(unavailable.getMessage(), 450)));
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
