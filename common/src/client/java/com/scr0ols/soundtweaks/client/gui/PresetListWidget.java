package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.layout.PresetRowLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Preset list (left panel of {@link PresetsScreen}). */
class PresetListWidget extends AbstractSelectionList<PresetListWidget.PresetRow> {

    private final PresetsScreen screen;

    PresetListWidget(PresetsScreen screen, Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
        this.screen = screen;
    }

    void refresh() {
        this.clearEntries();
        for (PresetConfig.Preset preset : PresetConfig.getPresets())
            this.addEntry(new PresetRow(preset));
    }

    @Override public int getRowWidth() { return this.width - 20; }
    @Override protected int scrollBarX() { return this.getX() + this.width - 6; }
    @Override public void updateWidgetNarration(NarrationElementOutput o) {}

    class PresetRow extends AbstractSelectionList.Entry<PresetRow> {

        private final PresetConfig.Preset preset;
        PresetRow(PresetConfig.Preset preset) { this.preset = preset; }

        private int rowW() { return PresetListWidget.this.getRowWidth(); }

        private PresetRowLayout rowLayout() {
            Font font = PresetListWidget.this.minecraft.font;
            String sc = PresetKeyNames.displayLabel(preset);
            int scW = sc.equals(PresetKeyNames.NONE) ? 0 : font.width(" [" + sc + "]");
            return PresetRowLayout.compute(getX(), getY(), rowW(), font.width(preset.name), scW);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = PresetListWidget.this.minecraft.font;
            boolean active   = PresetConfig.isActive(preset.id);
            boolean fav      = PresetConfig.isFavorite(preset.id);
            boolean selected = (screen.editingPreset() == preset);
            int rW = rowW(), pc = preset.argbColor();
            PresetRowLayout l = rowLayout();

            int rowBg = selected ? 0xFF383838 : hovered ? 0xFF282828 : 0xFF222222;
            g.fill(getX(), getY(), getX() + rW, getY() + PresetRowLayout.ROW_H, rowBg);
            g.fill(getX(), getY() + 23, getX() + rW, getY() + PresetRowLayout.ROW_H, 0xFF111111);
            g.fill(getX(), getY(), getX() + (selected || active ? 6 : 4), getY() + 23, pc | 0xFF000000);

            Rect b = l.badge();
            PowerIcon.draw(g, b.x() + (b.w() - PowerIcon.SIZE) / 2, b.y() + (b.h() - PowerIcon.SIZE) / 2 + 1,
                    active ? 0xFF55FF55 : 0xFF666666);

            int nameCol = selected ? 0xFFFFFFFF : active ? 0xFFDDDDDD : 0xFF999999;
            String shown = GuiText.ellipsize(font, preset.name, l.nameMaxW());
            g.text(font, shown, l.nameX(), getY() + 8, nameCol);
            if (l.hasShortcut())
                g.text(font, " [" + PresetKeyNames.displayLabel(preset) + "]", l.shortcutX(), getY() + 8, 0xFF556655);

            Rect s = l.star();
            boolean hovStar = s.contains(mouseX, mouseY);
            // Favourite button: filled with preset colour (no inner margin)
            g.fill(s.x() - 1, s.y() - 1, s.right() + 1, s.bottom() + 1, fav ? 0xFFFFFFFF : 0xFF111111); // borda
            g.fill(s.x(), s.y(), s.right(), s.bottom(), fav ? pc | 0xFF000000 : hovStar ? 0xFF4A4A4A : 0xFF2A2A2A);

            if (hovStar) {
                g.setTooltipForNextFrame(Component.translatable(
                        fav ? "soundtweaks.presets.favourite_remove" : "soundtweaks.presets.favourite_add"), mouseX, mouseY);
            } else if (hovered && !shown.equals(preset.name) && mouseX >= l.nameX() && mouseX < l.nameX() + l.nameMaxW()) {
                g.setTooltipForNextFrame(Component.literal(preset.name), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
            if (consumed) return false;
            if (screen.isCreating()) return false;
            int mx = (int) event.x(), my = (int) event.y();
            PresetRowLayout l = rowLayout();

            if (l.star().contains(mx, my)) {
                PresetConfig.setFavorite(preset.id, !PresetConfig.isFavorite(preset.id)); return true;
            }
            if (l.badge().contains(mx, my)) {
                PresetConfig.setActive(preset.id, !PresetConfig.isActive(preset.id)); return true;
            }
            if (screen.editingPreset() == preset && !screen.isListStep()) {
                PresetListWidget.this.setSelected(null);
                screen.closeDetailPanel();
            } else {
                PresetListWidget.this.setSelected(this);
                screen.openEditOverlay(preset);
            }
            return true;
        }

        @Override public boolean mouseDragged(MouseButtonEvent e, double dX, double dY) { return false; }
        @Override public boolean mouseReleased(MouseButtonEvent e) { return false; }
        public void updateNarration(NarrationElementOutput o) {}
    }
}
