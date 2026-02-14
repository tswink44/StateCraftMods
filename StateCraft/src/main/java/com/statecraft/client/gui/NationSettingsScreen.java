package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.UpdateNationSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for managing nation settings (admin only)
 */
public class NationSettingsScreen extends StateCraftScreen {
    private final String nationName;

    private EditBox descriptionField;
    private EditBox tagField;
    private Button openToggle;
    private Button saveButton;

    private boolean isOpen = false;
    private boolean hasChanges = false;

    public NationSettingsScreen(String nationName) {
        super(Component.literal("Nation Settings"));
        this.nationName = nationName;
        this.guiWidth = 260; this.guiHeight = 180;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 160;
        int fieldX = guiLeft + 70;
        int startY = guiTop + 40;

        // Tag field
        this.tagField = new EditBox(this.font, fieldX, startY, 60, 18, Component.literal("Tag"));
        this.tagField.setMaxLength(5);
        this.tagField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.tagField);

        // Description field
        this.descriptionField = new EditBox(this.font, fieldX, startY + 26, fieldWidth, 18, Component.literal("Description"));
        this.descriptionField.setMaxLength(100);
        this.descriptionField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.descriptionField);

        // Open/Closed toggle
        this.openToggle = this.addRenderableWidget(createButton(
            fieldX, startY + 52, 80, 18,
            Component.literal(isOpen ? "§aOpen" : "§cClosed"),
            btn -> toggleOpen()
        ));

        // Save button
        this.saveButton = this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 85, guiTop + guiHeight - 32,
            80, 20,
            Component.literal("Save"),
            btn -> saveSettings()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 5, guiTop + guiHeight - 32,
            80, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int labelX = guiLeft + 20;
        int startY = guiTop + 40;

        graphics.drawString(this.font, "§7Tag:", labelX, startY + 5, COLOR_TEXT);
        graphics.drawString(this.font, "§7Info:", labelX, startY + 31, COLOR_TEXT);
        graphics.drawString(this.font, "§7Status:", labelX, startY + 57, COLOR_TEXT);

        // Unsaved changes indicator
        if (hasChanges) {
            graphics.drawString(this.font, "§e* Unsaved changes", guiLeft + 15, guiTop + guiHeight - 50, 0xFFFFAA00);
        }
    }

    private void toggleOpen() {
        isOpen = !isOpen;
        openToggle.setMessage(Component.literal(isOpen ? "§aOpen" : "§cClosed"));
        hasChanges = true;
    }

    private void saveSettings() {
        String tag = tagField.getValue().trim();
        String description = descriptionField.getValue().trim();

        NetworkHandler.sendToServer(new UpdateNationSettingsPacket(nationName, tag, description, isOpen));

        hasChanges = false;
        goBack();
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called to populate current settings
    public void setCurrentSettings(String tag, String description, boolean open) {
        this.tagField.setValue(tag);
        this.descriptionField.setValue(description);
        this.isOpen = open;
        this.openToggle.setMessage(Component.literal(open ? "§aOpen" : "§cClosed"));
        this.hasChanges = false;
    }
}

