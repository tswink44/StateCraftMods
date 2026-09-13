package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess.Transfer;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.statecraft.economy.TestWorld.*;

/** Also executable with plain Java 17 while Forge's integration build is running elsewhere. */
public final class EconomyRegressionScenarios {
    private EconomyRegressionScenarios() {}

    public static void ledgerAtomicity() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 10_000); w.set(BUYER, 1000);
        w.engine.transferBatch(List.of(new Transfer(OWNER.account(), BUYER.account(), 5000, "first"),
                new Transfer(BUYER.account(), FOREIGN.account(), 3000, "second")));
        eq(5000, w.engine.balance(OWNER.account())); eq(3000, w.engine.balance(BUYER.account())); eq(3000, w.engine.balance(FOREIGN.account()));
        int history = w.data.transactions.size();
        expect(UserError.class, () -> w.engine.transferBatch(List.of(new Transfer(OWNER.account(), BUYER.account(), 100, "valid"),
                new Transfer(FOREIGN.account(), BUYER.account(), 999_999, "invalid"))));
        eq(5000, w.engine.balance(OWNER.account())); eq(history, w.data.transactions.size());
        expect(UserError.class, () -> w.engine.ledger.prepare(List.of(new Transfer(OWNER.account(), BUYER.account(), 100, "rollback")))
                .commitWith(() -> { throw new UserError("Atomic participant refused."); }));
        eq(5000, w.engine.balance(OWNER.account())); eq(history, w.data.transactions.size());
        w.engine.transferBatch(List.of(new Transfer(OWNER.account(), OWNER.account(), 10, "self no-op")));
        eq(history, w.data.transactions.size());
    }

    public static void ledgerNegativeOverflowAndNetting() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 1000); w.set(BUYER, Money.MAX);
        expect(UserError.class, () -> w.engine.transferBatch(List.of(new Transfer(OWNER.account(), BUYER.account(), -1, "negative"))));
        expect(UserError.class, () -> w.engine.transferBatch(List.of(new Transfer(OWNER.account(), BUYER.account(), 1, "overflow"))));
        eq(1000, w.engine.balance(OWNER.account())); eq(Money.MAX, w.engine.balance(BUYER.account()));
        w.engine.transferBatch(List.of(new Transfer(OWNER.account(), BUYER.account(), 100, "netted in"),
                new Transfer(BUYER.account(), FOREIGN.account(), 100, "netted out")));
        eq(Money.MAX, w.engine.balance(BUYER.account())); eq(100, w.engine.balance(FOREIGN.account()));
        expect(UserError.class, () -> Money.multiply(Money.MAX, 2));
        expect(UserError.class, () -> Money.parse("-1"));
        expect(UserError.class, () -> Money.parse("0.001"));
        expect(UserError.class, () -> w.engine.adminAdjust(ADMIN, OWNER.account(), "take", 1000));
        eq(900, w.engine.balance(OWNER.account()));
    }

    public static void dailySpendingLimits() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 1000);
        w.engine.ledger.spendingLimit(OWNER.account(), 100);
        w.engine.pay(OWNER, "me", BUYER.account(), 80, "limited payment");
        expect(UserError.class, () -> w.engine.pay(OWNER, "me", BUYER.account(), 21, "over limit"));
        eq(920, w.engine.balance(OWNER.account()));
        w.engine.pay(BUYER, "me", OWNER.account(), 80, "return");
        expect(UserError.class, () -> w.engine.pay(OWNER, "me", BUYER.account(), 21, "returns do not reset limits"));
        w.advance(86_400_000);
        w.engine.pay(OWNER, "me", BUYER.account(), 100, "new UTC day");
        eq(900, w.engine.balance(OWNER.account()));
    }

    public static void treasuryAuthorization() {
        TestWorld w = new TestWorld();
        w.set("nation:n1", 10_000); w.set("state:s1", 10_000); w.set("company:co", 10_000);
        Inventory inventory = new Inventory();
        expect(UserError.class, () -> w.engine.withdrawCash(BUYER, "nation:n1", 100, inventory));
        expect(UserError.class, () -> w.engine.pay(BUYER, "company:co", BUYER.account(), 100, "ordinary members are not treasurers"));
        w.data.selectedAccounts.put(BUYER.id().toString(), "nation:n1");
        expect(UserError.class, () -> w.engine.selectedAccount(BUYER));
        w.governance.grant(BUYER, "nation:n1");
        w.engine.selectAccount(BUYER, "nation:n1");
        w.engine.withdrawCash(BUYER, "selected", 100, inventory);
        eq(9900, w.engine.balance("nation:n1"));
        expect(UserError.class, () -> w.engine.withdrawCash(BUYER, "state:s1", 100, inventory));
        w.governance.granted.clear();
        expect(UserError.class, () -> w.engine.withdrawCash(BUYER, "selected", 100, inventory));
        expect(UserError.class, () -> w.engine.requireAccount(OWNER, BUYER.account()));
        expect(UserError.class, () -> w.engine.requireAccount(BUYER, "system:fees"));
    }

    public static void cashPrecisionAndRoundTrip() {
        TestWorld w = new TestWorld();
        Inventory inventory = new Inventory();
        w.set(OWNER, 12_345);
        eq(12_300, w.engine.withdrawCash(OWNER, "me", 12_345, inventory));
        eq(45, w.engine.balance(OWNER.account()));
        eq(1, inventory.count(HUNDRED)); eq(2, inventory.count(TEN)); eq(3, inventory.count(ONE));
        eq(12_300, w.engine.depositCash(OWNER, "me", inventory));
        eq(12_345, w.engine.balance(OWNER.account()));
        eq(0, inventory.count(ONE) + inventory.count(TEN) + inventory.count(HUNDRED));
        expect(UserError.class, () -> w.engine.withdrawCash(OWNER, "me", 45, inventory));
        eq(12_345, w.engine.balance(OWNER.account()));
    }

    public static void cashInventoryPreflight() {
        TestWorld w = new TestWorld();
        Inventory full = new Inventory(1);
        full.fill();
        w.set(OWNER, 1000);
        expect(UserError.class, () -> w.engine.withdrawCash(OWNER, "me", 100, full));
        eq(1000, w.engine.balance(OWNER.account())); eq(0, full.commits);
        Inventory cash = new Inventory(1).item(0, ONE, 3);
        w.set(OWNER, Money.MAX);
        expect(UserError.class, () -> w.engine.depositCash(OWNER, "me", cash));
        eq(3, cash.count(ONE)); eq(Money.MAX, w.engine.balance(OWNER.account()));
        w.set(OWNER, 0);
        cash.rejectReplace = true;
        int history = w.data.transactions.size();
        expect(UserError.class, () -> w.engine.depositCash(OWNER, "me", cash));
        eq(0, w.engine.balance(OWNER.account())); eq(3, cash.count(ONE)); eq(history, w.data.transactions.size());
    }

    public static void utilityAndTaggedCurrencySafety() {
        TestWorld w = new TestWorld();
        Inventory inventory = new Inventory().item(0, new ItemLot(ONE, "{Forged:1b}", 2, 64));
        expect(UserError.class, () -> w.engine.depositCash(OWNER, "me", inventory));
        eq(2, inventory.count(ONE));
        inventory.item(0, ONE, 2);
        inventory.utilities.clear();
        expect(UserError.class, () -> w.engine.depositCash(OWNER, "me", inventory));
        inventory.item(0, "minecraft:wheat", 2);
        expect(UserError.class, () -> w.engine.commerce.sellHub(OWNER, 1, inventory));
        eq(2, inventory.count("minecraft:wheat"));
    }

    public static void hubTaxesAndLimits() {
        TestWorld w = new TestWorld(c -> { c.hubDailyLimitCents = 500; c.hubDailyItemLimit = 20; });
        w.governance.setting("n1", "salesTaxBps", "1000");
        w.governance.setting("s1", "salesTaxBps", "500");
        w.governance.setting("c1", "incomeTaxBps", "500");
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 40);
        Commerce.Sale sale = w.engine.commerce.sellHub(OWNER, 20, inventory);
        eq(500, sale.gross()); eq(400, sale.net()); eq(100, sale.taxes());
        eq(400, w.engine.balance(OWNER.account())); eq(50, w.engine.balance("nation:n1"));
        eq(25, w.engine.balance("state:s1")); eq(25, w.engine.balance("city:c1"));
        expect(UserError.class, () -> w.engine.commerce.sellHub(OWNER, 1, inventory));
        eq(20, inventory.count("minecraft:wheat"));
        w.advance(86_400_000);
        w.engine.commerce.sellHub(OWNER, 20, inventory);
        eq(800, w.engine.balance(OWNER.account()));
    }

    public static void hubCurrencyArbitrageAndInvalidPrices() {
        TestWorld w = new TestWorld();
        w.engine.reconfigure(w.config, new ItemValues(Map.of(ONE, 100_000L), w.values.currency(), id -> true));
        Inventory currency = new Inventory().item(0, ONE, 5);
        expect(UserError.class, () -> w.engine.commerce.sellHub(OWNER, 5, currency));
        eq(5, currency.count(ONE)); eq(0, w.engine.balance(OWNER.account()));
        expect(UserError.class, () -> new ItemValues(Map.of("minecraft:wheat", 0L), w.values.currency(), id -> true));
        expect(UserError.class, () -> new ItemValues(Map.of("minecraft:wheat", -1L), w.values.currency(), id -> true));
        expect(UserError.class, () -> new ItemValues(Map.of("invalid item", 1L), w.values.currency(), id -> true));
        expect(UserError.class, () -> new ItemValues(Map.of("unknown:item", 1L), w.values.currency(), id -> !id.equals("unknown:item")));
    }

    public static void marketplacePartialNbtEscrow() {
        TestWorld w = new TestWorld(c -> c.marketListingFeeCents = 25);
        w.set(OWNER, 100); w.set(BUYER, 1000);
        String tag = "{display:{Name:'{\"text\":\"Rare\"}'},Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}],CustomModelData:7}";
        Inventory seller = new Inventory().item(0, new ItemLot("minecraft:diamond", tag, 10, 64));
        String listing = w.engine.commerce.listMarket(OWNER, 8, 125, seller);
        eq(2, seller.count("minecraft:diamond")); eq(75, w.engine.balance(OWNER.account()));
        w.engine.commerce.buyMarket(BUYER, listing, 3);
        eq(5, w.data.market.get(listing).remaining); eq(625, w.engine.balance(BUYER.account()));
        eq(450, w.engine.balance(OWNER.account()));
        eq(tag, w.data.deliveries.get(BUYER.id().toString()).get(0).item.snbt());
        expect(UserError.class, () -> w.engine.commerce.buyMarket(OWNER, listing, 1));
        expect(UserError.class, () -> w.engine.commerce.buyMarket(BUYER, listing, 6));
        w.engine.commerce.cancelMarket(OWNER, listing);
        eq(0, w.data.market.size());
        Inventory buyer = new Inventory(1);
        buyer.fill();
        eq(0, w.engine.commerce.collect(BUYER, buyer));
        buyer.item(0, ItemLot.EMPTY);
        eq(3, w.engine.commerce.collect(BUYER, buyer));
        eq(tag, buyer.slots.get(0).snbt());
        eq(5, w.engine.commerce.collect(OWNER, seller));
        eq(7, seller.count("minecraft:diamond"));
        eq(0, w.data.deliveries.size());
    }

    public static void marketplaceExpirationOfflineAndRestart() {
        TestWorld w = new TestWorld(c -> c.marketListingFeeCents = 10);
        w.set(OWNER, 100);
        Inventory seller = new Inventory().item(0, new ItemLot("minecraft:diamond", "{custom:\"kept\"}", 4, 64));
        String listing = w.engine.commerce.listMarket(OWNER, 4, 10, seller);
        w.restart();
        eq(4, w.data.market.get(listing).remaining);
        w.advance(1000); w.tick();
        eq(0, w.data.market.size()); eq(90, w.engine.balance(OWNER.account()));
        eq(4, w.data.deliveries.get(OWNER.id().toString()).get(0).item.count());
        w.restart();
        eq(4, w.engine.commerce.collect(OWNER, seller));
        eq("{custom:\"kept\"}", seller.slots.get(0).snbt());
        check(!w.governance.messages.isEmpty(), "offline expiry mail missing");
    }

    public static void marketplaceFeesTariffsAndRejections() {
        TestWorld w = new TestWorld(c -> c.marketCommissionBps = 200);
        w.governance.setting("n1", "incomeTaxBps", "1000");
        w.governance.setting("n2", "salesTaxBps", "500");
        w.governance.setting("n2", "tariffBps", "1000");
        w.set(FOREIGN, 2000);
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 2);
        String listing = w.engine.commerce.listMarket(OWNER, 2, 1000, inventory);
        Commerce.Settlement sale = w.engine.commerce.buyMarket(FOREIGN, listing, 1);
        eq(1150, sale.buyerCost()); eq(880, sale.sellerNet()); eq(20, sale.fee());
        eq(850, w.engine.balance(FOREIGN.account())); eq(880, w.engine.balance(OWNER.account()));
        eq(100, w.engine.balance("nation:n1")); eq(150, w.engine.balance("nation:n2"));
        w.governance.setting("c1", "incomeTaxBps", "10000");
        expect(UserError.class, () -> w.engine.commerce.buyMarket(FOREIGN, listing, 1));
        eq(1, w.data.market.get(listing).remaining); eq(850, w.engine.balance(FOREIGN.account()));
        expect(UserError.class, () -> w.engine.commerce.listMarket(OWNER, 1, -1, inventory));
        expect(UserError.class, () -> w.engine.commerce.buyMarket(FOREIGN, listing, 0));
    }

    public static void deliveryCapacityReservesRefundSlots() {
        TestWorld w = new TestWorld(c -> { c.maximumListingsPerPlayer = 1; c.maximumDeliveryEntries = 1; });
        Inventory seller = new Inventory().item(0, "minecraft:wheat", 2);
        Inventory buyer = new Inventory().item(0, "minecraft:diamond", 1);
        w.set(OWNER, 1000);
        String first = w.engine.commerce.listMarket(OWNER, 1, 100, seller);
        String second = w.engine.commerce.listMarket(BUYER, 1, 100, buyer);
        expect(UserError.class, () -> w.engine.commerce.buyMarket(OWNER, second, 1));
        w.engine.commerce.cancelMarket(OWNER, first);
        eq(1, w.data.deliveries.get(OWNER.id().toString()).size());
        expect(UserError.class, () -> w.engine.commerce.listMarket(OWNER, 1, 100, seller));
        eq(1, w.engine.commerce.collect(OWNER, seller));
        w.engine.commerce.buyMarket(OWNER, second, 1);
    }

    public static void valuationFactorsAndPersistence() {
        TestWorld w = new TestWorld(c -> {
            c.locationBonusBps = 5000; c.demandBonusPerChunkBps = 250; c.improvementBonusBps = 500;
        }, claim -> new ValuationEnvironment.Conditions("test:rich", 12_000, 0, 4));
        w.governance.improvements(CLAIM, 2);
        w.governance.setting("n1", "propertyTaxBps", "1000");
        EconomyData.Valuation value = w.engine.property.value(CLAIM);
        eq(19_602, value.value);
        eq("test:rich", value.biome);
        w.governance.setting("c1", "baseChunkValue", "20000");
        w.advance(1000);
        eq(39_204, w.engine.valueOf(CLAIM));
        check(w.dirty > 0, "valuation changes were not marked persistent");
        w.governance.setting("c1", "baseChunkValue", "not cents");
        w.advance(1000);
        expect(UserError.class, () -> w.engine.valueOf(CLAIM));
        eq(39_204, w.data.valuations.get(CLAIM).value);
    }

    public static void propertyTaxArrearsAndMandatoryDeductions() {
        TestWorld w = new TestWorld();
        w.governance.setting("n1", "propertyTaxBps", "1000");
        w.set(OWNER, 100);
        eq(9000, w.engine.valueOf(CLAIM));
        w.advance(1000); w.tick();
        eq(BigInteger.valueOf(900), w.engine.taxes.arrears(OWNER.account()));
        eq(100, w.engine.balance(OWNER.account()));
        w.config.mandatoryPropertyTaxes = true;
        w.engine.ledger.spendingLimit(OWNER.account(), 0);
        w.tick();
        eq(0, w.engine.balance(OWNER.account())); eq(BigInteger.valueOf(800), w.engine.taxes.arrears(OWNER.account()));
        w.set(OWNER, 800); w.tick();
        eq(BigInteger.ZERO, w.engine.taxes.arrears(OWNER.account())); eq(900, w.engine.balance("nation:n1"));
        check(w.data.taxHistory.stream().anyMatch(t -> t.status().equals("ASSESSED")), "assessment report missing");
        check(w.data.taxHistory.stream().anyMatch(t -> t.status().equals("ARREARS PAID")), "payment report missing");
    }

    public static void boundedTaxCatchUpDoesNotEraseDebt() {
        TestWorld w = new TestWorld();
        w.governance.setting("n1", "propertyTaxBps", "1000");
        w.engine.valueOf(CLAIM);
        w.advance(10_000); w.tick();
        eq(BigInteger.valueOf(3600), w.engine.taxes.arrears(OWNER.account()));
        w.tick(); w.tick();
        eq(BigInteger.valueOf(9000), w.engine.taxes.arrears(OWNER.account()));
        w.engine.taxes.assess(OWNER.account(), "nation:n1", "n1", "propertyTaxBps", Money.MAX, CLAIM);
        w.engine.taxes.assess(OWNER.account(), "nation:n1", "n1", "propertyTaxBps", Money.MAX, CLAIM);
        eq(BigInteger.valueOf(Money.MAX).multiply(BigInteger.TWO).add(BigInteger.valueOf(9000)), w.engine.taxes.arrears(OWNER.account()));
    }

    public static void propertyOwnershipAndGovernmentProceeds() {
        TestWorld w = new TestWorld();
        w.governance.claimOwner(CLAIM, "city:c1");
        w.set(BUYER, 2500);
        w.engine.property.list(OWNER, CLAIM, 2000);
        check(w.engine.isClaimEncumbered(CLAIM), "listed property is not locked");
        expect(UserError.class, () -> w.governance.transferProperty(CLAIM, OWNER.account()));
        w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE);
        eq(2000, w.engine.balance("city:c1")); eq(0, w.engine.balance(OWNER.account()));
        eq(BUYER.account(), w.governance.claims.get(CLAIM).ownerAccount());
        eq("n1", w.governance.claims.get(CLAIM).nationId()); eq("c1", w.governance.claims.get(CLAIM).cityId());
        check(!w.engine.isClaimEncumbered(CLAIM), "completed property listing is still locked");
    }

    public static void propertyCashAndExternalFailureRollback() {
        TestWorld w = new TestWorld();
        Inventory cash = new Inventory().item(0, HUNDRED, 1);
        w.engine.property.list(OWNER, CLAIM, 12_000);
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, cash));
        eq(1, cash.count(HUNDRED)); eq(0, w.engine.balance(BUYER.account()));
        w.set(BUYER, 3000);
        w.governance.rejectProperty = true;
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, cash));
        eq(3000, w.engine.balance(BUYER.account())); eq(0, w.engine.balance(OWNER.account()));
        eq(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount()); eq(1, cash.count(HUNDRED));
        w.governance.rejectProperty = false;
        cash.rejectReplace = true;
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, cash));
        eq(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount()); eq(3000, w.engine.balance(BUYER.account()));
        cash.rejectReplace = false;
        w.engine.property.buy(BUYER, CLAIM, true, cash);
        eq(1000, w.engine.balance(BUYER.account())); eq(12_000, w.engine.balance(OWNER.account())); eq(0, cash.count(HUNDRED));
    }

    public static void propertyEligibilityExpiryAndArrearsGuard() {
        TestWorld w = new TestWorld();
        w.engine.property.list(OWNER, CLAIM, 1000);
        w.set(BUYER, 1000);
        w.governance.ineligibleBuyers.add(BUYER.id());
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE));
        eq(1000, w.engine.balance(BUYER.account()));
        expect(UserError.class, () -> w.engine.property.delist(FOREIGN, CLAIM));
        w.advance(1000); w.tick();
        check(!w.engine.isClaimEncumbered(CLAIM), "expired property listing remains locked");
        w.engine.taxes.assess("company:co", "nation:n1", "n1", "corporateTaxBps", 100, "unpaid company tax");
        check(w.engine.isAccountInUse("company:co"), "arrears must prevent account disposal");
    }

    public static void dividendsRoundingAndCorporateTax() {
        TestWorld w = new TestWorld();
        w.governance.companies.get("co").shares.clear();
        for (Actor actor : List.of(OWNER, BUYER, FOREIGN)) w.governance.companies.get("co").shares.put(actor.id(), 1L);
        w.set("company:co", 2000);
        Commerce.Dividend dividend = w.engine.commerce.dividend(OWNER, "co", 1001, new Inventory());
        eq(999, dividend.paidToShareholders()); eq(2, dividend.retainedRemainder()); eq(1001, w.engine.balance("company:co"));
        eq(333, w.engine.balance(OWNER.account())); eq(333, w.engine.balance(BUYER.account())); eq(333, w.engine.balance(FOREIGN.account()));
        expect(UserError.class, () -> w.engine.commerce.dividend(BUYER, "co", 100, new Inventory()));
        w.governance.setting("n1", "corporateTaxBps", "1000");
        dividend = w.engine.commerce.dividend(OWNER, "co", 1000, new Inventory());
        eq(100, dividend.corporateTax()); eq(900, dividend.paidToShareholders()); eq(1, w.engine.balance("company:co"));
        eq(100, w.engine.balance("nation:n1"));
    }

    public static void companyFeesArePersistentObligations() {
        TestWorld w = new TestWorld(c -> c.companyPeriodicFeeCents = 100);
        w.tick(); w.advance(1000); w.tick();
        eq(BigInteger.valueOf(100), w.engine.taxes.arrears("company:co"));
        check(w.engine.isAccountInUse("company:co"), "company with unpaid fees can be deleted");
        w.set("company:co", 100);
        w.engine.execute(OWNER, "tax pay all company:co", InventoryPort.NONE);
        eq(100, w.engine.balance("system:fees")); eq(BigInteger.ZERO, w.engine.taxes.arrears("company:co"));
    }

    public static void stockReservationPartialAndRestart() {
        TestWorld w = new TestWorld();
        w.set(BUYER, 1000);
        String id = w.engine.commerce.listStock(OWNER, "co", 30, 10);
        eq(40, w.governance.availableShares("co", OWNER.id()));
        expect(UserError.class, () -> w.engine.commerce.listStock(OWNER, "co", 50, 10));
        expect(UserError.class, () -> w.governance.transferShares("co", OWNER.id(), FOREIGN.id(), 41));
        w.restart();
        eq(1, w.governance.reservations.size());
        w.engine.commerce.buyStock(BUYER, id, 20);
        eq(50, w.governance.sharesOf("co", BUYER.id())); eq(50, w.governance.sharesOf("co", OWNER.id()));
        eq(10, w.data.stocks.get(id).remaining); eq(200, w.engine.balance(OWNER.account()));
        w.engine.commerce.cancelStock(OWNER, id);
        eq(50, w.governance.availableShares("co", OWNER.id())); eq(0, w.governance.reservations.size());
    }

    public static void stockFailureSelfBuyAndExpiry() {
        TestWorld w = new TestWorld();
        w.set(BUYER, 1000);
        String id = w.engine.commerce.listStock(OWNER, "co", 30, 10);
        expect(UserError.class, () -> w.engine.commerce.buyStock(OWNER, id, 1));
        w.governance.rejectShares = true;
        expect(UserError.class, () -> w.engine.commerce.buyStock(BUYER, id, 1));
        eq(1000, w.engine.balance(BUYER.account())); eq(0, w.engine.balance(OWNER.account())); eq(30, w.data.stocks.get(id).remaining);
        w.governance.rejectShares = false;
        w.advance(1000); w.tick();
        eq(0, w.data.stocks.size()); eq(70, w.governance.availableShares("co", OWNER.id()));
    }

    public static void fundedDepositorInterestDoesNotMintOrCompound() {
        TestWorld w = new TestWorld(c -> c.minimumBankReserveCents = 1000);
        String bank = w.bank(2000, 100, 0);
        w.set(BUYER, 10_000);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        long total = totalLedger(w);
        w.advance(1000); w.tick();
        eq(10_100, w.engine.banking.balance(BUYER, bank, null).balance()); eq(12_000, w.engine.balance("bank:co"));
        eq(total, totalLedger(w));
        w.advance(1000); w.tick();
        eq(10_200, w.engine.banking.balance(BUYER, bank, null).balance());
        w.engine.banking.withdraw(BUYER, bank, 10_200);
        w.engine.banking.close(OWNER, bank);
        eq(1800, w.engine.balance("company:co")); eq(10_200, w.engine.balance(BUYER.account())); eq(total, totalLedger(w));
    }

    public static void depositTimeWeightingAndRateContracts() {
        TestWorld w = new TestWorld();
        String bank = w.bank(2000, 100, 100);
        w.set(BUYER, 10_000);
        w.engine.banking.deposit(BUYER, bank, 5000);
        w.advance(500);
        w.engine.banking.rates(OWNER, bank, 200, 200, 100, 0, 0);
        w.engine.banking.deposit(BUYER, bank, 5000);
        w.advance(500); w.tick();
        eq(10_075, w.engine.banking.balance(BUYER, bank, null).balance());
        eq(100, w.data.banks.get(bank).deposits.get(BUYER.id().toString()).rateBps);
        w.engine.banking.withdraw(BUYER, bank, 10_075);
        w.engine.banking.deposit(BUYER, bank, 1000);
        eq(200, w.data.banks.get(bank).deposits.get(BUYER.id().toString()).rateBps);
    }

    public static void bankReservesAndProtectedLiabilities() {
        TestWorld w = new TestWorld();
        String bank = w.bank(2000, 0, 0);
        w.set(BUYER, 10_000);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        expect(UserError.class, () -> w.engine.pay(OWNER, "bank:co", OWNER.account(), 2001, "cannot siphon deposits"));
        expect(UserError.class, () -> w.engine.withdrawCash(OWNER, "bank:co", 2100, new Inventory()));
        expect(UserError.class, () -> w.engine.banking.close(OWNER, bank));
        expect(UserError.class, () -> w.engine.banking.balance(FOREIGN, bank, BUYER.id().toString()));
        check(w.engine.isAccountInUse("company:co"), "bank company can close with liabilities");
        String loan = w.loan(OWNER, bank, 10_000, null, false, false, false);
        eq(2000, w.engine.balance("bank:co")); eq(2000, w.engine.banking.requiredReserve(w.data.banks.get(bank)));
        w.engine.banking.associate(FOREIGN, bank);
        String impossible = w.engine.banking.requestLoan(FOREIGN, bank, 1, 4, null, false, false, false);
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, impossible));
        eq("REQUESTED", w.data.loans.get(impossible).status);
        expect(UserError.class, () -> w.engine.banking.withdraw(BUYER, bank, 5000));
        eq(10_000, w.engine.banking.balance(BUYER, bank, null).balance());
        eq("ACTIVE", w.data.loans.get(loan).status);
    }

    public static void unfundedInterestIsRetainedAndLaterFunded() {
        TestWorld w = new TestWorld(c -> c.minimumBankReserveCents = 1000);
        String bank = w.bank(2000, 100, 100);
        w.set(BUYER, 10_000); w.set(OWNER, 100);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        String loan = w.loan(OWNER, bank, 10_000, null, false, false, false);
        w.advance(1000); w.tick();
        Banking.DepositSummary deposit = w.engine.banking.balance(BUYER, bank, null);
        eq(10_000, deposit.balance()); eq(BigInteger.valueOf(100), deposit.unpaidInterest());
        w.engine.banking.repayAll(OWNER, loan);
        w.tick();
        deposit = w.engine.banking.balance(BUYER, bank, null);
        eq(10_100, deposit.balance()); eq(BigInteger.ZERO, deposit.unpaidInterest());
        eq("REPAID", w.data.loans.get(loan).status); eq(12_100, w.engine.balance("bank:co"));
    }

    public static void bankFeesAndUnauthorizedWithdrawals() {
        TestWorld w = new TestWorld();
        String bank = w.bank(1000, 0, 0);
        w.engine.banking.rates(OWNER, bank, 0, 0, 100, 10, 5);
        w.set(BUYER, 100);
        eq(90, w.engine.banking.deposit(BUYER, bank, 100));
        w.engine.banking.withdraw(BUYER, bank, 20);
        eq(65, w.engine.banking.balance(BUYER, bank, null).balance());
        eq(1080, w.engine.balance("bank:co")); eq(20, w.engine.balance(BUYER.account()));
        expect(UserError.class, () -> w.engine.banking.withdraw(FOREIGN, bank, 1));
        expect(UserError.class, () -> w.engine.banking.capital(BUYER, bank, true, 1));
        expect(UserError.class, () -> w.engine.banking.rates(BUYER, bank, 0, 0, 0, 0, 0));
    }

    public static void securedEligibilityAndDoublePledgeProtection() {
        TestWorld w = new TestWorld(c -> c.allowUnsecuredLoans = false);
        String bank = w.bank(20_000, 0, 0);
        w.engine.banking.associate(BUYER, bank);
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 1, 4, null, false, false, false));
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 7001, 4, BUYER_CLAIM, false, true, false));
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 5000, 4, BUYER_CLAIM, false, false, false));
        String request = w.engine.banking.requestLoan(BUYER, bank, 7000, 4, BUYER_CLAIM, false, true, false);
        check(w.engine.isClaimEncumbered(BUYER_CLAIM), "collateral was not locked at application");
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 1, 4, BUYER_CLAIM, false, true, false));
        expect(UserError.class, () -> w.engine.property.list(BUYER, BUYER_CLAIM, 10_000));
        expect(UserError.class, () -> w.governance.transferProperty(BUYER_CLAIM, FOREIGN.account()));
        w.engine.banking.reject(BUYER, request);
        check(!w.engine.isClaimEncumbered(BUYER_CLAIM), "cancelled application's collateral was not released");
        String expired = w.engine.banking.requestLoan(BUYER, bank, 1000, 4, BUYER_CLAIM, false, true, false);
        w.advance(1000); w.tick();
        eq("EXPIRED", w.data.loans.get(expired).status);
        check(!w.engine.isClaimEncumbered(BUYER_CLAIM), "expired application's collateral was not released");
    }

    public static void repaymentScheduleAndAutoPay() {
        TestWorld w = new TestWorld();
        String bank = w.bank(10_000, 0, 100);
        w.set(BUYER, 100);
        String loan = w.loan(BUYER, bank, 4000, null, false, false, true);
        w.advance(1000); w.tick();
        EconomyData.Loan contract = w.data.loans.get(loan);
        eq(3000, contract.principal); eq(0, contract.interest); eq(3060, w.engine.balance(BUYER.account()));
        eq(0, contract.missedPayments);
        expect(UserError.class, () -> w.engine.banking.autoPay(OWNER, loan, false));
        w.engine.banking.autoPay(BUYER, loan, false);
        w.advance(1000);
        eq(30, w.engine.banking.loanSummary(BUYER, loan).interest());
        w.engine.banking.repay(BUYER, loan, 500);
        eq(2530, contract.principal); eq(0, contract.interest);
        expect(UserError.class, () -> w.engine.banking.repay(BUYER, loan, 9999));
        w.engine.banking.repayAll(BUYER, loan);
        eq("REPAID", contract.status);
    }

    public static void loanInterestCapAndOriginalTerms() {
        TestWorld w = new TestWorld(c -> c.loanLifetimeInterestCapBps = 500);
        String bank = w.bank(50_000, 0, 1000);
        w.engine.banking.associate(BUYER, bank);
        String loan = w.engine.banking.requestLoan(BUYER, bank, 10_000, 4, null, false, false, false);
        w.engine.banking.rates(OWNER, bank, 0, 0, 1000, 0, 0);
        w.engine.banking.approve(OWNER, loan);
        eq(1000, w.data.loans.get(loan).rateBps); eq(0, w.data.loans.get(loan).originationFee);
        w.advance(1_000_000_000_000L);
        Banking.LoanSummary summary = w.engine.banking.loanSummary(BUYER, loan);
        eq(500, summary.interest()); eq(10_500, summary.total());
        w.advance(1_000_000_000_000L);
        eq(500, w.engine.banking.loanSummary(BUYER, loan).interest());
    }

    public static void defaultWithoutConsentCannotSeizeAnything() {
        TestWorld w = new TestWorld();
        String bank = w.bank(5000, 0, 0);
        w.set(BUYER, 200);
        String loan = w.loan(BUYER, bank, 400, null, false, false, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 400, "spend loan");
        w.advance(2000); w.tick();
        eq("DEFAULTED", w.data.loans.get(loan).status); eq(400, w.data.loans.get(loan).principal);
        eq(200, w.engine.balance(BUYER.account())); eq(BUYER.account(), w.governance.claims.get(BUYER_CLAIM).ownerAccount());
        w.engine.banking.recover(OWNER, loan);
        eq(200, w.engine.balance(BUYER.account()));
        check(!w.engine.isClaimEncumbered(BUYER_CLAIM), "an unpledged property became collateral");
    }

    public static void defaultConsentedSeizureIsDebtLimited() {
        TestWorld w = new TestWorld();
        String bank = w.bank(5000, 0, 0);
        w.set(BUYER, 500);
        String loan = w.loan(BUYER, bank, 400, null, true, false, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 400, "spend disbursement");
        w.engine.ledger.spendingLimit(BUYER.account(), 0);
        w.advance(2000); w.tick();
        eq("REPAID", w.data.loans.get(loan).status); eq(100, w.engine.balance(BUYER.account()));
        eq(5000, w.engine.balance("bank:co"));
        eq(BUYER.account(), w.governance.claims.get(BUYER_CLAIM).ownerAccount());
    }

    public static void repossessionPaysSurplusAndPreservesPolitics() {
        TestWorld w = new TestWorld();
        String bank = w.bank(20_000, 0, 0);
        String loan = w.loan(BUYER, bank, 5000, BUYER_CLAIM, false, true, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 5000, "spend disbursement");
        w.advance(2000); w.tick();
        EconomyData.Loan contract = w.data.loans.get(loan);
        eq("REPAID", contract.status); check(contract.repossessed, "specific collateral was not repossessed");
        eq(5000, w.engine.balance(BUYER.account())); eq(10_000, w.engine.balance("bank:co"));
        eq("company:co", w.governance.claims.get(BUYER_CLAIM).ownerAccount());
        eq("n1", w.governance.claims.get(BUYER_CLAIM).nationId()); eq("c1", w.governance.claims.get(BUYER_CLAIM).cityId());
        eq(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount());
    }

    public static void unfundedRepossessionIsDeferredNotPartial() {
        TestWorld w = new TestWorld();
        String bank = w.bank(6000, 0, 0);
        String loan = w.loan(BUYER, bank, 5000, BUYER_CLAIM, false, true, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 5000, "spend disbursement");
        w.advance(2000); w.tick();
        eq("DEFAULTED", w.data.loans.get(loan).status); eq(5000, w.data.loans.get(loan).principal);
        eq(BUYER.account(), w.governance.claims.get(BUYER_CLAIM).ownerAccount()); eq(1000, w.engine.balance("bank:co"));
        check(w.engine.isClaimEncumbered(BUYER_CLAIM), "deferred recovery lost collateral lock");
        w.engine.adminAdjust(ADMIN, "bank:co", "mint", 5000);
        w.tick();
        eq("REPAID", w.data.loans.get(loan).status); eq("company:co", w.governance.claims.get(BUYER_CLAIM).ownerAccount());
    }

    public static void repossessionShortfallDoesNotSeizeOtherAssets() {
        TestWorld w = new TestWorld();
        String bank = w.bank(20_000, 0, 0);
        String loan = w.loan(BUYER, bank, 7000, BUYER_CLAIM, false, true, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 7000, "spend");
        w.governance.setting("c1", "baseChunkValue", "5000");
        w.advance(2000); w.tick();
        eq(2000, w.data.loans.get(loan).principal); eq("DEFAULTED", w.data.loans.get(loan).status);
        eq("company:co", w.governance.claims.get(BUYER_CLAIM).ownerAccount());
        check(w.data.loans.get(loan).collateralReleased, "repossessed collateral stayed pledged");
        w.set(BUYER, 1000); w.tick();
        eq(1000, w.engine.balance(BUYER.account())); eq(2000, w.data.loans.get(loan).principal);
    }

    public static void commandValidationAndHistoryBounds() {
        TestWorld w = new TestWorld(c -> c.historyLimit = 10);
        w.set(OWNER, 1000);
        expect(UserError.class, () -> w.engine.execute(OWNER, "admin mint me 100.00", InventoryPort.NONE));
        expect(UserError.class, () -> w.engine.execute(OWNER, "pay Buyer -1", InventoryPort.NONE));
        expect(UserError.class, () -> w.engine.execute(OWNER, "pay Buyer 1.001", InventoryPort.NONE));
        expect(UserError.class, () -> w.engine.execute(OWNER, "x".repeat(4097), InventoryPort.NONE));
        expect(UserError.class, () -> w.engine.execute(OWNER, "pay \"unterminated", InventoryPort.NONE));
        for (int i = 0; i < 20; i++) w.engine.execute(OWNER, "pay Buyer 0.01", InventoryPort.NONE);
        eq(10, w.data.transactions.size()); eq(20, w.engine.balance(BUYER.account()));
        check(w.engine.execute(OWNER, "balance me", InventoryPort.NONE).contains("$9.80"), "command amount interpretation is not cents");
        expect(UserError.class, () -> w.engine.history(BUYER, OWNER.account()));
    }

    public static void propertyBankCashFundingIsOneTransaction() {
        TestWorld w = new TestWorld();
        String bank = w.bank(1000, 0, 0);
        w.set(BUYER, 9000);
        w.engine.banking.deposit(BUYER, bank, 8000);
        Inventory inventory = new Inventory().item(0, TEN, 3);
        w.engine.property.list(OWNER, CLAIM, 10_000);
        w.governance.rejectProperty = true;
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, bank, inventory));
        eq(1000, w.engine.balance(BUYER.account())); eq(8000, w.engine.banking.balance(BUYER, bank, null).balance());
        eq(9000, w.engine.balance("bank:co")); eq(3, inventory.count(TEN));
        w.governance.rejectProperty = false;
        w.engine.execute(BUYER, "property buy " + CLAIM + " bank co cash", inventory);
        eq(0, w.engine.balance(BUYER.account())); eq(0, inventory.count(TEN));
        eq(2000, w.engine.banking.balance(BUYER, bank, null).balance()); eq(3000, w.engine.balance("bank:co"));
        eq(10_000, w.engine.balance(OWNER.account())); eq(BUYER.account(), w.governance.claims.get(CLAIM).ownerAccount());
    }

    public static void propertyBankFundingPreservesInventoryOnReserveFailure() {
        TestWorld w = new TestWorld();
        String bank = w.bank(2000, 0, 0);
        w.set(BUYER, 10_000);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        w.loan(FOREIGN, bank, 10_000, null, false, false, false);
        Inventory inventory = new Inventory().item(0, TEN, 2);
        w.engine.property.list(OWNER, CLAIM, 9000);
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, bank, inventory));
        eq(2, inventory.count(TEN)); eq(0, w.engine.balance(BUYER.account()));
        eq(10_000, w.engine.banking.balance(BUYER, bank, null).balance());
        eq(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount());
    }

    public static void propertySaleKeepsOverdueTaxWithSeller() {
        TestWorld w = new TestWorld(c -> c.propertyListingLifetimeMillis = 60_000);
        w.governance.setting("n1", "propertyTaxBps", "1000");
        w.engine.valueOf(CLAIM);
        w.engine.property.list(OWNER, CLAIM, 1000);
        w.set(BUYER, 1000);
        w.advance(10_000);
        w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE);
        eq(BigInteger.valueOf(9000), w.engine.taxes.arrears(OWNER.account()));
        eq(BigInteger.ZERO, w.engine.taxes.arrears(BUYER.account()));
        eq(BUYER.account(), w.data.valuations.get(CLAIM).taxOwnerAccount);
        w.tick();
        eq(BigInteger.valueOf(9000), w.engine.taxes.arrears(OWNER.account()));
        check(w.engine.isAccountInUse(OWNER.account()), "selling property erased the original owner's obligations");
    }

    public static void everyListingPageIsReachable() {
        TestWorld w = new TestWorld();
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 30);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) ids.add(w.engine.commerce.listMarket(OWNER, 1, 10, inventory));
        String secondPage = w.engine.execute(OWNER, "page 2 market own", InventoryPort.NONE);
        check(secondPage.contains(ids.get(12)), "second listing page is inaccessible");
        check(!secondPage.contains(ids.get(0)), "page 2 repeated page 1");
        String thirdPage = w.engine.execute(OWNER, "page 3 market own", InventoryPort.NONE);
        check(thirdPage.contains(ids.get(24)), "last listing page is inaccessible");
        expect(UserError.class, () -> w.engine.execute(OWNER, "page 4 market own", InventoryPort.NONE));
        check(w.engine.execute(OWNER, "market inspect " + ids.get(24), InventoryPort.NONE).contains("minecraft:wheat"), "listing inspection missing");
        check(w.engine.execute(OWNER, "market own", InventoryPort.NONE).contains(ids.get(0)), "pagination leaked between requests");
    }

    public static void closedLoanHistoryIsBoundedWithoutDroppingLiveDebt() {
        TestWorld w = new TestWorld(c -> c.historyLimit = 10);
        String bank = w.bank(10_000, 0, 0);
        w.engine.banking.associate(BUYER, bank);
        String live = w.engine.banking.requestLoan(BUYER, bank, 1000, 4, BUYER_CLAIM, false, true, false);
        w.engine.banking.approve(OWNER, live);
        for (int i = 0; i < 25; i++) {
            String closed = w.engine.banking.requestLoan(BUYER, bank, 1, 4, null, false, false, false);
            w.engine.banking.reject(BUYER, closed);
        }
        eq(11, w.data.loans.size());
        eq("ACTIVE", w.data.loans.get(live).status);
        check(w.engine.isClaimEncumbered(BUYER_CLAIM), "history trimming erased collateral");
        expect(UserError.class, () -> w.engine.banking.close(OWNER, bank));
    }

    public static void clockRollbackCannotResetDailyAllowances() {
        TestWorld w = new TestWorld(c -> c.hubDailyLimitCents = 100);
        w.advance(86_400_000);
        w.set(OWNER, 1000);
        w.engine.ledger.spendingLimit(OWNER.account(), 100);
        w.engine.pay(OWNER, "me", BUYER.account(), 100, "use allowance");
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 8);
        w.engine.commerce.sellHub(OWNER, 4, inventory);
        w.clock.now -= 86_400_000;
        expect(UserError.class, () -> w.engine.pay(OWNER, "me", BUYER.account(), 1, "clock moved backwards"));
        expect(UserError.class, () -> w.engine.commerce.sellHub(OWNER, 1, inventory));
        eq(4, inventory.count("minecraft:wheat"));
    }

    public static void reentrantPaymentsCannotCorruptOuterTransactions() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 1000);
        int history = w.data.transactions.size();
        Ledger.Plan payment = w.engine.ledger.prepare(List.of(new Transfer(OWNER.account(), BUYER.account(), 100, "outer")));
        expect(UserError.class, () -> payment.commitWith(() ->
                w.engine.transferBatch(List.of(new Transfer(OWNER.account(), FOREIGN.account(), 10, "reentrant")))));
        eq(1000, w.engine.balance(OWNER.account())); eq(0, w.engine.balance(BUYER.account())); eq(0, w.engine.balance(FOREIGN.account()));
        eq(history, w.data.transactions.size());
        w.engine.pay(OWNER, "me", BUYER.account(), 100, "after rollback");
        eq(900, w.engine.balance(OWNER.account())); eq(100, w.engine.balance(BUYER.account()));
    }

    public static void queuedItemEscrowKeepsEmptyAccountsInUse() {
        TestWorld w = new TestWorld();
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 2);
        String listing = w.engine.commerce.listMarket(OWNER, 1, 100, inventory);
        eq(0, w.engine.balance(OWNER.account()));
        check(w.engine.isAccountInUse(OWNER.account()), "an active item listing did not protect an empty account");
        w.engine.commerce.cancelMarket(OWNER, listing);
        check(w.engine.isAccountInUse(OWNER.account()), "queued refund escrow did not protect an empty account");
        eq(1, w.engine.commerce.collect(OWNER, inventory));
        check(!w.engine.isAccountInUse(OWNER.account()), "collected item escrow left a stale account lock");
        w.set(BUYER, 100);
        listing = w.engine.commerce.listMarket(OWNER, 1, 100, inventory);
        w.engine.commerce.buyMarket(BUYER, listing, 1);
        eq(0, w.engine.balance(BUYER.account()));
        check(w.engine.isAccountInUse(BUYER.account()), "undelivered purchased items did not protect an empty buyer account");
    }

    public static void zeroBalanceObligationsStillProtectIntegrationAccounts() {
        TestWorld w = new TestWorld(c -> c.minimumBankReserveBps = 0);
        String bank = w.bank(0, 0, 0);
        w.set(BUYER, 100);
        w.engine.banking.deposit(BUYER, bank, 100);
        w.loan(FOREIGN, bank, 100, null, false, false, false);
        w.engine.pay(FOREIGN, "me", OWNER.account(), 100, "spend disbursement");
        eq(0, w.engine.balance("bank:co")); eq(0, w.engine.balance("company:co"));
        eq(0, w.engine.balance(BUYER.account())); eq(0, w.engine.balance(FOREIGN.account()));
        check(w.engine.isAccountInUse("bank:co"), "zero-cash bank with liabilities was considered unused");
        check(w.engine.isAccountInUse("company:co"), "empty bank-company treasury was considered unused");
        check(w.engine.isAccountInUse(BUYER.account()), "bank depositor's empty wallet was considered unused");
        check(w.engine.isAccountInUse(FOREIGN.account()), "borrower's empty wallet was considered unused");
        w.engine.taxes.assess(FOREIGN.account(), "nation:n1", "n1", "incomeTaxBps", 50, "unpaid obligation");
        eq(0, w.engine.balance("nation:n1"));
        check(w.engine.isAccountInUse("nation:n1"), "an unfunded tax receivable did not protect its government treasury");
    }

    public static void coreContractEscrowIsSupportedButNotPlayerAccessible() {
        TestWorld w = new TestWorld();
        String escrow = "escrow:contract:" + new java.util.UUID(0, 222);
        String fees = "system:statecraft-fees";
        w.set(OWNER, 1000);
        w.set("company:co", 10);
        w.engine.transferBatch(List.of(new Transfer(OWNER.account(), escrow, 600, "Fund core contract"),
                new Transfer(OWNER.account(), fees, 50, "Core fee")));
        eq(350, w.engine.balance(OWNER.account())); eq(600, w.engine.balance(escrow)); eq(50, w.engine.balance(fees));
        check(w.engine.isAccountInUse(escrow), "funded contract escrow was considered unused");
        expect(UserError.class, () -> w.engine.requireAccount(OWNER, escrow));
        expect(UserError.class, () -> w.engine.selectAccount(OWNER, escrow));
        expect(UserError.class, () -> w.engine.pay(OWNER, escrow, OWNER.account(), 1, "unauthorized withdrawal"));
        expect(UserError.class, () -> w.engine.pay(OWNER, "me", escrow, 1, "unsolicited escrow deposit"));
        expect(UserError.class, () -> w.engine.pay(OWNER, "me", fees, 1, "unsolicited internal fee deposit"));
        expect(UserError.class, () -> w.engine.commerce.companyPay(OWNER, "co", escrow, 1, new Inventory()));
        w.engine.transferBatch(List.of(new Transfer(escrow, "company:co", 600, "Settle core contract")));
        eq(0, w.engine.balance(escrow)); eq(610, w.engine.balance("company:co"));
        expect(UserError.class, () -> Ledger.account("escrow:contract:invalid identifier"));
        expect(UserError.class, () -> Ledger.account("escrow::contract"));
        expect(UserError.class, () -> Ledger.account("company:co:extra"));
    }

    public static void propertyFailureNeverRequiresReacquiringTheFormerTitle() {
        TestWorld w = new TestWorld();
        w.set(BUYER, 3000);
        w.governance.rejectReturningProperty = true;
        Inventory inventory = new Inventory().item(0, HUNDRED, 1);
        w.engine.property.list(OWNER, CLAIM, 12_000);
        inventory.rejectReplace = true;
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, inventory));
        eq(0, w.governance.propertyTransfers);
        eq(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount());
        inventory.rejectReplace = false;
        w.governance.rejectProperty = true;
        int history = w.data.transactions.size();
        expect(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, inventory));
        eq(1, inventory.count(HUNDRED)); eq(3000, w.engine.balance(BUYER.account()));
        eq(0, w.engine.balance(OWNER.account())); eq(history, w.data.transactions.size());
        eq(0, w.governance.propertyTransfers);
        check(w.engine.isClaimEncumbered(CLAIM), "refused title settlement lost the listing lock");
        w.governance.rejectProperty = false;
        w.engine.property.buy(BUYER, CLAIM, true, inventory);
        eq(1, w.governance.propertyTransfers); eq(0, inventory.count(HUNDRED));
        eq(BUYER.account(), w.governance.claims.get(CLAIM).ownerAccount());
    }

    public static void inventoryRollbackUsesTheCommittedReceipt() {
        Inventory inventory = new Inventory().item(0, ONE, 2);
        InventoryPort.Plan plan = inventory.plan();
        plan.remove(inventory.held(), 1);
        plan.commit();
        eq(1, inventory.count(ONE));
        plan.rollback();
        eq(2, inventory.count(ONE));
        expect(IllegalStateException.class, plan::commit);
        expect(IllegalStateException.class, plan::rollback);
    }

    public static void completeStackMetadataSurvivesPartialEscrowAndRefunds() {
        TestWorld w = new TestWorld();
        String metadata = "{tag:{display:{Name:'{\"text\":\"Charged\"}'}},ForgeCaps:{\"example:charge\":{energy:1234}}}";
        Inventory inventory = new Inventory().item(0, new ItemLot("minecraft:diamond", metadata, 4, 64, true));
        w.set(BUYER, 100);
        String listing = w.engine.commerce.listMarket(OWNER, 4, 25, inventory);
        w.engine.commerce.buyMarket(BUYER, listing, 2);
        w.engine.commerce.cancelMarket(OWNER, listing);
        ItemLot purchased = w.data.deliveries.get(BUYER.id().toString()).get(0).item;
        ItemLot returned = w.data.deliveries.get(OWNER.id().toString()).get(0).item;
        check(purchased.fullStackData() && returned.fullStackData(), "serialized capability metadata lost its explicit format marker");
        eq(metadata, purchased.snbt()); eq(metadata, returned.snbt());
        eq(2, purchased.count()); eq(2, returned.count());
        eq(2, w.engine.commerce.collect(OWNER, inventory));
        check(inventory.held().fullStackData(), "collection lost full stack metadata");
        eq(metadata, inventory.held().snbt());
    }

    public static void elapsedDepositInterestProtectsOrdinaryBankOutflowsWithoutMutation() {
        TestWorld w = new TestWorld();
        String bank = w.bank(1000, 100, 0);
        w.set(BUYER, 10_000);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        w.engine.taxes.assess("bank:co", "nation:n1", "n1", "propertyTaxBps", 100, CLAIM);
        EconomyData.Deposit deposit = w.data.banks.get(bank).deposits.get(BUYER.id().toString());
        long lastAccrued = deposit.lastAccruedAt;
        int history = w.data.transactions.size();
        w.advance(10_000);
        w.dirty = 0;
        eq(BigInteger.valueOf(11_000), w.engine.banking.liabilities(w.data.banks.get(bank)));
        eq(0, w.engine.spendable("bank:co"));
        expect(UserError.class, () -> w.engine.pay(OWNER, "bank:co", OWNER.account(), 1000, "elapsed depositor interest"));
        expect(UserError.class, () -> w.engine.banking.capital(OWNER, bank, true, 1));
        expect(UserError.class, () -> w.engine.withdrawCash(OWNER, "bank:co", 100, new Inventory()));
        expect(UserError.class, () -> w.engine.adminAdjust(ADMIN, "bank:co", "take", 1));
        expect(UserError.class, () -> w.engine.transferBatch(List.of(new Transfer("bank:co", OWNER.account(), 1, "trusted outflow"))));
        eq(0, w.engine.taxes.pay("bank:co", Money.MAX, true));
        eq(11_000, w.engine.balance("bank:co")); eq(0, w.engine.balance(OWNER.account()));
        eq(10_000, deposit.balance); eq("0", deposit.pendingInterest); eq("0", deposit.interestRemainder);
        eq(lastAccrued, deposit.lastAccruedAt); eq(history, w.data.transactions.size()); eq(0, w.dirty);
        w.engine.banking.tickDeposit(bank + "|" + BUYER.id());
        eq(11_000, deposit.balance);
        expect(UserError.class, () -> w.engine.pay(OWNER, "bank:co", OWNER.account(), 1, "same decision after maintenance"));
        w.engine.adminAdjust(ADMIN, "bank:co", "mint", 100);
        w.engine.pay(OWNER, "bank:co", OWNER.account(), 100, "only actual surplus");
        eq(11_000, w.engine.balance("bank:co"));
    }

    public static void depositReserveQuotesPreserveSavedRatesFractionsAndLargeInterest() {
        TestWorld w = new TestWorld();
        String bank = w.bank(1, 100, 0);
        w.set(BUYER, 100);
        w.engine.banking.deposit(BUYER, bank, 100);
        w.advance(333);
        w.engine.banking.tickDeposit(bank + "|" + BUYER.id());
        EconomyData.Deposit deposit = w.data.banks.get(bank).deposits.get(BUYER.id().toString());
        eq("3330000", deposit.interestRemainder);
        w.engine.banking.rates(OWNER, bank, 0, 0, 0, 0, 0);
        w.config.financialPeriodMillis = 2000;
        w.advance(667);
        w.dirty = 0;
        eq(BigInteger.valueOf(101), w.engine.banking.liabilities(w.data.banks.get(bank)));
        expect(UserError.class, () -> w.engine.pay(OWNER, "bank:co", OWNER.account(), 1, "saved fractional interest"));
        eq("3330000", deposit.interestRemainder); eq("0", deposit.pendingInterest); eq(0, w.dirty);
        w.engine.banking.tickDeposit(bank + "|" + BUYER.id());
        eq(101, deposit.balance); eq("0", deposit.interestRemainder);
        w.clock.now -= 500;
        eq(BigInteger.valueOf(101), w.engine.banking.liabilities(w.data.banks.get(bank)));

        TestWorld huge = new TestWorld();
        String hugeBank = huge.bank(Money.MAX - 1000, 100, 0);
        huge.set(BUYER, 1000);
        huge.engine.banking.deposit(BUYER, hugeBank, 1000);
        huge.clock.now = Long.MAX_VALUE;
        huge.dirty = 0;
        check(huge.engine.banking.liabilities(huge.data.banks.get(hugeBank)).compareTo(BigInteger.valueOf(Money.MAX)) > 0,
                "long elapsed interest was truncated");
        eq(Money.MAX, huge.engine.banking.requiredReserve(huge.data.banks.get(hugeBank)));
        eq(Money.MAX, huge.engine.banking.protectedBalance(huge.data.banks.get(hugeBank)));
        expect(UserError.class, () -> huge.engine.pay(OWNER, "bank:co", OWNER.account(), 1, "overflow-safe reserve"));
        eq(Money.MAX, huge.engine.balance("bank:co")); eq(0, huge.dirty);
    }

    public static void lendingAndWithdrawalsIncludeOtherDepositorsElapsedInterest() {
        TestWorld w = new TestWorld(c -> c.minimumBankReserveBps = 10_000);
        String bank = w.bank(1000, 100, 0);
        w.set(BUYER, 10_000); w.set(FOREIGN, 1000);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        w.engine.banking.deposit(FOREIGN, bank, 1000);
        w.advance(10_000);
        w.engine.banking.associate(OWNER, bank);
        String application = w.engine.banking.requestLoan(OWNER, bank, 100, 4, null, false, false, false);
        eq(12_100, w.engine.banking.requiredReserve(w.data.banks.get(bank)));
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, application));
        eq("REQUESTED", w.data.loans.get(application).status); eq(0, w.dirty);
        expect(UserError.class, () -> w.engine.banking.withdraw(BUYER, bank, 1000));
        eq(12_000, w.engine.balance("bank:co")); eq(0, w.engine.balance(BUYER.account()));
        eq(11_000, w.engine.banking.balance(BUYER, bank, null).balance());
        eq("0", w.data.banks.get(bank).deposits.get(FOREIGN.id().toString()).pendingInterest);
        check(w.dirty > 0, "withdrawal failure lost the customer's separately accrued/funded interest");
        w.engine.adminAdjust(ADMIN, "bank:co", "mint", 100);
        w.engine.banking.withdraw(BUYER, bank, 1000);
        eq(11_100, w.engine.balance("bank:co")); eq(10_000, w.engine.banking.balance(BUYER, bank, null).balance());
    }

    public static void loanApplicationsUseCurrentContractualExposureWithoutBookingInterest() {
        TestWorld w = new TestWorld(c -> { c.maximumBorrowerDebtCents = 1000; c.loanApplicationLifetimeMillis = 10_000; });
        String bank = w.bank(10_000, 0, 500);
        String active = w.loan(BUYER, bank, 800, null, false, false, false);
        long lastAccrued = w.data.loans.get(active).lastAccruedAt;
        w.advance(1000);
        w.engine.banking.rates(OWNER, bank, 0, 0, 0, 0, 0);
        w.config.financialPeriodMillis = 2000;
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 200, 4, null, false, false, false));
        eq(1, w.data.loans.size()); eq(0, w.data.loans.get(active).interest);
        eq(lastAccrued, w.data.loans.get(active).lastAccruedAt); eq(0, w.dirty);
        String application = w.engine.banking.requestLoan(BUYER, bank, 160, 4, null, false, false, false);
        w.engine.banking.approve(OWNER, application);
        eq(840, w.engine.banking.loanSummary(BUYER, active).total());
        eq(160, w.engine.banking.loanSummary(BUYER, application).total());
    }

    public static void repossessionSurplusPreservesElapsedDepositReserves() {
        TestWorld w = new TestWorld(c -> c.minimumBankReserveBps = 10_000);
        String bank = w.bank(10_000, 100, 0);
        w.set(FOREIGN, 10_000);
        w.engine.banking.deposit(FOREIGN, bank, 10_000);
        String loan = w.loan(BUYER, bank, 5000, BUYER_CLAIM, false, true, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 5000, "spend loan");
        w.advance(2000);
        expect(UserError.class, () -> w.engine.banking.tickLoan(loan));
        eq("DEFAULTED", w.data.loans.get(loan).status);
        eq(BUYER.account(), w.governance.claims.get(BUYER_CLAIM).ownerAccount());
        eq(15_000, w.engine.balance("bank:co"));
        eq("0", w.data.banks.get(bank).deposits.get(FOREIGN.id().toString()).pendingInterest);
        w.engine.adminAdjust(ADMIN, "bank:co", "mint", 200);
        w.engine.banking.recover(OWNER, loan);
        eq("REPAID", w.data.loans.get(loan).status);
        eq("company:co", w.governance.claims.get(BUYER_CLAIM).ownerAccount());
        eq(10_200, w.engine.balance("bank:co")); eq(5000, w.engine.balance(BUYER.account()));
    }

    public static void loanFundingRechecksAggregateExposureBeforeAndAfterInterestRefresh() {
        TestWorld w = new TestWorld(c -> { c.maximumBorrowerDebtCents = 1000; c.loanApplicationLifetimeMillis = 10_000; });
        String bank = w.bank(10_000, 0, 500);
        String active = w.loan(BUYER, bank, 800, null, false, false, false);
        String application = w.engine.banking.requestLoan(BUYER, bank, 200, 4, null, false, false, false);
        w.advance(1000);
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, application));
        eq("REQUESTED", w.data.loans.get(application).status); eq(0, w.data.loans.get(active).interest);
        eq(9200, w.engine.balance("bank:co")); eq(800, w.engine.balance(BUYER.account())); eq(0, w.dirty);
        eq(40, w.engine.banking.loanSummary(BUYER, active).interest());
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, application));
        eq(0, w.dirty); eq("REQUESTED", w.data.loans.get(application).status);
        w.engine.banking.repay(BUYER, active, 40);
        w.engine.banking.approve(OWNER, application);
        eq(1000, w.engine.banking.loanSummary(BUYER, active).total()
                + w.engine.banking.loanSummary(BUYER, application).total());
    }

    public static void borrowerLimitsIncludeOtherApplicationsButNotExpiredOnesOrDuplicatePrincipal() {
        TestWorld w = new TestWorld(c -> c.maximumBorrowerDebtCents = 1000);
        String bank = w.bank(10_000, 0, 0);
        w.loan(BUYER, bank, 600, null, false, false, false);
        String first = w.engine.banking.requestLoan(BUYER, bank, 200, 4, null, false, false, false);
        String second = w.engine.banking.requestLoan(BUYER, bank, 200, 4, null, false, false, false);
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 1, 4, null, false, false, false));
        w.config.maximumBorrowerDebtCents = 900;
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, first));
        w.engine.banking.reject(BUYER, second);
        w.engine.banking.approve(OWNER, first);
        eq("ACTIVE", w.data.loans.get(first).status);
        eq(800, w.engine.balance(BUYER.account()));

        TestWorld expired = new TestWorld(c -> c.maximumBorrowerDebtCents = 1000);
        String expiredBank = expired.bank(10_000, 0, 500);
        expired.engine.banking.associate(BUYER, expiredBank);
        String old = expired.engine.banking.requestLoan(BUYER, expiredBank, 1000, 4, null, false, false, false);
        expired.advance(1000);
        String current = expired.engine.banking.requestLoan(BUYER, expiredBank, 1000, 4, null, false, false, false);
        expect(UserError.class, () -> expired.engine.banking.approve(OWNER, old));
        expired.engine.banking.approve(OWNER, current);
        eq(1000, expired.engine.balance(BUYER.account()));
        eq(0, expired.data.loans.get(current).interest);
    }

    public static void borrowerExposureUsesCarriedFractionsLifetimeCapsAndAllBanks() {
        TestWorld w = new TestWorld(c -> { c.maximumBorrowerDebtCents = 1000; c.loanApplicationLifetimeMillis = 10_000; });
        String bank = w.bank(10_000, 0, 500);
        String active = w.loan(BUYER, bank, 800, null, false, false, false);
        w.advance(333);
        eq(13, w.engine.banking.loanSummary(BUYER, active).interest());
        w.governance.companies.put("other", new Government.Company());
        w.set("company:other", 1000);
        String other = w.engine.banking.create(OWNER, "other", "Other bank", 1000, 0, 0);
        w.engine.banking.associate(BUYER, other);
        String application = w.engine.banking.requestLoan(BUYER, other, 187, 4, null, false, false, false);
        w.advance(25);
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, application));
        eq(13, w.data.loans.get(active).interest); eq("3200000", w.data.loans.get(active).interestRemainder);
        eq(14, w.engine.banking.loanSummary(BUYER, active).interest());

        TestWorld capped = new TestWorld(c -> { c.maximumBorrowerDebtCents = 1000; c.loanLifetimeInterestCapBps = 500; });
        String cappedBank = capped.bank(10_000, 0, 1000);
        String cappedLoan = capped.loan(BUYER, cappedBank, 800, null, false, false, false);
        capped.advance(1_000_000_000_000L);
        String atLimit = capped.engine.banking.requestLoan(BUYER, cappedBank, 160, 4, null, false, false, false);
        capped.engine.banking.approve(OWNER, atLimit);
        eq(840, capped.engine.banking.loanSummary(BUYER, cappedLoan).total());
        capped.dirty = 0;
        capped.advance(1);
        eq(840, capped.engine.banking.loanSummary(BUYER, cappedLoan).total());
        eq(0, capped.dirty);
    }

    public static void borrowerDefaultStillBlocksNewApplicationsAndPendingFunding() {
        TestWorld w = new TestWorld(c -> c.loanApplicationLifetimeMillis = 10_000);
        String bank = w.bank(10_000, 0, 0);
        String active = w.loan(BUYER, bank, 800, null, false, false, false);
        String application = w.engine.banking.requestLoan(BUYER, bank, 200, 4, null, false, false, false);
        w.advance(2000);
        w.engine.banking.tickLoan(active);
        eq("DEFAULTED", w.data.loans.get(active).status);
        expect(UserError.class, () -> w.engine.banking.approve(OWNER, application));
        expect(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 1, 4, null, false, false, false));
        eq("REQUESTED", w.data.loans.get(application).status); eq(800, w.engine.balance(BUYER.account()));
    }

    public static void mandatoryPropertyArrearsFollowTheSellerAfterTheirFinalSaleAndRestart() {
        TestWorld w = new TestWorld(c -> { c.mandatoryPropertyTaxes = true; c.propertyListingLifetimeMillis = 10_000; });
        w.governance.setting("n1", "propertyTaxBps", "1000");
        w.engine.valueOf(CLAIM);
        w.engine.property.list(OWNER, CLAIM, 1000);
        w.set(BUYER, 1000);
        w.engine.ledger.spendingLimit(OWNER.account(), 0);
        w.advance(1000);
        w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE);
        eq(BigInteger.valueOf(900), w.engine.taxes.arrears(OWNER.account()));
        eq(1000, w.engine.balance(OWNER.account()));
        check(w.governance.claims.values().stream().noneMatch(c -> c.ownerAccount().equals(OWNER.account())),
                "seller still owns a claim");
        w.restart();
        w.dirty = 0;
        w.tick();
        eq(BigInteger.ZERO, w.engine.taxes.arrears(OWNER.account()));
        eq(100, w.engine.balance(OWNER.account())); eq(900, w.engine.balance("nation:n1"));
        eq(BigInteger.ZERO, w.engine.taxes.arrears(BUYER.account()));
        check(w.dirty > 0, "former-owner collection was not marked dirty");
    }

    public static void mandatoryPropertyArrearsSurviveRepossession() {
        TestWorld w = new TestWorld(c -> c.mandatoryPropertyTaxes = true);
        w.governance.setting("n1", "propertyTaxBps", "1000");
        String bank = w.bank(20_000, 0, 0);
        String loan = w.loan(BUYER, bank, 5000, BUYER_CLAIM, false, true, false);
        w.engine.pay(BUYER, "me", OWNER.account(), 5000, "spend loan");
        w.advance(2000);
        w.engine.banking.tickLoan(loan);
        eq("company:co", w.governance.claims.get(BUYER_CLAIM).ownerAccount());
        eq(BigInteger.valueOf(1800), w.engine.taxes.arrears(BUYER.account()));
        eq(4000, w.engine.balance(BUYER.account()));
        w.restart();
        w.tick();
        eq(BigInteger.ZERO, w.engine.taxes.arrears(BUYER.account()));
        eq(2200, w.engine.balance(BUYER.account()));
        check(w.data.taxHistory.stream().anyMatch(t -> t.payer().equals(BUYER.account())
                && t.kind().equals("propertyTaxBps") && t.status().equals("ARREARS PAID") && t.cents() == 1800),
                "repossessed owner's assessment was not collected from that owner");
    }

    public static void mandatoryPropertyCollectionIsBoundedFairAndDoesNotCollectOptionalTaxes() {
        TestWorld w = new TestWorld(c -> { c.mandatoryPropertyTaxes = true; c.workPerTick = 1; });
        w.governance.claims.clear();
        w.governance.companies.clear();
        w.set(OWNER, 100); w.set(BUYER, 100);
        w.set("nation:n1", Money.MAX);
        w.engine.taxes.assess(OWNER.account(), "nation:n1", "n1", "propertyTaxBps", 100, "old property");
        w.engine.taxes.assess(BUYER.account(), "nation:n2", "n2", "incomeTaxBps", 50, "optional income");
        w.engine.taxes.assess(BUYER.account(), "nation:n2", "n2", "companyFee", 50, "optional fee");
        w.engine.taxes.assess(BUYER.account(), "nation:n2", "n2", "propertyTaxBps", 100, "another old property");
        w.restart();
        w.dirty = 0;
        w.tick();
        eq(100, w.engine.balance(OWNER.account())); eq(100, w.engine.balance(BUYER.account())); eq(0, w.dirty);
        w.tick();
        eq(100, w.engine.balance("nation:n2")); eq(0, w.engine.balance(BUYER.account()));
        eq(BigInteger.valueOf(100), w.engine.taxes.arrears(BUYER.account()));
        eq(1, w.data.taxHistory.stream().filter(t -> t.status().equals("ARREARS PAID")).count());
        w.set("nation:n1", Money.MAX - 100);
        w.tick();
        eq(0, w.engine.balance(OWNER.account())); eq(BigInteger.ZERO, w.engine.taxes.arrears(OWNER.account()));
        w.set(BUYER, 100);
        for (int i = 0; i < 4; i++) w.tick();
        eq(100, w.engine.balance(BUYER.account())); eq(BigInteger.valueOf(100), w.engine.taxes.arrears(BUYER.account()));
        eq(100, w.engine.taxes.pay(BUYER.account(), Money.MAX, false));
        eq(BigInteger.ZERO, w.engine.taxes.arrears(BUYER.account()));
    }

    public static void canonicalRawAndPrefixedPlayerRecipientsUseTheSameValidation() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 1000);
        String full = "abcdef12-abcd-abcd-abcd-abcdefabcdef";
        int accounts = w.data.accounts.size(), history = w.data.transactions.size();
        w.dirty = 0;
        for (String invalid : List.of("1-1-1-1-1", "player:1-1-1-1-1", full.toUpperCase(java.util.Locale.ROOT),
                "player:" + full.toUpperCase(java.util.Locale.ROOT), "abcdef12-abcd-abcd-abcd-abcdefabcde", "no-such-player")) {
            expect(UserError.class, () -> w.engine.execute(OWNER, "pay " + invalid + " 1.00", InventoryPort.NONE));
        }
        eq(1000, w.engine.balance(OWNER.account())); eq(accounts, w.data.accounts.size());
        eq(history, w.data.transactions.size()); eq(0, w.dirty);
        check(!w.data.accounts.containsKey("player:00000001-0001-0001-0001-000000000001"), "short UUID created a padded account");
        for (String valid : List.of("bUyEr", BUYER.id().toString(), BUYER.account(), full, "player:" + full)) {
            w.engine.execute(OWNER, "pay " + valid + " 1.00", InventoryPort.NONE);
        }
        eq(300, w.engine.balance(BUYER.account())); eq(200, w.engine.balance("player:" + full));
        eq(500, w.engine.balance(OWNER.account()));
        Actor named = actor(44, "Quoted Friend", false, 0);
        w.engine.ensurePlayer(named);
        w.engine.execute(OWNER, "pay \"Quoted Friend\" 1.00", InventoryPort.NONE);
        eq(100, w.engine.balance(named.account()));
        w.engine.ensurePlayer(actor(45, "Buyer", false, 0));
        expect(UserError.class, () -> w.engine.execute(OWNER, "pay Buyer 1.00", InventoryPort.NONE));
    }

    public static void establishedPlayerReadQueriesDoNotMarkEconomyDirty() {
        TestWorld w = new TestWorld();
        String bank = w.bank(10_000, 100, 500);
        w.loan(BUYER, bank, 800, null, false, false, false);
        w.set(OWNER, 1000);
        w.engine.banking.deposit(OWNER, bank, 100);
        w.engine.property.value(CLAIM);
        String listing = w.engine.commerce.listMarket(OWNER, 1, 100, new Inventory().item(0, "minecraft:wheat", 1));
        w.advance(500);
        w.dirty = 0;
        for (String query : List.of("help", "balance me", "accounts", "account list", "history", "limits", "hub settings",
                "hub prices", "prices", "price get minecraft:wheat", "market search", "market own", "market inspect " + listing,
                "market deliveries", "property value here", "property listings", "property own", "company balance co",
                "company fees co", "stock search", "stock own", "bank list", "bank balance co", "bank report co",
                "bank loans", "bank loans co", "tax rates", "tax quote 1.00", "tax arrears", "tax report", "top player", "guide")) {
            w.engine.execute(OWNER, query, InventoryPort.NONE);
            eq(0, w.dirty);
        }
        w.engine.execute(ADMIN, "admin audit", InventoryPort.NONE);
        w.engine.execute(ADMIN, "admin notices", InventoryPort.NONE);
        w.engine.execute(ADMIN, "admin suggestions", InventoryPort.NONE);
        eq(0, w.dirty);
        expect(UserError.class, () -> w.engine.execute(OWNER, "pay Buyer -1", InventoryPort.NONE));
        expect(UserError.class, () -> w.engine.execute(OWNER, "not-a-command", InventoryPort.NONE));
        eq(0, w.dirty);
    }

    public static void ordinaryEconomyMutationsAndNewPlayersMarkDirtyWithoutGatewayHelp() {
        TestWorld w = new TestWorld();
        w.bank(10_000, 0, 0);
        w.set(OWNER, 10_000);
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 10);
        for (String command : List.of("pay Buyer 1.00", "account select company:co", "hub sell 1", "market list 1 1.00",
                "bank associate co", "bank deposit 1.00 co", "bank withdraw 0.50 co", "property list here 1.00",
                "property delist here", "stock list co 1 1.00", "hub suggest 1.00 review")) {
            w.dirty = 0;
            w.engine.execute(OWNER, command, inventory);
            check(w.dirty > 0, "mutation did not invoke the dirty callback: " + command);
        }
        w.dirty = 0;
        w.engine.ledger.spendingLimit(OWNER.account(), 10_000);
        check(w.dirty > 0, "spending-limit mutation was not persistent");
        w.dirty = 0;
        w.engine.taxes.assess(OWNER.account(), "nation:n1", "n1", "incomeTaxBps", 10, "test");
        check(w.dirty > 0, "assessment was not persistent");
        w.dirty = 0;
        w.engine.taxes.pay(OWNER.account(), Money.MAX, false);
        check(w.dirty > 0, "tax payment was not persistent");
        w.config.initialPlayerBalanceCents = 100;
        Actor newcomer = actor(600, "Newcomer", false, 0);
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.execute(newcomer, "not-a-command", InventoryPort.NONE));
        eq(100, w.engine.balance(newcomer.account()));
        check(w.dirty > 0, "failed first command lost the initial grant/player record");
        w.dirty = 0;
        w.engine.execute(newcomer, "help", InventoryPort.NONE);
        eq(0, w.dirty);
        w.engine.ensurePlayer(actor(600, "Renamed", false, 0));
        check(w.dirty > 0, "remembered name change was not persistent");
    }

    public static void readAccrualTracksWholeAndFractionalInterestButNotIdleClocks() {
        TestWorld w = new TestWorld();
        String bank = w.bank(10_000, 0, 500);
        String loan = w.loan(BUYER, bank, 800, null, false, false, false);
        w.advance(25);
        w.dirty = 0;
        w.engine.execute(BUYER, "loan show " + loan, InventoryPort.NONE);
        eq(1, w.data.loans.get(loan).interest);
        check(w.dirty > 0, "read-triggered interest was not persistent");
        w.dirty = 0;
        w.engine.banking.loanSummary(BUYER, loan);
        eq(0, w.dirty);
        w.advance(1);
        w.engine.banking.loanSummary(BUYER, loan);
        eq(1, w.data.loans.get(loan).interest);
        check(w.dirty > 0, "carried fractional interest was not persistent");

        TestWorld idle = new TestWorld(c -> c.loanLifetimeInterestCapBps = 0);
        String idleBank = idle.bank(10_000, 0, 500);
        idle.set(BUYER, 100);
        idle.engine.banking.deposit(BUYER, idleBank, 100);
        String cappedLoan = idle.loan(BUYER, idleBank, 800, null, false, false, false);
        idle.advance(10);
        idle.dirty = 0;
        idle.engine.banking.tickDeposit(idleBank + "|" + BUYER.id());
        idle.engine.banking.loanSummary(BUYER, cappedLoan);
        idle.engine.banking.tickLoan(cappedLoan);
        eq(0, idle.dirty);
        eq(idle.clock.millis(), idle.data.loans.get(cappedLoan).lastAccruedAt);
    }

    public static void partialFailuresPreserveAccrualAssessmentsAndEscrowMutationCallbacks() {
        TestWorld w = new TestWorld();
        String bank = w.bank(10_000, 100, 500);
        w.set(BUYER, 10_000);
        w.engine.banking.deposit(BUYER, bank, 10_000);
        String loan = w.loan(FOREIGN, bank, 800, null, false, false, false);
        w.advance(1000);
        int history = w.data.transactions.size();
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.banking.deposit(BUYER, bank, 1));
        eq("100", w.data.banks.get(bank).deposits.get(BUYER.id().toString()).pendingInterest);
        check(w.dirty > 0, "failed deposit lost independently accrued interest");
        w.dirty = 0;
        expect(UserError.class, () -> w.engine.banking.repay(FOREIGN, loan, Money.MAX));
        eq(40, w.data.loans.get(loan).interest);
        eq(history, w.data.transactions.size());
        check(w.dirty > 0, "failed repayment lost independently accrued interest");

        TestWorld property = new TestWorld(c -> c.propertyListingLifetimeMillis = 10_000);
        property.governance.setting("n1", "propertyTaxBps", "1000");
        property.engine.valueOf(CLAIM);
        property.engine.property.list(OWNER, CLAIM, 1000);
        property.advance(1000);
        property.dirty = 0;
        expect(UserError.class, () -> property.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE));
        eq(BigInteger.valueOf(900), property.engine.taxes.arrears(OWNER.account()));
        eq(OWNER.account(), property.governance.claims.get(CLAIM).ownerAccount());
        check(property.dirty > 0, "failed property purchase lost due assessments");

        TestWorld expired = new TestWorld();
        String listing = expired.engine.commerce.listMarket(OWNER, 1, 100, new Inventory().item(0, "minecraft:wheat", 1));
        expired.advance(1000);
        expired.dirty = 0;
        expect(UserError.class, () -> expired.engine.execute(OWNER, "market inspect " + listing, InventoryPort.NONE));
        eq(0, expired.data.market.size()); eq(1, expired.data.deliveries.get(OWNER.id().toString()).size());
        check(expired.dirty > 0, "expired-listing read lost its escrow refund");
    }

    public static void scheduledRemovalsAndMissedPaymentCorrectionsMarkDirty() {
        TestWorld w = new TestWorld();
        w.engine.commerce.tickCompany("co");
        w.governance.companies.remove("co");
        w.dirty = 0;
        check(!w.engine.commerce.tickCompany("co"), "missing company remained scheduled");
        check(w.data.companyFinance.isEmpty(), "missing company retained its fee schedule");
        check(w.dirty > 0, "company-finance removal was not persistent");
        w.dirty = 0;
        w.engine.commerce.tickCompany("co");
        eq(0, w.dirty);

        TestWorld loans = new TestWorld();
        String bank = loans.bank(10_000, 0, 0);
        String loan = loans.loan(BUYER, bank, 800, null, false, false, false);
        loans.advance(1000);
        loans.engine.banking.tickLoan(loan);
        eq(1, loans.data.loans.get(loan).missedPayments);
        loans.engine.banking.repay(BUYER, loan, 200);
        loans.dirty = 0;
        loans.engine.banking.tickLoan(loan);
        eq(0, loans.data.loans.get(loan).missedPayments);
        check(loans.dirty > 0, "cleared missed-payment count was not persistent");
    }

    public static void noOpLedgerAndPreferencesStayCleanWhileHistoryTrimsRemainPersistent() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 1000);
        w.engine.selectAccount(OWNER, "me");
        w.dirty = 0;
        w.engine.transferBatch(List.of());
        w.engine.transferBatch(List.of(new Transfer(OWNER.account(), OWNER.account(), 100, "no-op")));
        w.engine.adminAdjust(ADMIN, OWNER.account(), "set", 1000);
        w.engine.ledger.spendingLimit(OWNER.account(), Money.MAX);
        w.engine.selectAccount(OWNER, "me");
        w.engine.reconfigure(w.config, w.values);
        eq(0, w.dirty);
        expect(UserError.class, () -> w.engine.ledger.prepare(List.of(new Transfer(OWNER.account(), BUYER.account(), 100, "rollback")))
                .commitWith(() -> { throw new UserError("Participant refused."); }));
        eq(0, w.dirty); eq(1000, w.engine.balance(OWNER.account()));
        for (int i = 0; i < 12; i++) w.engine.pay(OWNER, "me", BUYER.account(), 1, "history");
        w.config.historyLimit = 10;
        w.dirty = 0;
        w.engine.reconfigure(w.config, w.values);
        eq(10, w.data.transactions.size());
        check(w.dirty > 0, "history trimming was not persistent");
    }

    private static long totalLedger(TestWorld w) {
        return w.data.accounts.values().stream().mapToLong(account -> account.balance).sum();
    }
    static void eq(long expected, long actual) { if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual); }
    static void eq(Object expected, Object actual) { if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual); }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " but the action succeeded.");
    }

    public static void main(String[] args) throws Exception {
        int count = 0;
        List<String> failures = new ArrayList<>();
        for (var method : EconomyRegressionScenarios.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0) {
                try { method.invoke(null); count++; System.out.println("PASS " + method.getName()); }
                catch (java.lang.reflect.InvocationTargetException failure) {
                    failures.add(method.getName());
                    System.err.println("FAIL " + method.getName());
                    failure.getCause().printStackTrace();
                }
            }
        }
        if (!failures.isEmpty()) throw new AssertionError("Failed scenarios: " + failures);
        System.out.println(count + " deterministic economy scenarios passed.");
    }
}
