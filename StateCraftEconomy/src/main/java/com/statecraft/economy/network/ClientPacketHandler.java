package com.statecraft.economy.network;

import com.statecraft.economy.client.screen.ATMScreen;
import com.statecraft.economy.client.screen.SimpleATMScreen;
import com.statecraft.economy.network.packets.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles packets received on the client
 */
public class ClientPacketHandler {

    // Cached balance for GUI display
    private static double cachedBalance = 0;
    private static double cachedNationBalance = -1;

    // Cached available accounts
    private static List<SyncAccountsPacket.AccountInfo> cachedAccounts = new ArrayList<>();

    // Cached transfer recipients
    private static String cachedRecipientType = "";
    private static List<SyncTransferRecipientsPacket.RecipientInfo> cachedRecipients = new ArrayList<>();

    public static void handleSyncBalance(SyncBalancePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedBalance = packet.getBalance();
            cachedNationBalance = packet.getNationBalance();

            // Update ATM screen if open
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ATMScreen atmScreen) {
                atmScreen.setBalance(cachedBalance);
            } else if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateBalance(cachedBalance);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleTransactionResult(TransactionResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String prefix = packet.isSuccess() ? "§a" : "§c";
                mc.player.sendSystemMessage(Component.literal(prefix + packet.getMessage()));
            }
            cachedBalance = packet.getNewBalance();

            // Update ATM screen if open
            if (mc.screen instanceof ATMScreen atmScreen) {
                atmScreen.setBalance(cachedBalance);
                atmScreen.setStatusMessage(packet.getMessage(), 100);
            } else if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateBalance(cachedBalance);
                simpleAtm.showTransactionResult(packet.isSuccess(), packet.getMessage());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenATMScreen(OpenATMScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new SimpleATMScreen(packet.getBankId()));
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncAccounts(SyncAccountsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedAccounts = new ArrayList<>(packet.getAccounts());

            // Update ATM screen if open
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateAvailableAccounts(cachedAccounts);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncTransferRecipients(SyncTransferRecipientsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedRecipientType = packet.getRecipientType();
            cachedRecipients = new ArrayList<>(packet.getRecipients());

            // Update ATM screen if open
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateTransferRecipients(cachedRecipientType, cachedRecipients);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static double getCachedBalance() {
        return cachedBalance;
    }

    public static double getCachedNationBalance() {
        return cachedNationBalance;
    }

    public static List<SyncAccountsPacket.AccountInfo> getCachedAccounts() {
        return cachedAccounts;
    }

    public static String getCachedRecipientType() {
        return cachedRecipientType;
    }

    public static List<SyncTransferRecipientsPacket.RecipientInfo> getCachedRecipients() {
        return cachedRecipients;
    }
}

