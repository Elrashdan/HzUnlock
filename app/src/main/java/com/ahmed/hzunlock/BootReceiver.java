package com.ahmed.hzunlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart, starts the guard; it re-applies as soon as Shizuku is back. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!HzUtil.prefs(c).getBoolean(HzUtil.KEY_AUTOSTART, true)) return;
        if (!Unlocker.hasWorkingFix(c)) return;
        try {
            c.startForegroundService(new Intent(c, OverlayService.class));
        } catch (Exception ignored) {
            // Opening the app once starts it again.
        }
    }
}
