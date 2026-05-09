package dev.compatmanager.fabric;

import dev.compatmanager.CompatManagerCommon;
import net.fabricmc.api.ModInitializer;

public class CompatManagerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CompatManagerCommon.init();
    }
}
