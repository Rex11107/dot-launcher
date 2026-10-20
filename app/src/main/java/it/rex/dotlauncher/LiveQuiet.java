package it.rex.dotlauncher;

import android.os.SystemClock;

/**
 * Gli sfondi animati girano nello stesso processo del launcher: mentre l'utente tocca o scorre la home,
 * il launcher li mette in pausa per qualche istante, così la scheda grafica resta tutta per le animazioni
 * delle pagine e non si sentono scatti. Lo sfondo riparte da solo appena il dito si ferma.
 */
final class LiveQuiet {
    private static volatile long until;
    private static volatile boolean covered;

    private LiveQuiet() {
    }

    /** Pausa breve: si rinnova a ogni tocco o passo di scorrimento. */
    static void hush() {
        until = SystemClock.uptimeMillis() + 300;
    }

    /**
     * Cassetto, pagina a sinistra o impostazioni aperti: lo sfondo è quasi tutto coperto da un pannello
     * semitrasparente. Se continuasse a muoversi, il telefono dovrebbe ricomporre lo schermo intero a ogni
     * fotogramma: resta fermo finché il pannello non si chiude.
     */
    static void setCovered(boolean c) {
        covered = c;
    }

    /** Millisecondi di pausa che restano (0 se lo sfondo può disegnare). */
    static long remaining() {
        if (covered) return 250; // si ricontrolla ogni tanto, costa niente
        return Math.max(0, until - SystemClock.uptimeMillis());
    }
}
