package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * "Add pictures from your phone": shows the address and the code of the little upload page (PhoneUpload). The page runs
 * only while this screen is open. Same look as the other settings screens.
 */
public class PhoneUploadActivity extends Activity {
    private PhoneUploadView view;
    private PhoneUpload server;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new PhoneUploadView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        server = new PhoneUpload(this);
        boolean ok = server.start();
        view.show(ok ? PhoneUpload.ipAddress() : null, server.port, server.code, ok);
        view.resume();
    }

    @Override protected void onPause() {
        view.pause();
        if (server != null) server.stop();
        server = null;
        super.onPause();
    }

    static final class PhoneUploadView extends View implements Choreographer.FrameCallback {
        private final Scene scene = new Scene();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private String ip, code = "";
        private int port;
        private boolean serving, running;
        private long startedAt, lastNanos, lastSky;
        private float k = 1;
        private Paint title, step, address, codePaint, hint;

        PhoneUploadView(Context c) {
            super(c);
            HomeData.Weather wx = HomeData.get(c).weather;
            Shaders.sky(scene.palette, System.currentTimeMillis(), wx.sunrise, wx.sunset);
            scene.setMotion(0.5f, 0.2f);
            scene.applyPalette();
        }

        void show(String ip, int port, String code, boolean serving) {
            this.ip = ip;
            this.port = port;
            this.code = code;
            this.serving = serving;
            invalidate();
        }

        void resume() {
            if (running) return;
            running = true;
            startedAt = SystemClock.uptimeMillis();
            lastNanos = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void pause() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            // The picture count changes while the phone sends: refresh about twice a second.
            if (SystemClock.uptimeMillis() - lastSky >= 500) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            k = width / 1920f;
            scene.setSize(width, height);
            title = Ui.text(Ui.displaySemibold, 56 * k, 0xFFFFFFFF);
            title.setTextAlign(Paint.Align.CENTER);
            step = Ui.text(Ui.regular, 30 * k, 0xB3FFFFFF);
            step.setTextAlign(Paint.Align.CENTER);
            address = Ui.text(Ui.displaySemibold, 68 * k, 0xFFFFFFFF);
            address.setTextAlign(Paint.Align.CENTER);
            codePaint = Ui.text(Ui.displaySemibold, 120 * k, 0xFFFFFFFF);
            codePaint.setTextAlign(Paint.Align.CENTER);
            codePaint.setLetterSpacing(0.3f);
            hint = Ui.text(Ui.regular, 25 * k, 0x80FFFFFF);
            hint.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas c) {
            if (title == null) return;
            long up = SystemClock.uptimeMillis();
            if (up - lastSky >= 500) {
                lastSky = up;
                scene.advance((up - startedAt) / 1000f);
            }
            scene.drawBackground(c);
            float cx = getWidth() / 2f;
            c.drawText(Ui.tr("Adicionar fotos do celular", "Add pictures from your phone"), cx, 170 * k, title);
            if (!serving || ip == null) {
                c.drawText(serving ? Ui.tr("Conecte a TV ao Wi-Fi.", "Connect the TV to Wi-Fi.")
                        : Ui.tr("Não foi possível iniciar.", "Couldn't start."), cx, 480 * k, step);
                return;
            }
            c.drawText(Ui.tr("1. No celular (no mesmo Wi-Fi), abra:", "1. On your phone (same Wi-Fi), open:"), cx, 300 * k, step);
            c.drawText("http://" + ip + ":" + port, cx, 400 * k, address);
            c.drawText(Ui.tr("2. Digite o código:", "2. Enter the code:"), cx, 520 * k, step);
            c.drawText(code, cx, 650 * k, codePaint);
            c.drawText(Ui.tr("3. Escolha as fotos. Elas aparecem na TV na hora.", "3. Choose your photos. They show up on the TV right away."), cx, 760 * k, step);
            int n = PhoneUpload.pictures(getContext()).size();
            c.drawText(n + " " + Ui.tr("fotos na TV", "pictures on this TV"), cx, 860 * k, step);
            c.drawText(Ui.tr("Voltar: fechar", "Back: close"), cx, 980 * k, hint);
        }
    }
}
