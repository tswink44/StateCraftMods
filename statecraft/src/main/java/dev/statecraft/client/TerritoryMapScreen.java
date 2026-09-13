package dev.statecraft.client;

import dev.statecraft.StateCraft;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.compat.XaeroWaypointExporter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

final class TerritoryMapScreen extends Screen {
    private final Screen parent;
    private final Map<Long, TerritorySnapshot.Territory> chunks = new HashMap<>();
    private TerritorySnapshot snapshot;
    private int cell;
    private int left;
    private int top;
    private int diameter;
    private String selected = "Hover a chunk to inspect its government and owner.";
    private String copied = "";
    private String exportStatus = "";
    private Path exportedPath;
    private int statusColor = 0x71D6C1;
    private Button copyPath;

    TerritoryMapScreen(Screen parent) {
        super(Component.literal("Territory Map"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshSnapshot();
        int rowWidth = Math.min(360, width - 24);
        int rowLeft = (width - rowWidth) / 2;
        int buttonWidth = (rowWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Export Xaero"), ignored -> export())
                .tooltip(Tooltip.create(Component.literal("Export nearby city waypoints for manual Xaero import.")))
                .bounds(rowLeft, height - 26, buttonWidth, 20).build());
        copyPath = addRenderableWidget(Button.builder(Component.literal("Copy path"), ignored -> {
            minecraft.keyboardHandler.setClipboard(exportedPath.toString());
            exportStatus = "Copied export path.";
            statusColor = 0x71D6C1;
        }).bounds(rowLeft + buttonWidth + 4, height - 26, buttonWidth, 20).build());
        copyPath.active = exportedPath != null;
        if (exportedPath != null) {
            copyPath.setTooltip(Tooltip.create(Component.literal(exportedPath.toString())));
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(rowLeft + (buttonWidth + 4) * 2, height - 26, buttonWidth, 20).build());
    }

    private void refreshSnapshot() {
        snapshot = ClientHooks.territory();
        chunks.clear();
        snapshot.territories().forEach(t -> chunks.put(key(t.x(), t.z()), t));
        diameter = snapshot.radius() * 2 + 1;
        cell = Math.max(3, Math.min((height - 102) / diameter, (width - 32) / diameter));
        left = (width - diameter * cell) / 2;
        top = 30;
    }

    @Override
    public void tick() {
        if (snapshot != ClientHooks.territory()) {
            refreshSnapshot();
        }
    }

    private void export() {
        try {
            exportedPath = XaeroWaypointExporter.exportXaero(snapshot,
                    FMLPaths.GAMEDIR.get().resolve("statecraft").resolve("exports").resolve("xaero"));
            exportStatus = "Waypoints exported. Copy the path to locate the import file.";
            statusColor = 0x71D6C1;
            copied = "";
            copyPath.active = true;
            copyPath.setTooltip(Tooltip.create(Component.literal(exportedPath.toString())));
        } catch (IOException e) {
            StateCraft.LOGGER.error("Could not export StateCraft waypoints.", e);
            exportStatus = "Export failed: " + e.getMessage();
            statusColor = 0xFF9292;
            copied = "";
        }
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xE8121923);
        graphics.drawCenteredString(font, title, width / 2, 10, 0x71D6C1);
        if (!copied.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth("Copied: " + copied, width - 16),
                    8, height - 37, 0x71D6C1, false);
        } else if (!exportStatus.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(exportStatus, width - 16),
                    8, height - 37, statusColor, false);
        }
        for (int dx = 0; dx < diameter; dx++) {
            for (int dz = 0; dz < diameter; dz++) {
                int x = snapshot.centerX() - snapshot.radius() + dx;
                int z = snapshot.centerZ() - snapshot.radius() + dz;
                TerritorySnapshot.Territory territory = chunks.get(key(x, z));
                int px = left + dx * cell;
                int pz = top + dz * cell;
                graphics.fill(px, pz, px + cell - 1, pz + cell - 1,
                        territory == null ? 0xFF253443 : 0xFF000000 | territory.color());
                if (x == snapshot.centerX() && z == snapshot.centerZ()) {
                    graphics.renderOutline(px, pz, cell, cell, 0xFFFFFFFF);
                }
                if (mouseX >= px && mouseX < px + cell && mouseY >= pz && mouseY < pz + cell) {
                    graphics.renderOutline(px, pz, cell, cell, 0xFFFFD470);
                    selected = snapshot.dimension() + " | " + x + ", " + z + "\n"
                            + (territory == null ? "Wilderness" : territory.nationName() + " > "
                                + territory.stateName() + " > " + territory.cityName()
                                + "\nOwner: " + territory.ownerAccount() + " | Improvements: " + territory.improvements());
                }
            }
        }
        int y = top + diameter * cell + 6;
        for (var line : font.split(Component.literal(selected), width - 32)) {
            if (y > height - 48) {
                break;
            }
            graphics.drawString(font, line, 16, y, 0xDFE9F2, false);
            y += 10;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= left && mouseX < left + diameter * cell
                && mouseY >= top && mouseY < top + diameter * cell) {
            int x = snapshot.centerX() - snapshot.radius() + (int) ((mouseX - left) / cell);
            int z = snapshot.centerZ() - snapshot.radius() + (int) ((mouseY - top) / cell);
            copied = new ChunkKey(snapshot.dimension(), x, z).toString();
            minecraft.keyboardHandler.setClipboard(copied);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
