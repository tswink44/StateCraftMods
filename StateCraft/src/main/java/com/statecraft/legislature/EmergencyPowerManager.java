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

        // Notify all nation members
        notifyNationMembers(nation, server,
            "§c§l[EXECUTIVE ORDER] §6" + power.getDisplayName() + " §7has been invoked by the nation leader.");

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

        double taxRate = 0.10; // 10%
        double totalCollected = 0;
        int citizensTaxed = 0;

        Set<UUID> allMembers = new HashSet<>();
        allMembers.addAll(nation.getMembers());
        allMembers.addAll(nation.getOfficers());
        allMembers.addAll(nation.getAdmins());
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

                // Notify online citizen
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member != null) {
                    member.sendSystemMessage(Component.literal(
                        "§c[EMERGENCY TAX] §e10% of your balance (" + IntegrationRegistry.formatCurrency(taxAmount) +
                        ") has been levied by the nation leader."));
                }
            }
        }

        // Deposit to nation treasury
        if (totalCollected > 0) {
            IntegrationRegistry.depositToNation(nation.getName(), totalCollected, "Emergency Tax Collection");
        }

        emergencyTaxCollected.put(nation.getId(), System.currentTimeMillis());

        // Immediately deactivate — it's a one-time action
        Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
        legislature.deactivateEmergencyPower(EmergencyPower.EMERGENCY_TAX);

        return "§6Emergency Tax collected: " + IntegrationRegistry.formatCurrency(totalCollected) +
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
        if (!nation.isMember(tempLeaderId) && !nation.isAdmin(tempLeaderId) &&
            !nation.isOfficer(tempLeaderId)) {
            Legislature legislature = LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());
            legislature.deactivateEmergencyPower(EmergencyPower.SUCCESSION_CRISIS);
            return "§c" + temporaryLeaderName + " is not a member of " + nation.getName() + ".";
        }

        // Store original leader
        successionOriginalLeaders.put(nation.getId(), nation.getLeaderId());

        // Set temporary leader
        nation.setLeaderId(tempLeaderId);
        // Ensure they have admin
        nation.addAdmin(tempLeaderId);
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

    // ==================== Utilities ====================

    private void notifyNationMembers(Nation nation, MinecraftServer server, String message) {
        Set<UUID> allMembers = new HashSet<>();
        allMembers.addAll(nation.getMembers());
        allMembers.addAll(nation.getOfficers());
        allMembers.addAll(nation.getAdmins());
        allMembers.add(nation.getLeaderId());

        for (UUID memberId : allMembers) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
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

        return tag;
    }

    /**
     * Load persistent state from NBT.
     * Restores successionOriginalLeaders and emergencyTaxCollected maps.
     */
    public void load(CompoundTag tag) {
        successionOriginalLeaders.clear();
        emergencyTaxCollected.clear();

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

        StateCraft.LOGGER.info("Loaded EmergencyPowerManager state: {} succession entries, {} tax collection entries",
            successionOriginalLeaders.size(), emergencyTaxCollected.size());
    }
}


