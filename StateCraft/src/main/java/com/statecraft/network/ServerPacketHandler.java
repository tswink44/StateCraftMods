package com.statecraft.network;

import com.statecraft.StateCraft;
import com.statecraft.company.Company;
import com.statecraft.company.CompanyManager;
import com.statecraft.company.ShareholderProposal;
import com.statecraft.company.ShareholderVoteManager;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.contract.Contract;
import com.statecraft.contract.ContractBid;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.*;
import com.statecraft.data.NationSavedData;
import com.statecraft.integration.IntegrationRegistry;
import com.statecraft.mail.Mail;
import com.statecraft.mail.Mailbox;
import com.statecraft.mail.MailManager;
import com.statecraft.network.packets.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Handles packets received on the server from clients
 */
public class ServerPacketHandler {

    public static void handleRequestNationData(RequestNationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            // Look up player's company memberships (independent of nation membership)
            List<String> companyNames = getPlayerCompanyNames(player.getUUID());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new SyncNationDataPacket("", "", "",
                    0, 0, false, false, companyNames), player);
            } else {
                // Find player's state and city
                String stateName = "";
                String cityName = "";

                for (State state : nation.getAllStates()) {
                    // Check if player is a direct citizen of this state
                    if (state.isCitizen(player.getUUID())) {
                        stateName = state.getName();
                        // Now check if they're also a city resident in this state
                        for (City city : state.getAllCities()) {
                            if (city.isResident(player.getUUID())) {
                                cityName = city.getName();
                                break;
                            }
                        }
                        break; // Found their primary state
                    }
                    // Also check cities (for backward compatibility - city residents are implicitly state citizens)
                    for (City city : state.getAllCities()) {
                        if (city.isResident(player.getUUID())) {
                            stateName = state.getName();
                            cityName = city.getName();
                            break;
                        }
                    }
                    if (!stateName.isEmpty()) break;
                }

                NetworkHandler.sendToPlayer(new SyncNationDataPacket(
                    nation.getName(),
                    stateName,
                    cityName,
                    nation.getTotalChunkCount(),
                    nation.getAllMembers().size(),
                    player.getUUID().equals(nation.getLeaderId()),
                    nation.isOfficer(player.getUUID()),
                    companyNames
                ), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCreateNation(CreateNationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            // Validate
            if (packet.getName().length() < 3 || packet.getName().length() > 24) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Name must be 3-24 characters"), player);
                return;
            }

            // Check creation fee
            double creationFee = StateCraftConfig.NATION_CREATION_FEE.get();
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                double playerBalance = IntegrationRegistry.getPlayerBalance(player.getUUID());
                if (playerBalance < creationFee) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Insufficient funds! Nation creation costs " + IntegrationRegistry.formatCurrency(creationFee) +
                        ". You have " + IntegrationRegistry.formatCurrency(playerBalance) + "."), player);
                    return;
                }
            }

            Nation nation = manager.createNation(packet.getName(), player.getUUID());
            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not create nation. Name may be taken."), player);
                return;
            }

            // Charge creation fee after successful creation
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                IntegrationRegistry.withdrawFromPlayer(player.getUUID(), creationFee, "Nation creation fee: " + packet.getName());
            }

            if (!packet.getTag().isEmpty()) {
                nation.setTag(packet.getTag());
            }
            if (!packet.getDescription().isEmpty()) {
                nation.setDescription(packet.getDescription());
            }

            // Save data
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true, nation.getName()), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestNationDetails(RequestNationDetailsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found"), player);
                return;
            }

            // Gather data
            List<String> stateNames = new ArrayList<>();
            for (State state : nation.getAllStates()) {
                stateNames.add(state.getName());
            }

            List<String> allyNames = new ArrayList<>();
            for (UUID allyId : nation.getAllies()) {
                Nation ally = manager.getNation(allyId);
                if (ally != null) allyNames.add(ally.getName());
            }

            List<String> enemyNames = new ArrayList<>();
            for (UUID enemyId : nation.getEnemies()) {
                Nation enemy = manager.getNation(enemyId);
                if (enemy != null) enemyNames.add(enemy.getName());
            }

            // Get leader name
            String leaderName = player.getServer().getProfileCache()
                .get(nation.getLeaderId())
                .map(p -> p.getName())
                .orElse("Unknown");

            // Check if player is a member of this nation
            boolean isMember = nation.isMember(player.getUUID());

            NetworkHandler.sendToPlayer(new SyncNationDataPacket(
                nation.getName(),
                nation.getStateCount(),
                nation.getMaxStates(),
                nation.getTotalCityCount(),
                nation.getTotalChunkCount(),
                nation.getAllMembers().size(),
                IntegrationRegistry.getNationBalance(nation.getName()),
                nation.isOpen(),
                nation.getDescription(),
                leaderName,
                player.getUUID().equals(nation.getLeaderId()),
                nation.isOfficer(player.getUUID()),
                isMember,
                stateNames,
                allyNames,
                enemyNames
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestChunkMap(RequestChunkMapPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            ChunkPos playerPos = new ChunkPos(player.blockPosition());
            Nation playerNation = manager.getPlayerNation(player.getUUID());

            int halfSize = packet.getSize() / 2;
            Map<Long, SyncChunkMapPacket.ChunkInfo> chunks = new HashMap<>();

            for (int dz = -halfSize; dz <= halfSize; dz++) {
                for (int dx = -halfSize; dx <= halfSize; dx++) {
                    int chunkX = playerPos.x + dx;
                    int chunkZ = playerPos.z + dz;
                    ChunkPos pos = new ChunkPos(chunkX, chunkZ);

                    ClaimedChunk claimed = manager.getClaimedChunk(pos, player.level().dimension());
                    if (claimed != null) {
                        City city = manager.getCity(claimed.getCityId());
                        if (city != null) {
                            State state = manager.getState(city.getStateId());
                            if (state != null) {
                                Nation nation = manager.getNation(state.getNationId());
                                if (nation != null) {
                                    boolean isPlayerNation = playerNation != null &&
                                        nation.getId().equals(playerNation.getId());
                                    boolean isAlly = playerNation != null &&
                                        playerNation.isAlly(nation.getId());
                                    boolean isEnemy = playerNation != null &&
                                        playerNation.isEnemy(nation.getId());
                                    boolean canManage = isPlayerNation &&
                                        (nation.isLeaderOrOfficer(player.getUUID()) ||
                                         player.getUUID().equals(state.getGovernorId()) ||
                                         player.getUUID().equals(city.getMayorId()));

                                    long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                                    chunks.put(key, new SyncChunkMapPacket.ChunkInfo(
                                        nation.getName(),
                                        city.getName(),
                                        isPlayerNation,
                                        isAlly,
                                        isEnemy,
                                        canManage
                                    ));
                                }
                            }
                        }
                    }
                }
            }

            String nationName = playerNation != null ? playerNation.getName() : null;
            NetworkHandler.sendToPlayer(new SyncChunkMapPacket(
                playerPos.x, playerPos.z, nationName, chunks
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleChunkAction(ChunkActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());

            switch (packet.getAction()) {
                case CLAIM -> {
                    // Find a city to claim for
                    Nation nation = manager.getPlayerNation(player.getUUID());
                    if (nation == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You are not in a nation"), player);
                        return;
                    }

                    // Find first city the player can claim for
                    City targetCity = null;
                    for (State state : nation.getAllStates()) {
                        for (City city : state.getAllCities()) {
                            if (player.getUUID().equals(city.getMayorId()) ||
                                player.getUUID().equals(state.getGovernorId()) ||
                                nation.isLeaderOrOfficer(player.getUUID())) {
                                targetCity = city;
                                break;
                            }
                        }
                        if (targetCity != null) break;
                    }

                    if (targetCity == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "No city to claim for"), player);
                        return;
                    }

                    // Check chunk claim fee if economy mod is loaded
                    double chunkClaimFee = getChunkClaimFee(nation);
                    if (chunkClaimFee > 0) {
                        double cityBalance = getCityTreasuryBalance(targetCity.getId());
                        if (cityBalance < chunkClaimFee) {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                String.format("City treasury has insufficient funds. Need $%.2f, have $%.2f",
                                    chunkClaimFee, cityBalance)), player);
                            return;
                        }
                    }

                    ChunkClaimManager.ClaimResult result = manager.claimChunkWithResult(targetCity, pos, player.level().dimension());

                    switch (result) {
                        case SUCCESS -> {
                            // Deduct chunk claim fee from city treasury and deposit to nation
                            if (chunkClaimFee > 0) {
                                deductFromCityTreasury(targetCity.getId(), chunkClaimFee);
                                depositToNationTreasury(nation.getId(), chunkClaimFee,
                                    "Chunk claim fee from " + targetCity.getName());
                            }

                            if (player.level() instanceof ServerLevel level) {
                                NationSavedData.get(level).markForSave();
                                // Broadcast chunk update to nearby players
                                broadcastChunkUpdateToNearby(level, pos.x, pos.z);
                            }
                            String feeMsg = chunkClaimFee > 0 ? String.format(" ($%.2f deducted from treasury)", chunkClaimFee) : "";
                            NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Chunk claimed" + feeMsg), player);
                        }
                        case ALREADY_CLAIMED -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false, "This chunk is already claimed"), player);
                        }
                        case CITY_CHUNK_LIMIT -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "City has reached its chunk limit (" + targetCity.getMaxChunks() + ")"), player);
                        }
                        case STATE_CHUNK_LIMIT -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "State has reached its chunk limit"), player);
                        }
                        case NATION_CHUNK_LIMIT -> {
                            // Get nation's maxChunksPerCity for the error message (nation already defined above)
                            int limit = nation != null ? nation.getMaxChunksPerCity() : 50;
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "Nation law limits cities to " + limit + " chunks. Enact legislation to increase."), player);
                        }
                        case NOT_CONTIGUOUS -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "Chunk must be adjacent to existing city or state territory"), player);
                        }
                        case INSUFFICIENT_FUNDS -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "City treasury has insufficient funds to claim chunk"), player);
                        }
                        case PLAYER_CHUNK_LIMIT -> {
                            int playerLimit = manager.getEffectiveMaxChunksPerPlayer(nation);
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "You have reached your personal chunk limit (" + playerLimit + "). " +
                                "Nation legislation can change this limit."), player);
                        }
                    }
                }
                case UNCLAIM -> {
                    boolean success = manager.unclaimChunk(pos, player.level().dimension());
                    if (success && player.level() instanceof ServerLevel level) {
                        NationSavedData.get(level).markForSave();
                        // Broadcast chunk update to nearby players
                        broadcastChunkUpdateToNearby(level, pos.x, pos.z);
                    }
                    NetworkHandler.sendToPlayer(new ActionResultPacket(success,
                        success ? "Chunk unclaimed" : "Could not unclaim"), player);
                }
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Broadcast chunk border updates to all players near the specified chunk
     */
    public static void broadcastChunkUpdateToNearby(ServerLevel level, int chunkX, int chunkZ) {
        int broadcastRadius = 10; // How far to look for players (in chunks)
        int updateRadius = 3; // Radius of chunk update to send to each player

        for (ServerPlayer nearbyPlayer : level.players()) {
            int playerChunkX = nearbyPlayer.chunkPosition().x;
            int playerChunkZ = nearbyPlayer.chunkPosition().z;

            // Check if player is within broadcast range
            if (Math.abs(playerChunkX - chunkX) <= broadcastRadius &&
                Math.abs(playerChunkZ - chunkZ) <= broadcastRadius) {
                // Send them an updated chunk border packet centered on the changed chunk
                sendChunkBordersToPlayer(nearbyPlayer, chunkX, chunkZ, updateRadius);
            }
        }
    }

    /**
     * Send chunk border data to a specific player for an area
     */
    private static void sendChunkBordersToPlayer(ServerPlayer player, int centerX, int centerZ, int radius) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        Nation playerNation = manager.getPlayerNation(player.getUUID());

        Map<Long, SyncChunkBordersPacket.ChunkBorderInfo> chunks = new HashMap<>();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);

                ClaimedChunk claimed = manager.getClaimedChunk(pos, player.level().dimension());
                if (claimed != null) {
                    City city = manager.getCity(claimed.getCityId());
                    if (city != null) {
                        State state = manager.getState(city.getStateId());
                        if (state != null) {
                            Nation nation = manager.getNation(state.getNationId());
                            if (nation != null) {
                                boolean isOwn = playerNation != null &&
                                    nation.getId().equals(playerNation.getId());
                                boolean isAlly = playerNation != null &&
                                    playerNation.isAlly(nation.getId());
                                boolean isEnemy = playerNation != null &&
                                    playerNation.isEnemy(nation.getId());

                                long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                                chunks.put(key, new SyncChunkBordersPacket.ChunkBorderInfo(
                                    isOwn, isAlly, isEnemy,
                                    nation.getName(), state.getName(), city.getName()
                                ));
                            }
                        }
                    }
                }
            }
        }

        NetworkHandler.sendToPlayer(new SyncChunkBordersPacket(chunks, centerX, centerZ, radius), player);
    }

    public static void handleRequestInvitations(RequestInvitationsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            InvitationManager invManager = InvitationManager.getInstance();
            ChunkClaimManager claimManager = ChunkClaimManager.getInstance();

            List<Invitation> invites = invManager.getInvitationsForPlayer(player.getUUID());
            List<SyncInvitationsPacket.InviteInfo> infos = new ArrayList<>();

            for (Invitation inv : invites) {
                String entityName = "";
                if (inv.getType() == Invitation.InvitationType.NATION) {
                    Nation nation = claimManager.getNation(inv.getEntityId());
                    if (nation != null) entityName = nation.getName();
                } else {
                    City city = claimManager.getCity(inv.getEntityId());
                    if (city != null) entityName = city.getName();
                }

                String senderName = player.getServer().getProfileCache()
                    .get(inv.getSenderId())
                    .map(p -> p.getName())
                    .orElse("Unknown");

                infos.add(new SyncInvitationsPacket.InviteInfo(
                    inv.getId().toString(),
                    inv.getType().name(),
                    entityName,
                    senderName,
                    inv.getRemainingTimeSeconds()
                ));
            }

            NetworkHandler.sendToPlayer(new SyncInvitationsPacket(infos), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleInvitationAction(InvitationActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            InvitationManager invManager = InvitationManager.getInstance();
            UUID invId = UUID.fromString(packet.getInvitationId());

            boolean success;
            String message;

            if (packet.isAccept()) {
                success = invManager.acceptInvitation(invId, player.getUUID());
                message = success ? "Invitation accepted!" : "Could not accept invitation";
            } else {
                success = invManager.denyInvitation(invId, player.getUUID());
                message = success ? "Invitation declined" : "Could not decline invitation";
            }

            if (success && player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(success, message), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleUpdateNationSettings(UpdateNationSettingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found"), player);
                return;
            }

            if (!player.getUUID().equals(nation.getLeaderId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only the nation leader can change settings"), player);
                return;
            }

            nation.setTag(packet.getTag());
            nation.setDescription(packet.getDescription());
            nation.setOpen(packet.isOpen());
            manager.markDirty();

            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Settings saved"), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestChunkBorders(RequestChunkBordersPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            int playerChunkX = player.chunkPosition().x;
            int playerChunkZ = player.chunkPosition().z;
            Nation playerNation = manager.getPlayerNation(player.getUUID());

            int radius = Math.min(packet.getRadius(), 10); // Cap radius
            Map<Long, SyncChunkBordersPacket.ChunkBorderInfo> chunks = new HashMap<>();

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    ChunkPos pos = new ChunkPos(chunkX, chunkZ);

                    ClaimedChunk claimed = manager.getClaimedChunk(pos, player.level().dimension());
                    if (claimed != null) {
                        City city = manager.getCity(claimed.getCityId());
                        if (city != null) {
                            State state = manager.getState(city.getStateId());
                            Nation nation = manager.getNation(state.getNationId());
                            if (nation != null) {
                                boolean isOwn = playerNation != null &&
                                    nation.getId().equals(playerNation.getId());
                                boolean isAlly = playerNation != null &&
                                    playerNation.isAlly(nation.getId());
                                boolean isEnemy = playerNation != null &&
                                    playerNation.isEnemy(nation.getId());

                                long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                                chunks.put(key, new SyncChunkBordersPacket.ChunkBorderInfo(
                                    isOwn, isAlly, isEnemy,
                                    nation.getName(), state.getName(), city.getName()
                                ));
                            }
                        }
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncChunkBordersPacket(chunks, playerChunkX, playerChunkZ, radius), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestMembers(RequestMembersPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            List<SyncMembersPacket.MemberInfo> members = new ArrayList<>();

            if (nation != null) {
                // Add leader
                String leaderName = getPlayerName(player.server, nation.getLeaderId());
                boolean leaderOnline = isPlayerOnline(player.server, nation.getLeaderId());
                members.add(new SyncMembersPacket.MemberInfo(leaderName, "Leader", leaderOnline));

                // Add officers
                for (UUID officerId : nation.getOfficers()) {
                    if (!officerId.equals(nation.getLeaderId())) {
                        String name = getPlayerName(player.server, officerId);
                        boolean online = isPlayerOnline(player.server, officerId);
                        members.add(new SyncMembersPacket.MemberInfo(name, "Officer", online));
                    }
                }

                // Add regular members (not leader or officer)
                for (UUID memberId : nation.getMembers()) {
                    if (!memberId.equals(nation.getLeaderId()) &&
                        !nation.getOfficers().contains(memberId)) {
                        String name = getPlayerName(player.server, memberId);
                        boolean online = isPlayerOnline(player.server, memberId);
                        members.add(new SyncMembersPacket.MemberInfo(name, "Citizen", online));
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncMembersPacket(members), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestStates(RequestStatesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            List<SyncStatesPacket.StateInfo> states = new ArrayList<>();
            boolean canCreateState = false;

            if (nation != null) {
                // Check if player is the nation leader (can create states)
                canCreateState = player.getUUID().equals(nation.getLeaderId());

                for (State state : nation.getAllStates()) {
                    String governorName = getPlayerName(player.server, state.getGovernorId());
                    boolean isCitizen = state.isCitizen(player.getUUID());
                    states.add(new SyncStatesPacket.StateInfo(
                        state.getName(),
                        governorName,
                        state.getCityCount(),
                        state.getTotalChunkCount(),
                        isCitizen
                    ));
                }
            }

            NetworkHandler.sendToPlayer(new SyncStatesPacket(states, canCreateState), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCreateState(CreateStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You are not in a nation!"), player);
                return;
            }

            if (!player.getUUID().equals(nation.getLeaderId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only the nation leader can create states!"), player);
                return;
            }

            String name = packet.getStateName().trim();
            if (name.length() < 3 || name.length() > 24) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "State name must be 3-24 characters!"), player);
                return;
            }

            if (nation.getStateByName(name) != null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "A state with that name already exists!"), player);
                return;
            }

            // Check if nation can create more states (legislature can change this limit)
            if (!nation.canCreateState()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Nation has reached maximum states (" + nation.getMaxStates() + "). Enact legislation to increase the limit."), player);
                return;
            }

            // Check creation fee
            double creationFee = StateCraftConfig.STATE_CREATION_FEE.get();
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                double playerBalance = IntegrationRegistry.getPlayerBalance(player.getUUID());
                if (playerBalance < creationFee) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Insufficient funds! State creation costs " + IntegrationRegistry.formatCurrency(creationFee) +
                        ". You have " + IntegrationRegistry.formatCurrency(playerBalance) + "."), player);
                    return;
                }
            }

            // Check if player is already governor of another state
            boolean vacantGovernor = manager.isGovernorOfAnyState(player.getUUID());

            State state = manager.createState(nation, name, player.getUUID());            if (state == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not create state. An error occurred."), player);
                return;
            }

            // Charge creation fee after successful creation
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                IntegrationRegistry.withdrawFromPlayer(player.getUUID(), creationFee, "State creation fee: " + name);
            }

            // Mark data dirty
            if (player.level() instanceof ServerLevel serverLevel) {
                NationSavedData.get(serverLevel).markForSave();
            }

            String feeMsg = creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()
                ? " (Cost: " + IntegrationRegistry.formatCurrency(creationFee) + ")" : "";

            if (vacantGovernor) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                    "State '" + name + "' created with §eVACANT governor§r position! Use the Appoint button to assign a governor." + feeMsg), player);
            } else {
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "State '" + name + "' created successfully!" + feeMsg), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String getPlayerName(net.minecraft.server.MinecraftServer server, UUID playerId) {
        if (playerId == null) return "Vacant";
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        // Try to get cached name from user cache
        var profile = server.getProfileCache().get(playerId);
        return profile.map(gameProfile -> gameProfile.getName()).orElse("Unknown");
    }

    /**
     * Get the names of all companies a player is a member of (shareholder or officer).
     */
    private static List<String> getPlayerCompanyNames(UUID playerId) {
        List<String> names = new ArrayList<>();
        try {
            com.statecraft.company.CompanyManager companyManager = com.statecraft.company.CompanyManager.getInstance();
            for (com.statecraft.company.Company company : companyManager.getPlayerCompanies(playerId)) {
                names.add(company.getName());
            }
        } catch (Exception e) {
            // CompanyManager may not be initialized yet
        }
        return names;
    }

    private static boolean isPlayerOnline(net.minecraft.server.MinecraftServer server, UUID playerId) {
        if (playerId == null) return false;
        return server.getPlayerList().getPlayer(playerId) != null;
    }

    public static void handleRequestStateDetails(RequestStateDetailsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) return;

            State state = nation.getStateByName(packet.getStateName());
            if (state == null) return;

            String governorName = getPlayerName(player.server, state.getGovernorId());
            int memberCount = state.getAllResidents().size();
            boolean isGovernor = player.getUUID().equals(state.getGovernorId());
            boolean canManage = isGovernor || nation.isLeaderOrOfficer(player.getUUID());
            boolean isNationLeader = player.getUUID().equals(nation.getLeaderId());

            List<String> cityNames = new ArrayList<>();
            for (City city : state.getAllCities()) {
                cityNames.add(city.getName());
            }

            NetworkHandler.sendToPlayer(new SyncStateDetailsPacket(
                state.getName(),
                governorName,
                state.getCityCount(),
                state.getTotalChunkCount(),
                memberCount,
                isGovernor,
                canManage,
                cityNames,
                isNationLeader
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestCities(RequestCitiesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            List<SyncCitiesPacket.CityInfo> cities = new ArrayList<>();
            boolean canCreateCity = false;

            if (nation != null) {
                State state = nation.getStateByName(packet.getStateName());
                if (state != null) {
                    // Check if player can create cities (state governor or nation leader/officer)
                    canCreateCity = player.getUUID().equals(state.getGovernorId()) || nation.isLeaderOrOfficer(player.getUUID());

                    for (City city : state.getAllCities()) {
                        String mayorName = getPlayerName(player.server, city.getMayorId());
                        boolean isResident = city.isResident(player.getUUID());
                        cities.add(new SyncCitiesPacket.CityInfo(
                            city.getName(),
                            mayorName,
                            city.getChunkCount(),
                            city.getResidents().size(), // Mayor is already in residents set
                            isResident,
                            city.isPublicJoin()
                        ));
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncCitiesPacket(cities, canCreateCity), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCreateCity(CreateCityPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You are not in a nation!"), player);
                return;
            }

            State state = nation.getStateByName(packet.getStateName());
            if (state == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "State not found!"), player);
                return;
            }

            // Check permissions - must be state governor or nation admin
            if (!player.getUUID().equals(state.getGovernorId()) && !nation.isLeaderOrOfficer(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only the governor or nation admins can create cities!"), player);
                return;
            }

            String name = packet.getCityName().trim();
            if (name.length() < 3 || name.length() > 24) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "City name must be 3-24 characters!"), player);
                return;
            }

            // Check if city name already exists in this state
            if (state.getCityByName(name) != null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "A city with that name already exists!"), player);
                return;
            }

            // Check creation fee
            double creationFee = StateCraftConfig.CITY_CREATION_FEE.get();
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                double playerBalance = IntegrationRegistry.getPlayerBalance(player.getUUID());
                if (playerBalance < creationFee) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Insufficient funds! City creation costs " + IntegrationRegistry.formatCurrency(creationFee) +
                        ". You have " + IntegrationRegistry.formatCurrency(playerBalance) + "."), player);
                    return;
                }
            }

            // Check max cities limit before creating
            if (state.getCityCount() >= state.getMaxCities()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Cannot create city! This state has reached its maximum of " + state.getMaxCities() + " cities."), player);
                return;
            }

            // Check if player is already mayor of another city
            boolean vacantMayor = manager.isMayorOfAnyCity(player.getUUID());

            City city = manager.createCity(state, name, player.getUUID());
            if (city == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not create city. Please try again."), player);
                return;
            }

            // Charge creation fee after successful creation
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                IntegrationRegistry.withdrawFromPlayer(player.getUUID(), creationFee, "City creation fee: " + name);
            }

            // Mark data dirty
            if (player.level() instanceof ServerLevel serverLevel) {
                NationSavedData.get(serverLevel).markForSave();
            }

            String feeMsg = creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()
                ? " (Cost: " + IntegrationRegistry.formatCurrency(creationFee) + ")" : "";

            if (vacantMayor) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                    "City '" + name + "' created with §eVACANT mayor§r position! Use the Appoint button to assign a mayor." + feeMsg), player);
            } else {
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "City '" + name + "' created successfully!" + feeMsg), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestCityDetails(RequestCityDetailsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) return;

            State state = nation.getStateByName(packet.getStateName());
            if (state == null) return;

            City city = state.getCityByName(packet.getCityName());
            if (city == null) return;

            String mayorName = getPlayerName(player.server, city.getMayorId());
            boolean isMayor = player.getUUID().equals(city.getMayorId());
            boolean canManage = isMayor || player.getUUID().equals(state.getGovernorId()) || nation.isLeaderOrOfficer(player.getUUID());
            boolean canAppoint = player.getUUID().equals(state.getGovernorId()) || player.getUUID().equals(nation.getLeaderId());

            List<String> residentNames = new ArrayList<>();
            residentNames.add(mayorName); // Mayor first
            for (UUID residentId : city.getResidents()) {
                if (!residentId.equals(city.getMayorId())) {
                    residentNames.add(getPlayerName(player.server, residentId));
                }
            }

            NetworkHandler.sendToPlayer(new SyncCityDetailsPacket(
                city.getName(),
                mayorName,
                city.getChunkCount(),
                city.getResidents().size(),
                isMayor,
                canManage,
                residentNames,
                canAppoint
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestCitySettings(RequestCitySettingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) return;

            State state = nation.getStateByName(packet.getStateName());
            if (state == null) return;

            City city = state.getCityByName(packet.getCityName());
            if (city == null) return;

            // Check permissions - only mayor, governor, or nation admin can view settings
            boolean canManage = player.getUUID().equals(city.getMayorId()) ||
                               player.getUUID().equals(state.getGovernorId()) ||
                               nation.isLeaderOrOfficer(player.getUUID());

            if (!canManage) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou don't have permission to view city settings"));
                return;
            }

            // Send settings to client
            NetworkHandler.sendToPlayer(new SyncCitySettingsPacket(
                city.getName(),
                city.getDescription(),
                city.getFlagUrl(),
                city.isPublicJoin(),
                city.getTaxRate() * 100, // Convert to percentage
                city.getSalesTaxRate() * 100, // Convert to percentage
                city.getStatePassThroughRate() * 100 // Convert to percentage
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestNationLaws(RequestNationLawsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNation not found"));
                return;
            }

            // Build list of current policies/laws
            List<SyncNationLawsPacket.PolicyInfo> policies = new ArrayList<>();

            // Taxation policies
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Taxation", "Nation Sales Tax", "PERCENTAGE",
                nation.getSalesTaxRate(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Taxation", "State Pass-Through Rate", "PERCENTAGE",
                nation.getStatePassThroughRate(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Taxation", "Base Chunk Value", "CURRENCY",
                nation.getBaseChunkValue(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Taxation", "Chunk Claim Fee", "CURRENCY",
                nation.getChunkClaimFee(), ""
            ));

            // Territory policies
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Territory", "Max States", "INTEGER",
                nation.getMaxStates(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Territory", "Max Cities per State", "INTEGER",
                nation.getDefaultMaxCitiesPerState(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Territory", "Max Chunks per City", "INTEGER",
                nation.getMaxChunksPerCity(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Territory", "Open Borders", "BOOLEAN",
                nation.hasOpenBorders() ? 1 : 0, ""
            ));

            // Membership policies
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Membership", "Open Nation", "BOOLEAN",
                nation.isOpen() ? 1 : 0, ""
            ));

            // Constitutional settings
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Constitutional", "Leader Term Duration", "INTEGER",
                nation.getLeaderTermDays(), "days"
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Constitutional", "Election Duration", "INTEGER",
                nation.getElectionDurationDays(), "days"
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Constitutional", "Max Officers", "INTEGER",
                nation.getMaxOfficers(), ""
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Constitutional", "Nation Name", "TEXT",
                0, nation.getName()
            ));
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Constitutional", "Nation Flag", "TEXT",
                0, nation.getFlagUrl() != null ? nation.getFlagUrl() : ""
            ));

            // Diplomacy info
            Set<UUID> allies = nation.getAllies();
            Set<UUID> enemies = nation.getEnemies();

            StringBuilder allyNames = new StringBuilder();
            for (UUID allyId : allies) {
                Nation ally = manager.getNation(allyId);
                if (ally != null) {
                    if (allyNames.length() > 0) allyNames.append(", ");
                    allyNames.append(ally.getName());
                }
            }
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Diplomacy", "Allies", "TEXT",
                allies.size(), allyNames.length() > 0 ? allyNames.toString() : "None"
            ));

            StringBuilder enemyNames = new StringBuilder();
            for (UUID enemyId : enemies) {
                Nation enemy = manager.getNation(enemyId);
                if (enemy != null) {
                    if (enemyNames.length() > 0) enemyNames.append(", ");
                    enemyNames.append(enemy.getName());
                }
            }
            policies.add(new SyncNationLawsPacket.PolicyInfo(
                "Diplomacy", "At War With", "TEXT",
                enemies.size(), enemyNames.length() > 0 ? enemyNames.toString() : "None"
            ));

            // Build enacted laws from the codex
            List<SyncNationLawsPacket.EnactedLawInfo> enactedLaws = new ArrayList<>();
            com.statecraft.legislature.LegislatureManager legManager = com.statecraft.legislature.LegislatureManager.getInstance();
            com.statecraft.legislature.Legislature legislature = legManager.getOrCreateLegislature(nation.getId());
            for (com.statecraft.legislature.Law law : legislature.getCodex().getAllLaws()) {
                java.util.Map<String, String> policyChanges = new java.util.HashMap<>();
                for (java.util.Map.Entry<com.statecraft.legislature.PolicyType, String> entry : law.getPolicyChanges().entrySet()) {
                    policyChanges.put(entry.getKey().getDisplayName(), entry.getValue());
                }
                // Get full text from custom law entries
                String fullText = "";
                for (java.util.Map.Entry<com.statecraft.legislature.PolicyType, String> entry : law.getPolicyChanges().entrySet()) {
                    if (entry.getKey().getCategory() == com.statecraft.legislature.PolicyType.Category.CUSTOM) {
                        fullText = entry.getValue();
                        break;
                    }
                }
                enactedLaws.add(new SyncNationLawsPacket.EnactedLawInfo(
                    law.getLawNumber(), law.getTitle(), law.getDescription(), law.getAuthorName(),
                    law.getEnactedTime(), law.getYesVotes(), law.getNoVotes(), law.getAbstainVotes(),
                    law.wasVetoProof(), law.getPolicyChanges().values().stream()
                        .anyMatch(v -> false), // isConstitutionalAmendment - check type
                    law.isRepealed(), policyChanges, fullText
                ));
            }

            // Send to client
            NetworkHandler.sendToPlayer(new SyncNationLawsPacket(nation.getName(), policies, enactedLaws), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleToggleAutoClaim(ToggleAutoClaimPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AutoClaimManager.getInstance().toggleAutoClaim(player, packet.getCityName());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestChunkPermits(RequestChunkPermitsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            if (chunk == null) {
                // Unclaimed chunk - no permits
                NetworkHandler.sendToPlayer(new SyncChunkPermitsPacket(
                    packet.getChunkX(), packet.getChunkZ(),
                    "UNCLAIMED", "Wilderness",
                    false, new HashMap<>()
                ), player);
                return;
            }

            // Determine ownership info
            String ownershipType = chunk.getOwnershipType().name();
            String ownerName;
            boolean canManagePermits = false;

            if (chunk.getOwnershipType() == OwnershipType.PLAYER) {
                // Private ownership
                UUID ownerId = chunk.getPlayerOwner();
                ownerName = ownerId != null ? getPlayerName(player.server, ownerId) : "Unknown";
                canManagePermits = player.getUUID().equals(ownerId);
            } else if (chunk.getOwnershipType() == OwnershipType.COMPANY) {
                // Company ownership
                UUID companyId = chunk.getCompanyOwner();
                if (companyId != null) {
                    com.statecraft.company.Company company =
                        com.statecraft.company.CompanyManager.getInstance().getCompany(companyId);
                    if (company != null) {
                        ownerName = company.getName() + " (Company)";
                        canManagePermits = company.isOfficer(player.getUUID());
                    } else {
                        ownerName = "Unknown Company";
                    }
                } else {
                    ownerName = "Unknown Company";
                }
            } else {
                // Government ownership (HIERARCHY)
                City city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    State state = manager.getState(city.getStateId());
                    Nation nation = state != null ? manager.getNation(state.getNationId()) : null;
                    ownerName = city.getName();

                    // Check if player is Mayor, Governor, or President
                    boolean isMayor = player.getUUID().equals(city.getMayorId());
                    boolean isGovernor = state != null && player.getUUID().equals(state.getGovernorId());
                    boolean isPresident = nation != null && player.getUUID().equals(nation.getLeaderId());
                    boolean isNationLeaderOrOfficer = nation != null && nation.isLeaderOrOfficer(player.getUUID());

                    canManagePermits = isMayor || isGovernor || isPresident || isNationLeaderOrOfficer;
                } else {
                    ownerName = "Unknown City";
                }
            }

            // Get permit holders with their names
            Map<UUID, String> permitHolders = new HashMap<>();
            for (UUID permitId : chunk.getPermitHolders()) {
                if (chunk.hasBuildingPermit(permitId)) {
                    permitHolders.put(permitId, getPlayerName(player.server, permitId));
                }
            }

            NetworkHandler.sendToPlayer(new SyncChunkPermitsPacket(
                packet.getChunkX(), packet.getChunkZ(),
                ownershipType, ownerName,
                canManagePermits, permitHolders
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleModifyChunkPermit(ModifyChunkPermitPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            if (chunk == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Chunk is not claimed"), player);
                return;
            }

            // Check permission to manage permits
            boolean canManage = false;
            if (chunk.getOwnershipType() == OwnershipType.PLAYER) {
                canManage = player.getUUID().equals(chunk.getPlayerOwner());
            } else {
                City city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    State state = manager.getState(city.getStateId());
                    Nation nation = state != null ? manager.getNation(state.getNationId()) : null;

                    boolean isMayor = player.getUUID().equals(city.getMayorId());
                    boolean isGovernor = state != null && player.getUUID().equals(state.getGovernorId());
                    boolean isPresident = nation != null && player.getUUID().equals(nation.getLeaderId());
                    boolean isNationLeaderOrOfficer = nation != null && nation.isLeaderOrOfficer(player.getUUID());

                    canManage = isMayor || isGovernor || isPresident || isNationLeaderOrOfficer;
                }
            }

            if (!canManage) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You don't have permission to manage permits here"), player);
                return;
            }

            // Find target player
            ServerPlayer targetPlayer = player.server.getPlayerList().getPlayerByName(packet.getPlayerName());
            UUID targetId = null;

            if (targetPlayer != null) {
                targetId = targetPlayer.getUUID();
            } else {
                // Try to find by cached name (for offline players)
                // For simplicity, only allow online players for now
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Player not found (must be online)"), player);
                return;
            }

            // Apply action
            if (packet.getAction() == ModifyChunkPermitPacket.Action.GRANT) {
                chunk.grantBuildingPermit(targetId);
                manager.markDirty();
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Granted building permit to " + targetPlayer.getName()), player);
            } else {
                chunk.revokeBuildingPermit(targetId);
                manager.markDirty();
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Revoked building permit from " + targetPlayer.getName()), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestChunkInfo(RequestChunkInfoPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            String ownershipType;
            String ownerName = "";
            String nationName = "";
            String stateName = "";
            String cityName = "";
            boolean canManagePermits = false;
            boolean isOwner = false;
            List<String> permitHolders = new ArrayList<>();

            if (chunk == null) {
                ownershipType = "UNCLAIMED";
            } else {
                ownershipType = chunk.getOwnershipType().name();

                // Get hierarchy info
                City city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    cityName = city.getName();
                    State state = manager.getState(city.getStateId());
                    if (state != null) {
                        stateName = state.getName();
                        Nation nation = manager.getNation(state.getNationId());
                        if (nation != null) {
                            nationName = nation.getName();
                        }
                    }
                }

                if (chunk.getOwnershipType() == OwnershipType.PLAYER) {
                    UUID ownerId = chunk.getPlayerOwner();
                    ownerName = ownerId != null ? getPlayerName(player.server, ownerId) : "Unknown";
                    canManagePermits = player.getUUID().equals(ownerId);
                    isOwner = player.getUUID().equals(ownerId);
                } else if (chunk.getOwnershipType() == OwnershipType.COMPANY) {
                    UUID companyId = chunk.getCompanyOwner();
                    if (companyId != null) {
                        com.statecraft.company.Company company =
                            com.statecraft.company.CompanyManager.getInstance().getCompany(companyId);
                        if (company != null) {
                            ownerName = company.getName() + " (Company)";
                            canManagePermits = company.isOfficer(player.getUUID());
                            isOwner = company.isOfficer(player.getUUID());
                        } else {
                            ownerName = "Unknown Company";
                        }
                    }
                } else {
                    ownerName = cityName;
                    // Check government permissions
                    if (city != null) {
                        State state = manager.getState(city.getStateId());
                        Nation nation = state != null ? manager.getNation(state.getNationId()) : null;
                        boolean isMayor = player.getUUID().equals(city.getMayorId());
                        boolean isGovernor = state != null && player.getUUID().equals(state.getGovernorId());
                        boolean isPresident = nation != null && player.getUUID().equals(nation.getLeaderId());
                        boolean isNationLeaderOrOfficer = nation != null && nation.isLeaderOrOfficer(player.getUUID());

                        canManagePermits = isMayor || isGovernor || isPresident || isNationLeaderOrOfficer;
                    }
                }

                // Get permit holders
                for (UUID permitId : chunk.getPermitHolders()) {
                    if (chunk.hasBuildingPermit(permitId)) {
                        permitHolders.add(getPlayerName(player.server, permitId));
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncChunkInfoPacket(
                packet.getChunkX(), packet.getChunkZ(),
                ownershipType, ownerName, nationName, stateName, cityName,
                canManagePermits, permitHolders, isOwner
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSetNickname(SetNicknamePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            String nickname = packet.getNickname();

            // Validate nickname (basic validation)
            if (nickname.length() > 32) {
                nickname = nickname.substring(0, 32);
            }

            // Store nickname in player's persistent data
            player.getPersistentData().putString("statecraft_nickname", nickname);

            StateCraft.LOGGER.debug("Player {} set nickname to: {}", player.getName().getString(), nickname);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestMarketplaceData(RequestMarketplaceDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            List<SyncMarketplaceDataPacket.ListingInfo> listings = new ArrayList<>();

            int centerX = packet.getCenterX();
            int centerZ = packet.getCenterZ();
            int radius = 20; // Search within 20 chunks of center

            // Search for chunks for sale in the area
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;

                    ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                    ClaimedChunk chunk = manager.getClaimedChunk(pos, player.level().dimension());

                    if (chunk != null && chunk.isForSale()) {
                        // Get owner info
                        String ownerName = "";
                        boolean isGovernment = chunk.getOwnershipType() == OwnershipType.HIERARCHY;
                        String cityName = "";

                        if (!isGovernment && chunk.getPlayerOwner() != null) {
                            var profile = player.server.getProfileCache().get(chunk.getPlayerOwner());
                            ownerName = profile.map(p -> p.getName()).orElse("Unknown");
                        }

                        // Get city name
                        City city = manager.getCity(chunk.getCityId());
                        if (city != null) {
                            cityName = city.getName();
                            if (isGovernment) {
                                ownerName = cityName;
                            }
                        }

                        // Get valuation from economy integration
                        double valuation = 0;
                        if (IntegrationRegistry.hasEconomyIntegration()) {
                            // Use total chunk value (includes all multipliers) for marketplace display
                            valuation = IntegrationRegistry.getChunkTotalValue(
                                chunkX, chunkZ, player.level().dimension().location().toString());
                        }

                        listings.add(new SyncMarketplaceDataPacket.ListingInfo(
                            chunkX, chunkZ,
                            ownerName,
                            isGovernment,
                            chunk.getSalePrice(),
                            cityName,
                            valuation
                        ));
                    }
                }
            }

            // Sort by distance from player
            listings.sort((a, b) -> {
                int distA = Math.abs(a.chunkX - centerX) + Math.abs(a.chunkZ - centerZ);
                int distB = Math.abs(b.chunkX - centerX) + Math.abs(b.chunkZ - centerZ);
                return Integer.compare(distA, distB);
            });

            NetworkHandler.sendToPlayer(new SyncMarketplaceDataPacket(listings), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleUpdateEntitySettings(UpdateEntitySettingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            UUID playerId = player.getUUID();
            String entityName = packet.getEntityName();
            String newName = packet.getNewName();
            String flagUrl = packet.getFlagUrl();

            // Validate flag URL (basic validation - must be http/https or empty)
            if (!flagUrl.isEmpty() && !flagUrl.startsWith("http://") && !flagUrl.startsWith("https://")) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cInvalid flag URL. Must start with http:// or https://"));
                return;
            }

            // Limit URL length
            if (flagUrl.length() > 512) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cFlag URL is too long (max 512 characters)"));
                return;
            }

            // Validate new name
            if (!newName.isEmpty() && (newName.length() < 2 || newName.length() > 32)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cName must be between 2 and 32 characters"));
                return;
            }

            switch (packet.getEntityType()) {
                case NATION -> {
                    Nation nation = manager.getNationByName(entityName);
                    if (nation == null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNation not found"));
                        return;
                    }

                    // Check if player is admin
                    if (!nation.isLeaderOrOfficer(playerId)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou must be a nation admin to change settings"));
                        return;
                    }

                    // Nation name changes require a constitutional amendment via the legislature
                    if (!newName.isEmpty() && !newName.equals(nation.getName())) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cNation name can only be changed via a constitutional amendment in the legislature."));
                        return;
                    }

                    nation.setFlagUrl(flagUrl);

                    // Update state pass-through rate if provided (what states give to nation)
                    if (packet.getTaxRate() >= 0) {
                        double statePassThrough = Math.max(0, Math.min(100, packet.getTaxRate()));
                        nation.setStatePassThroughRate(statePassThrough / 100.0); // Convert to decimal
                    }

                    manager.markDirty();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aNation settings updated"));
                }
                case STATE -> {
                    // Find state by name within player's nation
                    Nation playerNation = manager.getPlayerNation(playerId);
                    if (playerNation == null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are not in a nation"));
                        return;
                    }

                    State state = null;
                    for (State s : playerNation.getAllStates()) {
                        if (s.getName().equals(entityName)) {
                            state = s;
                            break;
                        }
                    }

                    if (state == null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cState not found"));
                        return;
                    }

                    // Check if player is governor or nation admin
                    if (!playerId.equals(state.getGovernorId()) && !playerNation.isLeaderOrOfficer(playerId)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou must be a state governor or nation admin to change settings"));
                        return;
                    }

                    // Update name if provided and different
                    if (!newName.isEmpty() && !newName.equals(state.getName())) {
                        // Check if name is taken within nation
                        boolean nameTaken = playerNation.getAllStates().stream()
                            .anyMatch(s -> s.getName().equals(newName));
                        if (nameTaken) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cA state with that name already exists"));
                            return;
                        }
                        state.setName(newName);
                    }

                    state.setFlagUrl(flagUrl);

                    // Update city pass-through rate if provided (what cities give to state)
                    if (packet.getTaxRate() >= 0) {
                        double cityPassThrough = Math.max(0, Math.min(100, packet.getTaxRate()));
                        state.setCityPassThroughRate(cityPassThrough / 100.0); // Convert to decimal
                    }

                    // Update state sales tax rate if provided
                    if (packet.getPassThroughRate() >= 0) {
                        double salesTax = Math.max(0, Math.min(50, packet.getPassThroughRate()));
                        state.setSalesTaxRate(salesTax / 100.0); // Convert to decimal
                    }

                    // Note: Max chunks per state is now controlled by server config (statecraft.toml)
                    // and cannot be changed via GUI

                    manager.markDirty();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aState settings updated"));
                }
                case CITY -> {
                    // Find city by name
                    Nation playerNation = manager.getPlayerNation(playerId);
                    if (playerNation == null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are not in a nation"));
                        return;
                    }

                    City city = null;
                    State parentState = null;
                    outer:
                    for (State s : playerNation.getAllStates()) {
                        for (City c : s.getAllCities()) {
                            if (c.getName().equals(entityName)) {
                                city = c;
                                parentState = s;
                                break outer;
                            }
                        }
                    }

                    if (city == null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCity not found"));
                        return;
                    }

                    // Check if player is mayor, state governor, or nation admin
                    if (!playerId.equals(city.getMayorId()) &&
                        !playerId.equals(parentState.getGovernorId()) &&
                        !playerNation.isLeaderOrOfficer(playerId)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou must be a city mayor, state governor, or nation admin to change settings"));
                        return;
                    }

                    // Update name if provided and different
                    if (!newName.isEmpty() && !newName.equals(city.getName())) {
                        // Check if name is taken within state
                        boolean nameTaken = parentState.getAllCities().stream()
                            .anyMatch(c -> c.getName().equals(newName));
                        if (nameTaken) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cA city with that name already exists in this state"));
                            return;
                        }
                        city.setName(newName);
                    }

                    city.setFlagUrl(flagUrl);
                    city.setPublicJoin(packet.isPublicJoin());

                    // Update property tax rate if provided (cities set the property tax rate)
                    if (packet.getTaxRate() >= 0) {
                        double taxRate = Math.max(0, Math.min(100, packet.getTaxRate()));
                        city.setTaxRate(taxRate / 100.0); // Convert to decimal
                    }

                    // Update sales tax rate if provided (second value in packet)
                    if (packet.getPassThroughRate() >= 0) {
                        double salesTaxRate = Math.max(0, Math.min(50, packet.getPassThroughRate()));
                        city.setSalesTaxRate(salesTaxRate / 100.0); // Convert to decimal
                    }

                    // Note: Max chunks per city is now controlled by server config (statecraft.toml)
                    // and cannot be changed via GUI
                    // Note: City pass-through rate is controlled by the State, not the City

                    manager.markDirty();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aCity settings updated"));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestMailData(RequestMailDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            MailManager mailManager = MailManager.getInstance();
            Mailbox mailbox = mailManager.getPlayerMailbox(player.getUUID());

            switch (packet.getAction()) {
                case GET_INBOX -> {
                    // Build mail list for client
                    List<SyncMailDataPacket.MailInfo> mailInfos = new ArrayList<>();
                    for (Mail mail : mailbox.getInbox()) {
                        mailInfos.add(new SyncMailDataPacket.MailInfo(
                            mail.getId().toString(),
                            mail.getSubject(),
                            mail.getBody(),
                            mail.getSenderName(),
                            mail.getFormattedTimestamp(),
                            mail.getType(),
                            mail.isRead(),
                            mail.getAttachedCurrency(),
                            mail.isCurrencyClaimed()
                        ));
                    }

                    NetworkHandler.sendToPlayer(new SyncMailDataPacket(
                        mailInfos, mailbox.getUnreadCount(), mailbox.getTotalCount()
                    ), player);
                }
                case MARK_READ -> {
                    String mailId = packet.getMailId();
                    if (!mailId.isEmpty()) {
                        Mail mail = mailbox.getMessage(UUID.fromString(mailId));
                        if (mail != null) {
                            mail.setRead(true);
                            mailManager.markDirty();
                        }
                    }
                }
                case MARK_ALL_READ -> {
                    mailbox.markAllAsRead();
                    mailManager.markDirty();
                }
                case DELETE -> {
                    String mailId = packet.getMailId();
                    if (!mailId.isEmpty()) {
                        mailbox.deleteMessage(UUID.fromString(mailId));
                        mailManager.markDirty();
                    }
                }
                case ARCHIVE -> {
                    String mailId = packet.getMailId();
                    if (!mailId.isEmpty()) {
                        Mail mail = mailbox.getMessage(UUID.fromString(mailId));
                        if (mail != null) {
                            mail.setArchived(true);
                            mailManager.markDirty();
                        }
                    }
                }
                case CLAIM_CURRENCY -> {
                    String mailId = packet.getMailId();
                    if (!mailId.isEmpty()) {
                        Mail mail = mailbox.getMessage(UUID.fromString(mailId));
                        if (mail != null && mail.hasUnclaimedCurrency()) {
                            double amount = mail.getAttachedCurrency();
                            boolean deposited = IntegrationRegistry.depositToPlayer(
                                player.getUUID(), amount, "Mail currency from " + mail.getSenderName());
                            if (deposited) {
                                mail.setCurrencyClaimed(true);
                                mailManager.markDirty();
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§a[Mail] Claimed " + IntegrationRegistry.formatCurrency(amount) + " from " + mail.getSenderName() + "!"));
                            } else {
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§cFailed to claim currency. Economy system may be unavailable."));
                            }
                        } else if (mail != null && mail.isCurrencyClaimed()) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§cCurrency has already been claimed from this mail."));
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestGovMailData(RequestGovMailDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            MailManager mailManager = MailManager.getInstance();

            String entityName = packet.getEntityName();
            Mailbox mailbox = null;

            // Find the entity and get its mailbox
            switch (packet.getEntityType()) {
                case NATION -> {
                    Nation nation = manager.getNationByName(entityName);
                    if (nation != null) {
                        mailbox = mailManager.getNationMailbox(nation.getId());
                    }
                }
                case STATE -> {
                    // Need to find state by name within player's nation
                    Nation playerNation = manager.getPlayerNation(player.getUUID());
                    if (playerNation != null) {
                        State state = playerNation.getStateByName(entityName);
                        if (state != null) {
                            mailbox = mailManager.getStateMailbox(state.getId());
                        }
                    }
                }
                case CITY -> {
                    // Need to find city by name within player's nation
                    Nation playerNation = manager.getPlayerNation(player.getUUID());
                    if (playerNation != null) {
                        outer:
                        for (State state : playerNation.getAllStates()) {
                            City city = state.getCityByName(entityName);
                            if (city != null) {
                                mailbox = mailManager.getCityMailbox(city.getId());
                                break outer;
                            }
                        }
                    }
                }
                case COMPANY -> {
                    // Find company by name
                    com.statecraft.company.Company company =
                        com.statecraft.company.CompanyManager.getInstance().getCompanyByName(entityName);
                    if (company != null) {
                        // Verify player is an officer or founder
                        if (company.isOfficer(player.getUUID())) {
                            mailbox = mailManager.getCompanyMailbox(company.getId());
                        }
                    }
                }
            }

            if (mailbox == null) {
                StateCraft.LOGGER.warn("Could not find mailbox for {} entity: {}", packet.getEntityType(), entityName);
                return;
            }

            switch (packet.getAction()) {
                case GET_INBOX -> {
                    // Build mail list for client
                    List<SyncMailDataPacket.MailInfo> mailInfos = new ArrayList<>();
                    for (Mail mail : mailbox.getInbox()) {
                        mailInfos.add(new SyncMailDataPacket.MailInfo(
                            mail.getId().toString(),
                            mail.getSubject(),
                            mail.getBody(),
                            mail.getSenderName(),
                            mail.getFormattedTimestamp(),
                            mail.getType(),
                            mail.isRead(),
                            mail.getAttachedCurrency(),
                            mail.isCurrencyClaimed()
                        ));
                    }
                    NetworkHandler.sendToPlayer(new SyncMailDataPacket(
                        mailInfos, mailbox.getUnreadCount(), mailbox.getAllMessages().size()
                    ), player);
                }
                case MARK_ALL_READ -> {
                    mailbox.markAllAsRead();
                    mailManager.markDirty();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSendMail(SendMailPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            String recipientName = packet.getRecipientName();
            String subject = packet.getSubject();
            String body = packet.getBody();

            // Find recipient by name
            ServerPlayer recipient = sender.server.getPlayerList().getPlayerByName(recipientName);
            UUID recipientId = null;

            if (recipient != null) {
                recipientId = recipient.getUUID();
            } else {
                // Try to find offline player
                var profile = sender.server.getProfileCache().get(recipientName);
                if (profile.isPresent()) {
                    recipientId = profile.get().getId();
                }
            }

            if (recipientId == null) {
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cPlayer '" + recipientName + "' not found!"));
                return;
            }

            if (recipientId.equals(sender.getUUID())) {
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cYou cannot send mail to yourself!"));
                return;
            }

            double currencyAmount = packet.getAttachedCurrency();

            // Handle currency attachment
            if (currencyAmount > 0) {
                if (!IntegrationRegistry.hasEconomyIntegration()) {
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cEconomy system is not available. Cannot attach currency."));
                    return;
                }

                double senderBalance = IntegrationRegistry.getPlayerBalance(sender.getUUID());
                if (senderBalance < currencyAmount) {
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cInsufficient funds! You have " + IntegrationRegistry.formatCurrency(senderBalance) +
                        " but tried to attach " + IntegrationRegistry.formatCurrency(currencyAmount) + "."));
                    return;
                }

                // Withdraw from sender
                boolean withdrawn = IntegrationRegistry.withdrawFromPlayer(sender.getUUID(), currencyAmount,
                    "Mail currency attachment to " + recipientName);
                if (!withdrawn) {
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cFailed to withdraw currency. Transaction cancelled."));
                    return;
                }

                // Send mail with currency
                MailManager.getInstance().sendPlayerMailWithCurrency(
                    sender.getUUID(),
                    sender.getName().getString(),
                    recipientId,
                    subject,
                    body,
                    currencyAmount
                );

                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aMail sent to " + recipientName + " with " +
                    IntegrationRegistry.formatCurrency(currencyAmount) + " attached!"));
            } else {
                // Send the mail (no currency)
                MailManager.getInstance().sendPlayerMail(
                    sender.getUUID(),
                    sender.getName().getString(),
                    recipientId,
                    subject,
                    body
                );

                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aMail sent to " + recipientName + "!"));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleJoinCitizenship(JoinCitizenshipPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            UUID playerId = player.getUUID();

            // Get the nation
            Nation nation = manager.getNationByName(packet.getNationName());
            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            // Handle NATION join type
            if (packet.getType() == JoinCitizenshipPacket.JoinType.NATION) {
                // Check if player is already in a nation
                Nation playerNation = manager.getPlayerNation(playerId);
                if (playerNation != null) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "You are already in a nation! Leave first before joining another."), player);
                    return;
                }

                // Check if nation is open
                if (!nation.isOpen()) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "This nation is not open to new members. Ask for an invite!"), player);
                    return;
                }

                // Add player to nation using manager method (this updates the playerNationIndex)
                boolean success = manager.addPlayerToNation(playerId, nation.getId());
                if (!success) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Failed to join nation. Please try again."), player);
                    return;
                }

                // Mark data dirty
                if (player.level() instanceof ServerLevel serverLevel) {
                    NationSavedData.get(serverLevel).markForSave();
                }

                NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                    "You have joined " + nation.getName() + "!"), player);

                // Notify other nation members
                for (UUID memberId : nation.getMembers()) {
                    if (!memberId.equals(playerId)) {
                        ServerPlayer member = player.getServer().getPlayerList().getPlayer(memberId);
                        if (member != null) {
                            member.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§e" + player.getName().getString() + " §ahas joined the nation!"));
                        }
                    }
                }
                return;
            }

            // Check if player is a member of this nation (for STATE/CITY joins)
            Nation playerNation = manager.getPlayerNation(playerId);
            if (playerNation == null || !playerNation.getId().equals(nation.getId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "You must be a member of " + nation.getName() + " to become a citizen of its states/cities!"), player);
                return;
            }

            // Get the state
            State state = nation.getStateByName(packet.getStateName());
            if (state == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "State not found!"), player);
                return;
            }

            if (packet.getType() == JoinCitizenshipPacket.JoinType.STATE) {
                // Joining a state
                if (state.isCitizen(playerId)) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "You are already a citizen of " + state.getName() + "!"), player);
                    return;
                }

                // Check if this is player's first state (primary) or if they own a chunk
                boolean hasPrimaryState = false;
                for (State s : nation.getAllStates()) {
                    if (s.isCitizen(playerId)) {
                        hasPrimaryState = true;
                        break;
                    }
                }

                if (hasPrimaryState) {
                    // Must own a chunk in this state to join as secondary
                    if (!state.playerOwnsChunkInState(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You must own a chunk in " + state.getName() + " to become a citizen! " +
                            "(Purchase land first)"), player);
                        return;
                    }
                }

                // Add as citizen
                state.addCitizen(playerId);

                // Mark data dirty
                if (player.level() instanceof ServerLevel serverLevel) {
                    NationSavedData.get(serverLevel).markForSave();
                }

                NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                    "You are now a citizen of " + state.getName() + "!"), player);

            } else {
                // Joining a city
                String cityName = packet.getCityName();
                City city = state.getCityByName(cityName);
                if (city == null) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "City not found!"), player);
                    return;
                }

                if (city.isResident(playerId)) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "You are already a resident of " + city.getName() + "!"), player);
                    return;
                }

                // Check if city allows public join
                if (!city.isPublicJoin()) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        city.getName() + " is not accepting new residents. Contact the mayor for an invite."), player);
                    return;
                }

                // Check if this is player's first city (primary) or if they own a chunk
                boolean hasPrimaryCity = false;
                for (State s : nation.getAllStates()) {
                    for (City c : s.getAllCities()) {
                        if (c.isResident(playerId)) {
                            hasPrimaryCity = true;
                            break;
                        }
                    }
                    if (hasPrimaryCity) break;
                }

                if (hasPrimaryCity) {
                    // Must own a chunk in this city to join as secondary
                    if (!city.playerOwnsChunkInCity(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You must own a chunk in " + city.getName() + " to become a resident! " +
                            "(Purchase land first)"), player);
                        return;
                    }
                }

                // Add as resident (also makes them a state citizen if not already)
                city.addResident(playerId);
                if (!state.isCitizen(playerId)) {
                    state.addCitizen(playerId);
                }

                // Mark data dirty
                if (player.level() instanceof ServerLevel serverLevel) {
                    NationSavedData.get(serverLevel).markForSave();
                }

                NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                    "You are now a resident of " + city.getName() + "!"), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestMyStates(RequestMyStatesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            List<SyncMyStatesPacket.StateEntry> states = new ArrayList<>();

            if (nation != null) {
                boolean foundPrimary = false;

                for (State state : nation.getAllStates()) {
                    if (state.isCitizen(player.getUUID())) {
                        String governorName = getPlayerName(player.server, state.getGovernorId());

                        // Count chunks owned by player in this state
                        int ownedChunks = 0;
                        for (City city : state.getAllCities()) {
                            for (ClaimedChunk chunk : city.getAllChunks()) {
                                if (player.getUUID().equals(chunk.getPlayerOwner())) {
                                    ownedChunks++;
                                }
                            }
                        }

                        // First state found is primary
                        boolean isPrimary = !foundPrimary;
                        foundPrimary = true;

                        states.add(new SyncMyStatesPacket.StateEntry(
                            state.getName(),
                            governorName,
                            isPrimary,
                            ownedChunks
                        ));
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncMyStatesPacket(states), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestMyCities(RequestMyCitiesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            List<SyncMyCitiesPacket.CityEntry> cities = new ArrayList<>();

            if (nation != null) {
                boolean foundPrimary = false;

                for (State state : nation.getAllStates()) {
                    for (City city : state.getAllCities()) {
                        if (city.isResident(player.getUUID())) {
                            String mayorName = getPlayerName(player.server, city.getMayorId());

                            // Count chunks owned by player in this city
                            int ownedChunks = 0;
                            for (ClaimedChunk chunk : city.getAllChunks()) {
                                if (player.getUUID().equals(chunk.getPlayerOwner())) {
                                    ownedChunks++;
                                }
                            }

                            // First city found is primary
                            boolean isPrimary = !foundPrimary;
                            foundPrimary = true;

                            cities.add(new SyncMyCitiesPacket.CityEntry(
                                city.getName(),
                                state.getName(),
                                mayorName,
                                isPrimary,
                                ownedChunks
                            ));
                        }
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncMyCitiesPacket(cities), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleLeaveCitizenship(LeaveCitizenshipPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            UUID playerId = player.getUUID();

            switch (packet.getType()) {
                case NATION -> {
                    // Leaving a nation
                    Nation nation = manager.getNationByName(packet.getNationName());
                    if (nation == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                        return;
                    }

                    // Check if player is a member
                    if (!nation.isMember(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You are not a member of " + nation.getName() + "!"), player);
                        return;
                    }

                    // Check if player is the leader
                    if (playerId.equals(nation.getLeaderId())) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You cannot leave a nation you lead! Transfer leadership first or disband the nation."), player);
                        return;
                    }

                    // Check if player owns any property in the nation
                    if (nation.playerOwnsPropertyInNation(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You cannot leave the nation while you own property! Sell or transfer your chunks first."), player);
                        return;
                    }

                    // Remove from all cities and states in this nation first
                    for (State state : nation.getAllStates()) {
                        for (City city : state.getAllCities()) {
                            city.removeResident(playerId);
                        }
                        state.removeCitizen(playerId);
                    }

                    // Remove from nation
                    nation.removeMember(playerId);

                    // Mark data dirty
                    if (player.level() instanceof ServerLevel serverLevel) {
                        NationSavedData.get(serverLevel).markForSave();
                    }

                    NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                        "You have left " + nation.getName() + "."), player);
                }

                case STATE -> {
                    // Leaving a state
                    Nation nation = manager.getNationByName(packet.getNationName());
                    if (nation == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                        return;
                    }

                    State state = nation.getStateByName(packet.getStateName());
                    if (state == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "State not found!"), player);
                        return;
                    }

                    // Check if player is a citizen
                    if (!state.isCitizen(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You are not a citizen of " + state.getName() + "!"), player);
                        return;
                    }

                    // Check if player is the governor
                    if (playerId.equals(state.getGovernorId())) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You cannot leave a state you govern! Transfer governorship first."), player);
                        return;
                    }

                    // Check if player owns any property in the state
                    if (state.playerOwnsChunkInState(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You cannot leave the state while you own property there! Sell or transfer your chunks first."), player);
                        return;
                    }

                    // Remove from all cities in this state first
                    for (City city : state.getAllCities()) {
                        city.removeResident(playerId);
                    }

                    // Remove from state
                    state.removeCitizen(playerId);

                    // Mark data dirty
                    if (player.level() instanceof ServerLevel serverLevel) {
                        NationSavedData.get(serverLevel).markForSave();
                    }

                    NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                        "You have left " + state.getName() + "."), player);
                }

                case CITY -> {
                    // Leaving a city
                    Nation nation = manager.getNationByName(packet.getNationName());
                    if (nation == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                        return;
                    }

                    State state = nation.getStateByName(packet.getStateName());
                    if (state == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "State not found!"), player);
                        return;
                    }

                    City city = state.getCityByName(packet.getCityName());
                    if (city == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "City not found!"), player);
                        return;
                    }

                    // Check if player is a resident
                    if (!city.isResident(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You are not a resident of " + city.getName() + "!"), player);
                        return;
                    }

                    // Check if player is the mayor
                    if (playerId.equals(city.getMayorId())) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You cannot leave a city you are mayor of! Transfer mayorship first."), player);
                        return;
                    }

                    // Check if player owns any property in the city
                    if (city.playerOwnsChunkInCity(playerId)) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You cannot leave the city while you own property there! Sell or transfer your chunks first."), player);
                        return;
                    }

                    // Remove from city
                    city.removeResident(playerId);

                    // Mark data dirty
                    if (player.level() instanceof ServerLevel level) {
                        NationSavedData.get(level).markForSave();
                    }

                    NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                        "You have left " + city.getName() + "."), player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleInvitePlayer(InvitePlayerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            // Find the target player
            ServerPlayer target = sender.server.getPlayerList().getPlayerByName(packet.getPlayerName());
            if (target == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Player '" + packet.getPlayerName() + "' not found or is offline!"), sender);
                return;
            }

            switch (packet.getType()) {
                case NATION -> {
                    Nation nation = manager.getNationByName(packet.getEntityName());
                    if (nation == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), sender);
                        return;
                    }

                    // Check if sender has permission to invite
                    if (!nation.isLeaderOrOfficer(sender.getUUID())) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "You don't have permission to invite players to this nation!"), sender);
                        return;
                    }

                    // Check if target is already in a nation
                    if (manager.getPlayerNation(target.getUUID()) != null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "That player is already in a nation!"), sender);
                        return;
                    }

                    // Create invitation
                    Invitation invite = InvitationManager.getInstance().createInvitation(
                        target.getUUID(),
                        sender.getUUID(),
                        nation.getId(),
                        Invitation.InvitationType.NATION
                    );

                    if (invite == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "That player already has a pending invite to your nation!"), sender);
                        return;
                    }

                    // Mark data dirty
                    if (sender.level() instanceof ServerLevel serverLevel) {
                        NationSavedData.get(serverLevel).markForSave();
                    }

                    // Notify sender
                    NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                        "Invited " + target.getName().getString() + " to " + nation.getName() + "!"), sender);

                    // Notify target
                    target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§6You've been invited to join §e" + nation.getName() + "§6!"));
                    target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7Use §e/sc nation accept " + nation.getName() + " §7to join, or §e/sc nation deny " + nation.getName() + " §7to decline."));
                    target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7This invite expires in 5 minutes."));
                }

                case STATE -> {
                    // TODO: Implement state invitations if needed
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "State invitations are not yet implemented!"), sender);
                }

                case CITY -> {
                    // TODO: Implement city invitations if needed
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "City invitations are not yet implemented!"), sender);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestAllNations(RequestAllNationsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            UUID playerId = player.getUUID();

            List<SyncAllNationsPacket.NationEntry> nationsList = new ArrayList<>();

            for (Nation nation : manager.getAllNations()) {
                String leaderName = getPlayerName(player.server, nation.getLeaderId());
                boolean isMember = nation.isMember(playerId);

                nationsList.add(new SyncAllNationsPacket.NationEntry(
                    nation.getName(),
                    leaderName,
                    nation.getAllMembers().size(),
                    nation.getStateCount(),
                    nation.isOpen(),
                    isMember
                ));
            }

            // Sort: player's nation first, then alphabetically
            nationsList.sort((a, b) -> {
                if (a.isMember && !b.isMember) return -1;
                if (!a.isMember && b.isMember) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });

            NetworkHandler.sendToPlayer(new SyncAllNationsPacket(nationsList), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleAbandonChunk(AbandonChunkPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            UUID playerId = player.getUUID();
            int chunkX = packet.getChunkX();
            int chunkZ = packet.getChunkZ();

            // Find the chunk
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());
            if (chunk == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "This chunk is not claimed by any city."), player);
                return;
            }

            // Check if player owns this chunk
            if (!playerId.equals(chunk.getPlayerOwner())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "You do not own this chunk!"), player);
                return;
            }

            // Get the city this chunk belongs to
            City city = manager.getCity(chunk.getCityId());
            if (city == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Could not find the city this chunk belongs to."), player);
                return;
            }

            // Remove private ownership - chunk returns to city (government) ownership
            chunk.setPlayerOwner(null);
            chunk.setForSale(false);
            chunk.setSalePrice(0);

            // Mark data dirty
            if (player.level() instanceof ServerLevel serverLevel) {
                NationSavedData.get(serverLevel).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                "You have abandoned your claim on chunk (" + chunkX + ", " + chunkZ + "). " +
                "It has been returned to " + city.getName() + "."), player);
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== Economy Integration Helpers ====================

    /**
     * Get the chunk claim fee for a nation
     * First checks nation's legislature-set fee, then falls back to config
     * Returns 0 if fee is disabled or economy mod is not loaded
     */
    private static double getChunkClaimFee(Nation nation) {
        try {
            // Check if StateCraft Economy is loaded (required for fee to work)
            if (!net.minecraftforge.fml.ModList.get().isLoaded("statecraft_economy")) {
                return 0;
            }

            // First check nation's legislature-set chunk claim fee
            if (nation != null && nation.getChunkClaimFee() > 0) {
                return nation.getChunkClaimFee();
            }

            // Fall back to config if enabled
            if (!com.statecraft.config.StateCraftConfig.CHUNK_CLAIM_FEE_ENABLED.get()) {
                return 0;
            }

            return com.statecraft.config.StateCraftConfig.CHUNK_CLAIM_FEE.get();
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Could not get chunk claim fee: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get city treasury balance
     * Returns 0 if economy mod is not loaded
     */
    private static double getCityTreasuryBalance(UUID cityId) {
        try {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("statecraft_economy")) {
                return Double.MAX_VALUE; // No economy = no limit
            }

            Class<?> managerClass = Class.forName("com.statecraft.economy.core.EconomyManager");
            Object instance = managerClass.getMethod("getInstance").invoke(null);
            Object treasury = managerClass.getMethod("getOrCreateCityTreasury", UUID.class).invoke(instance, cityId);
            return (double) treasury.getClass().getMethod("getBalance").invoke(treasury);
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Could not get city treasury balance: {}", e.getMessage());
            return Double.MAX_VALUE; // If we can't check, assume they have enough
        }
    }

    /**
     * Deduct amount from city treasury
     */
    private static void deductFromCityTreasury(UUID cityId, double amount) {
        try {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("statecraft_economy")) {
                return;
            }

            Class<?> managerClass = Class.forName("com.statecraft.economy.core.EconomyManager");
            Object instance = managerClass.getMethod("getInstance").invoke(null);
            Object treasury = managerClass.getMethod("getOrCreateCityTreasury", UUID.class).invoke(instance, cityId);
            treasury.getClass().getMethod("subtract", double.class).invoke(treasury, amount);
            managerClass.getMethod("markDirty").invoke(instance);
            StateCraft.LOGGER.info("Deducted ${} from city {} for chunk claim fee", String.format("%.2f", amount), cityId);
        } catch (Exception e) {
            StateCraft.LOGGER.error("Could not deduct from city treasury: {}", e.getMessage());
        }
    }

    /**
     * Deposit chunk claim fee to nation treasury
     */
    private static void depositToNationTreasury(UUID nationId, double amount, String reason) {
        try {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("statecraft_economy")) {
                return;
            }

            Class<?> managerClass = Class.forName("com.statecraft.economy.core.EconomyManager");
            Object instance = managerClass.getMethod("getInstance").invoke(null);
            Object treasury = managerClass.getMethod("getOrCreateNationTreasury", UUID.class).invoke(instance, nationId);
            treasury.getClass().getMethod("add", double.class).invoke(treasury, amount);
            managerClass.getMethod("markDirty").invoke(instance);
            StateCraft.LOGGER.info("Deposited ${} to nation {} treasury: {}", String.format("%.2f", amount), nationId, reason);
        } catch (Exception e) {
            StateCraft.LOGGER.error("Could not deposit to nation treasury: {}", e.getMessage());
        }
    }

    // ==================== Contract Packet Handlers ====================

    /**
     * Handle request for contract data
     */
    public static void handleRequestContracts(RequestContractsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            if (nation == null) {
                // Send empty contract data
                NetworkHandler.sendToPlayer(new SyncContractsPacket(
                    "",
                    false,
                    false,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    0
                ), player);
                return;
            }

            syncContractsToPlayer(player, nation);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle creating a new government contract
     */
    public static void handleCreateContract(CreateContractPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            // Check if player is a legislator (officer) or leader (only they can create contracts)
            boolean isLeader = player.getUUID().equals(nation.getLeaderId());
            boolean isLegislator = nation.isOfficer(player.getUUID());

            if (!isLeader && !isLegislator) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Only legislature members or the leader can create contracts!"), player);
                return;
            }

            // Validate input
            if (packet.getTitle() == null || packet.getTitle().trim().isEmpty()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Contract title is required!"), player);
                return;
            }

            if (packet.getBudget() <= 0) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Contract budget must be greater than 0!"), player);
                return;
            }

            if (packet.getChunks() == null || packet.getChunks().isEmpty()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "At least one chunk must be designated!"), player);
                return;
            }

            // Verify chunks belong to the nation - use dimension resource key
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.tryParse(packet.getDimension()));

            for (CreateContractPacket.ChunkData chunkData : packet.getChunks()) {
                ChunkPos chunkPos = new ChunkPos(chunkData.x, chunkData.z);
                ClaimedChunk claimed = manager.getClaimedChunk(chunkPos, dimKey);
                if (claimed == null) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Chunk (" + chunkData.x + ", " + chunkData.z + ") is not claimed!"), player);
                    return;
                }
                // Verify the chunk belongs to this nation by checking its city
                City chunkCity = manager.getCity(claimed.getCityId());
                if (chunkCity == null) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Chunk (" + chunkData.x + ", " + chunkData.z + ") has no associated city!"), player);
                    return;
                }
                State chunkState = manager.getState(chunkCity.getStateId());
                if (chunkState == null || !chunkState.getNationId().equals(nation.getId())) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Chunk (" + chunkData.x + ", " + chunkData.z + ") does not belong to " + nation.getName() + "!"), player);
                    return;
                }
            }

            // Check if nation treasury can afford to put budget in escrow
            double nationBalance = 0;
            if (IntegrationRegistry.hasEconomyIntegration()) {
                nationBalance = IntegrationRegistry.getNationBalance(nation.getName());
            }

            if (nationBalance < packet.getBudget()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Nation treasury has insufficient funds! Need " + IntegrationRegistry.formatCurrency(packet.getBudget()) +
                    " but only has " + IntegrationRegistry.formatCurrency(nationBalance)), player);
                return;
            }

            // Create the contract
            ContractManager contractManager = ContractManager.getInstance();
            Contract contract = contractManager.createContract(
                nation.getId(),
                packet.getTitle(),
                player.getUUID(),
                player.getName().getString()
            );

            // Set contract details
            contract.setDescription(packet.getDescription());
            contract.setRequirements(packet.getRequirements());
            contract.setTotalBudget(packet.getBudget());
            contract.setBondAmount(packet.getBondAmount());
            contract.setDimension(packet.getDimension());

            // Set compensation type
            try {
                Contract.CompensationType compType = Contract.CompensationType.valueOf(packet.getCompensationType());
                contract.setCompensationType(compType);

                // For valuation-based, set the payment rate per improvement point
                if (compType == Contract.CompensationType.VALUATION_BASED) {
                    double paymentPerPoint = packet.getPaymentPerImprovementPoint();
                    if (paymentPerPoint <= 0) {
                        paymentPerPoint = 1.0; // Default $1 per improvement point
                    }
                    contract.setPaymentPerImprovementPoint(paymentPerPoint);
                }

                // For milestone type, set custom milestone descriptions
                if (compType == Contract.CompensationType.MILESTONE && packet.getMilestoneDescriptions() != null) {
                    for (java.util.Map.Entry<Integer, String> entry : packet.getMilestoneDescriptions().entrySet()) {
                        contract.setMilestoneDescription(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IllegalArgumentException e) {
                contract.setCompensationType(Contract.CompensationType.MILESTONE);
            }

            // Add designated chunks
            for (CreateContractPacket.ChunkData chunkData : packet.getChunks()) {
                contract.addDesignatedChunk(new ChunkPos(chunkData.x, chunkData.z));
            }

            // Move budget to escrow (withdraw from nation treasury)
            if (IntegrationRegistry.hasEconomyIntegration()) {
                IntegrationRegistry.withdrawFromNation(nation.getName(), packet.getBudget());
            }
            contract.depositToEscrow(packet.getBudget());

            // Mark for save
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                "Contract " + contract.getContractNumber() + " created! Budget of " +
                IntegrationRegistry.formatCurrency(packet.getBudget()) + " held in escrow."), player);

            // Sync contracts to the player
            syncContractsToPlayer(player, nation);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle submitting a bid on a contract
     */
    public static void handleSubmitContractBid(SubmitContractBidPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            // Parse contract ID
            UUID contractId;
            try {
                contractId = UUID.fromString(packet.getContractId());
            } catch (IllegalArgumentException e) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Invalid contract ID!"), player);
                return;
            }

            ContractManager contractManager = ContractManager.getInstance();
            Contract contract = contractManager.getContract(contractId);

            if (contract == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Contract not found!"), player);
                return;
            }

            // Verify contract belongs to the nation
            if (!contract.getNationId().equals(nation.getId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Contract does not belong to this nation!"), player);
                return;
            }

            // Check contract is in bidding status
            if (contract.getStatus() != Contract.Status.BIDDING) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "This contract is not open for bidding!"), player);
                return;
            }

            // Validate bid amount
            if (packet.getBidAmount() <= 0) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Bid amount must be greater than 0!"), player);
                return;
            }

            // Check if player has already bid (only one bid per player per contract)
            for (ContractBid existingBid : contract.getBids()) {
                if (existingBid.getBidderId().equals(player.getUUID())) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "You have already submitted a bid on this contract!"), player);
                    return;
                }
            }

            // If bond is required, check if player can afford it
            if (contract.getBondAmount() > 0) {
                double playerBalance = IntegrationRegistry.hasEconomyIntegration()
                    ? IntegrationRegistry.getPlayerBalance(player.getUUID())
                    : 0;

                if (playerBalance < contract.getBondAmount()) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Insufficient funds for required bond! Need " +
                        IntegrationRegistry.formatCurrency(contract.getBondAmount())), player);
                    return;
                }

                // Withdraw bond from player (will be held in escrow)
                IntegrationRegistry.withdrawFromPlayer(player.getUUID(), contract.getBondAmount(), "Contract bid bond for " + contract.getContractNumber());
            }

            // Create and submit the bid
            ContractBid bid = new ContractBid(
                contractId,
                player.getUUID(),
                player.getName().getString(),
                packet.getBidAmount()
            );
            bid.setProposal(packet.getProposal());
            bid.setProposedDurationDays(packet.getProposedDays());
            bid.setBondPaid(contract.getBondAmount() > 0);

            boolean success = contractManager.submitBid(contractId, bid);

            if (!success) {
                // Refund bond if bid submission failed
                if (contract.getBondAmount() > 0) {
                    IntegrationRegistry.depositToPlayer(player.getUUID(), contract.getBondAmount());
                }
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Failed to submit bid!"), player);
                return;
            }

            // Mark for save
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                "Bid submitted for " + IntegrationRegistry.formatCurrency(packet.getBidAmount()) +
                " on contract " + contract.getContractNumber() +
                (contract.getBondAmount() > 0 ? " (Bond of " + IntegrationRegistry.formatCurrency(contract.getBondAmount()) + " held)" : "")), player);

            // Sync contracts to the player
            syncContractsToPlayer(player, nation);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle contract actions (open bidding, approve bid, complete milestone, etc.)
     */
    public static void handleContractAction(ContractActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            // Parse contract ID
            UUID contractId;
            try {
                contractId = UUID.fromString(packet.getContractId());
            } catch (IllegalArgumentException e) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Invalid contract ID!"), player);
                return;
            }

            ContractManager contractManager = ContractManager.getInstance();
            Contract contract = contractManager.getContract(contractId);

            if (contract == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Contract not found!"), player);
                return;
            }

            // Verify contract belongs to the nation
            if (!contract.getNationId().equals(nation.getId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Contract does not belong to this nation!"), player);
                return;
            }

            // Check permissions based on action type
            boolean isLeader = player.getUUID().equals(nation.getLeaderId());
            boolean isLegislator = nation.isOfficer(player.getUUID());
            boolean isContractor = player.getUUID().equals(contract.getContractorId());

            String resultMessage;
            boolean success;

            switch (packet.getAction()) {
                case OPEN_BIDDING:
                    // Only legislature members/leader can open bidding
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can open contracts for bidding!"), player);
                        return;
                    }
                    long biddingDuration = packet.getDuration() > 0 ? packet.getDuration() : 7L * 24 * 60 * 60 * 1000; // Default 7 days
                    success = contractManager.openForBidding(contractId, biddingDuration);
                    resultMessage = success ? "Contract " + contract.getContractNumber() + " is now open for bidding!" :
                        "Failed to open contract for bidding!";
                    break;

                case CLOSE_BIDDING:
                    // Only legislature members/leader can close bidding
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can close bidding!"), player);
                        return;
                    }
                    success = contractManager.closeBidding(contractId);
                    resultMessage = success ? "Bidding closed on contract " + contract.getContractNumber() :
                        "Failed to close bidding!";
                    break;

                case APPROVE_BID:
                    // Only legislature can approve bids
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can approve bids!"), player);
                        return;
                    }

                    UUID bidId;
                    try {
                        bidId = UUID.fromString(packet.getTargetId());
                    } catch (IllegalArgumentException e) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Invalid bid ID!"), player);
                        return;
                    }

                    // Get the bid to find proposed duration
                    ContractBid selectedBid = null;
                    for (ContractBid bid : contract.getBids()) {
                        if (bid.getBidId().equals(bidId)) {
                            selectedBid = bid;
                            break;
                        }
                    }

                    if (selectedBid == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Bid not found!"), player);
                        return;
                    }

                    // Calculate deadline based on proposed duration (default 7 days if not specified)
                    long deadlineDuration = packet.getDuration() > 0 ? packet.getDuration() :
                        (long) selectedBid.getProposedDurationDays() * 24 * 60 * 60 * 1000;

                    success = contractManager.approveBid(contractId, bidId, deadlineDuration);

                    if (success) {
                        // Store baseline improvement scores for valuation-based compensation
                        if (contract.getCompensationType() == Contract.CompensationType.VALUATION_BASED) {
                            for (net.minecraft.world.level.ChunkPos chunk : contract.getDesignatedChunks()) {
                                int baselineScore = IntegrationRegistry.getChunkImprovementScore(
                                    chunk.x, chunk.z, contract.getDimension());
                                contract.setBaselineImprovementScore(chunk.toLong(), baselineScore);
                            }
                            StateCraft.LOGGER.info("Stored baseline improvement scores for valuation-based contract {}",
                                contract.getContractNumber());
                        }

                        resultMessage = "Bid from " + selectedBid.getBidderName() + " approved for contract " +
                            contract.getContractNumber() + "!";

                        // Notify the contractor
                        ServerPlayer contractor = player.getServer().getPlayerList().getPlayer(selectedBid.getBidderId());
                        if (contractor != null) {
                            contractor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§a[Contracts] §fYour bid for contract " + contract.getContractNumber() +
                                " (" + contract.getTitle() + ") has been approved! You have " +
                                selectedBid.getProposedDurationDays() + " days to complete the project."));
                        }

                        // Refund bonds for non-selected bidders
                        for (ContractBid bid : contract.getBids()) {
                            if (!bid.getBidId().equals(bidId) && bid.isBondPaid() && contract.getBondAmount() > 0) {
                                IntegrationRegistry.depositToPlayer(bid.getBidderId(), contract.getBondAmount());
                                ServerPlayer bidderPlayer = player.getServer().getPlayerList().getPlayer(bid.getBidderId());
                                if (bidderPlayer != null) {
                                    bidderPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        "§e[Contracts] §fYour bond of " + IntegrationRegistry.formatCurrency(contract.getBondAmount()) +
                                        " has been refunded. Another bid was selected for " + contract.getContractNumber() + "."));
                                }
                            }
                        }
                    } else {
                        resultMessage = "Failed to approve bid!";
                    }
                    break;

                case UPDATE_PROGRESS:
                    // Only contractor can update progress
                    if (!isContractor) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only the contractor can update progress!"), player);
                        return;
                    }
                    contractManager.updateProgress(contractId, packet.getValue());
                    resultMessage = "Progress updated to " + packet.getValue() + "%";
                    success = true;
                    break;

                case REQUEST_MILESTONE_APPROVAL:
                    // Only contractor can request milestone approval
                    if (!isContractor) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only the contractor can request milestone approval!"), player);
                        return;
                    }
                    success = contract.requestMilestoneApproval(packet.getValue());
                    if (success) {
                        contractManager.markDirty();
                        resultMessage = "Milestone " + packet.getValue() + "% approval requested! Awaiting legislature review.";

                        // Notify nation leader
                        ServerPlayer leader = player.getServer().getPlayerList().getPlayer(nation.getLeaderId());
                        if (leader != null && !leader.getUUID().equals(player.getUUID())) {
                            leader.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§e[Contracts] §f" + contract.getContractorName() + " has requested approval for milestone " +
                                packet.getValue() + "% on contract " + contract.getContractNumber() + "."));
                        }
                    } else {
                        resultMessage = "Failed to request milestone approval! Ensure progress is at or above " + packet.getValue() + "% and previous milestones are complete.";
                    }
                    break;

                case COMPLETE_MILESTONE:
                    // Only legislature can approve milestones
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can approve milestones!"), player);
                        return;
                    }
                    success = contractManager.completeMilestone(contractId, packet.getValue());
                    if (success) {
                        // Calculate milestone payment
                        double milestonePayment = contract.getTotalBudget() * 0.25; // 25% per milestone
                        // Transfer from escrow to contractor
                        if (contract.getEscrowBalance() >= milestonePayment && IntegrationRegistry.hasEconomyIntegration()) {
                            contract.withdrawFromEscrow(milestonePayment);
                            IntegrationRegistry.depositToPlayer(contract.getContractorId(), milestonePayment);
                            contract.recordPayment(milestonePayment);
                        }
                        resultMessage = "Milestone " + packet.getValue() + "% approved! Payment of " +
                            IntegrationRegistry.formatCurrency(milestonePayment) + " released.";

                        // Notify contractor
                        ServerPlayer contractor = player.getServer().getPlayerList().getPlayer(contract.getContractorId());
                        if (contractor != null) {
                            contractor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§a[Contracts] §fMilestone " + packet.getValue() + "% approved for " +
                                contract.getContractNumber() + "! Payment of " +
                                IntegrationRegistry.formatCurrency(milestonePayment) + " released."));
                        }
                    } else {
                        resultMessage = "Failed to complete milestone!";
                    }
                    break;

                case COMPLETE_CONTRACT:
                    // Only government can mark complete (after final inspection)
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can complete contracts!"), player);
                        return;
                    }
                    success = contractManager.completeContract(contractId);
                    if (success) {
                        double finalPayment = 0;
                        String paymentDetails = "";

                        // Handle payment based on compensation type
                        if (contract.getCompensationType() == Contract.CompensationType.VALUATION_BASED) {
                            // Calculate payment based on improvement score increase
                            java.util.Map<Long, Integer> currentScores = new java.util.HashMap<>();
                            for (net.minecraft.world.level.ChunkPos chunk : contract.getDesignatedChunks()) {
                                int currentScore = IntegrationRegistry.getChunkImprovementScore(
                                    chunk.x, chunk.z, contract.getDimension());
                                currentScores.put(chunk.toLong(), currentScore);
                            }

                            int totalImprovement = contract.calculateTotalImprovement(currentScores);
                            double valuationPayment = contract.calculateValuationBasedPayment(currentScores);

                            // Cap payment at budget (escrow balance)
                            finalPayment = Math.min(valuationPayment, contract.getEscrowBalance());

                            if (finalPayment > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                                contract.withdrawFromEscrow(finalPayment);
                                IntegrationRegistry.depositToPlayer(contract.getContractorId(), finalPayment);
                                contract.recordPayment(finalPayment);
                            }

                            // Return unused escrow to nation treasury
                            double unusedEscrow = contract.getEscrowBalance();
                            if (unusedEscrow > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                                contract.withdrawFromEscrow(unusedEscrow);
                                IntegrationRegistry.depositToNation(nation.getName(), unusedEscrow,
                                    "Unused escrow returned from contract " + contract.getContractNumber());
                            }

                            paymentDetails = String.format(" Improvement: %d points = %s (unused: %s returned to treasury)",
                                totalImprovement, IntegrationRegistry.formatCurrency(finalPayment),
                                IntegrationRegistry.formatCurrency(unusedEscrow));
                        } else {
                            // For FIXED and MILESTONE types, release remaining escrow
                            double remainingEscrow = contract.getEscrowBalance();
                            if (remainingEscrow > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                                contract.withdrawFromEscrow(remainingEscrow);
                                IntegrationRegistry.depositToPlayer(contract.getContractorId(), remainingEscrow);
                                contract.recordPayment(remainingEscrow);
                                finalPayment = remainingEscrow;
                            }
                            paymentDetails = " Final payment: " + IntegrationRegistry.formatCurrency(finalPayment);
                        }

                        // Return contractor's bond
                        if (contract.getBondAmount() > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                            IntegrationRegistry.depositToPlayer(contract.getContractorId(), contract.getBondAmount());
                        }

                        resultMessage = "Contract " + contract.getContractNumber() + " completed successfully!" +
                            paymentDetails + " Bond released to " + contract.getContractorName() + ".";

                        // Notify contractor
                        ServerPlayer contractor = player.getServer().getPlayerList().getPlayer(contract.getContractorId());
                        if (contractor != null) {
                            contractor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§a[Contracts] §fCongratulations! Contract " + contract.getContractNumber() +
                                " has been marked complete." + paymentDetails + " Bond released!"));
                        }
                    } else {
                        resultMessage = "Failed to complete contract!";
                    }
                    break;

                case CANCEL_CONTRACT:
                    // Only government can cancel
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can cancel contracts!"), player);
                        return;
                    }
                    success = contractManager.cancelContract(contractId);
                    if (success) {
                        // Return escrow to nation treasury
                        double escrowRefund = contract.getEscrowBalance();
                        if (escrowRefund > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                            contract.withdrawFromEscrow(escrowRefund);
                            IntegrationRegistry.depositToNation(nation.getName(), escrowRefund);
                        }

                        // Refund bonds to all bidders
                        for (ContractBid bid : contract.getBids()) {
                            if (bid.isBondPaid() && contract.getBondAmount() > 0) {
                                IntegrationRegistry.depositToPlayer(bid.getBidderId(), contract.getBondAmount());
                            }
                        }

                        resultMessage = "Contract " + contract.getContractNumber() + " cancelled. Escrow returned to treasury.";

                        // Notify contractor if any
                        if (contract.getContractorId() != null) {
                            ServerPlayer contractor = player.getServer().getPlayerList().getPlayer(contract.getContractorId());
                            if (contractor != null) {
                                contractor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§c[Contracts] §fContract " + contract.getContractNumber() + " has been cancelled."));
                            }
                        }
                    } else {
                        resultMessage = "Failed to cancel contract!";
                    }
                    break;

                case FAIL_CONTRACT:
                    // Only government can mark as failed
                    if (!isLeader && !isLegislator) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Only legislature members or the leader can mark contracts as failed!"), player);
                        return;
                    }
                    success = contractManager.failContract(contractId);
                    if (success) {
                        // Return remaining escrow to nation treasury
                        double escrowRefund = contract.getEscrowBalance();
                        if (escrowRefund > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                            contract.withdrawFromEscrow(escrowRefund);
                            IntegrationRegistry.depositToNation(nation.getName(), escrowRefund);
                        }

                        // Forfeit contractor's bond to nation treasury
                        if (contract.getBondAmount() > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                            IntegrationRegistry.depositToNation(nation.getName(), contract.getBondAmount());
                        }

                        resultMessage = "Contract " + contract.getContractNumber() + " marked as failed. " +
                            "Bond forfeited, remaining escrow returned to treasury.";

                        // Notify contractor
                        if (contract.getContractorId() != null) {
                            ServerPlayer contractor = player.getServer().getPlayerList().getPlayer(contract.getContractorId());
                            if (contractor != null) {
                                contractor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§c[Contracts] §fContract " + contract.getContractNumber() +
                                    " has been marked as failed. Your bond has been forfeited."));
                            }
                        }
                    } else {
                        resultMessage = "Failed to mark contract as failed!";
                    }
                    break;

                default:
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Unknown action!"), player);
                    return;
            }

            // Mark for save
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(success, resultMessage), player);

            // Sync contracts to the player
            syncContractsToPlayer(player, nation);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Helper method to sync contracts to a player
     */
    private static void syncContractsToPlayer(ServerPlayer player, Nation nation) {
        ContractManager contractManager = ContractManager.getInstance();
        List<Contract> contracts = contractManager.getNationContracts(nation.getId());

        // Check player permissions
        boolean isLeader = player.getUUID().equals(nation.getLeaderId());
        boolean isLegislator = nation.isOfficer(player.getUUID());

        // Get nation treasury balance
        double treasuryBalance = IntegrationRegistry.hasEconomyIntegration() ?
            IntegrationRegistry.getNationBalance(nation.getName()) : 0;

        // Categorize contracts
        List<SyncContractsPacket.ContractSummary> openBidding = new ArrayList<>();
        List<SyncContractsPacket.ContractSummary> pendingApproval = new ArrayList<>();
        List<SyncContractsPacket.ContractSummary> activeContracts = new ArrayList<>();
        List<SyncContractsPacket.ContractSummary> history = new ArrayList<>();
        List<SyncContractsPacket.ContractSummary> myContracts = new ArrayList<>();

        for (Contract contract : contracts) {
            // Build bid summaries
            List<SyncContractsPacket.BidSummary> bidSummaries = new ArrayList<>();
            boolean playerHasBid = false;
            for (ContractBid bid : contract.getBids()) {
                if (bid.getBidderId().equals(player.getUUID())) {
                    playerHasBid = true;
                }
                bidSummaries.add(new SyncContractsPacket.BidSummary(
                    bid.getBidId().toString(),
                    bid.getBidderName(),
                    bid.getBidAmount(),
                    bid.getProposedDurationDays(),
                    bid.getProposal(),
                    bid.isBondPaid()
                ));
            }

            // Calculate time remaining
            long timeRemaining = 0;
            if (contract.getStatus() == Contract.Status.BIDDING) {
                timeRemaining = contract.getBiddingEndTime() - System.currentTimeMillis();
            } else if (contract.getStatus() == Contract.Status.ACTIVE) {
                timeRemaining = contract.getDeadline() - System.currentTimeMillis();
            }

            // Check if current player is the contractor
            boolean isPlayerContractor = player.getUUID().equals(contract.getContractorId());

            // Get milestone completion status
            java.util.Map<Integer, Boolean> milestonesCompleted = new java.util.HashMap<>(contract.getMilestonesCompleted());

            // Get pending milestone approval requests
            java.util.Set<Integer> pendingMilestoneApprovals = new java.util.HashSet<>(contract.getMilestoneApprovalRequests().keySet());

            // Get chunk coordinates
            java.util.List<int[]> chunkCoordinates = new java.util.ArrayList<>();
            for (net.minecraft.world.level.ChunkPos chunk : contract.getDesignatedChunks()) {
                chunkCoordinates.add(new int[]{chunk.x, chunk.z});
            }

            SyncContractsPacket.ContractSummary summary = new SyncContractsPacket.ContractSummary(
                contract.getContractId().toString(),
                contract.getContractNumber(),
                contract.getTitle(),
                contract.getDescription() != null ? contract.getDescription() : "",
                contract.getCreatorName(),
                contract.getStatus().name(),
                contract.getCompensationType().name(),
                contract.getTotalBudget(),
                contract.getBondAmount(),
                contract.getDesignatedChunks().size(),
                contract.getBids().size(),
                timeRemaining,
                contract.getContractorName() != null ? contract.getContractorName() : "",
                contract.getProgressPercent(),
                playerHasBid,
                isPlayerContractor,
                bidSummaries,
                milestonesCompleted,
                pendingMilestoneApprovals,
                chunkCoordinates,
                contract.getDimension()
            );

            // Categorize by status
            switch (contract.getStatus()) {
                case BIDDING:
                    openBidding.add(summary);
                    break;
                case PENDING_APPROVAL:
                    pendingApproval.add(summary);
                    break;
                case ACTIVE:
                    activeContracts.add(summary);
                    break;
                case COMPLETED:
                case CANCELLED:
                case FAILED:
                    history.add(summary);
                    break;
                case DRAFT:
                    // Only show drafts to legislators
                    if (isLeader || isLegislator) {
                        pendingApproval.add(summary);
                    }
                    break;
            }

            // Add to my contracts if player is the contractor
            if (player.getUUID().equals(contract.getContractorId())) {
                myContracts.add(summary);
            }
        }

        NetworkHandler.sendToPlayer(new SyncContractsPacket(
            nation.getName(),
            isLegislator,
            isLeader,
            openBidding,
            pendingApproval,
            activeContracts,
            history,
            myContracts,
            treasuryBalance
        ), player);
    }

    // ==================== Election Handlers ====================

    public static void handleRequestElectionData(RequestElectionDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            if (nation == null) {
                // Send empty election data
                NetworkHandler.sendToPlayer(new SyncElectionDataPacket(
                    false, "", Election.Status.COMPLETED, 0,
                    new ArrayList<>(), 0, false, 0, new ArrayList<>(), "", new HashMap<>()
                ), player);
                return;
            }

            ElectionManager electionManager = ElectionManager.getInstance();
            Election election = electionManager.getActiveElection(nation.getId());

            boolean hasActiveElection = election != null && election.getStatus() == Election.Status.ACTIVE;
            List<SyncElectionDataPacket.CandidateInfo> candidates = new ArrayList<>();
            int totalVotes = 0;
            boolean hasVoted = false;
            String winnerName = "";
            Map<UUID, Integer> results = new HashMap<>();

            if (election != null) {
                UUID incumbentId = election.getIncumbentId();
                for (Map.Entry<UUID, String> entry : election.getCandidates().entrySet()) {
                    candidates.add(new SyncElectionDataPacket.CandidateInfo(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getKey().equals(incumbentId)
                    ));
                }
                totalVotes = election.getVoteCount();
                hasVoted = election.hasVoted(player.getUUID());

                if (election.getStatus() == Election.Status.COMPLETED) {
                    winnerName = election.getWinnerName();
                    results = election.getResults();
                }
            }

            // Get next election time
            Long nextTime = electionManager.getNextElectionTime(nation.getId());
            long nextElectionTime = nextTime != null ? nextTime : 0;

            // Get history
            List<SyncElectionDataPacket.HistoryEntry> history = new ArrayList<>();
            for (ElectionResult result : electionManager.getElectionHistory(nation.getId())) {
                history.add(new SyncElectionDataPacket.HistoryEntry(
                    result.winnerName(),
                    result.voteCount(),
                    result.totalVoters(),
                    result.timestamp()
                ));
            }

            NetworkHandler.sendToPlayer(new SyncElectionDataPacket(
                hasActiveElection,
                nation.getName(),
                election != null ? election.getStatus() : Election.Status.COMPLETED,
                election != null ? election.getEndTime() : 0,
                candidates,
                totalVotes,
                hasVoted,
                nextElectionTime,
                history,
                winnerName,
                results
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCastVote(CastVotePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You are not in a nation!"), player);
                return;
            }

            String error = ElectionManager.getInstance().castVote(nation, player.getUUID(), packet.getCandidateId());
            if (error != null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, error), player);
            } else {
                if (player.level() instanceof ServerLevel level) {
                    NationSavedData.get(level).markForSave();
                }
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Vote cast successfully!"), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRegisterCandidate(RegisterCandidatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getPlayerNation(player.getUUID());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You are not in a nation!"), player);
                return;
            }

            String error = ElectionManager.getInstance().registerCandidate(
                nation, player.getUUID(), player.getName().getString(), player.server);

            if (error != null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, error), player);
            } else {
                if (player.level() instanceof ServerLevel level) {
                    NationSavedData.get(level).markForSave();
                }
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "You are now a candidate!"), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestLegislatureData(RequestLegislatureDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                // Send empty data
                NetworkHandler.sendToPlayer(new SyncLegislatureDataPacket(
                    packet.getNationName(), false, false,
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0
                ), player);
                return;
            }

            com.statecraft.legislature.LegislatureManager legManager = com.statecraft.legislature.LegislatureManager.getInstance();
            com.statecraft.legislature.Legislature legislature = legManager.getOrCreateLegislature(nation.getId());

            // Check if player is a legislature member (governor or officer, but not leader)
            Set<UUID> votingMembers = legislature.getVotingMembers(nation);
            boolean isLegislatureMember = votingMembers.contains(player.getUUID());
            boolean isNationLeader = player.getUUID().equals(nation.getLeaderId());

            // Build active bills list
            List<SyncLegislatureDataPacket.BillSummary> activeBills = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (com.statecraft.legislature.Bill bill : legislature.getActiveBills()) {
                long timeRemaining = 0;
                if (bill.getStatus() == com.statecraft.legislature.Bill.Status.DEBATE) {
                    timeRemaining = Math.max(0, bill.getDebateEndTime() - now);
                } else if (bill.getStatus() == com.statecraft.legislature.Bill.Status.VOTING) {
                    timeRemaining = Math.max(0, bill.getVoteEndTime() - now);
                }

                boolean playerHasVoted = bill.getVotes().containsKey(player.getUUID());
                boolean needsLeaderAction = bill.getStatus() == com.statecraft.legislature.Bill.Status.PASSED && !bill.isVetoProof();

                // Count votes dynamically from the votes map
                int yesCount = 0, noCount = 0;
                for (com.statecraft.legislature.Bill.Vote v : bill.getVotes().values()) {
                    if (v == com.statecraft.legislature.Bill.Vote.YES) yesCount++;
                    else if (v == com.statecraft.legislature.Bill.Vote.NO) noCount++;
                }

                activeBills.add(new SyncLegislatureDataPacket.BillSummary(
                    bill.getBillId().toString(),
                    bill.getBillNumber(),
                    bill.getTitle(),
                    bill.getAuthorName(),
                    bill.getStatus().name(),
                    yesCount,
                    noCount,
                    timeRemaining,
                    playerHasVoted,
                    needsLeaderAction
                ));
            }

            // Build history list (most recent 10)
            List<SyncLegislatureDataPacket.BillSummary> recentHistory = new ArrayList<>();
            List<com.statecraft.legislature.Bill> history = legislature.getBillHistory();
            for (int i = 0; i < Math.min(10, history.size()); i++) {
                com.statecraft.legislature.Bill bill = history.get(i);

                // Convert policy changes to string map
                java.util.Map<String, String> policyChangesMap = new java.util.HashMap<>();
                for (java.util.Map.Entry<com.statecraft.legislature.PolicyType, String> entry : bill.getPolicyChanges().entrySet()) {
                    policyChangesMap.put(entry.getKey().getDisplayName(), entry.getValue());
                }
                // Get full text from custom law entries
                String fullText = "";
                for (java.util.Map.Entry<com.statecraft.legislature.PolicyType, String> entry : bill.getPolicyChanges().entrySet()) {
                    if (entry.getKey().getCategory() == com.statecraft.legislature.PolicyType.Category.CUSTOM) {
                        fullText = entry.getValue();
                        break;
                    }
                }
                recentHistory.add(new SyncLegislatureDataPacket.BillSummary(
                    bill.getBillId().toString(),
                    bill.getBillNumber(),
                    bill.getTitle(),
                    bill.getDescription(),
                    bill.getAuthorName(),
                    bill.getStatus().name(),
                    bill.getYesVotes(),
                    bill.getNoVotes(),
                    0, // abstainVotes
                    0L, // timeRemaining
                    bill.getEnactedTime() > 0 ? bill.getEnactedTime() : bill.getVoteEndTime(), // enactedTime
                    false, // playerHasVoted
                    false, // needsLeaderAction
                    bill.isVetoProof(),
                    bill.isConstitutionalAmendment(),
                    policyChangesMap,
                    fullText
                ));
            }

            // Build voting members list
            List<String> votingMemberNames = new ArrayList<>();
            for (UUID memberId : votingMembers) {
                String memberName = player.getServer().getProfileCache()
                    .get(memberId)
                    .map(profile -> profile.getName())
                    .orElse("Unknown");
                votingMemberNames.add(memberName);
            }

            NetworkHandler.sendToPlayer(new SyncLegislatureDataPacket(
                nation.getName(),
                isLegislatureMember,
                isNationLeader,
                activeBills,
                recentHistory,
                votingMemberNames,
                votingMembers.size()
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestOfficerManagementData(RequestOfficerManagementDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new SyncOfficerManagementDataPacket(
                    packet.getNationName(), false, new ArrayList<>(), new ArrayList<>()
                ), player);
                return;
            }

            boolean isLeader = player.getUUID().equals(nation.getLeaderId());

            // Build list of current officers
            List<SyncOfficerManagementDataPacket.OfficerInfo> officers = new ArrayList<>();
            for (UUID officerId : nation.getOfficers()) {
                String officerName = player.getServer().getProfileCache()
                    .get(officerId)
                    .map(profile -> profile.getName())
                    .orElse("Unknown");
                boolean isOnline = player.getServer().getPlayerList().getPlayer(officerId) != null;
                officers.add(new SyncOfficerManagementDataPacket.OfficerInfo(officerId, officerName, isOnline));
            }

            // Build list of citizens who can be appointed (all nation members except leader and current officers)
            List<SyncOfficerManagementDataPacket.CitizenInfo> citizens = new ArrayList<>();
            Set<UUID> allMembers = nation.getAllMembers();
            Set<UUID> currentOfficers = nation.getOfficers();

            // Check which members are governors
            Set<UUID> governors = new HashSet<>();
            for (State state : nation.getAllStates()) {
                governors.add(state.getGovernorId());
            }

            for (UUID memberId : allMembers) {
                // Skip leader - they can't be an officer
                if (memberId.equals(nation.getLeaderId())) continue;
                // Skip current officers (they're shown separately)
                if (currentOfficers.contains(memberId)) continue;

                String memberName = player.getServer().getProfileCache()
                    .get(memberId)
                    .map(profile -> profile.getName())
                    .orElse("Unknown");
                boolean isOnline = player.getServer().getPlayerList().getPlayer(memberId) != null;
                boolean isGovernor = governors.contains(memberId);

                citizens.add(new SyncOfficerManagementDataPacket.CitizenInfo(memberId, memberName, isGovernor, isOnline));
            }

            // Sort citizens: online first, then by name
            citizens.sort((a, b) -> {
                if (a.isOnline() != b.isOnline()) return a.isOnline() ? -1 : 1;
                return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
            });

            NetworkHandler.sendToPlayer(new SyncOfficerManagementDataPacket(
                nation.getName(), isLeader, citizens, officers
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleModifyOfficer(ModifyOfficerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            // Only the nation leader can modify officers
            if (!player.getUUID().equals(nation.getLeaderId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only the nation leader can manage officers!"), player);
                return;
            }

            UUID targetId = packet.getTargetPlayerId();

            if (packet.getAction() == ModifyOfficerPacket.Action.APPOINT) {
                // Validate: check nation's max officers limit (constitutional setting)
                if (!nation.canAddOfficer()) {
                    int maxOfficers = nation.getMaxOfficers();
                    if (maxOfficers == 0) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "This nation's constitution does not allow officers!"), player);
                    } else {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Maximum of " + maxOfficers + " officers allowed!"), player);
                    }
                    return;
                }

                // Validate: must be a nation member
                if (!nation.getAllMembers().contains(targetId)) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Player is not a member of this nation!"), player);
                    return;
                }

                // Validate: can't appoint the leader
                if (targetId.equals(nation.getLeaderId())) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Cannot appoint the leader as an officer!"), player);
                    return;
                }

                // Validate: not already an officer
                if (nation.isOfficer(targetId)) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Player is already an officer!"), player);
                    return;
                }

                // Appoint the officer
                nation.addOfficer(targetId);

                // Save data
                if (player.level() instanceof ServerLevel level) {
                    NationSavedData.get(level).markForSave();
                }

                String targetName = player.getServer().getProfileCache()
                    .get(targetId)
                    .map(profile -> profile.getName())
                    .orElse("Unknown");

                NetworkHandler.sendToPlayer(new ActionResultPacket(true, targetName + " has been appointed as an officer!"), player);

                // Notify the appointed player if online
                ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayer(targetId);
                if (targetPlayer != null) {
                    targetPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a[" + nation.getName() + "] §eYou have been appointed as a nation officer!"));
                }

            } else if (packet.getAction() == ModifyOfficerPacket.Action.REMOVE) {
                // Validate: must be an officer
                if (!nation.isOfficer(targetId)) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Player is not an officer!"), player);
                    return;
                }

                // Remove the officer
                nation.removeOfficer(targetId);

                // Save data
                if (player.level() instanceof ServerLevel level) {
                    NationSavedData.get(level).markForSave();
                }

                String targetName = player.getServer().getProfileCache()
                    .get(targetId)
                    .map(profile -> profile.getName())
                    .orElse("Unknown");

                NetworkHandler.sendToPlayer(new ActionResultPacket(true, targetName + " has been removed as an officer."), player);

                // Notify the removed player if online
                ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayer(targetId);
                if (targetPlayer != null) {
                    targetPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[" + nation.getName() + "] §eYou have been removed as a nation officer."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleProposeBill(ProposeBillPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            com.statecraft.legislature.LegislatureManager legManager = com.statecraft.legislature.LegislatureManager.getInstance();
            com.statecraft.legislature.Legislature legislature = legManager.getOrCreateLegislature(nation.getId());

            // Check if player can propose bills (must be a voting member or leader)
            if (!legislature.canProposeBill(nation, player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "You must be the leader, a governor, or an officer to propose legislation!"), player);
                return;
            }

            // Validate bill data
            String title = packet.getTitle().trim();
            String description = packet.getDescription().trim();

            if (title.length() < 5 || title.length() > 64) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Title must be 5-64 characters!"), player);
                return;
            }

            if (packet.getPolicyChanges().isEmpty()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Bill must have at least one policy change!"), player);
                return;
            }

            // Check if any policy changes require constitutional amendment
            boolean requiresConstitutionalAmendment = false;
            boolean containsImpeachment = false;
            boolean containsRatification = false;
            boolean containsOverride = false;
            for (String policyName : packet.getPolicyChanges().keySet()) {
                try {
                    com.statecraft.legislature.PolicyType policyType =
                        com.statecraft.legislature.PolicyType.valueOf(policyName);
                    if (policyType.requiresConstitutionalAmendment()) {
                        requiresConstitutionalAmendment = true;
                    }
                    if (policyType == com.statecraft.legislature.PolicyType.IMPEACH_LEADER) {
                        containsImpeachment = true;
                    }
                    if (policyType == com.statecraft.legislature.PolicyType.RATIFY_EMERGENCY_POWER) {
                        containsRatification = true;
                    }
                    if (policyType == com.statecraft.legislature.PolicyType.OVERRIDE_EMERGENCY_POWER) {
                        containsOverride = true;
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            // RATIFY bills are auto-created by the system — players cannot propose them
            if (containsRatification) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Ratification bills are automatically created when emergency powers are invoked. You cannot propose one manually."), player);
                return;
            }

            // The leader cannot propose their own impeachment
            if (containsImpeachment && player.getUUID().equals(nation.getLeaderId())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "The nation leader cannot propose an impeachment bill!"), player);
                return;
            }

            // Impeachment must be the only policy change in the bill
            if (containsImpeachment && packet.getPolicyChanges().size() > 1) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Impeachment must be the sole item in a bill — it cannot be combined with other policy changes."), player);
                return;
            }

            // Override must be the only policy change and requires an active emergency power
            if (containsOverride) {
                if (packet.getPolicyChanges().size() > 1) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Emergency power override must be the sole item in a bill."), player);
                    return;
                }
                // Validate the target power is actually active
                String powerName = packet.getPolicyChanges().get(
                    com.statecraft.legislature.PolicyType.OVERRIDE_EMERGENCY_POWER.name());
                if (powerName != null) {
                    try {
                        com.statecraft.legislature.EmergencyPower targetPower =
                            com.statecraft.legislature.EmergencyPower.valueOf(powerName);
                        if (!legislature.isEmergencyPowerActive(targetPower)) {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                targetPower.getDisplayName() + " is not currently active."), player);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                            "Invalid emergency power specified."), player);
                        return;
                    }
                }
            }

            // Create the draft bill (type depends on policies)
            String playerName = player.getName().getString();
            com.statecraft.legislature.Bill bill;
            if (containsOverride) {
                // Override bills use EMERGENCY_RATIFICATION type (simple majority, bypasses leader)
                String billNumber = legislature.generateBillNumber();
                bill = new com.statecraft.legislature.Bill(nation.getId(), billNumber, title, description,
                    player.getUUID(), playerName, com.statecraft.legislature.Bill.BillType.EMERGENCY_RATIFICATION);
                // Add to draft bills so submitForDebate can find it
                legislature.addDraftBill(bill);
            } else if (requiresConstitutionalAmendment) {
                bill = legislature.createConstitutionalAmendment(
                    player.getUUID(), playerName, title, description);
            } else {
                bill = legislature.createDraft(
                    player.getUUID(), playerName, title, description);
            }

            // Add policy changes
            for (Map.Entry<String, String> entry : packet.getPolicyChanges().entrySet()) {
                try {
                    com.statecraft.legislature.PolicyType policyType =
                        com.statecraft.legislature.PolicyType.valueOf(entry.getKey());

                    if (policyType.isValidValue(entry.getValue())) {
                        bill.addPolicyChange(policyType, entry.getValue());
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid policy type, skip
                }
            }

            if (bill.getPolicyChanges().isEmpty()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "No valid policy changes!"), player);
                return;
            }

            // Submit for debate (default 24 hours debate period)
            long debateDuration = com.statecraft.config.StateCraftConfig.LEGISLATURE_DEBATE_HOURS.get() * 60 * 60 * 1000L;
            boolean submitted = legislature.submitForDebate(bill.getBillId(), debateDuration);

            if (!submitted) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Failed to submit bill!"), player);
                return;
            }

            // Save data
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            // Notify legislature members
            String billTypeStr = bill.isConstitutionalAmendment() ? "§c[Constitutional Amendment] " : "";
            String message = "§6[Legislature] " + billTypeStr + "§e" + playerName + " proposed: §f" + title;
            if (bill.isConstitutionalAmendment()) {
                message += " §7(Requires 2/3 majority, cannot be vetoed)";
            }
            Set<UUID> votingMembers = legislature.getVotingMembers(nation);
            for (UUID memberId : votingMembers) {
                ServerPlayer memberPlayer = player.getServer().getPlayerList().getPlayer(memberId);
                if (memberPlayer != null) {
                    memberPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                }
            }

            // Also notify the leader
            ServerPlayer leader = player.getServer().getPlayerList().getPlayer(nation.getLeaderId());
            if (leader != null && !votingMembers.contains(nation.getLeaderId())) {
                leader.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            }

            String successMsg = bill.isConstitutionalAmendment() ?
                "Constitutional Amendment '" + title + "' submitted for debate! (" + bill.getBillNumber() + ") - Requires 2/3 majority" :
                "Bill '" + title + "' submitted for debate! (" + bill.getBillNumber() + ")";
            NetworkHandler.sendToPlayer(new ActionResultPacket(true, successMsg), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleVoteBill(VoteBillPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            com.statecraft.legislature.LegislatureManager legManager = com.statecraft.legislature.LegislatureManager.getInstance();
            com.statecraft.legislature.Legislature legislature = legManager.getOrCreateLegislature(nation.getId());

            // Check if player is a voting member
            Set<UUID> votingMembers = legislature.getVotingMembers(nation);
            if (!votingMembers.contains(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "You are not a voting member of the legislature!"), player);
                return;
            }

            // Find the bill
            UUID billUUID;
            try {
                billUUID = UUID.fromString(packet.getBillId());
            } catch (IllegalArgumentException e) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Invalid bill ID!"), player);
                return;
            }

            com.statecraft.legislature.Bill bill = legislature.getActiveBill(billUUID);
            if (bill == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Bill not found!"), player);
                return;
            }

            // Check if bill is in voting phase
            if (bill.getStatus() != com.statecraft.legislature.Bill.Status.VOTING) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Bill is not in voting phase!"), player);
                return;
            }

            // Check if already voted
            if (bill.getVotes().containsKey(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You have already voted on this bill!"), player);
                return;
            }

            // Cast vote
            com.statecraft.legislature.Bill.Vote vote = switch (packet.getVoteType()) {
                case YES -> com.statecraft.legislature.Bill.Vote.YES;
                case NO -> com.statecraft.legislature.Bill.Vote.NO;
                case ABSTAIN -> com.statecraft.legislature.Bill.Vote.ABSTAIN;
            };

            boolean success = bill.castVote(player.getUUID(), vote);
            if (!success) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Failed to cast vote!"), player);
                return;
            }

            // Verify the vote was recorded
            boolean voteRecorded = bill.getVotes().containsKey(player.getUUID());
            StateCraft.LOGGER.debug("Vote recorded for player {}: {} (verified in map: {})",
                player.getName().getString(), vote, voteRecorded);

            // Save data
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            String voteText = switch (packet.getVoteType()) {
                case YES -> "§aYES";
                case NO -> "§cNO";
                case ABSTAIN -> "§7ABSTAIN";
            };

            NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                "Vote recorded: " + voteText + " §fon " + bill.getBillNumber()), player);

            // Send updated legislature data to the player so GUI reflects the vote
            sendLegislatureDataToPlayer(player, nation, legislature, votingMembers);

            // Notify other legislature members about the vote
            String message = "§6[Legislature] §7" + player.getName().getString() + " voted on " + bill.getBillNumber();
            for (UUID memberId : votingMembers) {
                if (!memberId.equals(player.getUUID())) {
                    ServerPlayer memberPlayer = player.getServer().getPlayerList().getPlayer(memberId);
                    if (memberPlayer != null) {
                        memberPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Helper method to send legislature data to a player
     */
    private static void sendLegislatureDataToPlayer(ServerPlayer player, Nation nation,
            com.statecraft.legislature.Legislature legislature, Set<UUID> votingMembers) {

        boolean isLegislatureMember = votingMembers.contains(player.getUUID());
        boolean isNationLeader = player.getUUID().equals(nation.getLeaderId());

        // Build active bills list
        List<SyncLegislatureDataPacket.BillSummary> activeBills = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (com.statecraft.legislature.Bill bill : legislature.getActiveBills()) {
            long timeRemaining = 0;
            if (bill.getStatus() == com.statecraft.legislature.Bill.Status.DEBATE) {
                timeRemaining = Math.max(0, bill.getDebateEndTime() - now);
            } else if (bill.getStatus() == com.statecraft.legislature.Bill.Status.VOTING) {
                timeRemaining = Math.max(0, bill.getVoteEndTime() - now);
            }

            boolean playerHasVoted = bill.getVotes().containsKey(player.getUUID());
            boolean needsLeaderAction = bill.getStatus() == com.statecraft.legislature.Bill.Status.PASSED && !bill.isVetoProof();

            // Count votes dynamically from the votes map (not the yesVotes/noVotes fields which are only set during tally)
            int yesCount = 0, noCount = 0;
            for (com.statecraft.legislature.Bill.Vote v : bill.getVotes().values()) {
                if (v == com.statecraft.legislature.Bill.Vote.YES) yesCount++;
                else if (v == com.statecraft.legislature.Bill.Vote.NO) noCount++;
            }

            StateCraft.LOGGER.debug("Syncing bill {} to player {}: status={}, playerHasVoted={}, yesVotes={}, noVotes={}",
                bill.getBillNumber(), player.getName().getString(), bill.getStatus(),
                playerHasVoted, yesCount, noCount);

            activeBills.add(new SyncLegislatureDataPacket.BillSummary(
                bill.getBillId().toString(),
                bill.getBillNumber(),
                bill.getTitle(),
                bill.getAuthorName(),
                bill.getStatus().name(),
                yesCount,
                noCount,
                timeRemaining,
                playerHasVoted,
                needsLeaderAction
            ));
        }

        // Build history list (most recent 10)
        List<SyncLegislatureDataPacket.BillSummary> recentHistory = new ArrayList<>();
        List<com.statecraft.legislature.Bill> history = legislature.getBillHistory();
        for (int i = 0; i < Math.min(10, history.size()); i++) {
            com.statecraft.legislature.Bill historyBill = history.get(i);

            // Convert policy changes to string map
            java.util.Map<String, String> histPolicyChanges = new java.util.HashMap<>();
            for (java.util.Map.Entry<com.statecraft.legislature.PolicyType, String> entry : historyBill.getPolicyChanges().entrySet()) {
                histPolicyChanges.put(entry.getKey().getDisplayName(), entry.getValue());
            }

            // Get full text if this is a custom law
            String histFullText = "";
            for (java.util.Map.Entry<com.statecraft.legislature.PolicyType, String> entry : historyBill.getPolicyChanges().entrySet()) {
                if (entry.getKey() == com.statecraft.legislature.PolicyType.CUSTOM_LAW) {
                    histFullText = entry.getValue();
                    break;
                }
            }

            recentHistory.add(new SyncLegislatureDataPacket.BillSummary(
                historyBill.getBillId().toString(),
                historyBill.getBillNumber(),
                historyBill.getTitle(),
                historyBill.getDescription(),
                historyBill.getAuthorName(),
                historyBill.getStatus().name(),
                historyBill.getYesVotes(),
                historyBill.getNoVotes(),
                0, // abstainVotes
                0L, // timeRemaining
                historyBill.getEnactedTime() > 0 ? historyBill.getEnactedTime() : historyBill.getVoteEndTime(), // enactedTime
                false, // playerHasVoted
                false, // needsLeaderAction
                historyBill.isVetoProof(),
                historyBill.isConstitutionalAmendment(),
                histPolicyChanges,
                histFullText
            ));
        }

        // Build voting members list
        List<String> votingMemberNames = new ArrayList<>();
        for (UUID memberId : votingMembers) {
            String memberName = player.getServer().getProfileCache()
                .get(memberId)
                .map(profile -> profile.getName())
                .orElse("Unknown");
            votingMemberNames.add(memberName);
        }

        NetworkHandler.sendToPlayer(new SyncLegislatureDataPacket(
            nation.getName(),
            isLegislatureMember,
            isNationLeader,
            activeBills,
            recentHistory,
            votingMemberNames,
            votingMembers.size()
        ), player);
    }

    public static void handleLeaderBillAction(LeaderBillActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            // Check if player is the nation leader
            if (!nation.getLeaderId().equals(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Only the nation leader can sign or veto bills!"), player);
                return;
            }

            com.statecraft.legislature.LegislatureManager legManager = com.statecraft.legislature.LegislatureManager.getInstance();
            com.statecraft.legislature.Legislature legislature = legManager.getOrCreateLegislature(nation.getId());

            // Find the bill
            UUID billUUID;
            try {
                billUUID = UUID.fromString(packet.getBillId());
            } catch (IllegalArgumentException e) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Invalid bill ID!"), player);
                return;
            }

            com.statecraft.legislature.Bill bill = legislature.getActiveBill(billUUID);
            if (bill == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Bill not found!"), player);
                return;
            }

            // Check if bill is in PASSED status
            if (bill.getStatus() != com.statecraft.legislature.Bill.Status.PASSED) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                    "Bill is not awaiting leader action!"), player);
                return;
            }

            String resultMessage;
            String broadcastMessage;

            if (packet.getActionType() == LeaderBillActionPacket.ActionType.SIGN) {
                // Sign the bill into law
                bill.enact();

                // Apply policy changes through the LegislatureManager
                legManager.applyPolicyChanges(nation, bill.getPolicyChanges());

                // Create a law from this bill with proper constructor
                String lawNumber = legislature.getCodex().generateLawNumber(com.statecraft.legislature.Law.Type.NATION_LAW);
                com.statecraft.legislature.Law law = new com.statecraft.legislature.Law(
                    nation.getId(),
                    lawNumber,
                    com.statecraft.legislature.Law.Type.NATION_LAW,
                    bill.getTitle(),
                    bill.getDescription(),
                    bill.getAuthorName(),
                    bill.getPolicyChanges(),
                    bill.getYesVotes(),
                    bill.getNoVotes(),
                    bill.getAbstainVotes(),
                    bill.isVetoProof(),
                    null  // Full text for custom laws
                );
                legislature.getCodex().addLaw(law);

                resultMessage = "Bill " + bill.getBillNumber() + " signed into law!";
                broadcastMessage = "§6[Legislature] §a" + player.getName().getString() +
                    " signed " + bill.getBillNumber() + " into law: §f" + bill.getTitle();
            } else {
                // Veto the bill
                // Constitutional amendments cannot be vetoed
                if (bill.isConstitutionalAmendment()) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "Constitutional amendments cannot be vetoed!"), player);
                    return;
                }
                if (bill.isVetoProof()) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                        "This bill passed with a veto-proof majority and cannot be vetoed!"), player);
                    return;
                }

                bill.veto();
                resultMessage = "Bill " + bill.getBillNumber() + " vetoed.";
                broadcastMessage = "§6[Legislature] §c" + player.getName().getString() +
                    " vetoed " + bill.getBillNumber() + ": §f" + bill.getTitle();
            }

            // Archive the bill
            legislature.archiveBill(billUUID);

            // Save data
            if (player.level() instanceof ServerLevel level) {
                NationSavedData.get(level).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true, resultMessage), player);

            // Send updated legislature data to the player so GUI reflects the change
            Set<UUID> votingMembers = legislature.getVotingMembers(nation);
            sendLegislatureDataToPlayer(player, nation, legislature, votingMembers);

            // Notify all legislature members and the leader
            for (UUID memberId : votingMembers) {
                ServerPlayer memberPlayer = player.getServer().getPlayerList().getPlayer(memberId);
                if (memberPlayer != null) {
                    memberPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(broadcastMessage));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== Appointment Handling ====================

    public static void handleAppointLeader(AppointLeaderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());

            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Nation not found!"), player);
                return;
            }

            State state = nation.getStateByName(packet.getStateName());
            if (state == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "State not found!"), player);
                return;
            }

            // Resolve target player by name
            String targetName = packet.getTargetPlayer().trim();
            if (targetName.isEmpty()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Player name cannot be empty!"), player);
                return;
            }

            // Look up player UUID from profile cache
            var profileOpt = player.getServer().getProfileCache().get(targetName);
            if (profileOpt.isEmpty()) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Player '" + targetName + "' not found!"), player);
                return;
            }
            UUID targetId = profileOpt.get().getId();
            String resolvedName = profileOpt.get().getName();

            if (packet.getType() == AppointLeaderPacket.AppointmentType.GOVERNOR) {
                handleAppointGovernor(player, manager, nation, state, targetId, resolvedName);
            } else if (packet.getType() == AppointLeaderPacket.AppointmentType.MAYOR) {
                City city = state.getCityByName(packet.getCityName());
                if (city == null) {
                    NetworkHandler.sendToPlayer(new ActionResultPacket(false, "City not found!"), player);
                    return;
                }
                handleAppointMayor(player, manager, nation, state, city, targetId, resolvedName);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleAppointGovernor(ServerPlayer player, ChunkClaimManager manager,
                                               Nation nation, State state, UUID targetId, String targetName) {
        // Only the nation leader can appoint governors
        if (!player.getUUID().equals(nation.getLeaderId())) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only the nation leader can appoint governors!"), player);
            return;
        }

        // Target must be a member of the nation
        if (!nation.getAllMembers().contains(targetId)) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, targetName + " is not a member of this nation!"), player);
            return;
        }

        // Can't appoint yourself if you're already governor
        if (targetId.equals(state.getGovernorId())) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, targetName + " is already the governor of this state!"), player);
            return;
        }

        // Check if target is already governor of another state
        if (manager.isGovernorOfAnyState(targetId)) {
            String existingState = manager.getGovernorStateName(targetId);
            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                targetName + " is already the governor of " + (existingState != null ? existingState : "another state") +
                "! A player can only govern one state at a time."), player);
            return;
        }

        // Perform the appointment
        UUID oldGovernorId = state.getGovernorId();
        state.setGovernorId(targetId);

        // Make new governor a citizen of the state if not already
        if (!state.isCitizen(targetId)) {
            state.addCitizen(targetId);
        }

        // Save data
        if (player.level() instanceof ServerLevel level) {
            NationSavedData.get(level).markForSave();
        }

        NetworkHandler.sendToPlayer(new ActionResultPacket(true,
            targetName + " has been appointed as governor of " + state.getName() + "!"), player);

        // Notify the new governor if online
        ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayer(targetId);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6[" + nation.getName() + "] §eYou have been appointed as governor of §f" + state.getName() + "§e!"));
        }

        // Notify the old governor if online and different from new
        if (!oldGovernorId.equals(targetId)) {
            ServerPlayer oldGovernor = player.getServer().getPlayerList().getPlayer(oldGovernorId);
            if (oldGovernor != null) {
                oldGovernor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6[" + nation.getName() + "] §eYou are no longer governor of §f" + state.getName() +
                    "§e. §f" + targetName + "§e has been appointed as the new governor."));
            }
        }
    }

    private static void handleAppointMayor(ServerPlayer player, ChunkClaimManager manager,
                                            Nation nation, State state, City city, UUID targetId, String targetName) {
        // Only the state governor or nation leader can appoint mayors
        boolean isGovernor = player.getUUID().equals(state.getGovernorId());
        boolean isNationLeader = player.getUUID().equals(nation.getLeaderId());

        if (!isGovernor && !isNationLeader) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                "Only the state governor or nation leader can appoint mayors!"), player);
            return;
        }

        // Target must be a member of the nation
        if (!nation.getAllMembers().contains(targetId)) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, targetName + " is not a member of this nation!"), player);
            return;
        }

        // Can't appoint if already mayor of this city
        if (targetId.equals(city.getMayorId())) {
            NetworkHandler.sendToPlayer(new ActionResultPacket(false, targetName + " is already the mayor of this city!"), player);
            return;
        }

        // Check if target is already mayor of another city
        if (manager.isMayorOfAnyCity(targetId)) {
            String existingCity = manager.getMayorCityName(targetId);
            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                targetName + " is already the mayor of " + (existingCity != null ? existingCity : "another city") +
                "! A player can only be mayor of one city at a time."), player);
            return;
        }

        // Perform the appointment
        UUID oldMayorId = city.getMayorId();
        city.setMayorId(targetId);

        // Make new mayor a resident if not already
        if (!city.isResident(targetId)) {
            city.addResident(targetId);
        }

        // Also make them a citizen of the state if not already
        if (!state.isCitizen(targetId)) {
            state.addCitizen(targetId);
        }

        // Save data
        if (player.level() instanceof ServerLevel level) {
            NationSavedData.get(level).markForSave();
        }

        NetworkHandler.sendToPlayer(new ActionResultPacket(true,
            targetName + " has been appointed as mayor of " + city.getName() + "!"), player);

        // Notify the new mayor if online
        ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayer(targetId);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6[" + nation.getName() + "] §eYou have been appointed as mayor of §f" + city.getName() + "§e!"));
        }

        // Notify the old mayor if online and different from new
        if (!oldMayorId.equals(targetId)) {
            ServerPlayer oldMayor = player.getServer().getPlayerList().getPlayer(oldMayorId);
            if (oldMayor != null) {
                oldMayor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6[" + nation.getName() + "] §eYou are no longer mayor of §f" + city.getName() +
                    "§e. §f" + targetName + "§e has been appointed as the new mayor."));
            }
        }
    }

    // ==================== City Chunks Handling ====================

    public static void handleRequestCityChunks(RequestCityChunksPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());
            if (nation == null) {
                NetworkHandler.sendToPlayer(new SyncCityChunksPacket(packet.getCityName(), new ArrayList<>()), player);
                return;
            }

            State state = nation.getStateByName(packet.getStateName());
            if (state == null) {
                NetworkHandler.sendToPlayer(new SyncCityChunksPacket(packet.getCityName(), new ArrayList<>()), player);
                return;
            }

            City city = state.getCityByName(packet.getCityName());
            if (city == null) {
                NetworkHandler.sendToPlayer(new SyncCityChunksPacket(packet.getCityName(), new ArrayList<>()), player);
                return;
            }

            List<SyncCityChunksPacket.ChunkEntry> entries = new ArrayList<>();
            for (ClaimedChunk chunk : city.getAllChunks()) {
                String ownershipType = chunk.getOwnershipType().name();
                String ownerName;
                if (chunk.getOwnershipType() == OwnershipType.PLAYER && chunk.getPlayerOwner() != null) {
                    ownerName = getPlayerName(player.server, chunk.getPlayerOwner());
                } else if (chunk.getOwnershipType() == OwnershipType.COMPANY && chunk.getCompanyOwner() != null) {
                    com.statecraft.company.Company company =
                        com.statecraft.company.CompanyManager.getInstance().getCompany(chunk.getCompanyOwner());
                    ownerName = company != null ? company.getName() : "Unknown Co.";
                } else {
                    ownerName = city.getName();
                }

                String dimension = chunk.getDimension().location().toString();

                entries.add(new SyncCityChunksPacket.ChunkEntry(
                    chunk.getChunkPos().x,
                    chunk.getChunkPos().z,
                    dimension,
                    ownershipType,
                    ownerName,
                    chunk.isForSale(),
                    chunk.getSalePrice()
                ));
            }

            // Sort by coordinates for consistent display
            entries.sort((a, b) -> {
                int cmp = Integer.compare(a.x, b.x);
                return cmp != 0 ? cmp : Integer.compare(a.z, b.z);
            });

            NetworkHandler.sendToPlayer(new SyncCityChunksPacket(city.getName(), entries), player);
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== Eminent Domain / Nation Private Chunks ====================

    public static void handleRequestNationPrivateChunks(RequestNationPrivateChunksPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            Nation nation = manager.getNationByName(packet.getNationName());
            if (nation == null) {
                NetworkHandler.sendToPlayer(new SyncNationPrivateChunksPacket(new ArrayList<>()), player);
                return;
            }

            List<SyncNationPrivateChunksPacket.PrivateChunkEntry> entries = new ArrayList<>();

            for (State state : nation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    for (ClaimedChunk chunk : city.getAllChunks()) {
                        if (chunk.getOwnershipType() == OwnershipType.PLAYER && chunk.getPlayerOwner() != null) {
                            String ownerName = getPlayerName(player.server, chunk.getPlayerOwner());
                            String dimension = chunk.getDimension().location().toString();

                            // Get chunk valuation via economy integration
                            double valuation = 0;
                            String formattedValuation = "$0";
                            if (IntegrationRegistry.hasEconomyIntegration()) {
                                valuation = IntegrationRegistry.getChunkTotalValue(
                                    chunk.getChunkPos().x, chunk.getChunkPos().z, dimension);
                                formattedValuation = IntegrationRegistry.formatCurrency(valuation);
                            }

                            entries.add(new SyncNationPrivateChunksPacket.PrivateChunkEntry(
                                chunk.getChunkPos().x,
                                chunk.getChunkPos().z,
                                dimension,
                                ownerName,
                                city.getName(),
                                state.getName(),
                                formattedValuation,
                                valuation
                            ));
                        }
                    }
                }
            }

            // Sort by owner name, then coordinates
            entries.sort((a, b) -> {
                int cmp = a.ownerName.compareToIgnoreCase(b.ownerName);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a.chunkX, b.chunkX);
                return cmp != 0 ? cmp : Integer.compare(a.chunkZ, b.chunkZ);
            });

            NetworkHandler.sendToPlayer(new SyncNationPrivateChunksPacket(entries), player);
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== Emergency Power Handlers ====================

    public static void handleRequestEmergencyPowerData(RequestEmergencyPowerDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            sendEmergencyPowerData(player, packet.getNationName(), "");
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleInvokeEmergencyPower(InvokeEmergencyPowerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.statecraft.core.Nation nation = ChunkClaimManager.getInstance().getNationByName(packet.getNationName());
            if (nation == null) {
                sendEmergencyPowerData(player, packet.getNationName(), "§cNation not found.");
                return;
            }

            // Only the leader can invoke emergency powers
            if (!nation.getLeaderId().equals(player.getUUID())) {
                sendEmergencyPowerData(player, packet.getNationName(), "§cOnly the nation leader can use executive actions.");
                return;
            }

            com.statecraft.legislature.EmergencyPower power;
            try {
                power = com.statecraft.legislature.EmergencyPower.valueOf(packet.getPowerName());
            } catch (IllegalArgumentException e) {
                sendEmergencyPowerData(player, packet.getNationName(), "§cInvalid emergency power.");
                return;
            }

            String result;
            var manager = com.statecraft.legislature.EmergencyPowerManager.getInstance();
            if (packet.getAction() == InvokeEmergencyPowerPacket.Action.INVOKE) {
                result = manager.activatePower(nation, power, packet.getTargetValue(), player.server);
            } else {
                result = manager.revokePower(nation, power, player.server);
            }

            // Save state
            ServerLevel level = player.server.overworld();
            NationSavedData.get(level).markForSave();

            // Send updated data back
            sendEmergencyPowerData(player, packet.getNationName(), result);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendEmergencyPowerData(ServerPlayer player, String nationName, String resultMessage) {
        com.statecraft.core.Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            NetworkHandler.sendToPlayer(new SyncEmergencyPowerDataPacket(nationName, false, List.of(), "§cNation not found.", List.of()), player);
            return;
        }

        boolean isLeader = nation.getLeaderId().equals(player.getUUID());
        var legislature = com.statecraft.legislature.LegislatureManager.getInstance().getOrCreateLegislature(nation.getId());

        List<SyncEmergencyPowerDataPacket.PowerEntry> entries = new ArrayList<>();
        for (com.statecraft.legislature.EmergencyPower power : com.statecraft.legislature.EmergencyPower.values()) {
            SyncEmergencyPowerDataPacket.PowerStatus status;
            long remainingMs = 0;

            if (legislature.isEmergencyPowerActive(power)) {
                status = SyncEmergencyPowerDataPacket.PowerStatus.ACTIVE;
                // Get remaining time for duration-based powers
                var activePowers = legislature.getActiveEmergencyPowers();
                Long endTime = activePowers.get(power);
                if (endTime != null && endTime > 0) {
                    remainingMs = Math.max(0, endTime - System.currentTimeMillis());
                }
            } else if (legislature.isOnCooldown(power)) {
                status = power.isPermanent() ?
                    SyncEmergencyPowerDataPacket.PowerStatus.INSTANT_COOLDOWN :
                    SyncEmergencyPowerDataPacket.PowerStatus.COOLDOWN;
                remainingMs = legislature.getCooldownRemaining(power);
            } else {
                status = SyncEmergencyPowerDataPacket.PowerStatus.AVAILABLE;
            }

            boolean requiresTarget = (power == com.statecraft.legislature.EmergencyPower.DIPLOMATIC_CRISIS ||
                                       power == com.statecraft.legislature.EmergencyPower.SUCCESSION_CRISIS);

            entries.add(new SyncEmergencyPowerDataPacket.PowerEntry(
                power.name(),
                power.getDisplayName(),
                power.getDescription(),
                power.getDurationHours(),
                power.getCooldownDays(),
                status,
                remainingMs,
                requiresTarget
            ));
        }

        // Build history entries
        List<SyncEmergencyPowerDataPacket.HistoryEntry> historyEntries = new ArrayList<>();
        var historyEvents = com.statecraft.legislature.EmergencyPowerManager.getInstance().getHistory(nation.getId());
        for (var event : historyEvents) {
            String powerDisplayName;
            try {
                powerDisplayName = com.statecraft.legislature.EmergencyPower.valueOf(event.powerName).getDisplayName();
            } catch (IllegalArgumentException e) {
                powerDisplayName = event.powerName;
            }
            historyEntries.add(new SyncEmergencyPowerDataPacket.HistoryEntry(
                powerDisplayName,
                event.eventType.getDisplayName(),
                event.actorName,
                event.details,
                event.timestamp
            ));
        }

        NetworkHandler.sendToPlayer(new SyncEmergencyPowerDataPacket(nationName, isLeader, entries, resultMessage, historyEntries), player);
    }

    // ==================== Diplomacy Handlers ====================

    public static void handleRequestDiplomacyData(RequestDiplomacyDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            sendDiplomacyData(player, packet.getNationName(), "");
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleDiplomacyAction(DiplomacyActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Nation playerNation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (playerNation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "You are not in a nation!"), player);
                return;
            }

            // Leader-only check for all diplomatic actions
            if (!playerNation.getLeaderId().equals(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only the nation leader can perform diplomatic actions!"), player);
                return;
            }

            DiplomacyManager diplomacy = DiplomacyManager.getInstance();
            String result;

            switch (packet.getAction()) {
                case DECLARE_WAR: {
                    Nation target = ChunkClaimManager.getInstance().getNationByName(packet.getTargetNationName());
                    if (target == null) {
                        result = "§cNation not found: " + packet.getTargetNationName();
                    } else {
                        result = diplomacy.declareWar(playerNation, target, player.server, false);
                    }
                    break;
                }
                case PROPOSE_PEACE: {
                    Nation target = ChunkClaimManager.getInstance().getNationByName(packet.getTargetNationName());
                    if (target == null) {
                        result = "§cNation not found: " + packet.getTargetNationName();
                    } else {
                        result = diplomacy.proposePeace(playerNation, target, player.server);
                    }
                    break;
                }
                case PROPOSE_ALLIANCE: {
                    Nation target = ChunkClaimManager.getInstance().getNationByName(packet.getTargetNationName());
                    if (target == null) {
                        result = "§cNation not found: " + packet.getTargetNationName();
                    } else {
                        result = diplomacy.proposeAlliance(playerNation, target, player.server);
                    }
                    break;
                }
                case BREAK_ALLIANCE: {
                    Nation target = ChunkClaimManager.getInstance().getNationByName(packet.getTargetNationName());
                    if (target == null) {
                        result = "§cNation not found: " + packet.getTargetNationName();
                    } else {
                        result = diplomacy.breakAlliance(playerNation, target, player.server);
                    }
                    break;
                }
                case ACCEPT_PROPOSAL: {
                    try {
                        UUID proposalId = UUID.fromString(packet.getProposalId());
                        result = diplomacy.acceptProposal(proposalId, player.getUUID(), player.server);
                    } catch (IllegalArgumentException e) {
                        result = "§cInvalid proposal ID.";
                    }
                    break;
                }
                case REJECT_PROPOSAL: {
                    try {
                        UUID proposalId = UUID.fromString(packet.getProposalId());
                        result = diplomacy.rejectProposal(proposalId, player.getUUID(), player.server);
                    } catch (IllegalArgumentException e) {
                        result = "§cInvalid proposal ID.";
                    }
                    break;
                }
                default:
                    result = "§cUnknown diplomatic action.";
            }

            // Send updated diplomacy data back with the result message
            sendDiplomacyData(player, playerNation.getName(), result);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendDiplomacyData(ServerPlayer player, String nationName, String resultMessage) {
        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            NetworkHandler.sendToPlayer(new SyncDiplomacyDataPacket(
                nationName, false, List.of(), List.of(), List.of(), "§cNation not found."), player);
            return;
        }

        boolean isLeader = nation.getLeaderId().equals(player.getUUID());
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        DiplomacyManager diplomacy = DiplomacyManager.getInstance();

        // Build nation relations list
        List<SyncDiplomacyDataPacket.NationRelation> relations = new ArrayList<>();
        for (Nation other : manager.getAllNations()) {
            if (other.getId().equals(nation.getId())) continue;

            DiplomacyManager.DiplomaticStatus status = diplomacy.getStatus(nation.getId(), other.getId());
            relations.add(new SyncDiplomacyDataPacket.NationRelation(
                other.getName(), status.name()));
        }

        // Sort: AT_WAR first, then TRUCE, then ALLIED, then NEUTRAL
        relations.sort((a, b) -> {
            int order = statusOrder(a.status) - statusOrder(b.status);
            if (order != 0) return order;
            return a.nationName.compareToIgnoreCase(b.nationName);
        });

        // Build inbound proposals
        List<SyncDiplomacyDataPacket.ProposalEntry> inbound = new ArrayList<>();
        for (DiplomacyManager.DiplomacyProposal p : diplomacy.getPendingProposals(nation.getId())) {
            Nation proposerNation = manager.getNation(p.proposerNationId);
            String proposerName = proposerNation != null ? proposerNation.getName() : "Unknown";
            inbound.add(new SyncDiplomacyDataPacket.ProposalEntry(
                p.id.toString(), p.type.name(), proposerName, p.expiresAt,
                p.hasTerms(), p.currencyDemand, p.chunkDemands.size()));
        }

        // Build outbound proposals
        List<SyncDiplomacyDataPacket.ProposalEntry> outbound = new ArrayList<>();
        for (DiplomacyManager.DiplomacyProposal p : diplomacy.getOutboundProposals(nation.getId())) {
            Nation targetNation = manager.getNation(p.targetNationId);
            String targetName = targetNation != null ? targetNation.getName() : "Unknown";
            outbound.add(new SyncDiplomacyDataPacket.ProposalEntry(
                p.id.toString(), p.type.name(), targetName, p.expiresAt,
                p.hasTerms(), p.currencyDemand, p.chunkDemands.size()));
        }

        NetworkHandler.sendToPlayer(new SyncDiplomacyDataPacket(
            nationName, isLeader, relations, inbound, outbound, resultMessage), player);
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "AT_WAR" -> 0;
            case "TRUCE" -> 1;
            case "ALLIED" -> 2;
            default -> 3; // NEUTRAL
        };
    }

    // ==================== Peace Terms Handlers ====================

    public static void handlePeaceTermsProposal(PeaceTermsProposalPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Nation playerNation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (playerNation == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are not in a nation."));
                return;
            }

            if (!playerNation.getLeaderId().equals(player.getUUID())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cOnly the nation leader can propose peace terms."));
                return;
            }

            Nation target = ChunkClaimManager.getInstance().getNationByName(packet.getTargetNationName());
            if (target == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNation not found: " + packet.getTargetNationName()));
                return;
            }

            // If this is a counter-proposal, remove the original proposal first
            if (!packet.getCounterProposalId().isEmpty()) {
                try {
                    UUID originalId = UUID.fromString(packet.getCounterProposalId());
                    DiplomacyManager.DiplomacyProposal original = DiplomacyManager.getInstance().getProposal(originalId);
                    if (original != null) {
                        // Silently remove the original proposal being countered
                        DiplomacyManager.getInstance().rejectProposal(originalId, player.getUUID(), player.server);
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            // Build chunk demands list
            List<DiplomacyManager.ChunkDemand> chunkDemands = new ArrayList<>();
            for (PeaceTermsProposalPacket.ChunkDemandData cd : packet.getChunkDemands()) {
                chunkDemands.add(new DiplomacyManager.ChunkDemand(cd.chunkX, cd.chunkZ, cd.dimension));
            }

            // Resolve receiving city
            UUID receivingCityId = null;
            if (!packet.getReceivingCityId().isEmpty()) {
                try {
                    receivingCityId = UUID.fromString(packet.getReceivingCityId());
                } catch (IllegalArgumentException e) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cInvalid receiving city ID."));
                    return;
                }
            }

            String result = DiplomacyManager.getInstance().proposePeaceWithTerms(
                playerNation, target, player.server,
                packet.getCurrencyDemand(), chunkDemands, receivingCityId);

            // Send updated diplomacy data back
            sendDiplomacyData(player, playerNation.getName(), result);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestTargetNationChunks(RequestTargetNationChunksPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Nation playerNation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            Nation target = ChunkClaimManager.getInstance().getNationByName(packet.getTargetNationName());

            if (playerNation == null || target == null) {
                NetworkHandler.sendToPlayer(new SyncTargetNationChunksPacket(
                    packet.getTargetNationName(), new ArrayList<>(), 0, new ArrayList<>()), player);
                return;
            }

            // Must be at war with the target to request their chunks
            if (!playerNation.isEnemy(target.getId())) {
                NetworkHandler.sendToPlayer(new SyncTargetNationChunksPacket(
                    packet.getTargetNationName(), new ArrayList<>(), 0, new ArrayList<>()), player);
                return;
            }

            // Build list of ALL chunks in target nation
            List<SyncTargetNationChunksPacket.NationChunkEntry> entries = new ArrayList<>();
            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            for (State state : target.getAllStates()) {
                for (City city : state.getAllCities()) {
                    for (ClaimedChunk chunk : city.getAllChunks()) {
                        String dimension = chunk.getDimension().location().toString();
                        int improvementScore = IntegrationRegistry.getChunkImprovementScore(
                            chunk.getChunkPos().x, chunk.getChunkPos().z, dimension);
                        double totalValue = IntegrationRegistry.getChunkTotalValue(
                            chunk.getChunkPos().x, chunk.getChunkPos().z, dimension);

                        entries.add(new SyncTargetNationChunksPacket.NationChunkEntry(
                            chunk.getChunkPos().x, chunk.getChunkPos().z,
                            dimension, city.getName(), improvementScore, totalValue));
                    }
                }
            }

            // Sort by city name then coordinates
            entries.sort((a, b) -> {
                int cmp = a.cityName.compareToIgnoreCase(b.cityName);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a.chunkX, b.chunkX);
                return cmp != 0 ? cmp : Integer.compare(a.chunkZ, b.chunkZ);
            });

            // Get target nation balance
            double targetBalance = IntegrationRegistry.getNationBalance(target.getName());

            // Build proposer's cities list for the receiving city dropdown
            List<SyncTargetNationChunksPacket.CityEntry> proposerCities = new ArrayList<>();
            for (State state : playerNation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    proposerCities.add(new SyncTargetNationChunksPacket.CityEntry(
                        city.getId().toString(), city.getName()));
                }
            }

            NetworkHandler.sendToPlayer(new SyncTargetNationChunksPacket(
                target.getName(), entries, targetBalance, proposerCities), player);
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== Company Handlers ====================

    public static void handleRequestCompanyData(RequestCompanyDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            sendCompanyData(player, "");
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCreateCompany(CreateCompanyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            String name = packet.getName().trim();
            if (name.length() < 3 || name.length() > 24) {
                sendCompanyData(player, "§cCompany name must be 3-24 characters.");
                return;
            }

            int shares = Math.max(1, Math.min(1000000, packet.getTotalShares()));

            // Determine HQ city from player's current location
            UUID headquartersCityId = null;
            ChunkClaimManager claimManager = ChunkClaimManager.getInstance();
            ClaimedChunk chunk = claimManager.getClaimedChunk(
                player.chunkPosition(), player.level().dimension());
            if (chunk != null) {
                headquartersCityId = chunk.getCityId();
            }

            Company company = CompanyManager.getInstance().createCompany(
                name, player.getUUID(), shares, headquartersCityId);

            if (company == null) {
                sendCompanyData(player, "§cFailed to create company. Name may be taken or you've reached the limit.");
                return;
            }

            if (!packet.getDescription().isBlank()) {
                company.setDescription(packet.getDescription().trim());
            }

            // Handle bank type
            boolean isBank = packet.isBank();
            if (isBank) {
                company.setCompanyType(Company.CompanyType.BANK);
                CompanyManager.getInstance().markDirty();
                // Initialize bank via economy integration if available
                IntegrationRegistry.notifyBankCreated(company.getId());
            }

            // Notify economy integration
            IntegrationRegistry.notifyCompanyCreated(company.getId());

            String cityName = "";
            if (headquartersCityId != null) {
                City city2 = claimManager.getCity(headquartersCityId);
                if (city2 != null) cityName = city2.getName();
            }

            String typeLabel = isBank ? "§a§lBank Created! " : "§a§lCompany Created! ";
            sendCompanyData(player, typeLabel + "§r§f" + name + " §7with " + shares + " shares."
                + (cityName.isEmpty() ? "" : " HQ: " + cityName));
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleCompanyAction(CompanyActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CompanyManager companyManager = CompanyManager.getInstance();
            UUID companyId;
            try {
                companyId = UUID.fromString(packet.getCompanyId());
            } catch (IllegalArgumentException e) {
                sendCompanyData(player, "§cInvalid company.");
                return;
            }

            Company company = companyManager.getCompany(companyId);
            if (company == null) {
                sendCompanyData(player, "§cCompany not found.");
                return;
            }

            String result;

            switch (packet.getAction()) {
                case ADD_OFFICER -> {
                    if (!company.isFounder(player.getUUID())) {
                        result = "§cOnly the founder can add officers.";
                    } else {
                        ServerPlayer target = player.server.getPlayerList().getPlayerByName(packet.getTargetPlayer());
                        if (target == null) {
                            result = "§cPlayer not found: " + packet.getTargetPlayer();
                        } else if (company.addOfficer(target.getUUID())) {
                            companyManager.markDirty();
                            result = "§a" + packet.getTargetPlayer() + " added as officer.";
                        } else {
                            result = "§cPlayer is already an officer.";
                        }
                    }
                }
                case REMOVE_OFFICER -> {
                    if (!company.isFounder(player.getUUID())) {
                        result = "§cOnly the founder can remove officers.";
                    } else {
                        ServerPlayer target = player.server.getPlayerList().getPlayerByName(packet.getTargetPlayer());
                        if (target == null) {
                            result = "§cPlayer not found: " + packet.getTargetPlayer();
                        } else if (company.removeOfficer(target.getUUID())) {
                            companyManager.markDirty();
                            result = "§a" + packet.getTargetPlayer() + " removed as officer.";
                        } else {
                            result = "§cCannot remove this player.";
                        }
                    }
                }
                case TRANSFER_SHARES -> {
                    if (!company.isShareholder(player.getUUID())) {
                        result = "§cYou don't own any shares.";
                    } else {
                        ServerPlayer target = player.server.getPlayerList().getPlayerByName(packet.getTargetPlayer());
                        if (target == null) {
                            result = "§cPlayer not found: " + packet.getTargetPlayer();
                        } else if (company.transferShares(player.getUUID(), target.getUUID(), packet.getIntValue())) {
                            companyManager.markDirty();
                            result = "§aTransferred " + packet.getIntValue() + " shares to " + packet.getTargetPlayer() + ".";
                        } else {
                            result = "§cInsufficient shares.";
                        }
                    }
                }
                case SET_DIVIDEND_RATE -> {
                    if (!company.isFounder(player.getUUID())) {
                        result = "§cOnly the founder can set dividend rate.";
                    } else {
                        IntegrationRegistry.setDividendRate(company.getId(), packet.getDoubleValue());
                        companyManager.markDirty();
                        result = "§aDividend rate set to " + String.format("%.1f%%", packet.getDoubleValue() * 100);
                    }
                }
                case TOGGLE_DIVIDENDS -> {
                    if (!company.isFounder(player.getUUID())) {
                        result = "§cOnly the founder can toggle dividends.";
                    } else {
                        boolean currentlyEnabled = IntegrationRegistry.isDividendsEnabled(company.getId());
                        IntegrationRegistry.setDividendsEnabled(company.getId(), !currentlyEnabled);
                        companyManager.markDirty();
                        result = !currentlyEnabled ? "§aDividends enabled." : "§7Dividends disabled.";
                    }
                }
                case RENAME -> {
                    if (!company.isFounder(player.getUUID())) {
                        result = "§cOnly the founder can rename the company.";
                    } else {
                        String newName = packet.getStringValue().trim();
                        if (newName.length() < 3 || newName.length() > 24) {
                            result = "§cName must be 3-24 characters.";
                        } else if (companyManager.renameCompany(companyId, newName)) {
                            result = "§aCompany renamed to " + newName + ".";
                        } else {
                            result = "§cName already taken.";
                        }
                    }
                }
                case SET_DESCRIPTION -> {
                    if (!company.canManage(player.getUUID())) {
                        result = "§cYou don't have permission.";
                    } else {
                        company.setDescription(packet.getStringValue().trim());
                        companyManager.markDirty();
                        result = "§aDescription updated.";
                    }
                }
                case DISSOLVE -> {
                    if (!company.isFounder(player.getUUID())) {
                        result = "§cOnly the founder can dissolve the company.";
                    } else if (companyManager.dissolveCompany(companyId, player.getUUID())) {
                        result = "§cCompany dissolved.";
                    } else {
                        result = "§cFailed to dissolve company.";
                    }
                }
                default -> result = "§cUnknown action.";
            }

            sendCompanyData(player, result);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendCompanyData(ServerPlayer player, String resultMessage) {
        CompanyManager companyManager = CompanyManager.getInstance();
        List<Company> playerCompanies = companyManager.getPlayerCompanies(player.getUUID());

        if (playerCompanies.isEmpty()) {
            NetworkHandler.sendToPlayer(new SyncCompanyDataPacket(
                false, "", "", "", "", 0, 0, 0, 0, "",
                false, false, false, 0.0, "",
                new ArrayList<>(), new ArrayList<>(), resultMessage), player);
            return;
        }

        // Send data for the first company the player is associated with
        Company company = playerCompanies.get(0);

        // Resolve founder name
        String founderName = resolvePlayerName(player.server, company.getFounderId());

        // Resolve HQ city name
        String hqCity = "";
        if (company.getHeadquartersCityId() != null) {
            City city = ChunkClaimManager.getInstance().getCity(company.getHeadquartersCityId());
            if (city != null) hqCity = city.getName();
        }

        // Build shareholder list
        List<SyncCompanyDataPacket.ShareholderEntry> shareholders = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : company.getShareholders().entrySet()) {
            String name = resolvePlayerName(player.server, entry.getKey());
            double pct = company.getSharePercentage(entry.getKey());
            shareholders.add(new SyncCompanyDataPacket.ShareholderEntry(name, entry.getValue(), pct));
        }

        // Build officer names list
        List<String> officerNames = new ArrayList<>();
        for (UUID officerId : company.getOfficers()) {
            officerNames.add(resolvePlayerName(player.server, officerId));
        }

        NetworkHandler.sendToPlayer(new SyncCompanyDataPacket(
            true,
            company.getName(),
            company.getId().toString(),
            founderName,
            company.getDescription(),
            company.getTotalShares(),
            company.getShareCount(player.getUUID()),
            company.getShareholders().size(),
            company.getOfficers().size(),
            hqCity,
            company.isFounder(player.getUUID()),
            company.isOfficer(player.getUUID()),
            IntegrationRegistry.isDividendsEnabled(company.getId()),
            IntegrationRegistry.getDividendRate(company.getId()),
            company.getCompanyType().name(),
            shareholders,
            officerNames,
            resultMessage
        ), player);
    }

    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName();
        var profile = server.getProfileCache();
        if (profile != null) {
            var cached = profile.get(playerId);
            if (cached.isPresent()) return cached.get().getName();
        }
        return playerId.toString().substring(0, 8);
    }

    // ==================== Shareholder Voting ====================

    public static void handleShareholderVote(ShareholderVotePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            UUID companyId;
            try {
                companyId = UUID.fromString(packet.getCompanyId());
            } catch (IllegalArgumentException e) {
                return;
            }

            Company company = CompanyManager.getInstance().getCompany(companyId);
            if (company == null) {
                sendShareholderVotesData(player, companyId, "§cCompany not found.");
                return;
            }

            ShareholderVoteManager voteManager = ShareholderVoteManager.getInstance();
            String result;

            switch (packet.getAction()) {
                case CREATE_PROPOSAL -> {
                    try {
                        ShareholderProposal.ProposalType proposalType =
                            ShareholderProposal.ProposalType.valueOf(packet.getProposalType());
                        result = voteManager.createProposal(
                            companyId, player.getUUID(), player.getGameProfile().getName(),
                            proposalType,
                            packet.getDoubleValue(), packet.getIntValue(), packet.getLongValue(),
                            packet.getStringValue(),
                            player.server
                        );
                    } catch (IllegalArgumentException e) {
                        result = "§cInvalid proposal type.";
                    }
                }
                case CAST_VOTE -> {
                    if (packet.getProposalId().isEmpty()) {
                        // Empty proposal ID = just requesting data refresh, no vote to cast
                        result = "";
                    } else {
                        try {
                            UUID proposalId = UUID.fromString(packet.getProposalId());
                            ShareholderProposal.Vote vote = ShareholderProposal.Vote.valueOf(packet.getVoteChoice());
                            result = voteManager.castVote(proposalId, player.getUUID(), vote);
                        } catch (IllegalArgumentException e) {
                            result = "§cInvalid vote parameters.";
                        }
                    }
                }
                default -> result = "§cUnknown action.";
            }

            sendShareholderVotesData(player, companyId, result);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendShareholderVotesData(ServerPlayer player, UUID companyId, String resultMessage) {
        Company company = CompanyManager.getInstance().getCompany(companyId);
        if (company == null) {
            NetworkHandler.sendToPlayer(new SyncShareholderVotesPacket(new java.util.ArrayList<>(), resultMessage), player);
            return;
        }

        ShareholderVoteManager voteManager = ShareholderVoteManager.getInstance();
        java.util.List<ShareholderProposal> companyProposals = voteManager.getCompanyProposals(companyId);

        java.util.List<SyncShareholderVotesPacket.ProposalInfo> infos = new java.util.ArrayList<>();
        for (ShareholderProposal proposal : companyProposals) {
            ShareholderProposal.VoteTally tally = proposal.getTally(company);
            String playerVote = "";
            if (proposal.hasVoted(player.getUUID())) {
                playerVote = proposal.getVotes().get(player.getUUID()).name();
            }

            infos.add(new SyncShareholderVotesPacket.ProposalInfo(
                proposal.getId().toString(),
                proposal.getType().name(),
                proposal.getType().getDisplayName(),
                proposal.getSummary(),
                proposal.getProposerName(),
                proposal.getStatus().name(),
                proposal.getExpiresAt(),
                tally.sharesYes(),
                tally.sharesNo(),
                tally.sharesAbstain(),
                tally.sharesVoted(),
                tally.totalShares(),
                playerVote,
                proposal.getDoubleValue(),
                proposal.getIntValue(),
                proposal.getLongValue()
            ));
        }

        NetworkHandler.sendToPlayer(new SyncShareholderVotesPacket(infos, resultMessage != null ? resultMessage : ""), player);
    }
}
