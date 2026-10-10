package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * Onda di puntini: un anello di punti che si espande dal basso e svanisce.
 * Si vede tornando alla home e aprendo il cassetto. Non intercetta i tocchi.
 */
class DotWave extends View {
    private static final long DURATION = 560;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float pitch, band;
    private int color = 0xFFFFFFFF;
    private long start = -1;
    private float ox, oy;

    DotWave(Context c) {
        super(c);
        float dp = c.getResources().getDisplayMetrics().density;
        pitch = 15 * dp;
        band = 70 * dp;
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** Fa partire l'onda dal punto (fx, fy) espresso in frazioni della vista (0..1). */
    void play(int color, float fx, float fy) {
        if (!Draw.anim || getWidth() == 0) return;
        this.color = color;
        ox = getWidth() * fx;
        oy = getHeight() * fy;
        start = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (start < 0) return;
        float t = (SystemClock.uptimeMillis() - start) / (float) DURATION;
        if (t >= 1f) {
            start = -1;
            return;
        }
        float W = getWidth(), H = getHeight();
        float maxR = (float) Math.hypot(Math.max(ox, W - ox), Math.max(oy, H - oy));
        float e = 1f - (1f - t) * (1f - t) * (1f - t); // easeOutCubic
        float front = e * (maxR + band);
        float fade = 1f - t;
        p.setStyle(Paint.Style.FILL);
        float x0 = (W % pitch) / 2f + pitch / 2f, y0 = (H % pitch) / 2f + pitch / 2f;
        for (float y = y0; y < H; y += pitch) {
            for (float x = x0; x < W; x += pitch) {
                float d = (float) Math.hypot(x - ox, y - oy);
                float behind = front - d; // >0 se l'onda è già passata
                if (behind < 0 || behind > band) continue;
                float k = 1f - behind / band; // più intenso sul fronte
                float a = k * k * fade * 0.55f;
                if (a < 0.02f) continue;
                p.setColor(Theme.alpha(color, a));
                cv.drawCircle(x, y, pitch * (0.12f + 0.16f * k), p);
            }
        }
        postInvalidateOnAnimation();
    }
}
