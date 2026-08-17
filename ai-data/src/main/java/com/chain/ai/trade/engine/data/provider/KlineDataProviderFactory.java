package com.chain.ai.trade.engine.data.provider;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.provider.impl.RealKlineDataProvider;
import com.chain.ai.trade.engine.data.provider.impl.TestKlineDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * K线数据提供者工厂
 * 根据testMode参数选择不同的提供者实现
 */
@Component
@RequiredArgsConstructor
public class KlineDataProviderFactory {

    private final RealKlineDataProvider realProvider;
    private final TestKlineDataProvider testProvider;

    /**
     * 根据参数获取对应的提供者
     *
     * @param params 交易策略参数
     * @return K线数据提供者
     */
    public KlineDataProvider getProvider(TradingStrategyParams params) {
        if (params.getTestMode() != null && params.getTestMode()) {
            // 测试模式：重置提供者状态
            testProvider.reset();
            return testProvider;
        }
        return realProvider;
    }

    /**
     * 根据testMode标志获取对应的提供者
     *
     * @param testMode 是否测试模式
     * @return K线数据提供者
     */
    public KlineDataProvider getProvider(boolean testMode) {
        if (testMode) {
            testProvider.reset();
            return testProvider;
        }
        return realProvider;
    }
}
