package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.dto.SmcMultiPeriodResponse;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.dos.SmcBarResult;
import com.chain.ai.trade.engine.data.entity.dos.SmcOrderBlock;
import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.engine.data.entity.dto.smc.GlobalMetrics;
import com.chain.ai.trade.engine.data.entity.dto.smc.MultiPeriodSmcData;
import com.chain.ai.trade.engine.data.entity.dto.smc.PeriodData;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.strategy.IndicatorStrategyRouter;
import com.chain.ai.trade.extension.strategy.IndicatorCriticalLevelsStrategy;
import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.common.entity.constants.TrendType;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.SmcCriticalLevelsCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.ChaosExceptionEvaluator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.PositionRatioCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.WaveIndexCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.trend.SmcTrendUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.*;

@Slf4j
@Service
public class SmcMultiPeriodService {

    @Autowired
    private ICandlestickService candlestickService;

    @Autowired
    private IndicatorStrategyRouter indicatorStrategyRouter;

    private final ConcurrentHashMap<String, SmcMultiPeriodResponse> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "smc-multi-period-refresh");
        t.setDaemon(true);
        return t;
    });

    private static final int BAR_COUNT = 1000;
    private static final int ATR_PERIOD = 14;

    private static final List<PeriodConfig> PERIODS = List.of(
        new PeriodConfig("3M", CandlestickIntervalEnum.OKXMIN3),
        new PeriodConfig("15M", CandlestickIntervalEnum.OKXMIN15),
        new PeriodConfig("1H", CandlestickIntervalEnum.OKXMIN60),
        new PeriodConfig("4H", CandlestickIntervalEnum.OKX4HOUR),
        new PeriodConfig("1D", CandlestickIntervalEnum.OKX1D)
    );

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::refreshAllCached, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
    }

    public SmcMultiPeriodResponse getCached(String symbol) {
        return cache.computeIfAbsent(symbol, this::compute);
    }

    public void evict(String symbol) {
        cache.remove(symbol);
    }

    private void refreshAllCached() {
        for (String symbol : new ArrayList<>(cache.keySet())) {
            try {
                cache.put(symbol, compute(symbol));
            } catch (Exception e) {
                log.error("刷新灵猞AI多维引擎缓存失败: symbol={}", symbol, e);
            }
        }
    }

    public static class MultiPeriodResult {
        private Map<String, SmartMoneyConceptsIndicator.Result> results;
        private double currentPrice;
        private Map<String, SmcBarResult> barResults;
        private Map<String, List<SmartMoneyConceptsIndicator.Result>> historyResults;  // ★ 新增：历史序列

        public Map<String, SmartMoneyConceptsIndicator.Result> getResults() { return results; }
        public void setResults(Map<String, SmartMoneyConceptsIndicator.Result> results) { this.results = results; }
        public double getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
        public Map<String, SmcBarResult> getBarResults() { return barResults; }
        public void setBarResults(Map<String, SmcBarResult> barResults) { this.barResults = barResults; }

        public Map<String, List<SmartMoneyConceptsIndicator.Result>> getHistoryResults() {
            return historyResults;
        }

        public void setHistoryResults(Map<String, List<SmartMoneyConceptsIndicator.Result>> historyResults) {
            this.historyResults = historyResults;
        }
    }

    public MultiPeriodResult computeMultiPeriod(String symbol,
                                                BiFunction<String, CandlestickIntervalEnum, List<Candlestick>> klinesLoader) {

        Map<String, SmartMoneyConceptsIndicator.Result> latestResults = new LinkedHashMap<>();
        Map<String, List<SmartMoneyConceptsIndicator.Result>> historyResults = new LinkedHashMap<>();  // ★ 新增
        Map<String, SmcBarResult> barResults = new LinkedHashMap<>();
        BarSeries series4h = null;

        for (PeriodConfig pc : PERIODS) {
            try {
                List<Candlestick> klines = klinesLoader.apply(symbol, pc.interval);
                if (klines == null || klines.size() < 80) {
                    log.warn("{} {} 数据不足: {}", symbol, pc.label, klines == null ? 0 : klines.size());
                    continue;
                }
                BarSeries series = IndicatorWrapHelper.buildSeries(klines);
                SmartMoneyConceptsIndicator.Config config = buildConfig();
                SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(series, config, null, null, null);

                // ★ 关键改动：保存整个历史序列，不只是最后一个
                int totalBars = series.getBarCount();
                List<SmartMoneyConceptsIndicator.Result> resultList = new ArrayList<>(totalBars);
                for (int i = 0; i < totalBars; i++) {
                    resultList.add(indicator.getValue(i));
                }
                historyResults.put(pc.label, resultList);

                int last = totalBars - 1;
                SmartMoneyConceptsIndicator.Result r = resultList.get(last);
                latestResults.put(pc.label, r);
                barResults.put(pc.label, buildBarResult(series.getBar(last), r, pc.label));

                if ("4H".equals(pc.label)) {
                    series4h = series;
                }
            } catch (Exception e) {
                log.error("计算 {} {} SMC指标失败", symbol, pc.label, e);
            }
        }

        double currentPrice = series4h != null
                ? series4h.getBar(series4h.getBarCount() - 1).getClosePrice().doubleValue()
                : Double.NaN;

        MultiPeriodResult result = new MultiPeriodResult();
        result.setResults(latestResults);
        result.setHistoryResults(historyResults);  // ★ 新增
        result.setCurrentPrice(currentPrice);
        result.setBarResults(barResults);
        return result;
    }

    public List<CriticalLevel> buildCriticalLevelsByDirection(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice) {
        return SmcCriticalLevelsCalculator.buildByDirection(results, direction, currentPrice, "ALL");
    }

    private SmcMultiPeriodResponse compute(String symbol) {
        MultiPeriodResult mpResult = computeMultiPeriod(symbol,
                (sym, interval) -> loadLatestKlines(sym, interval, BAR_COUNT));

        String trendState = resolveTrendState(mpResult.getResults().get("4H"), mpResult.getCurrentPrice());

        SmcMultiPeriodResponse response = new SmcMultiPeriodResponse();
        response.setSymbol(symbol);
        response.setMatrix(buildMatrix(mpResult.getResults()));
        response.setCore(buildCoreData(mpResult.getResults(), trendState));
        response.setCriticalLevels(buildCriticalLevels(mpResult.getResults(), trendState, mpResult.getCurrentPrice()));
        response.getCore().setCompositeState(resolveCompositeState(mpResult.getResults(), mpResult.getCurrentPrice()));

        // ★ 新增：构建结构评估数据（波次、位置比、混沌特例等）
        MultiPeriodSmcData structureData = buildStructureData(symbol, mpResult);
        response.setStructureData(structureData);

        return response;
    }

    /**
     * 从已计算的 SMC 历史结果中构建结构评估数据
     * ★ 只使用 mpResult 中的现有数据，不重新拉取 K 线
     */
    private MultiPeriodSmcData buildStructureData(String symbol, MultiPeriodResult mpResult) {
        MultiPeriodSmcData data = new MultiPeriodSmcData();

        // 1. 提取各周期的历史 Result 列表
        List<SmartMoneyConceptsIndicator.Result> list4h = mpResult.getHistoryResults().get("4H");
        List<SmartMoneyConceptsIndicator.Result> list1h = mpResult.getHistoryResults().get("1H");
        List<SmartMoneyConceptsIndicator.Result> list15m = mpResult.getHistoryResults().get("15M");

        if (list4h == null || list4h.isEmpty()) {
            return data; // 数据不足，返回空
        }

        // 2. 计算 4H 层数据（战略层）
        int lastIdx4h = list4h.size() - 1;
        SmartMoneyConceptsIndicator.Result r4h = list4h.get(lastIdx4h);

        // ★ 注意：波次计算需要知道方向（isBuy），这里我们根据 swingTrend 自动推断：
        // swingTrend == 1 → 多头（isBuy=true），swingTrend == -1 → 空头（isBuy=false）
        int swingTrend4h = r4h.getSwingTrend();
        boolean isBuy4h = swingTrend4h == 1;

        int waveIndex = WaveIndexCalculator.calculate(list4h, lastIdx4h, isBuy4h);
        int age4h = WaveIndexCalculator.calculateStructureAge(list4h, lastIdx4h);
        data.setFlipCount4h(WaveIndexCalculator.calculateFlipCount(list4h, lastIdx4h, 20));

        // 4H 位置比
        double currentPrice = mpResult.getCurrentPrice();
        double positionRatio4h = PositionRatioCalculator.calculate(r4h, isBuy4h, currentPrice);

        // ★ PeriodData for 4H
        data.getPeriods().put("4H", PeriodData.builder()
                .swingTrend(swingTrend4h)
                .waveIndex(waveIndex)
                .wavePhase(WaveIndexCalculator.getWavePhase(waveIndex, isBuy4h))
                .positionRatio(positionRatio4h)
                .structureAge(age4h)
                .bullishBOS(r4h.isSwingBullishBOS())
                .bearishBOS(r4h.isSwingBearishBOS())
                .bullishCHOCH(r4h.isSwingBullishCHOCH())
                .bearishCHOCH(r4h.isSwingBearishCHOCH())
                .build());

        // 3. 计算 1H 层数据（战术层）
        if (list1h != null && !list1h.isEmpty()) {
            int lastIdx1h = list1h.size() - 1;
            SmartMoneyConceptsIndicator.Result r1h = list1h.get(lastIdx1h);

            // 位置比（自动适配方向）
            double positionRatio1h = PositionRatioCalculator.calculate(r1h, isBuy4h, currentPrice);
            // 结构年龄
            int age1h = WaveIndexCalculator.calculateStructureAge(list1h, lastIdx1h);
            // 波次（使用 1H 自身方向）
            boolean isBuy1h = r1h.getSwingTrend() == 1;
            int waveIndex1h = WaveIndexCalculator.calculate(list1h, lastIdx1h, isBuy1h);

            // ★ PeriodData for 1H
            data.getPeriods().put("1H", PeriodData.builder()
                    .swingTrend(r1h.getSwingTrend())
                    .waveIndex(waveIndex1h)
                    .wavePhase(WaveIndexCalculator.getWavePhase(waveIndex1h, isBuy1h))
                    .positionRatio(positionRatio1h)
                    .structureAge(age1h)
                    .bullishBOS(r1h.isSwingBullishBOS())
                    .bearishBOS(r1h.isSwingBearishBOS())
                    .bullishCHOCH(r1h.isSwingBullishCHOCH())
                    .bearishCHOCH(r1h.isSwingBearishCHOCH())
                    .build());
        }

        // 4. 计算 15M 层数据（执行层）
        if (list15m != null && !list15m.isEmpty()) {
            int lastIdx15m = list15m.size() - 1;
            SmartMoneyConceptsIndicator.Result r15m = list15m.get(lastIdx15m);

            double positionRatio15m = PositionRatioCalculator.calculate(r15m, isBuy4h, currentPrice);
            int age15m = WaveIndexCalculator.calculateStructureAge(list15m, lastIdx15m);
            // 波次（使用 15M 自身方向）
            boolean isBuy15m = r15m.getSwingTrend() == 1;
            int waveIndex15m = WaveIndexCalculator.calculate(list15m, lastIdx15m, isBuy15m);

            // ★ PeriodData for 15M
            data.getPeriods().put("15M", PeriodData.builder()
                    .swingTrend(r15m.getSwingTrend())
                    .waveIndex(waveIndex15m)
                    .wavePhase(WaveIndexCalculator.getWavePhase(waveIndex15m, isBuy15m))
                    .positionRatio(positionRatio15m)
                    .structureAge(age15m)
                    .bullishBOS(r15m.isSwingBullishBOS())
                    .bearishBOS(r15m.isSwingBearishBOS())
                    .build());
        }

        // 5. 构建全局评估指标
        // 混沌特例判定
        boolean chaos = ChaosExceptionEvaluator.evaluate(
                waveIndex,
                0.0,  // riskRewardRatio（简化版，实际计算后填入）
                0.0,  // riskPercent（简化版，实际计算后填入）
                data.getFlipCount4h()
        ).isTriggered();

        data.setGlobal(GlobalMetrics.builder()
                .riskRewardRatio(0.0)
                .riskPercent(0.0)
                .chaosException(chaos)
                .chaosForcedMultiplier(chaos ? 0.2 : 0.0)
                .build());

        // 新架构 → 旧字段同步
        data.syncToLegacyFields();

        // ★ 生成阶段说明（基于多周期联动关系）
        String phaseDescription = generatePhaseDescription(data);
        data.setPhaseDescription(phaseDescription);

        return data;
    }

    /**
     * 生成阶段说明（不依赖权重规则引擎）
     * 基于多周期结构联动逻辑生成纯文本建议
     */
    private String generatePhaseDescription(MultiPeriodSmcData data) {

        // ========== 提取关键变量（增加空值保护） ==========
        int wave4h = data.getWaveIndex4h();
        int age4h = data.getStructureAge4h();
        int trend4h = data.getSwingTrend4h();
        boolean isBullish4h = trend4h == 1;
        boolean isBearish4h = trend4h == -1;

        double posRatio1h = data.getPositionRatio1h();
        int age1h = data.getStructureAge1h();
        int trend1h = data.getSwingTrend1h();
        boolean isBullish1h = trend1h == 1;
        boolean isBearish1h = trend1h == -1;

        double posRatio15m = data.getPositionRatio15m();
        int age15m = data.getStructureAge15m();
        boolean internalBullish15m = data.isSwingBullishBOS15m();
        boolean internalBearish15m = data.isSwingBearishBOS15m();

        boolean is4hAged = age4h > 30;
        boolean is1hAged = age1h > 100;
        boolean is15mAged = age15m > 200;

        boolean inSupport1h = posRatio1h < 0.382;
        boolean inResistance1h = posRatio1h > 0.618;

        // ========== 第1优先级：硬性否决条件 ==========

        // 1H阻力区追高
        if (inResistance1h && isBullish4h) {
            return "4H多头方向，但1H已到阻力区(" + String.format("%.2f", posRatio1h) + ")，追高风险，等待回调至支撑区";
        }

        // 4H试盘但年龄过大（横盘太久未突破）
        if (wave4h == 1 && is4hAged) {
            return "4H试盘已横盘" + age4h + "根未突破，趋势未延续，观望等待突破或下破";
        }

        // 1H结构老化 + 在阻力区
        if (is1hAged && inResistance1h) {
            return "1H年龄" + age1h + "根结构老化，且价格在阻力区，支撑/阻力效力减弱，谨慎操作";
        }

        // ========== 第2优先级：共振机会 ==========

        // 黄金共振：4H确认/加速 + 1H支撑区
        boolean is4hConfirmOrAccel = (wave4h == 2 || wave4h == 3 || wave4h == -2 || wave4h == -3);
        if (is4hConfirmOrAccel && inSupport1h) {
            String phase = WaveIndexCalculator.getWavePhase(wave4h, isBullish4h);
            return "4H" + phase + "段，1H在支撑区(" + String.format("%.2f", posRatio1h) + ")，黄金共振机会，关注入场信号";
        }

        // 4H+1H方向共振 + 1H支撑区
        if (isBullish4h && isBullish1h && inSupport1h) {
            return "4H+1H共振多头，1H在支撑区(" + String.format("%.2f", posRatio1h) + ")，等待企稳后入场";
        }
        if (isBearish4h && isBearish1h && inResistance1h) {
            return "4H+1H共振空头，1H在阻力区(" + String.format("%.2f", posRatio1h) + ")，等待反弹衰竭后入场";
        }

        // ========== 第3优先级：回调/蓄力状态 ==========

        // 4H多头 + 1H空头（标准回调）
        if (isBullish4h && isBearish1h && inSupport1h) {
            return "4H多头方向，1H回调至支撑区(" + String.format("%.2f", posRatio1h) + ")，关注1H是否企稳";
        }
        // 4H空头 + 1H多头（标准反弹）
        if (isBearish4h && isBullish1h && inResistance1h) {
            return "4H空头方向，1H反弹至阻力区(" + String.format("%.2f", posRatio1h) + ")，关注1H是否见顶";
        }

        // 4H试盘 + 1H支撑区
        if (wave4h == 1 && inSupport1h) {
            return "4H试盘期，1H已回调至支撑区(" + String.format("%.2f", posRatio1h) + ")，关注支撑有效性，有效则轻仓试多";
        }

        // ========== 第4优先级：风险警示 ==========

        if (is1hAged) {
            return "1H年龄" + age1h + "根，结构老化信号，当前方向" + (isBullish1h ? "多头" : "空头") + "，建议观望";
        }

        if (is15mAged && !is1hAged) {
            return "微观(15M)年龄" + age15m + "根结构老化，当前" + (internalBullish15m ? "内部多头共振" : internalBearish15m ? "内部空头共振" : "无内部信号") + "，需警惕微观变盘";
        }

        // ========== 第5优先级：兜底 ==========

        return "多周期方向不一致或信号不明确，建议观望";
    }

    private List<Candlestick> loadLatestKlines(String symbol, CandlestickIntervalEnum interval, int limit) {
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

    private SmcBarResult buildBarResult(Bar bar, SmartMoneyConceptsIndicator.Result r, String label) {
        SmcBarResult dto = new SmcBarResult();
        dto.setTimestamp(bar.getBeginTime().toEpochMilli() - 8 * 60 * 60 * 1000L);
        dto.setOpen(bar.getOpenPrice().doubleValue());
        dto.setHigh(bar.getHighPrice().doubleValue());
        dto.setLow(bar.getLowPrice().doubleValue());
        dto.setClose(bar.getClosePrice().doubleValue());
        dto.setVolume(bar.getVolume().longValue());
        dto.setSwingTrend(r.getSwingTrend());
        dto.setInternalTrend(r.getInternalTrend());
        dto.setSwingBullishBOS(r.isSwingBullishBOS());
        dto.setSwingBearishBOS(r.isSwingBearishBOS());
        dto.setSwingBullishCHOCH(r.isSwingBullishCHOCH());
        dto.setSwingBearishCHOCH(r.isSwingBearishCHOCH());
        dto.setInternalBullishBOS(r.isInternalBullishBOS());
        dto.setInternalBearishBOS(r.isInternalBearishBOS());
        dto.setInternalBullishCHOCH(r.isInternalBullishCHOCH());
        dto.setInternalBearishCHOCH(r.isInternalBearishCHOCH());
        dto.setSwingOrderBlocks(toOrderBlocks(r.getSwingOrderBlocks()));
        dto.setInternalOrderBlocks(toOrderBlocks(r.getInternalOrderBlocks()));
        dto.setBullishFairValueGap(r.isBullishFairValueGap());
        dto.setBearishFairValueGap(r.isBearishFairValueGap());
        dto.setLastBullishFVGTop(r.getLastBullishFVGTop());
        dto.setLastBullishFVGBottom(r.getLastBullishFVGBottom());
        dto.setLastBearishFVGTop(r.getLastBearishFVGTop());
        dto.setLastBearishFVGBottom(r.getLastBearishFVGBottom());
        dto.setEqualHighs(r.isEqualHighs());
        dto.setEqualLows(r.isEqualLows());
        return dto;
    }

    private List<SmcOrderBlock> toOrderBlocks(List<SmartMoneyConceptsIndicator.OrderBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return List.of();
        return blocks.stream().map(b -> {
            SmcOrderBlock dto = new SmcOrderBlock();
            dto.setHigh(b.barHigh);
            dto.setLow(b.barLow);
            dto.setTime((long) b.barTime);
            dto.setBias(b.bias);
            return dto;
        }).collect(Collectors.toList());
    }

    private List<SmcMultiPeriodResponse.MatrixItem> buildMatrix(Map<String, SmartMoneyConceptsIndicator.Result> results) {
        List<SmcMultiPeriodResponse.MatrixItem> matrix = new ArrayList<>();
        for (PeriodConfig pc : PERIODS) {
            SmartMoneyConceptsIndicator.Result r = results.get(pc.label);
            String direction = "震荡";
            if (r != null) {
                direction = resolveDirection(r);
            }
            SmcMultiPeriodResponse.MatrixItem item = new SmcMultiPeriodResponse.MatrixItem();
            item.setPeriod(pc.label);
            item.setDirection(direction);
            matrix.add(item);
        }
        return matrix;
    }

    private String resolveDirection(SmartMoneyConceptsIndicator.Result r) {
        int swing = r.getSwingTrend();
        if (swing == 1) return "多头";
        if (swing == -1) return "空头";
        return "震荡";
    }

    private String resolveTrendState(SmartMoneyConceptsIndicator.Result r, double close) {
        if (r == null) return "完全震荡";
        TrendType type = SmcTrendUtils.resolve4hTrendState(
                r.getSwingTrend(), close,
                r.getLastSwingLow(), r.getLastHigherLow(),
                r.getLastSwingHigh(), r.getLastLowerHigh());
        return SmcTrendUtils.toChineseName(type);
    }

    /**
     * 解析21种复合状态（看板展示用，独立于关键点位计算）
     */
    private String resolveCompositeState(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            double currentPrice) {
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        SmartMoneyConceptsIndicator.Result r1h = results.get("1H");
        SmartMoneyConceptsIndicator.Result r15m = results.get("15M");
        if (r4h == null || r1h == null || r15m == null) {
            return null;
        }

        Map<CandlestickIntervalEnum, SmartMoneyConceptsIndicator.Result> map = new HashMap<>();
        map.put(OKX4HOUR, r4h);
        map.put(OKXMIN60, r1h);
        map.put(OKXMIN15, r15m);

        boolean priceBrokenHigherLow = !Double.isNaN(r1h.getLastHigherLow())
                && currentPrice < r1h.getLastHigherLow();
        boolean priceBrokenLowerHigh = !Double.isNaN(r1h.getLastLowerHigh())
                && currentPrice > r1h.getLastLowerHigh();

        CompositeState cs = SmcTrendUtils.getDetailedTrendState(
                map, currentPrice, priceBrokenHigherLow, priceBrokenLowerHigh);
        return compositeStateToChinese(cs);
    }

    /**
     * CompositeState → 中文名映射
     */
    private String compositeStateToChinese(CompositeState s) {
        switch (s) {
            case STRONG_BULLISH_HEALTHY:    return "强上升·健康";
            case STRONG_BULLISH_SHALLOW_PULLBACK:  return "强上升·浅回调";
            case STRONG_BULLISH_WARNING_1H: return "强上升·预警回调(1H)";
            case STRONG_BULLISH_WARNING_4H: return "强上升·预警回调(4H)";
            case STRONG_BULLISH_CONFIRMED_PULLBACK: return "强上升·确认回调";
            case BULLISH_PULLBACK_ONGOING:  return "上升回调·进行中";
            case BULLISH_PULLBACK_BOTTOMING: return "上升回调·筑底";
            case BULLISH_PULLBACK_FAILURE:  return "上升回调·失败";
            case BULLISH_ENDING_CONTINUE_DOWN: return "上升末端·延续下跌";
            case BULLISH_ENDING_CONFIRM:    return "上升末端·转势确认";
            case STRONG_BEARISH_HEALTHY:    return "强下降·健康";
            case STRONG_BEARISH_SHALLOW_BOUNCE: return "强下降·浅反弹";
            case STRONG_BEARISH_WARNING_1H: return "强下降·预警反弹(1H)";
            case STRONG_BEARISH_WARNING_4H: return "强下降·预警反弹(4H)";
            case STRONG_BEARISH_CONFIRMED_BOUNCE: return "强下降·确认反弹";
            case BEARISH_PULLBACK_ONGOING:  return "下降反弹·进行中";
            case BEARISH_PULLBACK_TOPPING:  return "下降反弹·筑顶";
            case BEARISH_PULLBACK_FAILURE:  return "下降反弹·失败";
            case BEARISH_ENDING_CONTINUE_UP: return "下降末端·延续反弹";
            case BEARISH_ENDING_CONFIRM:    return "下降末端·转势确认";
            case RANGING_NO_DIRECTION:      return "完全震荡";
            default: return "完全震荡";
        }
    }

    private SmcMultiPeriodResponse.CoreData buildCoreData(Map<String, SmartMoneyConceptsIndicator.Result> results, String trendState) {
        // 文档第4节: 核心数据生成 — 机构共振 + 市场格局 + 趋势状态
        SmcMultiPeriodResponse.CoreData core = new SmcMultiPeriodResponse.CoreData();

        int bullishCount = 0;
        int bearishCount = 0;
        int totalValid = 0;
        for (PeriodConfig pc : PERIODS) {
            SmartMoneyConceptsIndicator.Result r = results.get(pc.label);
            if (r == null) continue;
            totalValid++;
            String dir = resolveDirection(r);
            if ("多头".equals(dir)) bullishCount++;
            else if ("空头".equals(dir)) bearishCount++;
        }
        // 文档第4.2节: 机构共振 — 5个周期中>=4个同向为共振
        if (totalValid >= 4) {
            if (bullishCount >= 4 && bearishCount <= 1) {
                core.setInstitutionResonance("多方共振");
            } else if (bearishCount >= 4 && bullishCount <= 1) {
                core.setInstitutionResonance("空方共振");
            } else {
                core.setInstitutionResonance("无共振");
            }
        } else {
            core.setInstitutionResonance("数据不足");
        }

        // 文档第4.3节: 市场格局 — 基于4H swingTrend + 振幅判断趋势市/震荡市/高波动
        SmartMoneyConceptsIndicator.Result result4h = results.get("4H");
        if (result4h != null) {
            int swing4h = result4h.getSwingTrend();
            if (swing4h == 1 || swing4h == -1) {
                core.setMarketGenre("趋势市");
            } else {
                double high = result4h.getLastSwingHigh();
                double low = result4h.getLastSwingLow();
                if (!Double.isNaN(high) && !Double.isNaN(low) && low > 0) {
                    double range = (high - low) / low * 100;
                    if (range > 5.0) {
                        core.setMarketGenre("高波动");
                    } else {
                        core.setMarketGenre("震荡市");
                    }
                } else {
                    core.setMarketGenre("震荡市");
                }
            }
        } else {
            core.setMarketGenre("--");
        }

        core.setTrendState(trendState);
        core.setUpdateTime(new SimpleDateFormat("HH:mm:ss").format(new Date()));
        return core;
    }

    private List<CriticalLevel> buildCriticalLevels(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState,
            double currentPrice) {
        IndicatorCriticalLevelsStrategy strategy = indicatorStrategyRouter.resolve(null);
        String direction = strategy.resolveDirection(results, trendState);
        if (direction == null) {
            return List.of();
        }
        String entryObTypeFilter = strategy.resolveEntryObFilter(results, trendState);
        return SmcCriticalLevelsCalculator.buildByDirection(results, direction, currentPrice, entryObTypeFilter);
    }


    private static record PeriodConfig(String label, CandlestickIntervalEnum interval) {
    }
}
