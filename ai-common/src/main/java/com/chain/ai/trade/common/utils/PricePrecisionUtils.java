package com.chain.ai.trade.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PricePrecisionUtils {

    private PricePrecisionUtils() {
    }

    public static BigDecimal normalizePrice(String symbol, BigDecimal price) {
        if (price == null) {
            return null;
        }
        int scale = resolvePriceScale(symbol);
        if (scale < 0) {
            return price;
        }
        return price.setScale(scale, RoundingMode.HALF_UP);
    }

    public static int resolvePriceScale(String symbol) {
        String base = resolveBaseAsset(symbol);
        if (base == null) {
            return 2;
        }
        if ("BTC".equals(base)) {
            return 1;
        }
        if ("ETH".equals(base)) {
            return 2;
        }
        return 2;
    }

    private static String resolveBaseAsset(String symbol) {
        if (symbol == null) {
            return null;
        }
        String s = symbol.trim();
        if (s.isEmpty()) {
            return null;
        }
        int dash = s.indexOf('-');
        String base = dash > 0 ? s.substring(0, dash) : s;
        base = base.trim().toUpperCase();
        return base.isEmpty() ? null : base;
    }
}

