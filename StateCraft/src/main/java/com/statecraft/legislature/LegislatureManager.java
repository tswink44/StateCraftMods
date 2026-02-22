package com.statecraft.legislature;

import com.statecraft.StateCraft;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.City;
import com.statecraft.core.ClaimedChunk;
import com.statecraft.core.DiplomacyManager;
import com.statecraft.core.Nation;
import com.statecraft.core.OwnershipType;
import com.statecraft.core.State;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for all nation legislatures
 * Handles tick processing, bill transitions, and notifications
 */
public class LegislatureManager {

    private static LegislatureManager instance;

    private final Map<UUID, Legislature> legislatures;  // nationId -> Legislature
    private boolean dirty = false;
    private MinecraftServer server;

    private LegislatureManager() {
        this.legislatures = new ConcurrentHashMap<>();
    }

    public static LegislatureManager getInstance() {
        if (instance == null) {
            instance = new LegislatureManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    /**
     * Set the server reference for triggering saves
     */
    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Get or create a legislature for a nation
     */
    public Legislature getOrCreateLegislature(UUID nationId) {
        return legislatures.computeIfAbsent(nationId, Legislature::new);
    }

    /**
     * Get a legislature for a nation (may return null)
     */
    public Legislature getLegislature(UUID nationId) {
        return legislatures.get(nationId);
    }

    /**
     * Remove a legislature when a nation is disbanded
     */
    public void removeLegislature(UUID nationId) {
        legislatures.remove(nationId);
        markDirty();
    }

    /**
     * Get the current value of a passed policy for a nation.
     * Called by the Economy mod via reflection to read policy values like IMPORT_TARIFF.
     *
     * @param nationId The nation UUID
     * @param policy   The policy type to query
     * @return The current policy value as a Number, or null if not applicable
     */
    public Number getPassedPolicyValue(UUID nationId, PolicyType policy) {
        Nation nation = ChunkClaimManager.getInstance().getNation(nationId);
        if (nation == null) return null;

        return switch (policy) {
            case IMPORT_TARIFF -> nation.getImportTariffRate();
            case NATION_SALES_TAX_RATE -> nation.getSalesTaxRate();
            case STATE_PASS_THROUGH_RATE -> nation.getStatePassThroughRate();
            case BASE_CHUNK_VALUE -> nation.getBaseChunkValue();
            case CHUNK_CLAIM_FEE -> nation.getChunkClaimFee();
            case EMERGENCY_TAX_RATE -> nation.getEmergencyTaxRate();
            default -> null;
        };
    }

    /**
     * Called every server tick to process bill transitions
     */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Legislature legislature : legislatures.values()) {
            tickLegislature(legislature, now, server);
        }

        // Tick diplomacy manager to expire proposals and truces
        DiplomacyManager.getInstance().tick();
    }

    private void tickLegislature(Legislature legislature, long now, MinecraftServer server) {
        Nation nation = ChunkClaimManager.getInstance().getNation(legislature.getNationId());
        if (nation == null) return;

        List<UUID> toArchive = new ArrayList<>();

        for (Bill bill : legislature.getActiveBills()) {
            switch (bill.getStatus()) {
                case DEBATE:
                    // Check if debate period ended
                    if (now >= bill.getDebateEndTime()) {
                        long votingDuration = StateCraftConfig.LEGISLATURE_VOTING_HOURS.get() * 60 * 60 * 1000L;
                        bill.startVoting(now + votingDuration);
                        notifyLegislatureMembers(legislature, nation, server,
                            "§6[Legislature] §eVoting has begun on: §f" + bill.getTitle());
                        markDirty();
                    }
                    break;

                case VOTING:
                    // Check if voting period ended
                    if (now >= bill.getVoteEndTime()) {
                        Set<UUID> voters = legislature.getVotingMembers(nation);
                        bill.tallyVotes(
                            voters.size(),
                            StateCraftConfig.LEGISLATURE_QUORUM_PERCENT.get(),
                            StateCraftConfig.LEGISLATURE_VETO_OVERRIDE_PERCENT.get()
                        );

                        // Constitutional amendments go directly to ENACTED status
                        if (bill.getStatus() == Bill.Status.ENACTED && bill.isConstitutionalAmendment()) {
                            // Apply policy changes for constitutional amendment
                            applyPolicyChanges(nation, bill.getPolicyChanges());

                            // Create a law from this amendment
                            String lawNumber = legislature.getCodex().generateLawNumber(Law.Type.NATION_LAW);
                            Law law = new Law(
                                nation.getId(),
                                lawNumber,
                                Law.Type.NATION_LAW,
                                bill.getTitle(),
                                bill.getDescription(),
                                bill.getAuthorName(),
                                bill.getPolicyChanges(),
                                bill.getYesVotes(),
                                bill.getNoVotes(),
                                bill.getAbstainVotes(),
                                true,  // Constitutional amendments are always veto-proof
                                null
                            );
                            legislature.getCodex().addLaw(law);

                            notifyLegislatureMembers(legislature, nation, server,
                                "§6[Legislature] §a§lConstitutional Amendment ENACTED: §f" + bill.getTitle() +
                                " §7(Yes: " + bill.getYesVotes() + ", No: " + bill.getNoVotes() + " - 2/3 majority achieved)");
                            sendMailToLeader(nation, server,
                                "Constitutional Amendment Enacted",
                                "Constitutional Amendment " + bill.getBillNumber() + " (" + bill.getTitle() +
                                ") passed with a 2/3 majority and has been enacted. As a constitutional amendment, it cannot be vetoed.");
                            toArchive.add(bill.getBillId());
                        } else if (bill.getStatus() == Bill.Status.ENACTED && bill.isEmergencyRatification()) {
                            // Emergency ratification PASSED — power is ratified
                            applyPolicyChanges(nation, bill.getPolicyChanges());
                            notifyLegislatureMembers(legislature, nation, server,
                                "§6[Legislature] §a§lEmergency Power Ratified: §f" + bill.getTitle() +
                                " §7(Yes: " + bill.getYesVotes() + ", No: " + bill.getNoVotes() + ")");
                            toArchive.add(bill.getBillId());
                        } else if (bill.getStatus() == Bill.Status.PASSED) {
                            if (bill.isVetoProof()) {
                                // Auto-enact veto-proof bills
                                enactBill(legislature, bill, nation, server);
                                // Notify leader
                                sendMailToLeader(nation, server,
                                    "Veto-Proof Bill Enacted",
                                    "Bill " + bill.getBillNumber() + " (" + bill.getTitle() +
                                    ") passed with a veto-proof majority and has been automatically enacted.");
                            } else {
                                // Notify leader of pending bill
                                sendMailToLeader(nation, server,
                                    "Bill Awaiting Your Action",
                                    "Bill " + bill.getBillNumber() + " (" + bill.getTitle() +
                                    ") has passed the legislature. You may sign it into law or veto it.");
                            }
                        } else if (bill.getStatus() == Bill.Status.FAILED) {
                            // Check if this is a failed emergency ratification — reverse the power
                            if (bill.isEmergencyRatification()) {
                                String powerName = bill.getPolicyChanges().get(PolicyType.RATIFY_EMERGENCY_POWER);
                                if (powerName != null) {
                                    try {
                                        EmergencyPower failedPower = EmergencyPower.valueOf(powerName);
                                        EmergencyPowerManager.getInstance().reverseUnratifiedPower(nation, failedPower, server);
                                        notifyLegislatureMembers(legislature, nation, server,
                                            "§6[Legislature] §c§lRatification FAILED: §f" + bill.getTitle() +
                                            " §7(Yes: " + bill.getYesVotes() + ", No: " + bill.getNoVotes() +
                                            ") — §cEmergency power reversed!");
                                    } catch (IllegalArgumentException e) {
                                        StateCraft.LOGGER.warn("Invalid emergency power in failed ratification bill: {}", powerName);
                                    }
                                }
                            } else {
                                String failReason = bill.isConstitutionalAmendment() ?
                                    " §7(Did not achieve 2/3 majority)" : "";
                                notifyLegislatureMembers(legislature, nation, server,
                                    "§6[Legislature] §cBill failed: §f" + bill.getTitle() +
                                    " §7(Yes: " + bill.getYesVotes() + ", No: " + bill.getNoVotes() + ")" + failReason);
                            }
                            toArchive.add(bill.getBillId());
                        }
                        markDirty();
                    }
                    break;

                case PASSED:
                    // Check for expiration (leader has 48 hours to act on non-veto-proof bills)
                    long passedTime = bill.getVoteEndTime();  // Use vote end as passed time
                    if (now - passedTime > 48 * 60 * 60 * 1000L) {
                        // Auto-enact after 48 hours of no action
                        enactBill(legislature, bill, nation, server);
                        sendMailToLeader(nation, server,
                            "Bill Auto-Enacted",
                            "Bill " + bill.getBillNumber() + " (" + bill.getTitle() +
                            ") has been automatically enacted after 48 hours without action.");
                    }
                    break;

                case VETOED:
                case ENACTED:
                case FAILED:
                case EXPIRED:
                    toArchive.add(bill.getBillId());
                    break;
            }
        }

        // Archive completed bills
        for (UUID billId : toArchive) {
            legislature.archiveBill(billId);
        }

        // Check emergency power expirations
        for (Map.Entry<EmergencyPower, Long> entry : legislature.getActiveEmergencyPowers().entrySet()) {
            if (entry.getValue() > 0 && now >= entry.getValue()) {
                // Reverse mechanical effects before deactivating
                Nation expNation = ChunkClaimManager.getInstance().getNation(legislature.getNationId());
                if (expNation != null) {
                    EmergencyPowerManager.getInstance().onPowerExpired(expNation, entry.getKey(), server);
                }
                legislature.deactivateEmergencyPower(entry.getKey());
                notifyLegislatureMembers(legislature, nation, server,
                    "§6[Emergency] §e" + entry.getKey().getDisplayName() + " has expired.");
                markDirty();
            }
        }
    }

    /**
     * Enact a bill - apply its policy changes and add to codex
     */
    public void enactBill(Legislature legislature, Bill bill, Nation nation, MinecraftServer server) {
        bill.enact();

        // Apply policy changes
        for (Map.Entry<PolicyType, String> change : bill.getPolicyChanges().entrySet()) {
            applyPolicyChange(nation, change.getKey(), change.getValue());
        }

        // Create law and add to codex
        String lawNumber = legislature.getCodex().generateLawNumber(Law.Type.NATION_LAW);
        Law law = new Law(
            nation.getId(),
            lawNumber,
            Law.Type.NATION_LAW,
            bill.getTitle(),
            bill.getDescription(),
            bill.getAuthorName(),
            bill.getPolicyChanges(),
            bill.getYesVotes(),
            bill.getNoVotes(),
            bill.getAbstainVotes(),
            bill.isVetoProof(),
            null  // Full text from custom laws
        );
        legislature.getCodex().addLaw(law);

        notifyLegislatureMembers(legislature, nation, server,
            "§6[Legislature] §aNew law enacted: §f" + lawNumber + " - " + bill.getTitle());

        markDirty();
    }

    /**
     * Create an executive order (emergency power)
     */
    public Law createExecutiveOrder(Legislature legislature, Nation nation, EmergencyPower power,
                                     String description, MinecraftServer server) {
        String lawNumber = legislature.getCodex().generateLawNumber(Law.Type.EXECUTIVE_ORDER);

        Map<PolicyType, String> changes = new HashMap<>();
        // Executive orders don't typically change policies directly, but we track them

        Law law = new Law(
            nation.getId(),
            lawNumber,
            Law.Type.EXECUTIVE_ORDER,
            power.getDisplayName(),
            description,
            getLeaderName(nation, server),
            changes,
            0, 0, 0, false,
            "Emergency power invoked: " + power.getDescription()
        );
        legislature.getCodex().addLaw(law);

        notifyLegislatureMembers(legislature, nation, server,
            "§6[Executive Order] §c" + lawNumber + ": " + power.getDisplayName());

        markDirty();
        return law;
    }

    /**
     * Apply policy changes from a map to a nation
     * Public method for use by packet handlers
     */
    public void applyPolicyChanges(Nation nation, Map<PolicyType, String> policyChanges) {
        for (Map.Entry<PolicyType, String> entry : policyChanges.entrySet()) {
            applyPolicyChange(nation, entry.getKey(), entry.getValue());
        }
        markDirty();
    }

    /**
     * Apply a policy change to a nation
     */
    private void applyPolicyChange(Nation nation, PolicyType policy, String value) {
        try {
            switch (policy) {
                case STATE_PASS_THROUGH_RATE:
                    nation.setStatePassThroughRate(Double.parseDouble(value));
                    StateCraft.LOGGER.info("Policy change: State pass-through rate set to {}% for nation {}",
                        Double.parseDouble(value) * 100, nation.getName());
                    // Recalculate all chunk valuations for this nation immediately
                    recalculateNationChunkValuations(nation.getId());
                    break;
                case MAX_STATES_PER_NATION:
                    nation.setMaxStates(Integer.parseInt(value));;
                    break;
                case MAX_CITIES_PER_STATE:
                    // Apply to all states in the nation
                    int maxCities = Integer.parseInt(value);
                    for (State state : nation.getAllStates()) {
                        state.setMaxCities(maxCities);
                    }
                    // Store on nation for new states
                    nation.setDefaultMaxCitiesPerState(maxCities);
                    StateCraft.LOGGER.info("Policy change: Max cities per state set to {} for nation {}",
                        maxCities, nation.getName());
                    break;
                case MAX_CHUNKS_PER_CITY:
                    // Apply to all cities in all states
                    int maxChunks = Integer.parseInt(value);
                    for (State state : nation.getAllStates()) {
                        for (City city : state.getAllCities()) {
                            city.setMaxChunks(maxChunks);
                        }
                    }
                    // Store on nation for new cities
                    nation.setDefaultMaxChunksPerCity(maxChunks);
                    StateCraft.LOGGER.info("Policy change: Max chunks per city set to {} for nation {}",
                        maxChunks, nation.getName());
                    break;
                case MAX_CHUNKS_PER_PLAYER:
                    int maxPlayerChunks = Integer.parseInt(value);
                    nation.setMaxChunksPerPlayer(maxPlayerChunks);
                    StateCraft.LOGGER.info("Policy change: Max chunks per player set to {} for nation {}",
                        maxPlayerChunks, nation.getName());
                    break;
                case OPEN_NATION:
                    nation.setOpen(Boolean.parseBoolean(value));
                    break;
                case OPEN_BORDERS:
                    nation.setOpenBorders(Boolean.parseBoolean(value));
                    StateCraft.LOGGER.info("Policy change: Open borders set to {} for nation {}",
                        value, nation.getName());
                    break;
                case DECLARE_WAR: {
                    // Bilateral war declaration via DiplomacyManager
                    Nation warTarget = resolveNation(value);
                    if (warTarget != null && server != null) {
                        String result = DiplomacyManager.getInstance().declareWar(nation, warTarget, server, false);
                        StateCraft.LOGGER.info("Legislature DECLARE_WAR: {} -> {} : {}",
                            nation.getName(), warTarget.getName(), result);
                    } else if (warTarget == null) {
                        StateCraft.LOGGER.warn("Legislature DECLARE_WAR: target nation not found: {}", value);
                    }
                    break;
                }
                case DECLARE_PEACE: {
                    // Creates a pending peace proposal via DiplomacyManager
                    Nation peaceTarget = resolveNation(value);
                    if (peaceTarget != null && server != null) {
                        String result = DiplomacyManager.getInstance().proposePeace(nation, peaceTarget, server);
                        StateCraft.LOGGER.info("Legislature DECLARE_PEACE: {} -> {} : {}",
                            nation.getName(), peaceTarget.getName(), result);
                    } else if (peaceTarget == null) {
                        StateCraft.LOGGER.warn("Legislature DECLARE_PEACE: target nation not found: {}", value);
                    }
                    break;
                }
                case FORM_ALLIANCE: {
                    // Creates a pending alliance proposal via DiplomacyManager
                    Nation allyTarget = resolveNation(value);
                    if (allyTarget != null && server != null) {
                        String result = DiplomacyManager.getInstance().proposeAlliance(nation, allyTarget, server);
                        StateCraft.LOGGER.info("Legislature FORM_ALLIANCE: {} -> {} : {}",
                            nation.getName(), allyTarget.getName(), result);
                    } else if (allyTarget == null) {
                        StateCraft.LOGGER.warn("Legislature FORM_ALLIANCE: target nation not found: {}", value);
                    }
                    break;
                }
                case BREAK_ALLIANCE: {
                    // Bilateral alliance break via DiplomacyManager
                    Nation breakTarget = resolveNation(value);
                    if (breakTarget != null && server != null) {
                        String result = DiplomacyManager.getInstance().breakAlliance(nation, breakTarget, server);
                        StateCraft.LOGGER.info("Legislature BREAK_ALLIANCE: {} -> {} : {}",
                            nation.getName(), breakTarget.getName(), result);
                    } else if (breakTarget == null) {
                        StateCraft.LOGGER.warn("Legislature BREAK_ALLIANCE: target nation not found: {}", value);
                    }
                    break;
                }
                case BASE_CHUNK_VALUE:
                    nation.setBaseChunkValue(Double.parseDouble(value));
                    StateCraft.LOGGER.info("Policy change: Base chunk value set to ${} for nation {}",
                        value, nation.getName());
                    // Recalculate all chunk valuations for this nation immediately
                    recalculateNationChunkValuations(nation.getId());
                    break;
                case CHUNK_CLAIM_FEE:
                    nation.setChunkClaimFee(Double.parseDouble(value));
                    StateCraft.LOGGER.info("Policy change: Chunk claim fee set to ${} for nation {}",
                        value, nation.getName());
                    break;
                case EMINENT_DOMAIN:
                    // Parse chunk target: "chunkX,chunkZ,dimension"
                    String[] edParts = value.split(",", 3);
                    if (edParts.length < 3) {
                        StateCraft.LOGGER.error("Invalid eminent domain value: {}", value);
                        break;
                    }
                    int edChunkX = Integer.parseInt(edParts[0].trim());
                    int edChunkZ = Integer.parseInt(edParts[1].trim());
                    String edDimension = edParts[2].trim();

                    // Find the chunk
                    net.minecraft.world.level.ChunkPos edPos = new net.minecraft.world.level.ChunkPos(edChunkX, edChunkZ);
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> edDimKey =
                        net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            new net.minecraft.resources.ResourceLocation(edDimension));
                    ClaimedChunk edChunk = ChunkClaimManager.getInstance().getClaimedChunk(edPos, edDimKey);

                    if (edChunk == null) {
                        StateCraft.LOGGER.warn("Eminent domain: Chunk ({}, {}) not found or not claimed", edChunkX, edChunkZ);
                        break;
                    }

                    if (edChunk.getOwnershipType() != OwnershipType.PLAYER || edChunk.getPlayerOwner() == null) {
                        StateCraft.LOGGER.warn("Eminent domain: Chunk ({}, {}) is not privately owned", edChunkX, edChunkZ);
                        break;
                    }

                    UUID previousOwner = edChunk.getPlayerOwner();

                    // Calculate compensation (10x the tax valuation)
                    double chunkValuation = 0;
                    if (com.statecraft.integration.IntegrationRegistry.hasEconomyIntegration()) {
                        chunkValuation = com.statecraft.integration.IntegrationRegistry.getChunkTotalValue(edChunkX, edChunkZ, edDimension);
                    }
                    // Ensure a minimum compensation even if valuation is zero or unavailable
                    if (chunkValuation <= 0) {
                        chunkValuation = nation.getBaseChunkValue();
                    }
                    double compensation = chunkValuation * 10;

                    // Always pay the previous owner — eminent domain requires compensation
                    boolean nationPaid = false;
                    boolean wentNegative = false;
                    if (com.statecraft.integration.IntegrationRegistry.hasEconomyIntegration()) {
                        // Try normal withdraw first
                        nationPaid = com.statecraft.integration.IntegrationRegistry.withdrawFromNation(
                            nation.getName(), compensation,
                            "Eminent domain compensation for chunk (" + edChunkX + ", " + edChunkZ + ")");

                        if (!nationPaid) {
                            // Nation has insufficient funds — force withdraw (treasury goes negative)
                            nationPaid = com.statecraft.integration.IntegrationRegistry.forceWithdrawFromNation(
                                nation.getName(), compensation,
                                "Eminent domain compensation for chunk (" + edChunkX + ", " + edChunkZ + ")");
                            wentNegative = nationPaid;
                            if (nationPaid) {
                                StateCraft.LOGGER.warn("Eminent domain: Nation '{}' treasury went negative to pay ${} compensation",
                                    nation.getName(), String.format("%.2f", compensation));
                            } else {
                                StateCraft.LOGGER.error("Eminent domain: Failed to force-withdraw from nation '{}' treasury", nation.getName());
                            }
                        }

                        // Always deposit compensation to the previous owner
                        com.statecraft.integration.IntegrationRegistry.depositToPlayer(
                            previousOwner, compensation,
                            "Eminent domain compensation for chunk (" + edChunkX + ", " + edChunkZ + ") in " + nation.getName());
                    }

                    // Revert chunk to public (hierarchy) ownership
                    edChunk.setPlayerOwner(null);
                    edChunk.setOwnershipType(OwnershipType.HIERARCHY);
                    // Clear any sale listing
                    edChunk.setForSale(false);
                    ChunkClaimManager.getInstance().markDirty();

                    // Send mail notification to the previous owner
                    String edCompensationStr = com.statecraft.integration.IntegrationRegistry.formatCurrency(compensation);
                    String edMailSubject = "Eminent Domain — Your Property Has Been Seized";
                    String edMailBody = "The nation of " + nation.getName() + " has enacted eminent domain on your property at chunk (" +
                        edChunkX + ", " + edChunkZ + ").\n\n" +
                        "Chunk valuation: " + com.statecraft.integration.IntegrationRegistry.formatCurrency(chunkValuation) + "\n" +
                        "Compensation (10x valuation): " + edCompensationStr + "\n" +
                        "The compensation has been deposited to your account." +
                        (wentNegative ? "\n\nNote: The nation treasury went into debt to pay this compensation." : "") +
                        "\n\nThe chunk has been returned to government ownership.";
                    try {
                        MailManager mailManager = MailManager.getInstance();
                        mailManager.sendSystemMail(previousOwner, Mail.MailType.GOV_ANNOUNCEMENT, edMailSubject, edMailBody);
                    } catch (Exception mailEx) {
                        StateCraft.LOGGER.warn("Failed to send eminent domain notification mail: {}", mailEx.getMessage());
                    }

                    StateCraft.LOGGER.info("Eminent domain enacted: Chunk ({}, {}) repossessed from player {} in nation {}. Compensation: {}{}",
                        edChunkX, edChunkZ, previousOwner, nation.getName(), edCompensationStr,
                        wentNegative ? " (nation treasury went negative)" : "");
                    break;
                case NATION_SALES_TAX_RATE:
                    nation.setSalesTaxRate(Double.parseDouble(value));
                    StateCraft.LOGGER.info("Policy change: Nation sales tax rate set to {}% for nation {}",
                        Double.parseDouble(value) * 100, nation.getName());
                    break;
                case IMPORT_TARIFF:
                    nation.setImportTariffRate(Double.parseDouble(value));
                    StateCraft.LOGGER.info("Policy change: Import tariff rate set to {}% for nation {}",
                        Double.parseDouble(value) * 100, nation.getName());
                    break;
                case EMERGENCY_TAX_RATE:
                    double emergencyRate = Double.parseDouble(value);
                    nation.setEmergencyTaxRate(emergencyRate); // Setter clamps to server max
                    StateCraft.LOGGER.info("Policy change: Emergency tax rate set to {}% for nation {}",
                        nation.getEmergencyTaxRate() * 100, nation.getName());
                    break;
                case LEADER_SPENDING_LIMIT:
                    double spendingLimit = Double.parseDouble(value);
                    // Set via economy mod integration (soft dependency)
                    try {
                        Class<?> spendingLimitClass = Class.forName("com.statecraft.economy.core.SpendingLimitManager");
                        Object spendingManager = spendingLimitClass.getMethod("getInstance").invoke(null);
                        spendingLimitClass.getMethod("setNationLeaderLimit", UUID.class, double.class)
                            .invoke(spendingManager, nation.getId(), spendingLimit);
                        StateCraft.LOGGER.info("Policy change: Nation leader spending limit set to ${} for nation {}",
                            value, nation.getName());
                    } catch (ClassNotFoundException e) {
                        StateCraft.LOGGER.warn("Economy mod not loaded — cannot set leader spending limit");
                    } catch (Exception e) {
                        StateCraft.LOGGER.warn("Error setting leader spending limit: {}", e.getMessage());
                    }
                    break;
                // Constitutional policies (require 2/3 majority, cannot be vetoed)
                case LEADER_TERM_DURATION:
                    int termDays = Integer.parseInt(value);
                    nation.setLeaderTermDays(termDays);
                    StateCraft.LOGGER.info("Constitutional amendment: Leader term duration set to {} days for nation {}",
                        termDays, nation.getName());
                    break;
                case ELECTION_DURATION:
                    int electionDays = Integer.parseInt(value);
                    nation.setElectionDurationDays(electionDays);
                    StateCraft.LOGGER.info("Constitutional amendment: Election duration set to {} days for nation {}",
                        electionDays, nation.getName());
                    break;
                case MAX_OFFICERS:
                    int maxOfficers = Integer.parseInt(value);
                    nation.setMaxOfficers(maxOfficers);
                    StateCraft.LOGGER.info("Constitutional amendment: Max officers set to {} for nation {}",
                        maxOfficers, nation.getName());
                    // Note: If current officers exceed new max, they are NOT automatically removed
                    // This allows for grandfathering but prevents new appointments
                    if (nation.getOfficers().size() > maxOfficers) {
                        StateCraft.LOGGER.warn("Nation {} has {} officers but max is now {}. Excess officers remain until removed.",
                            nation.getName(), nation.getOfficers().size(), maxOfficers);
                    }
                    break;
                case NATION_NAME:
                    String oldName = nation.getName();
                    String newName = value.trim();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        // Check if name is taken
                        Nation existingNation = ChunkClaimManager.getInstance().getNationByName(newName);
                        if (existingNation == null || existingNation.getId().equals(nation.getId())) {
                            nation.setName(newName);
                            ChunkClaimManager.getInstance().markDirty();
                            StateCraft.LOGGER.info("Constitutional amendment: Nation renamed from '{}' to '{}'",
                                oldName, newName);
                        } else {
                            StateCraft.LOGGER.warn("Cannot rename nation: name '{}' already taken", newName);
                        }
                    }
                    break;
                case NATION_FLAG:
                    nation.setFlagUrl(value);
                    StateCraft.LOGGER.info("Constitutional amendment: Nation flag updated for nation {}",
                        nation.getName());
                    break;
                case IMPEACH_LEADER:
                    handleImpeachment(nation, value);
                    break;
                case RATIFY_EMERGENCY_POWER:
                    // Ratification PASSED — the power is ratified, no reversal needed
                    try {
                        EmergencyPower ratifiedPower = EmergencyPower.valueOf(value);
                        StateCraft.LOGGER.info("Legislature ratified emergency power {} for nation {}",
                            ratifiedPower.name(), nation.getName());
                        EmergencyPowerManager.getInstance().recordEvent(
                            nation.getId(), ratifiedPower, EmergencyPowerManager.EventType.RATIFIED,
                            "Legislature", "Legislature vote passed — power ratified");
                        if (server != null) {
                            notifyLegislatureMembers(
                                getOrCreateLegislature(nation.getId()), nation, server,
                                "§a[Legislature] §f" + ratifiedPower.getDisplayName() +
                                " §ahas been ratified by the legislature.");
                        }
                    } catch (IllegalArgumentException e) {
                        StateCraft.LOGGER.warn("Invalid emergency power name in ratification: {}", value);
                    }
                    break;
                case OVERRIDE_EMERGENCY_POWER:
                    // Override PASSED — cancel the active emergency power
                    try {
                        EmergencyPower overriddenPower = EmergencyPower.valueOf(value);
                        if (server != null) {
                            EmergencyPowerManager.getInstance().overrideActivePower(nation, overriddenPower, server);
                        }
                        StateCraft.LOGGER.info("Legislature overrode emergency power {} for nation {}",
                            overriddenPower.name(), nation.getName());
                    } catch (IllegalArgumentException e) {
                        StateCraft.LOGGER.warn("Invalid emergency power name in override: {}", value);
                    }
                    break;
                // Custom laws don't need direct application
                default:
                    StateCraft.LOGGER.info("Policy change (roleplay): {} = {}", policy.getDisplayName(), value);
            }
        } catch (Exception e) {
            StateCraft.LOGGER.error("Failed to apply policy change: {} = {}", policy, value, e);
        }
    }

    private void notifyLegislatureMembers(Legislature legislature, Nation nation,
                                           MinecraftServer server, String message) {
        Set<UUID> members = legislature.getVotingMembers(nation);
        members.add(nation.getLeaderId());  // Also notify leader

        for (UUID memberId : members) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private void sendMailToLeader(Nation nation, MinecraftServer server, String subject, String body) {
        MailManager mailManager = MailManager.getInstance();
        mailManager.sendSystemMail(nation.getLeaderId(), Mail.MailType.GOV_ANNOUNCEMENT, subject, body);
    }

    private String getLeaderName(Nation nation, MinecraftServer server) {
        ServerPlayer leader = server.getPlayerList().getPlayer(nation.getLeaderId());
        if (leader != null) {
            return leader.getName().getString();
        }
        return "Leader";
    }

    /**
     * Handle impeachment of a nation leader.
     * Removes the leader, appoints a temporary caretaker (first officer),
     * and triggers an immediate election.
     */
    private void handleImpeachment(Nation nation, String reason) {
        UUID oldLeaderId = nation.getLeaderId();
        String oldLeaderName = server != null ? getLeaderName(nation, server) : "Unknown";

        // Appoint a temporary caretaker leader
        // Priority: first officer, then first governor, then leave as-is for election
        UUID caretakerId = null;
        String caretakerName = "Unknown";

        // Try officers first
        if (!nation.getOfficers().isEmpty()) {
            caretakerId = nation.getOfficers().iterator().next();
        }

        // Try governors if no officers
        if (caretakerId == null) {
            for (State state : nation.getAllStates()) {
                UUID govId = state.getGovernorId();
                if (govId != null && !govId.equals(oldLeaderId)) {
                    caretakerId = govId;
                    break;
                }
            }
        }

        // Set the caretaker as temporary leader
        if (caretakerId != null) {
            nation.setLeaderId(caretakerId);
            if (server != null) {
                ServerPlayer caretaker = server.getPlayerList().getPlayer(caretakerId);
                if (caretaker != null) {
                    caretakerName = caretaker.getName().getString();
                    caretaker.sendSystemMessage(Component.literal(
                        "§6§l[Impeachment] §eYou have been appointed as caretaker leader of §f" +
                        nation.getName() + "§e pending an emergency election."));
                }
            }
        }

        ChunkClaimManager.getInstance().markDirty();

        // Trigger an immediate election
        if (server != null) {
            com.statecraft.core.ElectionManager electionManager = com.statecraft.core.ElectionManager.getInstance();
            com.statecraft.core.Election election = electionManager.startElection(nation, server);
            if (election != null) {
                StateCraft.LOGGER.info("Impeachment: Emergency election started for nation '{}'", nation.getName());
            }
        }

        // Notify all online members
        if (server != null) {
            String announcement = "§c§l[IMPEACHMENT] §f" + oldLeaderName +
                " §chas been removed as leader of §f" + nation.getName() + "§c!";
            if (!reason.isEmpty()) {
                announcement += "\n§7Reason: §f" + reason;
            }
            if (caretakerId != null) {
                announcement += "\n§e" + caretakerName + " §7serves as caretaker leader pending election.";
            }

            for (UUID memberId : nation.getAllMembers()) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member != null) {
                    member.sendSystemMessage(Component.literal(announcement));
                }
            }
        }

        // Send mail to the impeached leader
        try {
            MailManager mailManager = MailManager.getInstance();
            String mailBody = "You have been impeached as leader of " + nation.getName() +
                " by a 2/3 supermajority vote of the legislature.\n\n" +
                "Reason: " + (reason.isEmpty() ? "No reason provided." : reason) + "\n\n" +
                (caretakerId != null ?
                    caretakerName + " has been appointed as caretaker leader. " : "") +
                "An emergency election has been called.";
            mailManager.sendSystemMail(oldLeaderId, Mail.MailType.GOV_ANNOUNCEMENT,
                "You Have Been Impeached", mailBody);
        } catch (Exception e) {
            StateCraft.LOGGER.warn("Failed to send impeachment mail: {}", e.getMessage());
        }

        // Send mail to the caretaker
        if (caretakerId != null) {
            try {
                MailManager mailManager = MailManager.getInstance();
                String mailBody = "Following the impeachment of " + oldLeaderName +
                    ", you have been appointed as caretaker leader of " + nation.getName() +
                    " pending an emergency election.\n\n" +
                    "An election is now active. You may run as a candidate or simply serve until the election concludes.";
                mailManager.sendSystemMail(caretakerId, Mail.MailType.GOV_ANNOUNCEMENT,
                    "Appointed Caretaker Leader", mailBody);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Failed to send caretaker appointment mail: {}", e.getMessage());
            }
        }

        StateCraft.LOGGER.info("Impeachment: {} removed as leader of '{}'. Reason: {}. Caretaker: {}",
            oldLeaderName, nation.getName(), reason.isEmpty() ? "none" : reason,
            caretakerId != null ? caretakerName : "none");
    }

    /**
     * Resolve a nation from a value that may be a UUID string or a nation name.
     */
    private Nation resolveNation(String value) {
        try {
            UUID nationId = UUID.fromString(value);
            return ChunkClaimManager.getInstance().getNation(nationId);
        } catch (IllegalArgumentException e) {
            return ChunkClaimManager.getInstance().getNationByName(value);
        }
    }

    public void markDirty() {
        this.dirty = true;
        // Also trigger world save to persist legislature data
        if (server != null) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                NationSavedData.get(overworld).markForSave();
            }
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * Recalculate all chunk valuations for a specific nation when taxation parameters change.
     * Uses reflection to call StateCraftEconomy without hard dependency.
     *
     * @param nationId The UUID of the nation whose chunks need recalculation
     */
    private void recalculateNationChunkValuations(UUID nationId) {
        try {
            Class<?> valuationManagerClass = Class.forName("com.statecraft.economy.valuation.ChunkValuationManager");
            Object manager = valuationManagerClass.getMethod("getInstance").invoke(null);
            valuationManagerClass.getMethod("recalculateNationChunks", UUID.class).invoke(manager, nationId);
            StateCraft.LOGGER.info("Recalculated chunk valuations for nation {} due to policy change", nationId);
        } catch (ClassNotFoundException e) {
            // StateCraftEconomy mod not loaded, ignore
            StateCraft.LOGGER.debug("StateCraftEconomy not loaded, skipping valuation recalculation");
        } catch (Exception e) {
            StateCraft.LOGGER.warn("Failed to recalculate chunk valuations: {}", e.getMessage());
        }
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag legislaturesList = new ListTag();
        for (Legislature legislature : legislatures.values()) {
            legislaturesList.add(legislature.save());
        }
        tag.put("legislatures", legislaturesList);

        // Save EmergencyPowerManager state
        tag.put("emergencyPowerManager", EmergencyPowerManager.getInstance().save());

        // Save DiplomacyManager state
        tag.put("diplomacyManager", DiplomacyManager.getInstance().save());

        return tag;
    }

    public void load(CompoundTag tag) {
        legislatures.clear();

        ListTag legislaturesList = tag.getList("legislatures", Tag.TAG_COMPOUND);
        for (int i = 0; i < legislaturesList.size(); i++) {
            Legislature legislature = Legislature.load(legislaturesList.getCompound(i));
            legislatures.put(legislature.getNationId(), legislature);
        }

        // Load EmergencyPowerManager state
        if (tag.contains("emergencyPowerManager")) {
            EmergencyPowerManager.getInstance().load(tag.getCompound("emergencyPowerManager"));
        }

        // Load DiplomacyManager state
        if (tag.contains("diplomacyManager")) {
            DiplomacyManager.getInstance().load(tag.getCompound("diplomacyManager"));
        }
    }
}



