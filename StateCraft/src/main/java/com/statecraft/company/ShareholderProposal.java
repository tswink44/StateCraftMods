package com.statecraft.company;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a shareholder proposal that company shareholders vote on.
 * Votes are weighted by share ownership (1 share = 1 vote).
 * Requires 25% quorum (of total shares) and >50% approval (of voted non-abstain shares) to pass.
 */
public class ShareholderProposal {

    public enum ProposalType {
        SET_DIVIDEND_RATE("Set Dividend Rate"),
        ISSUE_SHARES("Issue New Shares"),
        SHARE_BUYBACK("Share Buyback"),
        DISSOLVE_COMPANY("Dissolve Company"),
        CONVERT_TO_BANK("Convert to Bank"),
        CONVERT_TO_GENERAL("Convert to General Company"),
        REMOVE_OFFICER("Remove Officer"),
        SET_DIVIDEND_PERIOD("Set Dividend Period");

        private final String displayName;
        ProposalType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public enum Vote {
        YES, NO, ABSTAIN
    }

    public enum Status {
        ACTIVE, PASSED, FAILED, EXPIRED
    }

    private final UUID id;
    private final UUID companyId;
    private final UUID proposerId;
    private final String proposerName;
    private final ProposalType type;
    private final String description;
    private Status status;
    private final long createdTime;
    private final long expiresAt;

    // Type-specific values
    private double doubleValue;       // For dividend rate (0.0-1.0)
    private int intValue;             // For share count
    private long longValue;           // For dividend period ticks
    private UUID targetPlayerId;      // For officer removal
    private String targetPlayerName;  // For officer removal display

    // Votes: playerId -> vote
    private final Map<UUID, Vote> votes = new HashMap<>();

    /** Default proposal duration: 48 hours */
    public static final long DEFAULT_DURATION_MS = 48L * 60 * 60 * 1000;

    /** Minimum quorum: 25% of total shares must have voted */
    public static final double QUORUM_THRESHOLD = 0.25;

    /** Cooldown per proposal type per company: 24 hours */
    public static final long COOLDOWN_MS = 24L * 60 * 60 * 1000;

    public ShareholderProposal(UUID companyId, UUID proposerId, String proposerName,
                                ProposalType type, String description) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.proposerId = proposerId;
        this.proposerName = proposerName;
        this.type = type;
        this.description = description;
        this.status = Status.ACTIVE;
        this.createdTime = System.currentTimeMillis();
        this.expiresAt = createdTime + DEFAULT_DURATION_MS;
    }

    private ShareholderProposal(UUID id, UUID companyId, UUID proposerId, String proposerName,
                                 ProposalType type, String description, Status status,
                                 long createdTime, long expiresAt) {
        this.id = id;
        this.companyId = companyId;
        this.proposerId = proposerId;
        this.proposerName = proposerName;
        this.type = type;
        this.description = description;
        this.status = status;
        this.createdTime = createdTime;
        this.expiresAt = expiresAt;
    }

    // ==================== Getters ====================

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getProposerId() { return proposerId; }
    public String getProposerName() { return proposerName; }
    public ProposalType getType() { return type; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public long getCreatedTime() { return createdTime; }
    public long getExpiresAt() { return expiresAt; }
    public double getDoubleValue() { return doubleValue; }
    public int getIntValue() { return intValue; }
    public long getLongValue() { return longValue; }
    public UUID getTargetPlayerId() { return targetPlayerId; }
    public String getTargetPlayerName() { return targetPlayerName; }
    public Map<UUID, Vote> getVotes() { return Collections.unmodifiableMap(votes); }

    // ==================== Setters ====================

    public void setDoubleValue(double val) { this.doubleValue = val; }
    public void setIntValue(int val) { this.intValue = val; }
    public void setLongValue(long val) { this.longValue = val; }
    public void setTargetPlayerId(UUID id) { this.targetPlayerId = id; }
    public void setTargetPlayerName(String name) { this.targetPlayerName = name; }
    public void setStatus(Status status) { this.status = status; }

    // ==================== Voting ====================

    /**
     * Cast a vote. Returns false if already voted or proposal is not active.
     */
    public boolean castVote(UUID voterId, Vote vote) {
        if (status != Status.ACTIVE) return false;
        if (votes.containsKey(voterId)) return false;
        votes.put(voterId, vote);
        return true;
    }

    public boolean hasVoted(UUID voterId) {
        return votes.containsKey(voterId);
    }

    /**
     * Resolve the proposal result based on share-weighted votes.
     * @param company The company to check share weights against
     * @return true if the proposal passed
     */
    public boolean resolve(Company company) {
        if (status != Status.ACTIVE) return false;

        int totalShares = company.getTotalShares();
        if (totalShares <= 0) {
            status = Status.FAILED;
            return false;
        }

        int sharesVoted = 0;
        int sharesYes = 0;
        int sharesNo = 0;

        for (Map.Entry<UUID, Vote> entry : votes.entrySet()) {
            int voterShares = company.getShareCount(entry.getKey());
            sharesVoted += voterShares;
            switch (entry.getValue()) {
                case YES -> sharesYes += voterShares;
                case NO -> sharesNo += voterShares;
                // ABSTAIN counts toward quorum but not yes/no
            }
        }

        // Check quorum: 25% of total shares must have voted
        double quorumPercentage = (double) sharesVoted / totalShares;
        if (quorumPercentage < QUORUM_THRESHOLD) {
            status = Status.FAILED;
            return false;
        }

        // Check majority: >50% of voted (non-abstain) shares must be YES
        int decisiveShares = sharesYes + sharesNo;
        if (decisiveShares <= 0) {
            status = Status.FAILED;
            return false;
        }

        boolean passed = (double) sharesYes / decisiveShares > 0.5;
        status = passed ? Status.PASSED : Status.FAILED;
        return passed;
    }

    /**
     * Get current vote tallies weighted by shares.
     */
    public VoteTally getTally(Company company) {
        int totalShares = company.getTotalShares();
        int sharesVoted = 0;
        int sharesYes = 0;
        int sharesNo = 0;
        int sharesAbstain = 0;

        for (Map.Entry<UUID, Vote> entry : votes.entrySet()) {
            int voterShares = company.getShareCount(entry.getKey());
            sharesVoted += voterShares;
            switch (entry.getValue()) {
                case YES -> sharesYes += voterShares;
                case NO -> sharesNo += voterShares;
                case ABSTAIN -> sharesAbstain += voterShares;
            }
        }

        return new VoteTally(sharesYes, sharesNo, sharesAbstain, sharesVoted, totalShares);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Get a human-readable summary of this proposal.
     */
    public String getSummary() {
        return switch (type) {
            case SET_DIVIDEND_RATE -> String.format("Set dividend rate to %.1f%%", doubleValue * 100);
            case ISSUE_SHARES -> "Issue " + intValue + " new shares to founder";
            case SHARE_BUYBACK -> "Buy back " + intValue + " shares from treasury";
            case DISSOLVE_COMPANY -> "Dissolve the company";
            case CONVERT_TO_BANK -> "Convert company to a bank";
            case CONVERT_TO_GENERAL -> "Convert bank to a general company";
            case REMOVE_OFFICER -> "Remove officer: " + (targetPlayerName != null ? targetPlayerName : "Unknown");
            case SET_DIVIDEND_PERIOD -> {
                long hours = longValue / 72000;
                yield "Set dividend period to " + hours + " hour" + (hours != 1 ? "s" : "");
            }
        };
    }

    // ==================== Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("CompanyId", companyId);
        tag.putUUID("ProposerId", proposerId);
        tag.putString("ProposerName", proposerName);
        tag.putString("Type", type.name());
        tag.putString("Description", description);
        tag.putString("Status", status.name());
        tag.putLong("CreatedTime", createdTime);
        tag.putLong("ExpiresAt", expiresAt);
        tag.putDouble("DoubleValue", doubleValue);
        tag.putInt("IntValue", intValue);
        tag.putLong("LongValue", longValue);
        if (targetPlayerId != null) {
            tag.putUUID("TargetPlayerId", targetPlayerId);
        }
        if (targetPlayerName != null) {
            tag.putString("TargetPlayerName", targetPlayerName);
        }

        ListTag votesList = new ListTag();
        for (Map.Entry<UUID, Vote> entry : votes.entrySet()) {
            CompoundTag vTag = new CompoundTag();
            vTag.putUUID("Voter", entry.getKey());
            vTag.putString("Vote", entry.getValue().name());
            votesList.add(vTag);
        }
        tag.put("Votes", votesList);

        return tag;
    }

    public static ShareholderProposal load(CompoundTag tag) {
        ShareholderProposal proposal = new ShareholderProposal(
            tag.getUUID("Id"),
            tag.getUUID("CompanyId"),
            tag.getUUID("ProposerId"),
            tag.getString("ProposerName"),
            ProposalType.valueOf(tag.getString("Type")),
            tag.getString("Description"),
            Status.valueOf(tag.getString("Status")),
            tag.getLong("CreatedTime"),
            tag.getLong("ExpiresAt")
        );

        proposal.doubleValue = tag.getDouble("DoubleValue");
        proposal.intValue = tag.getInt("IntValue");
        proposal.longValue = tag.getLong("LongValue");
        if (tag.contains("TargetPlayerId")) {
            proposal.targetPlayerId = tag.getUUID("TargetPlayerId");
        }
        if (tag.contains("TargetPlayerName")) {
            proposal.targetPlayerName = tag.getString("TargetPlayerName");
        }

        if (tag.contains("Votes")) {
            ListTag votesList = tag.getList("Votes", Tag.TAG_COMPOUND);
            for (int i = 0; i < votesList.size(); i++) {
                CompoundTag vTag = votesList.getCompound(i);
                proposal.votes.put(
                    vTag.getUUID("Voter"),
                    Vote.valueOf(vTag.getString("Vote"))
                );
            }
        }

        return proposal;
    }

    // ==================== Inner Types ====================

    public record VoteTally(int sharesYes, int sharesNo, int sharesAbstain,
                             int sharesVoted, int totalShares) {
        public double yesPercentage() {
            int decisive = sharesYes + sharesNo;
            return decisive > 0 ? (double) sharesYes / decisive * 100 : 0;
        }
        public double noPercentage() {
            int decisive = sharesYes + sharesNo;
            return decisive > 0 ? (double) sharesNo / decisive * 100 : 0;
        }
        public double quorumPercentage() {
            return totalShares > 0 ? (double) sharesVoted / totalShares * 100 : 0;
        }
        public boolean hasQuorum() {
            return totalShares > 0 && (double) sharesVoted / totalShares >= QUORUM_THRESHOLD;
        }
    }
}

