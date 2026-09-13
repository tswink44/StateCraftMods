package dev.statecraft.runtime;

import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationLocksTest {
    @Test
    void removingEconomyDoesNotUnlockMortgagedPropertyOrFundedTreasuries() {
        IntegrationLocks locks = new IntegrationLocks();
        locks.accounts.add("nation:example");
        locks.claims.add("minecraft:overworld|0|0");
        var economy = locks.offlineAccess();
        assertFalse(economy.available());
        assertTrue(economy.isAccountInUse("nation:example"));
        assertFalse(economy.isAccountInUse("city:free"));
        assertTrue(economy.isClaimEncumbered("minecraft:overworld|0|0"));
        assertFalse(economy.isClaimEncumbered("minecraft:overworld|1|0"));
        assertThrows(UserError.class, () -> economy.balance("nation:example"));
    }
}
