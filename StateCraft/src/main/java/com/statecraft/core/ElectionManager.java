package com.statecraft.core;

import com.statecraft.StateCraft;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.integration.IntegrationRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages elections for all nations
 * Handles scheduling, voting, results, and history
 */
public class ElectionManager {

    private static final int MAX_HISTORY_SIZE = 5;
    private static ElectionManager instance;

    // Active/scheduled elections per nation
    private final Map<UUID, Election> activeElections = new ConcurrentHashMap<>();

    // Next scheduled election time per nation
    private final Map<UUID, Long> nextElectionTimes = new ConcurrentHashMap<>();

    // Election history per nation (last 5 results)
    private final Map<UUID, LinkedList<ElectionResult>> electionHistory = new ConcurrentHashMap<>();

    private boolean dirty = false;

    public static ElectionManager getInstance() {
        if (instance == null) {
            instance = new ElectionManager();
        }
        return instance;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * Called every server tick to check election timings
     */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Check for elections that need to start
        for (Map.Entry<UUID, Long> entry : nextElectionTimes.entrySet()) {
            UUID nationId = entry.getKey();
            long nextTime = entry.getValue();

            if (now >= nextTime && !activeElections.containsKey(nationId)) {
                // Time to start an election
                Nation nation = ChunkClaimManager.getInstance().getNation(nationId);
                if (nation != null && StateCraftConfig.ENABLE_NATION_ELECTIONS.get()) {
                    startElection(nation, server);
                }
            }
        }

        // Check for elections that need to end
        List<UUID> electionsToEnd = new ArrayList<>();
        for (Map.Entry<UUID, Election> entry : activeElections.entrySet()) {
            Election election = entry.getValue();
            if (election.getStatus() == Election.Status.ACTIVE && now >= election.getEndTime()) {
                electionsToEnd.add(entry.getKey());
            }
        }

        for (UUID nationId : electionsToEnd) {
            Nation nation = ChunkClaimManager.getInstance().getNation(nationId);
            if (nation != null) {
                endElection(nation, server, false);
            }
        }
    }

    /**
     * Start a new election for a nation
     */
    public Election startElection(Nation nation, MinecraftServer server) {
        UUID nationId = nation.getId();

        // Don't start if one is already active
        if (activeElections.containsKey(nationId)) {
            Election existing = activeElections.get(nationId);
            if (existing.getStatus() == Election.Status.ACTIVE) {
                return existing;
            }
        }

        Election election = new Election(nationId, nation.getLeaderId());

        long now = System.currentTimeMillis();
        // Use nation's constitutional election duration (in days), fall back to config hours if not set
        int durationDays = nation.getElectionDurationDays();
        long durationMs = durationDays * 24L * 60L * 60L * 1000L;

        election.setStartTime(now);
        election.setEndTime(now + durationMs);
        election.setStatus(Election.Status.ACTIVE);

        // Auto-register incumbent as candidate
        String leaderName = getPlayerName(server, nation.getLeaderId());
        election.addCandidate(nation.getLeaderId(), leaderName);

        activeElections.put(nationId, election);
        markDirty();

        // Notify all online nation members
        notifyNationMembers(nation, server,
            Component.literal("§6[Election] §eVoting has begun for " + nation.getName() + " leadership! Use §f/sc election§e to participate."));

        StateCraft.LOGGER.info("Election started for nation '{}' - ends in {} day(s)", nation.getName(), durationDays);

        return election;
    }

    /**
     * End an election and apply results
     */
    public void endElection(Nation nation, MinecraftServer server, boolean forced) {
        UUID nationId = nation.getId();
        Election election = activeElections.get(nationId);

        if (election == null || election.getStatus() == Election.Status.COMPLETED) {
            return;
        }

        // Finalize results
        election.finalizeResults();

        // Apply winner as new leader
        UUID winnerId = election.getWinnerId();
        if (winnerId != null) {
            nation.setLeaderId(winnerId);
        }

        // Store in history
        ElectionResult result = new ElectionResult(
            election.getWinnerId(),
            election.getWinnerName(),
            System.currentTimeMillis(),
            election.getWinnerVoteCount(),
            election.getTotalVoters()
        );
        addToHistory(nationId, result);

        // Schedule next election
        scheduleNextElection(nationId);

        // Remove from active
        activeElections.remove(nationId);
        markDirty();

        // Notify all online nation members
        String message = forced ?
            "§6[Election] §eElection ended early by admin. " :
            "§6[Election] §eVoting has ended! ";
        message += "§a" + election.getWinnerName() + "§e is the new leader of " + nation.getName() +
            " with §f" + election.getWinnerVoteCount() + "§e votes!";

        notifyNationMembers(nation, server, Component.literal(message));

        StateCraft.LOGGER.info("Election ended for nation '{}' - winner: {} with {} votes",
            nation.getName(), election.getWinnerName(), election.getWinnerVoteCount());
    }

    /**
     * Register a player as a candidate
     * @return null if successful, error message otherwise
     */
    public String registerCandidate(Nation nation, UUID playerId, String playerName, MinecraftServer server) {
        UUID nationId = nation.getId();
        Election election = activeElections.get(nationId);

        if (election == null || election.getStatus() != Election.Status.ACTIVE) {
            return "No active election for this nation";
        }

        if (!nation.isMember(playerId) && !nation.isAdmin(playerId) && !nation.getLeaderId().equals(playerId)) {
            // Check if citizen of any city
            boolean isCitizen = false;
            for (State state : nation.getAllStates()) {
                if (state.isCitizen(playerId)) {
                    isCitizen = true;
                    break;
                }
            }
            if (!isCitizen) {
                return "Only citizens of this nation can run for election";
            }
        }

        if (election.isCandidate(playerId)) {
            return "You are already registered as a candidate";
        }

        // Check and charge candidate fee
        double fee = StateCraftConfig.ELECTION_CANDIDATE_FEE.get();
        if (fee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
            double balance = IntegrationRegistry.getPlayerBalance(playerId);
            if (balance < fee) {
                return "Insufficient funds! Candidate registration costs " +
                    IntegrationRegistry.formatCurrency(fee) + ". You have " +
                    IntegrationRegistry.formatCurrency(balance) + ".";
            }

            boolean charged = IntegrationRegistry.withdrawFromPlayer(playerId, fee,
                "Election candidate fee for " + nation.getName());
            if (!charged) {
                return "Failed to charge candidate fee";
            }
        }

        if (!election.addCandidate(playerId, playerName)) {
            return "Failed to register as candidate";
        }

        markDirty();

        // Notify nation
        notifyNationMembers(nation, server,
            Component.literal("§6[Election] §a" + playerName + "§e has registered as a candidate!"));

        return null;  // Success
    }

    /**
     * Cast a vote in an election
     * @return null if successful, error message otherwise
     */
    public String castVote(Nation nation, UUID voterId, UUID candidateId) {
        UUID nationId = nation.getId();
        Election election = activeElections.get(nationId);

        if (election == null || election.getStatus() != Election.Status.ACTIVE) {
            return "No active election for this nation";
        }

        if (!election.isVotingOpen()) {
            return "Voting period has ended";
        }

        // Check if voter is a citizen
        if (!isNationCitizen(nation, voterId)) {
            return "Only citizens of this nation can vote";
        }

        if (election.hasVoted(voterId)) {
            return "You have already voted in this election";
        }

        if (!election.getCandidates().containsKey(candidateId)) {
            return "Invalid candidate";
        }

        if (!election.castVote(voterId, candidateId)) {
            return "Failed to cast vote";
        }

        markDirty();
        return null;  // Success
    }

    /**
     * Force set a nation's leader (admin command)
     */
    public void forceSetLeader(Nation nation, UUID newLeaderId, String newLeaderName, MinecraftServer server) {
        // Cancel any active election
        UUID nationId = nation.getId();
        if (activeElections.containsKey(nationId)) {
            activeElections.remove(nationId);
        }

        nation.setLeaderId(newLeaderId);

        // Add to history as admin action
        ElectionResult result = new ElectionResult(
            newLeaderId,
            newLeaderName + " (Admin Appointed)",
            System.currentTimeMillis(),
            0,
            0
        );
        addToHistory(nationId, result);

        markDirty();

        notifyNationMembers(nation, server,
            Component.literal("§6[Admin] §e" + newLeaderName + " has been appointed as leader of " + nation.getName()));
    }

    /**
     * Schedule the next automatic election for a nation
     * Uses the nation's constitutional leader term duration
     */
    public void scheduleNextElection(UUID nationId) {
        // Check if elections are enabled globally
        if (!StateCraftConfig.ENABLE_NATION_ELECTIONS.get()) {
            nextElectionTimes.remove(nationId);
            return;
        }

        // Get the nation to read its constitutional settings
        Nation nation = ChunkClaimManager.getInstance().getNation(nationId);
        int termDays;
        if (nation != null) {
            termDays = nation.getLeaderTermDays();
        } else {
            // Fallback to config if nation not found
            termDays = StateCraftConfig.ELECTION_INTERVAL_DAYS.get();
        }

        if (termDays <= 0) {
            nextElectionTimes.remove(nationId);
            return;
        }

        long nextTime = System.currentTimeMillis() + (termDays * 24L * 60L * 60L * 1000L);
        nextElectionTimes.put(nationId, nextTime);
        markDirty();
    }

    /**
     * Get active election for a nation
     */
    public Election getActiveElection(UUID nationId) {
        return activeElections.get(nationId);
    }

    /**
     * Get next scheduled election time
     */
    public Long getNextElectionTime(UUID nationId) {
        return nextElectionTimes.get(nationId);
    }

    /**
     * Get election history for a nation
     */
    public List<ElectionResult> getElectionHistory(UUID nationId) {
        LinkedList<ElectionResult> history = electionHistory.get(nationId);
        if (history == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(history);
    }

    private void addToHistory(UUID nationId, ElectionResult result) {
        LinkedList<ElectionResult> history = electionHistory.computeIfAbsent(nationId, k -> new LinkedList<>());
        history.addFirst(result);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeLast();
        }
    }

    /**
     * Check if a player is a citizen of a nation (member, admin, leader, or city resident)
     */
    private boolean isNationCitizen(Nation nation, UUID playerId) {
        if (nation.getLeaderId().equals(playerId)) return true;
        if (nation.isAdmin(playerId)) return true;
        if (nation.isMember(playerId)) return true;

        for (State state : nation.getAllStates()) {
            if (state.getGovernorId().equals(playerId)) return true;
            if (state.isCitizen(playerId)) return true;
            for (City city : state.getAllCities()) {
                if (city.getMayorId().equals(playerId)) return true;
                if (city.isResident(playerId)) return true;
            }
        }
        return false;
    }

    /**
     * Get all citizens of a nation (for voter count display)
     */
    public Set<UUID> getAllNationCitizens(Nation nation) {
        Set<UUID> citizens = new HashSet<>();
        citizens.add(nation.getLeaderId());
        citizens.addAll(nation.getAdmins());
        citizens.addAll(nation.getMembers());

        for (State state : nation.getAllStates()) {
            citizens.add(state.getGovernorId());
            for (City city : state.getAllCities()) {
                citizens.add(city.getMayorId());
                citizens.addAll(city.getResidents());
            }
        }
        return citizens;
    }

    /**
     * Notify all online nation members
     */
    private void notifyNationMembers(Nation nation, MinecraftServer server, Component message) {
        if (server == null) return;

        Set<UUID> citizens = getAllNationCitizens(nation);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (citizens.contains(player.getUUID())) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * Called when a player logs in - notify of active elections
     */
    public void onPlayerLogin(ServerPlayer player, MinecraftServer server) {
        Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
        if (nation == null) return;

        Election election = activeElections.get(nation.getId());
        if (election != null && election.getStatus() == Election.Status.ACTIVE) {
            long remaining = election.getRemainingTime();
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);

            player.sendSystemMessage(Component.literal(
                "§6[Election] §eAn election is in progress for " + nation.getName() +
                "! §f" + hours + "h " + minutes + "m§e remaining. Use §f/sc election§e to vote."));

            if (!election.hasVoted(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§6[Election] §cYou have not voted yet!"));
            }
        }
    }

    private String getPlayerName(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return "Unknown";
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        // Try to get from user cache
        var profile = server.getProfileCache().get(playerId);
        return profile.map(p -> p.getName()).orElse("Unknown");
    }

    // ==================== NBT Serialization ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Save active elections
        ListTag electionsList = new ListTag();
        for (Election election : activeElections.values()) {
            electionsList.add(election.save());
        }
        tag.put("activeElections", electionsList);

        // Save next election times
        ListTag nextTimesList = new ListTag();
        for (Map.Entry<UUID, Long> entry : nextElectionTimes.entrySet()) {
            CompoundTag timeTag = new CompoundTag();
            timeTag.putUUID("nationId", entry.getKey());
            timeTag.putLong("time", entry.getValue());
            nextTimesList.add(timeTag);
        }
        tag.put("nextElectionTimes", nextTimesList);

        // Save history
        ListTag historyList = new ListTag();
        for (Map.Entry<UUID, LinkedList<ElectionResult>> entry : electionHistory.entrySet()) {
            CompoundTag nationHistory = new CompoundTag();
            nationHistory.putUUID("nationId", entry.getKey());
            ListTag resultsList = new ListTag();
            for (ElectionResult result : entry.getValue()) {
                resultsList.add(result.save());
            }
            nationHistory.put("results", resultsList);
            historyList.add(nationHistory);
        }
        tag.put("electionHistory", historyList);

        return tag;
    }

    public void load(CompoundTag tag) {
        activeElections.clear();
        nextElectionTimes.clear();
        electionHistory.clear();

        // Load active elections
        ListTag electionsList = tag.getList("activeElections", Tag.TAG_COMPOUND);
        for (int i = 0; i < electionsList.size(); i++) {
            Election election = Election.load(electionsList.getCompound(i));
            activeElections.put(election.getNationId(), election);
        }

        // Load next election times
        ListTag nextTimesList = tag.getList("nextElectionTimes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nextTimesList.size(); i++) {
            CompoundTag timeTag = nextTimesList.getCompound(i);
            nextElectionTimes.put(timeTag.getUUID("nationId"), timeTag.getLong("time"));
        }

        // Load history
        ListTag historyList = tag.getList("electionHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyList.size(); i++) {
            CompoundTag nationHistory = historyList.getCompound(i);
            UUID nationId = nationHistory.getUUID("nationId");
            LinkedList<ElectionResult> results = new LinkedList<>();
            ListTag resultsList = nationHistory.getList("results", Tag.TAG_COMPOUND);
            for (int j = 0; j < resultsList.size(); j++) {
                results.add(ElectionResult.load(resultsList.getCompound(j)));
            }
            electionHistory.put(nationId, results);
        }
    }

    /**
     * Initialize elections for a newly created nation
     */
    public void onNationCreated(UUID nationId) {
        if (StateCraftConfig.ENABLE_NATION_ELECTIONS.get() &&
            StateCraftConfig.ELECTION_INTERVAL_DAYS.get() > 0) {
            scheduleNextElection(nationId);
        }
    }

    /**
     * Clean up when a nation is disbanded
     */
    public void onNationDisbanded(UUID nationId) {
        activeElections.remove(nationId);
        nextElectionTimes.remove(nationId);
        electionHistory.remove(nationId);
        markDirty();
    }
}


