package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.*;
import com.statecraft.network.packets.SyncEmergencyPowerDataPacket.PowerEntry;
import com.statecraft.network.packets.SyncEmergencyPowerDataPacket.PowerStatus;
import com.statecraft.network.packets.SyncEmergencyPowerDataPacket.HistoryEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Executive Actions screen — allows the nation leader to invoke/revoke emergency powers.
 * Only accessible by the nation leader from the Country Management screen.
 *
 * Shows each emergency power with its status (Available/Active/Cooldown),
 * an invoke/revoke button, and an optional target input for powers that require one.
 */
public class ExecutiveActionsScreen extends StateCraftScreen {

    private final String nationName;
    private boolean isLeader = false;
    private boolean dataLoaded = false;
    private List<PowerEntry> powers = new ArrayList<>();
    private List<HistoryEntry> history = new ArrayList<>();
    private String resultMessage = "";
    private long resultMessageTime = 0;

    // View mode
    private enum ViewMode { POWERS, HISTORY }
    private ViewMode viewMode = ViewMode.POWERS;

    // Scrolling
    private int scrollOffset = 0;
    private int historyScrollOffset = 0;
    private static final int ENTRY_HEIGHT = 56;
    private static final int HISTORY_ENTRY_HEIGHT = 30;
    private static final int MAX_VISIBLE = 4;
    private static final int MAX_HISTORY_VISIBLE = 6;

    // Target input for powers that require a target
    private EditBox targetInput;
    private String selectedPowerForTarget = null;

    // Confirmation dialog
    private boolean showConfirmation = false;
    private String confirmPowerName = null;
    private String confirmDisplayName = null;

    public ExecutiveActionsScreen(String nationName) {
        super(Component.literal("Executive Actions"));
        this.nationName = nationName;
        this.guiWidth = 320;
        this.guiHeight = 256;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        // Request data from server
        NetworkHandler.sendToServer(new RequestEmergencyPowerDataPacket(nationName));

        // Tab buttons
        this.addRenderableWidget(createButton(
            guiLeft + 10, guiTop + guiHeight - 28, 60, 20,
            Component.literal(viewMode == ViewMode.POWERS ? "§f⚡ Powers" : "§7⚡ Powers"),
            btn -> { viewMode = ViewMode.POWERS; scrollOffset = 0; rebuildButtons(); }
        ));
        this.addRenderableWidget(createButton(
            guiLeft + 75, guiTop + guiHeight - 28, 60, 20,
            Component.literal(viewMode == ViewMode.HISTORY ? "§f📜 History" : "§7📜 History"),
            btn -> { viewMode = ViewMode.HISTORY; historyScrollOffset = 0; rebuildButtons(); }
        ));

        // Target input (hidden initially)
        targetInput = new EditBox(this.font, guiLeft + 15, guiTop + guiHeight - 52, guiWidth - 110, 16,
            Component.literal("Target"));
        targetInput.setMaxLength(40);
        targetInput.setVisible(false);
        targetInput.setHint(Component.literal("§7Enter target..."));
        this.addRenderableWidget(targetInput);

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 70, guiTop + guiHeight - 28, 60, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    /**
     * Called by ClientPacketHandler when emergency power data arrives.
     */
    public void updateData(SyncEmergencyPowerDataPacket packet) {
        this.isLeader = packet.isLeader();
        this.powers = packet.getPowers();
        this.history = packet.getHistory();
        this.dataLoaded = true;

        if (!packet.getResultMessage().isEmpty()) {
            this.resultMessage = packet.getResultMessage();
            this.resultMessageTime = System.currentTimeMillis();
        }

        rebuildButtons();
    }

    private void rebuildButtons() {
        // Remove old action buttons (keep targetInput and back button)
        this.clearWidgets();
        init();

        if (!dataLoaded || !isLeader) return;
        if (viewMode != ViewMode.POWERS) return;

        int startY = guiTop + 26;
        int btnX = guiLeft + guiWidth - 85;

        for (int i = 0; i < powers.size(); i++) {
            int displayIndex = i - scrollOffset;
            if (displayIndex < 0 || displayIndex >= MAX_VISIBLE) continue;

            int y = startY + displayIndex * ENTRY_HEIGHT;
            PowerEntry entry = powers.get(i);

            switch (entry.status) {
                case AVAILABLE -> {
                    if (entry.requiresTarget) {
                        this.addRenderableWidget(createButton(
                            btnX, y + 2, 70, 14,
                            Component.literal("§c⚡ Invoke"),
                            btn -> showTargetInput(entry.powerName, entry.displayName)
                        ));
                    } else {
                        this.addRenderableWidget(createButton(
                            btnX, y + 2, 70, 14,
                            Component.literal("§c⚡ Invoke"),
                            btn -> confirmInvoke(entry.powerName, entry.displayName)
                        ));
                    }
                }
                case ACTIVE -> {
                    if (entry.durationHours > 0) {
                        this.addRenderableWidget(createButton(
                            btnX, y + 2, 70, 14,
                            Component.literal("§a✕ Revoke"),
                            btn -> revokePower(entry.powerName)
                        ));
                    }
                }
                case COOLDOWN, INSTANT_COOLDOWN -> {
                    // No button — on cooldown
                }
            }
        }
    }

    private void showTargetInput(String powerName, String displayName) {
        this.selectedPowerForTarget = powerName;
        targetInput.setVisible(true);
        targetInput.setValue("");
        targetInput.setFocused(true);

        // Add submit button next to input
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 90, guiTop + guiHeight - 52, 75, 16,
            Component.literal("§cConfirm"),
            btn -> {
                String target = targetInput.getValue().trim();
                if (!target.isEmpty()) {
                    invokePower(powerName, target);
                    targetInput.setVisible(false);
                    selectedPowerForTarget = null;
                }
            }
        ));
    }

    private void confirmInvoke(String powerName, String displayName) {
        this.showConfirmation = true;
        this.confirmPowerName = powerName;
        this.confirmDisplayName = displayName;
    }

    private void invokePower(String powerName, String targetValue) {
        NetworkHandler.sendToServer(new InvokeEmergencyPowerPacket(
            nationName, powerName, InvokeEmergencyPowerPacket.Action.INVOKE, targetValue));
        showConfirmation = false;
    }

    private void revokePower(String powerName) {
        NetworkHandler.sendToServer(new InvokeEmergencyPowerPacket(
            nationName, powerName, InvokeEmergencyPowerPacket.Action.REVOKE, ""));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = guiLeft + 10;

        // Title bar
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 22, 0xAA808080);
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 21, guiLeft + guiWidth - 10, guiTop + 22, 0xFF404040);
        graphics.drawString(this.font, "§c⚡ §fExecutive Actions", startX + 4, guiTop + 9, 0xFFFFFFFF);
        graphics.drawString(this.font, "§7" + nationName, guiLeft + guiWidth - 10 - this.font.width(nationName), guiTop + 9, 0xFF999999);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        if (!isLeader) {
            graphics.drawCenteredString(this.font, "§cOnly the nation leader can use executive actions",
                this.width / 2, guiTop + 80, COLOR_WARNING);
            graphics.drawCenteredString(this.font, "§7Emergency powers require Leader authority",
                this.width / 2, guiTop + 95, 0xFF999999);
            return;
        }

        if (viewMode == ViewMode.HISTORY) {
            renderHistoryView(graphics, startX, mouseX, mouseY);
            return;
        }

        // Render power entries
        int contentTop = guiTop + 26;
        int contentBottom = guiTop + guiHeight - 60;

        // Clip area
        for (int i = 0; i < powers.size(); i++) {
            int displayIndex = i - scrollOffset;
            if (displayIndex < 0 || displayIndex >= MAX_VISIBLE) continue;

            int y = contentTop + displayIndex * ENTRY_HEIGHT;
            if (y + ENTRY_HEIGHT > contentBottom) break;

            PowerEntry entry = powers.get(i);
            renderPowerEntry(graphics, startX, y, entry, mouseX, mouseY);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "▲", this.width / 2, contentTop - 8, 0xFFAAAAAA);
        }
        if (scrollOffset + MAX_VISIBLE < powers.size()) {
            graphics.drawCenteredString(this.font, "▼", this.width / 2, contentBottom - 2, 0xFFAAAAAA);
        }

        // Target input label
        if (targetInput.isVisible() && selectedPowerForTarget != null) {
            graphics.drawString(this.font, "§eTarget for action:", guiLeft + 15, guiTop + guiHeight - 65, 0xFFFFAA00);
        }

        // Result message (fades after 5 seconds)
        if (!resultMessage.isEmpty() && System.currentTimeMillis() - resultMessageTime < 5000) {
            int msgWidth = this.font.width(resultMessage.replaceAll("§.", ""));
            int msgX = this.width / 2 - msgWidth / 2 - 5;
            graphics.fill(msgX - 3, guiTop + guiHeight - 75, msgX + msgWidth + 8, guiTop + guiHeight - 63, 0xDD000000);
            graphics.drawCenteredString(this.font, resultMessage, this.width / 2, guiTop + guiHeight - 73, 0xFFFFFFFF);
        }

        // Confirmation dialog is rendered in render() override so it appears on top of all widgets
    }

    private void renderHistoryView(GuiGraphics graphics, int startX, int mouseX, int mouseY) {
        int contentTop = guiTop + 26;
        int contentBottom = guiTop + guiHeight - 60;

        if (history.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No executive action history yet.",
                this.width / 2, guiTop + 100, 0xFF999999);
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm");

        for (int i = 0; i < history.size(); i++) {
            int displayIndex = i - historyScrollOffset;
            if (displayIndex < 0 || displayIndex >= MAX_HISTORY_VISIBLE) continue;

            int y = contentTop + displayIndex * HISTORY_ENTRY_HEIGHT;
            if (y + HISTORY_ENTRY_HEIGHT > contentBottom) break;

            HistoryEntry entry = history.get(i);
            renderHistoryEntry(graphics, startX, y, entry, dateFormat);
        }

        // Scroll indicators
        if (historyScrollOffset > 0) {
            graphics.drawCenteredString(this.font, "▲", this.width / 2, contentTop - 8, 0xFFAAAAAA);
        }
        if (historyScrollOffset + MAX_HISTORY_VISIBLE < history.size()) {
            graphics.drawCenteredString(this.font, "▼", this.width / 2, contentBottom - 2, 0xFFAAAAAA);
        }

        // Total count
        graphics.drawString(this.font, "§8" + history.size() + " events",
            guiLeft + guiWidth - 10 - this.font.width(history.size() + " events"),
            contentBottom + 2, 0xFF666666);
    }

    private void renderHistoryEntry(GuiGraphics graphics, int startX, int y, HistoryEntry entry, SimpleDateFormat dateFormat) {
        int width = guiWidth - 20;
        int entryRight = startX + width;

        // Background color based on event type
        int bgColor = switch (entry.eventType) {
            case "Invoked" -> 0x33CC4444;     // Red
            case "Revoked" -> 0x3344CC44;     // Green
            case "Expired" -> 0x33666666;     // Gray
            case "Ratified" -> 0x334488CC;    // Blue
            case "Not Ratified" -> 0x33CC8844; // Orange
            case "Overridden" -> 0x33CCCC44;  // Yellow
            default -> 0x33444444;
        };
        graphics.fill(startX, y, entryRight, y + HISTORY_ENTRY_HEIGHT - 2, bgColor);
        graphics.fill(startX, y, entryRight, y + 1, 0xFF404040);

        // Event type badge color
        String typeColor = switch (entry.eventType) {
            case "Invoked" -> "§c";
            case "Revoked" -> "§a";
            case "Expired" -> "§8";
            case "Ratified" -> "§b";
            case "Not Ratified" -> "§6";
            case "Overridden" -> "§e";
            default -> "§7";
        };

        // Line 1: [EVENT_TYPE] Power Name — by Actor
        String line1 = typeColor + "[" + entry.eventType.toUpperCase() + "] §f" + entry.powerDisplayName +
            " §7— by §f" + entry.actorName;
        graphics.drawString(this.font, line1, startX + 4, y + 4, 0xFFFFFFFF);

        // Line 2: Timestamp + details
        String timeStr = dateFormat.format(new Date(entry.timestamp));
        String line2 = "§8" + timeStr;
        if (!entry.details.isEmpty()) {
            line2 += " §7| " + entry.details;
        }
        // Trim if too long
        if (this.font.width(line2.replaceAll("§.", "")) > width - 8) {
            line2 = line2.substring(0, Math.min(line2.length(), 60)) + "...";
        }
        graphics.drawString(this.font, line2, startX + 4, y + 16, 0xFF888888);
    }

    private void renderPowerEntry(GuiGraphics graphics, int startX, int y, PowerEntry entry, int mouseX, int mouseY) {
        int width = guiWidth - 20;
        int entryRight = startX + width;

        // Background
        int bgColor = switch (entry.status) {
            case ACTIVE -> 0x44CC4444;  // Red tint when active
            case COOLDOWN, INSTANT_COOLDOWN -> 0x44666666; // Gray when on cooldown
            case AVAILABLE -> 0x44444488; // Blue when available
        };
        graphics.fill(startX, y, entryRight, y + ENTRY_HEIGHT - 2, bgColor);
        graphics.fill(startX, y, entryRight, y + 1, 0xFF505050);
        graphics.fill(startX, y + ENTRY_HEIGHT - 3, entryRight, y + ENTRY_HEIGHT - 2, 0xFF404040);

        // Power name and status
        String statusText = switch (entry.status) {
            case AVAILABLE -> "§a[AVAILABLE]";
            case ACTIVE -> "§c[ACTIVE]";
            case COOLDOWN, INSTANT_COOLDOWN -> "§8[COOLDOWN]";
        };

        graphics.drawString(this.font, "§f" + entry.displayName, startX + 4, y + 4, 0xFFFFFFFF);
        graphics.drawString(this.font, statusText, entryRight - this.font.width(statusText.replaceAll("§.", "")) - 75, y + 4, 0xFFFFFFFF);

        // Description
        graphics.drawString(this.font, "§7" + entry.description, startX + 4, y + 16, 0xFFAAAAAA);

        // Duration/Cooldown info
        String infoLine = "";
        if (entry.status == PowerStatus.ACTIVE && entry.remainingMs > 0) {
            long hours = entry.remainingMs / (1000 * 60 * 60);
            long mins = (entry.remainingMs / (1000 * 60)) % 60;
            infoLine = "§eExpires in: " + hours + "h " + mins + "m";
        } else if (entry.status == PowerStatus.COOLDOWN || entry.status == PowerStatus.INSTANT_COOLDOWN) {
            if (entry.remainingMs > 0) {
                long days = entry.remainingMs / (1000 * 60 * 60 * 24);
                long hours = (entry.remainingMs / (1000 * 60 * 60)) % 24;
                infoLine = "§8Cooldown: " + days + "d " + hours + "h remaining";
            }
        } else if (entry.status == PowerStatus.AVAILABLE) {
            if (entry.durationHours > 0) {
                infoLine = "§7Duration: " + entry.durationHours + "h  |  Cooldown: " + entry.cooldownDays + " days";
            } else {
                infoLine = "§7Instant  |  Cooldown: " + entry.cooldownDays + " days";
            }
        }

        if (!infoLine.isEmpty()) {
            graphics.drawString(this.font, infoLine, startX + 4, y + 28, 0xFF888888);
        }

        // Target requirement hint
        if (entry.requiresTarget && entry.status == PowerStatus.AVAILABLE) {
            graphics.drawString(this.font, "§8Requires target input", startX + 4, y + 40, 0xFF666666);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showConfirmation) {
            int dialogWidth = 220;
            int dialogHeight = 80;
            int dx = this.width / 2 - dialogWidth / 2;
            int dy = this.height / 2 - dialogHeight / 2;

            int btnY = dy + dialogHeight - 24;
            // Confirm button
            if (mouseX >= dx + 20 && mouseX <= dx + 100 && mouseY >= btnY && mouseY <= btnY + 18) {
                invokePower(confirmPowerName, "");
                return true;
            }
            // Cancel button
            if (mouseX >= dx + 120 && mouseX <= dx + 200 && mouseY >= btnY && mouseY <= btnY + 18) {
                showConfirmation = false;
                confirmPowerName = null;
                confirmDisplayName = null;
                return true;
            }
            return true; // Consume all clicks when dialog is open
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render confirmation dialog on top of everything (after all widgets)
        if (showConfirmation) {
            int dialogWidth = 220;
            int dialogHeight = 80;
            int dx = this.width / 2 - dialogWidth / 2;
            int dy = this.height / 2 - dialogHeight / 2;
            int btnY = dy + dialogHeight - 24;

            // Full-screen opaque dimming overlay to block all content behind the dialog
            graphics.fill(0, 0, this.width, this.height, 0xCC000000);

            // Dialog background (fully opaque)
            graphics.fill(dx - 2, dy - 2, dx + dialogWidth + 2, dy + dialogHeight + 2, 0xFF000000);
            graphics.fill(dx, dy, dx + dialogWidth, dy + dialogHeight, 0xFF1A1A2E);
            graphics.fill(dx, dy, dx + dialogWidth, dy + 1, COLOR_WARNING);

            // Text
            graphics.drawCenteredString(this.font, "§c§lConfirm Executive Action", this.width / 2, dy + 8, 0xFFFF4444);
            graphics.drawCenteredString(this.font, "§fInvoke §e" + confirmDisplayName + "§f?", this.width / 2, dy + 24, 0xFFFFFFFF);
            graphics.drawCenteredString(this.font, "§7This action has serious consequences.", this.width / 2, dy + 36, 0xFF999999);

            // Confirm button
            boolean hoverConfirm = mouseX >= dx + 20 && mouseX <= dx + 100 && mouseY >= btnY && mouseY <= btnY + 18;
            graphics.fill(dx + 20, btnY, dx + 100, btnY + 18, hoverConfirm ? 0xFFCC3333 : 0xFF882222);
            graphics.drawCenteredString(this.font, "§cConfirm", dx + 60, btnY + 5, 0xFFFFFFFF);

            // Cancel button
            boolean hoverCancel = mouseX >= dx + 120 && mouseX <= dx + 200 && mouseY >= btnY && mouseY <= btnY + 18;
            graphics.fill(dx + 120, btnY, dx + 200, btnY + 18, hoverCancel ? 0xFF555555 : 0xFF333333);
            graphics.drawCenteredString(this.font, "§7Cancel", dx + 160, btnY + 5, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showConfirmation) return true;
        if (viewMode == ViewMode.HISTORY) {
            if (delta > 0 && historyScrollOffset > 0) {
                historyScrollOffset--;
            } else if (delta < 0 && historyScrollOffset + MAX_HISTORY_VISIBLE < history.size()) {
                historyScrollOffset++;
            }
        } else {
            if (delta > 0 && scrollOffset > 0) {
                scrollOffset--;
                rebuildButtons();
            } else if (delta < 0 && scrollOffset + MAX_VISIBLE < powers.size()) {
                scrollOffset++;
                rebuildButtons();
            }
        }
        return true;
    }

    private void goBack() {
        this.minecraft.setScreen(new CountryManagementScreen(nationName, true, true, true));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

