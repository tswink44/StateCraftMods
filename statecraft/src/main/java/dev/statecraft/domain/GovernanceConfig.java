package dev.statecraft.domain;

import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class GovernanceConfig {
    public int maxGovernments = 512;
    public int maxNations = 64;
    public int maxStatesPerNation = 32;
    public int maxCitiesPerState = 32;
    public int maxMembersPerNation = 512;
    public int maxMembersPerState = 256;
    public int maxMembersPerCity = 128;
    public int maxOfficers = 32;
    public int maxClaimsPerCity = 256;
    public int maxTotalClaims = 16_384;
    public int maxPermitsPerChunk = 32;
    public int maxInvitationsPerGovernment = 64;
    public int maxInvitationsPerPlayer = 32;
    public int maxCompanies = 256;
    public int maxCompaniesPerPlayer = 8;
    public int maxCompanyMembers = 128;
    public int maxCompanyShareholders = 512;
    public int maxShareReservations = 4_096;
    public int maxCompanyProposals = 32;
    public int maxActiveBillsPerNation = 32;
    public int maxLawsPerNation = 128;
    public int maxDiplomaticProposalsPerNation = 16;
    public int maxTreatyChunks = 32;
    public int maxContractsPerGovernment = 32;
    public int maxContractBids = 64;
    public int maxContractChunks = 32;
    public int maxHistory = 128;
    public int maxMail = 128;
    public int pageSize = 8;
    public int maxCommandOutput = 16_000;
    public int maxNameLength = 48;
    public int maxDescriptionLength = 1_000;
    public int maxMailBodyLength = 2_000;
    public int legislativeQuorumBps = 5_000;
    public int amendmentThresholdBps = 6_666;
    public int overrideThresholdBps = 6_666;
    public int companyQuorumBps = 5_000;

    public long nationCreationFee = 0;
    public long stateCreationFee = 0;
    public long cityCreationFee = 0;
    public long claimFee = 0;
    public long companyCreationFee = 0;
    public long electionCandidateFee = 0;
    public long defaultBaseChunkValue = 10_000;
    public long totalCompanyShares = 10_000;
    public long invitationDurationMillis = 604_800_000;
    public long electionIntervalMillis = 604_800_000;
    public long electionVotingMillis = 86_400_000;
    public long debateDurationMillis = 86_400_000;
    public long legislativeVotingMillis = 86_400_000;
    public long signatureDurationMillis = 86_400_000;
    public long emergencyDurationMillis = 3_600_000;
    public long emergencyCooldownMillis = 86_400_000;
    public long allianceProposalDurationMillis = 172_800_000;
    public long peaceProposalDurationMillis = 604_800_000;
    public long truceDurationMillis = 86_400_000;
    public long companyVotingMillis = 86_400_000;
    public long contractBiddingMillis = 604_800_000;
    public long contractReviewMillis = 604_800_000;

    public boolean requireAdjacentClaims = true;
    public boolean requireConnectedClaims = true;
    public boolean defaultOpenMembership = false;
    public boolean defaultMemberAccess = true;
    public boolean defaultForeignAccess = false;
    public boolean defaultAlliedAccess = true;
    public boolean defaultEnemyAccess = false;
    public boolean defaultPvp = false;
    public boolean defaultExplosions = false;
    public boolean defaultForeignProperty = false;
    public boolean wildernessPvp = true;
    public boolean wildernessExplosions = true;
    public boolean warOverridesPvp = true;
    public boolean protectFriendlyPvp = true;
    public boolean citizenLegislature = false;
    public boolean allowCitizenBills = true;
    public boolean requirePeaceRatification = true;
    public boolean requireLegislationForPolicy = false;
    public String feeAccount = "system:statecraft-fees";

    public void validate() {
        try {
            for (Field field : getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String key = field.getName();
                if (field.getType() == int.class) {
                    int value = field.getInt(this);
                    int upper = key.endsWith("Bps") ? 10_000 : 1_000_000;
                    if (value < 1 || value > upper) bad(key, "must be between 1 and " + upper);
                } else if (field.getType() == long.class) {
                    long value = field.getLong(this);
                    if (key.endsWith("Millis")) {
                        if (value < 1_000 || value > 31_536_000_000L)
                            bad(key, "must be between 1000 milliseconds and one year");
                    } else {
                        Money.nonNegative(value);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        if (maxNameLength < 3 || maxNameLength > 64) bad("maxNameLength", "must be between 3 and 64");
        if (maxDescriptionLength > 2_000) bad("maxDescriptionLength", "must not exceed 2000");
        if (maxMailBodyLength > 4_000) bad("maxMailBodyLength", "must not exceed 4000");
        if (pageSize > 20) bad("pageSize", "must not exceed 20");
        if (maxCommandOutput < 512 || maxCommandOutput > 24_000)
            bad("maxCommandOutput", "must be between 512 and 24000");
        if (maxGovernments > 4_096 || maxCompanies > 4_096 || maxTotalClaims > 100_000)
            bad("world limits", "exceed the supported snapshot limits");
        if (maxHistory > 1_000 || maxMail > 1_000) bad("history/mail limits", "must not exceed 1000");
        if (maxTreatyChunks > 64 || maxContractChunks > 64 || maxContractBids > 256)
            bad("workflow limits", "exceed supported bounds");
        if (electionVotingMillis >= electionIntervalMillis)
            bad("electionVotingMillis", "must be shorter than electionIntervalMillis");
        if (emergencyCooldownMillis < emergencyDurationMillis)
            bad("emergencyCooldownMillis", "must be at least emergencyDurationMillis");
        if (totalCompanyShares < 1 || totalCompanyShares > 1_000_000_000L)
            bad("totalCompanyShares", "must be between 1 and 1000000000");
        if (amendmentThresholdBps < 5_001 || overrideThresholdBps < 5_001)
            bad("supermajorities", "must be greater than 5000 basis points");
        if (feeAccount == null || !feeAccount.matches("system:[a-z0-9_.-]{1,64}"))
            bad("feeAccount", "must be a system:<identifier> account");
    }

    GovernanceConfig copy() {
        GovernanceConfig result = new GovernanceConfig();
        try {
            for (Field field : GovernanceConfig.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) field.set(result, field.get(this));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private static void bad(String key, String problem) {
        throw new UserError("Invalid governance configuration: " + key + " " + problem + ".");
    }
}
