package com.statecraft.economy.block;

import com.statecraft.economy.block.entity.MarketplaceBlockEntity;
import com.statecraft.economy.block.entity.ModBlockEntities;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.OpenMarketplaceScreenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Marketplace Block — Players interact to browse/list items on the global marketplace.
 * Must be placed in a claimed city chunk if requireCityPlacement config is true.
 */
public class MarketplaceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public MarketplaceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MarketplaceBlockEntity marketplace) {
                marketplace.setOwner(player);

                // Enforce city placement requirement
                if (EconomyConfig.REQUIRE_CITY_PLACEMENT.get() && !marketplace.isInCity()) {
                    level.destroyBlock(pos, true);
                    player.sendSystemMessage(Component.literal("§cMarketplace blocks must be placed in a claimed city!"));
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!EconomyConfig.MARKETPLACE_ENABLED.get()) {
                player.sendSystemMessage(Component.literal("§cThe marketplace is currently disabled."));
                return InteractionResult.CONSUME;
            }
            // Send packet to open the marketplace screen (no inventory/container)
            NetworkHandler.sendToPlayer(new OpenMarketplaceScreenPacket(), (ServerPlayer) player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketplaceBlockEntity(pos, state);
    }
}

