package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

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

        private int rowW()  { return PresetListWidget.this.getRowWidth(); }
        private int starX() { return getX() + rowW() - 22; }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            boolean active   = PresetConfig.isActive(preset.id);
            boolean fav      = PresetConfig.isFavorite(preset.id);
            boolean selected = (screen.editingPreset() == preset);
            int rW = rowW(), pc = preset.argbColor();

            int rowBg = selected ? 0xFF383838 : hovered ? 0xFF282828 : 0xFF222222;
            g.fill(getX(), getY(), getX() + rW, getY() + 24, rowBg);
            g.fill(getX(), getY() + 23, getX() + rW, getY() + 24, 0xFF111111);
            g.fill(getX(), getY(), getX() + (selected || active ? 6 : 4), getY() + 23, pc | 0xFF000000);

            int badgeX = getX() + 10, badgeY = getY() + 6;
            g.fill(badgeX-1, badgeY-1, badgeX+23, badgeY+12, active ? 0xFF336633 : 0xFF444444);
            g.fill(badgeX, badgeY, badgeX+22, badgeY+11, active ? 0xFF1A3A1A : 0xFF2A2A2A);
            g.centeredText(PresetListWidget.this.minecraft.font, active ? "ON" : "OFF", badgeX+11, badgeY+2, active ? 0xFF55FF55 : 0xFF888888);

            int nameCol = selected ? 0xFFFFFFFF : active ? 0xFFDDDDDD : 0xFF999999;
            g.text(PresetListWidget.this.minecraft.font, preset.name, getX()+38, getY()+8, nameCol);
            String sc = PresetKeyNames.displayLabel(preset);
            if (!sc.equals(PresetKeyNames.NONE))
                g.text(PresetListWidget.this.minecraft.font, " [" + sc + "]",
                        getX()+38+PresetListWidget.this.minecraft.font.width(preset.name), getY()+8, 0xFF556655);

            int sx = starX();
            boolean hovStar = mouseX >= sx && mouseX < sx+18 && mouseY >= getY()+4 && mouseY < getY()+20;
            // Favourite button: filled with preset colour (no inner margin)
            g.fill(sx-1, getY()+3, sx+19, getY()+21, fav ? 0xFFFFFFFF : 0xFF111111); // borda
            if (fav) {
                g.fill(sx, getY()+4, sx+18, getY()+20, pc | 0xFF000000);
            } else {
                g.fill(sx, getY()+4, sx+18, getY()+20, hovStar ? 0xFF4A4A4A : 0xFF2A2A2A);
            }
            // Hover label
            if (hovStar) {
                String tip = fav ? "Remove favourite" : "Add to favourites";
                g.text(PresetListWidget.this.minecraft.font, tip,
                       sx - PresetListWidget.this.minecraft.font.width(tip) - 4, getY() + 8, 0xFFAAAAAA);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
            if (consumed) return false;
            if (screen.isCreating()) return false;
            double mx = event.x(), my = event.y();

            int sx = starX();
            if (mx >= sx && mx < sx+18 && my >= getY()+4 && my < getY()+20) {
                PresetConfig.setFavorite(preset.id, !PresetConfig.isFavorite(preset.id)); return true;
            }
            int badgeX = getX()+10, badgeY = getY()+6;
            if (mx >= badgeX && mx < badgeX+22 && my >= badgeY && my < badgeY+11) {
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
