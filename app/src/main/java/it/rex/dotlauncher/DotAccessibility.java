package it.rex.dotlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servizio di accessibilità: serve per le azioni dei gesti che un launcher non può fare da solo
 * (bloccare lo schermo, screenshot, app recenti, menu di spegnimento) e, solo se l'utente attiva il
 * "blocco rigido", per riportare alla home quando si apre un'app in pausa (focus o limite raggiunto).
 * Legge solo il nome del pacchetto dell'app in primo piano, mai il contenuto dello schermo.
 * Se l'utente accende la barra del volume di Dot, riceve anche i tasti (solo quelli del volume vengono usati).
 */
public class DotAccessibility extends AccessibilityService {
    private static DotAccessibility instance;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    /** Cache del controllo dei limiti: pacchetto -> {istante del controllo, superato 0/1}. */
    private final Map<String, long[]> limitCache = new HashMap<>();
    private String lastPkg = "";
    private long lastBlock;
    private VolumePanel volume;
    private CameraRing ring;

    /** Segreto casuale del processo: la home accetta "blocked" solo da qui, non da altre app. */
    static final long TOKEN = new java.security.SecureRandom().nextLong();

    static boolean active() {
        return instance != null;
    }

    /** Esegue un'azione globale; false se il servizio non è attivo o l'azione non è disponibile. */
    static boolean perform(int action) {
        DotAccessibility s = instance;
        return s != null && s.performGlobalAction(action);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        applyKeyFilter();
        applyRing();
    }

    /** Anello della fotocamera: acceso o spento secondo le impostazioni. */
    private void applyRing() {
        boolean on = CameraRing.enabled(getSharedPreferences("dot", Context.MODE_PRIVATE));
        if (on && ring == null) {
            ring = new CameraRing(this);
            ring.start();
        } else if (!on && ring != null) {
            ring.stop();
            ring = null;
        }
    }

    /** Da chiamare quando si accende o spegne l'anello della fotocamera. */
    static void ringChanged() {
        DotAccessibility s = instance;
        if (s != null) s.ui.post(s::applyRing);
    }

    /** I tasti arrivano al servizio solo con la barra del volume accesa: altrimenti nessun filtro. */
    private void applyKeyFilter() {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            boolean on = VolumePanel.enabled(getSharedPreferences("dot", Context.MODE_PRIVATE));
            if (on) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
            if (!on && volume != null) volume.hide();
        } catch (Exception ignored) {
        }
    }

    /** Da chiamare quando si accende o spegne la barra del volume. */
    static void volumeChanged() {
        DotAccessibility s = instance;
        if (s != null) s.ui.post(s::applyKeyFilter);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            if (volume == null) volume = new VolumePanel(this);
            return volume.onKey(event);
        } catch (Exception e) {
            return false; // in caso di problemi decide il sistema
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (volume != null) volume.hide();
        if (ring != null) ring.stop();
        exec.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();
        if (pkg.equals(lastPkg)) return; // stessa app: già controllata
        lastPkg = pkg;
        if (pkg.equals(getPackageName())) return;
        SharedPreferences p = getSharedPreferences("dot", Context.MODE_PRIVATE);
        if (!p.getBoolean("hardBlock", false)) return;
        if (Usage.paused(p, pkg)) {
            block(pkg, "focus");
            return;
        }
        int lim = Usage.limit(p, pkg);
        if (lim <= 0 || Usage.inGrace(pkg)) return;
        long[] c = limitCache.get(pkg);
        long now = System.currentTimeMillis();
        if (c != null && now - c[0] < 60000) {
            if (c[1] == 1) block(pkg, "limit");
            return;
        }
        try {
            exec.execute(() -> {
                boolean over = Usage.granted(this) && Usage.todayMs(this, pkg) >= lim * 60000L;
                ui.post(() -> {
                    limitCache.put(pkg, new long[]{System.currentTimeMillis(), over ? 1 : 0});
                    if (over && pkg.equals(lastPkg)) block(pkg, "limit");
                });
            });
        } catch (Exception ignored) {
        }
    }

    private void block(String pkg, String why) {
        long now = System.currentTimeMillis();
        if (now - lastBlock < 1500) return;
        lastBlock = now;
        lastPkg = "";
        try {
            Intent i = new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("blocked", pkg)
                    .putExtra("why", why)
                    .putExtra("tok", TOKEN);
            startActivity(i);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    /** Dimentica la cache dei limiti (dopo aver cambiato un limite o concesso altro tempo). */
    static void reset() {
        DotAccessibility s = instance;
        if (s == null) return;
        s.ui.post(() -> {
            s.limitCache.clear();
            s.lastPkg = "";
        });
    }

    @Override
    public void onInterrupt() {
    }
}
