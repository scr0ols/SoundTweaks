package com.scr0ols.soundtweaks.layout;

/** Immutable screen rectangle; {@link #right()} and {@link #bottom()} are exclusive. */
public record Rect(int x, int y, int w, int h) {

    /** Stands for "not shown". */
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public boolean isEmpty() { return w <= 0 || h <= 0; }

    public int right() { return x + w; }

    public int bottom() { return y + h; }

    public boolean contains(int px, int py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public boolean intersects(Rect o) {
        if (w <= 0 || h <= 0 || o.w <= 0 || o.h <= 0) return false;
        return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
    }

    /** Shrinks every side by {@code n}; the size is floored at zero. */
    public Rect inset(int n) {
        return new Rect(x + n, y + n, Math.max(0, w - 2 * n), Math.max(0, h - 2 * n));
    }
}
