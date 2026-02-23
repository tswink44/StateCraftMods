package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client packet containing available accounts for the player
 * Includes government accounts they have admin access to
 * Also includes the full bank list for the ATM bank selection dropdown
 */
public class SyncAccountsPacket {

    private final List<AccountInfo> accounts;
    private final List<BankInfo> banks;

    public SyncAccountsPacket(List<AccountInfo> accounts, List<BankInfo> banks) {
        this.accounts = accounts;
        this.banks = banks;
    }

    public SyncAccountsPacket(List<AccountInfo> accounts) {
        this(accounts, new ArrayList<>());
    }

    public SyncAccountsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.accounts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String type = buf.readUtf();
            String name = buf.readUtf();
            String id = buf.readUtf();
            double balance = buf.readDouble();
            accounts.add(new AccountInfo(type, name, id, balance));
        }

        int bankCount = buf.readVarInt();
        this.banks = new ArrayList<>(bankCount);
        for (int i = 0; i < bankCount; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String displayName = buf.readUtf();
            double interestRate = buf.readDouble();
            double withdrawalFee = buf.readDouble();
            double transferFee = buf.readDouble();
            boolean allowsLoans = buf.readBoolean();
            int color = buf.readVarInt();
            banks.add(new BankInfo(id, name, displayName, interestRate, withdrawalFee, transferFee, allowsLoans, color));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(accounts.size());
        for (AccountInfo account : accounts) {
            buf.writeUtf(account.type());
            buf.writeUtf(account.name());
            buf.writeUtf(account.id());
            buf.writeDouble(account.balance());
        }

        buf.writeVarInt(banks.size());
        for (BankInfo bank : banks) {
            buf.writeUtf(bank.id());
            buf.writeUtf(bank.name());
            buf.writeUtf(bank.displayName());
            buf.writeDouble(bank.interestRate());
            buf.writeDouble(bank.withdrawalFee());
            buf.writeDouble(bank.transferFee());
            buf.writeBoolean(bank.allowsLoans());
            buf.writeVarInt(bank.color());
        }
    }

    public List<AccountInfo> getAccounts() {
        return accounts;
    }

    public List<BankInfo> getBanks() {
        return banks;
    }

    /**
     * Information about an available account
     * @param type PERSONAL, NATION, STATE, CITY, or COMPANY
     * @param name Display name (e.g., player name, nation name, company name)
     * @param id Unique identifier for the account
     * @param balance Current balance
     */
    public record AccountInfo(String type, String name, String id, double balance) {
        public String getDisplayName() {
            return switch (type) {
                case "PERSONAL" -> "Personal Account";
                case "NATION" -> "Nation: " + name;
                case "STATE" -> "State: " + name;
                case "CITY" -> "City: " + name;
                case "COMPANY" -> "Company: " + name;
                case "BANK_DEPOSIT" -> "Bank: " + name;
                default -> name;
            };
        }
    }

    /**
     * Information about a bank for the ATM bank selection dropdown
     */
    public record BankInfo(String id, String name, String displayName,
                           double interestRate, double withdrawalFee, double transferFee,
                           boolean allowsLoans, int color) {
    }
}

