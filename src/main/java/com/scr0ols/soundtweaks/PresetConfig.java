package com.scr0ols.soundtweaks;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class PresetConfig {

    public static final int CUSTOM_COLOR_INDEX = 18;

    public static final int[] PRESET_COLORS = {
        0xFF555566, 0xFF993333, 0xFF336633, 0xFF2255AA, 0xFF886622, 0xFF7733AA,
        0xFF227788, 0xFFCC5522, 0xFF996688, 0xFF44AA55, 0xFF2277AA, 0xFFAA9933,
        0xFF884444, 0xFF337755, 0xFF553366, 0xFF888844, 0xFFAA4488, 0xFF445533
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
    private static final Set<String>  activeNames   = new LinkedHashSet<>();
    private static final List<String> favoriteNames = new ArrayList<>();
    private static volatile long      lastSaveRequest = 0;

    // ── Leitura ───────────────────────────────────────────────────────────────

    public static List<Preset> getPresets() { return Collections.unmodifiableList(presets); }

    public static boolean isActive(String name)   { return activeNames.contains(name); }
    public static boolean isFavorite(String name) { return favoriteNames.contains(name); }

    public static List<Preset> getActivePresets() {
        List<Preset> result = new ArrayList<>();
        for (Preset p : presets) if (activeNames.contains(p.name)) result.add(p);
        return result;
    }

    public static List<Preset> getFavoritePresets() {
        return favoriteNames.stream()
                .map(n -> presets.stream().filter(p -> p.name.equals(n)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Mutação ───────────────────────────────────────────────────────────────

    public static void setActive(String name, boolean active) {
        if (active) activeNames.add(name);
        else        activeNames.remove(name);
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
        markDirty();
    }

    // ── Persistência ──────────────────────────────────────────────────────────

    public static void markDirty() { lastSaveRequest = System.currentTimeMillis(); }

    public static void tickSave() {
        if (lastSaveRequest > 0 && System.currentTimeMillis() - lastSaveRequest > 300) {
            save();
            lastSaveRequest = 0;
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
        SoundTweaks.LOGGER.info("SoundTweaks: {} presets migrados do formato antigo", presets.size());
        save();
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

    // ── Import ────────────────────────────────────────────────────────────────

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
            if (added > 0) save();
            return added;
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar presets de {}", file, e);
            return -1;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        src.entrySet().forEach(e -> dst.put(e.getKey(), e.getValue().getAsFloat()));
    }

    private static JsonObject toJsonObject(Map<String, Float> map) {
        JsonObject obj = new JsonObject();
        map.forEach(obj::addProperty);
        return obj;
    }
}
