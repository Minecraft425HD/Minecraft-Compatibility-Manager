package dev.compatmanager.quilt;

import dev.compatmanager.CompatManagerCommon;
import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import dev.compatmanager.gui.CompatToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;
import org.quiltmc.qkl.library.client.input.KeyMappingBuilder;

public class CompatManagerQuilt implements ModInitializer, ClientModInitializer {

    public static KeyMapping openManagerKey;

    @Override
    public void onInitialize(ModContainer mod) {
        CompatManagerCommon.init();
    }

    @Override
    public void onInitializeClient(ModContainer mod) {
        openManagerKey = new KeyMapping(
                "compatmanager.key.open_manager",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_F8,
                "key.categories.misc"
        );

        // Toast on scan complete
        Thread t = new Thread(() -> {
            while (!CompatibilityScanner.getInstance().isScanComplete()) {
                try { Thread.sleep(250); } catch (InterruptedException e) { break; }
            }
            int n = CompatibilityScanner.getInstance().getLastResults().size();
            if (n > 0) {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.getToasts().addToast(new CompatToast(
                        Component.translatable("compatmanager.toast.scan_complete"),
                        Component.translatable("compatmanager.toast.issues", n)
                )));
            }
        }, "CompatManager-ToastWait");
        t.setDaemon(true);
        t.start();
    }
}
