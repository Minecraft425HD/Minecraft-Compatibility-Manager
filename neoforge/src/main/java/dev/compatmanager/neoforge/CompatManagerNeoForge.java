package dev.compatmanager.neoforge;

import dev.compatmanager.CompatManagerCommon;
import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import dev.compatmanager.gui.CompatToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Mod("compatmanager")
public class CompatManagerNeoForge {

    public static KeyMapping openManagerKey;

    public CompatManagerNeoForge(IEventBus modBus) {
        modBus.addListener(this::setup);
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        CompatManagerCommon.init();
    }

    private void clientSetup(FMLClientSetupEvent event) {
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

    private void registerKeys(RegisterKeyMappingsEvent event) {
        openManagerKey = new KeyMapping(
                "compatmanager.key.open_manager",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.misc"
        );
        event.register(openManagerKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (openManagerKey != null && openManagerKey.consumeClick()) {
            mc.setScreen(new CompatManagerScreen(mc.screen));
        }
    }
}
