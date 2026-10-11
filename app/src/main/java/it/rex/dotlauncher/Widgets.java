package it.rex.dotlauncher;

/** Catalogo dei widget Nothing integrati: nome e misure consentite (colonne x righe). */
final class Widgets {
    static final String[] TYPES = {
            "clock_dots", "clock_analog", "clock_pill", "date_big", "date_dot", "calendar",
            "weather", "weather_week", "battery_ring", "battery_pill", "battery_dots", "alarm", "search",
            "world_clock", "countdown", "photo", "compass", "steps",
            "year", "day", "clock_ring", "moon", "sun", "weather_hours", "counter", "proverb", "dice", "week",
            "storage", "note", "torch", "media", "usage", "focus"
    };

    /** Categorie per la scelta dei widget: {titolo, tipi…}. */
    static final String[][] CATEGORIES = {
            {"Orologi", "clock_dots", "clock_analog", "clock_pill", "clock_ring", "world_clock", "alarm"},
            {"Data e tempo", "date_big", "date_dot", "calendar", "week", "year", "day", "countdown"},
            {"Meteo e cielo", "weather", "weather_week", "weather_hours", "sun", "moon"},
            {"Batteria e sistema", "battery_ring", "battery_pill", "battery_dots", "storage", "torch"},
            {"Musica e strumenti", "media", "search", "compass", "steps", "counter", "dice"},
            {"Foto e testi", "photo", "note", "proverb"},
            {"Benessere digitale", "usage", "focus"}
    };

    static String name(String type) {
        switch (type) {
            case "clock_dots": return "Orologio a puntini";
            case "clock_analog": return "Orologio analogico";
            case "clock_pill": return "Orologio a pillola";
            case "date_big": return "Data grande";
            case "date_dot": return "Data (cerchio)";
            case "calendar": return "Calendario del mese";
            case "weather": return "Meteo";
            case "weather_week": return "Meteo settimana";
            case "battery_ring": return "Batteria (anello)";
            case "battery_pill": return "Batteria (pillola)";
            case "battery_dots": return "Batteria (puntini)";
            case "alarm": return "Sveglia";
            case "search": return "Barra di ricerca";
            case "world_clock": return "Fusi orari";
            case "countdown": return "Conto alla rovescia";
            case "photo": return "Foto";
            case "compass": return "Bussola";
            case "steps": return "Contapassi";
            case "year": return "Anno in punti";
            case "day": return "Giornata";
            case "clock_ring": return "Orologio ad anello";
            case "moon": return "Fase lunare";
            case "sun": return "Alba e tramonto";
            case "weather_hours": return "Meteo delle prossime ore";
            case "counter": return "Contatore";
            case "proverb": return "Proverbio del giorno";
            case "dice": return "Dado";
            case "week": return "Settimana";
            case "storage": return "Memoria";
            case "note": return "Nota";
            case "torch": return "Torcia";
            case "media": return "Musica";
            case "usage": return "Tempo di utilizzo";
            case "focus": return "Focus";
            case "stack": return "Pila di widget";
            case "app": return "App";
            case "folder": return "Cartella";
            case "shortcut": return "Scorciatoia";
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
            case "app": return new int[][]{{1, 1}, {2, 2}};
            case "folder": return new int[][]{{1, 1}, {2, 2}};
            case "shortcut": return new int[][]{{1, 1}};
            default: return new int[][]{{1, 1}, {2, 1}, {2, 2}, {F, 1}, {F, 2}, {F, 3}, {F, 4}};
        }
    }

    private Widgets() {}
}
