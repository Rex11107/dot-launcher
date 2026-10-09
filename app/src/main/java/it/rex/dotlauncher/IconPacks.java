package it.rex.dotlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pacchetti di icone nel formato standard (ADW / Nova / Lawnchair):
 * il pacchetto contiene appfilter.xml, che associa ogni app a un disegno.
 */
final class IconPacks {
    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
    };

    /** Pacchetti installati: nome pacchetto → nome visibile. */
    static Map<String, String> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> out = new LinkedHashMap<>();
        for (String a : ACTIONS) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(a), PackageManager.GET_META_DATA)) {
                    String pkg = ri.activityInfo.packageName;
                    if (!out.containsKey(pkg)) out.put(pkg, String.valueOf(ri.loadLabel(pm)));
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private final String pkg;
    private final Resources res;
    private final Map<String, String> map = new HashMap<>();
    private final int density;

    private IconPacks(Context c, String pkg) throws Exception {
        this.pkg = pkg;
        this.res = c.getPackageManager().getResourcesForApplication(pkg);
        this.density = c.getResources().getDisplayMetrics().densityDpi;
        parse(c);
    }

    /** Carica un pacchetto; null se non è installato o non è leggibile. */
    static IconPacks load(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            IconPacks p = new IconPacks(c, pkg);
            return p.map.isEmpty() ? null : p;
        } catch (Exception e) {
            return null;
        }
    }

    int size() {
        return map.size();
    }

    private void parse(Context c) throws Exception {
        XmlPullParser xp;
        int id = res.getIdentifier("appfilter", "xml", pkg);
        InputStream in = null;
        if (id != 0) {
            xp = res.getXml(id);
        } else {
            in = c.createPackageContext(pkg, 0).getAssets().open("appfilter.xml");
            XmlPullParserFactory f = XmlPullParserFactory.newInstance();
            xp = f.newPullParser();
            xp.setInput(in, "UTF-8");
        }
        try {
            int ev = xp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "item".equals(xp.getName())) {
                    String comp = xp.getAttributeValue(null, "component");
                    String draw = xp.getAttributeValue(null, "drawable");
                    if (comp != null && draw != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                        String inner = comp.substring(14, comp.length() - 1);
                        ComponentName cn = ComponentName.unflattenFromString(inner);
                        if (cn != null) map.put(cn.flattenToString(), draw);
                    }
                }
                ev = xp.next();
            }
        } finally {
            if (xp instanceof XmlResourceParser) ((XmlResourceParser) xp).close();
            if (in != null) in.close();
        }
    }

    /** Disegno del pacchetto per questa app, oppure null se il pacchetto non la copre. */
    Drawable iconFor(ComponentName cn) {
        String name = map.get(cn.flattenToString());
        if (name == null) return null;
        try {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id == 0) id = res.getIdentifier(name, "mipmap", pkg);
            if (id == 0) return null;
            return res.getDrawableForDensity(id, density, null);
        } catch (Exception e) {
            return null;
        }
    }

    static List<String> actions() {
        List<String> l = new ArrayList<>();
        for (String a : ACTIONS) l.add(a);
        return l;
    }
}
