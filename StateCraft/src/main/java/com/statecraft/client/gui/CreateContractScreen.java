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
    private EditBox paymentPerPointField;  // For VALUATION_BASED compensation

    // Milestone description fields (for MILESTONE compensation type)
    private EditBox milestone25Field;
    private EditBox milestone50Field;
    private EditBox milestone75Field;
    private EditBox milestone100Field;

    // Contract settings
    private String compensationType = "MILESTONE";
    private final Set<ChunkPos> selectedChunks = new HashSet<>();
    private String dimension = "minecraft:overworld";

    // UI state
    private Button compTypeButton;
    private String errorMessage = "";

    // Step tracking
    private int currentStep = 1;
    private int totalSteps = 2;  // Dynamic: 2 for non-milestone, 3 for milestone

    public CreateContractScreen(String nationName) {
        super(Component.literal("Create Contract"));
        this.nationName = nationName;
        this.guiWidth = 300;
        this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Update total steps based on compensation type
        totalSteps = compensationType.equals("MILESTONE") ? 3 : 2;

        if (currentStep == 1) {
            initStep1();
        } else if (currentStep == 2) {
            initStep2();
        } else {
            initStep3();  // Milestone customization
        }
    }

    private void initStep1() {
        int fieldWidth = guiWidth - 40;
        int x = guiLeft + 20;
        int y = guiTop + 38;

        // Title field
        titleField = new EditBox(this.font, x, y, fieldWidth, 16, Component.literal("Title"));
        titleField.setMaxLength(100);
        titleField.setHint(Component.literal("Contract title..."));
        this.addRenderableWidget(titleField);
        y += 30;

        // Description field
        descriptionField = new EditBox(this.font, x, y, fieldWidth, 16, Component.literal("Description"));
        descriptionField.setMaxLength(256);
        descriptionField.setHint(Component.literal("Brief description..."));
        this.addRenderableWidget(descriptionField);
        y += 30;

        // Requirements field
        requirementsField = new EditBox(this.font, x, y, fieldWidth, 16, Component.literal("Requirements"));
        requirementsField.setMaxLength(512);
        requirementsField.setHint(Component.literal("Build requirements..."));
        this.addRenderableWidget(requirementsField);
        y += 30;

        // Budget and Bond fields side by side
        int halfWidth = (fieldWidth - 10) / 2;

        budgetField = new EditBox(this.font, x, y, halfWidth, 16, Component.literal("Budget"));
        budgetField.setMaxLength(12);
        budgetField.setHint(Component.literal("0.00"));
        budgetField.setFilter(this::isValidNumberInput);
        this.addRenderableWidget(budgetField);

        // Bond field (optional)
        bondField = new EditBox(this.font, x + halfWidth + 10, y, halfWidth, 16, Component.literal("Bond"));
        bondField.setMaxLength(12);
        bondField.setHint(Component.literal("0 (optional)"));
        bondField.setFilter(this::isValidNumberInput);
        this.addRenderableWidget(bondField);
        y += 30;

        // Compensation type selector
        compTypeButton = this.addRenderableWidget(createButton(
            x, y, 150, 18,
            Component.literal("Type: " + getCompTypeDisplayName()),
            btn -> cycleCompensationType()
        ));

        // Payment per improvement point (only for VALUATION_BASED)
        paymentPerPointField = new EditBox(this.font, x + 160, y, 80, 16, Component.literal("$/Point"));
        paymentPerPointField.setMaxLength(8);
        paymentPerPointField.setHint(Component.literal("1.00"));
        paymentPerPointField.setValue("1.00");
        paymentPerPointField.setFilter(this::isValidNumberInput);
        paymentPerPointField.visible = compensationType.equals("VALUATION_BASED");
        this.addRenderableWidget(paymentPerPointField);

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
        int buttonWidth = (guiWidth - 50) / 2;

        // Chunk selection info
        // Player's current chunk is pre-selected
        if (selectedChunks.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            ChunkPos playerChunk = new ChunkPos(this.minecraft.player.blockPosition());
            selectedChunks.add(playerChunk);
            dimension = this.minecraft.player.level().dimension().location().toString();
        }

        // Main button to open chunk map selector
        this.addRenderableWidget(createButton(
            x, y + 30, guiWidth - 40, 20,
            Component.literal("§6Open Chunk Map"),
            btn -> openChunkMap()
        ));

        // Quick action buttons
        this.addRenderableWidget(createButton(
            x, y + 55, buttonWidth, 18,
            Component.literal("§a+ Current"),
            btn -> addCurrentChunk()
        ));

        this.addRenderableWidget(createButton(
            x + buttonWidth + 10, y + 55, buttonWidth, 18,
            Component.literal("§cClear All"),
            btn -> clearChunks()
        ));

        this.addRenderableWidget(createButton(
            x, y + 77, buttonWidth, 18,
            Component.literal("3x3 Area"),
            btn -> select3x3Area()
        ));

        this.addRenderableWidget(createButton(
            x + buttonWidth + 10, y + 77, buttonWidth, 18,
            Component.literal("5x5 Area"),
            btn -> select5x5Area()
        ));

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 60, 20,
            Component.literal("§7← Back"),
            btn -> goToStep1()
        ));

        // Next button - goes to milestones for MILESTONE type, otherwise creates contract
        String nextLabel = compensationType.equals("MILESTONE") ? "Next §7→" : "§aCreate";
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 85, buttonY, 70, 20,
            Component.literal(nextLabel),
            btn -> {
                if (compensationType.equals("MILESTONE")) {
                    goToStep3();
                } else {
                    createContract();
                }
            }
        ));
    }

    private void initStep3() {
        int x = guiLeft + 20;
        int y = guiTop + 40;

        // Milestone description fields
        int fieldWidth = guiWidth - 70;

        milestone25Field = new EditBox(this.font, x + 40, y, fieldWidth, 16, Component.literal("25%"));
        milestone25Field.setMaxLength(100);
        milestone25Field.setValue("Foundation complete");
        this.addRenderableWidget(milestone25Field);
        y += 26;

        milestone50Field = new EditBox(this.font, x + 40, y, fieldWidth, 16, Component.literal("50%"));
        milestone50Field.setMaxLength(100);
        milestone50Field.setValue("Structure complete");
        this.addRenderableWidget(milestone50Field);
        y += 26;

        milestone75Field = new EditBox(this.font, x + 40, y, fieldWidth, 16, Component.literal("75%"));
        milestone75Field.setMaxLength(100);
        milestone75Field.setValue("Details complete");
        this.addRenderableWidget(milestone75Field);
        y += 26;

        milestone100Field = new EditBox(this.font, x + 40, y, fieldWidth, 16, Component.literal("100%"));
        milestone100Field.setMaxLength(100);
        milestone100Field.setValue("Final inspection");
        this.addRenderableWidget(milestone100Field);

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 60, 20,
            Component.literal("§7← Back"),
            btn -> goToStep2()
        ));

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 85, buttonY, 70, 20,
            Component.literal("§aCreate"),
            btn -> createContract()
        ));
    }

    private void goToStep3() {
        currentStep = 3;
        rebuildWidgets();
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

        // Show/hide payment per point field based on compensation type
        if (paymentPerPointField != null) {
            paymentPerPointField.visible = compensationType.equals("VALUATION_BASED");
        }
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
        double paymentPerPoint;
        try {
            budget = Double.parseDouble(budgetField != null && !budgetField.getValue().isEmpty()
                ? budgetField.getValue() : "0");
            bond = Double.parseDouble(bondField != null && !bondField.getValue().isEmpty()
                ? bondField.getValue() : "0");
            paymentPerPoint = Double.parseDouble(paymentPerPointField != null && !paymentPerPointField.getValue().isEmpty()
                ? paymentPerPointField.getValue() : "1.0");
        } catch (NumberFormatException e) {
            errorMessage = "Invalid number format";
            return;
        }

        // Build chunk list
        List<CreateContractPacket.ChunkData> chunks = new ArrayList<>();
        for (ChunkPos pos : selectedChunks) {
            chunks.add(new CreateContractPacket.ChunkData(pos.x, pos.z));
        }

        // Build milestone descriptions (for MILESTONE type)
        java.util.Map<Integer, String> milestoneDescs = new java.util.HashMap<>();
        if (compensationType.equals("MILESTONE")) {
            if (milestone25Field != null && !milestone25Field.getValue().isEmpty()) {
                milestoneDescs.put(25, milestone25Field.getValue().trim());
            }
            if (milestone50Field != null && !milestone50Field.getValue().isEmpty()) {
                milestoneDescs.put(50, milestone50Field.getValue().trim());
            }
            if (milestone75Field != null && !milestone75Field.getValue().isEmpty()) {
                milestoneDescs.put(75, milestone75Field.getValue().trim());
            }
            if (milestone100Field != null && !milestone100Field.getValue().isEmpty()) {
                milestoneDescs.put(100, milestone100Field.getValue().trim());
            }
        }

        // Send packet
        NetworkHandler.sendToServer(new CreateContractPacket(
            nationName, title, description, requirements,
            budget, bond, compensationType, paymentPerPoint, chunks, dimension, milestoneDescs
        ));

        // Go back to contracts list
        goBack();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Step indicator
        String stepText = "Step " + currentStep + "/" + totalSteps;
        graphics.drawString(this.font, "§7" + stepText, guiLeft + guiWidth - font.width(stepText) - 15, guiTop + 8, COLOR_TEXT);

        if (currentStep == 1) {
            renderStep1Content(graphics);
        } else if (currentStep == 2) {
            renderStep2Content(graphics);
        } else {
            renderStep3Content(graphics);
        }

        // Error message
        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + guiHeight - 42, COLOR_WARNING);
        }
    }

    private void renderStep3Content(GuiGraphics graphics) {
        int x = guiLeft + 20;
        int y = guiTop + 28;

        graphics.drawString(this.font, "§6Milestone Descriptions", x, y, COLOR_PRIMARY);
        y += 14;

        graphics.drawString(this.font, "§725%:", x, y, 0xFFAAAAAA);
        y += 26;
        graphics.drawString(this.font, "§750%:", x, y, 0xFFAAAAAA);
        y += 26;
        graphics.drawString(this.font, "§775%:", x, y, 0xFFAAAAAA);
        y += 26;
        graphics.drawString(this.font, "§7100%:", x, y, 0xFFAAAAAA);
    }

    private void renderStep1Content(GuiGraphics graphics) {
        int x = guiLeft + 20;
        int y = guiTop + 28;
        int fieldWidth = guiWidth - 40;
        int halfWidth = (fieldWidth - 10) / 2;

        // Field labels positioned above their text boxes
        graphics.drawString(this.font, "§7Title:", x, y, 0xFFAAAAAA);
        y += 30;
        graphics.drawString(this.font, "§7Description:", x, y, 0xFFAAAAAA);
        y += 30;
        graphics.drawString(this.font, "§7Requirements:", x, y, 0xFFAAAAAA);
        y += 30;
        graphics.drawString(this.font, "§7Budget ($):", x, y, 0xFFAAAAAA);
        graphics.drawString(this.font, "§7Bond ($):", x + halfWidth + 10, y, 0xFFAAAAAA);
        y += 30;
        graphics.drawString(this.font, "§7Payment:", x, y, 0xFFAAAAAA);

        // Compensation type description below the button
        String compDesc = switch (compensationType) {
            case "FIXED" -> "§8Full payment on completion";
            case "MILESTONE" -> "§8Payments at 25%, 50%, 75%, 100%";
            case "VALUATION_BASED" -> "§8Pay per improvement point";
            default -> "";
        };
        if (!compDesc.isEmpty()) {
            graphics.drawString(this.font, compDesc, x, y + 20, 0xFF888888);
        }
    }

    private void renderStep2Content(GuiGraphics graphics) {
        int x = guiLeft + 20;
        int y = guiTop + 28;

        graphics.drawString(this.font, "§6Select Project Chunks", x, y, COLOR_PRIMARY);
        y += 12;

        // Selected chunks info
        graphics.drawString(this.font, "§7Selected: §f" + selectedChunks.size() + " chunks", x, y, COLOR_TEXT);

        // Dimension (shortened)
        String dimDisplay = dimension.replace("minecraft:", "");
        int dimWidth = this.font.width("Dim: " + dimDisplay);
        graphics.drawString(this.font, "§7Dim: §f" + dimDisplay, guiLeft + guiWidth - dimWidth - 20, y, COLOR_TEXT);

        // List selected chunks below buttons
        y = guiTop + 130;
        if (!selectedChunks.isEmpty()) {
            graphics.drawString(this.font, "§7Chunks:", x, y, 0xFFAAAAAA);
            y += 10;

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (ChunkPos chunk : selectedChunks) {
                if (count >= 4) {
                    sb.append("+" + (selectedChunks.size() - 4) + " more");
                    break;
                }
                if (count > 0) sb.append(", ");
                sb.append("(" + chunk.x + "," + chunk.z + ")");
                count++;
            }
            graphics.drawString(this.font, "§8" + sb.toString(), x, y, 0xFF666666);
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



