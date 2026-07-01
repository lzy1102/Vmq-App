package com.shinian.pay.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置管理器
 * 统一管理 SharedPreferences 的读写操作
 */
public class ConfigManager {

    private static final String SP_NAME_CONFIG = "shinian";
    private static final String SP_NAME_LOGS = "items";
    private static final String SP_NAME_STATE = "state_switch";

    private static final String KEY_HOST = "host";
    private static final String KEY_KEY = "key";
    private static final String KEY_LOGS_STR = "logsStr";
    private static final String KEY_STATE_SWITCH = "state_switch";

    private final Context mContext;

    private ConfigManager(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * 获取单例实例
     */
    public static ConfigManager getInstance(Context context) {
        return new ConfigManager(context);
    }

    // ==================== 服务端配置 ====================

    /**
     * 获取通知地址（host）
     * @return 通知地址，未配置时返回空字符串
     */
    public String getHost() {
        return getSp(SP_NAME_CONFIG).getString(KEY_HOST, "");
    }

    /**
     * 获取通讯密钥（key）
     * @return 通讯密钥，未配置时返回空字符串
     */
    public String getKey() {
        return getSp(SP_NAME_CONFIG).getString(KEY_KEY, "");
    }

    /**
     * 保存服务端配置
     * @param host 通知地址
     * @param key 通讯密钥
     */
    public void saveConfig(String host, String key) {
        getSpEditor(SP_NAME_CONFIG)
                .putString(KEY_HOST, host)
                .putString(KEY_KEY, key)
                .apply();
    }

    /**
     * 检查是否已配置服务端信息
     * @return true 表示已配置，false 表示未配置
     */
    public boolean isConfigured() {
        String host = getHost();
        String key = getKey();
        return host != null && !host.isEmpty() && key != null && !key.isEmpty();
    }

    // ==================== 日志管理 ====================

    /**
     * 获取日志内容
     * @return 日志字符串
     */
    public String getLogs() {
        return getSp(SP_NAME_LOGS).getString(KEY_LOGS_STR, "");
    }

    /**
     * 保存日志内容
     * @param logs 日志字符串
     */
    public void saveLogs(String logs) {
        getSpEditor(SP_NAME_LOGS)
                .putString(KEY_LOGS_STR, logs)
                .apply();
    }

    /**
     * 清空日志
     */
    public void clearLogs() {
        saveLogs("");
    }

    /**
     * 追加日志（自动限制最大行数）
     * @param newLog 新日志内容
     * @param maxLines 最大行数
     * @return 更新后的完整日志
     */
    public String appendLog(String newLog, int maxLines) {
        String logs = getLogs();
        String[] lines = logs.split("\n");

        // 如果超过最大行数，截取最后一部分
        if (lines.length >= maxLines) {
            int lastIndex = logs.indexOf("\n");
            for (int i = 1; i < maxLines && lastIndex >= 0; i++) {
                lastIndex = logs.indexOf("\n", lastIndex + 1);
            }
            if (lastIndex >= 0 && lastIndex < logs.length() - 1) {
                logs = logs.substring(lastIndex + 1);
            }
        }

        // 追加新日志
        logs = newLog + "\n" + logs;

        // 保存
        saveLogs(logs);

        return logs;
    }

    // ==================== 屏幕常亮设置 ====================

    /**
     * 获取屏幕常亮状态
     * @return "no" 表示开启，"off" 表示关闭，空字符串表示默认
     */
    public String getScreenState() {
        return getSp(SP_NAME_STATE).getString(KEY_STATE_SWITCH, "");
    }

    /**
     * 保存屏幕常亮状态
     * @param state "no" 表示开启，"off" 表示关闭
     */
    public void saveScreenState(String state) {
        getSpEditor(SP_NAME_STATE)
                .putString(KEY_STATE_SWITCH, state)
                .apply();
    }

    /**
     * 检查屏幕常亮是否开启
     * @return true 表示开启，false 表示关闭
     */
    public boolean isScreenAlwaysOn() {
        return "no".equals(getScreenState());
    }

    // ==================== 内部方法 ====================

    private SharedPreferences getSp(String name) {
        return mContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getSpEditor(String name) {
        return getSp(name).edit();
    }
}
