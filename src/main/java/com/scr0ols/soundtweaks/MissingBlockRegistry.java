package com.scr0ols.soundtweaks;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blocks without dedicated sound events in BuiltInRegistries.SOUND_EVENT,
 * or blocks that act as "master" over groups of existing sounds.
 *
 * Controlled via LevelMixin (playLocalSound) for sounds routed through that path,
 * and/or via cascade in BlockConfig for groups of SoundConfig entries.
 */
public class MissingBlockRegistry {

    public static final List<String> BLOCK_IDS = List.of(
        "minecraft:observer",          // activation click
        "minecraft:dropper",           // firing sound
        "minecraft:stonecutter",       // use sound
        "minecraft:cauldron",          // fill/use sounds
        "minecraft:loom",              // wood open sound
        "minecraft:cartography_table", // wood open sound
        "minecraft:note_block",        // MASTER: controls all block.note_block.*
        "minecraft:jukebox"            // MASTER: controls all music_disc.*
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
     * Blocks that act as "master" over a group of SoundConfig entries.
     * When their volume is changed, all entries in the group are updated.
     *
     * Key: blockId
     * Value: prefix of the soundIds to update in SoundConfig
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

    /** MissingBlockRegistry blocks that belong to the REDSTONE category. */
    public static final Set<String> REDSTONE_BLOCK_IDS = Set.of(
        "minecraft:observer",
        "minecraft:dropper",
        "minecraft:note_block"
    );

    public static boolean isGroupControl(String blockId) {
        return GROUP_PREFIXES.containsKey(blockId);
    }
}
