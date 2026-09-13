package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceData.Bid;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.Company;
import dev.statecraft.domain.GovernanceData.CompanyProposal;
import dev.statecraft.domain.GovernanceData.Contract;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.Invitation;
import dev.statecraft.domain.GovernanceData.Player;
import dev.statecraft.domain.GovernanceData.ShareReservation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.statecraft.domain.GovernanceEngine.check;
import static dev.statecraft.domain.GovernanceEngine.deadline;

final class Commerce {
    private static final Set<String> ACTIVE_CONTRACTS = Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED");
    private static final Set<String> ACTIVE_PROPOSALS = Set.of("VOTING", "READY");
    private static final Set<String> CLOSED_CONTRACTS = Set.of("COMPLETED", "CANCELLED", "EXPIRED");
    private static final Set<String> CLOSED_PROPOSALS = Set.of("ENACTED", "FAILED", "CANCELLED", "EXPIRED");
    private final GovernanceEngine e;

    Commerce(GovernanceEngine engine) {
        e = engine;
    }

    String companyCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action)) {
            args.between(1, 3, "company list [page]");
            return e.page("Companies", e.data.companies.values().stream().filter(e::viewableCompany)
                    .sorted(Comparator.comparing(c -> c.name)).map(c -> c.name + " [" + c.id + "] owner="
                            + e.playerName(c.owner) + " shares=" + c.totalShares).toList(), args.page(2));
        }
        if ("create".equals(action)) {
            args.exactly(3, "company create <name>");
            String name = companyName(args.get(2), null);
            check(e.data.companies.size() < e.config.maxCompanies, "World company limit reached.");
            check(e.data.companies.values().stream().filter(Objects::nonNull)
                            .filter(c -> actor.id().toString().equals(c.owner)).count() < e.config.maxCompaniesPerPlayer,
                    "Your company ownership limit has been reached.");
            Company company = new Company();
            company.id = GovernanceEngine.newId();
            company.name = name;
            company.owner = actor.id().toString();
            company.createdAt = e.now();
            company.totalShares = e.config.totalCompanyShares;
            company.shares.put(company.owner, company.totalShares);
            company.members.add(company.owner);
            e.pay(List.of(new EconomyAccess.Transfer(actor.account(), e.config.feeAccount,
                    e.config.companyCreationFee, "Company creation")));
            e.data.companies.put(company.id, company);
            e.history("company", company.id, "Created by " + actor.id());
            return "Created company " + name + " [" + company.id + "] with " + company.totalShares + " shares.";
        }
        if (Set.of("proposal", "vote", "execute", "cancel").contains(action))
            return companyProposalCommand(actor, action, args);
        if ("propose".equals(action)) return proposeCompany(actor, args);
        if ("proposals".equals(action)) {
            args.between(3, 4, "company proposals <company|all> [page]");
            Company company = "all".equals(args.get(2)) ? null : e.companyRequired(args.get(2));
            return e.page(company == null ? "All shareholder proposals" : company.name + " proposals",
                    e.data.companyProposals.values().stream().filter(Objects::nonNull)
                            .filter(p -> company == null || company.id.equals(p.companyId))
                            .sorted(Comparator.comparingLong((CompanyProposal p) -> p.createdAt).reversed()
                                    .thenComparing(p -> p.id, Comparator.nullsFirst(Comparator.naturalOrder())))
                            .map(p -> p.id + " " + p.status + " " + p.title + " [" + p.type + "=" + p.value + "]"
                                    + " company=" + e.companyName(p.companyId) + " [" + p.companyId + "]").toList(), args.page(3));
        }
        Company company = e.companyRequired(args.get(2));
        switch (action) {
            case "info" -> {
                args.exactly(3, "company info <company>");
                return company.name + " [" + company.id + "]\nOwner: " + e.playerName(company.owner)
                        + "\nMembers: " + company.members.size() + "\nTotal shares: " + company.totalShares
                        + "\nYour shares: " + sharesOf(company.id, actor.id()) + " (available: "
                        + availableShares(company.id, actor.id()) + ")\nTreasury: company:" + company.id
                        + "\nDescription: " + company.description;
            }
            case "members" -> {
                args.between(3, 4, "company members <company> [page]");
                return e.page(company.name + " members", company.members.stream().sorted()
                        .map(id -> e.playerName(id) + " [" + id + "] "
                                + (id.equals(company.owner) ? "owner" : company.officers.contains(id) ? "officer" : "member")).toList(), args.page(3));
            }
            case "shareholders" -> {
                args.between(3, 4, "company shareholders <company> [page]");
                return e.page(company.name + " shareholders", company.shares.entrySet().stream()
                        .filter(s -> s.getValue() != null && s.getValue() > 0).sorted(Map.Entry.comparingByKey())
                        .map(s -> e.playerName(s.getKey()) + ": " + s.getValue()).toList(), args.page(3));
            }
            case "invite" -> {
                args.exactly(4, "company invite <company> <player>");
                manage(actor, company);
                Player player = e.resolvePlayer(args.get(3));
                check(!company.members.contains(player.id), "That player is already a company member.");
                Invitation invitation = e.invitation(null, company.id, player.id);
                if (invitation == null) {
                    e.checkInvitationCapacity(null, company.id, player.id);
                    invitation = new Invitation();
                    invitation.id = GovernanceEngine.newId();
                    invitation.companyId = company.id;
                    invitation.playerId = player.id;
                }
                invitation.invitedBy = actor.id().toString();
                invitation.expiresAt = deadline(e.now(), e.config.invitationDurationMillis);
                e.data.invitations.put(invitation.id, invitation);
                e.mail(UUID.fromString(player.id), "Company invitation", "Join " + company.name + " using company accept " + company.id + ".");
                return "Company invitation sent.";
            }
            case "accept", "join" -> {
                args.exactly(3, "company accept <company>");
                check(!company.members.contains(actor.id().toString()), "You are already a member.");
                Invitation invitation = e.invitation(null, company.id, actor.id().toString());
                check(invitation != null, "A company invitation is required.");
                check(company.members.size() < e.config.maxCompanyMembers, "Company membership limit reached.");
                company.members.add(actor.id().toString());
                e.data.invitations.remove(invitation.id);
                e.history("company", company.id, "Member joined: " + actor.id());
                return "Joined company. Employment and share ownership are independent.";
            }
            case "decline", "revoke" -> {
                args.exactly("revoke".equals(action) ? 4 : 3, "company " + action + " <company>" + ("revoke".equals(action) ? " <player>" : ""));
                String player = actor.id().toString();
                if ("revoke".equals(action)) {
                    manage(actor, company);
                    player = e.resolvePlayer(args.get(3)).id;
                }
                Invitation invitation = e.invitation(null, company.id, player);
                check(invitation != null, "No active company invitation exists.");
                e.data.invitations.remove(invitation.id);
                return "Company invitation removed.";
            }
            case "leave", "kick" -> {
                args.exactly("kick".equals(action) ? 4 : 3, "company " + action + " <company>" + ("kick".equals(action) ? " <player>" : ""));
                String player = actor.id().toString();
                if ("kick".equals(action)) {
                    manage(actor, company);
                    player = e.resolvePlayer(args.get(3)).id;
                    check(actor.admin() || actor.id().toString().equals(company.owner) || !company.officers.contains(player),
                            "Only the owner may remove another company officer.");
                }
                check(!player.equals(company.owner), "Transfer company management ownership before the owner leaves.");
                check(company.members.remove(player), "That player is not a member.");
                company.officers.remove(player);
                e.history("company", company.id, "Member removed: " + player);
                return "Membership removed; existing shares and reservations are preserved.";
            }
            case "officer" -> {
                args.exactly(5, "company officer <company> <player> <add|remove>");
                owner(actor, company);
                Player player = e.resolvePlayer(args.get(3));
                check(company.members.contains(player.id), "Officers must be company members.");
                check(!player.id.equals(company.owner), "The owner already has management authority.");
                check(Set.of("add", "remove").contains(args.get(4)), "Use add or remove.");
                if ("add".equals(args.get(4))) {
                    check(company.officers.size() < e.config.maxOfficers, "Company officer limit reached.");
                    check(company.officers.add(player.id), "That player is already an officer.");
                } else check(company.officers.remove(player.id), "That player is not an officer.");
                e.history("company", company.id, "Officer " + args.get(4) + ": " + player.id);
                return "Company officers updated.";
            }
            case "owner" -> {
                args.exactly(4, "company owner <company> <player>");
                owner(actor, company);
                Player target = e.resolvePlayer(args.get(3));
                validateNewOwner(company, target.id);
                check(!target.id.equals(company.owner), "That player is already the company owner.");
                company.owner = target.id;
                company.officers.remove(target.id);
                e.history("company", company.id, "Administrative owner changed to " + target.id);
                return "Company management ownership transferred. Equity shares were not changed.";
            }
            case "rename", "description" -> {
                args.between(4, 64, "company " + action + " <company> <text>");
                manage(actor, company);
                if ("rename".equals(action)) company.name = companyName(args.tail(3), company.id);
                else company.description = GovernanceEngine.prose(args.tail(3), e.config.maxDescriptionLength, "Company description");
                e.history("company", company.id, action + " by " + actor.id());
                return "Company " + action + " updated.";
            }
            case "transfer" -> {
                args.exactly(5, "company transfer <company> <player> <shares>");
                Player player = e.resolvePlayer(args.get(3));
                transferShares(company.id, actor.id(), UUID.fromString(player.id), Arguments.quantity(args.get(4)));
                return "Shares transferred.";
            }
            case "disband" -> {
                args.exactly(3, "company disband <company>");
                owner(actor, company);
                disband(company, actor.admin());
                return "Company disbanded.";
            }
            default -> throw new UserError("Unknown company action '" + action + "'. Use help companies.");
        }
    }

    private String proposeCompany(Actor actor, Arguments args) {
        args.between(7, 64, "company propose <company> <owner|dividend|roleplay|dissolve> <value|-> <title> <text>");
        Company company = e.companyRequired(args.get(2));
        assertShareIntegrity(company);
        check(company.shares.getOrDefault(actor.id().toString(), 0L) > 0, "Only shareholders may introduce company proposals.");
        check(e.data.companyProposals.values().stream().filter(Objects::nonNull)
                        .filter(p -> company.id.equals(p.companyId) && ACTIVE_PROPOSALS.contains(p.status)).count()
                        < e.config.maxCompanyProposals, "Too many active company proposals.");
        String type = args.get(3);
        String value;
        switch (type) {
            case "owner" -> {
                Player player = e.resolvePlayer(args.get(4));
                check(company.members.contains(player.id) && company.shares.getOrDefault(player.id, 0L) > 0,
                        "A proposed owner must be a company member and shareholder.");
                value = player.id;
            }
            case "dividend" -> {
                long amount = Money.positive(Money.parse(args.get(4)));
                e.requireEconomy(amount);
                value = Long.toString(amount);
            }
            case "roleplay", "dissolve" -> {
                check("-".equals(args.get(4)), "Use '-' as the value for roleplay/dissolve proposals.");
                value = "-";
            }
            default -> throw new UserError("Company proposal types are owner, dividend, roleplay, and dissolve.");
        }
        CompanyProposal proposal = new CompanyProposal();
        proposal.id = GovernanceEngine.newId();
        proposal.companyId = company.id;
        proposal.author = actor.id().toString();
        proposal.type = type;
        proposal.value = value;
        proposal.title = GovernanceEngine.text(args.get(5), 100, "Proposal title");
        proposal.text = GovernanceEngine.prose(args.tail(6), e.config.maxDescriptionLength, "Proposal text");
        proposal.createdAt = e.now();
        proposal.endsAt = deadline(e.now(), e.config.companyVotingMillis);
        proposal.executionEndsAt = deadline(proposal.endsAt, e.config.companyVotingMillis);
        company.shares.forEach((id, shares) -> { if (shares > 0) proposal.electorate.put(id, shares); });
        e.data.companyProposals.put(proposal.id, proposal);
        e.history("company-proposal", proposal.id, "Introduced " + type + " for " + company.id + " by " + actor.id());
        return "Company proposal " + proposal.id + ". Voting weight is fixed at creation, including reserved shares still owned.";
    }

    private String companyProposalCommand(Actor actor, String action, Arguments args) {
        CompanyProposal proposal = companyProposal(args.get(2));
        switch (action) {
            case "proposal" -> {
                args.exactly(3, "company proposal <proposalId>");
                long eligible = validateBallot(proposal);
                return proposal.title + " [" + proposal.id + "] " + proposal.status + "\nCompany: " + e.companyName(proposal.companyId)
                        + "\nPolicy: " + proposal.type + "=" + proposal.value + "\nVoting ends: " + proposal.endsAt
                        + "\nSettlement ends: " + settlementDeadline(proposal)
                        + "\nEligible shares=" + eligible + " yes=" + weightedVotes(proposal, "yes")
                        + " no=" + weightedVotes(proposal, "no") + " abstain=" + weightedVotes(proposal, "abstain")
                        + "\n" + proposal.text + (proposal.lastError.isEmpty() ? "" : "\nBlocked: " + proposal.lastError);
            }
            case "vote" -> {
                args.exactly(4, "company vote <proposalId> <yes|no|abstain>");
                validateBallot(proposal);
                check("VOTING".equals(proposal.status) && e.now() < proposal.endsAt, "This proposal is not accepting votes.");
                check(Set.of("yes", "no", "abstain").contains(args.get(3)), "Vote yes, no, or abstain.");
                check(proposal.electorate.getOrDefault(actor.id().toString(), 0L) > 0, "You were not a shareholder when this ballot opened.");
                check(!proposal.votes.containsKey(actor.id().toString()), "You already voted on this proposal.");
                proposal.votes.put(actor.id().toString(), args.get(3));
                return "Share-weighted vote recorded.";
            }
            case "execute" -> {
                args.exactly(3, "company execute <proposalId>");
                Company company = e.companyRequired(proposal.companyId);
                check(actor.admin() || company.shares.getOrDefault(actor.id().toString(), 0L) > 0
                                || e.mayManageCompany(actor.id(), company.id), "Only company participants may retry execution.");
                check("READY".equals(proposal.status), "This proposal is not awaiting execution.");
                check(e.now() < settlementDeadline(proposal), "The settlement window has expired.");
                enactCompanyProposal(proposal);
                return "Shareholder proposal enacted.";
            }
            case "cancel" -> {
                args.exactly(3, "company cancel <proposalId>");
                check(ACTIVE_PROPOSALS.contains(proposal.status), "This proposal has already concluded.");
                check(actor.admin() || (actor.id().toString().equals(proposal.author) && "VOTING".equals(proposal.status)
                                && proposal.votes.isEmpty()), "Only the author before the first vote, or an operator, may cancel.");
                proposal.status = "CANCELLED";
                e.history("company-proposal", proposal.id, "Cancelled by " + actor.id());
                return "Company proposal cancelled.";
            }
            default -> throw new IllegalStateException();
        }
    }

    private void advanceCompanyProposal(CompanyProposal proposal, long now) {
        if ("VOTING".equals(proposal.status) && now >= proposal.endsAt) {
            long eligible = validateBallot(proposal);
            long yes = weightedVotes(proposal, "yes");
            long no = weightedVotes(proposal, "no");
            long cast = yes + no + weightedVotes(proposal, "abstain");
            proposal.status = Politics.reaches(cast, eligible, e.config.companyQuorumBps) && yes > no ? "READY" : "FAILED";
            e.history("company-proposal", proposal.id, proposal.status + ": eligible=" + eligible + " yes=" + yes + " no=" + no);
            if ("READY".equals(proposal.status) && now < settlementDeadline(proposal)) {
                try { enactCompanyProposal(proposal); }
                catch (UserError error) {
                    proposal.lastError = clip(error.getMessage());
                    e.history("company-proposal", proposal.id, "Settlement blocked: " + proposal.lastError);
                }
            }
        }
        if ("READY".equals(proposal.status) && now >= settlementDeadline(proposal)) {
            proposal.status = "EXPIRED";
            e.history("company-proposal", proposal.id, "Settlement window expired without enactment.");
        }
    }

    private void enactCompanyProposal(CompanyProposal proposal) {
        Company company = e.companyRequired(proposal.companyId);
        long electorate = validateBallot(proposal);
        long yes = weightedVotes(proposal, "yes");
        long no = weightedVotes(proposal, "no");
        check(Politics.reaches(yes + no + weightedVotes(proposal, "abstain"), electorate, e.config.companyQuorumBps)
                && yes > no, "This proposal has no valid shareholder approval.");
        assertShareIntegrity(company);
        switch (proposal.type) {
            case "owner" -> {
                validateNewOwner(company, proposal.value);
                company.owner = proposal.value;
                company.officers.remove(proposal.value);
            }
            case "dividend" -> {
                long cents;
                try { cents = Money.positive(Long.parseLong(proposal.value)); }
                catch (NumberFormatException error) { throw new UserError("Invalid dividend amount in this proposal."); }
                e.requireEconomy(cents);
                e.pay(dividends(company, cents, proposal.id));
            }
            case "roleplay" -> { }
            case "dissolve" -> {
                assertDisposable(company, proposal.id);
                deleteCompany(company);
            }
            default -> throw new UserError("Unknown company proposal policy; operator review is required.");
        }
        proposal.status = "ENACTED";
        proposal.lastError = "";
        e.history("company-proposal", proposal.id, "Enacted " + proposal.type + " for " + company.id
                + ("roleplay".equals(proposal.type) ? " (roleplay only)" : ""));
    }

    private List<EconomyAccess.Transfer> dividends(Company company, long total, String reference) {
        Map<String, Long> allocations = new LinkedHashMap<>();
        Map<String, BigInteger> remainders = new HashMap<>();
        BigInteger denominator = BigInteger.valueOf(company.totalShares);
        long allocated = 0;
        for (Map.Entry<String, Long> shareholder : company.shares.entrySet()) {
            e.requirePlayer(UUID.fromString(shareholder.getKey()));
            BigInteger[] division = BigInteger.valueOf(total).multiply(BigInteger.valueOf(shareholder.getValue()))
                    .divideAndRemainder(denominator);
            long cents = division[0].longValueExact();
            allocations.put(shareholder.getKey(), cents);
            remainders.put(shareholder.getKey(), division[1]);
            allocated = Money.add(allocated, cents);
        }
        long remainder = total - allocated;
        List<String> order = allocations.keySet().stream()
                .sorted(Comparator.<String, BigInteger>comparing(remainders::get).reversed().thenComparing(Comparator.naturalOrder())).toList();
        for (int i = 0; i < remainder; i++) allocations.merge(order.get(i), 1L, Long::sum);
        return allocations.entrySet().stream().filter(a -> a.getValue() > 0)
                .map(a -> new EconomyAccess.Transfer("company:" + company.id, "player:" + a.getKey(), a.getValue(),
                        "Shareholder dividend " + reference)).toList();
    }

    private long weightedVotes(CompanyProposal proposal, String choice) {
        return proposal.votes.entrySet().stream().filter(v -> choice.equals(v.getValue()))
                .mapToLong(v -> proposal.electorate.getOrDefault(v.getKey(), 0L)).sum();
    }

    private long validateBallot(CompanyProposal proposal) {
        check(proposal.type != null && Set.of("owner", "dividend", "roleplay", "dissolve").contains(proposal.type),
                "Unknown shareholder proposal policy; operator audit required.");
        long electorate = 0;
        for (Map.Entry<String, Long> holder : proposal.electorate.entrySet()) {
            check(GovernanceEngine.validUuid(holder.getKey()) && e.data.players.containsKey(holder.getKey())
                            && holder.getValue() != null && holder.getValue() > 0 && holder.getValue() <= 1_000_000_000L,
                    "Invalid shareholder ballot weights; operator audit required.");
            electorate += holder.getValue();
            check(electorate <= 1_000_000_000L, "Invalid shareholder ballot supply; operator audit required.");
        }
        check(electorate > 0, "This shareholder ballot has no electorate.");
        for (Map.Entry<String, String> vote : proposal.votes.entrySet())
            check(proposal.electorate.containsKey(vote.getKey()) && vote.getValue() != null
                            && Set.of("yes", "no", "abstain").contains(vote.getValue()),
                    "Invalid shareholder vote; operator audit required.");
        return electorate;
    }

    private CompanyProposal companyProposal(String id) {
        CompanyProposal proposal = e.data.companyProposals.get(id);
        check(proposal != null, "Unknown company proposal ID.");
        return proposal;
    }

    private long settlementDeadline(CompanyProposal proposal) {
        if (proposal.executionEndsAt == 0)
            proposal.executionEndsAt = deadline(proposal.endsAt, e.config.companyVotingMillis);
        return proposal.executionEndsAt;
    }

    long sharesOf(String companyId, UUID shareholder) {
        Company company = e.data.companies.get(companyId);
        if (company == null || shareholder == null) return 0;
        Long shares = company.shares.getOrDefault(shareholder.toString(), 0L);
        check(shares != null && shares >= 0 && shares <= company.totalShares, "Invalid shareholder balance; operator audit required.");
        return shares;
    }

    long availableShares(String companyId, UUID shareholder) {
        Company company = e.data.companies.get(companyId);
        if (company == null || shareholder == null) return 0;
        Map<String, Long> reserved = assertShareIntegrity(company);
        return sharesOf(companyId, shareholder) - reserved.getOrDefault(shareholder.toString(), 0L);
    }

    void transferShares(String companyId, UUID from, UUID to, long quantity) {
        Company company = e.companyRequired(companyId);
        e.requirePlayer(from);
        e.requirePlayer(to);
        check(!from.equals(to), "Shares cannot be transferred to their current owner.");
        shareQuantity(quantity);
        assertShareIntegrity(company);
        check(availableShares(company.id, from) >= quantity, "Not enough unreserved shares.");
        validateShareRecipient(company, from.toString(), to.toString(), quantity);
        moveShares(company, from.toString(), to.toString(), quantity);
        e.history("shares", company.id, from + " -> " + to + ": " + quantity);
    }

    void reserveShares(String companyId, UUID owner, String reference, long quantity) {
        Company company = e.companyRequired(companyId);
        e.requirePlayer(owner);
        reference(reference);
        shareQuantity(quantity);
        assertShareIntegrity(company);
        ShareReservation existing = e.data.shareReservations.get(reference);
        if (existing != null) {
            check(company.id.equals(existing.companyId) && owner.toString().equals(existing.owner) && quantity == existing.quantity,
                    "This reservation reference is already in use with different terms.");
            return;
        }
        check(e.data.shareReservations.size() < e.config.maxShareReservations, "World share reservation limit reached.");
        check(availableShares(company.id, owner) >= quantity, "Not enough unreserved shares for this listing.");
        ShareReservation reservation = new ShareReservation();
        reservation.reference = reference;
        reservation.companyId = company.id;
        reservation.owner = owner.toString();
        reservation.quantity = quantity;
        e.data.shareReservations.put(reference, reservation);
        e.history("shares", company.id, "Reserved " + quantity + " from " + owner + " as " + reference);
    }

    void releaseShares(String reference) {
        reference(reference);
        ShareReservation reservation = e.data.shareReservations.remove(reference);
        if (reservation != null) e.history("shares", reservation.companyId, "Released reservation " + reference);
    }

    void settleShares(String reference, UUID buyer, long quantity) {
        reference(reference);
        e.requirePlayer(buyer);
        shareQuantity(quantity);
        ShareReservation reservation = e.data.shareReservations.get(reference);
        check(reservation != null, "Unknown share reservation.");
        Company company = e.companyRequired(reservation.companyId);
        assertShareIntegrity(company);
        check(!buyer.toString().equals(reservation.owner), "You cannot buy your own reserved shares.");
        check(quantity <= reservation.quantity, "The requested quantity exceeds the remaining reserved shares.");
        validateShareRecipient(company, reservation.owner, buyer.toString(), quantity);
        moveShares(company, reservation.owner, buyer.toString(), quantity);
        reservation.quantity -= quantity;
        if (reservation.quantity == 0) e.data.shareReservations.remove(reference);
        e.history("shares", company.id, "Settled " + quantity + " from " + reference + " to " + buyer);
    }

    private void moveShares(Company company, String from, String to, long quantity) {
        long remaining = company.shares.getOrDefault(from, 0L) - quantity;
        if (remaining == 0) company.shares.remove(from);
        else company.shares.put(from, remaining);
        company.shares.put(to, company.shares.getOrDefault(to, 0L) + quantity);
    }

    private void validateShareRecipient(Company company, String from, String to, long quantity) {
        check(company.shares.containsKey(to) || company.shares.size() < e.config.maxCompanyShareholders
                        || company.shares.getOrDefault(from, 0L) == quantity,
                "Company shareholder limit reached.");
        check(company.shares.getOrDefault(to, 0L) <= company.totalShares - quantity, "Share transfer exceeds the company's issued shares.");
    }

    Map<String, Long> assertShareIntegrity(Company company) {
        check(company.totalShares > 0 && company.totalShares <= 1_000_000_000L, "Invalid company share issuance; operator audit required.");
        Map<String, Long> reservations = new HashMap<>();
        for (Map.Entry<String, ShareReservation> entry : e.data.shareReservations.entrySet()) {
            ShareReservation reservation = entry.getValue();
            if (reservation == null || !company.id.equals(reservation.companyId)) continue;
            check(Objects.equals(entry.getKey(), reservation.reference) && reservation.reference != null
                            && reservation.reference.matches("[A-Za-z0-9_.:-]{1,128}")
                            && company.shares.containsKey(reservation.owner) && reservation.quantity > 0
                            && reservation.quantity <= company.totalShares,
                    "A share reservation has no valid owner, reference, or quantity; operator audit required.");
            long reserved = reservations.getOrDefault(reservation.owner, 0L) + reservation.quantity;
            check(reserved <= company.totalShares, "Share reservations exceed issued shares; operator audit required.");
            reservations.put(reservation.owner, reserved);
        }
        long total = 0;
        for (Map.Entry<String, Long> shares : company.shares.entrySet()) {
            check(GovernanceEngine.validUuid(shares.getKey()) && e.data.players.containsKey(shares.getKey())
                            && shares.getValue() != null && shares.getValue() > 0 && shares.getValue() <= company.totalShares,
                    "Invalid company shareholder ledger; operator audit required.");
            total += shares.getValue();
            check(total <= company.totalShares, "Company share supply is inconsistent; operator audit required.");
            check(reservations.getOrDefault(shares.getKey(), 0L) <= shares.getValue(), "Share reservations exceed the holder's shares.");
        }
        check(total == company.totalShares, "Company share supply is inconsistent; operator audit required.");
        return reservations;
    }

    private void shareQuantity(long value) {
        check(value > 0 && value <= 1_000_000_000L, "Share quantity must be between 1 and 1000000000.");
    }

    private void reference(String reference) {
        check(reference != null && reference.matches("[A-Za-z0-9_.:-]{1,128}"), "Invalid stock reservation reference.");
    }

    void validateNewOwner(Company company, String player) {
        check(e.data.players.containsKey(player) && company.members.contains(player)
                        && company.shares.getOrDefault(player, 0L) > 0, "The new owner must be a known member and shareholder.");
        check(e.data.companies.values().stream().filter(Objects::nonNull)
                        .filter(c -> !c.id.equals(company.id) && player.equals(c.owner)).count() < e.config.maxCompaniesPerPlayer,
                "The new owner's company limit has been reached.");
        e.requireFinancialGuards();
        check(!e.economy.isAccountInUse("company:" + company.id), "Company ownership cannot change while its economy account is in use.");
        check(!companyLocked(company.id), "Company ownership cannot change while a government bid/contract is active.");
    }

    void disband(Company company, boolean administrator) {
        if (!administrator) {
            check(company.shares.entrySet().stream().noneMatch(s -> !company.owner.equals(s.getKey()) && s.getValue() > 0),
                    "Other shareholders must approve dissolution through a shareholder proposal.");
        }
        assertDisposable(company, null);
        deleteCompany(company);
    }

    private void assertDisposable(Company company, String ignoredProposal) {
        assertShareIntegrity(company);
        check(e.data.shareReservations.values().stream().filter(Objects::nonNull)
                .noneMatch(r -> company.id.equals(r.companyId)), "Cancel all reserved stock listings before dissolving the company.");
        e.assertAccountDisposable("company:" + company.id);
        check(!companyLocked(company.id), "Withdraw/settle active government bids or contracts before dissolving this company.");
        check(e.data.companyProposals.values().stream().filter(Objects::nonNull)
                        .noneMatch(p -> company.id.equals(p.companyId) && !Objects.equals(ignoredProposal, p.id)
                                && (p.status == null || !CLOSED_PROPOSALS.contains(p.status))), "Conclude other company proposals before dissolution.");
        check(e.data.claims.values().stream().filter(Objects::nonNull)
                .noneMatch(c -> ("company:" + company.id).equals(c.ownerAccount)), "Transfer company-owned property before dissolution.");
    }

    private void deleteCompany(Company company) {
        e.data.companies.remove(company.id);
        e.data.invitations.values().removeIf(i -> i != null && company.id.equals(i.companyId));
        e.history("company", company.id, "Dissolved " + company.name);
    }

    private void manage(Actor actor, Company company) {
        check(actor.admin() || e.mayManageCompany(actor.id(), company.id), "Only company managers may perform that action.");
    }

    private void owner(Actor actor, Company company) {
        check(actor.admin() || actor.id().toString().equals(company.owner), "Only the company owner may perform that action.");
    }

    private String companyName(String name, String id) {
        String valid = e.organizationName(name);
        check(e.data.companies.values().stream().filter(Objects::nonNull)
                        .noneMatch(c -> !Objects.equals(id, c.id) && valid.equalsIgnoreCase(c.name)), "That company name is already in use.");
        return valid;
    }

    String contractCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action) || "my".equals(action)) {
            args.between(1, "my".equals(action) ? 3 : 4, "contract " + action + ("my".equals(action) ? " [page]" : " [government|all] [page]"));
            String ref = "my".equals(action) ? "all" : args.optional(2, "all");
            Government government = "all".equals(ref) ? null : e.gov(ref);
            List<String> rows = e.data.contracts.values().stream().filter(Objects::nonNull)
                    .filter(c -> government == null || government.id.equals(c.governmentId))
                    .filter(c -> !"my".equals(action) || c.bids.containsKey(actor.id().toString()) || actor.id().toString().equals(c.author))
                    .sorted(Comparator.comparingLong((Contract c) -> c.createdAt).reversed().thenComparing(c -> c.id))
                    .map(c -> c.id + " " + c.status + " " + c.title + " government=" + e.governmentName(c.governmentId)).toList();
            return e.page("Government contracts", rows, args.page("my".equals(action) ? 2 : 3));
        }
        if ("create".equals(action)) {
            args.exactly(6, "contract create <government> <title> <description> <chunkKey,...|here>");
            Government government = e.gov(args.get(2));
            e.manage(actor, government);
            check(e.data.contracts.values().stream().filter(Objects::nonNull)
                            .filter(c -> government.id.equals(c.governmentId) && ACTIVE_CONTRACTS.contains(c.status)).count()
                            < e.config.maxContractsPerGovernment, "Government active contract limit reached.");
            Contract contract = new Contract();
            contract.id = GovernanceEngine.newId();
            contract.governmentId = government.id;
            contract.author = actor.id().toString();
            contract.title = GovernanceEngine.text(args.get(3), 100, "Contract title");
            contract.description = GovernanceEngine.prose(args.get(4), e.config.maxDescriptionLength, "Contract description");
            String[] values = args.get(5).split(",", -1);
            check(values.length >= 1 && values.length <= e.config.maxContractChunks, "Invalid number of contract chunks.");
            Set<String> seen = new LinkedHashSet<>();
            for (String value : values) {
                String key = e.chunkKey(actor, value);
                check(seen.add(key), "A contract cannot list the same chunk twice.");
                e.assertClaimFree(key, null);
            }
            contract.chunks.addAll(seen);
            contract.createdAt = e.now();
            contract.bidEndsAt = deadline(e.now(), e.config.contractBiddingMillis);
            contract.reviewEndsAt = deadline(contract.bidEndsAt, e.config.contractReviewMillis);
            validateContractChunks(contract);
            e.data.contracts.put(contract.id, contract);
            e.history("contract", contract.id, "Created by " + actor.id() + " for " + government.id);
            e.governmentMail(government.id, "Contract opened", contract.title + " [" + contract.id + "]. Bidding ends " + contract.bidEndsAt + ".");
            return "Created government contract " + contract.id + "; selected chunks are reserved.";
        }
        Contract contract = contract(args.get(2));
        switch (action) {
            case "info" -> {
                args.between(3, 4, "contract info <contractId> [page]");
                List<String> rows = new ArrayList<>();
                rows.add(contract.id + " " + contract.status + " " + contract.title);
                rows.add("Government: " + e.governmentName(contract.governmentId) + "; author: " + e.playerName(contract.author));
                rows.add(contract.description);
                rows.add("Bidding ends=" + contract.bidEndsAt + ", review ends=" + contract.reviewEndsAt + ", bids=" + contract.bids.size());
                rows.add("Winner=" + e.playerName(contract.winner) + ", payee=" + contract.payeeAccount
                        + ", escrow=" + Money.format(contract.escrowCents));
                if (!contract.completionNote.isEmpty()) rows.add("Submission: " + contract.completionNote);
                rows.addAll(contract.chunks);
                return e.page("Contract details", rows, args.page(3));
            }
            case "bid" -> {
                args.between(5, 6, "contract bid <contractId> <amount> <description> [company:nameOrId]");
                check("OPEN".equals(contract.status) && e.now() < contract.bidEndsAt, "This contract is not accepting bids.");
                check(contract.bids.containsKey(actor.id().toString()) || contract.bids.size() < e.config.maxContractBids,
                        "Contract bid limit reached.");
                long amount = Money.parse(args.get(3));
                e.requireEconomy(amount);
                String text = GovernanceEngine.prose(args.get(4), e.config.maxDescriptionLength, "Bid description");
                String account = actor.account();
                if (args.size() == 6) {
                    check(args.get(5).startsWith("company:"), "Optional bid destination must be company:<name or UUID>.");
                    Company company = e.companyRequired(args.get(5).substring("company:".length()));
                    check(e.mayManageCompany(actor.id(), company.id), "You cannot bid on behalf of that company.");
                    account = "company:" + company.id;
                }
                Bid bid = new Bid();
                bid.bidder = actor.id().toString();
                bid.account = account;
                bid.cents = amount;
                bid.text = text;
                bid.createdAt = e.now();
                validateBidDestination(bid, true);
                contract.bids.put(bid.bidder, bid);
                e.history("contract", contract.id, "Bid submitted by " + actor.id() + " for " + amount);
                return "Bid submitted. Its payee is fixed to " + account + ".";
            }
            case "withdraw" -> {
                args.exactly(3, "contract withdraw <contractId>");
                check(Set.of("OPEN", "REVIEW").contains(contract.status), "Only unawarded bids may be withdrawn.");
                check(contract.bids.remove(actor.id().toString()) != null, "You do not have a bid on this contract.");
                return "Bid withdrawn.";
            }
            case "bids" -> {
                args.between(3, 4, "contract bids <contractId> [page]");
                e.manage(actor, e.gov(contract.governmentId));
                return e.page("Bid review", contract.bids.values().stream().filter(Objects::nonNull)
                        .sorted(Comparator.comparingLong((Bid b) -> b.cents).thenComparing(b -> b.bidder))
                        .map(b -> e.playerName(b.bidder) + " [" + b.bidder + "] " + Money.format(b.cents)
                                + " to=" + b.account + " " + b.text).toList(), args.page(3));
            }
            case "review" -> {
                args.exactly(3, "contract review <contractId>");
                e.manage(actor, e.gov(contract.governmentId));
                check("OPEN".equals(contract.status), "Only open contracts can enter bid review.");
                check(!contract.bids.isEmpty(), "There are no bids to review.");
                contract.status = "REVIEW";
                contract.reviewEndsAt = deadline(e.now(), e.config.contractReviewMillis);
                e.history("contract", contract.id, "Review opened by " + actor.id());
                return "Bidding closed; contract entered review.";
            }
            case "award" -> {
                args.exactly(4, "contract award <contractId> <bidder>");
                Government government = e.gov(contract.governmentId);
                e.manage(actor, government);
                check("REVIEW".equals(contract.status), "Move the contract into review before awarding it.");
                Player winner = e.resolvePlayer(args.get(3));
                Bid bid = contract.bids.get(winner.id);
                check(bid != null, "That player has not bid on this contract.");
                validateBidDestination(bid, true);
                validateContractChunks(contract);
                check(bid.cents == 0 || !conflicted(actor.id().toString(), bid.bidder, bid.account),
                        "A paid bidder, company participant, or beneficiary cannot award their own contract.");
                e.requireEconomy(bid.cents);
                check(!e.economy.available() || e.economy.balance(escrow(contract)) == 0,
                        "The contract escrow already contains funds; operator audit is required.");
                e.pay(List.of(new EconomyAccess.Transfer(e.account(government), escrow(contract), bid.cents,
                        "Award government contract " + contract.id)));
                contract.winner = bid.bidder;
                contract.payeeAccount = bid.account;
                contract.escrowCents = bid.cents;
                contract.awardedBy = actor.id().toString();
                contract.status = "AWARDED";
                e.history("contract", contract.id, "Awarded by " + actor.id() + " to " + bid.bidder + "; escrow=" + bid.cents);
                e.mail(UUID.fromString(bid.bidder), "Contract awarded", contract.title + " [" + contract.id + "] was awarded to you. Submit work for independent review.");
                e.governmentMail(government.id, "Contract awarded", contract.id + " awarded to " + winner.name + " for " + Money.format(bid.cents) + ".");
                return "Contract awarded and payment escrowed.";
            }
            case "submit" -> {
                args.between(4, 64, "contract submit <contractId> <completionNote>");
                check("AWARDED".equals(contract.status), "Only awarded work may be submitted.");
                check(contractor(actor, contract), "Only the selected contractor or its company managers may submit work.");
                String note = GovernanceEngine.prose(args.tail(3), e.config.maxDescriptionLength, "Completion note");
                contract.completionNote = note;
                contract.submittedBy = actor.id().toString();
                contract.status = "SUBMITTED";
                e.history("contract", contract.id, "Work submitted by " + actor.id());
                e.governmentMail(contract.governmentId, "Contract work submitted", contract.title + " [" + contract.id + "] awaits independent completion approval.");
                return "Work submitted; an authorized government reviewer must approve completion.";
            }
            case "return" -> {
                args.between(4, 64, "contract return <contractId> <reason>");
                e.manage(actor, e.gov(contract.governmentId));
                check("SUBMITTED".equals(contract.status), "Only submitted work may be returned for correction.");
                check(contract.escrowCents == 0 || !conflicted(actor.id().toString(), contract.winner, contract.payeeAccount),
                        "A paid contractor cannot review their own work.");
                String reason = GovernanceEngine.prose(args.tail(3), e.config.maxDescriptionLength, "Return reason");
                contract.status = "AWARDED";
                e.history("contract", contract.id, "Returned for correction by " + actor.id() + ": " + reason);
                e.mail(UUID.fromString(contract.winner), "Contract corrections requested", contract.title + ": " + reason);
                return "Work returned for correction; escrow is unchanged.";
            }
            case "complete" -> {
                args.exactly(3, "contract complete <contractId>");
                Government government = e.gov(contract.governmentId);
                e.manage(actor, government);
                check("SUBMITTED".equals(contract.status), "Only submitted work may be approved for completion.");
                validateContractChunks(contract);
                validateAward(contract);
                check(contract.escrowCents == 0 || (!actor.id().toString().equals(contract.submittedBy)
                                && !conflicted(actor.id().toString(), contract.winner, contract.payeeAccount)),
                        "You cannot approve or pay your own paid contract, even as an operator.");
                long paid = contract.escrowCents;
                e.pay(List.of(new EconomyAccess.Transfer(escrow(contract), contract.payeeAccount, paid,
                        "Complete government contract " + contract.id)));
                contract.escrowCents = 0;
                contract.status = "COMPLETED";
                e.history("contract", contract.id, "Completed by " + actor.id() + "; paid " + paid);
                e.governmentMail(government.id, "Contract completed", contract.title + " [" + contract.id + "] paid " + Money.format(paid) + ".");
                return "Contract completed and escrow paid to the original authorized payee.";
            }
            case "cancel" -> {
                args.between(4, 64, "contract cancel <contractId> <reason>");
                Government government = e.gov(contract.governmentId);
                check(e.canManage(actor, government) || contractor(actor, contract), "Only the issuer or selected contractor may cancel.");
                check(ACTIVE_CONTRACTS.contains(contract.status), "This contract has already concluded.");
                String reason = GovernanceEngine.prose(args.tail(3), e.config.maxDescriptionLength, "Cancellation reason");
                long refund = Money.nonNegative(contract.escrowCents);
                if (refund > 0) validateAward(contract);
                e.pay(List.of(new EconomyAccess.Transfer(escrow(contract), e.account(government), refund,
                        "Cancel government contract " + contract.id)));
                contract.escrowCents = 0;
                contract.status = "CANCELLED";
                e.history("contract", contract.id, "Cancelled by " + actor.id() + "; refunded " + refund + ": " + reason);
                e.governmentMail(government.id, "Contract cancelled", contract.title + " refunded " + Money.format(refund) + ". " + reason);
                return "Contract cancelled; any escrow was refunded to the issuing government.";
            }
            default -> throw new UserError("Unknown contract action '" + action + "'. Use help contracts.");
        }
    }

    void validateBidDestination(Bid bid, boolean requireCurrentManager) {
        check(bid != null && GovernanceEngine.validUuid(bid.bidder), "Invalid contract bidder.");
        e.requirePlayer(UUID.fromString(bid.bidder));
        Money.nonNegative(bid.cents);
        if (("player:" + bid.bidder).equals(bid.account)) return;
        check(bid.account != null && bid.account.startsWith("company:"), "Contract payees must be the bidder or their authorized company.");
        Company company = e.companyRequired(bid.account.substring("company:".length()));
        check(("company:" + company.id).equals(bid.account), "Invalid company payment destination.");
        if (requireCurrentManager)
            check(e.mayManageCompany(UUID.fromString(bid.bidder), company.id), "The bidder no longer manages their nominated company.");
    }

    void validateAward(Contract contract) {
        Bid bid = contract.bids.get(contract.winner);
        validateBidDestination(bid, false);
        check(Objects.equals(contract.winner, bid.bidder) && Objects.equals(bid.account, contract.payeeAccount)
                        && bid.cents == contract.escrowCents,
                "Contract award and escrow do not match the accepted bid; operator audit required.");
    }

    private void validateContractChunks(Contract contract) {
        Government government = e.gov(contract.governmentId);
        Set<String> scope = e.subtree(government).stream().map(g -> g.id).collect(Collectors.toSet());
        Set<String> governmentAccounts = e.subtree(government).stream().map(e::account).collect(Collectors.toSet());
        check(!contract.chunks.isEmpty() && contract.chunks.size() <= e.config.maxContractChunks, "Invalid contract chunk selection.");
        check(new HashSet<>(contract.chunks).size() == contract.chunks.size(), "Contract chunk selection contains duplicates.");
        for (String key : contract.chunks) {
            Claim claim = e.requiredClaim(key);
            check(scope.contains(claim.cityId), "Contract chunks must lie within the issuing government's territory.");
            check(governmentAccounts.contains(claim.ownerAccount), "Contract chunks must be government-owned, not privately owned.");
            check(!e.economy.isClaimEncumbered(key), "A selected contract chunk is encumbered by the economy.");
            check(!e.politics.claimLocked(key, null), "A selected contract chunk is reserved in a treaty.");
            check(e.data.contracts.values().stream().filter(Objects::nonNull)
                            .noneMatch(c -> !contract.id.equals(c.id) && holdsContractObligations(c) && c.chunks.contains(key)),
                    "A selected chunk is already reserved in another active contract.");
        }
    }

    boolean contractor(Actor actor, Contract contract) {
        if (contract.winner == null) return false;
        return actor.id().toString().equals(contract.winner)
                || (contract.payeeAccount != null && contract.payeeAccount.startsWith("company:")
                && e.mayAccessAccount(actor.id(), contract.payeeAccount));
    }

    boolean conflicted(String player, String bidder, String account) {
        if (player.equals(bidder) || ("player:" + player).equals(account)) return true;
        if (account != null && account.startsWith("company:")) {
            Company company = e.data.companies.get(account.substring("company:".length()));
            return company != null && (company.members.contains(player) || company.shares.getOrDefault(player, 0L) > 0);
        }
        return false;
    }

    boolean claimLocked(String key) {
        return e.data.contracts.values().stream().filter(Objects::nonNull)
                .anyMatch(c -> holdsContractObligations(c) && c.chunks.contains(key));
    }

    boolean governmentLocked(String id) {
        return e.data.contracts.values().stream().filter(Objects::nonNull)
                .anyMatch(c -> id.equals(c.governmentId) && holdsContractObligations(c));
    }

    boolean companyLocked(String id) {
        String account = "company:" + id;
        return e.data.contracts.values().stream().filter(Objects::nonNull)
                .filter(this::holdsContractObligations).anyMatch(c -> account.equals(c.payeeAccount)
                        || (!("AWARDED".equals(c.status) || "SUBMITTED".equals(c.status))
                        && c.bids.values().stream()
                        .filter(Objects::nonNull).anyMatch(b -> account.equals(b.account))));
    }

    private boolean holdsContractObligations(Contract contract) {
        return contract.escrowCents != 0 || contract.status == null || !CLOSED_CONTRACTS.contains(contract.status);
    }

    private String escrow(Contract contract) {
        return "escrow:contract:" + contract.id;
    }

    private Contract contract(String id) {
        Contract contract = e.data.contracts.get(id);
        check(contract != null, "Unknown government contract ID.");
        return contract;
    }

    void tick(long now) {
        for (CompanyProposal proposal : new ArrayList<>(e.data.companyProposals.values())) {
            if (proposal == null || proposal.status == null || !GovernanceEngine.validUuid(proposal.id)
                    || e.data.companyProposals.get(proposal.id) != proposal || !ACTIVE_PROPOSALS.contains(proposal.status)) continue;
            try { advanceCompanyProposal(proposal, now); }
            catch (UserError error) {
                proposal.status = "FAILED";
                proposal.lastError = clip(error.getMessage());
                e.history("company-proposal", proposal.id, "Invalid proposal: " + proposal.lastError);
            }
        }
        for (Contract contract : e.data.contracts.values()) {
            if (contract == null || !GovernanceEngine.validUuid(contract.id) || e.data.contracts.get(contract.id) != contract) continue;
            if ("OPEN".equals(contract.status) && now >= contract.bidEndsAt) {
                contract.status = contract.bids.isEmpty() ? "EXPIRED" : "REVIEW";
                e.history("contract", contract.id, "Bidding concluded: " + contract.status);
            }
            if ("REVIEW".equals(contract.status) && now >= contract.reviewEndsAt) {
                contract.status = "EXPIRED";
                e.history("contract", contract.id, "Unawarded contract review window expired.");
            }
        }
        Map<String, Integer> retainedProposals = new HashMap<>();
        e.data.companyProposals.values().stream().filter(Objects::nonNull)
                .filter(p -> GovernanceEngine.validUuid(p.id) && p.status != null && CLOSED_PROPOSALS.contains(p.status))
                .sorted(Comparator.comparingLong((CompanyProposal p) -> p.createdAt).reversed().thenComparing(p -> p.id)).toList()
                .forEach(p -> {
                    if (retainedProposals.merge(p.companyId, 1, Integer::sum) > e.config.maxHistory)
                        e.data.companyProposals.remove(p.id);
                });
        Map<String, Integer> retainedContracts = new HashMap<>();
        e.data.contracts.values().stream().filter(Objects::nonNull)
                .filter(c -> c.escrowCents == 0 && GovernanceEngine.validUuid(c.id) && c.status != null
                        && CLOSED_CONTRACTS.contains(c.status))
                .sorted(Comparator.comparingLong((Contract c) -> c.createdAt).reversed().thenComparing(c -> c.id)).toList()
                .forEach(c -> {
                    if (retainedContracts.merge(c.governmentId, 1, Integer::sum) > e.config.maxHistory)
                        e.data.contracts.remove(c.id);
                });
    }

    private static String clip(String value) {
        return value == null ? "Action unavailable." : value.substring(0, Math.min(value.length(), 500));
    }
}
