package it.rex.dotlauncher;

import android.content.SharedPreferences;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Sfondi animati realistici (buco nero, aurora, nebulosa, gradiente fluido), disegnati dalla scheda grafica
 * con piccoli programmi (shader) in assets/shaders. Per la batteria: si ferma quando non è visibile,
 * da 15 a 25 fotogrammi al secondo secondo la scena, lavora a risoluzione ridotta e si mette in pausa mentre
 * si tocca o si scorre la home (vedi LiveQuiet), così le animazioni del launcher restano fluide.
 */
public class ShaderWallpaper extends WallpaperService {
    static final String PREF = "glStyle";
    static final String[] FILES = {"blackhole", "aurora", "nebula", "fluid"};
    static final String[] NAMES = {"Buco nero realistico", "Aurora", "Nebulosa", "Gradiente fluido"};
    static final String[] DESCS = {"Il disco di luce piegato dalla gravità, come nei film",
            "Tende di luce verde e viola sopra le montagne", "Gas colorato che si muove piano tra le stelle",
            "Macchie morbide nel tuo colore d'accento"};
    /** Risoluzione di lavoro rispetto allo schermo, per scena (le scene sono morbide: non si nota). */
    private static final float[] SCALE = {0.42f, 0.36f, 0.33f, 0.25f};
    /** Fotogrammi al secondo che si cercano, per scena: il buco nero gira, le altre si muovono piano. */
    private static final int[] FPS = {25, 20, 15, 15};

    /**
     * Ogni quanti aggiornamenti dello schermo si disegna un fotogramma. Si va a tempo con lo schermo
     * (Choreographer) invece che con un timer: un timer da 40 ms su uno schermo da 60 Hz alterna intervalli
     * da 33 e 50 ms e il movimento sembra a scatti; un intervallo intero di aggiornamenti resta regolare.
     */
    private static float hz;

    /** Da richiamare quando lo sfondo torna visibile: lo schermo può aver cambiato frequenza (60/90/120 Hz). */
    static void readRefresh(android.content.Context c) {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) c.getSystemService(WINDOW_SERVICE);
            hz = Math.max(30f, wm.getDefaultDisplay().getRefreshRate());
        } catch (Exception e) {
            hz = 60f;
        }
    }

    static int vsyncStep(android.content.Context c, int fps) {
        if (hz <= 0) readRefresh(c);
        if (lowEnd(c)) fps = Math.max(10, fps * 2 / 3);
        return Math.max(1, (int) Math.ceil(hz / fps - 0.05f));
    }

    private static Boolean lowEnd;

    /**
     * Telefono economico o con poca memoria (meno di 4 GB): gli sfondi animati lavorano ancora più leggeri,
     * con meno pixel e meno fotogrammi. Le scene restano le stesse.
     */
    static boolean lowEnd(android.content.Context c) {
        if (lowEnd == null) {
            boolean low = false;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                low = am.isLowRamDevice() || mi.totalMem < 3_800_000_000L;
            } catch (Exception ignored) {
            }
            lowEnd = low;
        }
        return lowEnd;
    }

    @Override
    public Engine onCreateEngine() {
        return new GlEngine();
    }

    private final class GlEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private HandlerThread thread;
        private Handler gl;
        private SharedPreferences prefs;
        private volatile boolean visible;
        private EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        private EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        private EGLConfig cfg;
        private EGLSurface surf = EGL14.EGL_NO_SURFACE;
        private int prog = 0, progScene = -1, w = 1, h = 1;
        private volatile int scene;
        private volatile float shift;
        private float shown;      // parallasse disegnata: raggiunge piano quella vera
        private float clock;      // tempo della scena: si ferma con le pause, così non salta
        private long lastNs;
        private volatile float[] accent = {0.84f, 0.1f, 0.13f};
        private FloatBuffer quad;
        private final Runnable frame = this::drawFrame;
        private android.view.Choreographer choreo;   // del thread della grafica
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
            applySize(holder);
            // priorità bassa: il disegno della home viene sempre prima
            thread = new HandlerThread("dot-gl", Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            gl = new Handler(thread.getLooper());
            gl.post(() -> choreo = android.view.Choreographer.getInstance());
            quad = quad();
        }

        /** Superficie più piccola dello schermo: il compositore la allarga, la scheda grafica lavora meno. */
        private void applySize(SurfaceHolder holder) {
            try {
                DisplayMetrics m = new DisplayMetrics();
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                wm.getDefaultDisplay().getRealMetrics(m);
                float s = SCALE[scene] * (lowEnd(ShaderWallpaper.this) ? 0.8f : 1f);
                holder.setFixedSize(Math.max(1, Math.round(m.widthPixels * s)), Math.max(1, Math.round(m.heightPixels * s)));
            } catch (Exception ignored) {
            }
        }

        private void readPrefs() {
            scene = Math.max(0, Math.min(FILES.length - 1, prefs.getInt(PREF, 0)));
            try {
                int a = Theme.build(ShaderWallpaper.this, prefs).accent;
                accent = new float[]{((a >> 16) & 255) / 255f, ((a >> 8) & 255) / 255f, (a & 255) / 255f};
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (PREF.equals(key) || "accentC".equals(key)) {
                int old = scene;
                readPrefs();
                if (old != scene) applySize(getSurfaceHolder());
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            gl.post(() -> {
                initEgl();
                makeSurface(holder);
            });
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            gl.post(() -> {
                w = width;
                h = height;
                if (surf == EGL14.EGL_NO_SURFACE) makeSurface(holder);
                if (visible) drawFrame();
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            // la superficie va liberata prima di tornare: si aspetta il thread della grafica
            CountDownLatch done = new CountDownLatch(1);
            gl.post(() -> {
                gl.removeCallbacks(frame);
                if (choreo != null) choreo.removeFrameCallback(vsync);
                destroySurface();
                done.countDown();
            });
            try {
                done.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            gl.removeCallbacks(frame);
            if (v) readRefresh(ShaderWallpaper.this);
            if (v) gl.post(() -> {
                lastNs = 0;
                drawFrame();
            });
        }

        @Override
        public void onOffsetsChanged(float xOff, float yOff, float xStep, float yStep, int xPx, int yPx) {
            shift = (xOff - 0.5f) * 2f;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
            gl.post(() -> {
                destroySurface();
                if (prog != 0) GLES20.glDeleteProgram(prog);
                if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx);
                // niente eglTerminate: la connessione grafica è condivisa con il resto del launcher
                EGL14.eglReleaseThread();
                ctx = EGL14.EGL_NO_CONTEXT;
                dpy = EGL14.EGL_NO_DISPLAY;
                thread.quitSafely();
            });
        }

        // ---------- EGL ----------

        private void initEgl() {
            if (ctx != EGL14.EGL_NO_CONTEXT) return;
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            EGL14.eglInitialize(dpy, ver, 0, ver, 1);
            int[] attrs = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_NONE};
            EGLConfig[] cs = new EGLConfig[1];
            int[] n = new int[1];
            EGL14.eglChooseConfig(dpy, attrs, 0, cs, 0, 1, n, 0);
            cfg = cs[0];
            ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        }

        private void makeSurface(SurfaceHolder holder) {
            if (ctx == EGL14.EGL_NO_CONTEXT || holder.getSurface() == null || !holder.getSurface().isValid()) return;
            destroySurface();
            try {
                surf = EGL14.eglCreateWindowSurface(dpy, cfg, holder.getSurface(), new int[]{EGL14.EGL_NONE}, 0);
                EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
            } catch (Exception e) {
                surf = EGL14.EGL_NO_SURFACE;
            }
        }

        private void destroySurface() {
            if (surf != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(dpy, surf);
                surf = EGL14.EGL_NO_SURFACE;
            }
        }

        private void drawFrame() {
            if (choreo != null) choreo.removeFrameCallback(vsync);
            gl.removeCallbacks(frame);
            if (!visible || surf == EGL14.EGL_NO_SURFACE) return;
            long wait = LiveQuiet.remaining();
            if (wait > 0) {
                // l'utente sta toccando o scorrendo la home: si lascia il fotogramma fermo
                lastNs = 0;
                gl.postDelayed(frame, wait + 16);
                return;
            }
            if (prog == 0 || progScene != scene) {
                if (prog != 0) GLES20.glDeleteProgram(prog);
                prog = buildProgram(ShaderWallpaper.this, scene);
                progScene = scene;
            }
            long now = System.nanoTime();
            if (lastNs != 0) clock += Math.min(0.1f, (now - lastNs) / 1e9f);
            lastNs = now;
            if (clock > 3600f) clock -= 3600f;
            shown += (shift - shown) * 0.2f;
            if (Math.abs(shift - shown) < 0.002f) shown = shift;
            draw(prog, w, h, clock, shown, accent, quad);
            EGL14.eglSwapBuffers(dpy, surf);
            if (visible) {
                step = vsyncStep(ShaderWallpaper.this, FPS[scene]);
                vsyncs = 0;
                if (choreo != null) choreo.postFrameCallback(vsync);
                else gl.postDelayed(frame, 1000 / FPS[scene]);
            }
        }
    }

    // ---------- shader (usati sia dallo sfondo sia per l'immagine fissa) ----------

    private static String asset(android.content.Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open("shaders/" + name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int r;
            while ((r = in.read(b)) > 0) out.write(b, 0, r);
            return out.toString("UTF-8");
        }
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            android.util.Log.w("DotWallpaper", "shader: " + GLES20.glGetShaderInfoLog(s));
            GLES20.glDeleteShader(s);
            return 0;
        }
        return s;
    }

    /** Programma della scena (0 se la scheda grafica non lo accetta: si disegna nero). */
    static int buildProgram(android.content.Context c, int scene) {
        try {
            String frag = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + asset(c, "common.glsl") + asset(c, FILES[scene] + ".glsl")
                    + "\nvoid main(){ vec4 c; mainImage(c, gl_FragCoord.xy); gl_FragColor = c; }\n";
            int vs = compile(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPos; void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }");
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, frag);
            if (vs == 0 || fs == 0) {
                if (vs != 0) GLES20.glDeleteShader(vs);
                if (fs != 0) GLES20.glDeleteShader(fs);
                return 0;
            }
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs);
            GLES20.glAttachShader(p, fs);
            GLES20.glBindAttribLocation(p, 0, "aPos");
            GLES20.glLinkProgram(p);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) {
                GLES20.glDeleteProgram(p);
                return 0;
            }
            return p;
        } catch (Exception e) {
            return 0;
        }
    }

    static void draw(int prog, int w, int h, float t, float shift, float[] a, FloatBuffer quad) {
        GLES20.glViewport(0, 0, w, h);
        if (prog == 0) {
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            return;
        }
        GLES20.glUseProgram(prog);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "uRes"), w, h);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uTime"), t);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uShift"), shift);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(prog, "uAccent"), a[0], a[1], a[2]);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    static FloatBuffer quad() {
        float[] v = {-1, -1, 1, -1, -1, 1, 1, 1};
        FloatBuffer q = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        q.put(v).position(0);
        return q;
    }

    /**
     * Un fotogramma della scena come immagine, per la schermata di blocco (dove molti telefoni non mostrano
     * gli sfondi animati). Va chiamato da un thread di lavoro; restituisce null se la grafica non è disponibile.
     */
    static Bitmap still(android.content.Context c, int scene, int w, int h, int accentColor) {
        EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext cx = EGL14.EGL_NO_CONTEXT;
        EGLSurface sf = EGL14.EGL_NO_SURFACE;
        try {
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(d, ver, 0, ver, 1)) return null;
            int[] attrs = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE};
            EGLConfig[] cs = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(d, attrs, 0, cs, 0, 1, n, 0) || n[0] == 0) return null;
            cx = EGL14.eglCreateContext(d, cs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            sf = EGL14.eglCreatePbufferSurface(d, cs[0], new int[]{EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE}, 0);
            if (cx == EGL14.EGL_NO_CONTEXT || sf == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(d, sf, sf, cx)) return null;
            int p = buildProgram(c, scene);
            if (p == 0) return null;
            float[] a = {((accentColor >> 16) & 255) / 255f, ((accentColor >> 8) & 255) / 255f, (accentColor & 255) / 255f};
            draw(p, w, h, 20f, 0f, a, quad());
            GLES20.glFinish();
            ByteBuffer px = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px);
            GLES20.glDeleteProgram(p);
            Bitmap raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            px.rewind();
            raw.copyPixelsFromBuffer(px);
            // OpenGL legge dal basso verso l'alto: si capovolge
            android.graphics.Matrix flip = new android.graphics.Matrix();
            flip.preScale(1, -1);
            Bitmap out = Bitmap.createBitmap(raw, 0, 0, w, h, flip, false);
            if (out != raw) raw.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            if (d != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (sf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(d, sf);
                if (cx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(d, cx);
                EGL14.eglReleaseThread();
            }
        }
    }
}
