package dev.compatmanager.standalone.detector;

import com.google.gson.*;
import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Detects @Overwrite mixin conflicts.
 *
 * When two mods both use @Overwrite on the same target class method,
 * the second one silently wins — the first mod's logic is completely lost.
 * This is one of the most common causes of mysterious crashes.
 */
public class MixinConflictDetector implements IssueDetector {

    // Annotation descriptor bytes we look for in .class constant pools
    private static final byte[] OVERWRITE_BYTES =
            "Lorg/spongepowered/asm/mixin/Overwrite;".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MIXIN_BYTES =
            "Lorg/spongepowered/asm/mixin/Mixin;".getBytes(StandardCharsets.UTF_8);

    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        List<File>           jars = platform.getJarFiles();

        // targetClass → list of modIds that @Overwrite something in it
        Map<String, List<String>> targetToOverwriters = new LinkedHashMap<>();

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);
            Set<String> targets = findOverwriteTargets(jar);
            for (String target : targets) {
                targetToOverwriters.computeIfAbsent(target, k -> new ArrayList<>()).add(modId);
            }
        }

        for (Map.Entry<String, List<String>> entry : targetToOverwriters.entrySet()) {
            List<String> overwriters = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            if (overwriters.size() < 2) continue;

            String targetClass = entry.getKey().replace('/', '.');
            issues.add(StandaloneIssue.builder("Mixin @Overwrite Conflict", Severity.CRITICAL)
                    .affectedMods(overwriters)
                    .description(overwriters.size() + " mods use @Overwrite on the same class: "
                            + targetClass + ". The last-loaded mod's version wins; others are silently lost. "
                            + "This causes unpredictable crashes or broken features.")
                    .solutions(
                            "Check if a newer version of any of these mods resolved the conflict",
                            "Report the conflict to both mod authors — one should switch to @Inject/@ModifyArg",
                            "Remove one of: " + String.join(", ", overwriters)
                    )
                    .build());
        }

        return issues;
    }

    /** Returns the set of Minecraft target class names that this JAR @Overwrites. */
    private Set<String> findOverwriteTargets(File jar) {
        Set<String> targets = new HashSet<>();
        try (ZipFile zf = new ZipFile(jar)) {
            // 1. Find all *.mixins.json to know which classes are Mixin classes
            List<String> mixinClassPaths = collectMixinClassPaths(zf);

            // 2. For each mixin class, check for @Overwrite + extract target
            for (String classPath : mixinClassPaths) {
                ZipEntry entry = zf.getEntry(classPath);
                if (entry == null) continue;
                byte[] bytes;
                try (InputStream in = zf.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                } catch (Exception e) { continue; }

                if (!containsSequence(bytes, OVERWRITE_BYTES)) continue;

                // Extract Minecraft target class names from constant pool strings
                Set<String> classRefs = extractMinecraftClassRefs(bytes);
                targets.addAll(classRefs);
            }
        } catch (Exception ignored) {}
        return targets;
    }

    private List<String> collectMixinClassPaths(ZipFile zf) {
        List<String> classPaths = new ArrayList<>();
        zf.stream()
                .filter(e -> e.getName().endsWith(".mixins.json") || e.getName().endsWith("mixins.json"))
                .forEach(e -> {
                    try (InputStream in = zf.getInputStream(e)) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        classPaths.addAll(parseMixinsJson(json));
                    } catch (Exception ignored) {}
                });
        return classPaths;
    }

    private List<String> parseMixinsJson(String json) {
        List<String> paths = new ArrayList<>();
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            String pkg = obj.has("package") ? obj.get("package").getAsString().replace('.', '/') : "";
            if (!pkg.isEmpty() && !pkg.endsWith("/")) pkg += "/";

            for (String key : List.of("mixins", "client", "server")) {
                if (!obj.has(key)) continue;
                for (JsonElement el : obj.getAsJsonArray(key)) {
                    String name = el.getAsString().replace('.', '/');
                    paths.add(pkg + name + ".class");
                }
            }
        } catch (Exception ignored) {}
        return paths;
    }

    /**
     * Scans class bytes for Minecraft class references (net/minecraft/...).
     * These appear as UTF-8 constant pool entries — readable as raw byte sequences.
     */
    private Set<String> extractMinecraftClassRefs(byte[] bytes) {
        Set<String> found = new HashSet<>();
        byte[] prefix = "net/minecraft/".getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i <= bytes.length - prefix.length; i++) {
            if (!matchAt(bytes, i, prefix)) continue;

            // Read forward until a non-class-name character
            int start = i;
            int end   = i + prefix.length;
            while (end < bytes.length && isClassNameChar(bytes[end])) end++;
            String className = new String(bytes, start, end - start, StandardCharsets.UTF_8);
            if (className.length() > prefix.length) {
                found.add(className);
            }
            i = end;
        }
        return found;
    }

    private boolean containsSequence(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private boolean matchAt(byte[] bytes, int pos, byte[] pattern) {
        if (pos + pattern.length > bytes.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (bytes[pos + i] != pattern[i]) return false;
        }
        return true;
    }

    private boolean isClassNameChar(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                || (b >= '0' && b <= '9') || b == '/' || b == '_' || b == '$';
    }
}
