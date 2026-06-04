package com.scr0ols.soundtweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Volume configuration persisted to disk.
 * Two static instances: SOUNDS (sounds, [0-2]) and BLOCKS (blocks, [0-1]).
 */
public class VolumeConfig {

    public static final VolumeConfig SOUNDS = new VolumeConfig("soundtweaks.json",        0.0f, 2.0f);
    public static final VolumeConfig BLOCKS = new VolumeConfig("soundtweaks_blocks.json", 0.0f, 1.0f);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Float>>() {}.getType();

    /** Shared executor for SOUNDS and BLOCKS — ensures saves never block the render thread. */
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundTweaks-Config-Save");
        t.setDaemon(true);
        return t;
    });

    /** Flush and shutdown the executor — call when the client stops to avoid losing the last save. */
    public static void shutdownSaveExecutor() {
        SAVE_EXECUTOR.shutdown();
        try {
            if (!SAVE_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS))
                SAVE_EXECUTOR.shutdownNow();
        } catch (InterruptedException e) {
            SAVE_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private final Map<String, Float> volumes = new ConcurrentHashMap<>();
    private volatile long lastSaveRequest = 0;
    private final Path  configFile;
    private final float minVol;
    private final float maxVol;

    private VolumeConfig(String fileName, float minVol, float maxVol) {
        this.configFile = FabricLoader.getInstance().getConfigDir().resolve(fileName);
        this.minVol     = minVol;
        this.maxVol     = maxVol;
    }

    public float getVolume(String id) {
        return volumes.getOrDefault(id, 1.0f);
    }

    public void setVolume(String id, float volume) {
        volumes.put(id, Mth.clamp(volume, minVol, maxVol));
        lastSaveRequest = System.currentTimeMillis();
    }

    public Map<String, Float> getAll() { return volumes; }

    public void tickSave() {
        if (lastSaveRequest > 0 && System.currentTimeMillis() - lastSaveRequest > 300) {
            lastSaveRequest = 0;
            // Immutable snapshot to avoid race condition during async write
            final String json = GSON.toJson(new LinkedHashMap<>(volumes));
            final Path   target = configFile;
            SAVE_EXECUTOR.submit(() -> {
                try {
                    Files.writeString(target, json);
                } catch (IOException e) {
                    SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar {}", target.getFileName(), e);
                }
            });
        }
    }

    public void load() {
        if (!Files.exists(configFile)) return;
        try {
            Map<String, Float> loaded = GSON.fromJson(Files.readString(configFile), MAP_TYPE);
            if (loaded != null) {
                volumes.clear();
                loaded.forEach((id, vol) -> volumes.put(id, Mth.clamp(vol, minVol, maxVol)));
            }
            SoundTweaks.LOGGER.info("SoundTweaks: {} carregado ({} entradas)",
                    configFile.getFileName(), volumes.size());
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar {}", configFile.getFileName(), e);
        }
    }

    public void save() {
        try {
            Files.writeString(configFile, GSON.toJson(volumes));
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar {}", configFile.getFileName(), e);
        }
    }

    /**
     * Exports current volumes to an external file.
     * @return number of entries exported, or -1 on error
     */
    public int exportTo(Path file) {
        try {
            Files.writeString(file, GSON.toJson(new LinkedHashMap<>(volumes)));
            SoundTweaks.LOGGER.info("SoundTweaks: exportadas {} entradas para {}", volumes.size(), file);
            return volumes.size();
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao exportar para {}", file, e);
            return -1;
        }
    }

    /**
     * Imports volumes from an external file. Replaces all current values.
     * @return number of entries imported, or -1 on error
     */
    public int importFrom(Path file) {
        try {
            Map<String, Float> loaded = GSON.fromJson(Files.readString(file), MAP_TYPE);
            if (loaded == null) return -1;

            // Validate entries before touching any state
            Map<String, Float> validated = new LinkedHashMap<>();
            loaded.forEach((id, vol) -> {
                if (vol != null && Float.isFinite(vol))
                    validated.put(id, Mth.clamp(vol, minVol, maxVol));
            });

            // Write to disk FIRST — if it fails, memory stays intact
            Files.writeString(configFile, GSON.toJson(validated));

            volumes.clear();
            volumes.putAll(validated);

            SoundTweaks.LOGGER.info("SoundTweaks: importadas {} entradas de {}", volumes.size(), file);
            return volumes.size();
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar de {}", file, e);
            return -1;
        }
    }
}
