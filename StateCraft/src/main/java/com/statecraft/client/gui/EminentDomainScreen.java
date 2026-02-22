package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationPrivateChunksPacket;
import com.statecraft.network.packets.SyncNationPrivateChunksPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for selecting a privately owned chunk for eminent domain legislation.
 * Shows a table of all privately owned chunks in the nation with their owners
 * and valuations. Clicking a chunk selects it and returns to the bill proposal screen.
 */
public class EminentDomainScreen extends StateCraftScreen {

    private final String nationName;
    private final Consumer<String> onChunkSelected;  // Returns "chunkX,chunkZ,dimension"
    private final Runnable onCancel;

    // Data from server
    private boolean dataLoaded = false;
    private List<SyncNationPrivateChunksPacket.PrivateChunkEntry> chunks = new ArrayList<>();

    // Scrolling
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 8;
    private static final int ROW_HEIGHT = 16;

    // Selected row
    private int selectedIndex = -1;

    public EminentDomainScreen(String nationName, Consumer<String> onChunkSelected, Runnable onCancel) {
        super(Component.literal("Eminent Domain - Select Chunk"));
        this.nationName = nationName;
        this.onChunkSelected = onChunkSelected;
        this.onCancel = onCancel;
        this.guiWidth = 340;
        this.guiHeight = 230;
    }

    @Override
    protected void init() {
        super.init();

        // Request private chunk data from server
        NetworkHandler.sendToServer(new RequestNationPrivateChunksPacket(nationName));

        int buttonY = guiTop + guiHeight - 28;

        // Confirm button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 80, buttonY, 70, 20,
            Component.literal("§aConfirm"),
            btn -> confirmSelection()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 10, buttonY, 70, 20,
            Component.literal("Cancel"),
            btn -> cancel()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading privately owned chunks...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (chunks.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No privately owned chunks in this nation", this.width / 2, guiTop + 80, 0xFFAAAAAA);
            return;
        }

        // Column headers
        int headerY = guiTop + 28;
        int colCoords = guiLeft + 12;
        int colOwner = guiLeft + 80;
        int colCity = guiLeft + 155;
        int colValue = guiLeft + 235;
        int colCompensation = guiLeft + 285;

        graphics.drawString(this.font, "§6Chunk", colCoords, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6Owner", colOwner, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6City", colCity, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6Value", colValue, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§610x", colCompensation, headerY, COLOR_PRIMARY);

        renderDivider(graphics, guiLeft + 10, headerY + 11, guiWidth - 20);

        // Chunk list
        int listTop = headerY + 16;
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE, chunks.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            SyncNationPrivateChunksPacket.PrivateChunkEntry chunk = chunks.get(i);
            int y = listTop + (i - scrollOffset) * ROW_HEIGHT;

            // Highlight selected row
            boolean isSelected = (i == selectedIndex);
            boolean hovered = mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10
                && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 2;

            if (isSelected) {
                graphics.fill(guiLeft + 10, y - 2, guiLeft + guiWidth - 10, y + ROW_HEIGHT - 3, 0x404A90D9);
            } else if (hovered) {
                graphics.fill(guiLeft + 10, y - 2, guiLeft + guiWidth - 10, y + ROW_HEIGHT - 3, 0x20FFFFFF);
            }

            // Coordinates
            String coordStr = "(" + chunk.chunkX + ", " + chunk.chunkZ + ")";
            graphics.drawString(this.font, (isSelected ? "§b" : "§f") + coordStr, colCoords, y, COLOR_TEXT);

            // Owner name
            String ownerLabel = chunk.ownerName;
            if (ownerLabel.length() > 10) {
                ownerLabel = ownerLabel.substring(0, 9) + "…";
            }
            graphics.drawString(this.font, "§e" + ownerLabel, colOwner, y, COLOR_TEXT);

            // City
            String cityLabel = chunk.cityName;
            if (cityLabel.length() > 10) {
                cityLabel = cityLabel.substring(0, 9) + "…";
            }
            graphics.drawString(this.font, "§7" + cityLabel, colCity, y, COLOR_TEXT);

            // Valuation
            graphics.drawString(this.font, "§f" + chunk.formattedValuation, colValue, y, COLOR_TEXT);

            // 10x compensation
            String compensationStr = String.format("$%.0f", chunk.valuation * 10);
            graphics.drawString(this.font, "§c" + compensationStr, colCompensation, y, COLOR_TEXT);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ scroll up", this.width / 2, listTop - 8, 0xFF888888);
        }
        if (endIndex < chunks.size()) {
            int bottomY = listTop + MAX_VISIBLE * ROW_HEIGHT;
            graphics.drawCenteredString(this.font, "§7▼ " + (chunks.size() - endIndex) + " more", this.width / 2, bottomY, 0xFF888888);
        }

        // Footer: selection info
        int footerY = guiTop + guiHeight - 48;
        renderDivider(graphics, guiLeft + 10, footerY - 3, guiWidth - 20);
        if (selectedIndex >= 0 && selectedIndex < chunks.size()) {
            SyncNationPrivateChunksPacket.PrivateChunkEntry selected = chunks.get(selectedIndex);
            String info = "§7Selected: §f(" + selected.chunkX + ", " + selected.chunkZ + ") §7owned by §e" + selected.ownerName
                + " §7| Compensation: §c$" + String.format("%.0f", selected.valuation * 10);
            graphics.drawCenteredString(this.font, info, this.width / 2, footerY + 2, 0xFFCCCCCC);
        } else {
            graphics.drawCenteredString(this.font, "§8Click a chunk to select it for eminent domain", this.width / 2, footerY + 2, 0xFF888888);
        }

        // Total count
        graphics.drawString(this.font, "§8" + chunks.size() + " private chunks", guiLeft + 12, guiTop + guiHeight - 28, 0xFF666666);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded && !chunks.isEmpty()) {
            int headerY = guiTop + 28;
            int listTop = headerY + 16;
            int endIndex = Math.min(scrollOffset + MAX_VISIBLE, chunks.size());

            for (int i = scrollOffset; i < endIndex; i++) {
                int y = listTop + (i - scrollOffset) * ROW_HEIGHT;
                if (mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10
                    && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 2) {
                    selectedIndex = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + MAX_VISIBLE < chunks.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < chunks.size()) {
            SyncNationPrivateChunksPacket.PrivateChunkEntry selected = chunks.get(selectedIndex);
            String value = selected.chunkX + "," + selected.chunkZ + "," + selected.dimension;
            onChunkSelected.accept(value);
        }
    }

    private void cancel() {
        onCancel.run();
    }

    @Override
    public void onClose() {
        cancel();
    }

    /**
     * Called by network handler when private chunk data is received
     */
    public void updateChunks(List<SyncNationPrivateChunksPacket.PrivateChunkEntry> chunks) {
        this.chunks = new ArrayList<>(chunks);
        this.dataLoaded = true;
        this.selectedIndex = -1;
        this.scrollOffset = 0;
    }
}

