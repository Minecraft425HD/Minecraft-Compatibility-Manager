package dev.compatmanager.standalone;

import dev.compatmanager.standalone.db.OnlineDatabase;
import dev.compatmanager.standalone.detector.ScanContext;
import dev.compatmanager.standalone.fix.AutoFixer;
import dev.compatmanager.standalone.fix.ScriptGenerator;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;
import dev.compatmanager.standalone.report.ReportGenerator;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Standalone Minecraft Compatibility Manager.
 *
 * Works with EVERY Minecraft Java Edition version ever released.
 * No Minecraft installation required — just point it at your mods folder.
 *
 * Usage:
 *   java -jar compatmanager-standalone.jar [mods-folder] [options]
 *
 * Options:
 *   --mc-version <1.20.1>          MC version for context-sensitive detection
 *   --loader <fabric|forge|...>    Loader hint (fabric, forge, neoforge, quilt)
 *   --config-dir <path>            Also scan this config folder for keybind/ID conflicts
 *   --fix                          Automatically disable conflicting mods
 *   --dry-run                      Preview --fix actions without making changes
 *   --script                       Generate fix.bat + fix.sh scripts
 *   --html [file]                  Write HTML report (default: compat-report.html)
 *   --json [file]                  Write JSON report (default: compat-report.json)
 *   --verbose                      Show all mods, not just problematic ones
 *   --help                         Show this help
 */
public class StandaloneScanner {

    private record CLIArgs(
            Path    modsDir,
            boolean explicitModsDir,  // true when user supplied the path on the CLI
            String  mcVersion,
            String  loaderHint,
            Path    configDir,
            boolean fix,
            boolean dryRun,
            boolean script,
            Path    htmlOut,
            boolean htmlEnabled,
            Path    jsonOut,
            boolean jsonEnabled,
            boolean verbose,
            boolean help,
            boolean offline,
            boolean updateDb,
            String  curseForgeKey
    ) {}

    public static void main(String[] args) throws Exception {
        CLIArgs cli = parseArgs(args);

        if (cli.help()) {
            printHelp();
            System.exit(0);
        }

        Path modsDir = cli.modsDir();
        // Only attempt auto-detection when the user didn't explicitly supply a path
        if (!cli.explicitModsDir() && !modsDir.toFile().exists()) modsDir = findDefaultModsDir();

        if (!modsDir.toFile().exists()) {
            System.err.println("ERROR: Mods folder not found: " + modsDir.toAbsolutePath());
            System.err.println("Usage: java -jar compatmanager-standalone.jar [mods-folder] [options]");
            System.err.println("Run with --help for full usage.");
            System.exit(1);
        }

        System.out.println("Scanning: " + modsDir.toAbsolutePath());
        if (!cli.mcVersion().isBlank())
            System.out.println("MC version: " + cli.mcVersion());
        if (!cli.loaderHint().isBlank())
            System.out.println("Loader: " + cli.loaderHint());
        if (cli.offline())
            System.out.println("Mode: offline (API calls disabled)");

        // Refresh online database if requested
        if (cli.updateDb() && !cli.offline()) {
            System.out.print("Updating online incompatibility database... ");
            OnlineDatabase.update();
            System.out.println("done.");
        }

        ScanContext ctx = new ScanContext(
                cli.mcVersion(), cli.loaderHint(), cli.configDir(),
                cli.verbose(), cli.dryRun(),
                cli.offline(), cli.updateDb(), cli.curseForgeKey());

        StandaloneModPlatform platform = new StandaloneModPlatform(modsDir);
        List<UnifiedModMeta>  mods     = platform.getLoadedMods();
        Map<String, Long>     loaders  = platform.getLoaderSummary();
        StandaloneDetector    detector = new StandaloneDetector(platform, ctx);
        List<StandaloneIssue> issues   = detector.detectAll();
        ReportGenerator       reporter = new ReportGenerator();

        // Auto-fix
        if (cli.fix() || cli.dryRun()) {
            AutoFixer fixer = new AutoFixer(modsDir, cli.dryRun());
            int fixed = 0;
            for (StandaloneIssue issue : issues) {
                if (issue.canAutoFix() && !issue.isFixed()) {
                    issue.applyFix(fixer);
                    if (issue.isFixed()) fixed++;
                }
            }
            if (fixed > 0) {
                System.out.println((cli.dryRun() ? "[DRY RUN] Would fix: " : "Fixed: ") + fixed + " issue(s).");
            }
        }

        // Script generation
        if (cli.script()) {
            new ScriptGenerator(modsDir).generate(issues);
        }

        // Console output
        reporter.printConsole(mods, issues, loaders, cli.verbose());

        // HTML report
        if (cli.htmlEnabled()) {
            Path htmlPath = cli.htmlOut() != null ? cli.htmlOut()
                    : modsDir.resolve("compat-report.html");
            reporter.writeHtml(mods, issues, htmlPath);
        } else {
            // Always write a default HTML report
            reporter.writeHtml(mods, issues, Paths.get("compat-report.html"));
        }

        // JSON report
        if (cli.jsonEnabled()) {
            Path jsonPath = cli.jsonOut() != null ? cli.jsonOut()
                    : modsDir.resolve("compat-report.json");
            reporter.writeJson(mods, issues, jsonPath);
        }

        // Exit code: 2=critical, 1=issues found, 0=clean
        // Dry-run: issues aren't truly fixed, so reflect original severity in exit code
        boolean hasCritical = issues.stream()
                .anyMatch(i -> i.severity() == StandaloneIssue.Severity.CRITICAL
                        && (cli.dryRun() || !i.isFixed()));
        boolean hasAny = issues.stream().anyMatch(i -> cli.dryRun() || !i.isFixed());
        System.exit(hasCritical ? 2 : hasAny ? 1 : 0);
    }

    // ── CLI parsing ───────────────────────────────────────────────────────

    private static CLIArgs parseArgs(String[] args) {
        Path    modsDir          = Paths.get("mods");
        boolean explicitModsDir  = false;
        String  mcVersion        = "";
        String  loaderHint       = "";
        Path    configDir        = null;
        boolean fix              = false;
        boolean dryRun           = false;
        boolean script           = false;
        Path    htmlOut          = null;
        boolean htmlEnabled      = false;
        Path    jsonOut          = null;
        boolean jsonEnabled      = false;
        boolean verbose          = false;
        boolean help             = false;
        boolean offline          = false;
        boolean updateDb         = false;
        String  curseForgeKey    = "";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mc-version"      -> { if (i + 1 < args.length) mcVersion     = args[++i]; }
                case "--loader"          -> { if (i + 1 < args.length) loaderHint    = args[++i]; }
                case "--config-dir"      -> { if (i + 1 < args.length) configDir     = Paths.get(args[++i]); }
                case "--curseforge-key"  -> { if (i + 1 < args.length) curseForgeKey = args[++i]; }
                case "--fix"             -> fix       = true;
                case "--dry-run"         -> dryRun    = true;
                case "--script"          -> script    = true;
                case "--verbose"         -> verbose   = true;
                case "--offline"         -> offline   = true;
                case "--update-db"       -> updateDb  = true;
                case "--help", "-h"      -> help      = true;
                case "--html" -> {
                    htmlEnabled = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--"))
                        htmlOut = Paths.get(args[++i]);
                }
                case "--json" -> {
                    jsonEnabled = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--"))
                        jsonOut = Paths.get(args[++i]);
                }
                default -> {
                    if (!args[i].startsWith("--")) {
                        modsDir = Paths.get(args[i]);
                        explicitModsDir = true;
                    } else {
                        System.err.println("WARNING: Unknown flag '" + args[i] + "' — ignored. Run --help for usage.");
                    }
                }
            }
        }

        if (dryRun) fix = false; // dryRun implies preview mode; don't actually fix

        return new CLIArgs(modsDir, explicitModsDir, mcVersion, loaderHint, configDir,
                fix, dryRun, script, htmlOut, htmlEnabled, jsonOut, jsonEnabled, verbose, help,
                offline, updateDb, curseForgeKey);
    }

    // ── Auto-detect mods folder ───────────────────────────────────────────

    private static Path findDefaultModsDir() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        List<String> candidates;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            candidates = List.of(
                    appdata != null ? appdata + "\\.minecraft\\mods" : "",
                    home + "\\curseforge\\minecraft\\Instances"
            );
        } else if (os.contains("mac")) {
            candidates = List.of(
                    home + "/Library/Application Support/minecraft/mods",
                    home + "/Library/Application Support/PrismLauncher/instances"
            );
        } else {
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

    // ── Help text ─────────────────────────────────────────────────────────

    private static void printHelp() {
        System.out.println("""
                Minecraft Compatibility Manager — Standalone Scanner
                Supports ALL Minecraft Java Edition versions ever released.

                USAGE:
                  java -jar compatmanager-standalone.jar [mods-folder] [options]

                ARGUMENTS:
                  mods-folder           Path to your mods folder (default: ./mods or auto-detected)

                OPTIONS:
                  --mc-version <ver>        Minecraft version for version-aware detection (e.g. 1.20.1)
                  --loader <name>           Loader hint: fabric | forge | neoforge | quilt
                  --config-dir <path>       Scan config folder for keybind/ID conflicts
                  --fix                     Automatically move conflicting mods to disabled-by-compatmanager/
                  --dry-run                 Preview --fix actions without making changes
                  --script                  Generate fix.bat + fix.sh scripts
                  --html [file]             Write HTML report (default: compat-report.html)
                  --json [file]             Write JSON report (default: compat-report.json)
                  --verbose                 Show all mods, not only problematic ones
                  --offline                 Disable all network/API calls (use cached data only)
                  --update-db               Force-refresh the online incompatibility database
                  --curseforge-key <key>    CurseForge API key (or set CF_API_KEY env var)
                  --help, -h                Show this help

                EXIT CODES:
                  0   No issues found
                  1   Issues found (non-critical)
                  2   Critical issues found

                EXAMPLES:
                  java -jar compatmanager-standalone.jar
                  java -jar compatmanager-standalone.jar ~/.minecraft/mods --mc-version 1.20.1
                  java -jar compatmanager-standalone.jar ./mods --fix --dry-run
                  java -jar compatmanager-standalone.jar ./mods --fix --json report.json
                  java -jar compatmanager-standalone.jar ./mods --config-dir ./config --verbose
                  java -jar compatmanager-standalone.jar ./mods --script
                """);
    }
}
