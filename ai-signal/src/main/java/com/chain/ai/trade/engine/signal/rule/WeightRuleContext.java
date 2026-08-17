package com.chain.ai.trade.engine.signal.rule;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Data
public class WeightRuleContext {
    // 交易品种标识
    private String symbol;
    // 是否开多（true=开多，false=开空）
    private boolean isBuy;
    // 当前触发价格
    private double currentPrice;
    // 市场整体趋势方向（BULLISH/BEARISH/RANGING）
    private String marketTrend;
    // SMC 复合趋势状态（CompositeState 枚举名，如 STRONG_BULLISH_CONFIRMED_PULLBACK 等）
    private CompositeState trendState;

    /**
     * 信号触发周期
     * 例如: "15m", "1h", "4h"
     */
    private String signalPeriod;

    // SMC 趋势评分（已弃用，保留兼容）
    private Double smcTrendScore;
    // SMC 位置评分（已弃用，保留兼容）
    private Double smcPositionScore;
    // SMC 净盈亏比（已弃用，保留兼容）
    private Double smcNetRR;

    // 是否在震荡区间内
    private Boolean swingRanging;
    // 摆动突破方向
    private Integer swingBreakout;
    // SMC OB 是否在震荡
    private Boolean smcObRanging;
    // 星期几
    private Integer weekday;

    // SMC 趋势极性（-1=空头，0=震荡，1=多头）
    private Integer smcTrendPolarity;
    // SMC 趋势强度评分（0~100）
    private Double smcTrendStrength;
    // SMC 15分钟级别位置评分（已弃用，保留兼容）
    private Double smcPositionScore15m;
    // SMC 1小时级别位置评分（已弃用，保留兼容）
    private Double smcPositionScore1h;
    // 当前价格是否在订单块（OB）内部（0=否，1=是）
    private Integer smcInsideOB;
    // SMC 风险占当前价格百分比
    private Double smcRiskPercent;
    // SMC 目标盈利点数
    private Double smcRewardPoints;
    // SMC EMA 综合评分（已弃用，保留兼容）
    private Double smcEmaScore;
    // 开仓方向与趋势方向是否一致（-1=不一致，0=中性，1=一致）
    private Integer smcDirectionAligned;
    // 信号共振方向：顺势做多 / 顺势做空 / 逆势 / 方向分歧
    private String smcAlignment;
    // 当前价格是否在供给区（0=否，1=是）
    private Integer smcInSupplyZone;
    // 当前价格是否在需求区（0=否，1=是）
    private Integer smcInDemandZone;
    // 15M内部BOS方向是否一致（0=否，1=是）
    private Integer smcInternalBosAligned;
    // 混沌特例是否触发（0=否，1=是）
    private Integer smcChaosException;

    // ==================== §5 规则补充 SMC 字段 ====================
    // 20小时振幅百分比（用于窄幅/中幅横盘熔断）
    private double smcRangePercent20h;
    // 结构方向翻转次数（用于趋势流畅判断）
    private int smcFlipCount;
    // 1H 级别价格位置比率（0~1，0.382=支撑 0.618=阻力）
    private double smc1hPositionRatio;
    // 4H 级别价格位置比率（0~1，用于极端区判断）
    private double smc4hPositionRatio;
    // 4H 波次（如 2=主升, 3=加速, -2=主跌, -3=加速）
    private int smc4hWave;
    // 1H 波次
    private int smc1hWave;
    // 4H 结构年龄（条数，用于衰老判断）
    private int smc4hAge;
    // 1H 结构年龄（条数，用于新鲜/老化判断）
    private int smc1hAge;
    // HL 健康度（1=健康, 0=未知, -1=危险, -2=损坏）
    private int smcHlHealth;
    // LH 健康度（1=健康, 0=未知, -1=危险, -2=损坏）
    private int smcLhHealth;
    // SMC 盈亏比（用于最小盈亏比否决）
    private double smcRiskRewardRatio;
    // 仓位保证金占比%（用于最大仓位否决，需外部输入，默认为0）
    private double smcPositionMarginPercent;

    // K线快照列表（默认 K 线数据，信号触发周期的 K 线）
    private List<CandlestickSnapshot> kLines;

    // EMA 快线值
    private Double emaFast;
    // EMA 慢线值
    private Double emaSlow;
    // 快慢线比值
    private Double emaRatio;
    // MACD DIF 值
    private Double macdLine;
    // MACD DEA 值
    private Double macdSignal;
    // MACD 柱状图值
    private Double macdHistogram;
    // 当前均量比值
    private Double volumeRatio;
    // 成交量趋势方向（INCREASING/DECREASING）
    private String volumeTrend;
    // 价格在布林带/通道中的位置（0~1）
    private Double pricePosition;
    // 检测到的K线形态集合
    private Set<String> detectedPatterns;
    // 摆动点类型（HIGH/LOW）
    private String swingPointType;
    // 摆动点强度值
    private Double swingPointStrength;
    // 星期几（1=周一~7=周日）
    private Integer dayOfWeek;
    // 当前小时（0~23）
    private Integer hourOfDay;
    // 是否在交易时段内
    private boolean tradingSession;

    // ==================== L2 信号特征字段（来自 SignalFeatureProvider） ====================
    // 平均绝对空间（%）
    private double avgSpace;
    // 累积比：SUM(space) / SUM(abs_space)
    private double cumRatio;
    // 最近连续同向笔数
    private int directionSeq;
    // 上一次信号时间戳（毫秒）
    private long lastSignalTime;
    // 上一次信号方向（LONG/SHORT）
    private String lastDirection;
    // 最新一笔 space_pct
    private double latestSpace;
    // 当前触发信号方向（LONG/SHORT）
    private String currentDirection;

    // abs_space 分位数（用于 *_ABS_PERCENTILE 运算符）
    private double percentile20;
    private double percentile40;
    private double percentile70;
    private double percentile85;
    private double percentile95;

    // space 分位数（用于 *_RATIO_PERCENTILE 运算符）
    private double cumRatioPercentile40;
    private double cumRatioPercentile60;

    /**
     * 当前评估时间戳（毫秒）
     * <p>
     * 实盘由 DefaultSignService 注入 System.currentTimeMillis()；
     * 回测由 BacktestContextHolder 注入回测游标时间。
     */
    private long currentTimeMs;

    /**
     * 距上一次信号的等待分钟数（动态计算）
     * <p>
     * 用于 FEATURE_WAIT_MINUTES 指标，等价于 (currentTimeMs - lastSignalTime) / 60000。
     */
    public double getWaitMinutes() {
        if (currentTimeMs <= 0 || lastSignalTime <= 0) {
            return 0.0;
        }
        return (currentTimeMs - lastSignalTime) / 60000.0;
    }

    /**
     * 多周期 K 线懒加载缓存
     * key: 周期标识 ("15m", "1h", "4h")
     * value: 该周期的 K 线数据列表
     */
    private transient Map<String, List<CandlestickSnapshot>> periodKLineCache = new ConcurrentHashMap<>();

    // ==================== 核心方法：懒加载获取 K 线 ====================

    /**
     * 获取指定周期的 K 线数据（懒加载 + 缓存）
     */
    public List<CandlestickSnapshot> getKLinesForPeriod(String period, ICandlestickService service) {
        if (period == null || period.isEmpty() || period.equals(signalPeriod)) {
            return this.kLines;
        }

        List<CandlestickSnapshot> cached = periodKLineCache.get(period);
        if (cached != null) {
            return cached;
        }

        if (service == null || symbol == null || this.kLines == null || this.kLines.isEmpty()) {
            return null;
        }

        CandlestickIntervalEnum interval = parsePeriod(period);
        if (interval == null) {
            return null;
        }

        try {
            Long latestId = this.kLines.get(this.kLines.size() - 1).getId();
            long targetIntervalMs = interval.getMinNum() * 60L * 1000L;
            long alignedBoundary = (latestId / targetIntervalMs) * targetIntervalMs;
            long lastCompleteCandleId = alignedBoundary - targetIntervalMs;

            List<Candlestick> candlesticks = service.listByLeId(
                    lastCompleteCandleId, symbol, interval, 200);

            if (candlesticks == null || candlesticks.isEmpty()) {
                return null;
            }

            List<CandlestickSnapshot> result = candlesticks.stream()
                    .map(this::toSnapshot)
                    .collect(Collectors.toList());

            periodKLineCache.put(period, result);
            return result;

        } catch (Exception e) {
            log.warn("获取周期 {} K线数据失败: {}", period, e.getMessage());
            return null;
        }
    }

    /**
     * 预加载多周期 K 线数据（可选，提升首次评估性能）
     */
    public void preloadPeriodKLines(List<String> periods, ICandlestickService service) {
        for (String period : periods) {
            getKLinesForPeriod(period, service);
        }
    }

    private CandlestickIntervalEnum parsePeriod(String period) {
        for (CandlestickIntervalEnum interval : CandlestickIntervalEnum.values()) {
            if (interval.getCode().equalsIgnoreCase(period)) {
                return interval;
            }
        }
        return null;
    }

    private CandlestickSnapshot toSnapshot(Candlestick c) {
        CandlestickSnapshot snapshot = new CandlestickSnapshot();
        snapshot.setId(c.getId());
        snapshot.setOpen(c.getOpenPrice().doubleValue());
        snapshot.setHigh(c.getHighPrice().doubleValue());
        snapshot.setLow(c.getLowPrice().doubleValue());
        snapshot.setClose(c.getClosePrice().doubleValue());
        snapshot.setExchange(c.getExchange());
        return snapshot;
    }

    @Data
    public static class CandlestickSnapshot {
        private long id;
        private double open;
        private double high;
        private double low;
        private double close;
        private Exchange exchange;
    }
}
