package dev.statecraft.client;

import dev.statecraft.StateCraft;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.client.state.MapSelection;
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
import org.lwjgl.glfw.GLFW;

final class TerritoryMapScreen extends Screen {
    private final Screen parent;
    private final Map<Long, TerritorySnapshot.Territory> chunks = new HashMap<>();
    private TerritorySnapshot snapshot;
    private int cell;
    private int left;
    private int top;
    private int diameter;
    private int column;
    private int row;
    private ChunkKey selected;
    private Component hovered = ClientText.tr("gui.statecraft.map.hint", "Hover a chunk. Click or use arrows to select; right-click copies.");
    private Component status = Component.empty();
    private Path exportedPath;
    private Button copyPath;
    private Button copyChunk;
    private Button open;
    private Button information;

    TerritoryMapScreen(Screen parent) {
        super(ClientText.tr("gui.statecraft.map.title", "Territory map"));
        this.parent = parent;
    }
    @Override
    protected void init() {
        refreshSnapshot();
        int rowWidth = Math.min(440, width - 24);
        int rowLeft = (width - rowWidth) / 2;
        int buttonWidth = (rowWidth - 8) / 3;
        information = addRenderableWidget(Button.builder(Component.empty(), ignored -> minecraft.setScreen(new InformationScreen(
                        this, title, status.getString().isEmpty() ? hovered : status.copy().append("\n").append(hovered))))
                .bounds(rowLeft, height - 84, rowWidth, 18).build());
        copyChunk = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.map.copy_chunk", "Copy chunk"), ignored -> copyChunk())
                .bounds(rowLeft, height - 60, buttonWidth, 20).build());
        open = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.map.open_claim", "Open claim"), ignored -> {
                    if (selected != null) ClientHooks.openEntity(MapSelection.reference(selected));
                }).bounds(rowLeft + buttonWidth + 4, height - 60, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.map.borders", "Cycle borders"), ignored -> ClientHooks.cycleBorders())
                .bounds(rowLeft + (buttonWidth + 4) * 2, height - 60, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.map.export", "Export Xaero"), ignored -> export())
                .tooltip(Tooltip.create(ClientText.tr("gui.statecraft.map.export_hint", "Export nearby supported city waypoints for manual Xaero import.")))
                .bounds(rowLeft, height - 28, buttonWidth, 20).build());
        copyPath = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.map.copy_path", "Copy path"), ignored -> {
            if (exportedPath != null) {
                minecraft.keyboardHandler.setClipboard(exportedPath.toString());
                status = ClientText.tr("gui.statecraft.map.path_copied", "Copied export path.");
            }
        }).bounds(rowLeft + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(rowLeft + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        controls();
    }
    private void refreshSnapshot() {
        TerritorySnapshot replacement = ClientHooks.territory();
        if (snapshot == null || !snapshot.dimension().equals(replacement.dimension())) {
            selected = null;
            column = replacement.radius();
            row = replacement.radius();
        }
        snapshot = replacement;
        chunks.clear();
        snapshot.territories().stream().filter(t -> MapSelection.supported(t.x(), t.z()))
                .forEach(t -> chunks.put(key(t.x(), t.z()), t));
        diameter = snapshot.radius() * 2 + 1;
        cell = Math.max(1, Math.min((height - 124) / diameter, (width - 32) / diameter));
        left = (width - diameter * cell) / 2;
        top = 30;
        if (selected != null && (!selected.dimension().equals(snapshot.dimension())
                || Math.abs((long) selected.x() - snapshot.centerX()) > snapshot.radius()
                || Math.abs((long) selected.z() - snapshot.centerZ()) > snapshot.radius())) selected = null;
        if (selected != null) {
            column = selected.x() - snapshot.centerX() + snapshot.radius();
            row = selected.z() - snapshot.centerZ() + snapshot.radius();
        }
    }
    private void controls() {
        copyPath.active = exportedPath != null;
        copyChunk.active = selected != null;
        open.active = selected != null;
        open.setMessage(selected != null && !chunks.containsKey(key(selected.x(), selected.z()))
                ? ClientText.tr("gui.statecraft.map.open_wilderness", "Claim options")
                : ClientText.tr("gui.statecraft.map.open_claim", "Open claim"));
        information.setMessage(status.getString().isEmpty() ? hovered : status);
    }
    @Override
    public void tick() {
        if (snapshot != ClientHooks.territory()) refreshSnapshot();
        controls();
    }
    private void export() {
        try {
            exportedPath = XaeroWaypointExporter.exportXaero(MapSelection.exportable(snapshot),
                    FMLPaths.GAMEDIR.get().resolve("statecraft").resolve("exports").resolve("xaero"));
            status = ClientText.tr("gui.statecraft.map.exported", "Waypoints exported. Copy the path to locate the import file.");
        } catch (IOException failure) {
            StateCraft.LOGGER.error("Could not export StateCraft waypoints.", failure);
            status = ClientText.tr("gui.statecraft.map.export_failed", "Export failed: %s", failure.getMessage());
        }
        controls();
    }
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    private Component describe(ChunkKey chunk) {
        var territory = chunks.get(key(chunk.x(), chunk.z()));
        Component text = Component.literal(chunk.toString());
        if (territory == null) return text.copy().append("\n").append(ClientText.tr("gui.statecraft.map.wilderness", "Wilderness"));
        return text.copy().append("\n").append(territory.nationName() + " > " + territory.stateName() + " > " + territory.cityName())
                .append("\n").append(ClientText.tr("gui.statecraft.map.owner", "Owner: %s | Improvements: %s",
                        territory.ownerAccount(), territory.improvements()));
    }
    private void select(int column, int row) {
        var chunk = MapSelection.cell(snapshot, column, row);
        if (chunk.isEmpty()) {
            status = ClientText.tr("gui.statecraft.map.unavailable",
                    "Outside the supported world range. This cell cannot be selected, opened or exported.");
            return;
        }
        this.column = column;
        this.row = row;
        selected = chunk.get();
        hovered = describe(selected);
        status = Component.empty();
    }
    private void copyChunk() {
        if (selected != null) {
            minecraft.keyboardHandler.setClipboard(selected.toString());
            status = ClientText.tr("gui.statecraft.map.copied", "Copied: %s", selected);
        }
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEC121923);
        graphics.drawCenteredString(font, title, width / 2, 10, 0x71D6C1);
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                var chunk = MapSelection.cell(snapshot, x, z);
                TerritorySnapshot.Territory territory = chunk.map(value -> chunks.get(key(value.x(), value.z()))).orElse(null);
                int px = left + x * cell;
                int py = top + z * cell;
                graphics.fill(px, py, px + Math.max(1, cell - 1), py + Math.max(1, cell - 1),
                        chunk.isEmpty() ? 0xFF090C10 : territory == null ? 0xFF253443 : 0xFF000000 | territory.color());
                if (chunk.isEmpty()) {
                    graphics.fill(px + cell / 2, py, px + cell / 2 + 1, py + Math.max(1, cell - 1), 0xFF645F63);
                } else if (chunk.get().equals(selected)) {
                    graphics.renderOutline(px, py, cell, cell, 0xFFFFD470);
                } else if (x == snapshot.radius() && z == snapshot.radius()) {
                    graphics.renderOutline(px, py, cell, cell, 0xFFFFFFFF);
                }
                if (mouseX >= px && mouseX < px + cell && mouseY >= py && mouseY < py + cell) {
                    hovered = chunk.map(this::describe).orElseGet(() -> ClientText.tr("gui.statecraft.map.unavailable",
                            "Outside the supported world range. This cell cannot be selected, opened or exported."));
                }
            }
        }
        Component caption = selected == null ? ClientText.tr("gui.statecraft.map.hint",
                "Hover a chunk. Click or use arrows to select; right-click copies.") : Component.literal(selected.toString());
        graphics.drawString(font, font.plainSubstrByWidth(caption.getString(), width - 24), 12,
                Math.min(height - 97, top + diameter * cell + 5), 0xDFE9F2, false);
        controls();
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && mouseX >= left && mouseX < left + diameter * cell
                && mouseY >= top && mouseY < top + diameter * cell) {
            int x = (int) ((mouseX - left) / cell);
            int z = (int) ((mouseY - top) / cell);
            if (MapSelection.cell(snapshot, x, z).isPresent()) {
                select(x, z);
                if (button == 1) copyChunk();
            } else status = ClientText.tr("gui.statecraft.map.unavailable",
                    "Outside the supported world range. This cell cannot be selected, opened or exported.");
            controls();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> select(column - 1, row);
            case GLFW.GLFW_KEY_RIGHT -> select(column + 1, row);
            case GLFW.GLFW_KEY_UP -> select(column, row - 1);
            case GLFW.GLFW_KEY_DOWN -> select(column, row + 1);
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        controls();
        return true;
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(hovered); }
    @Override
    public void onClose() { minecraft.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
