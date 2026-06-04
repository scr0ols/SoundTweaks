package com.scr0ols.soundtweaks.client.gui;

import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * Utilities for portable config between instances.
 */
public final class ConfigFileUtil {

    private ConfigFileUtil() {}

    /** Opens the OS file explorer at the mod's config folder. */
    public static void openConfigFolder() {
        Util.getPlatform().openPath(FMLPaths.CONFIGDIR.get());
    }

    /** Returns the absolute path of the config folder (for display to the user). */
    public static String getConfigDirString() {
        return FMLPaths.CONFIGDIR.get().toAbsolutePath().toString();
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
            Path relative = FMLPaths.CONFIGDIR.get().resolve(p);
            if (relative.toFile().isFile()) return relative;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
