package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.form.FormValidation;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiView;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.statecraft.economy.TestWorld.*;
import static dev.statecraft.economy.EconomyRegressionScenarios.*;

/** Pure Java entry point as well as runner-wired JUnit dynamic scenarios. */
public final class EconomyPresentationScenarios {
    private EconomyPresentationScenarios() {}

    public static void committedPurchaseSurvivesExpectedAutomaticCollectionRefusal() {
        TestWorld w = new TestWorld();
        w.set(BUYER, 1000);
        String id = w.engine.commerce.listMarket(OWNER, 2, 100, new Inventory().item(0, "minecraft:wheat", 2));
        Inventory refused = new Inventory();
        refused.rejectReplace = true;
        String response = w.engine.execute(BUYER, "market buy " + id + " 1", refused);
        check(response.contains("Purchased") && response.contains("queued") && response.contains("Inventory changed")
                && response.contains("market collect") && response.contains("do not repeat"), "purchase success was hidden by collection refusal");
        eq(900, w.engine.balance(BUYER.account())); eq(100, w.engine.balance(OWNER.account()));
        eq(1, w.data.deliveries.get(BUYER.id().toString()).get(0).item.count());
        eq(1, w.data.market.get(id).remaining);
        refused.rejectReplace = false;
        eq(1, w.engine.commerce.collect(BUYER, refused));
        response = w.engine.execute(BUYER, "market buy " + id + " 1", refused);
        check(response.contains("Purchased") && response.contains("collected 1"), "ordinary automatic collection regressed");
        eq(2, refused.count("minecraft:wheat")); check(!w.data.deliveries.containsKey(BUYER.id().toString()), "collected delivery remained queued");

        String unexpected = w.engine.commerce.listMarket(OWNER, 1, 100, new Inventory().item(0, "minecraft:wheat", 1));
        InventoryPort broken = new InventoryPort() {
            final Inventory delegate = new Inventory();
            @Override public List<ItemLot> snapshot() { return delegate.snapshot(); }
            @Override public int selectedSlot() { return delegate.selectedSlot(); }
            @Override public boolean near(Utility utility) { return true; }
            @Override public void replace(List<ItemLot> expected, List<ItemLot> replacement) { throw new IllegalStateException("Unexpected inventory adapter failure"); }
        };
        expect(IllegalStateException.class, () -> w.engine.execute(BUYER, "market buy " + unexpected + " 1", broken));
        eq(700, w.engine.balance(BUYER.account()));
        eq(1, w.data.deliveries.get(BUYER.id().toString()).get(0).item.count());
    }

    public static void currentAutopayIsSeparateFromTheOriginalAgreement() {
        TestWorld w = new TestWorld();
        String bank = w.bank(10_000, 0, 100);
        String id = w.loan(BUYER, bank, 4000, null, false, false, true);
        String original = w.data.loans.get(id).terms;
        w.engine.banking.autoPay(BUYER, id, false);
        String output = w.engine.execute(BUYER, "loan show " + id, InventoryPort.NONE);
        check(output.contains("current automatic payments=false") && output.contains("Original agreement (immutable)")
                && output.contains("automatic payments=true"), "current mode and historical consent were conflated");
        eq(original, w.data.loans.get(id).terms);
        UiView detail = detail(w, BUYER, EntityRef.Kind.LOAN, id);
        check(detail.body().fallback().contains("Current automatic payments: false"), "loan panel omitted the current mode");
        check(detail.actions().stream().anyMatch(action -> action.template().equals("loan autopay <loanId> true") && action.enabled()),
                "manual loan lacks an enabled autopay action");
        check(detail.actions().stream().anyMatch(action -> action.template().equals("loan autopay <loanId> false") && !action.enabled()),
                "redundant autopay action was not explained");
        FormSchema form = form(w, BUYER, "bank", "loan show <loanId>", Map.of("loanId", id));
        check(form.fields().get(0).choices().stream().anyMatch(choice -> choice.detail().contains("current autopay off")),
                "loan selector omitted current mode");
        w.advance(1000); w.engine.banking.tickLoan(id);
        eq(4000, w.engine.balance(BUYER.account())); eq(4000, w.data.loans.get(id).principal);
        check(w.engine.banking.previewLoan(BUYER, id).currentlyDue() > 0, "manual installment was not due");
        eq(original, w.data.loans.get(id).terms);
    }

    public static void everyEconomyViewIsTypedAuthorizedPagedAndReadOnly() {
        registerMenus();
        Fixture f = new Fixture();
        for (var menu : MenuRegistry.pages()) {
            if (!menu.id().startsWith("economy:")) continue;
            String before = snapshot(f.w);
            UiView view = f.ui.view(new UiContext(OWNER, UiQuery.page(menu.id())));
            eq(before, snapshot(f.w));
            check(view.rows().size() <= UiView.PAGE_SIZE, "unbounded section payload");
            check(!view.emptyHint().fallback().isBlank(), "missing empty-state guidance");
            validateActions(view);
            validateSeeds(f.w, OWNER, view);
            for (var row : view.rows()) {
                if (!row.entity().present()) continue;
                UiView detail = f.ui.view(new UiContext(OWNER, UiQuery.detail(row.entity())));
                eq(before, snapshot(f.w));
                validateActions(detail);
                validateSeeds(f.w, OWNER, detail);
            }
        }
        UiView missing = f.ui.view(new UiContext(OWNER, new UiQuery("economy:market", EntityRef.NONE, "no-such-item", 0)));
        eq(0, missing.rows().size()); check(!missing.emptyHint().fallback().isBlank(), "search has no empty hint");
    }

    public static void privateAndForgedReferencesCannotExposeOtherPlayersFinance() {
        Fixture f = new Fixture();
        String loan = f.w.loan(BUYER, "co", 800, null, false, false, false);
        f.w.engine.taxes.assess(BUYER.account(), "nation:n2", "n2", "incomeTaxBps", 900, "private debt");
        f.w.engine.commerce.buyMarket(BUYER, f.market, 1);
        String delivery = f.w.data.deliveries.get(BUYER.id().toString()).get(0).id;
        String arrear = ActionPreview.digest(f.w.data.arrears.values().stream().filter(debt -> debt.payer.equals(BUYER.account())).findFirst().orElseThrow().id);
        String before = snapshot(f.w);
        for (EntityRef entity : List.of(ref(EntityRef.Kind.ACCOUNT, BUYER.account()), ref(EntityRef.Kind.LOAN, loan),
                ref(EntityRef.Kind.DELIVERY, delivery), ref(EntityRef.Kind.ARREARS, arrear), ref(EntityRef.Kind.COMPANY, "co"),
                ref(EntityRef.Kind.GOVERNMENT, "n1"), ref(EntityRef.Kind.LOAN, "../" + loan), ref(EntityRef.Kind.DASHBOARD, "core:dashboard"))) {
            expect(UserError.class, () -> f.ui.view(new UiContext(FOREIGN, UiQuery.detail(entity))));
        }
        eq(before, snapshot(f.w));
        UiView foreignLoans = f.ui.view(new UiContext(FOREIGN, UiQuery.page("economy:loans")));
        check(foreignLoans.rows().stream().noneMatch(row -> row.entity().id().equals(loan)), "borrower loan leaked into another player's section");
        UiView taxes = f.ui.view(new UiContext(FOREIGN, UiQuery.page("economy:tax")));
        check(taxes.rows().stream().noneMatch(row -> row.entity().id().equals(arrear)), "recipient treasury leadership exposed the payer's private arrears");
        expect(UserError.class, () -> preview(f.w, OWNER, "atm", "transfer <fromAccount> <toAccount> <amount>",
                Map.of("fromAccount", BUYER.account(), "toAccount", OWNER.account(), "amount", "1.00"), InventoryPort.NONE));
        expect(UserError.class, () -> preview(f.w, OWNER, "bank", "loan autopay <loanId> false", Map.of("loanId", loan), InventoryPort.NONE));
    }

    public static void dashboardScopesManagersAndPaginatesPendingWork() {
        registerMenus();
        TestWorld w = new TestWorld();
        Inventory items = new Inventory().item(0, "minecraft:wheat", 32);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 26; i++) ids.add(w.engine.commerce.listMarket(OWNER, 1, 100, items));
        w.engine.commerce.listMarket(BUYER, 1, 200, new Inventory().item(0, "minecraft:diamond", 1));
        EconomyPresentation ui = new EconomyPresentation(w.engine);
        UiView first = ui.dashboard(OWNER, "", 0), second = ui.dashboard(OWNER, "", 20);
        eq(20, first.rows().size()); check(first.more(), "first dashboard page hid further tasks");
        eq(6, second.rows().size()); check(!second.more(), "last dashboard page promised more tasks");
        eq(26, rowIds(first, second).size());
        eq(ids.get(25), ui.dashboard(OWNER, ids.get(25), 0).rows().get(0).entity().id());
        check(first.rows().stream().noneMatch(row -> row.title().fallback().contains("diamond")), "another seller's task leaked");
        String bank = w.bank(10_000, 0, 0);
        w.engine.banking.associate(BUYER, bank);
        String application = w.engine.banking.requestLoan(BUYER, bank, 500, 4, null, false, false, false);
        check(ui.dashboard(OWNER, application, 0).rows().stream().anyMatch(row -> row.entity().id().equals(application)), "manager cannot discover an application");
        eq(0, ui.dashboard(FOREIGN, application, 0).rows().size());
        w.engine.banking.approve(OWNER, application);
        eq(0, ui.dashboard(OWNER, application, 0).rows().size());
        eq(1, ui.dashboard(BUYER, application, 0).rows().size());
    }

    public static void retainedLoanHistoryToleratesClosedCompaniesAndBanks() {
        registerMenus();
        TestWorld w = new TestWorld();
        String bank = w.bank(1000, 0, 0);
        String loan = w.loan(BUYER, bank, 500, null, false, false, false);
        w.engine.banking.repayAll(BUYER, loan);
        w.engine.banking.close(OWNER, bank);
        w.governance.companies.remove("co");
        UiView view = detail(w, BUYER, EntityRef.Kind.LOAN, loan);
        check(view.body().fallback().contains("REPAID") && view.body().fallback().contains("Original agreement"), "legitimate retained history was unavailable");
        check(view.actions().stream().noneMatch(action -> action.enabled()), "closed historical loan offers financial actions");
        validateActions(view);
    }

    public static void everyRegisteredFinancialAndOrdinaryMutationHasAPureReview() {
        Fixture f = new Fixture();
        Map<String, String> values = new java.util.HashMap<>(Map.of(
                "account", OWNER.account(), "fromAccount", OWNER.account(), "toAccount", BUYER.account(), "companyAccount", "company:co",
                "recipient", BUYER.account(), "amount", "1.00", "quantity", "1", "quantityOrAll", "1", "sharesOrAll", "1", "shares", "1"));
        values.putAll(Map.of("bank", "co", "company", "co", "loanId", f.loan, "listingId", f.market, "unitPrice", "1.00",
                "budget", "1.00", "principal", "1.00", "periods", "4", "collateralOrNone", "none", "noneBalanceCollateralOrBoth", "none"));
        values.putAll(Map.of("autoOrManual", "manual", "name", "New Bank", "seedCapital", "1.00", "depositBps", "100",
                "loanBps", "100", "originationBps", "100", "depositFee", "0.10", "withdrawalFee", "0.20",
                "reason", "New price suggestion", "amountOrAll", "1.00"));
        values.put("chunkKeyOrHere", CLAIM); values.put("price", "1.00");
        for (var menu : MenuRegistry.pages()) {
            if (!menu.id().startsWith("economy:")) continue;
            for (var action : menu.actions()) {
                if (action.intent() != ActionIntent.MUTATION) continue;
                String template = action.command();
                Actor actor = OWNER;
                Map<String, String> input = new java.util.HashMap<>();
                for (String key : new CommandTemplate(template).fields()) input.put(key, values.get(key));
                if (template.startsWith("stock ") && input.containsKey("listingId")) input.put("listingId", f.stock);
                if (template.startsWith("market buy") || template.startsWith("stock buy") || template.startsWith("property buy")) actor = BUYER;
                if (template.startsWith("loan approve") || template.startsWith("loan cancel")) input.put("loanId", f.application);
                if (template.startsWith("bank create")) input.put("company", "other");
                if (template.startsWith("property list")) input.put("chunkKeyOrHere", "minecraft:overworld|2|0");
                ActionPreview preview;
                try { preview = preview(f.w, actor, menu.id().substring(8), template, input, f.inventory); }
                catch (UserError failure) { throw new AssertionError("Missing or invalid review for " + template, failure); }
                check(!preview.lines().isEmpty(), "registered mutation has an empty review: " + template);
                check(preview.lines().size() <= ActionPreview.MAX_LINES, "oversized action preview");
            }
        }
    }

    public static void quoteFingerprintsIgnoreBalancesAndUnrelatedElectronicInventory() {
        TestWorld w = new TestWorld();
        w.set(BUYER, 1000);
        String id = w.engine.commerce.listMarket(OWNER, 3, 100, new Inventory().item(0, "minecraft:wheat", 3));
        Map<String, String> values = Map.of("listingId", id, "quantityOrAll", "1");
        ActionPreview first = preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", values, new Inventory().item(0, "minecraft:diamond", 1));
        w.set(BUYER, 2000);
        Inventory unrelated = new Inventory().item(3, "minecraft:stone", 64); unrelated.selected = 3;
        ActionPreview second = preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", values, unrelated);
        eq(first.fingerprint(), second.fingerprint());
        w.data.market.get(id).unitPrice = 101;
        check(!first.fingerprint().equals(preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", values, unrelated).fingerprint()),
                "repriced listing retained its old fingerprint");
        w.data.market.get(id).unitPrice = 100;
        w.data.market.get(id).item = new ItemLot("minecraft:wheat", "{Custom:1}", 3, 64);
        check(!first.fingerprint().equals(preview(w, BUYER, "market", "market buy <listingId> <quantityOrAll>", values, unrelated).fingerprint()),
                "changed escrow metadata retained its old fingerprint");
    }

    public static void cashAndHubQuotesMatchActualDenominationsTaxesAndInventory() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 2000);
        Inventory inventory = new Inventory();
        ActionPreview withdrawal = preview(w, OWNER, "atm", "cash withdraw <amount> <account>", Map.of("amount", "12.34", "account", OWNER.account()), inventory);
        moneyLine(withdrawal, "Total debit", 1200);
        moneyLine(withdrawal, "Unissued amount staying electronic", 34);
        w.engine.execute(OWNER, "cash withdraw 12.34 me", inventory);
        eq(800, w.engine.balance(OWNER.account())); eq(1, inventory.count(TEN)); eq(2, inventory.count(ONE));
        inventory.item(2, new ItemLot(TEN, "{Forged:1}", 1, 64));
        ActionPreview deposit = preview(w, OWNER, "atm", "cash deposit <account>", Map.of("account", OWNER.account()), inventory);
        moneyLine(deposit, "Account credit", 1200);
        w.engine.execute(OWNER, "cash deposit me", inventory);
        eq(2000, w.engine.balance(OWNER.account())); eq(1, inventory.count(TEN));
        w.governance.setting("n1", "salesTaxBps", "1000");
        w.governance.setting("n1", "incomeTaxBps", "500");
        Inventory wheat = new Inventory().item(0, "minecraft:wheat", 2);
        ActionPreview hub = preview(w, OWNER, "hub", "hub sell <quantity>", Map.of("quantity", "2"), wheat);
        moneyLine(hub, "Gross amount", 50); moneyLine(hub, "Taxes", 7); moneyLine(hub, "Net received", 43);
        w.engine.execute(OWNER, "hub sell 2", wheat);
        eq(2043, w.engine.balance(OWNER.account())); eq(7, w.engine.balance("nation:n1")); eq(0, wheat.count("minecraft:wheat"));
        Inventory full = new Inventory(); full.fill();
        expect(UserError.class, () -> preview(w, OWNER, "atm", "cash withdraw <amount> <account>",
                Map.of("amount", "1.00", "account", OWNER.account()), full));
        expect(UserError.class, () -> preview(w, OWNER, "hub", "hub sell <quantity>", Map.of("quantity", "1"), InventoryPort.NONE));
    }

    public static void marketAndStockQuotesMatchFeesTariffsAndSettlement() {
        TestWorld w = new TestWorld(c -> { c.marketCommissionBps = 200; c.stockListingFeeCents = 7; });
        w.governance.setting("n1", "incomeTaxBps", "500");
        w.governance.setting("n2", "salesTaxBps", "1000");
        w.governance.setting("n2", "tariffBps", "500");
        w.set(FOREIGN, 1000); w.set(OWNER, 100);
        String listing = w.engine.commerce.listMarket(OWNER, 2, 100, new Inventory().item(0, "minecraft:wheat", 2));
        ActionPreview market = preview(w, FOREIGN, "market", "market buy <listingId> <quantityOrAll>",
                Map.of("listingId", listing, "quantityOrAll", "all"), InventoryPort.NONE);
        moneyLine(market, "Total purchase cost", 230); moneyLine(market, "Seller net proceeds", 186);
        moneyLine(market, "Buyer taxes and tariffs", 30); moneyLine(market, "Seller taxes", 10); moneyLine(market, "Commission", 4);
        w.engine.execute(FOREIGN, "market buy " + listing + " all", InventoryPort.NONE);
        eq(770, w.engine.balance(FOREIGN.account())); eq(286, w.engine.balance(OWNER.account()));
        ActionPreview offer = preview(w, OWNER, "stock", "stock list <company> <shares> <unitPrice>",
                Map.of("company", "co", "shares", "10", "unitPrice", "1.00"), InventoryPort.NONE);
        moneyLine(offer, "Fee payable now", 7);
        String stock = w.engine.commerce.listStock(OWNER, "co", 10, 100);
        ActionPreview purchase = preview(w, FOREIGN, "stock", "stock buy <listingId> <sharesOrAll>",
                Map.of("listingId", stock, "sharesOrAll", "2"), InventoryPort.NONE);
        moneyLine(purchase, "Total purchase cost", 230); moneyLine(purchase, "Seller net proceeds", 190);
        w.engine.execute(FOREIGN, "stock buy " + stock + " 2", InventoryPort.NONE);
        eq(540, w.engine.balance(FOREIGN.account())); eq(469, w.engine.balance(OWNER.account()));
        eq(2, w.governance.sharesOf("co", FOREIGN.id()));
    }

    public static void companyPaymentDividendAndTaxQuotesMatchCommit() {
        TestWorld w = new TestWorld(c -> c.companyTransferFeeCents = 10);
        w.set("company:co", 2000);
        Inventory inventory = new Inventory();
        ActionPreview payment = preview(w, OWNER, "company", "company pay <company> <recipient> <amount>",
                Map.of("company", "co", "recipient", BUYER.account(), "amount", "1.00"), inventory);
        moneyLine(payment, "Total debit", 110);
        w.engine.execute(OWNER, "company pay co Buyer 1.00", inventory);
        eq(1890, w.engine.balance("company:co")); eq(100, w.engine.balance(BUYER.account()));
        w.governance.companies.get("co").shares.clear();
        for (Actor actor : List.of(OWNER, BUYER, FOREIGN)) w.governance.companies.get("co").shares.put(actor.id(), 1L);
        w.governance.setting("n1", "corporateTaxBps", "1000");
        ActionPreview dividend = preview(w, OWNER, "company", "company dividend <company> <budget>",
                Map.of("company", "co", "budget", "10.01"), inventory);
        moneyLine(dividend, "Dividend budget", 1001); moneyLine(dividend, "Corporate tax", 100);
        moneyLine(dividend, "Paid to shareholders", 900); moneyLine(dividend, "Rounding remainder retained", 1);
        moneyLine(dividend, "Total debit", 1000);
        w.engine.execute(OWNER, "company dividend co 10.01", inventory);
        eq(890, w.engine.balance("company:co"));
        w.engine.taxes.assess("company:co", "nation:n1", "n1", "companyFee", 1000, "due");
        ActionPreview taxes = preview(w, OWNER, "tax", "tax pay <amountOrAll> <account>",
                Map.of("amountOrAll", "all", "account", "company:co"), InventoryPort.NONE);
        moneyLine(taxes, "Total debit", 890);
        w.engine.execute(OWNER, "tax pay all company:co", InventoryPort.NONE);
        eq(0, w.engine.balance("company:co")); eq(java.math.BigInteger.valueOf(110), w.engine.taxes.arrears("company:co"));
        expect(UserError.class, () -> preview(w, OWNER, "company", "company pay <company> <recipient> <amount>",
                Map.of("company", "co", "recipient", BUYER.account(), "amount", "1.00"), InventoryPort.NONE));
    }

    public static void bankAndLoanQuotesProjectUnprocessedInterestWithoutBookingIt() {
        TestWorld w = new TestWorld();
        String bank = w.bank(5000, 100, 500);
        w.engine.banking.rates(OWNER, bank, 100, 500, 100, 25, 25);
        w.set(BUYER, 10_000);
        ActionPreview deposit = preview(w, BUYER, "bank", "bank deposit <amount> <bank>", Map.of("amount", "100.00", "bank", bank), InventoryPort.NONE);
        moneyLine(deposit, "Deposit credit", 9975);
        w.engine.execute(BUYER, "bank deposit 100.00 co", InventoryPort.NONE);
        w.advance(1000);
        ActionPreview withdrawal = preview(w, BUYER, "bank", "bank withdraw <amount> <bank>", Map.of("amount", "5.00", "bank", bank), InventoryPort.NONE);
        moneyLine(withdrawal, "Wallet credit", 500); moneyLine(withdrawal, "Total deposit debit", 525);
        eq("0", w.data.banks.get(bank).deposits.get(BUYER.id().toString()).pendingInterest);
        w.engine.execute(BUYER, "bank withdraw 5.00 co", InventoryPort.NONE);
        eq(9549, w.engine.banking.balance(BUYER, bank, null).balance()); eq(500, w.engine.balance(BUYER.account()));
        w.engine.banking.associate(FOREIGN, bank);
        Map<String, String> application = Map.of("bank", bank, "principal", "10.00", "periods", "4",
                "collateralOrNone", "none", "noneBalanceCollateralOrBoth", "none", "autoOrManual", "manual");
        ActionPreview request = preview(w, FOREIGN, "bank",
                "loan request <bank> <principal> <periods> <collateralOrNone> <noneBalanceCollateralOrBoth> <autoOrManual>", application, InventoryPort.NONE);
        moneyLine(request, "Total debit now", 0); moneyLine(request, "Future net disbursement if approved", 990);
        String loan = w.engine.banking.requestLoan(FOREIGN, bank, 1000, 4, null, false, false, false);
        ActionPreview approval = preview(w, OWNER, "loans", "loan approve <loanId>", Map.of("loanId", loan), InventoryPort.NONE);
        moneyLine(approval, "Bank cash debit / borrower credit", 990);
        w.engine.banking.approve(OWNER, loan);
        w.set(FOREIGN, 2000); w.advance(1000);
        ActionPreview repayment = preview(w, FOREIGN, "loans", "loan repay <loanId> <amountOrAll>",
                Map.of("loanId", loan, "amountOrAll", "all"), InventoryPort.NONE);
        moneyLine(repayment, "Total debit", 1050); moneyLine(repayment, "Interest paid first", 50);
        eq(0, w.data.loans.get(loan).interest);
        w.engine.banking.repayAll(FOREIGN, loan);
        eq("REPAID", w.data.loans.get(loan).status); eq(950, w.engine.balance(FOREIGN.account()));
    }

    public static void propertyQuotesShareWalletCashBankFeesAndReservePreflight() {
        for (String suffix : List.of("", " cash", " bank <bank>", " bank <bank> cash")) {
            TestWorld w = new TestWorld();
            String bank = w.bank(1000, 0, 0);
            w.engine.banking.rates(OWNER, bank, 0, 0, 0, 0, 100);
            w.set(BUYER, 9000);
            w.engine.banking.deposit(BUYER, bank, 8000);
            Inventory inventory = new Inventory().item(0, TEN, 3);
            long price = suffix.isEmpty() ? 1000 : suffix.equals(" cash") ? 4000 : suffix.endsWith("cash") ? 10_000 : 7000;
            w.engine.property.list(OWNER, CLAIM, price);
            Map<String, String> fields = suffix.contains("bank") ? Map.of("chunkKeyOrHere", CLAIM, "bank", bank) : Map.of("chunkKeyOrHere", CLAIM);
            String template = "property buy <chunkKeyOrHere>" + suffix;
            ActionPreview quote = preview(w, BUYER, "property", template, fields, inventory);
            moneyLine(quote, "Total purchase cost", price);
            moneyLine(quote, "Bank withdrawal fee", suffix.contains("bank") ? 100 : 0);
            moneyLine(quote, "Physical cash deposited into wallet", suffix.endsWith("cash") ? 3000 : 0);
            check(w.data.valuations.isEmpty(), "preview created a valuation");
            ActionSelection selection = ActionSelection.form("economy:property", template, fields);
            w.engine.execute(BUYER, selection.rendered(), inventory);
            eq(0, w.engine.balance(BUYER.account())); eq(price, w.engine.balance(OWNER.account()));
            eq(BUYER.account(), w.governance.claims.get(CLAIM).ownerAccount());
            eq(suffix.contains("bank") ? 1900 : 8000, w.engine.banking.balance(BUYER, bank, null).balance());
            eq(suffix.endsWith("cash") ? 0 : 3, inventory.count(TEN));
        }
        TestWorld blocked = new TestWorld(c -> c.minimumBankReserveBps = 10_000);
        String bank = blocked.bank(0, 100, 0);
        blocked.set(BUYER, 1000); blocked.engine.banking.deposit(BUYER, bank, 1000);
        blocked.engine.property.list(OWNER, CLAIM, 500);
        blocked.advance(500);
        expect(UserError.class, () -> preview(blocked, BUYER, "property", "property buy <chunkKeyOrHere> bank <bank>",
                Map.of("chunkKeyOrHere", CLAIM, "bank", bank), InventoryPort.NONE));
    }

    public static void bankCreationAndNonfinancialTermsAreExplicitFutureObligations() {
        TestWorld w = new TestWorld(c -> c.bankCreationFeeCents = 100);
        w.set("company:co", 2000);
        ActionPreview creation = preview(w, OWNER, "bank", "bank create <company> <name> <seedCapital> <depositBps> <loanBps>",
                Map.of("company", "co", "name", "Community", "seedCapital", "10.00", "depositBps", "100", "loanBps", "500"), InventoryPort.NONE);
        moneyLine(creation, "Total debit", 1100);
        w.engine.banking.create(OWNER, "co", "Community", 1000, 100, 500);
        eq(900, w.engine.balance("company:co")); eq(1000, w.engine.balance("bank:co"));
        ActionPreview terms = preview(w, OWNER, "bank", "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>",
                Map.of("bank", "co", "depositBps", "200", "loanBps", "700", "originationBps", "100",
                        "depositFee", "1.00", "withdrawalFee", "2.00"), InventoryPort.NONE);
        moneyLine(terms, "Total debit now", 0);
        check(terms.lines().stream().anyMatch(line -> line.value().fallback().contains("Existing interest contracts remain unchanged")), "future/current terms distinction missing");
    }

    public static void responsiveFieldConstraintsRejectInvalidInputAndKeepAllAlternatives() {
        Fixture f = new Fixture();
        FormSchema deposit = form(f.w, OWNER, "bank", "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>",
                Map.of("bank", "co", "depositBps", "201", "loanBps", "100", "originationBps", "0", "depositFee", "-1", "withdrawalFee", "0"));
        var values = deposit.fields().stream().collect(Collectors.toMap(dev.statecraft.api.form.FormField::key, dev.statecraft.api.form.FormField::value));
        check(FormValidation.errors(deposit, values).keySet().containsAll(Set.of("depositBps", "depositFee")), "money/rate constraints failed to explain invalid values");
        FormSchema shares = form(f.w, BUYER, "stock", "stock buy <listingId> <sharesOrAll>", Map.of("listingId", f.stock, "sharesOrAll", "all"));
        var count = shares.fields().stream().filter(field -> field.key().equals("sharesOrAll")).findFirst().orElseThrow();
        eq(f.w.data.stocks.get(f.stock).remaining, count.constraints().maximum());
        check(count.constraints().error("all", count.label()).isEmpty(), "all alternative was rejected");
        check(count.constraints().error("1.5", count.label()).isPresent(), "fractional shares were accepted");
        check(count.constraints().error("999999", count.label()).isPresent(), "listing quantity bound was omitted");
        FormSchema loan = form(f.w, OWNER, "loans", "loan repay <loanId> <amountOrAll>", Map.of("loanId", f.loan, "amountOrAll", "all"));
        var amount = loan.fields().stream().filter(field -> field.key().equals("amountOrAll")).findFirst().orElseThrow();
        eq(f.w.engine.banking.previewLoan(OWNER, f.loan).total(), amount.constraints().maximum());
        check(amount.constraints().error("0.001", amount.label()).isPresent(), "excess monetary precision was accepted");
        FormSchema suggestion = form(f.w, OWNER, "hub", "hub suggest <unitPrice> <reason>", Map.of("unitPrice", "1.00", "reason", "x".repeat(257)));
        eq(256, suggestion.fields().stream().filter(field -> field.key().equals("reason")).findFirst().orElseThrow().constraints().maxLength());
        FormSchema offer = form(f.w, OWNER, "market", "market list <quantity> <unitPrice>", Map.of("quantity", "2", "unitPrice", "1.00"));
        eq(Money.MAX / 2, offer.fields().stream().filter(field -> field.key().equals("unitPrice")).findFirst().orElseThrow().constraints().maximum());
    }

    public static void menuIntentsDistinguishMoneyQueriesNavigationAndFutureTerms() {
        registerMenus();
        Set<String> monetary = Set.of("transfer", "cash deposit", "cash withdraw", "hub sell", "market list", "market buy",
                "property buy", "company pay", "company dividend", "bank create", "bank deposit", "bank withdraw",
                "stock list", "stock buy", "tax pay", "loan approve", "loan repay");
        for (var menu : MenuRegistry.pages()) {
            if (!menu.id().startsWith("economy:")) continue;
            for (var action : menu.actions()) {
                boolean financial = monetary.stream().anyMatch(prefix -> action.command().startsWith(prefix + " "));
                eq(financial, action.financial());
                if (financial) eq(ActionIntent.MUTATION, action.intent());
                if (action.command().startsWith("gui ")) eq(ActionIntent.NAVIGATION, action.intent());
                if (action.command().startsWith("loan autopay ") || action.command().startsWith("loan request ")
                        || action.command().startsWith("bank terms ")) eq(ActionIntent.MUTATION, action.intent());
            }
        }
        check(!MenuRegistry.get("economy:detail").listed(), "generic details leaked into the navigation catalog");
        TestWorld w = new TestWorld();
        expect(UserError.class, () -> preview(w, ADMIN, "bank", "admin mint <account> <amount>",
                Map.of("account", ADMIN.account(), "amount", "1.00"), InventoryPort.NONE));
        expect(UserError.class, () -> new EconomyPresentation(w.engine).preview(ADMIN,
                ActionSelection.raw("economy:bank", "admin mint me 1.00"), InventoryPort.NONE));
    }

    public static void longDisplayDataStaysBoundedAndGuideUsesLoadedServerContent() {
        registerMenus();
        TestWorld w = new TestWorld();
        List<EconomyData.Delivery> deliveries = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            EconomyData.Delivery delivery = new EconomyData.Delivery();
            delivery.id = new java.util.UUID(0, 5000 + i).toString();
            delivery.item = new ItemLot("example:" + "item".repeat(150), "{data:1}", 64, 64);
            delivery.reason = "Long retained delivery ".repeat(300);
            delivery.createdAt = w.clock.millis();
            deliveries.add(delivery);
        }
        w.data.deliveries.put(OWNER.id().toString(), deliveries);
        w.engine.setCommandHooks(new EconomyCommands.Hooks() {
            @Override public String guide() { return "Loaded recipe: test:custom_atm"; }
        });
        EconomyPresentation ui = new EconomyPresentation(w.engine);
        String before = snapshot(w);
        UiView page = ui.view(new UiContext(OWNER, UiQuery.page("economy:deliveries")));
        eq(20, page.rows().size()); check(page.more(), "bounded delivery page lost further entries");
        page.rows().forEach(row -> check(row.title().fallback().length() <= 256 && row.detail().fallback().length() <= 2048, "unbounded row text"));
        for (var row : page.rows()) ui.view(new UiContext(OWNER, UiQuery.detail(row.entity())));
        check(ui.view(new UiContext(OWNER, UiQuery.page("economy:guide"))).body().fallback().contains("test:custom_atm"), "guide discarded loaded recipe content");
        eq(before, snapshot(w));
    }

    public static void pendingInitialGrantsCannotChangeAReviewedPaymentAtSubmission() {
        registerMenus();
        TestWorld w = new TestWorld(config -> config.initialPlayerBalanceCents = 100);
        Actor newcomer = actor(800, "Uninitialized", false, 0);
        w.engine.taxes.assess(newcomer.account(), "nation:n1", "n1", "incomeTaxBps", 100, "offline debt");
        EconomyPresentation ui = new EconomyPresentation(w.engine);
        String before = snapshot(w);
        UiView view = ui.view(new UiContext(newcomer, UiQuery.page("economy:atm")));
        var initialize = view.actions().stream().filter(action -> action.template().equals("balance me")).findFirst().orElseThrow();
        expect(UserError.class, () -> preview(w, newcomer, "tax", "tax pay <amountOrAll> <account>",
                Map.of("amountOrAll", "all", "account", newcomer.account()), InventoryPort.NONE));
        eq(before, snapshot(w));
        w.engine.execute(newcomer, initialize.selection().rendered(), InventoryPort.NONE);
        eq(100, w.engine.balance(newcomer.account()));
        ActionPreview quote = preview(w, newcomer, "tax", "tax pay <amountOrAll> <account>",
                Map.of("amountOrAll", "all", "account", newcomer.account()), InventoryPort.NONE);
        moneyLine(quote, "Total debit", 100);
        w.engine.execute(newcomer, "tax pay all me", InventoryPort.NONE);
        eq(0, w.engine.balance(newcomer.account())); eq(0, w.engine.taxes.arrears(newcomer.account()).signum());
    }

    public static void hereReviewsBindTheResolvedChunkIncludingItsDimension() {
        TestWorld w = new TestWorld();
        String adjacent = "minecraft:overworld|2|0", otherDimension = "minecraft:the_nether|0|0";
        for (String key : List.of(adjacent, otherDimension)) {
            var chunk = dev.statecraft.api.ChunkKey.parse(key);
            w.governance.claims.put(key, new dev.statecraft.api.GovernanceAccess.ClaimView(
                    key, chunk.dimension(), chunk.x(), chunk.z(), "c1", "s1", "n1", OWNER.account(), 0, 0));
        }
        for (String key : List.of(CLAIM, adjacent, otherDimension)) w.engine.property.list(OWNER, key, 100);
        w.set(BUYER, 1000);
        Actor origin = at(BUYER, CLAIM), moved = at(BUYER, adjacent), dimensionChanged = at(BUYER, otherDimension);
        String template = "property buy <chunkKeyOrHere>";
        Map<String, String> here = Map.of("chunkKeyOrHere", "here");
        ActionPreview first = preview(w, origin, "property", template, here, InventoryPort.NONE);
        ActionPreview next = preview(w, moved, "property", template, here, InventoryPort.NONE);
        ActionPreview crossDimension = preview(w, dimensionChanged, "property", template, here, InventoryPort.NONE);
        check(!first.fingerprint().equals(next.fingerprint()), "here silently retargeted an equally priced/owned adjacent claim");
        check(!first.fingerprint().equals(crossDimension.fingerprint()), "here omitted the dimension from its material identity");
        eq(CLAIM, first.lines().stream().filter(line -> line.label().fallback().equals("Property") && line.material()).findFirst().orElseThrow().value().fallback());
        eq(otherDimension, crossDimension.lines().stream().filter(line -> line.label().fallback().equals("Property")).findFirst().orElseThrow().value().fallback());
        Map<String, String> explicit = Map.of("chunkKeyOrHere", CLAIM);
        eq(preview(w, origin, "property", template, explicit, InventoryPort.NONE).fingerprint(),
                preview(w, dimensionChanged, "property", template, explicit, InventoryPort.NONE).fingerprint());
        w.engine.execute(moved, ActionSelection.form("economy:property", template, here).rendered(), InventoryPort.NONE);
        eq(BUYER.account(), w.governance.claims.get(adjacent).ownerAccount());
        eq(OWNER.account(), w.governance.claims.get(CLAIM).ownerAccount());
        eq(OWNER.account(), w.governance.claims.get(otherDimension).ownerAccount());
        eq(900, w.engine.balance(BUYER.account()));
    }

    public static void heldStackCountIdentityAndNbtAreMaterialForSalesAndListings() {
        for (String template : List.of("hub sell <quantity>", "market list <quantity> <unitPrice>")) {
            TestWorld w = new TestWorld();
            String page = template.startsWith("hub") ? "hub" : "market";
            Map<String, String> fields = page.equals("hub") ? Map.of("quantity", "2") : Map.of("quantity", "2", "unitPrice", "1.00");
            Inventory inventory = new Inventory().item(0, new ItemLot("minecraft:wheat", "{Custom:1}", 4, 64));
            ActionPreview first = preview(w, OWNER, page, template, fields, inventory);
            inventory.item(0, new ItemLot("minecraft:wheat", "{Custom:1}", 5, 64));
            check(!first.fingerprint().equals(preview(w, OWNER, page, template, fields, inventory).fingerprint()),
                    "held count changed without requiring review: " + template);
            inventory.item(0, new ItemLot("minecraft:wheat", "{Custom:2}", 4, 64));
            check(!first.fingerprint().equals(preview(w, OWNER, page, template, fields, inventory).fingerprint()),
                    "held NBT changed without requiring review: " + template);
            inventory.item(0, new ItemLot("minecraft:diamond", "{Custom:1}", 4, 64));
            check(!first.fingerprint().equals(preview(w, OWNER, page, template, fields, inventory).fingerprint()),
                    "held item identity changed without requiring review: " + template);
            inventory.item(0, new ItemLot("minecraft:wheat", "{Custom:1}", 5, 64));
            preview(w, OWNER, page, template, fields, inventory);
            w.engine.execute(OWNER, ActionSelection.form("economy:" + page, template, fields).rendered(), inventory);
            eq(3, inventory.count("minecraft:wheat")); eq("{Custom:1}", inventory.held().snbt());
            if (page.equals("hub")) eq(50, w.engine.balance(OWNER.account()));
            else {
                EconomyData.MarketListing listing = w.data.market.values().iterator().next();
                eq(2, listing.remaining); eq("{Custom:1}", listing.item.snbt());
            }
        }
    }

    public static void cashCountsAndNbtAreBoundEvenWhenTheQuotedWithdrawalIsUnchanged() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 1000);
        Inventory inventory = new Inventory().item(0, ONE, 2);
        Map<String, String> values = Map.of("amount", "1.00", "account", OWNER.account());
        String template = "cash withdraw <amount> <account>";
        ActionPreview first = preview(w, OWNER, "atm", template, values, inventory);
        inventory.item(0, ONE, 3);
        ActionPreview countChanged = preview(w, OWNER, "atm", template, values, inventory);
        moneyLine(first, "Total debit", 100); moneyLine(countChanged, "Total debit", 100);
        check(!first.fingerprint().equals(countChanged.fingerprint()), "cash stack count was omitted from review identity");
        inventory.item(0, new ItemLot(ONE, "{Custom:1}", 2, 64));
        ActionPreview tagged = preview(w, OWNER, "atm", template, values, inventory);
        moneyLine(tagged, "Total debit", 100);
        check(!first.fingerprint().equals(tagged.fingerprint()), "cash metadata changed without requiring review");
        w.engine.execute(OWNER, ActionSelection.form("economy:atm", template, values).rendered(), inventory);
        eq(900, w.engine.balance(OWNER.account())); eq(3, inventory.count(ONE)); eq("{Custom:1}", inventory.held().snbt());
    }

    public static void listingReviewsBindTheirFutureTaxOriginEvenWithIdenticalCurrentFees() {
        TestWorld w = new TestWorld();
        Actor moved = at(OWNER, BUYER_CLAIM);
        Inventory inventory = new Inventory().item(0, "minecraft:wheat", 4);
        for (String template : List.of("market list <quantity> <unitPrice>", "stock list <company> <shares> <unitPrice>")) {
            String page = template.startsWith("market") ? "market" : "stock";
            Map<String, String> values = page.equals("market") ? Map.of("quantity", "1", "unitPrice", "1.00")
                    : Map.of("company", "co", "shares", "1", "unitPrice", "1.00");
            ActionPreview first = preview(w, OWNER, page, template, values, inventory);
            ActionPreview next = preview(w, moved, page, template, values, inventory);
            check(!first.fingerprint().equals(next.fingerprint()), "listing tax origin silently changed: " + template);
            eq(BUYER_CLAIM, next.lines().stream().filter(line -> line.label().fallback().equals("Source tax location")).findFirst().orElseThrow().value().fallback());
            w.engine.execute(moved, ActionSelection.form("economy:" + page, template, values).rendered(), inventory);
        }
        eq(BUYER_CLAIM, w.data.market.values().iterator().next().sourceChunk);
        eq(BUYER_CLAIM, w.data.stocks.values().iterator().next().sourceChunk);
    }

    private static final class Fixture {
        final TestWorld w = new TestWorld(c -> { c.marketLifetimeMillis = 100_000; c.stockLifetimeMillis = 100_000; c.propertyListingLifetimeMillis = 100_000; });
        final Inventory inventory = new Inventory().item(0, "minecraft:wheat", 40).item(1, TEN, 2);
        final EconomyPresentation ui;
        final String market, stock, loan, application;
        Fixture() {
            registerMenus();
            w.bank(100_000, 0, 0);
            w.set("company:co", 10_000);
            w.set(OWNER, 100_000); w.set(BUYER, 100_000);
            w.engine.banking.deposit(OWNER, "co", 10_000);
            w.engine.banking.deposit(BUYER, "co", 10_000);
            loan = w.loan(OWNER, "co", 1000, null, false, false, false);
            application = w.engine.banking.requestLoan(OWNER, "co", 1000, 4, null, false, false, false);
            market = w.engine.commerce.listMarket(OWNER, 4, 100, inventory);
            stock = w.engine.commerce.listStock(OWNER, "co", 4, 100);
            w.engine.property.list(OWNER, CLAIM, 1000);
            w.governance.claims.put("minecraft:overworld|2|0", new dev.statecraft.api.GovernanceAccess.ClaimView(
                    "minecraft:overworld|2|0", "minecraft:overworld", 2, 0, "c1", "s1", "n1", OWNER.account(), 0, 0));
            w.governance.companies.put("other", new Government.Company());
            w.set("company:other", 1000);
            w.engine.taxes.assess(OWNER.account(), "nation:n1", "n1", "incomeTaxBps", 100, "test");
            ui = new EconomyPresentation(w.engine);
        }
    }

    private static ActionPreview preview(TestWorld w, Actor actor, String page, String template, Map<String, String> values, InventoryPort inventory) {
        registerMenus();
        String before = snapshot(w), items = encode(inventory.snapshot());
        try {
            return new EconomyPresentation(w.engine).preview(actor, ActionSelection.form("economy:" + page, template, values), inventory);
        } finally {
            eq(before, snapshot(w)); eq(items, encode(inventory.snapshot()));
        }
    }

    private static UiView detail(TestWorld w, Actor actor, EntityRef.Kind kind, String id) {
        registerMenus();
        String before = snapshot(w);
        try { return new EconomyPresentation(w.engine).view(new UiContext(actor, UiQuery.detail(ref(kind, id)))); }
        finally { eq(before, snapshot(w)); }
    }

    private static EntityRef ref(EntityRef.Kind kind, String id) { return new EntityRef("economy", kind, id); }
    private static Actor at(Actor actor, String key) {
        var chunk = dev.statecraft.api.ChunkKey.parse(key);
        return new Actor(actor.id(), actor.name(), actor.admin(), chunk.dimension(), chunk.x(), chunk.z());
    }
    private static void moneyLine(ActionPreview preview, String label, long expected) {
        eq(Money.format(expected), preview.lines().stream().filter(line -> line.label().fallback().equals(label)).findFirst().orElseThrow().value().fallback());
    }
    private static void validateActions(UiView view) {
        for (var action : view.actions()) {
            action.selection().registeredAction();
            check(new CommandTemplate(action.template()).fields().containsAll(action.values().keySet()), "contextual seeds do not belong to the registered template");
            if (!action.enabled()) check(!action.disabledReason().fallback().isBlank(), "disabled action lacks a reason");
        }
    }
    private static void validateSeeds(TestWorld w, Actor actor, UiView view) {
        for (var action : view.actions()) {
            if (!action.enabled() || action.values().isEmpty()) continue;
            FormSchema schema = form(w, actor, action.page().substring(8), action.template(), action.values());
            for (var seed : action.values().entrySet()) {
                eq(seed.getValue(), schema.fields().stream().filter(field -> field.key().equals(seed.getKey())).findFirst().orElseThrow().value());
            }
        }
    }
    private static Set<String> rowIds(UiView first, UiView second) {
        return java.util.stream.Stream.concat(first.rows().stream(), second.rows().stream()).map(row -> row.entity().id()).collect(Collectors.toSet());
    }
    private static FormSchema form(TestWorld w, Actor actor, String page, String command, Map<String, String> values) {
        FormContext context = new FormContext(actor, "economy:" + page, command, values, FormQuery.INITIAL);
        FormBuilder form = new FormBuilder(context);
        new EconomyForms(w.engine).describe(context, form);
        return form.build();
    }
    private static void registerMenus() {
        if (MenuRegistry.pages().stream().anyMatch(page -> page.id().equals("economy:atm"))) return;
        try {
            var method = Class.forName("dev.statecraft.economy.forge.EconomyMenus").getDeclaredMethod("register");
            method.setAccessible(true); method.invoke(null);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static String snapshot(TestWorld w) {
        return encode(w.data) + encode(w.governance.claims) + encode(w.governance.companies)
                + encode(w.governance.reservations) + encode(w.governance.messages) + w.dirty + ":" + w.clock.now;
    }
    private static String encode(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Iterable<?> items) {
            List<String> result = new ArrayList<>(); items.forEach(item -> result.add(encode(item))); return result.toString();
        }
        if (value instanceof java.util.UUID) return value.toString();
        List<String> fields = new ArrayList<>();
        for (var field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            try { field.setAccessible(true); fields.add(field.getName() + "=" + encode(field.get(value))); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        return value.getClass().getSimpleName() + fields;
    }
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (var method : EconomyPresentationScenarios.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) continue;
            try { method.invoke(null); count++; }
            catch (java.lang.reflect.InvocationTargetException failure) { throw new AssertionError(method.getName(), failure.getCause()); }
        }
        System.out.println(count + " deterministic economy presentation scenarios passed.");
    }
}
