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
    static final String[] SLIDERS = {".........", "###R#####", ".........", "######R##", ".........", "#R#######", ".........", "####R####", "........."};
    static final String[] PHONE = {".#######.", ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#.", ".#######.", ".#..R..#.", ".#######."};
    static final String[] CHIP = {".#.#.#.#.", "#########", ".#.....#.", "##.###.##", ".#.#R#.#.", "##.###.##", ".#.....#.", "#########", ".#.#.#.#."};
    static final String[] WIFI = {".........", "..#####..", ".#.....#.", "#..###..#", "..#...#..", "....#....", "...#R#...", "....#....", "........."};
    static final String[] CAMERA = {".........", "...###...", "#########", "#..###..#", "#.#...#R#", "#.#...#.#", "#..###..#", "#########", "........."};
    static final String[] PIN = {"..#####..", ".#.....#.", "#..###..#", "#..#R#..#", "#..###..#", ".#.....#.", "..#...#..", "...#.#...", "....#...."};
    static final String[] CALENDAR = {".#.....#.", "#########", "#.......#", "#########", "#.#.#.#.#", "#.......#", "#.#.#.R.#", "#.......#", "#########"};
    static final String[] MAIL = {".........", "#########", "##.....##", "#.#...#.#", "#..#.#..#", "#...#...#", "#.......#", "#########", "........."};
    static final String[] LOCK = {"...###...", "..#...#..", "..#...#..", ".#######.", ".#######.", ".###R###.", ".###R###.", ".#######.", "........."};
    static final String[] COFFEE = {"..R..R...", ".R..R....", ".........", "#######..", "#######.#", "#######.#", ".#####.#.", "..###....", "#########"};
    static final String[] BALL = {"..#####..", ".#..#..#.", "#...#...#", "#...#...#", "#########", "#...#...#", "#...#...#", ".#..#..#.", "..#####.."};
    static final String[] PLANE = {"....#....", "...###...", "...###...", ".#######.", "#########", "...###...", "...###...", "..#####..", ".##...##."};
    static final String[] CART = {"#........", ".#.......", ".########", ".#......#", ".#.....#.", ".#######.", ".#.......", "..R...R..", "........."};
    static final String[] PALETTE = {"..#####..", ".#.R.R.#.", "#.......#", "#.R...#.#", "#.....##.", "#....#...", "#.....##.", ".#.....#.", "..######."};
    static final String[] MOON = {"...###...", ".##......", ".#.......", "#........", "#........", "#.......#", ".#.....#.", ".##...##.", "...###..."};
    static final String[] WRENCH = {"......##.", ".....#..#", ".....#.#.", "....###..", "...###...", "..###....", ".###.....", "###......", "##......."};
    static final String[] DOWNLOAD = {"....#....", "....#....", "....#....", "..#.#.#..", "...###...", "....#....", ".........", "#.......#", "#########"};
    static final String[] LEAF = {"......###", "....#####", "...###.##", "..###.###", ".###.###.", ".#.####..", "..###....", ".#.......", "#........"};
    static final String[] PAW = {".#.....#.", ".#..#..#.", "....#....", "#.......#", "...###...", "..#####..", ".#######.", ".###.###.", "........."};
    static final String[] FILM = {"#########", "#.#.#.#.#", "#########", "#.......#", "#...R...#", "#.......#", "#########", "#.#.#.#.#", "#########"};
    static final String[] NEWS = {"########.", "#......##", "#.###..##", "#.###..##", "#......##", "#.####.##", "#......##", "#.####.##", "#########"};
    static final String[] CHART = {"#........", "#......R.", "#.....R..", "#..R.R...", "#.R.R....", "#R.......", "#........", "#........", "#########"};
    static final String[] PEOPLE = {"..#...#..", ".###.###.", "..#...#..", ".........", ".###.###.", "#########", "#########", ".........", "........."};
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
            {"home", "Casa"}, {"car", "Auto"}, {"code", "Sviluppo"}, {"grid", "Tutte"},
            {"sliders", "Impostazioni"}, {"phone", "Dispositivo"}, {"chip", "Sistema"}, {"wifi", "Connessioni"},
            {"camera", "Fotocamera"}, {"pin", "Mappe"}, {"calendar", "Calendario"}, {"mail", "Posta"},
            {"lock", "Sicurezza"}, {"cloud", "Cloud"}, {"bolt", "Energia"}, {"bell", "Avvisi"},
            {"coffee", "Cibo"}, {"ball", "Sport"}, {"plane", "Viaggi"}, {"cart", "Spesa"},
            {"palette", "Arte"}, {"sun", "Meteo"}, {"moon", "Notte"}, {"wrench", "Attrezzi"},
            {"download", "Download"}, {"leaf", "Natura"}, {"paw", "Animali"}, {"film", "Film"},
            {"news", "Notizie"}, {"chart", "Grafici"}, {"people", "Persone"}, {"steps", "Fitness"}
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
            case "sliders": return SLIDERS;
            case "phone": return PHONE;
            case "chip": return CHIP;
            case "wifi": return WIFI;
            case "camera": return CAMERA;
            case "pin": return PIN;
            case "calendar": return CALENDAR;
            case "mail": return MAIL;
            case "lock": return LOCK;
            case "coffee": return COFFEE;
            case "ball": return BALL;
            case "plane": return PLANE;
            case "cart": return CART;
            case "palette": return PALETTE;
            case "moon": return MOON;
            case "wrench": return WRENCH;
            case "download": return DOWNLOAD;
            case "leaf": return LEAF;
            case "paw": return PAW;
            case "film": return FILM;
            case "news": return NEWS;
            case "chart": return CHART;
            case "people": return PEOPLE;
            case "cloud": return Draw.CLOUD;
            case "bolt": return Draw.BOLT;
            case "bell": return Draw.BELL;
            case "sun": return Draw.SUN;
            case "steps": return Draw.STEPS;
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
