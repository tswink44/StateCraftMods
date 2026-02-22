package com.statecraft.company;

import com.statecraft.StateCraft;
import com.statecraft.integration.IntegrationRegistry;
import com.statecraft.mail.Mail;
import com.statecraft.mail.MailManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages shareholder proposals and voting for companies.
 * Officers/founders create proposals, all shareholders vote (share-weighted).
 * Proposals auto-resolve when they expire (48 hours).
 *
 * Singleton — one instance per server.
 */
public class ShareholderVoteManager {

    private static ShareholderVoteManager instance;

    /** All proposals indexed by proposal ID. Includes active and recently resolved. */
    private final Map<UUID, ShareholderProposal> proposals = new ConcurrentHashMap<>();

    /** Tracks last resolution time per (companyId, proposalType) for cooldowns. */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /** Max number of proposals to keep in history per company */
    private static final int MAX_HISTORY_PER_COMPANY = 20;

    private boolean dirty = false;

    private ShareholderVoteManager() {}

    public static ShareholderVoteManager getInstance() {
        if (instance == null) {
            instance = new ShareholderVoteManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Proposal Creation ====================

    /**
     * Create a new shareholder proposal.
     *
     * @param companyId     The company UUID
     * @param proposerId    The player creating the proposal (must be officer/founder)
     * @param proposerName  Display name of the proposer
     * @param type          The proposal type
     * @param doubleValue   Type-specific double value (e.g., dividend rate)
     * @param intValue      Type-specific int value (e.g., share count)
     * @param longValue     Type-specific long value (e.g., dividend period ticks)
     * @param targetPlayer  Target player name (for REMOVE_OFFICER)
     * @param server        Server instance for notifications
     * @return Result message (null prefix = error, starts with §a = success)
     */
    public String createProposal(UUID companyId, UUID proposerId, String proposerName,
                                  ShareholderProposal.ProposalType type,
                                  double doubleValue, int intValue, long longValue,
                                  String targetPlayer, @Nullable MinecraftServer server) {
        Company company = CompanyManager.getInstance().getCompany(companyId);
        if (company == null) return "§cCompany not found.";

        // Only officers/founders can create proposals
        if (!company.isOfficer(proposerId)) {
            return "§cOnly officers and the founder can create proposals.";
        }

        // Check for active proposal of the same type
        boolean hasActive = proposals.values().stream()
            .anyMatch(p -> p.getCompanyId().equals(companyId)
                && p.getType() == type
                && p.getStatus() == ShareholderProposal.Status.ACTIVE);
        if (hasActive) {
            return "§cThere is already an active proposal of this type.";
        }

        // Check cooldown
        String cooldownKey = companyId + ":" + type.name();
        Long lastResolved = cooldowns.get(cooldownKey);
        if (lastResolved != null) {
            long elapsed = System.currentTimeMillis() - lastResolved;
            if (elapsed < ShareholderProposal.COOLDOWN_MS) {
                long hoursRemaining = (ShareholderProposal.COOLDOWN_MS - elapsed) / (1000 * 60 * 60);
                long minutesRemaining = ((ShareholderProposal.COOLDOWN_MS - elapsed) % (1000 * 60 * 60)) / (1000 * 60);
                return "§cCooldown active. " + hoursRemaining + "h " + minutesRemaining + "m remaining.";
            }
        }

        // Validate type-specific parameters
        String validationError = validateProposal(company, type, doubleValue, intValue, longValue, targetPlayer);
        if (validationError != null) return validationError;

        // Build description
        ShareholderProposal proposal = new ShareholderProposal(
            companyId, proposerId, proposerName, type,
            "" // Description built from summary
        );
        proposal.setDoubleValue(doubleValue);
        proposal.setIntValue(intValue);
        proposal.setLongValue(longValue);

        // Resolve target player for REMOVE_OFFICER
        if (type == ShareholderProposal.ProposalType.REMOVE_OFFICER && targetPlayer != null && server != null) {
            ServerPlayer target = server.getPlayerList().getPlayerByName(targetPlayer);
            if (target != null) {
                if (!company.isOfficer(target.getUUID()) || company.isFounder(target.getUUID())) {
                    return "§cTarget is not a removable officer.";
                }
                proposal.setTargetPlayerId(target.getUUID());
                proposal.setTargetPlayerName(targetPlayer);
            } else {
                return "§cPlayer not found: " + targetPlayer;
            }
        }

        proposals.put(proposal.getId(), proposal);
        dirty = true;

        // Notify all shareholders via mail
        notifyShareholdersOfNewProposal(company, proposal, server);

        StateCraft.LOGGER.info("Shareholder proposal created: {} for company '{}' by {}",
            type, company.getName(), proposerName);

        return "§aProposal created: " + proposal.getSummary() + " §7(48h voting period)";
    }

    @Nullable
    private String validateProposal(Company company, ShareholderProposal.ProposalType type,
                                     double doubleValue, int intValue, long longValue, String targetPlayer) {
        return switch (type) {
            case SET_DIVIDEND_RATE -> {
                if (doubleValue < 0 || doubleValue > 1.0)
                    yield "§cDividend rate must be between 0% and 100%.";
                yield null;
            }
            case ISSUE_SHARES -> {
                if (intValue <= 0 || intValue > 1000000)
                    yield "§cShare count must be between 1 and 1,000,000.";
                yield null;
            }
            case SHARE_BUYBACK -> {
                if (intValue <= 0)
                    yield "§cBuyback count must be positive.";
                if (intValue > company.getTotalShares() / 2)
                    yield "§cCannot buy back more than half the total shares.";
                yield null;
            }
            case DISSOLVE_COMPANY -> null;
            case CONVERT_TO_BANK -> {
                if (company.isBank())
                    yield "§cCompany is already a bank.";
                yield null;
            }
            case CONVERT_TO_GENERAL -> {
                if (!company.isBank())
                    yield "§cCompany is already a general company.";
                yield null;
            }
            case REMOVE_OFFICER -> {
                if (targetPlayer == null || targetPlayer.isEmpty())
                    yield "§cMust specify an officer to remove.";
                yield null;
            }
            case SET_DIVIDEND_PERIOD -> {
                if (longValue < 1200) // Minimum 1 minute
                    yield "§cDividend period must be at least 1200 ticks (1 minute).";
                yield null;
            }
        };
    }

    // ==================== Voting ====================

    /**
     * Cast a vote on a proposal.
     *
     * @param proposalId The proposal UUID
     * @param voterId    The voter's UUID (must be a shareholder)
     * @param vote       The vote choice
     * @return Result message
     */
    public String castVote(UUID proposalId, UUID voterId, ShareholderProposal.Vote vote) {
        ShareholderProposal proposal = proposals.get(proposalId);
        if (proposal == null) return "§cProposal not found.";
        if (proposal.getStatus() != ShareholderProposal.Status.ACTIVE) return "§cThis proposal is no longer active.";
        if (proposal.isExpired()) return "§cThis proposal has expired.";

        Company company = CompanyManager.getInstance().getCompany(proposal.getCompanyId());
        if (company == null) return "§cCompany no longer exists.";

        // Must be a shareholder to vote
        if (!company.isShareholder(voterId)) {
            return "§cOnly shareholders can vote.";
        }

        if (proposal.hasVoted(voterId)) {
            return "§cYou have already voted on this proposal.";
        }

        proposal.castVote(voterId, vote);
        dirty = true;

        String voteStr = switch (vote) {
            case YES -> "§aYES";
            case NO -> "§cNO";
            case ABSTAIN -> "§7ABSTAIN";
        };

        int playerShares = company.getShareCount(voterId);
        ShareholderProposal.VoteTally tally = proposal.getTally(company);

        return "§aVote recorded: " + voteStr + " §7(" + playerShares + " shares, " +
            String.format("%.0f%%", tally.quorumPercentage()) + " quorum)";
    }

    // ==================== Tick / Expiration ====================

    /**
     * Called periodically to resolve expired proposals.
     */
    public void tick(MinecraftServer server) {
        boolean changed = false;

        for (ShareholderProposal proposal : proposals.values()) {
            if (proposal.getStatus() != ShareholderProposal.Status.ACTIVE) continue;
            if (!proposal.isExpired()) continue;

            Company company = CompanyManager.getInstance().getCompany(proposal.getCompanyId());
            if (company == null) {
                proposal.setStatus(ShareholderProposal.Status.EXPIRED);
                changed = true;
                continue;
            }

            // Resolve the proposal
            boolean passed = proposal.resolve(company);

            // Record cooldown
            String cooldownKey = proposal.getCompanyId() + ":" + proposal.getType().name();
            cooldowns.put(cooldownKey, System.currentTimeMillis());

            if (passed) {
                applyProposal(proposal, company, server);
            }

            // Notify shareholders of result
            notifyShareholdersOfResult(company, proposal, server);

            // Send to company mailbox
            notifyCompanyMailbox(company, proposal);

            changed = true;
        }

        // Prune old resolved proposals (keep max per company)
        pruneOldProposals();

        if (changed) {
            dirty = true;
        }
    }

    /**
     * Apply the effects of a passed proposal.
     */
    private void applyProposal(ShareholderProposal proposal, Company company, MinecraftServer server) {
        switch (proposal.getType()) {
            case SET_DIVIDEND_RATE -> {
                IntegrationRegistry.setDividendRate(company.getId(), proposal.getDoubleValue());
                StateCraft.LOGGER.info("Shareholder vote: {} dividend rate set to {}%",
                    company.getName(), String.format("%.1f", proposal.getDoubleValue() * 100));
            }
            case SET_DIVIDEND_PERIOD -> {
                IntegrationRegistry.setDividendPeriodTicks(company.getId(), proposal.getLongValue());
                StateCraft.LOGGER.info("Shareholder vote: {} dividend period set to {} ticks",
                    company.getName(), proposal.getLongValue());
            }
            case ISSUE_SHARES -> {
                company.issueShares(company.getFounderId(), proposal.getIntValue());
                CompanyManager.getInstance().markDirty();
                StateCraft.LOGGER.info("Shareholder vote: {} issued {} new shares to founder",
                    company.getName(), proposal.getIntValue());
            }
            case SHARE_BUYBACK -> {
                // Buy back shares from the founder, removing them from circulation
                int count = proposal.getIntValue();
                UUID founderId = company.getFounderId();
                boolean success = company.buybackShares(founderId, count);
                if (success) {
                    StateCraft.LOGGER.info("Shareholder vote: {} bought back {} shares from founder (new total: {})",
                        company.getName(), count, company.getTotalShares());
                } else {
                    StateCraft.LOGGER.warn("Shareholder vote: {} buyback of {} shares failed — founder has insufficient shares",
                        company.getName(), count);
                }
                CompanyManager.getInstance().markDirty();
            }
            case DISSOLVE_COMPANY -> {
                // Dissolve via economy integration if available, then via CompanyManager
                boolean dissolved = IntegrationRegistry.notifyCompanyDissolving(company.getId(), company.getFounderId());
                if (!dissolved) {
                    CompanyManager.getInstance().dissolveCompany(company.getId(), company.getFounderId());
                }
                StateCraft.LOGGER.info("Shareholder vote: {} dissolved by shareholder vote", company.getName());
            }
            case CONVERT_TO_BANK -> {
                company.setCompanyType(Company.CompanyType.BANK);
                IntegrationRegistry.notifyBankCreated(company.getId());
                CompanyManager.getInstance().markDirty();
                StateCraft.LOGGER.info("Shareholder vote: {} converted to bank", company.getName());
            }
            case CONVERT_TO_GENERAL -> {
                company.setCompanyType(Company.CompanyType.GENERAL);
                CompanyManager.getInstance().markDirty();
                StateCraft.LOGGER.info("Shareholder vote: {} converted to general company", company.getName());
            }
            case REMOVE_OFFICER -> {
                UUID targetId = proposal.getTargetPlayerId();
                if (targetId != null && company.isOfficer(targetId) && !company.isFounder(targetId)) {
                    company.removeOfficer(targetId);
                    CompanyManager.getInstance().markDirty();
                    StateCraft.LOGGER.info("Shareholder vote: {} removed officer {}",
                        company.getName(), proposal.getTargetPlayerName());

                    // Notify removed officer
                    if (server != null) {
                        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                        if (target != null) {
                            target.sendSystemMessage(Component.literal(
                                "§c[" + company.getName() + "] §fYou have been removed as officer by shareholder vote."));
                        }
                    }
                }
            }
        }
    }

    // ==================== Queries ====================

    /**
     * Get all proposals for a company (active first, then recent resolved).
     */
    public List<ShareholderProposal> getCompanyProposals(UUID companyId) {
        return proposals.values().stream()
            .filter(p -> p.getCompanyId().equals(companyId))
            .sorted((a, b) -> {
                // Active first, then by creation time descending
                if (a.getStatus() == ShareholderProposal.Status.ACTIVE && b.getStatus() != ShareholderProposal.Status.ACTIVE)
                    return -1;
                if (b.getStatus() == ShareholderProposal.Status.ACTIVE && a.getStatus() != ShareholderProposal.Status.ACTIVE)
                    return 1;
                return Long.compare(b.getCreatedTime(), a.getCreatedTime());
            })
            .collect(Collectors.toList());
    }

    /**
     * Get active proposals for a company.
     */
    public List<ShareholderProposal> getActiveProposals(UUID companyId) {
        return proposals.values().stream()
            .filter(p -> p.getCompanyId().equals(companyId))
            .filter(p -> p.getStatus() == ShareholderProposal.Status.ACTIVE)
            .filter(p -> !p.isExpired())
            .collect(Collectors.toList());
    }

    @Nullable
    public ShareholderProposal getProposal(UUID proposalId) {
        return proposals.get(proposalId);
    }

    // ==================== Notifications ====================

    private void notifyShareholdersOfNewProposal(Company company, ShareholderProposal proposal,
                                                   @Nullable MinecraftServer server) {
        String subject = "New Proposal: " + proposal.getType().getDisplayName();
        String body = "A new shareholder proposal has been created for " + company.getName() + ".\n\n" +
            "§e" + proposal.getSummary() + "\n\n" +
            "§7Proposed by: §f" + proposal.getProposerName() + "\n" +
            "§7Voting ends in 48 hours.\n\n" +
            "§7Use the Company screen → Votes tab to cast your vote.";

        for (UUID shareholderId : company.getShareholders().keySet()) {
            if (shareholderId.equals(proposal.getProposerId())) continue;
            try {
                MailManager.getInstance().sendSystemMail(shareholderId, Mail.MailType.SYSTEM, subject, body);
            } catch (Exception e) {
                StateCraft.LOGGER.debug("Failed to send proposal notification mail: {}", e.getMessage());
            }

            // Online notification
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(shareholderId);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                        "§e[" + company.getName() + "] §fNew proposal: " + proposal.getSummary() +
                        " §7— Vote in Company > Votes tab"));
                }
            }
        }
    }

    private void notifyShareholdersOfResult(Company company, ShareholderProposal proposal,
                                              @Nullable MinecraftServer server) {
        boolean passed = proposal.getStatus() == ShareholderProposal.Status.PASSED;
        ShareholderProposal.VoteTally tally = proposal.getTally(company);

        String statusStr = passed ? "§a§lPASSED" : "§c§lFAILED";
        String subject = "Vote Result: " + proposal.getType().getDisplayName() + " — " + (passed ? "Passed" : "Failed");
        String body = "The shareholder vote on " + proposal.getSummary() + " has concluded.\n\n" +
            "§fResult: " + statusStr + "\n" +
            String.format("§7Yes: §a%.0f%% §7| No: §c%.0f%% §7| Quorum: §f%.0f%%/25%%",
                tally.yesPercentage(), tally.noPercentage(), tally.quorumPercentage());

        for (UUID shareholderId : company.getShareholders().keySet()) {
            try {
                MailManager.getInstance().sendSystemMail(shareholderId, Mail.MailType.SYSTEM, subject, body);
            } catch (Exception e) {
                StateCraft.LOGGER.debug("Failed to send vote result mail: {}", e.getMessage());
            }
        }
    }

    private void notifyCompanyMailbox(Company company, ShareholderProposal proposal) {
        boolean passed = proposal.getStatus() == ShareholderProposal.Status.PASSED;
        ShareholderProposal.VoteTally tally = proposal.getTally(company);

        String statusStr = passed ? "PASSED" : "FAILED";
        try {
            MailManager.getInstance().sendCompanySystemMail(company.getId(), Mail.MailType.SYSTEM,
                "Vote Result: " + proposal.getType().getDisplayName() + " — " + statusStr,
                proposal.getSummary() + "\n\n" +
                String.format("Yes: %.0f%% | No: %.0f%% | Quorum: %.0f%%/25%%",
                    tally.yesPercentage(), tally.noPercentage(), tally.quorumPercentage()));
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Failed to send vote result to company mailbox: {}", e.getMessage());
        }
    }

    // ==================== Cleanup ====================

    private void pruneOldProposals() {
        // Group by company, keep only MAX_HISTORY_PER_COMPANY resolved proposals
        Map<UUID, List<ShareholderProposal>> byCompany = proposals.values().stream()
            .filter(p -> p.getStatus() != ShareholderProposal.Status.ACTIVE)
            .collect(Collectors.groupingBy(ShareholderProposal::getCompanyId));

        for (Map.Entry<UUID, List<ShareholderProposal>> entry : byCompany.entrySet()) {
            List<ShareholderProposal> resolved = entry.getValue();
            if (resolved.size() > MAX_HISTORY_PER_COMPANY) {
                resolved.sort((a, b) -> Long.compare(b.getCreatedTime(), a.getCreatedTime()));
                for (int i = MAX_HISTORY_PER_COMPANY; i < resolved.size(); i++) {
                    proposals.remove(resolved.get(i).getId());
                }
            }
        }
    }

    // ==================== Persistence ====================

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag proposalsList = new ListTag();
        for (ShareholderProposal proposal : proposals.values()) {
            proposalsList.add(proposal.save());
        }
        tag.put("Proposals", proposalsList);

        // Save cooldowns
        CompoundTag cooldownsTag = new CompoundTag();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            cooldownsTag.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("Cooldowns", cooldownsTag);

        return tag;
    }

    public void load(CompoundTag tag) {
        proposals.clear();
        cooldowns.clear();

        if (tag.contains("Proposals")) {
            ListTag proposalsList = tag.getList("Proposals", Tag.TAG_COMPOUND);
            for (int i = 0; i < proposalsList.size(); i++) {
                try {
                    ShareholderProposal proposal = ShareholderProposal.load(proposalsList.getCompound(i));
                    proposals.put(proposal.getId(), proposal);
                } catch (Exception e) {
                    StateCraft.LOGGER.warn("Failed to load shareholder proposal: {}", e.getMessage());
                }
            }
        }

        if (tag.contains("Cooldowns")) {
            CompoundTag cooldownsTag = tag.getCompound("Cooldowns");
            for (String key : cooldownsTag.getAllKeys()) {
                cooldowns.put(key, cooldownsTag.getLong(key));
            }
        }

        dirty = false;
        StateCraft.LOGGER.info("Loaded {} shareholder proposals, {} cooldown entries",
            proposals.size(), cooldowns.size());
    }
}



