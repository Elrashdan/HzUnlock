package com.ahmed.hzunlock;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

/** Shared helpers: find the fastest display mode and write the refresh-rate settings. */
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

    public static boolean hasSecureSettings(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Sets peak and min refresh rate. Returns null on success, or an error message. */
    public static String applySettings(Context c, float hz) {
        if (!hasSecureSettings(c)) return "no_permission";
        try {
            ContentResolver cr = c.getContentResolver();
            Settings.System.putFloat(cr, "peak_refresh_rate", hz);
            Settings.System.putFloat(cr, "min_refresh_rate", hz);
            prefs(c).edit().putBoolean(KEY_SETTINGS_APPLIED, true).apply();
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Removes the forced minimum so the system can lower the rate again. */
    public static String resetSettings(Context c) {
        if (!hasSecureSettings(c)) return "no_permission";
        try {
            Settings.System.putFloat(c.getContentResolver(), "min_refresh_rate", 0f);
            prefs(c).edit().putBoolean(KEY_SETTINGS_APPLIED, false).apply();
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
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
