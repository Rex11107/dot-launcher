package it.rex.dotlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Riceve l'ora della sveglia (alba o suono) e la consegna al servizio che la fa suonare.
 * Riceve anche riavvio, cambio d'ora e aggiornamento dell'app, per riprogrammare la prossima sveglia.
 */
public class DotAlarmReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "it.rex.dotlauncher.ALARM_FIRE";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (DotTimer.ACTION_CANCEL.equals(a)) {
            DotTimer.cancel(c);
            return;
        }
        if (DotTimer.ACTION_ADD.equals(a)) {
            DotTimer.add(c, 60);
            return;
        }
        if (ACTION_FIRE.equals(a)) {
            if (intent.getBooleanExtra(DotTimer.EXTRA_TIMER, false)) DotTimer.finished(c); // timer a zero: suona
            Intent s = new Intent(c, DotAlarmService.class).setAction(DotAlarmService.ACTION_START)
                    .putExtras(intent);
            try {
                c.startForegroundService(s);
            } catch (Exception e) {
                // ultima risorsa: almeno la schermata della sveglia
                try {
                    c.startActivity(new Intent(c, DotAlarmActivity.class).putExtras(intent)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            }
            return;
        }
        // riavvio, ora cambiata, app aggiornata: si riprogramma (anche un timer in corso)
        DotAlarms.schedule(c);
        DotTimer.reschedule(c);
    }
}
