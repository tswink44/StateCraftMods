package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.mail.Mail;
import com.statecraft.mail.Mailbox;
import com.statecraft.mail.MailManager;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.OpenGuiPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Commands for the mail system
 * /sc mail - Open mail GUI
 * /sc mail send <player> <subject> - Send mail to player
 * /sc mail read - Show unread count
 * /sc mail list - List recent mail in chat
 */
public class MailCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("mail")
            .executes(MailCommand::openMailGui)
            .then(Commands.literal("send")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(MailCommand::sendMail))))
            .then(Commands.literal("read")
                .executes(MailCommand::showUnreadCount))
            .then(Commands.literal("list")
                .executes(MailCommand::listMail))
            .then(Commands.literal("clear")
                .then(Commands.literal("read")
                    .executes(MailCommand::clearReadMail))
                .then(Commands.literal("all")
                    .executes(MailCommand::clearAllMail)));
    }

    private static int openMailGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.MAIL_INBOX,
                "", "", "",
                0, 0
            ), player);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int sendMail(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer sender = context.getSource().getPlayerOrException();
            ServerPlayer recipient = EntityArgument.getPlayer(context, "player");
            String message = StringArgumentType.getString(context, "message");

            if (sender.getUUID().equals(recipient.getUUID())) {
                context.getSource().sendFailure(Component.literal("§cYou cannot send mail to yourself!"));
                return 0;
            }

            // Parse subject and body - first line is subject
            String subject;
            String body;
            int newlineIndex = message.indexOf('\n');
            if (newlineIndex > 0) {
                subject = message.substring(0, newlineIndex).trim();
                body = message.substring(newlineIndex + 1).trim();
            } else {
                // Use first 30 chars as subject if no newline
                subject = message.length() > 30 ? message.substring(0, 30) + "..." : message;
                body = message;
            }

            MailManager.getInstance().sendPlayerMail(
                sender.getUUID(),
                sender.getName().getString(),
                recipient.getUUID(),
                subject,
                body
            );

            context.getSource().sendSuccess(() ->
                Component.literal("§aMail sent to " + recipient.getName().getString()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int showUnreadCount(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Mailbox mailbox = MailManager.getInstance().getPlayerMailbox(player.getUUID());
            int unread = mailbox.getUnreadCount();

            if (unread == 0) {
                context.getSource().sendSuccess(() ->
                    Component.literal("§7You have no unread mail."), false);
            } else {
                context.getSource().sendSuccess(() ->
                    Component.literal("§eYou have §f" + unread + "§e unread message" +
                        (unread > 1 ? "s" : "") + "."), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int listMail(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Mailbox mailbox = MailManager.getInstance().getPlayerMailbox(player.getUUID());
            List<Mail> inbox = mailbox.getInbox();

            if (inbox.isEmpty()) {
                context.getSource().sendSuccess(() ->
                    Component.literal("§7Your mailbox is empty."), false);
                return 1;
            }

            context.getSource().sendSuccess(() ->
                Component.literal("§6=== Recent Mail ==="), false);

            int shown = 0;
            for (Mail mail : inbox) {
                if (shown >= 5) break;

                String readMarker = mail.isRead() ? "§7" : "§e§l";
                String typeColor = mail.getType().getColorCode();

                context.getSource().sendSuccess(() ->
                    Component.literal(readMarker + "• " + typeColor + mail.getSubject() +
                        " §7- " + mail.getSenderName() + " §8(" + mail.getFormattedTimestamp() + ")"), false);
                shown++;
            }

            if (inbox.size() > 5) {
                int remaining = inbox.size() - 5;
                context.getSource().sendSuccess(() ->
                    Component.literal("§7... and " + remaining + " more. Use §e/sc mail§7 to view all."), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int clearReadMail(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Mailbox mailbox = MailManager.getInstance().getPlayerMailbox(player.getUUID());

            int beforeCount = mailbox.getTotalCount();
            mailbox.deleteReadMessages();
            int deleted = beforeCount - mailbox.getTotalCount();

            MailManager.getInstance().markDirty();

            context.getSource().sendSuccess(() ->
                Component.literal("§aDeleted " + deleted + " read message" + (deleted != 1 ? "s" : "") + "."), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int clearAllMail(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Mailbox mailbox = MailManager.getInstance().getPlayerMailbox(player.getUUID());

            int count = mailbox.getTotalCount();
            mailbox.clearAll();

            MailManager.getInstance().markDirty();

            context.getSource().sendSuccess(() ->
                Component.literal("§aDeleted all " + count + " message" + (count != 1 ? "s" : "") + "."), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }
}


