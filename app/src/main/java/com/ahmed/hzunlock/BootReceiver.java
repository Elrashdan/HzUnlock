package com.ahmed.hzunlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.Display;

/** Re-applies everything after a restart if the user turned on auto-start. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!HzUtil.prefs(c).getBoolean(HzUtil.KEY_AUTOSTART, false)) return;

        if (HzUtil.prefs(c).getBoolean(HzUtil.KEY_SETTINGS_APPLIED, false)) {
            Display.Mode m = HzUtil.bestMode(HzUtil.defaultDisplay(c));
            HzUtil.applySettings(c, m.getRefreshRate());
        }
        try {
            c.startForegroundService(new Intent(c, OverlayService.class));
        } catch (Exception ignored) {
            // Some systems block starts from boot; opening the app once starts it again.
        }
    }
}
