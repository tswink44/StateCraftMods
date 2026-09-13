package dev.statecraft.economy.forge;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuPage.Action;
import dev.statecraft.api.MenuRegistry;

import java.util.List;

final class EconomyMenus {
    private EconomyMenus() {}

    static void register() {
        page("atm", "StateCraft ATM", "balance",
                action("Select account", "account select <account>"),
                action("My accounts", "accounts"),
                action("Deposit all physical cash", "cash deposit <account>"),
                action("Withdraw cash", "cash withdraw <amount> <account>"),
                action("Electronic transfer", "transfer <fromAccount> <toAccount> <amount>"),
                action("Activity", "history <account> <page>"),
                action("Daily spending allowance", "limits <account>"),
                action("Banks and loans", "gui economy:bank"),
                action("Marketplace", "gui economy:market"),
                action("Tax reports", "gui economy:tax"));
        page("hub", "Trading Hub", "hub settings",
                action("Sell held item", "hub sell <quantity>"),
                action("Search prices", "hub prices <itemSearch>"),
                action("Suggest held-item price", "hub suggest <unitPrice> <reason>"),
                action("Recipe guide", "gui economy:guide"),
                action("My balance", "balance me"));
        page("market", "Player Marketplace", "market search",
                action("List held items", "market list <quantity> <unitPrice>"),
                action("Search listings", "market search <search>"),
                action("Inspect listing NBT", "market inspect <listingId>"),
                action("Listing search page", "page <page> market search <search>"),
                action("Buy full or partial listing", "market buy <listingId> <quantityOrAll>"),
                action("My listings", "market own"),
                action("Cancel listing", "market cancel <listingId>"),
                action("Collect deliveries", "market collect"),
                action("Queued deliveries", "market deliveries"),
                action("Property market", "gui economy:property"));
        page("property", "Property Market", "property listings",
                action("Current chunk valuation", "property value here"),
                action("List property", "property list <chunkKeyOrHere> <price>"),
                action("Delist property", "property delist <chunkKeyOrHere>"),
                action("Buy with electronic balance", "property buy <chunkKeyOrHere>"),
                action("Buy with cash funding near an ATM", "property buy <chunkKeyOrHere> cash"),
                action("Buy using wallet and bank deposit", "property buy <chunkKeyOrHere> bank <bank>"),
                action("Buy using wallet, bank, and cash", "property buy <chunkKeyOrHere> bank <bank> cash"),
                action("Property listings page", "page <page> property listings"),
                action("My property", "property own"),
                action("Taxes", "gui economy:tax"));
        page("company", "Company Vault", "accounts",
                action("Company balance", "company balance <company>"),
                action("Company payment", "company pay <company> <recipient> <amount>"),
                action("Distribute dividend budget", "company dividend <company> <budget>"),
                action("Company fees and arrears", "company fees <company>"),
                action("Deposit physical cash", "cash deposit <companyAccount>"),
                action("Withdraw physical cash", "cash withdraw <amount> <companyAccount>"),
                action("Bank management", "gui economy:bank"),
                action("Stock market", "gui economy:stock"));
        page("bank", "Banks and Loans", "bank list",
                action("Choose associated bank", "bank associate <bank>"),
                action("My bank deposit", "bank balance <bank>"),
                action("Deposit electronic money", "bank deposit <amount> <bank>"),
                action("Withdraw to wallet", "bank withdraw <amount> <bank>"),
                action("Create company bank", "bank create <company> <name> <seedCapital> <depositBps> <loanBps>"),
                action("Bank report", "bank report <bank>"),
                action("My loans", "bank loans"),
                action("Apply with explicit recovery consent", "loan request <bank> <principal> <periods> <collateralOrNone> <noneBalanceCollateralOrBoth> <autoOrManual>"),
                action("Review loan terms", "loan show <loanId>"),
                action("Approve application", "loan approve <loanId>"),
                action("Repay loan", "loan repay <loanId> <amountOrAll>"),
                action("Bank terms and fees", "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>"));
        page("stock", "Stock Market", "stock search",
                action("Search stock listings", "stock search <companySearch>"),
                action("Stock search page", "page <page> stock search <companySearch>"),
                action("Reserve shares for sale", "stock list <company> <shares> <unitPrice>"),
                action("Buy shares", "stock buy <listingId> <sharesOrAll>"),
                action("My share listings", "stock own"),
                action("Cancel share listing", "stock cancel <listingId>"),
                action("Company finance", "gui economy:company"));
        page("tax", "Taxes and Obligations", "tax rates",
                action("Illustrate current taxes", "tax quote <amount>"),
                action("Account tax report", "tax report <account> <page>"),
                action("Account arrears", "tax arrears <account>"),
                action("Pay obligations", "tax pay <amountOrAll> <account>"),
                action("Current property value", "property value here"));
        page("guide", "Economy Recipe and Action Guide", "guide",
                action("Supported commands", "help"),
                action("Configured merchants", "merchant list"),
                action("ATM", "gui economy:atm"),
                action("Trading Hub", "gui economy:hub"),
                action("Marketplace", "gui economy:market"),
                action("Company Vault", "gui economy:company"),
                action("Stock Market", "gui economy:stock"));
    }

    private static Action action(String label, String command) { return new Action(label, command); }
    private static void page(String id, String title, String query, Action... actions) {
        MenuRegistry.register(new MenuPage("economy:" + id, title, query, List.of(actions)));
    }
}
