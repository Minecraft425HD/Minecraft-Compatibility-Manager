package dev.compatmanager.gui;

import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.platform.Services;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CompatManagerScreen extends Screen {

    private static final int HEADER = 48;
    private static final int FOOTER = 38;

    private final Screen parent;
    private IssueListWidget list;
    private List<CompatibilityIssue> issues = List.of();
    private boolean scanning = false;

    public CompatManagerScreen(Screen parent) {
        super(Component.translatable("compatmanager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new IssueListWidget(this, minecraft, width, height, HEADER, height - FOOTER);
        addWidget(list);

        addRenderableWidget(Button.builder(
                        Component.translatable("compatmanager.button.scan"),
                        btn -> startScan())
                .bounds(width / 2 - 112, height - FOOTER + 9, 104, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("compatmanager.button.close"),
                        btn -> onClose())
                .bounds(width / 2 + 8, height - FOOTER + 9, 104, 20)
                .build());

        // Load cached results if available
        if (CompatibilityScanner.getInstance().isScanComplete()) {
            issues = CompatibilityScanner.getInstance().getLastResults();
            list.setIssues(issues);
        }
    }

    private void startScan() {
        scanning = true;
        new Thread(() -> {
            issues = CompatibilityScanner.getInstance().scan();
            minecraft.execute(() -> {
                list.setIssues(issues);
                scanning = false;
            });
        }, "CompatManager-Scan").start();
    }

    public void openDetail(CompatibilityIssue issue) {
        minecraft.setScreen(new IssueDetailScreen(this, issue));
    }

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float delta) {
        renderBackground(gfx);

        // Header / footer
        gfx.fill(0, 0, width, HEADER, 0xCC000000);
        gfx.fill(0, height - FOOTER, width, height, 0xCC000000);

        // Title
        gfx.drawCenteredString(font,
                Component.translatable("compatmanager.title"), width / 2, 8, 0xFFFFFF);

        // Platform badge
        String badge = Services.PLATFORM.getName() + " · MC " + Services.PLATFORM.getMinecraftVersion();
        gfx.drawCenteredString(font, badge, width / 2, 20, 0x888888);

        // Status line
        Component status;
        if (scanning) {
            status = Component.translatable("compatmanager.scanning");
        } else if (issues.isEmpty() && CompatibilityScanner.getInstance().isScanComplete()) {
            status = Component.translatable("compatmanager.no_issues");
        } else if (!issues.isEmpty()) {
            status = Component.translatable("compatmanager.issues_found", issues.size());
        } else {
            status = Component.translatable("compatmanager.subtitle");
        }
        gfx.drawCenteredString(font, status, width / 2, 34, 0xAAAAAA);

        list.render(gfx, mx, my, delta);
        super.render(gfx, mx, my, delta);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
