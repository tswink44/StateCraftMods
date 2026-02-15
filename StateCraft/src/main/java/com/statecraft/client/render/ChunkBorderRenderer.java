package com.statecraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.statecraft.StateCraft;
import com.statecraft.client.ChunkBorderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Renders chunk claim borders in the world
 * Shows colored lines at chunk boundaries for claimed chunks
 */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public class ChunkBorderRenderer {

    private static final float BORDER_HEIGHT = 256.0f; // Full world height
    private static final float LINE_WIDTH = 2.0f;
    private static final int RENDER_DISTANCE = 8; // Chunks to render (should match or be less than cache radius)

    // Colors (RGBA 0-1 range)
    private static final float[] COLOR_OWN = {0.2f, 0.8f, 0.2f, 0.6f};      // Green
    private static final float[] COLOR_ALLY = {0.2f, 0.6f, 1.0f, 0.6f};     // Blue
    private static final float[] COLOR_ENEMY = {1.0f, 0.2f, 0.2f, 0.6f};    // Red
    private static final float[] COLOR_OTHER = {1.0f, 0.6f, 0.0f, 0.5f};    // Orange
    private static final float[] COLOR_UNCLAIMED = {0.5f, 0.5f, 0.5f, 0.3f}; // Gray for unclaimed chunks

    private static boolean enabled = true;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled) return;

        // Check if we should render individual chunk borders
        TerritoryBorderRenderer.BorderMode mode = TerritoryBorderRenderer.getMode();
        if (mode != TerritoryBorderRenderer.BorderMode.ALL_CHUNKS &&
            mode != TerritoryBorderRenderer.BorderMode.MY_NATION_CHUNKS) return;

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean myNationOnly = (mode == TerritoryBorderRenderer.BorderMode.MY_NATION_CHUNKS);
        boolean showAllChunks = (mode == TerritoryBorderRenderer.BorderMode.ALL_CHUNKS);

        // Get camera position
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

        // Render borders for nearby chunks
        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);

                // In ALL_CHUNKS mode, render every chunk with appropriate color
                if (showAllChunks) {
                    float[] color;
                    if (info != null && info.isClaimed()) {
                        color = getColorForChunk(info);
                    } else {
                        color = COLOR_UNCLAIMED; // Gray for unclaimed
                    }
                    renderChunkBorder(buffer, matrix, chunkX, chunkZ, color, cameraPos.y);
                } else {
                    // MY_NATION_CHUNKS mode - only show claimed chunks that are ours
                    if (info == null || !info.isClaimed()) continue;
                    if (myNationOnly && !info.isOwn()) continue;

                    float[] color = getColorForChunk(info);
                    renderChunkBorder(buffer, matrix, chunkX, chunkZ, color, cameraPos.y);
                }
            }
        }

        tesselator.end();

        // Reset state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();
    }

    private static float[] getColorForChunk(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return COLOR_OWN;
        if (info.isAlly()) return COLOR_ALLY;
        if (info.isEnemy()) return COLOR_ENEMY;
        return COLOR_OTHER;
    }

    private static void renderChunkBorder(BufferBuilder buffer, Matrix4f matrix,
                                           int chunkX, int chunkZ, float[] color, double cameraY) {
        // Chunk world coordinates
        int x1 = chunkX * 16;
        int z1 = chunkZ * 16;
        int x2 = x1 + 16;
        int z2 = z1 + 16;

        // Render at multiple Y levels for visibility
        float minY = (float) Math.max(-64, cameraY - 32);
        float maxY = (float) Math.min(320, cameraY + 32);

        // Vertical lines at corners
        addLine(buffer, matrix, x1, minY, z1, x1, maxY, z1, color);
        addLine(buffer, matrix, x2, minY, z1, x2, maxY, z1, color);
        addLine(buffer, matrix, x1, minY, z2, x1, maxY, z2, color);
        addLine(buffer, matrix, x2, minY, z2, x2, maxY, z2, color);

        // Horizontal lines at camera level
        float y = (float) cameraY;

        // Bottom edges
        addLine(buffer, matrix, x1, y, z1, x2, y, z1, color);
        addLine(buffer, matrix, x1, y, z2, x2, y, z2, color);
        addLine(buffer, matrix, x1, y, z1, x1, y, z2, color);
        addLine(buffer, matrix, x2, y, z1, x2, y, z2, color);

        // Additional horizontal lines above and below
        for (float yOffset : new float[]{-8, -4, 4, 8}) {
            float yLevel = (float) (cameraY + yOffset);
            if (yLevel < minY || yLevel > maxY) continue;

            addLine(buffer, matrix, x1, yLevel, z1, x2, yLevel, z1, color);
            addLine(buffer, matrix, x1, yLevel, z2, x2, yLevel, z2, color);
            addLine(buffer, matrix, x1, yLevel, z1, x1, yLevel, z2, color);
            addLine(buffer, matrix, x2, yLevel, z1, x2, yLevel, z2, color);
        }
    }

    private static void addLine(BufferBuilder buffer, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float[] color) {
        buffer.vertex(matrix, x1, y1, z1).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(color[0], color[1], color[2], color[3]).endVertex();
    }

    public static void setEnabled(boolean enabled) {
        ChunkBorderRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }
}

