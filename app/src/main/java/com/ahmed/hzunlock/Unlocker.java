package com.ahmed.hzunlock;

import android.content.Context;

/**
 * Shizuku-side steps. Blocking: call from a background thread.
 *  prepare(): permissions + refresh-rate settings + keep the app alive.
 *  verify():  after the RateKeeper layer is up, checks the result and,
 *             if it still is not at the target, attaches a focused report.
 */
public final class Unlocker {
    private Unlocker() {}

    private static final String GREP = "grep -i -E 'refresh|rate|fps|hz|frame'";

    public static float target(Context c) {
        return HzUtil.bestMode(HzUtil.defaultDisplay(c)).getRefreshRate();
    }

    public static boolean atTarget(Context c) {
        return HzUtil.defaultDisplay(c).getRefreshRate() >= target(c) - 1f;
    }

    public static String prepare(Context c) {
        StringBuilder log = new StringBuilder();
        String pkg = c.getPackageName();
        int hz = Math.round(target(c));
        log.append("الهدف: ").append(hz).append(" هرتز\n");

        sh(log, "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS");
        sh(log, "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
        sh(log, "dumpsys deviceidle whitelist +" + pkg);
        sh(log, "settings put system peak_refresh_rate " + hz + ".0");
        sh(log, "settings put system min_refresh_rate " + hz + ".0");
        HzUtil.prefs(c).edit().putBoolean(HzUtil.KEY_SETTINGS_APPLIED, true).apply();
        log.append("بشغّل طبقة الـ ").append(hz).append(" هرتز...\n");
        return log.toString();
    }

    public static String verify(Context c) {
        int hz = Math.round(target(c));
        int now = Math.round(HzUtil.defaultDisplay(c).getRefreshRate());
        if (atTarget(c)) {
            return "✓ اشتغل! الشاشة على " + hz + " هرتز.\n"
                    + "سيب التطبيق في الخلفية، وهو هيفضل محافظ على المعامل.\n";
        }
        return "✗ لسه " + now + " هرتز حسب السيستم.\n"
                + "بص على مؤشر Show refresh rate، وابعتلي التقرير ده كله:\n"
                + "\n===== تقرير تفصيلي =====\n" + diagnostics() + '\n';
    }

    /** Used after a restart once Shizuku is back. */
    public static void reapply(Context c) {
        if (!ShizukuShell.isReady()) return;
        if (!HzUtil.prefs(c).getBoolean(HzUtil.KEY_SETTINGS_APPLIED, false)) return;
        int hz = Math.round(target(c));
        ShizukuShell.exec("settings put system peak_refresh_rate " + hz + ".0; "
                + "settings put system min_refresh_rate " + hz + ".0");
    }

    public static boolean hasWorkingFix(Context c) {
        return HzUtil.prefs(c).getBoolean(HzUtil.KEY_SETTINGS_APPLIED, false);
    }

    public static String reset(Context c) {
        StringBuilder log = new StringBuilder();
        sh(log, "settings put system min_refresh_rate 0.0");
        HzUtil.prefs(c).edit().putBoolean(HzUtil.KEY_SETTINGS_APPLIED, false).apply();
        log.append("اترجع للطبيعي.\n");
        return log.toString();
    }

    public static String diagnostics() {
        return "[display]\n" + ShizukuShell.exec(
                    "dumpsys display | grep -i -E 'DisplayModeSpecs|mActiveModeId|"
                    + "frameRateCategoryRate|mActiveRenderFrameRate' | cut -c1-260 | head -12")
                + "\n\n[surfaceflinger]\n" + ShizukuShell.exec(
                    "dumpsys SurfaceFlinger | grep -i -E 'vote|frameRate|refresh|fps|"
                    + "activeMode|desiredMode|policy|HzUnlock|hzunlock' | cut -c1-220 | head -60")
                + "\n\n[props]\n" + ShizukuShell.exec(
                    "getprop | grep -i -E 'refresh|fps|frame_rate|framerate|ltpo|small_area' | head -40")
                + "\n\n[settings]\n" + ShizukuShell.exec(
                    "settings list system | " + GREP + "; settings list secure | " + GREP
                    + "; settings list global | " + GREP);
    }

    private static void sh(StringBuilder log, String cmd) {
        String out = ShizukuShell.exec(cmd);
        log.append("$ ").append(cmd).append('\n');
        if (!out.isEmpty()) log.append(out).append('\n');
    }
}
