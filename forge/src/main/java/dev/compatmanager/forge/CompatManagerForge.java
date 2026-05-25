package dev.compatmanager.forge;

import dev.compatmanager.CompatManagerCommon;
import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.gui.CompatManagerScreen;
import dev.compatmanager.gui.CompatToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@Mod("compatmanager")
public class CompatManagerForge {

    public static KeyMapping openManagerKey;

    public CompatManagerForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        CompatManagerCommon.init();
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Toast when scan finishes
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
                KeyConflictContext.IN_GAME,
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
