package com.scr0ols.soundtweaks.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * Utilitários para config portável entre instâncias.
 */
public final class ConfigFileUtil {

    private ConfigFileUtil() {}

    /** Abre o explorador de ficheiros do sistema operativo na pasta de config do mod. */
    public static void openConfigFolder() {
        Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir());
    }

    /** Devolve o caminho absoluto da pasta de config (para mostrar ao utilizador). */
    public static String getConfigDirString() {
        return FabricLoader.getInstance().getConfigDir().toAbsolutePath().toString();
    }

    /**
     * Tenta resolver um caminho inserido pelo utilizador.
     * Aceita caminho absoluto ou nome de ficheiro relativo à pasta config.
     * Devolve null se o ficheiro não existir.
     */
    public static Path resolvePath(String input) {
        if (input == null || input.isBlank()) return null;
        input = input.trim();
        try {
            Path p = Path.of(input);
            if (p.isAbsolute() && p.toFile().isFile()) return p;
            // Tentar relativo à pasta config
            Path relative = FabricLoader.getInstance().getConfigDir().resolve(p);
            if (relative.toFile().isFile()) return relative;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
