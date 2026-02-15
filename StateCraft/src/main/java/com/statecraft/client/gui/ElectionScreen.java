package com.statecraft.client.gui;

import com.statecraft.core.Election;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * GUI screen for nation elections
 * Shows election status, candidates, allows voting and registration
 */
public class ElectionScreen extends Screen {

    private SyncElectionDataPacket electionData;
    private Button voteButton;
    private Button registerButton;
    private Button refreshButton;
    private Button historyButton;
    private int selectedCandidateIndex = -1;
    private int scrollOffset = 0;
    private static final int CANDIDATE_HEIGHT = 24;
    private static final int MAX_VISIBLE_CANDIDATES = 8;

    public ElectionScreen() {
        super(Component.literal("Nation Elections"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 45;

        // Refresh button
        refreshButton = Button.builder(Component.literal("Refresh"), btn -> requestData())
            .bounds(centerX + 80, startY, 60, 20)
            .build();
        addRenderableWidget(refreshButton);

        // Vote button
        voteButton = Button.builder(Component.literal("Vote"), btn -> castVote())
            .bounds(centerX - 130, this.height - 60, 80, 20)
            .build();
        addRenderableWidget(voteButton);
        voteButton.active = false;

        // History button
        historyButton = Button.builder(Component.literal("History"), btn -> openHistoryScreen())
            .bounds(centerX - 40, this.height - 60, 80, 20)
            .build();
        addRenderableWidget(historyButton);

        // Register as candidate button
        registerButton = Button.builder(Component.literal("Run for Office"), btn -> registerAsCandidate())
            .bounds(centerX + 50, this.height - 60, 80, 20)
            .build();
        addRenderableWidget(registerButton);
        registerButton.active = false;

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(centerX - 50, this.height - 30, 100, 20)
            .build());

        // Request election data
        requestData();
    }

    private void requestData() {
        NetworkHandler.sendToServer(new RequestElectionDataPacket());
    }

    public void updateElectionData(SyncElectionDataPacket data) {
        this.electionData = data;
        this.selectedCandidateIndex = -1;
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (electionData == null) {
            voteButton.active = false;
            registerButton.active = false;
            return;
        }

        boolean isActiveElection = electionData.hasActiveElection() &&
            electionData.getStatus() == Election.Status.ACTIVE;

        // Can vote if: election is active, hasn't voted, and has selected a candidate
        voteButton.active = isActiveElection && !electionData.hasVoted() && selectedCandidateIndex >= 0;

        // Can register if: election is active and not already a candidate
        boolean isCandidate = false;
        if (minecraft != null && minecraft.player != null) {
            UUID playerId = minecraft.player.getUUID();
            for (SyncElectionDataPacket.CandidateInfo candidate : electionData.getCandidates()) {
                if (candidate.id().equals(playerId)) {
                    isCandidate = true;
                    break;
                }
            }
        }
        registerButton.active = isActiveElection && !isCandidate;
    }

    private void castVote() {
        if (electionData == null || selectedCandidateIndex < 0) return;

        List<SyncElectionDataPacket.CandidateInfo> candidates = electionData.getCandidates();
        if (selectedCandidateIndex >= candidates.size()) return;

        UUID candidateId = candidates.get(selectedCandidateIndex).id();
        NetworkHandler.sendToServer(new CastVotePacket(candidateId));

        // Refresh after a short delay
        voteButton.active = false;
    }

    private void registerAsCandidate() {
        NetworkHandler.sendToServer(new RegisterCandidatePacket());
        registerButton.active = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int centerX = this.width / 2;
        int startY = 20;

        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, startY, 0xFFFFFF);

        if (electionData == null) {
            graphics.drawCenteredString(this.font, "Loading...", centerX, startY + 50, 0xAAAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        startY += 20;

        // Nation name
        graphics.drawCenteredString(this.font, "§6" + electionData.getNationName(), centerX, startY, 0xFFFFFF);
        startY += 15;

        if (electionData.hasActiveElection() && electionData.getStatus() == Election.Status.ACTIVE) {
            renderActiveElection(graphics, centerX, startY, mouseX, mouseY);
        } else {
            renderNoElection(graphics, centerX, startY);
        }


        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderActiveElection(GuiGraphics graphics, int centerX, int startY, int mouseX, int mouseY) {
        // Time remaining
        long remaining = electionData.getEndTime() - System.currentTimeMillis();
        if (remaining > 0) {
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            graphics.drawCenteredString(this.font, "§eTime Remaining: §f" + hours + "h " + minutes + "m", centerX, startY, 0xFFFFFF);
        }
        startY += 15;

        // Voting status
        if (electionData.hasVoted()) {
            graphics.drawCenteredString(this.font, "§aYou have voted!", centerX, startY, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(this.font, "§cYou have not voted yet", centerX, startY, 0xFFFFFF);
        }
        startY += 15;

        // Vote count (just total, not per candidate)
        graphics.drawCenteredString(this.font, "§7Votes cast: " + electionData.getTotalVotes(), centerX, startY, 0xFFFFFF);
        startY += 20;

        // Candidates header
        graphics.drawString(this.font, "§6Candidates:", centerX - 100, startY, 0xFFFFFF);
        startY += 12;

        // Render candidates list
        List<SyncElectionDataPacket.CandidateInfo> candidates = electionData.getCandidates();
        int listX = centerX - 100;
        int listY = startY;
        int listWidth = 200;
        int visibleCount = Math.min(candidates.size(), MAX_VISIBLE_CANDIDATES);

        for (int i = scrollOffset; i < Math.min(scrollOffset + visibleCount, candidates.size()); i++) {
            SyncElectionDataPacket.CandidateInfo candidate = candidates.get(i);
            int itemY = listY + (i - scrollOffset) * CANDIDATE_HEIGHT;

            // Background
            boolean isSelected = i == selectedCandidateIndex;
            boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                               mouseY >= itemY && mouseY < itemY + CANDIDATE_HEIGHT;

            int bgColor = isSelected ? 0x80006600 : (isHovered ? 0x80444444 : 0x80222222);
            graphics.fill(listX, itemY, listX + listWidth, itemY + CANDIDATE_HEIGHT - 2, bgColor);

            // Name
            String displayName = candidate.name();
            if (candidate.isIncumbent()) {
                displayName += " §7(Incumbent)";
            }
            graphics.drawString(this.font, displayName, listX + 5, itemY + 7, isSelected ? 0x00FF00 : 0xFFFFFF);
        }
    }

    private void renderNoElection(GuiGraphics graphics, int centerX, int startY) {
        graphics.drawCenteredString(this.font, "§7No active election", centerX, startY, 0xFFFFFF);
        startY += 15;

        // Show next election time
        if (electionData.getNextElectionTime() > 0) {
            long timeUntil = electionData.getNextElectionTime() - System.currentTimeMillis();
            if (timeUntil > 0) {
                long days = timeUntil / (24 * 60 * 60 * 1000);
                long hours = (timeUntil % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
                graphics.drawCenteredString(this.font, "§eNext election in: §f" + days + " days, " + hours + " hours", centerX, startY, 0xFFFFFF);
            }
        } else {
            graphics.drawCenteredString(this.font, "§7No elections scheduled", centerX, startY, 0xFFFFFF);
        }

        // Show completed election results if available
        if (electionData.getStatus() == Election.Status.COMPLETED &&
            electionData.getWinnerName() != null && !electionData.getWinnerName().isEmpty()) {
            startY += 30;
            graphics.drawCenteredString(this.font, "§6Last Election Results:", centerX, startY, 0xFFFFFF);
            startY += 15;
            graphics.drawCenteredString(this.font, "§aWinner: §f" + electionData.getWinnerName(), centerX, startY, 0xFFFFFF);

            // Show vote breakdown
            startY += 15;
            Map<UUID, Integer> results = electionData.getResults();
            for (SyncElectionDataPacket.CandidateInfo candidate : electionData.getCandidates()) {
                int votes = results.getOrDefault(candidate.id(), 0);
                graphics.drawCenteredString(this.font, "§7" + candidate.name() + ": " + votes + " votes", centerX, startY, 0xFFFFFF);
                startY += 12;
            }
        }
    }

    private void openHistoryScreen() {
        if (electionData != null) {
            this.minecraft.setScreen(new ElectionHistoryScreen(electionData.getHistory(), electionData.getNationName()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (electionData != null && electionData.hasActiveElection()) {
            int centerX = this.width / 2;
            int listX = centerX - 100;
            int listY = 107; // Approximate Y where candidates start
            int listWidth = 200;

            List<SyncElectionDataPacket.CandidateInfo> candidates = electionData.getCandidates();
            int visibleCount = Math.min(candidates.size(), MAX_VISIBLE_CANDIDATES);

            for (int i = scrollOffset; i < Math.min(scrollOffset + visibleCount, candidates.size()); i++) {
                int itemY = listY + (i - scrollOffset) * CANDIDATE_HEIGHT;
                if (mouseX >= listX && mouseX < listX + listWidth &&
                    mouseY >= itemY && mouseY < itemY + CANDIDATE_HEIGHT) {
                    selectedCandidateIndex = i;
                    updateButtonStates();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (electionData != null) {
            int maxScroll = Math.max(0, electionData.getCandidates().size() - MAX_VISIBLE_CANDIDATES);
            if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else {
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

