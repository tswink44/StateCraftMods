package dev.statecraft.economy;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.form.FormState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.statecraft.economy.TestWorld.*;
import static org.junit.jupiter.api.Assertions.*;

final class EconomyFormsTest {
    @Test void propertyChoicesNameOnlyAssignedTiersAndKeepCanonicalLocations() {
        TestWorld world = new TestWorld();
        EconomyDisplay display = new EconomyDisplay(world.engine);
        for (int depth = 0; depth < 3; depth++) {
            world.governance.allocation(CLAIM, depth > 0 ? "s1" : null, depth == 2 ? "c1" : null);
            String title = List.of("Unassigned region (Nation One)", "Unassigned city (State One / Nation One)",
                    "[City One] (State One / Nation One)").get(depth);
            assertEquals(title, display.claimTitle(world.governance.claims.get(CLAIM)));
            FormBuilder choices = form(world, OWNER, "property", "property list <chunkKeyOrHere> <price>", Map.of(), FormQuery.INITIAL);
            FormChoice selected = choices.choices("chunkKeyOrHere").stream().filter(c -> c.value().equals(CLAIM)).findFirst().orElseThrow();
            assertTrue(selected.label().contains(title));
            assertTrue(selected.label().contains("Chunk 0, 0 (Overworld)"));
            assertEquals(CLAIM, choices.value("chunkKeyOrHere"));
            assertFalse(selected.label().contains("null"));
        }
        assertEquals("Unclaimed land", display.claimTitle(null));
    }

    @Test void sourceAccountsAreFilteredAndRevokedSelectionsAreCleared() {
        TestWorld world = new TestWorld();
        world.set("nation:n1", 1000);
        world.governance.grant(BUYER, "nation:n1");
        world.engine.selectAccount(BUYER, "nation:n1");
        String command = "transfer <fromAccount> <toAccount> <amount>";
        FormBuilder initial = form(world, BUYER, "atm", command, Map.of(), FormQuery.INITIAL);
        assertEquals("nation:n1", initial.value("fromAccount"));
        assertTrue(values(initial, "fromAccount").contains(BUYER.account()));
        assertFalse(values(initial, "fromAccount").contains("company:co"));
        world.governance.granted.clear();
        FormBuilder revoked = form(world, BUYER, "atm", command, Map.of("fromAccount", "nation:n1"), FormQuery.INITIAL);
        assertEquals("", revoked.value("fromAccount"));
        assertFalse(values(revoked, "fromAccount").contains("nation:n1"));
        FormBuilder refreshed = form(world, BUYER, "atm", command, Map.of(), FormQuery.INITIAL);
        assertEquals(BUYER.account(), refreshed.value("fromAccount"));
        assertEquals("nation:n1", world.data.selectedAccounts.get(BUYER.id().toString()), "Metadata must not rewrite the stored account.");
    }

    @Test void commonCatalogCannotSmuggleTreasuryAccessOrPrivateBalances() {
        TestWorld world = new TestWorld();
        world.set(OWNER, 987_654_321);
        String command = "transfer <fromAccount> <toAccount> <amount>";
        FormContext context = context(BUYER, "atm", command, Map.of(), FormQuery.INITIAL);
        FormBuilder builder = new FormBuilder(context);
        builder.choice("fromAccount", "Source", "", List.of(new FormChoice("nation:n1", "Unauthorized treasury")), "nation:n1", List.of(), false);
        builder.choice("toAccount", "Target", "", List.of(
                new FormChoice("system:statecraft-fees", "Private internal account", "Secret balance"),
                new FormChoice("escrow:contract:" + new UUID(0, 700), "Private escrow", "Secret balance"),
                new FormChoice(OWNER.account(), "Owner's private balance", "$9,876,543.21"),
                new FormChoice(new UUID(0, 555).toString(), "Remembered", "Private details")), "", List.of(), false);
        new EconomyForms(world.engine).describe(context, builder);
        assertEquals(Set.of(BUYER.account()), values(builder, "fromAccount"));
        assertTrue(values(builder, "toAccount").contains("player:" + new UUID(0, 555)));
        assertTrue(values(builder, "toAccount").contains("nation:n1"));
        assertTrue(values(builder, "toAccount").contains("company:co"));
        assertTrue(values(builder, "toAccount").stream().noneMatch(v -> v.startsWith("escrow:") || v.startsWith("system:") || v.startsWith("bank:")));
        assertFalse(builder.build().toString().contains("$9,876,543.21"));
        assertFalse(builder.build().toString().contains("Secret balance"));
        assertFalse(builder.build().toString().contains("Private details"));
    }

    @Test void shareholderCompanyChoicesDoNotRequireManagerAuthority() {
        TestWorld world = new TestWorld();
        FormBuilder shares = form(world, BUYER, "stock", "stock list <company> <shares> <unitPrice>", Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of("co"), values(shares, "company"));
        assertEquals("co", shares.value("company"));
        assertEquals("1", shares.value("shares"));
        assertEquals("", shares.value("unitPrice"));
        FormBuilder vault = form(world, BUYER, "company", "company dividend <company> <budget>", Map.of(), FormQuery.INITIAL);
        assertTrue(values(vault, "company").isEmpty());
        world.engine.commerce.listStock(BUYER, "co", 30, 1);
        assertTrue(values(form(world, BUYER, "stock", "stock list <company> <shares> <unitPrice>", Map.of(), FormQuery.INITIAL), "company").isEmpty());
    }

    @Test void bankCreationRequiresAnUnregisteredFundedManagedCompany() {
        TestWorld world = new TestWorld(c -> { c.minimumBankReserveCents = 100; c.bankCreationFeeCents = 50; });
        String command = "bank create <company> <name> <seedCapital> <depositBps> <loanBps>";
        world.set("company:co", 149);
        assertTrue(values(form(world, OWNER, "bank", command, Map.of(), FormQuery.INITIAL), "company").isEmpty());
        world.set("company:co", 150);
        FormBuilder eligible = form(world, OWNER, "bank", command, Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of("co"), values(eligible, "company"));
        assertEquals("", eligible.value("seedCapital"));
        assertEquals("0", eligible.value("depositBps"));
        world.engine.banking.create(OWNER, "co", "Bank", 100, 0, 0);
        assertTrue(values(form(world, OWNER, "bank", command, Map.of(), FormQuery.INITIAL), "company").isEmpty());
        world.engine.banking.close(OWNER, "co");
        world.set("company:co", 1000);
        assertTrue(values(form(world, OWNER, "bank", command, Map.of(), FormQuery.INITIAL), "company").isEmpty(), "A retired bank identity cannot be recreated.");
    }

    @Test void companyCashFieldsNeverFallBackToPersonalAccounts() {
        TestWorld world = new TestWorld();
        String command = "cash deposit <companyAccount>";
        assertEquals(Set.of("company:co"), values(form(world, OWNER, "company", command, Map.of(), FormQuery.INITIAL), "companyAccount"));
        FormBuilder member = form(world, BUYER, "company", command, Map.of(), FormQuery.INITIAL);
        assertEquals("", member.value("companyAccount"));
        assertTrue(values(member, "companyAccount").isEmpty());
    }

    @Test void retiredAccountDefaultsFallBackToTheAuthorizedWallet() {
        TestWorld world = new TestWorld();
        world.bank(1000, 0, 0);
        world.engine.selectAccount(OWNER, "bank:co");
        world.engine.banking.close(OWNER, "co");
        FormBuilder account = form(world, OWNER, "atm", "cash withdraw <amount> <account>", Map.of(), FormQuery.INITIAL);
        assertEquals(OWNER.account(), account.value("account"));
        assertFalse(values(account, "account").contains("bank:co"));
        assertEquals("bank:co", world.data.selectedAccounts.get(OWNER.id().toString()));
    }

    @Test void marketSelectorsRespectOwnershipExpiryAndAction() {
        TestWorld world = new TestWorld();
        EconomyData.MarketListing own = market(world, 1, BUYER, 5, 1000);
        EconomyData.MarketListing live = market(world, 2, OWNER, 8, 1000);
        EconomyData.MarketListing expired = market(world, 3, BUYER, 3, -1);
        market(world, 4, OWNER, 0, 1000);
        FormBuilder buying = form(world, BUYER, "market", "market buy <listingId> <quantityOrAll>", Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of(live.id), values(buying, "listingId"));
        assertEquals("1", buying.value("quantityOrAll"));
        assertEquals(Set.of("1", "all", "8"), values(buying, "quantityOrAll"));
        assertTrue(field(buying, "quantityOrAll").allowCustom());
        assertEquals(List.of("listingId"), field(buying, "quantityOrAll").dependencies());
        assertEquals(Set.of(own.id, expired.id), values(form(world, BUYER, "market", "market cancel <listingId>", Map.of(), FormQuery.INITIAL), "listingId"));
        assertEquals(Set.of(own.id, live.id), values(form(world, BUYER, "market", "market inspect <listingId>", Map.of(), FormQuery.INITIAL), "listingId"));
        assertTrue(field(buying, "listingId").choices().get(0).detail().contains("$1.50"));
        assertTrue(field(buying, "listingId").choices().get(0).label().contains("Wheat"));
    }

    @Test void stockSelectorsRespectReservationsAndSelfPurchaseRules() {
        TestWorld world = new TestWorld();
        String own = world.engine.commerce.listStock(BUYER, "co", 5, 20);
        String live = world.engine.commerce.listStock(OWNER, "co", 10, 25);
        String expired = world.engine.commerce.listStock(OWNER, "co", 5, 25);
        world.data.stocks.get(expired).expiresAt = world.clock.millis() - 1;
        FormBuilder buy = form(world, BUYER, "stock", "stock buy <listingId> <sharesOrAll>", Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of(live), values(buy, "listingId"));
        assertEquals("1", buy.value("sharesOrAll"));
        assertEquals(Set.of(own), values(form(world, BUYER, "stock", "stock cancel <listingId>", Map.of(), FormQuery.INITIAL), "listingId"));
        assertEquals(Set.of(live, expired), values(form(world, OWNER, "stock", "stock cancel <listingId>", Map.of(), FormQuery.INITIAL), "listingId"));
        assertEquals(3, values(form(world, ADMIN, "stock", "stock cancel <listingId>", Map.of(), FormQuery.INITIAL), "listingId").size());
    }

    @Test void propertyChoicesUseActualOwnershipAndEligibleActiveListings() {
        TestWorld world = new TestWorld();
        world.engine.property.list(OWNER, CLAIM, 5000);
        FormBuilder buy = form(world, BUYER, "property", "property buy <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of(CLAIM), values(buy, "chunkKeyOrHere"));
        assertEquals(CLAIM, buy.value("chunkKeyOrHere"));
        assertTrue(values(form(world, OWNER, "property", "property buy <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL), "chunkKeyOrHere").isEmpty());
        assertTrue(values(form(world, BUYER, "property", "property delist <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL), "chunkKeyOrHere").isEmpty());
        assertFalse(values(form(world, OWNER, "property", "property list <chunkKeyOrHere> <price>", Map.of(), FormQuery.INITIAL), "chunkKeyOrHere").contains(CLAIM));
        world.governance.ineligibleBuyers.add(BUYER.id());
        assertTrue(values(form(world, BUYER, "property", "property buy <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL), "chunkKeyOrHere").isEmpty());
        world.governance.ineligibleBuyers.clear();
        world.data.properties.get(CLAIM).expiresAt = world.clock.millis() - 1;
        assertTrue(values(form(world, BUYER, "property", "property buy <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL), "chunkKeyOrHere").isEmpty());
        assertEquals(Set.of(CLAIM), values(form(world, OWNER, "property", "property delist <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL), "chunkKeyOrHere"));
    }

    @Test void propertyBankFundingDependsOnAValidSelectionAndOwnDeposit() {
        TestWorld world = new TestWorld();
        world.bank(1000, 0, 0);
        world.set(BUYER, 100);
        world.engine.banking.deposit(BUYER, "co", 100);
        world.engine.property.list(OWNER, CLAIM, 5000);
        String command = "property buy <chunkKeyOrHere> bank <bank> cash";
        FormBuilder valid = form(world, BUYER, "property", command, Map.of(), FormQuery.INITIAL);
        assertEquals("co", valid.value("bank"));
        assertEquals(List.of("chunkKeyOrHere"), field(valid, "bank").dependencies());
        assertTrue(field(valid, "bank").choices().get(0).detail().contains("your deposit $1.00"));
        FormBuilder stale = form(world, BUYER, "property", command,
                Map.of("chunkKeyOrHere", "minecraft:overworld|999|999", "bank", "co"), FormQuery.INITIAL);
        assertTrue(values(stale, "bank").isEmpty());
        assertEquals("", stale.value("bank"));
    }

    @Test void bankSelectorsSeparatePublicManagedAndCustomerAccounts() {
        TestWorld world = new TestWorld();
        world.bank(1000, 100, 200);
        world.engine.banking.associate(BUYER, "co");
        assertEquals("co", form(world, BUYER, "bank", "bank deposit <amount> <bank>", Map.of(), FormQuery.INITIAL).value("bank"));
        assertTrue(values(form(world, BUYER, "bank", "bank withdraw <amount> <bank>", Map.of(), FormQuery.INITIAL), "bank").isEmpty());
        assertTrue(values(form(world, BUYER, "bank", "bank report <bank>", Map.of(), FormQuery.INITIAL), "bank").isEmpty());
        world.set(BUYER, 500);
        world.engine.banking.deposit(BUYER, "co", 500);
        assertEquals("co", form(world, BUYER, "bank", "bank withdraw <amount> <bank>", Map.of(), FormQuery.INITIAL).value("bank"));
        world.governance.grant(BUYER, "company:co");
        FormBuilder managed = form(world, BUYER, "bank", "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>", Map.of(), FormQuery.INITIAL);
        assertEquals("co", managed.value("bank"));
        assertEquals("100", managed.value("depositBps"));
        assertEquals("200", managed.value("loanBps"));
        assertEquals("", managed.value("depositFee"));
        world.governance.granted.clear();
        assertEquals("", form(world, BUYER, "bank", "bank report <bank>", Map.of("bank", "co"), FormQuery.INITIAL).value("bank"));
    }

    @Test void loanChoicesNeverExposeOtherCustomersPrivateApplications() {
        TestWorld world = new TestWorld();
        world.bank(1000, 0, 0);
        EconomyData.Loan own = loan(world, 1, BUYER, "REQUESTED", 1000);
        EconomyData.Loan other = loan(world, 2, FOREIGN, "REQUESTED", 1000);
        FormBuilder personal = form(world, BUYER, "bank", "loan show <loanId>", Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of(own.id), values(personal, "loanId"));
        assertFalse(personal.build().toString().contains(other.id));
        FormBuilder search = form(world, BUYER, "bank", "loan show <loanId>", Map.of("loanId", other.id),
                new FormQuery("loanId", "Foreign", 0));
        assertEquals("", search.value("loanId"));
        assertTrue(field(search, "loanId").choices().isEmpty());
        assertEquals(Set.of(own.id, other.id), values(form(world, OWNER, "bank", "loan show <loanId>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertEquals(Set.of(own.id, other.id), values(form(world, ADMIN, "bank", "loan show <loanId>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertTrue(values(form(world, BUYER, "bank", "loan approve <loanId>", Map.of(), FormQuery.INITIAL), "loanId").isEmpty());
    }

    @Test void loanActionsUseTheirOwnStatusAndPermissionRules() {
        TestWorld world = new TestWorld();
        world.bank(1000, 0, 0);
        EconomyData.Loan requested = loan(world, 1, BUYER, "REQUESTED", 1000);
        EconomyData.Loan expired = loan(world, 2, BUYER, "REQUESTED", -1);
        EconomyData.Loan active = loan(world, 3, BUYER, "ACTIVE", 1000);
        EconomyData.Loan defaulted = loan(world, 4, BUYER, "DEFAULTED", 1000);
        loan(world, 5, BUYER, "REPAID", 1000);
        EconomyData.Loan foreignActive = loan(world, 6, FOREIGN, "ACTIVE", 1000);
        assertEquals(Set.of(requested.id), values(form(world, OWNER, "bank", "loan approve <loanId>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertEquals(Set.of(active.id, defaulted.id), values(form(world, BUYER, "bank", "loan repay <loanId> <amountOrAll>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertTrue(values(form(world, OWNER, "bank", "loan repay <loanId> <amountOrAll>", Map.of(), FormQuery.INITIAL), "loanId").isEmpty(), "Managers cannot spend another borrower's wallet.");
        assertEquals(Set.of(active.id, defaulted.id, foreignActive.id), values(form(world, ADMIN, "bank", "loan repay <loanId> <amountOrAll>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertEquals(Set.of(requested.id, expired.id), values(form(world, BUYER, "bank", "loan cancel <loanId>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertEquals(Set.of(defaulted.id), values(form(world, OWNER, "bank", "loan recover <loanId>", Map.of(), FormQuery.INITIAL), "loanId"));
        assertTrue(values(form(world, ADMIN, "bank", "loan autopay <loanId> <enabled>", Map.of(), FormQuery.INITIAL), "loanId").isEmpty());
    }

    @Test void collateralAndRecoveryModesAreDependentAndSafelyDefaulted() {
        TestWorld world = new TestWorld();
        world.bank(1000, 0, 0);
        world.engine.banking.associate(BUYER, "co");
        String command = loanRequest();
        FormBuilder unsecured = form(world, BUYER, "bank", command, Map.of(), FormQuery.INITIAL);
        assertEquals("none", unsecured.value("collateralOrNone"));
        assertEquals("none", unsecured.value("noneBalanceCollateralOrBoth"));
        assertEquals("manual", unsecured.value("autoOrManual"));
        assertEquals(Set.of("none", "balance"), values(unsecured, "noneBalanceCollateralOrBoth"));
        assertEquals(Set.of("none", BUYER_CLAIM), values(unsecured, "collateralOrNone"));
        assertEquals(List.of("bank", "principal"), field(unsecured, "collateralOrNone").dependencies());
        FormBuilder secured = form(world, BUYER, "bank", command, Map.of("collateralOrNone", BUYER_CLAIM), FormQuery.INITIAL);
        assertEquals(Set.of("none", "collateral", "both"), values(secured, "noneBalanceCollateralOrBoth"));
        assertEquals("none", secured.value("noneBalanceCollateralOrBoth"), "Merely viewing a claim must not grant repossession consent.");
        FormBuilder cleared = form(world, BUYER, "bank", command,
                Map.of("collateralOrNone", "none", "noneBalanceCollateralOrBoth", "both"), FormQuery.INITIAL);
        assertEquals("", cleared.value("noneBalanceCollateralOrBoth"));
        FormState state = new FormState();
        state.apply(secured.build(), Map.of());
        Set<String> reset = state.change("principal", "25.00");
        assertTrue(reset.containsAll(Set.of("collateralOrNone", "noneBalanceCollateralOrBoth", "autoOrManual")));
    }

    @Test void unsupportedUnsecuredLoansAndPledgedClaimsAreNotOffered() {
        TestWorld world = new TestWorld(c -> c.allowUnsecuredLoans = false);
        world.bank(1000, 0, 0);
        world.engine.banking.associate(BUYER, "co");
        world.config.allowConsentedBalanceSeizure = false;
        FormBuilder initial = form(world, BUYER, "bank", loanRequest(), Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of(BUYER_CLAIM), values(initial, "collateralOrNone"));
        assertEquals("none", initial.value("noneBalanceCollateralOrBoth"));
        assertEquals(Set.of("none", "collateral"), values(initial, "noneBalanceCollateralOrBoth"));
        EconomyData.Loan pledge = loan(world, 7, BUYER, "ACTIVE", 1000);
        pledge.collateral = BUYER_CLAIM;
        pledge.consentRepossession = true;
        assertTrue(values(form(world, BUYER, "bank", loanRequest(), Map.of(), FormQuery.INITIAL), "collateralOrNone").isEmpty());
    }

    @Test void collateralUsesOnlyFreshCachedValuationsWithoutRecalculation() {
        TestWorld world = new TestWorld(c -> { c.allowUnsecuredLoans = false; c.maximumLoanToValueBps = 5000; });
        world.bank(1000, 0, 0);
        world.engine.banking.associate(BUYER, "co");
        EconomyData.Valuation cached = new EconomyData.Valuation();
        cached.chunk = BUYER_CLAIM;
        cached.value = 1000;
        cached.nextRecalculationAt = world.clock.millis() + 1000;
        world.data.valuations.put(BUYER_CLAIM, cached);
        assertTrue(values(form(world, BUYER, "bank", loanRequest(), Map.of("principal", "10.00"), FormQuery.INITIAL), "collateralOrNone").isEmpty());
        cached.nextRecalculationAt = world.clock.millis() - 1;
        assertEquals(Set.of(BUYER_CLAIM), values(form(world, BUYER, "bank", loanRequest(), Map.of("principal", "10.00"), FormQuery.INITIAL), "collateralOrNone"));
        assertEquals(1000, cached.value);
        assertTrue(values(form(world, BUYER, "bank", loanRequest(), Map.of("principal", "not money"), FormQuery.INITIAL), "collateralOrNone").isEmpty());
    }

    @Test void editableFieldsAndMoneyDefaultsKeepTheirIntendedTypes() {
        TestWorld world = new TestWorld();
        FormBuilder search = form(world, BUYER, "hub", "hub prices <itemSearch>", Map.of("itemSearch", "unpriced_custom_search"), FormQuery.INITIAL);
        assertEquals(FormField.Kind.CHOICE, field(search, "itemSearch").kind());
        assertTrue(field(search, "itemSearch").allowCustom());
        assertEquals("unpriced_custom_search", search.value("itemSearch"));
        assertTrue(values(search, "itemSearch").contains("minecraft:wheat"));
        assertTrue(field(search, "itemSearch").choices().stream().anyMatch(c -> c.detail().contains("$0.25")));
        FormBuilder suggestion = form(world, BUYER, "hub", "hub suggest <unitPrice> <reason>", Map.of(), FormQuery.INITIAL);
        assertEquals(FormField.Kind.TEXT, field(suggestion, "unitPrice").kind());
        assertEquals("", suggestion.value("unitPrice"));
        assertEquals(FormField.Kind.MULTILINE, field(suggestion, "reason").kind());
        FormBuilder tax = form(world, BUYER, "tax", "tax pay <amountOrAll> <account>", Map.of(), FormQuery.INITIAL);
        assertEquals("", tax.value("amountOrAll"));
        assertEquals(Set.of("all", "0.01"), values(tax, "amountOrAll"));
        assertTrue(field(tax, "amountOrAll").allowCustom());
        FormBuilder marketSearch = form(world, BUYER, "market", "page <page> market search <search>", Map.of("search", "custom text"), FormQuery.INITIAL);
        assertEquals("1", marketSearch.value("page"));
        assertTrue(field(marketSearch, "search").allowCustom());
        assertEquals("custom text", marketSearch.value("search"));
    }

    @Test void callerFilteringPrecedesSearchAndPagingAndValidOffPageSelectionsSurvive() {
        TestWorld world = new TestWorld();
        for (int i = 0; i < 25; i++) market(world, 100 + i, BUYER, 1, 1000);
        for (int i = 0; i < 25; i++) market(world, 200 + i, OWNER, 1, 1000);
        String offPage = id(224);
        FormBuilder first = form(world, BUYER, "market", "market buy <listingId> <quantityOrAll>",
                Map.of("listingId", offPage), FormQuery.INITIAL);
        assertEquals(25, first.choices("listingId").size());
        assertEquals(FormSchema.PAGE_SIZE, field(first, "listingId").choices().size());
        assertTrue(field(first, "listingId").more());
        assertEquals(offPage, first.value("listingId"));
        FormBuilder later = form(world, BUYER, "market", "market buy <listingId> <quantityOrAll>",
                Map.of("listingId", offPage), new FormQuery("listingId", "", 20));
        assertEquals(5, field(later, "listingId").choices().size());
        assertEquals(20, field(later, "listingId").offset());
        assertFalse(field(later, "listingId").more());
        FormBuilder forbidden = form(world, BUYER, "market", "market buy <listingId> <quantityOrAll>",
                Map.of("listingId", id(100)), new FormQuery("listingId", id(100), 0));
        assertEquals("", forbidden.value("listingId"));
        assertTrue(field(forbidden, "listingId").choices().isEmpty());
    }

    @Test void describingFormsDoesNotMutateModelsAccrueInterestOrInitializePlayers() {
        TestWorld world = new TestWorld(c -> c.initialPlayerBalanceCents = 500);
        world.bank(1000, 100, 100);
        world.engine.banking.associate(BUYER, "co");
        loan(world, 1, BUYER, "ACTIVE", 1000);
        world.engine.property.list(OWNER, CLAIM, 5000);
        market(world, 2, OWNER, 5, 100_000);
        world.advance(10_000);
        Gson gson = new Gson();
        String before = gson.toJson(world.data);
        int dirty = world.dirty;
        int messages = world.governance.messages.size();
        Actor newcomer = actor(900, "Newcomer", false, 0);
        form(world, newcomer, "atm", "cash withdraw <amount> <account>", Map.of(), FormQuery.INITIAL);
        form(world, BUYER, "bank", "loan show <loanId>", Map.of(), FormQuery.INITIAL);
        form(world, BUYER, "bank", loanRequest(), Map.of(), FormQuery.INITIAL);
        form(world, OWNER, "property", "property value <chunkKeyOrHere>", Map.of(), FormQuery.INITIAL);
        form(world, OWNER, "market", "market cancel <listingId>", Map.of(), FormQuery.INITIAL);
        form(world, OWNER, "bank", "bank report <bank>", Map.of(), FormQuery.INITIAL);
        assertEquals(before, gson.toJson(world.data));
        assertEquals(dirty, world.dirty);
        assertEquals(messages, world.governance.messages.size());
        assertTrue(world.data.valuations.isEmpty());
        assertFalse(world.data.playerNames.containsKey(newcomer.id().toString()));
    }

    @Test void choiceLabelsAndDetailsRespectProtocolBounds() {
        TestWorld world = new TestWorld();
        world.bank(1000, 0, 0);
        world.data.banks.get("co").name = "Long bank name ".repeat(20);
        market(world, 1, OWNER, 5, 1000).item = new ItemLot("example:" + "a".repeat(180), "{tag:1}", 5, 64);
        FormBuilder bank = form(world, BUYER, "bank", "bank associate <bank>", Map.of(), FormQuery.INITIAL);
        FormBuilder market = form(world, BUYER, "market", "market buy <listingId> <quantityOrAll>", Map.of(), FormQuery.INITIAL);
        for (FormBuilder builder : List.of(bank, market)) {
            for (FormField field : builder.build().fields()) {
                for (FormChoice choice : field.choices()) {
                    assertTrue(choice.label().length() <= 128);
                    assertTrue(choice.detail().length() <= 256);
                    assertTrue(choice.value().length() <= 256);
                }
            }
        }
    }

    private static FormBuilder form(TestWorld world, Actor actor, String page, String command, Map<String, String> values, FormQuery query) {
        FormContext context = context(actor, page, command, values, query);
        FormBuilder builder = new FormBuilder(context);
        new EconomyForms(world.engine).describe(context, builder);
        return builder;
    }

    private static FormContext context(Actor actor, String page, String command, Map<String, String> values, FormQuery query) {
        return new FormContext(actor, "economy:" + page, command, values, query);
    }

    private static FormField field(FormBuilder builder, String key) {
        return builder.build().field(key).orElseThrow();
    }

    private static Set<String> values(FormBuilder builder, String key) {
        return builder.choices(key).stream().map(FormChoice::value).collect(Collectors.toSet());
    }

    private static String loanRequest() {
        return "loan request <bank> <principal> <periods> <collateralOrNone> <noneBalanceCollateralOrBoth> <autoOrManual>";
    }

    private static String id(int index) { return new UUID(0, 10_000 + index).toString(); }

    private static EconomyData.MarketListing market(TestWorld world, int index, Actor seller, int quantity, long lifetime) {
        EconomyData.MarketListing listing = new EconomyData.MarketListing();
        listing.id = id(index);
        listing.seller = seller.id().toString();
        listing.sellerAccount = seller.account();
        listing.sourceChunk = seller.chunkKey();
        listing.sourceNation = world.governance.nationOf(seller.id()).orElse(null);
        listing.item = new ItemLot("minecraft:wheat", "{custom:1}", Math.max(1, quantity), 64);
        listing.remaining = quantity;
        listing.unitPrice = 150;
        listing.createdAt = world.clock.millis();
        listing.expiresAt = world.clock.millis() + lifetime;
        world.data.market.put(listing.id, listing);
        return listing;
    }

    private static EconomyData.Loan loan(TestWorld world, int index, Actor borrower, String status, long lifetime) {
        EconomyData.Loan loan = new EconomyData.Loan();
        loan.id = id(index);
        loan.bank = "co";
        loan.borrower = borrower.id().toString();
        loan.status = status;
        loan.originalPrincipal = 500;
        loan.principal = status.equals("REPAID") ? 0 : 500;
        loan.interest = status.equals("REPAID") ? 0 : 7;
        loan.interestAccrued = loan.interest;
        loan.interestCap = 500;
        loan.rateBps = 100;
        loan.periodMillis = 1000;
        loan.periods = 5;
        loan.issuedAt = world.clock.millis() - 5000;
        loan.lastAccruedAt = world.clock.millis() - 1000;
        loan.requestedAt = world.clock.millis();
        loan.applicationExpiresAt = world.clock.millis() + lifetime;
        loan.terms = "Private loan terms for " + borrower.name();
        world.data.loans.put(loan.id, loan);
        return loan;
    }

    public static void main(String[] args) throws Throwable {
        int count = 0;
        for (var method : EconomyFormsTest.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Test.class)) continue;
            try { method.invoke(new EconomyFormsTest()); count++; }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
        System.out.println(count + " economy form tests passed.");
    }
}
