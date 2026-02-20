package com.statecraft.economy.company;

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
import java.util.stream.Collectors;

/**
 * Central manager for all companies in the economy.
 * Handles company CRUD, dividend distribution, and company taxation.
 *
 * Singleton — one instance per server.
 */
public class CompanyManager {
    private static CompanyManager instance;

    // All companies indexed by ID
    private final Map<UUID, Company> companies = new ConcurrentHashMap<>();

    // Name index for uniqueness checks (lowercase -> company id)
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    private boolean dirty = false;

    private CompanyManager() {}

    public static CompanyManager getInstance() {
        if (instance == null) {
            instance = new CompanyManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Company CRUD ====================

    /**
     * Create a new company.
     *
     * @param name Company name (must be unique)
     * @param founderId UUID of the founding player
     * @param totalShares Total number of shares to issue
     * @param headquartersCityId Optional city where the company is registered (for tax jurisdiction)
     * @return The created Company, or null if creation failed (name taken, limit reached)
     */
    @Nullable
    public Company createCompany(String name, UUID founderId, int totalShares, @Nullable UUID headquartersCityId) {
        // Check name uniqueness
        if (nameIndex.containsKey(name.toLowerCase())) {
            return null;
        }

        // Check max companies per player
        int maxPerPlayer = EconomyConfig.MAX_COMPANIES_PER_PLAYER.get();
        if (maxPerPlayer > 0) {
            long foundedCount = companies.values().stream()
                .filter(c -> c.getFounderId().equals(founderId))
                .count();
            if (foundedCount >= maxPerPlayer) {
                return null;
            }
        }

        Company company = new Company(UUID.randomUUID(), name, founderId, totalShares);
        company.setHeadquartersCityId(headquartersCityId);

        companies.put(company.getId(), company);
        nameIndex.put(name.toLowerCase(), company.getId());

        // Create the company treasury account
        EconomyManager.getInstance().getOrCreateCompanyTreasury(company.getId());

        dirty = true;
        StateCraftEconomy.LOGGER.info("Company '{}' created by {} with {} shares", name, founderId, totalShares);
        return company;
    }

    /**
     * Dissolve a company and distribute remaining balance to shareholders.
     *
     * @param companyId The company to dissolve
     * @param initiatorId The player requesting dissolution (must be founder)
     * @return true if dissolved successfully
     */
    public boolean dissolveCompany(UUID companyId, UUID initiatorId) {
        Company company = companies.get(companyId);
        if (company == null) return false;
        if (!company.isFounder(initiatorId)) return false;

        EconomyManager ecoManager = EconomyManager.getInstance();

        // If this is a bank, dissolve the bank first (pays depositors before shareholders)
        if (company.isBank()) {
            BankManager bankManager = BankManager.getInstance();
            if (bankManager.isBank(companyId)) {
                // dissolveBank handles depositor refunds and loan cleanup, updates treasury balance
                bankManager.dissolveBank(companyId, null); // server can be null for non-notification path
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

        // Remove from indices
        nameIndex.remove(company.getName().toLowerCase());
        companies.remove(companyId);

        dirty = true;
        StateCraftEconomy.LOGGER.info("Company '{}' dissolved by {}", company.getName(), initiatorId);
        return true;
    }

    // ==================== Lookups ====================

    @Nullable
    public Company getCompany(UUID companyId) {
        return companies.get(companyId);
    }

    @Nullable
    public Company getCompanyByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase());
        return id != null ? companies.get(id) : null;
    }

    public Collection<Company> getAllCompanies() {
        return Collections.unmodifiableCollection(companies.values());
    }

    /**
     * Rename a company, updating the name index.
     * @return true if renamed successfully, false if name taken
     */
    public boolean renameCompany(UUID companyId, String newName) {
        Company company = companies.get(companyId);
        if (company == null) return false;
        if (nameIndex.containsKey(newName.toLowerCase())) return false;

        nameIndex.remove(company.getName().toLowerCase());
        company.setName(newName);
        nameIndex.put(newName.toLowerCase(), companyId);
        dirty = true;
        return true;
    }

    /**
     * Get all companies where a player is a shareholder, officer, or founder.
     */
    public List<Company> getPlayerCompanies(UUID playerId) {
        return companies.values().stream()
            .filter(c -> c.isShareholder(playerId) || c.isOfficer(playerId))
            .collect(Collectors.toList());
    }

    /**
     * Get all companies where a player is an officer (can manage).
     */
    public List<Company> getPlayerManagedCompanies(UUID playerId) {
        return companies.values().stream()
            .filter(c -> c.canManage(playerId))
            .collect(Collectors.toList());
    }

    // ==================== Dividend Distribution ====================

    /**
     * Called every server tick to check if any company dividends should be paid.
     */
    public void tick(MinecraftServer server) {
        long currentTime = server.overworld().getGameTime();

        for (Company company : companies.values()) {
            if (!company.isDividendsEnabled()) continue;
            if (company.getDividendRate() <= 0) continue;

            if (currentTime - company.getLastDividendTime() >= company.getDividendPeriodTicks()) {
                distributeDividends(server, company, currentTime);
            }
        }
    }

    /**
     * Distribute dividends for a single company.
     */
    private void distributeDividends(MinecraftServer server, Company company, long currentTime) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        double balance = ecoManager.getCompanyBalance(company.getId());
        double totalPayout = company.calculateTotalDividend(balance);

        if (totalPayout <= 0 || balance < totalPayout) {
            // Insufficient funds — skip this cycle and notify founder
            company.setLastDividendTime(currentTime);
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
            double payout = company.calculateDividend(balance, shareholderId);
            if (payout <= 0.01) continue;

            ecoManager.deposit(shareholderId, payout,
                "Dividend from " + company.getName() +
                " (" + String.format("%.1f%%", company.getSharePercentage(shareholderId) * 100) + " of shares)");

            // Notify online shareholders
            ServerPlayer shareholderPlayer = server.getPlayerList().getPlayer(shareholderId);
            if (shareholderPlayer != null) {
                shareholderPlayer.sendSystemMessage(Component.literal(
                    "§a[" + company.getName() + "] §fDividend received: " + ecoManager.formatCurrency(payout)
                ));
            }
        }

        company.setLastDividendTime(currentTime);
        dirty = true;
        ecoManager.markDirty();

        StateCraftEconomy.LOGGER.info("Company '{}' paid {} in dividends to {} shareholders",
            company.getName(), ecoManager.formatCurrency(totalPayout), company.getShareholders().size());
    }

    // ==================== Company Taxation ====================

    /**
     * Collect taxes from all companies. Called from TaxationManager.collectAllTaxes().
     * Tax is a flat rate on company balance, paid to the headquarters city's state treasury.
     */
    public void collectCompanyTaxes(MinecraftServer server) {
        if (companies.isEmpty()) return;

        EconomyManager ecoManager = EconomyManager.getInstance();
        double taxRate = EconomyConfig.COMPANY_TAX_RATE.get();
        if (taxRate <= 0) return;

        int companiesTaxed = 0;
        double totalCollected = 0;

        for (Company company : companies.values()) {
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag companiesList = new ListTag();
        for (Company company : companies.values()) {
            companiesList.add(company.save());
        }
        tag.put("Companies", companiesList);
        return tag;
    }

    public void load(CompoundTag tag) {
        companies.clear();
        nameIndex.clear();

        if (tag.contains("Companies")) {
            ListTag companiesList = tag.getList("Companies", Tag.TAG_COMPOUND);
            for (int i = 0; i < companiesList.size(); i++) {
                Company company = Company.load(companiesList.getCompound(i));
                companies.put(company.getId(), company);
                nameIndex.put(company.getName().toLowerCase(), company.getId());
            }
        }

        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded {} companies", companies.size());
    }
}



