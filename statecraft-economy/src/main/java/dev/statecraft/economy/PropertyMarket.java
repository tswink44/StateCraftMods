package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class PropertyMarket {
    private final EconomyEngine e;
    private final ValuationEnvironment environment;

    PropertyMarket(EconomyEngine engine, ValuationEnvironment environment) {
        e = engine;
        this.environment = environment;
    }

    public EconomyData.Valuation value(String key) {
        e.ledger.thread();
        ChunkKey.parse(key);
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElseThrow(() -> new UserError("That chunk is not claimed."));
        EconomyData.Valuation existing = e.data.valuations.get(key);
        if (existing != null && existing.taxOwnerAccount == null) {
            existing.taxOwnerAccount = claim.ownerAccount();
            e.dirty.run();
        }
        if (existing != null && existing.nextRecalculationAt > e.clock.millis()) return existing;
        return recalculate(claim);
    }

    public EconomyData.Valuation recalculate(GovernanceAccess.ClaimView claim) {
        e.ledger.thread();
        ValuationEnvironment.Conditions input = environment.sample(claim);
        if (input == null || input.biome() == null || input.distanceChunks() < 0 || input.nearbyClaims() < 0) {
            throw new UserError("The property environment is unavailable.");
        }
        EconomyData.Valuation previous = e.data.valuations.get(claim.key());
        EconomyData.Valuation result = new EconomyData.Valuation();
        result.chunk = claim.key();
        result.taxOwnerAccount = previous == null || previous.taxOwnerAccount == null ? claim.ownerAccount() : previous.taxOwnerAccount;
        result.base = e.config.defaultChunkValueCents;
        int propertyRates = 0;
        for (GovernanceAccess.GovernmentView government : e.taxes.tiers(claim.key(), claim.nationId())) {
            String base = government.settings().get("baseChunkValue");
            if (base != null) {
                try { result.base = Money.nonNegative(Long.parseLong(base)); }
                catch (IllegalArgumentException | UserError problem) {
                    throw new UserError(government.name() + " has an invalid baseChunkValue (integer cents).");
                }
            }
            propertyRates += Taxation.rate(government, "propertyTaxBps");
        }
        result.locationBps = 10_000 + (int) (e.config.locationBonusBps / (1 + input.distanceChunks() / 64));
        result.biome = input.biome();
        result.biomeBps = Math.max(1000, Math.min(30_000, input.biomeBps()));
        if (previous != null && (result.biome.equals("unloaded") || result.biome.equals("unknown"))) {
            result.biome = previous.biome;
            result.biomeBps = previous.biomeBps;
        }
        result.demandBps = 10_000 + (int) Math.min(10_000L, (long) input.nearbyClaims() * e.config.demandBonusPerChunkBps);
        result.improvementBps = 10_000 + (int) Math.min(20_000L, Math.max(0L, claim.improvements()) * e.config.improvementBonusBps);
        result.taxDiscountBps = Math.min(5000, propertyRates);
        BigInteger computed = BigInteger.valueOf(result.base);
        for (int factor : new int[]{result.locationBps, result.biomeBps, result.demandBps,
                result.improvementBps, 10_000 - result.taxDiscountBps}) {
            computed = computed.multiply(BigInteger.valueOf(factor)).divide(BigInteger.valueOf(10_000));
        }
        result.value = computed.min(BigInteger.valueOf(Money.MAX)).longValueExact();
        result.calculatedAt = e.clock.millis();
        result.nextRecalculationAt = EconomyEngine.deadline(result.calculatedAt, e.config.valuationIntervalMillis);
        result.nextTaxAt = previous == null ? EconomyEngine.deadline(result.calculatedAt, e.config.propertyTaxPeriodMillis)
                : previous.nextTaxAt;
        e.data.valuations.put(claim.key(), result);
        e.dirty.run();
        return result;
    }

    public String list(Actor actor, String key, long price) {
        e.ledger.thread();
        Money.positive(price);
        GovernanceAccess.ClaimView claim = e.governance.claim(ChunkKey.parse(key).toString())
                .orElseThrow(() -> new UserError("That chunk is not claimed."));
        if (!actor.admin() && !e.governance.maySellProperty(actor.id(), key)) {
            throw new UserError("Only the private owner or an authorized property official may list this chunk.");
        }
        if (e.isClaimEncumbered(key)) throw new UserError("The chunk is already listed or pledged as loan collateral.");
        if (e.data.properties.size() >= e.config.maximumActiveListings) throw new UserError("The property listing limit has been reached.");
        e.validateDestination(claim.ownerAccount());
        EconomyData.PropertyListing listing = new EconomyData.PropertyListing();
        listing.id = EconomyEngine.id();
        listing.chunk = key;
        listing.ownerAccount = claim.ownerAccount();
        listing.price = price;
        listing.createdAt = e.clock.millis();
        listing.expiresAt = EconomyEngine.deadline(listing.createdAt, e.config.propertyListingLifetimeMillis);
        e.data.properties.put(key, listing);
        e.schedule("property", key);
        e.dirty.run();
        return listing.id;
    }

    public void delist(Actor actor, String key) {
        e.ledger.thread();
        EconomyData.PropertyListing listing = e.data.properties.get(key);
        if (listing == null) throw new UserError("That chunk is not for sale.");
        if (!actor.admin() && !listing.ownerAccount.equals(actor.account()) && !e.governance.maySellProperty(actor.id(), key)) {
            throw new UserError("You cannot delist someone else's property.");
        }
        e.data.properties.remove(key);
        e.dirty.run();
    }

    public Commerce.Settlement buy(Actor buyer, String key, boolean fundWithCash, InventoryPort inventory) {
        return buy(buyer, key, fundWithCash, null, inventory);
    }

    public Commerce.Settlement buy(Actor buyer, String key, boolean fundWithCash, String fundingBank, InventoryPort inventory) {
        e.ledger.thread();
        EconomyData.PropertyListing listing = e.data.properties.get(key);
        if (listing == null) throw new UserError("That chunk is not for sale.");
        if (listing.expiresAt <= e.clock.millis()) {
            e.data.properties.remove(key);
            e.dirty.run();
            throw new UserError("That property listing expired.");
        }
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElseThrow(() -> new UserError("That claim no longer exists."));
        if (!claim.ownerAccount().equals(listing.ownerAccount)) throw new UserError("The property's ownership changed; delist and relist it.");
        if (buyer.account().equals(listing.ownerAccount)) throw new UserError("You already own this property.");
        if (!buyer.admin() && !e.governance.mayBuyProperty(buyer.id(), key)) {
            throw new UserError("You are not eligible to buy this property.");
        }
        if (e.banking.encumbers(key)) throw new UserError("The property is pledged as collateral.");
        e.validateDestination(listing.ownerAccount);
        settleDueBeforeTransfer(key);
        Commerce.Settlement settlement = e.commerce.settlement(buyer, listing.ownerAccount, key, claim.nationId(),
                listing.price, 0, "Property purchase " + listing.id);
        InventoryPort.Plan cash = inventory.plan();
        long deposit = 0;
        if (fundWithCash) {
            inventory.require(InventoryPort.Utility.ATM, InventoryPort.Utility.VAULT);
            deposit = e.values.removeCash(cash);
        }
        List<Ledger.Adjustment> adjustments = deposit == 0 ? List.of()
                : List.of(new Ledger.Adjustment(buyer.account(), deposit, true, "system:cash", "Cash funding for property"));
        if (fundingBank != null) e.banking.bank(fundingBank, true);
        long shortage = BigInteger.valueOf(settlement.buyerCost()).subtract(BigInteger.valueOf(e.balance(buyer.account())))
                .subtract(BigInteger.valueOf(deposit)).max(BigInteger.ZERO).longValueExact();
        Banking.WalletFunding bankFunding = fundingBank != null && shortage > 0
                ? e.banking.walletFunding(buyer, fundingBank, shortage) : null;
        List<dev.statecraft.api.EconomyAccess.Transfer> transfers = new ArrayList<>(settlement.transfers());
        if (bankFunding != null) transfers.add(bankFunding.transfer());
        Ledger.Plan payment = e.ledger.prepare(transfers, adjustments, true,
                bankFunding == null ? Map.of() : bankFunding.reserveOverrides());
        if (fundWithCash) cash.checkUnchanged();
        e.data.properties.remove(key);
        try {
            payment.commitWith(() -> {
                if (fundWithCash) cash.commit();
                try { e.governance.transferProperty(key, buyer.account()); }
                catch (RuntimeException | Error failure) {
                    if (fundWithCash) cash.rollback();
                    throw failure;
                }
                if (bankFunding != null) bankFunding.commit();
            });
        } catch (RuntimeException | Error failure) {
            e.data.properties.put(key, listing);
            throw failure;
        }
        e.taxes.record(buyer.account(), settlement.taxes().buyer(), key);
        e.taxes.record(listing.ownerAccount, settlement.taxes().seller(), key);
        transferred(key, buyer.account());
        if (listing.ownerAccount.startsWith("player:")) {
            e.mail(listing.ownerAccount.substring(7), "Property sold", key + " sold for net "
                    + Money.format(settlement.sellerNet()) + ". Political claim ownership is unchanged.");
        }
        e.dirty.run();
        return settlement;
    }

    boolean tickListing(String key) {
        EconomyData.PropertyListing listing = e.data.properties.get(key);
        if (listing == null) return false;
        if (listing.expiresAt > e.clock.millis()) return true;
        e.data.properties.remove(key);
        if (listing.ownerAccount.startsWith("player:")) {
            e.mail(listing.ownerAccount.substring(7), "Property listing expired", key + " is no longer offered for sale.");
        }
        e.dirty.run();
        return false;
    }

    boolean tickClaim(String key) {
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElse(null);
        if (claim == null) {
            if (e.data.valuations.remove(key) != null) e.dirty.run();
            return false;
        }
        EconomyData.Valuation value = value(key);
        if (!claim.ownerAccount().equals(value.taxOwnerAccount)) {
            settleDueBeforeTransfer(key);
            value.taxOwnerAccount = claim.ownerAccount();
            e.dirty.run();
        }
        int processed = 0;
        while (value.nextTaxAt <= e.clock.millis() && processed++ < e.config.catchUpPeriodsPerTick) {
            long next = EconomyEngine.deadline(value.nextTaxAt, e.config.propertyTaxPeriodMillis);
            List<Taxation.Charge> charges = e.taxes.quote(key, claim.nationId(), value.value, "propertyTaxBps");
            for (Taxation.Charge charge : charges) e.taxes.assess(value.taxOwnerAccount, charge, key);
            value.nextTaxAt = next;
            e.dirty.run();
        }
        if (e.config.mandatoryPropertyTaxes) e.taxes.pay(claim.ownerAccount(), Money.MAX, true);
        return true;
    }

    void settleDueBeforeTransfer(String key) {
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElseThrow(() -> new UserError("The claimed property disappeared."));
        EconomyData.Valuation value = value(key);
        if (value.nextTaxAt > e.clock.millis()) return;
        long periods = (e.clock.millis() - value.nextTaxAt) / e.config.propertyTaxPeriodMillis + 1;
        long next = BigInteger.valueOf(value.nextTaxAt).add(BigInteger.valueOf(periods)
                .multiply(BigInteger.valueOf(e.config.propertyTaxPeriodMillis))).longValueExact();
        List<Taxation.Charge> charges = e.taxes.quote(key, claim.nationId(), value.value, "propertyTaxBps");
        for (Taxation.Charge charge : charges) e.taxes.assessPeriods(value.taxOwnerAccount, charge, key, periods);
        value.nextTaxAt = next;
        e.dirty.run();
    }

    void transferred(String key, String ownerAccount) {
        EconomyData.Valuation value = e.data.valuations.get(key);
        if (value != null) value.taxOwnerAccount = ownerAccount;
        e.dirty.run();
    }
}
