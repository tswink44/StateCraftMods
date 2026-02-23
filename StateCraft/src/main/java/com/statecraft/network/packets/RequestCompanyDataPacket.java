package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request the player's company data for the Company GUI.
 * If companyId is set, requests data for that specific company.
 * If empty, requests data for the player's first company (legacy behavior).
 */
public class RequestCompanyDataPacket {

    private final String companyId;

    public RequestCompanyDataPacket() {
        this("");
    }

    public RequestCompanyDataPacket(String companyId) {
        this.companyId = companyId != null ? companyId : "";
    }

    public RequestCompanyDataPacket(FriendlyByteBuf buf) {
        this.companyId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(companyId);
    }

    public String getCompanyId() { return companyId; }
}

