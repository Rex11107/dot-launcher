package it.rex.dotlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Dot Sveglia: elenco delle sveglie, modifica, lista di controllo e scelta dell'app per il widget. */
public class AlarmsActivity extends Activity {
    private static final int REQ_SOUND = 41, REQ_NOTIF = 42, REQ_FILE = 43;
    private AmbientSound preview;
    private static final int PAGE_LIST = 0, PAGE_EDIT = 1, PAGE_CHECK = 2;

    private SharedPreferences prefs;
    private Theme th;
    private SettingsScreen st;
    private float dp;
    private int page = PAGE_LIST;
    private DotAlarms.Alarm editing;
    private TextView appChip;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("dot", MODE_PRIVATE);
        th = Theme.build(this, prefs);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setBackgroundDrawable(new ColorDrawable(th.bg));
        SystemBars.edgeToEdge(getWindow(), th.light);
        st = new SettingsScreen(this, th, T.t("Sveglia"), this::goBack);
        st.enableSwipeBack(); // aperta dalla home: anche qui Honor può tenersi il gesto indietro
        appChip = new TextView(this);
        appChip.setTextColor(th.onTile);
        appChip.setTypeface(th.bodyFace);
        appChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        appChip.setGravity(Gravity.CENTER);
        appChip.setMinHeight(px(40));
        appChip.setPadding(px(14), px(8), px(14), px(8));
        GradientDrawable cb = new GradientDrawable();
        cb.setCornerRadius(px(20));
        cb.setColor(Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f));
        cb.setStroke(Math.max(1, px(1)), Theme.alpha(th.onTile, 0.25f));
        appChip.setBackground(cb);
        appChip.setOnClickListener(v -> chooseApp());
        st.setAction(appChip);
        setContentView(st);
        st.setOnApplyWindowInsetsListener((v, in) -> {
            int[] r = SystemBars.read(in);
            st.setInsets(r[1], r[3]);
            return in;
        });
        st.requestApplyInsets();
        if (!prefs.getBoolean("alarmWarned", false)) {
            st.post(() -> Sheet.confirm(this, th, T.t("Prima di usare Dot Sveglia"),
                    T.t("Dot Sveglia è nuova e un launcher non è un'app sveglia: su alcuni telefoni il risparmio "
                            + "energetico può ritardarla o bloccarla. Perché suoni sempre servono alcune impostazioni, "
                            + "diverse da telefono a telefono: le trovi nella lista di controllo. Finché non hai fatto "
                            + "una prova completa, tieni anche una sveglia di riserva nell'Orologio."),
                    T.t("Ho capito"), () -> {
                        prefs.edit().putBoolean("alarmWarned", true).apply();
                        page = PAGE_CHECK;
                        build();
                    }));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // al ritorno dalle impostazioni di Android la lista di controllo si aggiorna
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    private void goBack() {
        if (page != PAGE_LIST) {
            page = PAGE_LIST;
            editing = null;
            build();
        } else finish();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private String fmt(int h, int m) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        return DateFormat.getTimeFormat(this).format(c.getTime());
    }

    private void build() {
        appChip.setText(appLabel() + "  ▾");
        appChip.setContentDescription(T.t("App della sveglia: ") + appLabel());
        st.clear();
        if (page == PAGE_EDIT && editing != null) buildEdit();
        else if (page == PAGE_CHECK) buildCheck();
        else buildList();
    }

    // ---------- elenco ----------

    private void buildList() {
        boolean dot = DotAlarms.enabled(this);
        if (!dot) {
            st.warn(SectionIcons.WARN, T.t("Dot Sveglia è spenta"),
                    T.t("Il widget apre ") + appLabel() + T.t(". Le sveglie qui sotto non suonano finché non scegli Dot Sveglia."),
                    () -> setApp("dot"));
        } else if (!DotAlarms.allOk(this)) {
            st.warn(SectionIcons.WARN, T.t("Impostazioni da completare"),
                    T.t("Senza queste la sveglia potrebbe non suonare. Tocca per la lista di controllo."),
                    () -> {
                        page = PAGE_CHECK;
                        build();
                    });
        }
        st.section(T.t("Prossima sveglia"));
        long[] n = DotAlarms.next(this);
        if (n == null || !dot) {
            st.row(SettingsScreen.CLOCK, T.t("Nessuna sveglia attiva"), "", () -> {});
        } else {
            DotAlarms.Alarm a = DotAlarms.find(this, (int) n[0]);
            String when = dayWord(n[1]) + " " + DateFormat.getTimeFormat(this).format(new Date(n[1]));
            long mins = Math.max(0, (n[1] - System.currentTimeMillis()) / 60000L);
            String in = T.t("tra ") + (mins / 60) + " h " + (mins % 60) + " min";
            String dawn = a != null && a.wake > 0 ? " · " + T.t("alba dalle ")
                    + DateFormat.getTimeFormat(this).format(new Date(n[1] - a.wake * 60000L)) : "";
            st.row(SettingsScreen.CLOCK, when, in + dawn, () -> {
                if (a != null) edit(a);
            });
            if (a != null) st.custom(nextButtons(a.id));
        }

        st.section(T.t("Sveglie"));
        List<DotAlarms.Alarm> list = DotAlarms.load(this);
        for (DotAlarms.Alarm a : list) {
            String title = fmt(a.hour, a.minute) + (a.name.isEmpty() ? "" : "  ·  " + a.name);
            long now0 = System.currentTimeMillis();
            String mod = a.skipAt > now0 ? T.t("prossima saltata") + " · "
                    : a.shiftAt > now0 ? T.t("prossima ") + (a.shiftMin > 0 ? "+" : "") + a.shiftMin + " min · " : "";
            String val = (a.on ? "" : T.t("Spenta") + " · ") + mod + daysLabel(a.days)
                    + (a.wake > 0 ? " · " + T.t("alba ") + a.wake + " min" : "");
            st.row(a.on ? SettingsScreen.CLOCK : SectionIcons.PAUSE, title, val, () -> edit(a));
        }
        st.row(SectionIcons.PLUS, T.t("Nuova sveglia"), "", () -> {
            DotAlarms.Alarm a = new DotAlarms.Alarm();
            a.id = DotAlarms.newId(list);
            list.add(a);
            DotAlarms.save(this, list);
            edit(a);
        });

        st.section(T.t("Affidabilità"));
        st.row(SectionIcons.SHIELD, T.t("Lista di controllo"), DotAlarms.allOk(this)
                ? T.t("Tutto a posto") : T.t("Alcune impostazioni da attivare"), () -> {
            page = PAGE_CHECK;
            build();
        });
        st.row(SectionIcons.PLAY, T.t("Prova breve"),
                T.t("Suona tra 1 minuto, con 30 secondi di alba: spegni lo schermo e aspetta"), () -> runTest(1));
        st.row(SectionIcons.PLAY, T.t("Prova lunga"),
                T.t("Suona tra 5 minuti: spegni lo schermo e non toccare il telefono. È la prova più vicina a una notte vera"),
                () -> runTest(5));
        st.note(T.t("La logica della sveglia riprende Fossify Clock e FakeDawn, progetti liberi con licenza GPL-3.0."));
    }

    /** −5 min, +5 min, Salta e Ripristina: valgono solo per la prossima sveglia, le ripetizioni restano uguali. */
    private View nextButtons(int id) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        boolean changed = DotAlarms.hasChanges(this);
        String[] labels = {"−5 min", "+5 min", T.t("Salta"), T.t("Ripristina")};
        String[] desc = {T.t("Anticipa di 5 minuti la prossima sveglia"), T.t("Ritarda di 5 minuti la prossima sveglia"),
                T.t("Salta la prossima sveglia"), T.t("Annulla le modifiche alla prossima sveglia")};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            boolean enabled = i < 3 || changed;
            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(th.bodyFace);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTextColor(enabled ? th.onTile : Theme.alpha(th.onTile, 0.35f));
            t.setMinHeight(px(48));
            t.setContentDescription(desc[i]);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(16));
            g.setStroke(Math.max(1, px(1)), Theme.alpha(th.onTile, enabled ? 0.35f : 0.15f));
            t.setBackground(g);
            t.setEnabled(enabled);
            t.setOnClickListener(v -> {
                if (k == 0) DotAlarms.shiftNext(this, id, -5);
                else if (k == 1) DotAlarms.shiftNext(this, id, 5);
                else if (k == 2) DotAlarms.skipNext(this, id);
                else DotAlarms.resetNext(this);
                build();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = i == 0 ? 0 : px(6);
            r.addView(t, lp);
        }
        return r;
    }

    private String dayWord(long t) {
        Calendar now = Calendar.getInstance(), c = Calendar.getInstance();
        c.setTimeInMillis(t);
        int d = c.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)
                + (c.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 365;
        if (d == 0) return T.t("Oggi alle");
        if (d == 1) return T.t("Domani alle");
        return new java.text.SimpleDateFormat("EEEE", T.loc()).format(new Date(t)) + T.t(" alle");
    }

    private String[] shortDays() {
        String[] w = new DateFormatSymbols(T.loc()).getShortWeekdays();
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) {
            String s = w[order[i]].replace(".", "");
            out[i] = s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase(T.loc()) + s.substring(1, Math.min(3, s.length()));
        }
        return out;
    }

    private String daysLabel(int d) {
        if (d == 0) return T.t("Una volta");
        if (d == 0x7F) return T.t("Ogni giorno");
        if (d == 0x1F) return T.t("Lun–Ven");
        if (d == 0x60) return T.t("Weekend");
        String[] n = shortDays();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((d & (1 << i)) == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(n[i]);
        }
        return sb.toString();
    }

    // ---------- modifica ----------

    private void edit(DotAlarms.Alarm a) {
        editing = a;
        page = PAGE_EDIT;
        build();
    }

    /** Salva la sveglia in modifica e ridisegna. */
    private void commit() {
        List<DotAlarms.Alarm> list = DotAlarms.load(this);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == editing.id) {
                list.set(i, editing);
                found = true;
            }
        }
        if (!found) list.add(editing);
        DotAlarms.save(this, list);
        build();
    }

    private void buildEdit() {
        DotAlarms.Alarm a = editing;
        st.section(T.t("Quando"));
        st.row(SettingsScreen.CLOCK, T.t("Ora"), fmt(a.hour, a.minute), () -> {
            int style = th.light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Dialog_Alert;
            new TimePickerDialog(this, style, (v, h, m) -> {
                a.hour = h;
                a.minute = m;
                commit();
            }, a.hour, a.minute, DateFormat.is24HourFormat(this)).show();
        });
        st.custom(daysRow(a));
        st.toggle(SectionIcons.CALENDAR, T.t("Attiva"), daysLabel(a.days), a.on, on -> {
            a.on = on;
            commit();
        });
        st.row(SectionIcons.NEWS, T.t("Nome"), a.name.isEmpty() ? T.t("Nessuno") : a.name, () ->
                Sheet.input(this, th, T.t("Nome della sveglia"), T.t("Es. Lavoro"), a.name, s -> {
                    a.name = s == null ? "" : s.trim();
                    commit();
                }));

        st.section(T.t("Alba"));
        final int[] wakes = {0, 5, 10, 15, 20, 30};
        String[] wl = new String[wakes.length];
        int wsel = 2;
        for (int i = 0; i < wakes.length; i++) {
            wl[i] = wakes[i] == 0 ? T.t("Nessuna") : wakes[i] + T.t(" minuti prima");
            if (wakes[i] == a.wake) wsel = i;
        }
        final int fw = wsel;
        st.row(Draw.SUN, T.t("Durata dell'alba"), wl[fw], () -> Sheet.list(this, th, T.t("Durata dell'alba"), wl, fw, w -> {
            a.wake = wakes[w];
            commit();
        }));
        String[] cl = new String[DotAlarms.COLORS.length];
        for (int i = 0; i < cl.length; i++) cl[i] = T.t(DotAlarms.COLORS[i][0]);
        st.row(SectionIcons.PALETTE, T.t("Colore della luce"), cl[a.color], () ->
                Sheet.list(this, th, T.t("Colore della luce"), cl, a.color, w -> {
                    a.color = w;
                    commit();
                }));
        final int[] brs = {25, 50, 75, 100};
        String[] bl = {"25%", "50%", "75%", "100%"};
        int bsel = 3;
        for (int i = 0; i < brs.length; i++) if (brs[i] == a.bright) bsel = i;
        final int fb = bsel;
        st.row(Draw.SUN, T.t("Luminosità massima"), bl[fb], () -> Sheet.list(this, th, T.t("Luminosità massima"), bl, fb, w -> {
            a.bright = brs[w];
            commit();
        }));

        st.section(T.t("Suono"));
        st.row(SectionIcons.MUSIC, T.t("Suono"), soundName(a), () -> pickSound(a));
        final int[] vols = {20, 40, 60, 80, 100};
        String[] vl = {"20%", "40%", "60%", "80%", "100%"};
        int vsel = 3;
        for (int i = 0; i < vols.length; i++) if (vols[i] == a.volume) vsel = i;
        final int fv = vsel;
        st.row(SectionIcons.SLIDERS, T.t("Volume massimo"), vl[fv] + T.t(" · sale in un minuto"), () ->
                Sheet.list(this, th, T.t("Volume massimo"), vl, fv, w -> {
                    a.volume = vols[w];
                    commit();
                }));
        st.toggle(SectionIcons.PHONE, T.t("Vibrazione"), T.t("Insieme al suono"), a.vibrate, on -> {
            a.vibrate = on;
            commit();
        });
        String[] keyActs = {T.t("Regolano il volume"), T.t("Posticipa"), T.t("Ferma")};
        st.row(SectionIcons.ARROW_UP, T.t("Tasto volume su"), keyActs[Math.max(0, Math.min(2, a.volUp))], () ->
                Sheet.list(this, th, T.t("Tasto volume su"), keyActs, a.volUp, w -> {
                    a.volUp = w;
                    commit();
                }));
        st.row(SectionIcons.ARROW_DOWN, T.t("Tasto volume giù"), keyActs[Math.max(0, Math.min(2, a.volDown))], () ->
                Sheet.list(this, th, T.t("Tasto volume giù"), keyActs, a.volDown, w -> {
                    a.volDown = w;
                    commit();
                }));

        st.section(T.t("Posticipa"));
        final int[] snz = {0, 3, 5, 10, 15, 20};
        String[] sl = new String[snz.length];
        int ssel = 2;
        for (int i = 0; i < snz.length; i++) {
            sl[i] = snz[i] == 0 ? T.t("Spento") : snz[i] + " min";
            if (snz[i] == a.snooze) ssel = i;
        }
        final int fs = ssel;
        st.row(SectionIcons.PAUSE, T.t("Durata del posticipo"), sl[fs], () ->
                Sheet.list(this, th, T.t("Durata del posticipo"), sl, fs, w -> {
                    a.snooze = snz[w];
                    commit();
                }));
        if (a.snooze > 0) {
            String[] ml = {T.t("L'alba riparte da capo"), T.t("Luce piena fino al suono"), T.t("Schermo spento fino al suono")};
            int msel = Math.max(0, Math.min(2, a.snoozeMode));
            st.row(Draw.SUN, T.t("Durante il posticipo"), ml[msel], () ->
                    Sheet.list(this, th, T.t("Durante il posticipo"), ml, msel, w -> {
                        a.snoozeMode = w;
                        commit();
                    }));
        }

        st.section("");
        st.row(SectionIcons.CLOSE, T.t("Elimina la sveglia"), "", () ->
                Sheet.confirm(this, th, T.t("Eliminare la sveglia?"), fmt(a.hour, a.minute), T.t("Elimina"), () -> {
                    List<DotAlarms.Alarm> list = DotAlarms.load(this);
                    List<DotAlarms.Alarm> keep = new ArrayList<>();
                    for (DotAlarms.Alarm x : list) if (x.id != a.id) keep.add(x);
                    DotAlarms.save(this, keep);
                    goBack();
                }));
    }

    /** I sette giorni come pulsanti da accendere e spegnere. */
    private View daysRow(DotAlarms.Alarm a) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = shortDays();
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            boolean on = (a.days & bit) != 0;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(th.bodyFace);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTextColor(on ? th.onAccent : th.onTile);
            t.setMinHeight(px(48));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(16));
            if (on) g.setColor(th.accent);
            else g.setStroke(Math.max(1, px(1)), Theme.alpha(th.onTile, 0.35f));
            t.setBackground(g);
            t.setContentDescription(names[i] + (on ? T.t(", attivo") : T.t(", non attivo")));
            t.setOnClickListener(v -> {
                a.days ^= bit;
                commit();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = i == 0 ? 0 : px(5);
            r.addView(t, lp);
        }
        return r;
    }

    private String soundName(DotAlarms.Alarm a) {
        String amb = AmbientSound.name(a.sound);
        if (amb != null) return amb;
        if (a.sound.startsWith("content://") && !a.sound.contains("/media/") ) {
            // file scelto dal telefono: si mostra il nome del file
            try (android.database.Cursor cu = getContentResolver().query(Uri.parse(a.sound),
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cu != null && cu.moveToFirst()) return cu.getString(0);
            } catch (Exception ignored) {
            }
        }
        try {
            Ringtone r = RingtoneManager.getRingtone(this, DotAlarms.soundUri(this, a));
            String t = r == null ? null : r.getTitle(this);
            if (t != null) return a.sound.isEmpty() ? T.t("Predefinito: ") + t : t;
        } catch (Exception ignored) {
        }
        return T.t("Suoneria della sveglia");
    }

    /** Suono: suoneria del telefono, un suono ambientale (con anteprima) o un file audio tuo. */
    private void pickSound(DotAlarms.Alarm a) {
        String[] opts = new String[AmbientSound.TYPES.length + 2];
        opts[0] = T.t("Suoneria del telefono…");
        for (int i = 0; i < AmbientSound.TYPES.length; i++) opts[i + 1] = T.t(AmbientSound.TYPES[i][1]);
        opts[opts.length - 1] = T.t("File audio…");
        int sel = -1;
        for (int i = 0; i < AmbientSound.TYPES.length; i++) if (AmbientSound.TYPES[i][0].equals(a.sound)) sel = i + 1;
        Sheet.list(this, th, T.t("Suono della sveglia"), opts, sel, w -> {
            stopPreview();
            if (w == 0) pickRingtone(a);
            else if (w == opts.length - 1) {
                Intent f = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
                try {
                    startActivityForResult(f, REQ_FILE);
                } catch (Exception e) {
                    Toast.makeText(this, T.t("Nessuna app per scegliere file"), Toast.LENGTH_SHORT).show();
                }
            } else {
                a.sound = AmbientSound.TYPES[w - 1][0];
                commit();
                // anteprima di 6 secondi al volume dei contenuti multimediali
                preview = new AmbientSound(a.sound, false);
                preview.start();
                st.postDelayed(this::stopPreview, 6000);
                Toast.makeText(this, T.t("Anteprima: 6 secondi"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopPreview() {
        if (preview != null) {
            preview.stop();
            preview = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
    }

    private void pickRingtone(DotAlarms.Alarm a) {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, T.t("Suono della sveglia"))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, DotAlarms.soundUri(this, a));
        try {
            startActivityForResult(i, REQ_SOUND);
        } catch (Exception e) {
            Toast.makeText(this, T.t("Nessun selettore di suonerie disponibile"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null && editing != null) {
            Uri u = data.getData();
            try {
                // il permesso di leggere il file resta anche dopo il riavvio del telefono
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            editing.sound = u.toString();
            commit();
            return;
        }
        if (req != REQ_SOUND || res != RESULT_OK || data == null || editing == null) return;
        Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        editing.sound = u == null || RingtoneManager.isDefault(u) ? "" : u.toString();
        commit();
    }

    // ---------- lista di controllo ----------

    private String state(boolean ok) {
        return ok ? T.t("✓ Attivo") : T.t("✗ Da attivare: tocca qui");
    }

    private void buildCheck() {
        st.note(T.t("Queste impostazioni dipendono dal telefono. Senza, Android o il risparmio energetico possono "
                + "ritardare o bloccare la sveglia. Torna qui dopo ogni passaggio: lo stato si aggiorna da solo."));
        st.section(T.t("Android"));
        // Da Android 13 il permesso arriva da USE_EXACT_ALARM: concesso all'installazione e non modificabile,
        // per questo nella pagina di sistema l'interruttore è grigio. Si apre la pagina solo se serve davvero.
        boolean auto = Build.VERSION.SDK_INT >= 33 && DotAlarms.exactOk(this);
        st.row(SettingsScreen.CLOCK, T.t("Sveglie e promemoria"),
                auto ? T.t("✓ Concesso automaticamente") : state(DotAlarms.exactOk(this)), () -> {
            if (DotAlarms.exactOk(this)) {
                Toast.makeText(this, T.t("Già a posto: Android lo concede da solo alle app sveglia."),
                        Toast.LENGTH_LONG).show();
            } else if (Build.VERSION.SDK_INT >= 31) {
                open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }
        });
        st.row(Draw.BELL, T.t("Notifiche"), state(DotAlarms.notifOk(this)), () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            } else {
                open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        st.row(SectionIcons.LOCK, T.t("Schermo intero sopra il blocco"), state(DotAlarms.fullScreenOk(this)), () -> {
            if (Build.VERSION.SDK_INT >= 34) open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        });
        st.row(SectionIcons.BATTERY, T.t("Batteria senza restrizioni"), state(DotAlarms.batteryOk(this)), () ->
                open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));
        st.section(T.t("Produttore del telefono"));
        st.row(SectionIcons.GEAR, T.t("Avvio app / esecuzione in background"),
                DotAlarms.autostartOk(this) ? T.t("✓ Fatto") : T.t("✗ Apri, attiva tutto, poi torna e conferma"),
                this::openAutostart);
        st.toggle(SectionIcons.SHIELD, T.t("L'ho attivato"), T.t("Honor/Huawei: Gestisci manualmente, tutti gli interruttori attivi"),
                DotAlarms.autostartOk(this), on -> {
                    prefs.edit().putBoolean("alarmAutostart", on).apply();
                    build();
                });
        st.section(T.t("Verifica"));
        st.row(SectionIcons.PLAY, T.t("Prova breve"),
                T.t("Suona tra 1 minuto, con 30 secondi di alba: spegni lo schermo e aspetta"), () -> runTest(1));
        st.row(SectionIcons.PLAY, T.t("Prova lunga"),
                T.t("Suona tra 5 minuti: spegni lo schermo e non toccare il telefono. È la prova più vicina a una notte vera"),
                () -> runTest(5));
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            open2(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private void open2(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /** Le pagine "avvio automatico" dei produttori più diffusi; se nessuna esiste, le informazioni dell'app. */
    private void openAutostart() {
        String[][] targets = {
                {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
        };
        for (String[] t : targets) {
            try {
                startActivity(new Intent().setComponent(new ComponentName(t[0], t[1]))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        open2(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void runTest(int minutes) {
        if (!DotAlarms.exactOk(this)) {
            Toast.makeText(this, T.t("Prima attiva \"Sveglie e promemoria\""), Toast.LENGTH_LONG).show();
            page = PAGE_CHECK;
            build();
            return;
        }
        DotAlarms.test(this, minutes);
        Toast.makeText(this, minutes == 1 ? T.t("Prova programmata: suona tra 1 minuto. Spegni lo schermo.")
                : T.t("Prova programmata: suona tra 5 minuti. Spegni lo schermo e non toccare il telefono."),
                Toast.LENGTH_LONG).show();
    }

    // ---------- quale app apre il widget ----------

    private String appLabel() {
        String a = prefs.getString(DotAlarms.PREF_APP, "system");
        if ("dot".equals(a)) return T.t("Dot Sveglia");
        if ("system".equals(a)) return T.t("Orologio");
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(a, 0)));
        } catch (Exception e) {
            return T.t("Orologio");
        }
    }

    private void chooseApp() {
        List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        keys.add("dot");
        labels.add(T.t("Dot Sveglia (con alba)"));
        keys.add("system");
        labels.add(T.t("Orologio di sistema"));
        PackageManager pm = getPackageManager();
        for (String action : new String[]{AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || keys.contains(p)) continue;
                keys.add(p);
                labels.add(String.valueOf(ri.loadLabel(pm)));
            }
        }
        int sel = keys.indexOf(prefs.getString(DotAlarms.PREF_APP, "system"));
        Sheet.list(this, th, T.t("Il widget Sveglia usa"), labels.toArray(new String[0]), sel, w -> setApp(keys.get(w)));
    }

    private void setApp(String key) {
        prefs.edit().putString(DotAlarms.PREF_APP, key).apply();
        DotAlarms.schedule(this);
        if ("dot".equals(key) && !prefs.getBoolean("alarmWarned", false)) recreate();
        else build();
    }

    /** Apre la sveglia scelta per il widget: Dot Sveglia, l'Orologio o un'altra app. */
    static void openFromWidget(Activity a) {
        String app = a.getSharedPreferences("dot", MODE_PRIVATE).getString(DotAlarms.PREF_APP, "system");
        Intent i;
        if ("dot".equals(app)) i = new Intent(a, AlarmsActivity.class);
        else if ("system".equals(app)) i = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        else {
            i = new Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage(app);
            if (i.resolveActivity(a.getPackageManager()) == null) i = a.getPackageManager().getLaunchIntentForPackage(app);
        }
        try {
            if (i != null) a.startActivity(i);
        } catch (Exception e) {
            a.startActivity(new Intent(a, AlarmsActivity.class));
        }
    }
}
