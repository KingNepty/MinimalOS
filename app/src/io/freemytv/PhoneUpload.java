package io.freemytv;

import android.content.Context;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny web page on the TV for sending pictures from a phone (same Wi-Fi): the phone opens http://<TV>:<port>, types the
 * 4-digit code shown on the TV, and picks photos. They are shrunk in the phone's browser and kept in the app's own
 * storage (filesDir/wallpapers), where the Images background finds them. It only runs while its screen is open.
 */
final class PhoneUpload {
    private static final int MAX_PICTURES = 60, MAX_BYTES = 10 * 1024 * 1024;

    final String code = String.format("%04d", new SecureRandom().nextInt(10000));
    private final Context context;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private volatile ServerSocket server;
    private volatile boolean running;
    int port;

    PhoneUpload(Context c) {
        context = c.getApplicationContext();
    }

    static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "wallpapers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static List<File> pictures(Context c) {
        List<File> out = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files != null) for (File f : files) if (f.isFile() && f.getName().endsWith(".jpg")) out.add(f);
        Collections.sort(out);
        return out;
    }

    /** Starts listening; false if no port is free. */
    boolean start() {
        for (int p = 8080; p < 8090 && server == null; p++) {
            try {
                server = new ServerSocket(p);
                port = p;
            } catch (IOException ignored) {
            }
        }
        if (server == null) return false;
        running = true;
        final ServerSocket s = server;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    final Socket socket = s.accept();
                    pool.execute(() -> handle(socket));
                } catch (IOException e) {
                    if (running) AppLog.w("phone upload: " + e.getMessage());
                }
            }
        }, "phone-upload");
        t.setDaemon(true);
        t.start();
        return true;
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        server = null;
        pool.shutdownNow();
    }

    /** The TV's address on the local network (Wi-Fi or Ethernet), or null. */
    static String ipAddress() {
        try {
            String fallback = null;
            for (Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces(); n != null && n.hasMoreElements();) {
                NetworkInterface ni = n.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements();) {
                    InetAddress ia = a.nextElement();
                    if (!(ia instanceof Inet4Address) || !ia.isSiteLocalAddress()) continue;
                    String name = ni.getName();
                    if (name.startsWith("wlan") || name.startsWith("eth")) return ia.getHostAddress();
                    if (fallback == null) fallback = ia.getHostAddress();
                }
            }
            return fallback;
        } catch (IOException e) {
            return null;
        }
    }

    // MARK: - HTTP

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(20_000);
            InputStream in = new java.io.BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            String request = line(in);
            if (request == null || request.isEmpty()) return;
            String[] parts = request.split(" ");
            if (parts.length < 2) return;
            int length = 0;
            for (String h; (h = line(in)) != null && !h.isEmpty();) {
                if (h.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                    try {
                        length = Integer.parseInt(h.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            String method = parts[0], target = parts[1];
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            if (path.equals("/") && method.equals("GET")) {
                send(out, 200, "text/html; charset=utf-8", page().getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!path.startsWith("/api/")) {
                send(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!code.equals(param(query, "code"))) {
                send(out, 403, "text/plain", "Wrong code".getBytes(StandardCharsets.UTF_8));
                return;
            }
            switch (path) {
                case "/api/list": {
                    StringBuilder sb = new StringBuilder("[");
                    for (File f : pictures(context)) sb.append(sb.length() > 1 ? "," : "").append('"').append(f.getName()).append('"');
                    send(out, 200, "application/json", sb.append(']').toString().getBytes(StandardCharsets.UTF_8));
                    return;
                }
                case "/api/img": {
                    File f = named(param(query, "name"));
                    if (f == null) {
                        send(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream fin = new FileInputStream(f)) {
                        byte[] buf = new byte[16384];
                        for (int n; (n = fin.read(buf)) > 0;) bytes.write(buf, 0, n);
                    }
                    send(out, 200, "image/jpeg", bytes.toByteArray());
                    return;
                }
                case "/api/upload": {
                    if (!method.equals("POST") || length <= 0 || length > MAX_BYTES) {
                        send(out, 413, "text/plain", "Too big".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    if (pictures(context).size() >= MAX_PICTURES) {
                        send(out, 409, "text/plain", "Full".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    File tmp = new File(dir(context), "incoming.tmp");
                    try (FileOutputStream fo = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[16384];
                        int left = length;
                        while (left > 0) {
                            int n = in.read(buf, 0, Math.min(buf.length, left));
                            if (n < 0) throw new IOException("short body");
                            fo.write(buf, 0, n);
                            left -= n;
                        }
                    }
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(tmp.getPath(), o);
                    if (o.outWidth <= 0) {
                        tmp.delete();
                        send(out, 415, "text/plain", "Not a picture".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    File dest = new File(dir(context), "u-" + System.currentTimeMillis() + "-" + new SecureRandom().nextInt(1000) + ".jpg");
                    if (!tmp.renameTo(dest)) {
                        tmp.delete();
                        send(out, 500, "text/plain", "Couldn't save".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                case "/api/delete": {
                    File f = named(param(query, "name"));
                    if (f != null) f.delete();
                    send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                case "/api/clear": {
                    for (File f : pictures(context)) f.delete();
                    send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                default:
                    send(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            AppLog.w("phone upload: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** A picture of ours by file name (letters, digits, dots, dashes only, so nothing outside the folder). */
    private File named(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+\\.jpg")) return null;
        File f = new File(dir(context), name);
        return f.isFile() ? f : null;
    }

    private static String param(String query, String key) {
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(key)) return kv.substring(eq + 1);
        }
        return null;
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int ch; (ch = in.read()) >= 0;) {
            if (ch == '\n') break;
            if (ch != '\r') sb.append((char) ch);
            if (sb.length() > 16_384) throw new IOException("header too long");
        }
        return sb.toString();
    }

    private static void send(OutputStream out, int status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    /** The page the phone sees, in the TV's language. */
    private static String page() {
        String html = "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<title>Free My TV</title><style>"
                + "body{font:17px system-ui,sans-serif;background:#0b0f1f;color:#fff;margin:0;padding:20px;max-width:560px;margin:auto}"
                + "h1{font-size:24px}input,button{font:inherit;padding:14px;border-radius:12px;border:0;width:100%;box-sizing:border-box;margin:8px 0}"
                + "input{background:#1d2340;color:#fff;text-align:center;font-size:28px;letter-spacing:8px}"
                + "button{background:#3a5bd9;color:#fff;font-weight:600}.g{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:12px 0}"
                + ".g div{position:relative}.g img{width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:10px;display:block}"
                + ".g b{position:absolute;top:4px;right:4px;background:#000b;border-radius:50%;width:28px;height:28px;text-align:center;line-height:28px}"
                + "#m,#e{opacity:.75}</style><h1>Free My TV</h1>"
                + "<div id=a><p>@CODE@</p><input id=c inputmode=numeric maxlength=4 placeholder=0000><button onclick=go()>OK</button><p id=e></p></div>"
                + "<div id=b hidden><input id=f type=file accept=image/* multiple hidden><button onclick=\"f.click()\">@PICK@</button>"
                + "<p id=m></p><div class=g id=g></div><button style=background:#3a3f55 onclick=clr()>@CLEAR@</button></div>"
                + "<script>let C='';"
                + "async function api(p,o,x){const r=await fetch('/api/'+p+'?code='+C+(x||''),o);if(!r.ok)throw r.status;return r}"
                + "async function list(){const j=await(await api('list')).json();"
                + "g.innerHTML=j.map(n=>'<div><img src=\"/api/img?code='+C+'&name='+n+'\"><b onclick=\"del(\\''+n+'\\')\">\u00d7</b></div>').join('');"
                + "m.textContent=j.length+' @COUNT@'}"
                + "async function go(){C=document.getElementById('c').value;try{await list();a.hidden=true;b.hidden=false}catch(x){e.textContent='@WRONG@'}}"
                + "async function del(n){await api('delete',{method:'POST'},'&name='+n);list()}"
                + "async function clr(){if(confirm('@CLEAR@?')){await api('clear',{method:'POST'});list()}}"
                + "async function shrink(f){const b=await createImageBitmap(f);const s=Math.min(1,1920/Math.max(b.width,b.height));"
                + "const v=document.createElement('canvas');v.width=Math.round(b.width*s);v.height=Math.round(b.height*s);"
                + "v.getContext('2d').drawImage(b,0,0,v.width,v.height);return new Promise(r=>v.toBlob(r,'image/jpeg',.85))}"
                + "f.onchange=async()=>{const fs=[...f.files];for(let i=0;i<fs.length;i++){m.textContent='@SENDING@ '+(i+1)+'/'+fs.length;"
                + "try{await api('upload',{method:'POST',headers:{'Content-Type':'image/jpeg'},body:await shrink(fs[i])})}catch(x){m.textContent='@FAILED@';return}}"
                + "f.value='';list()}</script>";
        return html.replace("@CODE@", Ui.tr("Digite o código que aparece na TV:", "Enter the code shown on the TV:"))
                .replace("@PICK@", Ui.tr("Escolher fotos", "Choose photos"))
                .replace("@CLEAR@", Ui.tr("Apagar todas", "Delete all"))
                .replace("@COUNT@", Ui.tr("fotos na TV", "pictures on the TV"))
                .replace("@WRONG@", Ui.tr("Código errado.", "Wrong code."))
                .replace("@SENDING@", Ui.tr("Enviando", "Sending"))
                .replace("@FAILED@", Ui.tr("Não foi possível enviar.", "Couldn't send."));
    }
}
