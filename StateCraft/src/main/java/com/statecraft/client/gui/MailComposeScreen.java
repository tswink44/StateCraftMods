package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.SendMailPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for composing and sending mail.
 * Supports individual mail and broadcast mail (to all nation members).
 */
public class MailComposeScreen extends StateCraftScreen {

    private EditBox recipientField;
    private EditBox subjectField;
    private EditBox bodyField;
    private EditBox currencyField;

    private final String prefilledRecipient;
    private final String prefilledSubject;
    private boolean broadcastMode;

    private String errorMessage = null;

    public MailComposeScreen(String recipient) {
        this(recipient, "", false);
    }

    public MailComposeScreen(String recipient, String subject) {
        this(recipient, subject, false);
    }

    public MailComposeScreen(String recipient, String subject, boolean broadcast) {
        super(Component.literal("Compose Mail"));
        this.prefilledRecipient = recipient;
        this.prefilledSubject = subject;
        this.broadcastMode = broadcast;
        this.guiWidth = 320;
        this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        int fieldX = guiLeft + 70;
        int fieldWidth = guiWidth - 85;
        int y = guiTop + 30;

        // Broadcast toggle button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 90, guiTop + 6, 80, 14,
            Component.literal(broadcastMode ? "§d📢 Broadcast" : "§7📢 Broadcast"),
            btn -> toggleBroadcast()
        ));

        // Recipient field (hidden in broadcast mode)
        recipientField = new EditBox(this.font, fieldX, y, fieldWidth, 18, Component.literal("To"));
        recipientField.setMaxLength(32);
        if (prefilledRecipient != null && !prefilledRecipient.isEmpty()) {
            recipientField.setValue(prefilledRecipient);
        }
        recipientField.setHint(Component.literal("Player name..."));
        recipientField.setEditable(!broadcastMode);
        recipientField.visible = !broadcastMode;
        this.addRenderableWidget(recipientField);
        y += 24;

        // Subject field
        subjectField = new EditBox(this.font, fieldX, y, fieldWidth, 18, Component.literal("Subject"));
        subjectField.setMaxLength(64);
        if (prefilledSubject != null && !prefilledSubject.isEmpty()) {
            subjectField.setValue(prefilledSubject);
        }
        subjectField.setHint(Component.literal("Subject..."));
        this.addRenderableWidget(subjectField);
        y += 28;

        // Body field (multi-line simulation with larger box)
        bodyField = new EditBox(this.font, guiLeft + 15, y, guiWidth - 30, 100, Component.literal("Message"));
        bodyField.setMaxLength(500);
        bodyField.setHint(Component.literal("Write your message..."));
        this.addRenderableWidget(bodyField);
        y += 105;

        // Currency attachment field (not available in broadcast mode)
        currencyField = new EditBox(this.font, fieldX, y, fieldWidth / 2, 18, Component.literal("Amount"));
        currencyField.setMaxLength(12);
        currencyField.setHint(Component.literal("0.00"));
        currencyField.setFilter(s -> s.matches("[0-9.]*")); // Numbers and decimal only
        currencyField.setEditable(!broadcastMode);
        currencyField.visible = !broadcastMode;
        this.addRenderableWidget(currencyField);

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 80;

        // Send button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - buttonWidth - 10, buttonY, buttonWidth, 20,
            Component.literal(broadcastMode ? "§d📢 Broadcast" : "Send"),
            btn -> sendMail()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 10, buttonY, buttonWidth, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Title
        String title = broadcastMode ? "§d📢 §fBroadcast to Nation" : "§6✉ §fCompose New Mail";
        graphics.drawCenteredString(this.font, title, this.width / 2, guiTop + 10, COLOR_PRIMARY);

        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Field labels
        int labelX = guiLeft + 15;
        if (broadcastMode) {
            graphics.drawString(this.font, "§dTo:", labelX, guiTop + 34, COLOR_TEXT);
            graphics.drawString(this.font, "§d§oAll Nation Members", guiLeft + 70, guiTop + 34, COLOR_TEXT);
        } else {
            graphics.drawString(this.font, "§7To:", labelX, guiTop + 34, COLOR_TEXT);
        }
        graphics.drawString(this.font, "§7Subject:", labelX, guiTop + 58, COLOR_TEXT);
        graphics.drawString(this.font, "§7Message:", labelX, guiTop + 78, COLOR_TEXT);
        if (!broadcastMode) {
            graphics.drawString(this.font, "§7Attach $:", labelX, guiTop + 188, COLOR_TEXT);
        } else {
            graphics.drawString(this.font, "§8Currency attachments not available for broadcasts", labelX, guiTop + 188, COLOR_SECONDARY);
        }

        // Error message
        if (errorMessage != null) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + guiHeight - 45, COLOR_TEXT);
        }

        // Character count for body
        int remaining = 500 - bodyField.getValue().length();
        String countText = "§8" + remaining + " characters remaining";
        graphics.drawString(this.font, countText, guiLeft + 15, guiTop + guiHeight - 58, COLOR_SECONDARY);
    }

    private void toggleBroadcast() {
        this.broadcastMode = !this.broadcastMode;
        this.errorMessage = null;
        this.rebuildWidgets();
    }

    private void sendMail() {
        String subject = subjectField.getValue().trim();
        String body = bodyField.getValue().trim();

        if (subject.isEmpty()) {
            errorMessage = "Please enter a subject!";
            return;
        }

        if (body.isEmpty()) {
            errorMessage = "Please enter a message!";
            return;
        }

        if (broadcastMode) {
            // Broadcast to all nation members — no recipient or currency needed
            NetworkHandler.sendToServer(new SendMailPacket("", subject, body, 0, true));
            goBack();
            return;
        }

        // Individual mail mode
        String recipient = recipientField.getValue().trim();

        if (recipient.isEmpty()) {
            errorMessage = "Please enter a recipient!";
            return;
        }

        // Parse currency attachment
        double currency = 0;
        String currencyText = currencyField.getValue().trim();
        if (!currencyText.isEmpty()) {
            try {
                currency = Double.parseDouble(currencyText);
                if (currency < 0) {
                    errorMessage = "Currency amount cannot be negative!";
                    return;
                }
                if (currency > 0 && currency < 0.01) {
                    errorMessage = "Minimum attachment is $0.01!";
                    return;
                }
                // Round to 2 decimal places
                currency = Math.round(currency * 100.0) / 100.0;
            } catch (NumberFormatException e) {
                errorMessage = "Invalid currency amount!";
                return;
            }
        }

        // Send to server
        NetworkHandler.sendToServer(new SendMailPacket(recipient, subject, body, currency));

        // Go back to inbox
        goBack();
    }

    private void goBack() {
        this.minecraft.setScreen(new MailInboxScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }
}

