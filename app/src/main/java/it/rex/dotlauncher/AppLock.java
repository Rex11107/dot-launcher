package it.rex.dotlauncher;

import java.util.Set;

/**
 * App bloccate: si aprono solo dopo impronta, volto o PIN.
 * Le chiavi sono quelle delle app ("pacchetto/attività", con "#utente" per il profilo di lavoro).
 */
final class AppLock {
    static final String PREF = "lockedApps";

    static boolean locked(Set<String> keys, String key) {
        return keys != null && key != null && keys.contains(key);
    }

    /**
     * Un'app del pacchetto è bloccata: vale per scorciatoie e widget che aprono l'app senza l'icona.
     * Si blocca anche la copia del profilo di lavoro, nel dubbio è meglio chiedere lo sblocco.
     */
    static boolean pkgLocked(Set<String> keys, String pkg) {
        if (keys == null || pkg == null || pkg.isEmpty()) return false;
        for (String k : keys) if (k.startsWith(pkg + "/")) return true;
        return false;
    }

    private AppLock() {}
}
