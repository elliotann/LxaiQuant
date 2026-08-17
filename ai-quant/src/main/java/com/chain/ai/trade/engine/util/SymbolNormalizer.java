package com.chain.ai.trade.engine.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Symbol 标准化工具类
 * 用于统一处理交易对格式
 */
@Component
@Slf4j
public class SymbolNormalizer {

    /**
     * 默认后缀
     */
    private static final String DEFAULT_SUFFIX = "-USDT-SWAP";

    /**
     * 标准化 Symbol
     * 如果 Symbol 不是完整的交易对格式（不包含 -SWAP 或 -SPOT），则添加默认后缀
     *
     * @param symbol 原始 Symbol
     * @return 标准化后的 Symbol
     */
    public String normalize(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            log.warn("Symbol 为空，返回默认值: UNKNOWN");
            return "UNKNOWN";
        }

        String normalized = symbol.toUpperCase().trim();

        // 如果已经是完整的交易对格式，直接返回
        if (normalized.contains("-SWAP") || normalized.contains("-SPOT")) {
            return normalized;
        }

        // 添加默认后缀
        String result = normalized + DEFAULT_SUFFIX;
        log.debug("Symbol 标准化: {} -> {}", symbol, result);
        return result;
    }
}

