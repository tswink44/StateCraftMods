package dev.statecraft.runtime;

import dev.statecraft.StateCraft;
import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.domain.AccessAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ProtectionEvents {
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public static String key(Level level, BlockPos pos) {
        return new ChunkKey(level.dimension().location().toString(), pos.getX() >> 4, pos.getZ() >> 4).toString();
    }

    private boolean allowed(ServerPlayer player, BlockPos pos, AccessAction action, UUID target) {
        boolean permitted = mayAct(player, pos, action, target);
        if (!permitted) {
            notifyDenied(player);
        }
        return permitted;
    }

    private boolean mayAct(ServerPlayer player, BlockPos pos, AccessAction action, UUID target) {
        ServerRuntime runtime = StateCraft.runtimeOrNull();
        if (runtime == null) {
            return true;
        }
        return runtime.engine().mayAct(ServerRuntime.actor(player), key(player.level(), pos), action, target);
    }

    private void notifyDenied(ServerPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastNotice.getOrDefault(player.getUUID(), 0L) >= 1500) {
            lastNotice.put(player.getUUID(), now);
            player.displayClientMessage(Component.literal("StateCraft: you do not have permission here.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastNotice.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && !allowed(player, event.getPos(), AccessAction.BREAK, null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void trackBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && StateCraft.runtimeOrNull() != null) {
            StateCraft.runtime().engine().recordImprovement(key(level, event.getPos()), -1);
            StateCraft.runtime().markDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void place(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || StateCraft.runtimeOrNull() == null) {
            return;
        }
        List<BlockPos> positions = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots().stream().map(BlockSnapshot::getPos).toList()
                : List.of(event.getPos());
        for (BlockPos position : positions) {
            boolean permitted = event.getEntity() instanceof ServerPlayer player
                    ? allowed(player, position, AccessAction.PLACE, null)
                    : StateCraft.runtime().governance().claim(key(level, position)).isEmpty();
            if (!permitted) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void trackPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || StateCraft.runtimeOrNull() == null) {
            return;
        }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                StateCraft.runtime().engine().recordImprovement(key(level, snapshot.getPos()), 1);
            }
        } else {
            StateCraft.runtime().engine().recordImprovement(key(level, event.getPos()), 1);
        }
        StateCraft.runtime().markDirty();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void blockInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || mayAct(player, event.getPos(), AccessAction.BLOCK_INTERACT, null)) {
            return;
        }
        event.setUseBlock(Event.Result.DENY);
        var stack = player.getItemInHand(event.getHand());
        if (stack.getItem() instanceof BlockItem) {
            var placement = new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec());
            if (mayAct(player, placement.getClickedPos(), AccessAction.PLACE, null)) {
                return;
            }
        } else if (stack.getItem() instanceof DiggerItem
                && mayAct(player, event.getPos(), AccessAction.PLACE, null)) {
            return;
        }
        notifyDenied(player);
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void startBreaking(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !allowed(player, event.getPos(), AccessAction.BREAK, null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void tool(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && !allowed(player, event.getPos(), AccessAction.PLACE, null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void bucket(FillBucketEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof BlockHitResult hit
                && (!allowed(player, hit.getBlockPos(), AccessAction.BLOCK_INTERACT, null)
                    || !allowed(player, hit.getBlockPos().relative(hit.getDirection()), AccessAction.PLACE, null))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void entityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !allowed(player, event.getTarget().blockPosition(), AccessAction.ENTITY_INTERACT, event.getTarget().getUUID())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void specificEntityInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !allowed(player, event.getTarget().blockPosition(), AccessAction.ENTITY_INTERACT, event.getTarget().getUUID())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !mayAttack(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void damage(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !mayAttack(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private boolean mayAttack(ServerPlayer attacker, Entity target) {
        if (attacker.level() != target.level()) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            return allowed(attacker, player.blockPosition(), AccessAction.PVP, player.getUUID())
                    && allowed(attacker, attacker.blockPosition(), AccessAction.PVP, player.getUUID());
        }
        return allowed(attacker, target.blockPosition(), AccessAction.ATTACK, target.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void explosion(ExplosionEvent.Detonate event) {
        ServerRuntime runtime = StateCraft.runtimeOrNull();
        if (runtime == null || event.getLevel().isClientSide()) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> !runtime.engine().allowsExplosion(key(event.getLevel(), pos)));
        event.getAffectedEntities().removeIf(entity -> !runtime.engine().allowsExplosion(key(event.getLevel(), entity.blockPosition())));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void trackExplosion(ExplosionEvent.Detonate event) {
        ServerRuntime runtime = StateCraft.runtimeOrNull();
        if (runtime != null && !event.getLevel().isClientSide()) {
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (!event.getLevel().getBlockState(pos).isAir()) {
                    runtime.engine().recordImprovement(key(event.getLevel(), pos), -1);
                }
            }
            runtime.markDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || StateCraft.runtimeOrNull() == null) {
            return;
        }
        var helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) {
            return;
        }
        String source = parcel(level, event.getPos());
        Direction movement = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        if (!source.equals(parcel(level, event.getFaceOffsetPos()))) {
            event.setCanceled(true);
            return;
        }
        for (BlockPos pos : helper.getToPush()) {
            if (!source.equals(parcel(level, pos)) || !source.equals(parcel(level, pos.relative(movement)))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : helper.getToDestroy()) {
            if (!source.equals(parcel(level, pos))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private String parcel(Level level, BlockPos pos) {
        return StateCraft.runtime().governance().claim(key(level, pos))
                .map(claim -> claim.cityId() + ":" + claim.ownerAccount()).orElse("wilderness");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void fluidBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && StateCraft.runtimeOrNull() != null
                && !parcel(level, event.getPos()).equals(parcel(level, event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void mobGrief(EntityMobGriefingEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && StateCraft.runtimeOrNull() != null
                && StateCraft.runtime().governance().claim(key(level, event.getEntity().blockPosition())).isPresent()) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void mobDestroy(LivingDestroyBlockEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && StateCraft.runtimeOrNull() != null
                && StateCraft.runtime().governance().claim(key(level, event.getPos())).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!allowed(player, event.getPos(), AccessAction.BREAK, null)) {
                event.setCanceled(true);
            }
        } else if (event.getLevel() instanceof ServerLevel level && StateCraft.runtimeOrNull() != null
                && StateCraft.runtime().governance().claim(key(level, event.getPos())).isPresent()) {
            event.setCanceled(true);
        }
    }
}
