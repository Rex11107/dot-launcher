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
            "Luci glyph", "Matrice", "Griglia", "Sabbia", "Tubi di vetro"
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
            default: tubes(c, w, h, r); break;
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

    // Forme squadrate grigie, simmetriche, con linee sottili
    private static void geometry(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF1E1F22);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeWidth(Math.max(1f, w / 900f));
        p.setColor(0x22FFFFFF);
        for (float y = h * 0.05f; y < h; y += h / 28f) c.drawLine(0, y, w, y, p);
        int n = 7;
        for (int i = 0; i < n; i++) {
            float bw = w * (0.12f + r.nextFloat() * 0.28f);
            float bh = h * (0.05f + r.nextFloat() * 0.16f);
            float x = w * r.nextFloat() * 0.45f;
            float y = h * (0.08f + r.nextFloat() * 0.8f);
            int g = 40 + r.nextInt(90);
            p.setColor(Color.rgb(g, g, g + 4));
            c.drawRect(x, y, x + bw, y + bh, p);
            c.drawRect(w - x - bw, y, w - x, y + bh, p); // specchio
        }
        p.setColor(RED);
        float s = w / 30f;
        c.drawRect(w / 2f - s / 2f, h * 0.7f, w / 2f + s / 2f, h * 0.7f + s, p);
        grain(c, w, h, r, 0.02f);
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

    // Strisce luminose bianche con alone, come luci sul retro di un telefono
    private static void glyphLights(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF050505);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        android.graphics.RectF rf = new android.graphics.RectF();
        float cx = w * (0.4f + r.nextFloat() * 0.2f), cy = h * (0.4f + r.nextFloat() * 0.15f);
        float R = w * 0.3f;
        rf.set(cx - R, cy - R, cx + R, cy + R);
        float start = r.nextFloat() * 360f;
        glow(c, p, w, path -> path.addArc(rf, start, 70), path -> path.addArc(rf, start + 100, 60),
                path -> path.addArc(rf, start + 190, 120),
                path -> {
                    float x = w * 0.82f;
                    path.moveTo(x, h * 0.15f);
                    path.lineTo(x, h * 0.32f);
                },
                path -> {
                    path.moveTo(w * 0.2f, h * 0.78f);
                    path.lineTo(w * 0.5f, h * 0.78f);
                });
        p.setStyle(Paint.Style.FILL);
        p.setColor(RED);
        c.drawCircle(w * 0.82f, h * 0.36f, w / 60f, p);
    }

    private interface PathMaker {
        void make(Path p);
    }

    private static void glow(Canvas c, Paint p, int w, PathMaker... makers) {
        for (PathMaker m : makers) {
            Path path = new Path();
            m.make(path);
            float[] widths = {w / 14f, w / 24f, w / 45f, w / 110f};
            int[] alphas = {14, 30, 70, 255};
            for (int i = 0; i < widths.length; i++) {
                p.setStrokeWidth(widths[i]);
                p.setColor(Color.argb(alphas[i], 255, 255, 255));
                c.drawPath(path, p);
            }
        }
    }

    // Cerchio di grandi punti (25x25) con un disegno simmetrico acceso
    private static void matrix(Canvas c, int w, int h, Random r) {
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n = 25;
        float size = w * 0.82f, pitch = size / n;
        float x0 = (w - size) / 2f, y0 = h * 0.42f - size / 2f;
        boolean[][] on = new boolean[n][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x <= n / 2; x++) {
                boolean v = r.nextFloat() < 0.32f;
                on[y][x] = v;
                on[y][n - 1 - x] = v;
            }
        }
        float rc = n / 2f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = x + 0.5f - rc, dy = y + 0.5f - rc;
                if (dx * dx + dy * dy > rc * rc) continue;
                boolean lit = on[y][x] && dx * dx + dy * dy < (rc - 2.5f) * (rc - 2.5f);
                p.setColor(lit ? 0xFFF2F2F2 : 0xFF1C1C1C);
                c.drawCircle(x0 + x * pitch + pitch / 2f, y0 + y * pitch + pitch / 2f, pitch * 0.4f, p);
            }
        }
        p.setColor(RED);
        c.drawCircle(x0 + size / 2f, y0 + size + pitch * 2.5f, pitch * 0.4f, p);
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

    // Tubi traslucidi con dettagli neri su grigio chiaro
    private static void tubes(Canvas c, int w, int h, Random r) {
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h, 0xFFD9DCE0, 0xFF9DA4AD, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        int n = 5 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            Path path = new Path();
            float x = w * r.nextFloat(), y = h * (0.05f + 0.9f * r.nextFloat());
            path.moveTo(x, y);
            path.cubicTo(x + w * (r.nextFloat() - 0.5f), y + h * 0.2f * (r.nextFloat() - 0.5f),
                    x + w * (r.nextFloat() - 0.5f), y + h * 0.3f * (r.nextFloat() - 0.5f),
                    x + w * 0.8f * (r.nextFloat() - 0.5f), y + h * 0.25f * (r.nextFloat() - 0.5f));
            float tw = w * (0.05f + r.nextFloat() * 0.05f);
            p.setStrokeWidth(tw);
            p.setColor(0x40FFFFFF);
            c.drawPath(path, p);
            p.setStrokeWidth(tw * 0.25f);
            p.setColor(0x70FFFFFF);
            c.drawPath(path, p);
            p.setStrokeWidth(tw * 0.08f);
            p.setColor(0x60000000);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF111111);
        for (int i = 0; i < 4; i++) c.drawCircle(w * r.nextFloat(), h * r.nextFloat(), w / 70f, p);
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
