package it.rex.dotlauncher;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * Pallino di notifica su un'icona. Di base è un solo punto in alto a destra (lo stile del launcher);
 * nelle impostazioni si possono scegliere colore, dimensione, angolo e il numero delle notifiche.
 */
class BadgeDrawable extends Drawable {
    // ---------- aspetto scelto nelle impostazioni ----------

    /** 0 = punto, 1 = numero delle notifiche. */
    static int style;
    /** 0 = piccolo, 1 = medio, 2 = grande. */
    static int sizeIdx = 1;
    /** 0 = in alto a destra, 1 = in alto a sinistra, 2 = in basso a destra, 3 = in basso a sinistra. */
    static int corner;
    /** 0 = colore d'accento del tema, altrimenti il colore scelto. */
    static int color;

    static final float[] SCALES = {0.75f, 1f, 1.35f};
    static final int[] COLORS = {0, 0xFFD71921, 0xFFFF6A13, 0xFF2EBD59, 0xFF2F6BFF, 0xFF8B5CF6, 0xFFFFFFFF, 0xFF111111};

    static void configure(SharedPreferences p) {
        style = clamp(p.getInt("badgeStyle", 0), 0, 1);
        sizeIdx = clamp(p.getInt("badgeSize", 1), 0, 2);
        corner = clamp(p.getInt("badgePos", 0), 0, 3);
        color = p.getInt("badgeColor", 0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Testo del numero: fino a 99, poi "99+". Vuoto se si mostra solo il punto. */
    static String label(int count) {
        if (style != 1 || count <= 0) return "";
        return count > 99 ? "99+" : String.valueOf(count);
    }

    /** Colore del testo sul pallino: bianco o quasi nero, quello che si legge meglio. */
    static int textOn(int bg) {
        double l = 0.2126 * lin(Color.red(bg)) + 0.7152 * lin(Color.green(bg)) + 0.0722 * lin(Color.blue(bg));
        // contrasto con il bianco (l=1) e con il quasi nero (#111, l≈0.0056)
        double withWhite = 1.05 / (l + 0.05), withBlack = (l + 0.05) / (0.0056 + 0.05);
        return withWhite >= withBlack ? 0xFFFFFFFF : 0xFF111111;
    }

    private static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /**
     * Centro del pallino per un'icona di lato size centrata in (cx, cy): {x, y}.
     * Con il numero il pallino è più grande, quindi sta un po' più verso l'angolo.
     */
    static float[] anchor(float cx, float cy, float size) {
        float off = size * 0.36f;
        float x = (corner == 1 || corner == 3) ? cx - off : cx + off;
        float y = corner >= 2 ? cy + off : cy - off;
        return new float[]{x, y};
    }

    private static final RectF rf = new RectF();

    /**
     * Sposta il pallino verso il centro quanto basta perché resti dentro l'icona
     * (con un piccolo margine, come il punto originale): altrimenti nel dock e nel cassetto verrebbe tagliato.
     */
    static float[] inside(float cx, float cy, float size, float x, float y, float halfW, float halfH) {
        float lim = size * 0.53f;
        if (x + halfW > cx + lim) x = cx + lim - halfW;
        if (x - halfW < cx - lim) x = cx - lim + halfW;
        if (y + halfH > cy + lim) y = cy + lim - halfH;
        if (y - halfH < cy - lim) y = cy - lim + halfH;
        return new float[]{x, y};
    }

    /** Pallino senza numero (cartelle e chiamate che non conoscono il conteggio). */
    static void draw(Canvas cv, Paint p, float cx, float cy, float size, int accent, int ring) {
        draw(cv, p, cx, cy, size, accent, ring, 0);
    }

    /** Disegna il pallino per un'icona di lato size centrata in (cx, cy). */
    static void draw(Canvas cv, Paint p, float cx, float cy, float size, int accent, int ring, int count) {
        int fill = color != 0 ? color : accent;
        float scale = SCALES[sizeIdx];
        float[] a = anchor(cx, cy, size);
        float x = a[0], y = a[1];
        String num = label(count);
        p.setStyle(Paint.Style.FILL);
        if (num.isEmpty()) {
            float r = size * 0.11f * scale;
            float[] in = inside(cx, cy, size, x, y, r * 1.45f, r * 1.45f);
            x = in[0];
            y = in[1];
            p.setColor(ring);
            cv.drawCircle(x, y, r * 1.45f, p);
            p.setColor(fill);
            cv.drawCircle(x, y, r, p);
            return;
        }
        // numero: pillola che si allarga con le cifre
        float r = size * 0.16f * scale;
        // il Paint arriva da chi disegna l'icona: carattere, allineamento e spaziatura si rimettono come erano
        Typeface old = p.getTypeface();
        float oldSize = p.getTextSize();
        Paint.Align oldAlign = p.getTextAlign();
        float oldSpacing = p.getLetterSpacing();
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(r * 1.25f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setLetterSpacing(0f);
        float tw = p.measureText(num);
        float half = Math.max(r, tw / 2f + r * 0.45f);
        float ringW = r * 0.3f;
        float[] in = inside(cx, cy, size, x, y, half + ringW, r + ringW);
        x = in[0];
        y = in[1];
        p.setColor(ring);
        rf.set(x - half - ringW, y - r - ringW, x + half + ringW, y + r + ringW);
        cv.drawRoundRect(rf, r + ringW, r + ringW, p);
        p.setColor(fill);
        rf.set(x - half, y - r, x + half, y + r);
        cv.drawRoundRect(rf, r, r, p);
        p.setColor(textOn(fill));
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(num, x - tw / 2f, y - (fm.ascent + fm.descent) / 2f, p);
        p.setTextAlign(oldAlign);
        p.setLetterSpacing(oldSpacing);
        p.setTypeface(old);
        p.setTextSize(oldSize);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, ring, count;

    BadgeDrawable(int accent, int ring) {
        this(accent, ring, 0);
    }

    BadgeDrawable(int accent, int ring, int count) {
        this.accent = accent;
        this.ring = ring;
        this.count = count;
    }

    @Override
    public void draw(Canvas cv) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        draw(cv, p, b.exactCenterX(), b.exactCenterY(), s, accent, ring, count);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
