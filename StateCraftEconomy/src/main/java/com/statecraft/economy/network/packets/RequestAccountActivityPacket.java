package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request transaction history for a specific account.
 * accountType: "PERSONAL", "NATION", "STATE", or "CITY"
 * accountId: UUID string of the account
 */
public class RequestAccountActivityPacket {

    private final String accountType;
    private final String accountId;

    public RequestAccountActivityPacket(String accountType, String accountId) {
        this.accountType = accountType;
        this.accountId = accountId;
    }

    public RequestAccountActivityPacket(FriendlyByteBuf buf) {
        this.accountType = buf.readUtf();
        this.accountId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(accountType);
        buf.writeUtf(accountId);
    }

    public String getAccountType() {
        return accountType;
    }

    public String getAccountId() {
        return accountId;
    }
}

