package com.chain.ai.trade.member.service.impl;

import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.member.service.ProductMarketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 机器人商品类型处理器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BotProductHandler implements ProductMarketHandler {

    private final ITradingBotService tradingBotService;
    private final ObjectMapper objectMapper;

    @Override
    public String getProductType() {
        return "bot";
    }

    @Override
    public void validateSource(String sourceId, String userId) {
        TradingBot bot = tradingBotService.getByBotId(sourceId);
        if (bot == null || !bot.getUserId().equals(userId)) {
            throw new RuntimeException("机器人不存在或不属于您");
        }
    }

    @Override
    public String getConfigSnapshot(String sourceId) {
        try {
            TradingBot bot = tradingBotService.getByBotId(sourceId);
            if (bot == null) return null;

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("symbol", bot.getTradingPair());
            snapshot.put("trading_pair", bot.getTradingPair());
            snapshot.put("exchange", bot.getExchange());
            snapshot.put("botName", bot.getBotName());
            snapshot.put("initial_capital", bot.getAllocatedCapital());

            // 解析 configuration JSON 获取更多配置
            if (bot.getConfiguration() != null) {
                try {
                    Map<String, Object> cfg = objectMapper.readValue(bot.getConfiguration(),
                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                    snapshot.put("timeframe", cfg.getOrDefault("timeframe", ""));
                    snapshot.put("bot_type", cfg.getOrDefault("bot_type", cfg.getOrDefault("botType", "")));
                    snapshot.put("strategy_mode", cfg.getOrDefault("strategy_mode", cfg.getOrDefault("strategyMode", "")));
                    snapshot.put("indicator_name", cfg.getOrDefault("indicator_name", cfg.getOrDefault("indicatorName", "")));

                    Map<String, Object> tradingConfig = new HashMap<>();
                    tradingConfig.put("symbol", bot.getTradingPair());
                    tradingConfig.put("timeframe", cfg.getOrDefault("timeframe", ""));
                    tradingConfig.put("initial_capital", bot.getAllocatedCapital());
                    tradingConfig.put("exchange", bot.getExchange());
                    if (cfg.containsKey("bot_params")) {
                        tradingConfig.put("bot_params", cfg.get("bot_params"));
                    }
                    if (cfg.containsKey("botParams")) {
                        tradingConfig.put("bot_params", cfg.get("botParams"));
                    }
                    snapshot.put("trading_config", tradingConfig);
                } catch (Exception e) {
                    log.warn("Failed to parse bot configuration JSON, using basic fields only");
                    Map<String, Object> tradingConfig = new HashMap<>();
                    tradingConfig.put("symbol", bot.getTradingPair());
                    tradingConfig.put("initial_capital", bot.getAllocatedCapital());
                    tradingConfig.put("exchange", bot.getExchange());
                    snapshot.put("trading_config", tradingConfig);
                }
            } else {
                Map<String, Object> tradingConfig = new HashMap<>();
                tradingConfig.put("symbol", bot.getTradingPair());
                tradingConfig.put("initial_capital", bot.getAllocatedCapital());
                tradingConfig.put("exchange", bot.getExchange());
                snapshot.put("trading_config", tradingConfig);
            }

            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Failed to get bot config snapshot for sourceId={}", sourceId, e);
            return null;
        }
    }

    @Override
    public void onPurchaseComplete(String userId, Long listingId) {
        // 购买机器人后的业务逻辑（如创建关联记录）
    }

    @Override
    public void syncUpdate(String userId, Long listingId) {
        // 同步更新机器人配置
    }
}
