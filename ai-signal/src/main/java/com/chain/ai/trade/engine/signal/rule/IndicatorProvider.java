package com.chain.ai.trade.engine.signal.rule;

import java.util.Map;

/**
 * SPI 接口：指标提供者
 * v5.2 设计文档 §4.2.1
 */
public interface IndicatorProvider {

    /**
     * 是否支持指定指标 ID
     */
    boolean supports(String indicatorType);

    /**
     * 解析指标值，返回通用类型容器
     *
     * @param ctx    评估上下文（含懒加载 K 线缓存）
     * @param params 指标参数（含 dataPeriod）
     */
    IndicatorValue resolve(WeightRuleContext ctx, Map<String, String> params);

    /**
     * 返回指标元数据
     */
    IndicatorMetadata getMetadata();
}
