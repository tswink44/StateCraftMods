package com.statecraft.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.company.Company;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.company.CompanyEconomyManager;
import com.statecraft.economy.company.DividendConfig;
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
                    .executes(CompanyCommands::setHeadquarters)))
            .then(Commands.literal("claim")
                .executes(CompanyCommands::claimChunk)
                .then(Commands.argument("company", StringArgumentType.string())
                    .executes(CompanyCommands::claimChunkNamed)))
            .then(Commands.literal("unclaim")
                .executes(CompanyCommands::unclaimChunk))
            .then(Commands.literal("chunks")
                .executes(CompanyCommands::listChunks)
                .then(Commands.argument("company", StringArgumentType.string())
                    .executes(CompanyCommands::listChunksNamed)));
    }

    // ==================== Create ====================

    private static int createCompany(CommandContext<CommandSourceStack> ctx, int totalShares, boolean isBank) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(ctx, "name");
            EconomyManager ecoManager = EconomyManager.getInstance();
            CompanyEconomyManager companyEcoManager = CompanyEconomyManager.getInstance();

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

            Company company = companyEcoManager.createCompany(name, player.getUUID(), totalShares, headquartersCityId);
            if (company == null) {
                player.sendSystemMessage(Component.literal(
                    "§cFailed to create company. Name may already be taken or you've reached the maximum number of companies."));
                return 0;
            }

            // Set company type and initialize bank if applicable
            if (isBank) {
                company.setCompanyType(Company.CompanyType.BANK);
                BankManager.getInstance().createBank(company.getId());
                CompanyManager.getInstance().markDirty();
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
        DividendConfig divConfig = CompanyEconomyManager.getInstance().getDividendConfig(company.getId());
        if (divConfig != null && divConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§7Dividends: §a" +
                String.format("%.1f%%", divConfig.getRate() * 100) + " §7every §f" +
                divConfig.getPeriodTicks() + " §7ticks"));
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

            CompanyEconomyManager.getInstance().getOrCreateDividendConfig(company.getId()).setRate(rate);
            CompanyEconomyManager.getInstance().markDirty();
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

            CompanyEconomyManager.getInstance().getOrCreateDividendConfig(company.getId()).setPeriodTicks(ticks);
            CompanyEconomyManager.getInstance().markDirty();
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

            CompanyEconomyManager.getInstance().getOrCreateDividendConfig(company.getId()).setEnabled(enabled);
            CompanyEconomyManager.getInstance().markDirty();
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
            if (CompanyEconomyManager.getInstance().dissolveCompany(company.getId(), player.getUUID())) {
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

    // ==================== Company Chunk Ownership ====================

    private static int claimChunk(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Company company = getManagedCompany(player);
            if (company == null) return 0;
            return doClaimChunk(player, company);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int claimChunkNamed(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(ctx, "company");
            Company company = CompanyManager.getInstance().getCompanyByName(name);
            if (company == null) {
                player.sendSystemMessage(Component.literal("§cCompany '" + name + "' not found."));
                return 0;
            }
            if (!company.isOfficer(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou must be an officer to claim chunks for this company."));
                return 0;
            }
            return doClaimChunk(player, company);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    /**
     * Claim the chunk the player is standing on for a company.
     * The chunk must already be claimed (part of a city) and must be either:
     * - Government-owned (HIERARCHY) and available for purchase
     * - Already owned by this company (no-op)
     *
     * The company must have its HQ in the same nation as the chunk's city.
     * A configurable claim fee is deducted from the company treasury.
     */
    private static int doClaimChunk(ServerPlayer player, Company company) {
        if (!StateCraftEconomy.isStateCraftLoaded() || !StateCraftIntegration.isInitialized()) {
            player.sendSystemMessage(Component.literal("§cStateCraft integration not available."));
            return 0;
        }

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var manager = managerClass.getMethod("getInstance").invoke(null);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class)
                .newInstance(player.chunkPosition().x, player.chunkPosition().z);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, player.level().dimension());

            if (chunk == null) {
                player.sendSystemMessage(Component.literal("§cThis chunk is not part of any city. It must be claimed by a city first."));
                return 0;
            }

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getOwnershipType = chunkClass.getMethod("getOwnershipType");
            var ownership = getOwnershipType.invoke(chunk);
            String ownershipStr = ownership.toString();

            // Check if already company-owned by this company
            if (ownershipStr.equals("COMPANY")) {
                var getCompanyOwner = chunkClass.getMethod("getCompanyOwner");
                UUID existingOwner = (UUID) getCompanyOwner.invoke(chunk);
                if (company.getId().equals(existingOwner)) {
                    player.sendSystemMessage(Component.literal("§eThis chunk is already owned by " + company.getName() + "."));
                    return 0;
                } else {
                    player.sendSystemMessage(Component.literal("§cThis chunk is owned by another company."));
                    return 0;
                }
            }

            // Must be HIERARCHY (government-owned) to claim
            if (!ownershipStr.equals("HIERARCHY")) {
                player.sendSystemMessage(Component.literal("§cThis chunk is privately owned by a player. Only government-owned chunks can be claimed by companies."));
                return 0;
            }

            // Check jurisdiction — company HQ must be in the same nation as this chunk's city
            var getCityId = chunkClass.getMethod("getCityId");
            UUID chunkCityId = (UUID) getCityId.invoke(chunk);

            UUID hqCityId = company.getHeadquartersCityId();
            if (hqCityId == null) {
                player.sendSystemMessage(Component.literal("§cYour company has no headquarters city set. Use §f/eco company hq set§c first."));
                return 0;
            }

            // Check both cities are in the same nation
            UUID chunkNationId = StateCraftIntegration.getNationIdForCity(chunkCityId);
            UUID hqNationId = StateCraftIntegration.getNationIdForCity(hqCityId);
            if (chunkNationId == null || hqNationId == null || !chunkNationId.equals(hqNationId)) {
                player.sendSystemMessage(Component.literal("§cCompany can only claim chunks in the same nation as its headquarters."));
                return 0;
            }

            // Check claim fee — uses the nation's chunk claim fee
            double claimFee = StateCraftIntegration.getChunkClaimFee(chunkNationId);
            EconomyManager ecoManager = EconomyManager.getInstance();
            double companyBalance = ecoManager.getCompanyBalance(company.getId());

            if (claimFee > 0 && companyBalance < claimFee) {
                player.sendSystemMessage(Component.literal(
                    "§cInsufficient company funds. Claim fee: " + ecoManager.formatCurrency(claimFee) +
                    ", treasury: " + ecoManager.formatCurrency(companyBalance)));
                return 0;
            }

            // Deduct fee
            if (claimFee > 0) {
                ecoManager.getOrCreateCompanyTreasury(company.getId()).subtract(claimFee);
                ecoManager.recordAccountTransaction(company.getId(),
                    com.statecraft.economy.core.Transaction.Type.PURCHASE, claimFee, null,
                    "Chunk claim fee (" + player.chunkPosition().x + ", " + player.chunkPosition().z + ")",
                    player.getUUID(), player.getName().getString());
                ecoManager.markDirty();
            }

            // Set company ownership
            var setCompanyOwner = chunkClass.getMethod("setCompanyOwner", UUID.class);
            setCompanyOwner.invoke(chunk, company.getId());

            // Mark dirty
            var markDirty = managerClass.getMethod("markDirty");
            markDirty.invoke(manager);

            String cityName = StateCraftIntegration.getCityName(chunkCityId);
            player.sendSystemMessage(Component.literal(
                "§a" + company.getName() + " now owns chunk (" + player.chunkPosition().x + ", " +
                player.chunkPosition().z + ") in " + (cityName != null ? cityName : "city") +
                (claimFee > 0 ? " §7(fee: " + ecoManager.formatCurrency(claimFee) + ")" : "")));
            return 1;

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error claiming chunk for company: {}", e.getMessage());
            player.sendSystemMessage(Component.literal("§cError claiming chunk: " + e.getMessage()));
            return 0;
        }
    }

    private static int unclaimChunk(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            if (!StateCraftEconomy.isStateCraftLoaded() || !StateCraftIntegration.isInitialized()) {
                player.sendSystemMessage(Component.literal("§cStateCraft integration not available."));
                return 0;
            }

            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var manager = managerClass.getMethod("getInstance").invoke(null);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class)
                .newInstance(player.chunkPosition().x, player.chunkPosition().z);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, player.level().dimension());

            if (chunk == null) {
                player.sendSystemMessage(Component.literal("§cThis chunk is not claimed."));
                return 0;
            }

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getOwnershipType = chunkClass.getMethod("getOwnershipType");
            var ownership = getOwnershipType.invoke(chunk);

            if (!ownership.toString().equals("COMPANY")) {
                player.sendSystemMessage(Component.literal("§cThis chunk is not company-owned."));
                return 0;
            }

            var getCompanyOwner = chunkClass.getMethod("getCompanyOwner");
            UUID companyId = (UUID) getCompanyOwner.invoke(chunk);
            Company company = CompanyManager.getInstance().getCompany(companyId);

            if (company == null) {
                player.sendSystemMessage(Component.literal("§cOwning company not found."));
                return 0;
            }

            if (!company.isOfficer(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou must be an officer of " + company.getName() + " to unclaim chunks."));
                return 0;
            }

            // Revert to government ownership
            var setCompanyOwner = chunkClass.getMethod("setCompanyOwner", UUID.class);
            setCompanyOwner.invoke(chunk, (UUID) null);

            var markDirty = managerClass.getMethod("markDirty");
            markDirty.invoke(manager);

            player.sendSystemMessage(Component.literal(
                "§a" + company.getName() + " released chunk (" + player.chunkPosition().x + ", " +
                player.chunkPosition().z + ") back to government ownership."));
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int listChunks(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Company company = getManagedCompany(player);
            if (company == null) return 0;
            return doListChunks(player, company);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int listChunksNamed(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(ctx, "company");
            Company company = CompanyManager.getInstance().getCompanyByName(name);
            if (company == null) {
                player.sendSystemMessage(Component.literal("§cCompany '" + name + "' not found."));
                return 0;
            }
            return doListChunks(player, company);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int doListChunks(ServerPlayer player, Company company) {
        if (!StateCraftEconomy.isStateCraftLoaded() || !StateCraftIntegration.isInitialized()) {
            player.sendSystemMessage(Component.literal("§cStateCraft integration not available."));
            return 0;
        }

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var manager = managerClass.getMethod("getInstance").invoke(null);
            var getCompanyOwnedChunks = managerClass.getMethod("getCompanyOwnedChunks", UUID.class);
            @SuppressWarnings("unchecked")
            var chunks = (java.util.List<?>) getCompanyOwnedChunks.invoke(manager, company.getId());

            player.sendSystemMessage(Component.literal(
                "§6§l=== " + company.getName() + " — Owned Chunks (" + chunks.size() + ") ==="));

            if (chunks.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7No chunks owned. Use §f/eco company claim§7 to claim."));
                return 1;
            }

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getChunkPos = chunkClass.getMethod("getChunkPos");
            var getCityId = chunkClass.getMethod("getCityId");
            var chunkPosFieldX = Class.forName("net.minecraft.world.level.ChunkPos").getField("x");
            var chunkPosFieldZ = Class.forName("net.minecraft.world.level.ChunkPos").getField("z");

            int shown = 0;
            for (Object chunk : chunks) {
                if (shown >= 15) {
                    int remaining = chunks.size() - 15;
                    player.sendSystemMessage(Component.literal("§8  +" + remaining + " more chunks..."));
                    break;
                }
                Object pos = getChunkPos.invoke(chunk);
                int x = (int) chunkPosFieldX.get(pos);
                int z = (int) chunkPosFieldZ.get(pos);
                UUID cityId = (UUID) getCityId.invoke(chunk);
                String cityName = StateCraftIntegration.getCityName(cityId);

                player.sendSystemMessage(Component.literal(
                    "§7  (" + x + ", " + z + ") §8in §e" + (cityName != null ? cityName : "??")));
                shown++;
            }

            return 1;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cError listing chunks: " + e.getMessage()));
            return 0;
        }
    }
}







