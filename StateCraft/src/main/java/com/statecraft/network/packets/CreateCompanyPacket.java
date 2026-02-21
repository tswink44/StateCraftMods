package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to create a new company.
 */
public class CreateCompanyPacket {
    private final String name;
    private final int totalShares;
    private final String description;

    public CreateCompanyPacket(String name, int totalShares, String description) {
        this.name = name;
        this.totalShares = totalShares;
        this.description = description;
    }

    public CreateCompanyPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf();
        this.totalShares = buf.readVarInt();
        this.description = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeVarInt(totalShares);
        buf.writeUtf(description);
    }

    public String getName() { return name; }
    public int getTotalShares() { return totalShares; }
    public String getDescription() { return description; }
}

