package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestCityChunksPacket;
import com.statecraft.network.packets.SyncCityChunksPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen listing all chunks in a city, with the ability to click on any chunk
 * to open its detailed ChunkInfoScreen.
 */
public class CityChunksScreen extends StateCraftScreen {

    private final String nationName;
    private final String stateName;
    private final String cityName;

    // Data from server
    private boolean dataLoaded = false;
    private List<SyncCityChunksPacket.ChunkEntry> chunks = new ArrayList<>();

    // Scrolling
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 7;
    private static final int ROW_HEIGHT = 18;

    public CityChunksScreen(String nationName, String stateName, String cityName) {
        super(Component.literal("Chunks: " + cityName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.guiWidth = 310;
        this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        // Request chunk data from server
        NetworkHandler.sendToServer(new RequestCityChunksPacket(nationName, stateName, cityName));

        int buttonY = guiTop + guiHeight - 28;

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 50, buttonY, 42, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (chunks.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No chunks claimed in this city", this.width / 2, guiTop + 80, 0xFFAAAAAA);
            return;
        }

        // Column headers
        int headerY = guiTop + 28;
        int colCoords = guiLeft + 18;
        int colOwner = guiLeft + 105;
        int colStatus = guiLeft + 205;

        graphics.drawString(this.font, "§6Coordinates", colCoords, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6Owner", colOwner, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6Status", colStatus, headerY, COLOR_PRIMARY);

        renderDivider(graphics, guiLeft + 10, headerY + 11, guiWidth - 20);

        // List area
        int listTop = headerY + 16;
        int listAreaHeight = MAX_VISIBLE * ROW_HEIGHT;
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE, chunks.size());

        // Render list background
        renderSubPanel(graphics, guiLeft + 8, listTop - 3, guiWidth - 16, listAreaHeight + 4);

        for (int i = scrollOffset; i < endIndex; i++) {
            SyncCityChunksPacket.ChunkEntry chunk = chunks.get(i);
            int rowIndex = i - scrollOffset;
            int y = listTop + rowIndex * ROW_HEIGHT;

            // Alternating row background
            if (rowIndex % 2 == 0) {
                graphics.fill(guiLeft + 9, y - 2, guiLeft + guiWidth - 9, y + ROW_HEIGHT - 4, 0x15FFFFFF);
            }

            // Highlight row on hover
            boolean hovered = mouseX >= guiLeft + 9 && mouseX < guiLeft + guiWidth - 9
                && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4;

            if (hovered) {
                graphics.fill(guiLeft + 9, y - 2, guiLeft + guiWidth - 9, y + ROW_HEIGHT - 4, 0x30FFFFFF);
            }

            // Coordinates
            String coordStr = "(" + chunk.x + ", " + chunk.z + ")";
            graphics.drawString(this.font, "§f" + coordStr, colCoords, y, COLOR_TEXT);

            // Owner
            String ownerColor = chunk.ownershipType.equals("PLAYER") ? "§b" :
                                chunk.ownershipType.equals("COMPANY") ? "§d" : "§7";
            String ownerLabel = chunk.ownerName;
            if (ownerLabel.length() > 12) {
                ownerLabel = ownerLabel.substring(0, 11) + "…";
            }
            graphics.drawString(this.font, ownerColor + ownerLabel, colOwner, y, COLOR_TEXT);

            // Status
            if (chunk.forSale) {
                String priceStr = String.format("$%.0f", chunk.salePrice);
                graphics.drawString(this.font, "§a" + priceStr, colStatus, y, COLOR_SECONDARY);
            } else {
                String typeLabel = chunk.ownershipType.equals("PLAYER") ? "Private" :
                                   chunk.ownershipType.equals("COMPANY") ? "Corporate" : "Public";
                graphics.drawString(this.font, "§7" + typeLabel, colStatus, y, COLOR_TEXT);
            }

            // Click hint arrow
            if (hovered) {
                graphics.drawString(this.font, "§e→", guiLeft + guiWidth - 22, y, COLOR_TEXT);
            }
        }

        // Scrollbar track (right edge of list area)
        if (chunks.size() > MAX_VISIBLE) {
            int scrollbarX = guiLeft + guiWidth - 13;
            int scrollbarTrackTop = listTop - 2;
            int scrollbarTrackHeight = listAreaHeight;

            // Track background
            graphics.fill(scrollbarX, scrollbarTrackTop, scrollbarX + 4, scrollbarTrackTop + scrollbarTrackHeight, 0x40FFFFFF);

            // Thumb
            float thumbRatio = (float) MAX_VISIBLE / chunks.size();
            int thumbHeight = Math.max(8, (int) (scrollbarTrackHeight * thumbRatio));
            float scrollRatio = (float) scrollOffset / (chunks.size() - MAX_VISIBLE);
            int thumbY = scrollbarTrackTop + (int) ((scrollbarTrackHeight - thumbHeight) * scrollRatio);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xAA4A90D9);
        }

        // Footer area - below the list with clear separation
        int footerY = listTop + listAreaHeight + 8;
        renderDivider(graphics, guiLeft + 10, footerY, guiWidth - 20);
        graphics.drawString(this.font, "§7Total: §f" + chunks.size() + " chunks", guiLeft + 15, footerY + 6, 0xFFAAAAAA);
        graphics.drawString(this.font, "§8Click a row to view details", guiLeft + guiWidth / 2, footerY + 6, 0xFF666666);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded && !chunks.isEmpty()) {
            int headerY = guiTop + 28;
            int listTop = headerY + 16;
            int endIndex = Math.min(scrollOffset + MAX_VISIBLE, chunks.size());

            for (int i = scrollOffset; i < endIndex; i++) {
                int y = listTop + (i - scrollOffset) * ROW_HEIGHT;
                if (mouseX >= guiLeft + 9 && mouseX < guiLeft + guiWidth - 9
                    && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4) {
                    SyncCityChunksPacket.ChunkEntry chunk = chunks.get(i);
                    openChunkInfo(chunk.x, chunk.z);
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

    private void openChunkInfo(int chunkX, int chunkZ) {
        this.minecraft.setScreen(new ChunkInfoScreen(chunkX, chunkZ));
    }

    private void goBack() {
        this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, cityName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler when chunk data is received
     */
    public void updateChunks(List<SyncCityChunksPacket.ChunkEntry> chunks) {
        this.chunks = new ArrayList<>(chunks);
        this.dataLoaded = true;
        this.scrollOffset = Math.min(scrollOffset, Math.max(0, chunks.size() - MAX_VISIBLE));
    }
}

