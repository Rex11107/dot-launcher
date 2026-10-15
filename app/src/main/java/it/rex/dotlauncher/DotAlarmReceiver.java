package it.rex.dotlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Riceve l'ora della sveglia (alba o suono) e la consegna al servizio che la fa suonare.
 * Riceve anche riavvio, cambio d'ora e aggiornamento dell'app, per riprogrammare la prossima sveglia.
 */
public class DotAlarmReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "it.rex.dotlauncher.ALARM_FIRE";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_FIRE.equals(a)) {
            Intent s = new Intent(c, DotAlarmService.class).setAction(DotAlarmService.ACTION_START)
                    .putExtras(intent);
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
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
        // riavvio, ora cambiata, app aggiornata: si riprogramma
        DotAlarms.schedule(c);
    }
}
