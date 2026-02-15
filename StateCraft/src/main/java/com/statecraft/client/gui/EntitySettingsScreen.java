package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.UpdateEntitySettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for editing Nation/State/City settings (name, flag URL)
 */
public class EntitySettingsScreen extends StateCraftScreen {

    private final UpdateEntitySettingsPacket.EntityType entityType;
    private final String entityName;
    private final String currentFlagUrl;

    private EditBox nameInput;
    private EditBox flagUrlInput;

    public EntitySettingsScreen(UpdateEntitySettingsPacket.EntityType entityType, String entityName, String currentFlagUrl) {
        super(Component.literal(getTitle(entityType)));
        this.entityType = entityType;
        this.entityName = entityName;
        this.currentFlagUrl = currentFlagUrl != null ? currentFlagUrl : "";
        this.guiWidth = 280;
        this.guiHeight = 180;
    }

    private static String getTitle(UpdateEntitySettingsPacket.EntityType type) {
        return switch (type) {
            case NATION -> "Nation Settings";
            case STATE -> "State Settings";
            case CITY -> "City Settings";
        };
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int inputWidth = guiWidth - 40;

        // Name input
        nameInput = new EditBox(this.font, centerX - inputWidth / 2, guiTop + 50, inputWidth, 18, Component.literal("Name"));
        nameInput.setMaxLength(32);
        nameInput.setValue(entityName);
        nameInput.setHint(Component.literal("Enter new name..."));
        this.addRenderableWidget(nameInput);

        // Flag URL input
        flagUrlInput = new EditBox(this.font, centerX - inputWidth / 2, guiTop + 95, inputWidth, 18, Component.literal("Flag URL"));
        flagUrlInput.setMaxLength(512);
        flagUrlInput.setValue(currentFlagUrl);
        flagUrlInput.setHint(Component.literal("https://example.com/flag.png"));
        this.addRenderableWidget(flagUrlInput);

        // Save button
        this.addRenderableWidget(Button.builder(
            Component.literal("Save"),
            btn -> saveSettings()
        ).pos(centerX - 65, guiTop + guiHeight - 35).size(60, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            btn -> goBack()
        ).pos(centerX + 5, guiTop + guiHeight - 35).size(60, 20).build());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Divider
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Labels
        graphics.drawString(this.font, "§7Name:", guiLeft + 20, guiTop + 38, 0xFFCCCCCC);
        graphics.drawString(this.font, "§7Flag URL:", guiLeft + 20, guiTop + 83, 0xFFCCCCCC);

        // Help text
        graphics.drawString(this.font, "§8Image URL for flag (PNG recommended)", guiLeft + 20, guiTop + 118, 0xFF888888);
    }

    private void saveSettings() {
        String newName = nameInput.getValue().trim();
        String flagUrl = flagUrlInput.getValue().trim();

        // Basic validation
        if (newName.isEmpty()) {
            newName = entityName; // Keep original name
        }

        // Send update to server
        NetworkHandler.sendToServer(new UpdateEntitySettingsPacket(
            entityType, entityName, newName, flagUrl
        ));

        goBack();
    }

    private void goBack() {
        // Go back to appropriate info screen
        switch (entityType) {
            case NATION -> this.minecraft.setScreen(new NationInfoScreen(entityName));
            case STATE -> this.minecraft.setScreen(new MainMenuScreen()); // Would need nation context
            case CITY -> this.minecraft.setScreen(new MainMenuScreen()); // Would need state context
        }
    }

    @Override
    public void onClose() {
        goBack();
    }
}

