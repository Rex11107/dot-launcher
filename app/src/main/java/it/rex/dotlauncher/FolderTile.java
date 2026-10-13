package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartella sulla home.
 * 1x1: cerchio con l'anteprima di 4 app. 2x2 ("cartella grande"): 4 icone toccabili direttamente,
 * la quarta diventa "+N" se le app sono più di quattro.
 */
class FolderTile extends View {
    final Item item;
    private final AppTile.Source src;
    private final Theme th;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rf = new RectF();
    float lastX, lastY;

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription(a11yText());
        info.setClassName(android.widget.Button.class.getName());
    }

    /** Cosa legge TalkBack: calcolato al momento, così dice sempre lo stato attuale. */
    CharSequence a11yText() {
        int n = apps(item).size();
        return T.t("Cartella") + " " + name(item) + ", " + n + T.n(n, " app", " app");
    }

    FolderTile(Context c, Item item, AppTile.Source src, Theme th) {
        super(c);
        this.item = item;
        this.src = src;
        this.th = th;
    }

    // ---------- dati della cartella (in Item.data come JSON) ----------

    static String name(Item it) {
        try {
            return T.t(new JSONObject(it.data).optString("n", "Cartella"));
        } catch (Exception e) {
            return T.t("Cartella");
        }
    }

    static List<String> apps(Item it) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(it.data).optJSONArray("a");
            if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** App scelta come copertina (vuoto = anteprima delle prime app). */
    static String cover(Item it) {
        try {
            String c = new JSONObject(it.data).optString("c", "");
            return apps(it).contains(c) ? c : "";
        } catch (Exception e) {
            return "";
        }
    }

    static void setCover(Item it, String key) {
        try {
            JSONObject o = new JSONObject(it.data == null || it.data.isEmpty() ? "{}" : it.data);
            if (key == null || key.isEmpty()) o.remove("c");
            else o.put("c", key);
            it.data = o.toString();
        } catch (Exception ignored) {
        }
    }

    static void set(Item it, String name, List<String> apps) {
        try {
            // si tengono le altre impostazioni (es. la copertina)
            JSONObject o;
            try {
                o = new JSONObject(it.data == null || it.data.isEmpty() ? "{}" : it.data);
            } catch (Exception e) {
                o = new JSONObject();
            }
            o.put("n", name);
            JSONArray a = new JSONArray();
            for (String k : apps) a.put(k);
            o.put("a", a);
            it.data = o.toString();
        } catch (Exception ignored) {
        }
    }

    /** Nella cartella grande: indice dell'app toccata (0..3), oppure -1 per "apri la cartella". */
    int tappedIndex() {
        if (item.w < 2 || !cover(item).isEmpty()) return -1;
        List<String> a = apps(item);
        int col = lastX < getWidth() / 2f ? 0 : 1;
        int row = lastY < getHeight() / 2f ? 0 : 1;
        int idx = row * 2 + col;
        if (a.size() > 4 && idx == 3) return -1;
        return idx < a.size() ? idx : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = ev.getX();
            lastY = ev.getY();
        }
        return super.onTouchEvent(ev);
    }

    private int bg() {
        return item.tone == 1 ? th.tileAlt : item.tone == 2 ? th.accent : th.tile;
    }

    private final android.graphics.Path clip = new android.graphics.Path();
    private final android.graphics.Rect srcR = new android.graphics.Rect();
    private final int[] loc = new int[2];

    /** Vetro smerigliato dello stile Moderno, come nei widget. */
    private void drawGlass(Canvas cv, float W, float H, float rad) {
        Bitmap g = th.glassBmp;
        View root = getRootView();
        if (g == null || root == null || root.getWidth() == 0) return;
        getLocationOnScreen(loc);
        float sx = g.getWidth() / (float) root.getWidth(), sy = g.getHeight() / (float) root.getHeight();
        srcR.set(Math.round(loc[0] * sx), Math.round(loc[1] * sy), Math.round((loc[0] + W) * sx), Math.round((loc[1] + H) * sy));
        clip.reset();
        clip.addRoundRect(0, 0, W, H, rad, rad, android.graphics.Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        rf.set(0, 0, W, H);
        cv.drawBitmap(g, srcR, rf, p);
        cv.restore();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float W = getWidth(), H = getHeight(), m = Math.min(W, H);
        p.setColorFilter(null);
        p.setStyle(Paint.Style.FILL);
        boolean circle = item.w == 1 && item.h == 1;
        float rad = circle ? m / 2f : Theme.radius(m, getResources().getDisplayMetrics().density);
        if (th.glass && item.tone == 0) drawGlass(cv, W, H, rad);
        p.setColor(bg());
        if (circle) cv.drawCircle(W / 2f, H / 2f, m / 2f, p);
        else {
            rf.set(0, 0, W, H);
            cv.drawRoundRect(rf, Theme.radius(m, getResources().getDisplayMetrics().density), Theme.radius(m, getResources().getDisplayMetrics().density), p);
        }
        if (th.stroke != 0 && item.tone == 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
            p.setColor(th.stroke);
            if (circle) cv.drawCircle(W / 2f, H / 2f, m / 2f - 1, p);
            else {
                rf.set(1, 1, W - 1, H - 1);
                cv.drawRoundRect(rf, Theme.radius(m, getResources().getDisplayMetrics().density), Theme.radius(m, getResources().getDisplayMetrics().density), p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xFFFFFFFF);

        List<String> a = apps(item);
        String cov = cover(item);
        if (!cov.isEmpty()) {
            // copertina: l'icona dell'app scelta, grande, con il numero di app in un angolo
            float icon = m * (circle ? 0.62f : 0.6f);
            Bitmap b = src.iconFor(cov, Math.round(icon));
            if (b != null) {
                rf.set(W / 2f - icon / 2f, H / 2f - icon / 2f, W / 2f + icon / 2f, H / 2f + icon / 2f);
                cv.drawBitmap(b, null, rf, p);
            }
            int fg = item.tone == 1 ? th.onTileAlt : item.tone == 2 ? th.onAccent : th.onTile;
            float cs = m * (circle ? 0.13f : 0.1f);
            float ccx = circle ? W / 2f + m * 0.33f : W - m * 0.14f, ccy = circle ? H / 2f + m * 0.33f : H - m * 0.14f;
            p.setColor(item.tone == 2 ? th.onAccent : th.accent);
            cv.drawCircle(ccx, ccy, cs, p);
            Draw.big(cv, th, String.valueOf(a.size()), ccx, ccy, cs * 0.9f, cs * 1.6f,
                    item.tone == 2 ? th.accent : th.onAccent, "", fg, 0, p, true);
            p.setColor(0xFFFFFFFF);
        } else {
        float area = circle ? m * 0.62f : m * 0.84f;
        float cell = area / 2f;
        float x0 = (W - area) / 2f, y0 = (H - area) / 2f;
        float icon = cell * (circle ? 0.86f : 0.82f);
        for (int i = 0; i < Math.min(4, a.size()); i++) {
            float cx = x0 + (i % 2) * cell + cell / 2f;
            float cy = y0 + (i / 2) * cell + cell / 2f;
            if (!circle && i == 3 && a.size() > 4) {
                int fg = item.tone == 1 ? th.onTileAlt : item.tone == 2 ? th.onAccent : th.onTile;
                Draw.big(cv, th, "+" + (a.size() - 3), cx, cy, icon * 0.32f, icon, fg, "+",
                        item.tone == 2 ? th.onAccent : th.accent, 0, p, true);
                continue;
            }
            Bitmap b = src.iconFor(a.get(i), Math.round(icon));
            if (b == null) continue;
            rf.set(cx - icon / 2f, cy - icon / 2f, cx + icon / 2f, cy + icon / 2f);
            cv.drawBitmap(b, null, rf, p);
        }
        }
        // un pallino sulla cartella se almeno un'app dentro ha notifiche
        for (String k : a) {
            if (src.badge(k) > 0) {
                float cs = circle ? m : m * 0.9f;
                BadgeDrawable.draw(cv, p, W / 2f, H / 2f, cs, th.accent, th.bg);
                break;
            }
        }
    }
}
