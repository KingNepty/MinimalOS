package io.freemytv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** System notices for the main process: the TV booted, or this app was updated. Not exported. */
public class HomeReceiver extends BroadcastReceiver {
    /** From the home screen (:home): a TV settings screen is about to open, don't redirect to us for a few seconds. */
    static final String PAUSE = "io.freemytv.PAUSE_REDIRECT";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            HomeKeyService.enableSelf(context);
            HomeMode.bootCheck(context);
        } else if (PAUSE.equals(action)) {
            HomeKeyService.pauseRedirect(12_000);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            HomeKeyService.enableSelf(context);   // the update turned the service off
            HomeMode.verify(context);   // app updated: complete MinimalOS mode if something is missing
            // And get back to our home screen (Android may refuse a start from the background; on Fire OS the
            // accessibility service does the same when it comes back, see HomeKeyService).
            try {
                context.startActivity(new Intent(context, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                AppLog.w("reopen after update: " + e.getMessage());
            }
        }
    }
}
