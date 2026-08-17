package com.chain.ai.trade.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "live-advice.scheduled")
public class ScheduledLiveAdviceProperties {

    private boolean enabled = false;
    private List<SymbolConfig> symbols = new ArrayList<>();
    private BigDecimal signalStrengthThreshold = BigDecimal.valueOf(50);

    @Data
    public static class SymbolConfig {
        private String symbol;
        private String accountId;
        private String interval = "3m";
        private BigDecimal leverage;
    }
}
