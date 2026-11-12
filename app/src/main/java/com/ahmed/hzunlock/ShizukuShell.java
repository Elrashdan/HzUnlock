package com.ahmed.hzunlock;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Runs shell commands with ADB-shell privileges through Shizuku. */
public final class ShizukuShell {
    private ShizukuShell() {}

    public static final int REQUEST_CODE = 42;
    public static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    public enum State { NOT_RUNNING, NO_PERMISSION, READY }

    public static State state() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return State.NOT_RUNNING;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? State.READY : State.NO_PERMISSION;
        } catch (Throwable t) {
            return State.NOT_RUNNING;
        }
    }

    public static boolean isReady() { return state() == State.READY; }

    /** Blocking: call from a background thread. Returns stdout + stderr. */
    public static String exec(String cmd) {
        try {
            Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            p.waitFor();
            return (out + err).trim();
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return "ERR: " + c;
        }
    }

    private static String read(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
