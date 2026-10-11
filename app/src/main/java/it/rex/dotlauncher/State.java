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
    static boolean hasCompass, hasSteps, stepPerm = true;
    static float heading;
    static int compassAccuracy = 3;
    static int steps;
    static boolean torchOn;

    // alba, tramonto, UV e previsioni orarie (Open-Meteo)
    static String sunrise = "", sunset = "";
    static long sunriseMs, sunsetMs;
    static int uvMax = -1;
    static int[] hTemp = new int[0], hCode = new int[0];
    static String[] hLabel = new String[0];
    static long[] hTime = new long[0];

    /** Posizione del sole tra alba (0) e tramonto (1); fuori dall'intervallo se è notte. */
    static float sunFraction() {
        if (sunriseMs <= 0 || sunsetMs <= sunriseMs) return -1;
        long now = System.currentTimeMillis();
        // gli orari sono di oggi: si confronta solo l'ora del giorno
        java.util.Calendar c = java.util.Calendar.getInstance();
        long midnight = now - (c.get(java.util.Calendar.HOUR_OF_DAY) * 3600000L + c.get(java.util.Calendar.MINUTE) * 60000L
                + c.get(java.util.Calendar.SECOND) * 1000L);
        long rise = midnight + sunriseMs, set = midnight + sunsetMs;
        return (now - rise) / (float) (set - rise);
    }

    /** Indice della prima ora di previsione non ancora passata. */
    static int hourIndex() {
        long now = System.currentTimeMillis() - 3600000L;
        for (int i = 0; i < hTime.length; i++) if (hTime[i] > now) return i;
        return Math.max(0, hTime.length - 1);
    }
    static int stepGoal = 8000;

    static boolean wOk;
    static int wTemp, wCode, wMax, wMin;
    static String wCity = "";
    static int[] dCode = new int[0];
    static int[] dMax = new int[0];
    static int[] dMin = new int[0];
    static String[] dName = new String[0];

    private static int minutesOf(String hhmm) {
        try {
            return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
        } catch (Exception e) {
            return 0;
        }
    }

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
                SimpleDateFormat out = new SimpleDateFormat("EEE", T.loc());
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
            if (d != null) {
                JSONArray sr = d.optJSONArray("sunrise"), ss = d.optJSONArray("sunset"), uv = d.optJSONArray("uv_index_max");
                if (sr != null && ss != null && sr.length() > 0) {
                    sunrise = sr.optString(0).length() >= 16 ? sr.optString(0).substring(11, 16) : "";
                    sunset = ss.optString(0).length() >= 16 ? ss.optString(0).substring(11, 16) : "";
                    sunriseMs = minutesOf(sunrise) * 60000L;
                    sunsetMs = minutesOf(sunset) * 60000L;
                }
                if (uv != null && uv.length() > 0) uvMax = (int) Math.round(uv.optDouble(0, -1));
            }
            JSONObject hr = j.optJSONObject("hourly");
            if (hr != null) {
                JSONArray ht = hr.getJSONArray("time"), tt = hr.getJSONArray("temperature_2m"), hc = hr.getJSONArray("weather_code");
                int n = Math.min(48, ht.length());
                hTemp = new int[n];
                hCode = new int[n];
                hLabel = new String[n];
                hTime = new long[n];
                SimpleDateFormat hin = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                for (int i = 0; i < n; i++) {
                    hTemp[i] = (int) Math.round(tt.optDouble(i, 0));
                    hCode[i] = hc.optInt(i, 3);
                    String ts = ht.getString(i);
                    hLabel[i] = ts.length() >= 13 ? ts.substring(11, 13) : "";
                    try {
                        hTime[i] = hin.parse(ts).getTime();
                    } catch (Exception e) {
                        hTime[i] = 0;
                    }
                }
            }
            wCity = city == null ? "" : city;
            wOk = true;
        } catch (Exception ignored) {
        }
    }

    static String describe(int code) {
        if (code == 0) return T.t("Sereno");
        if (code <= 2) return T.t("Poco nuvoloso");
        if (code == 3) return T.t("Nuvoloso");
        if (code == 45 || code == 48) return T.t("Nebbia");
        if (code >= 51 && code <= 57) return T.t("Pioggerella");
        if (code >= 61 && code <= 67) return T.t("Pioggia");
        if (code >= 71 && code <= 77) return T.t("Neve");
        if (code >= 80 && code <= 82) return T.t("Rovesci");
        if (code == 85 || code == 86) return T.t("Neve");
        if (code >= 95) return T.t("Temporale");
        return "";
    }

    private State() {}
}
