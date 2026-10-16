package it.rex.dotlauncher;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordine del cassetto "Più usate": quante volte ogni app è stata aperta negli ultimi 14 giorni,
 * letto dai dati di utilizzo di Android (lo stesso permesso del benessere digitale).
 * I dati restano sul telefono. "Tutte le app" resta sempre in ordine alfabetico.
 */
final class Freq {
    /** L'ordine scelto è "Più usate". */
    static volatile boolean on;
    /** Aperture per pacchetto. */
    private static volatile Map<String, Integer> opens = new HashMap<>();
    private static volatile long computedAt;

    static final long DAYS = 14;
    private static final long REFRESH = 30 * 60000L;

    /** Pacchetto dalla chiave di un'app ("pacchetto/attività#utente"). */
    static String pkg(String key) {
        if (key == null) return "";
        int s = key.indexOf('/');
        return s > 0 ? key.substring(0, s) : key;
    }

    static int score(String key) {
        Integer n = opens.get(pkg(key));
        return n == null ? 0 : n;
    }

    static boolean stale() {
        return System.currentTimeMillis() - computedAt > REFRESH;
    }

    /** Da chiamare fuori dal thread principale. Restituisce true se l'ordine è cambiato. */
    static boolean compute(Context c) {
        long now = System.currentTimeMillis();
        List<Usage.App> list = Usage.compute(c, now - DAYS * 86400000L);
        Map<String, Integer> m = new HashMap<>();
        for (Usage.App a : list) if (a.opens > 0) m.put(a.pkg, a.opens);
        computedAt = now;
        if (m.equals(opens)) return false;
        opens = m;
        return true;
    }

    /** Solo per le prove: imposta i conteggi a mano. */
    static void set(Map<String, Integer> m) {
        opens = new HashMap<>(m);
    }

    private Freq() {}
}
