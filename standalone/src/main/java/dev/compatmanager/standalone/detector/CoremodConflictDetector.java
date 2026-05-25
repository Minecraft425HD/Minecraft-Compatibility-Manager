package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Detects Forge coremod (ASM transformer) conflicts.
 *
 * Forge coremods declared via FMLCorePlugin in MANIFEST.MF apply raw bytecode
 * transformations before Minecraft classes load. Multiple coremods targeting
 * the same class WILL crash — there is no merging.
 *
 * Also detects JS coremods (1.12.2 META-INF/coremods.json) and
 * modern NeoForge/Forge transformers (META-INF/coremods.json, ITransformer).
 */
public class CoremodConflictDetector implements IssueDetector {

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        List<File>           jars = platform.getJarFiles();

        List<String> legacyCoremods  = new ArrayList<>(); // FMLCorePlugin manifest
        List<String> modernCoremods  = new ArrayList<>(); // coremods.json
        List<String> transformerMods = new ArrayList<>(); // ITransformer implementations

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);

            CoremodInfo info = analyze(jar, modId);
            if (info.isLegacyCoremod())  legacyCoremods.add(modId);
            if (info.hasModernCoremod()) modernCoremods.add(modId);
            if (info.hasTransformer())   transformerMods.add(modId);
        }

        if (legacyCoremods.size() >= 2) {
            issues.add(StandaloneIssue.builder("Legacy Coremod Conflict", Severity.HIGH)
                    .affectedMods(legacyCoremods)
                    .description(legacyCoremods.size() + " mods register legacy FML coremods "
                            + "(FMLCorePlugin in MANIFEST.MF): "
                            + String.join(", ", legacyCoremods) + ". "
                            + "Multiple coremods transforming the same class will crash. "
                            + "These run before Minecraft loads and cannot be safely combined.")
                    .solutions(
                            "Check if all listed coremods are truly necessary",
                            "Look for non-coremod alternatives for each mod",
                            "Test each coremod in isolation to find the conflicting pair"
                    )
                    .build());
        }

        if (modernCoremods.size() >= 2) {
            issues.add(StandaloneIssue.builder("Coremod Transformer Conflict", Severity.MEDIUM)
                    .affectedMods(modernCoremods)
                    .description(modernCoremods.size() + " mods use modern coremods.json transformers: "
                            + String.join(", ", modernCoremods) + ". "
                            + "If they target the same class with conflicting logic, one will override the other.")
                    .solutions(
                            "Check each mod's coremod documentation for known conflicts",
                            "Try loading mods one by one to isolate the conflicting pair"
                    )
                    .build());
        }

        // Warn about any coremod in a Fabric/Quilt environment (these shouldn't be there)
        String loader = ctx.loaderHint().toLowerCase();
        if ((loader.contains("fabric") || loader.contains("quilt"))
                && (!legacyCoremods.isEmpty() || !modernCoremods.isEmpty())) {
            List<String> wrongMods = new ArrayList<>(legacyCoremods);
            wrongMods.addAll(modernCoremods);
            issues.add(StandaloneIssue.builder("Forge Coremod in Fabric Environment", Severity.CRITICAL)
                    .affectedMods(wrongMods)
                    .description("Forge coremods detected in a Fabric/Quilt mods folder: "
                            + String.join(", ", wrongMods) + ". "
                            + "These are Forge-only mods and cannot run on Fabric.")
                    .solutions(
                            "Remove all Forge coremods from the Fabric mods folder",
                            "Find Fabric equivalents for each coremod"
                    )
                    .build());
        }

        return issues;
    }

    private CoremodInfo analyze(File jar, String modId) {
        boolean legacy  = false;
        boolean modern  = false;
        boolean transformer = false;

        try (ZipFile zf = new ZipFile(jar)) {
            // Check MANIFEST.MF for FMLCorePlugin
            ZipEntry mf = zf.getEntry("META-INF/MANIFEST.MF");
            if (mf != null) {
                try (InputStream in = zf.getInputStream(mf)) {
                    Manifest manifest = new Manifest(in);
                    Attributes attrs = manifest.getMainAttributes();
                    if (attrs.getValue("FMLCorePlugin") != null
                            || attrs.getValue("FMLCorePluginContainsFMLMod") != null) {
                        legacy = true;
                    }
                }
            }

            // Check for modern coremods.json
            ZipEntry cm = zf.getEntry("META-INF/coremods.json");
            if (cm != null) modern = true;

            // Check for ITransformer implementations (NeoForge/modern Forge)
            ZipEntry services = zf.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformer");
            if (services != null) transformer = true;

        } catch (Exception ignored) {}

        return new CoremodInfo(legacy, modern, transformer);
    }

    private record CoremodInfo(boolean isLegacyCoremod, boolean hasModernCoremod, boolean hasTransformer) {}
}
