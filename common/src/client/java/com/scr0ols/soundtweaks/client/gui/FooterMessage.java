package com.scr0ols.soundtweaks.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Timed one-line feedback (import/export result) drawn above the footer. */
final class FooterMessage {

    private static final long DURATION_MS = 4000L;

    private String text = "";
    private int color = 0xFFAAAAAA;
    private long expiry = 0L;

    void show(String message, int argb) {
        text = message;
        color = argb;
        expiry = System.currentTimeMillis() + DURATION_MS;
    }

    void render(GuiGraphicsExtractor g, Font font, int centerX, int y) {
        if (!text.isEmpty() && System.currentTimeMillis() < expiry)
            g.centeredText(font, text, centerX, y, color);
    }
}
