package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Widget in stile Nothing disegnato interamente sul Canvas. */
class NothingTile extends View {
    final Item item;
    private final Theme th;
    private final boolean h24;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final int[] loc = new int[2];
    private final Rect src = new Rect();

    // ---------- animazioni ----------
    private static final long DIGIT_MS = 520, HAND_MS = 420;
    private final String[] aCur = new String[3], aPrev = new String[3];
    private final long[] aTime = new long[3];
    private boolean animating;
    private int lastMin = -1;
    private float fromMin;
    private long handTime;

    /** Progresso per carattere: le cifre appena cambiate si "accendono" punto per punto. */
    private float[] prog(int slot, String text) {
        String t = DotTextView.normalize(text);
        long now = SystemClock.uptimeMillis();
        if (!t.equals(aCur[slot])) {
            if (aCur[slot] != null && Draw.anim) {
                aPrev[slot] = aCur[slot];
                aTime[slot] = now;
            }
            aCur[slot] = t;
        }
        if (aPrev[slot] == null) return null;
        float k = (now - aTime[slot]) / (float) DIGIT_MS;
        if (k >= 1f) {
            aPrev[slot] = null;
            return null;
        }
        animating = true;
        String pv = aPrev[slot];
        float[] out = new float[t.length()];
        for (int i = 0; i < t.length(); i++) {
            boolean changed = pv.length() != t.length() || pv.charAt(i) != t.charAt(i);
            out[i] = changed ? k : 1f;
        }
        return out;
    }

    private boolean chargeAnim;
    private final Rect visRect = new Rect();

    /** Fase 0..1 del ciclo di ricarica. */
    private float phase(long period) {
        return (SystemClock.uptimeMillis() % period) / (float) period;
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == VISIBLE) invalidate();
    }

    NothingTile(Context c, Item item, Theme th, boolean h24) {
        super(c);
        this.item = item;
        this.th = th;
        this.h24 = h24;
    }

    // ---------- accessibilità: cosa legge TalkBack ----------

    @Override
    public CharSequence getContentDescription() {
        String name = Widgets.name(item.type);
        String v = describe();
        return v == null || v.isEmpty() ? name : name + ": " + v;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Button.class.getName());
    }

    /** Il contenuto del widget in parole. */
    private String describe() {
        java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(getContext());
        switch (item.type) {
            case "clock_dots":
            case "clock_analog":
            case "clock_pill":
            case "clock_ring":
                return tf.format(new Date());
            case "date_big":
            case "date_dot":
            case "calendar":
            case "week":
            case "day":
                return new SimpleDateFormat("EEEE d MMMM", T.loc()).format(new Date());
            case "weather":
            case "weather_week":
            case "weather_hours":
                return State.wOk ? State.wTemp + "°, " + T.t(State.describe(State.wCode))
                        + (State.wCity.isEmpty() ? "" : ", " + State.wCity) : T.t("Tocca per il meteo");
            case "battery_ring":
            case "battery_pill":
            case "battery_dots":
                return State.battery < 0 ? "" : State.battery + "%" + (State.charging ? ", " + T.t("in carica") : "");
            case "alarm":
                return State.nextAlarm.isEmpty() ? T.t("Nessuna sveglia") : State.nextAlarm;
            case "steps":
                return State.steps + " " + T.t("passi");
            case "moon":
                return T.t(moonName(moonAge()));
            case "sun":
                return State.sunrise.isEmpty() ? "" : State.sunrise + " – " + State.sunset;
            case "counter":
                return String.valueOf(WData.getLong(item, "n", 0));
            case "note":
                return WData.get(item, "t", "");
            case "proverb":
                return proverb(item);
            case "media":
                return DotNotifications.mActive ? DotNotifications.mTitle + " " + DotNotifications.mArtist
                        : T.t("Niente in riproduzione");
            case "usage":
                return Usage.permitted ? T.t("Oggi: ") + Usage.fmt(Usage.total) : T.t("Tocca per attivare");
            case "focus":
                return Usage.focusActive ? T.t("Attivo") : T.t("Spento");
            case "torch":
                return State.torchOn ? T.t("Attivo") : T.t("Spento");
            default:
                return "";
        }
    }

    private int bgColor() {
        return item.tone == 1 ? th.tileAlt : item.tone == 2 ? th.accent : th.tile;
    }

    private int fg() {
        return item.tone == 1 ? th.onTileAlt : item.tone == 2 ? th.onAccent : th.onTile;
    }

    private int sub() {
        if (item.tone == 2) return Theme.alpha(th.onAccent, 0.8f);
        if (item.tone == 1) return Theme.alpha(th.onTileAlt, 0.6f);
        return th.sub;
    }

    private int acc() {
        return item.tone == 2 ? th.onAccent : th.accent;
    }

    private boolean isCircle() {
        return item.w == 1 && item.h == 1;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        animating = false;
        chargeAnim = false;
        drawShape(cv, W, H);
        switch (item.type) {
            case "clock_dots": clockDots(cv, W, H); break;
            case "clock_analog": clockAnalog(cv, W, H); break;
            case "clock_pill": clockPill(cv, W, H); break;
            case "date_big": dateBig(cv, W, H); break;
            case "date_dot": dateDot(cv, W, H); break;
            case "calendar": calendar(cv, W, H); break;
            case "weather": weather(cv, W, H); break;
            case "weather_week": weatherWeek(cv, W, H); break;
            case "battery_ring": batteryRing(cv, W, H); break;
            case "battery_pill": batteryPill(cv, W, H); break;
            case "battery_dots": batteryDots(cv, W, H); break;
            case "alarm": alarm(cv, W, H); break;
            case "search": search(cv, W, H); break;
            case "compass": compass(cv, W, H); break;
            case "countdown": countdown(cv, W, H); break;
            case "world_clock": worldClock(cv, W, H); break;
            case "steps": steps(cv, W, H); break;
            case "photo": photo(cv, W, H); break;
            case "year": year(cv, W, H); break;
            case "day": dayProgress(cv, W, H); break;
            case "clock_ring": clockRing(cv, W, H); break;
            case "moon": moon(cv, W, H); break;
            case "sun": sun(cv, W, H); break;
            case "weather_hours": weatherHours(cv, W, H); break;
            case "counter": counter(cv, W, H); break;
            case "proverb": proverb(cv, W, H); break;
            case "dice": dice(cv, W, H); break;
            case "week": week(cv, W, H); break;
            case "storage": storage(cv, W, H); break;
            case "note": note(cv, W, H); break;
            case "torch": torch(cv, W, H); break;
            case "media": media(cv, W, H); break;
            case "usage": usage(cv, W, H); break;
            case "focus": focus(cv, W, H); break;
        }
        if (animating) postInvalidateOnAnimation();
        // l'animazione di ricarica continua solo se si vede davvero (pagina corrente, home non coperta)
        else if (chargeAnim && getWindowVisibility() == VISIBLE && isShown() && getGlobalVisibleRect(visRect))
            postInvalidateDelayed(50);
    }

    private void drawShape(Canvas cv, float W, float H) {
        p.setStyle(Paint.Style.FILL);
        float m = Math.min(W, H);
        if (th.glass && item.tone == 0) drawGlass(cv, W, H, m);
        p.setColor(bgColor());
        if (isCircle()) {
            cv.drawCircle(W / 2f, H / 2f, m / 2f, p);
        } else {
            float r = (item.w == 1 || item.h == 1) ? m / 2f : Theme.radius(m, getResources().getDisplayMetrics().density);
            rf.set(0, 0, W, H);
            cv.drawRoundRect(rf, r, r, p);
        }
        if (th.stroke != 0 && item.tone == 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
            p.setColor(th.stroke);
            if (isCircle()) {
                cv.drawCircle(W / 2f, H / 2f, m / 2f - 1, p);
            } else {
                float r = (item.w == 1 || item.h == 1) ? m / 2f : Theme.radius(m, getResources().getDisplayMetrics().density);
                rf.set(1, 1, W - 1, H - 1);
                cv.drawRoundRect(rf, r, r, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** Vetro smerigliato: la parte di sfondo sfocato che sta dietro la tessera. */
    private void drawGlass(Canvas cv, float W, float H, float m) {
        Bitmap g = th.glassBmp;
        View root = getRootView();
        if (g == null || root == null || root.getWidth() == 0) return;
        getLocationOnScreen(loc);
        float sx = g.getWidth() / (float) root.getWidth();
        float sy = g.getHeight() / (float) root.getHeight();
        src.set(Math.round(loc[0] * sx), Math.round(loc[1] * sy),
                Math.round((loc[0] + W) * sx), Math.round((loc[1] + H) * sy));
        clip.reset();
        if (isCircle()) {
            clip.addCircle(W / 2f, H / 2f, m / 2f, Path.Direction.CW);
        } else {
            float r = (item.w == 1 || item.h == 1) ? m / 2f : Theme.radius(m, getResources().getDisplayMetrics().density);
            clip.addRoundRect(0, 0, W, H, r, r, Path.Direction.CW);
        }
        cv.save();
        cv.clipPath(clip);
        rf.set(0, 0, W, H);
        cv.drawBitmap(g, src, rf, bp);
        cv.restore();
    }

    private String time(String pattern24, String pattern12) {
        return new SimpleDateFormat(h24 ? pattern24 : pattern12, T.loc()).format(new Date());
    }

    // ---------- orologi ----------

    private void clockDots(Canvas cv, float W, float H) {
        float pad = Math.min(W, H) * 0.22f;
        String t = time("HH:mm", "h:mm");
        float h = Math.min(H - pad * 2, H * 0.62f);
        if (item.h >= 2) h = H * 0.42f;
        Draw.big(cv, th, t, W / 2f, item.h >= 2 ? H * 0.42f : H / 2f, h, W - pad * 2,
                fg(), ":", acc(), 0, p, true, prog(0, t));
        if (item.h >= 2) {
            String d = new SimpleDateFormat("EEEE d MMMM", T.loc()).format(new Date());
            Draw.label(cv, th, d, W / 2f, H * 0.82f, H * 0.075f, sub(), 0, p, W - pad * 2);
        }
    }

    private void clockAnalog(Canvas cv, float W, float H) {
        float cx = W / 2f, cy = H / 2f;
        float R = Math.min(W, H) / 2f;
        p.setStyle(Paint.Style.FILL);
        // tacche a puntini
        p.setColor(Theme.alpha(fg(), 0.35f));
        for (int i = 0; i < 12; i++) {
            if (i == 6) continue; // al suo posto c'è il punto rosso
            double a = Math.PI * 2 * i / 12;
            float rr = R * (i % 3 == 0 ? 0.035f : 0.022f);
            cv.drawCircle(cx + (float) Math.sin(a) * R * 0.8f, cy - (float) Math.cos(a) * R * 0.8f, rr, p);
        }
        Calendar c = Calendar.getInstance();
        int curMin = c.get(Calendar.MINUTE);
        float min = curMin;
        if (lastMin >= 0 && curMin != lastMin && Draw.anim) {
            int delta = (curMin - lastMin + 60) % 60;
            if (delta <= 2) {
                fromMin = lastMin;
                handTime = SystemClock.uptimeMillis();
            }
        }
        lastMin = curMin;
        if (handTime > 0) {
            float k = (SystemClock.uptimeMillis() - handTime) / (float) HAND_MS;
            if (k < 1f) {
                float delta = (curMin - fromMin + 60) % 60;
                min = fromMin + delta * Draw.easeOutBack(k);
                animating = true;
            } else handTime = 0;
        }
        float hr = c.get(Calendar.HOUR) + min / 60f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        // minuti: sottile e grigia
        p.setStrokeWidth(R * 0.07f);
        p.setColor(sub());
        double am = Math.PI * 2 * min / 60;
        cv.drawLine(cx, cy, cx + (float) Math.sin(am) * R * 0.66f, cy - (float) Math.cos(am) * R * 0.66f, p);
        // ore: spessa
        p.setStrokeWidth(R * 0.16f);
        p.setColor(fg());
        double ah = Math.PI * 2 * hr / 12;
        cv.drawLine(cx, cy, cx + (float) Math.sin(ah) * R * 0.42f, cy - (float) Math.cos(ah) * R * 0.42f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(acc());
        cv.drawCircle(cx, cy + R * 0.8f, R * 0.055f, p);
        p.setColor(fg());
        cv.drawCircle(cx, cy, R * 0.06f, p);
    }

    private void clockPill(Canvas cv, float W, float H) {
        if (H > W) {
            float h = W * 0.38f;
            String hh = time("HH", "hh"), mm = time("mm", "mm");
            Draw.big(cv, th, hh, W / 2f, H * 0.33f, h, W * 0.7f, fg(), "", acc(), 0, p, false, prog(1, hh));
            Draw.big(cv, th, mm, W / 2f, H * 0.67f, h, W * 0.7f, fg(), "", acc(), 0, p, false, prog(2, mm));
            p.setColor(acc());
            cv.drawCircle(W / 2f, H / 2f, W * 0.035f, p);
        } else {
            String t = time("HH:mm", "h:mm");
            Draw.big(cv, th, t, W / 2f, H / 2f, H * 0.4f, W * 0.72f, fg(), ":", acc(), 0, p, false, prog(0, t));
        }
    }

    // ---------- data e calendario ----------

    private void dateBig(Canvas cv, float W, float H) {
        Date d = new Date();
        String wd = new SimpleDateFormat("EEEE", T.loc()).format(d);
        String day = new SimpleDateFormat("d", T.loc()).format(d);
        String mo = new SimpleDateFormat("MMMM", T.loc()).format(d);
        Draw.label(cv, th, wd, W / 2f, H * 0.2f, H * 0.075f, fg(), 0, p, W * 0.8f);
        Draw.big(cv, th, day, W / 2f, H * 0.52f, H * 0.36f, W * 0.8f, acc(), "", acc(), 0, p, false);
        Draw.label(cv, th, mo, W / 2f, H * 0.88f, H * 0.075f, fg(), 0, p, W * 0.8f);
    }

    private void dateDot(Canvas cv, float W, float H) {
        Date d = new Date();
        String wd = new SimpleDateFormat("EEE", T.loc()).format(d).replace(".", "");
        String day = new SimpleDateFormat("d", T.loc()).format(d);
        Draw.label(cv, th, wd, W / 2f, H * 0.32f, H * 0.11f, acc(), 0, p, W * 0.6f);
        Draw.big(cv, th, day, W / 2f, H * 0.58f, H * 0.26f, W * 0.6f, fg(), "", acc(), 0, p, false);
    }

    private void calendar(Canvas cv, float W, float H) {
        Calendar c = Calendar.getInstance();
        int today = c.get(Calendar.DAY_OF_MONTH);
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar first = (Calendar) c.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7; // lunedì = 0
        int weeks = (offset + days + 6) / 7;
        float pad = Math.min(W, H) * 0.1f;
        float headerH = H * 0.2f;
        String mo = new SimpleDateFormat("MMMM", T.loc()).format(c.getTime());
        Draw.big(cv, th, mo, pad, pad + headerH * 0.35f, headerH * 0.42f, W * 0.6f, fg(), "", acc(), -1, p, true);
        float gx = pad, gw = W - pad * 2;
        float gy = pad + headerH * 0.8f, gh = H - gy - pad * 0.7f;
        float cw = gw / 7f, ch = gh / (weeks + 1);
        float ts = Math.min(ch * 0.5f, cw * 0.42f);
        String[] wn = T.en ? new String[]{"M", "T", "W", "T", "F", "S", "S"} : new String[]{"L", "M", "M", "G", "V", "S", "D"};
        p.setTypeface(th.bodyFace);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(ts);
        for (int i = 0; i < 7; i++) {
            p.setColor(i >= 5 ? acc() : sub());
            cv.drawText(wn[i], gx + cw * i + cw / 2f, gy + ch * 0.5f + ts * 0.36f, p);
        }
        for (int dd = 1; dd <= days; dd++) {
            int idx = offset + dd - 1;
            int col = idx % 7, row = idx / 7 + 1;
            float x = gx + cw * col + cw / 2f;
            float y = gy + ch * row + ch / 2f;
            if (dd == today) {
                p.setColor(acc());
                cv.drawCircle(x, y, Math.min(cw, ch) * 0.44f, p);
                p.setColor(item.tone == 2 ? th.accent : th.onAccent);
            } else {
                p.setColor(dd < today ? sub() : fg());
            }
            cv.drawText(String.valueOf(dd), x, y + ts * 0.36f, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    // ---------- meteo ----------

    private void weather(Canvas cv, float W, float H) {
        if (!State.wOk) {
            Draw.icon(cv, Draw.CLOUD, W / 2f, H * 0.42f, Math.min(W, H) * 0.3f, p, fg(), acc());
            Draw.label(cv, th, T.t("Tocca per il meteo"), W / 2f, H * 0.78f, Math.min(W, H) * 0.08f, sub(), 0, p, W * 0.8f);
            return;
        }
        String temp = State.wTemp + "°";
        if (isCircle()) {
            Draw.icon(cv, Draw.weatherIcon(State.wCode), W / 2f, H * 0.34f, W * 0.26f, p, fg(), acc());
            Draw.big(cv, th, temp, W / 2f, H * 0.66f, H * 0.17f, W * 0.6f, fg(), "°", acc(), 0, p, false);
            return;
        }
        float pad = Math.min(W, H) * 0.14f;
        if (item.h == 1) {
            Draw.icon(cv, Draw.weatherIcon(State.wCode), pad + H * 0.25f, H / 2f, H * 0.42f, p, fg(), acc());
            Draw.big(cv, th, temp, W - pad, H / 2f, H * 0.36f, W * 0.45f, fg(), "°", acc(), 1, p, false);
            return;
        }
        Draw.icon(cv, Draw.weatherIcon(State.wCode), pad + W * 0.12f, pad + W * 0.12f, W * 0.24f, p, fg(), acc());
        Draw.label(cv, th, "↑" + State.wMax + "°  ↓" + State.wMin + "°", W - pad, pad + W * 0.1f, H * 0.07f, sub(), 1, p, W * 0.5f);
        Draw.label(cv, th, State.describe(State.wCode), pad, H * 0.6f, H * 0.075f, sub(), -1, p, W - pad * 2);
        Draw.big(cv, th, temp, pad, H * 0.78f, H * 0.2f, W - pad * 2, fg(), "°", acc(), -1, p, false);
    }

    private void weatherWeek(Canvas cv, float W, float H) {
        float pad = Math.min(W, H) * 0.12f;
        if (!State.wOk) {
            Draw.label(cv, th, T.t("Tocca per il meteo"), W / 2f, H / 2f, H * 0.08f, sub(), 0, p, W * 0.8f);
            return;
        }
        Draw.icon(cv, Draw.weatherIcon(State.wCode), pad + H * 0.12f, pad + H * 0.12f, H * 0.24f, p, fg(), acc());
        Draw.big(cv, th, State.wTemp + "°", pad + H * 0.32f, pad + H * 0.12f, H * 0.2f, W * 0.3f, fg(), "°", acc(), -1, p, false);
        String right = State.wCity.isEmpty() ? State.describe(State.wCode) : State.wCity;
        Draw.label(cv, th, right, W - pad, pad + H * 0.08f, H * 0.065f, fg(), 1, p, W * 0.4f);
        Draw.label(cv, th, State.describe(State.wCode), W - pad, pad + H * 0.18f, H * 0.06f, sub(), 1, p, W * 0.4f);
        int n = Math.min(5, State.dCode.length - 1);
        if (n <= 0) return;
        float top = H * 0.5f;
        float cw = (W - pad * 2) / n;
        for (int i = 0; i < n; i++) {
            int k = i + 1;
            float x = pad + cw * i + cw / 2f;
            Draw.label(cv, th, State.dName[k], x, top + H * 0.06f, H * 0.06f, i == 0 ? acc() : sub(), 0, p, cw * 0.9f);
            Draw.icon(cv, Draw.weatherIcon(State.dCode[k]), x, top + H * 0.2f, H * 0.14f, p, fg(), acc());
            Draw.label(cv, th, State.dMax[k] + "°", x, top + H * 0.38f, H * 0.065f, fg(), 0, p, cw * 0.9f);
        }
    }

    // ---------- batteria ----------

    private int pct() {
        return Math.max(0, State.battery);
    }

    private void batteryRing(Canvas cv, float W, float H) {
        float cx = W / 2f, cy = H / 2f;
        float R = Math.min(W, H) * (isCircle() ? 0.34f : 0.3f);
        float sw = R * 0.32f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(Theme.alpha(fg(), 0.15f));
        cv.drawCircle(cx, cy, R, p);
        p.setColor(acc());
        rf.set(cx - R, cy - R, cx + R, cy + R);
        cv.drawArc(rf, -90, 360f * pct() / 100f, false, p);
        p.setStyle(Paint.Style.FILL);
        if (State.charging && Draw.anim && pct() < 100) {
            chargeAnim = true;
            // una scia di punti che gira sull'anello
            float ph = phase(1600);
            for (int k = 0; k < 4; k++) {
                double a = Math.PI * 2 * (ph - k * 0.035f) - Math.PI / 2;
                p.setColor(Theme.alpha(item.tone == 2 ? th.accent : th.light ? th.onTile : 0xFFFFFFFF, 0.9f - k * 0.22f));
                cv.drawCircle(cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, sw * (0.3f - k * 0.05f), p);
            }
        }
        if (State.charging) {
            Draw.icon(cv, Draw.BOLT, cx, cy - R * 0.25f, R * 0.45f, p, fg(), acc());
            Draw.big(cv, th, pct() + "%", cx, cy + R * 0.32f, R * 0.28f, R * 1.3f, fg(), "", acc(), 0, p, false);
        } else {
            Draw.big(cv, th, pct() + "%", cx, cy, R * 0.42f, R * 1.4f, fg(), "%", sub(), 0, p, false);
        }
    }

    private void batteryPill(Canvas cv, float W, float H) {
        float in = H * 0.12f;
        float r = (H - in * 2) / 2f;
        float trackW = W - in * 2;
        int pc = pct();
        p.setStyle(Paint.Style.FILL);
        // binario
        p.setColor(Theme.alpha(fg(), 0.12f));
        rf.set(in, in, W - in, H - in);
        cv.drawRoundRect(rf, r, r, p);
        // riempimento
        float fillW = Math.max(r * 2, trackW * pc / 100f);
        p.setColor(acc());
        rf.set(in, in, in + fillW, H - in);
        cv.drawRoundRect(rf, r, r, p);
        String t = pc + "%";
        int fillText = item.tone == 2 ? th.accent : th.onAccent;
        float free = trackW - fillW - r * 1.2f;
        boolean inside = pc >= 55 || free < H * 0.55f;
        if (State.charging && Draw.anim && pc < 100) {
            chargeAnim = true;
            // puntini che scorrono nella parte vuota, verso destra
            float startX = in + fillW + r * 0.6f;
            float textW = Math.min(free, Draw.cols(DotTextView.normalize(t)) * H * 0.3f / 7f * 1.15f);
            float endX = inside ? W - in - r * 0.6f : W - in - r * 0.7f - textW - r * 0.5f;
            float step = r * 0.7f;
            int n = (int) ((endX - startX) / step);
            float ph = phase(1400);
            for (int k = 0; k <= n; k++) {
                float pos = k / (float) Math.max(1, n);
                float d = ph - pos;
                if (d < 0) d += 1f;
                float a = Math.max(0f, 1f - d * 3.2f);
                p.setColor(Theme.alpha(acc(), 0.12f + 0.7f * a));
                cv.drawCircle(startX + k * step, H / 2f, r * 0.14f, p);
            }
        }
        if (inside) {
            // testo dentro il riempimento, in bianco
            float maxW = fillW - r * (State.charging ? 2.6f : 1.4f);
            Draw.big(cv, th, t, in + fillW - r * 0.7f, H / 2f, H * 0.3f, maxW, fillText, "", acc(), 1, p, false);
        } else {
            // testo nello spazio libero a destra
            float maxW = free;
            Draw.big(cv, th, t, W - in - r * 0.7f, H / 2f, H * 0.3f, maxW, fg(), "", acc(), 1, p, false);
        }
        if (State.charging) Draw.icon(cv, Draw.BOLT, in + r, H / 2f, H * 0.4f, p, fillText, fillText);
    }

    private void batteryDots(Canvas cv, float W, float H) {
        float pad = Math.min(W, H) * 0.14f;
        Draw.big(cv, th, pct() + "%", W - pad, pad + H * 0.05f, H * 0.08f, W * 0.5f, acc(), "", acc(), 1, p, true);
        int n = 5;
        float gTop = pad + H * 0.18f;
        float size = Math.min(W - pad * 2, H - gTop - pad);
        float pitch = size / n;
        float x0 = (W - size) / 2f;
        int filled = Math.round(pct() / 4f);
        int idx = 0;
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                idx++;
                boolean on = idx <= filled;
                float rad = pitch * 0.42f;
                int color = on ? (idx == filled ? acc() : fg()) : Theme.alpha(fg(), 0.15f);
                if (State.charging && Draw.anim && pct() < 100 && idx > filled && idx <= filled + 3) {
                    chargeAnim = true;
                    // i prossimi punti si accendono a turno
                    float ph = phase(1500) * 3f - (idx - filled - 1);
                    float a = ph > 0 && ph < 1 ? (float) Math.sin(Math.PI * ph) : 0f;
                    color = Theme.alpha(acc(), 0.15f + 0.75f * a);
                    rad *= 0.8f + 0.2f * a;
                }
                p.setColor(color);
                cv.drawCircle(x0 + col * pitch + pitch / 2f, gTop + row * pitch + pitch / 2f, rad, p);
            }
        }
    }

    // ---------- bussola ----------

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", T.t("SO"), "O", T.t("NO")};

    private void compass(Canvas cv, float W, float H) {
        float cx = W / 2f, cy = H / 2f, R = Math.min(W, H) / 2f;
        if (!State.hasCompass) {
            Draw.label(cv, th, T.t("Bussola non disponibile"), cx, cy, R * 0.13f, sub(), 0, p, W * 0.8f);
            return;
        }
        float hd = State.heading;
        boolean small = item.w == 1;
        float rr = R * 0.8f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 1; i < 36; i++) {
            double a = Math.toRadians(i * 10 - hd);
            boolean major = i % 9 == 0;
            p.setColor(Theme.alpha(fg(), major ? 0.85f : 0.28f));
            cv.drawCircle(cx + (float) Math.sin(a) * rr, cy - (float) Math.cos(a) * rr,
                    R * (major ? 0.04f : 0.022f), p);
        }
        double an = Math.toRadians(-hd);
        p.setColor(acc());
        cv.drawCircle(cx + (float) Math.sin(an) * rr, cy - (float) Math.cos(an) * rr, R * 0.07f, p);
        // indicatore fisso in alto: la direzione verso cui punta il telefono
        p.setColor(fg());
        Path tri = new Path();
        tri.moveTo(cx, cy - R * 0.95f);
        tri.lineTo(cx - R * 0.05f, cy - R * 0.87f);
        tri.lineTo(cx + R * 0.05f, cy - R * 0.87f);
        tri.close();
        cv.drawPath(tri, p);
        int deg = Math.round(hd) % 360;
        String dir = DIRS[Math.round(deg / 45f) % 8];
        if (small) {
            Draw.big(cv, th, String.valueOf(deg), cx, cy, R * 0.3f, R * 1.0f, fg(), "", acc(), 0, p, true);
            return;
        }
        String[] card = {"N", "E", "S", "O"};
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(k * 90 - hd);
            float lr = R * 0.6f;
            Draw.big(cv, th, card[k], cx + (float) Math.sin(a) * lr, cy - (float) Math.cos(a) * lr,
                    R * 0.1f, R * 0.2f, k == 0 ? acc() : sub(), "", acc(), 0, p, true);
        }
        Draw.big(cv, th, deg + "°", cx, cy - R * 0.06f, R * 0.2f, R * 0.7f, fg(), "°", acc(), 0, p, true);
        Draw.label(cv, th, dir, cx, cy + R * 0.25f, R * 0.11f, sub(), 0, p, R);
    }

    // ---------- conto alla rovescia ----------

    private void countdown(Canvas cv, float W, float H) {
        float m = Math.min(W, H), pad = m * 0.14f;
        long target = WData.getLong(item, "d", 0);
        String title = WData.get(item, "t", T.t("Evento"));
        if (target == 0) {
            Draw.label(cv, th, T.t("Tocca per impostare"), W / 2f, H / 2f, m * 0.09f, sub(), 0, p, W * 0.8f);
            return;
        }
        int d = WData.daysTo(target);
        String num = d == 0 ? T.t("OGGI") : String.valueOf(Math.abs(d));
        String unit = d == 0 ? title : T.n(Math.abs(d), "giorno", "giorni") + (d < 0 ? T.t(" fa") : "");
        int numColor = d == 0 ? acc() : fg();
        if (isCircle()) {
            Draw.big(cv, th, num, W / 2f, H * 0.44f, H * 0.24f, W * 0.62f, numColor, "", acc(), 0, p, true);
            Draw.label(cv, th, d == 0 ? "" : unit, W / 2f, H * 0.74f, H * 0.09f, sub(), 0, p, W * 0.6f);
            return;
        }
        if (item.h == 1) {
            float w = Draw.big(cv, th, num, pad, H / 2f, H * 0.38f, W * 0.5f, numColor, "", acc(), -1, p, true);
            float x = pad + w + H * 0.18f, maxW = W - x - pad;
            Draw.label(cv, th, title, x, H * 0.46f, H * 0.16f, fg(), -1, p, maxW);
            if (d != 0) Draw.label(cv, th, unit, x, H * 0.7f, H * 0.13f, acc(), -1, p, maxW);
            return;
        }
        Draw.label(cv, th, title, pad, pad + m * 0.08f, m * 0.085f, fg(), -1, p, W - pad * 2);
        Draw.big(cv, th, num, W / 2f, H * 0.5f, H * 0.3f, W - pad * 2, numColor, "", acc(), 0, p, true);
        if (d != 0) Draw.label(cv, th, unit, W / 2f, H * 0.76f, m * 0.08f, acc(), 0, p, W - pad * 2);
        String date = new SimpleDateFormat("d MMM yyyy", T.loc()).format(new Date(target));
        Draw.label(cv, th, date, W / 2f, H - pad * 0.8f, m * 0.065f, sub(), 0, p, W - pad * 2);
    }

    // ---------- fusi orari ----------

    private String zoneTime(String zone) {
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", T.loc());
        f.setTimeZone(TimeZone.getTimeZone(zone));
        return f.format(new Date());
    }

    private boolean night(String zone) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone));
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h < 6 || h >= 20;
    }

    private void worldClock(Canvas cv, float W, float H) {
        List<String[]> z = WData.zones(item);
        float m = Math.min(W, H), pad = m * 0.13f;
        if (z.isEmpty()) {
            Draw.label(cv, th, T.t("Tocca per scegliere"), W / 2f, H / 2f, m * 0.09f, sub(), 0, p, W * 0.8f);
            return;
        }
        if (isCircle()) {
            String[] c0 = z.get(0);
            Draw.big(cv, th, zoneTime(c0[1]), W / 2f, H * 0.47f, H * 0.2f, W * 0.66f, fg(), ":", acc(), 0, p, true);
            Draw.label(cv, th, c0[0], W / 2f, H * 0.74f, H * 0.085f, sub(), 0, p, W * 0.6f);
            return;
        }
        if (item.h >= 2 && item.w <= 2) {
            // elenco verticale: città a sinistra, ora a destra
            int n = Math.min(z.size(), item.h >= 3 ? 5 : 3);
            float rowH = (H - pad * 2) / n;
            for (int i = 0; i < n; i++) {
                String[] c = z.get(i);
                float y = pad + rowH * i + rowH / 2f;
                Draw.label(cv, th, c[0], pad, y - rowH * 0.02f, rowH * 0.22f, fg(), -1, p, W * 0.42f);
                Draw.label(cv, th, WData.offsetLabel(c[1]), pad, y + rowH * 0.24f, rowH * 0.17f,
                        night(c[1]) ? acc() : sub(), -1, p, W * 0.42f);
                Draw.big(cv, th, zoneTime(c[1]), W - pad, y, rowH * 0.36f, W * 0.48f, fg(), ":", acc(), 1, p, true);
            }
            return;
        }
        int n = Math.min(z.size(), item.w <= 2 ? 2 : 4);
        float cw = (W - pad * 2) / n;
        boolean tall = item.h >= 2;
        for (int i = 0; i < n; i++) {
            String[] c = z.get(i);
            float x = pad + cw * i + cw / 2f;
            Draw.label(cv, th, c[0], x, tall ? H * 0.3f : H * 0.33f, tall ? H * 0.075f : H * 0.13f, sub(), 0, p, cw * 0.92f);
            Draw.big(cv, th, zoneTime(c[1]), x, tall ? H * 0.52f : H * 0.62f, tall ? H * 0.17f : H * 0.26f,
                    cw * 0.86f, fg(), ":", acc(), 0, p, true);
            if (tall) Draw.label(cv, th, WData.offsetLabel(c[1]), x, H * 0.78f, H * 0.065f,
                    night(c[1]) ? acc() : sub(), 0, p, cw * 0.9f);
        }
    }

    // ---------- contapassi ----------

    private void steps(Canvas cv, float W, float H) {
        float cx = W / 2f, cy = H / 2f, m = Math.min(W, H), pad = m * 0.14f;
        if (!State.hasSteps) {
            Draw.icon(cv, Draw.STEPS, cx, H * 0.4f, m * 0.26f, p, fg(), acc());
            Draw.label(cv, th, T.t("Contapassi assente"), cx, H * 0.78f, m * 0.08f, sub(), 0, p, W * 0.8f);
            return;
        }
        if (!State.stepPerm) {
            Draw.icon(cv, Draw.STEPS, cx, H * 0.4f, m * 0.26f, p, fg(), acc());
            Draw.label(cv, th, T.t("Tocca per attivare"), cx, H * 0.78f, m * 0.08f, sub(), 0, p, W * 0.8f);
            return;
        }
        int goal = Math.max(100, State.stepGoal);
        int s = State.steps;
        float frac = Math.min(1f, s / (float) goal);
        if (item.h >= 2 || isCircle()) {
            boolean circ = isCircle();
            int n = circ ? 28 : 40;
            float R = m * (circ ? 0.4f : 0.38f);
            int lit = Math.round(frac * n);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                p.setColor(i < lit ? (i == lit - 1 || frac >= 1f ? acc() : fg()) : Theme.alpha(fg(), 0.15f));
                cv.drawCircle(cx + (float) Math.sin(a) * R, cy - (float) Math.cos(a) * R, m * (circ ? 0.028f : 0.022f), p);
            }
            Draw.big(cv, th, String.valueOf(s), cx, circ ? cy : cy - m * 0.04f, m * (circ ? 0.15f : 0.13f),
                    R * 1.3f, fg(), "", acc(), 0, p, true);
            if (!circ) {
                Draw.label(cv, th, T.t("passi"), cx, cy + m * 0.13f, m * 0.065f, sub(), 0, p, R);
                Draw.label(cv, th, T.t("obiettivo ") + goal, cx, cy + m * 0.22f, m * 0.05f, acc(), 0, p, R * 1.2f);
            }
            return;
        }
        Draw.icon(cv, Draw.STEPS, pad + H * 0.18f, H * 0.4f, H * 0.32f, p, fg(), acc());
        float x = pad + H * 0.45f;
        Draw.big(cv, th, String.valueOf(s), x, H * 0.4f, H * 0.3f, W - x - pad, fg(), "", acc(), -1, p, true);
        int n = 18;
        float barW = W - pad * 2, step = barW / n;
        int lit = Math.round(frac * n);
        for (int i = 0; i < n; i++) {
            p.setColor(i < lit ? acc() : Theme.alpha(fg(), 0.15f));
            cv.drawCircle(pad + step * i + step / 2f, H * 0.78f, Math.min(step * 0.32f, H * 0.045f), p);
        }
    }

    // ---------- foto ----------

    private Bitmap halfSrc;
    private String halfKey;

    private void photo(Canvas cv, float W, float H) {
        float m = Math.min(W, H);
        String u = WData.get(item, "u", "");
        if (u.isEmpty() || Photos.failed(u)) {
            Draw.icon(cv, Draw.PHOTO, W / 2f, H * 0.42f, m * 0.26f, p, fg(), acc());
            Draw.label(cv, th, u.isEmpty() ? T.t("Tocca per scegliere") : T.t("Foto non disponibile"), W / 2f, H * 0.76f,
                    m * 0.075f, sub(), 0, p, W * 0.8f);
            return;
        }
        Bitmap b = Photos.get(getContext(), u, (int) Math.max(W, H), this);
        if (b == null) return;
        clip.reset();
        if (isCircle()) clip.addCircle(W / 2f, H / 2f, m / 2f, Path.Direction.CW);
        else {
            float r = (item.w == 1 || item.h == 1) ? m / 2f : Theme.radius(m, getResources().getDisplayMetrics().density);
            clip.addRoundRect(0, 0, W, H, r, r, Path.Direction.CW);
        }
        // ritaglio centrale (center crop)
        float bw = b.getWidth(), bh = b.getHeight();
        float sc = Math.max(W / bw, H / bh);
        float cw = W / sc, ch = H / sc;
        src.set(Math.round((bw - cw) / 2f), Math.round((bh - ch) / 2f),
                Math.round((bw + cw) / 2f), Math.round((bh + ch) / 2f));
        if ("dots".equals(WData.get(item, "s", ""))) {
            halftone(cv, b, W, H);
            return;
        }
        cv.save();
        cv.clipPath(clip);
        rf.set(0, 0, W, H);
        cv.drawBitmap(b, src, rf, bp);
        cv.restore();
    }

    /** Foto a puntini: ogni punto è grande quanto è chiara (o scura, nel tema chiaro) quella zona. */
    private void halftone(Canvas cv, Bitmap b, float W, float H) {
        float m = Math.min(W, H);
        float pitch = Math.max(6f, m / (isCircle() ? 16f : 22f));
        int cols = Math.max(1, (int) (W / pitch)), rows = Math.max(1, (int) (H / pitch));
        String key = System.identityHashCode(b) + "@" + cols + "x" + rows + src.toShortString();
        if (!key.equals(halfKey)) {
            Bitmap crop = Bitmap.createBitmap(b, src.left, src.top,
                    Math.max(1, Math.min(src.width(), b.getWidth() - src.left)),
                    Math.max(1, Math.min(src.height(), b.getHeight() - src.top)));
            halfSrc = Bitmap.createScaledBitmap(crop, cols, rows, true);
            halfKey = key;
        }
        float ox = (W - cols * pitch) / 2f + pitch / 2f, oy = (H - rows * pitch) / 2f + pitch / 2f;
        cv.save();
        cv.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fg());
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int c = halfSrc.getPixel(x, y);
                float l = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
                float v = th.light && item.tone == 0 ? 1f - l : l;
                float r = pitch * 0.5f * (float) Math.pow(v, 0.8);
                if (r < pitch * 0.06f) continue;
                cv.drawCircle(ox + x * pitch, oy + y * pitch, r, p);
            }
        }
        cv.restore();
    }

    // =====================================================================
    // widget della serie 3
    // =====================================================================

    // ---------- anno in punti ----------

    private void year(Canvas cv, float W, float H) {
        Calendar c = Calendar.getInstance();
        int days = c.getActualMaximum(Calendar.DAY_OF_YEAR), today = c.get(Calendar.DAY_OF_YEAR);
        float m = Math.min(W, H), pad = m * 0.12f;
        float head = H * (item.h >= 3 ? 0.12f : 0.18f);
        int pct = Math.round(100f * (today - 1) / days);
        Draw.big(cv, th, String.valueOf(c.get(Calendar.YEAR)), pad, pad + head * 0.4f, head * 0.55f, W * 0.5f, fg(), "",
                acc(), -1, p, true);
        Draw.big(cv, th, pct + "%", W - pad, pad + head * 0.4f, head * 0.55f, W * 0.4f, acc(), "", acc(), 1, p, true);
        float gx = pad, gy = pad + head, gw = W - pad * 2, gh = H - gy - pad;
        // colonne e righe che riempiono al meglio l'area
        int cols = Math.max(7, (int) Math.round(Math.sqrt(days * gw / gh)));
        int rows = (int) Math.ceil(days / (float) cols);
        float pitch = Math.min(gw / cols, gh / rows);
        float ox = gx + (gw - pitch * cols) / 2f, oy = gy + (gh - pitch * rows) / 2f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < days; i++) {
            int col = i % cols, row = i / cols;
            float x = ox + col * pitch + pitch / 2f, y = oy + row * pitch + pitch / 2f;
            int d = i + 1;
            p.setColor(d < today ? fg() : d == today ? acc() : Theme.alpha(fg(), 0.15f));
            cv.drawCircle(x, y, pitch * (d == today ? 0.45f : 0.32f), p);
        }
    }

    // ---------- giornata ----------

    private void dayProgress(Canvas cv, float W, float H) {
        Calendar c = Calendar.getInstance();
        int hr = c.get(Calendar.HOUR_OF_DAY), min = c.get(Calendar.MINUTE);
        float frac = (hr * 60 + min) / 1440f;
        float pad = H * 0.16f;
        Draw.label(cv, th, T.t("Giornata"), pad, H * 0.36f, H * 0.14f, sub(), -1, p, W * 0.5f);
        Draw.big(cv, th, Math.round(frac * 100) + "%", W - pad, H * 0.3f, H * 0.2f, W * 0.4f, acc(), "", acc(), 1, p, true);
        int n = 24;
        float bw = W - pad * 2, step = bw / n, cy = H * 0.68f;
        for (int i = 0; i < n; i++) {
            boolean past = i < hr, now = i == hr;
            float r = Math.min(step * 0.36f, H * 0.11f);
            if (now) {
                float k = min / 60f;
                p.setColor(Theme.alpha(fg(), 0.15f));
                cv.drawCircle(pad + step * i + step / 2f, cy, r, p);
                p.setColor(acc());
                cv.drawCircle(pad + step * i + step / 2f, cy, r * (0.35f + 0.65f * k), p);
                continue;
            }
            p.setColor(past ? fg() : Theme.alpha(fg(), 0.15f));
            cv.drawCircle(pad + step * i + step / 2f, cy, r * (i % 6 == 0 ? 1f : 0.8f), p);
        }
    }

    // ---------- orologio ad anello ----------

    private void clockRing(Canvas cv, float W, float H) {
        Calendar c = Calendar.getInstance();
        int min = c.get(Calendar.MINUTE);
        String hh = time("HH", "h");
        float cx = W / 2f, cy = H / 2f, R = Math.min(W, H) * 0.4f;
        for (int i = 0; i < 60; i++) {
            double a = Math.PI * 2 * i / 60;
            boolean on = i <= min;
            float r = R * (i % 5 == 0 ? 0.055f : 0.035f);
            p.setColor(i == min ? acc() : on ? fg() : Theme.alpha(fg(), 0.15f));
            cv.drawCircle(cx + (float) Math.sin(a) * R, cy - (float) Math.cos(a) * R, r, p);
        }
        Draw.big(cv, th, hh, cx, cy, R * 0.62f, R * 1.2f, fg(), "", acc(), 0, p, true, prog(1, hh));
    }

    // ---------- luna ----------

    static final double SYNODIC = 29.530588853;

    /** Età della luna in giorni (0 = luna nuova). */
    static double moonAge() {
        long ref = 947182440000L; // 6 gennaio 2000, 18:14 UTC: luna nuova
        double days = (System.currentTimeMillis() - ref) / 86400000.0;
        double age = days % SYNODIC;
        return age < 0 ? age + SYNODIC : age;
    }

    static String moonName(double age) {
        String[] n = {T.t("Luna nuova"), T.t("Crescente"), T.t("Primo quarto"), T.t("Gibbosa crescente"), T.t("Luna piena"),
                T.t("Gibbosa calante"), T.t("Ultimo quarto"), T.t("Calante")};
        return n[(int) Math.floor((age / SYNODIC) * 8 + 0.5) % 8];
    }

    private void moon(Canvas cv, float W, float H) {
        double age = moonAge();
        double ph = 2 * Math.PI * age / SYNODIC;
        boolean waxing = age < SYNODIC / 2;
        int illum = (int) Math.round((1 - Math.cos(ph)) / 2 * 100);
        boolean circ = isCircle();
        float cx = W / 2f, cy = circ ? H / 2f : H * 0.42f, R = Math.min(W, H) * (circ ? 0.34f : 0.27f);
        int n = 15;
        float pitch = R * 2 / n;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float nx = (x + 0.5f) / n * 2 - 1, ny = (y + 0.5f) / n * 2 - 1;
                if (nx * nx + ny * ny > 1) continue;
                double edge = Math.sqrt(1 - ny * ny) * Math.cos(ph);
                boolean lit = waxing ? nx > edge : nx < -edge;
                p.setColor(lit ? fg() : Theme.alpha(fg(), 0.13f));
                cv.drawCircle(cx - R + (x + 0.5f) * pitch, cy - R + (y + 0.5f) * pitch, pitch * 0.4f, p);
            }
        }
        if (!circ) {
            Draw.label(cv, th, moonName(age), W / 2f, H * 0.8f, H * 0.075f, fg(), 0, p, W * 0.85f);
            Draw.label(cv, th, illum + "% illuminata", W / 2f, H * 0.9f, H * 0.06f, sub(), 0, p, W * 0.85f);
        }
    }

    // ---------- alba e tramonto ----------

    private void sun(Canvas cv, float W, float H) {
        if (!State.wOk || State.sunrise.isEmpty()) {
            Draw.icon(cv, Draw.SUN, W / 2f, H * 0.42f, Math.min(W, H) * 0.3f, p, fg(), acc());
            Draw.label(cv, th, T.t("Tocca per il meteo"), W / 2f, H * 0.8f, Math.min(W, H) * 0.08f, sub(), 0, p, W * 0.8f);
            return;
        }
        float pad = Math.min(W, H) * 0.14f;
        float baseY = item.h >= 2 ? H * 0.62f : H * 0.66f;
        float R = Math.min((W - pad * 2) / 2f, baseY - pad * 1.2f);
        float cx = W / 2f;
        int n = 25;
        float frac = State.sunFraction();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * i / (n - 1);
            float x = cx - (float) Math.cos(a) * R, y = baseY - (float) Math.sin(a) * R;
            p.setColor(i / (float) (n - 1) <= frac ? Theme.alpha(fg(), 0.8f) : Theme.alpha(fg(), 0.15f));
            cv.drawCircle(x, y, R * 0.035f, p);
        }
        p.setColor(Theme.alpha(fg(), 0.3f));
        p.setStrokeWidth(Math.max(1f, R * 0.012f));
        cv.drawLine(pad * 0.6f, baseY, W - pad * 0.6f, baseY, p);
        if (frac >= 0 && frac <= 1) {
            double a = Math.PI * frac;
            p.setColor(acc());
            cv.drawCircle(cx - (float) Math.cos(a) * R, baseY - (float) Math.sin(a) * R, R * 0.1f, p);
        }
        float ls = item.h >= 2 ? H * 0.075f : H * 0.13f;
        Draw.big(cv, th, State.sunrise, pad, baseY + ls * 1.4f, ls, W * 0.4f, fg(), ":", acc(), -1, p, true);
        Draw.big(cv, th, State.sunset, W - pad, baseY + ls * 1.4f, ls, W * 0.4f, fg(), ":", acc(), 1, p, true);
        if (item.h >= 2 && State.uvMax >= 0) {
            Draw.label(cv, th, "UV max " + State.uvMax, W / 2f, H - pad * 0.7f, H * 0.06f, sub(), 0, p, W * 0.8f);
        }
    }

    // ---------- meteo delle prossime ore ----------

    private void weatherHours(Canvas cv, float W, float H) {
        if (!State.wOk || State.hTemp.length == 0) {
            Draw.label(cv, th, T.t("Tocca per il meteo"), W / 2f, H / 2f, Math.min(W, H) * 0.12f, sub(), 0, p, W * 0.8f);
            return;
        }
        int start = State.hourIndex();
        int n = Math.min(item.w >= 4 ? 6 : 4, State.hTemp.length - start);
        if (n <= 0) return;
        float pad = Math.min(W, H) * 0.1f;
        float cw = (W - pad * 2) / n;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            lo = Math.min(lo, State.hTemp[start + i]);
            hi = Math.max(hi, State.hTemp[start + i]);
        }
        boolean tall = item.h >= 2;
        for (int i = 0; i < n; i++) {
            int k = start + i;
            float x = pad + cw * i + cw / 2f;
            String lab = i == 0 ? "ora" : State.hLabel[k];
            Draw.label(cv, th, lab, x, tall ? H * 0.2f : H * 0.28f, tall ? H * 0.065f : H * 0.13f,
                    i == 0 ? acc() : sub(), 0, p, cw * 0.9f);
            if (tall) {
                Draw.icon(cv, Draw.weatherIcon(State.hCode[k]), x, H * 0.38f, Math.min(cw * 0.6f, H * 0.18f), p, fg(), acc());
                // colonna di puntini proporzionale alla temperatura
                float t = hi == lo ? 0.5f : (State.hTemp[k] - lo) / (float) (hi - lo);
                int dots = 2 + Math.round(t * 4);
                for (int d = 0; d < 6; d++) {
                    p.setColor(d < dots ? (i == 0 ? acc() : fg()) : Theme.alpha(fg(), 0.12f));
                    cv.drawCircle(x, H * 0.82f - d * H * 0.045f, H * 0.014f, p);
                }
                Draw.big(cv, th, State.hTemp[k] + "°", x, H * 0.55f, H * 0.075f, cw * 0.9f, fg(), "°", acc(), 0, p, true);
            } else {
                Draw.big(cv, th, State.hTemp[k] + "°", x, H * 0.64f, H * 0.2f, cw * 0.9f, fg(), "°", acc(), 0, p, true);
            }
        }
    }

    // ---------- contatore ----------

    private void counter(Canvas cv, float W, float H) {
        long n = WData.getLong(item, "n", 0);
        String title = WData.get(item, "t", "");
        String s = String.valueOf(n);
        if (isCircle()) {
            Draw.big(cv, th, s, W / 2f, title.isEmpty() ? H / 2f : H * 0.45f, H * 0.26f, W * 0.62f, fg(), "", acc(), 0, p, true,
                    prog(0, s));
            if (!title.isEmpty()) Draw.label(cv, th, title, W / 2f, H * 0.76f, H * 0.08f, sub(), 0, p, W * 0.6f);
            return;
        }
        float pad = H * 0.2f;
        Draw.big(cv, th, s, pad, H / 2f, H * 0.42f, W * 0.55f, fg(), "", acc(), -1, p, true, prog(0, s));
        Draw.label(cv, th, title.isEmpty() ? T.t("Contatore") : title, W - pad, H * 0.44f, H * 0.13f, sub(), 1, p, W * 0.4f);
        p.setColor(acc());
        cv.drawCircle(W - pad - H * 0.08f, H * 0.66f, H * 0.08f, p);
        p.setColor(item.tone == 2 ? th.accent : th.onAccent);
        p.setStrokeWidth(H * 0.025f);
        float px = W - pad - H * 0.08f, py = H * 0.66f, l = H * 0.04f;
        cv.drawLine(px - l, py, px + l, py, p);
        cv.drawLine(px, py - l, px, py + l, p);
    }

    // ---------- proverbio del giorno ----------

    static final String[] PROVERBS = {
            T.t("Chi dorme non piglia pesci."), T.t("Chi va piano va sano e va lontano."), T.t("Meglio tardi che mai."),
            T.t("L'abito non fa il monaco."), T.t("Chi trova un amico trova un tesoro."),
            T.t("Tra il dire e il fare c'è di mezzo il mare."), T.t("Non tutte le ciambelle riescono col buco."),
            T.t("Chi semina vento raccoglie tempesta."), T.t("Rosso di sera, bel tempo si spera."),
            T.t("Il buongiorno si vede dal mattino."), T.t("Chi la dura la vince."), T.t("L'unione fa la forza."),
            T.t("Sbagliando s'impara."), T.t("Paese che vai, usanza che trovi."), T.t("Chi fa da sé fa per tre."),
            T.t("A caval donato non si guarda in bocca."), T.t("Ogni lasciata è persa."), T.t("Chi cerca trova."),
            T.t("Meglio un uovo oggi che una gallina domani."), T.t("Una mela al giorno toglie il medico di torno."),
            T.t("Chi ben comincia è a metà dell'opera."), T.t("Non c'è rosa senza spine."), T.t("Volere è potere."),
            T.t("Il riso fa buon sangue."), T.t("Chi tace acconsente."), T.t("Fidarsi è bene, non fidarsi è meglio."),
            T.t("Occhio non vede, cuore non duole."), T.t("Can che abbaia non morde."), T.t("L'appetito vien mangiando."),
            T.t("Aiutati che il ciel t'aiuta."), T.t("Tutto è bene quel che finisce bene."),
            T.t("La pazienza è la virtù dei forti."), T.t("Dimmi con chi vai e ti dirò chi sei."),
            T.t("Batti il ferro finché è caldo."), T.t("Non rimandare a domani quello che puoi fare oggi."),
            T.t("Ride bene chi ride ultimo."), T.t("Gallina vecchia fa buon brodo."), T.t("Chi non risica non rosica."),
            T.t("Il tempo è galantuomo."), T.t("Acqua passata non macina più.")
    };

    static String proverb(Item it) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int off = (int) WData.getLong(it, "o", 0);
        int i = (day + off) % PROVERBS.length;
        return PROVERBS[i < 0 ? i + PROVERBS.length : i];
    }

    private void proverb(Canvas cv, float W, float H) {
        float pad = Math.min(W, H) * 0.14f;
        Draw.label(cv, th, T.t("Proverbio del giorno"), pad, pad + H * 0.06f, Math.min(W, H) * 0.07f, acc(), -1, p, W - pad * 2);
        android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(fg());
        tp.setTypeface(th.nuovo ? th.titleFace : Fonts.medium);
        float size = Math.min(W, H) * (item.w >= 4 ? 0.13f : 0.11f);
        String text = proverb(item);
        android.text.StaticLayout sl;
        float maxH = H - pad * 2 - H * 0.12f;
        do {
            tp.setTextSize(size);
            sl = android.text.StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) (W - pad * 2))
                    .setLineSpacing(0, 1.05f).build();
            size *= 0.92f;
        } while (sl.getHeight() > maxH && size > 8);
        cv.save();
        cv.translate(pad, H - pad - sl.getHeight());
        sl.draw(cv);
        cv.restore();
    }

    // ---------- dado ----------

    private long rollTime;

    void roll() {
        rollTime = SystemClock.uptimeMillis();
        WData.put(item, "v", String.valueOf(1 + new java.util.Random().nextInt(6)));
        invalidate();
    }

    private void dice(Canvas cv, float W, float H) {
        int v = (int) WData.getLong(item, "v", 5);
        long el = SystemClock.uptimeMillis() - rollTime;
        if (rollTime > 0 && el < 500) {
            v = 1 + (int) ((el / 70) % 6);
            animating = true;
        }
        float m = Math.min(W, H), s = m * 0.5f;
        float cx = W / 2f, cy = H / 2f;
        float[][] pos = {{0, 0}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, 0}, {1, 0}};
        int[][] faces = {{0}, {1, 2}, {1, 0, 2}, {1, 2, 3, 4}, {1, 2, 3, 4, 0}, {1, 2, 3, 4, 5, 6}};
        float d = s * 0.33f;
        for (int i : faces[Math.max(0, Math.min(5, v - 1))]) {
            p.setColor(v == 1 ? acc() : fg());
            cv.drawCircle(cx + pos[i][0] * d, cy + pos[i][1] * d, s * 0.12f, p);
        }
    }

    // ---------- settimana ----------

    private void week(Canvas cv, float W, float H) {
        Calendar c = Calendar.getInstance();
        int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // lunedì = 0
        Calendar d = (Calendar) c.clone();
        d.add(Calendar.DAY_OF_MONTH, -dow);
        String[] names = T.en ? new String[]{"M", "T", "W", "T", "F", "S", "S"} : new String[]{"L", "M", "M", "G", "V", "S", "D"};
        float pad = H * 0.14f, cw = (W - pad * 2) / 7f;
        for (int i = 0; i < 7; i++) {
            float x = pad + cw * i + cw / 2f;
            boolean today = i == dow;
            Draw.label(cv, th, names[i], x, H * 0.36f, H * 0.14f, i >= 5 ? acc() : sub(), 0, p, cw);
            if (today) {
                p.setColor(acc());
                cv.drawCircle(x, H * 0.64f, Math.min(cw, H) * 0.26f, p);
            }
            String num = String.valueOf(d.get(Calendar.DAY_OF_MONTH));
            Draw.big(cv, th, num, x, H * 0.64f, H * 0.2f, cw * 0.7f,
                    today ? (item.tone == 2 ? th.accent : th.onAccent) : (i < dow ? sub() : fg()), "", acc(), 0, p, true);
            d.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    // ---------- memoria ----------

    private void storage(Canvas cv, float W, float H) {
        long total = 0, free = 0;
        try {
            android.os.StatFs st = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            total = st.getTotalBytes();
            free = st.getAvailableBytes();
        } catch (Exception ignored) {
        }
        float used = total > 0 ? (total - free) / (float) total : 0;
        float pad = Math.min(W, H) * 0.14f;
        String gb = String.format(T.loc(), "%.0f", free / 1e9);
        boolean tall = item.h >= 2;
        Draw.label(cv, th, T.t("Memoria libera"), pad, tall ? pad + H * 0.06f : H * 0.32f, tall ? H * 0.065f : H * 0.13f, sub(), -1,
                p, W - pad * 2);
        Draw.big(cv, th, gb + " GB", pad, tall ? H * 0.42f : H * 0.58f, tall ? H * 0.16f : H * 0.24f, W - pad * 2, fg(), "",
                acc(), -1, p, true);
        int n = tall ? 40 : 20, cols = tall ? 10 : 20;
        int lit = Math.round(used * n);
        float gw = W - pad * 2, pitch = gw / cols;
        float y0 = tall ? H * 0.62f : H * 0.82f;
        for (int i = 0; i < n; i++) {
            int col = i % cols, row = i / cols;
            p.setColor(i < lit ? (used > 0.9f ? acc() : fg()) : Theme.alpha(fg(), 0.15f));
            cv.drawCircle(pad + col * pitch + pitch / 2f, y0 + row * pitch, Math.min(pitch * 0.32f, H * 0.04f), p);
        }
    }

    // ---------- nota ----------

    private void note(Canvas cv, float W, float H) {
        String t = WData.get(item, "t", "");
        float pad = Math.min(W, H) * 0.14f;
        Draw.label(cv, th, T.t("Nota"), pad, pad + H * 0.04f, Math.min(W, H) * (item.h == 1 ? 0.12f : 0.07f), acc(), -1, p,
                W * 0.5f);
        android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(t.isEmpty() ? sub() : fg());
        tp.setTypeface(th.bodyFace);
        float size = Math.min(W, H) * (item.h == 1 ? 0.22f : 0.1f);
        String text = t.isEmpty() ? T.t("Tocca per scrivere") : t;
        float top = pad + H * (item.h == 1 ? 0.12f : 0.1f);
        float maxH = H - top - pad * 0.7f;
        android.text.StaticLayout sl;
        do {
            tp.setTextSize(size);
            sl = android.text.StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) (W - pad * 2))
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).setMaxLines(12).build();
            size *= 0.92f;
        } while (sl.getHeight() > maxH && size > 9);
        cv.save();
        cv.translate(pad, top);
        cv.clipRect(0, 0, W - pad * 2, maxH);
        sl.draw(cv);
        cv.restore();
    }

    // ---------- torcia ----------

    static final String[] BULB = {
            "...###...", "..#####..", ".#######.", ".#######.", ".#######.",
            "..#####..", "...###...", "...RRR...", "....R...."};

    private void torch(Canvas cv, float W, float H) {
        boolean on = State.torchOn;
        if (on) {
            p.setColor(Theme.alpha(acc(), 0.25f));
            cv.drawCircle(W / 2f, H / 2f, Math.min(W, H) * 0.38f, p);
        }
        Draw.icon(cv, BULB, W / 2f, H / 2f, Math.min(W, H) * 0.42f, p, on ? acc() : Theme.alpha(fg(), 0.55f),
                on ? fg() : sub());
    }

    // ---------- musica ----------

    private float touchX = -1, touchY = -1;

    @Override
    public boolean onTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            touchX = ev.getX();
            touchY = ev.getY();
        }
        return super.onTouchEvent(ev);
    }

    /** Pulsante della musica sotto l'ultimo tocco: 1 indietro, 2 play/pausa, 3 avanti, 0 nessuno. */
    int mediaHit() {
        RectF[] b = mediaButtons(getWidth(), getHeight());
        for (int i = 0; i < 3; i++) {
            RectF r = new RectF(b[i]);
            r.inset(-r.width() * 0.15f, -r.height() * 0.2f);
            if (r.contains(touchX, touchY)) return i + 1;
        }
        return 0;
    }

    private RectF[] mediaButtons(float W, float H) {
        RectF[] out = new RectF[3];
        boolean tall = item.h >= 2;
        float bs = tall ? Math.min(W, H) * 0.2f : H * 0.42f;
        float gap = bs * 0.35f;
        float total = bs * 3 + gap * 2;
        float x0 = tall ? (W - total) / 2f : W - H * 0.2f - total;
        float cy = tall ? H - Math.min(W, H) * 0.12f - bs / 2f : H / 2f;
        for (int i = 0; i < 3; i++) {
            float x = x0 + i * (bs + gap);
            out[i] = new RectF(x, cy - bs / 2f, x + bs, cy + bs / 2f);
        }
        return out;
    }

    static final String[] NOTE = {
            "....#####", "....#...#", "....#...#", "....#...#", "....#...#",
            ".###..###", "####.####", ".##...##.", "........."};

    private void media(Canvas cv, float W, float H) {
        float m = Math.min(W, H);
        if (!DotNotifications.active()) {
            Draw.icon(cv, NOTE, W / 2f, H * 0.4f, m * 0.3f, p, fg(), acc());
            Draw.label(cv, th, T.t("Tocca per attivare"), W / 2f, H * 0.82f, m * 0.08f, sub(), 0, p, W * 0.9f);
            return;
        }
        boolean tall = item.h >= 2;
        float pad = tall ? m * 0.12f : H * 0.16f;
        float artS = tall ? Math.min(H * 0.42f, W * 0.4f) : H - pad * 2;
        RectF art = new RectF(pad, pad, pad + artS, pad + artS);
        float rad = artS * 0.22f;
        if (DotNotifications.mArt != null) {
            cv.save();
            Path pa = new Path();
            pa.addRoundRect(art, rad, rad, Path.Direction.CW);
            cv.clipPath(pa);
            Bitmap a = DotNotifications.mArt;
            float sc = Math.max(artS / a.getWidth(), artS / a.getHeight());
            float cw = artS / sc, ch = artS / sc;
            src.set(Math.round((a.getWidth() - cw) / 2f), Math.round((a.getHeight() - ch) / 2f),
                    Math.round((a.getWidth() + cw) / 2f), Math.round((a.getHeight() + ch) / 2f));
            cv.drawBitmap(a, src, art, bp);
            cv.restore();
        } else {
            p.setColor(Theme.alpha(fg(), 0.1f));
            cv.drawRoundRect(art, rad, rad, p);
            Draw.icon(cv, NOTE, art.centerX(), art.centerY(), artS * 0.5f, p, fg(), acc());
        }
        String title = DotNotifications.mActive && !DotNotifications.mTitle.isEmpty() ? DotNotifications.mTitle
                : T.t("Niente in riproduzione");
        String artist = DotNotifications.mArtist;
        RectF[] b = mediaButtons(W, H);
        float tx, tw, ty;
        if (tall) {
            tx = tall && W >= H * 1.5f ? art.right + pad : pad;
            ty = tall && W >= H * 1.5f ? pad + artS * 0.35f : art.bottom + m * 0.12f;
            tw = W - tx - pad;
        } else {
            tx = art.right + pad * 0.8f;
            ty = H * 0.45f;
            tw = b[0].left - tx - pad * 0.5f;
        }
        android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(fg());
        tp.setTypeface(Fonts.medium);
        tp.setTextSize(tall ? m * 0.095f : H * 0.2f);
        if (tw > 10) {
            cv.drawText(android.text.TextUtils.ellipsize(title, tp, tw, android.text.TextUtils.TruncateAt.END).toString(),
                    tx, ty, tp);
            if (!artist.isEmpty()) {
                tp.setColor(sub());
                tp.setTypeface(th.bodyFace);
                tp.setTextSize(tall ? m * 0.075f : H * 0.16f);
                cv.drawText(android.text.TextUtils.ellipsize(artist, tp, tw, android.text.TextUtils.TruncateAt.END)
                        .toString(), tx, ty + tp.getTextSize() * 1.35f, tp);
            }
        }
        // pulsanti: indietro, play/pausa, avanti
        for (int i = 0; i < 3; i++) {
            RectF r = b[i];
            float cx = r.centerX(), cy = r.centerY(), s = r.width();
            if (i == 1) {
                p.setColor(acc());
                cv.drawCircle(cx, cy, s / 2f, p);
                p.setColor(item.tone == 2 ? th.accent : th.onAccent);
                if (DotNotifications.mPlaying) {
                    float bw = s * 0.1f, bh = s * 0.34f;
                    cv.drawRoundRect(cx - bw * 1.8f, cy - bh, cx - bw * 0.6f, cy + bh, bw / 2, bw / 2, p);
                    cv.drawRoundRect(cx + bw * 0.6f, cy - bh, cx + bw * 1.8f, cy + bh, bw / 2, bw / 2, p);
                } else {
                    Path t = new Path();
                    t.moveTo(cx - s * 0.13f, cy - s * 0.2f);
                    t.lineTo(cx + s * 0.22f, cy);
                    t.lineTo(cx - s * 0.13f, cy + s * 0.2f);
                    t.close();
                    cv.drawPath(t, p);
                }
            } else {
                p.setColor(fg());
                float dir = i == 0 ? -1 : 1;
                Path t = new Path();
                t.moveTo(cx - dir * s * 0.16f, cy - s * 0.18f);
                t.lineTo(cx + dir * s * 0.12f, cy);
                t.lineTo(cx - dir * s * 0.16f, cy + s * 0.18f);
                t.close();
                cv.drawPath(t, p);
                float bx = cx + dir * s * 0.16f;
                cv.drawRect(bx - s * 0.035f, cy - s * 0.18f, bx + s * 0.035f, cy + s * 0.18f, p);
            }
        }
    }

    // ---------- benessere digitale ----------

    static final String[] HOURGLASS = {
            "#########", ".#.....#.", "..#...#..", "...#R#...", "....R....",
            "...#.#...", "..#.R.#..", ".#RRRRR#.", "#########"};
    static final String[] TARGET = {
            "..#####..", ".#.....#.", "#..###..#", "#.#...#.#", "#.#.R.#.#",
            "#.#...#.#", "#..###..#", ".#.....#.", "..#####.."};

    private void usage(Canvas cv, float W, float H) {
        float m = Math.min(W, H);
        if (!Usage.permitted) {
            Draw.icon(cv, HOURGLASS, W / 2f, H * (item.h >= 2 ? 0.4f : 0.5f), m * 0.3f, p, fg(), acc());
            if (item.h >= 2)
                Draw.label(cv, th, T.t("Tocca per attivare"), W / 2f, H * 0.82f, m * 0.08f, sub(), 0, p, W * 0.9f);
            return;
        }
        List<Usage.App> top = Usage.today;
        String total = Usage.shortFmt(Usage.total);
        float pad = item.h >= 2 ? m * 0.12f : H * 0.2f;
        if (item.h == 1) {
            Draw.label(cv, th, T.t("Oggi"), pad, H * 0.36f, H * 0.14f, sub(), -1, p, W * 0.4f);
            Draw.big(cv, th, total, pad, H * 0.64f, H * 0.26f, W * 0.5f, fg(), "hmHM", acc(), -1, p, false);
            if (W > H * 3 && !top.isEmpty()) {
                // app più usata a destra
                String name = Usage.name(top.get(0).pkg);
                Draw.label(cv, th, name, W - pad, H * 0.42f, H * 0.14f, fg(), 1, p, W * 0.4f);
                Draw.label(cv, th, Usage.fmt(top.get(0).ms), W - pad, H * 0.68f, H * 0.12f, sub(), 1, p, W * 0.4f);
            }
            return;
        }
        boolean wide = W >= H * 1.6f;
        float leftW = wide ? W * 0.42f : W - pad * 2;
        Draw.label(cv, th, T.t("Tempo di utilizzo"), pad, pad + m * 0.06f, m * 0.07f, sub(), -1, p, leftW);
        Draw.big(cv, th, total, pad, pad + m * 0.27f, m * 0.17f, leftW, fg(), "hmHM", acc(), -1, p, false);
        if (wide) {
            Draw.label(cv, th, Usage.unlocks + T.n(Usage.unlocks, " sblocco", " sblocchi"), pad, H - pad, m * 0.065f, sub(), -1, p, leftW);
        }
        float x0 = wide ? W * 0.5f : pad, x1 = W - pad;
        float y0 = wide ? pad : pad + m * 0.48f, y1 = H - pad * 0.8f;
        int rows = wide ? 4 : 3;
        float rh = (y1 - y0) / rows;
        long max = top.isEmpty() ? 1 : Math.max(1, top.get(0).ms);
        android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(th.bodyFace);
        tp.setTextSize(Math.min(rh * 0.36f, m * 0.075f));
        if (top.isEmpty()) {
            tp.setColor(sub());
            cv.drawText(T.t("Ancora niente oggi"), x0, y0 + rh * 0.6f, tp);
            return;
        }
        for (int i = 0; i < rows && i < top.size(); i++) {
            Usage.App a = top.get(i);
            float y = y0 + rh * i;
            String t = Usage.shortFmt(a.ms);
            tp.setColor(sub());
            float tw = tp.measureText(t);
            cv.drawText(t, x1 - tw, y + rh * 0.45f, tp);
            tp.setColor(fg());
            cv.drawText(android.text.TextUtils.ellipsize(Usage.name(a.pkg), tp, x1 - x0 - tw - m * 0.04f,
                    android.text.TextUtils.TruncateAt.END).toString(), x0, y + rh * 0.45f, tp);
            // barra a puntini proporzionale
            int n = 14;
            float pitch = (x1 - x0) / n, r = Math.min(pitch * 0.3f, rh * 0.09f);
            int lit = Math.max(1, Math.round(n * a.ms / (float) max));
            for (int k = 0; k < n; k++) {
                p.setColor(k < lit ? (i == 0 ? acc() : fg()) : Theme.alpha(fg(), 0.13f));
                cv.drawCircle(x0 + pitch * k + pitch / 2f, y + rh * 0.72f, r, p);
            }
        }
    }

    private void focus(Canvas cv, float W, float H) {
        boolean on = Usage.focusActive;
        long until = Usage.focusUntil;
        float m = Math.min(W, H);
        if (isCircle()) {
            if (on) {
                p.setColor(Theme.alpha(acc(), 0.22f));
                cv.drawCircle(W / 2f, H / 2f, m * 0.4f, p);
            }
            Draw.icon(cv, TARGET, W / 2f, H / 2f, m * 0.42f, p, on ? acc() : Theme.alpha(fg(), 0.55f), on ? fg() : acc());
            return;
        }
        Draw.icon(cv, TARGET, H / 2f, H / 2f, H * 0.42f, p, on ? acc() : Theme.alpha(fg(), 0.55f), on ? fg() : acc());
        float x = H * 0.95f;
        Draw.label(cv, th, "Focus", x, H * 0.44f, H * 0.17f, fg(), -1, p, W - x - H * 0.2f);
        String st = !on ? T.t("Spento") : until == Long.MAX_VALUE ? T.t("Attivo")
                : T.t("Fino alle ") + new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", T.loc()).format(new Date(until));
        Draw.label(cv, th, st, x, H * 0.7f, H * 0.13f, on ? acc() : sub(), -1, p, W - x - H * 0.2f);
    }

    // ---------- sveglia e ricerca ----------

    private void alarm(Canvas cv, float W, float H) {
        String next = State.nextAlarm;
        if (isCircle()) {
            if (next.isEmpty()) {
                Draw.icon(cv, Draw.BELL, W / 2f, H / 2f, W * 0.36f, p, fg(), acc());
            } else {
                Draw.icon(cv, Draw.BELL, W / 2f, H * 0.33f, W * 0.2f, p, fg(), acc());
                Draw.big(cv, th, next, W / 2f, H * 0.64f, H * 0.12f, W * 0.66f, fg(), ":", acc(), 0, p, false);
            }
            return;
        }
        Draw.icon(cv, Draw.BELL, H / 2f, H / 2f, H * 0.42f, p, fg(), acc());
        Draw.big(cv, th, next.isEmpty() ? "--:--" : next, W - H * 0.35f, H / 2f, H * 0.3f, W * 0.55f,
                fg(), ":", acc(), 1, p, false);
    }

    private void search(Canvas cv, float W, float H) {
        Draw.icon(cv, Draw.SEARCH, H * 0.55f, H / 2f, H * 0.34f, p, fg(), acc());
        Draw.label(cv, th, T.t("Cerca app"), H * 0.95f, H / 2f + H * 0.07f, H * 0.2f, sub(), -1, p, W * 0.6f);
        p.setColor(acc());
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(W - H / 2f, H / 2f, H * 0.07f, p);
    }
}
