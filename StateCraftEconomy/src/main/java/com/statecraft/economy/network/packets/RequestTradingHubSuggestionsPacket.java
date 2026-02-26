package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request autocomplete suggestions (company names) for Trading Hub settings
 */
public class RequestTradingHubSuggestionsPacket {

    public RequestTradingHubSuggestionsPacket() {}

    public RequestTradingHubSuggestionsPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}
}

