package com.scr0ols.soundtweaks;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SoundRegistry {

    // TreeSet mantém os sons ordenados alfabeticamente automaticamente
    private static final TreeSet<String> knownSounds = new TreeSet<>();

    // Chamado no arranque — percorre todos os SoundEvents registados no jogo
    public static void populate() {
        BuiltInRegistries.SOUND_EVENT.keySet().forEach(id -> knownSounds.add(id.toString()));
        SoundTweaks.LOGGER.info("SoundTweaks: {} sons encontrados no registo", knownSounds.size());
        exportSoundList();
    }

    // Chamado pelo Mixin quando um som toca — captura sons de resource packs
    // que possam não estar no registo base
    public static void addDiscovered(String soundId) {
        knownSounds.add(soundId);
    }

    // Devolve todos os sons conhecidos (para a GUI)
    public static List<String> getAll() {
        return new ArrayList<>(knownSounds);
    }

    // Pesquisa por texto — usado pela barra de pesquisa da GUI
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
     * Devolve todos os sons de uma categoria específica.
     *
     * Para categorias normais: filtra por prefixo (ex: "block" → sons que começam com "...block.")
     * Para OTHERS: devolve sons cujo prefixo não encaixa em nenhuma categoria conhecida
     * Para HIDDEN: nunca deveria ser chamado, mas devolve lista vazia por segurança
     */
    public static List<String> getByCategory(SoundCategory category) {
        if (category == null) return getAll();

        // Recolher os prefixos de todas as categorias conhecidas (exceto OTHERS e HIDDEN)
        // Usados para detetar o que "não encaixa" na categoria OTHERS
        Set<String> knownPrefixes = new TreeSet<>();
        for (SoundCategory cat : SoundCategory.values()) {
            // Não adicionar prefixos de categorias com filtro extra (ex: REDSTONE usa "block"
            // mas não é a mesma coisa que BLOCK — os sons redstone já estão em BLOCK)
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
     * Devolve os objectos únicos de uma categoria (parts[1] de cada soundId),
     * ordenados alfabeticamente — usado para popular o segundo dropdown.
     *
     * Exemplo com categoria BLOCK:
     *   "minecraft:block.piston.extend"   → "piston"
     *   "minecraft:block.piston.contract" → "piston"  (duplicado, ignorado)
     *   "minecraft:block.note_block.hit"  → "note_block"
     *   → resultado: ["note_block", "piston", ...]
     *
     * O display name ("Piston", "Note Block") é responsabilidade do SoundDisplayHelper.
     */
    public static List<String> getObjectsByCategory(SoundCategory category) {
        List<String> sounds = getByCategory(category);
        TreeSet<String> objects = new TreeSet<>(); // TreeSet: deduplica e ordena automaticamente

        for (String soundId : sounds) {
            String withoutNamespace = soundId.contains(":") ? soundId.split(":")[1] : soundId;
            String[] parts = withoutNamespace.split("\\.");
            if (parts.length >= 2) {
                objects.add(parts[1]); // ex: "piston", "note_block", "zombie_villager"
            }
        }

        return new ArrayList<>(objects);
    }

    /**
     * Extrai o prefixo de categoria de um soundId (o segmento após o namespace e antes do primeiro ponto).
     * Privado — lógica interna do registry.
     * "minecraft:block.piston.extend" → "block"
     */
    private static String extractPrefix(String soundId) {
        String withoutNamespace = soundId.contains(":") ? soundId.split(":")[1] : soundId;
        int dotIndex = withoutNamespace.indexOf('.');
        return dotIndex >= 0 ? withoutNamespace.substring(0, dotIndex) : withoutNamespace;
    }

    /**
     * Agrupa uma lista de soundIds pelo "group key" (prefix.object).
     * Só devolve grupos com 2+ membros.
     * Exemplo: ["entity.horse.angry", "entity.horse.hurt", "entity.pig.ambient"]
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

    /** Extrai a chave de grupo de um soundId: "minecraft:entity.horse.angry" → "entity.horse". */
    public static String extractGroupKey(String soundId) {
        String withoutNs = soundId.contains(":") ? soundId.split(":")[1] : soundId;
        String[] parts = withoutNs.split("\\.");
        if (parts.length >= 2) return parts[0] + "." + parts[1];
        return null;
    }

    // Exporta a lista completa de sons para um ficheiro .txt — útil para referência e mapeamento manual
    private static void exportSoundList() {
        Path outFile = FabricLoader.getInstance().getConfigDir().resolve("soundtweaks_sounds.txt");
        try {
            Files.writeString(outFile, String.join("\n", knownSounds));
            SoundTweaks.LOGGER.info("SoundTweaks: lista de sons exportada para {}", outFile);
        } catch (IOException e) {
            SoundTweaks.LOGGER.warn("SoundTweaks: erro ao exportar lista de sons", e);
        }
    }
}
