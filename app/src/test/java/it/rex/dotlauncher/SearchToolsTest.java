package it.rex.dotlauncher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** La logica pura della ricerca: calcolatrice, convertitore, sveglie e timer scritti a parole. */
public class SearchToolsTest {

    // ---------- calcolatrice ----------

    @Test
    public void calcOperazioniBase() {
        assertEquals(7.0, SearchTools.calc("1+2*3"), 1e-9);
        assertEquals(9.0, SearchTools.calc("(1+2)*3"), 1e-9);
        assertEquals(2.5, SearchTools.calc("5/2"), 1e-9);
        assertEquals(8.0, SearchTools.calc("2^3"), 1e-9);
    }

    @Test
    public void calcSimboliEVirgola() {
        assertEquals(6.0, SearchTools.calc("2×3"), 1e-9);
        assertEquals(6.0, SearchTools.calc("2 x 3"), 1e-9);
        assertEquals(4.0, SearchTools.calc("8÷2"), 1e-9);
        assertEquals(7.0, SearchTools.calc("3,5*2"), 1e-9);
        assertEquals(3.0, SearchTools.calc("1+2="), 1e-9);
        assertEquals(16.0, SearchTools.calc("4²"), 1e-9);
    }

    @Test
    public void calcFunzioni() {
        assertEquals(4.0, SearchTools.calc("sqrt(16)"), 1e-9);
        assertEquals(3.0, SearchTools.calc("√9"), 1e-9);
        assertEquals(Math.PI, SearchTools.calc("pi"), 1e-9);
    }

    @Test
    public void calcRifiutaIlNonValido() {
        assertNull(SearchTools.calc("1/0"));
        assertNull(SearchTools.calc("1+"));
        assertNull(SearchTools.calc("ciao"));
    }

    @Test
    public void looksLikeMath() {
        assertTrue(SearchTools.looksLikeMath("2+2"));
        assertTrue(SearchTools.looksLikeMath("sqrt(9)"));
        assertTrue(SearchTools.looksLikeMath("3 x 4"));
        assertFalse(SearchTools.looksLikeMath(""));
        assertFalse(SearchTools.looksLikeMath("whatsapp"));
        assertFalse(SearchTools.looksLikeMath("2048"));
    }

    // ---------- convertitore ----------

    @Test
    public void convertTrovaUnaMisura() {
        List<String[]> r = SearchTools.convert("10 km in mi");
        assertEquals(1, r.size());
        assertTrue(r.get(0)[0].contains("6"));
    }

    @Test
    public void convertTemperatura() {
        List<String[]> r = SearchTools.convert("100 c in f");
        assertEquals(1, r.size());
        assertTrue(r.get(0)[0].contains("212"));
    }

    @Test
    public void convertSenzaDestinazioneDaPiuRisultati() {
        assertTrue(SearchTools.convert("70 kg").size() > 1);
    }

    @Test
    public void convertIgnoraIlResto() {
        assertTrue(SearchTools.convert("ciao").isEmpty());
        assertTrue(SearchTools.convert("10 km in kg").isEmpty()); // grandezze diverse
        assertTrue(SearchTools.convert("10 pizze").isEmpty());
    }

    // ---------- sveglia ----------

    @Test
    public void sveglia24Ore() {
        assertArrayEquals(new int[]{7, 30}, SearchTools.alarmTime("sveglia 7:30"));
        assertArrayEquals(new int[]{7, 0}, SearchTools.alarmTime("sveglia alle 7"));
        assertArrayEquals(new int[]{18, 45}, SearchTools.alarmTime("Sveglia 18.45"));
        assertArrayEquals(new int[]{6, 5}, SearchTools.alarmTime("svegliami alle 6h05"));
    }

    @Test
    public void svegliaAmPm() {
        assertArrayEquals(new int[]{18, 0}, SearchTools.alarmTime("alarm 6pm"));
        assertArrayEquals(new int[]{0, 15}, SearchTools.alarmTime("alarm 12:15 am"));
        assertArrayEquals(new int[]{12, 0}, SearchTools.alarmTime("alarm 12 pm"));
    }

    @Test
    public void svegliaNonValida() {
        assertNull(SearchTools.alarmTime("sveglia 25:00"));
        assertNull(SearchTools.alarmTime("sveglia 7:61"));
        assertNull(SearchTools.alarmTime("alarm 13pm"));
        assertNull(SearchTools.alarmTime("sveglia"));
        assertNull(SearchTools.alarmTime("pizza 7:30"));
    }

    // ---------- timer ----------

    @Test
    public void timerDurate() {
        assertEquals(600, SearchTools.timerSeconds("timer 10 min"));
        assertEquals(600, SearchTools.timerSeconds("timer 10"));          // senza unità: minuti
        assertEquals(5400, SearchTools.timerSeconds("timer 1h 30m"));
        assertEquals(5400, SearchTools.timerSeconds("timer 1 ora e 30 minuti"));
        assertEquals(45, SearchTools.timerSeconds("countdown 45 s"));
        assertEquals(90, SearchTools.timerSeconds("timer 1,5 min"));
    }

    @Test
    public void timerNonValido() {
        assertEquals(-1, SearchTools.timerSeconds("timer"));
        assertEquals(-1, SearchTools.timerSeconds("timer 10 min pizza"));
        assertEquals(-1, SearchTools.timerSeconds("timer 0"));
        assertEquals(-1, SearchTools.timerSeconds("timer 25 h"));          // oltre le 24 ore
        assertEquals(-1, SearchTools.timerSeconds("pizza 10 min"));
        assertEquals(-1, SearchTools.timerSeconds(null));
    }

    @Test
    public void durataScritta() {
        assertEquals("10 min", SearchTools.duration(600));
        assertEquals("1 h 30 min", SearchTools.duration(5400));
        assertEquals("45 s", SearchTools.duration(45));
        assertEquals("1 h 1 min 1 s", SearchTools.duration(3661));
        assertNotNull(SearchTools.duration(1));
    }
}
