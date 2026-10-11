package it.rex.dotlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Html;
import android.util.LruCache;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Notizie dai feed RSS/Atom delle testate: nessun intermediario, nessuna chiave. */
final class NewsFeed {
    static final class Article {
        String title = "", link = "", source = "", image = "";
        long time;
    }

    /** Fonti suggerite: {nome, indirizzo} (in inglese si propongono testate internazionali). */
    static final String[][] PRESETS = T.en ? new String[][]{
            {"BBC News", "https://feeds.bbci.co.uk/news/rss.xml"},
            {"The Guardian", "https://www.theguardian.com/international/rss"},
            {"Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"},
            {"The Verge", "https://www.theverge.com/rss/index.xml"},
            {"NPR", "https://feeds.npr.org/1001/rss.xml"},
            {"ANSA", "https://www.ansa.it/sito/ansait_rss.xml"},
            {"Il Post", "https://www.ilpost.it/feed/"},
    } : new String[][]{
            {"ANSA", "https://www.ansa.it/sito/ansait_rss.xml"},
            {"Il Post", "https://www.ilpost.it/feed/"},
            {"HDblog", "https://www.hdblog.it/feed/"},
            {"Wired Italia", "https://www.wired.it/feed/rss"},
            {"Il Fatto Quotidiano", "https://www.ilfattoquotidiano.it/feed/"},
            {"Internazionale", "https://www.internazionale.it/sitemaps/rss.xml"},
    };
    static final String DEFAULT_SOURCES = T.en
            ? "BBC News|https://feeds.bbci.co.uk/news/rss.xml\nThe Guardian|https://www.theguardian.com/international/rss"
            : "ANSA|https://www.ansa.it/sito/ansait_rss.xml\n"
            + "Il Post|https://www.ilpost.it/feed/";

    private static final Pattern IMG = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    static List<String[]> sources(String s) {
        List<String[]> out = new ArrayList<>();
        for (String line : s.split("\n")) {
            String[] kv = line.split("\\|", 2);
            if (kv.length == 2 && !kv[1].trim().isEmpty()) out.add(new String[]{kv[0].trim(), kv[1].trim()});
        }
        return out;
    }

    static String join(List<String[]> src) {
        StringBuilder sb = new StringBuilder();
        for (String[] kv : src) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(kv[0].replace("|", " ")).append('|').append(kv[1]);
        }
        return sb.toString();
    }

    /** Scarica tutte le fonti e unisce gli articoli, i più recenti per primi. */
    static List<Article> fetchAll(List<String[]> sources) {
        List<Article> all = new ArrayList<>();
        for (String[] s : sources) {
            try {
                all.addAll(fetch(s[0], s[1]));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(all, (a, b) -> Long.compare(b.time, a.time));
        return all.size() > 60 ? new ArrayList<>(all.subList(0, 60)) : all;
    }

    static List<Article> fetch(String name, String url) throws Exception {
        byte[] data = download(url, 3 * 1024 * 1024);
        XmlPullParser p = Xml.newPullParser();
        try {
            p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true);
        } catch (Exception ignored) {
        }
        p.setInput(new java.io.ByteArrayInputStream(data), null);
        List<Article> out = new ArrayList<>();
        Article cur = null;
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT && out.size() < 20) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
                if (tag.equals("item") || tag.equals("entry")) {
                    cur = new Article();
                    cur.source = name;
                } else if (cur != null) {
                    switch (tag) {
                        case "title":
                            cur.title = clean(readText(p));
                            break;
                        case "link": {
                            String href = p.getAttributeValue(null, "href");
                            String rel = p.getAttributeValue(null, "rel");
                            if (href != null) {
                                if (cur.link.isEmpty() || "alternate".equals(rel)) cur.link = href;
                            } else {
                                String t = readText(p).trim();
                                if (!t.isEmpty()) cur.link = t;
                            }
                            break;
                        }
                        case "pubdate":
                        case "published":
                        case "updated":
                        case "dc:date":
                            if (cur.time == 0) cur.time = parseDate(readText(p).trim());
                            break;
                        case "media:content":
                        case "media:thumbnail":
                        case "enclosure": {
                            String u = p.getAttributeValue(null, "url");
                            String type = p.getAttributeValue(null, "type");
                            if (u != null && cur.image.isEmpty() && (type == null || type.startsWith("image")
                                    || u.matches("(?i).*\\.(jpe?g|png|webp)(\\?.*)?"))) cur.image = u;
                            break;
                        }
                        case "thumb":
                        case "image": {
                            String t = readText(p).trim();
                            if (cur.image.isEmpty() && t.startsWith("http")) cur.image = t;
                            break;
                        }
                        case "description":
                        case "summary":
                        case "content":
                        case "content:encoded": {
                            String t = readText(p);
                            if (cur.image.isEmpty()) {
                                Matcher m = IMG.matcher(t);
                                if (m.find()) cur.image = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString();
                            }
                            break;
                        }
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && cur != null) {
                String tag = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
                if (tag.equals("item") || tag.equals("entry")) {
                    if (!cur.title.isEmpty() && cur.link.startsWith("http")) out.add(cur);
                    cur = null;
                }
            }
            ev = p.next();
        }
        return out;
    }

    /** Testo di un elemento, comprese eventuali parti annidate (HTML non racchiuso in CDATA). */
    private static String readText(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.START_TAG) {
                depth++;
                if ("img".equalsIgnoreCase(p.getName())) {
                    String src = p.getAttributeValue(null, "src");
                    if (src != null) sb.append("<img src=\"").append(src).append("\">");
                }
            } else if (ev == XmlPullParser.END_TAG) depth--;
            else if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) sb.append(p.getText());
            else if (ev == XmlPullParser.END_DOCUMENT) break;
        }
        return sb.toString();
    }

    private static String clean(String s) {
        String t = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        // alcuni feed sono codificati due volte (&amp;rsquo;)
        if (t.contains("&") && t.contains(";")) t = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString();
        return t.replaceAll("\\s+", " ").trim();
    }

    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
            "dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss"
    };

    static long parseDate(String s) {
        for (String f : DATE_FORMATS) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(f, Locale.ENGLISH);
                if (f.endsWith("'Z'")) df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date d = df.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static byte[] download(String url, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) DotLauncher");
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (loc != null && loc.startsWith("https")) return download(loc, max);
            throw new Exception("redirect");
        }
        if (code >= 400) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > max) break;
            }
            return bo.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    // ---------- immagini ----------

    private static final LruCache<String, Bitmap> IMAGES = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    static Bitmap cached(String url) {
        return url == null || url.isEmpty() ? null : IMAGES.get(url);
    }

    static Bitmap image(String url, int target) {
        if (url == null || !url.startsWith("http")) return null;
        Bitmap b = IMAGES.get(url);
        if (b != null) return b;
        try {
            byte[] d = download(url, 4 * 1024 * 1024);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(d, 0, d.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= target) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            b = BitmapFactory.decodeByteArray(d, 0, d.length, o2);
            if (b != null) IMAGES.put(url, b);
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    // ---------- cache su disco (prefs) ----------

    static String toJson(List<Article> list) {
        JSONArray a = new JSONArray();
        try {
            for (Article x : list) {
                a.put(new JSONObject().put("t", x.title).put("l", x.link).put("s", x.source)
                        .put("i", x.image).put("d", x.time));
            }
        } catch (Exception ignored) {
        }
        return a.toString();
    }

    static List<Article> fromJson(String s) {
        List<Article> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Article x = new Article();
                x.title = o.optString("t");
                x.link = o.optString("l");
                x.source = o.optString("s");
                x.image = o.optString("i");
                x.time = o.optLong("d");
                out.add(x);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private NewsFeed() {}
}
