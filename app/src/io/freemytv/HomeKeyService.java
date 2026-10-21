package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS has no home screen chooser: Amazon's launcher always wins. So, in Free My TV mode, this service watches for
 * Amazon's home screen coming to the front (Home button, or Back out of an app) and opens ours instead. It only
 * sees which window is on top: no screen content, no text, no keys (see res/xml/home_key_service.xml).
 * Runs in the main process. The service being on is the mode being on; "Original" turns it off (disableSelf).
 */
public class HomeKeyService extends AccessibilityService {
    static final String COMPONENT = HomeMode.PKG + "/" + HomeMode.PKG + ".HomeKeyService";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final long COOLDOWN = 1500;
    private static volatile HomeKeyService instance;
    private long last;

    static boolean running() {
        return instance != null;
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || cls == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        if (!cls.toString().contains("HomeActivity") || Watchdog.crashedTooOften(this)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - last < COOLDOWN) return;
        last = now;
        openHome(this);
    }

    @Override public void onInterrupt() {}
}
