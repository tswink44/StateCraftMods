package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.GovernanceAccess.ClaimView;
import dev.statecraft.api.GovernanceAccess.CompanyView;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiText;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalDashboardServiceTest {
    private final Actor actor = new Actor(id(1), "Alice", false, "minecraft:overworld", 0, 0);
    private final Map<String, GovernmentView> governments = new LinkedHashMap<>();
    private final List<CompanyView> companies = new ArrayList<>();
    private final List<ClaimView> claims = new ArrayList<>();
    private final GovernanceAccess governance = (GovernanceAccess) Proxy.newProxyInstance(
            GovernanceAccess.class.getClassLoader(), new Class<?>[]{GovernanceAccess.class}, (proxy, method, args) -> switch (method.getName()) {
                case "governments" -> List.copyOf(governments.values());
                case "companies" -> List.copyOf(companies);
                case "claims" -> List.copyOf(claims);
                default -> throw new AssertionError("Unexpected dashboard lookup or mutation: " + method.getName());
            });

    @Test
    void citizenshipLinksUseActualMembershipAndTheRequestedCityLabel() {
        hierarchy(true);
        PersonalDashboard dashboard = build(PersonalDashboard.Request.FIRST);
        assertEquals("Alice", dashboard.name());
        assertEquals("Arcadia", dashboard.nation().name().fallback());
        assertEquals("Westhaven", dashboard.state().name().fallback());
        assertEquals("[Oakvale] (Westhaven/Arcadia)", dashboard.city().name().fallback());
        assertEquals("statecraft:detail", dashboard.city().target().page());
        assertEquals(EntityRef.Kind.GOVERNMENT, dashboard.city().target().entity().kind());
        assertEquals(id(12).toString(), dashboard.city().target().entity().id());
        assertFalse(dashboard.city().name().fallback().contains(id(12).toString()));
    }

    @Test
    void shareholdingsIncludeNonEmployeeInvestmentsButNotMereCompanyMembership() {
        hierarchy(false);
        companies.add(new CompanyView(id(20).toString(), "Owned Shares", id(2), Set.of(id(2)), Map.of(actor.id(), 7L)));
        companies.add(new CompanyView(id(21).toString(), "Employer Only", id(2), Set.of(actor.id()), Map.of(id(2), 100L)));
        companies.add(new CompanyView(id(22).toString(), "Zero Shares", actor.id(), Set.of(actor.id()), Map.of(actor.id(), 0L)));
        PersonalDashboard dashboard = build(PersonalDashboard.Request.FIRST);
        assertEquals(1, dashboard.companies().total());
        var holding = dashboard.companies().entries().get(0);
        assertEquals("Owned Shares", holding.name().fallback());
        assertEquals("7 shares", holding.detail().fallback());
        assertEquals(EntityRef.Kind.COMPANY, holding.target().entity().kind());
        assertEquals(id(20).toString(), holding.target().entity().id());
        assertNull(dashboard.nation());
        assertNull(dashboard.city());
    }

    @Test
    void propertyCitiesAreDistinctAndExcludePublicAndOtherPrivateOwners() {
        hierarchy(true);
        claim(0, actor.account(), id(12).toString(), id(11).toString());
        claim(1, actor.account(), id(12).toString(), id(11).toString());
        claim(2, "city:" + id(12), id(12).toString(), id(11).toString());
        claim(3, "player:" + id(2), id(12).toString(), id(11).toString());
        PersonalDashboard dashboard = build(PersonalDashboard.Request.FIRST);
        assertEquals(1, dashboard.propertyCities().total());
        assertEquals("2 owned chunks", dashboard.propertyCities().entries().get(0).detail().fallback());
        assertEquals("[Oakvale] (Westhaven/Arcadia)", dashboard.propertyCities().entries().get(0).name().fallback());
    }

    @Test
    void privatelyOwnedUnassignedLandLinksToItsActualGoverningNationOrState() {
        hierarchy(false);
        claim(0, actor.account(), null, null);
        claim(1, actor.account(), null, id(11).toString());
        PersonalDashboard dashboard = build(PersonalDashboard.Request.FIRST);
        assertEquals(2, dashboard.propertyCities().total());
        Set<String> targets = dashboard.propertyCities().entries().stream().map(entry -> entry.target().entity().id())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(id(10).toString(), id(11).toString()), targets);
        assertTrue(dashboard.propertyCities().entries().stream().allMatch(entry -> entry.name().fallback().startsWith("Outside a")));
    }

    @Test
    void sectionsPageIndependentlyWithoutDroppingCitizenshipOrChangingOwnership() {
        hierarchy(true);
        for (int i = 0; i < 25; i++) companies.add(new CompanyView(id(100 + i).toString(),
                String.format(java.util.Locale.ROOT, "Company %02d", i), id(2), Set.of(), Map.of(actor.id(), 1L)));
        PersonalDashboard first = build(PersonalDashboard.Request.FIRST);
        PersonalDashboard second = build(PersonalDashboard.Request.FIRST.page(PersonalDashboard.Section.COMPANIES, 12));
        assertEquals(12, first.companies().entries().size());
        assertTrue(first.companies().more());
        assertEquals("Company 12", second.companies().entries().get(0).name().fallback());
        assertEquals(first.city(), second.city());
        assertEquals(0, second.propertyCities().offset());
        assertEquals(25, companies.size());
        assertFalse(first.economyAvailable());
        assertTrue(first.accounts().entries().isEmpty());
    }

    private PersonalDashboard build(PersonalDashboard.Request request) {
        return new PersonalDashboardService(governance).describe(actor, request, false, PersonalDashboard.Page.EMPTY, UiText.EMPTY);
    }

    private void hierarchy(boolean citizen) {
        Set<UUID> members = citizen ? Set.of(actor.id()) : Set.of();
        String nation = id(10).toString(), state = id(11).toString(), city = id(12).toString();
        governments.put(nation, new GovernmentView(nation, Kind.NATION, "Arcadia", null, nation, id(2), members, Map.of()));
        governments.put(state, new GovernmentView(state, Kind.STATE, "Westhaven", nation, nation, id(2), members, Map.of()));
        governments.put(city, new GovernmentView(city, Kind.CITY, "Oakvale", state, nation, id(2), members, Map.of()));
    }

    private void claim(int x, String owner, String city, String state) {
        claims.add(new ClaimView("minecraft:overworld|" + x + "|0", "minecraft:overworld", x, 0, city, state,
                id(10).toString(), owner, 0, 0));
    }

    private static UUID id(long value) { return new UUID(0, value); }
}
