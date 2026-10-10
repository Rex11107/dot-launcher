package it.rex.dotlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Icone a puntini 9x9 per le sezioni del cassetto e i pulsanti dell'intestazione. */
final class SectionIcons {
    static final String[] CHAT = {
            ".#######.", "#.......#", "#.......#", "#.R.R.R.#", "#.......#",
            ".######..", ".#.......", "#........", "........."};
    static final String[] GLOBE = {
            "..#####..", ".#..#..#.", "#..#.#..#", "#########", "#..#.#..#",
            "#########", "#..#.#..#", ".#..#..#.", "..#####.."};
    static final String[] TV = {
            "..#...#..", "...#.#...", "#########", "#.......#", "#.......#",
            "#.....R.#", "#.......#", "#########", ".#.....#."};
    static final String[] PAD = {
            ".........", "..#####..", ".#.....#.", "#.#...R.#", "###..R.R#",
            "#.#...R.#", "#.......#", ".##...##.", "........."};
    static final String[] CAP = {
            "....#....", "..##.##..", "##.....##", "..##.##.#", "..#.#.#.#",
            "..#...#.#", "...###..R", ".........", "........."};
    static final String[] MONEY = {
            ".........", "#########", "#.......#", "#..###..#", "#..#R#..#",
            "#..###..#", "#.......#", "#########", "........."};
    static final String[] GEAR = {
            "...#.#...", ".#.###.#.", "..#####..", "####.####", "###.R.###",
            "####.####", "..#####..", ".#.###.#.", "...#.#..."};
    static final String[] SHAPES = {
            "...#.....", "..###....", ".#####...", ".........", "......##.",
            "####.#RR#", "####.#RR#", "####..##.", "####....."};
    static final String[] MUSIC = {
            "....#####", "....#...#", "....#...#", "....#...#", "....#...#",
            ".###..###", "####.####", ".##...##.", "........."};
    static final String[] HEART = {
            ".##...##.", "####.####", "#########", "#########", ".#######.",
            "..#####..", "...###...", "....#....", "........."};
    static final String[] STAR = {
            "....#....", "....#....", "...###...", "#########", ".#######.",
            "..#####..", "..##.##..", ".##...##.", "#.......#"};
    static final String[] BAG = {
            "...###...", "..#...#..", "..#...#..", "#########", "#.......#",
            "#.......#", "#...R...#", "#.......#", "#########"};
    static final String[] BOOK = {
            ".........", "###...###", "#..#.#..#", "#..#.#..#", "#..#.#..#",
            "#..#.#..#", "####.####", "....#....", "........."};
    static final String[] WORK = {
            ".........", "...###...", "..#...#..", "#########", "#.......#",
            "####R####", "#.......#", "#########", "........."};
    static final String[] HEALTH = {
            "...###...", "...#R#...", "...#R#...", "####R####", "#RRRRRRR#",
            "####R####", "...#R#...", "...#R#...", "...###..."};
    static final String[] HOME = {
            "....#....", "...###...", "..#####..", ".#######.", "#########",
            ".#.....#.", ".#..R..#.", ".#..R..#.", ".#######."};
    static final String[] CAR = {
            ".........", "..#####..", ".#.....#.", ".#.....#.", "#########",
            "#R#####R#", "#########", ".#.....#.", "........."};
    static final String[] CODE = {
            ".........", "..#...#..", ".#..R..#.", "#...R...#", "#..R....#",
            ".#.R...#.", "..#...#..", ".........", "........."};
    static final String[] GRID = {
            "##..#..##", "##.###.##", ".........", "##.###.##", "##.###.##",
            ".........", "##.###.##", "##..#..##", "........."};
    static final String[] SEARCH = Draw.SEARCH;
    static final String[] MORE = {
            "....#....", "...###...", "....#....", ".........", "....#....",
            "...###...", "....#....", ".........", "....#...."};
    static final String[] CLOSE = {
            "#.......#", ".#.....#.", "..#...#..", "...#.#...", "....#....",
            "...#.#...", "..#...#..", ".#.....#.", "#.......#"};
    static final String[] PLUS = {
            "....#....", "....#....", "....#....", "....#....", "#########",
            "....#....", "....#....", "....#....", "....#...."};
    static final String[] FDROID = {
            ".#.....#.", "..#####..", ".#######.", ".##.#.##.", ".#######.",
            ".........", "#########", "#.......#", "#########"};
    static final String[] PLAY = {
            ".#.......", ".###.....", ".#####...", ".#######.", ".########",
            ".#######.", ".#####...", ".###.....", ".#......."};

    /** Icone sceglibili per una sezione: {nome interno, nome mostrato}. */
    static final String[][] CHOICES = {
            {"chat", "Messaggi"}, {"globe", "Internet"}, {"photo", "Foto"}, {"tv", "Video"},
            {"pad", "Giochi"}, {"cap", "Studio"}, {"money", "Soldi"}, {"gear", "Strumenti"},
            {"shapes", "Varie"}, {"music", "Musica"}, {"heart", "Preferiti"}, {"star", "Stella"},
            {"bag", "Acquisti"}, {"book", "Lettura"}, {"work", "Lavoro"}, {"health", "Salute"},
            {"home", "Casa"}, {"car", "Viaggi"}, {"code", "Sviluppo"}, {"grid", "Tutte"}
    };

    static String[] get(String name) {
        if (name == null) return SHAPES;
        switch (name) {
            case "chat": return CHAT;
            case "globe": return GLOBE;
            case "photo": return Draw.PHOTO;
            case "tv": return TV;
            case "pad": return PAD;
            case "cap": return CAP;
            case "money": return MONEY;
            case "gear": return GEAR;
            case "music": return MUSIC;
            case "heart": return HEART;
            case "star": return STAR;
            case "bag": return BAG;
            case "book": return BOOK;
            case "work": return WORK;
            case "health": return HEALTH;
            case "home": return HOME;
            case "car": return CAR;
            case "code": return CODE;
            case "grid": return GRID;
            default: return SHAPES;
        }
    }

    /** Icona su un cerchio pieno (bg = 0 per nessuno sfondo). */
    static Bitmap bitmap(String[] pat, int size, int fg, int accent, int bg) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (bg != 0) {
            p.setColor(bg);
            c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        }
        Draw.icon(c, pat, size / 2f, size / 2f, size * (bg != 0 ? 0.5f : 0.86f), p, fg, accent);
        return b;
    }

    private SectionIcons() {}
}
