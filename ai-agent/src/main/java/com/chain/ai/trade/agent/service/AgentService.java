package com.chain.ai.trade.agent.service;

import com.chain.ai.trade.agent.model.ModelRouter;
import com.chain.ai.trade.agent.tools.McpTool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentService {

    private final ModelRouter modelRouter;
    private final SkillLoader skillLoader;
    private final List<McpTool> allMcpTools;
    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();

    public AgentService(ModelRouter modelRouter, SkillLoader skillLoader,
                        List<McpTool> allMcpTools) {
        this.modelRouter = modelRouter;
        this.skillLoader = skillLoader;
        this.allMcpTools = allMcpTools;
    }

    private String enrichSystemPrompt(String skillName, String systemPrompt, String userInput) {
        StringBuilder enriched = new StringBuilder(systemPrompt);
        for (McpTool tool : allMcpTools) {
            String context = tool.getContextData(skillName, userInput);
            if (context != null && !context.isEmpty()) {
                enriched.append("\n\n").append(context);
            }
        }
        return enriched.toString();
    }

    public String chat(String skillName, String userInput, String sessionId) {
        var model = modelRouter.route(skillName);
        String basePrompt = skillLoader.getSkill(skillName);
        if (basePrompt == null) {
            throw new IllegalArgumentException("Unknown skill: " + skillName);
        }
        String systemPrompt = enrichSystemPrompt(skillName, basePrompt, userInput);

        ChatMemory memory = null;
        if (sessionId != null) {
            memory = sessionMemories.computeIfAbsent(sessionId,
                    id -> MessageWindowChatMemory.withMaxMessages(20));
        }

        Object[] toolsArray = allMcpTools.toArray();
        var builder = AiServices.builder(QuantAssistant.class)
                .chatModel(model)
                .systemMessageProvider(s -> systemPrompt)
                .tools(toolsArray);
        if (memory != null) {
            builder.chatMemory(memory);
        }
        QuantAssistant assistant = builder.build();

        return assistant.chat(userInput);
    }

    public TokenStream chatStream(String skillName, String userInput, String sessionId) {
        log.info("=== AgentService.chatStream === skill={}, tools count={}", skillName, allMcpTools.size());
        for (McpTool tool : allMcpTools) {
            log.info("  registered tool: {}", tool.getClass().getSimpleName());
        }

        var model = modelRouter.streamingRoute(skillName);
        String basePrompt = skillLoader.getSkill(skillName);
        if (basePrompt == null) {
            throw new IllegalArgumentException("Unknown skill: " + skillName);
        }
        String systemPrompt = enrichSystemPrompt(skillName, basePrompt, userInput);

        ChatMemory memory = null;
        if (sessionId != null) {
            memory = sessionMemories.computeIfAbsent(sessionId,
                    id -> MessageWindowChatMemory.withMaxMessages(20));
        }

        Object[] toolsArray = allMcpTools.toArray();
        var builder = AiServices.builder(StreamingQuantAssistant.class)
                .streamingChatModel(model)
                .systemMessageProvider(s -> systemPrompt)
                .tools(toolsArray);
        if (memory != null) {
            builder.chatMemory(memory);
        }
        StreamingQuantAssistant assistant = builder.build();

        return assistant.chat(userInput);
    }

    public interface QuantAssistant {
        String chat(@UserMessage String userMessage);
    }

    public interface StreamingQuantAssistant {
        TokenStream chat(@UserMessage String userMessage);
    }
}
