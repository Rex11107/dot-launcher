package it.rex.dotlauncher;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/** Pagina delle impostazioni a schermo intero: gruppi di voci con icona, valore e interruttori. */
class SettingsScreen extends SwipeBackFrame {
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
        setOnSwipeBack(onBack);
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
        scroll.animate().translationX(0).alpha(1f).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).withLayer().start();
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

    /** Voce solo da leggere: niente freccia e niente tocco. */
    void info(String[] icon, String title, String value) {
        LinearLayout r = baseRow(icon, title, value);
        r.setBackground(null);
        TextView v = r.getChildCount() > 1 && r.getChildAt(1) instanceof LinearLayout
                && ((LinearLayout) r.getChildAt(1)).getChildCount() > 1
                ? (TextView) ((LinearLayout) r.getChildAt(1)).getChildAt(1) : null;
        if (v != null) {
            v.setMaxLines(12); // qui la descrizione si legge per intero
            v.setTextIsSelectable(false);
        }
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
