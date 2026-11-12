package com.ahmed.hzunlock;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

public class MainActivity extends Activity {
    private static final String GRANT_CMD =
            "pm grant com.ahmed.hzunlock android.permission.WRITE_SECURE_SETTINGS";

    private TextView info;
    private Button overlayBtn;
    private Switch autostart;

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

        info = new TextView(this);
        info.setTextSize(15);
        info.setPadding(0, dp(12), 0, dp(12));
        root.addView(info);

        // Method 1: overlay (no ADB needed)
        root.addView(section("الطريقة ١: طبقة شفافة (من غير ADB)"));
        overlayBtn = button("", v -> toggleOverlay());
        root.addView(overlayBtn);

        autostart = new Switch(this);
        autostart.setText("شغّله تلقائي بعد الريستارت");
        autostart.setPadding(0, dp(8), 0, dp(8));
        autostart.setChecked(HzUtil.prefs(this).getBoolean(HzUtil.KEY_AUTOSTART, false));
        autostart.setOnCheckedChangeListener((s, on) ->
                HzUtil.prefs(this).edit().putBoolean(HzUtil.KEY_AUTOSTART, on).apply());
        root.addView(autostart);

        // Method 2: system settings (needs one-time grant)
        root.addView(section("الطريقة ٢: إعدادات السيستم (محتاجة إذن مرة واحدة)"));
        root.addView(button("ثبّت أعلى معامل في الإعدادات", v -> applySettings()));
        root.addView(button("رجّع الإعدادات للطبيعي", v -> {
            String err = HzUtil.resetSettings(this);
            toast(err == null ? "اترجعت" : messageFor(err));
            refresh();
        }));
        root.addView(button("انسخ أمر الإذن (لـ aShell)", v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("cmd", GRANT_CMD));
            toast("اتنسخ، الصقه في aShell");
        }));

        root.addView(button("تحديث المعلومات", v -> refresh()));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Display d = getDisplay();
        Display.Mode cur = d.getMode();
        Display.Mode best = HzUtil.bestMode(d);

        StringBuilder s = new StringBuilder();
        s.append("المعامل دلوقتي: ").append(round(d.getRefreshRate())).append(" هرتز\n");
        s.append("أعلى وضع الشاشة بتعلنه: ").append(round(best.getRefreshRate())).append(" هرتز\n\n");
        s.append("كل الأوضاع المتاحة:\n");
        for (Display.Mode m : d.getSupportedModes()) {
            s.append("• وضع ").append(m.getModeId()).append(": ")
                    .append(m.getPhysicalWidth()).append("×").append(m.getPhysicalHeight())
                    .append(" @ ").append(round(m.getRefreshRate())).append(" هرتز")
                    .append(m.getModeId() == cur.getModeId() ? "  ← الحالي" : "")
                    .append("\n");
        }
        s.append("\npeak_refresh_rate = ").append(HzUtil.readSetting(this, "peak_refresh_rate"));
        s.append("\nmin_refresh_rate = ").append(HzUtil.readSetting(this, "min_refresh_rate"));
        s.append("\n\nإذن الطبقة: ").append(Settings.canDrawOverlays(this) ? "✓" : "✗");
        s.append("\nإذن الإعدادات: ").append(HzUtil.hasSecureSettings(this) ? "✓" : "✗");
        if (best.getRefreshRate() < 100) {
            s.append("\n\n⚠ السيستم مش معلن وضع 120 هرتز، فغالبًا مفيش أي طريقة من غير روت هتفتحه.");
        }
        info.setText(s.toString());

        overlayBtn.setText(OverlayService.isRunning() ? "وقّف الطبقة" : "شغّل الطبقة (120 هرتز)");
    }

    private void toggleOverlay() {
        if (OverlayService.isRunning()) {
            stopService(new Intent(this, OverlayService.class));
            overlayBtn.postDelayed(this::refresh, 300);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("اسمح للتطبيق يظهر فوق التطبيقات التانية، وبعدين ارجع");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        startForegroundService(new Intent(this, OverlayService.class));
        overlayBtn.postDelayed(this::refresh, 800);
    }

    private void applySettings() {
        float hz = HzUtil.bestMode(getDisplay()).getRefreshRate();
        String err = HzUtil.applySettings(this, hz);
        toast(err == null ? "اتثبّت على " + round(hz) + " هرتز" : messageFor(err));
        refresh();
    }

    private String messageFor(String err) {
        if ("no_permission".equals(err)) {
            return "محتاج الإذن الأول: انسخ الأمر ونفّذه في aShell";
        }
        return "حصل خطأ: " + err;
    }

    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(17);
        v.setPadding(0, dp(18), 0, dp(6));
        return v;
    }

    private Button button(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }

    private static String round(float f) { return String.valueOf(Math.round(f)); }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
