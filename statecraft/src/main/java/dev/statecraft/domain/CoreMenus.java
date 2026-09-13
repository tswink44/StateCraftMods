package dev.statecraft.domain;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.ActionIntent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CoreMenus {
    private CoreMenus() {}

    public static synchronized void register() {
        Set<String> existing = new HashSet<>();
        MenuRegistry.pages().forEach(page -> existing.add(page.id()));
        page(existing, "main", "StateCraft", "info",
                navigation("Pending work", "statecraft:dashboard"), navigation("Governments", "statecraft:nations"),
                navigation("Companies", "statecraft:companies"), navigation("Personal mail", "statecraft:mail"),
                query("Help", "help <section> <page>"), query("Profile", "profile <player>"));
        page(existing, "dashboard", "Pending Work", "info",
                navigation("Overview", "statecraft:main"), navigation("Invitations", "statecraft:invitations"),
                navigation("Legislature", "statecraft:legislature"), navigation("Contracts", "statecraft:contracts"),
                navigation("Diplomacy", "statecraft:diplomacy"), navigation("Personal mail", "statecraft:mail"));
        page(existing, "detail", "Details", "info", false,
                navigation("Pending work", "statecraft:dashboard"), navigation("Overview", "statecraft:main"),
                navigation("Governments", "statecraft:nations"), navigation("Companies", "statecraft:companies"),
                navigation("Claims", "statecraft:claims"), navigation("Personal mail", "statecraft:mail"));
        page(existing, "nations", "Nations", "nation list",
                query("List", "nation list <page>"), financial("Create", "nation create <name>"),
                query("Information", "nation info <nation>"), mutation("Join", "nation join <nation>"),
                mutation("Leave", "nation leave <nation>"), mutation("Rename", "nation rename <nation> <name>"),
                mutation("Description", "nation description <nation> <description>"), mutation("Tag", "nation tag <nation> <tag>"),
                mutation("Flag", "nation flag <nation> <flag>"), query("Settings", "nation settings <nation>"),
                mutation("Set policy", "nation setting <nation> <key> <value>"),
                mutation("Disband", "nation disband <nation>"), mutation("Disband subtree", "nation disband <nation> cascade"));
        page(existing, "states", "States", "state list",
                query("List", "state list <page>"), financial("Create", "state create <nation> <name> <governor>"),
                query("Information", "state info <state>"), mutation("Join", "state join <state>"),
                mutation("Leave", "state leave <state>"), mutation("Rename", "state rename <state> <name>"),
                mutation("Description", "state description <state> <description>"), mutation("Tag", "state tag <state> <tag>"),
                mutation("Flag", "state flag <state> <flag>"), query("Settings", "state settings <state>"),
                mutation("Set policy", "state setting <state> <key> <value>"),
                mutation("Disband", "state disband <state>"), mutation("Disband subtree", "state disband <state> cascade"));
        page(existing, "cities", "Cities", "city list",
                query("List", "city list <page>"), financial("Create", "city create <state> <name> <mayor>"),
                query("Information", "city info <city>"), mutation("Join", "city join <city>"),
                mutation("Leave", "city leave <city>"), mutation("Rename", "city rename <city> <name>"),
                mutation("Description", "city description <city> <description>"), mutation("Tag", "city tag <city> <tag>"),
                mutation("Flag", "city flag <city> <flag>"), query("Settings", "city settings <city>"),
                mutation("Set policy", "city setting <city> <key> <value>"),
                mutation("Disband", "city disband <city>"), mutation("Disband subtree", "city disband <city> cascade"));
        page(existing, "members", "Citizens and Roles", "government list",
                query("Citizens", "government members <government> <page>"),
                query("Roles", "government roles <government> <page>"),
                mutation("Remove citizen", "government kick <government> <player>"),
                mutation("Transfer leadership", "government leader <government> <player>"),
                query("Profile", "profile <player>"));
        page(existing, "officers", "Officers", "government list",
                query("Officers", "government officers <government> <page>"),
                mutation("Appoint", "government officer <government> <player> add"),
                mutation("Remove", "government officer <government> <player> remove"),
                mutation("Transfer leadership", "government leader <government> <player>"));
        page(existing, "invitations", "Invitations", "mail invitations",
                query("Mine", "mail invitations <page>"), mutation("Invite", "government invite <government> <player>"),
                query("Official invitations", "government invitations <government> <page>"),
                mutation("Accept", "government accept <government>"), mutation("Decline", "government decline <government>"),
                mutation("Revoke", "government revoke <government> <player>"),
                mutation("Company accept", "company accept <company>"), mutation("Company decline", "company decline <company>"));
        page(existing, "claims", "Claims", "chunk info",
                financial("Claim here", "chunk claim <city> here"), financial("Claim", "chunk claim <city> <chunk>"),
                mutation("Unclaim", "chunk unclaim <chunk>"), mutation("Automatic claims on", "chunk autoclaim on"),
                mutation("Automatic claims off", "chunk autoclaim off"), query("List claims", "chunk list <government> <page>"),
                query("Chunk information", "chunk info <chunk>"), query("Permits", "chunk permits <chunk> <page>"),
                mutation("Grant permit", "chunk permit <chunk> <player> <actions>"),
                mutation("Revoke permit", "chunk permit <chunk> <player> none"),
                query("Protection diagnosis", "chunk protection <chunk> <player>"), query("Map", "chunk map <radius>"));
        page(existing, "map", "Territory Map", "chunk map",
                query("Map radius", "chunk map <radius>"), query("Chunk information", "chunk info <chunk>"));
        page(existing, "elections", "Elections", "election list",
                query("All elections", "election list <page>"),
                query("Status", "election status <nation>"), query("Candidates", "election candidates <nation> <page>"),
                financial("Stand for election", "election candidate <nation>"), mutation("Withdraw", "election withdraw <nation>"),
                mutation("Vote", "election vote <nation> <candidate>"), query("History", "election history <nation> <page>"));
        page(existing, "legislature", "Legislature", "bill list all",
                query("All bills", "bill list all <page>"), query("Nation directory", "nation list <page>"),
                query("Bills", "bill list <nation> <page>"), query("Read bill", "bill info <bill>"),
                mutation("Propose policy", "bill propose <nation> <policy> <value> <title> <text>"),
                mutation("Propose roleplay law", "bill propose <nation> roleplay - <title> <text>"),
                mutation("Constitutional amendment", "bill amend <nation> <policy> <value> <title> <text>"),
                mutation("Revise bill", "bill revise <bill> <value> <title> <text>"),
                mutation("Vote", "bill vote <bill> <yes_no_abstain>"), query("Roll call", "bill votes <bill> <page>"),
                mutation("Sign", "bill sign <bill>"), mutation("Veto", "bill veto <bill> <reason>"),
                mutation("Move override", "bill override <bill>"), mutation("Cancel bill", "bill cancel <bill>"),
                query("History", "bill history <bill> <page>"));
        page(existing, "laws", "Law Codex", "law list all",
                query("All codices", "law list all <page>"),
                query("Codex", "law list <nation> <page>"), query("Read law", "law read <nation> <law>"),
                query("Effective policies", "government settings <nation>"));
        page(existing, "executive", "Executive Actions", "nation list",
                query("Emergency status", "executive status <nation>"),
                mutation("Emergency order", "executive emergency <nation> <policy> <value> <reason>"),
                mutation("Rescind order", "executive rescind <nation>"),
                mutation("Sign bill", "bill sign <bill>"), mutation("Veto bill", "bill veto <bill> <reason>"),
                mutation("Appoint leader", "government leader <government> <player>"));
        page(existing, "diplomacy", "Diplomacy", "diplomacy proposals all",
                query("All proposals", "diplomacy proposals all <page>"), query("Nation directory", "nation list <page>"),
                query("Relations", "diplomacy status <nation> <page>"),
                query("Proposals", "diplomacy proposals <nation> <page>"),
                query("Terms", "diplomacy terms <proposal> <page>"),
                mutation("Propose alliance", "diplomacy alliance <from_nation> <to_nation> <message>"),
                financial("Accept", "diplomacy accept <proposal>"),
                mutation("End alliance", "diplomacy break <nation> <ally>"),
                mutation("Declare war", "diplomacy war <from_nation> <to_nation> <reason>"),
                financial("Propose peace", "diplomacy peace <from_nation> <to_nation> <offer_amount> <demand_amount> <chunk_terms_or_dash> <message>"),
                mutation("Propose truce", "diplomacy truce <from_nation> <to_nation>"),
                mutation("Ratify vote", "diplomacy ratify <proposal> <yes_no_abstain>"),
                financial("Retry settlement", "diplomacy execute <proposal>"),
                mutation("Reject", "diplomacy reject <proposal>"), mutation("Cancel", "diplomacy cancel <proposal>"));
        page(existing, "companies", "Companies", "company list",
                query("Directory", "company list <page>"), financial("Create", "company create <name>"),
                query("Information", "company info <company>"), query("Members", "company members <company> <page>"),
                query("Shareholders", "company shareholders <company> <page>"),
                mutation("Invite", "company invite <company> <player>"), mutation("Accept", "company accept <company>"),
                mutation("Revoke invitation", "company revoke <company> <player>"), mutation("Decline", "company decline <company>"),
                mutation("Leave", "company leave <company>"), mutation("Remove member", "company kick <company> <player>"),
                mutation("Appoint officer", "company officer <company> <player> add"),
                mutation("Remove officer", "company officer <company> <player> remove"),
                mutation("Transfer management", "company owner <company> <player>"),
                mutation("Rename", "company rename <company> <name>"), mutation("Description", "company description <company> <description>"),
                mutation("Transfer shares", "company transfer <company> <player> <shares>"),
                mutation("Disband", "company disband <company>"));
        page(existing, "shareholders", "Shareholder Decisions", "company proposals all",
                query("All proposals", "company proposals all <page>"), query("Company directory", "company list <page>"),
                query("Proposals", "company proposals <company> <page>"),
                query("Read proposal", "company proposal <proposal>"),
                financial("Propose", "company propose <company> <type> <value> <title> <text>"),
                mutation("Vote", "company vote <proposal> <yes_no_abstain>"),
                financial("Retry execution", "company execute <proposal>"), mutation("Cancel", "company cancel <proposal>"));
        page(existing, "contracts", "Government Contracts", "contract list",
                query("Directory", "contract list <government> <page>"), query("My contracts", "contract my <page>"),
                mutation("Create", "contract create <government> <title> <description> <chunks>"),
                query("Details", "contract info <contract> <page>"),
                financial("Player bid", "contract bid <contract> <amount> <description>"),
                financial("Company bid", "contract bid <contract> <amount> <description> company:<company>"),
                mutation("Withdraw bid", "contract withdraw <contract>"),
                query("Review bids", "contract bids <contract> <page>"), mutation("Close bidding", "contract review <contract>"),
                financial("Award", "contract award <contract> <bidder>"),
                mutation("Submit work", "contract submit <contract> <completion_note>"),
                mutation("Request corrections", "contract return <contract> <reason>"),
                financial("Approve completion", "contract complete <contract>"),
                financial("Cancel/refund", "contract cancel <contract> <reason>"));
        page(existing, "mail", "Personal Mail", "mail inbox",
                query("Inbox", "mail inbox <page>"), query("Sent", "mail sent <page>"),
                query("Read", "mail read <message>"), mutation("Compose", "mail send <recipient> <subject> <body>"),
                mutation("Reply", "mail reply <message> <body>"), mutation("Delete my copy", "mail delete <message>"));
        page(existing, "official_mail", "Official Mail", "government list",
                query("Inbox", "mail official inbox <government> <page>"),
                query("Sent", "mail official sent <government> <page>"),
                query("Read", "mail official read <government> <message>"),
                mutation("Compose", "mail official send <government> <recipient> <subject> <body>"),
                mutation("Reply", "mail official reply <government> <message> <body>"),
                mutation("Delete official copy", "mail official delete <government> <message>"));
        page(existing, "profile", "Player Profiles", "profile",
                query("Profile", "profile <player>"));
        page(existing, "admin", "Operator Administration", "info",
                mutation("Bypass on", "admin bypass on"), mutation("Bypass off", "admin bypass off"),
                mutation("Force unclaim", "admin unclaim <chunk>"),
                mutation("Delete government", "admin delete <kind> <government>"),
                mutation("Delete subtree", "admin delete <kind> <government> cascade"),
                mutation("Delete company", "admin delete company <company>"),
                mutation("Appoint leader", "admin leader <government> <player>"),
                mutation("Rename government", "admin rename <government> <name>"),
                mutation("Political reassignment", "admin reassign <chunk> <city>"),
                mutation("Private title reassignment", "admin owner <chunk> <account>"),
                query("Diagnostics", "admin diagnostics <chunk>"),
                query("Audit", "admin audit <page>"), query("Repair preview", "admin repair preview"),
                mutation("Apply safe repair", "admin repair apply"), query("Audit history", "admin history <page>"),
                mutation("Start election", "election start <nation>"), mutation("Close election", "election close <nation>"),
                mutation("Cancel election", "election cancel <nation>"));
        page(existing, "help", "Command Help", "help",
                query("Section", "help <section> <page>"));
    }

    private static MenuPage.Action query(String label, String command) {
        return new MenuPage.Action(label, command, ActionIntent.QUERY, false);
    }

    private static MenuPage.Action mutation(String label, String command) {
        return new MenuPage.Action(label, command, ActionIntent.MUTATION, false);
    }

    private static MenuPage.Action financial(String label, String command) {
        return new MenuPage.Action(label, command, ActionIntent.MUTATION, true);
    }

    private static MenuPage.Action navigation(String label, String page) {
        return new MenuPage.Action(label, "gui " + page, ActionIntent.NAVIGATION, false);
    }

    private static void page(Set<String> existing, String section, String title, String query, MenuPage.Action... actions) {
        page(existing, section, title, query, true, actions);
    }

    private static void page(Set<String> existing, String section, String title, String query, boolean listed,
                             MenuPage.Action... actions) {
        String id = "statecraft:" + section;
        if (existing.add(id)) MenuRegistry.register(new MenuPage(id, title, query, Arrays.asList(actions), listed));
    }
}
