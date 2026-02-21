package com.statecraft.economy.data;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.BankAccount;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.SpendingLimitManager;
import com.statecraft.economy.core.TaxationManager;
import com.statecraft.economy.core.Transaction;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.company.CompanyEconomyManager;
import com.statecraft.economy.company.BankManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent storage for economy data
 */
public class EconomySavedData extends SavedData {
    private static final String DATA_NAME = StateCraftEconomy.MOD_ID + "_economy";

    private final Map<UUID, BankAccount> playerAccounts = new HashMap<>();
    private final Map<UUID, BankAccount> nationTreasuries = new HashMap<>();
    private final Map<UUID, BankAccount> stateTreasuries = new HashMap<>();
    private final Map<UUID, BankAccount> cityTreasuries = new HashMap<>();
    private final Map<UUID, BankAccount> companyTreasuries = new HashMap<>();

    public EconomySavedData() {
        super();
    }

    public static EconomySavedData load(CompoundTag tag) {
        EconomySavedData data = new EconomySavedData();

        // Load bank registry
        if (tag.contains("BankRegistry")) {
            EconomyManager.getInstance().getBankRegistry().load(tag.getCompound("BankRegistry"));
        }

        // Load player accounts
        ListTag playerList = tag.getList("PlayerAccounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag accountTag = playerList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            long lastInterest = accountTag.getLong("LastInterest");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.PLAYER, bankId, balance);
            account.setLastInterestTime(lastInterest);
            data.playerAccounts.put(id, account);
        }

        // Load nation treasuries
        ListTag nationList = tag.getList("NationTreasuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < nationList.size(); i++) {
            CompoundTag accountTag = nationList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.NATION, bankId, balance);
            data.nationTreasuries.put(id, account);
        }

        // Load state treasuries
        ListTag stateList = tag.getList("StateTreasuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < stateList.size(); i++) {
            CompoundTag accountTag = stateList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.STATE, bankId, balance);
            data.stateTreasuries.put(id, account);
        }

        // Load city treasuries
        ListTag cityList = tag.getList("CityTreasuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < cityList.size(); i++) {
            CompoundTag accountTag = cityList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.CITY, bankId, balance);
            data.cityTreasuries.put(id, account);
        }

        // Load company treasuries
        if (tag.contains("CompanyTreasuries")) {
            ListTag companyList = tag.getList("CompanyTreasuries", Tag.TAG_COMPOUND);
            for (int i = 0; i < companyList.size(); i++) {
                CompoundTag accountTag = companyList.getCompound(i);
                UUID id = accountTag.getUUID("Id");
                double balance = accountTag.getDouble("Balance");
                UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

                BankAccount account = new BankAccount(id, BankAccount.AccountType.COMPANY, bankId, balance);
                data.companyTreasuries.put(id, account);
            }
        }

        StateCraftEconomy.LOGGER.info("Loaded economy data: {} player accounts, {} nation treasuries, {} state treasuries, {} city treasuries, {} company treasuries",
            data.playerAccounts.size(), data.nationTreasuries.size(), data.stateTreasuries.size(), data.cityTreasuries.size(), data.companyTreasuries.size());

        // Load transaction history
        if (tag.contains("TransactionHistory")) {
            Map<UUID, List<Transaction>> history = new HashMap<>();
            ListTag historyList = tag.getList("TransactionHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag accountHistoryTag = historyList.getCompound(i);
                UUID accountId = accountHistoryTag.getUUID("AccountId");
                ListTag txList = accountHistoryTag.getList("Transactions", Tag.TAG_COMPOUND);
                List<Transaction> transactions = new ArrayList<>();
                for (int j = 0; j < txList.size(); j++) {
                    CompoundTag txTag = txList.getCompound(j);
                    Transaction.Type type = Transaction.Type.valueOf(txTag.getString("Type"));
                    double amount = txTag.getDouble("Amount");
                    UUID otherId = txTag.contains("OtherId") ? txTag.getUUID("OtherId") : null;
                    String description = txTag.getString("Description");
                    long timestamp = txTag.getLong("Timestamp");
                    UUID initiatorId = txTag.contains("InitiatorId") ? txTag.getUUID("InitiatorId") : null;
                    String initiatorName = txTag.contains("InitiatorName") ? txTag.getString("InitiatorName") : null;
                    transactions.add(new Transaction(type, amount, otherId, description, timestamp, initiatorId, initiatorName));
                }
                history.put(accountId, transactions);
            }
            EconomyManager.getInstance().setTransactionHistory(history);
            StateCraftEconomy.LOGGER.info("Loaded transaction history for {} accounts", history.size());
        }

        // Load taxation manager state
        if (tag.contains("TaxationManager")) {
            TaxationManager.getInstance().load(tag.getCompound("TaxationManager"));
        }

        // Load spending limit manager state
        if (tag.contains("SpendingLimitManager")) {
            SpendingLimitManager.getInstance().load(tag.getCompound("SpendingLimitManager"));
        }

        // Load company manager state
        if (tag.contains("CompanyManager")) {
            CompoundTag companyManagerTag = tag.getCompound("CompanyManager");
            CompanyManager.getInstance().load(companyManagerTag);

            // Load CompanyEconomyManager dividend configs
            if (tag.contains("CompanyEconomyManager")) {
                CompanyEconomyManager.getInstance().load(tag.getCompound("CompanyEconomyManager"));
            } else {
                // Legacy migration: dividend data was stored inside Company tags
                CompanyEconomyManager.getInstance().migrateFromLegacyCompanyData(companyManagerTag);
            }
        }

        // Load bank manager state (must be after CompanyManager since it references companies)
        if (tag.contains("BankManager")) {
            BankManager.getInstance().load(tag.getCompound("BankManager"));
        }

        // Load marketplace manager state
        if (tag.contains("MarketplaceManager")) {
            com.statecraft.economy.marketplace.MarketplaceManager.getInstance().load(tag.getCompound("MarketplaceManager"));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        // Sync from manager
        EconomyManager manager = EconomyManager.getInstance();

        // Save bank registry
        tag.put("BankRegistry", manager.getBankRegistry().save());

        // Save player accounts
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, BankAccount> entry : manager.getPlayerAccounts().entrySet()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("Id", entry.getKey());
            accountTag.putDouble("Balance", entry.getValue().getBalance());
            accountTag.putLong("LastInterest", entry.getValue().getLastInterestTime());
            if (entry.getValue().getBankId() != null) {
                accountTag.putUUID("BankId", entry.getValue().getBankId());
            }
            playerList.add(accountTag);
        }
        tag.put("PlayerAccounts", playerList);

        // Save nation treasuries
        ListTag nationList = new ListTag();
        for (Map.Entry<UUID, BankAccount> entry : manager.getNationTreasuries().entrySet()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("Id", entry.getKey());
            accountTag.putDouble("Balance", entry.getValue().getBalance());
            if (entry.getValue().getBankId() != null) {
                accountTag.putUUID("BankId", entry.getValue().getBankId());
            }
            nationList.add(accountTag);
        }
        tag.put("NationTreasuries", nationList);

        // Save state treasuries
        ListTag stateList = new ListTag();
        for (Map.Entry<UUID, BankAccount> entry : manager.getStateTreasuries().entrySet()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("Id", entry.getKey());
            accountTag.putDouble("Balance", entry.getValue().getBalance());
            if (entry.getValue().getBankId() != null) {
                accountTag.putUUID("BankId", entry.getValue().getBankId());
            }
            stateList.add(accountTag);
        }
        tag.put("StateTreasuries", stateList);

        // Save city treasuries
        ListTag cityList = new ListTag();
        for (Map.Entry<UUID, BankAccount> entry : manager.getCityTreasuries().entrySet()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("Id", entry.getKey());
            accountTag.putDouble("Balance", entry.getValue().getBalance());
            if (entry.getValue().getBankId() != null) {
                accountTag.putUUID("BankId", entry.getValue().getBankId());
            }
            cityList.add(accountTag);
        }
        tag.put("CityTreasuries", cityList);

        // Save company treasuries
        ListTag companyList = new ListTag();
        for (Map.Entry<UUID, BankAccount> entry : manager.getCompanyTreasuries().entrySet()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("Id", entry.getKey());
            accountTag.putDouble("Balance", entry.getValue().getBalance());
            if (entry.getValue().getBankId() != null) {
                accountTag.putUUID("BankId", entry.getValue().getBankId());
            }
            companyList.add(accountTag);
        }
        tag.put("CompanyTreasuries", companyList);

        // Save transaction history
        ListTag historyList = new ListTag();
        Map<UUID, List<Transaction>> transactionHistory = manager.getTransactionHistoryMap();
        for (Map.Entry<UUID, List<Transaction>> entry : transactionHistory.entrySet()) {
            CompoundTag accountHistoryTag = new CompoundTag();
            accountHistoryTag.putUUID("AccountId", entry.getKey());
            ListTag txList = new ListTag();
            for (Transaction tx : entry.getValue()) {
                CompoundTag txTag = new CompoundTag();
                txTag.putString("Type", tx.getType().name());
                txTag.putDouble("Amount", tx.getAmount());
                if (tx.getOtherId() != null) {
                    txTag.putUUID("OtherId", tx.getOtherId());
                }
                txTag.putString("Description", tx.getDescription());
                txTag.putLong("Timestamp", tx.getTimestamp());
                if (tx.getInitiatorId() != null) {
                    txTag.putUUID("InitiatorId", tx.getInitiatorId());
                }
                if (tx.getInitiatorName() != null) {
                    txTag.putString("InitiatorName", tx.getInitiatorName());
                }
                txList.add(txTag);
            }
            accountHistoryTag.put("Transactions", txList);
            historyList.add(accountHistoryTag);
        }
        tag.put("TransactionHistory", historyList);

        // Save taxation manager state
        tag.put("TaxationManager", TaxationManager.getInstance().save());
        TaxationManager.getInstance().clearDirty();

        // Save spending limit manager state
        tag.put("SpendingLimitManager", SpendingLimitManager.getInstance().save());
        SpendingLimitManager.getInstance().clearDirty();

        // Save company manager state
        tag.put("CompanyManager", CompanyManager.getInstance().save());
        CompanyManager.getInstance().clearDirty();

        // Save company economy manager (dividend configs)
        tag.put("CompanyEconomyManager", CompanyEconomyManager.getInstance().save());
        CompanyEconomyManager.getInstance().clearDirty();

        // Save bank manager state
        tag.put("BankManager", BankManager.getInstance().save());
        BankManager.getInstance().clearDirty();

        // Save marketplace manager state
        tag.put("MarketplaceManager", com.statecraft.economy.marketplace.MarketplaceManager.getInstance().save());
        com.statecraft.economy.marketplace.MarketplaceManager.getInstance().clearDirty();

        manager.clearDirty();
        return tag;
    }

    public static EconomySavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(EconomySavedData::load, EconomySavedData::new, DATA_NAME);
    }

    public Map<UUID, BankAccount> getPlayerAccounts() {
        return playerAccounts;
    }

    public Map<UUID, BankAccount> getNationTreasuries() {
        return nationTreasuries;
    }

    public Map<UUID, BankAccount> getStateTreasuries() {
        return stateTreasuries;
    }

    public Map<UUID, BankAccount> getCityTreasuries() {
        return cityTreasuries;
    }

    public Map<UUID, BankAccount> getCompanyTreasuries() {
        return companyTreasuries;
    }

    public void markForSave() {
        setDirty();
    }
}

