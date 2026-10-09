package it.rex.dotlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Pannelli e menu in stile Nothing, con i caratteri del launcher (non quelli di sistema). */
final class Sheet {
    interface OnPick {
        void pick(int which);
    }

    interface OnText {
        void text(String value);
    }

    /** Elenco di scelte. selected >= 0 mostra il pallino rosso sulla scelta attuale. */
    static Dialog list(Activity a, Theme th, String title, String[] items, int selected, OnPick cb) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(dp, 14), px(dp, 13), px(dp, 14), px(dp, 13));
            row.setBackground(pressed(th, dp));
            TextView tv = new TextView(a);
            tv.setText(items[i]);
            tv.setTypeface(Fonts.regular);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setTextColor(th.onTile);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (selected >= 0) {
                View dot = new View(a);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                if (i == selected) g.setColor(th.accent);
                else g.setStroke(Math.max(1, px(dp, 1.5f)), Theme.alpha(th.onTile, 0.35f));
                dot.setBackground(g);
                row.addView(dot, new LinearLayout.LayoutParams(px(dp, 12), px(dp, 12)));
            }
            row.setOnClickListener(v -> {
                d.dismiss();
                cb.pick(which);
            });
            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(rows);
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        int estimate = items.length * px(dp, 50);
        int h = estimate > screenH * 0.6f ? (int) (screenH * 0.6f) : ViewGroup.LayoutParams.WRAP_CONTENT;
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        show(d, box, dp);
        return d;
    }

    static Dialog confirm(Activity a, Theme th, String title, String message, String ok, Runnable onOk) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        TextView msg = new TextView(a);
        msg.setText(message);
        msg.setTypeface(Fonts.regular);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        msg.setTextColor(th.sub);
        msg.setPadding(px(dp, 14), 0, px(dp, 14), px(dp, 8));
        box.addView(msg);
        box.addView(buttons(a, th, dp, d, ok, () -> {
            d.dismiss();
            onOk.run();
        }));
        show(d, box, dp);
        return d;
    }

    static Dialog input(Activity a, Theme th, String title, String hint, String value, OnText cb) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        EditText et = new EditText(a);
        et.setText(value);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setTypeface(Fonts.regular);
        et.setTextColor(th.onTile);
        et.setHintTextColor(th.sub);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.alpha(th.onTile, 0.07f));
        bg.setCornerRadius(px(dp, 22));
        et.setBackground(bg);
        et.setPadding(px(dp, 18), px(dp, 12), px(dp, 18), px(dp, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px(dp, 6);
        lp.rightMargin = px(dp, 6);
        box.addView(et, lp);
        box.addView(buttons(a, th, dp, d, "OK", () -> {
            d.dismiss();
            cb.text(et.getText().toString().trim());
        }));
        show(d, box, dp);
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        et.requestFocus();
        return d;
    }

    // ---------- interni ----------

    private static int px(float dp, float v) {
        return Math.round(v * dp);
    }

    private static Dialog base(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private static LinearLayout panel(Activity a, Theme th, String title) {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.sheetBg);
        bg.setCornerRadius(px(dp, 28));
        if (th.light) bg.setStroke(Math.max(1, px(dp, 1)), 0x14000000);
        box.setBackground(bg);
        box.setPadding(px(dp, 10), px(dp, 20), px(dp, 10), px(dp, 12));
        if (title != null && !title.isEmpty()) {
            View t;
            if (th.dots && DotFont.supports(title)) {
                DotTextView dt = new DotTextView(a);
                dt.setPitch(2.6f * dp);
                dt.setColor(th.onTile);
                dt.setAccent(".", th.accent);
                dt.setText(title + ".");
                t = dt;
            } else {
                TextView tv = new TextView(a);
                tv.setText(title);
                tv.setTypeface(Fonts.medium);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                tv.setTextColor(th.onTile);
                t = tv;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = px(dp, 14);
            lp.bottomMargin = px(dp, 12);
            box.addView(t, lp);
        }
        return box;
    }

    private static View buttons(Activity a, Theme th, float dp, Dialog d, String ok, Runnable onOk) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, px(dp, 14), px(dp, 4), 0);
        row.addView(pill(a, th, dp, "Annulla", false, v -> d.dismiss()));
        View okv = pill(a, th, dp, ok, true, v -> onOk.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px(dp, 10);
        row.addView(okv, lp);
        return row;
    }

    private static TextView pill(Activity a, Theme th, float dp, String text, boolean primary, View.OnClickListener l) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTypeface(Fonts.medium);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? 0xFFFFFFFF : th.onTile);
        b.setPadding(px(dp, 20), px(dp, 11), px(dp, 20), px(dp, 11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(dp, 24));
        g.setColor(primary ? th.accent : Theme.alpha(th.onTile, 0.08f));
        b.setBackground(g);
        b.setOnClickListener(l);
        return b;
    }

    private static StateListDrawable pressed(Theme th, float dp) {
        GradientDrawable on = new GradientDrawable();
        on.setColor(Theme.alpha(th.onTile, 0.08f));
        on.setCornerRadius(px(dp, 18));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, on);
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    private static void show(Dialog d, View content, float dp) {
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = d.getContext().getResources().getDisplayMetrics().widthPixels;
            w.setLayout(sw - px(dp, 24), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.y = px(dp, 20);
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
    }

    private Sheet() {}
}
