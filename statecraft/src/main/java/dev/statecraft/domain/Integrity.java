package dev.statecraft.domain;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceData.Bill;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.Company;
import dev.statecraft.domain.GovernanceData.CompanyProposal;
import dev.statecraft.domain.GovernanceData.Contract;
import dev.statecraft.domain.GovernanceData.DiplomaticProposal;
import dev.statecraft.domain.GovernanceData.Election;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.Invitation;
import dev.statecraft.domain.GovernanceData.Mail;
import dev.statecraft.domain.GovernanceData.Player;
import dev.statecraft.domain.GovernanceData.ShareReservation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class Integrity {
    private static final Set<String> CONTRACT_STATES = Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED", "COMPLETED", "CANCELLED", "EXPIRED");
    private static final Set<String> BILL_STATES = Set.of("DEBATE", "VOTING", "PASSED", "VETOED", "OVERRIDE_VOTING", "ENACTED", "FAILED", "CANCELLED", "EXPIRED");
    private static final Set<String> PROPOSAL_STATES = Set.of("PROPOSED", "AWAITING_RATIFICATION", "READY", "ENACTED", "FAILED", "CANCELLED", "REJECTED", "EXPIRED", "SUPERSEDED");
    private static final Set<String> COMPANY_PROPOSAL_STATES = Set.of("VOTING", "READY", "ENACTED", "FAILED", "CANCELLED", "EXPIRED");
    private static final Set<String> PERMITS = Arrays.stream(AccessAction.values()).filter(a -> a != AccessAction.PVP).map(Enum::name).collect(Collectors.toSet());
    private final GovernanceEngine e;

    Integrity(GovernanceEngine engine) {
        e = engine;
    }

    /** Absent JSON fields use POJO defaults; explicit null containers indicate malformed data. */
    static boolean initialize(GovernanceData data) {
        GovernanceEngine.check(data.schemaVersion == 1 || data.schemaVersion == GovernanceData.CURRENT_SCHEMA,
                "Unsupported governance schema " + data.schemaVersion + "; migrate/restore it without resetting world data.");
        required(data.players, "players");
        required(data.governments, "governments");
        required(data.claims, "claims");
        required(data.invitations, "invitations");
        required(data.elections, "elections");
        required(data.bills, "bills");
        required(data.laws, "laws");
        required(data.emergencies, "emergencies");
        required(data.relations, "relations");
        required(data.diplomacy, "diplomacy");
        required(data.companies, "companies");
        required(data.shareReservations, "shareReservations");
        required(data.companyProposals, "companyProposals");
        required(data.contracts, "contracts");
        required(data.history, "history");
        data.laws.forEach((id, laws) -> required(laws, "laws[" + id + "]"));
        for (Player player : data.players.values()) {
            if (player == null) continue;
            required(player.inbox, "player[" + player.id + "].inbox");
            required(player.sent, "player[" + player.id + "].sent");
        }
        for (Government government : data.governments.values()) {
            if (government == null) continue;
            String path = "government[" + government.id + "].";
            required(government.officers, path + "officers");
            required(government.settings, path + "settings");
            required(government.inbox, path + "inbox");
            required(government.sent, path + "sent");
            required(government.description, path + "description");
            required(government.tag, path + "tag");
            required(government.flag, path + "flag");
        }
        for (Claim claim : data.claims.values())
            if (claim != null) required(claim.permits, "claim[" + claim.key + "].permits");
        for (Election election : data.elections.values()) {
            if (election == null) continue;
            String path = "election[" + election.nationId + "].";
            required(election.candidates, path + "candidates");
            required(election.electorate, path + "electorate");
            required(election.votes, path + "votes");
            required(election.history, path + "history");
        }
        for (Bill bill : data.bills.values()) {
            if (bill == null) continue;
            String path = "bill[" + bill.id + "].";
            required(bill.status, path + "status");
            required(bill.electorate, path + "electorate");
            required(bill.votes, path + "votes");
            required(bill.history, path + "history");
        }
        for (DiplomaticProposal proposal : data.diplomacy.values()) {
            if (proposal == null) continue;
            String path = "diplomaticProposal[" + proposal.id + "].";
            required(proposal.status, path + "status");
            required(proposal.chunks, path + "chunks");
            required(proposal.ratified, path + "ratified");
            required(proposal.lastError, path + "lastError");
            required(proposal.message, path + "message");
        }
        for (Company company : data.companies.values()) {
            if (company == null) continue;
            String path = "company[" + company.id + "].";
            required(company.members, path + "members");
            required(company.officers, path + "officers");
            required(company.shares, path + "shares");
            required(company.description, path + "description");
        }
        for (CompanyProposal proposal : data.companyProposals.values()) {
            if (proposal == null) continue;
            String path = "companyProposal[" + proposal.id + "].";
            required(proposal.status, path + "status");
            required(proposal.electorate, path + "electorate");
            required(proposal.votes, path + "votes");
            required(proposal.lastError, path + "lastError");
        }
        for (Contract contract : data.contracts.values()) {
            if (contract == null) continue;
            String path = "contract[" + contract.id + "].";
            required(contract.status, path + "status");
            required(contract.chunks, path + "chunks");
            required(contract.bids, path + "bids");
            required(contract.completionNote, path + "completionNote");
        }
        if (data.schemaVersion == 1) {
            Territory.migrateLegacy(data);
            data.schemaVersion = GovernanceData.CURRENT_SCHEMA;
            return true;
        }
        return false;
    }

    private static void required(Object value, String path) {
        GovernanceEngine.check(value != null, "Malformed governance data: " + path
                + " is null. Existing data was not reset; restore/migrate this field from a valid backup.");
    }

    List<String> audit() {
        List<String> issues = issues();
        return issues.isEmpty() ? List.of("No integrity issues found.") : issues;
    }

    List<String> startupIssues() {
        return issues(true);
    }

    private List<String> issues() {
        return issues(false);
    }

    private List<String> issues(boolean structuralOnly) {
        List<String> result = new ArrayList<>();
        if (e.data.schemaVersion != GovernanceData.CURRENT_SCHEMA)
            result.add("Unsupported schema version " + e.data.schemaVersion + "; do not repair without migration.");
        if (e.data.lastTick < 0) result.add("World lastTick is negative.");
        Set<String> governmentNames = new HashSet<>();
        for (Map.Entry<String, Government> entry : e.data.governments.entrySet()) {
            String key = entry.getKey();
            Government government = entry.getValue();
            if (government == null) { result.add("Null government at " + key); continue; }
            identity(result, "Government", key, government.id);
            if (!e.validHierarchy(government)) result.add("Orphan/invalid government hierarchy " + key);
            if (government.name == null || !governmentNames.add(government.name.toLowerCase(Locale.ROOT)))
                result.add("Missing/duplicate government name " + key);
            if (!GovernanceEngine.validUuid(government.leader) || !e.member(government.leader, government))
                result.add("Invalid government leader " + key + "; use admin leader with an explicit citizen.");
            for (String officer : government.officers)
                if (!e.member(officer, government) || Objects.equals(officer, government.leader))
                    result.add("Invalid officer " + officer + " in " + key);
            for (Map.Entry<String, String> policy : government.settings.entrySet()) {
                try { GovernanceSettings.validate(policy.getKey(), policy.getValue()); }
                catch (UserError | NullPointerException error) { result.add("Invalid policy " + key + ":" + policy.getKey()); }
            }
            if (!structuralOnly && government.kind != null && e.members(government).size() > e.memberLimit(government.kind))
                result.add("Citizenship exceeds configured limit in " + key + " (never evicted automatically).");
            auditMail(result, government.inbox, government.sent,
                    government.kind == null ? "invalid:" + key : e.account(government), structuralOnly);
        }
        for (Map.Entry<String, Player> entry : e.data.players.entrySet()) {
            Player player = entry.getValue();
            if (player == null) { result.add("Null player at " + entry.getKey()); continue; }
            identity(result, "Player", entry.getKey(), player.id);
            if (player.name == null || player.name.isBlank()) result.add("Missing player name " + entry.getKey());
            Government nation = e.data.governments.get(player.nationId);
            Government state = e.data.governments.get(player.stateId);
            Government city = e.data.governments.get(player.cityId);
            if (player.nationId != null && (!e.validHierarchy(nation) || nation.kind != Kind.NATION))
                result.add("Dangling nation membership " + player.id);
            if (player.stateId != null && (!e.validHierarchy(state) || state.kind != Kind.STATE
                    || !Objects.equals(state.parentId, player.nationId))) result.add("Inconsistent state membership " + player.id);
            if (player.cityId != null && (!e.validHierarchy(city) || city.kind != Kind.CITY
                    || !Objects.equals(city.parentId, player.stateId) || !Objects.equals(e.nationId(city), player.nationId)))
                result.add("Inconsistent city membership " + player.id);
            if (player.autoClaim && (nation == null || !e.member(player.id, nation)))
                result.add("Invalid automatic claim membership " + player.id);
            auditMail(result, player.inbox, player.sent, "player:" + entry.getKey(), structuralOnly);
        }
        for (Map.Entry<String, Claim> entry : e.data.claims.entrySet()) {
            String key = entry.getKey();
            Claim claim = entry.getValue();
            if (claim == null) { result.add("Null claim at " + key); continue; }
            if (!Objects.equals(key, claim.key)) result.add("Claim key mismatch " + key);
            try {
                if (!ChunkKey.parse(key).toString().equals(key)) result.add("Noncanonical chunk key " + key);
            } catch (UserError | NullPointerException error) { result.add("Invalid chunk key " + key); }
            if (e.claimGovernment(claim) == null)
                result.add("Orphan/invalid national claim " + key + "; nation is required and state/city allocations must agree. Title and obligations are preserved for explicit reassignment.");
            try { e.validateOwnerAccount(claim.ownerAccount); }
            catch (UserError error) { result.add("Invalid private owner account at " + key + ": " + claim.ownerAccount); }
            if (claim.improvements < 0 || claim.improvements > 1_000_000_000) result.add("Invalid improvement count at " + key);
            for (Map.Entry<String, Set<String>> permit : claim.permits.entrySet()) {
                if (!e.data.players.containsKey(permit.getKey()) || permit.getValue() == null
                        || permit.getValue().isEmpty() || !PERMITS.containsAll(permit.getValue()))
                    result.add("Invalid chunk permit at " + key + " for " + permit.getKey());
            }
        }
        if (!structuralOnly) {
            for (Government government : e.data.governments.values()) {
                if (!e.validHierarchy(government)) continue;
                if (government.kind == Kind.NATION && e.nationClaims(government.id).size() > e.config.maxClaimsPerNation)
                    result.add("National claims exceed configured limit in " + government.id + " (never unclaimed automatically).");
                if (government.kind == Kind.CITY && e.cityClaims(government.id).size() > e.config.maxClaimsPerCity)
                    result.add("City allocations exceed configured limit in " + government.id + " (never unassigned automatically).");
            }
        }
        for (Map.Entry<String, Invitation> entry : e.data.invitations.entrySet()) {
            Invitation invite = entry.getValue();
            if (invite == null) { result.add("Null invitation " + entry.getKey()); continue; }
            identity(result, "Invitation", entry.getKey(), invite.id);
            if (!validInvitationReferences(invite)) result.add("Orphan/invalid invitation " + entry.getKey());
            else if (!structuralOnly && !validInvitation(invite)) result.add("Expired/inconsistent invitation " + entry.getKey());
        }
        for (Map.Entry<String, Election> entry : e.data.elections.entrySet()) {
            Election election = entry.getValue();
            Government nation = e.data.governments.get(entry.getKey());
            if (election == null || !e.validHierarchy(nation) || nation.kind != Kind.NATION
                    || !entry.getKey().equals(election.nationId)) {
                result.add("Orphan/invalid election " + entry.getKey());
                continue;
            }
            if (election.nextStartAt < 0 || election.endsAt < 0) result.add("Invalid election times " + entry.getKey());
            for (String candidate : election.candidates)
                if (!e.member(candidate, nation)) result.add("Ineligible election candidate " + candidate + " in " + nation.id);
            for (Map.Entry<String, String> vote : election.votes.entrySet())
                if (!election.electorate.contains(vote.getKey()) || !e.data.players.containsKey(vote.getValue()))
                    result.add("Invalid election ballot in " + nation.id);
        }
        Set<String> companyNames = new HashSet<>();
        for (Map.Entry<String, Company> entry : e.data.companies.entrySet()) {
            Company company = entry.getValue();
            if (company == null) { result.add("Null company " + entry.getKey()); continue; }
            identity(result, "Company", entry.getKey(), company.id);
            if (!e.viewableCompany(company)) result.add("Invalid company owner/membership " + entry.getKey());
            if (company.name == null || !companyNames.add(company.name.toLowerCase(Locale.ROOT)))
                result.add("Missing/duplicate company name " + entry.getKey());
            for (String member : company.members)
                if (!e.data.players.containsKey(member)) result.add("Unknown company member " + member + " in " + entry.getKey());
            for (String officer : company.officers)
                if (!company.members.contains(officer) || Objects.equals(officer, company.owner))
                    result.add("Invalid company officer " + officer + " in " + entry.getKey());
            try { e.commerce.assertShareIntegrity(company); }
            catch (UserError error) { result.add("Share ledger " + entry.getKey() + ": " + error.getMessage()); }
        }
        for (Map.Entry<String, ShareReservation> entry : e.data.shareReservations.entrySet()) {
            ShareReservation reservation = entry.getValue();
            if (reservation == null || !Objects.equals(entry.getKey(), reservation.reference)
                    || !e.data.companies.containsKey(reservation.companyId)
                    || !e.data.players.containsKey(reservation.owner) || reservation.quantity <= 0)
                result.add("Invalid stock reservation " + entry.getKey() + " (never released automatically).");
        }
        for (Map.Entry<String, Bill> entry : e.data.bills.entrySet()) {
            Bill bill = entry.getValue();
            if (bill == null) { result.add("Null bill " + entry.getKey()); continue; }
            identity(result, "Bill", entry.getKey(), bill.id);
            if (!known(BILL_STATES, bill.status)) result.add("Unknown bill state " + entry.getKey());
            if (!e.validHierarchy(e.data.governments.get(bill.nationId))) result.add("Orphan bill " + entry.getKey());
            try {
                if ("roleplay".equals(bill.policy)) {
                    if (!"-".equals(bill.value)) result.add("Invalid roleplay bill value " + bill.id);
                } else if ("treaty".equals(bill.policy)) {
                    if (!e.data.diplomacy.containsKey(bill.treatyId) && known(Set.of("DEBATE", "VOTING"), bill.status))
                        result.add("Missing treaty for active bill " + bill.id);
                } else GovernanceSettings.validate(bill.policy, bill.value);
            } catch (UserError | NullPointerException error) { result.add("Invalid bill policy " + bill.id); }
            for (Map.Entry<String, String> vote : bill.votes.entrySet())
                if (!bill.electorate.contains(vote.getKey()) || !known(Set.of("yes", "no", "abstain"), vote.getValue()))
                    result.add("Invalid legislative ballot " + bill.id);
        }
        for (Map.Entry<String, CompanyProposal> entry : e.data.companyProposals.entrySet()) {
            CompanyProposal proposal = entry.getValue();
            if (proposal == null) { result.add("Null company proposal " + entry.getKey()); continue; }
            identity(result, "Company proposal", entry.getKey(), proposal.id);
            if (!known(COMPANY_PROPOSAL_STATES, proposal.status)) result.add("Invalid company proposal state " + entry.getKey());
            if (known(Set.of("VOTING", "READY"), proposal.status) && !e.data.companies.containsKey(proposal.companyId))
                result.add("Orphan active company proposal " + entry.getKey());
            if (!known(Set.of("owner", "dividend", "roleplay", "dissolve"), proposal.type))
                result.add("Invalid company proposal policy " + entry.getKey());
            for (Map.Entry<String, Long> holder : proposal.electorate.entrySet())
                if (!e.data.players.containsKey(holder.getKey()) || holder.getValue() == null
                        || holder.getValue() <= 0 || holder.getValue() > 1_000_000_000L)
                    result.add("Invalid shareholder electorate " + entry.getKey());
        }
        for (Map.Entry<String, Contract> entry : e.data.contracts.entrySet()) {
            Contract contract = entry.getValue();
            if (contract == null) { result.add("Null contract " + entry.getKey()); continue; }
            identity(result, "Contract", entry.getKey(), contract.id);
            if (!known(CONTRACT_STATES, contract.status)) result.add("Invalid contract status " + entry.getKey());
            if (known(Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED"), contract.status)) {
                if (!e.validHierarchy(e.data.governments.get(contract.governmentId))) result.add("Orphan active contract " + entry.getKey());
                if (contract.chunks.stream().anyMatch(key -> !e.data.claims.containsKey(key)))
                    result.add("Active contract has missing chunks " + entry.getKey());
            }
            try { Money.nonNegative(contract.escrowCents); }
            catch (UserError error) { result.add("Invalid contract escrow amount " + entry.getKey()); }
            if (contract.escrowCents > 0 && !known(Set.of("AWARDED", "SUBMITTED"), contract.status))
                result.add("Contract has escrow outside an awarded state " + entry.getKey() + " (funds must be reconciled manually).");
            if (known(Set.of("AWARDED", "SUBMITTED"), contract.status)) {
                try { e.commerce.validateAward(contract); }
                catch (UserError error) { result.add("Contract award " + entry.getKey() + ": " + error.getMessage()); }
            }
        }
        for (Map.Entry<String, DiplomaticProposal> entry : e.data.diplomacy.entrySet()) {
            DiplomaticProposal proposal = entry.getValue();
            if (proposal == null) { result.add("Null diplomatic proposal " + entry.getKey()); continue; }
            identity(result, "Diplomatic proposal", entry.getKey(), proposal.id);
            if (!known(PROPOSAL_STATES, proposal.status) || !known(Set.of("ALLIANCE", "PEACE"), proposal.type))
                result.add("Invalid diplomatic proposal " + entry.getKey());
            if (known(Set.of("PROPOSED", "AWAITING_RATIFICATION", "READY"), proposal.status)) {
                try { e.politics.validateTreatyReferences(proposal); }
                catch (UserError error) { result.add(error.getMessage()); }
            }
            try { Money.nonNegative(proposal.offeredCents); Money.nonNegative(proposal.demandedCents); }
            catch (UserError error) { result.add("Invalid treaty monetary terms " + entry.getKey()); }
        }
        e.data.relations.forEach((key, relation) -> {
            if (relation == null || !Objects.equals(key, Politics.pair(relation.first, relation.second))
                    || Objects.equals(relation.first, relation.second) || !known(Set.of("NEUTRAL", "ALLIED", "WAR"), relation.status)
                    || !e.validHierarchy(e.data.governments.get(relation.first)) || !e.validHierarchy(e.data.governments.get(relation.second)))
                result.add("Invalid diplomatic relation " + key);
        });
        return result;
    }

    String repair() {
        GovernanceEngine.check(e.data.schemaVersion == GovernanceData.CURRENT_SCHEMA, "This schema requires an explicit migration, not orphan repair.");
        e.changed();
        int changes = 0;
        if (e.data.lastTick < 0) {
            e.data.lastTick = 0;
            changes++;
        }
        for (Map.Entry<String, Player> entry : e.data.players.entrySet()) {
            Player player = entry.getValue();
            if (player == null || !GovernanceEngine.validUuid(entry.getKey())) continue;
            if (player.id == null) { player.id = entry.getKey(); changes++; }
            if (player.name == null || player.name.isBlank()) { player.name = player.id; changes++; }
            Government nation = e.data.governments.get(player.nationId);
            if (player.nationId != null && (!e.validHierarchy(nation) || nation.kind != Kind.NATION)) {
                player.nationId = null;
                player.stateId = null;
                player.cityId = null;
                changes++;
            }
            Government state = e.data.governments.get(player.stateId);
            if (player.stateId != null && (!e.validHierarchy(state) || state.kind != Kind.STATE
                    || !Objects.equals(state.parentId, player.nationId))) {
                player.stateId = null;
                player.cityId = null;
                changes++;
            }
            Government city = e.data.governments.get(player.cityId);
            if (player.cityId != null && (!e.validHierarchy(city) || city.kind != Kind.CITY
                    || !Objects.equals(city.parentId, player.stateId) || !Objects.equals(e.nationId(city), player.nationId))) {
                player.cityId = null;
                changes++;
            }
            if (player.nationId == null) player.autoClaim = false;
        }
        for (Government government : e.data.governments.values()) {
            if (government == null) continue;
            int previous = government.officers.size();
            government.officers.removeIf(id -> !e.member(id, government) || Objects.equals(id, government.leader));
            changes += previous - government.officers.size();
            // Unknown policies are dropped, not converted into guessed tax/access defaults.
            for (String key : new ArrayList<>(government.settings.keySet())) {
                try { GovernanceSettings.validate(key, government.settings.get(key)); }
                catch (UserError | NullPointerException error) { government.settings.remove(key); changes++; }
            }
        }
        for (Company company : e.data.companies.values()) {
            if (company == null) continue;
            int before = company.members.size() + company.officers.size();
            company.members.removeIf(id -> !e.data.players.containsKey(id) && !Objects.equals(id, company.owner));
            company.officers.removeIf(id -> !company.members.contains(id) || Objects.equals(id, company.owner));
            changes += before - company.members.size() - company.officers.size();
        }
        int beforeInvites = e.data.invitations.size();
        e.data.invitations.values().removeIf(invite -> invite == null || !validInvitation(invite));
        changes += beforeInvites - e.data.invitations.size();
        for (Map.Entry<String, Claim> entry : new ArrayList<>(e.data.claims.entrySet())) {
            String key = entry.getKey();
            Claim claim = entry.getValue();
            if (claim == null) {
                if (freeClaim(key)) { e.data.claims.remove(key); changes++; }
                continue;
            }
            try {
                String canonical = ChunkKey.parse(key).toString();
                if (claim.key == null && canonical.equals(key)) { claim.key = key; changes++; }
                if (!canonical.equals(key) && !e.data.claims.containsKey(canonical) && freeClaim(key)) {
                    e.data.claims.remove(key);
                    claim.key = canonical;
                    e.data.claims.put(canonical, claim);
                    key = canonical;
                    changes++;
                }
            } catch (UserError | NullPointerException ignored) { }
            int before = claim.permits.size();
            claim.permits.entrySet().removeIf(p -> !e.data.players.containsKey(p.getKey())
                    || p.getValue() == null || p.getValue().isEmpty() || !PERMITS.containsAll(p.getValue()));
            changes += before - claim.permits.size();
            if (claim.improvements < 0 || claim.improvements > 1_000_000_000) {
                claim.improvements = Math.max(0, Math.min(1_000_000_000, claim.improvements));
                changes++;
            }
            // Missing national ownership is never inferred here, nor erased to make an audit pass.
        }
        for (Contract contract : e.data.contracts.values()) {
            if (contract != null && contract.escrowCents == 0 && !e.data.governments.containsKey(contract.governmentId)
                    && known(Set.of("OPEN", "REVIEW", "AWARDED", "SUBMITTED"), contract.status)) {
                contract.status = "CANCELLED";
                e.history("repair", contract.id, "Cancelled orphan contract with zero escrow.");
                changes++;
            }
        }
        // Only empty, financially inert orphan governments can be removed automatically.
        for (int pass = 0; pass < 3; pass++) {
            for (Map.Entry<String, Government> entry : new ArrayList<>(e.data.governments.entrySet())) {
                Government government = entry.getValue();
                if (government == null || government.kind == null || !Objects.equals(entry.getKey(), government.id)
                        || e.validHierarchy(government)) continue;
                if (e.data.players.values().stream().filter(Objects::nonNull).anyMatch(p -> Objects.equals(p.nationId, government.id)
                        || Objects.equals(p.stateId, government.id) || Objects.equals(p.cityId, government.id))) continue;
                if (e.data.governments.values().stream().filter(Objects::nonNull).anyMatch(g -> government.id.equals(g.parentId))) continue;
                if (e.data.claims.values().stream().filter(Objects::nonNull).anyMatch(c -> government.id.equals(c.nationId)
                        || government.id.equals(c.stateId) || government.id.equals(c.cityId)
                        || e.account(government).equals(c.ownerAccount))) continue;
                if (e.commerce.governmentLocked(government.id) || e.politics.governmentLocked(government.id)) continue;
                try { e.assertAccountDisposable(e.account(government)); }
                catch (UserError error) { continue; }
                e.data.governments.remove(entry.getKey());
                e.history("repair", entry.getKey(), "Removed empty financially inert orphan government.");
                changes++;
            }
        }
        for (Map.Entry<String, Election> entry : new ArrayList<>(e.data.elections.entrySet())) {
            Election election = entry.getValue();
            Government nation = e.data.governments.get(entry.getKey());
            if (!e.data.governments.containsKey(entry.getKey())) {
                e.data.elections.remove(entry.getKey());
                changes++;
            } else if (election != null && e.validHierarchy(nation) && nation.kind == Kind.NATION) {
                int before = election.candidates.size();
                election.candidates.removeIf(id -> !e.member(id, nation));
                changes += before - election.candidates.size();
            }
        }
        boundRetention();
        e.history("repair", "world", "Applied " + changes + " safe repairs; financial titles, shares and obligations were not guessed.");
        int remaining = issues().size();
        return "Applied " + changes + " safe repairs. " + remaining + " issues remain; use admin audit [page]."
                + "\nOrphan property, financial obligations, stock reservations, conflicting IDs and missing leaders require explicit operator decisions."
                + "\nUse admin reassign for preserved claims and admin leader for leadership; financial records are never discarded to make an audit pass.";
    }

    private boolean freeClaim(String key) {
        try { e.assertClaimFree(key, null); return true; }
        catch (UserError | NullPointerException error) { return false; }
    }

    private boolean validInvitation(Invitation invite) {
        if (!validInvitationReferences(invite) || invite.expiresAt <= e.now()) return false;
        if (invite.governmentId != null)
            return e.eligibleInvitation(e.data.players.get(invite.playerId), e.data.governments.get(invite.governmentId));
        return !e.data.companies.get(invite.companyId).members.contains(invite.playerId);
    }

    private boolean validInvitationReferences(Invitation invite) {
        if (!GovernanceEngine.validUuid(invite.id) || e.data.invitations.get(invite.id) != invite) return false;
        if (invite.expiresAt < 0 || !e.data.players.containsKey(invite.playerId)) return false;
        if ((invite.governmentId == null) == (invite.companyId == null)) return false;
        if (invite.governmentId != null)
            return e.validHierarchy(e.data.governments.get(invite.governmentId));
        Company company = e.data.companies.get(invite.companyId);
        return e.viewableCompany(company);
    }

    private void auditMail(List<String> issues, List<Mail> inbox, List<Mail> sent, String owner, boolean structuralOnly) {
        if (!structuralOnly && (inbox.size() > e.config.maxMail || sent.size() > e.config.maxMail))
            issues.add("Mailbox exceeds retention limit " + owner);
        if (inbox.stream().anyMatch(m -> m == null || !owner.equals(m.recipient)))
            issues.add("Invalid inbox envelope " + owner);
        if (sent.stream().anyMatch(m -> m == null || !owner.equals(m.sender)))
            issues.add("Invalid sent envelope " + owner);
    }

    void boundRetention() {
        e.trim(e.data.history, e.config.maxHistory);
        for (Player player : e.data.players.values()) {
            if (player == null) continue;
            e.trim(player.inbox, e.config.maxMail);
            e.trim(player.sent, e.config.maxMail);
        }
        for (Government government : e.data.governments.values()) {
            if (government == null) continue;
            e.trim(government.inbox, e.config.maxMail);
            e.trim(government.sent, e.config.maxMail);
        }
        for (Election election : e.data.elections.values())
            if (election != null) e.trim(election.history, e.config.maxHistory);
        for (Bill bill : e.data.bills.values())
            if (bill != null) e.trim(bill.history, e.config.maxHistory);
        e.data.laws.values().stream().filter(Objects::nonNull)
                .forEach(laws -> e.trim(laws, e.config.maxLawsPerNation));
    }

    private static void identity(List<String> issues, String type, String key, String id) {
        if (!GovernanceEngine.validUuid(key) || !Objects.equals(key, id)) issues.add(type + " UUID/index mismatch " + key);
    }

    private static boolean known(Set<String> choices, String value) {
        return value != null && choices.contains(value);
    }
}
