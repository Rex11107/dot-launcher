package it.rex.dotlauncher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pagina a sinistra della home: blocchi da mostrare, nascondere e riordinare
 * (Chiedi all'IA, Notizie, Calendario, Attività, Note).
 */
class LeftPanel extends FrameLayout {
    interface Host {
        void closePanel();
        void startSafe(Intent i);
        void toast(String s);
        void requestCalendar();
    }

    static final String[] BLOCKS = {"ai", "news", "calendar", "tasks", "notes"};
    static final String DEFAULT_ORDER = "ai,news,calendar,tasks,notes";
    static final String DEFAULT_OFF = "tasks,notes";

    private final Activity act;
    private final Host host;
    private final Theme th;
    private final SharedPreferences prefs, secret;
    private final boolean h24;
    private final float dp;
    private final ScrollView scroll;
    private final LinearLayout col;
    private final LinearLayout blocks;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final int slop;

    LeftPanel(Activity a, Theme th, SharedPreferences prefs, boolean h24, Host host) {
        super(a);
        this.act = a;
        this.th = th;
        this.prefs = prefs;
        this.h24 = h24;
        this.host = host;
        // le chiavi API stanno in un file a parte, escluso dai backup automatici e dall'esportazione
        secret = a.getSharedPreferences("secret", Context.MODE_PRIVATE);
        SharedPreferences.Editor move = null;
        for (String k : new ArrayList<>(prefs.getAll().keySet())) {
            if (!k.startsWith("aiKey_")) continue;
            if (move == null) move = secret.edit();
            move.putString(k, prefs.getString(k, ""));
            prefs.edit().remove(k).apply();
        }
        if (move != null) move.apply();
        dp = a.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
        setBackgroundColor(th.drawerBg);
        setClickable(true);
        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(12), px(16), px(24));
        scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(header());
        blocks = new LinearLayout(a);
        blocks.setOrientation(LinearLayout.VERTICAL);
        col.addView(blocks);
        rebuild();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    void setInsets(int top, int bottom) {
        setPadding(0, top, 0, bottom);
    }

    /** All'apertura: aggiorna notizie (se vecchie) e calendario. */
    void onShown() {
        rebuildHeaderDate();
        refreshNews(false);
        refreshCalendar();
    }

    void shutdown() {
        ui.removeCallbacksAndMessages(null);
        net.shutdownNow();
    }

    private void bg(Runnable r) {
        if (net.isShutdown()) return;
        try {
            net.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    // =====================================================================
    // intestazione e gestione dei blocchi
    // =====================================================================

    private TextView dateView;

    private View header() {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(6), px(8), 0, px(16));
        dateView = new TextView(act);
        dateView.setTextColor(th.onTile);
        dateView.setTypeface(th.dots ? Fonts.dot : th.titleFace);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        dateView.setSingleLine(true);
        rebuildHeaderDate();
        r.addView(dateView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(iconButton(SectionIcons.MORE, v -> editBlocks(), T.t("Modifica la pagina")));
        return r;
    }

    private void rebuildHeaderDate() {
        if (dateView == null) return;
        String d = new SimpleDateFormat("EEE d MMM", T.loc()).format(new Date()).replace(".", "");
        dateView.setText(th.upperLabels ? d.toUpperCase(T.loc()) : d);
    }

    private List<String> order() {
        List<String> out = new ArrayList<>();
        for (String s : prefs.getString("panelOrder", DEFAULT_ORDER).split(",")) {
            if (Arrays.asList(BLOCKS).contains(s) && !out.contains(s)) out.add(s);
        }
        for (String b : BLOCKS) if (!out.contains(b)) out.add(b);
        return out;
    }

    private Set<String> off() {
        return new HashSet<>(Arrays.asList(prefs.getString("panelOff", DEFAULT_OFF).split(",")));
    }

    static String blockName(String b) {
        switch (b) {
            case "ai": return T.t("Chiedi all'IA");
            case "news": return T.t("Notizie");
            case "calendar": return T.t("Calendario");
            case "tasks": return T.t("Attività");
            default: return T.t("Note");
        }
    }

    void rebuild() {
        blocks.removeAllViews();
        newsStrip = null;
        eventsBox = null;
        Set<String> off = off();
        boolean any = false;
        for (String b : order()) {
            if (off.contains(b)) continue;
            any = true;
            View v;
            switch (b) {
                case "ai": v = aiBlock(); break;
                case "news": v = newsBlock(); break;
                case "calendar": v = calendarBlock(); break;
                case "tasks": v = tasksBlock(); break;
                default: v = notesBlock(); break;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = px(12);
            blocks.addView(v, lp);
        }
        if (!any) {
            TextView t = text(T.t("Nessun blocco: tocca ⋮ in alto per sceglierne"), 14, th.sub);
            t.setPadding(px(6), px(20), px(6), 0);
            blocks.addView(t);
        }
        showNews();
    }

    private void editBlocks() {
        List<String> ord = order();
        Set<String> off = off();
        String[] labels = new String[ord.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = (off.contains(ord.get(i)) ? "○  " : "●  ") + blockName(ord.get(i));
        }
        Sheet.list(act, th, T.t("Blocchi della pagina"), labels, -1, w -> {
            String b = ord.get(w);
            boolean hidden = off.contains(b);
            String[] opts = {hidden ? T.t("Mostra") : T.t("Nascondi"), T.t("Sposta su"), T.t("Sposta giù")};
            Sheet.list(act, th, blockName(b), opts, -1, o -> {
                if (o == 0) {
                    if (hidden) off.remove(b);
                    else off.add(b);
                    prefs.edit().putString("panelOff", TextUtils.join(",", off)).apply();
                } else {
                    int i = ord.indexOf(b), j = o == 1 ? i - 1 : i + 1;
                    if (j >= 0 && j < ord.size()) {
                        ord.remove(i);
                        ord.add(j, b);
                        prefs.edit().putString("panelOrder", TextUtils.join(",", ord)).apply();
                    }
                }
                rebuild();
                onShown();
                editBlocks();
            });
        });
    }

    // ---------- elementi grafici comuni ----------

    private LinearLayout card(String title, View... actions) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.tile);
        bg.setCornerRadius(px(28));
        if (th.stroke != 0) bg.setStroke(Math.max(1, px(1)), th.stroke);
        c.setBackground(bg);
        c.setPadding(px(16), px(10), px(16), px(16));
        LinearLayout h = new LinearLayout(act);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(act);
        t.setText(th.upperLabels ? title.toUpperCase(T.loc()) : title);
        t.setTypeface(th.labelFace);
        t.setTextColor(th.sub);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(th.upperLabels ? 0.06f : 0f);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        for (View a : actions) h.addView(a);
        c.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40)));
        return c;
    }

    private ImageView iconButton(String[] pat, View.OnClickListener l, String desc) {
        ImageView iv = new ImageView(act);
        int s = px(38);
        iv.setImageBitmap(SectionIcons.bitmap(pat, s, th.onTile, th.accent, 0));
        iv.setPadding(px(9), px(9), px(9), px(9));
        iv.setOnClickListener(l);
        iv.setContentDescription(desc);
        iv.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return iv;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(th.bodyFace);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setHintTextColor(th.sub);
        e.setTextColor(th.onTile);
        e.setTypeface(th.bodyFace);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.alpha(th.onTile, th.light ? 0.06f : 0.08f));
        bg.setCornerRadius(px(22));
        e.setBackground(bg);
        e.setPadding(px(16), px(11), px(16), px(11));
        return e;
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // =====================================================================
    // Chiedi all'IA
    // =====================================================================

    private final List<String[]> history = new ArrayList<>();
    private LinearLayout aiLog;
    private TextView aiChip;
    private EditText aiInput;
    private boolean aiBusy;

    private String provider() {
        String p = prefs.getString("aiProvider", "claude");
        return Arrays.asList(AiClient.PROVIDERS).contains(p) ? p : "claude";
    }

    private String aiKey(String p) {
        return secret.getString("aiKey_" + p, "");
    }

    /** Risposte direttamente qui solo se c'è una chiave e la modalità API è attiva. */
    private boolean apiMode(String p) {
        return "api".equals(prefs.getString("aiMode_" + p, "app")) && !aiKey(p).isEmpty();
    }

    private View aiBlock() {
        aiChip = new TextView(act);
        aiChip.setTypeface(th.labelFace);
        aiChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        aiChip.setTextColor(th.onTile);
        aiChip.setPadding(px(12), px(5), px(12), px(5));
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(Theme.alpha(th.accent, 0.18f));
        cb.setCornerRadius(px(14));
        aiChip.setBackground(cb);
        aiChip.setOnClickListener(v -> {
            String[] ps = AiClient.PROVIDERS;
            int i = Arrays.asList(ps).indexOf(provider());
            prefs.edit().putString("aiProvider", ps[(i + 1) % ps.length]).apply();
            history.clear();
            updateAiChip();
            renderLog();
        });
        aiChip.setOnLongClickListener(v -> {
            aiSettings();
            return true;
        });
        LinearLayout c = card(T.t("Chiedi"), aiChip, iconButton(SectionIcons.MORE, v -> aiSettings(), T.t("Impostazioni IA")));

        aiLog = new LinearLayout(act);
        aiLog.setOrientation(LinearLayout.VERTICAL);
        c.addView(aiLog);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        aiInput = field("");
        aiInput.setMaxLines(4);
        aiInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        aiInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        aiInput.setOnEditorActionListener((v, id, ev) -> {
            sendAi();
            return true;
        });
        row.addView(aiInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView send = iconButton(SEND, v -> sendAi(), T.t("Invia"));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(px(44), px(44));
        sl.leftMargin = px(6);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setShape(GradientDrawable.OVAL);
        sbg.setColor(th.accent);
        send.setBackground(sbg);
        send.setImageBitmap(SectionIcons.bitmap(SEND, px(44), th.onAccent, th.onAccent, 0));
        row.addView(send, sl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = px(6);
        c.addView(row, rl);
        updateAiChip();
        renderLog();
        return c;
    }

    static final String[] SEND = {
            "....#....", "...###...", "..#####..", ".###.###.", "##..#..##",
            "....#....", "....#....", "....#....", "....#...."};

    private void updateAiChip() {
        if (aiChip == null) return;
        String p = provider();
        String l = AiClient.label(p) + (apiMode(p) ? " · API" : "");
        aiChip.setText(th.upperLabels ? l.toUpperCase(T.loc()) : l);
        if (aiInput != null) {
            aiInput.setHint(apiMode(p) ? T.t("Chiedi a ") + AiClient.label(p) + "…"
                    : T.t("Chiedi a ") + AiClient.label(p) + T.t(" (si apre l'app)…"));
        }
    }

    private void renderLog() {
        if (aiLog == null) return;
        aiLog.removeAllViews();
        if (history.isEmpty()) return;
        for (String[] m : history) {
            boolean user = "user".equals(m[0]);
            TextView t = text(m[1], user ? 13 : 16, user ? th.sub : th.onTile);
            if (!user && looksLikeMath(m[1])) t.setTypeface(Fonts.mono);
            t.setTextIsSelectable(!user);
            t.setPadding(px(4), px(user ? 8 : 2), px(4), px(2));
            aiLog.addView(t);
        }
        if (aiBusy) {
            TextView t = text("· · ·", 16, th.accent);
            t.setPadding(px(4), px(2), px(4), px(2));
            aiLog.addView(t);
        }
        TextView clear = text(T.t("✕  nuova domanda"), 12, th.sub);
        clear.setPadding(px(4), px(8), px(4), px(4));
        clear.setOnClickListener(v -> {
            history.clear();
            renderLog();
        });
        aiLog.addView(clear);
    }

    private static boolean looksLikeMath(String s) {
        int letters = 0, symbols = 0;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) letters++;
            else if ("0123456789=+-×÷*/^√()².,".indexOf(c) >= 0) symbols++;
        }
        return symbols > letters;
    }

    private void sendAi() {
        String q = aiInput.getText().toString().trim();
        if (q.isEmpty() || aiBusy) return;
        String p = provider();
        if (!apiMode(p)) {
            handOff(p, q);
            aiInput.setText("");
            hideKeyboard(aiInput);
            return;
        }
        aiInput.setText("");
        hideKeyboard(aiInput);
        history.add(new String[]{"user", q});
        aiBusy = true;
        renderLog();
        // gli errori (e la domanda che li ha causati) non vengono rimandati al servizio
        final List<String[]> h = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            String[] m = history.get(i);
            boolean failedQ = "user".equals(m[0]) && i + 1 < history.size() && history.get(i + 1)[1].startsWith("⚠");
            if (failedQ || m[1].startsWith("⚠")) continue;
            h.add(m);
        }
        final String key = aiKey(p);
        final String model = prefs.getString("aiModel_" + p, "");
        final String base = prefs.getString("aiBase_" + p, "");
        bg(() -> {
            String ans;
            try {
                ans = AiClient.ask(p, key, model, base, h);
                if (ans.isEmpty()) ans = T.t("(nessuna risposta)");
            } catch (Exception e) {
                ans = "⚠ " + (e.getMessage() == null ? T.t("Errore di rete") : e.getMessage());
            }
            final String a = ans;
            ui.post(() -> {
                aiBusy = false;
                history.add(new String[]{"assistant", a});
                renderLog();
            });
        });
    }

    /** Senza chiave: la domanda passa all'app del servizio (o al suo sito). */
    private void handOff(String p, String q) {
        String text = AiClient.PREFIX + q;
        String pkg = AiClient.appPackage(p);
        if (pkg != null) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                act.startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        String url = AiClient.webUrl(p, text);
        if (url == null) {
            host.toast(T.t("Per ") + AiClient.label(p) + T.t(" serve una chiave API: tocca ⋮"));
            aiSettings();
            return;
        }
        if (!AiClient.webPrefills(p)) {
            ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("domanda", text));
            host.toast(T.t("Domanda copiata: incollala nella chat"));
        }
        host.startSafe(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void aiSettings() {
        String p = provider();
        boolean api = "api".equals(prefs.getString("aiMode_" + p, "app"));
        String key = aiKey(p);
        String model = prefs.getString("aiModel_" + p, "");
        List<String> opts = new ArrayList<>();
        opts.add(T.t("Servizio: ") + AiClient.label(p));
        opts.add(T.t("Modalità: ") + (api ? T.t("risposte qui (chiave API)") : T.t("apri l'app o il sito")));
        opts.add(T.t("Chiave API: ") + (key.isEmpty() ? T.t("nessuna") : "•••• " + key.substring(Math.max(0, key.length() - 4))));
        opts.add(T.t("Modello: ") + (model.isEmpty() ? AiClient.defaultModel(p) + T.t(" (predefinito)") : model));
        if ("custom".equals(p) || "openrouter".equals(p)) {
            String b = prefs.getString("aiBase_" + p, "");
            opts.add(T.t("Indirizzo: ") + (b.isEmpty() ? AiClient.defaultBase(p).isEmpty() ? T.t("da impostare") : AiClient.defaultBase(p) : b));
        }
        if (!key.isEmpty()) opts.add(T.t("Cancella la chiave"));
        Sheet.list(act, th, T.t("Chiedi all'IA"), opts.toArray(new String[0]), -1, w -> {
            String o = opts.get(w);
            if (o.startsWith(T.t("Servizio: "))) {
                String[] ps = AiClient.PROVIDERS;
                String[] names = new String[ps.length];
                for (int i = 0; i < ps.length; i++) names[i] = AiClient.label(ps[i]);
                Sheet.list(act, th, T.t("Servizio"), names, Arrays.asList(ps).indexOf(p), s -> {
                    prefs.edit().putString("aiProvider", ps[s]).apply();
                    history.clear();
                    updateAiChip();
                    renderLog();
                    aiSettings();
                });
            } else if (o.startsWith(T.t("Modalità: "))) {
                if (!api && key.isEmpty()) {
                    host.toast(T.t("Inserisci prima una chiave API"));
                    askKey(p, true);
                    return;
                }
                prefs.edit().putString("aiMode_" + p, api ? "app" : "api").apply();
                updateAiChip();
                aiSettings();
            } else if (o.startsWith(T.t("Chiave API: "))) {
                askKey(p, false);
            } else if (o.startsWith(T.t("Modello: "))) {
                Sheet.input(act, th, T.t("Modello"), AiClient.defaultModel(p).isEmpty() ? T.t("nome del modello")
                        : AiClient.defaultModel(p), model, v -> {
                    prefs.edit().putString("aiModel_" + p, v.trim()).apply();
                    aiSettings();
                });
            } else if (o.startsWith(T.t("Indirizzo: "))) {
                Sheet.input(act, th, T.t("Indirizzo compatibile OpenAI"), "https://…/v1",
                        prefs.getString("aiBase_" + p, ""), v -> {
                            prefs.edit().putString("aiBase_" + p, v.trim()).apply();
                            aiSettings();
                        });
            } else if (o.equals(T.t("Cancella la chiave"))) {
                secret.edit().remove("aiKey_" + p).apply();
                prefs.edit().putString("aiMode_" + p, "app").apply();
                updateAiChip();
                host.toast(T.t("Chiave cancellata"));
            }
        });
    }

    private void askKey(String p, boolean enable) {
        Sheet.input(act, th, T.t("Chiave API di ") + AiClient.label(p), T.t("resta solo su questo telefono"), "", v -> {
            String k = v.trim();
            if (k.isEmpty()) return;
            secret.edit().putString("aiKey_" + p, k).apply();
            if (enable) prefs.edit().putString("aiMode_" + p, "api").apply();
            updateAiChip();
            host.toast(T.t("Chiave salvata solo su questo telefono"));
            aiSettings();
        });
    }

    // =====================================================================
    // Notizie
    // =====================================================================

    private LinearLayout newsStrip;
    private HorizontalScrollView newsScroll;
    private EditText newsSearch;
    private List<NewsFeed.Article> articles = new ArrayList<>();
    private boolean newsLoading;
    private String newsQuery = "";

    private View newsBlock() {
        newsSearch = field(T.t("Cerca tra le notizie"));
        newsSearch.setSingleLine(true);
        newsSearch.setVisibility(View.GONE);
        newsSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                newsQuery = DotTextView.normalize(s.toString().trim());
                showNews();
            }
        });
        LinearLayout c = card(T.t("Notizie"),
                iconButton(SectionIcons.SEARCH, v -> {
                    boolean show = newsSearch.getVisibility() != View.VISIBLE;
                    newsSearch.setVisibility(show ? View.VISIBLE : View.GONE);
                    if (show) {
                        newsSearch.requestFocus();
                        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(newsSearch, InputMethodManager.SHOW_IMPLICIT);
                    } else {
                        newsSearch.setText("");
                        hideKeyboard(newsSearch);
                    }
                }, T.t("Cerca")),
                iconButton(SectionIcons.MORE, v -> newsMenu(), T.t("Fonti")));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.bottomMargin = px(8);
        c.addView(newsSearch, sl);
        newsScroll = new HorizontalScrollView(act);
        newsScroll.setHorizontalScrollBarEnabled(false);
        newsStrip = new LinearLayout(act);
        newsStrip.setOrientation(LinearLayout.HORIZONTAL);
        newsScroll.addView(newsStrip);
        c.addView(newsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (articles.isEmpty()) articles = NewsFeed.fromJson(prefs.getString("newsCache", "[]"));
        return c;
    }

    private List<String[]> newsSources() {
        return NewsFeed.sources(prefs.getString("newsSources", NewsFeed.DEFAULT_SOURCES));
    }

    void refreshNews(boolean force) {
        if (newsStrip == null || newsLoading) return;
        long age = System.currentTimeMillis() - prefs.getLong("newsTime", 0);
        if (!force && age < 30 * 60 * 1000L && !articles.isEmpty()) return;
        newsLoading = true;
        showNews();
        final List<String[]> src = newsSources();
        bg(() -> {
            List<NewsFeed.Article> list = NewsFeed.fetchAll(src);
            ui.post(() -> {
                newsLoading = false;
                if (!list.isEmpty()) {
                    articles = list;
                    prefs.edit().putString("newsCache", NewsFeed.toJson(list))
                            .putLong("newsTime", System.currentTimeMillis()).apply();
                } else if (force) {
                    host.toast(T.t("Notizie non raggiungibili: controlla la connessione"));
                }
                showNews();
            });
        });
    }

    private void showNews() {
        if (newsStrip == null) return;
        newsStrip.removeAllViews();
        List<NewsFeed.Article> shown = new ArrayList<>();
        for (NewsFeed.Article a : articles) {
            if (!newsQuery.isEmpty() && !DotTextView.normalize(a.title + " " + a.source).contains(newsQuery)) continue;
            shown.add(a);
            if (shown.size() >= 30) break;
        }
        if (shown.isEmpty()) {
            TextView t = text(newsLoading ? T.t("Carico le notizie…") : !newsQuery.isEmpty() ? T.t("Nessuna notizia trovata")
                    : T.t("Nessuna notizia: tocca ⋮ per aggiornare o scegliere le fonti"), 14, th.sub);
            t.setPadding(px(4), px(8), px(4), px(8));
            newsStrip.addView(t);
            return;
        }
        for (NewsFeed.Article a : shown) newsStrip.addView(newsCard(a));
    }

    private View newsCard(NewsFeed.Article a) {
        int w = px(220);
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.alpha(th.onTile, th.light ? 0.05f : 0.07f));
        bg.setCornerRadius(px(20));
        c.setBackground(bg);
        c.setClipToOutline(true);
        int ih = px(110);
        if (!a.image.isEmpty()) {
            ImageView iv = new ImageView(act);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = NewsFeed.cached(a.image);
            if (b != null) iv.setImageBitmap(b);
            else {
                bg(() -> {
                    Bitmap bm = NewsFeed.image(a.image, px(240));
                    if (bm != null) ui.post(() -> iv.setImageBitmap(bm));
                });
            }
            c.addView(iv, new LinearLayout.LayoutParams(w, ih));
        }
        TextView src = text((a.source + "  ·  " + ago(a.time)), 11, th.accent);
        src.setTypeface(th.labelFace);
        src.setPadding(px(12), px(10), px(12), 0);
        c.addView(src, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView title = text(a.title, 14, th.onTile);
        title.setMaxLines(a.image.isEmpty() ? 7 : 3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(px(12), px(4), px(12), px(12));
        c.addView(title, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.setMinimumHeight(a.image.isEmpty() ? px(150) : 0);
        c.setOnClickListener(v -> host.startSafe(new Intent(Intent.ACTION_VIEW, Uri.parse(a.link))));
        c.setOnLongClickListener(v -> {
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, a.title + "\n" + a.link);
            host.startSafe(Intent.createChooser(s, T.t("Condividi")));
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = px(10);
        c.setLayoutParams(lp);
        return c;
    }

    private static String ago(long t) {
        if (t <= 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "ora";
        if (m < 60) return m + T.t(" min");
        long h = m / 60;
        if (h < 24) return h + " h";
        if (h < 48) return T.t("ieri");
        return new SimpleDateFormat("d MMM", T.loc()).format(new Date(t));
    }

    private void newsMenu() {
        List<String[]> src = newsSources();
        List<String> opts = new ArrayList<>();
        opts.add(T.t("Aggiorna ora"));
        for (String[] s : src) opts.add("✕  " + s[0]);
        opts.add(T.t("+  Aggiungi una fonte"));
        Sheet.list(act, th, T.t("Fonti delle notizie"), opts.toArray(new String[0]), -1, w -> {
            if (w == 0) {
                refreshNews(true);
            } else if (w == opts.size() - 1) {
                addSource();
            } else {
                String[] s = src.get(w - 1);
                Sheet.confirm(act, th, T.t("Togli la fonte"), T.t("Togliere ") + s[0] + T.t(" dalle notizie?"), T.t("Togli"), () -> {
                    src.remove(w - 1);
                    prefs.edit().putString("newsSources", NewsFeed.join(src)).apply();
                    refreshNews(true);
                });
            }
        });
    }

    private void addSource() {
        List<String[]> src = newsSources();
        List<String[]> avail = new ArrayList<>();
        for (String[] p : NewsFeed.PRESETS) {
            boolean has = false;
            for (String[] s : src) if (s[1].equals(p[1])) has = true;
            if (!has) avail.add(p);
        }
        String[] opts = new String[avail.size() + 1];
        for (int i = 0; i < avail.size(); i++) opts[i] = avail.get(i)[0];
        opts[avail.size()] = T.t("Indirizzo di un feed RSS…");
        Sheet.list(act, th, T.t("Aggiungi una fonte"), opts, -1, w -> {
            if (w < avail.size()) {
                src.add(avail.get(w));
                prefs.edit().putString("newsSources", NewsFeed.join(src)).apply();
                refreshNews(true);
            } else {
                Sheet.input(act, th, "Feed RSS", "https://…/feed", "", url -> {
                    if (!url.startsWith("https://")) {
                        host.toast("Serve un indirizzo che inizi con https://");
                        return;
                    }
                    String name = Uri.parse(url).getHost();
                    if (name == null) name = T.t("Fonte");
                    name = name.replaceFirst("^www\\.", "");
                    src.add(new String[]{name, url});
                    prefs.edit().putString("newsSources", NewsFeed.join(src)).apply();
                    refreshNews(true);
                });
            }
        });
    }

    // =====================================================================
    // Calendario
    // =====================================================================

    private LinearLayout eventsBox;

    private View calendarBlock() {
        LinearLayout c = card(T.t("Calendario"), iconButton(SectionIcons.PLUS, v -> {
            Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
            host.startSafe(i);
        }, T.t("Nuovo evento")));
        Item fake = new Item("calendar", 0, 0, 4, 3, 0, 0);
        NothingTile cal = new NothingTile(act, fake, th, h24);
        cal.setOnClickListener(v -> {
            host.startSafe(HomeActivity.calendarIntent());
        });
        int w = act.getResources().getDisplayMetrics().widthPixels - px(64);
        c.addView(cal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(w * 0.72f)));
        eventsBox = new LinearLayout(act);
        eventsBox.setOrientation(LinearLayout.VERTICAL);
        eventsBox.setPadding(0, px(8), 0, 0);
        c.addView(eventsBox);
        return c;
    }

    void refreshCalendar() {
        if (eventsBox == null) return;
        eventsBox.removeAllViews();
        if (act.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            TextView t = text(T.t("Tocca qui per vedere i tuoi prossimi eventi"), 14, th.accent);
            t.setPadding(px(4), px(6), px(4), px(4));
            t.setOnClickListener(v -> host.requestCalendar());
            eventsBox.addView(t);
            return;
        }
        final long now = System.currentTimeMillis();
        bg(() -> {
            List<Object[]> evs = new ArrayList<>();
            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            ContentUris.appendId(b, start.getTimeInMillis());
            ContentUris.appendId(b, now + 7L * 86400000L);
            String[] proj = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR};
            try (Cursor cur = act.getContentResolver().query(b.build(), proj, null, null,
                    CalendarContract.Instances.BEGIN + " ASC")) {
                while (cur != null && cur.moveToNext() && evs.size() < 6) {
                    long end = cur.getLong(3);
                    if (end < now) continue;
                    evs.add(new Object[]{cur.getLong(0), cur.getString(1), cur.getLong(2), end,
                            cur.getInt(4) == 1, cur.getInt(5)});
                }
            } catch (Exception ignored) {
            }
            ui.post(() -> showEvents(evs));
        });
    }

    private void showEvents(List<Object[]> evs) {
        if (eventsBox == null) return;
        eventsBox.removeAllViews();
        if (evs.isEmpty()) {
            TextView t = text(T.t("Nessun evento nei prossimi 7 giorni"), 14, th.sub);
            t.setPadding(px(4), px(6), px(4), px(4));
            eventsBox.addView(t);
            return;
        }
        SimpleDateFormat tf = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", T.loc());
        SimpleDateFormat df = new SimpleDateFormat("EEE d", T.loc());
        for (Object[] e : evs) {
            long id = (Long) e[0], begin = (Long) e[2], end = (Long) e[3];
            boolean allDay = (Boolean) e[4];
            int color = (Integer) e[5];
            int diff = WData.daysTo(begin);
            String day = diff <= 0 ? T.t("Oggi") : diff == 1 ? T.t("Domani") : df.format(new Date(begin)).replace(".", "");
            String when = day + (allDay ? "" : " · " + tf.format(new Date(begin)));
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(4), px(7), px(4), px(7));
            View dot = new View(act);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color == 0 ? th.accent : (color | 0xFF000000));
            dot.setBackground(g);
            row.addView(dot, new LinearLayout.LayoutParams(px(10), px(10)));
            LinearLayout texts = new LinearLayout(act);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(px(12), 0, 0, 0);
            TextView w = text(th.upperLabels ? when.toUpperCase(T.loc()) : when, 11, diff <= 0 ? th.accent : th.sub);
            w.setTypeface(th.labelFace);
            texts.addView(w);
            TextView t = text(e[1] == null ? T.t("(senza titolo)") : (String) e[1], 15, th.onTile);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(t);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id));
                i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
                i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
                host.startSafe(i);
            });
            eventsBox.addView(row);
        }
    }

    // =====================================================================
    // Attività e note
    // =====================================================================

    private View tasksBlock() {
        LinearLayout c = card(T.t("Attività"), iconButton(SectionIcons.CLOSE, v -> clearDone(), T.t("Togli le completate")));
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        c.addView(list);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            JSONArray a = tasks();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                final int idx = i;
                boolean done = o.optBoolean("d");
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(px(4), px(8), px(4), px(8));
                Check ck = new Check(act, done);
                row.addView(ck, new LinearLayout.LayoutParams(px(22), px(22)));
                TextView t = text(o.optString("t"), 15, done ? th.sub : th.onTile);
                if (done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                t.setPadding(px(12), 0, 0, 0);
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.setOnClickListener(v -> {
                    JSONArray b = tasks();
                    try {
                        b.getJSONObject(idx).put("d", !done);
                    } catch (Exception ignored) {
                    }
                    saveTasks(b);
                    render[0].run();
                });
                row.setOnLongClickListener(v -> {
                    JSONArray b = tasks();
                    b.remove(idx);
                    saveTasks(b);
                    render[0].run();
                    host.toast(T.t("Attività tolta"));
                    return true;
                });
                list.addView(row);
            }
        };
        tasksRender = render[0];
        render[0].run();
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText in = field(T.t("Nuova attività"));
        in.setSingleLine(true);
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Runnable add = () -> {
            String s = in.getText().toString().trim();
            if (s.isEmpty()) return;
            JSONArray b = tasks();
            try {
                b.put(new JSONObject().put("t", s).put("d", false));
            } catch (Exception ignored) {
            }
            saveTasks(b);
            in.setText("");
            render[0].run();
        };
        in.setOnEditorActionListener((v, id, ev) -> {
            add.run();
            return true;
        });
        row.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView plus = iconButton(SectionIcons.PLUS, v -> add.run(), T.t("Aggiungi"));
        row.addView(plus);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = px(6);
        c.addView(row, rl);
        return c;
    }

    private Runnable tasksRender;

    private JSONArray tasks() {
        try {
            return new JSONArray(prefs.getString("tasks", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveTasks(JSONArray a) {
        prefs.edit().putString("tasks", a.toString()).apply();
    }

    private void clearDone() {
        JSONArray a = tasks(), keep = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optBoolean("d")) keep.put(o);
        }
        saveTasks(keep);
        if (tasksRender != null) tasksRender.run();
    }

    /** Casella a puntino: cerchio vuoto o pieno rosso. */
    private class Check extends View {
        final boolean on;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Check(Context c, boolean on) {
            super(c);
            this.on = on;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
            p.setStrokeWidth(px(2));
            p.setColor(on ? th.accent : th.sub);
            cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r - px(2), p);
        }
    }

    private View notesBlock() {
        LinearLayout c = card(T.t("Note"));
        EditText e = field(T.t("Scrivi qui…"));
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setMinLines(4);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(prefs.getString("notes", ""));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString("notes", s.toString()).apply();
            }
        });
        c.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    // =====================================================================
    // scorrimento verso sinistra per chiudere
    // =====================================================================

    private float downX, downY;
    private boolean swiping, noSwipe;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // coordinate dello schermo: quelle locali si spostano insieme al pannello
                downX = ev.getRawX();
                downY = ev.getRawY();
                swiping = false;
                noSwipe = inside(newsScroll, downX, downY) || editTextAt(this, downX, downY);
                break;
            case MotionEvent.ACTION_MOVE:
                if (noSwipe || ev.getPointerCount() > 1) break;
                float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                if (dx < -slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    swiping = true;
                    animate().cancel();
                    return true;
                }
                break;
        }
        return false;
    }

    private static boolean inside(View v, float rx, float ry) {
        if (v == null || !v.isShown()) return false;
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return rx >= l[0] && rx < l[0] + v.getWidth() && ry >= l[1] && ry < l[1] + v.getHeight();
    }

    /** Il dito è su un campo di testo? Lì lo scorrimento orizzontale serve a muovere il cursore. */
    private static boolean editTextAt(View v, float rx, float ry) {
        if (v instanceof EditText) return inside(v, rx, ry);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (editTextAt(g.getChildAt(i), rx, ry)) return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!swiping) return super.onTouchEvent(ev);
        float dx = Math.min(0, ev.getRawX() - downX);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                setTranslationX(dx);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                if (-dx > getWidth() * 0.25f) host.closePanel();
                else animate().translationX(0).setDuration(160).start();
                return true;
        }
        return true;
    }
}
