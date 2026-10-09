package it.rex.dotlauncher;

import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Un elemento della home: widget Nothing, widget di sistema ("sys") o app ("app"). */
final class Item {
    String type;
    String data = "";
    int page, col, row, w = 1, h = 1;
    int tone; // 0 standard, 1 contrasto, 2 accento
    transient View view;

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
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item(o.getString("t"), o.optInt("c"), o.optInt("r"),
                        Math.max(1, o.optInt("w", 1)), Math.max(1, o.optInt("h", 1)),
                        o.optInt("o"), o.optInt("p"));
                it.data = o.optString("d", "");
                out.add(it);
            }
        } catch (Exception ignored) {
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
