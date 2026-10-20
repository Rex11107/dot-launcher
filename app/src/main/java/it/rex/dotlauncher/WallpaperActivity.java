package it.rex.dotlauncher;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Galleria di sfondi generati, più la scelta di una foto (anche trasformata a puntini). */
public class WallpaperActivity extends Activity {
    private static final int REQ_IMAGE = 7;
    private static final int REQ_LIVE = 8;
    private static final int REQ_MY = 9;

    private SharedPreferences prefs;
    private Theme th;
    private float dp;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private long[] seeds;
    private Bitmap[] previews;
    private Adapter adapter;
    private int pw, ph, cardW, cardH;
    private boolean busy;
    private GridView monoGrid, fixedGrid, liveGrid, myGrid;
    private Class<?> pendingSvc;   // sfondo animato in attesa della conferma di Android

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        prefs = getSharedPreferences("dot", MODE_PRIVATE);
        th = Theme.build(this, prefs);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setBackgroundDrawable(new ColorDrawable(th.bg));
        SystemBars.edgeToEdge(getWindow(), th.light);

        int n = WallpaperGen.NAMES.length;
        seeds = new long[n];
        previews = new Bitmap[n];
        for (int i = 0; i < n; i++) seeds[i] = rnd.nextLong();

        int sw = getResources().getDisplayMetrics().widthPixels;
        int pad = Math.round(16 * dp), gap = Math.round(12 * dp);
        cardW = (sw - pad * 2 - gap) / 2;
        cardH = Math.round(cardW * 1.9f);
        pw = cardW;
        ph = cardH;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, Math.round(24 * dp), pad, 0);

        DotTextView title = new DotTextView(this);
        title.setPitch(4.4f * dp);
        title.setColor(th.onTile);
        title.setAccent(".", th.accent);
        title.setText(T.t("SFONDI."));
        root.addView(title);

        // quattro schede: monocromatici, fissi, animati e i miei (scorrono se lo schermo è stretto)
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, Math.round(16 * dp), 0, Math.round(12 * dp));
        android.widget.HorizontalScrollView tabScroll = new android.widget.HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.addView(tabs);
        root.addView(tabScroll);
        FrameLayout pagesBox = new FrameLayout(this);
        root.addView(pagesBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 1) monocromatici: tinte unite
        monoGrid = gridView(gap);
        monoGrid.setAdapter(new MonoAdapter());
        monoGrid.setOnItemClickListener((pa, v, pos, id) -> askTarget(which -> applySolid(pos, which)));

        // 2) fissi: gli sfondi generati e le foto
        LinearLayout fixedPage = new LinearLayout(this);
        fixedPage.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(this);
        hint.setText(T.t("Tocca per applicare · tieni premuto per una nuova variante"));
        hint.setTextColor(th.sub);
        hint.setTypeface(th.bodyFace);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Math.round(10 * dp);
        hl.bottomMargin = Math.round(16 * dp);
        fixedPage.addView(hint, hl);

        GridView grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setSelector(new ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, Math.round(24 * dp));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((pa, v, pos, id) -> {
            if (pos == 0) pickImage();
            else askTarget(which -> applyGenerated(pos - 1, which));
        });
        grid.setOnItemLongClickListener((pa, v, pos, id) -> {
            if (pos == 0) return false;
            seeds[pos - 1] = rnd.nextLong();
            renderPreview(pos - 1);
            return true;
        });
        fixedPage.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        fixedGrid = grid;

        // 3) animati: realistici (scheda grafica) e a puntini
        liveGrid = gridView(gap);
        liveGrid.setAdapter(new LiveAdapter());
        liveGrid.setOnItemClickListener((pa, v, pos, id) -> {
            if (pos < ShaderWallpaper.FILES.length) applyAnimated(ShaderWallpaper.class, ShaderWallpaper.PREF, pos);
            else applyAnimated(DotLiveWallpaper.class, DotLiveWallpaper.PREF, pos - ShaderWallpaper.FILES.length);
        });

        // 4) i miei: immagini caricate, da sistemare sullo schermo
        myGrid = gridView(gap);
        myGrid.setAdapter(new MyAdapter());
        myGrid.setOnItemClickListener((pa, v, pos, id) -> {
            if (pos == 0) pickMine();
            else new CropEditor((java.io.File) myGrid.getAdapter().getItem(pos)).show();
        });
        myGrid.setOnItemLongClickListener((pa, v, pos, id) -> {
            if (pos == 0) return false;
            deleteMine((java.io.File) myGrid.getAdapter().getItem(pos));
            return true;
        });

        final View[] pages = {monoGrid, fixedPage, liveGrid, myGrid};
        final String[] tabNames = {T.t("Monocromatici"), T.t("Fissi"), T.t("Animati"), T.t("I miei")};
        final TextView[] tabViews = new TextView[4];
        for (int t = 0; t < 4; t++) {
            pagesBox.addView(pages[t], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            final int tab = t;
            TextView tv = new TextView(this);
            tv.setText(tabNames[t]);
            tv.setTypeface(th.bodyFace);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setGravity(Gravity.CENTER);
            tv.setMinHeight(Math.round(40 * dp));
            tv.setPadding(Math.round(16 * dp), 0, Math.round(16 * dp), 0);
            tv.setOnClickListener(v -> selectTab(tab, pages, tabViews));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.rightMargin = Math.round(8 * dp);
            tabs.addView(tv, tl);
            tabViews[t] = tv;
        }
        selectTab(Math.max(0, Math.min(3, prefs.getInt("wallTab", 1))), pages, tabViews);
        // aperta dalla home: anche qui Honor può tenersi il gesto indietro, quindi lo si legge da qui
        SwipeBackFrame frame = new SwipeBackFrame(this);
        frame.setOnSwipeBack(this::onBackPressed);
        frame.enableSwipeBack();
        frame.addView(root);
        setContentView(frame);
        final int padTop = Math.round(24 * dp);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int[] r = SystemBars.read(in);
            root.setPadding(pad + r[0], padTop + r[1], pad + r[2], 0);
            for (GridView g : new GridView[]{monoGrid, fixedGrid, liveGrid, myGrid}) g.setPadding(0, 0, 0, Math.round(24 * dp) + r[3]);
            return in;
        });
        root.requestApplyInsets();

        for (int i = 0; i < n; i++) renderPreview(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }

    private void renderPreview(int i) {
        final long seed = seeds[i];
        exec.execute(() -> {
            Bitmap b = WallpaperGen.make(i, pw, ph, seed);
            ui.post(() -> {
                previews[i] = b;
                adapter.notifyDataSetChanged();
            });
        });
    }

    private interface Target {
        void go(int which);
    }

    private void askTarget(Target t) {
        if (busy) return;
        String[] items = {T.t("Schermata home"), T.t("Home e schermata di blocco"), T.t("Solo schermata di blocco")};
        Sheet.list(this, th, T.t("Applica sfondo"), items, -1, w ->
                t.go(w == 0 ? WallpaperManager.FLAG_SYSTEM : w == 2 ? WallpaperManager.FLAG_LOCK
                        : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK));
    }

    private int[] screen() {
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        return new int[]{m.widthPixels, m.heightPixels};
    }

    private void applyGenerated(int style, int which) {
        final long seed = seeds[style];
        final int[] sz = screen();
        busy = true;
        toast(T.t("Applico lo sfondo…"));
        exec.execute(() -> {
            try {
                Bitmap b = WallpaperGen.make(style, sz[0], sz[1], seed);
                WallpaperManager.getInstance(this).setBitmap(b, null, true, which);
                if (home(which)) {
                    Glass.save(this, b);
                    prefs.edit().putInt("wallStyle", style).apply(); // per segnare lo sfondo in uso
                }
                done(which);
            } catch (Exception e) {
                fail();
            }
        });
    }

    private static boolean home(int which) {
        return (which & WallpaperManager.FLAG_SYSTEM) != 0;
    }

    /** Fatto. Se è cambiata solo la schermata di blocco, la home e le sue impostazioni restano come sono. */
    private void done(int which) {
        if (!home(which)) {
            ui.post(() -> {
                busy = false;
                toast(T.t("Schermata di blocco impostata"));
            });
            return;
        }
        prefs.edit().putBoolean("wall", true).putInt("wallVer", prefs.getInt("wallVer", 0) + 1).apply();
        ui.post(() -> {
            busy = false;
            toast(T.t("Sfondo applicato"));
            finish();
        });
    }

    private void fail() {
        ui.post(() -> {
            busy = false;
            toast(T.t("Impossibile applicare lo sfondo"));
        });
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, T.t("Scegli una foto")), REQ_IMAGE);
        } catch (Exception e) {
            toast(T.t("Nessuna galleria disponibile"));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LIVE) {
            // tornati dalla conferma di Android
            Class<?> svc = pendingSvc;
            pendingSvc = null;
            if (svc != null && liveActive(svc)) toast(T.t("Sfondo animato applicato"));
            if (liveGrid != null) ((BaseAdapter) liveGrid.getAdapter()).notifyDataSetChanged();
            return;
        }
        if (req == REQ_MY) {
            if (res != RESULT_OK || data == null) return;
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            if (!uris.isEmpty()) importMine(uris);
            return;
        }
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        String[] modes = {T.t("Originale"), T.t("A puntini (colori)"), T.t("A puntini (bianco e nero)"), T.t("A strisce (vetro rigato)")};
        Sheet.list(this, th, T.t("Come vuoi la foto?"), modes, -1, mode ->
                askTarget(which -> applyPhoto(uri, mode, which)));
    }

    private void applyPhoto(Uri uri, int mode, int which) {
        final int[] sz = screen();
        busy = true;
        toast(T.t("Applico lo sfondo…"));
        exec.execute(() -> {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                if (mode == 0) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        wm.setStream(in, null, true, which);
                    }
                    Bitmap src = home(which) ? decode(uri, Math.max(sz[0], sz[1]) / 4) : null;
                    if (src != null) Glass.save(this, WallpaperGen.cover(src, sz[0] / 4, sz[1] / 4));
                } else {
                    Bitmap src = decode(uri, Math.max(sz[0], sz[1]) / 2);
                    if (src == null) throw new Exception("decode");
                    Bitmap b = mode == 3 ? WallpaperGen.ribbed(src, sz[0], sz[1], System.nanoTime())
                            : WallpaperGen.dotify(src, sz[0], sz[1], mode == 2);
                    wm.setBitmap(b, null, true, which);
                    if (home(which)) Glass.save(this, b);
                }
                if (home(which)) prefs.edit().putInt("wallStyle", -1).apply(); // foto: nessuno sfondo generato in uso
                done(which);
            } catch (Exception e) {
                fail();
            }
        });
    }

    private Bitmap decode(Uri uri, int target) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, o2);
        }
    }

    private GridView gridView(int gap) {
        GridView g = new GridView(this);
        g.setNumColumns(2);
        g.setHorizontalSpacing(gap);
        g.setVerticalSpacing(gap);
        g.setSelector(new ColorDrawable(0));
        g.setVerticalScrollBarEnabled(false);
        g.setClipToPadding(false);
        g.setPadding(0, 0, 0, Math.round(24 * dp));
        return g;
    }

    private void selectTab(int tab, View[] pages, TextView[] tabViews) {
        prefs.edit().putInt("wallTab", tab).apply();
        for (int t = 0; t < pages.length; t++) {
            boolean on = t == tab;
            pages[t].setVisibility(on ? View.VISIBLE : View.GONE);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(20 * dp);
            g.setColor(on ? th.onTile : Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f));
            tabViews[t].setBackground(g);
            tabViews[t].setTextColor(on ? th.bg | 0xFF000000 : th.onTile);
            tabViews[t].setSelected(on);
        }
    }

    // ---------- monocromatici ----------

    private static final String[] MONO_NAMES = {"Nero", "Antracite", "Ardesia", "Grigio perla", "Bianco caldo",
            "Blu notte", "Verde bosco", "Bordeaux", "Sabbia", "Colore d'accento"};
    private static final int[] MONO = {0xFF000000, 0xFF1C1C1E, 0xFF3A3F47, 0xFFC9CCD1, 0xFFF4F1EA,
            0xFF0E1A33, 0xFF13261D, 0xFF3A0F17, 0xFFCDBB9B, 0};

    private int monoColor(int i) {
        return MONO[i] == 0 ? (0xFF000000 | th.accent) : MONO[i];
    }

    private void applySolid(int i, int which) {
        final int[] sz = screen();
        final int c = monoColor(i);
        busy = true;
        exec.execute(() -> {
            try {
                Bitmap b = Bitmap.createBitmap(Math.max(1, sz[0] / 4), Math.max(1, sz[1] / 4), Bitmap.Config.RGB_565);
                b.eraseColor(c);
                WallpaperManager.getInstance(this).setBitmap(b, null, true, which);
                if (home(which)) {
                    Glass.save(this, b);
                    prefs.edit().putInt("wallStyle", -100 - i).apply(); // per segnare la tinta in uso
                }
                done(which);
            } catch (Exception e) {
                fail();
            }
        });
    }

    private class MonoAdapter extends BaseAdapter {
        @Override public int getCount() { return MONO.length; }
        @Override public Object getItem(int i) { return i; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            int c = monoColor(pos);
            boolean inUse = prefs.getBoolean("wall", false) && prefs.getInt("wallStyle", 0) == -100 - pos;
            return card(new ColorDrawable(c), null, T.t(MONO_NAMES[pos]), inUse, Math.round(cardW * 1.1f),
                    Color.luminance(c) > 0.5f ? 0xFF111111 : 0xFFFFFFFF);
        }
    }

    // ---------- animati ----------

    private boolean liveActive(Class<?> svc) {
        try {
            android.app.WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
            return info != null && new android.content.ComponentName(this, svc).equals(info.getComponent());
        } catch (Exception e) {
            return false;
        }
    }

    private String animatedName(Class<?> svc, int scene) {
        return T.t(svc == ShaderWallpaper.class ? ShaderWallpaper.NAMES[scene] : LiveScenes.NAMES[scene]);
    }

    /**
     * Sfondo animato: si apre un'anteprima a due pagine. La prima mostra la scena in movimento con
     * "Imposta sfondo home"; scorrendo, la seconda mostra un fotogramma fisso della stessa scena con
     * "Imposta schermata home" e "Imposta schermata di blocco". Così la home e la schermata di blocco
     * si impostano con due pulsanti separati (molti telefoni, Honor compreso, non animano la schermata di blocco).
     */
    private void applyAnimated(Class<?> svc, String pref, int scene) {
        if (busy) return;
        new AnimatedPreview(svc, pref, scene).show();
    }

    /** Home animata: Android non lascia impostarla in silenzio, la prima volta compare la sua conferma. */
    private void setAnimatedHome(Class<?> svc, String pref, int scene) {
        // la home deve lasciar vedere lo sfondo del telefono, altrimenti lo coprirebbe con la tinta del tema
        prefs.edit().putInt(pref, scene).putBoolean("wall", true).apply();
        if (liveActive(svc)) {
            toast(T.t("Sfondo animato: ") + animatedName(svc, scene));
            if (liveGrid != null) ((BaseAdapter) liveGrid.getAdapter()).notifyDataSetChanged();
            return;
        }
        pendingSvc = svc;
        android.content.ComponentName me = new android.content.ComponentName(this, svc);
        try {
            startActivityForResult(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, me), REQ_LIVE);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER), REQ_LIVE);
                toast(T.t("Scegli \"Sfondo animato Dot\" dall'elenco"));
            } catch (Exception e2) {
                pendingSvc = null;
                toast(T.t("Questo telefono non permette gli sfondi animati"));
            }
        }
    }

    /** Fotogramma fisso della scena, a risoluzione piena (da un thread di lavoro; null se non riesce). */
    private Bitmap renderStill(Class<?> svc, int scene, int w, int h) {
        if (svc == ShaderWallpaper.class) return ShaderWallpaper.still(this, scene, w, h, th.accent);
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            LiveScenes sc = new LiveScenes();
            sc.accent = th.accent;
            sc.ghost = prefs.getBoolean("ghost", true);
            sc.draw(new Canvas(b), scene, w, h, 20f, 9 * dp, 0);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Immagine fissa sulla home o sulla schermata di blocco, con un esito chiaro. */
    private void setStill(Bitmap b, int which, Runnable after) {
        busy = true;
        exec.execute(() -> {
            String msg;
            boolean ok = false;
            try {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                int before = wm.getWallpaperId(which);
                wm.setBitmap(b, null, true, which);
                int now = wm.getWallpaperId(which);
                ok = true;
                if (which == WallpaperManager.FLAG_SYSTEM) {
                    Bitmap small = Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / 4), Math.max(1, b.getHeight() / 4), true);
                    Glass.save(this, small);
                    prefs.edit().putBoolean("wall", true).putInt("wallStyle", -1)
                            .putInt("wallVer", prefs.getInt("wallVer", 0) + 1).apply();
                    msg = T.t("Schermata home impostata");
                } else {
                    msg = now != before ? T.t("Schermata di blocco impostata")
                            : T.t("Android l'ha accettata, ma il telefono potrebbe ignorarla: se non cambia usa \"Imposta dalla Galleria\"");
                }
            } catch (Exception e) {
                msg = T.t("Il telefono ha rifiutato l'immagine: prova \"Imposta dalla Galleria\"");
            }
            final String m = msg;
            final boolean done = ok;
            ui.post(() -> {
                busy = false;
                toast(m);
                if (done && after != null) after.run();
            });
        });
    }

    private final class AnimatedPreview {
        private final Class<?> svc;
        private final int scene;
        private final android.app.Dialog dlg;
        private final FrameLayout pages;
        private final View[] page = new View[2];
        private final LinearLayout[] actions = new LinearLayout[2];
        private final View[] dots = new View[2];
        private final ImageView still;
        private final TextView stillMsg;
        private android.opengl.GLSurfaceView gl;
        private Bitmap stillBmp;
        private int current;
        private float downX;
        private boolean dragging;

        AnimatedPreview(Class<?> svc, String pref, int scene) {
            this.svc = svc;
            this.scene = scene;
            dlg = new android.app.Dialog(WallpaperActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            final int[] sz = screen();
            int cardH = Math.round(sz[1] * 0.58f), cardW = Math.round(cardH * sz[0] / (float) sz[1]);

            LinearLayout root = new LinearLayout(WallpaperActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(0xFF000000);
            root.setPadding(0, Math.round(28 * dp), 0, Math.round(24 * dp));

            TextView title = new TextView(WallpaperActivity.this);
            title.setText(animatedName(svc, scene));
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(th.titleFace);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setGravity(Gravity.CENTER);
            root.addView(title);

            pages = new FrameLayout(WallpaperActivity.this);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            pl.topMargin = Math.round(16 * dp);
            root.addView(pages, pl);

            // pagina 1: la scena in movimento
            FrameLayout live = card(cardW, cardH);
            if (svc == ShaderWallpaper.class) {
                gl = new android.opengl.GLSurfaceView(WallpaperActivity.this);
                gl.setEGLContextClientVersion(2);
                gl.getHolder().setFixedSize(Math.max(1, cardW / 2), Math.max(1, cardH / 2));
                gl.setRenderer(new PreviewRenderer(WallpaperActivity.this, scene, th.accent));
                live.addView(gl, new FrameLayout.LayoutParams(cardW, cardH));
            } else {
                live.addView(new LivePreview(WallpaperActivity.this, scene, th), new FrameLayout.LayoutParams(cardW, cardH));
            }
            page[0] = pageOf(live, T.t("Animato · schermata home"));

            // pagina 2: lo stesso sfondo fermo
            FrameLayout fixed = card(cardW, cardH);
            still = new ImageView(WallpaperActivity.this);
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fixed.addView(still, new FrameLayout.LayoutParams(cardW, cardH));
            stillMsg = new TextView(WallpaperActivity.this);
            stillMsg.setText(T.t("Preparo l'immagine…"));
            stillMsg.setTextColor(0xFFBBBBBB);
            stillMsg.setGravity(Gravity.CENTER);
            stillMsg.setPadding(Math.round(16 * dp), 0, Math.round(16 * dp), 0);
            fixed.addView(stillMsg, new FrameLayout.LayoutParams(cardW, cardH));
            page[1] = pageOf(fixed, T.t("Fisso · home o schermata di blocco"));
            for (View v : page) pages.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // indicatore delle pagine
            LinearLayout ind = new LinearLayout(WallpaperActivity.this);
            ind.setGravity(Gravity.CENTER);
            ind.setPadding(0, Math.round(10 * dp), 0, Math.round(14 * dp));
            for (int i = 0; i < 2; i++) {
                final int to = i;
                View d = new View(WallpaperActivity.this);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.round(8 * dp), Math.round(8 * dp));
                dl.leftMargin = dl.rightMargin = Math.round(5 * dp);
                d.setOnClickListener(v -> go(to));
                ind.addView(d, dl);
                dots[i] = d;
            }
            root.addView(ind);

            // pulsanti: cambiano con la pagina
            FrameLayout actBox = new FrameLayout(WallpaperActivity.this);
            root.addView(actBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            actions[0] = column();
            actions[0].addView(button(T.t("Imposta sfondo home"), true, () -> {
                dlg.dismiss();
                setAnimatedHome(svc, pref, scene);
            }));
            actions[0].addView(link(T.t("Scorri per la versione fissa →"), () -> go(1)));
            actions[1] = column();
            actions[1].addView(button(T.t("Imposta schermata home"), false, () -> withStill(b ->
                    setStill(b, WallpaperManager.FLAG_SYSTEM, () -> {
                        if (liveGrid != null) ((BaseAdapter) liveGrid.getAdapter()).notifyDataSetChanged();
                    }))));
            actions[1].addView(button(T.t("Imposta schermata di blocco"), true, () -> withStill(b ->
                    setStill(b, WallpaperManager.FLAG_LOCK, null))));
            actions[1].addView(link(T.t("Non cambia? Imposta dalla Galleria"), () -> withStill(this::viaGallery)));
            for (LinearLayout a : actions) actBox.addView(a);

            pages.setOnTouchListener((v, e) -> swipe(e));
            dlg.setContentView(root);
            dlg.setOnDismissListener(d -> {
                if (gl != null) gl.onPause();
                // niente recycle(): l'immagine potrebbe essere ancora in uso da "Imposta…" in background
                stillBmp = null;
            });
            dlg.setOnShowListener(d -> layoutPages(0, false));
            renderStillAsync(sz);
        }

        void show() {
            dlg.show();
            if (gl != null) gl.onResume();
        }

        private FrameLayout card(int w, int h) {
            FrameLayout c = new FrameLayout(WallpaperActivity.this);
            final float radius = 22 * dp;
            c.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            c.setClipToOutline(true);
            c.setBackgroundColor(0xFF111111);
            c.setLayoutParams(new LinearLayout.LayoutParams(w, h));
            return c;
        }

        private View pageOf(View card, String caption) {
            LinearLayout p = new LinearLayout(WallpaperActivity.this);
            p.setOrientation(LinearLayout.VERTICAL);
            p.setGravity(Gravity.CENTER_HORIZONTAL);
            p.addView(card);
            TextView t = new TextView(WallpaperActivity.this);
            t.setText(caption);
            t.setTextColor(0xFF9A9A9E);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Math.round(10 * dp), 0, 0);
            p.addView(t);
            return p;
        }

        private LinearLayout column() {
            LinearLayout l = new LinearLayout(WallpaperActivity.this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setPadding(Math.round(28 * dp), 0, Math.round(28 * dp), 0);
            return l;
        }

        private TextView button(String text, boolean primary, Runnable r) {
            TextView b = new TextView(WallpaperActivity.this);
            b.setText(text);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            b.setTypeface(th.titleFace);
            b.setTextColor(primary ? 0xFF000000 : 0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(26 * dp);
            g.setColor(primary ? 0xFFFFFFFF : 0xFF2A2A2D);
            b.setBackground(g);
            b.setOnClickListener(v -> {
                if (!busy) r.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(52 * dp));
            lp.bottomMargin = Math.round(10 * dp);
            b.setLayoutParams(lp);
            return b;
        }

        private TextView link(String text, Runnable r) {
            TextView t = new TextView(WallpaperActivity.this);
            t.setText(text);
            t.setTextColor(0xFFBBBBBB);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Math.round(40 * dp));
            t.setOnClickListener(v -> r.run());
            return t;
        }

        private void renderStillAsync(int[] sz) {
            exec.execute(() -> {
                Bitmap b = renderStill(svc, scene, Math.max(1, sz[0]), Math.max(1, sz[1]));
                ui.post(() -> {
                    if (!dlg.isShowing() && b != null) {
                        b.recycle();
                        return;
                    }
                    stillBmp = b;
                    if (b != null) {
                        still.setImageBitmap(b);
                        stillMsg.setVisibility(View.GONE);
                    } else {
                        stillMsg.setText(T.t("Questo telefono non riesce a preparare l'immagine fissa"));
                    }
                });
            });
        }

        private void withStill(java.util.function.Consumer<Bitmap> use) {
            if (stillBmp == null) {
                toast(T.t("Preparo l'immagine…"));
                return;
            }
            use.accept(stillBmp);
        }

        private void viaGallery(Bitmap b) {
            busy = true;
            exec.execute(() -> {
                Uri u = saveToGallery(b, scene);
                ui.post(() -> {
                    busy = false;
                    if (u == null) {
                        toast(T.t("Impossibile salvare in Galleria"));
                        return;
                    }
                    Intent i = Intent.createChooser(new Intent(Intent.ACTION_ATTACH_DATA)
                            .setDataAndType(u, "image/jpeg").putExtra("mimeType", "image/jpeg")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), T.t("Imposta come sfondo"));
                    try {
                        startActivity(i);
                        toast(T.t("Salvata in Galleria, album \"Dot Launcher\": scegli \"Schermata di blocco\""));
                    } catch (Exception e) {
                        toast(T.t("Salvata in Galleria, album \"Dot Launcher\": impostala da lì"));
                    }
                });
            });
        }

        private boolean swipe(android.view.MotionEvent e) {
            float w = pages.getWidth();
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    dragging = true;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!dragging) return false;
                    float dx = e.getRawX() - downX;
                    if ((current == 0 && dx > 0) || (current == 1 && dx < 0)) dx *= 0.25f; // bordo elastico
                    page[0].setTranslationX(-current * w + dx);
                    page[1].setTranslationX((1 - current) * w + dx);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    float d = e.getRawX() - downX;
                    int to = current;
                    if (d < -w * 0.18f) to = 1;
                    else if (d > w * 0.18f) to = 0;
                    go(to);
                    return true;
            }
            return false;
        }

        private void go(int to) {
            current = to;
            layoutPages(to, true);
        }

        private void layoutPages(int to, boolean anim) {
            float w = pages.getWidth() > 0 ? pages.getWidth() : screen()[0];
            for (int i = 0; i < 2; i++) {
                float x = (i - to) * w;
                if (anim) page[i].animate().translationX(x).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).start();
                else page[i].setTranslationX(x);
                actions[i].setVisibility(i == to ? View.VISIBLE : View.GONE);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(i == to ? 0xFFFFFFFF : 0xFF4A4A4E);
                dots[i].setBackground(g);
            }
            // la scena in movimento lavora solo quando si vede
            if (gl != null) {
                if (to == 0) gl.onResume();
                else gl.onPause();
            }
        }
    }

    /** Disegna la scena realistica nell'anteprima (circa 25 fotogrammi al secondo). */
    private static final class PreviewRenderer implements android.opengl.GLSurfaceView.Renderer {
        private final android.content.Context app;
        private final int scene;
        private final float[] accent;
        private final java.nio.FloatBuffer quad = ShaderWallpaper.quad();
        private final long start = System.nanoTime();
        private int prog, w = 1, h = 1;
        private long last;

        PreviewRenderer(android.content.Context c, int scene, int a) {
            app = c.getApplicationContext();
            this.scene = scene;
            accent = new float[]{((a >> 16) & 255) / 255f, ((a >> 8) & 255) / 255f, (a & 255) / 255f};
        }

        @Override
        public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 g, javax.microedition.khronos.egl.EGLConfig c) {
            prog = 0;
        }

        @Override
        public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 g, int width, int height) {
            w = width;
            h = height;
        }

        @Override
        public void onDrawFrame(javax.microedition.khronos.opengles.GL10 g) {
            // limite ai fotogrammi: l'anteprima non deve scaldare il telefono
            long now = System.nanoTime(), wait = 40_000_000L - (now - last);
            if (last != 0 && wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000L);
                } catch (InterruptedException ignored) {
                }
            }
            last = System.nanoTime();
            if (prog == 0) prog = ShaderWallpaper.buildProgram(app, scene);
            ShaderWallpaper.draw(prog, w, h, ((System.nanoTime() - start) / 1e9f) + 20f, 0f, accent, quad);
        }
    }

    /** Salva il fotogramma in Galleria (Immagini/Dot Launcher). Su Android 10+ non servono permessi. */
    private Uri saveToGallery(Bitmap b, int scene) {
        if (android.os.Build.VERSION.SDK_INT < 29) return null;
        try {
            android.content.ContentResolver cr = getContentResolver();
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "dot-blocco-" + scene + "-" + System.currentTimeMillis() + ".jpg");
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dot Launcher");
            v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (java.io.OutputStream out = cr.openOutputStream(uri)) {
                if (out == null || !b.compress(Bitmap.CompressFormat.JPEG, 94, out)) {
                    cr.delete(uri, null, null);
                    return null;
                }
            }
            v.clear();
            v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- i miei sfondi: immagini caricate dall'utente ----------

    private java.io.File myDir() {
        java.io.File d = new java.io.File(getFilesDir(), "mywalls");
        if (!d.isDirectory() && !d.mkdirs()) android.util.Log.w("DotWallpaper", "cartella degli sfondi non creata");
        return d;
    }

    /** Immagini caricate, dalla più recente. */
    private java.io.File[] myFiles() {
        java.io.File[] f = myDir().listFiles((dir, name) -> name.endsWith(".jpg"));
        if (f == null) return new java.io.File[0];
        java.util.Arrays.sort(f, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return f;
    }

    private void pickMine() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, T.t("Scegli le immagini")), REQ_MY);
        } catch (Exception e) {
            toast(T.t("Nessuna galleria disponibile"));
        }
    }

    /**
     * Copia le immagini scelte tra i miei sfondi, ridotte a una misura sensata per lo schermo
     * (al massimo una volta e mezzo la sua altezza sul lato lungo): restano nitide e non occupano troppo.
     */
    private void importMine(java.util.List<Uri> uris) {
        final int[] sz = screen();
        final int maxSide = Math.max(1600, Math.round(Math.max(sz[0], sz[1]) * 1.5f));
        busy = true;
        toast(T.t("Carico le immagini…"));
        exec.execute(() -> {
            java.util.List<java.io.File> saved = new java.util.ArrayList<>();
            long now = System.currentTimeMillis();
            for (int k = 0; k < uris.size(); k++) {
                try {
                    Bitmap b = decode(uris.get(k), maxSide);
                    if (b == null) continue;
                    float s = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
                    if (s < 1f) {
                        Bitmap r = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * s), Math.round(b.getHeight() * s), true);
                        if (r != b) b.recycle();
                        b = r;
                    }
                    java.io.File f = new java.io.File(myDir(), "w" + (now + k) + ".jpg");
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                        b.compress(Bitmap.CompressFormat.JPEG, 93, out);
                    }
                    b.recycle();
                    saved.add(f);
                } catch (Exception ignored) {
                }
            }
            ui.post(() -> {
                busy = false;
                if (myGrid != null) ((BaseAdapter) myGrid.getAdapter()).notifyDataSetChanged();
                if (saved.isEmpty()) toast(T.t("Impossibile leggere le immagini"));
                else if (saved.size() == 1) new CropEditor(saved.get(0)).show();
                else toast(saved.size() + " " + T.t("immagini aggiunte ai tuoi sfondi"));
            });
        });
    }

    private Bitmap decodeFile(java.io.File f, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o2);
    }

    private void deleteMine(java.io.File f) {
        Sheet.confirm(this, th, T.t("Elimina dai miei sfondi"), T.t("L'immagine resta nella tua galleria: si toglie solo da qui."),
                T.t("Elimina"), () -> {
                    if (!f.delete()) toast(T.t("Impossibile eliminare"));
                    thumbs.remove(f.getName());
                    if (myGrid != null) ((BaseAdapter) myGrid.getAdapter()).notifyDataSetChanged();
                });
    }

    private final java.util.Map<String, Bitmap> thumbs = new java.util.HashMap<>();

    private class MyAdapter extends BaseAdapter {
        private java.io.File[] files = myFiles();

        @Override
        public void notifyDataSetChanged() {
            files = myFiles();
            super.notifyDataSetChanged();
        }

        @Override public int getCount() { return files.length + 1; }
        @Override public Object getItem(int i) { return i == 0 ? null : files[i - 1]; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (pos == 0) {
                TextView plus = new TextView(WallpaperActivity.this);
                plus.setText("+");
                plus.setTextColor(th.onTile);
                plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
                plus.setGravity(Gravity.CENTER);
                return card(new ColorDrawable(Theme.alpha(th.onTile, th.light ? 0.07f : 0.1f)), plus,
                        T.t("Carica un'immagine"), false, cardH, th.onTile);
            }
            java.io.File f = files[pos - 1];
            Bitmap b = thumbs.get(f.getName());
            if (b == null) {
                b = decodeFile(f, cardH);
                if (b != null) thumbs.put(f.getName(), b);
            }
            ImageView iv = new ImageView(WallpaperActivity.this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (b != null) iv.setImageBitmap(b);
            boolean inUse = prefs.getBoolean("wall", false) && prefs.getInt("wallStyle", 0) == -1
                    && f.getName().equals(prefs.getString("myWall", ""));
            return card(null, iv, "", inUse, cardH, 0xFFFFFFFF);
        }
    }

    /** Schermata per sistemare un'immagine sullo schermo (riempi o adatta, sposta, ingrandisci) e applicarla. */
    private final class CropEditor {
        private final java.io.File file;
        private final android.app.Dialog dlg;
        private CropView crop;
        private int bgMode; // 0 automatico (colore dei bordi), 1 nero, 2 bianco
        private int edge = 0xFF000000;

        CropEditor(java.io.File file) {
            this.file = file;
            dlg = new android.app.Dialog(WallpaperActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        }

        void show() {
            final int[] sz = screen();
            Bitmap b = decodeFile(file, Math.round(Math.max(sz[0], sz[1]) * 1.5f));
            if (b == null) {
                toast(T.t("Impossibile leggere l'immagine"));
                return;
            }
            edge = CropView.edgeColor(b);
            crop = new CropView(WallpaperActivity.this, b);
            crop.setBackgroundFill(edge);

            LinearLayout root = new LinearLayout(WallpaperActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setBackgroundColor(0xFF000000);
            root.setPadding(Math.round(24 * dp), Math.round(28 * dp), Math.round(24 * dp), Math.round(20 * dp));

            TextView title = new TextView(WallpaperActivity.this);
            title.setText(T.t("Adatta allo schermo"));
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(th.titleFace);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            root.addView(title);

            int cardH = Math.round(sz[1] * 0.52f), cardW = Math.round(cardH * sz[0] / (float) sz[1]);
            FrameLayout frame = new FrameLayout(WallpaperActivity.this);
            final float radius = 22 * dp;
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            frame.setClipToOutline(true);
            frame.addView(crop, new FrameLayout.LayoutParams(cardW, cardH));
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(cardW, cardH);
            fl.topMargin = Math.round(14 * dp);
            root.addView(frame, fl);

            TextView hint = new TextView(WallpaperActivity.this);
            hint.setText(T.t("Sposta con un dito, ingrandisci con due, doppio tocco per ricominciare"));
            hint.setTextColor(0xFF9A9A9E);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, Math.round(8 * dp), 0, Math.round(10 * dp));
            root.addView(hint);

            final TextView[] fitChips = new TextView[2];
            LinearLayout fitRow = chipRow();
            String[] fitNames = {T.t("Riempi"), T.t("Adatta")};
            final TextView[] bgChips = new TextView[3];
            LinearLayout bgRow = chipRow();
            String[] bgNames = {T.t("Colore dei bordi"), T.t("Nero"), T.t("Bianco")};
            for (int i = 0; i < 2; i++) {
                final int k = i;
                fitChips[i] = chip(fitNames[i], () -> {
                    crop.setFill(k == 0);
                    paint(fitChips, k);
                    bgRow.setVisibility(k == 1 ? View.VISIBLE : View.GONE);
                });
                fitRow.addView(fitChips[i]);
            }
            for (int i = 0; i < 3; i++) {
                final int k = i;
                bgChips[i] = chip(bgNames[i], () -> {
                    bgMode = k;
                    crop.setBackgroundFill(k == 0 ? edge : k == 1 ? 0xFF000000 : 0xFFFFFFFF);
                    paint(bgChips, k);
                });
                bgRow.addView(bgChips[i]);
            }
            paint(fitChips, 0);
            paint(bgChips, 0);
            bgRow.setVisibility(View.GONE);
            root.addView(fitRow);
            root.addView(bgRow);

            View space = new View(WallpaperActivity.this);
            root.addView(space, new LinearLayout.LayoutParams(1, 0, 1f));

            TextView apply = new TextView(WallpaperActivity.this);
            apply.setText(T.t("Applica sfondo"));
            apply.setGravity(Gravity.CENTER);
            apply.setTextColor(0xFF000000);
            apply.setTypeface(th.titleFace);
            apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(26 * dp);
            g.setColor(0xFFFFFFFF);
            apply.setBackground(g);
            apply.setOnClickListener(v -> askTarget(this::apply));
            root.addView(apply, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(52 * dp)));

            TextView del = new TextView(WallpaperActivity.this);
            del.setText(T.t("Elimina dai miei sfondi"));
            del.setTextColor(0xFFBBBBBB);
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            del.setGravity(Gravity.CENTER);
            del.setMinHeight(Math.round(44 * dp));
            del.setOnClickListener(v -> {
                dlg.dismiss();
                deleteMine(file);
            });
            root.addView(del, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            dlg.setContentView(root);
            dlg.show();
        }

        private LinearLayout chipRow() {
            LinearLayout r = new LinearLayout(WallpaperActivity.this);
            r.setGravity(Gravity.CENTER);
            r.setPadding(0, Math.round(4 * dp), 0, Math.round(4 * dp));
            return r;
        }

        private TextView chip(String text, Runnable r) {
            TextView t = new TextView(WallpaperActivity.this);
            t.setText(text);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Math.round(36 * dp));
            t.setPadding(Math.round(14 * dp), 0, Math.round(14 * dp), 0);
            t.setOnClickListener(v -> r.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = lp.rightMargin = Math.round(4 * dp);
            t.setLayoutParams(lp);
            return t;
        }

        private void paint(TextView[] chips, int sel) {
            for (int i = 0; i < chips.length; i++) {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(18 * dp);
                g.setColor(i == sel ? 0xFFFFFFFF : 0xFF2A2A2D);
                chips[i].setBackground(g);
                chips[i].setTextColor(i == sel ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        private void apply(int which) {
            final int[] sz = screen();
            final Bitmap out = crop.render(sz[0], sz[1]);
            final boolean home = (which & WallpaperManager.FLAG_SYSTEM) != 0;
            busy = true;
            toast(T.t("Applico lo sfondo…"));
            exec.execute(() -> {
                try {
                    WallpaperManager.getInstance(WallpaperActivity.this).setBitmap(out, null, true, which);
                    if (home) {
                        Glass.save(WallpaperActivity.this, WallpaperGen.cover(out, sz[0] / 4, sz[1] / 4));
                        prefs.edit().putInt("wallStyle", -1).putString("myWall", file.getName()).apply();
                        ui.post(dlg::dismiss);
                    }
                    done(which);
                } catch (Exception e) {
                    fail();
                }
            });
        }
    }

    private Bitmap assetBitmap(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private class LiveAdapter extends BaseAdapter {
        private final Bitmap[] shots = new Bitmap[ShaderWallpaper.FILES.length];

        @Override public int getCount() { return ShaderWallpaper.FILES.length + LiveScenes.NAMES.length; }
        @Override public Object getItem(int i) { return i; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            int real = ShaderWallpaper.FILES.length;
            if (pos < real) {
                if (shots[pos] == null) shots[pos] = assetBitmap("live/" + ShaderWallpaper.FILES[pos] + ".jpg");
                ImageView iv = new ImageView(WallpaperActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (shots[pos] != null) iv.setImageBitmap(shots[pos]);
                boolean inUse = liveActive(ShaderWallpaper.class) && prefs.getInt(ShaderWallpaper.PREF, 0) == pos;
                return card(null, iv, T.t(ShaderWallpaper.NAMES[pos]), inUse, cardH, 0xFFFFFFFF);
            }
            int scene = pos - real;
            LivePreview pv = new LivePreview(WallpaperActivity.this, scene, th);
            boolean inUse = liveActive(DotLiveWallpaper.class) && prefs.getInt(DotLiveWallpaper.PREF, 0) == scene;
            return card(null, pv, T.t(LiveScenes.NAMES[scene]) + " · " + T.t("puntini"), inUse, cardH, 0xFFFFFFFF);
        }
    }

    /** Scheda di uno sfondo: anteprima (colore o vista), nome in basso e bordo se è in uso. */
    private View card(android.graphics.drawable.Drawable bg, View content, String name, boolean inUse, int height, int textColor) {
        FrameLayout card = new FrameLayout(this);
        card.setLayoutParams(new android.widget.AbsListView.LayoutParams(cardW, height));
        final float radius = 20 * dp;
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        if (bg != null) card.setBackground(bg);
        if (content != null) card.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (textColor == 0xFFFFFFFF) {
            View scrim = new View(this);
            scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x59000000, 0xB3000000}));
            card.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(84 * dp), Gravity.BOTTOM));
        }
        TextView label = new TextView(this);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(th.titleFace);
        label.setPadding(Math.round(12 * dp), 0, Math.round(12 * dp), Math.round(12 * dp));
        label.setText(inUse ? name + "  ·  " + T.t("in uso") : name);
        label.setTextColor(textColor);
        if (textColor == 0xFFFFFFFF) label.setShadowLayer(4 * dp, 0, 0, 0x99000000);
        card.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        if (inUse) {
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setCornerRadius(radius);
            ring.setStroke(Math.round(3 * dp), th.accent);
            card.setForeground(ring);
        }
        card.setContentDescription(label.getText());
        return card;
    }

    /** Anteprima animata di una scena: si muove solo mentre è sullo schermo, a circa 20 fotogrammi al secondo. */
    static final class LivePreview extends View {
        private final LiveScenes scenes = new LiveScenes();
        private final int scene;
        private final long start = System.nanoTime();
        private final float dp;

        LivePreview(android.content.Context c, int scene, Theme th) {
            super(c);
            this.scene = scene;
            scenes.accent = th.accent;
            dp = c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float t = (System.nanoTime() - start) / 1e9f;
            scenes.draw(cv, scene, getWidth(), getHeight(), t, 5 * dp, 0);
            if (isShown()) postInvalidateDelayed(50);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, T.t(s), Toast.LENGTH_SHORT).show();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return WallpaperGen.NAMES.length + 1; }
        @Override public Object getItem(int i) { return i; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            FrameLayout card = new FrameLayout(WallpaperActivity.this);
            card.setLayoutParams(new android.widget.AbsListView.LayoutParams(cardW, cardH));
            final float radius = 20 * dp;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);
            TextView label = new TextView(WallpaperActivity.this);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTypeface(th.titleFace);
            label.setPadding(Math.round(12 * dp), 0, Math.round(12 * dp), Math.round(12 * dp));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);

            if (pos == 0) {
                card.setBackgroundColor(th.light ? 0xFFFFFFFF : 0xFF1C1C1E);
                ImageView plus = new ImageView(WallpaperActivity.this);
                Bitmap pb = Bitmap.createBitmap(cardW / 3, cardW / 3, Bitmap.Config.ARGB_8888);
                Draw.icon(new Canvas(pb), Draw.PLUS, pb.getWidth() / 2f, pb.getHeight() / 2f,
                        pb.getWidth(), new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                        th.onTile, th.accent);
                plus.setImageBitmap(pb);
                card.addView(plus, new FrameLayout.LayoutParams(cardW / 3, cardW / 3, Gravity.CENTER));
                label.setText(T.t("Dalla galleria"));
                label.setTextColor(th.onTile);
            } else {
                ImageView iv = new ImageView(WallpaperActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap b = previews[pos - 1];
                if (b != null) iv.setImageBitmap(b);
                else iv.setBackgroundColor(th.tile);
                card.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // sfumatura scura in fondo: il nome si legge anche sugli sfondi chiari
                View scrim = new View(WallpaperActivity.this);
                scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0x00000000, 0x59000000, 0xB3000000}));
                card.addView(scrim, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.round(84 * dp), Gravity.BOTTOM));
                boolean inUse = prefs.getBoolean("wall", false) && prefs.getInt("wallStyle", -1) == pos - 1;
                label.setText(inUse ? WallpaperGen.NAMES[pos - 1] + "  ·  " + T.t("in uso") : WallpaperGen.NAMES[pos - 1]);
                label.setTextColor(0xFFFFFFFF);
                label.setShadowLayer(4 * dp, 0, 0, 0x99000000);
                if (inUse) {
                    android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
                    ring.setCornerRadius(radius);
                    ring.setStroke(Math.round(3 * dp), th.accent);
                    card.setForeground(ring);
                }
                card.setContentDescription(label.getText());
            }
            card.addView(label, lp);
            return card;
        }
    }
}
