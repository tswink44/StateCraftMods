package dev.statecraft.api;

import java.util.List;

public interface EconomyAccess {
    record Transfer(String from, String to, long cents, String reason) {}

    boolean available();

    long balance(String account);

    void transferBatch(List<Transfer> transfers);

    default void transfer(String from, String to, long cents, String reason) {
        Money.nonNegative(cents);
        if (cents != 0) {
            transferBatch(List.of(new Transfer(from, to, cents, reason)));
        }
    }

    boolean isClaimEncumbered(String chunkKey);

    boolean isAccountInUse(String account);

    long valueOf(String chunkKey);

    EconomyAccess UNAVAILABLE = new EconomyAccess() {
        @Override public boolean available() { return false; }
        @Override public long balance(String account) { throw missing(); }
        @Override public void transferBatch(List<Transfer> transfers) {
            if (transfers.stream().anyMatch(t -> t.cents() != 0)) {
                throw missing();
            }
        }
        @Override public boolean isClaimEncumbered(String chunkKey) { return false; }
        @Override public boolean isAccountInUse(String account) { return false; }
        @Override public long valueOf(String chunkKey) { throw missing(); }
        private UserError missing() {
            return new UserError("This action requires StateCraft Economy.");
        }
    };
}
