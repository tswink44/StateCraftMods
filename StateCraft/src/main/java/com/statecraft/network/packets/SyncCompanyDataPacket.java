package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Syncs the player's primary company data for the Company GUI.
 */
public class SyncCompanyDataPacket {

    private final boolean hasCompany;
    private final String companyName;
    private final String companyId; // UUID as string
    private final String founderName;
    private final String description;
    private final int totalShares;
    private final int playerShares;
    private final int shareholderCount;
    private final int officerCount;
    private final String headquartersCity;
    private final boolean isFounder;
    private final boolean isOfficer;
    private final boolean dividendsEnabled;
    private final double dividendRate;
    private final String companyType;
    private final List<ShareholderEntry> shareholders;
    private final List<String> officerNames;
    private final String resultMessage;

    // Bank-specific fields (only populated when companyType is BANK)
    private final double depositInterestRate;
    private final double loanInterestRate;
    private final double withdrawalFee;
    private final double transferFee;
    private final double reserveRatio;
    private final int activeLoanCount;
    private final double totalDeposits;

    public SyncCompanyDataPacket(boolean hasCompany, String companyName, String companyId,
                                  String founderName, String description,
                                  int totalShares, int playerShares,
                                  int shareholderCount, int officerCount,
                                  String headquartersCity,
                                  boolean isFounder, boolean isOfficer,
                                  boolean dividendsEnabled, double dividendRate,
                                  String companyType,
                                  List<ShareholderEntry> shareholders,
                                  List<String> officerNames,
                                  String resultMessage) {
        this(hasCompany, companyName, companyId, founderName, description,
             totalShares, playerShares, shareholderCount, officerCount,
             headquartersCity, isFounder, isOfficer, dividendsEnabled, dividendRate,
             companyType, shareholders, officerNames, resultMessage,
             0, 0, 0, 0, 0, 0, 0);
    }

    public SyncCompanyDataPacket(boolean hasCompany, String companyName, String companyId,
                                  String founderName, String description,
                                  int totalShares, int playerShares,
                                  int shareholderCount, int officerCount,
                                  String headquartersCity,
                                  boolean isFounder, boolean isOfficer,
                                  boolean dividendsEnabled, double dividendRate,
                                  String companyType,
                                  List<ShareholderEntry> shareholders,
                                  List<String> officerNames,
                                  String resultMessage,
                                  double depositInterestRate, double loanInterestRate,
                                  double withdrawalFee, double transferFee,
                                  double reserveRatio, int activeLoanCount,
                                  double totalDeposits) {
        this.hasCompany = hasCompany;
        this.companyName = companyName;
        this.companyId = companyId;
        this.founderName = founderName;
        this.description = description;
        this.totalShares = totalShares;
        this.playerShares = playerShares;
        this.shareholderCount = shareholderCount;
        this.officerCount = officerCount;
        this.headquartersCity = headquartersCity;
        this.isFounder = isFounder;
        this.isOfficer = isOfficer;
        this.dividendsEnabled = dividendsEnabled;
        this.dividendRate = dividendRate;
        this.companyType = companyType;
        this.shareholders = shareholders;
        this.officerNames = officerNames;
        this.resultMessage = resultMessage != null ? resultMessage : "";
        this.depositInterestRate = depositInterestRate;
        this.loanInterestRate = loanInterestRate;
        this.withdrawalFee = withdrawalFee;
        this.transferFee = transferFee;
        this.reserveRatio = reserveRatio;
        this.activeLoanCount = activeLoanCount;
        this.totalDeposits = totalDeposits;
    }

    public SyncCompanyDataPacket(FriendlyByteBuf buf) {
        this.hasCompany = buf.readBoolean();
        this.companyName = buf.readUtf();
        this.companyId = buf.readUtf();
        this.founderName = buf.readUtf();
        this.description = buf.readUtf();
        this.totalShares = buf.readVarInt();
        this.playerShares = buf.readVarInt();
        this.shareholderCount = buf.readVarInt();
        this.officerCount = buf.readVarInt();
        this.headquartersCity = buf.readUtf();
        this.isFounder = buf.readBoolean();
        this.isOfficer = buf.readBoolean();
        this.dividendsEnabled = buf.readBoolean();
        this.dividendRate = buf.readDouble();
        this.companyType = buf.readUtf();

        int shCount = buf.readVarInt();
        this.shareholders = new ArrayList<>(shCount);
        for (int i = 0; i < shCount; i++) {
            shareholders.add(new ShareholderEntry(buf.readUtf(), buf.readVarInt(), buf.readDouble()));
        }

        int offCount = buf.readVarInt();
        this.officerNames = new ArrayList<>(offCount);
        for (int i = 0; i < offCount; i++) {
            officerNames.add(buf.readUtf());
        }

        this.resultMessage = buf.readUtf();

        // Bank-specific fields
        this.depositInterestRate = buf.readDouble();
        this.loanInterestRate = buf.readDouble();
        this.withdrawalFee = buf.readDouble();
        this.transferFee = buf.readDouble();
        this.reserveRatio = buf.readDouble();
        this.activeLoanCount = buf.readVarInt();
        this.totalDeposits = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hasCompany);
        buf.writeUtf(companyName);
        buf.writeUtf(companyId);
        buf.writeUtf(founderName);
        buf.writeUtf(description);
        buf.writeVarInt(totalShares);
        buf.writeVarInt(playerShares);
        buf.writeVarInt(shareholderCount);
        buf.writeVarInt(officerCount);
        buf.writeUtf(headquartersCity);
        buf.writeBoolean(isFounder);
        buf.writeBoolean(isOfficer);
        buf.writeBoolean(dividendsEnabled);
        buf.writeDouble(dividendRate);
        buf.writeUtf(companyType);

        buf.writeVarInt(shareholders.size());
        for (ShareholderEntry sh : shareholders) {
            buf.writeUtf(sh.name);
            buf.writeVarInt(sh.shares);
            buf.writeDouble(sh.percentage);
        }

        buf.writeVarInt(officerNames.size());
        for (String name : officerNames) {
            buf.writeUtf(name);
        }

        buf.writeUtf(resultMessage);

        // Bank-specific fields
        buf.writeDouble(depositInterestRate);
        buf.writeDouble(loanInterestRate);
        buf.writeDouble(withdrawalFee);
        buf.writeDouble(transferFee);
        buf.writeDouble(reserveRatio);
        buf.writeVarInt(activeLoanCount);
        buf.writeDouble(totalDeposits);
    }

    // Getters
    public boolean hasCompany() { return hasCompany; }
    public String getCompanyName() { return companyName; }
    public String getCompanyId() { return companyId; }
    public String getFounderName() { return founderName; }
    public String getDescription() { return description; }
    public int getTotalShares() { return totalShares; }
    public int getPlayerShares() { return playerShares; }
    public int getShareholderCount() { return shareholderCount; }
    public int getOfficerCount() { return officerCount; }
    public String getHeadquartersCity() { return headquartersCity; }
    public boolean isFounder() { return isFounder; }
    public boolean isOfficer() { return isOfficer; }
    public boolean isDividendsEnabled() { return dividendsEnabled; }
    public double getDividendRate() { return dividendRate; }
    public String getCompanyType() { return companyType; }
    public List<ShareholderEntry> getShareholders() { return shareholders; }
    public List<String> getOfficerNames() { return officerNames; }
    public String getResultMessage() { return resultMessage; }
    public double getDepositInterestRate() { return depositInterestRate; }
    public double getLoanInterestRate() { return loanInterestRate; }
    public double getWithdrawalFee() { return withdrawalFee; }
    public double getTransferFee() { return transferFee; }
    public double getReserveRatio() { return reserveRatio; }
    public int getActiveLoanCount() { return activeLoanCount; }
    public double getTotalDeposits() { return totalDeposits; }

    public static class ShareholderEntry {
        public final String name;
        public final int shares;
        public final double percentage;

        public ShareholderEntry(String name, int shares, double percentage) {
            this.name = name;
            this.shares = shares;
            this.percentage = percentage;
        }
    }
}

