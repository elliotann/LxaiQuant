package com.chain.ai.trade.member.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 用于加密/解密API密钥等敏感信息
 */
public class AesGcmEncryptor {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256; // AES-256
    private static final int IV_LENGTH = 12;   // GCM推荐IV长度
    private static final int TAG_LENGTH = 128; // GCM认证标签长度
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    /**
     * 使用Base64编码的密钥字符串构造加密器
     * @param base64Key Base64编码的32字节密钥
     */
    public AesGcmEncryptor(String base64Key) {
        this(Base64.getDecoder().decode(base64Key));
    }
    
    /**
     * 使用原始字节密钥构造加密器
     * @param key 32字节密钥
     */
    public AesGcmEncryptor(byte[] key) {
        if (key.length != 32) {
            throw new IllegalArgumentException("密钥长度必须为32字节");
        }
        this.secretKey = new SecretKeySpec(key, "AES");
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * 生成随机密钥（Base64编码）
     * @return Base64编码的32字节密钥
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(KEY_LENGTH);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("生成密钥失败", e);
        }
    }
    
    /**
     * 加密明文
     * @param plainText 明文
     * @return Base64编码的密文（IV + 密文）
     */
    public String encrypt(String plainText) {
        try {
            // 生成随机IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // 加密
            byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));
            
            // 组合IV和密文
            byte[] encrypted = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(cipherText, 0, encrypted, iv.length, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    /**
     * 解密密文
     * @param encryptedData Base64编码的密文（IV + 密文）
     * @return 明文
     */
    public String decrypt(String encryptedData) {
        try {
            if (encryptedData == null || encryptedData.isBlank()) {
                throw new IllegalArgumentException("密文不能为空");
            }
            // Base64解码
            byte[] encrypted = Base64.getDecoder().decode(encryptedData);
            
            // 提取IV和密文
            if (encrypted.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文格式不正确");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[encrypted.length - IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, iv.length);
            System.arraycopy(encrypted, iv.length, cipherText, 0, cipherText.length);
            
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // 解密
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
