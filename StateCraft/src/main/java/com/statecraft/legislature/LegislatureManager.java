package com.statecraft.legislature;

import com.statecraft.StateCraft;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.Nation;
import com.statecraft.mail.Mail;
import com.statecraft.mail.MailManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for all nation legislatures
 * Handles tick processing, bill transitions, and notifications
 */
public class LegislatureManager {

    private static LegislatureManager instance;

    private final Map<UUID, Legislature> legislatures;  // nationId -> Legislature
    private boolean dirty = false;

    private LegislatureManager() {
        this.legislatures = new ConcurrentHashMap<>();
    }

    public static LegislatureManager getInstance() {
        if (instance == null) {
            instance = new LegislatureManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    /**
     * Get or create a legislature for a nation
     */
    public Legislature getOrCreateLegislature(UUID nationId) {
        return legislatures.computeIfAbsent(nationId, Legislature::new);
    }

    /**
     * Get a legislature for a nation (may return null)
     */
    public Legislature getLegislature(UUID nationId) {
        return legislatures.get(nationId);
    }

    /**
     * Remove a legislature when a nation is disbanded
     */
    public void removeLegislature(UUID nationId) {
        legislatures.remove(nationId);
        markDirty();
    }

    /**
     * Called every server tick to process bill transitions
     */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Legislature legislature : legislatures.values()) {
            tickLegislature(legislature, now, server);
        }
    }

    private void tickLegislature(Legislature legislature, long now, MinecraftServer server) {
        Nation nation = ChunkClaimManager.getInstance().getNation(legislature.getNationId());
        if (nation == null) return;

        List<UUID> toArchive = new ArrayList<>();

        for (Bill bill : legislature.getActiveBills()) {
            switch (bill.getStatus()) {
                case DEBATE:
                    // Check if debate period ended
                    if (now >= bill.getDebateEndTime()) {
                        long votingDuration = StateCraftConfig.LEGISLATURE_VOTING_HOURS.get() * 60 * 60 * 1000L;
                        bill.startVoting(now + votingDuration);
                        notifyLegislatureMembers(legislature, nation, server,
                            "§6[Legislature] §eVoting has begun on: §f" + bill.getTitle());
                        markDirty();
                    }
                    break;

                case VOTING:
                    // Check if voting period ended
                    if (now >= bill.getVoteEndTime()) {
                        Set<UUID> voters = legislature.getVotingMembers(nation);
                        bill.tallyVotes(
                            voters.size(),
                            StateCraftConfig.LEGISLATURE_QUORUM_PERCENT.get(),
                            StateCraftConfig.LEGISLATURE_VETO_OVERRIDE_PERCENT.get()
                        );

                        if (bill.getStatus() == Bill.Status.PASSED) {
                            if (bill.isVetoProof()) {
                                // Auto-enact veto-proof bills
                                enactBill(legislature, bill, nation, server);
                                // Notify leader
                                sendMailToLeader(nation, server,
                                    "Veto-Proof Bill Enacted",
                                    "Bill " + bill.getBillNumber() + " (" + bill.getTitle() +
                                    ") passed with a veto-proof majority and has been automatically enacted.");
                            } else {
                                // Notify leader of pending bill
                                sendMailToLeader(nation, server,
                                    "Bill Awaiting Your Action",
                                    "Bill " + bill.getBillNumber() + " (" + bill.getTitle() +
                                    ") has passed the legislature. You may sign it into law or veto it.");
                            }
                        } else {
                            notifyLegislatureMembers(legislature, nation, server,
                                "§6[Legislature] §cBill failed: §f" + bill.getTitle() +
                                " §7(Yes: " + bill.getYesVotes() + ", No: " + bill.getNoVotes() + ")");
                            toArchive.add(bill.getBillId());
                        }
                        markDirty();
                    }
                    break;

                case PASSED:
                    // Check for expiration (leader has 48 hours to act on non-veto-proof bills)
                    long passedTime = bill.getVoteEndTime();  // Use vote end as passed time
                    if (now - passedTime > 48 * 60 * 60 * 1000L) {
                        // Auto-enact after 48 hours of no action
                        enactBill(legislature, bill, nation, server);
                        sendMailToLeader(nation, server,
                            "Bill Auto-Enacted",
                            "Bill " + bill.getBillNumber() + " (" + bill.getTitle() +
                            ") has been automatically enacted after 48 hours without action.");
                    }
                    break;

                case VETOED:
                case ENACTED:
                case FAILED:
                case EXPIRED:
                    toArchive.add(bill.getBillId());
                    break;
            }
        }

        // Archive completed bills
        for (UUID billId : toArchive) {
            legislature.archiveBill(billId);
        }

        // Check emergency power expirations
        for (Map.Entry<EmergencyPower, Long> entry : legislature.getActiveEmergencyPowers().entrySet()) {
            if (entry.getValue() > 0 && now >= entry.getValue()) {
                legislature.deactivateEmergencyPower(entry.getKey());
                notifyLegislatureMembers(legislature, nation, server,
                    "§6[Emergency] §e" + entry.getKey().getDisplayName() + " has expired.");
                markDirty();
            }
        }
    }

    /**
     * Enact a bill - apply its policy changes and add to codex
     */
    public void enactBill(Legislature legislature, Bill bill, Nation nation, MinecraftServer server) {
        bill.enact();

        // Apply policy changes
        for (Map.Entry<PolicyType, String> change : bill.getPolicyChanges().entrySet()) {
            applyPolicyChange(nation, change.getKey(), change.getValue());
        }

        // Create law and add to codex
        String lawNumber = legislature.getCodex().generateLawNumber(Law.Type.NATION_LAW);
        Law law = new Law(
            nation.getId(),
            lawNumber,
            Law.Type.NATION_LAW,
            bill.getTitle(),
            bill.getDescription(),
            bill.getAuthorName(),
            bill.getPolicyChanges(),
            bill.getYesVotes(),
            bill.getNoVotes(),
            bill.getAbstainVotes(),
            bill.isVetoProof(),
            null  // Full text from custom laws
        );
        legislature.getCodex().addLaw(law);

        notifyLegislatureMembers(legislature, nation, server,
            "§6[Legislature] §aNew law enacted: §f" + lawNumber + " - " + bill.getTitle());

        markDirty();
    }

    /**
     * Create an executive order (emergency power)
     */
    public Law createExecutiveOrder(Legislature legislature, Nation nation, EmergencyPower power,
                                     String description, MinecraftServer server) {
        String lawNumber = legislature.getCodex().generateLawNumber(Law.Type.EXECUTIVE_ORDER);

        Map<PolicyType, String> changes = new HashMap<>();
        // Executive orders don't typically change policies directly, but we track them

        Law law = new Law(
            nation.getId(),
            lawNumber,
            Law.Type.EXECUTIVE_ORDER,
            power.getDisplayName(),
            description,
            getLeaderName(nation, server),
            changes,
            0, 0, 0, false,
            "Emergency power invoked: " + power.getDescription()
        );
        legislature.getCodex().addLaw(law);

        notifyLegislatureMembers(legislature, nation, server,
            "§6[Executive Order] §c" + lawNumber + ": " + power.getDisplayName());

        markDirty();
        return law;
    }

    /**
     * Apply policy changes from a map to a nation
     * Public method for use by packet handlers
     */
    public void applyPolicyChanges(Nation nation, Map<PolicyType, String> policyChanges) {
        for (Map.Entry<PolicyType, String> entry : policyChanges.entrySet()) {
            applyPolicyChange(nation, entry.getKey(), entry.getValue());
        }
        markDirty();
    }

    /**
     * Apply a policy change to a nation
     */
    private void applyPolicyChange(Nation nation, PolicyType policy, String value) {
        try {
            switch (policy) {
                case NATION_TAX_RATE:
                    // Would need economy integration
                    StateCraft.LOGGER.info("Policy change: Nation tax rate set to {}", value);
                    break;
                case STATE_PASS_THROUGH_RATE:
                    nation.setStatePassThroughRate(Double.parseDouble(value));
                    break;
                case MAX_STATES_PER_NATION:
                    nation.setMaxStates(Integer.parseInt(value));;
                    break;
                case OPEN_NATION:
                    nation.setOpen(Boolean.parseBoolean(value));
                    break;
                case OPEN_BORDERS:
                    // Would need to implement open borders tracking
                    StateCraft.LOGGER.info("Policy change: Open borders set to {}", value);
                    break;
                case DECLARE_WAR:
                    // Add to enemies
                    try {
                        UUID enemyId = UUID.fromString(value);
                        nation.addEnemy(enemyId);
                    } catch (IllegalArgumentException e) {
                        // Try to find nation by name
                        Nation enemy = ChunkClaimManager.getInstance().getNationByName(value);
                        if (enemy != null) {
                            nation.addEnemy(enemy.getId());
                        }
                    }
                    break;
                case DECLARE_PEACE:
                    try {
                        UUID enemyId = UUID.fromString(value);
                        nation.removeEnemy(enemyId);
                    } catch (IllegalArgumentException e) {
                        Nation enemy = ChunkClaimManager.getInstance().getNationByName(value);
                        if (enemy != null) {
                            nation.removeEnemy(enemy.getId());
                        }
                    }
                    break;
                case FORM_ALLIANCE:
                    try {
                        UUID allyId = UUID.fromString(value);
                        nation.addAlly(allyId);
                    } catch (IllegalArgumentException e) {
                        Nation ally = ChunkClaimManager.getInstance().getNationByName(value);
                        if (ally != null) {
                            nation.addAlly(ally.getId());
                        }
                    }
                    break;
                case BREAK_ALLIANCE:
                    try {
                        UUID allyId = UUID.fromString(value);
                        nation.removeAlly(allyId);
                    } catch (IllegalArgumentException e) {
                        Nation ally = ChunkClaimManager.getInstance().getNationByName(value);
                        if (ally != null) {
                            nation.removeAlly(ally.getId());
                        }
                    }
                    break;
                // Custom laws don't need direct application
                default:
                    StateCraft.LOGGER.info("Policy change (roleplay): {} = {}", policy.getDisplayName(), value);
            }
        } catch (Exception e) {
            StateCraft.LOGGER.error("Failed to apply policy change: {} = {}", policy, value, e);
        }
    }

    private void notifyLegislatureMembers(Legislature legislature, Nation nation,
                                           MinecraftServer server, String message) {
        Set<UUID> members = legislature.getVotingMembers(nation);
        members.add(nation.getLeaderId());  // Also notify leader

        for (UUID memberId : members) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private void sendMailToLeader(Nation nation, MinecraftServer server, String subject, String body) {
        MailManager mailManager = MailManager.getInstance();
        mailManager.sendSystemMail(nation.getLeaderId(), Mail.MailType.GOV_ANNOUNCEMENT, subject, body);
    }

    private String getLeaderName(Nation nation, MinecraftServer server) {
        ServerPlayer leader = server.getPlayerList().getPlayer(nation.getLeaderId());
        if (leader != null) {
            return leader.getName().getString();
        }
        return "Leader";
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag legislaturesList = new ListTag();
        for (Legislature legislature : legislatures.values()) {
            legislaturesList.add(legislature.save());
        }
        tag.put("legislatures", legislaturesList);

        return tag;
    }

    public void load(CompoundTag tag) {
        legislatures.clear();

        ListTag legislaturesList = tag.getList("legislatures", Tag.TAG_COMPOUND);
        for (int i = 0; i < legislaturesList.size(); i++) {
            Legislature legislature = Legislature.load(legislaturesList.getCompound(i));
            legislatures.put(legislature.getNationId(), legislature);
        }
    }
}



