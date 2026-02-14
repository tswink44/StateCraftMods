package com.statecraft.economy.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for StateCraft Economy
 * Allows servers to define custom currency items
 */
public class EconomyConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Currency configuration
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CURRENCY_ITEMS;
    public static final ForgeConfigSpec.DoubleValue STARTING_BALANCE;
    public static final ForgeConfigSpec.IntValue MAX_TRANSFER_PER_DAY;
    public static final ForgeConfigSpec.DoubleValue TRANSFER_FEE_PERCENT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_INTEREST;
    public static final ForgeConfigSpec.DoubleValue DAILY_INTEREST_RATE;
    public static final ForgeConfigSpec.DoubleValue MAX_BALANCE;

    // ATM configuration
    public static final ForgeConfigSpec.BooleanValue ATM_REQUIRES_POWER;
    public static final ForgeConfigSpec.IntValue ATM_RANGE;

    // StateCraft integration
    public static final ForgeConfigSpec.BooleanValue ENABLE_NATION_TREASURY;
    public static final ForgeConfigSpec.DoubleValue NATION_TAX_RATE;
    public static final ForgeConfigSpec.BooleanValue CITY_CAN_HAVE_TREASURY;

    static {
        BUILDER.comment("StateCraft Economy Configuration").push("economy");

        BUILDER.comment("Currency Items",
                       "Format: 'modid:itemname=value'",
                       "Example: 'minecraft:gold_ingot=100' means 1 gold ingot = 100 units",
                       "Default currency bills:");
        CURRENCY_ITEMS = BUILDER.defineList("currencyItems",
            Arrays.asList(
                "statecraft_economy:bill_1=1",
                "statecraft_economy:bill_10=10",
                "statecraft_economy:bill_100=100",
                "statecraft_economy:bill_1000=1000",
                "statecraft_economy:bill_10000=10000",
                "statecraft_economy:bill_100000=100000",
                "statecraft_economy:bill_1000000=1000000"
            ),
            obj -> obj instanceof String && ((String) obj).contains("=")
        );

        BUILDER.comment("Starting balance for new players");
        STARTING_BALANCE = BUILDER.defineInRange("startingBalance", 100.0, 0.0, 1000000.0);

        BUILDER.comment("Maximum transfers per day per player (0 = unlimited)");
        MAX_TRANSFER_PER_DAY = BUILDER.defineInRange("maxTransfersPerDay", 0, 0, 1000);

        BUILDER.comment("Transfer fee percentage (0.0 = no fee, 0.05 = 5% fee)");
        TRANSFER_FEE_PERCENT = BUILDER.defineInRange("transferFeePercent", 0.0, 0.0, 0.5);

        BUILDER.comment("Enable daily interest on bank accounts");
        ENABLE_INTEREST = BUILDER.define("enableInterest", false);

        BUILDER.comment("Daily interest rate (0.01 = 1% per day)");
        DAILY_INTEREST_RATE = BUILDER.defineInRange("dailyInterestRate", 0.001, 0.0, 0.1);

        BUILDER.comment("Maximum balance a player can have (0 = unlimited)");
        MAX_BALANCE = BUILDER.defineInRange("maxBalance", 0.0, 0.0, Double.MAX_VALUE);

        BUILDER.pop();

        BUILDER.comment("ATM Block Settings").push("atm");

        BUILDER.comment("Whether ATM requires redstone power to function");
        ATM_REQUIRES_POWER = BUILDER.define("requiresPower", false);

        BUILDER.comment("Range in blocks that ATM can be accessed from");
        ATM_RANGE = BUILDER.defineInRange("range", 5, 1, 16);

        BUILDER.pop();

        BUILDER.comment("StateCraft Integration Settings").push("statecraft");

        BUILDER.comment("Enable nation treasury integration (requires StateCraft mod)");
        ENABLE_NATION_TREASURY = BUILDER.define("enableNationTreasury", true);

        BUILDER.comment("Default nation tax rate on transactions (0.0 = no tax)");
        NATION_TAX_RATE = BUILDER.defineInRange("nationTaxRate", 0.0, 0.0, 0.5);

        BUILDER.comment("Allow cities to have their own treasury");
        CITY_CAN_HAVE_TREASURY = BUILDER.define("cityCanHaveTreasury", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

