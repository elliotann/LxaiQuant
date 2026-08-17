package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.ContractSpecUtils;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.engine.data.mtf.ResampleMultiTimeFrameProvider;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import com.chain.ai.trade.engine.strategy.DynamicRiskEngineDTO;
import com.chain.ai.trade.engine.strategy.ExitRulesConfigDTO;
import com.chain.ai.trade.engine.strategy.StrategyFactory;
import com.chain.ai.trade.engine.strategy.core.rule.MultiDirectionEntryRule;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.engine2.backtest.BacktestConfig;
import com.chain.ai.trade.engine2.core.cost.CostModel;
import com.chain.ai.trade.engine2.core.cost.MakerTakerCostModel;
import com.chain.ai.trade.engine2.persistence.RealtimeAsyncGateway;
import com.chain.ai.trade.engine2.rules.DefaultScaleInRule;
import com.chain.ai.trade.engine2.rules.ScaleInRule;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.engine2.rules.base.*;
import com.chain.ai.trade.engine2.rules.composite.OrTradingRule;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import com.chain.ai.trade.engine2.strategy.impl.SignalScriptStrategy;
import com.chain.ai.trade.order.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile({"dev", "live", "prod"})
@RequiredArgsConstructor
public class LiveEngineFactory {

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TradeOrderCloseMapper tradeOrderCloseMapper;
    private final TradeOrderCloseItemMapper tradeOrderCloseItemMapper;
    private final ITradeSignalService tradeSignalService;
    private final IStrategyService strategyService;
    private final ICandlestickService candlestickService;
    private final ITechnicalSignalService technicalSignalService;
    private final StrategyFactory strategyFactory;
    private final RedisCache redisCache;
    private final LiveExecutionHandler executionHandler;
    private final ITradingBotService tradingBotService;

    public LiveEngine createEngine(TradingBot bot) {
        log.info("开始装配引擎: botId={}, symbol={}", bot.getBotId(), bot.getTradingPair());

        Strategy strategy = loadStrategy(bot);
        if (strategy == null) {
            throw new IllegalArgumentException("策略不存在: " + bot.getStrategyId());
        }

        CandlestickIntervalEnum intervalEnum = parseInterval(strategy);
        CandlestickIntervalEnum okxInterval = intervalEnum.toOkxInterval();
        String intervalCode = intervalEnum.getCode();

        int warmupPeriod = getWarmupPeriod(strategy);
        Exchange exchange = Exchange.valueOf(bot.getExchange());
        BarSeries series = loadInitialBarSeries(bot.getTradingPair(), okxInterval, warmupPeriod, exchange);

        MultiTimeFrameProvider mtfProvider = new ResampleMultiTimeFrameProvider(
                series, okxInterval, candlestickService, exchange, bot.getTradingPair(), 1000);

        SignalCacheManager cacheManager = buildSignalCache(series, bot);
        MultiDirectionEntryRule entryRule = new MultiDirectionEntryRule(series, cacheManager);
        TradingRule exitRule = buildExitRule(series, bot, strategy, mtfProvider, cacheManager);

        ScriptStrategy scriptStrategy = new SignalScriptStrategy(
                strategy.getStrategyId(),
                bot.getTradingPair(),
                series,
                cacheManager,
                entryRule,
                exitRule,
                buildScaleInRule(bot, strategy, series)
        );

        RealtimeConfig config = buildConfig(bot, intervalCode);
        RealtimeAsyncGateway gateway = buildGateway(bot, config);
        RealtimeContext context = new RealtimeContext(config, executionHandler, gateway);

        LiveEngine engine = new LiveEngine(series, scriptStrategy, config, context, gateway);

        // 创建止损预计算器并注入引擎（供以损定量使用）
        createStopPreviewerIfNeeded(engine, bot, strategy, mtfProvider);

        // 注入信号频率控制器
        injectSignalFrequency(engine, strategy);

        engine.init();

        log.info("引擎装配完成: botId={}, symbol={}, interval={}, bars={}, warmup={}",
                bot.getBotId(), bot.getTradingPair(), intervalCode,
                series.getBarCount(), warmupPeriod);

        return engine;
    }

    private Strategy loadStrategy(TradingBot bot) {
        if (bot.getStrategyId() == null || bot.getStrategyId().isBlank()) {
            return null;
        }
        return strategyService.getByStrategyId(bot.getStrategyId());
    }

    private CandlestickIntervalEnum parseInterval(Strategy strategy) {
        String timeFrame = strategy.getTimeFrame();
        CandlestickIntervalEnum intervalEnum = CandlestickIntervalEnum.fromCodeValue(timeFrame);
        if (intervalEnum == null) {
            throw new IllegalArgumentException("无法解析时间框架: " + timeFrame);
        }
        return intervalEnum;
    }

    private int getWarmupPeriod(Strategy strategy) {
        return 50;
    }

    private BarSeries loadInitialBarSeries(String symbol, CandlestickIntervalEnum interval,
                                           int warmupPeriod, Exchange exchange) {
        int requiredBars = Math.min(Math.max(warmupPeriod + 50, 200), 3000);

        KlineParam param = KlineParam.builder()
                .symbol(symbol)
                .klineInterval(interval)
                .exchange(exchange)
                .size(requiredBars)
                .build();

        List<Candlestick> klines = candlestickService.getKlines(param);
        if (klines == null || klines.isEmpty()) {
            throw new IllegalStateException("历史K线数据为空: " + symbol);
        }

        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        log.info("历史K线加载完成: symbol={}, interval={}, loaded={}, required={}",
                symbol, interval.getCode(), series.getBarCount(), requiredBars);

        return series;
    }

    private SignalCacheManager buildSignalCache(BarSeries series, TradingBot bot) {
        if (technicalSignalService == null) {
            return new SignalCacheManager(null);
        }
        SignalCacheManager cm = new SignalCacheManager(technicalSignalService);
        try {
            Instant start = series.getFirstBar().getBeginTime();
            Instant end = series.getLastBar().getEndTime();
            cm.loadSignals(
                    LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                    LocalDateTime.ofInstant(end, ZoneOffset.UTC),
                    bot.getTradingPair(),
                    bot.getStrategyId(),
                    bot.getExchange()
            );
        } catch (Exception e) {
            log.warn("信号缓存加载失败: botId={}", bot.getBotId(), e);
        }
        return cm;
    }

    /**
     * 创建 SMC 止损预计算器（供以损定量开仓前预计算止损位）。
     * <p>优先级：结构止盈止损（structureStopProfit.enabled=true）> 固定百分比止盈止损。</p>
     */
    private void createStopPreviewerIfNeeded(LiveEngine engine, TradingBot bot, Strategy strategy,
                                              MultiTimeFrameProvider mtfProvider) {
        try {
            ExitRulesConfigDTO exitConfig = strategyFactory.loadExitRulesConfig(
                    strategy.getStrategyId(), bot.getBotId());
            if (exitConfig == null) return;

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

            var ss = exitConfig.getStructureStopProfit();
            if (ss != null && ss.isEnabled()) {
                // 结构止盈止损启用：优先 SMC 结构计算
                if (mtfProvider == null) return;
                var dsl = ss.getDynamicStopLoss();
                String stopPeriod = String.valueOf(dsl.getDailyPeriod());
                double buffer = toDecimalPct(dsl.getDailyBuffer());
                var ref = ss.getReference();
                String tpPeriod = ref != null && ref.getTakeProfitPeriod() > 0
                        ? String.valueOf(ref.getTakeProfitPeriod())
                        : stopPeriod;

                SmcStopPreviewer stopPreviewer =
                        new SmcStopPreviewer(
                                mtfProvider, bot.getTradingPair(), stopPeriod, buffer, tpPeriod, true, fixedSlPct, fixedTpPct);
                engine.setStopPreviewer(stopPreviewer);
                log.info("止损预计算器已创建（结构止盈止损优先）: stopPeriod={}, buffer={}, tpPeriod={}, fixedSl={}, fixedTp={}",
                        stopPeriod, buffer, tpPeriod, fixedSlPct, fixedTpPct);
            } else if (fixedSlPct > 0 || fixedTpPct > 0) {
                // 结构止盈止损未启用，仅固定百分比止盈止损兜底（不需要 mtfProvider）
                SmcStopPreviewer stopPreviewer =
                        new SmcStopPreviewer(
                                null, bot.getTradingPair(), "15", 0, null, false, fixedSlPct, fixedTpPct);
                engine.setStopPreviewer(stopPreviewer);
                log.info("止损预计算器已创建（固定百分比止盈止损兜底）: fixedSl={}, fixedTp={}", fixedSlPct, fixedTpPct);
            }
        } catch (Exception e) {
            log.warn("创建止损预计算器失败，以损定量将降级: {}", e.getMessage());
        }
    }

    private TradingRule buildExitRule(BarSeries series, TradingBot bot, Strategy strategy,
                                      MultiTimeFrameProvider mtfProvider, SignalCacheManager signalCache) {
        ExitRulesConfigDTO exitConfig = strategyFactory.loadExitRulesConfig(
                strategy.getStrategyId(), bot.getBotId());

        if (exitConfig == null) {
            log.warn("无出场规则配置: botId={}", bot.getBotId());
            return null;
        }

        List<TradingRule> rules = new ArrayList<>();

        if (exitConfig.getSignalReversal() != null && exitConfig.getSignalReversal().isEnabled()) {
            rules.add(new SignalReversalRule(signalCache));
        }

        if (exitConfig.getFixedPercentStopLoss() != null && exitConfig.getFixedPercentStopLoss().isEnabled()) {
            rules.add(new FixedStopLossRule(exitConfig.getFixedPercentStopLoss().getPercent()));
        }

        if (exitConfig.getFixedPercentTakeProfit() != null && exitConfig.getFixedPercentTakeProfit().isEnabled()) {
            rules.add(new FixedTakeProfitRule(exitConfig.getFixedPercentTakeProfit().getPercent()));
        }

        if (exitConfig.getSmcExit() != null && exitConfig.getSmcExit().isEnabled() && mtfProvider != null) {
            try {
                var smcExit = exitConfig.getSmcExit();
                var ref = smcExit.getReference();

                SmcDynamicExitRule smcLong = new SmcDynamicExitRule(mtfProvider, bot.getTradingPair(), com.chain.ai.trade.common.entity.constants.SignalType.LONG);
                configureSmcExitRule(smcLong, smcExit, ref);
                rules.add(smcLong);

                SmcDynamicExitRule smcShort = new SmcDynamicExitRule(mtfProvider, bot.getTradingPair(), com.chain.ai.trade.common.entity.constants.SignalType.SHORT);
                configureSmcExitRule(smcShort, smcExit, ref);
                rules.add(smcShort);

                log.info("SMC 动态出场规则已加载: symbol={}", bot.getTradingPair());
            } catch (Exception e) {
                log.error("创建 SMC 动态出场规则失败", e);
            }
        }

        var ss = exitConfig.getStructureStopProfit();
        if (ss != null && ss.isEnabled() && mtfProvider != null) {
            try {
                List<TradingRule> structuredRules = strategyFactory.buildStructureStopProfitRules(
                        mtfProvider, bot.getTradingPair(), exitConfig);
                rules.addAll(structuredRules);
                log.info("结构止盈止损规则已加载: symbol={}", bot.getTradingPair());
            } catch (Exception e) {
                log.error("创建结构止盈止损规则失败", e);
            }
        }

        // 动态风控引擎（防守线移动止损 / 进攻线移动止盈）
        DynamicRiskEngineDTO dre = strategyFactory.loadDynamicRiskEngine(strategy.getStrategyId());
        if (dre != null && mtfProvider != null) {
            try {
                List<TradingRule> dreRules = strategyFactory.buildDynamicRiskEngineRules(
                        mtfProvider, bot.getTradingPair(), dre);
                rules.addAll(dreRules);
                log.info("动态风控引擎出场规则已加载: symbol={}", bot.getTradingPair());
            } catch (Exception e) {
                log.error("创建动态风控引擎出场规则失败", e);
            }
        }

        if (rules.isEmpty()) {
            log.warn("无出场规则: botId={}", bot.getBotId());
            return null;
        }
        if (rules.size() == 1) {
            return rules.get(0);
        }
        return new OrTradingRule(rules);
    }

    /**
     * 构建加仓规则，从机器人配置 JSON 中解析加仓参数。
     * 若未配置加仓参数，DefaultScaleInRule 将返回 null，等效于不加仓。
     */
    private ScaleInRule buildScaleInRule(TradingBot bot, Strategy strategy, BarSeries series) {
        BacktestConfig backtestConfig = BacktestConfig.builder().build();
        Map<String, Object> cfg = new HashMap<>();

        // 从机器人配置 JSON 中提取加仓参数
        if (bot.getConfiguration() != null) {
            try {
                Map<String, Object> botCfg = new ObjectMapper().readValue(bot.getConfiguration(), Map.class);
                for (String key : new String[]{"addPosOnProfitPct", "addPosOnLossPct",
                        "addPosOnProfitGapPct", "addPosOnLossGapPct"}) {
                    if (botCfg.containsKey(key)) cfg.put(key, botCfg.get(key));
                }
            } catch (Exception e) {
                log.warn("解析机器人加仓配置失败: botId={}", bot.getBotId(), e);
            }
        }

        // 应用参数到 BacktestConfig（百分比值已按小数存储，直接使用）
        if (cfg.containsKey("addPosOnProfitPct")) {
            backtestConfig.setAddPosOnProfitPct(toDouble(cfg.get("addPosOnProfitPct")));
        }
        if (cfg.containsKey("addPosOnLossPct")) {
            backtestConfig.setAddPosOnLossPct(toDouble(cfg.get("addPosOnLossPct")));
        }
        if (cfg.containsKey("addPosOnProfitGapPct")) {
            backtestConfig.setAddPosOnProfitGapPct(toDouble(cfg.get("addPosOnProfitGapPct")));
        }
        if (cfg.containsKey("addPosOnLossGapPct")) {
            backtestConfig.setAddPosOnLossGapPct(toDouble(cfg.get("addPosOnLossGapPct")));
        }
        // 最大加仓次数：取策略的 maxPositionCount，默认为 1（即只允许首次开仓，不加仓）
        int maxPositions = strategy.getMaxPositionCount() != null ? strategy.getMaxPositionCount() : 1;
        backtestConfig.setMaxAddPositions(maxPositions);

        return new DefaultScaleInRule(backtestConfig, series);
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void configureSmcExitRule(SmcDynamicExitRule rule, ExitRulesConfigDTO.SmcConfig smc,
                                      ExitRulesConfigDTO.ReferenceConfig ref) {
        String stopPeriod = ref.getStopStructurePeriod() != null ? ref.getStopStructurePeriod() : "15";
        String targetPeriod = ref.getTargetPeriod() != null ? ref.getTargetPeriod() : stopPeriod;

        rule.setTargetPeriod(targetPeriod);
        rule.setStopLossPeriod(stopPeriod);
        rule.setStructureBreakPeriod(stopPeriod);
        rule.setUseStructureBreak(smc.getPassiveExit().isEnabled());
        rule.setUsePremiumDiscountExit(false);
        rule.setUseTargets(true);
    }

    private RealtimeConfig buildConfig(TradingBot bot, String intervalCode) {
        int leverage = parseLeverage(bot);
        BigDecimal initialCapital = bot.getAllocatedCapital() != null
                ? bot.getAllocatedCapital()
                : BigDecimal.valueOf(1000);

        ContractSpec contractSpec = ContractSpecUtils.getContractSpec(
                redisCache,
                Exchange.valueOf(bot.getExchange()),
                bot.getTradingPair()
        );

        return RealtimeConfig.builder()
                .symbol(bot.getTradingPair())
                .exchange(Exchange.valueOf(bot.getExchange()))
                .interval(intervalCode)
                .initialCapital(initialCapital)
                .positionAmount(initialCapital)
                .leverage(leverage)
                .slippage(BigDecimal.valueOf(0.001))
                .contractSpec(contractSpec)
                .costModel(defaultCostModel())
                .warmupPeriod(getWarmupPeriod(loadStrategy(bot)))
                .userId(bot.getUserId())
                .accountId(bot.getAccountId())
                .robotId(bot.getBotId())
                .build();
    }

    private RealtimeAsyncGateway buildGateway(TradingBot bot, RealtimeConfig config) {
        return new RealtimeAsyncGateway(
                tradeOrderMapper,
                tradeOrderItemMapper,
                tradeOrderCloseMapper,
                tradeOrderCloseItemMapper,
                tradeSignalService,
                bot.getUserId(),
                bot.getBotId(),
                bot.getAccountId(),
                config.getLeverage(),
                config.getSymbol(),
                config.getInterval(),
                false,
                redisCache,
                tradingBotService
        );
    }

    private int parseLeverage(TradingBot bot) {
        if (bot.getConfiguration() != null && !bot.getConfiguration().isBlank()) {
            try {
                org.json.JSONObject config = new org.json.JSONObject(bot.getConfiguration());
                if (config.has("leverage")) {
                    return config.optInt("leverage", 1);
                }
            } catch (Exception ignored) {
            }
        }
        return 1;
    }

    /**
     * 注入信号频率控制。
     */
    private void injectSignalFrequency(LiveEngine engine, Strategy strategy) {
        try {
            Map<String, Object> positionRisk = strategyFactory.loadPositionRiskConfig(strategy.getStrategyId());
            if (positionRisk == null || positionRisk.isEmpty()) return;

            Object freqEnabled = positionRisk.get("signalFrequencyEnabled");
            boolean enabled = freqEnabled instanceof Boolean && (Boolean) freqEnabled;
            if (!enabled) return;

            String granularity = positionRisk.getOrDefault("signalFrequencyGranularity", "15min").toString();
            String mode = positionRisk.getOrDefault("signalFrequencyMode", "structure_upgrade_exempt").toString();

            engine.initSignalFrequency(enabled, granularity, mode);
            log.info("实盘引擎信号频率控制已启用: granularity={}, mode={}", granularity, mode);
        } catch (Exception e) {
            log.warn("注入信号频率控制失败: {}", e.getMessage());
        }
    }

    /**
     * 将用户配置的百分比值转换为小数（如 0.08 → 0.0008）。
     */
    private static double toDecimalPct(double pct) {
        return pct / 100.0;
    }

    private CostModel defaultCostModel() {
        return new MakerTakerCostModel(
                BigDecimal.valueOf(0.0002),
                BigDecimal.valueOf(0.0005),
                false
        );
    }
}