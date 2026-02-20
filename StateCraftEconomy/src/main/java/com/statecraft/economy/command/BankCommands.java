package com.statecraft.economy.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.company.*;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Commands for the banking system.
 * Registered under /eco bank (or /economy bank).
 */
public class BankCommands {

    /**
     * Build the bank subcommand tree.
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBankCommand() {
        return Commands.literal("bank")
            // Player commands
            .then(Commands.literal("open")
                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                    .executes(BankCommands::openAccount)))
            .then(Commands.literal("close")
                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                    .executes(BankCommands::closeAccount)))
            .then(Commands.literal("deposit")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .then(Commands.argument("bankName", StringArgumentType.greedyString())
                        .executes(BankCommands::depositToBank))))
            .then(Commands.literal("withdraw")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .then(Commands.argument("bankName", StringArgumentType.greedyString())
                        .executes(BankCommands::withdrawFromBank))))
            .then(Commands.literal("balance")
                .executes(BankCommands::showAllBankBalances)
                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                    .executes(BankCommands::showBankBalance)))
            .then(Commands.literal("info")
                .then(Commands.argument("bankName", StringArgumentType.greedyString())
                    .executes(BankCommands::bankInfo)))
            .then(Commands.literal("list")
                .executes(BankCommands::listBanks))
            // Loan commands
            .then(Commands.literal("loan")
                .then(Commands.literal("apply")
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .then(Commands.argument("bankName", StringArgumentType.greedyString())
                            .executes(BankCommands::applyForLoan))))
                .then(Commands.literal("repay")
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(BankCommands::repayLoanAuto)
                        .then(Commands.argument("loanId", StringArgumentType.string())
                            .executes(BankCommands::repayLoanById))))
                .then(Commands.literal("list")
                    .executes(BankCommands::listLoans)))
            // Officer commands
            .then(Commands.literal("set")
                .then(Commands.literal("interest")
                    .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(BankCommands::setDepositInterest)))
                .then(Commands.literal("loanrate")
                    .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(BankCommands::setLoanInterest)))
                .then(Commands.literal("reserve")
                    .then(Commands.argument("ratio", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(BankCommands::setReserveRatio)))
                .then(Commands.literal("defaultstrategy")
                    .then(Commands.literal("seize")
                        .executes(ctx -> setDefaultStrategy(ctx, BankCompany.DefaultStrategy.SEIZE_BALANCE)))
                    .then(Commands.literal("repossess")
                        .executes(ctx -> setDefaultStrategy(ctx, BankCompany.DefaultStrategy.REPOSSESS_PROPERTY)))));
    }

    // ==================== Open / Close Account ====================

    private static int openAccount(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String bankName = StringArgumentType.getString(ctx, "bankName");

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;

            if (bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou already have an account at §f" + displayName));
                return 0;
            }

            bank.openAccount(player.getUUID());
            BankManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal(
                "§a✓ Account opened at §f" + displayName +
                "§a. Deposit interest rate: §f" + String.format("%.1f%%", bank.getDepositInterestRate() * 100)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int closeAccount(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String bankName = StringArgumentType.getString(ctx, "bankName");

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;

            if (!bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou don't have an account at §f" + displayName));
                return 0;
            }

            double balance = bank.getDepositorBalance(player.getUUID());
            if (balance > 0.01) {
                player.sendSystemMessage(Component.literal(
                    "§cWithdraw your balance of §f" + EconomyManager.getInstance().formatCurrency(balance) +
                    "§c first using §f/eco bank withdraw " + String.format("%.0f", balance) + " " + bankName));
                return 0;
            }

            if (!bank.getActiveLoansForBorrower(player.getUUID()).isEmpty()) {
                player.sendSystemMessage(Component.literal(
                    "§cYou have active loans at this bank. Repay them before closing your account."));
                return 0;
            }

            bank.closeAccount(player.getUUID());
            BankManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal("§aAccount closed at §f" + displayName));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Deposit / Withdraw ====================

    private static int depositToBank(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            String bankName = StringArgumentType.getString(ctx, "bankName");
            EconomyManager ecoManager = EconomyManager.getInstance();

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;

            if (!bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou don't have an account at §f" + displayName));
                return 0;
            }

            // Check player balance
            double playerBalance = ecoManager.getBalance(player.getUUID());
            if (playerBalance < amount) {
                player.sendSystemMessage(Component.literal(
                    "§cInsufficient funds. Your balance: " + ecoManager.formatCurrency(playerBalance)));
                return 0;
            }

            // Withdraw from player personal account
            ecoManager.withdraw(player.getUUID(), amount, "Bank deposit to " + displayName);

            // Add to depositor balance and bank treasury
            bank.addDepositorBalance(player.getUUID(), amount);
            ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).add(amount);

            // Record transaction
            ecoManager.recordAccountTransaction(bank.getCompanyId(),
                com.statecraft.economy.core.Transaction.Type.DEPOSIT,
                amount, player.getUUID(), "Bank deposit from " + player.getName().getString(),
                player.getUUID(), player.getName().getString());

            BankManager.getInstance().markDirty();
            ecoManager.markDirty();

            player.sendSystemMessage(Component.literal(
                "§a✓ Deposited " + ecoManager.formatCurrency(amount) + " to §f" + displayName +
                "§a. Bank balance: §f" + ecoManager.formatCurrency(bank.getDepositorBalance(player.getUUID()))));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int withdrawFromBank(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            String bankName = StringArgumentType.getString(ctx, "bankName");
            EconomyManager ecoManager = EconomyManager.getInstance();

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;

            if (!bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou don't have an account at §f" + displayName));
                return 0;
            }

            double bankBalance = bank.getDepositorBalance(player.getUUID());
            if (bankBalance < amount) {
                player.sendSystemMessage(Component.literal(
                    "§cInsufficient bank balance. Your balance at " + displayName + ": " +
                    ecoManager.formatCurrency(bankBalance)));
                return 0;
            }

            double treasuryBalance = ecoManager.getCompanyBalance(bank.getCompanyId());
            if (!bank.subtractDepositorBalance(player.getUUID(), amount, treasuryBalance)) {
                player.sendSystemMessage(Component.literal(
                    "§cWithdrawal denied — bank reserve requirements would be breached. " +
                    "Try a smaller amount."));
                return 0;
            }

            // Subtract from bank treasury and deposit to player
            ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).subtract(amount);
            ecoManager.deposit(player.getUUID(), amount, "Bank withdrawal from " + displayName);

            // Record transaction
            ecoManager.recordAccountTransaction(bank.getCompanyId(),
                com.statecraft.economy.core.Transaction.Type.WITHDRAWAL,
                amount, player.getUUID(), "Bank withdrawal by " + player.getName().getString(),
                player.getUUID(), player.getName().getString());

            BankManager.getInstance().markDirty();
            ecoManager.markDirty();

            player.sendSystemMessage(Component.literal(
                "§a✓ Withdrew " + ecoManager.formatCurrency(amount) + " from §f" + displayName +
                "§a. Remaining bank balance: §f" + ecoManager.formatCurrency(bank.getDepositorBalance(player.getUUID()))));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Balance ====================

    private static int showAllBankBalances(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            EconomyManager ecoManager = EconomyManager.getInstance();
            List<BankCompany> banks = BankManager.getInstance().getPlayerBanks(player.getUUID());

            if (banks.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7You don't have an account at any bank. Use §f/eco bank list §7and §f/eco bank open <name>"));
                return 0;
            }

            player.sendSystemMessage(Component.literal("§6§l═══ Your Bank Accounts ═══"));
            for (BankCompany bank : banks) {
                Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
                String name = company != null ? company.getName() : "Unknown";
                double balance = bank.getDepositorBalance(player.getUUID());
                player.sendSystemMessage(Component.literal(
                    "  §f" + name + "§7: " + ecoManager.formatCurrency(balance) +
                    " §7(Interest: " + String.format("%.1f%%", bank.getDepositInterestRate() * 100) + ")"));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int showBankBalance(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String bankName = StringArgumentType.getString(ctx, "bankName");
            EconomyManager ecoManager = EconomyManager.getInstance();

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;

            if (!bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cYou don't have an account at §f" + displayName));
                return 0;
            }

            player.sendSystemMessage(Component.literal(
                "§6" + displayName + " §7Balance: §f" +
                ecoManager.formatCurrency(bank.getDepositorBalance(player.getUUID()))));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Bank Info ====================

    private static int bankInfo(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String bankName = StringArgumentType.getString(ctx, "bankName");
            EconomyManager ecoManager = EconomyManager.getInstance();

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;
            boolean isOfficer = company != null && company.isOfficer(player.getUUID());
            double treasuryBalance = ecoManager.getCompanyBalance(bank.getCompanyId());

            player.sendSystemMessage(Component.literal("§6§l═══════ " + displayName + " (Bank) ═══════"));
            player.sendSystemMessage(Component.literal("§7Members: §f" + bank.getMemberCount()));
            player.sendSystemMessage(Component.literal("§7Deposit Interest: §f" +
                String.format("%.2f%%", bank.getDepositInterestRate() * 100) + " §7per period"));
            player.sendSystemMessage(Component.literal("§7Loan Interest: §f" +
                String.format("%.2f%%", bank.getLoanInterestRate() * 100) + " §7per period"));
            player.sendSystemMessage(Component.literal("§7Reserve Ratio: §f" +
                String.format("%.0f%%", bank.getReserveRatio() * 100)));
            player.sendSystemMessage(Component.literal("§7Default Strategy: §f" +
                bank.getDefaultStrategy().name().toLowerCase().replace("_", " ")));

            // Officer-only details
            if (isOfficer) {
                player.sendSystemMessage(Component.literal("§e--- Officer Details ---"));
                player.sendSystemMessage(Component.literal("§7Treasury: §f" + ecoManager.formatCurrency(treasuryBalance)));
                player.sendSystemMessage(Component.literal("§7Total Deposits: §f" +
                    ecoManager.formatCurrency(bank.getTotalDeposits())));
                player.sendSystemMessage(Component.literal("§7Available to Lend: §f" +
                    ecoManager.formatCurrency(bank.getAvailableToLend(treasuryBalance))));

                boolean compliant = bank.isReserveCompliant(treasuryBalance);
                player.sendSystemMessage(Component.literal("§7Reserve Status: " +
                    (compliant ? "§a✓ Compliant" : "§c✗ NON-COMPLIANT (deficit: " +
                    ecoManager.formatCurrency(bank.getReserveDeficit(treasuryBalance)) + ")")));

                player.sendSystemMessage(Component.literal("§7Active Loans: §f" + bank.getActiveLoans().size() +
                    " §7(Outstanding: " + ecoManager.formatCurrency(bank.getTotalOutstandingLoans()) + ")"));
            }

            // Player's own info if they're a member
            if (bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§b--- Your Account ---"));
                player.sendSystemMessage(Component.literal("§7Your Balance: §f" +
                    ecoManager.formatCurrency(bank.getDepositorBalance(player.getUUID()))));

                var myLoans = bank.getActiveLoansForBorrower(player.getUUID());
                if (!myLoans.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§7Your Active Loans: §f" + myLoans.size()));
                    for (Loan loan : myLoans) {
                        player.sendSystemMessage(Component.literal(
                            "  §7Loan " + loan.getId().toString().substring(0, 8) + ": §f" +
                            ecoManager.formatCurrency(loan.getRemainingBalance()) + " §7remaining" +
                            (loan.getMissedPayments() > 0 ? " §c(" + loan.getMissedPayments() + " missed)" : "")));
                    }
                }
            }

            player.sendSystemMessage(Component.literal("§6§l═══════════════════════════"));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== List Banks ====================

    private static int listBanks(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            EconomyManager ecoManager = EconomyManager.getInstance();
            Collection<BankCompany> allBanks = BankManager.getInstance().getAllBanks();

            if (allBanks.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                    "§7No banks exist yet. Create one with §f/eco company create <name> bank"));
                return 0;
            }

            player.sendSystemMessage(Component.literal("§6§l═══ Banks (" + allBanks.size() + ") ═══"));
            for (BankCompany bank : allBanks) {
                Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
                String name = company != null ? company.getName() : "Unknown";
                String memberStatus = bank.isMember(player.getUUID()) ? " §a[Member]" : "";
                player.sendSystemMessage(Component.literal(
                    "§f" + name + " §7- Interest: " +
                    String.format("%.1f%%", bank.getDepositInterestRate() * 100) +
                    " §7| Members: " + bank.getMemberCount() +
                    " §7| Loans: " + (bank.getLoanInterestRate() > 0 ? "§aYes" : "§cNo") +
                    memberStatus));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Loans ====================

    private static int applyForLoan(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            String bankName = StringArgumentType.getString(ctx, "bankName");
            EconomyManager ecoManager = EconomyManager.getInstance();

            BankCompany bank = BankManager.getInstance().getBankByName(bankName);
            if (bank == null) {
                player.sendSystemMessage(Component.literal("§cBank '" + bankName + "' not found."));
                return 0;
            }

            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            String displayName = company != null ? company.getName() : bankName;

            if (!bank.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                    "§cYou need an account at §f" + displayName + "§c to apply for a loan. Use §f/eco bank open " + bankName));
                return 0;
            }

            Loan loan = BankManager.getInstance().applyForLoan(player.getUUID(), bank.getCompanyId(), amount);
            if (loan == null) {
                // Determine reason
                int maxLoans = EconomyConfig.MAX_ACTIVE_LOANS_PER_PLAYER.get();
                if (maxLoans > 0 && bank.getActiveLoanCount(player.getUUID()) >= maxLoans) {
                    player.sendSystemMessage(Component.literal(
                        "§cMax active loans reached (" + maxLoans + "). Repay existing loans first."));
                } else {
                    double treasuryBalance = ecoManager.getCompanyBalance(bank.getCompanyId());
                    double available = bank.getAvailableToLend(treasuryBalance);
                    if (amount > available) {
                        player.sendSystemMessage(Component.literal(
                            "§cBank has insufficient lending capacity. Available: " +
                            ecoManager.formatCurrency(available)));
                    } else {
                        player.sendSystemMessage(Component.literal(
                            "§cLoan application denied. Check loan amount limits and bank status."));
                    }
                }
                return 0;
            }

            player.sendSystemMessage(Component.literal(
                "§a✓ Loan approved! §f" + ecoManager.formatCurrency(amount) + "§a deposited to your account."));
            player.sendSystemMessage(Component.literal(
                "§7Loan ID: §f" + loan.getId().toString().substring(0, 8) +
                " §7| Interest: §f" + String.format("%.1f%%", loan.getInterestRate() * 100) + " §7per period"));
            player.sendSystemMessage(Component.literal(
                "§7Repay with §f/eco bank loan repay <amount>"));

            // Notify bank officers
            if (company != null) {
                for (UUID officerId : company.getOfficers()) {
                    ServerPlayer officer = player.server.getPlayerList().getPlayer(officerId);
                    if (officer != null && !officerId.equals(player.getUUID())) {
                        officer.sendSystemMessage(Component.literal(
                            "§e[" + displayName + "] §fLoan issued: " + ecoManager.formatCurrency(amount) +
                            " to " + player.getName().getString()));
                    }
                }
            }

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int repayLoanAuto(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            EconomyManager ecoManager = EconomyManager.getInstance();

            // Find first active loan
            Loan activeLoan = null;
            for (BankCompany bank : BankManager.getInstance().getAllBanks()) {
                var loans = bank.getActiveLoansForBorrower(player.getUUID());
                if (!loans.isEmpty()) {
                    activeLoan = loans.get(0);
                    break;
                }
            }

            if (activeLoan == null) {
                player.sendSystemMessage(Component.literal("§cYou have no active loans."));
                return 0;
            }

            return doRepay(player, activeLoan, amount, ecoManager);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int repayLoanById(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            String loanIdStr = StringArgumentType.getString(ctx, "loanId");
            EconomyManager ecoManager = EconomyManager.getInstance();

            // Find the loan by partial ID
            Loan targetLoan = null;
            for (BankCompany bank : BankManager.getInstance().getAllBanks()) {
                for (Loan loan : bank.getActiveLoansForBorrower(player.getUUID())) {
                    if (loan.getId().toString().startsWith(loanIdStr)) {
                        targetLoan = loan;
                        break;
                    }
                }
                if (targetLoan != null) break;
            }

            if (targetLoan == null) {
                player.sendSystemMessage(Component.literal("§cLoan not found with ID starting with '" + loanIdStr + "'."));
                return 0;
            }

            return doRepay(player, targetLoan, amount, ecoManager);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int doRepay(ServerPlayer player, Loan loan, double amount, EconomyManager ecoManager) {
        double applied = BankManager.getInstance().repayLoan(player.getUUID(), loan.getId(), amount);
        if (applied <= 0) {
            player.sendSystemMessage(Component.literal("§cCould not apply payment. Check your balance."));
            return 0;
        }

        Company company = CompanyManager.getInstance().getCompany(loan.getBankCompanyId());
        String bankName = company != null ? company.getName() : "Bank";

        if (loan.isFullyRepaid()) {
            player.sendSystemMessage(Component.literal(
                "§a✓ Loan fully repaid! §7" + ecoManager.formatCurrency(applied) + " paid to §f" + bankName));
        } else {
            player.sendSystemMessage(Component.literal(
                "§a✓ Paid " + ecoManager.formatCurrency(applied) + " to §f" + bankName +
                "§a. Remaining: §f" + ecoManager.formatCurrency(loan.getRemainingBalance())));
        }
        return 1;
    }

    private static int listLoans(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            EconomyManager ecoManager = EconomyManager.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy");

            List<Loan> allLoans = new ArrayList<>();
            for (BankCompany bank : BankManager.getInstance().getAllBanks()) {
                allLoans.addAll(bank.getAllLoans().stream()
                    .filter(l -> l.getBorrowerId().equals(player.getUUID()))
                    .toList());
            }

            if (allLoans.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7You have no loans."));
                return 0;
            }

            player.sendSystemMessage(Component.literal("§6§l═══ Your Loans ═══"));
            for (Loan loan : allLoans) {
                Company company = CompanyManager.getInstance().getCompany(loan.getBankCompanyId());
                String bankName = company != null ? company.getName() : "Unknown";
                String statusColor = switch (loan.getStatus()) {
                    case ACTIVE -> "§a";
                    case REPAID -> "§2";
                    case DEFAULTED -> "§c";
                    case SEIZED -> "§4";
                };

                player.sendSystemMessage(Component.literal(
                    statusColor + "[" + loan.getStatus() + "] §f" + bankName +
                    " §7— " + ecoManager.formatCurrency(loan.getRemainingBalance()) + " remaining" +
                    " §7(Principal: " + ecoManager.formatCurrency(loan.getPrincipalAmount()) + ")" +
                    " §7ID: " + loan.getId().toString().substring(0, 8)));
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Officer Settings ====================

    private static int setDepositInterest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double rate = DoubleArgumentType.getDouble(ctx, "rate");

            BankCompany bank = getOfficerBank(player);
            if (bank == null) return 0;

            bank.setDepositInterestRate(rate);
            BankManager.getInstance().markDirty();

            // Update BankRegistry entry
            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            if (company != null) {
                var registryBank = EconomyManager.getInstance().getBankRegistry().getBank(bank.getCompanyId());
                if (registryBank != null) {
                    registryBank.setInterestRate(rate);
                }
            }

            player.sendSystemMessage(Component.literal(
                "§aDeposit interest rate set to §f" + String.format("%.2f%%", rate * 100)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setLoanInterest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double rate = DoubleArgumentType.getDouble(ctx, "rate");

            BankCompany bank = getOfficerBank(player);
            if (bank == null) return 0;

            bank.setLoanInterestRate(rate);
            BankManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal(
                "§aLoan interest rate set to §f" + String.format("%.2f%%", rate * 100)));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setReserveRatio(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            double ratio = DoubleArgumentType.getDouble(ctx, "ratio");

            BankCompany bank = getOfficerBank(player);
            if (bank == null) return 0;

            double globalMin = EconomyConfig.DEFAULT_RESERVE_RATIO.get();

            // Check for national policy override
            double nationalMin = globalMin;
            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            if (company != null && company.getHeadquartersCityId() != null && StateCraftEconomy.isStateCraftLoaded()) {
                double policyMin = StateCraftIntegration.getNationReserveRatioPolicy(company.getHeadquartersCityId());
                if (policyMin > 0) {
                    nationalMin = policyMin;
                }
            }

            bank.setReserveRatioWithPolicy(ratio, nationalMin);
            BankManager.getInstance().markDirty();

            double floor = Math.max(globalMin, nationalMin);
            String floorNote = bank.getReserveRatio() > ratio ?
                " §7(Minimum enforced: " + String.format("%.0f%%", floor * 100) + ")" : "";

            player.sendSystemMessage(Component.literal(
                "§aReserve ratio set to §f" + String.format("%.0f%%", bank.getReserveRatio() * 100) + floorNote));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setDefaultStrategy(CommandContext<CommandSourceStack> ctx, BankCompany.DefaultStrategy strategy) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            BankCompany bank = getOfficerBank(player);
            if (bank == null) return 0;

            bank.setDefaultStrategy(strategy);
            BankManager.getInstance().markDirty();
            player.sendSystemMessage(Component.literal(
                "§aLoan default strategy set to §f" + strategy.name().toLowerCase().replace("_", " ")));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Helpers ====================

    /**
     * Get the bank company the player is an officer of.
     */
    private static BankCompany getOfficerBank(ServerPlayer player) {
        for (BankCompany bank : BankManager.getInstance().getAllBanks()) {
            Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
            if (company != null && company.isOfficer(player.getUUID())) {
                return bank;
            }
        }
        player.sendSystemMessage(Component.literal("§cYou are not an officer of any bank."));
        return null;
    }
}


