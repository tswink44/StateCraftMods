package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PersonalDashboardService {
    private final GovernanceAccess governance;

    public PersonalDashboardService(GovernanceAccess governance) {
        this.governance = governance;
    }

    public PersonalDashboard describe(Actor actor, PersonalDashboard.Request request, boolean economyAvailable,
                                      PersonalDashboard.Page accounts, UiText notice) {
        Map<String, GovernmentView> governments = new HashMap<>();
        Map<Kind, GovernmentView> citizenship = new EnumMap<>(Kind.class);
        for (GovernmentView government : governance.governments()) {
            governments.put(government.id(), government);
            if (government.members().contains(actor.id())
                    && citizenship.putIfAbsent(government.kind(), government) != null) {
                throw new UserError("Your citizenship records need an operator's attention.");
            }
        }
        List<PersonalDashboard.Entry> companies = governance.companies().stream()
                .filter(company -> company.shares().getOrDefault(actor.id(), 0L) > 0)
                .sorted(java.util.Comparator.comparing((GovernanceAccess.CompanyView company) -> company.name().toLowerCase(Locale.ROOT))
                        .thenComparing(GovernanceAccess.CompanyView::id))
                .map(company -> new PersonalDashboard.Entry(UiText.literal(company.name()),
                        text("shares", "%s shares", "" + company.shares().get(actor.id())),
                        UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.COMPANY, company.id())))).toList();

        Map<String, Long> ownedCities = new HashMap<>();
        governance.claims().stream().filter(claim -> actor.account().equals(claim.ownerAccount()))
                .forEach(claim -> ownedCities.merge(claim.cityId() != null ? claim.cityId()
                        : claim.stateId() != null ? claim.stateId() : claim.nationId(), 1L, Long::sum));
        List<PersonalDashboard.Entry> cities = ownedCities.entrySet().stream()
                .map(entry -> {
                    GovernmentView city = governments.get(entry.getKey());
                    if (city == null) {
                        throw new UserError("An owned property's government needs an operator's attention.");
                    }
                    String location = city.kind() == Kind.CITY ? cityName(city, governments)
                            : city.kind() == Kind.STATE ? "Outside a city (" + city.name() + "/"
                                    + nationName(city.nationId(), governments) + ")"
                            : "Outside a state/city (" + city.name() + ")";
                    return new PersonalDashboard.Entry(UiText.literal(location),
                            entry.getValue() == 1 ? text("one_property", "1 owned chunk")
                                    : text("properties", "%s owned chunks", "" + entry.getValue()),
                            governmentTarget(city));
                }).sorted(java.util.Comparator.comparing((PersonalDashboard.Entry entry) -> entry.name().fallback().toLowerCase(Locale.ROOT))
                        .thenComparing(entry -> entry.target().entity().id())).toList();

        return new PersonalDashboard(actor.name(), citizenship(citizenship.get(Kind.NATION), governments),
                citizenship(citizenship.get(Kind.STATE), governments), citizenship(citizenship.get(Kind.CITY), governments),
                economyAvailable, accounts, PersonalDashboard.Page.of(companies, request.companies()),
                PersonalDashboard.Page.of(cities, request.propertyCities()), notice);
    }

    private static PersonalDashboard.Entry citizenship(GovernmentView government, Map<String, GovernmentView> governments) {
        return government == null ? null : new PersonalDashboard.Entry(
                UiText.literal(government.kind() == Kind.CITY ? cityName(government, governments) : government.name()),
                UiText.EMPTY, governmentTarget(government));
    }

    static String cityName(GovernmentView city, Map<String, GovernmentView> governments) {
        GovernmentView state = governments.get(city.parentId());
        GovernmentView nation = governments.get(city.nationId());
        String stateName = state == null ? "Former state" : DisplayText.name(state.name(), "Former state");
        String nationName = nation == null ? "Former nation" : DisplayText.name(nation.name(), "Former nation");
        return "[" + DisplayText.name(city.name(), "Former city") + "] (" + stateName + "/" + nationName + ")";
    }

    private static String nationName(String id, Map<String, GovernmentView> governments) {
        GovernmentView nation = governments.get(id);
        return nation == null ? "Former nation" : DisplayText.name(nation.name(), "Former nation");
    }

    private static UiQuery governmentTarget(GovernmentView government) {
        return UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, government.id()));
    }

    private static UiText text(String key, String template, String... arguments) {
        return UiText.tr("ui.statecraft.dashboard." + key, String.format(Locale.ROOT, template, (Object[]) arguments), arguments);
    }
}
