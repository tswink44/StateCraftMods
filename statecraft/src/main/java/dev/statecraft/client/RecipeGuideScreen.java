package dev.statecraft.client;

import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.client.state.RecipeGuideLayout;
import dev.statecraft.client.state.RecipeGuideLayout.Rect;
import dev.statecraft.client.state.RecipeGuideLayout.Selection;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class RecipeGuideScreen extends Screen {
    private static final String RECIPE_NAMESPACE = "statecraft_economy";
    private static final int RETAINED_VIEWS = 64;
    private static final Map<UUID, Selection<ResourceLocation>> SELECTIONS = new LinkedHashMap<>();
    private final UiScope scope;
    private final Selection<ResourceLocation> selection;
    private final List<Hover> hovers = new ArrayList<>();
    private RecipeGuideLayout layout;
    private ClientLevel shownLevel;
    private RecipeManager shownManager;
    private Map<ResourceLocation, Recipe<?>> snapshot = Map.of();
    private List<RecipeEntry> recipes = List.of();
    private List<Material> materials = List.of();
    private Availability availability = Availability.NO_LEVEL;
    private long nextScan;
    private boolean choosing;
    private int recipePage;
    private int materialPage;
    private Button previous;
    private Button next;
    private Button materialPrevious;
    private Button materialNext;

    private enum Availability { READY, NO_LEVEL, EMPTY, UNAVAILABLE }
    private record RecipeEntry(ResourceLocation id, Recipe<?> recipe, ItemStack result, ItemStack station,
                               List<Ingredient> ingredients, List<Integer> grid, boolean readable) {}
    private record Material(Ingredient ingredient, ItemStack[] alternatives, int occurrences) {}
    private record Hover(Rect bounds, ItemStack stack, List<Component> notes) {}

    public RecipeGuideScreen(ViewState state) {
        super(tr("title", "Crafting guide"));
        scope = ClientHooks.workspace() == null ? null : ClientHooks.workspace().scope();
        Selection<ResourceLocation> retained = SELECTIONS.remove(state.id());
        selection = retained == null ? new Selection<>() : retained;
        SELECTIONS.put(state.id(), selection);
        while (SELECTIONS.size() > RETAINED_VIEWS) SELECTIONS.remove(SELECTIONS.keySet().iterator().next());
    }

    @Override
    protected void init() {
        RecipeGuideLayout oldLayout = layout;
        layout = null;
        previous = next = materialPrevious = materialNext = null;
        if (width < RecipeGuideLayout.MIN_WIDTH || height < RecipeGuideLayout.MIN_HEIGHT) {
            button(tr("back", "Back"), new Rect(8, Math.max(8, height - 28), Math.max(20, width - 16), 20),
                    ignored -> ClientHooks.back(), tr("back_hint", "Return to the previous section."));
            return;
        }
        layout = new RecipeGuideLayout(width, height);
        syncRecipes(true);
        refreshMaterials();
        if (choosing && oldLayout != null) {
            recipePage = RecipeGuideLayout.pageForIndex(RecipeGuideLayout.pageStart(recipePage,
                    recipes.size(), oldLayout.choicesPerPage()), layout.choicesPerPage());
        }
        recipePage = Math.min(recipePage, recipePages() - 1);
        Button selector;
        if (choosing || recipes.isEmpty()) {
            selector = button(choosing ? tr("return_recipe", "Back to recipe") : tr("choose", "Choose a recipe"),
                    layout.selector(), ignored -> togglePicker(), tr("choose_hint", "Browse the world's synced recipes."));
            selector.active = choosing || !recipes.isEmpty();
        } else {
            selector = addRenderableWidget(new RecipeButton(layout.selector(), selectedRecipe(), true));
        }
        if (choosing) {
            int start = RecipeGuideLayout.pageStart(recipePage, recipes.size(), layout.choicesPerPage());
            for (int row = 0; row < layout.choicesPerPage() && start + row < recipes.size(); row++) {
                addRenderableWidget(new RecipeButton(layout.choiceRow(row), recipes.get(start + row), false));
            }
        } else {
            materialPrevious = button(Component.literal("<"), layout.materialPrevious(),
                    ignored -> pageMaterials(-1), tr("materials_previous", "Previous materials"));
            materialNext = button(Component.literal(">"), layout.materialNext(),
                    ignored -> pageMaterials(1), tr("materials_next", "Next materials"));
        }
        previous = button(choosing ? tr("page_previous", "Previous page") : tr("previous", "Previous recipe"),
                layout.previous(), ignored -> navigateRecipes(-1), tr("previous_hint", "Previous recipe or page (Page Up)."));
        next = button(choosing ? tr("page_next", "Next page") : tr("next", "Next recipe"),
                layout.next(), ignored -> navigateRecipes(1), tr("next_hint", "Next recipe or page (Page Down)."));
        button(tr("back", "Back"), layout.footerButton(0), ignored -> onClose(),
                tr("back_hint", "Return to the previous section."));
        bankingButton("economy:atm", tr("accounts", "Accounts"), layout.footerButton(1),
                tr("accounts_hint", "Open your accounts and banking actions."));
        bankingButton("economy:bank", tr("banks", "Banks"), layout.footerButton(2),
                tr("banks_hint", "Open banks and loans. Currency is issued by banks, not crafted."));
        controls();
        setInitialFocus(selector);
    }

    private Button button(Component label, Rect bounds, Button.OnPress action, Component hint) {
        return addRenderableWidget(UiButton.create(label, action)
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .tooltip(Tooltip.create(hint)).build());
    }

    private void bankingButton(String page, Component label, Rect bounds, Component hint) {
        boolean registered = MenuRegistry.pages().stream().anyMatch(entry -> entry.id().equals(page));
        Button button = button(label, bounds, ignored -> {
            if (ClientHooks.current(scope)) ClientHooks.navigate(UiQuery.page(page));
        }, registered ? hint : tr("section_unavailable", "This section is not available on this client."));
        button.active = registered && ClientHooks.current(scope);
    }

    private boolean syncRecipes(boolean force) {
        ClientLevel level = minecraft.level;
        long now = ClientHooks.time();
        boolean changedLevel = level != shownLevel;
        if (!force && !changedLevel && now < nextScan) {
            ResourceLocation selected = selection.selected().orElse(null);
            if (selected == null || shownManager == null
                    || shownManager.byKey(selected).orElse(null) == snapshot.get(selected)) return false;
        }
        nextScan = now + 500;
        shownLevel = level;
        if (level == null) return unavailable(Availability.NO_LEVEL);
        try {
            RecipeManager manager = level.getRecipeManager();
            Map<ResourceLocation, Recipe<?>> live = new LinkedHashMap<>();
            for (Recipe<?> recipe : manager.getRecipes()) {
                if (RECIPE_NAMESPACE.equals(recipe.getId().getNamespace())) live.put(recipe.getId(), recipe);
            }
            boolean same = !changedLevel && manager == shownManager && live.size() == snapshot.size()
                    && live.entrySet().stream().allMatch(entry -> snapshot.get(entry.getKey()) == entry.getValue());
            shownManager = manager;
            if (same && (availability == Availability.READY || availability == Availability.EMPTY)) return false;
            snapshot = Map.copyOf(live);
            recipes = live.values().stream().map(recipe -> readRecipe(recipe, level))
                    .sorted(Comparator.comparing((RecipeEntry entry) -> recipeName(entry).getString(),
                            String.CASE_INSENSITIVE_ORDER).thenComparing(entry -> entry.id().toString()))
                    .toList();
            selection.reconcile(recipes.stream().map(RecipeEntry::id).toList());
            availability = recipes.isEmpty() ? Availability.EMPTY : Availability.READY;
            materialPage = 0;
            if (layout != null) recipePage = RecipeGuideLayout.pageForIndex(selection.index(), layout.choicesPerPage());
            return true;
        } catch (RuntimeException failure) {
            return unavailable(Availability.UNAVAILABLE);
        }
    }

    private boolean unavailable(Availability reason) {
        boolean changed = availability != reason || !recipes.isEmpty();
        availability = reason;
        shownManager = null;
        snapshot = Map.of();
        recipes = List.of();
        materials = List.of();
        selection.reconcile(List.of());
        recipePage = materialPage = 0;
        return changed;
    }

    private RecipeEntry readRecipe(Recipe<?> recipe, ClientLevel level) {
        try {
            ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
            ItemStack station = recipe.getToastSymbol().copy();
            List<Ingredient> ingredients = List.copyOf(recipe.getIngredients());
            List<Integer> slots = List.of();
            if (recipe instanceof ShapedRecipe shaped && shaped.getWidth() >= 1 && shaped.getWidth() <= 3
                    && shaped.getHeight() >= 1 && shaped.getHeight() <= 3
                    && ingredients.size() == shaped.getWidth() * shaped.getHeight()) {
                slots = RecipeGuideLayout.shapedSlots(shaped.getWidth(), shaped.getHeight());
            } else if (recipe instanceof ShapelessRecipe && ingredients.size() <= 9) {
                slots = RecipeGuideLayout.shapelessSlots(ingredients.size());
            }
            return new RecipeEntry(recipe.getId(), recipe, result, station, ingredients, slots, true);
        } catch (RuntimeException failure) {
            return new RecipeEntry(recipe.getId(), recipe, ItemStack.EMPTY, ItemStack.EMPTY, List.of(), List.of(), false);
        }
    }

    private RecipeEntry selectedRecipe() {
        int index = selection.index();
        return index < 0 || index >= recipes.size() ? null : recipes.get(index);
    }

    private void refreshMaterials() {
        RecipeEntry entry = selectedRecipe();
        List<Material> replacement = new ArrayList<>();
        if (entry != null) {
            for (Ingredient ingredient : entry.ingredients()) {
                if (ingredient.isEmpty()) continue;
                ItemStack[] alternatives = alternatives(ingredient);
                int same = -1;
                for (int i = 0; i < replacement.size(); i++) {
                    Material prior = replacement.get(i);
                    if (prior.ingredient() == ingredient || ingredient.getClass() == Ingredient.class
                            && prior.ingredient().getClass() == Ingredient.class
                            && sameAlternatives(prior.alternatives(), alternatives)) {
                        same = i;
                        break;
                    }
                }
                if (same < 0) replacement.add(new Material(ingredient, alternatives, 1));
                else {
                    Material prior = replacement.get(same);
                    replacement.set(same, new Material(prior.ingredient(), prior.alternatives(), prior.occurrences() + 1));
                }
            }
        }
        materials = List.copyOf(replacement);
        if (layout != null) materialPage = Math.min(materialPage, materialPages() - 1);
    }

    private static boolean sameAlternatives(ItemStack[] first, ItemStack[] second) {
        if (first.length == 0 || first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) if (!ItemStack.matches(first[i], second[i])) return false;
        return true;
    }

    private static ItemStack[] alternatives(Ingredient ingredient) {
        try {
            return java.util.Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty()).toArray(ItemStack[]::new);
        } catch (RuntimeException failure) {
            return new ItemStack[0];
        }
    }

    private static ItemStack alternative(ItemStack[] choices) {
        int index = RecipeGuideLayout.alternativeIndex(ClientHooks.time(), choices.length);
        return index < 0 ? ItemStack.EMPTY : choices[index];
    }

    private void togglePicker() {
        syncRecipes(true);
        choosing = !choosing;
        recipePage = RecipeGuideLayout.pageForIndex(selection.index(), layout.choicesPerPage());
        rebuildWidgets();
    }

    private void selectRecipe(ResourceLocation id) {
        syncRecipes(true);
        if (snapshot.containsKey(id)) {
            selection.select(id);
            materialPage = 0;
            choosing = false;
        }
        rebuildWidgets();
    }

    private void navigateRecipes(int direction) {
        syncRecipes(true);
        if (choosing) recipePage = Math.max(0, Math.min(recipePage + direction, recipePages() - 1));
        else if (selection.step(direction)) materialPage = 0;
        rebuildWidgets();
    }

    private void pageMaterials(int direction) {
        materialPage = Math.max(0, Math.min(materialPage + direction, materialPages() - 1));
        controls();
    }

    private int recipePages() { return RecipeGuideLayout.pageCount(recipes.size(), layout.choicesPerPage()); }
    private int materialPages() { return RecipeGuideLayout.pageCount(materials.size(), layout.materialsPerPage()); }

    private void controls() {
        if (layout == null || previous == null) return;
        previous.active = !recipes.isEmpty() && (choosing ? recipePage > 0 : selection.index() > 0);
        next.active = !recipes.isEmpty() && (choosing ? recipePage + 1 < recipePages() : selection.index() + 1 < recipes.size());
        if (materialPrevious != null) {
            materialPrevious.visible = materialNext.visible = !choosing && materialPages() > 1;
            materialPrevious.active = materialPage > 0;
            materialNext.active = materialPage + 1 < materialPages();
        }
    }

    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) {
            minecraft.setScreen(null);
            return;
        }
        if (syncRecipes(false)) rebuildWidgets();
        refreshMaterials();
        controls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiTheme.background(graphics, width, height);
        hovers.clear();
        if (layout == null) {
            UiTheme.header(graphics, title, width, 12);
            drawFitted(graphics, tr("window_small", "Enlarge the window to view recipes."),
                    8, 36, Math.max(0, width - 16), UiTheme.MUTED);
        } else {
            UiTheme.header(graphics, title, width, layout.headerY());
            panel(graphics, layout.body(), UiTheme.SURFACE);
            if (availability != Availability.READY) renderUnavailable(graphics);
            else if (!choosing) renderRecipe(graphics, mouseX, mouseY);
            Component count = recipes.isEmpty() ? tr("none", "No recipes")
                    : choosing ? tr("page_position", "Page %s / %s", recipePage + 1, recipePages())
                    : tr("position", "%s / %s", selection.index() + 1, recipes.size());
            centered(graphics, count, layout.counter(), UiTheme.MUTED);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHover(graphics, mouseX, mouseY);
    }

    private void renderUnavailable(GuiGraphics graphics) {
        Component heading;
        Component explanation;
        if (availability == Availability.NO_LEVEL) {
            heading = tr("no_world", "No world connected");
            explanation = tr("no_world_hint", "Join a world to view the recipes it has synced to your client.");
        } else if (availability == Availability.UNAVAILABLE) {
            heading = tr("unavailable", "Recipes unavailable");
            explanation = tr("unavailable_hint", "The client's recipes could not be read. The guide will retry automatically.");
        } else {
            heading = tr("empty", "No Economy recipes received");
            explanation = tr("empty_hint", "Check that client and server have matching Economy modules and enabled datapacks. Synced recipes will appear here automatically.");
        }
        int x = layout.body().x() + 12;
        int y = layout.body().y() + 14;
        drawFitted(graphics, heading, x, y, layout.body().width() - 24, UiTheme.TEXT);
        wrapped(graphics, explanation, new Rect(x, y + 18, layout.body().width() - 24, layout.body().height() - 40),
                UiTheme.MUTED);
    }

    private void renderRecipe(GuiGraphics graphics, int mouseX, int mouseY) {
        RecipeEntry entry = selectedRecipe();
        if (entry == null) return;
        if (!entry.readable()) {
            wrapped(graphics, tr("preview_unavailable", "This recipe cannot provide a client preview. Other recipes can still be browsed."),
                    new Rect(layout.body().x() + 12, layout.body().y() + 16,
                            layout.body().width() - 24, layout.body().height() - 28), UiTheme.WARNING);
            return;
        }
        boolean craftingGrid = !entry.grid().isEmpty();
        int separator = layout.legend().x() - 7;
        graphics.fill(separator, layout.body().y() + 8, separator + 1, layout.body().bottom() - 8, UiTheme.BORDER);
        centered(graphics, craftingGrid ? tr("crafting", "Crafting") : tr("station", "Station"),
                new Rect(layout.grid().x() - 2, layout.gridLabelY(), 68, 9), UiTheme.MUTED);
        centered(graphics, tr("result", "Result"),
                new Rect(layout.output().x() - 10, layout.gridLabelY(), 44, 9), UiTheme.MUTED);
        if (craftingGrid) {
            for (int slot = 0; slot < 9; slot++) {
                int ingredientIndex = entry.grid().get(slot);
                Ingredient ingredient = ingredientIndex < 0 ? Ingredient.EMPTY : entry.ingredients().get(ingredientIndex);
                if (ingredient.isEmpty()) renderSlot(graphics, layout.gridSlot(slot), ItemStack.EMPTY, List.of(), mouseX, mouseY);
                else {
                    ItemStack[] choices = alternatives(ingredient);
                    renderSlot(graphics, layout.gridSlot(slot), alternative(choices), ingredientNotes(choices.length), mouseX, mouseY);
                }
            }
        } else {
            renderSlot(graphics, layout.station(), entry.station(),
                    List.of(tr("station_hint", "Recipe workstation; materials are listed separately.")), mouseX, mouseY);
        }
        arrow(graphics, layout.arrow());
        renderSlot(graphics, layout.output(), entry.result(), entry.result().isEmpty()
                ? List.of(tr("dynamic_result", "The output depends on the input items; no fixed result was synced."))
                : List.of(tr("makes", "Makes %s per craft", entry.result().getCount())), mouseX, mouseY);
        if (!entry.result().isEmpty()) {
            centered(graphics, tr("quantity", "× %s", entry.result().getCount()),
                    new Rect(layout.output().x() - 10, layout.output().bottom() + 4, 44, 9), UiTheme.TEXT);
        }
        drawFitted(graphics, method(entry), layout.diagram().x(), layout.methodY(), layout.diagram().width(), UiTheme.ACCENT);
        drawFitted(graphics, craftingGrid ? tr("hover_hint", "Hover items for details") : tr("list_hint", "List only; not a grid"),
                layout.diagram().x(), layout.methodY() + 11, layout.diagram().width(), UiTheme.MUTED);
        renderMaterials(graphics, mouseX, mouseY);
    }

    private void renderMaterials(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect legend = layout.legend();
        drawFitted(graphics, tr("materials", "Materials (%s)", materials.size()), legend.x(), legend.y(), legend.width(), UiTheme.TEXT);
        int start = RecipeGuideLayout.pageStart(materialPage, materials.size(), layout.materialsPerPage());
        for (int row = 0; row < layout.materialsPerPage() && start + row < materials.size(); row++) {
            Material material = materials.get(start + row);
            ItemStack choice = alternative(material.alternatives());
            long amount = choice.isEmpty() ? material.occurrences() : (long) choice.getCount() * material.occurrences();
            ItemStack display = choice.isEmpty() ? ItemStack.EMPTY : choice.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
            Rect bounds = layout.materialRow(row);
            List<Component> notes = new ArrayList<>(ingredientNotes(material.alternatives().length));
            notes.add(tr("required", "%s required per craft", amount));
            renderSlot(graphics, new Rect(bounds.x(), bounds.y() + 1, 20, 20), display, notes, mouseX, mouseY);
            Component name = choice.isEmpty() ? tr("no_matches", "No matching items")
                    : tr("material", "%s × %s", amount, choice.getHoverName().getString());
            boolean variants = material.alternatives().length > 1;
            drawFitted(graphics, name, bounds.x() + 25, bounds.y() + (variants ? 2 : 7), bounds.width() - 25,
                    choice.isEmpty() ? UiTheme.WARNING : UiTheme.TEXT);
            if (variants) {
                int index = RecipeGuideLayout.alternativeIndex(ClientHooks.time(), material.alternatives().length);
                drawFitted(graphics, tr("alternatives", "Option %s of %s", index + 1, material.alternatives().length),
                        bounds.x() + 25, bounds.y() + 12, bounds.width() - 25, UiTheme.MUTED);
            }
            hovers.add(new Hover(bounds, display, notes));
        }
        if (materials.isEmpty()) {
            wrapped(graphics, tr("no_materials", "No fixed ingredient list was synced."),
                    new Rect(legend.x(), legend.y() + 16, legend.width(), legend.height() - 40), UiTheme.MUTED);
        }
        centered(graphics, materialPages() > 1 ? tr("position", "%s / %s", materialPage + 1, materialPages())
                        : tr("per_craft", "Per craft"), layout.materialCounter(), UiTheme.MUTED);
    }

    private static List<Component> ingredientNotes(int count) {
        if (count == 0) return List.of(tr("no_matches_hint", "This ingredient has no item previews on the client. Check synced recipes and item tags."));
        if (count == 1) return List.of();
        return List.of(tr("alternatives_hint", "Accepts any of %s alternatives. Item previews cycle automatically.", count));
    }

    private void renderSlot(GuiGraphics graphics, Rect bounds, ItemStack stack, List<Component> notes, int mouseX, int mouseY) {
        panel(graphics, bounds, bounds.contains(mouseX, mouseY) ? UiTheme.HOVER : UiTheme.BACKGROUND);
        if (!stack.isEmpty()) {
            int x = bounds.x() + (bounds.width() - 16) / 2;
            int y = bounds.y() + (bounds.height() - 16) / 2;
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        } else if (!notes.isEmpty()) centered(graphics, Component.literal("?"), bounds, UiTheme.WARNING);
        if (!stack.isEmpty() || !notes.isEmpty()) hovers.add(new Hover(bounds, stack, notes));
    }

    private static void panel(GuiGraphics graphics, Rect bounds, int fill) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), UiTheme.BORDER);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, fill);
    }

    private static void arrow(GuiGraphics graphics, Rect bounds) {
        int middle = bounds.y() + bounds.height() / 2;
        graphics.fill(bounds.x(), middle - 1, bounds.right() - 6, middle + 1, UiTheme.ACCENT);
        for (int i = 0; i < 5; i++) {
            graphics.fill(bounds.right() - 10 + i, middle - 4 + i,
                    bounds.right() - 9 + i, middle + 5 - i, UiTheme.ACCENT);
        }
    }

    private void centered(GuiGraphics graphics, Component text, Rect bounds, int color) {
        Component fitted = fit(text, bounds.width());
        graphics.drawString(font, fitted, bounds.x() + (bounds.width() - font.width(fitted)) / 2,
                bounds.y() + (bounds.height() - 9) / 2, color, false);
        if (font.width(text) > bounds.width()) hovers.add(new Hover(bounds, ItemStack.EMPTY, List.of(text)));
    }

    private Component fit(Component text, int maximum) {
        if (font.width(text) <= maximum) return text;
        if (maximum < font.width("…")) return Component.empty();
        return Component.literal(font.plainSubstrByWidth(text.getString(), maximum - font.width("…")) + "…")
                .withStyle(text.getStyle());
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int maximum, int color) {
        graphics.drawString(font, fit(text, maximum), x, y, color, false);
        if (font.width(text) > maximum) hovers.add(new Hover(new Rect(x, y, Math.max(0, maximum), 9), ItemStack.EMPTY, List.of(text)));
    }

    private void wrapped(GuiGraphics graphics, Component text, Rect bounds, int color) {
        int y = bounds.y();
        for (var line : font.split(text, Math.max(1, bounds.width()))) {
            if (y + 9 > bounds.bottom()) break;
            graphics.drawString(font, line, bounds.x(), y, color, false);
            y += 11;
        }
        hovers.add(new Hover(bounds, ItemStack.EMPTY, List.of(text)));
    }

    private void renderHover(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = hovers.size() - 1; i >= 0; i--) {
            Hover hover = hovers.get(i);
            if (!hover.bounds().contains(mouseX, mouseY)) continue;
            if (hover.stack().isEmpty()) {
                var lines = hover.notes().stream().flatMap(note -> font.split(note, Math.max(40, Math.min(240, width - 20))).stream()).toList();
                graphics.renderTooltip(font, lines, mouseX, mouseY);
            } else {
                List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hover.stack()));
                lines.addAll(hover.notes());
                graphics.renderTooltip(font, lines, hover.stack().getTooltipImage(), hover.stack(), mouseX, mouseY);
            }
            return;
        }
    }

    private static Component recipeName(RecipeEntry entry) {
        if (!entry.readable()) return tr("unknown_recipe", "Unavailable recipe");
        if (!entry.result().isEmpty()) return entry.result().getHoverName();
        return tr("special_recipe", "%s recipe", typeName(entry).getString());
    }

    private static Component typeName(RecipeEntry entry) {
        RecipeType<?> type = entry.recipe().getType();
        if (type == RecipeType.CRAFTING) return tr("type.crafting", "Crafting");
        if (type == RecipeType.SMELTING) return tr("type.smelting", "Smelting");
        if (type == RecipeType.BLASTING) return tr("type.blasting", "Blasting");
        if (type == RecipeType.SMOKING) return tr("type.smoking", "Smoking");
        if (type == RecipeType.CAMPFIRE_COOKING) return tr("type.campfire", "Campfire cooking");
        if (type == RecipeType.STONECUTTING) return tr("type.stonecutting", "Stonecutting");
        if (type == RecipeType.SMITHING) return tr("type.smithing", "Smithing");
        return tr("type.special", "Special");
    }

    private static Component method(RecipeEntry entry) {
        if (!entry.readable()) return tr("unknown_recipe", "Unavailable recipe");
        if (entry.recipe() instanceof ShapedRecipe shaped) {
            return entry.grid().isEmpty()
                    ? tr("pattern_list", "%s × %s pattern; list only", shaped.getWidth(), shaped.getHeight())
                    : tr("pattern", "Exact pattern · %s × %s", shaped.getWidth(), shaped.getHeight());
        }
        if (entry.recipe() instanceof ShapelessRecipe && !entry.grid().isEmpty()) return tr("shapeless", "Any arrangement");
        return typeName(entry);
    }

    @Override
    public Component getNarrationMessage() {
        RecipeEntry entry = selectedRecipe();
        if (entry == null) return title.copy().append(". ").append(tr("none", "No recipes"));
        Component message = title.copy().append(". ").append(recipeName(entry)).append(". ").append(method(entry));
        for (Material material : materials) {
            ItemStack stack = alternative(material.alternatives());
            if (!stack.isEmpty()) message = message.copy().append(". ").append(tr("material", "%s × %s",
                    (long) stack.getCount() * material.occurrences(), stack.getHoverName().getString()));
        }
        return message;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (layout != null && amount != 0) {
            if (!choosing && layout.legend().contains(mouseX, mouseY) && materialPages() > 1) {
                pageMaterials(amount > 0 ? -1 : 1);
                return true;
            }
            if (choosing && layout.body().contains(mouseX, mouseY)) {
                navigateRecipes(amount > 0 ? -1 : 1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (layout != null && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            navigateRecipes(keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (choosing && layout != null) {
            choosing = false;
            rebuildWidgets();
        } else ClientHooks.back();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static Component tr(String suffix, String fallback, Object... arguments) {
        return ClientText.tr("gui.statecraft.recipes." + suffix, fallback, arguments);
    }

    private final class RecipeButton extends Button {
        private final RecipeEntry entry;
        private final boolean selector;

        private RecipeButton(Rect bounds, RecipeEntry entry, boolean selector) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    recipeName(entry).copy().append(". ").append(method(entry)),
                    ignored -> {
                        if (selector) togglePicker();
                        else selectRecipe(entry.id());
                    }, DEFAULT_NARRATION);
            this.entry = entry;
            this.selector = selector;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Rect bounds = new Rect(getX(), getY(), getWidth(), getHeight());
            panel(graphics, bounds, isHoveredOrFocused() ? UiTheme.HOVER : UiTheme.SURFACE);
            if (selection.selected().filter(entry.id()::equals).isPresent()) {
                graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), UiTheme.ACCENT);
            }
            int itemX = getX() + 6;
            int itemY = getY() + (getHeight() - 16) / 2;
            if (!entry.result().isEmpty()) {
                graphics.renderItem(entry.result(), itemX, itemY);
                graphics.renderItemDecorations(font, entry.result(), itemX, itemY);
            } else centered(graphics, Component.literal("?"), new Rect(itemX, itemY, 16, 16), UiTheme.WARNING);
            drawFitted(graphics, recipeName(entry), getX() + 28, getY() + (selector ? 7 : 4),
                    getWidth() - (selector ? 50 : 36), UiTheme.TEXT);
            if (selector) graphics.drawString(font, Component.literal("v"), getX() + getWidth() - 14, getY() + 7, UiTheme.ACCENT, false);
            else {
                Component detail = entry.result().isEmpty() ? method(entry)
                        : tr("choice_detail", "%s · Makes %s", method(entry).getString(), entry.result().getCount());
                drawFitted(graphics, detail, getX() + 28, getY() + 16, getWidth() - 36, UiTheme.MUTED);
            }
            List<Component> notes = new ArrayList<>();
            if (entry.result().isEmpty()) notes.add(recipeName(entry));
            notes.add(method(entry));
            if (selector) notes.add(tr("choose_hint", "Browse the world's synced recipes."));
            if (!entry.readable()) notes.add(tr("preview_unavailable", "This recipe cannot provide a client preview. Other recipes can still be browsed."));
            else if (entry.result().isEmpty()) notes.add(tr("dynamic_result", "The output depends on the input items; no fixed result was synced."));
            hovers.add(new Hover(bounds, entry.result(), notes));
        }
    }
}
