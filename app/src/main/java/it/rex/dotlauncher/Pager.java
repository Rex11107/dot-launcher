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

    /** Passaggio tra le pagine: 0 scorrimento, 1 profondità, 2 cubo, 3 dissolvenza. */
    static int transition;

    Pager(Context c) {
        super(c);
        scroller = new Scroller(c);
        setChildrenDrawingOrderEnabled(true);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
    }

    /** Larghezza delle zone laterali riservate alla gesture "indietro" del sistema. */
    void setEdgeGuard(int left, int right) {
        edgeL = left;
        edgeR = right;
    }

    /** Trascinando oltre la prima pagina verso destra (pagina a sinistra della home). */
    interface EdgeListener {
        void onPullBeforeFirst();
    }

    private EdgeListener edge;

    void setEdgeListener(EdgeListener l) {
        edge = l;
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
        applyTransition();
    }

    /** Avvisato a ogni scorrimento con la posizione da 0 (prima pagina) a 1 (ultima): serve allo sfondo animato. */
    interface ScrollHook {
        void onScroll(float position);
    }

    ScrollHook scrollHook;

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        applyTransition();
        if (scrollHook != null && getWidth() > 0 && getChildCount() > 1) {
            scrollHook.onScroll(Math.max(0f, Math.min(1f, l / (float) (getWidth() * (getChildCount() - 1)))));
        }
    }

    /** Effetto sulle pagine in base a quanto sono lontane dal centro (0 = al centro, ±1 = fuori). */
    void applyTransition() {
        int W = getWidth(), H = getHeight();
        if (W == 0) return;
        float cam = 9000 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            float off = (i * W - getScrollX()) / (float) W;
            float a = Math.abs(off);
            // valori di riposo
            float tx = 0, sc = 1f, al = 1f, rot = 0;
            c.setPivotX(W / 2f);
            c.setPivotY(H / 2f);
            // oltre la prima o l'ultima pagina (rimbalzo) non c'è una pagina con cui fare l'effetto
            boolean edge = (i == 0 && off > 0) || (i == getChildCount() - 1 && off < 0);
            if (a < 1f && !edge) {
                switch (transition) {
                    case 1: // profondità: la pagina che arriva da destra cresce da dietro
                        if (off > 0) {
                            tx = -off * W;
                            sc = 0.82f + 0.18f * (1 - off);
                            al = 1 - off;
                        }
                        break;
                    case 2: // cubo: le pagine ruotano sullo spigolo
                        rot = 90f * off * 0.75f;
                        c.setPivotX(off < 0 ? W : 0);
                        c.setPivotY(H / 2f);
                        c.setCameraDistance(cam);
                        break;
                    case 3: // dissolvenza: le pagine restano ferme e si sostituiscono
                        tx = -off * W;
                        al = 1 - a;
                        break;
                    default:
                        break;
                }
            }
            c.setTranslationX(tx);
            c.setScaleX(sc);
            c.setScaleY(sc);
            c.setAlpha(al);
            c.setRotationY(rot);
        }
    }

    @Override
    protected int getChildDrawingOrder(int count, int i) {
        // nella profondità la pagina che arriva sta sotto quella che se ne va
        return transition == 1 ? count - 1 - i : i;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (TileGrid.anyDragging) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeId = ev.getPointerId(0);
                downX = lastX = ev.getX();
                downY = ev.getY();
                edgeTouch = downX < edgeL || downX > getWidth() - edgeR;
                dragging = !scroller.isFinished();
                if (dragging) scroller.abortAnimation();
                break;
            case MotionEvent.ACTION_MOVE: {
                if (edgeTouch) break;
                int idx = index(ev);
                float dx = ev.getX(idx) - downX, dy = ev.getY(idx) - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    lastX = ev.getX(idx);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(ev);
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
                activeId = ev.getPointerId(0);
                downX = lastX = ev.getX();
                downY = ev.getY();
                if (!scroller.isFinished()) scroller.abortAnimation();
                return true;
            case MotionEvent.ACTION_MOVE: {
                int idx = index(ev);
                float x = ev.getX(idx);
                if (!dragging && Math.abs(x - downX) > slop) dragging = true;
                if (dragging) {
                    float d = lastX - x;
                    int max = Math.max(0, (getChildCount() - 1) * W);
                    float nx = Math.max(-W * 0.15f, Math.min(max + W * 0.15f, getScrollX() + d));
                    scrollTo((int) nx, 0);
                }
                lastX = x;
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(ev);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.computeCurrentVelocity(1000);
                float v = vt.getXVelocity();
                if (ev.getActionMasked() == MotionEvent.ACTION_UP && edge != null && current == 0 && dragging
                        && (getScrollX() < -W * 0.06f || (getScrollX() <= 0 && v > minFling))) {
                    snapTo(0);
                    vt.recycle();
                    vt = null;
                    dragging = false;
                    edge.onPullBeforeFirst();
                    return true;
                }
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

    // ---------- più dita: si segue un solo dito, e se si alza si passa all'altro senza salti ----------

    private int activeId;

    private int index(MotionEvent ev) {
        int i = ev.findPointerIndex(activeId);
        return i < 0 ? 0 : i;
    }

    private void pointerUp(MotionEvent ev) {
        int i = ev.getActionIndex();
        if (ev.getPointerId(i) != activeId) return;
        int other = i == 0 ? 1 : 0;
        activeId = ev.getPointerId(other);
        lastX = ev.getX(other);
        downX = lastX;
        downY = ev.getY(other);
    }

    void snapTo(int page) {
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        int dx = page * getWidth() - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, Math.round(Math.min(420, 160 + Math.abs(dx) / 3) * Motion.factor));
        boolean changed = page != current;
        current = page;
        invalidate();
        if (changed && listener != null) listener.onPageChanged(page);
    }

    void setPageNow(int page) {
        if (!scroller.isFinished()) scroller.abortAnimation();
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

}
