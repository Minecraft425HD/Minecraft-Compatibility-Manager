package dev.compatmanager.gui;

import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.Solution;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IssueDetailScreen extends Screen {

    private final Screen parent;
    private final CompatibilityIssue issue;

    public IssueDetailScreen(Screen parent, CompatibilityIssue issue) {
        super(Component.translatable(issue.getType().translationKey()));
        this.parent = parent;
        this.issue  = issue;
    }

    @Override
    protected void init() {
        // Back
        addRenderableWidget(Button.builder(
                        Component.translatable("compatmanager.button.back"),
                        btn -> minecraft.setScreen(parent))
                .bounds(8, 8, 80, 20).build());

        // Copy technical detail
        if (!issue.getTechnicalDetail().isBlank()) {
            addRenderableWidget(Button.builder(
                            Component.translatable("compatmanager.button.copy"),
                            btn -> minecraft.keyboardHandler.setClipboard(issue.getTechnicalDetail()))
                    .bounds(width - 132, 8, 124, 20).build());
        }

        // Solution buttons
        List<Solution> solutions = issue.getSolutions();
        int y = computeSolutionStartY();
        for (int i = 0; i < solutions.size(); i++) {
            Solution sol = solutions.get(i);
            String raw = Component.translatable(sol.getDescriptionKey(),
                    (Object[]) sol.getDescriptionArgs()).getString();
            String label = (i + 1) + ". " + (raw.length() > 46 ? raw.substring(0, 43) + "…" : raw);

            Button btn = Button.builder(
                            Component.literal(label),
                            b -> applySolution(sol))
                    .bounds(width / 2 - 152, y + i * 24, 304, 20)
                    .build();
            addRenderableWidget(btn);
        }
    }

    private int computeSolutionStartY() {
        int y = 44;
        y += 14 * (issue.getAffectedMods().size() + 2);
        y += wrapText(issue.getTechnicalDetail(), width - 40).size() * 12 + 18;
        return Math.min(y, height - issue.getSolutions().size() * 24 - 10);
    }

    private void applySolution(Solution sol) {
        switch (sol.getActionType()) {
            case AUTO_FIX -> { sol.apply(); minecraft.setScreen(parent); }
            case OPEN_URL -> sol.getActionPayload().ifPresent(url ->
                    Util.getPlatform().openUri(url));
            case COPY_TEXT -> sol.getActionPayload().ifPresent(txt ->
                    minecraft.keyboardHandler.setClipboard(txt));
            case OPEN_CONFIG -> sol.getActionPayload().ifPresent(path ->
                    Util.getPlatform().openFile(new File(path)));
            default -> { /* MANUAL_INSTRUCTION — no action, just display */ }
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float delta) {
        renderBackground(gfx);
        gfx.fill(0, 0, width, 36, 0xCC000000);

        // Header: type + severity
        String header = Component.translatable(issue.getType().translationKey()).getString()
                + "  [" + Component.translatable(issue.getSeverity().translationKey()).getString() + "]";
        gfx.drawCenteredString(font, header, width / 2, 13, issue.getSeverity().argb);

        int y = 44;

        // Affected mods
        gfx.drawString(font,
                Component.translatable("compatmanager.detail.affected_mods"), 16, y, 0xFFFFFF, true);
        y += 13;
        for (String mod : issue.getAffectedMods()) {
            gfx.drawString(font, "• " + mod, 26, y, 0xCCCCCC, false);
            y += 12;
        }
        y += 6;

        // Technical description
        if (!issue.getTechnicalDetail().isBlank()) {
            gfx.drawString(font,
                    Component.translatable("compatmanager.detail.description"), 16, y, 0xFFFFFF, true);
            y += 13;
            for (String line : wrapText(issue.getTechnicalDetail(), width - 40)) {
                gfx.drawString(font, line, 26, y, 0xAAAAAA, false);
                y += 12;
            }
            y += 6;
        }

        // Solutions header
        if (!issue.getSolutions().isEmpty()) {
            gfx.drawString(font,
                    Component.translatable("compatmanager.detail.solutions"), 16, y, 0xFFFFFF, true);
        }

        super.render(gfx, mx, my, delta);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(candidate) > maxWidth) {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
