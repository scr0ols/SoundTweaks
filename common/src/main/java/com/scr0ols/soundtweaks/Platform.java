package com.scr0ols.soundtweaks;

import java.nio.file.Path;

/**
 * Loader-supplied facts the shared code needs. Each loader entrypoint calls
 * {@link #setConfigDir(Path)} before anything reads or writes a config file.
 */
public final class Platform {

    private static volatile Path configDir;

    private Platform() {}

    public static void setConfigDir(Path dir) {
        configDir = dir;
    }

    /** The directory the loader reserves for mod configuration files. */
    public static Path configDir() {
        Path dir = configDir;
        if (dir == null) {
            throw new IllegalStateException("Platform.setConfigDir was not called before using the config directory");
        }
        return dir;
    }
}
