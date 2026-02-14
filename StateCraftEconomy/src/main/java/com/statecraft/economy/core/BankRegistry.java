package com.statecraft.economy.core;

import com.statecraft.economy.StateCraftEconomy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Registry for managing multiple banks
 * Provides a default bank and allows custom banks to be created
 */
public class BankRegistry {

    private static final UUID DEFAULT_BANK_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<UUID, Bank> banks = new HashMap<>();
    private UUID defaultBankId = DEFAULT_BANK_ID;

    public BankRegistry() {
        // Create the default bank
        Bank defaultBank = new Bank(DEFAULT_BANK_ID, "default", "Central Bank");
        defaultBank.setInterestRate(0.02); // 2% annual interest
        defaultBank.setColor(0x2196F3); // Blue color
        banks.put(DEFAULT_BANK_ID, defaultBank);
    }

    /**
     * Get the default bank
     */
    public Bank getDefaultBank() {
        return banks.get(defaultBankId);
    }

    /**
     * Get a bank by ID
     */
    public Bank getBank(UUID bankId) {
        return banks.getOrDefault(bankId, getDefaultBank());
    }

    /**
     * Register a new bank
     */
    public void registerBank(Bank bank) {
        if (banks.containsKey(bank.getId())) {
            StateCraftEconomy.LOGGER.warn("Bank with ID {} already exists, overwriting", bank.getId());
        }
        banks.put(bank.getId(), bank);
    }

    /**
     * Remove a bank (cannot remove default bank)
     */
    public boolean removeBank(UUID bankId) {
        if (bankId.equals(defaultBankId)) {
            StateCraftEconomy.LOGGER.warn("Cannot remove default bank");
            return false;
        }
        return banks.remove(bankId) != null;
    }

    /**
     * Get all registered banks
     */
    public Collection<Bank> getAllBanks() {
        return Collections.unmodifiableCollection(banks.values());
    }

    /**
     * Get a bank by name
     */
    public Bank getBankByName(String name) {
        return banks.values().stream()
            .filter(bank -> bank.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if a bank exists
     */
    public boolean bankExists(UUID bankId) {
        return banks.containsKey(bankId);
    }

    /**
     * Set the default bank
     */
    public void setDefaultBank(UUID bankId) {
        if (banks.containsKey(bankId)) {
            this.defaultBankId = bankId;
        } else {
            StateCraftEconomy.LOGGER.warn("Cannot set default bank to non-existent bank {}", bankId);
        }
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("defaultBank", defaultBankId);

        ListTag banksList = new ListTag();
        for (Bank bank : banks.values()) {
            banksList.add(bank.save());
        }
        tag.put("banks", banksList);

        return tag;
    }

    public void load(CompoundTag tag) {
        banks.clear();

        if (tag.contains("defaultBank")) {
            defaultBankId = tag.getUUID("defaultBank");
        }

        ListTag banksList = tag.getList("banks", Tag.TAG_COMPOUND);
        for (int i = 0; i < banksList.size(); i++) {
            Bank bank = Bank.load(banksList.getCompound(i));
            banks.put(bank.getId(), bank);
        }

        // Ensure default bank exists
        if (!banks.containsKey(DEFAULT_BANK_ID)) {
            Bank defaultBank = new Bank(DEFAULT_BANK_ID, "default", "Central Bank");
            defaultBank.setColor(0x2196F3);
            banks.put(DEFAULT_BANK_ID, defaultBank);
        }
    }
}

