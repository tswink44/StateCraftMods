package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.InvitePlayerPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Dialog screen for inviting players to nations/states/cities
 */
public class InvitePlayerScreen extends StateCraftScreen {

    public enum InviteTarget {
        NATION,
        STATE,
        CITY
    }

    private final InviteTarget targetType;
    private final String entityName;
    private final Runnable onClose;

    private EditBox playerNameInput;
    private String statusMessage = "";
    private int statusTicks = 0;
    private boolean statusIsError = false;

    public InvitePlayerScreen(InviteTarget targetType, String entityName, Runnable onClose) {
        super(Component.literal("Invite Player"));
        this.targetType = targetType;
        this.entityName = entityName;
        this.onClose = onClose;
        this.guiWidth = 200;
        this.guiHeight = 100;
    }

    @Override
    protected void init() {
        super.init();

        // Player name input
        playerNameInput = new EditBox(this.font, guiLeft + 15, guiTop + 35, guiWidth - 30, 20,
            Component.literal("Player Name"));
        playerNameInput.setMaxLength(16);
        playerNameInput.setHint(Component.literal("Enter player name..."));
        this.addRenderableWidget(playerNameInput);

        // Invite button
        this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 30,
            80, 20,
            Component.literal("§aInvite"),
            btn -> sendInvite()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 95, guiTop + guiHeight - 30,
            80, 20,
            Component.literal("Cancel"),
            btn -> closeScreen()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        String targetText = switch (targetType) {
            case NATION -> "Invite to Nation: " + entityName;
            case STATE -> "Invite to State: " + entityName;
            case CITY -> "Invite to City: " + entityName;
        };
        graphics.drawString(this.font, "§7" + targetText, guiLeft + 15, guiTop + 25, COLOR_TEXT);

        // Status message
        if (statusTicks > 0) {
            int statusColor = statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            graphics.drawCenteredString(this.font, statusMessage, guiLeft + guiWidth / 2, guiTop + 60, statusColor);
            statusTicks--;
        }
    }

    private void sendInvite() {
        String playerName = playerNameInput.getValue().trim();

        if (playerName.isEmpty()) {
            showStatus("§cPlease enter a player name", true);
            return;
        }

        InvitePlayerPacket packet = switch (targetType) {
            case NATION -> InvitePlayerPacket.inviteToNation(entityName, playerName);
            case STATE -> InvitePlayerPacket.inviteToState(entityName, playerName);
            case CITY -> InvitePlayerPacket.inviteToCity(entityName, playerName);
        };

        NetworkHandler.sendToServer(packet);
        showStatus("§aSending invite...", false);

        // Clear input
        playerNameInput.setValue("");
    }

    private void showStatus(String message, boolean isError) {
        this.statusMessage = message;
        this.statusTicks = 60; // 3 seconds
        this.statusIsError = isError;
    }

    private void closeScreen() {
        if (onClose != null) {
            onClose.run();
        } else {
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        if (onClose != null) {
            onClose.run();
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    /**
     * Called when server responds with invite result
     */
    public void handleResult(boolean success, String message) {
        showStatus(message, !success);
        if (success) {
            // Auto-close after a short delay on success
            // The status message will show briefly before closing
        }
    }
}

