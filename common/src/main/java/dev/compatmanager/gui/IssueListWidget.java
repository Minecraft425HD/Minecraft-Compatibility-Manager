package dev.compatmanager.gui;

import dev.compatmanager.api.CompatibilityIssue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public class IssueListWidget extends ObjectSelectionList<IssueListWidget.IssueEntry> {

    private final CompatManagerScreen parent;

    public IssueListWidget(CompatManagerScreen parent, Minecraft mc,
                           int width, int height, int top, int bottom) {
        super(mc, width, height, top, bottom, 42);
        this.parent = parent;
    }

    public void setIssues(List<CompatibilityIssue> issues) {
        clearEntries();
        issues.forEach(i -> addEntry(new IssueEntry(i)));
    }

    @Override protected int getScrollbarPosition() { return getRight() - 6; }
    @Override public int getRowWidth()             { return width - 12; }

    // ── Entry ──────────────────────────────────────────────────────────────

    public class IssueEntry extends ObjectSelectionList.Entry<IssueEntry> {

        private final CompatibilityIssue issue;

        IssueEntry(CompatibilityIssue issue) { this.issue = issue; }

        @Override
        public void render(GuiGraphics gfx, int index, int top, int left,
                           int width, int height, int mx, int my,
                           boolean hovered, float partial) {

            // Row background
            gfx.fill(left, top, left + width, top + height - 2,
                    hovered ? 0x80505050 : 0x60202020);

            // Severity stripe on the left
            gfx.fill(left, top, left + 4, top + height - 2, issue.getSeverity().argb);

            // Mod-type title
            String title = Component.translatable(issue.getType().translationKey()).getString();
            gfx.drawString(minecraft.font, title, left + 8, top + 3, 0xFFFFFF, true);

            // Severity badge (right-aligned)
            String sev = "[" + Component.translatable(
                    issue.getSeverity().translationKey()).getString() + "]";
            gfx.drawString(minecraft.font, sev,
                    left + width - minecraft.font.width(sev) - 6,
                    top + 3, issue.getSeverity().argb, true);

            // Affected mods preview
            String mods = String.join(", ", issue.getAffectedMods());
            if (mods.length() > 52) mods = mods.substring(0, 49) + "…";
            gfx.drawString(minecraft.font, mods, left + 8, top + 15, 0xAAAAAA, false);

            // Solution hint
            if (!issue.getSolutions().isEmpty()) {
                gfx.drawString(minecraft.font,
                        issue.getSolutions().size() + " fix(es) available →",
                        left + 8, top + 27, 0x55FF55, false);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) { parent.openDetail(issue); return true; }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.translatable(issue.getType().translationKey());
        }
    }
}
