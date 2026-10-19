package it.rex.dotlauncher;

import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Un elemento della home: widget a puntini, widget di sistema ("sys") o app ("app"). */
final class Item {
    String type;
    String data = "";
    int page, col, row, w = 1, h = 1;
    int tone; // 0 standard, 1 contrasto, 2 accento
    transient View view;

    // ---------- pila di widget ("stack") ----------
    /** Widget della pila (solo per "stack"); la loro posizione e misura seguono la pila. */
    private transient List<Item> kids;
    /** Widget della pila mostrato adesso. */
    transient int current;
    /** La pila a cui appartiene questo widget (null se è sulla home da solo). */
    transient Item parent;

    boolean isStack() {
        return "stack".equals(type);
    }

    List<Item> kids() {
        if (kids == null) {
            kids = new ArrayList<>();
            try {
                JSONObject o = new JSONObject(data == null || data.isEmpty() ? "{}" : data);
                JSONArray a = o.optJSONArray("k");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject k = a.optJSONObject(i);
                        if (k == null || k.optString("t").isEmpty() || "stack".equals(k.optString("t"))) continue;
                        Item it = new Item(k.optString("t"), col, row, w, h, Math.max(0, Math.min(2, k.optInt("o"))), page);
                        it.data = k.optString("d", "");
                        kids.add(it);
                    }
                }
                current = o.optInt("i", 0);
            } catch (Exception ignored) {
            }
        }
        for (Item k : kids) {
            k.parent = this;
            k.page = page;
            k.col = col;
            k.row = row;
            k.w = w;
            k.h = h;
        }
        if (current >= kids.size() || current < 0) current = 0;
        return kids;
    }

    /** Riscrive i widget della pila dentro data. */
    void pack() {
        if (!isStack() || kids == null) return;
        try {
            JSONArray a = new JSONArray();
            for (Item k : kids) {
                JSONObject o = new JSONObject();
                o.put("t", k.type);
                o.put("d", k.data);
                o.put("o", k.tone);
                a.put(o);
            }
            JSONObject o = new JSONObject();
            o.put("k", a);
            o.put("i", current);
            data = o.toString();
        } catch (Exception ignored) {
        }
    }

    /** Questo elemento e, per le pile, i widget che contiene. */
    static List<Item> leaves(List<Item> items) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (it.isStack()) out.addAll(it.kids());
            else out.add(it);
        }
        return out;
    }

    Item(String type, int col, int row, int w, int h, int tone, int page) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.w = w;
        this.h = h;
        this.tone = tone;
        this.page = page;
    }

    boolean isApp() {
        return "app".equals(type);
    }

    boolean isSys() {
        return "sys".equals(type);
    }

    JSONObject toJson() throws Exception {
        pack();
        JSONObject o = new JSONObject();
        o.put("t", type);
        o.put("d", data);
        o.put("p", page);
        o.put("c", col);
        o.put("r", row);
        o.put("w", w);
        o.put("h", h);
        o.put("o", tone);
        return o;
    }

    static List<Item> listFromJson(String s) {
        List<Item> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        JSONArray a;
        try {
            a = new JSONArray(s);
        } catch (Exception e) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            // un elemento rovinato si salta, senza perdere tutta la home
            try {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item(o.getString("t"), Math.max(0, o.optInt("c")), Math.max(0, o.optInt("r")),
                        Math.max(1, o.optInt("w", 1)), Math.max(1, o.optInt("h", 1)),
                        Math.max(0, Math.min(2, o.optInt("o"))), Math.max(0, o.optInt("p")));
                it.data = o.optString("d", "");
                out.add(it);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static String listToJson(List<Item> items) {
        JSONArray a = new JSONArray();
        for (Item it : items) {
            try {
                a.put(it.toJson());
            } catch (Exception ignored) {
            }
        }
        return a.toString();
    }
}
