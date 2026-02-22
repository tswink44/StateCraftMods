package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Screen for composing peace treaty terms: currency demand and chunk selection.
 * Opened when leader clicks "Peace" in DiplomacyScreen or "Counter" on an inbound proposal.
 */
public class PeaceTermsScreen extends StateCraftScreen {

    private final String nationName;
    private final String targetNationName;
    @Nullable private final String counterProposalId;

    // Data from server
    private boolean dataLoaded = false;
    private List<SyncTargetNationChunksPacket.NationChunkEntry> targetChunks = new ArrayList<>();
    private List<SyncTargetNationChunksPacket.CityEntry> proposerCities = new ArrayList<>();
    private double targetBalance = 0;

    // UI state
    private EditBox currencyInput;
    private final Set<Integer> selectedChunkIndices = new HashSet<>();
    private int selectedCityIndex = 0;
    private int chunkScrollOffset = 0;
    private static final int MAX_VISIBLE_CHUNKS = 6;
    private static final int ROW_HEIGHT = 14;

    private String errorMessage = "";
    private long errorMessageTime = 0;

    public PeaceTermsScreen(String nationName, String targetNationName, @Nullable String counterProposalId) {
        super(Component.literal("Peace Terms"));
        this.nationName = nationName;
        this.targetNationName = targetNationName;
        this.counterProposalId = counterProposalId;
        this.guiWidth = 340;
        this.guiHeight = 240;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        // Clamp height to fit on screen
        if (this.guiHeight > this.height - 10) {
            this.guiHeight = this.height - 10;
            this.guiTop = 5;
        }

        // Request target nation chunks from server
        NetworkHandler.sendToServer(new RequestTargetNationChunksPacket(targetNationName));

        rebuildUI();
    }

    /**
     * Called when the server sends target nation chunk data.
     */
    public void updateChunkData(SyncTargetNationChunksPacket packet) {
        this.targetChunks = packet.getChunks();
        this.proposerCities = packet.getProposerCities();
        this.targetBalance = packet.getTargetBalance();
        this.dataLoaded = true;
        rebuildUI();
    }

    private void rebuildUI() {
        this.clearWidgets();

        int inputY = guiTop + 38;

        // Currency input
        currencyInput = new EditBox(this.font, guiLeft + 100, inputY, 80, 14, Component.literal("Currency"));
        currencyInput.setMaxLength(12);
        currencyInput.setHint(Component.literal("0"));
        this.addRenderableWidget(currencyInput);

        // Receiving city selector buttons
        if (!proposerCities.isEmpty()) {
            int cityY = guiTop + 58;
            this.addRenderableWidget(createButton(guiLeft + 100, cityY, 14, 14,
                Component.literal("◀"), btn -> {
                    if (selectedCityIndex > 0) selectedCityIndex--;
                    rebuildUI();
                }));

            this.addRenderableWidget(createButton(guiLeft + 230, cityY, 14, 14,
                Component.literal("▶"), btn -> {
                    if (selectedCityIndex < proposerCities.size() - 1) selectedCityIndex++;
                    rebuildUI();
                }));
        }

        // Bottom buttons
        int btnY = guiTop + guiHeight - 24;
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 95, btnY, 80, 18,
            Component.literal("§aSend Proposal"), btn -> sendProposal()));

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 15, btnY, 80, 18,
            Component.literal("Cancel"), btn -> goBack()));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = guiLeft + 10;
        int endX = guiLeft + guiWidth - 10;

        // Title
        graphics.fill(startX, guiTop + 6, endX, guiTop + 20, 0xAA808080);
        String titlePrefix = counterProposalId != null ? "Counter-Proposal" : "Peace Terms";
        graphics.drawString(this.font, "☮ " + titlePrefix + " → " + targetNationName,
            startX + 4, guiTop + 9, 0xFFFFFFFF);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", guiLeft + guiWidth / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        // Currency section
        int y = guiTop + 28;
        graphics.drawString(this.font, "§6Currency Demand:", startX, y, COLOR_TEXT);
        y += 12;
        graphics.drawString(this.font, "§7Amount: $", startX, y + 3, COLOR_TEXT);

        // Show max allowed
        String maxInfo = String.format("§8(Target treasury: $%.0f)", targetBalance);
        graphics.drawString(this.font, maxInfo, guiLeft + 190, y + 3, COLOR_TEXT);

        // Receiving city section
        y = guiTop + 58;
        graphics.drawString(this.font, "§6Receive in:", startX, y + 3, COLOR_TEXT);
        if (!proposerCities.isEmpty() && selectedCityIndex < proposerCities.size()) {
            String cityName = proposerCities.get(selectedCityIndex).cityName;
            int cityNameX = guiLeft + 118;
            int maxCityWidth = 108;
            if (this.font.width(cityName) > maxCityWidth) {
                cityName = this.font.plainSubstrByWidth(cityName, maxCityWidth - 6) + "…";
            }
            graphics.drawCenteredString(this.font, "§f" + cityName,
                guiLeft + 172, y + 3, 0xFFFFFFFF);
        } else {
            graphics.drawString(this.font, "§cNo cities", guiLeft + 118, y + 3, COLOR_TEXT);
        }

        // Chunk selection section
        y = guiTop + 78;
        renderDivider(graphics, startX, y - 2, guiWidth - 20);
        graphics.drawString(this.font, "§6Chunk Demands:", startX, y, COLOR_TEXT);

        // Show caps
        String capsInfo = String.format("§8(Selected: %d | Score: %d)", selectedChunkIndices.size(), getTotalSelectedScore());
        int capsWidth = this.font.width(capsInfo.replaceAll("§.", ""));
        graphics.drawString(this.font, capsInfo, endX - capsWidth, y, COLOR_TEXT);

        y += 14;

        // Column headers
        graphics.drawString(this.font, "§7Chunk", startX + 4, y, COLOR_TEXT);
        graphics.drawString(this.font, "§7City", startX + 80, y, COLOR_TEXT);
        graphics.drawString(this.font, "§7Score", startX + 180, y, COLOR_TEXT);
        graphics.drawString(this.font, "§7Value", startX + 230, y, COLOR_TEXT);
        y += 12;

        // Chunk list
        if (targetChunks.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No chunks available",
                guiLeft + guiWidth / 2, y + 10, 0xFF888888);
        } else {
            int endIndex = Math.min(chunkScrollOffset + MAX_VISIBLE_CHUNKS, targetChunks.size());
            for (int i = chunkScrollOffset; i < endIndex; i++) {
                SyncTargetNationChunksPacket.NationChunkEntry chunk = targetChunks.get(i);
                int rowY = y + (i - chunkScrollOffset) * ROW_HEIGHT;

                boolean selected = selectedChunkIndices.contains(i);
                boolean hovered = mouseX >= startX && mouseX < endX
                    && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 2;

                // Background
                if (selected) {
                    graphics.fill(startX, rowY - 1, endX, rowY + ROW_HEIGHT - 2, 0x404A90D9);
                } else if (hovered) {
                    graphics.fill(startX, rowY - 1, endX, rowY + ROW_HEIGHT - 2, 0x20FFFFFF);
                }

                // Checkbox
                String check = selected ? "§a☑" : "§7☐";
                graphics.drawString(this.font, check, startX + 1, rowY, COLOR_TEXT);

                // Coordinates
                String coordStr = "(" + chunk.chunkX + ", " + chunk.chunkZ + ")";
                graphics.drawString(this.font, (selected ? "§b" : "§f") + coordStr, startX + 14, rowY, COLOR_TEXT);

                // City name
                String cityName = chunk.cityName;
                if (cityName.length() > 12) cityName = cityName.substring(0, 11) + "…";
                graphics.drawString(this.font, "§7" + cityName, startX + 80, rowY, COLOR_TEXT);

                // Improvement score
                graphics.drawString(this.font, "§e" + chunk.improvementScore, startX + 180, rowY, COLOR_TEXT);

                // Value
                graphics.drawString(this.font, String.format("§f$%.0f", chunk.totalValue), startX + 230, rowY, COLOR_TEXT);
            }

            // Scroll indicators
            if (chunkScrollOffset > 0) {
                graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth / 2, y - 8, 0xFF888888);
            }
            if (endIndex < targetChunks.size()) {
                int bottomY = y + MAX_VISIBLE_CHUNKS * ROW_HEIGHT;
                graphics.drawCenteredString(this.font, "§7▼ " + (targetChunks.size() - endIndex) + " more",
                    guiLeft + guiWidth / 2, bottomY, 0xFF888888);
            }
        }

        // Error message
        if (!errorMessage.isEmpty() && System.currentTimeMillis() - errorMessageTime < 4000) {
            graphics.drawCenteredString(this.font, errorMessage,
                guiLeft + guiWidth / 2, guiTop + guiHeight - 40, 0xFFFF4444);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded && !targetChunks.isEmpty()) {
            int startX = guiLeft + 10;
            int endX = guiLeft + guiWidth - 10;
            int listY = guiTop + 78 + 14 + 12;

            int endIndex = Math.min(chunkScrollOffset + MAX_VISIBLE_CHUNKS, targetChunks.size());
            for (int i = chunkScrollOffset; i < endIndex; i++) {
                int rowY = listY + (i - chunkScrollOffset) * ROW_HEIGHT;
                if (mouseX >= startX && mouseX < endX
                    && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 2) {
                    if (selectedChunkIndices.contains(i)) {
                        selectedChunkIndices.remove(i);
                    } else {
                        selectedChunkIndices.add(i);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && chunkScrollOffset > 0) {
            chunkScrollOffset--;
            return true;
        } else if (delta < 0 && chunkScrollOffset + MAX_VISIBLE_CHUNKS < targetChunks.size()) {
            chunkScrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getTotalSelectedScore() {
        int total = 0;
        for (int idx : selectedChunkIndices) {
            if (idx < targetChunks.size()) {
                total += targetChunks.get(idx).improvementScore;
            }
        }
        return total;
    }

    private void sendProposal() {
        // Parse currency
        double currencyDemand = 0;
        String currencyStr = currencyInput.getValue().trim();
        if (!currencyStr.isEmpty()) {
            try {
                currencyDemand = Double.parseDouble(currencyStr);
                if (currencyDemand < 0) {
                    setError("§cCurrency demand cannot be negative.");
                    return;
                }
            } catch (NumberFormatException e) {
                setError("§cInvalid currency amount.");
                return;
            }
        }

        // Build chunk demands
        List<PeaceTermsProposalPacket.ChunkDemandData> chunkDemands = new ArrayList<>();
        for (int idx : selectedChunkIndices) {
            if (idx < targetChunks.size()) {
                SyncTargetNationChunksPacket.NationChunkEntry chunk = targetChunks.get(idx);
                chunkDemands.add(new PeaceTermsProposalPacket.ChunkDemandData(
                    chunk.chunkX, chunk.chunkZ, chunk.dimension));
            }
        }

        // Validate receiving city if chunks are selected
        String receivingCityId = "";
        if (!chunkDemands.isEmpty()) {
            if (proposerCities.isEmpty()) {
                setError("§cNo cities available to receive chunks.");
                return;
            }
            receivingCityId = proposerCities.get(selectedCityIndex).cityId;
        }

        // Check at least some terms or allow zero-terms peace
        // (zero-terms is valid — just a simple peace proposal)

        NetworkHandler.sendToServer(new PeaceTermsProposalPacket(
            nationName, targetNationName, currencyDemand, chunkDemands,
            receivingCityId, counterProposalId != null ? counterProposalId : ""));

        goBack();
    }

    private void setError(String msg) {
        this.errorMessage = msg;
        this.errorMessageTime = System.currentTimeMillis();
    }

    private void goBack() {
        this.minecraft.setScreen(new DiplomacyScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

