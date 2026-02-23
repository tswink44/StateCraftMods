package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request the list of all companies the player owns shares in.
 */
public class RequestCompanyListPacket {

    public RequestCompanyListPacket() {}

    public RequestCompanyListPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}
}

