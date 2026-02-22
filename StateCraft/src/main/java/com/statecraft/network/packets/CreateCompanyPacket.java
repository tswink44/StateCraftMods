package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to create a new company.
 */
public class CreateCompanyPacket {
    private final String name;
    private final int totalShares;
    private final String description;
    private final String companyType; // "GENERAL" or "BANK"

    public CreateCompanyPacket(String name, int totalShares, String description) {
        this(name, totalShares, description, "GENERAL");
    }

    public CreateCompanyPacket(String name, int totalShares, String description, String companyType) {
        this.name = name;
        this.totalShares = totalShares;
        this.description = description;
        this.companyType = companyType != null ? companyType : "GENERAL";
    }

    public CreateCompanyPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf();
        this.totalShares = buf.readVarInt();
        this.description = buf.readUtf();
        this.companyType = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeVarInt(totalShares);
        buf.writeUtf(description);
        buf.writeUtf(companyType, 32);
    }

    public String getName() { return name; }
    public int getTotalShares() { return totalShares; }
    public String getDescription() { return description; }
    public String getCompanyType() { return companyType; }
    public boolean isBank() { return "BANK".equalsIgnoreCase(companyType); }
}

