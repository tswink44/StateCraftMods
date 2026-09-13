package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiProvider;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiRow;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import dev.statecraft.api.ui.PersonalDashboard;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Typed server-side views. Neither views nor reviews execute commands or book accrual. */
public final class EconomyPresentation implements UiProvider {
    private static final Set<String> SECTIONS = Set.of("atm", "hub", "market", "deliveries", "property", "company",
            "bank", "loans", "stock", "tax", "guide", "dashboard");
    private static final Set<String> OWED = Set.of("ACTIVE", "DEFAULTED");
    private final EconomyEngine e;
    private final EconomyDisplay display;
    private final EconomyPreview reviews;

    public EconomyPresentation(EconomyEngine engine) {
        e = Objects.requireNonNull(engine);
        display = new EconomyDisplay(engine);
        reviews = new EconomyPreview(engine);
    }

    public ActionPreview preview(Actor actor, ActionSelection selection, InventoryPort inventory) {
        return reviews.preview(actor, selection, inventory);
    }

    @Override public String queryText(Actor actor, ActionSelection selection, String commandResult) {
        if (!selection.namespace().equals("economy") || selection.template().isEmpty()) return commandResult;
        var menu = MenuRegistry.get(selection.page());
        if (!menu.query().equals(selection.template()) && menu.actions().stream()
                .noneMatch(action -> action.command().equals(selection.template()) && action.intent() == ActionIntent.QUERY)) return commandResult;
        e.ledger.thread();
        List<String> args = CommandLine.split(selection.rendered());
        if (args.get(0).equalsIgnoreCase("page")) args = args.subList(2, args.size());
        String family = args.get(0).toLowerCase(Locale.ROOT);
        String operation = args.size() > 1 ? args.get(1).toLowerCase(Locale.ROOT) : "";
        return switch (family) {
            case "accounts" -> e.accessibleAccounts(actor).stream().map(account -> display.account(actor, account)).collect(Collectors.joining("\n"));
            case "balance", "bal", "limits" -> accountQuery(actor, args, family.equals("limits"), commandResult);
            case "history", "activity" -> {
                e.requireAccount(actor, args.size() > 1 ? args.get(1) : "selected");
                yield queryRows(commandResult, 1, row -> {
                    var match = Pattern.compile("^(\\S+) (\\S+) → (\\S+) (\\$[0-9,]+\\.[0-9]{2}) \\((.*)\\)$").matcher(row);
                    if (!match.matches()) return null;
                    return t("query.transaction", "%s — %s → %s; %s (%s)",
                            date(java.time.Instant.parse(match.group(1)).toEpochMilli()), display.account(match.group(2)),
                            display.account(match.group(3)), match.group(4), display.transactionReason(actor, match.group(5))).fallback();
                });
            }
            case "hub" -> operation.equals("prices") ? priceQuery(commandResult)
                    : operation.equals("settings") || operation.isEmpty()
                        ? t("query.hub", "Trading Hub\n%s", commandResult.substring(commandResult.indexOf('\n') + 1)).fallback() : commandResult;
            case "prices" -> priceQuery(commandResult);
            case "market" -> marketQuery(actor, args, operation, commandResult);
            case "property", "chunk" -> propertyQuery(actor, args, operation, commandResult);
            case "company", "vault" -> companyQuery(actor, args, operation, commandResult);
            case "stock" -> operation.equals("search") || operation.equals("own")
                    ? queryRows(commandResult, 1, row -> {
                        EconomyData.StockListing listing = e.data.stocks.get(firstColumn(row));
                        return listing == null ? null : queryRow(stockRow(listing));
                    }) : commandResult;
            case "bank" -> bankQuery(actor, args, operation, commandResult);
            case "loan" -> operation.equals("show") ? loanQuery(actor, args.get(2), commandResult) : commandResult;
            case "tax" -> taxQuery(actor, args, operation, commandResult);
            default -> commandResult;
        };
    }

    private String accountQuery(Actor actor, List<String> args, boolean limits, String result) {
        String account = e.requireAccount(actor, args.size() > 1 ? args.get(1) : "selected");
        if (!limits) {
            var balance = Pattern.compile("^" + Pattern.quote(account) + ": (\\$[0-9,]+\\.[0-9]{2})$").matcher(result);
            return balance.matches() ? t("query.balance", "%s: %s", display.account(actor, account), balance.group(1)).fallback() : result;
        }
        var amounts = Pattern.compile("^" + Pattern.quote(account)
                + " UTC daily spending limit (\\$[0-9,]+\\.[0-9]{2}); spent (\\$[0-9,]+\\.[0-9]{2}); remaining (\\$[0-9,]+\\.[0-9]{2})\\.$").matcher(result);
        if (!amounts.matches()) return result;
        return t("query.limits", "%s\nDaily spending limit (UTC): %s; spent: %s; remaining: %s.",
                display.account(actor, account), amounts.group(1), amounts.group(2), amounts.group(3)).fallback();
    }

    private String priceQuery(String result) {
        return queryRows(result, 1, row -> {
            int separator = row.indexOf(" = ");
            return separator < 0 ? null : EconomyDisplay.registry(row.substring(0, separator)) + row.substring(separator);
        });
    }

    private String marketQuery(Actor actor, List<String> args, String operation, String result) {
        if (operation.equals("search") || operation.equals("own")) {
            return queryRows(result, 1, row -> {
                EconomyData.MarketListing listing = e.data.market.get(firstColumn(row));
                return listing == null ? null : queryRow(marketRow(listing));
            });
        }
        if (operation.equals("inspect")) {
            EconomyData.MarketListing listing = e.data.market.get(args.get(2));
            if (listing == null) return result;
            return t("detail.market", "Seller: %s\nItem: %s\nAvailable: %s\nUnit price: %s\nExpires: %s\nItem attributes:\n%s",
                    display.account(listing.sellerAccount), display.item(listing.item), "" + listing.remaining,
                    Money.format(listing.unitPrice), date(listing.expiresAt), display.itemDetails(listing.item)).fallback();
        }
        if (operation.equals("deliveries")) {
            List<EconomyData.Delivery> queue = e.data.deliveries.getOrDefault(actor.id().toString(), List.of());
            return queryRows(result, 1, row -> queue.stream()
                    .filter(delivery -> row.equals(delivery.item.count() + " × " + delivery.item.item() + " — " + delivery.reason))
                    .findFirst().map(delivery -> queryRow(deliveryRow(delivery))).orElse(null));
        }
        return result;
    }

    private String propertyQuery(Actor actor, List<String> args, String operation, String result) {
        if (operation.equals("value")) {
            String key = args.size() < 3 || args.get(2).equalsIgnoreCase("here") ? actor.chunkKey() : args.get(2);
            EconomyData.Valuation value = e.data.valuations.get(key);
            if (value == null) return result;
            return t("query.valuation", "%s\nValue: %s; base value: %s; location factor: %s; biome: %s (%s); demand factor: %s; improvement factor: %s; property-tax discount: %s.",
                    DisplayText.chunk(value.chunk), Money.format(value.value), Money.format(value.base),
                    DisplayText.percent(value.locationBps), EconomyDisplay.registry(value.biome), DisplayText.percent(value.biomeBps),
                    DisplayText.percent(value.demandBps), DisplayText.percent(value.improvementBps), DisplayText.percent(value.taxDiscountBps)).fallback();
        }
        if (operation.equals("listings")) {
            return queryRows(result, 1, row -> {
                String[] fields = row.split(" \\| ", 3);
                if (fields.length != 3 || !fields[2].startsWith("seller ")) return null;
                return t("query.property_listing", "%s — %s; seller: %s.", DisplayText.chunk(fields[0]), fields[1],
                        display.account(fields[2].substring(7))).fallback();
            });
        }
        if (operation.equals("own")) {
            return queryRows(result, 1, row -> {
                boolean encumbered = row.endsWith(" [encumbered]");
                String key = encumbered ? row.substring(0, row.length() - " [encumbered]".length()) : row;
                return t("query.property_own", "%s; encumbered: %s.", DisplayText.chunk(key), DisplayText.yesNo(encumbered)).fallback();
            });
        }
        return result;
    }

    private String companyQuery(Actor actor, List<String> args, String operation, String result) {
        if (!Set.of("balance", "fees").contains(operation)) return result;
        GovernanceAccess.CompanyView company = e.governance.company(args.get(2)).orElseThrow(() -> new UserError("That company is unavailable."));
        e.requireAccount(actor, company.account());
        if (operation.equals("balance")) return t("query.balance", "%s: %s",
                display.account(company.account()), Money.format(e.balance(company.account()))).fallback();
        return t("query.company_fees", "%s\nTransfer fee: %s; periodic fee: %s per %s; unpaid obligations: %s. Use Pay obligations to settle this treasury's debts.",
                display.company(company.id()), Money.format(e.config.companyTransferFeeCents), Money.format(e.config.companyPeriodicFeeCents),
                DisplayText.duration(e.config.companyFeePeriodMillis), money(e.taxes.arrears(company.account()))).fallback();
    }

    private String bankQuery(Actor actor, List<String> args, String operation, String result) {
        if (operation.equals("list")) {
            return queryRows(result, 1, row -> {
                EconomyData.Bank bank = e.data.banks.get(firstColumn(row));
                if (bank == null) return null;
                return t("query.bank_listing", "%s — %s; deposit %s; loan %s per %s; deposit fee %s; withdrawal fee %s.",
                        display.bank(bank.id), bank.branding, DisplayText.percent(bank.depositInterestBps),
                        DisplayText.percent(bank.loanInterestBps), DisplayText.duration(bank.interestPeriodMillis),
                        Money.format(bank.depositFeeCents), Money.format(bank.withdrawalFeeCents)).fallback();
            });
        }
        if (operation.equals("balance")) {
            String id = args.size() > 2 ? args.get(2) : e.banking.associatedBank(actor);
            String customer = args.size() > 3 ? args.get(3) : null;
            e.banking.balance(actor, id, customer);
            EconomyData.Bank bank = e.banking.bank(id, false);
            return display.bank(bank.id) + " — " + display.player(customer == null ? actor.id().toString() : customer) + "\n" + result;
        }
        if (operation.equals("report")) {
            EconomyData.Bank bank = e.banking.bank(args.get(2), false);
            e.requireAccount(actor, "company:" + bank.company);
            String rendered = queryRows(result, 2, row -> {
                var match = Pattern.compile("^(\\S+) (\\$[0-9,]+\\.[0-9]{2}) (.*)$").matcher(row);
                return match.matches() ? DisplayText.words(match.group(1)) + " " + match.group(2) + " "
                        + display.bankEventDetail(actor, match.group(1), match.group(3)) : null;
            });
            return rendered.startsWith(bank.name + ": ") ? display.bank(bank.id) + rendered.substring(bank.name.length()) : rendered;
        }
        if (operation.equals("loans")) {
            Map<String, EconomyData.Loan> loans = e.banking.loans(actor, args.size() > 2 ? args.get(2) : null).stream()
                    .collect(Collectors.toMap(loan -> loan.id, Function.identity()));
            return queryRows(result, 1, row -> {
                EconomyData.Loan loan = loans.get(firstColumn(row));
                return loan == null ? null : t("query.loan_row", "%s — %s; borrower: %s; principal: %s; booked interest: %s.",
                        display.bank(loan.bank), DisplayText.words(loan.status), display.player(loan.borrower),
                        Money.format(loan.principal), Money.format(loan.interest)).fallback();
            });
        }
        if (operation.equals("loan") && args.size() > 3 && args.get(2).equalsIgnoreCase("show")) return loanQuery(actor, args.get(3), result);
        return result;
    }

    private String loanQuery(Actor actor, String id, String result) {
        EconomyData.Loan loan = e.banking.loan(id);
        e.banking.requireLoanViewer(actor, loan);
        String header = result.split("\n", 2)[0];
        var match = Pattern.compile("^([^;]+); outstanding (\\$[0-9,]+\\.[0-9]{2}); currently due (\\$[0-9,]+\\.[0-9]{2}); missed payments ([0-9]+); current automatic payments=(true|false)(?:; next unpaid installment (\\S+))?$").matcher(header);
        if (!match.matches()) return result;
        return t("query.loan", "%s\nStatus: %s; outstanding: %s; currently due: %s; missed payments: %s.\nCurrent automatic payments: %s; next unpaid installment: %s.\nOriginal agreement (immutable):\n%s",
                display.loan(loan), DisplayText.words(match.group(1)), match.group(2), match.group(3), match.group(4),
                DisplayText.yesNo(Boolean.parseBoolean(match.group(5))),
                match.group(6) == null ? date(0) : date(java.time.Instant.parse(match.group(6)).toEpochMilli()), display.agreement(loan)).fallback();
    }

    private String taxQuery(Actor actor, List<String> args, String operation, String result) {
        if (operation.equals("rates")) {
            return e.taxes.tiers(actor.chunkKey(), e.governance.nationOf(actor.id()).orElse(null)).stream()
                    .map(government -> display.government(government.id()) + " (" + DisplayText.words(government.kind().name()) + ")\n"
                            + government.settings().entrySet().stream()
                                .map(entry -> EconomyDisplay.tax(entry.getKey()) + ": " + display.setting(entry.getKey(), entry.getValue()))
                                .collect(Collectors.joining("; "))).collect(Collectors.joining("\n"));
        }
        if (operation.equals("quote")) {
            long gross = Money.parse(args.get(2));
            List<String> rows = new ArrayList<>();
            for (String kind : List.of("incomeTaxBps", "salesTaxBps", "propertyTaxBps", "corporateTaxBps", "tariffBps")) {
                List<Taxation.Charge> charges = e.taxes.quote(actor.chunkKey(), e.governance.nationOf(actor.id()).orElse(null), gross, kind);
                rows.add(EconomyDisplay.tax(kind) + ": " + Money.format(Taxation.total(charges))
                        + (charges.isEmpty() ? "" : " — " + charges.stream().map(charge -> display.account(charge.account())
                                + ": " + Money.format(charge.cents())).collect(Collectors.joining("; "))));
            }
            return t("query.tax_quote", "Independent tax illustrations for %s; tariffs apply only on foreign imports, corporate tax only to corporate activity:",
                    Money.format(gross)).fallback() + "\n" + String.join("\n", rows);
        }
        if (operation.equals("arrears") || operation.equals("report")) {
            e.requireAccount(actor, args.size() > 2 ? args.get(2) : "selected");
            boolean arrears = operation.equals("arrears");
            return queryRows(result, arrears ? 2 : 1, row -> {
                var match = (arrears ? Pattern.compile("^(\\S+) → (\\S+) (\\$[0-9,]+\\.[0-9]{2}) \\((.*)\\)$")
                        : Pattern.compile("^(.+?) (\\S+) (\\$[0-9,]+\\.[0-9]{2}) payer=(\\S+) government=(\\S+) (.*)$")).matcher(row);
                if (!match.matches()) return null;
                return arrears ? t("query.arrear", "%s → %s; %s (%s)", EconomyDisplay.tax(match.group(1)),
                        display.account(match.group(2)), match.group(3), display.subject(match.group(1), match.group(4))).fallback()
                        : t("query.tax_entry", "%s — %s; %s; payer: %s; recipient: %s; %s",
                                DisplayText.words(match.group(1)), EconomyDisplay.tax(match.group(2)), match.group(3),
                                display.account(match.group(4)), match.group(5).equals("null")
                                        ? match.group(2).equals("companyFee") ? "Public fee fund" : "No government" : display.government(match.group(5)),
                                display.subject(match.group(2), match.group(6))).fallback();
            });
        }
        return result;
    }

    private static String firstColumn(String row) {
        int separator = row.indexOf(" | ");
        return separator < 0 ? "" : row.substring(0, separator);
    }

    private static String queryRow(UiRow row) { return row.title().fallback() + " — " + row.detail().fallback(); }

    /** Keep the original authorized page, count, ordering and filter result, rather than running another query. */
    private static String queryRows(String result, int headings, Function<String, String> format) {
        List<String> lines = new ArrayList<>(List.of(result.split("\n", -1)));
        if (lines.size() <= headings || !lines.get(headings - 1).contains(" entries, page ")) return result;
        for (int index = headings; index < lines.size(); index++) {
            if (lines.get(index).equals("(none)")) continue;
            String formatted;
            try { formatted = format.apply(lines.get(index)); }
            catch (IllegalArgumentException | UserError | java.time.DateTimeException unsupported) { return result; }
            if (formatted == null) return result;
            lines.set(index, formatted);
        }
        return String.join("\n", lines);
    }

    @Override public UiView view(UiContext context) {
        e.ledger.thread();
        UiQuery query = context.query();
        if (!query.namespace().equals("economy")) throw new UserError("This is not an economy view.");
        if (query.entity().present()) {
            if (query.page().equals("economy:atm")) {
                if (!query.entity().namespace().equals("economy")) throw new UserError("Invalid account overview.");
                return switch (query.entity().kind()) {
                    case ACCOUNT -> accountDetail(context.actor(), query.entity().id(), query);
                    case BANK -> personalBankAtm(context.actor(), query.entity().id());
                    default -> throw new UserError("Choose an account to open its ATM.");
                };
            }
            if (!query.page().equals("economy:detail")) throw new UserError("Open typed entities through the details page.");
            return detail(context.actor(), query);
        }
        String section = query.page().substring("economy:".length());
        if (section.equals("detail")) return textDetail(t("title.details", "Economy details"),
                t("view.choose_record", "Choose a record from an economy section to inspect its authorized details."),
                List.of(nav("atm", "action.accounts", "Accounts"), nav("dashboard", "action.dashboard", "Pending work")));
        if (!SECTIONS.contains(section)) throw new UserError("Unknown economy section.");
        if (section.equals("dashboard")) return dashboard(context.actor(), query.search(), query.offset());
        Actor actor = context.actor();
        Stream<UiRow> rows = switch (section) {
            case "atm" -> e.accessibleAccounts(actor).stream().map(account -> accountRow(actor, account));
            case "bank" -> e.data.banks.values().stream().filter(bank -> !bank.closed || manages(actor, bank) || bank.deposits.containsKey(actor.id().toString()))
                    .map(bank -> new UiRow(UiText.literal(display.bank(bank.id)),
                            t("row.bank", "Deposit %s; loan %s per %s; closed: %s.",
                                    DisplayText.percent(bank.depositInterestBps), DisplayText.percent(bank.loanInterestBps),
                                    DisplayText.duration(bank.interestPeriodMillis), DisplayText.yesNo(bank.closed)),
                            ref(EntityRef.Kind.BANK, bank.id)));
            case "loans" -> e.data.loans.values().stream().filter(loan -> canViewLoan(actor, loan)).map(loan -> loanRow(actor, loan));
            case "market" -> e.data.market.values().stream().filter(listing -> listing.expiresAt > e.clock.millis()
                    || listing.seller.equals(actor.id().toString()) || actor.admin()).map(this::marketRow);
            case "deliveries" -> e.data.deliveries.getOrDefault(actor.id().toString(), List.of()).stream().map(this::deliveryRow);
            case "property" -> e.governance.claims().stream().filter(claim -> e.data.properties.containsKey(claim.key())
                    || authorized(actor, claim.ownerAccount())).map(this::claimRow);
            case "company" -> e.governance.companies().stream().filter(company -> authorized(actor, company.account()))
                    .map(company -> new UiRow(UiText.literal(display.company(company.id())),
                            t("row.company", "Treasury %s; unpaid obligations %s.", Money.format(e.balance(company.account())),
                                    money(e.taxes.arrears(company.account()))), ref(EntityRef.Kind.COMPANY, company.id())));
            case "stock" -> e.data.stocks.values().stream().filter(listing -> listing.expiresAt > e.clock.millis()
                    || listing.seller.equals(actor.id().toString()) || actor.admin()).map(this::stockRow);
            case "tax" -> e.data.arrears.values().stream().filter(debt -> authorized(actor, debt.payer)).map(this::arrearRow);
            case "hub" -> e.values.prices().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(price -> new UiRow(UiText.literal(shorten(EconomyDisplay.registry(price.getKey()), 256)), t("row.price", "Hub unit price: %s.",
                            Money.format(price.getValue())), EntityRef.NONE));
            case "guide" -> SECTIONS.stream().filter(id -> !id.equals("guide")).sorted()
                    .map(id -> new UiRow(title(id), t("row.section", "Open the %s section.", title(id).fallback()),
                            ref(EntityRef.Kind.DASHBOARD, "economy:" + id)));
            default -> Stream.empty();
        };
        List<UiAction> actions = new ArrayList<>();
        if (e.initialGrantPending(actor)) actions.add(initializeAction());
        actions.add(nav("guide", "action.guide", "Guide"));
        actions.add(nav("dashboard", "action.dashboard", "Pending work"));
        if (section.equals("deliveries") || section.equals("market")) actions.add(collectAction(actor));
        if (section.equals("bank")) actions.add(nav("loans", "action.loans", "Loan management"));
        if (section.equals("atm")) actions.add(action("atm", "transfer <fromAccount> <toAccount> <amount>",
                "action.transfer", "Electronic transfer", Map.of("fromAccount", actor.account())));
        UiText body = t("view.section", "Select a row for authorized details and prefilled actions. Amounts are server-side snapshots.");
        if (section.equals("guide")) body = UiText.literal(shorten(e.guideText(), 18_000));
        if (section.equals("hub")) {
            EconomyData.HubQuota quota = e.data.hubQuotas.get(actor.id().toString());
            long sold = quota != null && quota.day >= Math.floorDiv(e.clock.millis(), 86_400_000) ? quota.gross : 0;
            body = t("view.hub", "Daily gross limit: %s; sold today: %s; daily item limit: %s. Currency is not sellable. Sales and income taxes are withheld.",
                    Money.format(e.config.hubDailyLimitCents), Money.format(sold), "" + e.config.hubDailyItemLimit);
            actions.add(action("hub", "hub sell <quantity>", "action.sell_hub", "Sell held item", Map.of("quantity", "1")));
            actions.add(action("hub", "hub suggest <unitPrice> <reason>", "action.suggest", "Suggest held-item price", Map.of()));
        }
        return page(title(section), body,
                rows, actions, query.search(), query.offset());
    }

    @Override
    public PersonalDashboard.Page personalAccounts(Actor actor, int offset) {
        e.ledger.thread();
        List<PersonalDashboard.Entry> entries = new ArrayList<>();
        String own = e.requireAccount(actor, actor.account());
        entries.add(new PersonalDashboard.Entry(t("dashboard.wallet", "Personal account"),
                UiText.literal(Money.format(e.balance(own))),
                new UiQuery("economy:atm", ref(EntityRef.Kind.ACCOUNT, own), "", 0)));
        String player = actor.id().toString();
        e.data.banks.values().stream()
                .filter(bank -> bank.deposits.containsKey(player) || !bank.closed && bank.id.equals(e.data.bankAssociations.get(player)))
                .sorted(java.util.Comparator.comparing((EconomyData.Bank bank) -> bank.name.toLowerCase(Locale.ROOT))
                        .thenComparing(bank -> bank.id))
                .forEach(bank -> {
                    Banking.DepositSummary deposit = e.banking.balance(actor, bank.id, null);
                    entries.add(new PersonalDashboard.Entry(UiText.literal(display.bank(bank.id)),
                            UiText.literal(Money.format(deposit.balance())),
                            new UiQuery("economy:atm", ref(EntityRef.Kind.BANK, bank.id), "", 0)));
                });
        return PersonalDashboard.Page.of(entries, offset);
    }

    private UiView personalBankAtm(Actor actor, String id) {
        EconomyData.Bank bank = e.banking.bank(id, false);
        if (!bank.id.equals(id)) throw new UserError("Choose the bank's account from your dashboard.");
        Banking.DepositSummary balance = e.banking.balance(actor, id, null);
        List<UiAction> actions = List.of(
                enabled(action("bank", "bank deposit <amount> <bank>", "action.account_deposit", "Deposit into this account",
                        Map.of("bank", id)), !bank.closed, "This bank is closed."),
                enabled(action("bank", "bank withdraw <amount> <bank>", "action.account_withdraw", "Withdraw to personal account",
                        Map.of("bank", id)), !bank.closed && balance.balance() > 0, "This account has no available deposit or the bank is closed."),
                action("bank", "bank balance <bank>", "action.account_balance", "Refresh account balance", Map.of("bank", id)));
        return new UiView(t("dashboard.atm_title", "ATM - %s", display.bank(id)),
                t("dashboard.atm_body", "Account holder: %s\nBalance: %s\nInterest awaiting payment: %s\nDeposit fee: %s\nWithdrawal fee: %s",
                        actor.name(), Money.format(balance.balance()), money(balance.unpaidInterest()),
                        Money.format(bank.depositFeeCents), Money.format(bank.withdrawalFeeCents)),
                List.of(), actions, 0, false, UiText.EMPTY);
    }

    @Override public UiView dashboard(Actor actor, String search, int offset) {
        e.ledger.thread();
        new UiQuery("economy:dashboard", EntityRef.NONE, search, offset);
        Stream<UiRow> loans = e.data.loans.values().stream().filter(loan -> canViewLoan(actor, loan))
                .filter(loan -> loan.status.equals("REQUESTED") && loan.applicationExpiresAt > e.clock.millis()
                        || loan.borrower.equals(actor.id().toString()) && OWED.contains(loan.status)
                        && (!loan.autoPay || e.banking.previewLoan(actor, loan.id).currentlyDue() > 0))
                .map(loan -> loanRow(actor, loan));
        Stream<UiRow> arrears = e.data.arrears.values().stream().filter(debt -> authorized(actor, debt.payer)).map(this::arrearRow);
        Stream<UiRow> deliveries = e.data.deliveries.getOrDefault(actor.id().toString(), List.of()).stream().map(this::deliveryRow);
        Stream<UiRow> market = e.data.market.values().stream().filter(listing -> listing.seller.equals(actor.id().toString())).map(this::marketRow);
        Stream<UiRow> property = e.data.properties.values().stream().filter(listing -> authorized(actor, listing.ownerAccount))
                .map(listing -> new UiRow(UiText.literal(DisplayText.chunk(listing.chunk)), t("row.property_listing", "Offered for %s; expires %s.",
                        Money.format(listing.price), date(listing.expiresAt)), ref(EntityRef.Kind.CLAIM, listing.chunk)));
        Stream<UiRow> stocks = e.data.stocks.values().stream().filter(listing -> listing.seller.equals(actor.id().toString())).map(this::stockRow);
        List<UiAction> actions = new ArrayList<>(List.of(collectAction(actor), nav("loans", "action.loans", "Loan management"),
                nav("tax", "action.taxes", "Taxes and obligations")));
        if (e.initialGrantPending(actor)) actions.add(initializeAction());
        return page(title("dashboard"), t("dashboard.body", "Your applications, manual or overdue loans, obligations, deliveries, and outstanding listings."),
                Stream.of(loans, arrears, deliveries, market, property, stocks).flatMap(stream -> stream),
                actions,
                search, offset);
    }

    private UiView detail(Actor actor, UiQuery query) {
        EntityRef entity = query.entity();
        if (!entity.namespace().equals("economy")) throw new UserError("Invalid economy entity namespace.");
        return switch (entity.kind()) {
            case ACCOUNT -> accountDetail(actor, entity.id(), query);
            case BANK -> bankDetail(actor, entity.id(), query);
            case LOAN -> loanDetail(actor, entity.id());
            case MARKET_LISTING -> marketDetail(actor, entity.id());
            case STOCK_LISTING -> stockDetail(actor, entity.id());
            case CLAIM -> claimDetail(actor, entity.id());
            case COMPANY -> companyDetail(actor, entity.id(), query);
            case ARREARS -> arrearDetail(actor, entity.id());
            case DELIVERY -> deliveryDetail(actor, entity.id());
            case DASHBOARD -> {
                if (!entity.id().startsWith("economy:") || !SECTIONS.contains(entity.id().substring(8))) throw new UserError("Unknown economy destination.");
                yield view(new UiContext(actor, new UiQuery(entity.id(), EntityRef.NONE, query.search(), query.offset())));
            }
            default -> throw new UserError("Unsupported economy entity kind.");
        };
    }

    private UiView accountDetail(Actor actor, String id, UiQuery query) {
        String account = e.requireAccount(actor, id);
        if (!account.equals(id)) throw new UserError("Use the canonical account identifier.");
        EconomyData.Account record = e.data.accounts.get(account);
        long limit = record == null ? Money.MAX : record.dailyLimit;
        long spent = record != null && record.spentDay >= Math.floorDiv(e.clock.millis(), 86_400_000) ? record.spent : 0;
        List<UiAction> actions = List.of(
                action("atm", "account select <account>", "action.select", "Select account", Map.of("account", account)),
                action("atm", "transfer <fromAccount> <toAccount> <amount>", "action.transfer", "Electronic transfer", Map.of("fromAccount", account)),
                action("atm", "cash deposit <account>", "action.cash_deposit", "Deposit physical cash", Map.of("account", account)),
                action("atm", "cash withdraw <amount> <account>", "action.cash_withdraw", "Withdraw physical cash", Map.of("account", account)),
                action("atm", "history <account> <page>", "action.history", "Activity", Map.of("account", account, "page", "1")),
                action("tax", "tax pay <amountOrAll> <account>", "action.pay_taxes", "Pay obligations", Map.of("account", account)));
        return page(UiText.literal(display.account(actor, account)), t("detail.account", "Balance: %s\nAvailable after protected reserves: %s\nDaily limit: %s\nSpent today: %s",
                        Money.format(e.balance(account)), Money.format(e.spendable(account)), Money.format(limit), Money.format(spent)),
                e.data.arrears.values().stream().filter(debt -> debt.payer.equals(account)).map(this::arrearRow),
                actions, query.search(), query.offset());
    }

    private UiView bankDetail(Actor actor, String id, UiQuery query) {
        EconomyData.Bank bank = e.banking.bank(id, false);
        if (!bank.id.equals(id)) throw new UserError("Use the bank's canonical identity.");
        boolean manager = manages(actor, bank);
        Banking.DepositSummary own = e.banking.balance(actor, id, null);
        String finances = manager ? t("detail.bank_assets", "Cash assets %s; effective liabilities %s; lending reserve %s.",
                Money.format(e.balance(bank.account())), money(e.banking.liabilities(bank)), Money.format(e.banking.requiredReserve(bank))).fallback() : "";
        List<UiAction> actions = new ArrayList<>();
        actions.add(enabled(action("bank", "bank associate <bank>", "action.associate", "Associate with bank", Map.of("bank", id)),
                !bank.closed, "Bank is closed."));
        actions.add(enabled(action("bank", "bank deposit <amount> <bank>", "action.deposit", "Deposit electronic money", Map.of("bank", id)),
                !bank.closed, "Bank is closed."));
        UiAction withdrawal = action("bank", "bank withdraw <amount> <bank>", "action.withdraw", "Withdraw to wallet", Map.of("bank", id));
        try {
            Banking.WalletFunding funding = e.banking.quoteWalletFunding(actor, id, 1);
            e.ledger.prepare(List.of(funding.transfer()), List.of(), true, funding.reserveOverrides());
        } catch (UserError denied) { withdrawal = withdrawal.disabled(unavailable(display.failure(denied.getMessage()))); }
        actions.add(withdrawal);
        boolean applicationCapacity = e.banking.borrowingCapacity(actor) > 0
                && e.data.loans.values().stream().filter(loan -> loan.bank.equals(id) && Set.of("REQUESTED", "ACTIVE", "DEFAULTED").contains(loan.status))
                    .count() < e.config.maximumBankLoans;
        actions.add(enabled(action("bank", "loan request <bank> <principal> <periods> <collateralOrNone> <noneBalanceCollateralOrBoth> <autoOrManual>",
                        "action.apply", "Apply for a loan", Map.of("bank", id)),
                !bank.closed && applicationCapacity && id.equals(e.data.bankAssociations.get(actor.id().toString())),
                "Associate with an open bank; resolve defaults or debt/application limits before borrowing."));
        actions.add(enabled(action("bank", "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>",
                "action.bank_terms", "Change future bank terms", Map.of("bank", id)), manager && !bank.closed, "Only an active bank's treasury manager may change its terms."));
        return page(UiText.literal(display.bank(id)),
                t("detail.bank", "Bank: %s\nYour funded deposit: %s\nYour booked unpaid interest: %s\nAdvertised deposit/loan rates: %s / %s per %s\n%s",
                        display.bank(id), Money.format(own.balance()), money(own.unpaidInterest()),
                        DisplayText.percent(bank.depositInterestBps), DisplayText.percent(bank.loanInterestBps),
                        DisplayText.duration(bank.interestPeriodMillis), finances),
                e.data.loans.values().stream().filter(loan -> loan.bank.equals(id) && canViewLoan(actor, loan)).map(loan -> loanRow(actor, loan)),
                actions, query.search(), query.offset());
    }

    private UiView loanDetail(Actor actor, String id) {
        EconomyData.Loan loan = e.banking.loan(id);
        Banking.LoanSummary summary = e.banking.previewLoan(actor, id);
        EconomyData.Bank bank = e.data.banks.get(loan.bank);
        boolean borrower = loan.borrower.equals(actor.id().toString());
        boolean owed = OWED.contains(loan.status), pending = loan.status.equals("REQUESTED");
        boolean open = owed || pending;
        List<UiAction> actions = new ArrayList<>();
        actions.add(enabled(action("loans", "loan repay <loanId> <amountOrAll>", "action.repay", "Repay loan", Map.of("loanId", id)),
                owed && (borrower || actor.admin()) && bank != null && !bank.closed, "Only the borrower or an operator may repay an outstanding loan."));
        actions.add(enabled(action("loans", "loan autopay <loanId> true", "action.autopay_on", "Enable automatic payments", Map.of("loanId", id)),
                borrower && open && !loan.autoPay, "Only the borrower may enable payments on an open loan that is currently manual."));
        actions.add(enabled(action("loans", "loan autopay <loanId> false", "action.autopay_off", "Disable automatic payments", Map.of("loanId", id)),
                borrower && open && loan.autoPay, "Only the borrower may disable currently enabled automatic payments."));
        UiAction approval = action("loans", "loan approve <loanId>", "action.approve", "Approve application", Map.of("loanId", id));
        try {
            Banking.LoanFunding funding = e.banking.quoteApproval(actor, id);
            e.ledger.prepare(List.of(funding.transfer()), List.of(), true, funding.reserves());
        } catch (UserError denied) { approval = approval.disabled(unavailable(display.failure(denied.getMessage()))); }
        actions.add(approval);
        actions.add(enabled(action("loans", "loan cancel <loanId>", "action.cancel_loan", "Cancel application", Map.of("loanId", id)),
                pending && (borrower || bank != null && manages(actor, bank)), "Only the applicant or bank manager may cancel a pending application."));
        List<UiRow> rows = new ArrayList<>();
        if (bank != null) rows.add(new UiRow(UiText.literal(display.bank(bank.id)),
                t("row.loan_bank", "Lender; closed: %s.", DisplayText.yesNo(bank.closed)), ref(EntityRef.Kind.BANK, bank.id)));
        if (loan.collateral != null && e.governance.claim(loan.collateral).isPresent()) rows.add(claimRow(e.governance.claim(loan.collateral).orElseThrow()));
        return new UiView(t("title.loan", "Loan with %s", display.bank(loan.bank)),
                t("detail.loan", "Status: %s\nCurrent automatic payments: %s\nPrincipal: %s\nInterest through now: %s\nOutstanding total: %s\nCurrently due: %s\nNext unpaid installment: %s\nBorrower: %s\nRequested: %s\nApplication expires: %s\nFunded: %s\nOriginal agreement (immutable):\n%s",
                        DisplayText.words(summary.status()), DisplayText.yesNo(summary.autoPay()), Money.format(summary.principal()), Money.format(summary.interest()),
                        Money.format(summary.total()), Money.format(summary.currentlyDue()), date(summary.nextDueAt()),
                        display.player(loan.borrower), date(loan.requestedAt), date(loan.applicationExpiresAt), date(loan.issuedAt),
                        display.agreement(loan)),
                rows, actions, 0, false, empty());
    }

    private UiView marketDetail(Actor actor, String id) {
        EconomyData.MarketListing listing = e.data.market.get(id);
        if (listing == null) throw new UserError("That listing has completed or been removed. Check your deliveries and activity.");
        boolean own = listing.seller.equals(actor.id().toString());
        if (listing.expiresAt <= e.clock.millis() && !own && !actor.admin()) throw new UserError("That public listing has expired.");
        UiAction buy = action("market", "market buy <listingId> <quantityOrAll>", "action.buy", "Buy listing",
                Map.of("listingId", id, "quantityOrAll", "1"));
        try {
            Commerce.MarketPurchase quote = e.commerce.quoteBuyMarket(actor, id, 1);
            e.ledger.prepare(quote.settlement().transfers());
        } catch (UserError denied) { buy = buy.disabled(unavailable(display.failure(denied.getMessage()))); }
        return textDetail(t("title.market_listing", "Marketplace listing"),
                t("detail.market", "Seller: %s\nItem: %s\nAvailable: %s\nUnit price: %s\nExpires: %s\nItem attributes:\n%s",
                        display.player(listing.seller), display.item(listing.item), "" + listing.remaining,
                        Money.format(listing.unitPrice), date(listing.expiresAt), display.itemDetails(listing.item)),
                List.of(buy, enabled(action("market", "market cancel <listingId>", "action.cancel_listing", "Cancel listing", Map.of("listingId", id)),
                        own || actor.admin(), "Only the seller or an operator may cancel this listing."), collectAction(actor)));
    }

    private UiView stockDetail(Actor actor, String id) {
        EconomyData.StockListing listing = e.data.stocks.get(id);
        if (listing == null) throw new UserError("That stock listing has completed or been removed.");
        boolean own = listing.seller.equals(actor.id().toString());
        if (listing.expiresAt <= e.clock.millis() && !own && !actor.admin()) throw new UserError("That public listing has expired.");
        UiAction buy = action("stock", "stock buy <listingId> <sharesOrAll>", "action.buy_shares", "Buy shares",
                Map.of("listingId", id, "sharesOrAll", "1"));
        try { e.ledger.prepare(e.commerce.quoteBuyStock(actor, id, 1).transfers()); }
        catch (UserError denied) { buy = buy.disabled(unavailable(display.failure(denied.getMessage()))); }
        return textDetail(t("title.stock_listing", "Share listing"),
                t("detail.stock", "Company: %s\nSeller: %s\nRemaining shares: %s\nUnit price: %s\nExpires: %s",
                        display.company(listing.company), display.player(listing.seller), "" + listing.remaining, Money.format(listing.unitPrice), date(listing.expiresAt)),
                List.of(buy, enabled(action("stock", "stock cancel <listingId>", "action.cancel_listing", "Cancel listing", Map.of("listingId", id)),
                        own || actor.admin(), "Only the seller or an operator may cancel this listing.")));
    }

    private UiView claimDetail(Actor actor, String key) {
        dev.statecraft.api.ChunkKey.parse(key);
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElseThrow(() -> new UserError("That claim no longer exists."));
        EconomyData.PropertyListing listing = e.data.properties.get(key);
        boolean sell = actor.admin() || e.governance.maySellProperty(actor.id(), key);
        boolean buy = listing != null && listing.expiresAt > e.clock.millis() && claim.ownerAccount().equals(listing.ownerAccount)
                && !listing.ownerAccount.equals(actor.account()) && !e.banking.encumbers(key)
                && (actor.admin() || e.governance.mayBuyProperty(actor.id(), key));
        List<UiAction> actions = new ArrayList<>();
        actions.add(action("property", "property value <chunkKeyOrHere>", "action.value", "Recalculate property value", Map.of("chunkKeyOrHere", key)));
        actions.add(enabled(action("property", "property list <chunkKeyOrHere> <price>", "action.list_property", "List property",
                Map.of("chunkKeyOrHere", key)), sell && !e.isClaimEncumbered(key), "Only an authorized seller may list unencumbered property."));
        actions.add(enabled(action("property", "property delist <chunkKeyOrHere>", "action.delist", "Delist property",
                Map.of("chunkKeyOrHere", key)), listing != null && (sell || listing.ownerAccount.equals(actor.account())),
                "Only the seller or an authorized property official may delist this claim."));
        for (String suffix : List.of("", " cash", " bank <bank>", " bank <bank> cash")) {
            UiAction action = action("property", "property buy <chunkKeyOrHere>" + suffix,
                    suffix.contains("bank") ? "action.buy_property_bank" : suffix.contains("cash") ? "action.buy_property_cash" : "action.buy_property",
                    suffix.contains("bank") ? "Buy with bank funding" : suffix.contains("cash") ? "Buy with cash funding" : "Buy property",
                    Map.of("chunkKeyOrHere", key));
            actions.add(enabled(action, buy, "Property must be actively listed, unpledged, and available to this buyer."));
        }
        EconomyData.Valuation value = e.data.valuations.get(key);
        return textDetail(t("title.property", "Property"),
                t("detail.property", "Chunk: %s\nPrivate owner: %s\nLast cached value: %s\nSale price: %s\nEncumbered: %s\nPast assessments remain the previous owner's responsibility.",
                        DisplayText.chunk(key) + " - " + display.claimTitle(claim), display.account(claim.ownerAccount()),
                        value == null ? "Not calculated" : Money.format(value.value),
                        listing == null ? "Not listed" : Money.format(listing.price), DisplayText.yesNo(e.isClaimEncumbered(key))), actions);
    }

    private UiView companyDetail(Actor actor, String id, UiQuery query) {
        GovernanceAccess.CompanyView company = e.governance.company(id).orElseThrow(() -> new UserError("That company has closed. Retained loan/listing records keep their original identity."));
        if (!company.id().equals(id)) throw new UserError("Use the company's canonical identity.");
        e.requireAccount(actor, company.account());
        List<UiAction> actions = new ArrayList<>(List.of(
                action("company", "company pay <company> <recipient> <amount>", "action.company_pay", "Company payment", Map.of("company", id)),
                action("company", "company dividend <company> <budget>", "action.dividend", "Distribute dividends", Map.of("company", id)),
                action("tax", "tax pay <amountOrAll> <account>", "action.pay_taxes", "Pay obligations", Map.of("account", company.account()))));
        if (!e.data.banks.containsKey(id)) actions.add(action("bank", "bank create <company> <name> <seedCapital> <depositBps> <loanBps>",
                "action.create_bank", "Create company bank", Map.of("company", id)));
        return page(UiText.literal(display.company(company.id())),
                t("detail.company", "Treasury: %s\nTransfer fee: %s\nPeriodic fee: %s per %s\nUnpaid obligations: %s",
                        Money.format(e.balance(company.account())), Money.format(e.config.companyTransferFeeCents),
                        Money.format(e.config.companyPeriodicFeeCents), DisplayText.duration(e.config.companyFeePeriodMillis), money(e.taxes.arrears(company.account()))),
                e.data.arrears.values().stream().filter(debt -> debt.payer.equals(company.account())).map(this::arrearRow),
                actions, query.search(), query.offset());
    }

    private UiView arrearDetail(Actor actor, String id) {
        EconomyData.Arrear debt = e.data.arrears.values().stream().filter(value -> debtId(value).equals(id)).findFirst()
                .orElseThrow(() -> new UserError("This obligation was settled or is unavailable."));
        e.requireAccount(actor, debt.payer);
        return textDetail(t("title.arrear", "Outstanding obligation"),
                t("detail.arrear", "Payer: %s\nRecipient: %s\nType: %s\nSubject: %s\nOutstanding: %s\nFirst assessed: %s",
                        display.account(debt.payer), display.account(debt.recipient), EconomyDisplay.tax(debt.kind),
                        shorten(display.subject(debt), 1800), money(new BigInteger(debt.cents)), date(debt.firstDue)),
                List.of(action("tax", "tax pay <amountOrAll> <account>", "action.pay_taxes", "Pay obligations", Map.of("account", debt.payer)),
                        action("tax", "tax report <account> <page>", "action.tax_report", "Tax report", Map.of("account", debt.payer, "page", "1"))));
    }

    private UiView deliveryDetail(Actor actor, String id) {
        EconomyData.Delivery delivery = e.data.deliveries.getOrDefault(actor.id().toString(), List.of()).stream()
                .filter(value -> value.id.equals(id)).findFirst().orElseThrow(() -> new UserError("This is not one of your queued deliveries."));
        return textDetail(t("title.delivery", "Queued delivery"),
                t("detail.delivery", "Item: %s\nQuantity: %s\nReason: %s\nQueued: %s\nItem attributes: %s\nCollection fills available inventory space; uncollected items remain queued.",
                        display.item(delivery.item), "" + delivery.item.count(), shorten(EconomyDisplay.deliveryReason(delivery.reason), 1800),
                        date(delivery.createdAt), display.itemDetails(delivery.item)),
                List.of(collectAction(actor)));
    }

    private UiRow accountRow(Actor actor, String account) {
        return new UiRow(UiText.literal(display.account(actor, account)), t("row.account", "Balance %s; available %s.",
                Money.format(e.balance(account)), Money.format(e.spendable(account))), ref(EntityRef.Kind.ACCOUNT, account));
    }

    private UiRow loanRow(Actor actor, EconomyData.Loan loan) {
        Banking.LoanSummary summary = e.banking.previewLoan(actor, loan.id);
        String status = loan.status.equals("REQUESTED") && loan.applicationExpiresAt <= e.clock.millis() ? "EXPIRED APPLICATION" : loan.status;
        return new UiRow(t("row.loan_title", "%s — %s", display.bank(loan.bank), DisplayText.words(status)),
                t("row.loan", "Borrower %s; outstanding %s; due %s; current autopay %s; next unpaid installment %s.",
                        display.player(loan.borrower), Money.format(summary.total()), Money.format(summary.currentlyDue()),
                        DisplayText.yesNo(loan.autoPay), date(summary.nextDueAt())),
                ref(EntityRef.Kind.LOAN, loan.id));
    }

    private UiRow marketRow(EconomyData.MarketListing listing) {
        return new UiRow(t("row.item", "%s × %s", "" + listing.remaining, display.item(listing.item)),
                t("row.listing", "%s each; seller %s; expires %s.", Money.format(listing.unitPrice), display.player(listing.seller), date(listing.expiresAt)),
                ref(EntityRef.Kind.MARKET_LISTING, listing.id));
    }

    private UiRow stockRow(EconomyData.StockListing listing) {
        return new UiRow(t("row.shares", "%s — %s shares", display.company(listing.company), "" + listing.remaining),
                t("row.listing", "%s each; seller %s; expires %s.", Money.format(listing.unitPrice), display.player(listing.seller), date(listing.expiresAt)),
                ref(EntityRef.Kind.STOCK_LISTING, listing.id));
    }

    private UiRow claimRow(GovernanceAccess.ClaimView claim) {
        EconomyData.PropertyListing listing = e.data.properties.get(claim.key());
        return new UiRow(UiText.literal(shorten(DisplayText.chunk(claim.key()) + " - " + display.claimTitle(claim), 256)),
                t("row.claim", "Private owner %s; asking price %s.", display.account(claim.ownerAccount()),
                listing == null ? "Not listed" : Money.format(listing.price)), ref(EntityRef.Kind.CLAIM, claim.key()));
    }

    private UiRow arrearRow(EconomyData.Arrear debt) {
        return new UiRow(t("row.arrear_title", "%s — %s", shorten(EconomyDisplay.tax(debt.kind), 96), shorten(money(new BigInteger(debt.cents)), 96)),
                t("row.arrear", "Payer %s; recipient %s; subject %s.", display.account(debt.payer), display.account(debt.recipient), shorten(display.subject(debt), 1024)),
                ref(EntityRef.Kind.ARREARS, debtId(debt)));
    }

    private UiRow deliveryRow(EconomyData.Delivery delivery) {
        return new UiRow(t("row.item", "%s × %s", "" + delivery.item.count(), display.item(delivery.item)),
                t("row.delivery", "Queued delivery: %s", shorten(EconomyDisplay.deliveryReason(delivery.reason), 1024)), ref(EntityRef.Kind.DELIVERY, delivery.id));
    }

    private boolean canViewLoan(Actor actor, EconomyData.Loan loan) {
        try { e.banking.requireLoanViewer(actor, loan); return true; } catch (UserError denied) { return false; }
    }

    private boolean authorized(Actor actor, String account) {
        try { return e.requireAccount(actor, account).equals(account); } catch (UserError denied) { return false; }
    }

    private boolean manages(Actor actor, EconomyData.Bank bank) { return authorized(actor, "company:" + bank.company); }
    private static String debtId(EconomyData.Arrear debt) { return ActionPreview.digest(debt.id); }
    private static EntityRef ref(EntityRef.Kind kind, String id) { return new EntityRef("economy", kind, id); }

    private UiAction collectAction(Actor actor) {
        return enabled(action("market", "market collect", "action.collect", "Collect queued deliveries", Map.of()),
                !e.data.deliveries.getOrDefault(actor.id().toString(), List.of()).isEmpty(), "You have no queued deliveries.");
    }

    private static UiAction initializeAction() {
        return action("hub", "balance me", "action.initialize", "Initialize economy profile", Map.of());
    }

    private static UiAction nav(String destination, String key, String label) {
        String source = destination.equals("guide") ? "hub" : destination.equals("dashboard") ? "atm"
                : destination.equals("loans") ? "bank" : "dashboard";
        String template = "gui economy:" + destination;
        return action(source, template, key, label, Map.of());
    }

    private static UiAction action(String page, String template, String key, String label, Map<String, String> seeds) {
        return new UiAction("economy:" + page, template, t(key, label), seeds);
    }

    private static UiAction enabled(UiAction action, boolean allowed, String reason) { return allowed ? action : action.disabled(unavailable(reason)); }
    static UiText unavailable(String reason) { return t("action.unavailable", "Unavailable: %s", shorten(reason, 470)); }
    private static UiView textDetail(UiText title, UiText body, List<UiAction> actions) {
        return new UiView(title, body, List.of(), actions, 0, false, empty());
    }

    private static UiView page(UiText title, UiText body, Stream<UiRow> source, List<UiAction> actions, String search, int requested) {
        String needle = search.strip().toLowerCase(Locale.ROOT);
        int offset = requested / UiView.PAGE_SIZE * UiView.PAGE_SIZE;
        List<UiRow> rows = source.filter(row -> needle.isEmpty()
                || (row.title().fallback() + " " + row.detail().fallback() + " " + row.entity().id()).toLowerCase(Locale.ROOT).contains(needle))
                .skip(offset).limit(UiView.PAGE_SIZE + 1L).toList();
        return new UiView(title, body, rows.subList(0, Math.min(rows.size(), UiView.PAGE_SIZE)),
                actions, offset, rows.size() > UiView.PAGE_SIZE, empty());
    }

    private static UiText empty() { return t("view.empty", "No matching records you are authorized to view. Clear the search or choose another section."); }
    private static UiText title(String section) {
        return switch (section) {
            case "atm" -> t("title.atm", "Accounts and ATM");
            case "hub" -> t("title.hub", "Trading Hub prices");
            case "bank" -> t("title.bank", "Company banks");
            case "loans" -> t("title.loans", "Loan management");
            case "market" -> t("title.market", "Marketplace");
            case "deliveries" -> t("title.deliveries", "Queued deliveries");
            case "property" -> t("title.property", "Property");
            case "company" -> t("title.company", "Company finance");
            case "stock" -> t("title.stock", "Stock market");
            case "tax" -> t("title.tax", "Taxes and obligations");
            case "guide" -> t("title.guide", "Economy guide");
            default -> t("title.dashboard", "Economy pending work");
        };
    }

    static UiText t(String key, String template, String... arguments) {
        return UiText.tr("ui.statecraft.economy." + key, String.format(Locale.ROOT, template, (Object[]) arguments), arguments);
    }

    static String money(BigInteger cents) { return "$" + new BigDecimal(cents, 2).toPlainString(); }
    static String date(long time) { return DisplayText.date(time); }
    static String shorten(String text, int maximum) { return text.length() > maximum ? text.substring(0, maximum - 3) + "..." : text; }
}
