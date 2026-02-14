package com.statecraft.economy.network.packets;

import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.gui.ATMMenu;
import com.statecraft.economy.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet for ATM transaction requests (check balance, deposit, withdraw, transfer)
 */
public class ATMTransactionPacket {

    public enum Action {
        CHECK_BALANCE,
        DEPOSIT,
        WITHDRAW,
        TRANSFER
    }

    private final Action action;
    private final double amount;
    private final String recipient; // Player name for transfers

    public ATMTransactionPacket(Action action, double amount, String recipient) {
        this.action = action;
        this.amount = amount;
        this.recipient = recipient;
    }

    public static void encode(ATMTransactionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeDouble(packet.amount);
        buffer.writeUtf(packet.recipient);
    }

    public static ATMTransactionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        double amount = buffer.readDouble();
        String recipient = buffer.readUtf();
        return new ATMTransactionPacket(action, amount, recipient);
    }

    public static void handle(ATMTransactionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            EconomyManager manager = EconomyManager.getInstance();

            switch (packet.action) {
                case CHECK_BALANCE -> {
                    double balance = manager.getBalance(player.getUUID());
                    NetworkHandler.sendToPlayer(new SyncBalancePacket(balance, 0), player);
                }
                case DEPOSIT -> {
                    // Check player inventory for currency items worth the specified amount
                    double requiredAmount = packet.amount;
                    if (requiredAmount <= 0) {
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                            "Invalid deposit amount", manager.getBalance(player.getUUID())), player);
                        return;
                    }

                    // Calculate how much currency the player has in inventory
                    double availableCurrency = 0;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        var stack = player.getInventory().getItem(i);
                        availableCurrency += manager.getCurrencyValue(stack);
                    }

                    // Check if player has enough
                    if (availableCurrency < requiredAmount) {
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                            "Insufficient currency in inventory. Have: " + manager.formatCurrency(availableCurrency) + 
                            ", Need: " + manager.formatCurrency(requiredAmount), 
                            manager.getBalance(player.getUUID())), player);
                        return;
                    }

                    // Remove currency items from inventory
                    double remaining = requiredAmount;
                    for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                        var stack = player.getInventory().getItem(i);
                        if (stack.isEmpty()) continue;

                        double itemValue = manager.getCurrencyValue(stack);
                        if (itemValue <= 0) continue;

                        double valuePerItem = itemValue / stack.getCount();
                        int itemsNeeded = (int) Math.ceil(remaining / valuePerItem);
                        int itemsToRemove = Math.min(itemsNeeded, stack.getCount());

                        double valueRemoved = valuePerItem * itemsToRemove;
                        stack.shrink(itemsToRemove);
                        remaining -= valueRemoved;
                    }

                    // Deposit the amount
                    manager.deposit(player.getUUID(), requiredAmount, "ATM deposit");
                    double newBalance = manager.getBalance(player.getUUID());
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                        "Deposited " + manager.formatCurrency(requiredAmount), newBalance), player);
                }
                case WITHDRAW -> {
                    TransactionResult result = manager.withdraw(player.getUUID(), packet.amount, "ATM withdrawal");

                    if (result.isSuccess()) {
                        // Give currency items to the player
                        var items = manager.convertToItems(packet.amount);
                        boolean allAdded = true;

                        for (var itemStack : items) {
                            if (!player.getInventory().add(itemStack)) {
                                // Drop items that don't fit
                                player.drop(itemStack, false);
                            }
                        }

                        String message = result.getMessage();
                        if (items.isEmpty()) {
                            message += " (No currency items available - check config)";
                        }

                        NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                            message, result.getNewBalance()), player);
                    } else {
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                            result.getMessage(), result.getNewBalance()), player);
                    }
                }
                case TRANSFER -> {
                    // Parse recipient format: "TYPE:id"
                    String[] parts = packet.recipient.split(":", 2);
                    if (parts.length != 2) {
                        // Legacy format - try as player name
                        ServerPlayer recipientPlayer = player.server.getPlayerList().getPlayerByName(packet.recipient);
                        if (recipientPlayer == null) {
                            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                "Invalid recipient format", manager.getBalance(player.getUUID())), player);
                            return;
                        }
                        TransactionResult result = manager.transfer(player.getUUID(), recipientPlayer.getUUID(),
                            packet.amount, "Transfer via ATM");
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(result.isSuccess(),
                            result.getMessage(), result.getNewBalance()), player);
                        if (result.isSuccess()) {
                            recipientPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§a[Economy] Received " + manager.formatCurrency(packet.amount) + " from " + player.getName().getString()));
                        }
                        return;
                    }

                    String targetType = parts[0];
                    String targetId = parts[1];

                    try {
                        UUID targetUUID = UUID.fromString(targetId);
                        TransactionResult result;
                        String recipientName = targetId;

                        switch (targetType) {
                            case "PLAYER" -> {
                                // Transfer to player
                                result = manager.transfer(player.getUUID(), targetUUID,
                                    packet.amount, "Transfer via ATM");
                                // Try to notify online player
                                ServerPlayer recipientPlayer = player.server.getPlayerList().getPlayer(targetUUID);
                                if (result.isSuccess() && recipientPlayer != null) {
                                    recipientPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        "§a[Economy] Received " + manager.formatCurrency(packet.amount) + " from " + player.getName().getString()));
                                }
                            }
                            case "NATION" -> {
                                // Transfer from player to nation treasury
                                result = transferToEntity(manager, player, targetUUID, packet.amount, "nation");
                            }
                            case "STATE" -> {
                                // Transfer from player to state treasury
                                result = transferToEntity(manager, player, targetUUID, packet.amount, "state");
                            }
                            case "CITY" -> {
                                // Transfer from player to city treasury
                                result = transferToEntity(manager, player, targetUUID, packet.amount, "city");
                            }
                            default -> {
                                result = new TransactionResult(false, "Unknown recipient type: " + targetType,
                                    manager.getBalance(player.getUUID()));
                            }
                        }

                        NetworkHandler.sendToPlayer(new TransactionResultPacket(result.isSuccess(),
                            result.getMessage(), result.getNewBalance()), player);
                    } catch (IllegalArgumentException e) {
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                            "Invalid recipient ID", manager.getBalance(player.getUUID())), player);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Helper method to transfer funds from a player to a government entity (nation, state, city)
     */
    private static TransactionResult transferToEntity(EconomyManager manager, ServerPlayer player,
                                                        UUID entityId, double amount, String entityType) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        double playerBalance = manager.getBalance(player.getUUID());
        if (playerBalance < amount) {
            return new TransactionResult(false, "Insufficient funds", playerBalance);
        }

        // Withdraw from player
        TransactionResult withdrawResult = manager.withdraw(player.getUUID(), amount,
            "Transfer to " + entityType + " treasury");
        if (!withdrawResult.isSuccess()) {
            return withdrawResult;
        }

        // Deposit to entity treasury
        switch (entityType.toLowerCase()) {
            case "nation" -> {
                manager.getOrCreateNationTreasury(entityId).add(amount);
            }
            case "state" -> {
                manager.getOrCreateStateTreasury(entityId).add(amount);
            }
            case "city" -> {
                manager.getOrCreateCityTreasury(entityId).add(amount);
            }
            default -> {
                // Shouldn't happen, but refund just in case
                manager.deposit(player.getUUID(), amount, "Transfer refund");
                return new TransactionResult(false, "Unknown entity type", manager.getBalance(player.getUUID()));
            }
        }

        return new TransactionResult(true,
            "Transferred " + manager.formatCurrency(amount) + " to " + entityType + " treasury",
            manager.getBalance(player.getUUID()));
    }
}

