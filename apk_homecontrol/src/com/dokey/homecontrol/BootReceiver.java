package com.dokey.homecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* 开机自启：盒子重启后自动恢复常驻服务（覆盖标准 boot + 部分厂商 quickboot） */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent == null ? "" : intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a);
        if (boot) {
            try {
                context.startForegroundService(new Intent(context, MainService.class));
            } catch (Throwable t) {
                context.startService(new Intent(context, MainService.class));
            }
        }
    }
}
