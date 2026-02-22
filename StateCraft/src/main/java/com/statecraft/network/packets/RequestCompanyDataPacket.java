package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request the player's company data for the Company GUI.
 */
public class RequestCompanyDataPacket {

    public RequestCompanyDataPacket() {}

    public RequestCompanyDataPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}
}

