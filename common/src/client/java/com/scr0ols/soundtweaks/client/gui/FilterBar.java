package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.layout.FilterBarLayout;
import com.scr0ols.soundtweaks.layout.LayoutMode;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * [Category] [Object] [x] [Search] plus optional buttons on the right, positioned by a {@link FilterBarLayout}.
 *
 * The dropdowns are not widgets (they must draw on top of everything), so the owning screen forwards
 * render, mouse and key events to this bar, and calls {@link #render} last.
 */
public class FilterBar {

    private final FilterDropdown category;
    private final FilterDropdown object;
    private final Button clear;
    private final EditBox search;
    private final List<AbstractWidget> trailing;

    /**
     * @param trailing extra buttons shown to the right of the search box, left to right; their current
     *                 widths are used by the layout
     */
    public FilterBar(Font font,
                     Consumer<String> onCategory,
                     Consumer<String> onObject,
                     Runnable onClear,
                     Consumer<String> onSearch,
                     List<AbstractWidget> trailing) {
        this.category = new FilterDropdown(0, 0, 0, I18n.get("soundtweaks.gui.category"), onCategory);
        this.object = new FilterDropdown(0, 0, 0, I18n.get("soundtweaks.gui.object"), onObject);
        this.object.setActive(false);

        this.clear = Button.builder(Component.literal("x"), btn -> onClear.run())
                .bounds(0, 0, FilterBarLayout.CLEAR_W, LayoutMode.BUTTON_H).build();
        this.clear.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.clear_filters")));

        this.search = new EditBox(font, 0, 0, FilterBarLayout.MIN_SEARCH_W, LayoutMode.BUTTON_H,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.search.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.search.setResponder(onSearch);

        this.trailing = List.copyOf(trailing);
    }

    public FilterDropdown category() { return category; }
    public FilterDropdown object()   { return object; }
    public EditBox searchBox()       { return search; }

    /** Hands the clear button, search box and trailing buttons to the screen's widget list. */
    public void addWidgets(Consumer<AbstractWidget> add) {
        add.accept(clear);
        add.accept(search);
        trailing.forEach(add);
    }

    /** Lays the bar out inside {@code area} (the full width of its zone) and returns the layout used. */
    public FilterBarLayout layout(Rect area) {
        int[] widths = trailing.stream().mapToInt(AbstractWidget::getWidth).toArray();
        FilterBarLayout layout = FilterBarLayout.compute(area, widths);
        setLayout(layout);
        return layout;
    }

    public void setLayout(FilterBarLayout layout) {
        category.setBounds(layout.category().x(), layout.category().y(), layout.category().w());
        object.setBounds(layout.object().x(), layout.object().y(), layout.object().w());
        place(clear, layout.clear());
        place(search, layout.search());
        for (int i = 0; i < trailing.size(); i++) {
            place(trailing.get(i), layout.trailing().get(i));
        }
    }

    private static void place(AbstractWidget widget, Rect r) {
        widget.setX(r.x());
        widget.setY(r.y());
        widget.setWidth(r.w());
        widget.setHeight(r.h());
    }

    /** Draws the dropdowns (and their popups); call after everything else on the screen. */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        category.render(graphics, mouseX, mouseY);
        object.render(graphics, mouseX, mouseY);
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (category.mouseClicked(event)) {
            if (category.isOpen()) object.close();
            return true;
        }
        if (object.mouseClicked(event)) {
            if (object.isOpen()) category.close();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseY) {
        return category.mouseDragged(mouseY) || object.mouseDragged(mouseY);
    }

    public void mouseReleased() {
        category.mouseReleased();
        object.mouseReleased();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return category.mouseScrolled(mouseX, mouseY, scrollY) || object.mouseScrolled(mouseX, mouseY, scrollY);
    }

    public boolean isDropdownOpen() {
        return category.isOpen() || object.isOpen();
    }

    /** Closes whichever popup is open; returns false when none was. */
    public boolean closeDropdowns() {
        if (category.isOpen()) { category.close(); return true; }
        if (object.isOpen())   { object.close();   return true; }
        return false;
    }

    /** Forwards a letter to the open popup; returns false when none is open. */
    public boolean jumpToLetter(char letter) {
        if (category.isOpen()) return category.jumpToLetter(letter);
        if (object.isOpen())   return object.jumpToLetter(letter);
        return false;
    }
}
