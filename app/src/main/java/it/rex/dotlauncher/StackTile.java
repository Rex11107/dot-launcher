package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import java.util.List;

/**
 * Pila di widget: più widget Dot nello stesso spazio, si scorre in verticale per cambiarli.
 * I puntini sul bordo destro indicano quale si sta guardando.
 */
class StackTile extends FrameLayout {
    interface Host {
        View createKidView(Item kid);
        void onStackChanged(Item stack);
    }

    final Item stack;
    private final Theme th;
    private final Host host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int slop;
    private float downX, downY;
    private boolean swiping;
    private long dotsUntil;

    StackTile(Context c, Item stack, Theme th, Host host) {
        super(c);
        this.stack = stack;
        this.th = th;
        this.host = host;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(true);
        rebuild();
    }

    /** Ricrea le viste dei widget della pila. */
    void rebuild() {
        removeAllViews();
        List<Item> kids = stack.kids();
        for (int i = 0; i < kids.size(); i++) {
            View v = host.createKidView(kids.get(i));
            if (v == null) continue;
            addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            v.setVisibility(i == stack.current ? VISIBLE : GONE);
        }
        showDots();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        Item k = currentKid();
        info.setContentDescription(T.t("Pila di widget") + ", " + (stack.current + 1) + "/" + getChildCount()
                + (k != null ? ", " + Widgets.name(k.type) : ""));
        // con TalkBack si cambia widget con le azioni "scorri avanti/indietro"
        if (getChildCount() > 1) {
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.setScrollable(true);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, android.os.Bundle args) {
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            go(1);
            return true;
        }
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            go(-1);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    Item currentKid() {
        List<Item> kids = stack.kids();
        return kids.isEmpty() ? null : kids.get(stack.current);
    }

    @Override
    protected void onMeasure(int wms, int hms) {
        stack.kids(); // misura e posizione dei widget seguono la pila
        super.onMeasure(wms, hms);
    }

    // ---------- scorrimento ----------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (getChildCount() < 2) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                swiping = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                // un widget di sistema con una lista che può ancora scorrere si tiene il suo gesto
                View cur = getChildAt(stack.current);
                if (cur instanceof android.appwidget.AppWidgetHostView && canScrollDeep(cur, dy < 0 ? 1 : -1)) break;
                if (Math.abs(dy) > slop * 1.5f && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                    swiping = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!swiping) return super.onTouchEvent(ev);
        View cur = getChildAt(stack.current);
        float dy = ev.getY() - downY;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (cur != null) {
                    cur.setTranslationY(dy * 0.5f);
                    cur.setAlpha(1f - Math.min(0.6f, Math.abs(dy) / Math.max(1, getHeight())));
                }
                showDots();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                if (ev.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dy) > getHeight() * 0.18f) {
                    go(dy < 0 ? 1 : -1);
                } else if (cur != null) {
                    cur.animate().translationY(0).alpha(1f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
                }
                return true;
        }
        return true;
    }

    private static boolean canScrollDeep(View v, int dir) {
        if (v.canScrollVertically(dir)) return true;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (canScrollDeep(g.getChildAt(i), dir)) return true;
        }
        return false;
    }

    /** Passa al widget successivo (dir 1) o precedente (dir -1), a giro. */
    void go(int dir) {
        int n = getChildCount();
        if (n < 2) return;
        int from = stack.current, to = ((from + dir) % n + n) % n;
        show(to, dir);
    }

    /** Mostra un widget preciso della pila (dir = verso dell'animazione, 0 senza animazione). */
    void show(int to, int dir) {
        int n = getChildCount();
        if (to < 0 || to >= n || to == stack.current) return;
        View a = getChildAt(stack.current), b = getChildAt(to);
        stack.current = to;
        float h = Math.max(1, getHeight());
        if (dir == 0 || !Draw.anim) {
            if (a != null) {
                a.animate().cancel();
                a.setVisibility(GONE);
                a.setTranslationY(0);
                a.setAlpha(1f);
            }
            b.animate().cancel();
            b.setTranslationY(0);
            b.setAlpha(1f);
            b.setVisibility(VISIBLE);
        } else {
            // si annulla ciò che era in corso, così una nuova animazione parte sempre dai valori giusti
            b.animate().cancel();
            b.setVisibility(VISIBLE);
            b.setTranslationY(dir * h * 0.5f);
            b.setAlpha(0f);
            b.animate().translationY(0).alpha(1f).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).start();
            if (a != null) {
                a.animate().cancel();
                a.animate().translationY(-dir * h * 0.5f).alpha(0f).setDuration(Motion.EXIT)
                        .setInterpolator(Motion.EASE_IN).withEndAction(() -> {
                            if (a != getChildAt(stack.current)) a.setVisibility(GONE);
                            a.setTranslationY(0);
                            a.setAlpha(1f);
                        }).start();
            }
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
        b.invalidate();
        showDots();
        host.onStackChanged(stack);
    }

    // ---------- puntini ----------

    private void showDots() {
        dotsUntil = System.currentTimeMillis() + 1800;
        invalidate();
        removeCallbacks(hideDots);
        postDelayed(hideDots, 1850);
    }

    private final Runnable hideDots = this::invalidate;

    @Override
    protected void dispatchDraw(Canvas cv) {
        super.dispatchDraw(cv);
        int n = getChildCount();
        if (n < 2) return;
        boolean full = System.currentTimeMillis() < dotsUntil;
        float d = getResources().getDisplayMetrics().density;
        float r = 2.2f * d, gap = 7f * d;
        float x = getWidth() - 7f * d;
        if (stack.w == 1 && stack.h == 1) x = getWidth() - 3f * d;
        float y0 = getHeight() / 2f - (n - 1) * gap / 2f;
        Item k = currentKid();
        int fg = k != null && k.tone == 2 ? th.onAccent : k != null && k.tone == 1 ? th.onTileAlt : th.onTile;
        for (int i = 0; i < n; i++) {
            boolean on = i == stack.current;
            p.setColor(on ? (k != null && k.tone == 2 ? th.onAccent : th.accent)
                    : Theme.alpha(fg, full ? 0.4f : 0.18f));
            cv.drawCircle(x, y0 + i * gap, on ? r * 1.15f : r * 0.85f, p);
        }
    }
}
