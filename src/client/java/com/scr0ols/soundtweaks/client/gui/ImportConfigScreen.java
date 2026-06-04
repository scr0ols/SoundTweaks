package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.VolumeConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/**
 * Screen for importing configs from other instances.
 * The user pastes the JSON file path and confirms.
 */
public class ImportConfigScreen extends Screen {

    public enum ImportType {
        PRESETS("soundtweaks.import.presets.title",
                "Import: adds presets from a JSON file.",
                "Export: saves all current presets to a JSON file.",
                "soundtweaks_presets_export.json"),
        SOUNDS("soundtweaks.import.sounds.title",
                "Import: replaces all current sound volumes.",
                "Export: saves current sound volumes to a JSON file.",
                "soundtweaks_export.json"),
        BLOCKS("soundtweaks.import.blocks.title",
                "Import: replaces all current block volumes.",
                "Export: saves current block volumes to a JSON file.",
                "soundtweaks_blocks_export.json");

        final String titleKey, line1, line2, defaultExportName;
        ImportType(String titleKey, String line1, String line2, String defaultExportName) {
            this.titleKey = titleKey; this.line1 = line1; this.line2 = line2;
            this.defaultExportName = defaultExportName;
        }
    }

    private final Screen parent;
    private final ImportType type;
    private final Runnable onSuccess;

    private EditBox pathBox;
    private Button  confirmBtn, exportBtn, cancelBtn;

    // Feedback after an import attempt
    private String feedbackMsg  = "";
    private int    feedbackColor = 0xFFAAAAAA;

    public ImportConfigScreen(Screen parent, ImportType type, Runnable onSuccess) {
        super(Component.translatable(type.titleKey));
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
        this.pathBox.setHint(Component.translatable("soundtweaks.gui.path_hint"));
        this.addRenderableWidget(this.pathBox);
        this.setFocused(pathBox);
        this.pathBox.setFocused(true);

        // three centred buttons: [ Import ] [ Export ] [ Cancel ]
        int btnW = 90, btnGap = 6;
        int totalBtns = btnW * 3 + btnGap * 2;
        int btnStartX = px + pw / 2 - totalBtns / 2;
        int btnY = py + ph - 30;

        this.confirmBtn = Button.builder(Component.translatable("soundtweaks.gui.import"),
                btn -> doImport()
        ).bounds(btnStartX, btnY, btnW, 20).build();
        this.addRenderableWidget(confirmBtn);

        this.exportBtn = Button.builder(Component.translatable("soundtweaks.gui.export"),
                btn -> doExport()
        ).bounds(btnStartX + btnW + btnGap, btnY, btnW, 20).build();
        this.addRenderableWidget(exportBtn);

        this.cancelBtn = Button.builder(Component.translatable("soundtweaks.gui.cancel"),
                btn -> this.minecraft.setScreen(parent)
        ).bounds(btnStartX + (btnW + btnGap) * 2, btnY, btnW, 20).build();
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

        // Title
        g.centeredText(this.font, net.minecraft.client.resources.language.I18n.get(type.titleKey), this.width / 2, py + 10, 0xFFCCCCFF);
        g.fill(px + 8, py + 22, px + pw - 8, py + 23, 0xFF333355);

        // Description
        g.centeredText(this.font, type.line1, this.width / 2, py + 32, 0xFFAAAAAA);
        g.centeredText(this.font, type.line2, this.width / 2, py + 46, 0xFF777788);

        // Label do campo
        g.text(this.font, "File path:", px + 10, py + 68, 0xFF888899);

        // Current config folder (hint for the user)
        String cfgDir = ConfigFileUtil.getConfigDirString();
        g.text(this.font, "Config folder: " + cfgDir, px + 10, py + 112, 0xFF555566);

        // Feedback
        if (!feedbackMsg.isEmpty()) {
            g.centeredText(this.font, feedbackMsg, this.width / 2, py + 130, feedbackColor);
        }
    }

    private void doExport() {
        String target;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json")).flip();
            target = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export " + net.minecraft.client.resources.language.I18n.get(type.titleKey), type.defaultExportName, filters,
                    "JSON file (*.json)");
        }
        if (target == null) return;

        int result;
        switch (type) {
            case PRESETS -> result = PresetConfig.exportTo(java.nio.file.Path.of(target));
            case SOUNDS  -> result = VolumeConfig.SOUNDS.exportTo(java.nio.file.Path.of(target));
            case BLOCKS  -> result = VolumeConfig.BLOCKS.exportTo(java.nio.file.Path.of(target));
            default      -> result = -1;
        }

        if (result < 0) {
            feedbackMsg   = "Export failed. Check logs for details.";
            feedbackColor = 0xFFFF6666;
        } else {
            feedbackMsg   = "Exported " + result + " entries to file.";
            feedbackColor = 0xFF88FF88;
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
            case SOUNDS  -> result = VolumeConfig.SOUNDS.importFrom(path);
            case BLOCKS  -> result = VolumeConfig.BLOCKS.importFrom(path);
            default      -> result = -1;
        }

        if (result < 0) {
            feedbackMsg   = "Error reading file. Is it a valid JSON config?";
            feedbackColor = 0xFFFF6666;
        } else {
            feedbackMsg   = "Success! Imported " + result + " entries.";
            feedbackColor = 0xFF88FF88;
            if (onSuccess != null) onSuccess.run();
            // Close immediately after success
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
