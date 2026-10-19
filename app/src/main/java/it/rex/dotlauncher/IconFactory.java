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
 * Icone monocromatiche: glifo su cerchio, nello stile a puntini del launcher.
 *
 * Approccio prudente: si trasforma un'icona solo quando si è sicuri del risultato.
 *  1) Icona tematica ufficiale dell'app (Android 13+): si usa sempre.
 *  2) Icona "piatta" (pochi colori, es. simbolo bianco su sfondo colorato): si ricava il simbolo,
 *     e lo si usa solo se supera tutti i controlli di qualità.
 *  3) Tutto il resto (immagini, giochi, sfumature, foto): icona originale, non modificata.
 * L'utente può comunque forzare "originale" o "monocromatica" per ogni singola app.
 */
final class IconFactory {
    static final String AUTO = "auto";        // cerchio come le tessere, glifo a contrasto
    static final String INVERSE = "inverse";  // colori invertiti
    static final String COLOR = "color";      // icone originali
    static final String TINTED = "tinted";    // simbolo nel colore d'accento su un fondo appena tinto
    static final String CLEAR = "clear";      // fondo trasparente, solo un velo e un bordo sottile

    static final int MODE_AUTO = 0;
    static final int MODE_ORIGINAL = 1;
    static final int MODE_GLYPH = 2;

    private static final float GLYPH = 0.50f; // lato del simbolo rispetto al cerchio
    /** Icone a colori: un po' più piccole della piastrella, perché un colore pieno pesa più di un simbolo. */
    private static final float COLOR_SIZE = 0.80f;

    /** Forma delle icone: 0 cerchio, 1 squircle, 2 quadrato arrotondato, 3 quadrato. */
    static int shape = 0;

    static android.graphics.Path shapePath(float size, float inset) {
        android.graphics.Path path = new android.graphics.Path();
        float l = inset, r = size - inset;
        switch (shape) {
            case 1: {
                // superellisse (n = 4): la forma "squircle"
                float cx = size / 2f, rad = size / 2f - inset;
                for (int i = 0; i <= 72; i++) {
                    double t = Math.PI * 2 * i / 72;
                    double c = Math.cos(t), s = Math.sin(t);
                    float x = cx + (float) (Math.signum(c) * Math.pow(Math.abs(c), 0.5) * rad);
                    float y = cx + (float) (Math.signum(s) * Math.pow(Math.abs(s), 0.5) * rad);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                break;
            }
            case 2: path.addRoundRect(l, l, r, r, size * 0.24f, size * 0.24f, android.graphics.Path.Direction.CW); break;
            case 3: path.addRoundRect(l, l, r, r, size * 0.06f, size * 0.06f, android.graphics.Path.Direction.CW); break;
            default: path.addCircle(size / 2f, size / 2f, size / 2f - inset, android.graphics.Path.Direction.CW);
        }
        return path;
    }

    /** Immagine scelta dall'utente come icona: ritagliata al centro dentro la forma. */
    static Bitmap custom(Bitmap src, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.clipPath(shapePath(size, 0));
        float sc = Math.max(size / (float) src.getWidth(), size / (float) src.getHeight());
        float w = src.getWidth() * sc, h = src.getHeight() * sc;
        c.drawBitmap(src, null, new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    static Bitmap make(Drawable d, int size, String style, boolean red, int mode, Theme th) {
        if (d == null) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        synchronized (d) { // lo stesso Drawable può essere disegnato da due thread
            return makeLocked(d, size, style, red, mode, th);
        }
    }

    private static Bitmap makeLocked(Drawable d, int size, String style, boolean red, int mode, Theme th) {
        if (mode == MODE_ORIGINAL) return original(d, size); // scelta esplicita per questa app: intatta
        if (COLOR.equals(style) && !red && mode != MODE_GLYPH) return uniform(d, size, th);

        Bitmap mask = glyph(d, size * 2, mode == MODE_GLYPH); // risoluzione doppia per bordi puliti
        Rect box = mask == null ? null : bounds(mask);
        if (box == null) return uniform(d, size, th); // non trasformabile in modo pulito: colori originali

        int bg, fg;
        boolean clear = CLEAR.equals(style) && !red;
        if (red) {
            bg = th.accent;
            fg = th.onAccent;
        } else if (TINTED.equals(style)) {
            int base = th.light ? 0xFFFFFFFF : 0xFF161616;
            bg = Theme.blend(base, 0xFF000000 | th.accent, th.light ? 0.16f : 0.22f);
            fg = readable(0xFF000000 | th.accent, bg, th.light);
        } else if (clear) {
            bg = Theme.alpha(th.light ? 0xFF000000 : 0xFFFFFFFF, 0.10f);
            fg = th.light ? 0xFF111111 : 0xFFFFFFFF;
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
        c.drawPath(shapePath(size, 0), p);
        int ring = clear ? Theme.alpha(th.light ? 0xFF000000 : 0xFFFFFFFF, 0.28f)
                : TINTED.equals(style) ? 0 : th.iconStroke;
        if (ring != 0 && !red && !INVERSE.equals(style)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, size / 60f));
            p.setColor(ring);
            c.drawPath(shapePath(size, p.getStrokeWidth() / 2f), p);
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

    /** Il colore d'accento, schiarito o scurito quanto basta per un contrasto di almeno 3:1 sul fondo dell'icona. */
    static int readable(int col, int bg, boolean light) {
        int target = light ? 0xFF000000 : 0xFFFFFFFF;
        int c = col;
        for (int i = 1; i <= 10 && Theme.contrast(c, bg) < 3.0; i++) c = Theme.blend(col, target, i / 10f);
        return c;
    }

    /**
     * Colori originali, ma con la stessa forma e la stessa misura delle altre icone.
     * Molti telefoni consegnano le icone già ritagliate e con un margine trasparente:
     * accanto ai simboli su piastrella sembrerebbero più piccole e di forma diversa.
     */
    static Bitmap uniform(Drawable d, int size, Theme th) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        android.graphics.Path shapePath = shapePath(size, 0);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // icona adattiva: si disegnano i due strati a tutta forma, con la nostra sagoma
        int inner = Math.round(size * COLOR_SIZE), off = (size - inner) / 2;
        android.graphics.Path innerPath = shapePath(inner, 0);
        innerPath.offset(off, off);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int extra = inner / 4; // gli strati sono di 108dp, la parte visibile di 72dp
            c.save();
            c.clipPath(innerPath);
            if (a.getBackground() != null) {
                Drawable b = a.getBackground();
                b.setBounds(off - extra, off - extra, off + inner + extra, off + inner + extra);
                b.draw(c);
            }
            if (a.getForeground() != null) {
                Drawable f = a.getForeground();
                f.setBounds(off - extra, off - extra, off + inner + extra, off + inner + extra);
                f.draw(c);
            }
            c.restore();
            return out;
        }

        // icona già pronta: si toglie il margine trasparente e si guarda quanto è "piena"
        int big = size * 2;
        Bitmap src = original(d, big);
        int w = big, h = big;
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int l = w, t = h, r = -1, btm = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 16) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > btm) btm = y;
                }
            }
        }
        if (r < 0) return out;
        int solid = 0;
        for (int y = t; y <= btm; y++) for (int x = l; x <= r; x++) if ((px[y * w + x] >>> 24) > 200) solid++;
        int bw = r - l + 1, bh = btm - t + 1;
        float fill = solid / (float) (bw * bh);
        Rect box = new Rect(l, t, r + 1, btm + 1);

        if (fill >= 0.75f && Math.abs(bw - bh) <= Math.max(bw, bh) * 0.12f) {
            // piastrella piena (quadrata, tonda, squircle): la si allarga a tutta forma
            float sc = inner / (float) Math.min(bw, bh) * 1.04f; // un filo in più copre i bordi sfumati
            float dw = bw * sc, dh = bh * sc;
            c.save();
            c.clipPath(innerPath);
            c.drawBitmap(src, box, new RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f), p);
            c.restore();
        } else {
            // logo senza fondo: va sulla piastrella del tema, come i simboli
            p.setColor(th.iconBg);
            c.drawPath(shapePath, p);
            if (th.iconStroke != 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, size / 60f));
                p.setColor(th.iconStroke);
                c.drawPath(shapePath(size, p.getStrokeWidth() / 2f), p);
                p.setStyle(Paint.Style.FILL);
            }
            p.setColor(0xFFFFFFFF);
            float sc = size * 0.66f / Math.max(bw, bh);
            float dw = bw * sc, dh = bh * sc;
            c.drawBitmap(src, box, new RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f), p);
        }
        src.recycle();
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
