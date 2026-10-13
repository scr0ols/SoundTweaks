package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.layout.ImportDialogLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.platform.InputConstants;

import java.nio.file.Path;

/**
 * Screen for importing configs from other instances.
 * The user pastes the JSON file path and confirms.
 */
public class ImportConfigScreen extends Screen {

    public enum ImportType {
        PRESETS("soundtweaks.import.presets.title",
                "soundtweaks.import.presets.line1",
                "soundtweaks.import.presets.line2",
                "soundtweaks_presets_export.json"),
        SOUNDS("soundtweaks.import.sounds.title",
                "soundtweaks.import.sounds.line1",
                "soundtweaks.import.sounds.line2",
                "soundtweaks_export.json"),
        BLOCKS("soundtweaks.import.blocks.title",
                "soundtweaks.import.blocks.line1",
                "soundtweaks.import.blocks.line2",
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

    private ImportDialogLayout layout;
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
        String draft = pathBox != null ? pathBox.getValue() : "";
        this.layout = ImportDialogLayout.compute(this.width, this.height);
        ImportDialogLayout l = layout;

        this.pathBox = new EditBox(this.font, l.pathBox().x(), l.pathBox().y(), l.pathBox().w(), l.pathBox().h(),
                Component.empty());
        this.pathBox.setMaxLength(512);
        this.pathBox.setTextColor(0xFFFFFFFF);
        this.pathBox.setHint(Component.translatable("soundtweaks.gui.path_hint"));
        this.pathBox.setValue(draft);
        this.addRenderableWidget(this.pathBox);
        this.setFocused(pathBox);
        this.pathBox.setFocused(true);

        this.confirmBtn = dialogButton("soundtweaks.gui.import", l.importButton(), btn -> doImport());
        this.exportBtn  = dialogButton("soundtweaks.gui.export", l.exportButton(), btn -> doExport());
        this.cancelBtn  = dialogButton("soundtweaks.gui.cancel", l.cancelButton(),
                btn -> this.minecraft.gui.setScreen(parent));
    }

    private Button dialogButton(String key, Rect r, Button.OnPress action) {
        Button b = Button.builder(Component.translatable(key), action).bounds(r.x(), r.y(), r.w(), r.h()).build();
        this.addRenderableWidget(b);
        return b;
    }

    /** Dim + panel are drawn here, before the widgets, so they can never cover them. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Rect p = layout.panel();
        g.fill(0, 0, this.width, this.height, 0xBB000000);
        g.fill(p.x(), p.y(), p.right(), p.bottom(), 0xFF1A1A2E);
        g.fill(p.x(), p.y(), p.right(), p.y() + 1, 0xFF444466);
        g.fill(p.x(), p.bottom() - 1, p.right(), p.bottom(), 0xFF444466);
        g.fill(p.x(), p.y(), p.x() + 1, p.bottom(), 0xFF444466);
        g.fill(p.right() - 1, p.y(), p.right(), p.bottom(), 0xFF444466);
        g.fill(p.x() + 8, p.y() + 22, p.right() - 8, p.y() + 23, 0xFF333355);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        ImportDialogLayout l = layout;
        int cx = l.panel().x() + l.panel().w() / 2;

        g.centeredText(this.font, GuiText.ellipsize(this.font, I18n.get(type.titleKey), l.title().w()),
                cx, l.title().y() + 2, 0xFFCCCCFF);
        g.centeredText(this.font, GuiText.ellipsize(this.font, I18n.get(type.line1), l.description().w()),
                cx, l.description().y() + 2, 0xFFAAAAAA);
        g.centeredText(this.font, GuiText.ellipsize(this.font, I18n.get(type.line2), l.description().w()),
                cx, l.description().y() + 16, 0xFF777788);
        g.text(this.font, I18n.get("soundtweaks.import.file_path"), l.pathLabel().x(), l.pathLabel().y(), 0xFF888899);

        // Config folder: cut to the dialog width, full path in a tooltip
        String folder = I18n.get("soundtweaks.import.config_folder", ConfigFileUtil.getConfigDirString());
        String shown = GuiText.ellipsize(this.font, folder, l.folderLine().w());
        g.text(this.font, shown, l.folderLine().x(), l.folderLine().y(), 0xFF555566);
        if (!shown.equals(folder) && l.folderLine().contains(mouseX, mouseY)) {
            g.setTooltipForNextFrame(Component.literal(folder), mouseX, mouseY);
        }

        if (!feedbackMsg.isEmpty()) {
            int lineY = l.feedback().y();
            int shownLines = 0;
            for (FormattedCharSequence line : this.font.split(Component.literal(feedbackMsg), l.feedback().w())) {
                if (shownLines++ == 2) break;
                g.centeredText(this.font, line, cx, lineY, feedbackColor);
                lineY += 12;
            }
        }
    }

    private void doExport() {
        FileDialogs.saveJson(type.defaultExportName, this::exportTo, this::exportToConfigFolder);
    }

    /** The system file dialog is unavailable: export to the typed path, or else into the config folder. */
    private void exportToConfigFolder() {
        String input = pathBox.getValue().trim();
        Path target;
        try {
            target = input.isEmpty()
                    ? Path.of(ConfigFileUtil.getConfigDirString(), type.defaultExportName)
                    : Path.of(ConfigFileUtil.getConfigDirString()).resolve(input);
        } catch (java.nio.file.InvalidPathException e) {
            feedbackMsg   = I18n.get("soundtweaks.import.invalid_path", input);
            feedbackColor = 0xFFFF6666;
            return;
        }
        exportTo(target);
    }

    private void exportTo(Path target) {
        int result;
        switch (type) {
            case PRESETS -> result = PresetConfig.exportTo(target);
            case SOUNDS  -> result = VolumeConfig.SOUNDS.exportTo(target);
            case BLOCKS  -> result = VolumeConfig.BLOCKS.exportTo(target);
            default      -> result = -1;
        }

        if (result < 0) {
            feedbackMsg   = I18n.get("soundtweaks.import.export_failed");
            feedbackColor = 0xFFFF6666;
        } else {
            feedbackMsg   = I18n.get("soundtweaks.import.exported", result);
            feedbackColor = 0xFF88FF88;
        }
    }

    private void doImport() {
        String input = pathBox.getValue().trim();
        Path path = ConfigFileUtil.resolvePath(input);

        if (path == null) {
            feedbackMsg   = I18n.get("soundtweaks.import.not_found", input.isBlank() ? I18n.get("soundtweaks.import.empty") : input);
            feedbackColor = 0xFFFF6666;
            return;
        }

        if (type == ImportType.PRESETS) {
            PresetConfig.ImportResult ir = PresetConfig.importFrom(path);
            if (ir == null) {
                feedbackMsg   = I18n.get("soundtweaks.import.read_error");
                feedbackColor = 0xFFFF6666;
            } else if (ir.imported() == 0) {
                feedbackMsg   = I18n.get("soundtweaks.import.no_presets");
                feedbackColor = 0xFFFFAA44;
            } else if (ir.conflictsReassigned() > 0) {
                feedbackMsg   = I18n.get("soundtweaks.import.imported_conflicts", ir.imported(), ir.conflictsReassigned());
                feedbackColor = 0xFFFFAA44;
                if (onSuccess != null) onSuccess.run();
                // Stay open so the user sees the conflict warning
            } else {
                feedbackMsg   = I18n.get("soundtweaks.import.imported_presets", ir.imported());
                feedbackColor = 0xFF88FF88;
                if (onSuccess != null) onSuccess.run();
                this.minecraft.gui.setScreen(parent);
            }
            return;
        }

        int result;
        switch (type) {
            case SOUNDS  -> result = VolumeConfig.SOUNDS.importFrom(path);
            case BLOCKS  -> result = VolumeConfig.BLOCKS.importFrom(path);
            default      -> result = -1;
        }

        if (result < 0) {
            feedbackMsg   = I18n.get("soundtweaks.import.read_error");
            feedbackColor = 0xFFFF6666;
        } else if (result == 0) {
            feedbackMsg   = I18n.get("soundtweaks.import.no_entries");
            feedbackColor = 0xFFFFAA44;
        } else {
            feedbackMsg   = I18n.get("soundtweaks.import.imported_entries", result);
            feedbackColor = 0xFF88FF88;
            if (onSuccess != null) onSuccess.run();
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            doImport(); return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            this.minecraft.gui.setScreen(parent); return true;
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
    public void onClose() { this.minecraft.gui.setScreen(parent); }
}
