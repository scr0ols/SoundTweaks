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
        String withoutNs = soundId.contains(":") ? soundId.split(":")[1] : soundId;
        String[] parts = withoutNs.split("\\.");
        return parts.length >= 2 && REDSTONE_OBJECTS.contains(parts[1]);
    }

    // ── Sons sem ficheiro de áudio, acessibilidade ou sem relevância — excluídos da GUI ──
    private static final Set<String> SILENT_SOUNDS;
    static {
        HashSet<String> s = new HashSet<>(Arrays.asList(
            // Originais sem áudio
            "minecraft:block.fungus.fall",
            "minecraft:block.fungus.hit",
            "minecraft:music.nether.warped_forest",
            // Sons idle de blocos
            "minecraft:block.deadbush.idle",
            "minecraft:block.eyeblossom.idle",
            "minecraft:block.firefly_bush.idle",
            "minecraft:block.pale_hanging_moss.idle",
            "minecraft:block.sand.idle",
            "minecraft:block.creaking_heart.idle",
            // Sons ambient de blocos
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
            // Sons idle de entidades
            "minecraft:entity.axolotl.idle_air",
            "minecraft:entity.axolotl.idle_water",
            "minecraft:entity.breeze.idle_air",
            "minecraft:entity.breeze.idle_ground",
            "minecraft:entity.sniffer.idle",
            // Sons ambient de entidades (acessibilidade auditiva)
            "minecraft:entity.allay.ambient_with_item",
            "minecraft:entity.allay.ambient_without_item",
            "minecraft:entity.armadillo.ambient",
            "minecraft:entity.baby_cat.ambient",
            "minecraft:entity.baby_chicken.ambient",
            "minecraft:entity.baby_horse.ambient",
            "minecraft:entity.baby_nautilus.ambient",
            "minecraft:entity.baby_nautilus.ambient_land",
            "minecraft:entity.baby_pig.ambient",
            "minecraft:entity.baby_wolf.ambient",
            "minecraft:entity.bat.ambient",
            "minecraft:entity.blaze.ambient",
            "minecraft:entity.bogged.ambient",
            "minecraft:entity.camel.ambient",
            "minecraft:entity.camel_husk.ambient",
            "minecraft:entity.cat.ambient",
            "minecraft:entity.cat_royal.ambient",
            "minecraft:entity.chicken.ambient",
            "minecraft:entity.chicken_picky.ambient",
            "minecraft:entity.cod.ambient",
            "minecraft:entity.cow.ambient",
            "minecraft:entity.cow_moody.ambient",
            "minecraft:entity.creaking.ambient",
            "minecraft:entity.dolphin.ambient",
            "minecraft:entity.dolphin.ambient_water",
            "minecraft:entity.donkey.ambient",
            "minecraft:entity.drowned.ambient",
            "minecraft:entity.drowned.ambient_water",
            "minecraft:entity.elder_guardian.ambient",
            "minecraft:entity.elder_guardian.ambient_land",
            "minecraft:entity.ender_dragon.ambient",
            "minecraft:entity.enderman.ambient",
            "minecraft:entity.endermite.ambient",
            "minecraft:entity.evoker.ambient",
            "minecraft:entity.fox.ambient",
            "minecraft:entity.frog.ambient",
            "minecraft:entity.ghast.ambient",
            "minecraft:entity.ghastling.ambient",
            "minecraft:entity.glow_squid.ambient",
            "minecraft:entity.goat.ambient",
            "minecraft:entity.goat.screaming.ambient",
            "minecraft:entity.guardian.ambient",
            "minecraft:entity.guardian.ambient_land",
            "minecraft:entity.happy_ghast.ambient",
            "minecraft:entity.hoglin.ambient",
            "minecraft:entity.horse.ambient",
            "minecraft:entity.husk.ambient",
            "minecraft:entity.illusioner.ambient",
            "minecraft:entity.llama.ambient",
            "minecraft:entity.mule.ambient",
            "minecraft:entity.nautilus.ambient",
            "minecraft:entity.nautilus.ambient_land",
            "minecraft:entity.ocelot.ambient",
            "minecraft:entity.panda.ambient",
            "minecraft:entity.panda.worried_ambient",
            "minecraft:entity.parched.ambient",
            "minecraft:entity.parrot.ambient",
            "minecraft:entity.phantom.ambient",
            "minecraft:entity.pig.ambient",
            "minecraft:entity.pig_big.ambient",
            "minecraft:entity.pig_mini.ambient",
            "minecraft:entity.piglin.ambient",
            "minecraft:entity.piglin_brute.ambient",
            "minecraft:entity.pillager.ambient",
            "minecraft:entity.polar_bear.ambient",
            "minecraft:entity.polar_bear.ambient_baby",
            "minecraft:entity.rabbit.ambient",
            "minecraft:entity.ravager.ambient",
            "minecraft:entity.salmon.ambient",
            "minecraft:entity.sheep.ambient",
            "minecraft:entity.shulker.ambient",
            "minecraft:entity.silverfish.ambient",
            "minecraft:entity.skeleton.ambient",
            "minecraft:entity.skeleton_horse.ambient",
            "minecraft:entity.skeleton_horse.ambient_water",
            "minecraft:entity.snow_golem.ambient",
            "minecraft:entity.spider.ambient",
            "minecraft:entity.squid.ambient",
            "minecraft:entity.stray.ambient",
            "minecraft:entity.strider.ambient",
            "minecraft:entity.tropical_fish.ambient",
            "minecraft:entity.turtle.ambient_land",
            "minecraft:entity.vex.ambient",
            "minecraft:entity.villager.ambient",
            "minecraft:entity.vindicator.ambient",
            "minecraft:entity.wandering_trader.ambient",
            "minecraft:entity.warden.ambient",
            "minecraft:entity.witch.ambient",
            "minecraft:entity.wither.ambient",
            "minecraft:entity.wither_skeleton.ambient",
            "minecraft:entity.wolf.ambient",
            "minecraft:entity.wolf_angry.ambient",
            "minecraft:entity.wolf_big.ambient",
            "minecraft:entity.wolf_cute.ambient",
            "minecraft:entity.wolf_grumpy.ambient",
            "minecraft:entity.wolf_puglin.ambient",
            "minecraft:entity.wolf_sad.ambient",
            "minecraft:entity.zoglin.ambient",
            "minecraft:entity.zombie.ambient",
            "minecraft:entity.zombie_horse.ambient",
            "minecraft:entity.zombie_nautilus.ambient",
            "minecraft:entity.zombie_nautilus.ambient_land",
            "minecraft:entity.zombie_villager.ambient",
            "minecraft:entity.zombified_piglin.ambient",
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
