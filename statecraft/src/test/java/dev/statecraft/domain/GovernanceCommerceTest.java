package dev.statecraft.domain;

import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceCommerceTest extends DomainFixture {
    @Test
    void shareReservationsPreventDoubleSpendingAndSupportPartialSettlement() {
        String company = company(alice, "Builders");
        engine.reserveShares(company, alice.id(), "stock:listing-one", 6_000);
        assertEquals(10_000, engine.sharesOf(company, alice.id()));
        assertEquals(4_000, engine.availableShares(company, alice.id()));
        assertThrows(UserError.class, () -> engine.reserveShares(company, alice.id(), "stock:listing-two", 4_001));
        assertThrows(UserError.class, () -> engine.transferShares(company, alice.id(), cara.id(), 4_001));
        engine.transferShares(company, alice.id(), cara.id(), 4_000);
        assertEquals(0, engine.availableShares(company, alice.id()));
        engine.settleShares("stock:listing-one", bob.id(), 2_500);
        assertEquals(3_500, data.shareReservations.get("stock:listing-one").quantity);
        assertEquals(3_500, engine.sharesOf(company, alice.id()));
        assertEquals(2_500, engine.sharesOf(company, bob.id()));
        assertEquals(0, engine.availableShares(company, alice.id()));
        assertThrows(UserError.class, () -> engine.settleShares("stock:listing-one", alice.id(), 1));
        assertThrows(UserError.class, () -> engine.settleShares("stock:listing-one", bob.id(), 3_501));
        engine.settleShares("stock:listing-one", bob.id(), 3_500);
        assertFalse(data.shareReservations.containsKey("stock:listing-one"));
        assertEquals(0, engine.sharesOf(company, alice.id()));
        assertEquals(10_000, engine.company(company).orElseThrow().shares().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void reservationReferencesAreStableIdempotentAndReleasedWithoutChangingEquity() {
        String company = company(alice, "Builders");
        engine.reserveShares(company, alice.id(), "listing-1", 100);
        engine.reserveShares(company, alice.id(), "listing-1", 100);
        assertEquals(9_900, engine.availableShares(company, alice.id()));
        assertThrows(UserError.class, () -> engine.reserveShares(company, alice.id(), "listing-1", 101));
        engine.releaseShares("listing-1");
        engine.releaseShares("listing-1");
        assertEquals(10_000, engine.availableShares(company, alice.id()));
        assertEquals(10_000, engine.sharesOf(company, alice.id()));
        assertThrows(UserError.class, () -> engine.settleShares("missing", bob.id(), 1));
        assertThrows(UserError.class, () -> engine.reserveShares(company, alice.id(), "bad reference", 1));
        assertThrows(UserError.class, () -> engine.transferShares(company, alice.id(), bob.id(), -1));
        assertThrows(UserError.class, () -> engine.transferShares(company, alice.id(), UUID.randomUUID(), 1));
    }

    @Test
    void reservedStockAndOtherShareholdersProtectCompanyDeletion() {
        String company = company(alice, "Builders");
        engine.reserveShares(company, alice.id(), "listing", 100);
        assertThrows(UserError.class, () -> run(alice, "company disband " + company));
        assertThrows(UserError.class, () -> run(operator, "admin delete company " + company));
        engine.releaseShares("listing");
        engine.transferShares(company, alice.id(), bob.id(), 100);
        assertThrows(UserError.class, () -> run(alice, "company disband " + company));
        engine.transferShares(company, bob.id(), alice.id(), 100);
        run(alice, "company disband " + company);
        assertTrue(engine.company(company).isEmpty());
    }

    @Test
    void equityDoesNotGrantTreasuryAuthorityAndMembershipDoesNotGrantEquity() {
        String company = company(alice, "Builders");
        engine.transferShares(company, alice.id(), bob.id(), 2_000);
        assertFalse(engine.mayManageCompany(bob.id(), company));
        assertFalse(engine.mayAccessAccount(bob.id(), "company:" + company));
        employ(alice, cara, company);
        assertEquals(0, engine.sharesOf(company, cara.id()));
        assertFalse(engine.mayManageCompany(cara.id(), company));
        run(alice, "company officer " + company + " Cara add");
        assertTrue(engine.mayManageCompany(cara.id(), company));
        assertTrue(engine.accountsFor(cara.id()).contains("company:" + company));
        assertThrows(UserError.class, () -> run(cara, "company owner " + company + " Cara"));
        run(cara, "company leave " + company);
        assertFalse(engine.mayAccessAccount(cara.id(), "company:" + company));
        assertEquals(2_000, engine.sharesOf(company, bob.id()));
    }

    @Test
    void managementOwnershipRequiresMemberShareholderAndClearAccountObligations() {
        String company = company(alice, "Builders");
        assertThrows(UserError.class, () -> run(alice, "company leave " + company));
        assertThrows(UserError.class, () -> run(alice, "company owner " + company + " Bob"));
        employ(alice, bob, company);
        assertThrows(UserError.class, () -> run(alice, "company owner " + company + " Bob"));
        engine.transferShares(company, alice.id(), bob.id(), 100);
        useEconomy();
        economy.used.add("company:" + company);
        assertThrows(UserError.class, () -> run(alice, "company owner " + company + " Bob"));
        economy.used.clear();
        run(alice, "company owner " + company + " Bob");
        assertEquals(bob.id(), engine.company(company).orElseThrow().owner());
        assertFalse(engine.mayManageCompany(alice.id(), company));
        assertEquals(9_900, engine.sharesOf(company, alice.id()));
    }

    @Test
    void shareholderProposalWeightsAreFrozenSoTransferredSharesCannotVoteTwice() {
        String company = company(alice, "Builders");
        employ(alice, bob, company);
        engine.transferShares(company, alice.id(), bob.id(), 4_000);
        run(alice, "company propose " + company + " owner Bob Succession \"Elect Bob manager.\"");
        String proposal = latestCompanyProposal();
        engine.transferShares(company, alice.id(), cara.id(), 6_000);
        assertThrows(UserError.class, () -> run(cara, "company vote " + proposal + " yes"));
        run(alice, "company vote " + proposal + " yes");
        run(bob, "company vote " + proposal + " no");
        assertThrows(UserError.class, () -> run(alice, "company vote " + proposal + " no"));
        advance(1_000);
        assertEquals("ENACTED", data.companyProposals.get(proposal).status);
        assertEquals(bob.id(), engine.company(company).orElseThrow().owner());
        assertEquals(10_000, engine.company(company).orElseThrow().shares().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void dividendsPayEveryShareholderAtomicallyWithDeterministicCentRounding() {
        config.totalCompanyShares = 3;
        configure();
        String company = company(alice, "Builders");
        engine.transferShares(company, alice.id(), bob.id(), 1);
        engine.transferShares(company, alice.id(), cara.id(), 1);
        useEconomy();
        economy.balances.put("company:" + company, 100L);
        run(alice, "company propose " + company + " dividend 1.00 Dividend \"Distribute one dollar.\"");
        String proposal = latestCompanyProposal();
        run(alice, "company vote " + proposal + " yes");
        run(bob, "company vote " + proposal + " yes");
        advance(1_000);
        assertEquals("ENACTED", data.companyProposals.get(proposal).status);
        assertEquals(0, economy.balance("company:" + company));
        assertEquals(34, economy.balance(alice.account()));
        assertEquals(33, economy.balance(bob.account()));
        assertEquals(33, economy.balance(cara.account()));
        assertEquals(1, economy.batches.size());
        assertEquals(3, economy.batches.get(0).size());
    }

    @Test
    void rejectedDividendCanRetryButNeverPartiallyPaysOrExecutesTwice() {
        String company = company(alice, "Builders");
        assertThrows(UserError.class, () -> run(alice, "company propose " + company + " dividend 5 Dividend Text"));
        useEconomy();
        run(alice, "company propose " + company + " dividend 5 Dividend Text");
        String proposal = latestCompanyProposal();
        run(alice, "company vote " + proposal + " yes");
        advance(1_000);
        assertEquals("READY", data.companyProposals.get(proposal).status);
        assertTrue(economy.batches.isEmpty());
        economy.balances.put("company:" + company, 500L);
        run(alice, "company execute " + proposal);
        assertEquals(500, economy.balance(alice.account()));
        assertThrows(UserError.class, () -> run(alice, "company execute " + proposal));
        assertEquals(1, economy.batches.size());
    }

    @Test
    void votedDissolutionWorksWithoutConfiscatingReservedSharesOrProperty() {
        String company = company(alice, "Builders");
        engine.transferShares(company, alice.id(), bob.id(), 4_000);
        run(alice, "company propose " + company + " dissolve - Dissolve \"Close the company.\"");
        String proposal = latestCompanyProposal();
        run(alice, "company vote " + proposal + " yes");
        run(bob, "company vote " + proposal + " yes");
        advance(1_000);
        assertEquals("ENACTED", data.companyProposals.get(proposal).status);
        assertTrue(engine.company(company).isEmpty());
        assertTrue(run(alice, "company proposal " + proposal).contains("ENACTED"));
    }

    @Test
    void fullHolderTransferAtShareholderLimitDoesNotNeedAnExtraSlot() {
        config.maxCompanyShareholders = 1;
        configure();
        String company = company(alice, "Builders");
        assertThrows(UserError.class, () -> engine.transferShares(company, alice.id(), bob.id(), 1));
        engine.transferShares(company, alice.id(), bob.id(), 10_000);
        assertEquals(1, engine.company(company).orElseThrow().shares().size());
        assertEquals(10_000, engine.sharesOf(company, bob.id()));
    }

    @Test
    void paidContractRequiresReviewEscrowsAwardAndPaysOnlyAfterIndependentApproval() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 10_000L);
        run(alice, "contract create " + tree.nation() + " Road \"Build a road.\" " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 25 \"Stone road.\"");
        assertThrows(UserError.class, () -> run(alice, "contract award " + contract + " Bob"));
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        assertEquals("AWARDED", data.contracts.get(contract).status);
        assertEquals(7_500, economy.balance("nation:" + tree.nation()));
        assertEquals(2_500, economy.balance("escrow:contract:" + contract));
        assertEquals(0, economy.balance(bob.account()));
        assertThrows(UserError.class, () -> run(alice, "contract complete " + contract));
        run(bob, "contract submit " + contract + " \"Road is ready.\"");
        assertThrows(UserError.class, () -> run(bob, "contract complete " + contract));
        assertThrows(UserError.class, () -> run(operator(bob, true), "contract complete " + contract));
        run(alice, "contract complete " + contract);
        assertEquals("COMPLETED", data.contracts.get(contract).status);
        assertEquals(2_500, economy.balance(bob.account()));
        assertEquals(0, economy.balance("escrow:contract:" + contract));
        assertEquals(0, data.contracts.get(contract).escrowCents);
        assertThrows(UserError.class, () -> run(alice, "contract complete " + contract));
        assertTrue(engine.maySellProperty(alice.id(), key));
    }

    @Test
    void biddersCannotChooseUntrustedPayeesOrAwardTheirOwnPaidWork() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String otherCompany = company(dave, "Other Builders");
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 10_000L);
        run(alice, "contract create " + tree.nation() + " Road \"Build a road.\" " + key);
        String contract = latestContract();
        assertThrows(UserError.class, () -> run(bob, "contract bid " + contract + " 10 Work player:" + dave.id()));
        assertThrows(UserError.class, () -> run(bob, "contract bid " + contract + " 10 Work company:" + otherCompany));
        run(alice, "contract bid " + contract + " 10 Work");
        run(alice, "contract review " + contract);
        assertThrows(UserError.class, () -> run(alice, "contract award " + contract + " Alice"));
        assertThrows(UserError.class, () -> run(operator(alice, true), "contract award " + contract + " Alice"));
        assertEquals("REVIEW", data.contracts.get(contract).status);
        assertTrue(economy.batches.isEmpty());
    }

    @Test
    void failedEscrowTransferLeavesAwardUnchangedAndBidsAreNotPublic() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        run(alice, "contract create " + tree.nation() + " Road \"Build a road.\" " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 10 \"Confidential quote.\"");
        assertThrows(UserError.class, () -> run(cara, "contract bids " + contract));
        run(alice, "contract review " + contract);
        assertThrows(UserError.class, () -> run(alice, "contract award " + contract + " Bob"));
        assertEquals("REVIEW", data.contracts.get(contract).status);
        assertNull(data.contracts.get(contract).winner);
        assertEquals(0, data.contracts.get(contract).escrowCents);
        assertEquals(0, economy.balance("escrow:contract:" + contract));
    }

    @Test
    void contractCancellationRefundsOnlyTheIssuerAndRequiresEconomyForEscrow() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 10_000L);
        run(alice, "contract create " + tree.nation() + " Road \"Build a road.\" " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 20 Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        assertThrows(UserError.class, () -> run(cara, "contract cancel " + contract + " Unwanted"));
        engine.setEconomy(EconomyAccess.UNAVAILABLE);
        assertThrows(UserError.class, () -> run(bob, "contract cancel " + contract + " Unable"));
        assertEquals("AWARDED", data.contracts.get(contract).status);
        assertEquals(2_000, data.contracts.get(contract).escrowCents);
        useEconomy();
        run(bob, "contract cancel " + contract + " \"Unable to complete.\"");
        assertEquals("CANCELLED", data.contracts.get(contract).status);
        assertEquals(10_000, economy.balance("nation:" + tree.nation()));
        assertEquals(0, economy.balance("escrow:contract:" + contract));
        assertEquals(0, economy.balance(bob.account()));
    }

    @Test
    void activeContractsProtectChunksGovernmentsAndCompanyOwnership() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String company = company(bob, "Builders");
        employ(bob, cara, company);
        engine.transferShares(company, bob.id(), cara.id(), 100);
        run(alice, "contract create " + tree.nation() + " Road \"Build a road.\" " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 0 Work company:" + company);
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + key));
        assertThrows(UserError.class, () -> engine.transferProperty(key, alice.account()));
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        assertThrows(UserError.class, () -> run(operator, "admin delete company " + company));
        assertThrows(UserError.class, () -> run(bob, "company owner " + company + " Cara"));
        run(bob, "contract withdraw " + contract);
        run(bob, "company owner " + company + " Cara");
    }

    @Test
    void zeroPriceContractsWorkWithoutEconomyButNonzeroBidsDoNot() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        run(alice, "contract create " + tree.nation() + " Park \"Plant trees.\" " + key);
        String contract = latestContract();
        assertThrows(UserError.class, () -> run(bob, "contract bid " + contract + " 0.01 Work"));
        run(bob, "contract bid " + contract + " 0 Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        run(bob, "contract submit " + contract + " Done");
        run(alice, "contract return " + contract + " \"Add more trees.\"");
        run(bob, "contract submit " + contract + " Done");
        run(alice, "contract complete " + contract);
        assertEquals("COMPLETED", data.contracts.get(contract).status);
        assertFalse(data.economySeen);
    }

    @Test
    void payoutRevalidatesTheRecordedPayeeInsteadOfTrustingAnArbitraryDestination() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 10_000L);
        run(alice, "contract create " + tree.nation() + " Road \"Build a road.\" " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 10 Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        run(bob, "contract submit " + contract + " Done");
        data.contracts.get(contract).payeeAccount = felix.account();
        assertThrows(UserError.class, () -> run(alice, "contract complete " + contract));
        assertEquals("SUBMITTED", data.contracts.get(contract).status);
        assertEquals(1_000, economy.balance("escrow:contract:" + contract));
        assertEquals(0, economy.balance(felix.account()));
    }

    @Test
    void unawardedContractsExpireButAwardedEscrowDoesNotDisappearOnLongTicks() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        run(alice, "contract create " + tree.nation() + " Empty \"No bids.\" " + key);
        String empty = latestContract();
        advance(5_000);
        assertEquals("EXPIRED", data.contracts.get(empty).status);
        run(alice, "contract create " + tree.nation() + " Review \"Review bids.\" " + key);
        String review = latestContract();
        run(bob, "contract bid " + review + " 0 Work");
        advance(5_000);
        assertEquals("REVIEW", data.contracts.get(review).status);
        advance(5_000);
        assertEquals("EXPIRED", data.contracts.get(review).status);
        run(alice, "contract create " + tree.nation() + " Awarded \"Work pending.\" " + key);
        String awarded = latestContract();
        run(bob, "contract bid " + awarded + " 0 Work");
        run(alice, "contract review " + awarded);
        run(alice, "contract award " + awarded + " Bob");
        advance(1_000_000);
        assertEquals("AWARDED", data.contracts.get(awarded).status);
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + key));
    }
}
