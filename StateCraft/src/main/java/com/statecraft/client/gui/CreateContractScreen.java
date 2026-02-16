package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.CreateContractPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen for creating a new government contract
 * Allows setting title, description, budget, bond, and selecting chunks
 */
public class CreateContractScreen extends StateCraftScreen {

    private final String nationName;

    // Input fields
    private EditBox titleField;
    private EditBox descriptionField;
    private EditBox requirementsField;
    private EditBox budgetField;
    private EditBox bondField;

    // Contract settings
    private String compensationType = "MILESTONE";
    private final Set<ChunkPos> selectedChunks = new HashSet<>();
    private String dimension = "minecraft:overworld";

    // UI state
    private Button compTypeButton;
    private String errorMessage = "";

    // Step tracking
    private int currentStep = 1;
    private static final int TOTAL_STEPS = 2;

    public CreateContractScreen(String nationName) {
        super(Component.literal("Create Contract"));
        this.nationName = nationName;
        this.guiWidth = 320;
        this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        if (currentStep == 1) {
            initStep1();
        } else {
            initStep2();
        }
    }

    private void initStep1() {
        int fieldWidth = guiWidth - 40;
        int x = guiLeft + 20;
        int y = guiTop + 35;

        // Title field
        titleField = new EditBox(this.font, x, y, fieldWidth, 18, Component.literal("Title"));
        titleField.setMaxLength(100);
        titleField.setHint(Component.literal("Contract title..."));
        this.addRenderableWidget(titleField);
        y += 28;

        // Description field
        descriptionField = new EditBox(this.font, x, y, fieldWidth, 18, Component.literal("Description"));
        descriptionField.setMaxLength(256);
        descriptionField.setHint(Component.literal("Brief description..."));
        this.addRenderableWidget(descriptionField);
        y += 28;

        // Requirements field
        requirementsField = new EditBox(this.font, x, y, fieldWidth, 18, Component.literal("Requirements"));
        requirementsField.setMaxLength(512);
        requirementsField.setHint(Component.literal("Build requirements..."));
        this.addRenderableWidget(requirementsField);
        y += 34;

        // Budget field
        budgetField = new EditBox(this.font, x, y, 100, 18, Component.literal("Budget"));
        budgetField.setMaxLength(12);
        budgetField.setHint(Component.literal("0.00"));
        budgetField.setFilter(this::isValidNumberInput);
        this.addRenderableWidget(budgetField);

        // Bond field (optional)
        bondField = new EditBox(this.font, x + 160, y, 100, 18, Component.literal("Bond"));
        bondField.setMaxLength(12);
        bondField.setHint(Component.literal("0 (optional)"));
        bondField.setFilter(this::isValidNumberInput);
        this.addRenderableWidget(bondField);
        y += 30;

        // Compensation type selector
        compTypeButton = this.addRenderableWidget(createButton(
            x, y, 140, 18,
            Component.literal("Type: " + getCompTypeDisplayName()),
            btn -> cycleCompensationType()
        ));
        y += 28;

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 100, buttonY, 85, 20,
            Component.literal("Next §7→"),
            btn -> goToStep2()
        ));

        this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 60, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    private void initStep2() {
        int x = guiLeft + 20;
        int y = guiTop + 35;

        // Chunk selection info
        // Player's current chunk is pre-selected
        if (selectedChunks.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            ChunkPos playerChunk = new ChunkPos(this.minecraft.player.blockPosition());
            selectedChunks.add(playerChunk);
            dimension = this.minecraft.player.level().dimension().location().toString();
        }

        // Main button to open chunk map selector
        this.addRenderableWidget(createButton(
            x, y + 40, guiWidth - 40, 22,
            Component.literal("§6Open Chunk Selection Map"),
            btn -> openChunkMap()
        ));

        // Quick action buttons
        this.addRenderableWidget(createButton(
            x, y + 70, 130, 18,
            Component.literal("§a+ Add Current Chunk"),
            btn -> addCurrentChunk()
        ));

        this.addRenderableWidget(createButton(
            x + 140, y + 70, 130, 18,
            Component.literal("§c- Clear Selection"),
            btn -> clearChunks()
        ));

        this.addRenderableWidget(createButton(
            x, y + 95, 130, 18,
            Component.literal("Quick: 3x3 Area"),
            btn -> select3x3Area()
        ));

        this.addRenderableWidget(createButton(
            x + 140, y + 95, 130, 18,
            Component.literal("Quick: 5x5 Area"),
            btn -> select5x5Area()
        ));

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 60, 20,
            Component.literal("§7← Back"),
            btn -> goToStep1()
        ));

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 100, buttonY, 85, 20,
            Component.literal("§aCreate"),
            btn -> createContract()
        ));
    }

    private void openChunkMap() {
        // Open the chunk selection map screen
        this.minecraft.setScreen(new ContractChunkSelectScreen(
            selectedChunks,
            dimension,
            newSelection -> {
                // On confirm - update selection and return to this screen
                selectedChunks.clear();
                selectedChunks.addAll(newSelection);
                this.minecraft.setScreen(this);
            },
            () -> {
                // On cancel - just return to this screen
                this.minecraft.setScreen(this);
            }
        ));
    }

    private boolean isValidNumberInput(String s) {
        if (s.isEmpty()) return true;
        try {
            if (s.equals(".") || s.endsWith(".")) {
                // Allow typing decimal point
                String test = s.equals(".") ? "0" : s.substring(0, s.length() - 1);
                Double.parseDouble(test);
                return true;
            }
            double val = Double.parseDouble(s);
            return val >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void cycleCompensationType() {
        switch (compensationType) {
            case "FIXED" -> compensationType = "MILESTONE";
            case "MILESTONE" -> compensationType = "VALUATION_BASED";
            case "VALUATION_BASED" -> compensationType = "FIXED";
        }
        compTypeButton.setMessage(Component.literal("Type: " + getCompTypeDisplayName()));
    }

    private String getCompTypeDisplayName() {
        return switch (compensationType) {
            case "FIXED" -> "Fixed Payment";
            case "MILESTONE" -> "Milestone Payments";
            case "VALUATION_BASED" -> "Valuation Based";
            default -> compensationType;
        };
    }

    private void addCurrentChunk() {
        if (this.minecraft != null && this.minecraft.player != null) {
            ChunkPos chunk = new ChunkPos(this.minecraft.player.blockPosition());
            selectedChunks.add(chunk);
        }
    }

    private void select3x3Area() {
        if (this.minecraft != null && this.minecraft.player != null) {
            ChunkPos center = new ChunkPos(this.minecraft.player.blockPosition());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    selectedChunks.add(new ChunkPos(center.x + dx, center.z + dz));
                }
            }
        }
    }

    private void select5x5Area() {
        if (this.minecraft != null && this.minecraft.player != null) {
            ChunkPos center = new ChunkPos(this.minecraft.player.blockPosition());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    selectedChunks.add(new ChunkPos(center.x + dx, center.z + dz));
                }
            }
        }
    }

    private void clearChunks() {
        selectedChunks.clear();
    }

    private void goToStep1() {
        currentStep = 1;
        rebuildWidgets();
    }

    private void goToStep2() {
        // Validate step 1
        String title = titleField.getValue().trim();
        if (title.isEmpty()) {
            errorMessage = "Title is required";
            return;
        }

        double budget;
        try {
            budget = Double.parseDouble(budgetField.getValue().isEmpty() ? "0" : budgetField.getValue());
            if (budget <= 0) {
                errorMessage = "Budget must be greater than 0";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid budget amount";
            return;
        }

        errorMessage = "";
        currentStep = 2;
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        super.rebuildWidgets();
    }

    private void createContract() {
        // Validate
        if (selectedChunks.isEmpty()) {
            errorMessage = "Select at least one chunk";
            return;
        }

        String title = titleField != null ? titleField.getValue().trim() : "";
        String description = descriptionField != null ? descriptionField.getValue().trim() : "";
        String requirements = requirementsField != null ? requirementsField.getValue().trim() : "";

        double budget;
        double bond;
        try {
            budget = Double.parseDouble(budgetField != null && !budgetField.getValue().isEmpty()
                ? budgetField.getValue() : "0");
            bond = Double.parseDouble(bondField != null && !bondField.getValue().isEmpty()
                ? bondField.getValue() : "0");
        } catch (NumberFormatException e) {
            errorMessage = "Invalid number format";
            return;
        }

        // Build chunk list
        List<CreateContractPacket.ChunkData> chunks = new ArrayList<>();
        for (ChunkPos pos : selectedChunks) {
            chunks.add(new CreateContractPacket.ChunkData(pos.x, pos.z));
        }

        // Send packet
        NetworkHandler.sendToServer(new CreateContractPacket(
            nationName, title, description, requirements,
            budget, bond, compensationType, chunks, dimension
        ));

        // Go back to contracts list
        goBack();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Step indicator
        String stepText = "Step " + currentStep + "/" + TOTAL_STEPS;
        graphics.drawString(this.font, "§7" + stepText, guiLeft + guiWidth - font.width(stepText) - 15, guiTop + 8, COLOR_TEXT);

        if (currentStep == 1) {
            renderStep1Content(graphics);
        } else {
            renderStep2Content(graphics);
        }

        // Error message
        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + guiHeight - 50, COLOR_WARNING);
        }
    }

    private void renderStep1Content(GuiGraphics graphics) {
        int x = guiLeft + 20;
        int y = guiTop + 28;

        // Field labels
        graphics.drawString(this.font, "§7Title:", x, y, 0xFFAAAAAA);
        y += 28;
        graphics.drawString(this.font, "§7Description:", x, y, 0xFFAAAAAA);
        y += 28;
        graphics.drawString(this.font, "§7Requirements:", x, y, 0xFFAAAAAA);
        y += 34;
        graphics.drawString(this.font, "§7Budget ($):", x, y, 0xFFAAAAAA);
        graphics.drawString(this.font, "§7Bond ($):", x + 160, y, 0xFFAAAAAA);
        y += 30;
        graphics.drawString(this.font, "§7Payment:", x, y, 0xFFAAAAAA);

        // Compensation type description
        y += 22;
        String compDesc = switch (compensationType) {
            case "FIXED" -> "§8Full payment on completion";
            case "MILESTONE" -> "§8Payments at 25%, 50%, 75%, 100%";
            case "VALUATION_BASED" -> "§8Based on chunk value increase";
            default -> "";
        };
        graphics.drawString(this.font, compDesc, x, y, 0xFF888888);
    }

    private void renderStep2Content(GuiGraphics graphics) {
        int x = guiLeft + 20;
        int y = guiTop + 35;

        graphics.drawString(this.font, "§6Select Project Chunks", x, y, COLOR_PRIMARY);
        y += 16;

        // Selected chunks info
        graphics.drawString(this.font, "§7Selected: §f" + selectedChunks.size() + " chunks", x, y, COLOR_TEXT);

        // Dimension
        String dimDisplay = dimension.replace("minecraft:", "");
        graphics.drawString(this.font, "§7Dimension: §f" + dimDisplay, x + 140, y, COLOR_TEXT);
        y += 20;

        // Instructions for map button
        graphics.drawString(this.font, "§7Use the map to visually select chunks:", x, y, 0xFFAAAAAA);

        // List some selected chunks
        y = guiTop + 125;
        graphics.drawString(this.font, "§7Chunks included:", x, y, 0xFFAAAAAA);
        y += 12;

        int count = 0;
        int col = 0;
        int startX = x;
        for (ChunkPos chunk : selectedChunks) {
            if (count >= 15) {
                graphics.drawString(this.font, "§8... +" + (selectedChunks.size() - 15) + " more", startX, y, 0xFF666666);
                break;
            }

            String chunkText = "(" + chunk.x + "," + chunk.z + ")";
            graphics.drawString(this.font, "§8" + chunkText, startX + col * 90, y, 0xFF666666);
            count++;
            col++;
            if (col >= 3) {
                col = 0;
                y += 11;
            }
        }

        // Current position
        if (this.minecraft != null && this.minecraft.player != null) {
            ChunkPos playerChunk = new ChunkPos(this.minecraft.player.blockPosition());
            y = guiTop + guiHeight - 70;
            graphics.drawString(this.font, "§7Your position: §fChunk (" + playerChunk.x + ", " + playerChunk.z + ")", x, y, 0xFF888888);
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new ContractsMainScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}



