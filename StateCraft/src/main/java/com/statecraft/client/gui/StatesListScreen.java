package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestStatesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen listing all states in a nation
 */
public class StatesListScreen extends StateCraftScreen {
    private final String nationName;
    private List<StateData> states = new ArrayList<>();
    private boolean dataLoaded = false;

    public StatesListScreen(String nationName) {
        super(Component.literal("States: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 280; this.guiHeight = 200;
    }

    @Override
    protected void init() {
        super.init();

        // Request states data from server
        NetworkHandler.sendToServer(new RequestStatesPacket(nationName));

        // Create State button
        this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Create State"),
            btn -> createState()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 95, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading states...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (states.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No states yet", this.width / 2, guiTop + 70, COLOR_TEXT);
            graphics.drawCenteredString(this.font, "§8Create one to get started!", this.width / 2, guiTop + 85, 0xFFAAAAAA);
            return;
        }

        int y = guiTop + 35;
        for (int i = 0; i < Math.min(5, states.size()); i++) {
            StateData state = states.get(i);

            // State panel - highlight if hovered
            boolean hovered = mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                             mouseY >= y && mouseY <= y + 26;

            if (hovered) {
                renderSubPanel(graphics, guiLeft + 14, y - 1, guiWidth - 28, 28);
            } else {
                renderSubPanel(graphics, guiLeft + 15, y, guiWidth - 30, 26);
            }

            graphics.drawString(this.font, "§e" + state.name, guiLeft + 22, y + 4, COLOR_TEXT);
            graphics.drawString(this.font, "§7Governor: §f" + state.governorName, guiLeft + 22, y + 14, 0xFFAAAAAA);

            // Stats on right
            String stats = state.cityCount + " cities, " + state.chunkCount + " chunks";
            int statsWidth = this.font.width(stats);
            graphics.drawString(this.font, "§8" + stats, guiLeft + guiWidth - 22 - statsWidth, y + 9, 0xFF888888);

            y += 30;
        }

        if (states.size() > 5) {
            graphics.drawCenteredString(this.font, "§8+" + (states.size() - 5) + " more states...", this.width / 2, y + 2, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded) {
            int y = guiTop + 35;
            for (int i = 0; i < Math.min(5, states.size()); i++) {
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y && mouseY <= y + 26) {
                    openStateInfo(states.get(i).name);
                    return true;
                }
                y += 30;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openStateInfo(String stateName) {
        this.minecraft.setScreen(new StateInfoScreen(nationName, stateName));
    }

    private void createState() {
        this.minecraft.setScreen(new CreateStateScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    public void updateStates(List<StateData> states) {
        this.states = states;
        this.dataLoaded = true;
    }

    public static class StateData {
        public String name;
        public String governorName;
        public int cityCount;
        public int chunkCount;

        public StateData(String name, String governor, int cities, int chunks) {
            this.name = name;
            this.governorName = governor;
            this.cityCount = cities;
            this.chunkCount = chunks;
        }
    }
}

