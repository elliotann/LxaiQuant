package com.chain.ai.trade.engine.risk.evaluator;

import java.time.LocalDateTime;
import java.util.List;

public interface SignalCountService {
    long countSignals(String symbol, String indicator, LocalDateTime startTime, LocalDateTime endTime, List<String> directions);
}
