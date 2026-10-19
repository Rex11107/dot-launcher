package it.rex.dotlauncher;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * Sfondo animato di Dot Launcher (buco nero, meteo dal vivo, onda di puntini).
 * Per la batteria: disegna solo quando è visibile (si ferma con un'app aperta o lo schermo spento),
 * al massimo 30 fotogrammi al secondo, e su Canvas accelerato senza motori 3D.
 */
public class DotLiveWallpaper extends WallpaperService {
    static final String PREF = "liveStyle";

    @Override
    public Engine onCreateEngine() {
        return new DotEngine();
    }

    private final class DotEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler h = new Handler(Looper.getMainLooper());
        private final LiveScenes scenes = new LiveScenes();
        private final long start = System.nanoTime();
        private SharedPreferences prefs;
        private boolean visible;
        private int scene;
        private float xOffset = 0.5f;
        private final Runnable frame = this::drawFrame;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences("dot", MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            readPrefs();
            setOffsetNotificationsEnabled(true);
        }

        private void readPrefs() {
            scene = Math.max(0, Math.min(LiveScenes.NAMES.length - 1, prefs.getInt(PREF, 0)));
            try {
                scenes.accent = Theme.build(DotLiveWallpaper.this, prefs).accent;
            } catch (Exception ignored) {
            }
            scenes.ghost = prefs.getBoolean("ghost", true);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (PREF.equals(key) || "accentC".equals(key) || "ghost".equals(key)) {
                readPrefs();
                if (visible) drawFrame();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            h.removeCallbacks(frame);
            if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            h.removeCallbacks(frame);
            if (v) drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            h.removeCallbacks(frame);
        }

        @Override
        public void onOffsetsChanged(float xOff, float yOff, float xStep, float yStep, int xPx, int yPx) {
            xOffset = xOff;
            if (visible) {
                h.removeCallbacks(frame);
                drawFrame();
            }
        }

        private void drawFrame() {
            if (!visible) return;
            SurfaceHolder sh = getSurfaceHolder();
            Canvas cv = null;
            try {
                cv = Build.VERSION.SDK_INT >= 26 ? sh.lockHardwareCanvas() : sh.lockCanvas();
                if (cv != null) {
                    float dp = getResources().getDisplayMetrics().density;
                    float t = (System.nanoTime() - start) / 1e9f;
                    // parallasse leggera: passando da una pagina all'altra la scena scorre di poco
                    float shift = (0.5f - xOffset) * 60 * dp;
                    scenes.draw(cv, scene, cv.getWidth(), cv.getHeight(), t, 9 * dp, shift);
                }
            } catch (Exception ignored) {
            } finally {
                if (cv != null) {
                    try {
                        sh.unlockCanvasAndPost(cv);
                    } catch (Exception ignored) {
                    }
                }
            }
            h.removeCallbacks(frame);
            if (visible) h.postDelayed(frame, 33); // circa 30 fotogrammi al secondo
        }
    }
}
