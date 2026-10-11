package it.rex.dotlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Pallino di notifica in alto a destra di un'icona (stile Nothing: un solo punto, senza numero). */
class BadgeDrawable extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color, ring;

    BadgeDrawable(int color, int ring) {
        this.color = color;
        this.ring = ring;
    }

    /** Disegna il pallino per un'icona di lato size centrata in (cx, cy). */
    static void draw(Canvas cv, Paint p, float cx, float cy, float size, int color, int ring) {
        float r = size * 0.11f;
        float x = cx + size * 0.36f, y = cy - size * 0.36f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(ring);
        cv.drawCircle(x, y, r * 1.45f, p);
        p.setColor(color);
        cv.drawCircle(x, y, r, p);
    }

    @Override
    public void draw(Canvas cv) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        draw(cv, p, b.exactCenterX(), b.exactCenterY(), s, color, ring);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
