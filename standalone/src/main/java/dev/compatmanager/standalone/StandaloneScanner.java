package dev.compatmanager.standalone;

import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;
import dev.compatmanager.standalone.report.ReportGenerator;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Standalone Minecraft Compatibility Manager.
 *
 * Works with EVERY Minecraft Java Edition version ever released.
 * No Minecraft installation required — just point it at your mods folder.
 *
 * Usage:
 *   java -jar compatmanager-standalone.jar [mods-folder] [--html report.html]
 *
 * Examples:
 *   java -jar compatmanager-standalone.jar
 *       → scans ./mods in the current directory
 *
 *   java -jar compatmanager-standalone.jar "C:\Users\You\AppData\Roaming\.minecraft\mods"
 *       → scans a specific mods folder
 *
 *   java -jar compatmanager-standalone.jar ~/.minecraft/mods --html report.html
 *       → scans and also writes an HTML report
 *
 * Supports all mod metadata formats:
 *   fabric.mod.json         (Fabric 1.14+)
 *   quilt.mod.json          (Quilt 1.18+)
 *   META-INF/mods.toml      (Forge 1.13 – 1.20.1)
 *   META-INF/neoforge.mods.toml (NeoForge 1.20.2+)
 *   mcmod.info              (Forge 1.7 – 1.12.2)
 *   litemod.json            (LiteLoader 1.5 – 1.12.2)
 *   MANIFEST.MF             (fallback for all JARs)
 */
public class StandaloneScanner {

    public static void main(String[] args) throws Exception {
        Path modsDir  = Paths.get("mods");
        Path htmlOut  = null;

        // Parse args
        for (int i = 0; i < args.length; i++) {
            if ("--html".equals(args[i]) && i + 1 < args.length) {
                htmlOut = Paths.get(args[++i]);
            } else if (!args[i].startsWith("--")) {
                modsDir = Paths.get(args[i]);
            }
        }

        // Auto-detect common mods folder locations
        if (!modsDir.toFile().exists()) {
            modsDir = findDefaultModsDir();
        }

        if (!modsDir.toFile().exists()) {
            System.err.println("ERROR: Mods folder not found: " + modsDir.toAbsolutePath());
            System.err.println("Usage: java -jar compatmanager-standalone.jar [mods-folder]");
            System.exit(1);
        }

        System.out.println("Scanning: " + modsDir.toAbsolutePath());

        StandaloneModPlatform platform  = new StandaloneModPlatform(modsDir);
        List<UnifiedModMeta>  mods      = platform.getLoadedMods();
        Map<String, Long>     loaders   = platform.getLoaderSummary();
        StandaloneDetector    detector  = new StandaloneDetector(platform);
        List<StandaloneIssue> issues    = detector.detectAll();
        ReportGenerator       reporter  = new ReportGenerator();

        reporter.printConsole(mods, issues, loaders);

        if (htmlOut != null) {
            reporter.writeHtml(mods, issues, htmlOut);
        } else {
            // Always write HTML report alongside the scanner
            Path defaultHtml = Paths.get("compat-report.html");
            reporter.writeHtml(mods, issues, defaultHtml);
        }

        // Exit code: 0 = no issues, 1 = issues found, 2 = critical issues
        boolean hasCritical = issues.stream()
                .anyMatch(i -> i.severity() == StandaloneIssue.Severity.CRITICAL);
        System.exit(hasCritical ? 2 : issues.isEmpty() ? 0 : 1);
    }

    private static Path findDefaultModsDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        List<String> candidates;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            candidates = List.of(
                    appdata != null ? appdata + "\\.minecraft\\mods" : "",
                    appdata != null ? appdata + "\\PrismLauncher\\instances\\*\\mods" : "",
                    home + "\\curseforge\\minecraft\\Instances\\*\\mods"
            );
        } else if (os.contains("mac")) {
            candidates = List.of(
                    home + "/Library/Application Support/minecraft/mods",
                    home + "/Library/Application Support/PrismLauncher/instances"
            );
        } else {
            // Linux
            candidates = List.of(
                    home + "/.minecraft/mods",
                    home + "/.local/share/PrismLauncher/instances",
                    home + "/snap/minecraft/common/.minecraft/mods"
            );
        }

        for (String c : candidates) {
            if (c.isBlank()) continue;
            File f = new File(c);
            if (f.exists() && f.isDirectory()) return f.toPath();
        }

        return Paths.get("mods");
    }
}
