package com.statecraft.network;

import com.statecraft.StateCraft;
import com.statecraft.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Handles network packet registration and sending for client-server sync
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(StateCraft.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        // Client -> Server packets
        CHANNEL.messageBuilder(RequestNationDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestNationDataPacket::encode)
            .decoder(RequestNationDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestNationData)
            .add();

        CHANNEL.messageBuilder(CreateNationPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(CreateNationPacket::encode)
            .decoder(CreateNationPacket::new)
            .consumerMainThread(ServerPacketHandler::handleCreateNation)
            .add();

        CHANNEL.messageBuilder(RequestNationDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestNationDetailsPacket::encode)
            .decoder(RequestNationDetailsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestNationDetails)
            .add();

        CHANNEL.messageBuilder(RequestChunkMapPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestChunkMapPacket::encode)
            .decoder(RequestChunkMapPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestChunkMap)
            .add();

        CHANNEL.messageBuilder(ChunkActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ChunkActionPacket::encode)
            .decoder(ChunkActionPacket::new)
            .consumerMainThread(ServerPacketHandler::handleChunkAction)
            .add();

        CHANNEL.messageBuilder(RequestInvitationsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestInvitationsPacket::encode)
            .decoder(RequestInvitationsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestInvitations)
            .add();

        CHANNEL.messageBuilder(InvitationActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(InvitationActionPacket::encode)
            .decoder(InvitationActionPacket::new)
            .consumerMainThread(ServerPacketHandler::handleInvitationAction)
            .add();

        CHANNEL.messageBuilder(UpdateNationSettingsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(UpdateNationSettingsPacket::encode)
            .decoder(UpdateNationSettingsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleUpdateNationSettings)
            .add();

        CHANNEL.messageBuilder(RequestChunkBordersPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestChunkBordersPacket::encode)
            .decoder(RequestChunkBordersPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestChunkBorders)
            .add();

        CHANNEL.messageBuilder(RequestMembersPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestMembersPacket::encode)
            .decoder(RequestMembersPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestMembers)
            .add();

        CHANNEL.messageBuilder(RequestStatesPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestStatesPacket::encode)
            .decoder(RequestStatesPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestStates)
            .add();

        CHANNEL.messageBuilder(CreateStatePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(CreateStatePacket::encode)
            .decoder(CreateStatePacket::new)
            .consumerMainThread(ServerPacketHandler::handleCreateState)
            .add();

        CHANNEL.messageBuilder(RequestStateDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestStateDetailsPacket::encode)
            .decoder(RequestStateDetailsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestStateDetails)
            .add();

        CHANNEL.messageBuilder(RequestCitiesPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestCitiesPacket::encode)
            .decoder(RequestCitiesPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestCities)
            .add();

        CHANNEL.messageBuilder(CreateCityPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(CreateCityPacket::encode)
            .decoder(CreateCityPacket::new)
            .consumerMainThread(ServerPacketHandler::handleCreateCity)
            .add();

        CHANNEL.messageBuilder(RequestCityDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestCityDetailsPacket::encode)
            .decoder(RequestCityDetailsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestCityDetails)
            .add();

        CHANNEL.messageBuilder(ToggleAutoClaimPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ToggleAutoClaimPacket::encode)
            .decoder(ToggleAutoClaimPacket::new)
            .consumerMainThread(ServerPacketHandler::handleToggleAutoClaim)
            .add();

        CHANNEL.messageBuilder(RequestChunkPermitsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestChunkPermitsPacket::encode)
            .decoder(RequestChunkPermitsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestChunkPermits)
            .add();

        CHANNEL.messageBuilder(ModifyChunkPermitPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ModifyChunkPermitPacket::encode)
            .decoder(ModifyChunkPermitPacket::new)
            .consumerMainThread(ServerPacketHandler::handleModifyChunkPermit)
            .add();

        CHANNEL.messageBuilder(RequestChunkInfoPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestChunkInfoPacket::encode)
            .decoder(RequestChunkInfoPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestChunkInfo)
            .add();

        CHANNEL.messageBuilder(AbandonChunkPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(AbandonChunkPacket::encode)
            .decoder(AbandonChunkPacket::new)
            .consumerMainThread(ServerPacketHandler::handleAbandonChunk)
            .add();

        CHANNEL.messageBuilder(SetNicknamePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(SetNicknamePacket::encode)
            .decoder(SetNicknamePacket::new)
            .consumerMainThread(ServerPacketHandler::handleSetNickname)
            .add();

        CHANNEL.messageBuilder(RequestMarketplaceDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestMarketplaceDataPacket::encode)
            .decoder(RequestMarketplaceDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestMarketplaceData)
            .add();

        CHANNEL.messageBuilder(UpdateEntitySettingsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(UpdateEntitySettingsPacket::encode)
            .decoder(UpdateEntitySettingsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleUpdateEntitySettings)
            .add();

        // Mail packets
        CHANNEL.messageBuilder(RequestMailDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestMailDataPacket::encode)
            .decoder(RequestMailDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestMailData)
            .add();

        CHANNEL.messageBuilder(RequestGovMailDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestGovMailDataPacket::encode)
            .decoder(RequestGovMailDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestGovMailData)
            .add();

        CHANNEL.messageBuilder(SendMailPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(SendMailPacket::encode)
            .decoder(SendMailPacket::new)
            .consumerMainThread(ServerPacketHandler::handleSendMail)
            .add();

        // Citizenship packets
        CHANNEL.messageBuilder(JoinCitizenshipPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(JoinCitizenshipPacket::encode)
            .decoder(JoinCitizenshipPacket::new)
            .consumerMainThread(ServerPacketHandler::handleJoinCitizenship)
            .add();

        CHANNEL.messageBuilder(LeaveCitizenshipPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(LeaveCitizenshipPacket::encode)
            .decoder(LeaveCitizenshipPacket::new)
            .consumerMainThread(ServerPacketHandler::handleLeaveCitizenship)
            .add();

        // My States/Cities packets
        CHANNEL.messageBuilder(RequestMyStatesPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestMyStatesPacket::encode)
            .decoder(RequestMyStatesPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestMyStates)
            .add();

        CHANNEL.messageBuilder(RequestMyCitiesPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestMyCitiesPacket::encode)
            .decoder(RequestMyCitiesPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestMyCities)
            .add();

        CHANNEL.messageBuilder(RequestAllNationsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestAllNationsPacket::encode)
            .decoder(RequestAllNationsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestAllNations)
            .add();

        // Server -> Client packets (responses)
        CHANNEL.messageBuilder(SyncAllNationsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAllNationsPacket::encode)
            .decoder(SyncAllNationsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncAllNations)
            .add();

        CHANNEL.messageBuilder(SyncMyStatesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMyStatesPacket::encode)
            .decoder(SyncMyStatesPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncMyStates)
            .add();

        CHANNEL.messageBuilder(SyncMyCitiesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMyCitiesPacket::encode)
            .decoder(SyncMyCitiesPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncMyCities)
            .add();

        CHANNEL.messageBuilder(SyncNationDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncNationDataPacket::encode)
            .decoder(SyncNationDataPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncNationData)
            .add();

        CHANNEL.messageBuilder(SyncChunkMapPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkMapPacket::encode)
            .decoder(SyncChunkMapPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncChunkMap)
            .add();

        CHANNEL.messageBuilder(SyncInvitationsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncInvitationsPacket::encode)
            .decoder(SyncInvitationsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncInvitations)
            .add();

        CHANNEL.messageBuilder(ActionResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ActionResultPacket::encode)
            .decoder(ActionResultPacket::new)
            .consumerMainThread(ClientPacketHandler::handleActionResult)
            .add();

        CHANNEL.messageBuilder(OpenGuiPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenGuiPacket::encode)
            .decoder(OpenGuiPacket::new)
            .consumerMainThread(ClientPacketHandler::handleOpenGui)
            .add();

        CHANNEL.messageBuilder(SyncChunkBordersPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkBordersPacket::encode)
            .decoder(SyncChunkBordersPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncChunkBorders)
            .add();

        CHANNEL.messageBuilder(SetBorderModePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SetBorderModePacket::encode)
            .decoder(SetBorderModePacket::new)
            .consumerMainThread(ClientPacketHandler::handleSetBorderMode)
            .add();

        CHANNEL.messageBuilder(SyncMembersPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMembersPacket::encode)
            .decoder(SyncMembersPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncMembers)
            .add();

        CHANNEL.messageBuilder(SyncStatesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncStatesPacket::encode)
            .decoder(SyncStatesPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncStates)
            .add();

        CHANNEL.messageBuilder(SyncStateDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncStateDetailsPacket::encode)
            .decoder(SyncStateDetailsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncStateDetails)
            .add();

        CHANNEL.messageBuilder(SyncCitiesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCitiesPacket::encode)
            .decoder(SyncCitiesPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncCities)
            .add();

        CHANNEL.messageBuilder(SyncCityDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCityDetailsPacket::encode)
            .decoder(SyncCityDetailsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncCityDetails)
            .add();

        CHANNEL.messageBuilder(SyncAutoClaimPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAutoClaimPacket::encode)
            .decoder(SyncAutoClaimPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncAutoClaim)
            .add();

        CHANNEL.messageBuilder(SyncChunkPermitsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkPermitsPacket::encode)
            .decoder(SyncChunkPermitsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncChunkPermits)
            .add();

        CHANNEL.messageBuilder(SyncChunkInfoPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkInfoPacket::encode)
            .decoder(SyncChunkInfoPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncChunkInfo)
            .add();

        CHANNEL.messageBuilder(SyncMarketplaceDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMarketplaceDataPacket::encode)
            .decoder(SyncMarketplaceDataPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncMarketplaceData)
            .add();

        CHANNEL.messageBuilder(SyncMailDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMailDataPacket::encode)
            .decoder(SyncMailDataPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncMailData)
            .add();

        StateCraft.LOGGER.info("StateCraft network packets registered");
    }

    /**
     * Send a packet to the server
     */
    public static <T> void sendToServer(T packet) {
        CHANNEL.sendToServer(packet);
    }

    /**
     * Send a packet to a specific player
     */
    public static <T> void sendToPlayer(T packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Send a packet to all players
     */
    public static <T> void sendToAll(T packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}

