package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceData.Bill;
import dev.statecraft.domain.GovernanceData.ChunkTerm;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.DiplomaticProposal;
import dev.statecraft.domain.GovernanceData.Election;
import dev.statecraft.domain.GovernanceData.Emergency;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.History;
import dev.statecraft.domain.GovernanceData.Law;
import dev.statecraft.domain.GovernanceData.Player;
import dev.statecraft.domain.GovernanceData.Relation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
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

final class Politics {
    private static final Set<String> LIVE_BILLS = Set.of("DEBATE", "VOTING", "PASSED", "VETOED", "OVERRIDE_VOTING");
    private static final Set<String> LIVE_PROPOSALS = Set.of("PROPOSED", "AWAITING_RATIFICATION", "READY");
    private static final Set<String> CLOSED_BILLS = Set.of("ENACTED", "FAILED", "CANCELLED", "EXPIRED");
    private static final Set<String> CLOSED_PROPOSALS = Set.of("ENACTED", "FAILED", "CANCELLED", "REJECTED", "EXPIRED", "SUPERSEDED");
    private final GovernanceEngine e;

    Politics(GovernanceEngine engine) {
        e = engine;
    }

    void schedule(Government nation) {
        Election election = new Election();
        election.nationId = nation.id;
        election.nextStartAt = deadline(e.now(), e.config.electionIntervalMillis);
        election.scheduleExhausted = election.nextStartAt <= e.now();
        e.data.elections.put(nation.id, election);
        e.changed();
    }

    String electionCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "status");
        if ("list".equals(action)) {
            args.between(2, 3, "election list [page]");
            List<String> rows = e.data.governments.values().stream().filter(e::viewableGovernment)
                    .filter(g -> g.kind == Kind.NATION).sorted(Comparator.comparing(g -> g.name)).map(g -> {
                        Election election = e.data.elections.get(g.id);
                        return g.name + " [" + g.id + "] " + (election == null ? "Not scheduled"
                                : (election.voting ? "VOTING until " + election.endsAt : "REGISTRATION until " + election.nextStartAt)
                                + ", candidates=" + election.candidates.size());
                    }).toList();
            return e.page("National elections", rows, args.page(2));
        }
        Government nation = args.size() > 2 ? nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
        Election election = e.data.elections.get(nation.id);
        if (election == null) {
            schedule(nation);
            election = e.data.elections.get(nation.id);
        }
        switch (action) {
            case "status" -> {
                args.between(1, 3, "election status [nation]");
                return nation.name + " [" + nation.id + "] election: " + (election.voting ? "VOTING" : "REGISTRATION")
                        + "\nNext start: " + election.nextStartAt + "\nVoting ends: " + election.endsAt
                        + "\nCandidates: " + election.candidates.size() + "\nBallots: " + election.votes.size()
                        + "\nSchedule exhausted: " + election.scheduleExhausted;
            }
            case "candidates" -> {
                args.between(3, 4, "election candidates <nation> [page]");
                return e.page("Candidates", election.candidates.stream().sorted()
                        .map(id -> e.playerName(id) + " [" + id + "]").toList(), args.page(3));
            }
            case "candidate", "register" -> {
                args.exactly(3, "election candidate <nation>");
                check(e.member(actor.id().toString(), nation), "Candidates must be current citizens.");
                check(!election.voting && !election.scheduleExhausted, "Candidate registration is closed.");
                check(!election.candidates.contains(actor.id().toString()), "You are already registered.");
                check(election.candidates.size() < e.config.maxMembersPerNation, "Candidate limit reached.");
                e.pay(List.of(candidateFee(actor, nation)));
                election.candidates.add(actor.id().toString());
                electionHistory(election, e.now(), "Candidate registered: " + actor.id());
                return "Candidate registered. Registration fees are non-refundable.";
            }
            case "withdraw" -> {
                args.exactly(3, "election withdraw <nation>");
                check(!election.voting, "Candidates cannot withdraw after voting opens.");
                check(election.candidates.remove(actor.id().toString()), "You are not registered.");
                electionHistory(election, e.now(), "Candidate withdrew: " + actor.id());
                return "Candidacy withdrawn.";
            }
            case "vote" -> {
                args.exactly(4, "election vote <nation> <candidate>");
                check(election.voting && e.now() < election.endsAt, "The election is not accepting votes.");
                String voter = actor.id().toString();
                check(election.electorate.contains(voter) && e.member(voter, nation),
                        "Only citizens eligible when voting opened may vote.");
                check(!election.votes.containsKey(voter), "You have already voted in this election.");
                Player candidate = e.resolvePlayer(args.get(3));
                check(election.candidates.contains(candidate.id) && e.member(candidate.id, nation),
                        "That player is not an eligible candidate.");
                election.votes.put(voter, candidate.id);
                e.changed();
                return "Your ballot has been recorded.";
            }
            case "history" -> {
                args.between(3, 4, "election history <nation> [page]");
                List<History> newest = new ArrayList<>(election.history);
                Collections.reverse(newest);
                return e.page("Election history", newest.stream().filter(Objects::nonNull).map(h -> h.at + ": " + h.text).toList(), args.page(3));
            }
            case "start" -> {
                args.exactly(3, "election start <nation>");
                e.admin(actor);
                check(!election.voting, "An election is already running.");
                openElection(nation, election, e.now());
                return "Election voting opened by operator.";
            }
            case "close" -> {
                args.exactly(3, "election close <nation>");
                e.admin(actor);
                check(election.voting, "No election is running.");
                finishElection(nation, election, e.now());
                return "Election closed by operator. See election history.";
            }
            case "cancel" -> {
                args.exactly(3, "election cancel <nation>");
                e.admin(actor);
                e.changed();
                election.voting = false;
                election.candidates.clear();
                election.electorate.clear();
                election.votes.clear();
                election.endsAt = 0;
                election.nextStartAt = deadline(e.now(), e.config.electionIntervalMillis);
                election.scheduleExhausted = election.nextStartAt <= e.now();
                electionHistory(election, e.now(), "Election cancelled by operator " + actor.id() + "; no fee refunds.");
                return "Election cancelled and rescheduled.";
            }
            default -> throw new UserError("Unknown election action '" + action + "'. Use help elections.");
        }
    }

    private void openElection(Government nation, Election election, long at) {
        e.changed();
        election.voting = true;
        election.startedAt = at;
        election.endsAt = deadline(at, e.config.electionVotingMillis);
        election.electorate = new LinkedHashSet<>(e.members(nation));
        election.candidates.removeIf(id -> !e.member(id, nation));
        election.votes.clear();
        electionHistory(election, at, "Voting opened with " + election.candidates.size()
                + " candidates and " + election.electorate.size() + " eligible voters.");
        notifyGovernment(nation.id, "Election voting", "Voting is open until " + election.endsAt + ".");
    }

    private void finishElection(Government nation, Election election, long now) {
        List<String> candidates = election.candidates.stream().filter(id -> e.member(id, nation)).sorted().toList();
        Map<String, Long> totals = new HashMap<>();
        candidates.forEach(id -> totals.put(id, 0L));
        election.votes.forEach((voter, candidate) -> {
            if (GovernanceEngine.validUuid(voter) && e.data.players.containsKey(voter)
                    && election.electorate.contains(voter) && totals.containsKey(candidate))
                totals.merge(candidate, 1L, Long::sum);
        });
        long ballots = totals.values().stream().mapToLong(Long::longValue).sum();
        String result;
        if (candidates.isEmpty()) result = "No eligible candidates; incumbent retained.";
        else if (ballots == 0) result = "No valid ballots; incumbent retained.";
        else {
            String winner = candidates.stream().min(Comparator.<String>comparingLong(totals::get).reversed()
                    .thenComparing(Comparator.naturalOrder())).orElseThrow();
            if (!winner.equals(nation.leader)) e.appointLeader(nation, winner);
            result = "Winner: " + e.playerName(winner) + " [" + winner + "], votes=" + totals.get(winner)
                    + "/" + ballots + ". Ties use ascending candidate UUID.";
        }
        electionHistory(election, Math.min(now, election.endsAt), result);
        notifyGovernment(nation.id, "Election results", result);
        election.voting = false;
        election.candidates.clear();
        election.electorate.clear();
        election.votes.clear();
        long next = deadline(election.startedAt, e.config.electionIntervalMillis);
        if (next <= now) {
            long remainder = (now - next) % e.config.electionIntervalMillis;
            next = deadline(now, e.config.electionIntervalMillis - remainder);
            electionHistory(election, now, "Missed empty cycles skipped; next registration closes at " + next + ".");
        }
        election.nextStartAt = next;
        election.scheduleExhausted = next <= now;
        election.endsAt = 0;
    }

    private void electionHistory(Election election, long at, String text) {
        e.appendHistory(election.history, at, "election", election.nationId, text);
        e.history("election", election.nationId, text);
    }

    EconomyAccess.Transfer candidateFee(Actor actor, Government nation) {
        return new EconomyAccess.Transfer(actor.account(), e.account(nation),
                e.config.electionCandidateFee, "Election candidate registration");
    }

    void membershipChanged(String playerId) {
        for (Election election : e.data.elections.values()) {
            if (election != null && !e.member(playerId, e.data.governments.get(election.nationId))
                    && election.candidates.remove(playerId)) e.changed();
        }
        // Cast ballots are retained: expelling a voter must not erase an already cast vote.
    }

    String billCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action)) {
            args.between(1, 4, "bill list [nation|all] [page]");
            Government nation = "all".equals(args.optional(2, "")) ? null
                    : args.size() > 2 ? nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
            List<String> rows = e.data.bills.values().stream().filter(Objects::nonNull)
                    .filter(b -> nation == null || nation.id.equals(b.nationId))
                    .sorted(Comparator.comparingLong((Bill b) -> b.createdAt).reversed()
                            .thenComparing(b -> b.id, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(b -> b.id + " " + b.status + " " + b.title + " [" + b.policy + "=" + b.value + "]"
                            + " nation=" + e.governmentName(b.nationId) + " [" + b.nationId + "]").toList();
            return e.page(nation == null ? "All national bills" : "Bills of " + nation.name, rows, args.page(3));
        }
        if ("propose".equals(action) || "amend".equals(action)) {
            args.between(7, 64, "bill " + action + " <nation> <policy|roleplay> <value|-> <title> <text>");
            Government nation = nation(args.get(2));
            check(actor.admin() || (e.member(actor.id().toString(), nation)
                            && (e.config.allowCitizenBills || legislator(actor.id().toString(), nation))),
                    "You are not eligible to introduce bills in this nation.");
            String policy = args.get(3);
            String value = billValue(policy, args.get(4));
            String title = GovernanceEngine.text(args.get(5), 100, "Bill title");
            String text = GovernanceEngine.prose(args.tail(6), e.config.maxDescriptionLength, "Bill text");
            billCapacity(nation.id);
            Bill bill = newBill(nation.id, actor.id().toString(), policy, value, title, text);
            bill.amendment = "amend".equals(action);
            e.data.bills.put(bill.id, bill);
            e.changed();
            billHistory(bill, "Introduced by " + actor.id() + (bill.amendment ? " as constitutional amendment" : ""));
            notifyGovernment(nation.id, "Bill introduced", title + " [" + bill.id + "]. Debate ends at " + bill.debateEndsAt + ".");
            return "Introduced bill " + bill.id + ". " + ("roleplay".equals(policy) ? "This law is roleplay-only." : "This policy is mechanically enforced after enactment.");
        }
        Bill bill = bill(args.get(2));
        Government nation = nation(bill.nationId);
        switch (action) {
            case "info", "status" -> {
                args.exactly(3, "bill info <billId>");
                return bill.id + ": " + bill.title + "\nStatus: " + bill.status + "\nNation: " + nation.name
                        + "\nPolicy: " + bill.policy + "=" + bill.value + "\nConstitutional amendment: " + bill.amendment
                        + "\nDebate ends: " + bill.debateEndsAt + "\nVote ends: " + bill.voteEndsAt
                        + "\nDecision deadline: " + bill.decisionEndsAt + "\n" + tallyText(bill)
                        + "\n" + bill.text + (bill.treatyId == null ? "" : "\nTreaty: " + bill.treatyId);
            }
            case "vote" -> {
                args.exactly(4, "bill vote <billId> <yes|no|abstain>");
                voteBill(actor, bill, args.get(3));
                return "Legislative vote recorded.";
            }
            case "votes" -> {
                args.between(3, 4, "bill votes <billId> [page]");
                return e.page("Legislative roll call", bill.votes.entrySet().stream()
                        .map(v -> e.playerName(v.getKey()) + ": " + v.getValue()).toList(), args.page(3));
            }
            case "sign" -> {
                args.exactly(3, "bill sign <billId>");
                e.executive(actor, nation);
                check("PASSED".equals(bill.status), "Only a passed bill awaiting signature can be signed.");
                check(bill.treatyId == null, "Treaty ratifications do not require an additional executive signature.");
                enact(bill, "Signed by " + actor.id());
                return "Bill enacted.";
            }
            case "veto" -> {
                args.between(4, 64, "bill veto <billId> <reason>");
                e.executive(actor, nation);
                check("PASSED".equals(bill.status), "Only a passed bill awaiting signature can be vetoed.");
                String reason = GovernanceEngine.prose(args.tail(3), e.config.maxDescriptionLength, "Veto reason");
                e.changed();
                bill.status = "VETOED";
                bill.decisionEndsAt = deadline(e.now(), e.config.signatureDurationMillis);
                billHistory(bill, "Vetoed by " + actor.id() + ": " + reason);
                notifyGovernment(nation.id, "Bill vetoed", bill.title + ". An override may be moved before " + bill.decisionEndsAt + ".");
                return "Bill vetoed; the legislature may move to override.";
            }
            case "override" -> {
                args.exactly(3, "bill override <billId>");
                check(legislator(actor.id().toString(), nation), "Only current legislators may move an override.");
                check("VETOED".equals(bill.status), "Only a vetoed bill can enter override voting.");
                e.changed();
                bill.status = "OVERRIDE_VOTING";
                bill.electorate = electorate(nation);
                bill.votes.clear();
                bill.voteEndsAt = deadline(e.now(), e.config.legislativeVotingMillis);
                billHistory(bill, "Veto override voting opened by " + actor.id());
                return "Override voting opened. A whole-electorate supermajority is required.";
            }
            case "revise" -> {
                args.between(6, 64, "bill revise <billId> <value> <title> <text>");
                check(actor.id().toString().equals(bill.author), "Only the author may revise their bill.");
                check("DEBATE".equals(bill.status) && bill.treatyId == null, "Only ordinary bills in debate may be revised.");
                String value = billValue(bill.policy, args.get(3));
                String title = GovernanceEngine.text(args.get(4), 100, "Bill title");
                String text = GovernanceEngine.prose(args.tail(5), e.config.maxDescriptionLength, "Bill text");
                e.changed();
                bill.value = value;
                bill.title = title;
                bill.text = text;
                bill.debateEndsAt = deadline(e.now(), e.config.debateDurationMillis);
                billHistory(bill, "Revised; full debate period restarted.");
                return "Bill revised; debate restarted.";
            }
            case "cancel" -> {
                args.exactly(3, "bill cancel <billId>");
                check(LIVE_BILLS.contains(bill.status), "This bill has already concluded.");
                check(actor.admin() || ("DEBATE".equals(bill.status) && actor.id().toString().equals(bill.author)
                                && bill.treatyId == null),
                        "Only the author during debate, or an operator, may cancel a bill.");
                e.changed();
                bill.status = "CANCELLED";
                billHistory(bill, "Cancelled by " + actor.id());
                if (bill.treatyId != null) failTreaty(bill.treatyId, "A ratification bill was cancelled.");
                return "Bill cancelled.";
            }
            case "history" -> {
                args.between(3, 4, "bill history <billId> [page]");
                List<History> newest = new ArrayList<>(bill.history);
                Collections.reverse(newest);
                return e.page("Bill history", newest.stream().filter(Objects::nonNull).map(h -> h.at + " " + h.text).toList(), args.page(3));
            }
            default -> throw new UserError("Unknown bill action '" + action + "'. Use help legislature.");
        }
    }

    String lawCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "list");
        if ("list".equals(action) && "all".equals(args.optional(2, ""))) {
            args.between(3, 4, "law list all [page]");
            List<String> rows = new ArrayList<>();
            e.data.laws.forEach((nationId, codex) -> {
                List<Law> newest = new ArrayList<>(codex);
                Collections.reverse(newest);
                newest.stream().filter(Objects::nonNull).forEach(law ->
                        rows.add(law.id + " " + law.title + " nation=" + e.governmentName(nationId) + " [" + nationId + "]"
                                + (law.roleplayOnly ? " [ROLEPLAY ONLY]" : " [" + law.policy + "=" + law.value + "]")
                                + (law.amendment ? " [AMENDMENT]" : "")));
            });
            return e.page("All national codices", rows, args.page(3));
        }
        Government nation = args.size() > 2 ? nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
        List<Law> laws = e.data.laws.getOrDefault(nation.id, List.of());
        switch (action) {
            case "list" -> {
                args.between(1, 4, "law list [nation|all] [page]");
                List<Law> newest = new ArrayList<>(laws);
                Collections.reverse(newest);
                return e.page(nation.name + " codex", newest.stream().filter(Objects::nonNull).map(l -> l.id + " " + l.title
                        + " " + (l.roleplayOnly ? "[ROLEPLAY ONLY]" : "[" + l.policy + "=" + l.value + "]")
                        + (l.amendment ? " [AMENDMENT]" : "")).toList(), args.page(3));
            }
            case "read" -> {
                args.exactly(4, "law read <nation> <lawId>");
                Law law = laws.stream().filter(Objects::nonNull).filter(l -> args.get(3).equals(l.id)).findFirst()
                        .orElseThrow(() -> new UserError("That law is not in this nation's retained codex."));
                return law.title + "\nEnacted: " + law.enactedAt + "\n" + law.policy + "=" + law.value
                        + "\n" + (law.roleplayOnly ? "Roleplay only; no mechanical effects." : "Mechanically enforced policy.")
                        + "\n" + law.text;
            }
            default -> throw new UserError("Unknown law action. Use law list or law read.");
        }
    }

    String executiveCommand(Actor actor, Arguments args) {
        String action = args.get(1);
        Government nation = nation(args.get(2));
        if ("status".equals(action)) {
            args.exactly(3, "executive status <nation>");
            Emergency order = e.data.emergencies.get(nation.id);
            return order == null ? "No emergency order. Last use: " + nation.lastEmergencyAt
                    : "Emergency " + order.policy + "=" + order.value + " expires=" + order.expiresAt + "\n" + order.reason;
        }
        e.executive(actor, nation);
        switch (action) {
            case "emergency" -> {
                args.between(6, 64, "executive emergency <nation> <policy> <value> <reason>");
                String value = GovernanceSettings.validate(args.get(3), args.get(4));
                String reason = GovernanceEngine.prose(args.tail(5), e.config.maxDescriptionLength, "Emergency reason");
                check(!e.data.emergencies.containsKey(nation.id), "Rescind or await expiry of the existing emergency order.");
                check(nation.lastEmergencyAt < 0 || (e.now() >= nation.lastEmergencyAt
                                && e.now() - nation.lastEmergencyAt >= e.config.emergencyCooldownMillis),
                        "Emergency executive power is on cooldown.");
                long expires = deadline(e.now(), e.config.emergencyDurationMillis);
                check(expires > e.now(), "The world clock cannot schedule another emergency.");
                Emergency order = new Emergency();
                order.nationId = nation.id;
                order.policy = args.get(3);
                order.value = value;
                order.reason = reason;
                order.author = actor.id().toString();
                order.expiresAt = expires;
                e.changed();
                nation.lastEmergencyAt = e.now();
                e.data.emergencies.put(nation.id, order);
                e.history("emergency", nation.id, order.policy + "=" + value + " by " + actor.id() + ": " + reason);
                notifyGovernment(nation.id, "Emergency order", order.policy + "=" + value + " until " + expires + ". " + reason);
                return "Temporary emergency policy applied. Permanent law/settings remain intact underneath it.";
            }
            case "rescind" -> {
                args.exactly(3, "executive rescind <nation>");
                boolean existed = e.data.emergencies.containsKey(nation.id);
                Emergency removed = e.data.emergencies.remove(nation.id);
                if (existed) e.changed();
                check(removed != null, "No emergency order is active.");
                e.history("emergency", nation.id, "Rescinded by " + actor.id());
                return "Emergency order rescinded; cooldown remains in force.";
            }
            default -> throw new UserError("Unknown executive action. Use status, emergency, or rescind.");
        }
    }

    private void voteBill(Actor actor, Bill bill, String choice) {
        check(Set.of("yes", "no", "abstain").contains(choice), "Vote yes, no, or abstain.");
        check(Set.of("VOTING", "OVERRIDE_VOTING").contains(bill.status) && e.now() < bill.voteEndsAt,
                "This bill is not currently accepting votes.");
        String voter = actor.id().toString();
        Government nation = nation(bill.nationId);
        check(bill.electorate.contains(voter) && legislator(voter, nation),
                "Only current legislators on this ballot's eligibility roll may vote.");
        check(!bill.votes.containsKey(voter), "You already voted on this ballot.");
        bill.votes.put(voter, choice);
        e.changed();
    }

    private Bill newBill(String nation, String author, String policy, String value, String title, String text) {
        Bill bill = new Bill();
        bill.id = GovernanceEngine.newId();
        bill.nationId = nation;
        bill.author = author;
        bill.policy = policy;
        bill.value = value;
        bill.title = title;
        bill.text = text;
        bill.createdAt = e.now();
        bill.debateEndsAt = deadline(bill.createdAt, e.config.debateDurationMillis);
        return bill;
    }

    String billValue(String policy, String value) {
        if ("roleplay".equals(policy)) {
            check("-".equals(value), "Roleplay bills use '-' as their value.");
            return "-";
        }
        return GovernanceSettings.validate(policy, value);
    }

    void billCapacity(String nation) {
        check(e.data.bills.values().stream().filter(Objects::nonNull)
                        .filter(b -> nation.equals(b.nationId) && LIVE_BILLS.contains(b.status)).count()
                        < e.config.maxActiveBillsPerNation, "This nation has too many active bills.");
    }

    private Set<String> electorate(Government nation) {
        return e.members(nation).stream().filter(id -> legislator(id, nation))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    boolean legislator(String player, Government nation) {
        return e.member(player, nation) && (player.equals(nation.leader) || nation.officers.contains(player)
                || e.flag(nation, "citizenLegislature"));
    }

    private long votes(Bill bill, String choice) {
        return bill.votes.entrySet().stream().filter(v -> bill.electorate.contains(v.getKey()) && choice.equals(v.getValue())).count();
    }

    private String tallyText(Bill bill) {
        return "Eligible=" + bill.electorate.size() + " yes=" + votes(bill, "yes") + " no=" + votes(bill, "no")
                + " abstain=" + votes(bill, "abstain");
    }

    private void advanceBill(Bill bill, long now) {
        Government nation = e.data.governments.get(bill.nationId);
        if (!e.validHierarchy(nation) || nation.kind != Kind.NATION) return;
        if (bill.treatyId != null) {
            DiplomaticProposal treaty = e.data.diplomacy.get(bill.treatyId);
            check("treaty".equals(bill.policy) && Objects.equals(bill.value, bill.treatyId)
                            && treaty != null && (bill.nationId.equals(treaty.fromNation) || bill.nationId.equals(treaty.toNation)),
                    "This ratification bill has invalid treaty references.");
        } else billValue(bill.policy, bill.value);
        if ("DEBATE".equals(bill.status) && now >= bill.debateEndsAt) {
            e.changed();
            bill.electorate = electorate(nation);
            bill.status = "VOTING";
            bill.voteEndsAt = deadline(bill.debateEndsAt, e.config.legislativeVotingMillis);
            billHistory(bill, "Voting opened; electorate=" + bill.electorate.size());
        }
        if (Set.of("VOTING", "OVERRIDE_VOTING").contains(bill.status) && now >= bill.voteEndsAt) {
            boolean override = "OVERRIDE_VOTING".equals(bill.status);
            long yes = votes(bill, "yes");
            long no = votes(bill, "no");
            long cast = yes + no + votes(bill, "abstain");
            boolean quorum = bill.electorate.size() > 0 && reaches(cast, bill.electorate.size(), e.config.legislativeQuorumBps);
            boolean majority = override ? reaches(yes, bill.electorate.size(), e.config.overrideThresholdBps)
                    : bill.amendment ? reaches(yes, bill.electorate.size(), e.config.amendmentThresholdBps) : yes > no;
            billHistory(bill, "Ballot closed: " + tallyText(bill) + ", quorum=" + quorum);
            if (!quorum || !majority) {
                bill.status = "FAILED";
                billHistory(bill, override ? "Veto sustained." : "Bill failed.");
                if (bill.treatyId != null) failTreaty(bill.treatyId, "Ratification failed in " + nation.name + ".");
            } else if (bill.treatyId != null) {
                bill.status = "ENACTED";
                billHistory(bill, "Legislature ratified treaty " + bill.treatyId);
                ratificationPassed(bill);
            } else if (override) {
                enact(bill, "Veto overridden by legislative supermajority.");
            } else {
                bill.status = "PASSED";
                bill.decisionEndsAt = deadline(bill.voteEndsAt, e.config.signatureDurationMillis);
                billHistory(bill, "Passed; awaiting executive signature or veto.");
                notifyGovernment(nation.id, "Bill passed", bill.title + " [" + bill.id + "] awaits signature before " + bill.decisionEndsAt + ".");
            }
        }
        if (Set.of("PASSED", "VETOED").contains(bill.status) && now >= bill.decisionEndsAt) {
            bill.status = "EXPIRED";
            billHistory(bill, "Executive/override decision window expired without enactment.");
        }
    }

    private void enact(Bill bill, String reason) {
        Government nation = nation(bill.nationId);
        if (!"roleplay".equals(bill.policy)) GovernanceSettings.validate(bill.policy, bill.value);
        Law law = new Law();
        law.id = bill.id;
        law.title = bill.title;
        law.text = bill.text;
        law.policy = bill.policy;
        law.value = bill.value;
        law.amendment = bill.amendment;
        law.roleplayOnly = "roleplay".equals(bill.policy);
        law.enactedAt = e.now();
        if (!law.roleplayOnly) e.setPolicy(nation, bill.policy, bill.value);
        e.changed();
        List<Law> laws = e.data.laws.computeIfAbsent(nation.id, ignored -> new ArrayList<>());
        laws.add(law);
        e.trim(laws, e.config.maxLawsPerNation);
        bill.status = "ENACTED";
        billHistory(bill, "Enacted: " + reason);
        notifyGovernment(nation.id, "Law enacted", bill.title + (law.roleplayOnly ? " (roleplay only)." : " (" + bill.policy + "=" + bill.value + ")."));
    }

    private void billHistory(Bill bill, String text) {
        e.appendHistory(bill.history, e.now(), "bill", bill.id, text);
        e.history("bill", bill.id, text);
    }

    private Bill bill(String id) {
        Bill bill = e.data.bills.get(id);
        check(bill != null, "Unknown bill ID.");
        return bill;
    }

    String diplomacyCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "status");
        switch (action) {
            case "status", "proposals" -> {
                args.between(1, 4, "diplomacy " + action + " [nation|all] [page]");
                Government nation = "all".equals(args.optional(2, "")) ? null
                        : args.size() > 2 ? nation(args.get(2)) : e.ownGovernment(actor, Kind.NATION);
                List<String> rows = new ArrayList<>();
                if ("status".equals(action)) {
                    for (Relation relation : e.data.relations.values()) {
                        if (relation != null && (nation == null || nation.id.equals(relation.first) || nation.id.equals(relation.second)))
                            rows.add(e.governmentName(relation.first) + " [" + relation.first + "] <-> "
                                    + e.governmentName(relation.second) + " [" + relation.second + "]"
                                    + ": " + relation.status + ", truceUntil=" + relation.truceUntil);
                    }
                } else {
                    e.data.diplomacy.values().stream().filter(Objects::nonNull)
                            .filter(p -> nation == null || nation.id.equals(p.fromNation) || nation.id.equals(p.toNation))
                            .sorted(Comparator.comparingLong((DiplomaticProposal p) -> p.createdAt).reversed()
                                    .thenComparing(p -> p.id, Comparator.nullsFirst(Comparator.naturalOrder())))
                            .forEach(p -> rows.add(p.id + " " + p.type + " " + p.status + " "
                                    + e.governmentName(p.fromNation) + " [" + p.fromNation + "] -> "
                                    + e.governmentName(p.toNation) + " [" + p.toNation + "]"
                                    + " expires=" + p.expiresAt));
                }
                return e.page(nation == null ? "Global diplomacy" : nation.name + " diplomacy", rows, args.page(3));
            }
            case "war" -> {
                args.between(4, 64, "diplomacy war <fromNation> <toNation> [reason]");
                Government from = nation(args.get(2));
                Government to = nation(args.get(3));
                e.executive(actor, from);
                different(from, to);
                String reason = args.size() > 4 ? GovernanceEngine.prose(args.tail(4), e.config.maxDescriptionLength, "War reason") : "War declared.";
                check(!"WAR".equals(relationStatus(from.id, to.id)), "These nations are already at war.");
                check(!"ALLIED".equals(relationStatus(from.id, to.id)), "Break the alliance before declaring war.");
                check(!truce(from.id, to.id, e.now()), "A binding post-war truce is still in effect.");
                Relation relation = relation(from.id, to.id);
                e.changed();
                relation.status = "WAR";
                relation.warStartedAt = e.now();
                closeAllianceProposals(from.id, to.id);
                notifyPair(from.id, to.id, "War declared", from.name + " declared war on " + to.name + ". " + reason);
                e.history("diplomacy", pair(from.id, to.id), "War declared by " + actor.id() + ": " + reason);
                return "War declared.";
            }
            case "break" -> {
                args.exactly(4, "diplomacy break <fromNation> <ally>");
                Government from = nation(args.get(2));
                Government to = nation(args.get(3));
                e.executive(actor, from);
                different(from, to);
                check("ALLIED".equals(relationStatus(from.id, to.id)), "These nations are not allied.");
                e.changed();
                relation(from.id, to.id).status = "NEUTRAL";
                notifyPair(from.id, to.id, "Alliance ended", from.name + " ended the alliance with " + to.name + ".");
                e.history("diplomacy", pair(from.id, to.id), "Alliance ended by " + actor.id());
                return "Alliance ended. Any existing truce remains binding.";
            }
            case "alliance" -> {
                args.between(4, 64, "diplomacy alliance <fromNation> <toNation> [message]");
                Government from = nation(args.get(2));
                Government to = nation(args.get(3));
                e.executive(actor, from);
                different(from, to);
                check("NEUTRAL".equals(relationStatus(from.id, to.id)), "Alliance proposals require neutral relations; settle any war first.");
                proposalCapacity(from.id, to.id);
                noDuplicateProposal(from.id, to.id, "ALLIANCE");
                String message = args.size() > 4 ? GovernanceEngine.prose(args.tail(4), e.config.maxDescriptionLength, "Diplomatic message") : "Alliance proposed.";
                DiplomaticProposal proposal = proposal("ALLIANCE", from, to, message);
                proposal.expiresAt = deadline(e.now(), e.config.allianceProposalDurationMillis);
                e.data.diplomacy.put(proposal.id, proposal);
                e.changed();
                notifyPair(from.id, to.id, "Alliance proposal", from.name + " proposes an alliance. Proposal " + proposal.id + ". " + message);
                e.history("diplomacy", proposal.id, "Alliance proposed by " + actor.id());
                return "Alliance proposal " + proposal.id + ".";
            }
            case "peace", "truce" -> {
                args.between("truce".equals(action) ? 4 : 7, "truce".equals(action) ? 4 : 64,
                        "truce".equals(action) ? "diplomacy truce <fromNation> <toNation>"
                                : "diplomacy peace <fromNation> <toNation> <offerAmount> <demandAmount> <chunk=destinationCity,...|-> [message]");
                Government from = nation(args.get(2));
                Government to = nation(args.get(3));
                e.executive(actor, from);
                different(from, to);
                check("WAR".equals(relationStatus(from.id, to.id)), "Peace proposals require an active war.");
                proposalCapacity(from.id, to.id);
                noDuplicateProposal(from.id, to.id, "PEACE");
                String message = args.size() > 7 ? GovernanceEngine.prose(args.tail(7), e.config.maxDescriptionLength, "Treaty message") : "Peace and a binding truce proposed.";
                DiplomaticProposal proposal = proposal("PEACE", from, to, message);
                proposal.expiresAt = deadline(e.now(), e.config.peaceProposalDurationMillis);
                if ("peace".equals(action)) {
                    proposal.offeredCents = Money.parse(args.get(4));
                    proposal.demandedCents = Money.parse(args.get(5));
                    e.requireEconomy(proposal.offeredCents);
                    e.requireEconomy(proposal.demandedCents);
                    parseTerms(proposal, args.get(6));
                }
                validateTreaty(proposal);
                e.data.diplomacy.put(proposal.id, proposal);
                e.changed();
                notifyPair(from.id, to.id, "Peace proposal", from.name + " proposes peace: " + proposal.id
                        + ". Offered " + Money.format(proposal.offeredCents) + ", demanded " + Money.format(proposal.demandedCents)
                        + ", chunks=" + proposal.chunks.size() + ". " + proposal.message);
                e.history("diplomacy", proposal.id, "Peace proposed by " + actor.id());
                return "Peace proposal " + proposal.id + ". Its selected chunks are reserved until conclusion or expiry.";
            }
            case "terms", "info" -> {
                args.between(3, 4, "diplomacy terms <proposalId> [page]");
                DiplomaticProposal proposal = diplomaticProposal(args.get(2));
                List<String> rows = new ArrayList<>();
                rows.add(proposal.id + " " + proposal.type + " " + proposal.status + " expires=" + proposal.expiresAt);
                rows.add(e.governmentName(proposal.fromNation) + " pays " + Money.format(proposal.offeredCents)
                        + "; " + e.governmentName(proposal.toNation) + " pays " + Money.format(proposal.demandedCents));
                rows.add("Ratified: " + proposal.ratified.stream().map(e::governmentName).collect(Collectors.joining(", ")));
                rows.add(proposal.message);
                if (!proposal.lastError.isEmpty()) rows.add("Execution blocked: " + proposal.lastError);
                for (ChunkTerm term : proposal.chunks)
                    rows.add(term == null ? "Invalid chunk term: operator repair required."
                            : term.key + ": " + e.governmentName(term.fromNation) + " -> " + e.governmentName(term.toNation)
                            + " (state=" + e.governmentName(term.toState) + ", city=" + e.governmentName(term.toCity) + ")");
                e.data.bills.values().stream().filter(Objects::nonNull).filter(b -> proposal.id.equals(b.treatyId))
                        .forEach(b -> rows.add("Ratification bill: " + e.governmentName(b.nationId) + " " + b.id + " " + b.status));
                return e.page("Diplomatic terms", rows, args.page(3));
            }
            case "accept" -> {
                args.exactly(3, "diplomacy accept <proposalId>");
                DiplomaticProposal proposal = diplomaticProposal(args.get(2));
                e.executive(actor, nation(proposal.toNation));
                check("PROPOSED".equals(proposal.status) && e.now() < proposal.expiresAt, "This proposal is no longer awaiting acceptance.");
                validateTreatyReferences(proposal);
                if ("ALLIANCE".equals(proposal.type)) {
                    check("NEUTRAL".equals(relationStatus(proposal.fromNation, proposal.toNation)), "Relations changed; this alliance cannot be accepted.");
                    e.changed();
                    relation(proposal.fromNation, proposal.toNation).status = "ALLIED";
                    proposal.status = "ENACTED";
                    closeAllianceProposals(proposal.fromNation, proposal.toNation);
                    notifyPair(proposal.fromNation, proposal.toNation, "Alliance formed", "Alliance proposal " + proposal.id + " was accepted.");
                    e.history("diplomacy", proposal.id, "Alliance accepted by " + actor.id());
                    return "Alliance formed.";
                }
                check("PEACE".equals(proposal.type), "Unknown diplomatic proposal type.");
                validateTreaty(proposal);
                if (requiresRatification(proposal)) {
                    billCapacity(proposal.fromNation);
                    billCapacity(proposal.toNation);
                    List<Bill> bills = new ArrayList<>();
                    for (String nationId : List.of(proposal.fromNation, proposal.toNation)) {
                        Bill bill = newBill(nationId, actor.id().toString(), "treaty", proposal.id,
                                "Ratify peace " + proposal.id.substring(0, 8), "Ratify the immutable terms of treaty " + proposal.id + ".");
                        bill.treatyId = proposal.id;
                        bills.add(bill);
                    }
                    e.changed();
                    proposal.status = "AWAITING_RATIFICATION";
                    bills.forEach(b -> { e.data.bills.put(b.id, b); billHistory(b, "Peace ratification introduced."); });
                    notifyPair(proposal.fromNation, proposal.toNation, "Peace awaits ratification",
                            "Proposal " + proposal.id + " was accepted by the executives. Both legislatures must now ratify before expiry.");
                    return "Accepted pending both legislatures' ratification. Use diplomacy terms for bill IDs.";
                }
                executeTreaty(proposal);
                return "Peace enacted and treaty terms settled atomically; truce is binding.";
            }
            case "ratify" -> {
                args.exactly(4, "diplomacy ratify <proposalId> <yes|no|abstain>");
                DiplomaticProposal proposal = diplomaticProposal(args.get(2));
                String nationId = e.nationOf(actor.id()).orElseThrow(() -> new UserError("You are not a citizen."));
                Bill bill = e.data.bills.values().stream().filter(Objects::nonNull)
                        .filter(b -> proposal.id.equals(b.treatyId) && nationId.equals(b.nationId) && "VOTING".equals(b.status))
                        .findFirst().orElseThrow(() -> new UserError("Your legislature has no open ratification ballot for this treaty."));
                voteBill(actor, bill, args.get(3));
                return "Your legislature's ratification vote was recorded.";
            }
            case "execute" -> {
                args.exactly(3, "diplomacy execute <proposalId>");
                DiplomaticProposal proposal = diplomaticProposal(args.get(2));
                diplomaticExecutive(actor, proposal);
                check("READY".equals(proposal.status), "This treaty is not ready for a settlement retry.");
                executeTreaty(proposal);
                return "Treaty settled; peace and truce are in effect.";
            }
            case "reject", "cancel" -> {
                args.exactly(3, "diplomacy " + action + " <proposalId>");
                DiplomaticProposal proposal = diplomaticProposal(args.get(2));
                e.executive(actor, nation("cancel".equals(action) ? proposal.fromNation : proposal.toNation));
                check(LIVE_PROPOSALS.contains(proposal.status), "This proposal has already concluded.");
                finishProposal(proposal, "cancel".equals(action) ? "CANCELLED" : "REJECTED", "By " + actor.id());
                return "Diplomatic proposal " + proposal.status.toLowerCase(java.util.Locale.ROOT) + ".";
            }
            default -> throw new UserError("Unknown diplomacy action '" + action + "'. Use help diplomacy.");
        }
    }

    private DiplomaticProposal proposal(String type, Government from, Government to, String message) {
        DiplomaticProposal proposal = new DiplomaticProposal();
        proposal.id = GovernanceEngine.newId();
        proposal.type = type;
        proposal.fromNation = from.id;
        proposal.toNation = to.id;
        proposal.message = message;
        proposal.createdAt = e.now();
        return proposal;
    }

    void parseTerms(DiplomaticProposal proposal, String text) {
        if ("-".equals(text)) return;
        Set<String> seen = new HashSet<>();
        String[] terms = text.split(",", -1);
        check(terms.length <= e.config.maxTreatyChunks, "Too many chunks in this treaty.");
        for (String value : terms) {
            String[] parts = value.split("=", -1);
            check(parts.length == 2, "Chunk terms use chunkKey=destinationNationOrAllocation, separated by commas.");
            String key = ChunkKey.parse(parts[0]).toString();
            check(seen.add(key), "A chunk cannot appear twice in a treaty.");
            Claim claim = e.requiredClaim(key);
            check(e.viewableClaim(claim), "Repair the selected claim before negotiating its national ownership.");
            Government destination = e.gov(parts[1]);
            ChunkTerm term = new ChunkTerm();
            term.key = key;
            term.fromNation = claim.nationId;
            term.fromState = claim.stateId;
            term.fromCity = claim.cityId;
            term.toNation = e.nationId(destination);
            term.toState = destination.kind == Kind.CITY ? destination.parentId
                    : destination.kind == Kind.STATE ? destination.id : null;
            term.toCity = destination.kind == Kind.CITY ? destination.id : null;
            proposal.chunks.add(term);
        }
    }

    void validateTreatyReferences(DiplomaticProposal proposal) {
        String repair = "Treaty " + proposal.id + " requires operator repair: ";
        Government from = e.data.governments.get(proposal.fromNation);
        Government to = e.data.governments.get(proposal.toNation);
        check(e.validHierarchy(from) && from.kind == Kind.NATION, repair + "fromNation must reference a valid nation.");
        check(e.validHierarchy(to) && to.kind == Kind.NATION, repair + "toNation must reference a valid nation.");
        check(!from.id.equals(to.id), repair + "signatories must be different nations.");
        check(proposal.ratified != null, repair + "ratified nation set is missing.");
        check(proposal.chunks != null, repair + "chunk terms are missing.");
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < proposal.chunks.size(); index++) {
            ChunkTerm term = proposal.chunks.get(index);
            String field = repair + "chunk term " + (index + 1) + " ";
            check(term != null, field + "is null.");
            check(term.key != null, field + "key is missing.");
            try {
                check(term.key.equals(ChunkKey.parse(term.key).toString()), field + "key is noncanonical.");
            } catch (UserError error) {
                throw new UserError(field + "key must be canonical dimension|x|z coordinates: " + term.key);
            }
            check(seen.add(term.key), field + "duplicates chunk " + term.key + ".");
            validateTermScope(term.fromNation, term.fromState, term.fromCity, field + "from");
            validateTermScope(term.toNation, term.toState, term.toCity, field + "to");
            String donorNation = term.fromNation;
            String receivingNation = term.toNation;
            check((from.id.equals(donorNation) && to.id.equals(receivingNation))
                            || (to.id.equals(donorNation) && from.id.equals(receivingNation)),
                    field + "national ownership must transfer between opposite signatories.");
            Claim claim = e.data.claims.get(term.key);
            check(e.viewableClaim(claim), field + "references a missing or invalid claimed chunk.");
            check(Objects.equals(term.fromNation, claim.nationId) && Objects.equals(term.fromState, claim.stateId)
                            && Objects.equals(term.fromCity, claim.cityId),
                    field + "source national ownership/state/city snapshot no longer matches the claim.");
        }
    }

    private void validateTermScope(String nationId, String stateId, String cityId, String field) {
        Government nation = e.data.governments.get(nationId);
        check(e.validHierarchy(nation) && nation.kind == Kind.NATION, field + "Nation must reference a valid nation.");
        if (stateId == null) {
            check(cityId == null, field + "City requires an explicit state allocation.");
            return;
        }
        Government state = e.data.governments.get(stateId);
        check(e.validHierarchy(state) && state.kind == Kind.STATE && nationId.equals(state.parentId),
                field + "State must belong to the selected nation.");
        if (cityId == null) return;
        Government city = e.data.governments.get(cityId);
        check(e.validHierarchy(city) && city.kind == Kind.CITY && stateId.equals(city.parentId),
                field + "City must belong to the selected state.");
    }

    void validateTreaty(DiplomaticProposal proposal) {
        validateTreatyReferences(proposal);
        Government from = nation(proposal.fromNation);
        Government to = nation(proposal.toNation);
        different(from, to);
        check("WAR".equals(relationStatus(from.id, to.id)), "The nations are no longer at war.");
        check(e.now() < proposal.expiresAt, "This peace proposal has expired.");
        e.requireEconomy(proposal.offeredCents);
        e.requireEconomy(proposal.demandedCents);
        check(proposal.chunks.size() <= e.config.maxTreatyChunks, "Treaty chunk limit exceeded.");
        Map<String, Set<String>> after = new LinkedHashMap<>();
        Map<String, Set<String>> incoming = new LinkedHashMap<>();
        Map<String, Set<String>> dimensions = new LinkedHashMap<>();
        List<Territory.Allocation> allocations = new ArrayList<>();
        for (ChunkTerm term : proposal.chunks) {
            Claim claim = e.requiredClaim(term.key);
            check(Objects.equals(term.fromNation, claim.nationId) && Objects.equals(term.fromState, claim.stateId)
                    && Objects.equals(term.fromCity, claim.cityId), "A treaty chunk has changed national ownership or allocation.");
            check(e.publicTitle(claim), "Treaties cannot confiscate privately owned property.");
            Government owner = e.gov(claim.ownerAccount.split(":", 2)[1]);
            check(term.fromNation.equals(e.nationId(owner)), "A treaty cannot transfer another nation's public property.");
            e.assertClaimFree(term.key, proposal.id);
            after.computeIfAbsent(term.fromNation, e::nationClaims).remove(term.key);
            after.computeIfAbsent(term.toNation, e::nationClaims).add(term.key);
            incoming.computeIfAbsent(term.toNation, ignored -> new HashSet<>()).add(term.key);
            String dimension = ChunkKey.parse(term.key).dimension();
            dimensions.computeIfAbsent(term.fromNation, ignored -> new HashSet<>()).add(dimension);
            dimensions.computeIfAbsent(term.toNation, ignored -> new HashSet<>()).add(dimension);
            allocations.add(e.territory.allocation(claim, term.toNation, term.toState, term.toCity));
        }
        e.territory.validateCapacity(allocations);
        for (Map.Entry<String, Set<String>> nation : after.entrySet()) {
            if (e.config.requireConnectedClaims) {
                Set<String> affected = nation.getValue().stream()
                        .filter(key -> dimensions.get(nation.getKey()).contains(ChunkKey.parse(key).dimension()))
                        .collect(Collectors.toSet());
                check(e.connected(affected), "Treaty would create disconnected national territory in " + e.governmentName(nation.getKey()) + ".");
            }
            if (e.config.requireAdjacentClaims && incoming.containsKey(nation.getKey())) {
                Set<String> base = new HashSet<>(nation.getValue());
                base.removeAll(incoming.get(nation.getKey()));
                e.territory.validateAdditions(base, incoming.get(nation.getKey()));
            }
        }
    }

    private void executeTreaty(DiplomaticProposal proposal) {
        validateTreaty(proposal);
        if ("READY".equals(proposal.status) || "AWAITING_RATIFICATION".equals(proposal.status))
            check(proposal.ratified.containsAll(List.of(proposal.fromNation, proposal.toNation)), "Both ratifications are required.");
        List<Territory.Allocation> allocations = proposal.chunks.stream()
                .map(term -> e.territory.allocation(e.data.claims.get(term.key), term.toNation, term.toState, term.toCity)).toList();
        e.pay(treatyTransfers(proposal));
        // No validation or external payment remains after this point.
        e.changed();
        allocations.forEach(e.territory::apply);
        Relation relation = relation(proposal.fromNation, proposal.toNation);
        relation.status = "NEUTRAL";
        relation.truceUntil = deadline(e.now(), e.config.truceDurationMillis);
        proposal.status = "ENACTED";
        proposal.lastError = "";
        e.history("diplomacy", proposal.id, "Peace enacted; offered=" + proposal.offeredCents
                + ", demanded=" + proposal.demandedCents + ", chunks=" + proposal.chunks.size());
        notifyPair(proposal.fromNation, proposal.toNation, "Peace enacted", "Treaty " + proposal.id
                + " is settled. A binding truce lasts until " + relation.truceUntil + ".");
    }

    List<EconomyAccess.Transfer> treatyTransfers(DiplomaticProposal proposal) {
        String from = e.account(nation(proposal.fromNation));
        String to = e.account(nation(proposal.toNation));
        return List.of(new EconomyAccess.Transfer(from, to, proposal.offeredCents, "Peace treaty " + proposal.id),
                new EconomyAccess.Transfer(to, from, proposal.demandedCents, "Peace treaty " + proposal.id));
    }

    boolean requiresRatification(DiplomaticProposal proposal) {
        return e.config.requirePeaceRatification || e.flag(nation(proposal.fromNation), "peaceRatification")
                || e.flag(nation(proposal.toNation), "peaceRatification");
    }

    private void ratificationPassed(Bill bill) {
        DiplomaticProposal proposal = e.data.diplomacy.get(bill.treatyId);
        if (proposal == null || !"AWAITING_RATIFICATION".equals(proposal.status)) return;
        check(proposal.ratified != null, "Treaty " + proposal.id + " requires operator repair: ratified nation set is missing.");
        e.changed();
        proposal.ratified.add(bill.nationId);
        notifyPair(proposal.fromNation, proposal.toNation, "Treaty ratification",
                e.governmentName(bill.nationId) + " ratified treaty " + proposal.id + ".");
        if (proposal.fromNation != null && proposal.toNation != null
                && proposal.ratified.contains(proposal.fromNation) && proposal.ratified.contains(proposal.toNation)) {
            proposal.status = "READY";
            try {
                executeTreaty(proposal);
            } catch (UserError error) {
                proposal.lastError = clipped(error.getMessage());
                notifyPair(proposal.fromNation, proposal.toNation, "Treaty settlement blocked",
                        "Treaty " + proposal.id + " is ratified but not enacted: " + proposal.lastError
                                + ". An executive may retry with diplomacy execute before expiry.");
            }
        }
    }

    private void failTreaty(String id, String reason) {
        DiplomaticProposal proposal = e.data.diplomacy.get(id);
        if (proposal != null && LIVE_PROPOSALS.contains(proposal.status)) finishProposal(proposal, "FAILED", reason);
    }

    private void finishProposal(DiplomaticProposal proposal, String status, String reason) {
        e.changed();
        proposal.status = status;
        for (Bill bill : e.data.bills.values()) {
            if (bill != null && proposal.id.equals(bill.treatyId) && LIVE_BILLS.contains(bill.status)) {
                bill.status = "CANCELLED";
                billHistory(bill, "Treaty concluded without settlement: " + status);
            }
        }
        notifyPair(proposal.fromNation, proposal.toNation, "Diplomatic proposal " + status.toLowerCase(java.util.Locale.ROOT),
                proposal.id + ": " + reason);
        e.history("diplomacy", proposal.id, status + ": " + reason);
    }

    private DiplomaticProposal diplomaticProposal(String id) {
        DiplomaticProposal proposal = e.data.diplomacy.get(id);
        check(proposal != null, "Unknown diplomatic proposal ID.");
        return proposal;
    }

    void proposalCapacity(String from, String to) {
        for (String nation : List.of(from, to)) {
            check(e.data.diplomacy.values().stream().filter(Objects::nonNull)
                            .filter(p -> LIVE_PROPOSALS.contains(p.status) && (nation.equals(p.fromNation) || nation.equals(p.toNation))).count()
                            < e.config.maxDiplomaticProposalsPerNation, "A signatory has too many active diplomatic proposals.");
        }
    }

    void noDuplicateProposal(String from, String to, String type) {
        check(e.data.diplomacy.values().stream().filter(Objects::nonNull).noneMatch(p -> type.equals(p.type)
                        && LIVE_PROPOSALS.contains(p.status) && pair(from, to).equals(pair(p.fromNation, p.toNation))),
                "A proposal of this type already exists between these nations.");
    }

    private void closeAllianceProposals(String from, String to) {
        for (DiplomaticProposal proposal : e.data.diplomacy.values()) {
            if (proposal != null && "ALLIANCE".equals(proposal.type) && "PROPOSED".equals(proposal.status)
                    && pair(from, to).equals(pair(proposal.fromNation, proposal.toNation))) {
                proposal.status = "SUPERSEDED";
                e.changed();
            }
        }
    }

    void diplomaticExecutive(Actor actor, DiplomaticProposal proposal) {
        if (actor.admin()) return;
        Government from = nation(proposal.fromNation);
        Government to = nation(proposal.toNation);
        check((actor.id().toString().equals(from.leader) && e.member(from.leader, from))
                        || (actor.id().toString().equals(to.leader) && e.member(to.leader, to)),
                "Only a signatory's current leader may execute this treaty.");
    }

    boolean claimLocked(String key, String ignoredProposal) {
        return e.data.diplomacy.values().stream().filter(Objects::nonNull)
                .filter(p -> holdsProposalObligations(p) && !Objects.equals(p.id, ignoredProposal))
                .anyMatch(p -> p.chunks == null || p.chunks.stream().filter(Objects::nonNull)
                        .anyMatch(term -> Objects.equals(key, term.key)));
    }

    boolean governmentLocked(String governmentId) {
        Election election = e.data.elections.get(governmentId);
        return (election != null && (election.voting || !election.candidates.isEmpty()))
                || e.data.bills.values().stream().filter(Objects::nonNull)
                .anyMatch(b -> governmentId.equals(b.nationId) && (b.status == null || !CLOSED_BILLS.contains(b.status)))
                || e.data.diplomacy.values().stream().filter(Objects::nonNull)
                .anyMatch(p -> holdsProposalObligations(p) && (governmentId.equals(p.fromNation) || governmentId.equals(p.toNation)
                        || p.chunks == null || p.chunks.stream().filter(Objects::nonNull)
                        .anyMatch(term -> governmentId.equals(term.fromNation) || governmentId.equals(term.toNation)
                                || governmentId.equals(term.fromState) || governmentId.equals(term.toState)
                                || governmentId.equals(term.fromCity) || governmentId.equals(term.toCity))))
                || e.data.relations.values().stream().filter(Objects::nonNull)
                .anyMatch(r -> (governmentId.equals(r.first) || governmentId.equals(r.second))
                        && (!"NEUTRAL".equals(r.status) || r.truceUntil > e.now()));
    }

    private boolean holdsProposalObligations(DiplomaticProposal proposal) {
        return proposal.status == null || !CLOSED_PROPOSALS.contains(proposal.status);
    }

    String relationStatus(String first, String second) {
        if (first == null || second == null) return "NEUTRAL";
        if (first.equals(second)) return "SAME";
        Relation relation = e.data.relations.get(pair(first, second));
        return relation == null || relation.status == null ? "NEUTRAL" : relation.status;
    }

    boolean truce(String first, String second, long now) {
        if (first == null || second == null || first.equals(second)) return false;
        Relation relation = e.data.relations.get(pair(first, second));
        return relation != null && relation.truceUntil > now;
    }

    private Relation relation(String first, String second) {
        return e.data.relations.computeIfAbsent(pair(first, second), ignored -> {
            Relation relation = new Relation();
            relation.first = first.compareTo(second) < 0 ? first : second;
            relation.second = first.compareTo(second) < 0 ? second : first;
            e.changed();
            return relation;
        });
    }

    static String pair(String first, String second) {
        if (first == null || second == null) return "(invalid)";
        return first.compareTo(second) < 0 ? first + "|" + second : second + "|" + first;
    }

    Government nation(String reference) {
        Government nation = e.gov(reference);
        check(nation.kind == Kind.NATION, "Diplomacy, national elections and legislation require a nation.");
        return nation;
    }

    private void different(Government first, Government second) {
        check(!first.id.equals(second.id), "Choose two different nations.");
    }

    private void notifyPair(String first, String second, String subject, String body) {
        notifyGovernment(first, subject, body);
        notifyGovernment(second, subject, body);
    }

    private void notifyGovernment(String id, String subject, String body) {
        Government government = e.data.governments.get(id);
        if (e.validHierarchy(government)) e.governmentMail(id, subject, body);
    }

    void tick(long now) {
        for (Government nation : new ArrayList<>(e.data.governments.values())) {
            if (!e.viewableGovernment(nation) || nation.kind != Kind.NATION) continue;
            if (!e.data.elections.containsKey(nation.id)) schedule(nation);
            Election election = e.data.elections.get(nation.id);
            if (election == null || !nation.id.equals(election.nationId) || election.scheduleExhausted) continue;
            if (!election.voting && now >= election.nextStartAt) openElection(nation, election, election.nextStartAt);
            if (election.voting && now >= election.endsAt) finishElection(nation, election, now);
        }
        for (Emergency emergency : new ArrayList<>(e.data.emergencies.values())) {
            if (emergency != null && emergency.expiresAt <= now) {
                e.data.emergencies.remove(emergency.nationId);
                e.history("emergency", emergency.nationId, "Temporary order expired.");
            }
        }
        for (DiplomaticProposal proposal : new ArrayList<>(e.data.diplomacy.values())) {
            if (proposal != null && proposal.status != null && GovernanceEngine.validUuid(proposal.id)
                    && LIVE_PROPOSALS.contains(proposal.status) && proposal.expiresAt <= now)
                finishProposal(proposal, "EXPIRED", "Proposal expiry elapsed; war/relations remain unchanged.");
        }
        for (Bill bill : new ArrayList<>(e.data.bills.values())) {
            if (bill == null || bill.status == null || !GovernanceEngine.validUuid(bill.id)
                    || e.data.bills.get(bill.id) != bill || !LIVE_BILLS.contains(bill.status)) continue;
            try {
                advanceBill(bill, now);
            } catch (UserError error) {
                bill.status = "FAILED";
                billHistory(bill, "Invalid/unavailable policy: " + clipped(error.getMessage()));
                if (bill.treatyId != null) failTreaty(bill.treatyId, "Ratification could not be processed.");
            }
        }
        if (e.data.relations.values().removeIf(r -> r != null && "NEUTRAL".equals(r.status) && r.truceUntil <= now))
            e.changed();
        pruneHistory();
    }

    private void pruneHistory() {
        Map<String, Integer> retainedBills = new HashMap<>();
        e.data.bills.values().stream().filter(Objects::nonNull)
                .filter(b -> GovernanceEngine.validUuid(b.id) && b.status != null && CLOSED_BILLS.contains(b.status))
                .sorted(Comparator.comparingLong((Bill b) -> b.createdAt).reversed().thenComparing(b -> b.id))
                .toList().forEach(b -> {
                    int count = retainedBills.merge(b.nationId, 1, Integer::sum);
                    if (count > e.config.maxHistory) {
                        e.data.bills.remove(b.id);
                        e.changed();
                    }
                });
        Map<String, Integer> retainedProposals = new HashMap<>();
        e.data.diplomacy.values().stream().filter(Objects::nonNull)
                .filter(p -> GovernanceEngine.validUuid(p.id) && p.status != null && CLOSED_PROPOSALS.contains(p.status))
                .sorted(Comparator.comparingLong((DiplomaticProposal p) -> p.createdAt).reversed()
                        .thenComparing(p -> p.id)).toList().forEach(p -> {
                    int count = retainedProposals.merge(p.fromNation, 1, Integer::sum);
                    if (count > e.config.maxHistory) {
                        e.data.diplomacy.remove(p.id);
                        e.changed();
                    }
                });
    }

    static boolean reaches(long numerator, long denominator, int basisPoints) {
        if (denominator <= 0 || numerator <= 0) return false;
        return BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(10_000))
                .compareTo(BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(basisPoints))) >= 0;
    }

    private static String clipped(String value) {
        return value == null ? "Action unavailable." : value.substring(0, Math.min(value.length(), 500));
    }
}
