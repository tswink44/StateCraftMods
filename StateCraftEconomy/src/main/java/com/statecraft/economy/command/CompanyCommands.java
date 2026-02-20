package com.statecraft.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.company.Company;
import com.statecraft.economy.company.CompanyManager;
import com.statecraft.economy.company.BankManager;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Commands for the company system.
 * Registered under /eco company (or /economy company).
 */
public class CompanyCommands {

    /**
     * Build the company subcommand tree. Returns the literal node to attach to the economy command.
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildCompanyCommand() {
        return Commands.literal("company")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ctx -> createCompany(ctx, 1000, false))
                    .then(Commands.literal("bank")
                        .executes(ctx -> createCompany(ctx, 1000, true))
                        .then(Commands.argument("shares", IntegerArgumentType.integer(1, 1000000))
                            .executes(ctx -> createCompany(ctx, IntegerArgumentType.getInteger(ctx, "shares"), true))))
                    .then(Commands.argument("shares", IntegerArgumentType.integer(1, 1000000))
                        .executes(ctx -> createCompany(ctx, IntegerArgumentType.getInteger(ctx, "shares"), false)))))
            .then(Commands.literal("info")
                .executes(CompanyCommands::infoSelf)
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(CompanyCommands::infoNamed)))
            .then(Commands.literal("list")
                .executes(CompanyCommands::listCompanies))
            .then(Commands.literal("officer")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(CompanyCommands::addOfficer)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(CompanyCommands::removeOfficer))))
            .then(Commands.literal("shares")
                .then(Commands.literal("transfer")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(CompanyCommands::transferShares)))))
            .then(Commands.literal("dividend")
                .then(Commands.literal("set")
                    .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(CompanyCommands::setDividendRate)))
                .then(Commands.literal("period")
                    .then(Commands.argument("ticks", LongArgumentType.longArg(1200))
                        .executes(CompanyCommands::setDividendPeriod)))
                .then(Commands.literal("enable")
                    .executes(ctx -> setDividendsEnabled(ctx, true)))
                .then(Commands.literal("disable")
                    .executes(ctx -> setDividendsEnabled(ctx, false))))
            .then(Commands.literal("dissolve")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(CompanyCommands::dissolveCompany)))
            .then(Commands.literal("rename")
                .then(Commands.argument("newName", StringArgumentType.string())
                    .executes(CompanyCommands::renameCompany)))
            .then(Commands.literal("hq")
                .then(Commands.literal("set")
                    .executes(CompanyCommands::setHeadquarters)));
    }

    // ==================== Create ====================

    private static int createCompany(CommandContext<CommandSourceStack> ctx, int totalShares, boolean isBank) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(ctx, "name");
            EconomyManager ecoManager = EconomyManager.getInstance();
            CompanyManager companyManager = CompanyManager.getInstance();

            // Check registration fee (banks have higher fee)
            double fee = isBank ? EconomyConfig.BANK_REGISTRATION_FEE.get() : EconomyConfig.COMPANY_REGISTRATION_FEE.get();
            if (fee > 0) {
                double balance = ecoManager.getBalance(player.getUUID());
                if (balance < fee) {
                    String typeLabel = isBank ? "Bank" : "Company";
                    player.sendSystemMessage(Component.literal(
                        "§cInsufficient funds. " + typeLabel + " registration costs " + ecoManager.formatCurrency(fee) +
                        ". Your balance: " + ecoManager.formatCurrency(balance)));
                    return 0;
                }
            }

            // Determine HQ city from player's current location
            UUID headquartersCityId = null;
            if (StateCraftEconomy.isStateCraftLoaded()) {
                headquartersCityId = StateCraftIntegration.getPlayerCityId(player);
            }

            Company company = companyManager.createCompany(name, player.getUUID(), totalShares, headquartersCityId);
            if (company == null) {
                player.sendSystemMessage(Component.literal(
                    "§cFailed to create company. Name may already be taken or you've reached the maximum number of companies."));
                return 0;
            }

            // Set company type and initialize bank if applicable
            if (isBank) {
                company.setCompanyType(Company.CompanyType.BANK);
                BankManager.getInstance().createBank(company.getId());
                companyManager.markDirty();
            }

            // Charge registration fee (deposited to state treasury if possible)
            if (fee > 0) {
                ecoManager.withdraw(player.getUUID(), fee, "Company registration fee for " + name);

                // Deposit fee to the state treasury
                if (headquartersCityId != null && StateCraftEconomy.isStateCraftLoaded()) {
                    UUID stateId = StateCraftIntegration.getStateIdForCity(headquartersCityId);
                    if (stateId != null) {
                        ecoManager.getOrCreateStateTreasury(stateId).add(fee);
                        ecoManager.recordAccountTransaction(stateId,
                            com.statecraft.economy.core.Transaction.Type.TAX,
                            fee, company.getId(),
                            "Company registration fee from " + name,
                            null, "Company Registry");
                        ecoManager.markDirty();
                    }
                }
            }

            String feeMsg = fee > 0 ? " (Registration fee: " + ecoManager.formatCurrency(fee) + ")" : "";
            String typeLabel = isBank ? "§a§lBank Created! " : "§a§lCompany Created! ";
            player.sendSystemMessage(Component.literal(
                typeLabel + "§r§f" + name + " §7with " + totalShares + " shares." + feeMsg));

            if (isBank) {
                player.sendSystemMessage(Component.literal(
                    "§7Players can open accounts with §f/eco bank open " + name));
            }

            if (headquartersCityId != null) {
                String cityName = StateCraftIntegration.getCityName(headquartersCityId);
                player.sendSystemMessage(Component.literal(
                    "§7Headquarters registered in: §f" + (cityName != null ? cityName : "Unknown City")));
            }

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError creating company: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Info ====================

    private static int infoSelf(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            var companies = CompanyManager.getInstance().getPlayerCompanies(player.getUUID());

            if (companies.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7You are not part of any company. Use §f/eco company create <name>"));
                return 0;
            }

            // Show info for first company
            return showCompanyInfo(player, companies.get(0));
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int infoNamed(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(ctx, "name");
            Company company = CompanyManager.getInstance().getCompanyByName(name);

            if (company == null) {
                player.sendSystemMessage(Component.literal("§cCompany '" + name + "' not found."));
                return 0;
            }

            return showCompanyInfo(player, company);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int showCompanyInfo(ServerPlayer player, Company company) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        double balance = ecoManager.getCompanyBalance(company.getId());

        player.sendSystemMessage(Component.literal("§6§l═══════ " + company.getName() + " ═══════"));
        player.sendSystemMessage(Component.literal("§7Balance: §f" + ecoManager.formatCurrency(balance)));
        player.sendSystemMessage(Component.literal("§7Total Shares: §f" + company.getTotalShares() +
            " §7(Issued: §f" + company.getIssuedShares() + "§7)"));

        // Show founder
        String founderName = getPlayerName(player, company.getFounderId());
        player.sendSystemMessage(Component.literal("§7Founder: §f" + founderName));

        // Officers
        Set<UUID> officers = company.getOfficers();
        if (!officers.isEmpty()) {
            StringBuilder officerNames = new StringBuilder();
            for (UUID officerId : officers) {
                if (officerNames.length() > 0) officerNames.append(", ");
                officerNames.append(getPlayerName(player, officerId));
            }
            player.sendSystemMessage(Component.literal("§7Officers: §f" + officerNames));
        }

        // Shareholders (top 5)
        Map<UUID, Integer> shareholders = company.getShareholders();
        player.sendSystemMessage(Component.literal("§7Shareholders (" + shareholders.size() + "):"));
        shareholders.entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> {
                String name = getPlayerName(player, entry.getKey());
                double pct = company.getSharePercentage(entry.getKey()) * 100;
                player.sendSystemMessage(Component.literal(
                    "  §f" + name + "§7: " + entry.getValue() + " shares (" + String.format("%.1f%%", pct) + ")"));
            });
        if (shareholders.size() > 5) {
            player.sendSystemMessage(Component.literal("  §7... and " + (shareholders.size() - 5) + " more"));
        }

        // Dividends
        if (company.isDividendsEnabled()) {
            player.sendSystemMessage(Component.literal("§7Dividends: §a" +
                String.format("%.1f%%", company.getDividendRate() * 100) + " §7every §f" +
                company.getDividendPeriodTicks() + " §7ticks"));
        } else {
            player.sendSystemMessage(Component.literal("§7Dividends: §cDisabled"));
        }

        // HQ
        if (company.getHeadquartersCityId() != null && StateCraftEconomy.isStateCraftLoaded()) {
            String cityName = StateCraftIntegration.getCityName(company.getHeadquartersCityId());
            player.sendSystemMessage(Component.literal("§7Headquarters: §f" + (cityName != null ? cityName : "Unknown")));
        }

        // Created date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        player.sendSystemMessage(Component.literal("§7Founded: §f" + sdf.format(new Date(company.getCreatedTime()))));
        player.sendSystemMessage(Component.literal("§6§l═══════════════════════"));

        return 1;
    }

    // ==================== List ====================

    private static int listCompanies(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Collection<Company> allCompanies = CompanyManager.getInstance().getAllCompanies();
            EconomyManager ecoManager = EconomyManager.getInstance();

            if (allCompanies.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7No companies exist yet. Use §f/eco company create <name>"));
                return 0;
            }

            player.sendSystemMessage(Component.literal("§6§l═══ Companies (" + allCompanies.size() + ") ═══"));
            for (Company company : allCompanies) {
                double balance = ecoManager.getCompanyBalance(company.getId());
                String role = "";
                if (company.isFounder(player.getUUID())) role = " §a[Founder]";
                else if (company.isOfficer(player.getUUID())) role = " §e[Officer]";
                else if (company.isShareholder(player.getUUID())) role = " §b[Shareholder]";

                player.sendSystemMessage(Component.literal(
                    "§f" + company.getName() + " §7- " + ecoManager.formatCurrency(balance) +
                    " §7(" + company.getShareholders().size() + " shareholders)" + role));
            }

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Officers ====================

    private static int addOfficer(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            Company company = getFoundedCompany(player);
            if (company == null) return 0;

            if (company.addOfficer(target.getUUID())) {
                CompanyManager.getInstance().markDirty();
                player.sendSystemMessage(Component.literal(
                    "§a" + target.getName().getString() + " added as officer of " + company.getName()));
                target.sendSystemMessage(Component.literal(
                    "§aYou have been appointed as an officer of §f" + company.getName()));
            } else {
                player.sendSystemMessage(Component.literal(
                    "§c" + target.getName().getString() + " is already an officer."));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeOfficer(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            Company company = getFoundedCompany(player);
            if (company == null) return 0;

            if (company.removeOfficer(target.getUUID())) {
                CompanyManager.getInstance().markDirty();
                player.sendSystemMessage(Component.literal(
                    "§a" + target.getName().getString() + " removed as officer of " + company.getName()));
            } else {
                player.sendSystemMessage(Component.literal(
                    "§cCannot remove that player from officer role."));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Shares ====================

    private static int transferShares(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            int count = IntegerArgumentType.getInteger(ctx, "count");

            // Find the company the player has shares in
            var companies = CompanyManager.getInstance().getPlayerCompanies(player.getUUID());
            Company company = companies.isEmpty() ? null : companies.get(0);
            if (company == null) {
                player.sendSystemMessage(Component.literal("§cYou don't own shares in any company."));
                return 0;
            }

            int currentShares = company.getShareCount(player.getUUID());
            if (currentShares < count) {
                player.sendSystemMessage(Component.literal(
                    "§cYou only have " + currentShares + " shares in " + company.getName()));
                return 0;
            }

            if (company.transferShares(player.getUUID(), target.getUUID(), count)) {
                CompanyManager.getInstance().markDirty();
                player.sendSystemMessage(Component.literal(
                    "§aTransferred " + count + " shares of " + company.getName() + " to " + target.getName().getString()));
                target.sendSystemMessage(Component.literal(
                    "§aReceived " + count + " shares of §f" + company.getName() + "§a from " + player.getName().getString()));
            } else {
                player.sendSystemMessage(Component.literal("§cShare transfer failed."));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Dividends ====================

    private static int setDividendRate(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double rate = DoubleArgumentType.getDouble(ctx, "rate");
            Company company = getManagedCompany(player);
            if (company == null) return 0;

            company.setDividendRate(rate);
            CompanyManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal(
                "§aDividend rate for " + company.getName() + " set to " + String.format("%.1f%%", rate * 100)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setDividendPeriod(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            long ticks = LongArgumentType.getLong(ctx, "ticks");
            Company company = getManagedCompany(player);
            if (company == null) return 0;

            company.setDividendPeriodTicks(ticks);
            CompanyManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal(
                "§aDividend period for " + company.getName() + " set to " + ticks + " ticks"));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setDividendsEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Company company = getManagedCompany(player);
            if (company == null) return 0;

            company.setDividendsEnabled(enabled);
            CompanyManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal(
                "§aDividends for " + company.getName() + (enabled ? " §aenabled" : " §cdisabled")));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Dissolve ====================

    private static int dissolveCompany(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(ctx, "name");
            Company company = CompanyManager.getInstance().getCompanyByName(name);

            if (company == null) {
                player.sendSystemMessage(Component.literal("§cCompany '" + name + "' not found."));
                return 0;
            }

            if (!company.isFounder(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cOnly the founder can dissolve a company."));
                return 0;
            }

            double balance = EconomyManager.getInstance().getCompanyBalance(company.getId());
            if (CompanyManager.getInstance().dissolveCompany(company.getId(), player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                    "§a" + name + " has been dissolved. " +
                    (balance > 0 ? EconomyManager.getInstance().formatCurrency(balance) + " distributed to shareholders." : "")));
            } else {
                player.sendSystemMessage(Component.literal("§cFailed to dissolve company."));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Rename ====================

    private static int renameCompany(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String newName = StringArgumentType.getString(ctx, "newName");
            Company company = getFoundedCompany(player);
            if (company == null) return 0;

            String oldName = company.getName();
            if (!CompanyManager.getInstance().renameCompany(company.getId(), newName)) {
                player.sendSystemMessage(Component.literal("§cA company with that name already exists."));
                return 0;
            }

            player.sendSystemMessage(Component.literal(
                "§aCompany renamed from §f" + oldName + "§a to §f" + newName));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Set Headquarters ====================

    private static int setHeadquarters(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Company company = getFoundedCompany(player);
            if (company == null) return 0;

            if (!StateCraftEconomy.isStateCraftLoaded()) {
                player.sendSystemMessage(Component.literal("§cStateCraft is not loaded — cannot determine city."));
                return 0;
            }

            UUID cityId = StateCraftIntegration.getPlayerCityId(player);
            if (cityId == null) {
                player.sendSystemMessage(Component.literal("§cYou are not in a claimed city chunk. Stand in the HQ city."));
                return 0;
            }

            company.setHeadquartersCityId(cityId);
            CompanyManager.getInstance().markDirty();
            String cityName = StateCraftIntegration.getCityName(cityId);
            player.sendSystemMessage(Component.literal(
                "§aHeadquarters for " + company.getName() + " set to §f" + (cityName != null ? cityName : "Unknown City")));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Helpers ====================

    /**
     * Get the first company the player founded (for founder-only operations).
     */
    private static Company getFoundedCompany(ServerPlayer player) {
        var companies = CompanyManager.getInstance().getPlayerCompanies(player.getUUID());
        for (Company c : companies) {
            if (c.isFounder(player.getUUID())) return c;
        }
        player.sendSystemMessage(Component.literal("§cYou haven't founded any company."));
        return null;
    }

    /**
     * Get the first company the player can manage (founder or officer).
     */
    private static Company getManagedCompany(ServerPlayer player) {
        var companies = CompanyManager.getInstance().getPlayerManagedCompanies(player.getUUID());
        if (companies.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cYou don't manage any company."));
            return null;
        }
        return companies.get(0);
    }

    private static String getPlayerName(ServerPlayer viewer, UUID playerId) {
        ServerPlayer p = viewer.server.getPlayerList().getPlayer(playerId);
        if (p != null) return p.getName().getString();
        // Try user cache
        var profile = viewer.server.getProfileCache();
        if (profile != null) {
            var opt = profile.get(playerId);
            if (opt.isPresent()) return opt.get().getName();
        }
        return playerId.toString().substring(0, 8) + "...";
    }
}







