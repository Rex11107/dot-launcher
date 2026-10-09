package it.rex.dotlauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/** Contenitore a pagine orizzontali con scatto sulla pagina. */
class Pager extends ViewGroup {
    interface Listener {
        void onPageChanged(int page);
    }

    private final Scroller scroller;
    private final int slop, minFling;
    private VelocityTracker vt;
    private float downX, downY, lastX;
    private boolean dragging;
    private int current;
    private Listener listener;
    private int edgeL, edgeR;
    private boolean edgeTouch;

    Pager(Context c) {
        super(c);
        scroller = new Scroller(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
    }

    /** Larghezza delle zone laterali riservate alla gesture "indietro" del sistema. */
    void setEdgeGuard(int left, int right) {
        edgeL = left;
        edgeR = right;
    }

    void setListener(Listener l) {
        listener = l;
    }

    int getCurrent() {
        return current;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int W = MeasureSpec.getSize(ws), H = MeasureSpec.getSize(hs);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int W = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * W, 0, (i + 1) * W, b - t);
        }
        if (changed && scroller.isFinished()) scrollTo(current * W, 0);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (TileGrid.anyDragging) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                edgeTouch = downX < edgeL || downX > getWidth() - edgeR;
                dragging = !scroller.isFinished();
                if (dragging) scroller.abortAnimation();
                break;
            case MotionEvent.ACTION_MOVE:
                if (edgeTouch) break;
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    lastX = ev.getX();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(ev);
        int W = getWidth();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                if (!scroller.isFinished()) scroller.abortAnimation();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(ev.getX() - downX) > slop) dragging = true;
                if (dragging) {
                    float d = lastX - ev.getX();
                    int max = Math.max(0, (getChildCount() - 1) * W);
                    float nx = Math.max(-W * 0.15f, Math.min(max + W * 0.15f, getScrollX() + d));
                    scrollTo((int) nx, 0);
                }
                lastX = ev.getX();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float v = vt.getXVelocity();
                int target = Math.round(getScrollX() / (float) Math.max(1, W));
                if (Math.abs(v) > minFling) target = v < 0 ? current + 1 : current - 1;
                snapTo(target);
                vt.recycle();
                vt = null;
                dragging = false;
                return true;
        }
        return true;
    }

    void snapTo(int page) {
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        int dx = page * getWidth() - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, Math.min(420, 160 + Math.abs(dx) / 3));
        boolean changed = page != current;
        current = page;
        invalidate();
        if (changed && listener != null) listener.onPageChanged(page);
    }

    void setPageNow(int page) {
        current = Math.max(0, page);
        scrollTo(current * getWidth(), 0);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    View pageView(int i) {
        return getChildAt(i);
    }
}
