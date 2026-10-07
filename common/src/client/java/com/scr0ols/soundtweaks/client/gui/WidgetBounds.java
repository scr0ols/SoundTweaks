package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.components.AbstractWidget;

/** Applies layout rects to widgets. */
final class WidgetBounds {

    private WidgetBounds() {}

    /** Moves and resizes {@code widget} to {@code r}. */
    static void place(AbstractWidget widget, Rect r) {
        widget.setX(r.x());
        widget.setY(r.y());
        widget.setWidth(r.w());
        widget.setHeight(r.h());
    }

    /** Places the widget and shows it, or hides it when {@code r} is empty. */
    static void placeOrHide(AbstractWidget widget, Rect r) {
        widget.visible = !r.isEmpty();
        if (!r.isEmpty()) place(widget, r);
    }
}
