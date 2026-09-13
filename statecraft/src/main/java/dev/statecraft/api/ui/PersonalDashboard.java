package dev.statecraft.api.ui;

import dev.statecraft.api.UserError;
import java.util.List;
import java.util.Objects;

public record PersonalDashboard(String name, Entry nation, Entry state, Entry city, boolean economyAvailable,
                                Page accounts, Page companies, Page propertyCities, UiText notice) {
    public static final int PAGE_SIZE = 12;
    public static final int MAX_OFFSET = 100_000;

    public enum Section { ACCOUNTS, COMPANIES, PROPERTY_CITIES }

    public record Request(int accounts, int companies, int propertyCities) {
        public static final Request FIRST = new Request(0, 0, 0);

        public Request {
            for (int offset : new int[]{accounts, companies, propertyCities}) {
                if (offset < 0 || offset > MAX_OFFSET || offset % PAGE_SIZE != 0) {
                    throw new UserError("Invalid dashboard page.");
                }
            }
        }

        public Request page(Section section, int offset) {
            return switch (section) {
                case ACCOUNTS -> new Request(offset, companies, propertyCities);
                case COMPANIES -> new Request(accounts, offset, propertyCities);
                case PROPERTY_CITIES -> new Request(accounts, companies, offset);
            };
        }
    }

    public record Entry(UiText name, UiText detail, UiQuery target) {
        public Entry {
            Objects.requireNonNull(name);
            Objects.requireNonNull(detail);
            Objects.requireNonNull(target);
            if (name.fallback().isBlank() || name.fallback().length() > 256 || detail.fallback().length() > 256
                    || name.characters() + detail.characters() > 2048) {
                throw new IllegalArgumentException("Invalid dashboard entry.");
            }
        }
    }

    public record Page(List<Entry> entries, int offset, int total) {
        public static final Page EMPTY = new Page(List.of(), 0, 0);

        public Page {
            entries = List.copyOf(entries);
            if (entries.size() > PAGE_SIZE || offset < 0 || offset > MAX_OFFSET || offset % PAGE_SIZE != 0
                    || total < 0 || offset > Math.max(0, total - 1) || offset + entries.size() > total) {
                throw new IllegalArgumentException("Invalid dashboard page.");
            }
        }

        public boolean more() { return offset + entries.size() < total; }

        public static Page of(List<Entry> entries, int requestedOffset) {
            if (requestedOffset < 0 || requestedOffset > MAX_OFFSET || requestedOffset % PAGE_SIZE != 0) {
                throw new UserError("Invalid dashboard page.");
            }
            int last = entries.isEmpty() ? 0 : (entries.size() - 1) / PAGE_SIZE * PAGE_SIZE;
            int offset = Math.min(requestedOffset, last);
            return new Page(entries.subList(offset, Math.min(entries.size(), offset + PAGE_SIZE)), offset, entries.size());
        }
    }

    public PersonalDashboard {
        Objects.requireNonNull(name);
        Objects.requireNonNull(accounts);
        Objects.requireNonNull(companies);
        Objects.requireNonNull(propertyCities);
        Objects.requireNonNull(notice);
        if (name.isBlank() || name.length() > 64 || notice.characters() > 1024) {
            throw new IllegalArgumentException("Invalid personal dashboard.");
        }
    }

    public Page section(Section section) {
        return switch (section) {
            case ACCOUNTS -> accounts;
            case COMPANIES -> companies;
            case PROPERTY_CITIES -> propertyCities;
        };
    }
}
