package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.domain.GovernanceData.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.statecraft.domain.GovernanceEngine.check;
import static dev.statecraft.domain.GovernancePresentation.*;

/** Pure validation and calculation: no execute/rollback, payment, ticking or model initialization. */
final class GovernancePreviews {
    private static final Set<String> LIVE_BILLS = Set.of("DEBATE", "VOTING", "PASSED", "VETOED", "OVERRIDE_VOTING");
    private static final Set<String> LIVE_DIPLOMACY = Set.of("PROPOSED", "AWAITING_RATIFICATION", "READY");
    private final GovernanceEngine e;
    private Actor actor;
    private String player;
    private Review review;

    GovernancePreviews(GovernanceEngine engine) { e = engine; }

    ActionPreview preview(Actor actor, ActionSelection selection) {
        this.actor = Objects.requireNonNull(actor, "actor");
        player = actor.id().toString();
        Objects.requireNonNull(selection, "selection");
        check("statecraft".equals(selection.namespace()), "This is not a governance action.");
        MenuRegistry.get(selection.page());
        if (!selection.template().isEmpty()) selection.registeredAction();
        Player known = e.requirePlayer(actor.id());
        check(Objects.equals(known.name, actor.name()), "Your player profile changed. Refresh it on the server before reviewing an action.");
        String rendered = selection.rendered();
        List<String> words = new ArrayList<>(CommandLine.split(rendered));
        if (!words.isEmpty() && Set.of("sc", "statecraft", "/sc", "/statecraft").contains(words.get(0))) words.remove(0);
        check(!words.isEmpty(), "Choose an action to review.");
        String family = words.get(0).toLowerCase(Locale.ROOT);
        if (Set.of("claim", "unclaim", "map").contains(family)) {
            words.set(0, family);
            words.add(0, "chunk");
            family = "chunk";
        }
        Arguments args = new Arguments(words);
        review = new Review(rendered);
        if ("gui".equals(family)) {
            args.exactly(2, "gui <page>");
            check(selection.intent() == ActionIntent.NAVIGATION, "Use a registered navigation action.");
            MenuRegistry.get(args.get(1));
            review.effect(t("effect.navigate", "Open %s without changing governance data.", MenuRegistry.get(args.get(1)).title()));
            return review.finish();
        }
        check(actor.admin() || !e.requiresRepair() || Set.of("help", "info").contains(family),
                "Governance requires operator repair before this command can run.");
        switch (family) {
            case "nation" -> government(Kind.NATION, args);
            case "state" -> government(Kind.STATE, args);
            case "city" -> government(Kind.CITY, args);
            case "government" -> government(null, args);
            case "chunk" -> chunk(args);
            case "company" -> company(args);
            case "contract" -> contract(args);
            case "election" -> election(args);
            case "bill" -> bill(args);
            case "law" -> law(args);
            case "executive" -> executive(args);
            case "diplomacy" -> diplomacy(args);
            case "mail" -> mail(args);
            case "profile" -> {
                args.between(1, 2, "profile [player]");
                Player target = args.size() == 2 ? e.resolvePlayer(args.get(1)) : e.requirePlayer(actor.id());
                review.party("subject_player", target.id);
                review.readOnly();
            }
            case "admin" -> admin(args);
            case "info" -> { args.exactly(1, "info"); review.readOnly(); }
            case "help" -> {
                args.between(1, 3, "help [section] [page]");
                GovernanceHelp.lines(args.optional(1, "overview"));
                args.page(2);
                review.readOnly();
            }
            default -> throw new UserError("This command has no governance review. Use a registered governance or runtime action.");
        }
        return review.finish();
    }

    private void government(Kind kind, Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action)) {
            args.between(1, 3, "government list [page]");
            args.page(2);
            review.readOnly();
            return;
        }
        if ("create".equals(action)) {
            check(kind != null, "Choose nation, state or city creation.");
            args.between(kind == Kind.NATION ? 3 : 4, kind == Kind.NATION ? 4 : 5, "government creation");
            Government parent = kind == Kind.NATION ? null : e.gov(args.get(2));
            if (parent != null) {
                check(parent.kind == (kind == Kind.STATE ? Kind.NATION : Kind.STATE), "Invalid parent government level.");
                e.manage(actor, parent);
                review.government(parent);
            }
            String name = e.validGovernmentName(args.get(kind == Kind.NATION ? 2 : 3), null);
            int leaderIndex = kind == Kind.NATION ? 3 : 4;
            Player leader = args.size() > leaderIndex ? e.resolvePlayer(args.get(leaderIndex)) : e.requirePlayer(actor.id());
            if (kind == Kind.NATION && !leader.id.equals(player)) e.admin(actor);
            e.checkCreationCapacity(kind, parent);
            e.checkCreationLeader(kind, parent, leader);
            review.field("name", "Name", name);
            review.field("level", "Government level", kind.name(), DisplayText.words(kind.name()));
            review.party("leader", leader.id);
            review.membership(leader);
            review.payments(List.of(e.creationFee(actor, kind, parent)), false, true);
            review.effect(t("effect.government_create", "Create the government, assign its leader and grant the required membership. Creation fees are paid now."));
            if (kind == Kind.NATION) review.duration("Election interval", e.config.electionIntervalMillis);
            return;
        }
        Government government = args.size() > 2 ? e.gov(args.get(2)) : own(kind);
        check(kind == null || kind == government.kind, "Government level does not match.");
        review.government(government);
        switch (action) {
            case "info", "settings", "members", "roles", "officers", "invitations" -> {
                args.between("info".equals(action) ? 2 : 3, Set.of("info", "settings").contains(action) ? 3 : 4, "government query");
                if (args.size() > 3) args.page(3);
                review.readOnly();
            }
            case "join", "accept" -> {
                args.exactly(3, "government join|accept <government>");
                Player target = e.requirePlayer(actor.id());
                check(!e.member(player, government), "You are already a citizen.");
                e.checkJoinPath(target, government);
                check(e.members(government).size() < e.memberLimit(government.kind), "Citizenship limit reached.");
                Invitation invitation = e.invitation(government.id, null, player);
                check(invitation != null || "join".equals(action) && e.flag(government, "open"), "A live invitation is required.");
                if (invitation != null) review.bind("invitation", invitation.id + ":" + invitation.expiresAt);
                review.membership(target);
                review.effect(t("effect.join", "Join this government while preserving valid ancestor citizenship. No tax inheritance is introduced."));
            }
            case "leave", "kick" -> {
                args.between("leave".equals(action) ? 2 : 4, "leave".equals(action) ? 3 : 4, "government leave|kick");
                Player target = e.requirePlayer(actor.id());
                if ("kick".equals(action)) {
                    e.manage(actor, government);
                    target = e.resolvePlayer(args.get(3));
                    check(!target.id.equals(player), "Use leave for your own membership.");
                    check(e.mayRemoveMembership(actor, target.id, government), "A subordinate or peer cannot remove an ancestor official's local membership.");
                }
                check(e.member(target.id, government), "The player is not a citizen of this government.");
                Set<String> scope = e.membershipRemovalScope(target.id, government);
                review.party("subject_player", target.id);
                review.membership(target);
                review.bind("membership-removal", scope.stream().sorted().toList());
                review.effect(t("effect.leave", "Remove the selected citizenship and descendant memberships and offices. Ancestor citizenship and offices remain intact. Automatic claiming ends only when national citizenship is removed."));
            }
            case "invite", "revoke", "decline" -> {
                args.exactly("decline".equals(action) ? 3 : 4, "government invitation");
                String target = player;
                if (!"decline".equals(action)) {
                    e.manage(actor, government);
                    target = e.resolvePlayer(args.get(3)).id;
                }
                Invitation existing = e.invitation(government.id, null, target);
                if ("invite".equals(action)) {
                    check(!e.member(target, government) && e.eligibleInvitation(e.data.players.get(target), government),
                            "The invitee needs matching parent citizenship and no conflicting membership.");
                    if (existing == null) e.checkInvitationCapacity(government.id, null, target);
                    review.duration("Invitation duration", e.config.invitationDurationMillis);
                    review.effect(t("effect.invite", "Create or renew an invitation and notify the invitee. Citizenship changes only after acceptance."));
                } else {
                    check(existing != null, "No active invitation exists.");
                    review.effect(t("effect.invitation_remove", "Remove this live invitation without changing existing memberships."));
                }
                review.party("invitee", target);
                if (existing != null) review.bind("invitation", existing.id + ":" + existing.expiresAt);
            }
            case "leader", "appoint" -> {
                args.exactly(4, "government leader <government> <player>");
                e.executive(actor, government);
                Player target = e.resolvePlayer(args.get(3));
                check(e.member(target.id, government), "The new leader must be a current citizen.");
                check(!target.id.equals(government.leader), "That player already leads this government.");
                review.party("previous_leader", government.leader);
                review.party("new_leader", target.id);
                review.membership(target);
                review.effect(t("effect.leader", "Transfer this leadership office and treasury authority. Private titles and equity do not change."));
            }
            case "officer" -> {
                args.exactly(5, "government officer <government> <player> <add|remove>");
                e.executive(actor, government);
                Player target = e.resolvePlayer(args.get(3));
                check(e.member(target.id, government) && !target.id.equals(government.leader), "Officers must be non-leader citizens.");
                rosterChange(government.officers, target.id, args.get(4), e.config.maxOfficers);
                review.party("officer", target.id);
                review.field("operation", "Operation", args.get(4), DisplayText.words(args.get(4)));
                review.effect(t("effect.officer", "Change this official roster and its scoped descendant-management authority."));
            }
            case "rename", "description", "tag", "flag" -> {
                args.between(4, 64, "government metadata <government> <text>");
                e.manage(actor, government);
                String value = args.tail(3);
                String previous = switch (action) {
                    case "rename" -> { value = e.validGovernmentName(value, government.id); yield government.name; }
                    case "description" -> { value = prose(value, "Description"); yield government.description; }
                    case "tag" -> { check(value.matches("[A-Za-z0-9_-]{1,12}"), "Tags use 1–12 letters, digits, underscores or hyphens."); yield government.tag; }
                    default -> { value = GovernanceEngine.text(value, 128, "Flag"); yield government.flag; }
                };
                review.field("previous", "Previous value", previous);
                review.field("replacement", "Replacement value", value);
                review.effect(t("effect.metadata", "Update descriptive metadata; names remain unique and no scripts or URLs are executed."));
            }
            case "setting" -> {
                args.exactly(5, "government setting <government> <key> <value|inherit>");
                e.executive(actor, government);
                String policy = args.get(3);
                check(!e.requiresNationalLegislation(actor, government, policy), "National mechanical policies require legislation. Authorized local policies remain directly manageable.");
                String value = args.get(4);
                if ("inherit".equals(value)) check(GovernanceSettings.defaults(e.config).containsKey(policy), "Unknown government policy.");
                else value = GovernanceSettings.validate(policy, value);
                review.field("policy", "Policy", policy, policyName(policy));
                review.policyValue("previous", "Previous value", policy, government.settings.getOrDefault(policy, "inherit"));
                review.policyValue("replacement", "Replacement value", policy, value);
                review.policyValue("effective", "Current effective value", policy, e.settings(government).get(policy));
                review.policyValue("resulting_effective", "Resulting effective value", policy, e.previewSettings(government, policy, value).get(policy));
                review.bind("policy-default", GovernanceSettings.defaults(e.config).get(policy));
                for (Government scope : e.ancestors(government)) {
                    if (scope == government || GovernanceSettings.inherited(policy))
                        review.bind("policy-scope", scope.id + ":" + scope.settings.get(policy) + ":" + emergencyState(e.data.emergencies.get(scope.id)));
                }
                review.effect(t("effect.policy", "Update this local override. Tax rates remain independent at each level; active emergency overlays may temporarily take precedence."));
            }
            case "disband" -> {
                args.between(3, 4, "government disband <government> [cascade]");
                e.executive(actor, government);
                if (args.size() == 4) check("cascade".equals(args.get(3)), "Use the literal cascade confirmation.");
                disband(government, args.size() == 4);
            }
            default -> throw new UserError("Unknown government action.");
        }
    }

    private Government own(Kind kind) {
        check(kind != null, "Specify a government.");
        return e.ownGovernment(actor, kind);
    }

    private String targetChunk(String reference) {
        String key = e.chunkKey(actor, reference);
        review.field("target_chunk", "Target chunk", key, chunkName(key));
        return key;
    }

    private void chunk(Arguments args) {
        String action = args.optional(1, "info");
        switch (action) {
            case "claim" -> {
                args.between(2, 4, "chunk claim [nation] [chunks|here]");
                boolean locationOnly = args.size() == 3 && ("here".equals(args.get(2)) || args.get(2).contains("|"));
                Government nation = args.size() > 2 && !locationOnly ? e.gov(args.get(2)) : own(Kind.NATION);
                List<String> keys = e.territory.keys(actor, locationOnly ? args.get(2) : args.optional(3, "here"));
                e.territory.claimPlan(actor, nation, keys);
                review.government(nation);
                if (keys.size() == 1) review.field("target_chunk", "Target chunk", keys.get(0), chunkName(keys.get(0)));
                review.field("chunks", "Selected chunks", String.join("\n", keys),
                        keys.stream().map(GovernancePresentation::chunkName).collect(Collectors.joining("\n")));
                review.bind("national-territory", e.nationClaims(nation.id).stream().sorted().toList());
                review.field("public_title", "Public title", e.account(nation), account(e, e.account(nation)));
                review.payments(List.of(e.territory.claimFee(actor, nation, keys.size())), false, true);
                review.effect(t("effect.claim", "Claim the selected unowned chunks for the nation, with no state or city assignment. The actor pays one atomic batch of claim fees to the nation treasury."));
            }
            case "unclaim" -> {
                args.between(2, 4, "chunk unclaim [nation] [chunks|here]");
                boolean locationOnly = args.size() == 3 && ("here".equals(args.get(2)) || args.get(2).contains("|"));
                Government nation = args.size() > 2 && !locationOnly ? e.gov(args.get(2)) : own(Kind.NATION);
                List<String> keys = e.territory.keys(actor, locationOnly ? args.get(2) : args.optional(3, "here"));
                e.territory.unclaimPlan(actor, nation, keys, false);
                review.government(nation);
                if (keys.size() == 1) review.field("target_chunk", "Target chunk", keys.get(0), chunkName(keys.get(0)));
                review.chunks(keys);
                review.bind("national-territory", e.nationClaims(nation.id).stream().sorted().toList());
                review.effect(t("effect.unclaim", "Remove the selected national claims, titles, improvements and permits. National and private-title authority, connectivity and every financial/workflow commitment must permit the entire batch."));
            }
            case "assignstate", "assigncity" -> {
                args.exactly(5, "assignstate <nation> <chunks> <state_or_none> or assigncity <state> <chunks> <city_or_none>");
                Government scope = e.gov(args.get(2));
                Government target = "none".equals(args.get(4)) ? null : e.gov(args.get(4));
                List<String> keys = e.territory.keys(actor, args.get(3));
                List<Territory.Allocation> plans = "assignstate".equals(action)
                        ? e.territory.statePlan(actor, scope, keys, target)
                        : e.territory.cityPlan(actor, scope, keys, target);
                review.government(scope);
                review.chunks(keys);
                review.field("allocation_target", "Allocation target", target == null ? "none" : target.id,
                        target == null ? "Unassigned" : governmentLabel(e, target.id, target.kind));
                review.allocations(plans);
                review.effect(t("effect.allocate", "Allocate existing national land without a claim fee or treasury balance transfer. Public title and future sale proceeds follow the most-specific government; changed public titles lose old permits. Private owners and their permits remain intact. Changing a state clears an incompatible city."));
            }
            case "autoclaim" -> {
                args.exactly(3, "chunk autoclaim <on|off>");
                check(Set.of("on", "off").contains(args.get(2)), "Use on or off.");
                boolean enabled = "on".equals(args.get(2));
                Player known = e.requirePlayer(actor.id());
                review.field("previous", "Previous value", Boolean.toString(known.autoClaim), known.autoClaim ? "On" : "Off");
                review.field("replacement", "Replacement value", Boolean.toString(enabled), enabled ? "On" : "Off");
                if (enabled) {
                    Government nation = own(Kind.NATION);
                    e.territory.nationalManager(actor, nation);
                    review.government(nation);
                    review.payments(List.of(e.claimFee(actor, nation)), true, false);
                }
                review.effect(t("effect.autoclaim", "Change automatic claiming for your nation. No claim or payment happens now; every movement rechecks current national authority, adjacency, funds and the fee. New claims have no state or city assignment."));
            }
            case "permit" -> {
                args.exactly(5, "chunk permit <chunk> <player> <actions>");
                Claim claim = e.requiredClaim(targetChunk(args.get(2)));
                titleManager(claim);
                Player target = e.resolvePlayer(args.get(3));
                Set<String> actions = permitActions(args.get(4));
                check(actions.isEmpty() || claim.permits.containsKey(target.id) || claim.permits.size() < e.config.maxPermitsPerChunk, "Chunk permit limit reached.");
                review.claim(claim);
                review.party("permit_holder", target.id);
                review.field("actions", "Permitted actions", actions.stream().sorted().collect(Collectors.joining(", ")),
                        actions.isEmpty() ? "None" : actions.stream().sorted().map(GovernancePresentation::permissionName).collect(Collectors.joining(", ")));
                review.effect(t("effect.permit", "Replace this player's explicit non-PvP permissions on this private title. Permits never override PvP rules."));
            }
            case "permits", "protection", "info" -> {
                args.between("info".equals(action) ? 1 : 2, "info".equals(action) ? 3 : 4, "chunk query");
                String key = targetChunk(args.optional(2, "here"));
                if ("permits".equals(action)) { titleManager(e.requiredClaim(key)); args.page(3); }
                if ("protection".equals(action) && args.size() > 3) e.resolvePlayer(args.get(3));
                review.field("chunk", "Chunk", key, chunkName(key));
                review.readOnly();
            }
            case "list" -> {
                args.between(2, 4, "chunk list [government|all] [page]");
                if (!"all".equals(args.optional(2, "all"))) review.government(e.gov(args.get(2)));
                args.page(3);
                review.readOnly();
            }
            case "map" -> {
                args.between(2, 3, "chunk map [radius]");
                if (args.size() == 3) Arguments.integer(args.get(2), 1, 5, "radius");
                targetChunk("here");
                review.readOnly();
            }
            default -> throw new UserError("Unknown chunk action.");
        }
    }

    private Set<String> permitActions(String value) {
        if ("none".equals(value)) return Set.of();
        Set<String> actions = new LinkedHashSet<>();
        if ("all".equals(value)) {
            for (AccessAction action : AccessAction.values()) if (action != AccessAction.PVP) actions.add(action.name());
        } else for (String name : value.split(",", -1)) {
            AccessAction action;
            try { action = AccessAction.valueOf(name.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { throw new UserError("Unknown permit action: " + name); }
            check(action != AccessAction.PVP, "Permits cannot override PvP.");
            actions.add(action.name());
        }
        return actions;
    }

    private void contract(Arguments args) {
        String action = args.optional(1, "list");
        if (Set.of("list", "my").contains(action)) {
            args.between(1, "my".equals(action) ? 3 : 4, "contract list|my");
            if ("list".equals(action) && !"all".equals(args.optional(2, "all"))) e.gov(args.get(2));
            args.page("my".equals(action) ? 2 : 3);
            review.readOnly();
            return;
        }
        if ("create".equals(action)) {
            args.exactly(6, "contract create <government> <title> <description> <chunks>");
            Government issuer = e.gov(args.get(2));
            e.manage(actor, issuer);
            check(e.data.contracts.values().stream().filter(Objects::nonNull).filter(c -> issuer.id.equals(c.governmentId)
                    && Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED").contains(c.status)).count() < e.config.maxContractsPerGovernment,
                    "Government active contract limit reached.");
            Contract draft = new Contract();
            draft.id = GovernanceEngine.newId();
            draft.governmentId = issuer.id;
            draft.title = GovernanceEngine.text(args.get(3), 100, "Contract title");
            draft.description = prose(args.get(4), "Contract description");
            String[] chunks = args.get(5).split(",", -1);
            check(chunks.length > 0 && chunks.length <= e.config.maxContractChunks, "Invalid number of contract chunks.");
            for (String value : chunks) {
                String key = e.chunkKey(actor, value);
                e.assertClaimFree(key, null);
                draft.chunks.add(key);
            }
            e.commerce.validateContractChunks(draft);
            review.government(issuer);
            review.field("title", "Title", draft.title);
            review.field("description", "Description", draft.description);
            review.chunks(draft.chunks);
            review.duration("Bidding duration", e.config.contractBiddingMillis);
            review.duration("Review duration", e.config.contractReviewMillis);
            review.effect(t("effect.contract_create", "Reserve the selected government-owned chunks and open bidding. No payment occurs until an independently approved award."));
            return;
        }
        Contract contract = required(e.data.contracts, args.get(2), "contract");
        review.contract(contract);
        switch (action) {
            case "info", "bids" -> {
                args.between(3, 4, "contract info|bids <contract> [page]");
                if ("bids".equals(action)) e.manage(actor, e.gov(contract.governmentId));
                args.page(3);
                review.readOnly();
            }
            case "bid" -> {
                Bid bid = e.commerce.bidPlan(actor, contract, args);
                Government issuer = e.gov(contract.governmentId);
                review.field("payee", "Payee", bid.account, account(e, bid.account));
                review.field("bid_text", "Bid description", bid.text);
                Bid previous = contract.bids.get(player);
                review.bind("previous-bid", bidState(previous));
                review.payments(List.of(new EconomyAccess.Transfer(e.account(issuer), bid.account, bid.cents, "Conditional contract bid")), true, true);
                review.effect(t("effect.contract_bid", "Submit or replace your bid. No payment happens now: the issuer funds escrow at award and the fixed payee receives it only after completion approval."));
            }
            case "withdraw" -> {
                args.exactly(3, "contract withdraw <contract>");
                check(Set.of("OPEN", "REVIEW").contains(contract.status), "Only unawarded bids may be withdrawn.");
                Bid bid = contract.bids.get(player);
                check(bid != null, "You have no bid on this contract.");
                review.bind("withdrawn-bid", bidState(bid));
                review.effect(t("effect.contract_withdraw", "Remove your unawarded bid. No escrow or payment is affected."));
            }
            case "review" -> {
                args.exactly(3, "contract review <contract>");
                e.manage(actor, e.gov(contract.governmentId));
                check("OPEN".equals(contract.status) && e.now() < contract.bidEndsAt && !contract.bids.isEmpty(), "At least one open bid is required.");
                review.bind("bids", contract.bids.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + bidState(entry.getValue())).toList());
                review.duration("Review duration", e.config.contractReviewMillis);
                review.effect(t("effect.contract_review", "Close bidding and start the bid-review window. No award or payment is made."));
            }
            case "award" -> {
                args.exactly(4, "contract award <contract> <bidder>");
                Commerce.AwardPlan plan = e.commerce.awardPlan(actor, contract, args.get(3));
                review.party("contractor", plan.winner().id);
                review.field("payee", "Payee", plan.bid().account, account(e, plan.bid().account));
                review.field("bid_text", "Bid description", plan.bid().text);
                review.bind("accepted-bid", bidState(plan.bid()));
                review.chunks(contract.chunks);
                review.payments(List.of(plan.transfer()), false, true);
                review.effect(t("effect.contract_award", "Fund this contract's escrow now and lock the accepted bidder and payee. The contractor is not paid until independent completion approval."));
            }
            case "submit" -> {
                args.between(4, 64, "contract submit <contract> <note>");
                check("AWARDED".equals(contract.status) && e.commerce.contractor(actor, contract), "Only the selected contractor or its managers may submit awarded work.");
                review.field("submission", "Submission", prose(args.tail(3), "Completion note"));
                review.effect(t("effect.contract_submit", "Submit work for independent government review. Escrow is unchanged and no payout happens now."));
            }
            case "return" -> {
                args.between(4, 64, "contract return <contract> <reason>");
                e.manage(actor, e.gov(contract.governmentId));
                check("SUBMITTED".equals(contract.status), "Only submitted work may be returned.");
                check(contract.escrowCents == 0 || !e.commerce.conflicted(player, contract.winner, contract.payeeAccount), "A paid contractor cannot review their own work.");
                review.field("reason", "Reason", prose(args.tail(3), "Return reason"));
                review.effect(t("effect.contract_return", "Return work for correction without changing the award, original payee or escrow."));
            }
            case "complete" -> {
                args.exactly(3, "contract complete <contract>");
                EconomyAccess.Transfer payment = e.commerce.completionPlan(actor, contract);
                review.bind("accepted-bid", bidState(contract.bids.get(contract.winner)));
                review.chunks(contract.chunks);
                review.payments(List.of(payment), false, true);
                review.effect(t("effect.contract_complete", "Approve the submitted work, pay the original accepted payee from escrow and release the contract's chunk reservations."));
            }
            case "cancel" -> {
                args.between(4, 64, "contract cancel <contract> <reason>");
                review.field("reason", "Reason", prose(args.tail(3), "Cancellation reason"));
                review.payments(List.of(e.commerce.cancellationPlan(actor, contract)), false, true);
                review.effect(t("effect.contract_cancel", "Cancel this active contract, refund escrow only to its issuing government and release its reservations."));
            }
            default -> throw new UserError("Unknown contract action.");
        }
    }

    private void election(Arguments args) {
        String action = args.optional(1, "status");
        if ("list".equals(action)) {
            args.between(2, 3, "election list [page]");
            args.page(2);
            review.readOnly();
            return;
        }
        Government nation = args.size() > 2 ? e.politics.nation(args.get(2)) : own(Kind.NATION);
        Election election = e.data.elections.get(nation.id);
        check(election != null, "The election schedule is missing. Open election status or let server maintenance initialize it, then review again.");
        review.government(nation);
        review.bind("election-stage", List.of(election.voting, election.startedAt, election.nextStartAt, election.endsAt, election.scheduleExhausted));
        switch (action) {
            case "status", "candidates", "history" -> {
                args.between("status".equals(action) ? 1 : 3, "status".equals(action) ? 3 : 4, "election query");
                args.page(3);
                review.readOnly();
            }
            case "candidate", "register" -> {
                args.exactly(3, "election candidate <nation>");
                check(e.member(player, nation), "Candidates must be current citizens.");
                check(!election.voting && !election.scheduleExhausted, "Registration is closed.");
                check(!election.candidates.contains(player) && election.candidates.size() < e.config.maxMembersPerNation, "You are already registered or the candidate limit was reached.");
                review.payments(List.of(e.politics.candidateFee(actor, nation)), false, true);
                review.field("registration_deadline", "Registration deadline", Long.toString(election.nextStartAt), DisplayText.date(election.nextStartAt));
                review.effect(t("effect.candidate", "Register as a candidate and pay the current nonrefundable registration fee now."));
            }
            case "withdraw" -> {
                args.exactly(3, "election withdraw <nation>");
                check(!election.voting && election.candidates.contains(player), "Only registered candidates may withdraw before voting.");
                review.effect(t("effect.candidate_withdraw", "Withdraw this candidacy. Any registration fee remains nonrefundable."));
            }
            case "vote" -> {
                args.exactly(4, "election vote <nation> <candidate>");
                check(election.voting && e.now() < election.endsAt && election.electorate.contains(player) && e.member(player, nation), "You have no eligible open election ballot.");
                check(!election.votes.containsKey(player), "You already voted.");
                Player candidate = e.resolvePlayer(args.get(3));
                check(election.candidates.contains(candidate.id) && e.member(candidate.id, nation), "The candidate is no longer eligible.");
                review.party("candidate", candidate.id);
                review.effect(t("effect.election_vote", "Record your one ballot for this candidate. The ballot is retained even if you later leave the nation."));
            }
            case "start", "close", "cancel" -> {
                args.exactly(3, "election administration <nation>");
                e.admin(actor);
                if ("start".equals(action)) check(!election.voting, "An election is already running.");
                if ("close".equals(action)) check(election.voting, "No election is running.");
                review.bind("candidates", election.candidates.stream().sorted().toList());
                review.bind("votes", sorted(election.votes));
                review.bind("electorate", e.members(nation).stream().sorted().toList());
                review.duration("Election interval", e.config.electionIntervalMillis);
                review.duration("Voting duration", e.config.electionVotingMillis);
                review.effect(t("effect.election_admin", "Operator action: %s. Closing applies the current valid result; cancellation clears the ballot without refunding candidate fees.", DisplayText.words(action)));
            }
            default -> throw new UserError("Unknown election action.");
        }
    }

    private void bill(Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action)) {
            args.between(1, 4, "bill list [nation|all] [page]");
            if (!"all".equals(args.optional(2, ""))) review.government(args.size() > 2 ? e.politics.nation(args.get(2)) : own(Kind.NATION));
            args.page(3);
            review.readOnly();
            return;
        }
        if (Set.of("propose", "amend").contains(action)) {
            args.between(7, 64, "bill propose|amend <nation> <policy> <value> <title> <text>");
            Government nation = e.politics.nation(args.get(2));
            check(actor.admin() || e.member(player, nation) && (e.config.allowCitizenBills || e.politics.legislator(player, nation)), "You are not eligible to introduce a national bill.");
            e.politics.billCapacity(nation.id);
            review.government(nation);
            review.field("policy", "Policy", args.get(3), policyName(args.get(3)));
            review.policyValue("value", "Value", args.get(3), e.politics.billValue(args.get(3), args.get(4)));
            review.field("title", "Title", GovernanceEngine.text(args.get(5), 100, "Bill title"));
            review.field("text", "Text", prose(args.tail(6), "Bill text"));
            review.duration("Debate duration", e.config.debateDurationMillis);
            review.effect(t("effect.bill_propose", "Introduce a national bill for debate. No policy changes or money transfers happen now; amendments use the stricter whole-electorate threshold."));
            return;
        }
        Bill bill = required(e.data.bills, args.get(2), "bill");
        Government nation = e.politics.nation(bill.nationId);
        review.government(nation);
        review.bill(bill);
        switch (action) {
            case "info", "status", "votes", "history" -> {
                args.between(3, Set.of("info", "status").contains(action) ? 3 : 4, "bill query");
                args.page(3);
                review.readOnly();
            }
            case "vote" -> {
                args.exactly(4, "bill vote <bill> <yes|no|abstain>");
                billVote(bill, args.get(3));
            }
            case "sign", "veto" -> {
                args.between("sign".equals(action) ? 3 : 4, "sign".equals(action) ? 3 : 64, "bill sign|veto");
                e.executive(actor, nation);
                check("PASSED".equals(bill.status) && e.now() < bill.decisionEndsAt, "The executive decision window is not open.");
                if ("sign".equals(action)) {
                    check(bill.treatyId == null, "Treaty ratifications need no additional signature.");
                    e.politics.billValue(bill.policy, bill.value);
                    review.effect(t("effect.bill_sign", "Enact this retained bill. Mechanical national settings update now; roleplay laws record text only and national taxes do not inherit locally."));
                } else {
                    review.field("reason", "Reason", prose(args.tail(3), "Veto reason"));
                    review.duration("Override decision window", e.config.signatureDurationMillis);
                    review.effect(t("effect.bill_veto", "Veto this passed bill and open the override decision window. No policy is enacted."));
                }
            }
            case "override" -> {
                args.exactly(3, "bill override <bill>");
                check("VETOED".equals(bill.status) && e.now() < bill.decisionEndsAt && e.politics.legislator(player, nation), "Only current legislators may open an unexpired veto override.");
                review.bind("new-electorate", e.members(nation).stream().filter(id -> e.politics.legislator(id, nation)).sorted().toList());
                review.field("threshold", "Whole-electorate approval required", Integer.toString(e.config.overrideThresholdBps),
                        DisplayText.percent(e.config.overrideThresholdBps));
                review.duration("Voting duration", e.config.legislativeVotingMillis);
                review.effect(t("effect.bill_override", "Open a new frozen-electorate override ballot. A successful supermajority automatically enacts the bill."));
            }
            case "revise" -> {
                args.between(6, 64, "bill revise <bill> <value> <title> <text>");
                check(player.equals(bill.author) && "DEBATE".equals(bill.status) && bill.treatyId == null, "Only the author may revise an ordinary bill during debate.");
                review.policyValue("new_value", "New value", bill.policy, e.politics.billValue(bill.policy, args.get(3)));
                review.field("new_title", "New title", GovernanceEngine.text(args.get(4), 100, "Bill title"));
                review.field("new_text", "New text", prose(args.tail(5), "Bill text"));
                review.duration("Restarted debate duration", e.config.debateDurationMillis);
                review.effect(t("effect.bill_revise", "Replace this bill's editable terms and restart the full debate period."));
            }
            case "cancel" -> {
                args.exactly(3, "bill cancel <bill>");
                check(has(LIVE_BILLS, bill.status), "The bill has concluded.");
                check(actor.admin() || "DEBATE".equals(bill.status) && player.equals(bill.author) && bill.treatyId == null, "Only the author during ordinary debate, or an operator, may cancel.");
                review.effect(t("effect.bill_cancel", "Cancel this bill. Cancelling a treaty ballot also ends its pending treaty without settlement."));
            }
            default -> throw new UserError("Unknown bill action.");
        }
    }

    private void billVote(Bill bill, String choice) {
        check(Set.of("yes", "no", "abstain").contains(choice), "Vote yes, no or abstain.");
        Government nation = e.politics.nation(bill.nationId);
        check(Set.of("VOTING", "OVERRIDE_VOTING").contains(bill.status) && e.now() < bill.voteEndsAt, "No legislative ballot is open.");
        check(bill.electorate.contains(player) && e.politics.legislator(player, nation) && !bill.votes.containsKey(player), "You have no eligible uncast legislative ballot.");
        review.field("vote", "Vote", choice, DisplayText.words(choice));
        if (bill.treatyId != null) {
            DiplomaticProposal treaty = required(e.data.diplomacy, bill.treatyId, "treaty");
            review.treaty(treaty);
            review.payments(e.politics.treatyTransfers(treaty), true, false);
        }
        review.effect(t("effect.bill_vote", "Record your one ballot on these retained terms. A treaty can settle only after both legislatures approve; this vote itself transfers no money or land."));
    }

    private void law(Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action)) {
            args.between(1, 4, "law list [nation|all] [page]");
            if (!"all".equals(args.optional(2, ""))) review.government(args.size() > 2 ? e.politics.nation(args.get(2)) : own(Kind.NATION));
            args.page(3);
        } else {
            check("read".equals(action), "Unknown law action.");
            args.exactly(4, "law read <nation> <law>");
            Government nation = e.politics.nation(args.get(2));
            check(e.data.laws.getOrDefault(nation.id, List.of()).stream().filter(Objects::nonNull).anyMatch(l -> args.get(3).equals(l.id)), "That law is not retained in this codex.");
            review.government(nation);
        }
        review.readOnly();
    }

    private void executive(Arguments args) {
        String action = args.get(1);
        Government nation = e.politics.nation(args.get(2));
        review.government(nation);
        if ("status".equals(action)) { args.exactly(3, "executive status <nation>"); review.readOnly(); return; }
        e.executive(actor, nation);
        Emergency current = e.data.emergencies.get(nation.id);
        review.bind("emergency", emergencyState(current));
        if ("emergency".equals(action)) {
            args.between(6, 64, "executive emergency <nation> <policy> <value> <reason>");
            check(!e.data.emergencies.containsKey(nation.id), "Rescind or await expiry of the current emergency order.");
            check(nation.lastEmergencyAt < 0 || e.now() >= nation.lastEmergencyAt && e.now() - nation.lastEmergencyAt >= e.config.emergencyCooldownMillis, "Emergency authority is on cooldown.");
            check(GovernanceEngine.deadline(e.now(), e.config.emergencyDurationMillis) > e.now(), "The world clock cannot schedule another emergency.");
            review.field("policy", "Policy", args.get(3), policyName(args.get(3)));
            review.policyValue("value", "Value", args.get(3), GovernanceSettings.validate(args.get(3), args.get(4)));
            review.field("reason", "Reason", prose(args.tail(5), "Emergency reason"));
            review.duration("Emergency duration", e.config.emergencyDurationMillis);
            review.duration("Emergency cooldown", e.config.emergencyCooldownMillis);
            review.effect(t("effect.emergency", "Temporarily overlay this national policy without replacing its permanent setting or law. Cooldown survives rescission."));
        } else {
            check("rescind".equals(action), "Unknown executive action.");
            args.exactly(3, "executive rescind <nation>");
            check(current != null, "No emergency order is active.");
            review.field("order", "Current order", current.policy + "=" + current.value,
                    policyName(current.policy) + ": " + policyValue(e, current.policy, current.value));
            review.effect(t("effect.rescind", "Remove the current emergency overlay and reveal the permanent settings underneath it. Cooldown remains in force."));
        }
    }

    private void diplomacy(Arguments args) {
        String action = args.optional(1, "status");
        if (Set.of("status", "proposals").contains(action)) {
            args.between(1, 4, "diplomacy query [nation|all] [page]");
            if (!"all".equals(args.optional(2, ""))) review.government(args.size() > 2 ? e.politics.nation(args.get(2)) : own(Kind.NATION));
            args.page(3);
            review.readOnly();
            return;
        }
        if (Set.of("war", "break", "alliance", "peace", "truce").contains(action)) {
            boolean peace = "peace".equals(action);
            args.between(peace ? 7 : 4, Set.of("break", "truce").contains(action) ? 4 : 64, "diplomatic proposal");
            Government from = e.politics.nation(args.get(2));
            Government to = e.politics.nation(args.get(3));
            e.executive(actor, from);
            check(!from.id.equals(to.id), "Choose two different nations.");
            review.party("from_nation", e.account(from));
            review.party("to_nation", e.account(to));
            String relation = e.politics.relationStatus(from.id, to.id);
            review.field("relation", "Current relation", relation, DisplayText.words(relation));
            Relation current = e.data.relations.get(Politics.pair(from.id, to.id));
            review.bind("relation", current == null ? "" : current.status + ":" + current.truceUntil + ":" + current.warStartedAt);
            if ("war".equals(action)) {
                check(!Set.of("WAR", "ALLIED").contains(relation), "End any alliance and do not declare an existing war twice.");
                check(!e.politics.truce(from.id, to.id, e.now()), "The binding truce has not expired.");
                if (args.size() > 4) review.field("reason", "Reason", prose(args.tail(4), "War reason"));
                review.effect(t("effect.war", "Declare war, supersede pending alliances and apply the configured wartime access/PvP rules. No money or territory transfers now."));
            } else if ("break".equals(action)) {
                check("ALLIED".equals(relation), "These nations are not allied.");
                review.effect(t("effect.alliance_break", "End the alliance. Any existing binding truce remains in force."));
            } else {
                String type = "alliance".equals(action) ? "ALLIANCE" : "PEACE";
                check(("ALLIANCE".equals(type) ? "NEUTRAL" : "WAR").equals(relation), "Current relations do not permit that proposal.");
                e.politics.proposalCapacity(from.id, to.id);
                e.politics.noDuplicateProposal(from.id, to.id, type);
                if ("ALLIANCE".equals(type)) {
                    if (args.size() > 4) review.field("message", "Message", prose(args.tail(4), "Diplomatic message"));
                    review.duration("Proposal duration", e.config.allianceProposalDurationMillis);
                    review.effect(t("effect.alliance_propose", "Offer an alliance for counterparty acceptance. No relationship changes until it is accepted."));
                } else {
                    DiplomaticProposal draft = new DiplomaticProposal();
                    draft.id = GovernanceEngine.newId();
                    draft.type = type;
                    draft.fromNation = from.id;
                    draft.toNation = to.id;
                    draft.expiresAt = GovernanceEngine.deadline(e.now(), e.config.peaceProposalDurationMillis);
                    if (peace) {
                        draft.offeredCents = Money.parse(args.get(4));
                        draft.demandedCents = Money.parse(args.get(5));
                        e.politics.parseTerms(draft, args.get(6));
                        if (args.size() > 7) review.field("message", "Message", prose(args.tail(7), "Treaty message"));
                    }
                    e.politics.validateTreaty(draft);
                    review.treatyTerms(draft);
                    review.payments(e.politics.treatyTransfers(draft), true, true);
                    review.duration("Proposal duration", e.config.peaceProposalDurationMillis);
                    review.effect(t("effect.peace_propose", "Propose these monetary and territorial terms and reserve selected claims. No payment or transfer happens until acceptance and any required ratification."));
                }
            }
            return;
        }
        DiplomaticProposal proposal = required(e.data.diplomacy, args.get(2), "diplomatic proposal");
        if (Set.of("terms", "info").contains(action)) {
            args.between(3, 4, "diplomacy terms <proposal> [page]");
            args.page(3);
            review.readOnly();
            return;
        }
        review.treaty(proposal);
        switch (action) {
            case "accept" -> {
                args.exactly(3, "diplomacy accept <proposal>");
                e.executive(actor, e.politics.nation(proposal.toNation));
                check("PROPOSED".equals(proposal.status) && e.now() < proposal.expiresAt, "This proposal no longer awaits acceptance.");
                e.politics.validateTreatyReferences(proposal);
                if ("ALLIANCE".equals(proposal.type)) {
                    check("NEUTRAL".equals(e.politics.relationStatus(proposal.fromNation, proposal.toNation)), "Relations changed; the alliance cannot be accepted.");
                    review.effect(t("effect.alliance_accept", "Accept this alliance and end competing pending alliance offers between the same nations. No money moves."));
                } else {
                    check("PEACE".equals(proposal.type), "Unknown proposal type.");
                    e.politics.validateTreaty(proposal);
                    boolean ratification = e.politics.requiresRatification(proposal);
                    if (ratification) { e.politics.billCapacity(proposal.fromNation); e.politics.billCapacity(proposal.toNation); }
                    review.field("ratification", "Legislative ratification required", Boolean.toString(ratification), DisplayText.yesNo(ratification));
                    review.payments(e.politics.treatyTransfers(proposal), ratification, true);
                    review.effect(ratification
                            ? t("effect.peace_accept_pending", "Accept the immutable proposal and introduce both ratification bills. No money or land moves now.")
                            : t("effect.peace_settle", "Settle all monetary terms atomically, then transfer national ownership and negotiated allocations of existing public claims. Public title follows the destination, old public permits clear, war ends and the binding truce begins."));
                    review.duration("Binding truce duration", e.config.truceDurationMillis);
                }
            }
            case "execute" -> {
                args.exactly(3, "diplomacy execute <proposal>");
                e.politics.diplomaticExecutive(actor, proposal);
                check("READY".equals(proposal.status), "This treaty is not awaiting a settlement retry.");
                e.politics.validateTreaty(proposal);
                check(proposal.ratified.containsAll(List.of(proposal.fromNation, proposal.toNation)), "Both ratifications are required.");
                review.payments(e.politics.treatyTransfers(proposal), false, true);
                review.duration("Binding truce duration", e.config.truceDurationMillis);
                review.effect(t("effect.peace_settle", "Settle all monetary terms atomically, then transfer national ownership and negotiated allocations of existing public claims. Public title follows the destination, old public permits clear, war ends and the binding truce begins."));
            }
            case "ratify" -> {
                args.exactly(4, "diplomacy ratify <proposal> <vote>");
                String nation = e.nationOf(actor.id()).orElseThrow(() -> new UserError("You are not a citizen."));
                Bill bill = e.data.bills.values().stream().filter(Objects::nonNull)
                        .filter(b -> proposal.id.equals(b.treatyId) && nation.equals(b.nationId) && "VOTING".equals(b.status))
                        .findFirst().orElseThrow(() -> new UserError("Your legislature has no open ratification ballot."));
                review.bill(bill);
                billVote(bill, args.get(3));
            }
            case "reject", "cancel" -> {
                args.exactly(3, "diplomacy reject|cancel <proposal>");
                e.executive(actor, e.politics.nation("cancel".equals(action) ? proposal.fromNation : proposal.toNation));
                check(has(LIVE_DIPLOMACY, proposal.status) && e.now() < proposal.expiresAt, "The proposal has concluded.");
                review.effect(t("effect.treaty_cancel", "Conclude this proposal without payment or territorial settlement and cancel its outstanding ratification bills."));
            }
            default -> throw new UserError("Unknown diplomatic action.");
        }
    }

    private void company(Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action)) {
            args.between(1, 3, "company list [page]");
            args.page(2); review.readOnly(); return;
        }
        if ("create".equals(action)) {
            args.exactly(3, "company create <name>");
            review.field("name", "Name", e.commerce.companyCreationName(actor, args.get(2)));
            review.field("issued_shares", "Issued shares", Long.toString(e.config.totalCompanyShares));
            review.payments(List.of(e.commerce.creationFee(actor)), false, true);
            review.effect(t("effect.company_create", "Create the company, appoint you as manager/member and issue its entire fixed share supply to you. The incorporation fee is paid now."));
            return;
        }
        if (Set.of("proposal", "vote", "execute", "cancel").contains(action)) {
            companyProposal(action, args);
            return;
        }
        if ("proposals".equals(action) && "all".equals(args.get(2))) {
            args.between(3, 4, "company proposals all [page]");
            args.page(3); review.readOnly(); return;
        }
        Company company = e.companyRequired(args.get(2));
        review.company(company);
        switch (action) {
            case "info", "members", "shareholders", "proposals" -> {
                args.between(3, "info".equals(action) ? 3 : 4, "company query");
                args.page(3); review.readOnly();
            }
            case "propose" -> {
                args.between(7, 64, "company propose <company> <type> <value> <title> <text>");
                e.commerce.assertShareIntegrity(company);
                check(company.shares.getOrDefault(player, 0L) > 0, "Only shareholders may propose decisions.");
                check(e.data.companyProposals.values().stream().filter(Objects::nonNull).filter(p -> company.id.equals(p.companyId)
                        && Set.of("VOTING", "READY").contains(p.status)).count() < e.config.maxCompanyProposals, "Company proposal limit reached.");
                String type = args.get(3);
                switch (type) {
                    case "owner" -> {
                        Player nominee = e.resolvePlayer(args.get(4));
                        check(company.members.contains(nominee.id) && company.shares.getOrDefault(nominee.id, 0L) > 0, "The proposed owner must be a member shareholder.");
                        review.party("new_owner", nominee.id);
                    }
                    case "dividend" -> {
                        long cents = Money.positive(Money.parse(args.get(4)));
                        review.payments(List.of(new EconomyAccess.Transfer("company:" + company.id, "shareholders:at-execution", cents, "Conditional dividend")), true, true);
                    }
                    case "roleplay", "dissolve" -> check("-".equals(args.get(4)), "Use - for roleplay/dissolution proposals.");
                    default -> throw new UserError("Unknown shareholder proposal type.");
                }
                review.field("decision", "Decision", type, decisionName(type));
                review.field("title", "Title", GovernanceEngine.text(args.get(5), 100, "Proposal title"));
                review.field("text", "Text", prose(args.tail(6), "Proposal text"));
                review.bind("frozen-share-electorate", sorted(company.shares));
                review.duration("Voting duration", e.config.companyVotingMillis);
                review.effect(t("effect.company_propose", "Open a share-weighted ballot, freezing current holdings for voting only. No payment happens now; approved dividends use holdings at execution, not that frozen electorate."));
            }
            case "invite", "revoke", "decline" -> {
                args.exactly("decline".equals(action) ? 3 : 4, "company invitation");
                String target = player;
                if (!"decline".equals(action)) { companyManager(company); target = e.resolvePlayer(args.get(3)).id; }
                Invitation invitation = e.invitation(null, company.id, target);
                if ("invite".equals(action)) {
                    check(!company.members.contains(target), "That player is already employed.");
                    if (invitation == null) e.checkInvitationCapacity(null, company.id, target);
                    review.duration("Invitation duration", e.config.invitationDurationMillis);
                    review.effect(t("effect.company_invite", "Create or renew an employment invitation. Accepting employment does not grant shares."));
                } else {
                    check(invitation != null, "No live company invitation exists.");
                    review.effect(t("effect.invitation_remove", "Remove this live invitation without changing existing memberships."));
                }
                review.party("invitee", target);
                if (invitation != null) review.bind("invitation", invitation.id + ":" + invitation.expiresAt);
            }
            case "accept", "join" -> {
                args.exactly(3, "company accept <company>");
                Invitation invitation = e.invitation(null, company.id, player);
                check(!company.members.contains(player) && invitation != null && company.members.size() < e.config.maxCompanyMembers, "A live invitation and an available employment slot are required.");
                review.bind("invitation", invitation.id + ":" + invitation.expiresAt);
                review.effect(t("effect.company_join", "Join the company's employment roster without receiving management authority or equity."));
            }
            case "leave", "kick" -> {
                args.exactly("kick".equals(action) ? 4 : 3, "company leave|kick");
                String target = player;
                if ("kick".equals(action)) {
                    companyManager(company);
                    target = e.resolvePlayer(args.get(3)).id;
                    check(actor.admin() || player.equals(company.owner) || !company.officers.contains(target), "Only the owner may remove another company officer.");
                }
                check(company.members.contains(target) && !target.equals(company.owner), "The owner must transfer management before leaving; the target must be employed.");
                review.party("employee", target);
                review.effect(t("effect.company_leave", "Remove employment and company-office permissions. Existing shares and share reservations remain owned by that player."));
            }
            case "officer" -> {
                args.exactly(5, "company officer <company> <player> <add|remove>");
                companyOwner(company);
                Player target = e.resolvePlayer(args.get(3));
                check(company.members.contains(target.id) && !target.id.equals(company.owner), "Officers must be non-owner members.");
                rosterChange(company.officers, target.id, args.get(4), e.config.maxOfficers);
                review.party("officer", target.id);
                review.field("operation", "Operation", args.get(4), DisplayText.words(args.get(4)));
                review.effect(t("effect.company_officer", "Change this member's company treasury-management authority without changing equity."));
            }
            case "owner" -> {
                args.exactly(4, "company owner <company> <player>");
                companyOwner(company);
                Player target = e.resolvePlayer(args.get(3));
                e.commerce.validateNewOwner(company, target.id);
                check(!target.id.equals(company.owner), "That player already manages the company.");
                review.party("new_owner", target.id);
                review.effect(t("effect.company_owner", "Transfer administrative company ownership, not shares or private titles."));
            }
            case "rename", "description" -> {
                args.between(4, 64, "company metadata <company> <text>");
                companyManager(company);
                review.field("previous", "Previous value", "rename".equals(action) ? company.name : company.description);
                review.field("replacement", "Replacement value", "rename".equals(action)
                        ? e.commerce.companyName(args.tail(3), company.id) : prose(args.tail(3), "Company description"));
                review.effect(t("effect.metadata", "Update descriptive metadata; names remain unique and no scripts or URLs are executed."));
            }
            case "transfer" -> {
                args.exactly(5, "company transfer <company> <player> <shares>");
                Player recipient = e.resolvePlayer(args.get(3));
                long quantity = Arguments.quantity(args.get(4));
                check(!recipient.id.equals(player), "You cannot transfer shares to yourself.");
                check(e.availableShares(company.id, actor.id()) >= quantity, "Not enough unreserved shares.");
                e.commerce.validateShareRecipient(company, player, recipient.id, quantity);
                review.party("recipient", recipient.id);
                review.field("shares", "Shares", Long.toString(quantity));
                review.bind("source-shares", company.shares.get(player));
                review.bind("destination-shares", company.shares.get(recipient.id));
                review.effect(t("effect.shares", "Transfer these unreserved shares without moving money or changing employment/management authority."));
            }
            case "disband" -> {
                args.exactly(3, "company disband <company>");
                companyOwner(company);
                check(actor.admin() || company.shares.entrySet().stream().noneMatch(h -> !company.owner.equals(h.getKey()) && h.getValue() > 0), "Other shareholders must approve dissolution.");
                e.commerce.assertDisposable(company, null);
                review.bind("shareholders", sorted(company.shares));
                review.effect(t("effect.company_disband", "Dissolve the company and its invitations only after balances, property, shares and active obligations permit deletion."));
            }
            default -> throw new UserError("Unknown company action.");
        }
    }

    private void companyProposal(String action, Arguments args) {
        CompanyProposal proposal = required(e.data.companyProposals, args.get(2), "company proposal");
        review.field("proposal", "Proposal", proposal.id, proposal.title + " — " + DisplayText.date(proposal.createdAt));
        review.field("company", "Company", e.companyName(proposal.companyId) + " [" + proposal.companyId + "]",
                companyName(e, proposal.companyId) + " (company)");
        review.field("decision", "Decision", proposal.type + "=" + proposal.value,
                decisionName(proposal.type) + ": " + decisionValue(e, proposal.type, proposal.value));
        review.field("title", "Title", proposal.title);
        review.field("text", "Text", proposal.text);
        review.bind("proposal-stage", proposal.status + ":" + proposal.endsAt + ":" + proposal.executionEndsAt);
        review.bind("proposal-parent-and-electorate", proposal.companyId + ":" + proposal.author + ":" + proposal.createdAt + ":" + sorted(proposal.electorate));
        if ("proposal".equals(action)) {
            args.exactly(3, "company proposal <proposal>");
            e.commerce.validateBallot(proposal);
            review.readOnly();
            return;
        }
        if ("cancel".equals(action)) {
            args.exactly(3, "company cancel <proposal>");
            check(has(Set.of("VOTING", "READY"), proposal.status) && (actor.admin()
                    || "VOTING".equals(proposal.status) && player.equals(proposal.author) && proposal.votes.isEmpty()), "Only the author before the first vote, or an operator, may cancel.");
            review.effect(t("effect.company_cancel", "Cancel this shareholder proposal without applying its proposed effects."));
            return;
        }
        long electorate = e.commerce.validateBallot(proposal);
        if ("vote".equals(action)) {
            args.exactly(4, "company vote <proposal> <vote>");
            e.companyRequired(proposal.companyId);
            check("VOTING".equals(proposal.status) && e.now() < proposal.endsAt, "The shareholder ballot is closed.");
            check(Set.of("yes", "no", "abstain").contains(args.get(3)), "Vote yes, no or abstain.");
            check(proposal.electorate.getOrDefault(player, 0L) > 0 && !proposal.votes.containsKey(player), "You have no eligible uncast shareholder ballot.");
            review.field("vote", "Vote", args.get(3), DisplayText.words(args.get(3)));
            review.field("weight", "Frozen voting weight", Long.toString(proposal.electorate.get(player)));
            review.effect(t("effect.company_vote", "Record one vote with the shares fixed when the ballot opened. Current share transfers cannot duplicate its voting weight."));
            return;
        }
        args.exactly(3, "company execute <proposal>");
        Company company = e.companyRequired(proposal.companyId);
        long deadline = proposal.executionEndsAt == 0 ? GovernanceEngine.deadline(proposal.endsAt, e.config.companyVotingMillis) : proposal.executionEndsAt;
        check("READY".equals(proposal.status) && e.now() < deadline, "The proposal is not in its settlement window.");
        check(actor.admin() || company.shares.getOrDefault(player, 0L) > 0 || e.mayManageCompany(actor.id(), company.id), "Only company participants may retry settlement.");
        long yes = e.commerce.weightedVotes(proposal, "yes"), no = e.commerce.weightedVotes(proposal, "no");
        check(Politics.reaches(yes + no + e.commerce.weightedVotes(proposal, "abstain"), electorate, e.config.companyQuorumBps) && yes > no, "Valid shareholder approval is required.");
        e.commerce.assertShareIntegrity(company);
        review.company(company);
        review.bind("current-shares", sorted(company.shares));
        switch (proposal.type) {
            case "owner" -> { e.commerce.validateNewOwner(company, proposal.value); review.party("new_owner", proposal.value); }
            case "dividend" -> {
                long cents;
                try { cents = Money.positive(Long.parseLong(proposal.value)); }
                catch (NumberFormatException invalid) { throw new UserError("The saved dividend amount requires repair."); }
                review.payments(e.commerce.dividends(company, cents, proposal.id), false, true);
            }
            case "dissolve" -> e.commerce.assertDisposable(company, proposal.id);
            case "roleplay" -> { }
            default -> throw new UserError("Unknown shareholder decision.");
        }
        review.effect(t("effect.company_execute", "Enact the approved shareholder decision now. Dividends use current holdings with exact cent allocation; all external financial guards still apply."));
    }

    private void mail(Arguments args) {
        String action = args.optional(1, "inbox");
        boolean official = "official".equals(action);
        int argument = official ? 4 : 2;
        String owner = actor.account();
        Government government = null;
        if (official) {
            action = args.get(2);
            government = e.gov(args.get(3));
            e.manage(actor, government);
            owner = e.account(government);
            review.government(government);
        }
        if (Set.of("inbox", "sent", "invitations").contains(action)) {
            check(!official || !"invitations".equals(action), "Use government invitations for official invitations.");
            args.between(official ? 4 : 1, argument + 1, "mail query [page]");
            args.page(argument);
            review.readOnly();
            return;
        }
        if (Set.of("send", "compose").contains(action)) {
            args.between(argument + 3, 64, "mail send <recipient> <subject> <body>");
            String recipient = e.communications.recipient(args.get(argument));
            String body = e.communications.userMessage(args.get(argument + 1), args.tail(argument + 2));
            review.party("sender", owner);
            review.party("recipient", recipient);
            review.field("subject", "Subject", args.get(argument + 1));
            review.field("body", "Body", body);
            review.effect(t("effect.mail_send", "Deliver this message and save the sender's copy. Mail retention may remove the oldest copies independently."));
            return;
        }
        check(Set.of("read", "reply", "delete").contains(action), "Unknown mail action.");
        args.between("reply".equals(action) ? argument + 2 : argument + 1, "reply".equals(action) ? 64 : argument + 1, "mail read|reply|delete");
        String id = args.get(argument);
        GovernancePresentation.MailEntry entry;
        try { entry = GovernancePresentation.mail(e, actor, owner + "|inbox|" + id); }
        catch (UserError missing) {
            if ("reply".equals(action)) throw missing;
            entry = GovernancePresentation.mail(e, actor, owner + "|sent|" + id);
        }
        Mail message = entry.message();
        review.bind("message_id", id);
        review.field("sent_at", "Sent at", Long.toString(message.sentAt), DisplayText.date(message.sentAt));
        review.field("subject", "Subject", message.subject);
        review.party("sender", message.sender);
        review.party("recipient", message.recipient);
        review.bind("message-body", message.body);
        if ("read".equals(action)) {
            review.effect(t("effect.mail_read", "Open this authorized message and mark its selected mailbox copy read. Reviewing this action does not mark it read."));
        } else if ("delete".equals(action)) {
            review.field("mailbox", "Mailbox", owner, account(e, owner));
            review.effect(t("effect.mail_delete", "Delete matching inbox/sent copies from this mailbox only. Other owners' copies are not removed."));
        } else {
            check(!"system".equals(message.sender), "System notifications cannot receive replies.");
            e.communications.validateRecipientAccount(message.sender);
            String subject = "Re: " + message.subject;
            subject = subject.substring(0, Math.min(100, subject.length()));
            review.field("reply_body", "Reply body", e.communications.userMessage(subject, args.tail(argument + 1)));
            review.party("reply_from", owner);
            review.party("reply_to", message.sender);
            review.effect(t("effect.mail_reply", "Send a new reply to this retained sender, using the currently authorized personal or official mailbox."));
        }
    }

    private void admin(Arguments args) {
        e.admin(actor);
        String action = args.get(1);
        switch (action) {
            case "bypass" -> {
                args.exactly(3, "admin bypass <on|off>");
                check(Set.of("on", "off").contains(args.get(2)), "Use on or off.");
                review.field("previous", "Previous value", Boolean.toString(e.requirePlayer(actor.id()).bypass),
                        e.requirePlayer(actor.id()).bypass ? "On" : "Off");
                review.field("replacement", "Replacement value", args.get(2), DisplayText.words(args.get(2)));
                review.effect(t("effect.bypass", "Change your explicit protection bypass. It remains valid only while you are currently an operator."));
            }
            case "unclaim" -> {
                args.exactly(3, "admin unclaim <chunk>");
                review.claim(e.unclaimPlan(actor, targetChunk(args.get(2)), true));
                review.effect(t("effect.force_unclaim", "Remove this claim and title with explicit operator authority, bypassing adjacency but never financial or workflow locks."));
            }
            case "delete" -> {
                args.between(4, 5, "admin delete <kind> <organization> [cascade]");
                if ("company".equals(args.get(2))) {
                    args.exactly(4, "admin delete company <company>");
                    Company company = e.companyRequired(args.get(3));
                    e.commerce.assertDisposable(company, null);
                    review.company(company);
                    review.bind("shareholders", sorted(company.shares));
                    review.effect(t("effect.company_disband", "Dissolve the company and its invitations only after balances, property, shares and active obligations permit deletion."));
                } else {
                    Government government = e.gov(args.get(3));
                    check(government.kind.name().equalsIgnoreCase(args.get(2)), "Government level does not match.");
                    if (args.size() == 5) check("cascade".equals(args.get(4)), "Use the literal cascade confirmation.");
                    review.government(government);
                    disband(government, args.size() == 5);
                }
            }
            case "rename", "leader" -> {
                args.exactly(4, "admin rename|leader <government> <value>");
                Government government = e.rawGovernment(args.get(2));
                review.field("government", "Government", government.id, governmentLabel(e, government.id, government.kind));
                if ("rename".equals(action)) {
                    review.field("previous", "Previous value", government.name);
                    review.field("replacement", "Replacement value", e.validGovernmentName(args.get(3), government.id));
                    review.effect(t("effect.metadata", "Update descriptive metadata; names remain unique and no scripts or URLs are executed."));
                } else {
                    check(e.validHierarchy(government), "Repair the hierarchy before appointing a leader.");
                    Player nominee = e.resolvePlayer(args.get(3));
                    if (!e.member(nominee.id, government)) administrativeMembership(nominee, government);
                    review.membership(nominee);
                    review.party("previous_leader", government.leader);
                    review.party("new_leader", nominee.id);
                    review.effect(t("effect.admin_leader", "Assign the required citizenship and transfer this leadership office. Existing affected leadership must still be transferred first."));
                }
            }
            case "owner", "reassign" -> {
                args.exactly(4, "admin owner|reassign <chunk> <destination>");
                String key = targetChunk(args.get(2));
                Claim claim = e.requiredClaim(key);
                review.claim(claim);
                if ("owner".equals(action)) {
                    check(e.viewableClaim(claim), "Repair this claim's hierarchy before transferring title.");
                    String destination = args.get(3);
                    e.validateOwnerAccount(destination);
                    check(!destination.startsWith("player:") || e.eligibleOwner(claim, destination), "Foreign player property ownership is not enabled.");
                    e.assertClaimFree(claim.key, null);
                    review.party("new_owner", destination);
                    review.effect(t("effect.property_owner", "Reassign only this private title and clear prior permits. Political city/state/nation and improvements remain unchanged."));
                } else {
                    Government target = e.gov(args.get(3));
                    Territory.Allocation plan = e.territory.reassignPlan(actor, key, target);
                    review.government(target);
                    review.allocations(List.of(plan));
                    review.effect(t("effect.property_reassign", "Explicitly reassign national ownership and optional state/city allocations, bypassing adjacency but not obligations. Public title follows the destination. Private owners and their permits are preserved."));
                }
            }
            case "audit", "history", "diagnostics" -> {
                args.between(2, 3, "admin query");
                if ("diagnostics".equals(action)) targetChunk(args.optional(2, "here")); else args.page(2);
                review.readOnly();
            }
            case "repair" -> {
                args.exactly(3, "admin repair <preview|apply>");
                check(Set.of("preview", "apply").contains(args.get(2)), "Use preview or apply.");
                if ("preview".equals(args.get(2))) review.readOnly();
                else {
                    check(e.data.schemaVersion == GovernanceData.CURRENT_SCHEMA, "This schema needs migration, not automatic repair.");
                    List<String> issues = e.integrity.audit();
                    review.field("audit", "Current integrity audit", String.join("\n", issues));
                    review.bind("repair-limits", List.of(e.config.maxHistory, e.config.maxMail, e.config.maxLawsPerNation));
                    review.effect(t("effect.repair", "Apply only safe integrity cleanup and retention. Leadership, private titles, shares and financial obligations are never guessed or discarded to force a clean audit."));
                }
            }
            default -> throw new UserError("This is not a core-domain administration action. Use the registered runtime action for saves, backups or diagnostics outside governance.");
        }
    }

    private void administrativeMembership(Player target, Government government) {
        Government removal = !Objects.equals(target.nationId, e.nationId(government)) ? e.data.governments.get(target.nationId)
                : government.kind != Kind.NATION && !Objects.equals(target.stateId, government.kind == Kind.STATE ? government.id : government.parentId)
                ? e.data.governments.get(target.stateId)
                : government.kind == Kind.CITY && !Objects.equals(target.cityId, government.id) ? e.data.governments.get(target.cityId) : null;
        for (Government scope : e.ancestors(government))
            check(e.member(target.id, scope) || e.members(scope).size() < e.memberLimit(scope.kind), "Citizenship capacity is full in " + scope.name + ".");
        if (removal != null) e.membershipRemovalScope(target.id, removal);
    }

    private void disband(Government government, boolean cascade) {
        List<Government> removed = e.disbandPlan(government, cascade, actor.admin());
        Set<String> ids = removed.stream().map(g -> g.id).collect(Collectors.toSet());
        List<Claim> claims = e.data.claims.values().stream().filter(Objects::nonNull)
                .filter(c -> government.kind == Kind.NATION && government.id.equals(c.nationId)).toList();
        review.field("governments_removed", "Governments removed", Integer.toString(removed.size()));
        review.field("claims_removed", "Claims and private titles removed", Integer.toString(claims.size()));
        review.field("private_titles", "Private titles removed", Long.toString(claims.stream().filter(c -> !e.publicTitle(c)).count()));
        review.field("removal_scope", "Organizations removed", removed.stream().map(g -> g.id).collect(Collectors.joining("\n")),
                removed.stream().map(g -> governmentLabel(e, g.id, g.kind)).collect(Collectors.joining("\n")));
        if (!claims.isEmpty()) review.field("removed_territory", "Territory and titles removed",
                claims.stream().map(GovernancePreviews::claimState).collect(Collectors.joining("\n")),
                claims.stream().map(c -> chunkName(c.key) + " — " + account(e, c.ownerAccount)).collect(Collectors.joining("\n")));
        removed.stream().sorted(Comparator.comparing(g -> g.id)).forEach(g -> review.bind("removed-government", g.id + ":" + g.leader + ":" + g.officers.stream().sorted().toList()));
        claims.stream().sorted(Comparator.comparing(c -> c.key)).forEach(c -> review.bind("removed-claim", claimState(c)));
        e.data.players.values().stream().filter(Objects::nonNull).filter(p -> ids.contains(p.nationId) || ids.contains(p.stateId) || ids.contains(p.cityId))
                .sorted(Comparator.comparing(p -> p.id)).forEach(p -> review.bind("removed-membership", p.id + ":" + p.nationId + ":" + p.stateId + ":" + p.cityId));
        review.effect(t("effect.disband", "Permanently remove the selected government scope and memberships. State/city allocations must already be cleared; their deletion never unclaims land. Only explicit nation cascade removes its unencumbered public claims. Private titles block national deletion, including operator deletion."));
    }

    private void rosterChange(Set<String> roster, String target, String operation, int limit) {
        check(Set.of("add", "remove").contains(operation), "Use add or remove.");
        if ("add".equals(operation)) check(!roster.contains(target) && roster.size() < limit, "The official already exists or the officer limit was reached.");
        else check(roster.contains(target), "That player is not an officer.");
        review.bind("previous-office", roster.contains(target));
    }

    private void companyManager(Company company) {
        check(actor.admin() || e.mayManageCompany(actor.id(), company.id), "Only company managers may perform this action.");
    }
    private void companyOwner(Company company) {
        check(actor.admin() || player.equals(company.owner), "Only the company owner may perform this action.");
    }
    private void titleManager(Claim claim) {
        check(actor.admin() || e.mayAccessAccount(actor.id(), claim.ownerAccount), "Only the private title's managers may perform this action.");
    }
    private String prose(String value, String label) { return GovernanceEngine.prose(value, e.config.maxDescriptionLength, label); }
    private static boolean has(Set<String> choices, String value) { return value != null && choices.contains(value); }
    private static <T> T required(Map<String, T> values, String id, String type) {
        check(GovernanceEngine.validUuid(id), "Use a canonical " + type + " UUID.");
        T value = values.get(id);
        check(value != null, "Unknown " + type + ".");
        return value;
    }
    private static String bidState(Bid bid) {
        return bid == null ? "" : bid.bidder + ":" + bid.account + ":" + bid.cents + ":" + bid.text + ":" + bid.createdAt;
    }
    private static String claimState(Claim claim) {
        return claim.key + ":" + claim.nationId + ":" + claim.stateId + ":" + claim.cityId
                + ":" + claim.ownerAccount + ":" + claim.improvements + ":" + claim.claimedAt + ":" + sorted(claim.permits);
    }
    private static String emergencyState(Emergency order) {
        return order == null ? "" : order.policy + ":" + order.value + ":" + order.author + ":" + order.expiresAt + ":" + order.reason;
    }
    private static String sorted(Map<String, ?> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(";"));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible); }
    }

    private static void digestValue(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private final class Review {
        private final List<ActionPreview.Line> lines = new ArrayList<>();
        private final StringBuilder state = new StringBuilder();
        private boolean money;

        Review(String command) {
            bind("actor", player + ":" + actor.admin());
            bind("command", command);
            party("actor", actor.account());
        }

        void bind(String label, Object value) {
            String string = Objects.toString(value, "");
            state.append(label.length()).append(':').append(label).append(string.length()).append(':').append(string);
        }

        void field(String key, String label, String value) {
            field(key, label, value, value);
        }

        void field(String key, String label, String value, String display) {
            bind(key, value);
            add(new ActionPreview.Line(t("preview." + key, label), UiText.literal(GovernancePresentation.clip(display, 4096)), true));
        }

        void policyValue(String key, String label, String policy, String value) {
            field(key, label, value, GovernancePresentation.policyValue(e, policy, value));
        }

        void party(String key, String id) {
            String name = id;
            String display = person(e, id);
            if (id != null && GovernanceEngine.validUuid(id)) {
                if (e.data.players.containsKey(id)) name = e.playerName(id) + " [" + id + "]";
                else if (e.data.governments.containsKey(id)) {
                    name = e.governmentName(id) + " [" + id + "]";
                    display = governmentLabel(e, id, null);
                }
            } else if (id != null) {
                if (id.contains(":")) name = accountState(id);
                display = account(e, id);
            }
            String label = switch (key) {
                case "actor" -> "Acting as"; case "leader" -> "Leader"; case "subject_player" -> "Subject player";
                case "invitee" -> "Invitee"; case "previous_leader" -> "Previous leader"; case "new_leader" -> "New leader";
                case "officer" -> "Officer"; case "permit_holder" -> "Permit holder"; case "contractor" -> "Contractor";
                case "candidate" -> "Candidate"; case "from_nation" -> "Proposing nation"; case "to_nation" -> "Counterparty nation";
                case "new_owner" -> "New owner"; case "employee" -> "Employee"; case "recipient" -> "Recipient";
                case "sender" -> "Sender"; case "reply_from" -> "Reply sender"; case "reply_to" -> "Reply recipient";
                default -> key;
            };
            field(key, label, name, display);
        }

        // Keep the previous material representation separate from the player-facing account name.
        private String accountState(String account) {
            if (account.startsWith("player:")) return e.playerName(account.substring(7)) + " [" + account + "]";
            if (account.startsWith("company:")) {
                String id = account.substring(8);
                return e.data.companies.get(id) == null ? "Former company [" + account + "]"
                        : e.companyName(id) + " [" + account + "]";
            }
            if (account.startsWith("escrow:contract:"))
                return "Contract escrow [" + account + "]";
            if (account.startsWith("system:"))
                return "System fee account [" + account + "]";
            if ("shareholders:at-execution".equals(account))
                return "Current shareholders at execution (recipients are not fixed yet)";
            String[] parts = account.split(":", 2);
            if (parts.length == 2 && Set.of("nation", "state", "city").contains(parts[0])) {
                Government government = e.data.governments.get(parts[1]);
                return government == null
                        ? "Former or unavailable government account [" + account + "]"
                        : government.name + " [" + account + "]";
            }
            return account;
        }

        void duration(String label, long duration) {
            field("duration_" + GovernancePresentation.actionKey(label), label, Long.toString(duration), DisplayText.duration(duration));
        }

        void government(Government government) {
            field("government", "Government", government.name + " [" + government.id + "] " + government.kind,
                    governmentLabel(e, government.id, government.kind));
            bind("government-authority", government.leader + ":" + government.parentId + ":" + government.officers.stream().sorted().toList());
        }

        void company(Company company) {
            field("company", "Company", company.name + " [" + company.id + "]", companyName(e, company.id) + " (company)");
            field("current_owner", "Current manager", company.owner, person(e, company.owner));
            bind("company-authority", company.members.stream().sorted().toList() + ":" + company.officers.stream().sorted().toList());
        }

        void membership(Player target) {
            field("current_membership", "Current citizenship", target.nationId + " / " + target.stateId + " / " + target.cityId,
                    "Nation: " + governmentName(e, target.nationId, Kind.NATION)
                            + "\nState: " + governmentName(e, target.stateId, Kind.STATE)
                            + "\nCity: " + governmentName(e, target.cityId, Kind.CITY));
            bind("membership-player", target.id + ":" + target.autoClaim);
        }

        void claim(Claim claim) {
            field("chunk", "Chunk", claim.key, chunkName(claim.key));
            field("private_title", "Private title", claim.ownerAccount, account(e, claim.ownerAccount));
            field("political_nation", "Claiming nation", claim.nationId, governmentLabel(e, claim.nationId, Kind.NATION));
            field("political_state", "State allocation", claim.stateId, governmentName(e, claim.stateId, Kind.STATE));
            field("political_city", "Current political city", claim.cityId, governmentLabel(e, claim.cityId, Kind.CITY));
            bind("claim", claimState(claim));
        }

        void chunks(List<String> keys) {
            field("chunks", "Selected chunks", String.join("\n", keys),
                    keys.stream().map(GovernancePresentation::chunkName).collect(Collectors.joining("\n")));
            keys.stream().sorted().map(e::requiredClaim).forEach(claim -> bind("claim", claimState(claim)));
        }

        void allocations(List<Territory.Allocation> plans) {
            String material = plans.stream().map(plan -> plan.claim().key + ":" + plan.nationId() + ":"
                    + plan.stateId() + ":" + plan.cityId() + ":" + plan.ownerAccount()).collect(Collectors.joining("\n"));
            String display = plans.stream().map(plan -> chunkName(plan.claim().key)
                    + " — nation: " + governmentName(e, plan.nationId(), Kind.NATION)
                    + "; state: " + governmentName(e, plan.stateId(), Kind.STATE)
                    + "; city: " + governmentName(e, plan.cityId(), Kind.CITY)
                    + "; title: " + account(e, plan.ownerAccount())).collect(Collectors.joining("\n"));
            field("allocation_result", "Resulting allocations and titles", material, display);
        }

        void contract(Contract contract) {
            field("contract", "Contract", contract.title + " [" + contract.id + "]",
                    contract.title + " — " + governmentName(e, contract.governmentId, null));
            field("status", "Current status", contract.status, DisplayText.words(contract.status));
            bind("contract-terms", contract.governmentId + ":" + contract.winner + ":" + contract.payeeAccount + ":"
                    + contract.escrowCents + ":" + contract.submittedBy + ":" + contract.completionNote + ":" + contract.bidEndsAt
                    + ":" + contract.reviewEndsAt + ":" + contract.description + ":" + contract.chunks);
        }

        void bill(Bill bill) {
            field("bill", "Bill", bill.title + " [" + bill.id + "]",
                    policyTitle(e, bill.policy, bill.value, bill.title) + " — " + governmentName(e, bill.nationId, Kind.NATION));
            field("bill_terms", "Retained bill terms", bill.policy + "=" + bill.value + "\n" + bill.text,
                    policyName(bill.policy) + ": " + GovernancePresentation.policyValue(e, bill.policy, bill.value)
                            + "\n" + policyBody(e, bill.policy, bill.value, bill.text));
            bind("bill-stage", bill.status + ":" + bill.nationId + ":" + bill.author + ":" + bill.treatyId + ":"
                    + bill.amendment + ":" + bill.voteEndsAt + ":" + bill.decisionEndsAt);
        }

        void treaty(DiplomaticProposal proposal) {
            field("treaty", "Diplomatic proposal", proposal.id,
                    DisplayText.words(proposal.type) + " — " + DisplayText.date(proposal.createdAt));
            field("status", "Current status", proposal.status, DisplayText.words(proposal.status));
            field("message", "Message", proposal.message);
            bind("treaty-stage", proposal.type + ":" + proposal.expiresAt + ":" + proposal.ratified);
            showTreatyTerms(proposal);
        }

        void treatyTerms(DiplomaticProposal proposal) {
            e.politics.validateTreatyReferences(proposal);
            showTreatyTerms(proposal);
        }

        private void showTreatyTerms(DiplomaticProposal proposal) {
            party("from_nation", "nation:" + proposal.fromNation);
            party("to_nation", "nation:" + proposal.toNation);
            String terms = proposal.chunks == null ? "(missing terms)" : proposal.chunks.stream()
                    .map(term -> term == null ? "(invalid term)" : term.key + ": " + term.fromNation + "/" + term.fromState
                            + "/" + term.fromCity + " -> " + term.toNation + "/" + term.toState + "/" + term.toCity).collect(Collectors.joining("\n"));
            String display = proposal.chunks == null ? "Territorial terms are unavailable." : proposal.chunks.stream()
                    .map(term -> term == null ? "A territorial transfer is unavailable." : chunkName(term.key) + ": "
                            + territoryScope(e, term.fromNation, term.fromState, term.fromCity)
                            + " → " + territoryScope(e, term.toNation, term.toState, term.toCity))
                    .collect(Collectors.joining("\n"));
            field("territory_terms", "Territorial terms", terms.isEmpty() ? "—" : terms, display.isEmpty() ? "No territory changes" : display);
            if (proposal.chunks != null) proposal.chunks.stream().filter(Objects::nonNull).forEach(term -> {
                Claim claim = e.data.claims.get(term.key);
                bind("treaty-claim", claim == null ? term.key + ":missing" : claimState(claim));
            });
            bind("money-terms", proposal.offeredCents + ":" + proposal.demandedCents);
        }

        void payments(List<EconomyAccess.Transfer> transfers, boolean future, boolean requiresEconomy) {
            money = true;
            long total = 0;
            Map<String, Long> net = new LinkedHashMap<>();
            Map<String, Long> funding = new LinkedHashMap<>();
            Map<String, Integer> terms = new LinkedHashMap<>();
            Set<String> recipients = new LinkedHashSet<>();
            MessageDigest distribution = sha256();
            boolean grouped = transfers.size() > 8;
            List<String> parties = new ArrayList<>();
            List<String> displayParties = new ArrayList<>();
            for (EconomyAccess.Transfer transfer : transfers) {
                Money.nonNegative(transfer.cents());
                if (requiresEconomy) e.requireEconomy(transfer.cents());
                total = Math.addExact(total, transfer.cents());
                net.merge(transfer.from(), -transfer.cents(), Math::addExact);
                net.merge(transfer.to(), transfer.cents(), Math::addExact);
                funding.merge(transfer.from(), transfer.cents(), Math::addExact);
                terms.merge(transfer.from(), 1, Integer::sum);
                recipients.add(transfer.to());
                if (!grouped) {
                    parties.add(accountState(transfer.from()) + " → " + accountState(transfer.to()) + ": " + Money.format(transfer.cents()));
                    displayParties.add(account(e, transfer.from()) + " → " + account(e, transfer.to()) + ": " + Money.format(transfer.cents()));
                }
                digestValue(distribution, transfer.from());
                digestValue(distribution, transfer.to());
                digestValue(distribution, Long.toString(transfer.cents()));
            }
            bind("payment-distribution", HexFormat.of().formatHex(distribution.digest()));
            if (grouped) {
                funding.entrySet().stream().limit(4).forEach(source -> {
                    parties.add(String.format(Locale.ROOT, "%s pays %s across %s transfer terms.",
                            accountState(source.getKey()), Money.format(source.getValue()), terms.get(source.getKey())));
                    displayParties.add(t("preview.payment_group", "%s pays %s across %s transfer terms.",
                            account(e, source.getKey()), Money.format(source.getValue()), terms.get(source.getKey())).fallback());
                });
                if (funding.size() > 4) {
                    String additional = t("preview.additional_sources", "%s additional funding accounts are included.", funding.size() - 4).fallback();
                    parties.add(additional);
                    displayParties.add(additional);
                }
                UiText summary = t("preview.recipient_summary", "%s recipients. Every recipient and exact amount will be checked again before payment.", recipients.size());
                bind("recipient-summary", recipients.size() + " recipient accounts. Exact accounts and amounts are bound to this review's fingerprint.");
                add(new ActionPreview.Line(t("preview.distribution", "Distribution"), summary, true));
            }
            field(future ? "conditional_total" : "pay_now", future ? "Conditional future transfers (not paid now)" : "Transfers now", Money.format(total));
            field("transfer_count", "Transfer terms", Integer.toString(transfers.size()));
            field("payment_parties", "Payment parties and amounts", String.join("\n", parties), String.join("\n", displayParties));
            bind("payment-timing", future);
            if (e.economy.available()) {
                List<String> balances = new ArrayList<>();
                boolean privateBalances = false;
                for (Map.Entry<String, Long> account : net.entrySet()) {
                    if (account.getValue() >= 0) continue;
                    boolean visible = actor.admin() || e.mayAccessAccount(actor.id(), account.getKey());
                    if (future && !visible) {
                        privateBalances = true;
                        continue;
                    }
                    long balance = e.economy.balance(account.getKey());
                    Money.nonNegative(balance);
                    if (!future) check(balance >= -account.getValue(), "Insufficient current funds in " + account(e, account.getKey()) + ".");
                    if (visible) balances.add(account(e, account.getKey()) + ": " + Money.format(balance));
                    else privateBalances = true;
                }
                if (privateBalances) balances.add(t("preview.private_balances", "Funding balances outside your account authority are not disclosed.").fallback());
                if (balances.isEmpty()) balances.add(t("preview.no_net_debit", "This batch has no net debit requiring a funding balance.").fallback());
                add(new ActionPreview.Line(t("preview.balances", "Current source balances (external holds still apply)"),
                        UiText.literal(GovernancePresentation.clip(String.join("\n", balances), 4096)), false));
            } else {
                add(new ActionPreview.Line(t("preview.economy", "Economy availability"),
                        t("preview.economy_offline", "Economy is unavailable. Zero-price actions work; any nonzero settlement requires the addon."), false));
            }
        }

        void effect(UiText effect) {
            bind("effect", effect.fallback());
            add(new ActionPreview.Line(t("preview.effect", "Effect"), effect, true));
        }

        void readOnly() {
            effect(t("effect.query", "Inspect authorized information. The eventual command may perform ordinary scheduled maintenance; this review does not."));
        }

        private void add(ActionPreview.Line line) {
            check(lines.size() < ActionPreview.MAX_LINES, "This review exceeds the supported detail limit; choose a narrower action.");
            lines.add(line);
        }

        ActionPreview finish() {
            return new ActionPreview(t("preview.review_title", "Review governance action"), lines,
                    money ? t("preview.money_warning", "Review parties, timing and amounts. No payment has been made by this review. Confirmation rechecks permissions and material terms; external financial guards can still refuse settlement.")
                            : t("preview.warning", "No mutation has been applied by this review. Confirmation rechecks authority and the affected terms."),
                    ActionPreview.digest(state.toString()));
        }
    }
}
