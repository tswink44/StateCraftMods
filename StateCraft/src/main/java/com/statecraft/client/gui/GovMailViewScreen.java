package com.statecraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for viewing a single government mail message
 */
public class GovMailViewScreen extends StateCraftScreen {

    private final GovMailboxScreen.EntityType entityType;
    private final String entityName;
    private final String parentInfo;

    private final String mailId;
    private final String subject;
    private final String body;
    private final String senderName;
    private final String timeAgo;
    private final String mailType;

    private List<String> wrappedBody;
    private int scrollOffset = 0;
    private static final int LINES_VISIBLE = 10;

    public GovMailViewScreen(GovMailboxScreen.EntityType entityType, String entityName, String parentInfo,
                             String mailId, String subject, String body,
                             String senderName, String timeAgo, String mailType) {
        super(Component.literal("View Mail"));
        this.entityType = entityType;
        this.entityName = entityName;
        this.parentInfo = parentInfo;
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

        // Wrap body text
        wrappedBody = wrapText(body, guiWidth - 30);

        int buttonY = guiTop + guiHeight - 30;

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, buttonY, 80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = guiTop + 8;
        int x = guiLeft + 15;

        // Type indicator and subject
        String typeColor = getTypeColor(mailType);
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

    private String getTypeColor(String type) {
        if (type == null) return "§7";
        return switch (type) {
            case "GOV_TAX_REVENUE" -> "§2";
            case "TAX_SUMMARY", "TAX_WARNING" -> "§e";
            case "TAX_REPOSSESSION" -> "§c";
            case "CHUNK_PURCHASE", "CHUNK_SALE" -> "§a";
            case "SYSTEM" -> "§b";
            default -> "§7";
        };
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
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                // Check if whole string fits
                if (this.font.width(remaining) <= maxWidth) {
                    lines.add(remaining);
                    break;
                }

                // Find break point
                int breakIndex = remaining.length();
                while (breakIndex > 0 && this.font.width(remaining.substring(0, breakIndex)) > maxWidth) {
                    breakIndex--;
                }

                // Try to break at space
                int spaceIndex = remaining.lastIndexOf(' ', breakIndex);
                if (spaceIndex > 0) {
                    breakIndex = spaceIndex;
                }

                lines.add(remaining.substring(0, breakIndex));
                remaining = remaining.substring(breakIndex).stripLeading();
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

    private void goBack() {
        this.minecraft.setScreen(new GovMailboxScreen(entityType, entityName, parentInfo));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

