package com.shinian.pay.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.shinian.pay.service.ForeService;
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

        KeepAliveManager.startAll(context);

        if (!isNotificationListenerEnabled(context)) {
            Log.w(TAG, "通知监听权限未开启，启动 MainActivity 引导用户");
            try {
                Intent mainIntent = new Intent(context, com.shinian.pay.ui.MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(mainIntent);
            } catch (Exception e) {
                Log.e(TAG, "启动 MainActivity 失败", e);
            }
        } else {
            Log.d(TAG, "通知监听权限已开启");
        }

        Log.i(TAG, "所有保活服务已启动完成");
    }

    private boolean isNotificationListenerEnabled(Context context) {
        String pkgName = context.getPackageName();
        String flat = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );
        if (TextUtils.isEmpty(flat)) {
            return false;
        }
        String[] names = flat.split(":");
        for (String name : names) {
            ComponentName cn = ComponentName.unflattenFromString(name);
            if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
