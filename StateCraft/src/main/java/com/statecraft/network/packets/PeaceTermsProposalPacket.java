package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: Propose peace with specific terms (currency + chunk demands).
 * Also used for counter-proposals (counterProposalId is set to the original proposal being countered).
 */
public class PeaceTermsProposalPacket {

    private final String nationName;
    private final String targetNationName;
    private final double currencyDemand;
    private final List<ChunkDemandData> chunkDemands;
    private final String receivingCityId; // UUID string of the city to receive chunks
    private final String counterProposalId; // UUID string of proposal being countered, empty if new

    public static class ChunkDemandData {
        public final int chunkX;
        public final int chunkZ;
        public final String dimension;

        public ChunkDemandData(int chunkX, int chunkZ, String dimension) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
        }
    }

    public PeaceTermsProposalPacket(String nationName, String targetNationName, double currencyDemand,
                                     List<ChunkDemandData> chunkDemands, String receivingCityId,
                                     String counterProposalId) {
        this.nationName = nationName;
        this.targetNationName = targetNationName;
        this.currencyDemand = currencyDemand;
        this.chunkDemands = chunkDemands != null ? chunkDemands : new ArrayList<>();
        this.receivingCityId = receivingCityId != null ? receivingCityId : "";
        this.counterProposalId = counterProposalId != null ? counterProposalId : "";
    }

    public PeaceTermsProposalPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
        this.targetNationName = buf.readUtf();
        this.currencyDemand = buf.readDouble();

        int count = buf.readVarInt();
        this.chunkDemands = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunkDemands.add(new ChunkDemandData(buf.readInt(), buf.readInt(), buf.readUtf(128)));
        }

        this.receivingCityId = buf.readUtf();
        this.counterProposalId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
        buf.writeUtf(targetNationName);
        buf.writeDouble(currencyDemand);

        buf.writeVarInt(chunkDemands.size());
        for (ChunkDemandData cd : chunkDemands) {
            buf.writeInt(cd.chunkX);
            buf.writeInt(cd.chunkZ);
            buf.writeUtf(cd.dimension, 128);
        }

        buf.writeUtf(receivingCityId);
        buf.writeUtf(counterProposalId);
    }

    public String getNationName() { return nationName; }
    public String getTargetNationName() { return targetNationName; }
    public double getCurrencyDemand() { return currencyDemand; }
    public List<ChunkDemandData> getChunkDemands() { return chunkDemands; }
    public String getReceivingCityId() { return receivingCityId; }
    public String getCounterProposalId() { return counterProposalId; }
}

