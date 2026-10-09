package it.rex.dotlauncher;

import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Disegno a tutto schermo: barra di stato e barra di navigazione trasparenti,
 * così sotto si vedono il colore del tema o lo sfondo.
 */
final class SystemBars {

    static void edgeToEdge(Window w, boolean darkIcons) {
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            w.setNavigationBarDividerColor(Color.TRANSPARENT);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            // niente velo grigio automatico dietro i tre tasti
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (darkIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
            }
        }
    }

    /**
     * Restituisce {sinistra, sopra, destra, sotto, gestureSinistra, gestureDestra, gestureSotto, tastiera}.
     * I margini "gesture" sono le zone dove il sistema intercetta indietro/home.
     */
    @SuppressWarnings("deprecation")
    static int[] read(WindowInsets in) {
        int[] r = new int[8];
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets g = in.getInsets(WindowInsets.Type.systemGestures());
            android.graphics.Insets mg = in.getInsets(WindowInsets.Type.mandatorySystemGestures());
            android.graphics.Insets ime = in.getInsets(WindowInsets.Type.ime());
            r[0] = s.left;
            r[1] = s.top;
            r[2] = s.right;
            r[3] = s.bottom;
            r[4] = g.left;
            r[5] = g.right;
            r[6] = Math.max(mg.bottom, s.bottom);
            r[7] = ime.bottom;
        } else {
            r[0] = in.getSystemWindowInsetLeft();
            r[1] = in.getSystemWindowInsetTop();
            r[2] = in.getSystemWindowInsetRight();
            r[3] = in.getStableInsetBottom();
            if (Build.VERSION.SDK_INT >= 29) {
                r[4] = in.getSystemGestureInsets().left;
                r[5] = in.getSystemGestureInsets().right;
                r[6] = Math.max(in.getMandatorySystemGestureInsets().bottom, r[3]);
            } else {
                r[6] = r[3];
            }
            r[7] = Math.max(0, in.getSystemWindowInsetBottom() - r[3]);
        }
        return r;
    }

    private SystemBars() {}
}
