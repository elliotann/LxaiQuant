package com.chain.ai.trade.engine.controller.test;

import com.chain.ai.trade.backtest.entity.dto.BacktestReportDTO;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.entity.dto.BacktestTaskDTO;
import com.chain.ai.trade.backtest.service.BacktestTaskService;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.BacktestRequest;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;

import com.chain.ai.trade.common.utils.RedisCache;

import com.chain.ai.trade.engine.model.PerformanceMetrics;
import com.chain.ai.trade.engine.data.provider.KlineDataProviderFactory;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.entity.dto.BacktestResponse;
import com.chain.ai.trade.engine.service.BacktestResultSaveService;
import com.chain.ai.trade.engine.service.WebSocketNotificationService;
import com.chain.ai.trade.engine.strategy.StrategyFactory;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.ta4j.core.BarSeries;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.BacktestSignalWeightRecalcService;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine.strategy.core.rule.MultiDirectionEntryRule;
import com.chain.ai.trade.engine2.backtest.BacktestResult;
import com.chain.ai.trade.engine2.persistence.PersistenceGateway;
import com.chain.ai.trade.engine2.strategy.impl.SignalScriptStrategy;
import com.chain.ai.trade.engine2.rules.DefaultScaleInRule;
import com.chain.ai.trade.engine2.rules.ScaleInRule;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.engine2.rules.base.SignalReversalRule;
import com.chain.ai.trade.engine2.rules.base.FixedStopLossRule;
import com.chain.ai.trade.engine2.rules.base.FixedTakeProfitRule;
import com.chain.ai.trade.engine2.rules.base.SmcDynamicExitRule;
import com.chain.ai.trade.engine2.rules.base.SmcStopPreviewer;
import com.chain.ai.trade.engine2.rules.composite.OrTradingRule;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.engine.data.mtf.ResampleMultiTimeFrameProvider;
import com.chain.ai.trade.engine.service.ExitRuleConfigService;
import com.chain.ai.trade.engine.strategy.DynamicRiskEngineDTO;
import com.chain.ai.trade.engine.strategy.ExitRulesConfigDTO;
import java.util.stream.Collectors;

import static com.chain.ai.trade.common.utils.BacktestCancelHelper.*;

@RestController
@RequestMapping("/api/backtest")
@ConditionalOnClass(name = "com.chain.ai.trade.engine2.backtest.BacktestService")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    // ==================== 常量 ====================
    private static final String BACKTEST_PREFIX = "BT_";
    private static final String DEFAULT_STRATEGY = "TREND_LINE_BOTH";
    private static final String DEFAULT_INTERVAL = "5m";
    private static final String SYSTEM_CREATOR = "SYSTEM";
    private static final String USDT = "USDT";
    private static final String SWAP_SUFFIX = "-SWAP";
    private static final String BACKTEST_LEGACY = "BACKTEST_LEGACY";
    /** 时区偏移：UTC+8 的毫秒数，用于修正 buildSeries 产生的 8 小时时间偏移（与 V1 引擎保持一致） */
    private static final long TIMEZONE_OFFSET_MS = 8 * 60 * 60 * 1000L;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final String IDEMPOTENT_KEY_PATTERN = "v7:order:*:%s:*";

    // ==================== 依赖注入 ====================

    private final BacktestTaskService backtestTaskService;
    private final BacktestTaskService backtestTaskDetailService;
    private final ICandlestickService candlestickService;
    private final WebSocketNotificationService webSocketService;

    private final com.chain.ai.trade.backtest.service.BacktestReportService backtestReportService;
    private final BacktestResultSaveService backtestResultSaveService;

    @Autowired(required = false)
    private ITradingBotService tradingBotService;
    @Autowired(required = false)
    private com.chain.ai.trade.engine.strategy.service.IStrategyService strategyService;
    @Autowired(required = false)
    private com.chain.ai.trade.engine2.backtest.BacktestService backtestServiceV2;

    @Autowired(required = false)
    private RedisCache redisCache;
    @Autowired(required = false)
    private StrategyFactory strategyFactory;
    @Autowired(required = false)
    private ITechnicalSignalService technicalSignalService;
    @Autowired(required = false)
    private BacktestSignalWeightRecalcService signalWeightRecalcService;
    @Autowired(required = false)
    private KlineDataProviderFactory klineDataProviderFactory;

    @Autowired(required = false)
    private PersistenceGateway persistenceGateway;

    @Autowired(required = false)
    private com.chain.ai.trade.order.mapper.TradeOrderMapper tradeOrderMapper;
    @Autowired(required = false)
    private com.chain.ai.trade.order.mapper.TradeOrderItemMapper tradeOrderItemMapper;
    @Autowired(required = false)
    private com.chain.ai.trade.order.mapper.TradeOrderCloseMapper tradeOrderCloseMapper;
    @Autowired(required = false)
    private com.chain.ai.trade.order.mapper.TradeOrderCloseItemMapper tradeOrderCloseItemMapper;

    @Autowired(required = false)
    private com.chain.ai.trade.engine.signal.service.ITradeSignalService tradeSignalService;

    // ==================== 公共辅助方法 ====================
    private String generateTaskId() {
        return BACKTEST_PREFIX + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> successResponse(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        if (data != null) resp.putAll(asMap(data));
        return resp;
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("errorMessage", message);
        return resp;
    }

    private Map<String, Object> asMap(Object obj) {
        try {
            return OBJECT_MAPPER.convertValue(obj, Map.class);
        } catch (Exception e) {
            log.warn("转换对象为Map失败", e);
            return new HashMap<>();
        }
    }

    // ==================== API 端点 ====================

    @PostMapping("/async/create")
    public ResponseEntity<Map<String, Object>> createAsyncBacktestTask(@RequestBody BacktestRequest request) {
        log.info("收到异步回测任务创建请求: {}", request);
        try {
            BacktestTaskDTO taskDTO = createBacktestTaskDTO(request);
            BacktestTaskDTO created = backtestTaskDetailService.createTask(taskDTO);
            String taskId = created.getTaskId();

            CompletableFuture.runAsync(() -> {
                registerTask(taskId);
                try {
                    executeBacktestAsync(taskId);
                } finally {
                    unregisterTask(taskId);
                }
            });

            return ResponseEntity.ok(successResponse(Map.of("taskId", taskId, "message", "回测任务已创建并开始异步执行")));
        } catch (Exception e) {
            log.error("创建异步回测任务失败", e);
            return ResponseEntity.internalServerError().body(errorResponse("创建异步回测任务失败: " + e.getMessage()));
        }
    }


    @GetMapping("/strategies")
    public ResponseEntity<String[]> getSupportedStrategies() {
        return ResponseEntity.ok(new String[]{"SIGNAL_BASED"});
    }

    @GetMapping("/async/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getAsyncBacktestTaskStatus(@PathVariable String taskId) {
        try {
            BacktestTaskDTO task = backtestTaskDetailService.getTaskDetail(taskId);
            if (task == null) {
                return ResponseEntity.notFound().build();
            }
            Map<String, Object> response = asMap(task);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取异步回测任务状态失败: {}", taskId, e);
            return ResponseEntity.internalServerError().body(errorResponse("获取任务状态失败: " + e.getMessage()));
        }
    }

    @GetMapping("/async/result/{taskId}")
    public ResponseEntity<Map<String, Object>> getAsyncBacktestResult(@PathVariable String taskId) {
        try {
            BacktestResultDTO result = backtestTaskService.getBacktestResult(taskId);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            Map<String, Object> response = asMap(result);
            response.put("success", true);
            // 补充起始资金，BacktestResultDTO 不存储该字段，从任务中获取
            BacktestTaskDTO task = backtestTaskDetailService.getTaskDetail(taskId);
            if (task != null && task.getInitialCapital() != null) {
                response.put("initialCapital", task.getInitialCapital());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取异步回测结果失败: {}", taskId, e);
            return ResponseEntity.internalServerError().body(errorResponse("获取回测结果失败: " + e.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getBacktestTaskStatus(@PathVariable String taskId) {
        try {
            BacktestTaskDTO task = getBacktestTask(taskId);
            if (task == null) {
                return ResponseEntity.notFound().build();
            }
            Map<String, Object> response = asMap(task);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取任务状态失败", e);
            return ResponseEntity.internalServerError().body(errorResponse(e.getMessage()));
        }
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<Map<String, Object>> getBacktestResult(@PathVariable String taskId) {
        try {
            BacktestResultDTO result = backtestTaskService.getBacktestResult(taskId);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            Map<String, Object> response = asMap(result);
            response.put("success", true);
            // 补充起始资金，BacktestResultDTO 不存储该字段，从任务中获取
            BacktestTaskDTO task = backtestTaskDetailService.getTaskDetail(taskId);
            if (task != null && task.getInitialCapital() != null) {
                response.put("initialCapital", task.getInitialCapital());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取回测结果失败", e);
            return ResponseEntity.internalServerError().body(errorResponse(e.getMessage()));
        }
    }

    @PostMapping("/report/generate/{taskId}")
    public ResponseEntity<Map<String, Object>> generateBacktestReport(@PathVariable String taskId) {
        log.info("收到生成回测报告请求: taskId={}", taskId);
        try {
            BacktestReportDTO report = backtestReportService.generateReport(taskId);
            if (report != null) {
                return ResponseEntity.ok(successResponse(Map.of("report", report, "message", "回测报告生成成功")));
            } else {
                return ResponseEntity.badRequest().body(errorResponse("生成回测报告失败"));
            }
        } catch (Exception e) {
            log.error("生成回测报告失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(errorResponse("生成回测报告异常: " + e.getMessage()));
        }
    }

    @GetMapping("/report/{taskId}")
    public ResponseEntity<Map<String, Object>> getBacktestReport(@PathVariable String taskId) {
        log.info("获取回测报告: taskId={}", taskId);
        try {
            BacktestReportDTO report = backtestReportService.getReport(taskId);
            if (report != null) {
                return ResponseEntity.ok(successResponse(Map.of("report", report)));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取回测报告失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(errorResponse("获取回测报告异常: " + e.getMessage()));
        }
    }

    @PutMapping("/report/notes/{taskId}")
    public ResponseEntity<Map<String, Object>> updateReportNotes(@PathVariable String taskId, @RequestBody Map<String, String> request) {
        log.info("更新报告笔记: taskId={}", taskId);
        try {
            String notes = request.get("notes");
            boolean success = backtestReportService.updateNotes(taskId, notes);
            return success ? ResponseEntity.ok(successResponse(Map.of("message", "笔记更新成功")))
                    : ResponseEntity.badRequest().body(errorResponse("笔记更新失败"));
        } catch (Exception e) {
            log.error("更新报告笔记失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(errorResponse("更新笔记异常: " + e.getMessage()));
        }
    }

    @PutMapping("/{taskId}/stop")
    public ResponseEntity<Map<String, Object>> stopBacktest(@PathVariable String taskId) {
        log.info("收到停止回测请求: taskId={}", taskId);
        try {
            cancelTask(taskId);
            backtestTaskDetailService.updateTaskStatus(taskId, "CANCELLED", "用户手动停止");
            return ResponseEntity.ok(successResponse(Map.of("message", "回测任务已停止")));
        } catch (Exception e) {
            log.error("停止回测任务失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(errorResponse("停止回测任务失败: " + e.getMessage()));
        }
    }

    // ==================== 异步执行核心逻辑（拆分后） ====================

    @Async
    public void executeBacktestAsync(String taskId) {
        log.info("开始异步执行回测任务: {}", taskId);
        try {
            webSocketService.sendTaskStatusUpdate(taskId, "RUNNING", 10, "开始执行回测任务");
            backtestTaskDetailService.updateTaskStatus(taskId, "RUNNING", null);

            BacktestTaskDTO task = getBacktestTask(taskId);
            if (task == null) {
                failTask(taskId, "任务不存在");
                return;
            }

            BacktestRequest request = parseTaskConfigFromMap(task.getConfig());
            if (request == null) {
                failTask(taskId, "配置解析失败");
                return;
            }

            if (request.getBacktestType() == BacktestRequest.BacktestType.TRADITIONAL_BACKTEST_NEW) {
                executeV2Backtest(taskId, request);
            } else if (request.getBacktestType() == BacktestRequest.BacktestType.PAPER_TRADING) {
                executePaperTest(taskId, request);
            }
        } catch (Exception e) {
            log.error("异步执行回测任务异常: {}", taskId, e);
            failTask(taskId, e.getMessage());
        }
    }



    // ==================== V2 引擎回测方法 ====================

    private void executeV2Backtest(String taskId, BacktestRequest request) {
        // 1. 解析K线周期
        String intervalCode = request.getInterval() != null ? request.getInterval() : DEFAULT_INTERVAL;

        // 2. 解析时间范围
        long[] timeRange = resolveTimeRange(request);
        String symbol = request.getCoinId().toUpperCase();
        LocalDateTime startLdt = java.time.Instant.ofEpochMilli(timeRange[0])
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endLdt = java.time.Instant.ofEpochMilli(timeRange[1])
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        String strategyId = request.getStrategyId() != null ? request.getStrategyId() : request.getRobotId();

        // 3. 构建回测配置（提前构建，后续 K 线加载和 MTF 需要用到交易所信息）
        com.chain.ai.trade.engine2.backtest.BacktestRequest v2Request =
                new com.chain.ai.trade.engine2.backtest.BacktestRequest();
        v2Request.setRobotId(request.getRobotId());
        v2Request.setStrategyId(strategyId);
        v2Request.setSymbol(symbol);
        // 仓位金额不显式设置，由 buildConfig 从机器人 allocatedCapital 获取（默认 1000）
        v2Request.setLeverage(request.getLeverage() != null
                ? request.getLeverage().intValue() : null);
        v2Request.setSlippage(request.getSlippageRate());
        v2Request.setInterval(intervalCode);
        v2Request.setStartDate(startLdt);
        v2Request.setEndDate(endLdt);
        com.chain.ai.trade.engine2.backtest.BacktestConfig config =
                backtestServiceV2 != null
                        ? backtestServiceV2.buildConfig(v2Request)
                        : com.chain.ai.trade.engine2.backtest.BacktestConfig.builder()
                                .symbol(symbol)
                                .warmupPeriod(50)
                                .initialCapital(BigDecimal.valueOf(10000))
                                .positionAmount(v2Request.getPositionAmount() != null
                                        ? v2Request.getPositionAmount() : BigDecimal.valueOf(1000))
                                .leverage(v2Request.getLeverage() != null
                                        ? v2Request.getLeverage() : 1)
                                .slippage(v2Request.getSlippage() != null
                                        ? v2Request.getSlippage() : 0.0)
                                .build();


        // 4. 加载K线数据（带上交易所信息）
        webSocketService.sendTaskProgress(taskId, 20, "加载K线数据");
        TradingStrategyParams klineParams = TradingStrategyParams.builder()
                .symbol(symbol)
                .interval(intervalCode)
                .startTime(timeRange[0])
                .endTime(timeRange[1])
                .testMode(true)
                .memberPlatform(config.getPlatform())
                .build();
        BarSeries series = klineDataProviderFactory.getProvider(true).fetchAllKlines(klineParams);
        if (series == null || series.getBarCount() == 0) {
            failTask(taskId, "未获取到K线数据");
            return;
        }

        // 5. 加载信号缓存
        webSocketService.sendTaskProgress(taskId, 40, "加载信号数据");
        if (technicalSignalService == null) {
            failTask(taskId, "信号服务不可用");
            return;
        }
        SignalCacheManager signalCache = new SignalCacheManager(technicalSignalService);
        signalCache.loadSignals(startLdt, endLdt, symbol,
                request.getSignalIndicatorType(), request.getSignalDataFrom());

        // 5.1 离线重算信号权重：用 L2 特征 + 权重规则引擎重算 signal_strength，写回内存缓存（不回写库）
        if (signalWeightRecalcService != null) {
            CandlestickIntervalEnum recalcInterval = CandlestickIntervalEnum.fromCodeValue(intervalCode);
            if (recalcInterval != null) {
                Map<String, SignalInfo> recalcMap = signalWeightRecalcService.recalcWeights(
                        request.getSignalIndicatorType(), symbol, recalcInterval.name(), startLdt, endLdt);
                if (recalcMap != null && !recalcMap.isEmpty()) {
                    signalCache.putAll(recalcMap);
                    log.info("离线重算写入信号缓存: symbol={}, 条数={}", symbol, recalcMap.size());
                }
            }
        }

        // 6. 创建入场规则（基于信号缓存）
        MultiDirectionEntryRule entryRule = new MultiDirectionEntryRule(series, signalCache);
        log.info("V2引擎入场规则: strategyId={}, symbol={}", strategyId, symbol);

        // 从策略配置中提取仓位模式和权益
        CandlestickIntervalEnum baseInterval = CandlestickIntervalEnum.fromCodeValue(intervalCode);
        String robotId = request.getRobotId();
        ExitRulesConfigDTO exitConfig = strategyFactory != null
                ? strategyFactory.loadExitRulesConfig(strategyId, robotId)
                : ExitRuleConfigService.loadConfigDTO(strategyId);
        // 账户权益
        BigDecimal initialCapital = config.getInitialCapital() != null ? config.getInitialCapital() : BigDecimal.ZERO;
        config.setAccountBalance(initialCapital);
        // 仓位模式：从 position_risk 配置读取
        if (strategyFactory != null) {
            Map<String, Object> positionRisk = strategyFactory.loadPositionRiskConfig(strategyId);
            if (positionRisk != null && !positionRisk.isEmpty()) {
                String fm = String.valueOf(positionRisk.getOrDefault("positionMode", ""));
                // fixed_ratio → QUALITY, risk_based → RISK
                config.setPositionMode("risk_based".equals(fm) ? "RISK" : "QUALITY");
                log.info("仓位模式: frontend={}, config={}", fm, config.getPositionMode());
                // 单笔风险比例
                Object riskPct = positionRisk.get("singleTradeRiskPct");
                if (riskPct instanceof Number) {
                    config.setSingleTradeRiskPct(((Number) riskPct).doubleValue());
                }
                // 信号频率控制配置
                Object freqEnabled = positionRisk.get("signalFrequencyEnabled");
                if (freqEnabled instanceof Boolean) {
                    config.setSignalFrequencyEnabled((Boolean) freqEnabled);
                }
                Object freqGranularity = positionRisk.get("signalFrequencyGranularity");
                if (freqGranularity != null) {
                    config.setSignalFrequencyGranularity(String.valueOf(freqGranularity));
                }
                Object freqMode = positionRisk.get("signalFrequencyMode");
                if (freqMode != null) {
                    config.setSignalFrequencyMode(String.valueOf(freqMode));
                }
            }
        }
        // 日常止损缓冲：从结构止盈止损 DTO 读取
        if (exitConfig != null && exitConfig.getStructureStopProfit() != null
                && exitConfig.getStructureStopProfit().getDynamicStopLoss() != null) {
            config.setDailyStopLossBuffer(
                    exitConfig.getStructureStopProfit().getDynamicStopLoss().getDailyBuffer());
        }

        // 8. 构建出场规则（从 DB 配置加载）并创建策略实例
        boolean smcEnabled = exitConfig != null && exitConfig.getSmcExit() != null && exitConfig.getSmcExit().isEnabled();
        boolean structureEnabled = exitConfig != null && exitConfig.getStructureStopProfit() != null
                && exitConfig.getStructureStopProfit().isEnabled();
        // 动态风控引擎是否启用（防守线移动止损 / 进攻线移动止盈）
        boolean dynamicRiskEnabled = false;
        if (strategyFactory != null) {
            DynamicRiskEngineDTO dre = strategyFactory.loadDynamicRiskEngine(strategyId);
            if (dre != null) {
                dynamicRiskEnabled = (dre.getTrailingStop() != null && dre.getTrailingStop().isEnabled())
                        || (dre.getTrailingTakeProfit() != null && dre.getTrailingTakeProfit().isEnabled());
            }
        }

        MultiTimeFrameProvider mtfProvider = null;
        if (baseInterval != null && (smcEnabled || structureEnabled || dynamicRiskEnabled)) {
            // SMC 需要足够的子周期历史数据计算 swing structure，使用直查模式-时间范围
            // 各周期独立从 DB 一次性查询，回测全程不重复查询
            // 预加载量按最高周期（240m×1000根≈166天）计算，确保所有周期都有足够历史数据
            long preloadMs = 240L * 1000L * 60_000L;
            mtfProvider = new ResampleMultiTimeFrameProvider(series, baseInterval, candlestickService,
                    config.getPlatform(), symbol, timeRange[0] - preloadMs, timeRange[1]);
        }
        TradingRule exitRule = buildExitRules(signalCache, strategyId, robotId, mtfProvider, symbol, series);
        ScaleInRule scaleInRule = new DefaultScaleInRule(config, series);
        SignalScriptStrategy strategy = new SignalScriptStrategy(strategyId, symbol, series, signalCache,
                entryRule, exitRule, scaleInRule);

        // 9. 运行回测引擎
        webSocketService.sendTaskProgress(taskId, 60, "执行V2引擎回测");
        com.chain.ai.trade.engine2.backtest.BacktestEngine engine =
                new com.chain.ai.trade.engine2.backtest.BacktestEngine(series, strategy, config,
                        persistenceGateway, taskId);

        // 注入信号频率控制器
        if (config.isSignalFrequencyEnabled()) {
            engine.initSignalFrequency(
                    config.isSignalFrequencyEnabled(),
                    config.getSignalFrequencyGranularity(),
                    config.getSignalFrequencyMode());
        }

        // 注入 SMC 止损预计算器（供以损定量使用）
        // 优先级：结构止盈止损 > 固定百分比止盈止损兜底
        if (exitConfig != null) {
            try {
                double fixedSlPct = 0;
                if (exitConfig.getFixedPercentStopLoss() != null
                        && exitConfig.getFixedPercentStopLoss().isEnabled()) {
                    fixedSlPct = exitConfig.getFixedPercentStopLoss().getPercent();
                }
                double fixedTpPct = 0;
                if (exitConfig.getFixedPercentTakeProfit() != null
                        && exitConfig.getFixedPercentTakeProfit().isEnabled()) {
                    fixedTpPct = exitConfig.getFixedPercentTakeProfit().getPercent();
                }

                if (structureEnabled) {
                    // 结构止盈止损启用：优先 SMC 结构计算
                    var ss = exitConfig.getStructureStopProfit();
                    var dsl = ss.getDynamicStopLoss();
                    String stopPeriod = String.valueOf(dsl.getDailyPeriod());
                    double buffer = dsl.getDailyBuffer() / 100.0;
                    var ref = ss.getReference();
                    String tpPeriod = ref != null && ref.getTakeProfitPeriod() > 0
                            ? String.valueOf(ref.getTakeProfitPeriod())
                            : stopPeriod;
                    SmcStopPreviewer stopPreviewer = new SmcStopPreviewer(
                            mtfProvider, symbol, stopPeriod, buffer, tpPeriod, true, fixedSlPct, fixedTpPct);
                    engine.setStopPreviewer(stopPreviewer);
                    log.info("回测引擎止损预计算器已注入（结构止盈止损优先）: stopPeriod={}, buffer={}, tpPeriod={}, fixedSl={}, fixedTp={}",
                            stopPeriod, buffer, tpPeriod, fixedSlPct, fixedTpPct);
                } else if (fixedSlPct > 0 || fixedTpPct > 0) {
                    // 结构止盈止损未启用，仅固定百分比止盈止损兜底
                    SmcStopPreviewer stopPreviewer = new SmcStopPreviewer(
                            null, symbol, "15", 0, null, false, fixedSlPct, fixedTpPct);
                    engine.setStopPreviewer(stopPreviewer);
                    log.info("回测引擎止损预计算器已注入（固定百分比止盈止损兜底）: fixedSl={}, fixedTp={}", fixedSlPct, fixedTpPct);
                }
            } catch (Exception e) {
                log.warn("注入止损预计算器失败，以损定量将降级: {}", e.getMessage());
            }
        }

        BacktestResult v2Result = engine.run();
        log.info("回测引擎执行完成: engineTrades.size()={}", v2Result.getTrades() != null ? v2Result.getTrades().size() : 0);

        if (isCancelled(taskId)) {
            cancelTask(taskId);
            return;
        }

        // 10. 转换结果（用于 websocket 通知和 UI 展示）
        webSocketService.sendTaskProgress(taskId, 80, "保存回测结果");
        BacktestResponse response = buildV2BacktestResponse(v2Result, request, series);

        // 12. V2引擎已通过 BacktestBatchGateway 保存权益曲线和绩效指标，无需重复保存

        webSocketService.sendTaskProgress(taskId, 90, "生成回测报告");
        tryGenerateReport(taskId);

        backtestTaskDetailService.updateTaskStatus(taskId, "COMPLETED", null);
        Map<String, Object> completed = Map.of(
                "success", true,
                "taskId", taskId,
                "backtestType", request.getBacktestType().name(),
                "message", "V2引擎回测执行完成"
        );
        webSocketService.sendTaskCompleted(taskId, true, completed);
        log.info("V2引擎回测任务执行完成: {}", taskId);
    }

    // ==================== Paper 模拟实盘引擎 ====================

    /**
     * Paper 模拟实盘 — 复用 executeV2Backtest 的 K 线加载和策略构建，改用 PaperEngine 逐根推送。
     */
    private void executePaperTest(String taskId, BacktestRequest request) {
        // 1. 解析参数
        String intervalCode = request.getInterval() != null ? request.getInterval() : DEFAULT_INTERVAL;
        long[] timeRange = resolveTimeRange(request);
        String symbol = request.getCoinId().toUpperCase();
        LocalDateTime startLdt = java.time.Instant.ofEpochMilli(timeRange[0])
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endLdt = java.time.Instant.ofEpochMilli(timeRange[1])
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        String strategyId = request.getStrategyId() != null ? request.getStrategyId() : request.getRobotId();

        // 2. 构建配置（提前构建，后续 K 线加载和 MTF 需要用到交易所信息）
        com.chain.ai.trade.engine2.backtest.BacktestRequest v2Request =
                new com.chain.ai.trade.engine2.backtest.BacktestRequest();
        v2Request.setRobotId(request.getRobotId());
        v2Request.setStrategyId(strategyId);
        v2Request.setSymbol(symbol);
        v2Request.setLeverage(request.getLeverage() != null ? request.getLeverage().intValue() : null);
        v2Request.setSlippage(request.getSlippageRate());
        v2Request.setInterval(intervalCode);
        v2Request.setStartDate(startLdt);
        v2Request.setEndDate(endLdt);

        com.chain.ai.trade.engine2.backtest.BacktestConfig v2Config =
                backtestServiceV2 != null
                        ? backtestServiceV2.buildConfig(v2Request)
                        : com.chain.ai.trade.engine2.backtest.BacktestConfig.builder()
                                .symbol(symbol).warmupPeriod(50)
                                .initialCapital(BigDecimal.valueOf(10000))
                                .positionAmount(v2Request.getPositionAmount() != null
                                        ? v2Request.getPositionAmount() : BigDecimal.valueOf(1000))
                                .leverage(v2Request.getLeverage() != null ? v2Request.getLeverage() : 1)
                                .slippage(v2Request.getSlippage() != null ? v2Request.getSlippage() : 0.0)
                                .build();
        Exchange exchange = v2Config.getPlatform() != null ? v2Config.getPlatform() : Exchange.OKX;

        // 3. 加载K线数据（带上交易所信息）
        webSocketService.sendTaskProgress(taskId, 20, "加载K线数据");
        TradingStrategyParams klineParams = TradingStrategyParams.builder()
                .symbol(symbol).interval(intervalCode)
                .startTime(timeRange[0]).endTime(timeRange[1]).testMode(true)
                .memberPlatform(exchange)
                .build();
        BarSeries series = klineDataProviderFactory.getProvider(true).fetchAllKlines(klineParams);
        if (series == null || series.getBarCount() == 0) {
            failTask(taskId, "未获取到K线数据");
            return;
        }

        // 4. 加载信号缓存
        webSocketService.sendTaskProgress(taskId, 40, "加载信号数据");
        if (technicalSignalService == null) {
            failTask(taskId, "信号服务不可用");
            return;
        }
        SignalCacheManager signalCache = new SignalCacheManager(technicalSignalService);
        signalCache.loadSignals(startLdt, endLdt, symbol,
                request.getSignalIndicatorType(), request.getSignalDataFrom());

        // 5. 创建入场规则
        MultiDirectionEntryRule entryRule = new MultiDirectionEntryRule(series, signalCache);

        // 转为 RealtimeConfig
        com.chain.ai.trade.engine2.realtime.RealtimeConfig realtimeConfig =
                com.chain.ai.trade.engine2.realtime.RealtimeConfig.builder()
                        .symbol(symbol)
                        .initialCapital(v2Config.getInitialCapital() != null
                                ? v2Config.getInitialCapital() : BigDecimal.valueOf(10000))
                        .leverage(v2Config.getLeverage())
                        .positionAmount(v2Config.getPositionAmount())
                        .slippage(BigDecimal.valueOf(v2Config.getSlippage()))
                        .contractSpec(v2Config.getContractSpec())
                        .costModel(new com.chain.ai.trade.engine2.core.cost.MakerTakerCostModel(
                                BigDecimal.valueOf(0.0002), BigDecimal.valueOf(0.0005), false))
                        .warmupPeriod(v2Config.getWarmupPeriod())
                        .positionMode(v2Config.getPositionMode())
                        .build();

        // 仓位模式配置
        if (strategyFactory != null) {
            Map<String, Object> positionRisk = strategyFactory.loadPositionRiskConfig(strategyId);
            if (positionRisk != null && !positionRisk.isEmpty()) {
                String fm = String.valueOf(positionRisk.getOrDefault("positionMode", ""));
                realtimeConfig.setPositionMode("risk_based".equals(fm) ? "RISK" : "QUALITY");
                // 信号频率控制配置
                Object freqEnabled = positionRisk.get("signalFrequencyEnabled");
                if (freqEnabled instanceof Boolean) {
                    realtimeConfig.setSignalFrequencyEnabled((Boolean) freqEnabled);
                }
                Object freqGranularity = positionRisk.get("signalFrequencyGranularity");
                if (freqGranularity != null) {
                    realtimeConfig.setSignalFrequencyGranularity(String.valueOf(freqGranularity));
                }
                Object freqMode = positionRisk.get("signalFrequencyMode");
                if (freqMode != null) {
                    realtimeConfig.setSignalFrequencyMode(String.valueOf(freqMode));
                }
            }
        }

        // 8. 构建出场规则和策略
        CandlestickIntervalEnum baseInterval = CandlestickIntervalEnum.fromCodeValue(intervalCode);
        String robotId = request.getRobotId();
        ExitRulesConfigDTO exitConfig = strategyFactory != null
                ? strategyFactory.loadExitRulesConfig(strategyId, robotId)
                : ExitRuleConfigService.loadConfigDTO(strategyId);
        boolean smcEnabled = exitConfig != null && exitConfig.getSmcExit() != null
                && exitConfig.getSmcExit().isEnabled();
        boolean structureEnabled = exitConfig != null && exitConfig.getStructureStopProfit() != null
                && exitConfig.getStructureStopProfit().isEnabled();
        // 动态风控引擎是否启用（防守线移动止损 / 进攻线移动止盈）
        boolean dynamicRiskEnabled = false;
        if (strategyFactory != null) {
            DynamicRiskEngineDTO dre = strategyFactory.loadDynamicRiskEngine(strategyId);
            if (dre != null) {
                dynamicRiskEnabled = (dre.getTrailingStop() != null && dre.getTrailingStop().isEnabled())
                        || (dre.getTrailingTakeProfit() != null && dre.getTrailingTakeProfit().isEnabled());
            }
        }

        MultiTimeFrameProvider mtfProvider = null;
        if (baseInterval != null && (smcEnabled || structureEnabled || dynamicRiskEnabled)) {
            long preloadMs = 240L * 1000L * 60_000L;
            mtfProvider = new ResampleMultiTimeFrameProvider(series, baseInterval, candlestickService,
                    exchange, symbol, timeRange[0] - preloadMs, timeRange[1]);
        }
        TradingRule exitRule = buildExitRules(signalCache, strategyId, robotId, mtfProvider, symbol, series);
        ScaleInRule scaleInRule = new DefaultScaleInRule(v2Config, series);
        SignalScriptStrategy strategy = new SignalScriptStrategy(strategyId, symbol, series, signalCache,
                entryRule, exitRule, scaleInRule);

        // 9. 创建 Paper 引擎（不走 Spring，手动组装）
        // 预热 MTF：在主线程触发 DB 查询，避免引擎线程无事务上下文
        // SmcDynamicExitRule.updateDynamicLevels() 硬编码查询 15m/60m 周期
        if (mtfProvider != null && baseInterval != null) {
            try { mtfProvider.getSeries(baseInterval); } catch (Exception e) {
                log.warn("MTF 预热 baseInterval 失败: {}", e.getMessage());
            }
            try { mtfProvider.getSeries(CandlestickIntervalEnum.MIN15); } catch (Exception e) {
                log.warn("MTF 预热 15m 失败: {}", e.getMessage());
            }
            try { mtfProvider.getSeries(CandlestickIntervalEnum.MIN60); } catch (Exception e) {
                log.warn("MTF 预热 60m 失败: {}", e.getMessage());
            }
        }

        com.chain.ai.trade.engine2.realtime.PaperExecutionHandler paperHandler =
                new com.chain.ai.trade.engine2.realtime.PaperExecutionHandler();

        String memberId = "1665908516499693568";
        String robotIdForGw = request.getRobotId() != null && !request.getRobotId().isBlank()
                ? request.getRobotId() : "2001";
        String accountId = String.valueOf(1768185450252304387L);
        int leverage = realtimeConfig.getLeverage();
        com.chain.ai.trade.engine2.persistence.RealtimeGateway gateway =
                new com.chain.ai.trade.engine2.persistence.RealtimeAsyncGateway(
                        tradeOrderMapper, tradeOrderItemMapper,
                        tradeOrderCloseMapper, tradeOrderCloseItemMapper,
                        tradeSignalService,
                        memberId, robotIdForGw, accountId, leverage,
                        symbol, intervalCode, true,
                        redisCache, tradingBotService);
        com.chain.ai.trade.engine2.realtime.RealtimeContext context =
                new com.chain.ai.trade.engine2.realtime.RealtimeContext(realtimeConfig, paperHandler, gateway);
        com.chain.ai.trade.engine2.realtime.PaperEngine engine =
                new com.chain.ai.trade.engine2.realtime.PaperEngine(series, strategy, realtimeConfig, context, gateway);

        // 注入信号频率控制器
        if (realtimeConfig.isSignalFrequencyEnabled()) {
            engine.initSignalFrequency(
                    realtimeConfig.isSignalFrequencyEnabled(),
                    realtimeConfig.getSignalFrequencyGranularity(),
                    realtimeConfig.getSignalFrequencyMode());
        }

        // PaperExecutionHandler 成交回调 → RealtimeContext
        paperHandler.addFillListener(fill -> {
            // 开仓成交：由 RealtimeContext.onOrderFilled 处理（Paper 模式下 submitOrder 已触发 onEntryFilled）
        });

        // 10. 启动引擎（run() 同步调用，一次性处理全部 Bar）
        webSocketService.sendTaskProgress(taskId, 60, "启动Paper引擎");

        int totalBars = series.getBarCount();
        engine.onProgress(idx ->
                webSocketService.sendTaskProgress(taskId,
                        60 + (int) (30.0 * idx / totalBars),
                        String.format("模拟实盘: %d/%d K线", idx, totalBars))
        );
        engine.run();

        // 11. 收集结果
        webSocketService.sendTaskProgress(taskId, 90, "处理结果");
        engine.stop();

        // 13. 从 RealtimeContext 提取结果，转为回测结果格式
        List<BacktestResult.TradeRecord> paperTrades = context.getTrades();
        List<com.chain.ai.trade.engine2.backtest.BacktestResult.EquityPoint> equityPoints = 
                context.getEquityCurve();

        com.chain.ai.trade.engine2.backtest.BacktestResult paperResult =
                com.chain.ai.trade.engine2.backtest.BacktestResult.builder()
                        .trades(paperTrades)
                        .equityCurve(equityPoints)
                        .finalEquity(context.getEquity(BigDecimal.valueOf(series.getBar(totalBars - 1)
                                .getClosePrice().doubleValue())))
                        .totalTrades(paperTrades.size())
                        .totalCommission(context.getTotalCommissionPaid())
                        .build();

        log.info("Paper 模拟实盘完成: trades={}, equityPoints={}", paperTrades.size(), equityPoints.size());

        // 14. 转换结果并保存（复用 V2 回测的结果构建）
        webSocketService.sendTaskProgress(taskId, 95, "保存结果");
        BacktestResponse response = buildV2BacktestResponse(paperResult, request, series);
        backtestResultSaveService.saveBacktestResult(taskId, response, request);

        backtestTaskDetailService.updateTaskStatus(taskId, "COMPLETED", null);
        Map<String, Object> completed = Map.of(
                "success", true,
                "taskId", taskId,
                "backtestType", "PAPER_TRADING",
                "message", "模拟实盘执行完成，共处理 " + engine.getBarIndex() + " 根K线"
        );
        webSocketService.sendTaskCompleted(taskId, true, completed);
        log.info("Paper 模拟实盘任务完成: {}", taskId);
    }

    private BacktestResponse buildV2BacktestResponse(BacktestResult v2Result, BacktestRequest request, BarSeries series) {
        // 1. 获取 V2 聚合交易记录
        List<BacktestResult.TradeRecord> engineTrades = v2Result.getTrades();
        log.info("buildV2BacktestResponse: engineTrades.size()={}", engineTrades != null ? engineTrades.size() : 0);

        // 2. 构建权益曲线和回撤JSON
        String[] equityAndDrawdown = v2EquityToJson(v2Result.getEquityCurve(), series);
        String equityCurveJson = equityAndDrawdown[0];
        String drawdownJson = equityAndDrawdown[1];

        // 3. 计算绩效指标（直接消费 V2 聚合记录）
        double metricsBaseAmount = resolveMetricsBaseAmount(request);
        double avgHoldingPeriod = calculateAverageHoldingPeriodFromEngineTrades(engineTrades, series);
        PerformanceMetrics perf = calculateTraditionalMetrics(
                engineTrades, drawdownJson, metricsBaseAmount,
                request.getStartTime(), request.getEndTime(), series, avgHoldingPeriod);

        // 4. 构建响应
        BacktestResponse.StrategyResult sResult = BacktestResponse.StrategyResult.builder()
                .strategyName(request.getStrategyType())
                .performanceMetrics(perf)
                .equityCurve(equityCurveJson)
                .drawdownSeries(drawdownJson)
                .success(true)
                .build();

        return BacktestResponse.builder()
                .success(true)
                .results(List.of(sResult))
                .executionTime(0L)
                .build();
    }

    /**
     * 从V2引擎交易记录计算平均持仓时间（天）。
     * 按 tradeId 分组，以开仓Bar的起始时间到最后一笔平仓Bar的结束时间计算持仓时长。
     */
    private double calculateAverageHoldingPeriodFromEngineTrades(
            List<BacktestResult.TradeRecord> engineTrades, BarSeries series) {
        if (engineTrades == null || engineTrades.isEmpty() || series == null) return 0.0;

        Map<String, List<BacktestResult.TradeRecord>> grouped = engineTrades.stream()
                .collect(Collectors.groupingBy(BacktestResult.TradeRecord::getTradeId, LinkedHashMap::new, Collectors.toList()));

        double totalDays = 0.0;
        int count = 0;

        for (List<BacktestResult.TradeRecord> group : grouped.values()) {
            int entryIdx = group.get(0).getEntryIndex();
            int exitIdx = group.stream()
                    .mapToInt(BacktestResult.TradeRecord::getExitIndex)
                    .max().orElse(entryIdx);

            if (entryIdx >= 0 && exitIdx >= 0 && entryIdx < series.getBarCount() && exitIdx < series.getBarCount()) {
                try {
                    long entryTime = series.getBar(entryIdx).getBeginTime().toEpochMilli();
                    long exitTime = series.getBar(exitIdx).getEndTime().toEpochMilli();
                    totalDays += Math.max(0, (exitTime - entryTime) / (24.0 * 60 * 60 * 1000.0));
                    count++;
                } catch (Exception ignored) {
                    log.debug("计算持仓时间异常: entryIdx={}, exitIdx={}", entryIdx, exitIdx);
                }
            }
        }
        return count > 0 ? totalDays / count : 0.0;
    }

    /**
     * 格式化 Bar 的结束时间为北京时间字符串（修正 buildSeries 产生的 8 小时偏移）。
     */
    private String formatBarTime(BarSeries series, int index) {
        try {
            if (index >= 0 && index < series.getBarCount()) {
                long adjustedMs = series.getBar(index).getBeginTime().toEpochMilli() - TIMEZONE_OFFSET_MS;
                return Instant.ofEpochMilli(adjustedMs).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 根据 DB 配置构建出场规则组合
     */
    private TradingRule buildExitRules(SignalCacheManager signalCache, String strategyId, String robotId, MultiTimeFrameProvider mtfProvider, String symbol, BarSeries series) {
        ExitRulesConfigDTO config = strategyFactory != null
                ? strategyFactory.loadExitRulesConfig(strategyId, robotId)
                : ExitRuleConfigService.loadConfigDTO(strategyId);
        List<TradingRule> rules = new ArrayList<>();

        // 1. 信号反转出场
        if (config.getSignalReversal() != null && config.getSignalReversal().isEnabled()) {
            rules.add(new SignalReversalRule(signalCache));
        }

        // 2. 固定百分比止损
        if (config.getFixedPercentStopLoss() != null && config.getFixedPercentStopLoss().isEnabled()) {
            rules.add(new FixedStopLossRule(config.getFixedPercentStopLoss().getPercent()));
        }

        // 3. 固定百分比止盈
        if (config.getFixedPercentTakeProfit() != null && config.getFixedPercentTakeProfit().isEnabled()) {
            rules.add(new FixedTakeProfitRule(config.getFixedPercentTakeProfit().getPercent()));
        }

        // 4. SMC 动态出场（基于多周期 SMC 指标的智能止损止盈）
        if (config.getSmcExit() != null && config.getSmcExit().isEnabled() && mtfProvider != null) {
            try {
                var smcExit = config.getSmcExit();
                var ref = smcExit.getReference();

                // LONG 方向
                SmcDynamicExitRule smcLong = new SmcDynamicExitRule(mtfProvider, symbol, SignalType.LONG);
                configureSmcExitRule(smcLong, smcExit, ref);
                rules.add(smcLong);

                // SHORT 方向
                SmcDynamicExitRule smcShort = new SmcDynamicExitRule(mtfProvider, symbol, SignalType.SHORT);
                configureSmcExitRule(smcShort, smcExit, ref);
                rules.add(smcShort);

                log.info("SMC 动态出场规则已加载: targetPeriod={}", ref.getTargetPeriod());
            } catch (Exception e) {
                log.error("创建 SMC 动态出场规则失败", e);
            }
        }

        // 5. 结构止盈止损（SmcStructuredExitRule — 新规则，与旧 SmcDynamicExitRule 独立并行）
        var ss = config.getStructureStopProfit();
        if (ss != null && ss.isEnabled() && mtfProvider != null && strategyFactory != null) {
            try {
                List<TradingRule> structuredRules = strategyFactory.buildStructureStopProfitRules(mtfProvider, symbol, config);
                rules.addAll(structuredRules);
                log.info("结构止盈止损规则已加载: symbol={}, rules={}", symbol, structuredRules.size());
            } catch (Exception e) {
                log.error("创建结构止盈止损规则失败", e);
            }
        }

        // 6. 动态风控引擎（防守线移动止损 / 进攻线移动止盈，独立于 SmcStructuredExitRule）
        DynamicRiskEngineDTO dre = strategyFactory != null
                ? strategyFactory.loadDynamicRiskEngine(strategyId)
                : null;
        if (dre != null && mtfProvider != null) {
            try {
                List<TradingRule> dreRules = strategyFactory.buildDynamicRiskEngineRules(mtfProvider, symbol, dre);
                rules.addAll(dreRules);
                log.info("动态风控引擎出场规则已加载: symbol={}, rules={}", symbol, dreRules.size());
            } catch (Exception e) {
                log.error("创建动态风控引擎出场规则失败", e);
            }
        }

        if (rules.isEmpty()) {
            return null;
        }
        if (rules.size() == 1) {
            return rules.get(0);
        }
        return new OrTradingRule(rules);
    }

    /**
     * 配置 SMC 动态出场规则的参数（从 DB 配置映射）
     */
    private void configureSmcExitRule(SmcDynamicExitRule rule, ExitRulesConfigDTO.SmcConfig smc, ExitRulesConfigDTO.ReferenceConfig ref) {
        String stopPeriod = ref.getStopStructurePeriod() != null ? ref.getStopStructurePeriod() : "15";
        String targetPeriod = ref.getTargetPeriod() != null ? ref.getTargetPeriod() : stopPeriod;

        rule.setTargetPeriod(targetPeriod);
        rule.setStopLossPeriod(stopPeriod);
        rule.setStructureBreakPeriod(stopPeriod);
        rule.setUseStructureBreak(smc.getPassiveExit().isEnabled());
        rule.setUsePremiumDiscountExit(false);
        rule.setUseTargets(true);

        // 主动止盈配置
        var atp = smc.getActiveTakeProfit();
        if (atp != null && atp.isEnabled()) {
            rule.setActiveTakeProfitEnabled(true);
            if (atp.getOb15m() != null) rule.setAtpOb15mPercent(atp.getOb15m().getClosePercent());
            if (atp.getOb1h() != null) rule.setAtpOb1hPercent(atp.getOb1h().getClosePercent());
            if (atp.getHigher() != null) {
                rule.setAtpHigherPercent(atp.getHigher().getClosePercent());
                rule.setAtpHigherPeriod(atp.getHigher().getPeriod());
            }
        }

        // 初始止损偏移（Initial Stop Offset）
        var iso = smc.getInitialStopOffset();
        if (iso.isEnabled()) {
            rule.setInitialStopOffsetEnabled(true);
            if ("points".equalsIgnoreCase(iso.getMode())) {
                if (iso.getPoints() > 0) rule.setInitialStopOffsetPoints(iso.getPoints());
            } else {
                if (iso.getPercent() > 0) rule.setInitialStopOffsetPercent(iso.getPercent());
            }
        }
    }

    private String[] v2EquityToJson(List<BacktestResult.EquityPoint> equityCurve, BarSeries series) {
        try {
            ArrayNode equityArray = OBJECT_MAPPER.createArrayNode();
            ArrayNode drawdownArray = OBJECT_MAPPER.createArrayNode();
            double peak = Double.NEGATIVE_INFINITY;

            for (BacktestResult.EquityPoint ep : equityCurve) {
                int idx = ep.getIndex();
                double equity = ep.getEquity().doubleValue();
                long time = 0;
                if (idx >= 0 && idx < series.getBarCount()) {
                    time = series.getBar(idx).getEndTime().toEpochMilli();
                }

                ObjectNode eqNode = OBJECT_MAPPER.createObjectNode();
                eqNode.put("time", time);
                eqNode.put("equity", equity);
                equityArray.add(eqNode);

                peak = Math.max(peak, equity);
                double dd = peak > 0 ? (equity - peak) / peak : 0.0;
                ObjectNode ddNode = OBJECT_MAPPER.createObjectNode();
                ddNode.put("time", time);
                ddNode.put("drawdown", dd);
                drawdownArray.add(ddNode);
            }

            return new String[]{
                    OBJECT_MAPPER.writeValueAsString(equityArray),
                    OBJECT_MAPPER.writeValueAsString(drawdownArray)
            };
        } catch (Exception e) {
            log.error("转换V2权益曲线到JSON失败", e);
            return new String[]{"[]", "[]"};
        }
    }

    // ==================== 内部辅助方法（拆分） ====================

    private void failTask(String taskId, String message) {
        backtestTaskDetailService.updateTaskStatus(taskId, "FAILED", message);
        webSocketService.sendTaskFailed(taskId, message);
    }

    private boolean isCancelled(String taskId) {
        return isCurrentTaskCancelled();
    }

    private void cancelTask(String taskId) {
        log.info("回测被用户手动停止: taskId={}", taskId);
        backtestTaskDetailService.updateTaskStatus(taskId, "CANCELLED", "用户手动停止");
        webSocketService.sendTaskStatusUpdate(taskId, "CANCELLED", 0, "回测已被用户手动停止");
    }

    private void tryGenerateReport(String taskId) {
        try {
            if (backtestReportService != null) {
                log.info("准备生成回测报告，taskId: {}", taskId);
                BacktestReportDTO report = backtestReportService.generateReport(taskId);
                if (report != null) log.info("回测报告生成成功: taskId={}, title={}", taskId, report.getTitle());
            }
        } catch (Exception e) {
            log.error("回测报告生成异常: taskId={}", taskId, e);
        }
    }

    private RobotConfig resolveRobotConfig(String robotId) {
        String strategyBean = DEFAULT_STRATEGY;
        String interval = DEFAULT_INTERVAL;
        String accountId = null;
        String strategyId = null;
        String exchange = null;
        BigDecimal totalEquity = null;
        Double leverage = null;
        BigDecimal allocatedCapital = null;

        if (robotId != null && !robotId.isBlank() && tradingBotService != null && strategyService != null) {
            try {
                TradingBot bot = tradingBotService.getByBotId(robotId);
                if (bot != null) {
                    log.info("成功获取机器人信息: robotId={}, botName={}", robotId, bot.getBotName());
                    accountId = bot.getAccountId();
                    totalEquity = bot.getCurrentCapital();
                    allocatedCapital = bot.getAllocatedCapital();
                    exchange = bot.getExchange();
                    leverage = extractLeverage(bot.getConfiguration());

                    if (bot.getStrategyId() != null) {
                        var strategyInfo = strategyService.getByStrategyId(bot.getStrategyId());
                        if (strategyInfo != null) {
                            strategyBean = strategyInfo.getClassName() != null ? strategyInfo.getClassName() : strategyBean;
                            interval = strategyInfo.getTimeFrame() != null ? strategyInfo.getTimeFrame() : interval;
                            strategyId = strategyInfo.getStrategyId();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("查询策略信息失败: robotId={}, 使用默认策略", robotId, e);
            }
        }
        return new RobotConfig(accountId, strategyBean, interval, strategyId, exchange, totalEquity, leverage, allocatedCapital);
    }

    private Double extractLeverage(String configJson) {
        if (configJson == null || configJson.isBlank()) return null;
        try {
            Map<String, Object> cfg = OBJECT_MAPPER.readValue(configJson, Map.class);
            Object lev = cfg.get("leverage");
            if (lev instanceof Number) {
                double v = ((Number) lev).doubleValue();
                return v > 0 ? v : null;
            }
            if (lev instanceof String) {
                double v = Double.parseDouble(((String) lev).trim());
                return v > 0 ? v : null;
            }
        } catch (Exception e) {
            log.warn("解析机器人杠杆配置失败: {}", e.getMessage());
        }
        return null;
    }

    private long[] resolveTimeRange(BacktestRequest request) {
        if (request.getStartTime() != null && request.getEndTime() != null && request.getStartTime() < request.getEndTime()) {
            return new long[]{request.getStartTime(), request.getEndTime()};
        } else if (request.getDays() != null && request.getDays() > 0) {
            long end = System.currentTimeMillis();
            long start = end - TimeUnit.DAYS.toMillis(request.getDays());
            return new long[]{start, end};
        } else {
            throw new IllegalArgumentException("请提供时间范围（startTime 与 endTime）或回测天数（days）");
        }
    }

    private TradingStrategyParams buildTradingStrategyParams(BacktestRequest request, String taskId,
                                                             RobotConfig robotCfg, long startTime, long endTime) {
        TradingStrategyParams.TradingStrategyParamsBuilder builder = TradingStrategyParams.builder()
                .symbol(request.getCoinId().toUpperCase())
                .strategyBeanName(robotCfg.strategyBeanName())
                .interval(robotCfg.interval())
                .testMode(true)
                .startTime(startTime)
                .endTime(endTime)
                .leverage(request.getLeverage() != null ? request.getLeverage().intValue() : 1)
                .strategyId(robotCfg.strategyId())
                .robotId(request.getRobotId())
                .amount(robotCfg.allocatedCapital() != null ? robotCfg.allocatedCapital() : BigDecimal.ZERO)
                .commissionRate(request.getCommissionRate() != null ? BigDecimal.valueOf(request.getCommissionRate() / 100.0) : null)
                .testReportId(taskId);

        applyAddPositionConfig(builder, request.getRobotId(), robotCfg.strategyId());

        if (robotCfg.accountId() != null) builder.accountId(robotCfg.accountId());
        if (robotCfg.exchange() != null) builder.memberPlatform(Exchange.valueOf(robotCfg.exchange()));
        if (request.getPositionAdjusterId() != null && !request.getPositionAdjusterId().isBlank()) {
            builder.additionalParams(Map.of("positionAdjusterId", request.getPositionAdjusterId()));
        }
        return builder.build();
    }

    private void applyAddPositionConfig(TradingStrategyParams.TradingStrategyParamsBuilder builder,
                                        String robotId, String strategyId) {
        Map<String, Object> config = new HashMap<>();

        if (strategyFactory != null) {
            Map<String, Object> db = strategyFactory.loadAddPositionConfig(strategyId);
            if (db != null) config.putAll(db);
        }

        if (robotId != null && tradingBotService != null) {
            try {
                TradingBot bot = tradingBotService.getByBotId(robotId);
                if (bot != null && bot.getConfiguration() != null) {
                    Map<String, Object> cfg = OBJECT_MAPPER.readValue(bot.getConfiguration(), Map.class);
                    for (String key : Arrays.asList("allowAddPosition", "addPosOnProfitPct", "addPosOnLossPct",
                            "addPosOnProfitGapPct", "addPosOnLossGapPct")) {
                        if (cfg.containsKey(key)) config.put(key, cfg.get(key));
                    }
                }
            } catch (Exception ignored) {}
        }

        // 设置到 builder
        Boolean allow = toBoolean(config.get("allowAddPosition"));
        if (allow != null) builder.allowAddPosition(allow);
        builder.addPosOnProfitPct(normalizePercent(toDouble(config.get("addPosOnProfitPct"))));
        builder.addPosOnLossPct(normalizePercent(toDouble(config.get("addPosOnLossPct"))));
        Double gapProfit = normalizePercent(toDouble(config.get("addPosOnProfitGapPct")));
        Double gapLoss = normalizePercent(toDouble(config.get("addPosOnLossGapPct")));
        builder.addPosOnProfitGapPct(gapProfit);
        builder.addPosOnLossGapPct(gapLoss);
    }

    private Boolean toBoolean(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private Double toDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Double normalizePercent(Double v) {
        if (v == null || v <= 0) return null;
        return v >= 1 ? v / 100.0 : v;
    }


    private int findBarIndexByTime(BarSeries series, Date orderTime) {
        if (series == null || orderTime == null) return -1;
        long millis = orderTime.getTime();
        for (int i = 0; i < series.getBarCount(); i++) {
            var bar = series.getBar(i);
            if (bar != null && bar.getBeginTime() != null && bar.getBeginTime().toEpochMilli() >= millis) {
                return i;
            }
        }
        return series.getEndIndex();
    }

    private PerformanceMetrics calculateTraditionalMetrics(
            List<BacktestResult.TradeRecord> tradeRecords, String drawdownSeriesJson,
            double initialAmount, Long startTime, Long endTime, BarSeries series,
            double averageHoldingPeriod) {

        List<BacktestResult.TradeRecord> closed = tradeRecords.stream()
                .filter(tr -> tr.getPnl().abs().doubleValue() > 1e-10)
                .collect(Collectors.toList());

        int total = closed.size();
        long wins = closed.stream().filter(tr -> tr.getPnl().signum() > 0).count();
        double winRate = total > 0 ? (double) wins / total : 0.0;

        double totalPnl = closed.stream().mapToDouble(tr -> tr.getPnl().doubleValue()).sum();
        double totalReturn = initialAmount > 0 ? totalPnl / initialAmount : 0.0;
        double totalCharge = closed.stream().mapToDouble(tr -> tr.getFee().doubleValue()).sum();

        double sumWin = closed.stream().filter(tr -> tr.getPnl().signum() > 0).mapToDouble(tr -> tr.getPnl().doubleValue()).sum();
        double sumLoss = closed.stream().filter(tr -> tr.getPnl().signum() < 0).mapToDouble(tr -> tr.getPnl().doubleValue()).sum();
        long losses = total - wins;
        double profitLossRatio = 0.0;
        if (wins > 0 && losses > 0) {
            double avgWin = sumWin / wins;
            double avgLoss = Math.abs(sumLoss) / losses;
            profitLossRatio = avgLoss > 0 ? avgWin / avgLoss : 0.0;
        }

        double maxDrawdown = parseMaxDrawdown(drawdownSeriesJson);

        double annualReturn = computeAnnualReturn(startTime, endTime, totalReturn);
        double volatility = computeAnnualizedVolatilityFromSeries(series);
        double sortinoRatio = (annualReturn != 0 && volatility != 0) ? annualReturn / volatility : 0.0;

        double avgWin = wins > 0 ? sumWin / wins : 0.0;
        double avgLoss = losses > 0 ? Math.abs(sumLoss) / losses : 0.0;
        double largestWin = closed.stream().filter(tr -> tr.getPnl().signum() > 0).mapToDouble(tr -> tr.getPnl().doubleValue()).max().orElse(0.0);
        double largestLoss = closed.stream().filter(tr -> tr.getPnl().signum() < 0).mapToDouble(tr -> tr.getPnl().doubleValue()).min().orElse(0.0);

        // 按退出索引排序已平仓记录，计算最大连续盈利/亏损
        int maxConsecutiveWins = 0, maxConsecutiveLosses = 0;
        int curWins = 0, curLosses = 0;
        List<BacktestResult.TradeRecord> sortedClosed = closed.stream()
                .sorted(Comparator.comparingInt(BacktestResult.TradeRecord::getExitIndex))
                .collect(Collectors.toList());
        for (BacktestResult.TradeRecord tr : sortedClosed) {
            if (tr.getPnl().signum() > 0) {
                curWins++;
                curLosses = 0;
                maxConsecutiveWins = Math.max(maxConsecutiveWins, curWins);
            } else {
                curLosses++;
                curWins = 0;
                maxConsecutiveLosses = Math.max(maxConsecutiveLosses, curLosses);
            }
        }

        return PerformanceMetrics.builder()
                .totalReturn(totalReturn)
                .maxDrawdown(maxDrawdown)
                .winRate(winRate)
                .totalTrades(total)
                .profitableTrades(wins)
                .profitLossRatio(profitLossRatio)
                .initialAmount(initialAmount)
                .totalCost(totalCharge)
                .annualReturn(annualReturn)
                .volatility(volatility)
                .sortinoRatio(sortinoRatio)
                .averageWin(avgWin)
                .averageLoss(avgLoss)
                .largestWinTrade(largestWin)
                .largestLossTrade(Math.abs(largestLoss))
                .maxConsecutiveWins(maxConsecutiveWins)
                .maxConsecutiveLosses(maxConsecutiveLosses)
                .averageHoldingPeriod(averageHoldingPeriod)
                .build();
    }

    private double parseMaxDrawdown(String json) {
        try {
            JsonNode arr = OBJECT_MAPPER.readTree(json);
            if (arr.isArray()) {
                double max = 0.0;
                for (JsonNode node : arr) {
                    double dd = Math.abs(node.get("drawdown").asDouble());
                    if (dd > max) max = dd;
                }
                return max;
            }
        } catch (Exception e) {
            log.warn("解析回撤序列失败: {}", e.getMessage());
        }
        return 0.0;
    }

    private double computeAnnualReturn(Long start, Long end, double totalReturn) {
        if (start == null || end == null || start >= end) return 0.0;
        long days = (end - start) / (24 * 60 * 60 * 1000L);
        if (days <= 0 || totalReturn == 0) return 0.0;
        double years = days / 365.0;
        return Math.pow(1.0 + totalReturn, 1.0 / years) - 1.0;
    }

    private double computeAnnualizedVolatilityFromSeries(BarSeries series) {
        if (series == null || series.getBarCount() < 2) return 0.0;
        int n = series.getBarCount();
        double sum = 0.0;
        int count = 0;
        for (int i = 1; i < n; i++) {
            double prev = series.getBar(i - 1).getClosePrice().doubleValue();
            double cur = series.getBar(i).getClosePrice().doubleValue();
            if (prev > 0 && cur > 0) {
                sum += Math.log(cur / prev);
                count++;
            }
        }
        if (count < 1) return 0.0;
        double mean = sum / count;
        double varSum = 0.0;
        for (int i = 1; i < n; i++) {
            double prev = series.getBar(i - 1).getClosePrice().doubleValue();
            double cur = series.getBar(i).getClosePrice().doubleValue();
            if (prev > 0 && cur > 0) {
                double logRet = Math.log(cur / prev);
                varSum += Math.pow(logRet - mean, 2);
            }
        }
        double variance = varSum / count;
        return Math.sqrt(variance) * Math.sqrt(365);
    }

    private double resolveMetricsBaseAmount(BacktestRequest request) {
        if (request.getRobotId() != null && !request.getRobotId().isBlank() && tradingBotService != null) {
            try {
                TradingBot bot = tradingBotService.getByBotId(request.getRobotId());
                if (bot != null && bot.getCurrentCapital() != null) {
                    return bot.getCurrentCapital().doubleValue();
                }
            } catch (Exception e) {
                log.warn("获取机器人总资金失败: {}", e.getMessage());
            }
        }
        return request.getInitialAmount() != null ? request.getInitialAmount() : 0.0;
    }

    // ----- 缓存清理 -----

    private void cleanupIdempotentKeys(String robotId) {
        if (redisCache == null || robotId == null || robotId.isBlank()) return;
        try {
            String pattern = String.format(IDEMPOTENT_KEY_PATTERN, robotId);
            List<Object> keys = redisCache.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                log.info("清理幂等拦截Redis key: pattern={}, count={}", pattern, keys.size());
                redisCache.multiDel(keys);
            }
        } catch (Exception e) {
            log.warn("清理幂等拦截Redis key异常: robotId={}", robotId, e);
        }
    }

    // ----- DTO 构建 -----

    private BacktestTaskDTO createBacktestTaskDTO(BacktestRequest request) {
        java.time.LocalDate startDate, endDate;
        if (request.getStartTime() != null && request.getEndTime() != null) {
            startDate = java.time.Instant.ofEpochMilli(request.getStartTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            endDate = java.time.Instant.ofEpochMilli(request.getEndTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        } else {
            int days = request.getDays() != null && request.getDays() > 0 ? request.getDays() : 30;
            startDate = java.time.LocalDate.now().minusDays(days);
            endDate = java.time.LocalDate.now();
        }

        String effectiveStrategy = request.getStrategyType() != null && !request.getStrategyType().isBlank()
                ? request.getStrategyType() : DEFAULT_STRATEGY;
        String coinId = request.getCoinId() != null ? request.getCoinId() : "UNKNOWN";

        if (request.getRobotId() != null && tradingBotService != null) {
            TradingBot bot = tradingBotService.getByBotId(request.getRobotId());
            if (bot != null) {
                if (bot.getCurrentCapital() != null) request.setInitialAmount(bot.getCurrentCapital().doubleValue());
                if (bot.getStrategyId() != null && !bot.getStrategyId().isBlank()) {
                    effectiveStrategy = bot.getStrategyId();
                    if (strategyFactory != null) {
                        String className = strategyFactory.resolveStrategyClassName(bot.getStrategyId());
                        request.setStrategyType(className != null ? className : bot.getStrategyId());
                    }
                    request.setStrategyId(bot.getStrategyId());
                }
                Double lev = extractLeverage(bot.getConfiguration());
                if (lev != null) request.setLeverage(lev);
            }
        }

        return BacktestTaskDTO.builder()
                .taskId(generateTaskId())
                .strategyName(effectiveStrategy)
                .strategyCode(effectiveStrategy)
                .strategyVersion("1.0")
                .startDate(startDate)
                .endDate(endDate)
                .initialCapital(BigDecimal.valueOf(request.getInitialAmount() != null ? request.getInitialAmount() : 0D))
                .currency(USDT)
                .benchmark(coinId + "-USDT")
                .universe(new String[]{coinId})
                .config(convertRequestToConfigMap(request))
                .robotId(request.getRobotId() != null && !request.getRobotId().isBlank() ? request.getRobotId() : "2001")
                .memberId("1665908516499693568")
                .accountId(1768185450252304387L)
                .leverage(request.getLeverage() != null ? request.getLeverage().intValue() : 1)
                .status("PENDING")
                .progress(0)
                .createdBy(SYSTEM_CREATOR)
                .createdAt(java.time.LocalDateTime.now())
                .partitionKey(java.time.LocalDate.now())
                .build();
    }

    private Map<String, Object> convertRequestToConfigMap(BacktestRequest request) {
        Map<String, Object> config = new HashMap<>();
        config.put("strategyType", request.getStrategyType());
        config.put("strategyId", request.getStrategyId());
        config.put("coinId", request.getCoinId());
        if (request.getStartTime() != null) config.put("startTime", request.getStartTime());
        if (request.getEndTime() != null) config.put("endTime", request.getEndTime());
        if (request.getDays() != null) config.put("days", request.getDays());
        config.put("initialAmount", request.getInitialAmount());
        config.put("leverage", request.getLeverage());
        config.put("isContractTrading", request.getIsContractTrading());
        config.put("commissionRate", request.getCommissionRate());
        config.put("slippageRate", request.getSlippageRate());
        config.put("executionMatchPolicy", request.getExecutionMatchPolicy());
        config.put("backtestType", request.getBacktestType() != null ? request.getBacktestType().name() : BacktestRequest.BacktestType.TRADITIONAL_BACKTEST_NEW.name());
        if (request.getRobotId() != null && !request.getRobotId().isBlank()) {
            config.put("robotId", request.getRobotId());
        }
        if (request.getPositionAdjusterId() != null) {
            config.put("positionAdjusterId", request.getPositionAdjusterId());
        }
        return config;
    }

    private BacktestRequest parseTaskConfigFromMap(Map<String, Object> configMap) {
        if (configMap == null) return null;
        try {
            BacktestRequest.BacktestRequestBuilder builder = BacktestRequest.builder();
            Optional.ofNullable(configMap.get("strategyType"))
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .ifPresent(builder::strategyType);
            Optional.ofNullable(configMap.get("strategyId")).map(Object::toString).ifPresent(builder::strategyId);
            Optional.ofNullable(configMap.get("coinId")).map(Object::toString).ifPresent(builder::coinId);
            Optional.ofNullable(configMap.get("startTime")).map(v -> ((Number) v).longValue()).ifPresent(builder::startTime);
            Optional.ofNullable(configMap.get("endTime")).map(v -> ((Number) v).longValue()).ifPresent(builder::endTime);
            Optional.ofNullable(configMap.get("days")).map(v -> ((Number) v).intValue()).ifPresent(builder::days);
            Optional.ofNullable(configMap.get("initialAmount")).map(v -> ((Number) v).doubleValue()).ifPresent(builder::initialAmount);
            Optional.ofNullable(configMap.get("leverage")).map(v -> ((Number) v).doubleValue()).ifPresent(builder::leverage);
            Optional.ofNullable(configMap.get("isContractTrading")).map(v -> (Boolean) v).ifPresent(builder::isContractTrading);
            Optional.ofNullable(configMap.get("commissionRate")).map(v -> ((Number) v).doubleValue()).ifPresent(builder::commissionRate);
            Optional.ofNullable(configMap.get("slippageRate")).map(v -> ((Number) v).doubleValue()).ifPresent(builder::slippageRate);
            Optional.ofNullable(configMap.get("executionMatchPolicy")).map(Object::toString).ifPresent(builder::executionMatchPolicy);
            Optional.ofNullable(configMap.get("backtestType"))
                    .map(Object::toString)
                    .map(s -> {
                        try { return BacktestRequest.BacktestType.valueOf(s); } catch (Exception e) { return BacktestRequest.BacktestType.TRADITIONAL_BACKTEST_NEW; }
                    })
                    .ifPresent(builder::backtestType);
            Optional.ofNullable(configMap.get("robotId")).map(Object::toString).ifPresent(builder::robotId);
            Optional.ofNullable(configMap.get("positionAdjusterId")).map(Object::toString).ifPresent(builder::positionAdjusterId);
            return builder.build();
        } catch (Exception e) {
            log.error("从Map解析任务配置失败", e);
            return null;
        }
    }



    private BacktestTaskDTO getBacktestTask(String taskId) {
        return backtestTaskDetailService.getTaskDetail(taskId);
    }

    // ==================== 内部记录类 ====================

    private record RobotConfig(String accountId, String strategyBeanName, String interval,
                               String strategyId, String exchange, BigDecimal totalEquity, Double leverage,
                               BigDecimal allocatedCapital) {}
}