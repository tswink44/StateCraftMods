package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.JoinCitizenshipPacket;
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
    private boolean canCreateCity = false; // Only state governor or nation officers can create cities
    private int scrollOffset = 0;
    private Button createCityButton;

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

        // Create City button - initially hidden until we know permissions
        createCityButton = this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("New City"),
            btn -> createCity()
        ));
        createCityButton.visible = false; // Hidden until data loads

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

            // City panel - clickable (reduced width for join button)
            boolean hovered = mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 55 &&
                             mouseY >= y && mouseY <= y + 26;

            if (hovered) {
                renderSubPanel(graphics, guiLeft + 14, y - 1, guiWidth - 68, 28);
            } else {
                renderSubPanel(graphics, guiLeft + 15, y, guiWidth - 70, 26);
            }

            // Citizenship indicator
            String citizenMark = city.isResident ? "§a✓ " : "";
            graphics.drawString(this.font, citizenMark + "§e" + city.name, guiLeft + 22, y + 4, COLOR_TEXT);
            graphics.drawString(this.font, "§7Mayor: §f" + city.mayorName, guiLeft + 22, y + 14, 0xFFAAAAAA);

            // Join button area on right (if not already a resident)
            if (!city.isResident) {
                if (city.isPublicJoin) {
                    boolean joinHovered = mouseX >= guiLeft + guiWidth - 50 && mouseX <= guiLeft + guiWidth - 15 &&
                                         mouseY >= y + 3 && mouseY <= y + 23;
                    int joinBgColor = joinHovered ? 0xFF4A7A4A : 0xFF3A5A3A;
                    graphics.fill(guiLeft + guiWidth - 50, y + 3, guiLeft + guiWidth - 15, y + 23, joinBgColor);
                    graphics.drawCenteredString(this.font, "§aJoin", guiLeft + guiWidth - 32, y + 9, 0xFFFFFFFF);
                } else {
                    // City is not open for joining
                    graphics.drawString(this.font, "§8Closed", guiLeft + guiWidth - 50, y + 9, 0xFF888888);
                }
            } else {
                // Show member indicator
                graphics.drawString(this.font, "§2Member", guiLeft + guiWidth - 55, y + 9, 0xFF55FF55);
            }

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
                CityData city = cities.get(i);

                // Check if clicked Join button (only if not resident and public join)
                if (!city.isResident && city.isPublicJoin &&
                    mouseX >= guiLeft + guiWidth - 50 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y + 3 && mouseY <= y + 23) {
                    joinCity(city.name);
                    return true;
                }

                // Check if clicked city info area
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 55 &&
                    mouseY >= y && mouseY <= y + 26) {
                    openCityInfo(city.name);
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

    private void joinCity(String cityName) {
        NetworkHandler.sendToServer(JoinCitizenshipPacket.joinCity(nationName, stateName, cityName));
        // Refresh data after a short delay
        NetworkHandler.sendToServer(new RequestCitiesPacket(nationName, stateName));
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

    public void updateCities(List<CityData> cities, boolean canCreateCity) {
        this.cities = cities;
        this.canCreateCity = canCreateCity;
        this.dataLoaded = true;

        // Show/hide create button based on permission
        if (createCityButton != null) {
            createCityButton.visible = canCreateCity;
        }
    }

    // Overload for backward compatibility
    public void updateCities(List<CityData> cities) {
        updateCities(cities, false);
    }

    public static class CityData {
        public String name;
        public String mayorName;
        public int chunkCount;
        public int residentCount;
        public boolean isResident;
        public boolean isPublicJoin;

        public CityData(String name, String mayorName, int chunkCount, int residentCount, boolean isResident, boolean isPublicJoin) {
            this.name = name;
            this.mayorName = mayorName;
            this.chunkCount = chunkCount;
            this.residentCount = residentCount;
            this.isResident = isResident;
            this.isPublicJoin = isPublicJoin;
        }

        // Backward compatibility constructor
        public CityData(String name, String mayorName, int chunkCount, int residentCount) {
            this(name, mayorName, chunkCount, residentCount, false, false);
        }
    }
}

