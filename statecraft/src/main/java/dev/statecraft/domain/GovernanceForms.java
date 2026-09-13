package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormProvider;
import dev.statecraft.domain.GovernanceData.Bill;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.Company;
import dev.statecraft.domain.GovernanceData.CompanyProposal;
import dev.statecraft.domain.GovernanceData.Contract;
import dev.statecraft.domain.GovernanceData.DiplomaticProposal;
import dev.statecraft.domain.GovernanceData.Election;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.Mail;
import dev.statecraft.domain.GovernanceData.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Read-only catalogs and defaults; commands remain the authoritative mutation path. */
public final class GovernanceForms implements FormProvider {
    private static final Set<String> BILL_ACTIVE = Set.of("DEBATE", "VOTING", "PASSED", "VETOED", "OVERRIDE_VOTING");
    private static final Set<String> DIPLOMACY_ACTIVE = Set.of("PROPOSED", "AWAITING_RATIFICATION", "READY");
    private static final Set<String> CONTRACT_ACTIVE = Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED");
    private final GovernanceEngine engine;

    public GovernanceForms(GovernanceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void describe(FormContext context, FormBuilder form) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(form, "form");
        synchronized (engine) {
            new Request(context, form).describe();
        }
    }

    private final class Request {
        private final FormContext context;
        private final FormBuilder form;
        private final Actor actor;
        private final String playerId;
        private final List<String> words;
        private final String family;
        private final String action;
        private final List<Player> players;
        private final List<Government> governments;
        private final List<Company> companies;

        Request(FormContext context, FormBuilder form) {
            this.context = context;
            this.form = form;
            actor = context.actor();
            playerId = actor.id().toString();
            words = context.words();
            family = word(0).toLowerCase(Locale.ROOT);
            action = word(1);
            players = engine.data.players.values().stream().filter(Objects::nonNull)
                    .filter(p -> GovernanceEngine.validUuid(p.id) && engine.data.players.get(p.id) == p).toList();
            governments = engine.data.governments.values().stream().filter(engine::viewableGovernment).toList();
            companies = engine.data.companies.values().stream().filter(engine::viewableCompany)
                    .filter(c -> passes(() -> engine.commerce.assertShareIntegrity(c))).toList();
        }

        void describe() {
            if (Set.of("economy", "statecraft_economy").contains(context.namespace())) {
                economyCommon();
                return;
            }
            if (!"statecraft".equals(context.namespace())) return;
            if (form.has("yes_no_abstain")) enumChoices("yes_no_abstain", "Vote explicitly", "yes", "no", "abstain");
            switch (family) {
                case "nation", "state", "city", "government" -> governmentForm();
                case "chunk" -> chunkForm();
                case "election" -> electionForm();
                case "bill", "law", "executive" -> legislationForm();
                case "diplomacy" -> diplomacyForm();
                case "company" -> companyForm();
                case "contract" -> contractForm();
                case "mail" -> mailForm();
                case "profile" -> playerChoices("player", p -> true, playerId);
                case "admin" -> adminForm();
                case "help" -> enumChoices("section", "Help section", "governments", "chunks", "elections",
                        "legislature", "executive", "diplomacy", "companies", "contracts", "mail", "profiles", "admin");
                default -> { }
            }
        }

        private boolean operational() {
            return actor.admin() || !engine.requiresRepair();
        }

        private boolean manage(Government government) {
            return government != null && engine.validHierarchy(government) && engine.canManage(actor, government);
        }

        private boolean executive(Government government) {
            return government != null && engine.validHierarchy(government)
                    && passes(() -> engine.executive(actor, government));
        }

        private boolean companyManager(Company company) {
            return company != null && (actor.admin() || engine.mayManageCompany(actor.id(), company.id));
        }

        private boolean companyOwner(Company company) {
            return company != null && (actor.admin() || playerId.equals(company.owner));
        }

        private boolean member(Government government) {
            return government != null && engine.member(playerId, government);
        }

        private void governmentForm() {
            if ("list".equals(action)) return;
            Kind kind = switch (family) {
                case "nation" -> Kind.NATION;
                case "state" -> Kind.STATE;
                case "city" -> Kind.CITY;
                default -> null;
            };
            if ("create".equals(action)) {
                text("name", "New " + family + " name", "Enter a new unique name; not an existing ID.", "", false);
                if (kind == Kind.NATION) {
                    playerChoices("leader", p -> operational() && (actor.admin() || playerId.equals(p.id))
                            && passes(() -> engine.checkCreationLeader(Kind.NATION, null, p)), playerId);
                    return;
                }
                Kind parentKind = kind == Kind.STATE ? Kind.NATION : Kind.STATE;
                String parentKey = kind == Kind.STATE ? "nation" : "state";
                govChoices(parentKey, g -> operational() && g.kind == parentKind && manage(g)
                                && passes(() -> engine.checkCreationCapacity(kind, g)),
                        own(parentKind), false);
                Government parent = selectedGovernment(parentKey);
                playerChoices(kind == Kind.STATE ? "governor" : "mayor",
                        p -> parent != null && passes(() -> engine.checkCreationLeader(kind, parent, p)),
                        playerId, parentKey);
                return;
            }
            String key = fieldAt(2);
            govChoices(key, g -> (kind == null || g.kind == kind) && governmentEligible(g, action),
                    own(kind), "list".equals(action));
            Government government = selectedGovernment(key);
            if (form.has("player")) {
                playerChoices("player", p -> government != null && governmentPerson(government, p), "", key);
            }
            if (government != null) {
                if ("rename".equals(action)) text("name", "New name", "Names must remain unique.", government.name, false, key);
                if ("description".equals(action)) text("description", "Description", "", government.description, true, key);
                if ("tag".equals(action)) text("tag", "Tag", "1-12 letters, digits, underscores or hyphens.", government.tag, false, key);
                if ("flag".equals(action)) text("flag", "Flag", "Single-line descriptive text, at most 128 characters.", government.flag, false, key);
            }
            if ("setting".equals(action)) policyFields(government, "key", false, true, key);
        }

        private boolean governmentEligible(Government government, String operation) {
            if (has(Set.of("list", "info", "members", "roles", "officers", "settings"), operation)) return true;
            if ("invitations".equals(operation))
                return manage(government) || engine.invitation(government.id, null, playerId) != null;
            if (!operational()) return false;
            return switch (operation) {
                case "join", "accept" -> {
                    Player player = knownActor();
                    boolean invited = engine.invitation(government.id, null, playerId) != null;
                    yield player != null && engine.eligibleInvitation(player, government)
                            && engine.members(government).size() < engine.memberLimit(government.kind)
                            && (invited || ("join".equals(operation) && engine.flag(government, "open")));
                }
                case "decline" -> engine.invitation(government.id, null, playerId) != null;
                case "leave" -> member(government) && canLeave(playerId, government);
                case "leader", "appoint", "officer", "disband", "setting" -> executive(government);
                case "invite", "revoke", "kick", "rename", "description", "tag", "flag" -> manage(government);
                default -> false;
            };
        }

        private boolean governmentPerson(Government government, Player player) {
            return switch (action) {
                case "leader", "appoint" -> engine.member(player.id, government) && !player.id.equals(government.leader);
                case "officer" -> engine.member(player.id, government) && !player.id.equals(government.leader)
                        && ("remove".equals(word(4)) ? government.officers.contains(player.id)
                        : !government.officers.contains(player.id) && government.officers.size() < engine.config.maxOfficers);
                case "kick" -> !playerId.equals(player.id) && engine.member(player.id, government)
                        && (actor.admin() || !engine.superior(player.id, government)) && canLeave(player.id, government);
                case "invite" -> !engine.member(player.id, government) && engine.eligibleInvitation(player, government)
                        && (engine.invitation(government.id, null, player.id) != null
                        || passes(() -> engine.checkInvitationCapacity(government.id, null, player.id)));
                case "revoke" -> engine.invitation(government.id, null, player.id) != null;
                default -> true;
            };
        }

        private boolean canLeave(String player, Government government) {
            return engine.subtree(government).stream().noneMatch(g -> player.equals(g.leader));
        }

        private void chunkForm() {
            if ("claim".equals(action)) {
                govChoices("city", g -> operational() && g.kind == Kind.CITY && manage(g)
                                && engine.data.claims.size() < engine.config.maxTotalClaims
                                && engine.cityClaims(g.id).size() < engine.config.maxClaimsPerCity,
                        own(Kind.CITY), false);
                choices("chunk", List.of(option("here", "Current chunk", actor.chunkKey())), "here", true,
                        "Enter unclaimed dimension|x|z coordinates; adjacency, fees and limits are checked on execution.", "city");
                return;
            }
            if ("list".equals(action)) {
                govChoices("government", g -> true, own(Kind.NATION), true);
                return;
            }
            claimChoices("chunk", c -> {
                if (has(Set.of("info", "protection"), action)) return true;
                if (!operational()) return false;
                if ("unclaim".equals(action)) return manage(engine.data.governments.get(c.cityId))
                        && titleManager(c) && coreClaimFree(c.key);
                return titleManager(c);
            }, false);
            Claim selected = selectedClaim("chunk");
            if ("permit".equals(action)) {
                playerChoices("player", p -> selected != null && titleManager(selected), "", "chunk");
                choices("actions", List.of(option("all", "All non-PvP actions"), option("none", "Remove permit"),
                        option("BREAK", "Break blocks"), option("PLACE", "Place blocks"),
                        option("BLOCK_INTERACT", "Use blocks / containers"), option("ENTITY_INTERACT", "Interact with entities"),
                        option("ATTACK", "Attack non-player entities"), option("BREAK,PLACE", "Build"),
                        option("BLOCK_INTERACT,ENTITY_INTERACT", "Interact")), "", true,
                        "Comma-separated action names; permits never override player-PvP rules.", "chunk", "player");
            } else if ("protection".equals(action)) playerChoices("player", p -> true, playerId);
        }

        private boolean titleManager(Claim claim) {
            return actor.admin() || engine.mayAccessAccount(actor.id(), claim.ownerAccount);
        }

        private boolean coreClaimFree(String key) {
            return !engine.commerce.claimLocked(key) && !engine.politics.claimLocked(key, null);
        }

        private void electionForm() {
            govChoices("nation", g -> g.kind == Kind.NATION && electionEligible(g), own(Kind.NATION), false);
            Government nation = selectedGovernment("nation");
            Election election = nation == null ? null : engine.data.elections.get(nation.id);
            playerChoices("candidate", p -> nation != null && election != null
                    && election.candidates.contains(p.id) && engine.member(p.id, nation), "", "nation");
        }

        private boolean electionEligible(Government nation) {
            if (has(Set.of("status", "candidates", "history", "list"), action)) return true;
            if (!operational()) return false;
            Election election = engine.data.elections.get(nation.id);
            if (election == null) return false;
            return switch (action) {
                case "candidate", "register" -> member(nation) && !election.voting && !election.scheduleExhausted
                        && !election.candidates.contains(playerId) && election.candidates.size() < engine.config.maxMembersPerNation;
                case "withdraw" -> !election.voting && election.candidates.contains(playerId);
                case "vote" -> member(nation) && election.voting && engine.now() < election.endsAt
                        && election.electorate.contains(playerId) && !election.votes.containsKey(playerId);
                case "start" -> actor.admin() && !election.voting;
                case "close" -> actor.admin() && election.voting;
                case "cancel" -> actor.admin();
                default -> false;
            };
        }

        private void legislationForm() {
            govChoices("nation", g -> g.kind == Kind.NATION && legislationNation(g), own(Kind.NATION),
                    "list".equals(action));
            Government nation = selectedGovernment("nation");
            if ("law".equals(family)) {
                List<FormChoice> laws = new ArrayList<>();
                if (nation != null) engine.data.laws.getOrDefault(nation.id, List.of()).stream()
                        .filter(Objects::nonNull).filter(l -> GovernanceEngine.validUuid(l.id))
                        .forEach(l -> laws.add(option(l.id, l.title, l.roleplayOnly ? "Roleplay only" : l.policy)));
                choices("law", laws, "", false, "Choose a nation first; only its retained laws are listed.", "nation");
                return;
            }
            List<Bill> eligible = engine.data.bills.values().stream().filter(Objects::nonNull)
                    .filter(b -> GovernanceEngine.validUuid(b.id) && validNation(b.nationId) != null && billEligible(b)).toList();
            choices("bill", eligible.stream().map(b -> option(b.id, b.title,
                    b.status + " - " + engine.governmentName(b.nationId))).toList(), "", false,
                    "Only bills available for this action are shown.");
            Bill selected = engine.data.bills.get(form.value("bill"));
            if (form.has("bill") && selected != null && !eligible.contains(selected)) selected = null;
            if ("revise".equals(action) && selected != null) {
                text("title", "Bill title", "", selected.title, false, "bill");
                text("text", "Bill text", "Line breaks and tabs are supported.", selected.text, true, "bill");
                policyValue(selected.policy, selected.value, "bill");
            } else if (form.has("policy")) {
                policyFields(nation, "policy", !"executive".equals(family), false, "nation");
            }
        }

        private boolean legislationNation(Government nation) {
            if ("law".equals(family) || "list".equals(action) || "status".equals(action)) return true;
            if (!operational()) return false;
            if ("executive".equals(family)) {
                if (!executive(nation)) return false;
                if ("rescind".equals(action)) return engine.data.emergencies.containsKey(nation.id);
                return !engine.data.emergencies.containsKey(nation.id) && (nation.lastEmergencyAt < 0
                        || engine.now() - nation.lastEmergencyAt >= engine.config.emergencyCooldownMillis);
            }
            if (has(Set.of("propose", "amend"), action))
                return (actor.admin() || member(nation) && (engine.config.allowCitizenBills
                        || engine.politics.legislator(playerId, nation)))
                        && engine.data.bills.values().stream().filter(Objects::nonNull)
                        .filter(b -> nation.id.equals(b.nationId) && has(BILL_ACTIVE, b.status)).count()
                        < engine.config.maxActiveBillsPerNation;
            return true;
        }

        private boolean billEligible(Bill bill) {
            if (has(Set.of("info", "status", "history", "votes"), action)) return true;
            if (!operational()) return false;
            Government nation = validNation(bill.nationId);
            return switch (action) {
                case "sign", "veto" -> "PASSED".equals(bill.status) && engine.now() < bill.decisionEndsAt && executive(nation);
                case "vote" -> has(Set.of("VOTING", "OVERRIDE_VOTING"), bill.status) && engine.now() < bill.voteEndsAt
                        && bill.electorate.contains(playerId) && !bill.votes.containsKey(playerId)
                        && engine.politics.legislator(playerId, nation);
                case "override" -> "VETOED".equals(bill.status) && engine.now() < bill.decisionEndsAt
                        && engine.politics.legislator(playerId, nation);
                case "revise" -> "DEBATE".equals(bill.status) && bill.treatyId == null && playerId.equals(bill.author);
                case "cancel" -> has(BILL_ACTIVE, bill.status) && (actor.admin()
                        || "DEBATE".equals(bill.status) && bill.treatyId == null && playerId.equals(bill.author));
                default -> false;
            };
        }

        private void policyFields(Government government, String key, boolean roleplay, boolean editing, String... dependencies) {
            List<FormChoice> policies = new ArrayList<>();
            GovernanceSettings.defaults(engine.config).keySet().stream()
                    .filter(name -> !editing || actor.admin() || !engine.config.requireLegislationForPolicy || "open".equals(name))
                    .forEach(name -> policies.add(option(name, FormBuilder.label(name),
                            GovernanceSettings.RATES.contains(name) ? "0-10000 basis points"
                                    : "baseChunkValue".equals(name) ? "Integer cents" : "true / false")));
            if (roleplay) policies.add(option("roleplay", "Roleplay only", "Records text without changing game mechanics."));
            choices(key, policies, "", false, "Choose a supported typed policy.", dependencies);
            String policy = form.value(key);
            String current = editing && government != null ? engine.settings(government).getOrDefault(policy, "") : "";
            policyValue(policy, current, concat(dependencies, key));
        }

        private void policyValue(String policy, String current, String... dependencies) {
            if (has(GovernanceSettings.BOOLEANS, policy)) {
                List<FormChoice> values = new ArrayList<>(List.of(option("true", "True"), option("false", "False")));
                if ("setting".equals(action)) values.add(option("inherit", "Inherit / server default"));
                choices("value", values, current, false,
                        "Boolean policy value.", dependencies);
            } else {
                text("value", "Policy value", "roleplay".equals(policy) ? "Use the literal - for roleplay laws."
                                : has(GovernanceSettings.RATES, policy) ? "Integer basis points: 0-10000."
                                : "baseChunkValue".equals(policy) ? "Non-negative integer cents." : "Choose a policy first.",
                        "roleplay".equals(policy) ? "-" : current, false, dependencies);
            }
        }

        private void diplomacyForm() {
            boolean publicQuery = has(Set.of("status", "proposals"), action);
            govChoices("nation", g -> g.kind == Kind.NATION && (publicQuery || operational() && executive(g)),
                    own(Kind.NATION), publicQuery);
            govChoices("from_nation", g -> operational() && g.kind == Kind.NATION && executive(g),
                    own(Kind.NATION), false);
            Government from = selectedGovernment(form.has("from_nation") ? "from_nation" : "nation");
            govChoices("to_nation", g -> operational() && g.kind == Kind.NATION && diplomaticPartner(from, g),
                    "", false, "from_nation");
            govChoices("ally", g -> from != null && g.kind == Kind.NATION && !g.id.equals(from.id)
                    && "ALLIED".equals(engine.politics.relationStatus(from.id, g.id)), "", false, "nation");
            choices("proposal", engine.data.diplomacy.values().stream().filter(Objects::nonNull)
                    .filter(p -> GovernanceEngine.validUuid(p.id) && diplomaticProposalEligible(p))
                    .map(p -> option(p.id, p.type + ": " + engine.governmentName(p.fromNation)
                            + " -> " + engine.governmentName(p.toNation), p.status)).toList(), "", false,
                    "Only proposals available to you for this action are shown.");
            text("message", "Diplomatic message", "Optional narrative terms; line breaks and tabs are supported.", "", true);
        }

        private boolean diplomaticPartner(Government from, Government to) {
            if (from == null || from.id.equals(to.id)) return false;
            String status = engine.politics.relationStatus(from.id, to.id);
            return switch (action) {
                case "alliance" -> "NEUTRAL".equals(status);
                case "peace", "truce" -> "WAR".equals(status);
                case "war" -> !"WAR".equals(status) && !"ALLIED".equals(status)
                        && !engine.politics.truce(from.id, to.id, engine.now());
                default -> true;
            };
        }

        private boolean diplomaticProposalEligible(DiplomaticProposal proposal) {
            if (has(Set.of("terms", "info"), action)) return true;
            if (!operational() || engine.now() >= proposal.expiresAt) return false;
            Government from = validNation(proposal.fromNation);
            Government to = validNation(proposal.toNation);
            if (from == null || to == null) return false;
            return switch (action) {
                case "accept" -> "PROPOSED".equals(proposal.status) && executive(to);
                case "reject" -> has(DIPLOMACY_ACTIVE, proposal.status) && executive(to);
                case "cancel" -> has(DIPLOMACY_ACTIVE, proposal.status) && executive(from);
                case "execute" -> "READY".equals(proposal.status) && (executive(from) || executive(to))
                        && proposal.ratified.containsAll(List.of(from.id, to.id));
                case "ratify" -> "AWAITING_RATIFICATION".equals(proposal.status)
                        && engine.data.bills.values().stream().filter(Objects::nonNull).anyMatch(b ->
                        proposal.id.equals(b.treatyId) && "VOTING".equals(b.status) && engine.now() < b.voteEndsAt
                                && b.electorate.contains(playerId) && !b.votes.containsKey(playerId)
                                && engine.politics.legislator(playerId, engine.data.governments.get(b.nationId)));
                default -> false;
            };
        }

        private void companyForm() {
            if ("create".equals(action)) {
                text("name", "New company name", "Enter a new unique company name.", "", false);
                return;
            }
            choices("company", companies.stream().filter(this::companyEligible)
                    .map(c -> companyOption(c)).toList(), preferredCompany(), false,
                    "Only companies available for this action are shown.");
            Company company = selectedCompany("company");
            if ("proposals".equals(action)) {
                List<FormChoice> options = new ArrayList<>(form.choices("company"));
                options.add(option("all", "All companies"));
                choices("company", options, preferredCompany(), false, "Browse one company or all companies.");
                company = selectedCompany("company");
            }
            Company selected = company;
            playerChoices("player", p -> selected != null && companyPerson(selected, p), "", "company");
            if (company != null) {
                if ("rename".equals(action)) text("name", "New company name", "", company.name, false, "company");
                if ("description".equals(action)) text("description", "Description", "", company.description, true, "company");
            }
            if ("propose".equals(action)) {
                enumChoices("type", "Shareholder proposal type", "owner", "dividend", "roleplay", "dissolve");
                if ("owner".equals(form.value("type"))) {
                    playerChoices("value", p -> selected != null && selected.members.contains(p.id)
                            && selected.shares.getOrDefault(p.id, 0L) > 0, "", "company", "type");
                } else text("value", "Proposal value", "dividend".equals(form.value("type"))
                                ? "Total dividend in dollars; no payment amount is preselected." : "Use - for roleplay or dissolution.",
                        has(Set.of("roleplay", "dissolve"), form.value("type")) ? "-" : "", false, "company", "type");
            }
            choices("proposal", engine.data.companyProposals.values().stream().filter(Objects::nonNull)
                    .filter(p -> GovernanceEngine.validUuid(p.id) && companyProposalEligible(p))
                    .map(p -> option(p.id, p.title, p.status + " - " + engine.companyName(p.companyId))).toList(),
                    "", false, "Choose an available shareholder proposal.");
        }

        private boolean companyEligible(Company company) {
            if (has(Set.of("info", "members", "shareholders", "proposals", "list"), action)) return true;
            if (!operational()) return false;
            return switch (action) {
                case "invite", "revoke", "kick", "rename", "description" -> companyManager(company);
                case "officer", "owner", "disband" -> companyOwner(company);
                case "accept", "join" -> !company.members.contains(playerId)
                        && company.members.size() < engine.config.maxCompanyMembers
                        && engine.invitation(null, company.id, playerId) != null;
                case "decline" -> engine.invitation(null, company.id, playerId) != null;
                case "leave" -> company.members.contains(playerId) && !playerId.equals(company.owner);
                case "transfer" -> availableShares(company) > 0;
                case "propose" -> company.shares.getOrDefault(playerId, 0L) > 0;
                default -> false;
            };
        }

        private boolean companyPerson(Company company, Player player) {
            return switch (action) {
                case "invite" -> !company.members.contains(player.id) && (engine.invitation(null, company.id, player.id) != null
                        || passes(() -> engine.checkInvitationCapacity(null, company.id, player.id)));
                case "revoke" -> engine.invitation(null, company.id, player.id) != null;
                case "kick" -> company.members.contains(player.id) && !company.owner.equals(player.id)
                        && (companyOwner(company) || !company.officers.contains(player.id));
                case "owner" -> !company.owner.equals(player.id)
                        && passes(() -> engine.commerce.validateNewOwner(company, player.id));
                case "officer" -> company.members.contains(player.id) && !company.owner.equals(player.id)
                        && ("remove".equals(word(4)) ? company.officers.contains(player.id)
                        : !company.officers.contains(player.id) && company.officers.size() < engine.config.maxOfficers);
                case "transfer" -> !playerId.equals(player.id);
                default -> true;
            };
        }

        private long availableShares(Company company) {
            try { return engine.availableShares(company.id, actor.id()); }
            catch (UserError unavailable) { return 0; }
        }

        private boolean companyProposalEligible(CompanyProposal proposal) {
            if ("proposal".equals(action)) return true;
            if (!operational()) return false;
            Company company = engine.data.companies.get(proposal.companyId);
            if (!companies.contains(company)) return false;
            return switch (action) {
                case "vote" -> "VOTING".equals(proposal.status) && engine.now() < proposal.endsAt
                        && proposal.electorate.get(playerId) != null && proposal.electorate.get(playerId) > 0
                        && !proposal.votes.containsKey(playerId);
                case "execute" -> "READY".equals(proposal.status)
                        && engine.now() < (proposal.executionEndsAt == 0
                        ? GovernanceEngine.deadline(proposal.endsAt, engine.config.companyVotingMillis) : proposal.executionEndsAt)
                        && (companyManager(company) || company.shares.getOrDefault(playerId, 0L) > 0);
                case "cancel" -> has(Set.of("VOTING", "READY"), proposal.status)
                        && (actor.admin() || "VOTING".equals(proposal.status) && playerId.equals(proposal.author) && proposal.votes.isEmpty());
                default -> false;
            };
        }

        private void contractForm() {
            govChoices("government", g -> !"create".equals(action) || operational() && manage(g)
                            && engine.data.contracts.values().stream().filter(Objects::nonNull)
                            .filter(c -> g.id.equals(c.governmentId) && has(CONTRACT_ACTIVE, c.status)).count()
                            < engine.config.maxContractsPerGovernment,
                    own(Kind.NATION), "list".equals(action));
            Government issuer = selectedGovernment("government");
            if ("create".equals(action)) {
                claimChoices("chunks", c -> issuer != null && managedPublicClaim(issuer, c) && coreClaimFree(c.key),
                        true, "government");
            }
            List<Contract> eligible = engine.data.contracts.values().stream().filter(Objects::nonNull)
                    .filter(c -> GovernanceEngine.validUuid(c.id) && contractEligible(c)).toList();
            choices("contract", eligible.stream().map(c -> option(c.id, c.title,
                    c.status + " - " + engine.governmentName(c.governmentId))).toList(), "", false,
                    "Only contracts available for this action are listed.");
            Contract contract = engine.data.contracts.get(form.value("contract"));
            if (contract != null && !eligible.contains(contract)) contract = null;
            Contract selected = contract;
            playerChoices("bidder", p -> selected != null && selected.bids.containsKey(p.id)
                    && passes(() -> engine.commerce.validateBidDestination(selected.bids.get(p.id), true))
                    && knownCompanyDestination(selected.bids.get(p.id).account)
                    && (selected.bids.get(p.id).cents == 0 || !engine.commerce.conflicted(playerId, p.id, selected.bids.get(p.id).account)),
                    "", "contract");
            choices("company", companies.stream().filter(c -> selected != null
                            && engine.mayManageCompany(actor.id(), c.id)).map(this::companyOption).toList(),
                    "", false, "Bid only as a company you actually manage, even if you are an operator.", "contract");
        }

        private boolean contractEligible(Contract contract) {
            if ("info".equals(action)) return true;
            if (!operational()) return false;
            if (!knownCompanyDestination(contract.payeeAccount)) return false;
            Government government = engine.data.governments.get(contract.governmentId);
            boolean official = manage(government);
            boolean contractor = engine.commerce.contractor(actor, contract);
            boolean independent = contract.escrowCents == 0
                    || !engine.commerce.conflicted(playerId, contract.winner, contract.payeeAccount);
            return switch (action) {
                case "bid" -> "OPEN".equals(contract.status) && engine.now() < contract.bidEndsAt
                        && (contract.bids.containsKey(playerId) || contract.bids.size() < engine.config.maxContractBids);
                case "withdraw" -> has(Set.of("OPEN", "REVIEW"), contract.status) && contract.bids.containsKey(playerId);
                case "bids" -> official;
                case "review" -> official && "OPEN".equals(contract.status) && !contract.bids.isEmpty();
                case "award" -> official && "REVIEW".equals(contract.status) && engine.now() < contract.reviewEndsAt;
                case "submit" -> contractor && "AWARDED".equals(contract.status);
                case "return" -> official && independent && "SUBMITTED".equals(contract.status);
                case "complete" -> official && independent && "SUBMITTED".equals(contract.status)
                        && (contract.escrowCents == 0 || !playerId.equals(contract.submittedBy));
                case "cancel" -> (official || contractor) && has(CONTRACT_ACTIVE, contract.status);
                default -> false;
            };
        }

        private boolean managedPublicClaim(Government government, Claim claim) {
            List<Government> scope = engine.subtree(government).stream().filter(engine::validHierarchy).toList();
            return scope.stream().anyMatch(g -> g.id.equals(claim.cityId))
                    && scope.stream().anyMatch(g -> engine.account(g).equals(claim.ownerAccount));
        }

        private boolean knownCompanyDestination(String account) {
            return account == null || !account.startsWith("company:")
                    || companies.stream().anyMatch(c -> ("company:" + c.id).equals(account));
        }

        private void mailForm() {
            boolean official = "official".equals(action);
            String operation = official ? word(2) : action;
            Government government = null;
            if (official) {
                govChoices("government", this::manage, own(Kind.NATION), false);
                government = selectedGovernment("government");
            }
            Player player = knownActor();
            List<Mail> inbox = official ? government == null ? List.of() : government.inbox
                    : player == null ? List.of() : player.inbox;
            List<Mail> sent = official ? government == null ? List.of() : government.sent
                    : player == null ? List.of() : player.sent;
            String owner = official ? government == null ? "" : engine.account(government) : actor.account();
            List<FormChoice> messages = new ArrayList<>();
            inbox.stream().filter(Objects::nonNull).filter(m -> owner.equals(m.recipient))
                    .filter(m -> !"reply".equals(operation) || !"system".equals(m.sender) && validMailSender(m.sender))
                    .filter(m -> GovernanceEngine.validUuid(m.id)).forEach(m ->
                            messages.add(option(m.id, m.subject, "Inbox - " + m.sentAt)));
            if (!"reply".equals(operation)) sent.stream().filter(Objects::nonNull)
                    .filter(m -> owner.equals(m.sender) && GovernanceEngine.validUuid(m.id))
                    .forEach(m -> messages.add(option(m.id, m.subject, "Sent - " + m.sentAt)));
            choices("message", messages, "", false, "Only this authorized mailbox's message IDs are shown; subjects only.",
                    official ? new String[]{"government"} : new String[0]);
            List<FormChoice> recipients = new ArrayList<>();
            if (!official || government != null) {
                recipients.addAll(playerOptions(p -> true));
                governments.forEach(g -> recipients.add(option("government:" + g.id, g.name + " (official mailbox)", g.kind.name())));
            }
            choices("recipient", recipients, "", false, "Known players or government mailboxes.",
                    official ? new String[]{"government"} : new String[0]);
        }

        private boolean validMailSender(String account) {
            if (account == null) return false;
            if (account.startsWith("player:")) return players.stream().anyMatch(p -> ("player:" + p.id).equals(account));
            return governments.stream().anyMatch(g -> engine.account(g).equals(account));
        }

        private void adminForm() {
            enumChoices("kind", "Government level", "nation", "state", "city");
            Kind kind = switch (form.value("kind")) {
                case "nation" -> Kind.NATION;
                case "state" -> Kind.STATE;
                case "city" -> Kind.CITY;
                default -> null;
            };
            List<Government> candidates = has(Set.of("leader", "rename"), action)
                    ? engine.data.governments.values().stream().filter(engine::validHierarchy).toList() : governments;
            choices("government", candidates.stream().filter(g -> actor.admin()
                            && (!form.has("kind") || kind != null && g.kind == kind))
                    .map(this::governmentOption).toList(), own(kind), false, "Operator-only government selection.", "kind");
            Government government = selectedGovernment("government");
            playerChoices("player", p -> actor.admin() && government != null, "", "government");
            govChoices("city", g -> actor.admin() && g.kind == Kind.CITY, own(Kind.CITY), false);
            choices("company", companies.stream().filter(c -> actor.admin()).map(this::companyOption).toList(),
                    "", false, "Operator-only company selection.");
            List<FormChoice> claims = new ArrayList<>();
            if (actor.admin()) engine.data.claims.forEach((key, claim) -> {
                if (claim != null && validChunk(key)) claims.add(option(key, key, engine.governmentName(claim.cityId)));
            });
            if (actor.admin() && (engine.data.claims.containsKey(actor.chunkKey()) || "diagnostics".equals(action)))
                claims.add(option("here", "Current chunk", actor.chunkKey()));
            choices("chunk", claims, "here", "diagnostics".equals(action) && actor.admin(),
                    "Operator-only; active financial/core commitments still apply.");
            choices("account", actor.admin() ? accountOptions(false, false) : List.of(), "", false,
                    "Existing title accounts only; internal system/escrow accounts cannot own land.", "chunk");
            if ("rename".equals(action) && government != null)
                text("name", "New government name", "", government.name, false, "government");
        }

        private void economyCommon() {
            for (String key : form.keys()) {
                String normalized = key.replace("_", "").toLowerCase(Locale.ROOT);
                switch (normalized) {
                    case "player", "recipient" -> playerChoices(key, p -> true, "");
                    case "government" -> govChoices(key, this::manage, own(Kind.NATION), false);
                    case "company" -> choices(key, companies.stream()
                                    .filter(c -> has(Set.of("list", "info", "buy", "shares"), action) || companyManager(c))
                                    .map(this::companyOption).toList(), preferredCompany(), false,
                            "Company IDs; financial commands may narrow these choices further.");
                    case "companyaccount" -> choices(key, accountOptions(true, true), "", false,
                            "Company treasuries you manage.");
                    case "account", "fromaccount" -> choices(key, accountOptions(true, false), actor.account(), false,
                            "Your player account or legitimately managed treasuries; no system/escrow accounts.");
                    case "toaccount" -> choices(key, accountOptions(false, false), "", false,
                            "Existing player, government or company destination accounts.");
                    default -> { }
                }
            }
        }

        private List<FormChoice> accountOptions(boolean managed, boolean onlyCompanies) {
            List<FormChoice> options = new ArrayList<>();
            if (!onlyCompanies) {
                players.stream().filter(p -> !managed || actor.admin() || playerId.equals(p.id))
                        .forEach(p -> options.add(option("player:" + p.id, label(p) + " (player)", p.id)));
                governments.stream().filter(g -> !managed || manage(g)).forEach(g ->
                        options.add(option(engine.account(g), g.name + " (" + g.kind.name().toLowerCase(Locale.ROOT) + ")", g.id)));
            }
            companies.stream().filter(c -> !managed || companyManager(c))
                    .forEach(c -> options.add(option("company:" + c.id, c.name + " (company)", c.id)));
            return options;
        }

        private void govChoices(String key, Predicate<Government> eligible, String preferred, boolean includeAll, String... dependencies) {
            if (key.isEmpty() || !form.has(key)) return;
            List<FormChoice> options = new ArrayList<>(governments.stream().filter(eligible).map(this::governmentOption).toList());
            if (includeAll) options.add(option("all", "All governments"));
            choices(key, options, preferred, false,
                    "Select an eligible government. No choices means the required role, membership or capacity is unavailable.", dependencies);
        }

        private void playerChoices(String key, Predicate<Player> eligible, String preferred, String... dependencies) {
            if (!form.has(key)) return;
            choices(key, playerOptions(eligible), preferred, false,
                    "Choose a known eligible player. Select the parent first; unavailable or assigned citizens are excluded.", dependencies);
        }

        private List<FormChoice> playerOptions(Predicate<Player> eligible) {
            return players.stream().filter(eligible).map(p -> option(p.id, label(p), p.id)).toList();
        }

        private void claimChoices(String key, Predicate<Claim> eligible, boolean custom, String... dependencies) {
            if (!form.has(key)) return;
            List<Claim> claims = engine.data.claims.values().stream().filter(engine::viewableClaim).filter(eligible).toList();
            List<FormChoice> options = new ArrayList<>();
            if (claims.stream().anyMatch(c -> actor.chunkKey().equals(c.key))) options.add(option("here", "Current chunk", actor.chunkKey()));
            claims.forEach(c -> options.add(option(c.key, c.key, engine.governmentName(c.cityId))));
            if (has(Set.of("info", "protection"), action) && options.stream().noneMatch(c -> "here".equals(c.value())))
                options.add(option("here", "Current chunk", actor.chunkKey()));
            choices(key, options, "here", custom || has(Set.of("info", "protection"), action),
                    custom ? "Choose an owned chunk or enter comma-separated owned chunk keys. Execution revalidates all commitments."
                            : "Only eligible claims are shown; financial encumbrances and connectivity are checked on execution.", dependencies);
        }

        private void enumChoices(String key, String hint, String... values) {
            choices(key, List.of(values).stream().map(value -> option(value, FormBuilder.label(value))).toList(),
                    "", false, hint);
        }

        private void choices(String key, Collection<FormChoice> options, String preferred, boolean custom, String hint, String... dependencies) {
            if (form.has(key)) form.choice(key, FormBuilder.label(key), hint, options,
                    preferred == null ? "" : preferred, List.of(dependencies), custom);
        }

        private void text(String key, String label, String hint, String value, boolean multiline, String... dependencies) {
            if (form.has(key)) form.text(key, label, hint, clip(value, 2048), multiline, dependencies);
        }

        private Government selectedGovernment(String key) {
            if (key == null || key.isEmpty() || !form.has(key)) return null;
            String value = form.value(key);
            if (form.choices(key).stream().noneMatch(option -> option.value().equals(value))) return null;
            Government government = engine.data.governments.get(value);
            return engine.validHierarchy(government) ? government : null;
        }

        private Company selectedCompany(String key) {
            if (!form.has(key)) return null;
            String value = form.value(key);
            if (form.choices(key).stream().noneMatch(option -> option.value().equals(value))) return null;
            Company company = engine.data.companies.get(value);
            return engine.viewableCompany(company) ? company : null;
        }

        private Claim selectedClaim(String key) {
            if (!form.has(key)) return null;
            String value = form.value(key);
            if ("here".equals(value)) value = actor.chunkKey();
            Claim claim = engine.data.claims.get(value);
            return engine.viewableClaim(claim) ? claim : null;
        }

        private Government validNation(String id) {
            Government government = engine.data.governments.get(id);
            return engine.viewableGovernment(government) && government.kind == Kind.NATION ? government : null;
        }

        private String own(Kind kind) {
            Player player = knownActor();
            if (player == null || kind == null) return "";
            return switch (kind) {
                case NATION -> player.nationId;
                case STATE -> player.stateId;
                case CITY -> player.cityId;
            };
        }

        private Player knownActor() {
            return players.stream().filter(p -> playerId.equals(p.id)).findFirst().orElse(null);
        }

        private String preferredCompany() {
            return companies.stream().filter(c -> playerId.equals(c.owner)).map(c -> c.id).findFirst().orElse("");
        }

        private FormChoice governmentOption(Government government) {
            return option(government.id, government.name, government.kind.name() + " - " + government.id);
        }

        private FormChoice companyOption(Company company) {
            return option(company.id, company.name, "Owner: " + engine.playerName(company.owner));
        }

        private String label(Player player) {
            return player.name == null || player.name.isBlank() ? player.id : player.name;
        }

        private String fieldAt(int index) {
            String value = word(index);
            return value.startsWith("<") && value.endsWith(">") ? value.substring(1, value.length() - 1) : "";
        }

        private String word(int index) {
            return index < words.size() ? words.get(index) : "";
        }
    }

    private static boolean passes(Runnable validation) {
        try { validation.run(); return true; }
        catch (UserError unavailable) { return false; }
    }

    private static boolean has(Set<String> values, String value) {
        return value != null && values.contains(value);
    }

    private static boolean validChunk(String value) {
        if (value == null) return false;
        try { return value.equals(ChunkKey.parse(value).toString()); }
        catch (UserError invalid) { return false; }
    }

    private static FormChoice option(String value, String label) {
        return option(value, label, "");
    }

    private static FormChoice option(String value, String label, String detail) {
        String safeLabel = clip(label, 128).replaceAll("\\p{Cntrl}", " ");
        return new FormChoice(value, safeLabel.isBlank() ? clip(value, 128) : safeLabel,
                clip(detail, 256).replaceAll("\\p{Cntrl}", " "));
    }

    private static String clip(String value, int limit) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), limit));
    }

    private static String[] concat(String[] first, String last) {
        List<String> result = new ArrayList<>(List.of(first));
        result.add(last);
        return result.toArray(String[]::new);
    }
}
