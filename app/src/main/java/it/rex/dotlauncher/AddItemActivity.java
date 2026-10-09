package it.rex.dotlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.UserManager;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

/**
 * Riceve le richieste "Aggiungi alla schermata Home" dalle altre app (es. Chrome, WhatsApp).
 * Accetta la scorciatoia e la mette in coda: la home la posiziona al prossimo avvio.
 */
public class AddItemActivity extends Activity {

    static String key(String pkg, String id, long serial, String label) {
        String l = label == null ? "" : label.replace("|", " ");
        return "sc:" + pkg + "|" + id + "|" + serial + "|" + l;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            LauncherApps.PinItemRequest req = la.getPinItemRequest(getIntent());
            if (req != null && req.isValid()
                    && req.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                ShortcutInfo si = req.getShortcutInfo();
                if (si != null && req.accept()) {
                    UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
                    long serial = um.getSerialNumberForUser(si.getUserHandle());
                    CharSequence label = si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel();
                    String k = key(si.getPackage(), si.getId(), serial, label == null ? "" : label.toString());
                    SharedPreferences p = getSharedPreferences("dot", MODE_PRIVATE);
                    Set<String> pend = new HashSet<>(p.getStringSet("pendingSc", new HashSet<>()));
                    pend.add(k);
                    p.edit().putStringSet("pendingSc", pend).commit();
                    Toast.makeText(this, "Aggiunto alla home", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aggiungere alla home", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
