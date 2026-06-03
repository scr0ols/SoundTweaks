package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Ecrã de gestão de presets — layout master-detail.
 * Esquerda: lista de presets simplificada (ON/OFF + estrela + nome).
 * Direita: painel de configuração do preset seleccionado (cor, rename, shortcut, edit sounds).
 */
public class PresetsScreen extends Screen {

    private final Screen parent;
    private PresetListWidget presetList;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int LIST_W        = 330;  // largura do painel esquerdo
    private static final int PANEL_HDR_H   = 28;   // altura do header do painel direito
    private static final int TAB_H         = 20;   // altura dos tabs
    private static final int CONTENT_Y     = 56;   // PANEL_HDR_H + TAB_H + 8
    private static final int DELETE_BTN_W  = 80;

    private int panelX() { return LIST_W + 1; }
    private int panelW() { return this.width - LIST_W - 1; }

    // ── Create overlay ────────────────────────────────────────────────────────
    private boolean creating = false;
    private EditBox createBox;
    private Button  createConfirmBtn, createCancelBtn;

    // ── Painel de detalhe (direita) ───────────────────────────────────────────
    private enum EditMode { NONE, COLOR, RENAME, SHORTCUT }
    private EditMode editMode = EditMode.NONE;
    @Nullable private PresetConfig.Preset editingPreset = null;

    // Widgets de rename
    private EditBox renameBox;
    private Button  renameConfirmBtn, renameCancelBtn;

    // Color hex EditBox
    private EditBox colorHexBox;

    // Captura de atalho
    private final LinkedHashSet<Integer> captureHeldKeys   = new LinkedHashSet<>();
    private final List<Integer>          lastHeldAtTrigger = new ArrayList<>();
    private int lastCapturedTrigger = 0;

    // Larguras dos 4 tabs
    private static final int[] TAB_W = {68, 68, 76, 80};

    // ── Construtor ────────────────────────────────────────────────────────────

    public PresetsScreen(Screen parent) {
        super(Component.translatable("soundtweaks.presets.title"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int listTop    = PANEL_HDR_H;
        int listBottom = this.height - 56;

        this.presetList = new PresetListWidget(this.minecraft, LIST_W,
                listBottom - listTop, listTop, 24);
        this.addRenderableWidget(this.presetList);
        this.presetList.refresh();

        // ── Footer ───────────────────────────────────────────────────────────
        // Linha 1 (height-50): [+ New Preset | lista] [Done | painel]
        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.presets.new"),
                btn -> enterCreateMode()
        ).bounds(4, this.height - 50, LIST_W - 8, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.gui.done"),
                btn -> this.onClose()
        ).bounds(panelX() + panelW() / 2 - 60, this.height - 50, 120, 20).build());

        // Linha 2 (height-26): [Import Presets | metade lista] [Open Config | metade lista]
        var importBtn = Button.builder(
                Component.literal("Import Presets..."),
                btn -> {
                    String selected;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer filters = stack.mallocPointer(1);
                        filters.put(stack.UTF8("*.json")).flip();
                        selected = TinyFileDialogs.tinyfd_openFileDialog(
                                "Select soundtweaks_presets.json", "", filters,
                                "JSON preset file (*.json)", false);
                    }
                    if (selected == null) return;
                    int result = PresetConfig.importFrom(java.nio.file.Path.of(selected));
                    if (result >= 0) presetList.refresh();
                }
        ).bounds(4, this.height - 26, LIST_W / 2 - 6, 18).build();
        importBtn.setTooltip(Tooltip.create(Component.literal(
                "Import presets from a shared file.\nA new ID is always generated — no conflicts.")));
        this.addRenderableWidget(importBtn);

        this.addRenderableWidget(Button.builder(
                Component.literal("Open Config Folder"),
                btn -> ConfigFileUtil.openConfigFolder()
        ).bounds(LIST_W / 2 + 2, this.height - 26, LIST_W / 2 - 6, 18).build());

        // ── Create overlay widgets ────────────────────────────────────────────
        int cx = this.width / 2 - 130, cy = this.height / 2 - 22;
        this.createBox = new EditBox(this.font, cx, cy, 220, 20, Component.empty());
        this.createBox.setHint(Component.translatable("soundtweaks.presets.name_hint"));
        this.createBox.setMaxLength(64);
        this.createBox.visible = false;
        this.addRenderableWidget(this.createBox);

        this.createConfirmBtn = Button.builder(
                Component.translatable("soundtweaks.presets.confirm"),
                btn -> confirmCreate()
        ).bounds(cx, cy + 24, 106, 18).build();
        this.createConfirmBtn.visible = false;
        this.addRenderableWidget(this.createConfirmBtn);

        this.createCancelBtn = Button.builder(Component.literal("Cancel"),
                btn -> exitCreateMode()
        ).bounds(cx + 110, cy + 24, 106, 18).build();
        this.createCancelBtn.visible = false;
        this.addRenderableWidget(this.createCancelBtn);

        // ── Rename widgets (painel direito) ──────────────────────────────────
        int renX = panelX() + 40;
        int renY = CONTENT_Y + 18;
        this.renameBox = new EditBox(this.font, renX, renY, panelW() - 80, 20, Component.empty());
        this.renameBox.setMaxLength(64);
        this.renameBox.visible = false;
        this.addRenderableWidget(this.renameBox);

        this.renameConfirmBtn = Button.builder(Component.literal("Save name"),
                btn -> confirmRename()
        ).bounds(renX, renY + 26, 115, 18).build();
        this.renameConfirmBtn.visible = false;
        this.addRenderableWidget(this.renameConfirmBtn);

        this.renameCancelBtn = Button.builder(Component.literal("Clear"),
                btn -> { renameBox.setValue(""); this.setFocused(renameBox); renameBox.setFocused(true); }
        ).bounds(renX + 125, renY + 26, 115, 18).build();
        this.renameCancelBtn.visible = false;
        this.addRenderableWidget(this.renameCancelBtn);

        // ── Color hex EditBox (painel direito) ────────────────────────────────
        int cgridW  = 6 * 22 + 5 * 3;  // 147px
        int cgridX  = panelX() + panelW() / 2 - cgridW / 2;
        int gridY   = CONTENT_Y + 20;   // 76
        int customY = gridY + 3 * (22 + 3) + 12;  // 163
        this.colorHexBox = new EditBox(this.font, cgridX + 26, customY, 110, 18, Component.empty());
        this.colorHexBox.setMaxLength(6);
        this.colorHexBox.setTextColor(0xFFFFFFFF);
        this.colorHexBox.visible = false;
        this.colorHexBox.setResponder(hex -> {
            if (editingPreset == null) return;
            try {
                int rgb = Integer.parseUnsignedInt(hex.trim(), 16);
                editingPreset.customColor = 0xFF000000 | (rgb & 0xFFFFFF);
                PresetConfig.markDirty();
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(this.colorHexBox);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // Esconder widgets de overlay antes de super renderizar
        setCreateWidgetsVisible(false);
        setRenameWidgetsVisible(false);
        colorHexBox.visible = false;

        super.extractRenderState(g, mouseX, mouseY, a);

        // Restaurar widgets visíveis
        if (creating) setCreateWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.RENAME) setRenameWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.COLOR
                && editingPreset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX)
            colorHexBox.visible = true;

        // ── Painel esquerdo — cabeçalho ──────────────────────────────────────
        g.centeredText(this.font, I18n.get("soundtweaks.presets.title"), LIST_W / 2, 10, 0xFFFFFFFF);
        g.fill(4, PANEL_HDR_H - 2, LIST_W - 4, PANEL_HDR_H - 1, 0xFF555555);

        // ── Divisor entre painéis ─────────────────────────────────────────────
        g.fill(LIST_W, 0, LIST_W + 1, this.height - 30, 0xFF111111);

        // ── Separador acima do footer ─────────────────────────────────────────
        g.fill(4, this.height - 54, this.width - 4, this.height - 53, 0xFF555555);

        // ── Painel direito ────────────────────────────────────────────────────
        renderDetailPanel(g, mouseX, mouseY, a);

        // ── Create overlay (por cima de tudo) ─────────────────────────────────
        if (creating) {
            g.fill(0, 0, this.width, this.height, 0xBB000000);
            int cx = this.width / 2 - 130, cy = this.height / 2 - 22;
            g.fill(cx - 10, cy - 26, cx + 232, cy + 46, 0xFF222233);
            g.fill(cx - 10, cy - 26, cx + 232, cy - 25, 0xFF444466);
            g.text(this.font, "New preset name:", cx, cy - 18, 0xFFCCCCFF);
            createBox.extractRenderState(g, mouseX, mouseY, a);
            createConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
            createCancelBtn.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    // ── Painel de detalhe ─────────────────────────────────────────────────────

    private void renderDetailPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int px = panelX();
        int pw = panelW();
        int cx = px + pw / 2;

        if (editingPreset == null) {
            g.centeredText(this.font, "Select a preset to configure",
                    cx, this.height / 2 - 8, 0xFF555566);
            g.centeredText(this.font, "or create a new one below",
                    cx, this.height / 2 + 8, 0xFF444455);
            return;
        }

        PresetConfig.Preset preset = editingPreset;
        int pc = preset.argbColor() & 0x00FFFFFF;

        // Header do painel com cor do preset
        g.fill(px, 0, this.width, PANEL_HDR_H, pc | 0x99000000);
        g.centeredText(this.font, preset.name, cx, 10, 0xFFFFFFFF);
        g.fill(px, PANEL_HDR_H - 1, this.width, PANEL_HDR_H, 0xFF333344);

        // Tabs
        renderTabs(g, mouseX, mouseY, px);
        g.fill(px + 4, PANEL_HDR_H + TAB_H + 2, this.width - 4, PANEL_HDR_H + TAB_H + 3, 0xFF333355);

        // Botão Delete (acima do footer, esquerda do painel)
        int delY = this.height - 50;
        boolean hovDel = mouseX >= px + 4 && mouseX < px + 4 + DELETE_BTN_W
                && mouseY >= delY && mouseY < delY + 20;
        g.fill(px + 3, delY - 1, px + 5 + DELETE_BTN_W, delY + 21, 0xFF111111);
        g.fill(px + 4, delY, px + 4 + DELETE_BTN_W, delY + 20, hovDel ? 0xFF4A2020 : 0xFF3A1A1A);
        g.centeredText(this.font, "Delete", px + 4 + DELETE_BTN_W / 2, delY + 6,
                hovDel ? 0xFFFF8888 : 0xFFCC5555);

        // Conteúdo do tab activo
        switch (editMode) {
            case COLOR    -> renderColorContent(g, mouseX, mouseY, px, pw, preset, a);
            case RENAME   -> renderRenameContent(g, mouseX, mouseY, a);
            case SHORTCUT -> renderShortcutContent(g, cx);
            default       -> {}
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, int px) {
        String[] labels = {"Color", "Rename", "Shortcut", "Edit Sounds"};
        EditMode[] modes = {EditMode.COLOR, EditMode.RENAME, EditMode.SHORTCUT, null};
        int tabX = px + 4, tabY = PANEL_HDR_H;

        for (int i = 0; i < 4; i++) {
            boolean active = (modes[i] != null && editMode == modes[i]);
            boolean hov    = mouseX >= tabX && mouseX < tabX + TAB_W[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_H;

            int bg = active ? 0xFF334466 : (hov ? 0xFF2A2A44 : 0xFF222233);
            g.fill(tabX, tabY, tabX + TAB_W[i], tabY + TAB_H, bg);
            g.fill(tabX, tabY, tabX + TAB_W[i], tabY + 1,
                    active ? 0xFF8888FF : 0xFF444466);
            g.centeredText(this.font, labels[i], tabX + TAB_W[i] / 2, tabY + 6,
                    active ? 0xFFFFFFFF : (hov ? 0xFFCCCCCC : 0xFF888899));
            tabX += TAB_W[i] + 4;
        }
    }

    private void renderColorContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                    int px, int pw, PresetConfig.Preset preset, float a) {
        int sq = 22, gap = 3, cols = 6;
        int gridW = cols * sq + (cols - 1) * gap;
        int gridX = px + pw / 2 - gridW / 2;
        int gridY = CONTENT_Y + 20;

        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % cols, row = i / cols;
            int qx = gridX + col * (sq + gap);
            int qy = gridY + row * (sq + gap);
            g.fill(qx, qy, qx + sq, qy + sq, PresetConfig.PRESET_COLORS[i] | 0xFF000000);

            boolean selected = (i == preset.colorIndex);
            boolean hov = mouseX >= qx && mouseX < qx + sq && mouseY >= qy && mouseY < qy + sq;
            if (selected) {
                g.fill(qx - 2, qy - 2, qx + sq + 2, qy,          0xFFFFFFFF);
                g.fill(qx - 2, qy + sq, qx + sq + 2, qy + sq + 2, 0xFFFFFFFF);
                g.fill(qx - 2, qy,      qx,           qy + sq,     0xFFFFFFFF);
                g.fill(qx + sq, qy,     qx + sq + 2,  qy + sq,     0xFFFFFFFF);
            } else if (hov) {
                g.fill(qx - 1, qy - 1, qx + sq + 1, qy,          0xFF888888);
                g.fill(qx - 1, qy + sq, qx + sq + 1, qy + sq + 1, 0xFF888888);
                g.fill(qx - 1, qy,      qx,           qy + sq,     0xFF888888);
                g.fill(qx + sq, qy,     qx + sq + 1,  qy + sq,     0xFF888888);
            }
        }

        // Slot custom
        int customY = gridY + 3 * (sq + gap) + 12;
        boolean customSel = (preset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX);
        boolean customHov = mouseX >= gridX && mouseX < gridX + sq
                && mouseY >= customY && mouseY < customY + sq;

        if (preset.customColor != 0) {
            g.fill(gridX, customY, gridX + sq, customY + sq, preset.customColor | 0xFF000000);
        } else {
            g.fill(gridX, customY, gridX + sq, customY + sq, 0xFF1A1A2E);
            g.fill(gridX,          customY,          gridX + sq, customY + 1,      0xFF556677);
            g.fill(gridX,          customY + sq - 1, gridX + sq, customY + sq,     0xFF556677);
            g.fill(gridX,          customY,          gridX + 1,  customY + sq,     0xFF556677);
            g.fill(gridX + sq - 1, customY,          gridX + sq, customY + sq,     0xFF556677);
            if (!customSel)
                g.centeredText(this.font, "+", gridX + sq / 2, customY + (sq - 8) / 2, 0xFF556677);
        }
        if (customSel) {
            g.fill(gridX - 2, customY - 2, gridX + sq + 2, customY,          0xFFFFFFFF);
            g.fill(gridX - 2, customY + sq, gridX + sq + 2, customY + sq + 2, 0xFFFFFFFF);
            g.fill(gridX - 2, customY,      gridX,           customY + sq,     0xFFFFFFFF);
            g.fill(gridX + sq, customY,     gridX + sq + 2,  customY + sq,     0xFFFFFFFF);
        } else if (customHov) {
            g.fill(gridX - 1, customY - 1, gridX + sq + 1, customY,          0xFF888888);
            g.fill(gridX - 1, customY + sq, gridX + sq + 1, customY + sq + 1, 0xFF888888);
            g.fill(gridX - 1, customY,      gridX,           customY + sq,     0xFF888888);
            g.fill(gridX + sq, customY,     gridX + sq + 1,  customY + sq,     0xFF888888);
        }

        g.text(this.font, "Custom", gridX + sq + 6, customY + (sq - 8) / 2,
                customSel ? 0xFFCCCCFF : 0xFF666688);
        if (customSel) colorHexBox.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderRenameContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        renameBox.extractRenderState(g, mouseX, mouseY, a);
        renameConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
        renameCancelBtn.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderShortcutContent(GuiGraphicsExtractor g, int cx) {
        String captureLabel;
        if (lastCapturedTrigger != 0) {
            StringBuilder sb = new StringBuilder();
            for (int k : lastHeldAtTrigger) sb.append(rawKeyName(k)).append(" + ");
            sb.append(rawKeyName(lastCapturedTrigger));
            captureLabel = sb.toString();
        } else {
            captureLabel = "---";
        }
        g.centeredText(this.font, captureLabel, cx, CONTENT_Y + 14,
                lastCapturedTrigger != 0 ? 0xFF88FF88 : 0xFF555555);

        String savedLabel = (editingPreset != null) ? keyDisplayLabel(editingPreset) : "---";
        boolean hasSaved  = !savedLabel.equals("---");
        g.centeredText(this.font, hasSaved ? "[" + savedLabel + "]" : "[blank]", cx, CONTENT_Y + 34,
                hasSaved ? 0xFFAABBAA : 0xFF666666);
        g.centeredText(this.font, "ENTER to confirm  ·  BACKSPACE to clear  ·  ESC to cancel",
                cx, CONTENT_Y + 50, 0xFF777777);
    }

    // ── Visibilidade de widgets de overlay ────────────────────────────────────

    private void setCreateWidgetsVisible(boolean v) {
        createBox.visible = v; createConfirmBtn.visible = v; createCancelBtn.visible = v;
    }

    private void setRenameWidgetsVisible(boolean v) {
        renameBox.visible = v; renameConfirmBtn.visible = v; renameCancelBtn.visible = v;
    }

    // ── Teclado ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (editingPreset != null) {
            if (editMode == EditMode.SHORTCUT) { handleShortcutKey(key); return true; }
            if (editMode == EditMode.RENAME) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirmRename(); return true; }
                if (key == GLFW.GLFW_KEY_ESCAPE) { setEditMode(EditMode.COLOR); return true; }
                return super.keyPressed(event);
            }
            if (editMode == EditMode.COLOR && this.getFocused() == colorHexBox) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                    this.setFocused(null); return true;
                }
                return super.keyPressed(event);
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { closeDetailPanel(); return true; }
            return true;
        }

        if (creating) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirmCreate(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { exitCreateMode(); return true; }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (editingPreset != null && editMode == EditMode.SHORTCUT) {
            captureHeldKeys.remove(event.key()); return true;
        }
        return super.keyReleased(event);
    }

    private void handleShortcutKey(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { resetShortcutCapture(); setEditMode(EditMode.COLOR); return; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            resetShortcutCapture();
            if (editingPreset != null) {
                editingPreset.shortcutKey = 0; editingPreset.shortcutHeldKey = 0; editingPreset.shortcutHeldKey2 = 0;
                PresetConfig.markDirty();
            }
            presetList.refresh();
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (lastCapturedTrigger != 0) confirmShortcut();
            return;
        }
        lastHeldAtTrigger.clear();
        lastHeldAtTrigger.addAll(captureHeldKeys);
        while (lastHeldAtTrigger.size() > 2) lastHeldAtTrigger.remove(0);
        lastCapturedTrigger = key;
        captureHeldKeys.add(key);
    }

    private void confirmShortcut() {
        if (editingPreset == null) return;
        int h1 = 0, h2 = 0;
        if (lastHeldAtTrigger.size() == 1) h1 = lastHeldAtTrigger.get(0);
        else if (lastHeldAtTrigger.size() >= 2) {
            h1 = lastHeldAtTrigger.get(lastHeldAtTrigger.size() - 2);
            h2 = lastHeldAtTrigger.get(lastHeldAtTrigger.size() - 1);
        }
        editingPreset.shortcutKey = lastCapturedTrigger & 0xFFFF;
        editingPreset.shortcutHeldKey = h1; editingPreset.shortcutHeldKey2 = h2;
        PresetConfig.markDirty();
        resetShortcutCapture();
        presetList.refresh();
    }

    private void resetShortcutCapture() {
        captureHeldKeys.clear(); lastHeldAtTrigger.clear(); lastCapturedTrigger = 0;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mx = event.x(), my = event.y();

        // Create overlay intercepção total
        if (creating) {
            if (createConfirmBtn.mouseClicked(event, consumed)) return true;
            if (createCancelBtn.mouseClicked(event, consumed))  return true;
            if (mx >= createBox.getX() && mx < createBox.getX() + createBox.getWidth()
                    && my >= createBox.getY() && my < createBox.getY() + createBox.getHeight()) {
                this.setFocused(createBox); createBox.setFocused(true);
                createBox.mouseClicked(event, consumed);
            }
            return true;
        }

        // Widgets (lista, botões de footer, rename, hex box)
        if (super.mouseClicked(event, consumed)) return true;

        // Áreas manuais do painel direito (tabs, color grid, delete)
        if (editingPreset != null && mx >= panelX()) {
            int px = panelX();

            // Delete
            int delY = this.height - 50;
            if (mx >= px + 4 && mx < px + 4 + DELETE_BTN_W && my >= delY && my < delY + 20) {
                PresetConfig.Preset target = editingPreset;
                this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            PresetConfig.deletePreset(target.name);
                            editingPreset = null; editMode = EditMode.NONE;
                            presetList.refresh();
                        }
                        this.minecraft.setScreen(PresetsScreen.this);
                    },
                    Component.literal("Apagar preset?"),
                    Component.literal("\"" + target.name + "\" será eliminado permanentemente.")
                ));
                return true;
            }

            // Tabs
            int tabX = px + 4, tabY = PANEL_HDR_H;
            for (int i = 0; i < 4; i++) {
                if (mx >= tabX && mx < tabX + TAB_W[i] && my >= tabY && my < tabY + TAB_H) {
                    handleTabClick(i); return true;
                }
                tabX += TAB_W[i] + 4;
            }

            // Color grid
            if (editMode == EditMode.COLOR) {
                if (colorHexBox.visible
                        && mx >= colorHexBox.getX() && mx < colorHexBox.getX() + colorHexBox.getWidth()
                        && my >= colorHexBox.getY() && my < colorHexBox.getY() + colorHexBox.getHeight()) {
                    this.setFocused(colorHexBox); colorHexBox.setFocused(true);
                    colorHexBox.mouseClicked(event, false);
                    return true;
                }
                handleColorGridClick(mx, my, px, panelW(), editingPreset);
            } else if (editMode == EditMode.RENAME) {
                if (renameConfirmBtn.mouseClicked(event, false)) return true;
                if (renameCancelBtn.mouseClicked(event, false))  return true;
                if (mx >= renameBox.getX() && mx < renameBox.getX() + renameBox.getWidth()
                        && my >= renameBox.getY() && my < renameBox.getY() + renameBox.getHeight()) {
                    this.setFocused(renameBox); renameBox.setFocused(true);
                    renameBox.mouseClicked(event, false);
                }
            }
            return true;
        }

        return false;
    }

    private void handleTabClick(int tabIndex) {
        switch (tabIndex) {
            case 0 -> setEditMode(EditMode.COLOR);
            case 1 -> setEditMode(EditMode.RENAME);
            case 2 -> setEditMode(EditMode.SHORTCUT);
            case 3 -> { if (editingPreset != null) this.minecraft.setScreen(new PresetEditorScreen(this, editingPreset)); }
        }
    }

    private void handleColorGridClick(double mx, double my, int px, int pw, PresetConfig.Preset preset) {
        if (preset == null) return;
        int sq = 22, gap = 3, cols = 6;
        int gridW = cols * sq + (cols - 1) * gap;
        int gridX = px + pw / 2 - gridW / 2;
        int gridY = CONTENT_Y + 20;

        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % cols, row = i / cols;
            int qx = gridX + col * (sq + gap);
            int qy = gridY + row * (sq + gap);
            if (mx >= qx && mx < qx + sq && my >= qy && my < qy + sq) {
                preset.colorIndex = i; PresetConfig.markDirty(); this.setFocused(null); return;
            }
        }
        int customY = gridY + 3 * (sq + gap) + 12;
        if (mx >= gridX && mx < gridX + sq && my >= customY && my < customY + sq) {
            preset.colorIndex = PresetConfig.CUSTOM_COLOR_INDEX;
            if (preset.customColor == 0) preset.customColor = 0xFF888888;
            PresetConfig.markDirty();
            colorHexBox.setValue(String.format("%06X", preset.customColor & 0xFFFFFF));
            colorHexBox.visible = true;
            this.setFocused(colorHexBox); colorHexBox.setFocused(true);
        }
    }

    // ── Painel de detalhe: abrir / fechar / trocar modo ──────────────────────

    void openEditOverlay(PresetConfig.Preset preset) {
        this.editingPreset = preset;
        setEditMode(EditMode.COLOR);
    }

    private void setEditMode(EditMode mode) {
        this.editMode = mode;
        setRenameWidgetsVisible(false);
        if (mode == EditMode.RENAME && editingPreset != null) {
            renameBox.setValue(editingPreset.name);
            setRenameWidgetsVisible(true);
            this.setFocused(renameBox); renameBox.setFocused(true);
        } else {
            this.setFocused(null);
        }
        if (mode == EditMode.SHORTCUT) resetShortcutCapture();
    }

    private void closeDetailPanel() {
        this.editingPreset = null; this.editMode = EditMode.NONE;
        setRenameWidgetsVisible(false); this.setFocused(null);
        if (presetList != null) presetList.setSelected(null);
        presetList.refresh();
    }

    // ── Criar preset ──────────────────────────────────────────────────────────

    private void enterCreateMode() {
        creating = true; createBox.setValue("");
        setCreateWidgetsVisible(true); this.setFocused(createBox); createBox.setFocused(true);
    }

    private void confirmCreate() {
        String name = createBox.getValue().trim();
        if (!name.isEmpty()) {
            PresetConfig.createFromCurrentConfig(name);
            presetList.refresh();
            // Seleccionar o novo preset no painel
            List<PresetConfig.Preset> all = PresetConfig.getPresets();
            if (!all.isEmpty()) openEditOverlay(all.get(all.size() - 1));
        }
        exitCreateMode();
    }

    private void exitCreateMode() {
        creating = false; setCreateWidgetsVisible(false); this.setFocused(null);
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    private void confirmRename() {
        if (editingPreset != null) {
            String name = renameBox.getValue().trim();
            if (!name.isEmpty()) { PresetConfig.renamePreset(editingPreset.name, name); presetList.refresh(); }
        }
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    // =========================================================================
    // Lista de presets (painel esquerdo)
    // =========================================================================

    class PresetListWidget extends AbstractSelectionList<PresetListWidget.PresetRow> {

        public PresetListWidget(net.minecraft.client.Minecraft mc,
                                int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        public void refresh() {
            this.clearEntries();
            for (PresetConfig.Preset preset : PresetConfig.getPresets())
                this.addEntry(new PresetRow(preset));
        }

        @Override public int getRowWidth() { return LIST_W - 20; }
        @Override protected int scrollBarX() { return this.getX() + LIST_W - 6; }
        @Override public void updateWidgetNarration(NarrationElementOutput o) {}

        // ── Linha de preset (simplificada) ───────────────────────────────────
        // [barra cor] [badge ON/OFF] [quadrado cor] [nome + atalho] ... [★]

        class PresetRow extends AbstractSelectionList.Entry<PresetRow> {

            private final PresetConfig.Preset preset;

            PresetRow(PresetConfig.Preset preset) { this.preset = preset; }

            private int rowW()  { return PresetListWidget.this.getRowWidth(); }
            private int starX() { return getX() + rowW() - 22; }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                       boolean hovered, float a) {
                boolean active   = PresetConfig.isActive(preset.name);
                boolean fav      = PresetConfig.isFavorite(preset.name);
                boolean selected = (PresetsScreen.this.editingPreset == preset);
                int rW = rowW();
                int pc = preset.argbColor();

                // Fundo da linha
                int rowBg = selected ? 0xFF383838 : hovered ? 0xFF282828 : 0xFF222222;
                g.fill(getX(), getY(), getX() + rW, getY() + 24, rowBg);
                g.fill(getX(), getY() + 23, getX() + rW, getY() + 24, 0xFF111111);

                // Barra colorida à esquerda (mais espessa quando seleccionado ou activo)
                int barW = (selected || active) ? 6 : 4;
                g.fill(getX(), getY(), getX() + barW, getY() + 23, pc | 0xFF000000);

                // Badge ON/OFF
                int badgeX = getX() + 10, badgeY = getY() + 6;
                int badgeBorder = active ? 0xFF336633 : 0xFF444444;
                int badgeBg     = active ? 0xFF1A3A1A : 0xFF2A2A2A;
                g.fill(badgeX - 1, badgeY - 1, badgeX + 23, badgeY + 12, badgeBorder);
                g.fill(badgeX, badgeY, badgeX + 22, badgeY + 11, badgeBg);
                g.centeredText(PresetListWidget.this.minecraft.font,
                        active ? "ON" : "OFF", badgeX + 11, badgeY + 2,
                        active ? 0xFF55FF55 : 0xFF888888);

                // Quadrado de cor (12×12 com borda preta)
                int sq = 12, sqX = getX() + 38, sqY = getY() + 6;
                g.fill(sqX - 1, sqY - 1, sqX + sq + 1, sqY + sq + 1, 0xFF000000);
                g.fill(sqX, sqY, sqX + sq, sqY + sq, pc | 0xFF000000);

                // Nome + atalho
                int nameCol = selected ? 0xFFFFFFFF : active ? 0xFFDDDDDD : 0xFF999999;
                g.text(PresetListWidget.this.minecraft.font, preset.name,
                        getX() + 58, getY() + 8, nameCol);
                String sc = keyDisplayLabel(preset);
                if (!sc.equals("---")) {
                    g.text(PresetListWidget.this.minecraft.font,
                            " [" + sc + "]",
                            getX() + 58 + PresetListWidget.this.minecraft.font.width(preset.name),
                            getY() + 8, 0xFF556655);
                }

                // [★] estrela
                int sx = starX();
                boolean hovStar = mouseX >= sx && mouseX < sx + 18
                        && mouseY >= getY() + 4 && mouseY < getY() + 20;
                g.fill(sx - 1, getY() + 3, sx + 19, getY() + 21, 0xFF111111);
                g.fill(sx, getY() + 4, sx + 18, getY() + 20, hovStar ? 0xFF4A4A4A : 0xFF3A3A3A);
                g.centeredText(PresetListWidget.this.minecraft.font,
                        fav ? "★" : "☆", sx + 9, getY() + 7,
                        fav ? 0xFFFFDD44 : 0xFF777777);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
                if (consumed) return false;
                if (PresetsScreen.this.creating) return false;

                double mx = event.x(), my = event.y();

                // Estrela
                int sx = starX();
                if (mx >= sx && mx < sx + 18 && my >= getY() + 4 && my < getY() + 20) {
                    PresetConfig.setFavorite(preset.name, !PresetConfig.isFavorite(preset.name));
                    return true;
                }

                // Badge ON/OFF
                int badgeX = getX() + 10, badgeY = getY() + 6;
                if (mx >= badgeX && mx < badgeX + 22 && my >= badgeY && my < badgeY + 11) {
                    PresetConfig.setActive(preset.name, !PresetConfig.isActive(preset.name));
                    return true;
                }

                // Seleccionar → mostrar no painel direito
                PresetListWidget.this.setSelected(this);
                PresetsScreen.this.openEditOverlay(preset);
                return true;
            }

            @Override public boolean mouseDragged(MouseButtonEvent e, double dX, double dY) { return false; }
            @Override public boolean mouseReleased(MouseButtonEvent e) { return false; }
            public void updateNarration(NarrationElementOutput o) {}
        }
    }

    // ── Utilitário: nome da tecla ─────────────────────────────────────────────

    static String keyDisplayLabel(PresetConfig.Preset preset) {
        if (preset.shortcutKey <= 0 && preset.shortcutHeldKey <= 0) return "---";
        if (preset.shortcutHeldKey != 0) {
            String s = rawKeyName(preset.shortcutHeldKey);
            if (preset.shortcutHeldKey2 != 0) s += "+" + rawKeyName(preset.shortcutHeldKey2);
            s += "+" + rawKeyName(preset.shortcutKey & 0xFFFF);
            return s;
        }
        return rawKeyName(preset.shortcutKey & 0xFFFF);
    }

    static String rawKeyName(int glfwKey) {
        if (glfwKey <= 0) return "---";
        String s = GLFW.glfwGetKeyName(glfwKey, 0);
        if (s != null && !s.isBlank()) return s.toUpperCase();
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_F1  -> "F1";   case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";   case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";   case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";   case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";   case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";  case GLFW.GLFW_KEY_F12 -> "F12";
            case GLFW.GLFW_KEY_UP    -> "UP";    case GLFW.GLFW_KEY_DOWN  -> "DOWN";
            case GLFW.GLFW_KEY_LEFT  -> "LEFT";  case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_INSERT    -> "INS";   case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_HOME      -> "HOME";  case GLFW.GLFW_KEY_END    -> "END";
            case GLFW.GLFW_KEY_PAGE_UP   -> "PgUp";  case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_SPACE      -> "Space"; case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_KP_ENTER   -> "Num Enter";
            case GLFW.GLFW_KEY_TAB        -> "Tab";  case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            case GLFW.GLFW_KEY_ESCAPE     -> "Esc";  case GLFW.GLFW_KEY_BACKSPACE -> "Bksp";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print"; case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_NUM_LOCK   -> "Num Lock"; case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll";
            case GLFW.GLFW_KEY_LEFT_SHIFT,  GLFW.GLFW_KEY_RIGHT_SHIFT   -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT,    GLFW.GLFW_KEY_RIGHT_ALT     -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER,  GLFW.GLFW_KEY_RIGHT_SUPER   -> "Super";
            case GLFW.GLFW_KEY_KP_0 -> "Num0"; case GLFW.GLFW_KEY_KP_1 -> "Num1";
            case GLFW.GLFW_KEY_KP_2 -> "Num2"; case GLFW.GLFW_KEY_KP_3 -> "Num3";
            case GLFW.GLFW_KEY_KP_4 -> "Num4"; case GLFW.GLFW_KEY_KP_5 -> "Num5";
            case GLFW.GLFW_KEY_KP_6 -> "Num6"; case GLFW.GLFW_KEY_KP_7 -> "Num7";
            case GLFW.GLFW_KEY_KP_8 -> "Num8"; case GLFW.GLFW_KEY_KP_9 -> "Num9";
            case GLFW.GLFW_KEY_KP_ADD      -> "Num+"; case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num-";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num*"; case GLFW.GLFW_KEY_KP_DIVIDE   -> "Num/";
            case GLFW.GLFW_KEY_KP_DECIMAL  -> "Num.";
            default -> "Key" + glfwKey;
        };
    }
}
