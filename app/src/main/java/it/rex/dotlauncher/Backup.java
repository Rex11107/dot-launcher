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
            "wjson", "wt", "blurWallId", "askedLoc", "widgets", "wcity",
            "stepDay", "stepBase", "stepLast", "stepAcc", "stepToday", "pendPos", "newsCache", "newsTime",
            "pendIcon", "customIcons", "font", "focusUntil", "focusDndSet", "onboardPage", "crashAt", "crashNew");

    // tipi attesi delle impostazioni lette come numeri o sì/no (le altre sono testi o insiemi)
    private static final Set<String> BOOLS = new HashSet<>(Arrays.asList("anim", "askedLoc", "dockInit",
            "drawerAll", "ghost", "labels", "panel", "scHint", "wall", "h24", "badges", "dockBg", "hideStatus", "locked", "focusDnd", "hardBlock", "onboarded", "hiddenLock", "hiddenSearch"));
    private static final Set<String> INTS = new HashSet<>(Arrays.asList("blurWallId", "cols", "pages", "stepAcc",
            "stepGoal", "stepToday", "wallVer", "accentC", "iconShape", "rows", "dockMax", "pageAnim", "menuAnim", "animSpeed", "openAnim",
            "badgeStyle", "badgeSize", "badgePos", "badgeColor"));
    private static final Set<String> LONGS = new HashSet<>(Arrays.asList("mlat", "mlon", "newsTime", "updCheck", "wt"));
    private static final Set<String> FLOATS = new HashSet<>(Arrays.asList("stepBase", "stepLast", "iconScale"));

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
        SharedPreferences.Editor ed = prefs.edit();
        for (String k : prefs.getAll().keySet()) if (!skip(k)) ed.remove(k);
        int droppedSys = 0;
        JSONArray keys = data.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.getString(i);
                JSONObject o = data.getJSONObject(k);
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
