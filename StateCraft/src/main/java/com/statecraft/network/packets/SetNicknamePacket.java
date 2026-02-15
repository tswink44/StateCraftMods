package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet to set a player's nickname
 */
public class SetNicknamePacket {
    private final String nickname;

    public SetNicknamePacket(String nickname) {
        this.nickname = nickname;
    }

    public SetNicknamePacket(FriendlyByteBuf buf) {
        this.nickname = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nickname, 32);
    }

    public String getNickname() {
        return nickname;
    }
}

