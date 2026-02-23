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
 * Has a chest-like layout (27 slots) for bulk selling
 */
public class TradingHubMenu extends AbstractContainerMenu {
    private final TradingHubBlockEntity blockEntity;
    private final Player player;
    private final ContainerLevelAccess access;

    // Slot indices - chest-like layout (3 rows of 9)
    public static final int TRADING_HUB_SLOTS = 27;
    private static final int TRADING_HUB_START = 0;
    private static final int TRADING_HUB_END = 27;
    private static final int PLAYER_INV_START = 27;
    private static final int PLAYER_INV_END = 54;
    private static final int PLAYER_HOTBAR_START = 54;
    private static final int PLAYER_HOTBAR_END = 63;

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

        // Add trading hub inventory slots (3 rows of 9, like a chest)
        IItemHandler itemHandler = blockEntity.getItemHandler();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotIndex = col + row * 9;
                this.addSlot(new SlotItemHandler(itemHandler, slotIndex, 8 + col * 18, 18 + row * 18));
            }
        }

        // Add player inventory slots (below trading hub inventory)
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
        // Player inventory slots - aligned with player inv texture at topPos+85
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 100 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        // Hotbar slots - aligned with player inv texture
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 158));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            if (index < TRADING_HUB_END) {
                // From trading hub to player inventory
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
                // From player inventory - try trading hub first, then hotbar
                if (!this.moveItemStackTo(slotStack, TRADING_HUB_START, TRADING_HUB_END, false)) {
                    if (!this.moveItemStackTo(slotStack, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (index >= PLAYER_HOTBAR_START && index < PLAYER_HOTBAR_END) {
                // From hotbar - try trading hub first, then inventory
                if (!this.moveItemStackTo(slotStack, TRADING_HUB_START, TRADING_HUB_END, false)) {
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
     * Get the current total sell value of all items
     */
    public double getCurrentSellValue() {
        return blockEntity.calculateTotalSellValue();
    }

    /**
     * Get the current total tax amount
     */
    public double getCurrentTaxAmount() {
        return blockEntity.calculateTotalTaxAmount();
    }

    /**
     * Get the count of sellable items
     */
    public int getSellableItemCount() {
        return blockEntity.countSellableItems();
    }

    /**
     * Check if player can modify settings
     */
    public boolean canModifySettings() {
        return blockEntity.canModifySettings(player);
    }
}

