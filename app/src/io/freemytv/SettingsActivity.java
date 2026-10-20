package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Free My TV's settings (the app's icon, "All Apps", or the gear on the home screen), in the style of Apple TV's
 * settings: the big symbol and title on the left, the list on the right with the focused row in white. "Home screen"
 * opens the Free My TV / Original choice (with a check on the current one); the switch runs in the main process
 * (HomeMode) and this screen only asks for it and follows the state file.
 */
public class SettingsActivity extends Activity {
    private SettingsView view;
    private DreamView preview;   // "Screensaver": the screensaver right here, without changing the system setting

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        // From ADB: am start -n io.freemytv/.SettingsActivity --ez screensaver true (straight to the preview).
        if (preview == null && getIntent().getBooleanExtra("screensaver", false)) {
            getIntent().removeExtra("screensaver");
            view.pause();
            showPreview();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();     // before pausing the view: closing the preview resumes it (view.resume)
        view.pause();
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            // Like the real screensaver: arrows, OK or Back close it (other keys go on to the system).
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_UP) closePreview();
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    interface Answer { void got(String text); }

    /** Asks for a city name with the on-screen keyboard (the voice remote's mic types too). */
    void askCity(String initial, final Answer answer) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(initial);   // the current city as a hint: the Fire keyboard doesn't select old text to replace it
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(Ui.tr("Cidade", "City"))
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

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        private static final int MAIN = 0, HOME_CHOICE = 1, APPS = 2, PREFS = 3, SHELF = 4, HIDDEN = 5, CITY = 6, SETUP = 7;
        private static final int VISIBLE = 10;   // rows of a list on screen at once
        private static final float LIST_X = 1000, LIST_R = 1824, LIST_TOP = 186, ROW_H = 74, ROW_GAP = 8, CHOICE_H = 104,
                MAIN_H = 62, MAIN_GAP = 8;
        private static final int AMBER = 0xFFFFC266, INK = 0xFF1C1C1E;
        private static final String[] LANGUAGES = {"auto", "en", "fr", "pt"}, UNITS = {"auto", "C", "F"};

        /** One line of a list screen. */
        private static final class Row {
            final String label, tag;
            String value = "";
            boolean chevron, check;
            int number;
            Row(String label, String tag) {
                this.label = label;
                this.tag = tag;
            }
        }

        private final SettingsActivity activity;
        private final Scene scene = new Scene();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Spring[] pop = new Spring[VISIBLE + 1];
        private final RectF tmp = new RectF(), rowRect = new RectF();
        private JSONObject state = new JSONObject(), recs = new JSONObject();
        private boolean hasKey;
        private int screen = MAIN, focus, scroll;
        private final List<Row> rows = new ArrayList<>();
        private List<HomeApps.Item> candidates = new ArrayList<>();
        private List<String> picked = new ArrayList<>();       // the shelf, in order
        private Set<String> hidden = new HashSet<>();           // hidden from All Apps
        private final List<String[]> cityResults = new ArrayList<>();   // name, place, latitude, longitude, country code
        private String lastSearch = "";
        private String note = "";
        private long noteUntil;
        private StaticLayout[] setupText = new StaticLayout[0];
        private float setupHeight;
        private Paint hint, hintWarn;
        private boolean running, okDown, backDown;
        private long startedAt, lastNanos, pendingAt, lastSky;
        private int w, h;
        private float k = 1;
        private Paint title, subtitle, version, rowLabel, rowValue, rowSub, header, cellLabel, cellValue, warn, spinner, white, shadow;
        private TextPaint notice, step, command;

        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                state = HomeMode.state(getContext());
                hasKey = Recs.hasKey(getContext());
                recs = Recs.status(getContext());
                if (!state.optString("busy").isEmpty()) pendingAt = 0;
                // First-time setup on Fire TV: the service came on, so switch Free My TV on and go back.
                if (screen == SETUP && state.optBoolean("fireService")) {
                    HomeMode.request(getContext(), HomeMode.FREE);
                    pendingAt = SystemClock.uptimeMillis();
                    enter(MAIN, 0);
                }
                invalidate();
                handler.postDelayed(this, 500);
            }
        };

        SettingsView(SettingsActivity a) {
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
            handler.post(poll);
            Choreographer.getInstance().postFrameCallback(this);
        }

        void pause() {
            running = false;
            handler.removeCallbacks(poll);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            boolean moving = busy();
            for (Spring s : pop) moving |= !s.settled();
            if (moving || SystemClock.uptimeMillis() - lastSky >= 100) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            k = w / 1920f;
            scene.setSize(w, h);
            title = Ui.text(Ui.displaySemibold, 50 * k, 0xFFFFFFFF);
            title.setTextAlign(Paint.Align.CENTER);
            subtitle = Ui.text(Ui.regular, 26 * k, 0x99FFFFFF);
            subtitle.setTextAlign(Paint.Align.CENTER);
            version = Ui.text(Ui.regular, 22 * k, 0x66FFFFFF);
            version.setTextAlign(Paint.Align.CENTER);
            rowLabel = Ui.text(Ui.medium, 30 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 28 * k, 0x8CFFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowSub = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            header = Ui.text(Ui.semibold, 21 * k, 0x73FFFFFF);
            header.setLetterSpacing(0.06f);
            cellLabel = Ui.text(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue = Ui.numbers(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue.setTextAlign(Paint.Align.RIGHT);
            warn = Ui.text(Ui.regular, 23 * k, AMBER);
            hint = Ui.text(Ui.regular, 23 * k, 0x99FFFFFF);
            hint.setTextAlign(Paint.Align.CENTER);
            hintWarn = Ui.text(Ui.regular, 23 * k, AMBER);
            hintWarn.setTextAlign(Paint.Align.CENTER);
            notice = new TextPaint(Ui.text(Ui.regular, 23 * k, 0xB3FFFFFF));
            step = new TextPaint(Ui.text(Ui.regular, 25 * k, 0xCCFFFFFF));
            command = new TextPaint(Ui.text(Ui.regular, 22 * k, 0xFFFFFFFF));
            command.setTypeface(Typeface.MONOSPACE);
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xFFF5F5F7);
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
        }

        private boolean busy() {
            return !state.optString("busy").isEmpty() || (pendingAt != 0 && SystemClock.uptimeMillis() - pendingAt < 10_000);
        }

        private boolean freeActive() {
            return HomeMode.FREE.equals(state.optString("mode")) && !state.optBoolean("homeReverted");
        }

        private boolean isList(int s) {
            return s == APPS || s == PREFS || s == SHELF || s == HIDDEN || s == CITY;
        }

        private int rowCount() {
            if (isList(screen)) return rows.size();
            return screen == MAIN ? 5 : 2;
        }

        // MARK: - Screens

        /** Goes to a screen with the focus on a row. */
        private void enter(int s, int row) {
            screen = s;
            focus = row;
            scroll = 0;
            Context c = getContext();
            if (s == SHELF) {
                candidates = HomeApps.shelfCandidates(c);
                picked = new ArrayList<>(HomeApps.shelfPackages(c));
            } else if (s == HIDDEN) {
                candidates = HomeApps.hideCandidates(c);
                hidden = HomeApps.hiddenApps(c);
            } else if (s == SETUP) {
                buildSetup();
            }
            buildRows();
            keepFocusVisible();
            for (Spring sp : pop) sp.snap(0);
            invalidate();
        }

        private String screenTitle() {
            switch (screen) {
                case APPS: return Ui.tr("Apps", "Apps");
                case PREFS: return Ui.tr("Preferências", "Preferences");
                case SHELF: return Ui.tr("Apps do Início", "Shelf Apps");
                case HIDDEN: return Ui.tr("Apps ocultos", "Hidden Apps");
                case CITY: return Ui.tr("Cidade", "City");
                default: return "";
            }
        }

        private static String apps(int n) {
            return n + (Ui.fr ? " applis" : " apps");
        }

        private static String languageName(String code) {
            switch (code) {
                case "en": return "English";
                case "fr": return "Français";
                case "pt": return "Português";
                default: return Ui.tr("Automático", "Automatic");
            }
        }

        private static String unitsName(String code) {
            return code.equals("C") ? "°C" : code.equals("F") ? "°F" : Ui.tr("Automático", "Automatic");
        }

        /** The saved value as one of the cycle's entries ("auto" when it isn't one of them). */
        private static String oneOf(String saved, String[] options) {
            String s = saved.trim();
            for (String o : options) if (!o.equals("auto") && s.toLowerCase(Locale.ROOT).startsWith(o.toLowerCase(Locale.ROOT))) return o;
            return "auto";
        }

        private void buildRows() {
            rows.clear();
            Context c = getContext();
            switch (screen) {
                case APPS: {
                    Row shelf = new Row(Ui.tr("Apps do Início", "Shelf Apps"), "shelf");
                    shelf.value = apps(HomeApps.shelfPackages(c).size());
                    shelf.chevron = true;
                    Row hide = new Row(Ui.tr("Apps ocultos", "Hidden Apps"), "hidden");
                    hide.value = apps(HomeApps.hiddenApps(c).size());
                    hide.chevron = true;
                    rows.add(shelf);
                    rows.add(hide);
                    break;
                }
                case PREFS: {
                    Row city = new Row(Ui.tr("Cidade", "City"), "city");
                    city.value = Prefs.city().trim().isEmpty() ? "—" : Prefs.city().trim();
                    city.chevron = true;
                    Row style = new Row(Ui.tr("Estilo do Início", "Home Style"), "style");
                    style.value = Prefs.homeStyle().equals("foryou") ? Ui.tr("Para você", "For You") : Ui.tr("Apps", "Apps");
                    Row language = new Row(Ui.tr("Idioma", "Language"), "lang");
                    language.value = languageName(oneOf(Prefs.language(), LANGUAGES));
                    Row units = new Row(Ui.tr("Temperatura", "Temperature"), "temp");
                    units.value = unitsName(oneOf(Prefs.temperature(), UNITS));
                    Row size = new Row(Ui.tr("Apps por linha", "Shelf Size"), "shelf");
                    size.value = String.valueOf(Prefs.shelfSize());
                    rows.add(city);
                    rows.add(style);
                    rows.add(size);
                    rows.add(language);
                    rows.add(units);
                    break;
                }
                case SHELF:
                    for (HomeApps.Item it : candidates) {
                        Row r = new Row(it.label, it.pkg);
                        r.number = picked.indexOf(it.pkg) + 1;
                        r.check = r.number > 0;
                        rows.add(r);
                    }
                    rows.add(new Row(Ui.tr("Voltar ao padrão", "Back to default"), "reset"));
                    break;
                case HIDDEN:
                    for (HomeApps.Item it : candidates) {
                        Row r = new Row(it.label, it.pkg);
                        r.check = hidden.contains(it.pkg);
                        rows.add(r);
                    }
                    rows.add(new Row(Ui.tr("Mostrar todos", "Show all"), "reset"));
                    break;
                case CITY: {
                    Row search = new Row(Ui.tr("Buscar…", "Search…"), "search");
                    rows.add(search);
                    for (int i = 0; i < cityResults.size(); i++) {
                        Row r = new Row(cityResults.get(i)[0], "r" + i);
                        r.value = cityResults.get(i)[1];
                        rows.add(r);
                    }
                    break;
                }
                default:
                    break;
            }
        }

        private void keepFocusVisible() {
            if (!isList(screen)) return;
            if (focus < scroll) scroll = focus;
            if (focus >= scroll + VISIBLE) scroll = focus - VISIBLE + 1;
        }

        // MARK: - Drawing

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            int slot = isList(screen) ? focus - scroll : focus;
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

            // On the left: big symbol, title, description.
            float cx = 500 * k;
            Symbols.draw(getContext(), c, "sparkles", cx, 400 * k, 150 * k, 0xEBFFFFFF);
            c.drawText("Free My TV", cx, 612 * k, title);
            c.drawText(Ui.tr("Início e tela de descanso da TV", "Your TV's home screen and screensaver"), cx, 660 * k, subtitle);
            c.drawText(versionName(getContext()), cx, 704 * k, version);

            if (screen == MAIN) drawMain(c, seconds);
            else if (isList(screen)) drawList(c, seconds);
            else if (screen == SETUP) drawSetup(c, seconds);
            else drawChoice(c);
        }

        private void drawMain(Canvas c, float seconds) {
            boolean working = busy();
            String busyText = state.optString("busy");
            String mode = freeActive() ? "Free My TV" : Ui.tr("Original", "Original");
            String[] labels = {Ui.tr("Início", "Home Screen"), Ui.tr("Apps", "Apps"), Ui.tr("Preferências", "Preferences"),
                    Ui.tr("Tela de Descanso", "Screensaver"), Ui.tr("Ajustes da TV", "TV Settings")};
            String[] values = {working ? "" : mode, "", "", Ui.tr("Ver agora", "Preview"), ""};
            for (int i = 0; i < 5; i++) {
                float y = (LIST_TOP + i * (MAIN_H + MAIN_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + MAIN_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(labels[i], rowRect.left + 30 * k, base, rowLabel);
                float right = rowRect.right - 30 * k;
                if (i == 1 || i == 2 || i == 4) {
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    Symbols.draw(getContext(), c, "chevron_right", right - cw / 2, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f));
                } else if (i == 0 && working) {
                    // Switching: the current step in place of the value, with the spinning arc.
                    String text = busyText.isEmpty() ? Ui.tr("Começando…", "Starting…") : busyText;
                    rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                    String shown = TextUtils.ellipsize(text, new TextPaint(rowValue), 470 * k, TextUtils.TruncateAt.END).toString();
                    c.drawText(shown, right, base, rowValue);
                    float sx = right - rowValue.measureText(shown) - 26 * k;
                    spinner.setColor(blend(0xFFFFFFFF, INK, f));
                    Ui.spinner(c, sx, rowRect.centerY(), 12 * k, seconds * 1.3f, spinner);
                } else {
                    rowValue.setColor(blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                    c.drawText(values[i], right, base, rowValue);
                }
            }

            float y = (LIST_TOP + 5 * (MAIN_H + MAIN_GAP) + 40) * k;
            String error = state.optString("error");
            if (working && (busyText.contains("Permitir") || busyText.contains("Allow"))) {
                c.drawText(Ui.tr("Na TV: marque “Sempre permitir” e aperte OK.", "On the TV: tick “Always allow” and press OK."),
                        LIST_X * k + 30 * k, y, warn);
                y += 46 * k;
            } else if (!working && !error.isEmpty()) {
                c.drawText(TextUtils.ellipsize(error, new TextPaint(warn), 760 * k, TextUtils.TruncateAt.END).toString(), LIST_X * k + 30 * k, y, warn);
                y += 46 * k;
            }
            y = drawComparison(c, y);
            y = drawRecs(c, y + 22 * k);
            drawNotices(c, y + 34 * k);
        }

        /** Home screen picks: status, and the credit TMDB asks for. */
        private float drawRecs(Canvas c, float y) {
            float x = LIST_X * k + 30 * k;
            c.drawText(Ui.tr("RECOMENDAÇÕES", "RECOMMENDATIONS"), x, y, header);
            String line;
            String error = recs.optString("error");
            int count = recs.optInt("count");
            if (!hasKey) line = Ui.tr("Sem chave do TMDB: veja o README (config.properties).", "No TMDB key: see the README (config.properties).");
            else if (!error.isEmpty()) line = Ui.tr("Erro: ", "Error: ") + error + ".";
            else if (count > 0) line = Ui.fr ? "Activé · " + count + " titres des applis de la TV, 3 nouveaux chaque heure."
                    : Ui.tr("Ligadas · " + count + " títulos nos apps da TV, 3 novos a cada hora.",
                    "On · " + count + " titles from the TV's apps, 3 new ones every hour.");
            else line = Ui.tr("Buscando os títulos…", "Fetching titles…");
            c.drawText(TextUtils.ellipsize(line, notice, 790 * k, TextUtils.TruncateAt.END).toString(), x, y + 42 * k, notice);
            version.setTextAlign(Paint.Align.LEFT);
            c.drawText(Ui.tr("Usa a API do TMDB, sem endosso ou certificação do TMDB.",
                    "This product uses the TMDB API but is not endorsed or certified by TMDB."), x, y + 78 * k, version);
            version.setTextAlign(Paint.Align.CENTER);
            return y + 78 * k;
        }

        /** List row: no background; focused, white with a shadow (the Apple TV focus). Returns how focused it is. */
        private float drawRowBackground(Canvas c, int i) {
            float f = Ui.clamp(pop[i].value);
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

        private float drawComparison(Canvas c, float y) {
            float x = LIST_X * k + 30 * k, colA = LIST_R * k - 220 * k, colB = LIST_R * k - 30 * k;
            c.drawText(Ui.tr("COMPARAÇÃO", "COMPARISON"), x, y, header);
            cellValue.setColor(0x73FFFFFF);
            c.drawText("Original", colA, y, cellValue);
            c.drawText("Free My TV", colB, y, cellValue);
            cellValue.setColor(0xD9FFFFFF);
            JSONObject metrics = state.optJSONObject("metrics");
            JSONObject a = metrics == null ? null : metrics.optJSONObject(HomeMode.ORIGINAL);
            JSONObject b = metrics == null ? null : metrics.optJSONObject(HomeMode.FREE);
            String[][] rows = {{Ui.tr("Botão Início", "Home button"), "homeMs"}, {Ui.tr("Memória livre", "Free memory"), "memAvailableMB"},
                    {Ui.tr("Memória do Início", "Home screen memory"), "homePssMB"}};
            for (int i = 0; i < rows.length; i++) {
                float base = y + (42 + 38 * i) * k;
                c.drawText(rows[i][0], x, base, cellLabel);
                c.drawText(metric(a, rows[i][1]), colA, base, cellValue);
                c.drawText(metric(b, rows[i][1]), colB, base, cellValue);
            }
            return y + (42 + 38 * rows.length) * k;
        }

        private void drawNotices(Canvas c, float y) {
            float x = LIST_X * k + 30 * k;
            c.drawText(Ui.tr("AVISOS", "NOTICES"), x, y, header);
            JSONArray notices = state.optJSONArray("notices");
            int shown = 0;
            for (int i = 0; notices != null && i < notices.length() && shown < 2; i++) {
                JSONObject o = notices.optJSONObject(i);
                if (o == null) continue;
                String line = ago(o.optLong("at")) + " · " + o.optString("text");
                c.drawText(TextUtils.ellipsize(line, notice, 790 * k, TextUtils.TruncateAt.END).toString(), x, y + (40 + 36 * shown) * k, notice);
                shown++;
            }
            if (shown == 0) c.drawText(Ui.tr("Nenhum ainda.", "None yet."), x, y + 40 * k, notice);
        }

        /** Any list screen: menus, the shelf and hidden apps, the city results. Picked rows show a check (and a number). */
        private void drawList(Canvas c, float seconds) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(screenTitle(), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int total = rows.size();
            for (int slot = 0; slot < VISIBLE && scroll + slot < total; slot++) {
                Row r = rows.get(scroll + slot);
                float y = (LIST_TOP + 10 + slot * (ROW_H + ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + ROW_H * k);
                float f = drawRowBackground(c, slot);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowValue.setColor(blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                float right = rowRect.right - 30 * k;
                float room = rowRect.width() - 60 * k;
                if (r.chevron) {
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    Symbols.draw(getContext(), c, "chevron_right", right - cw / 2, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f));
                    right -= cw + 18 * k;
                    room -= cw + 18 * k;
                }
                if (r.check) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", right - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                    if (r.number > 0) {
                        rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                        c.drawText(String.valueOf(r.number), right - cw - 18 * k, base, rowValue);
                    }
                    room -= cw + 90 * k;
                } else if (!r.value.isEmpty()) {
                    String shown = TextUtils.ellipsize(r.value, new TextPaint(rowValue), Math.min(room * 0.5f, 420 * k), TextUtils.TruncateAt.END).toString();
                    c.drawText(shown, right, base, rowValue);
                    room -= rowValue.measureText(shown) + 30 * k;
                }
                c.drawText(TextUtils.ellipsize(r.label, new TextPaint(rowLabel), Math.max(room, 100 * k), TextUtils.TruncateAt.END).toString(),
                        rowRect.left + 30 * k, base, rowLabel);
            }
            // On the left, under the title: how it works, or the last notice.
            if (SystemClock.uptimeMillis() < noteUntil) {
                c.drawText(note, 500 * k, 790 * k, hintWarn);
            } else {
                String[] lines = hintLines();
                for (int i = 0; i < lines.length; i++) c.drawText(lines[i], 500 * k, (790 + 36 * i) * k, hint);
            }
        }

        private String[] hintLines() {
            switch (screen) {
                case SHELF:
                    return new String[] {Ui.tr("OK: pôr ou tirar · ← →: mudar a ordem", "OK: add or remove · ← →: reorder"), maxText()};
                case HIDDEN:
                    return new String[] {Ui.tr("OK: ocultar ou mostrar", "OK: hide or show"),
                            Ui.tr("Os apps ocultos continuam instalados.", "Hidden apps stay installed.")};
                case PREFS:
                    return new String[] {Ui.tr("OK ou ← →: mudar", "OK or ← →: change")};
                case CITY:
                    return new String[] {Ui.tr("OK: buscar ou escolher", "OK: search or choose")};
                default:
                    return new String[0];
            }
        }

        private static String maxText() {
            return Ui.fr ? HomeApps.maxShelf() + " applis au maximum." : Ui.tr("No máximo " + HomeApps.maxShelf() + " apps.", "Up to " + HomeApps.maxShelf() + " apps.");
        }

        /** First-time Fire TV setup: what to type on a computer. This screen goes on by itself when the service is on. */
        private void buildSetup() {
            int width = Math.round((LIST_R - LIST_X - 60) * k);
            String ip = localIp();
            String[] texts = {
                    Ui.tr("No computador, conecte nesta TV:", "On your computer, connect to this TV:"),
                    "adb connect " + ip + ":5555",
                    Ui.tr("Depois rode isto, uma única vez:", "Then run this, once:"),
                    HomeMode.FIRE_ON,
                    Ui.tr("Esta tela continua sozinha quando funcionar.", "This screen goes on by itself when it works."),
            };
            setupText = new StaticLayout[texts.length];
            setupHeight = 0;
            for (int i = 0; i < texts.length; i++) {
                TextPaint p = (i == 1 || i == 3) ? command : step;
                setupText[i] = StaticLayout.Builder.obtain(texts[i], 0, texts[i].length(), p, width).build();
                setupHeight += setupText[i].getHeight() + (i == 1 || i == 3 ? 28 : 8) * k;
            }
        }

        private static String localIp() {
            try {
                for (Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces(); n.hasMoreElements(); ) {
                    NetworkInterface ni = n.nextElement();
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                        InetAddress ip = a.nextElement();
                        if (ip instanceof Inet4Address) return ip.getHostAddress();
                    }
                }
            } catch (SocketException ignored) {
            }
            return "<TV IP>";
        }

        private void drawSetup(Canvas c, float seconds) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(Ui.tr("Ligar o Free My TV", "Turn on Free My TV"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            float y = (LIST_TOP + 10) * k;
            for (int i = 0; i < setupText.length; i++) {
                c.save();
                c.translate(LIST_X * k + 30 * k, y);
                setupText[i].draw(c);
                c.restore();
                y += setupText[i].getHeight() + (i == 1 || i == 3 ? 28 : 8) * k;
            }
            // Waiting for the service.
            y += 28 * k;
            spinner.setColor(0xFFFFFFFF);
            Ui.spinner(c, LIST_X * k + 42 * k, y, 12 * k, seconds * 1.3f, spinner);
            c.drawText(Ui.tr("Esperando o serviço…", "Waiting for the service…"), LIST_X * k + 72 * k, y + Ui.halfCap(rowLabel) * 0.8f, notice);
        }

        /** Home screen choice: Free My TV or the original, with a check on the one in use. */
        private void drawChoice(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(Ui.tr("Início", "Home Screen"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            String[][] options = {{"Free My TV", Ui.tr("Sem anúncios, com a tela de descanso nova", "No ads, with the new screensaver")},
                    {"Original", Ui.tr("O Início de fábrica da TV", "The TV's factory home screen")}};
            int current = freeActive() ? 0 : 1;
            for (int i = 0; i < 2; i++) {
                float y = (LIST_TOP + 20 + i * (CHOICE_H + ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + CHOICE_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowSub.setColor(blend(0x8CFFFFFF, 0x991C1C1E, f));
                c.drawText(options[i][0], rowRect.left + 30 * k, rowRect.top + 44 * k, rowLabel);
                c.drawText(options[i][1], rowRect.left + 30 * k, rowRect.top + 80 * k, rowSub);
                if (i == current) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 30 * k - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            c.drawText(HomeMode.isFireOs()
                    ? Ui.tr("No Fire TV, o botão Início abre o Free My TV.",
                            "On Fire TV, the Home button opens Free My TV.")
                    : Ui.tr("A troca leva uns segundos e a tela pisca nas medições.",
                            "Switching takes a few seconds; the screen blinks while it measures."), LIST_X * k + 30 * k,
                    (LIST_TOP + 20 + 2 * (CHOICE_H + ROW_GAP) + 40) * k, hint);
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        private static String metric(JSONObject m, String key) {
            if (m == null || !m.has(key)) return "—";
            long v = m.optLong(key);
            if (key.equals("homeMs")) return v + " ms";
            if (key.equals("memAvailableMB") && v >= 1024) return String.format(Ui.locale(), "%.2f GB", v / 1024.0);
            return v + " MB";
        }

        private static String ago(long at) {
            long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
            if (minutes < 1) return Ui.tr("agora", "now");
            if (Ui.fr) {
                if (minutes < 60) return "il y a " + minutes + " min";
                if (minutes < 48 * 60) return "il y a " + (minutes / 60) + " h";
                return "il y a " + (minutes / 1440) + " jours";
            }
            if (minutes < 60) return Ui.tr("há " + minutes + " min", minutes + " min ago");
            if (minutes < 48 * 60) return Ui.tr("há " + (minutes / 60) + " h", (minutes / 60) + " h ago");
            return Ui.tr("há " + (minutes / 1440) + " dias", (minutes / 1440) + " days ago");
        }

        private static String versionName(Context c) {
            try {
                return "Free My TV " + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return "Free My TV";
            }
        }

        // MARK: - Keys

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && focus > 0) focus--;
                    keepFocusVisible();
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && focus < rowCount() - 1) focus++;
                    keepFocusVisible();
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) side(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) side(1);
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
                case KeyEvent.KEYCODE_BACK:
                    if (screen == MAIN) return false;   // leaves the settings
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                    } else {
                        if (backDown && !e.isCanceled()) back();
                        backDown = false;
                    }
                    invalidate();
                    return true;
                default:
                    return false;
            }
        }

        private void back() {
            switch (screen) {
                case SHELF: enter(APPS, 0); break;
                case HIDDEN: enter(APPS, 1); break;
                case APPS: enter(MAIN, 1); break;
                case CITY: enter(PREFS, 0); break;
                case PREFS: enter(MAIN, 2); break;
                default: enter(MAIN, 0); break;
            }
        }

        private void say(String text) {
            note = text;
            noteUntil = SystemClock.uptimeMillis() + 2500;
        }

        /** Left/right: reorder a shelf app, or change a preference's value. */
        private void side(int by) {
            if (screen == SHELF) moveShelfApp(by);
            else if (screen == PREFS) cycle(by);
        }

        private void cycle(int by) {
            if (focus >= rows.size()) return;
            String tag = rows.get(focus).tag;
            if (tag.equals("style")) {
                Prefs.put("homeStyle", Prefs.homeStyle().equals("foryou") ? "apps" : "foryou");
            } else if (tag.equals("shelf")) {
                int n = Prefs.shelfSize() + by;
                if (n > Prefs.SHELF_MAX) n = Prefs.SHELF_MIN;
                if (n < Prefs.SHELF_MIN) n = Prefs.SHELF_MAX;
                Prefs.put("shelfSize", String.valueOf(n));
            } else if (tag.equals("lang")) {
                String next = next(LANGUAGES, oneOf(Prefs.language(), LANGUAGES), by);
                Prefs.put("language", next);
                Ui.applyLanguage();
                HomeData.get(getContext()).settingsChanged();
            } else if (tag.equals("temp")) {
                Prefs.put("temperature", next(UNITS, oneOf(Prefs.temperature(), UNITS), by));
            } else {
                return;
            }
            buildRows();
            invalidate();
        }

        private static String next(String[] options, String current, int by) {
            int at = 0;
            for (int i = 0; i < options.length; i++) if (options[i].equals(current)) at = i;
            return options[(at + by + options.length) % options.length];
        }

        /** OK on a row: put the app on the shelf (at the end) or take it off; the last row goes back to the default. */
        private void toggleShelfApp() {
            Context c = getContext();
            String tag = rows.get(focus).tag;
            if (tag.equals("reset")) {
                HomeApps.setShelfChoice(c, null);
                picked = new ArrayList<>(HomeApps.shelfPackages(c));
                buildRows();
                return;
            }
            if (picked.contains(tag)) {
                if (picked.size() == 1) {
                    say(Ui.tr("Deixe pelo menos 1 app.", "Keep at least 1 app."));
                    return;
                }
                picked.remove(tag);
            } else {
                if (picked.size() >= HomeApps.maxShelf()) {
                    say(maxText());
                    return;
                }
                picked.add(tag);
            }
            HomeApps.setShelfChoice(c, picked);
            buildRows();
        }

        /** Left/right on a picked app: one place earlier or later on the shelf. */
        private void moveShelfApp(int by) {
            if (focus >= rows.size()) return;
            String pkg = rows.get(focus).tag;
            int at = picked.indexOf(pkg), to = at + by;
            if (at < 0 || to < 0 || to >= picked.size()) return;
            picked.remove(at);
            picked.add(to, pkg);
            HomeApps.setShelfChoice(getContext(), picked);
            buildRows();
            invalidate();
        }

        private void toggleHidden() {
            Context c = getContext();
            String tag = rows.get(focus).tag;
            if (tag.equals("reset")) hidden.clear();
            else if (!hidden.remove(tag)) hidden.add(tag);
            HomeApps.setHiddenApps(c, hidden);
            buildRows();
        }

        // MARK: - City search (Open-Meteo's geocoding: free, no key)

        private void askCity() {
            activity.askCity(Prefs.city().trim(), this::searchCity);
        }

        private void searchCity(final String query) {
            if (query.isEmpty()) return;
            lastSearch = query;
            say(Ui.tr("Buscando…", "Searching…"));
            final String language = Ui.fr ? "fr" : Ui.en ? "en" : "pt";
            new Thread(() -> {
                final List<String[]> found = new ArrayList<>();
                String error = null;
                try {
                    String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language=" + language
                            + "&name=" + URLEncoder.encode(query, "UTF-8");
                    JSONArray list = new JSONObject(HomeData.get(url)).optJSONArray("results");
                    for (int i = 0; list != null && i < list.length(); i++) {
                        JSONObject o = list.getJSONObject(i);
                        String admin = o.optString("admin1", ""), country = o.optString("country", "");
                        String place = admin.isEmpty() || admin.equals(o.optString("name")) ? country : admin + ", " + country;
                        found.add(new String[] {o.getString("name"), place, String.format(Locale.US, "%.4f", o.getDouble("latitude")),
                                String.format(Locale.US, "%.4f", o.getDouble("longitude")), o.optString("country_code", "")});
                    }
                } catch (Exception e) {
                    error = Ui.tr("Busca indisponível.", "Couldn't reach the city search.");
                }
                final String failed = error;
                handler.post(() -> {
                    if (screen != CITY) return;
                    cityResults.clear();
                    cityResults.addAll(found);
                    buildRows();
                    focus = found.isEmpty() ? 0 : 1;
                    keepFocusVisible();
                    if (failed != null) say(failed);
                    else if (found.isEmpty()) say(Ui.tr("Nenhuma cidade encontrada.", "No city found."));
                    else noteUntil = 0;
                    invalidate();
                });
            }, "city-search").start();
        }

        private static final String[] EURO = {"AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "HR", "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PT", "SI", "SK"};

        /** The crypto prices follow the country when it's one of the currencies the app knows. */
        private static String currencyFor(String country) {
            for (String c : EURO) if (c.equals(country)) return "EUR";
            switch (country) {
                case "US": return "USD";
                case "GB": return "GBP";
                case "BR": return "BRL";
                case "JP": return "JPY";
                default: return Prefs.currency();
            }
        }

        private void pickCity(int index) {
            String[] r = cityResults.get(index);
            Prefs.put(new String[][] {{"city", r[0]}, {"latitude", r[2]}, {"longitude", r[3]}, {"region", r[4].toUpperCase(Locale.ROOT)},
                    {"currency", currencyFor(r[4].toUpperCase(Locale.ROOT))}});
            HomeData.get(getContext()).settingsChanged();
            enter(PREFS, 0);
        }

        private void activate() {
            Context c = getContext();
            if (screen == HOME_CHOICE) {
                boolean wantFree = focus == 0;
                if (wantFree && !freeActive() && HomeMode.isFireOs() && !state.optBoolean("fireService")) {
                    enter(SETUP, 0);   // the service isn't on yet: show how
                    return;
                }
                if (wantFree != freeActive() && !busy()) {
                    HomeMode.request(c, wantFree ? HomeMode.FREE : HomeMode.ORIGINAL);
                    pendingAt = SystemClock.uptimeMillis();
                }
                enter(MAIN, 0);
                return;
            }
            if (screen == SHELF) {
                toggleShelfApp();
            } else if (screen == HIDDEN) {
                toggleHidden();
            } else if (screen == APPS) {
                enter(rows.get(focus).tag.equals("shelf") ? SHELF : HIDDEN, 0);
                return;
            } else if (screen == PREFS) {
                if (rows.get(focus).tag.equals("city")) {
                    enter(CITY, 0);
                    if (cityResults.isEmpty()) askCity();
                    return;
                }
                cycle(1);
            } else if (screen == CITY) {
                String tag = rows.get(focus).tag;
                if (tag.equals("search")) askCity();
                else pickCity(Integer.parseInt(tag.substring(1)));
                return;
            } else if (screen == SETUP) {
                return;
            } else if (focus == 0) {
                if (busy()) return;
                enter(HOME_CHOICE, freeActive() ? 0 : 1);
                return;
            } else if (focus == 1) {
                enter(APPS, 0);
                return;
            } else if (focus == 2) {
                enter(PREFS, 0);
                return;
            } else if (focus == 3) {
                pause();
                activity.showPreview();
            } else {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            invalidate();
        }
    }
}
