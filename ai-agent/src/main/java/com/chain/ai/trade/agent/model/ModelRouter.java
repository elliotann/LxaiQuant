package com.chain.ai.trade.agent.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ModelRouter {
    ChatModel route(String skillName);
    StreamingChatModel streamingRoute(String skillName);
}
