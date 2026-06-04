package com.scr0ols.soundtweaks.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class SoundDisplayHelper {

    // Override map: "minecraft:block.note_block.basedrum" → "Note Block (Base Drum)"
    // Loaded once on startup — null means not yet loaded
    // volatile: lazy-init read in ensureOverridesLoaded() may race with loadOverrides() write
    private static volatile Map<String, String> overrides = null;

    // --- Public API ---

    /**
     * Returns the display name for the GUI.
     * Checks overrides first; falls back to auto-formatting.
     * Examples:
     *   "minecraft:block.piston.extend"            → "Piston (extend)"
     *   "minecraft:entity.zombie_villager.ambient" → "Zombie Villager (ambient)"
     *   "minecraft:music.creative"                 → "Creative"
     */
    public static String getDisplayName(String soundId) {
        ensureOverridesLoaded();

        // 1. Check overrides first
        String override = overrides.get(soundId);
        if (override != null) return override;

        // 2. Auto-format
        return autoFormat(soundId);
    }

    /**
     * Extracts the category prefix — the segment before the first dot,
     * after removing the namespace.
     * "minecraft:block.piston.extend"    → "block"
     * "create:mechanical.piston.extend"  → "mechanical"
     */
    public static String getCategoryPrefix(String soundId) {
        String withoutNamespace = removeNamespace(soundId);
        int dotIndex = withoutNamespace.indexOf('.');
        // If there is no dot (malformed sound), the category is the id itself
        return dotIndex >= 0 ? withoutNamespace.substring(0, dotIndex) : withoutNamespace;
    }

    /**
     * Extracts the object name — parts[1], formatted.
     * "minecraft:block.piston.extend"    → "Piston"
     * "minecraft:entity.zombie_villager" → "Zombie Villager"
     * If there is no parts[1], returns an empty string.
     */
    public static String getObjectName(String soundId) {
        String withoutNamespace = removeNamespace(soundId);
        String[] parts = withoutNamespace.split("\\.");
        if (parts.length < 2) return "";
        return formatWord(parts[1]);
    }

    // --- Internal formatting ---

    /**
     * Auto-formats a soundId for display.
     * Logic:
     *   parts[0] = category (ignored in display)
     *   parts[1] = object   → capitalised, underscores → spaces
     *   parts[2+] = action  → joined with spaces, wrapped in parentheses
     */
    static String autoFormat(String soundId) {
        String withoutNamespace = removeNamespace(soundId);
        String[] parts = withoutNamespace.split("\\.");

        // Fallback cases: malformed or not enough parts
        if (parts.length == 0) return soundId;
        if (parts.length == 1) return formatWord(parts[0]);  // e.g. "intentionally_empty"

        // parts[1] = object
        String objectPart = formatWord(parts[1]);

        // parts[2+] = action (may be absent if only 2 parts)
        if (parts.length == 2) {
            // "minecraft:music.creative" → "Creative"  (no action, no parentheses)
            return objectPart;
        }

        // "minecraft:block.piston.extend" → "Piston (extend)"
        // "minecraft:entity.zombie_villager.converted_by_cure" → "Zombie Villager (converted by cure)"
        StringBuilder action = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) action.append(" ");
            // Action is not capitalised — stays lowercase to read as a description
            action.append(parts[i].replace('_', ' '));
        }

        return objectPart + " (" + action + ")";
    }

    /**
     * Capitalises the first letter and replaces underscores with spaces.
     * "zombie_villager" → "Zombie Villager"
     * "note_block"      → "Note Block"
     */
    private static String formatWord(String word) {
        if (word == null || word.isEmpty()) return word;

        // Replace underscores with spaces and capitalise each word
        String[] words = word.split("_");
        StringBuilder result = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) result.append(w.substring(1));
            }
        }
        return result.toString();
    }

    /**
     * Removes the namespace ("minecraft:", "create:", etc.).
     * If there is no ":", returns the original id.
     */
    private static String removeNamespace(String soundId) {
        int colonIndex = soundId.indexOf(':');
        return colonIndex >= 0 ? soundId.substring(colonIndex + 1) : soundId;
    }

    // --- Overrides ---

    /**
     * Ensures overrides are loaded.
     * Lazy — only reads the file on the first call.
     */
    private static void ensureOverridesLoaded() {
        if (overrides == null) {
            synchronized (SoundDisplayHelper.class) {
                if (overrides == null) loadOverrides(); // double-checked locking
            }
        }
    }

    /**
     * Reads soundtweaks_name_overrides.json from the config folder.
     * If the file does not exist, initialises an empty map (no error).
     * If the JSON is invalid, logs a warning and uses an empty map.
     */
    static void loadOverrides() {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("soundtweaks_name_overrides.json");

        if (!Files.exists(file)) {
            overrides = Collections.emptyMap();
            return;
        }

        try {
            String json = Files.readString(file);
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(json, type);
            overrides = loaded != null ? loaded : Collections.emptyMap();
        } catch (IOException e) {
            overrides = Collections.emptyMap();
        }
    }

    /**
     * Forces a reload of overrides — useful for tests.
     */
    public static void resetOverrides() {
        overrides = null;
    }
}
