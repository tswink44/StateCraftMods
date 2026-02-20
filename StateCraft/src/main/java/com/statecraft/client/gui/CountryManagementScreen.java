package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Country Management screen — hub for administrative functions.
 * Accessed from NationInfoScreen, provides row-based navigation to:
 * Mail, Settings, Elections, Legislature, View Laws, Contracts
 *
 * Visibility rules:
 * - Mail: admin only
 * - Settings: admin only
 * - Elections: all members
 * - Legislature (Propose): all members (server checks permissions)
 * - View Laws: all members
 * - Contracts: all members
 */
public class CountryManagementScreen extends StateCraftScreen {

    private final String nationName;

    // Permission flags passed from NationInfoScreen
    private final boolean isAdmin;
    private final boolean isMember;
    private final boolean isLeader;

    // Menu row entries
    private final List<MenuRow> rows = new ArrayList<>();

    public CountryManagementScreen(String nationName, boolean isAdmin, boolean isMember, boolean isLeader) {
        super(Component.literal("Management: " + nationName));
        this.nationName = nationName;
        this.isAdmin = isAdmin;
        this.isMember = isMember;
        this.isLeader = isLeader;
        this.guiWidth = 260;
        this.guiHeight = 200;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false; // We render our own title row
    }

    @Override
    protected void init() {
        super.init();

        rows.clear();

        int startX = guiLeft + 10;
        int arrowX = guiLeft + guiWidth - 32;
        int arrowBtnWidth = 22;
        int buttonHeight = 14;
        int startY = guiTop + 24;
        int spacing = 18;
        int row = 0;

        // === Admin Section ===
        if (isAdmin) {
            // Mail
            rows.add(new MenuRow("§eMail", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openMailScreen()
            ));
            row++;

            // Settings
            rows.add(new MenuRow("Settings", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openSettingsScreen()
            ));
            row++;

            // Gap after admin section
            row++;
        }

        // === Member Section ===
        if (isMember) {
            // Elections
            rows.add(new MenuRow("§6Elections", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openElectionsScreen()
            ));
            row++;

            // Legislature (Propose)
            rows.add(new MenuRow("§bLegislature", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openLegislatureScreen()
            ));
            row++;

            // View Laws
            rows.add(new MenuRow("View Laws", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openViewLawsScreen()
            ));
            row++;

            // Contracts
            rows.add(new MenuRow("§eContracts", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openContractsScreen()
            ));
            row++;

            // Diplomacy
            rows.add(new MenuRow("§d🌐 Diplomacy", startY + spacing * row, true));
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openDiplomacyScreen()
            ));
            row++;

            // Executive Actions (leader only)
            if (isLeader) {
                row++; // Gap before leader section
                rows.add(new MenuRow("§c⚡ Executive Actions", startY + spacing * row, true));
                this.addRenderableWidget(createCompactArrowButton(
                    arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                    btn -> openExecutiveActionsScreen()
                ));
                row++;
            }
        }

        // If not a member, show a message row
        if (!isMember) {
            rows.add(new MenuRow("§8Not a member of this nation", startY + spacing * row, false));
            row++;
        }

        // Back button at bottom
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 30, guiTop + guiHeight - 28, 60, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    private Button createCompactArrowButton(int x, int y, int width, int height, Button.OnPress onPress) {
        return Button.builder(Component.literal("→"), onPress)
            .pos(x, y)
            .size(width, height)
            .build();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = guiLeft + 10;

        // Title row
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, guiLeft + guiWidth - 10, guiTop + 20, 0xFF404040);
        graphics.drawString(this.font, "Country Management", startX + 4, guiTop + 9, 0xFFFFFFFF);

        // Render menu rows
        for (MenuRow row : rows) {
            int rowColor = row.active ? 0xAA808080 : 0xAA606060;
            graphics.fill(startX, row.y, guiLeft + guiWidth - 10, row.y + 14, rowColor);
            graphics.fill(startX, row.y, guiLeft + guiWidth - 10, row.y + 1, 0xFF505050);
            graphics.fill(startX, row.y + 13, guiLeft + guiWidth - 10, row.y + 14, 0xFF404040);

            int textColor = row.active ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawString(this.font, row.text, startX + 4, row.y + 3, textColor);
        }
    }

    // === Navigation methods ===

    private void openMailScreen() {
        this.minecraft.setScreen(new GovMailboxScreen(GovMailboxScreen.EntityType.NATION, nationName, ""));
    }

    private void openSettingsScreen() {
        this.minecraft.setScreen(new NationSettingsScreen(nationName));
    }

    private void openElectionsScreen() {
        this.minecraft.setScreen(new ElectionScreen());
    }

    private void openLegislatureScreen() {
        this.minecraft.setScreen(new LegislatureScreen(nationName));
    }

    private void openViewLawsScreen() {
        this.minecraft.setScreen(new NationLawsScreen(nationName));
    }

    private void openContractsScreen() {
        this.minecraft.setScreen(new ContractsMainScreen(nationName));
    }

    private void openDiplomacyScreen() {
        this.minecraft.setScreen(new DiplomacyScreen(nationName));
    }

    private void openExecutiveActionsScreen() {
        this.minecraft.setScreen(new ExecutiveActionsScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    private static class MenuRow {
        final String text;
        final int y;
        final boolean active;

        MenuRow(String text, int y, boolean active) {
            this.text = text;
            this.y = y;
            this.active = active;
        }
    }
}

