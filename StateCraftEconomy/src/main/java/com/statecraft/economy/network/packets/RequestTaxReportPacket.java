package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet sent from client to request tax report data
 */
public class RequestTaxReportPacket {

    public RequestTaxReportPacket() {
    }

    public RequestTaxReportPacket(FriendlyByteBuf buf) {
        // No data to read
    }

    public void toBytes(FriendlyByteBuf buf) {
        // No data to write
    }
}

