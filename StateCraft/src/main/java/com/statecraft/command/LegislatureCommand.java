package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.City;
import com.statecraft.core.Nation;
import com.statecraft.core.State;
import com.statecraft.data.NationSavedData;
import com.statecraft.legislature.Bill;
import com.statecraft.legislature.Legislature;
import com.statecraft.legislature.LegislatureManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Admin commands for managing legislature bills
 * These commands are for server operators only to manage bill states
 *
 * /statecraft admin legislature list <nation> - List all active bills
 * /statecraft admin legislature info <nation> <billNumber> - View bill details
 * /statecraft admin legislature endDebate <nation> <billNumber> - End debate early, start voting
 * /statecraft admin legislature pass <nation> <billNumber> - Force pass (send to leader)
 * /statecraft admin legislature enact <nation> <billNumber> - Force enact into law
 * /statecraft admin legislature fail <nation> <billNumber> - Force fail the bill
 * /statecraft admin legislature veto <nation> <billNumber> - Force veto the bill
 */
public class LegislatureCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> registerAdmin() {
        return Commands.literal("legislature")
            .requires(source -> source.hasPermission(2)) // Require op level 2+
            .then(Commands.literal("list")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .executes(LegislatureCommand::listBills)))
            .then(Commands.literal("info")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("billNumber", StringArgumentType.string())
                        .executes(LegislatureCommand::billInfo))))
            .then(Commands.literal("endDebate")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("billNumber", StringArgumentType.string())
                        .executes(LegislatureCommand::endDebate))))
            .then(Commands.literal("pass")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("billNumber", StringArgumentType.string())
                        .executes(LegislatureCommand::passBill))))
            .then(Commands.literal("enact")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("billNumber", StringArgumentType.string())
                        .executes(LegislatureCommand::enactBill))))
            .then(Commands.literal("fail")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("billNumber", StringArgumentType.string())
                        .executes(LegislatureCommand::failBill))))
            .then(Commands.literal("veto")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("billNumber", StringArgumentType.string())
                        .executes(LegislatureCommand::vetoBill))));
    }

    /**
     * List all active bills for a nation
     */
    private static int listBills(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendSuccess(() -> Component.literal("§7No legislature data for " + nationName), false);
            return 1;
        }

        Collection<Bill> activeBills = legislature.getActiveBills();

        context.getSource().sendSuccess(() -> Component.literal("§6=== Active Bills for " + nationName + " ==="), false);

        if (activeBills.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7No active bills"), false);
        } else {
            for (Bill bill : activeBills) {
                String statusColor = getStatusColor(bill.getStatus());
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7" + bill.getBillNumber() + " §f" + bill.getTitle() + " " + statusColor + "[" + bill.getStatus() + "]"
                ), false);
            }
        }

        context.getSource().sendSuccess(() -> Component.literal("§7Total: " + activeBills.size() + " active bill(s)"), false);
        return 1;
    }

    /**
     * Show detailed info about a specific bill
     */
    private static int billInfo(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        String billNumber = StringArgumentType.getString(context, "billNumber");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendFailure(Component.literal("§cNo legislature data for " + nationName));
            return 0;
        }

        Bill bill = findBillByNumber(legislature, billNumber);
        if (bill == null) {
            context.getSource().sendFailure(Component.literal("§cBill not found: " + billNumber));
            return 0;
        }

        String statusColor = getStatusColor(bill.getStatus());

        context.getSource().sendSuccess(() -> Component.literal("§6=== Bill Info: " + bill.getBillNumber() + " ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Title: §f" + bill.getTitle()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Author: §f" + bill.getAuthorName()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Status: " + statusColor + bill.getStatus()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Description: §f" + bill.getDescription()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Policy Changes: §f" + bill.getPolicyChanges().size()), false);

        if (bill.getStatus() == Bill.Status.VOTING || bill.getStatus() == Bill.Status.PASSED ||
            bill.getStatus() == Bill.Status.FAILED || bill.getStatus() == Bill.Status.ENACTED) {
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Votes: §aYes: " + bill.getYesVotes() + " §cNo: " + bill.getNoVotes() + " §7Abstain: " + bill.getAbstainVotes()
            ), false);
        }

        if (bill.isVetoProof()) {
            context.getSource().sendSuccess(() -> Component.literal("§e⚠ Veto-proof majority"), false);
        }

        return 1;
    }

    /**
     * End debate early and start voting
     */
    private static int endDebate(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        String billNumber = StringArgumentType.getString(context, "billNumber");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendFailure(Component.literal("§cNo legislature data for " + nationName));
            return 0;
        }

        Bill bill = findBillByNumber(legislature, billNumber);
        if (bill == null) {
            context.getSource().sendFailure(Component.literal("§cBill not found: " + billNumber));
            return 0;
        }

        if (bill.getStatus() != Bill.Status.DEBATE) {
            context.getSource().sendFailure(Component.literal("§cBill is not in debate stage! Current status: " + bill.getStatus()));
            return 0;
        }

        // Start voting with 24 hour period
        long votingDuration = 24 * 60 * 60 * 1000L;
        bill.startVoting(System.currentTimeMillis() + votingDuration);

        saveData(context);
        notifyLegislature(context, nation, legislature,
            "§6[Admin] §eDebate ended early on: §f" + bill.getTitle() + " §7- Voting has begun!");

        context.getSource().sendSuccess(() -> Component.literal(
            "§aDebate ended for bill " + billNumber + ". Voting has begun."
        ), true);

        return 1;
    }

    /**
     * Force pass the bill (send to leader for action)
     */
    private static int passBill(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        String billNumber = StringArgumentType.getString(context, "billNumber");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendFailure(Component.literal("§cNo legislature data for " + nationName));
            return 0;
        }

        Bill bill = findBillByNumber(legislature, billNumber);
        if (bill == null) {
            context.getSource().sendFailure(Component.literal("§cBill not found: " + billNumber));
            return 0;
        }

        if (bill.getStatus() != Bill.Status.DEBATE && bill.getStatus() != Bill.Status.VOTING) {
            context.getSource().sendFailure(Component.literal("§cBill cannot be passed from current status: " + bill.getStatus()));
            return 0;
        }

        // Use reflection to set status since it's not publicly settable
        setStatus(bill, Bill.Status.PASSED);

        saveData(context);
        notifyLegislature(context, nation, legislature,
            "§6[Admin] §aBill passed by admin: §f" + bill.getTitle() + " §7- Awaiting leader action");

        // Notify leader
        notifyLeader(context, nation, "§6[Legislature] §aBill '" + bill.getTitle() + "' awaits your action (sign or veto)");

        context.getSource().sendSuccess(() -> Component.literal(
            "§aBill " + billNumber + " has been passed. Awaiting leader action."
        ), true);

        return 1;
    }

    /**
     * Force enact the bill into law
     */
    private static int enactBill(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        String billNumber = StringArgumentType.getString(context, "billNumber");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendFailure(Component.literal("§cNo legislature data for " + nationName));
            return 0;
        }

        Bill bill = findBillByNumber(legislature, billNumber);
        if (bill == null) {
            context.getSource().sendFailure(Component.literal("§cBill not found: " + billNumber));
            return 0;
        }

        // Set status to enacted
        setStatus(bill, Bill.Status.ENACTED);

        // Apply policy changes to the nation
        applyPolicyChanges(nation, bill);

        // Add to law codex - create a Law from the Bill
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
            "" // fullText
        );
        legislature.getCodex().addLaw(law);

        // Archive the bill
        legislature.archiveBill(bill.getBillId());

        saveData(context);
        notifyLegislature(context, nation, legislature,
            "§6[Admin] §2Bill enacted into law: §f" + bill.getTitle());

        context.getSource().sendSuccess(() -> Component.literal(
            "§aBill " + billNumber + " has been enacted into law."
        ), true);

        return 1;
    }

    /**
     * Force fail the bill
     */
    private static int failBill(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        String billNumber = StringArgumentType.getString(context, "billNumber");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendFailure(Component.literal("§cNo legislature data for " + nationName));
            return 0;
        }

        Bill bill = findBillByNumber(legislature, billNumber);
        if (bill == null) {
            context.getSource().sendFailure(Component.literal("§cBill not found: " + billNumber));
            return 0;
        }

        // Set status to failed
        setStatus(bill, Bill.Status.FAILED);

        // Archive the bill
        legislature.archiveBill(bill.getBillId());

        saveData(context);
        notifyLegislature(context, nation, legislature,
            "§6[Admin] §cBill failed: §f" + bill.getTitle());

        context.getSource().sendSuccess(() -> Component.literal(
            "§cBill " + billNumber + " has been marked as failed."
        ), true);

        return 1;
    }

    /**
     * Force veto the bill
     */
    private static int vetoBill(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        String billNumber = StringArgumentType.getString(context, "billNumber");

        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation not found: " + nationName));
            return 0;
        }

        Legislature legislature = LegislatureManager.getInstance().getLegislature(nation.getId());
        if (legislature == null) {
            context.getSource().sendFailure(Component.literal("§cNo legislature data for " + nationName));
            return 0;
        }

        Bill bill = findBillByNumber(legislature, billNumber);
        if (bill == null) {
            context.getSource().sendFailure(Component.literal("§cBill not found: " + billNumber));
            return 0;
        }

        // Set status to vetoed
        setStatus(bill, Bill.Status.VETOED);

        // Archive the bill
        legislature.archiveBill(bill.getBillId());

        saveData(context);
        notifyLegislature(context, nation, legislature,
            "§6[Admin] §cBill vetoed: §f" + bill.getTitle());

        context.getSource().sendSuccess(() -> Component.literal(
            "§cBill " + billNumber + " has been vetoed."
        ), true);

        return 1;
    }

    // Helper methods

    private static Bill findBillByNumber(Legislature legislature, String billNumber) {
        for (Bill bill : legislature.getActiveBills()) {
            if (bill.getBillNumber().equalsIgnoreCase(billNumber)) {
                return bill;
            }
        }
        // Also check history
        for (Bill bill : legislature.getBillHistory()) {
            if (bill.getBillNumber().equalsIgnoreCase(billNumber)) {
                return bill;
            }
        }
        return null;
    }

    private static String getStatusColor(Bill.Status status) {
        return switch (status) {
            case DRAFT -> "§7";
            case DEBATE -> "§b";
            case VOTING -> "§e";
            case PASSED -> "§a";
            case ENACTED -> "§2";
            case VETOED -> "§c";
            case FAILED -> "§4";
            case EXPIRED -> "§8";
        };
    }

    private static void setStatus(Bill bill, Bill.Status newStatus) {
        try {
            java.lang.reflect.Field statusField = Bill.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(bill, newStatus);
        } catch (Exception e) {
            // Fallback - this shouldn't happen
            e.printStackTrace();
        }
    }

    private static void applyPolicyChanges(Nation nation, Bill bill) {
        for (var entry : bill.getPolicyChanges().entrySet()) {
            try {
                switch (entry.getKey()) {
                    case STATE_PASS_THROUGH_RATE -> {
                        double rate = Double.parseDouble(entry.getValue());
                        nation.setStatePassThroughRate(rate);
                    }
                    case BASE_CHUNK_VALUE -> {
                        double value = Double.parseDouble(entry.getValue());
                        nation.setBaseChunkValue(value);
                    }
                    case MAX_STATES_PER_NATION -> {
                        int max = Integer.parseInt(entry.getValue());
                        nation.setMaxStates(max);
                    }
                    case MAX_CITIES_PER_STATE -> {
                        int max = Integer.parseInt(entry.getValue());
                        for (State state : nation.getAllStates()) {
                            state.setMaxCities(max);
                        }
                        nation.setDefaultMaxCitiesPerState(max);
                    }
                    case MAX_CHUNKS_PER_CITY -> {
                        int max = Integer.parseInt(entry.getValue());
                        for (State state : nation.getAllStates()) {
                            for (City city : state.getAllCities()) {
                                city.setMaxChunks(max);
                            }
                        }
                        nation.setMaxChunksPerCity(max);
                    }
                    case MAX_CHUNKS_PER_PLAYER -> {
                        int max = Integer.parseInt(entry.getValue());
                        nation.setMaxChunksPerPlayer(max);
                    }
                    case OPEN_NATION -> {
                        boolean open = Boolean.parseBoolean(entry.getValue());
                        nation.setOpen(open);
                    }
                    case OPEN_BORDERS -> {
                        boolean open = Boolean.parseBoolean(entry.getValue());
                        nation.setOpenBorders(open);
                    }
                    default -> {
                        // Custom laws and diplomacy handled by LegislatureManager
                    }
                }
            } catch (Exception e) {
                // Log but continue with other changes
            }
        }
    }

    private static void saveData(CommandContext<CommandSourceStack> context) {
        try {
            ServerLevel level = context.getSource().getLevel();
            NationSavedData.get(level).markForSave();
        } catch (Exception e) {
            // Ignore
        }
    }

    private static void notifyLegislature(CommandContext<CommandSourceStack> context, Nation nation,
                                          Legislature legislature, String message) {
        try {
            Set<UUID> members = legislature.getVotingMembers(nation);
            for (UUID memberId : members) {
                ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(memberId);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private static void notifyLeader(CommandContext<CommandSourceStack> context, Nation nation, String message) {
        try {
            ServerPlayer leader = context.getSource().getServer().getPlayerList().getPlayer(nation.getLeaderId());
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(message));
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}



