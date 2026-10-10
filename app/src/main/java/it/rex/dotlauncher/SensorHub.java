package it.rex.dotlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sensori per i widget: bussola e contapassi.
 * Restano accesi solo mentre la home è visibile e solo se c'è il widget relativo.
 */
final class SensorHub implements SensorEventListener {
    interface Listener {
        void onSensorUpdate(String typePrefix);
    }

    private final SensorManager sm;
    private final SharedPreferences prefs;
    private final Listener listener;
    private boolean compassOn, stepsOn;

    // bussola
    private final float[] grav = new float[3], geo = new float[3];
    private boolean hasGrav, hasGeo;
    private final float[] rot = new float[9], orient = new float[3];
    private long lastCompassUi;
    private float lastShown = -999;

    SensorHub(Context c, SharedPreferences prefs, Listener l) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        this.prefs = prefs;
        this.listener = l;
        State.hasCompass = sm != null && (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
                || sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null
                || (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null));
        State.hasSteps = sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
        State.steps = todaySaved();
    }

    static boolean stepPermission(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED;
    }

    void start(Context c, boolean compass, boolean steps) {
        stop();
        if (sm == null) return;
        if (compass) {
            Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rv == null) rv = sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
            if (rv != null) {
                compassOn = sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
            } else {
                Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                Sensor m = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (a != null && m != null) {
                    compassOn = sm.registerListener(this, a, SensorManager.SENSOR_DELAY_UI)
                            & sm.registerListener(this, m, SensorManager.SENSOR_DELAY_UI);
                }
            }
        }
        State.stepPerm = stepPermission(c);
        if (steps && State.stepPerm) {
            Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (s != null) stepsOn = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    void stop() {
        if (sm != null && (compassOn || stepsOn)) sm.unregisterListener(this);
        compassOn = stepsOn = false;
        hasGrav = hasGeo = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        switch (e.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(rot, e.values);
                heading();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                lowPass(e.values, grav, hasGrav);
                hasGrav = true;
                if (hasGeo && SensorManager.getRotationMatrix(rot, null, grav, geo)) heading();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                lowPass(e.values, geo, hasGeo);
                hasGeo = true;
                State.compassAccuracy = e.accuracy;
                if (hasGrav && SensorManager.getRotationMatrix(rot, null, grav, geo)) heading();
                break;
            case Sensor.TYPE_STEP_COUNTER:
                steps(e.values[0]);
                break;
        }
    }

    private static void lowPass(float[] in, float[] out, boolean init) {
        float k = init ? 0.15f : 1f;
        for (int i = 0; i < 3; i++) out[i] += k * (in[i] - out[i]);
    }

    private void heading() {
        SensorManager.getOrientation(rot, orient);
        float deg = (float) Math.toDegrees(orient[0]);
        if (deg < 0) deg += 360f;
        // media "circolare" per evitare tremolii e salti tra 359° e 0°
        float d = deg - State.heading;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        State.heading = (State.heading + d * 0.25f + 360f) % 360f;
        State.headingOk = true;
        long now = SystemClock.uptimeMillis();
        float moved = Math.abs(State.heading - lastShown);
        if (moved > 180) moved = 360 - moved;
        if (now - lastCompassUi > 33 && moved > 0.4f) {
            lastCompassUi = now;
            lastShown = State.heading;
            listener.onSensorUpdate("compass");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD || s.getType() == Sensor.TYPE_ROTATION_VECTOR
                || s.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            State.compassAccuracy = accuracy;
        }
    }

    // ---------- contapassi ----------

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    private int todaySaved() {
        if (!today().equals(prefs.getString("stepDay", ""))) return 0;
        return prefs.getInt("stepToday", 0);
    }

    /**
     * Il sensore conta i passi dall'accensione del telefono: si tiene una base per il giorno corrente.
     * Dopo un riavvio il valore riparte da zero e si somma a quanto già contato.
     */
    private void steps(float raw) {
        float base = prefs.getFloat("stepBase", -1f);
        float last = prefs.getFloat("stepLast", -1f);
        int acc = prefs.getInt("stepAcc", 0);
        String day = today();
        SharedPreferences.Editor ed = prefs.edit();
        if (!day.equals(prefs.getString("stepDay", ""))) {
            // nuovo giorno: i passi fatti da quando la home li ha letti l'ultima volta vanno al giorno nuovo
            base = last >= 0 && last <= raw ? last : raw;
            acc = 0;
            ed.putString("stepDay", day);
        } else if (base < 0) {
            base = raw;
        } else if (raw < last) {
            // riavvio del telefono
            acc += Math.max(0, Math.round(last - base));
            base = 0;
        }
        int today = acc + Math.max(0, Math.round(raw - base));
        ed.putFloat("stepBase", base).putFloat("stepLast", raw).putInt("stepAcc", acc).putInt("stepToday", today).apply();
        if (today != State.steps) {
            State.steps = today;
            listener.onSensorUpdate("steps");
        }
    }
}
