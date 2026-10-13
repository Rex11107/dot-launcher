package it.rex.dotlauncher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Aggiornamento dall'app: scarica l'APK della nuova versione e lo installa con il programma di installazione di Android. */
final class Updater {
    interface Progress {
        /** Percentuale 0..100, oppure -1 se la dimensione non è nota. Chiamato fuori dal thread UI. */
        void on(int pct);
    }

    /** Scarica l'APK nella cache dell'app. Da chiamare fuori dal thread UI. */
    static File download(Context c, String url, Progress p) throws Exception {
        File out = new File(c.getCacheDir(), "update.apk");
        String u = url;
        HttpURLConnection con = null;
        boolean ok = false;
        // GitHub reindirizza al file vero: si seguono i reindirizzamenti a mano (al massimo 5)
        for (int i = 0; i < 5; i++) {
            // solo https: un reindirizzamento verso http permetterebbe di sostituire l'APK lungo la strada
            if (!u.startsWith("https://")) throw new Exception("https");
            con = (HttpURLConnection) new URL(u).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "DotLauncher");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = con.getHeaderField("Location");
                con.disconnect();
                if (loc == null) throw new Exception("redirect");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code != 200) throw new Exception("HTTP " + code);
            ok = true;
            break;
        }
        if (!ok) throw new Exception("redirect");
        long total = con.getContentLengthLong();
        try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int r, last = -2;
            while ((r = in.read(buf)) > 0) {
                os.write(buf, 0, r);
                done += r;
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                if (pct != last) {
                    last = pct;
                    p.on(pct);
                }
            }
        } finally {
            con.disconnect();
        }
        if (out.length() < 100 * 1024) throw new Exception("file troppo piccolo");
        return out;
    }

    /** Passa l'APK al programma di installazione: Android chiede conferma all'utente. */
    static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream out = s.openWrite("DotLauncher.apk", 0, apk.length());
                 InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                s.fsync(out);
            }
            Intent i = new Intent(c, UpdateReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(c, id, i, flags);
            s.commit(pend.getIntentSender());
        }
    }

    private Updater() {}
}
