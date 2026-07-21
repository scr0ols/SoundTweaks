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

    /** Link shown to users when import ID conflicts are detected. */
    public static final String WIKI_PRESETS_URL = "https://github.com/scr0ols/SoundTweaks/wiki/Managing-Presets";

    public static final int[] PRESET_COLORS = {
        // Retro Game — row 1: Red, Gold, Neon Green, Vivid Blue, Purple, Electric Blue
        0xFFE84118, 0xFFFBC531, 0xFF4CD137, 0xFF0097E6, 0xFF8C7AE6, 0xFF00A8FF,
        // row 2: Ochre, Lime, Denim, Brick, Navy, Night
        0xFFE1B12C, 0xFF44BD32, 0xFF487EB0, 0xFFC23616, 0xFF273C75, 0xFF192A56,
        // row 3: Orange, Burnt, Teal, Orchid, Lavender, Violet
        0xFFF79F1F, 0xFFEE5A24, 0xFF1289A7, 0xFFD980FA, 0xFF9980FA, 0xFF5758BB
    };

    public record ImportResult(int imported, int conflictsReassigned) {}

    public static class Preset {
        public int    id;
        public String name;
        public int    colorIndex;
        public int    customColor;
        public int    shortcutKey;
        public int    shortcutHeldKey;
        public int    shortcutHeldKey2;
        public final Map<String, Float> sounds;
        public final Map<String, Float> blocks;

        public Preset(int id, String name) {
            this.id               = id;
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

    private static final List<Preset>  presets      = new CopyOnWriteArrayList<>();
    private static final Set<Integer>  activeIds    = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final List<Integer> favoriteIds  = new CopyOnWriteArrayList<>();
    private static volatile long       lastSaveRequest = 0;

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
     * Immutable cache of active presets — rebuilt only when activeIds changes.
     * Avoids new ArrayList<>() in the audio hot path (called for every sound played).
     */
    private static volatile List<Preset> cachedActivePresets = Collections.emptyList();

    // synchronized: activeIds (synchronizedSet) iteration + volatile write must be atomic
    private static synchronized void rebuildActivePresetsCache() {
        List<Preset> result = new ArrayList<>();
        for (Preset p : presets) if (activeIds.contains(p.id)) result.add(p);
        cachedActivePresets = Collections.unmodifiableList(result);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public static List<Preset> getPresets() { return Collections.unmodifiableList(presets); }

    public static boolean isActive(int id)   { return activeIds.contains(id); }
    public static boolean isFavorite(int id) { return favoriteIds.contains(id); }

    /** Returns an immutable snapshot of active presets. Zero allocations in the hot path. */
    public static List<Preset> getActivePresets() {
        return cachedActivePresets;
    }

    public static List<Preset> getFavoritePresets() {
        return favoriteIds.stream()
                .map(id -> presets.stream().filter(p -> p.id == id).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public static void setActive(int id, boolean active) {
        if (active) activeIds.add(id);
        else        activeIds.remove(id);
        rebuildActivePresetsCache();
        markDirty();
    }

    public static void setFavorite(int id, boolean favorite) {
        if (favorite) { if (!favoriteIds.contains(id)) favoriteIds.add(id); }
        else          { favoriteIds.remove(id); }
        markDirty();
    }

    public static Preset createFromCurrentConfig(String name) {
        int newId = nextId();
        Preset p = new Preset(newId, name);
        VolumeConfig.SOUNDS.getAll().forEach((sid, vol) -> { if (vol != 1.0f) p.sounds.put(sid, vol); });
        VolumeConfig.BLOCKS.getAll().forEach((sid, vol) -> { if (vol != 1.0f) p.blocks.put(sid, vol); });
        presets.add(p);
        markDirty();
        return p;
    }

    public static void deletePreset(int id) {
        presets.removeIf(p -> p.id == id);
        activeIds.remove(id);
        favoriteIds.remove(id);
        rebuildActivePresetsCache();
        markDirty();
    }

    /** Rename is now trivial: the id stays stable, only the display name changes. */
    public static void renamePreset(int id, String newName) {
        if (newName.isBlank()) return;
        for (Preset p : presets) {
            if (p.id == id) { p.name = newName; break; }
        }
        markDirty();
    }

    private static int nextId() {
        return presets.stream().mapToInt(p -> p.id).max().orElse(-1) + 1;
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
            activeIds.clear();
            favoriteIds.clear();

            JsonArray presetsArr = root.getAsJsonArray("presets");
            if (presetsArr == null) return;

            // Detect format: legacy-uuid (primitives), name-keyed (objects without "id"), or current (objects with "id")
            boolean isLegacyUuid = false;
            boolean isNameKeyed  = false;
            for (JsonElement el : presetsArr) {
                if (el.isJsonPrimitive()) { isLegacyUuid = true; break; }
                if (el.isJsonObject() && !el.getAsJsonObject().has("id")) { isNameKeyed = true; break; }
            }

            JsonArray activeArr = root.getAsJsonArray("activePresets");
            JsonArray favArr    = root.getAsJsonArray("favoritePresets");

            if (isLegacyUuid) {
                migrateFromLegacy(presetsArr, activeArr, favArr);
            } else if (isNameKeyed) {
                migrateFromNameKeyed(presetsArr, activeArr, favArr);
            } else {
                // Current format: id-based
                for (JsonElement el : presetsArr) {
                    if (!el.isJsonObject()) continue;
                    Preset p = parsePreset(el.getAsJsonObject());
                    if (p != null) presets.add(p);
                }
                if (activeArr != null) activeArr.forEach(el -> activeIds.add(el.getAsInt()));
                if (favArr    != null) favArr.forEach(el    -> favoriteIds.add(el.getAsInt()));
                SoundTweaks.LOGGER.info("SoundTweaks: {} presets carregados", presets.size());
            }

            // Remove orphan id references (preset deleted but id still in active/favorite lists)
            Set<Integer> existingIds = new HashSet<>();
            for (Preset p : presets) existingIds.add(p.id);
            boolean hadOrphans = favoriteIds.removeIf(id -> !existingIds.contains(id));
            hadOrphans |= activeIds.removeIf(id -> !existingIds.contains(id));
            if (hadOrphans) {
                SoundTweaks.LOGGER.info("SoundTweaks: referências órfãs removidas de presets config");
                SAVE_EXECUTOR.submit(PresetConfig::save);
            }

            rebuildActivePresetsCache();
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar presets", e);
        }
    }

    /** Migrates from name-keyed format (activePresets/favoritePresets were arrays of strings). */
    private static void migrateFromNameKeyed(JsonArray presetsArr, JsonArray activeArr, JsonArray favArr) {
        Set<String> activeNames   = new HashSet<>();
        List<String> favoriteNames = new ArrayList<>();
        if (activeArr != null) activeArr.forEach(el -> activeNames.add(el.getAsString()));
        if (favArr    != null) favArr.forEach(el    -> { String n = el.getAsString(); if (!favoriteNames.contains(n)) favoriteNames.add(n); });

        int seq = 0;
        for (JsonElement el : presetsArr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            Preset p = parsePresetLegacy(seq++, obj);
            if (p != null) {
                presets.add(p);
                if (activeNames.contains(p.name))   activeIds.add(p.id);
                int fi = favoriteNames.indexOf(p.name);
                if (fi >= 0) favoriteIds.add(p.id);
            }
        }
        // favoriteIds must preserve the original order from favoriteNames
        favoriteIds.sort((a, b) -> {
            int ia = presets.stream().filter(p -> p.id == a).findFirst().map(p -> favoriteNames.indexOf(p.name)).orElse(0);
            int ib = presets.stream().filter(p -> p.id == b).findFirst().map(p -> favoriteNames.indexOf(p.name)).orElse(0);
            return Integer.compare(ia, ib);
        });
        rebuildActivePresetsCache();
        SoundTweaks.LOGGER.info("SoundTweaks: {} presets migrados do formato name-keyed para id-based", presets.size());
        SAVE_EXECUTOR.submit(PresetConfig::save);
    }

    private static void migrateFromLegacy(JsonArray presetsArr, JsonArray activeArr, JsonArray favArr) {
        Map<String, Integer> uuidToId = new LinkedHashMap<>();
        int seq = 0;
        for (JsonElement el : presetsArr) {
            if (el.isJsonPrimitive()) {
                String uuid = el.getAsString();
                Path file = LEGACY_DIR.resolve(uuid + ".json");
                if (!Files.exists(file)) continue;
                try {
                    JsonObject obj = GSON.fromJson(Files.readString(file), JsonObject.class);
                    Preset p = parsePresetLegacy(seq++, obj);
                    if (p != null) { presets.add(p); uuidToId.put(uuid, p.id); }
                } catch (Exception e) {
                    SoundTweaks.LOGGER.error("SoundTweaks: erro ao migrar preset {}", uuid, e);
                }
            } else if (el.isJsonObject()) {
                Preset p = parsePresetLegacy(seq++, el.getAsJsonObject());
                if (p != null) presets.add(p);
            }
        }
        activeIds.clear();
        if (activeArr != null)
            for (JsonElement el : activeArr) {
                Integer id = uuidToId.get(el.getAsString());
                if (id != null) activeIds.add(id);
            }
        favoriteIds.clear();
        if (favArr != null)
            for (JsonElement el : favArr) {
                Integer id = uuidToId.get(el.getAsString());
                if (id != null && !favoriteIds.contains(id)) favoriteIds.add(id);
            }
        rebuildActivePresetsCache();
        SoundTweaks.LOGGER.info("SoundTweaks: {} presets migrados do formato uuid para id-based", presets.size());
        SAVE_EXECUTOR.submit(PresetConfig::save);
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            JsonArray presetsArr = new JsonArray();
            for (Preset p : presets) presetsArr.add(serializePreset(p));
            root.add("presets", presetsArr);
            JsonArray activeArr = new JsonArray();
            activeIds.forEach(activeArr::add);
            root.add("activePresets", activeArr);
            JsonArray favArr = new JsonArray();
            favoriteIds.forEach(favArr::add);
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

    /**
     * Imports presets from a file.
     * If an imported preset has an id that already exists, a new id is assigned automatically
     * and the conflict count is reported in the returned {@link ImportResult}.
     * Returns null on error.
     */
    public static ImportResult importFrom(Path file) {
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

            if (toImport.isEmpty()) return new ImportResult(0, 0);

            Set<Integer> existingIds = new HashSet<>();
            for (Preset p : presets) existingIds.add(p.id);

            int added = 0, reassigned = 0;
            for (JsonObject obj : toImport) {
                String name = obj.has("name") ? obj.get("name").getAsString() : "Imported Preset";
                int importedId = obj.has("id") ? obj.get("id").getAsInt() : -1;

                int assignedId;
                if (importedId < 0 || existingIds.contains(importedId)) {
                    assignedId = nextId();
                    if (importedId >= 0) reassigned++;
                } else {
                    assignedId = importedId;
                }
                existingIds.add(assignedId);

                Preset p = new Preset(assignedId, name);
                if (obj.has("colorIndex"))       p.colorIndex       = obj.get("colorIndex").getAsInt();
                if (obj.has("customColor"))      p.customColor      = obj.get("customColor").getAsInt();
                if (obj.has("shortcutKey"))      p.shortcutKey      = obj.get("shortcutKey").getAsInt();
                if (obj.has("shortcutHeldKey"))  p.shortcutHeldKey  = obj.get("shortcutHeldKey").getAsInt();
                if (obj.has("shortcutHeldKey2")) p.shortcutHeldKey2 = obj.get("shortcutHeldKey2").getAsInt();
                readFloatMap(obj.getAsJsonObject("sounds"), p.sounds);
                readFloatMap(obj.getAsJsonObject("blocks"), p.blocks);
                presets.add(p);
                activeIds.add(assignedId);
                added++;
            }
            if (added > 0) { rebuildActivePresetsCache(); SAVE_EXECUTOR.submit(PresetConfig::save); }
            if (reassigned > 0)
                SoundTweaks.LOGGER.warn("SoundTweaks: {} preset(s) importados com ids em conflito — ids reatribuídos. Ver: {}", reassigned, WIKI_PRESETS_URL);
            return new ImportResult(added, reassigned);
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar presets de {}", file, e);
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Parses a preset that already has an "id" field (current format). */
    private static Preset parsePreset(JsonObject obj) {
        if (obj == null || !obj.has("name") || !obj.has("id")) return null;
        Preset p = new Preset(obj.get("id").getAsInt(), obj.get("name").getAsString());
        if (obj.has("colorIndex"))       p.colorIndex       = obj.get("colorIndex").getAsInt();
        if (obj.has("customColor"))      p.customColor      = obj.get("customColor").getAsInt();
        if (obj.has("shortcutKey"))      p.shortcutKey      = obj.get("shortcutKey").getAsInt();
        if (obj.has("shortcutHeldKey"))  p.shortcutHeldKey  = obj.get("shortcutHeldKey").getAsInt();
        if (obj.has("shortcutHeldKey2")) p.shortcutHeldKey2 = obj.get("shortcutHeldKey2").getAsInt();
        readFloatMap(obj.getAsJsonObject("sounds"), p.sounds);
        readFloatMap(obj.getAsJsonObject("blocks"), p.blocks);
        return p;
    }

    /** Parses a preset from a legacy format (no "id" field); assigns the given sequential id. */
    private static Preset parsePresetLegacy(int assignedId, JsonObject obj) {
        if (obj == null || !obj.has("name")) return null;
        Preset p = new Preset(assignedId, obj.get("name").getAsString());
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
        obj.addProperty("id",             p.id);
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
