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

    private boolean isBattery() {
        return item.type.startsWith("battery");
    }

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

    private int bgColor() {
        return item.tone == 1 ? th.tileAlt : item.tone == 2 ? th.accent : th.tile;
    }

    private int fg() {
        return item.tone == 1 ? th.onTileAlt : item.tone == 2 ? 0xFFFFFFFF : th.onTile;
    }

    private int sub() {
        if (item.tone == 2) return 0xCCFFFFFF;
        if (item.tone == 1) return Theme.alpha(th.onTileAlt, 0.6f);
        return th.sub;
    }

    private int acc() {
        return item.tone == 2 ? 0xFFFFFFFF : th.accent;
    }

    private boolean isCircle() {
        return item.w == 1 && item.h == 1;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0) return;
        animating = false;
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
        }
        if (animating) postInvalidateOnAnimation();
        else if (Draw.anim && State.charging && isBattery() && getWindowVisibility() == VISIBLE && isShown())
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
        return new SimpleDateFormat(h24 ? pattern24 : pattern12, Locale.ITALIAN).format(new Date());
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
            String d = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(new Date());
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
        String wd = new SimpleDateFormat("EEEE", Locale.ITALIAN).format(d);
        String day = new SimpleDateFormat("d", Locale.ITALIAN).format(d);
        String mo = new SimpleDateFormat("MMMM", Locale.ITALIAN).format(d);
        Draw.label(cv, th, wd, W / 2f, H * 0.2f, H * 0.075f, fg(), 0, p, W * 0.8f);
        Draw.big(cv, th, day, W / 2f, H * 0.52f, H * 0.36f, W * 0.8f, acc(), "", acc(), 0, p, false);
        Draw.label(cv, th, mo, W / 2f, H * 0.88f, H * 0.075f, fg(), 0, p, W * 0.8f);
    }

    private void dateDot(Canvas cv, float W, float H) {
        Date d = new Date();
        String wd = new SimpleDateFormat("EEE", Locale.ITALIAN).format(d).replace(".", "");
        String day = new SimpleDateFormat("d", Locale.ITALIAN).format(d);
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
        String mo = new SimpleDateFormat("MMMM", Locale.ITALIAN).format(c.getTime());
        Draw.big(cv, th, mo, pad, pad + headerH * 0.35f, headerH * 0.42f, W * 0.6f, fg(), "", acc(), -1, p, true);
        float gx = pad, gw = W - pad * 2;
        float gy = pad + headerH * 0.8f, gh = H - gy - pad * 0.7f;
        float cw = gw / 7f, ch = gh / (weeks + 1);
        float ts = Math.min(ch * 0.5f, cw * 0.42f);
        String[] wn = {"L", "M", "M", "G", "V", "S", "D"};
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
                p.setColor(item.tone == 2 ? th.accent : 0xFFFFFFFF);
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
            Draw.label(cv, th, "Tocca per il meteo", W / 2f, H * 0.78f, Math.min(W, H) * 0.08f, sub(), 0, p, W * 0.8f);
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
            Draw.label(cv, th, "Tocca per il meteo", W / 2f, H / 2f, H * 0.08f, sub(), 0, p, W * 0.8f);
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
        if (State.charging && Draw.anim) {
            // una scia di punti che gira sull'anello
            float ph = phase(1600);
            for (int k = 0; k < 4; k++) {
                double a = Math.PI * 2 * (ph - k * 0.035f) - Math.PI / 2;
                p.setColor(Theme.alpha(item.tone == 2 ? th.accent : 0xFFFFFFFF, 0.9f - k * 0.22f));
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
        int fillText = item.tone == 2 ? th.accent : 0xFFFFFFFF;
        float free = trackW - fillW - r * 1.2f;
        boolean inside = pc >= 55 || free < H * 0.55f;
        if (State.charging && Draw.anim && pc < 100) {
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
                if (State.charging && Draw.anim && idx > filled && idx <= filled + 3) {
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

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    private void compass(Canvas cv, float W, float H) {
        float cx = W / 2f, cy = H / 2f, R = Math.min(W, H) / 2f;
        if (!State.hasCompass) {
            Draw.label(cv, th, "Bussola non disponibile", cx, cy, R * 0.13f, sub(), 0, p, W * 0.8f);
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
        String title = WData.get(item, "t", "Evento");
        if (target == 0) {
            Draw.label(cv, th, "Tocca per impostare", W / 2f, H / 2f, m * 0.09f, sub(), 0, p, W * 0.8f);
            return;
        }
        int d = WData.daysTo(target);
        String num = d == 0 ? "OGGI" : String.valueOf(Math.abs(d));
        String unit = d == 0 ? title : (Math.abs(d) == 1 ? "giorno" : "giorni") + (d < 0 ? " fa" : "");
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
        String date = new SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).format(new Date(target));
        Draw.label(cv, th, date, W / 2f, H - pad * 0.8f, m * 0.065f, sub(), 0, p, W - pad * 2);
    }

    // ---------- fusi orari ----------

    private String zoneTime(String zone) {
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.ITALIAN);
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
            Draw.label(cv, th, "Tocca per scegliere", W / 2f, H / 2f, m * 0.09f, sub(), 0, p, W * 0.8f);
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
            Draw.label(cv, th, "Contapassi assente", cx, H * 0.78f, m * 0.08f, sub(), 0, p, W * 0.8f);
            return;
        }
        if (!State.stepPerm) {
            Draw.icon(cv, Draw.STEPS, cx, H * 0.4f, m * 0.26f, p, fg(), acc());
            Draw.label(cv, th, "Tocca per attivare", cx, H * 0.78f, m * 0.08f, sub(), 0, p, W * 0.8f);
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
                Draw.label(cv, th, "passi", cx, cy + m * 0.13f, m * 0.065f, sub(), 0, p, R);
                Draw.label(cv, th, "obiettivo " + goal, cx, cy + m * 0.22f, m * 0.05f, acc(), 0, p, R * 1.2f);
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
            Draw.label(cv, th, u.isEmpty() ? "Tocca per scegliere" : "Foto non disponibile", W / 2f, H * 0.76f,
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
        Draw.label(cv, th, "Cerca app", H * 0.95f, H / 2f + H * 0.07f, H * 0.2f, sub(), -1, p, W * 0.6f);
        p.setColor(acc());
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(W - H / 2f, H / 2f, H * 0.07f, p);
    }
}
