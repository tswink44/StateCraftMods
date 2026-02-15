package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestMyStatesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen listing all states the player is a citizen of
 */
public class MyStatesScreen extends StateCraftScreen {
    private final String nationName;
    private List<StateEntry> myStates = new ArrayList<>();
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 6;

    public MyStatesScreen(String nationName) {
        super(Component.literal("My States"));
        this.nationName = nationName;
        this.guiWidth = 280;
        this.guiHeight = 200;
    }

    @Override
    protected void init() {
        super.init();

        // Request player's states from server
        NetworkHandler.sendToServer(new RequestMyStatesPacket());

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
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (myStates.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7You are not a citizen of any state", this.width / 2, guiTop + 70, COLOR_TEXT);
            graphics.drawCenteredString(this.font, "§8Join a state from the States list", this.width / 2, guiTop + 85, 0xFFAAAAAA);
            return;
        }

        int y = guiTop + 35;

        for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, myStates.size()); i++) {
            StateEntry entry = myStates.get(i);

            // State panel - highlight if hovered
            boolean hovered = mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                             mouseY >= y && mouseY <= y + 24;

            if (hovered) {
                renderSubPanel(graphics, guiLeft + 14, y - 1, guiWidth - 28, 26);
            } else {
                renderSubPanel(graphics, guiLeft + 15, y, guiWidth - 30, 24);
            }

            // Primary indicator
            String prefix = entry.isPrimary ? "§6★ " : "§7• ";
            graphics.drawString(this.font, prefix + "§e" + entry.stateName, guiLeft + 22, y + 4, COLOR_TEXT);
            graphics.drawString(this.font, "§7Governor: §f" + entry.governorName, guiLeft + 22, y + 14, 0xFFAAAAAA);

            // Show chunk count on right if they own chunks there
            if (entry.ownedChunks > 0) {
                String chunkInfo = "§8" + entry.ownedChunks + " chunks";
                int infoWidth = this.font.width(chunkInfo);
                graphics.drawString(this.font, chunkInfo, guiLeft + guiWidth - 22 - infoWidth, y + 9, 0xFF888888);
            }

            y += 28;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ More above", this.width / 2, guiTop + 26, 0xFF888888);
        }
        if (scrollOffset + MAX_VISIBLE < myStates.size()) {
            graphics.drawCenteredString(this.font, "§7▼ More below", this.width / 2, guiTop + guiHeight - 45, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded) {
            int y = guiTop + 35;

            for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, myStates.size()); i++) {
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y && mouseY <= y + 24) {
                    openStateInfo(myStates.get(i).stateName);
                    return true;
                }
                y += 28;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + MAX_VISIBLE < myStates.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openStateInfo(String stateName) {
        this.minecraft.setScreen(new StateInfoScreen(nationName, stateName));
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler to update the states list
     */
    public void updateMyStates(List<StateEntry> states) {
        this.myStates = new ArrayList<>(states);
        this.dataLoaded = true;
    }

    public static class StateEntry {
        public final String stateName;
        public final String governorName;
        public final boolean isPrimary;
        public final int ownedChunks;

        public StateEntry(String stateName, String governorName, boolean isPrimary, int ownedChunks) {
            this.stateName = stateName;
            this.governorName = governorName;
            this.isPrimary = isPrimary;
            this.ownedChunks = ownedChunks;
        }
    }
}

