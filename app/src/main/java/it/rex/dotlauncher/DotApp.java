package it.rex.dotlauncher;

import android.app.Application;

/** Avvio dell'app: sceglie la lingua prima che si apra qualsiasi schermata. */
public class DotApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        T.init(this);
    }
}
