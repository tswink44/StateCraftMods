package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceDomainTest extends DomainFixture {
    @Test
    void hierarchyRequiresMatchingParentsAndOneMembershipAtEachLevel() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        assertThrows(UserError.class, () -> run(bob, "city join " + alpha.city()));
        run(bob, "nation join " + alpha.nation());
        assertThrows(UserError.class, () -> run(bob, "state join " + beta.state()));
        run(bob, "state join " + alpha.state());
        assertThrows(UserError.class, () -> run(bob, "city join " + beta.city()));
        run(bob, "city join " + alpha.city());
        assertThrows(UserError.class, () -> run(bob, "nation join " + beta.nation()));
        assertThrows(UserError.class, () -> run(bob, "nation create AnotherNation"));
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city()))
            assertTrue(government(id).members().contains(bob.id()));
        run(alice, "state kick " + alpha.state() + " Bob");
        assertEquals(alpha.nation(), data.players.get(bob.id().toString()).nationId);
        assertNull(data.players.get(bob.id().toString()).stateId);
        assertNull(data.players.get(bob.id().toString()).cityId);
    }

    @Test
    void allAffectedLeadershipMustTransferBeforeLeavingOrKicking() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        assertThrows(UserError.class, () -> run(alice, "nation leave"));
        assertThrows(UserError.class, () -> run(operator, "nation kick " + tree.nation() + " Alice"));
        run(alice, "nation leader " + tree.nation() + " Bob");
        assertThrows(UserError.class, () -> run(alice, "nation leave"));
        run(bob, "state leader " + tree.state() + " Bob");
        assertThrows(UserError.class, () -> run(alice, "nation leave"));
        run(bob, "city leader " + tree.city() + " Bob");
        run(alice, "nation leave");
        assertTrue(engine.nationOf(alice.id()).isEmpty());
        assertFalse(engine.mayManageGovernment(alice.id(), tree.city()));
        assertTrue(engine.mayManageGovernment(bob.id(), tree.city()));
        assertTrue(run(operator, "admin audit").contains("No integrity issues"));
    }

    @Test
    void permissionsInheritDownwardWithoutGrantingSiblingOrParentAuthority() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "state leader " + tree.state() + " Bob");
        run(dave, "nation join " + tree.nation());
        run(alice, "state create " + tree.nation() + " OtherState Dave");
        String otherState = government("OtherState").id();
        run(dave, "city create " + otherState + " OtherCity");
        String otherCity = government("OtherCity").id();
        assertFalse(engine.mayManageGovernment(bob.id(), otherCity));
        assertFalse(engine.mayManageGovernment(dave.id(), tree.city()));
        assertTrue(engine.mayManageGovernment(alice.id(), otherCity));
        assertThrows(UserError.class, () -> run(bob, "chunk claim " + otherCity + " here"));
        assertThrows(UserError.class, () -> run(alice, "nation join " + tree.nation()));
    }

    @Test
    void scopedOfficialsAndAccountsAgreeAtEveryLevel() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        join(dave, tree);
        run(alice, "state leader " + tree.state() + " Bob");
        run(alice, "city leader " + tree.city() + " Cara");
        assertTrue(engine.mayManageGovernment(alice.id(), tree.city()));
        assertTrue(engine.mayManageGovernment(bob.id(), tree.city()));
        assertFalse(engine.mayManageGovernment(bob.id(), tree.nation()));
        assertTrue(engine.mayManageGovernment(cara.id(), tree.city()));
        assertFalse(engine.mayManageGovernment(cara.id(), tree.state()));
        assertThrows(UserError.class, () -> run(cara, "nation setting " + tree.nation() + " open false"));
        assertThrows(UserError.class, () -> run(bob, "state kick " + tree.state() + " Alice"));
        run(alice, "nation officer " + tree.nation() + " Dave add");
        assertTrue(engine.mayManageGovernment(dave.id(), tree.city()));
        assertThrows(UserError.class, () -> run(dave, "nation leader " + tree.nation() + " Dave"));
        for (Actor actor : List.of(alice, bob, cara, dave, operator, felix)) {
            for (GovernanceAccess.GovernmentView government : engine.governments())
                assertEquals(engine.mayAccessAccount(actor.id(), government.account()),
                        engine.accountsFor(actor.id()).contains(government.account()));
            assertTrue(engine.accountsFor(actor.id()).contains(actor.account()));
        }
        assertFalse(engine.mayManageGovernment(operator.id(), tree.nation()));
        assertFalse(engine.mayAccessAccount(operator.id(), "nation:" + tree.nation()));
        run(operator, "nation setting " + tree.nation() + " open false");
        assertEquals("false", government(tree.nation()).settings().get("open"));
    }

    @Test
    void treasuryViewsAndTheirNestedCollectionsAreImmutableSnapshots() {
        Tree tree = tree(alice, "Alpha");
        GovernanceAccess.GovernmentView old = government(tree.nation());
        assertThrows(UnsupportedOperationException.class, () -> old.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.settings().put("open", "false"));
        assertThrows(UnsupportedOperationException.class, () -> engine.governments().clear());
        join(bob, tree);
        assertFalse(old.members().contains(bob.id()));
        String company = company(alice, "Immutable Company");
        GovernanceAccess.CompanyView oldCompany = engine.company(company).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> oldCompany.shares().clear());
        engine.transferShares(company, alice.id(), bob.id(), 100);
        assertFalse(oldCompany.shares().containsKey(bob.id()));
        assertFalse(engine.mayAccessAccount(UUID.randomUUID(), "nation:" + tree.nation()));
    }

    @Test
    void adjacencyAndNationLimitsAreDimensionAware() {
        config.maxClaimsPerNation = 3;
        config.maxClaimsPerCity = 3;
        configure();
        Tree tree = tree(alice, "Alpha");
        claim(alice, tree, 0, 0);
        assertThrows(UserError.class, () -> claim(alice, tree, 2, 0));
        claim(alice, tree, "minecraft:the_nether", 100, 100);
        claim(alice, tree, "minecraft:the_nether", 100, 101);
        assertThrows(UserError.class, () -> claim(alice, tree, 1, 0));
        assertEquals(3, engine.claims().size());
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " minecraft:overworld|1875001|0"));
    }

    @Test
    void unclaimCannotSplitANationAndForcedUnclaimStillHonorsEconomy() {
        Tree tree = tree(alice, "Alpha");
        String first = claim(alice, tree, 0, 0);
        String bridge = claim(alice, tree, 1, 0);
        claim(alice, tree, 2, 0);
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + bridge));
        useEconomy();
        economy.encumbered.add(bridge);
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + bridge));
        assertTrue(engine.claim(bridge).isPresent());
        economy.encumbered.remove(bridge);
        run(operator, "admin unclaim " + bridge);
        assertTrue(engine.claim(bridge).isEmpty());
        run(operator, "admin unclaim " + first);
        assertEquals(1, engine.claims().size());
    }

    @Test
    void privateTitlesAreSeparateFromPoliticalHierarchyAndProtectedFromOfficials() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String key = claim(alice, tree, 0, 0);
        assertTrue(engine.mayAct(bob, key, AccessAction.BREAK, null));
        engine.transferProperty(key, bob.account());
        assertEquals(tree.city(), engine.claim(key).orElseThrow().cityId());
        assertEquals(tree.nation(), engine.claim(key).orElseThrow().nationId());
        assertFalse(engine.mayAct(alice, key, AccessAction.BREAK, null));
        assertTrue(engine.mayAct(bob, key, AccessAction.BREAK, null));
        assertFalse(engine.maySellProperty(alice.id(), key));
        assertTrue(engine.maySellProperty(bob.id(), key));
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + key));
        assertThrows(UserError.class, () -> run(alice, "chunk permit " + key + " Dave all"));
        run(bob, "chunk permit " + key + " Alice BREAK");
        assertTrue(engine.mayAct(alice, key, AccessAction.BREAK, null));
        assertFalse(engine.mayAct(alice, key, AccessAction.PLACE, null));
        run(alice, "nation setting " + tree.nation() + " foreignAccess true");
        assertFalse(engine.mayAct(dave, key, AccessAction.BLOCK_INTERACT, null));
        assertThrows(UserError.class, () -> run(bob, "chunk permit " + key + " Dave PVP"));
    }

    @Test
    void propertyTransfersRequireValidAccountsAndCitizenEligibility() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        assertFalse(engine.mayBuyProperty(bob.id(), key));
        assertThrows(UserError.class, () -> engine.transferProperty(key, bob.account()));
        assertThrows(UserError.class, () -> engine.transferProperty(key, "player:" + UUID.randomUUID()));
        assertThrows(UserError.class, () -> engine.transferProperty(key, "company:" + UUID.randomUUID()));
        assertThrows(UserError.class, () -> engine.transferProperty(key, "nation:" + tree.city()));
        assertThrows(UserError.class, () -> engine.transferProperty(key, "system:fees"));
        assertThrows(UserError.class, () -> engine.transferProperty(null, alice.account()));
        run(alice, "nation setting " + tree.nation() + " foreignProperty true");
        assertTrue(engine.mayBuyProperty(bob.id(), key));
        engine.transferProperty(key, bob.account());
        assertEquals(bob.account(), engine.claim(key).orElseThrow().ownerAccount());
    }

    @Test
    void trustedCorporateAndGovernmentSettlementsPreservePoliticalOwnershipAcrossNations() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String bank = company(bob, "Foreign Bank");
        String key = claim(alice, alpha, 0, 0);
        engine.recordImprovement(key, 12);
        run(alice, "chunk permit " + key + " Cara BREAK");
        var before = engine.claim(key).orElseThrow();
        assertFalse(engine.mayBuyProperty(bob.id(), key));
        assertFalse(engine.mayBuyProperty(dave.id(), key));
        engine.transferProperty(key, "company:" + bank);
        assertEquals("company:" + bank, engine.claim(key).orElseThrow().ownerAccount());
        assertTrue(data.claims.get(key).permits.isEmpty());
        assertTrue(engine.mayAct(bob, key, AccessAction.BREAK, null));
        engine.transferProperty(key, "nation:" + beta.nation());
        var after = engine.claim(key).orElseThrow();
        assertEquals("nation:" + beta.nation(), after.ownerAccount());
        assertEquals(before.cityId(), after.cityId());
        assertEquals(before.stateId(), after.stateId());
        assertEquals(before.nationId(), after.nationId());
        assertEquals(before.dimension(), after.dimension());
        assertEquals(before.x(), after.x());
        assertEquals(before.z(), after.z());
        assertEquals(before.claimedAt(), after.claimedAt());
        assertEquals(12, after.improvements());
        assertTrue(engine.mayAct(dave, key, AccessAction.PLACE, null));
        assertFalse(engine.mayBuyProperty(dave.id(), key));
    }

    @Test
    void settlementExceptionNeverBypassesRemainingCollateralOrInvalidAccountReferences() {
        Tree alpha = tree(alice, "Alpha");
        String key = claim(alice, alpha, 0, 0);
        String bank = company(bob, "Foreign Bank");
        useEconomy();
        economy.encumbered.add(key);
        assertThrows(UserError.class, () -> engine.transferProperty(key, "company:" + bank));
        assertEquals("city:" + alpha.city(), engine.claim(key).orElseThrow().ownerAccount());
        economy.encumbered.remove(key);
        assertThrows(UserError.class, () -> engine.transferProperty(key, "company:" + UUID.randomUUID()));
        assertThrows(UserError.class, () -> engine.transferProperty(key, "nation:" + alpha.city()));
        engine.transferProperty(key, "company:" + bank);
        assertEquals("company:" + bank, engine.claim(key).orElseThrow().ownerAccount());
    }

    @Test
    void explicitOperatorBypassCannotSurviveLossOfOperatorAuthority() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        assertFalse(engine.mayAct(operator, key, AccessAction.BREAK, null));
        run(operator, "admin bypass on");
        assertTrue(engine.bypassEnabled(operator.id()));
        for (AccessAction action : AccessAction.values())
            assertTrue(engine.mayAct(operator, key, action, alice.id()));
        Actor deopped = operator(operator, false);
        assertFalse(engine.mayAct(deopped, key, AccessAction.BREAK, null));
        assertFalse(engine.bypassEnabled(operator.id()));
        assertThrows(UserError.class, () -> run(deopped, "admin bypass on"));
        assertFalse(engine.mayAct(operator, key, AccessAction.BREAK, null));
    }

    @Test
    void DiagnosingAnotherPlayerDoesNotChangeTheirBypassState() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        run(operator, "admin bypass on");
        run(alice, "chunk protection " + key + " Operator");
        assertTrue(engine.bypassEnabled(operator.id()));
        assertTrue(engine.mayAct(operator, key, AccessAction.PLACE, null));
    }

    @Test
    void ratesAreLocalWhileAccessAndValuationPoliciesInherit() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        run(alice, "nation setting " + tree.nation() + " incomeTaxBps 1000");
        run(alice, "nation setting " + tree.nation() + " baseChunkValue 12345");
        run(alice, "nation setting " + tree.nation() + " foreignAccess true");
        run(alice, "nation setting " + tree.nation() + " explosions true");
        assertEquals("0", government(tree.state()).settings().get("incomeTaxBps"));
        assertEquals("0", government(tree.city()).settings().get("incomeTaxBps"));
        assertEquals("12345", government(tree.city()).settings().get("baseChunkValue"));
        assertTrue(engine.mayAct(bob, key, AccessAction.BLOCK_INTERACT, null));
        assertTrue(engine.allowsExplosion(key));
        run(alice, "city setting " + tree.city() + " foreignAccess false");
        assertFalse(engine.mayAct(bob, key, AccessAction.BLOCK_INTERACT, null));
        run(alice, "city setting " + tree.city() + " foreignAccess inherit");
        assertTrue(engine.mayAct(bob, key, AccessAction.BLOCK_INTERACT, null));
        assertThrows(UserError.class, () -> run(alice, "nation setting " + tree.nation() + " incomeTaxBps 10001"));
        assertThrows(UserError.class, () -> run(alice, "nation setting " + tree.nation() + " pvp yes"));
        assertThrows(UserError.class, () -> run(alice, "nation setting " + tree.nation() + " unknown true"));
        assertEquals("1000", government(tree.nation()).settings().get("incomeTaxBps"));
    }

    @Test
    void buildingCountersCannotOverflowOrBecomeNegative() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        engine.recordImprovement(key, 50);
        assertEquals(50, engine.claim(key).orElseThrow().improvements());
        engine.recordImprovement(key, Integer.MAX_VALUE);
        assertEquals(1_000_000_000, engine.claim(key).orElseThrow().improvements());
        engine.recordImprovement(key, Integer.MIN_VALUE);
        assertEquals(0, engine.claim(key).orElseThrow().improvements());
        engine.recordImprovement("minecraft:overworld|99|99", 1);
        assertEquals(1, engine.claims().size());
    }

    @Test
    void automaticClaimsUseTheSameFeesPermissionsAndAdjacencyChecks() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        assertThrows(UserError.class, () -> run(bob, "chunk autoclaim on"));
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(bob, "chunk autoclaim on");
        config.claimFee = 100;
        configure();
        assertThrows(UserError.class, () -> engine.autoClaim(bob));
        assertTrue(engine.claims().isEmpty());
        useEconomy();
        economy.balances.put(bob.account(), 300L);
        engine.autoClaim(bob);
        assertEquals(200, economy.balance(bob.account()));
        engine.autoClaim(bob);
        assertEquals(200, economy.balance(bob.account()));
        assertThrows(UserError.class, () -> engine.autoClaim(at(bob, "minecraft:overworld", 3, 0)));
        assertEquals(200, economy.balance(bob.account()));
        engine.autoClaim(at(bob, "minecraft:overworld", 1, 0));
        assertEquals(100, economy.balance(bob.account()));
        run(bob, "city leave");
        assertTrue(engine.autoClaimEnabled(bob.id()));
        run(bob, "nation leave");
        assertFalse(engine.autoClaimEnabled(bob.id()));
    }

    @Test
    void paidCreationIsAtomicAndNeverFreeWithoutEconomy() {
        config.nationCreationFee = 500;
        configure();
        assertThrows(UserError.class, () -> run(alice, "nation create AlphaNation"));
        assertTrue(data.governments.isEmpty());
        useEconomy();
        assertThrows(UserError.class, () -> run(alice, "nation create AlphaNation"));
        assertNull(data.players.get(alice.id().toString()).nationId);
        economy.balances.put(alice.account(), 1_000L);
        run(alice, "nation create AlphaNation");
        assertEquals(500, economy.balance(alice.account()));
        assertEquals(500, economy.balance(config.feeAccount));
        assertEquals(1, economy.batches.size());
    }

    @Test
    void invitationsExistAtAllLevelsAndAreBoundedAndExpiring() {
        config.maxInvitationsPerGovernment = 1;
        configure();
        run(alice, "nation create AlphaNation");
        String nation = government("AlphaNation").id();
        assertThrows(UserError.class, () -> run(bob, "nation join " + nation));
        run(alice, "nation invite " + nation + " Bob");
        assertThrows(UserError.class, () -> run(alice, "nation invite " + nation + " Cara"));
        run(alice, "nation invite " + nation + " Bob");
        run(bob, "nation accept " + nation);
        assertTrue(data.invitations.isEmpty());
        run(alice, "state create " + nation + " AlphaState");
        String state = government("AlphaState").id();
        run(alice, "state invite " + state + " Bob");
        run(bob, "state accept " + state);
        run(alice, "city create " + state + " AlphaCity");
        String city = government("AlphaCity").id();
        run(alice, "city invite " + city + " Bob");
        advance(3_001);
        assertThrows(UserError.class, () -> run(bob, "city accept " + city));
        run(alice, "city invite " + city + " Bob");
        run(bob, "city accept " + city);
        assertEquals(city, data.players.get(bob.id().toString()).cityId);
    }

    @Test
    void personalMailIsPrivateEvenFromOperatorsAndCopiesReadIndependently() {
        run(alice, "mail send Bob \"Private plans\" \"Do not disclose these.\"");
        GovernanceData.Mail received = data.players.get(bob.id().toString()).inbox.get(0);
        GovernanceData.Mail sent = data.players.get(alice.id().toString()).sent.get(0);
        assertNotSame(received, sent);
        assertFalse(received.read);
        assertTrue(sent.read);
        assertThrows(UserError.class, () -> run(cara, "mail read " + received.id));
        assertThrows(UserError.class, () -> run(operator, "mail read " + received.id));
        assertTrue(run(bob, "mail read " + received.id).contains("Do not disclose"));
        assertTrue(received.read);
        run(alice, "mail delete " + sent.id);
        assertEquals(1, data.players.get(bob.id().toString()).inbox.size());
        run(bob, "mail reply " + received.id + " \"Understood.\"");
        assertEquals("Understood.", data.players.get(alice.id().toString()).inbox.get(0).body);
        assertFalse(run(cara, "profile Bob").contains("Unread"));
    }

    @Test
    void misplacedPersistedMailEnvelopeCannotLeakThroughAnotherMailbox() {
        run(alice, "mail send Cara Classified \"Not Bob's message.\"");
        GovernanceData.Mail privateMessage = data.players.get(cara.id().toString()).inbox.get(0);
        data.players.get(bob.id().toString()).inbox.add(privateMessage);
        assertFalse(run(bob, "mail inbox").contains("Classified"));
        assertThrows(UserError.class, () -> run(bob, "mail read " + privateMessage.id));
        assertThrows(UserError.class, () -> run(bob, "mail reply " + privateMessage.id + " Wrong"));
        assertFalse(privateMessage.read);
    }

    @Test
    void administrativeLeadershipReassignmentMovesMembershipWithoutAbandoningOtherOffices() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        run(operator, "admin leader " + beta.city() + " Bob");
        GovernanceData.Player player = data.players.get(bob.id().toString());
        assertEquals(beta.nation(), player.nationId);
        assertEquals(beta.state(), player.stateId);
        assertEquals(beta.city(), player.cityId);
        assertFalse(government(alpha.nation()).members().contains(bob.id()));
        assertEquals(bob.id(), government(beta.city()).leader());
        assertThrows(UserError.class, () -> run(operator, "admin leader " + alpha.nation() + " Dave"));
        assertEquals(beta.nation(), engine.nationOf(dave.id()).orElseThrow());
        assertTrue(run(operator, "admin audit").contains("No integrity issues"));
    }

    @Test
    void malformedWorldKeysNeverDefaultToWildernessPermission() {
        assertFalse(engine.mayAct(alice, "not-a-chunk", AccessAction.BREAK, null));
        assertFalse(engine.mayAct(alice, null, AccessAction.PLACE, null));
        assertFalse(engine.allowsExplosion("minecraft:overworld|tooFar|0"));
        assertFalse(engine.allowsExplosion(null));
    }

    @Test
    void officialComposeAndReadUseScopedGovernmentAuthority() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "mail official send " + tree.city() + " Bob Notice \"Official message.\"");
        GovernanceData.Mail mail = data.players.get(bob.id().toString()).inbox.get(0);
        assertEquals("city:" + tree.city(), mail.sender);
        assertThrows(UserError.class, () -> run(bob, "mail official sent " + tree.city()));
        run(alice, "city officer " + tree.city() + " Bob add");
        assertTrue(run(bob, "mail official sent " + tree.city()).contains(mail.id));
        assertThrows(UserError.class, () -> run(bob, "mail official send " + tree.nation() + " Cara Fake \"Not authorized.\""));
        run(cara, "mail send government:" + tree.nation() + " Petition \"Please read.\"");
        GovernanceData.Mail petition = data.governments.get(tree.nation()).inbox.get(0);
        assertThrows(UserError.class, () -> run(bob, "mail official read " + tree.nation() + " " + petition.id));
        assertTrue(run(alice, "mail official read " + tree.nation() + " " + petition.id).contains("Please read."));
    }

    @Test
    void mailAndCommandOutputHaveHardBounds() {
        config.maxMail = 2;
        config.maxCommandOutput = 512;
        configure();
        for (int i = 0; i < 5; i++) engine.mail(bob.id(), "Notice " + i, "Message " + i);
        assertEquals(2, data.players.get(bob.id().toString()).inbox.size());
        assertEquals("Notice 3", data.players.get(bob.id().toString()).inbox.get(0).subject);
        assertTrue(run(bob, "help governments").length() <= 512);
        assertThrows(UserError.class, () -> run(alice, "mail send Bob Subject " + q("x".repeat(config.maxMailBodyLength + 1))));
        assertThrows(UserError.class, () -> run(bob, "mail inbox 0"));
        assertThrows(UserError.class, () -> run(bob, "mail inbox 999"));
    }

    @Test
    void longRowsReducePageSizeInsteadOfDroppingEntireRowsToTruncation() {
        config.maxCommandOutput = 600;
        configure();
        List<String> rows = List.of("first " + "a".repeat(220), "second " + "b".repeat(220),
                "third " + "c".repeat(220), "fourth " + "d".repeat(220));
        String first = engine.page("Rows", rows, 1);
        String second = engine.page("Rows", rows, 2);
        assertTrue(first.length() <= 600);
        assertTrue(second.length() <= 600);
        assertTrue(first.contains(rows.get(0)));
        assertTrue(first.contains(rows.get(1)));
        assertTrue(second.contains(rows.get(2)));
        assertTrue(second.contains(rows.get(3)));
    }

    @Test
    void disbandRequiresExplicitCascadeAndCannotOrphanFundsOrMembers() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        claim(alice, tree, 0, 0);
        assertThrows(UserError.class, () -> run(alice, "nation disband " + tree.nation()));
        useEconomy();
        economy.balances.put("state:" + tree.state(), 1L);
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        assertEquals(3, data.governments.size());
        economy.balances.put("state:" + tree.state(), 0L);
        economy.used.add("city:" + tree.city());
        assertThrows(UserError.class, () -> run(alice, "nation disband " + tree.nation() + " cascade"));
        economy.used.clear();
        run(alice, "nation disband " + tree.nation() + " cascade");
        assertTrue(data.governments.isEmpty());
        assertTrue(data.claims.isEmpty());
        for (Actor actor : List.of(alice, bob)) {
            GovernanceData.Player player = data.players.get(actor.id().toString());
            assertNull(player.nationId);
            assertNull(player.stateId);
            assertNull(player.cityId);
        }
    }

    @Test
    void disablingPreviouslyPresentEconomyDoesNotBypassDestructiveChecks() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.encumbered.add(key);
        engine.setEconomy(EconomyAccess.UNAVAILABLE);
        assertTrue(data.economySeen);
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + key));
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        assertThrows(UserError.class, () -> engine.transferProperty(key, alice.account()));
        assertTrue(engine.claim(key).isPresent());
    }

    @Test
    void persistentFinancialLockAdapterWorksWhileEconomyIsUnavailable() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 500L);
        economy.used.add("nation:" + tree.nation());
        economy.encumbered.add(key);
        economy.available = false;
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + key));
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        economy.encumbered.remove(key);
        run(operator, "admin unclaim " + key);
        assertTrue(engine.claim(key).isEmpty());
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        economy.balances.put("nation:" + tree.nation(), 0L);
        economy.used.clear();
        run(operator, "admin delete nation " + tree.nation() + " cascade");
        assertTrue(data.governments.isEmpty());
        config.nationCreationFee = 1;
        configure();
        assertThrows(UserError.class, () -> run(alice, "nation create StillRequiresEconomy"));
    }

    @Test
    void safeExplicitRepairPreservesPrivateTitlesAndFinancialEncumbrances() {
        Tree tree = tree(alice, "Alpha");
        useEconomy();
        String missingCity = UUID.randomUUID().toString();
        GovernanceData.Claim publicClaim = orphan("minecraft:overworld|40|0", missingCity, "city:" + missingCity);
        GovernanceData.Claim privateClaim = orphan("minecraft:overworld|41|0", missingCity, bob.account());
        GovernanceData.Claim pledgedClaim = orphan("minecraft:overworld|42|0", missingCity, "city:" + missingCity);
        economy.encumbered.add(pledgedClaim.key);
        data.players.get(dave.id().toString()).nationId = UUID.randomUUID().toString();
        data.players.get(dave.id().toString()).autoClaim = true;
        data.governments.get(tree.nation()).officers.add(UUID.randomUUID().toString());
        int claimCount = data.claims.size();
        assertTrue(run(operator, "admin audit").contains("Orphan"));
        run(operator, "admin repair preview");
        assertEquals(claimCount, data.claims.size());
        run(operator, "admin repair apply");
        assertTrue(data.claims.containsKey(publicClaim.key));
        assertTrue(data.claims.containsKey(privateClaim.key));
        assertTrue(data.claims.containsKey(pledgedClaim.key));
        assertNull(data.players.get(dave.id().toString()).nationId);
        assertFalse(data.players.get(dave.id().toString()).autoClaim);
        run(operator, "admin reassign " + privateClaim.key + " " + tree.city());
        assertEquals(bob.account(), engine.claim(privateClaim.key).orElseThrow().ownerAccount());
    }

    @Test
    void malformedOrUnknownInputsFailClearlyWithoutCreatingObjects() {
        for (String input : List.of("nonsense", "nation create", "nation create \"unterminated",
                "nation create \"bad|name\"", "nation create xx", "nation create all",
                "mail send UnknownPlayer subject text", "admin bypass on",
                "chunk autoclaim perhaps", "help unknown", "nation create Foo\nbar")) {
            assertThrows(UserError.class, () -> run(alice, input), input);
        }
        assertThrows(UserError.class, () -> run(alice, "x".repeat(4_097)));
        assertThrows(UserError.class, () -> run(alice, null));
        assertTrue(data.governments.isEmpty());
        run(alice, "/sc nation create \"United Cities\"");
        assertTrue(engine.government("united cities").isPresent());
        assertThrows(UserError.class, () -> run(bob, "/statecraft nation create \"UNITED CITIES\""));
        Actor renamed = new Actor(bob.id(), "Robert", false, "minecraft:overworld", 0, 0);
        run(renamed, "info");
        run(alice, "mail send Robert Subject Body");
        assertThrows(UserError.class, () -> run(alice, "mail send Bob Subject Body"));
    }

    @Test
    void configurationIsValidatedAndCopiedRatherThanExternallyMutable() {
        assertDoesNotThrow(config::validate);
        for (var field : GovernanceConfig.class.getFields()) {
            assertFalse(Modifier.isFinal(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers()));
            assertTrue(field.getType().isPrimitive() || field.getType() == String.class);
        }
        config.electionVotingMillis = config.electionIntervalMillis;
        assertThrows(UserError.class, config::validate);
        config.electionVotingMillis = 2_000;
        config.nationCreationFee = -1;
        assertThrows(UserError.class, config::validate);
        config.nationCreationFee = 0;
        configure();
        config.nationCreationFee = 1_000;
        run(alice, "nation create StillFree");
        assertTrue(engine.government("StillFree").isPresent());
    }

    @Test
    void menusRegisterIdempotentlyWithExecutableSyntaxAndFields() {
        CoreMenus.register();
        int count = MenuRegistry.pages().size();
        CoreMenus.register();
        assertEquals(count, MenuRegistry.pages().size());
        assertTrue(count >= 20);
        for (var page : MenuRegistry.pages()) {
            if (!page.id().startsWith("statecraft:")) continue;
            assertFalse(page.query().isBlank());
            for (var action : page.actions()) {
                String command = action.command().replaceAll("<[a-z_]+>", "example");
                assertFalse(dev.statecraft.api.CommandLine.split(command).isEmpty());
                assertFalse(command.contains("<"));
            }
        }
    }

    @Test
    void everyCoreDiscoveryQueryWorksBeforeJoiningANation() {
        CoreMenus.register();
        for (var page : MenuRegistry.pages()) {
            if (page.id().startsWith("statecraft:"))
                assertDoesNotThrow(() -> run(felix, page.query()), page.id() + ": " + page.query());
        }
    }

    @Test
    void minimalGameTestSequenceCreatesTheWholeHierarchyAndCurrentClaim() {
        run(alice, "/sc nation create GTNation");
        run(alice, "/sc state create GTNation GTState");
        run(alice, "/sc city create GTState GTCity");
        run(alice, "/sc chunk claim GTNation here");
        run(alice, "/sc chunk assignstate GTNation here GTState");
        run(alice, "/sc chunk assigncity GTState here GTCity");
        var claim = engine.claim(alice.chunkKey()).orElseThrow();
        assertEquals(government("GTNation").id(), claim.nationId());
        assertEquals(government("GTState").id(), claim.stateId());
        assertEquals(government("GTCity").id(), claim.cityId());
        assertEquals("city:" + claim.cityId(), claim.ownerAccount());
    }

    @Test
    void mailFormQuotesMultilineValuesAsSingleArgumentsAndPreservesThem() {
        CoreMenus.register();
        String template = MenuRegistry.get("statecraft:mail").actions().stream()
                .filter(action -> "Compose".equals(action.label())).findFirst().orElseThrow().command();
        String body = "First line\n\tA \"quoted\" point and a literal \\path; <recipient> is just text.";
        String command = fillForm(template, Map.of("recipient", "Bob", "subject", "Detailed report", "body", body));
        List<String> words = dev.statecraft.api.CommandLine.split(command);
        assertEquals(5, words.size());
        assertEquals(body, words.get(4));
        run(alice, command);
        var message = data.players.get(bob.id().toString()).inbox.get(0);
        assertEquals(body, message.body);
        assertTrue(run(bob, "mail read " + message.id).contains(body));
        assertThrows(UserError.class, () -> run(alice,
                "mail send Bob " + q("Not\nA Subject") + " " + q(body)));
        engine.mail(bob.id(), "System report", "Gross: 10\r\n\tTax: 1\nNet: 9\u0000");
        String systemBody = data.players.get(bob.id().toString()).inbox.get(1).body;
        assertEquals("Gross: 10\n\tTax: 1\nNet: 9 ", systemBody);
    }

    @Test
    void lawFormPreservesMultilineNarrativeButNamesAndTitlesStaySingleLine() {
        Tree tree = tree(alice, "Alpha");
        CoreMenus.register();
        String template = MenuRegistry.get("statecraft:legislature").actions().stream()
                .filter(action -> "Propose roleplay law".equals(action.label())).findFirst().orElseThrow().command();
        String narrative = "Article I\n\tRespect private property.\nArticle II\n\tHold public debates.";
        run(alice, fillForm(template, Map.of("nation", tree.nation(), "title", "Public charter", "text", narrative)));
        String bill = latestBill();
        assertEquals(narrative, data.bills.get(bill).text);
        advance(1_000);
        run(alice, "bill vote " + bill + " yes");
        advance(1_000);
        run(alice, "bill sign " + bill);
        assertEquals(narrative, data.laws.get(tree.nation()).get(0).text);
        run(alice, "nation description " + tree.nation() + " " + q(narrative));
        assertEquals(narrative, data.governments.get(tree.nation()).description);
        assertThrows(UserError.class, () -> run(bob, "nation create " + q("Bad\nName")));
        assertThrows(UserError.class, () -> run(alice,
                fillForm(template, Map.of("nation", tree.nation(), "title", "Bad\tTitle", "text", narrative))));
    }

    @Test
    void companyBidTemplateKeepsItsPrefixAndQuotedCompanyNameInOneToken() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String company = company(bob, "Builder Guild");
        run(alice, "contract create " + tree.nation() + " Steps \"Build stone steps.\" " + key);
        String contract = latestContract();
        CoreMenus.register();
        String template = MenuRegistry.get("statecraft:contracts").actions().stream()
                .filter(action -> "Company bid".equals(action.label())).findFirst().orElseThrow().command();
        String description = "Northern route\n\tStone steps and railings.";
        String command = fillForm(template, Map.of("contract", contract, "amount", "0",
                "description", description, "company", "Builder Guild"));
        List<String> words = dev.statecraft.api.CommandLine.split(command);
        assertEquals(6, words.size());
        assertEquals("company:Builder Guild", words.get(5));
        run(bob, command);
        var bid = data.contracts.get(contract).bids.get(bob.id().toString());
        assertEquals("company:" + company, bid.account);
        assertEquals(description, bid.text);
    }

    @Test
    void workflowDirectoriesExposeParentAndWorkflowIdsToUnaffiliatedPlayers() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        run(alice, "bill propose " + alpha.nation() + " roleplay - Holiday \"A public holiday.\"");
        String bill = latestBill();
        String company = company(alice, "Builders");
        run(alice, "company propose " + company + " roleplay - Charter \"A public charter.\"");
        String proposal = latestCompanyProposal();
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        String alliance = latestDiplomacy();
        assertTrue(run(felix, "election list").contains(alpha.nation()));
        String bills = run(felix, "bill list all");
        assertTrue(bills.contains(bill));
        assertTrue(bills.contains(alpha.nation()));
        String proposals = run(felix, "company proposals all");
        assertTrue(proposals.contains(company));
        assertTrue(proposals.contains(proposal));
        String diplomacy = run(felix, "diplomacy proposals all");
        assertTrue(diplomacy.contains(alliance));
        assertTrue(diplomacy.contains(beta.nation()));
        advance(1_000);
        run(alice, "bill vote " + bill + " yes");
        advance(1_000);
        run(alice, "bill sign " + bill);
        String laws = run(felix, "law list all");
        assertTrue(laws.contains(bill));
        assertTrue(laws.contains(alpha.nation()));
    }

    private GovernanceData.Claim orphan(String key, String city, String owner) {
        GovernanceData.Claim claim = new GovernanceData.Claim();
        claim.key = key;
        claim.cityId = city;
        claim.ownerAccount = owner;
        data.claims.put(key, claim);
        return claim;
    }

    private String fillForm(String template, Map<String, String> values) {
        var matcher = java.util.regex.Pattern.compile("<([a-z][a-z0-9_]*)>").matcher(template);
        StringBuilder command = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            assertNotNull(value, "Missing form value: " + matcher.group(1));
            matcher.appendReplacement(command, java.util.regex.Matcher.quoteReplacement(q(value)));
        }
        matcher.appendTail(command);
        return command.toString();
    }
}
