package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestGovMailDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for viewing government entity mailboxes (Nation, State, City)
 */
public class GovMailboxScreen extends StateCraftScreen {

    public enum EntityType {
        NATION, STATE, CITY, COMPANY
    }

    private final EntityType entityType;
    private final String entityName;
    private final String parentInfo; // For navigation back

    // Mail data
    private boolean dataLoaded = false;
    private List<MailEntry> mailEntries = new ArrayList<>();
    private int unreadCount = 0;
    private int totalCount = 0;
    private int scrollOffset = 0;
    private static final int VISIBLE_ENTRIES = 4; // Reduced to prevent overlap with buttons
    private static final int ENTRY_HEIGHT = 26;

    public GovMailboxScreen(EntityType entityType, String entityName, String parentInfo) {
        super(Component.literal(getTitle(entityType, entityName)));
        this.entityType = entityType;
        this.entityName = entityName;
        this.parentInfo = parentInfo;
        this.guiWidth = 300;
        this.guiHeight = 240; // Increased height for better spacing
    }

    private static String getTitle(EntityType type, String name) {
        return switch (type) {
            case NATION -> "Nation Mailbox: " + name;
            case STATE -> "State Mailbox: " + name;
            case CITY -> "City Mailbox: " + name;
            case COMPANY -> "Company Mailbox: " + name;
        };
    }

    @Override
    protected void init() {
        super.init();

        // Request mail data from server
        RequestGovMailDataPacket.EntityType packetEntityType = switch (entityType) {
            case NATION -> RequestGovMailDataPacket.EntityType.NATION;
            case STATE -> RequestGovMailDataPacket.EntityType.STATE;
            case CITY -> RequestGovMailDataPacket.EntityType.CITY;
            case COMPANY -> RequestGovMailDataPacket.EntityType.COMPANY;
        };
        NetworkHandler.sendToServer(new RequestGovMailDataPacket(packetEntityType, entityName));

        int buttonY = guiTop + guiHeight - 30;

        // Compose button
        this.addRenderableWidget(createButton(
            guiLeft + 10, buttonY, 70, 20,
            Component.literal("Compose"),
            btn -> openComposeScreen()
        ));

        // Mark All Read button
        this.addRenderableWidget(createButton(
            guiLeft + 85, buttonY, 80, 20,
            Component.literal("Mark Read"),
            btn -> markAllRead()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 70, buttonY, 60, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int y = guiTop + 30;
        int leftCol = guiLeft + 15;

        // Header with unread count
        String headerText = String.format("§6%s Mail §7(%d unread / %d total)",
            entityType.name().charAt(0) + entityType.name().substring(1).toLowerCase(),
            unreadCount, totalCount);
        graphics.drawString(this.font, headerText, leftCol, y, COLOR_PRIMARY);
        y += 16;

        renderDivider(graphics, guiLeft + 10, y - 2, guiWidth - 20);
        y += 6;

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, y + 40, COLOR_TEXT);
            return;
        }

        if (mailEntries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No messages", this.width / 2, y + 40, 0xFF888888);
            String emptyMessage = entityType == EntityType.COMPANY
                ? "§7Corporate mail will appear here"
                : "§7Government mail will appear here";
            graphics.drawCenteredString(this.font, emptyMessage, this.width / 2, y + 55, COLOR_SECONDARY);
            return;
        }

        // Render mail entries
        int listEndY = guiTop + guiHeight - 55; // Stop before buttons
        for (int i = scrollOffset; i < Math.min(scrollOffset + VISIBLE_ENTRIES, mailEntries.size()); i++) {
            if (y + ENTRY_HEIGHT > listEndY) break; // Don't render beyond button area
            MailEntry entry = mailEntries.get(i);
            renderMailEntry(graphics, leftCol, y, guiWidth - 30, ENTRY_HEIGHT - 2, entry, mouseX, mouseY);
            y += ENTRY_HEIGHT;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ More above", this.width / 2, guiTop + 28, COLOR_SECONDARY);
        }
        if (scrollOffset + VISIBLE_ENTRIES < mailEntries.size()) {
            graphics.drawCenteredString(this.font, "§7▼ More below", this.width / 2, listEndY + 2, COLOR_SECONDARY);
        }
    }

    private void renderMailEntry(GuiGraphics graphics, int x, int y, int width, int height,
                                  MailEntry entry, int mouseX, int mouseY) {
        // Background
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int bgColor = hovered ? 0x40FFFFFF : 0x20FFFFFF;
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Unread indicator
        String readIndicator = entry.read ? "§7" : "§e● ";

        // Subject line
        String subject = readIndicator + (entry.read ? "§7" : "§f") + entry.subject;
        if (this.font.width(subject) > width - 10) {
            subject = subject.substring(0, Math.min(subject.length(), 30)) + "...";
        }
        graphics.drawString(this.font, subject, x + 4, y + 3, COLOR_TEXT);

        // From and time
        String fromTime = "§8From: " + entry.senderName + " • " + entry.timeAgo;
        graphics.drawString(this.font, fromTime, x + 4, y + 13, 0xFF888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicking on a mail entry
        int y = guiTop + 52;
        int leftCol = guiLeft + 15;
        int width = guiWidth - 30;
        int listEndY = guiTop + guiHeight - 55;

        for (int i = scrollOffset; i < Math.min(scrollOffset + VISIBLE_ENTRIES, mailEntries.size()); i++) {
            if (y + ENTRY_HEIGHT > listEndY) break;
            if (mouseX >= leftCol && mouseX < leftCol + width &&
                mouseY >= y && mouseY < y + ENTRY_HEIGHT - 2) {
                // Open this mail
                openMailView(mailEntries.get(i));
                return true;
            }
            y += ENTRY_HEIGHT;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + VISIBLE_ENTRIES < mailEntries.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openMailView(MailEntry entry) {
        // Mark as read locally
        entry.read = true;
        unreadCount = Math.max(0, unreadCount - 1);

        // Open the mail view screen
        this.minecraft.setScreen(new GovMailViewScreen(
            entityType, entityName, parentInfo,
            entry.mailId, entry.subject, entry.body,
            entry.senderName, entry.timeAgo, entry.type
        ));
    }

    private void openComposeScreen() {
        // TODO: Open compose screen for government mail
        if (this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(
                Component.literal("§7Compose screen coming soon"));
        }
    }

    private void markAllRead() {
        // Send packet to mark all government mail as read
        RequestGovMailDataPacket.EntityType packetEntityType = switch (entityType) {
            case NATION -> RequestGovMailDataPacket.EntityType.NATION;
            case STATE -> RequestGovMailDataPacket.EntityType.STATE;
            case CITY -> RequestGovMailDataPacket.EntityType.CITY;
            case COMPANY -> RequestGovMailDataPacket.EntityType.COMPANY;
        };
        NetworkHandler.sendToServer(new RequestGovMailDataPacket(packetEntityType, entityName,
            RequestGovMailDataPacket.Action.MARK_ALL_READ));

        // Update local state
        for (MailEntry entry : mailEntries) {
            entry.read = true;
        }
        unreadCount = 0;
    }

    private void goBack() {
        switch (entityType) {
            case NATION -> this.minecraft.setScreen(new NationInfoScreen(entityName));
            case STATE -> {
                String[] parts = parentInfo.split(":");
                if (parts.length >= 1) {
                    this.minecraft.setScreen(new StateInfoScreen(parts[0], entityName));
                } else {
                    this.minecraft.setScreen(new MainMenuScreen());
                }
            }
            case CITY -> {
                String[] parts = parentInfo.split(":");
                if (parts.length >= 2) {
                    this.minecraft.setScreen(new CityInfoScreen(parts[0], parts[1], entityName));
                } else {
                    this.minecraft.setScreen(new MainMenuScreen());
                }
            }
            case COMPANY -> this.minecraft.setScreen(new CompanyListScreen());
        }
    }

    /**
     * Update mail data from server
     */
    public void updateMailData(List<MailEntry> entries, int unread, int total) {
        this.mailEntries = new ArrayList<>(entries);
        this.unreadCount = unread;
        this.totalCount = total;
        this.dataLoaded = true;
    }

    /**
     * Mail entry data class
     */
    public static class MailEntry {
        public final String mailId;
        public final String subject;
        public final String body;
        public final String senderName;
        public final String timeAgo;
        public final String type;
        public boolean read;

        public MailEntry(String mailId, String subject, String body, String senderName,
                        String timeAgo, String type, boolean read) {
            this.mailId = mailId;
            this.subject = subject;
            this.body = body;
            this.senderName = senderName;
            this.timeAgo = timeAgo;
            this.type = type;
            this.read = read;
        }
    }
}

