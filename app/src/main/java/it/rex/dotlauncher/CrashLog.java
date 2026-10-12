package it.rex.dotlauncher;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Registro locale degli errori: se il launcher si chiude per un errore, il motivo resta in un file
 * sul telefono. Non viene inviato a nessuno: l'utente può copiarlo o condividerlo da Informazioni.
 */
final class CrashLog {
    private static final String FILE = "crash.txt";
    private static final int MAX = 48 * 1024;

    /** Da chiamare all'avvio dell'app: registra gli errori e poi lascia fare ad Android. */
    static void install(Context c) {
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                write(app, t, e);
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Testo di un errore, con le informazioni utili per capirlo (versione, telefono, Android). */
    static String format(String when, String version, String thread, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== " + when + " ===");
        pw.println("Dot Launcher " + version + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
                + ") · " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Thread: " + thread);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void write(Context c, Thread t, Throwable e) throws Exception {
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String entry = format(when, versionName(c), t.getName(), e);
        String old = read(c);
        String all = entry + "\n" + old;
        if (all.length() > MAX) all = all.substring(0, MAX); // si tengono gli errori più recenti
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), FILE))) {
            out.write(all.getBytes(StandardCharsets.UTF_8));
        }
        c.getSharedPreferences("dot", Context.MODE_PRIVATE).edit()
                .putLong("crashAt", System.currentTimeMillis()).putBoolean("crashNew", true).commit();
    }

    static String read(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), MAX)];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    static boolean has(Context c) {
        return new File(c.getFilesDir(), FILE).length() > 0;
    }

    /** Quanti errori ci sono nel registro. */
    static int count(String log) {
        int n = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^=== \\d").matcher(log);
        while (m.find()) n++;
        return n;
    }

    static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), FILE).delete();
        c.getSharedPreferences("dot", Context.MODE_PRIVATE).edit().remove("crashAt").remove("crashNew").apply();
    }

    private CrashLog() {}
}
