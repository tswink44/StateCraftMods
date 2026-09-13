package dev.statecraft.api;

import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.form.FormState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormApiTest {
    private static final Actor ACTOR = new Actor(UUID.randomUUID(), "Mayor", false, "minecraft:overworld", 0, 0);

    @Test
    void preferredParentsAndSoleEligibleGovernorsArePrepopulated() {
        FormBuilder builder = stateForm(Map.of(), FormQuery.INITIAL);
        assertEquals("nation-b", builder.value("nation"));
        assertEquals("citizen-b", builder.value("governor"));
        FormField governor = builder.build().field("governor").orElseThrow();
        assertEquals("Citizen B", governor.selectedLabel());
        assertEquals(List.of("nation"), governor.dependencies());
        assertEquals("", builder.value("name"));
    }

    @Test
    void forgedOrStaleGovernorSelectionsAreNotReplacedSilently() {
        FormBuilder builder = stateForm(Map.of("nation", "nation-b", "governor", "citizen-a"), FormQuery.INITIAL);
        assertEquals("", builder.value("governor"));
        FormState state = new FormState();
        state.apply(builder.build(), Map.of());
        assertFalse(state.complete());
    }

    @Test
    void changingParentClearsAllDescendantsButKeepsUnrelatedText() {
        FormBuilder builder = new FormBuilder(new FormContext(ACTOR, "statecraft:claims",
                "test <nation> <state> <city> <name>", Map.of("name", "Keep this"), FormQuery.INITIAL));
        builder.choice("nation", "Nation", "", List.of(new FormChoice("n", "Nation")), "n", List.of(), false);
        builder.choice("state", "State", "", List.of(new FormChoice("s", "State")), "s", List.of("nation"), false);
        builder.choice("city", "City", "", List.of(new FormChoice("c", "City")), "c", List.of("state"), false);
        FormState state = new FormState();
        state.apply(builder.build(), Map.of());
        assertTrue(state.complete());
        assertEquals(Set.of("state", "city"), state.change("nation", "other"));
        assertFalse(state.values().containsKey("state"));
        assertFalse(state.values().containsKey("city"));
        assertEquals("", state.label("city"));
        assertEquals("Keep this", state.value("name"));
        assertEquals(1, state.dependencyRevision());
        assertFalse(state.complete());
    }

    @Test
    void asynchronousMetadataDoesNotOverwriteNewlyTypedNames() {
        FormState state = new FormState();
        state.apply(stateForm(Map.of(), FormQuery.INITIAL).build(), Map.of());
        state.change("name", "First");
        Map<String, String> sent = state.values();
        state.change("name", "Still typing");
        state.apply(stateForm(sent, FormQuery.INITIAL).build(), sent);
        assertEquals("Still typing", state.value("name"));
        assertEquals("citizen-b", state.value("governor"));
    }

    @Test
    void searchAndPaginationDoNotInvalidateSelectionsOutsideTheVisiblePage() {
        List<FormChoice> choices = IntStream.range(0, 45).mapToObj(i -> new FormChoice("n" + i,
                String.format(java.util.Locale.ROOT, "Nation %03d", i), "Eligible")).toList();
        FormBuilder builder = new FormBuilder(new FormContext(ACTOR, "statecraft:states", "state create <nation>",
                Map.of("nation", "n44"), new FormQuery("nation", "", 20)));
        builder.choice("nation", "Nation", "", choices, "", List.of(), false);
        FormField field = builder.build().field("nation").orElseThrow();
        assertEquals(20, field.choices().size());
        assertEquals("n20", field.choices().get(0).value());
        assertEquals(20, field.offset());
        assertTrue(field.more());
        assertEquals("n44", field.value());
        assertEquals("Nation 044", field.selectedLabel());

        FormBuilder searched = new FormBuilder(new FormContext(ACTOR, "statecraft:states", "state create <nation>",
                Map.of("nation", "n44"), new FormQuery("nation", "n42", 0)));
        searched.choice("nation", "Nation", "", choices, "", List.of(), false);
        assertEquals(List.of(choices.get(42)), searched.build().field("nation").orElseThrow().choices());
        assertEquals("n44", searched.value("nation"));
    }

    @Test
    void boundsAndUnknownFieldsAreRejected() {
        assertThrows(UserError.class, () -> new FormContext(ACTOR, "statecraft:states", "state create <name>",
                Map.of("admin", "true"), FormQuery.INITIAL));
        assertThrows(UserError.class, () -> new FormQuery("nation", "x".repeat(81), 0));
        assertThrows(UserError.class, () -> new FormQuery("nation", "", -1));
        assertThrows(UserError.class, () -> new FormContext(ACTOR, "statecraft:states", "x <a> <b> <c>",
                Map.of("a", "x".repeat(2048), "b", "x".repeat(2048), "c", "x"), FormQuery.INITIAL));
        FormField invalid = new FormField("name", "Name", FormField.Kind.TEXT, "", "", "",
                List.of("missing"), false, List.of(), 0, false);
        assertThrows(IllegalArgumentException.class, () -> new FormSchema(List.of(invalid)));
    }

    @Test
    void numericAndLocationDefaultsDoNotInventPaymentAmounts() {
        FormBuilder builder = new FormBuilder(new FormContext(ACTOR, "statecraft:claims",
                "example <page> <radius> <quantity> <chunk> <amount>", Map.of(), FormQuery.INITIAL));
        assertEquals("1", builder.value("page"));
        assertEquals("3", builder.value("radius"));
        assertEquals("1", builder.value("quantity"));
        assertEquals("here", builder.value("chunk"));
        assertEquals("", builder.value("amount"));
    }

    @Test
    void editableSelectorsPreserveLongCustomValuesWithoutWeakeningClosedSelectors() {
        FormBuilder builder = new FormBuilder(new FormContext(ACTOR, "statecraft:contracts",
                "test <chunks> <governor>", Map.of(), FormQuery.INITIAL));
        builder.choice("chunks", "Chunks", "", List.of(new FormChoice("here", "Current chunk")),
                "here", List.of(), true);
        builder.choice("governor", "Governor", "", List.of(new FormChoice("citizen", "Citizen")),
                "citizen", List.of(), false);
        FormState state = new FormState();
        state.apply(builder.build(), Map.of());
        String longValue = "minecraft:overworld|1|2,".repeat(25);
        state.selectCustom("chunks", longValue);
        assertEquals(longValue, state.value("chunks"));
        assertTrue(state.label("chunks").length() <= 128);
        FormContext.validateValues("test <chunks> <governor>", state.values());
        assertThrows(UserError.class, () -> state.selectCustom("governor", "unauthorized"));
    }

    private static FormBuilder stateForm(Map<String, String> values, FormQuery query) {
        FormBuilder builder = new FormBuilder(new FormContext(ACTOR, "statecraft:states",
                "state create <nation> <name> <governor>", values, query));
        builder.choice("nation", "Nation", "Managed nations only",
                List.of(new FormChoice("nation-a", "Nation A"), new FormChoice("nation-b", "Nation B")),
                "nation-b", List.of(), false);
        List<FormChoice> governors = builder.value("nation").equals("nation-b")
                ? List.of(new FormChoice("citizen-b", "Citizen B")) : List.of(new FormChoice("citizen-a", "Citizen A"));
        builder.choice("governor", "Governor", "Eligible citizens of the selected nation",
                governors, "", List.of("nation"), false);
        return builder;
    }
}
