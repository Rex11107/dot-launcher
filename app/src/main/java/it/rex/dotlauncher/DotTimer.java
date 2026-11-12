package it.rex.dotlauncher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Timer di Dot Launcher. Non dipende dall'app Orologio del telefono: la fine è un evento dell'AlarmManager
 * (come le sveglie di Dot), a fine tempo suona con il servizio di Dot Sveglia, e durante il conto alla rovescia
 * c'è una notifica fissa con "+1 min" e "Annulla". L'anello della fotocamera legge il tempo da qui.
 */
final class DotTimer {
    static final String ACTION_CANCEL = "it.rex.dotlauncher.TIMER_CANCEL";
    static final String ACTION_ADD = "it.rex.dotlauncher.TIMER_ADD";
    static final String EXTRA_TIMER = "dotTimer";
    private static final String CHANNEL = "dot_timer";
    private static final int NOTIF_ID = 4201;

    private DotTimer() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dot", Context.MODE_PRIVATE);
    }

    /** Fine del timer (ms dall'epoca), 0 se non c'è. */
    static long end(Context c) {
        long e = prefs(c).getLong("timerEnd", 0);
        return e > System.currentTimeMillis() ? e : 0;
    }

    /** Durata totale del timer in corso (ms). */
    static long total(Context c) {
        return Math.max(1000, prefs(c).getLong("timerTotal", 1000));
    }

    static void start(Context c, int seconds) {
        long now = System.currentTimeMillis(), end = now + seconds * 1000L;
        prefs(c).edit().putLong("timerEnd", end).putLong("timerTotal", seconds * 1000L).apply();
        schedule(c, end);
        notifyRunning(c, end);
        CameraRing.timerChanged();
    }

    /** Aggiunge tempo (anche al totale, così l'anello non torna indietro). */
    static void add(Context c, int seconds) {
        long end = end(c);
        if (end == 0) return;
        end += seconds * 1000L;
        prefs(c).edit().putLong("timerEnd", end).putLong("timerTotal", total(c) + seconds * 1000L).apply();
        schedule(c, end);
        notifyRunning(c, end);
        CameraRing.timerChanged();
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(fire(c, 0));
        finished(c);
    }

    /** Il timer è arrivato a zero (o è stato annullato): si toglie la notifica fissa. */
    static void finished(Context c) {
        prefs(c).edit().remove("timerEnd").remove("timerTotal").apply();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
        CameraRing.timerChanged();
    }

    /** Dopo un riavvio o un aggiornamento: il timer in corso si riprogramma. */
    static void reschedule(Context c) {
        long end = end(c);
        if (end == 0) {
            if (prefs(c).contains("timerEnd")) finished(c);
            return;
        }
        schedule(c, end);
        notifyRunning(c, end);
    }

    private static PendingIntent fire(Context c, long end) {
        Intent i = new Intent(c, DotAlarmReceiver.class).setAction(DotAlarmReceiver.ACTION_FIRE)
                .putExtra(DotAlarms.EXTRA_ID, -1).putExtra(DotAlarms.EXTRA_RING, end).putExtra(EXTRA_TIMER, true);
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c, long end) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent show = PendingIntent.getActivity(c, 8, new Intent(c, AlarmsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(end, show), fire(c, end));
        } catch (SecurityException e) {
            // senza il permesso per gli orari esatti: comunque il più preciso possibile
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, fire(c, end));
        }
    }

    private static void notifyRunning(Context c, long end) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, T.t("Timer"), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent cancel = PendingIntent.getBroadcast(c, 9,
                new Intent(c, DotAlarmReceiver.class).setAction(ACTION_CANCEL), fl);
        PendingIntent add = PendingIntent.getBroadcast(c, 10,
                new Intent(c, DotAlarmReceiver.class).setAction(ACTION_ADD), fl);
        PendingIntent open = PendingIntent.getActivity(c, 11, new Intent(c, AlarmsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fl);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(T.t("Timer"))
                .setContentText(T.t("Finisce alle ") + android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date(end)))
                .setWhen(end)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, T.t("+1 min"), add).build())
                .addAction(new Notification.Action.Builder(null, T.t("Annulla"), cancel).build())
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
            // notifiche non permesse: il timer suona comunque, l'anello lo mostra lo stesso
        }
    }
}
