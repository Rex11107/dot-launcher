package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Il cassetto delle app diviso in sezioni: pagine orizzontali (una per sezione, poi "Tutte le app"
 * e la pagina "+") e la barra delle sezioni in basso.
 */
class AppSections extends LinearLayout {
    interface Host extends AppTile.Source, Sections.LabelSource {
        Theme theme();
        Set<String> hiddenKeys();
        List<HomeActivity.AppEntry> apps();
        void launchKey(String key, View v);
        void appLongPress(String key, View icon);
        void folderClick(Sections.Section s, Sections.Entry f);
        void folderLongPress(Sections.Section s, Sections.Entry f);
        void sectionMenu(int index);
        void allMenu();
        void newSection();
        void search();
        void openBrowser();
        void openStore(boolean fdroid);
        Bitmap storeIcon(boolean fdroid, int size);
    }

    /** Cella sotto il dito durante un trascinamento. */
    static final class Hit {
        Cell cell;
        Sections.Entry entry;
        boolean center, after;
    }

    private final Host host;
    private final Theme th;
    private final float dp;
    private final Pager pager;
    private final HorizontalScrollView barScroll;
    private final LinearLayout bar;
    private Sections model;
    private boolean alpha = true, showAll = true;
    private int current;
    private final List<List<Cell>> cells = new ArrayList<>();
    private final List<ScrollView> scrolls = new ArrayList<>();
    private Cell hiCell;
    private int hiTab = -1;

    AppSections(Context c, Host host) {
        super(c);
        this.host = host;
        this.th = host.theme();
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        pager = new Pager(c);
        pager.setListener(p -> {
            current = p;
            updateTabs();
        });
        addView(pager, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        barScroll = new HorizontalScrollView(c);
        barScroll.setHorizontalScrollBarEnabled(false);
        barScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        barScroll.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(60)));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.nuovo ? th.tile : Theme.alpha(th.onTile, th.light ? 0.07f : 0.1f));
        bg.setCornerRadius(px(24));
        if (th.stroke != 0) bg.setStroke(Math.max(1, px(1)), th.stroke);
        barScroll.setBackground(bg);
        barScroll.setClipToOutline(true);
        LayoutParams bl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(60));
        bl.topMargin = px(8);
        bl.bottomMargin = px(10);
        addView(barScroll, bl);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    void setEdgeGuard(int l, int r) {
        pager.setEdgeGuard(l, r);
    }

    void setModel(Sections m, boolean alpha, boolean showAll) {
        this.model = m;
        this.alpha = alpha;
        this.showAll = showAll;
    }

    int sectionCount() {
        return model == null ? 0 : model.list.size();
    }

    /** Pagine "vere" (sezioni + eventuale Tutte), senza la pagina "+". */
    int realPages() {
        return sectionCount() + (showAll ? 1 : 0);
    }

    int current() {
        return current;
    }

    /** Indice della sezione mostrata, -1 se è "Tutte le app" o la pagina "+". */
    int currentSection() {
        return current < sectionCount() ? current : -1;
    }

    void snapTo(int page) {
        pager.snapTo(page);
    }

    void setPageNow(int page) {
        current = Math.max(0, Math.min(page, realPages()));
        pager.setPageNow(current);
        updateTabs();
    }

    boolean canScrollUp() {
        if (current >= scrolls.size()) return false;
        return scrolls.get(current).canScrollVertically(-1);
    }

    void scrollToBottom() {
        for (ScrollView s : scrolls) s.post(() -> s.fullScroll(View.FOCUS_DOWN));
    }

    // =====================================================================
    // costruzione
    // =====================================================================

    void rebuild() {
        if (model == null) return;
        int keep = current;
        pager.removeAllViews();
        cells.clear();
        scrolls.clear();
        hiCell = null;
        Set<String> hidden = host.hiddenKeys();
        for (int i = 0; i < model.list.size(); i++) {
            Sections.Section s = model.list.get(i);
            List<Sections.Entry> vis = Sections.visible(s, hidden, alpha, host);
            final int idx = i;
            addPage(s.name, vis, s, () -> host.sectionMenu(idx));
        }
        if (showAll) {
            List<Sections.Entry> all = new ArrayList<>();
            for (HomeActivity.AppEntry a : host.apps()) {
                if (hidden.contains(a.key)) continue;
                Sections.Entry e = new Sections.Entry();
                e.app = a.key;
                all.add(e);
            }
            addPage("Tutte le app", all, null, host::allMenu);
        }
        addPlusPage();
        buildBar();
        current = Math.max(0, Math.min(keep, realPages()));
        pager.post(() -> {
            pager.setPageNow(current);
            updateTabs();
        });
    }

    private void addPage(String title, List<Sections.Entry> entries, Sections.Section sec, Runnable menu) {
        ScrollView sv = new ScrollView(getContext());
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.BOTTOM);
        col.setPadding(px(4), px(8), px(4), px(4));
        sv.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(actionsRow());
        col.addView(titleRow(title, menu));

        List<Cell> list = new ArrayList<>();
        int cols = TileGrid.COLS;
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                col.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Cell c = cell(entries.get(i), sec);
            list.add(c);
            row.addView(c, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (row != null) {
            for (int i = entries.size() % cols; i != 0 && i < cols; i++) {
                row.addView(new View(getContext()), new LayoutParams(0, 1, 1f));
            }
        }
        if (entries.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText(sec == null ? "Nessuna app" : "Sezione vuota: trascina qui un'app sull'icona della sezione in basso");
            empty.setTextColor(th.sub);
            empty.setTypeface(th.bodyFace);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setPadding(px(12), px(16), px(12), px(28));
            col.addView(empty);
        }
        cells.add(list);
        scrolls.add(sv);
        pager.addView(sv);
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    private void addPlusPage() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView plus = new ImageView(getContext());
        plus.setImageBitmap(SectionIcons.bitmap(SectionIcons.PLUS, px(96), th.onTile, th.accent,
                Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f)));
        plus.setOnClickListener(v -> host.newSection());
        col.addView(plus, new LayoutParams(px(96), px(96)));
        TextView t = new TextView(getContext());
        t.setText("Nuova sezione");
        t.setTextColor(th.onTile);
        t.setTypeface(th.titleFace);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setPadding(0, px(14), 0, 0);
        t.setOnClickListener(v -> host.newSection());
        col.addView(t);
        cells.add(new ArrayList<>());
        pager.addView(col);
    }

    private View actionsRow() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(8), 0, px(8), px(6));
        int s = px(42);
        int circle = Theme.alpha(th.onTile, th.light ? 0.08f : 0.12f);
        ImageView web = new ImageView(getContext());
        web.setImageBitmap(SectionIcons.bitmap(SectionIcons.GLOBE, s, th.onTile, th.accent, circle));
        web.setOnClickListener(v -> host.openBrowser());
        web.setContentDescription("Browser");
        r.addView(web, new LayoutParams(s, s));
        r.addView(new View(getContext()), new LayoutParams(0, 1, 1f));
        for (int i = 0; i < 2; i++) {
            final boolean fd = i == 1;
            ImageView b = new ImageView(getContext());
            Bitmap ic = host.storeIcon(fd, s);
            if (ic == null) {
                ic = SectionIcons.bitmap(fd ? SectionIcons.FDROID : SectionIcons.PLAY, s, th.onTile, th.accent, circle);
                b.setAlpha(fd ? 0.3f : 1f);
            }
            b.setImageBitmap(ic);
            b.setContentDescription(fd ? "F-Droid" : "Play Store");
            b.setOnClickListener(v -> host.openStore(fd));
            LayoutParams lp = new LayoutParams(s, s);
            lp.leftMargin = px(10);
            r.addView(b, lp);
        }
        return r;
    }

    private View titleRow(String title, Runnable menu) {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(10), px(6), px(4), px(14));
        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTextColor(th.onTile);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (th.dots) {
            t.setTypeface(Fonts.dot);
        } else {
            t.setTypeface(th.titleFace);
        }
        t.setAutoSizeTextTypeUniformWithConfiguration(18, th.dots ? 36 : 32, 1, TypedValue.COMPLEX_UNIT_SP);
        r.addView(t, new LayoutParams(0, px(48), 1f));
        int s = px(42);
        ImageView search = new ImageView(getContext());
        search.setImageBitmap(SectionIcons.bitmap(SectionIcons.SEARCH, s, th.onTile, th.accent, 0));
        search.setPadding(px(9), px(9), px(9), px(9));
        search.setOnClickListener(v -> host.search());
        search.setContentDescription("Cerca");
        r.addView(search, new LayoutParams(s, s));
        ImageView more = new ImageView(getContext());
        more.setImageBitmap(SectionIcons.bitmap(SectionIcons.MORE, s, th.onTile, th.accent, 0));
        more.setPadding(px(9), px(9), px(9), px(9));
        more.setOnClickListener(v -> menu.run());
        more.setContentDescription("Menù della sezione");
        LayoutParams ml = new LayoutParams(s, s);
        ml.leftMargin = px(4);
        r.addView(more, ml);
        return r;
    }

    private Cell cell(Sections.Entry e, Sections.Section sec) {
        Cell c = new Cell(getContext(), e);
        c.setOrientation(VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(0, px(8), 0, px(12));
        View icon;
        int s = px(54);
        if (e.isFolder()) {
            Item fake = new Item("folder", 0, 0, 1, 1, 0, 0);
            List<String> vis = new ArrayList<>();
            Set<String> hidden = host.hiddenKeys();
            for (String k : e.apps) if (!hidden.contains(k)) vis.add(k);
            if (alpha) {
                final Collator col = Collator.getInstance(Locale.ITALIAN);
                Collections.sort(vis, (x, y) -> col.compare(host.labelFor(x), host.labelFor(y)));
            }
            FolderTile.set(fake, e.name, vis);
            icon = new FolderTile(getContext(), fake, host, th);
            icon.setClickable(false);
            c.setOnClickListener(v -> host.folderClick(sec, e));
            c.setOnLongClickListener(v -> {
                host.folderLongPress(sec, e);
                return true;
            });
        } else {
            ImageView iv = new ImageView(getContext());
            iv.setImageBitmap(host.iconFor(e.app, s));
            icon = iv;
            c.setOnClickListener(v -> host.launchKey(e.app, iv));
            c.setOnLongClickListener(v -> {
                host.appLongPress(e.app, iv);
                return true;
            });
        }
        c.icon = icon;
        c.addView(icon, new LayoutParams(s, s));
        TextView tv = new TextView(getContext());
        tv.setText(e.isFolder() ? e.name : host.labelFor(e.app));
        tv.setTextColor(th.onTile);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTypeface(th.bodyFace);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        lp.leftMargin = px(2);
        lp.rightMargin = px(2);
        c.addView(tv, lp);
        return c;
    }

    // =====================================================================
    // barra delle sezioni
    // =====================================================================

    private void buildBar() {
        bar.removeAllViews();
        int n = realPages();
        int avail = getResources().getDisplayMetrics().widthPixels - px(32) - px(8);
        int w = Math.max(px(46), n == 0 ? avail : avail / n);
        bar.setPadding(px(4), 0, px(4), 0);
        for (int i = 0; i < n; i++) {
            String[] pat = i < sectionCount() ? SectionIcons.get(model.list.get(i).icon) : SectionIcons.GRID;
            Tab t = new Tab(getContext(), pat);
            final int page = i;
            t.setOnClickListener(v -> pager.snapTo(page));
            t.setContentDescription(i < sectionCount() ? model.list.get(i).name : "Tutte le app");
            bar.addView(t, new LayoutParams(w, px(60)));
        }
        updateTabs();
    }

    private void updateTabs() {
        for (int i = 0; i < bar.getChildCount(); i++) {
            Tab t = (Tab) bar.getChildAt(i);
            t.active = i == current;
            t.hover = i == hiTab;
            t.invalidate();
        }
        if (current < bar.getChildCount()) {
            View a = bar.getChildAt(current);
            barScroll.post(() -> {
                int l = a.getLeft(), r = a.getRight();
                int sx = barScroll.getScrollX(), w = barScroll.getWidth();
                if (l < sx) barScroll.smoothScrollTo(l - px(8), 0);
                else if (r > sx + w) barScroll.smoothScrollTo(r - w + px(8), 0);
            });
        }
    }

    private class Tab extends View {
        final String[] pat;
        boolean active, hover;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        Tab(Context c, String[] pat) {
            super(c);
            this.pat = pat;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            if (hover) {
                p.setColor(Theme.alpha(th.accent, 0.25f));
                cv.drawCircle(W / 2f, H / 2f - px(2), px(22), p);
            }
            Draw.icon(cv, pat, W / 2f, H / 2f - px(2), px(26), p, th.onTile, th.accent);
            if (active) {
                p.setColor(th.accent);
                float hw = px(11);
                r.set(W / 2f - hw, H - px(8), W / 2f + hw, H - px(4));
                cv.drawRoundRect(r, px(2), px(2), p);
            }
        }
    }

    /** Cella del cassetto: disegna l'evidenziazione durante il trascinamento. */
    static class Cell extends LinearLayout {
        final Sections.Entry entry;
        View icon;
        int mark; // 0 niente, 1 contenitore, 2 prima, 3 dopo
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        int accent;

        Cell(Context c, Sections.Entry e) {
            super(c);
            entry = e;
            setWillNotDraw(false);
        }

        @Override
        protected void dispatchDraw(Canvas cv) {
            float dp = getResources().getDisplayMetrics().density;
            if (mark == 1 && icon != null) {
                p.setColor(Theme.alpha(accent, 0.3f));
                float cx = icon.getLeft() + icon.getWidth() / 2f, cy = icon.getTop() + icon.getHeight() / 2f;
                cv.drawCircle(cx, cy, icon.getWidth() * 0.62f, p);
            }
            super.dispatchDraw(cv);
            if (mark == 2 || mark == 3) {
                p.setColor(accent);
                float x = mark == 2 ? dp * 2 : getWidth() - dp * 5;
                r.set(x, getHeight() * 0.12f, x + dp * 3, getHeight() * 0.6f);
                cv.drawRoundRect(r, dp * 2, dp * 2, p);
            }
        }
    }

    // =====================================================================
    // trascinamento
    // =====================================================================

    /** -2 se il dito non è sulla barra, -1 se è sulla barra ma non su un'icona, altrimenti la pagina. */
    int tabAt(float rawX, float rawY) {
        int[] l = new int[2];
        barScroll.getLocationOnScreen(l);
        if (rawY < l[1] - px(6) || rawY > l[1] + barScroll.getHeight() + px(10)) return -2;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View t = bar.getChildAt(i);
            int[] tl = new int[2];
            t.getLocationOnScreen(tl);
            if (rawX >= tl[0] && rawX < tl[0] + t.getWidth()) return i;
        }
        return -1;
    }

    Hit hit(float rawX, float rawY) {
        if (current >= cells.size()) return null;
        for (Cell c : cells.get(current)) {
            int[] l = new int[2];
            c.getLocationOnScreen(l);
            if (rawX < l[0] || rawX >= l[0] + c.getWidth() || rawY < l[1] || rawY >= l[1] + c.getHeight()) continue;
            Hit h = new Hit();
            h.cell = c;
            h.entry = c.entry;
            float cx = l[0] + c.getWidth() / 2f;
            float iconCy = l[1] + (c.icon != null ? c.icon.getTop() + c.icon.getHeight() / 2f : c.getHeight() / 2f);
            float rad = (c.icon != null ? c.icon.getWidth() : px(54)) * 0.42f;
            h.center = Math.abs(rawX - cx) < rad && Math.abs(rawY - iconCy) < rad;
            h.after = rawX > cx;
            return h;
        }
        return null;
    }

    void mark(Cell c, int mark) {
        if (hiCell != null && hiCell != c) {
            hiCell.mark = 0;
            hiCell.invalidate();
        }
        hiCell = c;
        if (c != null) {
            c.accent = th.accent;
            c.mark = mark;
            c.invalidate();
        }
    }

    void highlightTab(int i) {
        if (hiTab == i) return;
        hiTab = i;
        updateTabs();
    }

    void clearHighlight() {
        mark(null, 0);
        highlightTab(-1);
    }
}
