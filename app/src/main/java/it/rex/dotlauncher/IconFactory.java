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
 *
 * Approccio prudente: si trasforma un'icona solo quando si è sicuri del risultato.
 *  1) Icona tematica ufficiale dell'app (Android 13+): si usa sempre.
 *  2) Icona "piatta" (pochi colori, es. simbolo bianco su sfondo colorato): si ricava il simbolo,
 *     e lo si usa solo se supera tutti i controlli di qualità.
 *  3) Tutto il resto (immagini, giochi, sfumature, foto): icona originale, non modificata.
 * L'utente può comunque forzare "originale" o "Nothing" per ogni singola app.
 */
final class IconFactory {
    static final String AUTO = "auto";        // cerchio come le tessere, glifo a contrasto
    static final String INVERSE = "inverse";  // colori invertiti
    static final String COLOR = "color";      // icone originali

    static final int MODE_AUTO = 0;
    static final int MODE_ORIGINAL = 1;
    static final int MODE_GLYPH = 2;

    private static final float GLYPH = 0.50f; // lato del simbolo rispetto al cerchio

    static Bitmap make(Drawable d, int size, String style, boolean red, int mode, Theme th) {
        if (d == null) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        synchronized (d) { // lo stesso Drawable può essere disegnato da due thread
            return makeLocked(d, size, style, red, mode, th);
        }
    }

    private static Bitmap makeLocked(Drawable d, int size, String style, boolean red, int mode, Theme th) {
        if (mode == MODE_ORIGINAL || (COLOR.equals(style) && !red && mode != MODE_GLYPH)) {
            return original(d, size);
        }

        Bitmap mask = glyph(d, size * 2, mode == MODE_GLYPH); // risoluzione doppia per bordi puliti
        Rect box = mask == null ? null : bounds(mask);
        if (box == null) return original(d, size); // non trasformabile in modo pulito: si lascia com'è

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

        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
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
        float scale = size * GLYPH / Math.max(box.width(), box.height());
        float w = box.width() * scale, h = box.height() * scale;
        RectF dst = new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f);
        p.setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(mask, box, dst, p);
        return out;
    }

    /** Icona originale dell'app, disegnata così com'è. */
    static Bitmap original(Drawable d, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, size, size);
        d.draw(new Canvas(out));
        return out;
    }

    /** Icona di un pacchetto di icone: si disegna così com'è. */
    static Bitmap fromPack(Drawable d, int size) {
        synchronized (d) {
            return original(d, size);
        }
    }

    /**
     * Restituisce la forma del simbolo (conta solo l'alfa), oppure null se l'icona
     * non si può trasformare in modo affidabile.
     */
    private static Bitmap glyph(Drawable d, int size, boolean force) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int extra = size / 4;
        Drawable layer = d;
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            if (Build.VERSION.SDK_INT >= 33 && a.getMonochrome() != null) {
                Drawable m = a.getMonochrome().mutate();
                m.setBounds(-extra, -extra, size + extra, size + extra);
                m.draw(c);
                return b; // icona tematica ufficiale: affidabile per definizione
            }
            if (a.getForeground() != null) layer = a.getForeground();
            else extra = 0;
        } else {
            extra = 0;
        }
        layer.setBounds(-extra, -extra, size + extra, size + extra);
        layer.draw(c);

        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);

        // 1) l'icona deve essere "piatta": pochi colori. Immagini, foto e sfumature si lasciano stare.
        if (!force && !isFlat(px)) return null;

        // 2) si separa il simbolo dalla piastrella di sfondo (se c'è)
        int[] mask = extract(px, w, h);
        if (mask == null) return null;
        b.setPixels(mask, 0, w, 0, 0, w, h);
        if (force) return b;

        // 3) controllo di qualità del risultato
        Rect box = bounds(b);
        if (box == null || !looksLikeSymbol(mask, w, h, box)) return null;
        return b;
    }

    /** Le 4 tinte principali coprono quasi tutta l'icona? */
    private static boolean isFlat(int[] px) {
        int[] hist = new int[4096];
        int opaque = 0;
        for (int c : px) {
            if ((c >>> 24) < 200) continue;
            opaque++;
            hist[key(c)]++;
        }
        if (opaque < 50) return false;
        int[] top = new int[4];
        for (int v : hist) {
            if (v > top[3]) {
                int i = 3;
                while (i > 0 && v > top[i - 1]) {
                    top[i] = top[i - 1];
                    i--;
                }
                top[i] = v;
            }
        }
        int sum = top[0] + top[1] + top[2] + top[3];
        return sum >= opaque * 0.82f;
    }

    private static int key(int c) {
        return ((c >> 20) & 0xF) << 8 | ((c >> 12) & 0xF) << 4 | ((c >> 4) & 0xF);
    }

    /**
     * Toglie la piastrella: se un colore domina il contorno della forma, è lo sfondo e si toglie.
     * Altrimenti il simbolo è la sagoma intera (icone senza sfondo).
     */
    private static int[] extract(int[] px, int w, int h) {
        int[] hist = new int[4096];
        int opaque = 0;
        int l = w, t = h, r = -1, bo = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                if ((c >>> 24) < 160) continue;
                opaque++;
                hist[key(c)]++;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > bo) bo = y;
            }
        }
        if (opaque == 0) return null;
        int best = 0;
        for (int i = 1; i < hist.length; i++) if (hist[i] > hist[best]) best = i;

        int edge = 0, edgeHit = 0;
        int inset = Math.max(2, (r - l) / 20);
        for (int y = t; y <= bo; y += 2) {
            for (int x = l; x <= r; x += 2) {
                boolean nearEdge = x - l < inset || r - x < inset || y - t < inset || bo - y < inset;
                if (!nearEdge) continue;
                int c = px[y * w + x];
                if ((c >>> 24) < 160) continue;
                edge++;
                if (key(c) == best) edgeHit++;
            }
        }
        boolean plate = edge > 0 && edgeHit > edge * 0.6f && hist[best] > opaque * 0.3f;

        int[] out = new int[px.length];
        if (!plate) {
            for (int i = 0; i < px.length; i++) out[i] = px[i] & 0xFF000000;
            return out;
        }
        long rs = 0, gs = 0, bs = 0;
        int n = 0;
        for (int c : px) {
            if ((c >>> 24) < 160 || key(c) != best) continue;
            rs += (c >> 16) & 0xFF;
            gs += (c >> 8) & 0xFF;
            bs += c & 0xFF;
            n++;
        }
        int pr = (int) (rs / n), pg = (int) (gs / n), pb = (int) (bs / n);
        int kept = 0;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = c >>> 24;
            if (a == 0) continue;
            int dist = Math.max(Math.abs(((c >> 16) & 0xFF) - pr),
                    Math.max(Math.abs(((c >> 8) & 0xFF) - pg), Math.abs((c & 0xFF) - pb)));
            float k = (dist - 28) / 52f;
            if (k <= 0) continue;
            if (k > 1) k = 1;
            int na = (int) (a * k);
            out[i] = na << 24;
            if (na > 128) kept++;
        }
        return kept < opaque * 0.02f ? null : out;
    }

    /**
     * Il risultato sembra un simbolo pulito?
     * - non è una macchia piena (cerchio o quadrato senza dettagli);
     * - bordi netti (non "polvere" di pixel semitrasparenti);
     * - poche parti separate (le immagini estratte male si spezzano in tanti frammenti).
     */
    private static boolean looksLikeSymbol(int[] m, int w, int h, Rect box) {
        int bw = box.width(), bh = box.height();
        if (bw < 8 || bh < 8) return false;

        // macchia piena: l'ellisse inscritta è quasi tutta piena
        float cx = box.left + bw / 2f, cy = box.top + bh / 2f, rx = bw / 2f * 0.92f, ry = bh / 2f * 0.92f;
        int inside = 0, full = 0, soft = 0, visible = 0;
        for (int y = box.top; y < box.bottom; y += 2) {
            for (int x = box.left; x < box.right; x += 2) {
                int a = m[y * w + x] >>> 24;
                if (a > 30) visible++;
                if (a > 30 && a < 200) soft++;
                float dx = (x - cx) / rx, dy = (y - cy) / ry;
                if (dx * dx + dy * dy > 1f) continue;
                inside++;
                if (a > 128) full++;
            }
        }
        if (inside > 0 && full / (float) inside > 0.95f) return false;
        if (visible == 0 || soft / (float) visible > 0.30f) return false;

        // frammenti: si conta su una griglia 48x48
        int g = 48;
        boolean[] on = new boolean[g * g];
        for (int gy = 0; gy < g; gy++) {
            for (int gx = 0; gx < g; gx++) {
                int x = box.left + gx * bw / g, y = box.top + gy * bh / g;
                on[gy * g + gx] = (m[y * w + x] >>> 24) > 128;
            }
        }
        boolean[] seen = new boolean[g * g];
        int[] stack = new int[g * g];
        int parts = 0, specks = 0;
        for (int i = 0; i < on.length; i++) {
            if (!on[i] || seen[i]) continue;
            int sp = 0, cells = 0;
            stack[sp++] = i;
            seen[i] = true;
            while (sp > 0) {
                int k = stack[--sp];
                cells++;
                int kx = k % g, ky = k / g;
                for (int d = 0; d < 4; d++) {
                    int nx = kx + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int ny = ky + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= g || ny >= g) continue;
                    int nk = ny * g + nx;
                    if (on[nk] && !seen[nk]) {
                        seen[nk] = true;
                        stack[sp++] = nk;
                    }
                }
            }
            if (cells <= 2) specks++;
            else parts++;
        }
        return parts >= 1 && parts <= 12 && specks <= 6;
    }

    /** Rettangolo che contiene i pixel visibili. */
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
