package com.statecraft.economy.network;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Handles network packet registration and sending
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(StateCraftEconomy.MOD_ID, "main"),
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
        CHANNEL.messageBuilder(DepositPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(DepositPacket::encode)
            .decoder(DepositPacket::new)
            .consumerMainThread(ServerPacketHandler::handleDeposit)
            .add();

        CHANNEL.messageBuilder(WithdrawPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(WithdrawPacket::encode)
            .decoder(WithdrawPacket::new)
            .consumerMainThread(ServerPacketHandler::handleWithdraw)
            .add();

        CHANNEL.messageBuilder(TransferPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(TransferPacket::encode)
            .decoder(TransferPacket::new)
            .consumerMainThread(ServerPacketHandler::handleTransfer)
            .add();

        CHANNEL.messageBuilder(RequestBalancePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestBalancePacket::encode)
            .decoder(RequestBalancePacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestBalance)
            .add();

        CHANNEL.messageBuilder(NationTreasuryPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(NationTreasuryPacket::encode)
            .decoder(NationTreasuryPacket::new)
            .consumerMainThread(ServerPacketHandler::handleNationTreasury)
            .add();

        CHANNEL.messageBuilder(ATMTransactionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ATMTransactionPacket::encode)
            .decoder(ATMTransactionPacket::decode)
            .consumerMainThread(ATMTransactionPacket::handle)
            .add();

        CHANNEL.messageBuilder(RequestAccountsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestAccountsPacket::encode)
            .decoder(RequestAccountsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestAccounts)
            .add();

        CHANNEL.messageBuilder(RequestTransferRecipientsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestTransferRecipientsPacket::encode)
            .decoder(RequestTransferRecipientsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestTransferRecipients)
            .add();

        // Server -> Client packets
        CHANNEL.messageBuilder(SyncBalancePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncBalancePacket::encode)
            .decoder(SyncBalancePacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncBalance)
            .add();

        CHANNEL.messageBuilder(TransactionResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TransactionResultPacket::encode)
            .decoder(TransactionResultPacket::new)
            .consumerMainThread(ClientPacketHandler::handleTransactionResult)
            .add();

        CHANNEL.messageBuilder(OpenATMScreenPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenATMScreenPacket::encode)
            .decoder(OpenATMScreenPacket::new)
            .consumerMainThread(ClientPacketHandler::handleOpenATMScreen)
            .add();

        CHANNEL.messageBuilder(SyncAccountsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAccountsPacket::encode)
            .decoder(SyncAccountsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncAccounts)
            .add();

        CHANNEL.messageBuilder(SyncTransferRecipientsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncTransferRecipientsPacket::encode)
            .decoder(SyncTransferRecipientsPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncTransferRecipients)
            .add();

        // Chunk market packets
        CHANNEL.messageBuilder(ChunkMarketPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(ChunkMarketPacket::encode)
            .decoder(ChunkMarketPacket::new)
            .consumerMainThread(ServerPacketHandler::handleChunkMarket)
            .add();

        CHANNEL.messageBuilder(SyncChunkMarketInfoPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkMarketInfoPacket::encode)
            .decoder(SyncChunkMarketInfoPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncChunkMarketInfo)
            .add();

        // Chunk valuation packets
        CHANNEL.messageBuilder(RequestChunkValuationPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestChunkValuationPacket::encode)
            .decoder(RequestChunkValuationPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestChunkValuation)
            .add();

        CHANNEL.messageBuilder(SyncChunkValuationPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkValuationPacket::encode)
            .decoder(SyncChunkValuationPacket::new)
            .consumerMainThread(ClientPacketHandler::handleSyncChunkValuation)
            .add();

        StateCraftEconomy.LOGGER.info("StateCraft Economy network packets registered");
    }

    public static <T> void sendToServer(T packet) {
        CHANNEL.sendToServer(packet);
    }

    public static <T> void sendToPlayer(T packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static <T> void sendToAll(T packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}

