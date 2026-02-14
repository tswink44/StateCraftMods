package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestChunkInfoPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screen showing detailed information about the current chunk
 * Shows ownership, permits, and hierarchy (City -> State -> Nation)
 */
public class ChunkInfoScreen extends StateCraftScreen {

    private final int chunkX;
    private final int chunkZ;

    // Data from server
    private boolean dataLoaded = false;
    private String ownershipType = ""; // HIERARCHY, PLAYER, UNCLAIMED
    private String ownerName = "";
    private String nationName = "";
    private String stateName = "";
    private String cityName = "";
    private boolean canManagePermits = false;
    private List<String> permitHolders = new ArrayList<>();

    public ChunkInfoScreen(int chunkX, int chunkZ) {
        super(Component.literal("Chunk Info"));
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.guiWidth = 260; this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        // Request chunk data from server
        NetworkHandler.sendToServer(new RequestChunkInfoPacket(chunkX, chunkZ));

        int centerX = this.width / 2;
        int buttonY = guiTop + guiHeight - 55;

        // Manage Permits button
        Button permitsBtn = this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY,
            100, 20,
            Component.literal("Manage Permits"),
            btn -> openPermitsScreen()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 75, buttonY,
            60, 20,
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

        int y = guiTop + 30;
        int leftCol = guiLeft + 15;

        // Chunk coordinates
        graphics.drawString(this.font, "§6Chunk Coordinates", leftCol, y, COLOR_PRIMARY);
        y += 14;
        graphics.drawString(this.font, "§7X: §f" + chunkX + "  §7Z: §f" + chunkZ, leftCol + 10, y, COLOR_TEXT);
        y += 18;

        // Ownership section
        graphics.drawString(this.font, "§6Ownership", leftCol, y, COLOR_PRIMARY);
        y += 14;

        if (ownershipType.equals("UNCLAIMED")) {
            graphics.drawString(this.font, "§8Wilderness (Unclaimed)", leftCol + 10, y, 0xFF888888);
        } else if (ownershipType.equals("PLAYER")) {
            graphics.drawString(this.font, "§7Type: §ePrivate", leftCol + 10, y, COLOR_TEXT);
            y += 12;
            graphics.drawString(this.font, "§7Owner: §f" + ownerName, leftCol + 10, y, COLOR_TEXT);
        } else {
            graphics.drawString(this.font, "§7Type: §aGovernment", leftCol + 10, y, COLOR_TEXT);
            y += 12;
            if (!cityName.isEmpty()) {
                graphics.drawString(this.font, "§7City: §f" + cityName, leftCol + 10, y, COLOR_TEXT);
            }
        }
        y += 18;

        // Hierarchy section (if in a nation)
        if (!nationName.isEmpty()) {
            renderDivider(graphics, guiLeft + 10, y - 4, guiWidth - 20);
            graphics.drawString(this.font, "§6Territory Hierarchy", leftCol, y, COLOR_PRIMARY);
            y += 14;

            graphics.drawString(this.font, "§7Nation: §e" + nationName, leftCol + 10, y, COLOR_TEXT);
            y += 12;

            if (!stateName.isEmpty()) {
                graphics.drawString(this.font, "§7State: §f" + stateName, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }

            if (!cityName.isEmpty()) {
                graphics.drawString(this.font, "§7City: §a" + cityName, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }
        }

        // Permits section
        y += 6;
        renderDivider(graphics, guiLeft + 10, y - 4, guiWidth - 20);
        graphics.drawString(this.font, "§6Building Permits", leftCol, y, COLOR_PRIMARY);
        y += 14;

        if (permitHolders.isEmpty()) {
            graphics.drawString(this.font, "§8No permits granted", leftCol + 10, y, 0xFF888888);
        } else {
            for (int i = 0; i < Math.min(3, permitHolders.size()); i++) {
                graphics.drawString(this.font, "§7• §f" + permitHolders.get(i), leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }
            if (permitHolders.size() > 3) {
                graphics.drawString(this.font, "§8...and " + (permitHolders.size() - 3) + " more", leftCol + 10, y, 0xFF888888);
            }
        }
    }

    private void openPermitsScreen() {
        this.minecraft.setScreen(new ChunkPermitsScreen(chunkX, chunkZ));
    }

    private void goBack() {
        this.minecraft.setScreen(new ClaimsManagementScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String ownershipType, String ownerName, String nationName,
                           String stateName, String cityName, boolean canManagePermits,
                           List<String> permitHolders) {
        this.ownershipType = ownershipType;
        this.ownerName = ownerName;
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.canManagePermits = canManagePermits;
        this.permitHolders = new ArrayList<>(permitHolders);
        this.dataLoaded = true;
    }
}

