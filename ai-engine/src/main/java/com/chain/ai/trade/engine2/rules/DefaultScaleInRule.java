package com.chain.ai.trade.engine2.rules;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.backtest.BacktestConfig;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Objects;

/**
 * 配置驱动的默认加仓规则 — 从 BacktestConfig 读取参数，组合内置规则。
 * <p>
 * 等效于 V1 {@code PositionAdditionHandler.shouldTriggerSupplementaryOrder()} 的逻辑。
 * 策略可直接使用，无需手动编写 ScaleInRule 组合。
 * <p>
 * 组合逻辑（OR 关系）：
 * <pre>
 *   (profitRule.and(gapRule).and(emaRule).and(maxCountRule))
 *     .or(lossRule.and(gapRule).and(maxCountRule))
 * </pre>
 */
@Slf4j
public class DefaultScaleInRule implements ScaleInRule {

    private final ScaleInRule composedRule;

    public DefaultScaleInRule(BacktestConfig config, BarSeries series) {
        Objects.requireNonNull(config, "BacktestConfig must not be null");

        ScaleInRule maxCountRule = new MaxAddCountRule(config.getMaxAddPositions());
        ScaleInRule result = null;

        // 盈利加仓分支
        if (config.getAddPosOnProfitPct() > 0) {
            ScaleInRule profitRule = new ProfitScaleInRule(config.getAddPosOnProfitPct());
            if (config.getAddPosOnProfitGapPct() > 0) {
                profitRule = profitRule.and(new GapScaleInRule(config.getAddPosOnProfitGapPct()));
            }
            if (config.isProfitAddEmaTrendEnabled()) {
                profitRule = profitRule.and(new EmaTrendScaleInRule(
                        config.getProfitAddEmaFastPeriod(),
                        config.getProfitAddEmaSlowPeriod(),
                        config.getProfitAddEmaMinConsecutiveBars()
                ));
            }
            profitRule = profitRule.and(maxCountRule);
            result = profitRule;
        }

        // 亏损补仓分支
        if (config.getAddPosOnLossPct() > 0) {
            ScaleInRule lossRule = new LossScaleInRule(config.getAddPosOnLossPct());
            if (config.getAddPosOnLossGapPct() > 0) {
                lossRule = lossRule.and(new GapScaleInRule(config.getAddPosOnLossGapPct()));
            }
            lossRule = lossRule.and(maxCountRule);
            result = (result != null) ? result.or(lossRule) : lossRule;
        }

        this.composedRule = result;
    }

    @Override
    public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection) {
        if (composedRule == null) {
            log.info("DefaultScaleInRule: composedRule is null, config may be missing required parameters");
            return null;
        }
        return composedRule.shouldScaleIn(index, bar, series, context, signalDirection);
    }
}
