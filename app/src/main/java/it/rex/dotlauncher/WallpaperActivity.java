package it.rex.dotlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
        title.setText("SFONDI.");
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Tocca per applicare · tieni premuto per una nuova variante");
        hint.setTextColor(th.sub);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Math.round(10 * dp);
        hl.bottomMargin = Math.round(16 * dp);
        root.addView(hint, hl);

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
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        final int padTop = Math.round(24 * dp);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int[] r = SystemBars.read(in);
            root.setPadding(pad + r[0], padTop + r[1], pad + r[2], 0);
            grid.setPadding(0, 0, 0, Math.round(24 * dp) + r[3]);
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

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, th.light
                ? android.R.style.Theme_Material_Light_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert);
    }

    private void askTarget(Target t) {
        if (busy) return;
        String[] items = {"Schermata home", "Home e schermata di blocco"};
        dialog().setTitle("Applica sfondo").setItems(items, (d, w) ->
                t.go(w == 0 ? WallpaperManager.FLAG_SYSTEM
                        : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK)).show();
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
        toast("Applico lo sfondo…");
        exec.execute(() -> {
            try {
                Bitmap b = WallpaperGen.make(style, sz[0], sz[1], seed);
                WallpaperManager.getInstance(this).setBitmap(b, null, true, which);
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
            toast("Sfondo applicato");
            finish();
        });
    }

    private void fail() {
        ui.post(() -> {
            busy = false;
            toast("Impossibile applicare lo sfondo");
        });
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Scegli una foto"), REQ_IMAGE);
        } catch (Exception e) {
            toast("Nessuna galleria disponibile");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        String[] modes = {"Originale", "A puntini (colori)", "A puntini (bianco e nero)"};
        dialog().setTitle("Come vuoi la foto?").setItems(modes, (d, mode) ->
                askTarget(which -> applyPhoto(uri, mode, which))).show();
    }

    private void applyPhoto(Uri uri, int mode, int which) {
        final int[] sz = screen();
        busy = true;
        toast("Applico lo sfondo…");
        exec.execute(() -> {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(this);
                if (mode == 0) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        wm.setStream(in, null, true, which);
                    }
                } else {
                    Bitmap src = decode(uri, Math.max(sz[0], sz[1]) / 2);
                    if (src == null) throw new Exception("decode");
                    Bitmap b = WallpaperGen.dotify(src, sz[0], sz[1], mode == 2);
                    wm.setBitmap(b, null, true, which);
                }
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
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
                label.setText("Dalla galleria");
                label.setTextColor(th.onTile);
            } else {
                ImageView iv = new ImageView(WallpaperActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap b = previews[pos - 1];
                if (b != null) iv.setImageBitmap(b);
                else iv.setBackgroundColor(th.tile);
                card.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                label.setText(WallpaperGen.NAMES[pos - 1]);
                label.setTextColor(0xFFFFFFFF);
                label.setShadowLayer(6 * dp, 0, 0, 0xCC000000);
            }
            card.addView(label, lp);
            return card;
        }
    }
}
