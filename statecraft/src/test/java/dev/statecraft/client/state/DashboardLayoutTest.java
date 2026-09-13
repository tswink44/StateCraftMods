package dev.statecraft.client.state;

import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardLayoutTest {
    @Test
    void cardsStayWithinTheViewportAndRowsDoNotOverlapAtSupportedWidths() {
        for (int width : List.of(320, 480, 620, 960, 1600)) {
            var boxes = DashboardLayout.cards(width, 0, List.of(44, 70, 50, 44, 110));
            assertEquals(5, boxes.size());
            for (var box : boxes) {
                assertTrue(box.x() >= 16);
                assertTrue(box.x() + box.width() <= width - 16);
                assertTrue(box.height() >= 44);
            }
            int columns = DashboardLayout.columns(width);
            for (int i = columns; i < boxes.size(); i++) {
                var previous = boxes.get(i - columns);
                assertTrue(boxes.get(i).y() >= previous.y() + previous.height() + DashboardLayout.GAP);
            }
        }
    }

    @Test
    void dashboardNavigationRetainsIndependentPagesAcrossBackCopies() {
        ViewState state = new ViewState(UiQuery.page("statecraft:dashboard"));
        state.dashboardRequest(new PersonalDashboard.Request(12, 24, 36));
        state.scroll(120);
        ViewState restored = state.copy();
        assertTrue(restored.isPersonalDashboard());
        assertEquals(new PersonalDashboard.Request(12, 24, 36), restored.dashboardRequest());
        assertEquals(120, restored.scroll());
        assertFalse(restored.advancedOpen());
    }
}
