package com.statecraft.economy.gui;

import com.statecraft.economy.block.entity.TradingHubBlockEntity;
import com.statecraft.economy.config.ItemValueConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Menu (Container) for the Trading Hub block
 * Has a single slot for items to sell
 */
public class TradingHubMenu extends AbstractContainerMenu {
    private final TradingHubBlockEntity blockEntity;
    private final Player player;
    private final ContainerLevelAccess access;

    // Slot indices
    public static final int SELL_SLOT = 0;
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int PLAYER_HOTBAR_START = 28;
    private static final int PLAYER_HOTBAR_END = 37;

    // Client-side constructor (from network)
    public TradingHubMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getTradingHubEntity(playerInventory, extraData));
    }

    // Server-side constructor
    public TradingHubMenu(int containerId, Inventory playerInventory, TradingHubBlockEntity blockEntity) {
        super(ModMenuTypes.TRADING_HUB.get(), containerId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // Add sell slot (centered at top)
        IItemHandler itemHandler = blockEntity.getItemHandler();
        this.addSlot(new SlotItemHandler(itemHandler, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Only accept items that can be sold
                return ItemValueConfig.canSell(stack);
            }
        });

        // Add player inventory slots
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    private static TradingHubBlockEntity getTradingHubEntity(Inventory playerInventory, FriendlyByteBuf extraData) {
        var pos = extraData.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof TradingHubBlockEntity tradingHub) {
            return tradingHub;
        }
        throw new IllegalStateException("Block entity at " + pos + " is not a TradingHubBlockEntity");
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            if (index == SELL_SLOT) {
                // From sell slot to player inventory
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
                // From player inventory
                // Try sell slot first if item can be sold
                if (ItemValueConfig.canSell(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, SELL_SLOT, SELL_SLOT + 1, false)) {
                        // Then try hotbar
                        if (!this.moveItemStackTo(slotStack, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else {
                    // Item can't be sold, move to hotbar
                    if (!this.moveItemStackTo(slotStack, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (index >= PLAYER_HOTBAR_START && index < PLAYER_HOTBAR_END) {
                // From hotbar
                // Try sell slot first if item can be sold
                if (ItemValueConfig.canSell(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, SELL_SLOT, SELL_SLOT + 1, false)) {
                        // Then try inventory
                        if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else {
                    // Item can't be sold, move to inventory
                    if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, blockEntity.getBlockState().getBlock());
    }

    /**
     * Get the block entity
     */
    public TradingHubBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * Get the current sell value of items in the slot
     */
    public double getCurrentSellValue() {
        return blockEntity.calculateSellValue();
    }

    /**
     * Get the current tax amount
     */
    public double getCurrentTaxAmount() {
        return blockEntity.calculateTaxAmount();
    }

    /**
     * Perform the sale (called from client via packet)
     */
    public boolean performSale() {
        return blockEntity.sellItems(player);
    }
}

