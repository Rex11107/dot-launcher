package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Riquadro con le proporzioni dello schermo in cui si sistema un'immagine prima di farne uno sfondo:
 * un dito la sposta, due dita la ingrandiscono, doppio tocco torna alla misura di partenza.
 * "Riempi" copre tutto lo schermo (si taglia un po' dei bordi), "Adatta" la mostra intera con un colore
 * di sfondo dove non arriva. Lo sfondo finale si ottiene con {@link #render(int, int)} alla risoluzione dello schermo.
 */
final class CropView extends View {
    private final Bitmap bmp;
    private final Matrix m = new Matrix();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaler;
    private final GestureDetector taps;
    private float scale, minScale, tx, ty;
    private boolean fill = true, ready;
    private int bg = 0xFF000000;

    CropView(Context c, Bitmap bmp) {
        super(c);
        this.bmp = bmp;
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float ns = Math.max(minScale, Math.min(minScale * 6f, scale * d.getScaleFactor()));
                float f = ns / scale;
                // lo zoom resta centrato tra le due dita
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = ns;
                clamp();
                invalidate();
                return true;
            }
        });
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx -= dx;
                ty -= dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                reset();
                return true;
            }
        });
    }

    /** true: copre tutto lo schermo; false: immagine intera con il colore di sfondo attorno. */
    void setFill(boolean f) {
        fill = f;
        reset();
    }

    boolean fill() {
        return fill;
    }

    void setBackgroundFill(int color) {
        bg = color | 0xFF000000;
        invalidate();
    }

    /**
     * Colore dei bordi dell'immagine, lo sfondo "automatico" per la modalità Adatta. Si legge appena
     * dentro il bordo (le immagini scaricate hanno spesso una cornice sottile) e si prende il valore mediano,
     * così una linea o un dettaglio scuro non sporca il colore.
     */
    static int edgeColor(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int ix = Math.max(1, w * 3 / 100), iy = Math.max(1, h * 3 / 100);
        int n = 0;
        int[] r = new int[160], g = new int[160], bl = new int[160];
        for (int i = 0; i < 40; i++) {
            int x = ix + i * (w - 2 * ix) / 40, y = iy + i * (h - 2 * iy) / 40;
            int[] px = {b.getPixel(x, iy), b.getPixel(x, h - 1 - iy), b.getPixel(ix, y), b.getPixel(w - 1 - ix, y)};
            for (int c : px) {
                r[n] = (c >> 16) & 255;
                g[n] = (c >> 8) & 255;
                bl[n] = c & 255;
                n++;
            }
        }
        java.util.Arrays.sort(r);
        java.util.Arrays.sort(g);
        java.util.Arrays.sort(bl);
        return 0xFF000000 | (r[n / 2] << 16) | (g[n / 2] << 8) | bl[n / 2];
    }

    private void reset() {
        if (getWidth() == 0) return;
        float sx = getWidth() / (float) bmp.getWidth(), sy = getHeight() / (float) bmp.getHeight();
        minScale = fill ? Math.max(sx, sy) : Math.min(sx, sy);
        scale = minScale;
        tx = (getWidth() - bmp.getWidth() * scale) / 2f;
        ty = (getHeight() - bmp.getHeight() * scale) / 2f;
        ready = true;
        invalidate();
    }

    /** Se l'immagine è più grande del riquadro non si scopre il bordo; se è più piccola resta centrata. */
    private void clamp() {
        float w = bmp.getWidth() * scale, h = bmp.getHeight() * scale;
        tx = w >= getWidth() ? Math.max(getWidth() - w, Math.min(0, tx)) : (getWidth() - w) / 2f;
        ty = h >= getHeight() ? Math.max(getHeight() - h, Math.min(0, ty)) : (getHeight() - h) / 2f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        reset();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!ready) return;
        c.drawColor(bg);
        m.setScale(scale, scale);
        m.postTranslate(tx, ty);
        c.drawBitmap(bmp, m, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        getParent().requestDisallowInterceptTouchEvent(true);
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) taps.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** L'immagine come la si vede nel riquadro, alla risoluzione indicata (quella dello schermo). */
    Bitmap render(int outW, int outH) {
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(bg);
        float k = outW / (float) Math.max(1, getWidth());
        Matrix mm = new Matrix();
        mm.setScale(scale * k, scale * k);
        mm.postTranslate(tx * k, ty * k);
        c.drawBitmap(bmp, mm, paint);
        return out;
    }
}
