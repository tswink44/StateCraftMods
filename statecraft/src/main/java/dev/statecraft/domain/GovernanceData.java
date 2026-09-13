package dev.statecraft.domain;

import dev.statecraft.api.GovernanceAccess.Kind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The persistence boundary. All times are UTC epoch milliseconds and all money is in cents. */
public class GovernanceData {
    public static final int CURRENT_SCHEMA = 2;
    // Gson uses this legacy default when older sections omit the version field.
    public int schemaVersion = 1;
    public long lastTick;
    public boolean economySeen;
    public Map<String, Player> players = new LinkedHashMap<>();
    public Map<String, Government> governments = new LinkedHashMap<>();
    public Map<String, Claim> claims = new LinkedHashMap<>();
    public Map<String, Invitation> invitations = new LinkedHashMap<>();
    public Map<String, Election> elections = new LinkedHashMap<>();
    public Map<String, Bill> bills = new LinkedHashMap<>();
    public Map<String, List<Law>> laws = new LinkedHashMap<>();
    public Map<String, Emergency> emergencies = new LinkedHashMap<>();
    public Map<String, Relation> relations = new LinkedHashMap<>();
    public Map<String, DiplomaticProposal> diplomacy = new LinkedHashMap<>();
    public Map<String, Company> companies = new LinkedHashMap<>();
    public Map<String, ShareReservation> shareReservations = new LinkedHashMap<>();
    public Map<String, CompanyProposal> companyProposals = new LinkedHashMap<>();
    public Map<String, Contract> contracts = new LinkedHashMap<>();
    public List<History> history = new ArrayList<>();

    public static class Player {
        public String id;
        public String name;
        // Membership is canonical here, not duplicated in government member indexes.
        public String nationId;
        public String stateId;
        public String cityId;
        public boolean autoClaim;
        public boolean bypass;
        public long lastSeen;
        public List<Mail> inbox = new ArrayList<>();
        public List<Mail> sent = new ArrayList<>();
    }

    public static class Government {
        public String id;
        public Kind kind;
        public String name;
        public String parentId;
        public String leader;
        public String description = "";
        public String tag = "";
        public String flag = "";
        public long createdAt;
        public long lastEmergencyAt = -1;
        public Set<String> officers = new LinkedHashSet<>();
        public Map<String, String> settings = new LinkedHashMap<>();
        public List<Mail> inbox = new ArrayList<>();
        public List<Mail> sent = new ArrayList<>();
    }

    public static class Claim {
        public String key;
        public String nationId;
        public String stateId;
        public String cityId;
        public String ownerAccount;
        public int improvements;
        public long claimedAt;
        public Map<String, Set<String>> permits = new LinkedHashMap<>();
    }

    public static class Invitation {
        public String id;
        public String governmentId;
        public String companyId;
        public String playerId;
        public String invitedBy;
        public long expiresAt;
    }

    public static class Election {
        public String nationId;
        public long nextStartAt;
        public long startedAt;
        public long endsAt;
        public boolean voting;
        public boolean scheduleExhausted;
        public Set<String> candidates = new LinkedHashSet<>();
        public Set<String> electorate = new LinkedHashSet<>();
        public Map<String, String> votes = new LinkedHashMap<>();
        public List<History> history = new ArrayList<>();
    }

    public static class Bill {
        public String id;
        public String nationId;
        public String author;
        public String title;
        public String text;
        public String policy;
        public String value;
        public String treatyId;
        public boolean amendment;
        public String status = "DEBATE";
        public long createdAt;
        public long debateEndsAt;
        public long voteEndsAt;
        public long decisionEndsAt;
        public Set<String> electorate = new LinkedHashSet<>();
        public Map<String, String> votes = new LinkedHashMap<>();
        public List<History> history = new ArrayList<>();
    }

    public static class Law {
        public String id;
        public String title;
        public String text;
        public String policy;
        public String value;
        public boolean amendment;
        public boolean roleplayOnly;
        public long enactedAt;
    }

    public static class Emergency {
        public String nationId;
        public String policy;
        public String value;
        public String reason;
        public String author;
        public long expiresAt;
    }

    public static class Relation {
        public String first;
        public String second;
        public String status = "NEUTRAL";
        public long warStartedAt;
        public long truceUntil;
    }

    public static class DiplomaticProposal {
        public String id;
        public String type;
        public String fromNation;
        public String toNation;
        public String status = "PROPOSED";
        public String message = "";
        public long createdAt;
        public long expiresAt;
        public long offeredCents;
        public long demandedCents;
        public List<ChunkTerm> chunks = new ArrayList<>();
        public Set<String> ratified = new LinkedHashSet<>();
        public String lastError = "";
    }

    public static class ChunkTerm {
        public String key;
        public String fromNation;
        public String fromState;
        public String fromCity;
        public String toNation;
        public String toState;
        public String toCity;
    }

    public static class Company {
        public String id;
        public String name;
        public String description = "";
        public String owner;
        public long createdAt;
        public long totalShares;
        public Set<String> members = new LinkedHashSet<>();
        public Set<String> officers = new LinkedHashSet<>();
        public Map<String, Long> shares = new LinkedHashMap<>();
    }

    public static class ShareReservation {
        public String reference;
        public String companyId;
        public String owner;
        public long quantity;
    }

    public static class CompanyProposal {
        public String id;
        public String companyId;
        public String author;
        public String type;
        public String value;
        public String title;
        public String text;
        public String status = "VOTING";
        public String lastError = "";
        public long createdAt;
        public long endsAt;
        public long executionEndsAt;
        public Map<String, Long> electorate = new LinkedHashMap<>();
        public Map<String, String> votes = new LinkedHashMap<>();
    }

    public static class Contract {
        public String id;
        public String governmentId;
        public String author;
        public String title;
        public String description;
        public String status = "OPEN";
        public long createdAt;
        public long bidEndsAt;
        public long reviewEndsAt;
        public long escrowCents;
        public String winner;
        public String payeeAccount;
        public String awardedBy;
        public String submittedBy;
        public String completionNote = "";
        public List<String> chunks = new ArrayList<>();
        public Map<String, Bid> bids = new LinkedHashMap<>();
    }

    public static class Bid {
        public String bidder;
        public String account;
        public long cents;
        public String text;
        public long createdAt;
    }

    public static class Mail {
        public String id;
        public String sender;
        public String recipient;
        public String subject;
        public String body;
        public long sentAt;
        public boolean read;
    }

    public static class History {
        public long at;
        public String category;
        public String entityId;
        public String text;
    }
}
