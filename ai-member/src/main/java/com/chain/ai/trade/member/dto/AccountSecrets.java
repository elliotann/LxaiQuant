package com.chain.ai.trade.member.dto;

import lombok.Data;

/**
 * 账户密钥信息（明文）
 * 用于缓存和临时存储
 */
@Data
public class AccountSecrets {
    
    /**
     * API密钥
     */
    private char[] apiKey;
    
    /**
     * API密钥
     */
    private char[] apiSecret;
    
    /**
     * 密码（可选，某些交易所需要）
     */
    private char[] passphrase;
    
    /**
     * 清除敏感信息
     */
    public void clear() {
        if (apiKey != null) {
            java.util.Arrays.fill(apiKey, ' ');
            apiKey = null;
        }
        if (apiSecret != null) {
            java.util.Arrays.fill(apiSecret, ' ');
            apiSecret = null;
        }
        if (passphrase != null) {
            java.util.Arrays.fill(passphrase, ' ');
            passphrase = null;
        }
    }

    public AccountSecrets copy() {
        AccountSecrets copy = new AccountSecrets();
        copy.apiKey = this.apiKey != null ? this.apiKey.clone() : null;
        copy.apiSecret = this.apiSecret != null ? this.apiSecret.clone() : null;
        copy.passphrase = this.passphrase != null ? this.passphrase.clone() : null;
        return copy;
    }
}