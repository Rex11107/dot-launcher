package it.rex.dotlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Icone in stile Nothing: glifo monocromatico su cerchio.
 * 1) Se l'app ha l'icona tematica ufficiale (Android 13+) si usa quella.
 * 2) Altrimenti si ricava il simbolo: si toglie la "piastrella" di sfondo dell'icona,
 *    si tiene solo il disegno, lo si ritaglia e lo si ingrandisce al centro del cerchio.
 */
final class IconFactory {
    static final String AUTO = "auto";        // cerchio come le tessere, glifo a contrasto
    static final String INVERSE = "inverse";  // colori invertiti
    static final String COLOR = "color";      // icone originali

    private static final float GLYPH = 0.50f; // lato del simbolo rispetto al cerchio

    static Bitmap make(Drawable d, int size, String style, boolean red, Theme th) {
        if (d == null) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        synchronized (d) { // lo stesso Drawable può essere disegnato da due thread
            return makeLocked(d, size, style, red, th);
        }
    }

    private static Bitmap makeLocked(Drawable d, int size, String style, boolean red, Theme th) {
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
            bg = th.iconBgAlt;
            fg = th.iconFgAlt;
        } else {
            bg = th.iconBg;
            fg = th.iconFg;
        }

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(bg);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        int ring = th.iconStroke;
        if (ring != 0 && !red && !INVERSE.equals(style)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, size / 60f));
            p.setColor(ring);
            c.drawCircle(size / 2f, size / 2f, size / 2f - p.getStrokeWidth() / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFFFFFFFF); // il simbolo va disegnato pieno, non con la trasparenza del bordo

        Bitmap mask = glyph(d, size * 2); // lavoro a risoluzione doppia per bordi puliti
        Rect box = bounds(mask);
        if (box == null || isBlob(mask, box)) {
            // nessun simbolo riconoscibile (es. immagine di un gioco): icona originale a colori nel cerchio
            drawOriginal(c, d, size, p);
            return out;
        }
        float target = size * GLYPH;
        float scale = target / Math.max(box.width(), box.height());
        float w = box.width() * scale, h = box.height() * scale;
        RectF dst = new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f);
        p.setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(mask, box, dst, p);
        return out;
    }

    /** La sagoma riempie quasi tutto il suo rettangolo: non è un simbolo ma una macchia piena. */
    private static boolean isBlob(Bitmap mask, Rect box) {
        int w = mask.getWidth();
        int[] px = new int[box.width() * box.height()];
        mask.getPixels(px, 0, box.width(), box.left, box.top, box.width(), box.height());
        int full = 0;
        for (int c : px) if ((c >>> 24) > 128) full++;
        float fill = full / (float) px.length;
        float aspect = box.width() / (float) box.height();
        return fill > 0.80f && aspect > 0.7f && aspect < 1.4f;
    }

    /** Icona originale, ritagliata a cerchio e un po' rimpicciolita dentro il cerchio di sfondo. */
    private static void drawOriginal(Canvas c, Drawable d, int size, Paint p) {
        Bitmap src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(src);
        d.setBounds(0, 0, size, size);
        d.draw(sc);
        float in = size * 0.14f;
        android.graphics.Path clip = new android.graphics.Path();
        clip.addCircle(size / 2f, size / 2f, size / 2f - in, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        p.setColorFilter(null);
        c.drawBitmap(src, null, new RectF(in, in, size - in, size - in), p);
        c.restore();
    }

    /** Bitmap in cui conta solo l'alfa: la forma del simbolo. */
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
                return b; // icona tematica ufficiale: già pronta
            }
            Drawable f = a.getForeground();
            if (f != null) {
                f.setBounds(-extra, -extra, size + extra, size + extra);
                f.draw(c);
                extract(b);
                return b;
            }
        }
        d.setBounds(0, 0, size, size);
        d.draw(c);
        extract(b);
        return b;
    }

    /**
     * Separa il simbolo dallo sfondo dell'icona.
     * Se un colore domina (la piastrella), tutto ciò che gli somiglia diventa trasparente.
     * Se non c'è una piastrella, il simbolo è la sagoma intera.
     */
    private static void extract(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);

        int[] hist = new int[4096];
        int opaque = 0;
        for (int c : px) {
            if ((c >>> 24) < 160) continue;
            opaque++;
            hist[((c >> 20) & 0xF) << 8 | ((c >> 12) & 0xF) << 4 | ((c >> 4) & 0xF)]++;
        }
        if (opaque == 0) return;
        int best = 0;
        for (int i = 1; i < hist.length; i++) if (hist[i] > hist[best]) best = i;
        float share = hist[best] / (float) opaque;

        // È davvero una piastrella? Deve coprire buona parte del contorno della forma visibile.
        boolean plate = false;
        if (share > 0.30f) {
            int l = w, t = h, r = -1, bo = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((px[y * w + x] >>> 24) >= 160) {
                        if (x < l) l = x;
                        if (x > r) r = x;
                        if (y < t) t = y;
                        if (y > bo) bo = y;
                    }
                }
            }
            int edge = 0, edgeHit = 0;
            int inset = Math.max(2, (r - l) / 20);
            for (int y = t; y <= bo; y += 2) {
                for (int x = l; x <= r; x += 2) {
                    boolean nearEdge = x - l < inset || r - x < inset || y - t < inset || bo - y < inset;
                    if (!nearEdge) continue;
                    int c = px[y * w + x];
                    if ((c >>> 24) < 160) continue;
                    edge++;
                    int key = ((c >> 20) & 0xF) << 8 | ((c >> 12) & 0xF) << 4 | ((c >> 4) & 0xF);
                    if (key == best) edgeHit++;
                }
            }
            plate = edge > 0 && edgeHit > edge * 0.6f;
        }

        int[] out = new int[px.length];
        int kept = 0;
        if (plate) {
            // colore medio della piastrella
            long rs = 0, gs = 0, bs = 0;
            int n = 0;
            for (int c : px) {
                if ((c >>> 24) < 160) continue;
                int key = ((c >> 20) & 0xF) << 8 | ((c >> 12) & 0xF) << 4 | ((c >> 4) & 0xF);
                if (key != best) continue;
                rs += (c >> 16) & 0xFF;
                gs += (c >> 8) & 0xFF;
                bs += c & 0xFF;
                n++;
            }
            int pr = (int) (rs / n), pg = (int) (gs / n), pb = (int) (bs / n);
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int a = c >>> 24;
                if (a == 0) continue;
                int dist = Math.max(Math.abs(((c >> 16) & 0xFF) - pr),
                        Math.max(Math.abs(((c >> 8) & 0xFF) - pg), Math.abs((c & 0xFF) - pb)));
                float t = (dist - 28) / 52f; // 28..80 di differenza: transizione morbida
                if (t <= 0) continue;
                if (t > 1) t = 1;
                int na = (int) (a * t);
                out[i] = na << 24;
                if (na > 128) kept++;
            }
            // la piastrella era il simbolo stesso (es. forma piena con buchi): si usa la sagoma
            if (kept < opaque * 0.02f) {
                for (int i = 0; i < px.length; i++) out[i] = px[i] & 0xFF000000;
            }
        } else {
            for (int i = 0; i < px.length; i++) out[i] = px[i] & 0xFF000000;
        }
        b.setPixels(out, 0, w, 0, 0, w, h);
    }

    /** Rettangolo che contiene i pixel visibili del simbolo. */
    private static Rect bounds(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int l = w, t = h, r = -1, bo = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 40) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > bo) bo = y;
                }
            }
        }
        if (r < 0) return null;
        return new Rect(l, t, r + 1, bo + 1);
    }

    private IconFactory() {}
}
