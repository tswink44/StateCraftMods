package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Update nation settings (admin only)
 */
public class UpdateNationSettingsPacket {
    private final String nationName;
    private final String tag;
    private final String description;
    private final boolean open;

    public UpdateNationSettingsPacket(String nationName, String tag, String description, boolean open) {
        this.nationName = nationName;
        this.tag = tag;
        this.description = description;
        this.open = open;
    }

    public UpdateNationSettingsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(24);
        this.tag = buf.readUtf(5);
        this.description = buf.readUtf(100);
        this.open = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 24);
        buf.writeUtf(tag, 5);
        buf.writeUtf(description, 100);
        buf.writeBoolean(open);
    }

    public String getNationName() {
        return nationName;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOpen() {
        return open;
    }
}

