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

    private List<String> wrappedBody;
    private int scrollOffset = 0;
    private static final int LINES_VISIBLE = 10;

    public MailViewScreen(String mailId, String subject, String body,
                          String senderName, String timeAgo, Mail.MailType mailType) {
        super(Component.literal("View Mail"));
        this.mailId = mailId;
        this.subject = subject;
        this.body = body;
        this.senderName = senderName;
        this.timeAgo = timeAgo;
        this.mailType = mailType;
        this.guiWidth = 320;
        this.guiHeight = 240;
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
        y += 14;

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

    @Override
    public void onClose() {
        goBack();
    }
}

