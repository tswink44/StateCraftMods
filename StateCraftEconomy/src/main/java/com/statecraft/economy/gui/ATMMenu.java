package com.statecraft.economy.gui;

import com.statecraft.economy.core.EconomyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.UUID;

/**
 * Menu (Container) for the ATM block
 * No longer uses deposit slot - deposits are handled via inventory scanning
 */
public class ATMMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Player player;
    private final UUID bankId;

    public ATMMenu(int containerId, Inventory playerInventory, BlockPos pos, UUID bankId) {
        super(ModMenuTypes.ATM.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.bankId = bankId;


        // Add player inventory slots
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
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

            // Player inventory: 0-26 (3x9 grid)
            // Hotbar: 27-35 (1x9)
            if (index < 27) {
                // From inventory to hotbar
                if (!this.moveItemStackTo(slotStack, 27, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From hotbar to inventory
                if (!this.moveItemStackTo(slotStack, 0, 27, false)) {
                    return ItemStack.EMPTY;
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
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public BlockPos getPos() {
        return pos;
    }

    public UUID getBankId() {
        return bankId;
    }
}

