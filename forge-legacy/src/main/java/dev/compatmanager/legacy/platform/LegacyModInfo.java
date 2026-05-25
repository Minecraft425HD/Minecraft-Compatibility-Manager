package dev.compatmanager.legacy.platform;

import java.util.List;

public record LegacyModInfo(
        String modId,
        String version,
        String name,
        List<LegacyDepInfo> dependencies
) {}
