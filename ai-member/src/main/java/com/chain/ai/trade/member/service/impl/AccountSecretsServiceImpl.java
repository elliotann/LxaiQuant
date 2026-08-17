package com.chain.ai.trade.member.service.impl;

import com.chain.ai.trade.member.dto.AccountSecrets;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.AccountSecretsService;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import com.chain.ai.trade.member.util.AccountSecretKeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 账户密钥服务实现
 */
@Service
public class AccountSecretsServiceImpl implements AccountSecretsService {
    
    private final ITradingAccountService accountService;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);
    private final Map<String, CacheEntry> secretsCache = new ConcurrentHashMap<>();

    @Value("${account.secrets.key:}")
    private String configuredKey;
    
    /**
     * 构造函数中初始化加密器
     */
    public AccountSecretsServiceImpl(ITradingAccountService accountService, JdbcTemplate jdbcTemplate, Environment environment) {
        this.accountService = accountService;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }
    
    @Override
    public AccountSecrets getAccountSecrets(String accountId) {
        String id = accountId == null ? "" : accountId.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("账户ID不能为空");
        long now = System.currentTimeMillis();
        CacheEntry entry = secretsCache.get(id);
        if (entry != null && entry.expiry > now) {
            return entry.value.copy();
        }
        TradingAccount account = accountService.getById(id);
        if (account == null) {
            throw new RuntimeException("账户不存在: " + id);
        }

        try {
            String apiKeyPlain = null;
            String apiSecretPlain = null;
            String passphrasePlain = null;

            boolean hasEncryptedKeys = account.getApiKeyEnc() != null && !account.getApiKeyEnc().isBlank()
                    && account.getApiSecretEnc() != null && !account.getApiSecretEnc().isBlank();

            if (hasEncryptedKeys) {
                try {
                    AesGcmEncryptor encryptor = buildEncryptorOrThrow();
                    apiKeyPlain = encryptor.decrypt(account.getApiKeyEnc());
                    apiSecretPlain = encryptor.decrypt(account.getApiSecretEnc());
                    passphrasePlain = account.getPassphraseEnc() != null && !account.getPassphraseEnc().isBlank()
                            ? encryptor.decrypt(account.getPassphraseEnc())
                            : null;
                } catch (IllegalStateException missingKey) {
                    LegacyPlainSecrets legacy = readLegacyPlainSecrets(id);
                    if (legacy != null && legacy.apiKey != null && !legacy.apiKey.isBlank()
                            && legacy.apiSecret != null && !legacy.apiSecret.isBlank()) {
                        apiKeyPlain = legacy.apiKey;
                        apiSecretPlain = legacy.apiSecret;
                        passphrasePlain = legacy.passphrase;
                    } else {
                        throw missingKey;
                    }
                }
            } else {
                LegacyPlainSecrets legacy = readLegacyPlainSecrets(id);
                if (legacy != null && legacy.apiKey != null && !legacy.apiKey.isBlank()
                        && legacy.apiSecret != null && !legacy.apiSecret.isBlank()) {
                    apiKeyPlain = legacy.apiKey;
                    apiSecretPlain = legacy.apiSecret;
                    passphrasePlain = legacy.passphrase;
                    tryEncryptAndMigrate(id, legacy);
                } else {
                    throw new RuntimeException("账户密钥未设置: " + id);
                }
            }

            AccountSecrets secrets = new AccountSecrets();
            secrets.setApiKey(apiKeyPlain.toCharArray());
            secrets.setApiSecret(apiSecretPlain.toCharArray());
            secrets.setPassphrase(passphrasePlain != null ? passphrasePlain.toCharArray() : null);

            apiKeyPlain = null;
            apiSecretPlain = null;
            passphrasePlain = null;

            secretsCache.put(id, new CacheEntry(secrets, now + CACHE_TTL_MS));
            return secrets.copy();
        } catch (Exception e) {
            throw new RuntimeException("解密失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void saveEncryptedSecrets(String accountId, String apiKey, String apiSecret, String passphrase) {
        String id = accountId == null ? "" : accountId.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("账户ID不能为空");
        TradingAccount account = accountService.getById(id);
        if (account == null) {
            throw new RuntimeException("账户不存在: " + id);
        }
        
        try {
            AesGcmEncryptor encryptor = buildEncryptorOrThrow();
            String apiKeyEnc = encryptor.encrypt(apiKey);
            String apiSecretEnc = encryptor.encrypt(apiSecret);
            String passphraseEnc = passphrase != null ? encryptor.encrypt(passphrase) : null;
            
            account.setApiKeyEnc(apiKeyEnc);
            account.setApiSecretEnc(apiSecretEnc);
            account.setPassphraseEnc(passphraseEnc);
            
            accountService.updateAccount(account);
            
            secretsCache.remove(id);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    @Override
    public void clearCache(String accountId) {
        if (accountId == null) return;
        secretsCache.remove(accountId);
    }

    private AesGcmEncryptor buildEncryptorOrThrow() {
        String key = configuredKey != null && !configuredKey.trim().isEmpty() ? configuredKey.trim() : null;
        if (key == null) {
            key = AccountSecretKeyResolver.resolve(environment);
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("ACCOUNT_SECRET_KEY 未配置");
        }
        return new AesGcmEncryptor(key);
    }

    private LegacyPlainSecrets readLegacyPlainSecrets(String accountId) {
        try {
            if (!legacyColumnsPresent()) return null;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT api_key, api_secret, passphrase FROM t_member_third_account WHERE id = ? LIMIT 1",
                    accountId
            );
            if (rows == null || rows.isEmpty()) return null;
            Map<String, Object> r = rows.get(0);
            LegacyPlainSecrets s = new LegacyPlainSecrets();
            s.apiKey = r.get("api_key") == null ? null : String.valueOf(r.get("api_key"));
            s.apiSecret = r.get("api_secret") == null ? null : String.valueOf(r.get("api_secret"));
            s.passphrase = r.get("passphrase") == null ? null : String.valueOf(r.get("passphrase"));
            return s;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean legacyColumnsPresent() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_member_third_account' AND column_name IN ('api_key','api_secret')",
                    Integer.class
            );
            return cnt != null && cnt >= 2;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void tryEncryptAndMigrate(String accountId, LegacyPlainSecrets legacy) {
        try {
            AesGcmEncryptor encryptor = buildEncryptorOrThrow();
            String apiKeyEnc = encryptor.encrypt(legacy.apiKey);
            String apiSecretEnc = encryptor.encrypt(legacy.apiSecret);
            String passphraseEnc = legacy.passphrase != null && !legacy.passphrase.isBlank()
                    ? encryptor.encrypt(legacy.passphrase)
                    : null;
            jdbcTemplate.update(
                    "UPDATE t_member_third_account SET api_key_enc = ?, api_secret_enc = ?, passphrase_enc = ? WHERE id = ?",
                    apiKeyEnc, apiSecretEnc, passphraseEnc, accountId
            );
        } catch (Exception ignored) {
        }
    }

    private static final class LegacyPlainSecrets {
        String apiKey;
        String apiSecret;
        String passphrase;
    }

    private static final class CacheEntry {
        final AccountSecrets value;
        final long expiry;
        CacheEntry(AccountSecrets value, long expiry) {
            this.value = value;
            this.expiry = expiry;
        }
    }
}
