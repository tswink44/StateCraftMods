package com.statecraft.mail;

import com.google.gson.*;
import com.statecraft.StateCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all mailboxes in StateCraft.
 * Handles mail delivery, storage, and automated notifications.
 */
public class MailManager {

    private static MailManager instance;

    // Mailboxes indexed by owner ID
    private final Map<UUID, Mailbox> playerMailboxes = new ConcurrentHashMap<>();
    private final Map<UUID, Mailbox> cityMailboxes = new ConcurrentHashMap<>();
    private final Map<UUID, Mailbox> stateMailboxes = new ConcurrentHashMap<>();
    private final Map<UUID, Mailbox> nationMailboxes = new ConcurrentHashMap<>();

    // System sender info
    private static final String SYSTEM_SENDER_NAME = "§6StateCraft System";
    private static final String TAX_SENDER_NAME = "§6Tax Authority";
    private static final String LAND_SENDER_NAME = "§6Land Registry";

    private boolean dirty = false;
    private Path savePath;

    private MailManager() {}

    public static MailManager getInstance() {
        if (instance == null) {
            instance = new MailManager();
        }
        return instance;
    }

    /**
     * Initialize the mail manager with the server
     */
    public void init(MinecraftServer server) {
        savePath = server.getWorldPath(LevelResource.ROOT).resolve("statecraft_mail.json");
        load();
    }

    // ==================== Mailbox Access ====================

    /**
     * Get or create a player's mailbox
     */
    public Mailbox getPlayerMailbox(UUID playerId) {
        return playerMailboxes.computeIfAbsent(playerId,
            id -> new Mailbox(id, Mail.RecipientType.PLAYER));
    }

    /**
     * Get or create a city's mailbox
     */
    public Mailbox getCityMailbox(UUID cityId) {
        return cityMailboxes.computeIfAbsent(cityId,
            id -> new Mailbox(id, Mail.RecipientType.CITY));
    }

    /**
     * Get or create a state's mailbox
     */
    public Mailbox getStateMailbox(UUID stateId) {
        return stateMailboxes.computeIfAbsent(stateId,
            id -> new Mailbox(id, Mail.RecipientType.STATE));
    }

    /**
     * Get or create a nation's mailbox
     */
    public Mailbox getNationMailbox(UUID nationId) {
        return nationMailboxes.computeIfAbsent(nationId,
            id -> new Mailbox(id, Mail.RecipientType.NATION));
    }

    /**
     * Get mailbox by type and ID
     */
    @Nullable
    public Mailbox getMailbox(UUID id, Mail.RecipientType type) {
        return switch (type) {
            case PLAYER -> getPlayerMailbox(id);
            case CITY -> getCityMailbox(id);
            case STATE -> getStateMailbox(id);
            case NATION -> getNationMailbox(id);
        };
    }

    // ==================== Mail Sending ====================

    /**
     * Send mail from one player to another
     */
    public void sendPlayerMail(UUID senderId, String senderName, UUID recipientId,
                               String subject, String body) {
        Mail mail = new Mail(senderId, senderName, recipientId, Mail.RecipientType.PLAYER,
            Mail.MailType.PERSONAL, subject, body);
        deliverMail(mail);
    }

    /**
     * Send system mail to a player
     */
    public void sendSystemMail(UUID recipientId, Mail.MailType type, String subject, String body) {
        Mail mail = new Mail(null, SYSTEM_SENDER_NAME, recipientId, Mail.RecipientType.PLAYER,
            type, subject, body);
        deliverMail(mail);
    }

    /**
     * Send mail to a government entity
     */
    public void sendGovMail(UUID entityId, Mail.RecipientType entityType,
                           @Nullable UUID senderId, String senderName,
                           Mail.MailType type, String subject, String body) {
        Mail mail = new Mail(senderId, senderName, entityId, entityType, type, subject, body);
        deliverMail(mail);
    }

    /**
     * Deliver a mail to its recipient's mailbox
     */
    private void deliverMail(Mail mail) {
        StateCraft.LOGGER.debug("Delivering mail to {} (type: {}): {}",
            mail.getRecipientId(), mail.getRecipientType(), mail.getSubject());

        Mailbox mailbox = getMailbox(mail.getRecipientId(), mail.getRecipientType());
        if (mailbox != null) {
            mailbox.addMessage(mail);
            dirty = true;
            StateCraft.LOGGER.debug("Mail delivered successfully to mailbox with {} messages",
                mailbox.getAllMessages().size());

            // Send notification if it's a player and they're online
            if (mail.getRecipientType() == Mail.RecipientType.PLAYER && mailbox.isNotificationsEnabled()) {
                notifyPlayerOfNewMail(mail.getRecipientId(), mail);
            }
        } else {
            StateCraft.LOGGER.warn("Failed to deliver mail - no mailbox found for {} (type: {})",
                mail.getRecipientId(), mail.getRecipientType());
        }
    }

    /**
     * Notify an online player of new mail
     */
    private void notifyPlayerOfNewMail(UUID playerId, Mail mail) {
        // This will be called from server context
        try {
            MinecraftServer server = StateCraft.getServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                        "§e§l[Mail] §r" + mail.getType().getColorCode() + "New mail: §f" + mail.getSubject()
                    ));
                }
            }
        } catch (Exception ignored) {}
    }

    // ==================== Automated Mail Templates ====================

    /**
     * Send chunk purchase confirmation to buyer
     */
    public void sendChunkPurchaseConfirmation(UUID buyerId, int chunkX, int chunkZ,
                                               double price, String cityName, String sellerName) {
        String subject = "Chunk Purchase Confirmation";
        String body = String.format(
            "§7Congratulations on your purchase!\n\n" +
            "§fChunk Location: §e(%d, %d)\n" +
            "§fCity: §e%s\n" +
            "§fPurchase Price: §a$%.2f\n" +
            "§fSold By: §e%s\n\n" +
            "§7You are now the owner of this chunk. Remember to pay your property taxes!",
            chunkX, chunkZ, cityName, price, sellerName
        );
        sendSystemMail(buyerId, Mail.MailType.CHUNK_PURCHASE, subject, body);
    }

    /**
     * Send chunk sale confirmation to seller
     */
    public void sendChunkSaleConfirmation(UUID sellerId, int chunkX, int chunkZ,
                                          double price, String buyerName) {
        String subject = "Chunk Sale Confirmation";
        String body = String.format(
            "§7Your chunk has been sold!\n\n" +
            "§fChunk Location: §e(%d, %d)\n" +
            "§fSale Price: §a$%.2f\n" +
            "§fPurchased By: §e%s\n\n" +
            "§7The funds have been deposited to your account.",
            chunkX, chunkZ, price, buyerName
        );
        sendSystemMail(sellerId, Mail.MailType.CHUNK_SALE, subject, body);
    }

    /**
     * Send tax summary to player
     */
    public void sendPlayerTaxSummary(UUID playerId, int chunksOwned, double totalTaxPaid,
                                     double newBalance) {
        String subject = "Property Tax Summary";
        String body = String.format(
            "§7Tax Collection Complete\n\n" +
            "§fChunks Owned: §e%d\n" +
            "§fTotal Tax Paid: §c$%.2f\n" +
            "§fNew Balance: §%s$%.2f\n\n" +
            "§7Thank you for being a responsible property owner!",
            chunksOwned, totalTaxPaid,
            newBalance >= 0 ? "a" : "c", newBalance
        );
        sendSystemMail(playerId, Mail.MailType.TAX_SUMMARY, subject, body);
    }

    /**
     * Send tax warning for negative balance
     */
    public void sendTaxWarning(UUID playerId, int chunkX, int chunkZ,
                               int warningsRemaining, double balance) {
        String subject = "⚠ Tax Payment Warning";
        String body = String.format(
            "§c§lWARNING: Insufficient Funds\n\n" +
            "§fYour account went negative after property tax collection.\n\n" +
            "§fChunk at Risk: §e(%d, %d)\n" +
            "§fCurrent Balance: §c$%.2f\n" +
            "§fWarnings Remaining: §c%d\n\n" +
            "§7Please deposit funds to avoid repossession!\n" +
            "§cAfter %d more tax period(s) with negative balance, " +
            "your chunk will be repossessed by the government.",
            chunkX, chunkZ, balance, warningsRemaining, warningsRemaining
        );
        sendSystemMail(playerId, Mail.MailType.TAX_WARNING, subject, body);
    }

    /**
     * Send repossession notice
     */
    public void sendRepossessionNotice(UUID playerId, int chunkX, int chunkZ, String cityName) {
        String subject = "❌ Chunk Repossession Notice";
        String body = String.format(
            "§4§lCHUNK REPOSSESSED\n\n" +
            "§fDue to repeated failure to pay property taxes, " +
            "your chunk has been repossessed by the government.\n\n" +
            "§fChunk Location: §e(%d, %d)\n" +
            "§fCity: §e%s\n\n" +
            "§7The chunk is now government property and may be resold.\n" +
            "§7Please ensure you have sufficient funds before purchasing new property.",
            chunkX, chunkZ, cityName
        );
        sendSystemMail(playerId, Mail.MailType.TAX_REPOSSESSION, subject, body);
    }

    /**
     * Send tax revenue summary to city
     */
    public void sendCityTaxRevenue(UUID cityId, String cityName, double totalCollected,
                                   double keptAmount, double passedToState, int chunksProcessed) {
        String subject = "Tax Revenue Report";
        String body = String.format(
            "§2Tax Collection Report for %s\n\n" +
            "§fChunks Processed: §e%d\n" +
            "§fTotal Collected: §a$%.2f\n" +
            "§fCity Revenue: §a$%.2f\n" +
            "§fPassed to State: §e$%.2f\n\n" +
            "§7This revenue has been deposited to the city treasury.",
            cityName, chunksProcessed, totalCollected, keptAmount, passedToState
        );
        sendGovMail(cityId, Mail.RecipientType.CITY, null, TAX_SENDER_NAME,
            Mail.MailType.GOV_TAX_REVENUE, subject, body);
    }

    /**
     * Send tax revenue summary to state
     */
    public void sendStateTaxRevenue(UUID stateId, String stateName, double totalReceived,
                                    double keptAmount, double passedToNation) {
        String subject = "Tax Revenue Report";
        String body = String.format(
            "§2Tax Collection Report for %s\n\n" +
            "§fTotal Received from Cities: §a$%.2f\n" +
            "§fState Revenue: §a$%.2f\n" +
            "§fPassed to Nation: §e$%.2f\n\n" +
            "§7This revenue has been deposited to the state treasury.",
            stateName, totalReceived, keptAmount, passedToNation
        );
        sendGovMail(stateId, Mail.RecipientType.STATE, null, TAX_SENDER_NAME,
            Mail.MailType.GOV_TAX_REVENUE, subject, body);
    }

    /**
     * Send tax revenue summary to nation
     */
    public void sendNationTaxRevenue(UUID nationId, String nationName, double totalReceived) {
        String subject = "Tax Revenue Report";
        String body = String.format(
            "§2Tax Collection Report for %s\n\n" +
            "§fTotal Received from States: §a$%.2f\n\n" +
            "§7This revenue has been deposited to the nation treasury.",
            nationName, totalReceived
        );
        sendGovMail(nationId, Mail.RecipientType.NATION, null, TAX_SENDER_NAME,
            Mail.MailType.GOV_TAX_REVENUE, subject, body);
    }

    /**
     * Send welcome mail to new players
     */
    public void sendWelcomeMail(UUID playerId, String playerName) {
        String subject = "Welcome to StateCraft!";
        String body = String.format(
            "§aWelcome, %s!\n\n" +
            "§fStateCraft is a nation-building mod where you can:\n\n" +
            "§e• §fJoin or create nations\n" +
            "§e• §fBuild cities and claim territory\n" +
            "§e• §fBuy and sell land\n" +
            "§e• §fParticipate in government\n\n" +
            "§7Use §e/sc gui§7 to open the main menu.\n" +
            "§7Use §e/sc help§7 for a list of commands.\n\n" +
            "§aGood luck on your journey!",
            playerName
        );
        sendSystemMail(playerId, Mail.MailType.WELCOME, subject, body);
    }

    // ==================== Persistence ====================

    public void markDirty() {
        dirty = true;
    }

    public void save() {
        if (!dirty || savePath == null) return;

        try {
            JsonObject root = new JsonObject();

            // Save player mailboxes
            JsonObject playersJson = new JsonObject();
            for (Map.Entry<UUID, Mailbox> entry : playerMailboxes.entrySet()) {
                playersJson.add(entry.getKey().toString(), serializeMailbox(entry.getValue()));
            }
            root.add("players", playersJson);

            // Save city mailboxes
            JsonObject citiesJson = new JsonObject();
            for (Map.Entry<UUID, Mailbox> entry : cityMailboxes.entrySet()) {
                citiesJson.add(entry.getKey().toString(), serializeMailbox(entry.getValue()));
            }
            root.add("cities", citiesJson);

            // Save state mailboxes
            JsonObject statesJson = new JsonObject();
            for (Map.Entry<UUID, Mailbox> entry : stateMailboxes.entrySet()) {
                statesJson.add(entry.getKey().toString(), serializeMailbox(entry.getValue()));
            }
            root.add("states", statesJson);

            // Save nation mailboxes
            JsonObject nationsJson = new JsonObject();
            for (Map.Entry<UUID, Mailbox> entry : nationMailboxes.entrySet()) {
                nationsJson.add(entry.getKey().toString(), serializeMailbox(entry.getValue()));
            }
            root.add("nations", nationsJson);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(savePath, gson.toJson(root));

            dirty = false;
            StateCraft.LOGGER.debug("Saved mail data");
        } catch (IOException e) {
            StateCraft.LOGGER.error("Failed to save mail data", e);
        }
    }

    public void load() {
        if (savePath == null || !Files.exists(savePath)) {
            StateCraft.LOGGER.info("No mail data found, starting fresh");
            return;
        }

        try {
            String json = Files.readString(savePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Load player mailboxes
            if (root.has("players")) {
                JsonObject playersJson = root.getAsJsonObject("players");
                for (Map.Entry<String, JsonElement> entry : playersJson.entrySet()) {
                    UUID id = UUID.fromString(entry.getKey());
                    Mailbox mailbox = deserializeMailbox(entry.getValue().getAsJsonObject(),
                        id, Mail.RecipientType.PLAYER);
                    playerMailboxes.put(id, mailbox);
                }
            }

            // Load city mailboxes
            if (root.has("cities")) {
                JsonObject citiesJson = root.getAsJsonObject("cities");
                for (Map.Entry<String, JsonElement> entry : citiesJson.entrySet()) {
                    UUID id = UUID.fromString(entry.getKey());
                    Mailbox mailbox = deserializeMailbox(entry.getValue().getAsJsonObject(),
                        id, Mail.RecipientType.CITY);
                    cityMailboxes.put(id, mailbox);
                }
            }

            // Load state mailboxes
            if (root.has("states")) {
                JsonObject statesJson = root.getAsJsonObject("states");
                for (Map.Entry<String, JsonElement> entry : statesJson.entrySet()) {
                    UUID id = UUID.fromString(entry.getKey());
                    Mailbox mailbox = deserializeMailbox(entry.getValue().getAsJsonObject(),
                        id, Mail.RecipientType.STATE);
                    stateMailboxes.put(id, mailbox);
                }
            }

            // Load nation mailboxes
            if (root.has("nations")) {
                JsonObject nationsJson = root.getAsJsonObject("nations");
                for (Map.Entry<String, JsonElement> entry : nationsJson.entrySet()) {
                    UUID id = UUID.fromString(entry.getKey());
                    Mailbox mailbox = deserializeMailbox(entry.getValue().getAsJsonObject(),
                        id, Mail.RecipientType.NATION);
                    nationMailboxes.put(id, mailbox);
                }
            }

            StateCraft.LOGGER.info("Loaded mail data: {} player, {} city, {} state, {} nation mailboxes",
                playerMailboxes.size(), cityMailboxes.size(), stateMailboxes.size(), nationMailboxes.size());
        } catch (Exception e) {
            StateCraft.LOGGER.error("Failed to load mail data", e);
        }
    }

    private JsonObject serializeMailbox(Mailbox mailbox) {
        JsonObject obj = new JsonObject();
        obj.addProperty("maxMessages", mailbox.getMaxMessages());
        obj.addProperty("notificationsEnabled", mailbox.isNotificationsEnabled());

        JsonArray messagesArray = new JsonArray();
        for (Mail mail : mailbox.getMessagesInternal()) {
            messagesArray.add(serializeMail(mail));
        }
        obj.add("messages", messagesArray);

        return obj;
    }

    private Mailbox deserializeMailbox(JsonObject obj, UUID ownerId, Mail.RecipientType ownerType) {
        Mailbox mailbox = new Mailbox(ownerId, ownerType);

        if (obj.has("maxMessages")) {
            mailbox.setMaxMessages(obj.get("maxMessages").getAsInt());
        }
        if (obj.has("notificationsEnabled")) {
            mailbox.setNotificationsEnabled(obj.get("notificationsEnabled").getAsBoolean());
        }

        List<Mail> messages = new ArrayList<>();
        if (obj.has("messages")) {
            JsonArray messagesArray = obj.getAsJsonArray("messages");
            for (JsonElement elem : messagesArray) {
                Mail mail = deserializeMail(elem.getAsJsonObject());
                if (mail != null) {
                    messages.add(mail);
                }
            }
        }
        mailbox.loadMessages(messages);

        return mailbox;
    }

    private JsonObject serializeMail(Mail mail) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", mail.getId().toString());
        if (mail.getSenderId() != null) {
            obj.addProperty("senderId", mail.getSenderId().toString());
        }
        obj.addProperty("senderName", mail.getSenderName());
        obj.addProperty("recipientId", mail.getRecipientId().toString());
        obj.addProperty("recipientType", mail.getRecipientType().name());
        obj.addProperty("type", mail.getType().name());
        obj.addProperty("subject", mail.getSubject());
        obj.addProperty("body", mail.getBody());
        obj.addProperty("timestamp", mail.getTimestamp());
        obj.addProperty("read", mail.isRead());
        obj.addProperty("archived", mail.isArchived());
        return obj;
    }

    @Nullable
    private Mail deserializeMail(JsonObject obj) {
        try {
            UUID id = UUID.fromString(obj.get("id").getAsString());
            UUID senderId = obj.has("senderId") && !obj.get("senderId").isJsonNull()
                ? UUID.fromString(obj.get("senderId").getAsString()) : null;
            String senderName = obj.get("senderName").getAsString();
            UUID recipientId = UUID.fromString(obj.get("recipientId").getAsString());
            Mail.RecipientType recipientType = Mail.RecipientType.valueOf(obj.get("recipientType").getAsString());
            Mail.MailType type = Mail.MailType.valueOf(obj.get("type").getAsString());
            String subject = obj.get("subject").getAsString();
            String body = obj.get("body").getAsString();
            long timestamp = obj.get("timestamp").getAsLong();
            boolean read = obj.get("read").getAsBoolean();
            boolean archived = obj.get("archived").getAsBoolean();

            return new Mail(id, senderId, senderName, recipientId, recipientType,
                type, subject, body, timestamp, read, archived);
        } catch (Exception e) {
            StateCraft.LOGGER.warn("Failed to deserialize mail", e);
            return null;
        }
    }

    /**
     * Clear all data (for testing)
     */
    public void clear() {
        playerMailboxes.clear();
        cityMailboxes.clear();
        stateMailboxes.clear();
        nationMailboxes.clear();
        dirty = true;
    }
}

