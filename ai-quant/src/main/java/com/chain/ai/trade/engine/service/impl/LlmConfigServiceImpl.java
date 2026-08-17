package com.chain.ai.trade.engine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.engine.mapper.LlmConfigMapper;
import com.chain.ai.trade.engine.service.LlmConfigService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmConfigServiceImpl implements LlmConfigService {
    private static final String DEFAULT_CONFIG_KEY = "default";
    private static final String ACTIVE_CONFIG_KEY = "active";

    private final LlmConfigMapper llmConfigMapper;

    private volatile AesGcmEncryptor encryptor;

    @Override
    public LlmConfig getDefaultConfig() {
        return getByKey(DEFAULT_CONFIG_KEY);
    }

    @Override
    public LlmConfig upsertDefaultConfig(UpdateRequest req) {
        return upsertByKey(DEFAULT_CONFIG_KEY, req);
    }

    @Override
    public LlmConfig getByKey(String configKey) {
        return llmConfigMapper.selectOne(
            new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getConfigKey, configKey)
        );
    }

    @Override
    public List<LlmConfig> listByKeys(List<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) return List.of();
        return llmConfigMapper.selectList(
            new LambdaQueryWrapper<LlmConfig>().in(LlmConfig::getConfigKey, configKeys)
        );
    }

    @Override
    public LlmConfig getActiveSelection() {
        return getByKey(ACTIVE_CONFIG_KEY);
    }

    @Override
    public LlmConfig setActiveSelection(String provider, String model) {
        String p = normalizeProvider(provider);
        String m = safeTrim(model);
        if (p == null) throw new IllegalArgumentException("provider 不能为空");
        if (!isSupportedProvider(p)) throw new IllegalArgumentException("不支持的 provider: " + p);
        if (m == null) throw new IllegalArgumentException("model 不能为空");

        LlmConfig existing = getByKey(ACTIVE_CONFIG_KEY);
        if (existing == null) {
            existing = new LlmConfig();
            existing.setConfigKey(ACTIVE_CONFIG_KEY);
        }
        Date now = new Date();
        ensureBaseFields(existing, now);
        existing.setProvider(p);
        existing.setModel(m);
        existing.setApiBaseUrl(null);
        existing.setApiKeyEnc(null);
        existing.setApiKeyConfigured(false);
        existing.setExtraConfig(null);
        existing.setUpdateTime(now);
        existing.setUpdateBy("system");

        if (existing.getId() == null || existing.getId().isBlank()) {
            llmConfigMapper.insert(existing);
        } else {
            llmConfigMapper.updateById(existing);
        }
        return existing;
    }

    @Override
    public String getDecryptedActiveApiKey() {
        LlmConfig active = getActiveSelection();
        if (active == null || active.getApiKeyEnc() == null || active.getApiKeyEnc().isBlank()) {
            return "";
        }
        try {
            return getEncryptor().decrypt(active.getApiKeyEnc());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String getDecryptedApiKey(String configKey) {
        LlmConfig cfg = getByKey(configKey);
        if (cfg == null || cfg.getApiKeyEnc() == null || cfg.getApiKeyEnc().isBlank()) {
            return "";
        }
        try {
            return getEncryptor().decrypt(cfg.getApiKeyEnc());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public LlmConfig upsertByKey(String configKey, UpdateRequest req) {
        if (configKey == null || configKey.isBlank()) throw new IllegalArgumentException("configKey 不能为空");

        String provider = normalizeProvider(req.provider());
        String model = safeTrim(req.model());
        String apiBaseUrl = safeTrim(req.apiBaseUrl());
        String apiKeyPlain = safeTrim(req.apiKey());
        String extraConfig = safeTrim(req.extraConfig());

        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为空");
        }

        if (!isSupportedProvider(provider)) {
            throw new IllegalArgumentException("不支持的 provider: " + provider);
        }

        LlmConfig existing = getByKey(configKey);
        if (existing == null) {
            existing = new LlmConfig();
            existing.setConfigKey(configKey);
        }

        Date now = new Date();
        ensureBaseFields(existing, now);

        existing.setProvider(provider);
        existing.setModel(model);
        existing.setApiBaseUrl(apiBaseUrl);
        existing.setExtraConfig(extraConfig);
        existing.setUpdateTime(now);
        existing.setUpdateBy("system");

        if (providerRequiresKey(provider)) {
            if (apiKeyPlain == null || apiKeyPlain.isEmpty()) {
                if (existing.getApiKeyEnc() == null || existing.getApiKeyEnc().isBlank()) {
                    throw new IllegalArgumentException("apiKey 不能为空");
                }
                existing.setApiKeyConfigured(true);
            } else {
                existing.setApiKeyEnc(getEncryptor().encrypt(apiKeyPlain));
                existing.setApiKeyConfigured(true);
            }
        } else {
            existing.setApiKeyEnc(null);
            existing.setApiKeyConfigured(false);
        }

        if (existing.getId() == null || existing.getId().isBlank()) {
            llmConfigMapper.insert(existing);
        } else {
            llmConfigMapper.updateById(existing);
        }
        return existing;
    }

    private void ensureBaseFields(LlmConfig existing, Date now) {
        if (existing.getCreateTime() == null) existing.setCreateTime(now);
        if (existing.getUpdateTime() == null) existing.setUpdateTime(now);
        if (existing.getDeleteFlag() == null) existing.setDeleteFlag(false);
        if (existing.getCreateBy() == null || existing.getCreateBy().isBlank()) existing.setCreateBy("system");
        if (existing.getUpdateBy() == null || existing.getUpdateBy().isBlank()) existing.setUpdateBy("system");
    }

    private boolean providerRequiresKey(String provider) {
        return "deepseek".equals(provider) || "openclaw".equals(provider);
    }

    private boolean isSupportedProvider(String provider) {
        return "ollama".equals(provider) || "deepseek".equals(provider) || "openclaw".equals(provider);
    }

    private String normalizeProvider(String provider) {
        String val = safeTrim(provider);
        return val == null ? null : val.toLowerCase();
    }

    private String safeTrim(String val) {
        if (val == null) return null;
        String t = val.trim();
        return t.isEmpty() ? null : t;
    }

    private AesGcmEncryptor getEncryptor() {
        AesGcmEncryptor local = encryptor;
        if (local != null) return local;
        synchronized (this) {
            if (encryptor != null) return encryptor;
            String encryptionKey = System.getenv("ACCOUNT_SECRET_KEY");
            if (encryptionKey == null || encryptionKey.isEmpty()) {
                encryptionKey = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";
            }
            encryptor = new AesGcmEncryptor(encryptionKey);
            return encryptor;
        }
    }
}
