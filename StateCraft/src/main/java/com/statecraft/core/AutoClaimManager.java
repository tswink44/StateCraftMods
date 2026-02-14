package com.statecraft.core;

import com.statecraft.StateCraft;
import com.statecraft.data.NationSavedData;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.ActionResultPacket;
import com.statecraft.network.packets.SyncAutoClaimPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages auto-claim functionality for nation admins
 */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID)
public class AutoClaimManager {

    private static final AutoClaimManager INSTANCE = new AutoClaimManager();

    // Player UUID -> AutoClaimData
    private final Map<UUID, AutoClaimData> autoClaimPlayers = new HashMap<>();

    // Track last chunk position per player to detect chunk changes
    private final Map<UUID, ChunkPos> lastChunkPositions = new HashMap<>();

    public static AutoClaimManager getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a player can use auto-claim (must be nation admin or city mayor)
     */
    public boolean canPlayerAutoClaim(ServerPlayer player) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        Nation nation = manager.getPlayerNation(player.getUUID());

        if (nation == null) {
            return false;
        }

        // Must be nation admin (includes leader)
        return nation.isAdmin(player.getUUID());
    }

    /**
     * Toggle auto-claim for a player
     */
    public boolean toggleAutoClaim(ServerPlayer player, String cityName) {
        UUID playerId = player.getUUID();

        // Check permission
        if (!canPlayerAutoClaim(player)) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only nation admins can use auto-claim!"), player);
            syncAutoClaimState(player);
            return false;
        }

        // If already auto-claiming, disable it
        if (autoClaimPlayers.containsKey(playerId)) {
            autoClaimPlayers.remove(playerId);
            lastChunkPositions.remove(playerId);
            NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Auto-claim disabled"), player);
            syncAutoClaimState(player);
            return false;
        }

        // Find the city to claim for
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        Nation nation = manager.getPlayerNation(playerId);
        City targetCity = null;

        if (nation != null) {
            if (cityName != null && !cityName.isEmpty()) {
                // Find specific city
                for (State state : nation.getAllStates()) {
                    City city = state.getCityByName(cityName);
                    if (city != null) {
                        targetCity = city;
                        break;
                    }
                }
            } else {
                // Find any city the player can manage
                for (State state : nation.getAllStates()) {
                    for (City city : state.getAllCities()) {
                        if (city.getMayorId().equals(playerId) || nation.isAdmin(playerId)) {
                            targetCity = city;
                            break;
                        }
                    }
                    if (targetCity != null) break;
                }
            }
        }

        if (targetCity == null) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, "No city found to claim for!"), player);
            syncAutoClaimState(player);
            return false;
        }

        // Enable auto-claim
        autoClaimPlayers.put(playerId, new AutoClaimData(targetCity.getId(), targetCity.getName()));
        lastChunkPositions.put(playerId, player.chunkPosition());

        NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Auto-claim enabled for " + targetCity.getName()), player);
        syncAutoClaimState(player);
        return true;
    }

    /**
     * Disable auto-claim for a player
     */
    public void disableAutoClaim(UUID playerId) {
        autoClaimPlayers.remove(playerId);
        lastChunkPositions.remove(playerId);
    }

    /**
     * Check if auto-claim is enabled for a player
     */
    public boolean isAutoClaimEnabled(UUID playerId) {
        return autoClaimPlayers.containsKey(playerId);
    }

    /**
     * Get the city name being auto-claimed for
     */
    public String getAutoClaimCityName(UUID playerId) {
        AutoClaimData data = autoClaimPlayers.get(playerId);
        return data != null ? data.cityName : null;
    }

    /**
     * Sync auto-claim state to client
     */
    public void syncAutoClaimState(ServerPlayer player) {
        boolean enabled = isAutoClaimEnabled(player.getUUID());
        boolean canUse = canPlayerAutoClaim(player);
        String cityName = getAutoClaimCityName(player.getUUID());

        NetworkHandler.sendToPlayer(new SyncAutoClaimPacket(enabled, canUse, cityName != null ? cityName : ""), player);
    }

    /**
     * Process auto-claim when player moves to a new chunk
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        AutoClaimManager manager = getInstance();
        UUID playerId = player.getUUID();

        if (!manager.autoClaimPlayers.containsKey(playerId)) {
            return;
        }

        ChunkPos currentChunk = player.chunkPosition();
        ChunkPos lastChunk = manager.lastChunkPositions.get(playerId);

        // Check if player moved to a new chunk
        if (lastChunk != null && !currentChunk.equals(lastChunk)) {
            manager.lastChunkPositions.put(playerId, currentChunk);
            manager.tryAutoClaimChunk(player, currentChunk);
        } else if (lastChunk == null) {
            manager.lastChunkPositions.put(playerId, currentChunk);
        }
    }

    /**
     * Attempt to auto-claim a chunk
     */
    private void tryAutoClaimChunk(ServerPlayer player, ChunkPos pos) {
        AutoClaimData data = autoClaimPlayers.get(player.getUUID());
        if (data == null) return;

        ChunkClaimManager claimManager = ChunkClaimManager.getInstance();

        // Check if chunk is already claimed
        ClaimedChunk existing = claimManager.getClaimedChunk(pos, player.level().dimension());
        if (existing != null) {
            // Already claimed, skip silently
            return;
        }

        // Get the city
        City city = claimManager.getCity(data.cityId);
        if (city == null) {
            // City no longer exists, disable auto-claim
            disableAutoClaim(player.getUUID());
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Auto-claim disabled: city not found"), player);
            syncAutoClaimState(player);
            return;
        }

        // Try to claim the chunk
        ClaimedChunk claimed = claimManager.claimChunk(city, pos, player.level().dimension());
        if (claimed != null) {
            // Save data
            if (player.level() instanceof ServerLevel serverLevel) {
                NationSavedData.get(serverLevel).markForSave();
            }

            // Notify player
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§aClaimed chunk at " + pos.x + ", " + pos.z),
                true // Action bar
            );
        }
    }

    /**
     * Data class for auto-claim state
     */
    private static class AutoClaimData {
        final UUID cityId;
        final String cityName;

        AutoClaimData(UUID cityId, String cityName) {
            this.cityId = cityId;
            this.cityName = cityName;
        }
    }
}

