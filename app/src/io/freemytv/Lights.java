package io.freemytv;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The LED strip over Bluetooth LE (ELK-BLEDOM controllers, the "duoCo Strip" app). The strip takes one connection at a
 * time, so we connect for a command and hang up a moment later, which leaves the phone app free.
 */
final class Lights {
    private static final UUID[] SERVICES = {uuid(0xFFF0), uuid(0xFFE5)};
    private static final UUID[] WRITES = {uuid(0xFFF3), uuid(0xFFE9)};
    private static final long SCAN_MS = 8_000, IDLE_MS = 20_000, GAP_MS = 40;

    /** The color picker's swatches. */
    static final int[][] COLORS = {{255, 150, 70}, {255, 255, 255}, {255, 0, 0}, {255, 60, 0}, {255, 200, 0}, {120, 255, 0},
            {0, 255, 0}, {0, 255, 200}, {0, 0, 255}, {140, 0, 255}, {255, 0, 110}, {255, 0, 255}};
    static final String[][] COLOR_NAMES = {{"Quente", "Warm"}, {"Branco", "White"}, {"Vermelho", "Red"}, {"Laranja", "Orange"},
            {"Amarelo", "Yellow"}, {"Lima", "Lime"}, {"Verde", "Green"}, {"Ciano", "Cyan"}, {"Azul", "Blue"}, {"Roxo", "Purple"},
            {"Rosa", "Pink"}, {"Magenta", "Magenta"}};
    /** The brightness bar's steps (percent). */
    static final int[] LEVELS = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    /** The saturation bar's steps: 100% is the pure color, less mixes in white. */
    static final int[] SATURATION = {0, 20, 40, 60, 80, 100};

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private static Context app;
    private static BluetoothGatt gatt;
    private static BluetoothGattCharacteristic out;
    private static boolean busy, retried, flushing;

    private Lights() {}

    private static UUID uuid(int short16) {
        return UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", short16));
    }

    // MARK: - Commands

    static byte[] on() { return frame(0x04, 0xF0, 0x00, 0x01, 0xFF, 0x00); }
    static byte[] off() { return frame(0x04, 0x00, 0x00, 0x00, 0xFF, 0x00); }
    static byte[] color(int r, int g, int b) { return new byte[] {0x7E, 0x07, 0x05, 0x03, (byte) r, (byte) g, (byte) b, 0x10, (byte) 0xEF}; }
    static byte[] brightness(int percent) {
        return new byte[] {0x7E, 0x04, 0x01, (byte) Math.max(0, Math.min(100, percent)), 0x01, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xEF};
    }

    private static byte[] frame(int... b) {
        byte[] f = new byte[b.length + 3];
        f[0] = 0x7E;
        f[1] = 0x00;
        for (int i = 0; i < b.length; i++) f[i + 2] = (byte) b[i];
        f[f.length - 1] = (byte) 0xEF;
        return f;
    }

    // MARK: - The chosen strip

    static boolean ready() { return !Prefs.lights("address").isEmpty(); }
    static String name() { return Prefs.lights("name"); }

    static void choose(String name, String address) {
        Prefs.put(new String[][] {{"light_name", name}, {"light_address", address}});
    }

    static void forget() {
        Prefs.put(new String[][] {{"light_name", ""}, {"light_address", ""}});
    }

    /** The saved position of a button (`fallback` until it has been set). */
    static int at(String key, int count, int fallback) {
        try {
            return Math.max(0, Math.min(count - 1, Integer.parseInt(Prefs.lights(key))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** What we last sent (the strip can't tell us); assumed on until the first press. */
    static boolean isOn() { return at("pon", 2, 1) == 1; }

    static void setOn(Context c, boolean on) {
        save("pon", on ? 1 : 0);
        send(c, on ? on() : off());
    }

    static boolean saved(String key) { return !Prefs.lights(key).isEmpty(); }

    static void save(String key, int index) { Prefs.put("light_" + key, String.valueOf(index)); }

    /** A color at a saturation, as the frame to send. */
    static byte[] colorFrame(int color, int saturation) {
        int[] rgb = COLORS[color];
        float s = SATURATION[saturation] / 100f;
        return color(Math.round(rgb[0] * s + 255 * (1 - s)), Math.round(rgb[1] * s + 255 * (1 - s)), Math.round(rgb[2] * s + 255 * (1 - s)));
    }

    static void sendColor(Context c) {
        send(c, colorFrame(at("pcolor", COLORS.length, 0), at("psat", SATURATION.length, SATURATION.length - 1)));
    }

    // MARK: - Finding strips

    interface Found {
        void strip(String name, String address, int rssi);
        void done();
    }

    /** Listens for strips for a few seconds; each one is reported once, the strongest signal being the closest. */
    static void scan(Context context, final Found found) {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        final BluetoothLeScanner scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        if (scanner == null) {
            AppLog.w("lights: Bluetooth is off or missing");
            found.done();
            return;
        }
        final Set<String> seen = new HashSet<>();
        final ScanCallback cb = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult r) {
                String name = r.getScanRecord() == null ? null : r.getScanRecord().getDeviceName();
                if (name == null || !name.toUpperCase().startsWith("ELK-BLE")) return;
                if (seen.add(r.getDevice().getAddress())) found.strip(name, r.getDevice().getAddress(), r.getRssi());
            }
            @Override public void onScanFailed(int code) {
                AppLog.w("lights: scan failed " + code);
            }
        };
        try {
            scanner.startScan(cb);
        } catch (SecurityException e) {
            AppLog.w("lights: no permission: " + e.getMessage());
            found.done();
            return;
        }
        UI.postDelayed(() -> {
            try { scanner.stopScan(cb); } catch (RuntimeException ignored) { }
            found.done();
        }, SCAN_MS);
    }

    // MARK: - Sending

    static void send(Context context, final byte[] command) {
        if (!ready()) return;
        app = context.getApplicationContext();
        UI.post(() -> {
            // A newer color or brightness replaces one still waiting; on and off always go through.
            if (command[1] != 0x00) {
                final byte kind = command[1];
                queue.removeIf(q -> q[1] == kind);
            }
            queue.add(command);
            if (busy) {
                if (out != null && !flushing) flush();
                return;
            }
            busy = true;
            retried = false;
            connect();
        });
    }

    /** Connects ahead of a command (the home screen calls this when you reach the lights), so the first press isn't slow. */
    static void warm(Context context) {
        if (!ready()) return;
        app = context.getApplicationContext();
        UI.post(() -> {
            if (busy) return;
            busy = true;
            retried = false;
            connect();
        });
    }

    private static final Runnable HANG_UP = () -> {
        if (queue.isEmpty() && gatt != null) gatt.disconnect();
    };

    private static void connect() {
        BluetoothManager bm = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        try {
            BluetoothDevice device = bm.getAdapter().getRemoteDevice(Prefs.lights("address"));
            gatt = device.connectGatt(app, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
                    if (state == BluetoothGatt.STATE_CONNECTED) {
                        g.discoverServices();
                    } else if (state == BluetoothGatt.STATE_DISCONNECTED) {
                        UI.post(() -> {
                            boolean worked = out != null;
                            close();
                            if (!worked && !retried) {   // the first try often fails while the strip wakes up
                                retried = true;
                                busy = true;
                                connect();
                            } else if (!worked) {
                                AppLog.w("lights: could not connect (status " + status + ")");
                                queue.clear();
                            }
                        });
                    }
                }

                @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                    for (int i = 0; i < SERVICES.length && out == null; i++) {
                        BluetoothGattService s = g.getService(SERVICES[i]);
                        if (s != null) out = s.getCharacteristic(WRITES[i]);
                    }
                    if (out == null) {
                        AppLog.w("lights: no control channel");
                        g.disconnect();
                        return;
                    }
                    UI.post(Lights::flush);
                }
            }, BluetoothDevice.TRANSPORT_LE);
        } catch (RuntimeException e) {   // includes SecurityException and a bad address
            AppLog.w("lights: " + e.getMessage());
            queue.clear();
            close();
        }
    }

    private static void flush() {
        if (gatt == null || out == null) return;
        UI.removeCallbacks(HANG_UP);
        byte[] next = queue.poll();
        if (next != null) {
            flushing = true;
            out.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            out.setValue(next);
            gatt.writeCharacteristic(out);
            UI.postDelayed(Lights::flush, GAP_MS);   // the controller drops frames that arrive back to back
        } else {
            flushing = false;
            UI.postDelayed(HANG_UP, IDLE_MS);   // stays connected a while: the next press is instant
        }
    }

    private static void close() {
        if (gatt != null) {
            try { gatt.close(); } catch (RuntimeException ignored) { }
        }
        gatt = null;
        out = null;
        busy = false;
        flushing = false;
        UI.removeCallbacks(HANG_UP);
        if (!queue.isEmpty()) {   // something was asked while we were hanging up
            busy = true;
            retried = false;
            connect();
        }
    }
}
