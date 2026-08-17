package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.dto.AccountSecrets;

/**
 * 账户密钥服务接口
 */
public interface AccountSecretsService {
    
    /**
     * 获取账户密钥信息
     * @param accountId 账户ID
     * @return 密钥信息（明文）
     */
    AccountSecrets getAccountSecrets(String accountId);

    default AccountSecrets getAccountSecrets(Long accountId) {
        return getAccountSecrets(accountId == null ? null : String.valueOf(accountId));
    }
    
    /**
     * 加密并保存密钥
     * @param accountId 账户ID
     * @param apiKey API密钥
     * @param apiSecret API密钥
     * @param passphrase 密码（可选）
     */
    void saveEncryptedSecrets(String accountId, String apiKey, String apiSecret, String passphrase);

    default void saveEncryptedSecrets(Long accountId, String apiKey, String apiSecret, String passphrase) {
        saveEncryptedSecrets(accountId == null ? null : String.valueOf(accountId), apiKey, apiSecret, passphrase);
    }
    
    /**
     * 清除缓存中的密钥信息
     * @param accountId 账户ID
     */
    void clearCache(String accountId);

    default void clearCache(Long accountId) {
        clearCache(accountId == null ? null : String.valueOf(accountId));
    }
}
