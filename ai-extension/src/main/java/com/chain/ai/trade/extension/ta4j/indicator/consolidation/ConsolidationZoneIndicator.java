package com.chain.ai.trade.extension.ta4j.indicator.consolidation;

import com.chain.ai.trade.extension.ta4j.indicator.consolidation.ConsolidationLevel;
import com.chain.ai.trade.extension.ta4j.indicator.consolidation.ConsolidationZone;
import com.chain.ai.trade.extension.ta4j.indicator.consolidation.RangeNormalization;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.time.Instant;

import static com.chain.ai.trade.extension.ta4j.indicator.consolidation.RangeNormalization.*;

/**
 * 动态横盘箱体识别指标（锚点锁定模式）。
 * <p>
 * 核心设计：
 * <ul>
 *   <li><b>锚点锁定：</b>箱体一旦建立，边界永久固定，绝不因后续价格波动而缩小。</li>
 *   <li><b>状态持久化：</b>缓存当前有效的箱体，新K线只做突破检测，避免重复回溯。</li>
 *   <li><b>容错机制：</b>允许影线或收盘价轻微越界（幅度小于 tolerancePercent），视为噪音，不中断箱体。</li>
 * </ul>
 * </p>
 */
public class ConsolidationZoneIndicator extends CachedIndicator<ConsolidationZone> {

    // ==================== 可配置参数 ====================
    private final int minLookback;
    private final double maxAmpPercent;
    private final double ultraTightThreshold;
    private final double tightThreshold;
    private final RangeNormalization normalizationType;
    private final double tolerancePercent;      // 容错率（%），例如 0.3 表示 0.3%

    // ==================== 状态缓存（锚点） ====================
    private ConsolidationZone currentZone;      // 当前激活的箱体
    private int currentIndex;                   // 箱体对应的最新索引
    private boolean isActive;                   // 是否处于横盘状态

    // ==================== 构造方法 ====================

    /**
     * 完整构造器（含容错参数）。
     */
    public ConsolidationZoneIndicator(BarSeries series,
                                      int minLookback,
                                      RangeNormalization normalizationType,
                                      double maxAmpPercent,
                                      double ultraTightThreshold,
                                      double tightThreshold,
                                      double tolerancePercent) {
        super(series);
        this.minLookback = minLookback;
        this.normalizationType = normalizationType;
        this.maxAmpPercent = maxAmpPercent;
        this.ultraTightThreshold = ultraTightThreshold;
        this.tightThreshold = tightThreshold;
        this.tolerancePercent = tolerancePercent;
        this.currentZone = null;
        this.currentIndex = -1;
        this.isActive = false;
    }

    /**
     * 简化构造器（使用推荐默认值：CURRENT_PRICE，超窄2%，窄幅5%，最大10%，容错0.3%）。
     */
    public ConsolidationZoneIndicator(BarSeries series, int minLookback) {
        this(series, minLookback, RangeNormalization.CURRENT_PRICE, 10.0, 2.0, 5.0, 0.3);
    }

    // ==================== 核心计算 ====================

    @Override
    protected ConsolidationZone calculate(int index) {
        BarSeries series = getBarSeries();

        // 数据不足
        if (index < minLookback - 1) {
            return null;
        }

        // ---- 情况1：当前没有激活的箱体，尝试建立 ----
        if (!isActive || currentZone == null) {
            ConsolidationZone newZone = buildInitialZone(index);
            if (newZone != null) {
                currentZone = newZone;
                currentIndex = index;
                isActive = true;
                return currentZone;
            } else {
                // 未找到有效横盘
                isActive = false;
                currentZone = null;
                currentIndex = index;
                return null;
            }
        }

        // ---- 情况2：已有激活箱体，且当前索引连续（步进+1） ----
        if (index == currentIndex + 1) {
            Bar newBar = series.getBar(index);
            boolean isBroken = checkBreakout(newBar);

            if (isBroken) {
                // 有效突破 → 横盘结束，重置状态并重新建立
                isActive = false;
                currentZone = null;
                currentIndex = index;
                // 递归调用，尝试在新位置建立箱体
                return calculate(index);
            } else {
                // 仍在箱体内，更新结束索引和时间
                currentIndex = index;
                // 返回新对象（边界不变，仅更新 endIndex 和 endTime）
                return new ConsolidationZone(
                        currentZone.getStartIndex(), index,
                        currentZone.getStartTime(), series.getBar(index).getEndTime(),
                        currentZone.getValueHigh(), currentZone.getValueLow(),
                        currentZone.getPhysicalHigh(), currentZone.getPhysicalLow(),
                        currentZone.getLevel(), currentZone.getAmplitudePercent()
                );
            }
        }

        // ---- 情况3：跳跃式访问（索引跳过了若干步） ----
        if (index > currentIndex + 1) {
            // 检查从 currentIndex+1 到 index 的每一根K线，是否发生了突破
            for (int i = currentIndex + 1; i <= index; i++) {
                Bar bar = series.getBar(i);
                if (checkBreakout(bar)) {
                    // 如果中途有突破，则从该突破处重置
                    isActive = false;
                    currentZone = null;
                    currentIndex = i;
                    return calculate(index);
                }
            }
            // 若中间无突破，则直接扩展箱体至当前 index
            currentIndex = index;
            return new ConsolidationZone(
                    currentZone.getStartIndex(), index,
                    currentZone.getStartTime(), series.getBar(index).getEndTime(),
                    currentZone.getValueHigh(), currentZone.getValueLow(),
                    currentZone.getPhysicalHigh(), currentZone.getPhysicalLow(),
                    currentZone.getLevel(), currentZone.getAmplitudePercent()
            );
        }

        // ---- 其他情况（如重复调用同一索引），直接返回当前箱体 ----
        return currentZone;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建初始箱体（首次识别）。
     * <p>
     * 从当前 index 向左动态回溯，直到遇到有效突破或到达序列起点。
     * 回溯过程中，若遇到越界但幅度 ≤ tolerancePercent，则忽略并继续。
     * </p>
     *
     * @param index 当前K线索引
     * @return 如果识别到横盘则返回 ConsolidationZone，否则返回 null
     */
    private ConsolidationZone buildInitialZone(int index) {
        BarSeries series = getBarSeries();
        int endIdx = index;
        int startIdx = index - minLookback + 1;

        // 初始化极值
        Bar firstBar = series.getBar(startIdx);
        Num valueHigh = firstBar.getClosePrice();
        Num valueLow = firstBar.getClosePrice();
        Num physicalHigh = firstBar.getHighPrice();
        Num physicalLow = firstBar.getLowPrice();

        for (int i = startIdx + 1; i <= endIdx; i++) {
            Bar bar = series.getBar(i);
            valueHigh = valueHigh.max(bar.getClosePrice());
            valueLow = valueLow.min(bar.getClosePrice());
            physicalHigh = physicalHigh.max(bar.getHighPrice());
            physicalLow = physicalLow.min(bar.getLowPrice());
        }

        // 初筛：振幅是否超标
        double initAmp = calcAmplitude(valueHigh, valueLow, index);
        if (initAmp > maxAmpPercent) {
            return null;
        }

        // 动态回溯（带容错）
        int prevIdx = startIdx - 1;
        while (prevIdx >= 0) {
            Bar bar = series.getBar(prevIdx);
            Num prevHigh = bar.getHighPrice();
            Num prevLow = bar.getLowPrice();
            Num prevClose = bar.getClosePrice();

            // 检查影线越界（带容错）
            boolean highBreak = prevHigh.isGreaterThan(physicalHigh);
            boolean lowBreak = prevLow.isLessThan(physicalLow);
            if (highBreak || lowBreak) {
                double overPercent;
                if (highBreak) {
                    overPercent = prevHigh.minus(physicalHigh)
                            .dividedBy(physicalHigh).doubleValue() * 100;
                } else {
                    overPercent = physicalLow.minus(prevLow)
                            .dividedBy(physicalLow).doubleValue() * 100;
                }
                if (overPercent > tolerancePercent) {
                    break; // 越界幅度过大，停止回溯
                }
                // 否则视为噪音，忽略此越界，继续纳入
            }

            // 检查收盘价越界（带容错）
            boolean closeHighBreak = prevClose.isGreaterThan(valueHigh);
            boolean closeLowBreak = prevClose.isLessThan(valueLow);
            if (closeHighBreak || closeLowBreak) {
                double overPercent;
                if (closeHighBreak) {
                    overPercent = prevClose.minus(valueHigh)
                            .dividedBy(valueHigh).doubleValue() * 100;
                } else {
                    overPercent = valueLow.minus(prevClose)
                            .dividedBy(valueLow).doubleValue() * 100;
                }
                if (overPercent > tolerancePercent) {
                    break;
                }
                // 否则忽略，继续
            }

            // 纳入该K线（边界更新）
            // 价值区间在收盘价未真正突破时不变化，但为了防御，仍用max/min（实际不会扩大）
            valueHigh = valueHigh.max(prevClose);
            valueLow = valueLow.min(prevClose);
            physicalHigh = physicalHigh.max(prevHigh);
            physicalLow = physicalLow.min(prevLow);

            startIdx = prevIdx;
            prevIdx--;
        }

        // 最终振幅
        double finalAmp = calcAmplitude(valueHigh, valueLow, index);
        ConsolidationLevel level;
        if (finalAmp <= ultraTightThreshold) {
            level = ConsolidationLevel.ULTRA_TIGHT;
        } else if (finalAmp <= tightThreshold) {
            level = ConsolidationLevel.TIGHT;
        } else if (finalAmp <= maxAmpPercent) {
            level = ConsolidationLevel.WIDE;
        } else {
            return null;
        }

        // 获取时间
        Instant startTime = series.getBar(startIdx).getBeginTime();
        Instant endTime = series.getBar(endIdx).getEndTime();

        return new ConsolidationZone(
                startIdx, endIdx,
                startTime, endTime,
                valueHigh, valueLow,
                physicalHigh, physicalLow,
                level, finalAmp
        );
    }

    /**
     * 检查新K线是否有效突破当前箱体。
     * <p>
     * 判定逻辑：如果影线或收盘价突破物理边界，且越界幅度超过容错率，则视为有效突破。
     * </p>
     *
     * @param bar 新K线
     * @return true 表示有效突破，横盘结束；false 表示仍在箱体内
     */
    private boolean checkBreakout(Bar bar) {
        if (currentZone == null) return false;

        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        Num close = bar.getClosePrice();

        Num physicalHigh = currentZone.getPhysicalHigh();
        Num physicalLow = currentZone.getPhysicalLow();

        boolean highBreak = high.isGreaterThan(physicalHigh);
        boolean lowBreak = low.isLessThan(physicalLow);

        if (highBreak || lowBreak) {
            double overPercent;
            if (highBreak) {
                overPercent = high.minus(physicalHigh)
                        .dividedBy(physicalHigh).doubleValue() * 100;
            } else {
                overPercent = physicalLow.minus(low)
                        .dividedBy(physicalLow).doubleValue() * 100;
            }
            // 如果越界幅度超过容错，或者收盘价确认突破，则判为有效
            boolean closeConfirmed = close.isGreaterThan(physicalHigh) ||
                    close.isLessThan(physicalLow);
            return closeConfirmed || (overPercent > tolerancePercent);
        }
        return false;
    }

    /**
     * 计算价值区间的振幅（按所选归一化策略）。
     */
    private double calcAmplitude(Num valueHigh, Num valueLow, int index) {
        Num range = valueHigh.minus(valueLow);
        BarSeries series = getBarSeries();
        Num denom;

        switch (normalizationType) {
            case CURRENT_PRICE:
                denom = series.getBar(index).getClosePrice();
                break;
            case MID_PRICE:
                denom = valueHigh.plus(valueLow).dividedBy(DecimalNum.valueOf(2));
                break;
            case ABSOLUTE:
                return range.doubleValue();
            default:
                return 0;
        }

        if (denom.isLessThan(DecimalNum.valueOf(0.0001))) {
            return Double.MAX_VALUE;
        }

        return range.dividedBy(denom).doubleValue() * 100;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}