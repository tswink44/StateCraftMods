package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestMembersPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing all members of a nation
 */
public class MembersListScreen extends StateCraftScreen {
    private final String nationName;
    private List<MemberData> members = new ArrayList<>();
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 10;

    public MembersListScreen(String nationName) {
        super(Component.literal("Members: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 260; this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Request members data from server
        NetworkHandler.sendToServer(new RequestMembersPacket(nationName));

        // Invite button
        this.addRenderableWidget(createButton(
            guiLeft + 15, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Invite"),
            btn -> invitePlayer()
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
            graphics.drawCenteredString(this.font, "§7Loading members...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        if (members.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No members found", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        // Member count
        graphics.drawString(this.font, "§7Total: §f" + members.size() + " members", guiLeft + 15, guiTop + 28, COLOR_TEXT);

        int y = guiTop + 44;
        for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, members.size()); i++) {
            MemberData member = members.get(i);

            // Online indicator
            String onlineIndicator = member.online ? "§a● " : "§8○ ";

            // Role color
            String roleColor = member.role.equals("Leader") ? "§6" :
                              (member.role.equals("Admin") ? "§e" :
                              (member.role.equals("Officer") ? "§b" : "§f"));

            // Role badge
            String roleBadge = member.role.equals("Leader") ? " §8[§6L§8]" :
                              (member.role.equals("Admin") ? " §8[§eA§8]" :
                              (member.role.equals("Officer") ? " §8[§bO§8]" : ""));

            graphics.drawString(this.font, onlineIndicator + roleColor + member.name + roleBadge,
                               guiLeft + 15, y, COLOR_TEXT);
            y += 14;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 20, guiTop + 44, 0xFF888888);
        }
        if (scrollOffset + MAX_VISIBLE < members.size()) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 20, guiTop + guiHeight - 50, 0xFF888888);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + MAX_VISIBLE < members.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void invitePlayer() {
        // Open the invite player dialog
        this.minecraft.setScreen(new InvitePlayerScreen(
            InvitePlayerScreen.InviteTarget.NATION,
            nationName,
            () -> this.minecraft.setScreen(new MembersListScreen(nationName))
        ));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    public void updateMembers(List<MemberData> members) {
        this.members = members;
        this.dataLoaded = true;
    }

    public static class MemberData {
        public String name;
        public String role;
        public boolean online;

        public MemberData(String name, String role, boolean online) {
            this.name = name;
            this.role = role;
            this.online = online;
        }
    }
}

