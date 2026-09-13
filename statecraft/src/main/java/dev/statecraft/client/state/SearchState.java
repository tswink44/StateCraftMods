package dev.statecraft.client.state;

public final class SearchState {
    private String text = "";
    private int offset;
    private EditSelection selection = EditSelection.end("");

    public String text() { return text; }
    public int offset() { return offset; }
    public EditSelection selection() { return selection.bounded(text); }
    public void text(String value) {
        if (text.equals(value)) return;
        text = value;
        offset = 0;
    }
    public void offset(int value) { offset = Math.max(0, value); }
    public void selection(EditSelection value) { selection = value.bounded(text); }
    public SearchState copy() {
        SearchState copy = new SearchState();
        copy.text = text;
        copy.offset = offset;
        copy.selection = selection;
        return copy;
    }
}
