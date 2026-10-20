package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The "Apps" home style: a greeting and a big clock at the top left, pills (search, live TV, inputs, settings) at the
 * top right, the name of the focused app in large type, and a rounded panel with the apps in a grid (the shelf's apps
 * first, then the rest), scrolling a row at a time. The background glows in the focused app's color.
 * Everything is drawn with fast paths only (shader-filled rounded rectangles, one pre-blurred shadow): no clips, no live
 * blurs. It draws only while something moves.
 */
final class AppsHome extends View implements Choreographer.FrameCallback {
    // Layout, in a 1920 × 1080 space (scaled by k).
    private static final int COLS = 4;
    private static final float MARGIN = 120, TILE_W = 360, TILE_H = 202, GAP_X = 64, ROW_PITCH = 310;
    private static final float PANEL_TOP = 692, PANEL_H = 270, PANEL_PAD = 40, TILE_Y0 = 726;
    private static final float PILL_H = 72, PILL_CY = 104, PILL_GAP = 16, PILL_PAD = 26;
    private static final float SHADOW_PAD = 70;
    private static final int GRID = 0, PILLS = 1;
    private static final int INK = 0xFF1C1C1E, DARK = 0xFF060608;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Click click;

    private List<HomeApps.Item> apps = new ArrayList<>();
    private int[] brand = new int[0];
    private Spring[] pop = new Spring[0];
    private BitmapShader[] shaders = new BitmapShader[0];
    private final List<HomeApps.Item> pills = new ArrayList<>();
    private final RectF[] pillRects = new RectF[6];
    private final Spring[] pillFocus = new Spring[6];
    private List<HomeApps.Item> inputs = new ArrayList<>();

    private int zone = GRID, index, pillIndex, sheetIndex;
    private boolean sheetOpen, running, okDown;
    private final Spring scrollRow = new Spring(0.42f, 0.9f, 0);
    private final Spring glow = new Spring(0.8f, 1f, 1);
    private final Spring nameIn = new Spring(0.35f, 0.9f, 1);
    private final Spring sheet = new Spring(0.34f, 0.9f, 0);
    private int glowFrom = DARK, glowTo = DARK, glowShown = -1;
    // The background (the glow and the dark edge on the right) is baked into a small bitmap that is only redrawn when
    // the glow's color changes, and scaled to the screen: drawing gradients over the whole screen on every frame was
    // too much for this GPU (median frame 32 ms).
    private static final int BG_W = 480, BG_H = 270;
    private Bitmap background;
    private Canvas backgroundCanvas;
    private final Paint backgroundPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint bakedGlow = new Paint(), bakedEdge = new Paint();
    private final RectF screenRect = new RectF();

    private int w, h;
    private float k = 1;
    private long lastNanos, lastMinute = -1;
    private final RectF cell = new RectF(), tmp = new RectF(), panel = new RectF();
    private final Matrix matrix = new Matrix(), shaderMatrix = new Matrix();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG), glowPaint = new Paint(), vignette = new Paint();
    private Paint greetingPaint, clockPaint, suffixPaint, datePaint, namePaint, pillText, sheetTitle, sheetRow, hint;
    private Bitmap tileShadow;
    private float shadowFor;

    AppsHome(Activity a) {
        super(a);
        activity = a;
        click = new Click(a);
        for (int i = 0; i < pillFocus.length; i++) {
            pillFocus[i] = new Spring(0.28f, 0.85f, 0);
            pillRects[i] = new RectF();
        }
        stroke.setStyle(Paint.Style.STROKE);
        buildPills();
        setFocusable(true);
    }

    private void buildPills() {
        pills.clear();
        pills.add(HomeApps.action(HomeApps.SEARCH, "search", Ui.tr("Buscar", "Search"), "search"));
        try {
            if (HomeApps.liveTv(getContext()) != null) {
                pills.add(HomeApps.action(HomeApps.LIVE_TV, "live", Ui.tr("TV ao Vivo", "Live TV"), "live_tv"));
            }
        } catch (RuntimeException ignored) {
        }
        pills.add(HomeApps.action(HomeApps.INPUTS, "inputs", Ui.tr("Entradas", "Inputs"), "hdmi"));
        pills.add(HomeApps.action(HomeApps.SETTINGS, "settings", Ui.tr("Ajustes", "Settings"), "settings"));
    }

    // MARK: - Lifecycle (HomeActivity)

    void resume() {
        if (running) return;
        running = true;
        lastNanos = 0;
        lastMinute = -1;
        buildPills();
        layoutPills();
        reload();
        Choreographer.getInstance().postFrameCallback(this);
        handler.postDelayed(minuteTick, 15_000);
        invalidate();
    }

    void pause() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        handler.removeCallbacks(minuteTick);
    }

    void destroy() {
        pause();
        click.release();
    }

    /** Home pressed on the home screen: the first app focused, nothing open. */
    void reset(boolean animated) {
        zone = GRID;
        sheetOpen = false;
        sheet.target = animated ? 0 : sheet.value;
        if (!animated) sheet.snap(0);
        index = 0;
        focusChanged(true);
        if (!animated) scrollRow.snap(0);
        invalidate();
    }

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long minute = System.currentTimeMillis() / 60_000;
            if (minute != lastMinute) invalidate();
            handler.postDelayed(this, 15_000);
        }
    };

    // MARK: - Data

    private void reload() {
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            List<HomeApps.Item> all = new ArrayList<>(HomeApps.shelf(c, false));
            Set<String> have = new HashSet<>();
            for (HomeApps.Item it : all) have.add(it.pkg);
            for (HomeApps.Item it : HomeApps.others(c)) {
                if (it.pkg != null && !have.add(it.pkg)) continue;
                all.add(it);
            }
            final int[] colors = new int[all.size()];
            for (int i = 0; i < colors.length; i++) colors[i] = brandOf(all.get(i).tile);
            final List<HomeApps.Item> in = HomeApps.allInputs(c);
            handler.post(() -> apply(all, colors, in));
        });
    }

    private void apply(List<HomeApps.Item> list, int[] colors, List<HomeApps.Item> in) {
        String keep = index < apps.size() ? apps.get(index).pkg : null;
        apps = list;
        brand = colors;
        inputs = in;
        pop = new Spring[list.size()];
        shaders = new BitmapShader[list.size()];
        for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.34f, 0.74f, 0);
        int at = 0;
        for (int i = 0; i < list.size() && keep != null; i++) if (keep.equals(list.get(i).pkg)) at = i;
        index = Math.min(at, Math.max(0, list.size() - 1));
        scrollRow.snap(index / COLS);
        focusChanged(true);
        invalidate();
    }

    /** The most colorful of a sample of the app's art: its color (a deep gray if it has none). */
    private static int brandOf(Bitmap tile) {
        int best = 0xFF3A3A3C;
        if (tile == null) return best;
        float bestScore = 0.15f;
        float[] hsv = new float[3];
        try {
            for (int yy = 0; yy < 24; yy++) {
                for (int xx = 0; xx < 42; xx++) {
                    int px = tile.getPixel((xx * 2 + 1) * tile.getWidth() / 84, (yy * 2 + 1) * tile.getHeight() / 48);
                    android.graphics.Color.colorToHSV(px, hsv);
                    float score = hsv[1] * hsv[2];
                    if (score > bestScore) {
                        bestScore = score;
                        best = px | 0xFF000000;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return best;
    }

    // MARK: - Size

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        greetingPaint = Ui.text(Ui.semibold, 22 * k, 0x99FFFFFF);
        greetingPaint.setLetterSpacing(0.24f);
        clockPaint = Ui.text(Ui.thin, 126 * k, 0xFFFFFFFF);
        suffixPaint = Ui.text(Ui.semibold, 30 * k, 0xB3FFFFFF);
        datePaint = Ui.text(Ui.semibold, 24 * k, 0x99FFFFFF);
        datePaint.setLetterSpacing(0.13f);
        namePaint = Ui.text(Ui.light, 92 * k, 0xFFFFFFFF);
        pillText = Ui.text(Ui.semibold, 25 * k, 0xFFFFFFFF);
        sheetTitle = Ui.text(Ui.displaySemibold, 40 * k, 0xFFFFFFFF);
        sheetTitle.setTextAlign(Paint.Align.CENTER);
        sheetRow = Ui.text(Ui.medium, 32 * k, 0xF2FFFFFF);
        hint = Ui.text(Ui.regular, 26 * k, 0x99FFFFFF);
        hint.setTextAlign(Paint.Align.CENTER);
        stroke.setStrokeWidth(2f * k);
        bakedEdge.setShader(new LinearGradient(0, 0, BG_W, 0, new int[] {0x00000000, 0x00000000, 0xB8000000},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        screenRect.set(0, 0, w, h);
        glowShown = -1;
        tileShadow = null;
        layoutPills();
    }

    private void layoutPills() {
        if (pillText == null) return;
        float right = 1840 * k, ph = PILL_H * k;
        for (int i = pills.size() - 1; i >= 0; i--) {
            float width = 2 * PILL_PAD * k + 32 * k + 14 * k + pillText.measureText(pills.get(i).label);
            pillRects[i].set(right - width, PILL_CY * k - ph / 2, right, PILL_CY * k + ph / 2);
            right -= width + PILL_GAP * k;
        }
    }

    // MARK: - Frames

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        boolean moving = !scrollRow.settled() || !glow.settled() || !nameIn.settled() || !sheet.settled();
        for (Spring s : pop) moving |= !s.settled();
        for (int i = 0; i < pills.size(); i++) moving |= !pillFocus[i].settled();
        if (moving) invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    // MARK: - Drawing

    @Override protected void onDraw(Canvas c) {
        if (w == 0) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        scrollRow.step(dt);
        glow.step(dt);
        nameIn.step(dt);
        sheet.step(dt);
        for (int i = 0; i < pop.length; i++) {
            pop[i].target = zone == GRID && i == index ? 1 : 0;
            pop[i].step(dt);
        }
        for (int i = 0; i < pills.size(); i++) {
            pillFocus[i].target = zone == PILLS && i == pillIndex ? 1 : 0;
            pillFocus[i].step(dt);
        }
        drawBackground(c);
        drawHeader(c);
        drawPills(c);
        drawName(c);
        drawGrid(c);
        if (sheet.value > 0.01f) drawSheet(c);
    }

    private void drawBackground(Canvas c) {
        int col = blend(glowFrom, glowTo, Ui.clamp(glow.value));
        if (background == null) {
            background = Bitmap.createBitmap(BG_W, BG_H, Bitmap.Config.ARGB_8888);
            backgroundCanvas = new Canvas(background);
            glowShown = -1;
        }
        if (col != glowShown) {
            glowShown = col;
            backgroundCanvas.drawColor(DARK);
            bakedGlow.setShader(new RadialGradient(0.30f * BG_W, 0.40f * BG_H, 0.9f * BG_W,
                    new int[] {Ui.alpha(col, 0.66f), Ui.alpha(col, 0.28f), 0x00000000}, new float[] {0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            backgroundCanvas.drawRect(0, 0, BG_W, BG_H, bakedGlow);
            backgroundCanvas.drawRect(0, 0, BG_W, BG_H, bakedEdge);
        }
        c.drawBitmap(background, null, screenRect, backgroundPaint);
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return Ui.tr("BOM DIA", "GOOD MORNING");
        if (hour < 18) return Ui.tr("BOA TARDE", "GOOD AFTERNOON");
        return Ui.tr("BOA NOITE", "GOOD EVENING");
    }

    private void drawHeader(Canvas c) {
        long now = System.currentTimeMillis();
        lastMinute = now / 60_000;
        float x = MARGIN * k;
        c.drawText(greeting(), x, 70 * k, greetingPaint);
        String time = Ui.clock(now);
        float base = 190 * k;
        c.drawText(time, x - Ui.leftBearing(clockPaint, time), base, clockPaint);
        float end = x + clockPaint.measureText(time);
        if (Ui.en) {
            String suffix = new SimpleDateFormat("a", Locale.US).format(new Date(now));
            c.drawText(suffix, end + 14 * k, base, suffixPaint);
            end += 14 * k + suffixPaint.measureText(suffix);
        }
        float dividerX = end + 34 * k;
        stroke.setColor(0x38FFFFFF);
        c.drawLine(dividerX, base - 84 * k, dividerX, base - 6 * k, stroke);
        String date = Ui.date(now).replace(",", "").toUpperCase(Ui.locale());
        c.drawText(date, dividerX + 34 * k, base - 28 * k, datePaint);
    }

    private void drawPills(Canvas c) {
        for (int i = 0; i < pills.size(); i++) {
            RectF r = pillRects[i];
            float f = Ui.clamp(pillFocus[i].value), s = 1 + 0.06f * f;
            float cx = r.centerX(), cy = r.centerY();
            cell.set(cx - r.width() * s / 2, cy - r.height() * s / 2, cx + r.width() * s / 2, cy + r.height() * s / 2);
            float rad = cell.height() / 2;
            fill.setColor(blend(0x1FFFFFFF, 0xFFFFFFFF, f));
            c.drawRoundRect(cell, rad, rad, fill);
            if (f < 0.98f) {
                stroke.setColor(Ui.alpha(0xFFFFFFFF, 0.16f * (1 - f)));
                c.drawRoundRect(cell, rad, rad, stroke);
            }
            int ink = blend(0xFFFFFFFF, INK, f);
            HomeApps.Item it = pills.get(i);
            float iconX = cell.left + PILL_PAD * k * s + 16 * k * s;
            Symbols.draw(getContext(), c, it.symbol, iconX, cy, 31 * k * s, ink);
            pillText.setColor(ink);
            c.drawText(it.label, iconX + 16 * k * s + 14 * k, cy + Ui.halfCap(pillText), pillText);
        }
    }

    private void drawName(Canvas c) {
        if (index >= apps.size()) return;
        float f = Ui.clamp(nameIn.value);
        namePaint.setAlpha(Math.round(255 * f));
        String name = apps.get(index).label;
        float x = (MARGIN + 18) * k;
        c.drawText(TextUtils.ellipsize(name, new android.text.TextPaint(namePaint), 1500 * k, TextUtils.TruncateAt.END).toString(),
                x - Ui.leftBearing(namePaint, name), (612 + 16 * (1 - f)) * k, namePaint);
    }

    private float gridLeft() {
        return (1920 - (COLS * TILE_W + (COLS - 1) * GAP_X)) / 2f * k;
    }

    private void tileRect(int i, float rowScroll, RectF out) {
        int row = i / COLS, col = i % COLS;
        float x = gridLeft() + col * (TILE_W + GAP_X) * k;
        float y = (TILE_Y0 + (row - rowScroll) * ROW_PITCH) * k;
        out.set(x, y, x + TILE_W * k, y + TILE_H * k);
    }

    private void drawGrid(Canvas c) {
        float left = gridLeft() - PANEL_PAD * k, right = gridLeft() + (COLS * TILE_W + (COLS - 1) * GAP_X) * k + PANEL_PAD * k;
        panel.set(left, PANEL_TOP * k, right, (PANEL_TOP + PANEL_H) * k);
        fill.setColor(0x0FFFFFFF);
        c.drawRoundRect(panel, 46 * k, 46 * k, fill);
        stroke.setColor(0x24FFFFFF);
        c.drawRoundRect(panel, 46 * k, 46 * k, stroke);
        if (apps.isEmpty()) return;
        float rowScroll = scrollRow.value;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < apps.size(); i++) {
                if ((pass == 1) != (i == index && zone == GRID)) continue;
                float rowOff = i / COLS - rowScroll;
                if (rowOff < -1f || rowOff > 1.7f) continue;
                float alpha = rowOff < 0 ? 1 + rowOff : rowOff > 1 ? Math.max(0, 1 - (rowOff - 1) * 1.4f) : 1;
                if (alpha <= 0.01f) continue;
                drawTile(c, i, rowScroll, alpha);
            }
        }
    }

    private void drawTile(Canvas c, int i, float rowScroll, float alpha) {
        tileRect(i, rowScroll, tmp);
        float f = Ui.clamp(pop[i].value), s = 1 + 0.11f * pop[i].value;
        float bw = tmp.width(), bh = tmp.height(), cx = tmp.centerX(), cy = tmp.centerY();
        cell.set(cx - bw * s / 2, cy - bh * s / 2, cx + bw * s / 2, cy + bh * s / 2);
        float r = 26 * k * s;
        if (f > 0.02f) {
            // The pre-blurred shadow, grown with the tile.
            float pad = SHADOW_PAD * k;
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            tmp.set(-bw / 2 - pad, -bh / 2 - pad + 16 * k * f, bw / 2 + pad, bh / 2 + pad + 16 * k * f);
            shadowPaint.setAlpha(Math.round(255 * 0.65f * f * alpha));
            c.drawBitmap(tileShadow(bw, bh), null, tmp, shadowPaint);
            c.restore();
        }
        Bitmap art = apps.get(i).tile;
        if (art != null) {
            if (shaders[i] == null) shaders[i] = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            tmp.set(0, 0, art.getWidth(), art.getHeight());
            shaderMatrix.setRectToRect(tmp, cell, Matrix.ScaleToFit.FILL);
            shaders[i].setLocalMatrix(shaderMatrix);
            tilePaint.setShader(shaders[i]);
            tilePaint.setAlpha(Math.round(255 * alpha));
            c.drawRoundRect(cell, r, r, tilePaint);
            tilePaint.setShader(null);
        } else {
            fill.setColor(Ui.alpha(0xFF2C2C2E, alpha));
            c.drawRoundRect(cell, r, r, fill);
        }
        if (f > 0.02f) {
            stroke.setColor(Ui.alpha(0xFFFFFFFF, 0.55f * f * alpha));
            c.drawRoundRect(cell, r, r, stroke);
        }
    }

    /** One soft shadow at the tile's resting size, blurred once. */
    private Bitmap tileShadow(float bw, float bh) {
        if (tileShadow != null && shadowFor == k) return tileShadow;
        float pad = SHADOW_PAD * k;
        Bitmap b = Bitmap.createBitmap(Math.round(bw + 2 * pad), Math.round(bh + 2 * pad), Bitmap.Config.ARGB_8888);
        Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
        blur.setColor(0xFF000000);
        blur.setMaskFilter(new BlurMaskFilter(24 * k, BlurMaskFilter.Blur.NORMAL));
        new Canvas(b).drawRoundRect(pad, pad, pad + bw, pad + bh, 26 * k, 26 * k, blur);
        tileShadow = b;
        shadowFor = k;
        return b;
    }

    /** The HDMI inputs, as a list over the screen. */
    private void drawSheet(Canvas c) {
        float f = Ui.clamp(sheet.value);
        fill.setColor(Ui.alpha(0xFF000000, 0.72f * f));
        c.drawRect(0, 0, w, h, fill);
        float cx = w / 2f, top = 250 * k;
        sheetTitle.setAlpha(Math.round(255 * f));
        c.drawText(Ui.tr("Entradas", "Inputs"), cx, top, sheetTitle);
        if (inputs.isEmpty()) {
            hint.setAlpha(Math.round(255 * f));
            c.drawText(Ui.tr("Nenhuma entrada encontrada.", "No inputs found."), cx, top + 110 * k, hint);
            return;
        }
        float rowH = 92 * k, width = 760 * k;
        for (int i = 0; i < inputs.size() && i < 8; i++) {
            float y = top + 50 * k + i * (rowH + 12 * k);
            tmp.set(cx - width / 2, y, cx + width / 2, y + rowH);
            boolean on = i == sheetIndex;
            fill.setColor(Ui.alpha(on ? 0xFFFFFFFF : 0x2EFFFFFF, f));
            c.drawRoundRect(tmp, 26 * k, 26 * k, fill);
            sheetRow.setColor(Ui.alpha(on ? INK : 0xFFFFFFFF, f));
            HomeApps.Item it = inputs.get(i);
            Symbols.draw(getContext(), c, "hdmi", tmp.left + 56 * k, tmp.centerY(), 34 * k, Ui.alpha(on ? INK : 0xFFFFFFFF, f));
            c.drawText(it.label, tmp.left + 100 * k, tmp.centerY() + Ui.halfCap(sheetRow), sheetRow);
            if (it.connected) {
                fill.setColor(Ui.alpha(0xFF34C759, f));
                c.drawCircle(tmp.right - 44 * k, tmp.centerY(), 9 * k, fill);
            }
        }
    }

    private static int blend(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }

    // MARK: - Focus and keys

    /** The focused app changed: glow in its color, its name fades in, the grid scrolls to its row. */
    private void focusChanged(boolean snapGlow) {
        int target = index < brand.length ? brand[index] : DARK;
        int current = blend(glowFrom, glowTo, Ui.clamp(glow.value));
        if (snapGlow) {
            glowFrom = glowTo = target;
            glow.snap(1);
        } else if (target != glowTo) {
            glowFrom = current;
            glowTo = target;
            glow.value = 0;
            glow.velocity = 0;
            glow.target = 1;
        }
        nameIn.value = snapGlow ? 1 : 0;
        nameIn.velocity = 0;
        nameIn.target = 1;
        scrollRow.target = index / COLS;
    }

    boolean onKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode();
        if (sheetOpen) return sheetKey(e, down, code);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Opens on release, and only if the press happened here.
                if (down) {
                    if (e.getRepeatCount() == 0) okDown = true;
                } else {
                    if (okDown && !e.isCanceled()) activate();
                    okDown = false;
                }
                return true;
            default:
                return false;   // Back and the system keys go on to the system
        }
    }

    private boolean sheetKey(KeyEvent e, boolean down, int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && sheetIndex > 0) sheetIndex--;
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && sheetIndex < Math.min(inputs.size(), 8) - 1) sheetIndex++;
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
                    if (okDown && !e.isCanceled() && sheetIndex < inputs.size()) {
                        click.open();
                        HomeApps.Item it = inputs.get(sheetIndex);
                        closeSheet();
                        HomeApps.open(activity, it, this, w / 2 - 200, h / 2 - 50, 400, 100);
                    }
                    okDown = false;
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) closeSheet();
                return true;
            default:
                return false;
        }
    }

    private void openSheet() {
        sheetOpen = true;
        sheetIndex = 0;
        sheet.target = 1;
        invalidate();
    }

    private void closeSheet() {
        sheetOpen = false;
        sheet.target = 0;
        invalidate();
    }

    private void move(int dx, int dy) {
        int n = apps.size();
        if (zone == GRID) {
            int next = index;
            if (dx != 0) {
                next = Math.max(0, Math.min(n - 1, index + dx));
            } else if (dy > 0) {
                if (index + COLS <= n - 1) next = index + COLS;
                else if (index / COLS < (n - 1) / COLS) next = n - 1;
            } else if (dy < 0) {
                if (index >= COLS) {
                    next = index - COLS;
                } else {
                    zone = PILLS;
                    pillIndex = nearestPill(gridLeft() + (index % COLS) * (TILE_W + GAP_X) * k + TILE_W * k / 2);
                    click.move();
                    invalidate();
                    return;
                }
            }
            if (next == index) return;
            index = next;
            focusChanged(false);
        } else {
            if (dx != 0) {
                int next = Math.max(0, Math.min(pills.size() - 1, pillIndex + dx));
                if (next == pillIndex) return;
                pillIndex = next;
            } else if (dy > 0) {
                zone = GRID;
            } else {
                return;
            }
        }
        click.move();
        invalidate();
    }

    private int nearestPill(float x) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < pills.size(); i++) {
            float d = Math.abs(pillRects[i].centerX() - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private void activate() {
        click.open();
        if (zone == PILLS) {
            HomeApps.Item it = pills.get(pillIndex);
            if (it.kind == HomeApps.SEARCH) {
                activity.startActivity(new Intent(activity, SearchActivity.class));
            } else if (it.kind == HomeApps.INPUTS) {
                openSheet();
            } else {
                RectF r = pillRects[pillIndex];
                HomeApps.open(activity, it, this, Math.round(r.left), Math.round(r.top), Math.round(r.width()), Math.round(r.height()));
            }
            return;
        }
        if (apps.isEmpty()) return;
        tileRect(index, scrollRow.value, tmp);
        boolean opened = HomeApps.open(activity, apps.get(index), this, Math.round(tmp.left), Math.round(tmp.top),
                Math.round(tmp.width()), Math.round(tmp.height()));
        if (!opened) AppLog.w("couldn't open " + apps.get(index).label);
    }
}
