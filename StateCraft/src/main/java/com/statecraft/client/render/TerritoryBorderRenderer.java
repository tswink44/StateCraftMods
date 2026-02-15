package com.statecraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.statecraft.StateCraft;
import com.statecraft.client.ChunkBorderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.*;

/**
 * Renders aggregate territory borders - only the outer edges of contiguous claimed regions
 * Shows nation boundaries rather than individual chunk borders
 */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public class TerritoryBorderRenderer {

    private static final float LINE_WIDTH = 3.0f;
    private static final int RENDER_DISTANCE = 10; // Chunks to consider (should match cache radius)

    // Colors (RGBA 0-1 range) - slightly more saturated than chunk borders
    private static final float[] COLOR_OWN = {0.1f, 0.9f, 0.1f, 0.85f};      // Bright Green
    private static final float[] COLOR_ALLY = {0.1f, 0.5f, 1.0f, 0.85f};     // Bright Blue
    private static final float[] COLOR_ENEMY = {1.0f, 0.1f, 0.1f, 0.85f};    // Bright Red
    private static final float[] COLOR_OTHER = {1.0f, 0.7f, 0.0f, 0.75f};    // Bright Orange

    private static boolean enabled = true;

    /**
     * Border display modes - cycles through with keybind
     */
    public enum BorderMode {
        OFF("Off", "§cOff"),                                    // No borders
        ALL_CHUNKS("All Chunks", "§eAll Chunk Borders"),        // Individual chunk borders for all
        MY_NATION_CHUNKS("My Nation Chunks", "§aYour Nation Chunks"),  // Chunk borders for your nation only
        MY_NATION_TERRITORY("My Nation Territory", "§2Your Nation Territory"), // Aggregate borders for your nation
        ALL_TERRITORY("All Territories", "§6All Nation Borders");  // Aggregate borders for all nations

        private final String name;
        private final String displayText;

        BorderMode(String name, String displayText) {
            this.name = name;
            this.displayText = displayText;
        }

        public String getName() { return name; }
        public String getDisplayText() { return displayText; }
    }

    private static BorderMode mode = BorderMode.ALL_TERRITORY;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || mode == BorderMode.OFF) return;

        // This renderer handles territory (aggregate) modes
        if (mode != BorderMode.MY_NATION_TERRITORY && mode != BorderMode.ALL_TERRITORY) return;

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        int playerChunkX = mc.player.chunkPosition().x;
        int playerChunkZ = mc.player.chunkPosition().z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Setup rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(LINE_WIDTH);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();

        // Group chunks by nation
        Map<String, List<int[]>> nationChunks = new HashMap<>();
        Map<String, float[]> nationColors = new HashMap<>();

        boolean myNationOnly = (mode == BorderMode.MY_NATION_TERRITORY);

        for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
            for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
                if (info == null || !info.isClaimed()) continue;

                // Filter: if my nation only mode, skip chunks that aren't ours
                if (myNationOnly && !info.isOwn()) continue;

                String nationName = info.getNationName();
                if (nationName == null) continue;

                nationChunks.computeIfAbsent(nationName, k -> new ArrayList<>())
                           .add(new int[]{chunkX, chunkZ});

                if (!nationColors.containsKey(nationName)) {
                    nationColors.put(nationName, getColorForChunk(info));
                }
            }
        }

        // Render aggregate borders for each nation
        for (Map.Entry<String, List<int[]>> entry : nationChunks.entrySet()) {
            String nationName = entry.getKey();
            List<int[]> chunks = entry.getValue();
            float[] color = nationColors.get(nationName);

            renderAggregateBorder(buffer, matrix, chunks, color, cameraPos.y);
        }

        tesselator.end();

        // Reset state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();
    }

    private static void renderAggregateBorder(BufferBuilder buffer, Matrix4f matrix,
                                               List<int[]> chunks, float[] color, double cameraY) {
        // Create a set for O(1) lookup
        Set<Long> chunkSet = new HashSet<>();
        for (int[] chunk : chunks) {
            chunkSet.add(packChunk(chunk[0], chunk[1]));
        }

        // For each chunk, check each edge - only render if neighbor is NOT in the same set
        for (int[] chunk : chunks) {
            int chunkX = chunk[0];
            int chunkZ = chunk[1];

            int x1 = chunkX * 16;
            int z1 = chunkZ * 16;
            int x2 = x1 + 16;
            int z2 = z1 + 16;

            float minY = (float) Math.max(-64, cameraY - 40);
            float maxY = (float) Math.min(320, cameraY + 40);

            // Check North edge (z1) - neighbor at z-1
            if (!chunkSet.contains(packChunk(chunkX, chunkZ - 1))) {
                renderEdge(buffer, matrix, x1, x2, z1, z1, minY, maxY, color, cameraY, true);
            }

            // Check South edge (z2) - neighbor at z+1
            if (!chunkSet.contains(packChunk(chunkX, chunkZ + 1))) {
                renderEdge(buffer, matrix, x1, x2, z2, z2, minY, maxY, color, cameraY, true);
            }

            // Check West edge (x1) - neighbor at x-1
            if (!chunkSet.contains(packChunk(chunkX - 1, chunkZ))) {
                renderEdge(buffer, matrix, x1, x1, z1, z2, minY, maxY, color, cameraY, false);
            }

            // Check East edge (x2) - neighbor at x+1
            if (!chunkSet.contains(packChunk(chunkX + 1, chunkZ))) {
                renderEdge(buffer, matrix, x2, x2, z1, z2, minY, maxY, color, cameraY, false);
            }
        }
    }

    private static void renderEdge(BufferBuilder buffer, Matrix4f matrix,
                                    int x1, int x2, int z1, int z2,
                                    float minY, float maxY, float[] color,
                                    double cameraY, boolean isHorizontalEdge) {
        // Vertical lines at edge endpoints
        addLine(buffer, matrix, x1, minY, z1, x1, maxY, z1, color);
        if (x1 != x2 || z1 != z2) {
            addLine(buffer, matrix, x2, minY, z2, x2, maxY, z2, color);
        }

        // Horizontal lines at various Y levels
        float[] yLevels = {
            (float) cameraY,
            (float) cameraY - 4, (float) cameraY + 4,
            (float) cameraY - 8, (float) cameraY + 8,
            (float) cameraY - 16, (float) cameraY + 16
        };

        for (float y : yLevels) {
            if (y < minY || y > maxY) continue;
            addLine(buffer, matrix, x1, y, z1, x2, y, z2, color);
        }
    }

    private static long packChunk(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    private static float[] getColorForChunk(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return COLOR_OWN;
        if (info.isAlly()) return COLOR_ALLY;
        if (info.isEnemy()) return COLOR_ENEMY;
        return COLOR_OTHER;
    }

    private static void addLine(BufferBuilder buffer, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float[] color) {
        buffer.vertex(matrix, x1, y1, z1).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(color[0], color[1], color[2], color[3]).endVertex();
    }

    public static void setEnabled(boolean enabled) {
        TerritoryBorderRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setMode(BorderMode newMode) {
        mode = newMode;
    }

    public static BorderMode getMode() {
        return mode;
    }

    public static void cycleMode() {
        BorderMode[] modes = BorderMode.values();
        int next = (mode.ordinal() + 1) % modes.length;
        mode = modes[next];
    }
}



