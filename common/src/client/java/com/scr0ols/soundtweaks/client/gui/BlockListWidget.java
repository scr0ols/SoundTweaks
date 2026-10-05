package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.List;

public class BlockListWidget extends AbstractSelectionList<BlockListWidget.BlockEntry> {

    public BlockListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        populate(MissingBlockRegistry.BLOCK_IDS);
    }

    public void populate(List<String> blockIds) {
        this.clearEntries();
        for (String blockId : blockIds) {
            this.addEntry(new BlockEntry(blockId));
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BlockEntry selected = this.getSelected();
        if (selected != null) {
            float current = VolumeResolver.getEffectiveBlockVolume(selected.blockId);
            float newVal = Mth.clamp(current + (float)(scrollY * 0.05), 0.0f, 1.0f);
            selected.slider.setSliderValue(newVal);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {}

    public class BlockEntry extends AbstractSelectionList.Entry<BlockEntry> {

        final String blockId;
        private final String displayName;
        final BlockSliderButton slider;
        private long lastClickTime = 0;
        private static final long DOUBLE_CLICK_MS = 300;

        public BlockEntry(String blockId) {
            this.blockId     = blockId;
            this.displayName = MissingBlockRegistry.getDisplayName(blockId);
            this.slider      = new BlockSliderButton(blockId, 0, 0, 90, 14);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float a) {
            if (BlockListWidget.this.getSelected() == this) {
                graphics.fill(getX(), getY(),
                        getX() + BlockListWidget.this.getRowWidth(), getY() + 20,
                        0x44FFFFFF);
            }

            graphics.text(BlockListWidget.this.minecraft.font, this.displayName,
                    getX() + 4, getY() + 5, 0xFFFFFFFF);

            this.slider.setX(getX() + BlockListWidget.this.getRowWidth() - 94);
            this.slider.setY(getY() + 3);
            this.slider.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
            BlockListWidget.this.setSelected(this);

            long now = Util.getMillis();
            boolean isDoubleClick = (now - this.lastClickTime) < DOUBLE_CLICK_MS;
            this.lastClickTime = now;

            if (isDoubleClick) {
                float current = VolumeResolver.getEffectiveBlockVolume(this.blockId);
                float newVal = (current >= 1.0f) ? 0.0f : 1.0f;
                this.slider.setSliderValue(newVal);
                return true;
            }

            return this.slider.mouseClicked(event, consumed);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            return this.slider.mouseDragged(event, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            return this.slider.mouseReleased(event);
        }

        public void updateNarration(NarrationElementOutput output) {}
    }
}
