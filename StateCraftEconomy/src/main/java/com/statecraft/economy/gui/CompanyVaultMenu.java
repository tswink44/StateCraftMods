package com.statecraft.economy.gui;

import com.statecraft.economy.block.entity.CompanyVaultBlockEntity;
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
 * Menu (Container) for the Company Vault block.
 * Double-chest layout (54 slots = 6 rows of 9).
 */
public class CompanyVaultMenu extends AbstractContainerMenu {
    private final CompanyVaultBlockEntity blockEntity;
    private final Player player;
    private final ContainerLevelAccess access;

    // Slot indices — 6 rows of 9 vault slots + player inventory
    public static final int VAULT_SLOTS = 54;
    private static final int VAULT_START = 0;
    private static final int VAULT_END = 54;
    private static final int PLAYER_INV_START = 54;
    private static final int PLAYER_INV_END = 81;
    private static final int PLAYER_HOTBAR_START = 81;
    private static final int PLAYER_HOTBAR_END = 90;

    // Client-side constructor (from network)
    public CompanyVaultMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getVaultEntity(playerInventory, extraData));
    }

    // Server-side constructor
    public CompanyVaultMenu(int containerId, Inventory playerInventory, CompanyVaultBlockEntity blockEntity) {
        super(ModMenuTypes.COMPANY_VAULT.get(), containerId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // Add vault inventory slots (6 rows of 9, like a double chest)
        IItemHandler itemHandler = blockEntity.getItemHandler();
        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotIndex = col + row * 9;
                this.addSlot(new SlotItemHandler(itemHandler, slotIndex, 8 + col * 18, 18 + row * 18));
            }
        }

        // Add player inventory slots (below vault)
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    private static CompanyVaultBlockEntity getVaultEntity(Inventory playerInventory, FriendlyByteBuf extraData) {
        var pos = extraData.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof CompanyVaultBlockEntity vault) {
            return vault;
        }
        throw new IllegalStateException("Block entity at " + pos + " is not a CompanyVaultBlockEntity");
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // Player inventory — positioned below the 6-row vault
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack retStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            retStack = slotStack.copy();

            if (index < VAULT_END) {
                // Moving from vault to player inventory
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < PLAYER_HOTBAR_END) {
                // Moving from player inventory to vault
                if (!this.moveItemStackTo(slotStack, VAULT_START, VAULT_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return retStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> player.distanceToSqr(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    public CompanyVaultBlockEntity getBlockEntity() {
        return blockEntity;
    }
}

