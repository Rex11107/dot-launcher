package it.rex.dotlauncher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Servizio di accessibilità minimo: serve solo per le azioni dei gesti che un launcher non può fare da solo
 * (bloccare lo schermo, screenshot, app recenti, menu di spegnimento). Non legge lo schermo né gli eventi.
 */
public class DotAccessibility extends AccessibilityService {
    private static DotAccessibility instance;

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
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
