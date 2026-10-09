package it.rex.dotlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Icone in stile Nothing: glifo monocromatico su cerchio.
 * Usa l'icona tematica dell'app quando esiste, altrimenti ricava un glifo dall'icona originale.
 */
final class IconFactory {
    static final String AUTO = "auto";        // cerchio come le tessere, glifo a contrasto
    static final String INVERSE = "inverse";  // colori invertiti
    static final String COLOR = "color";      // icone originali

    static Bitmap make(Drawable d, int size, String style, boolean red, Theme th) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        if (d == null) return out;
        Canvas c = new Canvas(out);

        if (COLOR.equals(style) && !red) {
            d.setBounds(0, 0, size, size);
            d.draw(c);
            return out;
        }

        int bg, fg;
        if (red) {
            bg = th.accent;
            fg = 0xFFFFFFFF;
        } else if (INVERSE.equals(style)) {
            bg = th.tileAlt;
            fg = th.onTileAlt;
        } else {
            bg = th.tile;
            fg = th.onTile;
            if (th.nuovo) fg = Theme.blend(fg, th.accent, 0.3f);
        }

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(bg);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        if (th.stroke != 0 && !red && !INVERSE.equals(style)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, size / 60f));
            p.setColor(th.stroke);
            c.drawCircle(size / 2f, size / 2f, size / 2f - p.getStrokeWidth(), p);
            p.setStyle(Paint.Style.FILL);
        }

        Bitmap mask = glyph(d, size);
        p.setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(mask, 0, 0, p);
        return out;
    }

    /** Restituisce una bitmap in cui conta solo l'alfa: la forma del glifo. */
    private static Bitmap glyph(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int extra = size / 4;
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            if (Build.VERSION.SDK_INT >= 33 && a.getMonochrome() != null) {
                Drawable m = a.getMonochrome().mutate();
                m.setBounds(-extra, -extra, size + extra, size + extra);
                m.draw(c);
                return b;
            }
            Drawable f = a.getForeground();
            if (f != null) {
                f.setBounds(-extra, -extra, size + extra, size + extra);
                f.draw(c);
                toMask(b, false);
                return b;
            }
        }
        int in = Math.round(size * 0.2f);
        d.setBounds(in, in, size - in, size - in);
        d.draw(c);
        toMask(b, true);
        return b;
    }

    /** Converte i colori in trasparenza: le parti scure restano piene, quelle chiare si attenuano. */
    private static void toMask(Bitmap b, boolean legacy) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            float lum = (0.299f * Color.red(px[i]) + 0.587f * Color.green(px[i]) + 0.114f * Color.blue(px[i])) / 255f;
            float m = legacy ? (1f - lum) * 1.5f : 1f - 0.55f * lum;
            if (m < 0f) m = 0f;
            if (m > 1f) m = 1f;
            px[i] = ((int) (a * m)) << 24;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private IconFactory() {}
}
