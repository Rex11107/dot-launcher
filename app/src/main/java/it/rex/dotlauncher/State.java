package it.rex.dotlauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** Dati condivisi dai widget: batteria, meteo, prossima sveglia. */
final class State {
    static int battery = -1;
    static boolean charging;
    static String nextAlarm = "";

    // bussola e contapassi (SensorHub)
    static boolean hasCompass, headingOk, hasSteps, stepPerm = true;
    static float heading;
    static int compassAccuracy = 3;
    static int steps;
    static int stepGoal = 8000;

    static boolean wOk;
    static int wTemp, wCode, wMax, wMin;
    static String wCity = "";
    static int[] dCode = new int[0];
    static int[] dMax = new int[0];
    static int[] dMin = new int[0];
    static String[] dName = new String[0];

    static void parseWeather(String json, String city) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject j = new JSONObject(json);
            JSONObject cur = j.getJSONObject("current");
            wTemp = (int) Math.round(cur.getDouble("temperature_2m"));
            wCode = cur.getInt("weather_code");
            JSONObject d = j.optJSONObject("daily");
            if (d != null) {
                JSONArray t = d.getJSONArray("time");
                JSONArray c = d.getJSONArray("weather_code");
                JSONArray mx = d.getJSONArray("temperature_2m_max");
                JSONArray mn = d.getJSONArray("temperature_2m_min");
                int n = t.length();
                dCode = new int[n];
                dMax = new int[n];
                dMin = new int[n];
                dName = new String[n];
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.ITALIAN);
                for (int i = 0; i < n; i++) {
                    dCode[i] = c.optInt(i, 3);
                    dMax[i] = (int) Math.round(mx.optDouble(i, 0));
                    dMin[i] = (int) Math.round(mn.optDouble(i, 0));
                    try {
                        dName[i] = out.format(in.parse(t.getString(i))).replace(".", "");
                    } catch (Exception e) {
                        dName[i] = "";
                    }
                }
                if (n > 0) {
                    wMax = dMax[0];
                    wMin = dMin[0];
                }
            }
            wCity = city == null ? "" : city;
            wOk = true;
        } catch (Exception ignored) {
        }
    }

    static String describe(int code) {
        if (code == 0) return "Sereno";
        if (code <= 2) return "Poco nuvoloso";
        if (code == 3) return "Nuvoloso";
        if (code == 45 || code == 48) return "Nebbia";
        if (code >= 51 && code <= 57) return "Pioggerella";
        if (code >= 61 && code <= 67) return "Pioggia";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 80 && code <= 82) return "Rovesci";
        if (code == 85 || code == 86) return "Neve";
        if (code >= 95) return "Temporale";
        return "";
    }

    private State() {}
}
