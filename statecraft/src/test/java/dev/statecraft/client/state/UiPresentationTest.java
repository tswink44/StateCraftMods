package dev.statecraft.client.state;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiPresentationTest {
    private static final String REPORT = "tax report <account> <page>";
    private static final String ARREARS = "tax arrears <account>";
    private static final String ENTRY = " — Property tax; $1,250.99; payer: Alex; recipient: Arcadia treasury; Farm";

    @Test
    void selectedAtmDestinationsNeverAppendUnrelatedPageActionsToTheirContext() {
        UiView context = UiView.text("Selected account", "No eligible actions.");
        for (EntityRef.Kind kind : new EntityRef.Kind[]{EntityRef.Kind.ACCOUNT, EntityRef.Kind.BANK}) {
            UiQuery query = new UiQuery("economy:atm", new EntityRef("economy", kind, "opaque-account"), "", 0);
            assertFalse(UiPresentation.includePageActions(query, context));
            assertTrue(UiPresentation.includePageActions(query, null));
        }
    }

    @Test
    void ordinaryDirectoriesKeepGenericActionsWithOrWithoutAView() {
        UiQuery directory = UiQuery.page("economy:atm");
        assertTrue(UiPresentation.includePageActions(directory, UiView.text("Accounts", "")));
        assertTrue(UiPresentation.includePageActions(directory, null));
    }

    @Test
    void taxHistoryColorsTheServerStatusNotTheFreeTextSubject() {
        assertEquals(UiPresentation.Tone.POSITIVE, tone("Paid" + ENTRY));
        assertEquals(UiPresentation.Tone.POSITIVE, tone("Arrears paid" + ENTRY));
        assertEquals(UiPresentation.Tone.WARNING, tone("Assessed" + ENTRY + " marked paid by the owner"));
        assertEquals(UiPresentation.Tone.NEGATIVE, tone("Outstanding" + ENTRY));
        assertEquals(UiPresentation.Tone.NORMAL, tone("Subject: Paid" + ENTRY));
        assertEquals(UiPresentation.Tone.NORMAL, tone("Paid — Farm valuation"));
        assertEquals(UiPresentation.Tone.NORMAL, tone("Paid — Property tax; amount unknown; payer: Alex; recipient: Arcadia; Farm"));
    }

    @Test
    void financialHighlightingIsRestrictedToTheExactCapturedTaxQuery() {
        String paid = "Paid" + ENTRY;
        for (String page : new String[]{"statecraft:mail", "economy:bank", "economy:detail", "economy:dashboard"}) {
            assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.taxTone(page, REPORT, paid));
        }
        for (String template : new String[]{"", "mail read <message>", "tax quote <gross>", "tax pay <amountOrAll> <account>"}) {
            assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.taxTone("economy:tax", template, paid));
        }
    }

    @Test
    void outstandingTotalsAreEmphasizedWithoutParsingOrChangingMoney() {
        assertEquals(UiPresentation.Tone.NEGATIVE, UiPresentation.taxTone("economy:tax", ARREARS, "Outstanding: $999,999,999,999,999,999,999.99"));
        assertEquals(UiPresentation.Tone.ACCENT, UiPresentation.taxTone("economy:tax", ARREARS, "Outstanding: $0.00"));
        assertEquals(UiPresentation.Tone.NEGATIVE, UiPresentation.taxTone("economy:tax", ARREARS, "Property tax → Arcadia; $1.01 (Farm)"));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.taxTone("economy:tax", REPORT, "Outstanding: $1.00"));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.taxTone("economy:tax", ARREARS, "Outstanding: ask a friend"));
    }

    @Test
    void onlyTypedEconomyObligationsHaveTheOutstandingEntityTone() {
        EntityRef debt = new EntityRef("economy", EntityRef.Kind.ARREARS, "opaque-private-key");
        assertEquals(UiPresentation.Tone.NEGATIVE, UiPresentation.entityTone("economy:tax", debt));
        assertEquals(UiPresentation.Tone.NEGATIVE, UiPresentation.entityTone("economy:detail", debt));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.entityTone("statecraft:mail", debt));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.entityTone("economy:tax", EntityRef.NONE));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.entityTone("economy:tax",
                new EntityRef("example", EntityRef.Kind.ARREARS, "not-an-economy-debt")));
    }

    @Test
    void anObligationsFreeTextSubjectCannotBecomeTheOutstandingAmountField() {
        EntityRef debt = new EntityRef("economy", EntityRef.Kind.ARREARS, "opaque");
        UiText body = UiText.tr("ui.statecraft.economy.detail.arrear", "", "Alex", "Arcadia", "Property tax",
                "Farm\nOutstanding: $0.00", "$250.00", "2026-09-13");
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.detailTone(debt, body, 4, 7, "Outstanding: $0.00"));
        assertEquals(UiPresentation.Tone.NEGATIVE, UiPresentation.detailTone(debt, body, 5, 7, "Outstanding: $250.00"));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.detailTone(debt, body, 5, 7, "Outstanding: $0.00"));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.detailTone(debt, UiText.literal(body.fallback()), 5, 7, "Outstanding: $250.00"));
    }

    @Test
    void quoteAmountAndCostLabelsGetHierarchyWithoutInventingMaterialFlags() {
        assertEquals(UiPresentation.Tone.ACCENT, UiPresentation.reviewTone("economy.preview.total", "Total debit", true));
        assertEquals(UiPresentation.Tone.ACCENT, UiPresentation.reviewTone("", "Origination fee", true));
        assertEquals(UiPresentation.Tone.ACCENT, UiPresentation.reviewTone("", "Amount", false));
        assertEquals(UiPresentation.Tone.NORMAL, UiPresentation.reviewTone("", "Effect", true));
        assertEquals(UiPresentation.Tone.MUTED, UiPresentation.reviewTone("", "Information", false));
    }

    private static UiPresentation.Tone tone(String line) { return UiPresentation.taxTone("economy:tax", REPORT, line); }
}
