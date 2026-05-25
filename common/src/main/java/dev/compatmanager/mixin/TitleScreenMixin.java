package dev.compatmanager.mixin;

import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.Screen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() { super(Component.empty()); }

    @Inject(at = @At("TAIL"), method = "init")
    private void onInit(CallbackInfo ci) {
        int n = CompatibilityScanner.getInstance().getLastResults().size();
        String label = n > 0 ? "⚠ Compat (" + n + ")" : "Compat Manager";

        addRenderableWidget(Button.builder(
                        Component.literal(label),
                        btn -> minecraft.setScreen(new CompatManagerScreen(this)))
                .bounds(this.width - 114, 4, 110, 20)
                .build());
    }
}
