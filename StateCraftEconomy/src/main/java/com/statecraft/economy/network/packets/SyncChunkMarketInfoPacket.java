package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet to sync chunk market info to client
 */
public class SyncChunkMarketInfoPacket {

    private final int chunkX;
    private final int chunkZ;
    private final boolean isClaimed;
    private final boolean isForSale;
    private final double salePrice;
    private final String sellerName;
    private final String ownerName;
    private final String cityName;
    private final boolean isPrivatelyOwned;
    private final boolean canListForSale;
    private final boolean canBuy;

    // Constructor for unclaimed chunk
    public SyncChunkMarketInfoPacket(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.isClaimed = false;
        this.isForSale = false;
        this.salePrice = 0;
        this.sellerName = "";
        this.ownerName = "";
        this.cityName = "";
        this.isPrivatelyOwned = false;
        this.canListForSale = false;
        this.canBuy = false;
    }

    // Full constructor
    public SyncChunkMarketInfoPacket(int chunkX, int chunkZ, boolean isClaimed, boolean isForSale,
                                      double salePrice, String sellerName, String ownerName,
                                      String cityName, boolean isPrivatelyOwned,
                                      boolean canListForSale, boolean canBuy) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.isClaimed = isClaimed;
        this.isForSale = isForSale;
        this.salePrice = salePrice;
        this.sellerName = sellerName;
        this.ownerName = ownerName;
        this.cityName = cityName;
        this.isPrivatelyOwned = isPrivatelyOwned;
        this.canListForSale = canListForSale;
        this.canBuy = canBuy;
    }

    public SyncChunkMarketInfoPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.isClaimed = buf.readBoolean();
        this.isForSale = buf.readBoolean();
        this.salePrice = buf.readDouble();
        this.sellerName = buf.readUtf(64);
        this.ownerName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
        this.isPrivatelyOwned = buf.readBoolean();
        this.canListForSale = buf.readBoolean();
        this.canBuy = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeBoolean(isClaimed);
        buf.writeBoolean(isForSale);
        buf.writeDouble(salePrice);
        buf.writeUtf(sellerName, 64);
        buf.writeUtf(ownerName, 64);
        buf.writeUtf(cityName, 64);
        buf.writeBoolean(isPrivatelyOwned);
        buf.writeBoolean(canListForSale);
        buf.writeBoolean(canBuy);
    }

    // Getters
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public boolean isClaimed() { return isClaimed; }
    public boolean isForSale() { return isForSale; }
    public double getSalePrice() { return salePrice; }
    public String getSellerName() { return sellerName; }
    public String getOwnerName() { return ownerName; }
    public String getCityName() { return cityName; }
    public boolean isPrivatelyOwned() { return isPrivatelyOwned; }
    public boolean canListForSale() { return canListForSale; }
    public boolean canBuy() { return canBuy; }
}

