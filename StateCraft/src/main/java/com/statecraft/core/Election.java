package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents an election for nation leadership
 * Vote counts are hidden until the election is completed
 */
public class Election {

    public enum Status {
        SCHEDULED,  // Election is scheduled but not yet started
        ACTIVE,     // Voting is open
        COMPLETED   // Voting has ended, results are final
    }

    private final UUID electionId;
    private final UUID nationId;
    private UUID incumbentId;  // Current leader when election started (wins ties)
    private final Map<UUID, String> candidates;  // candidateId -> candidateName
    private final Map<UUID, UUID> votes;  // voterId -> candidateId
    private long startTime;
    private long endTime;
    private Status status;
    private UUID winnerId;
    private String winnerName;
    private int winnerVoteCount;
    private int totalVoters;

    public Election(UUID nationId, UUID incumbentId) {
        this.electionId = UUID.randomUUID();
        this.nationId = nationId;
        this.incumbentId = incumbentId;
        this.candidates = new HashMap<>();
        this.votes = new HashMap<>();
        this.status = Status.SCHEDULED;
        this.startTime = 0;
        this.endTime = 0;
    }

    // Private constructor for NBT loading - use loadFromNbt instead
    private Election(UUID electionId, UUID nationId, boolean fromNbt) {
        this.electionId = electionId;
        this.nationId = nationId;
        this.candidates = new HashMap<>();
        this.votes = new HashMap<>();
    }

    public UUID getElectionId() {
        return electionId;
    }

    public UUID getNationId() {
        return nationId;
    }

    public UUID getIncumbentId() {
        return incumbentId;
    }

    public void setIncumbentId(UUID incumbentId) {
        this.incumbentId = incumbentId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public int getWinnerVoteCount() {
        return winnerVoteCount;
    }

    public int getTotalVoters() {
        return totalVoters;
    }

    /**
     * Register a candidate for the election
     * @return true if successfully registered
     */
    public boolean addCandidate(UUID playerId, String playerName) {
        if (status != Status.SCHEDULED && status != Status.ACTIVE) {
            return false;  // Can only add candidates before/during election
        }
        if (candidates.containsKey(playerId)) {
            return false;  // Already a candidate
        }
        candidates.put(playerId, playerName);
        return true;
    }

    public boolean isCandidate(UUID playerId) {
        return candidates.containsKey(playerId);
    }

    public Map<UUID, String> getCandidates() {
        return Collections.unmodifiableMap(candidates);
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    /**
     * Cast a vote for a candidate
     * @return true if vote was successfully cast
     */
    public boolean castVote(UUID voterId, UUID candidateId) {
        if (status != Status.ACTIVE) {
            return false;  // Can only vote during active election
        }
        if (!candidates.containsKey(candidateId)) {
            return false;  // Invalid candidate
        }
        if (votes.containsKey(voterId)) {
            return false;  // Already voted
        }
        votes.put(voterId, candidateId);
        return true;
    }

    public boolean hasVoted(UUID voterId) {
        return votes.containsKey(voterId);
    }

    /**
     * Get total number of votes cast (doesn't reveal individual counts)
     */
    public int getVoteCount() {
        return votes.size();
    }

    /**
     * Check if election voting period is currently active based on time
     */
    public boolean isVotingOpen() {
        if (status != Status.ACTIVE) return false;
        long now = System.currentTimeMillis();
        return now >= startTime && now < endTime;
    }

    /**
     * Get remaining time in milliseconds
     */
    public long getRemainingTime() {
        if (status != Status.ACTIVE) return 0;
        long now = System.currentTimeMillis();
        return Math.max(0, endTime - now);
    }

    /**
     * Calculate and finalize results - only call when ending election
     * Incumbent wins in case of a tie
     */
    public void finalizeResults() {
        if (status == Status.COMPLETED) return;

        status = Status.COMPLETED;
        totalVoters = votes.size();

        // Count votes per candidate
        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID candidateId : candidates.keySet()) {
            voteCounts.put(candidateId, 0);
        }
        for (UUID candidateId : votes.values()) {
            voteCounts.merge(candidateId, 1, Integer::sum);
        }

        // Find winner (incumbent wins ties)
        UUID winner = null;
        int maxVotes = -1;

        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            UUID candidateId = entry.getKey();
            int voteCount = entry.getValue();

            if (voteCount > maxVotes) {
                maxVotes = voteCount;
                winner = candidateId;
            } else if (voteCount == maxVotes && candidateId.equals(incumbentId)) {
                // Tie goes to incumbent
                winner = candidateId;
            }
        }

        // If no candidates or no votes, incumbent stays
        if (winner == null) {
            winner = incumbentId;
            maxVotes = 0;
        }

        this.winnerId = winner;
        this.winnerName = candidates.getOrDefault(winner, "Unknown");
        this.winnerVoteCount = maxVotes;
    }

    /**
     * Get full results - only available after election is completed
     */
    public Map<UUID, Integer> getResults() {
        if (status != Status.COMPLETED) {
            return Collections.emptyMap();  // Results hidden until completed
        }

        Map<UUID, Integer> results = new HashMap<>();
        for (UUID candidateId : candidates.keySet()) {
            results.put(candidateId, 0);
        }
        for (UUID candidateId : votes.values()) {
            results.merge(candidateId, 1, Integer::sum);
        }
        return results;
    }

    // ==================== NBT Serialization ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("electionId", electionId);
        tag.putUUID("nationId", nationId);
        if (incumbentId != null) {
            tag.putUUID("incumbentId", incumbentId);
        }
        tag.putString("status", status.name());
        tag.putLong("startTime", startTime);
        tag.putLong("endTime", endTime);

        // Save candidates
        ListTag candidatesList = new ListTag();
        for (Map.Entry<UUID, String> entry : candidates.entrySet()) {
            CompoundTag candidateTag = new CompoundTag();
            candidateTag.putUUID("id", entry.getKey());
            candidateTag.putString("name", entry.getValue());
            candidatesList.add(candidateTag);
        }
        tag.put("candidates", candidatesList);

        // Save votes
        ListTag votesList = new ListTag();
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            CompoundTag voteTag = new CompoundTag();
            voteTag.putUUID("voter", entry.getKey());
            voteTag.putUUID("candidate", entry.getValue());
            votesList.add(voteTag);
        }
        tag.put("votes", votesList);

        // Save results if completed
        if (status == Status.COMPLETED) {
            if (winnerId != null) {
                tag.putUUID("winnerId", winnerId);
            }
            tag.putString("winnerName", winnerName != null ? winnerName : "");
            tag.putInt("winnerVoteCount", winnerVoteCount);
            tag.putInt("totalVoters", totalVoters);
        }

        return tag;
    }

    public static Election load(CompoundTag tag) {
        UUID electionId = tag.getUUID("electionId");
        UUID nationId = tag.getUUID("nationId");

        Election election = new Election(electionId, nationId, true);

        if (tag.contains("incumbentId")) {
            election.incumbentId = tag.getUUID("incumbentId");
        }
        election.status = Status.valueOf(tag.getString("status"));
        election.startTime = tag.getLong("startTime");
        election.endTime = tag.getLong("endTime");

        // Load candidates
        ListTag candidatesList = tag.getList("candidates", Tag.TAG_COMPOUND);
        for (int i = 0; i < candidatesList.size(); i++) {
            CompoundTag candidateTag = candidatesList.getCompound(i);
            election.candidates.put(
                candidateTag.getUUID("id"),
                candidateTag.getString("name")
            );
        }

        // Load votes
        ListTag votesList = tag.getList("votes", Tag.TAG_COMPOUND);
        for (int i = 0; i < votesList.size(); i++) {
            CompoundTag voteTag = votesList.getCompound(i);
            election.votes.put(
                voteTag.getUUID("voter"),
                voteTag.getUUID("candidate")
            );
        }

        // Load results if completed
        if (election.status == Status.COMPLETED) {
            if (tag.contains("winnerId")) {
                election.winnerId = tag.getUUID("winnerId");
            }
            election.winnerName = tag.getString("winnerName");
            election.winnerVoteCount = tag.getInt("winnerVoteCount");
            election.totalVoters = tag.getInt("totalVoters");
        }

        return election;
    }
}



