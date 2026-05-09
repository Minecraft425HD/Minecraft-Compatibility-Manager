package dev.compatmanager.gui;

import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.Solution;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class IssueDetailScreen extends Screen {

    private final Screen parent;
    private final CompatibilityIssue issue;
    private final List<ButtonWidget> solutionButtons = new ArrayList<>();

    public IssueDetailScreen(Screen parent, CompatibilityIssue issue) {
        super(Text.translatable(issue.getType().translationKey()));
        this.parent = parent;
        this.issue = issue;
    }

    @Override
    protected void init() {
        solutionButtons.clear();

        // Back button
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("compatmanager.button.back"),
                        btn -> client.setScreen(parent))
                .dimensions(8, 8, 80, 20)
                .build());

        // Copy technical detail button
        if (!issue.getTechnicalDetail().isEmpty()) {
            addDrawableChild(ButtonWidget.builder(
                            Text.translatable("compatmanager.button.copy"),
                            btn -> client.keyboard.setClipboard(issue.getTechnicalDetail()))
                    .dimensions(width - 130, 8, 120, 20)
                    .build());
        }

        // Solution buttons
        List<Solution> solutions = issue.getSolutions();
        int startY = 160;
        for (int i = 0; i < solutions.size(); i++) {
            Solution sol = solutions.get(i);
            int finalI = i;
            String label = Text.translatable(sol.getDescriptionKey(),
                    (Object[]) sol.getDescriptionArgs()).getString();
            if (label.length() > 45) label = label.substring(0, 42) + "...";

            ButtonWidget btn = ButtonWidget.builder(
                            Text.literal((finalI + 1) + ". " + label),
                            b -> applySolution(sol))
                    .dimensions(width / 2 - 150, startY + i * 24, 300, 20)
                    .build();

            // Only auto-fixable solutions are active; others are informational
            btn.active = sol.canAutoApply() || sol.getActionType() == Solution.ActionType.OPEN_URL
                    || sol.getActionType() == Solution.ActionType.COPY_COMMAND;

            solutionButtons.add(btn);
            addDrawableChild(btn);
        }
    }

    private void applySolution(Solution sol) {
        switch (sol.getActionType()) {
            case AUTO_FIX -> {
                sol.apply();
                client.setScreen(parent);
            }
            case OPEN_URL -> sol.getActionPayload().ifPresent(url ->
                    net.minecraft.util.Util.getOperatingSystem().open(url));
            case COPY_COMMAND -> sol.getActionPayload().ifPresent(cmd ->
                    client.keyboard.setClipboard(cmd));
            case OPEN_CONFIG -> sol.getActionPayload().ifPresent(path ->
                    net.minecraft.util.Util.getOperatingSystem().open(
                            new java.io.File(path)));
            default -> { /* MANUAL_INSTRUCTION — just display it */ }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Header
        context.fill(0, 0, width, 36, 0xCC000000);
        String typeName = Text.translatable(issue.getType().translationKey()).getString();
        String sevLabel = "[" + Text.translatable(issue.getSeverity().translationKey()).getString() + "]";
        context.drawCenteredTextWithShadow(textRenderer, typeName + " " + sevLabel,
                width / 2, 12, issue.getSeverity().color.getColorValue() | 0xFF000000);

        int y = 44;

        // Affected mods
        context.drawText(textRenderer,
                Text.translatable("compatmanager.detail.affected_mods"), 16, y, 0xFFFFFF, true);
        y += 12;
        for (String modId : issue.getAffectedMods()) {
            context.drawText(textRenderer, "• " + modId, 24, y, 0xCCCCCC, false);
            y += 11;
        }

        y += 8;

        // Technical detail
        if (!issue.getTechnicalDetail().isEmpty()) {
            context.drawText(textRenderer,
                    Text.translatable("compatmanager.detail.description"), 16, y, 0xFFFFFF, true);
            y += 12;
            // Word-wrap technical detail
            List<String> lines = wrapText(issue.getTechnicalDetail(), width - 40);
            for (String line : lines) {
                context.drawText(textRenderer, line, 24, y, 0xAAAAAA, false);
                y += 11;
            }
        }

        y += 8;

        // Solutions header
        if (!issue.getSolutions().isEmpty()) {
            context.drawText(textRenderer,
                    Text.translatable("compatmanager.detail.solutions"), 16, y, 0xFFFFFF, true);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) > maxWidth) {
                if (!current.isEmpty()) result.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
