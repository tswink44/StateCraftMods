package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.InvitationActionPacket;
import com.statecraft.network.packets.RequestInvitationsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for viewing and managing pending invitations
 */
public class InvitationsScreen extends StateCraftScreen {

    private List<InvitationData> invitations = new ArrayList<>();
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 5;
    private static final int ITEM_HEIGHT = 28;

    public InvitationsScreen() {
        super(Component.literal("Invitations"));
        this.guiWidth = 280; this.guiHeight = 200;
    }

    @Override
    protected void init() {
        super.init();

        // Request invitations from server
        NetworkHandler.sendToServer(new RequestInvitationsPacket());

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Clear existing dynamic buttons
        this.clearWidgets();

        // Re-add back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));

        // Divider
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (invitations.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No pending invitations", this.width / 2, guiTop + 70, COLOR_TEXT);
            graphics.drawCenteredString(this.font, "§8Ask a nation leader to invite you!", this.width / 2, guiTop + 85, 0xFFAAAAAA);
            return;
        }

        // Render invitation list
        int listY = guiTop + 30;
        int listWidth = guiWidth - 30;

        for (int i = scrollOffset; i < Math.min(scrollOffset + ITEMS_PER_PAGE, invitations.size()); i++) {
            InvitationData invite = invitations.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            // Item background
            renderSubPanel(graphics, guiLeft + 15, itemY, listWidth, ITEM_HEIGHT - 2);

            // Invitation type icon
            String typeIcon = invite.type.equals("NATION") ? "§6[N]" : "§b[C]";
            graphics.drawString(this.font, typeIcon, guiLeft + 20, itemY + 5, COLOR_TEXT);

            // Entity name
            graphics.drawString(this.font, "§f" + invite.entityName, guiLeft + 42, itemY + 5, COLOR_TEXT);

            // Time remaining
            String timeStr = formatTime(invite.remainingSeconds);
            graphics.drawString(this.font, "§7" + timeStr, guiLeft + 42, itemY + 15, 0xFFAAAAAA);

            // Accept button
            final int index = i;
            Button acceptBtn = createSmallButton(
                guiLeft + listWidth - 55, itemY + 5,
                Component.literal("§aAccept"),
                btn -> acceptInvitation(index)
            );
            this.addRenderableWidget(acceptBtn);

            // Deny button
            Button denyBtn = createSmallButton(
                guiLeft + listWidth + 10, itemY + 5,
                Component.literal("§cDeny"),
                btn -> denyInvitation(index)
            );
            this.addRenderableWidget(denyBtn);
        }

        // Scroll indicators
        if (invitations.size() > ITEMS_PER_PAGE) {
            int totalPages = (int) Math.ceil(invitations.size() / (double) ITEMS_PER_PAGE);
            int currentPage = scrollOffset / ITEMS_PER_PAGE + 1;
            graphics.drawCenteredString(this.font,
                "§7Page " + currentPage + "/" + totalPages,
                this.width / 2, guiTop + guiHeight - 45, COLOR_TEXT);

            // Scroll buttons
            if (scrollOffset > 0) {
                this.addRenderableWidget(createButton(
                    guiLeft + 15, guiTop + guiHeight - 50, 20, 16,
                    Component.literal("▲"),
                    btn -> scroll(-ITEMS_PER_PAGE)
                ));
            }
            if (scrollOffset + ITEMS_PER_PAGE < invitations.size()) {
                this.addRenderableWidget(createButton(
                    guiLeft + guiWidth - 35, guiTop + guiHeight - 50, 20, 16,
                    Component.literal("▼"),
                    btn -> scroll(ITEMS_PER_PAGE)
                ));
            }
        }
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "Expired";
        if (seconds < 60) return seconds + "s remaining";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m " + secs + "s remaining";
    }

    private void scroll(int amount) {
        scrollOffset = Math.max(0, Math.min(scrollOffset + amount, invitations.size() - ITEMS_PER_PAGE));
    }

    private void acceptInvitation(int index) {
        if (index >= 0 && index < invitations.size()) {
            InvitationData invite = invitations.get(index);
            NetworkHandler.sendToServer(new InvitationActionPacket(invite.id, true));
            invitations.remove(index);
        }
    }

    private void denyInvitation(int index) {
        if (index >= 0 && index < invitations.size()) {
            InvitationData invite = invitations.get(index);
            NetworkHandler.sendToServer(new InvitationActionPacket(invite.id, false));
            invitations.remove(index);
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scroll(-1);
        } else if (delta < 0) {
            scroll(1);
        }
        return true;
    }

    // Called by network handler when data is received
    public void updateInvitations(List<InvitationData> invites) {
        this.invitations = invites;
        this.dataLoaded = true;
        this.scrollOffset = 0;
    }

    // Called when an invitation action is processed
    public void onInvitationAccepted(String nationName) {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    /**
     * Data class for invitation info
     */
    public static class InvitationData {
        public String id;
        public String type;
        public String entityName;
        public String senderName;
        public long remainingSeconds;

        public InvitationData(String id, String type, String entityName, String senderName, long remaining) {
            this.id = id;
            this.type = type;
            this.entityName = entityName;
            this.senderName = senderName;
            this.remainingSeconds = remaining;
        }
    }
}

