package dev.statecraft.domain;

import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class GovernanceSettings {
    static final Set<String> RATES = Set.of(
            "incomeTaxBps", "salesTaxBps", "propertyTaxBps", "tariffBps", "corporateTaxBps");
    static final Set<String> BOOLEANS = Set.of(
            "foreignAccess", "alliedAccess", "enemyAccess", "memberAccess", "pvp",
            "explosions", "foreignProperty", "open", "friendlyFire", "citizenLegislature",
            "peaceRatification");

    private GovernanceSettings() {}

    static String validate(String key, String value) {
        if (key == null || value == null) throw new UserError("Policy keys and values cannot be null.");
        if (RATES.contains(key)) {
            try {
                int rate = Integer.parseInt(value);
                if (rate < 0 || rate > 10_000) throw new NumberFormatException();
                return Integer.toString(rate);
            } catch (NumberFormatException e) {
                throw new UserError(key + " must be an integer between 0 and 10000 basis points.");
            }
        }
        if (BOOLEANS.contains(key)) {
            if (!"true".equals(value) && !"false".equals(value))
                throw new UserError(key + " must be true or false.");
            return value;
        }
        if ("baseChunkValue".equals(key)) {
            try {
                return Long.toString(Money.nonNegative(Long.parseLong(value)));
            } catch (NumberFormatException e) {
                throw new UserError("baseChunkValue must be a non-negative integer in cents.");
            }
        }
        throw new UserError("Unknown policy '" + key + "'. Use government settings to see supported policies.");
    }

    static Map<String, String> defaults(GovernanceConfig config) {
        Map<String, String> result = new LinkedHashMap<>();
        RATES.stream().sorted().forEach(key -> result.put(key, "0"));
        result.put("baseChunkValue", Long.toString(config.defaultBaseChunkValue));
        result.put("foreignAccess", Boolean.toString(config.defaultForeignAccess));
        result.put("alliedAccess", Boolean.toString(config.defaultAlliedAccess));
        result.put("enemyAccess", Boolean.toString(config.defaultEnemyAccess));
        result.put("memberAccess", Boolean.toString(config.defaultMemberAccess));
        result.put("pvp", Boolean.toString(config.defaultPvp));
        result.put("explosions", Boolean.toString(config.defaultExplosions));
        result.put("foreignProperty", Boolean.toString(config.defaultForeignProperty));
        result.put("open", Boolean.toString(config.defaultOpenMembership));
        result.put("friendlyFire", "false");
        result.put("citizenLegislature", Boolean.toString(config.citizenLegislature));
        result.put("peaceRatification", Boolean.toString(config.requirePeaceRatification));
        return result;
    }

    static boolean inherited(String key) {
        return key != null && !RATES.contains(key) && !"open".equals(key);
    }
}
