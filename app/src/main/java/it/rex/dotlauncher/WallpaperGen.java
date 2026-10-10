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
            "Mosaico", "Pieghe", "Anelli chiari", "Puntini chiari",
            "Cerchi blu", "Alba arancio", "Geometrie", "Ovali", "Fiore",
            "Luci glyph", "Matrice", "Griglia", "Sabbia", "Tubi",
            "Pillole", "Rami", "Monocromo"
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
            case 7: dotField(c, w, h, r, true); break;
            case 8: blueCircles(c, w, h, r); break;
            case 9: orangeDawn(c, w, h, r); break;
            case 10: geometry(c, w, h, r); break;
            case 11: loops(c, w, h, r); break;
            case 12: flower(c, w, h, r); break;
            case 13: glyphLights(c, w, h, r); break;
            case 14: matrix(c, w, h, r); break;
            case 15: grid(c, w, h, r); break;
            case 16: sand(c, w, h, r); break;
            case 17: tubes(c, w, h, r); break;
            case 18: pills(c, w, h, r); break;
            case 19: branches(c, w, h, r); break;
            default: mono(c, w, h, r); break;
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

    // ---------- nuovi stili ----------

    // Sfumatura dal grigio al blu profondo con cerchi concentrici simmetrici
    private static void blueCircles(Canvas c, int w, int h, Random r) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFA7AFB8, 0xFF3B5878, 0xFF0B1A33},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        float cx = w / 2f, cy = h * (0.36f + r.nextFloat() * 0.18f);
        int n = 10;
        for (int i = 0; i < n; i++) {
            float rad = w * 0.07f + i * w * 0.085f;
            p.setStrokeWidth(i % 3 == 0 ? w / 160f : w / 420f);
            p.setColor(Color.argb(30 + (n - i) * 12, 255, 255, 255));
            c.drawCircle(cx, cy, rad, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 6; i++) {
            float rad = w * 0.07f + (2 + r.nextInt(7)) * w * 0.085f;
            double a = r.nextDouble() * Math.PI;
            float dx = (float) Math.cos(a) * rad, dy = (float) Math.sin(a) * rad;
            p.setColor(0xCCFFFFFF);
            float s = w / 90f;
            c.drawCircle(cx + dx, cy - dy, s, p);
            c.drawCircle(cx - dx, cy - dy, s, p); // simmetria
        }
        grain(c, w, h, r, 0.03f);
    }

    // Dal bianco all'arancio, con file di cerchi
    private static void orangeDawn(Canvas c, int w, int h, Random r) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFF3F1EE, 0xFFFFC08F, 0xFFFF5A14},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int cols = 4;
        float d = w / (float) cols;
        int filled = r.nextInt(cols * 8);
        for (int row = 0; row * d < h; row++) {
            for (int col = 0; col < cols; col++) {
                float x = d * col + d / 2f, y = d * row + d / 2f;
                int idx = row * cols + col;
                if (idx == filled) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF111111);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(w / 500f);
                    p.setColor(Color.argb(60 + (int) (90f * y / h), 30, 20, 10));
                }
                c.drawCircle(x, y, d * 0.42f, p);
            }
        }
        grain(c, w, h, r, 0.04f);
    }


    // Ovali allungati concentrici con sfumature (quattro combinazioni di colore)
    private static void loops(Canvas c, int w, int h, Random r) {
        int[][] ways = {{0xFFFF6B1A, 0xFFFFD6B0}, {0xFF7ED957, 0xFF173D24}, {0xFF4A7BFF, 0xFFC4D3FF},
                {0xFFD0D0D0, 0xFF3A3A3A}};
        int[] cw = ways[r.nextInt(ways.length)];
        c.drawColor(0xFF0A0A0A);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.save();
        c.rotate(-25f - r.nextFloat() * 20f, w / 2f, h / 2f);
        android.graphics.RectF rf = new android.graphics.RectF();
        for (int i = 0; i < 8; i++) {
            float rw = w * (1.5f - i * 0.15f), rh = h * (0.3f - i * 0.03f);
            rf.set(w / 2f - rw / 2f, h / 2f - rh / 2f, w / 2f + rw / 2f, h / 2f + rh / 2f);
            p.setStrokeWidth(w * (0.05f - i * 0.004f));
            p.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom, cw[0], cw[1], Shader.TileMode.CLAMP));
            p.setAlpha(255 - i * 22);
            c.drawRoundRect(rf, rh / 2f, rh / 2f, p);
        }
        c.restore();
        grain(c, w, h, r, 0.03f);
    }

    // Fiore di petali traslucidi su fondo scuro
    private static void flower(Canvas c, int w, int h, Random r) {
        int[] hues = {0xFF9B6BFF, 0xFFFF8A3D, 0xFF5FD38D, 0xFF4F8DFF};
        int col = hues[r.nextInt(hues.length)];
        c.drawColor(0xFF0B0B0C);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = w / 2f, cy = h * (0.42f + r.nextFloat() * 0.12f);
        int petals = 6 + r.nextInt(4);
        float len = w * (0.32f + r.nextFloat() * 0.1f);
        android.graphics.RectF rf = new android.graphics.RectF();
        for (int i = 0; i < petals; i++) {
            c.save();
            c.rotate(360f * i / petals + r.nextFloat() * 6f, cx, cy);
            rf.set(cx - len * 0.28f, cy - len * 1.05f, cx + len * 0.28f, cy);
            p.setShader(new LinearGradient(cx, cy, cx, cy - len, (col & 0x00FFFFFF) | 0x20000000,
                    (col & 0x00FFFFFF) | 0xB0000000, Shader.TileMode.CLAMP));
            c.drawOval(rf, p);
            c.restore();
        }
        p.setShader(new RadialGradient(cx, cy, len * 0.35f, new int[]{0xFFFFFFFF, (col & 0x00FFFFFF) | 0x80000000, 0},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, len * 0.35f, p);
        p.setShader(null);
        grain(c, w, h, r, 0.05f);
    }





    // Griglia regolare di puntini che sfuma, con un solo punto rosso
    private static void grid(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pitch = w / 18f;
        int cols = (int) (w / pitch), rows = (int) (h / pitch);
        int rx = r.nextInt(cols), ry = rows / 4 + r.nextInt(rows / 2);
        float cx = w * 0.5f, cy = h * (0.25f + r.nextFloat() * 0.4f), maxD = (float) Math.hypot(w, h) * 0.75f;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float px = (x + 0.5f) * pitch + (w - cols * pitch) / 2f, py = (y + 0.5f) * pitch;
                if (x == rx && y == ry) {
                    p.setColor(RED);
                    c.drawCircle(px, py, pitch * 0.16f, p);
                    continue;
                }
                float v = 1f - Math.min(1f, (float) Math.hypot(px - cx, py - cy) / maxD);
                p.setColor(Color.argb((int) (30 + 140 * v * v), 255, 255, 255));
                c.drawCircle(px, py, pitch * 0.08f, p);
            }
        }
    }

    // Sabbia: sfumatura calda chiara con luci morbide e grana fitta
    private static void sand(Canvas c, int w, int h, Random r) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h, 0xFFEDE8E0, 0xFFC4BCB1, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 3; i++) {
            float cx = w * r.nextFloat(), cy = h * r.nextFloat(), rad = w * (0.5f + r.nextFloat() * 0.5f);
            boolean light = i % 2 == 0;
            p.setShader(new RadialGradient(cx, cy, rad, light ? 0x55FFFFFF : 0x30604A3A, 0, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
        }
        p.setShader(null);
        grain(c, w, h, r, 0.12f);
    }


    // ---------- composizioni ispirate a Nothing OS (riviste) ----------

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
                (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static void blob(Canvas c, float cx, float cy, float rad, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, rad, new int[]{color, (color & 0x00FFFFFF) | 0x55000000, 0},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad, p);
    }

    private static void cross(Canvas c, float x, float y, float s, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeWidth(Math.max(1f, s / 40f));
        p.setColor(color);
        c.drawLine(x - s / 2f, y, x + s / 2f, y, p);
        c.drawLine(x, y - s / 2f, x, y + s / 2f, p);
    }

    /** Puntini sparsi dentro la forma già ritagliata: densità che cala lungo y (effetto grana). */
    private static void grainFill(Canvas c, float l, float t, float rr, float b, Random r, int color,
                                  float density, boolean fadeDown) {
        Paint p = new Paint();
        p.setColor(color);
        int n = (int) ((rr - l) * (b - t) * density);
        float[] pts = new float[Math.min(n, 400000) * 2];
        int k = 0;
        for (int i = 0; i < pts.length / 2; i++) {
            float y = t + r.nextFloat() * (b - t);
            float f = (y - t) / Math.max(1f, b - t);
            if (r.nextFloat() > (fadeDown ? 1f - f : f)) continue;
            pts[k++] = l + r.nextFloat() * (rr - l);
            pts[k++] = y;
        }
        p.setStrokeWidth(1.6f);
        c.drawPoints(pts, 0, k, p);
    }

    // Geometrie: sfumatura, cerchi traslucidi in colonna, rettangoli, mirini e piccoli segni colorati
    private static void geometry(Canvas c, int w, int h, Random r) {
        boolean warm = r.nextBoolean();
        Paint bg = new Paint();
        int[] cols = warm ? new int[]{0xFFEADFD0, 0xFFC9774A, 0xFF6E2A24, 0xFF1D2236}
                : new int[]{0xFF9097A0, 0xFF4C5869, 0xFF15233F, 0xFF060A16};
        bg.setShader(new LinearGradient(0, 0, 0, h, cols, new float[]{0f, 0.38f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        int accent = warm ? 0xFFD9683A : 0xFF2F63D8;
        blob(c, w * (0.2f + r.nextFloat() * 0.6f), h * (0.45f + r.nextFloat() * 0.3f), w * 0.8f,
                (accent & 0x00FFFFFF) | 0x70000000);
        blob(c, w * r.nextFloat(), h * (0.1f + r.nextFloat() * 0.3f), w * 0.5f, warm ? 0x50FFE8D0 : 0x40C8D8EA);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean left = r.nextBoolean();
        float R = w * 0.3f;
        float cx = left ? w * 0.34f : w * 0.64f;
        float top = h * (0.12f + r.nextFloat() * 0.08f);
        for (int i = 0; i < 3; i++) {
            float cy = top + R + i * R * 2f;
            p.setShader(new LinearGradient(0, cy - R, 0, cy + R, 0x40FFFFFF, 0x0CFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, R, p);
            cross(c, cx, cy, w * 0.06f, 0x90FFFFFF);
        }
        p.setShader(null);
        // rettangolo traslucido che si sovrappone ai cerchi
        float rx = left ? cx : 0, ry = top + R * 3.1f;
        p.setShader(new LinearGradient(rx, ry, rx + w * 0.66f, ry, (accent & 0x00FFFFFF) | 0x66000000,
                0x22FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(rx, ry, rx + w * 0.66f, ry + R * 2f, p);
        p.setShader(null);
        // cerchio pieno che esce dal bordo
        p.setColor(warm ? 0xD9802E1E : 0xD9061A8C);
        c.drawCircle(left ? w * 0.95f : w * 0.05f, h * 0.86f, w * 0.34f, p);
        // quadrato traslucido in alto
        p.setColor(0x26FFFFFF);
        float qx = left ? w * 0.62f : w * 0.06f;
        c.drawRect(qx, 0, qx + w * 0.34f, h * 0.13f, p);
        // segni: barretta rossa, quadratini, colonna di puntini
        p.setColor(0xFFC8553D);
        float by = h * (0.38f + r.nextFloat() * 0.1f);
        c.drawRect(0, by, w * 0.1f, by + h * 0.012f, p);
        p.setColor(accent);
        c.drawRect(w * 0.48f, by, w * 0.5f, by + w * 0.02f, p);
        p.setColor(0x55FFFFFF);
        c.drawRect(w * 0.64f, by + w * 0.004f, w * 0.72f, by + w * 0.016f, p);
        float dx = left ? w * 0.92f : w * 0.08f;
        for (int i = 0; i < 12; i++) {
            float y = h * 0.62f + i * h * 0.026f;
            c.drawCircle(dx + (r.nextFloat() - 0.5f) * w * 0.02f, y, w * 0.007f, p);
        }
        grain(c, w, h, r, 0.09f);
    }

    // Luci glyph: pannelli scuri, archi concentrici e linee al neon
    private static void glyphLights(Canvas c, int w, int h, Random r) {
        boolean white = r.nextFloat() < 0.3f;
        int neon = white ? 0xFFF4F4F4 : 0xFFFF1A1A;
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // pannelli arrotondati con fessure nere
        float gap = w * 0.025f, rad = w * 0.06f;
        android.graphics.RectF[] panels = {
                new android.graphics.RectF(-rad, -rad, w * 0.5f - gap / 2, h * 0.22f),
                new android.graphics.RectF(w * 0.5f + gap / 2, -rad, w + rad, h * 0.22f),
                new android.graphics.RectF(-rad, h * 0.22f + gap, w * 0.5f, h * 0.52f),
                new android.graphics.RectF(w * 0.58f, h * 0.23f + gap, w + rad, h * 0.42f),
                new android.graphics.RectF(w * 0.58f, h * 0.42f + gap, w + rad, h + rad),
                new android.graphics.RectF(-rad, h * 0.66f, w * 0.58f - gap, h * 0.86f),
                new android.graphics.RectF(-rad, h * 0.86f + gap, w * 0.58f - gap, h + rad)};
        for (int i = 0; i < panels.length; i++) {
            android.graphics.RectF pr = panels[i];
            int base = white ? 0xFF121212 : 0xFF1A0909;
            int glowC = white ? 0x40FFFFFF : 0x80A00000;
            float gx = r.nextBoolean() ? pr.left : pr.right, gy = r.nextBoolean() ? pr.top : pr.bottom;
            p.setShader(new RadialGradient(gx, gy, Math.max(pr.width(), pr.height()) * 0.9f,
                    new int[]{lerp(base, glowC | 0xFF000000, 0.35f), base}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(pr, rad, rad, p);
        }
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        float cx = w * 0.5f, cy = h * 0.11f;
        int arcs = 7;
        Path arcsPath = new Path();
        for (int i = 0; i < arcs; i++) {
            float R = w * (0.2f + i * 0.026f);
            android.graphics.RectF rf = new android.graphics.RectF(cx - R, cy - R, cx + R, cy + R);
            arcsPath.moveTo(cx - R, h * 0.22f);
            arcsPath.lineTo(cx - R, cy);
            arcsPath.arcTo(rf, 180, 180, false);
            arcsPath.lineTo(cx + R, h * 0.22f);
        }
        neon(c, p, arcsPath, w, neon, 1f);
        // riflesso sfocato degli archi nel pannello sotto
        c.save();
        c.clipRect(0, h * 0.22f + gap, w * 0.5f, h * 0.52f);
        c.translate(0, h * 0.13f);
        neon(c, p, arcsPath, w, neon, 0.35f);
        c.restore();
        // linee verticali con curve morbide
        Path lines = new Path();
        float x1 = w * 0.69f, x2 = w * 0.89f;
        lines.moveTo(x1, h * 0.23f);
        lines.lineTo(x1, h * 0.47f);
        lines.moveTo(x2, h * 0.23f);
        lines.lineTo(x2, h * 0.47f);
        float sx = w * 0.12f;
        lines.moveTo(sx, h * 0.52f);
        lines.lineTo(sx, h * 0.58f);
        lines.cubicTo(sx, h * 0.62f, w * 0.28f, h * 0.6f, w * 0.28f, h * 0.66f);
        lines.lineTo(w * 0.28f, h);
        lines.moveTo(w * 0.59f, h * 0.71f);
        lines.cubicTo(w * 0.5f, h * 0.76f, w * 0.48f, h * 0.8f, w * 0.48f, h * 0.84f);
        lines.lineTo(w * 0.48f, h);
        neon(c, p, lines, w, neon, 1f);
        Path faint = new Path();
        faint.moveTo(x1, h * 0.48f);
        faint.cubicTo(x1, h * 0.6f, w * 0.62f, h * 0.66f, w * 0.62f, h * 0.76f);
        faint.lineTo(w * 0.62f, h);
        faint.moveTo(x2, h * 0.48f);
        faint.cubicTo(x2, h * 0.6f, w * 0.78f, h * 0.66f, w * 0.78f, h * 0.76f);
        faint.lineTo(w * 0.78f, h);
        neon(c, p, faint, w, neon, 0.45f);
        p.setStyle(Paint.Style.FILL);
        p.setMaskFilter(null);
    }

    private static void neon(Canvas c, Paint p, Path path, int w, int color, float strength) {
        p.setStyle(Paint.Style.STROKE);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(w / 60f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        p.setStrokeWidth(w / 90f);
        p.setColor((color & 0x00FFFFFF) | ((int) (150 * strength) << 24));
        c.drawPath(path, p);
        p.setMaskFilter(strength < 0.5f ? new android.graphics.BlurMaskFilter(w / 300f,
                android.graphics.BlurMaskFilter.Blur.NORMAL) : null);
        p.setStrokeWidth(w / 330f);
        p.setColor((color & 0x00FFFFFF) | ((int) (255 * strength) << 24));
        c.drawPath(path, p);
        p.setMaskFilter(null);
    }

    // Matrice: griglia di punti spenti e un cerchio di punti accesi con un disegno
    private static final String[][] PIXEL_ART = {
            {".....#####.....", "...##.....##...", "..#.........#..", ".#...........#.", ".#..##...##..#.",
                    "#...##...##...#", "#.............#", "#.............#", "#..#.......#..#", ".#..#.....#..#.",
                    ".#...#####...#.", "..#.........#..", "...##.....##...", ".....#####.....", "..............."},
            {"...............", "..###.....###..", ".#####...#####.", "###############", "###############",
                    "###############", ".#############.", "..###########..", "...#########...", "....#######....",
                    ".....#####.....", "......###......", ".......#.......", "...............", "..............."},
            {".....#####.....", "...####........", "..###..........", ".###...........", ".###...........",
                    "###............", "###............", "###............", "###...........#", ".###.........##",
                    ".####.......###", "..#####...####.", "...#########...", ".....#####.....", "..............."},
            {".......#.......", ".......#.......", "......###......", "......###......", ".....#####.....",
                    "...#########...", "###############", "...#########...", ".....#####.....", "......###......",
                    "......###......", ".......#.......", ".......#.......", "...............", "..............."}
    };

    private static void matrix(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pitch = w / 27f;
        // griglia di fondo appena visibile, più chiara verso il centro
        float cxs = w / 2f, cys = h * 0.42f;
        for (float y = pitch / 2; y < h; y += pitch) {
            for (float x = pitch / 2; x < w; x += pitch) {
                float d = (float) Math.hypot(x - cxs, y - cys) / h;
                p.setColor(Color.argb((int) Math.max(10, 46 - 70 * d), 255, 255, 255));
                c.drawCircle(x, y, pitch * 0.12f, p);
            }
        }
        String[] art = PIXEL_ART[r.nextInt(PIXEL_ART.length)];
        int n = 21;
        float big = w * 0.8f / n;
        float x0 = cxs - big * n / 2f, y0 = cys - big * n / 2f;
        float rc = n / 2f;
        int off = (n - 15) / 2;
        android.graphics.BlurMaskFilter glow = new android.graphics.BlurMaskFilter(big * 0.5f,
                android.graphics.BlurMaskFilter.Blur.NORMAL);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float dx = x + 0.5f - rc, dy = y + 0.5f - rc;
                    if (dx * dx + dy * dy > rc * rc) continue;
                    int ax = x - off, ay = y - off;
                    boolean lit = ax >= 0 && ay >= 0 && ax < 15 && ay < 15 && art[ay].charAt(ax) == '#';
                    float px = x0 + x * big + big / 2f, py = y0 + y * big + big / 2f;
                    if (pass == 0) {
                        if (!lit) continue;
                        p.setMaskFilter(glow);
                        p.setColor(0x66FFFFFF);
                        c.drawCircle(px, py, big * 0.55f, p);
                    } else {
                        p.setMaskFilter(null);
                        p.setColor(lit ? 0xFFF5F5F5 : 0xFF1E1E1E);
                        c.drawCircle(px, py, big * 0.38f, p);
                    }
                }
            }
        }
        p.setMaskFilter(null);
        p.setColor(RED);
        c.drawCircle(cxs, y0 + big * n + big * 2f, big * 0.38f, p);
    }

    // Tubi morbidi in 3D: bande diagonali o anelli annidati, in tre combinazioni di colore
    private static void tubes(Canvas c, int w, int h, Random r) {
        int scheme = r.nextInt(3);
        int bgA, bgB, dark, light;
        if (scheme == 0) { bgA = 0xFF1B2F63; bgB = 0xFF050B1F; dark = 0xFF14244F; light = 0xFFD5E2F4; }
        else if (scheme == 1) { bgA = 0xFF8F939B; bgB = 0xFF262A33; dark = 0xFF23272F; light = 0xFFD4D6DA; }
        else { bgA = 0xFFB8CCB4; bgB = 0xFF3F5049; dark = 0xFF2E3A35; light = 0xFFC9DCC4; }
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w * 0.3f, h, bgA, bgB, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (r.nextBoolean()) {
            // bande diagonali larghe con una zona forata
            perforated(c, w, h, r, light);
            for (int i = 0; i < 2; i++) {
                Path path = new Path();
                float y = h * (0.15f + i * 0.5f + r.nextFloat() * 0.1f);
                path.moveTo(-w * 0.3f, y + h * 0.25f);
                path.cubicTo(w * 0.3f, y + h * 0.05f, w * 0.6f, y - h * 0.05f, w * 1.3f, y - h * 0.3f);
                tube(c, p, path, w * 0.38f, dark, light, w);
            }
        } else {
            // anelli allungati annidati che escono dal bordo
            float cx = w * (r.nextBoolean() ? 0.1f : 0.9f), cy = h * (0.3f + r.nextFloat() * 0.3f);
            for (int i = 3; i >= 0; i--) {
                float rw = w * (0.45f + i * 0.28f), rh = h * (0.09f + i * 0.08f);
                Path path = new Path();
                path.addRoundRect(new android.graphics.RectF(cx - rw, cy - rh, cx + rw, cy + rh), rh, rh,
                        Path.Direction.CW);
                tube(c, p, path, w * 0.13f, dark, light, w);
            }
        }
        grain(c, w, h, r, 0.02f);
    }

    /** Tubo: ombra sfocata, poi strisce sempre più strette e chiare verso il centro (effetto cilindro). */
    private static void tube(Canvas c, Paint p, Path path, float width, int dark, int light, int w) {
        p.setShader(null);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(width * 0.35f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        p.setStrokeWidth(width * 1.1f);
        p.setColor(0x99000000);
        c.save();
        c.translate(width * 0.08f, width * 0.15f);
        c.drawPath(path, p);
        c.restore();
        p.setMaskFilter(null);
        int steps = 22;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            p.setStrokeWidth(width * (1f - t * 0.92f));
            // bordo scuro, poi luce che cresce in modo morbido verso il centro
            p.setColor(lerp(dark, light, (float) Math.sin(t * Math.PI / 2)));
            c.drawPath(path, p);
        }
    }

    private static void perforated(Canvas c, int w, int h, Random r, int light) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pitch = w / 40f;
        float x0 = w * 0.35f, y0 = h * 0.6f;
        int row = 0;
        for (float y = y0; y < h; y += pitch * 0.87f, row++) {
            for (float x = x0 + (row % 2) * pitch / 2f; x < w; x += pitch) {
                float f = Math.min(1f, (x - x0) / (w - x0) + (y - y0) / (h - y0) * 0.5f);
                p.setColor(lerp(0x00FFFFFF, (light & 0x00FFFFFF) | 0x99000000, f));
                c.drawCircle(x, y, pitch * 0.32f, p);
            }
        }
    }

    // Pillole verticali rigate con ombra morbida
    private static void pills(Canvas c, int w, int h, Random r) {
        int[][] schemes = {{0xFFC5D9C2, 0xFF6F8A78, 0xFF2D3A34}, {0xFFE6DCCB, 0xFF9C8A70, 0xFF3D342A},
                {0xFFC9CFD8, 0xFF6C7686, 0xFF262C36}};
        int[] s = schemes[r.nextInt(schemes.length)];
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{s[0], lerp(s[0], s[1], 0.4f), s[2]},
                new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pw = w * 0.27f, top = h * 0.01f, bottom = h * (0.68f + r.nextFloat() * 0.06f);
        float[] xs = {w * 0.0f, w * 0.55f};
        for (float x : xs) {
            android.graphics.RectF rf = new android.graphics.RectF(x, top, x + pw, bottom);
            // alone chiaro intorno
            p.setMaskFilter(new android.graphics.BlurMaskFilter(w * 0.06f, android.graphics.BlurMaskFilter.Blur.OUTER));
            p.setColor(lerp(s[0], 0xFFFFFFFF, 0.3f));
            c.drawRoundRect(rf, pw / 2f, pw / 2f, p);
            p.setMaskFilter(null);
            p.setShader(new LinearGradient(0, top, 0, bottom, lerp(s[2], s[1], 0.2f), lerp(s[2], s[1], 0.5f),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(rf, pw / 2f, pw / 2f, p);
            p.setShader(null);
            c.save();
            Path clip = new Path();
            clip.addRoundRect(rf, pw / 2f, pw / 2f, Path.Direction.CW);
            c.clipPath(clip);
            p.setStrokeWidth(Math.max(1f, w / 500f));
            for (float lx = x; lx < x + pw; lx += w / 180f) {
                p.setColor(((int) ((lx - x) / (w / 180f)) % 2 == 0) ? 0x1EFFFFFF : 0x22000000);
                c.drawLine(lx, top, lx, bottom, p);
            }
            // ombra interna in alto
            p.setShader(new LinearGradient(0, top, 0, top + pw, 0x66000000, 0, Shader.TileMode.CLAMP));
            c.drawRect(rf, p);
            p.setShader(null);
            c.restore();
        }
        grain(c, w, h, r, 0.02f);
    }

    // Rami: dischi traslucidi sovrapposti e rametti scuri, su grigio chiaro
    private static void branches(Canvas c, int w, int h, Random r) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h, 0xFFF1F2F3, 0xFFC9CBCE, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(w / 120f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        for (int i = 0; i < 14; i++) {
            float cx = w * r.nextFloat(), cy = h * r.nextFloat(), rad = w * (0.2f + r.nextFloat() * 0.18f);
            p.setShader(new RadialGradient(cx - rad * 0.3f, cy - rad * 0.3f, rad * 1.4f,
                    0x60FFFFFF, 0x30A8ACB2, Shader.TileMode.CLAMP));
            c.drawOval(cx - rad, cy - rad * 0.85f, cx + rad, cy + rad * 0.85f, p);
        }
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(w / 700f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        p.setColor(0xFF15171A);
        branch(c, p, w * 0.48f, h * 1.02f, -90f, h * 0.28f, w / 110f, 0, r);
        p.setMaskFilter(null);
        p.setStyle(Paint.Style.FILL);
    }

    private static void branch(Canvas c, Paint p, float x, float y, float ang, float len, float width, int depth,
                               Random r) {
        if (depth > 5 || len < 8) return;
        double a = Math.toRadians(ang);
        float bend = (r.nextFloat() - 0.5f) * len * 0.25f;
        float x2 = x + (float) Math.cos(a) * len, y2 = y + (float) Math.sin(a) * len;
        Path path = new Path();
        path.moveTo(x, y);
        path.quadTo((x + x2) / 2f + bend, (y + y2) / 2f, x2, y2);
        p.setStrokeWidth(Math.max(1.2f, width));
        c.drawPath(path, p);
        // piccole gemme a stella lungo il ramo
        if (depth >= 2) {
            for (int i = 0; i < 2; i++) {
                float t = 0.4f + r.nextFloat() * 0.5f;
                float bx = x + (x2 - x) * t, by = y + (y2 - y) * t;
                for (int k = 0; k < 4; k++) {
                    double ka = r.nextDouble() * Math.PI * 2;
                    float kl = len * 0.04f;
                    c.drawLine(bx, by, bx + (float) Math.cos(ka) * kl, by + (float) Math.sin(ka) * kl, p);
                }
            }
        }
        int kids = depth == 0 ? 3 : 2;
        for (int i = 0; i < kids; i++) {
            float na = ang + (i - (kids - 1) / 2f) * (28f + r.nextFloat() * 18f) + (r.nextFloat() - 0.5f) * 12f;
            branch(c, p, x2, y2, na, len * (0.72f + r.nextFloat() * 0.12f), width * 0.68f, depth + 1, r);
        }
    }

    // Monocromo: forme grigie in colonna su nero, con grana e mirini
    private static void mono(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF141414);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = w * 0.5f;
        // rettangolo di grana luminosa in alto
        c.save();
        c.clipRect(w * 0.22f, h * 0.13f, w * 0.76f, h * 0.255f);
        grainFill(c, w * 0.22f, h * 0.13f, w * 0.76f, h * 0.255f, r, 0x90FFFFFF, 0.35f, false);
        c.restore();
        // cerchio pieno grigio
        float R = w * 0.21f, cy = h * 0.35f;
        p.setColor(0xFF666666);
        c.drawCircle(cx - w * 0.01f, cy, R, p);
        // quarto di forma con grana che sfuma
        c.save();
        Path q = new Path();
        q.addRect(cx, cy, cx + R * 1.5f, cy + R * 1.2f, Path.Direction.CW);
        c.clipPath(q);
        grainFill(c, cx, cy, cx + R * 1.5f, cy + R * 1.2f, r, 0xAAFFFFFF, 0.3f, true);
        c.restore();
        // pillola verticale con grana che svanisce in basso
        c.save();
        Path pill = new Path();
        pill.addRoundRect(new android.graphics.RectF(w * 0.31f, h * 0.46f, w * 0.69f, h * 0.82f), w * 0.19f, w * 0.19f,
                Path.Direction.CW);
        c.clipPath(pill);
        grainFill(c, w * 0.31f, h * 0.46f, w * 0.69f, h * 0.82f, r, 0x55FFFFFF, 0.22f, true);
        c.restore();
        // cerchio scuro sfumato
        p.setShader(new RadialGradient(w * 0.42f, h * 0.6f, w * 0.17f, 0xFF2A2A2A, 0xFF161616, Shader.TileMode.CLAMP));
        c.drawCircle(w * 0.4f, h * 0.62f, w * 0.15f, p);
        p.setShader(null);
        cross(c, cx, h * 0.145f, w * 0.18f, 0x99FFFFFF);
        cross(c, cx, h * 0.45f, w * 0.18f, 0x99FFFFFF);
        cross(c, cx, h * 0.63f, w * 0.18f, 0x99FFFFFF);
    }

    /** Foto "a strisce": la foto vista attraverso un vetro rigato (strisce verticali sfalsate). */
    static Bitmap ribbed(Bitmap src, int w, int h, long seed) {
        Random r = new Random(seed);
        Bitmap base = cover(src, w, h);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        int strips = 10 + r.nextInt(5);
        float sw = w / (float) strips;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.Rect s = new android.graphics.Rect();
        android.graphics.RectF d = new android.graphics.RectF();
        for (int i = 0; i < strips; i++) {
            float shift = (float) Math.sin(i * 0.9 + r.nextFloat()) * h * 0.06f + i * h * 0.008f;
            float srcX = i * sw - sw * 0.25f * (float) Math.sin(i * 1.7);
            s.set(Math.round(Math.max(0, srcX)), 0, Math.round(Math.min(w, srcX + sw)), h);
            d.set(i * sw, shift, i * sw + sw, h + shift);
            c.drawBitmap(base, s, d, p);
            if (shift > 0) {
                s.set(s.left, h - Math.round(shift), s.right, h);
                d.set(i * sw, 0, i * sw + sw, shift);
                c.drawBitmap(base, s, d, p);
            }
        }
        // luce e ombra di ogni striscia (vetro rigato)
        Paint g = new Paint();
        for (int i = 0; i < strips; i++) {
            g.setShader(new LinearGradient(i * sw, 0, i * sw + sw, 0,
                    new int[]{0x30000000, 0x00000000, 0x18FFFFFF, 0x40000000},
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(i * sw, 0, i * sw + sw, h, g);
        }
        return out;
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
