package com.chain.ai.trade.engine.utils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class PriceTargetsUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PRICE_TARGETS_KEY = "priceTargets";
    private static final String STOP_LOSS_LEVELS_KEY = "stopLossTargets"; // 保持与JSON结构一致
    private static final String OPTIMAL_STOP_LOSS_KEY = "suggestedStopLoss";
    private static final String OPTIMAL_TAKE_PROFIT_KEY = "suggestedTakeProfit";
}
