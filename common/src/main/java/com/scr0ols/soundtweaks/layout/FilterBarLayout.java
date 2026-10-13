package com.scr0ols.soundtweaks.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geometry of the filter bar: [Category] [Object] [x] [Search] plus optional buttons on the right.
 *
 * <p>The area is the full width of the zone the bar lives in; a 4 px margin is applied inside it.
 * On one line when there is room, otherwise [Category][Object][x] on the first line and the search
 * box (with the trailing buttons) on the second.
 */
public record FilterBarLayout(Rect category, Rect object, Rect clear, Rect search,
                              List<Rect> trailing, int lines, int height) {

    public static final int MARGIN = 4;
    public static final int GAP = 4;
    public static final int LINE_PITCH = 22;
    public static final int ONE_LINE_BP = 340;
    public static final int ONE_LINE_TRAILING_BP = 456;
    public static final int MIN_SEARCH_W = 60;
    public static final int MAX_DROPDOWNS_W = 250;
    public static final int MAX_DROPDOWNS_TRAILING_W = 220;
    public static final int CLEAR_W = LayoutMode.BUTTON_H;
    private static final int CATEGORY_SHARE_PCT = 48;

    public FilterBarLayout {
        trailing = List.copyOf(trailing);
    }

    /** Trailing buttons are square ({@code BUTTON_H} wide). */
    public static FilterBarLayout compute(Rect area, int trailingButtons) {
        int[] widths = new int[Math.max(0, trailingButtons)];
        Arrays.fill(widths, LayoutMode.BUTTON_H);
        return compute(area, widths);
    }

    public static FilterBarLayout compute(Rect area, int[] trailingWidths) {
        boolean hasTrailing = trailingWidths.length > 0;
        int innerX = area.x() + MARGIN;
        int innerW = Math.max(0, area.w() - 2 * MARGIN);
        int trailW = trailingTotal(trailingWidths);
        int breakpoint = hasTrailing ? ONE_LINE_TRAILING_BP : ONE_LINE_BP;
        return area.w() >= breakpoint
                ? oneLine(area, innerX, innerW, trailingWidths, trailW)
                : twoLines(area, innerX, innerW, trailingWidths, trailW);
    }

    private static FilterBarLayout oneLine(Rect area, int innerX, int innerW, int[] trailingWidths, int trailW) {
        int y = area.y();
        int reserved = trailingWidths.length > 0 ? trailW + GAP : 0;
        int cap = trailingWidths.length > 0 ? MAX_DROPDOWNS_TRAILING_W : MAX_DROPDOWNS_W;
        int dropdownsW = Math.max(0, Math.min(cap, innerW - CLEAR_W - 3 * GAP - reserved - MIN_SEARCH_W));
        int categoryW = dropdownsW * CATEGORY_SHARE_PCT / 100;
        int objectW = dropdownsW - categoryW;

        Rect category = new Rect(innerX, y, categoryW, LayoutMode.BUTTON_H);
        Rect object = new Rect(category.right() + GAP, y, objectW, LayoutMode.BUTTON_H);
        Rect clear = new Rect(object.right() + GAP, y, CLEAR_W, LayoutMode.BUTTON_H);
        int searchX = clear.right() + GAP;
        int searchRight = innerX + innerW - reserved;
        Rect search = new Rect(searchX, y, Math.max(0, searchRight - searchX), LayoutMode.BUTTON_H);
        return new FilterBarLayout(category, object, clear, search,
                trailingRects(trailingWidths, innerX + innerW, y), 1, LayoutMode.BUTTON_H + GAP);
    }

    private static FilterBarLayout twoLines(Rect area, int innerX, int innerW, int[] trailingWidths, int trailW) {
        int y = area.y();
        int dropdownsW = Math.max(0, innerW - CLEAR_W - 2 * GAP);
        int categoryW = dropdownsW * CATEGORY_SHARE_PCT / 100;
        int objectW = dropdownsW - categoryW;

        Rect category = new Rect(innerX, y, categoryW, LayoutMode.BUTTON_H);
        Rect object = new Rect(category.right() + GAP, y, objectW, LayoutMode.BUTTON_H);
        Rect clear = new Rect(object.right() + GAP, y, CLEAR_W, LayoutMode.BUTTON_H);

        int y2 = y + LINE_PITCH;
        int reserved = trailingWidths.length > 0 ? trailW + GAP : 0;
        Rect search = new Rect(innerX, y2, Math.max(0, innerW - reserved), LayoutMode.BUTTON_H);
        return new FilterBarLayout(category, object, clear, search,
                trailingRects(trailingWidths, innerX + innerW, y2), 2, LINE_PITCH + LayoutMode.BUTTON_H + GAP);
    }

    private static int trailingTotal(int[] widths) {
        int total = 0;
        for (int w : widths) total += w;
        return widths.length == 0 ? 0 : total + GAP * (widths.length - 1);
    }

    /** Right-aligned: the last button ends at {@code right}. */
    private static List<Rect> trailingRects(int[] widths, int right, int y) {
        List<Rect> rects = new ArrayList<>(widths.length);
        int x = right - trailingTotal(widths);
        for (int w : widths) {
            rects.add(new Rect(x, y, w, LayoutMode.BUTTON_H));
            x += w + GAP;
        }
        return rects;
    }
}
