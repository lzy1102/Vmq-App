package com.shinian.pay.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HTTP 工具类
 * 统一管理所有 HTTP 请求
 */
public class HttpUtil {

    private static final String TAG = "HttpUtil";
    private static final int CONNECT_TIMEOUT = 10;
    private static final int READ_TIMEOUT = 30;
    private static final int HTTP_TIMEOUT = 8000;

    private static volatile OkHttpClient sOkHttpClient;
    private static Handler sMainHandler;

    private HttpUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取 OkHttpClient 单例
     */
    private static OkHttpClient getOkHttpClient() {
        if (sOkHttpClient == null) {
            synchronized (HttpUtil.class) {
                if (sOkHttpClient == null) {
                    sOkHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sOkHttpClient;
    }

    /**
     * 获取主线程 Handler
     */
    private static Handler getMainHandler() {
        if (sMainHandler == null) {
            sMainHandler = new Handler(Looper.getMainLooper());
        }
        return sMainHandler;
    }

    // ==================== 异步请求 ====================

    /**
     * 异步 GET 请求
     * @param url 请求地址
     * @param callback 回调
     */
    public static void getAsync(String url, HttpCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "请求失败: " + e.getMessage());
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.body() != null) {
                        String data = response.body().string();
                        if (callback != null) {
                            callback.onSuccess(data);
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure("响应体为空");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析响应失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure(e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * 在主线程执行回调
     */
    public static void runOnMain(Runnable runnable) {
        getMainHandler().post(runnable);
    }

    // ==================== 同步请求 ====================

    /**
     * 同步 GET 请求
     * @param urlStr 请求地址
     * @return 响应内容，失败返回 null
     */
    public static String getSync(String urlStr) {
        HttpURLConnection conn = null;
        InputStream inStream = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(HTTP_TIMEOUT);

            inStream = conn.getInputStream();
            byte[] data = readInputStream(inStream);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "同步请求失败: " + e.getMessage());
            return null;
        } finally {
            closeQuietly(inStream);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ==================== URL 构建 ====================

    /**
     * 构建心跳请求 URL
     * @param host 服务端地址（IP:端口）
     * @param timestamp 时间戳
     * @param sign 签名
     * @return 完整的请求 URL
     */
    public static String buildHeartbeatUrl(String host, String timestamp, String sign) {
        return "http://" + host + "/appHeart?t=" + timestamp + "&sign=" + sign;
    }

    /**
     * 构建收款推送请求 URL
     * @param host 服务端地址（IP:端口）
     * @param type 支付类型（1=微信，2=支付宝）
     * @param price 金额
     * @param timestamp 时间戳
     * @param sign 签名
     * @return 完整的请求 URL
     */
    public static String buildPushUrl(String host, int type, String price, String timestamp, String sign) {
        return "http://" + host + "/appPush?t=" + timestamp
                + "&type=" + type
                + "&price=" + price
                + "&sign=" + sign;
    }

    // ==================== 工具方法 ====================

    /**
     * 读取输入流
     * @param inStream 输入流
     * @return 字节数组
     * @throws Exception 读取异常
     */
    private static byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        return outStream.toByteArray();
    }

    /**
     * 静默关闭资源
     */
    private static void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                // 静默处理
            }
        }
    }

    // ==================== 回调接口 ====================

    /**
     * HTTP 请求回调
     */
    public interface HttpCallback {
        /**
         * 请求成功
         * @param data 响应内容
         */
        void onSuccess(String data);

        /**
         * 请求失败
         * @param error 错误信息
         */
        void onFailure(String error);
    }
}
