package dev.compatmanager.legacy;

import dev.compatmanager.legacy.platform.LegacyDepInfo;
import dev.compatmanager.legacy.platform.LegacyForgeModPlatform;
import dev.compatmanager.legacy.platform.LegacyModInfo;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@Mod(
    modid   = "compatmanager",
    name    = "Minecraft Compatibility Manager",
    version = "@VERSION@",
    acceptedMinecraftVersions = "[1.7,1.13)",
    dependencies = "required-after:Forge"
)
public class CompatManagerLegacy {

    private static final Logger LOG = LogManager.getLogger("CompatManager");

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOG.info("Compatibility Manager loading on Forge " + net.minecraftforge.common.ForgeVersion.getVersion()
                + " / MC " + net.minecraftforge.fml.common.Loader.MC_VERSION);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Run scan after all mods have initialized
        new Thread(() -> {
            try {
                LegacyForgeModPlatform platform = new LegacyForgeModPlatform();
                List<String> issues = scanForIssues(platform);
                if (issues.isEmpty()) {
                    LOG.info("Compatibility scan complete: no issues found.");
                } else {
                    LOG.warn("Compatibility scan found {} issue(s):", issues.size());
                    issues.forEach(LOG::warn);
                    LOG.warn("Open the in-game Compat Manager screen (F8) for details and fixes.");
                }
            } catch (Exception e) {
                LOG.error("Compatibility scan failed: " + e.getMessage(), e);
            }
        }, "CompatManager-Scan").start();
    }

    private List<String> scanForIssues(LegacyForgeModPlatform platform) {
        List<String> issues = new ArrayList<>();

        for (LegacyModInfo mod : platform.getLoadedMods()) {
            for (LegacyDepInfo dep : mod.dependencies()) {
                if (dep.kind() == LegacyDepInfo.Kind.REQUIRED && !platform.isModLoaded(dep.modId())) {
                    issues.add("[CRITICAL] " + mod.modId() + " requires '"
                            + dep.modId() + "' which is not installed");
                }
                if (dep.kind() == LegacyDepInfo.Kind.INCOMPATIBLE && platform.isModLoaded(dep.modId())) {
                    issues.add("[CRITICAL] " + mod.modId() + " is incompatible with " + dep.modId());
                }
            }
        }

        // Detect duplicate IDs
        Map<String, Long> idCount = new HashMap<>();
        platform.getLoadedMods().forEach(m -> idCount.merge(m.modId(), 1L, Long::sum));
        idCount.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> issues.add("[CRITICAL] Duplicate mod ID: " + e.getKey()
                        + " (" + e.getValue() + " copies)"));

        // Known legacy incompatibilities
        checkKnownPair(platform, issues, "optifine",       "fastcraft",   "HIGH",
                "OptiFine and FastCraft conflict on chunk rendering");
        checkKnownPair(platform, issues, "nei",            "jei",         "HIGH",
                "NEI and JEI both handle recipe display — pick one");
        checkKnownPair(platform, issues, "codechickencore","codechickenlib", "HIGH",
                "CodeChickenCore and CodeChickenLib are different versions of the same library");
        checkKnownPair(platform, issues, "inventorytweaks","inventorysorter", "MEDIUM",
                "InventoryTweaks and InventorySorter both handle inventory sorting");
        checkKnownPair(platform, issues, "optifine",       "shaders",     "MEDIUM",
                "Built-in shader mod detected alongside OptiFine — may conflict");

        return issues;
    }

    private void checkKnownPair(LegacyForgeModPlatform platform, List<String> issues,
                                 String a, String b, String severity, String reason) {
        if (platform.isModLoaded(a) && platform.isModLoaded(b)) {
            issues.add("[" + severity + "] Known incompatibility: " + a + " + " + b + " — " + reason);
        }
    }
}
