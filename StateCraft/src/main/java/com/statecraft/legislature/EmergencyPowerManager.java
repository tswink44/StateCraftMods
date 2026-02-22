package com.statecraft.legislature;

import com.statecraft.StateCraft;
import com.statecraft.core.*;
import com.statecraft.integration.IntegrationRegistry;
import com.statecraft.mail.Mail;
import com.statecraft.mail.MailManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Handles the mechanical effects of emergency powers.
 * Called by LegislatureManager when a power is activated, and ticked to check expirations.
 *
 * Mechanical effects:
 * - MARTIAL_LAW: Suspends open borders (checked in ProtectionHandler.canInteract)
 * - ECONOMIC_EMERGENCY: Blocks non-leader treasury withdrawals (checked via IntegrationRegistry in ATM)
 * - DIPLOMATIC_CRISIS: Instantly declares war on target nation (both sides updated)
 * - EMERGENCY_TAX: One-time 10% levy on all citizen balances
 * - SUCCESSION_CRISIS: Sets a temporary leader while original is offline
 */
public class EmergencyPowerManager {

    private static EmergencyPowerManager instance;

    // Track original leader for succession crisis restoration
    // nationId -> original leaderId
    private final Map<UUID, UUID> successionOriginalLeaders = new HashMap<>();

    // Track which nations have already had their emergency tax collected (to prevent double-tax)
    // nationId -> timestamp of last emergency tax collection
    private final Map<UUID, Long> emergencyTaxCollected = new HashMap<>();

    // Track per-player emergency tax amounts for potential refund if legislature rejects ratification
    // nationId -> (playerId -> amount taxed)
    private final Map<UUID, Map<UUID, Double>> emergencyTaxAmounts = new HashMap<>();

    // Track emergency power event history per nation (last 50 events)
    // nationId -> list of events (most recent first)
    private final Map<UUID, List<EmergencyPowerEvent>> powerHistory = new HashMap<>();
    private static final int MAX_HISTORY_ENTRIES = 50;

    private EmergencyPowerManager() {}

    public static EmergencyPowerManager getInstance() {
        if (instance == null) {
            instance = new EmergencyPowerManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Power Activation ====================

    /**
     * Called when an emergency power is activated. Applies the immediate mechanical effects.
     *
     * @param nation The nation invoking the power
     * @param power The emergency power being activated
     * @param targetValue Optional target value (e.g. target nation name for DIPLOMATIC_CRISIS,
     *                    temporary leader name for SUCCESSION_CRISIS)
     * @param server The server instance
     * @return A result message describing what happened
     */
    public String activatePower(Nation nation, EmergencyPower power, String targetValue, MinecraftServer server) {
        Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());

        // Check cooldown
        if (legislature.isOnCooldown(power)) {
            long remaining = legislature.getCooldownRemaining(power);
            long hours = remaining / (1000 * 60 * 60);
            return "§c" + power.getDisplayName() + " is on cooldown. " + hours + " hours remaining.";
        }

        // Check if already active (for duration-based powers)
        if (power.getDurationHours() > 0 && legislature.isEmergencyPowerActive(power)) {
            return "§c" + power.getDisplayName() + " is already active.";
        }

        // Activate in legislature (handles timing/cooldown)
        if (!legislature.activateEmergencyPower(power)) {
            return "§cFailed to activate " + power.getDisplayName() + ".";
        }

        // Apply mechanical effects
        String result = switch (power) {
            case MARTIAL_LAW -> applyMartialLaw(nation, server);
            case ECONOMIC_EMERGENCY -> applyEconomicEmergency(nation, server);
            case DIPLOMATIC_CRISIS -> applyDiplomaticCrisis(nation, targetValue, server);
            case EMERGENCY_TAX -> applyEmergencyTax(nation, server);
            case SUCCESSION_CRISIS -> applySuccessionCrisis(nation, targetValue, server);
        };

        // Record as executive order in law codex
        Legislature leg = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
        LegislatureManager.getInstance().markDirty();

        // Record in event history
        String leaderName = getPlayerName(server, nation.getLeaderId());
        String details = targetValue != null && !targetValue.isEmpty() ? "Target: " + targetValue : "";
        recordEvent(nation.getId(), power, EventType.INVOKED, leaderName, details);

        // Auto-create ratification bill for legislature review
        createRatificationBill(nation, power, server);

        // Notify all nation members
        notifyNationMembers(nation, server,
            "§c§l[EXECUTIVE ORDER] §6" + power.getDisplayName() +
            " §7has been invoked by the nation leader. The legislature will vote to ratify this action.");

        StateCraft.LOGGER.info("Emergency power {} activated for nation {} by leader. Result: {}",
            power.name(), nation.getName(), result);

        return result;
    }

    /**
     * Called when a power is revoked early by the leader.
     */
    public String revokePower(Nation nation, EmergencyPower power, MinecraftServer server) {
        Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());

        if (!legislature.isEmergencyPowerActive(power)) {
            return "§c" + power.getDisplayName() + " is not currently active.";
        }

        // Reverse effects
        String result = switch (power) {
            case MARTIAL_LAW -> revokeMartialLaw(nation, server);
            case ECONOMIC_EMERGENCY -> revokeEconomicEmergency(nation, server);
            case DIPLOMATIC_CRISIS -> "§eDiplomatic Crisis effects are permanent. Use peace declaration instead.";
            case EMERGENCY_TAX -> "§eEmergency Tax was a one-time collection and cannot be reversed.";
            case SUCCESSION_CRISIS -> revokeSuccessionCrisis(nation, server);
        };

        legislature.deactivateEmergencyPower(power);
        LegislatureManager.getInstance().markDirty();

        // Record in event history
        String leaderName = getPlayerName(server, nation.getLeaderId());
        recordEvent(nation.getId(), power, EventType.REVOKED, leaderName, "");

        notifyNationMembers(nation, server,
            "§a[EXECUTIVE ORDER] §6" + power.getDisplayName() + " §7has been revoked.");

        return result;
    }

    // ==================== Power Expiration ====================

    /**
     * Called when a timed power expires. Reverses any ongoing effects.
     */
    public void onPowerExpired(Nation nation, EmergencyPower power, MinecraftServer server) {
        switch (power) {
            case MARTIAL_LAW -> revokeMartialLaw(nation, server);
            case ECONOMIC_EMERGENCY -> revokeEconomicEmergency(nation, server);
            case SUCCESSION_CRISIS -> revokeSuccessionCrisis(nation, server);
            default -> {} // Instant powers don't expire
        }
        recordEvent(nation.getId(), power, EventType.EXPIRED, "System", "Power duration elapsed");
    }

    // ==================== MARTIAL_LAW ====================

    private String applyMartialLaw(Nation nation, MinecraftServer server) {
        // Store current open borders state so we can restore it on expiration
        // Martial law overrides open borders — checked in ProtectionHandler
        // No actual state change needed here; ProtectionHandler.canInteract checks isEmergencyPowerActive

        String msg = "§c§lMartial Law declared! §eOpen borders suspended and building by non-citizens restricted for " +
            EmergencyPower.MARTIAL_LAW.getDurationHours() + " hours.";

        // Notify foreign players in this nation's territory
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!nation.isMember(player.getUUID())) {
                Nation playerNation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
                // Check if they're currently in this nation's territory
                ChunkPos chunkPos = new ChunkPos(player.blockPosition());
                ClaimedChunk chunk = ChunkClaimManager.getInstance().getClaimedChunk(chunkPos, player.level().dimension());
                if (chunk != null) {
                    City city = ChunkClaimManager.getInstance().getCity(chunk.getCityId());
                    if (city != null) {
                        State state = ChunkClaimManager.getInstance().getState(city.getStateId());
                        if (state != null && state.getNationId().equals(nation.getId())) {
                            player.sendSystemMessage(Component.literal(
                                "§c§l[" + nation.getName() + "] MARTIAL LAW — You are in foreign territory under martial law!"));
                        }
                    }
                }
            }
        }

        return msg;
    }

    private String revokeMartialLaw(Nation nation, MinecraftServer server) {
        return "§aMartial Law lifted. Open borders policy restored.";
    }

    /**
     * Check if martial law is active for a nation. Called by ProtectionHandler.
     */
    public static boolean isMartialLawActive(UUID nationId) {
        Legislature legislature = LegislatureManager.getInstance().getLegislature(nationId);
        return legislature != null && legislature.isEmergencyPowerActive(EmergencyPower.MARTIAL_LAW);
    }

    // ==================== ECONOMIC_EMERGENCY ====================

    private String applyEconomicEmergency(Nation nation, MinecraftServer server) {
        // Effect is checked dynamically by the economy mod via IntegrationRegistry.isEconomicEmergencyActive()
        return "§c§lEconomic Emergency declared! §eTreasury withdrawals frozen for all except the nation leader for " +
            EmergencyPower.ECONOMIC_EMERGENCY.getDurationHours() + " hours.";
    }

    private String revokeEconomicEmergency(Nation nation, MinecraftServer server) {
        return "§aEconomic Emergency lifted. Treasury withdrawals restored.";
    }

    /**
     * Check if an economic emergency is active for a nation. Called by economy mod via integration.
     */
    public static boolean isEconomicEmergencyActive(UUID nationId) {
        Legislature legislature = LegislatureManager.getInstance().getLegislature(nationId);
        return legislature != null && legislature.isEmergencyPowerActive(EmergencyPower.ECONOMIC_EMERGENCY);
    }

    /**
     * Check if a player is the leader of a nation with economic emergency.
     * Only the leader can withdraw during economic emergency.
     */
    public static boolean isLeaderOfNation(UUID playerId, UUID nationId) {
        Nation nation = ChunkClaimManager.getInstance().getNation(nationId);
        return nation != null && nation.getLeaderId().equals(playerId);
    }

    // ==================== DIPLOMATIC_CRISIS ====================

    private String applyDiplomaticCrisis(Nation nation, String targetNationName, MinecraftServer server) {
        if (targetNationName == null || targetNationName.isEmpty()) {
            // Deactivate since no target
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.DIPLOMATIC_CRISIS);
            return "§cDiplomatic Crisis requires a target nation name.";
        }

        Nation targetNation = ChunkClaimManager.getInstance().getNationByName(targetNationName);
        if (targetNation == null) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.DIPLOMATIC_CRISIS);
            return "§cNation not found: " + targetNationName;
        }

        if (targetNation.getId().equals(nation.getId())) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.DIPLOMATIC_CRISIS);
            return "§cCannot declare war on yourself.";
        }

        // Delegate to DiplomacyManager with bypassTruce=true (emergency power overrides truce)
        return DiplomacyManager.getInstance().declareWar(nation, targetNation, server, true);
    }

    // ==================== EMERGENCY_TAX ====================

    private String applyEmergencyTax(Nation nation, MinecraftServer server) {
        // One-time 10% levy on all citizen balances
        if (!IntegrationRegistry.hasEconomyIntegration()) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.EMERGENCY_TAX);
            return "§cEconomy mod is not loaded — cannot collect emergency tax.";
        }

        // Prevent double-taxation
        Long lastCollection = emergencyTaxCollected.get(nation.getId());
        if (lastCollection != null && (System.currentTimeMillis() - lastCollection) < 60000) {
            return "§cEmergency tax was already collected recently.";
        }

        double taxRate = nation.getEmergencyTaxRate() > 0
            ? nation.getEmergencyTaxRate()
            : com.statecraft.config.StateCraftConfig.EMERGENCY_TAX_RATE.get();
        int taxPercent = (int) Math.round(taxRate * 100);
        double totalCollected = 0;
        int citizensTaxed = 0;
        Map<UUID, Double> playerTaxAmounts = new HashMap<>();

        Set<UUID> allMembers = new HashSet<>();
        allMembers.addAll(nation.getMembers());
        allMembers.addAll(nation.getOfficers());
        allMembers.add(nation.getLeaderId());

        for (UUID memberId : allMembers) {
            double balance = IntegrationRegistry.getPlayerBalance(memberId);
            if (balance <= 0) continue;

            double taxAmount = balance * taxRate;
            if (taxAmount < 0.01) continue;

            boolean withdrawn = IntegrationRegistry.withdrawFromPlayer(memberId, taxAmount, "Emergency Tax — Executive Order");
            if (withdrawn) {
                totalCollected += taxAmount;
                citizensTaxed++;
                playerTaxAmounts.put(memberId, taxAmount);

                // Notify online citizen
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member != null) {
                    member.sendSystemMessage(Component.literal(
                        "§c[EMERGENCY TAX] §e" + taxPercent + "% of your balance (" + IntegrationRegistry.formatCurrency(taxAmount) +
                        ") has been levied by the nation leader. §7The legislature will vote to ratify this action."));
                }
            }
        }

        // Store per-player amounts for potential refund
        emergencyTaxAmounts.put(nation.getId(), playerTaxAmounts);

        // Deposit to nation treasury
        if (totalCollected > 0) {
            IntegrationRegistry.depositToNation(nation.getName(), totalCollected, "Emergency Tax Collection");
        }

        emergencyTaxCollected.put(nation.getId(), System.currentTimeMillis());

        // Immediately deactivate — it's a one-time action
        Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
        legislature.deactivateEmergencyPower(EmergencyPower.EMERGENCY_TAX);

        return "§6Emergency Tax (" + taxPercent + "%) collected: " + IntegrationRegistry.formatCurrency(totalCollected) +
            " from " + citizensTaxed + " citizens. §eDeposited to nation treasury.";
    }

    // ==================== SUCCESSION_CRISIS ====================

    private String applySuccessionCrisis(Nation nation, String temporaryLeaderName, MinecraftServer server) {
        if (temporaryLeaderName == null || temporaryLeaderName.isEmpty()) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.SUCCESSION_CRISIS);
            return "§cSuccession Crisis requires a temporary leader name.";
        }

        // Find the temporary leader
        var profile = server.getProfileCache().get(temporaryLeaderName);
        if (profile.isEmpty()) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.SUCCESSION_CRISIS);
            return "§cPlayer not found: " + temporaryLeaderName;
        }

        UUID tempLeaderId = profile.get().getId();

        // Must be a member of the nation
        if (!nation.isMember(tempLeaderId)) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.SUCCESSION_CRISIS);
            return "§c" + temporaryLeaderName + " is not a member of " + nation.getName() + ".";
        }

        // Store original leader
        successionOriginalLeaders.put(nation.getId(), nation.getLeaderId());

        // Set temporary leader
        nation.setLeaderId(tempLeaderId);
        // Ensure they have officer status
        nation.addOfficer(tempLeaderId);
        ChunkClaimManager.getInstance().markDirty();

        // Notify temporary leader
        ServerPlayer tempLeader = server.getPlayerList().getPlayer(tempLeaderId);
        if (tempLeader != null) {
            tempLeader.sendSystemMessage(Component.literal(
                "§6§l[SUCCESSION] §eYou have been appointed temporary leader of " + nation.getName() +
                " for " + EmergencyPower.SUCCESSION_CRISIS.getDurationHours() + " hours."));
        }

        return "§6Succession Crisis invoked. §e" + temporaryLeaderName + " is now temporary leader for " +
            EmergencyPower.SUCCESSION_CRISIS.getDurationHours() + " hours.";
    }

    private String revokeSuccessionCrisis(Nation nation, MinecraftServer server) {
        UUID originalLeader = successionOriginalLeaders.remove(nation.getId());
        if (originalLeader != null) {
            nation.setLeaderId(originalLeader);
            ChunkClaimManager.getInstance().markDirty();

            // Notify
            ServerPlayer leader = server.getPlayerList().getPlayer(originalLeader);
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(
                    "§a[SUCCESSION] §eLeadership of " + nation.getName() + " has been restored to you."));
            }

            return "§aSuccession Crisis ended. Original leader restored.";
        }
        return "§aSuccession Crisis ended.";
    }

    /**
     * Get the original leader for a nation under succession crisis.
     */
    public UUID getOriginalLeader(UUID nationId) {
        return successionOriginalLeaders.get(nationId);
    }

    // ==================== Ratification Bill Creation ====================

    /**
     * Auto-create a ratification bill when an emergency power is invoked.
     * The legislature must vote to ratify the power. If the vote fails,
     * the power is reversed (except DIPLOMATIC_CRISIS war declarations).
     */
    private void createRatificationBill(Nation nation, EmergencyPower power, MinecraftServer server) {
        Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());

        String billNumber = legislature.generateBillNumber();
        String title = "Ratify: " + power.getDisplayName();
        String description = "The nation leader has invoked " + power.getDisplayName() + ". " +
            "The legislature must vote to ratify this use of executive power. " +
            "If this vote FAILS, the emergency power will be reversed." +
            (power == EmergencyPower.DIPLOMATIC_CRISIS ?
                " (Note: War declarations cannot be reversed even if not ratified.)" : "") +
            (power == EmergencyPower.EMERGENCY_TAX ?
                " If not ratified, all collected taxes will be refunded to citizens." : "");

        Bill bill = new Bill(nation.getId(), billNumber, title, description,
            nation.getLeaderId(), "System (Auto-Ratification)", Bill.BillType.EMERGENCY_RATIFICATION);

        // Add policy change so applyPolicyChange knows what power to handle
        bill.addPolicyChange(PolicyType.RATIFY_EMERGENCY_POWER, power.name());

        // Skip debate — go straight to voting with a 24-hour voting period
        long votingDuration = com.statecraft.config.StateCraftConfig.LEGISLATURE_VOTING_HOURS.get() * 60 * 60 * 1000L;
        // For duration-based powers, ensure voting ends before the power expires
        if (power.getDurationHours() > 0) {
            long powerDuration = power.getDurationHours() * 60 * 60 * 1000L;
            votingDuration = Math.min(votingDuration, powerDuration - (60 * 60 * 1000L)); // End 1 hour before expiry
            votingDuration = Math.max(votingDuration, 2 * 60 * 60 * 1000L); // Minimum 2 hours
        }
        bill.startVoting(System.currentTimeMillis() + votingDuration);
        legislature.addActiveBill(bill);

        LegislatureManager.getInstance().markDirty();

        // Notify legislature members
        Set<UUID> voters = legislature.getVotingMembers(nation);
        for (UUID voterId : voters) {
            ServerPlayer voter = server.getPlayerList().getPlayer(voterId);
            if (voter != null) {
                voter.sendSystemMessage(Component.literal(
                    "§6[Legislature] §eEmergency ratification vote: §f" + power.getDisplayName() +
                    "§e. Vote YES to ratify, NO to reverse. Use §f/sc gui§e to vote."));
            }
        }

        // Send mail to legislature members
        try {
            MailManager mailManager = MailManager.getInstance();
            for (UUID voterId : voters) {
                mailManager.sendSystemMail(voterId, Mail.MailType.GOV_ANNOUNCEMENT,
                    "Emergency Ratification Vote: " + power.getDisplayName(),
                    "The nation leader has invoked " + power.getDisplayName() + ".\n\n" +
                    "As a legislature member, you must vote to ratify or reject this use of executive power.\n" +
                    "If the vote fails, the power will be reversed.\n\n" +
                    "Use /sc gui to access the Legislature and cast your vote.");
            }
        } catch (Exception e) {
            StateCraft.LOGGER.warn("Failed to send ratification vote mail: {}", e.getMessage());
        }

        StateCraft.LOGGER.info("Ratification bill {} created for emergency power {} in nation {}",
            billNumber, power.name(), nation.getName());
    }

    // ==================== Legislature Override / Reversal ====================

    /**
     * Reverse an emergency power because the legislature failed to ratify it.
     * Called when a RATIFY_EMERGENCY_POWER bill fails.
     */
    public String reverseUnratifiedPower(Nation nation, EmergencyPower power, MinecraftServer server) {
        String result;
        switch (power) {
            case MARTIAL_LAW -> {
                result = revokeMartialLaw(nation, server);
                Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
                legislature.deactivateEmergencyPower(power);
                result = "§a[Legislature] Martial Law reversed — legislature declined to ratify. Open borders restored.";
            }
            case ECONOMIC_EMERGENCY -> {
                result = revokeEconomicEmergency(nation, server);
                Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
                legislature.deactivateEmergencyPower(power);
                result = "§a[Legislature] Economic Emergency reversed — legislature declined to ratify. Treasury access restored.";
            }
            case DIPLOMATIC_CRISIS -> {
                // War cannot be undeclared — the declaration stands
                Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
                legislature.deactivateEmergencyPower(power);
                result = "§e[Legislature] Diplomatic Crisis not ratified. War declaration cannot be reversed, but the emergency power is deactivated.";
            }
            case EMERGENCY_TAX -> {
                result = refundEmergencyTax(nation, server);
                Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
                legislature.deactivateEmergencyPower(power);
            }
            case SUCCESSION_CRISIS -> {
                result = revokeSuccessionCrisis(nation, server);
                Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
                legislature.deactivateEmergencyPower(power);
                result = "§a[Legislature] Succession Crisis reversed — legislature declined to ratify. Original leader restored.";
            }
            default -> result = "§cUnknown power.";
        }

        LegislatureManager.getInstance().markDirty();

        recordEvent(nation.getId(), power, EventType.NOT_RATIFIED, "Legislature", "Legislature vote failed — power reversed");

        notifyNationMembers(nation, server,
            "§c[LEGISLATURE OVERRIDE] §6" + power.getDisplayName() +
            " §7was NOT ratified by the legislature and has been reversed.");

        StateCraft.LOGGER.info("Emergency power {} reversed for nation {} — legislature failed to ratify",
            power.name(), nation.getName());

        return result;
    }

    /**
     * Override/cancel an active emergency power because the legislature voted to override it.
     * Similar to reversal but applies to powers the legislature wants stopped mid-execution.
     */
    public String overrideActivePower(Nation nation, EmergencyPower power, MinecraftServer server) {
        Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
        if (!legislature.isEmergencyPowerActive(power)) {
            return "§c" + power.getDisplayName() + " is not currently active.";
        }

        // For ongoing powers, just deactivate. For instant ones, reverse.
        String result = switch (power) {
            case MARTIAL_LAW -> {
                revokeMartialLaw(nation, server);
                legislature.deactivateEmergencyPower(power);
                yield "§a[Legislature] Martial Law overridden by legislature vote. Open borders restored.";
            }
            case ECONOMIC_EMERGENCY -> {
                revokeEconomicEmergency(nation, server);
                legislature.deactivateEmergencyPower(power);
                yield "§a[Legislature] Economic Emergency overridden by legislature vote. Treasury access restored.";
            }
            case DIPLOMATIC_CRISIS -> {
                legislature.deactivateEmergencyPower(power);
                yield "§e[Legislature] Diplomatic Crisis overridden. War declaration cannot be reversed.";
            }
            case EMERGENCY_TAX -> {
                String refundResult = refundEmergencyTax(nation, server);
                legislature.deactivateEmergencyPower(power);
                yield refundResult;
            }
            case SUCCESSION_CRISIS -> {
                revokeSuccessionCrisis(nation, server);
                legislature.deactivateEmergencyPower(power);
                yield "§a[Legislature] Succession Crisis overridden. Original leader restored.";
            }
        };

        LegislatureManager.getInstance().markDirty();

        recordEvent(nation.getId(), power, EventType.OVERRIDDEN, "Legislature", "Legislature vote to override");

        notifyNationMembers(nation, server,
            "§c[LEGISLATURE OVERRIDE] §6" + power.getDisplayName() +
            " §7has been overridden by legislature vote.");

        StateCraft.LOGGER.info("Emergency power {} overridden by legislature for nation {}",
            power.name(), nation.getName());

        return result;
    }

    /**
     * Refund emergency tax to all affected citizens.
     * Withdraws the total from nation treasury and deposits back to each player.
     */
    private String refundEmergencyTax(Nation nation, MinecraftServer server) {
        Map<UUID, Double> playerAmounts = emergencyTaxAmounts.remove(nation.getId());
        if (playerAmounts == null || playerAmounts.isEmpty()) {
            return "§eNo emergency tax records found to refund.";
        }

        double totalRefunded = 0;
        int playersRefunded = 0;

        // Withdraw total from nation treasury first
        double totalToRefund = playerAmounts.values().stream().mapToDouble(Double::doubleValue).sum();
        boolean nationWithdrawn = IntegrationRegistry.withdrawFromNation(
            nation.getName(), totalToRefund, "Emergency Tax Refund — Legislature rejected ratification");

        if (!nationWithdrawn) {
            StateCraft.LOGGER.warn("Nation treasury insufficient for full emergency tax refund. Refunding from available funds.");
        }

        // Refund each player
        for (Map.Entry<UUID, Double> entry : playerAmounts.entrySet()) {
            UUID playerId = entry.getKey();
            double amount = entry.getValue();

            boolean deposited = IntegrationRegistry.depositToPlayer(playerId, amount,
                "Emergency Tax Refund — Legislature rejected ratification");
            if (deposited) {
                totalRefunded += amount;
                playersRefunded++;

                // Notify online citizen
                ServerPlayer member = server.getPlayerList().getPlayer(playerId);
                if (member != null) {
                    member.sendSystemMessage(Component.literal(
                        "§a[TAX REFUND] §e" + IntegrationRegistry.formatCurrency(amount) +
                        " has been refunded — the legislature rejected the Emergency Tax."));
                }
            }
        }

        return "§a[Legislature] Emergency Tax reversed — " + IntegrationRegistry.formatCurrency(totalRefunded) +
            " refunded to " + playersRefunded + " citizens.";
    }

    /**
     * Get the per-player emergency tax amounts for a nation (for refund purposes).
     */
    public Map<UUID, Double> getEmergencyTaxAmounts(UUID nationId) {
        return emergencyTaxAmounts.getOrDefault(nationId, Collections.emptyMap());
    }

    // ==================== Query Methods ====================

    /**
     * Check if any emergency power is active for a nation.
     */
    public static boolean hasAnyActivePower(UUID nationId) {
        Legislature legislature = LegislatureManager.getInstance().getLegislature(nationId);
        if (legislature == null) return false;
        for (EmergencyPower power : EmergencyPower.values()) {
            if (legislature.isEmergencyPowerActive(power)) return true;
        }
        return false;
    }

    // ==================== Event History ====================

    /**
     * Record an emergency power event in the history.
     */
    public void recordEvent(UUID nationId, EmergencyPower power, EventType type, String actorName, String details) {
        List<EmergencyPowerEvent> history = powerHistory.computeIfAbsent(nationId, k -> new ArrayList<>());
        history.add(0, new EmergencyPowerEvent(power.name(), type, actorName, details, System.currentTimeMillis()));
        // Trim to max entries
        while (history.size() > MAX_HISTORY_ENTRIES) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * Get the event history for a nation (most recent first).
     */
    public List<EmergencyPowerEvent> getHistory(UUID nationId) {
        return powerHistory.getOrDefault(nationId, Collections.emptyList());
    }

    /**
     * Types of emergency power events for history tracking.
     */
    public enum EventType {
        INVOKED("Invoked"),
        REVOKED("Revoked"),
        EXPIRED("Expired"),
        RATIFIED("Ratified"),
        NOT_RATIFIED("Not Ratified"),
        OVERRIDDEN("Overridden");

        private final String displayName;
        EventType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * A record of an emergency power event.
     */
    public static class EmergencyPowerEvent {
        public final String powerName;    // EmergencyPower enum name
        public final EventType eventType;
        public final String actorName;    // Player name or "System"
        public final String details;      // Optional extra info
        public final long timestamp;

        public EmergencyPowerEvent(String powerName, EventType eventType, String actorName, String details, long timestamp) {
            this.powerName = powerName;
            this.eventType = eventType;
            this.actorName = actorName;
            this.details = details != null ? details : "";
            this.timestamp = timestamp;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("power", powerName);
            tag.putString("eventType", eventType.name());
            tag.putString("actor", actorName);
            tag.putString("details", details);
            tag.putLong("timestamp", timestamp);
            return tag;
        }

        public static EmergencyPowerEvent load(CompoundTag tag) {
            return new EmergencyPowerEvent(
                tag.getString("power"),
                EventType.valueOf(tag.getString("eventType")),
                tag.getString("actor"),
                tag.getString("details"),
                tag.getLong("timestamp")
            );
        }
    }

    // ==================== Utilities ====================

    private void notifyNationMembers(Nation nation, MinecraftServer server, String message) {
        Set<UUID> allMembers = new HashSet<>();
        allMembers.addAll(nation.getMembers());
        allMembers.addAll(nation.getOfficers());
        allMembers.add(nation.getLeaderId());

        for (UUID memberId : allMembers) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private String getPlayerName(MinecraftServer server, UUID playerId) {
        if (server == null) return "Unknown";
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null ? player.getName().getString() : "Unknown";
    }

    // ==================== NBT Serialization ====================

    /**
     * Save persistent state to NBT.
     * Serializes successionOriginalLeaders (nationId -> originalLeaderId)
     * and emergencyTaxCollected (nationId -> timestamp).
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Save successionOriginalLeaders
        ListTag successionList = new ListTag();
        for (Map.Entry<UUID, UUID> entry : successionOriginalLeaders.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("nationId", entry.getKey());
            entryTag.putUUID("originalLeader", entry.getValue());
            successionList.add(entryTag);
        }
        tag.put("successionOriginalLeaders", successionList);

        // Save emergencyTaxCollected
        ListTag taxList = new ListTag();
        for (Map.Entry<UUID, Long> entry : emergencyTaxCollected.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("nationId", entry.getKey());
            entryTag.putLong("timestamp", entry.getValue());
            taxList.add(entryTag);
        }
        tag.put("emergencyTaxCollected", taxList);

        // Save emergencyTaxAmounts (per-player amounts for refund)
        ListTag taxAmountsList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Double>> nationEntry : emergencyTaxAmounts.entrySet()) {
            CompoundTag nationTag = new CompoundTag();
            nationTag.putUUID("nationId", nationEntry.getKey());
            ListTag playerAmountsList = new ListTag();
            for (Map.Entry<UUID, Double> playerEntry : nationEntry.getValue().entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("playerId", playerEntry.getKey());
                playerTag.putDouble("amount", playerEntry.getValue());
                playerAmountsList.add(playerTag);
            }
            nationTag.put("playerAmounts", playerAmountsList);
            taxAmountsList.add(nationTag);
        }
        tag.put("emergencyTaxAmounts", taxAmountsList);

        // Save power event history
        ListTag historyList = new ListTag();
        for (Map.Entry<UUID, List<EmergencyPowerEvent>> entry : powerHistory.entrySet()) {
            CompoundTag nationTag = new CompoundTag();
            nationTag.putUUID("nationId", entry.getKey());
            ListTag eventsList = new ListTag();
            for (EmergencyPowerEvent event : entry.getValue()) {
                eventsList.add(event.save());
            }
            nationTag.put("events", eventsList);
            historyList.add(nationTag);
        }
        tag.put("powerHistory", historyList);

        return tag;
    }

    /**
     * Load persistent state from NBT.
     * Restores successionOriginalLeaders and emergencyTaxCollected maps.
     */
    public void load(CompoundTag tag) {
        successionOriginalLeaders.clear();
        emergencyTaxCollected.clear();
        emergencyTaxAmounts.clear();
        powerHistory.clear();

        // Load successionOriginalLeaders
        if (tag.contains("successionOriginalLeaders")) {
            ListTag successionList = tag.getList("successionOriginalLeaders", Tag.TAG_COMPOUND);
            for (int i = 0; i < successionList.size(); i++) {
                CompoundTag entryTag = successionList.getCompound(i);
                UUID nationId = entryTag.getUUID("nationId");
                UUID originalLeader = entryTag.getUUID("originalLeader");
                successionOriginalLeaders.put(nationId, originalLeader);
            }
        }

        // Load emergencyTaxCollected
        if (tag.contains("emergencyTaxCollected")) {
            ListTag taxList = tag.getList("emergencyTaxCollected", Tag.TAG_COMPOUND);
            for (int i = 0; i < taxList.size(); i++) {
                CompoundTag entryTag = taxList.getCompound(i);
                UUID nationId = entryTag.getUUID("nationId");
                long timestamp = entryTag.getLong("timestamp");
                emergencyTaxCollected.put(nationId, timestamp);
            }
        }

        // Load emergencyTaxAmounts
        if (tag.contains("emergencyTaxAmounts")) {
            ListTag taxAmountsList = tag.getList("emergencyTaxAmounts", Tag.TAG_COMPOUND);
            for (int i = 0; i < taxAmountsList.size(); i++) {
                CompoundTag nationTag = taxAmountsList.getCompound(i);
                UUID nationId = nationTag.getUUID("nationId");
                Map<UUID, Double> playerAmounts = new HashMap<>();
                ListTag playerAmountsList = nationTag.getList("playerAmounts", Tag.TAG_COMPOUND);
                for (int j = 0; j < playerAmountsList.size(); j++) {
                    CompoundTag playerTag = playerAmountsList.getCompound(j);
                    UUID playerId = playerTag.getUUID("playerId");
                    double amount = playerTag.getDouble("amount");
                    playerAmounts.put(playerId, amount);
                }
                if (!playerAmounts.isEmpty()) {
                    emergencyTaxAmounts.put(nationId, playerAmounts);
                }
            }
        }

        // Load power event history
        if (tag.contains("powerHistory")) {
            ListTag historyList = tag.getList("powerHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag nationTag = historyList.getCompound(i);
                UUID nationId = nationTag.getUUID("nationId");
                List<EmergencyPowerEvent> events = new ArrayList<>();
                ListTag eventsList = nationTag.getList("events", Tag.TAG_COMPOUND);
                for (int j = 0; j < eventsList.size(); j++) {
                    try {
                        events.add(EmergencyPowerEvent.load(eventsList.getCompound(j)));
                    } catch (Exception ignored) {} // Skip corrupt entries
                }
                if (!events.isEmpty()) {
                    powerHistory.put(nationId, events);
                }
            }
        }

        StateCraft.LOGGER.info("Loaded EmergencyPowerManager state: {} succession entries, {} tax collection entries, {} tax refund records, {} history nations",
            successionOriginalLeaders.size(), emergencyTaxCollected.size(), emergencyTaxAmounts.size(), powerHistory.size());
    }
}


