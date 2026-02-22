package com.statecraft.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.contract.Contract;
import com.statecraft.contract.ContractBid;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.Nation;
import com.statecraft.data.NationSavedData;
import com.statecraft.integration.IntegrationRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Admin commands for government contract management
 * /statecraft admin contract list [nation] - List all contracts
 * /statecraft admin contract info <contractId> - View contract details
 * /statecraft admin contract create <nation> <title> <budget> - Create a contract
 * /statecraft admin contract delete <contractId> - Delete a contract
 * /statecraft admin contract setbudget <contractId> <amount> - Set contract budget
 * /statecraft admin contract setstatus <contractId> <status> - Set contract status
 * /statecraft admin contract award <contractId> <player> - Award contract to player
 * /statecraft admin contract complete <contractId> - Force complete a contract
 * /statecraft admin contract cancel <contractId> - Cancel a contract
 * /statecraft admin contract refund <contractId> - Refund escrow to nation
 * /statecraft admin contract progress <contractId> <percent> - Set contract progress
 */
public class ContractCommand {

    /**
     * Register admin contract subcommands
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerAdmin() {
        return Commands.literal("contract")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("list")
                .executes(ContractCommand::listAllContracts)
                .then(Commands.argument("nation", StringArgumentType.string())
                    .executes(ContractCommand::listNationContracts)))
            .then(Commands.literal("info")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ContractCommand::contractInfo)))
            .then(Commands.literal("create")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("title", StringArgumentType.greedyString())
                        .executes(ctx -> createContract(ctx, 100000))
                        .then(Commands.argument("budget", DoubleArgumentType.doubleArg(0))
                            .executes(ContractCommand::createContractWithBudget)))))
            .then(Commands.literal("delete")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ContractCommand::deleteContract)))
            .then(Commands.literal("setbudget")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(ContractCommand::setBudget))))
            .then(Commands.literal("setstatus")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .then(Commands.argument("status", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Contract.Status status : Contract.Status.values()) {
                                builder.suggest(status.name());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ContractCommand::setStatus))))
            .then(Commands.literal("award")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ContractCommand::awardContract))))
            .then(Commands.literal("complete")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ContractCommand::forceComplete)))
            .then(Commands.literal("cancel")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ContractCommand::cancelContract)))
            .then(Commands.literal("refund")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ContractCommand::refundEscrow)))
            .then(Commands.literal("progress")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                        .executes(ContractCommand::setProgress))))
            .then(Commands.literal("openbidding")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ctx -> openBidding(ctx, 7))
                    .then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                        .executes(ContractCommand::openBiddingWithDuration))))
            .then(Commands.literal("closebidding")
                .then(Commands.argument("contractId", StringArgumentType.string())
                    .executes(ContractCommand::closeBidding)));
    }

    private static int listAllContracts(CommandContext<CommandSourceStack> context) {
        ContractManager manager = ContractManager.getInstance();
        Collection<Nation> nations = ChunkClaimManager.getInstance().getAllNations();

        context.getSource().sendSuccess(() -> Component.literal("§6=== All Government Contracts ==="), false);

        int totalCount = 0;
        for (Nation nation : nations) {
            List<Contract> contracts = manager.getNationContracts(nation.getId());
            if (!contracts.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("§e" + nation.getName() + ":"), false);
                for (Contract contract : contracts) {
                    String statusColor = getStatusColor(contract.getStatus());
                    context.getSource().sendSuccess(() -> Component.literal(
                        "  §7" + contract.getContractNumber() + " §f" + contract.getTitle() +
                        " " + statusColor + "[" + contract.getStatus() + "] " +
                        "§a$" + String.format("%.2f", contract.getTotalBudget())
                    ), false);
                }
                totalCount += contracts.size();
            }
        }

        if (totalCount == 0) {
            context.getSource().sendSuccess(() -> Component.literal("§7No contracts found."), false);
        } else {
            int count = totalCount;
            context.getSource().sendSuccess(() -> Component.literal("§7Total: " + count + " contracts"), false);
        }

        return 1;
    }

    private static int listNationContracts(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);

        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation not found: " + nationName));
            return 0;
        }

        ContractManager manager = ContractManager.getInstance();
        List<Contract> contracts = manager.getNationContracts(nation.getId());

        context.getSource().sendSuccess(() -> Component.literal("§6=== Contracts for " + nation.getName() + " ==="), false);

        if (contracts.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7No contracts found."), false);
        } else {
            for (Contract contract : contracts) {
                String statusColor = getStatusColor(contract.getStatus());
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7" + contract.getContractNumber() + " §f" + contract.getTitle() +
                    " " + statusColor + "[" + contract.getStatus() + "] " +
                    "§a$" + String.format("%.2f", contract.getTotalBudget())
                ), false);
                context.getSource().sendSuccess(() -> Component.literal(
                    "  §8ID: " + contract.getContractId().toString().substring(0, 8) + "..."
                ), false);
            }
        }

        return 1;
    }

    private static int contractInfo(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        Nation nation = ChunkClaimManager.getInstance().getNation(contract.getNationId());
        String nationName = nation != null ? nation.getName() : "Unknown";

        context.getSource().sendSuccess(() -> Component.literal("§6=== Contract Details ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Number: §f" + contract.getContractNumber()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7ID: §f" + contract.getContractId()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Nation: §f" + nationName), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Title: §f" + contract.getTitle()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Status: " + getStatusColor(contract.getStatus()) + contract.getStatus()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Budget: §a$" + String.format("%.2f", contract.getTotalBudget())), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Escrow: §e$" + String.format("%.2f", contract.getEscrowBalance())), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Bond: §c$" + String.format("%.2f", contract.getBondAmount())), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Progress: §b" + contract.getProgressPercent() + "%"), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Compensation: §f" + contract.getCompensationType()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Creator: §f" + contract.getCreatorName()), false);

        if (contract.getContractorId() != null) {
            context.getSource().sendSuccess(() -> Component.literal("§7Contractor: §f" + contract.getContractorName()), false);
        }

        context.getSource().sendSuccess(() -> Component.literal("§7Bids: §f" + contract.getBids().size()), false);

        if (!contract.getDescription().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7Description: §f" + contract.getDescription()), false);
        }

        return 1;
    }

    private static int createContract(CommandContext<CommandSourceStack> context, double defaultBudget) {
        String nationName = StringArgumentType.getString(context, "nation");
        String title = StringArgumentType.getString(context, "title");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation not found: " + nationName));
            return 0;
        }

        ContractManager manager = ContractManager.getInstance();

        String creatorName = "Admin";
        UUID creatorId = null;
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            creatorName = player.getName().getString();
            creatorId = player.getUUID();
        }

        Contract contract = manager.createContract(nation.getId(), title, creatorId, creatorName);
        contract.setTotalBudget(defaultBudget);

        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aCreated contract §f" + contract.getContractNumber() + "§a for " + nationName +
            " with budget §e$" + String.format("%.2f", defaultBudget)
        ), true);
        context.getSource().sendSuccess(() -> Component.literal(
            "§7ID: " + contract.getContractId()
        ), false);

        return 1;
    }

    private static int createContractWithBudget(CommandContext<CommandSourceStack> context) {
        double budget = DoubleArgumentType.getDouble(context, "budget");
        String nationName = StringArgumentType.getString(context, "nation");
        String title = StringArgumentType.getString(context, "title");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation not found: " + nationName));
            return 0;
        }

        ContractManager manager = ContractManager.getInstance();

        String creatorName = "Admin";
        UUID creatorId = null;
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            creatorName = player.getName().getString();
            creatorId = player.getUUID();
        }

        Contract contract = manager.createContract(nation.getId(), title, creatorId, creatorName);
        contract.setTotalBudget(budget);
        contract.depositToEscrow(budget);

        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aCreated contract §f" + contract.getContractNumber() + "§a for " + nationName +
            " with budget §e$" + String.format("%.2f", budget)
        ), true);

        return 1;
    }

    private static int deleteContract(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        String contractNumber = contract.getContractNumber();
        ContractManager.getInstance().removeContract(contract.getContractId());
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§cDeleted contract §f" + contractNumber
        ), true);

        return 1;
    }

    private static int setBudget(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        contract.setTotalBudget(amount);
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aSet budget for §f" + contract.getContractNumber() + "§a to §e$" + String.format("%.2f", amount)
        ), true);

        return 1;
    }

    private static int setStatus(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        String statusStr = StringArgumentType.getString(context, "status");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        try {
            Contract.Status newStatus = Contract.Status.valueOf(statusStr.toUpperCase());
            contract.setStatus(newStatus);
            markDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aSet status for §f" + contract.getContractNumber() + "§a to " +
                getStatusColor(newStatus) + newStatus
            ), true);

            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Invalid status: " + statusStr));
            return 0;
        }
    }

    private static int awardContract(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");

            contract.setContractorId(player.getUUID());
            contract.setContractorName(player.getName().getString());
            contract.setStatus(Contract.Status.ACTIVE);
            contract.setStartTime(System.currentTimeMillis());
            contract.setDeadline(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)); // 30 days default

            markDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aAwarded contract §f" + contract.getContractNumber() + "§a to §e" + player.getName().getString()
            ), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found!"));
            return 0;
        }
    }

    private static int forceComplete(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        contract.setProgressPercent(100);
        contract.setStatus(Contract.Status.COMPLETED);
        contract.setCompletedTime(System.currentTimeMillis());

        // Pay out remaining escrow to contractor
        if (contract.getContractorId() != null && contract.getEscrowBalance() > 0) {
            double payout = contract.getEscrowBalance();
            if (IntegrationRegistry.hasEconomyIntegration()) {
                IntegrationRegistry.depositToPlayer(contract.getContractorId(), payout,
                    "Contract completion: " + contract.getContractNumber());
            }
            contract.recordPayment(payout);
        }

        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aForce completed contract §f" + contract.getContractNumber()
        ), true);

        return 1;
    }

    private static int cancelContract(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        contract.setStatus(Contract.Status.CANCELLED);
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§cCancelled contract §f" + contract.getContractNumber()
        ), true);

        return 1;
    }

    private static int refundEscrow(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        double escrow = contract.getEscrowBalance();
        if (escrow <= 0) {
            context.getSource().sendFailure(Component.literal("No escrow balance to refund."));
            return 0;
        }

        Nation nation = ChunkClaimManager.getInstance().getNation(contract.getNationId());
        if (nation != null && IntegrationRegistry.hasEconomyIntegration()) {
            IntegrationRegistry.depositToNation(nation.getName(), escrow);
        }

        contract.withdrawFromEscrow(escrow);
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aRefunded §e$" + String.format("%.2f", escrow) + "§a from contract " + contract.getContractNumber() +
            " to nation treasury"
        ), true);

        return 1;
    }

    private static int setProgress(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        int percent = IntegerArgumentType.getInteger(context, "percent");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        contract.setProgressPercent(percent);
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aSet progress for §f" + contract.getContractNumber() + "§a to §b" + percent + "%"
        ), true);

        return 1;
    }

    private static int openBidding(CommandContext<CommandSourceStack> context, int days) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        long durationMs = days * 24L * 60 * 60 * 1000;
        contract.openForBidding(durationMs);
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aOpened bidding for §f" + contract.getContractNumber() + "§a for " + days + " days"
        ), true);

        return 1;
    }

    private static int openBiddingWithDuration(CommandContext<CommandSourceStack> context) {
        int days = IntegerArgumentType.getInteger(context, "days");
        return openBidding(context, days);
    }

    private static int closeBidding(CommandContext<CommandSourceStack> context) {
        String contractIdStr = StringArgumentType.getString(context, "contractId");
        Contract contract = findContract(contractIdStr);

        if (contract == null) {
            context.getSource().sendFailure(Component.literal("Contract not found: " + contractIdStr));
            return 0;
        }

        contract.closeBidding();
        markDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aClosed bidding for §f" + contract.getContractNumber() + "§a with " +
            contract.getBids().size() + " bids"
        ), true);

        return 1;
    }

    // ==================== Helper Methods ====================

    private static Contract findContract(String identifier) {
        ContractManager manager = ContractManager.getInstance();

        // Try as UUID first
        try {
            UUID contractId = UUID.fromString(identifier);
            Contract contract = manager.getContract(contractId);
            if (contract != null) return contract;
        } catch (IllegalArgumentException ignored) {}

        // Try as partial UUID (first 8 chars)
        if (identifier.length() >= 8) {
            for (Nation nation : ChunkClaimManager.getInstance().getAllNations()) {
                for (Contract contract : manager.getNationContracts(nation.getId())) {
                    if (contract.getContractId().toString().startsWith(identifier)) {
                        return contract;
                    }
                }
            }
        }

        // Try as contract number
        for (Nation nation : ChunkClaimManager.getInstance().getAllNations()) {
            for (Contract contract : manager.getNationContracts(nation.getId())) {
                if (contract.getContractNumber().equalsIgnoreCase(identifier)) {
                    return contract;
                }
            }
        }

        return null;
    }

    private static String getStatusColor(Contract.Status status) {
        return switch (status) {
            case DRAFT -> "§7";
            case BIDDING -> "§e";
            case PENDING_APPROVAL -> "§6";
            case ACTIVE -> "§b";
            case COMPLETED -> "§a";
            case CANCELLED -> "§c";
            case FAILED -> "§4";
        };
    }

    private static void markDirty(CommandContext<CommandSourceStack> context) {
        ContractManager.getInstance().markDirty();
        ServerLevel level = context.getSource().getLevel();
        NationSavedData.get(level).markForSave();
    }
}

