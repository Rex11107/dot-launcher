package it.rex.dotlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Rende leggibile alle altre app (WhatsApp, email, Drive…) un solo file: il layout da condividere.
 * Non è esportato: l'accesso passa solo con il permesso temporaneo dato dall'invio (FLAG_GRANT_READ_URI_PERMISSION).
 */
public class ShareProvider extends ContentProvider {
    static final String AUTHORITY = "it.rex.dotlauncher.share";
    static final String NAME = "dotlauncher-layout.json";

    static File file(android.content.Context c) {
        File dir = new File(c.getCacheDir(), "share");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, NAME);
    }

    static Uri uri() {
        return Uri.parse("content://" + AUTHORITY + "/" + NAME);
    }

    private File target(Uri uri) throws FileNotFoundException {
        // un solo nome ammesso: niente percorsi verso altri file dell'app
        if (getContext() == null || !NAME.equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        File f = file(getContext());
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("sola lettura");
        return ParcelFileDescriptor.open(target(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = target(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{NAME, f.length()});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
