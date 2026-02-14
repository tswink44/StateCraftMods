package com.statecraft.network.packets;

import com.statecraft.client.gui.ChunkInfoScreen;
import com.statecraft.client.gui.ClaimsManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync chunk info data to client
 */
public class SyncChunkInfoPacket {
    private final int chunkX;
    private final int chunkZ;
    private final String ownershipType; // HIERARCHY, PLAYER, UNCLAIMED
    private final String ownerName;
    private final String nationName;
    private final String stateName;
    private final String cityName;
    private final boolean canManagePermits;
    private final List<String> permitHolders;

    public SyncChunkInfoPacket(int chunkX, int chunkZ, String ownershipType, String ownerName,
                                String nationName, String stateName, String cityName,
                                boolean canManagePermits, List<String> permitHolders) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.ownershipType = ownershipType;
        this.ownerName = ownerName;
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.canManagePermits = canManagePermits;
        this.permitHolders = permitHolders;
    }

    public SyncChunkInfoPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.ownershipType = buf.readUtf();
        this.ownerName = buf.readUtf();
        this.nationName = buf.readUtf();
        this.stateName = buf.readUtf();
        this.cityName = buf.readUtf();
        this.canManagePermits = buf.readBoolean();

        int count = buf.readInt();
        this.permitHolders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            permitHolders.add(buf.readUtf());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeUtf(ownershipType);
        buf.writeUtf(ownerName);
        buf.writeUtf(nationName);
        buf.writeUtf(stateName);
        buf.writeUtf(cityName);
        buf.writeBoolean(canManagePermits);

        buf.writeInt(permitHolders.size());
        for (String name : permitHolders) {
            buf.writeUtf(name);
        }
    }

    public static void handle(SyncChunkInfoPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChunkInfoScreen screen) {
                screen.updateData(
                    packet.ownershipType,
                    packet.ownerName,
                    packet.nationName,
                    packet.stateName,
                    packet.cityName,
                    packet.canManagePermits,
                    packet.permitHolders
                );
            } else if (mc.screen instanceof ClaimsManagementScreen screen) {
                screen.updateChunkInfo(
                    packet.ownershipType,
                    packet.ownerName,
                    packet.nationName,
                    packet.stateName,
                    packet.cityName
                );
            }
        });
    }
}

