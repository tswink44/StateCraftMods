package dev.statecraft.api.ui;

import java.util.Objects;

public record EntityRef(String namespace, Kind kind, String id) {
    public enum Kind {
        NONE, GOVERNMENT, COMPANY, CLAIM, ELECTION, BILL, LAW, DIPLOMACY, COMPANY_PROPOSAL,
        CONTRACT, INVITATION, MAIL, ACCOUNT, BANK, LOAN, MARKET_LISTING, STOCK_LISTING,
        ARREARS, DELIVERY, OPERATION, DASHBOARD, PLAYER
    }

    public static final EntityRef NONE = new EntityRef("", Kind.NONE, "");

    public EntityRef {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(id);
        if (kind == Kind.NONE) {
            if (!namespace.isEmpty() || !id.isEmpty()) throw new IllegalArgumentException("Invalid empty entity.");
        } else if (!namespace.matches("[a-z][a-z0-9_]{0,47}") || id.isBlank() || id.length() > 256) {
            throw new IllegalArgumentException("Invalid entity reference.");
        }
    }

    public boolean present() { return kind != Kind.NONE; }
    public String page() { return namespace + ":detail"; }
}
