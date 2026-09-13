package dev.statecraft.api.ui;

import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.UserError;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record GovernmentOverview(String id, GovernanceAccess.Kind kind, String name, String flag,
                                 String description, PersonalDashboard.Entry leader, PersonalDashboard.Entry parent,
                                 UiText treasury, PersonalDashboard.Page children, PersonalDashboard.Page officers,
                                 List<Group> groups, UiQuery officialInbox) {
    public static final int MAX_GROUPS = 8;
    public static final int MAX_ACTIONS_PER_GROUP = 16;
    public static final int MAX_CHARACTERS = 80_000;
    private static final Pattern UUID_TEXT = Pattern.compile(
            "(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    public enum Section { CHILDREN, OFFICERS }

    public record Request(String governmentId, int children, int officers) {
        public Request {
            if (!canonicalId(governmentId)) throw new UserError("Invalid government reference.");
            for (int offset : new int[]{children, officers}) {
                if (offset < 0 || offset > PersonalDashboard.MAX_OFFSET || offset % PersonalDashboard.PAGE_SIZE != 0) {
                    throw new UserError("Invalid government overview page.");
                }
            }
        }

        public Request page(Section section, int offset) {
            return switch (section) {
                case CHILDREN -> new Request(governmentId, offset, officers);
                case OFFICERS -> new Request(governmentId, children, offset);
            };
        }
    }

    public record Group(String id, UiText title, List<UiAction> actions) {
        public Group {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            actions = List.copyOf(actions);
            if (!id.matches("[a-z][a-z0-9_]{0,31}") || title.fallback().isBlank()
                    || title.fallback().length() > 128 || title.characters() > 1024
                    || actions.size() > MAX_ACTIONS_PER_GROUP) {
                throw new IllegalArgumentException("Invalid government action group.");
            }
            display(title);
            long characters = id.length() + title.characters();
            var identities = new HashSet<String>();
            for (UiAction action : actions) {
                if (!identities.add(action.page() + "\n" + action.template())) {
                    throw new IllegalArgumentException("Duplicate government action.");
                }
                display(action.label());
                display(action.disabledReason());
                characters += actionCharacters(action);
            }
            if (characters > MAX_CHARACTERS) throw new IllegalArgumentException("Government action group is too large.");
        }
    }

    public GovernmentOverview {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(treasury);
        Objects.requireNonNull(children);
        Objects.requireNonNull(officers);
        groups = List.copyOf(groups);
        if (!canonicalId(id) || Objects.requireNonNull(name).isBlank() || name.length() > 64
                || Objects.requireNonNull(flag).length() > 128 || Objects.requireNonNull(description).length() > 2000
                || treasury.characters() > 1024 || groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("Invalid government overview.");
        }
        display(name);
        display(flag);
        display(description);
        display(treasury);
        long characters = id.length() + name.length() + flag.length() + description.length() + treasury.characters();
        if (officialInbox != null) {
            if (!officialInbox.equals(new UiQuery("statecraft:official_mail",
                    new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, id), "", 0))) {
                throw new IllegalArgumentException("Invalid official inbox link.");
            }
            characters += officialInbox.page().length() + officialInbox.entity().namespace().length()
                    + officialInbox.entity().id().length() + 64;
        }
        characters += entryCharacters(leader, EntityRef.Kind.PLAYER);
        characters += entryCharacters(parent, EntityRef.Kind.GOVERNMENT);
        for (var entry : children.entries()) {
            characters += entryCharacters(entry, kind == GovernanceAccess.Kind.CITY
                    ? EntityRef.Kind.CLAIM : EntityRef.Kind.GOVERNMENT);
        }
        for (var entry : officers.entries()) characters += entryCharacters(entry, EntityRef.Kind.PLAYER);
        var groupIds = new HashSet<String>();
        var actionIds = new HashSet<String>();
        for (Group group : groups) {
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("Duplicate government action group.");
            characters += group.id().length() + group.title().characters();
            for (UiAction action : group.actions()) {
                if (!actionIds.add(action.page() + "\n" + action.template())) {
                    throw new IllegalArgumentException("Duplicate government action.");
                }
                characters += actionCharacters(action);
            }
        }
        if (characters > MAX_CHARACTERS) throw new IllegalArgumentException("Government overview exceeds its text budget.");
    }

    public PersonalDashboard.Page section(Section section) {
        return switch (section) {
            case CHILDREN -> children;
            case OFFICERS -> officers;
        };
    }

    private static boolean canonicalId(String id) {
        if (id == null) return false;
        try { return UUID.fromString(id).toString().equals(id); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static long entryCharacters(PersonalDashboard.Entry entry, EntityRef.Kind kind) {
        if (entry == null) return 0;
        display(entry.name());
        display(entry.detail());
        UiQuery target = entry.target();
        if (!"statecraft".equals(target.entity().namespace()) || target.entity().kind() != kind
                || !target.search().isEmpty() || target.offset() != 0
                || kind != EntityRef.Kind.CLAIM && !canonicalId(target.entity().id())) {
            throw new IllegalArgumentException("Invalid government overview link.");
        }
        return entry.name().characters() + entry.detail().characters() + target.page().length()
                + target.entity().namespace().length() + target.entity().id().length() + 64;
    }

    private static long actionCharacters(UiAction action) {
        long characters = action.page().length() + action.template().length()
                + action.label().characters() + action.disabledReason().characters();
        for (var value : action.values().entrySet()) characters += value.getKey().length() + value.getValue().length();
        return characters;
    }

    private static void display(UiText text) {
        display(text.fallback());
        text.arguments().forEach(GovernmentOverview::display);
    }

    private static void display(String text) {
        if (UUID_TEXT.matcher(text).find()) {
            throw new IllegalArgumentException("Government display text must not contain raw identifiers.");
        }
    }
}
