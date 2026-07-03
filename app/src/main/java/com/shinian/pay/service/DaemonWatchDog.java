package com.shinian.pay.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.shinian.pay.R;

public class DaemonWatchDog extends Service {

    private static final String TAG = "DaemonWatchDog";
    private static final String CHANNEL_ID = "daemon_watchdog_channel";
    private static final int NOTICE_ID = 3001;

    private static final String ACTION_CHECK_ALIVE = "com.shinian.pay.CHECK_ALIVE";

    private BroadcastReceiver aliveReceiver;
    private volatile boolean watchLoopRunning = false;
    private Thread watchThread;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DaemonWatchDog onCreate");
        registerAliveReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTICE_ID, buildNotification());
        startWatchLoop();
        return START_STICKY;
    }

    private void startWatchLoop() {
        if (watchLoopRunning) {
            Log.d(TAG, "WatchLoop 已在运行，跳过重复创建");
            return;
        }
        watchLoopRunning = true;
        watchThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3 * 60 * 1000);
                    ensureDaemonServiceAlive();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "DaemonWatchDog-WatchLoop");
        watchThread.start();
    }

    private void ensureDaemonServiceAlive() {
        try {
            Intent intent = new Intent(this, DaemonService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "拉活 DaemonService 失败", e);
        }
    }

    private void registerAliveReceiver() {
        aliveReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "收到心跳，DaemonWatchDog 存活");
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_CHECK_ALIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aliveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(aliveReceiver, filter);
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "守护看门狗",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            return new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("")
                    .setPriority(Notification.PRIORITY_MIN)
                    .setShowWhen(false)
                    .build();
        } else {
            return new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("")
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setShowWhen(false)
                    .build();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (aliveReceiver != null) {
            unregisterReceiver(aliveReceiver);
        }
        // 停止看门狗线程，防止线程泄漏和内存泄漏
        watchLoopRunning = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        try {
            Intent intent = new Intent(this, DaemonWatchDog.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "重启自身失败", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
