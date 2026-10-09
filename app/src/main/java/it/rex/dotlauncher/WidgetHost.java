package it.rex.dotlauncher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Host dei widget di sistema, con pressione prolungata per il menu del widget. */
class WidgetHost extends AppWidgetHost {
    WidgetHost(Context c, int hostId) {
        super(c, hostId);
    }

    @Override
    protected AppWidgetHostView onCreateView(Context c, int id, AppWidgetProviderInfo info) {
        return new LongPressView(c);
    }

    static class LongPressView extends AppWidgetHostView {
        private boolean fired;
        private boolean isPressedDown;
        private float downX, downY;
        private final int slop;
        private final Runnable check = () -> {
            if (getParent() != null && isPressedDown) {
                fired = performLongClick();
            }
        };

        LongPressView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fired = false;
                    isPressedDown = true;
                    downX = ev.getX();
                    downY = ev.getY();
                    postDelayed(check, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) {
                        isPressedDown = false;
                        removeCallbacks(check);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isPressedDown = false;
                    removeCallbacks(check);
                    break;
            }
            return fired;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (fired) {
                int a = ev.getActionMasked();
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) fired = false;
                return true;
            }
            return super.onTouchEvent(ev);
        }

        @Override
        public void cancelLongPress() {
            super.cancelLongPress();
            isPressedDown = false;
            removeCallbacks(check);
        }
    }
}
