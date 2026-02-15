package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.UpdateEntitySettingsPacket;
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

    private EditBox nameField;
    private EditBox descriptionField;
    private EditBox tagField;
    private EditBox flagUrlField;
    private Button openToggle;
    private Button saveButton;

    private boolean isOpen = false;
    private boolean hasChanges = false;
    private String currentFlagUrl = "";

    public NationSettingsScreen(String nationName) {
        super(Component.literal("Nation Settings"));
        this.nationName = nationName;
        this.guiWidth = 280; this.guiHeight = 250;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 160;
        int fieldX = guiLeft + 100;
        int startY = guiTop + 35;
        int rowSpacing = 22;

        // Name field
        this.nameField = new EditBox(this.font, fieldX, startY, fieldWidth, 16, Component.literal("Name"));
        this.nameField.setMaxLength(32);
        this.nameField.setValue(nationName);
        this.nameField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.nameField);

        // Tag field
        this.tagField = new EditBox(this.font, fieldX, startY + rowSpacing, 60, 16, Component.literal("Tag"));
        this.tagField.setMaxLength(5);
        this.tagField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.tagField);

        // Description field
        this.descriptionField = new EditBox(this.font, fieldX, startY + rowSpacing * 2, fieldWidth, 16, Component.literal("Description"));
        this.descriptionField.setMaxLength(100);
        this.descriptionField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.descriptionField);

        // Flag URL field
        this.flagUrlField = new EditBox(this.font, fieldX, startY + rowSpacing * 3, fieldWidth, 16, Component.literal("Flag URL"));
        this.flagUrlField.setMaxLength(512);
        this.flagUrlField.setValue(currentFlagUrl);
        this.flagUrlField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.flagUrlField);

        // Open/Closed toggle
        this.openToggle = this.addRenderableWidget(createButton(
            fieldX, startY + rowSpacing * 4, 80, 16,
            Component.literal(isOpen ? "§aOpen" : "§cClosed"),
            btn -> toggleOpen()
        ));

        // Officers management button
        this.addRenderableWidget(createButton(
            fieldX, startY + rowSpacing * 5, 100, 16,
            Component.literal("§eManage Officers"),
            btn -> openOfficerManagement()
        ));

        // Save button
        this.saveButton = this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 85, guiTop + guiHeight - 30,
            80, 20,
            Component.literal("Save"),
            btn -> saveSettings()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 5, guiTop + guiHeight - 30,
            80, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int labelX = guiLeft + 15;
        int startY = guiTop + 35;
        int rowSpacing = 22;

        graphics.drawString(this.font, "§7Name:", labelX, startY + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Tag:", labelX, startY + rowSpacing + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Info:", labelX, startY + rowSpacing * 2 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Flag:", labelX, startY + rowSpacing * 3 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Status:", labelX, startY + rowSpacing * 4 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Officers:", labelX, startY + rowSpacing * 5 + 4, COLOR_TEXT);

        // Unsaved changes indicator
        if (hasChanges) {
            graphics.drawString(this.font, "§e* Unsaved changes", guiLeft + 15, guiTop + guiHeight - 48, 0xFFFFAA00);
        }
    }

    private void toggleOpen() {
        isOpen = !isOpen;
        openToggle.setMessage(Component.literal(isOpen ? "§aOpen" : "§cClosed"));
        hasChanges = true;
    }

    private void saveSettings() {
        String newName = nameField.getValue().trim();
        String tag = tagField.getValue().trim();
        String description = descriptionField.getValue().trim();
        String flagUrl = flagUrlField.getValue().trim();

        // Send update packets
        // One for name/flag
        NetworkHandler.sendToServer(new UpdateEntitySettingsPacket(
            UpdateEntitySettingsPacket.EntityType.NATION, nationName, newName, flagUrl, false, -1, -1));

        // One for tag/description/open status
        NetworkHandler.sendToServer(new UpdateNationSettingsPacket(
            newName.isEmpty() ? nationName : newName, tag, description, isOpen));

        hasChanges = false;
        // Navigate to the updated nation name
        String finalName = newName.isEmpty() ? nationName : newName;
        this.minecraft.setScreen(new NationInfoScreen(finalName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    private void openOfficerManagement() {
        this.minecraft.setScreen(new OfficerManagementScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called to populate current settings
    public void setCurrentSettings(String tag, String description, boolean open, String flagUrl) {
        if (this.tagField != null) this.tagField.setValue(tag != null ? tag : "");
        if (this.descriptionField != null) this.descriptionField.setValue(description != null ? description : "");
        this.isOpen = open;
        if (this.openToggle != null) this.openToggle.setMessage(Component.literal(open ? "§aOpen" : "§cClosed"));
        this.currentFlagUrl = flagUrl != null ? flagUrl : "";
        if (this.flagUrlField != null) this.flagUrlField.setValue(currentFlagUrl);
        this.hasChanges = false;
    }

    // Overloads for backward compatibility
    public void setCurrentSettings(String tag, String description, boolean open, String flagUrl, double statePassThrough) {
        setCurrentSettings(tag, description, open, flagUrl);
    }

    public void setCurrentSettings(String tag, String description, boolean open) {
        setCurrentSettings(tag, description, open, "");
    }
}

