package dev.statecraft.economy.forge;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuPage.Action;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.ActionIntent;

import java.util.List;

final class EconomyMenus {
    private EconomyMenus() {}

    static void register() {
        page("dashboard", "Economy Pending Work", "balance",
                navigation("Accounts", "gui economy:atm"),
                navigation("Banks and loans", "gui economy:bank"),
                navigation("Marketplace", "gui economy:market"),
                mutation("Collect deliveries", "market collect"),
                navigation("Taxes and obligations", "gui economy:tax"));
        MenuRegistry.register(new MenuPage("economy:detail", "Economy Details", "help",
                List.of(navigation("Pending work", "gui economy:dashboard"), navigation("Accounts", "gui economy:atm")), false));
        page("atm", "StateCraft ATM", "balance",
                mutation("Select account", "account select <account>"),
                query("My accounts", "accounts"),
                financial("Deposit all physical cash", "cash deposit <account>"),
                financial("Withdraw cash", "cash withdraw <amount> <account>"),
                financial("Electronic transfer", "transfer <fromAccount> <toAccount> <amount>"),
                query("Activity", "history <account> <page>"),
                query("Daily spending allowance", "limits <account>"),
                navigation("Banks and loans", "gui economy:bank"),
                navigation("Marketplace", "gui economy:market"),
                navigation("Tax reports", "gui economy:tax"),
                navigation("Pending work", "gui economy:dashboard"));
        page("hub", "Trading Hub", "hub settings",
                financial("Sell held item", "hub sell <quantity>"),
                query("Search prices", "hub prices <itemSearch>"),
                mutation("Suggest held-item price", "hub suggest <unitPrice> <reason>"),
                navigation("Recipe guide", "gui economy:guide"),
                query("My balance", "balance me"));
        page("market", "Player Marketplace", "market search",
                financial("List held items", "market list <quantity> <unitPrice>"),
                query("Search listings", "market search <search>"),
                query("Inspect listing NBT", "market inspect <listingId>"),
                query("Listing search page", "page <page> market search <search>"),
                financial("Buy full or partial listing", "market buy <listingId> <quantityOrAll>"),
                query("My listings", "market own"),
                mutation("Cancel listing", "market cancel <listingId>"),
                mutation("Collect deliveries", "market collect"),
                query("Queued deliveries", "market deliveries"),
                navigation("Deliveries", "gui economy:deliveries"),
                navigation("Property market", "gui economy:property"));
        page("deliveries", "Queued Deliveries", "market deliveries",
                mutation("Collect deliveries", "market collect"), navigation("Marketplace", "gui economy:market"),
                navigation("Pending work", "gui economy:dashboard"));
        page("property", "Property Market", "property listings",
                query("Current chunk valuation", "property value here"),
                query("Property valuation", "property value <chunkKeyOrHere>"),
                mutation("List property", "property list <chunkKeyOrHere> <price>"),
                mutation("Delist property", "property delist <chunkKeyOrHere>"),
                financial("Buy with electronic balance", "property buy <chunkKeyOrHere>"),
                financial("Buy with cash funding near an ATM", "property buy <chunkKeyOrHere> cash"),
                financial("Buy using wallet and bank deposit", "property buy <chunkKeyOrHere> bank <bank>"),
                financial("Buy using wallet, bank, and cash", "property buy <chunkKeyOrHere> bank <bank> cash"),
                query("Property listings page", "page <page> property listings"),
                query("My property", "property own"),
                navigation("Taxes", "gui economy:tax"));
        page("company", "Company Vault", "accounts",
                query("Company balance", "company balance <company>"),
                financial("Company payment", "company pay <company> <recipient> <amount>"),
                financial("Distribute dividend budget", "company dividend <company> <budget>"),
                query("Company fees and arrears", "company fees <company>"),
                financial("Deposit physical cash", "cash deposit <companyAccount>"),
                financial("Withdraw physical cash", "cash withdraw <amount> <companyAccount>"),
                navigation("Bank management", "gui economy:bank"),
                navigation("Stock market", "gui economy:stock"));
        page("bank", "Banks and Loans", "bank list",
                mutation("Choose associated bank", "bank associate <bank>"),
                query("My bank deposit", "bank balance <bank>"),
                financial("Deposit electronic money", "bank deposit <amount> <bank>"),
                financial("Withdraw to wallet", "bank withdraw <amount> <bank>"),
                financial("Create company bank", "bank create <company> <name> <seedCapital> <depositBps> <loanBps>"),
                query("Bank report", "bank report <bank>"),
                query("My loans", "bank loans"),
                query("Managed bank loans", "bank loans <bank>"),
                mutation("Apply with explicit recovery consent", "loan request <bank> <principal> <periods> <collateralOrNone> <noneBalanceCollateralOrBoth> <autoOrManual>"),
                query("Review loan terms", "loan show <loanId>"),
                financial("Approve application", "loan approve <loanId>"),
                financial("Repay loan", "loan repay <loanId> <amountOrAll>"),
                mutation("Enable automatic payments", "loan autopay <loanId> true"),
                mutation("Disable automatic payments", "loan autopay <loanId> false"),
                mutation("Cancel loan application", "loan cancel <loanId>"),
                mutation("Bank terms and fees", "bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>"),
                navigation("Loan management", "gui economy:loans"));
        page("loans", "Loan Management", "bank loans",
                query("Review loan terms", "loan show <loanId>"),
                financial("Repay loan", "loan repay <loanId> <amountOrAll>"),
                mutation("Enable automatic payments", "loan autopay <loanId> true"),
                mutation("Disable automatic payments", "loan autopay <loanId> false"),
                financial("Approve application", "loan approve <loanId>"),
                mutation("Cancel loan application", "loan cancel <loanId>"),
                navigation("Banks", "gui economy:bank"));
        page("stock", "Stock Market", "stock search",
                query("Search stock listings", "stock search <companySearch>"),
                query("Stock search page", "page <page> stock search <companySearch>"),
                financial("Reserve shares for sale", "stock list <company> <shares> <unitPrice>"),
                financial("Buy shares", "stock buy <listingId> <sharesOrAll>"),
                query("My share listings", "stock own"),
                mutation("Cancel share listing", "stock cancel <listingId>"),
                navigation("Company finance", "gui economy:company"));
        page("tax", "Taxes and Obligations", "tax rates",
                query("Illustrate current taxes", "tax quote <amount>"),
                query("Account tax report", "tax report <account> <page>"),
                query("Account arrears", "tax arrears <account>"),
                financial("Pay obligations", "tax pay <amountOrAll> <account>"),
                query("Current property value", "property value here"));
        page("guide", "Economy Recipe and Action Guide", "guide",
                query("Supported commands", "help"),
                query("Configured merchants", "merchant list"),
                navigation("ATM", "gui economy:atm"),
                navigation("Trading Hub", "gui economy:hub"),
                navigation("Marketplace", "gui economy:market"),
                navigation("Company Vault", "gui economy:company"),
                navigation("Stock Market", "gui economy:stock"),
                navigation("Pending work", "gui economy:dashboard"));
    }

    private static Action query(String label, String command) { return new Action(label, command, ActionIntent.QUERY, false); }
    private static Action mutation(String label, String command) { return new Action(label, command, ActionIntent.MUTATION, false); }
    private static Action financial(String label, String command) { return new Action(label, command, ActionIntent.MUTATION, true); }
    private static Action navigation(String label, String command) { return new Action(label, command, ActionIntent.NAVIGATION, false); }
    private static void page(String id, String title, String query, Action... actions) {
        MenuRegistry.register(new MenuPage("economy:" + id, title, query, List.of(actions),
                !id.equals("dashboard") && !id.equals("guide")));
    }
}
