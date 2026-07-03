package com.shinian.pay.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.shinian.pay.service.ForeService;
import com.shinian.pay.service.DaemonWatchDog;
import com.shinian.pay.util.KeepAliveManager;

/**
 * 开机自启广播接收器
 * 设备开机完成后自动启动所有保活服务
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Log.i(TAG, "收到开机广播: " + action);
            startAllServices(context);
        }
    }

    private void startAllServices(Context context) {
        try {
            // 1. 启动 ForeService（主前台服务，通知栏常驻）
            Intent foreIntent = new Intent(context, ForeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(foreIntent);
            } else {
                context.startService(foreIntent);
            }
            Log.d(TAG, "ForeService 已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动 ForeService 失败", e);
        }

        // 2. 启动守护看门狗（DaemonWatchDog + DaemonService + WorkManager）
        KeepAliveManager.startAll(context);

        Log.i(TAG, "所有保活服务已启动完成");
    }
}
