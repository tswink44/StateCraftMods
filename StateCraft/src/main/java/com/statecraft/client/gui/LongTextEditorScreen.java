package com.statecraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for editing long-form text content (like custom laws)
 * Features a large scrollable text area
 */
public class LongTextEditorScreen extends StateCraftScreen {

    private final String fieldName;
    private final Consumer<String> onSave;
    private final Runnable onCancel;

    private EditBox textField;
    private List<String> wrappedLines = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int VISIBLE_LINES = 12;
    private static final int LINE_HEIGHT = 11;

    // Multi-line editing - we store full text and display wrapped
    private StringBuilder fullText;

    public LongTextEditorScreen(String fieldName, String initialText, Consumer<String> onSave, Runnable onCancel) {
        super(Component.literal("Edit: " + fieldName));
        this.fieldName = fieldName;
        this.onSave = onSave;
        this.onCancel = onCancel;
        this.fullText = new StringBuilder(initialText != null ? initialText : "");
        this.guiWidth = 320;
        this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Main text field (single EditBox, but we render it specially)
        this.textField = new EditBox(this.font, guiLeft + 15, guiTop + 35, guiWidth - 30, 16, Component.literal("Text"));
        this.textField.setMaxLength(2000);
        this.textField.setValue(fullText.toString());
        this.textField.setResponder(this::onTextChanged);
        this.addRenderableWidget(this.textField);

        // Save button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 90, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("§aSave"),
            btn -> save()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 10, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Cancel"),
            btn -> cancel()
        ));

        // Update wrapped lines
        updateWrappedLines();
    }

    private void onTextChanged(String text) {
        this.fullText = new StringBuilder(text);
        updateWrappedLines();
    }

    private void updateWrappedLines() {
        wrappedLines.clear();
        String text = fullText.toString();

        if (text.isEmpty()) {
            return;
        }

        int maxWidth = guiWidth - 40;

        // Split by newlines first
        String[] paragraphs = text.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                wrappedLines.add("");
                continue;
            }

            // Wrap each paragraph
            StringBuilder currentLine = new StringBuilder();
            String[] words = paragraph.split(" ");
            for (String word : words) {
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                if (this.font.width(testLine) > maxWidth) {
                    if (currentLine.length() > 0) {
                        wrappedLines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        // Word is too long, force break
                        wrappedLines.add(word);
                    }
                } else {
                    currentLine = new StringBuilder(testLine);
                }
            }
            if (currentLine.length() > 0) {
                wrappedLines.add(currentLine.toString());
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Field name label
        graphics.drawString(this.font, "§7" + fieldName + ":", guiLeft + 15, guiTop + 26, COLOR_TEXT);

        // Text preview area (below the edit box)
        int previewY = guiTop + 55;
        int previewHeight = guiHeight - 95;

        // Background for preview area
        graphics.fill(guiLeft + 12, previewY - 2, guiLeft + guiWidth - 12, previewY + previewHeight, 0x88000000);

        // Enable scissoring for scroll area
        graphics.enableScissor(guiLeft + 12, previewY, guiLeft + guiWidth - 12, previewY + previewHeight - 4);

        // Render wrapped lines
        int y = previewY;
        int startLine = scrollOffset;
        int endLine = Math.min(wrappedLines.size(), scrollOffset + VISIBLE_LINES);

        for (int i = startLine; i < endLine; i++) {
            graphics.drawString(this.font, wrappedLines.get(i), guiLeft + 15, y, 0xFFCCCCCC);
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 20, previewY, 0xFF888888);
        }
        if (scrollOffset + VISIBLE_LINES < wrappedLines.size()) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 20, previewY + previewHeight - 12, 0xFF888888);
        }

        // Character count
        int charCount = fullText.length();
        String countText = charCount + "/2000";
        int countColor = charCount > 1800 ? 0xFFFF6666 : 0xFF888888;
        graphics.drawString(this.font, countText, guiLeft + guiWidth - 60, guiTop + guiHeight - 42, countColor);

        // Instructions
        graphics.drawString(this.font, "§8Type in the field above. Preview shown below.", guiLeft + 15, guiTop + guiHeight - 42, 0xFF666666);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, wrappedLines.size() - VISIBLE_LINES);
        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 2);
        } else {
            scrollOffset = Math.min(maxScroll, scrollOffset + 2);
        }
        return true;
    }

    private void save() {
        onSave.accept(fullText.toString());
    }

    private void cancel() {
        onCancel.run();
    }

    @Override
    public void onClose() {
        cancel();
    }
}

