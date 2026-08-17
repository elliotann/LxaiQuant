package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.entity.LlmConfig;

import java.util.List;

public interface LlmConfigService {
    LlmConfig getDefaultConfig();

    LlmConfig upsertDefaultConfig(UpdateRequest req);

    LlmConfig getByKey(String configKey);

    LlmConfig upsertByKey(String configKey, UpdateRequest req);

    List<LlmConfig> listByKeys(List<String> configKeys);

    LlmConfig getActiveSelection();

    LlmConfig setActiveSelection(String provider, String model);

    String getDecryptedActiveApiKey();

    String getDecryptedApiKey(String configKey);

    record UpdateRequest(
        String provider,
        String model,
        String apiBaseUrl,
        String apiKey,
        String extraConfig
    ) {}
}
