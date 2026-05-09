package dev.compatmanager.mixin;

import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends net.minecraft.client.gui.screen.Screen {

    protected TitleScreenMixin() {
        super(Text.empty());
    }

    @Inject(at = @At("TAIL"), method = "init")
    private void onInit(CallbackInfo ci) {
        int issueCount = CompatibilityScanner.getInstance().getLastResults().size();
        String label = issueCount > 0
                ? "⚠ Compat (" + issueCount + ")"
                : "Compat Manager";

        int btnColor = issueCount > 0 ? 0xFF5555 : 0x55FF55;

        ButtonWidget button = ButtonWidget.builder(
                        Text.literal(label),
                        btn -> client.setScreen(new CompatManagerScreen(this)))
                .dimensions(this.width - 112, 4, 108, 20)
                .build();

        this.addDrawableChild(button);
    }
}
