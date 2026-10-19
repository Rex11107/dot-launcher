package it.rex.dotlauncher;

import android.content.SharedPreferences;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
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
 * al massimo 30 fotogrammi al secondo, e lavora a risoluzione ridotta (le scene sono morbide, non si nota).
 */
public class ShaderWallpaper extends WallpaperService {
    static final String PREF = "glStyle";
    static final String[] FILES = {"blackhole", "aurora", "nebula", "fluid"};
    static final String[] NAMES = {"Buco nero realistico", "Aurora", "Nebulosa", "Gradiente fluido"};
    static final String[] DESCS = {"Il disco di luce piegato dalla gravità, come nei film",
            "Tende di luce verde e viola sopra le montagne", "Gas colorato che si muove piano tra le stelle",
            "Macchie morbide nel tuo colore d'accento"};
    /** Risoluzione di lavoro rispetto allo schermo, per scena. */
    private static final float[] SCALE = {0.55f, 0.5f, 0.5f, 0.35f};

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
        private volatile float[] accent = {0.84f, 0.1f, 0.13f};
        private final long start = System.nanoTime();
        private FloatBuffer quad;
        private final Runnable frame = this::drawFrame;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences("dot", MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            readPrefs();
            setOffsetNotificationsEnabled(true);
            applySize(holder);
            thread = new HandlerThread("dot-gl");
            thread.start();
            gl = new Handler(thread.getLooper());
            float[] v = {-1, -1, 1, -1, -1, 1, 1, 1};
            quad = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quad.put(v).position(0);
        }

        /** Superficie più piccola dello schermo: il compositore la allarga, la scheda grafica lavora meno. */
        private void applySize(SurfaceHolder holder) {
            try {
                DisplayMetrics m = new DisplayMetrics();
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                wm.getDefaultDisplay().getRealMetrics(m);
                float s = SCALE[scene];
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
            if (v) gl.post(frame);
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
                if (dpy != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(dpy);
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

        // ---------- shader ----------

        private String asset(String name) throws Exception {
            try (InputStream in = getAssets().open("shaders/" + name)) {
                byte[] b = new byte[in.available()];
                int off = 0, r;
                while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
                return new String(b, 0, off, "UTF-8");
            }
        }

        private int compile(int type, String src) {
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

        private void ensureProgram() {
            if (prog != 0 && progScene == scene) return;
            if (prog != 0) GLES20.glDeleteProgram(prog);
            prog = 0;
            progScene = scene;
            try {
                String frag = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                        + asset("common.glsl") + asset(FILES[scene] + ".glsl")
                        + "\nvoid main(){ vec4 c; mainImage(c, gl_FragCoord.xy); gl_FragColor = c; }\n";
                int vs = compile(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPos; void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }");
                int fs = compile(GLES20.GL_FRAGMENT_SHADER, frag);
                if (vs == 0 || fs == 0) return;
                int p = GLES20.glCreateProgram();
                GLES20.glAttachShader(p, vs);
                GLES20.glAttachShader(p, fs);
                GLES20.glBindAttribLocation(p, 0, "aPos");
                GLES20.glLinkProgram(p);
                GLES20.glDeleteShader(vs);
                GLES20.glDeleteShader(fs);
                prog = p;
            } catch (Exception e) {
                prog = 0;
            }
        }

        private void drawFrame() {
            if (!visible || surf == EGL14.EGL_NO_SURFACE) return;
            ensureProgram();
            GLES20.glViewport(0, 0, w, h);
            if (prog != 0) {
                GLES20.glUseProgram(prog);
                float t = ((System.nanoTime() - start) / 1e9f) % 3600f;
                GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "uRes"), w, h);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uTime"), t);
                GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uShift"), shift);
                float[] a = accent;
                GLES20.glUniform3f(GLES20.glGetUniformLocation(prog, "uAccent"), a[0], a[1], a[2]);
                GLES20.glEnableVertexAttribArray(0);
                GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            } else {
                GLES20.glClearColor(0, 0, 0, 1);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }
            EGL14.eglSwapBuffers(dpy, surf);
            gl.removeCallbacks(frame);
            if (visible) gl.postDelayed(frame, 33); // circa 30 fotogrammi al secondo
        }
    }
}
