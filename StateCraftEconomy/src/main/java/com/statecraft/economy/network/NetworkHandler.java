package com.statecraft.economy.network;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.network.packets.*;
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

        // Trading Hub packet
        CHANNEL.messageBuilder(TradingHubSellPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(TradingHubSellPacket::toBytes)
            .decoder(TradingHubSellPacket::new)
            .consumerMainThread(TradingHubSellPacket::handle)
            .add();

        // Trading Hub settings packet
        CHANNEL.messageBuilder(TradingHubSettingsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(TradingHubSettingsPacket::encode)
            .decoder(TradingHubSettingsPacket::new)
            .consumerMainThread(TradingHubSettingsPacket::handle)
            .add();

        // Server -> Client packets - use dist-safe handlers
        CHANNEL.messageBuilder(SyncBalancePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncBalancePacket::encode)
            .decoder(SyncBalancePacket::new)
            .consumerMainThread(NetworkHandler::handleSyncBalanceClient)
            .add();

        CHANNEL.messageBuilder(TransactionResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TransactionResultPacket::encode)
            .decoder(TransactionResultPacket::new)
            .consumerMainThread(NetworkHandler::handleTransactionResultClient)
            .add();

        CHANNEL.messageBuilder(OpenATMScreenPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenATMScreenPacket::encode)
            .decoder(OpenATMScreenPacket::new)
            .consumerMainThread(NetworkHandler::handleOpenATMScreenClient)
            .add();

        CHANNEL.messageBuilder(SyncAccountsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAccountsPacket::encode)
            .decoder(SyncAccountsPacket::new)
            .consumerMainThread(NetworkHandler::handleSyncAccountsClient)
            .add();

        CHANNEL.messageBuilder(SyncTransferRecipientsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncTransferRecipientsPacket::encode)
            .decoder(SyncTransferRecipientsPacket::new)
            .consumerMainThread(NetworkHandler::handleSyncTransferRecipientsClient)
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
            .consumerMainThread(NetworkHandler::handleSyncChunkMarketInfoClient)
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
            .consumerMainThread(NetworkHandler::handleSyncChunkValuationClient)
            .add();

        // Account activity packets
        CHANNEL.messageBuilder(RequestAccountActivityPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestAccountActivityPacket::encode)
            .decoder(RequestAccountActivityPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestAccountActivity)
            .add();

        CHANNEL.messageBuilder(SyncAccountActivityPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncAccountActivityPacket::encode)
            .decoder(SyncAccountActivityPacket::new)
            .consumerMainThread(NetworkHandler::handleSyncAccountActivityClient)
            .add();

        // Marketplace packets
        CHANNEL.messageBuilder(OpenMarketplaceScreenPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenMarketplaceScreenPacket::encode)
            .decoder(OpenMarketplaceScreenPacket::new)
            .consumerMainThread(NetworkHandler::handleOpenMarketplaceScreenClient)
            .add();

        CHANNEL.messageBuilder(RequestMarketListingsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestMarketListingsPacket::encode)
            .decoder(RequestMarketListingsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestMarketListings)
            .add();

        CHANNEL.messageBuilder(SyncMarketListingsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncMarketListingsPacket::encode)
            .decoder(SyncMarketListingsPacket::new)
            .consumerMainThread(NetworkHandler::handleSyncMarketListingsClient)
            .add();

        CHANNEL.messageBuilder(MarketplaceActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(MarketplaceActionPacket::encode)
            .decoder(MarketplaceActionPacket::new)
            .consumerMainThread(MarketplaceActionPacket::handle)
            .add();

        // Stock Market packets
        CHANNEL.messageBuilder(OpenStockMarketScreenPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenStockMarketScreenPacket::encode)
            .decoder(OpenStockMarketScreenPacket::new)
            .consumerMainThread(NetworkHandler::handleOpenStockMarketScreenClient)
            .add();

        CHANNEL.messageBuilder(RequestStockListingsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestStockListingsPacket::encode)
            .decoder(RequestStockListingsPacket::new)
            .consumerMainThread(ServerPacketHandler::handleRequestStockListings)
            .add();

        CHANNEL.messageBuilder(SyncStockListingsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncStockListingsPacket::encode)
            .decoder(SyncStockListingsPacket::new)
            .consumerMainThread(NetworkHandler::handleSyncStockListingsClient)
            .add();

        CHANNEL.messageBuilder(StockMarketActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(StockMarketActionPacket::encode)
            .decoder(StockMarketActionPacket::new)
            .consumerMainThread(ServerPacketHandler::handleStockMarketAction)
            .add();

        // Item value sync packet (server -> client, sent on login)
        CHANNEL.messageBuilder(SyncItemValuesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncItemValuesPacket::encode)
            .decoder(SyncItemValuesPacket::new)
            .consumerMainThread(NetworkHandler::handleSyncItemValuesClient)
            .add();

        StateCraftEconomy.LOGGER.info("StateCraft Economy network packets registered");
    }

    // Client packet handlers using DistExecutor for safe loading
    private static void handleSyncBalanceClient(SyncBalancePacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncBalance(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleTransactionResultClient(TransactionResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleTransactionResult(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleOpenATMScreenClient(OpenATMScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenATMScreen(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncAccountsClient(SyncAccountsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncAccounts(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncTransferRecipientsClient(SyncTransferRecipientsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncTransferRecipients(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncChunkMarketInfoClient(SyncChunkMarketInfoPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncChunkMarketInfo(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncChunkValuationClient(SyncChunkValuationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncChunkValuation(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncAccountActivityClient(SyncAccountActivityPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncAccountActivity(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleOpenMarketplaceScreenClient(OpenMarketplaceScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenMarketplaceScreen(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncMarketListingsClient(SyncMarketListingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncMarketListings(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleOpenStockMarketScreenClient(OpenStockMarketScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenStockMarketScreen(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncStockListingsClient(SyncStockListingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncStockListings(packet, ctx));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSyncItemValuesClient(SyncItemValuesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncItemValues(packet, ctx));
        ctx.get().setPacketHandled(true);
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
