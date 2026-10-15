package it.rex.dotlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Il riquadro che si apre dal contapassi: distanza di oggi (stimata da passi × lunghezza del passo),
 * passi e obiettivo. Cresce dal widget e si chiude toccando fuori o con Indietro.
 */
final class StepsPopup {
    private static View backdrop, card;

    /** Lunghezza del passo in metri (predefinita 0,75 m). */
    static float stride(SharedPreferences p) {
        return p.getFloat("stride", 0.75f);
    }

    /** Miglia nei paesi che le usano, se l'unità è automatica. */
    static boolean miles(SharedPreferences p) {
        String u = p.getString("distUnit", "auto");
        if ("mi".equals(u)) return true;
        if ("km".equals(u)) return false;
        String c = Locale.getDefault().getCountry();
        return "US".equals(c) || "GB".equals(c) || "LR".equals(c) || "MM".equals(c);
    }

    static String distance(SharedPreferences p, int steps) {
        double m = steps * stride(p);
        NumberFormat nf = NumberFormat.getNumberInstance(T.loc());
        if (miles(p)) {
            nf.setMaximumFractionDigits(m / 1609.344 < 10 ? 2 : 1);
            nf.setMinimumFractionDigits(1);
            return nf.format(m / 1609.344) + " mi";
        }
        if (m < 1000) return Math.round(m) + " m";
        nf.setMaximumFractionDigits(m < 10000 ? 2 : 1);
        nf.setMinimumFractionDigits(1);
        return nf.format(m / 1000) + " km";
    }

    static boolean dismiss() {
        if (card == null) return false;
        final View c = card, b = backdrop;
        card = null;
        backdrop = null;
        c.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(Motion.EXIT).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) c.getParent();
            if (parent != null) {
                parent.removeView(c);
                parent.removeView(b);
            }
        }).start();
        if (b != null) b.animate().alpha(0f).setDuration(Motion.EXIT).start();
        return true;
    }

    static void show(Activity a, FrameLayout root, View anchor, Theme th, SharedPreferences prefs,
                     Runnable onGoal, Runnable onStride) {
        dismiss();
        float dp = a.getResources().getDisplayMetrics().density;
        int steps = State.steps, goal = Math.max(1, State.stepGoal);

        backdrop = new View(a);
        backdrop.setBackgroundColor(0x40000000);
        backdrop.setOnClickListener(v -> dismiss());
        backdrop.setContentDescription(T.t("Chiudi"));
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * dp);
        c.setPadding(pad, Math.round(16 * dp), pad, Math.round(16 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.sheetBg);
        bg.setCornerRadius(28 * dp);
        bg.setStroke(Math.max(1, Math.round(dp)), Theme.alpha(th.onTile, 0.12f));
        c.setBackground(bg);
        c.setElevation(12 * dp);
        c.setClickable(true);

        TextView head = text(a, th, T.t("Distanza di oggi"), 13, th.sub);
        c.addView(head);
        TextView dist = text(a, th, distance(prefs, steps), 40, th.onTile);
        dist.setTypeface(th.numFace);
        c.addView(dist);
        int pct = Math.round(100f * steps / goal);
        String line = NumberFormat.getIntegerInstance(T.loc()).format(steps) + T.t(" passi") + "  ·  "
                + pct + T.t("% dell'obiettivo");
        c.addView(text(a, th, line, 14, th.onTile));
        TextView note = text(a, th, T.t("Stima: passi × passo di ") + Math.round(stride(prefs) * 100) + " cm", 12, th.sub);
        note.setPadding(0, Math.round(6 * dp), 0, Math.round(12 * dp));
        c.addView(note);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button(a, th, dp, T.t("Obiettivo"), () -> {
            dismiss();
            onGoal.run();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.leftMargin = Math.round(8 * dp);
        row.addView(button(a, th, dp, T.t("Passo e unità"), () -> {
            dismiss();
            onStride.run();
        }), sl);
        c.addView(row);
        card = c;

        // posizione: centrato sul widget, sotto se c'è spazio, altrimenti sopra
        int[] ra = new int[2], rr = new int[2];
        anchor.getLocationInWindow(ra);
        root.getLocationInWindow(rr);
        int ax = ra[0] - rr[0], ay = ra[1] - rr[1];
        int W = root.getWidth(), H = root.getHeight();
        int cw = Math.min(W - Math.round(32 * dp), Math.max(anchor.getWidth(), Math.round(280 * dp)));
        c.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST));
        int ch = c.getMeasuredHeight();
        int x = Math.max(Math.round(16 * dp), Math.min(W - cw - Math.round(16 * dp), ax + anchor.getWidth() / 2 - cw / 2));
        int gap = Math.round(10 * dp);
        int y = ay + anchor.getHeight() + gap;
        if (y + ch > H - Math.round(24 * dp)) y = Math.max(Math.round(24 * dp), ay - ch - gap);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = x;
        lp.topMargin = y;
        root.addView(c, lp);

        // cresce dal widget
        c.setPivotX(ax + anchor.getWidth() / 2f - x);
        c.setPivotY(y > ay ? 0 : ch);
        c.setAlpha(0f);
        c.setScaleX(0.85f);
        c.setScaleY(0.85f);
        c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).start();
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(Motion.FADE).start();
    }

    private static TextView text(Activity a, Theme th, String s, float sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(th.bodyFace);
        return t;
    }

    private static TextView button(Activity a, Theme th, float dp, String label, Runnable r) {
        TextView b = text(a, th, label, 14, th.onTile);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Math.round(48 * dp));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24 * dp);
        g.setColor(Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f));
        b.setBackground(g);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private StepsPopup() {}
}
