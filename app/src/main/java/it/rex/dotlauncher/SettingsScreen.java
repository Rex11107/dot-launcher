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

    SettingsScreen(Activity a, Theme th, Runnable onBack) {
        super(a);
        this.act = a;
        this.th = th;
        dp = a.getResources().getDisplayMetrics().density;
        setBackgroundColor(th.drawerBg);
        setClickable(true);
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        addView(outer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(8), px(8), px(16), px(8));
        ImageView back = new ImageView(a);
        back.setImageBitmap(SectionIcons.bitmap(BACK, px(44), th.onTile, th.accent, 0));
        back.setPadding(px(12), px(12), px(12), px(12));
        back.setContentDescription(T.t("Indietro"));
        back.setOnClickListener(v -> onBack.run());
        bar.addView(back, new LinearLayout.LayoutParams(px(48), px(48)));
        View title;
        if (th.dots) {
            DotTextView dt = new DotTextView(a);
            dt.setPitch(3.4f * dp);
            dt.setFitWidth(true, 3.4f * dp);
            dt.setColor(th.onTile);
            dt.setAccent(".", th.accent);
            dt.setText(T.t("IMPOSTAZIONI."));
            title = dt;
        } else {
            TextView tv = new TextView(a);
            tv.setText(T.t("Impostazioni"));
            tv.setTextColor(th.onTile);
            tv.setTypeface(th.titleFace);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            title = tv;
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = px(6);
        bar.addView(title, tl);
        outer.addView(bar);

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(4), px(16), px(32));
        scroll.addView(col);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
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
    }

    // ---------- costruzione ----------

    void section(String title) {
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
        Switch sw = new Switch(act, on);
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

    /** Testo piccolo in fondo a un gruppo. */
    void note(String text) {
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

    /** Interruttore a pillola con il puntino che scorre. */
    private class Switch extends View {
        boolean on;
        private float pos;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Switch(Context c, boolean on) {
            super(c);
            this.on = on;
            pos = on ? 1f : 0f;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float target = on ? 1f : 0f;
            if (Math.abs(pos - target) > 0.01f) {
                pos += (target - pos) * 0.35f;
                postInvalidateOnAnimation();
            } else pos = target;
            float W = getWidth(), H = getHeight();
            r.set(0, 0, W, H);
            p.setColor(on ? th.accent : Theme.alpha(th.onTile, th.light ? 0.15f : 0.2f));
            cv.drawRoundRect(r, H / 2f, H / 2f, p);
            float rad = H * 0.32f;
            float x = H / 2f + (W - H) * pos;
            p.setColor(on ? th.onAccent : th.sub);
            cv.drawCircle(x, H / 2f, rad, p);
        }
    }
}
