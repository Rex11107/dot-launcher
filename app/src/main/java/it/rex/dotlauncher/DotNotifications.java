package it.rex.dotlauncher;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accesso alle notifiche (facoltativo): pallini sulle icone, anteprima nel menu delle app
 * e controllo della musica in riproduzione. Nulla viene salvato o inviato altrove.
 */
public class DotNotifications extends NotificationListenerService {
    interface Listener {
        void onNotificationsChanged();
        void onMediaChanged();
    }

    private static DotNotifications instance;
    private static Listener listener;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** Notifiche visibili per pacchetto (solo quelle che l'utente vede, non i servizi in corso). */
    private static Map<String, List<StatusBarNotification>> byPkg = new HashMap<>();

    // musica
    static String mTitle = "", mArtist = "", mPkg = "";
    static Bitmap mArt;
    static boolean mPlaying, mActive;
    private static MediaController controller;
    private MediaSessionManager msm;

    static boolean active() {
        return instance != null;
    }

    static void setListener(Listener l) {
        listener = l;
    }

    static int count(String pkg) {
        List<StatusBarNotification> l = byPkg.get(pkg);
        return l == null ? 0 : l.size();
    }

    static List<StatusBarNotification> of(String pkg) {
        List<StatusBarNotification> l = byPkg.get(pkg);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    static void dismissAll(String pkg) {
        DotNotifications s = instance;
        if (s == null) return;
        for (StatusBarNotification n : of(pkg)) {
            try {
                s.cancelNotification(n.getKey());
            } catch (Exception ignored) {
            }
        }
    }

    /** Non disturbare (solo priorità) acceso o spento; false se l'accesso alle notifiche non è attivo. */
    static boolean setDnd(boolean on) {
        DotNotifications s = instance;
        if (s == null) return false;
        try {
            s.requestInterruptionFilter(on ? INTERRUPTION_FILTER_PRIORITY : INTERRUPTION_FILTER_ALL);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- ciclo di vita ----------

    @Override
    public void onListenerConnected() {
        instance = this;
        rebuild();
        try {
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, DotNotifications.class);
            msm.addOnActiveSessionsChangedListener(sessions, me, UI);
            pickSession(msm.getActiveSessions(me));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        byPkg = new HashMap<>();
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
        }
        setController(null);
        notifyChanged();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        rebuild();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        rebuild();
    }

    private void rebuild() {
        Map<String, List<StatusBarNotification>> m = new HashMap<>();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) {
                for (StatusBarNotification n : all) {
                    Notification no = n.getNotification();
                    if (n.isOngoing() || (no.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    List<StatusBarNotification> l = m.get(n.getPackageName());
                    if (l == null) m.put(n.getPackageName(), l = new ArrayList<>());
                    l.add(n);
                }
            }
        } catch (Exception ignored) {
        }
        byPkg = m;
        notifyChanged();
    }

    private static void notifyChanged() {
        UI.post(() -> {
            if (listener != null) listener.onNotificationsChanged();
        });
    }

    // ---------- musica ----------

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::pickSession;

    /** Sceglie la sessione da mostrare: quella in riproduzione, altrimenti la più recente. */
    private void pickSession(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        setController(best);
    }

    private static final MediaController.Callback CB = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            readMedia();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            readMedia();
        }

        @Override
        public void onSessionDestroyed() {
            setController(null);
        }
    };

    private static void setController(MediaController c) {
        if (controller != null) {
            try {
                controller.unregisterCallback(CB);
            } catch (Exception ignored) {
            }
        }
        controller = c;
        if (c != null) {
            try {
                c.registerCallback(CB, UI);
            } catch (Exception ignored) {
            }
        }
        readMedia();
    }

    private static void readMedia() {
        MediaController c = controller;
        if (c == null) {
            mActive = false;
            mPlaying = false;
            mTitle = mArtist = mPkg = "";
            mArt = null;
        } else {
            MediaMetadata md = c.getMetadata();
            PlaybackState st = c.getPlaybackState();
            mActive = true;
            mPkg = c.getPackageName();
            mPlaying = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            mTitle = md == null ? "" : str(md.getString(MediaMetadata.METADATA_KEY_TITLE));
            mArtist = md == null ? "" : str(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
            if (mArtist.isEmpty() && md != null) mArtist = str(md.getString(MediaMetadata.METADATA_KEY_ALBUM));
            Bitmap art = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && md != null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            mArt = art;
        }
        UI.post(() -> {
            if (listener != null) listener.onMediaChanged();
        });
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    /** 1 indietro, 2 play/pausa, 3 avanti. */
    static boolean control(int what) {
        MediaController c = controller;
        if (c == null) return false;
        try {
            MediaController.TransportControls t = c.getTransportControls();
            if (what == 1) t.skipToPrevious();
            else if (what == 3) t.skipToNext();
            else if (mPlaying) t.pause();
            else t.play();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
