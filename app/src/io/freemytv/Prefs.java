package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings changed on the TV (Settings → Preferences). Whatever wasn't changed there comes from config.properties
 * (through Config), so a TV that was never touched behaves exactly as before. Used in the :home process.
 */
final class Prefs {
    private Prefs() {}

    private static volatile SharedPreferences sp;

    static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("preferences", Context.MODE_PRIVATE);
    }

    private static String get(String key, String fallback) {
        SharedPreferences p = sp;
        return p == null ? fallback : p.getString(key, fallback);
    }

    static String language() { return get("language", Config.LANGUAGE); }
    static String city() { return get("city", Config.CITY); }
    static String latitude() { return get("latitude", Config.LATITUDE); }
    static String longitude() { return get("longitude", Config.LONGITUDE); }
    static String region() { return get("region", Config.TMDB_REGION); }
    static String temperature() { return get("temperature", Config.TEMPERATURE); }
    static String currency() { return get("currency", Config.CURRENCY); }
    /** "apps" (the app grid on a color glow) or "foryou" (picks and cards). */
    static String homeStyle() { return get("homeStyle", "apps"); }

    static final int SHELF_MIN = 4, SHELF_MAX = 12, SHELF_DEFAULT = 6;

    /** How many apps the shelf holds (Settings → Preferences). */
    static int shelfSize() {
        try {
            return Math.max(SHELF_MIN, Math.min(SHELF_MAX, Integer.parseInt(get("shelfSize", String.valueOf(SHELF_DEFAULT)))));
        } catch (NumberFormatException e) {
            return SHELF_DEFAULT;
        }
    }

    /** Whether the "All apps" grid under the favorites is open. */
    static boolean allAppsOpen() { return get("allApps", "1").equals("1"); }

    /** How "All apps" is ordered: "recent" (the last opened first) or "az". */
    static boolean allAppsAz() { return get("allAppsSort", "recent").equals("az"); }

    /** The Apps home's background: "dynamic" (the focused app's color), "static" (one color) or "image". */
    static String bgMode() {
        String m = get("bgMode", "dynamic");
        return m.equals("static") || m.equals("image") ? m : "dynamic";
    }

    /** Where the Images background gets its pictures: "both", "mine" (sent from the phone) or "art" (show and movie art). */
    static String bgSource() {
        String s = get("bgSource", "both");
        return s.equals("mine") || s.equals("art") ? s : "both";
    }

    /** The static background's color, as RRGGBB. */
    static String bgColor() { return get("bgColor", "3A5BD9"); }

    static void put(String key, String value) {
        SharedPreferences p = sp;
        if (p != null) p.edit().putString(key, value).apply();
    }

    /** Several at once (the city brings its coordinates and country along). */
    static void put(String[][] pairs) {
        SharedPreferences p = sp;
        if (p == null) return;
        SharedPreferences.Editor e = p.edit();
        for (String[] kv : pairs) e.putString(kv[0], kv[1]);
        e.apply();
    }
}
