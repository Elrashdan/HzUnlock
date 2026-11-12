package com.ahmed.hzunlock;

import android.content.Context;
import android.view.Display;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The actual unlock steps, all run through Shizuku:
 *  1. write the standard refresh-rate settings,
 *  2. if the screen is still below the target, force the mode in SurfaceFlinger,
 *     trying candidate mode indexes until the display reports the target rate.
 * Blocking: call from a background thread.
 */
public final class Unlocker {
    private Unlocker() {}

    private static final String KEY_SF_INDEX = "sf_index";
    private static final String KEY_USER_PREFERRED = "user_preferred";

    private static String userPreferredCmd(Display.Mode m) {
        return "cmd display set-user-preferred-display-mode "
                + m.getPhysicalWidth() + " " + m.getPhysicalHeight() + " "
                + Math.round(m.getRefreshRate()) + ".0";
    }
    private static final String GREP = "grep -i -E 'refresh|rate|fps|hz|frame'";

    public static float target(Context c) {
        return HzUtil.bestMode(HzUtil.defaultDisplay(c)).getRefreshRate();
    }

    public static boolean atTarget(Context c) {
        return HzUtil.defaultDisplay(c).getRefreshRate() >= target(c) - 1f;
    }

    public static String unlock(Context c) {
        StringBuilder log = new StringBuilder();
        int hz = Math.round(target(c));
        log.append("الهدف: ").append(hz).append(" هرتز\n");

        sh(log, "pm grant " + c.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
        sh(log, "settings put system peak_refresh_rate " + hz + ".0");
        sh(log, "settings put system min_refresh_rate " + hz + ".0");
        HzUtil.prefs(c).edit().putBoolean(HzUtil.KEY_SETTINGS_APPLIED, true).apply();
        sleep(800);

        if (atTarget(c)) {
            log.append("✓ الإعدادات كفاية، الشاشة على ").append(hz).append(" هرتز\n");
            return log.toString();
        }
        log.append("الإعدادات لوحدها ما كفتش، بجرّب وضع الشاشة المفضّل...\n");
        Display.Mode best = HzUtil.bestMode(HzUtil.defaultDisplay(c));
        sh(log, userPreferredCmd(best));
        sleep(900);
        if (atTarget(c)) {
            HzUtil.prefs(c).edit().putBoolean(KEY_USER_PREFERRED, true).apply();
            log.append("✓ اشتغل! الشاشة على ").append(hz).append(" هرتز\n");
            return log.toString();
        }
        log.append("ما كفاش، بجرّب SurfaceFlinger...\n");

        if (forceSurfaceFlinger(c, log)) {
            log.append("✓ اشتغل! الشاشة على ").append(hz).append(" هرتز\n");
        } else {
            log.append("✗ ولا طريقة غيّرت المعامل اللي السيستم بيبلّغ عنه.\n");
            log.append("بص على مؤشر Show refresh rate نفسه، يمكن اتغيّر فعلًا.\n");
            log.append("\n===== تقرير تفصيلي =====\n").append(diagnostics()).append('\n');
        }
        return log.toString();
    }

    /** Tries candidate indexes; remembers the one that works. */
    private static boolean forceSurfaceFlinger(Context c, StringBuilder log) {
        Display d = HzUtil.defaultDisplay(c);
        Display.Mode best = HzUtil.bestMode(d);
        Set<Integer> candidates = new LinkedHashSet<>();
        int saved = HzUtil.prefs(c).getInt(KEY_SF_INDEX, -1);
        if (saved >= 0) candidates.add(saved);
        candidates.add(best.getModeId() - 1);
        candidates.add(best.getModeId());
        for (int i = 0; i <= d.getSupportedModes().length; i++) candidates.add(i);

        for (int idx : candidates) {
            if (idx < 0) continue;
            String out = ShizukuShell.exec("service call SurfaceFlinger 1035 i32 " + idx);
            sleep(900);
            boolean ok = atTarget(c);
            log.append("  رقم ").append(idx).append(": ")
                    .append(ok ? "✓" : "✗")
                    .append(out.isEmpty() ? "" : "  [" + out.replace('\n', ' ') + "]")
                    .append('\n');
            if (ok) {
                HzUtil.prefs(c).edit().putInt(KEY_SF_INDEX, idx).apply();
                return true;
            }
        }
        return false;
    }

    /** Quick re-apply used by the background service; no searching. */
    public static void reapply(Context c) {
        if (!ShizukuShell.isReady() || atTarget(c)) return;
        int idx = HzUtil.prefs(c).getInt(KEY_SF_INDEX, -1);
        if (HzUtil.prefs(c).getBoolean(KEY_USER_PREFERRED, false)) {
            ShizukuShell.exec(userPreferredCmd(HzUtil.bestMode(HzUtil.defaultDisplay(c))));
        } else if (idx >= 0) {
            ShizukuShell.exec("service call SurfaceFlinger 1035 i32 " + idx);
        } else if (HzUtil.prefs(c).getBoolean(HzUtil.KEY_SETTINGS_APPLIED, false)) {
            int hz = Math.round(target(c));
            ShizukuShell.exec("settings put system peak_refresh_rate " + hz + ".0; "
                    + "settings put system min_refresh_rate " + hz + ".0");
        }
    }

    public static boolean hasWorkingFix(Context c) {
        return HzUtil.prefs(c).getInt(KEY_SF_INDEX, -1) >= 0
                || HzUtil.prefs(c).getBoolean(KEY_USER_PREFERRED, false)
                || HzUtil.prefs(c).getBoolean(HzUtil.KEY_SETTINGS_APPLIED, false);
    }

    public static String reset(Context c) {
        StringBuilder log = new StringBuilder();
        sh(log, "settings put system min_refresh_rate 0.0");
        sh(log, "service call SurfaceFlinger 1035 i32 -1");
        sh(log, "cmd display reset-user-preferred-display-mode");
        HzUtil.prefs(c).edit()
                .remove(KEY_USER_PREFERRED)
                .putBoolean(HzUtil.KEY_SETTINGS_APPLIED, false)
                .remove(KEY_SF_INDEX).apply();
        log.append("اترجع للطبيعي. لو لسه ثابت، اعمل ريستارت.\n");
        return log.toString();
    }

    public static String diagnostics() {
        return "[votes]\n" + ShizukuShell.exec(
                    "dumpsys display | grep -i -E 'vote|priority|refreshrate|renderframerate|"
                    + "appRequest|lowpower|skin|thermal|brightness.*rate|modeId' | head -70")
                + "\n\n[surfaceflinger]\n" + ShizukuShell.exec(
                    "dumpsys SurfaceFlinger | grep -i -E 'refresh|fps|activeMode|desiredMode|"
                    + "policy|backdoor' | head -40")
                + "\n\n[props]\n" + ShizukuShell.exec(
                    "getprop | grep -i -E 'refresh|fps|frame_rate|framerate|ltpo|hz' | head -40")
                + "\n\n[system]\n" + ShizukuShell.exec("settings list system | " + GREP)
                + "\n\n[secure]\n" + ShizukuShell.exec("settings list secure | " + GREP)
                + "\n\n[global]\n" + ShizukuShell.exec("settings list global | " + GREP);
    }

    private static void sh(StringBuilder log, String cmd) {
        String out = ShizukuShell.exec(cmd);
        log.append("$ ").append(cmd).append('\n');
        if (!out.isEmpty()) log.append(out).append('\n');
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
