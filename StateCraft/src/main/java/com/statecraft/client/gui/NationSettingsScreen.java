package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.UpdateEntitySettingsPacket;
import com.statecraft.network.packets.UpdateNationSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for managing nation settings (admin only)
 * Note: Name, Flag, and Membership changes require constitutional amendments via the legislature
 */
public class NationSettingsScreen extends StateCraftScreen {
    private final String nationName;

    public NationSettingsScreen(String nationName) {
        super(Component.literal("Nation Settings"));
        this.nationName = nationName;
        this.guiWidth = 280; this.guiHeight = 140;
    }

    @Override
    protected void init() {
        super.init();

        int buttonX = guiLeft + guiWidth / 2 - 60;
        int startY = guiTop + 40;

        // Officers management button
        this.addRenderableWidget(createButton(
            buttonX, startY, 120, 20,
            Component.literal("§eManage Officers"),
            btn -> openOfficerManagement()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 30,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int labelX = guiLeft + 20;
        int startY = guiTop + 44;

        graphics.drawString(this.font, "§7Leadership:", labelX, startY + 4, COLOR_TEXT);

        // Info text about constitutional amendments
        graphics.drawCenteredString(this.font, "§8Name/Flag/Membership changes", this.width / 2, guiTop + guiHeight - 52, 0xFF666666);
        graphics.drawCenteredString(this.font, "§8require legislature bills", this.width / 2, guiTop + guiHeight - 42, 0xFF666666);
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    private void openOfficerManagement() {
        this.minecraft.setScreen(new OfficerManagementScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called to populate current settings (kept for backward compatibility)
    public void setCurrentSettings(String tag, String description, boolean open, String flagUrl) {
        // Membership is no longer editable here - requires legislature
    }

    // Overloads for backward compatibility
    public void setCurrentSettings(String tag, String description, boolean open, String flagUrl, double statePassThrough) {
        setCurrentSettings(tag, description, open, flagUrl);
    }

    public void setCurrentSettings(String tag, String description, boolean open) {
        setCurrentSettings(tag, description, open, "");
    }
}

