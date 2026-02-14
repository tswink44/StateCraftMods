package com.statecraft.network;

import com.statecraft.StateCraft;
import com.statecraft.core.*;
import com.statecraft.data.NationSavedData;
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
                    for (City city : state.getAllCities()) {
                        if (city.isResident(player.getUUID())) {
                            stateName = state.getName();
                            cityName = city.getName();
                            break;
                        }
                    }
                    if (!cityName.isEmpty()) break;
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

                    ClaimedChunk chunk = manager.claimChunk(targetCity, pos, player.level().dimension());
                    if (chunk == null) {
                        NetworkHandler.sendToPlayer(new ActionResultPacket(false, "Could not claim chunk"), player);
                        return;
                    }

                    if (player.level() instanceof ServerLevel level) {
                        NationSavedData.get(level).markForSave();
                        // Broadcast chunk update to nearby players
                        broadcastChunkUpdateToNearby(level, pos.x, pos.z);
                    }
                    NetworkHandler.sendToPlayer(new ActionResultPacket(true, "Chunk claimed"), player);
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

            if (nation != null) {
                for (State state : nation.getAllStates()) {
                    String governorName = getPlayerName(player.server, state.getGovernorId());
                    states.add(new SyncStatesPacket.StateInfo(
                        state.getName(),
                        governorName,
                        state.getCityCount(),
                        state.getTotalChunkCount()
                    ));
                }
            }

            NetworkHandler.sendToPlayer(new SyncStatesPacket(states), player);
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

            if (nation != null) {
                State state = nation.getStateByName(packet.getStateName());
                if (state != null) {
                    for (City city : state.getAllCities()) {
                        String mayorName = getPlayerName(player.server, city.getMayorId());
                        cities.add(new SyncCitiesPacket.CityInfo(
                            city.getName(),
                            mayorName,
                            city.getChunkCount(),
                            city.getResidents().size() + 1 // +1 for mayor
                        ));
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncCitiesPacket(cities), player);
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
                canManagePermits, permitHolders
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }
}

