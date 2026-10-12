package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.View;

/** Un'app sulla home: icona (1x1 o grande 2x2) con nome facoltativo. */
class AppTile extends View {
    /** Dimensione delle icone scelta dall'utente (1 = normale). */
    static float SCALE = 1f;

    interface Source {
        Bitmap iconFor(String key, int size);
        String labelFor(String key);

        /** Numero di notifiche dell'app (0 = nessun pallino). */
        int badge(String key);

        /** App in pausa (focus): icona attenuata. */
        boolean dimmed(String key);
    }

    final Item item;
    private final Source src;
    private final Theme th;
    private final boolean labels;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rf = new RectF();
    private String cacheLabel, cacheShort = "";
    private float cacheW;

    @Override
    public CharSequence getContentDescription() {
        String l = src.labelFor(item.data);
        int n = src.badge(item.data);
        return n > 0 ? l + ", " + n + T.n(n, " notifica", " notifiche") : l;
    }

    AppTile(Context c, Item item, Source src, Theme th, boolean labels) {
        super(c);
        this.item = item;
        this.src = src;
        this.th = th;
        this.labels = labels;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float W = getWidth(), H = getHeight();
        float m = Math.min(W, H);
        boolean showLabel = labels && item.w == 1;
        float size = m * Math.min(0.98f, (showLabel ? 0.68f : (item.w == 1 ? 0.86f : 0.9f)) * SCALE);
        float cx = W / 2f;
        float cy = showLabel ? H * 0.42f : H / 2f;
        Bitmap b = src.iconFor(item.data, Math.round(size));
        if (b != null) {
            rf.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            boolean dim = src.dimmed(item.data);
            if (dim) p.setAlpha(90);
            cv.drawBitmap(b, null, rf, p);
            p.setAlpha(255);
            if (!dim && src.badge(item.data) > 0) BadgeDrawable.draw(cv, p, cx, cy, size, th.accent, th.bg);
        } else {
            p.setColor(th.tile);
            cv.drawCircle(cx, cy, size / 2f, p);
        }
        if (showLabel) {
            String l = src.labelFor(item.data);
            if (l == null) l = "";
            p.setTypeface(th.bodyFace);
            p.setTextSize(m * 0.13f);
            p.setColor(th.wall ? 0xFFFFFFFF : th.onTile);
            if (th.wall) p.setShadowLayer(m * 0.03f, 0, 0, 0x99000000);
            // il nome accorciato si calcola una volta sola per nome e larghezza
            if (!l.equals(cacheLabel) || W != cacheW) {
                cacheLabel = l;
                cacheW = W;
                cacheShort = TextUtils.ellipsize(l, new android.text.TextPaint(p), W * 0.95f,
                        TextUtils.TruncateAt.END).toString();
            }
            String s = cacheShort;
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText(s, cx, H * 0.93f, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.clearShadowLayer();
        }
    }
}
