package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet containing available accounts for the player
 * Includes government accounts they have admin access to
 */
public class SyncAccountsPacket {

    private final List<AccountInfo> accounts;

    public SyncAccountsPacket(List<AccountInfo> accounts) {
        this.accounts = accounts;
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
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(accounts.size());
        for (AccountInfo account : accounts) {
            buf.writeUtf(account.type());
            buf.writeUtf(account.name());
            buf.writeUtf(account.id());
            buf.writeDouble(account.balance());
        }
    }

    public List<AccountInfo> getAccounts() {
        return accounts;
    }

    /**
     * Information about an available account
     * @param type PERSONAL, NATION, STATE, or CITY
     * @param name Display name (e.g., player name, nation name)
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
                default -> name;
            };
        }
    }
}

