package dev.compatmanager.standalone.fix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class AutoFixer {

    public record FixRecord(
            String timestamp,
            String modId,
            String originalPath,
            String disabledPath,
            String reason,
            String issueType
    ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path      disabledDir;
    private final Path      logFile;
    private final boolean   dryRun;
    private final Set<String> processed = new HashSet<>(); // deduplicate by jar path

    public AutoFixer(Path modsDir, boolean dryRun) {
        this.disabledDir = modsDir.resolve("disabled-by-compatmanager");
        this.logFile     = disabledDir.resolve("fix-log.json");
        this.dryRun      = dryRun;
    }

    /**
     * Moves the JAR at {@code jarPath} into the disabled folder.
     * Returns true if the operation succeeded (or would succeed in dry-run mode).
     * Silently skips duplicate calls for the same JAR path.
     */
    public boolean disableMod(String modId, String jarPath, String reason) {
        return disableMod(modId, jarPath, reason, "Incompatibility");
    }

    public boolean disableMod(String modId, String jarPath, String reason, String issueType) {
        if (jarPath == null || jarPath.isBlank()) return false;
        if (!processed.add(jarPath)) return true; // already handled this JAR
        Path src = Paths.get(jarPath);
        if (!src.toFile().exists()) return false;

        Path dst = disabledDir.resolve(src.getFileName());

        if (dryRun) {
            System.out.println("[DRY RUN] Would move: " + src.getFileName()
                    + " → disabled-by-compatmanager/" + dst.getFileName());
            return true;
        }

        try {
            Files.createDirectories(disabledDir);
            try {
                Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            appendLog(new FixRecord(
                    Instant.now().toString(), modId, src.toAbsolutePath().toString(),
                    dst.toAbsolutePath().toString(), reason, issueType));
            System.out.println("[FIX] Disabled: " + src.getFileName()
                    + " → disabled-by-compatmanager/");
            return true;
        } catch (IOException e) {
            System.err.println("[FIX ERROR] Could not move " + src.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    public List<FixRecord> readLog() {
        if (!logFile.toFile().exists()) return List.of();
        try (Reader r = new FileReader(logFile.toFile())) {
            List<FixRecord> list = GSON.fromJson(r, new TypeToken<List<FixRecord>>(){}.getType());
            return list != null ? list : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void appendLog(FixRecord record) throws IOException {
        List<FixRecord> existing = new ArrayList<>(readLog());
        existing.add(record);
        try (Writer w = new FileWriter(logFile.toFile())) {
            GSON.toJson(existing, w);
        }
    }
}
