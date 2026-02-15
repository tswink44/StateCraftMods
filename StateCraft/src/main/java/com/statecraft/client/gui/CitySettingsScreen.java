package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.UpdateEntitySettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for managing city settings (mayor/governor/admin only)
 * Note: Cities set the base tax rate. States set the pass-through rate from cities.
 *       Max chunks is controlled by server config (statecraft.toml)
 */
public class CitySettingsScreen extends StateCraftScreen {
    private final String nationName;
    private final String stateName;
    private final String cityName;

    private EditBox nameField;
    private EditBox descriptionField;
    private EditBox flagUrlField;
    private EditBox taxRateField;
    private Button publicJoinToggle;
    private Button saveButton;

    private boolean hasChanges = false;
    private boolean publicJoin = false;
    private String currentFlagUrl = "";
    private String currentDescription = "";
    private double currentTaxRate = 5.0; // Default 5%

    public CitySettingsScreen(String nationName, String stateName, String cityName) {
        super(Component.literal("City Settings"));
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
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
        this.nameField.setValue(cityName);
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

        // Public join toggle
        this.publicJoinToggle = this.addRenderableWidget(createButton(
            fieldX, startY + rowSpacing * 3, 80, 16,
            Component.literal(publicJoin ? "§aPublic" : "§cPrivate"),
            btn -> togglePublicJoin()
        ));

        // Tax rate field (base rate - cities control this)
        this.taxRateField = new EditBox(this.font, fieldX, startY + rowSpacing * 4, 60, 16, Component.literal("Tax Rate"));
        this.taxRateField.setMaxLength(5);
        this.taxRateField.setValue(String.format("%.1f", currentTaxRate));
        this.taxRateField.setResponder(s -> hasChanges = true);
        this.addRenderableWidget(this.taxRateField);


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
        graphics.drawString(this.font, "§7Join:", labelX, startY + rowSpacing * 3 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§7Tax Rate:", labelX, startY + rowSpacing * 4 + 4, COLOR_TEXT);
        graphics.drawString(this.font, "§8%", guiLeft + 165, startY + rowSpacing * 4 + 4, 0xFF888888);

        // Unsaved changes indicator
        if (hasChanges) {
            graphics.drawString(this.font, "§e* Unsaved changes", guiLeft + 15, guiTop + guiHeight - 48, 0xFFFFAA00);
        }
    }

    private void togglePublicJoin() {
        publicJoin = !publicJoin;
        publicJoinToggle.setMessage(Component.literal(publicJoin ? "§aPublic" : "§cPrivate"));
        hasChanges = true;
    }

    private void saveSettings() {
        String newName = nameField.getValue().trim();
        String flagUrl = flagUrlField.getValue().trim();

        // Parse tax rate
        double taxRate = parseDouble(taxRateField.getValue(), currentTaxRate);
        taxRate = Math.max(0, Math.min(100, taxRate));

        // Send update packet (maxChunks is now server config only)
        NetworkHandler.sendToServer(new UpdateEntitySettingsPacket(
            UpdateEntitySettingsPacket.EntityType.CITY, cityName, newName, flagUrl, publicJoin, taxRate, 0));

        hasChanges = false;
        String finalName = newName.isEmpty() ? cityName : newName;
        this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, finalName));
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
        this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, cityName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called to populate current settings
    public void setCurrentSettings(String description, String flagUrl, boolean publicJoin) {
        setCurrentSettings(description, flagUrl, publicJoin, 5.0);
    }

    public void setCurrentSettings(String description, String flagUrl, boolean publicJoin, double taxRate) {
        this.currentDescription = description != null ? description : "";
        this.currentFlagUrl = flagUrl != null ? flagUrl : "";
        this.publicJoin = publicJoin;
        this.currentTaxRate = taxRate;

        if (this.descriptionField != null) {
            this.descriptionField.setValue(currentDescription);
        }
        if (this.flagUrlField != null) {
            this.flagUrlField.setValue(currentFlagUrl);
        }
        if (this.publicJoinToggle != null) {
            this.publicJoinToggle.setMessage(Component.literal(publicJoin ? "§aPublic" : "§cPrivate"));
        }
        if (this.taxRateField != null) {
            this.taxRateField.setValue(String.format("%.1f", taxRate));
        }
        this.hasChanges = false;
    }

    // Keep overload for backward compatibility
    public void setCurrentSettings(String description, String flagUrl, boolean publicJoin, double taxRate, int maxChunks) {
        setCurrentSettings(description, flagUrl, publicJoin, taxRate);
    }
}
