package dev.statecraft.runtime;

import dev.statecraft.api.ui.ActionDisplay;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.UiText;
import java.util.Locale;

final class OperationPresentation {
    private OperationPresentation() {}

    static String result(UiOperations.Receipt receipt, long savedRevision) {
        ActionOutcome outcome = receipt.outcome(savedRevision);
        return outcome.uncertain()
                ? "This action is not confirmed. Do not repeat it; check its status or ask an operator for help."
                : ActionDisplay.feedback(receipt.intent(), receipt.summary(), outcome, receipt.text());
    }

    static String row(UiOperations.Receipt receipt, long savedRevision, boolean diagnostics) {
        return ActionDisplay.outcome(receipt.outcome(savedRevision)) + " | "
                + DisplayText.name(receipt.ownerName(), "Former player") + " | " + DisplayText.date(receipt.createdAt())
                + (diagnostics ? "\nOperation: " + receipt.operation().id() : "");
    }

    static UiText body(UiOperations.Receipt receipt, long savedRevision, boolean diagnostics) {
        String[] parts = {receipt.summary(), DisplayText.name(receipt.ownerName(), "Former player"),
                ActionDisplay.outcome(receipt.outcome(savedRevision)), DisplayText.date(receipt.createdAt()),
                result(receipt, savedRevision)};
        String template = "%s\nPlayer: %s\nStatus: %s\nCreated: %s\n%s";
        String text = String.format(Locale.ROOT, template, (Object[]) parts);
        return diagnostics ? UiText.literal(text + "\nOperation: " + receipt.operation().id()
                + "\nRecorded result:\n" + receipt.text())
                : UiText.tr("ui.statecraft.runtime.operations.body", text, parts);
    }
}
