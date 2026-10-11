package it.rex.dotlauncher;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calcolatrice e convertitore di unità per la ricerca: tutto sul telefono, nessuna rete. */
final class SearchTools {

    // =====================================================================
    // calcolatrice
    // =====================================================================

    /** Il testo sembra un calcolo? (almeno una cifra e un operatore o una funzione) */
    static boolean looksLikeMath(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || !s.matches(".*[0-9π].*") && !s.contains("pi")) return false;
        if (!s.matches("[0-9a-zπ+\\-*/×÷x^%().,√!\\s=²³]*")) return false;
        return s.matches(".*[+\\-*/×÷^%√!²³].*") || s.matches(".*\\b(sqrt|sin|cos|tan|ln|log|abs)\\b.*")
                || s.matches(".*\\d\\s*x\\s*\\d.*");
    }

    /** Risultato del calcolo, o null se non è un'espressione valida. */
    static Double calc(String q) {
        try {
            String s = q.trim().toLowerCase(Locale.ROOT);
            if (s.endsWith("=")) s = s.substring(0, s.length() - 1);
            s = s.replace("×", "*").replace("÷", "/").replace("√", "sqrt").replace("π", "pi")
                    .replace("²", "^2").replace("³", "^3").replaceAll("(?<=\\d)\\s*x\\s*(?=\\d)", "*");
            // virgola decimale italiana: "3,5" -> "3.5" (le migliaia con il punto non sono supportate)
            s = s.replaceAll("(\\d),(\\d)", "$1.$2");
            Parser p = new Parser(s);
            double v = p.expr();
            p.skip();
            if (p.pos < s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    static String format(double v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ITALIAN);
        if (Math.abs(v) >= 1e12 || (Math.abs(v) < 1e-6 && v != 0)) {
            return new DecimalFormat("0.######E0", sym).format(v);
        }
        DecimalFormat f = new DecimalFormat("#,##0.##########", sym);
        return f.format(v);
    }

    /** Parser a discesa ricorsiva: + - * / ^ % ! parentesi, funzioni e costanti. */
    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        void skip() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        boolean eat(char c) {
            skip();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        double expr() {
            double v = term();
            while (true) {
                if (eat('+')) v += term();
                else if (eat('-')) v -= term();
                else return v;
            }
        }

        double term() {
            double v = factor();
            while (true) {
                if (eat('*')) v *= factor();
                else if (eat('/')) v /= factor();
                else if (eat('%')) {
                    // "50%" alla fine o prima di un operatore = percentuale; "7 % 3" = resto
                    skip();
                    if (pos >= s.length() || "+-*/)".indexOf(s.charAt(pos)) >= 0) v = v / 100.0;
                    else v = v % factor();
                } else return v;
            }
        }

        double factor() {
            if (eat('+')) return factor();
            if (eat('-')) return -factor();
            double v = power();
            return v;
        }

        double power() {
            double v = postfix();
            if (eat('^')) v = Math.pow(v, factor());
            return v;
        }

        double postfix() {
            double v = atom();
            while (eat('!')) v = fact(v);
            return v;
        }

        double atom() {
            skip();
            if (eat('(')) {
                double v = expr();
                eat(')');
                return v;
            }
            int start = pos;
            if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                return Double.parseDouble(s.substring(start, pos));
            }
            while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
            String name = s.substring(start, pos);
            switch (name) {
                case "pi": return Math.PI;
                case "e": return Math.E;
                case "sqrt": return Math.sqrt(atomArg());
                case "sin": return Math.sin(Math.toRadians(atomArg()));
                case "cos": return Math.cos(Math.toRadians(atomArg()));
                case "tan": return Math.tan(Math.toRadians(atomArg()));
                case "ln": return Math.log(atomArg());
                case "log": return Math.log10(atomArg());
                case "abs": return Math.abs(atomArg());
                default: throw new IllegalArgumentException(name);
            }
        }

        double atomArg() {
            return postfix();
        }

        static double fact(double n) {
            if (n < 0 || n > 170 || n != Math.floor(n)) return Double.NaN;
            double r = 1;
            for (int i = 2; i <= n; i++) r *= i;
            return r;
        }
    }

    // =====================================================================
    // convertitore di unità
    // =====================================================================

    /** Unità: nome mostrato, dimensione, fattore verso l'unità base (le temperature a parte). */
    private static final class Unit {
        final String name, dim;
        final double factor;

        Unit(String name, String dim, double factor) {
            this.name = name;
            this.dim = dim;
            this.factor = factor;
        }
    }

    private static final Map<String, Unit> UNITS = new LinkedHashMap<>();

    private static void u(String dim, String name, double factor, String... aliases) {
        Unit unit = new Unit(name, dim, factor);
        UNITS.put(name.toLowerCase(Locale.ROOT), unit);
        for (String a : aliases) UNITS.put(a, unit);
    }

    static {
        // lunghezza (base: metro)
        u("len", "mm", 0.001, "millimetri", "millimetro");
        u("len", "cm", 0.01, "centimetri", "centimetro");
        u("len", "m", 1, "metri", "metro");
        u("len", "km", 1000, "chilometri", "chilometro", "kilometri");
        u("len", "in", 0.0254, "pollici", "pollice", "inch", "inches", "\"");
        u("len", "ft", 0.3048, "piedi", "piede", "feet", "foot");
        u("len", "yd", 0.9144, "iarde", "yard", "yards");
        u("len", "mi", 1609.344, "miglia", "miglio", "mile", "miles");
        u("len", "nmi", 1852, "miglia nautiche", "nodo-miglia");
        // massa (base: chilogrammo)
        u("mass", "mg", 1e-6, "milligrammi");
        u("mass", "g", 0.001, "grammi", "grammo");
        u("mass", "kg", 1, "chili", "chilo", "chilogrammi", "kilogrammi");
        u("mass", "t", 1000, "tonnellate", "tonnellata");
        u("mass", "oz", 0.028349523125, "once", "oncia", "ounce", "ounces");
        u("mass", "lb", 0.45359237, "libbre", "libbra", "lbs", "pound", "pounds");
        // volume (base: litro)
        u("vol", "ml", 0.001, "millilitri");
        u("vol", "cl", 0.01, "centilitri");
        u("vol", "l", 1, "litri", "litro");
        u("vol", "gal", 3.785411784, "galloni", "gallone", "gallon", "gallons");
        u("vol", "fl oz", 0.0295735, "floz");
        u("vol", "cup", 0.24, "tazze", "tazza", "cups");
        u("vol", "m³", 1000, "m3", "metri cubi");
        // velocità (base: m/s)
        u("speed", "km/h", 1 / 3.6, "kmh", "kph");
        u("speed", "m/s", 1, "ms");
        u("speed", "mph", 0.44704);
        u("speed", "nodi", 0.514444, "kn", "knot", "knots", "nodo");
        // area (base: m²)
        u("area", "m²", 1, "m2", "metri quadri", "mq");
        u("area", "km²", 1e6, "km2", "kmq");
        u("area", "ha", 1e4, "ettari", "ettaro");
        u("area", "acri", 4046.8564224, "acre", "acres", "acro");
        u("area", "ft²", 0.09290304, "ft2", "sqft");
        // dati (base: byte)
        u("data", "B", 1, "byte", "bytes");
        u("data", "KB", 1e3, "kb", "kilobyte");
        u("data", "MB", 1e6, "mb", "megabyte");
        u("data", "GB", 1e9, "gb", "gigabyte");
        u("data", "TB", 1e12, "tb", "terabyte");
        u("data", "KiB", 1024, "kib");
        u("data", "MiB", 1048576, "mib");
        u("data", "GiB", 1073741824, "gib");
        // tempo (base: secondo)
        u("time", "s", 1, "secondi", "secondo", "sec");
        u("time", "min", 60, "minuti", "minuto");
        u("time", "h", 3600, "ore", "ora", "hours", "hour");
        u("time", "giorni", 86400, "giorno", "days", "day", "gg");
        u("time", "settimane", 604800, "settimana", "weeks", "week");
        // temperatura (conversione speciale)
        u("temp", "°C", 1, "c", "celsius", "°c", "gradi");
        u("temp", "°F", 1, "f", "fahrenheit", "°f");
        u("temp", "K", 1, "k", "kelvin");
        // energia e potenza
        u("energy", "kcal", 4184, "calorie", "cal");
        u("energy", "kJ", 1000, "kj");
        u("energy", "kWh", 3.6e6, "kwh");
        u("power", "W", 1, "watt");
        u("power", "kW", 1000, "kw");
        u("power", "CV", 735.49875, "cv", "cavalli", "hp");
    }

    private static final Pattern CONV = Pattern.compile(
            "^\\s*(-?[0-9]+(?:[.,][0-9]+)?)\\s*([^0-9\\s].*?)(?:\\s+(?:in|a|to|->|→|=)\\s+(.+?))?\\s*$");

    /** Conversioni per testi come "10 km in mi", "70 kg", "30 c in f". Vuota se non è una misura. */
    static List<String[]> convert(String q) {
        List<String[]> out = new ArrayList<>();
        Matcher m = CONV.matcher(q.toLowerCase(Locale.ROOT));
        if (!m.matches()) return out;
        double v;
        try {
            v = Double.parseDouble(m.group(1).replace(',', '.'));
        } catch (Exception e) {
            return out;
        }
        Unit from = UNITS.get(m.group(2).trim());
        if (from == null) return out;
        Unit to = m.group(3) == null ? null : UNITS.get(m.group(3).trim());
        if (to != null && !to.dim.equals(from.dim)) return out;
        String fromText = format(v) + " " + from.name;
        for (Unit u : new java.util.LinkedHashSet<>(UNITS.values())) {
            if (!u.dim.equals(from.dim) || u == from) continue;
            if (to != null && u != to) continue;
            double r = "temp".equals(from.dim) ? temp(v, from.name, u.name) : v * from.factor / u.factor;
            out.add(new String[]{format(round(r)) + " " + u.name, fromText});
        }
        return out;
    }

    private static double round(double r) {
        if (r == 0) return 0;
        double mag = Math.pow(10, 6 - Math.floor(Math.log10(Math.abs(r))));
        return Math.round(r * mag) / mag;
    }

    private static double temp(double v, String from, String to) {
        double c = from.equals("°C") ? v : from.equals("°F") ? (v - 32) * 5 / 9 : v - 273.15;
        return to.equals("°C") ? c : to.equals("°F") ? c * 9 / 5 + 32 : c + 273.15;
    }

    private SearchTools() {}
}
