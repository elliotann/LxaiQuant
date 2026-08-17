package com.chain.ai.trade.extension.ta4j.core;

import com.chain.ai.trade.common.entity.dto.SignalInfo;
import org.ta4j.core.Rule;

import java.util.Map;

public interface SignalCacheAware {
    Map<String, SignalInfo> getSignalCache();
    Rule getLongMacdExitRule();
    Rule getShortMacdExitRule();
}
