package it.rex.dotlauncher;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * Sfondo animato di Dot Launcher (buco nero, meteo dal vivo, onda di puntini).
 * Per la batteria: disegna solo quando è visibile (si ferma con un'app aperta o lo schermo spento),
 * circa 25 fotogrammi al secondo, in pausa mentre si tocca la home (LiveQuiet), e su Canvas accelerato senza motori 3D.
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
        private float clock, shown = 0.5f; // tempo della scena e parallasse disegnata (raggiunge piano quella vera)
        private long lastNs;
        private SharedPreferences prefs;
        private boolean visible;
        private int scene;
        private float xOffset = 0.5f;
        private final Runnable frame = this::drawFrame;
        // a tempo con lo schermo (vedi ShaderWallpaper.vsyncStep): fotogrammi regolari, niente scatti
        private final android.view.Choreographer choreo = android.view.Choreographer.getInstance();
        private int vsyncs, step = 3;
        private final android.view.Choreographer.FrameCallback vsync = new android.view.Choreographer.FrameCallback() {
            @Override
            public void doFrame(long t) {
                if (!visible) return;
                if (++vsyncs >= step) drawFrame();
                else choreo.postFrameCallback(this);
            }
        };

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
            choreo.removeFrameCallback(vsync);
            if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            h.removeCallbacks(frame);
            lastNs = 0;
            if (v) ShaderWallpaper.readRefresh(DotLiveWallpaper.this);
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
            xOffset = xOff; // lo raccoglie il prossimo fotogramma: niente disegni extra durante lo scorrimento
        }

        private void drawFrame() {
            choreo.removeFrameCallback(vsync);
            h.removeCallbacks(frame);
            if (!visible) return;
            long wait = LiveQuiet.remaining();
            if (wait > 0) {
                // la home si sta muovendo sotto il dito: fotogramma fermo, si riparte subito dopo
                lastNs = 0;
                h.postDelayed(frame, wait + 16);
                return;
            }
            long now = System.nanoTime();
            if (lastNs != 0) clock += Math.min(0.1f, (now - lastNs) / 1e9f);
            lastNs = now;
            shown += (xOffset - shown) * 0.2f;
            SurfaceHolder sh = getSurfaceHolder();
            Canvas cv = null;
            try {
                cv = sh.lockHardwareCanvas();
                if (cv != null) {
                    float dp = getResources().getDisplayMetrics().density;
                    float t = clock;
                    // parallasse leggera: passando da una pagina all'altra la scena scorre di poco
                    float shift = (0.5f - shown) * 60 * dp;
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
            // circa 25 fotogrammi al secondo (meno sui telefoni economici), a tempo con lo schermo
            if (visible) {
                step = ShaderWallpaper.vsyncStep(DotLiveWallpaper.this, 25);
                vsyncs = 0;
                choreo.postFrameCallback(vsync);
            }
        }
    }
}
