package dev.compatmanager.platform;

import java.util.ServiceLoader;

/**
 * Loads platform services via Java's ServiceLoader.
 * Each loader module registers its implementation under
 * META-INF/services/dev.compatmanager.platform.ModPlatform.
 */
public final class Services {

    public static final ModPlatform PLATFORM = load(ModPlatform.class);

    private Services() {}

    private static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No implementation found for " + clazz.getName()
                        + ". Make sure a platform module is on the classpath."));
    }
}
