package com.scr0ols.soundtweaks;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blocos sem eventos de som dedicados em BuiltInRegistries.SOUND_EVENT,
 * ou blocos com controlo "master" sobre grupos de sons existentes.
 *
 * Controlados via LevelMixin (playLocalSound) para sons que passam por aí,
 * e/ou via cascata em BlockConfig para grupos de SoundConfig entries.
 */
public class MissingBlockRegistry {

    public static final List<String> BLOCK_IDS = List.of(
        "minecraft:observer",          // click de activação
        "minecraft:dropper",           // som ao disparar
        "minecraft:stonecutter",       // som ao usar
        "minecraft:cauldron",          // sons ao encher/usar
        "minecraft:loom",              // som de madeira ao abrir
        "minecraft:cartography_table", // som de madeira ao abrir
        "minecraft:note_block",        // MASTER: controla todos os block.note_block.*
        "minecraft:jukebox"            // MASTER: controla todos os music_disc.*
    );

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
        "minecraft:observer",          "Observer",
        "minecraft:dropper",           "Dropper",
        "minecraft:stonecutter",       "Stonecutter",
        "minecraft:cauldron",          "Cauldron",
        "minecraft:loom",              "Loom",
        "minecraft:cartography_table", "Cartography Table",
        "minecraft:note_block",        "Note Block (all)",
        "minecraft:jukebox",           "Jukebox (discs)"
    );

    /**
     * Blocos que funcionam como "master" sobre um grupo de SoundConfig entries.
     * Quando o volume destes é alterado, todas as entradas do grupo são actualizadas.
     *
     * Key: blockId
     * Value: prefixo dos soundIds a actualizar em SoundConfig
     */
    public static final Map<String, String> GROUP_PREFIXES = Map.of(
        "minecraft:note_block", "minecraft:block.note_block.",
        "minecraft:jukebox",    "minecraft:music_disc."
    );

    public static String getDisplayName(String blockId) {
        return DISPLAY_NAMES.getOrDefault(blockId, blockId);
    }

    public static boolean contains(String blockId) {
        return BLOCK_IDS.contains(blockId);
    }

    /** Blocos da MissingBlockRegistry que pertencem à categoria REDSTONE. */
    public static final Set<String> REDSTONE_BLOCK_IDS = Set.of(
        "minecraft:observer",
        "minecraft:dropper",
        "minecraft:note_block"
    );

    public static boolean isGroupControl(String blockId) {
        return GROUP_PREFIXES.containsKey(blockId);
    }
}
