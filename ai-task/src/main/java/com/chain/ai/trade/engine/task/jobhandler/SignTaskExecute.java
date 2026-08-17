package com.chain.ai.trade.engine.task.jobhandler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.push.SignalMessagePublisher;
import com.chain.ai.trade.common.push.SignalPushMessage;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.service.ai.filter.AiSignalFilterService;
import com.chain.ai.trade.engine.service.ai.filter.dto.AiFilterRequest;
import com.chain.ai.trade.engine.service.ai.filter.dto.AiFilterResult;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureStatistics;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.factory.SignFactory;
import com.chain.ai.trade.engine.signal.feature.SignalFeatureProvider;
import com.chain.ai.trade.engine.signal.service.ISignService;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.chain.ai.trade.common.entity.constants.SignalType.*;

@Slf4j
@Component
public class SignTaskExecute {

    // ---------- 常量 ----------
    private static final String KLINE_SIGN_CACHE_PREFIX = "kline:sign:";
    private static final String KLINE_SIGN_LOCK_PREFIX = "kline:sign:lock:";
    private static final int DEFAULT_KLINE_SIZE = 400;
    private static final long LOCK_EXPIRE_SECONDS = 30;
    private static final long MIN_TTL_MINUTES = 5;
    private static final long MAX_TTL_MINUTES = 2880; // 2 days

    private static final String DIRECTION_LONG = "LONG";
    private static final String DIRECTION_SHORT = "SHORT";

    // 任务参数键名
    private static final String PARAM_INTERVAL = "interval";
    private static final String PARAM_STRATEGY_TYPE = "strategyType";
    private static final String PARAM_SYMBOL = "symbol";
    private static final String PARAM_ROBOT_ID = "robotId";
    private static final String PARAM_EXCHANGE = "exchange";
    private static final String PARAM_SIZE = "size";

    // ---------- 依赖注入 ----------
    @Autowired
    private ICandlestickService candlestickService;
    @Autowired
    private RedisCache redisCache;
    @Autowired
    private AiSignalFilterService aiSignalFilterService;
    @Autowired
    private IStrategyService strategyService;
    @Autowired
    private ITechnicalSignalService technicalSignalService;
    @Autowired
    private SignalFeatureProvider featureProvider;
    @Autowired
    private ITradingBotService tradingBotService;
    @Autowired
    private SignalMessagePublisher signalMessagePublisher;

    // ---------- 任务入口 ----------
    @XxlJob("signTaskExecute")
    public void execute() {
        String param = XxlJobHelper.getJobParam();
        log.info("信号生成任务开始，参数: {}", param);

        SignalContext ctx = parseAndValidateParams(param);
        if (ctx == null) {
            log.warn("参数无效，任务终止");
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // 1. 获取K线数据
            List<Candlestick> candlesticks = fetchKlineData(ctx);
            if (candlesticks.isEmpty()) {
                log.warn("K线数据为空 - 品种: {}, 周期: {}", ctx.symbol, ctx.interval);
                return;
            }

            Candlestick latestKline = candlesticks.get(candlesticks.size() - 1);
            String cacheKey = buildCacheKey(ctx, latestKline);
            String lockKey = KLINE_SIGN_LOCK_PREFIX + cacheKey;

            // 2. 快速失败：已处理过则直接返回（减少锁竞争）
            if (redisCache.hasKey(cacheKey)) {
                log.debug("K线已成功处理过，跳过 - key: {}", cacheKey);
                return;
            }

            // 3. 获取分布式锁
            boolean lockAcquired = redisCache.setIfAbsent(lockKey, System.currentTimeMillis(),
                    LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.debug("其他实例正在处理该K线，跳过 - key: {}", cacheKey);
                return;
            }

            try {
                // 双重检查：锁内再次确认
                if (redisCache.hasKey(cacheKey)) {
                    log.debug("K线已被其他线程处理，跳过 - key: {}", cacheKey);
                    return;
                }

                // 4. 执行信号生成核心流程
                processSignal(ctx, candlesticks, latestKline, cacheKey);

                log.info("信号生成任务完成 - 策略: {}, 品种: {}, 耗时: {}ms",
                        ctx.strategyType, ctx.symbol, System.currentTimeMillis() - startTime);

            } finally {
                redisCache.deleteObject(lockKey);
            }

        } catch (Exception e) {
            log.error("信号生成任务执行异常 - 参数: {}", param, e);
            XxlJobHelper.handleFail("信号生成任务执行失败: " + e.getMessage());
        }
    }

    // ---------- 核心流程 ----------
    private void processSignal(SignalContext ctx, List<Candlestick> candlesticks,
                               Candlestick latestKline, String cacheKey) {
        long t0 = System.currentTimeMillis();

        // 4.1 计算技术信号
        BuyAndSellWeightDto result = calculateSignal(ctx, candlesticks);
        long t1 = System.currentTimeMillis();
        log.info("信号耗时 - calculateSignal: {}ms", t1 - t0);
        if (!isValidSignal(result)) {
            log.debug("未生成有效信号（信号为空或无买卖类型）, calculateSignal耗时: {}ms", t1 - t0);
            return;
        }

        // 4.15 动态低波过滤（拦截后 signalStrength 置 0）
        if (isLowVolatilityRejected(ctx)) {
            technicalSignalService.markRejected(result.getSignalId());
            log.info("低波过滤拦截，信号被拒绝 - 策略: {}, 品种: {}, 周期: {}, signalId: {}",
                    ctx.strategyType, ctx.symbol, ctx.interval, result.getSignalId());
            return;
        }

        // 4.2 AI过滤并获取原始信号（可能调整强度）
        TechnicalSignal signal = applyAiFilter(result, ctx, latestKline.getId());
        long t2 = System.currentTimeMillis();
        log.info("信号耗时 - applyAiFilter: {}ms", t2 - t1);

        // 4.3 推送信号到Redis
        pushSignalToRedis(result, ctx, signal);
        long t3 = System.currentTimeMillis();
        log.info("信号耗时 - pushSignalToRedis: {}ms, processSignal总计: {}ms", t3 - t2, t3 - t0);

        // 4.4 标记该K线已处理（写入缓存）
        long expireMinutes = computeCacheExpireMinutes(ctx.interval);
        redisCache.setCacheObject(cacheKey, System.currentTimeMillis(), expireMinutes, TimeUnit.MINUTES);
        log.info("信号处理完成，已缓存 - key: {}, 过期: {}分钟", cacheKey, expireMinutes);
    }

    // ---------- 参数解析 ----------
    private SignalContext parseAndValidateParams(String param) {
        if (StringUtils.isEmpty(param)) {
            log.warn("任务参数为空");
            return null;
        }

        JSONObject params;
        try {
            params = JSONUtil.parseObj(param);
        } catch (Exception e) {
            log.warn("参数JSON解析失败: {}", param, e);
            return null;
        }

        // 必要字段校验
        if (!params.containsKey(PARAM_INTERVAL) || !params.containsKey(PARAM_STRATEGY_TYPE)
                || !params.containsKey(PARAM_SYMBOL) || !params.containsKey(PARAM_ROBOT_ID)
                || !params.containsKey(PARAM_EXCHANGE)) {
            log.warn("参数缺失必要字段: {}", param);
            return null;
        }

        // 周期解析
        CandlestickIntervalEnum interval = resolveInterval(params);
        if (interval == null) {
            return null;
        }

        // 策略类型解析
        SignFactory.SignType strategyType = resolveStrategyType(params);
        if (strategyType == null) {
            return null;
        }

        String symbol = params.getStr(PARAM_SYMBOL);
        String robotId = params.getStr(PARAM_ROBOT_ID);

        // 交易所解析
        Exchange exchange = resolveExchange(params);
        if (exchange == null) {
            return null;
        }

        // K线数量（可选）
        Integer klineSize = params.getInt(PARAM_SIZE);
        if (klineSize == null || klineSize <= 0) {
            klineSize = DEFAULT_KLINE_SIZE;
        }

        return new SignalContext(interval, strategyType, symbol, robotId, klineSize, exchange);
    }

    private CandlestickIntervalEnum resolveInterval(JSONObject params) {
        String intervalStr = params.getStr(PARAM_INTERVAL);
        CandlestickIntervalEnum interval = params.getEnum(CandlestickIntervalEnum.class, PARAM_INTERVAL);
        if (interval == null) {
            interval = CandlestickIntervalEnum.fromCode(intervalStr);
        }
        if (interval == null) {
            log.warn("无效的K线周期: {}", intervalStr);
        }
        return interval;
    }

    private SignFactory.SignType resolveStrategyType(JSONObject params) {
        SignFactory.SignType type = params.getEnum(SignFactory.SignType.class, PARAM_STRATEGY_TYPE);
        if (type == null) {
            log.warn("无效的策略类型: {}", params.getStr(PARAM_STRATEGY_TYPE));
        }
        return type;
    }

    private Exchange resolveExchange(JSONObject params) {
        String exchangeStr = params.getStr(PARAM_EXCHANGE);
        try {
            return Exchange.valueOf(exchangeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的交易所参数: {}", exchangeStr);
            return null;
        }
    }

    // ---------- 数据获取 ----------
    private List<Candlestick> fetchKlineData(SignalContext ctx) {
        KlineParam klineParam = KlineParam.builder()
                // TODO: 从机器人或上下文中获取真实的memberId/accountId
                .memberId("1111")
                .accountId("1111")
                .symbol(ctx.symbol)
                .size(ctx.klineSize)
                .klineInterval(ctx.interval)
                .exchange(ctx.exchange)
                .build();
        return candlestickService.getLastKlines(klineParam);
    }

    // ---------- 信号计算 ----------
    private BuyAndSellWeightDto calculateSignal(SignalContext ctx, List<Candlestick> candlesticks) {
        IndicatorCalcDto calcDto = new IndicatorCalcDto();
        calcDto.setKLines(candlesticks);
        calcDto.setRobotId(ctx.robotId);
        calcDto.setSymbol(ctx.symbol);
        calcDto.setRobotName(ctx.strategyType.name());
        calcDto.setCandlestickIntervalEnum(ctx.interval);
        ISignService factory = SignFactory.getInstance(ctx.strategyType);
        if (factory == null) {
            log.error("获取信号工厂实例失败: {}", ctx.strategyType);
            return null;
        }
        return factory.execute(calcDto);
    }

    private boolean isValidSignal(BuyAndSellWeightDto result) {
        return result != null && result.getSignalId() != null && result.getSignalType() != null;
    }

    /**
     * 动态低波过滤：样本充足（windowSize >= 5）时 avgSpace 低于 p35 分位则拒绝；
     * 样本不足时降级为固定阈值 0.4%。
     */
    private boolean isLowVolatilityRejected(SignalContext ctx) {
        String strategyName = ctx.strategyType.name();
        String timeframe = ctx.interval.name();

        FeatureStatistics stats = featureProvider.getFullStatistics(strategyName, ctx.symbol, timeframe);
        int windowSize = stats != null ? stats.getWindowSize() : 0;

        if (windowSize >= 5) {
            double avgSpace = stats.getAvgSpace();
            double p35 = featureProvider.getAbsSpacePercentile(strategyName, ctx.symbol, timeframe, 0.35);
            if (avgSpace > 0 && avgSpace < p35) {
                log.info("动态低波过滤命中: strategy={}, symbol={}, avgSpace={}%, p35={}%",
                        strategyName, ctx.symbol, avgSpace, p35);
                return true;
            }
        } else {
            double avgSpace = featureProvider.getAvgSpace(strategyName, ctx.symbol, timeframe);
            if (avgSpace > 0 && avgSpace < 0.4) {
                log.info("低波过滤命中(降级阈值0.4%): strategy={}, symbol={}, avgSpace={}%",
                        strategyName, ctx.symbol, avgSpace);
                return true;
            }
        }
        return false;
    }

    // ---------- AI过滤 ----------
    private TechnicalSignal applyAiFilter(BuyAndSellWeightDto result, SignalContext ctx, Long signalTime) {
        try {
            // 查询机器人及策略信息
            TradingBot tradingBot = tradingBotService.getByBotId(ctx.robotId);
            if (tradingBot == null) {
                log.warn("机器人不存在，跳过AI过滤 - robotId: {}", ctx.robotId);
                return null;
            }
            Strategy strategy = strategyService.getByStrategyId(tradingBot.getStrategyId());
            if (strategy == null) {
                log.warn("策略不存在，跳过AI过滤 - strategyId: {}", tradingBot.getStrategyId());
                return null;
            }

            // 查询原始信号
            TechnicalSignal savedSignal = technicalSignalService.getById(result.getSignalId());
            if (savedSignal == null) {
                log.warn("信号记录不存在，跳过AI过滤 - signalId: {}", result.getSignalId());
                return null;
            }

            // 构建AI请求
            AiFilterRequest filterRequest = AiFilterRequest.builder()
                    .strategy(strategy)
                    .symbol(ctx.symbol)
                    .direction(result.getSignalType().name())
                    .signalStrength(savedSignal.getSignalStrength())
                    .signalTime(signalTime)
                    .build();

            AiFilterResult filterResult = aiSignalFilterService.filter(filterRequest);
            if (filterResult.isEnabled()) {
                // 更新信号强度
                TechnicalSignal updateSignal = new TechnicalSignal();
                updateSignal.setId(result.getSignalId());
                updateSignal.setSignalStrength(filterResult.getAdjustedStrength());
                updateSignal.setAiFilterResult(filterResult.getAiFilterResultJson());
                technicalSignalService.updateById(updateSignal);

                log.info("AI过滤完成 - 信号ID: {}, 原始强度: {}, 调整后: {}, 决策: {}",
                        result.getSignalId(), savedSignal.getSignalStrength(),
                        filterResult.getAdjustedStrength(), filterResult.getDecision());

                // 更新内存中的信号对象，以便后续推送使用调整后的值
                savedSignal.setSignalStrength(filterResult.getAdjustedStrength());
                savedSignal.setAiFilterResult(filterResult.getAiFilterResultJson());
            }
            return savedSignal;

        } catch (Exception e) {
            log.error("AI过滤执行异常 - 信号ID: {}", result.getSignalId(), e);
            return null;
        }
    }

    // ---------- 信号推送 ----------
    private void pushSignalToRedis(BuyAndSellWeightDto result, SignalContext ctx, TechnicalSignal signal) {
        if (signal == null) {
            // 若AI过滤未返回，从数据库重新获取原始信号
            signal = technicalSignalService.getById(result.getSignalId());
        }
        if (signal == null) {
            log.warn("信号记录不存在，无法推送 - signalId: {}", result.getSignalId());
            return;
        }

        try {
            String direction = mapDirection(result.getSignalType());
            double strength = Optional.ofNullable(signal.getSignalStrength())
                    .map(BigDecimal::doubleValue)
                    .orElse(0.0);
            double price = Optional.ofNullable(signal.getClosePrice())
                    .map(BigDecimal::doubleValue)
                    .orElse(0.0);
            String entryType = signal.getEntryType() != null ? signal.getEntryType().name() : "MARKET";

            Map<String, Object> data = Map.of(
                    "strength", strength,
                    "entryType", entryType,
                    "price", price,
                    "timeframe", ctx.interval.name()
            );

            long ttlMinutes = computeCacheExpireMinutes(ctx.interval);
            long ttlSeconds = Math.max(MIN_TTL_MINUTES, Math.min(MAX_TTL_MINUTES, ttlMinutes)) * 60;

            SignalPushMessage message = SignalPushMessage.builder()
                    .type("NEW_SIGNAL")
                    .source("AI_LIVE_ADVICE_SCHEDULED")
                    .symbol(ctx.symbol)
                    .direction(direction)
                    .robotId(ctx.robotId)
                    .timestamp(System.currentTimeMillis())
                    .data(data)
                    .ttlMinutes(ttlMinutes)
                    .build();

            signalMessagePublisher.push(message);
            log.info("信号已推送Redis - 信号ID: {}, 方向: {}, robotId: {}, TTL: {}秒",
                    result.getSignalId(), direction, ctx.robotId, ttlSeconds);
        } catch (Exception e) {
            log.error("信号推送异常 - 信号ID: {}", result.getSignalId(), e);
        }
    }

    private String mapDirection(SignalType signalType) {
        if (signalType == SHORT || signalType == CALLBACK_SHORT || signalType == CLOSE_SHORT) {
            return DIRECTION_SHORT;
        }
        return DIRECTION_LONG;
    }

    // ---------- 缓存辅助方法 ----------
    private String buildCacheKey(SignalContext ctx, Candlestick kline) {
        long timeSlot = alignTimestampToInterval(kline.getId(), ctx.interval);
        return String.format("%s%s_%s_%s_%s_%d",
                KLINE_SIGN_CACHE_PREFIX,
                ctx.strategyType.name(),
                ctx.symbol,
                ctx.interval.name(),
                ctx.robotId,
                timeSlot);
    }

    private long alignTimestampToInterval(long timestamp, CandlestickIntervalEnum interval) {
        long periodMillis = interval.getMinNum() * 60_000L;
        return (timestamp / periodMillis) * periodMillis;
    }

    private long computeCacheExpireMinutes(CandlestickIntervalEnum interval) {
        int periodMinutes = interval.getMinNum();
        if (periodMinutes <= 5) {
            return periodMinutes * 3L;
        } else if (periodMinutes <= 60) {
            return periodMinutes * 2L;
        } else {
            return periodMinutes + 60L;
        }
    }

    // ---------- 内部上下文类 ----------
    private static class SignalContext {
        final CandlestickIntervalEnum interval;
        final SignFactory.SignType strategyType;
        final String symbol;
        final String robotId;
        final int klineSize;
        final Exchange exchange;

        SignalContext(CandlestickIntervalEnum interval, SignFactory.SignType strategyType,
                      String symbol, String robotId, int klineSize, Exchange exchange) {
            this.interval = interval;
            this.strategyType = strategyType;
            this.symbol = symbol;
            this.robotId = robotId;
            this.klineSize = klineSize;
            this.exchange = exchange;
        }
    }
}