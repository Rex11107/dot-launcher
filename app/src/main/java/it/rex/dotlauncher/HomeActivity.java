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
    private static final int HOST_ID = 0x0D07;
    private static final int MAX_DOCK = 5;

    private SharedPreferences prefs;
    private Theme th;
    private String themeSig;
    private float dp;
    private boolean h24;

    private FrameLayout root;
    private LinearLayout homeBox;
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

        homeBox = new LinearLayout(this);
        LinearLayout home = homeBox;
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(0, px(32), 0, px(6));

        pager = new Pager(this);
        pager.setListener(p -> indicator.setCurrent(p));
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
            homeBox.setPadding(r[0], r[1] + px(8), r[2], r[3] + px(6));
            drawer.setPadding(px(16) + r[0], r[1] + px(20), px(16) + r[2], r[7]);
            grid.setPadding(0, 0, 0, px(24) + (r[7] > 0 ? 0 : r[3]));
            gestureBottom = r[6];
            navBottom = r[3];
            pager.setEdgeGuard(r[4], r[5]);
            return in;
        });
        root.requestApplyInsets();
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
                labels.add(isRed(a.key) ? "Icona normale" : "Icona rossa");
                acts.add(() -> toggleRed(a.key));
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
        labels.add("Rimuovi");
        acts.add(() -> removeItem(it));
        String title = it.isApp() ? labelFor(it.data) : it.isSys() ? "Widget" : Widgets.name(it.type);
        dialog().setTitle(title).setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
    }

    @Override
    public void onEmptyLongPress(int page, int col, int row) {
        String[] opts = {"Widget Nothing", "Widget di sistema", "Aggiungi app", "Sfondi", "Impostazioni",
                "Aggiungi pagina", "Rimuovi questa pagina", "Launcher predefinito"};
        dialog().setItems(opts, (d, w) -> {
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
        }).show();
    }

    private void chooseSize(Item it) {
        int[][] sizes = Widgets.sizes(it.type);
        String[] labels = new String[sizes.length];
        int sel = -1;
        for (int i = 0; i < sizes.length; i++) {
            labels[i] = sizes[i][0] + " × " + sizes[i][1];
            if (sizes[i][0] == it.w && sizes[i][1] == it.h) sel = i;
        }
        dialog().setTitle("Dimensione").setSingleChoiceItems(labels, sel, (d, w) -> {
            d.dismiss();
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
        }).show();
    }

    private void chooseTone(Item it) {
        String[] labels = {"Standard", "Contrasto", "Accento (rosso)"};
        dialog().setTitle("Colore").setSingleChoiceItems(labels, it.tone, (d, w) -> {
            d.dismiss();
            it.tone = w;
            saveLayout();
            if (it.view != null) it.view.invalidate();
        }).show();
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
        dialog().setTitle("Rimuovere la pagina?")
                .setMessage("Gli elementi presenti su questa pagina verranno tolti dalla home.")
                .setPositiveButton("Rimuovi", (d, w) -> {
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
                })
                .setNegativeButton("Annulla", null).show();
    }

    private void pickNothingWidget(int page, int col, int row) {
        String[] names = new String[Widgets.TYPES.length];
        for (int i = 0; i < names.length; i++) names[i] = Widgets.name(Widgets.TYPES[i]);
        dialog().setTitle("Widget Nothing").setItems(names, (d, w) -> {
            String type = Widgets.TYPES[w];
            int[] s = Widgets.sizes(type)[0];
            int tone = "alarm".equals(type) ? 2 : 0;
            addItem(new Item(type, col, row, s[0], s[1], tone, page), page, col, row);
        }).show();
    }

    private void pickAppForHome(int page, int col, int row) {
        if (allApps.isEmpty()) return;
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = allApps.get(i).label;
        dialog().setTitle("Aggiungi app").setItems(labels, (d, w) -> addAppToHome(allApps.get(w), page, col, row)).show();
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
        dialog().setTitle("Widget di sistema").setItems(arr, (d, w) -> {
            pendingPage = page;
            pendingCol = col;
            pendingRow = row;
            bindWidget(infos[w]);
        }).show();
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
            ui.post(() -> {
                allApps.clear();
                allApps.addAll(list);
                appsByKey.clear();
                for (AppEntry a : list) appsByKey.put(a.key, a);
                iconCache.clear();
                adapter.refresh();
                buildDock();
                for (Item it : items) if (it.view instanceof AppTile) it.view.invalidate();
            });
        });
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
        for (Item it : items) if (it.view instanceof AppTile) it.view.invalidate();
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
            b = IconFactory.make(a.icon, size, prefs.getString("icons", IconFactory.AUTO), isRed(key), th);
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
        labels.add(isRed(a.key) ? "Icona normale" : "Icona rossa");
        acts.add(() -> toggleRed(a.key));
        if (fromDock == null) {
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
        dialog().setTitle(a.label).setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
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

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, th.light
                ? android.R.style.Theme_Material_Light_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert);
    }

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
                "App nascoste…"
        };
        dialog().setTitle("Impostazioni").setItems(items, (d, w) -> {
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
                case 7: manageHidden(); break;
            }
        }).show();
    }

    private void choose(String title, String[] labels, String[] values, String key, String cur) {
        int sel = Arrays.asList(values).indexOf(cur);
        dialog().setTitle(title).setSingleChoiceItems(labels, sel, (d, w) -> {
            d.dismiss();
            if (values[w].equals(cur)) return;
            prefs.edit().putString(key, values[w]).apply();
            recreate();
        }).show();
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
        dialog().setTitle("App nascoste").setItems(labels, (d, w) -> {
            Set<String> s = hidden();
            s.remove(hiddenApps.get(w).key);
            prefs.edit().putStringSet("hidden", s).apply();
            adapter.refresh();
        }).show();
    }

    private void askCity() {
        EditText et = new EditText(this);
        et.setText(prefs.getString("city", ""));
        et.setHint("es. Milano (vuoto = posizione automatica)");
        et.setSingleLine(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(px(20), px(8), px(20), 0);
        box.addView(et);
        dialog().setTitle("Città per il meteo").setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    String c = et.getText().toString().trim();
                    if (c.isEmpty()) {
                        prefs.edit().remove("city").remove("mlat").remove("mlon").apply();
                        refreshWeather(true);
                    } else {
                        geocode(c);
                    }
                })
                .setNegativeButton("Annulla", null).show();
    }

    // ---------- meteo (Open-Meteo, gratuito e senza chiave) ----------

    private void maybeRefreshWeather() {
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
