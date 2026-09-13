package dev.statecraft.runtime;

import dev.statecraft.api.EconomyAccess;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class IntegrationLocks {
    public Set<String> accounts = new LinkedHashSet<>();
    public Set<String> claims = new LinkedHashSet<>();

    public EconomyAccess offlineAccess() {
        if (accounts == null || claims == null) {
            throw new IllegalStateException("Invalid persisted economy integration locks.");
        }
        return new EconomyAccess() {
            @Override public boolean available() { return false; }
            @Override public long balance(String account) { return UNAVAILABLE.balance(account); }
            @Override public void transferBatch(List<Transfer> transfers) { UNAVAILABLE.transferBatch(transfers); }
            @Override public boolean isClaimEncumbered(String chunkKey) { return claims.contains(chunkKey); }
            @Override public boolean isAccountInUse(String account) { return accounts.contains(account); }
            @Override public long valueOf(String chunkKey) { return UNAVAILABLE.valueOf(chunkKey); }
        };
    }
}
