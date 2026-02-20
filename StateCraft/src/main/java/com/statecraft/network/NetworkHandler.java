package com.statecraft.network;

import com.statecraft.StateCraft;
import com.statecraft.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

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

        // Invite packets
        CHANNEL.messageBuilder(InvitePlayerPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(InvitePlayerPacket::encode)
            .decoder(InvitePlayerPacket::new)
            .consumerMainThread(ServerPacketHandler::handleInvitePlayer)
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

        // Server -> Client packets (responses) - use dist-safe handlers
        CHANNEL.messageBuilder(SyncAllNationsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAllNationsPacket::encode)
            .decoder(SyncAllNationsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncAllNations(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncMyStatesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMyStatesPacket::encode)
            .decoder(SyncMyStatesPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncMyStates(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncMyCitiesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMyCitiesPacket::encode)
            .decoder(SyncMyCitiesPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncMyCities(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncNationDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncNationDataPacket::encode)
            .decoder(SyncNationDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncNationData(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncChunkMapPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkMapPacket::encode)
            .decoder(SyncChunkMapPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncChunkMap(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncInvitationsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncInvitationsPacket::encode)
            .decoder(SyncInvitationsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncInvitations(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(ActionResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ActionResultPacket::encode)
            .decoder(ActionResultPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleActionResult(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(OpenGuiPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenGuiPacket::encode)
            .decoder(OpenGuiPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleOpenGui(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncChunkBordersPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkBordersPacket::encode)
            .decoder(SyncChunkBordersPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncChunkBorders(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SetBorderModePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SetBorderModePacket::encode)
            .decoder(SetBorderModePacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSetBorderMode(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncMembersPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMembersPacket::encode)
            .decoder(SyncMembersPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncMembers(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncStatesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncStatesPacket::encode)
            .decoder(SyncStatesPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncStates(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncStateDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncStateDetailsPacket::encode)
            .decoder(SyncStateDetailsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncStateDetails(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncCitiesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCitiesPacket::encode)
            .decoder(SyncCitiesPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncCities(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncCityDetailsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCityDetailsPacket::encode)
            .decoder(SyncCityDetailsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncCityDetails(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(RequestCitySettingsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestCitySettingsPacket::encode)
            .decoder(RequestCitySettingsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestCitySettings)
            .add();

        CHANNEL.messageBuilder(SyncCitySettingsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCitySettingsPacket::encode)
            .decoder(SyncCitySettingsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncCitySettings(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(RequestNationLawsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestNationLawsPacket::encode)
            .decoder(RequestNationLawsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestNationLaws)
            .add();

        CHANNEL.messageBuilder(SyncNationLawsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncNationLawsPacket::encode)
            .decoder(SyncNationLawsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncNationLaws(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncAutoClaimPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAutoClaimPacket::encode)
            .decoder(SyncAutoClaimPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncAutoClaim(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncChunkPermitsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkPermitsPacket::encode)
            .decoder(SyncChunkPermitsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncChunkPermits(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncChunkInfoPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkInfoPacket::encode)
            .decoder(SyncChunkInfoPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncChunkInfo(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncMarketplaceDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMarketplaceDataPacket::encode)
            .decoder(SyncMarketplaceDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncMarketplaceData(pkt, ctx), ctx))
            .add();

        CHANNEL.messageBuilder(SyncMailDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMailDataPacket::encode)
            .decoder(SyncMailDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncMailData(pkt, ctx), ctx))
            .add();

        // Election packets (Client -> Server)
        CHANNEL.messageBuilder(RequestElectionDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestElectionDataPacket::encode)
            .decoder(RequestElectionDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestElectionData)
            .add();

        CHANNEL.messageBuilder(CastVotePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(CastVotePacket::encode)
            .decoder(CastVotePacket::new)
            .consumerMainThread(ServerPacketHandler::handleCastVote)
            .add();

        CHANNEL.messageBuilder(RegisterCandidatePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RegisterCandidatePacket::encode)
            .decoder(RegisterCandidatePacket::new)
            .consumerMainThread(ServerPacketHandler::handleRegisterCandidate)
            .add();

        // Election packets (Server -> Client)
        CHANNEL.messageBuilder(SyncElectionDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncElectionDataPacket::encode)
            .decoder(SyncElectionDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncElectionData(pkt, ctx), ctx))
            .add();

        // Legislature packets (Client -> Server)
        CHANNEL.messageBuilder(RequestLegislatureDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestLegislatureDataPacket::encode)
            .decoder(RequestLegislatureDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestLegislatureData)
            .add();

        CHANNEL.messageBuilder(ProposeBillPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ProposeBillPacket::encode)
            .decoder(ProposeBillPacket::new)
            .consumerMainThread(ServerPacketHandler::handleProposeBill)
            .add();

        CHANNEL.messageBuilder(VoteBillPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(VoteBillPacket::encode)
            .decoder(VoteBillPacket::new)
            .consumerMainThread(ServerPacketHandler::handleVoteBill)
            .add();

        CHANNEL.messageBuilder(LeaderBillActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(LeaderBillActionPacket::encode)
            .decoder(LeaderBillActionPacket::new)
            .consumerMainThread(ServerPacketHandler::handleLeaderBillAction)
            .add();

        // Legislature packets (Server -> Client)
        CHANNEL.messageBuilder(SyncLegislatureDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncLegislatureDataPacket::encode)
            .decoder(SyncLegislatureDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncLegislatureData(pkt, ctx), ctx))
            .add();

        // Officer management packets (Client -> Server)
        CHANNEL.messageBuilder(RequestOfficerManagementDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestOfficerManagementDataPacket::encode)
            .decoder(RequestOfficerManagementDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestOfficerManagementData)
            .add();

        CHANNEL.messageBuilder(ModifyOfficerPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ModifyOfficerPacket::encode)
            .decoder(ModifyOfficerPacket::new)
            .consumerMainThread(ServerPacketHandler::handleModifyOfficer)
            .add();

        // Officer management packets (Server -> Client)
        CHANNEL.messageBuilder(SyncOfficerManagementDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncOfficerManagementDataPacket::encode)
            .decoder(SyncOfficerManagementDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncOfficerManagementData(pkt, ctx), ctx))
            .add();

        // Appointment packets (Client -> Server)
        CHANNEL.messageBuilder(AppointLeaderPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(AppointLeaderPacket::encode)
            .decoder(AppointLeaderPacket::new)
            .consumerMainThread(ServerPacketHandler::handleAppointLeader)
            .add();

        // Contract packets (Client -> Server)
        CHANNEL.messageBuilder(RequestContractsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestContractsPacket::encode)
            .decoder(RequestContractsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestContracts)
            .add();

        CHANNEL.messageBuilder(CreateContractPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(CreateContractPacket::encode)
            .decoder(CreateContractPacket::new)
            .consumerMainThread(ServerPacketHandler::handleCreateContract)
            .add();

        CHANNEL.messageBuilder(SubmitContractBidPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(SubmitContractBidPacket::encode)
            .decoder(SubmitContractBidPacket::new)
            .consumerMainThread(ServerPacketHandler::handleSubmitContractBid)
            .add();

        CHANNEL.messageBuilder(ContractActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ContractActionPacket::encode)
            .decoder(ContractActionPacket::new)
            .consumerMainThread(ServerPacketHandler::handleContractAction)
            .add();

        // Contract packets (Server -> Client)
        CHANNEL.messageBuilder(SyncContractsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncContractsPacket::encode)
            .decoder(SyncContractsPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncContracts(pkt, ctx), ctx))
            .add();

        // City chunks packets
        CHANNEL.messageBuilder(RequestCityChunksPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestCityChunksPacket::encode)
            .decoder(RequestCityChunksPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestCityChunks)
            .add();

        CHANNEL.messageBuilder(SyncCityChunksPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncCityChunksPacket::encode)
            .decoder(SyncCityChunksPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncCityChunks(pkt, ctx), ctx))
            .add();

        // Eminent domain / nation private chunks packets
        CHANNEL.messageBuilder(RequestNationPrivateChunksPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestNationPrivateChunksPacket::encode)
            .decoder(RequestNationPrivateChunksPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestNationPrivateChunks)
            .add();

        CHANNEL.messageBuilder(SyncNationPrivateChunksPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncNationPrivateChunksPacket::encode)
            .decoder(SyncNationPrivateChunksPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncNationPrivateChunks(pkt, ctx), ctx))
            .add();

        // Emergency power packets
        CHANNEL.messageBuilder(RequestEmergencyPowerDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestEmergencyPowerDataPacket::encode)
            .decoder(RequestEmergencyPowerDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestEmergencyPowerData)
            .add();

        CHANNEL.messageBuilder(InvokeEmergencyPowerPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(InvokeEmergencyPowerPacket::encode)
            .decoder(InvokeEmergencyPowerPacket::new)
            .consumerMainThread(ServerPacketHandler::handleInvokeEmergencyPower)
            .add();

        CHANNEL.messageBuilder(SyncEmergencyPowerDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncEmergencyPowerDataPacket::encode)
            .decoder(SyncEmergencyPowerDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncEmergencyPowerData(pkt, ctx), ctx))
            .add();

        // Diplomacy packets (Client -> Server)
        CHANNEL.messageBuilder(RequestDiplomacyDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestDiplomacyDataPacket::encode)
            .decoder(RequestDiplomacyDataPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestDiplomacyData)
            .add();

        CHANNEL.messageBuilder(DiplomacyActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(DiplomacyActionPacket::encode)
            .decoder(DiplomacyActionPacket::new)
            .consumerMainThread(ServerPacketHandler::handleDiplomacyAction)
            .add();

        // Diplomacy packets (Server -> Client)
        CHANNEL.messageBuilder(SyncDiplomacyDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncDiplomacyDataPacket::encode)
            .decoder(SyncDiplomacyDataPacket::new)
            .consumerMainThread((pkt, ctx) -> handleClientSide(() -> ClientPacketHandler.handleSyncDiplomacyData(pkt, ctx), ctx))
            .add();

        StateCraft.LOGGER.info("StateCraft network packets registered");
    }

    /**
     * Helper method to safely handle client-side packets using DistExecutor
     */
    private static void handleClientSide(Runnable handler, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> handler);
        ctx.get().setPacketHandled(true);
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

