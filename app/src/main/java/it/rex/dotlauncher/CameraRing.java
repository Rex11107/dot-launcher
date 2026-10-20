package it.rex.dotlauncher;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * Anello attorno alla fotocamera nello schermo: mostra l'avanzamento di download e caricamenti, il tempo
 * che resta di un timer e la carica della batteria. La posizione e la forma del foro le dice Android
 * (DisplayCutout), quindi l'anello si adatta da solo a ogni telefono; dalle impostazioni si può comunque
 * spostare e ridimensionare per i telefoni che comunicano misure imprecise.
 * Si disegna sopra le altre app con il servizio di accessibilità, non riceve tocchi e si nasconde quando
 * un'app va a schermo intero (barra di stato nascosta).
 */
final class CameraRing implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREF = "ringOn";

    // ---------- cosa mostrare: aggiornato dalle notifiche ----------

    /** Avanzamento di un trasferimento: 0..1, -2 se senza percentuale, -1 se non c'è. */
    private static volatile float transfer = -1f;
    /** Timer: istante di fine (ms dall'epoca) e durata totale stimata, 0 se non c'è. */
    private static volatile long timerEnd, timerTotal, lastTimerSeen;
    /** Ultimo tempo letto per ogni notifica dell'orologio: {millisecondi che restano, quando}. */
    private static final java.util.Map<String, long[]> seenTimes = new java.util.HashMap<>();

    /** App dell'orologio (di sistema o del produttore). */
    private static boolean isClock(String pkg) {
        String p = pkg == null ? "" : pkg.toLowerCase(java.util.Locale.ROOT);
        return p.contains("deskclock") || p.endsWith(".clock") || p.contains("clock") && !p.contains("dotlauncher");
    }

    private static final java.util.regex.Pattern TIME = java.util.regex.Pattern.compile("(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})");

    /** Millisecondi da un testo come "04:12" o "1:02:30"; -1 se non c'è un tempo. */
    private static long timeLeft(CharSequence cs) {
        if (cs == null) return -1;
        java.util.regex.Matcher m = TIME.matcher(cs);
        if (!m.find()) return -1;
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        return ((h * 60 + Long.parseLong(m.group(2))) * 60 + Long.parseLong(m.group(3))) * 1000L;
    }
    private static CameraRing instance;

    /** Dalle notifiche attive (chiamato dal servizio delle notifiche a ogni cambiamento). */
    static void readNotifications(StatusBarNotification[] all) {
        float tr = -1f;
        long tEnd = 0;
        long now = System.currentTimeMillis();
        if (all != null) {
            for (StatusBarNotification n : all) {
                Notification no = n.getNotification();
                if (no == null || no.extras == null) continue;
                int max = no.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
                int prog = no.extras.getInt(Notification.EXTRA_PROGRESS, 0);
                boolean ind = no.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
                if (max > 0 && tr < 0) tr = Math.max(0f, Math.min(1f, prog / (float) max));
                else if (ind && tr == -1f) tr = -2f;
                // timer: notifica con cronometro che conta verso un istante futuro (conto alla rovescia)…
                long end = 0;
                boolean chrono = no.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
                        || no.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
                if (chrono && no.when > now) end = no.when;
                // …oppure, nell'app Orologio, un testo con il tempo che resta ("04:12", "1:02:30")
                if (end == 0 && isClock(n.getPackageName())) {
                    long left = timeLeft(no.extras.getCharSequence(Notification.EXTRA_TITLE));
                    if (left < 0) left = timeLeft(no.extras.getCharSequence(Notification.EXTRA_TEXT));
                    // vale solo se scende davvero col passare del tempo: "prossima sveglia 07:30" resta fermo
                    if (seenTimes.size() > 30) seenTimes.clear();
                    long[] prev = seenTimes.get(n.getKey());
                    if (left > 0) {
                        if (prev != null && left < prev[0] && Math.abs((prev[0] - left) - (now - prev[1])) < 2500) end = now + left;
                        if (prev == null || left != prev[0]) seenTimes.put(n.getKey(), new long[]{left, now});
                    }
                }
                if (end > 0 && (tEnd == 0 || end < tEnd)) tEnd = end;
            }
        }
        transfer = tr;
        if (tEnd == 0 && timerEnd > now && now - lastTimerSeen < 3000) tEnd = timerEnd; // tra un aggiornamento e l'altro
        if (tEnd != 0) lastTimerSeen = now;
        if (tEnd == 0) {
            timerEnd = 0;
            timerTotal = 0;
        } else {
            // la durata totale non c'è nella notifica: si prende quella vista la prima volta
            if (Math.abs(tEnd - timerEnd) > 1500 || timerTotal == 0) timerTotal = Math.max(1000, tEnd - now);
            timerEnd = tEnd;
        }
        CameraRing r = instance;
        if (r != null) r.ui.post(r::refresh);
    }

    // ---------- finestra ----------

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float dp;
    private RingView view;
    private boolean added;
    private int battery = -1;
    private boolean charging;

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            battery = lv < 0 ? -1 : Math.round(lv * 100f / Math.max(1, sc));
            charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            refresh();
        }
    };

    CameraRing(AccessibilityService s) {
        svc = s;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        prefs = s.getSharedPreferences("dot", Context.MODE_PRIVATE);
        dp = s.getResources().getDisplayMetrics().density;
    }

    static boolean enabled(SharedPreferences p) {
        return p.getBoolean(PREF, false);
    }

    void start() {
        instance = this;
        prefs.registerOnSharedPreferenceChangeListener(this);
        try {
            svc.registerReceiver(power, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception ignored) {
        }
        DotNotifications.requestRing();
        refresh();
    }

    void stop() {
        if (instance == this) instance = null;
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        try {
            svc.unregisterReceiver(power);
        } catch (Exception ignored) {
        }
        ui.removeCallbacksAndMessages(null);
        remove();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key != null && key.startsWith("ring")) {
            if (view != null) view.requestApplyInsets();
            refresh();
        }
    }

    private void add() {
        if (added) return;
        view = new RingView(svc);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, Math.round(90 * dp),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        if (Build.VERSION.SDK_INT >= 30) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        else lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            wm.addView(view, lp);
            added = true;
        } catch (Exception e) {
            view = null;
        }
    }

    private void remove() {
        if (!added || view == null) return;
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
        added = false;
        view = null;
    }

    /** Cosa mostrare adesso, in ordine: trasferimento, timer, ricarica (o la prova della regolazione). */
    private int mode() {
        if (prefs.getBoolean("ringTest", false)) return 4;
        if (prefs.getBoolean("ringTransfer", true) && transfer != -1f) return 1;
        if (prefs.getBoolean("ringTimer", true) && timerEnd > System.currentTimeMillis()) return 2;
        if (prefs.getBoolean("ringCharge", true) && charging && battery >= 0) return 3;
        return 0;
    }

    private void refresh() {
        if (!enabled(prefs) || mode() == 0) {
            remove();
            return;
        }
        add();
        if (view != null) view.invalidate();
    }

    // ---------- disegno ----------

    private final class RingView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path ring = new Path(), part = new Path();
        private final PathMeasure pm = new PathMeasure();
        private final RectF hole = new RectF();
        private final float[] pos = new float[2];
        private final Theme th;
        private final Typeface digits;
        private boolean barsHidden, haveCutout, seenBars, legacyFull;

        RingView(Context c) {
            super(c);
            th = Theme.build(c, prefs);
            Typeface t = null;
            try {
                t = Typeface.createFromAsset(c.getAssets(), "fonts/Doto.ttf");
            } catch (Exception ignored) {
            }
            digits = t != null ? t : Typeface.MONOSPACE;
            // segnale in più per lo schermo intero: alcune versioni di Android lo comunicano solo così
            //noinspection deprecation
            setOnSystemUiVisibilityChangeListener(v -> {
                legacyFull = (v & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
                invalidate();
            });
        }

        @Override
        protected void onConfigurationChanged(android.content.res.Configuration c) {
            super.onConfigurationChanged(c);
            requestApplyInsets();
            invalidate();
        }

        /** In orizzontale (video, giochi) o a schermo intero l'anello non si mostra. */
        private boolean hiddenNow() {
            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return true;
            return barsHidden || legacyFull;
        }

        @Override
        public WindowInsets onApplyWindowInsets(WindowInsets in) {
            // schermo intero (video, giochi): niente anello
            // (solo se Android ha già detto almeno una volta che la barra c'è: alcune finestre sovrapposte
            // non ricevono questa informazione, e lì l'anello resta sempre visibile)
            if (Build.VERSION.SDK_INT >= 30) {
                boolean vis = in.isVisible(WindowInsets.Type.statusBars());
                if (vis) seenBars = true;
                barsHidden = seenBars && !vis;
            }
            haveCutout = false;
            DisplayCutout cut = in.getDisplayCutout();
            if (cut != null) {
                // il foro in alto: tra i rettangoli del ritaglio, quello che tocca il bordo superiore
                int sw = getResources().getDisplayMetrics().widthPixels;
                for (Rect r : cut.getBoundingRects()) {
                    // foro nella parte alta dello schermo e non più largo di metà schermo (non un notch enorme)
                    if (r.top < 48 * dp && r.bottom < 90 * dp && r.width() < sw * 0.5f) {
                        hole.set(r);
                        haveCutout = true;
                        break;
                    }
                }
            }
            invalidate();
            return super.onApplyWindowInsets(in);
        }

        /** Rettangolo del foro, con la regolazione manuale dalle impostazioni. */
        private RectF holeRect() {
            RectF h = new RectF();
            if (haveCutout) {
                h.set(hole);
                // i rettangoli del ritaglio partono dal bordo: il foro vero è la parte bassa, quasi quadrata
                if (h.height() > h.width() * 1.15f && h.width() < 70 * dp) h.top = h.bottom - h.width();
            } else {
                // nessun foro comunicato: in alto al centro, misura di un foro comune
                float cx = getWidth() / 2f, cy = 14 * dp, r = 6 * dp;
                h.set(cx - r, cy - r, cx + r, cy + r);
            }
            float dx = prefs.getInt("ringDx", 0) * dp / 2f, dy = prefs.getInt("ringDy", 0) * dp / 2f;
            float sc = 1f + prefs.getInt("ringSize", 0) / 20f;
            float cx = h.centerX() + dx, cy = h.centerY() + dy, hw = h.width() / 2f * sc, hh = h.height() / 2f * sc;
            h.set(cx - hw, cy - hh, cx + hw, cy + hh);
            return h;
        }

        /** Contorno attorno al foro (cerchio o pillola), che parte in alto al centro e gira in senso orario. */
        private void buildRing(RectF h, float gap) {
            RectF o = new RectF(h.left - gap, h.top - gap, h.right + gap, h.bottom + gap);
            float rr = Math.min(o.width(), o.height()) / 2f;
            ring.reset();
            ring.moveTo(o.centerX(), o.top);
            ring.lineTo(o.right - rr, o.top);
            ring.arcTo(new RectF(o.right - 2 * rr, o.top, o.right, o.top + 2 * rr), -90, 90, false);
            ring.lineTo(o.right, o.bottom - rr);
            ring.arcTo(new RectF(o.right - 2 * rr, o.bottom - 2 * rr, o.right, o.bottom), 0, 90, false);
            ring.lineTo(o.left + rr, o.bottom);
            ring.arcTo(new RectF(o.left, o.bottom - 2 * rr, o.left + 2 * rr, o.bottom), 90, 90, false);
            ring.lineTo(o.left, o.top + rr);
            ring.arcTo(new RectF(o.left, o.top, o.left + 2 * rr, o.top + 2 * rr), 180, 90, false);
            ring.close();
            pm.setPath(ring, false);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (hiddenNow()) {
                postInvalidateDelayed(1000); // si ricontrolla: quando si torna in verticale ricompare
                return;
            }
            int mode = mode();
            if (mode == 0) {
                ui.post(CameraRing.this::refresh); // finito (timer scaduto): la finestra si toglie
                return;
            }
            float frac;
            int col;
            String label;
            boolean spin = false;
            long now = System.currentTimeMillis();
            switch (mode) {
                case 1:
                    spin = transfer == -2f;
                    frac = spin ? 0.25f : transfer;
                    col = th.accent;
                    label = spin ? "" : Math.round(transfer * 100) + "%";
                    break;
                case 2:
                    long left = Math.max(0, timerEnd - now);
                    frac = timerTotal > 0 ? left / (float) timerTotal : 0f;
                    col = 0xFFF2F2F2;
                    long s = (left + 999) / 1000;
                    label = s >= 3600 ? (s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60) : (s / 60) + ":" + two(s % 60);
                    break;
                case 3:
                    frac = battery / 100f;
                    col = 0xFF3CC86E;
                    label = battery + "%";
                    break;
                default: // prova della regolazione
                    frac = 0.75f;
                    col = th.accent;
                    label = "75%";
            }
            RectF h = holeRect();
            boolean dots = !"line".equals(prefs.getString("ringStyle", "dots"));
            buildRing(h, (dots ? 4.5f : 3.5f) * dp);
            float len = pm.getLength();
            float start = 0f;
            if (spin) start = ((SystemClock.uptimeMillis() % 1200) / 1200f) * len;
            if (dots) {
                int n = Math.max(16, Math.round(len / (3.4f * dp)));
                for (int i = 0; i < n; i++) {
                    float d = i * len / n;
                    pm.getPosTan(d, pos, null);
                    float rel = ((d - start) % len + len) % len / len;
                    boolean on = rel < frac;
                    p.setColor(on ? col : 0x55FFFFFF);
                    c.drawCircle(pos[0], pos[1], (on ? 1.25f : 0.9f) * dp, p);
                }
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(1.6f * dp);
                p.setColor(0x40FFFFFF);
                c.drawPath(ring, p);
                part.reset();
                float a = start, b = start + frac * len;
                if (b <= len) pm.getSegment(a, b, part, true);
                else {
                    pm.getSegment(a, len, part, true);
                    pm.getSegment(0, b - len, part, true);
                }
                p.setColor(col);
                p.setStrokeWidth(2f * dp);
                c.drawPath(part, p);
                p.setStyle(Paint.Style.FILL);
            }
            if (prefs.getBoolean("ringText", true) && !label.isEmpty()) {
                p.setTypeface(digits);
                p.setTextSize(11 * dp);
                p.setColor(col);
                // il numero va dal lato con più spazio
                boolean right = h.centerX() < getWidth() * 0.6f;
                p.setTextAlign(right ? Paint.Align.LEFT : Paint.Align.RIGHT);
                float x = right ? h.right + 9 * dp : h.left - 9 * dp;
                c.drawText(label, x, h.centerY() + 4 * dp, p);
            }
            // si ridisegna solo se qualcosa si muove: rotazione, timer (ogni secondo)
            if (spin) postInvalidateDelayed(50);
            else if (mode == 2) postInvalidateDelayed(1000 - now % 1000);
        }
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
