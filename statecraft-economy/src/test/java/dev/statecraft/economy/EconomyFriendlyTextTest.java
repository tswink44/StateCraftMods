package dev.statecraft.economy;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.statecraft.economy.TestWorld.*;
import static org.junit.jupiter.api.Assertions.*;

final class EconomyFriendlyTextTest {
    private static final String COMPANY = new UUID(0, 1001).toString();
    private static final String NATION = new UUID(0, 1002).toString();
    private static final Pattern UUID_TEXT = Pattern.compile("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final String TRANSFER = "transfer <fromAccount> <toAccount> <amount>";
    private static final String REQUEST = "loan request <bank> <principal> <periods> <collateralOrNone> <noneBalanceCollateralOrBoth> <autoOrManual>";

    @Test void ordinaryViewsUseNamesAndRolesButRetainTypedIdsAndActionSeeds() {
        TestWorld w = world();
        bank(w);
        w.engine.banking.associate(BUYER, COMPANY);
        String loan = w.engine.banking.requestLoan(BUYER, COMPANY, 1000, 4, null, false, false, false);
        String market = w.engine.commerce.listMarket(OWNER, 4, 100, new Inventory().item(0, "minecraft:wheat", 4));
        String deliverySale = w.engine.commerce.listMarket(BUYER, 2, 50, new Inventory().item(0, "minecraft:diamond", 2));
        w.engine.commerce.buyMarket(OWNER, deliverySale, 1);
        w.engine.commerce.listStock(OWNER, COMPANY, 2, 100);
        w.engine.property.list(OWNER, CLAIM, 1000);
        w.engine.taxes.assess(OWNER.account(), "nation:" + NATION, NATION, "propertyTaxBps", 12345, CLAIM);
        w.engine.taxes.assess("company:" + COMPANY, "system:fees", null, "companyFee", 77, COMPANY);

        String before = snapshot(w);
        for (Actor actor : List.of(OWNER, BUYER, ADMIN)) {
            for (String page : List.of("atm", "bank", "loans", "market", "deliveries", "property", "company", "stock", "tax", "dashboard", "hub")) {
                UiView view = page(w, actor, page);
                friendly(visible(view));
                for (var row : view.rows()) {
                    if (row.entity().present()) friendly(visible(detail(w, actor, row.entity().kind(), row.entity().id())));
                }
            }
        }
        assertEquals(before, snapshot(w));
        UiView account = detail(w, OWNER, EntityRef.Kind.ACCOUNT, OWNER.account());
        assertEquals("Alice - Personal account", account.title().fallback());
        assertEquals(OWNER.account(), account.actions().get(0).values().get("account"));
        assertTrue(account.actions().get(0).selection().rendered().contains(OWNER.id().toString()));
        assertTrue(page(w, OWNER, "atm").rows().stream().anyMatch(row ->
                row.title().fallback().equals("Arcadia - Nation treasury") && row.entity().id().equals("nation:" + NATION)));
        assertEquals("Example Company", detail(w, OWNER, EntityRef.Kind.COMPANY, COMPANY).title().fallback());
        assertEquals("Harbor Bank", detail(w, OWNER, EntityRef.Kind.BANK, COMPANY).title().fallback());
        assertTrue(page(w, OWNER, "loans").rows().stream().anyMatch(row -> row.entity().id().equals(loan)));
        assertEquals(market, detail(w, BUYER, EntityRef.Kind.MARKET_LISTING, market).actions().get(0).values().get("listingId"));
        assertTrue(detail(w, OWNER, EntityRef.Kind.CLAIM, CLAIM).body().fallback().contains("Chunk 0, 0 (Overworld)"));
        assertTrue(detail(w, OWNER, EntityRef.Kind.COMPANY, COMPANY).body().fallback().contains("per 1 day"));
    }

    @Test void choicesHaveFriendlyLabelsAndDetailsWithoutChangingValuesOrRates() {
        TestWorld w = world();
        bank(w);
        w.engine.property.list(OWNER, CLAIM, 1000);
        String listing = w.engine.commerce.listMarket(OWNER, 2, 100, new Inventory().item(0, "minecraft:wheat", 2));
        w.engine.banking.associate(BUYER, COMPANY);
        String loan = w.engine.banking.requestLoan(BUYER, COMPANY, 1000, 4, null, false, false, false);
        FormSchema accounts = form(w, OWNER, "atm", TRANSFER, Map.of());
        FormChoice own = field(accounts, "fromAccount").choices().stream().filter(c -> c.value().equals(OWNER.account())).findFirst().orElseThrow();
        assertEquals("Alice - Personal account", own.label());
        assertTrue(own.detail().contains("$100.00"));
        assertTrue(field(accounts, "toAccount").choices().stream().anyMatch(c ->
                c.value().equals("company:" + COMPANY) && c.label().equals("Example Company - Company treasury")));
        String terms = "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>";
        FormSchema rates = form(w, OWNER, "bank", terms, Map.of("bank", COMPANY));
        assertEquals("75", field(rates, "depositBps").value());
        assertEquals("0.75%", field(rates, "depositBps").selectedLabel());
        assertEquals("125", field(rates, "loanBps").value());
        assertEquals("1.25%", field(rates, "loanBps").selectedLabel());
        List<FormSchema> forms = List.of(accounts, rates,
                form(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", Map.of("listingId", listing)),
                form(w, BUYER, "property", "property buy <chunkKeyOrHere>", Map.of("chunkKeyOrHere", CLAIM)),
                form(w, BUYER, "loans", "loan show <loanId>", Map.of("loanId", loan)),
                form(w, OWNER, "stock", "stock list <company> <shares> <unitPrice>", Map.of("company", COMPANY)),
                form(w, OWNER, "hub", "hub prices <itemSearch>", Map.of()),
                form(w, OWNER, "market", "market search <search>", Map.of()),
                form(w, BUYER, "bank", REQUEST, Map.of("bank", COMPANY)));
        for (FormSchema form : forms) {
            for (FormField field : form.fields()) {
                friendly(field.label() + " " + field.hint() + " " + field.selectedLabel());
                field.choices().forEach(choice -> friendly(choice.label() + " " + choice.detail()));
            }
        }
        assertEquals(listing, field(forms.get(2), "listingId").value());
        assertEquals(CLAIM, field(forms.get(3), "chunkKeyOrHere").value());
        assertEquals(loan, field(forms.get(4), "loanId").value());
    }

    @Test void retainedRecordsUseUnavailableNamesRatherThanOpaqueFallbacks() {
        TestWorld w = world();
        bank(w);
        String stock = w.engine.commerce.listStock(OWNER, COMPANY, 2, 100);
        String loan = w.loan(BUYER, COMPANY, 1000, null, false, false, false);
        w.engine.banking.repayAll(BUYER, loan);
        w.engine.banking.close(OWNER, COMPANY);
        w.governance.companies.remove(COMPANY);
        w.governance.governments.remove(NATION);
        w.data.banks.remove(COMPANY);
        w.data.playerNames.remove(BUYER.id().toString());
        EconomyDisplay display = new EconomyDisplay(w.engine);
        assertEquals("Former company - Company treasury", display.account("company:" + COMPANY));
        assertEquals("Former bank - Bank assets", display.account("bank:" + COMPANY));
        assertEquals("Former player - Personal account", display.account(BUYER.account()));
        assertEquals("Former nation - Nation treasury", display.account("nation:" + NATION));
        UiView history = detail(w, BUYER, EntityRef.Kind.LOAN, loan);
        assertEquals("Loan with Former bank", history.title().fallback());
        assertTrue(history.body().fallback().contains("Borrower: Former player"));
        friendly(visible(history));
        UiView shares = detail(w, OWNER, EntityRef.Kind.STOCK_LISTING, stock);
        assertTrue(shares.body().fallback().contains("Company: Former company"));
        friendly(visible(shares));
        assertTrue(field(form(w, BUYER, "loans", "loan show <loanId>", Map.of("loanId", loan)), "loanId")
                .selectedLabel().startsWith("Former bank"));
    }

    @Test void customNamesAndMaterialItemAttributesAreShownWithoutRewritingEscrow() {
        TestWorld w = world();
        String snbt = "{tag:{display:{Name:'{\"text\":\"Harvest Reserve\"}',Lore:['{\"text\":\"Stored carefully\"}']},"
                + "Damage:7,Enchantments:[{id:\"minecraft:unbreaking\",lvl:2s}],"
                + "AttributeModifiers:[{AttributeName:\"minecraft:generic.max_health\",Amount:2.0d,Operation:0,UUID:[I;1,2,3,4]}]},"
                + "ForgeCaps:{\"example:energy\":{stored:250,capacity:1000}}}";
        ItemLot original = new ItemLot("minecraft:wheat", snbt, 3, 64, true);
        String listing = w.engine.commerce.listMarket(OWNER, 2, 100, new Inventory().item(0, original));
        ItemLot escrow = w.data.market.get(listing).item;
        assertEquals(original.withCount(2), escrow);
        UiView detail = detail(w, BUYER, EntityRef.Kind.MARKET_LISTING, listing);
        ActionPreview preview = preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>",
                Map.of("listingId", listing, "quantityOrAll", "1"), InventoryPort.NONE);
        for (String text : List.of(visible(detail), visible(preview))) {
            friendly(text);
            assertTrue(text.contains("Harvest Reserve (Wheat)"));
            assertTrue(text.contains("Unbreaking"));
            assertTrue(text.contains("Level: 2"));
            assertTrue(text.contains("Durability used: 7"));
            assertTrue(text.contains("Amount: 2.0"));
            assertTrue(text.contains("Stored: 250"));
            assertTrue(text.contains("Capacity: 1000"));
            assertFalse(text.contains("ForgeCaps"));
            assertFalse(text.contains("UUID"));
            assertFalse(text.contains("{tag:"));
        }
        assertEquals(escrow, w.data.market.get(listing).item);
        w.engine.commerce.buyMarket(BUYER, listing, 1);
        ItemLot delivered = w.data.deliveries.get(BUYER.id().toString()).get(0).item;
        assertEquals(escrow.withCount(1), delivered);
        assertEquals(snbt, delivered.snbt());
        assertTrue(delivered.fullStackData());
    }

    @Test void financialReviewsPreserveExactMoneyAndNamedTaxRecipients() {
        TestWorld w = world();
        w.config.marketCommissionBps = 200;
        w.governance.setting("n1", "incomeTaxBps", "500");
        w.governance.setting("n2", "salesTaxBps", "1000");
        w.governance.setting("n2", "tariffBps", "500");
        String listing = w.engine.commerce.listMarket(OWNER, 2, 1001, new Inventory().item(0, "minecraft:wheat", 2));
        ActionPreview quote = preview(w, FOREIGN, "market", "market buy <listingId> <quantityOrAll>",
                Map.of("listingId", listing, "quantityOrAll", "2"), InventoryPort.NONE);
        money(quote, "Sale price before buyer taxes", 2002);
        money(quote, "Buyer taxes and tariffs", 300);
        money(quote, "Seller taxes", 100);
        money(quote, "Commission", 40);
        money(quote, "Total purchase cost", 2302);
        money(quote, "Seller net proceeds", 1862);
        assertTrue(line(quote, "Tax recipients").contains("Sales Tax → Foreign Nation - Nation treasury: $2.00"));
        assertTrue(line(quote, "Tax recipients").contains("Income Tax → Nation One - Nation treasury: $1.00"));
        assertEquals("Alice - Personal account", line(quote, "Seller"));
        friendly(visible(quote));

        ActionPreview creation = preview(w, OWNER, "bank", "bank create <company> <name> <seedCapital> <depositBps> <loanBps>",
                Map.of("company", COMPANY, "name", "New Harbor", "seedCapital", "10.00", "depositBps", "75", "loanBps", "125"),
                InventoryPort.NONE);
        assertTrue(line(creation, "Recipients").contains("New Harbor - Bank assets: $10.00"));
        assertFalse(visible(creation).contains("Former bank"));
        assertEquals("0.75% / 1.25% / 0%", line(creation, "Deposit / loan / origination rates"));
        assertEquals("1 minute", line(creation, "Interest period"));
        friendly(visible(creation));
    }

    @Test void originalAgreementKeepsOriginationRatesConsentAndModeSeparateFromCurrentSettings() {
        TestWorld w = world();
        bank(w);
        w.data.banks.get(COMPANY).originationFeeBps = 250;
        w.engine.banking.associate(BUYER, COMPANY);
        Map<String, String> input = Map.of("bank", COMPANY, "principal", "10.00", "periods", "4",
                "collateralOrNone", BUYER_CLAIM, "noneBalanceCollateralOrBoth", "both", "autoOrManual", "auto");
        ActionPreview application = preview(w, BUYER, "bank", REQUEST, input, InventoryPort.NONE);
        money(application, "Contract principal", 1000);
        money(application, "Origination fee retained if funded", 25);
        money(application, "Lifetime interest cap", 1000);
        money(application, "Future net disbursement if approved", 975);
        assertEquals("Yes / Yes", line(application, "Balance recovery / repossession consent"));
        assertEquals("4", line(application, "Repayment periods"));
        assertEquals(DisplayText.chunk(BUYER_CLAIM), line(application, "Specifically pledged property"));
        String id = w.engine.banking.requestLoan(BUYER, COMPANY, 1000, 4, BUYER_CLAIM, true, true, true);
        String original = w.data.loans.get(id).terms;
        w.engine.banking.autoPay(BUYER, id, false);
        w.data.banks.get(COMPANY).loanInterestBps = 900;
        w.data.banks.get(COMPANY).interestPeriodMillis = 120_000;
        w.config.loanGraceMillis = 86_400_000;
        w.config.defaultAfterMissedPayments = 5;
        ActionPreview approval = preview(w, OWNER, "loans", "loan approve <loanId>", Map.of("loanId", id), InventoryPort.NONE);
        assertEquals("No", line(approval, "Current automatic payments"));
        String agreement = line(approval, "Original agreement");
        assertTrue(agreement.contains("1.25% simple interest per 1 minute"));
        assertTrue(agreement.contains("4 equal-principal repayment periods"));
        assertTrue(agreement.contains("2 missed periods plus 1 minute 30 seconds grace"));
        assertTrue(agreement.contains("Original automatic payments: Yes"));
        assertTrue(agreement.contains("Personal-wallet recovery consent: Yes"));
        assertTrue(agreement.contains("Repossession consent: Yes"));
        assertTrue(agreement.contains("surplus equity is returned") && agreement.contains("shortfall remains owed"));
        assertFalse(agreement.contains("9%"));
        friendly(visible(application));
        friendly(visible(approval));
        w.engine.banking.approve(OWNER, id);
        UiView detail = detail(w, BUYER, EntityRef.Kind.LOAN, id);
        assertTrue(detail.body().fallback().contains("Current automatic payments: No"));
        assertTrue(detail.body().fallback().contains("Original automatic payments: Yes"));
        assertTrue(detail.body().fallback().contains("Sep 13, 2026 at 13:00 UTC"));
        assertFalse(detail.body().fallback().contains("2026-09-13T"));
        assertFalse(detail.body().fallback().contains(Long.toString(w.clock.millis())));
        assertEquals(original, w.data.loans.get(id).terms);
    }

    @Test void equalDisplayNamesDoNotEraseMaterialIdentityChanges() {
        TestWorld w = world();
        w.data.playerNames.put(BUYER.id().toString(), "Same person");
        w.data.playerNames.put(FOREIGN.id().toString(), "Same person");
        ActionPreview first = preview(w, OWNER, "atm", TRANSFER,
                Map.of("fromAccount", OWNER.account(), "toAccount", BUYER.account(), "amount", "1.00"), InventoryPort.NONE);
        ActionPreview second = preview(w, OWNER, "atm", TRANSFER,
                Map.of("fromAccount", OWNER.account(), "toAccount", FOREIGN.account(), "amount", "1.00"), InventoryPort.NONE);
        assertEquals(visible(first), visible(second));
        assertNotEquals(first.fingerprint(), second.fingerprint());

        w.data.playerNames.put(OWNER.id().toString(), "Same person");
        String listing = w.engine.commerce.listMarket(OWNER, 2, 100, new Inventory().item(0, "minecraft:wheat", 2));
        Map<String, String> selection = Map.of("listingId", listing, "quantityOrAll", "1");
        ActionPreview sellerBefore = preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", selection, InventoryPort.NONE);
        w.data.market.get(listing).sellerAccount = FOREIGN.account();
        ActionPreview sellerAfter = preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", selection, InventoryPort.NONE);
        assertEquals(visible(sellerBefore), visible(sellerAfter));
        assertNotEquals(sellerBefore.fingerprint(), sellerAfter.fingerprint());
    }

    @Test void userAuthoredNamesReasonsAndTextAreNotBlanketScrubbed() {
        TestWorld w = world();
        String reason = "Keep the note about " + BUYER.id() + " unchanged";
        ActionPreview suggestion = preview(w, OWNER, "hub", "hub suggest <unitPrice> <reason>",
                Map.of("unitPrice", "1.25", "reason", reason), new Inventory().item(0, "minecraft:wheat", 1));
        assertEquals(reason, line(suggestion, "Reason"));
        assertEquals(reason, EconomyDisplay.deliveryReason(reason));
        w.data.playerNames.put(OWNER.id().toString(), "Alice [original]");
        assertEquals("Alice [original] - Personal account", new EconomyDisplay(w.engine).account(OWNER.account()));
        assertEquals("Marketplace purchase", EconomyDisplay.deliveryReason("Marketplace purchase " + new UUID(0, 789)));
    }

    @Test void allBuiltInGuidedQueriesHaveReadOnlyFriendlyOutput() {
        TestWorld w = world();
        bank(w);
        String loan = w.loan(OWNER, COMPANY, 1000, null, false, false, false);
        String listing = w.engine.commerce.listMarket(OWNER, 3, 100, new Inventory().item(0, "minecraft:wheat", 3));
        w.engine.commerce.listStock(OWNER, COMPANY, 3, 100);
        w.engine.property.list(OWNER, CLAIM, 1000);
        w.engine.banking.deposit(OWNER, COMPANY, 100);
        w.engine.taxes.assess(OWNER.account(), "nation:" + NATION, NATION, "propertyTaxBps", 123, CLAIM);
        Map<String, String> values = new java.util.HashMap<>(Map.of("account", OWNER.account(), "page", "1", "itemSearch", "minecraft:wheat",
                "search", "minecraft:wheat", "listingId", listing, "company", COMPANY, "companySearch", COMPANY,
                "bank", COMPANY, "loanId", loan, "amount", "1.00"));
        values.put("chunkKeyOrHere", CLAIM);
        menus();
        for (var page : MenuRegistry.pages()) {
            if (!page.id().startsWith("economy:")) continue;
            List<String> commands = new java.util.ArrayList<>();
            commands.add(page.query());
            page.actions().stream().filter(action -> action.intent() == dev.statecraft.api.ui.ActionIntent.QUERY)
                    .map(dev.statecraft.api.MenuPage.Action::command).filter(command -> !command.equals("merchant list")).forEach(commands::add);
            for (String command : commands) {
                Map<String, String> fields = new CommandTemplate(command).fields().stream()
                        .collect(Collectors.toMap(Function.identity(), key -> java.util.Objects.requireNonNull(values.get(key), command + ": " + key)));
                QueryResult result = query(w, OWNER, page.id().substring(8), command, fields);
                friendly(result.shown());
                assertTrue(moneyValues(result.shown()).containsAll(moneyValues(result.raw())), command + "\n" + result.shown());
            }
        }
    }

    @Test void guidedHistoryPreservesAuthorizedPagingAmountsAndUserProse() {
        TestWorld w = world();
        w.set(OWNER, 10_000_000);
        w.data.transactions.clear();
        for (int i = 0; i < 17; i++) w.engine.pay(OWNER, OWNER.account(), BUYER.account(), 100_000 + i, "Human note " + i);
        w.engine.pay(FOREIGN, FOREIGN.account(), BUYER.account(), 50, "Other account's private note");
        QueryResult result = query(w, OWNER, "atm", "history <account> <page>", Map.of("account", OWNER.account(), "page", "2"));
        assertEquals("Activity — 17 entries, page 2/2", result.shown().lines().findFirst().orElseThrow());
        assertEquals(result.raw().lines().findFirst(), result.shown().lines().findFirst());
        assertEquals(moneyValues(result.raw()), moneyValues(result.shown()));
        assertEquals(6, result.shown().lines().count());
        assertTrue(result.shown().contains("Alice - Personal account → Bob - Personal account"));
        assertTrue(result.shown().contains("Human note 4") && result.shown().contains("Human note 0"));
        assertFalse(result.shown().contains("Human note 5"));
        assertFalse(result.shown().contains("Other account's private note"));
        friendly(result.shown());
        String prose = "My own note about " + BUYER.id() + " must remain verbatim.";
        w.engine.pay(OWNER, OWNER.account(), BUYER.account(), 1, prose);
        assertTrue(query(w, OWNER, "atm", "history <account> <page>", Map.of("account", OWNER.account(), "page", "1")).shown().contains(prose));
        assertThrows(UserError.class, () -> query(w, FOREIGN, "atm", "history <account> <page>",
                Map.of("account", OWNER.account(), "page", "1")));
        EconomyData.Account account = w.data.accounts.get(OWNER.account());
        account.dailyLimit = 1000;
        account.spent = 250;
        account.spentDay = Math.floorDiv(w.clock.millis(), 86_400_000L);
        ActionSelection limits = ActionSelection.form("economy:atm", "limits <account>", Map.of("account", OWNER.account()));
        String raw = w.engine.execute(OWNER, limits.rendered(), InventoryPort.NONE);
        w.advance(86_400_000);
        assertEquals(moneyValues(raw), moneyValues(new EconomyPresentation(w.engine).queryText(OWNER, limits, raw)),
                "Formatting a completed query must not recalculate the quoted daily allowance after midnight.");
    }

    @Test void guidedListingQueriesKeepOriginalFiltersPagesAndSelectedIdentifiers() {
        TestWorld w = world();
        for (int i = 0; i < 14; i++) {
            w.engine.commerce.listMarket(OWNER, 1, 100 + i, new Inventory().item(0, "minecraft:wheat", 1));
            w.engine.commerce.listStock(OWNER, COMPANY, 1, 200 + i);
        }
        w.engine.commerce.listMarket(OWNER, 1, 999, new Inventory().item(0, "minecraft:diamond", 1));
        QueryResult market = query(w, BUYER, "market", "page <page> market search <search>",
                Map.of("page", "2", "search", "minecraft:wheat"));
        assertEquals("Marketplace — 14 entries, page 2/2", market.shown().lines().findFirst().orElseThrow());
        assertEquals(moneyValues(market.raw()), moneyValues(market.shown()));
        assertFalse(market.shown().contains("Diamond"));
        friendly(market.shown());
        QueryResult stock = query(w, BUYER, "stock", "page <page> stock search <companySearch>",
                Map.of("page", "2", "companySearch", COMPANY));
        assertEquals("Stock market — 14 entries, page 2/2", stock.shown().lines().findFirst().orElseThrow());
        assertEquals(moneyValues(stock.raw()), moneyValues(stock.shown()));
        assertTrue(stock.shown().contains("Example Company"));
        friendly(stock.shown());
        String listing = w.data.market.keySet().iterator().next();
        QueryResult selected = query(w, BUYER, "market", "market search <search>", Map.of("search", listing));
        assertEquals("Marketplace — 1 entries, page 1/1", selected.shown().lines().findFirst().orElseThrow());
        assertTrue(w.data.market.containsKey(listing));
        friendly(selected.shown());
        QueryResult prices = query(w, BUYER, "hub", "hub prices <itemSearch>", Map.of("itemSearch", "minecraft:wheat"));
        assertTrue(prices.shown().contains("Wheat = $0.25"));
        assertEquals(moneyValues(prices.raw()), moneyValues(prices.shown()));
    }

    @Test void guidedTaxQueriesRetainAllPoliciesAssessmentsAndExactObligations() {
        TestWorld w = world();
        w.governance.setting("n1", "incomeTaxBps", "125");
        w.governance.setting("n1", "baseChunkValue", "12345");
        w.governance.setting("n1", "foreignAccess", "false");
        QueryResult policies = query(w, OWNER, "tax", "tax rates", Map.of());
        assertTrue(policies.shown().contains("Income Tax: 1.25%"));
        assertTrue(policies.shown().contains("Base Chunk Value: $123.45"));
        assertTrue(policies.shown().contains("Foreign Access: No"));
        friendly(policies.shown());
        QueryResult quote = query(w, OWNER, "tax", "tax quote <amount>", Map.of("amount", "100.00"));
        assertTrue(quote.shown().contains("Income Tax: $1.25 — Nation One - Nation treasury: $1.25"));
        assertTrue(quote.shown().contains("Sales Tax: $0.00") && quote.shown().contains("Property Tax: $0.00"));
        assertTrue(quote.shown().contains("Corporate Tax: $0.00") && quote.shown().contains("Tariff: $0.00"));
        friendly(quote.shown());
        w.engine.taxes.assessPeriods(OWNER.account(), new Taxation.Charge(NATION, "nation:" + NATION, "propertyTaxBps", Money.MAX), CLAIM, 12);
        String full = EconomyPresentation.money(java.math.BigInteger.valueOf(Money.MAX).multiply(java.math.BigInteger.valueOf(12)));
        QueryResult report = query(w, OWNER, "tax", "tax report <account> <page>", Map.of("account", OWNER.account(), "page", "1"));
        assertTrue(report.shown().contains("12 overdue periods") && report.shown().contains("full assessment: " + full), report.shown());
        assertTrue(report.shown().contains("Chunk 0, 0 (Overworld)"));
        assertTrue(report.shown().contains("recipient: Arcadia"));
        assertEquals(report.raw().lines().findFirst(), report.shown().lines().findFirst());
        friendly(report.shown());
        QueryResult arrears = query(w, OWNER, "tax", "tax arrears <account>", Map.of("account", OWNER.account()));
        assertTrue(arrears.shown().startsWith("Outstanding: " + full + "\n"));
        assertEquals(moneyValues(arrears.raw()), moneyValues(arrears.shown()));
        friendly(arrears.shown());
    }

    @Test void guidedBankHistoryRetainsHistoricalRatesAndLoanQueryDoesNotAccrueAgain() {
        TestWorld w = world();
        bank(w);
        w.set("bank:" + COMPANY, 500_000);
        w.config.maximumUnsecuredLoanCents = 200_000;
        w.engine.banking.rates(OWNER, COMPANY, 75, 125, 250, 10, 20);
        String id = w.loan(BUYER, COMPANY, 100_000, null, false, false, true);
        w.engine.banking.autoPay(BUYER, id, false);
        w.advance(60_000);
        QueryResult loan = query(w, BUYER, "loans", "loan show <loanId>", Map.of("loanId", id));
        assertTrue(loan.shown().contains("Current automatic payments: No"));
        assertTrue(loan.shown().contains("Original automatic payments: Yes"));
        assertTrue(loan.shown().contains("missed payments: " + w.data.loans.get(id).missedPayments));
        assertTrue(moneyValues(loan.shown()).containsAll(moneyValues(loan.raw())));
        friendly(loan.shown());
        w.engine.banking.repayAll(BUYER, id);
        w.data.loans.remove(id);
        w.engine.banking.rates(OWNER, COMPANY, 100, 900, 500, 50, 60);
        QueryResult report = query(w, OWNER, "bank", "bank report <bank>", Map.of("bank", COMPANY));
        assertEquals(report.raw().lines().findFirst(), report.shown().lines().findFirst());
        assertTrue(report.shown().contains("Future deposit rate 0.75%; loan rate 1.25%; origination fee rate 2.5%"));
        assertTrue(report.shown().contains("Future deposit rate 1%; loan rate 9%; origination fee rate 5%"));
        assertTrue(report.shown().contains("Former loan"));
        assertTrue(report.shown().contains("1.25% simple interest per 1 minute"));
        assertTrue(report.shown().contains("Original automatic payments: Yes"));
        friendly(report.shown());
        assertThrows(UserError.class, () -> query(w, FOREIGN, "bank", "bank report <bank>", Map.of("bank", COMPANY)));
    }

    @Test void advancedAndCustomQueryOutputsStayUntouched() {
        TestWorld w = world();
        menus();
        String custom = "Configured merchant notes: keep " + BUYER.id() + ", 125 bps, and custom prose.";
        w.engine.setCommandHooks(new EconomyCommands.Hooks() {
            @Override public String merchants(Actor actor, List<String> args) { return custom; }
        });
        QueryResult merchant = query(w, OWNER, "guide", "merchant list", Map.of());
        assertEquals(custom, merchant.shown());
        EconomyPresentation ui = new EconomyPresentation(w.engine);
        assertEquals(custom, ui.queryText(OWNER, ActionSelection.raw("economy:atm", "history me 1"), custom));
        assertEquals(custom, ui.queryText(OWNER, ActionSelection.form("economy:atm", "account select <account>",
                Map.of("account", OWNER.account())), custom));
        assertEquals(custom, new EconomyDisplay(w.engine).bankEventDetail(OWNER, "BRANDING", custom));
    }

    @Test void aHistoryReferenceDoesNotRevealAnotherBorrowersPrivateLoan() {
        TestWorld w = world();
        bank(w);
        String loan = w.loan(BUYER, COMPANY, 1000, null, false, false, false);
        w.engine.pay(FOREIGN, FOREIGN.account(), OWNER.account(), 1, "Loan repayment " + loan);
        String history = query(w, FOREIGN, "atm", "history <account> <page>", Map.of("account", FOREIGN.account(), "page", "1")).shown();
        assertFalse(history.contains("Bob"));
        assertFalse(history.contains("Harbor Bank"));
        friendly(history);
    }

    private static TestWorld world() {
        TestWorld w = new TestWorld(c -> {
            c.financialPeriodMillis = 60_000;
            c.loanGraceMillis = 90_000;
            c.companyFeePeriodMillis = 86_400_000;
            c.marketLifetimeMillis = c.stockLifetimeMillis = c.propertyListingLifetimeMillis = c.loanApplicationLifetimeMillis = 60_000;
        });
        w.clock.now = Instant.parse("2026-09-13T13:00:00Z").toEpochMilli();
        w.governance.companies.put(COMPANY, w.governance.companies.remove("co"));
        w.governance.governments.put(NATION, new GovernanceAccess.GovernmentView(NATION, GovernanceAccess.Kind.NATION,
                "Arcadia", null, NATION, OWNER.id(), Set.of(OWNER.id()), Map.of()));
        w.data.playerNames.put(OWNER.id().toString(), "Alice");
        w.data.playerNames.put(BUYER.id().toString(), "Bob");
        w.data.playerNames.put(FOREIGN.id().toString(), "Carol");
        for (Actor actor : List.of(OWNER, BUYER, FOREIGN)) w.set(actor, 10_000);
        w.set("nation:" + NATION, 76543);
        w.set("company:" + COMPANY, 100_000);
        return w;
    }

    private static void bank(TestWorld w) { w.engine.banking.create(OWNER, COMPANY, "Harbor Bank", 50_000, 75, 125); }
    private static FormField field(FormSchema schema, String key) {
        return schema.fields().stream().filter(field -> field.key().equals(key)).findFirst().orElseThrow();
    }
    private static FormSchema form(TestWorld w, Actor actor, String page, String command, Map<String, String> values) {
        String before = snapshot(w);
        FormContext context = new FormContext(actor, "economy:" + page, command, values, FormQuery.INITIAL);
        FormBuilder builder = new FormBuilder(context);
        new EconomyForms(w.engine).describe(context, builder);
        assertEquals(before, snapshot(w));
        return builder.build();
    }
    private static UiView page(TestWorld w, Actor actor, String page) { return view(w, actor, UiQuery.page("economy:" + page)); }
    private static UiView detail(TestWorld w, Actor actor, EntityRef.Kind kind, String id) {
        return view(w, actor, UiQuery.detail(new EntityRef("economy", kind, id)));
    }
    private static UiView view(TestWorld w, Actor actor, UiQuery query) {
        menus();
        String before = snapshot(w);
        UiView view = new EconomyPresentation(w.engine).view(new UiContext(actor, query));
        assertEquals(before, snapshot(w));
        return view;
    }
    private static ActionPreview preview(TestWorld w, Actor actor, String page, String command, Map<String, String> values, InventoryPort inventory) {
        menus();
        String before = snapshot(w);
        List<ItemLot> items = inventory.snapshot();
        ActionPreview result = new EconomyPresentation(w.engine).preview(actor, ActionSelection.form("economy:" + page, command, values), inventory);
        assertEquals(before, snapshot(w));
        assertEquals(items, inventory.snapshot());
        return result;
    }
    private static String line(ActionPreview preview, String label) {
        return preview.lines().stream().filter(line -> line.label().fallback().equals(label)).findFirst().orElseThrow().value().fallback();
    }
    private record QueryResult(String raw, String shown) {}
    private static QueryResult query(TestWorld w, Actor actor, String page, String command, Map<String, String> values) {
        menus();
        Actor named = new Actor(actor.id(), w.data.playerNames.getOrDefault(actor.id().toString(), actor.name()), actor.admin(),
                actor.dimension(), actor.chunkX(), actor.chunkZ());
        ActionSelection selection = ActionSelection.form("economy:" + page, command, values);
        String raw = w.engine.execute(named, selection.rendered(), InventoryPort.NONE);
        String before = snapshot(w);
        String shown = new EconomyPresentation(w.engine).queryText(named, selection, raw);
        assertEquals(before, snapshot(w), command + " mutated data while formatting a completed query");
        assertEquals(values, selection.values());
        return new QueryResult(raw, shown);
    }
    private static List<String> moneyValues(String text) {
        return Pattern.compile("\\$[0-9,]+\\.[0-9]{2}").matcher(text).results().map(java.util.regex.MatchResult::group).toList();
    }
    private static void money(ActionPreview preview, String label, long cents) { assertEquals(Money.format(cents), line(preview, label)); }
    private static String visible(ActionPreview preview) {
        return preview.title().fallback() + preview.warning().fallback() + preview.lines().stream()
                .map(line -> line.label().fallback() + ": " + line.value().fallback()).collect(Collectors.joining("\n"));
    }
    private static String visible(UiView view) {
        return view.title().fallback() + "\n" + view.body().fallback() + "\n"
                + view.rows().stream().map(row -> row.title().fallback() + " " + row.detail().fallback()).collect(Collectors.joining("\n"))
                + view.actions().stream().map(action -> action.label().fallback() + " " + action.disabledReason().fallback()).collect(Collectors.joining("\n"));
    }
    private static void friendly(String text) {
        assertFalse(UUID_TEXT.matcher(text).find(), text);
        assertFalse(text.contains("minecraft:"), text);
        assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("bps"), text);
        assertFalse(text.contains(" ms"), text);
    }
    private static String snapshot(TestWorld w) {
        Gson gson = new Gson();
        return gson.toJson(w.data) + gson.toJson(w.governance.claims) + gson.toJson(w.governance.reservations)
                + gson.toJson(w.governance.messages) + ":" + w.dirty;
    }
    private static void menus() {
        if (MenuRegistry.pages().stream().anyMatch(page -> page.id().equals("economy:atm"))) return;
        try {
            var method = Class.forName("dev.statecraft.economy.forge.EconomyMenus").getDeclaredMethod("register");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
