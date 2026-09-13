package dev.statecraft.client.state;

import dev.statecraft.api.form.FormField;
import java.util.ArrayList;
import java.util.List;

public final class FormPages {
    public static final int TOP = 51;
    public static final int FOOTER_HEIGHT = 91;
    public record Slot(int index, int labelY, int inputY, int inputHeight, int messageY) {}
    public record Page(int start, int end, List<Slot> slots) {}
    private final List<Page> pages;

    public FormPages(List<FormField> fields, int height) {
        int bottom = Math.max(TOP + 54, height - FOOTER_HEIGHT);
        var result = new ArrayList<Page>();
        int index = 0;
        while (index < fields.size()) {
            int start = index;
            int y = TOP;
            var slots = new ArrayList<Slot>();
            while (index < fields.size()) {
                int inputHeight = fields.get(index).kind() == FormField.Kind.MULTILINE ? 42 : 20;
                int block = inputHeight + 32;
                if (!slots.isEmpty() && y + block > bottom) break;
                inputHeight = Math.min(inputHeight, Math.max(20, bottom - y - 32));
                slots.add(new Slot(index, y, y + 12, inputHeight, y + 15 + inputHeight));
                y += inputHeight + 32;
                index++;
            }
            result.add(new Page(start, index, List.copyOf(slots)));
        }
        if (result.isEmpty()) result.add(new Page(0, 0, List.of()));
        pages = List.copyOf(result);
    }

    public List<Page> pages() { return pages; }
    public int pageContaining(int fieldIndex) {
        for (int i = 0; i < pages.size(); i++) {
            if (fieldIndex < pages.get(i).end()) return i;
        }
        return pages.size() - 1;
    }
    public Page page(int index) { return pages.get(Math.max(0, Math.min(pages.size() - 1, index))); }
}
