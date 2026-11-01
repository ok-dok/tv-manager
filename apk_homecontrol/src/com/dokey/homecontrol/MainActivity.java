package com.dokey.homecontrol;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* 电视管家 - 全屋联动关机（暗色卡片式控制台） */
public class MainActivity extends Activity {
    public static final StringBuilder logBuf = new StringBuilder();
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final int C_BG   = 0xFF0F1420;   // 页面底
    static final int C_CARD = 0xFF182234;   // 卡片
    static final int C_FLD  = 0xFF242F49;   // 输入框
    static final int C_BLUE = 0xFF3B82F6;
    static final int C_ORNG = 0xFFE0762C;
    static final int C_LEAF = 0xFF2BAE77;
    static final int C_GRAY = 0xFF33415C;
    static final int C_RED  = 0xFF95414F;
    static final int C_TXT  = 0xFFF2F5FA;
    static final int C_SUB  = 0xFF8A93A6;

    static final int DOT_ON = C_LEAF, DOT_OFF = C_ORNG, DOT_NONE = 0xFF55637F;

    LinearLayout statusCard;
    TextView statusDot, statusText;
    EditText etIP, etPort;
    TextView tvLog;

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(r));
        return g;
    }

    LinearLayout.LayoutParams lpWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    LinearLayout card(int pad) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        c.setBackground(round(C_CARD, 18));
        LinearLayout.LayoutParams p = lpWrap(); p.setMargins(0, dp(10), 0, 0); c.setLayoutParams(p);
        return c;
    }

    TextView lab(String s, int color, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(color); t.setTextSize(size);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    EditText input(String hint) {
        EditText et = new EditText(this);
        // 遥控器 D-pad 焦点高亮（蓝描边）
        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
        GradientDrawable focusBg = round(0xFF2A3761, 12); focusBg.setStroke(dp(2), 0xFF3B82F6);
        sld.addState(new int[]{android.R.attr.state_focused}, focusBg);
        sld.addState(new int[]{}, round(C_FLD, 12));
        et.setBackground(sld);
        et.setFocusable(true);
        et.setTextColor(C_TXT); et.setHintTextColor(0xFF5F6B87);
        et.setHint(hint); et.setTextSize(15); et.setSingleLine(true);
        et.setPadding(dp(14), dp(10), dp(14), dp(10));
        return et;
    }

    Button bigBtn(String text, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg); b.setTextSize(15);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        // 遥控器 D-pad 焦点高亮（白色描边）
        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
        GradientDrawable f1 = round(bg, 14); f1.setStroke(dp(2), 0xFFF2F4F8);
        GradientDrawable f2 = round(bg, 14); f2.setAlpha(200);
        sld.addState(new int[]{android.R.attr.state_pressed}, f2);
        sld.addState(new int[]{android.R.attr.state_focused}, f1);
        sld.addState(new int[]{}, round(bg, 14));
        b.setBackground(sld);
        b.setFocusable(true);
        b.setAllCaps(false);
        b.setPadding(dp(16), dp(9), dp(16), dp(9));
        LinearLayout.LayoutParams p = lpWrap(); p.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(p);
        b.setOnClickListener(l);
        return b;
    }

    TextView dot() {
        TextView d = new TextView(this);
        d.setText("●"); d.setTextSize(14);
        d.setTextColor(DOT_NONE);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, dp(1), dp(9), 0);
        return d;
    }

    void log(String s) { MainActivity.addLog(s); }
    static void poll() {
        MainActivity a = actRef;
        if (a != null && a.tvLog != null) a.runOnUiThread(() -> a.tvLog.setText(logBuf.toString()));
    }
    static MainActivity actRef;

    SharedPreferences pref() { return getSharedPreferences("hc", 0); }
    public static String getTvIpPref(android.content.Context c) { return c.getSharedPreferences("hc", 0).getString("ip", ""); }
    public static int getTvPortPref(android.content.Context c) { return c.getSharedPreferences("hc", 0).getInt("port", 5555); }
    public static String getTvModelPref(android.content.Context c) { return c.getSharedPreferences("hc", 0).getString("model", ""); }
    public static void setTvPref(android.content.Context c, String ip, String model) {
        c.getSharedPreferences("hc", 0).edit()
                .putString("ip", ip)
                .putString("model", model == null ? "" : model)
                .commit();
    }

    String getTvIp() { return getTvIpPref(this); }
    int getTvPort() { try { return Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception e) { return 5555; } }

    void save() {
        String ip = etIP.getText().toString().trim();
        int port = getTvPort();
        setTvPref(this, ip, getTvModelPref(this));
        pref().edit().putInt("port", port).commit();
        log("saved " + ip + ":" + port);
        refreshStatus();
    }

    // 自动检测电视：优先已保存的 IP，失效扫全 /24 找 ADB 5555 并读型号，命中即保存
    void scan() {
        log("scan ...");
        new Thread(() -> {
            String r = AutoDetect.detectAndPick(this, null);
            if (r == null) { log("scan done: no tv found"); return; }
            String[] parts = r.split("\t", 2);
            String ip = parts[0];
            String model = parts.length > 1 ? parts[1] : "";
            setTvPref(this, ip, model);
            pref().edit().putInt("port", 5555).commit();
            MAIN.post(() -> { if (etIP != null) etIP.setText(ip); refreshStatus(); });
            log("detect " + ip + " [" + model + "]");
            log("auto saved");
        }).start();
    }

    // 关电视（+可选关功放）
    void fire(String why, boolean withAmp) {
        log("[" + why + "]fire");
        new Thread(() -> {
            String ip = getTvIpPref(this);
            int port = getTvPortPref(this);
            if (ip == null || ip.length() == 0) {
                String r2 = AutoDetect.detectAndPick(this, null);
                if (r2 == null) { log("[" + why + "] no tv found"); return; }
                String[] parts = r2.split("\t", 2);
                ip = parts[0]; String model = parts.length > 1 ? parts[1] : "";
                setTvPref(this, ip, model);
                log("[" + why + "] detect " + ip + " [" + model + "]");
            }
            int r = AdbClient.shell(ip, port, "input keyevent KEYCODE_POWER");
            if (r != 1) {
                String r2 = AutoDetect.detectAndPick(this, ip);
                if (r2 != null) {
                    String[] p2 = r2.split("\t", 2);
                    String ip2 = p2[0];
                    if (!ip2.equals(ip)) {
                        setTvPref(this, ip2, p2.length > 1 ? p2[1] : "");
                        log("[retry-detect] " + ip2);
                        r = AdbClient.shell(ip2, port, "input keyevent KEYCODE_POWER");
                    }
                }
            }
            log("[" + why + "]tv_off " + (r == 1 ? "OKAY" : "FAIL"));
            if (r == 1 && withAmp) {
                try {
                    java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo 0F 36 > /sys/class/cec/cmd"});
                    p.waitFor();
                    log("amp " + (p.exitValue() == 0 ? "OKAY" : "SKIP"));
                } catch (Exception e) { log("amp SKIP"); }
            }
            MAIN.post(this::refreshStatus);
        }).start();
    }

    int statusColor() {
        String ip = getTvIpPref(this);
        if (ip == null || ip.length() == 0) return DOT_NONE;
        return AutoDetect.portOpen(ip, 5555, 500) ? DOT_ON : DOT_OFF;
    }

    void refreshStatus() {
        if (statusText == null) return;
        String ip = getTvIpPref(this);
        String model = getTvModelPref(this);
        statusDot.setTextColor(statusColor());
        if (ip == null || ip.length() == 0) statusText.setText("未检测电视，点下方“自动检测电视”");
        else statusText.setText(ip + (model == null || model.length() == 0 ? "" : "    · " + model));
    }

    public static void addLog(String s) { android.util.Log.d("HomeControl", s); append(s); }
    static void append(String s) {
        logBuf.insert(0, s + "\n");
        poll();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        actRef = this;
        buildUi();
        etIP.setText(getTvIp());
        etPort.setText(String.valueOf(getTvPortPref(this)));
        tvLog.setText(logBuf.toString());
        refreshStatus();
        tryStartService();
    }
    @Override protected void onResume() { super.onResume(); actRef = this; refreshStatus(); tryStartService(); }

    void tryStartService() {
        try { startForegroundService(new Intent(this, MainService.class)); }
        catch (Throwable t) {
            try { startService(new Intent(this, MainService.class)); } catch (Throwable t2) { }
        }
    }
    @Override protected void onDestroy() { actRef = null; super.onDestroy(); }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(C_BG, 0));
        root.setPadding(dp(14), dp(16), dp(14), dp(20));

        // ==== 头部
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(lab("电视管家", C_TXT, 24, true));
        head.addView(lab("一键关机 · 电视与功放联动", C_SUB, 13, false));
        LinearLayout.LayoutParams hp = lpWrap(); hp.setMargins(0, dp(4), 0, 0);
        head.setLayoutParams(hp);

        // ==== 状态卡
        statusCard = card(14);
        statusCard.addView(lab("电视设备", C_SUB, 12, false));
        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        srow.setPadding(0, dp(6), 0, 0);
        statusDot = dot();
        statusText = lab("", C_TXT, 14, false);
        srow.addView(statusDot);
        srow.addView(statusText);
        statusCard.addView(srow);

        // ==== 配置卡
        LinearLayout cfg = card(14);
        cfg.addView(lab("连接", C_SUB, 12, false));
        LinearLayout cr = new LinearLayout(this); cr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.weight = 1f; p1.rightMargin = dp(10); p1.setMargins(0, dp(8), 0, 0);
        etIP = input("电视 ADB IP"); etIP.setLayoutParams(p1);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
        p2.setMargins(0, dp(8), 0, 0);
        etPort = input("端口"); etPort.setLayoutParams(p2);
        cr.addView(etIP); cr.addView(etPort);
        cfg.addView(cr);
        cfg.addView(bigBtn("保存配置", C_GRAY, 0xFFDDE2EC, v -> save()));

        // ==== 控制卡
        LinearLayout act = card(12);
        act.addView(lab("控制", C_SUB, 12, false));
        Button bFire = bigBtn("关电视 + 关功放", C_ORNG, 0xFFFFFFFF, v -> fire("关电视+关功放", true));
        act.addView(bFire);
        // 两列：测试关电视 | 自动检测电视
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        Button bTest = bigBtn("测试关电视", C_BLUE, 0xFFFFFFFF, v -> fire("测试关电视", false));
        Button bScan = bigBtn("自动检测电视", C_GRAY, 0xFFDDE2EC, v -> scan());
        LinearLayout.LayoutParams w1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        w1.weight = 1f; w1.rightMargin = dp(6); w1.setMargins(0, dp(8), 0, 0);
        bTest.setLayoutParams(w1);
        LinearLayout.LayoutParams w2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        w2.weight = 1f; w2.setMargins(0, dp(8), 0, 0);
        bScan.setLayoutParams(w2);
        row2.addView(bTest); row2.addView(bScan);
        act.addView(row2);
        LinearLayout rr = new LinearLayout(this);
        rr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams q1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        q1.weight = 1f; q1.rightMargin = dp(6); q1.setMargins(0, dp(10), 0, 0);
        Button bStart = bigBtn("启动常驻", C_LEAF, 0xFFFFFFFF, v -> { startService(new Intent(this, MainService.class)); log("serv start"); });
        bStart.setLayoutParams(q1);
        LinearLayout.LayoutParams q2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        q2.setMargins(0, dp(10), 0, 0);
        Button bStop  = bigBtn("停止常驻", C_RED, 0xFFF4DADF, v -> { stopService(new Intent(this, MainService.class)); log("serv stop"); });
        bStop.setLayoutParams(q2);
        rr.addView(bStart); rr.addView(bStop);
        act.addView(rr);

        // ==== 日志卡
        LinearLayout lg = card(14);
        lg.addView(lab("运行日志", C_SUB, 12, false));
        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF9BA4B6);
        tvLog.setTextSize(11);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setBackground(round(0xFF121926, 12));
        tvLog.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110));
        tlp.setMargins(0, dp(8), 0, 0);
        tvLog.setLayoutParams(tlp);
        ScrollView inner = new ScrollView(this);
        inner.addView(tvLog);
        lg.addView(inner);

        root.addView(head);
        root.addView(statusCard);
        root.addView(cfg);
        root.addView(act);
        root.addView(lg);

        // 居中 + 固定逻辑宽度（大屏不会被拉得过宽）
        root.setLayoutParams(new LinearLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(Gravity.CENTER_HORIZONTAL);
        holder.addView(root);
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(holder, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sc.setBackground(round(C_BG, 0));
        setContentView(sc);
        // 启动时默认把焦点给主操作按钮（遥控第一下就能按）
        sc.post(() -> { if (bFire != null) bFire.requestFocus(); });
    }
}
