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

    FolderTile(Context c, Item item, AppTile.Source src, Theme th) {
        super(c);
        this.item = item;
        this.src = src;
        this.th = th;
    }

    // ---------- dati della cartella (in Item.data come JSON) ----------

    static String name(Item it) {
        try {
            return new JSONObject(it.data).optString("n", "Cartella");
        } catch (Exception e) {
            return "Cartella";
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

    static void set(Item it, String name, List<String> apps) {
        try {
            JSONObject o = new JSONObject();
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
        if (item.w < 2) return -1;
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

    @Override
    protected void onDraw(Canvas cv) {
        float W = getWidth(), H = getHeight(), m = Math.min(W, H);
        p.setColorFilter(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg());
        boolean circle = item.w == 1 && item.h == 1;
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
        float area = circle ? m * 0.62f : m * 0.84f;
        float cell = area / 2f;
        float x0 = (W - area) / 2f, y0 = (H - area) / 2f;
        float icon = cell * (circle ? 0.86f : 0.82f);
        for (int i = 0; i < Math.min(4, a.size()); i++) {
            float cx = x0 + (i % 2) * cell + cell / 2f;
            float cy = y0 + (i / 2) * cell + cell / 2f;
            if (!circle && i == 3 && a.size() > 4) {
                int fg = item.tone == 1 ? th.onTileAlt : item.tone == 2 ? 0xFFFFFFFF : th.onTile;
                Draw.big(cv, th, "+" + (a.size() - 3), cx, cy, icon * 0.32f, icon, fg, "+",
                        item.tone == 2 ? 0xFFFFFFFF : th.accent, 0, p, true);
                continue;
            }
            Bitmap b = src.iconFor(a.get(i), Math.round(icon));
            if (b == null) continue;
            rf.set(cx - icon / 2f, cy - icon / 2f, cx + icon / 2f, cy + icon / 2f);
            cv.drawBitmap(b, null, rf, p);
        }
    }
}
