package com.scr0ols.soundtweaks.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the main screen: header, filter bar, sound list, footer and the favourites panel
 * (sidebar, thin rail, or nothing).
 *
 * <p>Everything is absolute screen coordinates; the content spans the whole window width.
 * A rect that is not shown (title that does not fit, count, Presets button, panel parts)
 * is {@link Rect#EMPTY}.
 *
 * @param content          zone left of the panel that holds the header, filters, list and footer
 * @param panelBounds      the whole sidebar or rail, full height; {@link Rect#EMPTY} when there is none
 * @param panelHeader      clickable header of the sidebar (closes it) or the star area of the rail
 * @param favorites        one rect per visible favourite; entry {@code i} is favourite {@code firstFavorite + i}
 * @param hiddenFavorites  favourites that do not fit (above and below the visible window)
 * @param moreIndicator    where the rail draws "+N"; empty when nothing is hidden
 */
public record MainScreenLayout(ScreenFrame frame, LayoutMode mode, Panel panel, Rect content,
                               Rect header, Rect mute, Rect viewToggle, Rect presets, Rect title,
                               FilterBarLayout filterBar, Rect list,
                               Rect count, Rect importButton, Rect doneButton,
                               Rect panelBounds, Rect panelHeader, Rect manage,
                               List<Rect> favorites, int firstFavorite, int hiddenFavorites,
                               Rect moreIndicator) {

    public enum Panel { NONE, SIDEBAR, RAIL }

    public static final int HEADER_H = 24;
    public static final int FILTER_Y = 26;
    public static final int FOOTER_H = 36;
    /** Content width from which the footer shows the count and two centred buttons. */
    public static final int FOOTER_COUNT_BP = 400;
    /** Minimum room left of the header buttons for the title to be shown. */
    public static final int TITLE_MIN_W = 80;
    public static final int FOOTER_BUTTON_W = 120;
    public static final int SIDEBAR_MIN_W = 140;
    public static final int SIDEBAR_MAX_W = 220;
    /** Content width the sidebar always leaves: sidebar width is frame width minus this, clamped. */
    public static final int SIDEBAR_CONTENT_W = 420;
    public static final int SIDEBAR_FAVORITE_H = 22;
    public static final int SIDEBAR_FAVORITE_PITCH = 23;
    public static final int RAIL_FAVORITE_PITCH = 22;
    public static final int MORE_INDICATOR_H = 10;

    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int MUTE_X = 4;
    private static final int VIEW_X = 28;
    private static final int VIEW_W = 78;
    private static final int PRESETS_X = 110;
    private static final int PRESETS_W = 68;
    private static final int HEADER_BUTTON_Y = 2;
    private static final int PANEL_TOP = 26;
    private static final int RAIL_TOP = 28;
    private static final int FOOTER_BUTTON_FROM_BOTTOM = 26;
    private static final int COUNT_X = 8;
    private static final int COUNT_Y_FROM_BOTTOM = 22;
    private static final int COUNT_H = 10;
    private static final int MANAGE_FROM_BOTTOM = 24;

    public MainScreenLayout {
        favorites = List.copyOf(favorites);
    }

    public static MainScreenLayout compute(int w, int h, boolean sidebarOpen, int favoriteCount) {
        return compute(w, h, sidebarOpen, favoriteCount, 0);
    }

    /**
     * @param railFirst index of the first favourite shown by the thin rail (the mouse wheel moves it);
     *                  clamped to the last page, and ignored when the panel is not the rail
     */
    public static MainScreenLayout compute(int w, int h, boolean sidebarOpen, int favoriteCount, int railFirst) {
        ScreenFrame frame = ScreenFrame.of(w, h);
        Rect fb = frame.bounds();
        Panel panel = frame.useRail() ? Panel.RAIL : (sidebarOpen ? Panel.SIDEBAR : Panel.NONE);
        int panelW = switch (panel) {
            case RAIL -> LayoutMode.RAIL_W;
            case SIDEBAR -> Math.max(SIDEBAR_MIN_W, Math.min(SIDEBAR_MAX_W, fb.w() - SIDEBAR_CONTENT_W));
            case NONE -> 0;
        };
        Rect content = new Rect(fb.x(), 0, fb.w() - panelW, h);
        Rect panelBounds = panel == Panel.NONE ? Rect.EMPTY : new Rect(content.right(), 0, panelW, h);

        Rect header = new Rect(content.x(), 0, content.w(), HEADER_H);
        Rect mute = new Rect(content.x() + MUTE_X, HEADER_BUTTON_Y, LayoutMode.BUTTON_H, LayoutMode.BUTTON_H);
        Rect viewToggle = new Rect(content.x() + VIEW_X, HEADER_BUTTON_Y, VIEW_W, LayoutMode.BUTTON_H);
        boolean showPresets = panel != Panel.RAIL;
        Rect presets = showPresets
                ? new Rect(content.x() + PRESETS_X, HEADER_BUTTON_Y, PRESETS_W, LayoutMode.BUTTON_H)
                : Rect.EMPTY;
        Rect title = titleArea(content, showPresets ? PRESETS_X + PRESETS_W : VIEW_X + VIEW_W);

        FilterBarLayout filterBar = FilterBarLayout.compute(
                new Rect(content.x(), FILTER_Y, content.w(), LayoutMode.BUTTON_H), 0);
        int listY = FILTER_Y + filterBar.height();
        Rect list = new Rect(content.x(), listY, content.w(), Math.max(0, h - FOOTER_H - listY));

        boolean showCount = content.w() >= FOOTER_COUNT_BP;
        int buttonY = h - FOOTER_BUTTON_FROM_BOTTOM;
        Rect importButton;
        Rect doneButton;
        Rect count = Rect.EMPTY;
        if (showCount) {
            int mid = content.x() + content.w() / 2;
            importButton = new Rect(mid - FOOTER_BUTTON_W - GAP - 1, buttonY, FOOTER_BUTTON_W, LayoutMode.BUTTON_H);
            doneButton = new Rect(mid + GAP + 1, buttonY, FOOTER_BUTTON_W, LayoutMode.BUTTON_H);
            int countX = content.x() + COUNT_X;
            count = new Rect(countX, h - COUNT_Y_FROM_BOTTOM,
                    Math.max(0, importButton.x() - GAP - countX), COUNT_H);
        } else {
            int each = (content.w() - 3 * MARGIN) / 2;
            importButton = new Rect(content.x() + MARGIN, buttonY, each, LayoutMode.BUTTON_H);
            doneButton = new Rect(importButton.right() + MARGIN, buttonY, each, LayoutMode.BUTTON_H);
        }

        return switch (panel) {
            case NONE -> new MainScreenLayout(frame, frame.mode(), panel, content, header, mute, viewToggle,
                    presets, title, filterBar, list, count, importButton, doneButton,
                    Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, List.of(), 0, 0, Rect.EMPTY);
            case SIDEBAR -> sidebar(frame, content, header, mute, viewToggle, presets, title, filterBar, list,
                    count, importButton, doneButton, panelBounds, h, favoriteCount);
            case RAIL -> rail(frame, content, header, mute, viewToggle, presets, title, filterBar, list,
                    count, importButton, doneButton, panelBounds, h, favoriteCount, railFirst);
        };
    }

    private static Rect titleArea(Rect content, int buttonsRight) {
        int room = content.w() - buttonsRight;
        if (room < TITLE_MIN_W) return Rect.EMPTY;
        return new Rect(content.x() + buttonsRight + MARGIN, 0, room - 2 * MARGIN, HEADER_H);
    }

    private static MainScreenLayout sidebar(ScreenFrame frame, Rect content, Rect header, Rect mute,
                                            Rect viewToggle, Rect presets, Rect title, FilterBarLayout filterBar,
                                            Rect list, Rect count, Rect importButton, Rect doneButton,
                                            Rect panelBounds, int h, int favoriteCount) {
        Rect manage = new Rect(panelBounds.x() + 2, h - MANAGE_FROM_BOTTOM, panelBounds.w() - 4, LayoutMode.BUTTON_H);
        int limit = manage.y() - GAP;
        List<Rect> favorites = new ArrayList<>();
        for (int i = 0, y = PANEL_TOP; i < favoriteCount && y + SIDEBAR_FAVORITE_H <= limit;
             i++, y += SIDEBAR_FAVORITE_PITCH) {
            favorites.add(new Rect(panelBounds.x() + 1, y, panelBounds.w() - 2, SIDEBAR_FAVORITE_H));
        }
        return new MainScreenLayout(frame, frame.mode(), Panel.SIDEBAR, content, header, mute, viewToggle,
                presets, title, filterBar, list, count, importButton, doneButton, panelBounds,
                new Rect(panelBounds.x(), 0, panelBounds.w(), HEADER_H), manage,
                favorites, 0, favoriteCount - favorites.size(), Rect.EMPTY);
    }

    private static MainScreenLayout rail(ScreenFrame frame, Rect content, Rect header, Rect mute,
                                         Rect viewToggle, Rect presets, Rect title, FilterBarLayout filterBar,
                                         Rect list, Rect count, Rect importButton, Rect doneButton,
                                         Rect panelBounds, int h, int favoriteCount, int railFirst) {
        int button = LayoutMode.BUTTON_H;
        int squareX = panelBounds.x() + (panelBounds.w() - button) / 2;
        Rect manage = new Rect(squareX, h - MANAGE_FROM_BOTTOM, button, button);
        int limit = manage.y() - GAP;
        int capacity = Math.max(0, (limit - RAIL_TOP + (RAIL_FAVORITE_PITCH - button)) / RAIL_FAVORITE_PITCH);
        int visible = Math.min(capacity, favoriteCount);
        int first = Math.max(0, Math.min(railFirst, favoriteCount - visible));
        List<Rect> favorites = new ArrayList<>(visible);
        for (int i = 0; i < visible; i++) {
            favorites.add(new Rect(squareX, RAIL_TOP + i * RAIL_FAVORITE_PITCH, button, button));
        }
        int hidden = favoriteCount - visible;
        Rect more = hidden > 0
                ? new Rect(panelBounds.x(), manage.y() - 12, panelBounds.w(), MORE_INDICATOR_H)
                : Rect.EMPTY;
        return new MainScreenLayout(frame, frame.mode(), Panel.RAIL, content, header, mute, viewToggle,
                presets, title, filterBar, list, count, importButton, doneButton, panelBounds,
                new Rect(panelBounds.x(), 0, panelBounds.w(), HEADER_H), manage,
                favorites, first, hidden, more);
    }
}
