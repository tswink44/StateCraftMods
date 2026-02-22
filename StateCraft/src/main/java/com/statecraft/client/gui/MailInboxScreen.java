package com.statecraft.client.gui;

import com.statecraft.mail.Mail;
import com.statecraft.mail.Mailbox;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestMailDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mail inbox screen showing list of mail messages
 */
public class MailInboxScreen extends StateCraftScreen {

    private List<MailEntry> mailEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private boolean dataLoaded = false;

    // Filter options
    private boolean showUnreadOnly = false;
    private Mail.MailType filterType = null;

    // Layout constants
    private static final int ENTRY_HEIGHT = 24;
    private static final int VISIBLE_ENTRIES = 8;
    private int unreadCount = 0;
    private int totalCount = 0;

    public MailInboxScreen() {
        super(Component.literal("Mail"));
        this.guiWidth = 320;
        this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        // Request mail data from server
        NetworkHandler.sendToServer(new RequestMailDataPacket());

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 70;
        int spacing = 75;
        int startX = guiLeft + 15;

        // Compose button
        this.addRenderableWidget(createButton(
            startX, buttonY, buttonWidth, 20,
            Component.literal("Compose"),
            btn -> openComposeScreen()
        ));

        // Filter button
        this.addRenderableWidget(createButton(
            startX + spacing, buttonY, buttonWidth, 20,
            Component.literal(showUnreadOnly ? "All Mail" : "Unread"),
            btn -> toggleFilter()
        ));

        // Mark All Read button
        this.addRenderableWidget(createButton(
            startX + spacing * 2, buttonY, buttonWidth, 20,
            Component.literal("Mark Read"),
            btn -> markAllRead()
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
        // Draw header with mail icon
        String headerText = "§6✉ §fMail Inbox";
        graphics.drawString(this.font, headerText, guiLeft + 15, guiTop + 8, COLOR_PRIMARY);

        // Draw unread count
        String countText = "§7" + unreadCount + " unread / " + totalCount + " total";
        int countWidth = this.font.width(countText.replaceAll("§.", ""));
        graphics.drawString(this.font, countText, guiLeft + guiWidth - countWidth - 15, guiTop + 8, COLOR_TEXT);

        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        if (mailEntries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7Your mailbox is empty.", this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        // Draw mail entries
        int listY = guiTop + 28;
        int listX = guiLeft + 12;
        int listWidth = guiWidth - 24;

        for (int i = 0; i < VISIBLE_ENTRIES && i + scrollOffset < mailEntries.size(); i++) {
            int index = i + scrollOffset;
            MailEntry entry = mailEntries.get(index);
            int entryY = listY + i * ENTRY_HEIGHT;

            // Entry background
            boolean isHovered = mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT - 2;
            boolean isSelected = index == selectedIndex;

            int bgColor = isSelected ? 0x80404060 : (isHovered ? 0x60303050 : 0x40202030);
            graphics.fill(listX, entryY, listX + listWidth, entryY + ENTRY_HEIGHT - 2, bgColor);

            // Unread indicator
            if (!entry.read) {
                graphics.fill(listX, entryY, listX + 3, entryY + ENTRY_HEIGHT - 2, 0xFFFFAA00);
            }

            // Type indicator
            String typeIndicator = entry.type.getColorCode() + "●";
            graphics.drawString(this.font, typeIndicator, listX + 8, entryY + 3, COLOR_TEXT);

            // Subject (truncated)
            String readColor = entry.read ? "§7" : "§f";
            String currencyPrefix = "";
            if (entry.attachedCurrency > 0 && !entry.currencyClaimed) {
                currencyPrefix = "§a💰 ";
            } else if (entry.attachedCurrency > 0 && entry.currencyClaimed) {
                currencyPrefix = "§8💰 ";
            }
            String subject = entry.subject;
            int maxSubjectWidth = listWidth - 100 - (currencyPrefix.isEmpty() ? 0 : 14);
            if (this.font.width(subject) > maxSubjectWidth) {
                while (this.font.width(subject + "...") > maxSubjectWidth && subject.length() > 0) {
                    subject = subject.substring(0, subject.length() - 1);
                }
                subject += "...";
            }
            graphics.drawString(this.font, currencyPrefix + readColor + subject, listX + 20, entryY + 3, COLOR_TEXT);

            // Sender and time
            String senderInfo = "§8" + entry.senderName;
            String timeInfo = "§8" + entry.timeAgo;
            graphics.drawString(this.font, senderInfo, listX + 20, entryY + 13, COLOR_SECONDARY);

            int timeWidth = this.font.width(timeInfo.replaceAll("§.", ""));
            graphics.drawString(this.font, timeInfo, listX + listWidth - timeWidth - 5, entryY + 13, COLOR_SECONDARY);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ Scroll up", this.width / 2, listY - 8, COLOR_SECONDARY);
        }
        if (scrollOffset + VISIBLE_ENTRIES < mailEntries.size()) {
            int bottomY = listY + VISIBLE_ENTRIES * ENTRY_HEIGHT;
            graphics.drawCenteredString(this.font, "§7▼ Scroll down", this.width / 2, bottomY, COLOR_SECONDARY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded) {
            int listY = guiTop + 28;
            int listX = guiLeft + 12;
            int listWidth = guiWidth - 24;

            for (int i = 0; i < VISIBLE_ENTRIES && i + scrollOffset < mailEntries.size(); i++) {
                int index = i + scrollOffset;
                int entryY = listY + i * ENTRY_HEIGHT;

                if (mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT - 2) {
                    selectedIndex = index;
                    openMailView(mailEntries.get(index));
                    return true;
                }
            }
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

    private void openComposeScreen() {
        this.minecraft.setScreen(new MailComposeScreen(null));
    }

    private void toggleFilter() {
        showUnreadOnly = !showUnreadOnly;
        applyFilter();
        this.rebuildWidgets();
    }

    private void applyFilter() {
        // This would re-request filtered data from server
        // For now just mark for UI update
    }

    private void markAllRead() {
        NetworkHandler.sendToServer(new RequestMailDataPacket(RequestMailDataPacket.Action.MARK_ALL_READ));
        // Update local entries
        for (MailEntry entry : mailEntries) {
            entry.read = true;
        }
        unreadCount = 0;
    }

    private void openMailView(MailEntry entry) {
        this.minecraft.setScreen(new MailViewScreen(entry.mailId, entry.subject, entry.body,
            entry.senderName, entry.timeAgo, entry.type,
            entry.attachedCurrency, entry.currencyClaimed,
            entry.actionData, entry.actionTaken));
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler when mail data is received
     */
    public void updateMailData(List<MailEntry> entries, int unread, int total) {
        this.mailEntries = entries;
        this.unreadCount = unread;
        this.totalCount = total;
        this.dataLoaded = true;
        this.scrollOffset = 0;
        this.selectedIndex = -1;
    }

    /**
     * Simple data class for mail entry display
     */
    public static class MailEntry {
        public final String mailId;
        public final String subject;
        public final String body;
        public final String senderName;
        public final String timeAgo;
        public final Mail.MailType type;
        public boolean read;
        public final double attachedCurrency;
        public final boolean currencyClaimed;
        public final String actionData;
        public final boolean actionTaken;

        public MailEntry(String mailId, String subject, String body, String senderName,
                        String timeAgo, Mail.MailType type, boolean read,
                        double attachedCurrency, boolean currencyClaimed,
                        String actionData, boolean actionTaken) {
            this.mailId = mailId;
            this.subject = subject;
            this.body = body;
            this.senderName = senderName;
            this.timeAgo = timeAgo;
            this.type = type;
            this.read = read;
            this.attachedCurrency = attachedCurrency;
            this.currencyClaimed = currencyClaimed;
            this.actionData = actionData;
            this.actionTaken = actionTaken;
        }

        /** Backward-compatible constructor */
        public MailEntry(String mailId, String subject, String body, String senderName,
                        String timeAgo, Mail.MailType type, boolean read,
                        double attachedCurrency, boolean currencyClaimed) {
            this(mailId, subject, body, senderName, timeAgo, type, read,
                 attachedCurrency, currencyClaimed, "", false);
        }
    }
}

