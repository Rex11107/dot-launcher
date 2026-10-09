package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Una pagina della home: griglia di 4 colonne a celle quadrate.
 * Il trascinamento è gestito da HomeActivity; qui si disegna solo l'anteprima della posizione.
 */
class TileGrid extends ViewGroup {
    interface Host {
        boolean canPlace(Item it, int page, int col, int row, int w, int h);
        void onEmptyLongPress(int page, int col, int row);
    }

    static boolean anyDragging;
    static final int COLS = 4;

    static final int PREVIEW_NONE = 0;
    static final int PREVIEW_OK = 1;     // posto libero
    static final int PREVIEW_MERGE = 2;  // sopra un'app o una cartella: cartella
    static final int PREVIEW_BAD = 3;    // non c'è spazio

    final int page;
    private final Host host;
    private final float padH, padTop, gap;
    private float cell;
    private int rows = 6;
    private float downX, downY;

    private int pMode = PREVIEW_NONE, pCol, pRow, pW, pH;
    private int accent = 0xFFD71921, neutral = 0x66FFFFFF;
    private final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pr = new RectF();
    private final float dp;

    TileGrid(Context c, int page, Host host) {
        super(c);
        this.page = page;
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        padH = 16 * dp;
        padTop = 10 * dp;
        gap = 12 * dp;
        setClipChildren(false);
        setWillNotDraw(false);
        setClickable(true);
        setLongClickable(true);
        setOnLongClickListener(v -> {
            int col = (int) Math.floor((downX - padH) / (cell + gap));
            int row = (int) Math.floor((downY - padTop) / (cell + gap));
            host.onEmptyLongPress(page, Math.max(0, Math.min(COLS - 1, col)), Math.max(0, Math.min(rows - 1, row)));
            return true;
        });
    }

    void setColors(int accent, int neutral) {
        this.accent = accent;
        this.neutral = neutral;
    }

    int getRows() {
        return rows;
    }

    float getCell() {
        return cell;
    }

    float getGap() {
        return gap;
    }

    float getPadH() {
        return padH;
    }

    float getPadTop() {
        return padTop;
    }

    /** Cella il cui angolo in alto a sinistra è più vicino al punto (coordinate locali). */
    int[] nearestCell(float x, float y) {
        int c = Math.round((x - padH) / (cell + gap));
        int r = Math.round((y - padTop) / (cell + gap));
        return new int[]{c, r};
    }

    /** Cella che contiene il punto (coordinate locali). */
    int[] cellUnder(float x, float y) {
        int c = (int) Math.floor((x - padH + gap / 2f) / (cell + gap));
        int r = (int) Math.floor((y - padTop + gap / 2f) / (cell + gap));
        return new int[]{c, r};
    }

    void setPreview(int mode, int col, int row, int w, int h) {
        if (mode == pMode && col == pCol && row == pRow && w == pW && h == pH) return;
        pMode = mode;
        pCol = col;
        pRow = row;
        pW = w;
        pH = h;
        invalidate();
    }

    void clearPreview() {
        setPreview(PREVIEW_NONE, 0, 0, 0, 0);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int W = MeasureSpec.getSize(ws), H = MeasureSpec.getSize(hs);
        cell = (W - padH * 2 - gap * (COLS - 1)) / COLS;
        if (cell > 0) rows = Math.max(1, (int) ((H - padTop + gap) / (cell + gap)));
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            Item it = (Item) v.getTag();
            if (it == null) continue;
            int w = Math.round(it.w * cell + (it.w - 1) * gap);
            int h = Math.round(it.h * cell + (it.h - 1) * gap);
            v.measure(MeasureSpec.makeMeasureSpec(Math.max(w, 1), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(h, 1), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            Item it = (Item) v.getTag();
            if (it == null) continue;
            if (it.row + it.h > rows) {
                v.layout(0, 0, 0, 0); // non c'è spazio su questo schermo
                continue;
            }
            int x = Math.round(padH + it.col * (cell + gap));
            int y = Math.round(padTop + it.row * (cell + gap));
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }

    /** Anteprima disegnata sotto gli elementi: cella di arrivo durante il trascinamento. */
    @Override
    protected void onDraw(Canvas cv) {
        if (pMode == PREVIEW_NONE || cell <= 0) return;
        float x = padH + pCol * (cell + gap), y = padTop + pRow * (cell + gap);
        float w = pW * cell + (pW - 1) * gap, h = pH * cell + (pH - 1) * gap;
        pr.set(x, y, x + w, y + h);
        float m = Math.min(w, h);
        float rad = (pW == 1 && pH == 1) || pW == 1 || pH == 1 ? m / 2f : m * 0.16f;
        int col = pMode == PREVIEW_BAD ? neutral : accent;
        pp.setStyle(Paint.Style.FILL);
        pp.setPathEffect(null);
        pp.setColor(Theme.alpha(col, pMode == PREVIEW_MERGE ? 0.35f : 0.18f));
        cv.drawRoundRect(pr, rad, rad, pp);
        pp.setStyle(Paint.Style.STROKE);
        pp.setStrokeWidth(2 * dp);
        pp.setColor(col);
        pp.setPathEffect(pMode == PREVIEW_BAD ? new DashPathEffect(new float[]{6 * dp, 6 * dp}, 0) : null);
        pr.inset(dp, dp);
        cv.drawRoundRect(pr, rad, rad, pp);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        }
        return super.onTouchEvent(ev);
    }
}
