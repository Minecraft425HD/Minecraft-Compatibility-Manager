package dev.compatmanager.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

public class CompatToast implements Toast {

    private final Component title;
    private final Component body;

    public CompatToast(Component title, Component body) {
        this.title = title;
        this.body  = body;
    }

    @Override
    public Visibility render(GuiGraphics gfx, ToastComponent tc, long elapsed) {
        gfx.blitSprite(BACKGROUND_SPRITE, 0, 0, width(), height());
        gfx.drawString(tc.getMinecraft().font, title, 18, 7,  0xFFFFFF, false);
        gfx.drawString(tc.getMinecraft().font, body,  18, 18, 0xFF5555, false);
        return elapsed < 5_000L ? Visibility.SHOW : Visibility.HIDE;
    }
}
