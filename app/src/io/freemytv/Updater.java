package io.freemytv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Updates from GitHub releases: asks the repository in config.properties (update_repo=owner/name) for its latest release,
 * and if its tag is newer than this app's version, downloads the release's .apk and hands it to Android's installer (one
 * confirmation on the TV; the system only accepts an APK signed with the same key as the installed app).
 */
final class Updater {
    private Updater() {}

    static final class Info {
        String version = "", notes = "", url = "";
        long size;
    }

    interface Progress { void percent(int percent); }

    static boolean configured() {
        return Config.UPDATE_REPO.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+");
    }

    static String installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    /** The newest release of the repository (network: not on the UI thread). */
    static Info latest() throws Exception {
        String body = fetch("https://api.github.com/repos/" + Config.UPDATE_REPO + "/releases/latest", "application/vnd.github+json");
        JSONObject o = new JSONObject(body);
        Info info = new Info();
        info.version = o.optString("tag_name").replaceFirst("^[vV]", "");
        info.notes = o.optString("body").trim();
        JSONArray assets = o.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (a.optString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                info.url = a.optString("browser_download_url");
                info.size = a.optLong("size");
                break;
            }
        }
        if (info.version.isEmpty()) throw new IOException("no release");
        return info;
    }

    /** True when `remote` (like "1.2" or "1.10.1") is a later version than `local`. */
    static boolean newer(String remote, String local) {
        String[] r = remote.split("[^0-9]+"), l = local.split("[^0-9]+");
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int a = i < r.length && !r[i].isEmpty() ? Integer.parseInt(r[i]) : 0;
            int b = i < l.length && !l[i].isEmpty() ? Integer.parseInt(l[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    /** Downloads the release's APK into the app's cache (only from GitHub's own hosts). */
    static File download(Context c, Info info, Progress progress) throws Exception {
        URL url = new URL(info.url);
        String host = url.getHost();
        if (!url.getProtocol().equals("https") || !(host.equals("github.com") || host.endsWith(".github.com")
                || host.endsWith(".githubusercontent.com"))) {
            throw new IOException("not a GitHub address");
        }
        File dir = new File(c.getCacheDir(), "updates");
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        File out = new File(dir, "update.apk");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "MinimalOS-Updater");
        try (InputStream in = conn.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            long total = info.size > 0 ? info.size : conn.getContentLengthLong();
            byte[] buf = new byte[32768];
            long done = 0;
            int last = -1;
            for (int n; (n = in.read(buf)) > 0;) {
                os.write(buf, 0, n);
                done += n;
                int pct = total > 0 ? (int) (done * 100 / total) : 0;
                if (pct != last) {
                    last = pct;
                    progress.percent(pct);
                }
            }
        } finally {
            conn.disconnect();
        }
        if (info.size > 0 && out.length() != info.size) throw new IOException("incomplete download");
        return out;
    }

    /** Hands the APK to Android's installer: the TV asks once, then the app restarts on the new version. */
    static void install(Context c, File apk) throws IOException {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        int id = installer.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (OutputStream out = session.openWrite("update", 0, apk.length()); InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[32768];
                for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent result = new Intent(c, UpdateReceiver.class).setAction(UpdateReceiver.ACTION);
            PendingIntent pending = PendingIntent.getBroadcast(c, id, result, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pending.getIntentSender());
        }
    }

    private static String fetch(String address, String accept) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(20_000);
        conn.setRequestProperty("Accept", accept);
        conn.setRequestProperty("User-Agent", "MinimalOS-Updater");
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
