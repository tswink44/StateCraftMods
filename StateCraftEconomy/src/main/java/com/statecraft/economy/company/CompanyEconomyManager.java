package com.statecraft.economy.company;

import com.statecraft.company.Company;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.Transaction;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Economy-specific operations for companies.
 * Delegates company CRUD and data to StateCraft's CompanyManager.
 * Owns dividend configuration, treasury management, and company taxation.
 *
 * Singleton — one instance per server.
 */
public class CompanyEconomyManager {
    private static CompanyEconomyManager instance;

    /** Dividend configs keyed by company UUID. */
    private final Map<UUID, DividendConfig> dividendConfigs = new ConcurrentHashMap<>();

    private boolean dirty = false;

    private CompanyEconomyManager() {}

    public static CompanyEconomyManager getInstance() {
        if (instance == null) {
            instance = new CompanyEconomyManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Dividend Config Access ====================

    /**
     * Get or create dividend config for a company.
     */
    public DividendConfig getOrCreateDividendConfig(UUID companyId) {
        return dividendConfigs.computeIfAbsent(companyId, DividendConfig::new);
    }

    /**
     * Get dividend config for a company, or null if none exists.
     */
    @Nullable
    public DividendConfig getDividendConfig(UUID companyId) {
        return dividendConfigs.get(companyId);
    }

    // ==================== Economy-Aware Company CRUD ====================

    /**
     * Create a new company with a treasury account.
     * Delegates the core creation to StateCraft's CompanyManager, then creates the treasury.
     */
    @Nullable
    public Company createCompany(String name, UUID founderId, int totalShares, @Nullable UUID headquartersCityId) {
        Company company = CompanyManager.getInstance().createCompany(name, founderId, totalShares, headquartersCityId);
        if (company == null) return null;

        // Create the company treasury account
        EconomyManager.getInstance().getOrCreateCompanyTreasury(company.getId());
        return company;
    }

    /**
     * Dissolve a company: handle bank dissolution, distribute balance to shareholders,
     * then delegate the actual removal to StateCraft's CompanyManager.
     */
    public boolean dissolveCompany(UUID companyId, UUID initiatorId) {
        Company company = CompanyManager.getInstance().getCompany(companyId);
        if (company == null) return false;
        if (!company.isFounder(initiatorId)) return false;

        EconomyManager ecoManager = EconomyManager.getInstance();

        // If this is a bank, dissolve the bank first (pays depositors before shareholders)
        if (company.isBank()) {
            BankManager bankManager = BankManager.getInstance();
            if (bankManager.isBank(companyId)) {
                bankManager.dissolveBank(companyId, null);
            }
        }

        // Distribute remaining balance to shareholders proportionally
        double balance = ecoManager.getCompanyBalance(companyId);

        if (balance > 0) {
            for (Map.Entry<UUID, Integer> entry : company.getShareholders().entrySet()) {
                UUID shareholderId = entry.getKey();
                double percentage = company.getSharePercentage(shareholderId);
                double payout = balance * percentage;
                if (payout > 0.01) {
                    ecoManager.deposit(shareholderId, payout,
                        "Company dissolution payout from " + company.getName());
                }
            }
            // Zero out the company treasury
            ecoManager.getOrCreateCompanyTreasury(companyId).setBalance(0);
        }

        // Remove dividend config
        dividendConfigs.remove(companyId);

        // Delegate the actual removal to StateCraft's CompanyManager
        dirty = true;
        return CompanyManager.getInstance().dissolveCompany(companyId, initiatorId);
    }

    // ==================== Dividend Distribution ====================

    /**
     * Called every server tick to check if any company dividends should be paid.
     */
    public void tick(MinecraftServer server) {
        long currentTime = server.overworld().getGameTime();

        for (Company company : CompanyManager.getInstance().getAllCompanies()) {
            DividendConfig config = dividendConfigs.get(company.getId());
            if (config == null) continue;
            if (!config.isEnabled()) continue;
            if (config.getRate() <= 0) continue;

            if (currentTime - config.getLastDividendTime() >= config.getPeriodTicks()) {
                distributeDividends(server, company, config, currentTime);
            }
        }
    }

    /**
     * Distribute dividends for a single company.
     */
    private void distributeDividends(MinecraftServer server, Company company, DividendConfig config, long currentTime) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        double balance = ecoManager.getCompanyBalance(company.getId());
        double totalPayout = config.calculateTotalDividend(balance);

        if (totalPayout <= 0 || balance < totalPayout) {
            // Insufficient funds — skip this cycle and notify founder
            config.setLastDividendTime(currentTime);
            dirty = true;

            ServerPlayer founder = server.getPlayerList().getPlayer(company.getFounderId());
            if (founder != null) {
                founder.sendSystemMessage(Component.literal(
                    "§e[" + company.getName() + "] §cDividend payout deferred — insufficient funds. " +
                    "Balance: " + ecoManager.formatCurrency(balance) +
                    ", Required: " + ecoManager.formatCurrency(totalPayout)
                ));
            }

            // Send mail notification via StateCraft if available
            if (StateCraftEconomy.isStateCraftLoaded()) {
                StateCraftIntegration.sendCompanyDividendDeferredMail(
                    company.getFounderId(), company.getName(), balance, totalPayout);
            }
            return;
        }

        // Withdraw total payout from company treasury
        ecoManager.getOrCreateCompanyTreasury(company.getId()).subtract(totalPayout);

        // Record withdrawal transaction on company account
        ecoManager.recordAccountTransaction(company.getId(), Transaction.Type.WITHDRAWAL,
            totalPayout, null, "Dividend payout to shareholders",
            null, "Dividend System");

        // Distribute to each shareholder
        for (Map.Entry<UUID, Integer> entry : company.getShareholders().entrySet()) {
            UUID shareholderId = entry.getKey();
            double sharePercentage = company.getSharePercentage(shareholderId);
            double payout = config.calculateDividend(balance, sharePercentage);
            if (payout <= 0.01) continue;

            ecoManager.deposit(shareholderId, payout,
                "Dividend from " + company.getName() +
                " (" + String.format("%.1f%%", sharePercentage * 100) + " of shares)");

            // Notify online shareholders
            ServerPlayer shareholderPlayer = server.getPlayerList().getPlayer(shareholderId);
            if (shareholderPlayer != null) {
                shareholderPlayer.sendSystemMessage(Component.literal(
                    "§a[" + company.getName() + "] §fDividend received: " + ecoManager.formatCurrency(payout)
                ));
            }
        }

        config.setLastDividendTime(currentTime);
        dirty = true;
        ecoManager.markDirty();

        StateCraftEconomy.LOGGER.info("Company '{}' paid {} in dividends to {} shareholders",
            company.getName(), ecoManager.formatCurrency(totalPayout), company.getShareholders().size());

        // Send summary to the company's mailbox
        if (StateCraftEconomy.isStateCraftLoaded()) {
            StateCraftIntegration.sendCompanyMailboxNotificationById(company.getId(), "FINANCIAL",
                "Dividend Payout — " + company.getName(),
                String.format(
                    "§aDividends distributed successfully.\n\n" +
                    "§7Total Payout: §f%s\n" +
                    "§7Shareholders: §f%d\n" +
                    "§7Rate: §f%.1f%%\n" +
                    "§7Remaining Balance: §f%s",
                    ecoManager.formatCurrency(totalPayout),
                    company.getShareholders().size(),
                    config.getRate() * 100,
                    ecoManager.formatCurrency(balance - totalPayout)));
        }
    }

    // ==================== Company Taxation ====================

    /**
     * Collect taxes from all companies. Called from TaxationManager.collectAllTaxes().
     * Tax is a flat rate on company balance, paid to the headquarters city's state treasury.
     */
    public void collectCompanyTaxes(MinecraftServer server) {
        Collection<Company> allCompanies = CompanyManager.getInstance().getAllCompanies();
        if (allCompanies.isEmpty()) return;

        EconomyManager ecoManager = EconomyManager.getInstance();
        double taxRate = EconomyConfig.COMPANY_TAX_RATE.get();
        if (taxRate <= 0) return;

        int companiesTaxed = 0;
        double totalCollected = 0;

        for (Company company : allCompanies) {
            double balance = ecoManager.getCompanyBalance(company.getId());
            if (balance <= 0) continue;

            double tax = balance * taxRate;
            if (tax < 0.01) continue;

            // Withdraw tax from company
            ecoManager.getOrCreateCompanyTreasury(company.getId()).forceSubtract(tax);

            // Record tax transaction on company account
            ecoManager.recordAccountTransaction(company.getId(), Transaction.Type.TAX,
                tax, null, "Corporate tax (" + String.format("%.1f%%", taxRate * 100) + " of balance)",
                null, "Tax System");

            // Deposit to the state where the company's HQ city is located
            UUID cityId = company.getHeadquartersCityId();
            if (cityId != null && StateCraftEconomy.isStateCraftLoaded()) {
                UUID stateId = StateCraftIntegration.getStateIdForCity(cityId);
                if (stateId != null) {
                    ecoManager.getOrCreateStateTreasury(stateId).add(tax);
                    ecoManager.recordAccountTransaction(stateId, Transaction.Type.TAX,
                        tax, company.getId(),
                        "Corporate tax from " + company.getName(),
                        null, "Tax System");
                } else {
                    // Fallback: deposit to nation if no state found
                    UUID nationId = StateCraftIntegration.getNationIdForCity(cityId);
                    if (nationId != null) {
                        ecoManager.getOrCreateNationTreasury(nationId).add(tax);
                        ecoManager.recordAccountTransaction(nationId, Transaction.Type.TAX,
                            tax, company.getId(),
                            "Corporate tax from " + company.getName(),
                            null, "Tax System");
                    }
                }
            }

            // Notify founder
            ServerPlayer founder = server.getPlayerList().getPlayer(company.getFounderId());
            if (founder != null) {
                founder.sendSystemMessage(Component.literal(
                    "§e[" + company.getName() + "] §7Corporate tax paid: §c" + ecoManager.formatCurrency(tax)
                ));
            }

            companiesTaxed++;
            totalCollected += tax;
        }

        if (companiesTaxed > 0) {
            ecoManager.markDirty();
            dirty = true;
            StateCraftEconomy.LOGGER.info("Corporate tax collected: {} from {} companies",
                ecoManager.formatCurrency(totalCollected), companiesTaxed);
        }
    }

    // ==================== Persistence ====================

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    /**
     * Save dividend configs to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag dividendList = new ListTag();
        for (DividendConfig config : dividendConfigs.values()) {
            dividendList.add(config.save());
        }
        tag.put("DividendConfigs", dividendList);
        return tag;
    }

    /**
     * Load dividend configs from NBT.
     */
    public void load(CompoundTag tag) {
        dividendConfigs.clear();

        if (tag.contains("DividendConfigs")) {
            ListTag dividendList = tag.getList("DividendConfigs", Tag.TAG_COMPOUND);
            for (int i = 0; i < dividendList.size(); i++) {
                DividendConfig config = DividendConfig.load(dividendList.getCompound(i));
                dividendConfigs.put(config.getCompanyId(), config);
            }
        }

        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded {} dividend configs", dividendConfigs.size());
    }

    /**
     * Migrate dividend data from legacy Company NBT format.
     * Called after CompanyManager.load() on worlds that have dividend data
     * stored in the old Company tags but no CompanyEconomyManager section.
     *
     * @param companyManagerTag The raw CompanyManager NBT tag
     */
    public void migrateFromLegacyCompanyData(CompoundTag companyManagerTag) {
        if (!companyManagerTag.contains("Companies")) return;

        ListTag companiesList = companyManagerTag.getList("Companies", Tag.TAG_COMPOUND);
        int migrated = 0;
        for (int i = 0; i < companiesList.size(); i++) {
            CompoundTag companyTag = companiesList.getCompound(i);
            // Only migrate if the company tag has dividend data
            if (companyTag.contains("DividendsEnabled") || companyTag.contains("DividendRate")) {
                UUID companyId = companyTag.getUUID("Id");
                if (!dividendConfigs.containsKey(companyId)) {
                    DividendConfig config = DividendConfig.importFromLegacyCompanyTag(companyId, companyTag);
                    dividendConfigs.put(companyId, config);
                    migrated++;
                }
            }
        }

        if (migrated > 0) {
            dirty = true;
            StateCraftEconomy.LOGGER.info("Migrated {} dividend configs from legacy Company data", migrated);
        }
    }
}
