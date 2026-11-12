package com.ahmed.hzunlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

/** Shared helpers: find the fastest display mode, read settings, preferences. */
public final class HzUtil {
    private HzUtil() {}

    public static final String PREFS = "hz_prefs";
    public static final String KEY_AUTOSTART = "autostart";
    public static final String KEY_SETTINGS_APPLIED = "settings_applied";

    public static Display defaultDisplay(Context c) {
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        return dm.getDisplay(Display.DEFAULT_DISPLAY);
    }

    /** Highest-refresh mode at the current resolution (so the screen never changes resolution). */
    public static Display.Mode bestMode(Display d) {
        Display.Mode cur = d.getMode();
        Display.Mode best = cur;
        for (Display.Mode m : d.getSupportedModes()) {
            boolean sameRes = m.getPhysicalWidth() == cur.getPhysicalWidth()
                    && m.getPhysicalHeight() == cur.getPhysicalHeight();
            if (sameRes && m.getRefreshRate() > best.getRefreshRate()) best = m;
        }
        return best;
    }

    public static String readSetting(Context c, String key) {
        try {
            String v = Settings.System.getString(c.getContentResolver(), key);
            return v == null ? "—" : v;
        } catch (Exception e) {
            return "?";
        }
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
