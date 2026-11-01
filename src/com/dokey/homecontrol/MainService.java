package com.dokey.homecontrol;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

/* 常驻：注册 SCREEN_OFF/SCREEN_ON 接收器 + 每 1.5s 轮询 dumpsys power 监控 Wakefulness 变化
   触发：切到 Asleep / Display Power OFF / 收 SCREEN_OFF → adb 电视关机 + CEC 关功放 */
public class MainService extends Service {
    static long sLastFire = 0;
    static String sLastWak = "";
    Handler handler = new Handler();
    BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) fire("SCREEN_OFF");
        }
    };

    Runnable poller = new Runnable() {
        @Override public void run() {
            String d = getPowerState();
            boolean asleep = d != null && (d.contains("mWakefulness=Asleep") || d.contains("mWakefulness=0") || d.contains("Display Power: state=OFF"));
            if (asleep && !"asleep".equals(sLastWak)) fire("POLL");
            sLastWak = asleep ? "asleep" : "awake";
            handler.postDelayed(this, 1500);
        }
    };

    String getPowerState() {
        try {
            Process p = Runtime.getRuntime().exec("dumpsys power");
            java.io.InputStream is = p.getInputStream();
            byte[] b = new byte[16384];
            int n = 0, total = 0;
            while ((n = is.read(b, total, b.length - total)) > 0) { total += n; if (total >= b.length) break; }
            is.close();
            try { p.waitFor(); } catch (Exception ignore) { }
            if (total <= 0) return null;
            int len = Math.min(total, b.length);
            for (int i = 0; i < len; i++) if (b[i] == 0) { len = i; break; }
            return new String(b, 0, len, "UTF-8");
        } catch (Exception e) { return null; }
    }

    void fire(final String why) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - sLastFire < 8000) return;
        sLastFire = now;
        new Thread(() -> {
            String ip = MainActivity.getTvIpPref(MainService.this);
            int port = MainActivity.getTvPortPref(MainService.this);
            if (ip == null || ip.length() == 0) {
                String r2 = AutoDetect.detectAndPick(MainService.this, null);
                if (r2 == null) { MainActivity.addLog("[" + why + "] no tv found"); return; }
                String[] parts = r2.split("\t", 2);
                ip = parts[0]; String model = parts.length > 1 ? parts[1] : "";
                MainActivity.setTvPref(MainService.this, ip, model);
                MainActivity.addLog("[" + why + "] detect " + ip + " [" + model + "]");
            }
            int r = AdbClient.shell(ip, port, "input keyevent KEYCODE_POWER");
            if (r != 1) {
                // 已保存的电视可能换 IP 了：重扫一次再试
                String r2 = AutoDetect.detectAndPick(MainService.this, ip);
                if (r2 != null) {
                    String[] parts2 = r2.split("\t", 2);
                    String ip2 = parts2[0];
                    String model2 = parts2.length > 1 ? parts2[1] : "";
                    if (!ip2.equals(ip)) {
                        MainActivity.setTvPref(MainService.this, ip2, model2);
                        MainActivity.addLog("[retry-detect] " + ip2 + " [" + model2 + "]");
                        r = AdbClient.shell(ip2, port, "input keyevent KEYCODE_POWER");
                    }
                }
            }
            MainActivity.addLog("[" + why + "]tv_off " + (r == 1 ? "OKAY" : "FAIL"));
            // 功放尽量关，但失败无所谓
            boolean okCec = false;
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/sys/class/cec/cmd");
                fw.write("0F 36\n"); fw.close(); okCec = true;
            } catch (Exception e) { }
            if (!okCec) {
                try {
                    Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo 0F 36 > /sys/class/cec/cmd"});
                    p2.waitFor(); okCec = p2.exitValue() == 0;
                } catch (Exception e) { }
            }
            MainActivity.addLog("    cec " + (okCec ? "OKAY" : "SKIP"));
        }).start();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String ch = "homecontrol";
        Notification n;
        android.app.Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(ch, "全屋调度", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, ch);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("电视管家").setContentText("全屋联动已就绪")
         .setSmallIcon(android.R.drawable.ic_dialog_info);
        n = b.build();
        startForeground(1, n);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);
        // 启动即预检测电视 IP，防止待机瞬间才发现网络已断
        new Thread(() -> {
            String saved = MainActivity.getTvIpPref(MainService.this);
            if (saved != null && saved.length() > 0 && AutoDetect.portOpen(saved, 5555, 500)) {
                MainActivity.addLog("prefill-ok " + saved);
                return;
            }
            String found = AutoDetect.detectAndPick(MainService.this, saved);
            if (found != null) {
                String[] p = found.split("\t", 2);
                String ip = p[0], mdl = p.length > 1 ? p[1] : "";
                MainActivity.setTvPref(MainService.this, ip, mdl);
                MainActivity.addLog("prefill " + ip + " [" + mdl + "]");
            } else {
                MainActivity.addLog("prefill: none found");
            }
        }).start();
        handler.postDelayed(poller, 1200);
        MainActivity.addLog("service on");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        MainActivity.addLog("svc start_cmd");
        return Service.START_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignore) { }
        handler.removeCallbacks(poller);
        MainActivity.addLog("service off");
    }
}
