package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.Invitation;
import com.statecraft.core.InvitationManager;
import com.statecraft.core.Nation;
import com.statecraft.data.NationSavedData;
import com.statecraft.integration.IntegrationRegistry;
import com.statecraft.mail.MailManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Commands for nation management
 * /statecraft nation create <name>
 * /statecraft nation info [name]
 * /statecraft nation list
 * /statecraft nation invite <player>
 * /statecraft nation accept [nation]
 * /statecraft nation deny [nation]
 * /statecraft nation invites
 * /statecraft nation kick <player>
 * /statecraft nation leave
 * /statecraft nation disband
 * /statecraft nation setopen <true|false>
 * /statecraft nation join <nation>
 */
public class NationCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("nation")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(NationCommand::createNation)))
            .then(Commands.literal("info")
                .executes(NationCommand::infoSelf)
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(NationCommand::infoOther)))
            .then(Commands.literal("list")
                .executes(NationCommand::listNations))
            .then(Commands.literal("invite")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(NationCommand::invitePlayer)))
            .then(Commands.literal("accept")
                .executes(NationCommand::acceptInvite)
                .then(Commands.argument("nation", StringArgumentType.string())
                    .executes(NationCommand::acceptInviteNamed)))
            .then(Commands.literal("deny")
                .executes(NationCommand::denyInvite)
                .then(Commands.argument("nation", StringArgumentType.string())
                    .executes(NationCommand::denyInviteNamed)))
            .then(Commands.literal("invites")
                .executes(NationCommand::listInvites))
            .then(Commands.literal("join")
                .then(Commands.argument("nation", StringArgumentType.string())
                    .executes(NationCommand::joinNation)))
            .then(Commands.literal("kick")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(NationCommand::kickPlayer)))
            .then(Commands.literal("leave")
                .executes(NationCommand::leaveNation))
            .then(Commands.literal("disband")
                .executes(NationCommand::disbandNation))
            .then(Commands.literal("setopen")
                .then(Commands.argument("open", StringArgumentType.word())
                    .executes(NationCommand::setOpen)));
    }

    private static int createNation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");

            // Validate name
            if (name.length() < 3 || name.length() > 24) {
                context.getSource().sendFailure(Component.literal("Nation name must be 3-24 characters!"));
                return 0;
            }

            // Check creation fee
            double creationFee = StateCraftConfig.NATION_CREATION_FEE.get();
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                double playerBalance = IntegrationRegistry.getPlayerBalance(player.getUUID());
                if (playerBalance < creationFee) {
                    context.getSource().sendFailure(Component.literal(
                        "§cInsufficient funds! Nation creation costs " + IntegrationRegistry.formatCurrency(creationFee) +
                        ". You have " + IntegrationRegistry.formatCurrency(playerBalance) + "."));
                    return 0;
                }
            }

            Nation nation = ChunkClaimManager.getInstance().createNation(name, player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("Could not create nation. Name may be taken or you're already in a nation."));
                return 0;
            }

            // Charge creation fee after successful creation
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                boolean charged = IntegrationRegistry.withdrawFromPlayer(player.getUUID(), creationFee, "Nation creation fee: " + name);
                if (!charged) {
                    // This shouldn't happen since we checked balance, but handle it gracefully
                    context.getSource().sendSystemMessage(Component.literal("§eWarning: Could not charge creation fee."));
                }
            }

            // Mark data dirty
            markDataDirty(context);

            String feeMessage = creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()
                ? " (Cost: " + IntegrationRegistry.formatCurrency(creationFee) + ")" : "";
            context.getSource().sendSuccess(() -> Component.literal("§aNation §e" + name + "§a created successfully!" + feeMessage), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int infoSelf(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());

            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            sendNationInfo(context.getSource(), nation);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int infoOther(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        Nation nation = ChunkClaimManager.getInstance().getNationByName(name);

        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation '" + name + "' not found!"));
            return 0;
        }

        sendNationInfo(context.getSource(), nation);
        return 1;
    }

    private static void sendNationInfo(CommandSourceStack source, Nation nation) {
        source.sendSuccess(() -> Component.literal("§6=== Nation: §e" + nation.getName() + " §6==="), false);
        source.sendSuccess(() -> Component.literal("§7States: §f" + nation.getStateCount() + "/" + nation.getMaxStates()), false);
        source.sendSuccess(() -> Component.literal("§7Cities: §f" + nation.getTotalCityCount()), false);
        source.sendSuccess(() -> Component.literal("§7Claimed Chunks: §f" + nation.getTotalChunkCount()), false);
        source.sendSuccess(() -> Component.literal("§7Members: §f" + nation.getAllMembers().size()), false);
        source.sendSuccess(() -> Component.literal("§7Open: §f" + (nation.isOpen() ? "Yes" : "No")), false);
        if (!nation.getDescription().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Description: §f" + nation.getDescription()), false);
        }
    }

    private static int listNations(CommandContext<CommandSourceStack> context) {
        var nations = ChunkClaimManager.getInstance().getAllNations();

        if (nations.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7No nations exist yet."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("§6=== Nations (" + nations.size() + ") ==="), false);
        for (Nation nation : nations) {
            context.getSource().sendSuccess(() -> Component.literal(
                "§e" + nation.getName() + " §7- " + nation.getAllMembers().size() + " members, " +
                nation.getTotalChunkCount() + " chunks"
            ), false);
        }
        return 1;
    }

    private static int invitePlayer(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer sender = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "player");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(sender.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            if (!nation.isLeaderOrOfficer(sender.getUUID())) {
                context.getSource().sendFailure(Component.literal("You don't have permission to invite players!"));
                return 0;
            }

            // Check if target is already in a nation
            if (ChunkClaimManager.getInstance().getPlayerNation(target.getUUID()) != null) {
                context.getSource().sendFailure(Component.literal("That player is already in a nation!"));
                return 0;
            }

            // Create invitation
            Invitation invite = InvitationManager.getInstance().createInvitation(
                target.getUUID(),
                sender.getUUID(),
                nation.getId(),
                Invitation.InvitationType.NATION
            );

            if (invite == null) {
                context.getSource().sendFailure(Component.literal("That player already has a pending invite to your nation!"));
                return 0;
            }

            markDataDirty(context);

            // Notify sender
            final String nationName = nation.getName();
            context.getSource().sendSuccess(() -> Component.literal(
                "§aInvited §e" + target.getName().getString() + "§a to " + nationName + "!"
            ), false);

            // Send mail to target with accept/deny buttons
            MailManager.getInstance().sendNationInviteMail(
                target.getUUID(),
                sender.getUUID(),
                sender.getName().getString(),
                nation.getId(),
                nationName
            );

            // Notify target in chat as well
            target.displayClientMessage(Component.literal(
                "§6You've been invited to join §e" + nationName + "§6!"
            ), false);
            target.displayClientMessage(Component.literal(
                "§7Check your mail to accept or deny, or use §e/nation accept §7/ §e/nation deny§7."
            ), false);
            target.displayClientMessage(Component.literal(
                "§7This invite expires in 5 minutes."
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int acceptInvite(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            List<Invitation> invites = InvitationManager.getInstance().getInvitationsForPlayer(player.getUUID());

            if (invites.isEmpty()) {
                context.getSource().sendFailure(Component.literal("You have no pending invitations!"));
                return 0;
            }

            // Accept the most recent invitation
            Invitation invite = invites.get(invites.size() - 1);
            return processAcceptInvite(context, player, invite);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int acceptInviteNamed(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String nationName = StringArgumentType.getString(context, "nation");

            List<Invitation> invites = InvitationManager.getInstance().getInvitationsForPlayer(player.getUUID());

            Invitation targetInvite = null;
            for (Invitation inv : invites) {
                if (inv.getType() == Invitation.InvitationType.NATION) {
                    Nation nation = ChunkClaimManager.getInstance().getNation(inv.getEntityId());
                    if (nation != null && nation.getName().equalsIgnoreCase(nationName)) {
                        targetInvite = inv;
                        break;
                    }
                }
            }

            if (targetInvite == null) {
                context.getSource().sendFailure(Component.literal("You don't have an invitation from '" + nationName + "'!"));
                return 0;
            }

            return processAcceptInvite(context, player, targetInvite);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int processAcceptInvite(CommandContext<CommandSourceStack> context, ServerPlayer player, Invitation invite) {
        Nation nation = ChunkClaimManager.getInstance().getNation(invite.getEntityId());
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("That nation no longer exists!"));
            InvitationManager.getInstance().denyInvitation(invite.getId(), player.getUUID());
            return 0;
        }

        boolean success = InvitationManager.getInstance().acceptInvitation(invite.getId(), player.getUUID());
        if (!success) {
            context.getSource().sendFailure(Component.literal("Failed to join the nation. You may already be in a nation."));
            return 0;
        }

        markDataDirty(context);

        String nationName = nation.getName();
        context.getSource().sendSuccess(() -> Component.literal(
            "§aYou have joined §e" + nationName + "§a!"
        ), false);

        // Notify online nation members
        if (context.getSource().getServer() != null) {
            for (ServerPlayer online : context.getSource().getServer().getPlayerList().getPlayers()) {
                if (nation.isMember(online.getUUID()) && !online.getUUID().equals(player.getUUID())) {
                    online.displayClientMessage(Component.literal(
                        "§e" + player.getName().getString() + "§a has joined the nation!"
                    ), false);
                }
            }
        }

        return 1;
    }

    private static int denyInvite(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            List<Invitation> invites = InvitationManager.getInstance().getInvitationsForPlayer(player.getUUID());

            if (invites.isEmpty()) {
                context.getSource().sendFailure(Component.literal("You have no pending invitations!"));
                return 0;
            }

            // Deny the most recent invitation
            Invitation invite = invites.get(invites.size() - 1);
            return processDenyInvite(context, player, invite);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int denyInviteNamed(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String nationName = StringArgumentType.getString(context, "nation");

            List<Invitation> invites = InvitationManager.getInstance().getInvitationsForPlayer(player.getUUID());

            Invitation targetInvite = null;
            for (Invitation inv : invites) {
                if (inv.getType() == Invitation.InvitationType.NATION) {
                    Nation nation = ChunkClaimManager.getInstance().getNation(inv.getEntityId());
                    if (nation != null && nation.getName().equalsIgnoreCase(nationName)) {
                        targetInvite = inv;
                        break;
                    }
                }
            }

            if (targetInvite == null) {
                context.getSource().sendFailure(Component.literal("You don't have an invitation from '" + nationName + "'!"));
                return 0;
            }

            return processDenyInvite(context, player, targetInvite);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int processDenyInvite(CommandContext<CommandSourceStack> context, ServerPlayer player, Invitation invite) {
        Nation nation = ChunkClaimManager.getInstance().getNation(invite.getEntityId());
        String nationName = nation != null ? nation.getName() : "Unknown";

        InvitationManager.getInstance().denyInvitation(invite.getId(), player.getUUID());
        markDataDirty(context);

        context.getSource().sendSuccess(() -> Component.literal(
            "§cYou have declined the invitation from §e" + nationName + "§c."
        ), false);

        return 1;
    }

    private static int listInvites(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            List<Invitation> invites = InvitationManager.getInstance().getInvitationsForPlayer(player.getUUID());

            if (invites.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("§7You have no pending invitations."), false);
                return 1;
            }

            context.getSource().sendSuccess(() -> Component.literal("§6=== Your Invitations (" + invites.size() + ") ==="), false);
            for (Invitation inv : invites) {
                String entityName = "Unknown";
                if (inv.getType() == Invitation.InvitationType.NATION) {
                    Nation nation = ChunkClaimManager.getInstance().getNation(inv.getEntityId());
                    if (nation != null) entityName = nation.getName();
                }
                final String name = entityName;
                final long remaining = inv.getRemainingTimeSeconds();
                context.getSource().sendSuccess(() -> Component.literal(
                    "§e" + name + " §7(" + inv.getType() + ") - " + remaining + "s remaining"
                ), false);
            }
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Use §e/nation accept <name> §7or §e/nation deny <name>"
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int joinNation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String nationName = StringArgumentType.getString(context, "nation");

            // Check if player is already in a nation
            if (ChunkClaimManager.getInstance().getPlayerNation(player.getUUID()) != null) {
                context.getSource().sendFailure(Component.literal("You are already in a nation! Leave first with /nation leave"));
                return 0;
            }

            Nation nation = ChunkClaimManager.getInstance().getNationByName(nationName);
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("Nation '" + nationName + "' not found!"));
                return 0;
            }

            if (!nation.isOpen()) {
                context.getSource().sendFailure(Component.literal("That nation is not open for new members. Ask for an invite!"));
                return 0;
            }

            // Join the nation
            boolean success = ChunkClaimManager.getInstance().addPlayerToNation(player.getUUID(), nation.getId());
            if (!success) {
                context.getSource().sendFailure(Component.literal("Failed to join the nation."));
                return 0;
            }

            markDataDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aYou have joined §e" + nation.getName() + "§a!"
            ), false);

            // Notify online nation members
            if (context.getSource().getServer() != null) {
                for (ServerPlayer online : context.getSource().getServer().getPlayerList().getPlayers()) {
                    if (nation.isMember(online.getUUID()) && !online.getUUID().equals(player.getUUID())) {
                        online.displayClientMessage(Component.literal(
                            "§e" + player.getName().getString() + "§a has joined the nation!"
                        ), false);
                    }
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int kickPlayer(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer sender = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "player");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(sender.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            if (!nation.isLeaderOrOfficer(sender.getUUID())) {
                context.getSource().sendFailure(Component.literal("You don't have permission to kick players!"));
                return 0;
            }

            // Check if target is in the same nation
            Nation targetNation = ChunkClaimManager.getInstance().getPlayerNation(target.getUUID());
            if (targetNation == null || !targetNation.getId().equals(nation.getId())) {
                context.getSource().sendFailure(Component.literal("That player is not in your nation!"));
                return 0;
            }

            // Can't kick the leader
            if (target.getUUID().equals(nation.getLeaderId())) {
                context.getSource().sendFailure(Component.literal("You cannot kick the nation leader!"));
                return 0;
            }

            // Can't kick other officers unless you're the leader
            if (nation.isLeaderOrOfficer(target.getUUID()) && !sender.getUUID().equals(nation.getLeaderId())) {
                context.getSource().sendFailure(Component.literal("Only the leader can kick officers!"));
                return 0;
            }

            // Perform the kick
            ChunkClaimManager.getInstance().removePlayerFromNation(target.getUUID());
            markDataDirty(context);

            String targetName = target.getName().getString();
            context.getSource().sendSuccess(() -> Component.literal(
                "§c" + targetName + " has been kicked from the nation!"
            ), true);

            // Notify the kicked player
            target.displayClientMessage(Component.literal(
                "§cYou have been kicked from §e" + nation.getName() + "§c!"
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int leaveNation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());

            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            if (player.getUUID().equals(nation.getLeaderId())) {
                context.getSource().sendFailure(Component.literal("Leaders cannot leave! Transfer leadership or disband the nation."));
                return 0;
            }

            String nationName = nation.getName();

            // Perform the leave
            ChunkClaimManager.getInstance().removePlayerFromNation(player.getUUID());
            markDataDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§cYou have left §e" + nationName + "§c."
            ), false);

            // Notify online nation members
            if (context.getSource().getServer() != null) {
                for (ServerPlayer online : context.getSource().getServer().getPlayerList().getPlayers()) {
                    if (nation.isMember(online.getUUID())) {
                        online.displayClientMessage(Component.literal(
                            "§e" + player.getName().getString() + "§c has left the nation."
                        ), false);
                    }
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int disbandNation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());

            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            if (!player.getUUID().equals(nation.getLeaderId())) {
                context.getSource().sendFailure(Component.literal("Only the nation leader can disband the nation!"));
                return 0;
            }

            String nationName = nation.getName();
            ChunkClaimManager.getInstance().disbandNation(nation.getId());
            markDataDirty(context);

            context.getSource().sendSuccess(() -> Component.literal("§cNation §e" + nationName + "§c has been disbanded!"), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int setOpen(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String openStr = StringArgumentType.getString(context, "open");
            boolean open = openStr.equalsIgnoreCase("true") || openStr.equalsIgnoreCase("yes");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            if (!nation.isLeaderOrOfficer(player.getUUID())) {
                context.getSource().sendFailure(Component.literal("You don't have permission to change nation settings!"));
                return 0;
            }

            nation.setOpen(open);
            ChunkClaimManager.getInstance().markDirty();
            markDataDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aNation is now " + (open ? "§eopen§a to new members!" : "§eclosed§a to new members!")
            ), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static void markDataDirty(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        NationSavedData.get(level).markForSave();
    }
}

