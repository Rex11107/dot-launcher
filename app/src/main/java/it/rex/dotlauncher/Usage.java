package it.rex.dotlauncher;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tempo di utilizzo, limiti giornalieri e modalità focus.
 * I dati d'uso vengono letti da Android solo quando servono e restano sul telefono.
 */
final class Usage {

    static final class App {
        final String pkg;
        long ms;
        int opens;

        App(String pkg) {
            this.pkg = pkg;
        }
    }

    // ultimo calcolo di oggi (lo usa il widget)
    static volatile List<App> today = new ArrayList<>();
    static volatile long total;
    static volatile int unlocks;
    static volatile long computedAt;
    /** Stato per i widget (aggiornato dalla home). */
    static volatile boolean permitted, focusActive;
    static volatile long focusUntil;
    static volatile Map<String, String> names = new HashMap<>();

    static String name(String pkg) {
        String n = names.get(pkg);
        return n == null ? pkg : n;
    }

    // =====================================================================
    // permesso
    // =====================================================================

    @SuppressWarnings("deprecation")
    static boolean granted(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return c.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================================
    // calcolo
    // =====================================================================

    static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static final Set<String> IGNORED = new HashSet<>();

    static {
        IGNORED.add("android");
        IGNORED.add("com.android.systemui");
        IGNORED.add("com.hihonor.android.launcher");
        IGNORED.add("com.huawei.android.launcher");
    }

    /** Tempo in primo piano per app da {@code from} a adesso, ordinato dal più alto. Da chiamare fuori dal thread UI. */
    static List<App> compute(Context c, long from) {
        List<App> out = new ArrayList<>();
        UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (um == null) return out;
        long now = System.currentTimeMillis();
        UsageEvents ev;
        try {
            ev = um.queryEvents(from, now);
        } catch (Exception e) {
            return out;
        }
        if (ev == null) return out;
        String me = c.getPackageName();
        Map<String, App> apps = new HashMap<>();
        Map<String, Set<String>> open = new HashMap<>();
        Map<String, Long> start = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int screenOn = 0;
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            int t = e.getEventType();
            long ts = e.getTimeStamp();
            String pkg = e.getPackageName();
            if (t == 15) { // SCREEN_INTERACTIVE
                screenOn++;
                continue;
            }
            if (t == 16 || t == 26) { // SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN: si chiude tutto
                for (Map.Entry<String, Long> s : start.entrySet()) app(apps, s.getKey()).ms += Math.max(0, ts - s.getValue());
                start.clear();
                open.clear();
                continue;
            }
            if (pkg == null || pkg.equals(me) || IGNORED.contains(pkg)) continue;
            String cls = e.getClassName() == null ? "" : e.getClassName();
            if (t == 1) { // ACTIVITY_RESUMED
                Set<String> s = open.get(pkg);
                if (s == null) open.put(pkg, s = new HashSet<>());
                if (s.isEmpty()) {
                    start.put(pkg, ts);
                    app(apps, pkg).opens++;
                }
                s.add(cls);
                seen.add(pkg);
            } else if (t == 2 || t == 23) { // ACTIVITY_PAUSED, ACTIVITY_STOPPED
                Set<String> s = open.get(pkg);
                if (s != null && s.remove(cls)) {
                    if (s.isEmpty()) {
                        Long st = start.remove(pkg);
                        if (st != null) app(apps, pkg).ms += Math.max(0, ts - st);
                    }
                } else if (t == 2 && !seen.contains(pkg)) {
                    // era già aperta prima dell'inizio dell'intervallo
                    app(apps, pkg).ms += Math.max(0, ts - from);
                }
                seen.add(pkg);
            }
        }
        // app ancora in primo piano
        for (Map.Entry<String, Long> s : start.entrySet()) app(apps, s.getKey()).ms += Math.max(0, now - s.getValue());
        for (App a : apps.values()) if (a.ms >= 1000) out.add(a);
        Collections.sort(out, (a, b) -> Long.compare(b.ms, a.ms));
        if (from == startOfToday()) unlocks = screenOn;
        return out;
    }

    private static App app(Map<String, App> m, String pkg) {
        App a = m.get(pkg);
        if (a == null) m.put(pkg, a = new App(pkg));
        return a;
    }

    /** Ricalcola i dati di oggi per il widget (fuori dal thread UI). */
    static void refreshToday(Context c) {
        if (!granted(c)) return;
        List<App> l = compute(c, startOfToday());
        long t = 0;
        for (App a : l) t += a.ms;
        today = l;
        total = t;
        computedAt = System.currentTimeMillis();
    }

    /** Minuti di utilizzo di oggi per un pacchetto. */
    static long todayMs(Context c, String pkg) {
        for (App a : compute(c, startOfToday())) if (a.pkg.equals(pkg)) return a.ms;
        return 0;
    }

    static String fmt(long ms) {
        long min = ms / 60000;
        if (min < 1) return ms > 0 ? "<1 min" : "0 min";
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** Forma corta per i widget: "2h14", "45m". */
    static String shortFmt(long ms) {
        long min = ms / 60000;
        if (min < 60) return min + "m";
        long m = min % 60;
        return (min / 60) + "h" + (m < 10 ? "0" : "") + m;
    }

    // =====================================================================
    // limiti, pausa e focus (preferenze)
    // =====================================================================

    /** Limite giornaliero in minuti per pacchetto (0 = nessuno). */
    static int limit(SharedPreferences p, String pkg) {
        try {
            return new JSONObject(p.getString("limits", "{}")).optInt(pkg, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    static void setLimit(SharedPreferences p, String pkg, int minutes) {
        try {
            JSONObject o = new JSONObject(p.getString("limits", "{}"));
            if (minutes <= 0) o.remove(pkg);
            else o.put(pkg, minutes);
            p.edit().putString("limits", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static Set<String> limited(SharedPreferences p) {
        Set<String> out = new HashSet<>();
        try {
            Iterator<String> it = new JSONObject(p.getString("limits", "{}")).keys();
            while (it.hasNext()) out.add(it.next());
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Fino a quando il "permesso" di usare un'app oltre il limite o in focus è valido. */
    static final Map<String, Long> GRACE = new HashMap<>();

    static boolean inGrace(String pkg) {
        Long g = GRACE.get(pkg);
        return g != null && g > System.currentTimeMillis();
    }

    static void grant(String pkg, long minutes) {
        GRACE.put(pkg, System.currentTimeMillis() + minutes * 60000L);
    }

    static Set<String> mindful(SharedPreferences p) {
        return new HashSet<>(p.getStringSet("mindful", new HashSet<>()));
    }

    static Set<String> focusApps(SharedPreferences p) {
        return new HashSet<>(p.getStringSet("focusApps", new HashSet<>()));
    }

    static boolean focusOn(SharedPreferences p) {
        return p.getLong("focusUntil", 0) > System.currentTimeMillis();
    }

    /** L'app è "in pausa" adesso per via del focus? */
    static boolean paused(SharedPreferences p, String pkg) {
        return focusOn(p) && focusApps(p).contains(pkg) && !inGrace(pkg);
    }

    private Usage() {}
}
