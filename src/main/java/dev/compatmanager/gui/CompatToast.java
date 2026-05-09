package dev.compatmanager.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

public class CompatToast implements Toast {

    private final Text title;
    private final Text message;
    private long startTime;
    private boolean initialized = false;

    public CompatToast(Text title, Text message) {
        this.title = title;
        this.message = message;
    }

    @Override
    public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        if (!initialized) {
            this.startTime = startTime;
            initialized = true;
        }

        context.drawGuiTexture(TEXTURE, 0, 0, getWidth(), getHeight());

        TextRenderer tr = manager.getClient().textRenderer;
        context.drawText(tr, title, 18, 7, 0xFFFFFF, false);
        context.drawText(tr, message, 18, 18, 0xFF5555, false);

        // Show for 5 seconds
        return startTime - this.startTime < 5000L ? Visibility.SHOW : Visibility.HIDE;
    }
}
