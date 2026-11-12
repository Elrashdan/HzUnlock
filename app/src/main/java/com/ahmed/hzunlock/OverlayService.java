package com.ahmed.hzunlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Keeps an invisible 1x1 overlay window on screen that asks the system for the
 * fastest display mode. Because the overlay sits above every app, its request
 * applies to whatever is on screen.
 */
public class OverlayService extends Service {
    private static final String CHANNEL = "hz_overlay";
    private static volatile boolean running = false;

    private WindowManager wm;
    private View overlay;

    public static boolean isRunning() { return running; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        addOverlay();
        running = true;
        return START_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Hz Unlock", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Display.Mode m = HzUtil.bestMode(HzUtil.defaultDisplay(this));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Hz Unlock شغّال")
                .setContentText("طالب " + Math.round(m.getRefreshRate()) + " هرتز")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void addOverlay() {
        if (overlay != null) return;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display.Mode m = HzUtil.bestMode(HzUtil.defaultDisplay(this));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.preferredDisplayModeId = m.getModeId();
        lp.preferredRefreshRate = m.getRefreshRate();

        overlay = new View(this);
        try {
            wm.addView(overlay, lp);
        } catch (Exception e) {
            overlay = null;
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (overlay != null && wm != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
