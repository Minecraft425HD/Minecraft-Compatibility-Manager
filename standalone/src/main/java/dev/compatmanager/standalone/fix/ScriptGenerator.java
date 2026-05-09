package dev.compatmanager.standalone.fix;

import dev.compatmanager.standalone.StandaloneIssue;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class ScriptGenerator {

    private final Path modsDir;

    public ScriptGenerator(Path modsDir) {
        this.modsDir = modsDir;
    }

    public void generate(List<StandaloneIssue> issues) throws IOException {
        StringBuilder bat = new StringBuilder();
        StringBuilder sh  = new StringBuilder();

        bat.append("@echo off\r\n");
        bat.append("REM Minecraft Compatibility Manager — Auto-fix script\r\n");
        bat.append("REM Run this script to disable conflicting mods\r\n\r\n");
        bat.append("md \"disabled-by-compatmanager\" 2>nul\r\n\r\n");

        sh.append("#!/bin/sh\n");
        sh.append("# Minecraft Compatibility Manager — Auto-fix script\n");
        sh.append("# Run this script to disable conflicting mods\n\n");
        sh.append("mkdir -p disabled-by-compatmanager\n\n");

        boolean anyFix = false;
        for (StandaloneIssue issue : issues) {
            if (!issue.canAutoFix()) continue;
            anyFix = true;

            String jarPath   = issue.jarPath();
            Path   jarFile   = Paths.get(jarPath);
            String jarName   = jarFile.getFileName().toString();
            String reason    = issue.description().replace("\"", "\\\"");
            String affectedStr = String.join(", ", issue.affectedMods());

            bat.append("REM ").append(issue.type()).append(": ").append(affectedStr).append("\r\n");
            bat.append("move /Y \"").append(jarPath.replace("/", "\\")).append("\" ")
               .append("\"disabled-by-compatmanager\\").append(jarName).append("\"\r\n\r\n");

            sh.append("# ").append(issue.type()).append(": ").append(affectedStr).append("\n");
            sh.append("mv -f '").append(jarPath.replace("'", "'\"'\"'")).append("' ")
              .append("'disabled-by-compatmanager/").append(jarName).append("'\n\n");
        }

        if (!anyFix) {
            bat.append("echo No fixable issues found.\r\n");
            sh.append("echo 'No fixable issues found.'\n");
        } else {
            bat.append("echo Done. Check disabled-by-compatmanager\\ for moved files.\r\n");
            sh.append("echo 'Done. Check disabled-by-compatmanager/ for moved files.'\n");
        }

        Path scriptDir = modsDir;
        Files.writeString(scriptDir.resolve("fix.bat"), bat.toString());
        Files.writeString(scriptDir.resolve("fix.sh"), sh.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        scriptDir.resolve("fix.sh").toFile().setExecutable(true);

        System.out.println("Scripts written: fix.bat + fix.sh");
    }
}
