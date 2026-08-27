package ru.fifth.horror.trigger;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Session-local per-director selection of trigger zones to visualize. */
public final class TriggerVisualizationSelection {
    private boolean all;
    private final Set<String> included = new HashSet<>();
    private final Set<String> excluded = new HashSet<>();

    public void showAll() {
        all = true;
        included.clear();
        excluded.clear();
    }

    public void hideAll() {
        all = false;
        included.clear();
        excluded.clear();
    }

    public void show(String id) {
        String key = key(id);
        if (all) excluded.remove(key);
        else included.add(key);
    }

    public void hide(String id) {
        String key = key(id);
        if (all) excluded.add(key);
        else included.remove(key);
    }

    public boolean includes(String id) {
        String key = key(id);
        return all ? !excluded.contains(key) : included.contains(key);
    }

    public boolean isEmpty() {
        return !all && included.isEmpty();
    }

    private static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
