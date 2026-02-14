package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.CreateNationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for creating a new nation
 */
public class CreateNationScreen extends StateCraftScreen {

    private EditBox nameField;
    private EditBox tagField;
    private EditBox descriptionField;
    private Button createButton;
    private Button cancelButton;

    private String errorMessage = null;
    private boolean creating = false;

    public CreateNationScreen() {
        super(Component.literal("Create Nation"));
        this.guiWidth = 260; this.guiHeight = 180;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 180;
        int fieldX = guiLeft + (guiWidth - fieldWidth) / 2;
        int startY = guiTop + 35;

        // Nation name field
        this.nameField = new EditBox(this.font, fieldX, startY, fieldWidth, 18, Component.literal("Nation Name"));
        this.nameField.setMaxLength(24);
        this.nameField.setHint(Component.literal("Nation Name (3-24 chars)"));
        this.nameField.setResponder(this::onNameChanged);
        this.addRenderableWidget(this.nameField);

        // Nation tag field (short prefix)
        this.tagField = new EditBox(this.font, fieldX, startY + 28, 60, 18, Component.literal("Tag"));
        this.tagField.setMaxLength(5);
        this.tagField.setHint(Component.literal("Tag"));
        this.addRenderableWidget(this.tagField);

        // Description field
        this.descriptionField = new EditBox(this.font, fieldX, startY + 56, fieldWidth, 18, Component.literal("Description"));
        this.descriptionField.setMaxLength(100);
        this.descriptionField.setHint(Component.literal("Description (optional)"));
        this.addRenderableWidget(this.descriptionField);

        // Create button
        this.createButton = this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 85, guiTop + guiHeight - 35,
            80, 20,
            Component.literal("Create"),
            btn -> createNation()
        ));
        this.createButton.active = false;

        // Cancel button
        this.cancelButton = this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 5, guiTop + guiHeight - 35,
            80, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));

        // Focus name field
        this.setInitialFocus(this.nameField);
    }

    private void onNameChanged(String name) {
        // Validate name length
        boolean valid = name.length() >= 3 && name.length() <= 24;
        this.createButton.active = valid && !creating;
        this.errorMessage = null;

        if (name.length() > 0 && name.length() < 3) {
            this.errorMessage = "Name too short (min 3 characters)";
        }
    }

    private void createNation() {
        String name = this.nameField.getValue().trim();
        String tag = this.tagField.getValue().trim();
        String description = this.descriptionField.getValue().trim();

        if (name.length() < 3) {
            this.errorMessage = "Name must be at least 3 characters!";
            return;
        }

        // Send creation request to server
        this.creating = true;
        this.createButton.active = false;
        this.createButton.setMessage(Component.literal("Creating..."));

        NetworkHandler.sendToServer(new CreateNationPacket(name, tag, description));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;

        // Divider
        renderDivider(graphics, guiLeft + 10, guiTop + 24, guiWidth - 20);

        // Field labels
        int labelX = guiLeft + 20;
        int startY = guiTop + 35;

        graphics.drawString(this.font, "Name:", labelX - 5, startY + 5, COLOR_TEXT);
        graphics.drawString(this.font, "Tag:", labelX - 5, startY + 33, COLOR_TEXT);
        graphics.drawString(this.font, "Info:", labelX - 5, startY + 61, COLOR_TEXT);

        // Requirements
        graphics.drawString(this.font, "§7(3-24 chars)", guiLeft + guiWidth - 70, startY + 5, 0xFFAAAAAA);
        graphics.drawString(this.font, "§7(1-5)", guiLeft + 95, startY + 33, 0xFFAAAAAA);

        // Error message
        if (errorMessage != null) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, centerX, guiTop + guiHeight - 55, COLOR_WARNING);
        }

        // Info text
        graphics.drawCenteredString(this.font, "§8Creating a nation costs nothing!", centerX, guiTop + guiHeight - 55, 0xFF888888);
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler on success
    public void onNationCreated(String nationName) {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    // Called by network handler on failure
    public void onCreationFailed(String reason) {
        this.creating = false;
        this.createButton.active = true;
        this.createButton.setMessage(Component.literal("Create"));
        this.errorMessage = reason;
    }
}

