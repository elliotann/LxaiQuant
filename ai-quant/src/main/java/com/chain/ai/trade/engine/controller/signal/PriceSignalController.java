package com.chain.ai.trade.engine.controller.signal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.query.TechnicalSignalQuery;
import com.chain.ai.trade.engine.signal.factory.SignFactory;
import com.chain.ai.trade.engine.signal.mapper.TradeSignalMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.common.utils.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 信号管理控制器
 * 提供技术信号的分页查询和历史信号生成功能
 */
@RestController
@RequestMapping("/api/price-signal")
@RequiredArgsConstructor
@Slf4j
public class PriceSignalController {

    private final com.chain.ai.trade.engine.signal.service.ITechnicalSignalService technicalSignalService;
    private final ICandlestickService candlestickService;
    private final TradeSignalMapper tradeSignalMapper;
    private final ITradingBotService tradingBotService;
    private final IStrategyService strategyService;

    /**
     * 分页查询技术信号
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listTechnicalSignals(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String indicator,
            @RequestParam(required = false) String technicalDirection,
            @RequestParam(required = false) String timeframe,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        try {
            TechnicalSignalQuery query = new TechnicalSignalQuery();
            query.setSymbol(symbol);
            query.setIndicator(indicator);
            query.setTechnicalDirection(technicalDirection);
          //  query.setTimeframe(timeframe);
            query.setPageNum(pageNum);
            query.setPageSize(pageSize);

            if (StringUtils.isNotEmpty(startTime)) {
                query.setStartTime(java.time.LocalDateTime.parse(startTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            if (StringUtils.isNotEmpty(endTime)) {
                query.setEndTime(java.time.LocalDateTime.parse(endTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }

            IPage<TechnicalSignal> page = technicalSignalService.pageTechnicalSignals(query);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("查询技术信号失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询技术信号失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 生成历史信号
     * 参考 HistorySignTaskExecute 的逻辑
     */
    @PostMapping("/generate-history")
    public ResponseEntity<Map<String, Object>> generateHistorySignals(@RequestBody Map<String, Object> request) {
        try {
            String symbol = (String) request.get("symbol");
            String intervalStr = (String) request.get("interval");
            String strategyTypeStr = (String) request.get("strategyType");
            Long startTime = request.get("startTime") != null ? Long.valueOf(request.get("startTime").toString()) : null;
            String robotId = (String) request.get("robotId");

            if (StringUtils.isEmpty(symbol) || StringUtils.isEmpty(intervalStr) || startTime == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "参数不完整");
                return ResponseEntity.badRequest().body(result);
            }

            CandlestickIntervalEnum klineInterval = CandlestickIntervalEnum.valueOf(intervalStr.toUpperCase());
            // 如果前端没传 strategyType，从 robotId 解析策略类型
            SignFactory.SignType strategyType;
            if (StringUtils.isNotEmpty(strategyTypeStr)) {
                strategyType = SignFactory.SignType.valueOf(strategyTypeStr.toUpperCase());
            } else if (StringUtils.isNotEmpty(robotId)) {
                strategyType = resolveSignTypeFromRobotId(robotId);
                if (strategyType == null) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("message", "无法从机器人ID解析策略类型");
                    return ResponseEntity.badRequest().body(result);
                }
                log.info("从 robotId={} 解析出 strategyType={}", robotId, strategyType);
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "参数不完整，缺少 strategyType 或 robotId");
                return ResponseEntity.badRequest().body(result);
            }

            log.info("开始生成历史信号: symbol={}, interval={}, strategyType={}, startTime={}",
                    symbol, klineInterval, strategyType, startTime);

            // 获取K线
            KlineParam klineParam = KlineParam.builder()
                    .memberId("1111")
                    .accountId("1111")
                    .symbol(symbol)
                    .klineInterval(klineInterval)
                    .build();

            // 计算向前1000根K线的时间作为startTime
            long klineCount = 999;
            long intervalSeconds = klineInterval.getMinNum() * 60; // 每根K线的秒数
            long calculatedStartTime = startTime - (klineCount * intervalSeconds);

            klineParam.setStartTime(calculatedStartTime * 1000);
            klineParam.setEndTime(startTime * 1000);
            klineParam.setTest(true);

            log.info("K线查询参数: startTime={}, endTime={}, interval={}, calculatedStartTime={}",
                    calculatedStartTime, startTime, klineInterval, calculatedStartTime);

            List<Candlestick> candlesticksList = candlestickService.getKlines(klineParam);
            if (candlesticksList.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "K线数据为空");
                return ResponseEntity.badRequest().body(result);
            }

            LinkedList<Candlestick> candlesticks = new LinkedList<>(candlesticksList);
            Candlestick nextKline = null;
            Long nextKlineTime =candlesticks.getLast().getId();
            int signalCount = 0;

            while (true) {
                IndicatorCalcDto calcDto = new IndicatorCalcDto();
                calcDto.setKLines(candlesticks);
                calcDto.setRobotId(robotId);
                calcDto.setSymbol(symbol);
                calcDto.setRobotName(strategyType.name());
                calcDto.setCandlestickIntervalEnum(klineInterval);

                BuyAndSellWeightDto resultSmooth = SignFactory.getInstance(strategyType).execute(calcDto);
                if (resultSmooth != null) {
                    signalCount++;
                }
                log.debug("{} 计算多结果为:{}", strategyType, resultSmooth != null ? resultSmooth.getSignalType() : null);

                // 获取最新K线
                if (klineInterval != null) {
                    nextKlineTime += klineInterval.getMinNum() * 60 * 1000;
                }
                log.debug("最后K线时间:{}", DateUtil.longConvertDateTime(nextKlineTime));

                nextKline = candlestickService.getCandlestick(nextKlineTime, symbol, klineInterval, candlesticks.getFirst().getExchange());

                if (nextKline == null) {
                    break;
                }

                candlesticks.removeFirst();
                candlesticks.addLast(nextKline);
            }

            log.info("历史信号生成完成: symbol={}, strategyType={}, signalCount={}", symbol, strategyType, signalCount);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "历史信号生成完成");
            result.put("signalCount", signalCount);
            result.put("symbol", symbol);
            result.put("strategyType", strategyType.name());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("生成历史信号失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "生成历史信号失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 根据指标类型清除所有技术信号
     */
    @DeleteMapping("/clear-by-indicator")
    public ResponseEntity<Map<String, Object>> clearSignalsByIndicator(
            @RequestParam(required = false) String indicator) {
        try {
            if (StringUtils.isEmpty(indicator)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "指标类型不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            log.info("开始清除技术信号: indicator={}", indicator);

            // 1. 查询该指标下的所有技术信号ID
            List<TechnicalSignal> toDeleteSignals = technicalSignalService.list(
                    new LambdaQueryWrapper<TechnicalSignal>()
                            .eq(TechnicalSignal::getIndicator, indicator)
                            .select(TechnicalSignal::getId));
            List<Long> technicalSignalIds = toDeleteSignals.stream()
                    .map(TechnicalSignal::getId)
                    .collect(java.util.stream.Collectors.toList());

            if (!technicalSignalIds.isEmpty()) {
                // 2. 先物理删除关联的业务信号（TradeSignal 有 @TableLogic，必须绕过逻辑删除）
                int tradeDeleted = tradeSignalMapper.deletePhysicalByTechnicalSignalIds(technicalSignalIds);
                log.info("删除关联业务信号: 技术信号数量={}, 删除数量={}", technicalSignalIds.size(), tradeDeleted);

                // 3. 再删除技术信号
                boolean deleted = technicalSignalService.remove(
                        new LambdaQueryWrapper<TechnicalSignal>()
                                .eq(TechnicalSignal::getIndicator, indicator));
                log.info("清除技术信号完成: indicator={}, 删除数量={}", indicator, technicalSignalIds.size());

                Map<String, Object> result = new HashMap<>();
                result.put("success", deleted);
                result.put("message", deleted ? "清除成功" : "清除失败");
                result.put("deletedCount", technicalSignalIds.size());
                result.put("indicator", indicator);

                return ResponseEntity.ok(result);
            } else {
                log.info("没有找到需要清除的技术信号: indicator={}", indicator);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "没有需要清除的信号");
                result.put("deletedCount", 0);
                result.put("indicator", indicator);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            log.error("清除技术信号失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "清除技术信号失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 根据 robotId 解析策略类型
     */
    private SignFactory.SignType resolveSignTypeFromRobotId(String robotId) {
        try {
            TradingBot bot = tradingBotService.getByBotId(robotId);
            if (bot == null || StringUtils.isEmpty(bot.getStrategyId())) {
                log.warn("机器人不存在或未关联策略: robotId={}", robotId);
                return null;
            }
            return resolveSignTypeFromClassName(bot.getStrategyId(), bot.getStrategyId());
        } catch (Exception e) {
            log.error("解析策略类型失败: robotId={}", robotId, e);
            return null;
        }
    }

    /**
     * 根据 className 和策略名称映射为 SignFactory.SignType
     */
    private SignFactory.SignType resolveSignTypeFromClassName(String className, String strategyName) {
        String name = className != null ? className : "";
        String lowerName = name.toLowerCase();

        // 按关键词匹配
        if (lowerName.contains("bollinger") || lowerName.contains("boll")) {
            return SignFactory.SignType.BOLL_RSI;
        }
        if (lowerName.contains("macd")) {
            return SignFactory.SignType.MACD;
        }
        if (lowerName.contains("rangefilter") || lowerName.contains("range_filter")) {
            return SignFactory.SignType.RANGE_FILTER;
        }
        if (lowerName.contains("fibonacci") || lowerName.contains("fib_bands") || lowerName.contains("fib")) {
            return SignFactory.SignType.FIB_BANDS;
        }
        if (lowerName.contains("ssl")) {
            return SignFactory.SignType.SSL_CHANNEL;
        }
        if (lowerName.contains("logreg") || lowerName.contains("logistic")) {
            return SignFactory.SignType.LOGREG_CHANNEL_TREND;
        }
        if (lowerName.contains("combined")) {
            return SignFactory.SignType.COMBINED;
        }
        // AI 策略需要根据策略名称进一步区分
        if (lowerName.contains("ai") || lowerName.contains("artificial")) {
            return resolveAiSignType(strategyName);
        }
        return null;
    }

    /**
     * 根据策略名称解析 AI 策略的具体类型
     */
    private SignFactory.SignType resolveAiSignType(String strategyName) {
        if (strategyName == null) {
            return null;
        }
        String lowerName = strategyName.toLowerCase();
        if (lowerName.contains("趋势") || lowerName.contains("trend")) {
            return SignFactory.SignType.AI_TREND;
        }
        if (lowerName.contains("网格") || lowerName.contains("grid")) {
            return SignFactory.SignType.AI_GRID;
        }
        if (lowerName.contains("均值回归") || lowerName.contains("mean") || lowerName.contains("reversion")) {
            return SignFactory.SignType.AI_MEAN_REVERSION;
        }
        if (lowerName.contains("突破") || lowerName.contains("breakout")) {
            return SignFactory.SignType.AI_BREAKOUT;
        }
        if (lowerName.contains("剥头皮") || lowerName.contains("scalp")) {
            return SignFactory.SignType.AI_SCALPING;
        }
        return null;
    }
}

