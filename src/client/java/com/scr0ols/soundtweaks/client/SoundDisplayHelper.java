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

    // Map de overrides: "minecraft:block.note_block.basedrum" → "Note Block (Base Drum)"
    // Carregado uma vez no arranque — null significa ainda não carregado
    private static Map<String, String> overrides = null;

    // --- API pública ---

    /**
     * Devolve o nome de display para a GUI.
     * Tenta override primeiro; se não houver, usa auto-formatação.
     * Exemplos:
     *   "minecraft:block.piston.extend"       → "Piston (extend)"
     *   "minecraft:entity.zombie_villager.ambient" → "Zombie Villager (ambient)"
     *   "minecraft:music.creative"             → "Creative"
     */
    public static String getDisplayName(String soundId) {
        ensureOverridesLoaded();

        // 1. Verificar overrides primeiro
        String override = overrides.get(soundId);
        if (override != null) return override;

        // 2. Auto-formatação
        return autoFormat(soundId);
    }

    /**
     * Extrai o prefixo de categoria — o segmento antes do primeiro ponto,
     * após remover o namespace.
     * "minecraft:block.piston.extend" → "block"
     * "create:mechanical.piston.extend" → "mechanical"
     */
    public static String getCategoryPrefix(String soundId) {
        String withoutNamespace = removeNamespace(soundId);
        int dotIndex = withoutNamespace.indexOf('.');
        // Se não houver ponto (som malformado), a categoria é o próprio id
        return dotIndex >= 0 ? withoutNamespace.substring(0, dotIndex) : withoutNamespace;
    }

    /**
     * Extrai o nome do objecto — parts[1], formatado.
     * "minecraft:block.piston.extend"    → "Piston"
     * "minecraft:entity.zombie_villager" → "Zombie Villager"
     * Se não houver parts[1], devolve string vazia.
     */
    public static String getObjectName(String soundId) {
        String withoutNamespace = removeNamespace(soundId);
        String[] parts = withoutNamespace.split("\\.");
        if (parts.length < 2) return "";
        return formatWord(parts[1]);
    }

    // --- Formatação interna ---

    /**
     * Auto-formata um soundId para display.
     * Lógica:
     *   parts[0] = categoria (ignorado no display)
     *   parts[1] = objecto  → capitalizado, underscores → espaços
     *   parts[2+] = acção   → juntos com espaço, entre parênteses
     */
    static String autoFormat(String soundId) {
        String withoutNamespace = removeNamespace(soundId);
        String[] parts = withoutNamespace.split("\\.");

        // Casos de fallback: malformado ou sem partes suficientes
        if (parts.length == 0) return soundId;
        if (parts.length == 1) return formatWord(parts[0]);  // ex: "intentionally_empty"

        // parts[1] = objecto
        String objectPart = formatWord(parts[1]);

        // parts[2+] = acção (pode ser vazia se só houver 2 partes)
        if (parts.length == 2) {
            // "minecraft:music.creative" → "Creative"  (sem acção, não põe parênteses)
            return objectPart;
        }

        // "minecraft:block.piston.extend" → "Piston (extend)"
        // "minecraft:entity.zombie_villager.converted_by_cure" → "Zombie Villager (converted by cure)"
        StringBuilder action = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) action.append(" ");
            // A acção não é capitalizada — fica em lowercase para parecer descritiva
            action.append(parts[i].replace('_', ' '));
        }

        return objectPart + " (" + action + ")";
    }

    /**
     * Capitaliza a primeira letra e substitui underscores por espaços.
     * "zombie_villager" → "Zombie Villager"
     * "note_block"      → "Note Block"
     */
    private static String formatWord(String word) {
        if (word == null || word.isEmpty()) return word;

        // Substituir underscores por espaços e capitalizar cada palavra
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
     * Remove o namespace ("minecraft:", "create:", etc.).
     * Se não houver ":", devolve o id original.
     */
    private static String removeNamespace(String soundId) {
        int colonIndex = soundId.indexOf(':');
        return colonIndex >= 0 ? soundId.substring(colonIndex + 1) : soundId;
    }

    // --- Overrides ---

    /**
     * Garante que os overrides estão carregados.
     * Chamado lazy — só lê o ficheiro na primeira invocação.
     */
    private static void ensureOverridesLoaded() {
        if (overrides == null) {
            loadOverrides();
        }
    }

    /**
     * Lê soundtweaks_name_overrides.json da pasta config.
     * Se o ficheiro não existir, inicia o map vazio (sem erro).
     * Se o JSON for inválido, loga aviso e usa map vazio.
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
     * Força o recarregamento dos overrides — útil para testes.
     */
    public static void resetOverrides() {
        overrides = null;
    }
}
