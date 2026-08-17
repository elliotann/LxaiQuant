package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.service.LlmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmProvidersController {

    private static final List<String> PROVIDERS = List.of("ollama", "deepseek", "openclaw");
    private static final String PROVIDER_KEY_PREFIX = "provider:";
    private static final String DEEPSEEK_DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String OPENCLAW_DEFAULT_BASE_URL = "http://192.168.1.17:18789";

    private final LlmConfigService llmConfigService;

    @GetMapping("/providers")
    public ApiResponse<Map<String, Object>> getProviders() {
        LlmConfig active = llmConfigService.getActiveSelection();
        Map<String, Object> activeDto = new HashMap<>();
        if (active != null) {
            activeDto.put("provider", active.getProvider());
            activeDto.put("model", active.getModel());
            activeDto.put("updatedAt", active.getUpdateTime());
        } else {
            activeDto.put("provider", "ollama");
            activeDto.put("model", "qwen3:4b");
        }

        List<String> keys = new ArrayList<>();
        for (String p : PROVIDERS) {
            keys.add(PROVIDER_KEY_PREFIX + p);
        }

        List<LlmConfig> configs = llmConfigService.listByKeys(keys);
        Map<String, LlmConfig> mapByKey = new HashMap<>();
        for (LlmConfig cfg : configs) {
            mapByKey.put(cfg.getConfigKey(), cfg);
        }

        List<Map<String, Object>> providers = new ArrayList<>();
        for (String p : PROVIDERS) {
            LlmConfig cfg = mapByKey.get(PROVIDER_KEY_PREFIX + p);
            Map<String, Object> dto = new HashMap<>();
            dto.put("provider", p);
            dto.put("model", cfg != null ? cfg.getModel() : null);
            if ("deepseek".equals(p)) {
                String baseUrl = cfg != null ? cfg.getApiBaseUrl() : null;
                dto.put("apiBaseUrl", baseUrl == null || baseUrl.isBlank() ? DEEPSEEK_DEFAULT_BASE_URL : baseUrl);
            } else if ("openclaw".equals(p)) {
                String baseUrl = cfg != null ? cfg.getApiBaseUrl() : null;
                dto.put("apiBaseUrl", baseUrl == null || baseUrl.isBlank() ? OPENCLAW_DEFAULT_BASE_URL : baseUrl);
            } else {
                dto.put("apiBaseUrl", cfg != null ? cfg.getApiBaseUrl() : null);
            }
            dto.put("apiKeyConfigured", cfg != null && Boolean.TRUE.equals(cfg.getApiKeyConfigured()));
            dto.put("extraConfig", cfg != null ? cfg.getExtraConfig() : null);
            dto.put("updatedAt", cfg != null ? cfg.getUpdateTime() : null);
            providers.add(dto);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("active", activeDto);
        result.put("providers", providers);
        return ApiResponse.success(result);
    }

    @PutMapping("/providers/{provider}")
    public ApiResponse<Map<String, Object>> upsertProvider(
        @PathVariable("provider") String provider,
        @RequestBody UpdateProviderRequest req
    ) {
        String p = provider == null ? null : provider.trim().toLowerCase();
        if (p == null || p.isBlank()) return ApiResponse.error(400, "provider 不能为空");

        LlmConfig saved;
        try {
            saved = llmConfigService.upsertByKey(
                PROVIDER_KEY_PREFIX + p,
                new LlmConfigService.UpdateRequest(
                    p,
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

    @GetMapping("/active")
    public ApiResponse<Map<String, Object>> getActive() {
        LlmConfig active = llmConfigService.getActiveSelection();
        Map<String, Object> dto = new HashMap<>();
        if (active != null) {
            dto.put("provider", active.getProvider());
            dto.put("model", active.getModel());
            dto.put("updatedAt", active.getUpdateTime());
        } else {
            dto.put("provider", "ollama");
            dto.put("model", "qwen3:4b");
        }
        return ApiResponse.success(dto);
    }

    @PutMapping("/active")
    public ApiResponse<Map<String, Object>> setActive(@RequestBody ActiveRequest req) {
        LlmConfig saved;
        try {
            saved = llmConfigService.setActiveSelection(req.provider, req.model);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
        Map<String, Object> dto = new HashMap<>();
        dto.put("provider", saved.getProvider());
        dto.put("model", saved.getModel());
        dto.put("updatedAt", saved.getUpdateTime());
        return ApiResponse.success("保存成功", dto);
    }

    public static class UpdateProviderRequest {
        public String model;
        public String apiBaseUrl;
        public String apiKey;
        public String extraConfig;
    }

    public static class ActiveRequest {
        public String provider;
        public String model;
    }
}
