package com.statecraft.economy.block.entity;

import com.statecraft.economy.config.ItemValueConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.gui.TradingHubMenu;
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
 * Block entity for Trading Hub - handles inventory and selling logic
 */
public class TradingHubBlockEntity extends BlockEntity implements MenuProvider {

    // Slot for items to sell
    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // Only accept items that have a sell value
            return ItemValueConfig.canSell(stack);
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    // Statistics tracking
    private long totalSold = 0;
    private double totalEarnings = 0;

    public TradingHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADING_HUB.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Trading Hub");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TradingHubMenu(containerId, playerInventory, this);
    }

    /**
     * Get the item handler for the sell slot
     */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * Get the current item in the sell slot
     */
    public ItemStack getSellSlotItem() {
        return itemHandler.getStackInSlot(0);
    }

    /**
     * Calculate the value of the current item in the sell slot
     */
    public double calculateSellValue() {
        ItemStack stack = getSellSlotItem();
        if (stack.isEmpty()) return 0;

        double baseValue = ItemValueConfig.getStackValue(stack);

        // Apply base tax rate from config
        double taxRate = ItemValueConfig.SELL_TAX_RATE.get();
        double afterTax = baseValue * (1.0 - taxRate);

        // TODO: Hook into StateCraft for nation taxation
        // This will be implemented later to add nation-specific taxes

        return afterTax;
    }

    /**
     * Calculate the tax amount for the current item
     */
    public double calculateTaxAmount() {
        ItemStack stack = getSellSlotItem();
        if (stack.isEmpty()) return 0;

        double baseValue = ItemValueConfig.getStackValue(stack);
        double taxRate = ItemValueConfig.SELL_TAX_RATE.get();

        // TODO: Add nation tax rate when StateCraft integration is implemented

        return baseValue * taxRate;
    }

    /**
     * Sell the items in the sell slot
     * @param player The player selling the items
     * @return true if the sale was successful
     */
    public boolean sellItems(Player player) {
        ItemStack stack = getSellSlotItem();
        if (stack.isEmpty()) return false;

        if (!ItemValueConfig.canSell(stack)) return false;

        double value = calculateSellValue();
        if (value <= 0) return false;

        // Add money to player's balance
        UUID playerId = player.getUUID();
        EconomyManager.getInstance().deposit(playerId, value, "Trading Hub sale");

        // Track statistics
        totalSold += stack.getCount();
        totalEarnings += value;

        // Remove the items
        itemHandler.setStackInSlot(0, ItemStack.EMPTY);
        setChanged();

        return true;
    }

    /**
     * Drop all contents when block is broken
     */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // Capability handling
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

    // NBT Serialization
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putLong("totalSold", totalSold);
        tag.putDouble("totalEarnings", totalEarnings);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("inventory"));
        }
        totalSold = tag.getLong("totalSold");
        totalEarnings = tag.getDouble("totalEarnings");
    }

    // Statistics getters
    public long getTotalSold() {
        return totalSold;
    }

    public double getTotalEarnings() {
        return totalEarnings;
    }
}

