package com.statecraft.event;

import com.statecraft.StateCraft;
import com.statecraft.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles chunk protection - prevents unauthorized actions in claimed chunks
 */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID)
public class ProtectionHandler {

    // Players with admin bypass enabled (ops who used /sc admin bypass)
    private static final Set<UUID> bypassPlayers = new HashSet<>();

    /**
     * Check if a player has bypass enabled
     */
    public static boolean hasBypass(UUID playerId) {
        return bypassPlayers.contains(playerId);
    }

    /**
     * Toggle bypass for a player
     */
    public static boolean toggleBypass(UUID playerId) {
        if (bypassPlayers.contains(playerId)) {
            bypassPlayers.remove(playerId);
            return false;
        } else {
            bypassPlayers.add(playerId);
            return true;
        }
    }

    /**
     * Set bypass state for a player
     */
    public static void setBypass(UUID playerId, boolean enabled) {
        if (enabled) {
            bypassPlayers.add(playerId);
        } else {
            bypassPlayers.remove(playerId);
        }
    }

    // ==================== Block Break Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        if (!canInteract(player, event.getPos(), Permission.BREAK)) {
            event.setCanceled(true);
            sendDeniedMessage(player, "break blocks");
        }
    }

    // ==================== Block Place Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!canInteract(player, event.getPos(), Permission.BUILD)) {
            event.setCanceled(true);
            sendDeniedMessage(player, "place blocks");
        }
    }

    // ==================== Right-Click Block Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Check for container access
        if (isContainer(event)) {
            if (!canInteract(player, event.getPos(), Permission.CONTAINER)) {
                event.setCanceled(true);
                event.setUseBlock(Event.Result.DENY);
                sendDeniedMessage(player, "access containers");
                return;
            }
        }

        // Check for general interaction (buttons, levers, doors, etc.)
        // Only block if the target is an interactable block AND player lacks INTERACT permission
        if (isInteractable(event)) {
            if (!canInteract(player, event.getPos(), Permission.INTERACT)) {
                event.setCanceled(true);
                event.setUseBlock(Event.Result.DENY);
                sendDeniedMessage(player, "interact here");
                return;
            }
        }

        // For non-interactable blocks (e.g., placing blocks against a surface),
        // allow if the player has BUILD permission. The EntityPlaceEvent will do
        // the actual BUILD permission check for block placement.
        // Only deny if the player has neither BUILD nor INTERACT permission
        if (!canInteract(player, event.getPos(), Permission.BUILD) &&
            !canInteract(player, event.getPos(), Permission.INTERACT)) {
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            sendDeniedMessage(player, "interact here");
        }
    }

    // ==================== Entity Interaction Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getTarget().blockPosition();

        if (!canInteract(player, pos, Permission.INTERACT)) {
            event.setCanceled(true);
            sendDeniedMessage(player, "interact with entities");
        }
    }

    // ==================== Entity Attack Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        BlockPos pos = target.blockPosition();

        // PvP protection based on nation relationships
        if (target instanceof ServerPlayer targetPlayer) {
            // Bypass check
            if (hasBypass(player.getUUID())) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation attackerNation = manager.getPlayerNation(player.getUUID());
            Nation targetNation = manager.getPlayerNation(targetPlayer.getUUID());

            // If both players are in nations, check relationships
            if (attackerNation != null && targetNation != null) {
                // Same nation: deny PvP (configurable)
                if (attackerNation.getId().equals(targetNation.getId())) {
                    if (com.statecraft.config.StateCraftConfig.PVP_PROTECT_SAME_NATION.get()) {
                        event.setCanceled(true);
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cYou cannot attack a fellow citizen!"), true);
                        return;
                    }
                }

                // At war: always allow PvP
                if (attackerNation.isEnemy(targetNation.getId())) {
                    return; // PvP allowed
                }

                // Allied nations: deny PvP (configurable)
                if (attackerNation.isAlly(targetNation.getId())) {
                    if (com.statecraft.config.StateCraftConfig.PVP_PROTECT_ALLIES.get()) {
                        event.setCanceled(true);
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cYou cannot attack a citizen of an allied nation!"), true);
                        return;
                    }
                }
            }

            // Neutral / nationless: allow PvP (vanilla behavior)
            return;
        }

        // Non-player entities: protect based on chunk permissions
        if (!canInteract(player, pos, Permission.INTERACT)) {
            event.setCanceled(true);
            sendDeniedMessage(player, "attack entities");
        }
    }

    // ==================== Explosion Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;

        ChunkClaimManager manager = ChunkClaimManager.getInstance();

        // Remove blocks in claimed chunks from explosion
        event.getAffectedBlocks().removeIf(pos -> {
            ChunkPos chunkPos = new ChunkPos(pos);
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, event.getLevel().dimension());
            return chunk != null; // Remove from explosion if claimed
        });

        // Remove entities in claimed chunks from explosion damage
        event.getAffectedEntities().removeIf(entity -> {
            ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, entity.level().dimension());
            return chunk != null;
        });
    }

    // ==================== Farmland Trampling Protection ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!canInteract(player, event.getPos(), Permission.BUILD)) {
            event.setCanceled(true);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Check if a player can perform an action at a position
     */
    private static boolean canInteract(ServerPlayer player, BlockPos pos, Permission permission) {
        // Bypass check first
        if (hasBypass(player.getUUID())) {
            return true;
        }

        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        ChunkPos chunkPos = new ChunkPos(pos);

        ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

        // WILDERNESS: Unclaimed chunks are not protected - anyone can interact
        if (chunk == null) {
            return true;
        }

        // Get the nation that owns this chunk through city -> state -> nation hierarchy
        City city = manager.getCity(chunk.getCityId());
        if (city == null) {
            // Chunk's city doesn't exist anymore - allow interaction
            return true;
        }

        State state = manager.getState(city.getStateId());
        if (state == null) {
            return true;
        }

        Nation chunkNation = manager.getNation(state.getNationId());
        if (chunkNation == null) {
            // Chunk's nation doesn't exist anymore - allow interaction
            return true;
        }

        // Check if player is a member of the nation that owns this chunk
        if (chunkNation.isMember(player.getUUID())) {
            // Player is a citizen - check their role-based permissions
            PermissionLevel role = manager.getPlayerRoleInChunk(player.getUUID(), chunk);
            return chunk.hasPermission(player.getUUID(), permission, role);
        }

        // Player is a foreigner - check open borders policy
        Nation playerNation = manager.getPlayerNation(player.getUUID());

        // MARTIAL LAW check: If martial law is active, ALL foreigners are blocked regardless of open borders
        if (com.statecraft.legislature.EmergencyPowerManager.isMartialLawActive(chunkNation.getId())) {
            return false;
        }

        if (!chunkNation.canForeignerInteract(player.getUUID(), playerNation)) {
            // Foreigners not allowed due to closed borders or war
            return false;
        }

        // Open borders allows foreigners - but still respect chunk-specific permissions
        // Foreigners get OUTSIDER level permission check
        return chunk.hasPermission(player.getUUID(), permission, PermissionLevel.OUTSIDER);
    }

    /**
     * Check if the interaction is with a container block.
     * Uses type-based checks (MenuProvider, Container interfaces, block entity hierarchy)
     * instead of fragile string matching. This correctly catches:
     * - All vanilla containers (chests, furnaces, hoppers, barrels, brewing stands, etc.)
     * - Modded containers from other mods
     * - StateCraft Economy blocks (trading hub, company vault, etc.)
     */
    private static boolean isContainer(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return false;

        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // Check block type directly for known container blocks
        if (block instanceof ChestBlock ||
            block instanceof BarrelBlock ||
            block instanceof ShulkerBoxBlock ||
            block instanceof HopperBlock ||
            block instanceof DropperBlock ||
            block instanceof DispenserBlock ||
            block instanceof AbstractFurnaceBlock ||
            block instanceof BrewingStandBlock ||
            block instanceof AnvilBlock ||
            block instanceof EnchantmentTableBlock ||
            block instanceof BeaconBlock ||
            block instanceof LecternBlock) {
            return true;
        }

        // Check if the block entity implements MenuProvider or Container
        // This catches all modded containers and any vanilla ones not covered above
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider || blockEntity instanceof Container) {
            return true;
        }

        return false;
    }

    /**
     * Check if the interaction is with an interactable block (doors, buttons, levers, etc.)
     * These are blocks that have a use action when right-clicked, as opposed to
     * blocks that are just surfaces for placing other blocks against.
     * Uses type-based checks instead of fragile string matching.
     */
    private static boolean isInteractable(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return false;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        Block block = state.getBlock();

        // Doors, trapdoors, fence gates
        if (block instanceof DoorBlock ||
            block instanceof TrapDoorBlock ||
            block instanceof FenceGateBlock) {
            return true;
        }

        // Buttons, levers
        if (block instanceof ButtonBlock ||
            block instanceof LeverBlock) {
            return true;
        }

        // Redstone components
        if (block instanceof DiodeBlock ||      // Covers RepeaterBlock and ComparatorBlock
            block instanceof DaylightDetectorBlock ||
            block instanceof NoteBlock) {
            return true;
        }

        // Misc interactable blocks
        if (block instanceof BellBlock ||
            block instanceof BedBlock ||
            block instanceof JukeboxBlock ||
            block instanceof CakeBlock ||
            block instanceof CandleCakeBlock ||
            block instanceof FlowerPotBlock ||
            block instanceof CampfireBlock ||
            block instanceof RespawnAnchorBlock ||
            block instanceof DragonEggBlock ||
            block instanceof CommandBlock ||
            block instanceof StructureBlock) {
            return true;
        }

        return false;
    }

    /**
     * Send a permission denied message to the player
     */
    private static void sendDeniedMessage(ServerPlayer player, String action) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

        if (chunk == null) {
            // Wilderness - player needs to join a nation
            player.displayClientMessage(
                Component.literal("§cJoin a nation to interact here! §7Use §e/sc nation list§7 or §e/sc gui"),
                true
            );
        } else {
            // Claimed chunk - check why denied
            City city = manager.getCity(chunk.getCityId());
            Nation chunkNation = null;
            if (city != null) {
                State state = manager.getState(city.getStateId());
                if (state != null) {
                    chunkNation = manager.getNation(state.getNationId());
                }
            }
            Nation playerNation = manager.getPlayerNation(player.getUUID());

            if (chunkNation != null && !chunkNation.isMember(player.getUUID())) {
                // Player is a foreigner
                if (com.statecraft.legislature.EmergencyPowerManager.isMartialLawActive(chunkNation.getId())) {
                    // Martial law
                    player.displayClientMessage(
                        Component.literal("§c§l[MARTIAL LAW] §c" + chunkNation.getName() + " is under martial law. Foreign interaction blocked."),
                        true
                    );
                } else if (playerNation != null && chunkNation.isEnemy(playerNation.getId())) {
                    // At war
                    player.displayClientMessage(
                        Component.literal("§cYour nation is at war with " + chunkNation.getName() + "! Cannot interact."),
                        true
                    );
                } else if (!chunkNation.hasOpenBorders()) {
                    // Closed borders
                    player.displayClientMessage(
                        Component.literal("§c" + chunkNation.getName() + " has closed borders. Cannot " + action + "."),
                        true
                    );
                } else {
                    // Open borders but no permission for this specific action
                    player.displayClientMessage(
                        Component.literal("§cYou don't have permission to " + action + " here!"),
                        true
                    );
                }
            } else {
                // Citizen but no permission
                player.displayClientMessage(
                    Component.literal("§cYou don't have permission to " + action + " here!"),
                    true
                );
            }
        }
    }
}

