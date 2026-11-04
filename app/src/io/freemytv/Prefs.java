package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings changed on the TV itself (MinimalOS > Settings). config.properties only gives the first
 * values; anything the user changes here wins. Read from the :home process (home screen, screensaver, settings).
 */
final class Prefs {
    private Prefs() {}

    /** Remote keys we handle, with the app each opens until the user picks another. Codes read from the TCL's log. */
    static final int KEY_NETFLIX = 4062, KEY_YOUTUBE = 190, KEY_PRIME = 4099, KEY_TCL_CHANNEL = 4020;
    /** Vestel's Netflix button: STEM_1, which the TV hands to its Netflix key receiver (dumpsys window policy). */
    static final int KEY_VESTEL_NETFLIX = KeyEvent.KEYCODE_STEM_1;
    static final int[] KEYS = RemoteKeys.VESTEL ? new int[] {KEY_VESTEL_NETFLIX}
            : new int[] {KEY_NETFLIX, KEY_YOUTUBE, KEY_PRIME, KEY_TCL_CHANNEL};

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;

    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
        migrateOld();
        migrateKeys();   // before anything reads the button choices
        Ui.applyLanguage();   // the language picked on the TV, now that it can be read
    }

    /** MinimalOS kept its settings in "preferences" before they joined the shared "settings" file: copied over once. */
    private static void migrateOld() {
        SharedPreferences old = app.getSharedPreferences("preferences", Context.MODE_PRIVATE);
        if (old.getAll().isEmpty() || sp().getBoolean("migratedOld", false)) return;
        SharedPreferences.Editor e = sp().edit();
        for (java.util.Map.Entry<String, ?> kv : old.getAll().entrySet()) {
            if (!sp().contains(kv.getKey()) && kv.getValue() instanceof String) e.putString(kv.getKey(), (String) kv.getValue());
        }
        e.putBoolean("migratedOld", true).apply();
    }

    private static SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        sp().edit().putString(key, value).apply();
        version++;
    }

    private static void put(String key, boolean value) {
        sp().edit().putBoolean(key, value).apply();
        version++;
    }

    // MARK: - Language and units

    /** "auto", "pt", "en", "es", "de", "ru" or "fr". */
    static String language() {
        String v = app == null ? Config.LANGUAGE : sp().getString("language", Config.LANGUAGE);
        v = v.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("ru") ? "ru" : v.startsWith("pt") ? "pt" : v.startsWith("es") ? "es" : v.startsWith("de") ? "de"
                : v.startsWith("fr") ? "fr" : v.startsWith("en") ? "en" : "auto";
    }

    static void setLanguage(String v) { put("language", v); Ui.applyLanguage(); }

    /** "auto", "C" or "F". */
    static String temperature() {
        String v = sp().getString("temperature", Config.TEMPERATURE).trim().toUpperCase(Locale.ROOT);
        return v.startsWith("F") ? "F" : v.startsWith("C") ? "C" : "auto";
    }

    static void setTemperature(String v) { put("temperature", v); }

    /** "auto" (dark from sunset to sunrise), "light" or "dark". */
    static String theme() {
        String v = sp().getString("theme", "auto");
        return v.equals("light") || v.equals("dark") ? v : "auto";
    }

    static void setTheme(String v) { put("theme", v); }

    // MARK: - City

    static String city() { return sp().getString("city", Config.CITY).trim(); }
    static String latitude() { return sp().getString("latitude", Config.LATITUDE); }
    static String longitude() { return sp().getString("longitude", Config.LONGITUDE); }

    static void setCity(String name, String latitude, String longitude) {
        sp().edit().putString("city", name).putString("latitude", latitude).putString("longitude", longitude).apply();
        version++;
    }

    // MARK: - MinimalOS home (Apps style, backgrounds, screensaver, lights)

    private static String getp(String key, String fallback) {
        return app == null ? fallback : sp().getString(key, fallback);
    }

    static String currency() { return getp("currency", Config.CURRENCY); }

    static String region() { return getp("region", Config.TMDB_REGION); }
    /** "apps" (the app grid on a color glow) or "foryou" (picks and cards). */
    static String homeStyle() { return getp("homeStyle", "apps"); }

    static final int SHELF_MIN = 4, SHELF_MAX = 12, SHELF_DEFAULT = 6;

    /** How many apps the shelf holds (Settings → Preferences). */
    static int shelfSize() {
        try {
            return Math.max(SHELF_MIN, Math.min(SHELF_MAX, Integer.parseInt(getp("shelfSize", String.valueOf(SHELF_DEFAULT)))));
        } catch (NumberFormatException e) {
            return SHELF_DEFAULT;
        }
    }

    /** Whether the "All apps" grid under the favorites is open. */
    static boolean allAppsOpen() { return getp("allApps", "1").equals("1"); }

    /** How "All apps" is ordered: "recent" (the last opened first) or "az". */
    static boolean allAppsAz() { return getp("allAppsSort", "recent").equals("az"); }

    /** The Apps home's background: "dynamic" (the focused app's color), "static" (one color) or "image". */
    static String bgMode() {
        String m = getp("bgMode", "dynamic");
        return m.equals("static") || m.equals("image") ? m : "dynamic";
    }

    /** Where the Images background gets its pictures: "both", "mine" (sent from the phone) or "art" (show and movie art). */
    static String bgSource() {
        String s = getp("bgSource", "both");
        return s.equals("mine") || s.equals("art") ? s : "both";
    }

    /** How the Images background goes through its pictures: "order" (one after the other) or "random". */
    static boolean bgRandom() { return getp("bgOrder", "order").equals("random"); }

    static final String[] BG_EVERY = {"15", "30", "60", "120", "300"}, BG_FADE = {"1", "2", "4", "8"};

    /** Seconds each Images background picture stays (Settings → Preferences → Background). */
    static int bgEvery() { return seconds("bgEvery", "60", BG_EVERY); }

    /** Seconds the crossfade to the next picture takes. */
    static int bgFade() { return seconds("bgFade", "2", BG_FADE); }

    /** What the screensaver shows: "mixed", "art" (movie and show art from TMDB) or "mine" (your photos). */
    static String standbySource() {
        String s = getp("standbySource", "mixed");
        return s.equals("art") || s.equals("mine") ? s : "mixed";
    }

    /** The screensaver's order: "order" or "random". */
    static boolean standbyRandom() { return getp("standbyOrder", "order").equals("random"); }

    private static int seconds(String key, String fallback, String[] allowed) {
        String v = getp(key, fallback);
        for (String a : allowed) if (a.equals(v)) return Integer.parseInt(v);
        return Integer.parseInt(fallback);
    }

    /** The static background's color, as RRGGBB. */
    static String bgColor() { return getp("bgColor", "3A5BD9"); }

    /** The app that was selected last on the home screen (its package), so the home comes back to it. */
    /** The LED strip: its saved name and address, and where the color and brightness buttons are. */
    static String lights(String key) { return getp("light_" + key, ""); }

    /** The app the Live TV button opens (package); empty = the TV's own. */
    static String liveApp() { return getp("liveApp", ""); }

    static String lastApp() { return getp("lastApp", ""); }


    /** Settings that change what the screens show (language, place, units): the home screen starts over on the next resume. */
    private static final java.util.Set<String> REDRAW = new java.util.HashSet<>(java.util.Arrays.asList(
            "language", "city", "latitude", "longitude", "region", "currency", "temperature", "theme"));

    /** Saves one value. Only those in REDRAW make the screens draw everything again. */
    static void set(String key, String value) {
        sp().edit().putString(key, value).apply();
        if (REDRAW.contains(key)) version++;
    }

    /** Several at once (the city brings its coordinates, country and currency along). */
    static void set(String[][] pairs) {
        SharedPreferences.Editor e = sp().edit();
        boolean redraw = false;
        for (String[] kv : pairs) {
            e.putString(kv[0], kv[1]);
            redraw |= REDRAW.contains(kv[0]);
        }
        e.apply();
        if (redraw) version++;
    }

    // MARK: - Widgets and coins

    static boolean weather() { return sp().getBoolean("weather", true); }
    static void setWeather(boolean on) { put("weather", on); }

    static boolean quotes() { return sp().getBoolean("quotes", Config.SHOW_CRYPTO); }
    static void setQuotes(boolean on) { put("quotes", on); }

    static boolean coin(int i) { return sp().getBoolean("coin" + i, true); }
    static void setCoin(int i, boolean on) { put("coin" + i, on); }

    // MARK: - Shelf

    /** Packages on the home shelf, in order; null until the user edits it (then the built-in list applies). */
    static List<String> shelf() {
        String v = sp().getString("shelf", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setShelf(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("shelf", sb.toString());
    }

    // MARK: - Remote buttons

    private static String defaultApp(int keyCode) {
        switch (keyCode) {
            case KEY_NETFLIX: case KEY_VESTEL_NETFLIX: return "com.netflix.ninja";
            case KEY_YOUTUBE: return "com.google.android.youtube.tv";
            case KEY_PRIME: return "com.amazon.amazonvideo.livingroom";
            case KEY_TCL_CHANNEL: return "";
            default: return "";
        }
    }

    // The key service runs in its own small process (:keys), so that a busy home screen can never delay the TV's keys.
    // SharedPreferences are not shared between processes, so these few values live in a tiny file both processes read.
    // The service asks on every key: the values stay in memory and the file is read again only when it changes.
    private static File keysFile() {
        return new File(app.getFilesDir(), "remote-keys.txt");
    }

    private static final String[] keyApps = new String[KEYS.length];   // null = the default
    private static long keysStamp = Long.MIN_VALUE;

    /** A temporary file per process (two can start together after an update), synced before it replaces the old one. */
    private static boolean writeKeys(String text) {
        File tmp = new File(app.getFilesDir(), "remote-keys." + android.os.Process.myPid() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return tmp.renameTo(keysFile());
    }

    /** Choices saved before they moved to the file (in SharedPreferences) are copied there once. */
    private static void migrateKeys() {
        if (keysFile().exists()) return;
        SharedPreferences old = sp();
        boolean any = false;
        for (int key : KEYS) any |= old.contains("key" + key);
        if (!any) return;
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(old.getString("key" + key, defaultApp(key))).append('\n');
        writeKeys(sb.toString());
    }

    /** Package the key opens ("" = nothing). */
    static String keyApp(int keyCode) {
        synchronized (keyApps) {
            File f = keysFile();
            long stamp = f.lastModified() * 31 + f.length();   // 0 without the file: every key opens its default
            if (stamp != keysStamp) {
                keysStamp = stamp;
                java.util.Arrays.fill(keyApps, null);
                if (stamp != 0) {
                    try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            for (int i = 0; i < KEYS.length; i++) {
                                String prefix = KEYS[i] + "=";
                                if (line.startsWith(prefix)) keyApps[i] = line.substring(prefix.length());
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == keyCode && keyApps[i] != null) return keyApps[i];
            return defaultApp(keyCode);
        }
    }

    static void setKeyApp(int keyCode, String pkg) {
        StringBuilder sb = new StringBuilder();
        for (int key : KEYS) sb.append(key).append('=').append(key == keyCode ? pkg : keyApp(key)).append('\n');
        if (writeKeys(sb.toString())) version++;
    }
}
