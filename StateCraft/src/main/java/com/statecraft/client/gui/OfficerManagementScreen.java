package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.ModifyOfficerPacket;
import com.statecraft.network.packets.RequestOfficerManagementDataPacket;
import com.statecraft.network.packets.SyncOfficerManagementDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screen for nation leaders to manage officers (appoint/remove)
 * Max 3 officers allowed per nation
 */
public class OfficerManagementScreen extends StateCraftScreen {

    private static final int MAX_OFFICERS = 3;

    private final String nationName;

    // Data from server
    private boolean dataLoaded = false;
    private boolean isLeader = false;
    private List<SyncOfficerManagementDataPacket.CitizenInfo> citizens = new ArrayList<>();
    private List<SyncOfficerManagementDataPacket.OfficerInfo> officers = new ArrayList<>();

    // UI state
    private int citizenScrollOffset = 0;
    private int officerScrollOffset = 0;
    private static final int MAX_VISIBLE = 6;

    // Selected citizen for appointment
    private UUID selectedCitizenId = null;

    public OfficerManagementScreen(String nationName) {
        super(Component.literal("Manage Officers"));
        this.nationName = nationName;
        this.guiWidth = 320;
        this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        // Request data from server
        NetworkHandler.sendToServer(new RequestOfficerManagementDataPacket(nationName));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 70, guiTop + guiHeight - 28, 55, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        if (!isLeader) {
            graphics.drawCenteredString(this.font, "§cOnly the nation leader can manage officers",
                this.width / 2, guiTop + 100, COLOR_WARNING);
            return;
        }

        int leftX = guiLeft + 15;
        int rightX = guiLeft + guiWidth / 2 + 10;
        int y = guiTop + 30;

        // Left side: Current Officers
        renderOfficersSection(graphics, leftX, y, mouseX, mouseY);

        // Right side: Available Citizens
        renderCitizensSection(graphics, rightX, y, mouseX, mouseY);

        // Divider between sections
        int dividerX = guiLeft + guiWidth / 2;
        graphics.fill(dividerX - 1, guiTop + 28, dividerX, guiTop + guiHeight - 35, COLOR_BORDER);
    }

    private void renderOfficersSection(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Header
        String headerColor = officers.size() >= MAX_OFFICERS ? "§c" : "§6";
        graphics.drawString(this.font, headerColor + "Officers §7(" + officers.size() + "/" + MAX_OFFICERS + ")",
            x, y, COLOR_PRIMARY);
        y += 14;

        if (officers.isEmpty()) {
            graphics.drawString(this.font, "§8No officers appointed", x, y + 10, 0xFFAAAAAA);
            return;
        }

        int sectionWidth = guiWidth / 2 - 30;
        int endIndex = Math.min(officerScrollOffset + MAX_VISIBLE, officers.size());

        for (int i = officerScrollOffset; i < endIndex; i++) {
            SyncOfficerManagementDataPacket.OfficerInfo officer = officers.get(i);

            // Online indicator
            String onlineIndicator = officer.isOnline() ? "§a● " : "§8○ ";
            graphics.drawString(this.font, onlineIndicator + "§f" + officer.getPlayerName(), x, y, COLOR_TEXT);

            // Remove button
            int btnX = x + sectionWidth - 35;
            int btnY = y - 2;
            boolean hovered = mouseX >= btnX && mouseX < btnX + 35 && mouseY >= btnY && mouseY < btnY + 12;

            graphics.fill(btnX, btnY, btnX + 35, btnY + 12, hovered ? 0xFFAA3333 : 0xFF662222);
            graphics.drawCenteredString(this.font, "§cRemove", btnX + 17, btnY + 2, COLOR_TEXT);

            y += 16;
        }

        // Scroll indicators
        if (officerScrollOffset > 0) {
            graphics.drawString(this.font, "§7▲", x + sectionWidth - 10, guiTop + 44, 0xFF888888);
        }
        if (officerScrollOffset + MAX_VISIBLE < officers.size()) {
            graphics.drawString(this.font, "§7▼", x + sectionWidth - 10, guiTop + guiHeight - 55, 0xFF888888);
        }
    }

    private void renderCitizensSection(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Header
        graphics.drawString(this.font, "§6Available Citizens", x, y, COLOR_PRIMARY);
        y += 14;

        // Filter out citizens who are already officers
        List<SyncOfficerManagementDataPacket.CitizenInfo> availableCitizens = citizens.stream()
            .filter(c -> officers.stream().noneMatch(o -> o.getPlayerId().equals(c.getPlayerId())))
            .toList();

        if (availableCitizens.isEmpty()) {
            graphics.drawString(this.font, "§8No eligible citizens", x, y + 10, 0xFFAAAAAA);
            return;
        }

        boolean canAppoint = officers.size() < MAX_OFFICERS;
        int sectionWidth = guiWidth / 2 - 30;
        int endIndex = Math.min(citizenScrollOffset + MAX_VISIBLE, availableCitizens.size());

        for (int i = citizenScrollOffset; i < endIndex; i++) {
            SyncOfficerManagementDataPacket.CitizenInfo citizen = availableCitizens.get(i);

            // Online indicator
            String onlineIndicator = citizen.isOnline() ? "§a● " : "§8○ ";
            String governorBadge = citizen.isGovernor() ? " §8[§bGov§8]" : "";
            graphics.drawString(this.font, onlineIndicator + "§f" + citizen.getPlayerName() + governorBadge, x, y, COLOR_TEXT);

            // Appoint button (only if we can appoint more)
            if (canAppoint) {
                int btnX = x + sectionWidth - 40;
                int btnY = y - 2;
                boolean hovered = mouseX >= btnX && mouseX < btnX + 40 && mouseY >= btnY && mouseY < btnY + 12;

                graphics.fill(btnX, btnY, btnX + 40, btnY + 12, hovered ? 0xFF33AA33 : 0xFF226622);
                graphics.drawCenteredString(this.font, "§aAppoint", btnX + 20, btnY + 2, COLOR_TEXT);
            }

            y += 16;
        }

        // Scroll indicators
        if (citizenScrollOffset > 0) {
            graphics.drawString(this.font, "§7▲", x + sectionWidth - 10, guiTop + 44, 0xFF888888);
        }
        if (citizenScrollOffset + MAX_VISIBLE < availableCitizens.size()) {
            graphics.drawString(this.font, "§7▼", x + sectionWidth - 10, guiTop + guiHeight - 55, 0xFF888888);
        }

        if (!canAppoint) {
            graphics.drawString(this.font, "§cMax officers reached", x, guiTop + guiHeight - 55, 0xFFAA3333);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded && isLeader) {
            // Check officer remove buttons
            int leftX = guiLeft + 15;
            int sectionWidth = guiWidth / 2 - 30;
            int y = guiTop + 44;

            int endIndex = Math.min(officerScrollOffset + MAX_VISIBLE, officers.size());
            for (int i = officerScrollOffset; i < endIndex; i++) {
                int btnX = leftX + sectionWidth - 35;
                int btnY = y - 2;

                if (mouseX >= btnX && mouseX < btnX + 35 && mouseY >= btnY && mouseY < btnY + 12) {
                    removeOfficer(officers.get(i).getPlayerId());
                    return true;
                }
                y += 16;
            }

            // Check citizen appoint buttons
            if (officers.size() < MAX_OFFICERS) {
                int rightX = guiLeft + guiWidth / 2 + 10;
                y = guiTop + 44;

                List<SyncOfficerManagementDataPacket.CitizenInfo> availableCitizens = citizens.stream()
                    .filter(c -> officers.stream().noneMatch(o -> o.getPlayerId().equals(c.getPlayerId())))
                    .toList();

                endIndex = Math.min(citizenScrollOffset + MAX_VISIBLE, availableCitizens.size());
                for (int i = citizenScrollOffset; i < endIndex; i++) {
                    int btnX = rightX + sectionWidth - 40;
                    int btnY = y - 2;

                    if (mouseX >= btnX && mouseX < btnX + 40 && mouseY >= btnY && mouseY < btnY + 12) {
                        appointOfficer(availableCitizens.get(i).getPlayerId());
                        return true;
                    }
                    y += 16;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int midX = guiLeft + guiWidth / 2;

        if (mouseX < midX) {
            // Scrolling in officers section
            int maxScroll = Math.max(0, officers.size() - MAX_VISIBLE);
            if (delta > 0) {
                officerScrollOffset = Math.max(0, officerScrollOffset - 1);
            } else {
                officerScrollOffset = Math.min(maxScroll, officerScrollOffset + 1);
            }
        } else {
            // Scrolling in citizens section
            List<SyncOfficerManagementDataPacket.CitizenInfo> availableCitizens = citizens.stream()
                .filter(c -> officers.stream().noneMatch(o -> o.getPlayerId().equals(c.getPlayerId())))
                .toList();
            int maxScroll = Math.max(0, availableCitizens.size() - MAX_VISIBLE);
            if (delta > 0) {
                citizenScrollOffset = Math.max(0, citizenScrollOffset - 1);
            } else {
                citizenScrollOffset = Math.min(maxScroll, citizenScrollOffset + 1);
            }
        }

        return true;
    }

    private void appointOfficer(UUID playerId) {
        NetworkHandler.sendToServer(new ModifyOfficerPacket(nationName, playerId, ModifyOfficerPacket.Action.APPOINT));
        // Request refresh
        NetworkHandler.sendToServer(new RequestOfficerManagementDataPacket(nationName));
    }

    private void removeOfficer(UUID playerId) {
        NetworkHandler.sendToServer(new ModifyOfficerPacket(nationName, playerId, ModifyOfficerPacket.Action.REMOVE));
        // Request refresh
        NetworkHandler.sendToServer(new RequestOfficerManagementDataPacket(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationSettingsScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler when data is received
     */
    public void updateData(boolean isLeader,
                           List<SyncOfficerManagementDataPacket.CitizenInfo> citizens,
                           List<SyncOfficerManagementDataPacket.OfficerInfo> officers) {
        this.isLeader = isLeader;
        this.citizens = citizens;
        this.officers = officers;
        this.dataLoaded = true;

        // Reset scroll if data changed
        this.citizenScrollOffset = Math.min(citizenScrollOffset, Math.max(0, citizens.size() - MAX_VISIBLE));
        this.officerScrollOffset = Math.min(officerScrollOffset, Math.max(0, officers.size() - MAX_VISIBLE));
    }
}

