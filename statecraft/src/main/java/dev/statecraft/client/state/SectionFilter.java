package dev.statecraft.client.state;

public final class SectionFilter {
    private String text;
    private boolean open;
    private EditSelection selection;

    public SectionFilter(String applied, EditSelection selection) {
        text = applied;
        open = !applied.isEmpty();
        this.selection = selection.bounded(applied);
    }
    public String text() { return text; }
    public void text(String value) { text = value; }
    public boolean open() { return open; }
    public void toggle() { open = !open; }
    public EditSelection selection() { return selection; }
    public void selection(EditSelection value) { selection = value.bounded(text); }
}
