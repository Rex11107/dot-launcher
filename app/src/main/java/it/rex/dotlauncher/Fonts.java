package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Caratteri inclusi nell'app (licenza SIL OFL): Geist e Space Mono.
 * Così il launcher non eredita il font di sistema scelto nel tema del telefono.
 */
final class Fonts {
    static Typeface thin, light, regular, medium, mono, dot;
    /** Carattere scelto dall'utente (file .ttf/.otf copiato nell'app), o null. */
    static Typeface custom;
    private static String customName = "";

    static void loadCustom(Context c, String file) {
        if (file == null) file = "";
        if (file.equals(customName)) return;
        customName = file;
        custom = null;
        if (file.isEmpty()) return;
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), "fonts/" + file);
            if (f.exists()) custom = Typeface.createFromFile(f);
        } catch (Exception ignored) {
            custom = null;
        }
    }

    static void init(Context c) {
        if (regular != null) return;
        thin = geist(c, 200);
        light = geist(c, 300);
        regular = geist(c, 400);
        medium = geist(c, 500);
        try {
            mono = Typeface.createFromAsset(c.getAssets(), "fonts/SpaceMono.ttf");
        } catch (Exception e) {
            mono = Typeface.MONOSPACE;
        }
        // Doto: font a puntini su griglia 6x10 (OFL), punti rotondi
        try {
            dot = new Typeface.Builder(c.getAssets(), "fonts/Doto.ttf")
                    .setFontVariationSettings("'wght' 700, 'ROND' 100")
                    .build();
        } catch (Exception e) {
            dot = null;
        }
        if (dot == null) dot = mono;
    }

    private static Typeface geist(Context c, int weight) {
        try {
            Typeface t = new Typeface.Builder(c.getAssets(), "fonts/Geist.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (t != null) return t;
        } catch (Exception ignored) {
        }
        return Typeface.SANS_SERIF;
    }

    private Fonts() {}
}
