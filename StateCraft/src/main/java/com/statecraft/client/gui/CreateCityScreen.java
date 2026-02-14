package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.CreateCityPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for creating a new city
 */
public class CreateCityScreen extends StateCraftScreen {

    private final String nationName;
    private final String stateName;
    private EditBox nameInput;
    private Button createButton;
    private String errorMessage = "";

    public CreateCityScreen(String nationName, String stateName) {
        super(Component.literal("Create City"));
        this.nationName = nationName;
        this.stateName = stateName;
        this.guiWidth = 220; this.guiHeight = 150;
    }

    @Override
    protected void init() {
        super.init();

        // City name input
        this.nameInput = new EditBox(this.font, guiLeft + 15, guiTop + 55, guiWidth - 30, 20,
            Component.literal("City Name"));
        this.nameInput.setMaxLength(24);
        this.nameInput.setHint(Component.literal("Enter city name..."));
        this.nameInput.setResponder(this::onNameChanged);
        this.addRenderableWidget(nameInput);

        // Create button
        this.createButton = this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 32,
            90, 20,
            Component.literal("Create"),
            btn -> createCity()
        ));
        this.createButton.active = false;

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 105, guiTop + guiHeight - 32,
            90, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // State info
        graphics.drawString(this.font, "§7State: §f" + stateName, guiLeft + 15, guiTop + 30, COLOR_TEXT);

        // Label
        graphics.drawString(this.font, "§7City Name:", guiLeft + 15, guiTop + 43, COLOR_TEXT);

        // Error message
        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + 83, 0xFFFF5555);
        } else {
            // Requirements
            graphics.drawString(this.font, "§83-24 characters", guiLeft + 15, guiTop + 83, 0xFF888888);
        }

        // Info
        graphics.drawCenteredString(this.font, "§8You will be the mayor of this city", this.width / 2, guiTop + 98, 0xFF666666);
    }

    private void onNameChanged(String name) {
        errorMessage = "";

        if (name.isEmpty()) {
            createButton.active = false;
            return;
        }

        if (name.length() < 3) {
            errorMessage = "Name too short (min 3)";
            createButton.active = false;
            return;
        }

        if (name.length() > 24) {
            errorMessage = "Name too long (max 24)";
            createButton.active = false;
            return;
        }

        // Check for invalid characters
        if (!name.matches("^[a-zA-Z0-9_ ]+$")) {
            errorMessage = "Invalid characters";
            createButton.active = false;
            return;
        }

        createButton.active = true;
    }

    private void createCity() {
        String name = nameInput.getValue().trim();

        if (name.length() < 3 || name.length() > 24) {
            errorMessage = "Invalid name length";
            return;
        }

        // Send packet to server
        NetworkHandler.sendToServer(new CreateCityPacket(stateName, name));

        // Go back to cities list
        goBack();
    }

    private void goBack() {
        this.minecraft.setScreen(new CitiesListScreen(nationName, stateName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

