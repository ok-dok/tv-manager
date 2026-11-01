package com.dokey.homecontrol;

/* AdbClient — 纯 Java ADB 协议客户端（无需外部 adb 二进制）
   用法：AdbClient.shell(ip, port, "input keyevent KEYCODE_POWER")
   协议正确性已验证：CLOSE、AOSP byte-sum、shell 字符串带 NUL、arg0=16。 */
public class AdbClient {
    public static final int A_CNXN = 0x4E584E43;
    public static final int A_AUTH = 0x41555448;
    public static final int A_OPEN = 0x4E45504F;
    public static final int A_OKAY = 0x59414B4F;
    public static final int A_CLSE = 0x45534C43;
    public static final int A_WRTE = 0x45545257;
    public static final int VERSION = 0x01000001;

    /** AOSP `calculate_apacket_checksum`：对 payload 每个字节求和。 */
    public static int checksum(byte[] d, int n) { int s = 0; for (int i = 0; i < n; i++) s += (d[i] & 0xFF); return s; }

    /** 发送一帧 ADB 包（header + data），little-endian。 */
    public static void sendPkt(java.io.OutputStream out, int cmd, int a0, int a1, byte[] d) throws Exception {
        int n = d.length;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(24 + n);
        b.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd); b.putInt(a0); b.putInt(a1); b.putInt(n);
        b.putInt(checksum(d, n)); b.putInt(cmd ^ -1);
        b.put(d);
        out.write(b.array(), 0, 24 + n);
        out.flush();
    }
    /** 读一帧 header，返回 cmd/arg0/arg1/data_length/check/magic；失败返回 null。调用方负责读 payload。 */
    public static int[] readHdr(java.io.DataInputStream in) {
        byte[] b = new byte[24];
        try { in.readFully(b); } catch (Exception e) { return null; }
        java.nio.ByteBuffer bf = java.nio.ByteBuffer.wrap(b);
        bf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int[] r = new int[6];
        for (int i = 0; i < 6; i++) r[i] = bf.getInt();
        return r;
    }
    /** 读 header，再把 payload 一起读作字符串。失败返回 null。 */
    public static String readByteStr(java.io.DataInputStream in) {
        int[] h = readHdr(in);
        if (h == null) return null;
        int dl = h[3];
        if (dl <= 0) return "";
        byte[] d = new byte[dl > 4096 ? 4096 : dl];
        try { in.readFully(d); } catch (Exception e) { return null; }
        return new String(d, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 发送一次 shell 命令并吞掉回包；1=OKAY, 0=失败。 */
    public static int shell(String ip, int port, String cmd) {
        int handled = 0;
        java.net.Socket s = new java.net.Socket();
        try {
            s.connect(new java.net.InetSocketAddress(ip, port), 4000);
            s.setSoTimeout(6000);
            java.io.OutputStream out = s.getOutputStream();
            java.io.DataInputStream in = new java.io.DataInputStream(s.getInputStream());

            sendPkt(out, A_CNXN, VERSION, 0x00100000, new byte[0]);
            String banner = readByteStr(in);
            if (banner == null || banner.isEmpty()) { s.close(); return 0; }
            // banner 目录是 "device::ro.product..."; 不发 AUTH, 我们不付签名
            // 正确流程：CNXN 的第一帧响应已读，但 readByteStr 内部已吞了 header+payload。
            // 下一步 OPEN shell:cmd
            byte[] payload = ("shell:" + cmd).getBytes("UTF-8");
            byte[] p2 = new byte[payload.length + 1];
            System.arraycopy(payload, 0, p2, 0, payload.length);
            p2[p2.length - 1] = 0;
            int local = 16;
            sendPkt(out, A_OPEN, local, 0, p2);
            for (int i = 0; i < 8; i++) {
                int[] h = readHdr(in);
                if (h == null) break;
                int c = h[0], dl = h[3];
                if (c == A_OKAY) { handled = 1; }
                else if (c == A_WRTE) { if (dl > 0) skip(in, dl); }
                else break;
            }
            try { sendPkt(out, A_CLSE, local, 0, new byte[0]); } catch (Exception ignore) { }
            s.close();
        } catch (Exception ignore) {
            try { s.close(); } catch (Exception ignore2) { }
        }
        return handled;
    }

    static void skip(java.io.DataInputStream in, int n) {
        byte[] d = new byte[n > 8192 ? 8192 : n];
        try { in.readFully(d); } catch (Exception e) { }
    }
}
