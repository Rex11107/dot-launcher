package it.rex.dotlauncher;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Vetro smerigliato: quando lo sfondo viene applicato dal launcher se ne salva una copia sfocata,
 * che le tessere dello stile Nuovo disegnano sotto il loro colore semitrasparente.
 * Se lo sfondo viene cambiato da un'altra app, la copia non vale più e l'effetto si spegne da solo.
 */
final class Glass {
    private static Bitmap cached;
    private static int cachedId = Integer.MIN_VALUE;

    static Bitmap get(Context c) {
        SharedPreferences p = c.getSharedPreferences("dot", Context.MODE_PRIVATE);
        int saved = p.getInt("blurWallId", Integer.MIN_VALUE);
        if (saved == Integer.MIN_VALUE) return null;
        int now;
        try {
            now = WallpaperManager.getInstance(c).getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
            return null;
        }
        if (now != saved) return null;
        if (cached != null && cachedId == now) return cached;
        File f = new File(c.getFilesDir(), "wall_blur.png");
        if (!f.exists()) return null;
        cached = BitmapFactory.decodeFile(f.getPath());
        cachedId = now;
        return cached;
    }

    /** Da chiamare subito dopo aver applicato lo sfondo. */
    static void save(Context c, Bitmap full) {
        try {
            Bitmap small = blur(full);
            File f = new File(c.getFilesDir(), "wall_blur.png");
            try (FileOutputStream out = new FileOutputStream(f)) {
                small.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            int id = WallpaperManager.getInstance(c).getWallpaperId(WallpaperManager.FLAG_SYSTEM);
            c.getSharedPreferences("dot", Context.MODE_PRIVATE).edit().putInt("blurWallId", id).apply();
            cached = small;
            cachedId = id;
        } catch (Exception ignored) {
        }
    }

    private static Bitmap blur(Bitmap src) {
        int w = Math.max(8, src.getWidth() / 16), h = Math.max(8, src.getHeight() / 16);
        Bitmap s = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) px = boxPass(px, w, h, 2);
        s.setPixels(px, 0, w, 0, 0, w, h);
        return s;
    }

    private static int[] boxPass(int[] in, int w, int h, int r) {
        int[] out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = Math.min(h - 1, Math.max(0, y + dy));
                    for (int dx = -r; dx <= r; dx++) {
                        int xx = Math.min(w - 1, Math.max(0, x + dx));
                        int c = in[yy * w + xx];
                        rs += (c >> 16) & 0xFF;
                        gs += (c >> 8) & 0xFF;
                        bs += c & 0xFF;
                        n++;
                    }
                }
                out[y * w + x] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
            }
        }
        return out;
    }

    private Glass() {}
}
