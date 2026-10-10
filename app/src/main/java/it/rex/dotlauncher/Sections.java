package it.rex.dotlauncher;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sezioni del cassetto delle app. Ogni sezione contiene app e contenitori (cartelle con un nome).
 * Salvate in prefs come JSON ("sections").
 */
final class Sections {
    static final class Entry {
        String app;          // app singola (chiave)
        String name;         // contenitore: nome
        List<String> apps;   // contenitore: chiavi delle app

        boolean isFolder() {
            return apps != null;
        }
    }

    static final class Section {
        String name, icon, cat;
        final List<Entry> entries = new ArrayList<>();
    }

    final List<Section> list = new ArrayList<>();
    private final SharedPreferences prefs;

    Sections(SharedPreferences prefs) {
        this.prefs = prefs;
        load();
    }

    // ---------- salvataggio ----------

    private void load() {
        list.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString("sections", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Section s = new Section();
                s.name = o.optString("n", "Sezione");
                s.icon = o.optString("i", "shapes");
                s.cat = o.has("c") ? o.optString("c") : null;
                JSONArray e = o.optJSONArray("e");
                if (e != null) {
                    for (int j = 0; j < e.length(); j++) {
                        JSONObject x = e.getJSONObject(j);
                        Entry en = new Entry();
                        if (x.has("l")) {
                            en.name = x.optString("f", "Contenitore");
                            en.apps = new ArrayList<>();
                            JSONArray l = x.getJSONArray("l");
                            for (int k = 0; k < l.length(); k++) en.apps.add(l.getString(k));
                        } else {
                            en.app = x.optString("a", "");
                            if (en.app.isEmpty()) continue;
                        }
                        s.entries.add(en);
                    }
                }
                list.add(s);
            }
        } catch (Exception e) {
            list.clear();
        }
    }

    void save() {
        try {
            JSONArray a = new JSONArray();
            for (Section s : list) {
                JSONObject o = new JSONObject();
                o.put("n", s.name);
                o.put("i", s.icon);
                if (s.cat != null) o.put("c", s.cat);
                JSONArray e = new JSONArray();
                for (Entry en : s.entries) {
                    JSONObject x = new JSONObject();
                    if (en.isFolder()) {
                        x.put("f", en.name);
                        JSONArray l = new JSONArray();
                        for (String k : en.apps) l.put(k);
                        x.put("l", l);
                    } else x.put("a", en.app);
                    e.put(x);
                }
                o.put("e", e);
                a.put(o);
            }
            prefs.edit().putString("sections", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------- sezioni predefinite e smistamento automatico ----------

    private static Section make(String name, String icon, String cat) {
        Section s = new Section();
        s.name = name;
        s.icon = icon;
        s.cat = cat;
        return s;
    }

    private void defaults() {
        list.add(make("Comunicazione", "chat", "com"));
        list.add(make("Internet", "globe", "web"));
        list.add(make("Multimedia", "photo", "media"));
        list.add(make("Intrattenimento", "tv", "fun"));
        list.add(make("Giochi", "pad", "games"));
        list.add(make("Istruzione", "cap", "edu"));
        list.add(make("Finanze e banche", "money", "fin"));
        list.add(make("Strumenti", "gear", "tools"));
        list.add(make("Varie", "shapes", "misc"));
    }

    private static final String[][] WORDS = {
            {"fin", "bank", "banca", "banco", "paypal", "poste", "bancoposta", "revolut", "satispay", "intesa",
                    "unicredit", "bnl", "fineco", "mediolanum", "n26", "hype", "bper", "credem", "binance",
                    "coinbase", "crypto", "trading", "finanz", "finance", "wallet", "nexi", "amex", "isybank",
                    "widiba", "webank", "illimity", "carige", "sella", "postepay", "mooney", "klarna", "scalapay",
                    "wise", "trade republic", "traderepublic", "directa", "degiro", "etoro", "money", "soldi"},
            {"com", "whatsapp", "telegram", "org.thoughtcrime", "signal", "messenger", "com.facebook.orca",
                    "android.gm", "gmail", "mail", "email", "contacts", "contatti", "dialer", "telefono",
                    "messag", "mms", "sms", "teams", "zoom", "skype", "meet", "viber", "threema", "element",
                    "phone", "outlook", "k9", "thunderbird", "fairemail"},
            {"edu", "duolingo", "classroom", "school", "scuola", "learn", "quiz", "patente", "dizionario",
                    "dictionary", "anki", "khan", "coursera", "udemy", "spaggiari", "classeviva", "argo",
                    "moodle", "university", "universit", "studio", "study", "photomath", "wolfram", "babbel"},
            {"games", "minecraft", "steam", "xbox", "playstation", "games.", ".game", "gaijin", "supercell",
                    "roblox", "epicgames", "warships", "age of"},
            {"web", "browser", "chrome", "firefox", "mozilla", "brave", "opera", "duckduckgo", "vivaldi",
                    "maps", "booking", "airbnb", "trenitalia", "italo", "flixbus", "koleo", "trenit", "atbus",
                    "translate", "traduttore", "vpn", "googlequicksearchbox", "github", "moovit", "uber",
                    "freenow", "skyscanner", "ryanair", "chatbox", "chatgpt", "claude", "gemini", "ebay",
                    "amazon.mshop", "shopping", "vinted", "subito"},
            {"fun", "youtube", "netflix", "primevideo", "prime video", "disney", "twitch", "tiktok",
                    "musically", "instagram", "reddit", "discord", "facebook", "twitter", "video",
                    "cinema", "jellyfin", "pixiv", "crunchyroll", "dazn", "mediaset", "raiplay", "plex",
                    "libretube", "newpipe", "pinterest", "tumblr", "bluesky", "mastodon", "threads", "snapchat",
                    "janitor", "vidaa", "uci cinemas"},
            {"media", "camera", "fotocamera", "gallery", "galleria", "photo", "foto", "music", "musica",
                    "recorder", "registrator", "spotify", "snapseed", "immich", "image", "aves", "lightroom",
                    "picsart", "audio", "podcast", "soundcloud", "deezer", "tidal", "outertune", "retro"},
            {"tools", "settings", "impostazioni", "calcul", "calcol", "clock", "orologio", "files", "file manager",
                    "weather", "meteo", "notes", "note", "calendar", "calendario", "keep", "drive", "docs",
                    "office", "scanner", "torch", "torcia", "compass", "bussola", "backup", "launcher",
                    "security", "authenticator", "password", "bitwarden", "proton", "nextcloud", "vending",
                    "fdroid", "droidify", "aurora", "termux", "toolkit", "manager"},
    };

    /** Categoria per un'app: prima per nome/pacchetto (più preciso), poi per categoria di sistema. */
    static String categorize(HomeActivity.AppEntry a) {
        String hay = (a.component.getPackageName() + " " + a.label).toLowerCase(Locale.ROOT);
        for (String[] group : WORDS) {
            for (int i = 1; i < group.length; i++) {
                if (hay.contains(group[i])) return group[0];
            }
        }
        if (a.game) return "games";
        switch (a.cat) {
            case ApplicationInfo.CATEGORY_GAME: return "games";
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_IMAGE: return "media";
            case ApplicationInfo.CATEGORY_VIDEO: return "fun";
            case ApplicationInfo.CATEGORY_SOCIAL: return "fun";
            case ApplicationInfo.CATEGORY_NEWS:
            case ApplicationInfo.CATEGORY_MAPS: return "web";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
            case ApplicationInfo.CATEGORY_ACCESSIBILITY: return "tools";
            default: return "misc";
        }
    }

    private Section sectionForCat(String cat) {
        for (Section s : list) if (cat.equals(s.cat)) return s;
        for (Section s : list) if ("misc".equals(s.cat)) return s;
        if (list.isEmpty()) {
            list.add(make("Varie", "shapes", "misc"));
        }
        return list.get(list.size() - 1);
    }

    /**
     * Allinea le sezioni alle app installate: toglie quelle disinstallate e smista le nuove.
     * Restituisce true se qualcosa è cambiato.
     */
    boolean sync(List<HomeActivity.AppEntry> apps) {
        if (apps.isEmpty()) return false; // elenco non ancora pronto: non toccare nulla
        boolean changed = false;
        if (list.isEmpty()) {
            defaults();
            changed = true;
        }
        Set<String> installed = new HashSet<>();
        for (HomeActivity.AppEntry a : apps) installed.add(a.key);
        Set<String> placed = new HashSet<>();
        for (Section s : list) {
            for (int i = s.entries.size() - 1; i >= 0; i--) {
                Entry e = s.entries.get(i);
                if (e.isFolder()) {
                    List<String> keep = new ArrayList<>();
                    for (String k : e.apps) if (installed.contains(k) && placed.add(k)) keep.add(k);
                    if (keep.size() != e.apps.size()) {
                        e.apps = keep;
                        changed = true;
                    }
                    if (keep.isEmpty()) {
                        s.entries.remove(i);
                        changed = true;
                    } else if (keep.size() == 1) {
                        e.app = keep.get(0);
                        e.apps = null;
                        e.name = null;
                        changed = true;
                    }
                } else if (!installed.contains(e.app) || !placed.add(e.app)) {
                    s.entries.remove(i);
                    changed = true;
                }
            }
        }
        for (HomeActivity.AppEntry a : apps) {
            if (placed.contains(a.key)) continue;
            Entry e = new Entry();
            e.app = a.key;
            sectionForCat(categorize(a)).entries.add(e);
            changed = true;
        }
        return changed;
    }

    // ---------- operazioni ----------

    Section sectionOf(String key) {
        for (Section s : list) {
            for (Entry e : s.entries) {
                if (key.equals(e.app) || (e.isFolder() && e.apps.contains(key))) return s;
            }
        }
        return null;
    }

    Entry folderOf(String key) {
        for (Section s : list) for (Entry e : s.entries) if (e.isFolder() && e.apps.contains(key)) return e;
        return null;
    }

    /** Toglie l'app da dove si trova. Un contenitore rimasto con una sola app torna app singola. */
    void removeKey(String key) {
        for (Section s : list) {
            for (int i = s.entries.size() - 1; i >= 0; i--) {
                Entry e = s.entries.get(i);
                if (key.equals(e.app)) {
                    s.entries.remove(i);
                } else if (e.isFolder() && e.apps.remove(key)) {
                    if (e.apps.isEmpty()) s.entries.remove(i);
                    else if (e.apps.size() == 1) {
                        e.app = e.apps.get(0);
                        e.apps = null;
                        e.name = null;
                    }
                }
            }
        }
    }

    /** Sposta l'app nella sezione, accanto a ref (prima o dopo); ref null = in fondo. */
    void moveTo(String key, Section s, Entry ref, boolean after) {
        if (ref != null && key.equals(ref.app)) return;
        removeKey(key);
        Entry e = new Entry();
        e.app = key;
        int idx = ref == null ? -1 : s.entries.indexOf(ref);
        if (idx < 0) s.entries.add(e);
        else s.entries.add(after ? idx + 1 : idx, e);
    }

    /** Lascia l'app sopra un'altra app (nasce un contenitore) o sopra un contenitore. */
    boolean merge(String key, Entry target) {
        if (key.equals(target.app)) return false;
        if (target.isFolder() && target.apps.contains(key)) return false;
        removeKey(key);
        if (target.isFolder()) {
            target.apps.add(key);
        } else {
            target.apps = new ArrayList<>();
            target.apps.add(target.app);
            target.apps.add(key);
            target.app = null;
            target.name = "Contenitore";
        }
        return true;
    }

    /** Scioglie un contenitore: le sue app tornano singole al suo posto. */
    void dissolve(Section s, Entry f) {
        int idx = s.entries.indexOf(f);
        if (idx < 0 || !f.isFolder()) return;
        s.entries.remove(idx);
        for (int i = f.apps.size() - 1; i >= 0; i--) {
            Entry e = new Entry();
            e.app = f.apps.get(i);
            s.entries.add(idx, e);
        }
    }

    void moveEntry(Entry e, Section from, Section to) {
        if (from == to) return;
        from.entries.remove(e);
        to.entries.add(e);
    }

    Section sectionOfEntry(Entry e) {
        for (Section s : list) if (s.entries.contains(e)) return s;
        return null;
    }

    Section add(String name, String icon) {
        Section s = make(name, icon, null);
        list.add(s);
        return s;
    }

    /** Elimina una sezione: le sue app vanno in "Varie" (o nella prima sezione rimasta). */
    boolean remove(Section s) {
        if (list.size() <= 1) return false;
        list.remove(s);
        Section dest = null;
        for (Section o : list) if ("misc".equals(o.cat)) dest = o;
        if (dest == null) dest = list.get(list.size() - 1);
        dest.entries.addAll(s.entries);
        if (s.cat != null && dest.cat == null) dest.cat = s.cat;
        return true;
    }

    void swap(int i, int j) {
        if (i < 0 || j < 0 || i >= list.size() || j >= list.size()) return;
        Collections.swap(list, i, j);
    }

    /** Elementi da mostrare: senza app nascoste, in ordine alfabetico o libero. */
    static List<Entry> visible(Section s, Set<String> hidden, boolean alpha, LabelSource labels) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : s.entries) {
            if (e.isFolder()) {
                boolean any = false;
                for (String k : e.apps) if (!hidden.contains(k)) any = true;
                if (any) out.add(e);
            } else if (!hidden.contains(e.app)) out.add(e);
        }
        if (alpha) {
            final Collator col = Collator.getInstance(Locale.ITALIAN);
            Collections.sort(out, (x, y) -> col.compare(
                    x.isFolder() ? x.name : labels.labelFor(x.app),
                    y.isFolder() ? y.name : labels.labelFor(y.app)));
        }
        return out;
    }

    interface LabelSource {
        String labelFor(String key);
    }
}
