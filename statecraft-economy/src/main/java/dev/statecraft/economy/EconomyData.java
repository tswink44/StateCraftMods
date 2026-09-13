package dev.statecraft.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One section of StateCraft's atomic, versioned world snapshot. */
public final class EconomyData {
    public int schemaVersion = 1;
    public Map<String, Account> accounts = new LinkedHashMap<>();
    public Map<String, String> playerNames = new LinkedHashMap<>();
    public Map<String, String> selectedAccounts = new LinkedHashMap<>();
    public List<Transaction> transactions = new ArrayList<>();
    public List<TaxEntry> taxHistory = new ArrayList<>();
    public Map<String, Arrear> arrears = new LinkedHashMap<>();
    public Map<String, MarketListing> market = new LinkedHashMap<>();
    public Map<String, List<Delivery>> deliveries = new LinkedHashMap<>();
    public Map<String, HubQuota> hubQuotas = new LinkedHashMap<>();
    public List<Suggestion> suggestions = new ArrayList<>();
    public Map<String, PropertyListing> properties = new LinkedHashMap<>();
    public Map<String, Valuation> valuations = new LinkedHashMap<>();
    public Map<String, StockListing> stocks = new LinkedHashMap<>();
    public Map<String, CompanyFinance> companyFinance = new LinkedHashMap<>();
    public Map<String, Bank> banks = new LinkedHashMap<>();
    public Map<String, String> bankAssociations = new LinkedHashMap<>();
    public Map<String, Loan> loans = new LinkedHashMap<>();
    public List<BankEvent> bankHistory = new ArrayList<>();
    public List<String> notices = new ArrayList<>();

    public static final class Account {
        public long balance;
        public long dailyLimit = dev.statecraft.api.Money.MAX;
        public long spentDay = Long.MIN_VALUE;
        public long spent;
    }

    public record Transaction(String id, long time, String from, String to, long cents, String reason) {}
    public record TaxEntry(long time, String payer, String government, String kind, long cents,
                           String subject, String status) {}
    public record Suggestion(String player, String item, long proposedCents, long time, String reason) {}
    public record BankEvent(long time, String bank, String player, String kind, long cents, String detail) {}

    public static final class Arrear {
        public String id;
        public String payer;
        public String recipient;
        public String government;
        public String kind;
        public String subject;
        // A debt is never erased merely because it exceeds the maximum size of one payment.
        public String cents = "0";
        public long firstDue;
    }

    public static final class MarketListing {
        public String id;
        public String seller;
        public String sellerAccount;
        public String sourceChunk;
        public String sourceNation;
        public ItemLot item;
        public int remaining;
        public long unitPrice;
        public long createdAt;
        public long expiresAt;
    }

    public static final class Delivery {
        public String id;
        public ItemLot item;
        public String reason;
        public long createdAt;
    }

    public static final class HubQuota {
        public long day = Long.MIN_VALUE;
        public long gross;
        public long items;
    }

    public static final class PropertyListing {
        public String id;
        public String chunk;
        public String ownerAccount;
        public long price;
        public long createdAt;
        public long expiresAt;
    }

    public static final class Valuation {
        public String chunk;
        public String taxOwnerAccount;
        public long value;
        public long base;
        public String biome;
        public int locationBps;
        public int biomeBps;
        public int demandBps;
        public int improvementBps;
        public int taxDiscountBps;
        public long calculatedAt;
        public long nextTaxAt;
        public long nextRecalculationAt;
    }

    public static final class StockListing {
        public String id;
        public String company;
        public String seller;
        public String sourceChunk;
        public String sourceNation;
        public long remaining;
        public long unitPrice;
        public long createdAt;
        public long expiresAt;
    }

    public static final class CompanyFinance {
        public String company;
        public long nextFeeAt;
    }

    public static final class Bank {
        public String id;
        public String company;
        public String name;
        public String branding;
        public int depositInterestBps;
        public int loanInterestBps;
        public int originationFeeBps;
        public long depositFeeCents;
        public long withdrawalFeeCents;
        public long interestPeriodMillis;
        public boolean closed;
        public long openedAt;
        public Map<String, Deposit> deposits = new LinkedHashMap<>();
        public String account() { return "bank:" + id; }
    }

    public static final class Deposit {
        public long balance;
        public long principal;
        public String pendingInterest = "0";
        public String interestRemainder = "0";
        public long lastAccruedAt;
        public int rateBps;
        public long periodMillis;
    }

    public static final class Loan {
        public String id;
        public String bank;
        public String borrower;
        public String status = "REQUESTED";
        public long originalPrincipal;
        public long principal;
        public long interest;
        public long interestAccrued;
        public long interestCap;
        public String interestRemainder = "0";
        public int rateBps;
        public long originationFee;
        public int periods;
        public long periodMillis;
        public long requestedAt;
        public long applicationExpiresAt;
        public long issuedAt;
        public long lastAccruedAt;
        public long graceMillis;
        public int defaultMissedPayments;
        public String collateral;
        public boolean consentBalanceSeizure;
        public boolean consentRepossession;
        public boolean autoPay;
        public boolean collateralReleased;
        public boolean repossessed;
        public int reportedDuePeriod;
        public int missedPayments;
        public long lastNoticeAt;
        public String terms;
    }
}
