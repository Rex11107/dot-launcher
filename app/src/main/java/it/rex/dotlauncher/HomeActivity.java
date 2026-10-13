package it.rex.dotlauncher;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlarmManager;
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
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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

public class HomeActivity extends Activity implements TileGrid.Host, AppTile.Source, AppSections.Host,
        UniversalSearch.Host, DotNotifications.Listener, StackTile.Host, Onboarding.Host {
    private static final int REQ_BIND = 1;
    private static final int REQ_CONFIG = 2;
    private static final int REQ_LOC = 3;
    private static final int REQ_EXPORT = 4;
    private static final int REQ_IMPORT = 5;
    private static final int REQ_PHOTO = 6;
    private static final int REQ_STEPS = 7;
    private static final int REQ_CAL = 8;
    private static final int REQ_CONTACTS = 9;
    private static final int REQ_ICON = 10;
    private static final int REQ_FONT = 11;
    private static final int REQ_ROLE = 12;
    private static final int REQ_AUTH = 13;
    private static final int HOST_ID = 0x0D07;

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
    private IndicatorView indicator;
    private LinearLayout dock;
    private final List<TileGrid> grids = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private int pages = 2;

    private LinearLayout drawer;
    private EditText search;
    private UniversalSearch grid; // risultati della ricerca universale
    private boolean drawerOpen;
    private LinearLayout searchRow;
    private boolean searchMode;
    private Sections sections;
    private AppSections secView;
    private boolean drawerDirty = true;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final Map<String, AppEntry> appsByKey = new HashMap<>();
    /** Icone pronte: limitate a un ottavo della memoria dell'app (telefoni con poca RAM). */
    private final android.util.LruCache<String, Bitmap> iconCache = new android.util.LruCache<String, Bitmap>(
            (int) Math.max(4L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
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
    private LeftPanel panel;
    private boolean panelOpen;
    private boolean started;
    private Item pendingPhoto;

    static class AppEntry {
        String label;
        String key;
        ComponentName component;
        UserHandle user;
        Drawable icon;
        int cat = -1;     // categoria dichiarata dall'app (ApplicationInfo.CATEGORY_*)
        String origLabel; // nome originale (se l'utente l'ha rinominata)
        boolean game;
    }

    // ---------- ricevitori ----------

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateAlarm();
            refreshTiles(null);
            if (System.currentTimeMillis() - Usage.computedAt > 5 * 60000L) refreshUsage();
            else updateWellbeing();
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
        // più eventi ravvicinati (es. aggiornamenti in blocco) producono un solo ricaricamento
        @Override public void onPackageRemoved(String p, UserHandle u) { reloadAppsSoon(); }
        @Override public void onPackageAdded(String p, UserHandle u) { reloadAppsSoon(); }
        @Override public void onPackageChanged(String p, UserHandle u) { reloadAppsSoon(); }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { reloadAppsSoon(); }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { reloadAppsSoon(); }
    };

    // ---------- ciclo di vita ----------

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        dp = getResources().getDisplayMetrics().density;
        Configuration cfg = getResources().getConfiguration();
        cfgDpi = cfg.densityDpi;
        cfgFont = cfg.fontScale;
        cfgWidth = cfg.screenWidthDp;
        prefs = getSharedPreferences("dot", MODE_PRIVATE);
        migrateFromV1();
        TileGrid.COLS = prefs.getInt("cols", 4) == 5 ? 5 : 4;
        TileGrid.ROWS = Math.max(0, Math.min(9, prefs.getInt("rows", 0)));
        IconFactory.shape = Math.max(0, Math.min(3, prefs.getInt("iconShape", 0)));
        AppTile.SCALE = Math.max(0.7f, Math.min(1.25f, prefs.getFloat("iconScale", 1f)));
        Draw.ghost = prefs.getBoolean("ghost", true);
        // le animazioni a punti seguono anche la "Scala animazione" di sistema (spente = niente animazioni)
        Draw.anim = prefs.getBoolean("anim", true) && Motion.systemOn();
        Motion.setSpeed(prefs.getInt("animSpeed", 1));
        Motion.menu = Math.max(0, Math.min(2, prefs.getInt("menuAnim", 0)));
        Pager.transition = Math.max(0, Math.min(3, prefs.getInt("pageAnim", 0)));
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
        // prima installazione (nessuna home salvata): si mostra la configurazione guidata
        boolean firstRun = !prefs.contains("layout");
        if (!prefs.getBoolean("onboarded", false) && !firstRun && !prefs.contains("onboardPage")) {
            prefs.edit().putBoolean("onboarded", true).apply(); // chi aggiorna da una versione precedente
        }
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
        registerBack();
        if (!prefs.getBoolean("onboarded", false)) showOnboarding(prefs.getInt("onboardPage", 0));

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                // la lista del cassetto era già in cima quando il dito è sceso?
                drawerTopOnDown = drawerOpen && !(searchMode ? grid.canScrollVertically(-1) : secView.canScrollUp());
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (drawerOpen || TileGrid.anyDragging || !emptyHomeAt(e.getRawX(), e.getRawY())) return false;
                runGesture("g_dbl");
                return true;
            }

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
                    // un widget con una lista che scorre si tiene il suo gesto
                    if (scrollableWidgetAt(e1.getRawX(), e1.getRawY(), dy)) return false;
                    if (dy < -min) {
                        runGesture("g_up");
                        flingTook = true;
                    } else if (dy > min) {
                        runGesture("g_down");
                        flingTook = true;
                    }
                } else if (dy > min && drawerTopOnDown
                        && !(searchMode ? grid.canScrollVertically(-1) : secView.canScrollUp())) {
                    closeDrawer();
                    flingTook = true;
                }
                return false;
            }
        });

        launcherApps.registerCallback(appsCallback, ui);
        loadApps();
        if (getIntent() != null && getIntent().hasExtra("blocked")) {
            final Intent bi = getIntent();
            ui.postDelayed(() -> handleBlocked(bi), 900);
        }
        buildPages();
        root.post(this::fitToScreen);
        if (saved != null && saved.getBoolean("set", false)) {
            final int y = saved.getInt("setY", 0);
            root.post(() -> {
                showSettings();
                settings.scrollTo(y);
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pw", pendingWidgetId);
        out.putInt("pp", pendingPage);
        out.putInt("pc", pendingCol);
        out.putInt("pr", pendingRow);
        out.putInt("pph", pendingPhoto == null ? -1 : items.indexOf(pendingPhoto));
        out.putBoolean("set", settingsOpen);
        if (settings != null) out.putInt("setY", settings.scrollY());
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
            IntentFilter pf = new IntentFilter();
            pf.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
            pf.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
            pf.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
            registerReceiver(profileReceiver, pf);
            receiversOn = true;
        }
        started = true;
        Draw.anim = prefs.getBoolean("anim", true) && Motion.systemOn();
        DotNotifications.setListener(this);
        // Il resto aspetta che il gesto del sistema sia finito. Con un launcher diverso da quello di serie,
        // scorrendo in su da un'app (Home o app recenti) la home viene riavviata proprio mentre il sistema
        // anima il gesto: tutto il lavoro fatto qui rubava fotogrammi e il gesto a volte "non prendeva".
        // Se si passa solo di qui per arrivare alle app recenti, onStop annulla tutto e non si fa nulla.
        ui.removeCallbacks(wakeWork);
        ui.postDelayed(wakeWork, WAKE_DELAY_MS);
        if (prefs.getBoolean("crashNew", false) && onboarding == null) {
            prefs.edit().putBoolean("crashNew", false).apply();
            Sheet.confirm(this, th, T.t("Il launcher si è chiuso"),
                    T.t("Si è verificato un errore e Dot Launcher si è riavviato. Il motivo è salvato solo sul telefono: "
                            + "puoi vederlo e inviarmelo da Impostazioni → Informazioni → Segnala un problema."),
                    T.t("Vedi"), this::reportProblem);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        waveOnWake = true; // l'onda parte dopo il gesto, non durante
    }

    private static final long WAKE_DELAY_MS = 350;
    private boolean waveOnWake;

    /** Lavoro al ritorno sulla home, rimandato a gesto finito (vedi onStart). */
    private final Runnable wakeWork = new Runnable() {
        @Override
        public void run() {
            if (!started || destroyed) return;
            readQuiet();
            onNotificationsChanged();
            startSensors();
            watchTorch(true);
            updateAlarm();
            refreshTiles(null);
            maybeRefreshWeather();
            consumePendingShortcuts();
            checkUpdate(false);
            refreshUsage();
            if (waveOnWake) {
                waveOnWake = false;
                if (hasWindowFocus()) playWave(); // solo se la home è davvero davanti, non dietro le app recenti
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        consumePendingShortcuts();
        if (onboarding != null) onboarding.refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacks(wakeWork); // passati di qui solo per le app recenti: niente lavoro inutile
        waveOnWake = false;
        try {
            host.stopListening();
        } catch (Exception ignored) {
        }
        if (receiversOn) {
            unregisterReceiver(timeReceiver);
            unregisterReceiver(batteryReceiver);
            unregisterReceiver(profileReceiver);
            receiversOn = false;
        }
        started = false;
        if (pendingAuth == null) authUntil = 0; // uscendo dalla home le app nascoste tornano protette
        DotNotifications.setListener(null);
        if (sensors != null) sensors.stop();
        watchTorch(false);
        if (dragSrc >= 0) finishDrag(false);
        exitResize();
        // uscendo verso un'app il cassetto si chiude subito: al ritorno la home è pulita
        if (drawerOpen) hideDrawerNow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        launcherApps.unregisterCallback(appsCallback);
        ui.removeCallbacksAndMessages(null);
        if (locCancel != null) locCancel.cancel();
        appExec.shutdownNow();
        netExec.shutdownNow();
        if (panel != null) panel.shutdown();
        if (grid != null) grid.shutdown();
    }

    private boolean destroyed;
    private android.os.CancellationSignal locCancel;

    /** Lavoro in background che non fa crollare nulla se la home è già stata chiusa (es. dopo recreate). */
    private void bg(ExecutorService ex, Runnable r) {
        if (destroyed || ex.isShutdown()) return;
        try {
            ex.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        // dimensioni dello schermo, densità o grandezza del testo cambiate: tutte le misure vanno ricalcolate
        boolean sizes = c.densityDpi != cfgDpi || c.fontScale != cfgFont || c.screenWidthDp != cfgWidth;
        if (sizes || "auto".equals(prefs.getString("mode", "dark"))) recreate();
    }

    private int cfgDpi, cfgWidth;
    private float cfgFont;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra("blocked") && intent.getLongExtra("tok", 0) == DotAccessibility.TOKEN) {
            // riportati qui dal blocco rigido: si chiude ciò che era aperto e si spiega perché
            if (dragSrc >= 0) finishDrag(false);
            exitResize();
            if (settingsOpen) closeSettings();
            if (panelOpen) hidePanelNow();
            if (drawerOpen) hideDrawerNow();
            ui.postDelayed(() -> handleBlocked(intent), 250);
            return;
        }
        // Solo se eravamo già sulla home (Home premuto di nuovo): chiudi il cassetto o torna alla prima pagina.
        // Se invece si sta tornando da un'app, la home resta com'era, senza animazioni.
        // con un pannello (menù) aperto la finestra non ha il focus, ma siamo comunque sulla home
        boolean alreadyHome = hasWindowFocus() | Sheet.dismissOpen();
        if (dragSrc >= 0) finishDrag(false);
        exitResize();
        if (settingsOpen) closeSettings();
        if (panelOpen) {
            if (alreadyHome) closePanel();
            else hidePanelNow();
        } else if (drawerOpen) {
            if (alreadyHome) closeDrawer();
            else hideDrawerNow();
        } else if (alreadyHome && pager != null && pager.getCurrent() != 0) {
            pager.snapTo(0);
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (onboarding != null) return; // la configurazione guidata si chiude con "Salta" o "Inizia"
        if (resizer != null) exitResize();
        else if (settingsOpen) closeSettings();
        else if (panelOpen) closePanel();
        else if (drawerOpen) {
            if (searchMode) exitSearch();
            else closeDrawer();
        }
    }

    // ---------- gesto indietro predittivo (Android 13 e successivi) ----------

    /** Il pannello che il gesto indietro chiuderebbe (per l'anteprima mentre si trascina). */
    private View backTarget() {
        if (onboarding != null || resizer != null) return null;
        if (settingsOpen) return settings;
        if (panelOpen) return panel;
        if (drawerOpen && !searchMode) return drawer;
        return null;
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= 34) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, new android.window.OnBackAnimationCallback() {
                        View target;

                        @Override
                        public void onBackStarted(android.window.BackEvent e) {
                            target = backTarget();
                            if (target != null) target.animate().cancel();
                        }

                        @Override
                        public void onBackProgressed(android.window.BackEvent e) {
                            if (target == null) return;
                            // il pannello si rimpicciolisce un po' e si sposta verso il bordo da cui parte il gesto
                            float p = e.getProgress();
                            target.setScaleX(1f - 0.08f * p);
                            target.setScaleY(1f - 0.08f * p);
                            float dir = e.getSwipeEdge() == android.window.BackEvent.EDGE_LEFT ? 1f : -1f;
                            target.setTranslationX(dir * px(24) * p);
                        }

                        @Override
                        public void onBackInvoked() {
                            target = null;
                            handleBack();
                        }

                        @Override
                        public void onBackCancelled() {
                            if (target != null) {
                                target.animate().scaleX(1f).scaleY(1f).translationX(0).setDuration(Motion.FADE)
                                        .setInterpolator(Motion.STANDARD).start();
                            }
                            target = null;
                        }
                    });
        } else if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            fingerDown = true;
            flingTook = false;
            // il riconoscitore dei gesti segue solo i tocchi iniziati senza pannelli aperti sopra la home
            gestureArmed = resizer == null && !panelOpen && !settingsOpen && onboarding == null;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            fingerDown = false;
        }
        if (dragSrc >= 0) {
            // con due dita si segue solo la prima
            if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) return true;
            lastRawX = ev.getRawX();
            lastRawY = ev.getRawY();
            onDragEvent(ev);
            return true;
        }
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        // cassetto che segue il dito: scorrendo in su dalla home si tira fuori un po' alla volta
        if (pulling) {
            onPull(ev);
            return true;
        }
        if (swallow) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swallow = false;
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            pullDownX = ev.getRawX();
            pullDownY = ev.getRawY();
        } else if (action == MotionEvent.ACTION_MOVE && swipeOnIcon(ev)) {
            return true;
        } else if (action == MotionEvent.ACTION_MOVE && canPull(ev)) {
            beginPull(ev);
            return true;
        }
        if (gestureArmed && (resizer != null || panelOpen || settingsOpen)) {
            // si è aperto qualcosa a metà gesto: il riconoscitore riparte da zero
            MotionEvent c = MotionEvent.obtain(ev);
            c.setAction(MotionEvent.ACTION_CANCEL);
            if (gestures != null) gestures.onTouchEvent(c);
            c.recycle();
            gestureArmed = false;
        }
        if (gestures != null && gestureArmed) gestures.onTouchEvent(ev);
        if (gestureArmed && ev.getPointerCount() >= 2) twoFinger(ev);
        if (flingTook && action == MotionEvent.ACTION_UP) {
            // il gesto ha già aperto il cassetto o le notifiche: la tessera sotto il dito non deve anche aprirsi
            MotionEvent c = MotionEvent.obtain(ev);
            c.setAction(MotionEvent.ACTION_CANCEL);
            boolean r = super.dispatchTouchEvent(c);
            c.recycle();
            return r;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean fingerDown, flingTook, gestureArmed, drawerTopOnDown;
    /** Chiave per ritrovare l'app di un'icona del dock (setTag con chiave). */
    private static final int R_KEY = 0x7f0d0001;
    /** Dopo un'azione scorrendo su un'icona, il resto del tocco viene ignorato. */
    private boolean swallow;
    private boolean pulling;
    private float pullDownX, pullDownY, pullStartY, pullLastY, pullSpeed;
    private long pullLastT;

    /** Scorrimento in su partito da un'icona con un'azione: la esegue e ignora il resto del tocco. */
    private boolean swipeOnIcon(MotionEvent ev) {
        if (!gestureArmed || drawerOpen || panelOpen || settingsOpen || resizer != null || onboarding != null) return false;
        if (ev.getPointerCount() != 1 || TileGrid.anyDragging) return false;
        float dx = ev.getRawX() - pullDownX, dy = ev.getRawY() - pullDownY;
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (dy > -slop * 2.5f || Math.abs(dy) < Math.abs(dx) * 1.5f) return false;
        String act = swipeActionAt(pullDownX, pullDownY);
        if (act == null) return false;
        swallow = true;
        flingTook = true;
        MotionEvent c = MotionEvent.obtain(ev);
        c.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(c);
        if (gestures != null) gestures.onTouchEvent(c);
        c.recycle();
        View root = getWindow().getDecorView();
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        runAction(act);
        return true;
    }

    private boolean canPull(MotionEvent ev) {
        if (!gestureArmed || drawerOpen || panelOpen || settingsOpen || resizer != null || onboarding != null) return false;
        if (ev.getPointerCount() != 1 || TileGrid.anyDragging || !"drawer".equals(gestureAction("g_up"))) return false;
        float dx = ev.getRawX() - pullDownX, dy = ev.getRawY() - pullDownY;
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (dy > -slop * 1.5f || Math.abs(dy) < Math.abs(dx) * 1.3f) return false;
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        // la striscia in basso appartiene alla gesture "home" del sistema
        if (pullDownY - rl[1] > root.getHeight() - gestureBottom - px(12)) return false;
        return !scrollableWidgetAt(pullDownX, pullDownY, dy);
    }

    private void beginPull(MotionEvent ev) {
        pulling = true;
        flingTook = true;
        pullStartY = pullLastY = ev.getRawY();
        pullLastT = ev.getEventTime();
        pullSpeed = 0;
        // la tessera sotto il dito e il riconoscitore dei gesti lasciano perdere questo tocco
        MotionEvent c = MotionEvent.obtain(ev);
        c.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(c);
        if (gestures != null) gestures.onTouchEvent(c);
        c.recycle();
        if (drawerDirty) rebuildDrawer();
        exitSearch();
        drawer.animate().cancel();
        homeBox.animate().cancel();
        menuRest(drawer);
        drawer.setAlpha(0f);
        drawer.setTranslationY(pullRange());
        drawer.setVisibility(View.VISIBLE);
    }

    private float pullRange() {
        return Math.max(px(120), root.getHeight() * 0.32f);
    }

    private void onPull(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_POINTER_DOWN) {
            // è arrivato un secondo dito: era un gesto a due dita, non il cassetto
            pulling = false;
            pullSpeed = 0;
            drawer.animate().cancel();
            drawer.setVisibility(View.GONE);
            menuRest(drawer);
            restoreHome(false);
            if (gestureArmed) twoFinger(ev);
            return;
        }
        if (a == MotionEvent.ACTION_MOVE) {
            long dt = ev.getEventTime() - pullLastT;
            if (dt > 0) pullSpeed = pullSpeed * 0.4f + (ev.getRawY() - pullLastY) / dt * 0.6f;
            pullLastY = ev.getRawY();
            pullLastT = ev.getEventTime();
            float range = pullRange();
            float dist = Math.max(0, Math.min(range, pullStartY - ev.getRawY()));
            float p = dist / range;
            drawer.setTranslationY(range - dist);
            drawer.setAlpha(Math.min(1f, p * 1.3f));
            homeBox.setAlpha(1f - 0.6f * p);
            homeBox.setScaleX(1f - 0.06f * p);
            homeBox.setScaleY(1f - 0.06f * p);
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            pulling = false;
            float p = 1f - drawer.getTranslationY() / pullRange();
            float fast = 0.5f * getResources().getDisplayMetrics().density; // px/ms
            boolean open = a == MotionEvent.ACTION_UP && pullSpeed < fast && (p > 0.4f || pullSpeed < -fast);
            pullSpeed = 0;
            if (open) {
                openDrawer(false);
            } else {
                drawer.animate().cancel();
                drawer.animate().alpha(0f).translationY(pullRange()).setDuration(Motion.EXIT).setInterpolator(Motion.EASE_IN)
                        .withEndAction(() -> {
                            if (!drawerOpen) drawer.setVisibility(View.GONE);
                        }).start();
                restoreHome(true);
            }
        }
    }
    private int insTop, insBottom;
    private Onboarding onboarding;

    /** C'è un widget di sistema sotto il dito che può ancora scorrere in quella direzione? */
    private boolean scrollableWidgetAt(float rx, float ry, float dy) {
        int page = pager.getCurrent();
        if (page >= grids.size()) return false;
        TileGrid g = grids.get(page);
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            boolean stack = v instanceof StackTile && ((StackTile) v).getChildCount() > 1;
            if (!(v instanceof AppWidgetHostView) && !stack) continue;
            int[] l = new int[2];
            v.getLocationOnScreen(l);
            if (rx < l[0] || rx > l[0] + v.getWidth() || ry < l[1] || ry > l[1] + v.getHeight()) continue;
            if (stack) return true; // la pila si scorre in verticale
            return canScrollDeep(v, dy > 0 ? -1 : 1);
        }
        return false;
    }

    private static boolean canScrollDeep(View v, int dir) {
        if (v.canScrollVertically(dir)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (canScrollDeep(g.getChildAt(i), dir)) return true;
        }
        return false;
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
            ui.postDelayed(() -> refreshTiles("battery"), 450);
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
        // stesso margine laterale dei widget (16dp): la pillola del dock non tocca i bordi dello schermo
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(76));
        dockLp.leftMargin = px(16);
        dockLp.rightMargin = px(16);
        home.addView(dock, dockLp);

        root.addView(home, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (prefs.getBoolean("panel", true)) {
            panel = new LeftPanel(this, th, prefs, h24, new LeftPanel.Host() {
                @Override public void closePanel() { HomeActivity.this.closePanel(); }
                @Override public void startSafe(Intent i) { safeStart(i); }
                @Override public void toast(String s) { HomeActivity.this.toast(s); }
                @Override public void requestCalendar() {
                    requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL);
                }
            });
            panel.setVisibility(View.GONE);
            root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            pager.setEdgeListener(this::openPanel);
        }

        buildDrawer();
        root.addView(drawer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        buildDropBar();
    }

    private void applyWindow() {
        if (prefs.getBoolean("hideStatus", false)) {
            // barra di stato nascosta: riappare scorrendo dal bordo in alto
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.statusBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
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
            secView.setPadding(0, 0, 0, r[7] > 0 ? 0 : r[3]);
            secView.setEdgeGuard(r[4], r[5]);
            if (panel != null) panel.setInsets(r[1], Math.max(r[3], r[7]));
            if (settings != null) settings.setInsets(r[1], r[3]);
            gestureBottom = r[6];
            insTop = r[1];
            insBottom = Math.max(r[3], r[7]);
            if (onboarding != null) onboarding.setInsets(insTop, insBottom);
            dropBar.setPadding(0, r[1] + px(8), 0, px(8));
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

        search = new EditText(this);
        search.setHint(T.t("Cerca"));
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
            @Override public void afterTextChanged(Editable s) { grid.setQuery(s.toString()); }
        });
        search.setOnEditorActionListener((v, actionId, ev) -> grid.openFirst());
        searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView closeSearch = new ImageView(this);
        closeSearch.setImageBitmap(SectionIcons.bitmap(SectionIcons.CLOSE, px(44), th.onTile, th.accent,
                Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f)));
        closeSearch.setPadding(px(4), px(4), px(4), px(4));
        closeSearch.setContentDescription(T.t("Chiudi la ricerca"));
        closeSearch.setOnClickListener(v -> exitSearch());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(48), px(48));
        cl.leftMargin = px(8);
        searchRow.addView(closeSearch, cl);
        searchRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = px(16);
        slp.bottomMargin = px(16);
        drawer.addView(searchRow, slp);

        grid = new UniversalSearch(this, this);
        grid.setPadding(0, 0, 0, px(24));
        grid.setVisibility(View.GONE);
        drawer.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // sezioni del cassetto
        sections = new Sections(prefs);
        secView = new AppSections(this, this);
        drawer.addView(secView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    // ---------- cassetto a sezioni ----------

    private boolean drawerFree() {
        return "free".equals(prefs.getString("drawerOrder", "alpha"));
    }

    /** Elenco app cambiato (installazioni, app nascoste, icone): riallinea ricerca e sezioni. */
    private void refreshDrawer() {
        if (searchMode) grid.setQuery(search.getText().toString());
        if (sections != null && sections.sync(allApps)) sections.save();
        drawerDirty = true;
        if (drawerOpen) rebuildDrawer();
        else ui.post(() -> {
            // pronto prima dell'apertura: niente scatti al primo fotogramma
            if (drawerDirty && !drawerOpen) rebuildDrawer();
        });
    }

    private void rebuildDrawer() {
        if (secView == null) return;
        drawerDirty = false;
        secView.setModel(sections, !drawerFree(), prefs.getBoolean("drawerAll", true));
        secView.rebuild();
    }

    private void saveSections(int page) {
        sections.save();
        rebuildDrawer();
        secView.setPageNow(page);
    }

    private void enterSearch() {
        searchMode = true;
        secView.setVisibility(View.GONE);
        searchRow.setVisibility(View.VISIBLE);
        grid.setVisibility(View.VISIBLE);
        searchRow.setAlpha(0f);
        grid.setAlpha(0f);
        searchRow.animate().alpha(1f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
        grid.animate().alpha(1f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
        grid.prepare();
        search.setText("");
        grid.setQuery("");
        grid.scrollTo(0, 0);
        ui.postDelayed(() -> {
            search.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void exitSearch() {
        searchMode = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        boolean was = searchRow.getVisibility() == View.VISIBLE;
        searchRow.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        secView.setVisibility(View.VISIBLE);
        if (was && drawerOpen) {
            secView.setAlpha(0f);
            secView.animate().alpha(1f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
        } else secView.setAlpha(1f);
    }

    @Override
    public Theme theme() {
        return th;
    }

    @Override
    public int badge(String key) {
        if (key == null || !prefs.getBoolean("badges", true) || !DotNotifications.active()) return 0;
        AppEntry a = appsByKey.get(key);
        return a == null ? 0 : DotNotifications.count(a.component.getPackageName());
    }

    @Override
    public void onNotificationsChanged() {
        if (destroyed) return;
        refreshAppViews();
        buildDock();
        if (secView != null) secView.updateBadges();
    }

    private boolean wasPlaying;

    @Override
    public void onMediaChanged() {
        if (destroyed) return;
        refreshTiles("media");
        // pila "intelligente": quando parte la musica si mostra il widget della musica
        boolean began = DotNotifications.mPlaying && !wasPlaying;
        wasPlaying = DotNotifications.mPlaying;
        if (began) {
            for (Item st : items) {
                if (!st.isStack() || !(st.view instanceof StackTile)) continue;
                List<Item> kids = st.kids();
                for (int i = 0; i < kids.size(); i++) {
                    if ("media".equals(kids.get(i).type) && st.current != i) ((StackTile) st.view).show(i, 1);
                }
            }
        }
    }

    @Override
    public Set<String> hiddenKeys() {
        return hidden();
    }

    @Override
    public List<AppEntry> apps() {
        return allApps;
    }

    @Override
    public void launchKey(String key, View v) {
        launch(appsByKey.get(key), v);
    }

    @Override
    public void appLongPress(String key, View icon) {
        beginDrag(SRC_DRAWER, null, key, icon);
    }

    @Override
    public void search() {
        enterSearch();
    }

    @Override
    public void openBrowser() {
        Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
        try {
            startActivity(i);
        } catch (Exception e) {
            safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
        }
    }

    private AppEntry storeApp(boolean fdroid) {
        String[] pk = fdroid
                ? new String[]{"org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid"}
                : new String[]{"com.android.vending"};
        for (String p : pk) {
            for (AppEntry a : allApps) if (a.component.getPackageName().equals(p)) return a;
        }
        return null;
    }

    @Override
    public void openStore(boolean fdroid) {
        AppEntry a = storeApp(fdroid);
        if (a == null) toast(fdroid ? T.t("F-Droid non è installato") : T.t("Play Store non disponibile"));
        else launch(a, null);
    }

    @Override
    public Bitmap storeIcon(boolean fdroid, int size) {
        AppEntry a = storeApp(fdroid);
        return a == null ? null : iconFor(a.key, size);
    }

    @Override
    public void sectionMenu(int idx) {
        if (idx < 0 || idx >= sections.list.size()) return;
        Sections.Section s = sections.list.get(idx);
        boolean work = "work".equals(s.cat) && hasWorkProfile();
        String[] base = {T.t("Rinomina"), T.t("Cambia icona"), T.t("Sposta a sinistra"), T.t("Sposta a destra"), T.t("Nuova sezione"), T.t("Elimina sezione")};
        String[] opts = base;
        if (work) {
            opts = Arrays.copyOf(base, base.length + 1);
            opts[base.length] = workPaused() ? T.t("Riattiva le app di lavoro") : T.t("Metti in pausa le app di lavoro");
        }
        Sheet.list(this, th, T.t(s.name), opts, -1, w -> {
            switch (w) {
                case 6:
                    setWorkPaused(!workPaused());
                    break;
                case 0:
                    Sheet.input(this, th, T.t("Nome della sezione"), T.t("es. Lavoro"), s.name, n -> {
                        if (n.isEmpty()) return;
                        s.name = n;
                        saveSections(idx);
                    });
                    break;
                case 1:
                    pickSectionIcon(ic -> {
                        s.icon = ic;
                        saveSections(idx);
                    });
                    break;
                case 2:
                    if (idx > 0) {
                        sections.swap(idx, idx - 1);
                        saveSections(idx - 1);
                    }
                    break;
                case 3:
                    if (idx < sections.list.size() - 1) {
                        sections.swap(idx, idx + 1);
                        saveSections(idx + 1);
                    }
                    break;
                case 4:
                    newSection();
                    break;
                default:
                    Sheet.confirm(this, th, T.t("Elimina sezione"),
                            T.t("Le app di \"") + s.name + T.t("\" passano nella sezione Varie (o nell'ultima rimasta)."),
                            T.t("Elimina"), () -> {
                                if (sections.remove(s)) saveSections(Math.max(0, idx - 1));
                                else toast(T.t("Serve almeno una sezione"));
                            });
            }
        });
    }

    @Override
    public void allMenu() {
        String[] opts = {T.t("Nascondi \"Tutte le app\""), T.t("Nuova sezione")};
        Sheet.list(this, th, T.t("Tutte le app"), opts, -1, w -> {
            if (w == 0) {
                prefs.edit().putBoolean("drawerAll", false).apply();
                refreshDrawer();
                toast(T.t("Si riattiva dalle impostazioni"));
            } else newSection();
        });
    }

    @Override
    public void newSection() {
        Sheet.input(this, th, T.t("Nuova sezione"), T.t("es. Lavoro"), "", n -> {
            if (n.isEmpty()) return;
            pickSectionIcon(ic -> {
                sections.add(n, ic);
                saveSections(sections.list.size() - 1);
                toast(T.t("Trascina le app sull'icona della nuova sezione"));
            });
        });
    }

    /** Crea sulla home una cartella con le stesse app del contenitore. */
    private void copyFolderToHome(Sections.Entry f) {
        if (f == null || !f.isFolder()) return;
        int page = pager.getCurrent();
        Item it = new Item("folder", 0, 0, 1, 1, 0, page);
        FolderTile.set(it, f.name, new ArrayList<>(f.apps));
        closeDrawer();
        addItem(it, page, 0, 0);
        toast("\"" + f.name + T.t("\" copiato sulla home"));
    }

    /** Passa all'ordine libero tenendo l'ordine alfabetico attuale come punto di partenza. */
    private void ensureFreeOrder() {
        if (drawerFree()) return;
        sections.sortAll(this);
        prefs.edit().putString("drawerOrder", "free").apply();
        toast(T.t("Ordine libero attivato: ora puoi disporre app e contenitori"));
    }

    private void pickSectionIcon(java.util.function.Consumer<String> cb) {
        final String[][] ch = SectionIcons.CHOICES;
        final int s = px(54);
        final int circle = Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f);
        Sheet.grid(this, th, T.t("Icona"), ch.length,
                i -> SectionIcons.bitmap(SectionIcons.get(ch[i][0]), s, th.onTile, th.accent, circle),
                i -> ch[i][1], i -> cb.accept(ch[i][0]), i -> cb.accept(ch[i][0]),
                T.t("Scegli l'icona della sezione"));
    }

    private void pickSection(String title, java.util.function.Consumer<Sections.Section> cb) {
        String[] names = new String[sections.list.size()];
        Bitmap[] icons = new Bitmap[names.length];
        int circle = Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f);
        for (int i = 0; i < names.length; i++) {
            Sections.Section s = sections.list.get(i);
            names[i] = s.name;
            icons[i] = SectionIcons.bitmap(SectionIcons.get(s.icon), px(36), th.onTile, th.accent, circle);
        }
        Sheet.list(this, th, title, names, icons, -1, w -> cb.accept(sections.list.get(w)), null);
    }

    private void moveAppToSection(AppEntry a) {
        pickSection(T.t("Sposta in sezione"), s -> {
            sections.moveTo(a.key, s, null, false);
            saveSections(secView.current());
            toast(a.label + " → " + s.name);
        });
    }

    @Override
    public void folderClick(Sections.Section s, Sections.Entry f) {
        final List<AppEntry> apps = new ArrayList<>();
        Set<String> h = hidden();
        for (String k : f.apps) {
            AppEntry a = appsByKey.get(k);
            if (a != null && !h.contains(k)) apps.add(a);
        }
        if (!drawerFree()) {
            final Collator col = Collator.getInstance(T.loc());
            Collections.sort(apps, (x, y) -> col.compare(x.label, y.label));
        }
        Sheet.grid(this, th, f.name, apps.size(),
                i -> iconFor(apps.get(i).key, px(54)), i -> apps.get(i).label,
                i -> launch(apps.get(i), null),
                i -> folderAppMenu(s, f, apps.get(i)),
                T.t("Tocca per aprire · tieni premuto per altre opzioni"));
    }

    private void folderAppMenu(Sections.Section s, Sections.Entry f, AppEntry a) {
        String[] opts = {T.t("Togli dal contenitore"), T.t("Sposta in sezione…"), T.t("Aggiungi alla home"), T.t("Info app"), T.t("Disinstalla")};
        Sheet.list(this, th, a.label, opts, -1, w -> {
            switch (w) {
                case 0:
                    sections.moveTo(a.key, s, f, true);
                    saveSections(secView.current());
                    break;
                case 1: moveAppToSection(a); break;
                case 2:
                    closeDrawer();
                    addAppToHome(a, pager.getCurrent(), 0, 0);
                    break;
                case 3: appInfo(a); break;
                default: uninstall(a);
            }
        });
    }

    @Override
    public void folderDrag(Sections.Section s, Sections.Entry f, View icon) {
        dragEntry = f;
        beginDrag(SRC_DFOLDER, null, null, icon);
        if (dragSrc != SRC_DFOLDER) dragEntry = null;
    }

    @Override
    public void folderLongPress(Sections.Section s, Sections.Entry f) {
        folderMenu(s, f);
    }

    private Dialog folderMenu(Sections.Section s, Sections.Entry f) {
        String[] opts = {T.t("Rinomina"), T.t("Sposta in sezione…"), T.t("Copia sulla home"), T.t("Sciogli contenitore")};
        return Sheet.list(this, th, f.name, opts, -1, w -> {
            switch (w) {
                case 0:
                    Sheet.input(this, th, T.t("Nome del contenitore"), T.t("es. Social"), f.name, n -> {
                        f.name = n.isEmpty() ? T.t("Contenitore") : n;
                        saveSections(secView.current());
                    });
                    break;
                case 1:
                    pickSection(T.t("Sposta in sezione"), to -> {
                        Sections.Section from = sections.sectionOfEntry(f);
                        if (from != null) sections.moveEntry(f, from, to);
                        saveSections(secView.current());
                    });
                    break;
                case 2:
                    copyFolderToHome(f);
                    break;
                default:
                    sections.dissolve(s, f);
                    saveSections(secView.current());
            }
        });
    }

    // ---------- pagina a sinistra ----------

    private void openPanel() {
        if (panel == null || panelOpen || drawerOpen) return;
        panelOpen = true;
        panel.animate().cancel();
        if (panel.getVisibility() != View.VISIBLE) {
            menuFrom(panel, -Math.max(1, root.getWidth()), 0);
            panel.setVisibility(View.VISIBLE);
        }
        menuIn(panel).start();
        recedeHome(0.97f, root.getWidth() * 0.18f);
        panel.onShown();
    }

    private void closePanel() {
        if (panel == null || !panelOpen) return;
        panelOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(panel.getWindowToken(), 0);
        panel.animate().cancel();
        menuOut(panel, -Math.max(1, root.getWidth()), 0).withEndAction(() -> {
            if (!panelOpen) panel.setVisibility(View.GONE);
        }).start();
        if (!drawerOpen && !settingsOpen) restoreHome(true);
    }

    private void hidePanelNow() {
        if (panel == null) return;
        panelOpen = false;
        panel.animate().cancel();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(panel.getWindowToken(), 0);
        panel.setVisibility(View.GONE);
        menuRest(panel);
        if (!drawerOpen && !settingsOpen) restoreHome(false);
    }

    private void openDrawer(boolean keyboard) {
        if (drawerOpen) return;
        drawerOpen = true;
        if (drawerDirty) rebuildDrawer();
        if (keyboard) enterSearch();
        else exitSearch();
        drawer.animate().cancel();
        if (drawer.getVisibility() != View.VISIBLE) {
            menuFrom(drawer, 0, px(60));
            drawer.setVisibility(View.VISIBLE);
        }
        menuIn(drawer).start();
        recedeHome(0.94f, 0);
        playWave();
    }

    // ---------- la home arretra dietro cassetto, pagina a sinistra e impostazioni ----------


    // ---------- stile di apertura dei menu (Impostazioni → Animazioni) ----------

    /** Posizione di partenza di un pannello che sta per entrare. dx/dy = da dove arriva nello stile "scorrimento". */
    private void menuFrom(View v, float dx, float dy) {
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0);
        v.setTranslationY(0);
        switch (Motion.menu) {
            case 1: // zoom
                v.setScaleX(0.9f);
                v.setScaleY(0.9f);
                v.setAlpha(0f);
                break;
            case 2: // dissolvenza
                v.setAlpha(0f);
                break;
            default: // scorrimento
                v.setTranslationX(dx);
                v.setTranslationY(dy);
                v.setAlpha(dy != 0 ? 0f : 1f);
        }
    }

    private android.view.ViewPropertyAnimator menuIn(View v) {
        return v.animate().alpha(1f).translationX(0).translationY(0).scaleX(1f).scaleY(1f)
                .setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT);
    }

    private android.view.ViewPropertyAnimator menuOut(View v, float dx, float dy) {
        android.view.ViewPropertyAnimator a = v.animate().setDuration(Motion.EXIT).setInterpolator(Motion.EASE_IN);
        switch (Motion.menu) {
            case 1:
                return a.alpha(0f).scaleX(0.92f).scaleY(0.92f);
            case 2:
                return a.alpha(0f).scaleX(1f).scaleY(1f);
            default:
                return a.translationX(dx).translationY(dy).alpha(dy != 0 ? 0f : 1f).scaleX(1f).scaleY(1f);
        }
    }

    private static void menuRest(View v) {
        v.setAlpha(1f);
        v.setTranslationX(0);
        v.setTranslationY(0);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    private void recedeHome(float scale, float dx) {
        homeBox.animate().cancel();
        homeBox.setVisibility(View.VISIBLE);
        homeBox.animate().alpha(0.4f).scaleX(scale).scaleY(scale).translationX(dx).setDuration(Motion.ENTER)
                .setInterpolator(Motion.EASE_OUT).withEndAction(() -> {
                    // coperta del tutto: smette di disegnare (anche le animazioni dei widget)
                    if (drawerOpen || panelOpen || settingsOpen) homeBox.setVisibility(View.INVISIBLE);
                }).start();
    }

    private void restoreHome(boolean animated) {
        homeBox.animate().cancel();
        homeBox.setVisibility(View.VISIBLE);
        if (animated) {
            homeBox.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0).setDuration(Motion.ENTER)
                    .setInterpolator(Motion.EASE_OUT).start();
        } else {
            homeBox.setAlpha(1f);
            homeBox.setScaleX(1f);
            homeBox.setScaleY(1f);
            homeBox.setTranslationX(0);
        }
        refreshTiles(null);
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        drawer.animate().cancel();
        menuOut(drawer, 0, px(60))
                .withEndAction(() -> {
                    if (drawerOpen) return;
                    drawer.setVisibility(View.GONE);
                    exitSearch();
                }).start();
        if (!panelOpen && !settingsOpen) restoreHome(true);
    }

    private void hideDrawerNow() {
        drawerOpen = false;
        drawer.animate().cancel();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        drawer.setVisibility(View.GONE);
        menuRest(drawer);
        exitSearch();
        if (!panelOpen && !settingsOpen) restoreHome(false);
    }

    private void expandQuickSettings() {
        try {
            @SuppressLint("WrongConstant") // servizio nascosto di Android, voluto: non c'è un'alternativa pubblica
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sb);
        } catch (Exception e) {
            expandNotifications();
        }
    }

    // =====================================================================
    // Gesti configurabili
    // =====================================================================

    /** {chiave, nome, azione predefinita} */
    static final String[][] GESTURES = {
            {"g_up", T.t("Scorri in su"), "drawer"},
            {"g_down", T.t("Scorri in giù"), "notif"},
            {"g_dbl", T.t("Doppio tocco"), "lock"},
            {"g_two_down", T.t("Due dita in giù"), "qs"},
            {"g_two_up", T.t("Due dita in su"), "search"},
            {"g_pinch", T.t("Pizzica (avvicina le dita)"), "settings"},
    };

    private static String[] gestureIcon(String key) {
        switch (key) {
            case "g_up": return SectionIcons.SWIPE_UP;
            case "g_down": return SectionIcons.SWIPE_DOWN;
            case "g_dbl": return SectionIcons.DOUBLE_TAP;
            case "g_two_down": return SectionIcons.TWO_DOWN;
            case "g_two_up": return SectionIcons.TWO_UP;
            case "g_pinch": return SectionIcons.PINCH;
            default: return SectionIcons.STAR;
        }
    }

    /** {azione, nome} */
    static final String[][] ACTIONS = {
            {"none", T.t("Niente")},
            {"drawer", T.t("Apri il cassetto delle app")},
            {"search", T.t("Cerca")},
            {"notif", T.t("Notifiche")},
            {"qs", T.t("Impostazioni rapide")},
            {"lock", T.t("Blocca lo schermo")},
            {"screenshot", "Screenshot"},
            {"recents", T.t("App recenti")},
            {"power", T.t("Menu di spegnimento")},
            {"panel", T.t("Pagina a sinistra")},
            {"settings", T.t("Impostazioni del launcher")},
            {"torch", T.t("Torcia")},
            {"focus", T.t("Modalità focus")},
            {"usage", T.t("Tempo di utilizzo")},
            {"app", T.t("Apri un'app…")},
    };

    private String gestureAction(String g) {
        for (String[] x : GESTURES) if (x[0].equals(g)) return prefs.getString(g, x[2]);
        return "none";
    }

    static String actionName(String a) {
        if (a.startsWith("app:")) return T.t("Apri un'app");
        for (String[] x : ACTIONS) if (x[0].equals(a)) return x[1];
        return T.t("Niente");
    }

    private String actionLabel(String a) {
        if (a.startsWith("app:")) return T.t("Apri ") + labelFor(a.substring(4));
        return actionName(a);
    }

    private void runGesture(String g) {
        runAction(gestureAction(g));
    }

    private void runAction(String a) {
        if (a.startsWith("app:")) {
            AppEntry e = appsByKey.get(a.substring(4));
            if (e != null) launch(e, null);
            else toast(T.t("L'app di questo gesto non è più installata"));
            return;
        }
        switch (a) {
            case "drawer": openDrawer(false); break;
            case "search": openDrawer(true); break;
            case "notif": expandNotifications(); break;
            case "qs": expandQuickSettings(); break;
            case "panel": openPanel(); break;
            case "settings": showSettings(); break;
            case "torch": toggleTorch(); break;
            case "focus":
                if (Usage.focusOn(prefs)) {
                    stopFocus();
                    toast(T.t("Focus terminato"));
                } else focusMenu();
                break;
            case "usage": showUsage(); break;
            case "lock":
                if (Build.VERSION.SDK_INT >= 28) globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                else toast(T.t("Bloccare lo schermo richiede Android 9 o successivo"));
                break;
            case "screenshot":
                if (Build.VERSION.SDK_INT >= 28) globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
                else toast(T.t("Lo screenshot richiede Android 9 o successivo"));
                break;
            case "recents": globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); break;
            case "power": globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); break;
            default: break;
        }
    }

    /** Azioni che passano dal servizio di accessibilità: se non è attivo si spiega come attivarlo. */
    private void globalAction(int action) {
        if (DotAccessibility.perform(action)) return;
        Sheet.confirm(this, th, T.t("Attiva i gesti di sistema"),
                T.t("Per bloccare lo schermo, fare screenshot o aprire le app recenti serve il servizio ")
                        + T.t("\"Gesti di Dot Launcher\" in Accessibilità. Non legge lo schermo e non raccoglie dati.\n\n")
                        + T.t("Se Android dice che è un'impostazione con restrizioni: Impostazioni → App → Dot Launcher → ")
                        + T.t("menu ⋮ → \"Consenti impostazioni con restrizioni\", poi riprova."),
                T.t("Apri Accessibilità"), () -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    /** Il punto è su uno spazio vuoto della home (non su un'app, un widget, il dock)? */
    private boolean emptyHomeAt(float rx, float ry) {
        if (panelOpen || settingsOpen || resizer != null) return false;
        int page = pager.getCurrent();
        if (page >= grids.size()) return false;
        int[] dl = new int[2];
        dock.getLocationOnScreen(dl);
        if (ry >= dl[1]) return false;
        TileGrid g = grids.get(page);
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            int[] l = new int[2];
            v.getLocationOnScreen(l);
            if (rx >= l[0] && rx < l[0] + v.getWidth() && ry >= l[1] && ry < l[1] + v.getHeight()) return false;
        }
        return true;
    }

    interface OnAction {
        void set(String action);
    }

    private void chooseGesture(String g, String title) {
        chooseAction(title, gestureAction(g), a -> {
            prefs.edit().putString(g, a).apply();
            refillSettings();
        });
    }

    /** Scelta di un'azione (quelle dei gesti, oppure aprire un'app). */
    private void chooseAction(String title, String cur, OnAction store) {
        String[] names = new String[ACTIONS.length];
        int sel = -1;
        for (int i = 0; i < ACTIONS.length; i++) {
            names[i] = ACTIONS[i][1];
            if (ACTIONS[i][0].equals(cur) || (cur.startsWith("app:") && "app".equals(ACTIONS[i][0]))) sel = i;
        }
        Sheet.list(this, th, title, names, sel, w -> {
            String a = ACTIONS[w][0];
            if ("app".equals(a)) {
                String[] labels = new String[allApps.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = allApps.get(i).label;
                Sheet.list(this, th, T.t("Quale app?"), labels, -1, k -> store.set("app:" + allApps.get(k).key));
                return;
            }
            store.set(a);
            if (("lock".equals(a) || "screenshot".equals(a) || "recents".equals(a) || "power".equals(a))
                    && !DotAccessibility.active()) {
                globalAction(-1);
            }
        });
    }

    // ---------- gesti a due dita (scorrimento e pizzico) ----------

    private float twoStartY, pinchStartDist;
    private boolean twoDone;

    private void twoFinger(MotionEvent ev) {
        if (drawerOpen || panelOpen || settingsOpen || resizer != null || dragSrc >= 0) return;
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() == 2) {
            twoStartY = (ev.getY(0) + ev.getY(1)) / 2f;
            pinchStartDist = dist(ev);
            twoDone = false;
        } else if (a == MotionEvent.ACTION_MOVE && ev.getPointerCount() == 2 && !twoDone) {
            float y = (ev.getY(0) + ev.getY(1)) / 2f;
            float d = dist(ev);
            if (pinchStartDist > 0 && d < pinchStartDist * 0.6f) {
                twoDone = true;
                flingTook = true;
                runGesture("g_pinch");
            } else if (Math.abs(d - pinchStartDist) < pinchStartDist * 0.25f) {
                if (y - twoStartY > 110 * dp) {
                    twoDone = true;
                    flingTook = true;
                    runGesture("g_two_down");
                } else if (twoStartY - y > 110 * dp) {
                    twoDone = true;
                    flingTook = true;
                    runGesture("g_two_up");
                }
            }
        }
    }

    private static float dist(MotionEvent ev) {
        if (ev.getPointerCount() < 2) return 0;
        return (float) Math.hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1));
    }

    private void expandNotifications() {
        try {
            @SuppressLint("WrongConstant") // servizio nascosto di Android, voluto: non c'è un'alternativa pubblica
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
        for (Item it : Item.leaves(items)) {
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
            if (info == null) {
                // app del widget disattivata, aggiornata o non raggiungibile: si tiene il posto invece di perderlo
                v = missingWidget();
            } else {
                v = host.createView(getApplicationContext(), id, info);
            }
        } else if (it.isStack()) {
            if (it.kids().isEmpty()) return null;
            v = new StackTile(this, it, th, this);
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

    private View missingWidget() {
        TextView t = new TextView(this);
        t.setText(T.t("Widget non disponibile\ntieni premuto per toglierlo"));
        t.setGravity(Gravity.CENTER);
        t.setTextColor(th.sub);
        t.setTypeface(th.bodyFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.alpha(th.tile, 0.6f));
        bg.setCornerRadius(px(24));
        bg.setStroke(Math.max(1, px(1)), Theme.alpha(th.onTile, 0.15f));
        t.setBackground(bg);
        return t;
    }

    private void refreshTiles(String prefix) {
        for (Item it : Item.leaves(items)) {
            if (it.view instanceof NothingTile && (prefix == null || it.type.startsWith(prefix))) {
                it.view.invalidate();
            }
        }
    }

    // =====================================================================
    // Pila di widget
    // =====================================================================

    @Override
    public View createKidView(Item kid) {
        View v;
        if (kid.isSys()) {
            // widget di un'altra app dentro la pila
            AppWidgetProviderInfo info = null;
            int id = -1;
            try {
                id = Integer.parseInt(kid.data);
                info = awm.getAppWidgetInfo(id);
            } catch (Exception ignored) {
            }
            v = info == null ? missingWidget() : host.createView(getApplicationContext(), id, info);
        } else {
            v = new NothingTile(this, kid, th, h24);
            v.setOnClickListener(x -> onTileClick(kid));
        }
        kid.view = v;
        v.setTag(kid);
        v.setOnLongClickListener(x -> {
            Item st = kid.parent;
            if (st != null && st.view != null) startHomeDrag(st, st.view);
            return true;
        });
        return v;
    }

    @Override
    public void onStackChanged(Item stack) {
        saveLayout();
    }

    /** Misure consentite: per una pila solo quelle valide per tutti i suoi widget. */
    private int[][] sizesOf(Item it) {
        if (!it.isStack()) return Widgets.sizes(it.type);
        List<int[]> ok = new ArrayList<>();
        List<Item> kids = it.kids();
        if (kids.isEmpty()) return new int[][]{{it.w, it.h}};
        for (int[] z : Widgets.sizes(kids.get(0).type)) {
            boolean all = true;
            for (Item k : kids) if (!hasSize(Widgets.sizes(k.type), z[0], z[1])) all = false;
            if (all) ok.add(z);
        }
        if (ok.isEmpty()) ok.add(new int[]{it.w, it.h});
        return ok.toArray(new int[0][]);
    }

    /** I widget Dot (non app, cartelle, scorciatoie, widget di sistema o pile) possono entrare in una pila. */
    /** Widget Dot e widget di sistema possono entrare in una pila (non app, cartelle, scorciatoie o altre pile). */
    private static boolean stackable(Item it) {
        return it != null && !it.isApp() && !it.isStack() && !"folder".equals(it.type)
                && !"shortcut".equals(it.type);
    }

    /** Libera gli identificativi dei widget di sistema dell'elemento (anche quelli dentro una pila). */
    private void releaseWidgets(Item it) {
        List<Item> all = it.isStack() ? it.kids() : java.util.Collections.singletonList(it);
        for (Item k : all) {
            if (!k.isSys()) continue;
            try {
                host.deleteAppWidgetId(Integer.parseInt(k.data));
            } catch (Exception ignored) {
            }
        }
    }

    /** Mette {@code add} nella pila {@code target} (se target è un widget singolo, la pila nasce adesso). */
    private void mergeIntoStack(Item target, Item add) {
        Item stack;
        if (target.isStack()) {
            stack = target;
        } else {
            stack = new Item("stack", target.col, target.row, target.w, target.h, 0, target.page);
            stack.data = "";
            int idx = items.indexOf(target);
            items.remove(target);
            if (idx >= 0) items.add(idx, stack);
            else items.add(stack);
            stack.kids().add(target);
            toast(T.t("Pila creata: scorri in verticale per cambiare widget"));
        }
        List<Item> kids = stack.kids();
        if (add.isStack()) kids.addAll(add.kids());
        else kids.add(add);
        stack.current = kids.size() - 1;
        stack.kids();
        saveLayout();
        buildPages();
        startSensors();
        watchTorch(true);
    }

    /** Toglie un widget dalla pila e lo rimette sulla home (se c'è posto) o lo elimina. */
    private void takeOutOfStack(Item stack, int idx, boolean keep) {
        List<Item> kids = stack.kids();
        if (idx < 0 || idx >= kids.size()) return;
        Item k = kids.remove(idx);
        k.parent = null;
        if (!keep) releaseWidgets(k);
        if (stack.current >= kids.size()) stack.current = Math.max(0, kids.size() - 1);
        if (kids.size() == 1) {
            // una pila con un solo widget torna un widget normale
            Item only = kids.get(0);
            only.parent = null;
            int i = items.indexOf(stack);
            items.remove(stack);
            if (i >= 0) items.add(i, only);
            else items.add(only);
            stack = only;
        }
        if (keep) {
            int[] spot = findSpot(k, stack.page, 0, 0, true);
            k.page = spot[0];
            k.col = spot[1];
            k.row = spot[2];
            items.add(k);
            if (k.page != stack.page) toast(T.t("Messo in un'altra pagina: qui non c'era posto"));
        }
        saveLayout();
        buildPages();
    }

    /** Nome di un widget della pila: per quelli di sistema, il nome dato dall'app. */
    private String kidName(Item k) {
        if (k.isSys()) {
            try {
                AppWidgetProviderInfo info = awm.getAppWidgetInfo(Integer.parseInt(k.data));
                if (info != null) return info.loadLabel(getPackageManager());
            } catch (Exception ignored) {
            }
        }
        return Widgets.name(k.type);
    }

    private void manageStack(Item stack) {
        List<Item> kids = stack.kids();
        String[] names = new String[kids.size()];
        for (int i = 0; i < kids.size(); i++) names[i] = (i + 1) + ". " + kidName(kids.get(i));
        Sheet.list(this, th, T.t("Widget nella pila"), names, stack.current, w -> {
            List<String> l = new ArrayList<>();
            List<Runnable> a = new ArrayList<>();
            l.add(T.t("Mostra"));
            a.add(() -> {
                if (stack.view instanceof StackTile) ((StackTile) stack.view).show(w, 1);
            });
            if (w > 0) {
                l.add(T.t("Sposta su"));
                a.add(() -> moveKid(stack, w, w - 1));
            }
            if (w < kids.size() - 1) {
                l.add(T.t("Sposta giù"));
                a.add(() -> moveKid(stack, w, w + 1));
            }
            l.add(T.t("Togli dalla pila e mettilo sulla home"));
            a.add(() -> takeOutOfStack(stack, w, true));
            l.add(T.t("Elimina dalla pila"));
            a.add(() -> takeOutOfStack(stack, w, false));
            Sheet.list(this, th, kidName(kids.get(w)), l.toArray(new String[0]), -1, x -> a.get(x).run());
        });
    }

    private void moveKid(Item stack, int from, int to) {
        List<Item> kids = stack.kids();
        Item k = kids.remove(from);
        kids.add(to, k);
        stack.current = to;
        saveLayout();
        buildPages();
    }

    private void onTileClick(Item it) {
        switch (it.type) {
            case "clock_dots":
            case "clock_analog":
            case "clock_pill":
            case "clock_ring":
            case "world_clock":
            case "alarm":
                safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                break;
            case "date_big":
            case "date_dot":
            case "calendar":
            case "year":
            case "day":
            case "week":
                safeStart(calendarIntent());
                break;
            case "weather":
            case "weather_week":
            case "sun":
            case "weather_hours":
                refreshWeather(true);
                toast(T.t("Aggiorno il meteo…"));
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
                        ? T.t("Bussola imprecisa: muovi il telefono disegnando un 8")
                        : T.t("Se la bussola sbaglia, muovi il telefono disegnando un 8"));
                break;
            case "countdown":
                editCountdown(it);
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
            case "moon": {
                double age = NothingTile.moonAge();
                int next = (int) Math.round((NothingTile.SYNODIC / 2 - age + NothingTile.SYNODIC) % NothingTile.SYNODIC);
                toast(NothingTile.moonName(age) + T.t(" · luna piena tra ") + next + T.n(next, " giorno", " giorni"));
                break;
            }
            case "counter":
                WData.put(it, "n", String.valueOf(WData.getLong(it, "n", 0) + 1));
                saveLayout();
                if (it.view != null) it.view.invalidate();
                if (it.view != null) it.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                break;
            case "proverb":
                WData.put(it, "o", String.valueOf(WData.getLong(it, "o", 0) + 1));
                saveLayout();
                if (it.view != null) it.view.invalidate();
                break;
            case "dice":
                if (it.view instanceof NothingTile) {
                    ((NothingTile) it.view).roll();
                    it.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    saveLayout();
                }
                break;
            case "storage":
                safeStart(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
                break;
            case "note":
                editNote(it);
                break;
            case "torch":
                toggleTorch();
                break;
            case "usage":
                showUsage();
                break;
            case "focus":
                focusMenu();
                break;
            case "media": {
                if (!DotNotifications.active()) {
                    askNotificationAccess();
                    break;
                }
                int hit = it.view instanceof NothingTile ? ((NothingTile) it.view).mediaHit() : 0;
                if (hit > 0 && DotNotifications.mActive) {
                    DotNotifications.control(hit);
                    if (it.view != null) it.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } else if (!DotNotifications.mPkg.isEmpty()) {
                    Intent li = getPackageManager().getLaunchIntentForPackage(DotNotifications.mPkg);
                    if (li != null) safeStart(li);
                } else toast("Nessuna musica in riproduzione");
                break;
            }
        }
    }

    /** Apre il calendario del telefono su oggi. */
    static Intent calendarIntent() {
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(b, System.currentTimeMillis());
        return new Intent(Intent.ACTION_VIEW).setData(b.build());
    }

    public void askNotificationAccess() {
        Sheet.confirm(this, th, T.t("Accesso alle notifiche"),
                T.t("Serve per i pallini sulle icone, l'anteprima delle notifiche nel menu delle app e il widget ")
                        + T.t("della musica. Le notifiche restano sul telefono: non vengono salvate né inviate.\n\n")
                        + T.t("Se Android dice che è un'impostazione con restrizioni: Impostazioni → App → Dot Launcher → ")
                        + T.t("menu ⋮ → \"Consenti impostazioni con restrizioni\", poi riprova."),
                T.t("Apri le impostazioni"), () -> {
                    Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    if (Build.VERSION.SDK_INT >= 30) {
                        Intent d = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
                        d.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                new ComponentName(this, DotNotifications.class).flattenToString());
                        try {
                            startActivity(d);
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                    safeStart(i);
                });
    }

    // ---------- nome e icona personalizzati ----------

    private void renameApp(AppEntry a) {
        String orig = a.origLabel != null ? a.origLabel : a.label;
        Sheet.input(this, th, T.t("Nome dell'app"), orig, a.label, n -> {
            String v = n.trim();
            putJson("customLabels", a.key, v.isEmpty() || v.equals(orig) ? null : v);
            toast(v.isEmpty() || v.equals(orig) ? T.t("Nome originale ripristinato") : T.t("Rinominata in ") + v);
            loadApps();
        });
    }

    private void changeIcon(AppEntry a) {
        String[] opts = {T.t("Immagine dalla galleria"), T.t("Icona di un'altra app"), T.t("Ripristina l'icona")};
        Sheet.list(this, th, T.t("Icona di ") + a.label, opts, -1, w -> {
            if (w == 0) {
                prefs.edit().putString("pendIcon", a.key).apply();
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(i, REQ_ICON);
                } catch (Exception e) {
                    toast("Impossibile aprire la galleria");
                }
            } else if (w == 1) {
                String[] labels = new String[allApps.size()];
                Bitmap[] icons = new Bitmap[allApps.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = allApps.get(i).label;
                    icons[i] = iconFor(allApps.get(i).key, px(36));
                }
                Sheet.list(this, th, "Usa l'icona di…", labels, icons, -1,
                        k -> setCustomIcon(a, "app:" + allApps.get(k).key), null);
            } else {
                setCustomIcon(a, null);
            }
        });
    }

    private void setCustomIcon(AppEntry a, String value) {
        String old = customIcons().get(a.key);
        if (old != null && old.startsWith("file:") && (value == null || !value.equals(old))) {
            //noinspection ResultOfMethodCallIgnored
            new java.io.File(getFilesDir(), "icons/" + old.substring(5)).delete();
        }
        putJson("customIcons", a.key, value);
        customIcons = null;
        iconCache.evictAll();
        refreshDrawer();
        buildDock();
        refreshAppViews();
    }

    private void saveIconImage(Uri uri) {
        String key = prefs.getString("pendIcon", "");
        prefs.edit().remove("pendIcon").apply();
        AppEntry a = appsByKey.get(key);
        if (a == null) return;
        bg(appExec, () -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, o);
                }
                int sample = 1;
                while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= 512) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap b;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    b = BitmapFactory.decodeStream(in, null, o2);
                }
                if (b == null) throw new Exception("decode");
                java.io.File dir = new java.io.File(getFilesDir(), "icons");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String name = Integer.toHexString(key.hashCode()) + "_" + System.currentTimeMillis() + ".png";
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name))) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                ui.post(() -> setCustomIcon(a, "file:" + name));
            } catch (Exception | OutOfMemoryError e) {
                ui.post(() -> toast("Immagine non leggibile"));
            }
        });
    }

    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "application/x-font-ttf",
                "application/x-font-otf", "application/octet-stream", "font/*"});
        try {
            startActivityForResult(i, REQ_FONT);
        } catch (Exception e) {
            toast("Impossibile aprire i file");
        }
    }

    private void saveFont(Uri uri) {
        bg(appExec, () -> {
            try {
                java.io.File dir = new java.io.File(getFilesDir(), "fonts");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String name = "custom_" + System.currentTimeMillis() + ".ttf";
                java.io.File f = new java.io.File(dir, name);
                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    if (in == null) throw new Exception("stream");
                    byte[] buf = new byte[16384];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                        if (total > 20L * 1024 * 1024) throw new Exception("too big");
                    }
                }
                // prova a caricarlo: se non è un carattere valido si scarta
                android.graphics.Typeface.createFromFile(f);
                String old = prefs.getString("font", "");
                ui.post(() -> {
                    if (!old.isEmpty()) {
                        //noinspection ResultOfMethodCallIgnored
                        new java.io.File(dir, old).delete();
                    }
                    prefs.edit().putString("font", name).apply();
                    recreate();
                });
            } catch (Exception e) {
                ui.post(() -> toast("Il file non è un carattere valido (.ttf o .otf)"));
            }
        });
    }

    private void editNote(Item it) {
        Sheet.input(this, th, "Nota", "Scrivi qui…", WData.get(it, "t", ""), true, t -> {
            WData.put(it, "t", t);
            saveLayout();
            if (it.view != null) it.view.invalidate();
        });
    }

    // ---------- torcia ----------

    private android.hardware.camera2.CameraManager.TorchCallback torchCb;
    private String torchId;

    private String torchCamera() {
        if (torchId != null) return torchId;
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(f)) {
                    torchId = id;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return torchId;
    }

    private void toggleTorch() {
        String id = torchCamera();
        if (id == null) {
            toast("Questo telefono non ha il flash");
            return;
        }
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);
            cm.setTorchMode(id, !State.torchOn);
        } catch (Exception e) {
            toast("Torcia non disponibile in questo momento");
        }
    }

    private void watchTorch(boolean on) {
        android.hardware.camera2.CameraManager cm =
                (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);
        if (cm == null) return;
        boolean any = false;
        for (Item it : Item.leaves(items)) if ("torch".equals(it.type)) any = true;
        try {
            if (on && any && torchCb == null) {
                torchCb = new android.hardware.camera2.CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(String cameraId, boolean enabled) {
                        if (!cameraId.equals(torchCamera())) return;
                        State.torchOn = enabled;
                        refreshTiles("torch");
                    }
                };
                cm.registerTorchCallback(torchCb, ui);
            } else if (!on && torchCb != null) {
                cm.unregisterTorchCallback(torchCb);
                torchCb = null;
            }
        } catch (Exception ignored) {
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
            case "note": editNote(it); break;
            case "torch": watchTorch(true); break;
            case "media": if (!DotNotifications.active()) askNotificationAccess(); break;
            case "usage":
                if (!Usage.granted(this)) askUsageAccess();
                else refreshUsage();
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
        Sheet.input(this, th, T.t("Nome dell'evento"), T.t("es. Vacanze"), WData.get(it, "t", ""), t -> {
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
                        WData.put(it, "t", t.isEmpty() ? T.t("Evento") : t);
                        WData.put(it, "d", String.valueOf(sel.getTimeInMillis()));
                        saveLayout();
                        if (it.view != null) it.view.invalidate();
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            picker.setTitle(T.t("Data dell'evento"));
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
            State.nextAlarm = info == null ? "" : new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", T.loc())
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
                    toast(apps.get(i).label + T.t(" tolta dalla cartella"));
                });
    }

    private void renameFolder(Item folder) {
        Sheet.input(this, th, T.t("Nome della cartella"), T.t("es. Social"), FolderTile.name(folder), n -> {
            FolderTile.set(folder, n.isEmpty() ? T.t("Cartella") : n, FolderTile.apps(folder));
            saveLayout();
            if (folder.view != null) folder.view.invalidate();
        });
    }

    private void addToFolder(AppEntry a) {
        final List<Item> folders = new ArrayList<>();
        for (Item it : items) if ("folder".equals(it.type)) folders.add(it);
        if (folders.isEmpty()) {
            toast(T.t("Nessuna cartella: trascina un'app sopra un'altra per crearne una"));
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < names.length; i++) names[i] = FolderTile.name(folders.get(i));
        Sheet.list(this, th, T.t("Aggiungi a una cartella"), names, -1, w -> {
            Item f = folders.get(w);
            List<String> apps = FolderTile.apps(f);
            if (!apps.contains(a.key)) apps.add(a.key);
            FolderTile.set(f, FolderTile.name(f), apps);
            saveLayout();
            if (f.view != null) f.view.invalidate();
            closeDrawer();
        });
    }

    public void onItemMenu(Item it) {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        int[][] sizes = sizesOf(it);
        final boolean lk = locked();
        if (sizes.length > 1 && !lk) {
            labels.add(T.t("Ridimensiona"));
            acts.add(() -> startResize(it));
        }
        final Item cur = it.isStack() ? (it.view instanceof StackTile ? ((StackTile) it.view).currentKid() : null) : it;
        if (!it.isSys() && !it.isApp() && cur != null && !cur.isSys()) {
            labels.add(it.isStack() ? T.t("Colore di questo widget") : T.t("Colore"));
            acts.add(() -> chooseTone(cur));
        }
        if (cur != null) widgetActions(cur, labels, acts);
        if (it.isStack()) {
            labels.add(T.t("Widget nella pila…"));
            acts.add(() -> manageStack(it));
            if (!lk) {
                labels.add(T.t("Aggiungi un widget alla pila"));
                acts.add(() -> widgetPicker(T.t("Aggiungi alla pila"), new int[]{it.w, it.h}, (type, sz) -> {
                    Item n = new Item(type, it.col, it.row, sz[0], sz[1], 0, it.page);
                    mergeIntoStack(it, n);
                    configureNew(n);
                }));
            }
        } else if (stackable(it) && !lk) {
            labels.add(T.t("Crea una pila con un altro widget"));
            acts.add(() -> widgetPicker(T.t("Aggiungi alla pila"), new int[]{it.w, it.h}, (type, sz) -> {
                Item n = new Item(type, it.col, it.row, sz[0], sz[1], 0, it.page);
                mergeIntoStack(it, n);
                configureNew(n);
            }));
        }
        if (it.isApp()) {
            AppEntry a = appsByKey.get(it.data);
            if (a != null) {
                labels.add(T.t("Rinomina"));
                acts.add(() -> renameApp(a));
                labels.add(T.t("Cambia icona"));
                acts.add(() -> changeIcon(a));
                labels.add(T.t("Stile icona"));
                acts.add(() -> chooseIconMode(a));
                labels.add(T.t("Azione scorrendo in su…"));
                acts.add(() -> chooseSwipeAction(a));
                labels.add(T.t("Benessere digitale…"));
                acts.add(() -> appWellbeing(a));
                labels.add(T.t("Info app"));
                acts.add(() -> appInfo(a));
            }
        }
        if (lk) {
            labels.add(T.t("Sblocca la home"));
            acts.add(() -> setLocked(false));
            String title = itemTitle(it);
            showMenuWithShortcuts(title, it.isApp() ? appsByKey.get(it.data) : null, labels, acts);
            return;
        }
        if (it.page < pages - 1) {
            labels.add(T.t("Sposta alla pagina successiva"));
            acts.add(() -> moveToPage(it, it.page + 1));
        }
        if (it.page > 0) {
            labels.add(T.t("Sposta alla pagina precedente"));
            acts.add(() -> moveToPage(it, it.page - 1));
        }
        if ("folder".equals(it.type)) {
            labels.add(T.t("Rinomina"));
            acts.add(() -> renameFolder(it));
        }
        labels.add(T.t("Rimuovi"));
        acts.add(() -> removeItem(it));
        String title = itemTitle(it);
        showMenuWithShortcuts(title, it.isApp() ? appsByKey.get(it.data) : null, labels, acts);
    }

    // ---------- azioni scorrendo in su su un'icona ----------

    private String swipeAction(String key) {
        if (key == null) return "";
        try {
            return new org.json.JSONObject(prefs.getString("swipeActs", "{}")).optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    private void chooseSwipeAction(AppEntry a) {
        String cur = swipeAction(a.key);
        chooseAction(T.t("Scorrendo in su sull'icona"), cur.isEmpty() ? "none" : cur, act -> {
            try {
                org.json.JSONObject o = new org.json.JSONObject(prefs.getString("swipeActs", "{}"));
                if ("none".equals(act)) o.remove(a.key);
                else o.put(a.key, act);
                prefs.edit().putString("swipeActs", o.toString()).apply();
                toast("none".equals(act) ? T.t("Azione tolta") : T.t("Scorri in su sull'icona di ") + a.label + ": " + actionLabel(act));
            } catch (Exception ignored) {
            }
        });
    }

    /** Azione dell'icona sotto il punto (home o dock), oppure null. */
    private String swipeActionAt(float rx, float ry) {
        String key = null;
        if (dock != null && dock.isShown()) {
            for (int i = 0; i < dock.getChildCount() && key == null; i++) {
                View c = dock.getChildAt(i);
                if (c.getTag(R_KEY) instanceof String && inside(c, rx, ry)) key = (String) c.getTag(R_KEY);
            }
        }
        int page = pager.getCurrent();
        if (key == null && page < grids.size()) {
            TileGrid g = grids.get(page);
            for (int i = 0; i < g.getChildCount() && key == null; i++) {
                View c = g.getChildAt(i);
                if (c instanceof AppTile && inside(c, rx, ry)) key = ((AppTile) c).item.data;
            }
        }
        String a = swipeAction(key);
        return a.isEmpty() ? null : a;
    }

    private static boolean inside(View v, float rx, float ry) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return rx >= l[0] && rx < l[0] + v.getWidth() && ry >= l[1] && ry < l[1] + v.getHeight();
    }

    private void folderLook(Item f) {
        boolean cov = !FolderTile.cover(f).isEmpty();
        Sheet.list(this, th, T.t("Aspetto della cartella"),
                new String[]{T.t("Anteprima delle app"), T.t("Copertina con un'app…")}, cov ? 1 : 0, w -> {
                    if (w == 0) {
                        FolderTile.setCover(f, "");
                        saveLayout();
                        if (f.view != null) f.view.invalidate();
                        return;
                    }
                    List<String> keys = FolderTile.apps(f);
                    String[] names = new String[keys.size()];
                    Bitmap[] icons = new Bitmap[keys.size()];
                    for (int i = 0; i < keys.size(); i++) {
                        names[i] = labelFor(keys.get(i));
                        icons[i] = iconFor(keys.get(i), px(36));
                    }
                    Sheet.list(this, th, T.t("Copertina"), names, icons, keys.indexOf(FolderTile.cover(f)), x -> {
                        FolderTile.setCover(f, keys.get(x));
                        saveLayout();
                        if (f.view != null) f.view.invalidate();
                    }, null);
                });
    }

    private String itemTitle(Item it) {
        if (it.isApp() || "shortcut".equals(it.type)) return labelFor(it.data);
        if (it.isSys()) return "Widget";
        if ("folder".equals(it.type)) return FolderTile.name(it);
        if (it.isStack() && it.view instanceof StackTile) {
            Item k = ((StackTile) it.view).currentKid();
            if (k != null) return T.t("Pila · ") + kidName(k);
        }
        return Widgets.name(it.type);
    }

    /** Voci del menu specifiche di un widget Dot (anche quando è dentro una pila). */
    private void widgetActions(Item it, List<String> labels, List<Runnable> acts) {
        switch (it.type) {
            case "folder":
                labels.add(T.t("Aspetto della cartella"));
                acts.add(() -> folderLook(it));
                break;
            case "photo": {
                boolean dots = "dots".equals(WData.get(it, "s", ""));
                labels.add(T.t("Cambia foto"));
                acts.add(() -> pickPhoto(it));
                labels.add(dots ? T.t("Effetto: foto normale") : T.t("Effetto: foto a puntini"));
                acts.add(() -> {
                    WData.put(it, "s", dots ? "" : "dots");
                    saveLayout();
                    if (it.view != null) it.view.invalidate();
                });
                break;
            }
            case "countdown":
                labels.add(T.t("Modifica evento"));
                acts.add(() -> editCountdown(it));
                break;
            case "world_clock":
                labels.add(T.t("Scegli le città"));
                acts.add(() -> chooseCities(it));
                break;
            case "steps":
                labels.add(T.t("Obiettivo di passi"));
                acts.add(this::editStepGoal);
                break;
            case "counter":
                labels.add(T.t("Togli uno (−1)"));
                acts.add(() -> {
                    WData.put(it, "n", String.valueOf(Math.max(0, WData.getLong(it, "n", 0) - 1)));
                    saveLayout();
                    if (it.view != null) it.view.invalidate();
                });
                labels.add(T.t("Azzera"));
                acts.add(() -> {
                    WData.put(it, "n", "0");
                    saveLayout();
                    if (it.view != null) it.view.invalidate();
                });
                labels.add(T.t("Dai un nome"));
                acts.add(() -> Sheet.input(this, th, T.t("Nome del contatore"), T.t("es. Caffè"), WData.get(it, "t", ""), t -> {
                    WData.put(it, "t", t);
                    saveLayout();
                    if (it.view != null) it.view.invalidate();
                }));
                break;
            case "note":
                labels.add(T.t("Modifica la nota"));
                acts.add(() -> editNote(it));
                break;
            case "proverb":
                labels.add(T.t("Copia il proverbio"));
                acts.add(() -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("proverbio", NothingTile.proverb(it)));
                    toast(T.t("Copiato"));
                });
                break;
        }
    }

    @Override
    public void onEmptyLongPress(int page, int col, int row) {
        if (locked()) {
            Sheet.list(this, th, T.t("Home bloccata"), new String[]{T.t("Sblocca la home"), T.t("Impostazioni"), T.t("Sfondi")}, -1, w -> {
                if (w == 0) setLocked(false);
                else if (w == 1) showSettings();
                else startActivity(new Intent(this, WallpaperActivity.class));
            });
            return;
        }
        // prima ciò che si aggiunge, poi aspetto e impostazioni; la rimozione in fondo, in rosso
        List<String> opts = new ArrayList<>(Arrays.asList(T.t("Widget Dot"), T.t("Widget di sistema"),
                T.t("Aggiungi app"), T.t("Aggiungi pagina"), T.t("Sfondi"), T.t("Impostazioni"), T.t("Launcher predefinito")));
        List<String[]> icons = new ArrayList<>(Arrays.asList(SectionIcons.WIDGETS, SectionIcons.PHONE, SectionIcons.PLUS,
                SectionIcons.PAGE_ADD, Draw.PHOTO, SectionIcons.GEAR, SectionIcons.HOME));
        if (pages > 1) {
            opts.add(Sheet.DANGER + T.t("Rimuovi questa pagina"));
            icons.add(SectionIcons.CLOSE);
        }
        Bitmap[] bm = new Bitmap[icons.size()];
        for (int i = 0; i < bm.length; i++) {
            boolean danger = i == 7;
            int col0 = danger ? (th.light ? 0xFFC0161D : 0xFFFF6B6B) : th.onTile;
            bm[i] = SectionIcons.bitmap(icons.get(i), px(26), col0, danger ? col0 : th.accent, 0);
        }
        Sheet.list(this, th, "Home", opts.toArray(new String[0]), bm, -1, w -> {
            switch (w) {
                case 0: pickNothingWidget(page, col, row); break;
                case 1: pickSysWidget(page, col, row); break;
                case 2: pickAppForHome(page, col, row); break;
                case 3:
                    pages++;
                    saveLayout();
                    buildPages();
                    pager.postDelayed(() -> pager.snapTo(pages - 1), 120);
                    break;
                case 4: startActivity(new Intent(this, WallpaperActivity.class)); break;
                case 5: showSettings(); break;
                case 6: safeStart(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
                default: removePage(page); break;
            }
        }, null);
    }

    private void chooseTone(Item it) {
        String[] labels = {"Standard", T.t("Contrasto"), T.t("Colore d'accento")};
        Sheet.list(this, th, T.t("Colore"), labels, it.tone, w -> {
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
            toast(T.t("La pagina è piena"));
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
        releaseWidgets(it);
        saveLayout();
        buildPages();
    }

    private void removePage(int page) {
        if (pages <= 1) {
            toast(T.t("Serve almeno una pagina"));
            return;
        }
        Sheet.confirm(this, th, T.t("Rimuovere la pagina?"),
                T.t("Gli elementi presenti su questa pagina verranno tolti dalla home."), T.t("Rimuovi"), () -> {
                    for (Item it : new ArrayList<>(items)) {
                        if (it.page == page) {
                            items.remove(it);
                            releaseWidgets(it);
                        } else if (it.page > page) {
                            it.page--;
                        }
                    }
                    pages--;
                    saveLayout();
                    buildPages();
                });
    }

    interface WidgetChoice {
        void pick(String type, int[] size);
    }

    private void pickNothingWidget(int page, int col, int row) {
        widgetPicker(T.t("Widget Dot"), null, (type, sz) -> addDotWidget(type, sz, page, col, row));
    }

    /** Scelta di un widget Dot; con {@code only} si mostrano solo quelli disponibili in quella misura. */
    private void widgetPicker(String title, int[] only, WidgetChoice choice) {
        final Dialog[] dlg = new Dialog[1];
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(4), 0, px(4), px(8));
        int maxW = getResources().getDisplayMetrics().widthPixels - px(72);
        if (only == null) {
            TextView tip = new TextView(this);
            tip.setText(T.t("Suggerimento: trascina un widget sopra un altro della stessa misura per creare una pila ")
                    + T.t("da scorrere in verticale."));
            tip.setTextColor(th.sub);
            tip.setTypeface(th.bodyFace);
            tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tip.setPadding(px(10), px(4), px(10), 0);
            list.addView(tip);
        }
        for (String[] cat : Widgets.CATEGORIES) {
            boolean header = false;
            for (int k = 1; k < cat.length; k++) {
                final String type = cat[k];
                int[][] sizes = Widgets.sizes(type);
                if (only != null) {
                    if (!hasSize(sizes, only[0], only[1])) continue;
                    sizes = new int[][]{only};
                }
                if (!header) {
                    list.addView(pickerHeader(cat[0]));
                    header = true;
                }
                final int[][] fsizes = sizes;
                LinearLayout card = pickerCard();
                ImageView iv = new ImageView(this);
                iv.setAdjustViewBounds(true);
                iv.setImageBitmap(widgetPreview(type, sizes[0], maxW, px(150)));
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                il.gravity = Gravity.CENTER_HORIZONTAL;
                card.addView(iv, il);
                LinearLayout row2 = new LinearLayout(this);
                row2.setOrientation(LinearLayout.HORIZONTAL);
                row2.setGravity(Gravity.CENTER_VERTICAL);
                row2.setPadding(0, px(10), 0, 0);
                TextView name = new TextView(this);
                name.setText(Widgets.name(type));
                name.setTextColor(th.onTile);
                name.setTypeface(th.bodyFace);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                // con più di due misure il nome sta sopra e le misure sotto: niente nomi spezzati su due righe
                LinearLayout chips = row2;
                if (sizes.length > 2) {
                    row2.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    row2.setOrientation(LinearLayout.VERTICAL);
                    row2.setGravity(Gravity.START);
                    chips = new LinearLayout(this);
                    chips.setOrientation(LinearLayout.HORIZONTAL);
                    chips.setPadding(0, px(8), 0, 0);
                    row2.addView(chips);
                } else {
                    row2.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                // misure sempre dalla più piccola alla più grande; quella predefinita (tocco sulla scheda) è evidenziata
                final int[] def = sizes[0];
                int[][] ordered = sizes.clone();
                java.util.Arrays.sort(ordered, (x, y) -> x[0] * x[1] != y[0] * y[1]
                        ? Integer.compare(x[0] * x[1], y[0] * y[1]) : Integer.compare(x[0], y[0]));
                boolean firstChip = true;
                for (int[] sz : ordered) {
                    TextView chip = new TextView(this);
                    chip.setText(sz[0] + "×" + sz[1]);
                    chip.setTypeface(th.labelFace);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    chip.setTextColor(th.onTile);
                    chip.setGravity(Gravity.CENTER);
                    chip.setMinHeight(px(40));
                    chip.setMinWidth(px(48));
                    chip.setPadding(px(10), px(8), px(10), px(8));
                    GradientDrawable cb = new GradientDrawable();
                    cb.setCornerRadius(px(20));
                    cb.setColor(Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f));
                    if (sz == def) cb.setStroke(Math.max(1, px(1.5f)), th.accent);
                    chip.setBackground(cb);
                    chip.setContentDescription(Widgets.name(type) + " " + sz[0] + " × " + sz[1]);
                    chip.setOnClickListener(v -> {
                        if (dlg[0] != null) dlg[0].dismiss();
                        choice.pick(type, sz);
                    });
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    cl.leftMargin = chips == row2 || !firstChip ? px(6) : 0;
                    firstChip = false;
                    chips.addView(chip, cl);
                }
                card.addView(row2);
                card.setOnClickListener(v -> {
                    if (dlg[0] != null) dlg[0].dismiss();
                    choice.pick(type, fsizes[0]);
                });
                list.addView(card);
            }
        }
        dlg[0] = Sheet.custom(this, th, title, list);
    }

    static boolean hasSize(int[][] sizes, int w, int h) {
        for (int[] z : sizes) if (z[0] == w && z[1] == h) return true;
        return false;
    }

    private void addDotWidget(String type, int[] s, int page, int col, int row) {
        int tone = "alarm".equals(type) ? 2 : 0;
        Item it = new Item(type, col, row, s[0], s[1], tone, page);
        addItem(it, page, col, row);
        configureNew(it);
    }

    private TextView pickerHeader(String title) {
        TextView t = new TextView(this);
        t.setText(th.upperLabels ? title.toUpperCase(T.loc()) : title);
        t.setTypeface(th.labelFace);
        t.setTextColor(th.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(th.upperLabels ? 0.06f : 0f);
        t.setPadding(px(10), px(16), px(10), px(8));
        return t;
    }

    private LinearLayout pickerCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(14), px(14), px(14), px(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(Theme.alpha(th.onTile, th.light ? 0.05f : 0.06f));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(10);
        card.setLayoutParams(lp);
        return card;
    }

    /** Anteprima di un widget Dot disegnata alla misura reale e poi rimpicciolita. */
    private Bitmap widgetPreview(String type, int[] size, int maxW, int maxH) {
        float cell = grids.isEmpty() ? 0 : grids.get(0).getCell();
        float gap = grids.isEmpty() ? px(12) : grids.get(0).getGap();
        if (cell <= 0) cell = (getResources().getDisplayMetrics().widthPixels - px(32) - gap * (TileGrid.COLS - 1)) / TileGrid.COLS;
        int w = Math.max(1, Math.round(size[0] * cell + (size[0] - 1) * gap));
        int h = Math.max(1, Math.round(size[1] * cell + (size[1] - 1) * gap));
        Item it = new Item(type, 0, 0, size[0], size[1], "alarm".equals(type) ? 2 : 0, 0);
        switch (type) {
            case "countdown":
                WData.put(it, "t", T.t("Vacanze"));
                WData.put(it, "d", String.valueOf(System.currentTimeMillis() + 12L * 86400000L));
                break;
            case "note": WData.put(it, "t", T.t("Comprare il pane e ritirare il pacco")); break;
            case "counter":
                WData.put(it, "n", "3");
                WData.put(it, "t", T.t("Caffè"));
                break;
            case "world_clock": WData.put(it, "z", WData.DEFAULT_ZONES); break;
        }
        NothingTile v = new NothingTile(this, it, th, h24);
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        float sc = Math.min(1f, Math.min(maxW / (float) w, maxH / (float) h));
        if (sc < 1f) {
            Bitmap s2 = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)), true);
            b.recycle();
            b = s2;
        }
        return b;
    }

    private void pickAppForHome(int page, int col, int row) {
        if (allApps.isEmpty()) return;
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = allApps.get(i).label;
        Sheet.list(this, th, T.t("Aggiungi app"), labels, -1, w -> addAppToHome(allApps.get(w), page, col, row));
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
            toast(T.t("Nessun widget disponibile"));
            return;
        }
        final PackageManager pm = getPackageManager();
        final Map<String, String> appNames = new HashMap<>();
        for (AppWidgetProviderInfo p : providers) {
            String pkg = p.provider.getPackageName();
            if (appNames.containsKey(pkg)) continue;
            String app;
            try {
                app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (Exception e) {
                app = pkg;
            }
            appNames.put(pkg, app);
        }
        final Collator col2 = Collator.getInstance(T.loc());
        Collections.sort(providers, (a, b) -> {
            int c = col2.compare(appNames.get(a.provider.getPackageName()), appNames.get(b.provider.getPackageName()));
            return c != 0 ? c : col2.compare(String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm)));
        });
        final Dialog[] dlg = new Dialog[1];
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(4), 0, px(4), px(8));
        float cell = grids.isEmpty() ? px(80) : Math.max(1f, grids.get(0).getCell());
        float gap = grids.isEmpty() ? px(12) : grids.get(0).getGap();
        final int dpi = getResources().getDisplayMetrics().densityDpi;
        String lastPkg = null;
        for (AppWidgetProviderInfo info : providers) {
            String pkg = info.provider.getPackageName();
            if (!pkg.equals(lastPkg)) {
                list.addView(pickerHeader(appNames.get(pkg)));
                lastPkg = pkg;
            }
            LinearLayout card = pickerCard();
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(px(140));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(110));
            card.addView(iv, il);
            bg(appExec, () -> {
                android.graphics.drawable.Drawable d = null;
                try {
                    d = info.loadPreviewImage(this, dpi);
                    if (d == null) d = info.loadIcon(this, dpi);
                } catch (Exception ignored) {
                }
                final android.graphics.drawable.Drawable fd = d;
                if (fd != null) ui.post(() -> iv.setImageDrawable(fd));
            });
            int w = Math.max(1, Math.min(TileGrid.COLS, (int) Math.ceil((info.minWidth + gap) / (cell + gap))));
            int h = Math.max(1, (int) Math.ceil((info.minHeight + gap) / (cell + gap)));
            TextView name = new TextView(this);
            name.setText(String.valueOf(info.loadLabel(pm)) + "   ·   " + w + "×" + h);
            name.setTextColor(th.onTile);
            name.setTypeface(th.bodyFace);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            name.setPadding(0, px(8), 0, 0);
            card.addView(name);
            card.setOnClickListener(v -> {
                if (dlg[0] != null) dlg[0].dismiss();
                pendingPage = page;
                pendingCol = col;
                pendingRow = row;
                bindWidget(info);
            });
            list.addView(card);
        }
        dlg[0] = Sheet.custom(this, th, T.t("Widget di sistema"), list);
    }

    private void bindWidget(AppWidgetProviderInfo info) {
        prefs.edit().putString("pendPos", pendingPage + "," + pendingCol + "," + pendingRow).apply();
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
                toast(T.t("Impossibile aggiungere il widget"));
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
        prefs.edit().remove("pendPos").apply();
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
        prefs.edit().remove("pendPos").apply();
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
        for (Item it : Item.leaves(items)) {
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
        if (req == REQ_AUTH) {
            Runnable ok = pendingAuth;
            pendingAuth = null;
            if (res == RESULT_OK && ok != null) {
                authUntil = System.currentTimeMillis() + 60000;
                ok.run();
            }
            return;
        }
        if (req == REQ_EXPORT || req == REQ_IMPORT) {
            if (res != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            try {
                if (req == REQ_EXPORT) {
                    Backup.export(this, prefs, uri);
                    toast(T.t("Configurazione esportata"));
                } else {
                    int dropped = Backup.restore(this, prefs, uri);
                    toast(dropped > 0
                            ? T.t("Configurazione importata. ") + dropped + T.n(dropped, " widget di sistema va riaggiunto", " widget di sistema vanno riaggiunti")
                            : T.t("Configurazione importata"));
                    recreate();
                }
            } catch (Exception e) {
                toast(req == REQ_EXPORT ? T.t("Esportazione non riuscita") : T.t("File di configurazione non valido"));
            }
            return;
        }
        if (req == REQ_ICON || req == REQ_FONT) {
            if (res != RESULT_OK || data == null || data.getData() == null) return;
            if (req == REQ_ICON) saveIconImage(data.getData());
            else saveFont(data.getData());
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
        if ((req == REQ_BIND || req == REQ_CONFIG) && data != null) {
            // se il launcher è stato chiuso nel frattempo, l'id e la posizione si recuperano qui
            int id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (id != -1) pendingWidgetId = id;
        }
        if ((req == REQ_BIND || req == REQ_CONFIG) && pendingWidgetId != -1 && prefs.contains("pendPos")) {
            String[] pp = prefs.getString("pendPos", "0,0,0").split(",");
            try {
                pendingPage = Integer.parseInt(pp[0]);
                pendingCol = Integer.parseInt(pp[1]);
                pendingRow = Integer.parseInt(pp[2]);
            } catch (Exception ignored) {
            }
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

    private final Runnable reloadApps = this::loadApps;

    private void reloadAppsSoon() {
        ui.removeCallbacks(reloadApps);
        ui.postDelayed(reloadApps, 400);
    }

    private void loadApps() {
        bg(appExec, () -> {
            List<AppEntry> list = new ArrayList<>();
            try {
                final UserHandle me = android.os.Process.myUserHandle();
                final android.os.UserManager um = (android.os.UserManager) getSystemService(USER_SERVICE);
                for (UserHandle u : launcherApps.getProfiles()) {
                    for (LauncherActivityInfo i : launcherApps.getActivityList(null, u)) {
                        ComponentName cn = i.getComponentName();
                        if (cn.getPackageName().equals(getPackageName())) continue;
                        AppEntry a = new AppEntry();
                        a.label = String.valueOf(i.getLabel());
                        a.component = cn;
                        a.user = u;
                        // le app del profilo di lavoro hanno una chiave distinta (stessa app, utente diverso)
                        a.key = u.equals(me) ? cn.flattenToString()
                                : cn.flattenToString() + "#" + (um != null ? um.getSerialNumberForUser(u) : u.hashCode());
                        try {
                            ApplicationInfo ai = i.getApplicationInfo();
                            a.cat = ai.category;
                            a.game = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                        } catch (Exception ignored) {
                        }
                        try {
                            a.icon = i.getIcon(0);
                        } catch (Exception ignored) {
                        }
                        list.add(a);
                    }
                }
            } catch (Exception ignored) {
            }
            // nomi scelti dall'utente
            Map<String, String> names = jsonMap(prefs.getString("customLabels", "{}"));
            for (AppEntry a : list) {
                a.origLabel = a.label;
                String n = names.get(a.key);
                if (n != null && !n.isEmpty()) a.label = n;
            }
            final Collator col = Collator.getInstance(T.loc());
            Collections.sort(list, (x, y) -> col.compare(x.label, y.label));
            Map<String, AppEntry> lookup = new HashMap<>();
            for (AppEntry a : list) lookup.put(a.key, a);
            iconLookup = lookup;
            // icone del cassetto e del dock preparate in background: il cassetto si apre senza scatti
            final Map<String, Bitmap> pre = new HashMap<>();
            final int size = px(54 * AppTile.SCALE);
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
                } catch (Exception | OutOfMemoryError ignored) {
                    // memoria finita: le icone mancanti si creano più tardi, quando servono
                }
            }
            ui.post(() -> {
                iconPack = pack;
                allApps.clear();
                allApps.addAll(list);
                appsByKey.clear();
                Map<String, String> pkgNames = new HashMap<>();
                for (AppEntry a : list) {
                    appsByKey.put(a.key, a);
                    if (!pkgNames.containsKey(a.component.getPackageName())) pkgNames.put(a.component.getPackageName(), a.label);
                }
                Usage.names = pkgNames;
                iconCache.evictAll();
                for (Map.Entry<String, Bitmap> e : pre.entrySet()) iconCache.put(e.getKey(), e.getValue());
                refreshDrawer();
                buildDock();
                refreshAppViews();
            });
        });
    }

    private volatile Map<String, AppEntry> iconLookup = new HashMap<>();
    private volatile Map<String, String> customIcons;

    private Map<String, String> customIcons() {
        Map<String, String> m = customIcons;
        if (m == null) customIcons = m = jsonMap(prefs.getString("customIcons", "{}"));
        return m;
    }

    static Map<String, String> jsonMap(String json) {
        Map<String, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(json);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.put(k, o.optString(k));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void putJson(String pref, String key, String value) {
        try {
            JSONObject o = new JSONObject(prefs.getString(pref, "{}"));
            if (value == null) o.remove(key);
            else o.put(key, value);
            prefs.edit().putString(pref, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private Bitmap makeIcon(AppEntry a, int size, String style, boolean red, int mode, IconPacks pack) {
        String ci = customIcons().get(a.key);
        if (ci != null) {
            if (ci.startsWith("file:")) {
                Bitmap src = BitmapFactory.decodeFile(new java.io.File(getFilesDir(), "icons/" + ci.substring(5)).getPath());
                if (src != null) return IconFactory.custom(src, size);
            } else if (ci.startsWith("app:")) {
                AppEntry o = iconLookup.get(ci.substring(4));
                if (o != null && o != a && !o.key.equals(a.key)) {
                    return makeIcon(o, size, style, red, mode, pack);
                }
            }
        }
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

    /** Scelta per singola app: automatica, sempre originale, sempre a simbolo, simbolo nel colore d'accento. */
    private void chooseIconMode(AppEntry a) {
        String[] labels = {T.t("Automatica"), T.t("Originale (non modificata)"), T.t("Simbolo (forzato)"), T.t("Simbolo nel colore d'accento")};
        int cur = isRed(a.key) ? 3 : iconMode(a.key);
        Sheet.list(this, th, T.t("Stile icona"), labels, cur, w -> {
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
            iconCache.evictAll();
            refreshDrawer();
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
        } catch (Exception | OutOfMemoryError e) {
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

    /** Apre un'app passando prima dal benessere digitale (focus, limiti, pausa). */
    private void launch(AppEntry a, View v) {
        if (a == null) return;
        String pkg = a.component.getPackageName();
        if (Usage.paused(prefs, pkg)) {
            focusSheet(a, v);
            return;
        }
        int lim = Usage.limit(prefs, pkg);
        if (lim > 0 && !Usage.inGrace(pkg) && Usage.granted(this)) {
            bg(appExec, () -> {
                long ms = Usage.todayMs(this, pkg);
                ui.post(() -> {
                    if (destroyed) return;
                    if (ms >= lim * 60000L) limitSheet(a, v, ms, lim);
                    else mindfulThenLaunch(a, v);
                });
            });
            return;
        }
        mindfulThenLaunch(a, v);
    }

    private void launchNow(AppEntry a, View v) {
        if (a == null) return;
        if (v != null && !v.isAttachedToWindow()) v = null;
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
            toast(T.t("Impossibile aprire ") + a.label);
        }
    }

    // =====================================================================
    // Benessere digitale: tempo di utilizzo, limiti, pausa prima di aprire, focus
    // =====================================================================

    private void mindfulThenLaunch(AppEntry a, View v) {
        String pkg = a.component.getPackageName();
        if (!Usage.mindful(prefs).contains(pkg) || Usage.inGrace(pkg)) {
            launchNow(a, v);
            return;
        }
        String msg = T.t("Stai per aprire ") + a.label + T.t(". Prenditi qualche secondo: ti serve davvero adesso?");
        Sheet.pause(this, th, T.t("Un respiro"), msg, 5, new String[]{T.t("Lascia stare"), T.t("Apri ") + a.label}, w -> {
            Usage.grant(pkg, 3);
            launchNow(a, v);
        });
    }

    private void focusSheet(AppEntry a, View v) {
        String pkg = a.component.getPackageName();
        long until = prefs.getLong("focusUntil", 0);
        String when = until == Long.MAX_VALUE ? T.t("finché non lo spegni") : T.t("fino alle ") + hhmm(until);
        Sheet.pause(this, th, T.t("Focus attivo"),
                a.label + T.t(" è in pausa ") + when + T.t(". L'hai scelta tu come distrazione da tenere lontana."), 10,
                new String[]{T.t("Torna a concentrarti"), T.t("Apri per 5 minuti"), T.t("Termina il focus")}, w -> {
                    if (w == 1) Usage.grant(pkg, 5);
                    else stopFocus();
                    DotAccessibility.reset();
                    launchNow(a, v);
                });
    }

    private void limitSheet(AppEntry a, View v, long ms, int lim) {
        String pkg = a.component.getPackageName();
        Sheet.pause(this, th, T.t("Limite raggiunto"),
                T.t("Oggi hai usato ") + a.label + T.t(" per ") + Usage.fmt(ms) + T.t(". Il limite che hai scelto è ")
                        + Usage.fmt(lim * 60000L) + ".", 10,
                new String[]{T.t("Va bene, basta così"), T.t("Ancora 5 minuti"), T.t("Ancora 15 minuti"), T.t("Ignora il limite per oggi")},
                w -> {
                    long mins = w == 1 ? 5 : w == 2 ? 15 : (Usage.startOfToday() + 86400000L - System.currentTimeMillis()) / 60000 + 1;
                    Usage.grant(pkg, mins);
                    DotAccessibility.reset();
                    launchNow(a, v);
                });
    }

    /** Il servizio di accessibilità ha riportato alla home da un'app in pausa. */
    private void handleBlocked(Intent intent) {
        if (intent == null) return;
        String pkg = intent.getStringExtra("blocked");
        if (pkg == null) return;
        // la home è aperta a tutte le app: si accetta solo la richiesta del nostro servizio
        if (intent.getLongExtra("tok", 0) != DotAccessibility.TOKEN) {
            intent.removeExtra("blocked");
            return;
        }
        String why = intent.getStringExtra("why");
        intent.removeExtra("blocked");
        AppEntry a = null;
        for (AppEntry e : allApps) {
            if (e.component.getPackageName().equals(pkg)) {
                a = e;
                if (e.user == null || e.user.equals(android.os.Process.myUserHandle())) break;
            }
        }
        if (a == null) return;
        final AppEntry fa = a;
        Sheet.dismissOpen();
        if ("limit".equals(why)) {
            int lim = Usage.limit(prefs, pkg);
            bg(appExec, () -> {
                long ms = Usage.todayMs(this, pkg);
                ui.post(() -> {
                    if (!destroyed) limitSheet(fa, null, ms, lim);
                });
            });
        } else focusSheet(fa, null);
    }

    private String hhmm(long t) {
        return new java.text.SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", T.loc()).format(new java.util.Date(t));
    }

    // ---------- focus ----------

    private void focusMenu() {
        if (Usage.focusOn(prefs)) {
            long until = prefs.getLong("focusUntil", 0);
            String[] o = {T.t("Termina il focus"), T.t("Aggiungi 30 minuti"), T.t("Scegli le app che distraggono")};
            Sheet.list(this, th, T.t("Focus ") + (until == Long.MAX_VALUE ? T.t("attivo") : T.t("fino alle ") + hhmm(until)), o, -1, w -> {
                if (w == 0) stopFocus();
                else if (w == 1) startFocus(until == Long.MAX_VALUE ? Long.MAX_VALUE : until + 30 * 60000L);
                else pickFocusApps(null);
            });
            return;
        }
        if (Usage.focusApps(prefs).isEmpty()) {
            pickFocusApps(this::focusMenu);
            return;
        }
        String[] o = {T.t("25 minuti"), T.t("50 minuti"), T.t("1 ora"), T.t("2 ore"), T.t("Finché non lo spengo"), T.t("Scegli le app che distraggono")};
        long[] mins = {25, 50, 60, 120, -1};
        Sheet.list(this, th, T.t("Inizia il focus"), o, -1, w -> {
            if (w == 5) pickFocusApps(null);
            else startFocus(mins[w] < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + mins[w] * 60000L);
        });
    }

    private void startFocus(long until) {
        prefs.edit().putLong("focusUntil", until).apply();
        if (prefs.getBoolean("focusDnd", false)) {
            if (DotNotifications.setDnd(true)) prefs.edit().putBoolean("focusDndSet", true).apply();
            else toast(T.t("Per attivare Non disturbare serve l'accesso alle notifiche"));
        }
        Usage.GRACE.clear();
        DotAccessibility.reset();
        int n = Usage.focusApps(prefs).size();
        toast(T.t("Focus attivo: ") + n + T.n(n, " app in pausa", " app in pausa"));
        updateWellbeing();
        if (settingsOpen) refillSettings();
    }

    private void stopFocus() {
        prefs.edit().putLong("focusUntil", 0).apply();
        if (prefs.getBoolean("focusDndSet", false)) {
            DotNotifications.setDnd(false);
            prefs.edit().putBoolean("focusDndSet", false).apply();
        }
        DotAccessibility.reset();
        updateWellbeing();
        if (settingsOpen) refillSettings();
    }

    /** Stato del focus per i widget e il cassetto; chiude il focus scaduto. */
    private void updateWellbeing() {
        long until = prefs.getLong("focusUntil", 0);
        if (until != 0 && until <= System.currentTimeMillis()) {
            prefs.edit().putLong("focusUntil", 0).apply();
            if (prefs.getBoolean("focusDndSet", false)) {
                DotNotifications.setDnd(false);
                prefs.edit().putBoolean("focusDndSet", false).apply();
            }
            until = 0;
        }
        boolean was = Usage.focusActive;
        Usage.focusActive = until > System.currentTimeMillis();
        Usage.focusUntil = until;
        Usage.permitted = Usage.granted(this);
        refreshTiles("focus");
        if (was != Usage.focusActive) onNotificationsChanged();
    }

    private void refreshUsage() {
        updateWellbeing();
        if (!Usage.permitted) return;
        boolean any = false;
        for (Item it : Item.leaves(items)) if ("usage".equals(it.type)) any = true;
        if (!any && !settingsOpen) return;
        bg(appExec, () -> {
            Usage.refreshToday(getApplicationContext());
            ui.post(() -> {
                if (!destroyed) refreshTiles("usage");
            });
        });
    }

    /** L'app è in pausa (focus o profilo di lavoro sospeso)? Icona attenuata nel cassetto, nel dock e sulla home. */
    public boolean dimmed(String key) {
        if (key == null) return false;
        AppEntry a = appsByKey.get(key);
        if (a == null) return false;
        if (a.user != null && quietUsers.contains(a.user)) return true;
        return Usage.focusActive && Usage.focusApps(prefs).contains(a.component.getPackageName());
    }

    // ---------- profilo di lavoro ----------

    /** Profili (di lavoro) attualmente in pausa. */
    private final Set<android.os.UserHandle> quietUsers = new HashSet<>();

    private boolean hasWorkProfile() {
        try {
            return launcherApps.getProfiles().size() > 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean workPaused() {
        return !quietUsers.isEmpty();
    }

    /** Rilegge quali profili sono in pausa; true se è cambiato qualcosa. */
    private boolean readQuiet() {
        Set<android.os.UserHandle> now = new HashSet<>();
        try {
            android.os.UserManager um = userManager();
            android.os.UserHandle me = android.os.Process.myUserHandle();
            for (android.os.UserHandle u : launcherApps.getProfiles()) {
                if (!u.equals(me) && um.isQuietModeEnabled(u)) now.add(u);
            }
        } catch (Exception ignored) {
        }
        if (now.equals(quietUsers)) return false;
        quietUsers.clear();
        quietUsers.addAll(now);
        return true;
    }

    private void setWorkPaused(boolean pause) {
        if (Build.VERSION.SDK_INT < 28) {
            toast(T.t("Mettere in pausa le app di lavoro richiede Android 9 o successivo"));
            return;
        }
        android.os.UserHandle me = android.os.Process.myUserHandle();
        try {
            for (android.os.UserHandle u : launcherApps.getProfiles()) {
                if (!u.equals(me)) userManager().requestQuietModeEnabled(pause, u);
            }
        } catch (SecurityException e) {
            toast(T.t("Serve che Dot Launcher sia il launcher predefinito"));
            return;
        } catch (Exception e) {
            toast(T.t("Impossibile cambiare lo stato delle app di lavoro"));
            return;
        }
        ui.postDelayed(this::onWorkProfileChanged, 600);
    }

    private void onWorkProfileChanged() {
        if (destroyed) return;
        if (readQuiet()) onNotificationsChanged();
    }

    private final BroadcastReceiver profileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            onWorkProfileChanged();
        }
    };

    private void pickFocusApps(Runnable after) {
        pickApps(T.t("App che distraggono"), Usage.focusApps(prefs), sel -> {
            prefs.edit().putStringSet("focusApps", sel).apply();
            DotAccessibility.reset();
            if (Usage.focusActive) {
                refreshDrawer();
                refreshAppViews();
                buildDock();
            }
            if (settingsOpen) refillSettings();
            if (after != null && !sel.isEmpty()) after.run();
        });
    }

    interface OnApps {
        void picked(Set<String> pkgs);
    }

    /** Scelta multipla di app (per pacchetto): si tocca per selezionare, si conferma chiudendo. */
    private void pickApps(String title, Set<String> selected, OnApps done) {
        final Set<String> sel = new HashSet<>(selected);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(this);
        hint.setText(T.t("Tocca le app per sceglierle, poi chiudi il pannello."));
        hint.setTextColor(th.sub);
        hint.setTypeface(th.bodyFace);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(px(14), 0, px(14), px(8));
        list.addView(hint);
        Set<String> seen = new HashSet<>();
        for (AppEntry a : allApps) {
            String pkg = a.component.getPackageName();
            if (!seen.add(pkg)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(12), px(8), px(14), px(8));
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(iconFor(a.key, px(36)));
            row.addView(iv, new LinearLayout.LayoutParams(px(36), px(36)));
            TextView t = new TextView(this);
            t.setText(a.label);
            t.setTextColor(th.onTile);
            t.setTypeface(th.bodyFace);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setPadding(px(14), 0, px(8), 0);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            View check = new View(this);
            Runnable paint = () -> {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                if (sel.contains(pkg)) g.setColor(th.accent);
                else g.setStroke(px(2), Theme.alpha(th.onTile, 0.35f));
                check.setBackground(g);
            };
            paint.run();
            row.addView(check, new LinearLayout.LayoutParams(px(20), px(20)));
            row.setOnClickListener(x -> {
                if (!sel.remove(pkg)) sel.add(pkg);
                paint.run();
                x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            });
            list.addView(row);
        }
        Dialog d = Sheet.custom(this, th, title, list);
        d.setOnDismissListener(x -> done.picked(sel));
    }

    // ---------- tempo di utilizzo e limiti ----------

    public void askUsageAccess() {
        Sheet.confirm(this, th, T.t("Tempo di utilizzo"),
                T.t("Per sapere quanto usi ogni app, Dot Launcher ha bisogno dell'accesso ai dati di utilizzo. ")
                        + T.t("I dati vengono letti solo sul telefono, quando servono, e non vengono salvati né inviati.\n\n")
                        + T.t("Nell'elenco che si apre scegli Dot Launcher e attiva \"Consenti accesso\"."),
                T.t("Apri le impostazioni"), () -> {
                    Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    if (Build.VERSION.SDK_INT >= 29) i.setData(Uri.fromParts("package", getPackageName(), null));
                    try {
                        startActivity(i);
                    } catch (Exception e) {
                        safeStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    }
                });
    }

    private void showUsage() {
        if (!Usage.granted(this)) {
            askUsageAccess();
            return;
        }
        bg(appExec, () -> {
            Usage.refreshToday(getApplicationContext());
            ui.post(() -> {
                if (destroyed) return;
                refreshTiles("usage");
                List<Usage.App> l = Usage.today;
                List<AppEntry> entries = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                List<Bitmap> icons = new ArrayList<>();
                for (Usage.App u : l) {
                    AppEntry a = entryForPkg(u.pkg);
                    if (a == null) continue;
                    entries.add(a);
                    int lim = Usage.limit(prefs, u.pkg);
                    labels.add(a.label + "\n" + Usage.fmt(u.ms) + " · " + u.opens + T.n(u.opens, " apertura", " aperture")
                            + (lim > 0 ? T.t(" · limite ") + Usage.fmt(lim * 60000L) : ""));
                    icons.add(iconFor(a.key, px(36)));
                }
                if (entries.isEmpty()) {
                    toast(T.t("Oggi non hai ancora usato nessuna app"));
                    return;
                }
                Sheet.list(this, th, T.t("Oggi · ") + Usage.fmt(Usage.total), labels.toArray(new String[0]),
                        icons.toArray(new Bitmap[0]), -1, w -> appWellbeing(entries.get(w)), null);
            });
        });
    }

    private AppEntry entryForPkg(String pkg) {
        AppEntry found = null;
        for (AppEntry e : allApps) {
            if (!e.component.getPackageName().equals(pkg)) continue;
            if (found == null || e.user == null || e.user.equals(android.os.Process.myUserHandle())) found = e;
        }
        return found;
    }

    /** Impostazioni di benessere di una singola app. */
    private void appWellbeing(AppEntry a) {
        String pkg = a.component.getPackageName();
        int lim = Usage.limit(prefs, pkg);
        boolean mind = Usage.mindful(prefs).contains(pkg);
        boolean dist = Usage.focusApps(prefs).contains(pkg);
        String[] o = {
                lim > 0 ? T.t("Limite giornaliero: ") + Usage.fmt(lim * 60000L) : T.t("Imposta un limite giornaliero"),
                mind ? T.t("Pausa prima di aprire: attiva") : T.t("Pausa prima di aprire: spenta"),
                dist ? T.t("Distrae (in pausa durante il focus): sì") : T.t("Distrae (in pausa durante il focus): no"),
                T.t("Tempo di utilizzo di oggi")
        };
        Sheet.list(this, th, a.label, o, -1, w -> {
            if (w == 0) chooseLimit(a);
            else if (w == 1) {
                Set<String> s = Usage.mindful(prefs);
                if (mind) s.remove(pkg);
                else s.add(pkg);
                prefs.edit().putStringSet("mindful", s).apply();
                toast(mind ? T.t("Pausa tolta") : T.t("Prima di aprire ") + a.label + T.t(" ci sarà un respiro di 5 secondi"));
                if (settingsOpen) refillSettings();
            } else if (w == 2) {
                Set<String> s = Usage.focusApps(prefs);
                if (dist) s.remove(pkg);
                else s.add(pkg);
                prefs.edit().putStringSet("focusApps", s).apply();
                DotAccessibility.reset();
                if (Usage.focusActive) onNotificationsChanged();
                if (settingsOpen) refillSettings();
            } else {
                if (!Usage.granted(this)) {
                    askUsageAccess();
                    return;
                }
                bg(appExec, () -> {
                    List<Usage.App> l = Usage.compute(getApplicationContext(), Usage.startOfToday());
                    long ms = 0;
                    int opens = 0;
                    for (Usage.App u : l) {
                        if (u.pkg.equals(pkg)) {
                            ms = u.ms;
                            opens = u.opens;
                        }
                    }
                    final long fms = ms;
                    final int fo = opens;
                    ui.post(() -> {
                        if (!destroyed) toast(a.label + T.t(" oggi: ") + Usage.fmt(fms) + ", " + fo + T.n(fo, " apertura", " aperture"));
                    });
                });
            }
        });
    }

    private void chooseLimit(AppEntry a) {
        String pkg = a.component.getPackageName();
        if (!Usage.granted(this)) {
            askUsageAccess();
            return;
        }
        String[] o = {T.t("Nessun limite"), T.t("15 minuti"), T.t("30 minuti"), T.t("45 minuti"), T.t("1 ora"), T.t("1 ora e mezza"), T.t("2 ore"), T.t("3 ore")};
        int[] m = {0, 15, 30, 45, 60, 90, 120, 180};
        int cur = Usage.limit(prefs, pkg), sel = 0;
        for (int i = 0; i < m.length; i++) if (m[i] == cur) sel = i;
        Sheet.list(this, th, T.t("Limite per ") + a.label, o, sel, w -> {
            Usage.setLimit(prefs, pkg, m[w]);
            Usage.GRACE.remove(pkg);
            DotAccessibility.reset();
            toast(m[w] == 0 ? T.t("Limite tolto") : T.t("Limite: ") + o[w] + T.t(" al giorno"));
            if (settingsOpen) refillSettings();
        });
    }

    private void limitsMenu() {
        if (!Usage.granted(this)) {
            askUsageAccess();
            return;
        }
        List<AppEntry> with = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String pkg : Usage.limited(prefs)) {
            AppEntry a = entryForPkg(pkg);
            if (a == null) continue;
            with.add(a);
            labels.add(a.label + " · " + Usage.fmt(Usage.limit(prefs, pkg) * 60000L));
        }
        labels.add(T.t("+ Aggiungi un limite"));
        Sheet.list(this, th, T.t("Limiti giornalieri"), labels.toArray(new String[0]), -1, w -> {
            if (w < with.size()) chooseLimit(with.get(w));
            else {
                String[] names = new String[allApps.size()];
                for (int i = 0; i < names.length; i++) names[i] = allApps.get(i).label;
                Sheet.list(this, th, T.t("Scegli l'app"), names, -1, x -> chooseLimit(allApps.get(x)));
            }
        });
    }

    private void wellbeingSettings(SettingsScreen st) {
        st.section(T.t("Benessere digitale"));
        boolean granted = Usage.granted(this);
        st.row(NothingTile.HOURGLASS, T.t("Tempo di utilizzo"), granted
                ? (Usage.computedAt > 0 ? T.t("Oggi: ") + Usage.fmt(Usage.total) : T.t("Tocca per vedere le app di oggi"))
                : T.t("Tocca per consentire l'accesso ai dati di utilizzo"), this::showUsage);
        long until = prefs.getLong("focusUntil", 0);
        st.row(NothingTile.TARGET, T.t("Modalità focus"), Usage.focusOn(prefs)
                ? (until == Long.MAX_VALUE ? T.t("Attiva") : T.t("Attiva fino alle ") + hhmm(until)) : T.t("Spenta: tocca per iniziare"),
                this::focusMenu);
        int nf = Usage.focusApps(prefs).size();
        st.row(SectionIcons.BLOCK, T.t("App che distraggono"), nf == 0 ? T.t("Nessuna: scegli quali mettere in pausa nel focus")
                : nf + T.n(nf, " app in pausa durante il focus", " app in pausa durante il focus"), () -> pickFocusApps(null));
        st.toggle(Draw.BELL, T.t("Non disturbare durante il focus"), T.t("Silenzia le notifiche (serve l'accesso alle notifiche)"),
                prefs.getBoolean("focusDnd", false), on -> {
                    prefs.edit().putBoolean("focusDnd", on).apply();
                    if (on && !DotNotifications.active()) askNotificationAccess();
                });
        int nl = Usage.limited(prefs).size();
        st.row(NothingTile.HOURGLASS, T.t("Limiti giornalieri"), nl == 0 ? T.t("Nessuno") : nl + T.n(nl, " app con limite", " app con limite"),
                this::limitsMenu);
        int nm = Usage.mindful(prefs).size();
        st.row(SectionIcons.PAUSE, T.t("Pausa prima di aprire"), nm == 0 ? T.t("Un respiro di 5 secondi prima delle app che scegli")
                : nm + T.n(nm, " app", " app") + T.t(" con un respiro prima di aprirsi"),
                () -> pickApps(T.t("Pausa prima di aprire"), Usage.mindful(prefs), sel -> {
                    prefs.edit().putStringSet("mindful", sel).apply();
                    if (settingsOpen) refillSettings();
                }));
        st.toggle(SectionIcons.LOCK, T.t("Blocco rigido"),
                T.t("Riporta alla home anche se apri l'app dalle notifiche o dalle recenti (usa il servizio di accessibilità)"),
                prefs.getBoolean("hardBlock", false), on -> {
                    prefs.edit().putBoolean("hardBlock", on).apply();
                    DotAccessibility.reset();
                    if (on && !DotAccessibility.active()) {
                        Sheet.confirm(this, th, T.t("Attiva il servizio"),
                                T.t("Il blocco rigido usa il servizio \"Gesti di Dot Launcher\" in Accessibilità: guarda solo ")
                                        + T.t("quale app è in primo piano, mai il contenuto dello schermo.\n\n")
                                        + T.t("Se Android dice che è un'impostazione con restrizioni: Impostazioni → App → Dot Launcher → ")
                                        + T.t("menu ⋮ → \"Consenti impostazioni con restrizioni\", poi riprova."),
                                T.t("Apri Accessibilità"), () -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
                    }
                });
    }

    // =====================================================================
    // Guida: dove si trova ogni funzione
    // =====================================================================

    private void showGuide() {
        final Dialog[] dlg = new Dialog[1];
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(4), 0, px(4), px(12));
        final Runnable openSet = this::showSettings;
        Object[][] g = {
                {"Home"},
                {"Aggiungere un widget", "Tieni premuto uno spazio vuoto della home → Widget Dot.",
                        (Runnable) () -> pickNothingWidget(pager.getCurrent(), 0, 0)},
                {"Widget delle altre app", "Tieni premuto uno spazio vuoto → Widget di sistema.",
                        (Runnable) () -> pickSysWidget(pager.getCurrent(), 0, 0)},
                {"Pila di widget", "Trascina un widget sopra un altro della stessa misura, poi scorri in verticale per cambiarlo.", null},
                {"Cartelle", "Trascina un'app sopra un'altra. Tieni premuta la cartella per rinominarla o cambiarne l'aspetto.", null},
                {"Modificare un widget", "Tieni premuto il widget: ridimensiona, colore e impostazioni del widget.", null},
                {"Sfondi", "Sfondi generati, foto a puntini o a strisce.",
                        (Runnable) () -> startActivity(new Intent(this, WallpaperActivity.class))},
                {"Cassetto e ricerca"},
                {"Aprire il cassetto", "Scorri in su dalla home: il cassetto segue il dito.", (Runnable) () -> openDrawer(false)},
                {"Ricerca universale", "App, impostazioni del telefono, contatti, eventi, calcoli e conversioni (es. \"10 km in mi\").",
                        (Runnable) () -> openDrawer(true)},
                {"Sezioni del cassetto", "Tieni premuta l'icona di una sezione nella barra in basso per rinominarla, spostarla o eliminarla. Trascina un'app su un'icona per spostarla in quella sezione.", null},
                {"App nascoste", "Tieni premuta un'app → Nascondi dal cassetto. Puoi proteggerle con impronta o PIN.",
                        (Runnable) () -> withAuth(this::manageHidden)},
                {"Profilo di lavoro", "Se il telefono ha un profilo di lavoro compare la sezione Lavoro: tieni premuta la sua icona per mettere in pausa le app.", null},
                {"Pagina a sinistra"},
                {"IA, notizie, calendario, attività e note", "Scorri a destra dalla prima pagina. Tocca ⋮ in alto per scegliere e ordinare i blocchi.",
                        (Runnable) this::openPanel},
                {"Personalizzazione"},
                {"Nome e icona di un'app", "Tieni premuta l'app → Rinomina o Cambia icona.", null},
                {"Colori, forme, caratteri e animazioni", "Impostazioni → Aspetto, Animazioni e Personalizzazione.", openSet},
                {"Gesti", "Impostazioni → Gesti: scegli cosa fanno scorrimenti, doppio tocco, due dita e pizzico.", openSet},
                {"Benessere digitale"},
                {"Tempo di utilizzo", "Quanto usi ogni app oggi, con il numero di aperture.", (Runnable) this::showUsage},
                {"Modalità focus", "Metti in pausa le app che distraggono per un po' di tempo.", (Runnable) this::focusMenu},
                {"Limiti giornalieri", "Un tempo massimo al giorno per le app che scegli.", (Runnable) this::limitsMenu},
                {"Pausa prima di aprire", "Tieni premuta un'app → Benessere digitale… → Pausa prima di aprire.", null},
                {"Aiuto"},
                {"Configurazione guidata", "Rivedi lingua, stile, icone e permessi.", (Runnable) () -> {
                    closeSettings();
                    prefs.edit().putBoolean("onboarded", false).apply();
                    showOnboarding(0);
                }},
                {"Segnala un problema", "Copia il registro degli errori e aprilo su GitHub.", (Runnable) this::reportProblem},
        };
        for (Object[] e : g) {
            if (e.length == 1) {
                list.addView(pickerHeader(T.t((String) e[0])));
                continue;
            }
            final Runnable act = (Runnable) e[2];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(12), px(10), px(10), px(10));
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(this);
            t.setText(T.t((String) e[0]));
            t.setTypeface(Fonts.medium);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(th.onTile);
            tx.addView(t);
            TextView d = new TextView(this);
            d.setText(T.t((String) e[1]));
            d.setTypeface(th.bodyFace);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            d.setTextColor(th.sub);
            d.setPadding(0, px(2), 0, 0);
            tx.addView(d);
            row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (act != null) {
                TextView go = new TextView(this);
                go.setText("›");
                go.setTextColor(th.accentText);
                go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                go.setPadding(px(10), 0, px(4), 0);
                row.addView(go);
                row.setOnClickListener(v -> {
                    if (dlg[0] != null) dlg[0].dismiss();
                    if (act == openSet || !settingsOpen) {
                        act.run();
                        return;
                    }
                    // la funzione si apre sopra la home: prima si chiudono le impostazioni
                    closeSettings();
                    ui.postDelayed(() -> {
                        if (!destroyed) act.run();
                    }, Motion.EXIT + 80);
                });
                GradientDrawable on = new GradientDrawable();
                on.setColor(Theme.alpha(th.onTile, 0.08f));
                on.setCornerRadius(px(18));
                android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
                sl.addState(new int[]{android.R.attr.state_pressed}, on);
                row.setBackground(sl);
            }
            list.addView(row);
        }
        dlg[0] = Sheet.custom(this, th, T.t("Guida"), list);
    }

    // =====================================================================
    // Registro degli errori
    // =====================================================================

    private static final String ISSUES = "https://github.com/Rex11107/dot-launcher/issues";

    private void reportProblem() {
        String log = CrashLog.read(this);
        if (log.isEmpty()) {
            Sheet.confirm(this, th, T.t("Segnala un problema"),
                    T.t("Non ci sono errori registrati. Se qualcosa non funziona come dovrebbe, descrivilo in una "
                            + "segnalazione su GitHub: indica il telefono, la versione e cosa stavi facendo."),
                    T.t("Apri GitHub"), () -> safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES))));
            return;
        }
        String[] o = {T.t("Copia il registro"), T.t("Condividi il registro"), T.t("Apri le segnalazioni su GitHub"),
                T.t("Cancella il registro")};
        Sheet.list(this, th, T.t("Segnala un problema"), o, -1, w -> {
            switch (w) {
                case 0: {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Dot Launcher", log));
                    toast(T.t("Registro copiato: incollalo nella segnalazione"));
                    break;
                }
                case 1: {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Dot Launcher " + versionName())
                            .putExtra(Intent.EXTRA_TEXT, log);
                    safeStart(Intent.createChooser(i, T.t("Condividi il registro")));
                    break;
                }
                case 2:
                    safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES)));
                    break;
                default:
                    CrashLog.clear(this);
                    toast(T.t("Registro cancellato"));
                    refillSettings();
            }
        });
    }

    // =====================================================================
    // Configurazione guidata
    // =====================================================================

    private void showOnboarding(int page) {
        if (onboarding != null) return;
        onboarding = new Onboarding(this, th, prefs, this, page);
        root.addView(onboarding, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        onboarding.setInsets(insTop, insBottom);
    }

    @Override
    public void onboardLanguage(String lang) {
        prefs.edit().putString("lang", lang).putInt("onboardPage", 1).commit();
        RestartActivity.restart(this);
    }

    @Override
    public void onboardDone(boolean lookChanged) {
        if (onboarding != null) {
            Onboarding o = onboarding;
            onboarding = null;
            o.animate().alpha(0f).setDuration(Motion.EXIT).setInterpolator(Motion.EASE_IN).withEndAction(() -> root.removeView(o)).start();
        }
        if ("free".equals(prefs.getString("drawerOrder", "alpha"))) {
            sections.sortAll(this);
            sections.save();
        }
        if (lookChanged) ui.postDelayed(this::recreate, 240);
        else refreshDrawer();
    }

    @Override
    public boolean notificationsOn() {
        return DotNotifications.active();
    }

    @Override
    public boolean usageOn() {
        return Usage.granted(this);
    }

    @Override
    public boolean locationOn() {
        return prefs.contains("mlat")
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void askLocation() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
    }

    @Override
    public boolean isDefaultLauncher() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                        && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_ROLE);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    private void appInfo(AppEntry a) {
        try {
            launcherApps.startAppDetailsActivity(a.component, a.user, null, null);
        } catch (Exception e) {
            toast(T.t("Impossibile aprire le informazioni"));
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

    private int dockMax() {
        return Math.max(0, Math.min(7, prefs.getInt("dockMax", 5)));
    }

    private void buildDock() {
        dock.removeAllViews();
        dock.setVisibility(dockMax() == 0 ? View.GONE : View.VISIBLE);
        if (prefs.getBoolean("dockBg", false)) {
            GradientDrawable db = new GradientDrawable();
            db.setColor(Theme.alpha(th.tile, th.wall ? 0.75f : 1f));
            db.setCornerRadius(px(36));
            if (th.stroke != 0) db.setStroke(Math.max(1, px(1)), th.stroke);
            dock.setBackground(db);
        } else dock.setBackground(null);
        List<String> keys = dockKeys();
        if (keys.size() > dockMax()) keys = new ArrayList<>(keys.subList(0, dockMax()));
        if (keys.isEmpty() && !prefs.getBoolean("dockInit", false) && !allApps.isEmpty()) {
            keys = defaultDock();
            saveDock(keys);
            prefs.edit().putBoolean("dockInit", true).apply();
        }
        // su schermi stretti (o con "dimensione display" grande) le icone si rimpiccioliscono per starci tutte
        int avail = getResources().getDisplayMetrics().widthPixels - px(16 * 2 + 16);
        int size = Math.max(px(36), Math.min(px(54 * AppTile.SCALE), avail / Math.max(1, keys.size()) - px(18)));
        for (String k : keys) {
            AppEntry a = appsByKey.get(k);
            if (a == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(iconFor(a.key, size));
            if (dimmed(a.key)) iv.setAlpha(0.35f);
            else if (badge(a.key) > 0) iv.setForeground(new BadgeDrawable(th.accent, th.bg));
            iv.setContentDescription(a.label);
            iv.setTag(R_KEY, a.key);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = px(9);
            lp.rightMargin = px(9);
            iv.setOnClickListener(v -> launch(a, v));
            iv.setOnLongClickListener(v -> {
                if (locked()) showAppMenu(a, Boolean.TRUE);
                else beginDrag(SRC_DOCK, null, a.key, v);
                return true;
            });
            dock.addView(iv, lp);
        }
        if (dock.getChildCount() == 0) {
            TextView t = new TextView(this);
            t.setText(T.t("Trascina qui un'app dal cassetto"));
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
            labels.add(T.t("Aggiungi alla home"));
            acts.add(() -> {
                closeDrawer();
                addAppToHome(a, pager.getCurrent(), 0, 0);
            });
        }
        labels.add(inDock ? T.t("Rimuovi dal dock") : T.t("Aggiungi al dock"));
        acts.add(() -> {
            if (inDock) dk.remove(a.key);
            else if (dk.size() >= dockMax()) {
                toast(T.t("Il dock contiene al massimo ") + dockMax() + T.n(dockMax(), " app", " app"));
                return;
            } else dk.add(a.key);
            prefs.edit().putBoolean("dockInit", true).apply();
            saveDock(dk);
            buildDock();
        });
        labels.add(T.t("Rinomina"));
        acts.add(() -> renameApp(a));
        labels.add(T.t("Cambia icona"));
        acts.add(() -> changeIcon(a));
        labels.add(T.t("Stile icona"));
        acts.add(() -> chooseIconMode(a));
        if (fromDock == null) {
            labels.add(T.t("Sposta in sezione…"));
            acts.add(() -> moveAppToSection(a));
            labels.add(T.t("Aggiungi a una cartella"));
            acts.add(() -> addToFolder(a));
            labels.add(T.t("Nascondi dal cassetto"));
            acts.add(() -> {
                Set<String> h = hidden();
                h.add(a.key);
                prefs.edit().putStringSet("hidden", h).apply();
                refreshDrawer();
            });
        }
        labels.add(T.t("Azione scorrendo in su…"));
        acts.add(() -> chooseSwipeAction(a));
        labels.add(T.t("Benessere digitale…"));
        acts.add(() -> appWellbeing(a));
        labels.add(T.t("Info app"));
        acts.add(() -> appInfo(a));
        labels.add(T.t("Disinstalla"));
        acts.add(() -> safeStart(new Intent(Intent.ACTION_DELETE,
                Uri.fromParts("package", a.component.getPackageName(), null))));
        return showMenuWithShortcuts(a.label, a, labels, acts);
    }


    // =====================================================================
    // Trascinamento: dal cassetto, dalla home e dal dock verso home, dock, cartelle e barra in alto
    // =====================================================================

    private static final int SRC_HOME = 0, SRC_DOCK = 1, SRC_DRAWER = 2, SRC_DFOLDER = 3;
    private Sections.Entry dragEntry; // contenitore del cassetto trascinato
    private static final int T_NONE = 0, T_BAR = 1, T_DOCK = 2, T_CELL = 3, T_MERGE = 4, T_BAD = 5, T_STACK = 10;
    // destinazioni dentro il cassetto a sezioni
    private static final int T_DTAB = 6, T_DMERGE = 7, T_DPOS = 8, T_DSEC = 9;
    private boolean drawerDrag; // trascinamento che resta nel cassetto
    private int tSec = -1;
    private Sections.Entry tEntry;
    private boolean tAfter;
    private Runnable hoverRun;
    private int hoverCode = Integer.MIN_VALUE;

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
        t.setTextColor(hot ? th.onAccent : th.onTile);
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
        if (wave == null || settingsOpen || panelOpen || resizer != null) return;
        wave.bringToFront();
        int c = th.light ? 0xFF000000 : 0xFFFFFFFF;
        wave.post(() -> wave.play(c, 0.5f, 1f));
    }

    private int iconCellPx() {
        float c = grids.isEmpty() ? 0 : grids.get(0).getCell();
        return c > 0 ? Math.round(c * 0.86f) : px(62);
    }

    private void startHomeDrag(Item it, View v) {
        if (locked()) {
            // home bloccata: niente trascinamenti, solo il menu (senza spostare o togliere)
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            onItemMenu(it);
            return;
        }
        beginDrag(SRC_HOME, it, it.isApp() ? it.data : null, v);
    }

    private boolean locked() {
        return prefs.getBoolean("locked", false);
    }

    private void setLocked(boolean on) {
        prefs.edit().putBoolean("locked", on).apply();
        toast(on ? T.t("Home bloccata: niente spostamenti accidentali") : T.t("Home sbloccata"));
        refillSettings();
    }

    private void beginDrag(int src, Item it, String key, View v) {
        if (dragSrc >= 0 || v == null || resizer != null || !fingerDown) return;
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
        if ((src == SRC_HOME || src == SRC_DFOLDER) && v.getWidth() > 0) {
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
        dragShadow.setVisibility(src == SRC_DRAWER || src == SRC_DFOLDER ? View.INVISIBLE : View.VISIBLE);
        root.addView(dragShadow, new FrameLayout.LayoutParams(shadowW, shadowH));
        positionShadow();
        dragShadow.animate().scaleX(1.06f).scaleY(1.06f).setDuration(Motion.QUICK).setInterpolator(Motion.STANDARD).start();
        if (src != SRC_DRAWER && src != SRC_DFOLDER) v.setAlpha(0.25f);

        TileGrid.anyDragging = true;
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // il tocco in corso non deve più arrivare alle altre viste (cassetto, pagine, widget)
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        super.dispatchTouchEvent(cancel);
        if (gestures != null) gestures.onTouchEvent(cancel);
        cancel.recycle();
        // nel cassetto il menù compare subito; se il dito si sposta si chiude e parte il trascinamento
        if (src == SRC_DFOLDER && dragEntry != null) {
            Sections.Section fs = sections.sectionOfEntry(dragEntry);
            if (fs != null) dragMenu = folderMenu(fs, dragEntry);
        }
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
                    // dal cassetto a sezioni il trascinamento resta nel cassetto (sezioni, contenitori, ordine);
                    // per portare l'app sulla home si passa sopra "Sulla home" in alto
                    drawerDrag = (dragSrc == SRC_DRAWER || dragSrc == SRC_DFOLDER) && drawerOpen && !searchMode;
                    if (dragSrc == SRC_DRAWER && !drawerDrag) hideDrawerNow();
                    if (drawerDrag && dragSourceView != null) dragSourceView.setAlpha(0.3f);
                    if (dragSourceView != null && dragSrc != SRC_DRAWER && dragSrc != SRC_DFOLDER)
                        dragSourceView.setVisibility(View.INVISIBLE);
                    dragShadow.setVisibility(View.VISIBLE);
                    showDropBar();
                    if (!drawerDrag) {
                        // la home si rimpicciolisce verso il basso per fare spazio alla barra in alto
                        pager.setPivotX(pager.getWidth() / 2f);
                        pager.setPivotY(pager.getHeight());
                        pager.animate().scaleX(0.88f).scaleY(0.88f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
                    }
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
        dropLeft.setText(drawerDrag ? T.t("Sulla home") : dragSrc == SRC_DRAWER ? T.t("Info app")
                : dragSrc == SRC_DOCK ? T.t("Togli dal dock") : T.t("Rimuovi"));
        dropRight.setText(T.t("Disinstalla"));
        dropRight.setVisibility(app ? View.VISIBLE : View.GONE);
        if (dragSrc == SRC_DRAWER && !app) dropLeft.setVisibility(View.GONE);
        else dropLeft.setVisibility(View.VISIBLE);
        styleDropPill(dropLeft, false);
        styleDropPill(dropRight, false);
        dropBar.animate().cancel();
        dropBar.setAlpha(0f);
        dropBar.setVisibility(View.VISIBLE);
        dropBar.animate().alpha(1f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
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
        return dockMax() > 0 && dragKey != null && !dragKey.startsWith("sc:") && (dragItem == null || dragItem.isApp());
    }

    private Item itemAt(int page, int c, int r) {
        for (Item o : items) {
            if (o.page != page) continue;
            if (c >= o.col && c < o.col + o.w && r >= o.row && r < o.row + o.h) return o;
        }
        return null;
    }

    private void updateTarget() {
        if (drawerDrag) {
            updateDrawerTarget();
            return;
        }
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
            boolean full = dk.size() >= dockMax() && !dk.contains(dragKey);
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

        if (dragSrc == SRC_HOME && dragItem != null && (stackable(dragItem) || dragItem.isStack())) {
            // widget sopra un altro widget della stessa misura: pila
            int[] cu = g.cellUnder(cx, cy);
            Item o = itemAt(page, cu[0], cu[1]);
            if (o != null && o != dragItem && (stackable(o) || o.isStack()) && o.w == dragW && o.h == dragH) {
                tKind = T_STACK;
                tTarget = o;
                g.setPreview(TileGrid.PREVIEW_MERGE, o.col, o.row, o.w, o.h);
                setEdge(edgeDirAt());
                return;
            }
        }
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

    private void hover(int code, Runnable r, long delay) {
        if (code == hoverCode) return;
        clearHover();
        hoverCode = code;
        if (r != null) {
            hoverRun = r;
            ui.postDelayed(r, delay);
        }
    }

    private void clearHover() {
        if (hoverRun != null) ui.removeCallbacks(hoverRun);
        hoverRun = null;
        hoverCode = Integer.MIN_VALUE;
    }

    /** Destinazioni mentre si trascina un'app dentro il cassetto a sezioni. */
    private void updateDrawerTarget() {
        clearTargets();
        tKind = T_NONE;
        tEntry = null;
        tSec = -1;
        // 1) barra in alto: "Sulla home" (passandoci sopra si va alla home) o "Disinstalla"
        int[] bl = new int[2];
        dropBar.getLocationOnScreen(bl);
        if (lastRawY < bl[1] + dropBar.getHeight()) {
            boolean right = dropRight.getVisibility() == View.VISIBLE && lastRawX > root.getWidth() / 2f;
            tKind = T_BAR;
            tZone = right ? 2 : 1;
            styleDropPill(right ? dropRight : dropLeft, true);
            secView.clearHighlight();
            setEdge(0);
            hover(right ? -2 : -1, right || dragSrc != SRC_DRAWER ? null : this::switchDragToHome, 450);
            return;
        }
        // 2) barra delle sezioni: lasciando l'app su un'icona la si sposta in quella sezione
        int tab = secView.tabAt(lastRawX, lastRawY);
        if (tab != -2) {
            secView.mark(null, 0);
            setEdge(0);
            if (tab >= 0) {
                secView.highlightTab(tab);
                if (tab < sections.list.size()) {
                    tKind = T_DTAB;
                    tSec = tab;
                }
                final int page = tab;
                hover(1000 + tab, () -> secView.snapTo(page), 700);
            } else {
                secView.highlightTab(-1);
                clearHover();
            }
            return;
        }
        secView.highlightTab(-1);
        clearHover();
        setEdge(edgeDirAt());
        // 3) la pagina della sezione: sopra un'app = contenitore; di lato = posizione (ordine libero)
        int sec = secView.currentSection();
        if (sec < 0) {
            secView.mark(null, 0);
            return;
        }
        tSec = sec;
        AppSections.Hit h = secView.hit(lastRawX, lastRawY);
        boolean folder = dragSrc == SRC_DFOLDER;
        boolean self = h != null && h.entry != null
                && (folder ? h.entry == dragEntry : dragKey != null && dragKey.equals(h.entry.app));
        if (h != null && h.entry != null && !self) {
            if (h.center && !folder) {
                tKind = T_DMERGE;
                tEntry = h.entry;
                secView.mark(h.cell, 1);
                return;
            }
            // di lato: posizione (in ordine alfabetico si passa all'ordine libero)
            tKind = T_DPOS;
            tEntry = h.entry;
            tAfter = h.after;
            secView.mark(h.cell, h.after ? 3 : 2);
            return;
        }
        secView.mark(null, 0);
        tKind = T_DSEC;
    }

    /** Dal cassetto alla home senza lasciare l'app. */
    private void switchDragToHome() {
        if (!drawerDrag || dragSrc < 0) return;
        drawerDrag = false;
        clearHover();
        secView.clearHighlight();
        hideDrawerNow();
        showDropBar();
        pager.setPivotX(pager.getWidth() / 2f);
        pager.setPivotY(pager.getHeight());
        pager.animate().scaleX(0.88f).scaleY(0.88f).setDuration(Motion.FADE).setInterpolator(Motion.STANDARD).start();
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        updateTarget();
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
        if (drawerDrag) {
            int t = secView.current() + edgeDir;
            if (t >= 0 && t < secView.realPages()) {
                secView.mark(null, 0);
                tKind = T_NONE;
                secView.snapTo(t);
                root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                ui.postDelayed(retarget, 450);
            }
            ui.postDelayed(edgeRun, 900);
            return;
        }
        int target = pager.getCurrent() + edgeDir;
        if (target < 0) return;
        if (target >= pages) {
            boolean lastHasItems = false;
            for (Item o : items) if (o.page == pages - 1 && o != dragItem) lastHasItems = true;
            if (!lastHasItems) return;
            addEmptyPage();
        }
        for (TileGrid g : grids) g.clearPreview();
        tKind = T_NONE;
        pager.snapTo(target);
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        ui.postDelayed(retarget, 450);
        ui.postDelayed(edgeRun, 900);
    }

    /** Dopo il cambio di pagina automatico la destinazione va ricalcolata sulla pagina nuova. */
    private final Runnable retarget = () -> {
        if (dragSrc >= 0 && dragMoved) updateTarget();
    };

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
        ui.removeCallbacks(retarget);
        edgeDir = 0;
        TileGrid.anyDragging = false;
        clearTargets();
        if (dropBar.getVisibility() == View.VISIBLE) {
            dropBar.animate().cancel();
            dropBar.animate().alpha(0f).setDuration(Motion.QUICK).setInterpolator(Motion.STANDARD).withEndAction(() -> {
                if (dragSrc < 0) dropBar.setVisibility(View.GONE);
            }).start();
        }
        pager.animate().scaleX(1f).scaleY(1f).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).start();
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
        boolean wasDrawer = drawerDrag;
        drawerDrag = false;
        Sections.Entry dEntry = dragEntry;
        dragEntry = null;
        clearHover();
        if (secView != null) secView.clearHighlight();
        dragSrc = -1;
        dragItem = null;
        dragKey = null;
        dragSourceView = null;
        if (!commit) return;
        if (!moved) {
            if (!menuShown) showMenuFor(src, it, key);
            return;
        }
        if (wasDrawer && src == SRC_DFOLDER) dropFolderInDrawer(dEntry, kind);
        else if (wasDrawer) dropInDrawer(key, kind);
        else performDrop(src, it, key, kind);
    }

    private void dropFolderInDrawer(Sections.Entry e, int kind) {
        if (e == null) return;
        Sections.Section from = sections.sectionOfEntry(e);
        if (from == null) return;
        switch (kind) {
            case T_BAR:
                if (tZone == 1) copyFolderToHome(e);
                return;
            case T_DTAB:
            case T_DSEC: {
                if (tSec < 0 || tSec >= sections.list.size()) return;
                Sections.Section to = sections.list.get(tSec);
                if (to == from) return;
                sections.moveEntry(e, from, to);
                if (kind == T_DTAB) toast("\"" + e.name + "\" → " + to.name);
                break;
            }
            case T_DPOS:
                if (tEntry == null || tSec < 0 || tSec >= sections.list.size()) return;
                ensureFreeOrder();
                sections.moveEntryNear(e, sections.list.get(tSec), tEntry, tAfter);
                break;
            default:
                return;
        }
        saveSections(secView.current());
    }

    private void dropInDrawer(String key, int kind) {
        AppEntry a = key == null ? null : appsByKey.get(key);
        if (key == null) return;
        switch (kind) {
            case T_BAR:
                if (tZone == 2 && a != null) uninstall(a);
                else if (tZone == 1 && a != null) {
                    closeDrawer();
                    addAppToHome(a, pager.getCurrent(), 0, 0);
                    toast(a.label + T.t(" aggiunta alla home"));
                }
                return;
            case T_DTAB: {
                if (tSec < 0 || tSec >= sections.list.size()) return;
                Sections.Section s = sections.list.get(tSec);
                if (sections.sectionOf(key) == s) return;
                sections.moveTo(key, s, null, false);
                toast((a != null ? a.label : "App") + " → " + s.name);
                break;
            }
            case T_DMERGE:
                if (tEntry == null || !sections.merge(key, tEntry)) return;
                break;
            case T_DPOS:
                if (tEntry == null || tSec < 0) return;
                ensureFreeOrder();
                sections.moveTo(key, sections.list.get(tSec), tEntry, tAfter);
                break;
            case T_DSEC: {
                if (tSec < 0 || tSec >= sections.list.size()) return;
                Sections.Section s = sections.list.get(tSec);
                if (sections.sectionOf(key) == s) return;
                sections.moveTo(key, s, null, false);
                break;
            }
            default:
                return;
        }
        saveSections(secView.current());
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
                if (old < 0 && dk.size() >= dockMax()) {
                    toast(T.t("Il dock contiene al massimo ") + dockMax() + T.n(dockMax(), " app", " app"));
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
            case T_STACK: {
                Item target = tTarget;
                if (target == null || it == null || src != SRC_HOME || !items.contains(target)) return;
                items.remove(it);
                mergeIntoStack(target, it);
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
                    FolderTile.set(f, T.t("Cartella"), apps);
                    items.remove(target);
                    items.add(f);
                    toast(T.t("Cartella creata"));
                }
                if (src == SRC_HOME && it != null) items.remove(it);
                if (src == SRC_DOCK) removeFromDock(key);
                saveLayout();
                buildPages();
                return;
            }
            case T_CELL:
                // posizione non più valida (es. dopo il cambio di pagina): l'elemento resta dov'era
                if (!canPlace(it, page, tCol, tRow, dragW, dragH)) return;
                if (src == SRC_HOME && it != null) {
                    int oldPage = it.page;
                    it.page = page;
                    it.col = tCol;
                    it.row = tRow;
                    saveLayout();
                    if (moveView(it, oldPage)) return;
                } else if (key != null) {
                    Item n = new Item("app", tCol, tRow, 1, 1, 0, page);
                    n.data = key;
                    items.add(n);
                    if (src == SRC_DOCK) removeFromDock(key);
                    saveLayout();
                    View v = page < grids.size() ? createView(n) : null;
                    if (v != null) {
                        grids.get(page).addView(v);
                        popIn(v);
                        return;
                    }
                }
                saveLayout();
                buildPages();
                return;
            default:
                // posizione non valida: finishDrag ha già rimesso l'elemento al suo posto
                break;
        }
    }

    /** Sposta la vista già esistente nella nuova cella (o pagina) senza ricostruire la home. */
    private boolean moveView(Item it, int oldPage) {
        if (it.view == null || oldPage >= grids.size() || it.page >= grids.size()) return false;
        if (oldPage != it.page) {
            ViewGroup parent = (ViewGroup) it.view.getParent();
            if (parent != null) parent.removeView(it.view);
            grids.get(it.page).addView(it.view);
        } else {
            grids.get(it.page).requestLayout();
        }
        popIn(it.view);
        if (it.isSys()) ui.postDelayed(this::updateSysWidgetSizes, 60);
        if (th.glass) ui.postDelayed(() -> refreshTiles(null), 120);
        return true;
    }

    /** Piccolo "assestamento" dell'elemento appena lasciato. */
    private void popIn(View v) {
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(Motion.POP).setInterpolator(Motion.pop()).start();
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
        toast(T.t("Trascina il pallino per ridimensionare · tocca fuori per finire"));
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
            this.allowed = it.isSys() ? null : sizesOf(it);
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
                        // stessa vista, nuova misura: niente ricostruzione (i widget di sistema non lampeggiano)
                        if (it.view != null && it.page < grids.size()) {
                            grids.get(it.page).requestLayout();
                            if (it.isSys()) ui.postDelayed(HomeActivity.this::updateSysWidgetSizes, 60);
                            it.view.invalidate();
                        } else buildPages();
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
        if (key == null || !key.startsWith("sc:")) return new String[]{"", "", "0", ""};
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
            toast(T.t("Scorciatoia non disponibile"));
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
                toast(T.t("È già sulla home"));
                return;
            }
        }
        repinShortcuts(si.getPackage(), serial, si.getId());
        scCache.remove(key);
        Item it = new Item("shortcut", 0, 0, 1, 1, 0, pager.getCurrent());
        it.data = key;
        closeDrawer();
        addItem(it, pager.getCurrent(), 0, 0);
        toast(T.t("Scorciatoia aggiunta alla home"));
    }

    /** Mostra il menu di un'app con le azioni rapide in cima. */
    private Dialog showMenuWithShortcuts(String title, AppEntry a, List<String> labels, List<Runnable> acts) {
        // notifiche dell'app in cima al menu (se l'accesso alle notifiche è attivo)
        if (a != null && DotNotifications.active()) {
            final String pkg = a.component.getPackageName();
            List<android.service.notification.StatusBarNotification> ns = DotNotifications.of(pkg);
            int i = 0;
            for (android.service.notification.StatusBarNotification n : ns) {
                if (i >= 3) break;
                android.os.Bundle ex = n.getNotification().extras;
                CharSequence t = ex.getCharSequence(android.app.Notification.EXTRA_TITLE);
                CharSequence x = ex.getCharSequence(android.app.Notification.EXTRA_TEXT);
                String line = "●  " + (t == null ? a.label : t) + (x == null ? "" : ": " + x);
                if (line.length() > 90) line = line.substring(0, 88) + "…";
                labels.add(i, line);
                acts.add(i, () -> {
                    try {
                        if (n.getNotification().contentIntent != null) n.getNotification().contentIntent.send();
                        else launch(a, null);
                    } catch (Exception e) {
                        launch(a, null);
                    }
                });
                i++;
            }
            if (!ns.isEmpty()) {
                labels.add(i, T.t("Cancella le notifiche (") + ns.size() + ")");
                acts.add(i, () -> DotNotifications.dismissAll(pkg));
            }
        }
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
                    toast(T.t("Azione non disponibile"));
                }
            } else {
                acts.get(w - n).run();
            }
        }, w -> {
            if (w < n) pinShortcutToHome(scs.get(w));
        });
        if (n > 0 && !prefs.getBoolean("scHint", false)) {
            prefs.edit().putBoolean("scHint", true).apply();
            toast(T.t("Tieni premuta un'azione per metterla sulla home"));
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
        if (manual) toast(T.t("Controllo gli aggiornamenti…"));
        bg(netExec, () -> {
            try {
                JSONObject j = new JSONObject(http("https://api.github.com/repos/Rex11107/dot-launcher/releases/latest"));
                String tag = j.optString("tag_name", "");
                long n = Long.parseLong(tag.substring(tag.lastIndexOf('.') + 1));
                if (n > versionCode()) {
                    if (!manual && tag.equals(prefs.getString("updSeen", ""))) return;
                    prefs.edit().putString("updSeen", tag).apply();
                    ui.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Sheet.confirm(this, th, T.t("Aggiornamento"),
                                T.t("È pronta la versione ") + tag.replace("v", "") + T.t(" (hai la ") + versionName()
                                        + T.t("). Si installa sopra quella attuale: home e impostazioni restano."),
                                T.t("Installa l'aggiornamento"), this::startUpdate);
                    });
                } else if (manual) {
                    ui.post(() -> toast(T.t("Hai già l'ultima versione (") + versionName() + ")"));
                }
            } catch (Exception e) {
                if (manual) ui.post(() -> toast(T.t("Impossibile controllare gli aggiornamenti")));
            }
        });
    }

    /** Scarica e installa la nuova versione senza passare dal browser. */
    private void startUpdate() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            Sheet.confirm(this, th, T.t("Consenti gli aggiornamenti"),
                    T.t("Per installare la nuova versione da qui, Android deve permettere a Dot Launcher di installare app. "
                            + "Attiva \"Consenti da questa fonte\", poi torna qui e tocca di nuovo Installa l'aggiornamento."),
                    T.t("Apri le impostazioni"), () -> safeStart(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()))));
            return;
        }
        final TextView[] tv = new TextView[1];
        final Dialog d = Sheet.progress(this, th, T.t("Aggiornamento"), tv);
        tv[0].setText(T.t("Scarico…"));
        bg(netExec, () -> {
            try {
                java.io.File f = Updater.download(getApplicationContext(), UPDATE_URL, pct -> ui.post(() ->
                        tv[0].setText(pct < 0 ? T.t("Scarico…") : T.t("Scarico… ") + pct + "%")));
                ui.post(() -> tv[0].setText(T.t("Apro l'installazione…")));
                Updater.install(getApplicationContext(), f);
                ui.postDelayed(() -> {
                    try {
                        d.dismiss();
                    } catch (Exception ignored) {
                    }
                }, 800);
            } catch (Exception e) {
                ui.post(() -> {
                    if (destroyed) return;
                    try {
                        d.dismiss();
                    } catch (Exception ignored) {
                    }
                    Sheet.confirm(this, th, T.t("Aggiornamento non riuscito"),
                            T.t("Non sono riuscito a scaricare o installare la nuova versione. Puoi scaricarla dal browser."),
                            T.t("Scarica dal browser"), () -> safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_URL))));
                });
            }
        });
    }

    // ---------- impostazioni (pagina a schermo intero) ----------

    private SettingsScreen settings;
    private boolean settingsOpen;

    private void showSettings() {
        if (settings == null) {
            settings = new SettingsScreen(this, th, this::closeSettings);
            settings.setVisibility(View.GONE);
            root.addView(settings, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            root.requestApplyInsets();
        }
        fillSettings();
        if (settingsOpen) return;
        settingsOpen = true;
        settings.bringToFront();
        settings.animate().cancel();
        if (settings.getVisibility() != View.VISIBLE) {
            menuFrom(settings, 0, px(40));
            settings.setVisibility(View.VISIBLE);
        }
        menuIn(settings).start();
        recedeHome(0.95f, 0);
    }

    private void closeSettings() {
        if (settings == null || !settingsOpen) return;
        settingsOpen = false;
        settings.animate().cancel();
        menuOut(settings, 0, px(40))
                .withEndAction(() -> {
                    if (!settingsOpen) settings.setVisibility(View.GONE);
                }).start();
        if (!drawerOpen && !panelOpen) restoreHome(true);
    }

    /** Ricostruisce le voci (dopo una modifica che non riavvia la home). */
    private void refillSettings() {
        if (settings == null || !settingsOpen) return;
        int y = settings.scrollY();
        fillSettings();
        settings.scrollTo(y);
    }

    private void fillSettings() {
        SettingsScreen st = settings;
        st.clear();
        String style = prefs.getString("style", "classic");
        String mode = prefs.getString("mode", "dark");
        String icons = prefs.getString("icons", IconFactory.AUTO);
        String city = prefs.getString("city", "");

        st.section(T.t("Aspetto"));
        String lang = prefs.getString("lang", "auto");
        st.row(SectionIcons.GLOBE, T.t("Lingua"), "it".equals(lang) ? "Italiano" : "en".equals(lang) ? "English"
                : T.t("Automatica (lingua del telefono)"), () -> {
            String[] vals = {"auto", "it", "en"};
            Sheet.list(this, th, T.t("Lingua"), new String[]{T.t("Automatica (lingua del telefono)"), "Italiano", "English"},
                    Arrays.asList(vals).indexOf(lang), w -> {
                        if (vals[w].equals(lang)) return;
                        prefs.edit().putString("lang", vals[w]).commit();
                        RestartActivity.restart(this);
                    });
        });
        st.row(SectionIcons.PALETTE, T.t("Stile"), "nuovo".equals(style) ? T.t("Moderno: numeri sottili, vetro")
                : T.t("Classico: puntini e caratteri a matrice"), () -> choose(T.t("Stile"),
                new String[]{T.t("Classico"), T.t("Moderno")}, new String[]{"classic", "nuovo"}, "style", style));
        st.row(SectionIcons.MOON, T.t("Tema"), "light".equals(mode) ? T.t("Chiaro") : "auto".equals(mode) ? T.t("Automatico (segue il sistema)")
                : "grey".equals(mode) ? T.t("Scuro (grigio)") : T.t("Extra scuro (nero)"), () -> choose(T.t("Tema"),
                new String[]{T.t("Extra scuro (nero)"), T.t("Scuro (grigio)"), T.t("Chiaro"), T.t("Automatico")},
                new String[]{"dark", "grey", "light", "auto"}, "mode", mode));
        st.row(SectionIcons.GRID, T.t("Icone"), IconFactory.INVERSE.equals(icons) ? T.t("Invertite")
                : IconFactory.COLOR.equals(icons) ? T.t("A colori (originali)") : T.t("Monocromatiche"), () -> choose(T.t("Icone"),
                new String[]{T.t("Monocromatiche"), T.t("Invertite"), T.t("A colori")},
                new String[]{IconFactory.AUTO, IconFactory.INVERSE, IconFactory.COLOR}, "icons", icons));
        st.row(SectionIcons.BAG, T.t("Pacchetto di icone"), packLabel(), this::pickIconPack);
        st.row(Draw.PHOTO, T.t("Sfondi"), T.t("Sfondi generati, foto a puntini o a strisce"),
                () -> startActivity(new Intent(this, WallpaperActivity.class)));
        st.toggle(Draw.PHOTO, T.t("Usa lo sfondo del telefono"), T.t("Altrimenti tinta unita del tema"),
                prefs.getBoolean("wall", false), on -> toggle("wall", !on));
        st.toggle(SectionIcons.CHIP, T.t("Punti spenti sui display"), T.t("Si vede la griglia dei puntini spenti"),
                prefs.getBoolean("ghost", true), on -> toggle("ghost", !on));

        st.section(T.t("Animazioni"));
        final String[] pageNames = {T.t("Scorrimento"), T.t("Profondità"), T.t("Cubo"), T.t("Dissolvenza")};
        final String[] pageDesc = {T.t("Le pagine scorrono una accanto all'altra"),
                T.t("La pagina nuova cresce da dietro quella che se ne va"),
                T.t("Le pagine ruotano come le facce di un cubo"),
                T.t("Le pagine restano ferme e si sostituiscono sfumando")};
        st.row(SectionIcons.GRID, T.t("Passaggio tra le pagine"), pageNames[Pager.transition] + " · " + pageDesc[Pager.transition],
                () -> Sheet.list(this, th, T.t("Passaggio tra le pagine"), pageNames, Pager.transition, w -> {
                    prefs.edit().putInt("pageAnim", w).apply();
                    Pager.transition = w;
                    pager.applyTransition();
                    if (secView != null) secView.applyTransition();
                    refillSettings();
                    toast(T.t("Vale per la home e per il cassetto delle app"));
                }));
        final String[] menuNames = {T.t("Scorrimento"), T.t("Zoom"), T.t("Dissolvenza")};
        final String[] menuDesc = {T.t("Cassetto e impostazioni salgono dal basso, la pagina a sinistra arriva di lato"),
                T.t("I pannelli si ingrandiscono dal centro"), T.t("I pannelli compaiono sfumando, senza movimento")};
        st.row(SectionIcons.SLIDERS, T.t("Apertura dei menu"), menuNames[Motion.menu] + " · " + menuDesc[Motion.menu],
                () -> Sheet.list(this, th, T.t("Apertura dei menu"), menuNames, Motion.menu, w -> {
                    prefs.edit().putInt("menuAnim", w).apply();
                    Motion.menu = w;
                    // piccola anteprima: le impostazioni si richiudono e si riaprono con il nuovo stile
                    closeSettings();
                    ui.postDelayed(() -> {
                        if (!destroyed) showSettings();
                    }, Motion.EXIT + 220);
                }));
        final String[] speeds = {T.t("Lenta"), T.t("Normale"), T.t("Rapida")};
        final int speed = Math.max(0, Math.min(2, prefs.getInt("animSpeed", 1)));
        st.row(Draw.BOLT, T.t("Velocità delle animazioni"), speeds[speed],
                () -> Sheet.list(this, th, T.t("Velocità delle animazioni"), speeds, speed, w -> {
                    prefs.edit().putInt("animSpeed", w).apply();
                    Motion.setSpeed(w);
                    refillSettings();
                }));
        st.toggle(SectionIcons.WAVE, T.t("Animazioni a punti"), T.t("Cifre che si accendono, onda, ricarica"),
                prefs.getBoolean("anim", true), on -> toggle("anim", !on));
        if (!Motion.systemOn()) {
            st.warn(SectionIcons.WARN, T.t("Animazioni di sistema spente"),
                    T.t("Android ha la scala animazione a zero: riattivala nelle Opzioni sviluppatore"),
                    () -> safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        }

        st.section(T.t("Personalizzazione"));
        final int accC = prefs.getInt("accentC", 0);
        st.row(SectionIcons.PALETTE, T.t("Colore d'accento"), accentName(accC), this::chooseAccent);
        final String[] shapes = {T.t("Cerchio"), "Squircle", T.t("Quadrato arrotondato"), T.t("Quadrato")};
        st.row(SectionIcons.SHAPES, T.t("Forma delle icone"), shapes[IconFactory.shape], () ->
                Sheet.list(this, th, T.t("Forma delle icone"), shapes, IconFactory.shape, w -> {
                    prefs.edit().putInt("iconShape", w).apply();
                    recreate();
                }));
        final String[] scales = {T.t("Piccole"), T.t("Normali"), T.t("Grandi"), T.t("Molto grandi")};
        final float[] scaleV = {0.85f, 1f, 1.12f, 1.22f};
        int sIdx = 1;
        for (int i = 0; i < scaleV.length; i++) if (Math.abs(scaleV[i] - AppTile.SCALE) < 0.02f) sIdx = i;
        final int curScale = sIdx;
        st.row(SectionIcons.GRID, T.t("Dimensione delle icone"), scales[curScale], () ->
                Sheet.list(this, th, T.t("Dimensione delle icone"), scales, curScale, w -> {
                    prefs.edit().putFloat("iconScale", scaleV[w]).apply();
                    recreate();
                }));
        final String[] rowsL = {T.t("Automatiche"), T.t("4 righe"), T.t("5 righe"), T.t("6 righe"), T.t("7 righe"), T.t("8 righe")};
        final int rowsCur = TileGrid.ROWS == 0 ? 0 : Math.max(0, Math.min(5, TileGrid.ROWS - 3));
        st.row(SectionIcons.CHART, T.t("Righe della home"), rowsL[rowsCur] + T.t(" · celle quadrate, centrate se servono"), () ->
                Sheet.list(this, th, T.t("Righe della home"), rowsL, rowsCur, w -> {
                    prefs.edit().putInt("rows", w == 0 ? 0 : w + 3).apply();
                    recreate();
                }));
        final String[] dockL = {T.t("Nascosto"), "3 app", "4 app", "5 app", "6 app"};
        final int dockCur = dockMax() == 0 ? 0 : Math.max(1, Math.min(4, dockMax() - 2));
        st.row(SectionIcons.DOCK, T.t("Dock"), dockL[dockCur], () -> Sheet.list(this, th, T.t("App nel dock"), dockL, dockCur, w -> {
            prefs.edit().putInt("dockMax", w == 0 ? 0 : w + 2).apply();
            recreate();
        }));
        st.toggle(SectionIcons.DOCK, T.t("Sfondo del dock"), T.t("Una pillola dietro le app del dock"),
                prefs.getBoolean("dockBg", false), on -> toggle("dockBg", !on));
        String font = prefs.getString("font", "");
        st.row(SectionIcons.BOOK, T.t("Carattere"), font.isEmpty() ? T.t("Predefinito (Geist, Doto per le etichette a puntini)")
                : T.t("Personalizzato"), () -> Sheet.list(this, th, T.t("Carattere"),
                new String[]{T.t("Predefinito"), T.t("Scegli un file .ttf o .otf…")}, font.isEmpty() ? 0 : -1, w -> {
                    if (w == 1) pickFont();
                    else if (!font.isEmpty()) {
                        //noinspection ResultOfMethodCallIgnored
                        new java.io.File(new java.io.File(getFilesDir(), "fonts"), font).delete();
                        prefs.edit().remove("font").apply();
                        recreate();
                    }
                }));
        st.toggle(SectionIcons.PHONE, T.t("Nascondi la barra di stato"), T.t("Riappare scorrendo dal bordo in alto"),
                prefs.getBoolean("hideStatus", false), on -> toggle("hideStatus", !on));
        st.toggle(SectionIcons.LOCK, T.t("Blocca la home"), T.t("Niente spostamenti o rimozioni per errore"),
                locked(), this::setLocked);

        st.section("Home");
        st.row(SectionIcons.GRID, T.t("Griglia"), TileGrid.COLS + T.t(" colonne"), () -> Sheet.list(this, th, T.t("Griglia della home"),
                new String[]{T.t("4 colonne"), T.t("5 colonne")}, TileGrid.COLS == 5 ? 1 : 0, w -> setColumns(w == 1 ? 5 : 4)));
        st.toggle(SectionIcons.NEWS, T.t("Nomi delle app"), T.t("Mostra il nome sotto le icone della home"),
                prefs.getBoolean("labels", false), on -> toggle("labels", !on));
        st.toggle(SettingsScreen.CLOCK, T.t("Orologio a 24 ore"), h24 ? "14:30" : "2:30",
                h24, on -> toggle("h24", !on));
        st.toggle(SectionIcons.PANEL, T.t("Pagina a sinistra"), T.t("IA, notizie, calendario, attività e note"),
                prefs.getBoolean("panel", true), on -> toggle("panel", !on));
        st.row(SectionIcons.WIDGETS, T.t("Aggiungi un widget Dot"), Widgets.TYPES.length + T.t(" widget con anteprima"), () -> {
            closeSettings();
            pickNothingWidget(pager.getCurrent(), 0, 0);
        });
        st.row(SectionIcons.PHONE, T.t("Aggiungi un widget di sistema"), T.t("Widget delle app installate"), () -> {
            closeSettings();
            pickSysWidget(pager.getCurrent(), 0, 0);
        });

        st.section(T.t("Cassetto delle app"));
        final boolean free = drawerFree();
        st.row(SectionIcons.CHART, T.t("Ordine delle app"), free ? T.t("Libero: le disponi trascinandole") : T.t("Alfabetico"), () ->
                Sheet.list(this, th, T.t("Ordine delle app"), new String[]{T.t("Alfabetico"), T.t("Libero")}, free ? 1 : 0, w -> {
                    boolean toFree = w == 1;
                    if (toFree == free) return;
                    if (toFree) {
                        sections.sortAll(this);
                        sections.save();
                    }
                    prefs.edit().putString("drawerOrder", toFree ? "free" : "alpha").apply();
                    refreshDrawer();
                    refillSettings();
                }));
        st.toggle(SectionIcons.GRID, T.t("Sezione \"Tutte le app\""), T.t("In fondo alle sezioni, in ordine alfabetico"),
                prefs.getBoolean("drawerAll", true), on -> {
                    prefs.edit().putBoolean("drawerAll", on).apply();
                    refreshDrawer();
                });
        final int nHidden = hidden().size();
        st.row(SectionIcons.LOCK, T.t("App nascoste"), nHidden == 0 ? T.t("Nessuna") : nHidden + T.n(nHidden, " app nascosta", " app nascoste"),
                () -> withAuth(this::manageHidden));
        st.toggle(SectionIcons.LOCK, T.t("Proteggi le app nascoste"), T.t("Chiedi impronta, volto o PIN per vederle"),
                prefs.getBoolean("hiddenLock", false), on -> {
                    if (!on) {
                        // per spegnerla bisogna sbloccare: altrimenti chiunque potrebbe toglierla
                        withAuth(() -> {
                            prefs.edit().putBoolean("hiddenLock", false).apply();
                            refillSettings();
                        });
                        refillSettings();
                        return;
                    }
                    android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                    if (km == null || !km.isDeviceSecure()) {
                        toast(T.t("Imposta prima un blocco schermo (PIN, sequenza o password)"));
                        refillSettings();
                        return;
                    }
                    prefs.edit().putBoolean("hiddenLock", true).apply();
                    authUntil = System.currentTimeMillis() + 60000;
                    refillSettings();
                });

        st.section(T.t("Notifiche e musica"));
        st.row(Draw.BELL, T.t("Accesso alle notifiche"), DotNotifications.active()
                ? T.t("Attivo: pallini, anteprime e musica") : T.t("Spento: tocca per attivarlo"), this::askNotificationAccess);
        st.toggle(Draw.BELL, T.t("Pallini di notifica"), T.t("Un punto sulle icone delle app con notifiche"),
                prefs.getBoolean("badges", true), on -> {
                    prefs.edit().putBoolean("badges", on).apply();
                    onNotificationsChanged();
                });

        wellbeingSettings(st);

        st.section(T.t("Gesti"));
        for (String[] g : GESTURES) {
            final String key = g[0], name = g[1];
            st.row(gestureIcon(key), name, actionLabel(gestureAction(key)), () -> chooseGesture(key, name));
        }
        st.row(SectionIcons.LOCK, T.t("Servizio per i gesti di sistema"), DotAccessibility.active()
                        ? T.t("Attivo: blocco schermo, screenshot, recenti") : T.t("Spento: serve per bloccare lo schermo e altro"),
                () -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        st.section(T.t("Ricerca"));
        st.row(SectionIcons.GLOBE, T.t("Motore di ricerca web"), UniversalSearch.engineName(searchEngine()), () -> {
            String[] names = new String[UniversalSearch.ENGINES.length];
            int sel = 0;
            for (int i = 0; i < names.length; i++) {
                names[i] = UniversalSearch.ENGINES[i][1];
                if (UniversalSearch.ENGINES[i][0].equals(searchEngine())) sel = i;
            }
            Sheet.list(this, th, T.t("Motore di ricerca"), names, sel, w -> {
                prefs.edit().putString("engine", UniversalSearch.ENGINES[w][0]).apply();
                refillSettings();
            });
        });
        st.row(SectionIcons.PEOPLE, T.t("Contatti nella ricerca"),
                checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                        ? T.t("Attivi") : T.t("Tocca per consentire l'accesso"), this::requestContacts);

        st.section(T.t("Meteo e sensori"));
        st.row(Draw.SUN, T.t("Città per il meteo"), city.isEmpty() ? T.t("Posizione automatica") : city, this::askCity);
        st.row(Draw.STEPS, T.t("Obiettivo di passi"), State.stepGoal + T.t(" passi al giorno"), this::editStepGoal);

        st.section("Backup");
        st.row(SectionIcons.DOWNLOAD, T.t("Esporta la configurazione"), T.t("Home, cassetto, impostazioni (senza chiavi API)"),
                this::exportBackup);
        st.row(Draw.CLOUD, T.t("Importa una configurazione"), T.t("Da un file esportato in precedenza"), this::importBackup);

        st.section(T.t("Informazioni"));
        st.row(SectionIcons.BOOK, T.t("Guida"), T.t("Dove trovare ogni funzione, con scorciatoie"), this::showGuide);
        st.row(SectionIcons.CHIP, T.t("Versione ") + versionName(), T.t("Tocca per cercare aggiornamenti"), () -> checkUpdate(true));
        st.row(SectionIcons.HOME, T.t("Launcher predefinito"), T.t("Scegli Dot Launcher come schermata Home"),
                this::requestDefaultLauncher);
        st.row(SectionIcons.STAR, T.t("Configurazione guidata"), T.t("Rivedi lingua, stile, icone e permessi"), () -> {
            closeSettings();
            prefs.edit().putBoolean("onboarded", false).apply();
            showOnboarding(0);
        });
        int nCrash = CrashLog.count(CrashLog.read(this));
        st.row(SectionIcons.MORE, T.t("Segnala un problema"), nCrash == 0 ? T.t("Nessun errore registrato")
                : nCrash + T.n(nCrash, " errore registrato", " errori registrati"), this::reportProblem);
        st.row(SectionIcons.CODE, T.t("Codice sorgente"), "github.com/Rex11107/dot-launcher · GPL-3.0",
                () -> safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Rex11107/dot-launcher"))));
        st.note(T.t("Caratteri: Geist, Space Mono e Doto, con licenza SIL Open Font License. Meteo: Open-Meteo. ")
                + T.t("Il launcher non ha server: nessun dato viene raccolto."));
    }

    static final int[] ACCENTS = {0, 0xFFD71921, 0xFFFF6A13, 0xFFF5A623, 0xFF2EBD59, 0xFF14B8A6, 0xFF2F6BFF,
            0xFF8B5CF6, 0xFFEC4899, 0xFF9CA3AF};
    static final String[] ACCENT_NAMES = {T.t("Predefinito"), T.t("Rosso Nothing"), T.t("Arancio"), T.t("Ambra"), T.t("Verde"), T.t("Acqua"), T.t("Blu"),
            T.t("Viola"), T.t("Rosa"), T.t("Grigio")};

    private String accentName(int c) {
        for (int i = 0; i < ACCENTS.length; i++) if (ACCENTS[i] == c) return ACCENT_NAMES[i];
        return T.t("Personalizzato");
    }

    private void chooseAccent() {
        int cur = prefs.getInt("accentC", 0);
        Bitmap[] dots = new Bitmap[ACCENTS.length];
        int sel = -1;
        for (int i = 0; i < ACCENTS.length; i++) {
            int c = ACCENTS[i] == 0 ? (th.wall && th.nuovo ? th.accent : Theme.RED) : ACCENTS[i];
            Bitmap b = Bitmap.createBitmap(px(28), px(28), Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(c);
            new Canvas(b).drawCircle(px(14), px(14), px(12), p);
            dots[i] = b;
            if (ACCENTS[i] == cur) sel = i;
        }
        String[] names = ACCENT_NAMES.clone();
        names[0] = T.t("Predefinito (rosso, o dallo sfondo nello stile Moderno)");
        final int s0 = sel;
        Sheet.list(this, th, T.t("Colore d'accento"), names, dots, s0, w -> {
            if (ACCENTS[w] == 0) prefs.edit().remove("accentC").apply();
            else prefs.edit().putInt("accentC", ACCENTS[w]).apply();
            recreate();
        }, null);
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
        if (pkg.isEmpty()) return T.t("nessuno");
        String name = IconPacks.installed(this).get(pkg);
        return name == null ? T.t("non più installato") : name;
    }

    private void pickIconPack() {
        final Map<String, String> packs = IconPacks.installed(this);
        final List<String> pkgs = new ArrayList<>(packs.keySet());
        String[] labels = new String[pkgs.size() + 1];
        labels[0] = T.t("Nessuno (conversione automatica)");
        String cur = prefs.getString("iconPack", "");
        int sel = 0;
        for (int i = 0; i < pkgs.size(); i++) {
            labels[i + 1] = packs.get(pkgs.get(i));
            if (pkgs.get(i).equals(cur)) sel = i + 1;
        }
        if (pkgs.isEmpty()) toast(T.t("Nessun pacchetto di icone installato"));
        Sheet.list(this, th, T.t("Pacchetto di icone"), labels, sel, w -> {
            String v = w == 0 ? "" : pkgs.get(w - 1);
            if (v.equals(cur)) return;
            if (!v.isEmpty() && IconPacks.load(this, v) == null) {
                toast(T.t("Questo pacchetto non è leggibile"));
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
            toast(T.t("Impossibile aprire il selettore di file"));
        }
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast(T.t("Impossibile aprire il selettore di file"));
        }
    }

    // ---------- sblocco con impronta o PIN ----------

    private long authUntil;
    private Runnable pendingAuth;

    /** Esegue {@code ok} solo dopo lo sblocco (se la protezione è attiva). Vale un minuto. */
    private void withAuth(Runnable ok) {
        if (!prefs.getBoolean("hiddenLock", false) || System.currentTimeMillis() < authUntil) {
            ok.run();
            return;
        }
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            ok.run(); // nessun blocco schermo impostato: non c'è niente con cui verificare
            return;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                android.hardware.biometrics.BiometricPrompt.Builder b = new android.hardware.biometrics.BiometricPrompt.Builder(this)
                        .setTitle(T.t("App nascoste"))
                        .setSubtitle(T.t("Sblocca per vederle"));
                if (Build.VERSION.SDK_INT >= 30) {
                    b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                } else if (Build.VERSION.SDK_INT >= 29) {
                    b.setDeviceCredentialAllowed(true);
                } else {
                    b.setNegativeButton(T.t("Usa il PIN"), getMainExecutor(), (d, w) -> confirmCredential(ok));
                }
                b.build().authenticate(new android.os.CancellationSignal(), getMainExecutor(),
                        new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                                authUntil = System.currentTimeMillis() + 60000;
                                ok.run();
                            }

                            @Override
                            public void onAuthenticationError(int code, CharSequence msg) {
                                // annullato o troppi tentativi: non si apre nulla
                            }
                        });
                return;
            } catch (Exception ignored) {
                // alcuni telefoni non hanno il riconoscimento biometrico: si passa al PIN
            }
        }
        confirmCredential(ok);
    }

    @SuppressWarnings("deprecation")
    private void confirmCredential(Runnable ok) {
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent(T.t("App nascoste"), T.t("Sblocca per vederle"));
        if (i == null) {
            ok.run();
            return;
        }
        pendingAuth = ok;
        try {
            startActivityForResult(i, REQ_AUTH);
        } catch (Exception e) {
            pendingAuth = null;
        }
    }

    private void manageHidden() {
        final List<AppEntry> hiddenApps = new ArrayList<>();
        Set<String> h = hidden();
        for (AppEntry a : allApps) if (h.contains(a.key)) hiddenApps.add(a);
        if (hiddenApps.isEmpty()) {
            toast(T.t("Nessuna app nascosta"));
            return;
        }
        Sheet.grid(this, th, T.t("App nascoste"), hiddenApps.size(),
                i -> iconFor(hiddenApps.get(i).key, px(54)), i -> hiddenApps.get(i).label,
                i -> launch(hiddenApps.get(i), null),
                i -> hiddenAppMenu(hiddenApps.get(i)),
                T.t("Tocca per aprire · tieni premuto per mostrarla o disinstallarla"));
    }

    private void hiddenAppMenu(AppEntry a) {
        String[] opts = {T.t("Mostra nel cassetto"), T.t("Info app"), T.t("Disinstalla")};
        Sheet.list(this, th, a.label, opts, -1, w -> {
            switch (w) {
                case 0:
                    Set<String> s = hidden();
                    s.remove(a.key);
                    prefs.edit().putStringSet("hidden", s).apply();
                    refreshDrawer();
                    toast(a.label + T.t(" di nuovo nel cassetto"));
                    if (!s.isEmpty()) manageHidden();
                    break;
                case 1: appInfo(a); break;
                default: uninstall(a); break;
            }
        });
    }

    private void askCity() {
        Sheet.input(this, th, T.t("Città per il meteo"), T.t("es. Milano (vuoto = posizione automatica)"),
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
        if (onboarding != null) onboarding.refresh();
        if (req == REQ_CAL || req == REQ_CONTACTS) {
            if (panel != null) panel.refreshCalendar();
            if (searchMode) grid.setQuery(search.getText().toString());
            return;
        }
        if (req == REQ_STEPS) {
            State.stepPerm = SensorHub.stepPermission(this);
            if (!State.stepPerm) toast(T.t("Senza il permesso \"Attività fisica\" il contapassi non può contare"));
            startSensors();
            refreshTiles("steps");
            return;
        }
        if (req != REQ_LOC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) locateAndFetch();
        else askCity();
    }

    private void locateAndFetch() {
        // il permesso si può revocare in qualsiasi momento dalle impostazioni: senza, si chiede la città
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askCity();
            return;
        }
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
                if (locCancel != null) locCancel.cancel();
                locCancel = new android.os.CancellationSignal();
                lm.getCurrentLocation(provider, locCancel, getMainExecutor(), loc -> {
                    if (destroyed) return;
                    if (loc != null) fetchWeather(loc.getLatitude(), loc.getLongitude(), null);
                    else toast(T.t("Posizione non disponibile: imposta una città"));
                });
            } catch (Exception ignored) {
            }
        } else {
            // Android 8-10: una sola lettura della posizione dalla rete (anche senza servizi Google)
            try {
                String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                @SuppressWarnings("deprecation")
                android.location.LocationListener l = new android.location.LocationListener() {
                    @Override public void onLocationChanged(Location loc) {
                        if (!destroyed && loc != null) fetchWeather(loc.getLatitude(), loc.getLongitude(), null);
                    }
                    @Override public void onStatusChanged(String p, int st, Bundle b) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                };
                lm.requestSingleUpdate(provider, l, Looper.getMainLooper());
            } catch (Exception ignored) {
            }
        }
    }

    private void fetchWeather(double lat, double lon, String knownCity) {
        bg(netExec, () -> {
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max"
                                + "&hourly=temperature_2m,weather_code"
                                + "&forecast_days=6&timezone=auto", lat, lon);
                String json = http(url);
                String city = knownCity;
                if (city == null || city.isEmpty()) city = reverseCity(lat, lon);
                prefs.edit().putString("wjson", json).putString("wcity", city)
                        .putLong("wt", System.currentTimeMillis()).apply();
                // i dati condivisi dai widget si aggiornano sul thread dell'interfaccia, mai durante un disegno
                final String fc = city;
                ui.post(() -> {
                    State.parseWeather(json, fc);
                    refreshTiles(null);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private String reverseCity(double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return "";
            @SuppressWarnings("deprecation")
            List<Address> res = new Geocoder(this, T.loc()).getFromLocation(lat, lon, 1);
            if (res != null && !res.isEmpty() && res.get(0).getLocality() != null) return res.get(0).getLocality();
        } catch (Exception ignored) {
        }
        return "";
    }

    private void geocode(String city) {
        bg(netExec, () -> {
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
                ui.post(() -> toast(T.t("Città non trovata")));
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

    @Override
    public void startSafe(Intent i) {
        safeStart(i);
    }

    @Override
    public void requestContacts() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
    }

    @Override
    public void requestCalendar() {
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL);
    }

    @Override
    public String searchEngine() {
        return prefs.getString("engine", "ddg");
    }

    private void safeStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast(T.t("Nessuna app disponibile"));
        }
    }

    public void toast(String s) {
        Toast.makeText(this, T.t(s), Toast.LENGTH_SHORT).show();
    }
}
