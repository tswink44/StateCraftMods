package com.statecraft.client.gui;

import com.statecraft.mail.Mail;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestMailDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for viewing a single mail message
 */
public class MailViewScreen extends StateCraftScreen {

    private final String mailId;
    private final String subject;
    private final String body;
    private final String senderName;
    private final String timeAgo;
    private final Mail.MailType mailType;
    private final double attachedCurrency;
    private final boolean currencyClaimed;
    private final String actionData;      // For actionable mail (nation ID for invites)
    private final boolean actionTaken;    // Whether action already taken

    private List<String> wrappedBody;
    private int scrollOffset = 0;
    private static final int LINES_VISIBLE = 10;

    public MailViewScreen(String mailId, String subject, String body,
                          String senderName, String timeAgo, Mail.MailType mailType,
                          double attachedCurrency, boolean currencyClaimed,
                          String actionData, boolean actionTaken) {
        super(Component.literal("View Mail"));
        this.mailId = mailId;
        this.subject = subject;
        this.body = body;
        this.senderName = senderName;
        this.timeAgo = timeAgo;
        this.mailType = mailType;
        this.attachedCurrency = attachedCurrency;
        this.currencyClaimed = currencyClaimed;
        this.actionData = actionData;
        this.actionTaken = actionTaken;
        this.guiWidth = 320;
        this.guiHeight = 240;
    }

    /** Backward-compatible constructor for code that doesn't pass action fields */
    public MailViewScreen(String mailId, String subject, String body,
                          String senderName, String timeAgo, Mail.MailType mailType,
                          double attachedCurrency, boolean currencyClaimed) {
        this(mailId, subject, body, senderName, timeAgo, mailType, attachedCurrency, currencyClaimed, null, false);
    }

    /** Backward-compatible constructor for code that doesn't pass currency fields */
    public MailViewScreen(String mailId, String subject, String body,
                          String senderName, String timeAgo, Mail.MailType mailType) {
        this(mailId, subject, body, senderName, timeAgo, mailType, 0, false, null, false);
    }

    @Override
    protected void init() {
        super.init();

        // Mark as read
        NetworkHandler.sendToServer(new RequestMailDataPacket(RequestMailDataPacket.Action.MARK_READ, mailId));

        // Wrap body text
        wrappedBody = wrapText(body, guiWidth - 30);

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 70;
        int spacing = 80;
        int startX = guiLeft + 20;

        // Reply button
        this.addRenderableWidget(createButton(
            startX, buttonY, buttonWidth, 20,
            Component.literal("Reply"),
            btn -> openReply()
        ));

        // Delete button
        this.addRenderableWidget(createButton(
            startX + spacing, buttonY, buttonWidth, 20,
            Component.literal("Delete"),
            btn -> deleteMail()
        ));

        // Archive button
        this.addRenderableWidget(createButton(
            startX + spacing * 2, buttonY, buttonWidth, 20,
            Component.literal("Archive"),
            btn -> archiveMail()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 65, buttonY, 55, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));

        // Claim Currency button (if applicable)
        if (attachedCurrency > 0 && !currencyClaimed) {
            this.addRenderableWidget(createButton(
                guiLeft + 10, buttonY - 24, guiWidth - 20, 20,
                Component.literal("§a💰 Claim $" + String.format("%.2f", attachedCurrency)),
                btn -> claimCurrency()
            ));
        }

        // Accept/Deny buttons for actionable mail (nation invites, etc.)
        if (isActionableMail() && !actionTaken && actionData != null) {
            int actionButtonY = buttonY - 24;
            if (attachedCurrency > 0 && !currencyClaimed) {
                actionButtonY -= 24; // Move up if currency claim button exists
            }

            // Accept button
            this.addRenderableWidget(createButton(
                guiLeft + 10, actionButtonY, (guiWidth - 30) / 2, 20,
                Component.literal("§a✓ Accept"),
                btn -> acceptInvite()
            ));

            // Deny button
            this.addRenderableWidget(createButton(
                guiLeft + 15 + (guiWidth - 30) / 2, actionButtonY, (guiWidth - 30) / 2, 20,
                Component.literal("§c✗ Deny"),
                btn -> denyInvite()
            ));
        } else if (isActionableMail() && actionTaken) {
            // Show status that action was already taken
            // This is handled in renderContent
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = guiTop + 8;
        int x = guiLeft + 15;
        int maxWidth = guiWidth - 30;

        // Type indicator and subject
        String typeColor = mailType.getColorCode();
        graphics.drawString(this.font, typeColor + "● §f" + subject, x, y, COLOR_PRIMARY);
        y += 14;

        // Sender and time
        graphics.drawString(this.font, "§7From: §f" + senderName, x, y, COLOR_TEXT);
        y += 11;
        graphics.drawString(this.font, "§8" + timeAgo, x, y, COLOR_SECONDARY);
        y += 4;

        // Currency attachment indicator
        if (attachedCurrency > 0) {
            y += 10;
            if (currencyClaimed) {
                graphics.drawString(this.font, "§8💰 $" + String.format("%.2f", attachedCurrency) + " (claimed)", x, y, COLOR_SECONDARY);
            } else {
                graphics.drawString(this.font, "§a💰 $" + String.format("%.2f", attachedCurrency) + " attached §7— click Claim below!", x, y, COLOR_TEXT);
            }
        }
        y += 10;

        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 8;

        // Body
        int bodyStartY = y;
        int lineHeight = 11;

        for (int i = scrollOffset; i < wrappedBody.size() && i < scrollOffset + LINES_VISIBLE; i++) {
            graphics.drawString(this.font, wrappedBody.get(i), x, y, COLOR_TEXT);
            y += lineHeight;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawString(this.font, "§7▲", guiLeft + guiWidth - 20, bodyStartY, COLOR_SECONDARY);
        }
        if (scrollOffset + LINES_VISIBLE < wrappedBody.size()) {
            graphics.drawString(this.font, "§7▼", guiLeft + guiWidth - 20, bodyStartY + (LINES_VISIBLE - 1) * lineHeight, COLOR_SECONDARY);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        // Split by explicit newlines first
        String[] paragraphs = text.split("\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }

            // Wrap each paragraph
            StringBuilder currentLine = new StringBuilder();
            String[] words = paragraph.split(" ");

            for (String word : words) {
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                // Remove color codes for width calculation
                String cleanTest = testLine.replaceAll("§.", "");

                if (this.font.width(cleanTest) <= maxWidth) {
                    if (currentLine.length() > 0) currentLine.append(" ");
                    currentLine.append(word);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                    }
                    currentLine = new StringBuilder(word);
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + LINES_VISIBLE < wrappedBody.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openReply() {
        // Open compose screen with recipient pre-filled
        this.minecraft.setScreen(new MailComposeScreen(senderName, "Re: " + subject));
    }

    private void claimCurrency() {
        NetworkHandler.sendToServer(new RequestMailDataPacket(RequestMailDataPacket.Action.CLAIM_CURRENCY, mailId));
        goBack();
    }

    private void deleteMail() {
        NetworkHandler.sendToServer(new RequestMailDataPacket(RequestMailDataPacket.Action.DELETE, mailId));
        goBack();
    }

    private void archiveMail() {
        NetworkHandler.sendToServer(new RequestMailDataPacket(RequestMailDataPacket.Action.ARCHIVE, mailId));
        goBack();
    }

    private void goBack() {
        this.minecraft.setScreen(new MailInboxScreen());
    }

    /**
     * Check if this mail type supports actions (accept/deny buttons)
     */
    private boolean isActionableMail() {
        return mailType == Mail.MailType.NATION_INVITE || mailType == Mail.MailType.CITIZENSHIP_INVITE;
    }

    /**
     * Accept the invite action
     */
    private void acceptInvite() {
        if (actionData != null && !actionData.isEmpty()) {
            NetworkHandler.sendToServer(new RequestMailDataPacket(
                RequestMailDataPacket.Action.ACCEPT_INVITE, mailId, actionData));
            goBack();
        }
    }

    /**
     * Deny the invite action
     */
    private void denyInvite() {
        if (actionData != null && !actionData.isEmpty()) {
            NetworkHandler.sendToServer(new RequestMailDataPacket(
                RequestMailDataPacket.Action.DENY_INVITE, mailId, actionData));
            goBack();
        }
    }

    @Override
    public void onClose() {
        goBack();
    }
}
