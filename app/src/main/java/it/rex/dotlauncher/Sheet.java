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
        return list(a, th, title, items, null, selected, cb, null);
    }

    /** Elenco con icone facoltative (null = nessuna) e pressione prolungata facoltativa. */
    static Dialog list(Activity a, Theme th, String title, String[] items, android.graphics.Bitmap[] icons,
                       int selected, OnPick cb, OnPick longCb) {
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
            if (icons != null && i < icons.length && icons[i] != null) {
                android.widget.ImageView iv = new android.widget.ImageView(a);
                iv.setImageBitmap(icons[i]);
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(dp, 26), px(dp, 26));
                il.rightMargin = px(dp, 14);
                row.addView(iv, il);
            }
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
            if (longCb != null) {
                row.setOnLongClickListener(v -> {
                    d.dismiss();
                    longCb.pick(which);
                    return true;
                });
            }
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

    /** Griglia di app (contenuto di una cartella): tocco = apri, pressione prolungata = azione secondaria. */
    static Dialog grid(Activity a, Theme th, String title, int count,
                       java.util.function.IntFunction<android.graphics.Bitmap> icon,
                       java.util.function.IntFunction<String> label,
                       java.util.function.IntConsumer click,
                       java.util.function.IntConsumer longClick) {
        return grid(a, th, title, count, icon, label, click, longClick,
                "Tieni premuta un'app per toglierla dalla cartella");
    }

    static Dialog grid(Activity a, Theme th, String title, int count,
                       java.util.function.IntFunction<android.graphics.Bitmap> icon,
                       java.util.function.IntFunction<String> label,
                       java.util.function.IntConsumer click,
                       java.util.function.IntConsumer longClick, String hintText) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int which = i;
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, px(dp, 10), 0, px(dp, 10));
            cell.setBackground(pressed(th, dp));
            android.widget.ImageView iv = new android.widget.ImageView(a);
            iv.setImageBitmap(icon.apply(i));
            cell.addView(iv, new LinearLayout.LayoutParams(px(dp, 54), px(dp, 54)));
            TextView tv = new TextView(a);
            tv.setText(label.apply(i));
            tv.setTypeface(Fonts.regular);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tv.setTextColor(th.onTile);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = px(dp, 6);
            cell.addView(tv, tl);
            cell.setOnClickListener(v -> {
                d.dismiss();
                click.accept(which);
            });
            cell.setOnLongClickListener(v -> {
                d.dismiss();
                longClick.accept(which);
                return true;
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // celle vuote per allineare l'ultima riga
        if (row != null) {
            for (int i = count % 4; i != 0 && i < 4; i++) {
                row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        TextView hint = new TextView(a);
        hint.setText(hintText);
        hint.setTypeface(Fonts.regular);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(th.sub);
        hint.setPadding(px(dp, 14), px(dp, 8), px(dp, 14), 0);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(rows);
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        int estimate = ((count + 3) / 4) * px(dp, 100);
        int h = estimate > screenH * 0.55f ? (int) (screenH * 0.55f) : ViewGroup.LayoutParams.WRAP_CONTENT;
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        box.addView(hint);
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
        return input(a, th, title, hint, value, false, cb);
    }

    /** multi = testo su più righe (note), con la maiuscola a inizio frase. */
    static Dialog input(Activity a, Theme th, String title, String hint, String value, boolean multi, OnText cb) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        EditText et = new EditText(a);
        et.setText(value);
        et.setHint(hint);
        if (multi) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            et.setMinLines(3);
            et.setMaxLines(8);
            et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        } else {
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }
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

    /**
     * Un respiro prima di aprire un'app: cerchio a puntini che "respira", messaggio e scelte.
     * La prima scelta chiude il pannello senza aprire nulla; le altre si attivano dopo {@code waitSec} secondi.
     */
    static Dialog pause(Activity a, Theme th, String title, String message, int waitSec, String[] opts, OnPick cb) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        Breath br = new Breath(a, th);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(dp, 150));
        box.addView(br, bl);
        TextView msg = new TextView(a);
        msg.setText(message);
        msg.setTypeface(Fonts.regular);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        msg.setTextColor(th.sub);
        msg.setGravity(Gravity.CENTER_HORIZONTAL);
        msg.setPadding(px(dp, 14), px(dp, 6), px(dp, 14), px(dp, 10));
        box.addView(msg);
        TextView[] later = new TextView[opts.length];
        for (int i = 0; i < opts.length; i++) {
            final int w = i;
            TextView b = pill(a, th, dp, opts[i], i == 0, v -> {
                if (w > 0 && !v.isEnabled()) return;
                d.dismiss();
                if (w > 0) cb.pick(w);
            });
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(dp, 8);
            lp.leftMargin = px(dp, 6);
            lp.rightMargin = px(dp, 6);
            box.addView(b, lp);
            if (i > 0) {
                later[i] = b;
                if (waitSec > 0) {
                    b.setEnabled(false);
                    b.setAlpha(0.4f);
                }
            }
        }
        if (waitSec > 0) {
            final int[] left = {waitSec};
            final String[] base = opts.clone();
            Runnable tick = new Runnable() {
                @Override
                public void run() {
                    if (!d.isShowing()) return;
                    for (int i = 1; i < later.length; i++) {
                        if (later[i] == null) continue;
                        if (left[0] > 0) later[i].setText(base[i] + " · " + left[0]);
                        else {
                            later[i].setText(base[i]);
                            later[i].setEnabled(true);
                            later[i].animate().alpha(1f).setDuration(200).start();
                        }
                    }
                    if (left[0]-- > 0) box.postDelayed(this, 1000);
                }
            };
            tick.run();
        }
        show(d, box, dp);
        return d;
    }

    /** Cerchio di puntini che si espande e si contrae lentamente (4 s inspira, 4 s espira). */
    private static final class Breath extends View {
        private final Theme th;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final long t0 = android.os.SystemClock.uptimeMillis();

        Breath(android.content.Context c, Theme th) {
            super(c);
            this.th = th;
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            float W = getWidth(), H = getHeight();
            float t = ((android.os.SystemClock.uptimeMillis() - t0) % 8000) / 8000f;
            float k = (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2)); // 0..1..0
            float R = Math.min(W, H) * (0.15f + 0.2f * k);
            float cx = W / 2f, cy = H / 2f;
            float dot = Math.min(W, H) * 0.022f;
            int rings = 4;
            for (int r = 1; r <= rings; r++) {
                float rr = R * r / rings;
                int n = Math.max(6, Math.round(rr * 2 * (float) Math.PI / (dot * 3.2f)));
                p.setColor(Theme.alpha(r == rings ? th.accent : th.onTile, 0.25f + 0.6f * (r / (float) rings) * (0.4f + 0.6f * k)));
                for (int i = 0; i < n; i++) {
                    double ang = Math.PI * 2 * i / n + t * Math.PI * 0.5;
                    cv.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, dot, p);
                }
            }
            p.setColor(th.accent);
            cv.drawCircle(cx, cy, dot * 1.4f, p);
            p.setColor(th.sub);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            p.setTextSize(Math.min(W, H) * 0.075f);
            p.setTypeface(Fonts.regular);
            if (H > 0) cv.drawText(t < 0.5f ? "inspira" : "espira", cx, H - p.getTextSize() * 0.3f, p);
            if (isShown()) postInvalidateOnAnimation();
        }
    }

    // ---------- interni ----------

    private static int px(float dp, float v) {
        return Math.round(v * dp);
    }

    private static java.lang.ref.WeakReference<Dialog> last;

    private static Dialog base(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        last = new java.lang.ref.WeakReference<>(d);
        return d;
    }

    /** Chiude il pannello aperto (se c'è). Restituisce true se ce n'era uno. */
    static boolean dismissOpen() {
        Dialog d = last == null ? null : last.get();
        if (d != null && d.isShowing()) {
            try {
                d.dismiss();
            } catch (Exception ignored) {
            }
            return true;
        }
        return false;
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
                dt.setFitWidth(true, 2.6f * dp); // titoli lunghi: puntini più piccoli invece di tagliarli
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
                    t instanceof DotTextView ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = px(dp, 14);
            lp.rightMargin = px(dp, 14);
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
        b.setTextColor(primary ? th.onAccent : th.onTile);
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

    /** Pannello con contenuto libero (anteprime dei widget, ecc.), scorrevole. */
    static Dialog custom(Activity a, Theme th, String title, View content) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = base(a);
        LinearLayout box = panel(a, th, title);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (screenH * 0.72f)));
        show(d, box, dp);
        return d;
    }

    private static void show(Dialog d, View content, float dp) {
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = d.getContext().getResources().getDisplayMetrics().widthPixels;
            // su tablet e schermi larghi il pannello non si allarga oltre 560dp
            w.setLayout(Math.min(sw - px(dp, 24), px(dp, 560)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setWindowAnimations(android.R.style.Animation_InputMethod); // sale dal basso
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
