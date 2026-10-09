package it.rex.dotlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity implements TileGrid.Host, AppTile.Source {
    private static final int REQ_BIND = 1;
    private static final int REQ_CONFIG = 2;
    private static final int REQ_LOC = 3;
    private static final int REQ_EXPORT = 4;
    private static final int REQ_IMPORT = 5;
    private static final int HOST_ID = 0x0D07;
    private static final int MAX_DOCK = 5;

    private SharedPreferences prefs;
    private Theme th;
    private String themeSig;
    private float dp;
    private boolean h24;

    private FrameLayout root;
    private LinearLayout homeBox;
    private SystemBars.Scrim scrim;
    private boolean noLimits;
    private Pager pager;
    private int gestureBottom;
    private int navBottom;
    private IndicatorView indicator;
    private LinearLayout dock;
    private final List<TileGrid> grids = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private int pages = 2;

    private LinearLayout drawer;
    private EditText search;
    private GridView grid;
    private AppAdapter adapter;
    private boolean drawerOpen;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final Map<String, AppEntry> appsByKey = new HashMap<>();
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private LauncherApps launcherApps;
    private AppWidgetManager awm;
    private WidgetHost host;
    private int pendingWidgetId = -1;
    private volatile IconPacks iconPack;
    private int pendingPage, pendingCol, pendingRow;

    private final ExecutorService appExec = Executors.newSingleThreadExecutor();
    private final ExecutorService netExec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private GestureDetector gestures;
    private boolean receiversOn;
    private boolean fitted;

    static class AppEntry {
        String label;
        String key;
        ComponentName component;
        UserHandle user;
        Drawable icon;
    }

    // ---------- ricevitori ----------

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateAlarm();
            refreshTiles(null);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level >= 0 && scale > 0) State.battery = Math.round(level * 100f / scale);
            State.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            refreshTiles("battery");
        }
    };

    private final LauncherApps.Callback appsCallback = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String p, UserHandle u) { loadApps(); }
        @Override public void onPackageAdded(String p, UserHandle u) { loadApps(); }
        @Override public void onPackageChanged(String p, UserHandle u) { loadApps(); }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { loadApps(); }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { loadApps(); }
    };

    // ---------- ciclo di vita ----------

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("dot", MODE_PRIVATE);
        migrateFromV1();
        th = Theme.build(this, prefs);
        themeSig = Theme.signature(prefs);
        h24 = prefs.getBoolean("h24", DateFormat.is24HourFormat(this));
        if (saved != null) {
            pendingWidgetId = saved.getInt("pw", -1);
            pendingPage = saved.getInt("pp");
            pendingCol = saved.getInt("pc");
            pendingRow = saved.getInt("pr");
        }

        launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        awm = AppWidgetManager.getInstance(this);
        host = new WidgetHost(getApplicationContext(), HOST_ID);

        State.parseWeather(prefs.getString("wjson", ""), prefs.getString("wcity", ""));
        loadLayout();

        buildUi();
        setContentView(root);
        applyWindow();

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || TileGrid.anyDragging) return false;
                // la striscia in basso appartiene alla gesture "home" del sistema
                if (root != null && e1.getY() > root.getHeight() - gestureBottom - px(12)) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < Math.abs(dx) * 1.3f) return false;
                float min = 70 * dp;
                if (!drawerOpen) {
                    if (dy < -min) openDrawer(false);
                    else if (dy > min) expandNotifications();
                } else if (dy > min && !grid.canScrollVertically(-1)) {
                    closeDrawer();
                }
                return false;
            }
        });

        launcherApps.registerCallback(appsCallback, ui);
        loadApps();
        buildPages();
        root.post(this::fitToScreen);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pw", pendingWidgetId);
        out.putInt("pp", pendingPage);
        out.putInt("pc", pendingCol);
        out.putInt("pr", pendingRow);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!themeSig.equals(Theme.signature(prefs))) {
            recreate();
            return;
        }
        try {
            host.startListening();
        } catch (Exception ignored) {
        }
        if (!receiversOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            f.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
            registerReceiver(timeReceiver, f);
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            receiversOn = true;
        }
        updateAlarm();
        refreshTiles(null);
        maybeRefreshWeather();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            host.stopListening();
        } catch (Exception ignored) {
        }
        if (receiversOn) {
            unregisterReceiver(timeReceiver);
            unregisterReceiver(batteryReceiver);
            receiversOn = false;
        }
        // uscendo verso un'app il cassetto si chiude subito: al ritorno la home è pulita
        if (drawerOpen) hideDrawerNow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        launcherApps.unregisterCallback(appsCallback);
        appExec.shutdownNow();
        netExec.shutdownNow();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if ("auto".equals(prefs.getString("mode", "dark"))) recreate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Solo se eravamo già sulla home (Home premuto di nuovo): chiudi il cassetto o torna alla prima pagina.
        // Se invece si sta tornando da un'app, la home resta com'era, senza animazioni.
        boolean alreadyHome = hasWindowFocus();
        if (drawerOpen) {
            if (alreadyHome) closeDrawer();
            else hideDrawerNow();
        } else if (alreadyHome && pager != null && pager.getCurrent() != 0) {
            pager.snapTo(0);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // ---------- interfaccia ----------

    private int px(float v) {
        return Math.round(v * dp);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        scrim = new SystemBars.Scrim(this);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        homeBox = new LinearLayout(this);
        LinearLayout home = homeBox;
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(0, px(32), 0, px(6));

        pager = new Pager(this);
        pager.setListener(p -> {
            indicator.setCurrent(p);
            // il vetro smerigliato dipende dalla posizione sullo schermo: si ridisegna a pagina ferma
            if (th.glass) ui.postDelayed(() -> refreshTiles(null), 450);
        });
        home.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        indicator = new IndicatorView(this);
        indicator.setOnClickListener(v -> openDrawer(false));
        home.addView(indicator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(22)));

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        home.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(76)));

        root.addView(home, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildDrawer();
        root.addView(drawer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void applyWindow() {
        if (th.wall) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            root.setBackgroundColor(th.scrim);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(th.bg));
            root.setBackgroundColor(th.bg);
        }
        // Barre trasparenti: sotto si vede il colore del tema o lo sfondo
        SystemBars.edgeToEdge(getWindow(), th.darkIcons);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int[] r = SystemBars.read(in);
            if (noLimits && r[1] == 0) {
                // piano di riserva: dimensioni delle barre lette dalle risorse di sistema
                r[1] = SystemBars.systemDimen(this, "status_bar_height");
                r[3] = SystemBars.systemDimen(this, "navigation_bar_height");
                r[6] = r[3];
            }
            if (th.wall) scrim.set(r[1], r[3], th.light ? 0x99FFFFFF : 0x80000000);
            homeBox.setPadding(r[0], r[1] + px(8), r[2], r[3] + px(6));
            drawer.setPadding(px(16) + r[0], r[1] + px(20), px(16) + r[2], r[7]);
            grid.setPadding(0, 0, 0, px(24) + (r[7] > 0 ? 0 : r[3]));
            gestureBottom = r[6];
            navBottom = r[3];
            pager.setEdgeGuard(r[4], r[5]);
            return in;
        });
        root.requestApplyInsets();
        // Se MagicOS non ci lascia disegnare sotto la barra di stato, si usa l'opzione di riserva
        root.post(() -> {
            int[] loc = new int[2];
            root.getLocationOnScreen(loc);
            if (loc[1] > 0 && !noLimits) {
                noLimits = true;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                root.requestApplyInsets();
            }
        });
    }

    private void buildDrawer() {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(th.drawerBg);
        drawer.setPadding(px(16), px(28), px(16), 0);
        drawer.setVisibility(View.GONE);
        drawer.setClickable(true);

        DotTextView title = new DotTextView(this);
        title.setPitch(3.6f * dp);
        title.setColor(th.onTile);
        title.setText("APP.");
        title.setAccent(".", th.accent);
        title.setPadding(px(6), 0, 0, 0);
        drawer.addView(title);

        search = new EditText(this);
        search.setHint("Cerca");
        search.setHintTextColor(th.sub);
        search.setTextColor(th.onTile);
        search.setTypeface(th.bodyFace);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(th.nuovo ? th.tile : (th.light ? 0xFFFFFFFF : 0xFF1A1A1A));
        sb.setCornerRadius(px(26));
        if (th.stroke != 0) sb.setStroke(Math.max(1, px(1)), th.stroke);
        search.setBackground(sb);
        search.setPadding(px(20), px(13), px(20), px(13));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { adapter.setQuery(s.toString()); }
        });
        search.setOnEditorActionListener((v, actionId, ev) -> {
            if (adapter.getCount() > 0) {
                launch(adapter.getItem(0), null);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = px(16);
        slp.bottomMargin = px(16);
        drawer.addView(search, slp);

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(px(20));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, px(24));
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> launch(adapter.getItem(pos), v));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            showAppMenu(adapter.getItem(pos), null);
            return true;
        });
        drawer.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void openDrawer(boolean keyboard) {
        if (drawerOpen) return;
        drawerOpen = true;
        search.setText("");
        drawer.setAlpha(0f);
        drawer.setTranslationY(px(60));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).translationY(0).setDuration(180).start();
        grid.setSelection(0);
        if (keyboard) {
            ui.postDelayed(() -> {
                search.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }, 220);
        }
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        drawer.animate().alpha(0f).translationY(px(60)).setDuration(150)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    private void hideDrawerNow() {
        drawerOpen = false;
        drawer.animate().cancel();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        drawer.setVisibility(View.GONE);
        drawer.setAlpha(1f);
        drawer.setTranslationY(0);
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
        }
    }

    /** Puntini delle pagine sotto la griglia. */
    private class IndicatorView extends View {
        private int current;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IndicatorView(Context c) {
            super(c);
        }

        void setCurrent(int c) {
            current = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int n = Math.max(1, pages);
            float r = 3.2f * dp, gap = 12 * dp;
            float x = getWidth() / 2f - (n - 1) * gap / 2f;
            for (int i = 0; i < n; i++) {
                p.setColor(i == current ? th.accent : Theme.alpha(th.wall ? 0xFFFFFFFF : th.onTile, 0.3f));
                cv.drawCircle(x + i * gap, getHeight() / 2f, r, p);
            }
        }
    }

    // ---------- layout della home ----------

    private void migrateFromV1() {
        if (prefs.contains("layout")) return;
        List<Item> list = defaultLayout();
        // i widget di sistema della versione 1 vanno in una pagina in fondo
        String old = prefs.getString("widgets", "");
        if (!old.isEmpty()) {
            int page = 2, row = 0;
            for (String part : old.split(";")) {
                String[] f = part.split(",");
                if (f.length != 2) continue;
                Item it = new Item("sys", 0, row, 4, 2, 0, page);
                it.data = f[0];
                list.add(it);
                row += 2;
                if (row > 4) {
                    row = 0;
                    page++;
                }
            }
            prefs.edit().putInt("pages", page + (row > 0 ? 1 : 0)).apply();
        }
        prefs.edit().putString("layout", Item.listToJson(list)).remove("widgets").apply();
    }

    private List<Item> defaultLayout() {
        List<Item> l = new ArrayList<>();
        l.add(new Item("clock_dots", 0, 0, 4, 1, 0, 0));
        l.add(new Item("clock_analog", 0, 1, 2, 2, 0, 0));
        l.add(new Item("weather", 2, 1, 2, 2, 1, 0));
        l.add(new Item("battery_pill", 0, 3, 2, 1, 0, 0));
        l.add(new Item("alarm", 2, 3, 1, 1, 2, 0));
        l.add(new Item("date_dot", 3, 3, 1, 1, 0, 0));
        l.add(new Item("search", 0, 4, 4, 1, 0, 0));
        l.add(new Item("calendar", 0, 0, 4, 2, 0, 1));
        l.add(new Item("date_big", 0, 2, 2, 2, 1, 1));
        l.add(new Item("battery_ring", 2, 2, 2, 2, 0, 1));
        l.add(new Item("weather_week", 0, 4, 4, 2, 0, 1));
        return l;
    }

    private void loadLayout() {
        items.clear();
        items.addAll(Item.listFromJson(prefs.getString("layout", "")));
        pages = Math.max(1, prefs.getInt("pages", 2));
        for (Item it : items) pages = Math.max(pages, it.page + 1);
    }

    private void saveLayout() {
        prefs.edit().putString("layout", Item.listToJson(items)).putInt("pages", pages).apply();
    }

    private void buildPages() {
        int cur = pager.getCurrent();
        pager.removeAllViews();
        grids.clear();
        for (int p = 0; p < pages; p++) {
            TileGrid g = new TileGrid(this, p, this);
            grids.add(g);
            pager.addView(g);
        }
        List<Item> dead = new ArrayList<>();
        for (Item it : items) {
            if (it.page >= pages) it.page = pages - 1;
            View v = createView(it);
            if (v == null) {
                dead.add(it);
                continue;
            }
            grids.get(it.page).addView(v);
        }
        if (!dead.isEmpty()) {
            items.removeAll(dead);
            saveLayout();
        }
        indicator.invalidate();
        final int target = Math.min(cur, pages - 1);
        pager.post(() -> {
            pager.setPageNow(target);
            indicator.setCurrent(target);
            updateSysWidgetSizes();
            if (th.glass) pager.postDelayed(() -> refreshTiles(null), 300);
        });
    }

    /** Dopo la prima misura: sposta gli elementi che non entrano nello schermo. */
    private void fitToScreen() {
        if (fitted || grids.isEmpty()) return;
        fitted = true;
        int rows = grids.get(0).getRows();
        boolean changed = false;
        for (Item it : new ArrayList<>(items)) {
            if (it.row + it.h <= rows && it.col + it.w <= TileGrid.COLS) continue;
            it.h = Math.min(it.h, rows);
            it.w = Math.min(it.w, TileGrid.COLS);
            int[] spot = findSpot(it, it.page, 0, 0, true);
            it.page = spot[0];
            it.col = spot[1];
            it.row = spot[2];
            changed = true;
        }
        if (changed) {
            saveLayout();
            buildPages();
        }
    }

    private View createView(Item it) {
        View v;
        if (it.isApp()) {
            v = new AppTile(this, it, this, th, prefs.getBoolean("labels", false));
            v.setOnClickListener(x -> {
                AppEntry a = appsByKey.get(it.data);
                if (a != null) launch(a, x);
            });
        } else if ("folder".equals(it.type)) {
            FolderTile ft = new FolderTile(this, it, this, th);
            ft.setOnClickListener(x -> {
                int idx = ft.tappedIndex();
                List<String> apps = FolderTile.apps(it);
                if (idx >= 0) {
                    AppEntry a = appsByKey.get(apps.get(idx));
                    if (a != null) launch(a, x);
                } else {
                    openFolder(it);
                }
            });
            v = ft;
        } else if (it.isSys()) {
            int id;
            try {
                id = Integer.parseInt(it.data);
            } catch (NumberFormatException e) {
                return null;
            }
            AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
            if (info == null) return null;
            v = host.createView(getApplicationContext(), id, info);
        } else {
            v = new NothingTile(this, it, th, h24);
            v.setOnClickListener(x -> onTileClick(it));
        }
        v.setTag(it);
        it.view = v;
        v.setOnLongClickListener(x -> {
            if (it.page < grids.size()) grids.get(it.page).startDrag(x);
            return true;
        });
        return v;
    }

    private void refreshTiles(String prefix) {
        for (Item it : items) {
            if (it.view instanceof NothingTile && (prefix == null || it.type.startsWith(prefix))) {
                it.view.invalidate();
            }
        }
    }

    private void onTileClick(Item it) {
        switch (it.type) {
            case "clock_dots":
            case "clock_analog":
            case "clock_pill":
            case "alarm":
                safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                break;
            case "date_big":
            case "date_dot":
            case "calendar": {
                Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
                ContentUris.appendId(b, System.currentTimeMillis());
                safeStart(new Intent(Intent.ACTION_VIEW).setData(b.build()));
                break;
            }
            case "weather":
            case "weather_week":
                refreshWeather(true);
                toast("Aggiorno il meteo…");
                break;
            case "battery_ring":
            case "battery_pill":
            case "battery_dots":
                safeStart(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
                break;
            case "search":
                openDrawer(true);
                break;
        }
    }

    private void updateAlarm() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
            State.nextAlarm = info == null ? "" : new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.ITALIAN)
                    .format(new Date(info.getTriggerTime()));
        } catch (Exception e) {
            State.nextAlarm = "";
        }
    }

    // ---------- posizionamento (TileGrid.Host) ----------

    private int rows() {
        return grids.isEmpty() ? 6 : Math.max(1, grids.get(0).getRows());
    }

    @Override
    public boolean canPlace(Item self, int page, int col, int row, int w, int h) {
        if (col < 0 || row < 0 || col + w > TileGrid.COLS || row + h > rows()) return false;
        for (Item o : items) {
            if (o == self || o.page != page) continue;
            if (col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h) return false;
        }
        return true;
    }

    /** Trova un posto libero: prima la cella indicata, poi la stessa pagina, poi le altre (o una nuova). */
    private int[] findSpot(Item it, int page, int prefCol, int prefRow, boolean allowNewPage) {
        if (canPlace(it, page, prefCol, prefRow, it.w, it.h)) return new int[]{page, prefCol, prefRow};
        for (int pg = 0; pg < pages; pg++) {
            int p = (page + pg) % pages;
            for (int r = 0; r < rows(); r++) {
                for (int c = 0; c < TileGrid.COLS; c++) {
                    if (canPlace(it, p, c, r, it.w, it.h)) return new int[]{p, c, r};
                }
            }
        }
        if (!allowNewPage) return null;
        pages++;
        return new int[]{pages - 1, 0, 0};
    }

    private void addItem(Item it, int page, int col, int row) {
        int[] spot = findSpot(it, page, col, row, true);
        it.page = spot[0];
        it.col = spot[1];
        it.row = spot[2];
        items.add(it);
        saveLayout();
        buildPages();
        if (it.page != pager.getCurrent()) {
            final int target = it.page;
            pager.postDelayed(() -> pager.snapTo(target), 120);
        }
    }

    @Override
    public boolean onDropOnto(Item dragged, int page, int col, int row) {
        if (!dragged.isApp()) return false;
        Item target = null;
        for (Item o : items) {
            if (o == dragged || o.page != page) continue;
            if (col >= o.col && col < o.col + o.w && row >= o.row && row < o.row + o.h) {
                target = o;
                break;
            }
        }
        if (target == null) return false;
        if ("folder".equals(target.type)) {
            List<String> apps = FolderTile.apps(target);
            if (!apps.contains(dragged.data)) apps.add(dragged.data);
            FolderTile.set(target, FolderTile.name(target), apps);
            items.remove(dragged);
        } else if (target.isApp()) {
            Item f = new Item("folder", target.col, target.row, 1, 1, 0, target.page);
            List<String> apps = new ArrayList<>();
            apps.add(target.data);
            if (!apps.contains(dragged.data)) apps.add(dragged.data);
            FolderTile.set(f, "Cartella", apps);
            items.remove(target);
            items.remove(dragged);
            items.add(f);
            toast("Cartella creata");
        } else {
            return false;
        }
        saveLayout();
        ui.post(this::buildPages);
        return true;
    }

    private void openFolder(Item folder) {
        List<String> keys = FolderTile.apps(folder);
        List<AppEntry> apps = new ArrayList<>();
        for (String k : keys) {
            AppEntry a = appsByKey.get(k);
            if (a != null) apps.add(a);
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).label;
        Sheet.grid(this, th, FolderTile.name(folder), apps.size(),
                i -> iconFor(apps.get(i).key, px(54)), i -> labels[i],
                i -> launch(apps.get(i), null),
                i -> {
                    // pressione prolungata: togli dalla cartella e rimetti sulla home
                    List<String> left = FolderTile.apps(folder);
                    left.remove(apps.get(i).key);
                    if (left.size() <= 1) {
                        items.remove(folder);
                        for (String k : left) {
                            Item it = new Item("app", folder.col, folder.row, 1, 1, 0, folder.page);
                            it.data = k;
                            items.add(it);
                        }
                    } else {
                        FolderTile.set(folder, FolderTile.name(folder), left);
                    }
                    Item out = new Item("app", 0, 0, 1, 1, 0, folder.page);
                    out.data = apps.get(i).key;
                    saveLayout();
                    addItem(out, folder.page, 0, 0);
                    toast(apps.get(i).label + " tolta dalla cartella");
                });
    }

    private void renameFolder(Item folder) {
        Sheet.input(this, th, "Nome della cartella", "es. Social", FolderTile.name(folder), n -> {
            FolderTile.set(folder, n.isEmpty() ? "Cartella" : n, FolderTile.apps(folder));
            saveLayout();
            if (folder.view != null) folder.view.invalidate();
        });
    }

    private void addToFolder(AppEntry a) {
        final List<Item> folders = new ArrayList<>();
        for (Item it : items) if ("folder".equals(it.type)) folders.add(it);
        if (folders.isEmpty()) {
            toast("Nessuna cartella: trascina un'app sopra un'altra per crearne una");
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < names.length; i++) names[i] = FolderTile.name(folders.get(i));
        Sheet.list(this, th, "Aggiungi a una cartella", names, -1, w -> {
            Item f = folders.get(w);
            List<String> apps = FolderTile.apps(f);
            if (!apps.contains(a.key)) apps.add(a.key);
            FolderTile.set(f, FolderTile.name(f), apps);
            saveLayout();
            if (f.view != null) f.view.invalidate();
            closeDrawer();
        });
    }

    @Override
    public void onItemMoved(Item it) {
        saveLayout();
    }

    @Override
    public void onItemMenu(Item it) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        int[][] sizes = Widgets.sizes(it.type);
        if (sizes.length > 1) {
            labels.add("Dimensione");
            acts.add(() -> chooseSize(it));
        }
        if (!it.isSys() && !it.isApp()) {
            labels.add("Colore");
            acts.add(() -> chooseTone(it));
        }
        if (it.isApp()) {
            AppEntry a = appsByKey.get(it.data);
            if (a != null) {
                labels.add("Stile icona");
                acts.add(() -> chooseIconMode(a));
                labels.add("Info app");
                acts.add(() -> appInfo(a));
            }
        }
        if (it.page < pages - 1) {
            labels.add("Sposta alla pagina successiva");
            acts.add(() -> moveToPage(it, it.page + 1));
        }
        if (it.page > 0) {
            labels.add("Sposta alla pagina precedente");
            acts.add(() -> moveToPage(it, it.page - 1));
        }
        if ("folder".equals(it.type)) {
            labels.add("Rinomina");
            acts.add(() -> renameFolder(it));
        }
        labels.add("Rimuovi");
        acts.add(() -> removeItem(it));
        String title = it.isApp() ? labelFor(it.data) : it.isSys() ? "Widget"
                : "folder".equals(it.type) ? FolderTile.name(it) : Widgets.name(it.type);
        Sheet.list(this, th, title, labels.toArray(new String[0]), -1, w -> acts.get(w).run());
    }

    @Override
    public void onEmptyLongPress(int page, int col, int row) {
        String[] opts = {"Widget Nothing", "Widget di sistema", "Aggiungi app", "Sfondi", "Impostazioni",
                "Aggiungi pagina", "Rimuovi questa pagina", "Launcher predefinito"};
        Sheet.list(this, th, "Home", opts, -1, w -> {
            switch (w) {
                case 0: pickNothingWidget(page, col, row); break;
                case 1: pickSysWidget(page, col, row); break;
                case 2: pickAppForHome(page, col, row); break;
                case 3: startActivity(new Intent(this, WallpaperActivity.class)); break;
                case 4: showSettings(); break;
                case 5:
                    pages++;
                    saveLayout();
                    buildPages();
                    pager.postDelayed(() -> pager.snapTo(pages - 1), 120);
                    break;
                case 6: removePage(page); break;
                default: safeStart(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
            }
        });
    }

    private void chooseSize(Item it) {
        int[][] sizes = Widgets.sizes(it.type);
        String[] labels = new String[sizes.length];
        int sel = -1;
        for (int i = 0; i < sizes.length; i++) {
            labels[i] = sizes[i][0] + " × " + sizes[i][1];
            if (sizes[i][0] == it.w && sizes[i][1] == it.h) sel = i;
        }
        Sheet.list(this, th, "Dimensione", labels, sel, w -> {
            int nw = sizes[w][0], nh = sizes[w][1];
            if (canPlace(it, it.page, it.col, it.row, nw, nh)) {
                it.w = nw;
                it.h = nh;
            } else {
                int ow = it.w, oh = it.h;
                it.w = nw;
                it.h = nh;
                int[] spot = findSpot(it, it.page, 0, 0, false);
                if (spot == null) {
                    it.w = ow;
                    it.h = oh;
                    toast("Non c'è spazio per questa dimensione");
                    return;
                }
                it.page = spot[0];
                it.col = spot[1];
                it.row = spot[2];
            }
            saveLayout();
            buildPages();
        });
    }

    private void chooseTone(Item it) {
        String[] labels = {"Standard", "Contrasto", "Accento (rosso)"};
        Sheet.list(this, th, "Colore", labels, it.tone, w -> {
            it.tone = w;
            saveLayout();
            if (it.view != null) it.view.invalidate();
        });
    }

    private void moveToPage(Item it, int page) {
        int[] spot = null;
        for (int r = 0; r < rows() && spot == null; r++) {
            for (int c = 0; c < TileGrid.COLS; c++) {
                if (canPlace(it, page, c, r, it.w, it.h)) {
                    spot = new int[]{c, r};
                    break;
                }
            }
        }
        if (spot == null) {
            toast("La pagina è piena");
            return;
        }
        it.page = page;
        it.col = spot[0];
        it.row = spot[1];
        saveLayout();
        buildPages();
        pager.postDelayed(() -> pager.snapTo(page), 120);
    }

    private void removeItem(Item it) {
        items.remove(it);
        if (it.isSys()) {
            try {
                host.deleteAppWidgetId(Integer.parseInt(it.data));
            } catch (Exception ignored) {
            }
        }
        saveLayout();
        buildPages();
    }

    private void removePage(int page) {
        if (pages <= 1) {
            toast("Serve almeno una pagina");
            return;
        }
        Sheet.confirm(this, th, "Rimuovere la pagina?",
                "Gli elementi presenti su questa pagina verranno tolti dalla home.", "Rimuovi", () -> {
                    for (Item it : new ArrayList<>(items)) {
                        if (it.page == page) {
                            items.remove(it);
                            if (it.isSys()) {
                                try {
                                    host.deleteAppWidgetId(Integer.parseInt(it.data));
                                } catch (Exception ignored) {
                                }
                            }
                        } else if (it.page > page) {
                            it.page--;
                        }
                    }
                    pages--;
                    saveLayout();
                    buildPages();
                });
    }

    private void pickNothingWidget(int page, int col, int row) {
        String[] names = new String[Widgets.TYPES.length];
        for (int i = 0; i < names.length; i++) names[i] = Widgets.name(Widgets.TYPES[i]);
        Sheet.list(this, th, "Widget Nothing", names, -1, w -> {
            String type = Widgets.TYPES[w];
            int[] s = Widgets.sizes(type)[0];
            int tone = "alarm".equals(type) ? 2 : 0;
            addItem(new Item(type, col, row, s[0], s[1], tone, page), page, col, row);
        });
    }

    private void pickAppForHome(int page, int col, int row) {
        if (allApps.isEmpty()) return;
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = allApps.get(i).label;
        Sheet.list(this, th, "Aggiungi app", labels, -1, w -> addAppToHome(allApps.get(w), page, col, row));
    }

    private void addAppToHome(AppEntry a, int page, int col, int row) {
        Item it = new Item("app", col, row, 1, 1, 0, page);
        it.data = a.key;
        addItem(it, page, col, row);
    }

    // ---------- widget di sistema ----------

    private void pickSysWidget(int page, int col, int row) {
        final List<AppWidgetProviderInfo> providers = new ArrayList<>(awm.getInstalledProviders());
        if (providers.isEmpty()) {
            toast("Nessun widget disponibile");
            return;
        }
        final PackageManager pm = getPackageManager();
        final List<String> labels = new ArrayList<>();
        for (AppWidgetProviderInfo p : providers) labels.add(widgetLabel(pm, p));
        final Collator col2 = Collator.getInstance(Locale.ITALIAN);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) order.add(i);
        Collections.sort(order, (a, b) -> col2.compare(labels.get(a), labels.get(b)));
        final String[] arr = new String[order.size()];
        final AppWidgetProviderInfo[] infos = new AppWidgetProviderInfo[order.size()];
        for (int i = 0; i < order.size(); i++) {
            arr[i] = labels.get(order.get(i));
            infos[i] = providers.get(order.get(i));
        }
        Sheet.list(this, th, "Widget di sistema", arr, -1, w -> {
            pendingPage = page;
            pendingCol = col;
            pendingRow = row;
            bindWidget(infos[w]);
        });
    }

    private String widgetLabel(PackageManager pm, AppWidgetProviderInfo p) {
        String app;
        try {
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p.provider.getPackageName(), 0)));
        } catch (Exception e) {
            app = p.provider.getPackageName();
        }
        String w = String.valueOf(p.loadLabel(pm));
        return w.equals(app) ? app : app + " · " + w;
    }

    private void bindWidget(AppWidgetProviderInfo info) {
        int id = host.allocateAppWidgetId();
        pendingWidgetId = id;
        boolean ok;
        try {
            ok = awm.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null);
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            configureOrAdd(id);
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
            try {
                startActivityForResult(i, REQ_BIND);
            } catch (Exception e) {
                cancelPending();
                toast("Impossibile aggiungere il widget");
            }
        }
    }

    private void configureOrAdd(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            cancelPending();
            return;
        }
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null);
                return;
            } catch (Exception ignored) {
            }
        }
        finishAdd(id);
    }

    private void finishAdd(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            cancelPending();
            return;
        }
        float cell = grids.isEmpty() ? 80 * dp : grids.get(0).getCell();
        float gap = grids.isEmpty() ? 12 * dp : grids.get(0).getGap();
        int w = Math.max(1, Math.min(TileGrid.COLS, (int) Math.ceil((info.minWidth + gap) / (cell + gap))));
        int h = Math.max(1, Math.min(rows(), (int) Math.ceil((info.minHeight + gap) / (cell + gap))));
        Item it = new Item("sys", pendingCol, pendingRow, w, h, 0, pendingPage);
        it.data = String.valueOf(id);
        pendingWidgetId = -1;
        addItem(it, pendingPage, pendingCol, pendingRow);
    }

    private void cancelPending() {
        if (pendingWidgetId != -1) {
            try {
                host.deleteAppWidgetId(pendingWidgetId);
            } catch (Exception ignored) {
            }
        }
        pendingWidgetId = -1;
    }

    private void updateSysWidgetSizes() {
        if (grids.isEmpty()) return;
        float cell = grids.get(0).getCell(), gap = grids.get(0).getGap();
        if (cell <= 0) return;
        for (Item it : items) {
            if (!it.isSys()) continue;
            try {
                int id = Integer.parseInt(it.data);
                int wdp = Math.round((it.w * cell + (it.w - 1) * gap) / dp);
                int hdp = Math.round((it.h * cell + (it.h - 1) * gap) / dp);
                Bundle o = new Bundle();
                o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wdp);
                o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wdp);
                o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hdp);
                o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hdp);
                awm.updateAppWidgetOptions(id, o);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EXPORT || req == REQ_IMPORT) {
            if (res != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            try {
                if (req == REQ_EXPORT) {
                    Backup.export(this, prefs, uri);
                    toast("Configurazione esportata");
                } else {
                    int dropped = Backup.restore(this, prefs, uri);
                    toast(dropped > 0
                            ? "Configurazione importata. " + dropped + " widget di sistema vanno riaggiunti"
                            : "Configurazione importata");
                    recreate();
                }
            } catch (Exception e) {
                toast(req == REQ_EXPORT ? "Esportazione non riuscita" : "File di configurazione non valido");
            }
            return;
        }
        if (req == REQ_BIND) {
            if (res == RESULT_OK && pendingWidgetId != -1) configureOrAdd(pendingWidgetId);
            else cancelPending();
        } else if (req == REQ_CONFIG) {
            if (res == RESULT_OK && pendingWidgetId != -1) finishAdd(pendingWidgetId);
            else cancelPending();
        }
    }

    // ---------- app ----------

    private void loadApps() {
        appExec.execute(() -> {
            List<AppEntry> list = new ArrayList<>();
            try {
                for (UserHandle u : launcherApps.getProfiles()) {
                    for (LauncherActivityInfo i : launcherApps.getActivityList(null, u)) {
                        ComponentName cn = i.getComponentName();
                        if (cn.getPackageName().equals(getPackageName())) continue;
                        AppEntry a = new AppEntry();
                        a.label = String.valueOf(i.getLabel());
                        a.component = cn;
                        a.user = u;
                        a.key = cn.flattenToString();
                        try {
                            a.icon = i.getIcon(0);
                        } catch (Exception ignored) {
                        }
                        list.add(a);
                    }
                }
            } catch (Exception ignored) {
            }
            final Collator col = Collator.getInstance(Locale.ITALIAN);
            Collections.sort(list, (x, y) -> col.compare(x.label, y.label));
            // icone del cassetto e del dock preparate in background: il cassetto si apre senza scatti
            final Map<String, Bitmap> pre = new HashMap<>();
            final int size = px(54);
            final String style = prefs.getString("icons", IconFactory.AUTO);
            final Set<String> reds = new HashSet<>(prefs.getStringSet("redApps", new HashSet<>()));
            final Set<String> origs = new HashSet<>(prefs.getStringSet("origApps", new HashSet<>()));
            final Set<String> glyphs = new HashSet<>(prefs.getStringSet("glyphApps", new HashSet<>()));
            final IconPacks pack = IconPacks.load(this, prefs.getString("iconPack", ""));
            for (AppEntry a : list) {
                if (a.icon == null) continue;
                try {
                    int mode = origs.contains(a.key) ? IconFactory.MODE_ORIGINAL
                            : glyphs.contains(a.key) ? IconFactory.MODE_GLYPH : IconFactory.MODE_AUTO;
                    pre.put(a.key + "@" + size, makeIcon(a, size, style, reds.contains(a.key), mode, pack));
                } catch (Exception ignored) {
                }
            }
            ui.post(() -> {
                iconPack = pack;
                allApps.clear();
                allApps.addAll(list);
                appsByKey.clear();
                for (AppEntry a : list) appsByKey.put(a.key, a);
                iconCache.clear();
                iconCache.putAll(pre);
                adapter.refresh();
                buildDock();
                refreshAppViews();
            });
        });
    }

    private Bitmap makeIcon(AppEntry a, int size, String style, boolean red, int mode, IconPacks pack) {
        if (pack != null && !red && mode == IconFactory.MODE_AUTO) {
            Drawable pd = pack.iconFor(a.component);
            if (pd != null) return IconFactory.fromPack(pd, size);
        }
        return IconFactory.make(a.icon, size, style, red, mode, th);
    }

    private int iconMode(String key) {
        if (prefs.getStringSet("origApps", new HashSet<>()).contains(key)) return IconFactory.MODE_ORIGINAL;
        if (prefs.getStringSet("glyphApps", new HashSet<>()).contains(key)) return IconFactory.MODE_GLYPH;
        return IconFactory.MODE_AUTO;
    }

    /** Scelta per singola app: automatica, sempre originale, sempre Nothing, rossa. */
    private void chooseIconMode(AppEntry a) {
        String[] labels = {"Automatica", "Originale (non modificata)", "Nothing (forza il simbolo)", "Nothing rossa"};
        int cur = isRed(a.key) ? 3 : iconMode(a.key);
        Sheet.list(this, th, "Stile icona", labels, cur, w -> {
            Set<String> o = new HashSet<>(prefs.getStringSet("origApps", new HashSet<>()));
            Set<String> g = new HashSet<>(prefs.getStringSet("glyphApps", new HashSet<>()));
            Set<String> r = new HashSet<>(prefs.getStringSet("redApps", new HashSet<>()));
            o.remove(a.key);
            g.remove(a.key);
            r.remove(a.key);
            if (w == 1) o.add(a.key);
            else if (w == 2) g.add(a.key);
            else if (w == 3) r.add(a.key);
            prefs.edit().putStringSet("origApps", o).putStringSet("glyphApps", g).putStringSet("redApps", r).apply();
            iconCache.clear();
            adapter.notifyDataSetChanged();
            buildDock();
            refreshAppViews();
        });
    }

    private void refreshAppViews() {
        for (Item it : items) {
            if (it.view instanceof AppTile || it.view instanceof FolderTile) it.view.invalidate();
        }
    }

    private boolean isRed(String key) {
        return prefs.getStringSet("redApps", new HashSet<>()).contains(key);
    }

    private void toggleRed(String key) {
        Set<String> s = new HashSet<>(prefs.getStringSet("redApps", new HashSet<>()));
        if (!s.remove(key)) s.add(key);
        prefs.edit().putStringSet("redApps", s).apply();
        iconCache.clear();
        adapter.notifyDataSetChanged();
        buildDock();
        refreshAppViews();
    }

    @Override
    public Bitmap iconFor(String key, int size) {
        if (size <= 0) return null;
        String ck = key + "@" + size;
        Bitmap b = iconCache.get(ck);
        if (b != null) return b;
        AppEntry a = appsByKey.get(key);
        if (a == null || a.icon == null) return null;
        try {
            b = makeIcon(a, size, prefs.getString("icons", IconFactory.AUTO), isRed(key), iconMode(key), iconPack);
        } catch (Exception e) {
            return null;
        }
        iconCache.put(ck, b);
        return b;
    }

    @Override
    public String labelFor(String key) {
        AppEntry a = appsByKey.get(key);
        return a == null ? "" : a.label;
    }

    private void launch(AppEntry a, View v) {
        if (a == null) return;
        try {
            Rect r = null;
            Bundle opts = null;
            if (v != null && v.getWidth() > 0) {
                r = new Rect();
                v.getGlobalVisibleRect(r);
                opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle();
            }
            launcherApps.startMainActivity(a.component, a.user, r, opts);
        } catch (Exception e) {
            toast("Impossibile aprire " + a.label);
        }
    }

    private void appInfo(AppEntry a) {
        try {
            launcherApps.startAppDetailsActivity(a.component, a.user, null, null);
        } catch (Exception e) {
            toast("Impossibile aprire le informazioni");
        }
    }

    private Set<String> hidden() {
        return new HashSet<>(prefs.getStringSet("hidden", new HashSet<>()));
    }

    private List<String> dockKeys() {
        String s = prefs.getString("dock", "");
        List<String> out = new ArrayList<>();
        if (!TextUtils.isEmpty(s)) out.addAll(Arrays.asList(s.split("\\|")));
        return out;
    }

    private void saveDock(List<String> keys) {
        prefs.edit().putString("dock", TextUtils.join("|", keys)).apply();
    }

    private List<String> defaultDock() {
        List<Intent> intents = new ArrayList<>();
        intents.add(new Intent(Intent.ACTION_DIAL));
        intents.add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING));
        intents.add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        intents.add(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        List<String> keys = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (Intent i : intents) {
            try {
                ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri == null || ri.activityInfo == null) continue;
                for (AppEntry a : allApps) {
                    if (a.component.getPackageName().equals(ri.activityInfo.packageName)) {
                        if (!keys.contains(a.key)) keys.add(a.key);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return keys;
    }

    private void buildDock() {
        dock.removeAllViews();
        List<String> keys = dockKeys();
        if (keys.isEmpty() && !prefs.getBoolean("dockInit", false) && !allApps.isEmpty()) {
            keys = defaultDock();
            saveDock(keys);
            prefs.edit().putBoolean("dockInit", true).apply();
        }
        int size = px(54);
        for (String k : keys) {
            AppEntry a = appsByKey.get(k);
            if (a == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(iconFor(a.key, size));
            iv.setContentDescription(a.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = px(9);
            lp.rightMargin = px(9);
            iv.setOnClickListener(v -> launch(a, v));
            iv.setOnLongClickListener(v -> {
                showAppMenu(a, true);
                return true;
            });
            dock.addView(iv, lp);
        }
        if (dock.getChildCount() == 0) {
            TextView t = new TextView(this);
            t.setText("Tieni premuta un'app nel cassetto per aggiungerla qui");
            t.setTextColor(th.sub);
            t.setTypeface(th.bodyFace);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setGravity(Gravity.CENTER);
            dock.addView(t);
        }
    }

    private void showAppMenu(AppEntry a, Boolean fromDock) {
        final List<String> dk = dockKeys();
        final boolean inDock = dk.contains(a.key);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (fromDock == null) {
            labels.add("Aggiungi alla home");
            acts.add(() -> {
                closeDrawer();
                addAppToHome(a, pager.getCurrent(), 0, 0);
            });
        }
        labels.add(inDock ? "Rimuovi dal dock" : "Aggiungi al dock");
        acts.add(() -> {
            if (inDock) dk.remove(a.key);
            else if (dk.size() >= MAX_DOCK) {
                toast("Il dock contiene al massimo " + MAX_DOCK + " app");
                return;
            } else dk.add(a.key);
            prefs.edit().putBoolean("dockInit", true).apply();
            saveDock(dk);
            buildDock();
        });
        labels.add("Stile icona");
        acts.add(() -> chooseIconMode(a));
        if (fromDock == null) {
            labels.add("Aggiungi a una cartella");
            acts.add(() -> addToFolder(a));
            labels.add("Nascondi dal cassetto");
            acts.add(() -> {
                Set<String> h = hidden();
                h.add(a.key);
                prefs.edit().putStringSet("hidden", h).apply();
                adapter.refresh();
            });
        }
        labels.add("Info app");
        acts.add(() -> appInfo(a));
        labels.add("Disinstalla");
        acts.add(() -> safeStart(new Intent(Intent.ACTION_DELETE,
                Uri.fromParts("package", a.component.getPackageName(), null))));
        Sheet.list(this, th, a.label, labels.toArray(new String[0]), -1, w -> acts.get(w).run());
    }

    private class AppAdapter extends BaseAdapter {
        private final List<AppEntry> shown = new ArrayList<>();
        private String query = "";

        void setQuery(String q) {
            query = DotTextView.normalize(q.trim());
            refresh();
        }

        void refresh() {
            shown.clear();
            Set<String> h = hidden();
            for (AppEntry a : allApps) {
                if (h.contains(a.key)) continue;
                if (!query.isEmpty() && !DotTextView.normalize(a.label).contains(query)) continue;
                shown.add(a);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public AppEntry getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView iv;
            TextView tv;
            if (convert instanceof LinearLayout) {
                cell = (LinearLayout) convert;
                iv = (ImageView) cell.getChildAt(0);
                tv = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(HomeActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                iv = new ImageView(HomeActivity.this);
                cell.addView(iv, new LinearLayout.LayoutParams(px(54), px(54)));
                tv = new TextView(HomeActivity.this);
                tv.setTextColor(th.onTile);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                tv.setTypeface(th.bodyFace);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(6);
                cell.addView(tv, lp);
            }
            AppEntry a = shown.get(pos);
            iv.setImageBitmap(iconFor(a.key, px(54)));
            tv.setText(a.label);
            return cell;
        }
    }

    // ---------- impostazioni ----------

    private void showSettings() {
        String style = prefs.getString("style", "classic");
        String mode = prefs.getString("mode", "dark");
        String icons = prefs.getString("icons", IconFactory.AUTO);
        boolean wall = prefs.getBoolean("wall", false);
        boolean labels = prefs.getBoolean("labels", false);
        String city = prefs.getString("city", "");
        String[] items = {
                "Stile: " + ("nuovo".equals(style) ? "Nuovo (5.0)" : "Classico"),
                "Tema: " + ("light".equals(mode) ? "Chiaro" : "auto".equals(mode) ? "Automatico" : "Scuro"),
                "Icone: " + (IconFactory.INVERSE.equals(icons) ? "invertite"
                        : IconFactory.COLOR.equals(icons) ? "a colori" : "monocromatiche"),
                "Sfondo: " + (wall ? "sfondo di sistema" : "tinta unita"),
                "Nomi delle app sulla home: " + (labels ? "sì" : "no"),
                "Orologio: " + (h24 ? "24 ore" : "12 ore"),
                "Meteo: " + (city.isEmpty() ? "posizione automatica" : city),
                "Pacchetto di icone: " + packLabel(),
                "App nascoste…",
                "Esporta configurazione",
                "Importa configurazione"
        };
        Sheet.list(this, th, "Impostazioni", items, -1, w -> {
            switch (w) {
                case 0: choose("Stile", new String[]{"Classico", "Nuovo (5.0)"},
                        new String[]{"classic", "nuovo"}, "style", style); break;
                case 1: choose("Tema", new String[]{"Scuro", "Chiaro", "Automatico"},
                        new String[]{"dark", "light", "auto"}, "mode", mode); break;
                case 2: choose("Icone", new String[]{"Monocromatiche", "Invertite", "A colori"},
                        new String[]{IconFactory.AUTO, IconFactory.INVERSE, IconFactory.COLOR}, "icons", icons); break;
                case 3:
                    prefs.edit().putBoolean("wall", !wall).apply();
                    recreate();
                    break;
                case 4:
                    prefs.edit().putBoolean("labels", !labels).apply();
                    recreate();
                    break;
                case 5:
                    prefs.edit().putBoolean("h24", !h24).apply();
                    recreate();
                    break;
                case 6: askCity(); break;
                case 7: pickIconPack(); break;
                case 8: manageHidden(); break;
                case 9: exportBackup(); break;
                case 10: importBackup(); break;
            }
        });
    }

    private void choose(String title, String[] labels, String[] values, String key, String cur) {
        int sel = Arrays.asList(values).indexOf(cur);
        Sheet.list(this, th, title, labels, sel, w -> {
            if (values[w].equals(cur)) return;
            prefs.edit().putString(key, values[w]).apply();
            recreate();
        });
    }

    private String packLabel() {
        String pkg = prefs.getString("iconPack", "");
        if (pkg.isEmpty()) return "nessuno";
        String name = IconPacks.installed(this).get(pkg);
        return name == null ? "non più installato" : name;
    }

    private void pickIconPack() {
        final Map<String, String> packs = IconPacks.installed(this);
        final List<String> pkgs = new ArrayList<>(packs.keySet());
        String[] labels = new String[pkgs.size() + 1];
        labels[0] = "Nessuno (conversione automatica)";
        String cur = prefs.getString("iconPack", "");
        int sel = 0;
        for (int i = 0; i < pkgs.size(); i++) {
            labels[i + 1] = packs.get(pkgs.get(i));
            if (pkgs.get(i).equals(cur)) sel = i + 1;
        }
        if (pkgs.isEmpty()) toast("Nessun pacchetto di icone installato");
        Sheet.list(this, th, "Pacchetto di icone", labels, sel, w -> {
            String v = w == 0 ? "" : pkgs.get(w - 1);
            if (v.equals(cur)) return;
            if (!v.isEmpty() && IconPacks.load(this, v) == null) {
                toast("Questo pacchetto non è leggibile");
                return;
            }
            prefs.edit().putString("iconPack", v).apply();
            recreate();
        });
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "dotlauncher-backup.json");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast("Impossibile aprire il selettore di file");
        }
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("Impossibile aprire il selettore di file");
        }
    }

    private void manageHidden() {
        final List<AppEntry> hiddenApps = new ArrayList<>();
        Set<String> h = hidden();
        for (AppEntry a : allApps) if (h.contains(a.key)) hiddenApps.add(a);
        if (hiddenApps.isEmpty()) {
            toast("Nessuna app nascosta");
            return;
        }
        String[] labels = new String[hiddenApps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = "Mostra " + hiddenApps.get(i).label;
        Sheet.list(this, th, "App nascoste", labels, -1, w -> {
            Set<String> s = hidden();
            s.remove(hiddenApps.get(w).key);
            prefs.edit().putStringSet("hidden", s).apply();
            adapter.refresh();
        });
    }

    private void askCity() {
        Sheet.input(this, th, "Città per il meteo", "es. Milano (vuoto = posizione automatica)",
                prefs.getString("city", ""), c -> {
                    if (c.isEmpty()) {
                        prefs.edit().remove("city").remove("mlat").remove("mlon").apply();
                        refreshWeather(true);
                    } else {
                        geocode(c);
                    }
                });
    }

    // ---------- meteo (Open-Meteo, gratuito e senza chiave) ----------

    private void maybeRefreshWeather() {
        if (!State.wOk && !prefs.contains("mlat") && !prefs.getBoolean("askedLoc", false)) {
            prefs.edit().putBoolean("askedLoc", true).apply();
            ui.postDelayed(() -> refreshWeather(true), 800);
            return;
        }
        long age = System.currentTimeMillis() - prefs.getLong("wt", 0);
        if (age > 30 * 60 * 1000L) refreshWeather(false);
    }

    private void refreshWeather(boolean byUser) {
        if (prefs.contains("mlat")) {
            fetchWeather(Double.longBitsToDouble(prefs.getLong("mlat", 0)),
                    Double.longBitsToDouble(prefs.getLong("mlon", 0)), prefs.getString("city", ""));
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locateAndFetch();
        } else if (byUser) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req != REQ_LOC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) locateAndFetch();
        else askCity();
    }

    private void locateAndFetch() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
        }
        if (best != null) {
            fetchWeather(best.getLatitude(), best.getLongitude(), null);
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        ? LocationManager.NETWORK_PROVIDER : LocationManager.PASSIVE_PROVIDER;
                lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                    if (loc != null) fetchWeather(loc.getLatitude(), loc.getLongitude(), null);
                    else toast("Posizione non disponibile: imposta una città");
                });
            } catch (Exception ignored) {
            }
        }
    }

    private void fetchWeather(double lat, double lon, String knownCity) {
        netExec.execute(() -> {
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&forecast_days=6&timezone=auto", lat, lon);
                String json = http(url);
                String city = knownCity;
                if (city == null || city.isEmpty()) city = reverseCity(lat, lon);
                State.parseWeather(json, city);
                prefs.edit().putString("wjson", json).putString("wcity", city)
                        .putLong("wt", System.currentTimeMillis()).apply();
                ui.post(() -> refreshTiles("weather"));
            } catch (Exception ignored) {
            }
        });
    }

    private String reverseCity(double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return "";
            @SuppressWarnings("deprecation")
            List<Address> res = new Geocoder(this, Locale.ITALIAN).getFromLocation(lat, lon, 1);
            if (res != null && !res.isEmpty() && res.get(0).getLocality() != null) return res.get(0).getLocality();
        } catch (Exception ignored) {
        }
        return "";
    }

    private void geocode(String city) {
        netExec.execute(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=it&name="
                        + URLEncoder.encode(city, "UTF-8");
                JSONArray res = new JSONObject(http(url)).optJSONArray("results");
                if (res == null || res.length() == 0) throw new Exception("not found");
                JSONObject r = res.getJSONObject(0);
                double lat = r.getDouble("latitude");
                double lon = r.getDouble("longitude");
                String name = r.optString("name", city);
                prefs.edit().putString("city", name)
                        .putLong("mlat", Double.doubleToLongBits(lat))
                        .putLong("mlon", Double.doubleToLongBits(lon)).apply();
                fetchWeather(lat, lon, name);
            } catch (Exception e) {
                ui.post(() -> toast("Città non trovata"));
            }
        });
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    // ---------- utilità ----------

    private void safeStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("Nessuna app disponibile");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
