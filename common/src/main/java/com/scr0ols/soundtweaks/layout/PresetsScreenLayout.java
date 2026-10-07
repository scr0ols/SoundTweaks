package com.scr0ols.soundtweaks.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the presets screen. Returns absolute rects; parts that are not shown are {@link Rect#EMPTY}.
 *
 * <p>Modes: {@code CENTERED} (no preset selected: the list alone, centred), {@code SIDE_BY_SIDE} (list left,
 * detail panel right), and below {@link LayoutMode#TWO_STEP_BP} two steps: {@code LIST_STEP} (the list on the
 * whole width) then {@code DETAIL_STEP} (the panel on the whole width, with a back button).
 */
public record PresetsScreenLayout(
        Mode mode, boolean hasDetail, boolean soundsTab,
        Rect panel, Rect divider, Rect separator,
        Rect listTitle, Rect detailTitle, Rect backButton,
        Rect list, Rect importButton, Rect exportButton, Rect openConfigButton, Rect newButton, Rect doneButton,
        Rect message,
        List<Rect> tabs, int tabLines, Rect content,
        Rect soundsImportButton, FilterBarLayout soundsFilter, Rect soundsList) {

    public enum Mode { CENTERED, SIDE_BY_SIDE, LIST_STEP, DETAIL_STEP }

    public static final int HEADER_H = 28;
    public static final int TITLE_Y = 8;
    public static final int TITLE_H = 10;
    public static final int CENTERED_MAX_W = 400;
    public static final int LIST_MIN_W = 200;
    public static final int LIST_MAX_W = 330;
    public static final int LIST_SHARE_PCT = 38;
    public static final int TAB_PADDING = 14;
    public static final int TAB_GAP = 4;
    public static final int TAB_PITCH = 22;
    public static final int BACK_W = 56;
    public static final int DONE_W = 120;
    public static final int SOUNDS_IMPORT_W = 110;
    public static final int SOUNDS_FOOTER_WIDE_BP = 380;
    public static final int MUTE_W = 24;
    public static final int VIEW_TOGGLE_W = 78;

    private static final int ROW1_FROM_BOTTOM = 50;
    private static final int ROW2_FROM_BOTTOM = 26;
    private static final int MESSAGE_FROM_BOTTOM = 62;
    private static final int MESSAGE_H = 10;
    private static final int LIST_BOTTOM_FROM_BOTTOM = 64;
    private static final int SEPARATOR_FROM_BOTTOM = 36;
    private static final int SEPARATOR_H = 2;
    private static final int CONTENT_BOTTOM_FROM_BOTTOM = 38;
    private static final int CONTENT_GAP = 6;

    public PresetsScreenLayout {
        tabs = List.copyOf(tabs);
    }

    /**
     * @param hasSelection  a preset is being edited
     * @param detailStep    in two-step mode, show the detail (step 2) instead of the list (step 1)
     * @param soundsTab     the Edit Sounds tab is active (it has its own footer, filter bar and list)
     * @param tabTextWidths measured text width of each tab label; each tab is that plus {@link #TAB_PADDING}
     */
    public static PresetsScreenLayout compute(int w, int h, boolean hasSelection, boolean detailStep,
                                              boolean soundsTab, int[] tabTextWidths) {
        boolean twoStep = w < LayoutMode.TWO_STEP_BP;
        Mode mode = !hasSelection ? Mode.CENTERED
                : !twoStep ? Mode.SIDE_BY_SIDE
                : detailStep ? Mode.DETAIL_STEP : Mode.LIST_STEP;
        return switch (mode) {
            case CENTERED -> listOnly(mode, w, h, Math.min(CENTERED_MAX_W, w - 16));
            case LIST_STEP -> listOnly(mode, w, h, w - 8);
            case SIDE_BY_SIDE -> withDetail(mode, w, h, soundsTab, tabTextWidths);
            case DETAIL_STEP -> withDetail(mode, w, h, soundsTab, tabTextWidths);
        };
    }

    private static PresetsScreenLayout listOnly(Mode mode, int w, int h, int listW) {
        int x = (w - listW) / 2;
        Rect list = new Rect(x, HEADER_H, listW, listHeight(h));
        int row1 = h - ROW1_FROM_BOTTOM;
        int row2 = h - ROW2_FROM_BOTTOM;
        int half = (listW - 12) / 2;
        int small = (half - 4) / 2;
        int rightX = x + listW / 2 + 2;
        return new PresetsScreenLayout(mode, false, false,
                Rect.EMPTY, Rect.EMPTY, Rect.EMPTY,
                new Rect(x, TITLE_Y, listW, TITLE_H), Rect.EMPTY, Rect.EMPTY,
                list,
                button(x + 4, row1, small), button(x + 8 + small, row1, small), button(rightX, row1, half),
                button(x + 4, row2, half), button(rightX, row2, half),
                new Rect(x + 4, h - MESSAGE_FROM_BOTTOM, listW - 8, MESSAGE_H),
                List.of(), 0, Rect.EMPTY,
                Rect.EMPTY, emptyFilter(), Rect.EMPTY);
    }

    private static PresetsScreenLayout withDetail(Mode mode, int w, int h, boolean soundsTab, int[] tabTextWidths) {
        boolean sideBySide = mode == Mode.SIDE_BY_SIDE;
        int listW = (w * LIST_SHARE_PCT + 50) / 100;
        listW = Math.max(LIST_MIN_W, Math.min(LIST_MAX_W, listW));

        Rect panel;
        Rect divider = Rect.EMPTY;
        Rect list = Rect.EMPTY;
        Rect listTitle = Rect.EMPTY;
        Rect backButton = Rect.EMPTY;
        Rect detailTitle;
        Rect importButton = Rect.EMPTY;
        Rect exportButton = Rect.EMPTY;
        Rect openConfigButton = Rect.EMPTY;
        Rect newButton = Rect.EMPTY;
        Rect message = Rect.EMPTY;
        if (sideBySide) {
            panel = new Rect(listW + 1, 0, w - listW - 1, h);
            divider = new Rect(listW, HEADER_H, 1, h - HEADER_H);
            list = new Rect(0, HEADER_H, listW, listHeight(h));
            listTitle = new Rect(0, TITLE_Y, listW, TITLE_H);
            detailTitle = new Rect(panel.x(), TITLE_Y, panel.w(), TITLE_H);
            int row1 = h - ROW1_FROM_BOTTOM;
            int available = listW - 8 - 2 * 4;
            int small = available / 4;
            int large = available - 2 * small;
            importButton = button(4, row1, small);
            exportButton = button(8 + small, row1, small);
            openConfigButton = button(12 + 2 * small, row1, large);
            newButton = button(4, h - ROW2_FROM_BOTTOM, listW - 8);
            message = new Rect(4, h - MESSAGE_FROM_BOTTOM, listW - 8, MESSAGE_H);
        } else {
            panel = new Rect(0, 0, w, h);
            backButton = new Rect(4, 4, BACK_W, LayoutMode.BUTTON_H);
            detailTitle = new Rect(BACK_W + 8, TITLE_Y, Math.max(0, w - 2 * (BACK_W + 8)), TITLE_H);
        }

        int px = panel.x();
        int pw = panel.w();
        List<Rect> tabs = new ArrayList<>(tabTextWidths.length);
        int tabX = px + 4;
        int tabY = HEADER_H;
        for (int textW : tabTextWidths) {
            int tabW = textW + TAB_PADDING;
            if (tabX > px + 4 && tabX + tabW > px + pw - 4) {
                tabX = px + 4;
                tabY += TAB_PITCH;
            }
            tabs.add(new Rect(tabX, tabY, tabW, LayoutMode.BUTTON_H));
            tabX += tabW + TAB_GAP;
        }
        int tabLines = (tabY - HEADER_H) / TAB_PITCH + 1;

        int contentY = HEADER_H + tabLines * TAB_PITCH + CONTENT_GAP;
        int contentBottom = h - CONTENT_BOTTOM_FROM_BOTTOM;
        Rect content = new Rect(px, contentY, pw, Math.max(0, contentBottom - contentY));
        Rect separator = new Rect(px, h - SEPARATOR_FROM_BOTTOM, pw, SEPARATOR_H);

        FilterBarLayout filter = FilterBarLayout.compute(new Rect(px, contentY, pw, LayoutMode.BUTTON_H),
                new int[] {MUTE_W, VIEW_TOGGLE_W});
        int listTop = contentY + filter.height();
        Rect soundsList = new Rect(px, listTop, pw, Math.max(0, contentBottom - listTop));

        int footerY = h - ROW2_FROM_BOTTOM;
        Rect done;
        Rect soundsImport = Rect.EMPTY;
        if (soundsTab && pw >= SOUNDS_FOOTER_WIDE_BP) {
            soundsImport = new Rect(px + pw / 2 - 117, footerY, SOUNDS_IMPORT_W, LayoutMode.BUTTON_H);
            done = new Rect(px + pw / 2 - 3, footerY, DONE_W, LayoutMode.BUTTON_H);
        } else if (soundsTab) {
            int half = (pw - 12) / 2;
            soundsImport = new Rect(px + 4, footerY, half, LayoutMode.BUTTON_H);
            done = new Rect(px + 8 + half, footerY, half, LayoutMode.BUTTON_H);
        } else {
            done = new Rect(px + pw / 2 - DONE_W / 2, footerY, DONE_W, LayoutMode.BUTTON_H);
        }

        return new PresetsScreenLayout(mode, true, soundsTab,
                panel, divider, separator,
                listTitle, detailTitle, backButton,
                list, importButton, exportButton, openConfigButton, newButton, done,
                message,
                tabs, tabLines, content,
                soundsImport, filter, soundsList);
    }

    private static int listHeight(int h) {
        return Math.max(0, h - LIST_BOTTOM_FROM_BOTTOM - HEADER_H);
    }

    private static Rect button(int x, int y, int w) {
        return new Rect(x, y, w, LayoutMode.BUTTON_H);
    }

    /** Placeholder for modes without a sounds filter bar. */
    private static FilterBarLayout emptyFilter() {
        return new FilterBarLayout(Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, List.of(), 0, 0);
    }
}
