package dev.statecraft.api;

import java.util.Collection;
import java.util.List;

public enum MenuCategory {
    OVERVIEW("Overview & Help", "World information, profiles and guides"),
    GOVERNMENTS("Governments", "Nations, states, cities and membership"),
    TERRITORY("Territory", "Claims, borders and property"),
    POLITICS("Politics", "Elections, laws, executive powers and diplomacy"),
    BUSINESS("Companies & Contracts", "Companies, shareholder decisions and stocks"),
    ECONOMY("Banking & Trade", "Accounts, banks, taxes and item trading"),
    COMMUNICATIONS("Communications", "Personal and official mail"),
    ADMINISTRATION("Administration", "Operator tools and world maintenance"),
    OTHER("Other", "Additional registered sections");

    private final String title;
    private final String description;

    MenuCategory(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() { return title; }
    public String description() { return description; }

    public List<MenuPage> pages(Collection<MenuPage> pages) {
        return pages.stream().filter(page -> of(page.id()) == this).toList();
    }

    public static MenuCategory of(String page) {
        return switch (page) {
            case "statecraft:main", "statecraft:profile", "statecraft:help", "economy:guide" -> OVERVIEW;
            case "statecraft:nations", "statecraft:states", "statecraft:cities", "statecraft:members",
                 "statecraft:officers", "statecraft:invitations" -> GOVERNMENTS;
            case "statecraft:claims", "statecraft:map", "economy:property" -> TERRITORY;
            case "statecraft:elections", "statecraft:legislature", "statecraft:laws",
                 "statecraft:executive", "statecraft:diplomacy" -> POLITICS;
            case "statecraft:companies", "statecraft:shareholders", "statecraft:contracts",
                 "economy:company", "economy:stock" -> BUSINESS;
            case "economy:atm", "economy:bank", "economy:tax", "economy:hub", "economy:market" -> ECONOMY;
            case "statecraft:mail", "statecraft:official_mail" -> COMMUNICATIONS;
            case "statecraft:admin" -> ADMINISTRATION;
            default -> OTHER;
        };
    }
}
