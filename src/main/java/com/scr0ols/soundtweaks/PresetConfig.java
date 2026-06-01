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
        0xFF555566, // 0  cinzento
        0xFF993333, // 1  vermelho
        0xFF336633, // 2  verde escuro
        0xFF2255AA, // 3  azul
        0xFF886622, // 4  dourado
        0xFF7733AA, // 5  roxo
        0xFF227788, // 6  ciano
        0xFFCC5522, // 7  laranja
        0xFF996688, // 8  rosa
        0xFF44AA55, // 9  verde claro
        0xFF2277AA, // 10 azul claro
        0xFFAA9933, // 11 amarelo torrado
        0xFF884444, // 12 bordô
        0xFF337755, // 13 verde azulado
        0xFF553366, // 14 índigo
        0xFF888844, // 15 verde musgo
        0xFFAA4488, // 16 magenta
        0xFF445533  // 17 verde oliva
    };

    public static class Preset {
        public final String id;
        public String name;
        public int    colorIndex;
        public int    customColor;
        public int    shortcutKey;
        public int    shortcutHeldKey;
        public int    shortcutHeldKey2;
        public final Map<String, Float> sounds;
        public final Map<String, Float> blocks;

        public Preset(String id, String name) {
            this.id            = id;
            this.name          = name;
            this.colorIndex    = 0;
            this.shortcutKey   = 0;
            this.shortcutHeldKey  = 0;
            this.shortcutHeldKey2 = 0;
            this.sounds        = new LinkedHashMap<>();
            this.blocks        = new LinkedHashMap<>();
        }

        public int argbColor() {
            if (colorIndex == CUSTOM_COLOR_INDEX)
                return customColor != 0 ? (customColor | 0xFF000000) : 0xFF888888;
            int idx = colorIndex >= 0 && colorIndex < PRESET_COLORS.length ? colorIndex : 0;
            return PRESET_COLORS[idx];
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ficheiro master: lista de UUIDs + estado active/favorite. */
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("soundtweaks_presets.json");

    /** Pasta com um ficheiro JSON por preset. */
    private static final Path PRESETS_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("soundtweaks_presets");

    private static final List<Preset> presets         = new CopyOnWriteArrayList<>();
    private static final Set<String>  activePresetIds = new LinkedHashSet<>();
    private static final List<String> favoriteIds     = new ArrayList<>();
    private static volatile long      lastSaveRequest = 0;

    // ── Leitura ───────────────────────────────────────────────────────────────

    public static List<Preset> getPresets() { return Collections.unmodifiableList(presets); }

    public static boolean isActive(String presetId)   { return activePresetIds.contains(presetId); }
    public static boolean isFavorite(String presetId) { return favoriteIds.contains(presetId); }

    public static List<Preset> getActivePresets() {
        List<Preset> result = new ArrayList<>();
        for (Preset p : presets) if (activePresetIds.contains(p.id)) result.add(p);
        return result;
    }

    public static List<Preset> getFavoritePresets() {
        return favoriteIds.stream()
                .map(id -> presets.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Mutação ───────────────────────────────────────────────────────────────

    public static void setActive(String presetId, boolean active) {
        if (active) activePresetIds.add(presetId);
        else        activePresetIds.remove(presetId);
        markDirty();
    }

    public static void setFavorite(String presetId, boolean favorite) {
        if (favorite) { if (!favoriteIds.contains(presetId)) favoriteIds.add(presetId); }
        else          { favoriteIds.remove(presetId); }
        markDirty();
    }

    public static Preset createFromCurrentConfig(String name) {
        Preset p = new Preset(UUID.randomUUID().toString(), name);
        SoundConfig.getAll().forEach((id, vol) -> { if (vol != 1.0f) p.sounds.put(id, vol); });
        BlockConfig.getAll().forEach((id, vol) -> { if (vol != 1.0f) p.blocks.put(id, vol); });
        presets.add(p);
        markDirty();
        return p;
    }

    public static void deletePreset(String presetId) {
        presets.removeIf(p -> p.id.equals(presetId));
        activePresetIds.remove(presetId);
        favoriteIds.remove(presetId);
        try { Files.deleteIfExists(PRESETS_DIR.resolve(presetId + ".json")); }
        catch (IOException e) { SoundTweaks.LOGGER.error("SoundTweaks: erro ao apagar preset {}", presetId, e); }
        markDirty();
    }

    public static void renamePreset(String presetId, String newName) {
        for (Preset p : presets) {
            if (p.id.equals(presetId)) { p.name = newName.isBlank() ? p.name : newName.trim(); break; }
        }
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
            activePresetIds.clear();
            favoriteIds.clear();

            JsonArray activeArr = root.getAsJsonArray("activePresets");
            if (activeArr != null) activeArr.forEach(el -> activePresetIds.add(el.getAsString()));

            JsonArray favArr = root.getAsJsonArray("favoritePresets");
            if (favArr != null) favArr.forEach(el -> favoriteIds.add(el.getAsString()));

            JsonArray presetsArr = root.getAsJsonArray("presets");
            if (presetsArr != null) {
                boolean needsMigration = false;
                for (JsonElement el : presetsArr) {
                    if (el.isJsonPrimitive()) {
                        // Novo formato: UUID string → carregar ficheiro individual
                        String id = el.getAsString();
                        Preset p = loadPresetFile(id);
                        if (p != null) presets.add(p);
                    } else if (el.isJsonObject()) {
                        // Formato antigo: objecto inline → migrar para ficheiro individual
                        Preset p = parsePresetObject(el.getAsJsonObject());
                        presets.add(p);
                        savePresetFile(p);
                        needsMigration = true;
                    }
                }
                if (needsMigration) {
                    SoundTweaks.LOGGER.info("SoundTweaks: presets migrados para formato de ficheiros individuais");
                    save(); // reescrever master com UUIDs em vez de objectos
                }
            }

            SoundTweaks.LOGGER.info("SoundTweaks: {} presets carregados ({} favoritos)",
                    presets.size(), favoriteIds.size());
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar presets", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PRESETS_DIR);

            JsonObject root = new JsonObject();

            JsonArray presetsArr = new JsonArray();
            for (Preset p : presets) {
                presetsArr.add(p.id);
                savePresetFile(p);
            }
            root.add("presets", presetsArr);

            JsonArray activeArr = new JsonArray();
            activePresetIds.forEach(activeArr::add);
            root.add("activePresets", activeArr);

            JsonArray favArr = new JsonArray();
            favoriteIds.forEach(favArr::add);
            root.add("favoritePresets", favArr);

            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao guardar presets", e);
        }
    }

    // ── Import de ficheiro externo ────────────────────────────────────────────

    /**
     * Importa presets de um ficheiro de partilha.
     *
     * Formato suportado (simples, sem UUID):
     *   - Array de presets: [{name, colorIndex, sounds, blocks}, ...]
     *   - Objecto único:    {name, colorIndex, sounds, blocks}
     *   - Formato antigo (pack): {presets: [{...}, ...]}
     *
     * Um novo UUID é sempre gerado — nunca há conflitos por UUID duplicado.
     * Atalhos de teclado não são importados (são específicos de cada máquina).
     *
     * @return número de presets adicionados, ou -1 em caso de erro
     */
    public static int importFrom(Path file) {
        try {
            String json = Files.readString(file);
            JsonElement root = JsonParser.parseString(json);

            List<JsonObject> toImport = new ArrayList<>();
            if (root.isJsonArray()) {
                root.getAsJsonArray().forEach(el -> {
                    if (el.isJsonObject()) toImport.add(el.getAsJsonObject());
                });
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("presets") && obj.get("presets").isJsonArray()) {
                    // Formato pack ou formato antigo com array "presets"
                    obj.getAsJsonArray("presets").forEach(el -> {
                        if (el.isJsonObject()) toImport.add(el.getAsJsonObject());
                    });
                } else if (obj.has("name")) {
                    // Objecto de preset único
                    toImport.add(obj);
                }
            }

            if (toImport.isEmpty()) {
                SoundTweaks.LOGGER.warn("SoundTweaks: nenhum preset encontrado em {}", file);
                return 0;
            }

            int added = 0;
            for (JsonObject obj : toImport) {
                String newId = UUID.randomUUID().toString();
                String name  = obj.has("name") ? obj.get("name").getAsString() : "Imported Preset";

                Preset p = new Preset(newId, name);
                if (obj.has("colorIndex"))  p.colorIndex  = obj.get("colorIndex").getAsInt();
                if (obj.has("customColor")) p.customColor = obj.get("customColor").getAsInt();
                // Atalhos não importados — são específicos de cada máquina
                readFloatMap(obj.getAsJsonObject("sounds"), p.sounds);
                readFloatMap(obj.getAsJsonObject("blocks"), p.blocks);

                presets.add(p);
                activePresetIds.add(newId);
                SoundTweaks.LOGGER.info("SoundTweaks: preset importado '{}' (id={}) com {} sons e {} blocos",
                        name, newId, p.sounds.size(), p.blocks.size());
                added++;
            }

            if (added > 0) save();
            SoundTweaks.LOGGER.info("SoundTweaks: {} presets importados de {}", added, file);
            return added;
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao importar presets de {}", file, e);
            return -1;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Preset loadPresetFile(String id) {
        Path file = PRESETS_DIR.resolve(id + ".json");
        if (!Files.exists(file)) {
            SoundTweaks.LOGGER.warn("SoundTweaks: ficheiro de preset não encontrado: {}", file);
            return null;
        }
        try {
            JsonObject obj = GSON.fromJson(Files.readString(file), JsonObject.class);
            return parsePresetObject(obj);
        } catch (Exception e) {
            SoundTweaks.LOGGER.error("SoundTweaks: erro ao carregar preset {}", id, e);
            return null;
        }
    }

    private static Preset parsePresetObject(JsonObject obj) {
        Preset p = new Preset(obj.get("id").getAsString(), obj.get("name").getAsString());
        if (obj.has("colorIndex"))       p.colorIndex      = obj.get("colorIndex").getAsInt();
        if (obj.has("customColor"))      p.customColor     = obj.get("customColor").getAsInt();
        if (obj.has("shortcutKey"))      p.shortcutKey     = obj.get("shortcutKey").getAsInt();
        if (obj.has("shortcutHeldKey"))  p.shortcutHeldKey = obj.get("shortcutHeldKey").getAsInt();
        if (obj.has("shortcutHeldKey2")) p.shortcutHeldKey2 = obj.get("shortcutHeldKey2").getAsInt();
        readFloatMap(obj.getAsJsonObject("sounds"), p.sounds);
        readFloatMap(obj.getAsJsonObject("blocks"), p.blocks);
        return p;
    }

    private static void savePresetFile(Preset p) throws IOException {
        Files.createDirectories(PRESETS_DIR);
        JsonObject obj = new JsonObject();
        obj.addProperty("id",            p.id);
        obj.addProperty("name",          p.name);
        obj.addProperty("colorIndex",    p.colorIndex);
        if (p.customColor != 0) obj.addProperty("customColor", p.customColor);
        obj.addProperty("shortcutKey",      p.shortcutKey);
        obj.addProperty("shortcutHeldKey",  p.shortcutHeldKey);
        obj.addProperty("shortcutHeldKey2", p.shortcutHeldKey2);
        obj.add("sounds", toJsonObject(p.sounds));
        obj.add("blocks", toJsonObject(p.blocks));
        Files.writeString(PRESETS_DIR.resolve(p.id + ".json"), GSON.toJson(obj));
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
