package it.rex.dotlauncher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Calendar;
import java.util.Random;

/**
 * Scene degli sfondi animati, tutte a puntini: una griglia di punti la cui luce cambia nel tempo.
 * Disegnate da zero sul Canvas, senza immagini né motori 3D. La stessa classe disegna lo sfondo
 * vero (DotLiveWallpaper) e le anteprime nella pagina Sfondi.
 */
final class LiveScenes {
    static final int BLACK_HOLE = 0, WEATHER = 1, WAVE = 2;
    static final String[] NAMES = {"Buco nero", "Meteo dal vivo", "Onda di puntini"};
    static final String[] DESCS = {"Un disco di luce che gira attorno al vuoto",
            "Pioggia, neve, sole o stelle in base al meteo di adesso", "Cerchi di luce che si allargano piano"};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(7);
    // particelle del meteo (pioggia, neve): posizioni da 0 a 1, velocità e fase
    private final float[] px = new float[120], py = new float[120], pv = new float[120], pph = new float[120];
    // stelle di sfondo: posizione da 0 a 1 e fase di scintillio
    private final float[] sx = new float[90], sy = new float[90], sph = new float[90];
    private float lastT = -1;

    int accent = 0xFFD71921;
    boolean ghost = true;

    LiveScenes() {
        for (int i = 0; i < px.length; i++) {
            px[i] = rnd.nextFloat();
            py[i] = rnd.nextFloat();
            pv[i] = 0.6f + rnd.nextFloat() * 0.8f;
            pph[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < sx.length; i++) {
            sx[i] = rnd.nextFloat();
            sy[i] = rnd.nextFloat();
            sph[i] = rnd.nextFloat() * 6.28f;
        }
    }

    /**
     * Disegna la scena al tempo t (secondi). pitch = distanza fra i puntini in pixel.
     * shift = spostamento orizzontale per la parallasse (in pixel).
     */
    void draw(Canvas cv, int scene, float W, float H, float t, float pitch, float shift) {
        cv.drawColor(0xFF000000);
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        switch (scene) {
            case BLACK_HOLE: blackHole(cv, W, H, t, pitch, shift); break;
            case WEATHER: weather(cv, W, H, t, dt, pitch, shift); break;
            default: wave(cv, W, H, t, pitch, shift); break;
        }
    }

    // ---------------------------------------------------------------------
    // griglia: ogni puntino ha una luce da 0 a 1 e un colore
    // ---------------------------------------------------------------------

    private void dot(Canvas cv, float x, float y, float pitch, float light, int color) {
        if (light < 0.035f) {
            if (!ghost) return;
            p.setColor(0x16FFFFFF); // punto spento appena visibile, come sui display a matrice
            cv.drawCircle(x, y, pitch * 0.16f, p);
            return;
        }
        light = Math.min(1f, light);
        p.setColor(Color.argb(Math.round(70 + 185 * light), Color.red(color), Color.green(color), Color.blue(color)));
        cv.drawCircle(x, y, pitch * (0.16f + 0.27f * (float) Math.sqrt(light)), p);
    }

    private static float smooth(float e0, float e1, float x) {
        float k = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return k * k * (3 - 2 * k);
    }

    private static int mix(int a, int b, float k) {
        k = Math.max(0f, Math.min(1f, k));
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * k),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * k),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
    }

    // ---------------------------------------------------------------------
    // buco nero: disco di accrescimento inclinato, anello di fotoni e l'arco del disco piegato sopra
    // ---------------------------------------------------------------------

    private void blackHole(Canvas cv, float W, float H, float t, float pitch, float shift) {
        float cx = W / 2f + shift * 0.25f, cy = H * 0.4f;
        float R = Math.min(W, H) * 0.19f;           // orizzonte degli eventi
        float tilt = 0.3f;                           // disco visto quasi di taglio
        int hot = 0xFFFFF3DD, warm = 0xFFFFA040, deep = 0xFFB0301A;
        int cols = (int) (W / pitch) + 2, rows = (int) (H / pitch) + 2;
        float ox = (W - (cols - 1) * pitch) / 2f, oy = (H - (rows - 1) * pitch) / 2f;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = ox + c * pitch, y = oy + r * pitch;
                float dx = x - cx, dy = y - cy;
                float rs = (float) Math.hypot(dx, dy);       // distanza sullo schermo
                float light = 0f;
                int color = warm;
                boolean front = false;
                // 1) disco: ellisse schiacciata attorno al centro; la metà davanti passa sopra il buco
                float dyd = dy / tilt;
                float rd = (float) Math.hypot(dx, dyd);
                if (rd > R * 1.15f && rd < R * 5.2f) {
                    float a = (float) Math.atan2(dyd, dx);
                    float band = smooth(R * 1.15f, R * 1.5f, rd) * (1f - smooth(R * 2.4f, R * 5.2f, rd));
                    float spin = t * (2.2f * R / rd);       // più veloce vicino al centro
                    float streak = 0.6f + 0.4f * (float) Math.sin(a * 4 - spin * 3 + rd / R * 2.6f);
                    float doppler = 0.7f + 0.5f * (float) Math.cos(a + 0.3f); // il lato che viene verso di noi è più luminoso
                    boolean behind = dy < 0 && rs < R * 1.05f;
                    if (!behind) {
                        light = band * streak * doppler * 1.15f;
                        front = dy >= 0;
                        color = mix(hot, warm, smooth(R * 1.2f, R * 3f, rd));
                        color = mix(color, deep, smooth(R * 2.6f, R * 5f, rd) * 0.8f);
                    }
                }
                // 2) il lato lontano del disco, piegato dalla gravità in un arco attorno al buco
                if (rs > R * 1.02f && rs < R * 1.9f) {
                    float a = (float) Math.atan2(dy, dx);
                    float arc = smooth(R * 1.02f, R * 1.2f, rs) * (1f - smooth(R * 1.45f, R * 1.9f, rs));
                    float top = dy < 0 ? 1f : 0.55f;
                    float l = arc * top * (0.75f + 0.25f * (float) Math.sin(a * 6 + t * 2.4f));
                    if (l > light) {
                        light = l;
                        color = mix(hot, warm, 0.3f);
                    }
                }
                // 3) anello di fotoni sottile sul bordo
                float ring = 1f - Math.min(1f, Math.abs(rs - R) / (pitch * 0.8f));
                if (ring > light) {
                    light = ring * (0.85f + 0.15f * (float) Math.sin(t * 3 + rs));
                    color = hot;
                }
                // 4) bagliore tenue attorno
                float glow = 0.22f * (1f - smooth(R, R * 3.2f, rs));
                if (glow > light && rs >= R) {
                    light = glow;
                    color = warm;
                }
                // 5) dentro l'orizzonte: buio, tranne il disco che ci passa davanti
                if (rs < R * 0.97f && !front) light = 0f;
                dot(cv, x, y, pitch, light, color);
            }
        }
        stars(cv, W, H, t, pitch, cx, cy, R * 5f, 0.7f);
    }

    /** Stelle che scintillano, lontane dal centro (raggio libero), agganciate alla griglia. */
    private void stars(Canvas cv, float W, float H, float t, float pitch, float cx, float cy, float clear, float max) {
        for (int i = 0; i < sx.length; i++) {
            float x = Math.round(sx[i] * W / pitch) * pitch, y = Math.round(sy[i] * H / pitch) * pitch;
            if (Math.hypot(x - cx, (y - cy) * 1.8f) < clear) continue;
            float l = max * (0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(t * 1.3f + sph[i])));
            p.setColor(Color.argb(Math.round(255 * l), 255, 255, 255));
            cv.drawCircle(x, y, pitch * 0.2f, p);
        }
    }

    // ---------------------------------------------------------------------
    // meteo dal vivo: dal codice meteo di adesso (Open-Meteo, già letto dalla home)
    // ---------------------------------------------------------------------

    private void weather(Canvas cv, float W, float H, float t, float dt, float pitch, float shift) {
        int code = State.wOk ? State.wCode : 0;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = hour < 7 || hour >= 20;
        boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean cloudy = code == 2 || code == 3 || code == 45 || code == 48;
        boolean storm = code >= 95;
        // griglia di fondo: sole o luna e nuvole che passano
        int cols = (int) (W / pitch) + 2, rows = (int) (H / pitch) + 2;
        float ox = (W - (cols - 1) * pitch) / 2f, oy = (H - (rows - 1) * pitch) / 2f;
        float sunX = W * 0.72f + shift * 0.15f, sunY = H * 0.2f, sunR = Math.min(W, H) * (night ? 0.08f : 0.11f);
        float flash = storm && ((int) (t * 0.6f) % 7 == 0) && (t * 0.6f) % 1f < 0.08f ? 0.5f : 0f; // lampo ogni tanto
        int sunC = night ? 0xFFE8EEFF : 0xFFFFC14D, cloudC = rain || storm ? 0xFF8A93A6 : 0xFFDDE3EE;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = ox + c * pitch, y = oy + r * pitch;
                float light = 0, ld;
                int color = sunC;
                if (!rain && !snow) {
                    float d = (float) Math.hypot(x - sunX, y - sunY);
                    ld = d < sunR ? 1f : (night ? 0f : 0.55f * (1f - smooth(sunR, sunR * 3.2f, d)) * (0.85f + 0.15f * (float) Math.sin(t * 0.8f)));
                    light = ld;
                }
                if (cloudy || rain || snow || storm) {
                    // nuvole: somma di macchie morbide che scorrono piano
                    float cl = 0;
                    for (int k = 0; k < 4; k++) {
                        float bx = ((k * 0.31f + t * 0.012f * (1 + k * 0.3f)) % 1.4f - 0.2f) * W + shift * 0.3f;
                        float by = H * (0.12f + 0.07f * k);
                        float bw = W * (0.28f + 0.06f * k), bh = H * 0.07f;
                        float e = ((x - bx) * (x - bx)) / (bw * bw) + ((y - by) * (y - by)) / (bh * bh);
                        cl += (float) Math.exp(-e * 1.6f);
                    }
                    cl = Math.min(1f, cl) * (rain || storm ? 0.75f : 0.6f);
                    if (cl > light) {
                        light = cl;
                        color = cloudC;
                    }
                }
                if (flash > 0 && y < H * 0.45f) {
                    light = Math.min(1f, light + flash);
                    color = 0xFFFFFFFF;
                }
                dot(cv, x, y, pitch, light, color);
            }
        }
        if (night && !rain && !snow) stars(cv, W, H, t, pitch, sunX, sunY, sunR * 2.5f, 0.85f);
        if (rain) particles(cv, W, H, t, dt, pitch, true, storm);
        else if (snow) particles(cv, W, H, t, dt, pitch, false, false);
    }

    /** Pioggia (scie di tre puntini, in diagonale) o neve (fiocchi che ondeggiano), agganciate alla griglia. */
    private void particles(Canvas cv, float W, float H, float t, float dt, float pitch, boolean rain, boolean heavy) {
        int n = rain ? (heavy ? 120 : 80) : 70;
        for (int i = 0; i < n; i++) {
            float speed = rain ? 0.55f * pv[i] : 0.06f * pv[i];
            py[i] += speed * dt;
            if (rain) px[i] += speed * dt * 0.18f;
            if (py[i] > 1.05f) {
                py[i] = -0.05f;
                px[i] = rnd.nextFloat();
            }
            if (px[i] > 1.05f) px[i] -= 1.1f;
            float x = px[i] * W, y = py[i] * H;
            if (!rain) x += (float) Math.sin(t * 0.9f + pph[i]) * pitch * 1.5f;
            float gx = Math.round(x / pitch) * pitch, gy = Math.round(y / pitch) * pitch;
            if (rain) {
                for (int k = 0; k < 3; k++) {
                    p.setColor(Color.argb(220 - k * 70, 170, 205, 255));
                    cv.drawCircle(gx - k * pitch * 0.18f, gy - k * pitch, pitch * (0.32f - k * 0.06f), p);
                }
            } else {
                p.setColor(Color.argb(Math.round(150 + 100 * pv[i] / 1.4f), 255, 255, 255));
                cv.drawCircle(gx, gy, pitch * (0.22f + 0.12f * pv[i]), p);
            }
        }
    }

    // ---------------------------------------------------------------------
    // onda di puntini: due sorgenti che si muovono piano, nel colore d'accento
    // ---------------------------------------------------------------------

    private void wave(Canvas cv, float W, float H, float t, float pitch, float shift) {
        int cols = (int) (W / pitch) + 2, rows = (int) (H / pitch) + 2;
        float ox = (W - (cols - 1) * pitch) / 2f, oy = (H - (rows - 1) * pitch) / 2f;
        float ax = W * (0.5f + 0.25f * (float) Math.sin(t * 0.11f)) + shift * 0.3f, ay = H * (0.35f + 0.1f * (float) Math.cos(t * 0.07f));
        float bx = W * (0.5f + 0.3f * (float) Math.cos(t * 0.09f)) + shift * 0.3f, by = H * (0.75f + 0.08f * (float) Math.sin(t * 0.13f));
        float lam = Math.min(W, H) * 0.13f;
        int acc = 0xFF000000 | accent;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = ox + c * pitch, y = oy + r * pitch;
                float da = (float) Math.hypot(x - ax, y - ay), db = (float) Math.hypot(x - bx, y - by);
                float w = (float) (Math.sin(da / lam - t * 1.1f) + Math.sin(db / lam * 1.3f - t * 0.8f)) * 0.25f + 0.5f;
                float l = (float) Math.pow(Math.max(0f, w), 3) * 0.7f; // tenue: sta dietro alle icone
                dot(cv, x, y, pitch, l, mix(acc, 0xFFFFFFFF, l * 0.35f));
            }
        }
    }
}
