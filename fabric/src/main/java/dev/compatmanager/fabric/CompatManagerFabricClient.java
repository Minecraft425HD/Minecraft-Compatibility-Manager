package dev.compatmanager.fabric;

import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import dev.compatmanager.gui.CompatToast;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

public class CompatManagerFabricClient implements ClientModInitializer {

    private static KeyMapping openManagerKey;

    @Override
    public void onInitializeClient() {
        openManagerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "compatmanager.key.open_manager",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.misc"
        ));

        // Show toast after startup scan finishes
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            Thread t = new Thread(() -> {
                while (!CompatibilityScanner.getInstance().isScanComplete()) {
                    try { Thread.sleep(250); } catch (InterruptedException e) { break; }
                }
                int n = CompatibilityScanner.getInstance().getLastResults().size();
                if (n > 0) {
                    mc.execute(() -> mc.getToasts().addToast(new CompatToast(
                            Component.translatable("compatmanager.toast.scan_complete"),
                            Component.translatable("compatmanager.toast.issues", n)
                    )));
                }
            }, "CompatManager-ToastWait");
            t.setDaemon(true);
            t.start();
        });

        // Keybind handler
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openManagerKey.consumeClick()) {
                mc.setScreen(new CompatManagerScreen(mc.screen));
            }
        });
    }
}
