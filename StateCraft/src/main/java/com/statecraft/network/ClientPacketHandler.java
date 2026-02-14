package com.statecraft.network;

import com.statecraft.client.ChunkBorderCache;
import com.statecraft.client.gui.*;
import com.statecraft.client.render.ChunkBorderRenderer;
import com.statecraft.client.render.TerritoryBorderRenderer;
import com.statecraft.network.packets.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Handles packets received on the client from the server
 */
public class ClientPacketHandler {

    public static void handleSyncNationData(SyncNationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MainMenuScreen screen) {
                if (packet.isInNation()) {
                    screen.updateNationData(
                        packet.getNationName(),
                        packet.getStateName(),
                        packet.getCityName(),
                        packet.getChunks(),
                        packet.getMembers(),
                        packet.isLeader(),
                        packet.isAdmin()
                    );
                } else {
                    screen.setNoNation();
                }
            } else if (mc.screen instanceof ClaimsManagementScreen screen) {
                if (packet.isInNation()) {
                    screen.updateContext(
                        packet.getNationName(),
                        packet.getStateName(),
                        packet.getCityName()
                    );
                } else {
                    screen.setNoNation();
                }
            } else if (mc.screen instanceof NationInfoScreen screen) {
                if (packet.isDetailedData()) {
                    screen.updateData(
                        packet.getStates(),
                        packet.getMaxStates(),
                        packet.getCities(),
                        packet.getChunks(),
                        packet.getMembers(),
                        packet.getBalance(),
                        packet.isOpen(),
                        packet.getDescription(),
                        packet.getLeaderName(),
                        packet.isLeader(),
                        packet.isAdmin(),
                        packet.getStateNames(),
                        packet.getAllyNames(),
                        packet.getEnemyNames()
                    );
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncChunkMap(SyncChunkMapPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChunkMapScreen screen) {
                // Convert to screen's data format
                java.util.Map<Long, ChunkMapScreen.ChunkData> chunkData = new java.util.HashMap<>();
                for (Map.Entry<Long, SyncChunkMapPacket.ChunkInfo> entry : packet.getChunks().entrySet()) {
                    SyncChunkMapPacket.ChunkInfo info = entry.getValue();
                    chunkData.put(entry.getKey(), new ChunkMapScreen.ChunkData(
                        info.nationName,
                        info.cityName,
                        info.isPlayerNation,
                        info.isAlly,
                        info.isEnemy,
                        info.canManage
                    ));
                }
                screen.updateMapData(packet.getPlayerX(), packet.getPlayerZ(), packet.getPlayerNation(), chunkData);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncInvitations(SyncInvitationsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof InvitationsScreen screen) {
                List<InvitationsScreen.InvitationData> invites = new ArrayList<>();
                for (SyncInvitationsPacket.InviteInfo info : packet.getInvitations()) {
                    invites.add(new InvitationsScreen.InvitationData(
                        info.id,
                        info.type,
                        info.entityName,
                        info.senderName,
                        info.remainingSeconds
                    ));
                }
                screen.updateInvitations(invites);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleActionResult(ActionResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();

            if (packet.isSuccess()) {
                // Handle successful actions
                if (mc.screen instanceof CreateNationScreen screen) {
                    screen.onNationCreated(packet.getMessage());
                } else if (mc.screen instanceof InvitationsScreen screen) {
                    // If message looks like a nation name after accepting invite
                    if (packet.getMessage().contains("accepted")) {
                        // Stay on screen, it will refresh
                    }
                } else if (mc.screen instanceof ChunkMapScreen) {
                    // Refresh map after chunk claim/unclaim
                    NetworkHandler.sendToServer(new RequestChunkMapPacket(11));
                } else if (mc.screen instanceof CityInfoScreen) {
                    // Show success message
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§a" + packet.getMessage())
                        );
                    }
                }

                // Generic success message for certain screens
                if (mc.screen instanceof CreateStateScreen || mc.screen instanceof CreateCityScreen) {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§a" + packet.getMessage())
                        );
                    }
                }
            } else {
                // Handle failures
                if (mc.screen instanceof CreateNationScreen screen) {
                    screen.onCreationFailed(packet.getMessage());
                }

                // Show error message to player
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§c" + packet.getMessage())
                    );
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenGui(OpenGuiPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();

            switch (packet.getScreenType()) {
                case MAIN_MENU -> mc.setScreen(new MainMenuScreen());
                case NATION_INFO -> {
                    if (!packet.getNationName().isEmpty()) {
                        mc.setScreen(new NationInfoScreen(packet.getNationName()));
                    } else {
                        mc.setScreen(new MainMenuScreen());
                    }
                }
                case STATE_INFO -> {
                    if (!packet.getNationName().isEmpty() && !packet.getStateName().isEmpty()) {
                        mc.setScreen(new StateInfoScreen(packet.getNationName(), packet.getStateName()));
                    } else {
                        mc.setScreen(new MainMenuScreen());
                    }
                }
                case CITY_INFO -> {
                    if (!packet.getNationName().isEmpty() && !packet.getStateName().isEmpty() && !packet.getCityName().isEmpty()) {
                        mc.setScreen(new CityInfoScreen(packet.getNationName(), packet.getStateName(), packet.getCityName()));
                    } else {
                        mc.setScreen(new MainMenuScreen());
                    }
                }
                case CHUNK_INFO -> {
                    // Always open ChunkInfoScreen for chunk info command
                    mc.setScreen(new ChunkInfoScreen(packet.getChunkX(), packet.getChunkZ()));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncChunkBorders(SyncChunkBordersPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Convert packet data to cache format
            Map<Long, ChunkBorderCache.ChunkClaimInfo> cacheData = new java.util.HashMap<>();

            for (Map.Entry<Long, SyncChunkBordersPacket.ChunkBorderInfo> entry : packet.getChunks().entrySet()) {
                SyncChunkBordersPacket.ChunkBorderInfo info = entry.getValue();
                cacheData.put(entry.getKey(), new ChunkBorderCache.ChunkClaimInfo(
                    true, // claimed
                    info.own,
                    info.ally,
                    info.enemy,
                    info.nationName,
                    info.stateName,
                    info.cityName
                ));
            }

            // Update cache - this will clear the area first, then add only claimed chunks
            ChunkBorderCache.updateCache(cacheData, packet.getCenterX(), packet.getCenterZ(), packet.getRadius());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSetBorderMode(SetBorderModePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();

            if (packet.getMode() == -1) {
                // Cycle mode
                TerritoryBorderRenderer.cycleMode();
            } else {
                // Set specific mode
                TerritoryBorderRenderer.BorderMode[] modes = TerritoryBorderRenderer.BorderMode.values();
                if (packet.getMode() >= 0 && packet.getMode() < modes.length) {
                    TerritoryBorderRenderer.setMode(modes[packet.getMode()]);
                }
            }

            if (mc.player != null) {
                TerritoryBorderRenderer.BorderMode mode = TerritoryBorderRenderer.getMode();
                mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Border Mode: " + mode.getDisplayText()),
                    true
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncMembers(SyncMembersPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MembersListScreen screen) {
                List<MembersListScreen.MemberData> members = new ArrayList<>();
                for (SyncMembersPacket.MemberInfo info : packet.getMembers()) {
                    members.add(new MembersListScreen.MemberData(info.name, info.role, info.online));
                }
                screen.updateMembers(members);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncStates(SyncStatesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof StatesListScreen screen) {
                List<StatesListScreen.StateData> states = new ArrayList<>();
                for (SyncStatesPacket.StateInfo info : packet.getStates()) {
                    states.add(new StatesListScreen.StateData(
                        info.name, info.governorName, info.cityCount, info.chunkCount
                    ));
                }
                screen.updateStates(states);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncStateDetails(SyncStateDetailsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof StateInfoScreen screen) {
                screen.updateData(
                    packet.getGovernorName(),
                    packet.getCityCount(),
                    packet.getChunkCount(),
                    packet.getMemberCount(),
                    packet.isGovernor(),
                    packet.canManage(),
                    packet.getCityNames()
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncCities(SyncCitiesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CitiesListScreen screen) {
                List<CitiesListScreen.CityData> cities = new ArrayList<>();
                for (SyncCitiesPacket.CityInfo info : packet.getCities()) {
                    cities.add(new CitiesListScreen.CityData(
                        info.name, info.mayorName, info.chunkCount, info.residentCount
                    ));
                }
                screen.updateCities(cities);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncCityDetails(SyncCityDetailsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CityInfoScreen screen) {
                screen.updateData(
                    packet.getMayorName(),
                    packet.getChunkCount(),
                    packet.getResidentCount(),
                    packet.isMayor(),
                    packet.canManage(),
                    packet.getResidentNames()
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncAutoClaim(SyncAutoClaimPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ClaimsManagementScreen screen) {
                screen.updateAutoClaimState(packet.isEnabled(), packet.canUse(), packet.getCityName());
            } else if (mc.screen instanceof ChunkMapScreen screen) {
                screen.updateAutoClaimState(packet.isEnabled(), packet.canUse(), packet.getCityName());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncChunkPermits(SyncChunkPermitsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SyncChunkPermitsPacket.handle(packet);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncChunkInfo(SyncChunkInfoPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SyncChunkInfoPacket.handle(packet);
        });
        ctx.get().setPacketHandled(true);
    }
}


