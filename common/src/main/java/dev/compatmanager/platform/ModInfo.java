package dev.compatmanager.platform;

import java.util.List;

public record ModInfo(
        String id,
        String version,
        String displayName,
        String description,
        List<DependencyInfo> dependencies
) {}
