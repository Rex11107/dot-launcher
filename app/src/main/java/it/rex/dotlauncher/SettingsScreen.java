package it.rex.dotlauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Pagina delle impostazioni a schermo intero: gruppi di voci con icona, valore e interruttori. */
class SettingsScreen extends FrameLayout {
    interface OnToggle {
        void set(boolean on);
    }

    static final String[] CLOCK = {
            "..#####..", ".#.....#.", "#...#...#", "#...#...#", "#...RR..#",
            "#.......#", "#.......#", ".#.....#.", "..#####.."};
    static final String[] BACK = {
            "....#....", "...#.....", "..#......", ".#.......", "#########",
            ".#.......", "..#......", "...#.....", "....#...."};

    private final Activity act;
    private final Theme th;
    private final float dp;
    private final ScrollView scroll;
    private final LinearLayout col;
    private LinearLayout group;

    private final LinearLayout bar;
    private final LinearLayout outer;
    private View titleView;
    private View header;
    /** Ricerca: si mostrano solo le voci che contengono il testo (null = tutte). */
    private String filter;
    private String pendingSection;
    private int matches;

    // ---------- trascinamento laterale per tornare indietro ----------
    private Runnable onBackRun;
    private boolean swipeBack, swiping;
    private float downX, downY;
    private android.view.VelocityTracker vel;

    SettingsScreen(Activity a, Theme th, Runnable onBack) {
        this(a, th, null, onBack);
    }

    /** title null = "Impostazioni". */
    SettingsScreen(Activity a, Theme th, String titleText, Runnable onBack) {
        super(a);
        this.act = a;
        this.th = th;
        dp = a.getResources().getDisplayMetrics().density;
        setBackgroundColor(th.drawerBg);
        setClickable(true);
        outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        addView(outer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(8), px(8), px(16), px(8));
        ImageView back = new ImageView(a);
        back.setImageBitmap(SectionIcons.bitmap(BACK, px(44), th.onTile, th.accent, 0));
        back.setPadding(px(12), px(12), px(12), px(12));
        back.setContentDescription(T.t("Indietro"));
        back.setOnClickListener(v -> onBack.run());
        onBackRun = onBack;
        bar.addView(back, new LinearLayout.LayoutParams(px(48), px(48)));
        View title;
        if (th.dots) {
            DotTextView dt = new DotTextView(a);
            dt.setPitch(3.4f * dp);
            dt.setFitWidth(true, 3.4f * dp);
            dt.setColor(th.onTile);
            dt.setAccent(".", th.accent);
            title = dt;
        } else {
            TextView tv = new TextView(a);
            tv.setTextColor(th.onTile);
            tv.setTypeface(th.titleFace);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            title = tv;
        }
        titleView = title;
        setTitle(titleText);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = px(6);
        bar.addView(title, tl);
        outer.addView(bar);

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        // ciò che scorre sotto il titolo sfuma invece di restare tagliato a metà
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(px(28));
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(4), px(16), px(32));
        scroll.addView(col);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Passaggio tra elenco e categoria: il contenuto entra da destra (avanti) o da sinistra (indietro). */
    void slideContent(boolean forward) {
        scroll.animate().cancel();
        scroll.setTranslationX((forward ? 1 : -1) * px(36));
        scroll.setAlpha(0f);
        scroll.animate().translationX(0).alpha(1f).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).start();
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
            // a metà dei bordi il gesto arriva alla pagina invece che al sistema (Android ne concede 200 dp per lato)
            int w = r - l, h = b - t, half = px(100), edge = px(32);
            java.util.List<android.graphics.Rect> ex = new java.util.ArrayList<>();
            ex.add(new android.graphics.Rect(0, h / 2 - half, edge, h / 2 + half));
            ex.add(new android.graphics.Rect(w - edge, h / 2 - half, w, h / 2 + half));
            setSystemGestureExclusionRects(ex);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        if (!swipeBack) return super.onInterceptTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                swiping = false;
                if (vel != null) vel.recycle();
                vel = android.view.VelocityTracker.obtain();
                vel.addMovement(ev);
                break;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (vel != null) vel.addMovement(ev);
                float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                // solo un movimento chiaramente orizzontale: lo scorrimento verticale dell'elenco resta com'è
                if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.8f) {
                    swiping = true;
                    return true;
                }
                break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent ev) {
        if (!swipeBack) return super.onTouchEvent(ev);
        int a = ev.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN) {
            downX = ev.getRawX();
            downY = ev.getRawY();
            swiping = false;
            if (vel != null) vel.recycle();
            vel = android.view.VelocityTracker.obtain();
        }
        if (vel != null) vel.addMovement(ev);
        float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
        if (a == android.view.MotionEvent.ACTION_MOVE && !swiping) {
            int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.8f) swiping = true;
        }
        if (!swiping) return super.onTouchEvent(ev);
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
                    && (Math.abs(dx) > px(96) || (Math.abs(dx) > px(40) && Math.abs(v) > px(700) && Math.signum(v) == Math.signum(dx)));
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

    /** Titolo in alto (null = "Impostazioni"). */
    void setTitle(String titleText) {
        if (titleView instanceof DotTextView) {
            ((DotTextView) titleView).setText(titleText == null ? T.t("IMPOSTAZIONI.") : titleText.toUpperCase(T.loc()) + ".");
        } else if (titleView instanceof TextView) {
            ((TextView) titleView).setText(titleText == null ? T.t("Impostazioni") : titleText);
        }
        if (titleView != null) titleView.setContentDescription(titleText == null ? T.t("Impostazioni") : titleText);
    }

    /** Vista fissa sotto il titolo che non scorre e non si svuota con clear() (es. il campo di ricerca). */
    void setHeader(View v) {
        if (header != null) outer.removeView(header);
        header = v;
        if (v != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = px(16);
            lp.rightMargin = px(16);
            lp.bottomMargin = px(4);
            outer.addView(v, 1, lp);
        }
    }

    void showHeader(boolean show) {
        if (header != null) header.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Ricerca: da qui in poi si aggiungono solo le voci il cui titolo o descrizione contiene il testo;
     * le sezioni compaiono solo se hanno almeno una voce. null = nessun filtro.
     */
    void setFilter(String q) {
        filter = q == null || q.trim().isEmpty() ? null : DotTextView.normalize(q.trim());
        pendingSection = null;
        matches = 0;
    }

    /** Voci trovate con il filtro attuale. */
    int matches() {
        return matches;
    }

    private boolean filtering() {
        return filter != null;
    }

    /** Elemento in alto a destra, accanto al titolo. */
    void setAction(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px(8);
        bar.addView(v, lp);
    }

    /** Una vista libera nell'elenco; le voci successive aprono un nuovo gruppo. */
    void custom(View v) {
        if (filtering()) return;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(10);
        col.addView(v, lp);
        group = null;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    void setInsets(int top, int bottom) {
        setPadding(0, top, 0, bottom);
    }

    int scrollY() {
        return scroll.getScrollY();
    }

    void scrollTo(int y) {
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    void clear() {
        col.removeAllViews();
        group = null;
        pendingSection = null;
        matches = 0;
    }

    // ---------- costruzione ----------

    void section(String title) {
        if (filtering()) {
            // la sezione compare solo se almeno una voce corrisponde alla ricerca
            pendingSection = title;
            group = null;
            return;
        }
        title = T.t(title);
        TextView t = new TextView(act);
        t.setText(th.upperLabels ? title.toUpperCase(T.loc()) : title);
        t.setTypeface(th.labelFace);
        t.setTextColor(th.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(th.upperLabels ? 0.06f : 0f);
        t.setPadding(px(14), px(18), px(14), px(8));
        col.addView(t);
        group = new LinearLayout(act);
        group.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.tile);
        bg.setCornerRadius(px(26));
        if (th.stroke != 0) bg.setStroke(Math.max(1, px(1)), th.stroke);
        group.setBackground(bg);
        group.setClipToOutline(true);
        col.addView(group, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout baseRow(String[] icon, String title, String value) {
        title = T.t(title);
        value = T.t(value);
        if (filtering()) {
            String hay = DotTextView.normalize(title + " " + (value == null ? "" : value));
            if (!hay.contains(filter)) return new LinearLayout(act); // non corrisponde: la voce non si aggiunge
            matches++;
            if (group == null) {
                // prima voce trovata di questa sezione: ora la sezione si crea davvero
                String sec = pendingSection == null ? "" : pendingSection;
                String f = filter;
                filter = null;
                section(sec);
                filter = f;
                pendingSection = null;
            }
        }
        if (group == null) section("");
        if (group.getChildCount() > 0) {
            View div = new View(act);
            div.setBackgroundColor(Theme.alpha(th.onTile, th.light ? 0.06f : 0.07f));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
            dl.leftMargin = px(68);
            group.addView(div, dl);
        }
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(14), px(12), px(14), px(12));
        r.setMinimumHeight(px(64));
        r.setBackground(pressed());
        ImageView iv = new ImageView(act);
        iv.setImageBitmap(SectionIcons.bitmap(icon, px(40), th.onTile, th.accent,
                Theme.alpha(th.onTile, th.light ? 0.07f : 0.1f)));
        r.addView(iv, new LinearLayout.LayoutParams(px(40), px(40)));
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(14), 0, px(8), 0);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(th.onTile);
        t.setTypeface(th.bodyFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        texts.addView(t);
        if (value != null && !value.isEmpty()) {
            TextView v = new TextView(act);
            v.setText(value);
            v.setTextColor(th.sub);
            v.setTypeface(th.bodyFace);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            v.setMaxLines(2);
            v.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(v);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        group.addView(r);
        return r;
    }

    /** Voce che apre una scelta o un'azione. */
    void row(String[] icon, String title, String value, Runnable act) {
        LinearLayout r = baseRow(icon, title, value);
        TextView chev = new TextView(this.act);
        chev.setText("›");
        chev.setTextColor(th.sub);
        chev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        r.addView(chev);
        r.setOnClickListener(v -> act.run());
    }

    /** Voce con interruttore. */
    void toggle(String[] icon, String title, String desc, boolean on, OnToggle cb) {
        LinearLayout r = baseRow(icon, title, desc);
        PillSwitch sw = new PillSwitch(act, th, on);
        r.addView(sw, new LinearLayout.LayoutParams(px(48), px(28)));
        r.setOnClickListener(v -> {
            sw.on = !sw.on;
            sw.invalidate();
            v.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            v.postDelayed(() -> cb.set(sw.on), 160);
        });
        // TalkBack: la riga si comporta come un interruttore e dice se è acceso o spento
        r.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(sw.on);
            }
        });
    }

    /** Avviso: un riquadro a parte, color ambra, che non si confonde con le impostazioni. */
    void warn(String[] icon, String title, String text, Runnable act) {
        if (filtering()) return;
        final int amber = th.light ? 0xFFB26A00 : 0xFFFFB547;
        LinearLayout r = new LinearLayout(this.act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(14), px(14), px(14), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.alpha(amber, th.light ? 0.10f : 0.12f));
        bg.setStroke(Math.max(1, px(1)), Theme.alpha(amber, 0.45f));
        bg.setCornerRadius(px(22));
        r.setBackground(bg);
        ImageView iv = new ImageView(this.act);
        iv.setImageBitmap(SectionIcons.bitmap(icon, px(40), amber, amber, Theme.alpha(amber, 0.16f)));
        r.addView(iv, new LinearLayout.LayoutParams(px(40), px(40)));
        LinearLayout texts = new LinearLayout(this.act);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(14), 0, px(8), 0);
        TextView t = new TextView(this.act);
        t.setText(title);
        t.setTextColor(th.onTile);
        t.setTypeface(th.bodyFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        texts.addView(t);
        TextView v = new TextView(this.act);
        v.setText(text);
        v.setTextColor(th.sub);
        v.setTypeface(th.bodyFace);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        texts.addView(v);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chev = new TextView(this.act);
        chev.setText("›");
        chev.setTextColor(th.sub);
        chev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        r.addView(chev);
        r.setOnClickListener(x -> act.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(10);
        col.addView(r, lp);
        group = null; // le voci successive aprono un nuovo gruppo
    }

    /** Testo piccolo in fondo a un gruppo. */
    void note(String text) {
        if (filtering()) return;
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(th.sub);
        t.setTypeface(th.bodyFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(px(14), px(8), px(14), 0);
        col.addView(t);
    }

    private StateListDrawable pressed() {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.alpha(th.onTile, 0.08f));
        s.addState(new int[]{android.R.attr.state_pressed}, g);
        return s;
    }
}
