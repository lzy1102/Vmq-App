package com.shinian.pay.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.shinian.pay.service.DaemonService;
import com.shinian.pay.service.DaemonWatchDog;
import com.shinian.pay.service.KeepAliveWorker;
import com.shinian.pay.service.PlayerMusicService;

import java.util.concurrent.TimeUnit;

public class KeepAliveManager {

    private static final String TAG = "KeepAliveManager";
    private static final String WORK_NAME = "vmq_keep_alive";

    private KeepAliveManager() {
    }

    public static void startAll(Context context) {
        startDaemonService(context);
        startPlayerMusicService(context);
        startDaemonWatchDog(context);
        scheduleWorkManager(context);
    }

    public static void startDaemonService(Context context) {
        try {
            Intent intent = new Intent(context, DaemonService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动 DaemonService 失败", e);
        }
    }

    public static void startPlayerMusicService(Context context) {
        try {
            Intent intent = new Intent(context, PlayerMusicService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动 PlayerMusicService 失败", e);
        }
    }

    public static void startDaemonWatchDog(Context context) {
        try {
            Intent intent = new Intent(context, DaemonWatchDog.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动 DaemonWatchDog 失败", e);
        }
    }

    public static void scheduleWorkManager(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                KeepAliveWorker.class,
                15, TimeUnit.MINUTES
        ).setConstraints(constraints).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
        Log.d(TAG, "WorkManager 定时唤醒已注册 (15分钟)");
    }

    public static void cancelWorkManager(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    public static boolean isBatteryOptimizationIgnored(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true;
    }

    public static void showBatteryOptimizationDialog(Activity activity) {
        if (isBatteryOptimizationIgnored(activity)) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("电池白名单")
                .setMessage("为了保证收款监听不被系统杀掉，请将本应用加入电池优化白名单。\n\n点击「去设置」后，找到本应用并选择「不优化」。")
                .setCancelable(true)
                .setPositiveButton("去设置", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        activity.startActivity(intent);
                    }
                })
                .setNegativeButton("暂不设置", null)
                .show();
    }
}
