package com.scr0ols.soundtweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;

public class SoundConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("soundtweaks.json");

    // Mapa de soundId → multiplicador de volume (0.0 = mudo, 1.0 = normal, 2.0 = dobro)
    private static final Map<String, Float> volumes = new ConcurrentHashMap<>();
    private static volatile long lastSaveRequest = 0;

    // Devolve o volume configurado para um som, ou 1.0 se não estiver na config
    public static float getVolume(String soundId) {
        return volumes.getOrDefault(soundId, 1.0f);
    }

    // Define o volume de um som — guarda com debounce de 300ms via tickSave()
    public static void setVolume(String soundId, float volume) {
        volumes.put(soundId, Mth.clamp(volume, 0.0f, 2.0f));
        lastSaveRequest = System.currentTimeMillis();
    }

    // Chamado em ClientTickEvents — guarda apenas 300ms após o último ajuste
    public static void tickSave() {
        if (lastSaveRequest > 0 && System.currentTimeMillis() - lastSaveRequest > 300) {
            save();
            lastSaveRequest = 0;
        }
    }

    public static Map<String, Float> getAll() {
        return volumes;
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            SoundTweaks.LOGGER.info("SoundTweaks: sem config, a usar valores por defeito");
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            Type type = new TypeToken<Map<String, Float>>() {}.getType();
            Map<String, Float> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                volumes.clear();
                volumes.putAll(loaded);
            }
            SoundTweaks.LOGGER.info("SoundTweaks: config carregada ({} sons configurados)", volumes.size());
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar config", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_FILE, GSON.toJson(volumes));
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar config", e);
        }
    }

    /**
     * Importa volumes de um ficheiro externo (outro mundo/instância).
     * Substitui todos os valores actuais pelos do ficheiro importado.
     * @return número de sons importados, ou -1 em caso de erro
     */
    public static int importFrom(Path file) {
        try {
            String json = Files.readString(file);
            Type type = new TypeToken<Map<String, Float>>() {}.getType();
            Map<String, Float> loaded = GSON.fromJson(json, type);
            if (loaded == null) return -1;
            volumes.clear();
            loaded.forEach((id, vol) -> volumes.put(id, Mth.clamp(vol, 0.0f, 2.0f)));
            save();
            SoundTweaks.LOGGER.info("SoundTweaks: importados {} sons de {}", volumes.size(), file);
            return volumes.size();
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar config de {}", file, e);
            return -1;
        }
    }
}
