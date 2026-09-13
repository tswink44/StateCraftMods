package dev.statecraft.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.statecraft.StateCraft;
import dev.statecraft.api.BoundaryEdges;
import dev.statecraft.api.TerritorySnapshot;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public final class TerritoryRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static TerritorySnapshot last;
    private static BoundaryEdges.Mode lastMode;
    private static List<BoundaryEdges.Edge> edges = List.of();

    private TerritoryRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BoundaryEdges.Mode mode = ClientHooks.borderMode();
        TerritorySnapshot snapshot = ClientHooks.territory();
        if (minecraft.level == null || minecraft.player == null || mode == BoundaryEdges.Mode.OFF
                || !snapshot.dimension().equals(minecraft.level.dimension().location().toString())) {
            return;
        }
        if (last != snapshot || lastMode != mode) {
            edges = BoundaryEdges.of(snapshot, mode);
            last = snapshot;
            lastMode = mode;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var consumer = BUFFERS.getBuffer(RenderType.lines());
        double y = minecraft.player.getY();
        for (BoundaryEdges.Edge edge : edges) {
            float red = ((edge.color() >> 16) & 255) / 255f;
            float green = ((edge.color() >> 8) & 255) / 255f;
            float blue = (edge.color() & 255) / 255f;
            LevelRenderer.renderLineBox(pose, consumer, edge.x1(), y - 2, edge.z1(), edge.x2(), y + 12, edge.z2(),
                    red, green, blue, 0.85f);
        }
        if (mode == BoundaryEdges.Mode.CHUNK) {
            int cx = minecraft.player.chunkPosition().x;
            int cz = minecraft.player.chunkPosition().z;
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    LevelRenderer.renderLineBox(pose, consumer, x * 16, y - 1, z * 16,
                            x * 16 + 16, y + 4, z * 16 + 16, 0.85f, 0.85f, 0.85f, 0.6f);
                }
            }
        }
        BUFFERS.endBatch(RenderType.lines());
        pose.popPose();
    }
}
