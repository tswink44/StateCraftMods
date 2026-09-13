package dev.statecraft.integration;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceConfig;
import dev.statecraft.domain.GovernanceData;
import dev.statecraft.domain.GovernanceEngine;
import dev.statecraft.economy.EconomyConfig;
import dev.statecraft.economy.EconomyData;
import dev.statecraft.economy.EconomyEngine;
import dev.statecraft.economy.InventoryPort;
import dev.statecraft.economy.ItemLot;
import dev.statecraft.economy.ItemValues;
import dev.statecraft.economy.Ledger;
import dev.statecraft.economy.ValuationEnvironment;
import dev.statecraft.persistence.WorldStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceEconomyIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private static final Actor OWNER = actor("Founder", 0);
    private static final Actor BUYER = actor("Citizen", 0);
    private static final Actor FOREIGNER = actor("Visitor", 4);
    @TempDir Path world;

    @Test
    void actualEconomyChargesGovernanceFeesWithoutPartialGovernmentCreation() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.governanceConfig.nationCreationFee = 1000;
        fixture.governance.configure(fixture.governanceConfig);
        fixture.fund(OWNER.account(), 500);
        assertThrows(UserError.class, () -> fixture.governance.execute(OWNER, "nation create Arcadia"));
        assertTrue(fixture.governance.nationOf(OWNER.id()).isEmpty());
        assertEquals(500, fixture.economy.balance(OWNER.account()));
        assertEquals(0, fixture.economy.balance(fixture.governanceConfig.feeAccount));

        fixture.fund(OWNER.account(), 1500);
        fixture.governance.execute(OWNER, "nation create Arcadia");
        assertTrue(fixture.governance.nationOf(OWNER.id()).isPresent());
        assertEquals(1000, fixture.economy.balance(OWNER.account()));
        assertEquals(1000, fixture.economy.balance(fixture.governanceConfig.feeAccount));
    }

    @Test
    void namespacedContractEscrowDoesNotRelaxOtherAccountValidation() throws IOException {
        Fixture fixture = new Fixture(world);
        String id = UUID.randomUUID().toString();
        String escrow = "escrow:contract:" + id;
        assertEquals(escrow, Ledger.account(escrow));
        assertThrows(UserError.class, () -> fixture.economy.requireAccount(BUYER, escrow));
        for (String invalid : List.of("player:contract:" + id, "nation:contract:" + id,
                "company:contract:" + id, escrow + ":extra", "escrow:contract:not-a-uuid")) {
            assertThrows(UserError.class, () -> Ledger.account(invalid));
        }
    }

    @Test
    void allocationsMovePublicTitlesWithoutMovingTreasuryCashAndPreservePrivateTitles() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.governance.execute(OWNER, "nation create Arcadia");
        fixture.governance.execute(OWNER, "state create Arcadia Westhaven");
        fixture.governance.execute(OWNER, "city create Westhaven Oakvale");
        fixture.governance.execute(OWNER, "nation invite Arcadia Citizen");
        fixture.governance.execute(BUYER, "nation accept Arcadia");
        fixture.governance.execute(OWNER, "chunk claim Arcadia here");
        String key = OWNER.chunkKey();
        String nation = fixture.gov("Arcadia").account();
        String state = fixture.gov("Westhaven").account();
        String city = fixture.gov("Oakvale").account();
        var national = fixture.governance.claim(key).orElseThrow();
        assertNull(national.stateId());
        assertNull(national.cityId());
        assertEquals(nation, national.ownerAccount());
        fixture.fund(nation, 10000);

        fixture.governance.execute(OWNER, "chunk assignstate Arcadia here Westhaven");
        assertEquals(state, fixture.governance.claim(key).orElseThrow().ownerAccount());
        assertEquals(10000, fixture.economy.balance(nation));
        assertEquals(0, fixture.economy.balance(state));
        fixture.economy.property().list(OWNER, key, 5000);
        assertThrows(UserError.class, () -> fixture.governance.execute(OWNER, "chunk assigncity Westhaven here Oakvale"));
        assertEquals(state, fixture.governance.claim(key).orElseThrow().ownerAccount());
        assertNull(fixture.governance.claim(key).orElseThrow().cityId());
        fixture.economy.property().delist(OWNER, key);
        fixture.governance.execute(OWNER, "chunk assigncity Westhaven here Oakvale");
        assertEquals(city, fixture.governance.claim(key).orElseThrow().ownerAccount());

        fixture.governance.transferProperty(key, BUYER.account());
        fixture.governance.execute(OWNER, "chunk assigncity Westhaven here none");
        assertEquals(BUYER.account(), fixture.governance.claim(key).orElseThrow().ownerAccount());
        fixture.governance.execute(OWNER, "chunk assignstate Arcadia here none");
        var privateNational = fixture.governance.claim(key).orElseThrow();
        assertEquals(BUYER.account(), privateNational.ownerAccount());
        assertNull(privateNational.stateId());
        assertNull(privateNational.cityId());
        assertEquals(national.nationId(), privateNational.nationId());
        assertEquals(10000, fixture.economy.balance(nation));
        fixture.store.save();
        var loaded = new Fixture(world).governance.claim(key).orElseThrow();
        assertEquals(BUYER.account(), loaded.ownerAccount());
        assertNull(loaded.stateId());
        assertNull(loaded.cityId());
    }

    @Test
    void treasurySelectionsTrackActualInheritedOfficesAndTheirRevocation() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.hierarchy();
        List<String> treasuries = List.of(fixture.gov("Arcadia").account(),
                fixture.gov("Westhaven").account(), fixture.gov("Oakvale").account());
        for (String account : treasuries) {
            assertThrows(UserError.class, () -> fixture.economy.requireAccount(BUYER, account));
        }
        fixture.governance.execute(OWNER, "nation officer Arcadia Citizen add");
        for (String account : treasuries) {
            assertEquals(account, fixture.economy.requireAccount(BUYER, account));
            assertTrue(fixture.economy.accessibleAccounts(BUYER).contains(account));
        }
        fixture.economy.selectAccount(BUYER, fixture.gov("Oakvale").account());
        fixture.governance.execute(OWNER, "nation officer Arcadia Citizen remove");
        assertThrows(UserError.class, () -> fixture.economy.selectedAccount(BUYER));
        for (String account : treasuries) {
            assertThrows(UserError.class, () -> fixture.economy.requireAccount(BUYER, account));
            assertFalse(fixture.economy.accessibleAccounts(BUYER).contains(account));
        }
    }

    @Test
    void propertySettlementPaysRealCityTreasuryAndPreservesPoliticalOwnership() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.hierarchy();
        fixture.fund(BUYER.account(), 10000);
        fixture.fund(FOREIGNER.account(), 10000);
        var original = fixture.governance.claim(OWNER.chunkKey()).orElseThrow();
        fixture.economy.property().list(OWNER, original.key(), 5000);
        assertTrue(fixture.economy.isClaimEncumbered(original.key()));
        assertThrows(UserError.class, () -> fixture.governance.execute(OWNER, "chunk unclaim Arcadia here"));
        assertThrows(UserError.class, () -> fixture.economy.property()
                .buy(FOREIGNER, original.key(), false, InventoryPort.NONE));
        assertEquals(10000, fixture.economy.balance(FOREIGNER.account()));
        assertEquals(original.ownerAccount(), fixture.governance.claim(original.key()).orElseThrow().ownerAccount());

        fixture.economy.property().buy(BUYER, original.key(), false, InventoryPort.NONE);
        var purchased = fixture.governance.claim(original.key()).orElseThrow();
        assertEquals(BUYER.account(), purchased.ownerAccount());
        assertEquals(original.cityId(), purchased.cityId());
        assertEquals(original.stateId(), purchased.stateId());
        assertEquals(original.nationId(), purchased.nationId());
        assertEquals(5000, fixture.economy.balance(BUYER.account()));
        assertEquals(5000, fixture.economy.balance("city:" + original.cityId()));
        assertFalse(fixture.economy.isClaimEncumbered(original.key()));
        assertThrows(UserError.class, () -> fixture.governance.execute(OWNER, "chunk unclaim Arcadia here"));
    }

    @Test
    void stockEscrowSurvivesBothModelsReloadingAndCannotBeDoubleSpent() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.hierarchy();
        fixture.governance.execute(OWNER, "company create Guild");
        String company = fixture.governance.company("Guild").orElseThrow().id();
        long total = fixture.governance.sharesOf(company, OWNER.id());
        fixture.fund(BUYER.account(), 10000);
        String listing = fixture.economy.commerce().listStock(OWNER, company, 10, 100);
        assertEquals(total - 10, fixture.governance.availableShares(company, OWNER.id()));
        assertThrows(UserError.class, () -> fixture.governance.transferShares(company, OWNER.id(), BUYER.id(), total - 9));
        fixture.economy.commerce().buyStock(BUYER, listing, 3);
        assertEquals(3, fixture.governance.sharesOf(company, BUYER.id()));
        assertEquals(total - 10, fixture.governance.availableShares(company, OWNER.id()));
        assertEquals(300, fixture.economy.balance(OWNER.account()));
        fixture.store.save();

        Fixture restored = new Fixture(world);
        assertEquals(3, restored.governance.sharesOf(company, BUYER.id()));
        assertEquals(total - 10, restored.governance.availableShares(company, OWNER.id()));
        restored.economy.commerce().cancelStock(OWNER, listing);
        assertEquals(total - 3, restored.governance.availableShares(company, OWNER.id()));
        assertEquals(total, restored.governance.company(company).orElseThrow().shares().values()
                .stream().mapToLong(Long::longValue).sum());
        assertEquals(300, restored.economy.balance(OWNER.account()));
        assertEquals(9700, restored.economy.balance(BUYER.account()));
        restored.store.save();
    }

    @Test
    void marketplacePreservesTaggedItemsAndPaysEachActualGovernmentTaxExactlyOnce() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.hierarchy();
        fixture.governance.execute(OWNER, "nation setting Arcadia salesTaxBps 100");
        fixture.governance.execute(OWNER, "state setting Westhaven salesTaxBps 200");
        fixture.governance.execute(OWNER, "city setting Oakvale salesTaxBps 300");
        fixture.governance.execute(OWNER, "nation setting Arcadia incomeTaxBps 500");
        fixture.fund(BUYER.account(), 10000);
        String tag = "{CustomModelData:7}";
        MemoryInventory seller = new MemoryInventory(new ItemLot("minecraft:wheat", tag, 10, 64));
        MemoryInventory buyer = new MemoryInventory(new ItemLot("minecraft:wheat", "", 0, 64));
        String listing = fixture.economy.commerce().listMarket(OWNER, 5, 100, seller);
        var settlement = fixture.economy.commerce().buyMarket(BUYER, listing, 3);
        assertEquals(318, settlement.buyerCost());
        assertEquals(285, settlement.sellerNet());
        assertEquals(18, fixture.economy.balance(fixture.gov("Arcadia").account()));
        assertEquals(6, fixture.economy.balance(fixture.gov("Westhaven").account()));
        assertEquals(9, fixture.economy.balance(fixture.gov("Oakvale").account()));
        assertEquals(3, fixture.economy.commerce().collect(BUYER, buyer));
        assertEquals(tag, buyer.snapshot().get(0).snbt());
        assertEquals(3, buyer.snapshot().get(0).count());
        fixture.economy.commerce().cancelMarket(OWNER, listing);
        assertEquals(2, fixture.economy.commerce().collect(OWNER, seller));
        assertEquals(tag, seller.snapshot().get(0).snbt());
        assertEquals(7, seller.snapshot().get(0).count());
    }

    @Test
    void paidGovernmentContractsUseRealEscrowAndRequireIndependentApproval() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.hierarchy();
        fixture.governance.execute(BUYER, "company create Guild");
        String company = fixture.governance.company("Guild").orElseThrow().account();
        String city = fixture.gov("Oakvale").account();
        fixture.fund(city, 10000);
        String contract = identifier(fixture.governance.execute(OWNER,
                "contract create Oakvale Paving \"Build a road\" here"));
        fixture.governance.execute(BUYER, "contract bid " + contract + " 25 \"Road work\" company:Guild");
        fixture.governance.execute(OWNER, "contract review " + contract);
        fixture.governance.execute(OWNER, "contract award " + contract + " Citizen");
        assertEquals(7500, fixture.economy.balance(city));
        assertEquals(2500, fixture.economy.balance("escrow:contract:" + contract));
        assertEquals(0, fixture.economy.balance(company));
        fixture.governance.execute(BUYER, "contract submit " + contract + " \"Road complete\"");
        assertThrows(UserError.class, () -> fixture.governance.execute(BUYER, "contract complete " + contract));
        fixture.governance.execute(OWNER, "contract complete " + contract);
        assertEquals(0, fixture.economy.balance("escrow:contract:" + contract));
        assertEquals(2500, fixture.economy.balance(company));
    }

    @Test
    void peaceTermsMoveRealTreasuryFundsOnlyWhenTheOtherNationAccepts() throws IOException {
        Fixture fixture = new Fixture(world);
        fixture.governanceConfig.requirePeaceRatification = false;
        fixture.governance.configure(fixture.governanceConfig);
        fixture.hierarchy();
        fixture.governance.execute(FOREIGNER, "nation create Borealis");
        String from = fixture.gov("Arcadia").account();
        String to = fixture.gov("Borealis").account();
        fixture.fund(from, 5000);
        fixture.governance.execute(OWNER, "diplomacy war Arcadia Borealis \"Border dispute\"");
        String proposal = identifier(fixture.governance.execute(OWNER,
                "diplomacy peace Arcadia Borealis 20 0 - \"Peace offer\""));
        assertEquals(5000, fixture.economy.balance(from));
        assertEquals(0, fixture.economy.balance(to));
        fixture.governance.execute(FOREIGNER, "diplomacy accept " + proposal);
        assertEquals(3000, fixture.economy.balance(from));
        assertEquals(2000, fixture.economy.balance(to));
    }

    private static Actor actor(String name, int x) {
        return new Actor(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name, false, "minecraft:overworld", x, 0);
    }

    private static String identifier(String output) {
        var match = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}").matcher(output);
        assertTrue(match.find(), "Command should expose its created identifier: " + output);
        return match.group();
    }

    private static final class Fixture {
        final WorldStore store;
        final GovernanceConfig governanceConfig = new GovernanceConfig();
        final GovernanceEngine governance;
        final EconomyEngine economy;

        Fixture(Path world) throws IOException {
            store = new WorldStore(world);
            var governments = store.load("governance", GovernanceData.class, GovernanceData::new);
            var finances = store.load("economy", EconomyData.class, EconomyData::new);
            governance = new GovernanceEngine(governments, governanceConfig, EconomyAccess.UNAVAILABLE, CLOCK::millis);
            EconomyConfig config = new EconomyConfig();
            config.marketCommissionBps = 0;
            ItemValues values = new ItemValues(Map.of("minecraft:wheat", 100L),
                    Map.of("statecraft_economy:currency_1", 100L),
                    Set.of("minecraft:wheat", "statecraft_economy:currency_1")::contains);
            economy = new EconomyEngine(finances, governance, config, values, CLOCK, () -> {},
                    ValuationEnvironment.NEUTRAL, ignored -> 64);
            governance.setEconomy(economy);
            for (Actor actor : List.of(OWNER, BUYER, FOREIGNER)) {
                governance.login(actor);
                economy.ensurePlayer(actor);
            }
        }

        void hierarchy() {
            governance.execute(OWNER, "nation create Arcadia");
            governance.execute(OWNER, "state create Arcadia Westhaven");
            governance.execute(OWNER, "city create Westhaven Oakvale");
            governance.execute(OWNER, "chunk claim Arcadia here");
            governance.execute(OWNER, "chunk assignstate Arcadia here Westhaven");
            governance.execute(OWNER, "chunk assigncity Westhaven here Oakvale");
            governance.execute(OWNER, "nation invite Arcadia Citizen");
            governance.execute(BUYER, "nation accept Arcadia");
        }

        GovernmentView gov(String name) { return governance.government(name).orElseThrow(); }

        void fund(String account, long cents) {
            economy.ledger().prepare(List.of(),
                    List.of(new Ledger.Adjustment(account, cents, true, "system:test", "Integration test funding")),
                    false, Map.of()).commit();
        }
    }

    private static final class MemoryInventory implements InventoryPort {
        private List<ItemLot> slots;
        MemoryInventory(ItemLot... items) { slots = List.of(items); }
        @Override public List<ItemLot> snapshot() { return List.copyOf(slots); }
        @Override public int selectedSlot() { return 0; }
        @Override public boolean near(Utility utility) { return false; }
        @Override public void replace(List<ItemLot> expected, List<ItemLot> replacement) {
            if (!slots.equals(expected)) {
                throw new UserError("Inventory changed during the integration test.");
            }
            slots = new ArrayList<>(replacement);
        }
    }
}
