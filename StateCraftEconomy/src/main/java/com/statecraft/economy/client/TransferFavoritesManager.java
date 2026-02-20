package com.statecraft.economy.client;

import com.google.gson.*;
import com.statecraft.economy.StateCraftEconomy;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Client-side manager for transfer recipient favorites.
 * Stores favorites per-player in a JSON file in the game directory.
 * Favorites are keyed by (recipientType, recipientId) so a player can be
 * favorited independently of a nation, etc.
 */
public class TransferFavoritesManager {

    private static TransferFavoritesManager instance;

    // Set of favorite keys: "TYPE:id" (e.g., "PLAYER:uuid-string", "NATION:uuid-string")
    private final Set<String> favorites = new LinkedHashSet<>();

    private Path savePath;
    private boolean loaded = false;

    private TransferFavoritesManager() {}

    public static TransferFavoritesManager getInstance() {
        if (instance == null) {
            instance = new TransferFavoritesManager();
        }
        return instance;
    }

    /**
     * Build the storage key for a favorite entry.
     */
    private static String makeKey(String recipientType, String recipientId) {
        return recipientType + ":" + recipientId;
    }

    /**
     * Check if a recipient is marked as a favorite.
     */
    public boolean isFavorite(String recipientType, String recipientId) {
        ensureLoaded();
        return favorites.contains(makeKey(recipientType, recipientId));
    }

    /**
     * Toggle the favorite status of a recipient.
     * @return true if the recipient is now a favorite, false if removed
     */
    public boolean toggleFavorite(String recipientType, String recipientId, String recipientName) {
        ensureLoaded();
        String key = makeKey(recipientType, recipientId);
        boolean added;
        if (favorites.contains(key)) {
            favorites.remove(key);
            added = false;
        } else {
            favorites.add(key);
            added = true;
        }
        save();
        return added;
    }

    /**
     * Add a recipient as a favorite.
     */
    public void addFavorite(String recipientType, String recipientId) {
        ensureLoaded();
        favorites.add(makeKey(recipientType, recipientId));
        save();
    }

    /**
     * Remove a recipient from favorites.
     */
    public void removeFavorite(String recipientType, String recipientId) {
        ensureLoaded();
        favorites.remove(makeKey(recipientType, recipientId));
        save();
    }

    /**
     * Get all favorite keys for a specific recipient type.
     * @return Set of recipientId strings that are favorited for this type
     */
    public Set<String> getFavoriteIdsForType(String recipientType) {
        ensureLoaded();
        Set<String> result = new LinkedHashSet<>();
        String prefix = recipientType + ":";
        for (String key : favorites) {
            if (key.startsWith(prefix)) {
                result.add(key.substring(prefix.length()));
            }
        }
        return result;
    }

    // ==================== Persistence ====================

    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private Path getSavePath() {
        if (savePath == null) {
            // Store in the Minecraft game directory under config/
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            savePath = gameDir.resolve("config").resolve("statecraft_economy_favorites.json");
        }
        return savePath;
    }

    private void load() {
        favorites.clear();
        Path path = getSavePath();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("favorites")) {
                JsonArray arr = root.getAsJsonArray("favorites");
                for (JsonElement el : arr) {
                    favorites.add(el.getAsString());
                }
            }
            StateCraftEconomy.LOGGER.debug("Loaded {} transfer favorites", favorites.size());
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.warn("Failed to load transfer favorites: {}", e.getMessage());
        }
    }

    private void save() {
        Path path = getSavePath();
        try {
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String key : favorites) {
                arr.add(key);
            }
            root.add("favorites", arr);

            try (Writer writer = Files.newBufferedWriter(path)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
            StateCraftEconomy.LOGGER.debug("Saved {} transfer favorites", favorites.size());
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.warn("Failed to save transfer favorites: {}", e.getMessage());
        }
    }
}

