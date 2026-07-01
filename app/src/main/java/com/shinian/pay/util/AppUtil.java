package com.shinian.pay.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

/**
 * 应用工具类
 * 统一管理应用相关的通用方法
 */
public class AppUtil {

    private static final String TAG = "AppUtil";

    private AppUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取应用版本号（versionCode）
     * @param context 上下文
     * @return 版本号字符串，获取失败返回空字符串
     */
    public static String getVersionCode(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return String.valueOf(pi.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本号失败", e);
            return "";
        }
    }

    /**
     * 获取应用版本名（versionName）
     * @param context 上下文
     * @return 版本名字符串，获取失败返回"未知版本"
     */
    public static String getVersionName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            if (pi != null && pi.versionName != null) {
                return pi.versionName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本名失败", e);
        }
        return "未知版本";
    }

    /**
     * 检查应用是否已安装
     * @param context 上下文
     * @param packageName 包名
     * @return true 表示已安装，false 表示未安装
     */
    public static boolean isAppInstalled(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 检查微信是否已安装
     * @param context 上下文
     * @return true 表示已安装
     */
    public static boolean isWechatInstalled(Context context) {
        return isAppInstalled(context, "com.tencent.mm");
    }

    /**
     * 检查支付宝是否已安装
     * @param context 上下文
     * @return true 表示已安装
     */
    public static boolean isAlipayInstalled(Context context) {
        return isAppInstalled(context, "com.eg.android.AlipayGphone");
    }

    /**
     * 复制文本到剪贴板
     * @param context 上下文
     * @param text 要复制的文本
     * @return true 表示复制成功
     */
    public static boolean copyToClipboard(Context context, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clipData = ClipData.newPlainText("Label", text);
                cm.setPrimaryClip(clipData);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "复制到剪贴板失败", e);
        }
        return false;
    }
}
