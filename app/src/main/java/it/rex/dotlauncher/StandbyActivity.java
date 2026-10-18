package it.rex.dotlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.view.WindowManager;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * StandBy: mentre il telefono è in carica, un orologio a puntini a tutto schermo con data, meteo e batteria.
 * Si chiude toccandolo o staccando il caricatore. Il contenuto si sposta piano ogni minuto
 * e la luminosità resta bassa, così sugli schermi AMOLED non resta il segno.
 */
public class StandbyActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Face face;

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction())) {
                finish();
                return;
            }
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) State.battery = Math.round(level * 100f / scale);
            int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            State.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            if (face != null) face.invalidate();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (face != null) {
                face.shift++;
                face.invalidate();
            }
            // al prossimo minuto esatto
            long now = System.currentTimeMillis();
            ui.postDelayed(this, 60000 - now % 60000 + 50);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.25f; // luce bassa: è un orologio da comodino
        getWindow().setAttributes(lp);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        SharedPreferences prefs = getSharedPreferences("dot", MODE_PRIVATE);
        face = new Face(this, Theme.build(this, prefs), prefs.getBoolean("h24", DateFormat.is24HourFormat(this)));
        face.setOnClickListener(v -> finish());
        face.setContentDescription(T.t("StandBy: tocca per chiudere"));
        setContentView(face);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(power, f);
        ui.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(power);
        } catch (Exception ignored) {
        }
        ui.removeCallbacks(tick);
        // uscendo (Home, spegnimento dello schermo) StandBy si chiude: si riapre alla prossima carica
        finish();
    }

    /** Il quadrante: ora a sinistra, data, meteo e batteria a destra (in verticale uno sotto l'altro). */
    static final class Face extends View {
        private final Theme th;
        private final boolean h24;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int shift;

        Face(Context c, Theme th, boolean h24) {
            super(c);
            this.th = th;
            this.h24 = h24;
            setBackgroundColor(0xFF000000);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            // piccolo spostamento a ogni minuto (fino a ±2% dello schermo)
            float dx = (float) Math.sin(shift * 1.7) * W * 0.02f, dy = (float) Math.cos(shift * 1.3) * H * 0.02f;
            cv.save();
            cv.translate(dx, dy);
            Date now = new Date();
            String time = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", T.loc()).format(now);
            String date = new SimpleDateFormat("EEEE d MMMM", T.loc()).format(now);
            int fg = 0xFFE6E6E6, sub = 0xFF8A8A8A;
            int acc = 0xFF000000 | th.accent;
            boolean land = W > H;
            float pad = Math.min(W, H) * 0.08f;
            if (land) {
                float th1 = H * 0.42f;
                Draw.big(cv, th, time, pad, H / 2f, th1, W * 0.6f - pad, fg, ":", acc, -1, p, true);
                float x = W - pad;
                Draw.label(cv, th, date, x, H * 0.36f, H * 0.065f, fg, 1, p, W * 0.34f);
                info(cv, x, H * 0.5f, H * 0.06f, W * 0.34f, sub, acc, 1);
            } else {
                float th1 = W * 0.34f;
                Draw.big(cv, th, time, W / 2f, H * 0.4f, th1, W - pad * 2, fg, ":", acc, 0, p, true);
                Draw.label(cv, th, date, W / 2f, H * 0.58f, W * 0.055f, fg, 0, p, W - pad * 2);
                info(cv, W / 2f, H * 0.66f, W * 0.05f, W - pad * 2, sub, acc, 0);
            }
            cv.restore();
        }

        /** Meteo e batteria, uno sotto l'altro. */
        private void info(Canvas cv, float x, float y, float size, float maxW, int sub, int acc, int align) {
            float line = size * 1.8f;
            if (State.wOk) {
                Draw.label(cv, th, State.wTemp + "° · " + State.describe(State.wCode), x, y, size, sub, align, p, maxW);
                y += line;
            }
            if (State.battery >= 0) {
                String b = State.battery + "%" + (State.charging ? T.t(" · in carica") : "");
                Draw.label(cv, th, b, x, y, size, State.battery >= 100 ? acc : sub, align, p, maxW);
            }
        }
    }
}
