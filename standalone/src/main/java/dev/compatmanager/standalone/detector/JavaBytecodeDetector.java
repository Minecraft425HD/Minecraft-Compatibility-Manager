package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JavaBytecodeDetector implements IssueDetector {

    private static final int CURRENT_MAJOR = Runtime.version().feature() + 44;

    private static final int[] MAJOR_TO_JAVA = {
        52, 8, 53, 9, 54, 10, 55, 11, 56, 12, 57, 13, 58, 14,
        59, 15, 60, 16, 61, 17, 62, 18, 63, 19, 64, 20, 65, 21
    };

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();
        List<UnifiedModMeta> mods  = platform.getLoadedMods();
        List<File>           jars  = platform.getJarFiles();

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            UnifiedModMeta mod = mods.get(i);
            File           jar = jars.get(i);

            int major = readClassMajor(jar);
            if (major <= 0) continue;

            if (major > CURRENT_MAJOR) {
                int requiredJava = majorToJava(major);
                int runtimeJava  = Runtime.version().feature();
                issues.add(StandaloneIssue.builder("Java Version Mismatch", Severity.CRITICAL)
                        .affectedMods(mod.id())
                        .description(mod.id() + " requires Java " + requiredJava
                                + " (class file major " + major + ") but you are running Java " + runtimeJava + ".")
                        .solutions(
                                "Install Java " + requiredJava + " or newer from https://adoptium.net",
                                "Check your launcher's Java settings and set it to Java " + requiredJava
                        )
                        .jarPath(jar.getAbsolutePath())
                        .build());
            }
        }
        return issues;
    }

    private int readClassMajor(File jar) {
        try (ZipFile zf = new ZipFile(jar)) {
            return zf.stream()
                    .filter(e -> e.getName().endsWith(".class")
                            && !e.getName().endsWith("module-info.class")
                            && !e.getName().endsWith("package-info.class"))
                    .findFirst()
                    .map(e -> readMajorFromEntry(zf, e))
                    .orElse(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    private int readMajorFromEntry(ZipFile zf, ZipEntry entry) {
        try (InputStream in = zf.getInputStream(entry)) {
            byte[] header = in.readNBytes(8);
            if (header.length < 8) return -1;
            // bytes 6+7 are the major version (big-endian)
            return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        } catch (Exception e) {
            return -1;
        }
    }

    private int majorToJava(int major) {
        for (int i = 0; i + 1 < MAJOR_TO_JAVA.length; i += 2) {
            if (MAJOR_TO_JAVA[i] == major) return MAJOR_TO_JAVA[i + 1];
        }
        return major - 44; // fallback approximation
    }
}
