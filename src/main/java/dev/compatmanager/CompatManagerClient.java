package dev.compatmanager;

import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class CompatManagerClient implements ClientModInitializer {

    private static KeyBinding openManagerKey;

    @Override
    public void onInitializeClient() {
        openManagerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "compatmanager.key.open_manager",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.misc"
        ));

        // Show toast notification when scan finds issues
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // Wait for initial scan
            new Thread(() -> {
                while (!CompatibilityScanner.getInstance().isScanComplete()) {
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                }
                int count = CompatibilityScanner.getInstance().getLastResults().size();
                if (count > 0) {
                    client.execute(() -> showIssueToast(client, count));
                }
            }, "CompatManager-ToastWait").start();
        });

        // Handle keybind every tick
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openManagerKey.wasPressed()) {
                client.setScreen(new CompatManagerScreen(client.currentScreen));
            }
        });
    }

    private void showIssueToast(MinecraftClient client, int count) {
        client.getToastManager().add(
                new dev.compatmanager.gui.CompatToast(
                        Text.translatable("compatmanager.toast.scan_complete"),
                        Text.translatable("compatmanager.toast.issues", count)
                )
        );
    }
}
