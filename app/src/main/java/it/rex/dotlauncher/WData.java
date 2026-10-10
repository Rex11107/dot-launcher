package it.rex.dotlauncher;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Impostazioni dei singoli widget, salvate come JSON in Item.data. */
final class WData {
    static String get(Item it, String key, String def) {
        try {
            JSONObject o = new JSONObject(it.data == null || it.data.isEmpty() ? "{}" : it.data);
            return o.optString(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    static long getLong(Item it, String key, long def) {
        try {
            return Long.parseLong(get(it, key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    static void put(Item it, String key, String value) {
        try {
            JSONObject o = new JSONObject(it.data == null || it.data.isEmpty() ? "{}" : it.data);
            o.put(key, value);
            it.data = o.toString();
        } catch (Exception e) {
            try {
                JSONObject o = new JSONObject();
                o.put(key, value);
                it.data = o.toString();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- conto alla rovescia ----------

    /** Giorni di calendario da oggi alla data (negativo se è passata). */
    static int daysTo(long target) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(target);
        for (Calendar c : new Calendar[]{a, b}) {
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }

    // ---------- fusi orari ----------

    static final String[][] CITIES = {
            {"Roma", "Europe/Rome"}, {"Londra", "Europe/London"}, {"Lisbona", "Europe/Lisbon"},
            {"Parigi", "Europe/Paris"}, {"Berlino", "Europe/Berlin"}, {"Madrid", "Europe/Madrid"},
            {"Atene", "Europe/Athens"}, {"Istanbul", "Europe/Istanbul"}, {"Mosca", "Europe/Moscow"},
            {"Reykjavik", "Atlantic/Reykjavik"}, {"Il Cairo", "Africa/Cairo"}, {"Lagos", "Africa/Lagos"},
            {"Johannesburg", "Africa/Johannesburg"}, {"Dubai", "Asia/Dubai"}, {"Nuova Delhi", "Asia/Kolkata"},
            {"Bangkok", "Asia/Bangkok"}, {"Singapore", "Asia/Singapore"}, {"Hong Kong", "Asia/Hong_Kong"},
            {"Pechino", "Asia/Shanghai"}, {"Seul", "Asia/Seoul"}, {"Tokyo", "Asia/Tokyo"},
            {"Sydney", "Australia/Sydney"}, {"Auckland", "Pacific/Auckland"}, {"Honolulu", "Pacific/Honolulu"},
            {"Los Angeles", "America/Los_Angeles"}, {"Denver", "America/Denver"}, {"Chicago", "America/Chicago"},
            {"New York", "America/New_York"}, {"Toronto", "America/Toronto"},
            {"Città del Messico", "America/Mexico_City"}, {"San Paolo", "America/Sao_Paulo"},
            {"Buenos Aires", "America/Argentina/Buenos_Aires"}
    };

    static final String DEFAULT_ZONES = "New York|America/New_York;Londra|Europe/London;Tokyo|Asia/Tokyo";

    /** Elenco delle città scelte: ogni voce è {nome, fuso}. */
    static List<String[]> zones(Item it) {
        String s = get(it, "z", DEFAULT_ZONES);
        List<String[]> out = new ArrayList<>();
        for (String part : s.split(";")) {
            String[] kv = part.split("\\|");
            if (kv.length == 2) out.add(kv);
        }
        return out;
    }

    static void setZones(Item it, List<String[]> z) {
        StringBuilder sb = new StringBuilder();
        for (String[] kv : z) {
            if (sb.length() > 0) sb.append(';');
            sb.append(kv[0]).append('|').append(kv[1]);
        }
        put(it, "z", sb.toString());
    }

    /** Differenza dall'ora locale, es. "+7H", "-5H 30", "ORA LOCALE". */
    static String offsetLabel(String zone) {
        long now = System.currentTimeMillis();
        int diff = TimeZone.getTimeZone(zone).getOffset(now) - TimeZone.getDefault().getOffset(now);
        if (diff == 0) return "ora locale";
        int mins = Math.abs(diff) / 60000;
        String s = (diff > 0 ? "+" : "-") + (mins / 60) + "h";
        if (mins % 60 != 0) s += " " + (mins % 60);
        return s;
    }

    private WData() {}
}
