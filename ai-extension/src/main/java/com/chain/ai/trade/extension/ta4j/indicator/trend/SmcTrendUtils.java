package com.chain.ai.trade.extension.ta4j.indicator.trend;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.common.entity.constants.TrendType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * smc方向判断
 */
@Slf4j
public class SmcTrendUtils {

    /**
     * 基于 H1 + M15 多周期 Swing/Internal 方向组合的趋势识别
     */
    public static TrendType identifyTrendType(int h1Swing, int h1Internal, int m15Swing, int m15Internal) {
        if (h1Swing == 1 && h1Internal == 1 && m15Swing == 1 && m15Internal == 1) return TrendType.STRONG_BULLISH;
        if (h1Swing == -1 && h1Internal == -1 && m15Swing == -1 && m15Internal == -1) return TrendType.STRONG_BEARISH;
        if (h1Swing == 1 && h1Internal == 1 && (m15Swing == -1 || m15Internal == -1)) return TrendType.BULLISH_PULLBACK;
        if (h1Swing == -1 && h1Internal == -1 && (m15Swing == 1 || m15Internal == 1)) return TrendType.BEARISH_PULLBACK;
        if ((h1Swing == -1|| h1Swing == 0)&& h1Internal == 1 && m15Swing == 1 && m15Internal == 1) return TrendType.POTENTIAL_BOTTOM;
        if ((h1Swing == 1 || h1Swing == 0 ) && h1Internal == -1 && m15Swing == -1 && m15Internal == -1) return TrendType.POTENTIAL_TOP;
        if (h1Swing == 0) return TrendType.RANGING;
        return TrendType.CHAOTIC;
    }

    /**
     * 基于 4H 结构位置的趋势状态识别（行情看板主趋势）
     * @param swingTrend 4H swingTrend
     * @param close 当前收盘价
     * @param lastSwingLow 最后一个摆动低点
     * @param lastHigherLow 最后一个更高的低点（HL）
     * @param lastSwingHigh 最后一个摆动高点
     * @param lastLowerHigh 最后一个更低的高点（LH）
     */
    public static TrendType resolve4hTrendState(int swingTrend, double close,
                                                 double lastSwingLow, double lastHigherLow,
                                                 double lastSwingHigh, double lastLowerHigh) {
        if (Double.isNaN(lastSwingLow) || Double.isNaN(lastSwingHigh)) {
            return TrendType.RANGING;
        }

        if (swingTrend == 1) {
            if (close < lastSwingLow) {
                if (!Double.isNaN(lastHigherLow) && close > lastHigherLow) {
                    return TrendType.BULLISH_PULLBACK;
                } else {
                    return TrendType.BULLISH_ENDING;
                }
            } else {
                return TrendType.STRONG_BULLISH;
            }
        } else if (swingTrend == -1) {
            if (close > lastSwingHigh) {
                if (!Double.isNaN(lastLowerHigh) && close < lastLowerHigh) {
                    return TrendType.BEARISH_PULLBACK;
                } else {
                    return TrendType.BEARISH_ENDING;
                }
            } else {
                return TrendType.STRONG_BEARISH;
            }
        } else {
            return TrendType.RANGING;
        }
    }

    /**
     * TrendType 转中文趋势状态名（行情看板用）
     */
    public static String toChineseName(TrendType type) {
        if (type == null) return "完全震荡";
        switch (type) {
            case STRONG_BULLISH: return "强上升";
            case BULLISH_PULLBACK: return "上升回调";
            case BULLISH_ENDING: return "上升末端";
            case STRONG_BEARISH: return "强下降";
            case BEARISH_PULLBACK: return "下降反弹";
            case BEARISH_ENDING: return "下降末端";
            default: return "完全震荡";
        }
    }


    /**
     * 获取详细的复合市场状态，返回 CompositeState 枚举（用于策略决策）
     * 基于持续性趋势方向、最近事件类型、价格位置，避免瞬时信号丢失。
     *
     * @param resultMap              周期结果映射
     * @param currentPrice          当前价格
     * @param priceBrokenHigherLow  价格是否跌破 lastHigherLow（上升回调时使用）
     * @param priceBrokenLowerHigh  价格是否突破 lastLowerHigh（下降反弹时使用）
     * @return 复合状态枚举
     */
    public static CompositeState getDetailedTrendState(
            Map<CandlestickIntervalEnum, SmartMoneyConceptsIndicator.Result> resultMap,
            double currentPrice,
            boolean priceBrokenHigherLow,
            boolean priceBrokenLowerHigh) {

        SmartMoneyConceptsIndicator.Result result4h = resultMap.get(CandlestickIntervalEnum.OKX4HOUR);
        SmartMoneyConceptsIndicator.Result result1h = resultMap.get(CandlestickIntervalEnum.OKXMIN60);
        SmartMoneyConceptsIndicator.Result result15m = resultMap.get(CandlestickIntervalEnum.OKXMIN15);

        if (result4h == null) {
            log.warn("SMC趋势判断: 缺少4H数据，返回UNKNOWN");
            return CompositeState.UNKNOWN;
        }

        // 宏观趋势
        TrendType macro4h = resolve4hTrendState(
                result4h.getSwingTrend(),
                currentPrice,
                result4h.getLastSwingLow(),
                result4h.getLastHigherLow(),
                result4h.getLastSwingHigh(),
                result4h.getLastLowerHigh()
        );

        // 获取持续性的趋势方向
        int h4Internal = result4h.getInternalTrend();
        int h1Swing = (result1h != null) ? result1h.getSwingTrend() : 0;
        int h1Internal = (result1h != null) ? result1h.getInternalTrend() : 0;
        int m15Swing = (result15m != null) ? result15m.getSwingTrend() : 0;
        int m15Internal = (result15m != null) ? result15m.getInternalTrend() : 0;

        // 获取最近事件类型（0=无, 1=BOS, 2=CHoCH）
        int h4LastEvent = result4h.getLastInternalEventType();
        int h1LastEvent = (result1h != null) ? result1h.getLastInternalEventType() : 0;
        int h1LastSwingEvent = (result1h != null) ? result1h.getLastSwingEventType() : 0;

        // 价格突破关键位（用于确认回调/反弹）
        boolean h1DownConfirmed = (h1Swing == -1) && (currentPrice < result1h.getLastSwingLow());
        boolean h1UpConfirmed   = (h1Swing == 1)  && (currentPrice > result1h.getLastSwingHigh());

        // ========== 强上升 ==========
        if (macro4h == TrendType.STRONG_BULLISH) {
            // 健康：4H内部无预警，1H内部无预警，1H无向下BOS，15M无反向内部趋势
            boolean healthy = (h4LastEvent != 2 || h4Internal != -1)
                    && (h1LastEvent != 2 || h1Internal != -1)
                    && !h1DownConfirmed
                    && (m15Internal != -1);
            if (healthy) {
                log.debug("SMC复合状态: 强上升·健康");
                return CompositeState.STRONG_BULLISH_HEALTHY;
            }
            // 4H内部预警
            if (h4LastEvent == 2 && h4Internal == -1) {
                log.debug("SMC复合状态: 强上升·预警回调（4H内部）");
                return CompositeState.STRONG_BULLISH_WARNING_4H;
            }
            // 1H内部预警
            if (h1LastEvent == 2 && h1Internal == -1 && !h1DownConfirmed) {
                log.debug("SMC复合状态: 强上升·预警回调（1H）");
                return CompositeState.STRONG_BULLISH_WARNING_1H;
            }
            // 浅回调
            if (m15Internal == -1 && h1Swing == 1) {
                log.debug("SMC复合状态: 强上升·浅回调");
                return CompositeState.STRONG_BULLISH_SHALLOW_PULLBACK;
            }
            // 确认回调（BOS 或 CHoCH 都算确认）
            if (h1DownConfirmed && (h1LastSwingEvent == 1 || h1LastSwingEvent == 2)) {
                log.debug("SMC复合状态: 强上升·确认回调");
                return CompositeState.STRONG_BULLISH_CONFIRMED_PULLBACK;
            }
            // 默认健康
            log.debug("SMC复合状态: 强上升·健康");
            return CompositeState.STRONG_BULLISH_HEALTHY;
        }

        // ========== 上升回调 ==========
        if (macro4h == TrendType.BULLISH_PULLBACK) {
            if (priceBrokenHigherLow) {
                log.debug("SMC复合状态: 上升回调·失败");
                return CompositeState.BULLISH_PULLBACK_FAILURE;
            }
            // 筑底
            if (h1Internal == 1 && m15Internal == 1) {
                log.debug("SMC复合状态: 上升回调·筑底");
                return CompositeState.BULLISH_PULLBACK_BOTTOMING;
            }
            log.debug("SMC复合状态: 上升回调·进行中");
            return CompositeState.BULLISH_PULLBACK_ONGOING;
        }

        // ========== 上升末端 ==========
        if (macro4h == TrendType.BULLISH_ENDING) {
            if (h1UpConfirmed && (h1LastSwingEvent == 1 || h1LastSwingEvent == 2)) {
                log.debug("SMC复合状态: 上升末端·转势确认");
                return CompositeState.BULLISH_ENDING_CONFIRM;
            }
            log.debug("SMC复合状态: 上升末端·延续下跌");
            return CompositeState.BULLISH_ENDING_CONTINUE_DOWN;
        }

        // ========== 强下降 ==========
        if (macro4h == TrendType.STRONG_BEARISH) {
            // 健康
            boolean healthy = (h4LastEvent != 2 || h4Internal != 1)
                    && (h1LastEvent != 2 || h1Internal != 1)
                    && !h1UpConfirmed
                    && (m15Internal != 1);
            if (healthy) {
                log.debug("SMC复合状态: 强下降·健康");
                return CompositeState.STRONG_BEARISH_HEALTHY;
            }
            // 4H内部预警
            if (h4LastEvent == 2 && h4Internal == 1) {
                log.debug("SMC复合状态: 强下降·预警反弹（4H内部）");
                return CompositeState.STRONG_BEARISH_WARNING_4H;
            }
            // 1H内部预警
            if (h1LastEvent == 2 && h1Internal == 1 && !h1UpConfirmed) {
                log.debug("SMC复合状态: 强下降·预警反弹（1H）");
                return CompositeState.STRONG_BEARISH_WARNING_1H;
            }
            // 浅反弹
            if (m15Internal == 1 && h1Swing == -1) {
                log.debug("SMC复合状态: 强下降·浅反弹");
                return CompositeState.STRONG_BEARISH_SHALLOW_BOUNCE;
            }
            // 确认反弹（BOS 或 CHoCH 都算确认）
            if (h1UpConfirmed && (h1LastSwingEvent == 1 || h1LastSwingEvent == 2)) {
                log.debug("SMC复合状态: 强下降·确认反弹");
                return CompositeState.STRONG_BEARISH_CONFIRMED_BOUNCE;
            }
            log.debug("SMC复合状态: 强下降·健康");
            return CompositeState.STRONG_BEARISH_HEALTHY;
        }

        // ========== 下降反弹 ==========
        if (macro4h == TrendType.BEARISH_PULLBACK) {
            if (priceBrokenLowerHigh) {
                log.debug("SMC复合状态: 下降反弹·失败");
                return CompositeState.BEARISH_PULLBACK_FAILURE;
            }
            // 筑顶
            if (h1Internal == -1 && m15Internal == -1) {
                log.debug("SMC复合状态: 下降反弹·筑顶");
                return CompositeState.BEARISH_PULLBACK_TOPPING;
            }
            log.debug("SMC复合状态: 下降反弹·进行中");
            return CompositeState.BEARISH_PULLBACK_ONGOING;
        }

        // ========== 下降末端 ==========
        if (macro4h == TrendType.BEARISH_ENDING) {
            if (h1DownConfirmed && (h1LastSwingEvent == 1 || h1LastSwingEvent == 2)) {
                log.debug("SMC复合状态: 下降末端·转势确认");
                return CompositeState.BEARISH_ENDING_CONFIRM;
            }
            log.debug("SMC复合状态: 下降末端·延续反弹");
            return CompositeState.BEARISH_ENDING_CONTINUE_UP;
        }

        // ========== 完全震荡 ==========
        if (macro4h == TrendType.RANGING) {
            log.debug("SMC复合状态: 震荡·无方向");
            return CompositeState.RANGING_NO_DIRECTION;
        }

        log.warn("SMC趋势判断: 未匹配到任何已知状态，返回UNKNOWN");
        return CompositeState.UNKNOWN;
    }
}
