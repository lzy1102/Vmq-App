package com.shinian.pay.util;

import android.text.TextUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 加密工具类
 * 统一管理所有 MD5 相关操作
 */
public class MD5Util {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private MD5Util() {
        // 私有构造函数，防止实例化
    }

    /**
     * 计算字符串的 MD5 值
     * @param input 输入字符串
     * @return 32位小写十六进制 MD5 值，输入为空时返回空字符串
     */
    public static String md5(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * 计算心跳签名
     * 签名算法: md5(timestamp + key)
     * @param timestamp 时间戳（毫秒）
     * @param key 通讯密钥
     * @return 签名字符串
     */
    public static String heartbeatSign(String timestamp, String key) {
        return md5(timestamp + key);
    }

    /**
     * 计算收款推送签名
     * 签名算法: md5(type + price + timestamp + key)
     * @param type 支付类型（1=微信，2=支付宝）
     * @param price 金额（保留两位小数）
     * @param timestamp 时间戳（毫秒）
     * @param key 通讯密钥
     * @return 签名字符串
     */
    public static String pushSign(int type, String price, String timestamp, String key) {
        return md5(type + price + timestamp + key);
    }
}
