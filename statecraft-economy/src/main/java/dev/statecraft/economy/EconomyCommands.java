package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EconomyCommands {
    public interface Hooks {
        default String reload() { throw new UserError("File reload is unavailable in this environment."); }
        default String setPrice(String item, Long cents) { throw new UserError("File price management is unavailable in this environment."); }
        default String merchants(Actor actor, List<String> args) { throw new UserError("Merchant spawning requires a running Minecraft server."); }
        default String guide() { return "Use cash, hub, market, property, company, bank, stock, tax, and history. Physical currency is not craftable."; }
    }

    private final EconomyEngine e;
    private Hooks hooks = new Hooks() {};
    private int viewPage = 1;

    EconomyCommands(EconomyEngine engine) { e = engine; }
    void hooks(Hooks hooks) { this.hooks = java.util.Objects.requireNonNull(hooks); }
    String guideText() { return hooks.guide(); }

    public String execute(Actor actor, String line, InventoryPort inventory) {
        List<String> args = CommandLine.split(line);
        int previousPage = viewPage;
        viewPage = 1;
        try {
            if (!args.isEmpty() && args.get(0).equalsIgnoreCase("page")) {
                need(args, 3, 64, "page <number> <readCommand...>");
                viewPage = integer(args.get(1));
                if (viewPage < 1) throw new UserError("Page numbers start at one.");
                args = args.subList(2, args.size());
            }
            return dispatch(actor, args, inventory);
        } finally { viewPage = previousPage; }
    }

    private String dispatch(Actor actor, List<String> args, InventoryPort inventory) {
        if (args.isEmpty()) return help();
        String command = lower(args.get(0));
        List<String> rest = args.subList(1, args.size());
        return switch (command) {
            case "help" -> help();
            case "balance", "bal" -> {
                need(rest, 0, 1, "balance [account]");
                String account = e.requireAccount(actor, rest.isEmpty() ? "selected" : rest.get(0));
                yield account + ": " + Money.format(e.balance(account));
            }
            case "accounts" -> String.join("\n", e.accessibleAccounts(actor));
            case "limits" -> {
                need(rest, 0, 1, "limits [account]");
                String id = e.requireAccount(actor, rest.isEmpty() ? "selected" : rest.get(0));
                EconomyData.Account account = e.data.accounts.get(id);
                long limit = account == null ? Money.MAX : account.dailyLimit;
                long spent = account != null && account.spentDay >= Math.floorDiv(e.clock.millis(), 86_400_000L) ? account.spent : 0;
                yield id + " UTC daily spending limit " + Money.format(limit) + "; spent " + Money.format(spent)
                        + "; remaining " + Money.format(Math.max(0, limit - spent)) + ".";
            }
            case "account" -> account(actor, rest);
            case "pay" -> {
                need(rest, 2, 3, "pay <recipient> <amount> [fromAccount]");
                e.pay(actor, rest.size() == 3 ? rest.get(2) : actor.account(), rest.get(0), amount(rest.get(1)), "Payment by " + actor.name());
                yield "Payment completed.";
            }
            case "transfer" -> {
                need(rest, 3, 3, "transfer <fromAccount> <toAccount> <amount>");
                e.pay(actor, rest.get(0), rest.get(1), amount(rest.get(2)), "Transfer by " + actor.name());
                yield "Transfer completed.";
            }
            case "cash" -> cash(actor, rest, inventory);
            case "history", "activity" -> history(actor, rest);
            case "top", "leaderboard" -> top(rest);
            case "hub" -> hub(actor, rest, inventory);
            case "prices" -> prices(rest.isEmpty() ? "" : CommandLine.tail(rest, 0));
            case "price" -> price(actor, rest);
            case "market" -> market(actor, rest, inventory);
            case "property", "chunk" -> property(actor, rest, inventory);
            case "company", "vault" -> company(actor, rest, inventory);
            case "stock" -> stock(actor, rest);
            case "bank" -> bank(actor, rest);
            case "loan" -> loan(actor, rest);
            case "tax" -> tax(actor, rest);
            case "admin" -> admin(actor, rest, inventory);
            case "reload" -> { EconomyEngine.requireAdmin(actor); need(rest, 0, 0, "reload"); yield hooks.reload(); }
            case "merchant" -> hooks.merchants(actor, rest);
            case "guide", "recipes" -> hooks.guide();
            default -> throw new UserError("Unknown economy action. Use /sce help.");
        };
    }

    private String help() {
        return "StateCraft Economy — /sce or /statecrafteconomy\n"
                + "balance [account] | accounts | account select <account> | pay <recipient> <amount> [from]\n"
                + "transfer <from> <to> <amount> | cash deposit [account] | cash withdraw <amount> [account]\n"
                + "hub | market | property | company | bank | loan | stock | tax | prices | history | limits | top | guide\n"
                + "Use a command family without arguments for its actions. Prefix any list/query with page <number> to read later pages.\n"
                + "All amounts are dollars with up to two decimal places.";
    }

    private String account(Actor actor, List<String> args) {
        if (args.isEmpty() || args.get(0).equals("list")) return String.join("\n", e.accessibleAccounts(actor));
        if (args.get(0).equals("select")) {
            need(args, 2, 2, "account select <account>");
            e.selectAccount(actor, args.get(1));
            return "Selected authorized account: " + e.selectedAccount(actor);
        }
        throw new UserError("account list | account select <type:id>");
    }

    private String cash(Actor actor, List<String> args, InventoryPort inventory) {
        if (args.isEmpty()) return "cash deposit [account] — deposits all untagged currency\ncash withdraw <amount> [account] — leaves denomination remainder in your balance";
        return switch (lower(args.get(0))) {
            case "deposit" -> {
                need(args, 1, 2, "cash deposit [account]");
                long cents = e.depositCash(actor, args.size() == 2 ? args.get(1) : "selected", inventory);
                yield "Deposited " + Money.format(cents) + " of physical currency.";
            }
            case "withdraw" -> {
                need(args, 2, 3, "cash withdraw <amount> [account]");
                long requested = amount(args.get(1));
                long issued = e.withdrawCash(actor, args.size() == 3 ? args.get(2) : "selected", requested, inventory);
                yield "Issued " + Money.format(issued) + "; " + Money.format(requested - issued) + " of the requested amount remains electronic.";
            }
            default -> throw new UserError("Use cash deposit or cash withdraw.");
        };
    }

    private String history(Actor actor, List<String> args) {
        need(args, 0, 2, "history [account] [page]");
        List<EconomyData.Transaction> history = new ArrayList<>(e.history(actor, args.isEmpty() ? "selected" : args.get(0)));
        Collections.reverse(history);
        return page(history.stream().map(t -> Instant.ofEpochMilli(t.time()) + " " + t.from() + " → " + t.to()
                + " " + Money.format(t.cents()) + " (" + t.reason() + ")").toList(), args.size() > 1 ? integer(args.get(1)) : 1, "Activity");
    }

    private String top(List<String> args) {
        need(args, 0, 1, "top [player|nation|state|city|company]");
        String kind = args.isEmpty() ? "player" : lower(args.get(0));
        if (!Set.of("player", "nation", "state", "city", "company").contains(kind)) throw new UserError("Choose a public account kind.");
        List<String> rows = e.data.accounts.entrySet().stream().filter(entry -> entry.getKey().startsWith(kind + ":"))
                .sorted(Comparator.<Map.Entry<String, EconomyData.Account>>comparingLong(entry -> entry.getValue().balance).reversed()
                        .thenComparing(Map.Entry::getKey)).limit(10)
                .map(entry -> entry.getKey() + " " + Money.format(entry.getValue().balance)).toList();
        return "Top " + kind + " balances (bank deposit liabilities are not double-counted):\n" + String.join("\n", rows);
    }

    private String hub(Actor actor, List<String> args, InventoryPort inventory) {
        if (args.isEmpty() || args.get(0).equals("settings")) {
            EconomyData.HubQuota quota = e.data.hubQuotas.get(actor.id().toString());
            long day = Math.floorDiv(e.clock.millis(), 86_400_000L);
            return "hub sell <quantity> | hub prices [search] | hub suggest <unitPrice> [reason]\n"
                    + "Daily gross limit " + Money.format(e.config.hubDailyLimitCents) + "; item limit " + e.config.hubDailyItemLimit
                    + "; sold today " + Money.format(quota != null && quota.day >= day ? quota.gross : 0)
                    + ". Currency items are never accepted. Sales/income taxes are withheld.";
        }
        return switch (lower(args.get(0))) {
            case "sell" -> {
                need(args, 2, 2, "hub sell <quantity>");
                Commerce.Sale sale = e.commerce.sellHub(actor, integer(args.get(1)), inventory);
                yield "Gross " + Money.format(sale.gross()) + "; tax " + Money.format(sale.taxes()) + "; received " + Money.format(sale.net()) + ".";
            }
            case "prices" -> prices(CommandLine.tail(args, 1));
            case "suggest" -> {
                need(args, 2, 32, "hub suggest <unitPrice> [reason]");
                e.commerce.suggest(actor, inventory.held().item(), amount(args.get(1)), CommandLine.tail(args, 2));
                yield "Price suggestion submitted for operator review.";
            }
            case "suggestions" -> { EconomyEngine.requireAdmin(actor); yield suggestions(); }
            default -> throw new UserError("Use hub sell, prices, suggest, or settings.");
        };
    }

    private String prices(String search) {
        String query = lower(search);
        return page(e.values.prices().entrySet().stream().filter(entry -> entry.getKey().contains(query))
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + " = " + Money.format(entry.getValue())
                        + (e.values.isCurrency(entry.getKey()) ? " [currency: not sellable]" : "")).toList(), 1, "Item sell prices");
    }

    private String price(Actor actor, List<String> args) {
        need(args, 1, 3, "price get <item> | price set <item> <amount> | price remove <item>");
        return switch (lower(args.get(0))) {
            case "get" -> { need(args, 2, 2, "price get <item>"); yield args.get(1) + ": " + Money.format(e.values.sellPrice(args.get(1))); }
            case "set" -> {
                EconomyEngine.requireAdmin(actor);
                need(args, 3, 3, "price set <item> <amount>");
                long cents = Money.positive(amount(args.get(2)));
                if (e.values.isCurrency(args.get(1))) throw new UserError("Currency cannot be assigned a sell price.");
                yield hooks.setPrice(args.get(1), cents);
            }
            case "remove" -> {
                EconomyEngine.requireAdmin(actor);
                need(args, 2, 2, "price remove <item>");
                yield hooks.setPrice(args.get(1), null);
            }
            default -> throw new UserError("Use price get, set, or remove.");
        };
    }

    private String market(Actor actor, List<String> args, InventoryPort inventory) {
        if (args.isEmpty()) return "market list <quantity> <unitPrice> | search [text] | own | inspect <id> | buy <id> <quantity|all> | cancel <id> | collect | deliveries\n"
                + "Later pages: /sce page <number> market search [text]";
        return switch (lower(args.get(0))) {
            case "list" -> {
                need(args, 3, 3, "market list <quantity> <unitPrice>");
                yield "Escrow listing created: " + e.commerce.listMarket(actor, integer(args.get(1)), amount(args.get(2)), inventory);
            }
            case "search", "own" -> {
                boolean own = args.get(0).equalsIgnoreCase("own");
                String search = own ? "" : lower(CommandLine.tail(args, 1));
                yield page(e.data.market.values().stream().filter(l -> l.expiresAt > e.clock.millis())
                        .filter(l -> !own || l.seller.equals(actor.id().toString()))
                        .filter(l -> l.item.item().contains(search) || l.id.contains(search)
                                || e.data.playerNames.getOrDefault(l.seller, "").toLowerCase(Locale.ROOT).contains(search))
                        .map(l -> l.id + " | " + l.remaining + " × " + l.item.item() + " @ " + Money.format(l.unitPrice)
                                + (l.item.snbt().isEmpty() ? "" : " [NBT preserved]")).toList(), 1, own ? "Your listings" : "Marketplace");
            }
            case "inspect" -> {
                need(args, 2, 2, "market inspect <id>");
                EconomyData.MarketListing listing = e.commerce.market(args.get(1));
                String nbt = listing.item.snbt();
                yield listing.id + " | seller " + listing.sellerAccount + " | " + listing.remaining + " × "
                        + listing.item.item() + " @ " + Money.format(listing.unitPrice) + " | expires " + Instant.ofEpochMilli(listing.expiresAt)
                        + "\nFull SNBT remains in escrow. Preview: " + (nbt.isEmpty() ? "(untagged)" : nbt.substring(0, Math.min(nbt.length(), 1500)))
                        + (nbt.length() > 1500 ? " … [preview truncated; escrow data is not truncated]" : "");
            }
            case "buy" -> {
                need(args, 3, 3, "market buy <id> <quantity|all>");
                int quantity = args.get(2).equalsIgnoreCase("all") ? e.commerce.market(args.get(1)).remaining : integer(args.get(2));
                Commerce.Settlement result = e.commerce.buyMarket(actor, args.get(1), quantity);
                int collected;
                try { collected = e.commerce.collect(actor, inventory); }
                catch (UserError deferred) {
                    yield "Purchased for " + Money.format(result.buyerCost()) + " including taxes. Your purchased items are queued."
                            + " Automatic collection was deferred: " + deferred.getMessage()
                            + " Use /sce market collect to receive deliveries; do not repeat the purchase.";
                }
                yield "Purchased for " + Money.format(result.buyerCost()) + " including taxes; collected " + collected
                        + " queued items. Remaining deliveries persist until market collect.";
            }
            case "cancel" -> { need(args, 2, 2, "market cancel <id>"); e.commerce.cancelMarket(actor, args.get(1)); yield "Cancelled; unsold items are in your delivery queue."; }
            case "collect" -> { need(args, 1, 1, "market collect"); yield "Collected " + e.commerce.collect(actor, inventory) + " items. Full-inventory remainders stay queued."; }
            case "deliveries" -> page(e.data.deliveries.getOrDefault(actor.id().toString(), List.of()).stream()
                    .map(d -> d.item.count() + " × " + d.item.item() + " — " + d.reason).toList(), 1, "Your persistent deliveries");
            default -> throw new UserError("Unknown marketplace action.");
        };
    }

    private String property(Actor actor, List<String> args, InventoryPort inventory) {
        if (args.isEmpty()) return "property value [here|chunkKey] | list <here|key> <price> | listings | own | delist <key>\n"
                + "property buy <key> [cash] | property buy <key> bank <bankId> [cash]";
        return switch (lower(args.get(0))) {
            case "value" -> {
                need(args, 1, 2, "property value [here|chunkKey]");
                EconomyData.Valuation v = e.property.value(chunk(actor, args.size() == 2 ? args.get(1) : "here"));
                yield v.chunk + " value " + Money.format(v.value) + "; base " + Money.format(v.base)
                        + "; location factor " + v.locationBps + " bps; biome " + v.biome + " " + v.biomeBps
                        + " bps; demand " + v.demandBps + "; improvements " + v.improvementBps + "; property-tax discount " + v.taxDiscountBps + " bps.";
            }
            case "list" -> {
                need(args, 3, 3, "property list <here|chunkKey> <price>");
                yield "Property listing created: " + e.property.list(actor, chunk(actor, args.get(1)), amount(args.get(2)));
            }
            case "delist" -> { need(args, 2, 2, "property delist <here|chunkKey>"); e.property.delist(actor, chunk(actor, args.get(1))); yield "Property delisted."; }
            case "buy" -> {
                need(args, 2, 5, "property buy <here|chunkKey> [cash] | property buy <key> bank <bankId> [cash]");
                boolean cash = false;
                String bank = null;
                if (args.size() == 3 && args.get(2).equalsIgnoreCase("cash")) cash = true;
                else if (args.size() >= 4 && args.get(2).equalsIgnoreCase("bank")) {
                    bank = args.get(3);
                    cash = args.size() == 5 && args.get(4).equalsIgnoreCase("cash");
                    if (args.size() == 5 && !cash) throw new UserError("Use cash for optional physical-currency funding.");
                } else if (args.size() != 2) throw new UserError("Choose cash, bank <bankId>, or bank <bankId> cash.");
                Commerce.Settlement result = e.property.buy(actor, chunk(actor, args.get(1)), cash, bank, inventory);
                yield "Private property acquired for " + Money.format(result.buyerCost()) + ". Political territory is unchanged.";
            }
            case "listings" -> page(e.data.properties.values().stream().filter(l -> l.expiresAt > e.clock.millis())
                    .map(l -> l.chunk + " | " + Money.format(l.price) + " | seller " + l.ownerAccount).toList(), 1, "Property market");
            case "own" -> page(e.governance.claims().stream().filter(c -> c.ownerAccount().equals(actor.account()))
                    .map(c -> c.key() + (e.isClaimEncumbered(c.key()) ? " [encumbered]" : "")).toList(), 1, "Your private property");
            default -> throw new UserError("Unknown property action.");
        };
    }

    private String company(Actor actor, List<String> args, InventoryPort inventory) {
        if (args.isEmpty()) return "company balance <company> | pay <company> <recipient> <amount> | dividend <company> <budget> | fees <company>";
        need(args, 2, 4, "company <action> <company> ...");
        GovernanceAccess.CompanyView company = e.governance.company(args.get(1)).orElseThrow(() -> new UserError("Unknown company."));
        e.requireAccount(actor, company.account());
        return switch (lower(args.get(0))) {
            case "balance" -> company.name() + " treasury: " + Money.format(e.balance(company.account()));
            case "fees" -> "Transfer fee " + Money.format(e.config.companyTransferFeeCents) + "; periodic fee "
                    + Money.format(e.config.companyPeriodicFeeCents) + " per " + e.config.companyFeePeriodMillis
                    + "ms; unpaid obligations " + money(e.taxes.arrears(company.account())) + ". Use tax pay <amount> " + company.account();
            case "pay" -> {
                need(args, 4, 4, "company pay <company> <recipient> <amount>");
                e.commerce.companyPay(actor, company.id(), args.get(2), amount(args.get(3)), inventory);
                yield "Authorized company payment completed.";
            }
            case "dividend" -> {
                need(args, 3, 3, "company dividend <company> <budget>");
                Commerce.Dividend result = e.commerce.dividend(actor, company.id(), amount(args.get(2)), inventory);
                yield "Paid shareholders " + Money.format(result.paidToShareholders()) + "; corporate tax "
                        + Money.format(result.corporateTax()) + "; rounding remainder retained " + Money.format(result.retainedRemainder()) + ".";
            }
            default -> throw new UserError("Unknown company finance action.");
        };
    }

    private String stock(Actor actor, List<String> args) {
        if (args.isEmpty()) return "stock list <company> <shares> <unitPrice> | search [company] | own | buy <id> <shares|all> | cancel <id>";
        return switch (lower(args.get(0))) {
            case "list" -> {
                need(args, 4, 4, "stock list <company> <shares> <unitPrice>");
                yield "Shares reserved: " + e.commerce.listStock(actor, args.get(1), whole(args.get(2)), amount(args.get(3)));
            }
            case "search", "own" -> {
                boolean own = args.get(0).equalsIgnoreCase("own");
                String search = own ? "" : lower(CommandLine.tail(args, 1));
                yield page(e.data.stocks.values().stream().filter(l -> l.expiresAt > e.clock.millis())
                        .filter(l -> !own || l.seller.equals(actor.id().toString())).filter(l -> l.company.toLowerCase(Locale.ROOT).contains(search)
                                || l.id.contains(search) || e.governance.company(l.company).map(c -> c.name().toLowerCase(Locale.ROOT).contains(search)).orElse(false))
                        .map(l -> l.id + " | " + l.company + " | " + l.remaining + " shares @ " + Money.format(l.unitPrice)).toList(), 1, "Stock market");
            }
            case "buy" -> {
                need(args, 3, 3, "stock buy <id> <shares|all>");
                EconomyData.StockListing listing = e.data.stocks.get(args.get(1));
                if (listing == null) throw new UserError("Unknown stock listing.");
                long quantity = args.get(2).equalsIgnoreCase("all") ? listing.remaining : whole(args.get(2));
                Commerce.Settlement result = e.commerce.buyStock(actor, args.get(1), quantity);
                yield "Shares purchased for " + Money.format(result.buyerCost()) + " including taxes.";
            }
            case "cancel" -> { need(args, 2, 2, "stock cancel <id>"); e.commerce.cancelStock(actor, args.get(1)); yield "Listing cancelled and reserved shares released."; }
            default -> throw new UserError("Unknown stock action.");
        };
    }

    private String bank(Actor actor, List<String> args) {
        if (args.isEmpty()) return "bank list | associate <bank> | balance [bank] | deposit <amount> [bank] | withdraw <amount> [bank]\n"
                + "create <company> <name> <seedCapital> <depositBps> <loanBps> | close <bank> | report <bank> | loans [bank]\n"
                + "capital <bank> <in|out> <amount> | terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>\n"
                + "brand <bank> <name> <branding> | bank loan (or /sce loan)";
        return switch (lower(args.get(0))) {
            case "list" -> page(e.data.banks.values().stream().filter(b -> !b.closed).map(b -> b.id + " | " + b.name + " | "
                    + b.branding + " | deposit " + b.depositInterestBps + " bps; loan " + b.loanInterestBps + " bps per "
                    + b.interestPeriodMillis + "ms; fees in " + Money.format(b.depositFeeCents) + "/out " + Money.format(b.withdrawalFeeCents))
                    .toList(), 1, "Company banks");
            case "create" -> {
                need(args, 6, 6, "bank create <company> <name> <seedCapital> <depositBps> <loanBps>");
                yield "Bank opened: " + e.banking.create(actor, args.get(1), args.get(2), amount(args.get(3)), integer(args.get(4)), integer(args.get(5)));
            }
            case "close" -> { need(args, 2, 2, "bank close <bank>"); e.banking.close(actor, args.get(1)); yield "Bank closed; remaining equity returned to its company."; }
            case "associate" -> { need(args, 2, 2, "bank associate <bank>"); e.banking.associate(actor, args.get(1)); yield "Bank association selected. Deposits at other banks remain yours."; }
            case "deposit", "withdraw" -> {
                need(args, 2, 3, "bank " + args.get(0) + " <amount> [bank]");
                String id = args.size() == 3 ? args.get(2) : e.banking.associatedBank(actor);
                long amount = amount(args.get(1));
                if (args.get(0).equalsIgnoreCase("deposit")) {
                    yield "Bank deposit credited " + Money.format(e.banking.deposit(actor, id, amount)) + " after its fee.";
                }
                e.banking.withdraw(actor, id, amount);
                yield "Withdrew " + Money.format(amount) + " to your electronic wallet; any bank fee was deducted from your deposit.";
            }
            case "balance" -> {
                need(args, 1, 3, "bank balance [bank] [customerUUID]");
                String id = args.size() >= 2 ? args.get(1) : e.banking.associatedBank(actor);
                Banking.DepositSummary result = e.banking.balance(actor, id, args.size() == 3 ? args.get(2) : null);
                yield "Bank deposit: " + Money.format(result.balance()) + "; simple-interest principal " + Money.format(result.interestPrincipal())
                        + "; owed but not yet funded interest " + money(result.unpaidInterest()) + ". These are bank liabilities, not extra wallet currency.";
            }
            case "capital" -> {
                need(args, 4, 4, "bank capital <bank> <in|out> <amount>");
                if (!Set.of("in", "out").contains(lower(args.get(2)))) throw new UserError("Choose in or out.");
                e.banking.capital(actor, args.get(1), args.get(2).equalsIgnoreCase("out"), amount(args.get(3)));
                yield "Bank capital transfer completed without invading protected liabilities.";
            }
            case "brand" -> { need(args, 4, 4, "bank brand <bank> <name> <branding>"); e.banking.brand(actor, args.get(1), args.get(2), args.get(3)); yield "Bank branding updated."; }
            case "terms" -> {
                need(args, 7, 7, "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>");
                e.banking.rates(actor, args.get(1), integer(args.get(2)), integer(args.get(3)), integer(args.get(4)),
                        amount(args.get(5)), amount(args.get(6)));
                yield "Terms updated. Existing deposit interest and loan contracts keep their original rates.";
            }
            case "report" -> {
                need(args, 2, 2, "bank report <bank>");
                EconomyData.Bank bank = e.banking.bank(args.get(1), false);
                e.requireAccount(actor, "company:" + bank.company);
                yield bank.name + ": cash assets " + Money.format(e.balance(bank.account()))
                        + "; deposits " + Money.format(e.banking.depositBalances(bank)) + "; total liabilities including unpaid interest "
                        + money(e.banking.liabilities(bank)) + "; minimum lending reserve " + Money.format(e.banking.requiredReserve(bank))
                        + "; protected for ordinary treasury withdrawals " + Money.format(e.banking.protectedBalance(bank))
                        + "\n" + page(e.data.bankHistory.stream().filter(event -> event.bank().equals(bank.id))
                        .sorted(Comparator.comparingLong(EconomyData.BankEvent::time).reversed())
                        .map(event -> event.kind() + " " + Money.format(event.cents()) + " " + event.detail()).toList(), 1, "Bank activity");
            }
            case "loans" -> {
                need(args, 1, 2, "bank loans [bank]");
                yield page(e.banking.loans(actor, args.size() == 2 ? args.get(1) : null).stream()
                        .map(l -> l.id + " | " + l.status + " | borrower " + l.borrower + " | principal " + Money.format(l.principal)
                                + " | interest " + Money.format(l.interest)).toList(), 1, "Loans");
            }
            case "loan" -> loan(actor, args.subList(1, args.size()));
            default -> throw new UserError("Unknown bank action.");
        };
    }

    private String loan(Actor actor, List<String> args) {
        if (args.isEmpty()) return "loan request <bank> <principal> <periods> <chunkKey|none> <none|balance|collateral|both> [auto|manual]\n"
                + "loan show <id> | approve <id> | cancel <id> | repay <id> <amount|all> | autopay <id> <true|false> | recover <id>\n"
                + "Only the borrower can grant recovery consent at origination; operators and banks cannot add it later.";
        return switch (lower(args.get(0))) {
            case "request" -> {
                need(args, 6, 7, "loan request <bank> <principal> <periods> <chunkKey|none> <none|balance|collateral|both> [auto|manual]");
                String consent = lower(args.get(5));
                if (!Set.of("none", "balance", "collateral", "both").contains(consent)) throw new UserError("Invalid recovery consent.");
                if (args.size() == 7 && !Set.of("auto", "manual").contains(lower(args.get(6)))) throw new UserError("Choose auto or manual repayment.");
                yield "Loan application recorded: " + e.banking.requestLoan(actor, args.get(1), amount(args.get(2)), integer(args.get(3)),
                        args.get(4), consent.equals("balance") || consent.equals("both"),
                        consent.equals("collateral") || consent.equals("both"), args.size() == 7 && args.get(6).equalsIgnoreCase("auto"))
                        + ". Review exact terms with loan show; the bank must approve before disbursement.";
            }
            case "show" -> {
                need(args, 2, 2, "loan show <id>");
                Banking.LoanSummary loan = e.banking.loanSummary(actor, args.get(1));
                yield loan.status() + "; outstanding " + Money.format(loan.total()) + "; currently due " + Money.format(loan.currentlyDue())
                        + "; missed payments " + loan.missedPayments()
                        + "; current automatic payments=" + loan.autoPay()
                        + (loan.nextDueAt() == 0 ? "" : "; next unpaid installment " + Instant.ofEpochMilli(loan.nextDueAt()))
                        + "\nOriginal agreement (immutable): " + loan.terms();
            }
            case "approve" -> { need(args, 2, 2, "loan approve <id>"); e.banking.approve(actor, args.get(1)); yield "Loan approved and funded from bank assets."; }
            case "cancel", "reject" -> { need(args, 2, 2, "loan cancel <id>"); e.banking.reject(actor, args.get(1)); yield "Application cancelled and collateral released."; }
            case "repay" -> {
                need(args, 3, 3, "loan repay <id> <amount|all>");
                if (args.get(2).equalsIgnoreCase("all")) e.banking.repayAll(actor, args.get(1));
                else e.banking.repay(actor, args.get(1), amount(args.get(2)));
                yield "Repayment applied to interest first, then principal.";
            }
            case "autopay" -> { need(args, 3, 3, "loan autopay <id> <true|false>"); e.banking.autoPay(actor, args.get(1), bool(args.get(2))); yield "Automatic-payment consent updated."; }
            case "recover" -> { need(args, 2, 2, "loan recover <id>"); e.banking.recover(actor, args.get(1)); yield "Eligible, explicitly consented default recovery processed; inspect loan show."; }
            default -> throw new UserError("Unknown loan action.");
        };
    }

    private String tax(Actor actor, List<String> args) {
        if (args.isEmpty()) return "tax rates | quote <amount> | report [account] [page] | arrears [account] | pay <amount|all> [account]";
        return switch (lower(args.get(0))) {
            case "rates" -> {
                need(args, 1, 1, "tax rates");
                yield String.join("\n", e.taxes.tiers(actor.chunkKey(), e.governance.nationOf(actor.id()).orElse(null)).stream()
                        .map(g -> g.name() + " (" + g.kind() + ") " + g.settings()).toList());
            }
            case "quote" -> {
                need(args, 2, 2, "tax quote <amount>");
                long gross = amount(args.get(1));
                List<String> rows = new ArrayList<>();
                for (String kind : List.of("incomeTaxBps", "salesTaxBps", "propertyTaxBps", "corporateTaxBps", "tariffBps")) {
                    List<Taxation.Charge> charges = e.taxes.quote(actor.chunkKey(), e.governance.nationOf(actor.id()).orElse(null), gross, kind);
                    rows.add(kind + ": " + Money.format(Taxation.total(charges)) + " " + charges);
                }
                yield "Independent tax illustrations; tariffs apply only on foreign imports, corporate tax only to corporate activity:\n" + String.join("\n", rows);
            }
            case "arrears" -> {
                need(args, 1, 2, "tax arrears [account]");
                String account = e.requireAccount(actor, args.size() == 2 ? args.get(1) : "selected");
                yield "Outstanding: " + money(e.taxes.arrears(account)) + "\n" + page(e.data.arrears.values().stream()
                        .filter(d -> d.payer.equals(account)).map(d -> d.kind + " → " + d.recipient + " "
                                + money(new BigInteger(d.cents)) + " (" + d.subject + ")").toList(), 1, "Unpaid obligations");
            }
            case "pay" -> {
                need(args, 2, 3, "tax pay <amount|all> [account]");
                String account = e.requireAccount(actor, args.size() == 3 ? args.get(2) : "selected");
                long maximum = args.get(1).equalsIgnoreCase("all") ? Money.MAX : amount(args.get(1));
                yield "Paid " + Money.format(e.taxes.pay(account, maximum, false)) + "; unpaid obligations remain explicitly recorded.";
            }
            case "report" -> {
                need(args, 1, 3, "tax report [account] [page]");
                String account = e.requireAccount(actor, args.size() >= 2 ? args.get(1) : "selected");
                List<EconomyData.TaxEntry> history = new ArrayList<>(e.data.taxHistory.stream()
                        .filter(t -> t.payer().equals(account) || (t.government() != null
                                && e.governance.government(t.government()).map(g -> g.account().equals(account)).orElse(false))).toList());
                Collections.reverse(history);
                yield page(history.stream().map(t -> t.status() + " " + t.kind() + " " + Money.format(t.cents())
                        + " payer=" + t.payer() + " government=" + t.government() + " " + t.subject()).toList(),
                        args.size() == 3 ? integer(args.get(2)) : 1, "Tax activity");
            }
            default -> throw new UserError("Unknown tax action.");
        };
    }

    private String admin(Actor actor, List<String> args, InventoryPort inventory) {
        EconomyEngine.requireAdmin(actor);
        if (args.isEmpty()) return "admin mint|set|take <account> <amount> | limit <account> <amount|unlimited> | cash <amount> | reload | audit | notices | suggestions";
        return switch (lower(args.get(0))) {
            case "mint", "set", "take" -> {
                need(args, 3, 3, "admin " + args.get(0) + " <account> <amount>");
                e.adminAdjust(actor, args.get(1), args.get(0), amount(args.get(2)));
                yield "Operator account adjustment recorded.";
            }
            case "limit" -> {
                need(args, 3, 3, "admin limit <account> <amount|unlimited>");
                e.ledger.spendingLimit(e.resolveAccount(actor, args.get(1)), args.get(2).equalsIgnoreCase("unlimited") ? Money.MAX : amount(args.get(2)));
                yield "UTC daily spending limit updated; previously spent amounts are not reset.";
            }
            case "cash" -> {
                need(args, 2, 2, "admin cash <amount>");
                InventoryPort.Plan plan = inventory.plan();
                long issued = e.values.addCash(plan, amount(args.get(1)), e.itemStackSize);
                plan.commit();
                e.data.transactions.add(new EconomyData.Transaction(EconomyEngine.id(), e.clock.millis(), "system:admin",
                        "system:cash", issued, "Physical cash minted for " + actor.id()));
                Ledger.trim(e.data.transactions, e.config.historyLimit);
                e.dirty.run();
                yield "Operator minted physical currency: " + Money.format(issued) + ".";
            }
            case "reload" -> { need(args, 1, 1, "admin reload"); yield hooks.reload(); }
            case "audit" -> String.join("\n", e.audit());
            case "notices" -> page(e.data.notices, 1, "Deferred-operation notices");
            case "suggestions" -> suggestions();
            default -> throw new UserError("Unknown operator economy action.");
        };
    }

    private String suggestions() {
        return page(e.data.suggestions.stream().map(s -> s.player() + " " + s.item() + " " + Money.format(s.proposedCents()) + " " + s.reason())
                .toList(), 1, "Item-price suggestions");
    }

    private String page(List<String> rows, int page, String heading) {
        if (viewPage != 1) page = viewPage;
        if (page < 1) throw new UserError("Page numbers start at one.");
        int pages = Math.max(1, (rows.size() + 11) / 12);
        if (page > pages) throw new UserError("There are only " + pages + " pages.");
        int first = (page - 1) * 12;
        return heading + " — " + rows.size() + " entries, page " + page + "/" + pages + "\n"
                + (rows.isEmpty() ? "(none)" : String.join("\n", rows.subList(first, Math.min(first + 12, rows.size()))));
    }

    private static void need(List<String> args, int minimum, int maximum, String usage) {
        if (args.size() < minimum || args.size() > maximum) throw new UserError("Usage: /sce " + usage);
    }
    private static String lower(String value) { return value.toLowerCase(Locale.ROOT); }
    private static long amount(String text) { return Money.parse(text); }
    private static long whole(String text) {
        try {
            if (!text.matches("[0-9]{1,18}")) throw new NumberFormatException();
            return Long.parseLong(text);
        } catch (NumberFormatException error) { throw new UserError("Enter a non-negative whole number."); }
    }
    private static int integer(String text) {
        long value = whole(text);
        if (value > Integer.MAX_VALUE) throw new UserError("That whole number is too large.");
        return (int) value;
    }
    private static boolean bool(String text) {
        if (text.equalsIgnoreCase("true")) return true;
        if (text.equalsIgnoreCase("false")) return false;
        throw new UserError("Enter true or false.");
    }
    private static String chunk(Actor actor, String text) { return text.equalsIgnoreCase("here") ? actor.chunkKey() : ChunkKey.parse(text).toString(); }
    private static String money(BigInteger cents) { return "$" + new BigDecimal(cents, 2).toPlainString(); }
}
