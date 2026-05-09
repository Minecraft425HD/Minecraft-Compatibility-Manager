package dev.compatmanager.gui;

import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.compat.CompatibilityScanner;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class CompatManagerScreen extends Screen {

    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 36;

    private final Screen parent;
    private IssueListWidget issueList;
    private ButtonWidget scanButton;
    private ButtonWidget closeButton;
    private List<CompatibilityIssue> issues = List.of();
    private boolean scanning = false;

    public CompatManagerScreen(Screen parent) {
        super(Text.translatable("compatmanager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop = HEADER_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;

        issueList = new IssueListWidget(this, client, width, height, listTop, listBottom, 40);
        addDrawableChild(issueList);

        scanButton = ButtonWidget.builder(
                        Text.translatable("compatmanager.button.scan"),
                        btn -> startScan())
                .dimensions(width / 2 - 110, height - FOOTER_HEIGHT + 6, 100, 20)
                .build();
        addDrawableChild(scanButton);

        closeButton = ButtonWidget.builder(
                        Text.translatable("compatmanager.button.close"),
                        btn -> close())
                .dimensions(width / 2 + 10, height - FOOTER_HEIGHT + 6, 100, 20)
                .build();
        addDrawableChild(closeButton);

        if (CompatibilityScanner.getInstance().isScanComplete()) {
            issues = CompatibilityScanner.getInstance().getLastResults();
            issueList.setIssues(issues);
        }
    }

    private void startScan() {
        scanning = true;
        scanButton.active = false;
        // Run on render thread but non-blocking feel via flag
        new Thread(() -> {
            issues = CompatibilityScanner.getInstance().scan();
            client.execute(() -> {
                issueList.setIssues(issues);
                scanning = false;
                scanButton.active = true;
            });
        }, "CompatManager-Scan").start();
    }

    public void openDetail(CompatibilityIssue issue) {
        client.setScreen(new IssueDetailScreen(this, issue));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Header background
        context.fill(0, 0, width, HEADER_HEIGHT, 0xCC000000);
        context.fill(0, height - FOOTER_HEIGHT, width, height, 0xCC000000);

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("compatmanager.title"), width / 2, 12, 0xFFFFFF);

        // Subtitle / status
        Text subtitle;
        if (scanning) {
            subtitle = Text.translatable("compatmanager.scanning");
        } else if (issues.isEmpty() && CompatibilityScanner.getInstance().isScanComplete()) {
            subtitle = Text.translatable("compatmanager.no_issues").copy()
                    .styled(s -> s.withColor(0x55FF55));
        } else if (!issues.isEmpty()) {
            subtitle = Text.translatable("compatmanager.issues_found", issues.size()).copy()
                    .styled(s -> s.withColor(issues.stream()
                            .anyMatch(i -> i.getSeverity().priority == 0) ? 0xFF5555 : 0xFFAA00));
        } else {
            subtitle = Text.translatable("compatmanager.button.scan");
        }
        context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, 30, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
