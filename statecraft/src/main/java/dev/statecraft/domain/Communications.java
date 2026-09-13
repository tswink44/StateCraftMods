package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.Mail;
import dev.statecraft.domain.GovernanceData.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static dev.statecraft.domain.GovernanceEngine.check;

final class Communications {
    private final GovernanceEngine e;

    Communications(GovernanceEngine engine) {
        e = engine;
    }

    String command(Actor actor, Arguments args) {
        String action = args.optional(1, "inbox");
        Player player = e.requirePlayer(actor.id());
        if ("official".equals(action)) return official(actor, args);
        switch (action) {
            case "inbox", "sent" -> {
                args.between(1, 3, "mail " + action + " [page]");
                return list(action, "sent".equals(action) ? player.sent : player.inbox, args.page(2),
                        actor.account(), "sent".equals(action));
            }
            case "read" -> {
                args.exactly(3, "mail read <messageId>");
                return read(player.inbox, player.sent, args.get(2), actor.account());
            }
            case "delete" -> {
                args.exactly(3, "mail delete <messageId>");
                boolean removed = player.inbox.removeIf(m -> m != null && args.get(2).equals(m.id));
                removed |= player.sent.removeIf(m -> m != null && args.get(2).equals(m.id));
                check(removed, "No message with that ID exists in your mailbox.");
                e.changed();
                return "Message deleted from your mailbox only.";
            }
            case "send", "compose" -> {
                args.between(5, 64, "mail send <player|government:nameOrId> <subject> <body>");
                String recipient = recipient(args.get(2));
                userMessage(args.get(3), args.tail(4));
                deliver(actor.account(), recipient, args.get(3), args.tail(4), true);
                return "Message sent.";
            }
            case "reply" -> {
                args.between(4, 64, "mail reply <messageId> <body>");
                Mail original = find(player.inbox, args.get(2), actor.account(), false);
                check(original != null, "That message is not in your inbox.");
                check(!"system".equals(original.sender), "System notifications cannot receive replies.");
                String subject = clip("Re: " + original.subject, 100);
                userMessage(subject, args.tail(3));
                validateRecipientAccount(original.sender);
                deliver(actor.account(), original.sender, subject, args.tail(3), true);
                return "Reply sent.";
            }
            case "invitations" -> {
                args.between(2, 3, "mail invitations [page]");
                List<String> rows = e.data.invitations.values().stream().filter(Objects::nonNull)
                        .filter(i -> player.id.equals(i.playerId) && i.expiresAt > e.now())
                        .map(i -> i.id + " " + (i.governmentId == null ? "Company " + e.companyName(i.companyId)
                                : e.governmentName(i.governmentId)) + " expires=" + i.expiresAt).toList();
                return e.page("Your invitations", rows, args.page(2));
            }
            default -> throw new UserError("Unknown mail action '" + action + "'. Use help mail.");
        }
    }

    private String official(Actor actor, Arguments args) {
        String action = args.get(2);
        Government government = e.gov(args.get(3));
        e.manage(actor, government);
        switch (action) {
            case "inbox", "sent" -> {
                args.between(4, 5, "mail official " + action + " <government> [page]");
                return list(government.name + " " + action,
                        "sent".equals(action) ? government.sent : government.inbox, args.page(4),
                        e.account(government), "sent".equals(action));
            }
            case "read" -> {
                args.exactly(5, "mail official read <government> <messageId>");
                return read(government.inbox, government.sent, args.get(4), e.account(government));
            }
            case "delete" -> {
                args.exactly(5, "mail official delete <government> <messageId>");
                boolean removed = government.inbox.removeIf(m -> m != null && args.get(4).equals(m.id));
                removed |= government.sent.removeIf(m -> m != null && args.get(4).equals(m.id));
                check(removed, "That message is not in this official mailbox.");
                e.changed();
                return "Official mailbox copy deleted.";
            }
            case "send", "compose" -> {
                args.between(7, 64, "mail official send <government> <player|government:nameOrId> <subject> <body>");
                String recipient = recipient(args.get(4));
                userMessage(args.get(5), args.tail(6));
                deliver(e.account(government), recipient, args.get(5), args.tail(6), true);
                e.history("official-mail", government.id, "Sent by " + actor.id() + " to " + recipient);
                return "Official message sent as " + government.name + ".";
            }
            case "reply" -> {
                args.between(6, 64, "mail official reply <government> <messageId> <body>");
                Mail original = find(government.inbox, args.get(4), e.account(government), false);
                check(original != null, "That message is not in this official inbox.");
                check(!"system".equals(original.sender), "System notifications cannot receive replies.");
                validateRecipientAccount(original.sender);
                String subject = clip("Re: " + original.subject, 100);
                userMessage(subject, args.tail(5));
                deliver(e.account(government), original.sender, subject, args.tail(5), true);
                e.history("official-mail", government.id, "Reply by " + actor.id() + " to " + original.sender);
                return "Official reply sent.";
            }
            default -> throw new UserError("Unknown official mail action. Use inbox, sent, read, send, reply, or delete.");
        }
    }

    String profile(Actor actor, Arguments args) {
        args.between(1, 2, "profile [player]");
        Player player = args.size() == 2 ? e.resolvePlayer(args.get(1)) : e.requirePlayer(actor.id());
        List<String> rows = new ArrayList<>();
        rows.add(player.name + " [" + player.id + "]");
        for (String id : new String[]{player.nationId, player.stateId, player.cityId}) {
            Government government = e.data.governments.get(id);
            if (e.validHierarchy(government)) rows.add(government.kind + ": " + government.name + " (" + e.role(player.id, government) + ")");
        }
        if (player.nationId == null) rows.add("Unaffiliated citizen");
        List<String> companies = e.data.companies.values().stream().filter(e::viewableCompany)
                .filter(c -> c.members.contains(player.id) || c.shares.getOrDefault(player.id, 0L) > 0)
                .map(c -> c.name + (player.id.equals(c.owner) ? " (owner)" : "")
                        + " shares=" + c.shares.getOrDefault(player.id, 0L)).toList();
        rows.add("Companies: " + companies.size());
        rows.addAll(companies.subList(0, Math.min(e.config.pageSize, companies.size())));
        if (player.id.equals(actor.id().toString()))
            rows.add("Unread personal mail: " + player.inbox.stream()
                    .filter(m -> m != null && ("player:" + player.id).equals(m.recipient) && !m.read).count());
        return String.join("\n", rows);
    }

    void deliver(String sender, String recipient, String subject, String body, boolean sentCopy) {
        validateRecipientAccount(recipient);
        if ("system".equals(sender)) {
            subject = clean(subject, 100, false);
            body = clean(body, e.config.maxMailBodyLength, true);
        } else body = userMessage(subject, body);
        Mail mail = new Mail();
        mail.id = GovernanceEngine.newId();
        mail.sender = sender;
        mail.recipient = recipient;
        mail.subject = subject;
        mail.body = body;
        mail.sentAt = e.now();
        List<Mail> inbox = box(recipient, false);
        e.changed();
        inbox.add(mail);
        e.trim(inbox, e.config.maxMail);
        if (sentCopy) {
            Mail copy = new Mail();
            copy.id = mail.id;
            copy.sender = mail.sender;
            copy.recipient = mail.recipient;
            copy.subject = mail.subject;
            copy.body = mail.body;
            copy.sentAt = mail.sentAt;
            copy.read = true;
            List<Mail> sent = box(sender, true);
            sent.add(copy);
            e.trim(sent, e.config.maxMail);
        }
    }

    String recipient(String reference) {
        if (reference.startsWith("government:")) return e.account(e.gov(reference.substring("government:".length())));
        return "player:" + e.resolvePlayer(reference).id;
    }

    void validateRecipientAccount(String account) {
        check(account != null, "No recipient was specified.");
        String[] parts = account.split(":", 2);
        check(parts.length == 2, "Invalid mail recipient.");
        if ("player".equals(parts[0])) {
            check(e.data.players.containsKey(parts[1]), "That player is not known.");
        } else {
            Government government = e.data.governments.get(parts[1]);
            check(e.validHierarchy(government) && account.equals(e.account(government)), "That official mailbox no longer exists.");
        }
    }

    private List<Mail> box(String account, boolean sent) {
        String[] parts = account.split(":", 2);
        if ("player".equals(parts[0])) {
            Player player = e.data.players.get(parts[1]);
            return sent ? player.sent : player.inbox;
        }
        Government government = e.data.governments.get(parts[1]);
        return sent ? government.sent : government.inbox;
    }

    private String list(String title, List<Mail> mailbox, int page, String owner, boolean sent) {
        List<Mail> newest = new ArrayList<>(mailbox);
        Collections.reverse(newest);
        return e.page(title, newest.stream().filter(Objects::nonNull)
                .filter(mail -> owner.equals(sent ? mail.sender : mail.recipient)).map(mail ->
                (mail.read ? "[read] " : "[unread] ") + mail.id + " | " + mail.subject
                        + " | from=" + mail.sender + " to=" + mail.recipient + " at=" + mail.sentAt).toList(), page);
    }

    private String read(List<Mail> inbox, List<Mail> sent, String id, String owner) {
        Mail message = find(inbox, id, owner, false);
        if (message == null) message = find(sent, id, owner, true);
        check(message != null, "That message is not in this mailbox.");
        markRead(message);
        return message.subject + "\nFrom: " + message.sender + "\nTo: " + message.recipient
                + "\nSent: " + message.sentAt + "\n" + message.body;
    }

    void markRead(Mail message) {
        if (!message.read) {
            message.read = true;
            e.changed();
        }
    }

    private Mail find(List<Mail> messages, String id, String owner, boolean sent) {
        return messages.stream().filter(Objects::nonNull)
                .filter(mail -> owner.equals(sent ? mail.sender : mail.recipient) && id.equals(mail.id)).findFirst().orElse(null);
    }

    String userMessage(String subject, String body) {
        GovernanceEngine.text(subject, 100, "Subject");
        return GovernanceEngine.prose(body, e.config.maxMailBodyLength, "Message body");
    }

    private static String clean(String value, int limit, boolean multiline) {
        String input = value == null || value.isBlank() ? "(notification)" : value;
        if (multiline) input = input.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(Math.min(input.length(), limit));
        for (int index = 0; index < input.length() && result.length() < limit; index++) {
            char valueAt = input.charAt(index);
            boolean allowedControl = multiline && (valueAt == '\n' || valueAt == '\t');
            result.append(Character.isISOControl(valueAt) && !allowedControl ? ' ' : valueAt);
        }
        return result.toString();
    }

    private static String clip(String value, int limit) {
        return value.substring(0, Math.min(value.length(), limit));
    }
}
