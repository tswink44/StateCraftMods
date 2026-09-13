package dev.statecraft.network;

import dev.statecraft.api.ui.PersonalDashboard;
import java.util.ArrayList;
import net.minecraft.network.FriendlyByteBuf;

final class DashboardCodec {
    private DashboardCodec() {}

    static void request(FriendlyByteBuf buffer, PersonalDashboard.Request request) {
        buffer.writeVarInt(request.accounts());
        buffer.writeVarInt(request.companies());
        buffer.writeVarInt(request.propertyCities());
    }

    static PersonalDashboard.Request request(FriendlyByteBuf buffer) {
        return new PersonalDashboard.Request(UiCodec.count(buffer, PersonalDashboard.MAX_OFFSET),
                UiCodec.count(buffer, PersonalDashboard.MAX_OFFSET), UiCodec.count(buffer, PersonalDashboard.MAX_OFFSET));
    }

    static void entry(FriendlyByteBuf buffer, PersonalDashboard.Entry entry) {
        UiCodec.text(buffer, entry.name());
        UiCodec.text(buffer, entry.detail());
        UiCodec.query(buffer, entry.target());
    }

    static PersonalDashboard.Entry entry(FriendlyByteBuf buffer) {
        return new PersonalDashboard.Entry(UiCodec.text(buffer), UiCodec.text(buffer), UiCodec.query(buffer));
    }

    static void optionalEntry(FriendlyByteBuf buffer, PersonalDashboard.Entry entry) {
        buffer.writeBoolean(entry != null);
        if (entry != null) entry(buffer, entry);
    }

    static PersonalDashboard.Entry optionalEntry(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? entry(buffer) : null;
    }

    static void page(FriendlyByteBuf buffer, PersonalDashboard.Page page) {
        buffer.writeVarInt(page.entries().size());
        for (var entry : page.entries()) entry(buffer, entry);
        buffer.writeVarInt(page.offset());
        buffer.writeVarInt(page.total());
    }

    static PersonalDashboard.Page page(FriendlyByteBuf buffer) {
        int size = UiCodec.count(buffer, PersonalDashboard.PAGE_SIZE);
        var entries = new ArrayList<PersonalDashboard.Entry>(size);
        for (int i = 0; i < size; i++) entries.add(entry(buffer));
        return new PersonalDashboard.Page(entries, UiCodec.count(buffer, PersonalDashboard.MAX_OFFSET),
                UiCodec.count(buffer, Integer.MAX_VALUE));
    }

    static void dashboard(FriendlyByteBuf buffer, PersonalDashboard data) {
        buffer.writeUtf(data.name(), 64);
        optionalEntry(buffer, data.nation());
        optionalEntry(buffer, data.state());
        optionalEntry(buffer, data.city());
        buffer.writeBoolean(data.economyAvailable());
        page(buffer, data.accounts());
        page(buffer, data.companies());
        page(buffer, data.propertyCities());
        UiCodec.text(buffer, data.notice());
    }

    static PersonalDashboard dashboard(FriendlyByteBuf buffer) {
        return new PersonalDashboard(buffer.readUtf(64), optionalEntry(buffer), optionalEntry(buffer), optionalEntry(buffer),
                buffer.readBoolean(), page(buffer), page(buffer), page(buffer), UiCodec.text(buffer));
    }
}
