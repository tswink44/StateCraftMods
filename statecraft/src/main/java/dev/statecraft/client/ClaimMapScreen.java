package dev.statecraft.client;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.form.FormValidation;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.ClaimMapMode;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.client.state.ClaimMapLayout;
import dev.statecraft.client.state.ClaimMapSelection;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.network.SuiteNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class ClaimMapScreen extends Screen {
    private static final String PAGE = "statecraft:claims";
    private static final Pattern UUID_TEXT = Pattern.compile("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private final ManagementScreen origin;
    private final String governmentId;
    private final ClaimMapMode mode;
    private final UiScope scope;
    private final ClaimMapSelection selection;
    private final Map<Long, TerritorySnapshot.Territory> territories = new HashMap<>();
    private TerritorySnapshot snapshot = TerritorySnapshot.EMPTY;
    private ClaimMapLayout layout;
    private TerrainMapTiles terrain;
    private FormSchema schema = FormSchema.EMPTY;
    private String dimension = "";
    private String governmentName = "";
    private String targetValue = "";
    private String targetName = "";
    private String metadataError = "";
    private Component fieldError = Component.empty();
    private Component notice = Component.empty();
    private int centerX;
    private int centerZ;
    private int radius = ClaimMapLayout.MIN_RADIUS;
    private int cursorColumn = radius;
    private int cursorRow = radius;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int eligibleVisible = -1;
    private int pending = -1;
    private long revision;
    private long refreshAt;
    private boolean metadataReady;
    private boolean governmentAllowed;
    private boolean keyboardNavigation;
    private boolean missingOwnership;
    private Button review;
    private Button clear;
    private Button target;
    private Button refresh;
    private Button status;
    private Button zoomIn;
    private Button zoomOut;
    private ChunkKey inspected;
    private TerrainMapTiles.State inspectedTerrain = TerrainMapTiles.State.UNLOADED;

    ClaimMapScreen(ManagementScreen origin, String governmentId, ClaimMapMode mode) {
        super(mapTitle(mode));
        this.origin = origin;
        this.governmentId = java.util.Objects.requireNonNull(governmentId);
        this.mode = java.util.Objects.requireNonNull(mode);
        this.scope = ClientHooks.scope();
        this.selection = new ClaimMapSelection(mode, governmentId);
    }

    private static Component mapTitle(ClaimMapMode mode) {
        return switch (mode) {
            case CLAIM -> text("title_claim", "Claim chunks");
            case ASSIGN_STATE -> text("title_state", "Assign territory to states");
            case ASSIGN_CITY -> text("title_city", "Assign territory to cities");
        };
    }

    private static Component text(String key, String fallback, Object... arguments) {
        return ClientText.tr("gui.statecraft.claimmap." + key, fallback, arguments);
    }

    @Override
    protected void init() {
        followPlayer();
        refreshSnapshot();
        if (terrain == null) terrain = new TerrainMapTiles();
        layout = ClaimMapLayout.fit(width, height, radius);
        zoomIn = button(text("zoom_in", "+"), ignored -> zoom(-1), layout.zoomIn());
        zoomOut = button(text("zoom_out", "−"), ignored -> zoom(1), layout.zoomOut());
        zoomIn.setTooltip(Tooltip.create(text("zoom_in_hint", "Show fewer, larger chunks. The map follows you.")));
        zoomOut.setTooltip(Tooltip.create(text("zoom_out_hint", "Show more surrounding chunks, up to eight in each direction.")));
        target = button(Component.empty(), ignored -> {
            if (target.active) minecraft.setScreen(new TargetPicker(this));
        }, layout.target());
        refresh = button(text("refresh", "Refresh"), ignored -> {
            notice = Component.empty();
            request(FormQuery.INITIAL);
        }, layout.refresh());
        refresh.setTooltip(Tooltip.create(text("refresh_hint", "Reload server permissions and eligible targets. Nothing is submitted.")));
        status = button(Component.empty(), ignored -> minecraft.setScreen(new InformationScreen(this,
                text("details", "Map status & details"), details())), layout.status());
        review = button(text("review", "Review selection"), ignored -> review(), layout.review());
        clear = button(text("clear", "Clear"), ignored -> {
            selection.clear();
            selectionChanged();
        }, layout.clear());
        button(text("back", "Back"), ignored -> onClose(), layout.back());
        controls();
        if (available() && !metadataReady && pending < 0) request(FormQuery.INITIAL);
    }

    private Button button(Component label, Button.OnPress press, ClaimMapLayout.Rect bounds) {
        return addRenderableWidget(UiButton.create(label, press)
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
    }

    private boolean available() {
        return minecraft != null && minecraft.player != null && minecraft.level != null && ClientHooks.current(scope);
    }

    private void followPlayer() {
        if (!available()) {
            if (terrain != null) {
                terrain.close();
                terrain = null;
            }
            if (pending >= 0 || refreshAt != 0) invalidateMetadata();
            return;
        }
        String current = minecraft.level.dimension().location().toString();
        int x = ClaimMapLayout.chunkCoordinate(minecraft.player.getX());
        int z = ClaimMapLayout.chunkCoordinate(minecraft.player.getZ());
        if (!dimension.equals(current)) {
            boolean cleared = selection.retainDimension(current);
            dimension = current;
            inspected = null;
            cursorColumn = radius;
            cursorRow = radius;
            invalidateMetadata();
            if (cleared) notice = text("dimension_changed", "Dimension changed; the previous chunk selection was cleared.");
            refreshAt = ClientHooks.time() + 200;
        } else if (x != centerX || z != centerZ) {
            cursorColumn = radius;
            cursorRow = radius;
        }
        centerX = x;
        centerZ = z;
    }

    private void refreshSnapshot() {
        TerritorySnapshot replacement = ClientHooks.territory();
        if (replacement == snapshot) return;
        snapshot = replacement;
        territories.clear();
        for (var territory : snapshot.territories()) territories.put(key(territory.x(), territory.z()), territory);
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    private TerritorySnapshot.Territory territory(ChunkKey chunk) {
        return ClaimMapSelection.covered(snapshot, chunk) ? territories.get(key(chunk.x(), chunk.z())) : null;
    }

    private void zoom(int amount) {
        radius = Math.max(ClaimMapLayout.MIN_RADIUS, Math.min(ClaimMapLayout.MAX_RADIUS, radius + amount));
        cursorColumn = radius;
        cursorRow = radius;
        rebuildWidgets();
    }

    private void invalidateMetadata() {
        ClientHooks.forgetForm(pending);
        pending = -1;
        refreshAt = 0;
        revision++;
        metadataReady = false;
        metadataError = "";
        fieldError = Component.empty();
    }

    private void selectionChanged() {
        notice = Component.empty();
        invalidateMetadata();
        refreshAt = ClientHooks.time() + 200;
        controls();
    }

    private void request(FormQuery query) {
        invalidateMetadata();
        if (!available()) return;
        Map<String, String> requested;
        try {
            requested = selection.validatedValues(targetValue);
        } catch (UserError | IllegalArgumentException invalid) {
            metadataError = text("batch_too_long",
                    "This batch exceeds the server's command limit. Select fewer chunks; atomic batches are never split.").getString();
            controls();
            return;
        }
        long requestedRevision = revision;
        try {
            pending = ClientHooks.requestForm(PAGE, mode.template(), requested, query,
                    response -> receive(response, requestedRevision, requested), () -> {
                        if (requestedRevision != revision) return;
                        pending = -1;
                        metadataError = text("timeout", "Server choices did not arrive. Refresh to retry; nothing was submitted.").getString();
                        controls();
                    });
        } catch (RuntimeException failure) {
            metadataError = text("request_failed", "Could not request server choices. Reconnect or refresh; nothing was submitted.").getString();
        }
        controls();
    }

    private void receive(SuiteNetwork.FormResponse response, long requestedRevision, Map<String, String> requested) {
        if (!available() || response.id() != pending || requestedRevision != revision
                || !PAGE.equals(response.page()) || !mode.template().equals(response.command())) return;
        pending = -1;
        if (!response.success()) {
            metadataError = safe(response.error(), text("metadata_unavailable", "This action is unavailable on the server.").getString());
            controls();
            return;
        }
        schema = response.schema();
        FormField government = schema.field(mode.governmentField()).orElse(null);
        FormField chunks = schema.field("chunks").orElse(null);
        governmentAllowed = government != null && governmentId.equals(government.value()) && !government.selectedLabel().isBlank();
        if (!governmentAllowed) {
            metadataError = text("permission", "You cannot manage this government, or it is no longer available.").getString();
            controls();
            return;
        }
        governmentName = safe(government.selectedLabel(), governmentLabel().getString());
        if (chunks == null || !requested.get("chunks").equals(chunks.value())
                || mode != ClaimMapMode.CLAIM && (targetField() == null || targetField().kind() != FormField.Kind.CHOICE)) {
            metadataError = text("schema_changed", "The server's map form is unavailable. Refresh or reopen this map.").getString();
            controls();
            return;
        }
        if (mode != ClaimMapMode.CLAIM) {
            FormField field = targetField();
            targetValue = field.selectedLabel().isBlank() ? "" : field.value();
            targetName = targetValue.isBlank() ? "" : safe(field.selectedLabel(), targetLabel().getString());
        }
        try {
            fieldError = FormValidation.errors(schema, selection.validatedValues(targetValue)).values().stream().findFirst()
                    .map(value -> Component.literal(safe(ClientText.of(value).getString(),
                            text("invalid_selection", "Check the selected chunks and target.").getString())))
                    .orElse(Component.empty());
            metadataReady = true;
        } catch (UserError | IllegalArgumentException invalid) {
            metadataError = text("batch_too_long",
                    "This batch exceeds the server's command limit. Select fewer chunks; atomic batches are never split.").getString();
        }
        controls();
    }

    private FormField targetField() { return schema.field(mode.targetField()).orElse(null); }

    private boolean properTarget() {
        FormField field = targetField();
        return mode == ClaimMapMode.CLAIM || field != null && !targetValue.isBlank()
                && targetValue.equals(field.value()) && !field.selectedLabel().isBlank();
    }

    private boolean ready() {
        return available() && metadataReady && governmentAllowed && pending < 0 && refreshAt == 0
                && selection.size() > 0 && selection.selectionEligibility(snapshot) == ClaimMapSelection.Eligibility.ALLOWED
                && properTarget() && metadataError.isBlank() && fieldError.getString().isBlank()
                && ClientHooks.submissionProblem(mode.template()).fallback().isEmpty();
    }

    private Component reason() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return text("offline", "Join a world to view and select terrain.");
        }
        if (!ClientHooks.current(scope)) return text("session_changed", "Session changed. Reopen the map from your government.");
        if (!metadataError.isBlank()) return Component.literal(metadataError);
        if (pending >= 0 || refreshAt != 0) return text("loading", "Checking server permissions and choices…");
        if (!notice.getString().isBlank()) return notice;
        if (!metadataReady) return text("refresh_required", "Refresh server choices before continuing.");
        if (selection.size() == 0) {
            if (eligibleVisible == 0 && missingOwnership) {
                return text("nearby_loading", "Waiting for the server's nearby territory data…");
            }
            if (eligibleVisible == 0) return mode == ClaimMapMode.CLAIM
                    ? text("no_unclaimed", "No unclaimed chunks in view. Move or zoom out.")
                    : text("no_territory", "No eligible government territory in view. Move closer or zoom out.");
            return text("select_hint", "Select chunks, then review. Clicking never submits.");
        }
        var eligibility = selection.selectionEligibility(snapshot);
        if (eligibility == ClaimMapSelection.Eligibility.UNKNOWN) {
            return text("selection_unknown", "Some selections lack current territory data. Move closer or clear them.");
        }
        if (eligibility == ClaimMapSelection.Eligibility.OUTSIDE_SCOPE) {
            return text("selection_changed", "Some selections are no longer eligible. Deselect them or clear the selection.");
        }
        if (!properTarget()) {
            FormField field = targetField();
            return field == null || field.choices().isEmpty() && !field.more()
                    ? text("no_targets", "No eligible targets. Check permissions or create a child government, then refresh.")
                    : text("choose_target", "Choose the receiving government, or Unassigned.");
        }
        if (!fieldError.getString().isBlank()) return fieldError;
        if (!ClientHooks.submissionProblem(mode.template()).fallback().isEmpty()) {
            return Component.literal(safe(ClientText.of(ClientHooks.submissionProblem(mode.template())).getString(),
                    text("review_unavailable", "Server review is currently unavailable.").getString()));
        }
        return text("ready", "Ready for server review. Nothing has been submitted.");
    }

    private Component details() {
        return reason().copy().append("\n\n").append(text("interaction_help",
                "Click chunks to toggle selection (up to 64). Arrow keys move the cursor; Space selects. Use +/− to zoom. The map follows your position."))
                .append("\n\n").append(text("terrain_help",
                        "Tiles show loaded surface blocks, including water. Stripes mean terrain is not loaded or is still being sampled; hover for details."))
                .append("\n\n").append(mode == ClaimMapMode.CLAIM
                        ? text("nation_only", "Only nations claim unclaimed land. Assign states and cities afterward.")
                        : text("ownership_help", "Allocation preserves private owners. Public title follows the receiving government. Unassigned clears the allocation."))
                .append("\n\n").append(text("review_help",
                        "Review checks current permissions, ownership and fees on the server. Back does not submit or cancel an operation already submitted."));
    }

    private void controls() {
        if (review == null) return;
        review.active = ready();
        clear.active = selection.size() > 0;
        refresh.active = available() && pending < 0;
        zoomIn.active = radius > ClaimMapLayout.MIN_RADIUS;
        zoomOut.active = radius < ClaimMapLayout.MAX_RADIUS;
        FormField field = targetField();
        target.active = mode != ClaimMapMode.CLAIM && available() && metadataReady && pending < 0 && refreshAt == 0
                && field != null && (!field.choices().isEmpty() || field.more() || !targetValue.isBlank());
        target.setMessage(mode == ClaimMapMode.CLAIM ? text("claim_scope", "Unclaimed chunks only")
                : targetName.isBlank() ? text("target_prompt", "%s: choose…", targetLabel().getString())
                : text("target_value", "%s: %s ▾", targetLabel().getString(), targetName));
        target.setTooltip(Tooltip.create(mode == ClaimMapMode.CLAIM ? text("nation_only",
                "Only nations claim unclaimed land. Assign states and cities afterward.")
                : target.active ? text("target_hint", "Search server-eligible targets. Unassigned clears this allocation.")
                : reason()));
        Component reason = reason();
        status.setMessage(reason);
        status.setTooltip(Tooltip.create(reason));
        review.setTooltip(Tooltip.create(review.active
                ? text("review_hint", "Review these exact chunks and the chosen target, including current server fees. No action is sent yet.")
                : reason));
    }

    private Component governmentLabel() {
        return mode == ClaimMapMode.ASSIGN_CITY ? text("state", "State") : text("nation", "Nation");
    }

    private Component targetLabel() {
        return mode == ClaimMapMode.ASSIGN_CITY ? text("city", "City") : text("state", "State");
    }

    private void review() {
        followPlayer();
        refreshSnapshot();
        controls();
        if (!review.active) return;
        try {
            var action = ActionSelection.form(PAGE, mode.template(), selection.validatedValues(targetValue));
            minecraft.setScreen(new TransactionReviewScreen(origin, this, action, ActionIntent.MUTATION, null));
        } catch (UserError | IllegalArgumentException | IllegalStateException failure) {
            metadataError = safe(failure.getMessage(), text("review_unavailable", "Server review is currently unavailable.").getString());
            controls();
        }
    }

    private void toggle(ChunkKey chunk) {
        if (!available()) return;
        refreshSnapshot();
        switch (selection.toggle(snapshot, chunk)) {
            case ADDED, REMOVED -> selectionChanged();
            case UNKNOWN -> notice = text("ownership_loading", "Ownership is not loaded here yet. Wait for the server's territory update.");
            case OUTSIDE_SCOPE -> notice = mode == ClaimMapMode.CLAIM
                    ? text("already_claimed", "This land is already claimed. Nations can claim only unclaimed chunks.")
                    : text("outside_scope", "Select land belonging to this government. Other territory cannot be allocated here.");
            case LIMIT_REACHED -> notice = text("selection_limit", "Up to 64 chunks per review. Deselect a chunk before adding another.");
        }
        controls();
    }

    private void pollRequests() {
        followPlayer();
        refreshSnapshot();
        if (refreshAt != 0 && ClientHooks.time() >= refreshAt) request(FormQuery.INITIAL);
    }

    @Override
    public void tick() {
        pollRequests();
        controls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        followPlayer();
        refreshSnapshot();
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 10);
        String name = governmentName.isBlank() ? governmentLabel().getString() : governmentName;
        drawLine(graphics, text("context", "%s · %s/64 selected", name, selection.size()), 10, layout.contextY(), width - 72, UiTheme.MUTED);
        graphics.drawCenteredString(font, text("north", "N ↑"), layout.map().x() + layout.map().width() / 2, layout.northY(), UiTheme.MUTED);
        if (mouseX != lastMouseX || mouseY != lastMouseY) keyboardNavigation = false;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        var pointer = available() && !keyboardNavigation
                ? layout.atPixel(dimension, centerX, centerZ, mouseX, mouseY) : Optional.<ChunkKey>empty();
        inspected = pointer.orElseGet(() -> available()
                ? layout.atCell(dimension, centerX, centerZ, cursorColumn, cursorRow).orElse(null) : null);
        inspectedTerrain = TerrainMapTiles.State.UNLOADED;
        eligibleVisible = 0;
        missingOwnership = false;
        graphics.fill(layout.map().x() - 2, layout.map().y() - 2, layout.map().right() + 2,
                layout.map().bottom() + 2, UiTheme.BORDER);
        if (available()) {
            if (terrain == null) terrain = new TerrainMapTiles();
            terrain.beginFrame(minecraft.level);
            // Sample the player's surroundings first rather than spending the initial frame at a distant corner.
            for (int ring = 0; ring <= radius; ring++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) drawCell(graphics, radius + dx, radius + dz);
                    }
                }
            }
            drawPlayer(graphics);
        } else {
            graphics.fill(layout.map().x(), layout.map().y(), layout.map().right(), layout.map().bottom(), UiTheme.SURFACE);
        }
        Component caption = inspected == null ? text("map_unavailable", "Terrain map unavailable")
                : text("chunk_caption", "Chunk %s, %s · %s", inspected.x(), inspected.z(),
                        inspectedTerrain == TerrainMapTiles.State.READY ? ownershipSummary(inspected) : terrainLabel().getString());
        drawLine(graphics, caption, 10, layout.captionY(), width - 20, UiTheme.MUTED);
        controls();
        super.render(graphics, mouseX, mouseY, delta);
        if (pointer.isPresent()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : describe(pointer.get())) lines.addAll(font.split(line, Math.min(250, width - 24)));
            graphics.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawLine(GuiGraphics graphics, Component line, int x, int y, int maxWidth, int color) {
        String value = line.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
        graphics.drawString(font, value, x, y, color, false);
    }

    private void drawCell(GuiGraphics graphics, int column, int row) {
        int size = layout.cellSize();
        int x = layout.map().x() + column * size;
        int y = layout.map().y() + row * size;
        var cell = layout.atCell(dimension, centerX, centerZ, column, row);
        if (cell.isEmpty()) {
            graphics.fill(x, y, x + size, y + size, UiTheme.BACKGROUND);
            graphics.renderOutline(x, y, size, size, UiTheme.BORDER);
            return;
        }
        ChunkKey chunk = cell.get();
        TerrainMapTiles.State state = terrain.draw(graphics, minecraft.level, chunk.x(), chunk.z(), x, y, size);
        var owner = territory(chunk);
        boolean covered = ClaimMapSelection.covered(snapshot, chunk);
        boolean inScope = ClaimMapSelection.inScope(mode, governmentId, owner);
        if (!covered) missingOwnership = true;
        else if (inScope) eligibleVisible++;
        if (owner != null) {
            int color = owner.color() & 0xFFFFFF;
            graphics.fill(x, y, x + size, y + size, 0x24000000 | color);
            graphics.renderOutline(x, y, size, size, 0xC0000000 | color);
        } else graphics.renderOutline(x, y, size, size, 0x80405562);
        if (!covered || !inScope) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x38000000);
        }
        if (selection.contains(chunk)) {
            graphics.renderOutline(x, y, size, size, UiTheme.WARNING);
            if (size >= 6) {
                int checkX = x + size - 6;
                graphics.fill(checkX, y + 3, checkX + 1, y + 5, UiTheme.TEXT);
                graphics.fill(checkX + 1, y + 4, checkX + 2, y + 5, UiTheme.TEXT);
                for (int i = 0; i < 3; i++) graphics.fill(checkX + 2 + i, y + 3 - i, checkX + 3 + i, y + 4 - i, UiTheme.TEXT);
            }
        }
        if (chunk.equals(inspected)) {
            inspectedTerrain = state;
            graphics.renderOutline(x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2), UiTheme.ACCENT);
        }
    }

    private void drawPlayer(GuiGraphics graphics) {
        double localX = minecraft.player.getX() - centerX * 16.0;
        double localZ = minecraft.player.getZ() - centerZ * 16.0;
        int x = layout.map().x() + radius * layout.cellSize() + (int) (localX / 16.0 * layout.cellSize());
        int y = layout.map().y() + radius * layout.cellSize() + (int) (localZ / 16.0 * layout.cellSize());
        graphics.fill(x - 3, y - 1, x + 4, y + 2, 0xFF101010);
        graphics.fill(x - 1, y - 3, x + 2, y + 4, 0xFF101010);
        graphics.fill(x - 2, y, x + 3, y + 1, UiTheme.TEXT);
        graphics.fill(x, y - 2, x + 1, y + 3, UiTheme.TEXT);
    }

    private String ownershipSummary(ChunkKey chunk) {
        if (!ClaimMapSelection.covered(snapshot, chunk)) return text("ownership_unknown", "Ownership loading").getString();
        var owner = territory(chunk);
        if (owner == null) return text("unclaimed", "Unclaimed").getString();
        return safe(owner.nationName(), text("nation", "Nation").getString()) + " › "
                + assignedName(owner.stateId(), owner.stateName()) + " › " + assignedName(owner.cityId(), owner.cityName());
    }

    private List<Component> describe(ChunkKey chunk) {
        var lines = new ArrayList<Component>();
        lines.add(text("location", "Chunk %s, %s (%s)", chunk.x(), chunk.z(), DisplayText.dimension(chunk.dimension())));
        lines.add(terrainLabel());
        if (!ClaimMapSelection.covered(snapshot, chunk)) lines.add(text("ownership_unknown", "Ownership loading"));
        else {
            var owner = territory(chunk);
            if (owner == null) lines.add(text("unclaimed", "Unclaimed"));
            else {
                lines.add(text("nation_value", "Nation: %s", safe(owner.nationName(), text("nation", "Nation").getString())));
                lines.add(text("state_value", "State: %s", assignedName(owner.stateId(), owner.stateName())));
                lines.add(text("city_value", "City: %s", assignedName(owner.cityId(), owner.cityName())));
                lines.add(text("owner_value", "Owner: %s", safe(owner.ownerName(), text("private_owner", "Private owner").getString())));
            }
        }
        if (chunk.x() == centerX && chunk.z() == centerZ) lines.add(text("you", "You are here"));
        lines.add(selection.contains(chunk) ? text("selected_hint", "Selected ✓ · Click to deselect")
                : selection.eligibility(snapshot, chunk) == ClaimMapSelection.Eligibility.ALLOWED
                    ? text("select_cell", "Click to select for review")
                    : text("ineligible_cell", "Not selectable in this government context"));
        return lines;
    }

    private Component terrainLabel() {
        return switch (inspectedTerrain) {
            case READY -> text("terrain_ready", "Loaded surface terrain");
            case LOADING -> text("terrain_loading", "Sampling loaded terrain…");
            case UNLOADED -> text("terrain_not_loaded", "Terrain not loaded");
            case UNAVAILABLE -> text("terrain_failed", "Terrain unavailable; retrying shortly");
        };
    }

    private static String assignedName(String id, String name) {
        String unassigned = text("unassigned", "Unassigned").getString();
        return id == null || id.isBlank() ? unassigned : safe(name, unassigned);
    }

    private static String safe(String value, String fallback) {
        String name = DisplayText.name(value, fallback);
        return UUID_TEXT.matcher(name).replaceAll(java.util.regex.Matcher.quoteReplacement(
                text("name_unavailable", "Name unavailable").getString()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && layout.map().contains(mouseX, mouseY)) {
            followPlayer();
            if (available()) {
                var chunk = layout.atPixel(dimension, centerX, centerZ, mouseX, mouseY);
                if (chunk.isPresent()) {
                    cursorColumn = chunk.get().x() - centerX + radius;
                    cursorRow = chunk.get().z() - centerZ + radius;
                    keyboardNavigation = false;
                    toggle(chunk.get());
                    setFocused(null);
                } else notice = text("world_edge", "Outside the supported world border.");
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> cursorColumn = Math.max(0, cursorColumn - 1);
            case GLFW.GLFW_KEY_RIGHT -> cursorColumn = Math.min(layout.diameter() - 1, cursorColumn + 1);
            case GLFW.GLFW_KEY_UP -> cursorRow = Math.max(0, cursorRow - 1);
            case GLFW.GLFW_KEY_DOWN -> cursorRow = Math.min(layout.diameter() - 1, cursorRow + 1);
            case GLFW.GLFW_KEY_SPACE -> {
                if (getFocused() != null) return super.keyPressed(keyCode, scanCode, modifiers);
                if (available()) layout.atCell(dimension, centerX, centerZ, cursorColumn, cursorRow).ifPresent(this::toggle);
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoom(-1);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoom(1);
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        keyboardNavigation = true;
        if (keyCode != GLFW.GLFW_KEY_SPACE) setFocused(null);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.map().contains(mouseX, mouseY) && delta != 0) {
            zoom(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public Component getNarrationMessage() {
        return title.copy().append(". ").append(text("selection_count", "%s of 64 chunks selected.", selection.size()))
                .append(" ").append(reason());
    }

    @Override
    public void removed() {
        invalidateMetadata();
        if (terrain != null) {
            terrain.close();
            terrain = null;
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(origin); }
    @Override
    public boolean isPauseScreen() { return false; }

    private static final class TargetPicker extends Screen {
        private final ClaimMapScreen owner;
        private final List<Button> rows = new ArrayList<>();
        private EditBox search;
        private Button previous;
        private Button next;
        private Button refresh;
        private Button status;
        private String query = "";
        private int scroll;
        private long queryAt;
        private boolean initialized;

        private TargetPicker(ClaimMapScreen owner) {
            super(text("picker_title", "Choose %s", owner.targetLabel().getString()));
            this.owner = owner;
        }

        @Override
        protected void init() {
            search = new EditBox(font, 12, 34, width - 24, 20, text("search", "Search targets"));
            search.setMaxLength(80);
            search.setHint(text("search_hint", "Search by name…"));
            search.setValue(query);
            search.setResponder(value -> {
                query = value;
                owner.invalidateMetadata();
                queryAt = ClientHooks.time() + 300;
                updated();
            });
            addRenderableWidget(search);
            rows.clear();
            int visible = Math.max(1, (height - 136) / 24);
            for (int row = 0; row < visible; row++) {
                final int index = row;
                rows.add(addRenderableWidget(UiButton.create(Component.empty(), ignored -> choose(index))
                        .bounds(12, 62 + row * 24, width - 24, 20).build()));
            }
            status = addRenderableWidget(UiButton.create(Component.empty(), ignored -> minecraft.setScreen(
                            new InformationScreen(this, title, statusText())))
                    .bounds(12, height - 70, width - 24, 18).build());
            int buttonWidth = (width - 36) / 4;
            previous = addRenderableWidget(UiButton.create(text("previous", "Previous"),
                            ignored -> request(Math.max(0, field().offset() - FormSchema.PAGE_SIZE)))
                    .bounds(12, height - 28, buttonWidth, 20).build());
            next = addRenderableWidget(UiButton.create(text("next", "Next"),
                            ignored -> request(field().offset() + FormSchema.PAGE_SIZE))
                    .bounds(16 + buttonWidth, height - 28, buttonWidth, 20).build());
            refresh = addRenderableWidget(UiButton.create(text("refresh", "Refresh"), ignored -> request(0))
                    .bounds(20 + buttonWidth * 2, height - 28, buttonWidth, 20).build());
            addRenderableWidget(UiButton.create(text("back", "Back"), ignored -> onClose())
                    .bounds(24 + buttonWidth * 3, height - 28, buttonWidth, 20).build());
            setInitialFocus(search);
            if (!initialized || !owner.metadataReady && owner.pending < 0) {
                initialized = true;
                request(0);
            }
            updated();
        }

        private FormField field() { return owner.targetField(); }
        private boolean choicesReady() {
            return owner.available() && owner.metadataReady && owner.metadataError.isBlank()
                    && owner.pending < 0 && owner.refreshAt == 0 && queryAt == 0 && field() != null;
        }

        private void request(int offset) {
            queryAt = 0;
            scroll = 0;
            owner.request(new FormQuery(owner.mode.targetField(), query, offset));
            updated();
        }

        private void choose(int row) {
            if (!choicesReady() || scroll + row >= field().choices().size()) return;
            FormChoice choice = field().choices().get(scroll + row);
            owner.targetValue = choice.value();
            owner.targetName = safe(choice.label(), owner.targetLabel().getString());
            owner.notice = Component.empty();
            minecraft.setScreen(owner);
        }

        private Component statusText() {
            if (owner.pending >= 0 || queryAt != 0 || owner.refreshAt != 0) return text("search_loading", "Loading eligible matches…");
            if (!owner.available() || !owner.metadataError.isBlank() || !owner.metadataReady) return owner.reason();
            if (field() == null || field().choices().isEmpty()) return text("search_empty", "No eligible matches. Try another name or refresh permissions.");
            return text("search_help", "Choose a name. Scroll for more entries on this page.");
        }

        private void updated() {
            if (status == null) return;
            FormField field = field();
            List<FormChoice> choices = field == null ? List.of() : field.choices();
            scroll = Math.max(0, Math.min(scroll, Math.max(0, choices.size() - rows.size())));
            for (int row = 0; row < rows.size(); row++) {
                Button button = rows.get(row);
                int index = scroll + row;
                button.visible = index < choices.size();
                button.active = button.visible && choicesReady();
                if (button.visible) {
                    Component label = Component.literal(safe(choices.get(index).label(), owner.targetLabel().getString()));
                    button.setMessage(label);
                    button.setTooltip(Tooltip.create(label));
                }
            }
            previous.active = choicesReady() && field.offset() > 0;
            next.active = choicesReady() && field.more() && field.offset() <= 1_000_000 - FormSchema.PAGE_SIZE;
            refresh.active = owner.available() && owner.pending < 0;
            status.setMessage(statusText());
            status.setTooltip(Tooltip.create(statusText()));
        }

        @Override
        public void tick() {
            search.tick();
            owner.pollRequests();
            if (queryAt != 0 && ClientHooks.time() >= queryAt) request(0);
            updated();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            UiTheme.background(graphics, width, height);
            UiTheme.header(graphics, title, width, 10);
            FormField field = field();
            if (field != null && choicesReady()) {
                int count = field.choices().size();
                graphics.drawString(font, text("search_page", "Page %s · %s–%s of %s on this page",
                        field.offset() / FormSchema.PAGE_SIZE + 1, count == 0 ? 0 : scroll + 1,
                        Math.min(count, scroll + rows.size()), count), 12, height - 47, UiTheme.MUTED, false);
            }
            updated();
            super.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (mouseX >= 12 && mouseX < width - 12 && mouseY >= 62 && mouseY < height - 74 && delta != 0) {
                scroll = Math.max(0, scroll - (int) Math.signum(delta) * 2);
                updated();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && search.isFocused()) {
                request(0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                scroll = Math.max(0, scroll + (keyCode == GLFW.GLFW_KEY_PAGE_UP ? -rows.size() : rows.size()));
                updated();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public Component getNarrationMessage() { return title.copy().append(". ").append(statusText()); }
        @Override
        public void removed() { owner.invalidateMetadata(); }
        @Override
        public void onClose() { minecraft.setScreen(owner); }
        @Override
        public boolean isPauseScreen() { return false; }
    }
}
