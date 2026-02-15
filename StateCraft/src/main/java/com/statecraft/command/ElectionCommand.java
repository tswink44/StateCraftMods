package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.core.*;
import com.statecraft.data.NationSavedData;
import com.statecraft.integration.IntegrationRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commands for nation elections
 * /sc election status - View current election status
 * /sc election vote <candidate> - Cast vote
 * /sc election run - Register as candidate
 * /sc election candidates - List candidates
 * /sc election history - View past winners
 */
public class ElectionCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("election")
            .then(Commands.literal("status")
                .executes(ElectionCommand::status))
            .then(Commands.literal("vote")
                .then(Commands.argument("candidate", StringArgumentType.string())
                    .executes(ElectionCommand::vote)))
            .then(Commands.literal("run")
                .executes(ElectionCommand::runForElection))
            .then(Commands.literal("candidates")
                .executes(ElectionCommand::listCandidates))
            .then(Commands.literal("history")
                .executes(ElectionCommand::history));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerAdmin() {
        return Commands.literal("election")
            .then(Commands.literal("start")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .requires(source -> source.hasPermission(2))
                    .executes(ElectionCommand::adminStart)))
            .then(Commands.literal("end")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .requires(source -> source.hasPermission(2))
                    .executes(ElectionCommand::adminEnd)))
            .then(Commands.literal("setleader")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(ElectionCommand::adminSetLeader))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());

            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            ElectionManager electionManager = ElectionManager.getInstance();
            Election election = electionManager.getActiveElection(nation.getId());

            if (election != null && election.getStatus() == Election.Status.ACTIVE) {
                long remaining = election.getRemainingTime();
                long hours = remaining / (60 * 60 * 1000);
                long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);

                context.getSource().sendSuccess(() -> Component.literal(
                    "§6=== Election Status for " + nation.getName() + " ==="), false);
                context.getSource().sendSuccess(() -> Component.literal(
                    "§eStatus: §aVoting in progress"), false);
                context.getSource().sendSuccess(() -> Component.literal(
                    "§eTime remaining: §f" + hours + "h " + minutes + "m"), false);
                context.getSource().sendSuccess(() -> Component.literal(
                    "§eCandidates: §f" + election.getCandidateCount()), false);
                context.getSource().sendSuccess(() -> Component.literal(
                    "§eVotes cast: §f" + election.getVoteCount()), false);

                if (election.hasVoted(player.getUUID())) {
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§aYou have voted in this election."), false);
                } else {
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§cYou have not voted yet! Use /sc election vote <candidate>"), false);
                }
            } else {
                Long nextTime = electionManager.getNextElectionTime(nation.getId());
                if (nextTime != null) {
                    long timeUntil = nextTime - System.currentTimeMillis();
                    long days = timeUntil / (24 * 60 * 60 * 1000);
                    long hours = (timeUntil % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);

                    context.getSource().sendSuccess(() -> Component.literal(
                        "§6=== Election Status for " + nation.getName() + " ==="), false);
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§eStatus: §7No active election"), false);
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§eNext election in: §f" + days + " days, " + hours + " hours"), false);
                } else {
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§7No elections scheduled for " + nation.getName()), false);
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int vote(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String candidateName = StringArgumentType.getString(context, "candidate");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            ElectionManager electionManager = ElectionManager.getInstance();
            Election election = electionManager.getActiveElection(nation.getId());

            if (election == null || election.getStatus() != Election.Status.ACTIVE) {
                context.getSource().sendFailure(Component.literal("There is no active election!"));
                return 0;
            }

            // Find candidate by name
            UUID candidateId = null;
            for (Map.Entry<UUID, String> entry : election.getCandidates().entrySet()) {
                if (entry.getValue().equalsIgnoreCase(candidateName)) {
                    candidateId = entry.getKey();
                    break;
                }
            }

            if (candidateId == null) {
                context.getSource().sendFailure(Component.literal(
                    "Candidate '" + candidateName + "' not found. Use /sc election candidates to see the list."));
                return 0;
            }

            String error = electionManager.castVote(nation, player.getUUID(), candidateId);
            if (error != null) {
                context.getSource().sendFailure(Component.literal(error));
                return 0;
            }

            markDataDirty(context);
            final String finalCandidateName = election.getCandidates().get(candidateId);
            context.getSource().sendSuccess(() -> Component.literal(
                "§aYou voted for §e" + finalCandidateName + "§a!"), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int runForElection(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            ElectionManager electionManager = ElectionManager.getInstance();
            Election election = electionManager.getActiveElection(nation.getId());

            if (election == null || election.getStatus() != Election.Status.ACTIVE) {
                context.getSource().sendFailure(Component.literal("There is no active election to run in!"));
                return 0;
            }

            // Show fee info if applicable
            double fee = StateCraftConfig.ELECTION_CANDIDATE_FEE.get();
            if (fee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                double balance = IntegrationRegistry.getPlayerBalance(player.getUUID());
                if (balance < fee) {
                    context.getSource().sendFailure(Component.literal(
                        "§cInsufficient funds! Candidate registration costs " +
                        IntegrationRegistry.formatCurrency(fee) + ". You have " +
                        IntegrationRegistry.formatCurrency(balance) + "."));
                    return 0;
                }
            }

            String error = electionManager.registerCandidate(nation, player.getUUID(),
                player.getName().getString(), player.server);

            if (error != null) {
                context.getSource().sendFailure(Component.literal(error));
                return 0;
            }

            markDataDirty(context);

            String feeMsg = "";
            if (fee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                feeMsg = " (Fee: " + IntegrationRegistry.formatCurrency(fee) + ")";
            }
            final String finalFeeMsg = feeMsg;
            context.getSource().sendSuccess(() -> Component.literal(
                "§aYou are now a candidate in the election!" + finalFeeMsg), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int listCandidates(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            ElectionManager electionManager = ElectionManager.getInstance();
            Election election = electionManager.getActiveElection(nation.getId());

            if (election == null) {
                context.getSource().sendFailure(Component.literal("There is no active election!"));
                return 0;
            }

            Map<UUID, String> candidates = election.getCandidates();

            context.getSource().sendSuccess(() -> Component.literal(
                "§6=== Candidates for " + nation.getName() + " Election ==="), false);

            if (candidates.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7No candidates registered yet."), false);
            } else {
                UUID incumbentId = election.getIncumbentId();
                for (Map.Entry<UUID, String> entry : candidates.entrySet()) {
                    String suffix = entry.getKey().equals(incumbentId) ? " §7(Incumbent)" : "";
                    final String candidateLine = "§e• §f" + entry.getValue() + suffix;
                    context.getSource().sendSuccess(() -> Component.literal(candidateLine), false);
                }
            }

            context.getSource().sendSuccess(() -> Component.literal(
                "§7Use /sc election vote <name> to vote"), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int history(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            List<ElectionResult> history = ElectionManager.getInstance().getElectionHistory(nation.getId());

            context.getSource().sendSuccess(() -> Component.literal(
                "§6=== Election History for " + nation.getName() + " ==="), false);

            if (history.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7No election history available."), false);
            } else {
                int i = 1;
                for (ElectionResult result : history) {
                    long daysAgo = (System.currentTimeMillis() - result.timestamp()) / (24 * 60 * 60 * 1000);
                    final String line = "§e" + i + ". §f" + result.winnerName() +
                        " §7(" + result.voteCount() + "/" + result.totalVoters() + " votes, " +
                        daysAgo + " days ago)";
                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                    i++;
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    // ==================== Admin Commands ====================

    private static int adminStart(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);

        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation '" + nationName + "' not found!"));
            return 0;
        }

        Election existing = ElectionManager.getInstance().getActiveElection(nation.getId());
        if (existing != null && existing.getStatus() == Election.Status.ACTIVE) {
            context.getSource().sendFailure(Component.literal("An election is already active for this nation!"));
            return 0;
        }

        ElectionManager.getInstance().startElection(nation, context.getSource().getServer());
        markDataDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aStarted election for nation §e" + nationName), true);
        return 1;
    }

    private static int adminEnd(CommandContext<CommandSourceStack> context) {
        String nationName = StringArgumentType.getString(context, "nation");
        Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);

        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation '" + nationName + "' not found!"));
            return 0;
        }

        Election election = ElectionManager.getInstance().getActiveElection(nation.getId());
        if (election == null || election.getStatus() != Election.Status.ACTIVE) {
            context.getSource().sendFailure(Component.literal("No active election for this nation!"));
            return 0;
        }

        ElectionManager.getInstance().endElection(nation, context.getSource().getServer(), true);
        markDataDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§aEnded election for nation §e" + nationName), true);
        return 1;
    }

    private static int adminSetLeader(CommandContext<CommandSourceStack> context) {
        try {
            String nationName = StringArgumentType.getString(context, "nation");
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");

            Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("Nation '" + nationName + "' not found!"));
                return 0;
            }

            ElectionManager.getInstance().forceSetLeader(
                nation,
                targetPlayer.getUUID(),
                targetPlayer.getName().getString(),
                context.getSource().getServer()
            );
            markDataDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aSet §e" + targetPlayer.getName().getString() + "§a as leader of §e" + nationName), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to set leader: " + e.getMessage()));
            return 0;
        }
    }

    private static void markDataDirty(CommandContext<CommandSourceStack> context) {
        try {
            ServerLevel level = context.getSource().getLevel();
            NationSavedData.get(level).markForSave();
        } catch (Exception ignored) {}
    }
}


