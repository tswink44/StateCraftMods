package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestAllNationsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen listing all nations that exist on the server
 */
public class NationsListScreen extends StateCraftScreen {
    private List<NationEntry> nations = new ArrayList<>();
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 6;

    public NationsListScreen() {
        super(Component.literal("Nations"));
        this.guiWidth = 300;
        this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Request nations list from server
        NetworkHandler.sendToServer(new RequestAllNationsPacket());

        // Create Nation button
        this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 28,
            90, 20,
            Component.literal("§aCreate Nation"),
            btn -> openCreateNationScreen()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 75, guiTop + guiHeight - 28,
            60, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading nations...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (nations.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No nations exist yet", this.width / 2, guiTop + 70, COLOR_TEXT);
            graphics.drawCenteredString(this.font, "§8Be the first to create one!", this.width / 2, guiTop + 85, 0xFFAAAAAA);
            return;
        }

        int y = guiTop + 35;

        for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, nations.size()); i++) {
            NationEntry entry = nations.get(i);

            // Nation panel - highlight if hovered
            boolean hovered = mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                             mouseY >= y && mouseY <= y + 26;

            if (hovered) {
                renderSubPanel(graphics, guiLeft + 14, y - 1, guiWidth - 28, 28);
            } else {
                renderSubPanel(graphics, guiLeft + 15, y, guiWidth - 30, 26);
            }

            // Member indicator if player is in this nation
            String prefix = entry.isMember ? "§a✓ " : "";
            graphics.drawString(this.font, prefix + "§e" + entry.name, guiLeft + 22, y + 4, COLOR_TEXT);

            // Stats line
            String stats = "§7Leader: §f" + entry.leaderName + " §8| " + entry.memberCount + " members";
            graphics.drawString(this.font, stats, guiLeft + 22, y + 14, 0xFFAAAAAA);

            // Open/Closed indicator on right
            if (entry.isOpen) {
                graphics.drawString(this.font, "§aOpen", guiLeft + guiWidth - 45, y + 9, 0xFF55FF55);
            } else {
                graphics.drawString(this.font, "§7Closed", guiLeft + guiWidth - 50, y + 9, 0xFF888888);
            }

            y += 30;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ More above", this.width / 2, guiTop + 26, 0xFF888888);
        }
        if (scrollOffset + MAX_VISIBLE < nations.size()) {
            graphics.drawCenteredString(this.font, "§7▼ More below", this.width / 2, guiTop + guiHeight - 45, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded) {
            int y = guiTop + 35;

            for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, nations.size()); i++) {
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y && mouseY <= y + 26) {
                    openNationInfo(nations.get(i).name);
                    return true;
                }
                y += 30;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + MAX_VISIBLE < nations.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openNationInfo(String nationName) {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    private void openCreateNationScreen() {
        this.minecraft.setScreen(new CreateNationScreen());
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler to update the nations list
     */
    public void updateNations(List<NationEntry> nations) {
        this.nations = new ArrayList<>(nations);
        this.dataLoaded = true;
    }

    public static class NationEntry {
        public final String name;
        public final String leaderName;
        public final int memberCount;
        public final int stateCount;
        public final boolean isOpen;
        public final boolean isMember;

        public NationEntry(String name, String leaderName, int memberCount, int stateCount, boolean isOpen, boolean isMember) {
            this.name = name;
            this.leaderName = leaderName;
            this.memberCount = memberCount;
            this.stateCount = stateCount;
            this.isOpen = isOpen;
            this.isMember = isMember;
        }
    }
}

