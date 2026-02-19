package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.AppointLeaderPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for appointing a governor or mayor.
 * Shows a text input for the player name and a confirm button.
 */
public class AppointScreen extends StateCraftScreen {

    public enum AppointType {
        GOVERNOR, MAYOR
    }

    private final String nationName;
    private final String stateName;
    private final String cityName;  // Only used for MAYOR
    private final AppointType type;
    private final Runnable onBack;

    private EditBox playerNameInput;
    private Button confirmButton;

    public AppointScreen(String nationName, String stateName, String cityName, AppointType type, Runnable onBack) {
        super(Component.literal(type == AppointType.GOVERNOR ? "Appoint Governor" : "Appoint Mayor"));
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.type = type;
        this.onBack = onBack;
        this.guiWidth = 220;
        this.guiHeight = 130;
    }

    @Override
    protected void init() {
        super.init();

        // Player name input
        playerNameInput = new EditBox(this.font, guiLeft + 15, guiTop + 55, guiWidth - 30, 18,
            Component.literal("Player Name"));
        playerNameInput.setMaxLength(16);
        playerNameInput.setHint(Component.literal("Enter player name..."));
        playerNameInput.setResponder(text -> {
            if (confirmButton != null) {
                confirmButton.active = !text.trim().isEmpty();
            }
        });
        this.addRenderableWidget(playerNameInput);

        // Confirm button
        confirmButton = this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 55, guiTop + guiHeight - 30,
            50, 20,
            Component.literal("§aAppoint"),
            btn -> doAppoint()
        ));
        confirmButton.active = false;

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 5, guiTop + guiHeight - 30,
            50, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));

        // Focus the input
        this.setInitialFocus(playerNameInput);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        String roleLabel = type == AppointType.GOVERNOR ? "Governor" : "Mayor";
        String targetLabel = type == AppointType.GOVERNOR ? stateName : cityName;

        graphics.drawCenteredString(this.font, "§7Appoint new " + roleLabel + " for:", this.width / 2, guiTop + 28, COLOR_TEXT);
        graphics.drawCenteredString(this.font, "§e" + targetLabel, this.width / 2, guiTop + 40, COLOR_PRIMARY);
    }

    private void doAppoint() {
        String targetPlayer = playerNameInput.getValue().trim();
        if (targetPlayer.isEmpty()) return;

        if (type == AppointType.GOVERNOR) {
            NetworkHandler.sendToServer(AppointLeaderPacket.appointGovernor(nationName, stateName, targetPlayer));
        } else {
            NetworkHandler.sendToServer(AppointLeaderPacket.appointMayor(nationName, stateName, cityName, targetPlayer));
        }

        goBack();
    }

    private void goBack() {
        if (onBack != null) {
            onBack.run();
        } else {
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        if (onBack != null) {
            onBack.run();
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}

