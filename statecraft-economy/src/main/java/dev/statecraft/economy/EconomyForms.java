package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.GovernanceAccess.ClaimView;
import dev.statecraft.api.GovernanceAccess.CompanyView;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormConstraints;
import dev.statecraft.api.form.FormProvider;
import dev.statecraft.api.ui.DisplayText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only suggestions; execution rechecks every permission, price, and financial condition. */
public final class EconomyForms implements FormProvider {
    private static final Set<String> OPEN_LOANS = Set.of("REQUESTED", "ACTIVE", "DEFAULTED");
    private static final Set<String> OWED_LOANS = Set.of("ACTIVE", "DEFAULTED");
    private final EconomyEngine engine;
    private final EconomyDisplay display;

    public EconomyForms(EconomyEngine engine) {
        this.engine = Objects.requireNonNull(engine);
        display = new EconomyDisplay(engine);
    }

    @Override public void describe(FormContext context, FormBuilder form) {
        if (!context.namespace().equals("economy")) return;
        engine.ledger.thread();
        List<String> words = context.words();
        int start = words.size() > 2 && words.get(0).equalsIgnoreCase("page") ? 2 : 0;
        String family = word(words, start);
        String action = word(words, start + 1);
        if (family.equals("bank") && action.equals("loan")) {
            family = "loan";
            action = word(words, start + 2);
        }
        if (family.equals("vault")) family = "company";
        if (family.equals("chunk")) family = "property";
        Actor actor = context.actor();

        companies(actor, form, family, action);
        accounts(actor, form);
        destinations(actor, form, family);
        properties(actor, form, family, action);
        listings(actor, form, family, action);
        banks(actor, form, family, action);
        loans(actor, form, family, action);
        collateral(actor, form);
        searchChoices(form, family);
        quantitiesAndAmounts(form, family, action);
        constraints(actor, form, family, action);
    }

    private void accounts(Actor actor, FormBuilder form) {
        for (String field : List.of("account", "fromAccount", "companyAccount")) {
            if (!form.has(field)) continue;
            Map<String, String> candidates = new LinkedHashMap<>();
            candidates.put(actor.account(), display.account(actor, actor.account()));
            for (FormChoice prior : form.choices(field)) {
                String account = resolve(actor, prior.value());
                if (account != null) candidates.putIfAbsent(account, playerLabel(account, prior.label()));
            }
            engine.governance.governments().forEach(g -> candidates.put(g.account(), display.account(g.account())));
            engine.governance.companies().forEach(c -> candidates.put(c.account(), display.account(c.account())));
            engine.data.banks.values().stream().filter(b -> !b.closed)
                    .forEach(b -> candidates.put(b.account(), display.account(b.account())));
            if (actor.admin()) {
                engine.data.accounts.keySet().forEach(account -> candidates.putIfAbsent(account, playerLabel(account, account)));
            }
            List<FormChoice> choices = new ArrayList<>();
            candidates.forEach((account, label) -> {
                if ((!field.equals("companyAccount") || account.startsWith("company:")) && authorized(actor, account)) {
                    choices.add(option(account, label, "Balance " + Money.format(engine.balance(account))));
                }
            });
            String saved = engine.data.selectedAccounts.getOrDefault(actor.id().toString(), actor.account());
            String selected = resolve(actor, saved);
            String preferred = selected != null && choices.stream().anyMatch(choice -> choice.value().equals(selected))
                    ? selected : actor.account();
            form.choice(field, field.equals("companyAccount") ? "Company treasury" : "Authorized account",
                    "Only accounts you currently control. Permissions and bank reserves are rechecked on submit.",
                    choices, preferred, List.of(), false);
        }
    }

    private void companies(Actor actor, FormBuilder form, String family, String action) {
        if (!form.has("company")) return;
        boolean shares = family.equals("stock") && action.equals("list");
        boolean creatingBank = family.equals("bank") && action.equals("create");
        List<FormChoice> choices = new ArrayList<>();
        for (CompanyView company : engine.governance.companies()) {
            if (shares) {
                long available = engine.governance.availableShares(company.id(), actor.id());
                if (available > 0) choices.add(option(company.id(), display.company(company.id()), available + " unreserved shares"));
            } else if (authorized(actor, company.account())) {
                if (creatingBank && (engine.data.banks.containsKey(company.id())
                        || !canFundBank(company.account()))) continue;
                String detail = "Company treasury " + Money.format(engine.balance(company.account()));
                choices.add(option(company.id(), display.company(company.id()), detail));
            }
        }
        String preferred = engine.data.selectedAccounts.getOrDefault(actor.id().toString(), "");
        if (preferred.startsWith("company:")) preferred = preferred.substring(8);
        form.choice("company", shares ? "Company shares you own" : "Managed company",
                creatingBank ? "Companies without an existing bank only; the treasury must cover minimum seed capital and the creation fee."
                        : shares ? "Shareholders may list their own available shares without being company managers."
                        : "Company treasury permission is required; ordinary membership is not enough.",
                choices, preferred, List.of(), false);
    }

    private boolean canFundBank(String account) {
        long balance = engine.balance(account);
        return balance >= engine.config.bankCreationFeeCents
                && balance - engine.config.bankCreationFeeCents >= engine.config.minimumBankReserveCents;
    }

    private void destinations(Actor actor, FormBuilder form, String family) {
        for (String field : List.of("toAccount", "recipient")) {
            if (!form.has(field)) continue;
            Map<String, FormChoice> choices = new LinkedHashMap<>();
            engine.data.playerNames.forEach((id, name) ->
                    choices.put("player:" + id, option("player:" + id, display.account("player:" + id), "Personal account")));
            for (FormChoice prior : form.choices(field)) {
                String account = resolve(actor, prior.value());
                if (account != null && account.startsWith("player:")) {
                    choices.putIfAbsent(account, option(account, playerLabel(account, prior.label()), "Personal account"));
                }
            }
            choices.put(actor.account(), option(actor.account(), display.account(actor, actor.account()), "Your wallet"));
            engine.governance.governments().forEach(g ->
                    choices.put(g.account(), option(g.account(), display.account(g.account()), DisplayText.words(g.kind().name()) + " treasury")));
            engine.governance.companies().forEach(c ->
                    choices.put(c.account(), option(c.account(), display.account(c.account()), "Company treasury")));
            String source = form.has("fromAccount") ? form.value("fromAccount")
                    : family.equals("company") && !form.value("company").isEmpty() ? "company:" + form.value("company") : actor.account();
            choices.remove(source);
            form.choice(field, "Recipient", "Known players and public treasuries; no private balances or internal escrow.",
                    choices.values(), "", List.of("fromAccount", "company"), false);
        }
    }

    private void properties(Actor actor, FormBuilder form, String family, String action) {
        if (!family.equals("property") || !form.has("chunkKeyOrHere")) return;
        List<FormChoice> choices = new ArrayList<>();
        if (action.equals("buy") || action.equals("delist")) {
            for (EconomyData.PropertyListing listing : engine.data.properties.values()) {
                ClaimView claim = engine.governance.claim(listing.chunk).orElse(null);
                boolean allowed;
                if (action.equals("buy")) {
                    allowed = listing.expiresAt > engine.clock.millis() && claim != null
                            && claim.ownerAccount().equals(listing.ownerAccount)
                            && !listing.ownerAccount.equals(actor.account()) && !engine.banking.encumbers(listing.chunk)
                            && (actor.admin() || engine.governance.mayBuyProperty(actor.id(), listing.chunk));
                } else {
                    allowed = actor.admin() || listing.ownerAccount.equals(actor.account())
                            || engine.governance.maySellProperty(actor.id(), listing.chunk);
                }
                if (allowed) choices.add(option(listing.chunk, chunkLabel(actor, listing.chunk),
                        Money.format(listing.price) + "; seller " + display.account(listing.ownerAccount)
                                + (listing.expiresAt <= engine.clock.millis() ? "; expired listing" : "")));
            }
        } else {
            for (ClaimView claim : engine.governance.claims()) {
                if (!action.equals("list") || (!engine.isClaimEncumbered(claim.key())
                        && (actor.admin() || engine.governance.maySellProperty(actor.id(), claim.key())))) {
                    choices.add(option(claim.key(), chunkLabel(actor, claim.key()), "Owner " + display.account(claim.ownerAccount()) + cachedValue(claim.key())));
                }
            }
        }
        form.choice("chunkKeyOrHere", action.equals("buy") ? "Property for sale" : "Claimed property",
                "Keeps the selected location even if you move. Ownership, eligibility and commitments are checked again on submit.",
                choices, actor.chunkKey(), List.of(), false);
    }

    private void listings(Actor actor, FormBuilder form, String family, String action) {
        if (!form.has("listingId")) return;
        List<FormChoice> choices = new ArrayList<>();
        boolean cancel = action.equals("cancel");
        if (family.equals("market")) {
            for (EconomyData.MarketListing listing : engine.data.market.values()) {
                if (listing.remaining <= 0 || (cancel ? !actor.admin() && !listing.seller.equals(actor.id().toString())
                        : listing.expiresAt <= engine.clock.millis()
                            || action.equals("buy") && listing.seller.equals(actor.id().toString()))) continue;
                choices.add(option(listing.id, listing.remaining + " × " + display.item(listing.item),
                        Money.format(listing.unitPrice) + " each; seller " + display.player(listing.seller)
                                + (!listing.item.snbt().isEmpty() ? "; " + display.itemDetails(listing.item) : "")
                                + (listing.expiresAt <= engine.clock.millis() ? "; expired, reclaimable" : "")));
            }
        } else if (family.equals("stock")) {
            for (EconomyData.StockListing listing : engine.data.stocks.values()) {
                if (listing.remaining <= 0 || (cancel ? !actor.admin() && !listing.seller.equals(actor.id().toString())
                        : listing.expiresAt <= engine.clock.millis() || listing.seller.equals(actor.id().toString())
                            || engine.governance.company(listing.company).isEmpty())) continue;
                String company = display.company(listing.company);
                choices.add(option(listing.id, company + " - " + listing.remaining + " shares",
                        Money.format(listing.unitPrice) + " per share; seller " + display.player(listing.seller)
                                + (listing.expiresAt <= engine.clock.millis() ? "; expired, releasable" : "")));
            }
        } else return;
        form.choice("listingId", family.equals("stock") ? "Share listing" : "Item listing",
                cancel ? "Your outstanding listings, including expired escrow awaiting return. Operators can recover other listings."
                        : "Active listings only. Self-purchases are excluded; the price and available quantity can change.",
                choices, "", List.of(), false);
    }

    private void banks(Actor actor, FormBuilder form, String family, String action) {
        if (!form.has("bank")) return;
        boolean managed = family.equals("bank") && Set.of("report", "terms", "brand", "capital", "close", "loans").contains(action)
                || family.equals("loan") && Set.of("approve", "recover").contains(action);
        boolean funding = family.equals("property") && action.equals("buy");
        boolean withdrawing = funding || family.equals("bank") && action.equals("withdraw");
        boolean history = family.equals("bank") && Set.of("report", "balance", "loans").contains(action);
        String associated = engine.data.bankAssociations.getOrDefault(actor.id().toString(), "");
        List<FormChoice> choices = new ArrayList<>();
        for (EconomyData.Bank bank : engine.data.banks.values()) {
            if (bank.closed && !history || managed && !managedBank(actor, bank)) continue;
            EconomyData.Deposit own = bank.deposits.get(actor.id().toString());
            if (bank.closed && !managed && own == null && !managedBank(actor, bank)) continue;
            long fundedBalance = own == null ? 0 : own.balance;
            if (withdrawing) {
                try { fundedBalance = engine.banking.quoteWalletFunding(actor, bank.id, 1).balanceBefore(); }
                catch (UserError unavailable) { continue; }
            }
            if (funding && !eligibleSelectedProperty(form)) continue;
            if (family.equals("bank") && action.equals("deposit") && own == null
                    && bank.deposits.size() >= engine.config.maximumBankCustomers) continue;
            if (family.equals("bank") && action.equals("deposit") && bank.depositFeeCents == Money.MAX) continue;
            if (family.equals("loan") && action.equals("request") && (!bank.id.equals(associated)
                    || hasDefault(actor) || openLoans(bank.id) >= engine.config.maximumBankLoans)) continue;
            if (family.equals("bank") && action.equals("close")
                    && (engine.banking.liabilities(bank).signum() != 0 || openLoans(bank.id) != 0)) continue;
            String detail = "Deposit " + DisplayText.percent(bank.depositInterestBps) + "; loan " + DisplayText.percent(bank.loanInterestBps)
                    + " per " + DisplayText.duration(bank.interestPeriodMillis);
            if (withdrawing) detail = "your deposit " + Money.format(fundedBalance)
                    + "; withdrawal fee " + Money.format(bank.withdrawalFeeCents);
            if (bank.closed) detail += "; closed";
            choices.add(option(bank.id, display.bank(bank.id), detail));
        }
        form.choice("bank", managed ? "Managed bank" : withdrawing ? "Your bank deposits" : "Bank",
                funding ? "Choose a property first. Only your funded deposits are offered; final costs, fees and reserves are checked on submit."
                        : family.equals("loan") && action.equals("request") ? "Apply to your associated bank; associate first using the bank menu."
                        : managed ? "Only banks whose company treasury you currently control." : "Your associated eligible bank is preselected.",
                choices, associated, funding ? List.of("chunkKeyOrHere") : List.of(), false);
    }

    private boolean eligibleSelectedProperty(FormBuilder form) {
        String key = form.value("chunkKeyOrHere");
        return !key.isEmpty() && form.choices("chunkKeyOrHere").stream().anyMatch(choice -> choice.value().equals(key));
    }

    private void loans(Actor actor, FormBuilder form, String family, String action) {
        if (!family.equals("loan") || !form.has("loanId")) return;
        List<FormChoice> choices = new ArrayList<>();
        for (EconomyData.Loan loan : engine.data.loans.values()) {
            EconomyData.Bank bank = engine.data.banks.get(loan.bank);
            boolean borrower = loan.borrower.equals(actor.id().toString());
            boolean manager = bank != null && managedBank(actor, bank);
            boolean activeBank = bank != null && !bank.closed;
            boolean allowed = switch (action) {
                case "approve" -> activeBank && (actor.admin() || manager) && loan.status.equals("REQUESTED")
                        && loan.applicationExpiresAt > engine.clock.millis();
                case "repay" -> activeBank && (borrower || actor.admin()) && OWED_LOANS.contains(loan.status);
                case "recover" -> activeBank && (actor.admin() || manager) && loan.status.equals("DEFAULTED");
                case "cancel", "reject" -> (borrower || actor.admin() || activeBank && manager) && loan.status.equals("REQUESTED");
                case "autopay" -> borrower && OPEN_LOANS.contains(loan.status);
                default -> borrower || actor.admin() || manager;
            };
            if (!allowed || form.has("bank") && !loan.bank.equals(form.value("bank"))) continue;
            String status = loan.status.equals("REQUESTED") && loan.applicationExpiresAt <= engine.clock.millis()
                    ? "EXPIRED APPLICATION" : loan.status;
            choices.add(option(loan.id, display.bank(loan.bank) + " - " + DisplayText.words(status),
                    "Borrower " + display.player(loan.borrower) + "; principal " + Money.format(loan.principal)
                            + "; accrued interest " + Money.format(loan.interest) + "; current autopay " + (loan.autoPay ? "on" : "off")));
        }
        form.choice("loanId", "Loan", "Only loans you may use for this action. Shown interest is the stored snapshot, not a new accrual.",
                choices, "", List.of("bank"), false);
    }

    private void collateral(Actor actor, FormBuilder form) {
        if (!form.has("collateralOrNone")) return;
        boolean ready = !form.has("bank") || !form.value("bank").isEmpty();
        long principal = 0;
        String hint = "Only your unencumbered private claims. Cached valuations are informational; final lending limits are checked on submit.";
        String entered = form.value("principal");
        if (!entered.isBlank()) {
            try { principal = Money.positive(Money.parse(entered)); }
            catch (UserError invalid) { ready = false; hint = "Enter a valid positive principal before choosing collateral."; }
        }
        List<FormChoice> choices = new ArrayList<>();
        if (ready) {
            if (engine.config.allowUnsecuredLoans && engine.config.maximumUnsecuredLoanCents > 0
                    && (principal == 0 || principal <= engine.config.maximumUnsecuredLoanCents)) {
                choices.add(option("none", "None - unsecured application", "Subject to the server's unsecured-loan and borrower limits."));
            }
            if (engine.config.allowConsentedRepossession) {
                for (ClaimView claim : engine.governance.claims()) {
                    if (!claim.ownerAccount().equals(actor.account()) || !engine.governance.maySellProperty(actor.id(), claim.key())
                            || engine.isClaimEncumbered(claim.key())) continue;
                    EconomyData.Valuation value = engine.data.valuations.get(claim.key());
                    if (principal > 0 && value != null && value.nextRecalculationAt > engine.clock.millis()
                            && principal > Money.tax(value.value, engine.config.maximumLoanToValueBps)) continue;
                    choices.add(option(claim.key(), chunkLabel(actor, claim.key()), "Private collateral" + cachedValue(claim.key())));
                }
            }
        }
        form.choice("collateralOrNone", "Collateral", ready ? hint : hint.startsWith("Enter") ? hint : "Choose an eligible associated bank first.",
                choices, "none", List.of("bank", "principal"), false);
        if (form.has("noneBalanceCollateralOrBoth")) {
            List<FormChoice> consent = new ArrayList<>();
            consent.add(option("none", "No recovery consent", "A secured application cannot proceed until you explicitly consent to its collateral recovery."));
            String selected = form.value("collateralOrNone");
            if (selected.equals("none")) {
                if (engine.config.allowConsentedBalanceSeizure) consent.add(option("balance", "Personal-wallet recovery",
                        "After default, authorize recovery only from your own wallet, up to the remaining debt."));
            } else if (!selected.isEmpty() && engine.config.allowConsentedRepossession) {
                consent.add(option("collateral", "Named-collateral recovery", "Explicitly authorize repossession of only the selected claim after default."));
                if (engine.config.allowConsentedBalanceSeizure) consent.add(option("both", "Wallet and named-collateral recovery",
                        "Explicitly authorize both recovery methods, limited to the contracted debt and selected property."));
            }
            form.choice("noneBalanceCollateralOrBoth", "Recovery consent",
                    "Defaults to no recovery authority. For secured borrowing, explicitly choose collateral consent; none intentionally prevents submission.",
                    consent, "none", List.of("bank", "collateralOrNone"), false);
        }
    }

    private void quantitiesAndAmounts(FormBuilder form, String family, String action) {
        if (form.has("autoOrManual")) {
            form.choice("autoOrManual", "Repayment mode", "Manual payments by default; automatic payments require your explicit choice.",
                    List.of(option("manual", "Manual payments", "No automatic wallet deductions."),
                            option("auto", "Automatic payments", "Authorize scheduled payments from your wallet, subject to spending limits.")),
                    "manual", List.of("bank", "principal", "periods", "collateralOrNone"), false);
        }
        for (String field : List.of("quantityOrAll", "sharesOrAll")) {
            if (!form.has(field)) continue;
            long remaining = remaining(form, family);
            List<FormChoice> choices = new ArrayList<>();
            choices.add(option("1", "One", "Start with a single item/share; you may type another positive quantity."));
            choices.add(option("all", "All remaining", remaining > 0 ? remaining + " currently available; execution rechecks quantity." : "Uses the chosen listing's remaining quantity."));
            if (remaining > 1) choices.add(option(Long.toString(remaining), remaining + " remaining", "The currently displayed quantity, not an automatic all purchase."));
            form.choice(field, field.equals("sharesOrAll") ? "Shares to buy" : "Items to buy",
                    "Choose all or enter a positive whole quantity. Default is one, never the entire listing.",
                    choices, "1", List.of("listingId"), true);
        }
        if (form.has("amountOrAll")) {
            form.choice("amountOrAll", "Amount ($) or all", "No payment amount is preselected. Enter dollars, or explicitly select all.",
                    List.of(option("all", "All outstanding", "Pays the currently applicable debt/obligations; no fixed amount is promised."),
                            option("0.01", "One cent", "A small partial payment; you may type another amount.")),
                    "", List.of("loanId", "account", "bank"), true);
        }
        if (form.has("quantity")) form.text("quantity", "Quantity", "Positive whole item count from your held stack and matching inventory.", "1", false);
        if (form.has("shares")) {
            String company = form.value("company");
            String hint = "Positive whole share count; reserved shares are unavailable.";
            if (!company.isEmpty() && engine.governance.company(company).isPresent()) {
                hint = "Positive whole share count; the selected company's availability is shown in its dropdown.";
            }
            form.text("shares", "Shares", hint, "1", false, "company");
        }
        if (form.has("periods")) form.text("periods", "Repayment periods", "Between 1 and " + engine.config.maximumLoanPeriods + " financial periods.", "1", false, "bank");
        for (String field : List.of("amount", "price", "unitPrice", "budget", "principal", "seedCapital", "depositFee", "withdrawalFee")) {
            if (!form.has(field)) continue;
            String[] dependencies = field.equals("principal") ? new String[]{"bank"}
                    : field.equals("unitPrice") ? new String[]{"company", "quantity", "shares"}
                    : field.equals("seedCapital") || field.equals("budget") ? new String[]{"company"}
                    : field.equals("price") ? new String[]{"chunkKeyOrHere"}
                    : new String[]{"account", "companyAccount", "fromAccount", "toAccount", "recipient", "company", "bank", "loanId", "listingId"};
            form.text(field, FormBuilder.label(field) + " ($)", "Enter dollars with at most two decimal places. No new amount is filled automatically.", "", false, dependencies);
        }
        EconomyData.Bank bank = engine.data.banks.get(form.value("bank"));
        for (String field : List.of("depositBps", "loanBps", "originationBps")) {
            if (!form.has(field)) continue;
            int rate = 0;
            if (bank != null && family.equals("bank") && action.equals("terms")) {
                rate = field.equals("depositBps") ? bank.depositInterestBps : field.equals("loanBps") ? bank.loanInterestBps : bank.originationFeeBps;
            }
            int maximum = field.equals("depositBps") ? engine.config.maximumDepositInterestBps
                    : field.equals("loanBps") ? engine.config.maximumLoanInterestBps : engine.config.maximumOriginationFeeBps;
            Set<Integer> rates = new java.util.TreeSet<>(List.of(0, 25, 50, 100, 125, 200, 500, 1000));
            rates.add(rate);
            try { rates.add(Integer.parseInt(form.value(field))); } catch (NumberFormatException incomplete) { }
            form.choice(field, field.equals("depositBps") ? "Deposit interest" : field.equals("loanBps") ? "Loan interest" : "Origination fee",
                    (field.equals("originationBps") ? "One-time fee." : "Interest per financial period.")
                            + " Choose a percentage, or enter hundredths of a percent (100 = 1%).",
                    rates.stream().filter(value -> value >= 0 && value <= maximum)
                            .map(value -> option("" + value, DisplayText.percent(value),
                                    field.equals("originationBps") ? "Charged once when funded" : "Per financial period")).toList(),
                    Integer.toString(rate), List.of("bank"), true);
        }
    }

    private void searchChoices(FormBuilder form, String family) {
        if (form.has("companySearch")) {
            form.choice("companySearch", "Company search", "Choose a company or type part of a company/listing name.",
                    engine.governance.companies().stream().map(c -> option(c.id(), display.company(c.id()), "Company shares")).toList(),
                    "", List.of(), true);
        }
        if (form.has("itemSearch")) {
            Map<String, FormChoice> choices = new LinkedHashMap<>();
            for (FormChoice prior : form.choices("itemSearch")) {
                if (prior.value().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    choices.put(prior.value(), option(prior.value(), EconomyDisplay.registry(prior.value()), "Item; custom searches are supported."));
                }
            }
            engine.values.prices().forEach((id, price) -> choices.put(id, option(id, EconomyDisplay.registry(id),
                    engine.values.isCurrency(id) ? "Currency - not sellable at the Hub" : "Hub value " + Money.format(price) + " each")));
            form.choice("itemSearch", "Item price search", "Choose a priced item or enter search text.",
                    choices.values(), "", List.of(), true);
        }
        if (form.has("search") && family.equals("market")) {
            Map<String, FormChoice> choices = new LinkedHashMap<>();
            for (EconomyData.MarketListing listing : engine.data.market.values()) {
                if (listing.remaining <= 0 || listing.expiresAt <= engine.clock.millis()) continue;
                choices.putIfAbsent(listing.item.item(), option(listing.item.item(), EconomyDisplay.registry(listing.item.item()), "Search every listing of this item."));
                choices.put(listing.id, option(listing.id, listing.remaining + " × " + display.item(listing.item),
                        Money.format(listing.unitPrice) + " each; seller " + display.player(listing.seller)));
            }
            form.choice("search", "Marketplace search", "Choose an item/listing, or type part of an item or seller name.",
                    choices.values(), "", List.of(), true);
        }
    }

    private void constraints(Actor actor, FormBuilder form, String family, String action) {
        for (String key : form.keys()) {
            int limit = switch (key) {
                case "name", "branding" -> 64;
                case "reason" -> 256;
                case "search", "itemSearch", "companySearch" -> 80;
                case "bank", "company", "account", "companyAccount", "fromAccount", "toAccount", "recipient" -> 128;
                case "loanId", "listingId", "chunkKeyOrHere", "collateralOrNone" -> 256;
                default -> 2048;
            };
            form.constraints(key, FormConstraints.text(limit));
        }
        form.constraints("page", FormConstraints.integer(1, Integer.MAX_VALUE));
        form.constraints("quantity", FormConstraints.integer(1, 1_000_000));
        form.constraints("periods", FormConstraints.integer(1, engine.config.maximumLoanPeriods));
        form.constraints("depositBps", FormConstraints.integer(0, engine.config.maximumDepositInterestBps));
        form.constraints("loanBps", FormConstraints.integer(0, engine.config.maximumLoanInterestBps));
        form.constraints("originationBps", FormConstraints.integer(0, engine.config.maximumOriginationFeeBps));
        String company = form.value("company");
        long shares = company.isEmpty() || engine.governance.company(company).isEmpty() ? Money.MAX
                : engine.governance.availableShares(company, actor.id());
        form.constraints("shares", FormConstraints.integer(1, Math.max(1, shares)));
        long remaining = remaining(form, family);
        form.constraints("quantityOrAll", FormConstraints.integer(1, Math.max(1, Math.min(1_000_000, remaining))).or("all"));
        form.constraints("sharesOrAll", FormConstraints.integer(1, Math.max(1, remaining)).or("all"));
        for (String key : List.of("amount", "price", "unitPrice", "budget", "principal", "seedCapital", "depositFee", "withdrawalFee")) {
            if (!form.has(key)) continue;
            long minimum = Set.of("depositFee", "withdrawalFee").contains(key)
                    || key.equals("amount") && family.equals("tax") && action.equals("quote") ? 0 : 1;
            if (key.equals("seedCapital")) minimum = engine.config.minimumBankReserveCents;
            long maximum = Money.MAX;
            if (key.equals("unitPrice") && action.equals("list")) {
                try {
                    long quantity = Long.parseLong(form.value(family.equals("stock") ? "shares" : "quantity"));
                    if (quantity > 0) maximum = Money.MAX / quantity;
                } catch (NumberFormatException incomplete) { }
            }
            if (key.equals("principal")) {
                maximum = engine.banking.borrowingCapacity(actor);
                if (form.value("collateralOrNone").equals("none")) maximum = Math.min(maximum, engine.config.maximumUnsecuredLoanCents);
            }
            if (key.equals("amount") && family.equals("bank")) {
                EconomyData.Bank bank = engine.data.banks.get(form.value("bank"));
                if (bank != null && action.equals("deposit")) {
                    minimum = Math.min(Money.MAX, bank.depositFeeCents + 1);
                    maximum = engine.balance(actor.account());
                } else if (bank != null && action.equals("withdraw")) {
                    try {
                        Banking.WalletFunding funding = engine.banking.quoteWalletFunding(actor, bank.id, 1);
                        maximum = funding.balanceBefore() - funding.fee();
                    } catch (UserError unavailable) { maximum = 0; }
                }
            }
            if (key.equals("amount") && !family.equals("bank") && !(family.equals("tax") && action.equals("quote"))
                    || key.equals("budget") || key.equals("seedCapital")) {
                String source = family.equals("company") || key.equals("seedCapital") ? company.isEmpty() ? "" : "company:" + company
                        : form.has("fromAccount") ? form.value("fromAccount")
                        : form.has("companyAccount") ? form.value("companyAccount") : form.value("account");
                if (!source.isEmpty() && authorized(actor, source)) {
                    maximum = engine.spendable(source);
                    if (family.equals("company") && action.equals("pay")) maximum = Math.max(0, maximum - engine.config.companyTransferFeeCents);
                    if (key.equals("seedCapital")) maximum = Math.max(0, maximum - engine.config.bankCreationFeeCents);
                }
            }
            form.constraints(key, FormConstraints.money(minimum, Math.max(minimum, maximum)));
        }
        long debt = Money.MAX;
        EconomyData.Loan loan = engine.data.loans.get(form.value("loanId"));
        if (family.equals("loan") && loan != null && loan.periodMillis > 0 && loan.periods > 0) {
            debt = engine.banking.previewLoan(actor, loan.id).total();
        } else if (family.equals("tax")) {
            String account = form.value("account");
            if (!account.isEmpty() && authorized(actor, account)) {
                debt = engine.taxes.arrears(account).min(java.math.BigInteger.valueOf(Money.MAX)).longValueExact();
            }
        }
        form.constraints("amountOrAll", FormConstraints.money(1, Math.max(1, debt)).or("all"));
    }

    private long remaining(FormBuilder form, String family) {
        String id = form.value("listingId");
        if (id.isEmpty() || form.choices("listingId").stream().noneMatch(choice -> choice.value().equals(id))) return 0;
        if (family.equals("market")) {
            EconomyData.MarketListing listing = engine.data.market.get(id);
            return listing == null ? 0 : listing.remaining;
        }
        EconomyData.StockListing listing = engine.data.stocks.get(id);
        return listing == null ? 0 : listing.remaining;
    }

    private boolean authorized(Actor actor, String account) {
        try { return engine.requireAccount(actor, account).equals(account); }
        catch (UserError denied) { return false; }
    }

    private boolean managedBank(Actor actor, EconomyData.Bank bank) {
        return authorized(actor, "company:" + bank.company);
    }

    private boolean hasDefault(Actor actor) {
        return engine.data.loans.values().stream().anyMatch(l -> l.borrower.equals(actor.id().toString()) && l.status.equals("DEFAULTED"));
    }

    private long openLoans(String bank) {
        return engine.data.loans.values().stream().filter(l -> l.bank.equals(bank) && OPEN_LOANS.contains(l.status)).count();
    }

    private String resolve(Actor actor, String value) {
        try { return engine.resolveAccount(actor, value); }
        catch (UserError unknown) { return null; }
    }

    private String playerLabel(String account, String prior) {
        if (!account.startsWith("player:")) return display.account(account);
        String id = account.substring(7);
        String fallback = prior.matches("[A-Za-z0-9_]{1,16}") ? prior : "Former player";
        return DisplayText.name(engine.data.playerNames.get(id), fallback) + " - Personal account";
    }

    private String cachedValue(String key) {
        EconomyData.Valuation value = engine.data.valuations.get(key);
        return value == null ? "; no cached valuation" : "; last valuation " + Money.format(value.value);
    }

    private String chunkLabel(Actor actor, String key) {
        String territory = engine.governance.claim(key).map(display::claimTitle).map(title -> title + " - ").orElse("");
        return (key.equals(actor.chunkKey()) ? "Current location - " : "") + territory + DisplayText.chunk(key);
    }

    private static FormChoice option(String value, String label, String detail) {
        return new FormChoice(value, shorten(label, 128), shorten(detail, 256));
    }

    private static String shorten(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, maximum - 3) + "...";
    }

    private static String word(List<String> words, int index) {
        return index < words.size() ? lower(words.get(index)) : "";
    }

    private static String lower(String value) { return value.toLowerCase(Locale.ROOT); }
}
