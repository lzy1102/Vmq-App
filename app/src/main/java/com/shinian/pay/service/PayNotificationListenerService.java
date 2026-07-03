package com.shinian.pay.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.shinian.pay.ui.MainActivity;
import com.shinian.pay.util.ConfigManager;
import com.shinian.pay.util.HttpUtil;
import com.shinian.pay.util.MD5Util;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class PayNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "PayNotService";
    private String host = "";
    private String key = "";
    private Thread newThread = null;
    private PowerManager.WakeLock mWakeLock = null;
    private OkHttpClient okHttpClient;
    private Handler mainHandler;
    private ConfigManager configManager;

    public static String md5(String string) {
        return MD5Util.md5(string);
    }

    // 释放设备电源锁
    public void releaseWakeLock() {
        if (null != mWakeLock) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    // 心跳进程
    public void initAppHeart() {
        Log.d(TAG, "开始启动心跳线程");

        // 防止重复启动
        if (newThread != null) {
            return;
        }
        // 申请设备电源锁
        acquireWakeLock(this);

        // 初始化复用的 OkHttpClient
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }

        newThread = new Thread(() -> {
            Log.d(TAG, "心跳线程启动！");
            while (true) {
                host = configManager.getHost();
                key = configManager.getKey();

                String t = String.valueOf(new Date().getTime());
                String sign = md5(t + key);
                String url = HttpUtil.buildHeartbeatUrl(host, t, sign);
                Request request = new Request.Builder()
                        .url(url)
                        .method("GET", null)
                        .build();

                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        final String error = e.getMessage();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                if (MainActivity.LogsTextView != null && !MainActivity.LogsTextView.getText().toString().contains("心跳状态错误")) {
                                    //发送监听日志
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                                + "\r\r\r\r" + "心跳状态错误，请重新配置或切换网络环境!\n错误详情：" + error);
                                    }
                                    Toast.makeText(getApplicationContext(), "心跳状态错误，请重新配置或切换网络!", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Log.d(TAG, "onResponse heard: " + response.body().string());
                    }
                });

                try {
                    Thread.sleep(50 * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        newThread.start();
    }


    // 支付平台包名常量
    private static final String PACKAGE_WECHAT = "com.tencent.mm";
    private static final String PACKAGE_WECHAT_WORK = "com.tencent.wework";
    private static final String PACKAGE_ALIPAY = "com.eg.android.AlipayGphone";
    private static final String PACKAGE_SELF = "com.shinian.pay";
    
    // 微信支付标题关键字
    private static final String[] WECHAT_PAY_TITLES = {
        "微信支付", "微信收款助手", "微信收款商业版", "对外收款", "企业微信","Weixin Cashier Assistant"
    };
    
    // 金额提取正则表达式（预编译提升性能）
    private static final java.util.regex.Pattern MONEY_PATTERN = 
            java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * 当收到一条消息的时候回调，sbn 即收到的消息
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        host = configManager.getHost();
        key = configManager.getKey();
        
        // 获取通知对象和包名（只获取一次）
        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        
        if (notification == null) {
            return;
        }
        
        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }
        
        String title = extras.getString(NotificationCompat.EXTRA_TITLE, "");
        String content = extras.getString(NotificationCompat.EXTRA_TEXT, "");
        Log.d(TAG, "包名: " + pkg);
        // 根据包名分发处理逻辑
        if (PACKAGE_WECHAT.equals(pkg) || PACKAGE_WECHAT_WORK.equals(pkg)) {
            handleWechatNotification(title, content);
        } else if (PACKAGE_ALIPAY.equals(pkg)) {
            handleAlipayNotification(title, content);
        } else if (PACKAGE_SELF.equals(pkg)) {
            handleSelfTestNotification(content);
        }
    }
    
    /**
     * 从文本内容中提取金额数字
     * @param content 包含金额的文本内容
     * @return 提取到的金额字符串，如果未找到则返回 null
     */
    public static String getMoney(String content) {
        if (content == null || content.isEmpty()) return null;
        
        int shoukuanIndex = content.indexOf("收款");
        int fukuanIndex = content.indexOf("付款");
        
        int startIndex = -1;
        if (shoukuanIndex >= 0 && fukuanIndex >= 0) {
            startIndex = Math.min(shoukuanIndex, fukuanIndex);
        } else if (shoukuanIndex >= 0) {
            startIndex = shoukuanIndex;
        } else if (fukuanIndex >= 0) {
            startIndex = fukuanIndex;
        } else {
            return null;
        }

        String afterKeyword = content.substring(startIndex);
        List<String> validAmounts = new ArrayList<>();
        java.util.regex.Matcher matcher = MONEY_PATTERN.matcher(afterKeyword);

        while (matcher.find()) {
            String matched = matcher.group();
            if (isValidNumber(matched)) {
                try {
                    double amount = Double.parseDouble(matched);
                    // 金额范围
                    if (amount >= 0.01 && amount <= 999999.99) {
                        validAmounts.add(matched);
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "getMoney: 匹配到无效金额：" + matched);
                }
            }
        }

        if (!validAmounts.isEmpty()) return validAmounts.get(0);
        return null;
    }
    
    /**
     * 处理支付宝收款通知
     */
    private void handleAlipayNotification(String title, String content) {
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            return;
        }
        
        String money = null;
        String platform = "";
        int platformType = 2;
        
        // 判断是普通收款还是店员通收款（添加括号明确优先级）
        boolean isNormalPay = (title.contains("成功收款") && content.contains("已转入余额")) 
                             || content.contains("通过扫码向你付款");
        boolean isStaffPay = title.contains("店员通") || content.contains("支付宝成功收款");
        
        if (isNormalPay) {
            // 普通收款：优先从标题获取金额
            platform = "支付宝";
            money = getMoney(title);
            if (money == null || money.isEmpty()) {
                money = getMoney(content);
            }
        } else if (isStaffPay) {
            // 店员通收款：优先从内容获取金额
            platform = "支付宝店员";
            platformType = 2; // 保持和普通支付宝一致
            money = getMoney(content);
            if (money == null || money.isEmpty()) {
                money = getMoney(title);
            }
        } else {
            return; // 不匹配的收款类型，直接返回
        }
        
        // 重试一次避免掉单
        if (money == null || money.isEmpty()) {
            if (isNormalPay) {
                money = getMoney(content);
            } else {
                money = getMoney(title);
            }
        }
        
        if (money != null && !money.isEmpty()) {
            try {
                double amount = Double.parseDouble(money);
                Toast.makeText(this, "匹配成功：" + platform + "到账" + money + "元", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onAccessibilityEvent: 匹配成功：" + platform + "到账 " + money + "元");
                appPush(platformType, amount);
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析" + platform + "金额失败：" + money, e);
                showMoneyParseErrorToast(platform);
            }
        } else {
            showMoneyParseErrorToast(platform);
        }
    }
    
    /**
     * 处理自检测试通知
     */
    private void handleSelfTestNotification(String content) {
        if ("测试推送通知，如果程序正常，则会提示监听权限正常".equals(content)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) 
                               + "\r\r\r\r" + "测试收款监听权限正常！");
            }
        }
    }
    
    /**
     * 显示金额解析错误提示（避免频繁弹出 Toast）
     */
    private void showMoneyParseErrorToast(String platform) {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        
        mainHandler.post(() -> 
            Toast.makeText(getApplicationContext(), 
                "监听到" + platform + "收款消息但未匹配到金额！", 
                Toast.LENGTH_SHORT).show()
        );
    }

    //当移除一条消息的时候回调，sbn是被移除的消息
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    // 申请设备电源锁
    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(Context context) {
        if (null == mWakeLock) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "WakeLock");
            if (null != mWakeLock) {
                mWakeLock.acquire();
            }
        }
    }

    /**
     * 推送收款通知到服务器
     * @param type 支付类型：1-微信，2-支付宝
     * @param price 收款金额
     */
    public void appPush(final int type, final double price) {
        host = configManager.getHost();
        key = configManager.getKey();
    
        String priceStr = String.format("%.2f", price);
            
        Log.d(TAG, "appPush: 开始 - 类型:" + type + ", 金额:" + priceStr);
    
        String t = String.valueOf(new Date().getTime());
        String sign = md5(type + priceStr + t + key);
        String url = HttpUtil.buildPushUrl(host, type, priceStr, t, sign);
            
        Log.d(TAG, "appPush: URL:" + url);
    
        // 使用复用的 OkHttpClient 实例
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }
    
        Request request = new Request.Builder().url(url).method("GET", null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String error = e.getMessage();
                Log.e(TAG, "appPush: 请求失败 - " + error);
                    
                // 发送失败日志
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) 
                            + "\r\r\r\r" + "通知回调失败：" + error);
                }
                    
                // 请求失败后延迟 1 秒自动补回调
                scheduleRetryCallback(type, price, priceStr);
            }
    
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String str = response.body().string();
                    JSONObject result = new JSONObject(str);
                    String msg = result.getString("msg");
                        
                    // 根据支付类型记录日志
                    logPushResult(type, priceStr, msg, str, false);
                        
                    // 通知回调成功后，清除对应的通知消息（防止通知栏堆积）
                    if ("成功".equals(msg)) {
                        cancelNotification(type);
                    }
                        
                } catch (JSONException e) {
                    String error = e.getMessage();
                    Log.e(TAG, "appPush: JSON解析失败 - " + error);
                        
                    // 发送失败日志
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) 
                                + "\r\r\r\r" + "通知回调失败：" + error);
                    }
                        
                    // JSON解析失败后延迟 1 秒自动补回调
                    scheduleRetryCallback(type, price, priceStr);
                }
            }
        });

    }

    /**
     * 清除支付通知（防止通知栏堆积）
     * @param type 支付类型：1-微信，2-支付宝
     */
    private void cancelNotification(int type) {
        try {
            // 获取所有活跃的通知
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications == null || activeNotifications.length == 0) {
                return;
            }

            // 根据支付类型确定要清除的通知包名
            String targetPackage = (type == 1) ? PACKAGE_WECHAT : PACKAGE_ALIPAY;

            // 遍历并清除指定包名的通知
            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn != null && targetPackage.equals(sbn.getPackageName())) {
                    // 取消该通知
                    cancelNotification(sbn.getKey());
                    Log.d(TAG, "cancelNotification: 已清除" + (type == 1 ? "微信" : "支付宝") +
                            "通知 - " + sbn.getPackageName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "cancelNotification: 清除通知失败 - " + e.getMessage());
        }
    }

    private void logPushResult(int type, String priceStr, String msg, String data, boolean isRetry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String prefix = isRetry ? "自动补回调：" : "";
            String payType = (type == 1) ? "微信支付" : "支付宝";
            String logContent = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    + "\r\r\r\r"
                    + prefix + "监听到" + payType + "收款" + priceStr + "元"
                    + "\t" + "通知回调状态：" + msg
                    + "\n" + "通知回调信息：" + data;
            sendMonitorLogs(logContent);
        }
    }
        
    /**
     * 延迟重试回调（消除重复代码）
     * 注意：同步网络请求在后台线程执行，避免阻塞主线程导致 ANR
     */
    private void scheduleRetryCallback(final int type, final double price, final String priceStr) {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        String t = String.valueOf(new Date().getTime());
                        String sign = md5(type + priceStr + t + key);
                        String url = HttpUtil.buildPushUrl(host, type, priceStr, t, sign);
                            
                        String data = HttpUtil.getSync(url);
                            
                        // 解析响应
                        JSONObject jsonObject = new JSONObject(data);
                        int code = jsonObject.getInt("code");
                        String message = jsonObject.getString("msg");
                            
                        if (code == 1 && "成功".equals(message)) {
                            // 记录补回调日志
                            logPushResult(type, priceStr, message, data, true);
                                
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), 
                                            "补通知回调成功：" + data, 
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            Log.w(TAG, "appPush: 补回调失败 - code:" + code + ", msg:" + message);
                        }
                    } catch (Exception e) {
                        String error = e.getMessage();
                        Log.e(TAG, "appPush: 补回调异常 - " + error);
                            
                        final String finalError = error;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplication(), 
                                        "自动补单回调失败！联系作者反馈\n错误详情：" + finalError, 
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }).start();
            }
        }, 1000); // 延迟 1 秒重试
    }

    /**
     * 验证字符串是否为合法的数字格式
     * @param str 待验证的字符串
     * @return true ，false
     */
    private static boolean isValidNumber(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        // 不能以小数点开头或结尾，且只能包含一个小数点
        int dotCount = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '.') {
                dotCount++;
                // 不能有超过一个小数点，或者小数点在开头/结尾
                if (dotCount > 1 || i == 0 || i == str.length() - 1) {
                    return false;
                }
            } else if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 处理微信收款通知
     */
    private void handleWechatNotification(String title, String content) {
        // null
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) return;


        // 检查是否为支付相关标题
        boolean isPayTitle = false;
        for (String payTitle : WECHAT_PAY_TITLES) {
            if (payTitle.equals(title)) {
                isPayTitle = true;
                break;
            }
        }
        // title 不匹配
        if (!isPayTitle) return;

        // 忽略支付消息
        if ((!content.contains("收款") && !content.contains("付款")) || content.contains("已支付")) return;


        // 尝试获取金额（重试一次避免掉单）
        String money = getMoney(content);
        if (money == null || money.isEmpty()) {
            money = getMoney(content);
        }

        if (money != null && !money.isEmpty()) {
            try {
                double amount = Double.parseDouble(money);
                Toast.makeText(this, "匹配成功：微信到账" + money + "元", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onAccessibilityEvent: 匹配成功：微信到账 " + money + "元");
                appPush(1, amount);
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析微信金额失败：" + money, e);
                showMoneyParseErrorToast("微信");
            }
        } else {
            showMoneyParseErrorToast("微信");
        }
    }

    // 监听服务连接成功时回调初始化心跳线程
    @Override
    public void onListenerConnected() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        if (configManager == null) {
            configManager = ConfigManager.getInstance(this);
        }

        initAppHeart();
        //延迟发送监听日志
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                            + "\r\r\r\r" + "监听服务开启成功！");
                }
            }
        }, 1000);
    }

    // 监听日志
    private void sendMonitorLogs(String msgStr) {
        String logsStr = configManager.appendLog(msgStr, 20);
    
        Message msg = new Message();
        msg.what = 0;
        Bundle bundle = new Bundle();
        bundle.putString("logsStr", logsStr);
        msg.setData(bundle);
        MainActivity.monitorLogHandler.sendMessage(msg);
    }

}
