package dev.statecraft.client.state;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.ui.ClaimMapMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClaimMapSelection {
    public static final int LIMIT = 64;
    public enum Eligibility { ALLOWED, UNKNOWN, OUTSIDE_SCOPE }
    public enum Change { ADDED, REMOVED, UNKNOWN, OUTSIDE_SCOPE, LIMIT_REACHED }

    private final ClaimMapMode mode;
    private final String government;
    private final Set<ChunkKey> selected = new LinkedHashSet<>();

    public ClaimMapSelection(ClaimMapMode mode, String government) {
        this.mode = java.util.Objects.requireNonNull(mode);
        this.government = java.util.Objects.requireNonNull(government);
    }

    public int size() { return selected.size(); }
    public boolean contains(ChunkKey chunk) { return selected.contains(chunk); }
    public Set<ChunkKey> chunks() { return Set.copyOf(selected); }
    public void clear() { selected.clear(); }

    public boolean retainDimension(String dimension) {
        return selected.removeIf(chunk -> !chunk.dimension().equals(dimension));
    }

    public Change toggle(TerritorySnapshot snapshot, ChunkKey chunk) {
        if (selected.remove(chunk)) return Change.REMOVED;
        Eligibility eligible = eligibility(snapshot, chunk);
        if (eligible == Eligibility.UNKNOWN) return Change.UNKNOWN;
        if (eligible == Eligibility.OUTSIDE_SCOPE || selected.stream()
                .anyMatch(existing -> !existing.dimension().equals(chunk.dimension()))) return Change.OUTSIDE_SCOPE;
        if (selected.size() == LIMIT) return Change.LIMIT_REACHED;
        selected.add(chunk);
        return Change.ADDED;
    }

    public Eligibility eligibility(TerritorySnapshot snapshot, ChunkKey chunk) {
        if (!covered(snapshot, chunk)) return Eligibility.UNKNOWN;
        return inScope(mode, government, territory(snapshot, chunk))
                ? Eligibility.ALLOWED : Eligibility.OUTSIDE_SCOPE;
    }

    public Eligibility selectionEligibility(TerritorySnapshot snapshot) {
        for (ChunkKey chunk : selected) {
            Eligibility result = eligibility(snapshot, chunk);
            if (result != Eligibility.ALLOWED) return result;
        }
        return Eligibility.ALLOWED;
    }

    public Map<String, String> values(String target) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(mode.governmentField(), government);
        values.put("chunks", selected.isEmpty() ? "here" : selected.stream()
                .sorted(Comparator.comparing(ChunkKey::dimension).thenComparingInt(ChunkKey::x).thenComparingInt(ChunkKey::z))
                .map(ChunkKey::toString).collect(Collectors.joining(",")));
        if (!mode.targetField().isEmpty() && target != null && !target.isBlank()) values.put(mode.targetField(), target);
        return Map.copyOf(values);
    }

    public Map<String, String> validatedValues(String target) {
        Map<String, String> values = values(target);
        FormContext.validateValues(mode.template(), values);
        CommandTemplate template = new CommandTemplate(mode.template());
        // Metadata may omit the target; a complete selection must also fit after command quoting and framing.
        if (template.fields().stream().allMatch(values::containsKey)) template.render(values);
        return values;
    }

    public static boolean covered(TerritorySnapshot snapshot, ChunkKey chunk) {
        return snapshot != null && snapshot.radius() > 0 && snapshot.dimension().equals(chunk.dimension())
                && Math.abs((long) chunk.x() - snapshot.centerX()) <= snapshot.radius()
                && Math.abs((long) chunk.z() - snapshot.centerZ()) <= snapshot.radius();
    }

    public static TerritorySnapshot.Territory territory(TerritorySnapshot snapshot, ChunkKey chunk) {
        if (!covered(snapshot, chunk)) return null;
        return snapshot.territories().stream().filter(value -> value.x() == chunk.x() && value.z() == chunk.z())
                .findFirst().orElse(null);
    }

    public static boolean inScope(ClaimMapMode mode, String government, TerritorySnapshot.Territory territory) {
        if (government == null || government.isBlank()) return false;
        return switch (mode) {
            case CLAIM -> territory == null;
            case ASSIGN_STATE -> territory != null && government.equals(territory.nationId());
            case ASSIGN_CITY -> territory != null && government.equals(territory.stateId());
        };
    }
}
