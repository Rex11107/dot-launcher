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
    private GridView monoGrid, fixedGrid, liveGrid;
    private Class<?> pendingSvc;   // sfondo animato in attesa della conferma di Android
    private int pendingScene;
    private boolean pendingLock;

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

        // tre schede: monocromatici, fissi e animati
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, Math.round(16 * dp), 0, Math.round(12 * dp));
        root.addView(tabs);
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

        final View[] pages = {monoGrid, fixedPage, liveGrid};
        final String[] tabNames = {T.t("Monocromatici"), T.t("Fissi"), T.t("Animati")};
        final TextView[] tabViews = new TextView[3];
        for (int t = 0; t < 3; t++) {
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
        selectTab(Math.max(0, Math.min(2, prefs.getInt("wallTab", 1))), pages, tabViews);
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
            for (GridView g : new GridView[]{monoGrid, fixedGrid, liveGrid}) g.setPadding(0, 0, 0, Math.round(24 * dp) + r[3]);
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
        String[] items = {T.t("Schermata home"), T.t("Home e schermata di blocco")};
        Sheet.list(this, th, T.t("Applica sfondo"), items, -1, w ->
                t.go(w == 0 ? WallpaperManager.FLAG_SYSTEM
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
                Glass.save(this, b);
                prefs.edit().putInt("wallStyle", style).apply(); // per segnare lo sfondo in uso
                done();
            } catch (Exception e) {
                fail();
            }
        });
    }

    private void done() {
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
            // tornati dalla conferma di Android: se lo sfondo è attivo e serve, si prepara la schermata di blocco
            Class<?> svc = pendingSvc;
            pendingSvc = null;
            if (svc != null && liveActive(svc)) {
                if (pendingLock) lockStill(svc, pendingScene);
                else toast(T.t("Sfondo animato applicato"));
            }
            if (liveGrid != null) ((BaseAdapter) liveGrid.getAdapter()).notifyDataSetChanged();
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
                    Bitmap src = decode(uri, Math.max(sz[0], sz[1]) / 4);
                    if (src != null) Glass.save(this, WallpaperGen.cover(src, sz[0] / 4, sz[1] / 4));
                } else {
                    Bitmap src = decode(uri, Math.max(sz[0], sz[1]) / 2);
                    if (src == null) throw new Exception("decode");
                    Bitmap b = mode == 3 ? WallpaperGen.ribbed(src, sz[0], sz[1], System.nanoTime())
                            : WallpaperGen.dotify(src, sz[0], sz[1], mode == 2);
                    wm.setBitmap(b, null, true, which);
                    Glass.save(this, b);
                }
                prefs.edit().putInt("wallStyle", -1).apply(); // foto: nessuno sfondo generato in uso
                done();
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
                Glass.save(this, b);
                prefs.edit().putInt("wallStyle", -100 - i).apply(); // per segnare la tinta in uso
                done();
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
     * Sfondo animato. Android non lascia impostarlo in silenzio: la prima volta compare la sua conferma.
     * Molti telefoni (Honor compreso) non mostrano gli sfondi animati sulla schermata di blocco, quindi lì
     * si può mettere un fotogramma fisso della stessa scena.
     */
    private void applyAnimated(Class<?> svc, String pref, int scene) {
        if (busy) return;
        String[] items = {T.t("Solo schermata home (animato)"), T.t("Home animata e blocco con la stessa immagine fissa")};
        Sheet.list(this, th, animatedName(svc, scene), items, -1, which -> {
            // la home deve lasciar vedere lo sfondo del telefono, altrimenti lo coprirebbe con la tinta del tema
            prefs.edit().putInt(pref, scene).putBoolean("wall", true).apply();
            boolean lock = which == 1;
            if (liveActive(svc)) {
                if (lock) lockStill(svc, scene);
                else toast(T.t("Sfondo animato: ") + animatedName(svc, scene));
                if (liveGrid != null) ((BaseAdapter) liveGrid.getAdapter()).notifyDataSetChanged();
                return;
            }
            pendingSvc = svc;
            pendingScene = scene;
            pendingLock = lock;
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
        });
    }

    /** Fotogramma fisso della scena sulla schermata di blocco (la home resta animata). */
    private void lockStill(Class<?> svc, int scene) {
        final int[] sz = screen();
        // risoluzione piena: è un solo fotogramma, e la schermata di blocco si guarda da vicino
        final int w = Math.max(1, sz[0]), h = Math.max(1, sz[1]);
        final int accent = th.accent;
        busy = true;
        toast(T.t("Preparo la schermata di blocco…"));
        exec.execute(() -> {
            Bitmap b = null;
            //noinspection ResultOfMethodCallIgnored
            ShareProvider.file(this, ShareProvider.LOCK).delete(); // mai un'immagine vecchia
            try {
                if (svc == ShaderWallpaper.class) {
                    b = ShaderWallpaper.still(this, scene, w, h, accent);
                } else {
                    b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    LiveScenes sc = new LiveScenes();
                    sc.accent = accent;
                    sc.ghost = prefs.getBoolean("ghost", true);
                    sc.draw(new Canvas(b), scene, w, h, 20f, 9 * dp, 0);
                }
                if (b == null) throw new IllegalStateException();
                // copia dell'immagine, per l'"Imposta come sfondo" del telefono
                java.io.File f = ShareProvider.file(this, ShareProvider.LOCK);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.JPEG, 92, out);
                }
                boolean set = false;
                if (!systemLock()) {
                    WallpaperManager wm = WallpaperManager.getInstance(this);
                    int before = wm.getWallpaperId(WallpaperManager.FLAG_LOCK);
                    wm.setBitmap(b, null, true, WallpaperManager.FLAG_LOCK);
                    set = wm.getWallpaperId(WallpaperManager.FLAG_LOCK) != before;
                }
                final boolean ok = set;
                ui.post(() -> {
                    busy = false;
                    if (ok) toast(T.t("Home animata, schermata di blocco aggiornata"));
                    else openSystemSetter();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy = false;
                    // ultima strada: l'immagine c'è già, la imposta il telefono
                    if (ShareProvider.file(this, ShareProvider.LOCK).exists()) openSystemSetter();
                    else toast(T.t("La schermata di blocco non si può cambiare su questo telefono"));
                });
            } finally {
                if (b != null) b.recycle();
            }
        });
    }

    /**
     * Honor e Huawei gestiscono la schermata di blocco con i loro temi: un'immagine impostata dalle app
     * viene accettata ma non mostrata. Lì la si passa all'"Imposta come sfondo" del telefono.
     */
    private static boolean systemLock() {
        String m = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        return m.contains("honor") || m.contains("huawei");
    }

    private void openSystemSetter() {
        Uri uri = ShareProvider.uri(ShareProvider.LOCK);
        Intent i = null;
        try {
            i = WallpaperManager.getInstance(this).getCropAndSetWallpaperIntent(uri);
        } catch (Exception ignored) {
        }
        if (i == null) {
            i = Intent.createChooser(new Intent(Intent.ACTION_ATTACH_DATA)
                    .setDataAndType(uri, "image/jpeg").putExtra("mimeType", "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), T.t("Imposta come sfondo"));
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
            toast(T.t("Scegli \"Schermata di blocco\": la home resta animata"));
        } catch (Exception e) {
            toast(T.t("La schermata di blocco non si può cambiare su questo telefono"));
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
