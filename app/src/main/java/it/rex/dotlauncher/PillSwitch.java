package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Interruttore a pillola con il puntino che scorre: lo stesso nelle impostazioni e nella pagina a sinistra. */
final class PillSwitch extends View {
    boolean on;
    private final Theme th;
    private final float dp;
    private float pos;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    PillSwitch(Context c, Theme th, boolean on) {
        super(c);
        this.th = th;
        this.on = on;
        dp = c.getResources().getDisplayMetrics().density;
        pos = on ? 1f : 0f;
    }

    void set(boolean v) {
        on = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float target = on ? 1f : 0f;
        if (Math.abs(pos - target) > 0.01f) {
            pos += (target - pos) * 0.35f;
            postInvalidateOnAnimation();
        } else pos = target;
        float W = getWidth(), H = getHeight();
        r.set(0, 0, W, H);
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? th.accent : Theme.alpha(th.onTile, th.light ? 0.15f : 0.2f));
        cv.drawRoundRect(r, H / 2f, H / 2f, p);
        if (!on) {
            // spento: un bordo visibile, così l'interruttore non sparisce nella riga
            float sw = Math.max(1f, dp * 1.5f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(Theme.alpha(th.onTile, th.light ? 0.4f : 0.45f));
            r.inset(sw / 2f, sw / 2f);
            cv.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
        float rad = H * 0.32f;
        float x = H / 2f + (W - H) * pos;
        p.setColor(on ? th.onAccent : th.sub);
        cv.drawCircle(x, H / 2f, rad, p);
    }
}
