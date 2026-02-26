package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sends autocomplete suggestions for Trading Hub settings.
 * Contains all company names and all online player names.
 */
public class SyncTradingHubSuggestionsPacket {
    private final List<String> companyNames;
    private final List<String> playerNames;

    public SyncTradingHubSuggestionsPacket(List<String> companyNames, List<String> playerNames) {
        this.companyNames = companyNames;
        this.playerNames = playerNames;
    }

    public SyncTradingHubSuggestionsPacket(FriendlyByteBuf buf) {
        int companyCount = buf.readVarInt();
        this.companyNames = new ArrayList<>(companyCount);
        for (int i = 0; i < companyCount; i++) {
            companyNames.add(buf.readUtf(64));
        }

        int playerCount = buf.readVarInt();
        this.playerNames = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            playerNames.add(buf.readUtf(64));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(companyNames.size());
        for (String name : companyNames) {
            buf.writeUtf(name, 64);
        }

        buf.writeVarInt(playerNames.size());
        for (String name : playerNames) {
            buf.writeUtf(name, 64);
        }
    }

    public List<String> getCompanyNames() { return companyNames; }
    public List<String> getPlayerNames() { return playerNames; }
}

