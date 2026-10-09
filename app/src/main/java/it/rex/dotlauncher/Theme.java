package it.rex.dotlauncher;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;

/** Colori e caratteri del launcher: stile Classico (predefinito) o Nuovo (ispirato a 5.0), scuro o chiaro. */
final class Theme {
    static final int RED = 0xFFD71921;

    final boolean light;
    final boolean nuovo;
    final boolean wall;
    final boolean glass;         // vetro smerigliato vero (sfondo sfocato dentro le tessere)
    final android.graphics.Bitmap glassBmp;
    final boolean darkIcons;     // icone scure nelle barre di sistema
    final boolean dots;          // numeri e titoli a puntini (Classico)
    final boolean upperLabels;   // etichette maiuscole (Classico)
    final int bg, tile, tileAlt, onTile, onTileAlt, sub, accent, stroke, drawerBg, scrim;
    final int iconBg, iconFg, iconBgAlt, iconFgAlt, iconStroke, sheetBg;
    final Typeface numFace, labelFace, bodyFace, titleFace;

    private Theme(Context c, SharedPreferences p) {
        Fonts.init(c);
        String mode = p.getString("mode", "dark");
        boolean sysNight = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        light = "light".equals(mode) || ("auto".equals(mode) && !sysNight);
        nuovo = "nuovo".equals(p.getString("style", "classic"));
        wall = p.getBoolean("wall", false);
        glassBmp = nuovo && wall ? Glass.get(c) : null;
        glass = glassBmp != null;
        dots = !nuovo;
        upperLabels = !nuovo;

        if (!nuovo) {
            if (!light) {
                bg = 0xFF000000; tile = 0xFF1C1C1E; tileAlt = 0xFFF2F2F2;
                onTile = 0xFFFFFFFF; onTileAlt = 0xFF111111; sub = 0xFF8E8E8E;
                drawerBg = 0xFA000000; scrim = 0x33000000; sheetBg = 0xFF161618;
            } else {
                bg = 0xFFE6E6E6; tile = 0xFFFFFFFF; tileAlt = 0xFF111111;
                onTile = 0xFF111111; onTileAlt = 0xFFFFFFFF; sub = 0xFF6B6B6B;
                drawerBg = 0xFAE6E6E6; scrim = 0x14FFFFFF; sheetBg = 0xFFFFFFFF;
            }
            stroke = 0;
            accent = RED;
            iconBg = tile;
            iconFg = onTile;
            numFace = Fonts.light;
            labelFace = Fonts.mono;
            titleFace = Fonts.medium;
        } else {
            if (!light) {
                bg = 0xFF0B0B0C;
                tile = glass ? 0x80202023 : 0xD9232326;
                tileAlt = 0xE6F2F2F2;
                onTile = 0xFFFFFFFF; onTileAlt = 0xFF111111; sub = 0xFFA8A8A8;
                stroke = 0x26FFFFFF; drawerBg = 0xF5101012; scrim = 0x26000000; sheetBg = 0xFF1A1A1C;
                iconBg = 0xF2262628;
            } else {
                bg = 0xFFEDEDED;
                tile = glass ? 0x99FFFFFF : 0xE6FFFFFF;
                tileAlt = 0xE6111111;
                onTile = 0xFF111111; onTileAlt = 0xFFFFFFFF; sub = 0xFF5E5E5E;
                stroke = 0x1A000000; drawerBg = 0xF5F2F2F2; scrim = 0x14FFFFFF; sheetBg = 0xFFF7F7F7;
                iconBg = 0xF7FFFFFF;
            }
            accent = wall ? wallpaperAccent(c) : RED;
            iconFg = blend(onTile, accent, 0.15f);
            numFace = Fonts.thin;
            labelFace = Fonts.medium;
            titleFace = Fonts.medium;
        }
        // bordo leggero dei cerchi: indispensabile nei temi chiari (cerchio bianco su fondo chiaro)
        iconStroke = light ? 0x2E000000 : (nuovo ? 0x26FFFFFF : 0);
        iconBgAlt = 0xFF000000 | tileAlt;
        iconFgAlt = onTileAlt;
        bodyFace = Fonts.regular;
        // le icone della barra di stato seguono il tema; una sfumatura dietro le barre garantisce il contrasto
        darkIcons = light;
    }

    static Theme build(Context c, SharedPreferences p) {
        return new Theme(c, p);
    }

    /** Cambia quando cambia qualcosa che richiede di ridisegnare il launcher. */
    static String signature(SharedPreferences p) {
        return p.getString("style", "classic") + "|" + p.getString("mode", "dark") + "|"
                + p.getString("icons", "auto") + "|" + p.getBoolean("wall", false) + "|"
                + p.getBoolean("labels", false) + "|" + p.getInt("wallVer", 0);
    }

    /** Colore principale dello sfondo di sistema, reso abbastanza vivo da fare da accento. */
    private static int wallpaperAccent(Context c) {
        try {
            WallpaperColors wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return RED;
            int col = wc.getPrimaryColor().toArgb();
            float[] hsv = new float[3];
            Color.colorToHSV(col, hsv);
            if (hsv[1] < 0.15f) return RED; // sfondo quasi grigio: si resta sul rosso
            hsv[1] = Math.max(hsv[1], 0.5f);
            hsv[2] = Math.min(Math.max(hsv[2], 0.6f), 0.92f);
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return RED;
        }
    }

    static int blend(int a, int b, float t) {
        int aa = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(aa, r, g, bl);
    }

    static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(Color.alpha(color) * a) << 24);
    }
}
