package it.rex.dotlauncher;

import android.animation.ValueAnimator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Tempi e curve delle animazioni, uguali in tutto il launcher.
 * Entrate più lunghe e decise, uscite più brevi: ciò che si chiude non deve far aspettare.
 */
final class Motion {
    /** Pannelli che entrano (cassetto, pagina a sinistra, impostazioni, pagine della guida). */
    static final long ENTER = 260;
    /** Pannelli che escono. */
    static final long EXIT = 180;
    /** Dissolvenze e cambi di contenuto sul posto. */
    static final long FADE = 160;
    /** Risposta al tocco (sollevare un'icona, rimbalzo indietro). */
    static final long QUICK = 120;
    /** Comparsa con rimbalzo (nuovo elemento sulla home). */
    static final long POP = 280;

    /** Decelerazione marcata: per ciò che entra. */
    static final Interpolator EASE_OUT = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    /** Accelerazione marcata: per ciò che esce. */
    static final Interpolator EASE_IN = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    /** Curva standard: per spostamenti e cambi sul posto. */
    static final Interpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    static Interpolator pop() {
        return new OvershootInterpolator(1.6f);
    }

    /** Le animazioni di sistema sono attive? (Opzioni sviluppatore → Scala animazione) */
    static boolean systemOn() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    private Motion() {}
}
