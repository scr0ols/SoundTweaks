package com.scr0ols.soundtweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SoundDeduplicationConfig {

    public static final SoundDeduplicationConfig INSTANCE = new SoundDeduplicationConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "soundtweaks_dedup.json";

    private volatile int maxPerPosition = 3;
    private volatile int maxPerSoundId  = 10;

    private SoundDeduplicationConfig() {}

    public int getMaxPerPosition() { return maxPerPosition; }
    public int getMaxPerSoundId()  { return maxPerSoundId; }

    public void setMaxPerPosition(int value) { maxPerPosition = Math.max(1, value); }
    public void setMaxPerSoundId(int value)  { maxPerSoundId  = Math.max(1, value); }

    public void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            save();
            return;
        }
        try {
            Data data = GSON.fromJson(Files.readString(path), Data.class);
            if (data != null) {
                if (data.maxPerPosition > 0) maxPerPosition = data.maxPerPosition;
                if (data.maxPerSoundId  > 0) maxPerSoundId  = data.maxPerSoundId;
            }
            SoundTweaks.LOGGER.info("SoundTweaks: dedup config carregada (pos={}, id={})",
                    maxPerPosition, maxPerSoundId);
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar {}", FILE_NAME, e);
        }
    }

    public void save() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try {
            Data data = new Data();
            data.maxPerPosition = maxPerPosition;
            data.maxPerSoundId  = maxPerSoundId;
            Files.writeString(path, GSON.toJson(data));
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar {}", FILE_NAME, e);
        }
    }

    private static class Data {
        int maxPerPosition = 3;
        int maxPerSoundId  = 10;
    }
}
