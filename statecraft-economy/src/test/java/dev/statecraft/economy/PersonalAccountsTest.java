package dev.statecraft.economy;

import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import org.junit.jupiter.api.Test;
import static dev.statecraft.economy.TestWorld.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalAccountsTest {
    @Test
    void dashboardBalancesAreThePlayersWalletAndDepositsNotTheBanksAssets() {
        TestWorld world = new TestWorld();
        String bank = world.bank(10_000, 0, 0);
        world.set(BUYER, 5_000);
        world.engine.banking.deposit(BUYER, bank, 500);
        int before = world.dirty;
        var presentation = new EconomyPresentation(world.engine);
        var accounts = presentation.personalAccounts(BUYER, 0);
        assertEquals(2, accounts.total());
        assertEquals("$45.00", accounts.entries().get(0).detail().fallback());
        assertEquals("$5.00", accounts.entries().get(1).detail().fallback());
        assertEquals(EntityRef.Kind.ACCOUNT, accounts.entries().get(0).target().entity().kind());
        assertEquals(BUYER.account(), accounts.entries().get(0).target().entity().id());
        assertEquals(EntityRef.Kind.BANK, accounts.entries().get(1).target().entity().kind());
        assertEquals(bank, accounts.entries().get(1).target().entity().id());
        assertEquals("economy:atm", accounts.entries().get(1).target().page());
        var atm = presentation.view(new UiContext(BUYER, accounts.entries().get(1).target()));
        assertTrue(atm.title().fallback().startsWith("ATM - "));
        assertTrue(atm.body().fallback().contains("Balance: $5.00"));
        assertFalse(atm.body().fallback().contains("$105.00"));
        assertTrue(atm.actions().stream().allMatch(action -> bank.equals(action.values().get("bank"))));
        assertEquals(before, world.dirty, "Opening the dashboard and ATM must not move money or accrue interest.");
    }

    @Test
    void aChangedActorCannotReuseAnotherPlayersWalletLink() {
        TestWorld world = new TestWorld();
        world.set(BUYER, 1000);
        var presentation = new EconomyPresentation(world.engine);
        UiQuery wallet = presentation.personalAccounts(BUYER, 0).entries().get(0).target();
        assertThrows(UserError.class, () -> presentation.view(new UiContext(OWNER, wallet)));
    }
}
