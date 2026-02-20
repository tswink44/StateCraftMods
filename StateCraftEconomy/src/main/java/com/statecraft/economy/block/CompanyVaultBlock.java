package com.statecraft.economy.block;

import com.statecraft.economy.block.entity.CompanyVaultBlockEntity;
import com.statecraft.economy.block.entity.ModBlockEntities;
import com.statecraft.economy.company.Company;
import com.statecraft.economy.company.CompanyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Company Vault Block — a shared storage container linked to a company.
 * Only company officers can access its inventory.
 * When placed, the placer assigns it to one of their companies.
 * 54 slots (double chest equivalent).
 */
public class CompanyVaultBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public CompanyVaultBlock(Properties properties) {
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

        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CompanyVaultBlockEntity vault) {
                // Auto-assign to the player's first managed company
                var managedCompanies = CompanyManager.getInstance().getPlayerManagedCompanies(player.getUUID());
                if (!managedCompanies.isEmpty()) {
                    Company company = managedCompanies.get(0);
                    vault.setCompanyId(company.getId());
                    vault.setCompanyName(company.getName());
                    player.sendSystemMessage(Component.literal(
                        "§a[Vault] Linked to company: §f" + company.getName() +
                        "§a. Use §f/eco company vault assign <name>§a to change."));
                } else {
                    player.sendSystemMessage(Component.literal(
                        "§c[Vault] You don't manage any companies. Use §f/eco company vault assign <name>§c after creating one."));
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CompanyVaultBlockEntity vault) {
                UUID companyId = vault.getCompanyId();
                if (companyId == null) {
                    player.sendSystemMessage(Component.literal(
                        "§c[Vault] This vault is not assigned to any company."));
                    return InteractionResult.CONSUME;
                }

                Company company = CompanyManager.getInstance().getCompany(companyId);
                if (company == null) {
                    player.sendSystemMessage(Component.literal(
                        "§c[Vault] The company this vault belongs to no longer exists."));
                    return InteractionResult.CONSUME;
                }

                // Check officer access
                if (!company.isOfficer(player.getUUID())) {
                    player.sendSystemMessage(Component.literal(
                        "§c[Vault] Only officers of §f" + company.getName() + "§c can access this vault."));
                    return InteractionResult.CONSUME;
                }

                // Open the vault GUI
                NetworkHooks.openScreen((ServerPlayer) player, vault, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanyVaultBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CompanyVaultBlockEntity vault) {
                vault.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}


