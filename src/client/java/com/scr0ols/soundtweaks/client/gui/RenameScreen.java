package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class RenameScreen extends Screen {

    private final Screen parent;
    private final String presetId;
    private final String currentName;

    private EditBox nameBox;
    private boolean focusSet = false;

    public RenameScreen(Screen parent, String presetId, String currentName) {
        super(Component.literal("Rename Preset"));
        this.parent      = parent;
        this.presetId    = presetId;
        this.currentName = currentName;
    }

    @Override
    protected void init() {
        int cx = this.width / 2 - 110;
        int cy = this.height / 2 - 10;

        this.nameBox = new EditBox(this.font, cx, cy, 220, 20, Component.empty());
        this.nameBox.setValue(currentName);
        this.nameBox.setMaxLength(50);
        this.addRenderableWidget(this.nameBox);
        this.setFocused(this.nameBox);
        this.nameBox.setFocused(true);

        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.presets.confirm"),
                btn -> confirm()
        ).bounds(cx, cy + 26, 106, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> this.onClose()
        ).bounds(cx + 114, cy + 26, 106, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        int cx = this.width / 2 - 110;
        int cy = this.height / 2 - 10;

        // Caixa de fundo
        graphics.fill(cx - 14, cy - 32, cx + 234, cy + 52, 0xFF1A1A2E);
        graphics.fill(cx - 14, cy - 32, cx + 234, cy - 31, 0xFF444466);
        graphics.fill(cx - 14, cy + 51, cx + 234, cy + 52, 0xFF444466);

        graphics.centeredText(this.font, "Rename preset:",
                this.width / 2, cy - 22, 0xFFCCCCFF);
    }

    @Override
    public void tick() {
        super.tick();
        // Garantir foco no primeiro tick (init() pode ser sobreposto pelo MC)
        if (!focusSet && nameBox != null) {
            this.setFocused(nameBox);
            nameBox.setFocused(true);
            focusSet = true;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257) { confirm(); return true; } // Enter
        if (event.key() == 256) { this.onClose(); return true; } // ESC
        return super.keyPressed(event);
    }

    private void confirm() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty()) {
            PresetConfig.renamePreset(presetId, name);
        }
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
