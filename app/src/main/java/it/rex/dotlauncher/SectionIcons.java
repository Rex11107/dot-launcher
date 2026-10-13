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
            ".........", "....#....", "..#####..", "#########", "..#####.#", "..#...#.#", "..#####.R", ".........", "........."
    };
    static final String[] MONEY = {
            ".........", "#########", "#.......#", "#..###..#", "#..#R#..#",
            "#..###..#", "#.......#", "#########", "........."};
    static final String[] GEAR = {
            "...#.#...", "..#####..", ".###.###.", "###...###", ".##.R.##.", "###...###", ".###.###.", "..#####..", "...#.#..."
    };
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
            "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", ".#.....#."
    };
    static final String[] BAG = {
            "...###...", "..#...#..", "..#...#..", "#########", "#.......#",
            "#.......#", "#...R...#", "#.......#", "#########"};
    static final String[] BOOK = {
            ".........", ".##...##.", "#..#.#..#", "#...#...#", "#...#...#", "#...#...#", "#...#...#", ".###R###.", "........."
    };
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
            ".........", ".....R...", "..#..R#..", ".#..R..#.", "#...R...#", ".#..R..#.", "..#R..#..", "...R.....", "........."
    };
    static final String[] GRID = {
            "##..#..##", "##.###.##", ".........", "##.###.##", "##.###.##",
            ".........", "##.###.##", "##..#..##", "........."};
    static final String[] SEARCH = Draw.SEARCH;
    static final String[] MORE = {
            "...###...", "...###...", ".........", "...###...", "...###...", ".........", "...###...", "...###...", "........."
    };
    static final String[] SLIDERS = {".........", "###R#####", ".........", "######R##", ".........", "#R#######", ".........", "####R####", "........."};
    static final String[] PHONE = {".#######.", ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#.", ".#######.", ".#..R..#.", ".#######."};
    static final String[] CHIP = {".#.#.#.#.", "#########", ".#.....#.", "##.###.##", ".#.#R#.#.", "##.###.##", ".#.....#.", "#########", ".#.#.#.#."};
    static final String[] WIFI = {".........", "..#####..", ".#.....#.", "#..###..#", "..#...#..", ".........", "....R....", ".........", "........."};
    static final String[] CAMERA = {".........", "...###...", "#########", "#..###..#", "#.#...#R#", "#.#...#.#", "#..###..#", "#########", "........."};
    static final String[] PIN = {"..#####..", ".#.....#.", "#..###..#", "#..#R#..#", "#..###..#", ".#.....#.", "..#...#..", "...#.#...", "....#...."};
    static final String[] CALENDAR = {".#.....#.", "#########", "#.......#", "#########", "#.#.#.#.#", "#.......#", "#.#.#.R.#", "#.......#", "#########"};
    static final String[] MAIL = {".........", "#########", "##.....##", "#.#...#.#", "#..#.#..#", "#...#...#", "#.......#", "#########", "........."};
    // ---------- impostazioni: un'icona diversa per ogni gesto e voce ----------
    static final String[] SWIPE_UP = {"....#....", "...###...", "..#.#.#..", ".#..#..#.", "....#....", "....#....", "....#....", "...RRR...", "...RRR..."};
    static final String[] SWIPE_DOWN = {"...RRR...", "...RRR...", "....#....", "....#....", "....#....", ".#..#..#.", "..#.#.#..", "...###...", "....#...."};
    static final String[] DOUBLE_TAP = {"..#####..", ".#.....#.", "#.......#", "#...R...#", "#..RRR..#", "#...R...#", "#.......#", ".#.....#.", "..#####.."};
    static final String[] TWO_DOWN = {"..R...R..", "..#...#..", "..#...#..", "..#...#..", "..#...#..", "#.#.#.#.#", ".###.###.", "..#...#..", "........."};
    static final String[] TWO_UP = {".........", "..#...#..", ".###.###.", "#.#.#.#.#", "..#...#..", "..#...#..", "..#...#..", "..#...#..", "..R...R.."};
    static final String[] PINCH = {"#.......#", ".#.....#.", "..##.##..", "..#...#..", "....R....", "..#...#..", "..##.##..", ".#.....#.", "#.......#"};
    static final String[] BLOCK = {"..#####..", ".##....#.", "#.#.....#", "#..#....#", "#...R...#", "#....#..#", "#.....#.#", ".#....##.", "..#####.."};
    static final String[] PAUSE = {".........", "..##.##..", "..##.##..", "..##.##..", "..##.##..", "..##.##..", "..##.##..", ".........", "....R...."};
    static final String[] WAVE = {".........", ".........", ".##......", "#..#.....", "....#...#", ".....#.#.", "......R..", ".........", "........."};
    static final String[] WIDGETS = {"####.####", "#..#.#..#", "#..#.#..#", "####.####", ".........", "####.####", "#..#.#R.#", "#..#.#..#", "####.####"};
    static final String[] DOCK = {".........", ".........", "#########", "#.......#", "#.......#", "#.......#", ".#######.", "#.#.R.#.#", ".#######."};
    static final String[] PANEL = {"#########", "#..#....#", "#R.#....#", "#..#....#", "#R.#....#", "#..#....#", "#R.#....#", "#..#....#", "#########"};
    static final String[] PAGE_ADD = {"######...", "#....#...", "#....#...", "#....#.R.", "#....#RRR", "#....#.R.", "#....#...", "#....#...", "######..."};
    static final String[] ARROW_UP = {"....#....", "...###...", "..#.#.#..", ".#..#..#.", "#...#...#", "....#....", "....#....", "....#....", "....#...."};
    static final String[] ARROW_DOWN = {"....#....", "....#....", "....#....", "....#....", "#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "....#...."};
    static final String[] WARN = {"....#....", "...#.#...", "...#.#...", "..#.R.#..", "..#.R.#..", ".#..R..#.", ".#.....#.", "#...R...#", "#########"};

    static final String[] LOCK = {"...###...", "..#...#..", "..#...#..", ".#######.", ".#######.", ".###R###.", ".###R###.", ".#######.", "........."};
    static final String[] COFFEE = {"..R..R...", ".R..R....", ".........", "#######..", "#######.#", "#######.#", ".#####.#.", "..###....", "#########"};
    static final String[] BALL = {"..#####..", ".#..#..#.", "#...#...#", "#...#...#", "#########", "#...#...#", "#...#...#", ".#..#..#.", "..#####.."};
    static final String[] PLANE = {"....#....", "...###...", "...###...", ".#######.", "#########", "...###...", "...###...", "..#####..", ".##...##."};
    static final String[] CART = {"#........", ".#.......", ".########", ".#......#", ".#.....#.", ".#######.", ".#.......", "..R...R..", "........."};
    static final String[] PALETTE = {"..#####..", ".#.R.R.#.", "#.......#", "#.R...#.#", "#.....##.", "#....#...", "#.....##.", ".#.....#.", "..######."};
    static final String[] MOON = {"...###...", ".##......", ".#.......", "#........", "#........", "#.......#", ".#.....#.", ".##...##.", "...###..."};
    static final String[] WRENCH = {"......#.#", ".....##.#", ".....####", "....###..", "...###...", "..###....", ".###.....", "###......", ".#......."};
    static final String[] DOWNLOAD = {"....#....", "....#....", "....#....", "..#.#.#..", "...###...", "....#....", ".........", "#.......#", "#########"};
    static final String[] LEAF = {"......###", "....#####", "...###.##", "..###.###", ".###.###.", ".#.####..", "..###....", ".#.......", "#........"};
    static final String[] PAW = {".#.....#.", ".#..#..#.", "....#....", "#.......#", "...###...", "..#####..", ".#######.", ".###.###.", "........."};
    static final String[] FILM = {"#########", "#.#.#.#.#", "#########", "#.......#", "#...R...#", "#.......#", "#########", "#.#.#.#.#", "#########"};
    static final String[] NEWS = {"########.", "#......##", "#.###..##", "#.###..##", "#......##", "#.####.##", "#......##", "#.####.##", "#########"};
    static final String[] CHART = {"#........", "#......R.", "#.....R..", "#..R.R...", "#.R.R....", "#R.......", "#........", "#........", "#########"};
    static final String[] PEOPLE = {".##...##.", "####.####", "####.####", ".##...##.", ".........", ".###.###.", "#########", "#########", "........."};
    static final String[] CLOSE = {
            "#.......#", ".#.....#.", "..#...#..", "...#.#...", "....#....",
            "...#.#...", "..#...#..", ".#.....#.", "#.......#"};
    static final String[] PLUS = {
            "....#....", "....#....", "....#....", "....#....", "#########",
            "....#....", "....#....", "....#....", "....#...."};
    static final String[] FDROID = {
            "#.......#", ".#.....#.", "..#####..", ".#######.", ".##R#R##.", ".#######.", ".........", ".#######.", ".#######."
    };
    static final String[] PLAY = {
            ".#.......", ".###.....", ".#####...", ".#######.", ".########",
            ".#######.", ".#####...", ".###.....", ".#......."};

    /** Icone sceglibili per una sezione: {nome interno, nome mostrato}. */
    static final String[][] CHOICES = {
            {"chat", T.t("Messaggi")}, {"globe", "Internet"}, {"photo", T.t("Foto")}, {"tv", "Video"},
            {"pad", T.t("Giochi")}, {"cap", T.t("Studio")}, {"money", T.t("Soldi")}, {"gear", T.t("Strumenti")},
            {"shapes", T.t("Varie")}, {"music", T.t("Musica")}, {"heart", T.t("Preferiti")}, {"star", T.t("Stella")},
            {"bag", T.t("Acquisti")}, {"book", T.t("Lettura")}, {"work", T.t("Lavoro")}, {"health", T.t("Salute")},
            {"home", T.t("Casa")}, {"car", "Auto"}, {"code", T.t("Sviluppo")}, {"grid", T.t("Tutte")},
            {"sliders", T.t("Impostazioni")}, {"phone", T.t("Dispositivo")}, {"chip", T.t("Sistema")}, {"wifi", T.t("Connessioni")},
            {"camera", T.t("Fotocamera")}, {"pin", T.t("Mappe")}, {"calendar", T.t("Calendario")}, {"mail", T.t("Posta")},
            {"lock", T.t("Sicurezza")}, {"cloud", "Cloud"}, {"bolt", T.t("Energia")}, {"bell", T.t("Avvisi")},
            {"coffee", T.t("Cibo")}, {"ball", "Sport"}, {"plane", T.t("Viaggi")}, {"cart", T.t("Spesa")},
            {"palette", T.t("Arte")}, {"sun", T.t("Meteo")}, {"moon", T.t("Notte")}, {"wrench", T.t("Attrezzi")},
            {"download", "Download"}, {"leaf", T.t("Natura")}, {"paw", T.t("Animali")}, {"film", "Film"},
            {"news", T.t("Notizie")}, {"chart", T.t("Grafici")}, {"people", T.t("Persone")}, {"steps", "Fitness"}
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
