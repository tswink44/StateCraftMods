package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.CreateStatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for creating a new state
 */
public class CreateStateScreen extends StateCraftScreen {

    private final String nationName;
    private EditBox nameInput;
    private Button createButton;
    private String errorMessage = "";

    public CreateStateScreen(String nationName) {
        super(Component.literal("Create State"));
        this.nationName = nationName;
        this.guiWidth = 220; this.guiHeight = 130;
    }

    @Override
    protected void init() {
        super.init();

        // State name input
        this.nameInput = new EditBox(this.font, guiLeft + 15, guiTop + 50, guiWidth - 30, 20,
            Component.literal("State Name"));
        this.nameInput.setMaxLength(24);
        this.nameInput.setHint(Component.literal("Enter state name..."));
        this.nameInput.setResponder(this::onNameChanged);
        this.addRenderableWidget(nameInput);

        // Create button
        this.createButton = this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 32,
            90, 20,
            Component.literal("Create"),
            btn -> createState()
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

        // Label
        graphics.drawString(this.font, "§7State Name:", guiLeft + 15, guiTop + 38, COLOR_TEXT);

        // Error message
        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + 78, 0xFFFF5555);
        }

        // Requirements
        graphics.drawString(this.font, "§83-24 characters", guiLeft + 15, guiTop + 78, 0xFF888888);
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

    private void createState() {
        String name = nameInput.getValue().trim();

        if (name.length() < 3 || name.length() > 24) {
            errorMessage = "Invalid name length";
            return;
        }

        // Send packet to server
        NetworkHandler.sendToServer(new CreateStatePacket(name));

        // Go back to states list
        goBack();
    }

    private void goBack() {
        this.minecraft.setScreen(new StatesListScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

