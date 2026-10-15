package it.rex.dotlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.List;

/**
 * Fa suonare la sveglia: resta in primo piano per tutta l'alba e il suono, così Android non la chiude.
 * Struttura ripresa da AlarmService di Fossify Clock (GPL-3.0), riscritta in Java e semplificata:
 * il volume sale sul lettore, senza toccare il volume di sistema.
 */
public class DotAlarmService extends Service {
    static final String ACTION_START = "it.rex.dotlauncher.ALARM_START";
    static final String ACTION_STOP = "it.rex.dotlauncher.ALARM_STOP";
    static final String ACTION_SNOOZE = "it.rex.dotlauncher.ALARM_SNOOZE";
    private static final String CHANNEL = "dot_alarm";
    private static final int NOTIF_ID = 4711;
    private static final long MAX_RING_MS = 10 * 60000L;   // dopo 10 minuti di suono si ferma da sola
    private static final long FADE_MS = 60000L;            // il volume sale in un minuto

    /** Stato letto dalla schermata della sveglia. */
    static volatile DotAlarms.Alarm active;
    static volatile long dawnStart, ringAt;
    static volatile boolean ringing, running;
    /** Posticipo con "luce piena": lo schermo resta al massimo fino al nuovo suono. */
    static volatile boolean fullLight, snoozed;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private int savedAlarmVolume = -1;
    private android.media.AudioFocusRequest focus;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // riavviato da Android senza dati: meglio non suonare a caso
            stopSelf();
            return START_NOT_STICKY;
        }
        String a = intent.getAction();
        if (ACTION_STOP.equals(a)) {
            stopAlarm();
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(a)) {
            snooze();
            return START_REDELIVER_INTENT;
        }
        startForegroundNow();
        int id = intent.getIntExtra(DotAlarms.EXTRA_ID, -1);
        boolean test = intent.getBooleanExtra(DotAlarms.EXTRA_TEST, false);
        long ring = intent.getLongExtra(DotAlarms.EXTRA_RING, 0);
        if (running && active != null && active.id == id && !test) return START_REDELIVER_INTENT; // alba già in corso

        DotAlarms.Alarm al = test ? null : DotAlarms.find(this, id);
        if (al == null) {
            al = new DotAlarms.Alarm();
            al.id = -1;
            al.name = test ? T.t("Prova della sveglia") : "";
            al.wake = 0;
            DotAlarms.Alarm first = firstAlarm();
            if (first != null) { // la prova usa colore, luce e suono della prima sveglia
                al.color = first.color;
                al.bright = first.bright;
                al.volume = first.volume;
                al.vibrate = first.vibrate;
                al.sound = first.sound;
            }
        }
        long now = System.currentTimeMillis();
        if (test) ring = now + 30000L;               // la prova: 30 secondi di alba, poi il suono
        if (ring <= 0 || ring < now - 60000L) ring = now; // in ritardo: si suona subito
        stopEffects();
        active = al;
        dawnStart = now;
        ringAt = ring;
        ringing = false;
        running = true;
        fullLight = false;
        snoozed = false;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dot:alarm");
            wake.acquire(Math.max(0, ring - now) + MAX_RING_MS + 60000L);
        }
        openScreen();
        h.postDelayed(this::startRinging, Math.max(0, ring - now));
        // se Android chiude il servizio mentre suona, lo riavvia con la stessa sveglia (che riprende subito)
        return START_REDELIVER_INTENT;
    }

    /**
     * Posticipa: si ferma il suono e si riprova dopo "snooze" minuti. Un evento di sicurezza nell'AlarmManager
     * fa suonare comunque la sveglia anche se Android chiudesse il servizio nel frattempo.
     */
    private void snooze() {
        DotAlarms.Alarm al = active;
        if (al == null || !running) return;
        int min = al.snooze > 0 ? al.snooze : 5;
        long now = System.currentTimeMillis(), ring = now + min * 60000L;
        stopEffects();
        DotAlarms.snoozeBackup(this, al.id, ring);
        if (al.snoozeMode == 2) {
            // schermo spento fino al suono: il servizio si ferma, ci pensa l'evento di sicurezza
            running = false;
            ringing = false;
            active = null;
            if (wake != null && wake.isHeld()) wake.release();
            wake = null;
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
            stopSelf();
            return;
        }
        // 0: l'alba riparte da capo durante il posticipo; 1: luce piena fino al nuovo suono
        dawnStart = now;
        ringAt = ring;
        ringing = false;
        snoozed = true;
        fullLight = al.snoozeMode == 1;
        if (wake != null && wake.isHeld()) wake.release();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dot:alarm");
            wake.acquire(min * 60000L + MAX_RING_MS + 60000L);
        }
        h.postDelayed(this::startRinging, ring - now);
    }

    private DotAlarms.Alarm firstAlarm() {
        List<DotAlarms.Alarm> l = DotAlarms.load(this);
        return l.isEmpty() ? null : l.get(0);
    }

    private void openScreen() {
        try {
            startActivity(new Intent(this, DotAlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // bloccato da Android: ci pensa la notifica a schermo intero
        }
    }

    private void startForegroundNow() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, T.t("Dot Sveglia"), NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);       // il suono lo gestisce il servizio
            ch.enableVibration(false);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent full = PendingIntent.getActivity(this, 10,
                new Intent(this, DotAlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fl);
        PendingIntent stop = PendingIntent.getService(this, 11,
                new Intent(this, DotAlarmService.class).setAction(ACTION_STOP), fl);
        PendingIntent snz = PendingIntent.getService(this, 12,
                new Intent(this, DotAlarmService.class).setAction(ACTION_SNOOZE), fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(T.t("Dot Sveglia"))
                .setContentText(T.t("Sveglia in corso"))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(new Notification.Action.Builder(null, T.t("Posticipa"), snz).build())
                .addAction(new Notification.Action.Builder(null, T.t("Ferma"), stop).build());
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            // senza servizio in primo piano si prova comunque ad aprire la schermata
        }
    }

    private void startRinging() {
        DotAlarms.Alarm al = active;
        if (al == null || !running) return;
        ringing = true;
        try {
            // Il volume sale sul volume delle sveglie del telefono, non sul lettore: sul lettore partiva quasi muto
            // e su alcuni telefoni (Honor) non si sentiva finché non si alzava il volume a mano.
            // Si parte da circa un terzo del volume scelto, si arriva al massimo in un minuto e alla fine
            // si rimette il volume com'era.
            final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            final int top = am == null ? 0 : am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            final int target = Math.max(1, Math.round(top * al.volume / 100f));
            final int from = Math.max(1, Math.round(target * 0.35f));
            if (am != null) {
                savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, from, 0);
                // la musica eventualmente in riproduzione si ferma mentre suona la sveglia
                focus = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                        .build();
                am.requestAudioFocus(focus);
            }
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, DotAlarms.soundUri(this, al));
            player.setLooping(true);
            player.prepare();
            player.setVolume(1f, 1f);
            player.start();
            final long t0 = System.currentTimeMillis();
            if (am != null && target > from) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        if (player == null) return;
                        float k = Math.min(1f, (System.currentTimeMillis() - t0) / (float) FADE_MS);
                        int v = from + Math.round((target - from) * k);
                        try {
                            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < v) {
                                am.setStreamVolume(AudioManager.STREAM_ALARM, v, 0);
                            }
                        } catch (Exception ignored) {
                        }
                        if (k < 1f) h.postDelayed(this, 1000);
                    }
                });
            }
        } catch (Exception e) {
            if (player != null) player.release();
            player = null;
        }
        if (al.vibrate) {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pat = {0, 600, 600};
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pat, 0));
                else vibrator.vibrate(pat, 0);
            }
        }
        h.postDelayed(this::stopAlarm, MAX_RING_MS);
    }

    private void stopEffects() {
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (savedAlarmVolume >= 0) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
                if (focus != null) am.abandonAudioFocusRequest(focus);
            }
            savedAlarmVolume = -1;
            focus = null;
        }
    }

    /** Ferma tutto e programma la sveglia successiva. */
    void stopAlarm() {
        DotAlarms.Alarm al = active;
        stopEffects();
        DotAlarms.cancelSnooze(this);
        fullLight = false;
        snoozed = false;
        running = false;
        ringing = false;
        active = null;
        if (al != null && al.id >= 0 && al.days == 0) {
            // sveglia singola: dopo aver suonato si spegne
            List<DotAlarms.Alarm> l = DotAlarms.load(this);
            for (DotAlarms.Alarm x : l) if (x.id == al.id) x.on = false;
            DotAlarms.save(this, l);
        } else {
            DotAlarms.schedule(this);
        }
        if (wake != null && wake.isHeld()) wake.release();
        wake = null;
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopEffects();
        running = false;
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    /** Dalla schermata della sveglia: pulsante Posticipa. */
    static void snooze(Context c) {
        try {
            c.startService(new Intent(c, DotAlarmService.class).setAction(ACTION_SNOOZE));
        } catch (Exception ignored) {
        }
    }

    /** Dalla schermata della sveglia: pulsante Ferma. */
    static void stop(Context c) {
        try {
            c.startService(new Intent(c, DotAlarmService.class).setAction(ACTION_STOP));
        } catch (Exception ignored) {
        }
    }
}
