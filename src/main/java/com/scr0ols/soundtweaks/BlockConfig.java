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

public class BlockConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("soundtweaks_blocks.json");

    private static final Map<String, Float> volumes = new ConcurrentHashMap<>();
    private static volatile long lastSaveRequest = 0;

    public static float getVolume(String blockId) {
        return volumes.getOrDefault(blockId, 1.0f);
    }

    public static void setVolume(String blockId, float volume) {
        volumes.put(blockId, Mth.clamp(volume, 0.0f, 1.0f));
        lastSaveRequest = System.currentTimeMillis();

        // Blocos "master": cascata para todos os SoundConfig entries do grupo
        String groupPrefix = MissingBlockRegistry.GROUP_PREFIXES.get(blockId);
        if (groupPrefix != null) {
            final String prefix = groupPrefix;
            final float clamped = Mth.clamp(volume, 0.0f, 1.0f);
            SoundRegistry.getAll().stream()
                    .filter(id -> id.startsWith(prefix))
                    .forEach(id -> SoundConfig.setVolume(id, clamped));
        }
    }

    public static Map<String, Float> getAll() {
        return volumes;
    }

    public static void tickSave() {
        if (lastSaveRequest > 0 && System.currentTimeMillis() - lastSaveRequest > 300) {
            save();
            lastSaveRequest = 0;
        }
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try {
            String json = Files.readString(CONFIG_FILE);
            Type type = new TypeToken<Map<String, Float>>() {}.getType();
            Map<String, Float> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                volumes.clear();
                volumes.putAll(loaded);
            }
            SoundTweaks.LOGGER.info("SoundTweaks: config de blocos carregada ({} blocos configurados)", volumes.size());
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar config de blocos", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_FILE, GSON.toJson(volumes));
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar config de blocos", e);
        }
    }

    /** Importa volumes de blocos de um ficheiro externo. Substitui os valores actuais. */
    public static int importFrom(Path file) {
        try {
            String json = Files.readString(file);
            Type type = new TypeToken<Map<String, Float>>() {}.getType();
            Map<String, Float> loaded = GSON.fromJson(json, type);
            if (loaded == null) return -1;
            volumes.clear();
            loaded.forEach((id, vol) -> volumes.put(id, net.minecraft.util.Mth.clamp(vol, 0.0f, 2.0f)));
            save();
            return volumes.size();
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar config de blocos de {}", file, e);
            return -1;
        }
    }
}
