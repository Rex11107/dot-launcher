package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.LruCache;
import android.view.View;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foto del widget "Foto": caricate in background, ridotte alla misura della tessera. */
final class Photos {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 10, 48L * 1024 * 1024)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    static boolean failed(String uri) {
        return FAILED.contains(uri);
    }

    /** Restituisce la foto se pronta; altrimenti la carica e poi ridisegna la vista. */
    static Bitmap get(Context c, String uri, int size, View v) {
        int bucket = Math.max(128, Integer.highestOneBit(Math.max(1, size)) * 2);
        String key = uri + "@" + bucket;
        Bitmap b = CACHE.get(key);
        if (b != null || FAILED.contains(uri)) return b;
        synchronized (LOADING) {
            if (!LOADING.add(key)) return null;
        }
        Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            Bitmap out = load(app, Uri.parse(uri), bucket);
            synchronized (LOADING) {
                LOADING.remove(key);
            }
            if (out != null) CACHE.put(key, out);
            else FAILED.add(uri);
            v.postInvalidate();
        });
        return null;
    }

    static void forget(String uri) {
        FAILED.remove(uri);
    }

    private static Bitmap load(Context c, Uri uri, int target) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, d);
            }
            if (b == null) return null;
            int rot = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
                    rot = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                            : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                            : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
                }
            } catch (Exception ignored) {
            }
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private Photos() {}
}
