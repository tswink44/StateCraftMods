package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.UpdateEntitySettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for managing state settings (governor/admin only)
 * Note: States set the pass-through rate that cities must give to the state.
 *       Nations set the pass-through rate that states must give to the nation.
 *       Max chunks is controlled by server config (statecraft.toml)
 */
public class StateSettingsScreen extends StateCraftScreen {
    private final String nationName;
    private final String stateName;

    private EditBox nameField;
    private EditBox descriptionField;
    private EditBox flagUrlField;
    private EditBox cityPassThroughField; // Rate cities must give to state
    private Button saveButton;

    private boolean hasChanges = false;
    private String currentFlagUrl = "";
    private String currentDescription = "";
    private double currentCityPassThrough = 20.0; // Default 20% from cities

    public StateSettingsScreen(String nationName, String stateName) {
        super(Component.literal("State Settings"));
        this.nationName = nationName;
        this.stateName = stateName;
        this.guiWidth = 280; this.guiHeight = 220;
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
        this.nameField.setValue(stateName);
        this.nameField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.nameField);

        // Description field
        this.descriptionField = new EditBox(this.font, fieldX, startY + rowSpacing, fieldWidth, 16, Component.literal("Description"));
        this.descriptionField.setMaxLength(100);
        this.descriptionField.setValue(currentDescription);
        this.descriptionField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.descriptionField);

        // Flag URL field
        this.flagUrlField = new EditBox(this.font, fieldX, startY + rowSpacing * 2, fieldWidth, 16, Component.literal("Flag URL"));
        this.flagUrlField.setMaxLength(512);
        this.flagUrlField.setValue(currentFlagUrl);
        this.flagUrlField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.flagUrlField);

        // City pass-through rate field (what cities give to state)
        this.cityPassThroughField = new EditBox(this.font, fieldX, startY + rowSpacing * 3, 60, 16, Component.literal("City Tax"));
        this.cityPassThroughField.setMaxLength(5);
        this.cityPassThroughField.setValue(String.format("%.1f", currentCityPassThrough));
        this.cityPassThroughField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.cityPassThroughField);


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
        graphics.drawString(this.font, "§7Info:", labelX, startY + rowSpacing + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Flag:", labelX, startY + rowSpacing * 2 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7From Cities:", labelX, startY + rowSpacing * 3 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§8%", guiLeft + 165, startY + rowSpacing * 3 + 4, 0xFF888888);

        // Unsaved changes indicator
        if (hasChanges) {
            graphics.drawString(this.font, "§e* Unsaved changes", guiLeft + 15, guiTop + guiHeight - 48, 0xFFFFAA00);
        }
    }

    private void saveSettings() {
        String newName = nameField.getValue().trim();
        String flagUrl = flagUrlField.getValue().trim();

        // Parse city pass-through rate
        double cityPassThrough = parseDouble(cityPassThroughField.getValue(), currentCityPassThrough);
        cityPassThrough = Math.max(0, Math.min(100, cityPassThrough));

        // Send update packet - taxRate field used for city pass-through (maxChunks now server config only)
        NetworkHandler.sendToServer(new UpdateEntitySettingsPacket(
            UpdateEntitySettingsPacket.EntityType.STATE, stateName, newName, flagUrl, false, cityPassThrough, 0));

        hasChanges = false;
        String finalName = newName.isEmpty() ? stateName : newName;
        this.minecraft.setScreen(new StateInfoScreen(nationName, finalName));
    }

    private double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new StateInfoScreen(nationName, stateName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called to populate current settings
    public void setCurrentSettings(String description, String flagUrl) {
        setCurrentSettings(description, flagUrl, 20.0);
    }

    public void setCurrentSettings(String description, String flagUrl, double cityPassThrough) {
        this.currentDescription = description != null ? description : "";
        this.currentFlagUrl = flagUrl != null ? flagUrl : "";
        this.currentCityPassThrough = cityPassThrough;

        if (this.descriptionField != null) {
            this.descriptionField.setValue(currentDescription);
        }
        if (this.flagUrlField != null) {
            this.flagUrlField.setValue(currentFlagUrl);
        }
        if (this.cityPassThroughField != null) {
            this.cityPassThroughField.setValue(String.format("%.1f", cityPassThrough));
        }
        this.hasChanges = false;
    }

    // Keep overload for backward compatibility
    public void setCurrentSettings(String description, String flagUrl, double cityPassThrough, int maxChunks) {
        setCurrentSettings(description, flagUrl, cityPassThrough);
    }
}
