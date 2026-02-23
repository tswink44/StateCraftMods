package com.statecraft.economy.block.entity;

import com.statecraft.company.Company;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.gui.CompanyVaultMenu;
import com.statecraft.economy.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Block entity for the Company Vault — shared storage for company officers.
 * 54 slots (double chest equivalent).
 * Linked to a specific company by UUID.
 */
public class CompanyVaultBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SIZE = 54;

    private final ItemStackHandler itemHandler = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    private UUID companyId;
    private String companyName = "";

    public CompanyVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPANY_VAULT.get(), pos, state);
    }

    // ==================== Company Link ====================

    @Nullable
    public UUID getCompanyId() { return companyId; }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
        setChanged();
    }

    public String getCompanyName() { return companyName; }

    public void setCompanyName(String name) {
        this.companyName = name != null ? name : "";
        setChanged();
    }

    // ==================== MenuProvider ====================

    @Override
    public Component getDisplayName() {
        if (companyName != null && !companyName.isEmpty()) {
            return Component.literal(companyName + " Vault");
        }
        return Component.literal("Company Vault");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CompanyVaultMenu(containerId, playerInventory, this);
    }

    // ==================== Capability ====================

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    // ==================== Item Handler Access ====================

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // ==================== NBT Persistence ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        if (companyId != null) {
            tag.putUUID("CompanyId", companyId);
        }
        NBTUtils.putSanitizedString(tag, "CompanyName", companyName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }
        if (tag.contains("CompanyId")) {
            companyId = tag.getUUID("CompanyId");
        }
        if (tag.contains("CompanyName")) {
            companyName = tag.getString("CompanyName");
        }
    }
}

