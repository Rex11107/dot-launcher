package it.rex.dotlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

/**
 * Barra del volume di Dot Launcher, al posto di quella del telefono.
 * I tasti del volume arrivano dal servizio di accessibilità (solo se l'utente accende la barra):
 * il volume si cambia senza far comparire il pannello di sistema e si mostra una pillola a puntini
 * sul lato dello schermo. Toccandola si apre il pannello completo (media, suoneria, notifiche, sveglia
 * e modalità suoneria). Durante le chiamate e mentre il telefono squilla decide il sistema.
 */
final class VolumePanel {
    static final String PREF = "volPanel";
    private static final int[] STREAMS = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM};
    private static final String[] NAMES = {"Media", "Suoneria", "Notifiche", "Sveglia"};
    private static final String[] MODES = {"Suono", "Vibra", "Silenzio"};
    private static final int[] RINGER = {AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE,
            AudioManager.RINGER_MODE_SILENT};

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final AudioManager am;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float dp;
    private PanelView view;
    private boolean shown, expanded;
    private int stream = AudioManager.STREAM_MUSIC;
    private final Runnable hide = this::hide;

    VolumePanel(AccessibilityService s) {
        svc = s;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
        prefs = s.getSharedPreferences("dot", Context.MODE_PRIVATE);
        dp = s.getResources().getDisplayMetrics().density;
    }

    static boolean enabled(SharedPreferences p) {
        return p.getBoolean(PREF, false);
    }

    /** true se il tasto è stato gestito qui (e quindi il sistema non mostra la sua barra). */
    boolean onKey(KeyEvent e) {
        int k = e.getKeyCode();
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (!enabled(prefs) || am == null || wm == null) return false;
        int mode = am.getMode();
        // chiamate, videochiamate e squillo in arrivo: meglio il comportamento del telefono
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
                || mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_CALL_SCREENING) return false;
        final int dir = k == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            // le ripetizioni del sistema si ignorano: la pressione lunga la gestisce il nostro timer,
            // così funziona uguale anche sui telefoni che non le mandano al servizio di accessibilità
            if (e.getRepeatCount() > 0) return true;
            if (!shown) stream = defaultStream();
            step(dir);
            ui.removeCallbacks(repeat);
            repeatDir = dir;
            ui.postDelayed(repeat, HOLD_DELAY);
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            ui.removeCallbacks(repeat);
        }
        return true;
    }

    /** Tenendo premuto: dopo un attimo il volume continua a salire o scendere finché si lascia il tasto. */
    private static final long HOLD_DELAY = 380, HOLD_EVERY = 90;
    private int repeatDir;
    private final Runnable repeat = new Runnable() {
        @Override
        public void run() {
            float before = level(stream);
            step(repeatDir);
            float after = level(stream);
            // arrivati in cima o in fondo ci si ferma (il tasto può restare premuto senza far nulla)
            if (after != before) ui.postDelayed(this, HOLD_EVERY);
        }
    };

    private void step(int dir) {
        adjust(stream, dir);
        show();
    }

    private int defaultStream() {
        if (am.isMusicActive()) return AudioManager.STREAM_MUSIC;
        return "ring".equals(prefs.getString("volIdle", "media")) ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC;
    }

    private void adjust(int s, int dir) {
        try {
            am.adjustStreamVolume(s, dir, 0); // 0: nessun pannello di sistema
        } catch (SecurityException ignored) {
            // abbassare la suoneria fino al silenzioso richiede l'accesso a "Non disturbare"
        }
    }

    private void set(int s, float frac) {
        int max = am.getStreamMaxVolume(s), min = minVol(s);
        int v = Math.round(min + Math.max(0f, Math.min(1f, frac)) * (max - min));
        try {
            am.setStreamVolume(s, v, 0);
        } catch (SecurityException ignored) {
        }
    }

    private int minVol(int s) {
        try {
            return android.os.Build.VERSION.SDK_INT >= 28 ? am.getStreamMinVolume(s) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private float level(int s) {
        int max = am.getStreamMaxVolume(s), min = minVol(s);
        if (max <= min) return 0f;
        return (am.getStreamVolume(s) - min) / (float) (max - min);
    }

    // ---------- finestra ----------

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        if (top()) {
            // capsula in alto, sotto la barra di stato
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = statusBar() + Math.round(10 * dp);
        } else {
            boolean left = "left".equals(prefs.getString("volSide", "right"));
            lp.gravity = (left ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
            lp.x = Math.round(14 * dp);
        }
        lp.windowAnimations = 0;
        return lp;
    }

    /** Stile scelto nelle impostazioni: pillola laterale (predefinita) o capsula in alto. */
    private boolean top() {
        return "top".equals(prefs.getString("volStyle", "side"));
    }

    /** File di puntini della pillola laterale: 1, 2 (predefinito) o 3. */
    private int lines() {
        return Math.max(1, Math.min(3, prefs.getInt("volDots", 2)));
    }

    private int statusBar() {
        int id = svc.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? svc.getResources().getDimensionPixelSize(id) : Math.round(24 * dp);
    }

    /** Da dove entra il pannello: dal lato scelto, o dall'alto. */
    private void enterFrom(View v, float f) {
        if (top()) {
            v.setTranslationX(0);
            v.setTranslationY(-f * 24 * dp);
        } else {
            boolean left = "left".equals(prefs.getString("volSide", "right"));
            v.setTranslationY(0);
            v.setTranslationX((left ? -1 : 1) * f * 24 * dp);
        }
    }

    private void show() {
        ui.removeCallbacks(hide);
        if (!shown) {
            expanded = false;
            view = new PanelView(svc);
            try {
                wm.addView(view, params());
                shown = true;
            } catch (Exception e) {
                view = null;
                return;
            }
            view.setAlpha(0f);
            enterFrom(view, 1f);
            view.animate().alpha(1f).translationX(0).translationY(0).setDuration(160).setInterpolator(Motion.EASE_OUT).start();
        } else {
            view.invalidate();
        }
        ui.postDelayed(hide, expanded ? 4500 : 2200);
    }

    void hide() {
        ui.removeCallbacks(hide);
        ui.removeCallbacks(repeat);
        if (!shown || view == null) return;
        final View v = view;
        shown = false;
        view = null;
        boolean up = top(), left = "left".equals(prefs.getString("volSide", "right"));
        v.animate().alpha(0f).translationX(up ? 0 : (left ? -1 : 1) * 24 * dp).translationY(up ? -24 * dp : 0)
                .setDuration(140).setInterpolator(Motion.EASE_IN)
                .withEndAction(() -> {
                    try {
                        wm.removeView(v);
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    private void expand() {
        if (view == null) return;
        expanded = true;
        view.requestLayout();
        view.setAlpha(0.4f);
        view.animate().alpha(1f).setDuration(140).start();
        show();
    }

    private void setRinger(int i) {
        try {
            am.setRingerMode(RINGER[i]);
        } catch (SecurityException e) {
            // serve l'accesso a "Non disturbare": si apre la pagina giusta
            hide();
            try {
                svc.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                android.widget.Toast.makeText(svc, T.t("Consenti \"Non disturbare\" a Dot Launcher"),
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
            }
        }
    }

    private int ringerIndex() {
        int m = am.getRingerMode();
        return m == AudioManager.RINGER_MODE_SILENT ? 2 : m == AudioManager.RINGER_MODE_VIBRATE ? 1 : 0;
    }

    // ---------- disegno ----------

    private final class PanelView extends View {
        private final Theme th;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path icon = new Path();
        private final int bg, fg, off, sub;
        private final Typeface digits;
        private float downX, downY;
        private boolean dragging;
        private int dragBar = -1;

        PanelView(Context c) {
            super(c);
            th = Theme.build(c, prefs);
            bg = th.light ? 0xF2F2F2F4 : 0xF0161618;
            fg = th.light ? 0xFF111111 : 0xFFF2F2F2;
            off = th.light ? 0x26000000 : 0x40FFFFFF;
            sub = th.light ? 0xFF6B6B6E : 0xFF9A9A9E;
            Typeface t = null;
            try {
                t = Typeface.createFromAsset(c.getAssets(), "fonts/Doto.ttf");
            } catch (Exception ignored) {
            }
            digits = t != null ? t : Typeface.MONOSPACE;
            setContentDescription(T.t("Volume"));
        }

        private int W() {
            if (expanded) return Math.round(220 * dp);
            if (top()) return Math.round(Math.min(340 * dp, getResources().getDisplayMetrics().widthPixels - 48 * dp));
            return Math.round((20 + 12 * lines()) * dp); // 32, 44 o 56 dp
        }

        private int H() {
            if (expanded) return Math.round(390 * dp);
            return Math.round((top() ? 52 : 290) * dp);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(W(), H());
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            r.set(0, 0, w, h);
            float rad = expanded ? 28 * dp : top() ? h / 2f : Math.min(22 * dp, w / 2f);
            cv.drawRoundRect(r, rad, rad, p);
            if (expanded) drawPanel(cv, w, h);
            else if (top()) drawCapsule(cv, w, h);
            else drawPill(cv, w, h);
        }

        /** Colonna di puntini (da 1 a 3 file) accesa dal basso fino al livello. */
        private void dots(Canvas cv, float cx, float top, float bot, int rows, float lv, int on, int files) {
            float step = (bot - top) / (rows - 1), rr = 3.3f * dp;
            int lit = Math.round(lv * rows);
            for (int i = 0; i < rows; i++) {
                float y = bot - i * step;
                p.setColor(i < lit ? on : off);
                for (int j = 0; j < files; j++) cv.drawCircle(cx + (j - (files - 1) / 2f) * 12 * dp, y, rr, p);
            }
        }

        // capsula in alto: altoparlante, una riga di puntini, percentuale
        private float rowLeft() {
            return 52 * dp;
        }

        private float rowRight() {
            return getWidth() - 52 * dp;
        }

        private void drawCapsule(Canvas cv, float w, float h) {
            float lv = level(stream), cy = h / 2f;
            drawIcon(cv, 28 * dp, cy, stream, lv <= 0f, fg, 0.85f);
            int n = Math.max(8, (int) ((rowRight() - rowLeft()) / (11 * dp)) + 1);
            float step = (rowRight() - rowLeft()) / (n - 1), rr = 3.6f * dp;
            int lit = Math.round(lv * n);
            for (int i = 0; i < n; i++) {
                p.setColor(i < lit ? th.accent : off);
                cv.drawCircle(rowLeft() + i * step, cy, rr, p);
            }
            p.setTypeface(digits);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(16 * dp);
            p.setColor(fg);
            cv.drawText(String.valueOf(Math.round(lv * 100)), w - 26 * dp, cy + 6 * dp, p);
        }

        private float pillTop() {
            return 50 * dp;
        }

        private float pillBot() {
            return getHeight() - 56 * dp;
        }

        private void drawPill(Canvas cv, float w, float h) {
            float lv = level(stream);
            p.setTypeface(digits);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize((lines() == 1 ? 14 : 18) * dp);
            p.setColor(fg);
            cv.drawText(String.valueOf(Math.round(lv * 100)), w / 2, 32 * dp, p);
            dots(cv, w / 2, pillTop(), pillBot(), 20, lv, th.accent, lines());
            drawIcon(cv, w / 2, h - 28 * dp, stream, lv <= 0f, fg, 0.9f);
        }

        private float barTop() {
            return 34 * dp;
        }

        private float barBot() {
            return getHeight() - 132 * dp;
        }

        private float colW() {
            return (getWidth() - 24 * dp) / 4f;
        }

        private void drawPanel(Canvas cv, float w, float h) {
            float top = barTop(), bot = barBot();
            p.setTextAlign(Paint.Align.CENTER);
            for (int k = 0; k < 4; k++) {
                float cx = 12 * dp + colW() * k + colW() / 2;
                float lv = level(STREAMS[k]);
                boolean sel = STREAMS[k] == stream;
                // percentuale sopra la colonna, a puntini come il resto
                p.setTypeface(digits);
                p.setTextSize(13 * dp);
                p.setColor(sel ? th.accent : sub);
                cv.drawText(String.valueOf(Math.round(lv * 100)), cx, top - 12 * dp, p);
                dots(cv, cx, top, bot, 18, lv, sel ? th.accent : fg, 2);
                drawIcon(cv, cx, bot + 24 * dp, STREAMS[k], lv <= 0f, sel ? th.accent : fg, 0.75f);
                p.setTypeface(th.bodyFace);
                p.setColor(sub);
                p.setTextSize(10 * dp);
                cv.drawText(T.t(NAMES[k]), cx, bot + 50 * dp, p);
            }
            // modalità suoneria: tre pastiglie, quella attiva piena
            int selMode = ringerIndex();
            float by = h - 40 * dp, bw = (w - 28 * dp) / 3f;
            p.setTypeface(th.bodyFace);
            for (int i = 0; i < 3; i++) {
                float bx = 14 * dp + i * bw;
                r.set(bx + 3 * dp, by - 16 * dp, bx + bw - 3 * dp, by + 16 * dp);
                p.setColor(i == selMode ? fg : off);
                cv.drawRoundRect(r, 16 * dp, 16 * dp, p);
                p.setColor(i == selMode ? (bg | 0xFF000000) : fg);
                p.setTextSize(11 * dp);
                cv.drawText(T.t(MODES[i]), bx + bw / 2, by + 4 * dp, p);
            }
        }

        /** Altoparlante per i media, campanella per suoneria e notifiche, sveglia per la sveglia. */
        private void drawIcon(Canvas cv, float cx, float cy, int s, boolean mute, int color, float scale) {
            float k = dp * scale;
            p.setColor(color);
            icon.reset();
            if (s == AudioManager.STREAM_MUSIC) {
                icon.moveTo(cx - 9 * k, cy - 4 * k);
                icon.lineTo(cx - 4 * k, cy - 4 * k);
                icon.lineTo(cx + 2 * k, cy - 10 * k);
                icon.lineTo(cx + 2 * k, cy + 10 * k);
                icon.lineTo(cx - 4 * k, cy + 4 * k);
                icon.lineTo(cx - 9 * k, cy + 4 * k);
                icon.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(icon, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * k);
                p.setStrokeCap(Paint.Cap.ROUND);
                if (!mute) {
                    r.set(cx - 2 * k, cy - 7 * k, cx + 10 * k, cy + 7 * k);
                    cv.drawArc(r, -50, 100, false, p);
                }
            } else if (s == AudioManager.STREAM_ALARM) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * k);
                p.setStrokeCap(Paint.Cap.ROUND);
                cv.drawCircle(cx, cy + k, 8 * k, p);
                cv.drawLine(cx, cy + k, cx, cy - 4 * k, p);
                cv.drawLine(cx, cy + k, cx + 3 * k, cy + 3 * k, p);
                cv.drawLine(cx - 9 * k, cy - 7 * k, cx - 6 * k, cy - 10 * k, p);
                cv.drawLine(cx + 9 * k, cy - 7 * k, cx + 6 * k, cy - 10 * k, p);
            } else {
                icon.moveTo(cx - 8 * k, cy + 5 * k);
                icon.lineTo(cx - 6 * k, cy + 2 * k);
                icon.lineTo(cx - 6 * k, cy - 3 * k);
                icon.cubicTo(cx - 6 * k, cy - 11 * k, cx + 6 * k, cy - 11 * k, cx + 6 * k, cy - 3 * k);
                icon.lineTo(cx + 6 * k, cy + 2 * k);
                icon.lineTo(cx + 8 * k, cy + 5 * k);
                icon.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(icon, p);
                cv.drawCircle(cx, cy + 8 * k, 2.2f * k, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * k);
                p.setStrokeCap(Paint.Cap.ROUND);
            }
            if (mute) cv.drawLine(cx - 10 * k, cy - 10 * k, cx + 10 * k, cy + 10 * k, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_OUTSIDE:
                    hide();
                    return true;
                case MotionEvent.ACTION_DOWN:
                    downY = e.getY();
                    downX = e.getX();
                    dragging = false;
                    dragBar = -1;
                    ui.removeCallbacks(hide);
                    if (expanded && e.getY() >= barTop() - 24 * dp && e.getY() <= barBot() + 60 * dp) {
                        dragBar = (int) Math.max(0, Math.min(3, (e.getX() - 12 * dp) / colW()));
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    boolean capsule = !expanded && top();
                    if (!dragging && (capsule ? Math.abs(e.getX() - downX) : Math.abs(e.getY() - downY)) > slop) dragging = true;
                    if (dragging) {
                        if (capsule) {
                            set(stream, (e.getX() - rowLeft()) / (rowRight() - rowLeft()));
                        } else if (!expanded) {
                            set(stream, (pillBot() - e.getY()) / (pillBot() - pillTop()));
                        } else if (dragBar >= 0) {
                            stream = STREAMS[dragBar];
                            set(stream, (barBot() - e.getY()) / (barBot() - barTop()));
                        }
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) {
                        if (!expanded) expand();
                        else if (e.getY() > getHeight() - 64 * dp) {
                            float bw = (getWidth() - 28 * dp) / 3f;
                            int i = (int) Math.max(0, Math.min(2, (e.getX() - 14 * dp) / bw));
                            setRinger(i);
                        } else if (dragBar >= 0) {
                            stream = STREAMS[dragBar];
                            set(stream, (barBot() - e.getY()) / (barBot() - barTop()));
                        }
                    }
                    invalidate();
                    ui.postDelayed(hide, expanded ? 4500 : 2200);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    ui.postDelayed(hide, 2200);
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
