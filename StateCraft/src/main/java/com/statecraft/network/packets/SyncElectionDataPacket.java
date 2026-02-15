package com.statecraft.network.packets;

import com.statecraft.core.Election;
import com.statecraft.core.ElectionResult;
import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

/**
 * Syncs election data from server to client
 */
public class SyncElectionDataPacket {

    private final boolean hasActiveElection;
    private final String nationName;
    private final Election.Status status;
    private final long endTime;
    private final List<CandidateInfo> candidates;
    private final int totalVotes;
    private final boolean hasVoted;
    private final long nextElectionTime;
    private final List<HistoryEntry> history;

    // For completed elections only
    private final String winnerName;
    private final Map<UUID, Integer> results;

    public record CandidateInfo(UUID id, String name, boolean isIncumbent) {}
    public record HistoryEntry(String winnerName, int voteCount, int totalVoters, long timestamp) {}

    public SyncElectionDataPacket(
            boolean hasActiveElection,
            String nationName,
            Election.Status status,
            long endTime,
            List<CandidateInfo> candidates,
            int totalVotes,
            boolean hasVoted,
            long nextElectionTime,
            List<HistoryEntry> history,
            String winnerName,
            Map<UUID, Integer> results
    ) {
        this.hasActiveElection = hasActiveElection;
        this.nationName = nationName;
        this.status = status;
        this.endTime = endTime;
        this.candidates = candidates;
        this.totalVotes = totalVotes;
        this.hasVoted = hasVoted;
        this.nextElectionTime = nextElectionTime;
        this.history = history;
        this.winnerName = winnerName;
        this.results = results;
    }

    public SyncElectionDataPacket(FriendlyByteBuf buf) {
        this.hasActiveElection = buf.readBoolean();
        this.nationName = buf.readUtf();
        this.status = buf.readEnum(Election.Status.class);
        this.endTime = buf.readLong();

        int candidateCount = buf.readInt();
        this.candidates = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            candidates.add(new CandidateInfo(
                buf.readUUID(),
                buf.readUtf(),
                buf.readBoolean()
            ));
        }

        this.totalVotes = buf.readInt();
        this.hasVoted = buf.readBoolean();
        this.nextElectionTime = buf.readLong();

        int historyCount = buf.readInt();
        this.history = new ArrayList<>();
        for (int i = 0; i < historyCount; i++) {
            history.add(new HistoryEntry(
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readLong()
            ));
        }

        this.winnerName = buf.readUtf();

        int resultsCount = buf.readInt();
        this.results = new HashMap<>();
        for (int i = 0; i < resultsCount; i++) {
            results.put(buf.readUUID(), buf.readInt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hasActiveElection);
        buf.writeUtf(nationName);
        buf.writeEnum(status);
        buf.writeLong(endTime);

        buf.writeInt(candidates.size());
        for (CandidateInfo candidate : candidates) {
            buf.writeUUID(candidate.id());
            buf.writeUtf(candidate.name());
            buf.writeBoolean(candidate.isIncumbent());
        }

        buf.writeInt(totalVotes);
        buf.writeBoolean(hasVoted);
        buf.writeLong(nextElectionTime);

        buf.writeInt(history.size());
        for (HistoryEntry entry : history) {
            buf.writeUtf(entry.winnerName());
            buf.writeInt(entry.voteCount());
            buf.writeInt(entry.totalVoters());
            buf.writeLong(entry.timestamp());
        }

        buf.writeUtf(winnerName != null ? winnerName : "");

        buf.writeInt(results.size());
        for (Map.Entry<UUID, Integer> entry : results.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    // Getters
    public boolean hasActiveElection() { return hasActiveElection; }
    public String getNationName() { return nationName; }
    public Election.Status getStatus() { return status; }
    public long getEndTime() { return endTime; }
    public List<CandidateInfo> getCandidates() { return candidates; }
    public int getTotalVotes() { return totalVotes; }
    public boolean hasVoted() { return hasVoted; }
    public long getNextElectionTime() { return nextElectionTime; }
    public List<HistoryEntry> getHistory() { return history; }
    public String getWinnerName() { return winnerName; }
    public Map<UUID, Integer> getResults() { return results; }
}

