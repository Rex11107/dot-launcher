package it.rex.dotlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/** Crea le icone: monocromatiche (stile Nothing), in bianco e nero o a colori. */
final class IconFactory {
    static final String MONO = "mono";
    static final String GRAY = "gray";
    static final String COLOR = "color";

    static Bitmap make(Drawable d, String style, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (d == null) return out;

        if (MONO.equals(style)) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0xFF1C1C1C);
            c.drawCircle(size / 2f, size / 2f, size / 2f, bg);

            Drawable mono = null;
            if (Build.VERSION.SDK_INT >= 33 && d instanceof AdaptiveIconDrawable) {
                mono = ((AdaptiveIconDrawable) d).getMonochrome();
            }
            if (mono != null) {
                // Icona tematica ufficiale dell'app: il livello monocromatico è grande 1,5x
                Drawable m = mono.mutate();
                m.setTint(Color.WHITE);
                int extra = size / 4;
                m.setBounds(-extra, -extra, size + extra, size + extra);
                m.draw(c);
            } else {
                // Nessuna icona tematica: icona originale in grigi, rimpicciolita nel cerchio
                Bitmap src = render(d, size);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                p.setColorFilter(grayFilter(1.15f));
                int inset = Math.round(size * 0.18f);
                c.drawBitmap(src, null,
                        new android.graphics.Rect(inset, inset, size - inset, size - inset), p);
            }
            return out;
        }

        Bitmap src = render(d, size);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (GRAY.equals(style)) p.setColorFilter(grayFilter(1.1f));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private static Bitmap render(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    private static ColorMatrixColorFilter grayFilter(float contrast) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0f);
        float t = (1f - contrast) * 128f;
        ColorMatrix k = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, t,
                0, contrast, 0, 0, t,
                0, 0, contrast, 0, t,
                0, 0, 0, 1, 0});
        m.postConcat(k);
        return new ColorMatrixColorFilter(m);
    }

    private IconFactory() {}
}
