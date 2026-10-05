package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.Platform;
import com.mojang.blaze3d.Blaze3D;

import java.nio.file.Path;

/**
 * Utilities for portable config between instances.
 */
public final class ConfigFileUtil {

    private ConfigFileUtil() {}

    /** Opens the OS file explorer at the mod's config folder. */
    public static void openConfigFolder() {
        Blaze3D.openPath(Platform.configDir());
    }

    /** Returns the absolute path of the config folder (for display to the user). */
    public static String getConfigDirString() {
        return Platform.configDir().toAbsolutePath().toString();
    }

    /**
     * Attempts to resolve a path entered by the user.
     * Accepts an absolute path or a filename relative to the config folder.
     * Returns null if the file does not exist.
     */
    public static Path resolvePath(String input) {
        if (input == null || input.isBlank()) return null;
        input = input.trim();
        try {
            Path p = Path.of(input);
            if (p.isAbsolute() && p.toFile().isFile()) return p;
            // Try relative to the config folder
            Path relative = Platform.configDir().resolve(p);
            if (relative.toFile().isFile()) return relative;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
