package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/** Fills the category and object filter dropdowns shared by the sound screens. */
final class SoundFilterOptions {

    private SoundFilterOptions() {}

    /** Visible categories, sorted by their translated label. */
    static void populateCategories(FilterDropdown dropdown) {
        List<String[]> pairs = new ArrayList<>();
        for (SoundCategory cat : SoundCategory.visibleCategories())
            pairs.add(new String[]{ cat.getDropdownKey(), I18n.get(cat.getLabelKey()) });
        pairs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        List<String> options = new ArrayList<>(), labels = new ArrayList<>();
        for (String[] p : pairs) { options.add(p[0]); labels.add(p[1]); }
        dropdown.setOptions(options, labels);
    }

    /** Objects of a category, sorted by label with digits after Z (char '~' > 'Z' in ASCII). */
    static void populateObjects(FilterDropdown dropdown, SoundCategory category) {
        List<String> raw    = new ArrayList<>(SoundRegistry.getObjectsByCategory(category));
        List<String> labels = new ArrayList<>();
        for (String obj : raw)
            labels.add(SoundDisplayHelper.getObjectName("minecraft:" + category.getPrefix() + "." + obj));
        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) order.add(new int[]{i});
        order.sort((a, b) -> {
            String la = labels.get(a[0]), lb = labels.get(b[0]);
            String ka = (!la.isEmpty() && Character.isDigit(la.charAt(0))) ? "~" + la : la;
            String kb = (!lb.isEmpty() && Character.isDigit(lb.charAt(0))) ? "~" + lb : lb;
            return ka.compareToIgnoreCase(kb);
        });
        List<String> sortedRaw = new ArrayList<>(), sortedLabels = new ArrayList<>();
        for (int[] idx : order) { sortedRaw.add(raw.get(idx[0])); sortedLabels.add(labels.get(idx[0])); }
        dropdown.setOptions(sortedRaw, sortedLabels);
    }
}
