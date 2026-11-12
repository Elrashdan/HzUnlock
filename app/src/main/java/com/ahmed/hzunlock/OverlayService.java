package com.ahmed.hzunlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Background guard: whenever MagicOS drops the refresh rate, it puts it back
 * through Shizuku. Also waits for Shizuku after a restart and re-applies then.
 */
public class OverlayService extends Service {
    private static final String CHANNEL = "hz_guard";
    private static volatile boolean running = false;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private DisplayManager dm;
    private long lastApply = 0;

    public static boolean isRunning() { return running; }

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int id) {}
                @Override public void onDisplayRemoved(int id) {}
                @Override public void onDisplayChanged(int id) { scheduleReapply(); }
            };

    private final Shizuku.OnBinderReceivedListener binderListener = this::scheduleReapply;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!running) {
            running = true;
            dm = getSystemService(DisplayManager.class);
            dm.registerDisplayListener(displayListener, main);
            Shizuku.addBinderReceivedListenerSticky(binderListener);
        }
        scheduleReapply();
        return START_STICKY;
    }

    private void scheduleReapply() {
        long now = System.currentTimeMillis();
        if (now - lastApply < 1500) return;
        lastApply = now;
        worker.execute(() -> Unlocker.reapply(this));
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
                .setContentText("محافظ على " + Math.round(Unlocker.target(this)) + " هرتز")
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
        if (dm != null) dm.unregisterDisplayListener(displayListener);
        Shizuku.removeBinderReceivedListener(binderListener);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
