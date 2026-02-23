package com.statecraft.core;

import com.statecraft.StateCraft;
import com.statecraft.data.NationSavedData;
import com.statecraft.mail.Mail;
import com.statecraft.mail.MailManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central manager for all diplomatic actions between nations.
 * Ensures all diplomacy is bilateral (both sides updated).
 *
 * - War / Break Alliance: immediate bilateral effect
 * - Peace / Alliance: proposal/acceptance workflow (leader-only)
 * - 48-hour truce after peace acceptance prevents re-declaring war
 * - Configurable max outbound proposals per nation (default 5)
 */
public class DiplomacyManager {

    private static DiplomacyManager instance;

    /** Pending diplomatic proposals (peace / alliance). Key = proposal UUID */
    private final Map<UUID, DiplomacyProposal> proposals = new ConcurrentHashMap<>();

    /** Active truces. Key = truce UUID */
    private final Map<UUID, Truce> truces = new ConcurrentHashMap<>();

    /** Pending treaty ratifications awaiting legislature approval. Key = ratification UUID */
    private final Map<UUID, PendingTreatyRatification> pendingRatifications = new ConcurrentHashMap<>();

    /** Default max outbound proposals per nation */
    private int maxProposalsPerNation = 5;

    /** Truce duration in milliseconds (48 hours) */
    private static final long TRUCE_DURATION_MS = 48L * 60 * 60 * 1000;

    /** Proposal expiry duration in milliseconds (7 days) */
    private static final long PROPOSAL_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    private boolean dirty = false;

    private DiplomacyManager() {}

    public static DiplomacyManager getInstance() {
        if (instance == null) {
            instance = new DiplomacyManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public void setMaxProposalsPerNation(int max) {
        this.maxProposalsPerNation = max;
    }

    public int getMaxProposalsPerNation() {
        return maxProposalsPerNation;
    }

    // ==================== War ====================

    /**
     * Declare war between two nations. Immediate bilateral effect.
     *
     * @param declarer  The nation declaring war
     * @param target    The target nation
     * @param server    The server instance (for notifications)
     * @param bypassTruce If true, ignores active truces (used by DIPLOMATIC_CRISIS emergency power)
     * @return A result message
     */
    public String declareWar(Nation declarer, Nation target, MinecraftServer server, boolean bypassTruce) {
        if (declarer.getId().equals(target.getId())) {
            return "§cCannot declare war on yourself.";
        }

        if (declarer.isEnemy(target.getId())) {
            return "§cYou are already at war with " + target.getName() + ".";
        }

        // Check truce
        if (!bypassTruce && isUnderTruce(declarer.getId(), target.getId())) {
            Truce truce = getActiveTruce(declarer.getId(), target.getId());
            if (truce != null) {
                long remaining = truce.expiresAt - System.currentTimeMillis();
                long hours = remaining / (1000 * 60 * 60);
                long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
                return "§cA truce with " + target.getName() + " is active. " + hours + "h " + minutes + "m remaining.";
            }
        }

        // Remove any pending alliance/peace proposals between these nations
        removeProposalsBetween(declarer.getId(), target.getId());

        // Bilateral: both sides become enemies
        declarer.addEnemy(target.getId());
        target.addEnemy(declarer.getId());
        ChunkClaimManager.getInstance().markDirty();
        markDirty();

        // Notify all members of both nations
        String warMsg = "§c§l[WAR DECLARATION] §e" + declarer.getName() + " has declared war on " + target.getName() + "!";
        notifyNationMembers(declarer, server, warMsg);
        notifyNationMembers(target, server, warMsg);

        // Send mail to both leaders
        sendDiplomacyMail(declarer.getLeaderId(), "War Declared on " + target.getName(),
            "Your nation has declared war on " + target.getName() + ".\n\n" +
            "Enemy citizens are now blocked from your territory and PvP is enabled between your nations.");
        sendDiplomacyMail(target.getLeaderId(), "War Declaration from " + declarer.getName(),
            "The nation of " + declarer.getName() + " has declared war on " + target.getName() + ".\n\n" +
            "Their citizens are now blocked from your territory and PvP is enabled between your nations.");

        StateCraft.LOGGER.info("War declared: {} vs {}", declarer.getName(), target.getName());
        return "§c§lWar declared on " + target.getName() + "! §eBoth nations are now enemies.";
    }

    // ==================== Peace ====================

    /**
     * Propose peace with no terms (backward compatible shortcut).
     */
    public String proposePeace(Nation proposer, Nation target, MinecraftServer server) {
        return proposePeaceWithTerms(proposer, target, server, 0, new ArrayList<>(), null);
    }

    /**
     * Propose peace with negotiated terms (currency demand, chunk demands).
     *
     * @param currencyDemand  Amount the proposer demands from the target's treasury
     * @param chunkDemands    Chunks the proposer demands from the target
     * @param receivingCityId City in the proposer's nation to receive demanded chunks (nullable if no chunks)
     * @return A result message
     */
    public String proposePeaceWithTerms(Nation proposer, Nation target, MinecraftServer server,
                                         double currencyDemand, List<ChunkDemand> chunkDemands,
                                         UUID receivingCityId) {
        if (proposer.getId().equals(target.getId())) {
            return "§cCannot propose peace with yourself.";
        }

        if (!proposer.isEnemy(target.getId())) {
            return "§cYou are not at war with " + target.getName() + ".";
        }

        // Check proposal cap
        if (getOutboundProposals(proposer.getId()).size() >= maxProposalsPerNation) {
            return "§cYou have reached the maximum number of outbound proposals (" + maxProposalsPerNation + ").";
        }

        // Check for existing peace proposal
        if (hasExistingProposal(proposer.getId(), target.getId(), ProposalType.PEACE)) {
            return "§cA peace proposal to " + target.getName() + " is already pending.";
        }

        // Validate currency demand against config cap
        if (currencyDemand > 0) {
            try {
                int maxPercent = com.statecraft.config.StateCraftConfig.PEACE_MAX_CURRENCY_PERCENT.get();
                double targetBalance = com.statecraft.integration.IntegrationRegistry.getNationBalance(target.getName());
                double maxCurrency = targetBalance * (maxPercent / 100.0);
                if (maxPercent > 0 && currencyDemand > maxCurrency) {
                    return "§cCurrency demand exceeds the maximum (" + maxPercent + "% of target treasury = " +
                        com.statecraft.integration.IntegrationRegistry.formatCurrency(maxCurrency) + ").";
                }
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error validating peace currency demand: {}", e.getMessage());
            }
        }

        // Validate chunk demands against config caps
        if (chunkDemands != null && !chunkDemands.isEmpty()) {
            try {
                int maxChunks = com.statecraft.config.StateCraftConfig.PEACE_MAX_CHUNK_COUNT.get();
                int maxImprovementScore = com.statecraft.config.StateCraftConfig.PEACE_MAX_CHUNK_IMPROVEMENT_SCORE.get();

                if (chunkDemands.size() > maxChunks) {
                    return "§cToo many chunks demanded (max " + maxChunks + ").";
                }

                int totalImprovementScore = 0;
                ChunkClaimManager claimManager = ChunkClaimManager.getInstance();
                for (ChunkDemand demand : chunkDemands) {
                    net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(demand.chunkX, demand.chunkZ);
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                        net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            new net.minecraft.resources.ResourceLocation(demand.dimension));
                    ClaimedChunk chunk = claimManager.getClaimedChunk(chunkPos, dimKey);
                    if (chunk == null) {
                        return "§cChunk (" + demand.chunkX + ", " + demand.chunkZ + ") is not claimed.";
                    }
                    City city = claimManager.getCity(chunk.getCityId());
                    if (city == null) {
                        return "§cChunk (" + demand.chunkX + ", " + demand.chunkZ + ") has no city.";
                    }
                    State state = claimManager.getState(city.getStateId());
                    if (state == null || !state.getNationId().equals(target.getId())) {
                        return "§cChunk (" + demand.chunkX + ", " + demand.chunkZ + ") does not belong to " + target.getName() + ".";
                    }

                    int score = com.statecraft.integration.IntegrationRegistry.getChunkImprovementScore(
                        demand.chunkX, demand.chunkZ, demand.dimension);
                    totalImprovementScore += score;
                }

                if (maxImprovementScore > 0 && totalImprovementScore > maxImprovementScore) {
                    return "§cTotal improvement score of demanded chunks (" + totalImprovementScore +
                        ") exceeds maximum (" + maxImprovementScore + ").";
                }
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error validating peace chunk demands: {}", e.getMessage());
                return "§cError validating chunk demands.";
            }

            // Validate receiving city belongs to proposer
            if (receivingCityId == null) {
                return "§cYou must select a city to receive the demanded chunks.";
            }
            City receivingCity = ChunkClaimManager.getInstance().getCity(receivingCityId);
            if (receivingCity == null) {
                return "§cReceiving city not found.";
            }
            State receivingState = ChunkClaimManager.getInstance().getState(receivingCity.getStateId());
            if (receivingState == null || !receivingState.getNationId().equals(proposer.getId())) {
                return "§cReceiving city does not belong to your nation.";
            }
        }

        DiplomacyProposal proposal = new DiplomacyProposal(
            UUID.randomUUID(), ProposalType.PEACE,
            proposer.getId(), target.getId(),
            System.currentTimeMillis(),
            System.currentTimeMillis() + PROPOSAL_EXPIRY_MS,
            currencyDemand,
            chunkDemands != null ? chunkDemands : new ArrayList<>(),
            receivingCityId
        );
        proposals.put(proposal.id, proposal);
        markDirty();

        // Build terms summary for notifications
        String termsSummary = buildTermsSummary(currencyDemand, chunkDemands);

        // Notify target nation leader
        notifyLeader(target, server, "§e§l[DIPLOMACY] §f" + proposer.getName() + " has proposed a peace treaty!" +
            (termsSummary.isEmpty() ? "" : " " + termsSummary));

        // Send mail
        String termsMailSection = termsSummary.isEmpty() ? "" :
            "\n\n=== Treaty Terms ===\n" + buildTermsMailBody(currencyDemand, chunkDemands);

        sendDiplomacyMail(target.getLeaderId(), "Peace Proposal from " + proposer.getName(),
            "The nation of " + proposer.getName() + " has proposed a peace treaty." + termsMailSection +
            "\n\nUse the Diplomacy screen to accept, reject, or counter-propose.\n" +
            "This proposal expires in 7 days.");
        sendDiplomacyMail(proposer.getLeaderId(), "Peace Proposal Sent to " + target.getName(),
            "Your peace proposal has been sent to " + target.getName() + "." + termsMailSection +
            "\nTheir leader must accept for peace to take effect.");

        StateCraft.LOGGER.info("Peace proposed: {} -> {}{}", proposer.getName(), target.getName(),
            termsSummary.isEmpty() ? "" : " [" + termsSummary + "]");
        return "§aPeace proposal sent to " + target.getName() + ". Their leader must accept.";
    }

    private String buildTermsSummary(double currencyDemand, List<ChunkDemand> chunkDemands) {
        List<String> parts = new ArrayList<>();
        if (currencyDemand > 0) {
            parts.add(com.statecraft.integration.IntegrationRegistry.formatCurrency(currencyDemand));
        }
        if (chunkDemands != null && !chunkDemands.isEmpty()) {
            parts.add(chunkDemands.size() + " chunk" + (chunkDemands.size() > 1 ? "s" : ""));
        }
        return parts.isEmpty() ? "" : "§6(Demands: " + String.join(" + ", parts) + ")";
    }

    private String buildTermsMailBody(double currencyDemand, List<ChunkDemand> chunkDemands) {
        StringBuilder sb = new StringBuilder();
        if (currencyDemand > 0) {
            sb.append("Currency: ").append(com.statecraft.integration.IntegrationRegistry.formatCurrency(currencyDemand)).append("\n");
        }
        if (chunkDemands != null && !chunkDemands.isEmpty()) {
            sb.append("Chunks (").append(chunkDemands.size()).append("):\n");
            for (ChunkDemand d : chunkDemands) {
                sb.append("  - (").append(d.chunkX).append(", ").append(d.chunkZ).append(")\n");
            }
        }
        return sb.toString();
    }

    // ==================== Alliance ====================

    /**
     * Propose an alliance with another nation.
     * Creates a pending proposal that the target leader must accept.
     */
    public String proposeAlliance(Nation proposer, Nation target, MinecraftServer server) {
        if (proposer.getId().equals(target.getId())) {
            return "§cCannot form an alliance with yourself.";
        }

        if (proposer.isAlly(target.getId())) {
            return "§cYou are already allied with " + target.getName() + ".";
        }

        if (proposer.isEnemy(target.getId())) {
            return "§cYou are at war with " + target.getName() + ". Declare peace first.";
        }

        // Check proposal cap
        if (getOutboundProposals(proposer.getId()).size() >= maxProposalsPerNation) {
            return "§cYou have reached the maximum number of outbound proposals (" + maxProposalsPerNation + ").";
        }

        // Check for existing alliance proposal
        if (hasExistingProposal(proposer.getId(), target.getId(), ProposalType.ALLIANCE)) {
            return "§cAn alliance proposal to " + target.getName() + " is already pending.";
        }

        DiplomacyProposal proposal = new DiplomacyProposal(
            UUID.randomUUID(), ProposalType.ALLIANCE,
            proposer.getId(), target.getId(),
            System.currentTimeMillis(),
            System.currentTimeMillis() + PROPOSAL_EXPIRY_MS
        );
        proposals.put(proposal.id, proposal);
        markDirty();

        // Notify target nation leader
        notifyLeader(target, server, "§a§l[DIPLOMACY] §f" + proposer.getName() + " has proposed an alliance!");

        // Send mail
        sendDiplomacyMail(target.getLeaderId(), "Alliance Proposal from " + proposer.getName(),
            "The nation of " + proposer.getName() + " has proposed an alliance.\n\n" +
            "Use the Diplomacy screen to accept or reject this proposal.\n" +
            "This proposal expires in 7 days.");
        sendDiplomacyMail(proposer.getLeaderId(), "Alliance Proposal Sent to " + target.getName(),
            "Your alliance proposal has been sent to " + target.getName() + ".\n" +
            "Their leader must accept for the alliance to take effect.");

        StateCraft.LOGGER.info("Alliance proposed: {} -> {}", proposer.getName(), target.getName());
        return "§aAlliance proposal sent to " + target.getName() + ". Their leader must accept.";
    }

    // ==================== Break Alliance ====================

    /**
     * Break an alliance with another nation. Immediate bilateral effect.
     */
    public String breakAlliance(Nation declarer, Nation target, MinecraftServer server) {
        if (!declarer.isAlly(target.getId())) {
            return "§cYou are not allied with " + target.getName() + ".";
        }

        // Bilateral: both sides lose ally status
        declarer.removeAlly(target.getId());
        target.removeAlly(declarer.getId());
        ChunkClaimManager.getInstance().markDirty();
        markDirty();

        // Notify both nations
        String breakMsg = "§c[DIPLOMACY] §eThe alliance between " + declarer.getName() + " and " + target.getName() + " has been dissolved.";
        notifyNationMembers(declarer, server, breakMsg);
        notifyNationMembers(target, server, breakMsg);

        // Send mail
        sendDiplomacyMail(declarer.getLeaderId(), "Alliance Broken with " + target.getName(),
            "Your nation has ended its alliance with " + target.getName() + ".");
        sendDiplomacyMail(target.getLeaderId(), "Alliance Broken by " + declarer.getName(),
            "The nation of " + declarer.getName() + " has ended its alliance with " + target.getName() + ".");

        StateCraft.LOGGER.info("Alliance broken: {} <-> {}", declarer.getName(), target.getName());
        return "§eAlliance with " + target.getName() + " has been dissolved.";
    }

    // ==================== Proposal Management ====================

    /**
     * Accept a pending proposal. Leader-only.
     *
     * @param proposalId The proposal UUID
     * @param acceptingPlayerId The player accepting (must be target nation's leader)
     * @param server The server instance
     * @return A result message
     */
    public String acceptProposal(UUID proposalId, UUID acceptingPlayerId, MinecraftServer server) {
        DiplomacyProposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            return "§cProposal not found or has expired.";
        }

        Nation target = ChunkClaimManager.getInstance().getNation(proposal.targetNationId);
        Nation proposer = ChunkClaimManager.getInstance().getNation(proposal.proposerNationId);
        if (target == null || proposer == null) {
            proposals.remove(proposalId);
            markDirty();
            return "§cOne or both nations no longer exist.";
        }

        // Leader-only check
        if (!target.getLeaderId().equals(acceptingPlayerId)) {
            return "§cOnly the nation leader can accept diplomatic proposals.";
        }

        // Check expiry
        if (System.currentTimeMillis() > proposal.expiresAt) {
            proposals.remove(proposalId);
            markDirty();
            return "§cThis proposal has expired.";
        }

        String result;
        switch (proposal.type) {
            case PEACE:
                if (!proposer.isEnemy(target.getId())) {
                    proposals.remove(proposalId);
                    markDirty();
                    return "§cYou are no longer at war with " + proposer.getName() + ".";
                }

                // Create pending treaty ratification for both legislatures
                result = createTreatyRatification(proposer, target, proposal, server);
                break;

            case ALLIANCE:
                if (proposer.isAlly(target.getId())) {
                    proposals.remove(proposalId);
                    markDirty();
                    return "§cYou are already allied with " + proposer.getName() + ".";
                }
                if (proposer.isEnemy(target.getId())) {
                    proposals.remove(proposalId);
                    markDirty();
                    return "§cYou are at war with " + proposer.getName() + ". Peace must be declared first.";
                }
                // Add ally status to both sides
                proposer.addAlly(target.getId());
                target.addAlly(proposer.getId());
                ChunkClaimManager.getInstance().markDirty();

                String allianceMsg = "§a§l[ALLIANCE FORMED] §f" + proposer.getName() + " and " + target.getName() +
                    " have formed an alliance!";
                notifyNationMembers(proposer, server, allianceMsg);
                notifyNationMembers(target, server, allianceMsg);

                sendDiplomacyMail(proposer.getLeaderId(), "Alliance Accepted by " + target.getName(),
                    target.getName() + " has accepted your alliance proposal.\n" +
                    "Your nations are now allies. Allied citizens can interact in each other's territory.");
                sendDiplomacyMail(target.getLeaderId(), "Alliance Formed with " + proposer.getName(),
                    "You have formed an alliance with " + proposer.getName() + ".\n" +
                    "Allied citizens can interact in each other's territory.");

                result = "§aAlliance formed with " + proposer.getName() + "!";
                StateCraft.LOGGER.info("Alliance formed: {} <-> {}", proposer.getName(), target.getName());
                break;

            default:
                result = "§cUnknown proposal type.";
        }

        proposals.remove(proposalId);
        markDirty();
        return result;
    }

    /**
     * Reject a pending proposal. Leader-only.
     */
    public String rejectProposal(UUID proposalId, UUID rejectingPlayerId, MinecraftServer server) {
        DiplomacyProposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            return "§cProposal not found or has expired.";
        }

        Nation target = ChunkClaimManager.getInstance().getNation(proposal.targetNationId);
        Nation proposer = ChunkClaimManager.getInstance().getNation(proposal.proposerNationId);
        if (target == null) {
            proposals.remove(proposalId);
            markDirty();
            return "§cNation no longer exists.";
        }

        // Leader-only check
        if (!target.getLeaderId().equals(rejectingPlayerId)) {
            return "§cOnly the nation leader can reject diplomatic proposals.";
        }

        String proposerName = proposer != null ? proposer.getName() : "Unknown Nation";
        String typeName = proposal.type == ProposalType.PEACE ? "peace" : "alliance";

        // Notify proposer
        if (proposer != null) {
            notifyLeader(proposer, server, "§c[DIPLOMACY] §f" + target.getName() + " has rejected your " + typeName + " proposal.");
            sendDiplomacyMail(proposer.getLeaderId(), typeName.substring(0, 1).toUpperCase() + typeName.substring(1) +
                " Proposal Rejected by " + target.getName(),
                target.getName() + " has rejected your " + typeName + " proposal.");
        }

        proposals.remove(proposalId);
        markDirty();

        StateCraft.LOGGER.info("{} proposal rejected: {} rejected {}'s proposal",
            typeName, target.getName(), proposerName);
        return "§eYou have rejected the " + typeName + " proposal from " + proposerName + ".";
    }

    // ==================== Query Methods ====================

    /**
     * Get all pending inbound proposals for a nation.
     */
    public List<DiplomacyProposal> getPendingProposals(UUID nationId) {
        return proposals.values().stream()
            .filter(p -> p.targetNationId.equals(nationId))
            .filter(p -> System.currentTimeMillis() <= p.expiresAt)
            .collect(Collectors.toList());
    }

    /**
     * Get all pending outbound proposals from a nation.
     */
    public List<DiplomacyProposal> getOutboundProposals(UUID nationId) {
        return proposals.values().stream()
            .filter(p -> p.proposerNationId.equals(nationId))
            .filter(p -> System.currentTimeMillis() <= p.expiresAt)
            .collect(Collectors.toList());
    }

    /**
     * Get a proposal by ID.
     */
    @Nullable
    public DiplomacyProposal getProposal(UUID proposalId) {
        return proposals.get(proposalId);
    }

    /**
     * Check if a truce is active between two nations.
     */
    public boolean isUnderTruce(UUID nationA, UUID nationB) {
        long now = System.currentTimeMillis();
        return truces.values().stream().anyMatch(t ->
            now < t.expiresAt &&
            ((t.nationA.equals(nationA) && t.nationB.equals(nationB)) ||
             (t.nationA.equals(nationB) && t.nationB.equals(nationA)))
        );
    }

    /**
     * Get the active truce between two nations, if any.
     */
    @Nullable
    public Truce getActiveTruce(UUID nationA, UUID nationB) {
        long now = System.currentTimeMillis();
        return truces.values().stream()
            .filter(t -> now < t.expiresAt)
            .filter(t -> (t.nationA.equals(nationA) && t.nationB.equals(nationB)) ||
                         (t.nationA.equals(nationB) && t.nationB.equals(nationA)))
            .findFirst().orElse(null);
    }

    /**
     * Get the diplomatic status between two nations.
     */
    public DiplomaticStatus getStatus(UUID nationA, UUID nationB) {
        Nation a = ChunkClaimManager.getInstance().getNation(nationA);
        if (a == null) return DiplomaticStatus.NEUTRAL;

        if (a.isAlly(nationB)) return DiplomaticStatus.ALLIED;
        if (a.isEnemy(nationB)) return DiplomaticStatus.AT_WAR;
        if (isUnderTruce(nationA, nationB)) return DiplomaticStatus.TRUCE;
        return DiplomaticStatus.NEUTRAL;
    }

    // ==================== Treaty Ratification ====================

    /**
     * Create a pending treaty ratification when both leaders agree to peace terms.
     * Creates ratification bills in both legislatures.
     */
    private String createTreatyRatification(Nation proposer, Nation target, DiplomacyProposal proposal,
                                             MinecraftServer server) {
        // Create the pending ratification record
        PendingTreatyRatification ratification = new PendingTreatyRatification(
            proposer.getId(), target.getId(),
            proposal.currencyDemand, proposal.chunkDemands, proposal.receivingCityId
        );
        pendingRatifications.put(ratification.getRatificationId(), ratification);

        // Get legislature manager
        com.statecraft.legislature.LegislatureManager legManager =
            com.statecraft.legislature.LegislatureManager.getInstance();

        // Create ratification bill for proposer nation
        com.statecraft.legislature.Legislature proposerLeg = legManager.getOrCreateLegislature(proposer.getId());
        com.statecraft.legislature.Bill proposerBill = createTreatyRatificationBill(
            proposer, target, ratification, proposerLeg, true);
        ratification.setProposerBillId(proposerBill.getBillId());

        // Create ratification bill for target nation
        com.statecraft.legislature.Legislature targetLeg = legManager.getOrCreateLegislature(target.getId());
        com.statecraft.legislature.Bill targetBill = createTreatyRatificationBill(
            target, proposer, ratification, targetLeg, false);
        ratification.setTargetBillId(targetBill.getBillId());

        legManager.markDirty();
        markDirty();

        // Notify both nations
        String termsInfo = ratification.getTermsSummary();
        String pendingMsg = "§e§l[PEACE TREATY] §fLeaders of " + proposer.getName() + " and " + target.getName() +
            " have agreed to peace terms. §6The treaty must be ratified by both legislatures before taking effect.";
        notifyNationMembers(proposer, server, pendingMsg);
        notifyNationMembers(target, server, pendingMsg);

        // Send mail to leaders
        sendDiplomacyMail(proposer.getLeaderId(), "Peace Treaty Pending Ratification - " + target.getName(),
            "You and " + target.getName() + "'s leader have agreed to peace terms.\n\n" +
            "Terms: " + termsInfo + "\n\n" +
            "The treaty must now be ratified by BOTH nations' legislatures before it takes effect.\n" +
            "If either legislature rejects the treaty, the war continues.");
        sendDiplomacyMail(target.getLeaderId(), "Peace Treaty Pending Ratification - " + proposer.getName(),
            "You have accepted " + proposer.getName() + "'s peace proposal.\n\n" +
            "Terms: " + termsInfo + "\n\n" +
            "The treaty must now be ratified by BOTH nations' legislatures before it takes effect.\n" +
            "If either legislature rejects the treaty, the war continues.");

        // Notify legislature members
        notifyLegislaturesOfTreaty(proposer, target, ratification, server);

        StateCraft.LOGGER.info("Peace treaty pending ratification: {} <-> {} (Terms: {})",
            proposer.getName(), target.getName(), termsInfo);

        return "§ePeace treaty with " + proposer.getName() + " is now pending legislature ratification in both nations.";
    }

    /**
     * Create a treaty ratification bill for a nation's legislature.
     */
    private com.statecraft.legislature.Bill createTreatyRatificationBill(
            Nation thisNation, Nation otherNation, PendingTreatyRatification ratification,
            com.statecraft.legislature.Legislature legislature, boolean isProposer) {

        String billNumber = legislature.generateBillNumber();
        String title = "Ratify Peace Treaty with " + otherNation.getName();

        StringBuilder description = new StringBuilder();
        description.append("This bill ratifies the peace treaty negotiated between ")
            .append(thisNation.getName()).append(" and ").append(otherNation.getName()).append(".\n\n");

        description.append("=== Treaty Terms ===\n");
        if (ratification.getCurrencyDemand() > 0) {
            if (isProposer) {
                description.append("• Receive $").append(String.format("%.0f", ratification.getCurrencyDemand()))
                    .append(" in reparations from ").append(otherNation.getName()).append("\n");
            } else {
                description.append("• Pay $").append(String.format("%.0f", ratification.getCurrencyDemand()))
                    .append(" in reparations to ").append(otherNation.getName()).append("\n");
            }
        }
        if (!ratification.getChunkDemands().isEmpty()) {
            if (isProposer) {
                description.append("• Receive ").append(ratification.getChunkDemands().size())
                    .append(" chunk(s) from ").append(otherNation.getName()).append("\n");
            } else {
                description.append("• Cede ").append(ratification.getChunkDemands().size())
                    .append(" chunk(s) to ").append(otherNation.getName()).append("\n");
            }
        }
        if (ratification.getCurrencyDemand() <= 0 && ratification.getChunkDemands().isEmpty()) {
            description.append("• Unconditional peace (no reparations or territory changes)\n");
        }

        description.append("\n=== Ratification Requirements ===\n");
        description.append("• BOTH nations' legislatures must vote YES for the treaty to take effect\n");
        description.append("• If EITHER legislature votes NO, the treaty fails and war continues\n");
        description.append("• This vote cannot be vetoed by the leader\n");
        description.append("\nVote YES to ratify the peace treaty, or NO to reject it and continue the war.");

        com.statecraft.legislature.Bill bill = new com.statecraft.legislature.Bill(
            thisNation.getId(), billNumber, title, description.toString(),
            thisNation.getLeaderId(), "Treaty Negotiation",
            com.statecraft.legislature.Bill.BillType.TREATY_RATIFICATION
        );

        // Add policy change to track what treaty this is for
        bill.addPolicyChange(com.statecraft.legislature.PolicyType.RATIFY_PEACE_TREATY,
            ratification.getRatificationId().toString());

        // Skip debate — go straight to voting with configurable duration
        long votingDuration = com.statecraft.config.StateCraftConfig.LEGISLATURE_VOTING_HOURS.get() * 60 * 60 * 1000L;
        bill.startVoting(System.currentTimeMillis() + votingDuration);
        legislature.addActiveBill(bill);

        return bill;
    }

    /**
     * Notify legislature members of both nations about the pending treaty ratification.
     */
    private void notifyLegislaturesOfTreaty(Nation proposer, Nation target,
                                             PendingTreatyRatification ratification, MinecraftServer server) {
        com.statecraft.legislature.LegislatureManager legManager =
            com.statecraft.legislature.LegislatureManager.getInstance();

        String termsInfo = ratification.getTermsSummary();

        // Notify proposer nation's legislature
        com.statecraft.legislature.Legislature proposerLeg = legManager.getLegislature(proposer.getId());
        if (proposerLeg != null) {
            Set<UUID> proposerVoters = proposerLeg.getVotingMembers(proposer);
            for (UUID voterId : proposerVoters) {
                ServerPlayer voter = server.getPlayerList().getPlayer(voterId);
                if (voter != null) {
                    voter.sendSystemMessage(Component.literal(
                        "§6[Legislature] §ePeace treaty ratification vote: §f" + target.getName() +
                        "§e. Terms: §f" + termsInfo + "§e. Use §f/sc gui§e to vote."));
                }
            }
        }

        // Notify target nation's legislature
        com.statecraft.legislature.Legislature targetLeg = legManager.getLegislature(target.getId());
        if (targetLeg != null) {
            Set<UUID> targetVoters = targetLeg.getVotingMembers(target);
            for (UUID voterId : targetVoters) {
                ServerPlayer voter = server.getPlayerList().getPlayer(voterId);
                if (voter != null) {
                    voter.sendSystemMessage(Component.literal(
                        "§6[Legislature] §ePeace treaty ratification vote: §f" + proposer.getName() +
                        "§e. Terms: §f" + termsInfo + "§e. Use §f/sc gui§e to vote."));
                }
            }
        }
    }

    /**
     * Called when a treaty ratification bill passes or fails in a legislature.
     * Checks if both legislatures have voted and handles the result.
     *
     * @param billId The bill that was just resolved
     * @param passed Whether the bill passed or failed
     * @param server The server instance
     */
    public void onTreatyRatificationVoteComplete(UUID billId, boolean passed, MinecraftServer server) {
        // Find the ratification this bill belongs to
        PendingTreatyRatification ratification = null;
        for (PendingTreatyRatification r : pendingRatifications.values()) {
            if (r.hasBill(billId)) {
                ratification = r;
                break;
            }
        }

        if (ratification == null) {
            StateCraft.LOGGER.warn("Treaty ratification vote complete for unknown bill: {}", billId);
            return;
        }

        Nation proposer = ChunkClaimManager.getInstance().getNation(ratification.getProposerNationId());
        Nation target = ChunkClaimManager.getInstance().getNation(ratification.getTargetNationId());

        if (proposer == null || target == null) {
            StateCraft.LOGGER.warn("Treaty ratification involves non-existent nation, removing");
            pendingRatifications.remove(ratification.getRatificationId());
            markDirty();
            return;
        }

        // Update ratification status
        boolean isProposerBill = ratification.getProposerBillId() != null &&
                                  ratification.getProposerBillId().equals(billId);

        if (passed) {
            if (isProposerBill) {
                ratification.setProposerRatified(true);
                StateCraft.LOGGER.info("Treaty ratified by proposer nation: {}", proposer.getName());
            } else {
                ratification.setTargetRatified(true);
                StateCraft.LOGGER.info("Treaty ratified by target nation: {}", target.getName());
            }
        } else {
            if (isProposerBill) {
                ratification.setProposerRejected(true);
                StateCraft.LOGGER.info("Treaty rejected by proposer nation: {}", proposer.getName());
            } else {
                ratification.setTargetRejected(true);
                StateCraft.LOGGER.info("Treaty rejected by target nation: {}", target.getName());
            }
        }

        markDirty();

        // Check if the treaty is now resolved
        if (ratification.isFullyRatified()) {
            // Both legislatures passed — execute the peace treaty!
            executePeaceTreaty(ratification, proposer, target, server);
            pendingRatifications.remove(ratification.getRatificationId());
            markDirty();
        } else if (ratification.isRejected()) {
            // One legislature rejected — treaty fails
            handleTreatyRejection(ratification, proposer, target, server);
            pendingRatifications.remove(ratification.getRatificationId());
            markDirty();
        } else {
            // One legislature has voted, waiting for the other
            String waitingFor = ratification.isProposerRatified() ? target.getName() : proposer.getName();
            String votedNation = isProposerBill ? proposer.getName() : target.getName();

            String waitMsg = "§e[Treaty Update] §f" + votedNation +
                "'s legislature has ratified the peace treaty. Waiting for " + waitingFor + "'s legislature.";
            notifyNationMembers(proposer, server, waitMsg);
            notifyNationMembers(target, server, waitMsg);
        }
    }

    /**
     * Execute a fully ratified peace treaty.
     */
    private void executePeaceTreaty(PendingTreatyRatification ratification, Nation proposer, Nation target,
                                     MinecraftServer server) {
        StringBuilder termsLog = new StringBuilder();

        // Execute currency transfer
        if (ratification.getCurrencyDemand() > 0 &&
            com.statecraft.integration.IntegrationRegistry.hasEconomyIntegration()) {
            boolean withdrew = com.statecraft.integration.IntegrationRegistry.forceWithdrawFromNation(
                target.getName(), ratification.getCurrencyDemand(),
                "Peace treaty reparations to " + proposer.getName());
            com.statecraft.integration.IntegrationRegistry.depositToNation(
                proposer.getName(), ratification.getCurrencyDemand(),
                "Peace treaty reparations from " + target.getName());
            termsLog.append("Currency: ").append(
                com.statecraft.integration.IntegrationRegistry.formatCurrency(ratification.getCurrencyDemand()));
            if (!withdrew) {
                termsLog.append(" (target treasury went into debt)");
            }
            termsLog.append("\n");
        }

        // Execute chunk transfers
        if (!ratification.getChunkDemands().isEmpty() && ratification.getReceivingCityId() != null) {
            City receivingCity = ChunkClaimManager.getInstance().getCity(ratification.getReceivingCityId());
            if (receivingCity != null) {
                int transferred = 0;
                for (ChunkDemand demand : ratification.getChunkDemands()) {
                    try {
                        net.minecraft.world.level.ChunkPos chunkPos =
                            new net.minecraft.world.level.ChunkPos(demand.chunkX, demand.chunkZ);
                        net.minecraft.resources.ResourceKey<Level> dimKey =
                            net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                new net.minecraft.resources.ResourceLocation(demand.dimension));
                        ClaimedChunk chunk = ChunkClaimManager.getInstance().getClaimedChunk(chunkPos, dimKey);
                        if (chunk != null) {
                            City oldCity = ChunkClaimManager.getInstance().getCity(chunk.getCityId());
                            if (oldCity != null) {
                                oldCity.unclaimChunk(chunkPos, dimKey);
                            }
                            UUID previousOwner = chunk.getPlayerOwner();
                            chunk.setPlayerOwner(null);
                            chunk.setOwnershipType(OwnershipType.HIERARCHY);
                            chunk.setForSale(false);
                            chunk.setCityId(ratification.getReceivingCityId());
                            ChunkClaimManager.getInstance().transferChunkToCity(chunk, receivingCity, chunkPos, dimKey);
                            transferred++;

                            if (previousOwner != null) {
                                sendDiplomacyMail(previousOwner,
                                    "Land Ceded in Peace Treaty",
                                    "Your privately owned chunk at (" + demand.chunkX + ", " + demand.chunkZ +
                                    ") has been ceded to " + proposer.getName() +
                                    " as part of a peace treaty with " + target.getName() + ".\n" +
                                    "The chunk is now government-owned territory of " + proposer.getName() + ".");
                            }
                        }
                    } catch (Exception e) {
                        StateCraft.LOGGER.warn("Failed to transfer chunk ({}, {}) in peace treaty: {}",
                            demand.chunkX, demand.chunkZ, e.getMessage());
                    }
                }
                termsLog.append("Chunks transferred: ").append(transferred).append("\n");
            } else {
                termsLog.append("Chunk transfer failed: receiving city no longer exists\n");
            }
        }

        // Remove enemy status from both sides
        proposer.removeEnemy(target.getId());
        target.removeEnemy(proposer.getId());

        // Create truce
        Truce truce = new Truce(UUID.randomUUID(), proposer.getId(), target.getId(),
            System.currentTimeMillis() + TRUCE_DURATION_MS);
        truces.put(truce.id, truce);

        ChunkClaimManager.getInstance().markDirty();

        String termsSection = termsLog.length() > 0 ?
            "\n\n=== Treaty Terms Executed ===\n" + termsLog : "";

        String peaceMsg = "§a§l[PEACE TREATY RATIFIED] §f" + proposer.getName() + " and " + target.getName() +
            " are now at peace! §7(48-hour truce in effect)";
        notifyNationMembers(proposer, server, peaceMsg);
        notifyNationMembers(target, server, peaceMsg);

        sendDiplomacyMail(proposer.getLeaderId(), "Peace Treaty Ratified - " + target.getName(),
            "Both legislatures have ratified the peace treaty with " + target.getName() + "!\n" +
            "A 48-hour truce is now in effect." + termsSection);
        sendDiplomacyMail(target.getLeaderId(), "Peace Treaty Ratified - " + proposer.getName(),
            "Both legislatures have ratified the peace treaty with " + proposer.getName() + "!\n" +
            "A 48-hour truce is now in effect." + termsSection);

        StateCraft.LOGGER.info("Peace treaty ratified and executed: {} <-> {}{}",
            proposer.getName(), target.getName(),
            termsLog.length() > 0 ? " [" + termsLog.toString().trim() + "]" : "");
    }

    /**
     * Handle a rejected peace treaty.
     */
    private void handleTreatyRejection(PendingTreatyRatification ratification, Nation proposer, Nation target,
                                        MinecraftServer server) {
        String rejectingNation = ratification.isProposerRejected() ? proposer.getName() : target.getName();

        String failMsg = "§c§l[PEACE TREATY REJECTED] §fThe peace treaty between " + proposer.getName() +
            " and " + target.getName() + " has been rejected by " + rejectingNation + "'s legislature. " +
            "§cThe war continues.";
        notifyNationMembers(proposer, server, failMsg);
        notifyNationMembers(target, server, failMsg);

        sendDiplomacyMail(proposer.getLeaderId(), "Peace Treaty Rejected",
            "The peace treaty with " + target.getName() + " has been rejected by " +
            rejectingNation + "'s legislature.\n\nThe war continues.");
        sendDiplomacyMail(target.getLeaderId(), "Peace Treaty Rejected",
            "The peace treaty with " + proposer.getName() + " has been rejected by " +
            rejectingNation + "'s legislature.\n\nThe war continues.");

        StateCraft.LOGGER.info("Peace treaty rejected by {}: {} <-> {}",
            rejectingNation, proposer.getName(), target.getName());
    }

    /**
     * Get all pending treaty ratifications involving a nation.
     */
    public List<PendingTreatyRatification> getPendingRatifications(UUID nationId) {
        return pendingRatifications.values().stream()
            .filter(r -> r.involvesNation(nationId))
            .filter(r -> !r.isExpired() && !r.isRejected() && !r.isFullyRatified())
            .collect(Collectors.toList());
    }

    /**
     * Get a specific pending ratification by ID.
     */
    @Nullable
    public PendingTreatyRatification getPendingRatification(UUID ratificationId) {
        return pendingRatifications.get(ratificationId);
    }

    // ==================== Tick ====================

    /**
     * Called periodically to expire old proposals and truces.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        // Sync config value
        try {
            int configMax = com.statecraft.config.StateCraftConfig.MAX_DIPLOMACY_PROPOSALS.get();
            if (configMax != maxProposalsPerNation) {
                maxProposalsPerNation = configMax;
            }
        } catch (Exception ignored) {}

        // Expire proposals
        Iterator<Map.Entry<UUID, DiplomacyProposal>> propIter = proposals.entrySet().iterator();
        while (propIter.hasNext()) {
            Map.Entry<UUID, DiplomacyProposal> entry = propIter.next();
            if (now > entry.getValue().expiresAt) {
                StateCraft.LOGGER.debug("Diplomacy proposal expired: {} ({} -> {})",
                    entry.getValue().type, entry.getValue().proposerNationId, entry.getValue().targetNationId);
                propIter.remove();
                changed = true;
            }
        }

        // Expire truces
        Iterator<Map.Entry<UUID, Truce>> truceIter = truces.entrySet().iterator();
        while (truceIter.hasNext()) {
            Map.Entry<UUID, Truce> entry = truceIter.next();
            if (now >= entry.getValue().expiresAt) {
                StateCraft.LOGGER.debug("Truce expired between {} and {}",
                    entry.getValue().nationA, entry.getValue().nationB);
                truceIter.remove();
                changed = true;
            }
        }

        // Expire pending treaty ratifications
        Iterator<Map.Entry<UUID, PendingTreatyRatification>> ratIter = pendingRatifications.entrySet().iterator();
        while (ratIter.hasNext()) {
            Map.Entry<UUID, PendingTreatyRatification> entry = ratIter.next();
            if (entry.getValue().isExpired()) {
                StateCraft.LOGGER.debug("Treaty ratification expired: {} <-> {}",
                    entry.getValue().getProposerNationId(), entry.getValue().getTargetNationId());
                ratIter.remove();
                changed = true;
            }
        }

        if (changed) {
            markDirty();
        }
    }

    // ==================== Internal Helpers ====================

    private boolean hasExistingProposal(UUID proposerNationId, UUID targetNationId, ProposalType type) {
        return proposals.values().stream().anyMatch(p ->
            p.proposerNationId.equals(proposerNationId) &&
            p.targetNationId.equals(targetNationId) &&
            p.type == type &&
            System.currentTimeMillis() <= p.expiresAt
        );
    }

    private void removeProposalsBetween(UUID nationA, UUID nationB) {
        proposals.entrySet().removeIf(entry -> {
            DiplomacyProposal p = entry.getValue();
            return (p.proposerNationId.equals(nationA) && p.targetNationId.equals(nationB)) ||
                   (p.proposerNationId.equals(nationB) && p.targetNationId.equals(nationA));
        });
    }

    private void notifyNationMembers(Nation nation, MinecraftServer server, String message) {
        Set<UUID> allMembers = nation.getAllMembers();
        for (UUID memberId : allMembers) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private void notifyLeader(Nation nation, MinecraftServer server, String message) {
        ServerPlayer leader = server.getPlayerList().getPlayer(nation.getLeaderId());
        if (leader != null) {
            leader.sendSystemMessage(Component.literal(message));
        }
    }

    private void sendDiplomacyMail(UUID recipientId, String subject, String body) {
        try {
            MailManager.getInstance().sendSystemMail(recipientId, Mail.MailType.GOV_ANNOUNCEMENT, subject, body);
        } catch (Exception e) {
            StateCraft.LOGGER.warn("Failed to send diplomacy mail: {}", e.getMessage());
        }
    }

    // ==================== Persistence ====================

    private void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Save proposals
        ListTag proposalsList = new ListTag();
        for (DiplomacyProposal proposal : proposals.values()) {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("id", proposal.id);
            pTag.putString("type", proposal.type.name());
            pTag.putUUID("proposerNationId", proposal.proposerNationId);
            pTag.putUUID("targetNationId", proposal.targetNationId);
            pTag.putLong("timestamp", proposal.timestamp);
            pTag.putLong("expiresAt", proposal.expiresAt);

            // Save peace terms
            if (proposal.type == ProposalType.PEACE && proposal.hasTerms()) {
                pTag.putDouble("currencyDemand", proposal.currencyDemand);
                if (proposal.receivingCityId != null) {
                    pTag.putUUID("receivingCityId", proposal.receivingCityId);
                }
                ListTag chunksList = new ListTag();
                for (ChunkDemand cd : proposal.chunkDemands) {
                    CompoundTag cTag = new CompoundTag();
                    cTag.putInt("chunkX", cd.chunkX);
                    cTag.putInt("chunkZ", cd.chunkZ);
                    cTag.putString("dimension", cd.dimension);
                    chunksList.add(cTag);
                }
                pTag.put("chunkDemands", chunksList);
            }

            proposalsList.add(pTag);
        }
        tag.put("proposals", proposalsList);

        // Save truces
        ListTag trucesList = new ListTag();
        for (Truce truce : truces.values()) {
            CompoundTag tTag = new CompoundTag();
            tTag.putUUID("id", truce.id);
            tTag.putUUID("nationA", truce.nationA);
            tTag.putUUID("nationB", truce.nationB);
            tTag.putLong("expiresAt", truce.expiresAt);
            trucesList.add(tTag);
        }
        tag.put("truces", trucesList);

        // Save pending treaty ratifications
        ListTag ratificationsList = new ListTag();
        for (PendingTreatyRatification ratification : pendingRatifications.values()) {
            ratificationsList.add(ratification.save());
        }
        tag.put("pendingRatifications", ratificationsList);

        tag.putInt("maxProposalsPerNation", maxProposalsPerNation);

        return tag;
    }

    public void load(CompoundTag tag) {
        proposals.clear();
        truces.clear();
        pendingRatifications.clear();

        // Load proposals
        if (tag.contains("proposals")) {
            ListTag proposalsList = tag.getList("proposals", Tag.TAG_COMPOUND);
            for (int i = 0; i < proposalsList.size(); i++) {
                CompoundTag pTag = proposalsList.getCompound(i);
                try {
                    double currencyDemand = pTag.getDouble("currencyDemand");
                    UUID receivingCityId = pTag.contains("receivingCityId") ? pTag.getUUID("receivingCityId") : null;
                    List<ChunkDemand> chunkDemands = new ArrayList<>();
                    if (pTag.contains("chunkDemands")) {
                        ListTag chunksList = pTag.getList("chunkDemands", Tag.TAG_COMPOUND);
                        for (int j = 0; j < chunksList.size(); j++) {
                            CompoundTag cTag = chunksList.getCompound(j);
                            chunkDemands.add(new ChunkDemand(
                                cTag.getInt("chunkX"), cTag.getInt("chunkZ"), cTag.getString("dimension")));
                        }
                    }

                    DiplomacyProposal proposal = new DiplomacyProposal(
                        pTag.getUUID("id"),
                        ProposalType.valueOf(pTag.getString("type")),
                        pTag.getUUID("proposerNationId"),
                        pTag.getUUID("targetNationId"),
                        pTag.getLong("timestamp"),
                        pTag.getLong("expiresAt"),
                        currencyDemand, chunkDemands, receivingCityId
                    );
                    // Only load non-expired proposals
                    if (System.currentTimeMillis() <= proposal.expiresAt) {
                        proposals.put(proposal.id, proposal);
                    }
                } catch (Exception e) {
                    StateCraft.LOGGER.warn("Failed to load diplomacy proposal: {}", e.getMessage());
                }
            }
        }

        // Load truces
        if (tag.contains("truces")) {
            ListTag trucesList = tag.getList("truces", Tag.TAG_COMPOUND);
            for (int i = 0; i < trucesList.size(); i++) {
                CompoundTag tTag = trucesList.getCompound(i);
                try {
                    Truce truce = new Truce(
                        tTag.getUUID("id"),
                        tTag.getUUID("nationA"),
                        tTag.getUUID("nationB"),
                        tTag.getLong("expiresAt")
                    );
                    // Only load non-expired truces
                    if (System.currentTimeMillis() < truce.expiresAt) {
                        truces.put(truce.id, truce);
                    }
                } catch (Exception e) {
                    StateCraft.LOGGER.warn("Failed to load truce: {}", e.getMessage());
                }
            }
        }

        // Load pending treaty ratifications
        if (tag.contains("pendingRatifications")) {
            ListTag ratificationsList = tag.getList("pendingRatifications", Tag.TAG_COMPOUND);
            for (int i = 0; i < ratificationsList.size(); i++) {
                CompoundTag rTag = ratificationsList.getCompound(i);
                try {
                    PendingTreatyRatification ratification = PendingTreatyRatification.load(rTag);
                    // Only load non-expired, non-resolved ratifications
                    if (!ratification.isExpired() && !ratification.isFullyRatified() && !ratification.isRejected()) {
                        pendingRatifications.put(ratification.getRatificationId(), ratification);
                    }
                } catch (Exception e) {
                    StateCraft.LOGGER.warn("Failed to load pending treaty ratification: {}", e.getMessage());
                }
            }
        }

        if (tag.contains("maxProposalsPerNation")) {
            maxProposalsPerNation = tag.getInt("maxProposalsPerNation");
        }

        StateCraft.LOGGER.info("Loaded DiplomacyManager: {} proposals, {} truces, {} pending ratifications",
            proposals.size(), truces.size(), pendingRatifications.size());
    }

    // ==================== Inner Types ====================

    public enum ProposalType {
        PEACE, ALLIANCE
    }

    public enum DiplomaticStatus {
        NEUTRAL, ALLIED, AT_WAR, TRUCE
    }

    public static class DiplomacyProposal {
        public final UUID id;
        public final ProposalType type;
        public final UUID proposerNationId;
        public final UUID targetNationId;
        public final long timestamp;
        public final long expiresAt;

        // Peace treaty terms (only used when type == PEACE)
        public final double currencyDemand;           // Currency proposer demands from target
        public final List<ChunkDemand> chunkDemands;  // Chunks proposer demands from target
        public final UUID receivingCityId;             // City in proposer's nation to receive chunks (nullable)

        public DiplomacyProposal(UUID id, ProposalType type, UUID proposerNationId, UUID targetNationId,
                                  long timestamp, long expiresAt) {
            this(id, type, proposerNationId, targetNationId, timestamp, expiresAt, 0, new ArrayList<>(), null);
        }

        public DiplomacyProposal(UUID id, ProposalType type, UUID proposerNationId, UUID targetNationId,
                                  long timestamp, long expiresAt,
                                  double currencyDemand, List<ChunkDemand> chunkDemands, UUID receivingCityId) {
            this.id = id;
            this.type = type;
            this.proposerNationId = proposerNationId;
            this.targetNationId = targetNationId;
            this.timestamp = timestamp;
            this.expiresAt = expiresAt;
            this.currencyDemand = currencyDemand;
            this.chunkDemands = chunkDemands != null ? new ArrayList<>(chunkDemands) : new ArrayList<>();
            this.receivingCityId = receivingCityId;
        }

        public boolean hasTerms() {
            return currencyDemand > 0 || !chunkDemands.isEmpty();
        }
    }

    /**
     * Represents a chunk demanded in a peace treaty
     */
    public static class ChunkDemand {
        public final int chunkX;
        public final int chunkZ;
        public final String dimension;

        public ChunkDemand(int chunkX, int chunkZ, String dimension) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
        }
    }

    public static class Truce {
        public final UUID id;
        public final UUID nationA;
        public final UUID nationB;
        public final long expiresAt;

        public Truce(UUID id, UUID nationA, UUID nationB, long expiresAt) {
            this.id = id;
            this.nationA = nationA;
            this.nationB = nationB;
            this.expiresAt = expiresAt;
        }
    }
}


