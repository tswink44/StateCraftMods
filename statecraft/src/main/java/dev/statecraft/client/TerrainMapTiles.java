package dev.statecraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.statecraft.StateCraft;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.client.state.TerrainTileCache;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One block per map pixel. Call beginFrame once before drawing tiles, and close when leaving the screen. */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public final class TerrainMapTiles implements AutoCloseable {
    public enum State { READY, LOADING, UNLOADED, UNAVAILABLE }
    private static final Set<TerrainMapTiles> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private final TerrainTileCache<ChunkKey, ResourceLocation> cache = new TerrainTileCache<>(
            location -> Minecraft.getInstance().getTextureManager().release(location));
    private String dimension = "";
    private int levelIdentity;
    private long frameTime;
    private boolean closed;
    private boolean loggedFailure;

    public TerrainMapTiles() { LIVE.add(this); }

    public void beginFrame(ClientLevel level) {
        if (closed) return;
        dimension = level == null ? "" : level.dimension().location().toString();
        levelIdentity = level == null ? 0 : System.identityHashCode(level);
        frameTime = Util.getMillis();
        cache.beginFrame(dimension + "@" + levelIdentity);
    }

    public State draw(GuiGraphics graphics, ClientLevel level, int chunkX, int chunkZ, int x, int y, int size) {
        hatch(graphics, x, y, size);
        if (closed || level == null) return State.UNLOADED;
        if (levelIdentity != System.identityHashCode(level) || !dimension.equals(level.dimension().location().toString())) {
            beginFrame(level);
        }
        var key = new ChunkKey(dimension, chunkX, chunkZ);
        // The false flag is essential: unloaded client chunks are never requested or replaced by empty terrain.
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            cache.discard(key);
            return State.UNLOADED;
        }
        if (cache.reserveUpdate(key, frameTime)) {
            ResourceLocation location = null;
            DynamicTexture texture = null;
            NativeImage image = null;
            try {
                image = surface(chunk, chunkX, chunkZ);
                texture = new DynamicTexture(image);
                texture.setFilter(false, false);
                location = Minecraft.getInstance().getTextureManager().register("statecraft_claim_map", texture);
                cache.put(key, location, frameTime);
            } catch (RuntimeException failure) {
                if (location != null) Minecraft.getInstance().getTextureManager().release(location);
                else if (texture != null) texture.close();
                else if (image != null) image.close();
                cache.put(key, null, frameTime);
                if (!loggedFailure) {
                    loggedFailure = true;
                    StateCraft.LOGGER.warn("Could not render a loaded chunk's surface terrain on the claim map.", failure);
                }
            }
        }
        ResourceLocation location = cache.get(key);
        if (location == null) return cache.contains(key) ? State.UNAVAILABLE : State.LOADING;
        graphics.blit(location, x, y, size, size, 0.0f, 0.0f, 16, 16, 16, 16);
        return State.READY;
    }

    private static NativeImage surface(LevelChunk chunk, int chunkX, int chunkZ) {
        NativeImage image = new NativeImage(16, 16, false);
        try {
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            int[] north = new int[16];
            int minimum = chunk.getMinBuildHeight();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    MapColor color = MapColor.NONE;
                    for (int y = height; y >= minimum && color == MapColor.NONE; y--) {
                        position.set((chunkX << 4) + x, y, (chunkZ << 4) + z);
                        var state = chunk.getBlockState(position);
                        var fluid = state.getFluidState();
                        if (!fluid.isEmpty() && !state.isFaceSturdy(chunk, position, Direction.UP)) {
                            state = fluid.createLegacyBlock();
                        }
                        color = state.getMapColor(chunk, position);
                        height = y;
                    }
                    MapColor.Brightness brightness = z == 0 || height == north[x] || color == MapColor.WATER
                            ? MapColor.Brightness.NORMAL : height > north[x] ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                    // Minecraft's map color is already ABGR, the byte order required by NativeImage.
                    image.setPixelRGBA(x, z, color.calculateRGBColor(brightness));
                    north[x] = height;
                }
            }
            return image;
        } catch (RuntimeException failure) {
            image.close();
            throw failure;
        }
    }

    private static void hatch(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, 0xFF20282D);
        for (int offset = 1; offset < size; offset += 4) {
            graphics.fill(x + offset, y, x + offset + 1, y + size, 0xFF344048);
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        for (TerrainMapTiles tiles : List.copyOf(LIVE)) tiles.close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        LIVE.remove(this);
        if (RenderSystem.isOnRenderThread()) cache.close();
        else RenderSystem.recordRenderCall(cache::close);
    }
}
