package com.chain.ai.trade.engine2.rules;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.backtest.model.EntryRecord;
import com.chain.ai.trade.engine2.core.ScaleInReason;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 加仓条件规则接口 — 类似 ta4j IRule 的可组合接口。
 * <p>
 * 策略通过 {@link #shouldScaleIn(int, Bar, BarSeries, TradingContext, SignalType)} 返回加仓信号，
 * 支持 {@link #and(ScaleInRule)} / {@link #or(ScaleInRule)} / {@link #negate()} 组合。
 * <p>
 * 内置实现覆盖 V1 PositionAdditionHandler 的全部条件：
 * <ul>
 *   <li>{@link ProfitScaleInRule} — 浮盈达阈值</li>
 *   <li>{@link LossScaleInRule} — 浮亏达阈值</li>
 *   <li>{@link GapScaleInRule} — 间距控制</li>
 *   <li>{@link EmaTrendScaleInRule} — EMA 趋势过滤</li>
 *   <li>{@link MaxAddCountRule} — 加仓次数上限</li>
 * </ul>
 */
@FunctionalInterface
public interface ScaleInRule {

    /**
     * 判断当前 K 线是否满足加仓条件。
     *
     * @param index           当前 K 线索引
     * @param bar             当前 K 线
     * @param series          K 线序列（用于指标计算）
     * @param context         交易上下文（只读查询）
     * @param signalDirection 信号方向（LONG/SHORT），规则只应在该方向上检查
     * @return ScaleInSignal 或 null（不加仓）
     */
    ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection);

    /** 与组合 — 两个规则都满足才触发 */
    default ScaleInRule and(ScaleInRule other) {
        Objects.requireNonNull(other);
        return (index, bar, series, context, signalDirection) -> {
            ScaleInSignal a = shouldScaleIn(index, bar, series, context, signalDirection);
            if (a == null) return null;
            ScaleInSignal b = other.shouldScaleIn(index, bar, series, context, signalDirection);
            if (b == null) return null;
            return merge(a, b);
        };
    }

    /** 或组合 — 任一规则满足即触发 */
    default ScaleInRule or(ScaleInRule other) {
        Objects.requireNonNull(other);
        return (index, bar, series, context, signalDirection) -> {
            ScaleInSignal a = shouldScaleIn(index, bar, series, context, signalDirection);
            if (a != null) return a;
            return other.shouldScaleIn(index, bar, series, context, signalDirection);
        };
    }

    /** 非组合 — 条件不满足时触发 */
    default ScaleInRule negate() {
        return (index, bar, series, context, signalDirection) -> {
            ScaleInSignal result = shouldScaleIn(index, bar, series, context, signalDirection);
            return result != null ? null : new ScaleInSignal(null, null);
        };
    }

    /**
     * 合并两个 ScaleInSignal：取非空的 direction/reason，取较紧的 TP/SL。
     */
    private static ScaleInSignal merge(ScaleInSignal a, ScaleInSignal b) {
        SignalType dir = a.getDirection() != null ? a.getDirection() : b.getDirection();
        ScaleInReason reason = a.getReason() != null ? a.getReason() : b.getReason();
        BigDecimal tp = minOrNull(a.getTakeProfitPrice(), b.getTakeProfitPrice());
        BigDecimal sl = maxOrNull(a.getStopLossPrice(), b.getStopLossPrice());
        BigDecimal price = a.getPrice() != null ? a.getPrice() : b.getPrice();
        return new ScaleInSignal(dir, reason, tp, sl, price);
    }

    private static BigDecimal minOrNull(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }

    private static BigDecimal maxOrNull(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.max(b);
    }

    // ========================================================================
    // 内置实现
    // ========================================================================

    /**
     * 间距控制规则 — 当前价距上一笔加仓价间距 ≥ 阈值。
     * <p>
     * 对应 V1 参数 {@code addPosOnProfitGapPct} / {@code addPosOnLossGapPct}。
     */
    class GapScaleInRule implements ScaleInRule {
        private final double gapPct;

        public GapScaleInRule(double gapPct) {
            this.gapPct = gapPct;
        }

        @Override
        public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection) {
            List<EntryRecord> entries;
            if (signalDirection == SignalType.LONG) {
                entries = context.getLongEntries();
            } else {
                entries = context.getShortEntries();
            }
            if (entries == null || entries.isEmpty()) return null;

            // V1 兼容: gapApplicable = openItems.size() > 1，首次加仓不检查间距
            if (entries.size() <= 1) {
                return new ScaleInSignal(signalDirection, ScaleInReason.TREND_ADD);
            }

            // 取上一笔加仓价
            BigDecimal lastEntryPrice = entries.get(entries.size() - 1).getPrice();
            BigDecimal currentPrice = BigDecimal.valueOf(bar.getClosePrice().doubleValue());

            double pctChange;
            if (signalDirection == SignalType.LONG) {
                pctChange = currentPrice.subtract(lastEntryPrice)
                        .divide(lastEntryPrice, 8, RoundingMode.HALF_UP).doubleValue();
            } else {
                pctChange = lastEntryPrice.subtract(currentPrice)
                        .divide(lastEntryPrice, 8, RoundingMode.HALF_UP).doubleValue();
            }

            if (pctChange >= gapPct) {
                return new ScaleInSignal(signalDirection, ScaleInReason.TREND_ADD);
            }
            return null;
        }
    }

    /**
     * 加仓次数上限规则 — 当前加仓次数 < maxCount。
     * <p>
     * 作为 and 组合中的过滤规则使用，返回 null 表示次数已满禁止加仓。
     */
    class MaxAddCountRule implements ScaleInRule {
        private final int maxCount;

        public MaxAddCountRule(int maxCount) {
            this.maxCount = maxCount;
        }

        @Override
        public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection) {
            int entryCount;
            if (signalDirection == SignalType.LONG) {
                entryCount = context.getLongEntries().size();
            } else {
                entryCount = context.getShortEntries().size();
            }
            if (entryCount >= maxCount) return null;

            // 返回通过信号，merge 时会取另一规则的方向/原因
            return new ScaleInSignal(null, null);
        }
    }
}
