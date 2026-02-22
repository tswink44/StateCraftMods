package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet to open the Marketplace screen.
 */
public class OpenMarketplaceScreenPacket {

    public OpenMarketplaceScreenPacket() {}

    public OpenMarketplaceScreenPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

