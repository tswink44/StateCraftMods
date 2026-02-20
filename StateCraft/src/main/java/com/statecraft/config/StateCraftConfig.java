package com.statecraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for StateCraft mod
 * NOTE: StateCraft Economy mod is REQUIRED for economy features to function
 */
public class StateCraftConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Territory Limits
    public static final ForgeConfigSpec.IntValue MAX_STATES_PER_NATION;
    public static final ForgeConfigSpec.IntValue MAX_CITIES_PER_STATE;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS_PER_CITY;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS_PER_PLAYER;

    // Economy Integration Settings (requires StateCraft Economy mod)
    public static final ForgeConfigSpec.BooleanValue ENABLE_NATION_TREASURY;
    public static final ForgeConfigSpec.DoubleValue NATION_TAX_RATE;
    public static final ForgeConfigSpec.BooleanValue CITY_CAN_HAVE_TREASURY;
    public static final ForgeConfigSpec.BooleanValue STATE_CAN_HAVE_TREASURY;
    public static final ForgeConfigSpec.BooleanValue CHUNK_CLAIM_FEE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CHUNK_CLAIM_FEE;

    // Creation Fees (requires StateCraft Economy mod)
    public static final ForgeConfigSpec.DoubleValue NATION_CREATION_FEE;
    public static final ForgeConfigSpec.DoubleValue STATE_CREATION_FEE;
    public static final ForgeConfigSpec.DoubleValue CITY_CREATION_FEE;

    // Election Settings
    public static final ForgeConfigSpec.BooleanValue ENABLE_NATION_ELECTIONS;
    public static final ForgeConfigSpec.IntValue ELECTION_INTERVAL_DAYS;
    public static final ForgeConfigSpec.IntValue ELECTION_DURATION_HOURS;
    public static final ForgeConfigSpec.DoubleValue ELECTION_CANDIDATE_FEE;

    // Legislature Settings
    public static final ForgeConfigSpec.BooleanValue ENABLE_LEGISLATURE;
    public static final ForgeConfigSpec.IntValue LEGISLATURE_DEBATE_HOURS;
    public static final ForgeConfigSpec.IntValue LEGISLATURE_VOTING_HOURS;
    public static final ForgeConfigSpec.IntValue LEGISLATURE_QUORUM_PERCENT;
    public static final ForgeConfigSpec.IntValue LEGISLATURE_VETO_OVERRIDE_PERCENT;
    public static final ForgeConfigSpec.IntValue EMERGENCY_POWER_COOLDOWN_DAYS;

    // Diplomacy Settings
    public static final ForgeConfigSpec.BooleanValue PVP_PROTECT_SAME_NATION;
    public static final ForgeConfigSpec.BooleanValue PVP_PROTECT_ALLIES;
    public static final ForgeConfigSpec.IntValue MAX_DIPLOMACY_PROPOSALS;

    static {
        BUILDER.comment(
            "StateCraft Configuration",
            "Territory and governance settings for nations, states, and cities"
        ).push("territory");

        BUILDER.comment("Maximum number of states a nation can have");
        MAX_STATES_PER_NATION = BUILDER.defineInRange("maxStatesPerNation", 10, 1, 100);

        BUILDER.comment("Maximum number of cities a state can have");
        MAX_CITIES_PER_STATE = BUILDER.defineInRange("maxCitiesPerState", 10, 1, 100);

        BUILDER.comment("Maximum number of chunks a city can claim");
        MAX_CHUNKS_PER_CITY = BUILDER.defineInRange("maxChunksPerCity", 100, 1, 10000);

        BUILDER.comment("Maximum number of chunks a player can personally own (0 = unlimited)");
        MAX_CHUNKS_PER_PLAYER = BUILDER.defineInRange("maxChunksPerPlayer", 0, 0, 10000);

        BUILDER.pop();

        BUILDER.comment(
            "Economy Integration Settings",
            "NOTE: StateCraft Economy mod is REQUIRED for economy features to function"
        ).push("economy");

        BUILDER.comment("Enable nation treasury integration (requires StateCraft Economy mod)");
        ENABLE_NATION_TREASURY = BUILDER.define("enableNationTreasury", true);

        BUILDER.comment("Default nation tax rate on transactions (0.0 = no tax, 0.1 = 10%)");
        NATION_TAX_RATE = BUILDER.defineInRange("nationTaxRate", 0.0, 0.0, 0.5);

        BUILDER.comment("Allow states to have their own treasury");
        STATE_CAN_HAVE_TREASURY = BUILDER.define("stateCanHaveTreasury", true);

        BUILDER.comment("Allow cities to have their own treasury");
        CITY_CAN_HAVE_TREASURY = BUILDER.define("cityCanHaveTreasury", true);

        BUILDER.comment("Enable chunk claim fee (cities must pay to claim chunks)");
        CHUNK_CLAIM_FEE_ENABLED = BUILDER.define("chunkClaimFeeEnabled", true);

        BUILDER.comment("Fee amount cities must pay to claim each chunk");
        CHUNK_CLAIM_FEE = BUILDER.defineInRange("chunkClaimFee", 100.0, 0.0, 1000000.0);

        BUILDER.pop();

        BUILDER.comment(
            "Creation Fees",
            "One-time fees for creating nations, states, and cities",
            "Set to 0 to disable the fee (requires StateCraft Economy mod)"
        ).push("creationFees");

        BUILDER.comment("Fee to create a new nation (paid by the founder)");
        NATION_CREATION_FEE = BUILDER.defineInRange("nationCreationFee", 100000.0, 0.0, 100000000.0);

        BUILDER.comment("Fee to create a new state within a nation (paid by the founder)");
        STATE_CREATION_FEE = BUILDER.defineInRange("stateCreationFee", 50000.0, 0.0, 100000000.0);

        BUILDER.comment("Fee to create a new city within a state (paid by the founder)");
        CITY_CREATION_FEE = BUILDER.defineInRange("cityCreationFee", 10000.0, 0.0, 100000000.0);

        BUILDER.pop();

        BUILDER.comment(
            "Election Settings",
            "Configure democratic elections for nation leadership"
        ).push("elections");

        BUILDER.comment("Enable automatic elections for nation leadership");
        ENABLE_NATION_ELECTIONS = BUILDER.define("enableNationElections", true);

        BUILDER.comment("Days between elections (0 = no automatic elections, only manual)");
        ELECTION_INTERVAL_DAYS = BUILDER.defineInRange("electionIntervalDays", 7, 0, 365);

        BUILDER.comment("Duration of voting period in real-time hours");
        ELECTION_DURATION_HOURS = BUILDER.defineInRange("electionDurationHours", 24, 1, 168);

        BUILDER.comment("Fee citizens must pay to register as a candidate (requires StateCraft Economy mod, 0 = free)");
        ELECTION_CANDIDATE_FEE = BUILDER.defineInRange("electionCandidateFee", 5000.0, 0.0, 100000000.0);

        BUILDER.pop();

        BUILDER.comment(
            "Legislature Settings",
            "Configure the nation legislature for voting on policies"
        ).push("legislature");

        BUILDER.comment("Enable nation legislature for voting on policies");
        ENABLE_LEGISLATURE = BUILDER.define("enableLegislature", true);

        BUILDER.comment("Duration of debate period in real-time hours before voting begins");
        LEGISLATURE_DEBATE_HOURS = BUILDER.defineInRange("debatePeriodHours", 24, 1, 168);

        BUILDER.comment("Duration of voting period in real-time hours");
        LEGISLATURE_VOTING_HOURS = BUILDER.defineInRange("votingPeriodHours", 24, 1, 168);

        BUILDER.comment("Minimum participation percentage required for quorum (0-100)");
        LEGISLATURE_QUORUM_PERCENT = BUILDER.defineInRange("quorumPercent", 50, 0, 100);

        BUILDER.comment("Percentage of YES votes required to override a veto (0-100)");
        LEGISLATURE_VETO_OVERRIDE_PERCENT = BUILDER.defineInRange("vetoOverridePercent", 67, 50, 100);

        BUILDER.comment("Cooldown in days before an emergency power can be used again");
        EMERGENCY_POWER_COOLDOWN_DAYS = BUILDER.defineInRange("emergencyPowerCooldownDays", 30, 0, 365);

        BUILDER.pop();

        BUILDER.comment(
            "Diplomacy Settings",
            "Configure PvP protection and diplomatic proposal limits"
        ).push("diplomacy");

        BUILDER.comment("Prevent PvP between players in the same nation (default: true)");
        PVP_PROTECT_SAME_NATION = BUILDER.define("pvpProtectSameNation", true);

        BUILDER.comment("Prevent PvP between players in allied nations (default: true)");
        PVP_PROTECT_ALLIES = BUILDER.define("pvpProtectAllies", true);

        BUILDER.comment("Maximum number of outbound diplomatic proposals per nation (alliance + peace)");
        MAX_DIPLOMACY_PROPOSALS = BUILDER.defineInRange("maxDiplomacyProposals", 5, 1, 50);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

