package dev.compatmanager.legacy.platform;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.*;

/**
 * Reads mod metadata from the Forge 1.7.10 – 1.12.2 FML loader.
 */
public class LegacyForgeModPlatform {

    public List<LegacyModInfo> getLoadedMods() {
        List<LegacyModInfo> result = new ArrayList<>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            result.add(fromContainer(mod));
        }
        return result;
    }

    public boolean isModLoaded(String modId) {
        return Loader.isModLoaded(modId);
    }

    public Optional<LegacyModInfo> getMod(String modId) {
        return getLoadedMods().stream()
                .filter(m -> m.modId().equalsIgnoreCase(modId))
                .findFirst();
    }

    public String getMinecraftVersion() {
        return Loader.MC_VERSION;
    }

    public String getForgeVersion() {
        ModContainer forge = Loader.instance().getIndexedModList().get("Forge");
        return forge != null ? forge.getVersion() : "unknown";
    }

    private LegacyModInfo fromContainer(ModContainer mc) {
        List<LegacyDepInfo> deps = new ArrayList<>();

        // Forge 1.12.2 provides getDependencies() as a list of ModContainer
        for (ModContainer dep : mc.getDependencies()) {
            deps.add(new LegacyDepInfo(dep.getModId(), "*", LegacyDepInfo.Kind.REQUIRED));
        }
        // Also check @Mod dependencies annotation string (parsed separately)
        String depString = mc.getDependencyString();
        if (depString != null) {
            parseLegacyDependencyString(depString, deps);
        }

        return new LegacyModInfo(
                mc.getModId(),
                mc.getVersion(),
                mc.getName(),
                deps
        );
    }

    /**
     * Parses the legacy Forge dependency string format:
     *   "required-after:modid;required-before:modid@[1.0,2.0)"
     */
    private void parseLegacyDependencyString(String depStr, List<LegacyDepInfo> out) {
        if (depStr == null || depStr.isBlank()) return;
        for (String token : depStr.split(";")) {
            token = token.trim();
            if (token.isEmpty()) continue;

            LegacyDepInfo.Kind kind = LegacyDepInfo.Kind.OPTIONAL;
            if (token.startsWith("required-after:") || token.startsWith("required-before:")) {
                kind = LegacyDepInfo.Kind.REQUIRED;
                token = token.substring(token.indexOf(':') + 1);
            } else if (token.startsWith("after:") || token.startsWith("before:")) {
                token = token.substring(token.indexOf(':') + 1);
            }

            String[] parts = token.split("@", 2);
            String modId  = parts[0].trim();
            String range  = parts.length > 1 ? parts[1].trim() : "*";
            if (!modId.isEmpty() && !modId.equals("*") && !modId.equalsIgnoreCase("Forge")) {
                out.add(new LegacyDepInfo(modId, range, kind));
            }
        }
    }
}
