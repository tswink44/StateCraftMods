package com.statecraft.network.packets;

import com.statecraft.client.gui.ChunkPermitsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.*;

/**
 * Sync chunk permit data to client
 */
public class SyncChunkPermitsPacket {
    private final int chunkX;
    private final int chunkZ;
    private final String ownershipType; // HIERARCHY, PLAYER
    private final String ownerName; // Player name if privately owned, or City/State/Nation name
    private final boolean canManagePermits;
    private final Map<UUID, String> permitHolders; // UUID -> Player name

    public SyncChunkPermitsPacket(int chunkX, int chunkZ, String ownershipType, String ownerName,
                                   boolean canManagePermits, Map<UUID, String> permitHolders) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.ownershipType = ownershipType;
        this.ownerName = ownerName;
        this.canManagePermits = canManagePermits;
        this.permitHolders = permitHolders;
    }

    public SyncChunkPermitsPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.ownershipType = buf.readUtf();
        this.ownerName = buf.readUtf();
        this.canManagePermits = buf.readBoolean();

        int count = buf.readInt();
        this.permitHolders = new HashMap<>();
        for (int i = 0; i < count; i++) {
            UUID playerId = buf.readUUID();
            String playerName = buf.readUtf();
            permitHolders.put(playerId, playerName);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeUtf(ownershipType);
        buf.writeUtf(ownerName);
        buf.writeBoolean(canManagePermits);

        buf.writeInt(permitHolders.size());
        for (Map.Entry<UUID, String> entry : permitHolders.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    public static void handle(SyncChunkPermitsPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChunkPermitsScreen screen) {
                screen.updateData(
                    packet.ownershipType,
                    packet.ownerName,
                    packet.canManagePermits,
                    packet.permitHolders
                );
            }
        });
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }
}

