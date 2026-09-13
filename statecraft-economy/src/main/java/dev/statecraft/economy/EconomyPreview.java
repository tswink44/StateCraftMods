package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess.Transfer;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.UiText;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.statecraft.economy.EconomyPresentation.*;

/** Discardable quote/plan construction only; no commit, command execution, or temporary model rollback. */
final class EconomyPreview {
    private final EconomyEngine e;

    EconomyPreview(EconomyEngine engine) { e = engine; }

    ActionPreview preview(Actor actor, ActionSelection selection, InventoryPort inventory) {
        e.ledger.thread();
        if (!selection.namespace().equals("economy") || selection.template().isEmpty()) throw new UserError("Choose a registered economy action.");
        var action = selection.registeredAction();
        List<String> args = CommandLine.split(selection.rendered());
        Review review = new Review();
        review.line("action", "Action", selection.rendered(), true);
        if (action.intent() != ActionIntent.MUTATION) {
            review.effect("query", "Query or navigation only; no financial submission.", false);
            return review.finish();
        }
        if (e.initialGrantPending(actor)) {
            throw new UserError("Initialize your economy profile with the My balance query before reviewing an action. This applies your one-time grant before calculating any payment.");
        }
        String family = args.get(0), operation = args.size() > 1 ? args.get(1) : "";
        switch (family) {
            case "transfer" -> {
                Transfer transfer = e.quoteTransfer(actor, args.get(1), args.get(2), amount(args.get(3)), "Reviewed transfer");
                transfers(review, List.of(transfer), actor, transfer.from());
                review.amount("total", "Total debit", transfer.cents(), true);
            }
            case "cash" -> cash(actor, args, inventory, review);
            case "hub" -> hub(actor, args, inventory, review);
            case "market" -> market(actor, args, inventory, review);
            case "property" -> property(actor, args, inventory, review);
            case "company" -> company(actor, args, inventory, review);
            case "stock" -> stock(actor, args, review);
            case "bank" -> bank(actor, args, review);
            case "loan" -> loan(actor, args, review);
            case "tax" -> {
                if (!operation.equals("pay")) throw new UserError("No mutation review exists for this tax action.");
                String account = e.requireAccount(actor, args.get(3));
                long maximum = args.get(2).equalsIgnoreCase("all") ? Money.MAX : amount(args.get(2));
                Taxation.PaymentQuote quote = e.taxes.quotePayment(account, maximum);
                transfers(review, quote.transfers(), actor, account);
                review.amount("total", "Total debit", quote.paid(), true);
                review.line("remaining", "Remaining obligations", money(e.taxes.arrears(account).subtract(BigInteger.valueOf(quote.paid()))), false);
                review.line("allocation", "Allocation", shorten(quote.payments().entrySet().stream()
                        .map(entry -> entry.getKey().kind + " -> " + entry.getKey().recipient + ": " + Money.format(entry.getValue()))
                        .collect(Collectors.joining("; ")), 3800), true);
            }
            case "account" -> {
                if (!operation.equals("select")) throw new UserError("No mutation review exists for this account action.");
                review.line("account", "Account", e.requireAccount(actor, args.get(2)), true);
                review.amount("total", "Total debit", 0, true);
                review.effect("account", "Changes your selected account; it grants no new permissions.", true);
            }
            default -> throw new UserError("This action has no economy review. No action was submitted.");
        }
        return review.finish();
    }

    private void cash(Actor actor, List<String> args, InventoryPort inventory, Review review) {
        List<ItemLot> before = inventory.snapshot();
        EconomyEngine.CashQuote quote;
        if (args.get(1).equals("deposit")) {
            quote = e.quoteCashDeposit(actor, args.get(2), inventory);
            review.amount("credited", "Account credit", quote.cents(), true);
        } else if (args.get(1).equals("withdraw")) {
            long requested = amount(args.get(2));
            quote = e.quoteCashWithdrawal(actor, args.get(3), requested, inventory);
            review.amount("requested", "Requested amount", requested, true);
            review.amount("total", "Total debit", quote.cents(), true);
            review.amount("cash_remainder", "Unissued amount staying electronic", requested - quote.cents(), true);
        } else throw new UserError("Unknown cash operation.");
        review.line("account", "Account", quote.account(), true);
        review.line("denominations", "Physical denominations", denominations(before, quote.items().slots()), true);
        available(review, actor, quote.account());
        review.bind("inventory", inventory.selectedSlot() + ":" + before);
        review.physical = true;
    }

    private void hub(Actor actor, List<String> args, InventoryPort inventory, Review review) {
        if (args.get(1).equals("sell")) {
            int quantity = integer(args.get(2));
            Commerce.HubQuote quote = e.commerce.quoteHub(actor, quantity, inventory);
            item(review, quote.held(), quantity, inventory);
            review.line("recipient", "Recipient", actor.account(), true);
            review.amount("gross", "Gross amount", quote.sale().gross(), true);
            review.amount("taxes", "Taxes", quote.sale().taxes(), true);
            review.amount("net", "Net received", quote.sale().net(), true);
            charges(review, quote.taxes());
            review.physical = true;
        } else if (args.get(1).equals("suggest")) {
            ItemLot held = inventory.held();
            long price = Money.positive(amount(args.get(2)));
            String reason = CommandLine.tail(args, 3);
            e.commerce.checkSuggestion(held.item(), price, reason);
            item(review, held, 0, inventory);
            review.amount("unit_price", "Suggested unit price", price, true);
            review.line("reason", "Reason", reason, true);
            review.effect("suggestion", "Operator suggestion only; no item or money moves and live prices do not change.", true);
        } else throw new UserError("Unknown Hub mutation.");
    }

    private void market(Actor actor, List<String> args, InventoryPort inventory, Review review) {
        switch (args.get(1)) {
            case "list" -> {
                int quantity = integer(args.get(2));
                long price = amount(args.get(3));
                Commerce.MarketOffer quote = e.commerce.quoteListMarket(actor, quantity, price, inventory);
                item(review, quote.held(), quantity, inventory);
                listingOrigin(review, actor);
                review.line("seller", "Seller", actor.account(), true);
                review.amount("unit_price", "Unit price", price, true);
                review.amount("future_gross", "Future gross if all units sell", Money.multiply(price, quantity), true);
                review.amount("fee", "Fee payable now", e.config.marketListingFeeCents, true);
                review.line("recipient", "Fee recipient", "system:fees", true);
                review.effect("market_listing", "Items enter persistent escrow. Sale taxes and commission are calculated when a buyer purchases.", true);
                available(review, actor, actor.account());
                review.physical = true;
            }
            case "buy" -> {
                EconomyData.MarketListing listing = e.data.market.get(args.get(2));
                if (listing == null) throw new UserError("Unknown marketplace listing.");
                int quantity = args.get(3).equalsIgnoreCase("all") ? listing.remaining : integer(args.get(3));
                Commerce.MarketPurchase quote = e.commerce.quoteBuyMarket(actor, listing.id, quantity);
                review.line("item", "Item", listing.item.item(), true);
                review.line("quantity", "Quantity", "" + quantity, true);
                review.bind("escrow-item", itemIdentity(listing.item));
                settlement(review, actor, listing.sellerAccount, quote.settlement());
                review.delivery("purchase", "Purchased items are queued first. Automatic collection may be deferred; use market collect, not another purchase.", false);
            }
            case "cancel" -> {
                EconomyData.MarketListing listing = e.data.market.get(args.get(2));
                if (listing == null) throw new UserError("Unknown marketplace listing.");
                if (!actor.admin() && !listing.seller.equals(actor.id().toString())) throw new UserError("Only the seller may cancel this listing.");
                review.line("recipient", "Return recipient", listing.sellerAccount, true);
                review.line("item", "Item", listing.item.item(), true);
                review.line("quantity", "Quantity returned to delivery queue", "" + listing.remaining, true);
                review.bind("escrow-item", itemIdentity(listing.item));
                review.effect("market_cancel", "Unsold items return to the seller's delivery queue. Listing fees are not refunded.", true);
            }
            case "collect" -> {
                Commerce.CollectionQuote quote = e.commerce.quoteCollect(actor, inventory);
                review.line("recipient", "Recipient", actor.account(), true);
                review.line("quantity", "Items collected now", "" + quote.total(), true);
                review.delivery("collection", "Inspects at most 32 queued entries. Anything that does not fit remains queued.", false);
                review.line("items", "Collection contents", shorten(quote.collected().entrySet().stream()
                        .map(entry -> entry.getValue() + " x " + entry.getKey().item.item()).collect(Collectors.joining("; ")), 3800), true);
                review.bind("deliveries", quote.collected().entrySet().stream()
                        .map(entry -> entry.getKey().id + ":" + itemIdentity(entry.getKey().item) + ":" + entry.getValue()).collect(Collectors.joining("|")));
                review.bind("inventory", inventory.selectedSlot() + ":" + inventory.snapshot());
                review.physical = true;
            }
            default -> throw new UserError("Unknown marketplace mutation.");
        }
    }

    private void property(Actor actor, List<String> args, InventoryPort inventory, Review review) {
        String key = args.get(2).equalsIgnoreCase("here") ? actor.chunkKey() : ChunkKey.parse(args.get(2)).toString();
        review.line("property", "Property", key, true);
        review.bind("property", key);
        switch (args.get(1)) {
            case "buy" -> {
                boolean cash = args.get(args.size() - 1).equals("cash");
                String bank = args.size() >= 5 && args.get(3).equals("bank") ? args.get(4) : null;
                PropertyMarket.PurchaseQuote quote = e.property.quoteBuy(actor, key, cash, bank, inventory);
                settlementLines(review, actor, quote.listing().ownerAccount, quote.settlement());
                review.amount("cash_deposit", "Physical cash deposited into wallet", quote.cashDeposit(), true);
                Banking.WalletFunding funding = quote.bankFunding();
                long bankAmount = funding == null ? 0 : funding.transfer().cents();
                review.amount("bank_funding", "Bank withdrawal to wallet", bankAmount, true);
                review.amount("bank_fee", "Bank withdrawal fee", funding == null ? 0 : funding.fee(), true);
                if (funding != null) {
                    review.line("bank", "Funding bank account", funding.transfer().from(), true);
                    review.amount("deposit_debit", "Total bank-deposit debit", funding.debit(), true);
                    review.amount("funded_deposit", "Available funded deposit", funding.balanceBefore(), false);
                }
                review.amount("wallet_contribution", "Existing wallet funds used",
                        Math.max(0, quote.settlement().buyerCost() - quote.cashDeposit() - bankAmount), true);
                review.amount("wallet_after", "Wallet after purchase", quote.payment().balanceAfter(actor.account()), false);
                review.effect("property_purchase", "Private title transfers only. Existing property-tax assessments stay with the original payer; political territory is unchanged.", true);
                if (cash) {
                    review.line("denominations", "Physical denominations", denominations(inventory.snapshot(), quote.cash().slots()), true);
                    review.bind("inventory", inventory.selectedSlot() + ":" + inventory.snapshot());
                    review.physical = true;
                }
            }
            case "list" -> {
                long price = amount(args.get(3));
                GovernanceAccess.ClaimView claim = e.property.quoteList(actor, key, price);
                review.line("seller", "Seller", claim.ownerAccount(), true);
                review.amount("unit_price", "Asking price", price, true);
                review.amount("total", "Total debit now", 0, true);
                review.effect("property_listing", "Creates a property offer and ownership lock. No sale proceeds are paid until a purchase.", true);
            }
            case "delist" -> {
                EconomyData.PropertyListing listing = e.property.quoteDelist(actor, key);
                review.line("seller", "Seller", listing.ownerAccount, true);
                review.amount("unit_price", "Removed asking price", listing.price, true);
                review.effect("property_delist", "Removes this offer without selling the title or moving money.", true);
            }
            default -> throw new UserError("Unknown property mutation.");
        }
    }

    private void company(Actor actor, List<String> args, InventoryPort inventory, Review review) {
        if (args.get(1).equals("pay")) {
            List<Transfer> payments = e.commerce.quoteCompanyPay(actor, args.get(2), args.get(3), amount(args.get(4)), inventory);
            transfers(review, payments, actor, payments.get(0).from());
            review.amount("payment", "Recipient payment", payments.get(0).cents(), true);
            review.amount("fee", "Company transfer fee", payments.get(1).cents(), true);
            review.amount("total", "Total debit", Money.add(payments.get(0).cents(), payments.get(1).cents()), true);
        } else if (args.get(1).equals("dividend")) {
            Commerce.DividendQuote quote = e.commerce.quoteDividend(actor, args.get(2), amount(args.get(3)), inventory);
            transfers(review, quote.transfers(), actor, quote.company().account());
            review.amount("budget", "Dividend budget", quote.dividend().requested(), true);
            review.amount("taxes", "Corporate tax", quote.dividend().corporateTax(), true);
            review.amount("net", "Paid to shareholders", quote.dividend().paidToShareholders(), true);
            review.amount("retained", "Rounding remainder retained", quote.dividend().retainedRemainder(), true);
            review.amount("total", "Total debit", Money.add(quote.dividend().paidToShareholders(), quote.dividend().corporateTax()), true);
            charges(review, quote.taxes());
        } else throw new UserError("Unknown company mutation.");
    }

    private void stock(Actor actor, List<String> args, Review review) {
        switch (args.get(1)) {
            case "buy" -> {
                EconomyData.StockListing listing = e.data.stocks.get(args.get(2));
                if (listing == null) throw new UserError("Unknown stock listing.");
                long quantity = args.get(3).equalsIgnoreCase("all") ? listing.remaining : whole(args.get(3));
                Commerce.Settlement quote = e.commerce.quoteBuyStock(actor, listing.id, quantity);
                review.line("company", "Company identity", listing.company, true);
                review.line("quantity", "Shares", "" + quantity, true);
                settlement(review, actor, "player:" + listing.seller, quote);
            }
            case "list" -> {
                long quantity = whole(args.get(3)), price = amount(args.get(4));
                GovernanceAccess.CompanyView company = e.commerce.quoteListStock(actor, args.get(2), quantity, price);
                listingOrigin(review, actor);
                transfers(review, List.of(new Transfer(actor.account(), "system:fees", e.config.stockListingFeeCents, "Stock listing fee")), actor, actor.account());
                review.line("company", "Company identity", company.id(), true);
                review.line("quantity", "Shares reserved", "" + quantity, true);
                review.amount("unit_price", "Unit price", price, true);
                review.amount("future_gross", "Future gross if all shares sell", Money.multiply(price, quantity), true);
                review.amount("fee", "Fee payable now", e.config.stockListingFeeCents, true);
                review.effect("stock_listing", "Reserves the seller's shares; no buyer proceeds are paid now.", true);
            }
            case "cancel" -> {
                EconomyData.StockListing listing = e.data.stocks.get(args.get(2));
                if (listing == null) throw new UserError("Unknown stock listing.");
                if (!actor.admin() && !listing.seller.equals(actor.id().toString())) throw new UserError("Only the shareholder may cancel this listing.");
                review.line("company", "Company identity", listing.company, true);
                review.line("recipient", "Shareholder", "player:" + listing.seller, true);
                review.line("quantity", "Shares released", "" + listing.remaining, true);
                review.effect("stock_cancel", "Releases only the unsold reservation. Listing fees are not refunded.", true);
            }
            default -> throw new UserError("Unknown stock mutation.");
        }
    }

    private void bank(Actor actor, List<String> args, Review review) {
        switch (args.get(1)) {
            case "create" -> {
                long capital = amount(args.get(4));
                EconomyData.Bank bank = e.banking.quoteCreate(actor, args.get(2), args.get(3), capital, integer(args.get(5)), integer(args.get(6)));
                transfers(review, e.banking.creationTransfers(bank, capital), actor, "company:" + bank.company);
                review.line("bank", "New bank identity and name", bank.id + " — " + bank.name, true);
                review.amount("capital", "Seed capital", capital, true);
                review.amount("fee", "Creation fee", e.config.bankCreationFeeCents, true);
                review.amount("total", "Total debit", Money.add(capital, e.config.bankCreationFeeCents), true);
                bankRates(review, bank.depositInterestBps, bank.loanInterestBps, 0, 0, 0, bank.interestPeriodMillis);
            }
            case "deposit" -> {
                long amount = amount(args.get(2));
                Banking.DepositQuote quote = e.banking.quoteDeposit(actor, args.get(3), amount);
                transfers(review, List.of(quote.transfer()), actor, actor.account());
                review.amount("total", "Total wallet debit", amount, true);
                review.amount("fee", "Deposit fee retained by bank", quote.bank().depositFeeCents, true);
                review.amount("credited", "Deposit credit", quote.credited(), true);
                EconomyData.Deposit existing = quote.bank().deposits.get(actor.id().toString());
                review.line("rate", "Deposit contract rate and period", (existing != null && existing.principal > 0
                        ? existing.rateBps + " bps / " + existing.periodMillis : quote.bank().depositInterestBps + " bps / " + quote.bank().interestPeriodMillis) + " ms", true);
            }
            case "withdraw" -> {
                Banking.WalletFunding quote = e.banking.quoteWalletFunding(actor, args.get(3), amount(args.get(2)));
                e.ledger.prepare(List.of(quote.transfer()), List.of(), true, quote.reserveOverrides());
                review.line("source", "Source bank", quote.transfer().from(), true);
                review.line("recipient", "Recipient", actor.account(), true);
                review.amount("net", "Wallet credit", quote.transfer().cents(), true);
                review.amount("fee", "Withdrawal fee retained by bank", quote.fee(), true);
                review.amount("total", "Total deposit debit", quote.debit(), true);
                review.amount("funded_deposit", "Available funded deposit", quote.balanceBefore(), false);
            }
            case "terms" -> {
                EconomyData.Bank bank = e.banking.bank(args.get(2), true);
                e.banking.requireManager(actor, bank);
                int deposit = integer(args.get(3)), loan = integer(args.get(4)), origination = integer(args.get(5));
                long depositFee = amount(args.get(6)), withdrawalFee = amount(args.get(7));
                e.banking.checkRates(deposit, loan, origination);
                review.line("bank", "Bank identity", bank.id, true);
                bankRates(review, deposit, loan, origination, depositFee, withdrawalFee, bank.interestPeriodMillis);
                review.amount("total", "Total debit now", 0, true);
                review.effect("bank_terms", "Updates future interest contracts and current voluntary deposit/withdrawal fees. Existing interest contracts remain unchanged.", true);
            }
            case "associate" -> {
                EconomyData.Bank bank = e.banking.bank(args.get(2), true);
                review.line("bank", "Associated bank", bank.id, true);
                review.effect("bank_association", "Changes a preference only. Existing deposits and loans are not transferred.", true);
            }
            default -> throw new UserError("Unknown bank mutation.");
        }
    }

    private void loan(Actor actor, List<String> args, Review review) {
        String operation = args.get(1);
        if (operation.equals("request")) {
            String consent = args.get(6), mode = args.get(7);
            if (!Set.of("none", "balance", "collateral", "both").contains(consent) || !Set.of("auto", "manual").contains(mode)) {
                throw new UserError("Choose explicit recovery consent and repayment mode.");
            }
            EconomyData.Loan quote = e.banking.quoteLoanRequest(actor, args.get(2), amount(args.get(3)), integer(args.get(4)), args.get(5),
                    consent.equals("balance") || consent.equals("both"), consent.equals("collateral") || consent.equals("both"), mode.equals("auto"));
            loanTerms(review, quote);
            review.amount("total", "Total debit now", 0, true);
            review.amount("future_disbursement", "Future net disbursement if approved", quote.originalPrincipal - quote.originationFee, true);
            review.effect("loan_request", "Application only; the bank must approve funding. Named collateral is reserved now.", true);
            return;
        }
        EconomyData.Loan loan = e.banking.loan(args.get(2));
        Banking.LoanSummary summary = e.banking.previewLoan(actor, loan.id);
        review.line("loan", "Loan identity", loan.id, true);
        switch (operation) {
            case "approve" -> {
                Banking.LoanFunding quote = e.banking.quoteApproval(actor, loan.id);
                e.ledger.prepare(List.of(quote.transfer()), List.of(), true, quote.reserves());
                review.line("source", "Source bank", quote.transfer().from(), true);
                review.line("recipient", "Borrower wallet", quote.transfer().to(), true);
                review.amount("total", "Bank cash debit / borrower credit", quote.transfer().cents(), true);
                loanTerms(review, loan);
                review.amount("balance", "Current bank cash", e.balance(quote.transfer().from()), false);
                review.amount("available", "Available after lending reserve",
                        Math.max(0, e.balance(quote.transfer().from()) - quote.reserves().get(quote.transfer().from())), false);
            }
            case "repay" -> {
                if (!actor.admin() && !loan.borrower.equals(actor.id().toString())) throw new UserError("Only the borrower may repay this loan from their wallet.");
                if (!Set.of("ACTIVE", "DEFAULTED").contains(loan.status)) throw new UserError("This loan is not outstanding.");
                EconomyData.Bank bank = e.banking.bank(loan.bank, true);
                long amount = args.get(3).equalsIgnoreCase("all") ? summary.total() : Money.positive(amount(args.get(3)));
                if (amount > summary.total()) throw new UserError("The repayment exceeds the remaining debt.");
                transfers(review, List.of(new Transfer(actor.account(), bank.account(), amount, "Loan repayment " + loan.id)), actor, actor.account());
                review.amount("total", "Total debit", amount, true);
                long interest = Math.min(amount, summary.interest());
                review.amount("interest", "Interest paid first", interest, true);
                review.amount("principal", "Principal repaid", amount - interest, true);
                review.amount("remaining", "Remaining loan debt", summary.total() - amount, true);
            }
            case "autopay" -> {
                if (!loan.borrower.equals(actor.id().toString())) throw new UserError("Only the borrower may change automatic-payment consent.");
                if (!Set.of("REQUESTED", "ACTIVE", "DEFAULTED").contains(loan.status)) throw new UserError("This loan is closed.");
                if (!Set.of("true", "false").contains(args.get(3))) throw new UserError("Choose true or false.");
                review.line("autopay_current", "Current automatic payments", "" + loan.autoPay, true);
                review.line("autopay_new", "New automatic payments", args.get(3), true);
                review.line("account", "Future payment source", actor.account(), true);
                review.amount("currently_due", "Currently due", summary.currentlyDue(), false);
                review.amount("total", "Total debit now", 0, true);
                review.effect("autopay", "Changes future scheduled payments only. It does not add recovery consent or rewrite the original agreement.", true);
            }
            case "cancel" -> {
                if (!loan.borrower.equals(actor.id().toString())) e.banking.requireManager(actor, e.banking.bank(loan.bank, true));
                if (!loan.status.equals("REQUESTED")) throw new UserError("Only a pending application can be cancelled.");
                review.line("collateral", "Collateral released", loan.collateral == null ? "None" : loan.collateral, true);
                review.effect("loan_cancel", "Cancels an unfunded application; no loan disbursement or repayment occurs.", true);
            }
            default -> throw new UserError("Unknown loan mutation.");
        }
    }

    private void loanTerms(Review review, EconomyData.Loan loan) {
        review.line("source", "Funding bank", "bank:" + loan.bank, true);
        review.line("recipient", "Borrower", "player:" + loan.borrower, true);
        review.amount("principal", "Contract principal", loan.originalPrincipal, true);
        review.amount("fee", "Origination fee retained if funded", loan.originationFee, true);
        review.amount("interest_cap", "Lifetime interest cap", loan.interestCap, true);
        review.line("rate", "Simple interest", loan.rateBps + " bps / " + loan.periodMillis + " ms", true);
        review.line("periods", "Repayment periods", "" + loan.periods, true);
        review.line("collateral", "Specifically pledged property", loan.collateral == null ? "None — unsecured" : loan.collateral, true);
        review.line("consent", "Balance recovery / repossession consent", loan.consentBalanceSeizure + " / " + loan.consentRepossession, true);
        review.line("autopay_current", "Current automatic payments", "" + loan.autoPay, true);
        review.line("original_terms", "Original agreement", loan.terms, true);
    }

    private void bankRates(Review review, int deposit, int loan, int origination, long depositFee, long withdrawalFee, long period) {
        review.line("rates", "Deposit / loan / origination basis points", deposit + " / " + loan + " / " + origination, true);
        review.line("period", "Interest period (ms)", "" + period, true);
        review.amount("deposit_fee", "Future deposit fee", depositFee, true);
        review.amount("withdrawal_fee", "Future withdrawal fee", withdrawalFee, true);
    }

    private void settlement(Review review, Actor actor, String seller, Commerce.Settlement quote) {
        transfers(review, quote.transfers(), actor, actor.account());
        settlementLines(review, actor, seller, quote);
    }

    private void settlementLines(Review review, Actor actor, String seller, Commerce.Settlement quote) {
        review.line("source", "Buyer wallet", actor.account(), true);
        review.line("seller", "Seller", seller, true);
        review.amount("gross", "Sale price before buyer taxes", quote.buyerCost() - quote.taxes().buyerTotal(), true);
        review.amount("buyer_taxes", "Buyer taxes and tariffs", quote.taxes().buyerTotal(), true);
        review.amount("seller_taxes", "Seller taxes", quote.taxes().sellerTotal(), true);
        review.amount("commission", "Commission", quote.fee(), true);
        review.amount("total", "Total purchase cost", quote.buyerCost(), true);
        review.amount("net", "Seller net proceeds", quote.sellerNet(), true);
        List<Taxation.Charge> charges = new ArrayList<>(quote.taxes().buyer());
        charges.addAll(quote.taxes().seller());
        charges(review, charges);
        available(review, actor, actor.account());
        review.bind("payments", paymentIdentity(quote.transfers()));
    }

    private void charges(Review review, List<Taxation.Charge> charges) {
        if (charges.isEmpty()) return;
        review.line("tax_recipients", "Tax recipients", shorten(charges.stream()
                .map(charge -> charge.kind() + " -> " + charge.account() + ": " + Money.format(charge.cents()))
                .collect(Collectors.joining("; ")), 3800), true);
    }

    private void transfers(Review review, List<Transfer> transfers, Actor actor, String source) {
        e.ledger.prepare(transfers);
        review.line("source", "Source account", source, true);
        Map<String, Long> recipients = new LinkedHashMap<>();
        transfers.stream().filter(transfer -> transfer.cents() > 0 && !transfer.from().equals(transfer.to()))
                .forEach(transfer -> recipients.merge(transfer.to(), transfer.cents(), Money::add));
        String parties = recipients.entrySet().stream().limit(20).map(entry -> entry.getKey() + ": " + Money.format(entry.getValue()))
                .collect(Collectors.joining("; "));
        if (recipients.size() > 20) parties += "; " + (recipients.size() - 20) + " additional recipients in the current share/tax register";
        review.line("recipients", "Recipients", shorten(parties.isEmpty() ? "No current payment" : parties, 3800), true);
        review.bind("payments", paymentIdentity(transfers));
        available(review, actor, source);
    }

    private void available(Review review, Actor actor, String source) {
        e.requireAccount(actor, source);
        review.amount("balance", "Current source balance", e.balance(source), false);
        review.amount("available", "Available after protected reserves", e.spendable(source), false);
    }

    private void item(Review review, ItemLot held, int quantity, InventoryPort inventory) {
        review.line("item", "Item", held.item(), true);
        if (quantity > 0) review.line("quantity", "Quantity", "" + quantity, true);
        review.bind("held", inventory.selectedSlot() + ":" + held.count() + ":" + itemIdentity(held));
    }

    private void listingOrigin(Review review, Actor actor) {
        String chunk = actor.chunkKey();
        String nation = e.governance.nationOf(actor.id()).orElse("");
        review.line("source_chunk", "Source tax location", chunk, true);
        review.line("source_nation", "Seller nation", nation, true);
        review.bind("listing-origin", chunk + "|" + nation);
    }

    private String denominations(List<ItemLot> before, List<ItemLot> after) {
        Map<String, Long> changes = new java.util.TreeMap<>();
        for (ItemLot lot : before) if (lot.snbt().isEmpty() && e.values.currency().containsKey(lot.item())) changes.merge(lot.item(), -(long) lot.count(), Long::sum);
        for (ItemLot lot : after) if (lot.snbt().isEmpty() && e.values.currency().containsKey(lot.item())) changes.merge(lot.item(), (long) lot.count(), Long::sum);
        return shorten(changes.entrySet().stream().filter(entry -> entry.getValue() != 0)
                .map(entry -> entry.getKey() + ": " + (entry.getValue() > 0 ? "+" : "") + entry.getValue())
                .collect(Collectors.joining("; ")), 3800);
    }

    private static String itemIdentity(ItemLot item) {
        return item.item() + "|" + item.fullStackData() + "|" + item.maxStackSize() + "|" + item.snbt();
    }
    private static String paymentIdentity(List<Transfer> transfers) {
        return transfers.stream().filter(transfer -> transfer.cents() > 0 && !transfer.from().equals(transfer.to()))
                .map(transfer -> transfer.from() + ">" + transfer.to() + ":" + transfer.cents()).sorted().collect(Collectors.joining("|"));
    }
    private static long amount(String raw) { return Money.parse(raw); }
    private static long whole(String raw) {
        try { return Long.parseLong(raw); } catch (NumberFormatException invalid) { throw new UserError("Enter a whole number."); }
    }
    private static int integer(String raw) {
        long result = whole(raw);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new UserError("The whole number is out of range.");
        return (int) result;
    }

    private static final class Review {
        final List<ActionPreview.Line> lines = new ArrayList<>();
        final StringBuilder bindings = new StringBuilder();
        boolean physical;

        void line(String key, String label, String value, boolean material) {
            String labelKey = label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_$", "");
            lines.add(new ActionPreview.Line(t("review." + key + "." + labelKey, label), UiText.literal(shorten(value, 4096)), material));
        }
        void amount(String key, String label, long cents, boolean material) { line(key, label, Money.format(cents), material); }
        void effect(String key, String explanation, boolean material) {
            lines.add(new ActionPreview.Line(t("review.effect.effect", "Effect"), t("review.effect." + key, explanation), material));
        }
        void delivery(String key, String explanation, boolean material) {
            lines.add(new ActionPreview.Line(t("review.delivery.delivery", "Delivery"), t("review.delivery." + key, explanation), material));
        }
        void bind(String kind, String value) { bindings.append(kind).append(':').append(ActionPreview.digest(value)).append(';'); }
        ActionPreview finish() {
            return new ActionPreview(t("review.title", "Review economy action"), lines,
                    physical ? t("review.physical_warning", "Inventory and world data have separate Minecraft persistence. Review physical items carefully; do not repeat an uncertain operation.")
                            : t("review.warning", "This is a quote, not a completed action. Eligibility and material terms are checked again immediately before submission."),
                    bindings.toString());
        }
    }
}
