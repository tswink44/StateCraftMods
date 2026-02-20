package com.statecraft.legislature;

/**
 * Defines all policy types that can be modified through legislation
 */
public enum PolicyType {
    // Taxation policies
    STATE_PASS_THROUGH_RATE("State Pass-Through Rate", Category.TAXATION, 0.0, 1.0, ValueType.PERCENTAGE,
        "Percentage of state revenue that must be passed to nation treasury"),
    IMPORT_TARIFF("Import Tariff", Category.TAXATION, 0.0, 1.0, ValueType.PERCENTAGE,
        "Tax rate on trade with other nations"),
    BASE_CHUNK_VALUE("Base Chunk Value", Category.TAXATION, 1, 100000, ValueType.CURRENCY,
        "Base valuation for chunks in the nation (default $100)"),
    NATION_SALES_TAX_RATE("Nation Sales Tax", Category.TAXATION, 0.0, 0.5, ValueType.PERCENTAGE,
        "Nation's sales tax rate on trading hub sales (0-50%)"),

    // Territory policies
    MAX_STATES_PER_NATION("Max States", Category.TERRITORY, 1, 100, ValueType.INTEGER,
        "Maximum number of states the nation can have"),
    MAX_CITIES_PER_STATE("Max Cities per State", Category.TERRITORY, 1, 100, ValueType.INTEGER,
        "Maximum number of cities each state can have"),
    MAX_CHUNKS_PER_CITY("Max Chunks per City", Category.TERRITORY, 1, 10000, ValueType.INTEGER,
        "Maximum chunks each city can claim"),
    OPEN_BORDERS("Open Borders", Category.TERRITORY, 0, 1, ValueType.BOOLEAN,
        "Allow foreign players to interact in nation territory"),

    // Membership policies
    OPEN_NATION("Open Nation", Category.MEMBERSHIP, 0, 1, ValueType.BOOLEAN,
        "Allow players to join without an invitation"),
    CITIZENSHIP_REQUIREMENTS("Citizenship Requirements", Category.MEMBERSHIP, 0, 0, ValueType.TEXT,
        "Custom requirements for citizenship (roleplay)"),

    // Diplomacy policies
    DECLARE_WAR("Declare War", Category.DIPLOMACY, 0, 0, ValueType.NATION_TARGET,
        "Declare enemy status against another nation"),
    DECLARE_PEACE("Declare Peace", Category.DIPLOMACY, 0, 0, ValueType.NATION_TARGET,
        "End enemy status with another nation"),
    FORM_ALLIANCE("Form Alliance", Category.DIPLOMACY, 0, 0, ValueType.NATION_TARGET,
        "Propose an alliance with another nation"),
    BREAK_ALLIANCE("Break Alliance", Category.DIPLOMACY, 0, 0, ValueType.NATION_TARGET,
        "End alliance with another nation"),

    // Economy policies
    MINIMUM_WAGE("Minimum Wage", Category.ECONOMY, 0, 10000, ValueType.CURRENCY,
        "Minimum payment for jobs (roleplay enforcement)"),
    CHUNK_CLAIM_FEE("Chunk Claim Fee", Category.ECONOMY, 0, 1000000, ValueType.CURRENCY,
        "Fee cities must pay to claim each chunk"),
    EMINENT_DOMAIN("Eminent Domain", Category.ECONOMY, 0, 0, ValueType.CHUNK_TARGET,
        "Repossess a privately owned chunk. Owner receives 10x the tax valuation from nation treasury."),
    LEADER_SPENDING_LIMIT("Leader Spending Limit", Category.ECONOMY, 0, 100000000, ValueType.CURRENCY,
        "Daily spending limit for the nation leader on the nation treasury (0 = unlimited). Overrides server default."),

    // Constitutional policies (require constitutional amendment to change)
    LEADER_TERM_DURATION("Leader Term Duration", Category.CONSTITUTIONAL, 1, 365, ValueType.INTEGER,
        "Duration of leader term in days (default: 7 days). Requires constitutional amendment."),
    ELECTION_DURATION("Election Duration", Category.CONSTITUTIONAL, 1, 30, ValueType.INTEGER,
        "Duration of election voting period in days (default: 1 day). Requires constitutional amendment."),
    MAX_OFFICERS("Max Officers", Category.CONSTITUTIONAL, 0, 3, ValueType.INTEGER,
        "Maximum number of officers the leader can appoint (0-3). Requires constitutional amendment."),
    NATION_NAME("Nation Name", Category.CONSTITUTIONAL, 0, 0, ValueType.TEXT,
        "Official name of the nation. Requires constitutional amendment."),
    NATION_FLAG("Nation Flag", Category.CONSTITUTIONAL, 0, 0, ValueType.TEXT,
        "URL to the nation's flag image. Requires constitutional amendment."),

    // Custom laws (roleplay)
    CUSTOM_LAW("Custom Law", Category.CUSTOM, 0, 0, ValueType.TEXT,
        "Custom law or regulation for roleplay purposes");

    private final String displayName;
    private final Category category;
    private final double minValue;
    private final double maxValue;
    private final ValueType valueType;
    private final String description;

    PolicyType(String displayName, Category category, double minValue, double maxValue,
               ValueType valueType, String description) {
        this.displayName = displayName;
        this.category = category;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.valueType = valueType;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public Category getCategory() { return category; }
    public double getMinValue() { return minValue; }
    public double getMaxValue() { return maxValue; }
    public ValueType getValueType() { return valueType; }
    public String getDescription() { return description; }

    /**
     * Check if this policy type requires a constitutional amendment to change
     * Constitutional policies require 2/3 majority vote and cannot be vetoed
     */
    public boolean requiresConstitutionalAmendment() {
        return category == Category.CONSTITUTIONAL;
    }

    public boolean isValidValue(String value) {
        try {
            switch (valueType) {
                case BOOLEAN:
                    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
                case INTEGER:
                    int intVal = Integer.parseInt(value);
                    return intVal >= minValue && intVal <= maxValue;
                case PERCENTAGE:
                case CURRENCY:
                    double doubleVal = Double.parseDouble(value);
                    return doubleVal >= minValue && doubleVal <= maxValue;
                case TEXT:
                case NATION_TARGET:
                    return value != null && !value.trim().isEmpty();
                case CHUNK_TARGET:
                    // Format: "chunkX,chunkZ,dimension"
                    if (value == null || value.trim().isEmpty()) return false;
                    String[] parts = value.split(",", 3);
                    if (parts.length < 3) return false;
                    Integer.parseInt(parts[0].trim());
                    Integer.parseInt(parts[1].trim());
                    return !parts[2].trim().isEmpty();
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public enum Category {
        TAXATION("Taxation"),
        TERRITORY("Territory"),
        MEMBERSHIP("Membership"),
        DIPLOMACY("Diplomacy"),
        ECONOMY("Economy"),
        CONSTITUTIONAL("Constitutional"),
        CUSTOM("Custom Laws");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public enum ValueType {
        BOOLEAN,
        INTEGER,
        PERCENTAGE,
        CURRENCY,
        TEXT,
        NATION_TARGET,
        CHUNK_TARGET
    }
}

