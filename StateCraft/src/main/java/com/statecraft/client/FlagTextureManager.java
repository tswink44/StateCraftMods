package com.statecraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.statecraft.StateCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages downloading and caching flag textures from URLs
 */
public class FlagTextureManager {
    private static FlagTextureManager instance;

    // Cache of URL -> ResourceLocation for loaded textures
    private final Map<String, ResourceLocation> textureCache = new ConcurrentHashMap<>();

    // Track loading state to avoid duplicate requests
    private final Map<String, CompletableFuture<ResourceLocation>> loadingFutures = new ConcurrentHashMap<>();

    // Placeholder for failed/loading textures
    private static final ResourceLocation PLACEHOLDER = new ResourceLocation(StateCraft.MOD_ID, "textures/gui/flag_placeholder.png");

    private int textureIdCounter = 0;

    private FlagTextureManager() {}

    public static FlagTextureManager getInstance() {
        if (instance == null) {
            instance = new FlagTextureManager();
        }
        return instance;
    }

    /**
     * Get a flag texture, loading it asynchronously if needed
     * @param url The URL of the flag image
     * @return ResourceLocation of the texture, or placeholder if not loaded yet
     */
    @Nullable
    public ResourceLocation getFlagTexture(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Check cache first
        ResourceLocation cached = textureCache.get(url);
        if (cached != null) {
            return cached;
        }

        // Start async load if not already loading
        if (!loadingFutures.containsKey(url)) {
            loadFlagAsync(url);
        }

        return null; // Return null while loading
    }

    /**
     * Check if a flag is currently being loaded
     */
    public boolean isLoading(String url) {
        return loadingFutures.containsKey(url) && !loadingFutures.get(url).isDone();
    }

    /**
     * Load a flag texture asynchronously
     */
    private void loadFlagAsync(String url) {
        CompletableFuture<ResourceLocation> future = CompletableFuture.supplyAsync(() -> {
            try {
                return downloadAndRegisterTexture(url);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Failed to load flag from URL: {}", url, e);
                return null;
            }
        }).thenApplyAsync(result -> {
            // Must register texture on main thread
            if (result != null) {
                textureCache.put(url, result);
            }
            loadingFutures.remove(url);
            return result;
        }, Minecraft.getInstance());

        loadingFutures.put(url, future);
    }

    /**
     * Download image from URL and register as texture
     */
    @Nullable
    private ResourceLocation downloadAndRegisterTexture(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "StateCraft Minecraft Mod");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                StateCraft.LOGGER.warn("Failed to download flag: HTTP {}", responseCode);
                return null;
            }

            // Check content type
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                StateCraft.LOGGER.warn("Invalid content type for flag: {}", contentType);
                return null;
            }

            // Limit file size (max 1MB)
            int contentLength = connection.getContentLength();
            if (contentLength > 1024 * 1024) {
                StateCraft.LOGGER.warn("Flag image too large: {} bytes", contentLength);
                return null;
            }

            try (InputStream inputStream = connection.getInputStream()) {
                NativeImage image = NativeImage.read(inputStream);

                // Resize if too large (max 128x128 for flags)
                if (image.getWidth() > 128 || image.getHeight() > 128) {
                    // For now, just use as-is - Minecraft will handle scaling
                }

                // Register texture on main thread
                final NativeImage finalImage = image;
                final int id = textureIdCounter++;

                ResourceLocation location = new ResourceLocation(StateCraft.MOD_ID, "flag_" + id);

                Minecraft.getInstance().execute(() -> {
                    DynamicTexture texture = new DynamicTexture(finalImage);
                    Minecraft.getInstance().getTextureManager().register(location, texture);
                });

                return location;
            }
        } catch (Exception e) {
            StateCraft.LOGGER.warn("Error downloading flag texture: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Clear all cached textures
     */
    public void clearCache() {
        for (ResourceLocation location : textureCache.values()) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
        textureCache.clear();
        loadingFutures.clear();
    }

    /**
     * Remove a specific texture from cache
     */
    public void invalidate(String url) {
        ResourceLocation location = textureCache.remove(url);
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
    }
}

