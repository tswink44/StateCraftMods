package com.statecraft.network;

import com.statecraft.StateCraft;
import com.statecraft.core.*;
import com.statecraft.data.NationSavedData;
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

            if (nation == null) {
                NetworkHandler.sendToPlayer(new SyncNationDataPacket(), player);
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
                    nation.isAdmin(player.getUUID())
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

            Nation nation = manager.createNation(packet.getName(), player.getUUID());
            if (nation == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not create nation. Name may be taken."), player);
                return;
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
                nation.getBalance(),
                nation.isOpen(),
                nation.getDescription(),
                leaderName,
                player.getUUID().equals(nation.getLeaderId()),
                nation.isAdmin(player.getUUID()),
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
                                        (nation.isAdmin(player.getUUID()) ||
                                         city.getMayorId().equals(player.getUUID()));

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
                            if (city.getMayorId().equals(player.getUUID()) || nation.isAdmin(player.getUUID())) {
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
                    double chunkClaimFee = getChunkClaimFee();
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
                            // Deduct chunk claim fee from city treasury
                            if (chunkClaimFee > 0) {
                                deductFromCityTreasury(targetCity.getId(), chunkClaimFee);
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
                        case NOT_CONTIGUOUS -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "Chunk must be adjacent to existing city or state territory"), player);
                        }
                        case INSUFFICIENT_FUNDS -> {
                            NetworkHandler.sendToPlayer(new ActionResultPacket(false,
                                "City treasury has insufficient funds to claim chunk"), player);
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

            if (!nation.isAdmin(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "No permission"), player);
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

                // Add admins
                for (UUID adminId : nation.getAdmins()) {
                    if (!adminId.equals(nation.getLeaderId())) {
                        String name = getPlayerName(player.server, adminId);
                        boolean online = isPlayerOnline(player.server, adminId);
                        members.add(new SyncMembersPacket.MemberInfo(name, "Admin", online));
                    }
                }

                // Add regular members
                for (UUID memberId : nation.getMembers()) {
                    if (!memberId.equals(nation.getLeaderId()) && !nation.getAdmins().contains(memberId)) {
                        String name = getPlayerName(player.server, memberId);
                        boolean online = isPlayerOnline(player.server, memberId);
                        members.add(new SyncMembersPacket.MemberInfo(name, "Member", online));
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
                // Check if player is a nation admin (can create states)
                canCreateState = nation.isAdmin(player.getUUID());

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

            if (!nation.isAdmin(player.getUUID())) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Only nation admins can create states!"), player);
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

            State state = manager.createState(nation, name, player.getUUID());
            if (state == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not create state. Max states may be reached."), player);
                return;
            }

            // Mark data dirty
            if (player.level() instanceof ServerLevel serverLevel) {
                NationSavedData.get(serverLevel).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true, "State '" + name + "' created successfully!"), player);
        });
        ctx.get().setPacketHandled(true);
    }

    private static String getPlayerName(net.minecraft.server.MinecraftServer server, UUID playerId) {
        if (playerId == null) return "Unknown";
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        // Try to get cached name from user cache
        var profile = server.getProfileCache().get(playerId);
        return profile.map(gameProfile -> gameProfile.getName()).orElse("Unknown");
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
            boolean canManage = isGovernor || nation.isAdmin(player.getUUID());

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
                cityNames
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
                    // Check if player can create cities (state governor or nation admin)
                    canCreateCity = state.getGovernorId().equals(player.getUUID()) || nation.isAdmin(player.getUUID());

                    for (City city : state.getAllCities()) {
                        String mayorName = getPlayerName(player.server, city.getMayorId());
                        boolean isResident = city.isResident(player.getUUID());
                        cities.add(new SyncCitiesPacket.CityInfo(
                            city.getName(),
                            mayorName,
                            city.getChunkCount(),
                            city.getResidents().size() + 1, // +1 for mayor
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
            if (!state.getGovernorId().equals(player.getUUID()) && !nation.isAdmin(player.getUUID())) {
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

            City city = manager.createCity(state, name, player.getUUID());
            if (city == null) {
                NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not create city. Max cities may be reached."), player);
                return;
            }

            // Mark data dirty
            if (player.level() instanceof ServerLevel serverLevel) {
                NationSavedData.get(serverLevel).markForSave();
            }

            NetworkHandler.sendToPlayer(new ActionResultPacket(true, "City '" + name + "' created successfully!"), player);
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
            boolean canManage = isMayor || state.getGovernorId().equals(player.getUUID()) || nation.isAdmin(player.getUUID());

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
                city.getResidents().size() + 1,
                isMayor,
                canManage,
                residentNames
            ), player);
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
                    boolean isNationAdmin = nation != null && nation.isAdmin(player.getUUID());

                    canManagePermits = isMayor || isGovernor || isPresident || isNationAdmin;
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
                    boolean isNationAdmin = nation != null && nation.isAdmin(player.getUUID());

                    canManage = isMayor || isGovernor || isPresident || isNationAdmin;
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
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Granted building permit to " + packet.getPlayerName()), player);
            } else {
                chunk.revokeBuildingPermit(targetId);
                manager.markDirty();
                NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Revoked building permit from " + packet.getPlayerName()), player);
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
                } else {
                    ownerName = cityName;
                    // Check government permissions
                    if (city != null) {
                        State state = manager.getState(city.getStateId());
                        Nation nation = state != null ? manager.getNation(state.getNationId()) : null;
                        boolean isMayor = player.getUUID().equals(city.getMayorId());
                        boolean isGovernor = state != null && player.getUUID().equals(state.getGovernorId());
                        boolean isPresident = nation != null && player.getUUID().equals(nation.getLeaderId());
                        boolean isNationAdmin = nation != null && nation.isAdmin(player.getUUID());
                        canManagePermits = isMayor || isGovernor || isPresident || isNationAdmin;
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

                        listings.add(new SyncMarketplaceDataPacket.ListingInfo(
                            chunkX, chunkZ,
                            ownerName,
                            isGovernment,
                            chunk.getSalePrice(),
                            cityName
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
                    if (!nation.isAdmin(playerId)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou must be a nation admin to change settings"));
                        return;
                    }

                    // Check if new name is taken
                    if (!newName.isEmpty() && !newName.equals(nation.getName())) {
                        Nation existing = manager.getNationByName(newName);
                        if (existing != null) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cA nation with that name already exists"));
                            return;
                        }
                        nation.setName(newName);
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
                    if (!playerId.equals(state.getGovernorId()) && !playerNation.isAdmin(playerId)) {
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

                    // Update max chunks if provided (using passThroughRate field as int)
                    if (packet.getPassThroughRate() > 0) {
                        int maxChunks = (int) packet.getPassThroughRate();
                        state.setMaxChunks(Math.max(1, Math.min(100000, maxChunks)));
                    }

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
                        !playerNation.isAdmin(playerId)) {
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

                    // Update base tax rate if provided (cities set the base rate)
                    if (packet.getTaxRate() >= 0) {
                        double taxRate = Math.max(0, Math.min(100, packet.getTaxRate()));
                        city.setTaxRate(taxRate / 100.0); // Convert to decimal
                    }

                    // Update max chunks if provided (using passThroughRate field as int)
                    if (packet.getPassThroughRate() > 0) {
                        int maxChunks = (int) packet.getPassThroughRate();
                        city.setMaxChunks(Math.max(1, Math.min(10000, maxChunks)));
                    }
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
                            mail.isRead()
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
                            mail.isRead()
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

            // Send the mail
            MailManager.getInstance().sendPlayerMail(
                sender.getUUID(),
                sender.getName().getString(),
                recipientId,
                subject,
                body
            );

            sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§aMail sent to " + recipientName + "!"));
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

            // Check if player is a member of this nation
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
                    if (player.level() instanceof ServerLevel serverLevel) {
                        NationSavedData.get(serverLevel).markForSave();
                    }

                    NetworkHandler.sendToPlayer(new ActionResultPacket(true,
                        "You have left " + city.getName() + "."), player);
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
     * Get the configured chunk claim fee from StateCraft Economy config
     * Returns 0 if economy mod is not loaded or fee is disabled
     */
    private static double getChunkClaimFee() {
        try {
            // Check if StateCraft Economy is loaded
            if (!net.minecraftforge.fml.ModList.get().isLoaded("statecraft_economy")) {
                return 0;
            }

            // Use reflection to access economy config to avoid hard dependency
            Class<?> configClass = Class.forName("com.statecraft.economy.config.EconomyConfig");

            // Check if fee is enabled
            Object enabledField = configClass.getField("CHUNK_CLAIM_FEE_ENABLED").get(null);
            boolean enabled = (boolean) enabledField.getClass().getMethod("get").invoke(enabledField);
            if (!enabled) {
                return 0;
            }

            // Get fee amount
            Object feeField = configClass.getField("CHUNK_CLAIM_FEE").get(null);
            return (double) feeField.getClass().getMethod("get").invoke(feeField);
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Could not get chunk claim fee from economy config: {}", e.getMessage());
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
}

