package it.rex.dotlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** Sfondi originali generati al volo, nello spirito dell'estetica Nothing. */
final class WallpaperGen {
    static final String[] NAMES = {
            "Puntini", "Onde scure", "Sfere", "Rosso e nero",
            "Mosaico", "Pieghe", "Anelli chiari", "Puntini chiari"
    };
    static final int RED = 0xFFD71921;

    static Bitmap make(int style, int w, int h, long seed) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Random r = new Random(seed);
        switch (style) {
            case 0: dotField(c, w, h, r, false); break;
            case 1: waves(c, w, h, r); break;
            case 2: spheres(c, w, h, r); break;
            case 3: redBlack(c, w, h, r); break;
            case 4: mosaic(c, w, h, r); break;
            case 5: folds(c, w, h, r); break;
            case 6: rings(c, w, h, r); break;
            default: dotField(c, w, h, r, true); break;
        }
        return b;
    }

    private static float field(float x, float y, float[] k) {
        return (float) (0.5 + 0.28 * Math.sin(x * k[0] + k[1]) * Math.cos(y * k[2] + k[3])
                + 0.22 * Math.sin((x * 0.6 + y) * k[4] + k[5]));
    }

    private static float[] keys(Random r, float scale) {
        float[] k = new float[6];
        for (int i = 0; i < 6; i += 2) {
            k[i] = (0.6f + r.nextFloat() * 1.6f) / scale;
            k[i + 1] = r.nextFloat() * 6.28f;
        }
        return k;
    }

    // Puntini di dimensione variabile, con un gruppo di punti rossi
    private static void dotField(Canvas c, int w, int h, Random r, boolean light) {
        c.drawColor(light ? 0xFFE6E6E6 : 0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pitch = w / 34f;
        float[] k = keys(r, w * 0.35f);
        float redX = w * (0.2f + r.nextFloat() * 0.6f), redY = h * (0.2f + r.nextFloat() * 0.6f);
        float redR = w * (0.12f + r.nextFloat() * 0.12f);
        for (float y = pitch / 2; y < h; y += pitch) {
            for (float x = pitch / 2; x < w; x += pitch) {
                float v = Math.max(0f, Math.min(1f, field(x, y, k)));
                float rad = pitch * 0.46f * v;
                if (rad < pitch * 0.06f) continue;
                boolean red = Math.hypot(x - redX, y - redY) < redR * (0.7f + 0.3f * v);
                int g = light ? (int) (210 - 150 * v) : (int) (40 + 200 * v);
                p.setColor(red ? RED : Color.rgb(g, g, g));
                c.drawCircle(x, y, rad, p);
            }
        }
    }

    // Linee sottili ondulate su fondo nero, come pieghe di tessuto
    private static void waves(Canvas c, int w, int h, Random r) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h, 0xFF101010, 0xFF020202, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, w / 600f));
        int lines = 46;
        float amp = h * (0.08f + r.nextFloat() * 0.08f);
        float f1 = 1.5f + r.nextFloat() * 2f, ph = r.nextFloat() * 6.28f;
        for (int i = 0; i < lines; i++) {
            float t = i / (float) lines;
            Path path = new Path();
            float y0 = h * (0.15f + 0.75f * t);
            for (int s = 0; s <= 60; s++) {
                float x = w * s / 60f;
                float y = y0 + (float) (amp * Math.sin(x / w * f1 * Math.PI + ph + t * 2.2)
                        + amp * 0.4 * Math.cos(x / w * 5 + t * 6));
                if (s == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            int a = (int) (18 + 70 * Math.sin(t * Math.PI));
            p.setColor(Color.argb(a, 255, 255, 255));
            c.drawPath(path, p);
        }
    }

    // Grandi sfere morbide in grigio scuro
    private static void spheres(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF030303);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n = 3 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            float rad = w * (0.45f + r.nextFloat() * 0.5f);
            float cx = w * (r.nextFloat() * 1.2f - 0.1f);
            float cy = h * (0.15f + r.nextFloat() * 0.8f);
            int light = 0xFF000000 | ((40 + r.nextInt(30)) * 0x010101);
            p.setShader(new RadialGradient(cx - rad * 0.35f, cy - rad * 0.4f, rad * 1.3f,
                    new int[]{light, 0xFF0E0E0E, 0xFF030303}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
        }
    }

    // Macchie rosse, bianche e nere con grana
    private static void redBlack(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFFE9E6E1);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] cols = {0xFF111111, RED, 0xFF2A2A2A, RED, 0xFF111111};
        for (int i = 0; i < cols.length; i++) {
            Path path = blob(w * (r.nextFloat()), h * (i / (float) cols.length + r.nextFloat() * 0.2f),
                    w * (0.35f + r.nextFloat() * 0.35f), r);
            p.setColor(cols[i]);
            c.drawPath(path, p);
        }
        grain(c, w, h, r, 0.06f);
    }

    private static Path blob(float cx, float cy, float rad, Random r) {
        Path path = new Path();
        int pts = 90;
        float a1 = r.nextFloat() * 6.28f, a2 = r.nextFloat() * 6.28f;
        int k1 = 2 + r.nextInt(3), k2 = 3 + r.nextInt(4);
        for (int i = 0; i <= pts; i++) {
            double t = Math.PI * 2 * i / pts;
            double rr = rad * (1 + 0.25 * Math.sin(k1 * t + a1) + 0.12 * Math.sin(k2 * t + a2));
            float x = cx + (float) (Math.cos(t) * rr * 1.3);
            float y = cy + (float) (Math.sin(t) * rr * 0.7);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    private static void grain(Canvas c, int w, int h, Random r, float amount) {
        Paint p = new Paint();
        int n = (int) (w * h * amount / 4);
        for (int i = 0; i < n; i++) {
            p.setColor(r.nextBoolean() ? 0x16000000 : 0x14FFFFFF);
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            c.drawRect(x, y, x + 2, y + 2, p);
        }
    }

    // Mosaico di puntini colorati su un campo sfumato
    private static void mosaic(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pitch = w / 48f;
        float[] k = keys(r, w * 0.4f);
        float[] k2 = keys(r, w * 0.3f);
        float hue0 = r.nextFloat() * 360f;
        float[] hsv = new float[3];
        for (float y = pitch / 2; y < h; y += pitch) {
            for (float x = pitch / 2; x < w; x += pitch) {
                float v = Math.max(0f, Math.min(1f, field(x, y, k)));
                float hv = field(x, y, k2);
                hsv[0] = (hue0 + 140f * hv) % 360f;
                hsv[1] = 0.55f + 0.35f * v;
                hsv[2] = 0.15f + 0.85f * v;
                p.setColor(Color.HSVToColor(hsv));
                c.drawCircle(x, y, pitch * 0.42f, p);
            }
        }
    }

    // Pieghe scure diagonali
    private static void folds(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF050505);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bands = 6 + r.nextInt(4);
        float ang = -20f - r.nextFloat() * 30f;
        c.save();
        c.rotate(ang, w / 2f, h / 2f);
        float bw = h * 1.6f / bands;
        for (int i = 0; i < bands; i++) {
            float top = -h * 0.3f + i * bw;
            int a = 0xFF000000 | ((8 + r.nextInt(22)) * 0x010101);
            int b = 0xFF000000 | ((2 + r.nextInt(10)) * 0x010101);
            p.setShader(new LinearGradient(0, top, 0, top + bw, a, b, Shader.TileMode.CLAMP));
            c.drawRect(-w, top, w * 2, top + bw, p);
        }
        c.restore();
    }

    // Anelli concentrici chiari con ombra
    private static void rings(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFFDCDCDC);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = w * (0.2f + r.nextFloat() * 0.6f), cy = h * (0.3f + r.nextFloat() * 0.4f);
        float rad = w * 0.95f;
        int dark = 1 + r.nextInt(3);
        for (int i = 0; i < 6; i++) {
            p.setShadowLayer(w / 40f, w / 120f, w / 90f, 0x40000000);
            p.setColor(i == dark ? 0xFF3A3A3A : (i % 2 == 0 ? 0xFFE4E4E4 : 0xFFD2D2D2));
            c.drawCircle(cx, cy, rad, p);
            rad *= 0.76f;
        }
        p.clearShadowLayer();
        grain(c, w, h, r, 0.03f);
    }

    /** Trasforma una foto in un mosaico di puntini. */
    static Bitmap dotify(Bitmap src, int w, int h, boolean mono) {
        Bitmap scaled = cover(src, w, h);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pitch = w / 56f;
        for (float y = pitch / 2; y < h; y += pitch) {
            for (float x = pitch / 2; x < w; x += pitch) {
                int px = scaled.getPixel(Math.min(w - 1, (int) x), Math.min(h - 1, (int) y));
                float lum = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255f;
                if (mono) {
                    float rad = pitch * 0.48f * lum;
                    if (rad < 0.6f) continue;
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(x, y, rad, p);
                } else {
                    p.setColor(px | 0xFF000000);
                    c.drawCircle(x, y, pitch * 0.45f * (0.35f + 0.65f * lum), p);
                }
            }
        }
        return out;
    }

    /** Ridimensiona e ritaglia al centro per riempire w x h. */
    static Bitmap cover(Bitmap src, int w, int h) {
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private WallpaperGen() {}
}
