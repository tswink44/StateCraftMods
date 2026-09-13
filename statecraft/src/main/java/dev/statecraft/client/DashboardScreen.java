package dev.statecraft.client;

import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.DashboardLayout;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

final class DashboardScreen extends Screen {
    private record Card(Component label, Component name, Component detail, Item icon, Runnable action) {}
    private record Heading(Component title, int y) {}
    private final ViewState state;
    private final UiScope scope;
    private final List<DashboardLink> links = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private PersonalDashboard shown;
    private int contentHeight;
    private Button refresh;

    DashboardScreen(ViewState state) {
        super(ClientText.tr("gui.statecraft.dashboard.title", "My Dashboard"));
        this.state = state;
        scope = ClientHooks.scope();
    }

    @Override
    protected void init() {
        links.clear();
        headings.clear();
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(12, 10, 64, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.dashboard.inbox", "Personal inbox"),
                        ignored -> ClientHooks.navigate(UiQuery.page("statecraft:mail")))
                .bounds(width - 116, 10, 104, 20).build());
        refresh = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.refresh", "Refresh"),
                        ignored -> ClientHooks.refresh(state)).bounds(16, height - 28, 84, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.sections", "Sections"),
                        ignored -> ClientHooks.sections(null)).bounds(width - 116, height - 28, 100, 20).build());
        shown = state.personalDashboard();
        if (shown != null) {
            List<Card> profile = new ArrayList<>();
            profile.add(new Card(label("name", "Name"), Component.literal(shown.name()), Component.empty(), Items.PLAYER_HEAD, null));
            profile.add(citizenship("nation", "Nation", shown.nation(), Items.WHITE_BANNER));
            profile.add(citizenship("state", "State", shown.state(), Items.MAP));
            profile.add(citizenship("city", "City", shown.city(), Items.BRICKS));
            int y = addCards(0, profile);
            y = section(PersonalDashboard.Section.ACCOUNTS, label("accounts", "Bank balances"), shown.accounts(), Items.GOLD_INGOT,
                    shown.economyAvailable() ? label("no_accounts", "No personal accounts yet.") : label("economy_absent", "Economy is not installed."), y);
            y = section(PersonalDashboard.Section.COMPANIES, label("companies", "Companies you own shares in"), shown.companies(),
                    Items.EMERALD, label("no_companies", "You do not own any company shares yet."), y);
            y = section(PersonalDashboard.Section.PROPERTY_CITIES, label("property", "Cities where you own property"), shown.propertyCities(),
                    Items.GRASS_BLOCK, label("no_property", "You do not own any property yet."), y);
            contentHeight = y;
        } else {
            contentHeight = 0;
        }
        scroll(state.scroll());
        refresh.active = state.pending() < 0;
        if (state.automaticRefresh()) ClientHooks.refresh(state);
    }

    private Card citizenship(String key, String title, PersonalDashboard.Entry entry, Item icon) {
        return entry == null ? new Card(label(key, title), label("no_" + key, "No " + key + " citizenship"),
                Component.empty(), icon, null)
                : new Card(label(key, title), ClientText.of(entry.name()), ClientText.of(entry.detail()), icon,
                        () -> ClientHooks.navigate(entry.target()));
    }

    private int section(PersonalDashboard.Section section, Component title, PersonalDashboard.Page page, Item icon,
                        Component empty, int y) {
        headings.add(new Heading(title, y + 10));
        y += 30;
        List<Card> cards = page.entries().stream().map(entry -> new Card(Component.empty(), ClientText.of(entry.name()),
                ClientText.of(entry.detail()), icon, () -> ClientHooks.navigate(entry.target()))).toList();
        if (cards.isEmpty()) cards = List.of(new Card(Component.empty(), empty, Component.empty(), icon, null));
        y = addCards(y, cards);
        if (page.offset() > 0 || page.more()) {
            int left = DashboardLayout.left(width);
            int fullWidth = DashboardLayout.width(width);
            DashboardLink previous = new DashboardLink(new DashboardLayout.Box(left, y, 100, 24),
                    new Card(Component.empty(), ClientText.tr("gui.statecraft.previous", "Previous"), Component.empty(), null,
                            page.offset() > 0 ? () -> page(section, page.offset() - PersonalDashboard.PAGE_SIZE) : null));
            DashboardLink next = new DashboardLink(new DashboardLayout.Box(left + fullWidth - 100, y, 100, 24),
                    new Card(Component.empty(), ClientText.tr("gui.statecraft.next", "Next"), Component.empty(), null,
                            page.more() ? () -> page(section, page.offset() + PersonalDashboard.PAGE_SIZE) : null));
            links.add(addWidget(previous));
            links.add(addWidget(next));
            y += 32;
        }
        return y + 8;
    }

    private void page(PersonalDashboard.Section section, int offset) {
        state.dashboardRequest(state.dashboardRequest().page(section, offset));
        ClientHooks.refresh(state);
    }

    private int addCards(int y, List<Card> cards) {
        int textWidth = DashboardLayout.cardWidth(width) - 48;
        List<Integer> heights = cards.stream().map(card -> Math.max(44,
                font.split(card.name(), textWidth).size() * 10 + font.split(card.detail(), textWidth).size() * 10
                        + (card.label().getString().isEmpty() ? 14 : 28))).toList();
        List<DashboardLayout.Box> positions = DashboardLayout.cards(width, y, heights);
        for (int i = 0; i < cards.size(); i++) links.add(addWidget(new DashboardLink(positions.get(i), cards.get(i))));
        return positions.isEmpty() ? y : positions.get(positions.size() - 1).y() + positions.get(positions.size() - 1).height()
                + DashboardLayout.GAP;
    }

    private void scroll(int pixels) {
        int available = Math.max(1, height - DashboardLayout.TOP - DashboardLayout.BOTTOM);
        state.scroll(Math.max(0, Math.min(Math.max(0, contentHeight - available), pixels)));
        for (DashboardLink link : links) link.setY(DashboardLayout.TOP + link.box.y() - state.scroll());
    }

    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) {
            minecraft.setScreen(null);
            return;
        }
        if (shown != state.personalDashboard()) rebuildWidgets();
        refresh.active = state.pending() < 0;
        if (state.automaticRefresh()) ClientHooks.refresh(state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 14);
        graphics.enableScissor(12, DashboardLayout.TOP, width - 12, height - DashboardLayout.BOTTOM);
        for (Heading heading : headings) {
            graphics.drawString(font, heading.title(), DashboardLayout.left(width), DashboardLayout.TOP + heading.y() - state.scroll(),
                    UiTheme.ACCENT, false);
        }
        int contentMouseY = mouseY >= DashboardLayout.TOP && mouseY < height - DashboardLayout.BOTTOM ? mouseY : -10000;
        for (DashboardLink link : links) {
            if (link.getY() + link.getHeight() > DashboardLayout.TOP && link.getY() < height - DashboardLayout.BOTTOM) {
                link.render(graphics, mouseX, contentMouseY, delta);
            }
        }
        if (shown == null) {
            Component message = state.pending() >= 0 ? label("loading", "Loading your dashboard...") : ClientText.of(state.placeholder());
            graphics.drawWordWrap(font, message, 20, DashboardLayout.TOP + 10, width - 40, UiTheme.MUTED);
        }
        graphics.disableScissor();
        UiText notice = state.banner().fallback().isEmpty() && shown != null ? shown.notice() : state.banner();
        if (!notice.fallback().isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(ClientText.of(notice).getString(), width - 40),
                    20, height - 41, UiTheme.WARNING, false);
        }
        int viewport = height - DashboardLayout.TOP - DashboardLayout.BOTTOM;
        if (contentHeight > viewport) {
            int bar = Math.max(12, viewport * viewport / contentHeight);
            int y = DashboardLayout.TOP + state.scroll() * (viewport - bar) / (contentHeight - viewport);
            graphics.fill(width - 10, y, width - 7, y + bar, UiTheme.ACCENT);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= DashboardLayout.TOP && mouseY < height - DashboardLayout.BOTTOM) {
            scroll(state.scroll() - (int) Math.signum(delta) * 36);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll(state.scroll() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (height - 100));
            return true;
        }
        boolean handled = super.keyPressed(key, scan, modifiers);
        if (getFocused() instanceof DashboardLink link) {
            if (link.getY() < DashboardLayout.TOP) scroll(link.box.y());
            else if (link.getY() + link.getHeight() > height - DashboardLayout.BOTTOM) {
                scroll(link.box.y() + link.getHeight() - (height - DashboardLayout.TOP - DashboardLayout.BOTTOM));
            }
        }
        return handled;
    }

    @Override
    public void removed() { ClientHooks.cancelView(state); }
    @Override
    public void onClose() { ClientHooks.back(); }
    @Override
    public boolean isPauseScreen() { return false; }

    private static Component label(String key, String text) { return ClientText.tr("gui.statecraft.dashboard." + key, text); }

    private final class DashboardLink extends Button {
        private final DashboardLayout.Box box;
        private final Card card;

        DashboardLink(DashboardLayout.Box box, Card card) {
            super(box.x(), DashboardLayout.TOP + box.y(), box.width(), box.height(), card.name(),
                    ignored -> { if (card.action() != null) card.action().run(); }, DEFAULT_NARRATION);
            this.box = box;
            this.card = card;
            active = card.action() != null;
            setTooltip(Tooltip.create(card.label().copy().append("\n").append(card.name()).append("\n").append(card.detail())));
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return y >= DashboardLayout.TOP && y < DashboardScreen.this.height - DashboardLayout.BOTTOM && super.isMouseOver(x, y);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean focused = active && (isFocused() || isMouseOver(mouseX, mouseY));
            graphics.fill(getX(), getY(), getX() + width, getY() + height, focused ? UiTheme.HOVER : UiTheme.SURFACE);
            if (focused) graphics.renderOutline(getX(), getY(), width, height, UiTheme.ACCENT);
            int x = getX() + 12;
            if (card.icon() != null) {
                graphics.renderItem(new ItemStack(card.icon()), x, getY() + (height - 16) / 2);
                x += 26;
            }
            int y = getY() + 8;
            if (!card.label().getString().isEmpty()) {
                graphics.drawString(font, card.label(), x, y, UiTheme.MUTED, false);
                y += 13;
            }
            int available = getX() + width - x - 10;
            for (var line : font.split(card.name(), available)) {
                graphics.drawString(font, line, x, y, UiTheme.TEXT, false);
                y += 10;
            }
            for (var line : font.split(card.detail(), available)) {
                graphics.drawString(font, line, x, y, card.icon() == Items.GOLD_INGOT ? UiTheme.POSITIVE : UiTheme.MUTED, false);
                y += 10;
            }
        }
    }
}
