package com.chain.ai.trade.agent.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MultiModelRouter {

    private final Map<ModelType, ChatModel> models = new ConcurrentHashMap<>();

    @Value("${ai.agent.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.agent.openai.model:gpt-4}")
    private String openaiModel;

    @Value("${ai.agent.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.agent.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${ai.agent.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.agent.ollama.model:llama3}")
    private String ollamaModel;

    @PostConstruct
    public void init() {
        if (!openaiApiKey.isBlank()) {
            models.put(ModelType.OPENAI, OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(openaiModel)
                    .build());
            log.info("OpenAI model initialized: {}", openaiModel);
        }
        if (!deepseekApiKey.isBlank()) {
            models.put(ModelType.DEEPSEEK, OpenAiChatModel.builder()
                    .baseUrl("https://api.deepseek.com/v1")
                    .apiKey(deepseekApiKey)
                    .modelName(deepseekModel)
                    .build());
            log.info("DeepSeek model initialized: {}", deepseekModel);
        }
        models.put(ModelType.OLLAMA, OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .build());
        log.info("Ollama model initialized: {} at {}", ollamaModel, ollamaBaseUrl);
    }

    public ChatModel route(String skillName) {
        if ("strategy-gen".equals(skillName) && models.containsKey(ModelType.OPENAI)) {
            return models.get(ModelType.OPENAI);
        }
        if (("review".equals(skillName) || "live-advice".equals(skillName))
                && models.containsKey(ModelType.DEEPSEEK)) {
            return models.get(ModelType.DEEPSEEK);
        }
        if ("radar-analysis".equals(skillName) && models.containsKey(ModelType.OLLAMA)) {
            return models.get(ModelType.OLLAMA);
        }
        return models.values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No AI model configured"));
    }
}
