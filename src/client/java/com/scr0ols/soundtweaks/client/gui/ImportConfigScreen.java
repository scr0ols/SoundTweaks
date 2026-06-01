package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.BlockConfig;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * Ecrã de importação de configs de outras instâncias.
 * O utilizador cola o caminho do ficheiro JSON e confirma.
 */
public class ImportConfigScreen extends Screen {

    public enum ImportType {
        PRESETS("Import Presets",
                "Imports presets from soundtweaks_presets.json",
                "New presets are added; existing IDs are kept."),
        SOUNDS("Import Sound Config",
                "Imports sound volumes from soundtweaks.json",
                "Replaces all current sound volumes."),
        BLOCKS("Import Block Config",
                "Imports block volumes from soundtweaks_blocks.json",
                "Replaces all current block volumes.");

        final String title, line1, line2;
        ImportType(String title, String line1, String line2) {
            this.title = title; this.line1 = line1; this.line2 = line2;
        }
    }

    private final Screen parent;
    private final ImportType type;
    private final Runnable onSuccess;

    private EditBox pathBox;
    private Button  confirmBtn, cancelBtn;

    // Feedback após tentativa de import
    private String feedbackMsg  = "";
    private int    feedbackColor = 0xFFAAAAAA;

    public ImportConfigScreen(Screen parent, ImportType type, Runnable onSuccess) {
        super(Component.literal(type.title));
        this.parent    = parent;
        this.type      = type;
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        int pw = 420, ph = 180;
        int px = this.width  / 2 - pw / 2;
        int py = this.height / 2 - ph / 2;

        int inputY = py + 80;

        this.pathBox = new EditBox(this.font, px + 10, inputY, pw - 20, 20, Component.empty());
        this.pathBox.setMaxLength(512);
        this.pathBox.setTextColor(0xFFFFFFFF);
        this.pathBox.setHint(Component.literal("e.g. C:\\...\\config\\soundtweaks_presets.json"));
        this.addRenderableWidget(this.pathBox);
        this.setFocused(pathBox);
        this.pathBox.setFocused(true);

        this.confirmBtn = Button.builder(Component.literal("Import"),
                btn -> doImport()
        ).bounds(px + pw / 2 - 105, py + ph - 30, 100, 20).build();
        this.addRenderableWidget(confirmBtn);

        this.cancelBtn = Button.builder(Component.literal("Cancel"),
                btn -> this.minecraft.setScreen(parent)
        ).bounds(px + pw / 2 + 5, py + ph - 30, 100, 20).build();
        this.addRenderableWidget(cancelBtn);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);

        int pw = 420, ph = 180;
        int px = this.width  / 2 - pw / 2;
        int py = this.height / 2 - ph / 2;

        // Fundo + borda
        g.fill(0, 0, this.width, this.height, 0xBB000000);
        g.fill(px, py, px + pw, py + ph, 0xFF1A1A2E);
        g.fill(px, py, px + pw, py + 1,  0xFF444466);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF444466);
        g.fill(px, py, px + 1, py + ph,  0xFF444466);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0xFF444466);

        // Título
        g.centeredText(this.font, type.title, this.width / 2, py + 10, 0xFFCCCCFF);
        g.fill(px + 8, py + 22, px + pw - 8, py + 23, 0xFF333355);

        // Descrição
        g.centeredText(this.font, type.line1, this.width / 2, py + 32, 0xFFAAAAAA);
        g.centeredText(this.font, type.line2, this.width / 2, py + 46, 0xFF777788);

        // Label do campo
        g.text(this.font, "File path:", px + 10, py + 68, 0xFF888899);

        // Pasta config atual (hint para o utilizador)
        String cfgDir = ConfigFileUtil.getConfigDirString();
        g.text(this.font, "Config folder: " + cfgDir, px + 10, py + 112, 0xFF555566);

        // Feedback
        if (!feedbackMsg.isEmpty()) {
            g.centeredText(this.font, feedbackMsg, this.width / 2, py + 130, feedbackColor);
        }
    }

    private void doImport() {
        String input = pathBox.getValue().trim();
        Path path = ConfigFileUtil.resolvePath(input);

        if (path == null) {
            feedbackMsg   = "File not found: " + (input.isBlank() ? "(empty)" : input);
            feedbackColor = 0xFFFF6666;
            return;
        }

        int result;
        switch (type) {
            case PRESETS -> result = PresetConfig.importFrom(path);
            case SOUNDS  -> result = SoundConfig.importFrom(path);
            case BLOCKS  -> result = BlockConfig.importFrom(path);
            default      -> result = -1;
        }

        if (result < 0) {
            feedbackMsg   = "Error reading file. Is it a valid JSON config?";
            feedbackColor = 0xFFFF6666;
        } else {
            feedbackMsg   = "Success! Imported " + result + " entries.";
            feedbackColor = 0xFF88FF88;
            if (onSuccess != null) onSuccess.run();
            // Fechar após breve delay — deixar o utilizador ver o feedback
            // (ou fechar imediatamente; escolha: fechar)
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            doImport(); return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parent); return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mx = event.x(), my = event.y();
        if (mx >= pathBox.getX() && mx < pathBox.getX() + pathBox.getWidth()
                && my >= pathBox.getY() && my < pathBox.getY() + pathBox.getHeight()) {
            this.setFocused(pathBox);
            pathBox.setFocused(true);
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }
}
