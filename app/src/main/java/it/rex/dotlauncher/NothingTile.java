package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Widget in stile Nothing disegnato interamente sul Canvas. */
class NothingTile extends View {
    final Item item;
    private final Theme th;
    private final boolean h24;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

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
        }
    }

    private void drawShape(Canvas cv, float W, float H) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(bgColor());
        float m = Math.min(W, H);
        if (isCircle()) {
            cv.drawCircle(W / 2f, H / 2f, m / 2f, p);
        } else {
            float r = (item.w == 1 || item.h == 1) ? m / 2f : m * 0.16f;
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
                float r = (item.w == 1 || item.h == 1) ? m / 2f : m * 0.16f;
                rf.set(1, 1, W - 1, H - 1);
                cv.drawRoundRect(rf, r, r, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
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
                fg(), ":", acc(), 0, p, true);
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
            double a = Math.PI * 2 * i / 12;
            float rr = R * (i % 3 == 0 ? 0.035f : 0.022f);
            cv.drawCircle(cx + (float) Math.sin(a) * R * 0.8f, cy - (float) Math.cos(a) * R * 0.8f, rr, p);
        }
        Calendar c = Calendar.getInstance();
        float min = c.get(Calendar.MINUTE);
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
        cv.drawCircle(cx, cy + R * 0.72f, R * 0.055f, p);
        p.setColor(fg());
        cv.drawCircle(cx, cy, R * 0.06f, p);
    }

    private void clockPill(Canvas cv, float W, float H) {
        if (H > W) {
            float h = W * 0.38f;
            Draw.big(cv, th, time("HH", "hh"), W / 2f, H * 0.33f, h, W * 0.7f, fg(), "", acc(), 0, p, false);
            Draw.big(cv, th, time("mm", "mm"), W / 2f, H * 0.67f, h, W * 0.7f, fg(), "", acc(), 0, p, false);
            p.setColor(acc());
            cv.drawCircle(W / 2f, H / 2f, W * 0.035f, p);
        } else {
            Draw.big(cv, th, time("HH:mm", "h:mm"), W / 2f, H / 2f, H * 0.4f, W * 0.72f, fg(), ":", acc(), 0, p, false);
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
        float fillW = (W - in * 2) * pct() / 100f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(acc());
        if (fillW > r * 2) {
            rf.set(in, in, in + fillW, H - in);
            cv.drawRoundRect(rf, r, r, p);
        } else if (fillW > 0) {
            cv.drawCircle(in + r, H / 2f, r * Math.max(0.3f, fillW / (r * 2)), p);
        }
        String t = pct() + "%";
        Draw.big(cv, th, t, W - in - r * 0.8f, H / 2f, H * 0.32f, W * 0.4f, item.tone == 2 ? th.accent : fg(), "", acc(), 1, p, false);
        if (State.charging) Draw.icon(cv, Draw.BOLT, in + r, H / 2f, H * 0.36f, p, 0xFFFFFFFF, 0xFFFFFFFF);
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
                p.setColor(on ? (idx == filled ? acc() : fg()) : Theme.alpha(fg(), 0.15f));
                cv.drawCircle(x0 + col * pitch + pitch / 2f, gTop + row * pitch + pitch / 2f, pitch * 0.42f, p);
            }
        }
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
