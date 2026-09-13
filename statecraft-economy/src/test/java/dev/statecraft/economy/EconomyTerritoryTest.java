package dev.statecraft.economy;

import com.google.gson.Gson;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static dev.statecraft.economy.TestWorld.*;
import static org.junit.jupiter.api.Assertions.*;

final class EconomyTerritoryTest {
    private static final List<String> GOVERNMENTS = List.of("n1", "s1", "c1");
    private static final List<String> TREASURIES = List.of("nation:n1", "state:s1", "city:c1");

    @Test void existingTiersApplyOnceWithoutActorNationFallbackOrCopiedRates() {
        for (int depth = 0; depth < 3; depth++) {
            TestWorld w = new TestWorld();
            allocate(w, CLAIM, depth);
            w.governance.nations.put(OWNER.id(), "n2");
            for (String kind : List.of("incomeTaxBps", "salesTaxBps", "propertyTaxBps", "corporateTaxBps", "tariffBps")) {
                rates(w, kind);
                w.governance.setting("n2", kind, "4000");
                List<Taxation.Charge> charges = w.engine.taxes.quote(CLAIM, "n2", 10_000, kind);
                assertEquals(GOVERNMENTS.subList(0, depth + 1), charges.stream().map(Taxation.Charge::government).toList());
                assertEquals(List.of(100L, 300L, 600L).get(depth).longValue(), Taxation.total(charges));
            }
            assertEquals(GOVERNMENTS.subList(0, depth + 1),
                    w.engine.taxes.tiers(CLAIM, "n2").stream().map(GovernanceAccess.GovernmentView::id).toList());
            Commerce.Sale sale = w.engine.commerce.sellHub(OWNER, 4, new Inventory().item(0, "minecraft:wheat", 4));
            assertEquals(List.of(2L, 6L, 12L).get(depth).longValue(), sale.taxes());
            assertEquals((depth + 1) * 2, w.data.taxHistory.size());
            assertTrue(w.data.taxHistory.stream().allMatch(t -> t.status().equals("PAID")
                    && GOVERNMENTS.subList(0, w.engine.taxes.tiers(CLAIM, "n2").size()).contains(t.government())));
            assertEquals(0, w.engine.balance("nation:n2"));
            assertFalse(w.engine.execute(OWNER, "tax rates", InventoryPort.NONE).contains("Foreign Nation"));
        }

        TestWorld w = new TestWorld();
        w.governance.setting("n1", "salesTaxBps", "1000");
        assertEquals(1, w.engine.taxes.quote(CLAIM, "n2", 10_000, "salesTaxBps").size());
        assertEquals(List.of("n2"), w.engine.taxes.tiers(FOREIGN.chunkKey(), "n2").stream()
                .map(GovernanceAccess.GovernmentView::id).toList());
        assertTrue(w.engine.taxes.tiers(FOREIGN.chunkKey(), null).isEmpty());
        assertTrue(w.engine.taxes.tiers(FOREIGN.chunkKey(), "c1").isEmpty());
        GOVERNMENTS.forEach(w.governance.governments::remove);
        assertTrue(w.engine.taxes.tiers(CLAIM, "n2").isEmpty(), "A claimed region must not adopt a foreign actor's tax nation.");
    }

    @Test void valuationUsesTheMostSpecificPresentBaseAndPreservesAllFactors() {
        long[] expected = {21_562, 42_253, 81_892};
        for (int depth = 0; depth < 3; depth++) {
            TestWorld w = new TestWorld(c -> {
                c.locationBonusBps = 5000;
                c.demandBonusPerChunkBps = 250;
                c.improvementBonusBps = 500;
            }, claim -> new ValuationEnvironment.Conditions("test:rich", 12_000, 0, 4));
            allocate(w, CLAIM, depth);
            w.governance.improvements(CLAIM, 2);
            rates(w, "propertyTaxBps");
            w.governance.setting("n1", "baseChunkValue", "10000");
            w.governance.setting("s1", "baseChunkValue", depth == 0 ? "not applicable" : "20000");
            w.governance.setting("c1", "baseChunkValue", depth < 2 ? "not applicable" : "40000");
            EconomyData.Valuation value = w.engine.property.value(CLAIM);
            assertEquals(expected[depth], value.value);
            assertEquals("test:rich", value.biome);
            assertEquals(11_000, value.improvementBps);
            assertEquals(11_000, value.demandBps);
            assertEquals(depth == 0 ? null : "s1", value.taxBasis.stateId());
            assertEquals(depth == 2 ? "c1" : null, value.taxBasis.cityId());
            long nextTax = value.nextTaxAt;
            w.governance.allocation(CLAIM, null, null);
            assertEquals(expected[0], w.engine.property.value(CLAIM).value, "Allocation changes invalidate a cached base immediately.");
            assertEquals(nextTax, w.data.valuations.get(CLAIM).nextTaxAt);
            assertEquals(2, w.governance.claims.get(CLAIM).improvements());
        }
    }

    @Test void publicSalesPayTheActualNationStateOrCityTitle() {
        for (int depth = 0; depth < 3; depth++) {
            TestWorld w = new TestWorld();
            w.governance.claimOwner(CLAIM, "city:c1");
            allocate(w, CLAIM, depth);
            GovernanceAccess.ClaimView before = w.governance.claims.get(CLAIM);
            w.set(BUYER, 2500);
            assertThrows(UserError.class, () -> w.engine.property.list(FOREIGN, CLAIM, 2000));
            w.engine.property.list(OWNER, CLAIM, 2000);
            assertEquals(TREASURIES.get(depth), w.data.properties.get(CLAIM).ownerAccount);
            assertThrows(UserError.class, () -> w.governance.assign(CLAIM, null, null));
            assertEquals(2000, w.engine.property.quoteBuy(BUYER, CLAIM, false, null, InventoryPort.NONE).settlement().sellerNet());
            w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE);
            assertEquals(2000, w.engine.balance(TREASURIES.get(depth)));
            assertEquals(0, w.engine.balance(OWNER.account()));
            assertEquals(500, w.engine.balance(BUYER.account()));
            assertEquals(BUYER.account(), w.governance.claims.get(CLAIM).ownerAccount());
            sameTerritory(before, w.governance.claims.get(CLAIM));
            assertFalse(w.engine.isClaimEncumbered(CLAIM));
        }
    }

    @Test void stalePublicTitlesAndRefusedTransfersLeaveCashAndLedgerUntouched() {
        for (int depth = 0; depth < 3; depth++) {
            TestWorld w = new TestWorld();
            w.governance.claimOwner(CLAIM, "city:c1");
            allocate(w, CLAIM, depth);
            Inventory cash = new Inventory().item(0, TEN, 1);
            w.engine.property.list(OWNER, CLAIM, 1000);
            int transactions = w.data.transactions.size();
            allocate(w, CLAIM, (depth + 1) % 3);
            assertThrows(UserError.class, () -> w.engine.property.quoteBuy(BUYER, CLAIM, true, null, cash));
            assertThrows(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, cash));
            allocate(w, CLAIM, depth);
            w.governance.rejectProperty = true;
            assertThrows(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, true, cash));
            assertEquals(transactions, w.data.transactions.size());
            assertEquals(1, cash.count(TEN));
            assertEquals(0, w.engine.balance(BUYER.account()));
            assertEquals(0, w.engine.balance(TREASURIES.get(depth)));
            assertEquals(0, w.governance.propertyTransfers);
            assertTrue(w.engine.isClaimEncumbered(CLAIM));
            w.governance.rejectProperty = false;
            w.engine.property.buy(BUYER, CLAIM, true, cash);
            assertEquals(0, cash.count(TEN));
            assertEquals(1000, w.engine.balance(TREASURIES.get(depth)));
        }
    }

    @Test void allocationsNeverRewritePrivatePlayerOrCompanyTitles() {
        for (String owner : List.of(OWNER.account(), "company:co")) {
            for (int depth = 0; depth < 3; depth++) {
                TestWorld w = new TestWorld();
                w.governance.claimOwner(CLAIM, owner);
                allocate(w, CLAIM, depth);
                assertEquals(owner, w.governance.claims.get(CLAIM).ownerAccount());
                rates(w, owner.startsWith("company:") ? "corporateTaxBps" : "incomeTaxBps");
                w.set(BUYER, 10_000);
                w.engine.property.list(OWNER, CLAIM, 10_000);
                w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE);
                assertEquals(List.of(9900L, 9700L, 9400L).get(depth).longValue(), w.engine.balance(owner));
                assertTrue(w.data.taxHistory.stream().allMatch(t -> t.payer().equals(owner)));
            }
        }
    }

    @Test void optionalCollateralStillRequiresConsentAndRecoversOnlyTheNamedTitle() {
        for (int depth = 0; depth < 3; depth++) {
            TestWorld w = new TestWorld();
            allocate(w, BUYER_CLAIM, depth);
            GovernanceAccess.ClaimView before = w.governance.claims.get(BUYER_CLAIM);
            String bank = w.bank(20_000, 0, 0);
            w.engine.banking.associate(BUYER, bank);
            assertThrows(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 5000, 4,
                    BUYER_CLAIM, false, false, false));
            String loan = w.engine.banking.requestLoan(BUYER, bank, 5000, 4, BUYER_CLAIM, false, true, false);
            assertThrows(UserError.class, () -> w.governance.assign(BUYER_CLAIM, null, null));
            assertThrows(UserError.class, () -> w.engine.banking.approve(FOREIGN, loan));
            w.engine.banking.approve(OWNER, loan);
            w.engine.pay(BUYER, "me", OWNER.account(), 5000, "Spend loan disbursement");
            w.governance.rejectProperty = true;
            w.advance(2000);
            w.tick();
            assertEquals("DEFAULTED", w.data.loans.get(loan).status);
            assertEquals(5000, w.data.loans.get(loan).principal);
            assertEquals(0, w.engine.balance(BUYER.account()));
            assertEquals(15_000, w.engine.balance("bank:co"));
            assertEquals(BUYER.account(), w.governance.claims.get(BUYER_CLAIM).ownerAccount());
            assertTrue(w.engine.isClaimEncumbered(BUYER_CLAIM));
            w.governance.rejectProperty = false;
            w.tick();
            assertEquals("REPAID", w.data.loans.get(loan).status);
            assertTrue(w.data.loans.get(loan).repossessed);
            assertEquals(5000, w.engine.balance(BUYER.account()));
            assertEquals(10_000, w.engine.balance("bank:co"));
            assertEquals("company:co", w.governance.claims.get(BUYER_CLAIM).ownerAccount());
            assertEquals(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount());
            sameTerritory(before, w.governance.claims.get(BUYER_CLAIM));
        }
    }

    @Test void itemAndShareOriginsUseTheClaimNationEvenWithoutCitizenshipOrCities() {
        for (int depth = 0; depth < 3; depth++) {
            TestWorld w = new TestWorld();
            allocate(w, CLAIM, depth);
            allocate(w, BUYER_CLAIM, depth);
            if (depth < 2) w.governance.governments.remove("c1");
            if (depth == 0) w.governance.governments.remove("s1");
            w.governance.nations.remove(OWNER.id());
            w.governance.setting("n1", "tariffBps", "1000");
            w.governance.setting("n2", "tariffBps", "500");
            w.set(BUYER, 3000);
            w.set(FOREIGN, 3000);
            String item = w.engine.commerce.listMarket(OWNER, 2, 1000, new Inventory().item(0, "minecraft:wheat", 2));
            String stock = w.engine.commerce.listStock(OWNER, "co", 2, 1000);
            assertEquals("n1", w.data.market.get(item).sourceNation);
            assertEquals("n1", w.data.stocks.get(stock).sourceNation);
            assertTrue(w.engine.isAccountInUse("nation:n1"), "Open financial origins protect the issuing nation.");
            w.governance.nations.put(OWNER.id(), "n2");
            w.governance.allocation(CLAIM, null, null);
            assertEquals(1000, w.engine.commerce.buyMarket(BUYER, item, 1).buyerCost());
            assertEquals(1000, w.engine.commerce.buyStock(BUYER, stock, 1).buyerCost());
            assertEquals(1050, w.engine.commerce.buyMarket(FOREIGN, item, 1).buyerCost());
            assertEquals(1050, w.engine.commerce.buyStock(FOREIGN, stock, 1).buyerCost());
            assertEquals(100, w.engine.balance("nation:n2"));
            assertEquals(0, w.engine.balance("nation:n1"));
            assertFalse(w.engine.isAccountInUse("nation:n1"));
        }
        TestWorld w = new TestWorld();
        String item = w.engine.commerce.listMarket(FOREIGN, 1, 100, new Inventory().item(0, "minecraft:wheat", 1));
        assertEquals("n2", w.data.market.get(item).sourceNation, "Only wilderness uses the actor's nation as origin.");
    }

    @Test void newlyObservedAllocationArrearsCannotSlipIntoCollateralFunding() {
        TestWorld w = new TestWorld();
        rates(w, "propertyTaxBps");
        w.engine.property.value(BUYER_CLAIM);
        String bank = w.bank(20_000, 0, 0);
        w.engine.banking.associate(BUYER, bank);
        w.advance(1000);
        w.governance.assign(BUYER_CLAIM, null, null);
        assertThrows(UserError.class, () -> w.engine.banking.requestLoan(BUYER, bank, 1000, 4,
                BUYER_CLAIM, false, true, false));
        assertEquals(BigInteger.valueOf(564), w.engine.taxes.arrears(BUYER.account()));
        assertTrue(w.data.loans.isEmpty());
        assertEquals(20_000, w.engine.balance("bank:co"));
        assertEquals(0, w.engine.balance(BUYER.account()));
    }

    @Test void overduePublicAssessmentsStayWithTheirOriginalOwnerAndJurisdiction() {
        TestWorld w = new TestWorld();
        rates(w, "propertyTaxBps");
        w.governance.claimOwner(CLAIM, "city:c1");
        w.governance.allocation(CLAIM, "s1", null);
        assertEquals(9700, w.engine.property.value(CLAIM).value);
        w.advance(1000);
        w.governance.assign(CLAIM, "s1", "c1");
        assertTrue(w.engine.isAccountInUse("state:s1"), "An unassessed old taxpayer must not close its account.");
        assertEquals(9400, w.engine.property.value(CLAIM).value);
        assertEquals(BigInteger.valueOf(97), w.engine.taxes.arrears("state:s1"));
        assertEquals(BigInteger.ZERO, w.engine.taxes.arrears("city:c1"));
        assertEquals(1, w.data.taxHistory.size());
        assertEquals("n1", w.data.taxHistory.get(0).government());
        assertEquals("state:s1", w.data.taxHistory.get(0).payer());
        assertTrue(w.engine.isClaimEncumbered(CLAIM));
        assertThrows(UserError.class, () -> w.governance.assign(CLAIM, null, null));
        w.advance(1000);
        w.engine.property.tickClaim(CLAIM);
        assertEquals(BigInteger.valueOf(97), w.engine.taxes.arrears("state:s1"));
        assertEquals(BigInteger.valueOf(282), w.engine.taxes.arrears("city:c1"));
        assertEquals(List.of("n1", "n1", "s1"), w.data.taxHistory.stream().map(EconomyData.TaxEntry::government).toList());
    }

    @Test void anAllocationBetweenPeriodsStartsNewTaxesWithoutReassigningOldDebts() {
        TestWorld w = new TestWorld();
        rates(w, "propertyTaxBps");
        w.governance.claimOwner(CLAIM, "city:c1");
        w.governance.allocation(CLAIM, null, null);
        w.engine.property.value(CLAIM);
        w.advance(500);
        w.governance.assign(CLAIM, "s1", null);
        w.engine.property.value(CLAIM);
        assertTrue(w.data.arrears.isEmpty());
        w.advance(500);
        w.engine.property.tickClaim(CLAIM);
        assertEquals(BigInteger.ZERO, w.engine.taxes.arrears("nation:n1"));
        assertEquals(BigInteger.valueOf(97), w.engine.taxes.arrears("state:s1"));
        assertEquals(BigInteger.ZERO, w.engine.taxes.arrears("city:c1"));
    }

    @Test void privateTitleChangesWithinTheSameJurisdictionKeepCurrentAssessmentPolicy() {
        TestWorld w = new TestWorld();
        w.governance.allocation(CLAIM, null, null);
        w.governance.setting("n1", "propertyTaxBps", "1000");
        w.engine.property.value(CLAIM);
        w.advance(1000);
        w.governance.setting("n1", "propertyTaxBps", "100");
        w.governance.claimOwner(CLAIM, BUYER.account());
        assertEquals(9900, w.engine.property.value(CLAIM).value);
        assertEquals(BigInteger.valueOf(99), w.engine.taxes.arrears(OWNER.account()));
        assertEquals(BigInteger.ZERO, w.engine.taxes.arrears(BUYER.account()));
    }

    @Test void liensBlockAssignmentsButTitleSettlementRetainsTheOriginalDebtorAndRollback() {
        TestWorld w = new TestWorld(c -> c.propertyListingLifetimeMillis = 30_000);
        w.governance.allocation(CLAIM, null, null);
        w.governance.setting("n1", "propertyTaxBps", "1000");
        w.engine.property.value(CLAIM);
        w.engine.property.list(OWNER, CLAIM, 1000);
        w.set(BUYER, 1000);
        w.advance(1000);
        w.engine.property.tickClaim(CLAIM);
        w.governance.rejectProperty = true;
        assertThrows(UserError.class, () -> w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE));
        assertEquals(0, w.engine.balance(OWNER.account()));
        assertEquals(1000, w.engine.balance(BUYER.account()));
        assertEquals(BigInteger.valueOf(900), w.engine.taxes.arrears(OWNER.account()));
        assertThrows(UserError.class, () -> w.governance.assign(CLAIM, "s1", null));
        w.governance.rejectProperty = false;
        w.engine.property.buy(BUYER, CLAIM, false, InventoryPort.NONE);
        assertEquals(BigInteger.ZERO, w.engine.taxes.arrears(BUYER.account()));
        assertTrue(w.engine.isClaimEncumbered(CLAIM), "A title-only settlement must not leave territorial assignments unlocked.");
        assertThrows(UserError.class, () -> w.governance.assign(CLAIM, "s1", null));
        assertEquals(900, w.engine.taxes.pay(OWNER.account(), 900, false));
        assertFalse(w.engine.isClaimEncumbered(CLAIM));
        w.governance.assign(CLAIM, "s1", null);
        assertEquals(BUYER.account(), w.governance.claims.get(CLAIM).ownerAccount());
        w.engine.taxes.assess(OWNER.account(), "nation:n1", "n1", "incomeTaxBps", 100, CLAIM);
        assertFalse(w.engine.isClaimEncumbered(CLAIM), "Non-property tax debts are account obligations, not territorial liens.");
    }

    @Test void taxJurisdictionSurvivesReloadAndLegacyValuationsAreUpgraded() {
        for (boolean legacy : List.of(false, true)) {
            TestWorld w = new TestWorld();
            rates(w, "propertyTaxBps");
            w.governance.claimOwner(CLAIM, "city:c1");
            w.governance.allocation(CLAIM, "s1", null);
            w.engine.property.value(CLAIM);
            Gson gson = new Gson();
            var snapshot = gson.toJsonTree(w.data).getAsJsonObject();
            if (legacy) snapshot.getAsJsonObject("valuations").getAsJsonObject(CLAIM).remove("taxBasis");
            EconomyData loaded = gson.fromJson(snapshot, EconomyData.class);
            w.advance(1000);
            w.governance.assign(CLAIM, "s1", "c1");
            EconomyEngine restored = new EconomyEngine(loaded, w.governance, w.config, w.values, w.clock,
                    () -> w.dirty++, ValuationEnvironment.NEUTRAL, id -> 64);
            w.governance.economy = restored;
            restored.property.value(CLAIM);
            assertEquals(BigInteger.valueOf(97), restored.taxes.arrears("state:s1"));
            assertEquals(BigInteger.ZERO, restored.taxes.arrears("city:c1"));
            assertEquals("city:c1", loaded.valuations.get(CLAIM).taxOwnerAccount);
            assertEquals("c1", loaded.valuations.get(CLAIM).taxBasis.cityId());
            EconomyIntegrity.validate(loaded);
        }
    }

    private static void allocate(TestWorld w, String key, int depth) {
        w.governance.allocation(key, depth > 0 ? "s1" : null, depth == 2 ? "c1" : null);
    }

    private static void rates(TestWorld w, String kind) {
        for (int tier = 0; tier < GOVERNMENTS.size(); tier++) {
            w.governance.setting(GOVERNMENTS.get(tier), kind, Integer.toString((tier + 1) * 100));
        }
    }

    private static void sameTerritory(GovernanceAccess.ClaimView before, GovernanceAccess.ClaimView after) {
        assertEquals(before.key(), after.key());
        assertEquals(before.nationId(), after.nationId());
        assertEquals(before.stateId(), after.stateId());
        assertEquals(before.cityId(), after.cityId());
        assertEquals(before.improvements(), after.improvements());
        assertEquals(before.claimedAt(), after.claimedAt());
    }
}
