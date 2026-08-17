package com.chain.ai.trade.engine.jobhandler;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.engine2.realtime.EngineRegistry;
import com.chain.ai.trade.engine2.realtime.LiveEngine;
import com.chain.ai.trade.engine2.realtime.LiveEngineFactory;
import com.chain.ai.trade.engine2.realtime.RealtimeEngine;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * V2 实盘引擎保活任务 — XXL-JOB 定时调度（每秒执行）。
 * <p>
 * 职责：
 * 1. 扫描运行中机器人 → 触发引擎创建 → 注册并启动（仅首次）
 * 2. 🔥 每秒 feedRunningEngines() 推送最新 K 线给所有运行中引擎
 * <p>
 * 所有策略装配逻辑下沉到 {@link LiveEngineFactory}。
 * </p>
 */
@Slf4j
@Component
public class V2LiveTradingTaskExecute {

    @Autowired
    private ITradingBotService tradingBotService;

    @Autowired
    private IStrategyService strategyService;

    @Autowired
    private EngineRegistry engineRegistry;

    @Autowired(required = false)
    private LiveEngineFactory liveEngineFactory;

    @Autowired(required = false)
    private ICandlestickService candlestickService;

    @Autowired(required = false)
    private RedisCache redisCache;

    /** 引擎线程池 — 每个 LiveEngine 一个线程执行 run() 生命周期 */
    private final ExecutorService engineExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "live-engine");
        t.setDaemon(true);
        return t;
    });

    /** 喂数据统计计数器（用于日志汇总） */
    private long feedCount = 0;

    // ==================== XXL-JOB 入口 ====================

    /**
     * Cron: 0/1 * * * * ?（每秒执行）
     * 阻塞处理策略: 丢弃后续调度
     */
    @XxlJob("v2LiveTradingExecute")
    public void execute() {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 清理已停止的引擎
            cleanupStoppedEngines();

            // 2. 检查 Factory 是否可用
            if (liveEngineFactory == null) {
                log.warn("LiveEngineFactory 未注入（非 live profile），跳过");
                return;
            }

            // 3. 获取运行中机器人
            List<TradingBot> runningBots = tradingBotService.listByStatus("RUNNING");

            // 4. 遍历启动引擎（仅首次创建）
            if (runningBots != null && !runningBots.isEmpty()) {
                int started = 0, skipped = 0, failed = 0;

                for (TradingBot bot : runningBots) {
                    try {
                        if (bot.getEnabled() == null || !bot.getEnabled()) {
                            skipped++;
                            continue;
                        }
                        if (bot.getStrategyId() == null || bot.getStrategyId().isBlank()) {
                            skipped++;
                            continue;
                        }
                        if (bot.getTradingPair() == null || bot.getTradingPair().isBlank()) {
                            skipped++;
                            continue;
                        }

                        String engineKey = buildEngineKey(bot);
                        if (engineKey == null) {
                            skipped++;
                            continue;
                        }

                        if (engineRegistry.isRunning(engineKey)) {
                            skipped++;
                            continue;
                        }

                        log.info("准备启动引擎: key={}, botId={}", engineKey, bot.getBotId());

                        LiveEngine engine = liveEngineFactory.createEngine(bot);
                        engineRegistry.register(engineKey, engine);
                        engineExecutor.submit(engine::run);

                        started++;
                        log.info("引擎启动成功: key={}, symbol={}", engineKey, bot.getTradingPair());

                    } catch (Exception e) {
                        failed++;
                        log.error("启动引擎失败: botId={}", bot.getBotId(), e);
                    }
                }

                if (started > 0 || failed > 0) {
                    log.info("引擎创建统计: 启动 {}, 跳过 {}, 失败 {}, 共 {}",
                            started, skipped, failed, runningBots.size());
                }
            }

            // ========== 🔥 5. 喂数据：每秒推送最新 K 线给所有运行中引擎 ==========
            feedRunningEngines();

            // ========== 6. 统计日志（每60秒汇总一次） ==========
            feedCount++;
            long elapsed = System.currentTimeMillis() - startTime;
            if (feedCount % 60 == 0) {
                int engineSize = engineRegistry.size();
                int runningCount = engineRegistry.getActiveKeys().size();
                log.info("喂数据统计: 累计{}次, 注册引擎{}, 运行中{}, 本次耗时{}ms",
                        feedCount, engineSize, runningCount, elapsed);
            }

        } catch (Exception e) {
            log.error("V2 保活任务异常", e);
            XxlJobHelper.handleFail("V2 保活失败: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 扫描已停止/暂停的机器人，注销对应引擎
     */
    private void cleanupStoppedEngines() {
        List<String> nonRunningStatuses = List.of("STOPPED", "PAUSED", "ERROR");
        List<TradingBot> allStopped = new ArrayList<>();

        for (String status : nonRunningStatuses) {
            try {
                List<TradingBot> bots = tradingBotService.listByStatus(status);
                if (bots != null && !bots.isEmpty()) {
                    allStopped.addAll(bots);
                }
            } catch (Exception e) {
                log.warn("查询 {} 状态机器人失败", status, e);
            }
        }

        if (allStopped.isEmpty()) return;

        for (TradingBot bot : allStopped) {
            try {
                String engineKey = buildEngineKey(bot);
                if (engineKey == null) continue;

                if (engineRegistry.isRunning(engineKey)) {
                    engineRegistry.unregister(engineKey);
                    log.info("引擎已停止: key={}, botId={}, status={}",
                            engineKey, bot.getBotId(), bot.getStatus());
                }
            } catch (Exception e) {
                log.warn("停止引擎失败: botId={}", bot.getBotId(), e);
            }
        }
    }

    /**
     * 构建引擎注册 Key：symbol_intervalCode
     */
    private String buildEngineKey(TradingBot bot) {
        if (bot.getStrategyId() == null) return null;
        Strategy strategy = strategyService.getByStrategyId(bot.getStrategyId());
        if (strategy == null || strategy.getTimeFrame() == null) return null;

        CandlestickIntervalEnum intervalEnum =
                CandlestickIntervalEnum.fromCodeValue(strategy.getTimeFrame());
        if (intervalEnum == null) return null;

        return bot.getTradingPair() + "_" + intervalEnum.getCode();
    }

    // ==================== 🔥 喂数据核心方法 ====================

    /**
     * 喂数据：给所有运行中引擎推送最新 K 线（每秒执行）。
     * <p>
     * 使用 Redis 缓存（5秒有效期），避免每秒查询数据库。
     * 推送方式：engine.syncBar() 每次只推最新一根 K 线。
     * </p>
     */
    private void feedRunningEngines() {
        // 1. 检查依赖
        if (candlestickService == null) {
            return;
        }

        // 2. 获取所有运行中引擎
        Map<String, RealtimeEngine> running = engineRegistry.getAllRunning();
        if (running.isEmpty()) {
            return;
        }

        // 3. 遍历喂数据
        for (Map.Entry<String, RealtimeEngine> entry : running.entrySet()) {
            String key = entry.getKey();
            RealtimeEngine engine = entry.getValue();

            if (!engine.isRunning()) {
                continue;
            }

            try {
                // 3.1 从 key 解析 symbol 和 interval
                int underscoreIdx = key.lastIndexOf('_');
                if (underscoreIdx <= 0) continue;
                String symbol = key.substring(0, underscoreIdx);
                String intervalCode = key.substring(underscoreIdx + 1);

                // 3.2 从缓存获取最新 K 线
                Exchange exchange = engine.getConfig().getExchange();
                Candlestick kline = getCachedLastKline(symbol, intervalCode, exchange);
                if (kline == null) continue;

                // 3.3 构建 Bar
                Bar bar = IndicatorWrapHelper.buildBar(kline);

                // 3.4 推送给引擎
                engine.syncBar(bar);

                if (log.isTraceEnabled()) {
                    log.trace("喂数据成功: key={}, time={}, close={}",
                            key, kline.getTimeStr(), kline.getClosePrice());
                }

            } catch (Exception e) {
                log.error("喂数据失败: key={}, error={}", key, e.getMessage(), e);
            }
        }
    }

    /**
     * 从缓存获取最新 K 线（5秒有效期）
     */
    private Candlestick getCachedLastKline(String symbol, String intervalCode, Exchange exchange) {
        String cacheKey = "v2:kline:last:" + exchange + ":" + symbol + ":" + intervalCode;

        // 1. 先查 Redis 缓存
        /*if (redisCache != null) {
            try {
                Candlestick cached = (Candlestick) redisCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }
            } catch (Exception e) {
                log.trace("Redis 缓存查询失败: {}", cacheKey, e);
            }
        }*/

        // 2. 查数据库
        CandlestickIntervalEnum interval = CandlestickIntervalEnum.fromCodeValue(intervalCode);
        if (interval == null) return null;

        CandlestickIntervalEnum queryInterval = interval.toOkxInterval();
        if (queryInterval == null) {
            queryInterval = interval;
        }

        List<Candlestick> klines = candlestickService.getLastKlines(
                KlineParam.builder()
                        .symbol(symbol)
                        .exchange(exchange)
                        .klineInterval(queryInterval)
                        .size(1)
                        .build()
        );
        if (klines == null || klines.isEmpty()) return null;
        Candlestick kline = klines.get(0);

        // 3. 写入 Redis 缓存（5秒）
        if (redisCache != null) {
            try {
                redisCache.put(cacheKey, kline, 5L, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.trace("Redis 缓存写入失败: {}", cacheKey, e);
            }
        }

        return kline;
    }
}