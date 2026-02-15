package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.JoinCitizenshipPacket;
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
    private boolean canCreateState = false; // Only nation officers can create states
    private Button createStateButton;

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

        // Create State button - initially hidden until we know permissions
        createStateButton = this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Create State"),
            btn -> createState()
        ));
        createStateButton.visible = false; // Hidden until data loads

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
            boolean hovered = mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 55 &&
                             mouseY >= y && mouseY <= y + 26;

            if (hovered) {
                renderSubPanel(graphics, guiLeft + 14, y - 1, guiWidth - 68, 28);
            } else {
                renderSubPanel(graphics, guiLeft + 15, y, guiWidth - 70, 26);
            }

            // Citizenship indicator
            String citizenMark = state.isCitizen ? "§a✓ " : "";
            graphics.drawString(this.font, citizenMark + "§e" + state.name, guiLeft + 22, y + 4, COLOR_TEXT);
            graphics.drawString(this.font, "§7Governor: §f" + state.governorName, guiLeft + 22, y + 14, 0xFFAAAAAA);

            // Join button area on right (if not already a citizen)
            if (!state.isCitizen) {
                boolean joinHovered = mouseX >= guiLeft + guiWidth - 50 && mouseX <= guiLeft + guiWidth - 15 &&
                                     mouseY >= y + 3 && mouseY <= y + 23;
                int joinBgColor = joinHovered ? 0xFF4A7A4A : 0xFF3A5A3A;
                graphics.fill(guiLeft + guiWidth - 50, y + 3, guiLeft + guiWidth - 15, y + 23, joinBgColor);
                graphics.drawCenteredString(this.font, "§aJoin", guiLeft + guiWidth - 32, y + 9, 0xFFFFFFFF);
            } else {
                // Show member indicator
                graphics.drawString(this.font, "§2Member", guiLeft + guiWidth - 55, y + 9, 0xFF55FF55);
            }

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
                StateData state = states.get(i);

                // Check if clicked Join button
                if (!state.isCitizen &&
                    mouseX >= guiLeft + guiWidth - 50 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y + 3 && mouseY <= y + 23) {
                    joinState(state.name);
                    return true;
                }

                // Check if clicked state info area
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 55 &&
                    mouseY >= y && mouseY <= y + 26) {
                    openStateInfo(state.name);
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

    private void joinState(String stateName) {
        NetworkHandler.sendToServer(JoinCitizenshipPacket.joinState(nationName, stateName));
        // Refresh data after a short delay
        NetworkHandler.sendToServer(new RequestStatesPacket(nationName));
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

    public void updateStates(List<StateData> states, boolean canCreateState) {
        this.states = states;
        this.canCreateState = canCreateState;
        this.dataLoaded = true;

        // Show/hide create button based on permission
        if (createStateButton != null) {
            createStateButton.visible = canCreateState;
        }
    }

    // Overload for backward compatibility
    public void updateStates(List<StateData> states) {
        updateStates(states, false);
    }

    public static class StateData {
        public String name;
        public String governorName;
        public int cityCount;
        public int chunkCount;
        public boolean isCitizen;

        public StateData(String name, String governor, int cities, int chunks, boolean isCitizen) {
            this.name = name;
            this.governorName = governor;
            this.cityCount = cities;
            this.chunkCount = chunks;
            this.isCitizen = isCitizen;
        }

        // Backward compatibility constructor
        public StateData(String name, String governor, int cities, int chunks) {
            this(name, governor, cities, chunks, false);
        }
    }
}

