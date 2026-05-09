package dev.compatmanager.gui;

import dev.compatmanager.api.CompatibilityIssue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.text.Text;

import java.util.List;

public class IssueListWidget extends EntryListWidget<IssueListWidget.IssueEntry> {

    private final CompatManagerScreen parent;

    public IssueListWidget(CompatManagerScreen parent, MinecraftClient client,
                           int width, int height, int top, int bottom, int itemHeight) {
        super(client, width, height, top, bottom, itemHeight);
        this.parent = parent;
    }

    public void setIssues(List<CompatibilityIssue> issues) {
        clearEntries();
        for (CompatibilityIssue issue : issues) {
            addEntry(new IssueEntry(issue));
        }
    }

    @Override
    public int getRowWidth() { return width - 12; }

    @Override
    protected int getScrollbarPositionX() { return getRight() - 6; }

    public class IssueEntry extends EntryListWidget.Entry<IssueEntry> {

        private final CompatibilityIssue issue;

        public IssueEntry(CompatibilityIssue issue) {
            this.issue = issue;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x,
                           int entryWidth, int entryHeight, int mouseX, int mouseY,
                           boolean hovered, float tickDelta) {
            int bgColor = hovered ? 0x80404040 : 0x60202020;
            context.fill(x, y, x + entryWidth, y + entryHeight - 2, bgColor);

            // Severity badge
            int badgeColor = getSeverityColor(issue.getSeverity().color.getColorValue());
            context.fill(x, y, x + 4, y + entryHeight - 2, badgeColor | 0xFF000000);

            // Issue type title
            String typeText = Text.translatable(issue.getType().translationKey()).getString();
            context.drawText(client.textRenderer, typeText, x + 8, y + 3, 0xFFFFFF, true);

            // Severity label
            String sevText = "[" + Text.translatable(issue.getSeverity().translationKey()).getString() + "]";
            int sevColor = badgeColor | 0xFF000000;
            context.drawText(client.textRenderer, sevText,
                    x + entryWidth - client.textRenderer.getWidth(sevText) - 4, y + 3, sevColor, true);

            // Affected mods preview
            String mods = String.join(", ", issue.getAffectedMods());
            if (mods.length() > 50) mods = mods.substring(0, 47) + "...";
            context.drawText(client.textRenderer, mods, x + 8, y + 14, 0xAAAAAA, false);

            // Solutions count
            if (!issue.getSolutions().isEmpty()) {
                String solutionText = issue.getSolutions().size() + " fix(es) available";
                context.drawText(client.textRenderer, solutionText,
                        x + 8, y + 25, 0x55FF55, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                parent.openDetail(issue);
                return true;
            }
            return false;
        }

        private int getSeverityColor(Integer colorValue) {
            if (colorValue == null) return 0xFFFFFF;
            return colorValue;
        }
    }
}
