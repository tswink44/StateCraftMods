package com.statecraft.client.gui;

import com.statecraft.network.packets.SyncElectionDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Screen showing past election history
 */
public class ElectionHistoryScreen extends Screen {

    private final List<SyncElectionDataPacket.HistoryEntry> history;
    private final String nationName;
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 45;
    private static final int MAX_VISIBLE_ENTRIES = 5;

    public ElectionHistoryScreen(List<SyncElectionDataPacket.HistoryEntry> history, String nationName) {
        super(Component.literal("Election History"));
        this.history = history;
        this.nationName = nationName;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(centerX - 50, this.height - 30, 100, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int centerX = this.width / 2;
        int startY = 20;

        // Title
        graphics.drawCenteredString(this.font, "§6" + nationName + " - Election History", centerX, startY, 0xFFFFFF);
        startY += 25;

        if (history.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No past elections recorded", centerX, startY + 50, 0xAAAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Render history entries
        int listX = centerX - 120;
        int listWidth = 240;
        int visibleCount = Math.min(history.size() - scrollOffset, MAX_VISIBLE_ENTRIES);

        for (int i = scrollOffset; i < scrollOffset + visibleCount && i < history.size(); i++) {
            SyncElectionDataPacket.HistoryEntry entry = history.get(i);
            int itemY = startY + (i - scrollOffset) * ENTRY_HEIGHT;

            // Background
            graphics.fill(listX, itemY, listX + listWidth, itemY + ENTRY_HEIGHT - 5, 0x80222222);

            // Election number (most recent = 1)
            int electionNumber = i + 1;
            graphics.drawString(this.font, "§7#" + electionNumber, listX + 5, itemY + 5, 0xFFFFFF);

            // Winner
            graphics.drawString(this.font, "§aWinner: §f" + entry.winnerName(), listX + 30, itemY + 5, 0xFFFFFF);

            // Vote info
            String voteInfo = "§7Votes: " + entry.voteCount() + "/" + entry.totalVoters();
            double percentage = entry.totalVoters() > 0
                ? (entry.voteCount() * 100.0 / entry.totalVoters())
                : 0;
            voteInfo += " (" + String.format("%.1f", percentage) + "%)";
            graphics.drawString(this.font, voteInfo, listX + 30, itemY + 17, 0xFFFFFF);

            // Time ago
            long daysAgo = (System.currentTimeMillis() - entry.timestamp()) / (24 * 60 * 60 * 1000);
            String timeAgo;
            if (daysAgo == 0) {
                long hoursAgo = (System.currentTimeMillis() - entry.timestamp()) / (60 * 60 * 1000);
                timeAgo = hoursAgo + " hours ago";
            } else if (daysAgo == 1) {
                timeAgo = "1 day ago";
            } else {
                timeAgo = daysAgo + " days ago";
            }
            graphics.drawString(this.font, "§8" + timeAgo, listX + 30, itemY + 29, 0xFFFFFF);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ Scroll up for more", centerX, startY - 12, 0xAAAAAA);
        }
        if (scrollOffset + MAX_VISIBLE_ENTRIES < history.size()) {
            int bottomY = startY + MAX_VISIBLE_ENTRIES * ENTRY_HEIGHT;
            graphics.drawCenteredString(this.font, "§7▼ Scroll down for more", centerX, bottomY, 0xAAAAAA);
        }

        // Total elections count
        graphics.drawCenteredString(this.font, "§7Total elections: " + history.size(), centerX, this.height - 50, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, history.size() - MAX_VISIBLE_ENTRIES);
        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(new ElectionScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

