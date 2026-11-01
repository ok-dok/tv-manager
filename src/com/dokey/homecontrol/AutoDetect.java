package com.dokey.homecontrol;

import android.content.Context;
import android.util.Log;

/* 自动发现/检测电视：先试已保存的 IP，失效则在同段扫 5555 开放的 ADB 设备并读型号
   返回 "ip\tmodel" 或 null。会同步线程内做探测。 */
public class AutoDetect {
    public static final int SCAN_THREAD_COUNT = 8;

    public static String mySubnetPrefix(Context c) {
        // 优先用 `ip addr` 输出；跳过 lo/loopback 段；备选 NetworkInterface。
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(Runtime.getRuntime().exec("ip addr show").getInputStream()));
            String line;
            String lastIf = "";
            while ((line = r.readLine()) != null) {
                String tt = line.trim();
                // "2: wlan0: <...>" — 跟踪当前接口名以过滤 lo
                if (tt.matches("^\\d+:\\s*\\w+:.*")) {
                    String in = tt.split(":")[1].trim();
                    lastIf = in;
                    continue;
                }
                if (lastIf.equals("lo")) continue;
                if (!tt.startsWith("inet ")) continue;
                String[] tk = tt.split("\\s+");
                if (tk.length >= 2 && tk[1].contains(".")) {
                    int cut = tk[1].indexOf('/');
                    String ip = cut > 0 ? tk[1].substring(0, cut) : tk[1];
                    int d = ip.lastIndexOf('.');
                    if (d > 0) { try { r.close(); } catch (Exception e) { } return ip.substring(0, d + 1); }
                }
            }
            try { r.close(); } catch (Exception e) { }
        } catch (Exception e) { }
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (!a.isAnyLocalAddress() && a.getAddress().length == 4 && !isLoopbackAddress(a)) {
                        String s = a.getHostAddress();
                        if (s == null || s.startsWith("127.")) continue;
                        int d = s.lastIndexOf('.');
                        if (d > 0) return s.substring(0, d + 1);
                    }
                }
            }
        } catch (Exception e) { }
        return null;
    }
    static boolean isLoopbackAddress(java.net.InetAddress a) {
        try { return a.isLoopbackAddress(); } catch (Exception e) { return false; }
    }

    public static boolean portOpen(String ip, int port, int tm) {
        try {
            java.net.Socket s = new java.net.Socket();
            s.connect(new java.net.InetSocketAddress(ip, port), tm);
            s.close();
            return true;
        } catch (Exception e) { return false; }
    }

    public static String probeModel(final String ip) {
        // 返回型号字符串；失败 null
        final Object[] box = new Object[1];
        Thread t = new Thread(() -> {
            try {
                java.net.Socket s = new java.net.Socket();
                s.connect(new java.net.InetSocketAddress(ip, 5555), 1800);
                s.setSoTimeout(2500);
                java.io.OutputStream out = s.getOutputStream();
                java.io.DataInputStream in = new java.io.DataInputStream(s.getInputStream());
                byte[] p = "shell:getprop ro.product.model".getBytes("UTF-8");
                byte[] p2 = new byte[p.length + 1];
                System.arraycopy(p, 0, p2, 0, p.length); p2[p2.length - 1] = 0;
                AdbClient.sendPkt(out, AdbClient.A_CNXN, AdbClient.VERSION, 0x00100000, new byte[0]);
                if (AdbClient.readByteStr(in) == null) { try { s.close(); } catch (Exception e) { } box[0] = null; return; }
                AdbClient.sendPkt(out, AdbClient.A_OPEN, 16, 0, p2);
                for (int i = 0; i < 6; i++) {
                    int[] h = AdbClient.readHdr(in);
                    if (h == null) break;
                    if (h[0] == AdbClient.A_OKAY) continue;
                    if (h[0] == AdbClient.A_WRTE) { int dl = h[3]; byte[] d = new byte[dl > 2048 ? 2048 : dl]; in.readFully(d); box[0] = new String(d, "UTF-8").trim(); }
                    if (h[0] == AdbClient.A_CLSE) break;
                }
                s.close();
            } catch (Exception e) { }
        });
        t.start();
        try { t.join(4000); } catch (Exception e) { }
        if (box[0] == null) return null;
        String v = box[0].toString();
        // 排除自己（盒子自身也开着一个 ADB）
        if (v.equals(android.os.Build.MODEL)) return null;
        return v.length() == 0 ? null : v;
    }

    interface TimeSpanCb { }

    // 同步扫同一 /24 段找 5555 开放的设备；命中第一个 ADB 可读型号的设备返回 "ip\tmodel"
    public static String detectAndPick(Context c, String excludeIp) {
        String prefix = mySubnetPrefix(c);
        if (prefix == null) return null;
        // 阶段1：已保存的 IP 可达优先
        String saved = MainActivity.getTvIpPref(c);
        if (saved != null && saved.length() > 0 && !saved.equals(excludeIp) && portOpen(saved, 5555, 500)) {
            String m = probeModel(saved);
            return saved + "\t" + (m == null ? "?" : m);
        }
        // 阶段2：并发扫描
        final String[] result = new String[1];
        final Object lock = new Object();
        Thread[] pool = new Thread[SCAN_THREAD_COUNT];
        final int[] idx = { 1 };
        for (int w = 0; w < SCAN_THREAD_COUNT; w++) {
            pool[w] = new Thread(() -> {
                while (true) {
                    int i;
                    synchronized (idx) { if (result[0] != null || idx[0] > 254) return; i = idx[0]++; }
                    String ip = prefix + i;
                    if (!portOpen(ip, 5555, 400)) continue;
                    String m = probeModel(ip);
                    if (m == null) continue;
                    synchronized (lock) { if (result[0] == null) result[0] = ip + "\t" + m; }
                }
            });
            pool[w].start();
        }
        for (Thread t : pool) { try { t.join(12000); } catch (Exception e) { } }
        return result[0];
    }
}
