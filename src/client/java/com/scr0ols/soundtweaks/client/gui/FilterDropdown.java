package com.scr0ols.soundtweaks.client.gui;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable dropdown component for category and object filters.
 *
 * Does NOT extend AbstractWidget — manually managed by SoundTweaksScreen
 * to ensure the popup is always drawn on top of everything else.
 *
 * Usage:
 *   1. Instantiate with position, dimensions, placeholder label, and callback
 *   2. Call render() in the Screen's extractRenderState() (AFTER all other widgets)
 *   3. Call mouseClicked() and mouseScrolled() BEFORE passing them to the normal UI
 */
public class FilterDropdown {

    // --- Dimensions and position ---
    private int x, y;
    private final int width;
    private static final int BUTTON_HEIGHT  = 20;
    private static final int ITEM_HEIGHT    = 14; // altura de cada linha na popup
    private static final int MAX_VISIBLE    = 8;  // maximum visible rows without scrolling
    private static final int POPUP_PADDING  = 2;  // padding interior da popup

    // --- State ---
    private List<String> options     = new ArrayList<>(); // internal values (e.g. "piston")
    private List<String> labels      = new ArrayList<>(); // display labels (e.g. "Piston")
    private String selectedValue     = null;  // null = I18n.get("soundtweaks.gui.all") / nothing selected
    private boolean isOpen           = false;
    private int hoveredIndex         = -1;    // hovered index in the popup (-1 = none)
    private int scrollOffset         = 0;     // number of lines scrolled up

    // --- Text ---
    private final String placeholder;         // text when nothing is selected (e.g. "Category")
    private final boolean enabled;            // disabled = grey button, popup does not open
    private boolean active           = true;  // can be disabled dynamically

    // --- Letter navigation ---
    private char lastJumpLetter      = 0;     // last letter used for jump
    private int  lastJumpIndex       = -1;    // index in options of the last jump (-1 = none)

    // --- Scrollbar drag ---
    private boolean isDragging       = false;
    private int     dragStartY       = 0;     // mouse Y when the drag started
    private int     dragStartOffset  = 0;     // scrollOffset when the drag started

    // --- Callback ---
    private final Consumer<String> onSelect;  // called when the user selects an option
                                              // null = clear selection (I18n.get("soundtweaks.gui.all"))

    // --- References ---
    private final Font font;

    public FilterDropdown(int x, int y, int width,
                          String placeholder,
                          Consumer<String> onSelect) {
        this.x           = x;
        this.y           = y;
        this.width       = width;
        this.placeholder = placeholder;
        this.onSelect    = onSelect;
        this.font        = Minecraft.getInstance().font;
        this.enabled     = true;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Updates the dropdown options.
     * Called when the category changes (for the object dropdown)
     * or on screen init (for the category dropdown).
     *
     * @param options  internal values (passed to the callback)
     * @param labels   display labels (same order as options)
     */
    public void setOptions(List<String> options, List<String> labels) {
        this.options       = new ArrayList<>(options);
        this.labels        = new ArrayList<>(labels);
        this.scrollOffset  = 0;
        this.hoveredIndex  = -1;
        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    /** Clears the selection (returns to "nothing selected" state). */
    public void clearSelection() {
        this.selectedValue  = null;
        this.isOpen         = false;
        this.scrollOffset   = 0;
        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    /** Closes the popup without changing the selection. */
    public void close() {
        this.isOpen         = false;
        this.hoveredIndex   = -1;
        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    /** Enables or disables the dropdown (e.g. disable "Object" while no category is selected). */
    public void setActive(boolean active) {
        this.active = active;
        if (!active) close();
    }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }

    public String getSelectedValue() { return selectedValue; }
    public boolean isOpen()          { return isOpen; }

    /** Restores the selection without firing the callback — used to persist state between sessions. */
    public void setSelectedValueSilently(@Nullable String value) {
        this.selectedValue = value;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Draws the button and, if open, the popup on top.
     * Must be called AFTER all other widgets on the screen.
     */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        renderButton(graphics, mouseX, mouseY);
        if (isOpen) {
            renderPopup(graphics, mouseX, mouseY);
        }
    }

    private void renderButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hovered = isMouseOverButton(mouseX, mouseY);

        // Button background
        int bgColor = !active          ? 0xFF333333  // disabled — dark grey
                    : hovered || isOpen ? 0xFF555566  // hover/open — bluish
                    :                     0xFF444444; // normal
        graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, bgColor);

        // Border
        int borderColor = active ? 0xFF888888 : 0xFF555555;
        graphics.fill(x,             y,                      x + width, y + 1,             borderColor); // top
        graphics.fill(x,             y + BUTTON_HEIGHT - 1,  x + width, y + BUTTON_HEIGHT, borderColor); // bottom
        graphics.fill(x,             y,                      x + 1,     y + BUTTON_HEIGHT, borderColor); // left
        graphics.fill(x + width - 1, y,                      x + width, y + BUTTON_HEIGHT, borderColor); // right

        // Label: selected value or placeholder
        String label = selectedValue != null
                ? getLabelForValue(selectedValue)
                : placeholder;
        int textColor = active ? 0xFFFFFFFF : 0xFF777777;

        // Truncate label if it does not fit (leave space for "▾")
        String truncated = truncateToWidth(label, width - 18);
        graphics.text(font, truncated, x + 5, y + 6, textColor);

        // Arrow ▾ (or ▴ if open)
        String arrow = isOpen ? "▴" : "▾";
        graphics.text(font, arrow, x + width - 11, y + 6, textColor);
    }

    private void renderPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int popupHeight = getPopupHeight();
        int popupY      = y + BUTTON_HEIGHT; // immediately below the button

        // Popup background — slightly darker than the screen
        graphics.fill(x, popupY, x + width, popupY + popupHeight, 0xFF222222);

        // Borda da popup
        graphics.fill(x,             popupY,                  x + width, popupY + 1,          0xFF888888);
        graphics.fill(x,             popupY + popupHeight - 1, x + width, popupY + popupHeight, 0xFF888888);
        graphics.fill(x,             popupY,                  x + 1,     popupY + popupHeight, 0xFF888888);
        graphics.fill(x + width - 1, popupY,                  x + width, popupY + popupHeight, 0xFF888888);

        // Visible rows
        int visibleCount = Math.min(MAX_VISIBLE, options.size() + 1); // +1 for I18n.get("soundtweaks.gui.all")
        hoveredIndex = -1;

        for (int i = 0; i < visibleCount; i++) {
            int optionIndex = i + scrollOffset - 1; // -1 because index 0 = I18n.get("soundtweaks.gui.all")
            int itemY = popupY + POPUP_PADDING + i * ITEM_HEIGHT;

            boolean isHovered = mouseX >= x && mouseX < x + width
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            // Determine whether this row is currently selected
            boolean isSelected;
            String lineLabel;
            if (optionIndex < 0) {
                // "All" row — index -1, always at the top (when scrollOffset = 0)
                isSelected = (selectedValue == null);
                lineLabel  = I18n.get("soundtweaks.gui.all");
            } else if (optionIndex < options.size()) {
                isSelected = options.get(optionIndex).equals(selectedValue);
                lineLabel  = labels.get(optionIndex);
            } else {
                break; // no more options
            }

            if (isHovered) hoveredIndex = optionIndex;

            // Row background: hover > selected > normal
            if (isHovered) {
                graphics.fill(x + 1, itemY, x + width - 1, itemY + ITEM_HEIGHT, 0xFF3355AA);
            } else if (isSelected) {
                graphics.fill(x + 1, itemY, x + width - 1, itemY + ITEM_HEIGHT, 0xFF334466);
            }

            // Row text — extra right margin when a scrollbar is present (9px bar + 3px gap)
            int totalOpts = options.size() + 1;
            int textMaxWidth = totalOpts > MAX_VISIBLE ? width - 18 : width - 10;
            String truncated = truncateToWidth(lineLabel, textMaxWidth);
            graphics.text(font, truncated, x + 5, itemY + 3, 0xFFFFFFFF);
        }

        // Scrollbar (when there are more options than visible space)
        // SCROLLBAR_W = 6px — wide enough to be clickable
        int totalOptions = options.size() + 1;
        if (totalOptions > MAX_VISIBLE) {
            int scrollTrackH = popupHeight - 4;
            int scrollThumbH = Math.max(12, scrollTrackH * MAX_VISIBLE / totalOptions);
            int scrollThumbY = popupY + 2 + (scrollTrackH - scrollThumbH) * scrollOffset / Math.max(1, totalOptions - MAX_VISIBLE);

            // Track (dark grey background)
            graphics.fill(x + width - 7, popupY + 2, x + width - 1, popupY + popupHeight - 2, 0xFF333333);
            // Thumb (light grey)
            graphics.fill(x + width - 7, scrollThumbY, x + width - 1, scrollThumbY + scrollThumbH, 0xFF888888);
        }
    }

    // -------------------------------------------------------------------------
    // Eventos de rato
    // -------------------------------------------------------------------------

    /**
     * Handles a mouse click.
     * @return true if the event was consumed (the normal UI should not process it)
     */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!active) return false;

        // Extract coordinates from the event (26.1.2 API uses record MouseButtonEvent)
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // Click on the button — toggle popup
        if (isMouseOverButton(mouseX, mouseY)) {
            isOpen = !isOpen;
            if (isOpen) {
                scrollOffset    = 0;
                lastJumpLetter  = 0;   // reset on open — each session starts fresh
                lastJumpIndex   = -1;
            }
            return true;
        }

        // Clique dentro da popup
        if (isOpen && isMouseOverPopup(mouseX, mouseY)) {
            int popupY      = y + BUTTON_HEIGHT;
            int popupHeight = getPopupHeight();
            int totalOptions = options.size() + 1;

            // Click on the scrollbar (right column, 6px wide) — start drag
            if (totalOptions > MAX_VISIBLE && mouseX >= x + width - 7 && mouseX < x + width - 1) {
                isDragging      = true;
                dragStartY      = mouseY;
                dragStartOffset = scrollOffset;
                // Also jump to the clicked position immediately
                int trackTop  = popupY + 2;
                int trackH    = popupHeight - 4;
                double ratio  = (double)(mouseY - trackTop) / trackH;
                int maxOffset = totalOptions - MAX_VISIBLE;
                scrollOffset  = (int) Math.max(0, Math.min(maxOffset, Math.round(ratio * maxOffset)));
                dragStartOffset = scrollOffset; // update drag base to the jumped position
                return true;
            }

            // Click on an option row — select and close
            int relativeY   = mouseY - popupY - POPUP_PADDING;
            int clickedLine = relativeY / ITEM_HEIGHT;
            int optionIndex = clickedLine + scrollOffset - 1; // -1 = I18n.get("soundtweaks.gui.all")

            if (optionIndex < 0) {
                selectedValue = null;
                onSelect.accept(null);
            } else if (optionIndex < options.size()) {
                selectedValue = options.get(optionIndex);
                onSelect.accept(selectedValue);
            }

            isOpen = false;
            return true;
        }

        // Click outside — close popup without consuming the event
        // (e.g. the user clicked a slider while the popup was open)
        if (isOpen) {
            isOpen = false;
        }

        return false;
    }

    /**
     * Mouse drag — only acts if currently dragging the scrollbar.
     * Called by SoundTweaksScreen.mouseDragged.
     * @return true if consumed
     */
    public boolean mouseDragged(double mouseY) {
        if (!isDragging) return false;

        int totalOptions = options.size() + 1;
        int maxOffset    = Math.max(0, totalOptions - MAX_VISIBLE);
        if (maxOffset == 0) return true;

        int popupHeight  = getPopupHeight();
        int trackH       = popupHeight - 4;
        // Pixels per scrollOffset unit
        double pixelsPerUnit = (double) trackH / maxOffset;
        int delta = (int) Math.round((mouseY - dragStartY) / pixelsPerUnit);
        scrollOffset = Math.max(0, Math.min(maxOffset, dragStartOffset + delta));
        return true;
    }

    /** Para o drag da scroll bar. */
    public void mouseReleased() {
        isDragging = false;
    }

    /**
     * Jumps to the next option whose label starts with the given letter.
     * - First press of X → goes to the 1st option starting with X
     * - Second press of X → goes to the 2nd option starting with X
     * - When the end of options starting with X is reached, wraps to the 1st (cycling)
     * - Switching letter restarts from the beginning for the new letter
     */
    public boolean jumpToLetter(char c) {
        if (!isOpen || labels.isEmpty()) return false;
        char upper = Character.toUpperCase(c);

        // Collect all option indices whose label starts with this letter
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (!label.isEmpty() && Character.toUpperCase(label.charAt(0)) == upper) {
                matches.add(i);
            }
        }
        if (matches.isEmpty()) return false;

        // Determine which index to show next
        int nextMatchPos; // position within matches[]
        if (upper != lastJumpLetter) {
            // New letter — start from the beginning
            nextMatchPos = 0;
        } else {
            // Same letter — advance to the next, with wrap-around
            int currentMatchPos = matches.indexOf(lastJumpIndex);
            nextMatchPos = (currentMatchPos + 1) % matches.size();
        }

        int targetIndex = matches.get(nextMatchPos); // index in options[]
        lastJumpLetter = upper;
        lastJumpIndex  = targetIndex;

        // Calculate scrollOffset to place the option at the top of the visible popup.
        // When scrollOffset=S, row 0 shows optionIndex = S-1.
        // To show options[targetIndex] in row 0: S = targetIndex + 1.
        int maxOffset = Math.max(0, options.size() + 1 - MAX_VISIBLE);
        scrollOffset  = Math.min(targetIndex + 1, maxOffset);
        return true;
    }

    /**
     * Mouse scroll — only acts when the cursor is over the open popup.
     * @return true if consumed
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isOpen) return false;
        if (!isMouseOverPopup((int) mouseX, (int) mouseY)) return false;

        int totalOptions = options.size() + 1; // +1 for I18n.get("soundtweaks.gui.all")
        int maxOffset = Math.max(0, totalOptions - MAX_VISIBLE);

        scrollOffset = (int) Math.max(0, Math.min(maxOffset, scrollOffset - scrollY));
        return true;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private boolean isMouseOverPopup(int mouseX, int mouseY) {
        if (!isOpen) return false;
        int popupY = y + BUTTON_HEIGHT;
        return mouseX >= x && mouseX < x + width
                && mouseY >= popupY && mouseY < popupY + getPopupHeight();
    }

    /** Total popup height in pixels. */
    private int getPopupHeight() {
        int totalOptions = options.size() + 1; // +1 for I18n.get("soundtweaks.gui.all")
        int visibleCount = Math.min(MAX_VISIBLE, totalOptions);
        return visibleCount * ITEM_HEIGHT + POPUP_PADDING * 2;
    }

    /** Returns the display label for an internal value. */
    private String getLabelForValue(String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(value)) return labels.get(i);
        }
        return value; // fallback: show the raw value
    }

    /** Truncates text to fit within a maximum pixel width, appending "..." if needed. */
    private String truncateToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        while (!text.isEmpty() && font.width(text) + ellipsisWidth > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
