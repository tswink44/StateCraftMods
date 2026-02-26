package com.statecraft.economy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.BankAccount;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.SpendingLimitManager;
import com.statecraft.economy.core.TaxationManager;
import com.statecraft.economy.core.Transaction;
import com.statecraft.economy.util.NBTUtils;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.company.CompanyEconomyManager;
import com.statecraft.economy.company.BankManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent storage for economy data using JSON files.
 * Data is saved to the world's statecraft/ directory as human-readable JSON.
 *
 * Migration: If the JSON file doesn't exist but the legacy .dat file does,
 * data is loaded from .dat and immediately saved as JSON.
 */
public class EconomySavedData {
    private static final String JSON_DIR = "statecraft";
    private static final String JSON_FILE = "economy.json";
    private static final String LEGACY_DAT_DIR = "data";
    private static final String LEGACY_DAT_FILE = StateCraftEconomy.MOD_ID + "_economy.dat";

    private static EconomySavedData instance;
    private boolean dirty = false;
    private Path savePath;

    private final Map<UUID, BankAccount> playerAccounts = new HashMap<>();
    private final Map<UUID, BankAccount> nationTreasuries = new HashMap<>();
    private final Map<UUID, BankAccount> stateTreasuries = new HashMap<>();
    private final Map<UUID, BankAccount> cityTreasuries = new HashMap<>();
    private final Map<UUID, BankAccount> companyTreasuries = new HashMap<>();

    private EconomySavedData() {}

    public static EconomySavedData getInstance() {
        if (instance == null) {
            instance = new EconomySavedData();
        }
        return instance;
    }

    /**
     * Initialize and load data from JSON (or migrate from legacy NBT).
     */
    public static EconomySavedData init(MinecraftServer server) {
        EconomySavedData data = getInstance();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        data.savePath = worldDir.resolve(JSON_DIR).resolve(JSON_FILE);
        data.loadData(worldDir);
        return data;
    }

    /**
     * Backward-compatible entry point used by existing callers.
     */
    public static EconomySavedData get(ServerLevel level) {
        if (instance == null || instance.savePath == null) {
            return init(level.getServer());
        }
        return instance;
    }

    // ======================== Load ========================

    private void loadData(Path worldDir) {
        if (Files.exists(savePath)) {
            loadFromJson();
        } else {
            // Try legacy .dat migration
            Path legacyPath = worldDir.resolve(LEGACY_DAT_DIR).resolve(LEGACY_DAT_FILE);
            if (Files.exists(legacyPath)) {
                StateCraftEconomy.LOGGER.info("Migrating legacy economy NBT data to JSON...");
                loadFromLegacyDat(legacyPath);
                // Immediately save as JSON
                saveToJson();
                // Rename the old .dat
                try {
                    Files.move(legacyPath, legacyPath.resolveSibling(LEGACY_DAT_FILE + ".migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
                    StateCraftEconomy.LOGGER.info("Legacy economy .dat file renamed to {}.migrated", LEGACY_DAT_FILE);
                } catch (IOException e) {
                    StateCraftEconomy.LOGGER.warn("Could not rename legacy economy .dat file: {}", e.getMessage());
                }
            } else {
                StateCraftEconomy.LOGGER.info("No existing economy data found, starting fresh");
            }
        }
    }

    private void loadFromJson() {
        try {
            String json = Files.readString(savePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            CompoundTag tag = NbtJsonConverter.fromJson(root);
            loadFromNbt(tag);
            StateCraftEconomy.LOGGER.info("Loaded economy data from JSON");
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Failed to load economy data from JSON!", e);
        }
    }

    private void loadFromLegacyDat(Path datPath) {
        try (InputStream is = Files.newInputStream(datPath)) {
            CompoundTag root = NbtIo.readCompressed(is);
            CompoundTag tag = root.contains("data") ? root.getCompound("data") : root;
            loadFromNbt(tag);
            StateCraftEconomy.LOGGER.info("Loaded economy data from legacy .dat file");
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Failed to load legacy economy .dat file!", e);
        }
    }

    /**
     * Core NBT loading logic — shared by JSON and legacy paths.
     */
    private void loadFromNbt(CompoundTag tag) {
        // Load bank registry
        if (tag.contains("BankRegistry")) {
            EconomyManager.getInstance().getBankRegistry().load(tag.getCompound("BankRegistry"));
        }

        // Load player accounts
        playerAccounts.clear();
        ListTag playerList = tag.getList("PlayerAccounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag accountTag = playerList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            long lastInterest = accountTag.getLong("LastInterest");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.PLAYER, bankId, balance);
            account.setLastInterestTime(lastInterest);
            playerAccounts.put(id, account);
        }

        // Load nation treasuries
        nationTreasuries.clear();
        ListTag nationList = tag.getList("NationTreasuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < nationList.size(); i++) {
            CompoundTag accountTag = nationList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.NATION, bankId, balance);
            nationTreasuries.put(id, account);
        }

        // Load state treasuries
        stateTreasuries.clear();
        ListTag stateList = tag.getList("StateTreasuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < stateList.size(); i++) {
            CompoundTag accountTag = stateList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.STATE, bankId, balance);
            stateTreasuries.put(id, account);
        }

        // Load city treasuries
        cityTreasuries.clear();
        ListTag cityList = tag.getList("CityTreasuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < cityList.size(); i++) {
            CompoundTag accountTag = cityList.getCompound(i);
            UUID id = accountTag.getUUID("Id");
            double balance = accountTag.getDouble("Balance");
            UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

            BankAccount account = new BankAccount(id, BankAccount.AccountType.CITY, bankId, balance);
            cityTreasuries.put(id, account);
        }

        // Load company treasuries
        companyTreasuries.clear();
        if (tag.contains("CompanyTreasuries")) {
            ListTag companyList = tag.getList("CompanyTreasuries", Tag.TAG_COMPOUND);
            for (int i = 0; i < companyList.size(); i++) {
                CompoundTag accountTag = companyList.getCompound(i);
                UUID id = accountTag.getUUID("Id");
                double balance = accountTag.getDouble("Balance");
                UUID bankId = accountTag.contains("BankId") ? accountTag.getUUID("BankId") : null;

                BankAccount account = new BankAccount(id, BankAccount.AccountType.COMPANY, bankId, balance);
                companyTreasuries.put(id, account);
            }
        }

        StateCraftEconomy.LOGGER.info("Loaded economy data: {} player accounts, {} nation treasuries, {} state treasuries, {} city treasuries, {} company treasuries",
            playerAccounts.size(), nationTreasuries.size(), stateTreasuries.size(), cityTreasuries.size(), companyTreasuries.size());

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

        // Load bank manager state
        if (tag.contains("BankManager")) {
            BankManager.getInstance().load(tag.getCompound("BankManager"));
        }

        // Load marketplace manager state
        if (tag.contains("MarketplaceManager")) {
            com.statecraft.economy.marketplace.MarketplaceManager.getInstance().load(tag.getCompound("MarketplaceManager"));
        }

        // Load stock market manager state
        if (tag.contains("StockMarketManager")) {
            com.statecraft.economy.stockmarket.StockMarketManager.getInstance().load(tag.getCompound("StockMarketManager"));
        }
    }

    // ======================== Save ========================

    /**
     * Save all economy data to JSON.
     */
    public void saveToJson() {
        if (savePath == null) return;

        try {
            CompoundTag tag = saveToNbt();
            JsonObject json = NbtJsonConverter.toJson(tag);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(json);

            Files.createDirectories(savePath.getParent());
            Path tmpPath = savePath.resolveSibling(JSON_FILE + ".tmp");
            Files.writeString(tmpPath, jsonStr);
            try {
                Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING);
            }

            clearDirty();
            StateCraftEconomy.LOGGER.debug("Saved economy data to JSON");
        } catch (IOException e) {
            StateCraftEconomy.LOGGER.error("Failed to save economy data to JSON!", e);
        }
    }

    /**
     * Core NBT save logic — builds the full CompoundTag from all managers.
     */
    private CompoundTag saveToNbt() {
        CompoundTag tag = new CompoundTag();
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
                NBTUtils.putSanitizedString(txTag, "Description", tx.getDescription());
                txTag.putLong("Timestamp", tx.getTimestamp());
                if (tx.getInitiatorId() != null) {
                    txTag.putUUID("InitiatorId", tx.getInitiatorId());
                }
                if (tx.getInitiatorName() != null) {
                    NBTUtils.putSanitizedString(txTag, "InitiatorName", tx.getInitiatorName());
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

        // Save stock market manager state
        tag.put("StockMarketManager", com.statecraft.economy.stockmarket.StockMarketManager.getInstance().save());
        com.statecraft.economy.stockmarket.StockMarketManager.getInstance().clearDirty();

        manager.clearDirty();
        return tag;
    }

    // ======================== Dirty tracking ========================

    public void markForSave() {
        this.dirty = true;
    }

    public boolean isDirtyCheck() {
        EconomyManager manager = EconomyManager.getInstance();
        TaxationManager taxManager = TaxationManager.getInstance();
        SpendingLimitManager spendingMgr = SpendingLimitManager.getInstance();
        CompanyManager companyMgr = CompanyManager.getInstance();
        CompanyEconomyManager companyEcoMgr = CompanyEconomyManager.getInstance();
        BankManager bankMgr = BankManager.getInstance();
        com.statecraft.economy.marketplace.MarketplaceManager marketMgr = com.statecraft.economy.marketplace.MarketplaceManager.getInstance();
        com.statecraft.economy.stockmarket.StockMarketManager stockMgr = com.statecraft.economy.stockmarket.StockMarketManager.getInstance();
        return dirty || manager.isDirty() || taxManager.isDirty() || spendingMgr.isDirty() ||
            companyMgr.isDirty() || companyEcoMgr.isDirty() || bankMgr.isDirty() ||
            marketMgr.isDirty() || stockMgr.isDirty();
    }

    private void clearDirty() {
        this.dirty = false;
    }

    /**
     * Save only if dirty.
     */
    public void saveIfDirty() {
        if (isDirtyCheck()) {
            saveToJson();
        }
    }

    /**
     * Reset the singleton (called on server stop).
     */
    public static void resetInstance() {
        instance = null;
    }

    // ======================== Getters for EconomyManager ========================

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
}

