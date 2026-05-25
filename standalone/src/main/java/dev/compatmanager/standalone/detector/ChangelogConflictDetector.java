package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipFile;

/**
 * Scans mod changelog and readme files for author-declared incompatibilities.
 *
 * Looks for phrases like "incompatible with X", "do not use with X",
 * "conflicts with X" — then cross-references X against loaded mods.
 */
public class ChangelogConflictDetector implements IssueDetector {

    private static final List<String> DOC_FILES = List.of(
            "CHANGELOG.md", "CHANGES.md", "CHANGELOG.txt", "CHANGES.txt",
            "README.md", "README.txt", "changelog.md", "readme.md",
            "INCOMPATIBILITIES.md", "KNOWN_ISSUES.md"
    );

    // Patterns: "incompatible with X", "conflicts with X", "do not use with X", etc.
    private static final List<Pattern> INCOMPAT_PATTERNS = List.of(
            Pattern.compile("(?i)incompatible\\s+with\\s+['\"`]?([\\w\\-_+]+)['\"`]?"),
            Pattern.compile("(?i)conflicts?\\s+with\\s+['\"`]?([\\w\\-_+]+)['\"`]?"),
            Pattern.compile("(?i)do\\s+not\\s+use\\s+(?:with|alongside)\\s+['\"`]?([\\w\\-_+]+)['\"`]?"),
            Pattern.compile("(?i)not\\s+compatible\\s+with\\s+['\"`]?([\\w\\-_+]+)['\"`]?"),
            Pattern.compile("(?i)breaks?\\s+(?:when\\s+used\\s+)?with\\s+['\"`]?([\\w\\-_+]+)['\"`]?"),
            Pattern.compile("(?i)known\\s+issue[s]?\\s+with\\s+['\"`]?([\\w\\-_+]+)['\"`]?"),
            Pattern.compile("(?i)causes?\\s+crash(?:es)?\\s+with\\s+['\"`]?([\\w\\-_+]+)['\"`]?")
    );

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods  = platform.getLoadedMods();
        List<File>           jars  = platform.getJarFiles();
        Set<String>          allIds = new HashSet<>();
        mods.forEach(m -> allIds.add(m.id().toLowerCase()));

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);

            Map<String, String> mentions = scanDocFiles(jar);
            for (Map.Entry<String, String> entry : mentions.entrySet()) {
                String mentionedMod = entry.getKey().toLowerCase();
                String sourcePhrase = entry.getValue();

                if (!allIds.contains(mentionedMod)) continue;
                if (mentionedMod.equals(modId.toLowerCase())) continue;

                issues.add(StandaloneIssue.builder("Author-Declared Incompatibility", Severity.HIGH)
                        .affectedMods(modId, mentionedMod)
                        .description("The mod '" + modId + "' mentions incompatibility with '"
                                + mentionedMod + "' in its changelog/readme. "
                                + "Exact phrase: \"" + sourcePhrase + "\"")
                        .solutions(
                                "Remove '" + mentionedMod + "' or '" + modId + "' from your mods folder",
                                "Check the mod's page for an updated version that resolves the conflict"
                        )
                        .build());
            }
        }

        return issues;
    }

    /** Returns a map of mentionedModId → matched phrase from all doc files in the JAR. */
    private Map<String, String> scanDocFiles(File jar) {
        Map<String, String> found = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(jar)) {
            for (String docFile : DOC_FILES) {
                var entry = zf.getEntry(docFile);
                if (entry == null) continue;
                try (InputStream in = zf.getInputStream(entry)) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    for (Pattern p : INCOMPAT_PATTERNS) {
                        Matcher m = p.matcher(text);
                        while (m.find()) {
                            String mentioned = m.group(1).toLowerCase().replace("-", "_");
                            if (!found.containsKey(mentioned)) {
                                // Grab surrounding context (up to 80 chars)
                                int start = Math.max(0, m.start() - 10);
                                int end   = Math.min(text.length(), m.end() + 30);
                                String ctx2 = text.substring(start, end)
                                        .replaceAll("\\s+", " ").trim();
                                found.put(mentioned, ctx2);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return found;
    }
}
