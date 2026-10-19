package it.rex.dotlauncher;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Il dock: una o due righe di icone, ognuna centrata. Resta un LinearLayout (le icone sono figli diretti),
 * così trascinamento, gesti sulle icone e menu funzionano come prima.
 */
class DockLayout extends LinearLayout {
    /** Righe del dock (1 o 2) e icone al massimo per riga. */
    int rows = 1, perRow = 5;

    DockLayout(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
    }

    private int cols() {
        int n = getChildCount();
        if (rows <= 1 || n <= perRow) return Math.max(1, n);
        return Math.max(1, Math.min(perRow, (n + rows - 1) / rows));
    }

    private boolean multi() {
        return rows > 1 && getChildCount() > cols();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (!multi()) {
            super.onMeasure(wSpec, hSpec);
            return;
        }
        int w = MeasureSpec.getSize(wSpec);
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            c.measure(MeasureSpec.makeMeasureSpec(Math.max(0, lp.width), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, lp.height), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, MeasureSpec.getSize(hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!multi()) {
            super.onLayout(changed, l, t, r, b);
            return;
        }
        int n = getChildCount(), cols = cols(), w = r - l, h = b - t;
        int rowsUsed = (n + cols - 1) / cols;
        int rowH = h / Math.max(1, rowsUsed);
        for (int row = 0; row < rowsUsed; row++) {
            int from = row * cols, to = Math.min(n, from + cols);
            int total = 0;
            for (int i = from; i < to; i++) {
                View c = getChildAt(i);
                LayoutParams lp = (LayoutParams) c.getLayoutParams();
                total += c.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            }
            int x = (w - total) / 2;
            for (int i = from; i < to; i++) {
                View c = getChildAt(i);
                LayoutParams lp = (LayoutParams) c.getLayoutParams();
                x += lp.leftMargin;
                int y = row * rowH + (rowH - c.getMeasuredHeight()) / 2;
                c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
                x += c.getMeasuredWidth() + lp.rightMargin;
            }
        }
    }

    /** Posizione in cui cadrebbe un'icona trascinata nel punto (rawX, rawY) dello schermo. */
    int dropIndex(float rawX, float rawY, View skip) {
        int idx = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (!(c instanceof android.widget.ImageView) || c == skip) continue;
            int[] cl = new int[2];
            c.getLocationOnScreen(cl);
            if (!multi()) {
                // una riga sola: conta solo la posizione orizzontale (come prima)
                if (cl[0] + c.getWidth() / 2f < rawX) idx++;
                continue;
            }
            boolean above = rawY > cl[1] + c.getHeight();                       // riga sopra il dito
            boolean sameRow = rawY >= cl[1] - c.getHeight() * 0.2f && rawY <= cl[1] + c.getHeight() * 1.2f;
            if (above || (sameRow && cl[0] + c.getWidth() / 2f < rawX)) idx++;
        }
        return idx;
    }
}
