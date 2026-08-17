package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.service.LlmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/llm/config")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService llmConfigService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getConfig() {
        LlmConfig cfg = llmConfigService.getDefaultConfig();
        if (cfg == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("provider", "ollama");
            empty.put("model", "qwen3:4b");
            empty.put("apiBaseUrl", null);
            empty.put("apiKeyConfigured", false);
            empty.put("extraConfig", null);
            return ApiResponse.success(empty);
        }

        Map<String, Object> dto = new HashMap<>();
        dto.put("provider", cfg.getProvider());
        dto.put("model", cfg.getModel());
        dto.put("apiBaseUrl", cfg.getApiBaseUrl());
        dto.put("apiKeyConfigured", Boolean.TRUE.equals(cfg.getApiKeyConfigured()));
        dto.put("extraConfig", cfg.getExtraConfig());
        dto.put("updatedAt", cfg.getUpdateTime());
        return ApiResponse.success(dto);
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> upsert(@RequestBody UpdateRequest req) {
        LlmConfig saved;
        try {
            saved = llmConfigService.upsertDefaultConfig(
                new LlmConfigService.UpdateRequest(
                    req.provider,
                    req.model,
                    req.apiBaseUrl,
                    req.apiKey,
                    req.extraConfig
                )
            );
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("保存失败: " + e.getMessage());
        }

        Map<String, Object> dto = new HashMap<>();
        dto.put("provider", saved.getProvider());
        dto.put("model", saved.getModel());
        dto.put("apiBaseUrl", saved.getApiBaseUrl());
        dto.put("apiKeyConfigured", Boolean.TRUE.equals(saved.getApiKeyConfigured()));
        dto.put("extraConfig", saved.getExtraConfig());
        dto.put("updatedAt", saved.getUpdateTime());
        return ApiResponse.success("保存成功", dto);
    }

    public static class UpdateRequest {
        public String provider;
        public String model;
        public String apiBaseUrl;
        public String apiKey;
        public String extraConfig;
    }
}
