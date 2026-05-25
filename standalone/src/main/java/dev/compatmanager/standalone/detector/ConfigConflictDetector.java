package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

public class ConfigConflictDetector implements IssueDetector {

    // Legacy Forge: key.<name>=<int_keycode>
    private static final Pattern CFG_KEYBIND  = Pattern.compile(
            "^\\s*(?:key[._]|key_bind[s]?[._])([\\w.]+)\\s*=\\s*(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE);

    // Legacy Forge: B:<name>=<int_id> or I:<name>=<int> inside block/item sections
    private static final Pattern CFG_BLOCK_ID = Pattern.compile(
            "^\\s*[BI]:(\\w+)\\s*=\\s*(\\d+)\\s*$");

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        if (ctx.configDir() == null) return List.of();

        Path configDir = ctx.configDir();
        if (!configDir.toFile().isDirectory()) return List.of();

        List<StandaloneIssue> issues = new ArrayList<>();

        // keycode → list of "configFile:keyName"
        Map<String, List<String>> keybindMap = new LinkedHashMap<>();
        // numeric ID → list of "configFile:registryName"
        Map<String, List<String>> blockIdMap = new LinkedHashMap<>();

        try (Stream<Path> files = Files.walk(configDir, 2)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".cfg")) {
                    parseCfg(file, keybindMap, blockIdMap);
                } else if (name.endsWith(".toml")) {
                    parseToml(file, keybindMap);
                } else if (name.endsWith(".json")) {
                    parseJson(file, keybindMap);
                }
            });
        } catch (IOException ignored) {}

        // Report keybind conflicts
        for (Map.Entry<String, List<String>> entry : keybindMap.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            String keyCode = entry.getKey();
            List<String> bindings = entry.getValue();
            issues.add(new StandaloneIssue(
                    "Keybind Conflict",
                    Severity.MEDIUM,
                    List.of(configDir.relativize(Paths.get(bindings.get(0).split(":")[0])).toString()),
                    bindings.size() + " keybinds are assigned to the same key (code " + keyCode + "): "
                            + String.join(", ", bindings),
                    List.of(
                            "Open Options → Controls and reassign conflicting keys",
                            "Edit the affected config files and change the key values"
                    )
            ));
        }

        // Report numeric block/item ID conflicts (legacy Forge 1.7–1.12)
        for (Map.Entry<String, List<String>> entry : blockIdMap.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            String id = entry.getKey();
            List<String> registries = entry.getValue();
            issues.add(new StandaloneIssue(
                    "Numeric ID Conflict",
                    Severity.CRITICAL,
                    registries,
                    "Multiple mods claim the same numeric block/item ID " + id + ": "
                            + String.join(", ", registries),
                    List.of(
                            "Edit each config file to assign a unique numeric ID",
                            "Use an ID fixer tool for 1.7–1.12 modpacks"
                    )
            ));
        }

        return issues;
    }

    private void parseCfg(Path file, Map<String, List<String>> keybindMap, Map<String, List<String>> blockIdMap) {
        String filename = file.toAbsolutePath().toString();
        boolean inBlockSection = false;
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim().toLowerCase();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;

                if (trimmed.contains("block") || trimmed.contains("item")) inBlockSection = true;
                if (trimmed.startsWith("[") && !trimmed.contains("block") && !trimmed.contains("item")) {
                    inBlockSection = false;
                }

                Matcher km = CFG_KEYBIND.matcher(line);
                if (km.matches()) {
                    String keyName = km.group(1);
                    String keyCode = km.group(2);
                    if (!"0".equals(keyCode) && !"-1".equals(keyCode)) {
                        keybindMap.computeIfAbsent(keyCode, k -> new ArrayList<>())
                                  .add(filename + ":" + keyName);
                    }
                    continue;
                }

                if (inBlockSection) {
                    Matcher bm = CFG_BLOCK_ID.matcher(line);
                    if (bm.matches()) {
                        String regName = bm.group(1);
                        String id      = bm.group(2);
                        int idVal;
                        try { idVal = Integer.parseInt(id); } catch (NumberFormatException e) { continue; }
                        if (idVal < 256) continue; // skip vanilla range
                        blockIdMap.computeIfAbsent(id, k -> new ArrayList<>())
                                  .add(filename + ":" + regName);
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void parseToml(Path file, Map<String, List<String>> keybindMap) {
        String filename = file.toAbsolutePath().toString();
        try {
            List<String> lines = Files.readAllLines(file);
            boolean inKeybindSection = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                if (trimmed.startsWith("[")) {
                    inKeybindSection = trimmed.toLowerCase().contains("keybind")
                            || trimmed.toLowerCase().contains("key_bind")
                            || trimmed.toLowerCase().contains("hotkey");
                    continue;
                }
                if (!inKeybindSection) continue;
                // Match: name = "key.keyboard.x" or name = "KEY_X"
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                String keyName  = trimmed.substring(0, eq).trim();
                String keyValue = trimmed.substring(eq + 1).trim()
                        .replace("\"", "").replace("'", "").toLowerCase();
                if (keyValue.startsWith("key.keyboard.") || keyValue.startsWith("key_")) {
                    keybindMap.computeIfAbsent(keyValue, k -> new ArrayList<>())
                              .add(filename + ":" + keyName);
                }
            }
        } catch (IOException ignored) {}
    }

    private void parseJson(Path file, Map<String, List<String>> keybindMap) {
        String filename = file.toAbsolutePath().toString();
        try {
            String content = Files.readString(file);
            // Simple regex scan for "key": "key.keyboard.x" patterns
            Matcher m = Pattern.compile("\"key\"\\s*:\\s*\"(key\\.keyboard\\.[^\"]+)\"")
                    .matcher(content);
            while (m.find()) {
                String keyValue = m.group(1).toLowerCase();
                keybindMap.computeIfAbsent(keyValue, k -> new ArrayList<>())
                          .add(filename + ":key");
            }
        } catch (IOException ignored) {}
    }
}
