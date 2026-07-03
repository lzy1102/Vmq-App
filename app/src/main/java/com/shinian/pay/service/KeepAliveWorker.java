package com.shinian.pay.service;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class KeepAliveWorker extends Worker {

    private static final String TAG = "KeepAliveWorker";

    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "WorkManager 定时检查：确保服务运行");

        ensureDaemonServiceRunning();

        return Result.success();
    }

    private void ensureDaemonServiceRunning() {
        Context ctx = getApplicationContext();
        Intent intent = new Intent(ctx, DaemonService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }
}
