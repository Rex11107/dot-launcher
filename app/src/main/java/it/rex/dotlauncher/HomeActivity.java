package it.rex.dotlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity {
    private static final int REQ_BIND = 1;
    private static final int REQ_CONFIG = 2;
    private static final int REQ_LOC = 3;
    private static final int HOST_ID = 0x0D07;
    private static final int RED = 0xFFD71921;
    private static final int GRAY = 0xFF9E9E9E;
    private static final int MAX_DOCK = 5;

    private SharedPreferences prefs;
    private float dp;

    private FrameLayout root;
    private LinearLayout home;
    private LinearLayout widgetBox;
    private LinearLayout dock;
    private DotTextView clock;
    private DotTextView dateView;
    private DotTextView weatherView;

    private LinearLayout drawer;
    private EditText search;
    private GridView grid;
    private AppAdapter adapter;
    private boolean drawerOpen;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<WidgetSlot> widgets = new ArrayList<>();
    private LauncherApps launcherApps;
    private AppWidgetManager awm;
    private WidgetHost host;
    private int pendingWidgetId = -1;

    private final ExecutorService iconExec = Executors.newSingleThreadExecutor();
    private final ExecutorService netExec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private GestureDetector gestures;
    private boolean receiverOn;

    // ---------- modelli ----------

    static class AppEntry {
        String label;
        String key;
        ComponentName component;
        UserHandle user;
        Bitmap icon;
    }

    static class WidgetSlot {
        int id;
        int heightDp;

        WidgetSlot(int id, int h) {
            this.id = id;
            this.heightDp = h;
        }
    }

    // ---------- ciclo di vita ----------

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    private final LauncherApps.Callback appsCallback = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String p, UserHandle u) { loadApps(); }
        @Override public void onPackageAdded(String p, UserHandle u) { loadApps(); }
        @Override public void onPackageChanged(String p, UserHandle u) { loadApps(); }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { loadApps(); }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { loadApps(); }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("dot", MODE_PRIVATE);
        if (saved != null) pendingWidgetId = saved.getInt("pw", -1);

        launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        awm = AppWidgetManager.getInstance(this);
        host = new WidgetHost(getApplicationContext(), HOST_ID);

        buildUi();
        setContentView(root);
        applyBackground();

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < Math.abs(dx) * 1.3f) return false;
                float min = 70 * dp;
                if (!drawerOpen) {
                    if (dy < -min) openDrawer();
                    else if (dy > min) expandNotifications();
                } else if (dy > min && !grid.canScrollVertically(-1)) {
                    closeDrawer();
                }
                return false;
            }
        });

        launcherApps.registerCallback(appsCallback, ui);
        loadApps();
        restoreWidgets();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pw", pendingWidgetId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            host.startListening();
        } catch (Exception ignored) {
        }
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(timeReceiver, f);
            receiverOn = true;
        }
        updateClock();
        maybeRefreshWeather();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            host.stopListening();
        } catch (Exception ignored) {
        }
        if (receiverOn) {
            unregisterReceiver(timeReceiver);
            receiverOn = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        launcherApps.unregisterCallback(appsCallback);
        iconExec.shutdownNow();
        netExec.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Tasto Home premuto mentre siamo già qui
        if (drawerOpen) closeDrawer();
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

        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(px(24), px(36), px(24), px(12));
        home.setClickable(true);
        home.setLongClickable(true);
        home.setOnLongClickListener(v -> {
            showHomeMenu();
            return true;
        });

        clock = new DotTextView(this);
        clock.setFitWidth(true, 12 * dp);
        clock.setAccent(":", RED);
        clock.setOnClickListener(v -> safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        home.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        dateView = new DotTextView(this);
        dateView.setPitch(4.2f * dp);
        dateView.setColor(Color.WHITE);
        dateView.setOnClickListener(v -> {
            Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
            ContentUris.appendId(b, System.currentTimeMillis());
            safeStart(new Intent(Intent.ACTION_VIEW).setData(b.build()));
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = px(22);
        home.addView(dateView, dlp);

        weatherView = new DotTextView(this);
        weatherView.setPitch(4.2f * dp);
        weatherView.setColor(GRAY);
        weatherView.setAccent("°", RED);
        weatherView.setText(prefs.getString("wtext", "METEO"));
        weatherView.setOnClickListener(v -> refreshWeather(true));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = px(12);
        home.addView(weatherView, wlp);

        widgetBox = new LinearLayout(this);
        widgetBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = px(28);
        home.addView(widgetBox, blp);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        home.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(76)));

        DotTextView hint = new DotTextView(this);
        hint.setPitch(2.2f * dp);
        hint.setColor(0xFF555555);
        hint.setCenter(true);
        hint.setText("APP");
        hint.setOnClickListener(v -> openDrawer());
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = px(6);
        hint.setPadding(0, px(6), 0, px(6));
        home.addView(hint, hlp);

        root.addView(home, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildDrawer();
        root.addView(drawer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildDrawer() {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xF5000000);
        drawer.setPadding(px(16), px(28), px(16), 0);
        drawer.setVisibility(View.GONE);
        drawer.setClickable(true);

        DotTextView title = new DotTextView(this);
        title.setPitch(3.4f * dp);
        title.setText("APP");
        title.setAccent(".", RED);
        title.setPadding(px(8), 0, 0, 0);
        drawer.addView(title);

        search = new EditText(this);
        search.setHint("Cerca");
        search.setHintTextColor(0xFF6E6E6E);
        search.setTextColor(Color.WHITE);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0xFF1A1A1A);
        sb.setCornerRadius(px(24));
        search.setBackground(sb);
        search.setPadding(px(18), px(12), px(18), px(12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.setQuery(s.toString());
            }
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
            showAppMenu(adapter.getItem(pos));
            return true;
        });
        drawer.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        search.setText("");
        drawer.setAlpha(0f);
        drawer.setTranslationY(px(60));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).translationY(0).setDuration(180).start();
        grid.setSelection(0);
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        hideKeyboard();
        drawer.animate().alpha(0f).translationY(px(60)).setDuration(150)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private void applyBackground() {
        boolean wall = prefs.getBoolean("wall", false);
        if (wall) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            root.setBackgroundColor(0x40000000);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            root.setBackgroundColor(Color.BLACK);
        }
    }

    private void updateClock() {
        boolean h24 = prefs.getBoolean("h24", DateFormat.is24HourFormat(this));
        Date now = new Date();
        clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.ITALIAN).format(now));
        dateView.setText(new SimpleDateFormat("EEE d MMM", Locale.ITALIAN).format(now)
                .replace(".", ""));
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
        }
    }

    // ---------- app ----------

    private void loadApps() {
        final String style = prefs.getString("icons", IconFactory.MONO);
        final int size = px(52);
        iconExec.execute(() -> {
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
                            a.icon = IconFactory.make(i.getIcon(0), style, size);
                        } catch (Exception e) {
                            a.icon = IconFactory.make(null, style, size);
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
                adapter.refresh();
                buildDock();
            });
        });
    }

    private AppEntry findApp(String key) {
        for (AppEntry a : allApps) if (a.key.equals(key)) return a;
        return null;
    }

    private AppEntry findByPackage(String pkg) {
        for (AppEntry a : allApps) if (a.component.getPackageName().equals(pkg)) return a;
        return null;
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
            if (drawerOpen) ui.postDelayed(this::closeDrawer, 300);
        } catch (Exception e) {
            toast("Impossibile aprire " + a.label);
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
                AppEntry a = findByPackage(ri.activityInfo.packageName);
                if (a != null && !keys.contains(a.key)) keys.add(a.key);
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
        int size = px(52);
        for (String k : keys) {
            AppEntry a = findApp(k);
            if (a == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(a.icon);
            iv.setContentDescription(a.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = px(8);
            lp.rightMargin = px(8);
            iv.setOnClickListener(v -> launch(a, v));
            iv.setOnLongClickListener(v -> {
                showAppMenu(a);
                return true;
            });
            dock.addView(iv, lp);
        }
        if (dock.getChildCount() == 0) {
            TextView t = new TextView(this);
            t.setText("Tieni premuta un'app nel cassetto per aggiungerla qui");
            t.setTextColor(0xFF666666);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setGravity(Gravity.CENTER);
            dock.addView(t);
        }
    }

    private void showAppMenu(AppEntry a) {
        final List<String> dk = dockKeys();
        final boolean inDock = dk.contains(a.key);
        String[] items = {
                inDock ? "Rimuovi dalla home" : "Aggiungi alla home",
                "Nascondi dal cassetto",
                "Info app",
                "Disinstalla"
        };
        dialog().setTitle(a.label).setItems(items, (d, which) -> {
            switch (which) {
                case 0:
                    if (inDock) dk.remove(a.key);
                    else if (dk.size() >= MAX_DOCK) {
                        toast("La home contiene al massimo " + MAX_DOCK + " app");
                        return;
                    } else dk.add(a.key);
                    prefs.edit().putBoolean("dockInit", true).apply();
                    saveDock(dk);
                    buildDock();
                    break;
                case 1:
                    Set<String> h = hidden();
                    h.add(a.key);
                    prefs.edit().putStringSet("hidden", h).apply();
                    adapter.refresh();
                    break;
                case 2:
                    try {
                        launcherApps.startAppDetailsActivity(a.component, a.user, null, null);
                    } catch (Exception e) {
                        toast("Impossibile aprire le informazioni");
                    }
                    break;
                case 3:
                    safeStart(new Intent(Intent.ACTION_DELETE,
                            Uri.fromParts("package", a.component.getPackageName(), null)));
                    break;
            }
        }).show();
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
                cell.addView(iv, new LinearLayout.LayoutParams(px(52), px(52)));
                tv = new TextView(HomeActivity.this);
                tv.setTextColor(0xFFDDDDDD);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(6);
                cell.addView(tv, lp);
            }
            AppEntry a = shown.get(pos);
            iv.setImageBitmap(a.icon);
            tv.setText(a.label);
            return cell;
        }
    }

    // ---------- menu e impostazioni ----------

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    private void showHomeMenu() {
        String[] items = {"Aggiungi widget", "Impostazioni", "Cambia sfondo", "Launcher predefinito"};
        dialog().setItems(items, (d, w) -> {
            if (w == 0) pickWidget();
            else if (w == 1) showSettings();
            else if (w == 2) safeStart(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Sfondo"));
            else safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
        }).show();
    }

    private void showSettings() {
        boolean h24 = prefs.getBoolean("h24", DateFormat.is24HourFormat(this));
        String style = prefs.getString("icons", IconFactory.MONO);
        boolean wall = prefs.getBoolean("wall", false);
        String city = prefs.getString("city", "");
        String styleLabel = IconFactory.MONO.equals(style) ? "monocromatiche"
                : IconFactory.GRAY.equals(style) ? "bianco e nero" : "a colori";
        String[] items = {
                "Orologio: " + (h24 ? "24 ore" : "12 ore"),
                "Icone: " + styleLabel,
                "Sfondo: " + (wall ? "sfondo di sistema" : "nero"),
                "Meteo: " + (city.isEmpty() ? "posizione automatica" : city),
                "App nascoste…"
        };
        dialog().setTitle("Impostazioni").setItems(items, (d, w) -> {
            switch (w) {
                case 0:
                    prefs.edit().putBoolean("h24", !h24).apply();
                    updateClock();
                    break;
                case 1:
                    pickIconStyle();
                    break;
                case 2:
                    prefs.edit().putBoolean("wall", !wall).apply();
                    applyBackground();
                    break;
                case 3:
                    askCity();
                    break;
                case 4:
                    manageHidden();
                    break;
            }
        }).show();
    }

    private void pickIconStyle() {
        String[] labels = {"Monocromatiche (stile Nothing)", "Bianco e nero", "A colori"};
        String[] values = {IconFactory.MONO, IconFactory.GRAY, IconFactory.COLOR};
        String cur = prefs.getString("icons", IconFactory.MONO);
        int sel = Arrays.asList(values).indexOf(cur);
        dialog().setTitle("Icone").setSingleChoiceItems(labels, sel, (d, w) -> {
            prefs.edit().putString("icons", values[w]).apply();
            d.dismiss();
            loadApps();
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

    // ---------- widget ----------

    private void restoreWidgets() {
        widgets.clear();
        String s = prefs.getString("widgets", "");
        if (!s.isEmpty()) {
            for (String part : s.split(";")) {
                String[] f = part.split(",");
                if (f.length != 2) continue;
                try {
                    widgets.add(new WidgetSlot(Integer.parseInt(f[0]), Integer.parseInt(f[1])));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        rebuildWidgets();
    }

    private void saveWidgets() {
        StringBuilder sb = new StringBuilder();
        for (WidgetSlot w : widgets) {
            if (sb.length() > 0) sb.append(';');
            sb.append(w.id).append(',').append(w.heightDp);
        }
        prefs.edit().putString("widgets", sb.toString()).apply();
    }

    private void rebuildWidgets() {
        widgetBox.removeAllViews();
        List<WidgetSlot> gone = new ArrayList<>();
        for (WidgetSlot w : widgets) {
            if (!addWidgetView(w)) gone.add(w);
        }
        if (!gone.isEmpty()) {
            widgets.removeAll(gone);
            saveWidgets();
        }
    }

    private boolean addWidgetView(WidgetSlot w) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(w.id);
        if (info == null) return false;
        AppWidgetHostView v = host.createView(getApplicationContext(), w.id, info);
        v.setOnLongClickListener(x -> {
            showWidgetMenu(w);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(w.heightDp));
        lp.bottomMargin = px(12);
        widgetBox.addView(v, lp);
        int widthDp = Math.round((getResources().getDisplayMetrics().widthPixels - px(48)) / dp);
        Bundle o = new Bundle();
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, w.heightDp);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, w.heightDp);
        try {
            awm.updateAppWidgetOptions(w.id, o);
        } catch (Exception ignored) {
        }
        return true;
    }

    private void showWidgetMenu(WidgetSlot w) {
        String[] items = {"Più alto", "Più basso", "Sposta su", "Sposta giù", "Rimuovi"};
        dialog().setTitle("Widget").setItems(items, (d, which) -> {
            int i = widgets.indexOf(w);
            switch (which) {
                case 0: w.heightDp = Math.min(w.heightDp + 40, 600); break;
                case 1: w.heightDp = Math.max(w.heightDp - 40, 40); break;
                case 2: if (i > 0) Collections.swap(widgets, i, i - 1); break;
                case 3: if (i >= 0 && i < widgets.size() - 1) Collections.swap(widgets, i, i + 1); break;
                case 4:
                    widgets.remove(w);
                    try {
                        host.deleteAppWidgetId(w.id);
                    } catch (Exception ignored) {
                    }
                    break;
            }
            saveWidgets();
            rebuildWidgets();
        }).show();
    }

    private void pickWidget() {
        final List<AppWidgetProviderInfo> providers = new ArrayList<>(awm.getInstalledProviders());
        if (providers.isEmpty()) {
            toast("Nessun widget disponibile");
            return;
        }
        final PackageManager pm = getPackageManager();
        final List<String> labels = new ArrayList<>();
        for (AppWidgetProviderInfo p : providers) labels.add(widgetLabel(pm, p));
        final Collator col = Collator.getInstance(Locale.ITALIAN);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) order.add(i);
        Collections.sort(order, (a, b) -> col.compare(labels.get(a), labels.get(b)));
        final String[] arr = new String[order.size()];
        final AppWidgetProviderInfo[] infos = new AppWidgetProviderInfo[order.size()];
        for (int i = 0; i < order.size(); i++) {
            arr[i] = labels.get(order.get(i));
            infos[i] = providers.get(order.get(i));
        }
        dialog().setTitle("Aggiungi widget").setItems(arr, (d, w) -> bindWidget(infos[w])).show();
    }

    private String widgetLabel(PackageManager pm, AppWidgetProviderInfo p) {
        String app;
        try {
            app = String.valueOf(pm.getApplicationLabel(
                    pm.getApplicationInfo(p.provider.getPackageName(), 0)));
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
                // si aggiunge comunque senza configurazione
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
        int h = Math.max(80, Math.round(info.minHeight / dp) + 16);
        WidgetSlot w = new WidgetSlot(id, h);
        widgets.add(w);
        saveWidgets();
        addWidgetView(w);
        pendingWidgetId = -1;
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

    // ---------- meteo (Open-Meteo, gratuito e senza chiave) ----------

    private void maybeRefreshWeather() {
        long age = System.currentTimeMillis() - prefs.getLong("wt", 0);
        if (age > 30 * 60 * 1000L) refreshWeather(false);
    }

    private void refreshWeather(boolean byUser) {
        if (prefs.contains("mlat")) {
            fetchWeather(Double.longBitsToDouble(prefs.getLong("mlat", 0)),
                    Double.longBitsToDouble(prefs.getLong("mlon", 0)));
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locateAndFetch();
        } else if (byUser) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        } else if (!prefs.contains("wtext")) {
            weatherView.setText("METEO: TOCCA");
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req != REQ_LOC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            locateAndFetch();
        } else {
            weatherView.setText("IMPOSTA CITTA");
            askCity();
        }
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
            fetchWeather(best.getLatitude(), best.getLongitude());
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        ? LocationManager.NETWORK_PROVIDER : LocationManager.PASSIVE_PROVIDER;
                lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                    if (loc != null) fetchWeather(loc.getLatitude(), loc.getLongitude());
                    else weatherView.setText("IMPOSTA CITTA");
                });
                return;
            } catch (Exception ignored) {
            }
        }
        weatherView.setText("IMPOSTA CITTA");
    }

    private void fetchWeather(double lat, double lon) {
        netExec.execute(() -> {
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
                JSONObject cur = new JSONObject(http(url)).getJSONObject("current");
                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                String txt = t + "° " + describe(cur.getInt("weather_code"));
                prefs.edit().putString("wtext", txt).putLong("wt", System.currentTimeMillis()).apply();
                ui.post(() -> weatherView.setText(txt));
            } catch (Exception e) {
                ui.post(() -> weatherView.setText(prefs.getString("wtext", "METEO --")));
            }
        });
    }

    private void geocode(String city) {
        weatherView.setText("...");
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
                fetchWeather(lat, lon);
            } catch (Exception e) {
                ui.post(() -> {
                    weatherView.setText("CITTA ?");
                    toast("Città non trovata");
                });
            }
        });
    }

    private static String describe(int code) {
        if (code == 0) return "SERENO";
        if (code <= 2) return "POCO NUVOLOSO";
        if (code == 3) return "NUVOLOSO";
        if (code == 45 || code == 48) return "NEBBIA";
        if (code >= 51 && code <= 57) return "PIOGGERELLA";
        if (code >= 61 && code <= 67) return "PIOGGIA";
        if (code >= 71 && code <= 77) return "NEVE";
        if (code >= 80 && code <= 82) return "ROVESCI";
        if (code >= 85 && code <= 86) return "NEVE";
        if (code >= 95) return "TEMPORALE";
        return "";
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
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
