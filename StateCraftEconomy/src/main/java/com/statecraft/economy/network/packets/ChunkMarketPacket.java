package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet for chunk market operations (list for sale, remove from sale, purchase)
 */
public class ChunkMarketPacket {

    public enum Action {
        LIST_FOR_SALE,
        REMOVE_FROM_SALE,
        PURCHASE,
        REQUEST_INFO
    }

    private final Action action;
    private final int chunkX;
    private final int chunkZ;
    private final double price; // Only used for LIST_FOR_SALE

    public ChunkMarketPacket(Action action, int chunkX, int chunkZ, double price) {
        this.action = action;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.price = price;
    }

    public ChunkMarketPacket(Action action, int chunkX, int chunkZ) {
        this(action, chunkX, chunkZ, 0);
    }

    public ChunkMarketPacket(FriendlyByteBuf buf) {
        this.action = Action.values()[buf.readInt()];
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.price = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(action.ordinal());
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeDouble(price);
    }

    public Action getAction() {
        return action;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public double getPrice() {
        return price;
    }
}

