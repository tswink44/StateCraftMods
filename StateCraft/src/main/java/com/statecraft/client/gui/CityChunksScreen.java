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
    private static final int MAX_VISIBLE = 8;
    private static final int ROW_HEIGHT = 18;

    public CityChunksScreen(String nationName, String stateName, String cityName) {
        super(Component.literal("Chunks: " + cityName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.guiWidth = 300;
        this.guiHeight = 220;
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
        int colCoords = guiLeft + 15;
        int colOwner = guiLeft + 95;
        int colStatus = guiLeft + 195;

        graphics.drawString(this.font, "§6Coordinates", colCoords, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6Owner", colOwner, headerY, COLOR_PRIMARY);
        graphics.drawString(this.font, "§6Status", colStatus, headerY, COLOR_PRIMARY);

        renderDivider(graphics, guiLeft + 10, headerY + 11, guiWidth - 20);

        // Chunk list
        int listTop = headerY + 16;
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE, chunks.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            SyncCityChunksPacket.ChunkEntry chunk = chunks.get(i);
            int y = listTop + (i - scrollOffset) * ROW_HEIGHT;

            // Highlight row on hover
            boolean hovered = mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10
                && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 3;

            if (hovered) {
                graphics.fill(guiLeft + 10, y - 2, guiLeft + guiWidth - 10, y + ROW_HEIGHT - 3, 0x30FFFFFF);
            }

            // Coordinates
            String coordStr = "(" + chunk.x + ", " + chunk.z + ")";
            graphics.drawString(this.font, "§f" + coordStr, colCoords, y, COLOR_TEXT);

            // Owner
            String ownerColor = chunk.ownershipType.equals("PLAYER") ? "§b" : "§7";
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
                String typeLabel = chunk.ownershipType.equals("PLAYER") ? "Private" : "Public";
                graphics.drawString(this.font, "§7" + typeLabel, colStatus, y, COLOR_TEXT);
            }

            // Click hint icon
            if (hovered) {
                graphics.drawString(this.font, "§e→", guiLeft + guiWidth - 20, y, COLOR_TEXT);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ scroll up", this.width / 2, listTop - 10, 0xFF888888);
        }
        if (endIndex < chunks.size()) {
            int bottomY = listTop + MAX_VISIBLE * ROW_HEIGHT;
            graphics.drawCenteredString(this.font, "§7▼ " + (chunks.size() - endIndex) + " more", this.width / 2, bottomY, 0xFF888888);
        }

        // Footer info
        int footerY = guiTop + guiHeight - 45;
        renderDivider(graphics, guiLeft + 10, footerY - 3, guiWidth - 20);
        graphics.drawCenteredString(this.font, "§7Total chunks: §f" + chunks.size() + "  §8| Click a chunk to view details",
            this.width / 2, footerY + 2, 0xFF888888);
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
                    && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 3) {
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

