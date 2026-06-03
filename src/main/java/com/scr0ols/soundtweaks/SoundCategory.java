package com.scr0ols.soundtweaks;

import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Sound categories for the GUI dropdown.
 *
 * Most categories map 1-to-1 with the sound ID prefix ("block", "entity", etc.).
 * REDSTONE is a cross-cutting category that filters by object name within "block.*".
 */
public enum SoundCategory {

    //           prefix       label key                           visible  extra filter
    BLOCK      ("block",      "soundtweaks.category.block",       true,    null),
    ENTITY     ("entity",     "soundtweaks.category.entity",      true,    null),
    ITEM       ("item",       "soundtweaks.category.item",        true,    null),
    AMBIENT    ("ambient",    "soundtweaks.category.ambient",     true,    null),
    MUSIC      ("music",      "soundtweaks.category.music",       true,    null),
    MUSIC_DISC ("music_disc", "soundtweaks.category.music_disc",  true,    null),
    UI         ("ui",         "soundtweaks.category.ui",          true,    null),
    WEATHER    ("weather",    "soundtweaks.category.weather",     true,    null),
    ENCHANT    ("enchant",    "soundtweaks.category.enchant",     true,    null),
    EVENT      ("event",      "soundtweaks.category.event",       true,    null),
    OTHERS     (null,         "soundtweaks.category.others",      true,    null),

    // Categoria especial: sons dentro de "block.*" cujo objecto é redstone
    // O filtro é aplicado DEPOIS do prefixo — method reference evita problemas de
    // ordem de inicialização (o Set é estático e estará pronto quando for chamado)
    REDSTONE   ("block",      "soundtweaks.category.redstone",    true,    SoundCategory::isRedstoneObject),

    HIDDEN     ("intentionally_empty", null,                      false,   null);

    // ── Objectos redstone (parts[1] do soundId) ───────────────────────────────
    private static final Set<String> REDSTONE_OBJECTS = Set.of(
        "piston", "comparator", "lever", "note_block", "tripwire",
        "dispenser", "crafter", "sculk_sensor", "sculk_shrieker",
        "sculk_catalyst", "redstone_torch"
    );

    private static boolean isRedstoneObject(String soundId) {
        int ci = soundId.indexOf(':');
        String withoutNs = ci >= 0 ? soundId.substring(ci + 1) : soundId;
        int dot1 = withoutNs.indexOf('.');
        if (dot1 < 0) return false;
        int dot2 = withoutNs.indexOf('.', dot1 + 1);
        String obj = dot2 >= 0 ? withoutNs.substring(dot1 + 1, dot2) : withoutNs.substring(dot1 + 1);
        return REDSTONE_OBJECTS.contains(obj);
    }

    // ── Sons sem ficheiro de áudio, acessibilidade ou sem relevância — excluídos da GUI ──
    private static final Set<String> SILENT_SOUNDS;
    static {
        HashSet<String> s = new HashSet<>(Arrays.asList(
            // Sons sem ficheiro de áudio
            "minecraft:block.fungus.fall",
            "minecraft:block.fungus.hit",
            "minecraft:music.nether.warped_forest",
            // Sons idle de blocos (decorativos, sem audio relevante)
            "minecraft:block.deadbush.idle",
            "minecraft:block.eyeblossom.idle",
            "minecraft:block.firefly_bush.idle",
            "minecraft:block.pale_hanging_moss.idle",
            "minecraft:block.sand.idle",
            "minecraft:block.creaking_heart.idle",
            // Sons ambient de blocos (loops contínuos — controlados via volume de bloco)
            "minecraft:block.beacon.ambient",
            "minecraft:block.candle.ambient",
            "minecraft:block.conduit.ambient",
            "minecraft:block.conduit.ambient.short",
            "minecraft:block.dried_ghast.ambient",
            "minecraft:block.dried_ghast.ambient_water",
            "minecraft:block.dry_grass.ambient",
            "minecraft:block.fire.ambient",
            "minecraft:block.lava.ambient",
            "minecraft:block.portal.ambient",
            "minecraft:block.respawn_anchor.ambient",
            "minecraft:block.trial_spawner.ambient",
            "minecraft:block.trial_spawner.ambient_ominous",
            "minecraft:block.vault.ambient",
            "minecraft:block.water.ambient",
            // Outros sem relevância prática
            "minecraft:block.hanging_sign.waxed_interact_fail",
            "minecraft:block.trial_spawner.place",
            "minecraft:block.vault.reject_rewarded_player",
            "minecraft:intentionally_empty"
        ));
        SILENT_SOUNDS = Collections.unmodifiableSet(s);
    }

    public static boolean isSilent(String soundId) {
        return SILENT_SOUNDS.contains(soundId);
    }

    // ── Campos ────────────────────────────────────────────────────────────────
    private final @Nullable String prefix;
    private final @Nullable String labelKey;
    private final boolean visible;
    private final @Nullable Predicate<String> extraFilter; // null = só prefixo

    SoundCategory(@Nullable String prefix, @Nullable String labelKey,
                  boolean visible, @Nullable Predicate<String> extraFilter) {
        this.prefix      = prefix;
        this.labelKey    = labelKey;
        this.visible     = visible;
        this.extraFilter = extraFilter;
    }

    /** Chave única usada no dropdown — difere do prefix para categorias com filtro extra. */
    public String getDropdownKey() {
        if (extraFilter != null) return name().toLowerCase(); // ex: "redstone"
        return prefix != null ? prefix : "others";
    }

    public @Nullable String  getPrefix()      { return prefix; }
    public @Nullable String  getLabelKey()    { return labelKey; }
    public boolean           isVisible()      { return visible; }
    public @Nullable Predicate<String> getExtraFilter() { return extraFilter; }

    /**
     * Verdadeiro se este soundId pertence a esta categoria.
     * Não deve ser chamado para OTHERS ou HIDDEN — têm lógica especial no Registry.
     */
    public boolean matches(String soundId) {
        if (prefix == null) return false; // OTHERS trata-se no Registry
        String withoutNs = soundId.contains(":") ? soundId.split(":")[1] : soundId;
        int dot = withoutNs.indexOf('.');
        String actualPrefix = dot >= 0 ? withoutNs.substring(0, dot) : withoutNs;
        if (!actualPrefix.equals(prefix)) return false;
        return extraFilter == null || extraFilter.test(soundId);
    }

    /** Devolve a categoria pela dropdownKey (inclui categorias com filtro extra). */
    public static @Nullable SoundCategory fromDropdownKey(@Nullable String key) {
        if (key == null) return null;
        for (SoundCategory cat : values()) {
            if (key.equals(cat.getDropdownKey())) return cat;
        }
        return null;
    }

    /** Devolve a categoria pelo prefixo. Ignora categorias com filtro extra (ex: REDSTONE). */
    public static SoundCategory fromPrefix(String prefix) {
        if (prefix == null) return OTHERS;
        for (SoundCategory cat : values()) {
            if (cat.extraFilter == null && prefix.equals(cat.prefix)) return cat;
        }
        return OTHERS;
    }

    /** Todas as categorias visíveis, por ordem do enum. */
    public static SoundCategory[] visibleCategories() {
        return Arrays.stream(values())
                .filter(SoundCategory::isVisible)
                .toArray(SoundCategory[]::new);
    }
}
