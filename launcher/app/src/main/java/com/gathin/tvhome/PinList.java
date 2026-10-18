package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PinList {

    static final String PLEX = "com.plexapp.android";

    private static final String PREFS = "tvhome_pins";
    private static final String KEY_PINS = "pins";
    private static final String KEY_SEEDED = "seeded";
    private static final String KEY_SEEN = "seen";

    private final SharedPreferences prefs;
    private final List<String> dockApps;
    private List<String> packages;
    private final Set<String> seen;

    PinList(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        dockApps = Collections.unmodifiableList(
                Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        seen = new LinkedHashSet<>(prefs.getStringSet(KEY_SEEN, Collections.emptySet()));
        seedIfNeeded();
        packages = load();
    }

    List<String> snapshot() {
        return new ArrayList<>(packages);
    }

    boolean contains(String pkg) {
        return packages.contains(pkg);
    }

    int size() {
        return packages.size();
    }

    int indexOf(String pkg) {
        return packages.indexOf(pkg);
    }

    void pinToTop(String pkg) {
        packages = PinList.pinToTop(packages, pkg);
        seen.add(pkg);
        persist();
    }

    void unpin(String pkg) {
        packages = PinList.unpin(packages, pkg);
        persist();
    }

    void restore(List<String> pins) {
        packages = new ArrayList<>(pins);
        persist();
    }

    boolean move(String pkg, int delta, Set<String> onScreen) {
        List<String> next = PinList.move(packages, pkg, delta, onScreen);
        if (next.equals(packages)) return false;
        packages = next;
        persist();
        return true;
    }

    void observeInstalled(Set<String> installed) {
        boolean seenChanged = seen.addAll(installed);
        List<String> next = PinList.pruneGone(packages, seen, installed);
        if (!seenChanged && next.equals(packages)) return;
        packages = next;
        persist();
    }

    void remove(String pkg) {
        if (!packages.contains(pkg)) return;
        packages = PinList.unpin(packages, pkg);
        persist();
    }

    void sort(List<AppAdapter.Entry> apps) {
        final List<String> pins = packages;
        final List<String> dock = dockApps;
        Collections.sort(apps, (a, b) -> {
            int pa = pins.indexOf(a.pkg), pb = pins.indexOf(b.pkg);
            if (pa >= 0 || pb >= 0) {
                if (pa < 0) return 1;
                if (pb < 0) return -1;
                return Integer.compare(pa, pb);
            }
            int fa = dock.indexOf(a.pkg), fb = dock.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
    }

    static List<String> seed(List<String> dockApps) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(PLEX);
        for (String pkg : dockApps) {
            if (pkg != null && !pkg.isEmpty()) out.add(pkg);
        }
        return new ArrayList<>(out);
    }

    static List<String> pinToTop(List<String> pins, String pkg) {
        ArrayList<String> out = new ArrayList<>();
        out.add(pkg);
        for (String p : pins) {
            if (!p.equals(pkg)) out.add(p);
        }
        return out;
    }

    static List<String> unpin(List<String> pins, String pkg) {
        ArrayList<String> out = new ArrayList<>();
        for (String p : pins) {
            if (!p.equals(pkg)) out.add(p);
        }
        return out;
    }

    static List<String> move(List<String> pins, String pkg, int delta, Set<String> onScreen) {
        ArrayList<String> out = new ArrayList<>(pins);
        int i = out.indexOf(pkg);
        if (i < 0) return out;
        int dir = delta < 0 ? -1 : 1;
        int j = i + dir;
        while (j >= 0 && j < out.size()) {
            if (onScreen == null || onScreen.contains(out.get(j))) {
                Collections.swap(out, i, j);
                return out;
            }
            j += dir;
        }
        return out;
    }

    static List<String> pruneGone(List<String> pins, Set<String> seen, Set<String> installed) {
        ArrayList<String> out = new ArrayList<>();
        for (String p : pins) {
            if (seen.contains(p) && !installed.contains(p)) continue;
            out.add(p);
        }
        return out;
    }

    private void seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return;
        packages = seed(dockApps);
        persist();
    }

    private List<String> load() {
        String raw = prefs.getString(KEY_PINS, "");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String s : raw.split("\n", -1)) {
                if (!s.isEmpty()) out.add(s);
            }
        }
        return new ArrayList<>(out);
    }

    private void persist() {
        prefs.edit()
                .putString(KEY_PINS, TextUtils.join("\n", packages))
                .putStringSet(KEY_SEEN, new HashSet<>(seen))
                .putBoolean(KEY_SEEDED, true)
                .apply();
    }
}
