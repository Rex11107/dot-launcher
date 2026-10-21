package it.rex.dotlauncher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ricerca universale del cassetto: calcolatrice, convertitore, app, scorciatoie delle app,
 * contatti, eventi, impostazioni del telefono e ricerca sul web.
 */
class UniversalSearch extends ScrollView {
    interface Host {
        Theme theme();
        List<HomeActivity.AppEntry> apps();
        Set<String> hiddenKeys();
        Bitmap iconFor(String key, int size);
        void launchKey(String key, View v);
        /** Esegue {@code ok} subito, o dopo lo sblocco se l'app del pacchetto è bloccata. */
        void unlockedPkg(String pkg, Runnable ok);
        void appLongPress(String key, View icon);
        void startSafe(Intent i);
        void toast(String s);
        void requestContacts();
        void requestCalendar();
        String searchEngine();
        /** Le app nascoste si possono trovare con la ricerca (opzione, spenta di base). */
        boolean hiddenSearch();
        /** Le app nascoste sono visibili adesso (nessuna protezione, o già sbloccate). */
        boolean hiddenOpen();
        /** Chiede impronta, volto o PIN per le app nascoste e poi esegue {@code ok}. */
        void unlockHidden(Runnable ok);
    }

    private final Activity act;
    private final Host host;
    private final Theme th;
    private final float dp;
    private final LinearLayout col;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private String query = "";

    /** Uso a una mano: se i risultati sono pochi stanno in basso, vicino al campo di ricerca. */
    void setBottomAligned() {
        setFillViewport(true);
        col.setGravity(Gravity.BOTTOM);
    }
    private int generation;
    private List<ShortcutInfo> shortcuts;
    private HomeActivity.AppEntry firstApp;
    private Runnable firstAction;

    UniversalSearch(Activity a, Host host) {
        super(a);
        this.act = a;
        this.host = host;
        this.th = host.theme();
        dp = a.getResources().getDisplayMetrics().density;
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    void shutdown() {
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
    }

    /** All'apertura della ricerca: prepara (in background) l'elenco delle scorciatoie delle app. */
    void prepare() {
        shortcuts = null;
        run(() -> {
            List<ShortcutInfo> list = new ArrayList<>();
            try {
                LauncherApps la = (LauncherApps) act.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (la != null && la.hasShortcutHostPermission()) {
                    LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
                    q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
                    for (UserHandle u : la.getProfiles()) {
                        List<ShortcutInfo> r = la.getShortcuts(q, u);
                        if (r != null) list.addAll(r);
                    }
                }
            } catch (Exception ignored) {
            }
            ui.post(() -> {
                shortcuts = list;
                if (!query.isEmpty()) setQuery(query);
            });
        });
    }

    private void run(Runnable r) {
        if (bg.isShutdown()) return;
        try {
            bg.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    /** Invio sulla tastiera: apre il primo risultato. */
    boolean openFirst() {
        if (firstAction != null) {
            firstAction.run();
            return true;
        }
        return false;
    }

    void setQuery(String raw) {
        query = raw.trim();
        final int gen = ++generation;
        col.removeAllViews();
        firstApp = null;
        firstAction = null;
        if (query.isEmpty()) {
            appsSection(recentApps(), T.t("Tutte le app"));
            return;
        }
        String norm = DotTextView.normalize(query);

        // 0) sveglia o timer: "sveglia 7:30", "timer 10 min"
        final int[] at = SearchTools.alarmTime(query);
        if (at != null) {
            final String hm = at[0] + ":" + (at[1] < 10 ? "0" : "") + at[1];
            final boolean dot = DotAlarms.enabled(act);
            header(T.t("Sveglia"));
            Runnable set = () -> setAlarm(at[0], at[1], hm, dot);
            row(SectionIcons.bitmap(SettingsScreen.CLOCK, px(40), th.onTile, th.accent, circle()),
                    T.t("Sveglia alle ") + hm, dot ? T.t("Dot Sveglia, una volta sola") : T.t("Con l'app della sveglia"), set, null);
            firstAction = set;
        }
        final int sec = SearchTools.timerSeconds(query);
        if (sec > 0) {
            final String d = SearchTools.duration(sec);
            header(T.t("Timer"));
            // il timer di Dot: lo vede anche l'anello della fotocamera
            Runnable set = () -> {
                DotTimer.start(act, sec);
                host.toast(T.t("Timer di ") + d + T.t(" avviato"));
            };
            Runnable clock = () -> {
                Intent i = new Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                        .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, sec)
                        .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
                host.startSafe(i);
                host.toast(T.t("Timer di ") + d + T.t(" avviato"));
            };
            row(SectionIcons.bitmap(SectionIcons.TIMER, px(40), th.onTile, th.accent, circle()),
                    T.t("Timer di ") + d, T.t("Timer di Dot Launcher"), set, null);
            row(SectionIcons.bitmap(SectionIcons.TIMER, px(40), th.onTile, th.accent, circle()),
                    T.t("Timer di ") + d, T.t("Con l'app Orologio"), clock, null);
            if (firstAction == null) firstAction = set;
        }

        // "timer" da solo: scelte rapide del timer di Dot (altrimenti compaiono solo le app Orologio)
        if (sec <= 0 && (norm.equals("timer") || norm.equals("timer "))) {
            header(T.t("Timer"));
            for (int m : new int[]{1, 5, 10, 30}) {
                final int s = m * 60;
                final String d = SearchTools.duration(s);
                Runnable set = () -> {
                    DotTimer.start(act, s);
                    host.toast(T.t("Timer di ") + d + T.t(" avviato"));
                };
                row(SectionIcons.bitmap(SectionIcons.TIMER, px(40), th.onTile, th.accent, circle()),
                        T.t("Timer di ") + d, T.t("Timer di Dot Launcher"), set, null);
                if (firstAction == null) firstAction = set;
            }
            row(SectionIcons.bitmap(SectionIcons.TIMER, px(40), th.onTile, th.accent, circle()),
                    T.t("Altro…"), T.t("Timer di Dot Launcher"),
                    () -> host.startSafe(new Intent(act, AlarmsActivity.class)), null);
        }

        // 1) calcolatrice
        if (SearchTools.looksLikeMath(query)) {
            Double v = SearchTools.calc(query);
            if (v != null) {
                String res = SearchTools.format(v);
                header(T.t("Calcolatrice"));
                Runnable copy = () -> copy(res);
                bigResult("= " + res, query, copy);
                firstAction = copy;
            }
        }
        // 2) convertitore
        List<String[]> conv = SearchTools.convert(query);
        if (!conv.isEmpty()) {
            header(T.t("Conversione"));
            for (int i = 0; i < Math.min(6, conv.size()); i++) {
                String[] c = conv.get(i);
                Runnable copy = () -> copy(c[0]);
                if (i == 0) bigResult(c[0], c[1], copy);
                else row(null, c[0], c[1], copy, null);
                if (firstAction == null) firstAction = copy;
            }
        }
        // 3) app
        List<HomeActivity.AppEntry> found = new ArrayList<>();
        List<HomeActivity.AppEntry> contains = new ArrayList<>();
        Set<String> hidden = host.hiddenKeys();
        for (HomeActivity.AppEntry a : host.apps()) {
            if (hidden.contains(a.key)) continue;
            String l = DotTextView.normalize(a.label);
            if (l.startsWith(norm) || l.contains(" " + norm)) found.add(a);
            else if (l.contains(norm)) contains.add(a);
        }
        found.addAll(contains);
        if (!found.isEmpty()) {
            firstApp = found.get(0);
            if (firstAction == null) firstAction = () -> host.launchKey(firstApp.key, null);
            appsSection(found.size() > 12 ? found.subList(0, 12) : found, "App");
        }
        hiddenApps(norm);
        // 4) scorciatoie delle app
        if (shortcuts != null && norm.length() >= 2) {
            int n = 0;
            for (ShortcutInfo si : shortcuts) {
                CharSequence l = si.getShortLabel();
                CharSequence ll = si.getLongLabel();
                String hay = DotTextView.normalize((l == null ? "" : l) + " " + (ll == null ? "" : ll));
                if (!hay.contains(norm)) continue;
                if (n == 0) header(T.t("Azioni delle app"));
                String app = appLabel(si.getPackage());
                row(shortcutIcon(si), l == null ? String.valueOf(ll) : String.valueOf(l), app,
                        () -> host.unlockedPkg(si.getPackage(), () -> {
                            try {
                                LauncherApps la = (LauncherApps) act.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                                la.startShortcut(si, null, null);
                            } catch (Exception e) {
                                host.toast(T.t("Azione non disponibile"));
                            }
                        }), null);
                if (++n >= 4) break;
            }
        }
        // 5) impostazioni del telefono
        int sn = 0;
        for (String[] s : SETTINGS) {
            if (!DotTextView.normalize(s[0] + " " + s[2]).contains(norm)) continue;
            if (sn == 0) header(T.t("Impostazioni del telefono"));
            row(SectionIcons.bitmap(SectionIcons.SLIDERS, px(40), th.onTile, th.accent, circle()), s[0], s[2],
                    () -> host.startSafe(new Intent(s[1])), null);
            if (++sn >= 4) break;
        }
        // 6) contatti ed eventi (in background)
        if (norm.length() >= 2) {
            final String q = query;
            run(() -> {
                List<Object[]> contacts = contacts(q);
                List<Object[]> events = events(q);
                List<Object[]> files = files(q);
                ui.post(() -> {
                    if (gen != generation) return;
                    showPeople(contacts, events);
                    showFiles(files);
                    webRow(q);
                });
            });
        } else {
            webRow(query);
        }
    }

    // ---------- sezioni ----------

    private int circle() {
        return Theme.alpha(th.onTile, th.light ? 0.07f : 0.1f);
    }

    private void header(String t) {
        TextView v = new TextView(act);
        v.setText(th.upperLabels ? t.toUpperCase(T.loc()) : t);
        v.setTypeface(th.labelFace);
        v.setTextColor(th.sub);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setLetterSpacing(th.upperLabels ? 0.06f : 0f);
        v.setPadding(px(6), px(16), px(6), px(6));
        col.addView(v);
    }

    private void bigResult(String main, String sub, Runnable click) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(16), px(12), px(16), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.tile);
        bg.setCornerRadius(px(24));
        box.setBackground(bg);
        TextView s = new TextView(act);
        s.setText(sub);
        s.setTextColor(th.sub);
        s.setTypeface(th.bodyFace);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        box.addView(s);
        TextView m = new TextView(act);
        m.setText(main);
        m.setTextColor(th.onTile);
        m.setTypeface(th.dots ? Fonts.dot : th.numFace);
        m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        m.setTextIsSelectable(false);
        box.addView(m);
        TextView hint = new TextView(act);
        hint.setText(T.t("Tocca per copiare"));
        hint.setTextColor(th.accentText);
        hint.setTypeface(th.bodyFace);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.addView(hint);
        box.setOnClickListener(v -> click.run());
        col.addView(box);
    }

    private void row(Bitmap icon, String title, String sub, Runnable click, Runnable longClick) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(6), px(8), px(6), px(8));
        if (icon != null) {
            ImageView iv = new ImageView(act);
            iv.setImageBitmap(icon);
            r.addView(iv, new LinearLayout.LayoutParams(px(40), px(40)));
        }
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(icon != null ? px(14) : px(4), 0, 0, 0);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(th.onTile);
        t.setTypeface(th.bodyFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(act);
            s.setText(sub);
            s.setTextColor(th.sub);
            s.setTypeface(th.bodyFace);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setOnClickListener(v -> click.run());
        if (longClick != null) r.setOnLongClickListener(v -> {
            longClick.run();
            return true;
        });
        col.addView(r);
    }

    private void appsSection(List<HomeActivity.AppEntry> apps, String title) {
        if (apps.isEmpty()) return;
        header(title);
        int cols = TileGrid.COLS;
        LinearLayout row = null;
        int s = px(54 * AppTile.SCALE);
        for (int i = 0; i < apps.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row);
            }
            HomeActivity.AppEntry a = apps.get(i);
            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, px(8), 0, px(10));
            ImageView iv = new ImageView(act);
            iv.setImageBitmap(host.iconFor(a.key, s));
            cell.addView(iv, new LinearLayout.LayoutParams(s, s));
            TextView tv = new TextView(act);
            tv.setText(a.label);
            tv.setTextColor(th.onTile);
            tv.setTypeface(th.bodyFace);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = px(6);
            cell.addView(tv, tl);
            cell.setOnClickListener(v -> host.launchKey(a.key, iv));
            cell.setOnLongClickListener(v -> {
                host.appLongPress(a.key, iv);
                return true;
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (row != null) {
            for (int i = apps.size() % cols; i != 0 && i < cols; i++) {
                row.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    /**
     * App nascoste che corrispondono alla ricerca, solo se l'opzione è attiva e solo cercandole (mai nell'elenco
     * senza testo). Con la protezione attiva compare una riga per sbloccare, uguale che ci siano risultati o no:
     * così non si capisce se esiste un'app nascosta con quel nome.
     */
    private void hiddenApps(String norm) {
        if (!host.hiddenSearch() || norm.length() < 2) return;
        Set<String> hidden = host.hiddenKeys();
        if (hidden.isEmpty()) return;
        if (!host.hiddenOpen()) {
            final String q = query;
            row(SectionIcons.bitmap(SectionIcons.EYE_OFF, px(40), th.onTile, th.accent, circle()),
                    T.t("Cerca anche tra le app nascoste"), T.t("Serve impronta, volto o PIN"),
                    () -> host.unlockHidden(() -> setQuery(q)), null);
            return;
        }
        List<HomeActivity.AppEntry> found = new ArrayList<>();
        for (HomeActivity.AppEntry a : host.apps()) {
            if (!hidden.contains(a.key)) continue;
            if (DotTextView.normalize(a.label).contains(norm)) found.add(a);
        }
        if (found.isEmpty()) return;
        if (firstAction == null) {
            final HomeActivity.AppEntry h = found.get(0);
            firstAction = () -> host.launchKey(h.key, null);
        }
        appsSection(found.size() > 8 ? found.subList(0, 8) : found, T.t("App nascoste"));
    }

    /** Senza testo: tutte le app in ordine alfabetico (come prima). */
    private List<HomeActivity.AppEntry> recentApps() {
        List<HomeActivity.AppEntry> out = new ArrayList<>();
        Set<String> hidden = host.hiddenKeys();
        for (HomeActivity.AppEntry a : host.apps()) if (!hidden.contains(a.key)) out.add(a);
        return out;
    }

    private void webRow(String q) {
        if (q.isEmpty()) return;
        header("Web");
        String eng = host.searchEngine();
        row(SectionIcons.bitmap(SectionIcons.GLOBE, px(40), th.onTile, th.accent, circle()),
                T.t("Cerca «") + q + "»", T.t("con ") + engineName(eng), () -> host.startSafe(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(engineUrl(eng, q)))), null);
        if (firstAction == null) firstAction = () -> host.startSafe(new Intent(Intent.ACTION_VIEW, Uri.parse(engineUrl(eng, q))));
    }

    static final String[][] ENGINES = {
            {"ddg", "DuckDuckGo", "https://duckduckgo.com/?q="},
            {"startpage", "Startpage", "https://www.startpage.com/do/search?q="},
            {"brave", "Brave Search", "https://search.brave.com/search?q="},
            {"ecosia", "Ecosia", "https://www.ecosia.org/search?q="},
            {"qwant", "Qwant", "https://www.qwant.com/?q="},
            {"google", "Google", "https://www.google.com/search?q="},
    };

    static String engineName(String id) {
        for (String[] e : ENGINES) if (e[0].equals(id)) return e[1];
        return ENGINES[0][1];
    }

    static String engineUrl(String id, String q) {
        String base = ENGINES[0][2];
        for (String[] e : ENGINES) if (e[0].equals(id)) base = e[2];
        try {
            return base + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            return base;
        }
    }

    // ---------- contatti ed eventi ----------

    private List<Object[]> contacts(String q) {
        List<Object[]> out = new ArrayList<>();
        if (act.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q));
        String[] proj = {ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER};
        try (Cursor c = act.getContentResolver().query(uri, proj, null, null, null)) {
            while (c != null && c.moveToNext() && out.size() < 4) {
                out.add(new Object[]{c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) > 0});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<Object[]> events(String q) {
        List<Object[]> out = new ArrayList<>();
        if (act.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 7L * 86400000L);
        ContentUris.appendId(b, now + 365L * 86400000L);
        String[] proj = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END};
        try (Cursor c = act.getContentResolver().query(b.build(), proj,
                CalendarContract.Instances.TITLE + " LIKE ?", new String[]{"%" + q + "%"},
                CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext() && out.size() < 4) {
                out.add(new Object[]{c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3)});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------- file: foto, video e audio, solo se l'utente ha dato il permesso nelle impostazioni ----------

    static String[] filePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_AUDIO"};
        }
        return new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    static boolean filesGranted(Context c) {
        for (String p : filePermissions()) if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }

    /** {uri, nome, tipo mime, data} dei file che contengono il testo nel nome; null senza permesso. */
    private List<Object[]> files(String q) {
        if (!filesGranted(act)) return null;
        List<Object[]> out = new ArrayList<>();
        Uri base = android.provider.MediaStore.Files.getContentUri("external");
        String[] proj = {android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.MIME_TYPE, android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE};
        String sel = android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (1,2,3)";
        String like = "%" + q.replace("%", "").replace("_", "") + "%";
        try (Cursor c = act.getContentResolver().query(base, proj, sel, new String[]{like},
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            while (c != null && c.moveToNext() && out.size() < 6) {
                long id = c.getLong(0);
                int type = c.getInt(4);
                Uri coll = type == 1 ? android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        : type == 3 ? android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                out.add(new Object[]{ContentUris.withAppendedId(coll, id), c.getString(1), c.getString(2), c.getLong(3) * 1000L, type});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void showFiles(List<Object[]> files) {
        if (files == null || files.isEmpty()) return;
        header(T.t("File"));
        SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy", T.loc());
        for (Object[] e : files) {
            final Uri u = (Uri) e[0];
            final String mime = (String) e[2];
            int type = (Integer) e[4];
            String[] icon = type == 1 ? Draw.PHOTO : type == 3 ? SectionIcons.FILM : SectionIcons.MUSIC;
            String kind = type == 1 ? T.t("Foto") : type == 3 ? T.t("Video") : T.t("Audio");
            row(SectionIcons.bitmap(icon, px(40), th.onTile, th.accent, circle()), (String) e[1],
                    kind + " · " + f.format(new Date((Long) e[3])), () -> {
                        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime == null ? "*/*" : mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        host.startSafe(i);
                    }, null);
        }
    }

    private void showPeople(List<Object[]> contacts, List<Object[]> events) {
        if (contacts == null) {
            header(T.t("Contatti"));
            row(SectionIcons.bitmap(SectionIcons.PEOPLE, px(40), th.onTile, th.accent, circle()),
                    T.t("Cerca anche nei contatti"), T.t("Tocca per consentire l'accesso ai contatti"), host::requestContacts, null);
        } else if (!contacts.isEmpty()) {
            header(T.t("Contatti"));
            for (Object[] c : contacts) {
                long id = (Long) c[0];
                String lookup = (String) c[1];
                String name = (String) c[2];
                Uri cu = ContactsContract.Contacts.getLookupUri(id, lookup);
                row(letter(name), name, (Boolean) c[3] ? T.t("Tieni premuto per chiamare") : "",
                        () -> host.startSafe(new Intent(Intent.ACTION_VIEW, cu)),
                        (Boolean) c[3] ? () -> dial(id) : null);
            }
        }
        if (events != null && !events.isEmpty()) {
            header(T.t("Eventi"));
            SimpleDateFormat f = new SimpleDateFormat("EEE d MMM · HH:mm", T.loc());
            for (Object[] e : events) {
                long id = (Long) e[0], begin = (Long) e[2], end = (Long) e[3];
                row(SectionIcons.bitmap(SectionIcons.CALENDAR, px(40), th.onTile, th.accent, circle()),
                        e[1] == null ? T.t("(senza titolo)") : (String) e[1], f.format(new Date(begin)), () -> {
                            Intent i = new Intent(Intent.ACTION_VIEW,
                                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id));
                            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
                            i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
                            host.startSafe(i);
                        }, null);
            }
        }
    }

    /** Apre il tastierino con il numero del contatto già scritto (non chiama da solo). */
    private void dial(long contactId) {
        run(() -> {
            String number = null;
            try (Cursor c = act.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                    new String[]{String.valueOf(contactId)}, null)) {
                if (c != null && c.moveToFirst()) number = c.getString(0);
            } catch (Exception ignored) {
            }
            final String n = number;
            ui.post(() -> {
                if (n != null) host.startSafe(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))));
            });
        });
    }

    /** Cerchio con l'iniziale del nome. */
    private Bitmap letter(String name) {
        int s = px(40);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(circle());
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(th.onTile);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setTypeface(th.dots ? Fonts.dot : Fonts.medium);
        p.setTextSize(s * 0.45f);
        String l = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(T.loc());
        c.drawText(l, s / 2f, s / 2f + s * 0.16f, p);
        return b;
    }

    private Bitmap shortcutIcon(ShortcutInfo si) {
        try {
            LauncherApps la = (LauncherApps) act.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            Drawable d = la.getShortcutIconDrawable(si, act.getResources().getDisplayMetrics().densityDpi);
            if (d == null) return null;
            int s = px(40);
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private String appLabel(String pkg) {
        for (HomeActivity.AppEntry a : host.apps()) if (a.component.getPackageName().equals(pkg)) return a.label;
        return pkg;
    }

    /** Sveglia una volta sola: in Dot Sveglia (con le impostazioni dell'ultima sveglia) o nell'app di sistema. */
    private void setAlarm(int h, int m, String hm, boolean dot) {
        if (dot) {
            try {
                java.util.List<DotAlarms.Alarm> list = DotAlarms.load(act);
                DotAlarms.Alarm a = list.isEmpty() ? new DotAlarms.Alarm()
                        : DotAlarms.Alarm.from(list.get(list.size() - 1).json()); // stessa alba, suono e volume
                a.id = DotAlarms.newId(list);
                a.hour = h;
                a.minute = m;
                a.days = 0;
                a.on = true;
                a.name = "";
                a.skipAt = 0;
                a.shiftAt = 0;
                a.shiftMin = 0;
                list.add(a);
                DotAlarms.save(act, list);
                host.toast(T.t("Dot Sveglia alle ") + hm);
                return;
            } catch (Exception ignored) {
                // se qualcosa va storto si passa all'app di sistema
            }
        }
        Intent i = new Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                .putExtra(android.provider.AlarmClock.EXTRA_HOUR, h)
                .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, m)
                .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
        host.startSafe(i);
        host.toast(T.t("Sveglia alle ") + hm);
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("risultato", text));
        host.toast(T.t("Copiato: ") + text);
    }

    /** Impostazioni del telefono raggiungibili dalla ricerca: {nome, azione, parole chiave}. */
    static final String[][] SETTINGS = {
            {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi rete internet wireless network"},
            {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "cuffie auricolari dispositivi headphones earbuds devices"},
            {T.t("Rete mobile"), Settings.ACTION_DATA_ROAMING_SETTINGS, "dati sim roaming 4g 5g data mobile cellular"},
            {T.t("Hotspot e tethering"), "android.settings.TETHER_SETTINGS", "hotspot condivisione tethering share"},
            {T.t("Modalità aereo"), Settings.ACTION_AIRPLANE_MODE_SETTINGS, "aereo volo airplane flight"},
            {"NFC", Settings.ACTION_NFC_SETTINGS, "pagamenti contactless payments"},
            {T.t("Schermo"), Settings.ACTION_DISPLAY_SETTINGS, "display luminosità tema scuro screen brightness dark theme"},
            {T.t("Suoni e vibrazione"), Settings.ACTION_SOUND_SETTINGS, "suoneria volume vibrazione audio sound ringtone vibration"},
            {T.t("Notifiche"), "android.settings.NOTIFICATION_SETTINGS", "avvisi notifications alerts"},
            {T.t("Non disturbare"), "android.settings.ZEN_MODE_SETTINGS", "silenzio dnd do not disturb silence"},
            {T.t("Batteria"), Intent.ACTION_POWER_USAGE_SUMMARY, "consumo energia risparmio battery power saver"},
            {T.t("Memoria"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "archiviazione spazio storage space"},
            {"App", Settings.ACTION_APPLICATION_SETTINGS, "applicazioni gestione app predefinite apps default applications"},
            {T.t("Posizione"), Settings.ACTION_LOCATION_SOURCE_SETTINGS, "gps localizzazione location"},
            {T.t("Sicurezza"), Settings.ACTION_SECURITY_SETTINGS, "blocco schermo impronta pin password security lock fingerprint"},
            {"Privacy", Settings.ACTION_PRIVACY_SETTINGS, "permessi permissions"},
            {"Account", Settings.ACTION_SYNC_SETTINGS, "sincronizzazione accounts sync"},
            {T.t("Accessibilità"), Settings.ACTION_ACCESSIBILITY_SETTINGS, "talkback testo grande accessibility large text"},
            {T.t("Data e ora"), Settings.ACTION_DATE_SETTINGS, "orologio fuso orario date time clock timezone"},
            {T.t("Lingua e tastiera"), Settings.ACTION_LOCALE_SETTINGS, "lingua italiano tastiera language english keyboard"},
            {T.t("Opzioni sviluppatore"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "sviluppatori debug usb developer options"},
            {T.t("Info telefono"), Settings.ACTION_DEVICE_INFO_SETTINGS, "versione android modello about phone version model"},
            {T.t("Launcher predefinito"), Settings.ACTION_HOME_SETTINGS, "home schermata iniziale default launcher home"},
            {T.t("Utilizzo dati"), "android.settings.DATA_USAGE_SETTINGS", "consumo dati traffico data usage"},
            {T.t("Sfondo del telefono"), Intent.ACTION_SET_WALLPAPER, "sfondo wallpaper background"},
            {T.t("Tutte le impostazioni"), Settings.ACTION_SETTINGS, "impostazioni configurazione settings all"},
    };
}
