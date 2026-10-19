package it.rex.dotlauncher;

/** Catalogo dei widget a puntini integrati: nome e misure consentite (colonne x righe). */
final class Widgets {
    static final String[] TYPES = {
            "clock_dots", "clock_analog", "clock_pill", "date_big", "date_dot", "calendar",
            "weather", "weather_week", "battery_ring", "battery_pill", "battery_dots", "alarm", "search",
            "world_clock", "countdown", "photo", "compass", "steps",
            "year", "day", "clock_ring", "moon", "sun", "weather_hours", "counter", "proverb", "dice", "week",
            "storage", "note", "torch", "media", "usage", "focus", "glance"
    };

    /** Categorie per la scelta dei widget: {titolo, tipi…}. */
    static final String[][] CATEGORIES = {
            {T.t("Orologi"), "clock_dots", "clock_analog", "clock_pill", "clock_ring", "world_clock", "alarm"},
            {T.t("Data e tempo"), "glance", "date_big", "date_dot", "calendar", "week", "year", "day", "countdown"},
            {T.t("Meteo e cielo"), "weather", "weather_week", "weather_hours", "sun", "moon"},
            {T.t("Batteria e sistema"), "battery_ring", "battery_pill", "battery_dots", "storage", "torch"},
            {T.t("Musica e strumenti"), "media", "search", "compass", "steps", "counter", "dice"},
            {T.t("Foto e testi"), "photo", "note", "proverb"},
            {T.t("Benessere digitale"), "usage", "focus"}
    };

    static String name(String type) {
        switch (type) {
            case "clock_dots": return T.t("Orologio a puntini");
            case "clock_analog": return T.t("Orologio analogico");
            case "clock_pill": return T.t("Orologio a pillola");
            case "date_big": return T.t("Data grande");
            case "date_dot": return T.t("Data (cerchio)");
            case "calendar": return T.t("Calendario del mese");
            case "weather": return T.t("Meteo");
            case "weather_week": return T.t("Meteo settimana");
            case "battery_ring": return T.t("Batteria (anello)");
            case "battery_pill": return T.t("Batteria (pillola)");
            case "battery_dots": return T.t("Batteria (puntini)");
            case "alarm": return T.t("Sveglia");
            case "search": return T.t("Barra di ricerca");
            case "world_clock": return T.t("Fusi orari");
            case "countdown": return T.t("Conto alla rovescia");
            case "photo": return T.t("Foto");
            case "compass": return T.t("Bussola");
            case "steps": return T.t("Contapassi");
            case "year": return T.t("Anno in punti");
            case "day": return T.t("Giornata");
            case "clock_ring": return T.t("Orologio ad anello");
            case "moon": return T.t("Fase lunare");
            case "sun": return T.t("Alba e tramonto");
            case "weather_hours": return T.t("Meteo delle prossime ore");
            case "counter": return T.t("Contatore");
            case "proverb": return T.t("Proverbio del giorno");
            case "dice": return T.t("Dado");
            case "week": return T.t("Settimana");
            case "storage": return T.t("Memoria");
            case "note": return T.t("Nota");
            case "torch": return T.t("Torcia");
            case "media": return T.t("Musica");
            case "usage": return T.t("Tempo di utilizzo");
            case "focus": return "Focus";
            case "glance": return T.t("Riepilogo");
            case "stack": return T.t("Pila di widget");
            case "app": return "App";
            case "folder": return T.t("Cartella");
            case "shortcut": return T.t("Scorciatoia");
            default: return "Widget";
        }
    }

    /** Misure consentite; F = larghezza piena (4 o 5 colonne). */
    static int[][] sizes(String type) {
        final int F = TileGrid.COLS;
        switch (type) {
            case "clock_dots": return new int[][]{{F, 1}, {2, 1}, {F, 2}};
            case "clock_analog": return new int[][]{{2, 2}, {1, 1}};
            case "clock_pill": return new int[][]{{1, 2}, {2, 1}};
            case "date_big": return new int[][]{{2, 2}};
            case "date_dot": return new int[][]{{1, 1}};
            case "calendar": return new int[][]{{F, 2}, {2, 2}, {F, 3}};
            case "weather": return new int[][]{{2, 2}, {2, 1}, {1, 1}};
            case "weather_week": return new int[][]{{F, 2}};
            case "battery_ring": return new int[][]{{2, 2}, {1, 1}};
            case "battery_pill": return new int[][]{{2, 1}, {F, 1}};
            case "battery_dots": return new int[][]{{2, 2}};
            case "alarm": return new int[][]{{1, 1}, {2, 1}};
            case "search": return new int[][]{{F, 1}};
            case "world_clock": return new int[][]{{F, 1}, {2, 1}, {2, 2}, {F, 2}, {1, 1}};
            case "countdown": return new int[][]{{2, 2}, {2, 1}, {1, 1}};
            case "photo": return new int[][]{{2, 2}, {1, 1}, {2, 1}, {F, 2}, {F, 3}};
            case "compass": return new int[][]{{2, 2}, {1, 1}};
            case "steps": return new int[][]{{2, 2}, {2, 1}, {1, 1}};
            case "year": return new int[][]{{F, 2}, {2, 2}, {F, 3}};
            case "day": return new int[][]{{F, 1}, {2, 1}};
            case "clock_ring": return new int[][]{{2, 2}, {1, 1}};
            case "moon": return new int[][]{{2, 2}, {1, 1}};
            case "sun": return new int[][]{{2, 2}, {2, 1}, {F, 1}};
            case "weather_hours": return new int[][]{{F, 2}, {F, 1}, {2, 2}};
            case "counter": return new int[][]{{2, 1}, {1, 1}};
            case "proverb": return new int[][]{{F, 2}, {2, 2}};
            case "dice": return new int[][]{{1, 1}, {2, 2}};
            case "week": return new int[][]{{F, 1}};
            case "storage": return new int[][]{{2, 2}, {2, 1}};
            case "note": return new int[][]{{2, 2}, {2, 1}, {F, 2}};
            case "torch": return new int[][]{{1, 1}};
            case "media": return new int[][]{{F, 1}, {2, 2}, {F, 2}};
            case "usage": return new int[][]{{2, 2}, {F, 2}, {2, 1}, {F, 1}};
            case "focus": return new int[][]{{1, 1}, {2, 1}};
            case "glance": return new int[][]{{F, 1}, {F, 2}, {2, 2}};
            case "app": return new int[][]{{1, 1}, {2, 2}};
            case "folder": return new int[][]{{1, 1}, {2, 2}};
            case "shortcut": return new int[][]{{1, 1}};
            default: return new int[][]{{1, 1}, {2, 1}, {2, 2}, {F, 1}, {F, 2}, {F, 3}, {F, 4}};
        }
    }

    private Widgets() {}
}
