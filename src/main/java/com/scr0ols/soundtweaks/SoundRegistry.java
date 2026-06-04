package com.scr0ols.soundtweaks;

import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class SoundRegistry {

    /**
     * Thread-safe set of known sounds.
     * ConcurrentHashMap.newKeySet() gives O(1) insert/contains vs O(log n) for TreeSet,
     * and is safe for concurrent access between the audio thread (addDiscovered)
     * and the render thread (getAll/getByCategory).
     */
    private static final Set<String> knownSounds = ConcurrentHashMap.newKeySet();

    // Called on startup — iterates all SoundEvents registered in the game
    public static void populate() {
        BuiltInRegistries.SOUND_EVENT.keySet().forEach(id -> knownSounds.add(id.toString()));
        SoundTweaks.LOGGER.info("SoundTweaks: {} sons encontrados no registo", knownSounds.size());
        exportSoundList();
    }

    // Called by the Mixin when a sound plays — captures sounds from resource packs
    // that may not be in the base registry
    public static void addDiscovered(String soundId) {
        knownSounds.add(soundId);
    }

    // Returns all known sounds sorted (for the GUI)
    public static List<String> getAll() {
        List<String> list = new ArrayList<>(knownSounds);
        Collections.sort(list);
        return list;
    }

    // Text search — used by the GUI search bar
    public static List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return getAll();
        }
        String q = query.toLowerCase();
        return knownSounds.stream()
                .filter(s -> s.contains(q))
                .toList();
    }

    public static int count() {
        return knownSounds.size();
    }

    /**
     * Returns all sounds for a specific category.
     *
     * For normal categories: filters by prefix (e.g. "block" → sounds starting with "...block.")
     * For OTHERS: returns sounds whose prefix does not match any known category
     * For HIDDEN: should never be called, but returns an empty list for safety
     */
    public static List<String> getByCategory(SoundCategory category) {
        if (category == null) return getAll();

        // Collect prefixes of all known categories (except OTHERS and HIDDEN)
        // Used to detect what "doesn't fit" in the OTHERS category
        Set<String> knownPrefixes = new TreeSet<>();
        for (SoundCategory cat : SoundCategory.values()) {
            // Skip categories with an extra filter (e.g. REDSTONE uses "block"
            // but is not the same as BLOCK — redstone sounds are already in BLOCK)
            if (cat.getPrefix() != null && cat.getExtraFilter() == null) {
                knownPrefixes.add(cat.getPrefix());
            }
        }

        return knownSounds.stream().filter(soundId -> {
            if (category == SoundCategory.OTHERS) {
                return !knownPrefixes.contains(extractPrefix(soundId));
            }
            return category.matches(soundId);
        }).toList();
    }

    /**
     * Returns unique objects for a category (parts[1] of each soundId),
     * sorted alphabetically — used to populate the second dropdown.
     *
     * Example with category BLOCK:
     *   "minecraft:block.piston.extend"   → "piston"
     *   "minecraft:block.piston.contract" → "piston"  (duplicate, ignored)
     *   "minecraft:block.note_block.hit"  → "note_block"
     *   → result: ["note_block", "piston", ...]
     *
     * Display name ("Piston", "Note Block") is the responsibility of SoundDisplayHelper.
     */
    public static List<String> getObjectsByCategory(SoundCategory category) {
        List<String> sounds = getByCategory(category);
        TreeSet<String> objects = new TreeSet<>(); // TreeSet: deduplicates and sorts automatically

        for (String soundId : sounds) {
            int ci = soundId.indexOf(':');
            String withoutNamespace = ci >= 0 ? soundId.substring(ci + 1) : soundId;
            int dot1 = withoutNamespace.indexOf('.');
            if (dot1 >= 0) {
                int dot2 = withoutNamespace.indexOf('.', dot1 + 1);
                String obj = dot2 >= 0
                        ? withoutNamespace.substring(dot1 + 1, dot2)
                        : withoutNamespace.substring(dot1 + 1);
                if (!obj.isEmpty()) objects.add(obj);
            }
        }

        return new ArrayList<>(objects);
    }

    /**
     * Extracts the category prefix from a soundId (the segment after the namespace and before the first dot).
     * Private — internal registry logic.
     * "minecraft:block.piston.extend" → "block"
     */
    private static String extractPrefix(String soundId) {
        int ci = soundId.indexOf(':');
        String withoutNamespace = ci >= 0 ? soundId.substring(ci + 1) : soundId;
        int dotIndex = withoutNamespace.indexOf('.');
        return dotIndex >= 0 ? withoutNamespace.substring(0, dotIndex) : withoutNamespace;
    }

    /**
     * Groups a list of soundIds by "group key" (prefix.object).
     * Only returns groups with 2+ members.
     * Example: ["entity.horse.angry", "entity.horse.hurt", "entity.pig.ambient"]
     *   → {"entity.horse": ["entity.horse.angry", "entity.horse.hurt"]}
     */
    public static Map<String, List<String>> getGroups(List<String> soundIds) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String id : soundIds) {
            String key = extractGroupKey(id);
            if (key != null) groups.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
        }
        groups.entrySet().removeIf(e -> e.getValue().size() < 2);
        return groups;
    }

    /** Extracts the group key from a soundId: "minecraft:entity.horse.angry" → "entity.horse". */
    public static String extractGroupKey(String soundId) {
        int ci = soundId.indexOf(':');
        String withoutNs = ci >= 0 ? soundId.substring(ci + 1) : soundId;
        int dot1 = withoutNs.indexOf('.');
        if (dot1 < 0) return null;
        int dot2 = withoutNs.indexOf('.', dot1 + 1);
        if (dot2 < 0) return null;
        return withoutNs.substring(0, dot2); // ex: "entity.horse"
    }

    // Exports the full sound list to a .txt file — useful for reference and manual mapping
    private static void exportSoundList() {
        Path outFile = FMLPaths.CONFIGDIR.get().resolve("soundtweaks_sounds.txt");
        try {
            Files.writeString(outFile, String.join("\n", knownSounds));
            SoundTweaks.LOGGER.info("SoundTweaks: lista de sons exportada para {}", outFile);
        } catch (IOException e) {
            SoundTweaks.LOGGER.warn("SoundTweaks: erro ao exportar lista de sons", e);
        }
    }
}
