package com.chain.ai.trade.engine.risk.adjuster.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 加仓专用仓位调节器 — 基于 QualityBasedAdjuster。
 * <p>
 * 双向持仓的主导逻辑由 BacktestEngine.applyPositionControl 处理（合约级精度），
 * 本类作为加仓调节器标识，后续可扩展加仓特有策略。
 * </p>
 *
 * @author system
 * @since 2026-08-08
 */
@Slf4j
@Component("scale-in-adjuster")
public class ScaleInPositionAdjuster extends QualityBasedAdjuster {

    @Override
    public String getId() {
        return "scale-in-adjuster";
    }
}
