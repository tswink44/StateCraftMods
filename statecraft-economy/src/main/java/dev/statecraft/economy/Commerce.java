package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess.Transfer;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Commerce {
    public record Sale(long gross, long net, long taxes) {}
    public record Settlement(List<Transfer> transfers, Taxation.TradeTaxes taxes, long buyerCost, long sellerNet, long fee) {}
    public record Dividend(long requested, long paidToShareholders, long corporateTax, long retainedRemainder) {}
    private final EconomyEngine e;

    Commerce(EconomyEngine engine) { e = engine; }

    public Sale sellHub(Actor actor, int quantity, InventoryPort inventory) {
        HubQuote quote = quoteHub(actor, quantity, inventory);
        quote.payment().commitWith(quote.items()::commit);
        e.data.hubQuotas.put(actor.id().toString(), quote.quota());
        e.taxes.record(actor.account(), quote.taxes(), quote.held().item());
        e.dirty.run();
        return quote.sale();
    }

    record HubQuote(Sale sale, List<Taxation.Charge> taxes, EconomyData.HubQuota quota,
                    ItemLot held, InventoryPort.Plan items, Ledger.Plan payment) {}

    HubQuote quoteHub(Actor actor, int quantity, InventoryPort inventory) {
        e.ledger.thread();
        inventory.require(InventoryPort.Utility.HUB);
        EconomyEngine.quantity(quantity);
        ItemLot held = inventory.held();
        long gross = Money.multiply(e.values.sellPrice(held.item()), quantity);
        long day = Math.floorDiv(e.clock.millis(), 86_400_000L);
        EconomyData.HubQuota old = e.data.hubQuotas.get(actor.id().toString());
        long priorGross = old != null && old.day >= day ? old.gross : 0;
        long priorItems = old != null && old.day >= day ? old.items : 0;
        long newGross = Money.add(priorGross, gross);
        if (newGross > e.config.hubDailyLimitCents || priorItems + quantity > e.config.hubDailyItemLimit) {
            throw new UserError("Your UTC-day Trading Hub sale limit would be exceeded.");
        }
        List<Taxation.Charge> charges = e.taxes.hub(actor, gross);
        long tax = Taxation.total(charges);
        if (tax > gross) throw new UserError("Combined sales and income tax rates exceed the sale proceeds.");
        InventoryPort.Plan items = inventory.plan();
        items.remove(held, quantity);
        Ledger.Plan payment = e.ledger.prepare(Taxation.transfers(actor.account(), charges, "Trading Hub tax"),
                List.of(new Ledger.Adjustment(actor.account(), gross, true, "system:hub", "Trading Hub: " + held.item())),
                true, Map.of());
        EconomyData.HubQuota quota = new EconomyData.HubQuota();
        quota.day = old == null ? day : Math.max(day, old.day);
        quota.gross = newGross;
        quota.items = priorItems + quantity;
        return new HubQuote(new Sale(gross, gross - tax, tax), charges, quota, held, items, payment);
    }

    public void suggest(Actor actor, String item, long cents, String reason) {
        e.ledger.thread();
        checkSuggestion(item, cents, reason);
        e.data.suggestions.removeIf(s -> s.player().equals(actor.id().toString()) && s.item().equals(item));
        e.data.suggestions.add(new EconomyData.Suggestion(actor.id().toString(), item, cents, e.clock.millis(), reason));
        Ledger.trim(e.data.suggestions, e.config.maximumSuggestions);
        e.dirty.run();
    }

    void checkSuggestion(String item, long cents, String reason) {
        if (item == null || !item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || e.itemStackSize.applyAsInt(item) < 1) {
            throw new UserError("Unknown item.");
        }
        Money.positive(cents);
        if (e.values.isCurrency(item)) throw new UserError("Currency cannot receive a Trading Hub sell price.");
        if (reason.length() > 256) throw new UserError("Keep suggestions to 256 characters.");
    }

    public String listMarket(Actor seller, int quantity, long unitPrice, InventoryPort inventory) {
        MarketOffer quote = quoteListMarket(seller, quantity, unitPrice, inventory);
        EconomyData.MarketListing listing = new EconomyData.MarketListing();
        listing.id = EconomyEngine.id();
        listing.seller = seller.id().toString();
        listing.sellerAccount = seller.account();
        listing.sourceChunk = seller.chunkKey();
        listing.sourceNation = e.governance.nationOf(seller.id()).orElse(null);
        listing.item = quote.held().withCount(quantity);
        listing.remaining = quantity;
        listing.unitPrice = unitPrice;
        listing.createdAt = e.clock.millis();
        listing.expiresAt = EconomyEngine.deadline(listing.createdAt, e.config.marketLifetimeMillis);
        quote.payment().commitWith(quote.items()::commit);
        e.data.market.put(listing.id, listing);
        e.schedule("market", listing.id);
        e.dirty.run();
        return listing.id;
    }

    record MarketOffer(ItemLot held, InventoryPort.Plan items, Ledger.Plan payment) {}

    MarketOffer quoteListMarket(Actor seller, int quantity, long unitPrice, InventoryPort inventory) {
        e.ledger.thread();
        EconomyEngine.quantity(quantity);
        Money.positive(unitPrice);
        Money.multiply(unitPrice, quantity);
        long listings = sellerListings(seller.id().toString());
        if (listings >= e.config.maximumListingsPerPlayer || e.data.market.size() >= e.config.maximumActiveListings) {
            throw new UserError("The marketplace listing limit has been reached.");
        }
        requireDeliverySpace(seller.id().toString(), 1);
        InventoryPort.Plan items = inventory.plan();
        ItemLot held = inventory.held();
        items.remove(held, quantity);
        EconomyEngine.deadline(e.clock.millis(), e.config.marketLifetimeMillis);
        return new MarketOffer(held, items, e.ledger.prepare(List.of(new Transfer(seller.account(), "system:fees",
                e.config.marketListingFeeCents, "Marketplace listing fee"))));
    }

    public Settlement settlement(Actor buyer, String sellerAccount, String sourceChunk, String sourceNation,
                                 long gross, int commissionBps, String reason) {
        Taxation.TradeTaxes taxes = e.taxes.trade(buyer, sellerAccount, sourceChunk, sourceNation, gross);
        long fee = Money.tax(gross, commissionBps);
        long deductions = Money.add(taxes.sellerTotal(), fee);
        if (deductions > gross) throw new UserError("Combined seller taxes and fees exceed sale proceeds.");
        long cost = Money.add(gross, taxes.buyerTotal());
        long net = gross - deductions;
        List<Transfer> payments = new ArrayList<>();
        payments.add(new Transfer(buyer.account(), sellerAccount, net, reason));
        payments.add(new Transfer(buyer.account(), "system:fees", fee, reason + " commission"));
        payments.addAll(Taxation.transfers(buyer.account(), taxes.buyer(), reason));
        payments.addAll(Taxation.transfers(buyer.account(), taxes.seller(), reason));
        return new Settlement(List.copyOf(payments), taxes, cost, net, fee);
    }

    public Settlement buyMarket(Actor buyer, String id, int quantity) {
        e.ledger.thread();
        EconomyEngine.quantity(quantity);
        market(id);
        MarketPurchase quote = quoteBuyMarket(buyer, id, quantity);
        EconomyData.MarketListing listing = quote.listing();
        Settlement settlement = quote.settlement();
        e.ledger.prepare(settlement.transfers()).commit();
        deliver(buyer.id().toString(), listing.item.withCount(quantity), "Marketplace purchase " + id);
        listing.remaining -= quantity;
        if (listing.remaining == 0) e.data.market.remove(id);
        recordSettlement(buyer, listing.sellerAccount, settlement, id);
        e.mail(listing.seller, "Marketplace sale", quantity + " × " + listing.item.item()
                + " sold. Net proceeds: " + Money.format(settlement.sellerNet()) + ".");
        e.dirty.run();
        return settlement;
    }

    record MarketPurchase(EconomyData.MarketListing listing, Settlement settlement) {}

    MarketPurchase quoteBuyMarket(Actor buyer, String id, int quantity) {
        e.ledger.thread();
        EconomyEngine.quantity(quantity);
        EconomyData.MarketListing listing = e.data.market.get(id);
        if (listing == null || listing.expiresAt <= e.clock.millis()) throw new UserError("That marketplace listing is unavailable or expired.");
        if (listing.seller.equals(buyer.id().toString())) throw new UserError("You cannot buy your own listing.");
        if (quantity > listing.remaining) throw new UserError("The listing does not have that many items.");
        requireDeliverySpace(buyer.id().toString(), 1);
        long gross = Money.multiply(listing.unitPrice, quantity);
        Settlement settlement = settlement(buyer, listing.sellerAccount, listing.sourceChunk, listing.sourceNation,
                gross, e.config.marketCommissionBps, "Marketplace purchase " + id);
        return new MarketPurchase(listing, settlement);
    }

    private void recordSettlement(Actor buyer, String sellerAccount, Settlement settlement, String subject) {
        e.taxes.record(buyer.account(), settlement.taxes().buyer(), subject);
        e.taxes.record(sellerAccount, settlement.taxes().seller(), subject);
    }

    public EconomyData.MarketListing market(String id) {
        EconomyData.MarketListing listing = e.data.market.get(id);
        if (listing == null) throw new UserError("Unknown or completed marketplace listing.");
        if (listing.expiresAt <= e.clock.millis()) {
            returnMarket(listing, "expired");
            throw new UserError("That marketplace listing expired. Its items were returned to the seller's delivery queue.");
        }
        return listing;
    }

    public void cancelMarket(Actor actor, String id) {
        e.ledger.thread();
        EconomyData.MarketListing listing = e.data.market.get(id);
        if (listing == null) throw new UserError("Unknown marketplace listing.");
        if (!actor.admin() && !listing.seller.equals(actor.id().toString())) throw new UserError("Only the seller may cancel this listing.");
        returnMarket(listing, "cancelled");
    }

    private void returnMarket(EconomyData.MarketListing listing, String reason) {
        deliver(listing.seller, listing.item.withCount(listing.remaining), "Listing " + reason + ": " + listing.id);
        e.data.market.remove(listing.id);
        e.mail(listing.seller, "Marketplace listing " + reason, "Collect " + listing.remaining + " × "
                + listing.item.item() + " with /sce market collect. Listing fees are nonrefundable.");
        e.dirty.run();
    }

    boolean tickMarket(String id) {
        EconomyData.MarketListing listing = e.data.market.get(id);
        if (listing == null) return false;
        if (listing.expiresAt <= e.clock.millis()) { returnMarket(listing, "expired"); return false; }
        return true;
    }

    private long sellerListings(String player) {
        return e.data.market.values().stream().filter(l -> l.seller.equals(player)).count();
    }

    private void requireDeliverySpace(String player, int newEntries) {
        long count = e.data.deliveries.getOrDefault(player, List.of()).size() + sellerListings(player);
        if (count + newEntries > e.config.maximumDeliveryEntries) {
            throw new UserError("Collect queued marketplace deliveries before creating more listings or purchases.");
        }
    }

    private void deliver(String player, ItemLot items, String reason) {
        EconomyData.Delivery delivery = new EconomyData.Delivery();
        delivery.id = EconomyEngine.id();
        delivery.item = items;
        delivery.reason = reason;
        delivery.createdAt = e.clock.millis();
        e.data.deliveries.computeIfAbsent(player, ignored -> new ArrayList<>()).add(delivery);
    }

    public int collect(Actor actor, InventoryPort inventory) {
        e.ledger.thread();
        List<EconomyData.Delivery> queue = e.data.deliveries.get(actor.id().toString());
        if (queue == null || queue.isEmpty()) return 0;
        CollectionQuote quote = quoteCollect(actor, inventory);
        if (quote.total() == 0) return 0;
        quote.items().commit();
        quote.collected().forEach((delivery, count) -> delivery.item = delivery.item.withCount(delivery.item.count() - count));
        queue.removeIf(delivery -> delivery.item.empty());
        if (queue.isEmpty()) e.data.deliveries.remove(actor.id().toString());
        e.dirty.run();
        return quote.total();
    }

    record CollectionQuote(InventoryPort.Plan items, Map<EconomyData.Delivery, Integer> collected, int total) {}

    CollectionQuote quoteCollect(Actor actor, InventoryPort inventory) {
        e.ledger.thread();
        List<EconomyData.Delivery> queue = e.data.deliveries.getOrDefault(actor.id().toString(), List.of());
        InventoryPort.Plan items = inventory.plan();
        Map<EconomyData.Delivery, Integer> collected = new LinkedHashMap<>();
        int total = 0, inspected = 0;
        for (EconomyData.Delivery delivery : queue) {
            if (++inspected > 32) break;
            int count = Math.min(items.capacity(delivery.item), delivery.item.count());
            if (count > 0) {
                items.add(delivery.item.withCount(count));
                collected.put(delivery, count);
                total += count;
            }
        }
        return new CollectionQuote(items, java.util.Collections.unmodifiableMap(collected), total);
    }

    public String listStock(Actor actor, String companyId, long quantity, long unitPrice) {
        GovernanceAccess.CompanyView company = quoteListStock(actor, companyId, quantity, unitPrice);
        EconomyData.StockListing listing = new EconomyData.StockListing();
        listing.id = "stock:" + EconomyEngine.id();
        listing.company = company.id();
        listing.seller = actor.id().toString();
        listing.sourceChunk = actor.chunkKey();
        listing.sourceNation = e.governance.nationOf(actor.id()).orElse(null);
        listing.remaining = quantity;
        listing.unitPrice = unitPrice;
        listing.createdAt = e.clock.millis();
        listing.expiresAt = EconomyEngine.deadline(listing.createdAt, e.config.stockLifetimeMillis);
        e.ledger.prepare(List.of(new Transfer(actor.account(), "system:fees", e.config.stockListingFeeCents,
                "Stock listing fee"))).commitWith(() -> e.governance.reserveShares(company.id(), actor.id(), listing.id, quantity));
        e.data.stocks.put(listing.id, listing);
        e.schedule("stock", listing.id);
        e.dirty.run();
        return listing.id;
    }

    GovernanceAccess.CompanyView quoteListStock(Actor actor, String companyId, long quantity, long unitPrice) {
        e.ledger.thread();
        if (quantity < 1) throw new UserError("Share quantity must be positive.");
        Money.positive(unitPrice);
        Money.multiply(unitPrice, quantity);
        GovernanceAccess.CompanyView company = e.governance.company(companyId).orElseThrow(() -> new UserError("Unknown company."));
        if (e.governance.availableShares(company.id(), actor.id()) < quantity) throw new UserError("Not enough unreserved shares.");
        if (e.data.stocks.size() >= e.config.maximumActiveListings
                || e.data.stocks.values().stream().filter(l -> l.seller.equals(actor.id().toString())).count()
                    >= e.config.maximumListingsPerPlayer) throw new UserError("The stock listing limit has been reached.");
        EconomyEngine.deadline(e.clock.millis(), e.config.stockLifetimeMillis);
        return company;
    }

    public Settlement buyStock(Actor actor, String id, long quantity) {
        e.ledger.thread();
        EconomyData.StockListing listing = e.data.stocks.get(id);
        if (listing == null) throw new UserError("Unknown stock listing.");
        if (listing.expiresAt <= e.clock.millis()) { releaseStock(listing, "expired"); throw new UserError("That listing expired."); }
        Settlement settlement = quoteBuyStock(actor, id, quantity);
        e.ledger.prepare(settlement.transfers()).commitWith(() -> e.governance.settleShares(id, actor.id(), quantity));
        listing.remaining -= quantity;
        if (listing.remaining == 0) e.data.stocks.remove(id);
        recordSettlement(actor, "player:" + listing.seller, settlement, id);
        e.mail(listing.seller, "Shares sold", quantity + " shares of " + listing.company
                + " sold for net " + Money.format(settlement.sellerNet()) + ".");
        e.dirty.run();
        return settlement;
    }

    Settlement quoteBuyStock(Actor actor, String id, long quantity) {
        e.ledger.thread();
        EconomyData.StockListing listing = e.data.stocks.get(id);
        if (listing == null || listing.expiresAt <= e.clock.millis()) throw new UserError("That stock listing is unavailable or expired.");
        if (quantity < 1 || quantity > listing.remaining) throw new UserError("Invalid share quantity.");
        if (listing.seller.equals(actor.id().toString())) throw new UserError("You cannot buy your own shares.");
        e.governance.company(listing.company).orElseThrow(() -> new UserError("The listed company no longer exists."));
        long gross = Money.multiply(listing.unitPrice, quantity);
        return settlement(actor, "player:" + listing.seller, listing.sourceChunk, listing.sourceNation,
                gross, e.config.stockCommissionBps, "Stock purchase " + id);
    }

    public void cancelStock(Actor actor, String id) {
        e.ledger.thread();
        EconomyData.StockListing listing = e.data.stocks.get(id);
        if (listing == null) throw new UserError("Unknown stock listing.");
        if (!actor.admin() && !listing.seller.equals(actor.id().toString())) throw new UserError("Only the shareholder may cancel this listing.");
        releaseStock(listing, "cancelled");
    }

    private void releaseStock(EconomyData.StockListing listing, String reason) {
        e.governance.releaseShares(listing.id);
        e.data.stocks.remove(listing.id);
        e.mail(listing.seller, "Stock listing " + reason, listing.remaining + " reserved shares were released.");
        e.dirty.run();
    }

    boolean tickStock(String id) {
        EconomyData.StockListing listing = e.data.stocks.get(id);
        if (listing == null) return false;
        if (listing.expiresAt <= e.clock.millis()) { releaseStock(listing, "expired"); return false; }
        return true;
    }

    public void companyPay(Actor actor, String companyId, String recipient, long amount, InventoryPort inventory) {
        e.ledger.prepare(quoteCompanyPay(actor, companyId, recipient, amount, inventory)).commit();
    }

    List<Transfer> quoteCompanyPay(Actor actor, String companyId, String recipient, long amount, InventoryPort inventory) {
        inventory.require(InventoryPort.Utility.VAULT);
        GovernanceAccess.CompanyView company = e.governance.company(companyId).orElseThrow(() -> new UserError("Unknown company."));
        e.requireAccount(actor, company.account());
        String target = e.resolveAccount(actor, recipient);
        e.validatePublicDestination(actor, target);
        Money.positive(amount);
        if (target.equals(company.account())) throw new UserError("Choose a different destination.");
        return List.of(new Transfer(company.account(), target, amount, "Company payment by " + actor.name()),
                new Transfer(company.account(), "system:fees", e.config.companyTransferFeeCents, "Company payment fee"));
    }

    public Dividend dividend(Actor actor, String companyId, long gross, InventoryPort inventory) {
        DividendQuote quote = quoteDividend(actor, companyId, gross, inventory);
        e.ledger.prepare(quote.transfers()).commit();
        e.taxes.record(quote.company().account(), quote.taxes(), "dividend:" + quote.company().id());
        return quote.dividend();
    }

    record DividendQuote(GovernanceAccess.CompanyView company, List<Transfer> transfers,
                         List<Taxation.Charge> taxes, Dividend dividend) {}

    DividendQuote quoteDividend(Actor actor, String companyId, long gross, InventoryPort inventory) {
        inventory.require(InventoryPort.Utility.VAULT);
        GovernanceAccess.CompanyView company = e.governance.company(companyId).orElseThrow(() -> new UserError("Unknown company."));
        e.requireAccount(actor, company.account());
        Money.positive(gross);
        BigInteger shares = company.shares().values().stream().map(value -> {
            if (value < 0) throw new UserError("The company's share register is invalid.");
            return BigInteger.valueOf(value);
        }).reduce(BigInteger.ZERO, BigInteger::add);
        if (shares.signum() == 0) throw new UserError("This company has no issued shares.");
        List<Taxation.Charge> corporate = e.taxes.quote(actor.chunkKey(), e.governance.nationOf(actor.id()).orElse(null),
                gross, "corporateTaxBps");
        long tax = Taxation.total(corporate);
        if (tax > gross) throw new UserError("Combined corporate taxes exceed the dividend budget.");
        long distributable = gross - tax, paid = 0;
        List<Transfer> transfers = new ArrayList<>(Taxation.transfers(company.account(), corporate, "Dividend corporate tax"));
        for (Map.Entry<UUID, Long> holding : company.shares().entrySet()) {
            long amount = BigInteger.valueOf(distributable).multiply(BigInteger.valueOf(holding.getValue()))
                    .divide(shares).longValueExact();
            if (amount > 0) transfers.add(new Transfer(company.account(), "player:" + holding.getKey(), amount,
                    "Dividend from " + company.id()));
            paid = Money.add(paid, amount);
        }
        if (e.balance(company.account()) < gross) throw new UserError("The company cannot fund the full dividend budget.");
        return new DividendQuote(company, List.copyOf(transfers), corporate, new Dividend(gross, paid, tax, distributable - paid));
    }

    boolean tickCompany(String id) {
        GovernanceAccess.CompanyView company = e.governance.company(id).orElse(null);
        if (company == null) {
            if (e.data.companyFinance.remove(id) != null) e.dirty.run();
            return false;
        }
        EconomyData.CompanyFinance finance = e.data.companyFinance.computeIfAbsent(id, ignored -> {
            EconomyData.CompanyFinance value = new EconomyData.CompanyFinance();
            value.company = id;
            value.nextFeeAt = EconomyEngine.deadline(e.clock.millis(), e.config.companyFeePeriodMillis);
            e.dirty.run();
            return value;
        });
        int processed = 0;
        while (finance.nextFeeAt <= e.clock.millis() && processed++ < e.config.catchUpPeriodsPerTick) {
            long next = EconomyEngine.deadline(finance.nextFeeAt, e.config.companyFeePeriodMillis);
            e.taxes.assess(company.account(), "system:fees", null, "companyFee", e.config.companyPeriodicFeeCents, id);
            finance.nextFeeAt = next;
            e.dirty.run();
        }
        return true;
    }
}
