package it.rex.dotlauncher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Riceve un layout di Dot Launcher aperto o condiviso da un'altra app: lo copia in un file temporaneo
 * e lo passa alla home, che chiede conferma prima di applicarlo. Non mostra niente di suo.
 */
public class ImportActivity extends Activity {
    static final String EXTRA_FILE = "importLayout";
    private static final long MAX = 2 * 1024 * 1024; // un layout è di pochi kB

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Uri src = null;
        Intent in = getIntent();
        if (in != null) {
            if (Intent.ACTION_SEND.equals(in.getAction())) src = in.getParcelableExtra(Intent.EXTRA_STREAM);
            else src = in.getData();
        }
        File out = new File(getCacheDir(), "incoming-layout.json");
        boolean ok = false;
        if (src != null) {
            try (InputStream is = getContentResolver().openInputStream(src); OutputStream os = new FileOutputStream(out)) {
                if (is != null) {
                    byte[] buf = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        total += n;
                        if (total > MAX) throw new Exception("troppo grande");
                        os.write(buf, 0, n);
                    }
                    ok = true;
                }
            } catch (Exception e) {
                ok = false;
            }
        }
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_FILE, ok); // solo un sì/no: il percorso lo conosce la home, non arriva da fuori
        startActivity(home);
        finish();
    }
}
