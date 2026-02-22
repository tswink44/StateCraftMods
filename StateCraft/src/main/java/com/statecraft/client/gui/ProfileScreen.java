package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationDataPacket;
import com.statecraft.network.packets.SetNicknamePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Profile screen showing player information in a compact row-based layout
 */
public class ProfileScreen extends StateCraftScreen {

    // Player data
    private UUID playerUUID;
    private String playerName = "";
    private String nickname = "";
    private String cityName = "";
    private String stateName = "";
    private String nationName = "";
    private List<String> companyNames = new ArrayList<>();
    private boolean dataLoaded = false;

    // Menu entries for custom row rendering
    private List<ProfileEntry> entries = new ArrayList<>();

    // Nickname edit box (shown when editing)
    private EditBox nicknameEditBox;
    private boolean editingNickname = false;

    public ProfileScreen() {
        super(Component.literal("Your Profile"));
        this.guiWidth = 280;
        this.guiHeight = 220;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false; // We render our own title row
    }

    @Override
    protected void init() {
        super.init();

        // Get player info
        if (this.minecraft != null && this.minecraft.player != null) {
            this.playerUUID = this.minecraft.player.getUUID();
            this.playerName = this.minecraft.player.getName().getString();
        }

        // Request nation data from server
        NetworkHandler.sendToServer(new RequestNationDataPacket());

        int arrowBtnWidth = 22;
        int buttonHeight = 14;
        int startX = guiLeft + 10;
        int arrowX = guiLeft + guiWidth - 32;
        int startY = guiTop + 24;
        int spacing = 18;

        entries.clear();

        int row = 0;

        // UUID row (no button, just display)
        entries.add(new ProfileEntry("UUID: " + (playerUUID != null ? playerUUID.toString() : "Unknown"), startY + spacing * row, false, false));
        row++;

        // Name row (no button, just display)
        entries.add(new ProfileEntry("Name: " + playerName, startY + spacing * row, false, false));
        row++;

        // Nickname row (with edit button)
        entries.add(new ProfileEntry("Nick: " + nickname, startY + spacing * row, true, true));
        this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> toggleNicknameEdit()
        ));
        row++;

        // Gap before territory section
        row++;

        // Company row
        String companyDisplay = companyNames.isEmpty() ? "None" : String.join(", ", companyNames);
        entries.add(new ProfileEntry("Company: " + companyDisplay, startY + spacing * row, !companyNames.isEmpty(), !companyNames.isEmpty()));
        if (!companyNames.isEmpty()) {
            this.addRenderableWidget(createCompactArrowButton(
                arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
                btn -> openCompanyScreen()
            ));
        }
        row++;

        // City row
        entries.add(new ProfileEntry("City: " + (cityName.isEmpty() ? "None" : cityName), startY + spacing * row, true, true));
        this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openCityScreen()
        ));
        row++;

        // State row
        entries.add(new ProfileEntry("State: " + (stateName.isEmpty() ? "None" : stateName), startY + spacing * row, true, true));
        this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openStateScreen()
        ));
        row++;

        // Nation row
        entries.add(new ProfileEntry("Nation: " + (nationName.isEmpty() ? "None" : nationName), startY + spacing * row, true, true));
        this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openNationScreen()
        ));
        row++;

        // Nickname edit box (hidden by default)
        nicknameEditBox = new EditBox(this.font, guiLeft + 50, guiTop + 24 + spacing * 2 + 2, guiWidth - 90, 12, Component.literal("Nickname"));
        nicknameEditBox.setMaxLength(32);
        nicknameEditBox.setValue(nickname);
        nicknameEditBox.setVisible(false);
        nicknameEditBox.setResponder(this::onNicknameChanged);
        this.addRenderableWidget(nicknameEditBox);

        // Back button at bottom
        this.addRenderableWidget(createButton(
            this.width / 2 - 40, guiTop + guiHeight - 24,
            80, 18,
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

        // Render title row
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, guiLeft + guiWidth - 10, guiTop + 20, 0xFF404040);
        graphics.drawString(this.font, "Your Profile", startX + 4, guiTop + 9, 0xFFFFFFFF);

        // Title icon (SC box)
        int iconX = guiLeft + guiWidth - 32;
        int iconY = guiTop + 6;
        graphics.fill(iconX, iconY, iconX + 22, iconY + 14, 0xFF1E90FF);
        graphics.drawCenteredString(this.font, "SC", iconX + 11, iconY + 3, 0xFFFFFFFF);

        // Render each entry row
        for (int i = 0; i < entries.size(); i++) {
            ProfileEntry entry = entries.get(i);

            // Row background
            int rowColor = entry.active ? 0xAA808080 : 0xAA606060;
            graphics.fill(startX, entry.y, guiLeft + guiWidth - 10, entry.y + 14, rowColor);

            // Border around row
            graphics.fill(startX, entry.y, guiLeft + guiWidth - 10, entry.y + 1, 0xFF505050);
            graphics.fill(startX, entry.y + 13, guiLeft + guiWidth - 10, entry.y + 14, 0xFF404040);

            // Text (left-aligned) - but skip nickname row if editing
            if (!(i == 2 && editingNickname)) {
                int textColor = entry.active ? 0xFFFFFFFF : 0xFFAAAAAA;
                graphics.drawString(this.font, entry.text, startX + 4, entry.y + 3, textColor);
            } else {
                // Show "Nick:" label when editing
                graphics.drawString(this.font, "Nick:", startX + 4, entry.y + 3, 0xFFFFFFFF);
            }
        }
    }

    private void toggleNicknameEdit() {
        editingNickname = !editingNickname;
        nicknameEditBox.setVisible(editingNickname);
        if (editingNickname) {
            nicknameEditBox.setFocused(true);
        } else {
            // Save nickname when closing edit mode
            saveNickname();
        }
    }

    private void onNicknameChanged(String newNickname) {
        this.nickname = newNickname;
        // Update the entry text
        if (entries.size() > 2) {
            entries.set(2, new ProfileEntry("Nick: " + nickname, entries.get(2).y, true, true));
        }
    }

    private void saveNickname() {
        // Send nickname to server
        NetworkHandler.sendToServer(new SetNicknamePacket(nickname));
    }

    private void openCityScreen() {
        if (!cityName.isEmpty() && !nationName.isEmpty() && !stateName.isEmpty()) {
            this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, cityName));
        }
    }

    private void openCompanyScreen() {
        this.minecraft.setScreen(new CompanyScreen());
    }

    private void openStateScreen() {
        if (!stateName.isEmpty() && !nationName.isEmpty()) {
            this.minecraft.setScreen(new StateInfoScreen(nationName, stateName));
        }
    }

    private void openNationScreen() {
        if (!nationName.isEmpty()) {
            this.minecraft.setScreen(new NationInfoScreen(nationName));
        } else {
            this.minecraft.setScreen(new CreateNationScreen());
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        // Save nickname if editing
        if (editingNickname) {
            saveNickname();
        }
        goBack();
    }

    // Called by network handler when data is received
    public void updateProfileData(String nationName, String stateName, String cityName, String nickname,
                                   List<String> companyNames) {
        this.nationName = nationName != null ? nationName : "";
        this.stateName = stateName != null ? stateName : "";
        this.cityName = cityName != null ? cityName : "";
        this.nickname = nickname != null ? nickname : "";
        this.companyNames = companyNames != null ? companyNames : new ArrayList<>();
        this.dataLoaded = true;

        // Rebuild entries with new data
        rebuildEntries();
    }

    private void rebuildEntries() {
        int startY = guiTop + 24;
        int spacing = 18;

        entries.clear();
        int row = 0;

        // UUID row
        entries.add(new ProfileEntry("UUID: " + (playerUUID != null ? playerUUID.toString() : "Unknown"), startY + spacing * row, false, false));
        row++;

        // Name row
        entries.add(new ProfileEntry("Name: " + playerName, startY + spacing * row, false, false));
        row++;

        // Nickname row
        entries.add(new ProfileEntry("Nick: " + nickname, startY + spacing * row, true, true));
        row++;

        // Gap
        row++;

        // Company row
        String companyDisplay = companyNames.isEmpty() ? "None" : String.join(", ", companyNames);
        entries.add(new ProfileEntry("Company: " + companyDisplay, startY + spacing * row, !companyNames.isEmpty(), !companyNames.isEmpty()));
        row++;

        // City row
        entries.add(new ProfileEntry("City: " + (cityName.isEmpty() ? "None" : cityName), startY + spacing * row, true, true));
        row++;

        // State row
        entries.add(new ProfileEntry("State: " + (stateName.isEmpty() ? "None" : stateName), startY + spacing * row, true, true));
        row++;

        // Nation row
        entries.add(new ProfileEntry("Nation: " + (nationName.isEmpty() ? "None" : nationName), startY + spacing * row, true, true));

        // Update nickname edit box value
        if (nicknameEditBox != null) {
            nicknameEditBox.setValue(nickname);
        }
    }

    /**
     * Helper class for profile entries
     */
    private static class ProfileEntry {
        final String text;
        final int y;
        final boolean active;
        final boolean hasButton;

        ProfileEntry(String text, int y, boolean active, boolean hasButton) {
            this.text = text;
            this.y = y;
            this.active = active;
            this.hasButton = hasButton;
        }
    }
}

