package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Search for a movie or show (TMDB) and see which of the TV's streaming apps has it: OK opens that app. Opened from the
 * magnifier on the home screen. Same look as the settings: the list on the right, the focused row in white.
 */
public class SearchActivity extends Activity {
    private SearchView view;
    private boolean asked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new SearchView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        if (!asked) {
            asked = true;
            view.ask();
        }
    }

    @Override protected void onPause() {
        view.pause();
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    interface Answer { void got(String text); }

    /** The Fire TV keyboard (with the phone QR code) comes up on its own for the field. */
    void askQuery(final Answer answer) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setHint(Ui.tr("Filme ou série", "Movie or show"));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(Ui.tr("Buscar", "Search"))
                .setView(input)
                .setPositiveButton("OK", (d, which) -> answer.got(input.getText().toString().trim()))
                .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                .create();
        input.setOnEditorActionListener((v, action, event) -> {
            dialog.dismiss();
            answer.got(input.getText().toString().trim());
            return true;
        });
        dialog.show();
    }

    static final class SearchView extends View implements Choreographer.FrameCallback {
        private static final int VISIBLE = 10;
        private static final float LIST_X = 1000, LIST_R = 1824, LIST_TOP = 186, ROW_H = 74, ROW_GAP = 8;
        private static final int AMBER = 0xFFFFC266, INK = 0xFF1C1C1E;

        private final SearchActivity activity;
        private final Scene scene = new Scene();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Spring[] pop = new Spring[VISIBLE + 1];
        private final RectF tmp = new RectF(), rowRect = new RectF();
        private final List<Recs.Hit> hits = new ArrayList<>();
        private String query = "", note = "";
        private boolean searching, running, okDown;
        private int focus, scroll;
        private long startedAt, lastNanos, lastSky;
        private int w;
        private float k = 1;
        private Paint title, subtitle, rowLabel, rowValue, heading, hint, white, shadow, spinner;

        SearchView(SearchActivity a) {
            super(a);
            activity = a;
            for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.28f, 0.85f, 0);
            HomeData.Weather wx = HomeData.get(a).weather;
            Shaders.sky(scene.palette, System.currentTimeMillis(), wx.sunrise, wx.sunset);
            scene.setMotion(0.5f, 0.2f);
            scene.applyPalette();
            setFocusable(true);
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
            boolean moving = searching;
            for (Spring s : pop) moving |= !s.settled();
            if (moving || SystemClock.uptimeMillis() - lastSky >= 100) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            k = w / 1920f;
            scene.setSize(width, height);
            title = Ui.text(Ui.displaySemibold, 50 * k, 0xFFFFFFFF);
            title.setTextAlign(Paint.Align.CENTER);
            subtitle = Ui.text(Ui.regular, 26 * k, 0x99FFFFFF);
            subtitle.setTextAlign(Paint.Align.CENTER);
            rowLabel = Ui.text(Ui.medium, 30 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 28 * k, 0x8CFFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            hint = Ui.text(Ui.regular, 23 * k, 0x99FFFFFF);
            hint.setTextAlign(Paint.Align.CENTER);
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xFFF5F5F7);
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
        }

        // MARK: - Searching

        void ask() {
            activity.askQuery(this::search);
        }

        private void search(final String text) {
            if (text.isEmpty()) return;
            query = text;
            searching = true;
            note = "";
            hits.clear();
            focus = 0;
            scroll = 0;
            final Context c = getContext().getApplicationContext();
            new Thread(() -> {
                List<Recs.Hit> found = new ArrayList<>();
                String error = null;
                try {
                    found = Recs.get(c).search(text);
                } catch (Exception e) {
                    error = Recs.hasKey(c) ? Ui.tr("Busca indisponível.", "Couldn't reach the search.")
                            : Ui.tr("Sem chave do TMDB (veja o README).", "No TMDB key (see the README).");
                }
                final List<Recs.Hit> result = found;
                final String failed = error;
                handler.post(() -> {
                    searching = false;
                    hits.clear();
                    hits.addAll(result);
                    focus = hits.isEmpty() ? 0 : 1;
                    scroll = 0;
                    note = failed != null ? failed : hits.isEmpty() ? Ui.tr("Nenhum resultado.", "No results.") : "";
                    invalidate();
                });
            }, "title-search").start();
        }

        private int rowCount() {
            return hits.size() + 1;   // "New search…", then the results
        }

        // MARK: - Drawing

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            int slot = focus - scroll;
            for (int i = 0; i < pop.length; i++) {
                pop[i].target = i == slot && i < rowCount() ? 1 : 0;
                pop[i].step(dt);
            }
            long up = SystemClock.uptimeMillis();
            float seconds = (up - startedAt) / 1000f;
            if (up - lastSky >= 100) {
                lastSky = up;
                scene.advance(seconds);
            }
            scene.drawBackground(c);

            float cx = 500 * k;
            Symbols.draw(getContext(), c, "search", cx, 400 * k, 150 * k, 0xEBFFFFFF);
            c.drawText(Ui.tr("Buscar", "Search"), cx, 612 * k, title);
            c.drawText(Ui.tr("Veja em qual app está", "See which of your apps has it"), cx, 660 * k, subtitle);
            if (!query.isEmpty()) c.drawText("“" + TextUtils.ellipsize(query, new TextPaint(subtitle), 640 * k, TextUtils.TruncateAt.END) + "”", cx, 704 * k, hint);

            c.drawText(Ui.tr("Resultados", "Results"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int total = rowCount();
            for (int s = 0; s < VISIBLE && scroll + s < total; s++) {
                int index = scroll + s;
                float y = (LIST_TOP + 10 + s * (ROW_H + ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + ROW_H * k);
                float f = rowBackground(c, s);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowValue.setColor(blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                float right = rowRect.right - 30 * k;
                if (index == 0) {
                    c.drawText(Ui.tr("Nova busca…", "New search…"), rowRect.left + 30 * k, base, rowLabel);
                    continue;
                }
                Recs.Hit h = hits.get(index - 1);
                String value = h.pkg != null ? h.serviceName : Ui.tr("Não está nos seus apps", "Not on your apps");
                float valueWidth = Math.min(rowValue.measureText(value), 380 * k);
                c.drawText(TextUtils.ellipsize(value, new TextPaint(rowValue), valueWidth, TextUtils.TruncateAt.END).toString(), right, base, rowValue);
                String label = h.title + (h.year.isEmpty() ? "" : "  ·  " + h.year);
                c.drawText(TextUtils.ellipsize(label, new TextPaint(rowLabel), rowRect.width() - 60 * k - valueWidth - 30 * k,
                        TextUtils.TruncateAt.END).toString(), rowRect.left + 30 * k, base, rowLabel);
            }
            if (searching) {
                spinner.setColor(0xFFFFFFFF);
                Ui.spinner(c, LIST_X * k + 42 * k, (LIST_TOP + 10 + 1 * (ROW_H + ROW_GAP) + 37) * k, 12 * k, seconds * 1.3f, spinner);
                c.drawText(Ui.tr("Buscando…", "Searching…"), LIST_X * k + 72 * k, (LIST_TOP + 10 + 1 * (ROW_H + ROW_GAP) + 37) * k + Ui.halfCap(rowLabel) * 0.8f, hint);
            } else if (!note.isEmpty()) {
                hint.setTextAlign(Paint.Align.LEFT);
                hint.setColor(AMBER);
                c.drawText(note, LIST_X * k + 30 * k, (LIST_TOP + 10 + (ROW_H + ROW_GAP) + 46) * k, hint);
                hint.setColor(0x99FFFFFF);
                hint.setTextAlign(Paint.Align.CENTER);
            }
        }

        private float rowBackground(Canvas c, int slot) {
            float f = Ui.clamp(pop[slot].value);
            if (f > 0.01f) {
                float s = 1 + 0.02f * f;
                float cx = rowRect.centerX(), cy = rowRect.centerY();
                tmp.set(cx - rowRect.width() * s / 2, cy - rowRect.height() * s / 2, cx + rowRect.width() * s / 2, cy + rowRect.height() * s / 2);
                float r = 18 * k * s;
                tmp.offset(0, 10 * k * f);
                shadow.setColor(Ui.alpha(0xFF000000, 0.4f * f));
                c.drawRoundRect(tmp, r, r, shadow);
                tmp.offset(0, -10 * k * f);
                white.setAlpha(Math.round(255 * f));
                c.drawRoundRect(tmp, r, r, white);
                rowRect.set(tmp);
            }
            return f;
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        // MARK: - Keys

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && focus > 0) focus--;
                    keepVisible();
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && focus < rowCount() - 1) focus++;
                    keepVisible();
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled()) activate();
                        okDown = false;
                    }
                    return true;
                default:
                    return false;   // Back and Home go to the system
            }
        }

        private void keepVisible() {
            if (focus < scroll) scroll = focus;
            if (focus >= scroll + VISIBLE) scroll = focus - VISIBLE + 1;
        }

        private void activate() {
            if (focus == 0) {
                ask();
                return;
            }
            Recs.Hit h = hits.get(focus - 1);
            if (h.pkg == null) {
                note = Ui.tr("Nenhum dos seus apps tem este título.", "None of your apps has this title.");
                invalidate();
                return;
            }
            Intent launch = HomeApps.launchIntent(getContext().getPackageManager(), h.pkg);
            if (launch == null) return;
            HomeApps.recordLaunch(getContext(), h.pkg);
            activity.startActivity(launch);
        }
    }
}
