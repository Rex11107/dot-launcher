package it.rex.dotlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Prima configurazione guidata: lingua, stile, tema, icone, cassetto, permessi facoltativi e launcher predefinito.
 * Le scelte di aspetto si applicano tutte insieme alla fine (un solo riavvio della home).
 */
class Onboarding extends FrameLayout {
    interface Host {
        void onboardLanguage(String lang);
        void onboardDone(boolean lookChanged);
        void askNotificationAccess();
        void askUsageAccess();
        void askLocation();
        boolean notificationsOn();
        boolean usageOn();
        boolean locationOn();
        boolean isDefaultLauncher();
        void requestDefaultLauncher();
    }

    static final int PAGES = 6;

    private final Theme th;
    private final Host host;
    private final SharedPreferences prefs;
    private final float dp;
    private int page;
    private final LinearLayout body;
    private final LinearLayout dotsRow;
    private final TextView next, skip;

    // scelte (si applicano alla fine)
    private String style, mode, icons, order;

    Onboarding(Context c, Theme th, SharedPreferences prefs, Host host, int startPage) {
        super(c);
        this.th = th;
        this.host = host;
        this.prefs = prefs;
        dp = c.getResources().getDisplayMetrics().density;
        style = prefs.getString("style", "classic");
        mode = prefs.getString("mode", "dark");
        icons = prefs.getString("icons", IconFactory.AUTO);
        order = prefs.getString("drawerOrder", "alpha");
        setBackgroundColor(th.bg);
        setClickable(true); // non lascia passare i tocchi alla home sotto

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(24), px(72), px(24), px(24));
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(20), px(12), px(20), px(28));
        skip = pill(T.t("Salta"), false);
        skip.setOnClickListener(v -> finish());
        bar.addView(skip);
        dotsRow = new LinearLayout(c);
        dotsRow.setGravity(Gravity.CENTER);
        bar.addView(dotsRow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        next = pill(T.t("Avanti"), true);
        next.setOnClickListener(v -> {
            if (page >= PAGES - 1) finish();
            else show(page + 1);
        });
        bar.addView(next);
        col.addView(bar);
        show(Math.max(0, Math.min(PAGES - 1, startPage)));
    }

    /** Spazio per barre di sistema (status bar in alto, gesture bar in basso). */
    void setInsets(int top, int bottom) {
        body.setPadding(px(24), top + px(48), px(24), px(24));
        View bar = (View) next.getParent();
        bar.setPadding(px(20), px(12), px(20), bottom + px(20));
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    // =====================================================================
    // pagine
    // =====================================================================

    private void show(int p) {
        page = p;
        prefs.edit().putInt("onboardPage", p).apply();
        body.removeAllViews();
        switch (p) {
            case 0: welcome(); break;
            case 1: look(); break;
            case 2: iconsPage(); break;
            case 3: drawer(); break;
            case 4: permissions(); break;
            default: launcher(); break;
        }
        next.setText(p >= PAGES - 1 ? T.t("Inizia") : T.t("Avanti"));
        skip.setVisibility(p >= PAGES - 1 ? INVISIBLE : VISIBLE);
        dotsRow.removeAllViews();
        for (int i = 0; i < PAGES; i++) {
            View d = new View(getContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == p ? th.accent : Theme.alpha(th.onTile, 0.25f));
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(i == p ? 8 : 6), px(i == p ? 8 : 6));
            lp.leftMargin = lp.rightMargin = px(4);
            dotsRow.addView(d, lp);
        }
        body.setAlpha(0f);
        body.setTranslationX(px(24));
        body.animate().alpha(1f).translationX(0).setDuration(Motion.ENTER).setInterpolator(Motion.EASE_OUT).start();
    }

    private void welcome() {
        title(T.t("Ciao."));
        text(T.t("Dot Launcher è una home minimale in stile Nothing: widget a puntini, cassetto a sezioni, "
                + "ricerca universale e benessere digitale. Tutto resta sul telefono."));
        header(T.t("Lingua"));
        String lang = prefs.getString("lang", "auto");
        String cur = "auto".equals(lang) ? (T.en ? "en" : "it") : lang;
        option("Italiano", null, "it".equals(cur), () -> {
            if (!"it".equals(cur)) host.onboardLanguage("it");
        });
        option("English", null, "en".equals(cur), () -> {
            if (!"en".equals(cur)) host.onboardLanguage("en");
        });
    }

    private void look() {
        title(T.t("Stile"));
        text(T.t("Puoi cambiare tutto più tardi dalle impostazioni."));
        option(T.t("Classico"), T.t("Classico: puntini e caratteri a matrice"), "classic".equals(style), () -> {
            style = "classic";
            show(page);
        });
        option(T.t("Moderno"), T.t("Moderno: numeri sottili, vetro"), "nuovo".equals(style), () -> {
            style = "nuovo";
            show(page);
        });
        header(T.t("Tema"));
        String[][] modes = {{"dark", "Extra scuro (nero)"}, {"grey", "Scuro (grigio)"}, {"light", "Chiaro"},
                {"auto", "Automatico (segue il sistema)"}};
        for (String[] m : modes) {
            option(T.t(m[1]), null, m[0].equals(mode), () -> {
                mode = m[0];
                show(page);
            });
        }
    }

    private void iconsPage() {
        title(T.t("Icone"));
        text(T.t("Le icone delle app possono diventare monocromatiche, come su Nothing OS, o restare a colori."));
        option(T.t("Monocromatiche"), T.t("Simbolo chiaro su fondo scuro"), IconFactory.AUTO.equals(icons), () -> {
            icons = IconFactory.AUTO;
            show(page);
        });
        option(T.t("Invertite"), T.t("Simbolo scuro su fondo chiaro"), IconFactory.INVERSE.equals(icons), () -> {
            icons = IconFactory.INVERSE;
            show(page);
        });
        option(T.t("A colori"), T.t("Le icone originali delle app"), IconFactory.COLOR.equals(icons), () -> {
            icons = IconFactory.COLOR;
            show(page);
        });
        text(T.t("Forma, dimensione e pacchetti di icone sono in Impostazioni → Personalizzazione."));
    }

    private void drawer() {
        title(T.t("Cassetto"));
        text(T.t("Scorri in su dalla home per aprire il cassetto. Le app sono già divise in sezioni per tipo "
                + "(comunicazione, giochi, strumenti…): puoi rinominarle, crearne di nuove e trascinare le app tra una e l'altra."));
        header(T.t("Ordine delle app"));
        option(T.t("Alfabetico"), T.t("Sempre in ordine, senza pensarci"), "alpha".equals(order), () -> {
            order = "alpha";
            show(page);
        });
        option(T.t("Libero"), T.t("Libero: le disponi trascinandole"), "free".equals(order), () -> {
            order = "free";
            show(page);
        });
    }

    private void permissions() {
        title(T.t("Permessi"));
        text(T.t("Tutti facoltativi: il launcher funziona anche senza. Puoi attivarli quando vuoi."));
        action(T.t("Notifiche"), T.t("Pallini sulle icone e widget della musica"), host.notificationsOn(),
                host::askNotificationAccess);
        action(T.t("Meteo"), T.t("Posizione approssimativa per il meteo (o scegli una città più tardi)"), host.locationOn(),
                host::askLocation);
        action(T.t("Tempo di utilizzo"), T.t("Per il widget del tempo di utilizzo e i limiti delle app"), host.usageOn(),
                host::askUsageAccess);
    }

    private void launcher() {
        title(T.t("Pronto."));
        text(T.t("Ultimo passo: rendi Dot Launcher la tua home, così il tasto Home porta qui."));
        action(T.t("Launcher predefinito"), T.t("Scegli Dot Launcher come schermata Home"), host.isDefaultLauncher(),
                host::requestDefaultLauncher);
        header(T.t("Qualche gesto"));
        text(T.t("Tieni premuto su uno spazio vuoto per widget e sfondi.\nTieni premuta un'app per spostarla o vedere le opzioni.\n"
                + "Scorri a destra dalla prima pagina per IA, notizie e calendario.\nPizzica la home per le impostazioni."));
    }

    /** Ricarica la pagina corrente (es. dopo aver concesso un permesso). */
    void refresh() {
        if (page == 4 || page == 5) show(page);
    }

    private void finish() {
        boolean changed = !style.equals(prefs.getString("style", "classic"))
                || !mode.equals(prefs.getString("mode", "dark"))
                || !icons.equals(prefs.getString("icons", IconFactory.AUTO));
        prefs.edit().putString("style", style).putString("mode", mode).putString("icons", icons)
                .putString("drawerOrder", order).putBoolean("onboarded", true).remove("onboardPage").apply();
        host.onboardDone(changed);
    }

    // =====================================================================
    // mattoncini
    // =====================================================================

    private void title(String s) {
        View t;
        if (th.dots && DotFont.supports(s)) {
            DotTextView dt = new DotTextView(getContext());
            dt.setPitch(5f * dp);
            dt.setFitWidth(true, 5f * dp);
            dt.setColor(th.onTile);
            dt.setAccent(".", th.accent);
            dt.setText(s);
            t = dt;
        } else {
            TextView tv = new TextView(getContext());
            tv.setText(s);
            tv.setTypeface(th.titleFace);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
            tv.setTextColor(th.onTile);
            t = tv;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(16);
        body.addView(t, lp);
    }

    private void text(String s) {
        TextView tv = new TextView(getContext());
        tv.setText(s);
        tv.setTypeface(th.bodyFace);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(th.sub);
        tv.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(14);
        body.addView(tv, lp);
    }

    private void header(String s) {
        TextView t = new TextView(getContext());
        t.setText(th.upperLabels ? s.toUpperCase(T.loc()) : s);
        t.setTypeface(th.labelFace);
        t.setTextColor(th.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(th.upperLabels ? 0.06f : 0f);
        t.setPadding(px(4), px(18), 0, px(8));
        body.addView(t);
    }

    private LinearLayout card(boolean selected) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(18), px(14), px(16), px(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(22));
        g.setColor(Theme.alpha(th.onTile, th.light ? 0.05f : 0.07f));
        if (selected) g.setStroke(px(2), th.accent);
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(10);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout texts(String title, String sub) {
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTypeface(Fonts.medium);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(th.onTile);
        tx.addView(t);
        if (sub != null) {
            TextView s = new TextView(getContext());
            s.setText(sub);
            s.setTypeface(th.bodyFace);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            s.setTextColor(th.sub);
            s.setPadding(0, px(2), 0, 0);
            tx.addView(s);
        }
        return tx;
    }

    private void option(String title, String sub, boolean selected, Runnable onPick) {
        LinearLayout c = card(selected);
        c.addView(texts(title, sub), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View dot = new View(getContext());
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (selected) g.setColor(th.accent);
        else g.setStroke(px(2), Theme.alpha(th.onTile, 0.3f));
        dot.setBackground(g);
        c.addView(dot, new LinearLayout.LayoutParams(px(16), px(16)));
        c.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            onPick.run();
        });
        body.addView(c);
    }

    private void action(String title, String sub, boolean done, Runnable act) {
        LinearLayout c = card(false);
        c.addView(texts(title, sub), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = pill(done ? T.t("Fatto") : T.t("Consenti"), !done);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setPadding(px(14), px(8), px(14), px(8));
        if (!done) b.setOnClickListener(v -> act.run());
        c.addView(b);
        body.addView(c);
    }

    private TextView pill(String text, boolean primary) {
        TextView b = new TextView(getContext());
        b.setText(text);
        b.setTypeface(Fonts.medium);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? th.onAccent : th.onTile);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(22), px(12), px(22), px(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(26));
        g.setColor(primary ? th.accent : Theme.alpha(th.onTile, 0.08f));
        b.setBackground(g);
        return b;
    }
}
