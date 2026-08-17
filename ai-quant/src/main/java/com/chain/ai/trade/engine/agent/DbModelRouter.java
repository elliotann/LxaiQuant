package com.chain.ai.trade.engine.agent;

import com.chain.ai.trade.agent.model.ModelRouter;
import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.engine.service.LlmConfigService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
@Slf4j
public class DbModelRouter implements ModelRouter {

    private final LlmConfigService llmConfigService;
    private final String ollamaBaseUrl;
    private final String ollamaModel;

    private final ConcurrentHashMap<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    public DbModelRouter(LlmConfigService llmConfigService,
                         @Value("${ai.agent.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
                         @Value("${ai.agent.ollama.model:qwen3:4b}") String ollamaModel) {
        this.llmConfigService = llmConfigService;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
    }

    @Override
    public ChatModel route(String skillName) {
        return modelCache.computeIfAbsent("default", k -> buildFromDbConfig());
    }

    @Override
    public StreamingChatModel streamingRoute(String skillName) {
        return streamingModelCache.computeIfAbsent("active", k -> buildStreamingFromDbConfig());
    }

    private static final String PROVIDER_KEY_PREFIX = "provider:";

    private Config resolveConfig() {
        LlmConfig active = llmConfigService.getActiveSelection();
        if (active == null) {
            log.warn("t_llm_config 未配置，回退到 Ollama");
            return new Config("ollama", ollamaModel, ollamaBaseUrl, "");
        }
        String provider = active.getProvider() != null ? active.getProvider().toLowerCase() : "ollama";
        String model = active.getModel() != null ? active.getModel() : ollamaModel;
        if ("deepseek".equals(provider) && "deepseek-reasoner".equals(model)) {
            model = "deepseek-chat";
            log.info("DeepSeek 模型名映射: deepseek-reasoner → deepseek-chat (避免 thinking mode 兼容问题, 支持 Tool Calls)");
        }
        String providerKey = PROVIDER_KEY_PREFIX + provider;
        LlmConfig providerCfg = llmConfigService.getByKey(providerKey);
        String apiBaseUrl = providerCfg != null ? providerCfg.getApiBaseUrl() : null;
        String apiKey = llmConfigService.getDecryptedApiKey(providerKey);
        return new Config(provider, model, apiBaseUrl, apiKey);
    }

    private ChatModel buildFromDbConfig() {
        Config config = resolveConfig();
        switch (config.provider) {
            case "deepseek":
                if (config.apiKey.isEmpty()) {
                    log.warn("DeepSeek API Key 未配置，回退到 Ollama");
                    return buildOllamaModel(resolveOllamaUrl(config), config.model);
                }
                String deepseekBaseUrl = config.apiBaseUrl != null ? config.apiBaseUrl : "https://api.deepseek.com";
                return buildOpenAiModel(deepseekBaseUrl, config.apiKey, config.model);
            case "openclaw":
                if (config.apiKey.isEmpty()) {
                    log.warn("OpenClaw API Key 未配置，回退到 Ollama");
                    return buildOllamaModel(resolveOllamaUrl(config), config.model);
                }
                String openaiBaseUrl = config.apiBaseUrl != null ? config.apiBaseUrl : "https://api.openai.com/v1";
                return buildOpenAiModel(openaiBaseUrl, config.apiKey, config.model);
            default:
                return buildOllamaModel(resolveOllamaUrl(config), config.model);
        }
    }

    private StreamingChatModel buildStreamingFromDbConfig() {
        Config config = resolveConfig();
        switch (config.provider) {
            case "deepseek":
                if (config.apiKey.isEmpty()) {
                    log.warn("DeepSeek API Key 未配置，回退到 Ollama");
                    return buildOllamaStreamingModel(resolveOllamaUrl(config), config.model);
                }
                String deepseekBaseUrl = config.apiBaseUrl != null ? config.apiBaseUrl : "https://api.deepseek.com";
                return buildOpenAiStreamingModel(deepseekBaseUrl, config.apiKey, config.model);
            case "openclaw":
                if (config.apiKey.isEmpty()) {
                    log.warn("OpenClaw API Key 未配置，回退到 Ollama");
                    return buildOllamaStreamingModel(resolveOllamaUrl(config), config.model);
                }
                String openaiBaseUrl = config.apiBaseUrl != null ? config.apiBaseUrl : "https://api.openai.com/v1";
                return buildOpenAiStreamingModel(openaiBaseUrl, config.apiKey, config.model);
            default:
                return buildOllamaStreamingModel(resolveOllamaUrl(config), config.model);
        }
    }

    private String resolveOllamaUrl(Config config) {
        return config.apiBaseUrl != null ? config.apiBaseUrl : ollamaBaseUrl;
    }

    private static ChatModel buildOpenAiModel(String baseUrl, String apiKey, String model) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .build();
    }

    private static ChatModel buildOllamaModel(String baseUrl, String model) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    private static StreamingChatModel buildOpenAiStreamingModel(String baseUrl, String apiKey, String model) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    private static StreamingChatModel buildOllamaStreamingModel(String baseUrl, String model) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    private record Config(String provider, String model, String apiBaseUrl, String apiKey) {}
}
