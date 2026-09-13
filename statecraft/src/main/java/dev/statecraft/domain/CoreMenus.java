package dev.statecraft.domain;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CoreMenus {
    private CoreMenus() {}

    public static synchronized void register() {
        Set<String> existing = new HashSet<>();
        MenuRegistry.pages().forEach(page -> existing.add(page.id()));
        page(existing, "main", "StateCraft", "info",
                action("Help", "help <section> <page>"), action("Profile", "profile <player>"));
        page(existing, "nations", "Nations", "nation list",
                action("List", "nation list <page>"), action("Create", "nation create <name>"),
                action("Information", "nation info <nation>"), action("Join", "nation join <nation>"),
                action("Leave", "nation leave <nation>"), action("Rename", "nation rename <nation> <name>"),
                action("Description", "nation description <nation> <description>"), action("Tag", "nation tag <nation> <tag>"),
                action("Flag", "nation flag <nation> <flag>"), action("Settings", "nation settings <nation>"),
                action("Set policy", "nation setting <nation> <key> <value>"),
                action("Disband", "nation disband <nation>"), action("Disband subtree", "nation disband <nation> cascade"));
        page(existing, "states", "States", "state list",
                action("List", "state list <page>"), action("Create", "state create <nation> <name> <governor>"),
                action("Information", "state info <state>"), action("Join", "state join <state>"),
                action("Leave", "state leave <state>"), action("Rename", "state rename <state> <name>"),
                action("Description", "state description <state> <description>"), action("Tag", "state tag <state> <tag>"),
                action("Flag", "state flag <state> <flag>"), action("Settings", "state settings <state>"),
                action("Set policy", "state setting <state> <key> <value>"),
                action("Disband", "state disband <state>"), action("Disband subtree", "state disband <state> cascade"));
        page(existing, "cities", "Cities", "city list",
                action("List", "city list <page>"), action("Create", "city create <state> <name> <mayor>"),
                action("Information", "city info <city>"), action("Join", "city join <city>"),
                action("Leave", "city leave <city>"), action("Rename", "city rename <city> <name>"),
                action("Description", "city description <city> <description>"), action("Tag", "city tag <city> <tag>"),
                action("Flag", "city flag <city> <flag>"), action("Settings", "city settings <city>"),
                action("Set policy", "city setting <city> <key> <value>"),
                action("Disband", "city disband <city>"), action("Disband subtree", "city disband <city> cascade"));
        page(existing, "members", "Citizens and Roles", "government list",
                action("Citizens", "government members <government> <page>"),
                action("Roles", "government roles <government> <page>"),
                action("Remove citizen", "government kick <government> <player>"),
                action("Transfer leadership", "government leader <government> <player>"),
                action("Profile", "profile <player>"));
        page(existing, "officers", "Officers", "government list",
                action("Officers", "government officers <government> <page>"),
                action("Appoint", "government officer <government> <player> add"),
                action("Remove", "government officer <government> <player> remove"),
                action("Transfer leadership", "government leader <government> <player>"));
        page(existing, "invitations", "Invitations", "mail invitations",
                action("Mine", "mail invitations <page>"), action("Invite", "government invite <government> <player>"),
                action("Official invitations", "government invitations <government> <page>"),
                action("Accept", "government accept <government>"), action("Decline", "government decline <government>"),
                action("Revoke", "government revoke <government> <player>"),
                action("Company accept", "company accept <company>"), action("Company decline", "company decline <company>"));
        page(existing, "claims", "Claims", "chunk info",
                action("Claim here", "chunk claim <city> here"), action("Claim", "chunk claim <city> <chunk>"),
                action("Unclaim", "chunk unclaim <chunk>"), action("Automatic claims on", "chunk autoclaim on"),
                action("Automatic claims off", "chunk autoclaim off"), action("List claims", "chunk list <government> <page>"),
                action("Chunk information", "chunk info <chunk>"), action("Permits", "chunk permits <chunk> <page>"),
                action("Grant permit", "chunk permit <chunk> <player> <actions>"),
                action("Revoke permit", "chunk permit <chunk> <player> none"),
                action("Protection diagnosis", "chunk protection <chunk> <player>"), action("Map", "chunk map <radius>"));
        page(existing, "map", "Territory Map", "chunk map",
                action("Map radius", "chunk map <radius>"), action("Chunk information", "chunk info <chunk>"));
        page(existing, "elections", "Elections", "election list",
                action("All elections", "election list <page>"),
                action("Status", "election status <nation>"), action("Candidates", "election candidates <nation> <page>"),
                action("Stand for election", "election candidate <nation>"), action("Withdraw", "election withdraw <nation>"),
                action("Vote", "election vote <nation> <candidate>"), action("History", "election history <nation> <page>"));
        page(existing, "legislature", "Legislature", "bill list all",
                action("All bills", "bill list all <page>"), action("Nation directory", "nation list <page>"),
                action("Bills", "bill list <nation> <page>"), action("Read bill", "bill info <bill>"),
                action("Propose policy", "bill propose <nation> <policy> <value> <title> <text>"),
                action("Propose roleplay law", "bill propose <nation> roleplay - <title> <text>"),
                action("Constitutional amendment", "bill amend <nation> <policy> <value> <title> <text>"),
                action("Revise bill", "bill revise <bill> <value> <title> <text>"),
                action("Vote", "bill vote <bill> <yes_no_abstain>"), action("Roll call", "bill votes <bill> <page>"),
                action("Sign", "bill sign <bill>"), action("Veto", "bill veto <bill> <reason>"),
                action("Move override", "bill override <bill>"), action("Cancel bill", "bill cancel <bill>"),
                action("History", "bill history <bill> <page>"));
        page(existing, "laws", "Law Codex", "law list all",
                action("All codices", "law list all <page>"),
                action("Codex", "law list <nation> <page>"), action("Read law", "law read <nation> <law>"),
                action("Effective policies", "government settings <nation>"));
        page(existing, "executive", "Executive Actions", "nation list",
                action("Emergency status", "executive status <nation>"),
                action("Emergency order", "executive emergency <nation> <policy> <value> <reason>"),
                action("Rescind order", "executive rescind <nation>"),
                action("Sign bill", "bill sign <bill>"), action("Veto bill", "bill veto <bill> <reason>"),
                action("Appoint leader", "government leader <government> <player>"));
        page(existing, "diplomacy", "Diplomacy", "diplomacy proposals all",
                action("All proposals", "diplomacy proposals all <page>"), action("Nation directory", "nation list <page>"),
                action("Relations", "diplomacy status <nation> <page>"),
                action("Proposals", "diplomacy proposals <nation> <page>"),
                action("Terms", "diplomacy terms <proposal> <page>"),
                action("Propose alliance", "diplomacy alliance <from_nation> <to_nation> <message>"),
                action("Accept", "diplomacy accept <proposal>"),
                action("End alliance", "diplomacy break <nation> <ally>"),
                action("Declare war", "diplomacy war <from_nation> <to_nation> <reason>"),
                action("Propose peace", "diplomacy peace <from_nation> <to_nation> <offer_amount> <demand_amount> <chunk_terms_or_dash> <message>"),
                action("Propose truce", "diplomacy truce <from_nation> <to_nation>"),
                action("Ratify vote", "diplomacy ratify <proposal> <yes_no_abstain>"),
                action("Retry settlement", "diplomacy execute <proposal>"),
                action("Reject", "diplomacy reject <proposal>"), action("Cancel", "diplomacy cancel <proposal>"));
        page(existing, "companies", "Companies", "company list",
                action("Directory", "company list <page>"), action("Create", "company create <name>"),
                action("Information", "company info <company>"), action("Members", "company members <company> <page>"),
                action("Shareholders", "company shareholders <company> <page>"),
                action("Invite", "company invite <company> <player>"), action("Accept", "company accept <company>"),
                action("Revoke invitation", "company revoke <company> <player>"), action("Decline", "company decline <company>"),
                action("Leave", "company leave <company>"), action("Remove member", "company kick <company> <player>"),
                action("Appoint officer", "company officer <company> <player> add"),
                action("Remove officer", "company officer <company> <player> remove"),
                action("Transfer management", "company owner <company> <player>"),
                action("Rename", "company rename <company> <name>"), action("Description", "company description <company> <description>"),
                action("Transfer shares", "company transfer <company> <player> <shares>"),
                action("Disband", "company disband <company>"));
        page(existing, "shareholders", "Shareholder Decisions", "company proposals all",
                action("All proposals", "company proposals all <page>"), action("Company directory", "company list <page>"),
                action("Proposals", "company proposals <company> <page>"),
                action("Read proposal", "company proposal <proposal>"),
                action("Propose", "company propose <company> <type> <value> <title> <text>"),
                action("Vote", "company vote <proposal> <yes_no_abstain>"),
                action("Retry execution", "company execute <proposal>"), action("Cancel", "company cancel <proposal>"));
        page(existing, "contracts", "Government Contracts", "contract list",
                action("Directory", "contract list <government> <page>"), action("My contracts", "contract my <page>"),
                action("Create", "contract create <government> <title> <description> <chunks>"),
                action("Details", "contract info <contract> <page>"),
                action("Player bid", "contract bid <contract> <amount> <description>"),
                action("Company bid", "contract bid <contract> <amount> <description> company:<company>"),
                action("Withdraw bid", "contract withdraw <contract>"),
                action("Review bids", "contract bids <contract> <page>"), action("Close bidding", "contract review <contract>"),
                action("Award", "contract award <contract> <bidder>"),
                action("Submit work", "contract submit <contract> <completion_note>"),
                action("Request corrections", "contract return <contract> <reason>"),
                action("Approve completion", "contract complete <contract>"),
                action("Cancel/refund", "contract cancel <contract> <reason>"));
        page(existing, "mail", "Personal Mail", "mail inbox",
                action("Inbox", "mail inbox <page>"), action("Sent", "mail sent <page>"),
                action("Read", "mail read <message>"), action("Compose", "mail send <recipient> <subject> <body>"),
                action("Reply", "mail reply <message> <body>"), action("Delete my copy", "mail delete <message>"));
        page(existing, "official_mail", "Official Mail", "government list",
                action("Inbox", "mail official inbox <government> <page>"),
                action("Sent", "mail official sent <government> <page>"),
                action("Read", "mail official read <government> <message>"),
                action("Compose", "mail official send <government> <recipient> <subject> <body>"),
                action("Reply", "mail official reply <government> <message> <body>"),
                action("Delete official copy", "mail official delete <government> <message>"));
        page(existing, "profile", "Player Profiles", "profile",
                action("Profile", "profile <player>"));
        page(existing, "admin", "Operator Administration", "info",
                action("Bypass on", "admin bypass on"), action("Bypass off", "admin bypass off"),
                action("Force unclaim", "admin unclaim <chunk>"),
                action("Delete government", "admin delete <kind> <government>"),
                action("Delete subtree", "admin delete <kind> <government> cascade"),
                action("Delete company", "admin delete company <company>"),
                action("Appoint leader", "admin leader <government> <player>"),
                action("Rename government", "admin rename <government> <name>"),
                action("Political reassignment", "admin reassign <chunk> <city>"),
                action("Private title reassignment", "admin owner <chunk> <account>"),
                action("Diagnostics", "admin diagnostics <chunk>"),
                action("Audit", "admin audit <page>"), action("Repair preview", "admin repair preview"),
                action("Apply safe repair", "admin repair apply"), action("Audit history", "admin history <page>"),
                action("Start election", "election start <nation>"), action("Close election", "election close <nation>"),
                action("Cancel election", "election cancel <nation>"));
        page(existing, "help", "Command Help", "help",
                action("Section", "help <section> <page>"));
    }

    private static MenuPage.Action action(String label, String command) {
        return new MenuPage.Action(label, command);
    }

    private static void page(Set<String> existing, String section, String title, String query, MenuPage.Action... actions) {
        String id = "statecraft:" + section;
        if (existing.add(id)) MenuRegistry.register(new MenuPage(id, title, query, Arrays.asList(actions)));
    }
}
