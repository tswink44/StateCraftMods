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
    public static final ForgeConfigSpec.BooleanValue ENABLE_INTEREST;
    public static final ForgeConfigSpec.DoubleValue DAILY_INTEREST_RATE;
    public static final ForgeConfigSpec.DoubleValue MAX_BALANCE;

    // ATM configuration
    public static final ForgeConfigSpec.BooleanValue ATM_REQUIRES_POWER;
    public static final ForgeConfigSpec.IntValue ATM_RANGE;
    public static final ForgeConfigSpec.DoubleValue LARGE_TRANSACTION_THRESHOLD;

    // Bank Card configuration
    public static final ForgeConfigSpec.ConfigValue<String> BANK_CARD_MODE;

    // Spending limit configuration
    public static final ForgeConfigSpec.DoubleValue NATION_LEADER_DAILY_LIMIT;
    public static final ForgeConfigSpec.DoubleValue NATION_OFFICER_DAILY_LIMIT;
    public static final ForgeConfigSpec.DoubleValue STATE_GOVERNOR_DAILY_LIMIT;
    public static final ForgeConfigSpec.DoubleValue CITY_MAYOR_DAILY_LIMIT;

    // Company configuration
    public static final ForgeConfigSpec.DoubleValue COMPANY_REGISTRATION_FEE;
    public static final ForgeConfigSpec.DoubleValue COMPANY_TAX_RATE;
    public static final ForgeConfigSpec.DoubleValue COMPANY_OFFICER_DAILY_LIMIT;

    // Banking configuration
    public static final ForgeConfigSpec.DoubleValue DEFAULT_RESERVE_RATIO;
    public static final ForgeConfigSpec.DoubleValue MAX_LOAN_AMOUNT;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_LOANS_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue LOAN_DEFAULT_THRESHOLD;
    public static final ForgeConfigSpec.LongValue BANK_INTEREST_PERIOD_TICKS;
    public static final ForgeConfigSpec.LongValue LOAN_INTEREST_ACCRUAL_PERIOD_TICKS;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_LOAN_INTEREST_RATE;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_DEPOSIT_INTEREST_RATE;
    public static final ForgeConfigSpec.DoubleValue BANK_REGISTRATION_FEE;

    // Marketplace configuration
    public static final ForgeConfigSpec.BooleanValue MARKETPLACE_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_LISTINGS_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue LISTING_FEE_PCT;
    public static final ForgeConfigSpec.IntValue LISTING_EXPIRATION_HOURS;
    public static final ForgeConfigSpec.DoubleValue MAX_PRICE_PER_ITEM;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_CITY_PLACEMENT;


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

        BUILDER.comment("DEPRECATED: Interest is now handled per-bank by player-created bank companies.",
                       "The default Central Bank pays no interest. This setting has no effect.",
                       "To earn interest, players open accounts at player-created banks.");
        ENABLE_INTEREST = BUILDER.define("enableInterest", false);

        BUILDER.comment("DEPRECATED: Interest rate is now configured per-bank via BankCompany.",
                       "See [banking] section for default deposit interest rate.");
        DAILY_INTEREST_RATE = BUILDER.defineInRange("dailyInterestRate", 0.001, 0.0, 0.1);

        BUILDER.comment("Maximum balance a player can have (0 = unlimited)");
        MAX_BALANCE = BUILDER.defineInRange("maxBalance", 0.0, 0.0, Double.MAX_VALUE);

        BUILDER.pop();

        BUILDER.comment("ATM Block Settings").push("atm");

        BUILDER.comment("Whether ATM requires redstone power to function");
        ATM_REQUIRES_POWER = BUILDER.define("requiresPower", false);

        BUILDER.comment("Range in blocks that ATM can be accessed from");
        ATM_RANGE = BUILDER.defineInRange("range", 5, 1, 16);

        BUILDER.comment("Transaction amount above which a confirmation dialog is shown (0 = disabled).",
                       "Applies to deposits, withdrawals, and transfers via the ATM GUI.");
        LARGE_TRANSACTION_THRESHOLD = BUILDER.defineInRange("largeTransactionThreshold", 10000.0, 0.0, Double.MAX_VALUE);

        BUILDER.comment("Bank Card access mode:",
                       "'full' = Bank Card opens full ATM interface remotely (no physical ATM needed)",
                       "'balance_only' = Bank Card only shows balance in chat (must visit ATM for transactions)");
        BANK_CARD_MODE = BUILDER.define("bankCardMode", "full");

        BUILDER.pop();

        BUILDER.comment("Government Spending Limits",
                       "Daily withdrawal/transfer limits from government treasury accounts per role.",
                       "Set to 0 for unlimited. Amounts are in currency units per real-world day.",
                       "Nation leader limit can also be overridden per-nation via legislature policy.").push("spendingLimits");

        BUILDER.comment("Nation leader daily spending limit (0 = unlimited, default)");
        NATION_LEADER_DAILY_LIMIT = BUILDER.defineInRange("nationLeaderDailyLimit", 0.0, 0.0, Double.MAX_VALUE);

        BUILDER.comment("Nation officer daily spending limit (was 'nationAdminDailyLimit')");
        NATION_OFFICER_DAILY_LIMIT = BUILDER.defineInRange("nationAdminDailyLimit", 50000.0, 0.0, Double.MAX_VALUE);

        BUILDER.comment("State governor daily spending limit");
        STATE_GOVERNOR_DAILY_LIMIT = BUILDER.defineInRange("stateGovernorDailyLimit", 20000.0, 0.0, Double.MAX_VALUE);

        BUILDER.comment("City mayor daily spending limit");
        CITY_MAYOR_DAILY_LIMIT = BUILDER.defineInRange("cityMayorDailyLimit", 5000.0, 0.0, Double.MAX_VALUE);

        BUILDER.pop();

        BUILDER.comment("Company Settings",
                       "Configuration for player-created companies.").push("companies");

        BUILDER.comment("Fee to register a new company (paid to the state treasury where HQ is located, 0 = free)");
        COMPANY_REGISTRATION_FEE = BUILDER.defineInRange("registrationFee", 1000.0, 0.0, Double.MAX_VALUE);


        BUILDER.comment("Corporate tax rate — percentage of company balance collected each tax period (0.0 = no tax, 0.05 = 5%)");
        COMPANY_TAX_RATE = BUILDER.defineInRange("companyTaxRate", 0.02, 0.0, 1.0);

        BUILDER.comment("Daily spending limit for company officers withdrawing/transferring from company treasury (0 = unlimited)");
        COMPANY_OFFICER_DAILY_LIMIT = BUILDER.defineInRange("companyOfficerDailyLimit", 20000.0, 0.0, Double.MAX_VALUE);

        BUILDER.pop();

        BUILDER.comment("Banking Settings",
                       "Configuration for bank-type companies. Banks hold depositor funds,",
                       "pay interest, and offer loans with a fractional reserve system.").push("banking");

        BUILDER.comment("Default fractional reserve ratio — minimum fraction of total deposits a bank must keep in treasury.",
                       "This is the global minimum; national legislature policy can set a higher floor per-nation.",
                       "0.20 = 20%, meaning the bank can lend out up to 80% of deposits.");
        DEFAULT_RESERVE_RATIO = BUILDER.defineInRange("defaultReserveRatio", 0.20, 0.0, 1.0);

        BUILDER.comment("Maximum loan amount a player can borrow from a single bank (0 = unlimited)");
        MAX_LOAN_AMOUNT = BUILDER.defineInRange("maxLoanAmount", 0.0, 0.0, Double.MAX_VALUE);

        BUILDER.comment("Maximum number of active loans a player can have at a single bank (0 = unlimited)");
        MAX_ACTIVE_LOANS_PER_PLAYER = BUILDER.defineInRange("maxActiveLoansPerPlayer", 3, 0, 100);

        BUILDER.comment("Number of consecutive missed loan payments before a loan defaults (triggers seizure/repossession)");
        LOAN_DEFAULT_THRESHOLD = BUILDER.defineInRange("loanDefaultThreshold", 3, 1, 100);

        BUILDER.comment("How often banks pay depositor interest, in game ticks (72000 = 1 real hour at 20 TPS)");
        BANK_INTEREST_PERIOD_TICKS = BUILDER.defineInRange("bankInterestPeriodTicks", 72000L, 1200L, Long.MAX_VALUE);

        BUILDER.comment("How often loan interest accrues, in game ticks (72000 = 1 real hour)");
        LOAN_INTEREST_ACCRUAL_PERIOD_TICKS = BUILDER.defineInRange("loanInterestAccrualPeriodTicks", 72000L, 1200L, Long.MAX_VALUE);

        BUILDER.comment("Default interest rate charged on loans per accrual period (0.05 = 5%)");
        DEFAULT_LOAN_INTEREST_RATE = BUILDER.defineInRange("defaultLoanInterestRate", 0.05, 0.0, 1.0);

        BUILDER.comment("Default interest rate paid to depositors per interest period (0.02 = 2%)");
        DEFAULT_DEPOSIT_INTEREST_RATE = BUILDER.defineInRange("defaultDepositInterestRate", 0.02, 0.0, 1.0);

        BUILDER.comment("Fee to register a bank company (higher than a standard company, 0 = free)");
        BANK_REGISTRATION_FEE = BUILDER.defineInRange("bankRegistrationFee", 5000.0, 0.0, Double.MAX_VALUE);

        BUILDER.pop();

        BUILDER.comment("Marketplace Settings",
                       "Configuration for the player-to-player marketplace system.",
                       "Marketplace blocks let players list items for sale globally.",
                       "Sales tax is based on seller's citizenship, import tariffs on buyer's nation.").push("marketplace");

        BUILDER.comment("Enable the marketplace system");
        MARKETPLACE_ENABLED = BUILDER.define("enabled", true);

        BUILDER.comment("Maximum number of active listings per player (0 = unlimited)");
        MAX_LISTINGS_PER_PLAYER = BUILDER.defineInRange("maxListingsPerPlayer", 20, 0, 1000);

        BUILDER.comment("Listing fee — percentage of listed price charged to seller on listing (deposited to city treasury).",
                       "0.01 = 1%. Set to 0 for no listing fee.");
        LISTING_FEE_PCT = BUILDER.defineInRange("listingFeePct", 0.01, 0.0, 1.0);

        BUILDER.comment("How long listings stay active before expiring, in hours (0 = never expire).",
                       "Default: 168 hours (1 week).");
        LISTING_EXPIRATION_HOURS = BUILDER.defineInRange("listingExpirationHours", 168, 0, 8760);

        BUILDER.comment("Maximum price per item (0 = unlimited)");
        MAX_PRICE_PER_ITEM = BUILDER.defineInRange("maxPricePerItem", 0.0, 0.0, Double.MAX_VALUE);

        BUILDER.comment("Require marketplace blocks to be placed in claimed city chunks");
        REQUIRE_CITY_PLACEMENT = BUILDER.define("requireCityPlacement", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

