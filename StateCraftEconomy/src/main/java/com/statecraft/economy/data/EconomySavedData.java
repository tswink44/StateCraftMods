package com.statecraft.economy.data;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.BankAccount;
import com.statecraft.economy.core.EconomyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
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

        StateCraftEconomy.LOGGER.info("Loaded economy data: {} player accounts, {} nation treasuries, {} state treasuries, {} city treasuries",
            data.playerAccounts.size(), data.nationTreasuries.size(), data.stateTreasuries.size(), data.cityTreasuries.size());

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

    public void markForSave() {
        setDirty();
    }
}

