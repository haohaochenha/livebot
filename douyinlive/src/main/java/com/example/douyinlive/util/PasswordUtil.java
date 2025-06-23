package com.example.douyinlive.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 密码加密工具类，使用 MD5 + 盐进行加密
 */
public class PasswordUtil {

    /**
     * 生成随机盐
     * @return 随机盐字符串
     */
    public static String generateSalt() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 使用 MD5 + 盐加密密码
     * @param password 原始密码
     * @param salt 盐值
     * @return 加密后的密码
     */
    public static String encryptPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String saltedPassword = password + salt;
            md.update(saltedPassword.getBytes());
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }

    /**
     * 验证密码是否匹配
     * @param inputPassword 输入的原始密码
     * @param salt 数据库中的盐值
     * @param storedPassword 数据库中存储的加密密码
     * @return 是否匹配
     */
    public static boolean verifyPassword(String inputPassword, String salt, String storedPassword) {
        String encryptedInput = encryptPassword(inputPassword, salt);
        return encryptedInput.equals(storedPassword);
    }
}