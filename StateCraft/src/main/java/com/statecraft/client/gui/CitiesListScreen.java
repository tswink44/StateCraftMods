package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestCitiesPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen listing all cities in a state
 */
public class CitiesListScreen extends StateCraftScreen {
    private final String nationName;
    private final String stateName;
    private List<CityData> cities = new ArrayList<>();
    private boolean dataLoaded = false;
    private int scrollOffset = 0;

    public CitiesListScreen(String nationName, String stateName) {
        super(Component.literal("Cities: " + stateName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.guiWidth = 280; this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Request cities data from server
        NetworkHandler.sendToServer(new RequestCitiesPacket(nationName, stateName));

        // Create City button
        this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("New City"),
            btn -> createCity()
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
            graphics.drawCenteredString(this.font, "§7Loading cities...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (cities.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No cities yet", this.width / 2, guiTop + 70, COLOR_TEXT);
            graphics.drawCenteredString(this.font, "§8Create one to start claiming land!", this.width / 2, guiTop + 85, 0xFFAAAAAA);
            return;
        }

        int y = guiTop + 35;
        int maxVisible = 6;

        for (int i = scrollOffset; i < Math.min(scrollOffset + maxVisible, cities.size()); i++) {
            CityData city = cities.get(i);

            // City panel - clickable
            boolean hovered = mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                             mouseY >= y && mouseY <= y + 26;

            if (hovered) {
                renderSubPanel(graphics, guiLeft + 14, y - 1, guiWidth - 28, 28);
            } else {
                renderSubPanel(graphics, guiLeft + 15, y, guiWidth - 30, 26);
            }

            graphics.drawString(this.font, "§e" + city.name, guiLeft + 22, y + 4, COLOR_TEXT);
            graphics.drawString(this.font, "§7Mayor: §f" + city.mayorName, guiLeft + 22, y + 14, 0xFFAAAAAA);

            // Stats on right
            String stats = city.chunkCount + " chunks, " + city.residentCount + " residents";
            int statsWidth = this.font.width(stats);
            graphics.drawString(this.font, "§8" + stats, guiLeft + guiWidth - 22 - statsWidth, y + 9, 0xFF888888);

            y += 30;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ Scroll up", this.width / 2, guiTop + 26, 0xFF888888);
        }
        if (scrollOffset + maxVisible < cities.size()) {
            graphics.drawCenteredString(this.font, "§7▼ Scroll down", this.width / 2, guiTop + guiHeight - 42, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded) {
            int y = guiTop + 35;
            int maxVisible = 6;

            for (int i = scrollOffset; i < Math.min(scrollOffset + maxVisible, cities.size()); i++) {
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y && mouseY <= y + 26) {
                    openCityInfo(cities.get(i).name);
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
        } else if (delta < 0 && scrollOffset + 6 < cities.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openCityInfo(String cityName) {
        this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, cityName));
    }

    private void createCity() {
        this.minecraft.setScreen(new CreateCityScreen(nationName, stateName));
    }

    private void goBack() {
        this.minecraft.setScreen(new StateInfoScreen(nationName, stateName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    public void updateCities(List<CityData> cities) {
        this.cities = cities;
        this.dataLoaded = true;
    }

    public static class CityData {
        public String name;
        public String mayorName;
        public int chunkCount;
        public int residentCount;

        public CityData(String name, String mayorName, int chunkCount, int residentCount) {
            this.name = name;
            this.mayorName = mayorName;
            this.chunkCount = chunkCount;
            this.residentCount = residentCount;
        }
    }
}

