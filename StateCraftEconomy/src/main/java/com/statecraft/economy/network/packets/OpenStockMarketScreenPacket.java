package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet to open the Stock Market screen.
 */
public class OpenStockMarketScreenPacket {

    public OpenStockMarketScreenPacket() {}

    public OpenStockMarketScreenPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

