package com.chain.ai.trade.extension.strategy;

/**
 * 信号驱动型关键点位策略
 * 信号生成使用，方向由信号自身提供，差异化体现在参数配置上
 */
public interface SignalCriticalLevelsStrategy extends CriticalLevelsStrategy {

    CriticalLevelsConfig getConfig();
}
