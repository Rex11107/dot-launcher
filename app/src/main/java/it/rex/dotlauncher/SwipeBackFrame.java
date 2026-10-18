package it.rex.dotlauncher;

import android.content.Context;
import android.widget.FrameLayout;

/**
 * Contenitore che torna indietro trascinando di lato. Serve nelle schermate del launcher: su molti telefoni
 * (Honor compreso) il sistema tiene per sé il gesto indietro quando l'app è la home, e non lo passa.
 * Il gesto si legge prima di passare il tocco ai figli, così funziona anche sopra elenchi che scorrono.
 */
class SwipeBackFrame extends FrameLayout {
    private Runnable onBackRun;
    private boolean swipeBack, swiping, vertical;
    private float downX, downY;
    private android.view.VelocityTracker vel;
    private final float density;

    SwipeBackFrame(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
    }

    void setOnSwipeBack(Runnable r) {
        onBackRun = r;
    }

    private int px(float v) {
        return Math.round(v * density);
    }

    /**
     * Trascinando di lato la pagina torna indietro da sola. Serve sopra la home: lì molti telefoni (Honor compreso)
     * tengono per sé il gesto indietro del sistema, perché per loro la home non ha niente "dietro".
     */
    void enableSwipeBack() {
        swipeBack = true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (swipeBack && android.os.Build.VERSION.SDK_INT >= 29) {
            // nella parte bassa dei bordi, dove arriva il pollice, il gesto va alla pagina invece che al sistema.
            // Android ne concede al massimo 200 dp per lato; si lascia libero l'angolo in basso (gesto Home).
            int w = r - l, h = b - t, band = px(200), edge = px(32);
            int bottom = h - getPaddingBottom() - px(56), top = Math.max(0, bottom - band);
            java.util.List<android.graphics.Rect> ex = new java.util.ArrayList<>();
            ex.add(new android.graphics.Rect(0, top, edge, bottom));
            ex.add(new android.graphics.Rect(w - edge, top, w, bottom));
            setSystemGestureExclusionRects(ex);
        }
    }

    /**
     * Il gesto si legge qui, prima di passare il tocco all'elenco: così arriva sempre, anche quando l'elenco
     * ha già cominciato a scorrere (che altrimenti impedirebbe alla pagina di vedere il resto del movimento).
     */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (!swipeBack) return super.dispatchTouchEvent(ev);
        int a = ev.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN) {
            downX = ev.getRawX();
            downY = ev.getRawY();
            swiping = false;
            vertical = false;
            if (vel != null) vel.recycle();
            vel = android.view.VelocityTracker.obtain();
        }
        if (vel != null) vel.addMovement(ev);
        float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
        if (!swiping) {
            if (a == android.view.MotionEvent.ACTION_MOVE && !vertical && ev.getPointerCount() == 1) {
                int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    // movimento orizzontale: da qui il tocco è del gesto, l'elenco riceve un annullamento
                    swiping = true;
                    android.view.MotionEvent c = android.view.MotionEvent.obtain(ev);
                    c.setAction(android.view.MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c);
                    c.recycle();
                    downX = ev.getRawX() - Math.signum(dx) * slop * 1.5f; // niente salto all'inizio
                    return true;
                } else if (Math.abs(dy) > slop * 2 && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                    vertical = true; // l'elenco sta scorrendo: il gesto laterale non parte più
                }
            }
            return super.dispatchTouchEvent(ev);
        }
        if (a == android.view.MotionEvent.ACTION_MOVE) {
            // la pagina segue il dito, un po' frenata, e si rimpicciolisce appena (come l'anteprima di Android)
            float p = Math.min(1f, Math.abs(dx) / (getWidth() * 0.5f));
            animate().cancel();
            setTranslationX(dx * 0.4f);
            setScaleX(1f - 0.06f * p);
            setScaleY(1f - 0.06f * p);
        } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
            float v = 0;
            if (vel != null) {
                vel.computeCurrentVelocity(1000);
                v = vel.getXVelocity();
                vel.recycle();
                vel = null;
            }
            swiping = false;
            boolean go = a == android.view.MotionEvent.ACTION_UP
                    && (Math.abs(dx) > px(72) || (Math.abs(dx) > px(28) && Math.abs(v) > px(500) && Math.signum(v) == Math.signum(dx)));
            if (go && onBackRun != null) {
                // posizione normale subito: chiusura o cambio di pagina hanno già la loro animazione
                animate().cancel();
                setTranslationX(0);
                setScaleX(1f);
                setScaleY(1f);
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                onBackRun.run();
            } else {
                animate().translationX(0).scaleX(1f).scaleY(1f).setDuration(Motion.FADE)
                        .setInterpolator(Motion.STANDARD).start();
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick(); // il trascinamento non è un clic; TalkBack usa il pulsante Indietro
    }

}
