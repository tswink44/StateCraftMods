package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestCompanyListPacket;
import com.statecraft.network.packets.SyncCompanyListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen listing all companies the player owns shares in.
 * Clicking on a company opens the full CompanyScreen for that company.
 */
public class CompanyListScreen extends StateCraftScreen {

    private List<SyncCompanyListPacket.CompanyEntry> companies = new ArrayList<>();
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 5;
    private static final int ENTRY_HEIGHT = 32;

    public CompanyListScreen() {
        super(Component.literal("My Companies"));
        this.guiWidth = 300;
        this.guiHeight = 230;
    }

    @Override
    protected void init() {
        super.init();
        NetworkHandler.sendToServer(new RequestCompanyListPacket());

        // Create Company button at bottom-left
        this.addRenderableWidget(createButton(
            guiLeft + 10, guiTop + guiHeight - 28,
            110, 18,
            Component.literal("§aCreate Company"),
            btn -> openCreateCompany()
        ));

        // Back button at bottom-right
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 90, guiTop + guiHeight - 28,
            80, 18,
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

        if (companies.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7You are not part of any company",
                this.width / 2, guiTop + 70, COLOR_TEXT);
            graphics.drawCenteredString(this.font, "§8Create one or buy shares on the Stock Market",
                this.width / 2, guiTop + 85, 0xFFAAAAAA);
            return;
        }

        // Scroll up indicator
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ More above", this.width / 2, guiTop + 26, 0xFF888888);
        }

        int y = guiTop + 38;
        int startX = guiLeft + 15;
        int panelW = guiWidth - 30;

        for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, companies.size()); i++) {
            SyncCompanyListPacket.CompanyEntry entry = companies.get(i);

            boolean hovered = mouseX >= startX && mouseX <= startX + panelW &&
                             mouseY >= y && mouseY <= y + ENTRY_HEIGHT - 2;

            if (hovered) {
                renderSubPanel(graphics, startX - 1, y - 1, panelW + 2, ENTRY_HEIGHT);
            } else {
                renderSubPanel(graphics, startX, y, panelW, ENTRY_HEIGHT - 2);
            }

            // Company icon + name
            String typeIcon = entry.getCompanyType().equals("BANK") ? "§9\uD83C\uDFE6 " : "§f\uD83C\uDFE2 ";
            String name = typeIcon + "§f" + entry.getCompanyName();
            graphics.drawString(this.font, name, startX + 5, y + 3, 0xFFFFFFFF);

            // Role badge on the right of name
            if (entry.isFounder()) {
                graphics.drawString(this.font, "§6[Founder]", startX + panelW - 55, y + 3, 0xFFFFAA00);
            } else if (entry.isOfficer()) {
                graphics.drawString(this.font, "§b[Officer]", startX + panelW - 55, y + 3, 0xFF55FFFF);
            }

            // Second line: shares info
            String sharesInfo = "§7Shares: §f" + entry.getPlayerShares() + "§7/§f" + entry.getTotalShares()
                + " §8(" + String.format("%.1f", entry.getSharePercentage()) + "%)";
            graphics.drawString(this.font, sharesInfo, startX + 5, y + 15, 0xFFAAAAAA);

            // Founder name on right
            String founder = "§8by " + entry.getFounderName();
            int founderW = this.font.width(founder.replaceAll("§.", ""));
            graphics.drawString(this.font, founder, startX + panelW - founderW - 5, y + 15, 0xFF888888);

            y += ENTRY_HEIGHT;
        }

        // Scroll down indicator
        if (scrollOffset + MAX_VISIBLE < companies.size()) {
            graphics.drawCenteredString(this.font, "§7▼ More below", this.width / 2, guiTop + guiHeight - 48, 0xFF888888);
        }

        // Count
        graphics.drawString(this.font, "§8" + companies.size() + " companies",
            guiLeft + guiWidth - 85, guiTop + guiHeight - 42, 0xFF666666);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded) {
            int y = guiTop + 38;
            int startX = guiLeft + 15;
            int panelW = guiWidth - 30;

            for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, companies.size()); i++) {
                if (mouseX >= startX && mouseX <= startX + panelW &&
                    mouseY >= y && mouseY <= y + ENTRY_HEIGHT - 2) {
                    openCompany(companies.get(i).getCompanyId());
                    return true;
                }
                y += ENTRY_HEIGHT;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + MAX_VISIBLE < companies.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openCompany(String companyId) {
        this.minecraft.setScreen(new CompanyScreen(companyId));
    }

    private void openCreateCompany() {
        // Open CompanyScreen with no specific company ID (will show create form)
        this.minecraft.setScreen(new CompanyScreen());
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler to update the company list
     */
    public void updateCompanyList(List<SyncCompanyListPacket.CompanyEntry> entries) {
        this.companies = new ArrayList<>(entries);
        this.dataLoaded = true;
    }
}

