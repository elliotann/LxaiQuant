package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.*;
import com.chain.ai.trade.engine.data.entity.dto.smc.MultiPeriodSmcData;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.service.smc.SmcStructureService;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import com.chain.ai.trade.engine.controller.dto.SmcMultiPeriodResponse;
import com.chain.ai.trade.engine.service.SmcMultiPeriodService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.*;

@RestController
@RequestMapping("/api/smc")
public class SmcController {
    @Autowired
    private ICandlestickService candlestickService;

    @Autowired
    private SmcMultiPeriodService smcMultiPeriodService;

    @Autowired
    private SmcStructureService smcStructureService;
    /**
     * 获取 Smart Money Concepts 指标计算结果
     * @param symbol 交易对
     * @param interval 时间周期，如 "1h", "4h", "1d"
     * @param from 起始时间戳（毫秒）
     * @param to 结束时间戳（毫秒）
     * @return SmcResponse
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSmc(@RequestParam String symbol,
                                                      @RequestParam String interval,
                                                      @RequestParam Long from,
                                                      @RequestParam Long to) {
        CandlestickIntervalEnum intervalEnum = parseInterval(interval);
        if (intervalEnum == null) {
            throw new IllegalArgumentException("无效的interval参数");
        }

        int maxCap = 6000;
        long stepMs = intervalEnum.getMinNum() != null ? intervalEnum.getMinNum().longValue() * 60_000L : 60L * 60_000L;
        int warmupBars = 1000;
        Long warmupFrom = from != null ? Math.max(0L, from - warmupBars * stepMs) : null;
        int take = computeLimit(intervalEnum, warmupFrom, to, maxCap);
        List<Candlestick> klines = loadKlines(symbol, intervalEnum, warmupFrom, to, take);
        BarSeries series = safeBuildSeries(klines);
        if (series == null || series.getBarCount() < 80) {
            SmcResponse response = new SmcResponse();
            response.setSymbol(symbol);
            response.setInterval(interval);
            response.setResults(List.of());
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            ok.put("data", response);
            return ResponseEntity.ok(ok);
        }

        // 2. 加载多时间框架数据（可选）
        BarSeries dailySeries = null;
        BarSeries weeklySeries = null;
        BarSeries monthlySeries = null;
        int dailyTake = computeLimit(OKX1D, from, to, maxCap);
        int weeklyTake = computeLimit(WEEK1, from, to, maxCap);
        int monthlyTake = computeLimit(MON1, from, to, maxCap);
        List<Candlestick> dailyKlines = loadKlines(symbol, OKX1D, from, to, dailyTake);
        List<Candlestick> weeklyKlines = loadKlines(symbol, WEEK1, from, to, weeklyTake);
        List<Candlestick> monthlyKlines = loadKlines(symbol, MON1, from, to, monthlyTake);
        dailySeries = safeBuildSeries(dailyKlines);
        weeklySeries = safeBuildSeries(weeklyKlines);
        monthlySeries = safeBuildSeries(monthlyKlines);

        // 3. 配置指标
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
        config.setShowDailyLevels(dailySeries != null);
        config.setShowWeeklyLevels(weeklySeries != null);
        config.setShowMonthlyLevels(monthlySeries != null);
        config.setShowPremiumDiscountZones(true);

        // 4. 计算指标
        SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(series, config, dailySeries, weeklySeries, monthlySeries);

        // 5. 组装结果
        List<SmcBarResult> results = new ArrayList<>();
        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            SmartMoneyConceptsIndicator.Result r = indicator.getValue(i);

            SmcBarResult dto = new SmcBarResult();
            dto.setTimestamp(bar.getBeginTime().toEpochMilli() - 8 * 60 * 60 * 1000L);
            dto.setOpen(bar.getOpenPrice().doubleValue());
            dto.setHigh(bar.getHighPrice().doubleValue());
            dto.setLow(bar.getLowPrice().doubleValue());
            dto.setClose(bar.getClosePrice().doubleValue());
            dto.setVolume(bar.getVolume().longValue());

            // 趋势
            dto.setInternalTrend(r.getInternalTrend());
            dto.setSwingTrend(r.getSwingTrend());

            // 结构信号
            dto.setInternalBullishBOS(r.isInternalBullishBOS());
            dto.setInternalBearishBOS(r.isInternalBearishBOS());
            dto.setInternalBullishCHOCH(r.isInternalBullishCHOCH());
            dto.setInternalBearishCHOCH(r.isInternalBearishCHOCH());
            dto.setSwingBullishBOS(r.isSwingBullishBOS());
            dto.setSwingBearishBOS(r.isSwingBearishBOS());
            dto.setSwingBullishCHOCH(r.isSwingBullishCHOCH());
            dto.setSwingBearishCHOCH(r.isSwingBearishCHOCH());

            // 订单块突破
            dto.setInternalBullishOrderBlockBreak(r.isInternalBullishOrderBlockBreak());
            dto.setInternalBearishOrderBlockBreak(r.isInternalBearishOrderBlockBreak());
            dto.setSwingBullishOrderBlockBreak(r.isSwingBullishOrderBlockBreak());
            dto.setSwingBearishOrderBlockBreak(r.isSwingBearishOrderBlockBreak());

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

            // MTF 水平
            dto.setDailyHigh(r.getDailyHigh());
            dto.setDailyLow(r.getDailyLow());
            dto.setWeeklyHigh(r.getWeeklyHigh());
            dto.setWeeklyLow(r.getWeeklyLow());
            dto.setMonthlyHigh(r.getMonthlyHigh());
            dto.setMonthlyLow(r.getMonthlyLow());

            // 溢价/折扣区域
            dto.setPremiumZoneTop(r.getPremiumZoneTop());
            dto.setPremiumZoneBottom(r.getPremiumZoneBottom());
            dto.setDiscountZoneTop(r.getDiscountZoneTop());
            dto.setDiscountZoneBottom(r.getDiscountZoneBottom());
            dto.setEquilibriumZoneTop(r.getEquilibriumZoneTop());
            dto.setEquilibriumZoneBottom(r.getEquilibriumZoneBottom());
            dto.setEquilibriumCenter(r.getEquilibriumCenter());
            dto.setCurrentZone(r.getCurrentZone());

            // 强弱高低点
            dto.setStrongHigh(r.getStrongHigh());
            dto.setWeakHigh(r.getWeakHigh());
            dto.setStrongLow(r.getStrongLow());
            dto.setWeakLow(r.getWeakLow());

            // 波段高低点
            dto.setTrailingHigh(r.getTrailingHigh());
            dto.setTrailingLow(r.getTrailingLow());
            dto.setTrailingHighTime(r.getTrailingHighTime());
            dto.setTrailingLowTime(r.getTrailingLowTime());

            // 蜡烛颜色
            dto.setCandleColor(r.getCandleColor());

            dto.setSwingOrderBlocks(toOrderBlocks(r.getSwingOrderBlocks()));
            dto.setInternalOrderBlocks(toOrderBlocks(r.getInternalOrderBlocks()));

            dto.setPivotTimestamps(r.getPivotTimestamps());
            dto.setPivotLevels(r.getPivotLevels());

            results.add(dto);
        }

        // 提取各周期最近2条 BOS/CHOCH 信号
        List<SmcBosChochSignal> signals = new ArrayList<>();
        Map<String, Integer> signalCount = new HashMap<>();
        String[] signalTypes = {
                "swingBullishBOS", "swingBearishBOS", "swingBullishCHOCH", "swingBearishCHOCH",
                "internalBullishBOS", "internalBearishBOS", "internalBullishCHOCH", "internalBearishCHOCH"
        };
        for (int i = results.size() - 1; i >= 0; i--) {
            SmcBarResult bar = results.get(i);
            for (String type : signalTypes) {
                if (signalCount.getOrDefault(type, 0) >= 2) continue;
                if (getBosChochField(bar, type)) {
                    Map<String, Long> pivotTs = bar.getPivotTimestamps();
                    Map<String, Double> pivotLs = bar.getPivotLevels();
                    long pivotTimestamp = pivotTs != null ? pivotTs.getOrDefault(type, bar.getTimestamp()) : bar.getTimestamp();
                    double price = pivotLs != null ? pivotLs.getOrDefault(type, bar.getClose()) : bar.getClose();
                    if (type.startsWith("swing")) {
                        System.out.println("[SMC CTRL] Swing signal: type=" + type + " price=" + price + " pivotTs=" + pivotTimestamp + " barTs=" + bar.getTimestamp() + " pivotLs hasKey=" + (pivotLs != null && pivotLs.containsKey(type)) + " close=" + bar.getClose());
                    }
                    signals.add(new SmcBosChochSignal(bar.getTimestamp(), price, type, pivotTimestamp));
                    signalCount.merge(type, 1, Integer::sum);
                }
            }
            if (signalCount.size() == signalTypes.length && signalCount.values().stream().allMatch(c -> c >= 2)) break;
        }

        // 收集摆动点标签（HH/HL/LL/LH）
        List<SmcSwingPoint> swingPoints = indicator.getSwingLabels().stream()
            .map(sl -> new SmcSwingPoint(sl.barTime, sl.price, sl.label))
            .collect(Collectors.toList());

        SmcResponse response = new SmcResponse();
        response.setSymbol(symbol);
        response.setInterval(interval);
        response.setBosChochSignals(signals);
        response.setSwingPoints(swingPoints);
        // 只返回最新2条
        int fromIndex = Math.max(0, results.size() - 2);
        response.setResults(results.subList(fromIndex, results.size()));

        Map<String, Object> ok = new HashMap<>();
        ok.put("success", true);
        ok.put("data", response);
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/multiPeriod")
    public ResponseEntity<Map<String, Object>> getMultiPeriod(@RequestParam String symbol) {
        SmcMultiPeriodResponse data = smcMultiPeriodService.getCached(symbol);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    private List<SmcOrderBlock> toOrderBlocks(List<SmartMoneyConceptsIndicator.OrderBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return List.of();
        return blocks.stream().map(b -> {
            SmcOrderBlock dto = new SmcOrderBlock();
            dto.setHigh(b.barHigh);
            dto.setLow(b.barLow);
            dto.setTime(b.barTime);
            dto.setBias(b.bias);
            return dto;
        }).collect(Collectors.toList());
    }

    private boolean getBosChochField(SmcBarResult bar, String type) {
        switch (type) {
            case "swingBullishBOS": return bar.isSwingBullishBOS();
            case "swingBearishBOS": return bar.isSwingBearishBOS();
            case "swingBullishCHOCH": return bar.isSwingBullishCHOCH();
            case "swingBearishCHOCH": return bar.isSwingBearishCHOCH();
            case "internalBullishBOS": return bar.isInternalBullishBOS();
            case "internalBearishBOS": return bar.isInternalBearishBOS();
            case "internalBullishCHOCH": return bar.isInternalBullishCHOCH();
            case "internalBearishCHOCH": return bar.isInternalBearishCHOCH();
            default: return false;
        }
    }

    private BarSeries safeBuildSeries(List<Candlestick> klines) {
        if (klines == null || klines.isEmpty()) return null;
        return IndicatorWrapHelper.buildSeries(klines);
    }

    private List<Candlestick> loadKlines(String symbol, CandlestickIntervalEnum interval, Long from, Long to, int limit) {
        KlineParam klineParam = KlineParam.builder()
                .symbol(symbol)
                .klineInterval(interval)
                .size(limit)
                .startTime(from)
                .endTime(to)
                .build();
        if (from != null || to != null) {
            return candlestickService.getKlines4KChart(klineParam);
        }
        return candlestickService.getLastKlines(klineParam);
    }

    private int computeLimit(CandlestickIntervalEnum interval, Long from, Long to, int maxCap) {
        if (from == null || to == null) return 500;
        long start = Math.min(from, to);
        long end = Math.max(from, to);
        long spanMs = Math.max(0, end - start);
        Integer mins = interval != null ? interval.getMinNum() : null;
        long stepMs = (mins != null ? mins.longValue() : 60L) * 60_000L;
        long expected = stepMs > 0 ? (spanMs / stepMs) + 10 : 500;
        long capped = Math.min(maxCap, Math.max(200, expected));
        return (int) capped;
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return null;
        }
        try {
            return CandlestickIntervalEnum.valueOf(interval);
        } catch (IllegalArgumentException ignored) {
            for (CandlestickIntervalEnum value : CandlestickIntervalEnum.values()) {
                if (interval.equalsIgnoreCase(value.getCode())) {
                    return value;
                }
            }
            return null;
        }
    }
}
