package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.SendMailPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for composing and sending mail
 */
public class MailComposeScreen extends StateCraftScreen {

    private EditBox recipientField;
    private EditBox subjectField;
    private EditBox bodyField;

    private final String prefilledRecipient;
    private final String prefilledSubject;

    private String errorMessage = null;

    public MailComposeScreen(String recipient) {
        this(recipient, "");
    }

    public MailComposeScreen(String recipient, String subject) {
        super(Component.literal("Compose Mail"));
        this.prefilledRecipient = recipient;
        this.prefilledSubject = subject;
        this.guiWidth = 320;
        this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        int fieldX = guiLeft + 70;
        int fieldWidth = guiWidth - 85;
        int y = guiTop + 30;

        // Recipient field
        recipientField = new EditBox(this.font, fieldX, y, fieldWidth, 18, Component.literal("To"));
        recipientField.setMaxLength(32);
        if (prefilledRecipient != null && !prefilledRecipient.isEmpty()) {
            recipientField.setValue(prefilledRecipient);
        }
        recipientField.setHint(Component.literal("Player name..."));
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

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 80;

        // Send button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - buttonWidth - 10, buttonY, buttonWidth, 20,
            Component.literal("Send"),
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
        graphics.drawCenteredString(this.font, "§6✉ §fCompose New Mail", this.width / 2, guiTop + 10, COLOR_PRIMARY);

        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Field labels
        int labelX = guiLeft + 15;
        graphics.drawString(this.font, "§7To:", labelX, guiTop + 34, COLOR_TEXT);
        graphics.drawString(this.font, "§7Subject:", labelX, guiTop + 58, COLOR_TEXT);
        graphics.drawString(this.font, "§7Message:", labelX, guiTop + 78, COLOR_TEXT);

        // Error message
        if (errorMessage != null) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + guiHeight - 45, COLOR_TEXT);
        }

        // Character count for body
        int remaining = 500 - bodyField.getValue().length();
        String countText = "§8" + remaining + " characters remaining";
        graphics.drawString(this.font, countText, guiLeft + 15, guiTop + guiHeight - 58, COLOR_SECONDARY);
    }

    private void sendMail() {
        String recipient = recipientField.getValue().trim();
        String subject = subjectField.getValue().trim();
        String body = bodyField.getValue().trim();

        // Validation
        if (recipient.isEmpty()) {
            errorMessage = "Please enter a recipient!";
            return;
        }

        if (subject.isEmpty()) {
            errorMessage = "Please enter a subject!";
            return;
        }

        if (body.isEmpty()) {
            errorMessage = "Please enter a message!";
            return;
        }

        // Send to server
        NetworkHandler.sendToServer(new SendMailPacket(recipient, subject, body));

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

