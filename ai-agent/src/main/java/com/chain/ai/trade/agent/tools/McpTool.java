package com.chain.ai.trade.agent.tools;

public interface McpTool {

    default String getContextData(String skillName, String userInput) {
        return "";
    }
}
