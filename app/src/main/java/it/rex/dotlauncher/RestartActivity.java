package it.rex.dotlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Riavvia il launcher (dopo il cambio di lingua): gira in un processo a parte, aspetta che quello
 * principale si chiuda e poi riapre la home.
 */
public class RestartActivity extends Activity {
    static void restart(Activity from) {
        Intent i = new Intent(from, RestartActivity.class)
                .putExtra("pid", android.os.Process.myPid())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        from.startActivity(i);
        from.finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        int pid = getIntent().getIntExtra("pid", -1);
        if (pid > 0 && pid != android.os.Process.myPid()) android.os.Process.killProcess(pid);
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(home);
        finish();
        overridePendingTransition(0, 0);
        Runtime.getRuntime().exit(0);
    }
}
