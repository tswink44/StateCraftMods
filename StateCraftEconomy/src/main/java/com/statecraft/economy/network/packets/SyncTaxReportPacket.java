package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent from server to client with tax report data
 */
public class SyncTaxReportPacket {

    private final TaxReportData data;

    public SyncTaxReportPacket(TaxReportData data) {
        this.data = data;
    }

    public SyncTaxReportPacket(FriendlyByteBuf buf) {
        int totalChunks = buf.readInt();
        double totalValue = buf.readDouble();
        double totalTax = buf.readDouble();
        double balance = buf.readDouble();
        int periodsAffordable = buf.readInt();
        String taxPeriod = buf.readUtf();
        String nextCollection = buf.readUtf();

        List<NationData> nations = new ArrayList<>();
        int nationCount = buf.readInt();
        for (int i = 0; i < nationCount; i++) {
            nations.add(readNation(buf));
        }

        this.data = new TaxReportData(totalChunks, totalValue, totalTax, balance,
            periodsAffordable, taxPeriod, nextCollection, nations);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(data.totalChunks);
        buf.writeDouble(data.totalValue);
        buf.writeDouble(data.totalTax);
        buf.writeDouble(data.balance);
        buf.writeInt(data.periodsAffordable);
        buf.writeUtf(data.taxPeriod);
        buf.writeUtf(data.nextCollection);

        buf.writeInt(data.nations.size());
        for (NationData nation : data.nations) {
            writeNation(buf, nation);
        }
    }

    private NationData readNation(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        double totalValue = buf.readDouble();
        double totalTax = buf.readDouble();

        List<StateData> states = new ArrayList<>();
        int stateCount = buf.readInt();
        for (int i = 0; i < stateCount; i++) {
            states.add(readState(buf));
        }

        return new NationData(name, totalValue, totalTax, states);
    }

    private void writeNation(FriendlyByteBuf buf, NationData nation) {
        buf.writeUtf(nation.name);
        buf.writeDouble(nation.totalValue);
        buf.writeDouble(nation.totalTax);

        buf.writeInt(nation.states.size());
        for (StateData state : nation.states) {
            writeState(buf, state);
        }
    }

    private StateData readState(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        double totalValue = buf.readDouble();
        double totalTax = buf.readDouble();

        List<CityData> cities = new ArrayList<>();
        int cityCount = buf.readInt();
        for (int i = 0; i < cityCount; i++) {
            cities.add(readCity(buf));
        }

        return new StateData(name, totalValue, totalTax, cities);
    }

    private void writeState(FriendlyByteBuf buf, StateData state) {
        buf.writeUtf(state.name);
        buf.writeDouble(state.totalValue);
        buf.writeDouble(state.totalTax);

        buf.writeInt(state.cities.size());
        for (CityData city : state.cities) {
            writeCity(buf, city);
        }
    }

    private CityData readCity(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        double taxRate = buf.readDouble();
        double totalValue = buf.readDouble();
        double totalTax = buf.readDouble();

        List<ChunkData> chunks = new ArrayList<>();
        int chunkCount = buf.readInt();
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(readChunk(buf));
        }

        return new CityData(name, taxRate, totalValue, totalTax, chunks);
    }

    private void writeCity(FriendlyByteBuf buf, CityData city) {
        buf.writeUtf(city.name);
        buf.writeDouble(city.taxRate);
        buf.writeDouble(city.totalValue);
        buf.writeDouble(city.totalTax);

        buf.writeInt(city.chunks.size());
        for (ChunkData chunk : city.chunks) {
            writeChunk(buf, chunk);
        }
    }

    private ChunkData readChunk(FriendlyByteBuf buf) {
        return new ChunkData(
            buf.readInt(),
            buf.readInt(),
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readInt(),
            buf.readDouble(),
            buf.readDouble()
        );
    }

    private void writeChunk(FriendlyByteBuf buf, ChunkData chunk) {
        buf.writeInt(chunk.chunkX);
        buf.writeInt(chunk.chunkZ);
        buf.writeUtf(chunk.dimension);
        buf.writeDouble(chunk.baseValue);
        buf.writeDouble(chunk.locationMult);
        buf.writeDouble(chunk.biomeMult);
        buf.writeDouble(chunk.demandMult);
        buf.writeDouble(chunk.govMult);
        buf.writeDouble(chunk.improvementMult);
        buf.writeInt(chunk.improvementScore);
        buf.writeDouble(chunk.totalValue);
        buf.writeDouble(chunk.estimatedTax);
    }

    public TaxReportData getData() {
        return data;
    }

    // ==================== Data Classes ====================

    public record TaxReportData(
        int totalChunks,
        double totalValue,
        double totalTax,
        double balance,
        int periodsAffordable,
        String taxPeriod,
        String nextCollection,
        List<NationData> nations
    ) {}

    public record NationData(
        String name,
        double totalValue,
        double totalTax,
        List<StateData> states
    ) {}

    public record StateData(
        String name,
        double totalValue,
        double totalTax,
        List<CityData> cities
    ) {}

    public record CityData(
        String name,
        double taxRate,
        double totalValue,
        double totalTax,
        List<ChunkData> chunks
    ) {}

    public record ChunkData(
        int chunkX,
        int chunkZ,
        String dimension,
        double baseValue,
        double locationMult,
        double biomeMult,
        double demandMult,
        double govMult,
        double improvementMult,
        int improvementScore,
        double totalValue,
        double estimatedTax
    ) {}
}

