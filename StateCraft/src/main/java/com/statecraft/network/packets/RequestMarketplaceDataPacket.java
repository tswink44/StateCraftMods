package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Request marketplace data (chunks for sale) around player's location
 */
public class RequestMarketplaceDataPacket {
    private final int centerX;
    private final int centerZ;

    public RequestMarketplaceDataPacket(int centerX, int centerZ) {
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    public RequestMarketplaceDataPacket(FriendlyByteBuf buf) {
        this.centerX = buf.readInt();
        this.centerZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }
}

