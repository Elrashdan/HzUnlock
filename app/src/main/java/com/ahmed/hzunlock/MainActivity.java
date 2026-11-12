package com.ahmed.hzunlock;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView status, info, logView;
    private Button mainBtn;
    private boolean busy = false;

    private final Shizuku.OnBinderReceivedListener onBinder = () -> main.post(this::refresh);
    private final Shizuku.OnBinderDeadListener onDead = () -> main.post(this::refresh);
    private final Shizuku.OnRequestPermissionResultListener onPerm = (code, result) -> {
        main.post(() -> {
            refresh();
            if (ShizukuShell.isReady()) runUnlock();
        });
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("Hz Unlock");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(17);
        status.setPadding(0, dp(14), 0, dp(10));
        root.addView(status);

        mainBtn = new Button(this);
        mainBtn.setAllCaps(false);
        mainBtn.setTextSize(18);
        mainBtn.setOnClickListener(v -> onMainClick());
        root.addView(mainBtn);

        Switch autostart = new Switch(this);
        autostart.setText("شغّله تلقائي بعد الريستارت");
        autostart.setPadding(0, dp(12), 0, dp(12));
        autostart.setChecked(HzUtil.prefs(this).getBoolean(HzUtil.KEY_AUTOSTART, true));
        autostart.setOnCheckedChangeListener((s, on) ->
                HzUtil.prefs(this).edit().putBoolean(HzUtil.KEY_AUTOSTART, on).apply());
        root.addView(autostart);

        info = new TextView(this);
        info.setTextSize(14);
        info.setPadding(0, dp(8), 0, dp(8));
        root.addView(info);

        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(8), 0, dp(8));
        root.addView(logView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(small("انسخ التقرير", v -> copyLog()), weight());
        row.addView(small("فحص خدمات Honor", v -> runDiagnostics()), weight());
        row.addView(small("وقّف ورجّع الطبيعي", v -> runReset()), weight());
        root.addView(row);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        Shizuku.addBinderReceivedListenerSticky(onBinder);
        Shizuku.addBinderDeadListener(onDead);
        Shizuku.addRequestPermissionResultListener(onPerm);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder);
        Shizuku.removeBinderDeadListener(onDead);
        Shizuku.removeRequestPermissionResultListener(onPerm);
        worker.shutdownNow();
        super.onDestroy();
    }

    private void refresh() {
        Display d = getDisplay();
        Display.Mode best = HzUtil.bestMode(d);
        int now = Math.round(d.getRefreshRate());
        int target = Math.round(best.getRefreshRate());

        switch (ShizukuShell.state()) {
            case NOT_RUNNING:
                status.setText(isShizukuInstalled()
                        ? "الخطوة ١ من ٢: Shizuku مش شغّال.\nافتحه واضغط Start (عن طريق Wireless debugging)، وبعدين ارجع هنا."
                        : "الخطوة ١ من ٢: محتاج تثبّت تطبيق Shizuku الأول.");
                mainBtn.setText(isShizukuInstalled() ? "افتح Shizuku" : "نزّل Shizuku");
                break;
            case NO_PERMISSION:
                status.setText("الخطوة ٢ من ٢: اسمح لـ Hz Unlock يستخدم Shizuku.");
                mainBtn.setText("اسمح وفك القفل");
                break;
            case READY:
                boolean on = OverlayService.isRunning() && now >= target - 1;
                status.setText(on ? "✓ شغّال على " + target + " هرتز" : "جاهز. اضغط الزرار.");
                mainBtn.setText(busy ? "جاري التنفيذ..." : "فك قفل " + target + " هرتز");
                break;
        }
        mainBtn.setEnabled(!busy);

        StringBuilder s = new StringBuilder();
        s.append("المعامل دلوقتي: ").append(now).append(" هرتز")
                .append("   |   أعلى وضع: ").append(target).append(" هرتز\n");
        for (Display.Mode m : d.getSupportedModes()) {
            s.append("• وضع ").append(m.getModeId()).append(": ")
                    .append(Math.round(m.getRefreshRate())).append(" هرتز")
                    .append(m.getModeId() == d.getMode().getModeId() ? "  ← الحالي" : "")
                    .append('\n');
        }
        s.append("peak = ").append(HzUtil.readSetting(this, "peak_refresh_rate"))
                .append("   min = ").append(HzUtil.readSetting(this, "min_refresh_rate"));
        info.setText(s.toString());
    }

    private void onMainClick() {
        switch (ShizukuShell.state()) {
            case NOT_RUNNING:
                openShizuku();
                break;
            case NO_PERMISSION:
                Shizuku.requestPermission(ShizukuShell.REQUEST_CODE);
                break;
            case READY:
                runUnlock();
                break;
        }
    }

    private void runUnlock() {
        if (busy) return;
        busy = true;
        logView.setText("جاري التنفيذ...");
        refresh();
        worker.execute(() -> {
            String log = Unlocker.prepare(this);
            main.post(() -> {
                logView.setText(log);
                stopService(new Intent(this, OverlayService.class));
                main.postDelayed(() -> {
                    if (!Settings.canDrawOverlays(this)) {
                        busy = false;
                        logView.append("✗ إذن الظهور فوق التطبيقات لسه مقفول. فعّله وجرّب تاني.\n");
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                        refresh();
                        return;
                    }
                    startForegroundService(new Intent(this, OverlayService.class));
                    // Give SurfaceFlinger a moment to switch, then check.
                    main.postDelayed(() -> worker.execute(() -> {
                        String result = Unlocker.verify(this);
                        main.post(() -> {
                            busy = false;
                            logView.append("\n" + result);
                            refresh();
                        });
                    }), 3000);
                }, 400);
            });
        });
    }

    private void runReset() {
        stopService(new Intent(this, OverlayService.class));
        if (!ShizukuShell.isReady()) {
            toast("وقّفت الخدمة. باقي الإعدادات هترجع بعد ريستارت.");
            return;
        }
        worker.execute(() -> {
            String log = Unlocker.reset(this);
            main.post(() -> { logView.setText(log); refresh(); });
        });
    }

    private void runDiagnostics() {
        if (!ShizukuShell.isReady()) { toast("شغّل Shizuku الأول"); return; }
        logView.setText("بجمع المعلومات...");
        worker.execute(() -> {
            String out = "===== فحص خدمات Honor =====\n" + Unlocker.probe()
                    + "\n\n===== الحالة =====\n" + Unlocker.diagnostics();
            main.post(() -> logView.setText(out));
        });
    }

    private void copyLog() {
        String text = info.getText() + "\n\n" + logView.getText();
        getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("Hz Unlock", text));
        toast("اتنسخ");
    }

    private boolean isShizukuInstalled() {
        try {
            getPackageManager().getPackageInfo(ShizukuShell.SHIZUKU_PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(ShizukuShell.SHIZUKU_PKG);
        if (launch != null) {
            startActivity(launch);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + ShizukuShell.SHIZUKU_PKG)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://play.google.com/store/apps/details?id=" + ShizukuShell.SHIZUKU_PKG)));
        }
    }

    private Button small(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
