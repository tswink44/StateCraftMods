package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiView;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentOverviewMailboxTest extends DomainFixture {
    @Test
    void inboxAndSentRowsAreScopedReadableAndUseExistingPrivateCopyReferences() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(bob, "Beta");
        run(bob, "mail send government:" + alpha.nation() + " ScopedIncoming TOP_SECRET_INCOMING");
        run(alice, "mail official send " + alpha.nation() + " Bob ScopedOutgoing TOP_SECRET_OUTGOING");
        run(alice, "mail official send " + alpha.nation() + " government:" + alpha.nation() + " ScopedSelf TOP_SECRET_SELF");
        engine.mail(alice.id(), "ScopedPersonal", "TOP_SECRET_PERSONAL");
        engine.governmentMail(alpha.state(), "ScopedChild", "TOP_SECRET_CHILD");
        engine.governmentMail(beta.nation(), "ScopedForeign", "TOP_SECRET_FOREIGN");
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        UiView inbox = service.officialMailbox(operator, alpha.nation(), "Scoped", 0);
        assertEquals(4, inbox.rows().size());
        assertTrue(inbox.title().fallback().contains("AlphaNation"));
        assertTrue(inbox.rows().stream().anyMatch(row -> row.title().fallback().equals("ScopedIncoming")
                && row.detail().fallback().contains("Bob") && row.detail().fallback().contains("Inbox")
                && row.detail().fallback().contains(DisplayText.date(time.get()))));
        assertTrue(inbox.rows().stream().anyMatch(row -> row.title().fallback().equals("ScopedOutgoing")
                && row.detail().fallback().contains("AlphaNation") && row.detail().fallback().contains("Bob")
                && row.detail().fallback().contains("Sent")));
        assertFalse(inbox.toString().contains("TOP_SECRET"));
        var references = new HashSet<String>();
        for (var row : inbox.rows()) {
            assertEquals(EntityRef.Kind.MAIL, row.entity().kind());
            assertEquals("statecraft", row.entity().namespace());
            assertTrue(references.add(row.entity().id()));
            var copy = GovernancePresentation.mail(engine, alice, row.entity().id());
            assertEquals(alpha.nation(), copy.government().id);
            assertEquals(GovernancePresentation.mailId(engine.account(copy.government()), copy.sent(), copy.message()), row.entity().id());
            assertFalse(row.title().fallback().contains(alpha.nation()));
            assertFalse(row.detail().fallback().contains(alpha.nation()));
        }
        var self = inbox.rows().stream().filter(row -> row.title().fallback().equals("ScopedSelf")).toList();
        assertEquals(2, self.size());
        assertNotEquals(self.get(0).entity().id(), self.get(1).entity().id());
        assertEquals(inbox.rows(), service.officialMailbox(alice, alpha.nation(), "Scoped", 0).rows());
        assertEquals(1, service.officialMailbox(operator, beta.nation(), "Scoped", 0).rows().size());
    }

    @Test
    void mailboxAndPrivateMessageReferencesRecheckCurrentAuthorityForEveryAccess() {
        Tree alpha = tree(alice, "Alpha");
        join(cara, alpha);
        join(dave, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        engine.governmentMail(alpha.nation(), "Memo", "Private");
        engine.governmentMail(alpha.state(), "StateMemo", "Private");
        engine.governmentMail(alpha.city(), "CityMemo", "Private");
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
            for (Actor official : List.of(alice, cara, operator)) {
                assertFalse(service.officialMailbox(official, id, "", 0).rows().isEmpty());
            }
            for (Actor visitor : List.of(bob, dave, operator(operator, false), actor(900, "Visitor", false))) {
                assertThrows(UserError.class, () -> service.officialMailbox(visitor, id, "", 0));
            }
        }
        String privateReference = service.officialMailbox(cara, alpha.nation(), "Memo", 0).rows().get(0).entity().id();
        assertThrows(UserError.class, () -> GovernancePresentation.mail(engine, dave, privateReference));
        run(alice, "government officer " + alpha.nation() + " Cara remove");
        assertThrows(UserError.class, () -> service.officialMailbox(cara, alpha.nation(), "", 0));
        assertThrows(UserError.class, () -> service.officialMailbox(cara, alpha.city(), "", 0));
        assertThrows(UserError.class, () -> GovernancePresentation.mail(engine, cara, privateReference));
    }

    @Test
    void directoryPagesAndFiltersHeadersWithoutReadingBodiesOrMutatingAnyMail() {
        Tree alpha = tree(alice, "Alpha");
        for (int i = 0; i < 25; i++) {
            time.incrementAndGet();
            engine.governmentMail(alpha.nation(), "Memo" + String.format(java.util.Locale.ROOT, "%02d", i), "SECRET_BODY_" + i);
        }
        AtomicInteger dirty = new AtomicInteger();
        engine = new GovernanceEngine(data, config, EconomyAccess.UNAVAILABLE, time::get, dirty::incrementAndGet);
        dirty.set(0);
        String before = new Gson().toJson(data);
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        UiView first = service.officialMailbox(alice, alpha.nation(), "Memo", 0);
        UiView last = service.officialMailbox(alice, alpha.nation(), "Memo", 20);
        assertEquals(20, first.rows().size());
        assertEquals("Memo24", first.rows().get(0).title().fallback());
        assertTrue(first.more());
        assertEquals(5, last.rows().size());
        assertEquals(20, last.offset());
        assertFalse(last.more());
        assertEquals(last.rows(), service.officialMailbox(alice, alpha.nation(), "Memo", 100_000).rows());
        assertEquals("Memo03", service.officialMailbox(alice, alpha.nation(), "  memo03  ", 0).rows().get(0).title().fallback());
        assertTrue(service.officialMailbox(alice, alpha.nation(), "SECRET_BODY", 0).rows().isEmpty());
        assertFalse(first.toString().contains("SECRET_BODY"));
        assertEquals(before, new Gson().toJson(data));
        assertEquals(0, dirty.get());
        assertTrue(data.governments.get(alpha.nation()).inbox.stream().noneMatch(mail -> mail.read));
    }

    @Test
    void everyMailboxActionIsSeededWithOnlyTheSelectedGovernment() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(bob, "Beta");
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
            UiView mailbox = service.officialMailbox(alice, id, "", 0);
            assertEquals(6, mailbox.actions().size());
            assertEquals(List.of("mail official inbox <government> <page>", "mail official sent <government> <page>",
                    "mail official read <government> <message>", "mail official send <government> <recipient> <subject> <body>",
                    "mail official reply <government> <message> <body>", "mail official delete <government> <message>"),
                    mailbox.actions().stream().map(action -> action.template()).toList());
            for (var action : mailbox.actions()) {
                assertEquals("statecraft:official_mail", action.page());
                assertEquals(id, action.values().get("government"));
                assertFalse(action.values().containsValue(beta.nation()));
                assertEquals(action.template(), action.selection().registeredAction().command());
                assertTrue(action.enabled());
            }
        }
    }

    @Test
    void forgedMailboxRequestsAndMisfiledCopiesDoNotExposeOtherMailboxes() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(bob, "Beta");
        String company = company(alice, "Builders");
        engine.governmentMail(beta.nation(), "Foreign", "Do not disclose");
        data.governments.get(alpha.nation()).inbox.add(data.governments.get(beta.nation()).inbox.get(0));
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        assertTrue(service.officialMailbox(alice, alpha.nation(), "Foreign", 0).rows().isEmpty());
        assertThrows(UserError.class, () -> service.officialMailbox(alice, "AlphaNation", "", 0));
        assertThrows(UserError.class, () -> service.officialMailbox(alice, company, "", 0));
        assertThrows(UserError.class, () -> service.officialMailbox(alice, alpha.nation(), "x".repeat(81), 0));
        assertThrows(UserError.class, () -> service.officialMailbox(alice, alpha.nation(), "", -1));
        assertThrows(UserError.class, () -> service.officialMailbox(alice, alpha.nation(), "", 100_001));
    }
}
