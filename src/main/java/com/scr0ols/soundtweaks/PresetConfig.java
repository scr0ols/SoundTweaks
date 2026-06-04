package com.scr0ols.soundtweaks;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PresetConfig {

    public static final int CUSTOM_COLOR_INDEX = 18;

    public static final int[] PRESET_COLORS = {
        // Retro Game — row 1: Red, Gold, Neon Green, Vivid Blue, Purple, Electric Blue
        0xFFE84118, 0xFFFBC531, 0xFF4CD137, 0xFF0097E6, 0xFF8C7AE6, 0xFF00A8FF,
        // row 2: Ochre, Lime, Denim, Brick, Navy, Night
        0xFFE1B12C, 0xFF44BD32, 0xFF487EB0, 0xFFC23616, 0xFF273C75, 0xFF192A56,
        // row 3: Orange, Burnt, Teal, Orchid, Lavender, Violet
        0xFFF79F1F, 0xFFEE5A24, 0xFF1289A7, 0xFFD980FA, 0xFF9980FA, 0xFF5758BB
    };

    public static class Preset {
        public String name;
        public int    colorIndex;
        public int    customColor;
        public int    shortcutKey;
        public int    shortcutHeldKey;
        public int    shortcutHeldKey2;
        public final Map<String, Float> sounds;
        public final Map<String, Float> blocks;

        public Preset(String name) {
            this.name             = name;
            this.colorIndex       = 0;
            this.customColor      = 0;
            this.shortcutKey      = 0;
            this.shortcutHeldKey  = 0;
            this.shortcutHeldKey2 = 0;
            this.sounds           = new LinkedHashMap<>();
            this.blocks           = new LinkedHashMap<>();
        }

        public int argbColor() {
            if (colorIndex == CUSTOM_COLOR_INDEX)
                return customColor != 0 ? (customColor | 0xFF000000) : 0xFF888888;
            int idx = colorIndex >= 0 && colorIndex < PRESET_COLORS.length ? colorIndex : 0;
            return PRESET_COLORS[idx];
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("soundtweaks_presets.json");
    private static final Path LEGACY_DIR  = FabricLoader.getInstance().getConfigDir()
            .resolve("soundtweaks_presets");

    private static final List<Preset> presets       = new CopyOnWriteArrayList<>();
    private static final Set<String>  activeNames   = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final List<String> favoriteNames = new CopyOnWriteArrayList<>();
    private static volatile long      lastSaveRequest = 0;

    /** Dedicated executor for async saves — never blocks the render thread. */
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundTweaks-Preset-Save");
        t.setDaemon(true);
        return t;
    });

    /** Flush and shutdown the executor — call when the client stops. */
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

    /**
     * Immutable cache of active presets — rebuilt only when activeNames changes.
     * Avoids new ArrayList<>() in the audio hot path (called for every sound played).
     */
    private static volatile List<Preset> cachedActivePresets = Collections.emptyList();

    // synchronized: activeNames (synchronizedSet) iteration + volatile write must be atomic
    private static synchronized void rebuildActivePresetsCache() {
        List<Preset> result = new ArrayList<>();
        for (Preset p : presets) if (activeNames.contains(p.name)) result.add(p);
        cachedActivePresets = Collections.unmodifiableList(result);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public static List<Preset> getPresets() { return Collections.unmodifiableList(presets); }

    public static boolean isActive(String name)   { return activeNames.contains(name); }
    public static boolean isFavorite(String name) { return favoriteNames.contains(name); }

    /** Returns an immutable snapshot of active presets. Zero allocations in the hot path. */
    public static List<Preset> getActivePresets() {
        return cachedActivePresets;
    }

    public static List<Preset> getFavoritePresets() {
        return favoriteNames.stream()
                .map(n -> presets.stream().filter(p -> p.name.equals(n)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public static void setActive(String name, boolean active) {
        if (active) activeNames.add(name);
        else        activeNames.remove(name);
        rebuildActivePresetsCache();
        markDirty();
    }

    public static void setFavorite(String name, boolean favorite) {
        if (favorite) { if (!favoriteNames.contains(name)) favoriteNames.add(name); }
        else          { favoriteNames.remove(name); }
        markDirty();
    }

    public static Preset createFromCurrentConfig(String name) {
        String unique = uniqueName(name);
        Preset p = new Preset(unique);
        VolumeConfig.SOUNDS.getAll().forEach((id, vol) -> { if (vol != 1.0f) p.sounds.put(id, vol); });
        VolumeConfig.BLOCKS.getAll().forEach((id, vol) -> { if (vol != 1.0f) p.blocks.put(id, vol); });
        presets.add(p);
        markDirty();
        return p;
    }

    public static void deletePreset(String name) {
        presets.removeIf(p -> p.name.equals(name));
        activeNames.remove(name);
        favoriteNames.remove(name);
        rebuildActivePresetsCache();
        markDirty();
    }

    public static void renamePreset(String oldName, String newName) {
        if (newName.isBlank() || oldName.equals(newName)) return;
        String unique = uniqueName(newName);
        for (Preset p : presets) {
            if (p.name.equals(oldName)) { p.name = unique; break; }
        }
        if (activeNames.remove(oldName))  activeNames.add(unique);
        int fi = favoriteNames.indexOf(oldName);
        if (fi >= 0) favoriteNames.set(fi, unique);
        rebuildActivePresetsCache();
        markDirty();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void markDirty() { lastSaveRequest = System.currentTimeMillis(); }

    public static void tickSave() {
        if (lastSaveRequest > 0 && System.currentTimeMillis() - lastSaveRequest > 300) {
            lastSaveRequest = 0;
            // save() uses thread-safe collections (CopyOnWriteArrayList, synchronizedSet)
            // — safe to call from a background thread
            SAVE_EXECUTOR.submit(PresetConfig::save);
        }
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(CONFIG_FILE), JsonObject.class);
            if (root == null) return;

            presets.clear();
            activeNames.clear();
            favoriteNames.clear();

            JsonArray activeArr = root.getAsJsonArray("activePresets");
            if (activeArr != null) activeArr.forEach(el -> activeNames.add(el.getAsString()));

            JsonArray favArr = root.getAsJsonArray("favoritePresets");
            if (favArr != null) favArr.forEach(el -> favoriteNames.add(el.getAsString()));

            JsonArray presetsArr = root.getAsJsonArray("presets");
            if (presetsArr == null) return;

            boolean isLegacyFormat = false;
            for (JsonElement el : presetsArr) {
                if (el.isJsonPrimitive()) { isLegacyFormat = true; break; }
            }

            if (isLegacyFormat) {
                migrateFromLegacy(presetsArr, activeArr, favArr);
            } else {
                for (JsonElement el : presetsArr) {
                    if (!el.isJsonObject()) continue;
                    Preset p = parsePreset(el.getAsJsonObject());
                    if (p != null) presets.add(p);
                }
                SoundTweaks.LOGGER.info("SoundTweaks: {} presets carregados", presets.size());
            }

            // Remove orphan references (preset deleted but still in favoriteNames/activeNames)
            Set<String> existingNames = new HashSet<>();
            for (Preset p : presets) existingNames.add(p.name);
            boolean hadOrphans = favoriteNames.removeIf(n -> !existingNames.contains(n));
            hadOrphans |= activeNames.removeIf(n -> !existingNames.contains(n));
            // If there were orphans, persist the cleanup immediately so the file stays consistent
            if (hadOrphans) {
                SoundTweaks.LOGGER.info("SoundTweaks: referências órfãs removidas de presets config");
                SAVE_EXECUTOR.submit(PresetConfig::save); // async — load() runs on the render thread
            }

            rebuildActivePresetsCache();
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar presets", e);
        }
    }

    private static void migrateFromLegacy(JsonArray presetsArr, JsonArray activeArr, JsonArray favArr) {
        Map<String, String> uuidToName = new LinkedHashMap<>();
        for (JsonElement el : presetsArr) {
            if (el.isJsonPrimitive()) {
                String uuid = el.getAsString();
                Path file = LEGACY_DIR.resolve(uuid + ".json");
                if (!Files.exists(file)) continue;
                try {
                    JsonObject obj = GSON.fromJson(Files.readString(file), JsonObject.class);
                    Preset p = parsePreset(obj);
                    if (p != null) { presets.add(p); uuidToName.put(uuid, p.name); }
                } catch (Exception e) {
                    SoundTweaks.LOGGER.error("SoundTweaks: erro ao migrar preset {}", uuid, e);
                }
            } else if (el.isJsonObject()) {
                Preset p = parsePreset(el.getAsJsonObject());
                if (p != null) presets.add(p);
            }
        }
        activeNames.clear();
        if (activeArr != null)
            for (JsonElement el : activeArr) {
                String n = uuidToName.get(el.getAsString());
                if (n != null) activeNames.add(n);
            }
        favoriteNames.clear();
        if (favArr != null)
            for (JsonElement el : favArr) {
                String n = uuidToName.get(el.getAsString());
                if (n != null && !favoriteNames.contains(n)) favoriteNames.add(n);
            }
        rebuildActivePresetsCache();
        SoundTweaks.LOGGER.info("SoundTweaks: {} presets migrados do formato antigo", presets.size());
        SAVE_EXECUTOR.submit(PresetConfig::save); // async — migrateFromLegacy() is called from load() on the render thread
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            JsonArray presetsArr = new JsonArray();
            for (Preset p : presets) presetsArr.add(serializePreset(p));
            root.add("presets", presetsArr);
            JsonArray activeArr = new JsonArray();
            activeNames.forEach(activeArr::add);
            root.add("activePresets", activeArr);
            JsonArray favArr = new JsonArray();
            favoriteNames.forEach(favArr::add);
            root.add("favoritePresets", favArr);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar presets", e);
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Exports all current presets to an external JSON file.
     * @return number of presets exported, or -1 on error
     */
    public static int exportTo(Path file) {
        try {
            JsonObject root = new JsonObject();
            JsonArray presetsArr = new JsonArray();
            for (Preset p : presets) presetsArr.add(serializePreset(p));
            root.add("presets", presetsArr);
            Files.writeString(file, GSON.toJson(root));
            SoundTweaks.LOGGER.info("SoundTweaks: exportados {} presets para {}", presets.size(), file);
            return presets.size();
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao exportar presets para {}", file, e);
            return -1;
        }
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    public static int importFrom(Path file) {
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file));
            List<JsonObject> toImport = new ArrayList<>();

            if (root.isJsonArray()) {
                root.getAsJsonArray().forEach(el -> { if (el.isJsonObject()) toImport.add(el.getAsJsonObject()); });
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("presets") && obj.get("presets").isJsonArray()) {
                    for (JsonElement el : obj.getAsJsonArray("presets")) {
                        if (el.isJsonObject()) {
                            toImport.add(el.getAsJsonObject());
                        } else if (el.isJsonPrimitive()) {
                            Path pfile = LEGACY_DIR.resolve(el.getAsString() + ".json");
                            if (Files.exists(pfile))
                                try { toImport.add(GSON.fromJson(Files.readString(pfile), JsonObject.class)); }
                                catch (Exception ignored) {}
                        }
                    }
                } else if (obj.has("name")) {
                    toImport.add(obj);
                }
            }

            if (toImport.isEmpty()) return 0;
            int added = 0;
            for (JsonObject obj : toImport) {
                String name = obj.has("name") ? obj.get("name").getAsString() : "Imported Preset";
                name = uniqueName(name);
                Preset p = new Preset(name);
                if (obj.has("colorIndex"))  p.colorIndex  = obj.get("colorIndex").getAsInt();
                if (obj.has("customColor")) p.customColor = obj.get("customColor").getAsInt();
                readFloatMap(obj.getAsJsonObject("sounds"), p.sounds);
                readFloatMap(obj.getAsJsonObject("blocks"), p.blocks);
                presets.add(p);
                activeNames.add(name);
                added++;
            }
            if (added > 0) { rebuildActivePresetsCache(); save(); }
            return added;
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar presets de {}", file, e);
            return -1;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String uniqueName(String base) {
        Set<String> names = new HashSet<>();
        for (Preset p : presets) names.add(p.name);
        if (!names.contains(base)) return base;
        int suffix = 2;
        while (names.contains(base + " (" + suffix + ")")) suffix++;
        return base + " (" + suffix + ")";
    }

    private static Preset parsePreset(JsonObject obj) {
        if (obj == null || !obj.has("name")) return null;
        Preset p = new Preset(obj.get("name").getAsString());
        if (obj.has("colorIndex"))       p.colorIndex       = obj.get("colorIndex").getAsInt();
        if (obj.has("customColor"))      p.customColor      = obj.get("customColor").getAsInt();
        if (obj.has("shortcutKey"))      p.shortcutKey      = obj.get("shortcutKey").getAsInt();
        if (obj.has("shortcutHeldKey"))  p.shortcutHeldKey  = obj.get("shortcutHeldKey").getAsInt();
        if (obj.has("shortcutHeldKey2")) p.shortcutHeldKey2 = obj.get("shortcutHeldKey2").getAsInt();
        readFloatMap(obj.getAsJsonObject("sounds"), p.sounds);
        readFloatMap(obj.getAsJsonObject("blocks"), p.blocks);
        return p;
    }

    private static JsonObject serializePreset(Preset p) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name",           p.name);
        obj.addProperty("colorIndex",     p.colorIndex);
        if (p.customColor != 0) obj.addProperty("customColor", p.customColor);
        obj.addProperty("shortcutKey",      p.shortcutKey);
        obj.addProperty("shortcutHeldKey",  p.shortcutHeldKey);
        obj.addProperty("shortcutHeldKey2", p.shortcutHeldKey2);
        obj.add("sounds", toJsonObject(p.sounds));
        obj.add("blocks", toJsonObject(p.blocks));
        return obj;
    }

    private static void readFloatMap(JsonObject src, Map<String, Float> dst) {
        if (src == null) return;
        src.entrySet().forEach(e -> {
            JsonElement val = e.getValue();
            // Ignore nulls and non-primitives (e.g. malformed arrays/objects)
            if (val == null || !val.isJsonPrimitive()) return;
            float v = val.getAsFloat();
            if (Float.isFinite(v)) dst.put(e.getKey(), Mth.clamp(v, 0f, 2f));
        });
    }

    private static JsonObject toJsonObject(Map<String, Float> map) {
        JsonObject obj = new JsonObject();
        map.forEach(obj::addProperty);
        return obj;
    }
}
