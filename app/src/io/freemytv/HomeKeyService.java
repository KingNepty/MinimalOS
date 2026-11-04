package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS has no home screen chooser: Amazon's launcher always wins. So, in Free My TV mode, this service watches for
 * Amazon's home screen coming to the front (Home button, or Back out of an app) and opens ours instead. It only
 * sees which window is on top (no screen content, no text), and the Home key, which it takes itself so Amazon's slow
 * launcher (about 3 s) never shows: a short press opens ours at once; holding it opens Amazon's home. Other keys pass
 * through untouched (see res/xml/home_key_service.xml).
 * Runs in the main process. The service being on is the mode being on; "Original" turns it off (disableSelf).
 */
public class HomeKeyService extends AccessibilityService {
    static final String COMPONENT = HomeMode.PKG + "/" + HomeMode.PKG + ".HomeKeyService";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final long COOLDOWN = 1500;
    private static volatile HomeKeyService instance;
    private long last;
    private static volatile long pausedUntil;
    private static final long LONG_PRESS = 600;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeLong;
    private final Runnable holdHome = new Runnable() {
        @Override public void run() {
            homeLong = true;
            pauseRedirect(8_000);
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setClassName(AMAZON_LAUNCHER, AMAZON_LAUNCHER + ".ui.HomeActivity_vNext")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                AppLog.w("Amazon's home: " + e.getMessage());
            }
        }
    };

    /** Lets Amazon's own screens (its settings) stay in front for a while instead of sending the person back to our home. */
    static void pauseRedirect(long ms) {
        pausedUntil = SystemClock.elapsedRealtime() + ms;
    }

    static boolean running() {
        return instance != null;
    }

    /**
     * Android turns this service off whenever the app is replaced (an update) and some TVs lose it at a reboot. With the
     * one-time permission grant (adb shell pm grant io.freemytv android.permission.WRITE_SECURE_SETTINGS, in the setup
     * command) the app turns it back on itself, as long as MinimalOS mode is the one the person chose.
     */
    static void enableSelf(Context c) {
        try {
            if (running() || !HomeMode.FREE.equals(HomeMode.state(c).optString("mode"))) return;
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            android.content.ContentResolver r = c.getContentResolver();
            String key = "enabled_accessibility_services", now = android.provider.Settings.Secure.getString(r, key);
            if (now == null || now.isEmpty()) now = COMPONENT;
            else if (!now.contains(COMPONENT)) now += ":" + COMPONENT;
            android.provider.Settings.Secure.putString(r, key, now);
            android.provider.Settings.Secure.putInt(r, "accessibility_enabled", 1);
        } catch (RuntimeException e) {
            AppLog.w("turn the service back on: " + e.getMessage());
        }
    }

    /** Turns the service off (only it can: Android has no other way). Returns false if it wasn't on. */
    static boolean turnOff() {
        HomeKeyService s = instance;
        if (s == null) return false;
        s.disableSelf();
        instance = null;
        return true;
    }

    /** Opens our home screen now (from the service, which Android lets start screens from the background). */
    static void openHome(Context fallback) {
        Context c = instance != null ? instance : fallback;
        c.startActivity(new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override protected void onServiceConnected() {
        instance = this;
        HomeMode.fireService(this, true);
        reopenAfterUpdate();
    }

    /**
     * After an update the app was stopped by the installer and the TV is left on the installer or Amazon's home screen:
     * when this service comes back on a different version than last time, open our home screen right away.
     */
    private void reopenAfterUpdate() {
        try {
            long now = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            android.content.SharedPreferences p = getSharedPreferences("version", MODE_PRIVATE);
            long before = p.getLong("code", 0);
            p.edit().putLong("code", now).apply();
            if (before != 0 && before != now) openHome(this);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
            AppLog.w("reopen after update: " + e.getMessage());
        }
    }

    @Override public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        HomeMode.fireService(this, false);
        return super.onUnbind(intent);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (Watchdog.crashedTooOften(this) || SystemClock.elapsedRealtime() < pausedUntil) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            AppLog.w("Home key caught");
            if (e.getRepeatCount() == 0) {
                homeLong = false;
                handler.postDelayed(holdHome, LONG_PRESS);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(holdHome);
            if (!homeLong) {
                last = SystemClock.elapsedRealtime();
                openHome(this);
            }
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        AppLog.w("Amazon launcher window: " + cls);   // for diagnosing other Fire OS versions
        // Any window of Amazon's launcher counts (its class name varies between Fire OS versions), except dialogs/popups
        // drawn by framework widgets that aren't a screen of their own.
        String name = cls == null ? "" : cls.toString();
        if (name.startsWith("android.widget.") && !name.endsWith("FrameLayout")) return;
        if (Watchdog.crashedTooOften(this)) return;
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil || now - last < COOLDOWN) return;
        last = now;
        openHome(this);
    }

    @Override public void onInterrupt() {}
}
