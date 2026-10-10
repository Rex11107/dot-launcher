package it.rex.dotlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
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
import android.content.pm.PackageInfo;
import android.content.pm.ShortcutInfo;
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
import android.os.UserManager;
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
import android.view.HapticFeedbackConstants;
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
import java.util.Calendar;
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
    private static final int REQ_PHOTO = 6;
    private static final int REQ_STEPS = 7;
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
    private SensorHub sensors;
    private boolean started;
    private Item pendingPhoto;

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
        TileGrid.COLS = prefs.getInt("cols", 4) == 5 ? 5 : 4;
        Draw.ghost = prefs.getBoolean("ghost", true);
        Draw.anim = prefs.getBoolean("anim", true);
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
        if (saved != null) {
            int pi = saved.getInt("pph", -1);
            if (pi >= 0 && pi < items.size()) pendingPhoto = items.get(pi);
        }
        State.stepGoal = prefs.getInt("stepGoal", 8000);
        sensors = new SensorHub(this, prefs, this::refreshTiles);

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
        out.putInt("pph", pendingPhoto == null ? -1 : items.indexOf(pendingPhoto));
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
        started = true;
        startSensors();
        updateAlarm();
        refreshTiles(null);
        maybeRefreshWeather();
        consumePendingShortcuts();
        checkUpdate(false);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        playWave();
    }

    @Override
    protected void onResume() {
        super.onResume();
        consumePendingShortcuts();
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
        started = false;
        if (sensors != null) sensors.stop();
        if (dragSrc >= 0) finishDrag(false);
        exitResize();
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
        if (resizer != null) exitResize();
        else if (drawerOpen) closeDrawer();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        if (dragSrc >= 0) {
            onDragEvent(ev);
            return true;
        }
        if (gestures != null && resizer == null) gestures.onTouchEvent(ev);
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
        buildDropBar();
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
            dropBar.setPadding(0, r[1] + px(8), 0, px(8));
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
        grid.setNumColumns(TileGrid.COLS);
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
            AppEntry a = adapter.getItem(pos);
            View icon = v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0 ? ((ViewGroup) v).getChildAt(0) : v;
            beginDrag(SRC_DRAWER, null, a.key, icon);
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
        playWave();
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
            g.setColors(th.accent, Theme.alpha(th.onTile, 0.45f));
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
        startSensors();
    }

    /** Accende bussola e contapassi solo se sulla home c'è il widget relativo. */
    private void startSensors() {
        if (!started || sensors == null) return;
        boolean compass = false, steps = false;
        for (Item it : items) {
            if ("compass".equals(it.type)) compass = true;
            if ("steps".equals(it.type)) steps = true;
        }
        sensors.start(this, compass, steps);
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
        } else if ("shortcut".equals(it.type)) {
            v = new AppTile(this, it, this, th, prefs.getBoolean("labels", false));
            v.setOnClickListener(x -> launchShortcut(it.data, x));
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
            startHomeDrag(it, x);
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
            case "compass":
                toast(State.compassAccuracy <= 1
                        ? "Bussola imprecisa: muovi il telefono disegnando un 8"
                        : "Se la bussola sbaglia, muovi il telefono disegnando un 8");
                break;
            case "countdown":
                editCountdown(it);
                break;
            case "world_clock":
                safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                break;
            case "steps":
                if (!SensorHub.stepPermission(this)) askStepPermission();
                else editStepGoal();
                break;
            case "photo": {
                String u = WData.get(it, "u", "");
                if (u.isEmpty() || Photos.failed(u)) pickPhoto(it);
                else {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse(u), "image/*");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    safeStart(i);
                }
                break;
            }
        }
    }

    // ---------- impostazioni dei nuovi widget ----------

    /** Dopo aver aggiunto un widget che ha bisogno di essere impostato. */
    private void configureNew(Item it) {
        switch (it.type) {
            case "countdown": editCountdown(it); break;
            case "photo": pickPhoto(it); break;
            case "steps":
                if (!SensorHub.stepPermission(this)) askStepPermission();
                break;
            case "world_clock":
                if (it.data == null || it.data.isEmpty()) {
                    WData.put(it, "z", WData.DEFAULT_ZONES);
                    saveLayout();
                }
                break;
        }
    }

    private void editCountdown(Item it) {
        Sheet.input(this, th, "Nome dell'evento", "es. Vacanze", WData.get(it, "t", ""), t -> {
            Calendar c = Calendar.getInstance();
            long cur = WData.getLong(it, "d", 0);
            if (cur > 0) c.setTimeInMillis(cur);
            else c.add(Calendar.DAY_OF_MONTH, 30);
            DatePickerDialog picker = new DatePickerDialog(this,
                    th.light ? android.R.style.Theme_DeviceDefault_Light_Dialog : android.R.style.Theme_DeviceDefault_Dialog,
                    (v, y, m, d) -> {
                        Calendar sel = Calendar.getInstance();
                        sel.set(y, m, d, 12, 0, 0);
                        sel.set(Calendar.MILLISECOND, 0);
                        WData.put(it, "t", t.isEmpty() ? "Evento" : t);
                        WData.put(it, "d", String.valueOf(sel.getTimeInMillis()));
                        saveLayout();
                        if (it.view != null) it.view.invalidate();
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            picker.setTitle("Data dell'evento");
            picker.show();
        });
    }

    private void pickPhoto(Item it) {
        pendingPhoto = it;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PHOTO);
        } catch (Exception e) {
            pendingPhoto = null;
            toast("Impossibile aprire la galleria");
        }
    }

    private void chooseCities(Item it) {
        List<String[]> cur = WData.zones(it);
        String[] labels = new String[WData.CITIES.length];
        for (int i = 0; i < labels.length; i++) {
            boolean on = false;
            for (String[] z : cur) if (z[1].equals(WData.CITIES[i][1])) on = true;
            labels[i] = (on ? "✓  " : "      ") + WData.CITIES[i][0] + "  ·  " + WData.offsetLabel(WData.CITIES[i][1]);
        }
        Sheet.list(this, th, "Città (max 5)", labels, -1, w -> {
            String[] c = WData.CITIES[w];
            String[] found = null;
            for (String[] z : cur) if (z[1].equals(c[1])) found = z;
            if (found != null) {
                if (cur.size() == 1) {
                    toast("Lascia almeno una città");
                } else cur.remove(found);
            } else if (cur.size() >= 5) {
                toast("Al massimo 5 città");
            } else {
                cur.add(new String[]{c[0], c[1]});
            }
            WData.setZones(it, cur);
            saveLayout();
            if (it.view != null) it.view.invalidate();
            chooseCities(it);
        });
    }

    private void askStepPermission() {
        if (Build.VERSION.SDK_INT >= 29) {
            requestPermissions(new String[]{"android.permission.ACTIVITY_RECOGNITION"}, REQ_STEPS);
        }
    }

    private void editStepGoal() {
        Sheet.input(this, th, "Obiettivo di passi", "es. 8000", String.valueOf(State.stepGoal), v -> {
            try {
                int g = Integer.parseInt(v.replaceAll("[^0-9]", ""));
                g = Math.max(500, Math.min(100000, g));
                prefs.edit().putInt("stepGoal", g).apply();
                State.stepGoal = g;
                refreshTiles("steps");
            } catch (NumberFormatException e) {
                toast("Scrivi un numero, es. 8000");
            }
        });
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

    public void onItemMoved(Item it) {
        saveLayout();
    }

    public void onItemMenu(Item it) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        int[][] sizes = Widgets.sizes(it.type);
        if (sizes.length > 1) {
            labels.add("Ridimensiona");
            acts.add(() -> startResize(it));
        }
        if (!it.isSys() && !it.isApp()) {
            labels.add("Colore");
            acts.add(() -> chooseTone(it));
        }
        switch (it.type) {
            case "photo": {
                boolean dots = "dots".equals(WData.get(it, "s", ""));
                labels.add("Cambia foto");
                acts.add(() -> pickPhoto(it));
                labels.add(dots ? "Effetto: foto normale" : "Effetto: foto a puntini");
                acts.add(() -> {
                    WData.put(it, "s", dots ? "" : "dots");
                    saveLayout();
                    if (it.view != null) it.view.invalidate();
                });
                break;
            }
            case "countdown":
                labels.add("Modifica evento");
                acts.add(() -> editCountdown(it));
                break;
            case "world_clock":
                labels.add("Scegli le città");
                acts.add(() -> chooseCities(it));
                break;
            case "steps":
                labels.add("Obiettivo di passi");
                acts.add(this::editStepGoal);
                break;
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
        String title = it.isApp() || "shortcut".equals(it.type) ? labelFor(it.data) : it.isSys() ? "Widget"
                : "folder".equals(it.type) ? FolderTile.name(it) : Widgets.name(it.type);
        showMenuWithShortcuts(title, it.isApp() ? appsByKey.get(it.data) : null, labels, acts);
    }

    @Override
    public void onEmptyLongPress(int page, int col, int row) {
        String[] opts = {"Widget Dot", "Widget di sistema", "Aggiungi app", "Sfondi", "Impostazioni",
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
        if ("shortcut".equals(it.type)) {
            String[] sp = scParts(it.data);
            try {
                repinShortcuts(sp[0], Long.parseLong(sp[2]), null);
            } catch (NumberFormatException ignored) {
            }
        }
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
        Sheet.list(this, th, "Widget Dot", names, -1, w -> {
            String type = Widgets.TYPES[w];
            int[] s = Widgets.sizes(type)[0];
            int tone = "alarm".equals(type) ? 2 : 0;
            Item it = new Item(type, col, row, s[0], s[1], tone, page);
            addItem(it, page, col, row);
            configureNew(it);
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
        if (req == REQ_PHOTO) {
            Item it = pendingPhoto;
            pendingPhoto = null;
            if (res != RESULT_OK || data == null || data.getData() == null || it == null) return;
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            Photos.forget(uri.toString());
            WData.put(it, "u", uri.toString());
            saveLayout();
            if (it.view != null) it.view.invalidate();
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
        String[] labels = {"Automatica", "Originale (non modificata)", "Simbolo (forzato)", "Simbolo rosso"};
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
        if (size <= 0 || key == null) return null;
        String ck = key + "@" + size;
        Bitmap b = iconCache.get(ck);
        if (b != null) return b;
        if (key.startsWith("sc:")) {
            b = shortcutIcon(key, size);
            if (b != null) iconCache.put(ck, b);
            return b;
        }
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
        if (key != null && key.startsWith("sc:")) return scParts(key)[3];
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
                beginDrag(SRC_DOCK, null, a.key, v);
                return true;
            });
            dock.addView(iv, lp);
        }
        if (dock.getChildCount() == 0) {
            TextView t = new TextView(this);
            t.setText("Trascina qui un'app dal cassetto");
            t.setTextColor(th.sub);
            t.setTypeface(th.bodyFace);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setGravity(Gravity.CENTER);
            dock.addView(t);
        }
    }

    private Dialog showAppMenu(AppEntry a, Boolean fromDock) {
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
        return showMenuWithShortcuts(a.label, a, labels, acts);
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

    // =====================================================================
    // Trascinamento: dal cassetto, dalla home e dal dock verso home, dock, cartelle e barra in alto
    // =====================================================================

    private static final int SRC_HOME = 0, SRC_DOCK = 1, SRC_DRAWER = 2;
    private static final int T_NONE = 0, T_BAR = 1, T_DOCK = 2, T_CELL = 3, T_MERGE = 4, T_BAD = 5;

    private int dragSrc = -1;
    private Item dragItem;
    private String dragKey;
    private View dragSourceView;
    private ImageView dragShadow;
    private int shadowW, shadowH, dragW = 1, dragH = 1;
    private float touchOffX, touchOffY, lastRawX, lastRawY, dragStartX, dragStartY;
    private boolean dragMoved;
    private Dialog dragMenu; // menù aperto subito alla pressione prolungata nel cassetto
    private LinearLayout dropBar;
    private TextView dropLeft, dropRight;
    private int tKind = T_NONE, tCol, tRow, tZone, tDockIndex;
    private Item tTarget;
    private int edgeDir;
    private final Runnable edgeRun = this::edgeTick;

    private TextView dropPill() {
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(th.labelFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setAllCaps(th.upperLabels);
        return t;
    }

    private void styleDropPill(TextView t, boolean hot) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(24));
        g.setColor(hot ? th.accent : th.sheetBg);
        if (!hot && th.light) g.setStroke(Math.max(1, px(1)), 0x22000000);
        t.setBackground(g);
        t.setTextColor(hot ? 0xFFFFFFFF : th.onTile);
    }

    private void buildDropBar() {
        dropBar = new LinearLayout(this);
        dropBar.setOrientation(LinearLayout.HORIZONTAL);
        dropBar.setGravity(Gravity.CENTER);
        dropBar.setVisibility(View.GONE);
        dropLeft = dropPill();
        dropRight = dropPill();
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, px(48), 1f);
        l.leftMargin = px(16);
        l.rightMargin = px(6);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, px(48), 1f);
        r.leftMargin = px(6);
        r.rightMargin = px(16);
        dropBar.addView(dropLeft, l);
        dropBar.addView(dropRight, r);
        dropBar.setPadding(0, px(40), 0, px(8));
        root.addView(dropBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        wave = new DotWave(this);
        root.addView(wave, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private DotWave wave;

    private void playWave() {
        if (wave == null) return;
        wave.bringToFront();
        int c = th.light ? 0xFF000000 : 0xFFFFFFFF;
        wave.post(() -> wave.play(c, 0.5f, 1f));
    }

    private int iconCellPx() {
        float c = grids.isEmpty() ? 0 : grids.get(0).getCell();
        return c > 0 ? Math.round(c * 0.86f) : px(62);
    }

    private void startHomeDrag(Item it, View v) {
        beginDrag(SRC_HOME, it, it.isApp() ? it.data : null, v);
    }

    private void beginDrag(int src, Item it, String key, View v) {
        if (dragSrc >= 0 || v == null || resizer != null) return;
        dragSrc = src;
        dragItem = it;
        dragKey = key;
        dragSourceView = v;
        dragMoved = false;
        dragStartX = lastRawX;
        dragStartY = lastRawY;
        dragW = it != null ? it.w : 1;
        dragH = it != null ? it.h : 1;
        tKind = T_NONE;

        Bitmap snap;
        if (src == SRC_HOME && v.getWidth() > 0) {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            snap = Bitmap.createBitmap(v.getWidth(), Math.max(1, v.getHeight()), Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(snap));
            shadowW = v.getWidth();
            shadowH = v.getHeight();
            touchOffX = lastRawX - loc[0];
            touchOffY = lastRawY - loc[1];
        } else {
            int s = iconCellPx();
            snap = key == null ? null : iconFor(key, s);
            shadowW = shadowH = s;
            touchOffX = s / 2f;
            touchOffY = s / 2f;
        }
        dragShadow = new ImageView(this);
        if (snap != null) dragShadow.setImageBitmap(snap);
        dragShadow.setElevation(px(10));
        dragShadow.setVisibility(src == SRC_DRAWER ? View.INVISIBLE : View.VISIBLE);
        root.addView(dragShadow, new FrameLayout.LayoutParams(shadowW, shadowH));
        positionShadow();
        dragShadow.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
        if (src != SRC_DRAWER) v.setAlpha(0.25f);

        TileGrid.anyDragging = true;
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // il tocco in corso non deve più arrivare alle altre viste (cassetto, pagine, widget)
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        super.dispatchTouchEvent(cancel);
        if (gestures != null) gestures.onTouchEvent(cancel);
        cancel.recycle();
        // nel cassetto il menù compare subito; se il dito si sposta si chiude e parte il trascinamento
        if (src == SRC_DRAWER && key != null) {
            AppEntry a = appsByKey.get(key);
            if (a != null) dragMenu = showAppMenu(a, null);
        }
    }

    private void positionShadow() {
        if (dragShadow == null) return;
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        dragShadow.setTranslationX(lastRawX - touchOffX - rl[0]);
        dragShadow.setTranslationY(lastRawY - touchOffY - rl[1]);
    }

    private void onDragEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!dragMoved && Math.hypot(lastRawX - dragStartX, lastRawY - dragStartY) > px(10)) {
                    dragMoved = true;
                    if (dragMenu != null) {
                        dragMenu.dismiss();
                        dragMenu = null;
                    }
                    if (dragSrc == SRC_DRAWER) hideDrawerNow();
                    if (dragSourceView != null && dragSrc != SRC_DRAWER) dragSourceView.setVisibility(View.INVISIBLE);
                    dragShadow.setVisibility(View.VISIBLE);
                    showDropBar();
                    // la home si rimpicciolisce verso il basso per fare spazio alla barra in alto
                    pager.setPivotX(pager.getWidth() / 2f);
                    pager.setPivotY(pager.getHeight());
                    pager.animate().scaleX(0.88f).scaleY(0.88f).setDuration(180).start();
                }
                if (dragMoved) {
                    positionShadow();
                    updateTarget();
                }
                break;
            case MotionEvent.ACTION_UP:
                finishDrag(true);
                break;
            case MotionEvent.ACTION_CANCEL:
                finishDrag(false);
                break;
        }
    }

    private void showDropBar() {
        boolean app = dragKey != null && !dragKey.startsWith("sc:") && appsByKey.containsKey(dragKey);
        dropLeft.setText(dragSrc == SRC_DRAWER ? "Info app" : dragSrc == SRC_DOCK ? "Togli dal dock" : "Rimuovi");
        dropRight.setText("Disinstalla");
        dropRight.setVisibility(app ? View.VISIBLE : View.GONE);
        if (dragSrc == SRC_DRAWER && !app) dropLeft.setVisibility(View.GONE);
        else dropLeft.setVisibility(View.VISIBLE);
        styleDropPill(dropLeft, false);
        styleDropPill(dropRight, false);
        dropBar.setAlpha(0f);
        dropBar.setVisibility(View.VISIBLE);
        dropBar.animate().alpha(1f).setDuration(150).start();
    }

    private void clearTargets() {
        for (TileGrid g : grids) g.clearPreview();
        dock.setBackground(null);
        if (dropBar.getVisibility() == View.VISIBLE) {
            styleDropPill(dropLeft, false);
            styleDropPill(dropRight, false);
        }
    }

    private boolean dockAccepts() {
        return dragKey != null && !dragKey.startsWith("sc:") && (dragItem == null || dragItem.isApp());
    }

    private Item itemAt(int page, int c, int r) {
        for (Item o : items) {
            if (o.page != page) continue;
            if (c >= o.col && c < o.col + o.w && r >= o.row && r < o.row + o.h) return o;
        }
        return null;
    }

    private void updateTarget() {
        clearTargets();
        tKind = T_NONE;
        tTarget = null;

        // 1) barra in alto (Rimuovi / Disinstalla / Info app)
        int[] bl = new int[2];
        dropBar.getLocationOnScreen(bl);
        if (lastRawY < bl[1] + dropBar.getHeight()) {
            boolean right = dropRight.getVisibility() == View.VISIBLE
                    && (dropLeft.getVisibility() != View.VISIBLE || lastRawX > root.getWidth() / 2f);
            tKind = T_BAR;
            tZone = right ? 2 : 1;
            styleDropPill(right ? dropRight : dropLeft, true);
            setEdge(0);
            return;
        }

        // 2) dock
        int[] dl = new int[2];
        dock.getLocationOnScreen(dl);
        if (lastRawY >= dl[1] - px(6) && dockAccepts()) {
            tKind = T_DOCK;
            int idx = 0;
            for (int i = 0; i < dock.getChildCount(); i++) {
                View c = dock.getChildAt(i);
                if (!(c instanceof ImageView) || c == dragSourceView) continue;
                int[] cl = new int[2];
                c.getLocationOnScreen(cl);
                if (cl[0] + c.getWidth() / 2f < lastRawX) idx++;
            }
            tDockIndex = idx;
            List<String> dk = dockKeys();
            boolean full = dk.size() >= MAX_DOCK && !dk.contains(dragKey);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(38));
            g.setColor(Theme.alpha(full ? th.sub : th.accent, 0.18f));
            g.setStroke(px(2), full ? th.sub : th.accent);
            dock.setBackground(g);
            setEdge(0);
            return;
        }

        // 3) griglia della pagina corrente
        int page = pager.getCurrent();
        if (page >= grids.size()) return;
        TileGrid g = grids.get(page);
        int[] gl = new int[2];
        g.getLocationOnScreen(gl);
        // coordinate locali della griglia (che durante il trascinamento è rimpicciolita)
        float sc = pager.getScaleX() > 0 ? pager.getScaleX() : 1f;
        float sx = (lastRawX - touchOffX - gl[0]) / sc, sy = (lastRawY - touchOffY - gl[1]) / sc;
        float cx = (lastRawX - touchOffX + shadowW / 2f - gl[0]) / sc;
        float cy = (lastRawY - touchOffY + shadowH / 2f - gl[1]) / sc;
        int rows = g.getRows();

        if (dragKey != null && !dragKey.startsWith("sc:") && (dragItem == null || dragItem.isApp())) {
            int[] cu = g.cellUnder(cx, cy);
            Item o = itemAt(page, cu[0], cu[1]);
            if (o != null && o != dragItem && (o.isApp() || "folder".equals(o.type))) {
                tKind = T_MERGE;
                tTarget = o;
                g.setPreview(TileGrid.PREVIEW_MERGE, o.col, o.row, o.w, o.h);
                setEdge(edgeDirAt());
                return;
            }
        }
        int col, row;
        if (dragW == 1 && dragH == 1) {
            int[] cu = g.cellUnder(cx, cy);
            col = cu[0];
            row = cu[1];
        } else {
            int[] nc = g.nearestCell(sx, sy);
            col = nc[0];
            row = nc[1];
        }
        col = Math.max(0, Math.min(TileGrid.COLS - dragW, col));
        row = Math.max(0, Math.min(rows - dragH, row));
        boolean ok = row >= 0 && canPlace(dragItem, page, col, row, dragW, dragH);
        tKind = ok ? T_CELL : T_BAD;
        tCol = col;
        tRow = row;
        g.setPreview(ok ? TileGrid.PREVIEW_OK : TileGrid.PREVIEW_BAD, col, row, dragW, dragH);
        setEdge(edgeDirAt());
    }

    private int edgeDirAt() {
        int edge = px(28);
        return lastRawX < edge ? -1 : lastRawX > root.getWidth() - edge ? 1 : 0;
    }

    private void setEdge(int dir) {
        if (dir == edgeDir) return;
        ui.removeCallbacks(edgeRun);
        edgeDir = dir;
        if (dir != 0) ui.postDelayed(edgeRun, 600);
    }

    /** Dito fermo al bordo: si passa alla pagina accanto (e se serve se ne crea una nuova). */
    private void edgeTick() {
        if (dragSrc < 0 || edgeDir == 0) return;
        int target = pager.getCurrent() + edgeDir;
        if (target < 0) return;
        if (target >= pages) {
            boolean lastHasItems = false;
            for (Item o : items) if (o.page == pages - 1 && o != dragItem) lastHasItems = true;
            if (!lastHasItems) return;
            addEmptyPage();
        }
        for (TileGrid g : grids) g.clearPreview();
        pager.snapTo(target);
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        ui.postDelayed(edgeRun, 900);
    }

    private void addEmptyPage() {
        pages++;
        TileGrid g = new TileGrid(this, pages - 1, this);
        g.setColors(th.accent, Theme.alpha(th.onTile, 0.45f));
        grids.add(g);
        pager.addView(g);
        indicator.invalidate();
    }

    private void finishDrag(boolean commit) {
        ui.removeCallbacks(edgeRun);
        edgeDir = 0;
        TileGrid.anyDragging = false;
        clearTargets();
        dropBar.setVisibility(View.GONE);
        pager.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        if (dragShadow != null) root.removeView(dragShadow);
        dragShadow = null;
        if (dragSourceView != null) {
            dragSourceView.setVisibility(View.VISIBLE);
            dragSourceView.setAlpha(1f);
        }
        int src = dragSrc;
        Item it = dragItem;
        String key = dragKey;
        boolean moved = dragMoved;
        int kind = tKind;
        boolean menuShown = dragMenu != null;
        dragMenu = null;
        dragSrc = -1;
        dragItem = null;
        dragKey = null;
        dragSourceView = null;
        if (!commit) return;
        if (!moved) {
            if (!menuShown) showMenuFor(src, it, key);
            return;
        }
        performDrop(src, it, key, kind);
    }

    private void showMenuFor(int src, Item it, String key) {
        if (src == SRC_HOME && it != null) {
            onItemMenu(it);
            return;
        }
        AppEntry a = key == null ? null : appsByKey.get(key);
        if (a != null) showAppMenu(a, src == SRC_DOCK ? Boolean.TRUE : null);
    }

    private void performDrop(int src, Item it, String key, int kind) {
        AppEntry a = key == null ? null : appsByKey.get(key);
        int page = pager.getCurrent();
        switch (kind) {
            case T_BAR:
                if (tZone == 2 && a != null) {
                    uninstall(a);
                } else if (tZone == 1) {
                    if (src == SRC_HOME && it != null) removeItem(it);
                    else if (src == SRC_DOCK) removeFromDock(key);
                    else if (a != null) appInfo(a);
                }
                return;
            case T_DOCK: {
                List<String> dk = dockKeys();
                int idx = tDockIndex;
                int old = dk.indexOf(key);
                if (old < 0 && dk.size() >= MAX_DOCK) {
                    toast("Il dock contiene al massimo " + MAX_DOCK + " app");
                    return;
                }
                if (old >= 0) {
                    dk.remove(old);
                    if (old < idx) idx--;
                }
                dk.add(Math.max(0, Math.min(dk.size(), idx)), key);
                prefs.edit().putBoolean("dockInit", true).apply();
                saveDock(dk);
                if (src == SRC_HOME && it != null) {
                    items.remove(it);
                    saveLayout();
                    buildPages();
                }
                buildDock();
                return;
            }
            case T_MERGE: {
                Item target = tTarget;
                if (target == null) return;
                if ("folder".equals(target.type)) {
                    List<String> apps = FolderTile.apps(target);
                    if (!apps.contains(key)) apps.add(key);
                    FolderTile.set(target, FolderTile.name(target), apps);
                } else {
                    int fs = target.w >= 2 && target.h >= 2 ? 2 : 1;
                    Item f = new Item("folder", target.col, target.row, fs, fs, 0, target.page);
                    List<String> apps = new ArrayList<>();
                    apps.add(target.data);
                    if (!apps.contains(key)) apps.add(key);
                    FolderTile.set(f, "Cartella", apps);
                    items.remove(target);
                    items.add(f);
                    toast("Cartella creata");
                }
                if (src == SRC_HOME && it != null) items.remove(it);
                if (src == SRC_DOCK) removeFromDock(key);
                saveLayout();
                buildPages();
                return;
            }
            case T_CELL:
                if (src == SRC_HOME && it != null) {
                    it.page = page;
                    it.col = tCol;
                    it.row = tRow;
                } else if (key != null) {
                    Item n = new Item("app", tCol, tRow, 1, 1, 0, page);
                    n.data = key;
                    items.add(n);
                    if (src == SRC_DOCK) removeFromDock(key);
                }
                saveLayout();
                buildPages();
                return;
            default:
                // posizione non valida: l'elemento torna dov'era
                if (src == SRC_HOME) buildPages();
        }
    }

    private void removeFromDock(String key) {
        List<String> dk = dockKeys();
        dk.remove(key);
        prefs.edit().putBoolean("dockInit", true).apply();
        saveDock(dk);
        buildDock();
    }

    private void uninstall(AppEntry a) {
        safeStart(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.component.getPackageName(), null)));
    }

    // =====================================================================
    // Ridimensionamento con la maniglia
    // =====================================================================

    private ResizeOverlay resizer;

    private void startResize(Item it) {
        if (it.view == null || it.page >= grids.size()) return;
        exitResize();
        resizer = new ResizeOverlay(this, it);
        root.addView(resizer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        toast("Trascina il pallino per ridimensionare · tocca fuori per finire");
    }

    private void exitResize() {
        if (resizer == null) return;
        root.removeView(resizer);
        resizer = null;
    }

    private class ResizeOverlay extends View {
        private final Item it;
        private final int[][] allowed;
        private int nw, nh;
        private boolean active, valid = true;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final int[] gl = new int[2], ol = new int[2];

        ResizeOverlay(Context c, Item it) {
            super(c);
            this.it = it;
            this.allowed = it.isSys() ? null : Widgets.sizes(it.type);
            nw = it.w;
            nh = it.h;
        }

        private TileGrid grid() {
            return grids.get(Math.min(it.page, grids.size() - 1));
        }

        /** Rettangolo dell'elemento con la dimensione provvisoria, in coordinate di questa vista. */
        private void rect() {
            TileGrid g = grid();
            g.getLocationOnScreen(gl);
            getLocationOnScreen(ol);
            float cell = g.getCell(), gap = g.getGap();
            float x = gl[0] - ol[0] + g.getPadH() + it.col * (cell + gap);
            float y = gl[1] - ol[1] + g.getPadTop() + it.row * (cell + gap);
            r.set(x, y, x + nw * cell + (nw - 1) * gap, y + nh * cell + (nh - 1) * gap);
        }

        @Override
        protected void onDraw(Canvas c) {
            rect();
            float m = Math.min(r.width(), r.height());
            float rad = (nw == 1 || nh == 1) ? m / 2f : Theme.radius(m, dp);
            int col = valid ? th.accent : th.sub;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(col, 0.12f));
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(px(2));
            p.setColor(col);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(r.right, r.bottom, px(14), p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(r.right, r.bottom, px(5), p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    rect();
                    if (Math.hypot(ev.getX() - r.right, ev.getY() - r.bottom) < px(44)) {
                        active = true;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } else {
                        exitResize();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!active) return true;
                    TileGrid g = grid();
                    float cell = g.getCell(), gap = g.getGap();
                    rect();
                    int w = Math.round((ev.getX() - r.left + gap) / (cell + gap));
                    int h = Math.round((ev.getY() - r.top + gap) / (cell + gap));
                    w = Math.max(1, Math.min(TileGrid.COLS - it.col, w));
                    h = Math.max(1, Math.min(g.getRows() - it.row, h));
                    if (allowed != null) {
                        int best = -1, bestD = Integer.MAX_VALUE;
                        for (int i = 0; i < allowed.length; i++) {
                            int[] s = allowed[i];
                            if (it.col + s[0] > TileGrid.COLS || it.row + s[1] > g.getRows()) continue;
                            int d = Math.abs(s[0] - w) + Math.abs(s[1] - h);
                            if (d < bestD) {
                                bestD = d;
                                best = i;
                            }
                        }
                        if (best >= 0) {
                            w = allowed[best][0];
                            h = allowed[best][1];
                        }
                    }
                    if (w != nw || h != nh) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    nw = w;
                    nh = h;
                    valid = canPlace(it, it.page, it.col, it.row, nw, nh);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!active) return true;
                    active = false;
                    if (valid && (nw != it.w || nh != it.h)) {
                        it.w = nw;
                        it.h = nh;
                        saveLayout();
                        buildPages();
                        postDelayed(this::invalidate, 80);
                    } else {
                        nw = it.w;
                        nh = it.h;
                        valid = true;
                        invalidate();
                    }
                    return true;
            }
            return true;
        }
    }

    // =====================================================================
    // Scorciatoie delle app: "Aggiungi alla schermata Home" e azioni rapide
    // =====================================================================

    private final Map<String, ShortcutInfo> scCache = new HashMap<>();

    private UserManager userManager() {
        return (UserManager) getSystemService(Context.USER_SERVICE);
    }

    private static String[] scParts(String key) {
        String[] p = key.substring(3).split("\\|", 4);
        return p.length == 4 ? p : new String[]{"", "", "0", ""};
    }

    private ShortcutInfo scInfo(String key) {
        if (scCache.containsKey(key)) return scCache.get(key);
        ShortcutInfo r = null;
        try {
            String[] p = scParts(key);
            UserHandle u = userManager().getUserForSerialNumber(Long.parseLong(p[2]));
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery()
                    .setPackage(p[0])
                    .setShortcutIds(Collections.singletonList(p[1]))
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> l = launcherApps.getShortcuts(q, u);
            if (l != null && !l.isEmpty()) r = l.get(0);
        } catch (Exception ignored) {
        }
        scCache.put(key, r);
        return r;
    }

    private Bitmap shortcutIcon(String key, int size) {
        ShortcutInfo si = scInfo(key);
        Drawable d = null;
        try {
            if (si != null) d = launcherApps.getShortcutIconDrawable(si, getResources().getDisplayMetrics().densityDpi);
        } catch (Exception ignored) {
        }
        if (d == null) {
            String pkg = scParts(key)[0];
            for (AppEntry a : allApps) {
                if (a.component.getPackageName().equals(pkg)) {
                    d = a.icon;
                    break;
                }
            }
        }
        if (d == null) return null;
        return IconFactory.make(d, size, prefs.getString("icons", IconFactory.AUTO), false, IconFactory.MODE_AUTO, th);
    }

    private void launchShortcut(String key, View v) {
        try {
            String[] p = scParts(key);
            UserHandle u = userManager().getUserForSerialNumber(Long.parseLong(p[2]));
            Rect r = null;
            Bundle opts = null;
            if (v != null && v.getWidth() > 0) {
                r = new Rect();
                v.getGlobalVisibleRect(r);
                opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle();
            }
            launcherApps.startShortcut(p[0], p[1], r, opts, u);
        } catch (Exception e) {
            toast("Scorciatoia non disponibile");
        }
    }

    /** Riaggiorna l'elenco delle scorciatoie fissate di un'app (dopo un'aggiunta o una rimozione). */
    private void repinShortcuts(String pkg, long serial, String extraId) {
        List<String> ids = new ArrayList<>();
        for (Item o : items) {
            if (!"shortcut".equals(o.type)) continue;
            String[] p = scParts(o.data);
            if (p[0].equals(pkg) && p[2].equals(String.valueOf(serial)) && !ids.contains(p[1])) ids.add(p[1]);
        }
        if (extraId != null && !ids.contains(extraId)) ids.add(extraId);
        try {
            launcherApps.pinShortcuts(pkg, ids, userManager().getUserForSerialNumber(serial));
        } catch (Exception ignored) {
        }
    }

    /** Scorciatoie arrivate da "Aggiungi alla schermata Home" mentre la home non era aperta. */
    private void consumePendingShortcuts() {
        Set<String> pend = prefs.getStringSet("pendingSc", null);
        if (pend == null || pend.isEmpty()) return;
        List<String> keys = new ArrayList<>(pend);
        prefs.edit().remove("pendingSc").apply();
        boolean added = false;
        for (String k : keys) {
            boolean exists = false;
            for (Item o : items) if (k.equals(o.data)) exists = true;
            if (exists) continue;
            scCache.remove(k);
            Item it = new Item("shortcut", 0, 0, 1, 1, 0, pager.getCurrent());
            it.data = k;
            int[] spot = findSpot(it, pager.getCurrent(), 0, 0, true);
            it.page = spot[0];
            it.col = spot[1];
            it.row = spot[2];
            items.add(it);
            added = true;
        }
        if (added) {
            saveLayout();
            buildPages();
        }
    }

    /** Azioni rapide dell'app (le stesse che mostrano gli altri launcher tenendo premuta l'icona). */
    private List<ShortcutInfo> appShortcuts(AppEntry a) {
        List<ShortcutInfo> out = new ArrayList<>();
        try {
            if (!launcherApps.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery()
                    .setPackage(a.component.getPackageName())
                    .setActivity(a.component)
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> l = launcherApps.getShortcuts(q, a.user);
            if (l == null) return out;
            List<ShortcutInfo> sorted = new ArrayList<>(l);
            Collections.sort(sorted, (x, y) -> {
                if (x.isDeclaredInManifest() != y.isDeclaredInManifest()) return x.isDeclaredInManifest() ? -1 : 1;
                return Integer.compare(x.getRank(), y.getRank());
            });
            for (ShortcutInfo si : sorted) {
                if (out.size() >= 4) break;
                if (si.isEnabled()) out.add(si);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String shortcutLabel(ShortcutInfo si) {
        CharSequence l = si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel();
        return l == null ? "" : l.toString();
    }

    private Bitmap shortcutRowIcon(ShortcutInfo si) {
        try {
            Drawable d = launcherApps.getShortcutIconDrawable(si, getResources().getDisplayMetrics().densityDpi);
            return d == null ? null : IconFactory.original(d, px(26));
        } catch (Exception e) {
            return null;
        }
    }

    private void pinShortcutToHome(ShortcutInfo si) {
        long serial = userManager().getSerialNumberForUser(si.getUserHandle());
        String key = AddItemActivity.key(si.getPackage(), si.getId(), serial, shortcutLabel(si));
        for (Item o : items) {
            if (key.equals(o.data)) {
                toast("È già sulla home");
                return;
            }
        }
        repinShortcuts(si.getPackage(), serial, si.getId());
        scCache.remove(key);
        Item it = new Item("shortcut", 0, 0, 1, 1, 0, pager.getCurrent());
        it.data = key;
        closeDrawer();
        addItem(it, pager.getCurrent(), 0, 0);
        toast("Scorciatoia aggiunta alla home");
    }

    /** Mostra il menu di un'app con le azioni rapide in cima. */
    private Dialog showMenuWithShortcuts(String title, AppEntry a, List<String> labels, List<Runnable> acts) {
        List<ShortcutInfo> scs = a == null ? new ArrayList<>() : appShortcuts(a);
        int n = scs.size();
        String[] all = new String[n + labels.size()];
        Bitmap[] icons = new Bitmap[all.length];
        for (int i = 0; i < n; i++) {
            all[i] = shortcutLabel(scs.get(i));
            icons[i] = shortcutRowIcon(scs.get(i));
        }
        for (int i = 0; i < labels.size(); i++) all[n + i] = labels.get(i);
        Dialog d = Sheet.list(this, th, title, all, icons, -1, w -> {
            if (w < n) {
                ShortcutInfo si = scs.get(w);
                try {
                    launcherApps.startShortcut(si.getPackage(), si.getId(), null, null, si.getUserHandle());
                } catch (Exception e) {
                    toast("Azione non disponibile");
                }
            } else {
                acts.get(w - n).run();
            }
        }, w -> {
            if (w < n) pinShortcutToHome(scs.get(w));
        });
        if (n > 0 && !prefs.getBoolean("scHint", false)) {
            prefs.edit().putBoolean("scHint", true).apply();
            toast("Tieni premuta un'azione per metterla sulla home");
        }
        return d;
    }

    // =====================================================================
    // Aggiornamenti: controllo dell'ultima versione su GitHub
    // =====================================================================

    static final String UPDATE_URL = "https://github.com/Rex11107/dot-launcher/releases/latest/download/DotLauncher.apk";

    @SuppressWarnings("deprecation")
    private long versionCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void checkUpdate(boolean manual) {
        long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong("updCheck", 0) < 6 * 60 * 60 * 1000L) return;
        prefs.edit().putLong("updCheck", now).apply();
        if (manual) toast("Controllo gli aggiornamenti…");
        netExec.execute(() -> {
            try {
                JSONObject j = new JSONObject(http("https://api.github.com/repos/Rex11107/dot-launcher/releases/latest"));
                String tag = j.optString("tag_name", "");
                long n = Long.parseLong(tag.substring(tag.lastIndexOf('.') + 1));
                if (n > versionCode()) {
                    if (!manual && tag.equals(prefs.getString("updSeen", ""))) return;
                    prefs.edit().putString("updSeen", tag).apply();
                    ui.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Sheet.confirm(this, th, "Aggiornamento",
                                "È pronta la versione " + tag.replace("v", "") + " (hai la " + versionName()
                                        + "). Si installa sopra quella attuale: home e impostazioni restano.",
                                "Scarica", () -> safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_URL))));
                    });
                } else if (manual) {
                    ui.post(() -> toast("Hai già l'ultima versione (" + versionName() + ")"));
                }
            } catch (Exception e) {
                if (manual) ui.post(() -> toast("Impossibile controllare gli aggiornamenti"));
            }
        });
    }

    // ---------- impostazioni ----------

    private void showSettings() {
        String style = prefs.getString("style", "classic");
        String mode = prefs.getString("mode", "dark");
        String icons = prefs.getString("icons", IconFactory.AUTO);
        boolean wall = prefs.getBoolean("wall", false);
        boolean labels = prefs.getBoolean("labels", false);
        String city = prefs.getString("city", "");
        boolean ghost = prefs.getBoolean("ghost", true);
        boolean anim = prefs.getBoolean("anim", true);
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        names.add("Stile: " + ("nuovo".equals(style) ? "Nuovo (5.0)" : "Classico"));
        acts.add(() -> choose("Stile", new String[]{"Classico", "Nuovo (5.0)"},
                new String[]{"classic", "nuovo"}, "style", style));
        names.add("Tema: " + ("light".equals(mode) ? "Chiaro" : "auto".equals(mode) ? "Automatico"
                : "grey".equals(mode) ? "Scuro (grigio)" : "Extra scuro (nero)"));
        acts.add(() -> choose("Tema", new String[]{"Extra scuro (nero)", "Scuro (grigio)", "Chiaro", "Automatico"},
                new String[]{"dark", "grey", "light", "auto"}, "mode", mode));
        names.add("Icone: " + (IconFactory.INVERSE.equals(icons) ? "invertite"
                : IconFactory.COLOR.equals(icons) ? "a colori" : "monocromatiche"));
        acts.add(() -> choose("Icone", new String[]{"Monocromatiche", "Invertite", "A colori"},
                new String[]{IconFactory.AUTO, IconFactory.INVERSE, IconFactory.COLOR}, "icons", icons));
        names.add("Griglia della home: " + TileGrid.COLS + " colonne");
        acts.add(() -> setColumns(TileGrid.COLS == 5 ? 4 : 5));
        names.add("Sfondo: " + (wall ? "sfondo di sistema" : "tinta unita"));
        acts.add(() -> toggle("wall", wall));
        names.add("Nomi delle app sulla home: " + (labels ? "sì" : "no"));
        acts.add(() -> toggle("labels", labels));
        names.add("Orologio: " + (h24 ? "24 ore" : "12 ore"));
        acts.add(() -> toggle("h24", h24));
        names.add("Meteo: " + (city.isEmpty() ? "posizione automatica" : city));
        acts.add(this::askCity);
        names.add("Pacchetto di icone: " + packLabel());
        acts.add(this::pickIconPack);
        names.add("Punti spenti sui display: " + (ghost ? "sì" : "no"));
        acts.add(() -> toggle("ghost", ghost));
        names.add("Animazioni a punti: " + (anim ? "sì" : "no"));
        acts.add(() -> toggle("anim", anim));
        names.add("App nascoste…");
        acts.add(this::manageHidden);
        names.add("Esporta configurazione");
        acts.add(this::exportBackup);
        names.add("Importa configurazione");
        acts.add(this::importBackup);
        names.add("Versione " + versionName() + " · cerca aggiornamenti");
        acts.add(() -> checkUpdate(true));
        Sheet.list(this, th, "Impostazioni", names.toArray(new String[0]), -1, w -> acts.get(w).run());
    }

    private void toggle(String key, boolean cur) {
        prefs.edit().putBoolean(key, !cur).apply();
        recreate();
    }

    /** Passa da 4 a 5 colonne (o viceversa): i widget a tutta larghezza si adattano. */
    private void setColumns(int n) {
        int old = TileGrid.COLS;
        if (n == old) return;
        TileGrid.COLS = n;
        for (Item it : items) {
            if (it.isApp() || "folder".equals(it.type) || "shortcut".equals(it.type)) continue;
            if (it.w != old) continue;
            if (n < old || canPlace(it, it.page, it.col, it.row, n, it.h)) it.w = n;
        }
        saveLayout();
        prefs.edit().putInt("cols", n).apply();
        recreate();
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
        Sheet.grid(this, th, "App nascoste", hiddenApps.size(),
                i -> iconFor(hiddenApps.get(i).key, px(54)), i -> hiddenApps.get(i).label,
                i -> launch(hiddenApps.get(i), null),
                i -> hiddenAppMenu(hiddenApps.get(i)),
                "Tocca per aprire · tieni premuto per mostrarla o disinstallarla");
    }

    private void hiddenAppMenu(AppEntry a) {
        String[] opts = {"Mostra nel cassetto", "Info app", "Disinstalla"};
        Sheet.list(this, th, a.label, opts, -1, w -> {
            switch (w) {
                case 0:
                    Set<String> s = hidden();
                    s.remove(a.key);
                    prefs.edit().putStringSet("hidden", s).apply();
                    adapter.refresh();
                    toast(a.label + " di nuovo nel cassetto");
                    if (!s.isEmpty()) manageHidden();
                    break;
                case 1: appInfo(a); break;
                default: uninstall(a); break;
            }
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
        if (req == REQ_STEPS) {
            State.stepPerm = SensorHub.stepPermission(this);
            if (!State.stepPerm) toast("Senza il permesso \"Attività fisica\" il contapassi non può contare");
            startSensors();
            refreshTiles("steps");
            return;
        }
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
