package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sends the list of all companies the player owns shares in.
 */
public class SyncCompanyListPacket {

    private final List<CompanyEntry> companies;

    public SyncCompanyListPacket(List<CompanyEntry> companies) {
        this.companies = companies;
    }

    public SyncCompanyListPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.companies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            companies.add(new CompanyEntry(
                buf.readUtf(),    // companyId
                buf.readUtf(),    // companyName
                buf.readUtf(),    // companyType
                buf.readUtf(),    // founderName
                buf.readInt(),    // playerShares
                buf.readInt(),    // totalShares
                buf.readDouble(), // sharePercentage
                buf.readBoolean(), // isFounder
                buf.readBoolean()  // isOfficer
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(companies.size());
        for (CompanyEntry entry : companies) {
            buf.writeUtf(entry.companyId);
            buf.writeUtf(entry.companyName);
            buf.writeUtf(entry.companyType);
            buf.writeUtf(entry.founderName);
            buf.writeInt(entry.playerShares);
            buf.writeInt(entry.totalShares);
            buf.writeDouble(entry.sharePercentage);
            buf.writeBoolean(entry.isFounder);
            buf.writeBoolean(entry.isOfficer);
        }
    }

    public List<CompanyEntry> getCompanies() { return companies; }

    public static class CompanyEntry {
        private final String companyId;
        private final String companyName;
        private final String companyType;
        private final String founderName;
        private final int playerShares;
        private final int totalShares;
        private final double sharePercentage;
        private final boolean isFounder;
        private final boolean isOfficer;

        public CompanyEntry(String companyId, String companyName, String companyType,
                           String founderName, int playerShares, int totalShares,
                           double sharePercentage, boolean isFounder, boolean isOfficer) {
            this.companyId = companyId;
            this.companyName = companyName;
            this.companyType = companyType;
            this.founderName = founderName;
            this.playerShares = playerShares;
            this.totalShares = totalShares;
            this.sharePercentage = sharePercentage;
            this.isFounder = isFounder;
            this.isOfficer = isOfficer;
        }

        public String getCompanyId() { return companyId; }
        public String getCompanyName() { return companyName; }
        public String getCompanyType() { return companyType; }
        public String getFounderName() { return founderName; }
        public int getPlayerShares() { return playerShares; }
        public int getTotalShares() { return totalShares; }
        public double getSharePercentage() { return sharePercentage; }
        public boolean isFounder() { return isFounder; }
        public boolean isOfficer() { return isOfficer; }
    }
}

