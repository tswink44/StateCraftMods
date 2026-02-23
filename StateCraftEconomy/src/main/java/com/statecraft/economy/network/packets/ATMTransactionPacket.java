package com.statecraft.economy.network.packets;

import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.SpendingLimitManager;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.gui.ATMMenu;
import com.statecraft.economy.integration.StateCraftIntegration;
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
        TRANSFER,
        OPEN_BANK_ACCOUNT
    }

    private final Action action;
    private final double amount;
    private final String recipient; // Player name for transfers
    private final String sourceAccount; // Source account for transfers: empty = personal, or "TYPE:uuid"
    private final String note; // Optional user note for deposits/withdrawals

    public ATMTransactionPacket(Action action, double amount, String recipient) {
        this(action, amount, recipient, "", "");
    }

    public ATMTransactionPacket(Action action, double amount, String recipient, String sourceAccount) {
        this(action, amount, recipient, sourceAccount, "");
    }

    public ATMTransactionPacket(Action action, double amount, String recipient, String sourceAccount, String note) {
        this.action = action;
        this.amount = amount;
        this.recipient = recipient;
        this.sourceAccount = sourceAccount != null ? sourceAccount : "";
        this.note = note != null ? note : "";
    }

    public static void encode(ATMTransactionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeDouble(packet.amount);
        buffer.writeUtf(packet.recipient);
        buffer.writeUtf(packet.sourceAccount);
        buffer.writeUtf(packet.note);
    }

    public static ATMTransactionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        double amount = buffer.readDouble();
        String recipient = buffer.readUtf();
        String sourceAccount = buffer.readUtf();
        String note = buffer.readUtf();
        return new ATMTransactionPacket(action, amount, recipient, sourceAccount, note);
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
                    // Check if depositing to a specific account (government treasury)
                    String accountTarget = packet.recipient;
                    boolean isGovernmentAccount = accountTarget != null && !accountTarget.isEmpty()
                        && accountTarget.contains(":");

                    // Build description with optional note
                    String noteSuffix = (packet.note != null && !packet.note.isEmpty()) ? " - " + packet.note : "";

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
                    double totalRemoved = 0;
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
                        totalRemoved += valueRemoved;
                        remaining -= valueRemoved;
                    }

                    // Calculate change (if we removed more than needed)
                    double change = totalRemoved - requiredAmount;
                    if (change > 0.001) { // Small epsilon for floating point comparison
                        // Give change back to player as currency items
                        var changeItems = manager.convertToItems(change);
                        for (var changeItem : changeItems) {
                            if (!player.getInventory().add(changeItem)) {
                                // Drop items that don't fit
                                player.drop(changeItem, false);
                            }
                        }
                    }

                    // Deposit the amount to the appropriate account
                    String changeMessage = change > 0.001 ? " (Change: " + manager.formatCurrency(change) + ")" : "";

                    if (isGovernmentAccount) {
                        String[] parts = accountTarget.split(":", 2);
                        String targetType = parts[0];
                        try {
                            UUID targetUUID = UUID.fromString(parts[1]);

                            switch (targetType) {
                                case "NATION" -> manager.getOrCreateNationTreasury(targetUUID).add(requiredAmount);
                                case "STATE" -> manager.getOrCreateStateTreasury(targetUUID).add(requiredAmount);
                                case "CITY" -> manager.getOrCreateCityTreasury(targetUUID).add(requiredAmount);
                                case "COMPANY" -> manager.getOrCreateCompanyTreasury(targetUUID).add(requiredAmount);
                                case "BANK_DEPOSIT" -> {
                                    // Add to both the bank treasury and the player's depositor balance
                                    manager.getOrCreateCompanyTreasury(targetUUID).add(requiredAmount);
                                    var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                                    var bank = bankMgr.getBank(targetUUID);
                                    if (bank != null) {
                                        bank.addDepositorBalance(player.getUUID(), requiredAmount);
                                        bankMgr.markDirty();
                                    }
                                }
                                default -> {
                                    // Unknown type, deposit to personal account as fallback
                                    manager.deposit(player.getUUID(), requiredAmount, "ATM deposit" + noteSuffix);
                                }
                            }

                            // Record transaction on the government account with initiator info
                            manager.recordAccountTransaction(targetUUID,
                                com.statecraft.economy.core.Transaction.Type.DEPOSIT,
                                requiredAmount, player.getUUID(),
                                "ATM deposit to " + targetType.toLowerCase() + " treasury" + noteSuffix,
                                player.getUUID(), player.getName().getString());

                            double entityBalance = switch (targetType) {
                                case "NATION" -> manager.getNationBalance(targetUUID);
                                case "STATE" -> manager.getStateBalance(targetUUID);
                                case "CITY" -> manager.getCityBalance(targetUUID);
                                case "COMPANY" -> manager.getCompanyBalance(targetUUID);
                                case "BANK_DEPOSIT" -> {
                                    var bank = com.statecraft.economy.company.BankManager.getInstance().getBank(targetUUID);
                                    yield bank != null ? bank.getDepositorBalance(player.getUUID()) : 0;
                                }
                                default -> manager.getBalance(player.getUUID());
                            };

                            NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                                "Deposited " + manager.formatCurrency(requiredAmount) + " to " + targetType.toLowerCase() + " treasury" + changeMessage,
                                entityBalance), player);
                        } catch (IllegalArgumentException e) {
                            // Invalid UUID, deposit to personal as fallback
                            manager.deposit(player.getUUID(), requiredAmount, "ATM deposit" + noteSuffix);
                            double newBalance = manager.getBalance(player.getUUID());
                            NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                                "Deposited " + manager.formatCurrency(requiredAmount) + changeMessage, newBalance), player);
                        }
                    } else {
                        // Personal account deposit
                        manager.deposit(player.getUUID(), requiredAmount, "ATM deposit" + noteSuffix);
                        double newBalance = manager.getBalance(player.getUUID());
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                            "Deposited " + manager.formatCurrency(requiredAmount) + changeMessage, newBalance), player);
                    }
                }
                case WITHDRAW -> {
                    // Check if withdrawing from a specific account (government treasury)
                    String accountTarget = packet.recipient;
                    boolean isGovernmentAccount = accountTarget != null && !accountTarget.isEmpty()
                        && accountTarget.contains(":");

                    // Build description with optional note
                    String noteSuffix = (packet.note != null && !packet.note.isEmpty()) ? " - " + packet.note : "";

                    TransactionResult result;

                    if (isGovernmentAccount) {
                        String[] parts = accountTarget.split(":", 2);
                        String targetType = parts[0];
                        try {
                            UUID targetUUID = UUID.fromString(parts[1]);

                            // Check balance and withdraw from government treasury
                            double treasuryBalance = switch (targetType) {
                                case "NATION" -> manager.getNationBalance(targetUUID);
                                case "STATE" -> manager.getStateBalance(targetUUID);
                                case "CITY" -> manager.getCityBalance(targetUUID);
                                case "COMPANY" -> manager.getCompanyBalance(targetUUID);
                                case "BANK_DEPOSIT" -> {
                                    var bank = com.statecraft.economy.company.BankManager.getInstance().getBank(targetUUID);
                                    yield bank != null ? bank.getDepositorBalance(player.getUUID()) : 0;
                                }
                                default -> 0;
                            };

                            if (treasuryBalance < packet.amount) {
                                NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                    "Insufficient funds in " + targetType.toLowerCase() + " treasury", treasuryBalance), player);
                                return;
                            }

                            // Check daily spending limit
                            SpendingLimitManager spendingMgr = SpendingLimitManager.getInstance();
                            SpendingLimitManager.GovernmentRole role =
                                StateCraftIntegration.getPlayerGovernmentRole(player, targetType, targetUUID);
                            UUID nationIdForLimit = StateCraftIntegration.getNationIdForAccount(targetType, targetUUID);
                            String limitError = spendingMgr.checkSpendingLimit(
                                player.getUUID(), targetType, targetUUID, packet.amount, role, nationIdForLimit, player);
                            if (limitError != null) {
                                NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                    limitError, treasuryBalance), player);
                                return;
                            }

                            // Check economic emergency — only leader can withdraw during emergency
                            if (targetType.equals("NATION") || targetType.equals("STATE") || targetType.equals("CITY")) {
                                UUID emergencyNationId = nationIdForLimit != null ? nationIdForLimit : targetUUID;
                                if (StateCraftIntegration.isEconomicEmergencyActive(emergencyNationId) &&
                                    !StateCraftIntegration.isNationLeader(player.getUUID(), emergencyNationId)) {
                                    NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                        "§c[ECONOMIC EMERGENCY] Treasury withdrawals are frozen. Only the nation leader can withdraw.",
                                        treasuryBalance), player);
                                    return;
                                }
                            }

                            // Withdraw from government treasury
                            switch (targetType) {
                                case "NATION" -> manager.getOrCreateNationTreasury(targetUUID).subtract(packet.amount);
                                case "STATE" -> manager.getOrCreateStateTreasury(targetUUID).subtract(packet.amount);
                                case "CITY" -> manager.getOrCreateCityTreasury(targetUUID).subtract(packet.amount);
                                case "COMPANY" -> manager.getOrCreateCompanyTreasury(targetUUID).subtract(packet.amount);
                                case "BANK_DEPOSIT" -> {
                                    var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                                    var bank = bankMgr.getBank(targetUUID);
                                    if (bank != null) {
                                        // Calculate withdrawal fee from the bank's registry entry
                                        com.statecraft.economy.core.Bank registryBank =
                                            manager.getBankRegistry().getBank(targetUUID);
                                        double withdrawalFeeRate = registryBank != null ? registryBank.getWithdrawalFee() : 0;
                                        double withdrawalFee = packet.amount * withdrawalFeeRate;
                                        double totalWithdrawal = packet.amount + withdrawalFee;

                                        double depositorBal = bank.getDepositorBalance(player.getUUID());
                                        if (depositorBal < totalWithdrawal) {
                                            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                                "Insufficient depositor balance" +
                                                (withdrawalFee > 0 ? " (including " + manager.formatCurrency(withdrawalFee) + " withdrawal fee)" : ""),
                                                depositorBal), player);
                                            return;
                                        }

                                        double compTreasury = manager.getCompanyBalance(targetUUID);
                                        if (!bank.subtractDepositorBalance(player.getUUID(), packet.amount, compTreasury)) {
                                            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                                "Withdrawal denied — bank reserve requirements would be breached",
                                                bank.getDepositorBalance(player.getUUID())), player);
                                            return;
                                        }
                                        // Deduct the withdrawal amount from the bank's company treasury
                                        manager.getOrCreateCompanyTreasury(targetUUID).subtract(packet.amount);

                                        // Deduct fee from depositor balance; fee stays in bank treasury as revenue
                                        if (withdrawalFee > 0) {
                                            bank.subtractDepositorBalanceRaw(player.getUUID(), withdrawalFee);
                                            manager.recordTransaction(player.getUUID(),
                                                com.statecraft.economy.core.Transaction.Type.FEE, withdrawalFee, targetUUID,
                                                "Bank withdrawal fee", null, "Bank Fee");
                                            manager.recordTransaction(targetUUID,
                                                com.statecraft.economy.core.Transaction.Type.FEE, withdrawalFee, player.getUUID(),
                                                "Withdrawal fee from " + player.getName().getString(),
                                                null, "Bank Fee System");
                                        }
                                        bankMgr.markDirty();
                                    }
                                }
                            }

                            // Record transaction on the government account with initiator info
                            manager.recordAccountTransaction(targetUUID,
                                com.statecraft.economy.core.Transaction.Type.WITHDRAWAL,
                                packet.amount, player.getUUID(),
                                "ATM withdrawal from " + targetType.toLowerCase() + " treasury" + noteSuffix,
                                player.getUUID(), player.getName().getString());

                            // Record spending against daily limit
                            spendingMgr.recordSpending(player.getUUID(), targetType, targetUUID, packet.amount);

                            double newTreasuryBalance = switch (targetType) {
                                case "NATION" -> manager.getNationBalance(targetUUID);
                                case "STATE" -> manager.getStateBalance(targetUUID);
                                case "CITY" -> manager.getCityBalance(targetUUID);
                                case "COMPANY" -> manager.getCompanyBalance(targetUUID);
                                case "BANK_DEPOSIT" -> {
                                    var bank = com.statecraft.economy.company.BankManager.getInstance().getBank(targetUUID);
                                    yield bank != null ? bank.getDepositorBalance(player.getUUID()) : 0;
                                }
                                default -> 0;
                            };

                            result = new TransactionResult(true,
                                "Withdrew " + manager.formatCurrency(packet.amount) + " from " + targetType.toLowerCase() + " treasury",
                                newTreasuryBalance);
                        } catch (IllegalArgumentException e) {
                            // Invalid UUID, use personal account as fallback
                            result = manager.withdraw(player.getUUID(), packet.amount, "ATM withdrawal" + noteSuffix);
                        }
                    } else {
                        // Personal account withdrawal
                        result = manager.withdraw(player.getUUID(), packet.amount, "ATM withdrawal" + noteSuffix);
                    }

                    if (result.isSuccess()) {
                        // Give currency items to the player
                        var items = manager.convertToItems(packet.amount);

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
                    // Determine source account
                    String sourceAccount = packet.sourceAccount;
                    boolean isSourceGovernment = sourceAccount != null && !sourceAccount.isEmpty()
                        && sourceAccount.contains(":");

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
                        // Transfer from source account to player
                        TransactionResult result;
                        if (isSourceGovernment) {
                            result = transferFromEntity(manager, player, sourceAccount, recipientPlayer.getUUID(), packet.amount, true);
                        } else {
                            result = manager.transfer(player.getUUID(), recipientPlayer.getUUID(),
                                packet.amount, "Transfer via ATM");
                        }
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

                        switch (targetType) {
                            case "PLAYER" -> {
                                // Transfer to player from source account
                                if (isSourceGovernment) {
                                    result = transferFromEntity(manager, player, sourceAccount, targetUUID, packet.amount, true);
                                } else {
                                    result = manager.transfer(player.getUUID(), targetUUID,
                                        packet.amount, "Transfer via ATM");
                                }
                                // Try to notify online player
                                ServerPlayer recipientPlayer = player.server.getPlayerList().getPlayer(targetUUID);
                                if (result.isSuccess() && recipientPlayer != null) {
                                    recipientPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        "§a[Economy] Received " + manager.formatCurrency(packet.amount) + " from " + player.getName().getString()));
                                }
                            }
                            case "NATION" -> {
                                // Transfer to nation treasury
                                if (isSourceGovernment) {
                                    result = transferFromEntity(manager, player, sourceAccount, targetUUID, packet.amount, false);
                                    if (result.isSuccess()) {
                                        manager.getOrCreateNationTreasury(targetUUID).add(packet.amount);
                                        // Record on target government account
                                        manager.recordAccountTransaction(targetUUID,
                                            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
                                            packet.amount, null, "Transfer from government account",
                                            player.getUUID(), player.getName().getString());
                                        result = new TransactionResult(true,
                                            "Transferred " + manager.formatCurrency(packet.amount) + " to nation treasury",
                                            result.getNewBalance());
                                    }
                                } else {
                                    result = transferToEntity(manager, player, targetUUID, packet.amount, "nation");
                                }
                            }
                            case "STATE" -> {
                                // Transfer to state treasury
                                if (isSourceGovernment) {
                                    result = transferFromEntity(manager, player, sourceAccount, targetUUID, packet.amount, false);
                                    if (result.isSuccess()) {
                                        manager.getOrCreateStateTreasury(targetUUID).add(packet.amount);
                                        // Record on target government account
                                        manager.recordAccountTransaction(targetUUID,
                                            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
                                            packet.amount, null, "Transfer from government account",
                                            player.getUUID(), player.getName().getString());
                                        result = new TransactionResult(true,
                                            "Transferred " + manager.formatCurrency(packet.amount) + " to state treasury",
                                            result.getNewBalance());
                                    }
                                } else {
                                    result = transferToEntity(manager, player, targetUUID, packet.amount, "state");
                                }
                            }
                            case "CITY" -> {
                                // Transfer to city treasury
                                if (isSourceGovernment) {
                                    result = transferFromEntity(manager, player, sourceAccount, targetUUID, packet.amount, false);
                                    if (result.isSuccess()) {
                                        manager.getOrCreateCityTreasury(targetUUID).add(packet.amount);
                                        // Record on target government account
                                        manager.recordAccountTransaction(targetUUID,
                                            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
                                            packet.amount, null, "Transfer from government account",
                                            player.getUUID(), player.getName().getString());
                                        result = new TransactionResult(true,
                                            "Transferred " + manager.formatCurrency(packet.amount) + " to city treasury",
                                            result.getNewBalance());
                                    }
                                } else {
                                    result = transferToEntity(manager, player, targetUUID, packet.amount, "city");
                                }
                            }
                            case "COMPANY" -> {
                                // Transfer to company treasury
                                if (isSourceGovernment) {
                                    result = transferFromEntity(manager, player, sourceAccount, targetUUID, packet.amount, false);
                                    if (result.isSuccess()) {
                                        manager.getOrCreateCompanyTreasury(targetUUID).add(packet.amount);
                                        manager.recordAccountTransaction(targetUUID,
                                            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
                                            packet.amount, null, "Transfer from account",
                                            player.getUUID(), player.getName().getString());
                                        result = new TransactionResult(true,
                                            "Transferred " + manager.formatCurrency(packet.amount) + " to company treasury",
                                            result.getNewBalance());
                                    }
                                } else {
                                    result = transferToEntity(manager, player, targetUUID, packet.amount, "company");
                                }
                            }
                            case "BANK_DEPOSIT" -> {
                                // Transfer to a bank deposit account
                                if (isSourceGovernment) {
                                    result = transferFromEntity(manager, player, sourceAccount, targetUUID, packet.amount, false);
                                    if (result.isSuccess()) {
                                        manager.getOrCreateCompanyTreasury(targetUUID).add(packet.amount);
                                        var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                                        var bank = bankMgr.getBank(targetUUID);
                                        if (bank != null && bank.isMember(player.getUUID())) {
                                            bank.addDepositorBalance(player.getUUID(), packet.amount);
                                            bankMgr.markDirty();
                                        }
                                        manager.recordAccountTransaction(targetUUID,
                                            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
                                            packet.amount, null, "Transfer from account",
                                            player.getUUID(), player.getName().getString());
                                        result = new TransactionResult(true,
                                            "Transferred " + manager.formatCurrency(packet.amount) + " to bank deposit",
                                            result.getNewBalance());
                                    }
                                } else {
                                    // Withdraw from player personal, add to bank deposit
                                    double playerBalance = manager.getBalance(player.getUUID());
                                    if (playerBalance < packet.amount) {
                                        result = new TransactionResult(false, "Insufficient funds", playerBalance);
                                    } else {
                                        manager.withdraw(player.getUUID(), packet.amount, "Bank deposit via transfer");
                                        manager.getOrCreateCompanyTreasury(targetUUID).add(packet.amount);
                                        var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                                        var bank = bankMgr.getBank(targetUUID);
                                        if (bank != null && bank.isMember(player.getUUID())) {
                                            bank.addDepositorBalance(player.getUUID(), packet.amount);
                                            bankMgr.markDirty();
                                        }
                                        manager.recordAccountTransaction(targetUUID,
                                            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
                                            packet.amount, player.getUUID(), "Bank deposit from " + player.getName().getString(),
                                            player.getUUID(), player.getName().getString());
                                        result = new TransactionResult(true,
                                            "Deposited " + manager.formatCurrency(packet.amount) + " to bank account",
                                            manager.getBalance(player.getUUID()));
                                    }
                                }
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
                case OPEN_BANK_ACCOUNT -> {
                    // recipient field contains the bank company UUID
                    try {
                        UUID bankCompanyId = UUID.fromString(packet.recipient);
                        var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                        var bank = bankMgr.getBank(bankCompanyId);
                        if (bank == null) {
                            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                "Bank not found", manager.getBalance(player.getUUID())), player);
                            return;
                        }
                        if (bank.isMember(player.getUUID())) {
                            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                                "You already have an account at this bank", manager.getBalance(player.getUUID())), player);
                            return;
                        }
                        bank.openAccount(player.getUUID());
                        bankMgr.markDirty();

                        var company = com.statecraft.company.CompanyManager.getInstance().getCompany(bankCompanyId);
                        String bankName = company != null ? company.getName() : "Bank";

                        // Refresh the player's account list
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                            "§aAccount opened at " + bankName + "! You can now deposit and withdraw.",
                            manager.getBalance(player.getUUID())), player);

                        // Send updated accounts list so the new bank deposit account appears
                        com.statecraft.economy.network.ServerPacketHandler.handleRequestAccounts(
                            new RequestAccountsPacket(), () -> ctx.get());
                    } catch (IllegalArgumentException e) {
                        NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                            "Invalid bank ID", manager.getBalance(player.getUUID())), player);
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
            case "company" -> {
                manager.getOrCreateCompanyTreasury(entityId).add(amount);
            }
            default -> {
                // Shouldn't happen, but refund just in case
                manager.deposit(player.getUUID(), amount, "Transfer refund");
                return new TransactionResult(false, "Unknown entity type", manager.getBalance(player.getUUID()));
            }
        }

        // Record transaction on the government account with initiator info
        manager.recordAccountTransaction(entityId,
            com.statecraft.economy.core.Transaction.Type.TRANSFER_IN,
            amount, player.getUUID(),
            "Transfer from " + player.getName().getString(),
            player.getUUID(), player.getName().getString());

        return new TransactionResult(true,
            "Transferred " + manager.formatCurrency(amount) + " to " + entityType + " treasury",
            manager.getBalance(player.getUUID()));
    }

    /**
     * Helper method to transfer funds from a government entity (nation, state, city)
     * @param depositToPlayer if true, deposits to target player account; if false, just withdraws from source
     */
    private static TransactionResult transferFromEntity(EconomyManager manager, ServerPlayer player,
                                                         String sourceAccount, UUID targetId, double amount, boolean depositToPlayer) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        // Parse source account: "TYPE:uuid"
        String[] sourceParts = sourceAccount.split(":", 2);
        if (sourceParts.length != 2) {
            return new TransactionResult(false, "Invalid source account format", 0);
        }

        String sourceType = sourceParts[0];
        UUID sourceUUID;
        try {
            sourceUUID = UUID.fromString(sourceParts[1]);
        } catch (IllegalArgumentException e) {
            return new TransactionResult(false, "Invalid source account ID", 0);
        }

        // Get source balance
        double sourceBalance = switch (sourceType) {
            case "NATION" -> manager.getNationBalance(sourceUUID);
            case "STATE" -> manager.getStateBalance(sourceUUID);
            case "CITY" -> manager.getCityBalance(sourceUUID);
            case "COMPANY" -> manager.getCompanyBalance(sourceUUID);
            case "BANK_DEPOSIT" -> {
                var bank = com.statecraft.economy.company.BankManager.getInstance().getBank(sourceUUID);
                yield bank != null ? bank.getDepositorBalance(player.getUUID()) : 0;
            }
            default -> 0;
        };

        if (sourceBalance < amount) {
            return new TransactionResult(false, "Insufficient funds in " + sourceType.toLowerCase() + " treasury", sourceBalance);
        }

        // Check daily spending limit
        SpendingLimitManager spendingMgr = SpendingLimitManager.getInstance();
        SpendingLimitManager.GovernmentRole role =
            StateCraftIntegration.getPlayerGovernmentRole(player, sourceType, sourceUUID);
        UUID nationIdForLimit = StateCraftIntegration.getNationIdForAccount(sourceType, sourceUUID);
        String limitError = spendingMgr.checkSpendingLimit(
            player.getUUID(), sourceType, sourceUUID, amount, role, nationIdForLimit, player);
        if (limitError != null) {
            return new TransactionResult(false, limitError, sourceBalance);
        }

        // Check economic emergency — only leader can transfer from government accounts during emergency
        if (sourceType.equals("NATION") || sourceType.equals("STATE") || sourceType.equals("CITY")) {
            UUID emergencyNationId = nationIdForLimit != null ? nationIdForLimit : sourceUUID;
            if (StateCraftIntegration.isEconomicEmergencyActive(emergencyNationId) &&
                !StateCraftIntegration.isNationLeader(player.getUUID(), emergencyNationId)) {
                return new TransactionResult(false,
                    "§c[ECONOMIC EMERGENCY] Treasury transfers are frozen. Only the nation leader can transfer funds.",
                    sourceBalance);
            }
        }

        // Withdraw from source treasury
        switch (sourceType) {
            case "NATION" -> manager.getOrCreateNationTreasury(sourceUUID).subtract(amount);
            case "STATE" -> manager.getOrCreateStateTreasury(sourceUUID).subtract(amount);
            case "CITY" -> manager.getOrCreateCityTreasury(sourceUUID).subtract(amount);
            case "COMPANY" -> manager.getOrCreateCompanyTreasury(sourceUUID).subtract(amount);
            case "BANK_DEPOSIT" -> {
                var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                var bank = bankMgr.getBank(sourceUUID);
                if (bank != null) {
                    double compTreasury = manager.getCompanyBalance(sourceUUID);
                    bank.subtractDepositorBalance(player.getUUID(), amount, compTreasury);
                    manager.getOrCreateCompanyTreasury(sourceUUID).subtract(amount);
                    bankMgr.markDirty();
                }
            }
            default -> {
                return new TransactionResult(false, "Unknown source type: " + sourceType, 0);
            }
        }

        // Record transaction on the source government account with initiator info
        String targetDesc = depositToPlayer ? "Transfer to player" : "Transfer out";
        manager.recordAccountTransaction(sourceUUID,
            com.statecraft.economy.core.Transaction.Type.TRANSFER_OUT,
            amount, targetId,
            targetDesc,
            player.getUUID(), player.getName().getString());

        // Record spending against daily limit
        spendingMgr.recordSpending(player.getUUID(), sourceType, sourceUUID, amount);

        // Deposit to target if requested
        if (depositToPlayer) {
            manager.deposit(targetId, amount, "Transfer from " + sourceType.toLowerCase() + " treasury");
        }

        // Get new source balance for display
        double newSourceBalance = switch (sourceType) {
            case "NATION" -> manager.getNationBalance(sourceUUID);
            case "STATE" -> manager.getStateBalance(sourceUUID);
            case "CITY" -> manager.getCityBalance(sourceUUID);
            case "COMPANY" -> manager.getCompanyBalance(sourceUUID);
            case "BANK_DEPOSIT" -> {
                var bank = com.statecraft.economy.company.BankManager.getInstance().getBank(sourceUUID);
                yield bank != null ? bank.getDepositorBalance(player.getUUID()) : 0;
            }
            default -> 0;
        };

        return new TransactionResult(true,
            "Transferred " + manager.formatCurrency(amount) + " from " + sourceType.toLowerCase() + " treasury",
            newSourceBalance);
    }
}

