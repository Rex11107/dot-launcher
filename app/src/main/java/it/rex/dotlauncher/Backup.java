package it.rex.dotlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Esporta e importa tutta la configurazione del launcher in un file JSON. */
final class Backup {
    // dati temporanei che non ha senso copiare
    private static final List<String> SKIP = Arrays.asList(
            "wjson", "wt", "blurWallId", "askedLoc", "wcity", "layoutBeforeFit", "pagesBeforeFit",
            "stepDay", "stepBase", "stepLast", "stepAcc", "stepToday", "pendPos", "newsCache", "newsTime",
            "pendIcon", "customIcons", "font", "focusUntil", "focusDndSet", "onboardPage", "crashAt", "crashNew");

    // tipi attesi delle impostazioni lette come numeri o sì/no (le altre sono testi o insiemi)
    private static final Set<String> BOOLS = new HashSet<>(Arrays.asList("anim", "askedLoc", "dockInit",
            "drawerAll", "ghost", "labels", "panel", "scHint", "wall", "h24", "badges", "dockBg", "hideStatus", "locked", "focusDnd", "hardBlock", "onboarded", "hiddenLock", "hiddenSearch", "standby", "oneHand",
            "inboxSeen", "workSec", "volPanel", "ringOn", "ringText", "ringTransfer", "ringTimer", "ringCharge"));
    private static final Set<String> INTS = new HashSet<>(Arrays.asList("blurWallId", "cols", "pages", "stepAcc",
            "stepGoal", "stepToday", "wallVer", "dockRows", "accentC", "iconShape", "rows", "dockMax", "pageAnim", "menuAnim", "animSpeed", "openAnim",
            "badgeStyle", "badgeSize", "badgePos", "badgeColor",
            "wallStyle", "wallTab", "liveStyle", "glStyle", "volDots", "ringDx", "ringDy", "ringSize"));
    private static final Set<String> LONGS = new HashSet<>(Arrays.asList("mlat", "mlon", "newsTime", "updCheck", "wt"));
    private static final Set<String> FLOATS = new HashSet<>(Arrays.asList("stepBase", "stepLast", "iconScale", "stride"));

    private static boolean typeOk(String k, String t) {
        if (BOOLS.contains(k)) return "b".equals(t);
        if (INTS.contains(k)) return "i".equals(t);
        if (LONGS.contains(k)) return "l".equals(t);
        if (FLOATS.contains(k)) return "f".equals(t);
        return "s".equals(t) || "set".equals(t);
    }

    /** Dati da non esportare: cache, stato dei sensori e soprattutto le chiavi API. */
    private static boolean skip(String k) {
        return SKIP.contains(k) || k.startsWith("aiKey_");
    }

    /**
     * Layout da condividere con altre persone: solo aspetto e disposizione. Niente posizione, note, attività,
     * notizie, app nascoste o bloccate, limiti, sveglie, chiavi o impostazioni dell'IA (elenco esplicito di ciò che entra).
     */
    static final Set<String> SHARE_KEYS = new HashSet<>(Arrays.asList(
            "style", "mode", "icons", "accentC", "iconShape", "iconScale", "iconPack", "rows", "cols", "pages",
            "dock", "dockMax", "dockRows", "dockBg", "labels", "h24", "hideStatus", "wall", "wallStyle", "wallVer", "ghost",
            "anim", "animSpeed", "pageAnim", "menuAnim", "openAnim", "badges", "badgeStyle", "badgeSize", "badgePos",
            "badgeColor", "drawerOrder", "drawerAll", "sections", "panel", "panelOrder", "panelOff", "engine",
            "swipeActs", "redApps", "origApps", "glyphApps", "layout", "oneHand", "liveStyle", "glStyle"));
    /** Widget con contenuti personali: nel layout condiviso restano, ma vuoti. */
    private static final Set<String> PERSONAL_WIDGETS = new HashSet<>(Arrays.asList("note", "photo", "countdown", "counter"));

    static boolean shareKey(String k) {
        return SHARE_KEYS.contains(k) || k.startsWith("g_");
    }

    /** Toglie dal layout i contenuti dei widget personali (anche dentro le pile). */
    static String cleanLayout(String layout) throws Exception {
        JSONArray a = new JSONArray(layout);
        for (int j = 0; j < a.length(); j++) {
            JSONObject it = a.getJSONObject(j);
            String type = it.optString("t");
            if (PERSONAL_WIDGETS.contains(type)) it.put("d", "");
            else if ("stack".equals(type)) {
                JSONObject st = new JSONObject(it.optString("d", "{}"));
                JSONArray kids = st.optJSONArray("k");
                if (kids != null) {
                    for (int q = 0; q < kids.length(); q++) {
                        JSONObject k = kids.getJSONObject(q);
                        if (PERSONAL_WIDGETS.contains(k.optString("t"))) k.put("d", "");
                    }
                    it.put("d", st.toString());
                }
            }
        }
        return a.toString();
    }

    /** I widget mostrati dalle icone sono legati a questo telefono: nel layout condiviso non vanno. */
    private static String cleanSwipe(String json) throws Exception {
        JSONObject o = new JSONObject(json), out = new JSONObject();
        JSONArray names = o.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String k = names.getString(i), v = o.optString(k);
                if (!v.startsWith("widget:")) out.put(k, v);
            }
        }
        return out.toString();
    }

    /** Scrive il layout da condividere (vedi SHARE_KEYS) nel file indicato. */
    static void exportShare(SharedPreferences prefs, java.io.File f) throws Exception {
        JSONObject data = new JSONObject();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String k = e.getKey();
            if (!shareKey(k)) continue;
            Object v = e.getValue();
            JSONObject o = new JSONObject();
            if (v instanceof String) {
                o.put("t", "s");
                o.put("v", "layout".equals(k) ? cleanLayout((String) v) : "swipeActs".equals(k) ? cleanSwipe((String) v) : v);
            } else if (v instanceof Boolean) { o.put("t", "b"); o.put("v", v); }
            else if (v instanceof Integer) { o.put("t", "i"); o.put("v", v); }
            else if (v instanceof Float) { o.put("t", "f"); o.put("v", (double) (Float) v); }
            else if (v instanceof Set) {
                JSONArray arr = new JSONArray();
                for (Object x : (Set<?>) v) arr.put(String.valueOf(x));
                o.put("t", "set");
                o.put("v", arr);
            } else continue;
            data.put(k, o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "DotLauncher");
        root.put("version", 1);
        root.put("share", true);
        root.put("prefs", data);
        try (OutputStream out = new java.io.FileOutputStream(f)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    static void export(Context c, SharedPreferences prefs, Uri uri) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "DotLauncher");
        root.put("version", 1);
        JSONObject data = new JSONObject();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (skip(e.getKey())) continue;
            Object v = e.getValue();
            JSONObject o = new JSONObject();
            if (v instanceof String) { o.put("t", "s"); o.put("v", v); }
            else if (v instanceof Boolean) { o.put("t", "b"); o.put("v", v); }
            else if (v instanceof Integer) { o.put("t", "i"); o.put("v", v); }
            else if (v instanceof Long) { o.put("t", "l"); o.put("v", v); }
            else if (v instanceof Float) { o.put("t", "f"); o.put("v", (double) (Float) v); }
            else if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object x : (Set<?>) v) a.put(String.valueOf(x));
                o.put("t", "set");
                o.put("v", a);
            } else continue;
            data.put(e.getKey(), o);
        }
        root.put("prefs", data);
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception("stream");
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Restituisce quanti widget di sistema non si potevano ripristinare. */
    static int restore(Context c, SharedPreferences prefs, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("stream");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        JSONObject root = new JSONObject(sb.toString());
        if (!"DotLauncher".equals(root.optString("app"))) throw new Exception("not a backup");
        JSONObject data = root.getJSONObject("prefs");
        // un layout condiviso cambia solo aspetto e disposizione: le altre impostazioni restano come sono
        final boolean share = root.optBoolean("share", false);
        SharedPreferences.Editor ed = prefs.edit();
        if (!share) {
            for (String k : prefs.getAll().keySet()) if (!skip(k)) ed.remove(k);
        }
        int droppedSys = 0;
        JSONArray keys = data.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.getString(i);
                JSONObject o = data.getJSONObject(k);
                if (share && !shareKey(k)) continue; // in un layout condiviso entra solo ciò che è ammesso
                if (!typeOk(k, o.getString("t"))) continue; // tipo inatteso: meglio saltarlo che bloccare la home
                switch (o.getString("t")) {
                    case "s":
                        String v = o.getString("v");
                        if ("layout".equals(k)) {
                            // i widget di sistema hanno identificativi legati all'installazione: vanno riaggiunti
                            JSONArray a = new JSONArray(v);
                            JSONArray keep = new JSONArray();
                            for (int j = 0; j < a.length(); j++) {
                                JSONObject it = a.getJSONObject(j);
                                String type = it.optString("t");
                                if ("sys".equals(type)) {
                                    droppedSys++;
                                    continue;
                                }
                                if ("stack".equals(type)) {
                                    // anche dentro le pile i widget di sistema vanno riaggiunti
                                    JSONObject st = new JSONObject(it.optString("d", "{}"));
                                    JSONArray kids = st.optJSONArray("k"), left = new JSONArray();
                                    if (kids != null) {
                                        for (int q = 0; q < kids.length(); q++) {
                                            if ("sys".equals(kids.getJSONObject(q).optString("t"))) droppedSys++;
                                            else left.put(kids.getJSONObject(q));
                                        }
                                    }
                                    if (left.length() == 0) continue;
                                    st.put("k", left);
                                    st.put("i", 0);
                                    it.put("d", st.toString());
                                }
                                keep.put(it);
                            }
                            v = keep.toString();
                        }
                        ed.putString(k, v);
                        break;
                    case "b": ed.putBoolean(k, o.getBoolean("v")); break;
                    case "i": ed.putInt(k, o.getInt("v")); break;
                    case "l": ed.putLong(k, o.getLong("v")); break;
                    case "f": ed.putFloat(k, (float) o.getDouble("v")); break;
                    case "set":
                        Set<String> s = new HashSet<>();
                        JSONArray a = o.getJSONArray("v");
                        for (int j = 0; j < a.length(); j++) s.add(a.getString(j));
                        ed.putStringSet(k, s);
                        break;
                }
            }
        }
        ed.putBoolean("dockInit", true);
        ed.commit();
        return droppedSys;
    }

    private Backup() {}
}
