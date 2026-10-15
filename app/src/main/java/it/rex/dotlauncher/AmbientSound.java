package it.rex.dotlauncher;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Suoni ambientali generati al momento dal codice: niente file da scaricare e niente diritti di terzi.
 * Onde del mare (rumore "marrone" che va e viene), pioggia (fruscio con gocce) e campanelle (note
 * pentatoniche che svaniscono piano).
 */
final class AmbientSound {
    static final String OCEAN = "gen:ocean", RAIN = "gen:rain", CHIMES = "gen:chimes";
    static final String[][] TYPES = {{OCEAN, "Onde del mare"}, {RAIN, "Pioggia"}, {CHIMES, "Campanelle"}};

    private static final int RATE = 22050;
    private final String type;
    private final boolean alarm;
    private volatile boolean on;
    private AudioTrack track;
    private Thread thread;

    AmbientSound(String type, boolean alarm) {
        this.type = type;
        this.alarm = alarm;
    }

    static boolean is(String sound) {
        return sound != null && sound.startsWith("gen:");
    }

    static String name(String sound) {
        for (String[] t : TYPES) if (t[0].equals(sound)) return T.t(t[1]);
        return null;
    }

    void start() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(alarm ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(min, RATE)) // circa mezzo secondo
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        on = true;
        track.play();
        thread = new Thread(this::loop, "dot-ambient");
        thread.start();
    }

    void stop() {
        on = false;
        try {
            if (thread != null) thread.join(300);
        } catch (InterruptedException ignored) {
        }
        if (track != null) {
            try {
                track.stop();
            } catch (Exception ignored) {
            }
            track.release();
            track = null;
        }
    }

    private void loop() {
        short[] buf = new short[RATE / 10];
        Random r = new Random();
        double t = 0, brown = 0, lp = 0, lp2 = 0, drop = 0, dropLp = 0;
        // campanelle: fino a 6 note che suonano insieme
        double[] freq = new double[6], amp = new double[6], ph = new double[6];
        double[] scale = {523.25, 587.33, 659.25, 783.99, 880.00, 1046.5, 1174.7, 1318.5};
        double nextChime = 0.3;
        // onde: ogni onda dura 6-11 secondi
        double waveStart = 0, waveLen = 8;
        double fadeIn = 0;
        while (on) {
            for (int i = 0; i < buf.length; i++) {
                double v;
                double white = r.nextDouble() * 2 - 1;
                switch (type) {
                    case OCEAN: {
                        brown = (brown + 0.02 * white) * 0.998;
                        lp += (brown - lp) * 0.15;
                        double ph0 = (t - waveStart) / waveLen;
                        if (ph0 >= 1) {
                            waveStart = t;
                            waveLen = 6 + r.nextDouble() * 5;
                            ph0 = 0;
                        }
                        // l'onda sale, si rompe e si ritira
                        double env = ph0 < 0.35 ? Math.pow(ph0 / 0.35, 2) : Math.exp(-(ph0 - 0.35) * 4.5);
                        double foam = white * 0.12 * env * env;
                        v = lp * 1.5 * (0.25 + 0.75 * env) + foam;
                        break;
                    }
                    case RAIN: {
                        lp += (white - lp) * 0.35;
                        lp2 += (lp - lp2) * 0.5;
                        if (r.nextDouble() < 25.0 / RATE) drop = 0.6 + r.nextDouble() * 0.4; // una goccia
                        drop *= 0.996;
                        dropLp += (white * drop - dropLp) * 0.7;
                        v = (lp2 * 0.35 + dropLp * 0.5) * 1.7;
                        break;
                    }
                    default: { // campanelle
                        if (t >= nextChime) {
                            int k = r.nextInt(freq.length);
                            freq[k] = scale[r.nextInt(scale.length)];
                            amp[k] = 0.27 + r.nextDouble() * 0.18;
                            ph[k] = 0;
                            nextChime = t + 0.35 + r.nextDouble() * 1.4;
                        }
                        v = 0;
                        for (int k = 0; k < freq.length; k++) {
                            if (amp[k] < 0.0005) continue;
                            ph[k] += 2 * Math.PI * freq[k] / RATE;
                            v += amp[k] * (Math.sin(ph[k]) + 0.3 * Math.sin(2.76 * ph[k]));
                            amp[k] *= 0.99993;
                        }
                        lp += (white - lp) * 0.02;
                        v += lp * 0.05; // un filo d'aria
                        break;
                    }
                }
                fadeIn = Math.min(1, fadeIn + 1.0 / (RATE * 2)); // 2 secondi di dissolvenza all'inizio
                v *= fadeIn;
                buf[i] = (short) Math.max(-32767, Math.min(32767, v * 26000));
                t += 1.0 / RATE;
            }
            AudioTrack tr = track;
            if (tr == null || !on) break;
            tr.write(buf, 0, buf.length);
        }
    }
}
