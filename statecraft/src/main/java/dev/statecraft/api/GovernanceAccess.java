package dev.statecraft.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GovernanceAccess {
    enum Kind { NATION, STATE, CITY }

    record GovernmentView(String id, Kind kind, String name, String parentId, String nationId,
                          UUID leader, Set<UUID> members, Map<String, String> settings) {
        public String account() { return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id; }
    }

    record CompanyView(String id, String name, UUID owner, Set<UUID> members, Map<UUID, Long> shares) {
        public String account() { return "company:" + id; }
    }

    record ClaimView(String key, String dimension, int x, int z, String cityId, String stateId,
                     String nationId, String ownerAccount, int improvements, long claimedAt) {}

    Collection<GovernmentView> governments();
    Optional<GovernmentView> government(String idOrName);
    Collection<CompanyView> companies();
    Optional<CompanyView> company(String idOrName);
    Collection<ClaimView> claims();
    Optional<ClaimView> claim(String key);
    Optional<String> nationOf(UUID player);
    boolean mayManageGovernment(UUID player, String governmentId);
    boolean mayManageCompany(UUID player, String companyId);
    boolean mayAccessAccount(UUID player, String account);
    Set<String> accountsFor(UUID player);
    boolean maySellProperty(UUID player, String chunkKey);
    boolean mayBuyProperty(UUID player, String chunkKey);
    void transferProperty(String chunkKey, String ownerAccount);
    long sharesOf(String companyId, UUID shareholder);
    long availableShares(String companyId, UUID shareholder);
    void transferShares(String companyId, UUID from, UUID to, long quantity);
    void reserveShares(String companyId, UUID owner, String reference, long quantity);
    void releaseShares(String reference);
    void settleShares(String reference, UUID buyer, long quantity);
    void mail(UUID recipient, String subject, String body);
    void governmentMail(String governmentId, String subject, String body);
}
