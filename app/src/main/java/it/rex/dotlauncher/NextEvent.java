package it.rex.dotlauncher;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Prossimo evento del calendario per il widget Riepilogo.
 * Si legge in background al massimo ogni 5 minuti, o subito quando l'evento mostrato è finito.
 */
final class NextEvent {
    /** Evento: id, titolo, inizio, fine, tutto il giorno. */
    static final class Ev {
        final long id, begin, end;
        final String title;
        final boolean allDay;

        Ev(long id, String title, long begin, long end, boolean allDay) {
            this.id = id;
            this.title = title;
            this.begin = begin;
            this.end = end;
            this.allDay = allDay;
        }
    }

    /** Evento da mostrare, oppure null. */
    static volatile Ev current;
    /** Il permesso del calendario c'è (se manca il widget invita a toccarlo). */
    static volatile boolean permitted = true;
    private static volatile long loadedAt;
    private static volatile boolean loading;

    private static final long REFRESH = 5 * 60000L;
    private static final long HORIZON = 7L * 86400000L;

    static boolean permitted(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Ricarica se i dati sono vecchi o l'evento mostrato è già finito; {@code done} gira sul thread principale. */
    static void refreshIfNeeded(Context c, boolean force, Runnable done) {
        long now = System.currentTimeMillis();
        Ev e = current;
        boolean stale = force || now - loadedAt > REFRESH || (e != null && endLocal(e) <= now);
        if (!stale || loading) return;
        permitted = permitted(c);
        if (!permitted) {
            current = null;
            loadedAt = now;
            if (done != null) done.run();
            return;
        }
        loading = true;
        final Context app = c.getApplicationContext();
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            Ev found = null;
            try {
                found = pick(query(app, now), now);
            } catch (Exception ignored) {
            }
            final Ev f = found;
            ui.post(() -> {
                current = f;
                loadedAt = System.currentTimeMillis();
                loading = false;
                if (done != null) done.run();
            });
        }, "next-event").start();
    }

    private static List<Ev> query(Context c, long now) {
        List<Ev> out = new ArrayList<>();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        // da mezzanotte: gli eventi di tutto il giorno di oggi iniziano prima di adesso
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        ContentUris.appendId(b, start.getTimeInMillis());
        ContentUris.appendId(b, now + HORIZON);
        String[] proj = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY};
        try (Cursor cur = c.getContentResolver().query(b.build(), proj, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            while (cur != null && cur.moveToNext() && out.size() < 40) {
                out.add(new Ev(cur.getLong(0), cur.getString(1), cur.getLong(2), cur.getLong(3), cur.getInt(4) == 1));
            }
        }
        return out;
    }

    /**
     * Sceglie l'evento da mostrare: il primo con un orario non ancora finito;
     * se non ce ne sono, il primo evento di tutto il giorno non ancora finito.
     */
    static Ev pick(List<Ev> evs, long now) {
        Ev allDay = null;
        for (Ev e : evs) {
            if (endLocal(e) <= now) continue;
            if (!e.allDay) return e;
            if (allDay == null) allDay = e;
        }
        return allDay;
    }

    /**
     * Quando, in breve: "Adesso", "Tra 25 min", "Oggi · 14:30", "Domani · 9:00", "ven · 9:00"
     * (per gli eventi di tutto il giorno solo il giorno).
     */
    static String when(Ev e, long now, boolean h24) {
        if (!e.allDay && e.begin <= now) return T.t("Adesso");
        if (!e.allDay && e.begin - now < 60 * 60000L) {
            long min = Math.max(1, (e.begin - now + 59999) / 60000);
            return T.t("Tra ") + min + " min";
        }
        int diff = daysBetween(now, e.allDay ? utcDayToLocal(e.begin) : e.begin);
        String day = diff <= 0 ? T.t("Oggi") : diff == 1 ? T.t("Domani")
                : new SimpleDateFormat("EEE d", T.loc()).format(new Date(e.allDay ? utcDayToLocal(e.begin) : e.begin)).replace(".", "");
        if (e.allDay) return day;
        return day + " · " + new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", T.loc()).format(new Date(e.begin));
    }

    /** Fine dell'evento nell'ora locale (per quelli di tutto il giorno, la mezzanotte locale). */
    static long endLocal(Ev e) {
        return e.allDay ? utcDayToLocal(e.end) : e.end;
    }

    /** Gli eventi di tutto il giorno sono salvati a mezzanotte UTC: si riportano al giorno locale. */
    static long utcDayToLocal(long utcMidnight) {
        Calendar u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utcMidnight);
        Calendar l = Calendar.getInstance();
        l.clear();
        l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return l.getTimeInMillis();
    }

    /** Giorni di calendario (locali) da {@code from} a {@code to}. */
    static int daysBetween(long from, long to) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(from);
        b.setTimeInMillis(to);
        int d = 0;
        if (b.before(a)) {
            while (a.get(Calendar.YEAR) != b.get(Calendar.YEAR) || a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR)) {
                b.add(Calendar.DAY_OF_YEAR, 1);
                d--;
                if (d < -400) break;
            }
            return d;
        }
        while (a.get(Calendar.YEAR) != b.get(Calendar.YEAR) || a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR)) {
            a.add(Calendar.DAY_OF_YEAR, 1);
            d++;
            if (d > 400) break;
        }
        return d;
    }

    private NextEvent() {}
}
