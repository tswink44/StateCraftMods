package com.statecraft.economy.block.entity;

import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.gui.ATMMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Block entity for ATM - handles the menu provider and bank association
 */
public class ATMBlockEntity extends BlockEntity implements MenuProvider {

    private UUID bankId; // Which bank this ATM belongs to

    public ATMBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATM.get(), pos, state);
        this.bankId = null; // Will default to central bank
    }

    public UUID getBankId() {
        if (bankId == null) {
            // Default to the central bank
            bankId = EconomyManager.getInstance().getBankRegistry().getDefaultBank().getId();
        }
        return bankId;
    }

    public void setBankId(UUID bankId) {
        this.bankId = bankId;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        if (bankId != null) {
            var bank = EconomyManager.getInstance().getBankRegistry().getBank(bankId);
            return Component.literal(bank.getDisplayName() + " ATM");
        }
        return Component.literal("ATM");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ATMMenu(containerId, playerInventory, this.worldPosition, getBankId());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (bankId != null) {
            tag.putUUID("bankId", bankId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("bankId")) {
            this.bankId = tag.getUUID("bankId");
        }
    }
}

