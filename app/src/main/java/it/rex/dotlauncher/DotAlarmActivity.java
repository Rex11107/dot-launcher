package it.rex.dotlauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.view.WindowManager;

import java.util.Date;

/**
 * La schermata della sveglia: un'alba che parte dal rosso scuro e sale fino al colore scelto,
 * con la luminosità dello schermo che cresce insieme. Idea dell'alba ispirata a FakeDawn (GPL-3.0).
 */
public class DotAlarmActivity extends Activity {
    private static final int DEEP = 0xFF1A0300;     // colore di partenza: quasi nero, rosso
    private static final int MID = 0xFFB3260A;      // a metà: rosso arancio

    private final Handler h = new Handler(Looper.getMainLooper());
    private Theme th;
    private Sky sky;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // i tasti del volume regolano la sveglia, non la musica
        setVolumeControlStream(android.media.AudioManager.STREAM_ALARM);
        th = Theme.build(this, getSharedPreferences("dot", MODE_PRIVATE));
        dp = getResources().getDisplayMetrics().density;
        SystemBars.edgeToEdge(getWindow(), false);
        sky = new Sky(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(sky);
        android.widget.LinearLayout bar = new android.widget.LinearLayout(this);
        bar.setOrientation(android.widget.LinearLayout.VERTICAL);
        bar.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        snoozeBtn = button(T.t("Posticipa"), 26, 84);
        snoozeBtn.setOnClickListener(v -> {
            DotAlarmService.snooze(this);
            v.postDelayed(() -> {
                if (!DotAlarmService.running) finish(); // posticipo a schermo spento
            }, 400);
        });
        stopBtn = button(T.t("Ferma"), 20, 64);
        stopBtn.setOnClickListener(v -> stopAll());
        android.widget.LinearLayout.LayoutParams sl = new android.widget.LinearLayout.LayoutParams(
                Math.round(320 * dp), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        bar.addView(snoozeBtn, sl);
        android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(
                Math.round(220 * dp), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Math.round(18 * dp);
        bar.addView(stopBtn, tl);
        android.widget.FrameLayout.LayoutParams bl = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        bl.bottomMargin = Math.round(96 * dp);
        root.addView(bar, bl);
        setContentView(root);
    }

    private android.widget.TextView snoozeBtn, stopBtn;

    private android.widget.TextView button(String text, int sp, int minH) {
        android.widget.TextView b = new android.widget.TextView(this);
        b.setText(text);
        b.setGravity(android.view.Gravity.CENTER);
        b.setTypeface(th.bodyFace);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        b.setMinHeight(Math.round(minH * dp));
        return b;
    }

    /** Colori dei pulsanti: chiari sul cielo scuro, scuri quando l'alba è piena. */
    private void styleButtons(boolean dark) {
        int fg = dark ? 0xFFFFF4E6 : 0xFF2A1408;
        DotAlarms.Alarm al = DotAlarmService.active;
        boolean canSnooze = al != null && al.snooze > 0 && DotAlarmService.ringing;
        snoozeBtn.setVisibility(canSnooze ? View.VISIBLE : View.GONE);
        if (al != null) snoozeBtn.setText(T.t("Posticipa") + " · " + al.snooze + " min");
        for (android.widget.TextView b : new android.widget.TextView[]{snoozeBtn, stopBtn}) {
            b.setTextColor(fg);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(48 * dp);
            if (b == snoozeBtn) g.setColor(dark ? 0x40FFFFFF : 0x40000000);
            else g.setStroke(Math.round(2 * dp), dark ? 0x99FFFFFF : 0x99000000);
            b.setBackground(g);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!DotAlarmService.running) {
                finish();
                return;
            }
            DotAlarms.Alarm al = DotAlarmService.active;
            float p = progress();
            float target = al == null ? 1f : Math.max(0.05f, al.bright / 100f);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            // curva lenta all'inizio: l'occhio percepisce la luminosità in modo non lineare, così il massimo
            // arriva davvero alla fine dell'alba e non a metà
            lp.screenBrightness = Math.max(0.01f, target * (float) Math.pow(p, 2.2));
            getWindow().setAttributes(lp);
            sky.invalidate();
            styleButtons(Math.pow(p, 1.4) < 0.55);
            h.postDelayed(this, 250);
        }
    };

    /** 0 = inizio dell'alba, 1 = ora del suono. */
    static float progress() {
        long span = DotAlarmService.ringAt - DotAlarmService.dawnStart;
        if (span <= 0 || DotAlarmService.ringing || DotAlarmService.fullLight) return 1f;
        float p = (System.currentTimeMillis() - DotAlarmService.dawnStart) / (float) span;
        return Math.max(0f, Math.min(1f, p));
    }

    @Override
    public void onBackPressed() {
        // la sveglia si ferma solo con il pulsante, per non spegnerla per sbaglio
    }

    private void stopAll() {
        DotAlarmService.stop(this);
        finish();
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** Il cielo: un sole che sale dal basso e schiarisce tutto lo schermo. */
    private class Sky extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Sky(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // i pulsanti veri sono sotto
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            DotAlarms.Alarm al = DotAlarmService.active;
            int end = al == null ? 0xFFFFB15C : al.endColor();
            float t = (float) Math.pow(progress(), 1.4); // anche i colori arrivano pieni solo alla fine
            int sky = t < 0.5f ? mix(DEEP, MID, t * 2f) : mix(MID, end, (t - 0.5f) * 2f);
            int top = mix(0xFF000000, sky, 0.35f + 0.65f * t);
            cv.drawColor(top);
            // il sole: un bagliore che sale dal fondo dello schermo
            float cy = H * (1.15f - 0.55f * t), r = Math.max(W, H) * (0.55f + 0.5f * t);
            p.setShader(new RadialGradient(W / 2f, cy, r, new int[]{mix(sky, 0xFFFFFFFF, 0.25f * t), sky, top},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, p);
            p.setShader(null);

            boolean dark = t < 0.55f;
            int fg = dark ? 0xFFFFF4E6 : 0xFF2A1408;
            String time = DateFormat.getTimeFormat(getContext()).format(new Date(DotAlarmService.ringAt));
            p.setColor(fg);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(th.numFace);
            p.setTextSize(Math.min(W, H) * 0.24f);
            cv.drawText(time, W / 2f, H * 0.34f, p);
            p.setTypeface(th.bodyFace);
            p.setTextSize(18 * dp);
            p.setAlpha(200);
            String sub = DotAlarmService.ringing ? (al != null && !al.name.isEmpty() ? al.name : T.t("Sveglia"))
                    : DotAlarmService.snoozed ? T.t("Posticipata") : T.t("Alba in corso");
            cv.drawText(sub, W / 2f, H * 0.34f + 40 * dp, p);
            p.setAlpha(255);
        }
    }

}
