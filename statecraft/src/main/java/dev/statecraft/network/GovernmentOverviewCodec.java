package dev.statecraft.network;

import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiAction;
import java.util.ArrayList;
import net.minecraft.network.FriendlyByteBuf;

final class GovernmentOverviewCodec {
    private GovernmentOverviewCodec() {}

    static void request(FriendlyByteBuf buffer, GovernmentOverview.Request query) {
        buffer.writeUtf(query.governmentId(), 64);
        buffer.writeVarInt(query.children());
        buffer.writeVarInt(query.officers());
    }

    static GovernmentOverview.Request request(FriendlyByteBuf buffer) {
        return new GovernmentOverview.Request(buffer.readUtf(64), UiCodec.count(buffer, PersonalDashboard.MAX_OFFSET),
                UiCodec.count(buffer, PersonalDashboard.MAX_OFFSET));
    }

    static void overview(FriendlyByteBuf buffer, GovernmentOverview data) {
        buffer.writeUtf(data.id(), 64);
        buffer.writeEnum(data.kind());
        buffer.writeUtf(data.name(), 128);
        buffer.writeUtf(data.flag(), 128);
        buffer.writeUtf(data.description(), 4096);
        DashboardCodec.optionalEntry(buffer, data.leader());
        DashboardCodec.optionalEntry(buffer, data.parent());
        UiCodec.text(buffer, data.treasury());
        DashboardCodec.page(buffer, data.children());
        DashboardCodec.page(buffer, data.officers());
        buffer.writeVarInt(data.groups().size());
        for (var group : data.groups()) {
            buffer.writeUtf(group.id(), 64);
            UiCodec.text(buffer, group.title());
            buffer.writeVarInt(group.actions().size());
            for (UiAction action : group.actions()) {
                buffer.writeUtf(action.page(), 96);
                buffer.writeUtf(action.template(), 4096);
                UiCodec.text(buffer, action.label());
                FormCodec.writeValues(buffer, action.values());
                buffer.writeBoolean(action.enabled());
                UiCodec.text(buffer, action.disabledReason());
            }
        }
        buffer.writeBoolean(data.officialInbox() != null);
        if (data.officialInbox() != null) UiCodec.query(buffer, data.officialInbox());
    }

    static GovernmentOverview overview(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(64);
        GovernanceAccess.Kind kind = buffer.readEnum(GovernanceAccess.Kind.class);
        String name = buffer.readUtf(128), flag = buffer.readUtf(128), description = buffer.readUtf(4096);
        var leader = DashboardCodec.optionalEntry(buffer);
        var parent = DashboardCodec.optionalEntry(buffer);
        var treasury = UiCodec.text(buffer);
        var children = DashboardCodec.page(buffer);
        var officers = DashboardCodec.page(buffer);
        int count = UiCodec.count(buffer, 8);
        var groups = new ArrayList<GovernmentOverview.Group>(count);
        for (int i = 0; i < count; i++) {
            String groupId = buffer.readUtf(64);
            var title = UiCodec.text(buffer);
            int actionCount = UiCodec.count(buffer, 32);
            var actions = new ArrayList<UiAction>(actionCount);
            for (int action = 0; action < actionCount; action++) {
                actions.add(new UiAction(buffer.readUtf(96), buffer.readUtf(4096), UiCodec.text(buffer),
                        FormCodec.readValues(buffer), buffer.readBoolean(), UiCodec.text(buffer)));
            }
            groups.add(new GovernmentOverview.Group(groupId, title, actions));
        }
        var inbox = buffer.readBoolean() ? UiCodec.query(buffer) : null;
        return new GovernmentOverview(id, kind, name, flag, description, leader, parent, treasury, children, officers, groups, inbox);
    }
}
