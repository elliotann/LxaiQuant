package com.chain.ai.trade.extension.ta4j.core.strategy;

import com.chain.ai.trade.extension.ta4j.core.rule.ExitSignal;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

/**
 * 双向持仓策略
 */
public interface MultiPositionStrategy extends Strategy {
    default Trade.TradeType shouldEnterDirection(int index, TradingRecord tradingRecord) {
        return shouldEnter(index, tradingRecord) ? getStartingType() : null;
    }

    default Trade.TradeType shouldExitDirection(int index, TradingRecord tradingRecord) {
        return shouldExit(index, tradingRecord) ? getStartingType().complementType() : null;
    }
    // 返回包含原因的退出信号，子类必须覆盖
    default ExitSignal shouldExitSignal(int index, TradingRecord tradingRecord) {
        throw new UnsupportedOperationException("MultiPositionStrategy subclasses must override 'shouldExitSignal'");
    }
    @Override
    default Strategy and(Strategy strategy) {
        throw new UnsupportedOperationException("MultiPositionStrategy does not support 'and' composition");
    }

    @Override
    default Strategy or(Strategy strategy) {
        throw new UnsupportedOperationException("MultiPositionStrategy does not support 'or' composition");
    }

    @Override
    default Strategy and(String name, Strategy strategy, int unstableBars) {
        throw new UnsupportedOperationException("MultiPositionStrategy does not support 'and' composition");
    }

    @Override
    default Strategy or(String name, Strategy strategy, int unstableBars) {
        throw new UnsupportedOperationException("MultiPositionStrategy does not support 'or' composition");
    }

    @Override
    default Strategy opposite() {
        throw new UnsupportedOperationException("MultiPositionStrategy does not support 'opposite' composition");
    }
}
