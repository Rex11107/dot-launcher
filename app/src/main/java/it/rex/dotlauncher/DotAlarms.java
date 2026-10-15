package it.rex.dotlauncher;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Dot Sveglia: le sveglie del launcher, con l'alba (luce che sale) prima del suono.
 * La logica di programmazione segue quella di Fossify Clock (GPL-3.0): setAlarmClock, che Android tratta
 * come una sveglia vera (icona nella barra, nessun ritardo in risparmio energetico), e riprogrammazione
 * dopo riavvio, cambio d'ora e aggiornamento dell'app.
 */
final class DotAlarms {
    /** Quale app apre il widget Sveglia: "dot", "system" o il nome del pacchetto di un'altra app. */
    static final String PREF_APP = "alarmApp";
    static final String PREF_LIST = "dotAlarms";
    static final String EXTRA_ID = "alarmId";
    static final String EXTRA_RING = "ringAt";
    static final String EXTRA_TEST = "test";

    /** Colori della luce: {nome, colore finale}. Si parte sempre dal rosso scuro. */
    static final String[][] COLORS = {
            {"Alba", "FFFFB15C"}, {"Arancio", "FFFF8A1F"}, {"Giallo caldo", "FFFFD27A"},
            {"Bianco caldo", "FFFFF1DC"}, {"Rosso", "FFFF4A2E"}};

    static final class Alarm {
        int id;
        int hour = 7, minute = 0;
        int days = 0x7F;           // bit 0 = lunedì … bit 6 = domenica; 0 = una volta sola
        String name = "";
        boolean on = true;
        int wake = 10;             // minuti di alba prima del suono
        int color = 0;             // indice in COLORS
        int bright = 100;          // luminosità finale dello schermo, %
        int volume = 80;           // volume massimo del suono, %
        boolean vibrate = true;
        String sound = "";         // vuoto = suoneria di sveglia del telefono
        int snooze = 5;            // minuti di posticipo; 0 = niente posticipo
        int snoozeMode = 0;        // durante il posticipo: 0 alba di nuovo, 1 luce piena, 2 schermo spento
        long skipAt;               // la ripetizione con questo suono (originale) si salta
        long shiftAt;              // la ripetizione con questo suono (originale) si sposta di shiftMin minuti
        int shiftMin;
        int volUp, volDown;        // tasti del volume sulla sveglia: 0 regolano il volume, 1 posticipa, 2 ferma

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("h", hour).put("m", minute).put("d", days)
                    .put("n", name).put("on", on).put("w", wake).put("c", color).put("b", bright)
                    .put("v", volume).put("vi", vibrate).put("s", sound)
                    .put("sn", snooze).put("sm", snoozeMode).put("sk", skipAt).put("sa", shiftAt).put("sh", shiftMin)
                    .put("vu", volUp).put("vd", volDown);
        }

        static Alarm from(JSONObject o) {
            Alarm a = new Alarm();
            a.id = o.optInt("id");
            a.hour = o.optInt("h", 7);
            a.minute = o.optInt("m", 0);
            a.days = o.optInt("d", 0x7F);
            a.name = o.optString("n", "");
            a.on = o.optBoolean("on", true);
            a.wake = o.optInt("w", 10);
            a.color = Math.max(0, Math.min(COLORS.length - 1, o.optInt("c", 0)));
            a.bright = o.optInt("b", 100);
            a.volume = o.optInt("v", 80);
            a.vibrate = o.optBoolean("vi", true);
            a.sound = o.optString("s", "");
            a.snooze = o.optInt("sn", 5);
            a.snoozeMode = o.optInt("sm", 0);
            a.skipAt = o.optLong("sk", 0);
            a.shiftAt = o.optLong("sa", 0);
            a.shiftMin = o.optInt("sh", 0);
            a.volUp = o.optInt("vu", 0);
            a.volDown = o.optInt("vd", 0);
            return a;
        }

        int endColor() {
            return (int) Long.parseLong(COLORS[color][1], 16);
        }

        /** Prossimo suono (non l'inizio dell'alba) a partire da "dopo", con salti e spostamenti. */
        long nextRing(long after) {
            long[] o = nextOccurrence(after);
            return o == null ? -1 : o[1];
        }

        /** {suono originale, suono effettivo} della prossima ripetizione, oppure null. */
        long[] nextOccurrence(long after) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(after);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            for (int i = 0; i < 9; i++) {
                long orig = c.getTimeInMillis();
                boolean day = days == 0 || (days & (1 << dayIndex(c))) != 0;
                c.add(Calendar.DAY_OF_YEAR, 1);
                if (!day || orig == skipAt) continue;
                long t = orig == shiftAt ? orig + shiftMin * 60000L : orig;
                // l'alba deve poter partire nel futuro: se il suono è troppo vicino si passa al giorno dopo
                if (t - wake * 60000L > after) return new long[]{orig, t};
            }
            return null;
        }
    }

    /** Lunedì = 0 … domenica = 6. */
    static int dayIndex(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dot", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return "dot".equals(prefs(c).getString(PREF_APP, "system"));
    }

    static List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(PREF_LIST, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Alarm.from(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(Context c, List<Alarm> list) {
        JSONArray a = new JSONArray();
        try {
            for (Alarm al : list) a.put(al.json());
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(PREF_LIST, a.toString()).apply();
        schedule(c);
    }

    static Alarm find(Context c, int id) {
        for (Alarm a : load(c)) if (a.id == id) return a;
        return null;
    }

    static int newId(List<Alarm> list) {
        int max = 0;
        for (Alarm a : list) max = Math.max(max, a.id);
        return max + 1;
    }

    /** {id, suono} della prossima sveglia attiva, oppure null. */
    static long[] next(Context c) {
        long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
        int id = -1;
        for (Alarm a : load(c)) {
            if (!a.on) continue;
            long t = a.nextRing(now);
            if (t > 0 && t < best) {
                best = t;
                id = a.id;
            }
        }
        return id < 0 ? null : new long[]{id, best};
    }

    /** code: 1 = suono, 4 = inizio dell'alba, 2 = prova. Codici diversi: Android non li confonde. */
    private static PendingIntent trigger(Context c, int code, int id, long ring, boolean test) {
        Intent i = new Intent(c, DotAlarmReceiver.class).setAction(DotAlarmReceiver.ACTION_FIRE)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_RING, ring).putExtra(EXTRA_TEST, test);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent show(Context c) {
        Intent i = new Intent(c, AlarmsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 3, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Programma la prossima sveglia (ne basta una alla volta: dopo che suona si programma la successiva). */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long[] n = enabled(c) ? next(c) : null;
        if (n == null) {
            am.cancel(trigger(c, 1, 0, 0, false));
            am.cancel(trigger(c, 4, 0, 0, false));
            return;
        }
        Alarm a = find(c, (int) n[0]);
        long start = n[1] - (a == null ? 0 : a.wake * 60000L);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(n[1], show(c)), trigger(c, 1, (int) n[0], n[1], false));
        } catch (SecurityException e) {
            // permesso "Sveglie e promemoria" mancante: la lista di controllo lo segnala
        }
        if (start < n[1]) {
            // l'alba parte prima del suono: un secondo evento esatto per l'inizio della luce
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, trigger(c, 4, (int) n[0], n[1], false));
            } catch (SecurityException ignored) {
            }
        } else {
            am.cancel(trigger(c, 4, 0, 0, false));
        }
    }

    /** Posticipo: un evento di sicurezza all'ora del nuovo suono, anche se il servizio venisse chiuso. */
    static void snoozeBackup(Context c, int id, long ring) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(ring, show(c)), trigger(c, 5, id, ring, false));
        } catch (SecurityException ignored) {
        }
    }

    static void cancelSnooze(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(trigger(c, 5, 0, 0, false));
    }

    /** −5/+5 minuti per la prossima ripetizione di una sveglia (le altre restano com'erano). */
    static void shiftNext(Context c, int id, int delta) {
        List<Alarm> l = load(c);
        long now = System.currentTimeMillis();
        for (Alarm a : l) {
            if (a.id != id) continue;
            long[] o = a.nextOccurrence(now);
            if (o == null) return;
            if (a.shiftAt == o[0]) a.shiftMin = Math.max(-120, Math.min(120, a.shiftMin + delta));
            else {
                a.shiftAt = o[0];
                a.shiftMin = delta;
            }
            if (a.shiftMin == 0) a.shiftAt = 0;
        }
        save(c, l);
    }

    /** Salta la prossima ripetizione di una sveglia. */
    static void skipNext(Context c, int id) {
        List<Alarm> l = load(c);
        long now = System.currentTimeMillis();
        for (Alarm a : l) {
            if (a.id != id) continue;
            long[] o = a.nextOccurrence(now);
            if (o == null) return;
            a.skipAt = o[0];
            if (a.shiftAt == o[0]) a.shiftAt = 0;
        }
        save(c, l);
    }

    /** Annulla salti e spostamenti ancora da venire. */
    static void resetNext(Context c) {
        List<Alarm> l = load(c);
        long now = System.currentTimeMillis();
        for (Alarm a : l) {
            if (a.skipAt > now) a.skipAt = 0;
            if (a.shiftAt > now) {
                a.shiftAt = 0;
                a.shiftMin = 0;
            }
        }
        save(c, l);
    }

    static boolean hasChanges(Context c) {
        long now = System.currentTimeMillis();
        for (Alarm a : load(c)) if (a.skipAt > now || a.shiftAt > now) return true;
        return false;
    }

    /** Prova: una sveglia vera tra "minutes" minuti, con 30 secondi di alba. */
    static void test(Context c, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long ring = System.currentTimeMillis() + minutes * 60000L;
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(ring, show(c)), trigger(c, 2, -1, ring, true));
        } catch (SecurityException ignored) {
        }
    }

    // ---------- lista di controllo ----------

    static boolean exactOk(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    static boolean notifOk(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    static boolean fullScreenOk(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.canUseFullScreenIntent();
    }

    static boolean batteryOk(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static boolean autostartOk(Context c) {
        return prefs(c).getBoolean("alarmAutostart", false);
    }

    static boolean allOk(Context c) {
        return exactOk(c) && notifOk(c) && fullScreenOk(c) && batteryOk(c) && autostartOk(c);
    }

    static Uri soundUri(Context c, Alarm a) {
        if (a != null && !a.sound.isEmpty() && !AmbientSound.is(a.sound)) return Uri.parse(a.sound);
        Uri u = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        return u;
    }

    private DotAlarms() {}
}
