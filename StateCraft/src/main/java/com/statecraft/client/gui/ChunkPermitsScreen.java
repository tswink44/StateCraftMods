package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.ModifyChunkPermitPacket;
import com.statecraft.network.packets.RequestChunkPermitsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Screen for managing chunk building permits
 * Allows owners (private or government) to grant/revoke building privileges
 */
public class ChunkPermitsScreen extends StateCraftScreen {

    private final int chunkX;
    private final int chunkZ;

    // Data from server
    private boolean dataLoaded = false;
    private String ownershipType = "";
    private String ownerName = "";
    private boolean canManagePermits = false;
    private Map<UUID, String> permitHolders = new HashMap<>();

    // UI components
    private EditBox playerNameInput;
    private Button grantButton;
    private Button revokeButton;

    // Scrolling for permit list
    private int scrollOffset = 0;
    private static final int VISIBLE_ENTRIES = 5;

    // Selected permit holder for revocation
    private UUID selectedPermitHolder = null;

    public ChunkPermitsScreen(int chunkX, int chunkZ) {
        super(Component.literal("Chunk Permits"));
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.guiWidth = 280; this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        // Request permit data from server
        NetworkHandler.sendToServer(new RequestChunkPermitsPacket(chunkX, chunkZ));

        int centerX = this.width / 2;

        // Player name input for granting permits
        playerNameInput = new EditBox(this.font, guiLeft + 15, guiTop + 140, 150, 20, Component.literal("Player Name"));
        playerNameInput.setMaxLength(16);
        playerNameInput.setHint(Component.literal("Enter player name..."));
        this.addRenderableWidget(playerNameInput);

        // Grant button
        grantButton = this.addRenderableWidget(createButton(
            guiLeft + 170, guiTop + 140,
            95, 20,
            Component.literal("Grant Permit"),
            btn -> grantPermit()
        ));
        grantButton.active = false;

        // Revoke button (for selected permit holder)
        revokeButton = this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + 165,
            120, 20,
            Component.literal("Revoke Selected"),
            btn -> revokeSelectedPermit()
        ));
        revokeButton.active = false;

        // Scroll buttons
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 35, guiTop + 55,
            20, 20,
            Component.literal("▲"),
            btn -> scrollUp()
        ));

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 35, guiTop + 125,
            20, 20,
            Component.literal("▼"),
            btn -> scrollDown()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            centerX - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int y = guiTop + 28;

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        // Chunk info
        graphics.drawString(this.font, "§7Chunk: §f(" + chunkX + ", " + chunkZ + ")", guiLeft + 15, y, COLOR_TEXT);
        y += 12;

        // Ownership info
        String ownershipDisplay = ownershipType.equals("PLAYER") ? "§ePrivate" : "§aGovernment";
        graphics.drawString(this.font, "§7Ownership: " + ownershipDisplay, guiLeft + 15, y, COLOR_TEXT);
        y += 12;

        graphics.drawString(this.font, "§7Owner: §f" + ownerName, guiLeft + 15, y, COLOR_TEXT);
        y += 16;

        // Permits section
        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 5;

        graphics.drawString(this.font, "§6Building Permits", guiLeft + 15, y, COLOR_PRIMARY);
        y += 14;

        // Permit list with selection
        renderSubPanel(graphics, guiLeft + 10, y, guiWidth - 50, VISIBLE_ENTRIES * 14 + 4);

        if (permitHolders.isEmpty()) {
            graphics.drawString(this.font, "§8No permits granted", guiLeft + 15, y + 8, 0xFF888888);
        } else {
            List<Map.Entry<UUID, String>> entries = new ArrayList<>(permitHolders.entrySet());
            int startIndex = Math.min(scrollOffset, Math.max(0, entries.size() - VISIBLE_ENTRIES));
            int endIndex = Math.min(startIndex + VISIBLE_ENTRIES, entries.size());

            int entryY = y + 4;
            for (int i = startIndex; i < endIndex; i++) {
                Map.Entry<UUID, String> entry = entries.get(i);
                boolean isSelected = entry.getKey().equals(selectedPermitHolder);

                // Highlight selected entry
                if (isSelected) {
                    graphics.fill(guiLeft + 12, entryY - 1, guiLeft + guiWidth - 42, entryY + 12, 0x55FFFFFF);
                }

                String prefix = isSelected ? "§e▶ " : "§7• ";
                graphics.drawString(this.font, prefix + "§f" + entry.getValue(), guiLeft + 15, entryY, COLOR_TEXT);
                entryY += 14;
            }

            // Scroll indicator
            if (entries.size() > VISIBLE_ENTRIES) {
                int scrollIndicatorY = y + 4 + (int)((float)startIndex / (entries.size() - VISIBLE_ENTRIES) * (VISIBLE_ENTRIES * 14 - 10));
                graphics.fill(guiLeft + guiWidth - 45, scrollIndicatorY, guiLeft + guiWidth - 42, scrollIndicatorY + 10, 0xAAFFFFFF);
            }
        }

        // Permission status
        y = guiTop + 190;
        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 5;

        if (canManagePermits) {
            graphics.drawString(this.font, "§aYou can manage permits for this chunk", guiLeft + 15, y, 0xFF55FF55);
        } else {
            graphics.drawString(this.font, "§cYou cannot manage permits here", guiLeft + 15, y, 0xFFFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check for clicks on permit list entries
        if (dataLoaded && canManagePermits && !permitHolders.isEmpty()) {
            int listY = guiTop + 28 + 12 + 12 + 16 + 5 + 14 + 4;
            int listX = guiLeft + 12;
            int listWidth = guiWidth - 54;

            if (mouseX >= listX && mouseX <= listX + listWidth) {
                List<Map.Entry<UUID, String>> entries = new ArrayList<>(permitHolders.entrySet());
                int startIndex = Math.min(scrollOffset, Math.max(0, entries.size() - VISIBLE_ENTRIES));

                for (int i = 0; i < VISIBLE_ENTRIES && startIndex + i < entries.size(); i++) {
                    int entryY = listY + i * 14;
                    if (mouseY >= entryY - 1 && mouseY <= entryY + 12) {
                        selectedPermitHolder = entries.get(startIndex + i).getKey();
                        updateButtonStates();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
        }
    }

    private void scrollDown() {
        if (scrollOffset < permitHolders.size() - VISIBLE_ENTRIES) {
            scrollOffset++;
        }
    }

    private void grantPermit() {
        String playerName = playerNameInput.getValue().trim();
        if (!playerName.isEmpty() && canManagePermits) {
            NetworkHandler.sendToServer(new ModifyChunkPermitPacket(
                chunkX, chunkZ, playerName, ModifyChunkPermitPacket.Action.GRANT
            ));
            playerNameInput.setValue("");
            // Refresh data
            NetworkHandler.sendToServer(new RequestChunkPermitsPacket(chunkX, chunkZ));
        }
    }

    private void revokeSelectedPermit() {
        if (selectedPermitHolder != null && canManagePermits) {
            String playerName = permitHolders.get(selectedPermitHolder);
            if (playerName != null) {
                NetworkHandler.sendToServer(new ModifyChunkPermitPacket(
                    chunkX, chunkZ, playerName, ModifyChunkPermitPacket.Action.REVOKE
                ));
                selectedPermitHolder = null;
                // Refresh data
                NetworkHandler.sendToServer(new RequestChunkPermitsPacket(chunkX, chunkZ));
            }
        }
    }

    private void updateButtonStates() {
        grantButton.active = canManagePermits && !playerNameInput.getValue().trim().isEmpty();
        revokeButton.active = canManagePermits && selectedPermitHolder != null;
    }

    @Override
    public void tick() {
        super.tick();
        playerNameInput.tick();
        updateButtonStates();
    }

    private void goBack() {
        this.minecraft.setScreen(new ChunkInfoScreen(chunkX, chunkZ));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String ownershipType, String ownerName, boolean canManagePermits, Map<UUID, String> permitHolders) {
        this.ownershipType = ownershipType;
        this.ownerName = ownerName;
        this.canManagePermits = canManagePermits;
        this.permitHolders = new HashMap<>(permitHolders);
        this.dataLoaded = true;
        this.selectedPermitHolder = null;
        this.scrollOffset = 0;
        updateButtonStates();
    }
}


