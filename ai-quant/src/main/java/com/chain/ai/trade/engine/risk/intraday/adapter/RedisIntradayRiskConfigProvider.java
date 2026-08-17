package com.chain.ai.trade.engine.risk.intraday.adapter;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskConfig;
import com.chain.ai.trade.engine.risk.intraday.port.RiskConfigPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisIntradayRiskConfigProvider implements RiskConfigPort {

    private static final String DEFAULT_CONFIG_KEY = "risk:intraday:config:default";

    private final RedisCache redisCache;
    private final ObjectMapper objectMapper;

    @Override
    public MemberRiskConfig getConfig(Long memberId) {
        String memberKey = memberId != null ? ("risk:intraday:config:member:" + memberId) : null;
        Object v = memberKey != null ? redisCache.get(memberKey) : null;
        if (v == null) {
            v = redisCache.get(DEFAULT_CONFIG_KEY);
        }
        try {
            if (v instanceof String s && !s.isBlank()) {
                return objectMapper.readValue(s, MemberRiskConfig.class);
            }
            if (v instanceof MemberRiskConfig c) {
                return c;
            }
        } catch (Exception ignored) {
        }
        return MemberRiskConfig.builder().build();
    }
}

