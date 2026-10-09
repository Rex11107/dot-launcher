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

        boolean[] official = new boolean[1];
        Bitmap mask = glyph(d, size * 2, official); // lavoro a risoluzione doppia per bordi puliti
        Rect box = bounds(mask);
        if (box == null || (!official[0] && isBlob(mask, box))) {
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

    /**
     * La sagoma è una macchia piena (cerchio, quadrato, rettangolo senza dettagli interni)?
     * Si controlla l'ellisse inscritta nel suo rettangolo: un vero simbolo ha sempre dei vuoti.
     */
    private static boolean isBlob(Bitmap mask, Rect box) {
        int bw = box.width(), bh = box.height();
        if (bw < 4 || bh < 4) return true;
        int[] px = new int[bw * bh];
        mask.getPixels(px, 0, bw, box.left, box.top, bw, bh);
        float cx = bw / 2f, cy = bh / 2f, rx = bw / 2f * 0.92f, ry = bh / 2f * 0.92f;
        int inside = 0, full = 0;
        for (int y = 0; y < bh; y += 2) {
            for (int x = 0; x < bw; x += 2) {
                float dx = (x - cx) / rx, dy = (y - cy) / ry;
                if (dx * dx + dy * dy > 1f) continue;
                inside++;
                if ((px[y * bw + x] >>> 24) > 128) full++;
            }
        }
        return inside > 0 && full / (float) inside > 0.95f;
    }

    /**
     * Icona originale a colori: si prende il disegno vero (senza la cornice aggiunta da Android
     * alle app vecchie), lo si ritaglia e lo si mette grande al centro del cerchio, con angoli arrotondati.
     */
    private static void drawOriginal(Canvas c, Drawable d, int size, Paint p) {
        int big = size * 2;
        Bitmap src = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(src);
        Drawable layer = d;
        int extra = 0;
        if (d instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) d).getForeground() != null) {
            layer = ((AdaptiveIconDrawable) d).getForeground();
            extra = big / 4;
        }
        layer.setBounds(-extra, -extra, big + extra, big + extra);
        layer.draw(sc);
        Rect box = bounds(src);
        if (box == null) return;
        float target = size * 0.60f;
        float scale = target / Math.max(box.width(), box.height());
        float w = box.width() * scale, h = box.height() * scale;
        RectF dst = new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f);
        android.graphics.Path clip = new android.graphics.Path();
        float r = Math.min(w, h) * 0.24f;
        clip.addRoundRect(dst, r, r, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        p.setColorFilter(null);
        p.setColor(0xFFFFFFFF);
        c.drawBitmap(src, box, dst, p);
        c.restore();
    }

    /** Icona di un pacchetto di icone: si disegna così com'è. */
    static Bitmap fromPack(Drawable d, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        synchronized (d) {
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(out));
        }
        return out;
    }

    /** Bitmap in cui conta solo l'alfa: la forma del simbolo. */
    private static Bitmap glyph(Drawable d, int size, boolean[] official) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int extra = size / 4;
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            if (Build.VERSION.SDK_INT >= 33 && a.getMonochrome() != null) {
                Drawable m = a.getMonochrome().mutate();
                m.setBounds(-extra, -extra, size + extra, size + extra);
                m.draw(c);
                official[0] = true;
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

        if (!plate && lumaPlate(px, w, h)) {
            b.setPixels(px, 0, w, 0, 0, w, h);
            return;
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

    /**
     * Piastrella sfumata (es. sfondo con gradiente e simbolo bianco): il bordo della forma ha
     * luminosità simile; il simbolo è ciò che esce da quell'intervallo di luminosità.
     * Se riesce, scrive il risultato in px e restituisce true.
     */
    private static boolean lumaPlate(int[] px, int w, int h) {
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
        if (r < 0) return false;
        int band = Math.max(3, (r - l) / 12);
        float min = 1f, max = 0f;
        int samples = 0, opaque = 0;
        for (int y = t; y <= bo; y += 2) {
            for (int x = l; x <= r; x += 2) {
                boolean edge = x - l < band || r - x < band || y - t < band || bo - y < band;
                if (!edge) continue;
                samples++;
                int c = px[y * w + x];
                if ((c >>> 24) < 160) continue;
                opaque++;
                float lum = luma(c);
                if (lum < min) min = lum;
                if (lum > max) max = lum;
            }
        }
        // la forma deve essere piena ai bordi e di luminosità abbastanza uniforme
        if (samples == 0 || opaque < samples * 0.55f || max - min > 0.35f) return false;
        int[] out = new int[px.length];
        int kept = 0, total = 0;
        float lo = min - 0.14f, hi = max + 0.14f;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            total++;
            float lum = luma(px[i]);
            float d = lum > hi ? lum - hi : (lum < lo ? lo - lum : 0f);
            if (d <= 0f) continue;
            float k = Math.min(1f, d / 0.12f);
            out[i] = ((int) (a * k)) << 24;
            if (k > 0.5f) kept++;
        }
        if (kept < total * 0.02f || kept > total * 0.7f) return false;
        System.arraycopy(out, 0, px, 0, px.length);
        return true;
    }

    private static float luma(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
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
