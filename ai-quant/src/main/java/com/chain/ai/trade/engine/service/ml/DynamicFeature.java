package com.chain.ai.trade.engine.service.ml;

public interface DynamicFeature {
    String getVariantName();
    double extract(int index);
}
