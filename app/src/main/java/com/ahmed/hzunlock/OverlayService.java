package com.ahmed.hzunlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Keeps the RateKeeper layer on screen above every app, and re-applies the
 * refresh-rate settings once Shizuku is back after a restart.
 */
public class OverlayService extends Service {
    private static final String CHANNEL = "hz_guard";
    private static volatile boolean running = false;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WindowManager wm;
    private RateKeeper keeper;

    public static boolean isRunning() { return running; }

    private final Shizuku.OnBinderReceivedListener binderListener =
            () -> worker.execute(() -> Unlocker.reapply(this));

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!running) {
            running = true;
            Shizuku.addBinderReceivedListenerSticky(binderListener);
        }
        addKeeper();
        return START_STICKY;
    }

    private void addKeeper() {
        if (keeper != null) return;
        if (!Settings.canDrawOverlays(this)) return;

        Display.Mode best = HzUtil.bestMode(HzUtil.defaultDisplay(this));
        float hz = best.getRefreshRate();
        int screenH = getResources().getDisplayMetrics().heightPixels;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                Math.max(200, screenH / 4),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // Must stay at or below 0.8, or Android blocks touches on apps underneath.
        lp.alpha = 0.5f;
        lp.preferredDisplayModeId = best.getModeId();
        lp.preferredRefreshRate = hz;
        setHiddenMinMax(lp, hz);

        wm = getSystemService(WindowManager.class);
        keeper = new RateKeeper(this, hz);
        try {
            wm.addView(keeper.view(), lp);
        } catch (Exception e) {
            keeper = null;
        }
    }

    /** Window-level min/max rate request; hidden API, so reached by reflection. */
    private static void setHiddenMinMax(WindowManager.LayoutParams lp, float hz) {
        try {
            if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("L");
            WindowManager.LayoutParams.class
                    .getField("preferredMinDisplayRefreshRate").setFloat(lp, hz);
            WindowManager.LayoutParams.class
                    .getField("preferredMaxDisplayRefreshRate").setFloat(lp, hz);
        } catch (Throwable ignored) {}
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Hz Unlock", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Hz Unlock شغّال")
                .setContentText("طالب " + Math.round(Unlocker.target(this)) + " هرتز")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (keeper != null) {
            keeper.stop();
            try { wm.removeView(keeper.view()); } catch (Exception ignored) {}
            keeper = null;
        }
        Shizuku.removeBinderReceivedListener(binderListener);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
