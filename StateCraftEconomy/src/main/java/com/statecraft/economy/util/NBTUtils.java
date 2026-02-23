package com.statecraft.economy.util;

import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;

/**
 * Utility class for safe NBT operations to prevent data corruption.
 * All string operations sanitize input and handle null values.
 *
 * This is a copy of com.statecraft.util.NBTUtils for the Economy module.
 */
public final class NBTUtils {

    private NBTUtils() {} // Utility class

    /**
     * Safely put a string into NBT, handling null values.
     * Null strings are saved as empty strings.
     */
    public static void putStringSafe(CompoundTag tag, String key, @Nullable String value) {
        tag.putString(key, value != null ? value : "");
    }

    /**
     * Safely put a string into NBT with sanitization.
     * Removes potentially problematic characters that could cause UTF encoding issues.
     * Use this for user-provided strings (names, descriptions, etc.)
     */
    public static void putSanitizedString(CompoundTag tag, String key, @Nullable String value) {
        tag.putString(key, sanitizeString(value));
    }

    /**
     * Get a string from NBT with null safety.
     * Returns empty string if the key doesn't exist.
     */
    public static String getStringSafe(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getString(key) : "";
    }

    /**
     * Get a string from NBT, returning a default value if not present.
     */
    public static String getStringOrDefault(CompoundTag tag, String key, String defaultValue) {
        return tag.contains(key) ? tag.getString(key) : defaultValue;
    }

    /**
     * Sanitize a string to prevent NBT corruption.
     * - Handles null values
     * - Removes control characters (except standard whitespace)
     * - Removes surrogate pairs that might be incomplete
     * - Trims excessive length to prevent memory issues
     */
    public static String sanitizeString(@Nullable String input) {
        if (input == null) {
            return "";
        }

        // Remove control characters except tab, newline, carriage return
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Skip control characters (except whitespace we want to keep)
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                continue;
            }

            // Handle surrogate pairs carefully
            if (Character.isHighSurrogate(c)) {
                // Make sure there's a valid low surrogate following
                if (i + 1 < input.length() && Character.isLowSurrogate(input.charAt(i + 1))) {
                    sb.append(c);
                    sb.append(input.charAt(i + 1));
                    i++; // Skip the low surrogate
                }
                // Otherwise skip the orphaned high surrogate
                continue;
            }

            // Skip orphaned low surrogates
            if (Character.isLowSurrogate(c)) {
                continue;
            }

            sb.append(c);
        }

        String result = sb.toString();

        // Limit string length to prevent excessive memory usage
        // 32KB should be more than enough for any reasonable text field
        if (result.length() > 32768) {
            result = result.substring(0, 32768);
        }

        return result;
    }

    /**
     * Validate that a string is safe for NBT storage.
     * Returns true if the string won't cause encoding issues.
     */
    public static boolean isValidForNBT(@Nullable String input) {
        if (input == null) {
            return true; // Null is handled by putStringSafe
        }

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Check for orphaned surrogates
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= input.length() || !Character.isLowSurrogate(input.charAt(i + 1))) {
                    return false;
                }
                i++; // Skip the valid low surrogate
            } else if (Character.isLowSurrogate(c)) {
                return false; // Orphaned low surrogate
            }
        }

        return true;
    }
}

