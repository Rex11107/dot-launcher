package it.rex.dotlauncher;

/** Catalogo dei widget Nothing integrati: nome e misure consentite (colonne x righe). */
final class Widgets {
    static final String[] TYPES = {
            "clock_dots", "clock_analog", "clock_pill", "date_big", "date_dot", "calendar",
            "weather", "weather_week", "battery_ring", "battery_pill", "battery_dots", "alarm", "search"
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
            case "app": return "App";
            case "folder": return "Cartella";
            case "shortcut": return "Scorciatoia";
            default: return "Widget";
        }
    }

    static int[][] sizes(String type) {
        switch (type) {
            case "clock_dots": return new int[][]{{4, 1}, {2, 1}, {4, 2}};
            case "clock_analog": return new int[][]{{2, 2}, {1, 1}};
            case "clock_pill": return new int[][]{{1, 2}, {2, 1}};
            case "date_big": return new int[][]{{2, 2}};
            case "date_dot": return new int[][]{{1, 1}};
            case "calendar": return new int[][]{{4, 2}, {2, 2}, {4, 3}};
            case "weather": return new int[][]{{2, 2}, {2, 1}, {1, 1}};
            case "weather_week": return new int[][]{{4, 2}};
            case "battery_ring": return new int[][]{{2, 2}, {1, 1}};
            case "battery_pill": return new int[][]{{2, 1}, {4, 1}};
            case "battery_dots": return new int[][]{{2, 2}};
            case "alarm": return new int[][]{{1, 1}, {2, 1}};
            case "search": return new int[][]{{4, 1}};
            case "app": return new int[][]{{1, 1}, {2, 2}};
            case "folder": return new int[][]{{1, 1}, {2, 2}};
            case "shortcut": return new int[][]{{1, 1}};
            default: return new int[][]{{1, 1}, {2, 1}, {2, 2}, {4, 1}, {4, 2}, {4, 3}, {4, 4}};
        }
    }

    private Widgets() {}
}
