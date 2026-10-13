package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.layout.Rect;
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

    /** Draws the message centred in {@code area}, cut with "..." when it is wider. */
    void render(GuiGraphicsExtractor g, Font font, Rect area) {
        if (text.isEmpty() || System.currentTimeMillis() >= expiry) return;
        g.centeredText(font, GuiText.ellipsize(font, text, area.w()), area.x() + area.w() / 2, area.y(), color);
    }
}
