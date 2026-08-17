package com.chain.ai.trade.engine.strategy.core.rule;

import cn.hutool.json.JSONObject;
import com.chain.ai.trade.engine.strategy.entity.dos.EntryRuleCondition;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.extension.ta4j.core.rule.DirectionalRule;
import com.chain.ai.trade.extension.ta4j.core.rule.ExitSignal;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.IntPredicate;

public class IndicatorDrivenDirectionRule implements DirectionalRule {

    private final IntPredicate condition;
    private final Trade.TradeType direction;

    private IndicatorDrivenDirectionRule(IntPredicate condition, Trade.TradeType direction) {
        this.condition = condition;
        this.direction = direction;
    }

    public static IndicatorDrivenDirectionRule buildEntry(JSONObject indicatorConfig, BarSeries series) {
        String type = indicatorConfig.getStr("indicatorType");
        JSONObject params = indicatorConfig.getJSONObject("params");
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        switch (type) {
            case "rsi_simple": {
                int period = params.getInt("rsiPeriod", 14);
                int oversold = params.getInt("oversold", 30);
                RSIIndicator rsi = new RSIIndicator(close, period);
                return new IndicatorDrivenDirectionRule(
                        index -> rsi.getValue(index).isLessThan(series.numFactory().numOf(oversold)),
                        Trade.TradeType.BUY);
            }
            case "rsi_ma_trend": {
                int period = params.getInt("rsiPeriod", 14);
                int oversold = params.getInt("oversold", 30);
                int fastPeriod = params.getInt("fastMaPeriod", 9);
                int slowPeriod = params.getInt("slowMaPeriod", 26);
                RSIIndicator rsi2 = new RSIIndicator(close, period);
                EMAIndicator fastMa = new EMAIndicator(close, fastPeriod);
                EMAIndicator slowMa = new EMAIndicator(close, slowPeriod);
                return new IndicatorDrivenDirectionRule(
                        index -> rsi2.getValue(index).isLessThan(series.numFactory().numOf(oversold))
                                && fastMa.getValue(index).isGreaterThan(slowMa.getValue(index)),
                        Trade.TradeType.BUY);
            }
            case "macd_cross": {
                int fastPeriod = params.getInt("fastPeriod", 12);
                int slowPeriod = params.getInt("slowPeriod", 26);
                int signalPeriod = params.getInt("signalPeriod", 9);
                MACDIndicator macd = new MACDIndicator(close, fastPeriod, slowPeriod);
                EMAIndicator signal = new EMAIndicator(macd, signalPeriod);
                return new IndicatorDrivenDirectionRule(
                        index -> index > 0
                                && macd.getValue(index).isGreaterThan(signal.getValue(index))
                                && macd.getValue(index - 1).isLessThanOrEqual(signal.getValue(index - 1)),
                        Trade.TradeType.BUY);
            }
            default:
                throw new UnsupportedOperationException("Unknown indicator type: " + type);
        }
    }

    public static IndicatorDrivenDirectionRule buildFromConditions(
            List<EntryRuleCondition> conditions, Trade.TradeType direction, BarSeries series) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }

        IntPredicate combined = buildSingleCondition(conditions.get(0), series);

        for (int i = 1; i < conditions.size(); i++) {
            EntryRuleCondition cond = conditions.get(i);
            IntPredicate predicate = buildSingleCondition(cond, series);
            String connector = cond.getConnector() != null ? cond.getConnector().toLowerCase() : "and";
            IntPredicate left = combined;
            if ("or".equals(connector)) {
                combined = index -> left.test(index) || predicate.test(index);
            } else {
                combined = index -> left.test(index) && predicate.test(index);
            }
        }

        return new IndicatorDrivenDirectionRule(combined, direction);
    }

    private static IntPredicate buildSingleCondition(EntryRuleCondition cond, BarSeries series) {
        String indicatorType = cond.getIndicatorType().toUpperCase();
        String operator = cond.getOperator();
        BigDecimal threshold = cond.getThreshold();
        JSONObject params = cond.getIndicatorParams() != null
                ? new JSONObject(cond.getIndicatorParams())
                : new JSONObject();

        ClosePriceIndicator close = new ClosePriceIndicator(series);

        switch (indicatorType) {
            case "RSI": {
                int period = params.getInt("period", 14);
                RSIIndicator rsi = new RSIIndicator(close, period);
                if (threshold != null) {
                    return createComparison(rsi, operator, threshold, series);
                }
                break;
            }
            case "MACD": {
                int fastPeriod = params.getInt("fastPeriod", 12);
                int slowPeriod = params.getInt("slowPeriod", 26);
                int signalPeriod = params.getInt("signalPeriod", 9);
                MACDIndicator macd = new MACDIndicator(close, fastPeriod, slowPeriod);
                EMAIndicator signalLine = new EMAIndicator(macd, signalPeriod);
                if ("cross_up".equals(operator)) {
                    return index -> index > 0
                            && macd.getValue(index).isGreaterThan(signalLine.getValue(index))
                            && macd.getValue(index - 1).isLessThanOrEqual(signalLine.getValue(index - 1));
                }
                if ("cross_down".equals(operator)) {
                    return index -> index > 0
                            && macd.getValue(index).isLessThan(signalLine.getValue(index))
                            && macd.getValue(index - 1).isGreaterThanOrEqual(signalLine.getValue(index - 1));
                }
                break;
            }
            case "VOLUME": {
                int period = params.getInt("period", 20);
                VolumeIndicator volume = new VolumeIndicator(series, period);
                if (threshold != null) {
                    return createComparison(volume, operator, threshold, series);
                }
                break;
            }
            default:
                throw new UnsupportedOperationException("Unsupported indicator type: " + indicatorType);
        }

        throw new UnsupportedOperationException(
                "Unsupported combination: indicator=" + indicatorType + ", operator=" + operator);
    }

    private static IntPredicate createComparison(
            Indicator<Num> indicator, String operator, BigDecimal threshold, BarSeries series) {
        Num value = series.numFactory().numOf(threshold);
        switch (operator) {
            case "gt":
                return index -> indicator.getValue(index).isGreaterThan(value);
            case "lt":
                return index -> indicator.getValue(index).isLessThan(value);
            case "eq":
                return index -> indicator.getValue(index).isEqual(value);
            case "cross_up":
                return index -> index > 0
                        && indicator.getValue(index).isGreaterThan(value)
                        && indicator.getValue(index - 1).isLessThanOrEqual(value);
            case "cross_down":
                return index -> index > 0
                        && indicator.getValue(index).isLessThan(value)
                        && indicator.getValue(index - 1).isGreaterThanOrEqual(value);
            default:
                throw new UnsupportedOperationException("Unknown operator: " + operator);
        }
    }

    @Override
    public ExitSignal getSignal(int index, TradingRecord tradingRecord) {
        if (condition == null || direction == null) return null;
        if (condition.test(index)) {
            return new ExitSignal(direction, ExitType.TECHNICAL_INDICATOR);
        }
        return null;
    }

    @Override
    public Trade.TradeType getDirection(int index, TradingRecord tradingRecord) {
        ExitSignal signal = getSignal(index, tradingRecord);
        return signal != null ? signal.getDirection() : null;
    }
}
