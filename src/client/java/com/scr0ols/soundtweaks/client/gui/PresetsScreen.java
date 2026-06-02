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
 * Ecrã de gestão de presets: listar, criar, apagar, marcar favorito.
 * Edição de cor/nome/atalho/sons via overlay editMode.
 */
public class PresetsScreen extends Screen {

    private final Screen parent;
    private PresetListWidget presetList;

    // ── Overlay criar preset ──────────────────────────────────────────────────
    private boolean creating = false;
    private EditBox createBox;
    private Button  createConfirmBtn, createCancelBtn;

    // ── Edit overlay ──────────────────────────────────────────────────────────
    private enum EditMode { NONE, COLOR, RENAME, SHORTCUT }
    private EditMode editMode = EditMode.NONE;
    @Nullable private PresetConfig.Preset editingPreset = null;

    // Widgets de renomeação (usados quando editMode == RENAME)
    private EditBox renameBox;
    private Button  renameConfirmBtn, renameCancelBtn;

    // Widget de cor hex (usado quando editMode == COLOR e slot custom selecionado)
    private EditBox colorHexBox;

    // Estado de captura de atalho — tracking em tempo real (malilib-style)
    // Teclas atualmente pressionadas durante captura
    private final LinkedHashSet<Integer> captureHeldKeys   = new LinkedHashSet<>();
    // Teclas que estavam held quando o trigger foi premido (última combo registada)
    private final List<Integer>          lastHeldAtTrigger = new ArrayList<>();
    private int lastCapturedTrigger = 0;

    // ── Layout: create overlay ────────────────────────────────────────────────
    private int createOvX() { return this.width / 2 - 130; }
    private int createOvY() { return this.height / 2 - 22; }

    // ── Layout: edit overlay ─────────────────────────────────────────────────
    private static final int EDIT_W = 320;
    private static final int EDIT_H = 220;
    private int editPanelX() { return this.width  / 2 - EDIT_W / 2; }
    private int editPanelY() { return this.height / 2 - EDIT_H / 2; }

    // Larguras dos 4 tabs (Color, Rename, Shortcut, Edit Sounds)
    private static final int[] TAB_W = {68, 68, 76, 80};

    // ── Construtor ────────────────────────────────────────────────────────────

    public PresetsScreen(Screen parent) {
        super(Component.translatable("soundtweaks.presets.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop    = 28;
        int listBottom = this.height - 76; // espaço para 2 linhas de botões no footer
        this.presetList = new PresetListWidget(this.minecraft, this.width,
                listBottom - listTop, listTop, 28);
        this.addRenderableWidget(this.presetList);
        this.presetList.refresh();

        // ── Footer: linha 1 — New Preset | Done ──────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.presets.new"),
                btn -> enterCreateMode()
        ).bounds(this.width / 2 - 125, this.height - 50, 120, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.gui.done"),
                btn -> this.onClose()
        ).bounds(this.width / 2 + 5, this.height - 50, 120, 20).build());

        // ── Footer: linha 2 — Import Presets | Open Config Folder ────────────
        var importPresetsBtn = Button.builder(
                Component.literal("Import Presets..."),
                btn -> {
                    String selected;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer filters = stack.mallocPointer(1);
                        filters.put(stack.UTF8("*.json")).flip();
                        selected = TinyFileDialogs.tinyfd_openFileDialog(
                                "Select soundtweaks_presets.json",
                                "",
                                filters,
                                "JSON preset file (*.json)",
                                false);
                    }
                    if (selected == null) return;
                    int result = PresetConfig.importFrom(java.nio.file.Path.of(selected));
                    if (result >= 0) presetList.refresh();
                }
        ).bounds(this.width / 2 - 125, this.height - 26, 120, 18).build();
        importPresetsBtn.setTooltip(Tooltip.create(Component.literal(
                "Import presets from a share file.\n" +
                "Format: [{name, colorIndex, sounds, blocks}]\n" +
                "A new ID is always generated — no conflicts.")));
        this.addRenderableWidget(importPresetsBtn);

        var openFolderBtn = Button.builder(
                Component.literal("Open Config Folder"),
                btn -> ConfigFileUtil.openConfigFolder()
        ).bounds(this.width / 2 + 5, this.height - 26, 120, 18).build();
        openFolderBtn.setTooltip(Tooltip.create(Component.literal(
                "Opens the config folder in your file explorer.\n" +
                "Copy soundtweaks*.json files between instances\n" +
                "to share your configuration.")));
        this.addRenderableWidget(openFolderBtn);

        // Create overlay widgets
        int cx = createOvX(), cy = createOvY();
        this.createBox = new EditBox(this.font, cx, cy, 220, 20, Component.empty());
        this.createBox.setHint(Component.translatable("soundtweaks.presets.name_hint"));
        this.createBox.setMaxLength(64);
        this.createBox.visible = false;
        this.addRenderableWidget(this.createBox);

        this.createConfirmBtn = Button.builder(Component.translatable("soundtweaks.presets.confirm"),
                btn -> confirmCreate()).bounds(cx, cy + 24, 106, 18).build();
        this.createConfirmBtn.visible = false;
        this.addRenderableWidget(this.createConfirmBtn);

        this.createCancelBtn = Button.builder(Component.literal("Cancel"),
                btn -> exitCreateMode()).bounds(cx + 110, cy + 24, 106, 18).build();
        this.createCancelBtn.visible = false;
        this.addRenderableWidget(this.createCancelBtn);

        // Rename widgets (usados dentro do edit overlay, modo RENAME)
        int px = editPanelX(), py = editPanelY();
        // renameBox: centrado horizontalmente no painel, a 74px do topo do painel
        int renX = px + 40, renY = py + 74;
        this.renameBox = new EditBox(this.font, renX, renY, EDIT_W - 80, 20, Component.empty());
        this.renameBox.setMaxLength(64);
        this.renameBox.visible = false;
        this.addRenderableWidget(this.renameBox);

        this.renameConfirmBtn = Button.builder(Component.literal("Save name"),
                btn -> confirmRename()).bounds(renX, renY + 26, 115, 18).build();
        this.renameConfirmBtn.visible = false;
        this.addRenderableWidget(this.renameConfirmBtn);

        this.renameCancelBtn = Button.builder(Component.literal("Clear"),
                btn -> { renameBox.setValue(""); this.setFocused(renameBox); renameBox.setFocused(true); }
        ).bounds(renX + 125, renY + 26, 115, 18).build();
        this.renameCancelBtn.visible = false;
        this.addRenderableWidget(this.renameCancelBtn);

        // Color hex EditBox — aparece abaixo da grelha de cores quando o slot custom está seleccionado
        // Posição: alinhada com o quadrado custom (fila 4 da grelha, mesmo X que a grelha + 28px)
        int cgridW  = 6 * 22 + 5 * 3; // 147px
        int cgridX  = px + EDIT_W / 2 - cgridW / 2;
        int cgridY  = py + 74; // contentY (py+54) + 20
        int customY = cgridY + 3 * (22 + 3) + 12; // fila custom: 12px extra abaixo da grelha
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Esconder todos os widgets de overlay antes de super os renderizar
        setCreateWidgetsVisible(false);
        setRenameWidgetsVisible(false);
        colorHexBox.visible = false;

        super.extractRenderState(graphics, mouseX, mouseY, a);

        // Restaurar apenas os que precisam
        if (creating) setCreateWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.RENAME) setRenameWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.COLOR) {
            if (editingPreset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX)
                colorHexBox.visible = true;
        }

        graphics.centeredText(this.font,
                I18n.get("soundtweaks.presets.title"), this.width / 2, 10, 0xFFFFFFFF);
        graphics.fill(8, this.height - 58, this.width - 8, this.height - 57, 0xFF555555);

        // Overlay criar (prioridade sobre edit)
        if (creating) {
            graphics.fill(0, 0, this.width, this.height, 0xBB000000);
            int cx = createOvX(), cy = createOvY();
            graphics.fill(cx - 10, cy - 26, cx + 232, cy + 46, 0xFF222233);
            graphics.fill(cx - 10, cy - 26, cx + 232, cy - 25, 0xFF444466);
            graphics.text(this.font, "New preset name:", cx, cy - 18, 0xFFCCCCFF);
            createBox.extractRenderState(graphics, mouseX, mouseY, a);
            createConfirmBtn.extractRenderState(graphics, mouseX, mouseY, a);
            createCancelBtn.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }

        // Edit overlay
        if (editingPreset != null) {
            renderEditOverlay(graphics, mouseX, mouseY, a);
        }
    }

    private void renderEditOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        PresetConfig.Preset preset = editingPreset;
        if (preset == null) { closeEditOverlay(); return; }

        g.fill(0, 0, this.width, this.height, 0xBB000000);

        int px = editPanelX(), py = editPanelY();

        // Painel
        g.fill(px, py, px + EDIT_W, py + EDIT_H, 0xFF1A1A2E);
        g.fill(px,              py,              px + EDIT_W, py + 1,              0xFF444466);
        g.fill(px,              py + EDIT_H - 1, px + EDIT_W, py + EDIT_H,         0xFF444466);
        g.fill(px,              py,              px + 1,       py + EDIT_H,         0xFF444466);
        g.fill(px + EDIT_W - 1, py,              px + EDIT_W, py + EDIT_H,         0xFF444466);

        // Título
        g.centeredText(this.font, "Editing: " + preset.name,
                px + EDIT_W / 2, py + 10, 0xFFCCCCFF);
        g.fill(px + 8, py + 22, px + EDIT_W - 8, py + 23, 0xFF333355);

        // Tabs
        renderTabs(g, mouseX, mouseY, px, py);

        // Separador após tabs
        int tabBottom = py + 28 + 18;
        g.fill(px + 8, tabBottom + 2, px + EDIT_W - 8, tabBottom + 3, 0xFF333355);

        int contentY = tabBottom + 8; // py + 56

        // Conteúdo do modo activo
        switch (editMode) {
            case COLOR    -> renderColorContent(g, mouseX, mouseY, px, contentY, preset, a);
            case RENAME   -> renderRenameContent(g, mouseX, mouseY, px, contentY, a);
            case SHORTCUT -> renderShortcutContent(g, px, contentY);
            default       -> {}
        }

        // Botão Done
        int doneX = px + EDIT_W - 76, doneY = py + EDIT_H - 26;
        boolean hovDone = mouseX >= doneX && mouseX < doneX + 68
                && mouseY >= doneY && mouseY < doneY + 18;
        g.fill(doneX, doneY, doneX + 68, doneY + 18, hovDone ? 0xFF335533 : 0xFF223322);
        g.centeredText(this.font, "Done", doneX + 34, doneY + 5, 0xFF88FF88);
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, int px, int py) {
        String[] labels = {"Color", "Rename", "Shortcut", "Edit Sounds"};
        EditMode[] modes = {EditMode.COLOR, EditMode.RENAME, EditMode.SHORTCUT, null};
        int tabX = px + 8, tabY = py + 28;

        for (int i = 0; i < 4; i++) {
            boolean active = (editMode == modes[i] && modes[i] != null);
            boolean hov    = mouseX >= tabX && mouseX < tabX + TAB_W[i]
                    && mouseY >= tabY && mouseY < tabY + 18;

            int bg = active ? 0xFF334466 : (hov ? 0xFF2A2A44 : 0xFF222233);
            g.fill(tabX, tabY, tabX + TAB_W[i], tabY + 18, bg);
            // Linha de destaque no topo quando activo
            g.fill(tabX, tabY, tabX + TAB_W[i], tabY + 1,
                    active ? 0xFF8888FF : 0xFF444466);

            int textCol = active ? 0xFFFFFFFF : (hov ? 0xFFCCCCCC : 0xFF888899);
            g.centeredText(this.font, labels[i], tabX + TAB_W[i] / 2, tabY + 5, textCol);
            tabX += TAB_W[i] + 4;
        }
    }

    private void renderColorContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                    int px, int contentY, PresetConfig.Preset preset, float a) {
        int sq = 22, gap = 3, cols = 6;
        int gridW = cols * sq + (cols - 1) * gap;
        int gridX = px + EDIT_W / 2 - gridW / 2;
        int gridY = contentY + 20;

        // ── 18 cores predefinidas ─────────────────────────────────────────────
        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % cols, row = i / cols;
            int qx = gridX + col * (sq + gap);
            int qy = gridY + row * (sq + gap);
            g.fill(qx, qy, qx + sq, qy + sq, PresetConfig.PRESET_COLORS[i] | 0xFF000000);

            boolean selected = (i == preset.colorIndex);
            boolean hov = mouseX >= qx && mouseX < qx + sq && mouseY >= qy && mouseY < qy + sq;

            if (selected) {
                // Borda branca 2px para a cor seleccionada
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

        // ── Slot de cor personalizada (linha 4, com espaço extra) ────────────
        int customY = gridY + 3 * (sq + gap) + 12;
        boolean customSel = (preset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX);
        boolean customHov = mouseX >= gridX && mouseX < gridX + sq
                && mouseY >= customY && mouseY < customY + sq;

        if (preset.customColor != 0) {
            g.fill(gridX, customY, gridX + sq, customY + sq, preset.customColor | 0xFF000000);
        } else {
            // Quadrado vazio: fundo escuro + borda subtil
            g.fill(gridX, customY, gridX + sq, customY + sq, 0xFF1A1A2E);
            g.fill(gridX,          customY,      gridX + sq, customY + 1,  0xFF556677);
            g.fill(gridX,          customY + sq - 1, gridX + sq, customY + sq, 0xFF556677);
            g.fill(gridX,          customY,      gridX + 1,  customY + sq, 0xFF556677);
            g.fill(gridX + sq - 1, customY,      gridX + sq, customY + sq, 0xFF556677);
            if (!customSel)
                g.centeredText(this.font, "+", gridX + sq / 2, customY + (sq - 8) / 2, 0xFF556677);
        }

        if (customSel) {
            // Borda branca 2px (igual às cores predefinidas)
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

        // EditBox hex — apenas quando custom selecionado
        if (customSel) {
            colorHexBox.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    private void renderRenameContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                     int px, int contentY, float a) {
        // EditBox e botões são widgets — renderizados manualmente aqui por cima de tudo
        renameBox.extractRenderState(g, mouseX, mouseY, a);
        renameConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
        renameCancelBtn.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderShortcutContent(GuiGraphicsExtractor g, int px, int contentY) {
        int cx2 = px + EDIT_W / 2;

        // Valor actualmente a ser capturado (o que o utilizador está a pressionar agora)
        String captureLabel;
        if (lastCapturedTrigger != 0) {
            StringBuilder sb = new StringBuilder();
            for (int k : lastHeldAtTrigger) sb.append(rawKeyName(k)).append(" + ");
            sb.append(rawKeyName(lastCapturedTrigger));
            captureLabel = sb.toString();
        } else {
            captureLabel = "---";
        }
        g.centeredText(this.font, captureLabel, cx2, contentY + 14,
                lastCapturedTrigger != 0 ? 0xFF88FF88 : 0xFF555555);

        // Valor actualmente guardado no preset
        String savedLabel = (editingPreset != null) ? keyDisplayLabel(editingPreset) : "---";
        boolean hasSaved  = !savedLabel.equals("---");
        String savedLine  = hasSaved ? "[" + savedLabel + "]" : "[blank]";
        g.centeredText(this.font, savedLine, cx2, contentY + 34,
                hasSaved ? 0xFFAABBAA : 0xFF666666);

        g.centeredText(this.font, "ENTER to confirm  ·  BACKSPACE to clear  ·  ESC to cancel",
                cx2, contentY + 50, 0xFF777777);
    }

    // ── Visibilidade de widgets de overlay ────────────────────────────────────

    private void setCreateWidgetsVisible(boolean v) {
        createBox.visible = v;
        createConfirmBtn.visible = v;
        createCancelBtn.visible  = v;
    }

    private void setRenameWidgetsVisible(boolean v) {
        renameBox.visible = v;
        renameConfirmBtn.visible = v;
        renameCancelBtn.visible  = v;
    }

    // ── Teclado ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // Edit overlay activo
        if (editingPreset != null) {
            if (editMode == EditMode.SHORTCUT) {
                handleShortcutKey(key);
                return true;
            }
            if (editMode == EditMode.RENAME) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    confirmRename(); return true;
                }
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    closeEditOverlay(); return true;
                }
                return super.keyPressed(event);
            }
            if (editMode == EditMode.COLOR && this.getFocused() == colorHexBox) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                        || key == GLFW.GLFW_KEY_ESCAPE) {
                    this.setFocused(null); return true;
                }
                return super.keyPressed(event);
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { closeEditOverlay(); return true; }
            return true;
        }

        // Create overlay activo
        if (creating) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmCreate(); return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { exitCreateMode(); return true; }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (editingPreset != null && editMode == EditMode.SHORTCUT) {
            captureHeldKeys.remove(event.key());
            return true;
        }
        return super.keyReleased(event);
    }

    private void handleShortcutKey(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            resetShortcutCapture();
            closeEditOverlay();
            return;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            resetShortcutCapture();
            if (editingPreset != null) {
                editingPreset.shortcutKey      = 0;
                editingPreset.shortcutHeldKey  = 0;
                editingPreset.shortcutHeldKey2 = 0;
                PresetConfig.markDirty();
            }
            presetList.refresh();
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (lastCapturedTrigger != 0) confirmShortcut();
            return;
        }

        // Guardar as teclas held ANTES de adicionar esta (= held keys da combo)
        lastHeldAtTrigger.clear();
        lastHeldAtTrigger.addAll(captureHeldKeys);
        // Máximo de 2 held keys (combo máxima = held1 + held2 + trigger)
        while (lastHeldAtTrigger.size() > 2) lastHeldAtTrigger.remove(0);

        lastCapturedTrigger = key;
        captureHeldKeys.add(key);
    }

    private void confirmShortcut() {
        if (editingPreset == null) return;
        int h1 = 0, h2 = 0;
        if (lastHeldAtTrigger.size() == 1) {
            h1 = lastHeldAtTrigger.get(0);
        } else if (lastHeldAtTrigger.size() >= 2) {
            h1 = lastHeldAtTrigger.get(lastHeldAtTrigger.size() - 2);
            h2 = lastHeldAtTrigger.get(lastHeldAtTrigger.size() - 1);
        }
        editingPreset.shortcutKey      = lastCapturedTrigger & 0xFFFF;
        editingPreset.shortcutHeldKey  = h1;
        editingPreset.shortcutHeldKey2 = h2;
        PresetConfig.markDirty();
        resetShortcutCapture();
        presetList.refresh();
    }

    private void resetShortcutCapture() {
        captureHeldKeys.clear();
        lastHeldAtTrigger.clear();
        lastCapturedTrigger = 0;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mx = event.x(), my = event.y();

        // Edit overlay — intercepção total
        if (editingPreset != null) {
            int px = editPanelX(), py = editPanelY();

            // Botão Done
            int doneX = px + EDIT_W - 76, doneY = py + EDIT_H - 26;
            if (mx >= doneX && mx < doneX + 68 && my >= doneY && my < doneY + 18) {
                closeEditOverlay(); return true;
            }

            // Tabs
            int tabX = px + 8, tabY = py + 28;
            for (int i = 0; i < 4; i++) {
                if (mx >= tabX && mx < tabX + TAB_W[i] && my >= tabY && my < tabY + 18) {
                    handleTabClick(i); return true;
                }
                tabX += TAB_W[i] + 4;
            }

            // Conteúdo por modo
            if (editMode == EditMode.COLOR) {
                // Clicar na EditBox hex
                if (colorHexBox.visible
                        && mx >= colorHexBox.getX() && mx < colorHexBox.getX() + colorHexBox.getWidth()
                        && my >= colorHexBox.getY() && my < colorHexBox.getY() + colorHexBox.getHeight()) {
                    this.setFocused(colorHexBox);
                    colorHexBox.setFocused(true);
                    colorHexBox.mouseClicked(event, false);
                    return true;
                }
                handleColorGridClick(mx, my, px, py, editingPreset);
            } else if (editMode == EditMode.RENAME) {
                if (renameConfirmBtn.mouseClicked(event, false)) return true;
                if (renameCancelBtn.mouseClicked(event, false))  return true;
                if (mx >= renameBox.getX() && mx < renameBox.getX() + renameBox.getWidth()
                        && my >= renameBox.getY() && my < renameBox.getY() + renameBox.getHeight()) {
                    this.setFocused(renameBox);
                    renameBox.setFocused(true);
                    renameBox.mouseClicked(event, false);
                }
            }
            return true; // absorver todos os cliques quando overlay activo
        }

        // Create overlay
        if (creating) {
            if (createConfirmBtn.mouseClicked(event, consumed)) return true;
            if (createCancelBtn.mouseClicked(event, consumed))  return true;
            if (mx >= createBox.getX() && mx < createBox.getX() + createBox.getWidth()
                    && my >= createBox.getY() && my < createBox.getY() + createBox.getHeight()) {
                this.setFocused(createBox);
                createBox.setFocused(true);
                createBox.mouseClicked(event, consumed);
            }
            return true;
        }

        return super.mouseClicked(event, consumed);
    }

    private void handleTabClick(int tabIndex) {
        switch (tabIndex) {
            case 0 -> setEditMode(EditMode.COLOR);
            case 1 -> setEditMode(EditMode.RENAME);
            case 2 -> setEditMode(EditMode.SHORTCUT);
            case 3 -> {
                if (editingPreset != null)
                    this.minecraft.setScreen(new PresetEditorScreen(this, editingPreset));
            }
        }
    }

    private void handleColorGridClick(double mx, double my, int px, int py, PresetConfig.Preset preset) {
        if (preset == null) return;

        int tabBottom = py + 28 + 18;
        int contentY  = tabBottom + 8;
        int sq = 22, gap = 3, cols = 6;
        int gridW = cols * sq + (cols - 1) * gap;
        int gridX = px + EDIT_W / 2 - gridW / 2;
        int gridY = contentY + 20;

        // 18 cores predefinidas
        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % cols, row = i / cols;
            int qx = gridX + col * (sq + gap);
            int qy = gridY + row * (sq + gap);
            if (mx >= qx && mx < qx + sq && my >= qy && my < qy + sq) {
                preset.colorIndex = i;
                PresetConfig.markDirty();
                this.setFocused(null); // desfocar hex box
                return;
            }
        }

        // Slot custom (linha 4, com espaço extra)
        int customY = gridY + 3 * (sq + gap) + 12;
        if (mx >= gridX && mx < gridX + sq && my >= customY && my < customY + sq) {
            preset.colorIndex = PresetConfig.CUSTOM_COLOR_INDEX;
            // Garantir que há sempre uma cor definida (cinzento por defeito)
            if (preset.customColor == 0) {
                preset.customColor = 0xFF888888;
            }
            PresetConfig.markDirty();
            colorHexBox.setValue(String.format("%06X", preset.customColor & 0xFFFFFF));
            colorHexBox.visible = true;
            this.setFocused(colorHexBox);
            colorHexBox.setFocused(true);
        }
    }

    // ── Edit overlay: abrir / fechar / trocar modo ────────────────────────────

    void openEditOverlay(PresetConfig.Preset preset) {
        this.editingPreset = preset;
        setEditMode(EditMode.COLOR);
    }

    private void setEditMode(EditMode mode) {
        this.editMode = mode;
        setRenameWidgetsVisible(false);

        if (mode == EditMode.RENAME) {
            if (editingPreset != null) {
                renameBox.setValue(editingPreset.name);
                setRenameWidgetsVisible(true);
                this.setFocused(renameBox);
                renameBox.setFocused(true);
            }
        } else {
            this.setFocused(null);
        }

        if (mode == EditMode.SHORTCUT) {
            resetShortcutCapture();
        }
    }

    private void closeEditOverlay() {
        this.editingPreset = null;
        this.editMode      = EditMode.NONE;
        setRenameWidgetsVisible(false);
        this.setFocused(null);
        presetList.refresh();
    }

    // ── Criar preset ──────────────────────────────────────────────────────────

    private void enterCreateMode() {
        creating = true;
        createBox.setValue("");
        setCreateWidgetsVisible(true);
        this.setFocused(createBox);
        createBox.setFocused(true);
    }

    private void confirmCreate() {
        String name = createBox.getValue().trim();
        if (!name.isEmpty()) {
            PresetConfig.createFromCurrentConfig(name);
            presetList.refresh();
        }
        exitCreateMode();
    }

    private void exitCreateMode() {
        creating = false;
        setCreateWidgetsVisible(false);
        this.setFocused(null);
    }

    // ── Renomear (dentro do edit overlay) ────────────────────────────────────

    private void confirmRename() {
        if (editingPreset != null) {
            String name = renameBox.getValue().trim();
            if (!name.isEmpty()) {
                PresetConfig.renamePreset(editingPreset.name, name);
                // editingPreset.name foi actualizado em PresetConfig.renamePreset()
                presetList.refresh();
            }
        }
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    // =========================================================================
    // Lista de presets
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

        @Override public int getRowWidth() { return Math.min(500, this.width - 20); }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.width / 2 + getRowWidth() / 2 + 4;
        }

        @Override public void updateWidgetNarration(NarrationElementOutput o) {}

        // ── Linha de preset ──────────────────────────────────────────────────
        // [ON/OFF zona toggle] [nome / selecção] ......... [★] [Edit] [X]

        class PresetRow extends AbstractSelectionList.Entry<PresetRow> {

            private final PresetConfig.Preset preset;

            PresetRow(PresetConfig.Preset preset) { this.preset = preset; }

            private int rowW()    { return PresetListWidget.this.getRowWidth(); }
            private int delX()    { return getX() + rowW() - 16; }   // [X] 14px
            private int editBtnX(){ return delX() - 56; }             // [Edit ▼] 52px, 4px gap
            private int starX()   { return editBtnX() - 18; }         // [★] 14px, 4px gap

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                       boolean hovered, float a) {
                boolean active = PresetConfig.isActive(preset.name);
                boolean fav    = PresetConfig.isFavorite(preset.name);
                int rW = rowW();
                int pc = preset.argbColor();

                // Fundo
                if (active) {
                    g.fill(getX(), getY(), getX() + rW, getY() + 28,
                            (pc & 0x00FFFFFF) | 0x88000000);
                    g.fill(getX(), getY(), getX() + 4, getY() + 28, pc | 0xFF000000);
                } else if (hovered) {
                    g.fill(getX(), getY(), getX() + rW, getY() + 28, 0x22FFFFFF);
                }

                // ON/OFF
                g.text(PresetListWidget.this.minecraft.font,
                        active ? "ON " : "OFF",
                        getX() + 8, getY() + 10, active ? 0xFF44FF44 : 0xFF888888);

                // Cor indicator (pequeno quadrado colorido)
                int sq = 10;
                int sqX = getX() + 46, sqY = getY() + 9;
                g.fill(sqX, sqY, sqX + sq, sqY + sq, pc | 0xFF000000);

                // Nome
                g.text(PresetListWidget.this.minecraft.font, preset.name,
                        getX() + 62, getY() + 10, 0xFFFFFFFF);

                // Atalho (se existir, texto pequeno à direita do nome)
                String shortcutLabel = keyDisplayLabel(preset);
                if (!shortcutLabel.equals("---")) {
                    g.text(PresetListWidget.this.minecraft.font,
                            "  [" + shortcutLabel + "]",
                            getX() + 62 + PresetListWidget.this.minecraft.font.width(preset.name),
                            getY() + 10, 0xFF66AA66);
                }

                // ── [★] favorito ────────────────────────────────────────────
                int sx  = starX();
                boolean hovStar = mouseX >= sx && mouseX < sx + 14
                        && mouseY >= getY() + 7 && mouseY < getY() + 21;
                g.fill(sx, getY() + 7, sx + 14, getY() + 21,
                        hovStar ? 0xFF555522 : 0xFF333311);
                g.centeredText(PresetListWidget.this.minecraft.font,
                        fav ? "★" : "☆",
                        sx + 7, getY() + 10, fav ? 0xFFFFDD44 : 0xFF666655);

                // ── [Edit ▼] ─────────────────────────────────────────────────
                int ex  = editBtnX();
                boolean hovEdit = mouseX >= ex && mouseX < ex + 52
                        && mouseY >= getY() + 7 && mouseY < getY() + 21;
                g.fill(ex, getY() + 7, ex + 52, getY() + 21,
                        hovEdit ? 0xFF445588 : 0xFF333355);
                g.centeredText(PresetListWidget.this.minecraft.font, "Edit",
                        ex + 26, getY() + 10, 0xFFAAAAFF);

                // ── [X] apagar ───────────────────────────────────────────────
                int dx  = delX();
                boolean hovDel = mouseX >= dx && mouseX < dx + 14
                        && mouseY >= getY() + 7 && mouseY < getY() + 21;
                g.fill(dx, getY() + 7, dx + 14, getY() + 21,
                        hovDel ? 0xFF885533 : 0xFF553322);
                g.centeredText(PresetListWidget.this.minecraft.font, "X",
                        dx + 7, getY() + 10, 0xFFFF6666);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
                if (consumed) return false;
                if (PresetsScreen.this.creating || PresetsScreen.this.editingPreset != null)
                    return false;

                PresetListWidget.this.setSelected(this);
                double mx = event.x(), my = event.y();

                // [X] apagar
                int dx = delX();
                if (mx >= dx && mx < dx + 14 && my >= getY() + 7 && my < getY() + 21) {
                    PresetConfig.deletePreset(preset.name);
                    PresetListWidget.this.removeEntry(this);
                    return true;
                }

                // [Edit ▼] — abrir overlay de edição
                int ex = editBtnX();
                if (mx >= ex && mx < ex + 52 && my >= getY() + 7 && my < getY() + 21) {
                    PresetsScreen.this.openEditOverlay(preset);
                    return true;
                }

                // [★] favorito
                int sx = starX();
                if (mx >= sx && mx < sx + 14 && my >= getY() + 7 && my < getY() + 21) {
                    PresetConfig.setFavorite(preset.name, !PresetConfig.isFavorite(preset.name));
                    return true;
                }

                // Zona ON/OFF (getX() até +44) → toggle
                if (mx < getX() + 44) {
                    PresetConfig.setActive(preset.name, !PresetConfig.isActive(preset.name));
                }
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
            // 2 ou 3 teclas: held1 [+ held2] + trigger
            String s = rawKeyName(preset.shortcutHeldKey);
            if (preset.shortcutHeldKey2 != 0) s += "+" + rawKeyName(preset.shortcutHeldKey2);
            s += "+" + rawKeyName(preset.shortcutKey & 0xFFFF);
            return s;
        }
        // 1 tecla: apenas trigger (sem held keys)
        return rawKeyName(preset.shortcutKey & 0xFFFF);
    }

    static String keyName(int encoded) {
        if (encoded <= 0) return "---";
        int mods    = (encoded >> 16) & 0xFFFF;
        int glfwKey = encoded & 0xFFFF;
        String base = rawKeyName(glfwKey);
        if (mods == 0) return base;
        StringBuilder sb = new StringBuilder();
        if ((mods & 2) != 0) sb.append("Ctrl+");
        if ((mods & 1) != 0) sb.append("Shift+");
        if ((mods & 4) != 0) sb.append("Alt+");
        sb.append(base);
        return sb.toString();
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
            case GLFW.GLFW_KEY_SPACE      -> "Space";
            case GLFW.GLFW_KEY_ENTER      -> "Enter";
            case GLFW.GLFW_KEY_KP_ENTER   -> "Num Enter";
            case GLFW.GLFW_KEY_TAB        -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK  -> "Caps";
            case GLFW.GLFW_KEY_ESCAPE     -> "Esc";
            case GLFW.GLFW_KEY_BACKSPACE  -> "Bksp";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print";
            case GLFW.GLFW_KEY_PAUSE      -> "Pause";
            case GLFW.GLFW_KEY_NUM_LOCK   -> "Num Lock";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll";
            case GLFW.GLFW_KEY_LEFT_SHIFT,  GLFW.GLFW_KEY_RIGHT_SHIFT   -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT,    GLFW.GLFW_KEY_RIGHT_ALT     -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER,  GLFW.GLFW_KEY_RIGHT_SUPER   -> "Super";
            case GLFW.GLFW_KEY_KP_0 -> "Num0"; case GLFW.GLFW_KEY_KP_1 -> "Num1";
            case GLFW.GLFW_KEY_KP_2 -> "Num2"; case GLFW.GLFW_KEY_KP_3 -> "Num3";
            case GLFW.GLFW_KEY_KP_4 -> "Num4"; case GLFW.GLFW_KEY_KP_5 -> "Num5";
            case GLFW.GLFW_KEY_KP_6 -> "Num6"; case GLFW.GLFW_KEY_KP_7 -> "Num7";
            case GLFW.GLFW_KEY_KP_8 -> "Num8"; case GLFW.GLFW_KEY_KP_9 -> "Num9";
            case GLFW.GLFW_KEY_KP_ADD      -> "Num+";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num-";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num*";
            case GLFW.GLFW_KEY_KP_DIVIDE   -> "Num/";
            case GLFW.GLFW_KEY_KP_DECIMAL  -> "Num.";
            default -> "Key" + glfwKey;
        };
    }
}
