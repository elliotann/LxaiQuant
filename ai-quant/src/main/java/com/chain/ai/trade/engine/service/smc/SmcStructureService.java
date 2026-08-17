package com.chain.ai.trade.engine.service.smc;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.dos.SmcBarResult;
import com.chain.ai.trade.engine.data.entity.dto.smc.ChaosExceptionResult;
import com.chain.ai.trade.engine.data.entity.dto.smc.MultiPeriodSmcData;
import com.chain.ai.trade.engine.data.entity.dto.smc.SmcStructureDTO;
import com.chain.ai.trade.engine.data.entity.dto.smc.SmcStructureDTO.OrderBlockDTO;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.ChaosExceptionEvaluator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.PositionRatioCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.WaveIndexCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SMC 结构数据服务 — 多周期信号评估与计算器编排
 * <p>提供单周期结构快照 + 多周期聚合数据，供行情看板和权重引擎使用</p>
 */
@Slf4j
@Service
public class SmcStructureService {

    private static final int BAR_COUNT = 500;
    private static final int FLIP_LOOKBACK = 20;

    @Autowired
    private ICandlestickService candlestickService;

    // ==================== 单周期结构快照 ====================

    /**
     * 获取单周期 SMC 结构快照（含所有扩展计算字段）
     */
    public SmcStructureDTO getStructure(String symbol, CandlestickIntervalEnum interval, long signalTimeMs) {
        // 1. 加载 K 线
        List<Candlestick> klines = loadKlines(symbol, interval, BAR_COUNT);
        if (klines == null || klines.size() < 80) {
            log.warn("SMC结构数据不足: symbol={}, period={}, size={}", symbol, interval, klines == null ? 0 : klines.size());
            return null;
        }

        // 2. 构建 BarSeries + Indicator
        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        SmartMoneyConceptsIndicator.Config config = buildConfig();
        SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(series, config, null, null, null);
        int lastIdx = series.getBarCount() - 1;

        // 3. 获取当前 Bar + Result
        Bar currentBar = series.getBar(lastIdx);
        SmartMoneyConceptsIndicator.Result result = indicator.getValue(lastIdx);

        // 4. 构建 barResults 列表（供计算器遍历）
        List<SmartMoneyConceptsIndicator.Result> resultList = buildResultList(indicator, lastIdx);

        // 5. 执行扩展计算
        int waveIndex = WaveIndexCalculator.calculate(resultList, lastIdx, result.getSwingTrend() >= 0);
        String wavePhase = WaveIndexCalculator.getWavePhase(waveIndex, result.getSwingTrend() >= 0);
        int flipCount = WaveIndexCalculator.calculateFlipCount(resultList, lastIdx, FLIP_LOOKBACK);
        int structureAge = WaveIndexCalculator.calculateStructureAge(resultList, lastIdx);

        double currentPrice = currentBar.getClosePrice().doubleValue();
        boolean isBuy = result.getSwingTrend() >= 0;
        double positionRatio = PositionRatioCalculator.calculate(result, isBuy, currentPrice);

        // 6. 组装 DTO
        SmcStructureDTO dto = copyResultFields(result);
        dto.setSymbol(symbol);
        dto.setPeriod(interval.getCode());
        dto.setTimestamp(currentBar.getBeginTime().toEpochMilli());

        dto.setWaveIndex(waveIndex);
        dto.setWavePhase(wavePhase);
        dto.setPositionRatio(positionRatio);
        dto.setStructureAge(structureAge);
        dto.setFlipCount(flipCount);

        return dto;
    }

    // ==================== 多周期聚合（权重引擎用） ====================

    /**
     * 获取多周期聚合数据（4H / 1H / 15M）— 专供权重引擎
     */
    public MultiPeriodSmcData getMultiPeriodForWeightEngine(String symbol) {
        MultiPeriodSmcData data = new MultiPeriodSmcData();

        // 4H
        SmartMoneyConceptsIndicator.Result r4h = computeSingle(symbol, CandlestickIntervalEnum.OKX4HOUR);
        if (r4h != null) {
            List<SmartMoneyConceptsIndicator.Result> list4h = buildResultListForInterval(symbol, CandlestickIntervalEnum.OKX4HOUR);
            if (!list4h.isEmpty()) {
                int last = list4h.size() - 1;
                data.setWaveIndex4h(WaveIndexCalculator.calculate(list4h, last, r4h.getSwingTrend() >= 0));
                data.setWavePhase4h(WaveIndexCalculator.getWavePhase(data.getWaveIndex4h(), r4h.getSwingTrend() >= 0));
                data.setFlipCount4h(WaveIndexCalculator.calculateFlipCount(list4h, last, FLIP_LOOKBACK));
                data.setStructureAge4h(WaveIndexCalculator.calculateStructureAge(list4h, last));
            }
            data.setSwingTrend4h(r4h.getSwingTrend());
            data.setSwingBullishBOS4h(r4h.isSwingBullishBOS());
            data.setSwingBearishBOS4h(r4h.isSwingBearishBOS());
            data.setSwingBullishCHOCH4h(r4h.isSwingBullishCHOCH());
            data.setSwingBearishCHOCH4h(r4h.isSwingBearishCHOCH());
        }

        // 1H
        SmartMoneyConceptsIndicator.Result r1h = computeSingle(symbol, CandlestickIntervalEnum.OKXMIN60);
        if (r1h != null) {
            List<SmartMoneyConceptsIndicator.Result> list1h = buildResultListForInterval(symbol, CandlestickIntervalEnum.OKXMIN60);
            double price1h = getCurrentPrice(symbol, CandlestickIntervalEnum.OKXMIN60);
            data.setPositionRatio1h(PositionRatioCalculator.calculate(r1h, r1h.getSwingTrend() >= 0, price1h));
            data.setStructureAge1h(WaveIndexCalculator.calculateStructureAge(list1h, list1h.size() - 1));
            data.setSwingTrend1h(r1h.getSwingTrend());
            data.setSwingBullishBOS1h(r1h.isSwingBullishBOS());
            data.setSwingBearishBOS1h(r1h.isSwingBearishBOS());
            data.setSwingBullishCHOCH1h(r1h.isSwingBullishCHOCH());
            data.setSwingBearishCHOCH1h(r1h.isSwingBearishCHOCH());
        }

        // 15M
        SmartMoneyConceptsIndicator.Result r15m = computeSingle(symbol, CandlestickIntervalEnum.OKXMIN15);
        if (r15m != null) {
            List<SmartMoneyConceptsIndicator.Result> list15m = buildResultListForInterval(symbol, CandlestickIntervalEnum.OKXMIN15);
            double price15m = getCurrentPrice(symbol, CandlestickIntervalEnum.OKXMIN15);
            data.setPositionRatio15m(PositionRatioCalculator.calculate(r15m, r15m.getSwingTrend() >= 0, price15m));
            data.setStructureAge15m(WaveIndexCalculator.calculateStructureAge(list15m, list15m.size() - 1));
            data.setSwingTrend15m(r15m.getSwingTrend());
            data.setSwingBullishBOS15m(r15m.isSwingBullishBOS());
            data.setSwingBearishBOS15m(r15m.isSwingBearishBOS());
        }

        // 综合评分 + 建议乘数 + 阶段说明
        double score = calculateCompositeScore(data);
        data.setCompositeScore(score);
        data.setSuggestedMultiplier(resolveSuggestedMultiplier(score));
        data.setPhaseDescription(resolvePhaseDescription(data, score));

        data.setTimestamp(System.currentTimeMillis());
        return data;
    }

    /**
     * 计算综合评分（0~5），基于趋势强度、位置比、结构年龄、盈亏比等因素
     */
    private double calculateCompositeScore(MultiPeriodSmcData data) {
        double score = 2.5; // 基础分

        // 4H 趋势方向加分
        if (data.getSwingTrend4h() != 0) {
            score += data.getSwingTrend4h() > 0 ? 0.5 : 0.5;
        }

        // 波次深度加分：离 0 越远说明趋势越成熟
        int waveAbs = Math.abs(data.getWaveIndex4h());
        if (waveAbs >= 3) score += 0.3;
        else if (waveAbs >= 2) score += 0.2;
        else if (waveAbs >= 1) score += 0.1;

        // 位置比：越接近边界越好
        double pr1h = data.getPositionRatio1h();
        if (!Double.isNaN(pr1h)) {
            if (pr1h >= 0.7) score += 0.4;      // 阻力/支撑附近
            else if (pr1h >= 0.5) score += 0.2;
        }
        double pr15m = data.getPositionRatio15m();
        if (!Double.isNaN(pr15m)) {
            if (pr15m >= 0.7) score += 0.3;
            else if (pr15m >= 0.5) score += 0.15;
        }

        // 盈亏比加分
        if (data.getRiskRewardRatio() >= 2.0) score += 0.5;
        else if (data.getRiskRewardRatio() >= 1.5) score += 0.3;
        else if (data.getRiskRewardRatio() >= 1.0) score += 0.1;

        // 结构年龄：新鲜结构加分
        if (data.getStructureAge1h() <= 3) score += 0.2;

        // 翻转次数：低翻转说明趋势流畅
        if (data.getFlipCount4h() <= 2) score += 0.2;

        // 混沌特例减分
        if (data.isChaosException()) score -= 0.5;

        return Math.max(0, Math.min(5, score));
    }

    /**
     * 根据评分映射建议乘数
     */
    private double resolveSuggestedMultiplier(double score) {
        if (score >= 4.0) return 2.0;   // 重仓
        if (score >= 3.0) return 1.5;   // 偏重
        if (score >= 2.0) return 1.0;   // 正常
        if (score >= 1.0) return 0.5;   // 轻仓
        return 0.0;                     // 不交易
    }

    /**
     * 生成阶段说明文字
     */
    private String resolvePhaseDescription(MultiPeriodSmcData data, double score) {
        if (data.isChaosException()) return "混沌区·观望";

        // 波次判断
        int wi = data.getWaveIndex4h();
        String phase = data.getWavePhase4h();
        boolean bearish = data.getSwingTrend4h() < 0;

        if (wi >= 4 || wi <= -4) {
            return "衰竭段·减仓区";
        }
        if (wi >= 2 || wi <= -2) {
            return score >= 3.0 ? "加速段·重仓区" : "加速段·轻仓区";
        }
        if (wi >= 1 || wi <= -1) {
            return "启动段·布局区";
        }
        return "初始段·观察区";
    }

    /**
     * 设置混沌特例和盈亏比字段（由上层在获取完整信号后调用）
     */
    public MultiPeriodSmcData enrichWithRiskData(MultiPeriodSmcData data,
                                                  double riskRewardRatio,
                                                  double riskPercent,
                                                  int waveIndex,
                                                  int flipCount) {
        data.setRiskRewardRatio(riskRewardRatio);
        data.setRiskPercent(riskPercent);

        ChaosExceptionResult chaos = ChaosExceptionEvaluator.evaluate(waveIndex, riskRewardRatio, riskPercent, flipCount);
        data.setChaosException(chaos.isTriggered());
        data.setChaosForcedMultiplier(chaos.getForcedMultiplier());

        return data;
    }

    // ==================== 内部方法 ====================

    private SmartMoneyConceptsIndicator.Result computeSingle(String symbol, CandlestickIntervalEnum interval) {
        List<Candlestick> klines = loadKlines(symbol, interval, BAR_COUNT);
        if (klines == null || klines.size() < 80) return null;

        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(series, buildConfig(), null, null, null);
        return indicator.getValue(series.getBarCount() - 1);
    }

    private List<SmartMoneyConceptsIndicator.Result> buildResultListForInterval(String symbol, CandlestickIntervalEnum interval) {
        List<Candlestick> klines = loadKlines(symbol, interval, BAR_COUNT);
        if (klines == null || klines.size() < 80) return List.of();

        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(series, buildConfig(), null, null, null);
        return buildResultList(indicator, series.getBarCount() - 1);
    }

    private List<SmartMoneyConceptsIndicator.Result> buildResultList(SmartMoneyConceptsIndicator indicator, int lastIdx) {
        List<SmartMoneyConceptsIndicator.Result> list = new ArrayList<>(lastIdx + 1);
        for (int i = 0; i <= lastIdx; i++) {
            list.add(indicator.getValue(i));
        }
        return list;
    }

    private double getCurrentPrice(String symbol, CandlestickIntervalEnum interval) {
        List<Candlestick> klines = loadKlines(symbol, interval, 3);
        if (klines == null || klines.isEmpty()) return Double.NaN;
        Candlestick last = klines.get(klines.size() - 1);
        return last.getClosePrice().doubleValue();
    }

    private List<Candlestick> loadKlines(String symbol, CandlestickIntervalEnum interval, int limit) {
        KlineParam param = KlineParam.builder()
                .symbol(symbol)
                .klineInterval(interval)
                .size(limit)
                .build();
        return candlestickService.getLastKlines(param);
    }

    private SmartMoneyConceptsIndicator.Config buildConfig() {
        SmartMoneyConceptsIndicator.Config config = new SmartMoneyConceptsIndicator.Config();
        config.setSwingsLength(50);
        config.setShowInternalOrderBlocks(true);
        config.setShowSwingOrderBlocks(true);
        config.setInternalOrderBlocksCount(5);
        config.setSwingOrderBlocksCount(5);
        config.setOrderBlockFilter("Atr");
        config.setOrderBlockMitigation("High/Low");
        config.setShowEqualHighsLows(true);
        config.setEqualHighsLowsLength(3);
        config.setEqualHighsLowsThreshold(0.1);
        config.setShowFairValueGaps(true);
        config.setFairValueGapsAutoThreshold(true);
        config.setShowDailyLevels(false);
        config.setShowWeeklyLevels(false);
        config.setShowMonthlyLevels(false);
        config.setShowPremiumDiscountZones(true);
        return config;
    }

    private SmcStructureDTO copyResultFields(SmartMoneyConceptsIndicator.Result r) {
        SmcStructureDTO dto = new SmcStructureDTO();

        // 趋势
        dto.setSwingTrend(r.getSwingTrend());
        dto.setInternalTrend(r.getInternalTrend());

        // 摆动点
        dto.setLastSwingHigh(r.getLastSwingHigh());
        dto.setLastSwingLow(r.getLastSwingLow());
        dto.setPrevSwingHigh(r.getPrevSwingHigh());
        dto.setPrevSwingLow(r.getPrevSwingLow());
        dto.setLastHigherLow(r.getLastHigherLow());
        dto.setLastLowerHigh(r.getLastLowerHigh());

        // 结构信号
        dto.setSwingBullishBOS(r.isSwingBullishBOS());
        dto.setSwingBearishBOS(r.isSwingBearishBOS());
        dto.setSwingBullishCHOCH(r.isSwingBullishCHOCH());
        dto.setSwingBearishCHOCH(r.isSwingBearishCHOCH());
        dto.setInternalBullishBOS(r.isInternalBullishBOS());
        dto.setInternalBearishBOS(r.isInternalBearishBOS());
        dto.setInternalBullishCHOCH(r.isInternalBullishCHOCH());
        dto.setInternalBearishCHOCH(r.isInternalBearishCHOCH());

        // 事件类型
        dto.setLastSwingEventType(r.getLastSwingEventType());
        dto.setLastInternalEventType(r.getLastInternalEventType());

        // 订单块突破
        dto.setSwingBullishOrderBlockBreak(r.isSwingBullishOrderBlockBreak());
        dto.setSwingBearishOrderBlockBreak(r.isSwingBearishOrderBlockBreak());
        dto.setInternalBullishOrderBlockBreak(r.isInternalBullishOrderBlockBreak());
        dto.setInternalBearishOrderBlockBreak(r.isInternalBearishOrderBlockBreak());

        // EQH/EQL
        dto.setEqualHighs(r.isEqualHighs());
        dto.setEqualLows(r.isEqualLows());

        // FVG
        dto.setBullishFairValueGap(r.isBullishFairValueGap());
        dto.setBearishFairValueGap(r.isBearishFairValueGap());
        dto.setBullishFVGBroken(r.isBullishFVGBroken());
        dto.setBearishFVGBroken(r.isBearishFVGBroken());
        dto.setLastBullishFVGTop(r.getLastBullishFVGTop());
        dto.setLastBullishFVGBottom(r.getLastBullishFVGBottom());
        dto.setLastBearishFVGTop(r.getLastBearishFVGTop());
        dto.setLastBearishFVGBottom(r.getLastBearishFVGBottom());

        // 溢价/折扣区域
        dto.setPremiumZoneTop(r.getPremiumZoneTop());
        dto.setPremiumZoneBottom(r.getPremiumZoneBottom());
        dto.setDiscountZoneTop(r.getDiscountZoneTop());
        dto.setDiscountZoneBottom(r.getDiscountZoneBottom());
        dto.setEquilibriumCenter(r.getEquilibriumCenter());
        dto.setCurrentZone(r.getCurrentZone());

        // 强弱高低点
        dto.setStrongHigh(r.getStrongHigh());
        dto.setStrongLow(r.getStrongLow());
        dto.setWeakHigh(r.getWeakHigh());
        dto.setWeakLow(r.getWeakLow());

        // 波段跟踪
        dto.setTrailingHigh(r.getTrailingHigh());
        dto.setTrailingLow(r.getTrailingLow());
        dto.setTrailingHighTime(r.getTrailingHighTime());
        dto.setTrailingLowTime(r.getTrailingLowTime());

        // 蜡烛颜色
        dto.setCandleColor(r.getCandleColor());

        // MTF水平
        dto.setDailyHigh(r.getDailyHigh());
        dto.setDailyLow(r.getDailyLow());
        dto.setWeeklyHigh(r.getWeeklyHigh());
        dto.setWeeklyLow(r.getWeeklyLow());
        dto.setMonthlyHigh(r.getMonthlyHigh());
        dto.setMonthlyLow(r.getMonthlyLow());

        // pivot映射
        dto.setPivotTimestamps(r.getPivotTimestamps());
        dto.setPivotLevels(r.getPivotLevels());

        // 订单块
        dto.setSwingOrderBlocks(toOrderBlockDTOs(r.getSwingOrderBlocks()));
        dto.setInternalOrderBlocks(toOrderBlockDTOs(r.getInternalOrderBlocks()));

        return dto;
    }

    private List<OrderBlockDTO> toOrderBlockDTOs(List<SmartMoneyConceptsIndicator.OrderBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return List.of();
        return blocks.stream().map(b -> {
            OrderBlockDTO dto = new OrderBlockDTO();
            dto.setHigh(b.barHigh);
            dto.setLow(b.barLow);
            dto.setTime(b.barTime);
            dto.setBias(b.bias);
            return dto;
        }).collect(Collectors.toList());
    }
}
