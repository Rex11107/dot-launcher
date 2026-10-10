package it.rex.dotlauncher;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Locale;

/** Funzioni di disegno condivise: testo a puntini, numeri, etichette, icone a puntini. */
final class Draw {

    // Icone a puntini 9x9: '#' = colore principale, 'R' = colore d'accento.
    static final String[] SUN = {
            "....#....", ".#.....#.", "...###...", "..#####..", "#.##R##.#",
            "..#####..", "...###...", ".#.....#.", "....#...."};
    static final String[] PARTLY = {
            ".R.......", "...RR....", "R.RRRR...", "..RR.##..", "..R#####.",
            ".########", "#########", ".#######.", "........."};
    static final String[] CLOUD = {
            ".........", "..##.....", ".####.##.", ".########", "#########",
            "#########", ".#######.", ".........", "........."};
    static final String[] RAIN = {
            "..##.....", ".####.##.", ".########", "#########", ".#######.",
            ".........", ".R..R..R.", "R..R..R..", "........."};
    static final String[] SNOW = {
            "..##.....", ".####.##.", ".########", "#########", ".#######.",
            ".........", "#...#...#", "..#...#..", "#...#...#"};
    static final String[] FOG = {
            "#######..", ".........", "..#######", ".........", "#########",
            ".........", ".#######.", ".........", "........."};
    static final String[] STORM = {
            "..##.....", ".####.##.", ".########", "#########", ".###R###.",
            "....RR...", "...RR....", "....R....", "...R....."};
    static final String[] BELL = {
            "....#....", "...###...", "..#####..", "..#####..", "..#####..",
            ".#######.", "#########", ".........", "....R...."};
    static final String[] BOLT = {
            "....###..", "...###...", "..###....", ".#######.", "....###..",
            "...###...", "..##.....", ".#.......", "........."};
    static final String[] SEARCH = {
            "..###....", ".#...#...", "#.....#..", "#.....#..", "#.....#..",
            ".#...#...", "..###.#..", ".......#.", "........#"};
    static final String[] STEPS = {
            ".##......", "####.....", "####.....", "####..##.", ".##..####",
            ".....####", ".##..####", ".##...##.", "......##."};
    static final String[] PHOTO = {
            "#########", "#.......#", "#....RR.#", "#....RR.#", "#.#.....#",
            "####..#.#", "#####.###", "#########", "........."};
    static final String[] PLUS = {".......", "...#...", "...#...", ".#####.", "...#...", "...#...", "......."};

    static String[] weatherIcon(int code) {
        if (code == 0) return SUN;
        if (code <= 2) return PARTLY;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        if (code >= 51) return RAIN;
        return CLOUD;
    }

    static int cols(String t) {
        int c = 0;
        for (int i = 0; i < t.length(); i++) {
            if (i > 0) c++;
            c += DotFont.glyph(t.charAt(i))[0].length();
        }
        return Math.max(c, 1);
    }

    /** Effetto display: si vedono anche i punti spenti della griglia. */
    static boolean ghost = true;
    /** Animazioni a punti (cifre che si accendono, ricarica, onda). */
    static boolean anim = true;

    /** Testo a puntini con angolo in alto a sinistra in (x, y). */
    static void dots(Canvas cv, String text, float x, float y, float pitch, Paint p,
                     int color, String accentChars, int accent) {
        dots(cv, text, x, y, pitch, p, color, accentChars, accent, null);
    }

    /**
     * Come sopra; progress (facoltativo) indica per ogni carattere quanto è "acceso" (0..1):
     * i punti delle cifre appena cambiate crescono riga per riga.
     */
    static void dots(Canvas cv, String text, float x, float y, float pitch, Paint p,
                     int color, String accentChars, int accent, float[] progress) {
        String t = DotTextView.normalize(text);
        float r = pitch * 0.39f;
        boolean showGhost = ghost && pitch >= 5f;
        int ghostColor = Theme.alpha(color, 0.12f);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            String[] g = DotFont.glyph(ch);
            int w = g[0].length();
            int on = accentChars != null && accentChars.indexOf(ch) >= 0 ? accent : color;
            float k = progress != null && i < progress.length ? progress[i] : 1f;
            int lastCol = i < t.length() - 1 ? w : w - 1; // la colonna di spazio fa parte del display
            for (int row = 0; row < DotFont.ROWS; row++) {
                for (int col = 0; col <= lastCol; col++) {
                    float cx = x + col * pitch + pitch / 2f, cy = y + row * pitch + pitch / 2f;
                    boolean lit = col < w && g[row].charAt(col) == '#';
                    if (!lit) {
                        if (showGhost) {
                            p.setColor(ghostColor);
                            cv.drawCircle(cx, cy, r * 0.82f, p);
                        }
                        continue;
                    }
                    float rk = 1f;
                    if (k < 1f) {
                        float v = k * 1.6f - row * 0.085f;
                        rk = v <= 0 ? 0 : v >= 1 ? 1 : easeOutBack(v);
                        if (showGhost) {
                            p.setColor(ghostColor);
                            cv.drawCircle(cx, cy, r * 0.82f, p);
                        }
                    }
                    if (rk <= 0) continue;
                    p.setColor(on);
                    cv.drawCircle(cx, cy, r * rk, p);
                }
            }
            x += (w + 1) * pitch;
        }
    }

    static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /**
     * Numeri e titoli grandi: a puntini nello stile Classico, sottili nello stile Nuovo.
     * align: -1 sinistra, 0 centro, 1 destra. (ax, cy) = ancoraggio orizzontale e centro verticale.
     */
    static float big(Canvas cv, Theme th, String text, float ax, float cy, float h, float maxW,
                     int color, String accentChars, int accent, int align, Paint p, boolean forceDots) {
        return big(cv, th, text, ax, cy, h, maxW, color, accentChars, accent, align, p, forceDots, null);
    }

    static float big(Canvas cv, Theme th, String text, float ax, float cy, float h, float maxW,
                     int color, String accentChars, int accent, int align, Paint p, boolean forceDots,
                     float[] progress) {
        p.setStyle(Paint.Style.FILL);
        if (th.dots || forceDots) {
            String t = DotTextView.normalize(text);
            int cols = cols(t);
            float pitch = h / 7f;
            if (cols * pitch > maxW) pitch = maxW / cols;
            float w = cols * pitch;
            float x = align < 0 ? ax : align == 0 ? ax - w / 2f : ax - w;
            dots(cv, t, x, cy - 3.5f * pitch, pitch, p, color, accentChars, accent, progress);
            return w;
        }
        p.setTypeface(th.numFace);
        p.setTextSize(h * 1.38f);
        float w = p.measureText(text);
        if (w > maxW) {
            p.setTextSize(p.getTextSize() * maxW / w);
            w = maxW;
        }
        float x = align < 0 ? ax : align == 0 ? ax - w / 2f : ax - w;
        float base = cy + p.getTextSize() * 0.36f;
        for (int i = 0; i < text.length(); i++) {
            String s = String.valueOf(text.charAt(i));
            p.setColor(accentChars != null && accentChars.contains(s) ? accent : color);
            cv.drawText(s, x, base, p);
            x += p.measureText(s);
        }
        return w;
    }

    /** Etichetta piccola: monospaziata maiuscola (Classico) o sans-serif (Nuovo). */
    static float label(Canvas cv, Theme th, String s, float ax, float baseline, float size,
                       int color, int align, Paint p, float maxW) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(th.labelFace);
        p.setTextSize(size);
        p.setColor(color);
        if (th.upperLabels) s = s.toUpperCase(Locale.ITALIAN);
        p.setLetterSpacing(th.upperLabels ? 0.04f : 0f);
        float w = p.measureText(s);
        if (maxW > 0 && w > maxW) {
            p.setTextSize(size * maxW / w);
            w = maxW;
        }
        float x = align < 0 ? ax : align == 0 ? ax - w / 2f : ax - w;
        cv.drawText(s, x, baseline, p);
        p.setLetterSpacing(0f);
        return w;
    }

    static void icon(Canvas cv, String[] pat, float cx, float cy, float size, Paint p, int color, int accent) {
        int n = pat.length;
        float pitch = size / n;
        float x0 = cx - size / 2f;
        float y0 = cy - size / 2f;
        float r = pitch * 0.4f;
        p.setStyle(Paint.Style.FILL);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < pat[row].length(); col++) {
                char ch = pat[row].charAt(col);
                if (ch == '.') continue;
                p.setColor(ch == 'R' ? accent : color);
                cv.drawCircle(x0 + col * pitch + pitch / 2f, y0 + row * pitch + pitch / 2f, r, p);
            }
        }
    }

    private Draw() {}
}
