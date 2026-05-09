package dev.compatmanager.standalone.report;

import com.google.gson.GsonBuilder;
import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.parser.UnifiedModMeta;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {

    private static final boolean ANSI = System.console() != null
            || Boolean.getBoolean("ansi");

    // ── Console report ────────────────────────────────────────────────────

    public void printConsole(List<UnifiedModMeta> mods, List<StandaloneIssue> issues,
                             Map<String, Long> loaderSummary) {
        printConsole(mods, issues, loaderSummary, false);
    }

    public void printConsole(List<UnifiedModMeta> mods, List<StandaloneIssue> issues,
                             Map<String, Long> loaderSummary, boolean verbose) {
        String sep = "═".repeat(70);
        println("");
        println(bold("╔" + sep + "╗"));
        println(bold("║") + center("  Minecraft Compatibility Manager — Standalone Scanner  ", 70) + bold("║"));
        println(bold("╚" + sep + "╝"));
        println("");

        println(bold("MODS SCANNED: ") + mods.size());
        loaderSummary.forEach((loader, count) ->
                println("  " + gray("•") + " " + loader + ": " + count + " mod(s)"));
        println("");

        if (verbose) {
            println(bold("ALL MODS:"));
            mods.forEach(m -> println("  " + gray("•") + " " + m.id()
                    + "  " + gray(m.version()) + "  " + gray("[" + m.loaderType() + "]")));
            println("");
        }

        if (issues.isEmpty()) {
            println(green("✔ No compatibility issues detected! Your mod list looks clean."));
        } else {
            println(color(StandaloneIssue.Severity.CRITICAL,
                    "⚠ " + issues.size() + " issue(s) found:\n"));

            long critical = count(issues, StandaloneIssue.Severity.CRITICAL);
            long high     = count(issues, StandaloneIssue.Severity.HIGH);
            long medium   = count(issues, StandaloneIssue.Severity.MEDIUM);
            long low      = count(issues, StandaloneIssue.Severity.LOW);

            if (critical > 0) println("  " + color(StandaloneIssue.Severity.CRITICAL, "CRITICAL: " + critical));
            if (high     > 0) println("  " + color(StandaloneIssue.Severity.HIGH,     "HIGH:     " + high));
            if (medium   > 0) println("  " + color(StandaloneIssue.Severity.MEDIUM,   "MEDIUM:   " + medium));
            if (low      > 0) println("  " + color(StandaloneIssue.Severity.LOW,      "LOW:      " + low));
            println("");

            for (int i = 0; i < issues.size(); i++) {
                printIssue(i + 1, issues.get(i));
            }
        }
        println("");
    }

    private void printIssue(int n, StandaloneIssue issue) {
        String sevStr  = "[" + issue.severity().name() + "]";
        String fixedStr = issue.isFixed() ? green(" [FIXED]") : (issue.canAutoFix() ? gray(" [auto-fixable]") : "");
        println(bold("" + n + ". " + issue.type()) + "  " + color(issue.severity(), sevStr) + fixedStr);
        println("   " + gray("Affected: ") + String.join(", ", issue.affectedMods()));
        println("   " + issue.description());
        if (!issue.jarPath().isBlank()) {
            println("   " + gray("JAR: ") + issue.jarPath());
        }
        if (!issue.solutions().isEmpty()) {
            println("   " + bold("Fixes:"));
            issue.solutions().forEach(s -> println("     " + green("→") + " " + s));
        }
        println("");
    }

    // ── JSON report ───────────────────────────────────────────────────────

    public void writeJson(List<UnifiedModMeta> mods, List<StandaloneIssue> issues, Path outputPath)
            throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("scanTime",   ts);
        root.put("totalMods",  mods.size());
        root.put("issueCount", issues.size());

        Map<String, Long> severity = new LinkedHashMap<>();
        for (StandaloneIssue.Severity sev : StandaloneIssue.Severity.values()) {
            long c = count(issues, sev);
            if (c > 0) severity.put(sev.name(), c);
        }
        root.put("bySeverity", severity);

        List<Map<String, Object>> issueList = new ArrayList<>();
        for (StandaloneIssue issue : issues) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("type",        issue.type());
            obj.put("severity",    issue.severity().name());
            obj.put("affectedMods",issue.affectedMods());
            obj.put("description", issue.description());
            obj.put("solutions",   issue.solutions());
            if (!issue.jarPath().isBlank()) obj.put("jarPath", issue.jarPath());
            obj.put("canAutoFix",  issue.canAutoFix());
            obj.put("isFixed",     issue.isFixed());
            issueList.add(obj);
        }
        root.put("issues", issueList);

        List<Map<String, Object>> modList = new ArrayList<>();
        for (UnifiedModMeta mod : mods) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("id",         mod.id());
            obj.put("name",       mod.name());
            obj.put("version",    mod.version());
            obj.put("loaderType", mod.loaderType());
            modList.add(obj);
        }
        root.put("mods", modList);

        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        Files.writeString(outputPath, json);
        println("JSON report written to: " + outputPath.toAbsolutePath());
    }

    // ── HTML report ───────────────────────────────────────────────────────

    public void writeHtml(List<UnifiedModMeta> mods, List<StandaloneIssue> issues, Path outputPath)
            throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Compatibility Manager Report</title>
                <style>
                  body { font-family: 'Segoe UI', sans-serif; background:#1a1a2e; color:#e0e0e0; margin:0; padding:20px; }
                  h1   { color:#7289da; }
                  .summary { background:#16213e; border-radius:8px; padding:16px; margin:16px 0; }
                  .issue { border-radius:8px; padding:16px; margin:12px 0; }
                  .CRITICAL { background:#3d0000; border-left:4px solid #ff5555; }
                  .HIGH     { background:#3d1500; border-left:4px solid #ff8800; }
                  .MEDIUM   { background:#3d3000; border-left:4px solid #ffaa00; }
                  .LOW      { background:#2a3d00; border-left:4px solid #aaff00; }
                  .INFO     { background:#001a2e; border-left:4px solid #55aaff; }
                  .sev  { font-weight:bold; padding:2px 8px; border-radius:4px; font-size:.8em; }
                  .CRITICAL .sev { background:#ff5555; color:#fff; }
                  .HIGH .sev     { background:#ff8800; color:#fff; }
                  .MEDIUM .sev   { background:#ffaa00; color:#000; }
                  .LOW .sev      { background:#aaff00; color:#000; }
                  .INFO .sev     { background:#55aaff; color:#000; }
                  .fixed-badge   { background:#00aa44; color:#fff; padding:2px 8px; border-radius:4px; font-size:.8em; margin-left:4px; }
                  .fixable-badge { background:#555; color:#aaa; padding:2px 8px; border-radius:4px; font-size:.8em; margin-left:4px; }
                  .affected { color:#aaa; font-size:.9em; margin:4px 0; }
                  .jar-path { color:#666; font-size:.8em; font-family:monospace; margin:4px 0; }
                  .fix  { color:#55ff55; margin:2px 0; }
                  .fix::before { content:'→ '; }
                  .mod-list { columns:3; }
                  .mod-entry { break-inside:avoid; font-size:.85em; color:#aaa; margin:2px 0; }
                  .badge { display:inline-block; padding:1px 6px; border-radius:3px;
                           font-size:.75em; background:#444; margin-left:4px; }
                  .fabric { background:#1b4b7a; } .forge { background:#4a2000; }
                  .neoforge { background:#1a3a00; } .quilt { background:#3a0050; }
                  .forge-legacy { background:#5a3000; } .liteloader { background:#2a4a00; }
                </style>
                </head>
                <body>
                """);

        sb.append("<h1>Minecraft Compatibility Manager</h1>\n");
        sb.append("<p>Report generated: <code>").append(ts).append("</code></p>\n");

        sb.append("<div class='summary'>\n");
        sb.append("<h2>").append(issues.isEmpty()
                ? "✔ No Issues Found"
                : "⚠ " + issues.size() + " Issue(s) Found").append("</h2>\n");
        sb.append("<p><strong>Total mods scanned: ").append(mods.size()).append("</strong></p>\n");
        if (!issues.isEmpty()) {
            for (StandaloneIssue.Severity sev : StandaloneIssue.Severity.values()) {
                long c = count(issues, sev);
                if (c > 0) sb.append("<span class='sev ").append(sev).append("'>")
                        .append(sev).append(": ").append(c).append("</span> \n");
            }
        }
        sb.append("</div>\n");

        for (StandaloneIssue issue : issues) {
            sb.append("<div class='issue ").append(issue.severity()).append("'>\n");
            sb.append("<strong>").append(escHtml(issue.type())).append("</strong> ");
            sb.append("<span class='sev'>").append(issue.severity()).append("</span>");
            if (issue.isFixed()) {
                sb.append("<span class='fixed-badge'>FIXED</span>");
            } else if (issue.canAutoFix()) {
                sb.append("<span class='fixable-badge'>auto-fixable</span>");
            }
            sb.append("\n");
            sb.append("<div class='affected'>Affected: ")
              .append(escHtml(String.join(", ", issue.affectedMods()))).append("</div>\n");
            sb.append("<p>").append(escHtml(issue.description())).append("</p>\n");
            if (!issue.jarPath().isBlank()) {
                sb.append("<div class='jar-path'>JAR: ")
                  .append(escHtml(issue.jarPath())).append("</div>\n");
            }
            if (!issue.solutions().isEmpty()) {
                sb.append("<p><strong>Fixes:</strong></p>\n");
                issue.solutions().forEach(s ->
                        sb.append("<div class='fix'>").append(escHtml(s)).append("</div>\n"));
            }
            sb.append("</div>\n");
        }

        sb.append("<div class='summary'><h3>Installed Mods (").append(mods.size())
          .append(")</h3><div class='mod-list'>\n");
        for (UnifiedModMeta mod : mods) {
            sb.append("<div class='mod-entry'>")
              .append(escHtml(mod.name().isBlank() ? mod.id() : mod.name()))
              .append(" <code>").append(escHtml(mod.version())).append("</code>")
              .append(" <span class='badge ").append(mod.loaderType()).append("'>")
              .append(mod.loaderType()).append("</span>")
              .append("</div>\n");
        }
        sb.append("</div></div>\n</body></html>");

        Files.writeString(outputPath, sb.toString());
        println("HTML report written to: " + outputPath.toAbsolutePath());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private long count(List<StandaloneIssue> issues, StandaloneIssue.Severity sev) {
        return issues.stream().filter(i -> i.severity() == sev).count();
    }

    private String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String bold(String s)  { return ANSI ? "\033[1m"    + s + "\033[0m" : s; }
    private String green(String s) { return ANSI ? "\033[0;32m" + s + "\033[0m" : s; }
    private String gray(String s)  { return ANSI ? "\033[0;90m" + s + "\033[0m" : s; }
    private String center(String s, int w) {
        int pad = (w - s.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + s + " ".repeat(Math.max(0, w - s.length() - pad));
    }
    private String color(StandaloneIssue.Severity sev, String s) {
        return ANSI ? "\033" + sev.ansiColor() + s + "\033[0m" : s;
    }
    private void println(String s) { System.out.println(s); }
}
