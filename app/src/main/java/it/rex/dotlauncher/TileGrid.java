package it.rex.dotlauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/**
 * Una pagina della home: griglia di 4 colonne a celle quadrate.
 * Pressione prolungata su un elemento: se si trascina lo si sposta, se si rilascia si apre il menu.
 */
class TileGrid extends ViewGroup {
    interface Host {
        boolean canPlace(Item it, int page, int col, int row, int w, int h);
        void onItemMoved(Item it);
        void onItemMenu(Item it);
        void onEmptyLongPress(int page, int col, int row);
        /** Elemento lasciato sopra un altro (es. app su app = cartella). true se gestito. */
        boolean onDropOnto(Item dragged, int page, int col, int row);
    }

    static boolean anyDragging;
    static final int COLS = 4;

    final int page;
    private final Host host;
    private final float padH, padTop, gap;
    private float cell;
    private int rows = 6;
    private final int slop;

    private View dragView;
    private boolean dragging, moved;
    private float lastRawX, lastRawY, startRawX, startRawY;
    private float downX, downY;

    TileGrid(Context c, int page, Host host) {
        super(c);
        this.page = page;
        this.host = host;
        float dp = c.getResources().getDisplayMetrics().density;
        padH = 16 * dp;
        padTop = 10 * dp;
        gap = 12 * dp;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(false);
        setClickable(true);
        setLongClickable(true);
        setOnLongClickListener(v -> {
            int col = (int) Math.floor((downX - padH) / (cell + gap));
            int row = (int) Math.floor((downY - padTop) / (cell + gap));
            host.onEmptyLongPress(page, Math.max(0, Math.min(COLS - 1, col)), Math.max(0, Math.min(rows - 1, row)));
            return true;
        });
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

    /** Da chiamare dal long-click di un elemento. */
    void startDrag(View v) {
        dragView = v;
        dragging = true;
        anyDragging = true;
        moved = false;
        startRawX = lastRawX;
        startRawY = lastRawY;
        v.animate().scaleX(1.06f).scaleY(1.06f).alpha(0.9f).setDuration(120).start();
        v.bringToFront();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        }
        if (dragging) {
            handleDrag(ev);
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        }
        if (dragging) {
            handleDrag(ev);
            return true;
        }
        return super.onTouchEvent(ev);
    }

    private void handleDrag(MotionEvent ev) {
        float dx = ev.getRawX() - startRawX;
        float dy = ev.getRawY() - startRawY;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(dx, dy) > slop) moved = true;
                if (moved) {
                    dragView.setTranslationX(dx);
                    dragView.setTranslationY(dy);
                }
                break;
            case MotionEvent.ACTION_UP:
                finishDrag(dx, dy, true);
                break;
            case MotionEvent.ACTION_CANCEL:
                finishDrag(0, 0, false);
                break;
        }
    }

    private void finishDrag(float dx, float dy, boolean commit) {
        View v = dragView;
        Item it = v == null ? null : (Item) v.getTag();
        dragging = false;
        anyDragging = false;
        dragView = null;
        if (v == null || it == null) return;
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
        if (commit && !moved) {
            v.setTranslationX(0);
            v.setTranslationY(0);
            host.onItemMenu(it);
            return;
        }
        if (commit) {
            int col = Math.round((v.getLeft() + dx - padH) / (cell + gap));
            int row = Math.round((v.getTop() + dy - padTop) / (cell + gap));
            col = Math.max(0, Math.min(COLS - it.w, col));
            row = Math.max(0, Math.min(rows - it.h, row));
            if (host.canPlace(it, page, col, row, it.w, it.h)) {
                it.col = col;
                it.row = row;
                host.onItemMoved(it);
            } else {
                // cella sotto il centro dell'elemento trascinato
                int cc = (int) Math.floor((v.getLeft() + dx + v.getWidth() / 2f - padH) / (cell + gap));
                int cr = (int) Math.floor((v.getTop() + dy + v.getHeight() / 2f - padTop) / (cell + gap));
                if (host.onDropOnto(it, page, cc, cr)) {
                    v.setTranslationX(0);
                    v.setTranslationY(0);
                    return;
                }
            }
        }
        v.setTranslationX(0);
        v.setTranslationY(0);
        requestLayout();
    }
}
