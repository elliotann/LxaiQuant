package com.chain.ai.trade.engine.task;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.service.KLineWebSocketService;
import com.chain.ai.trade.engine2.realtime.EngineRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * K线实时数据更新任务
 * 根据活跃订阅动态查询最新K线数据并推送给客户端
 */
@Slf4j
@Component
public class KLineRealTimeUpdateTask {

    private final ICandlestickService candlestickService;
    private final KLineWebSocketService kLineWebSocketService;

    @Autowired(required = false)
    private EngineRegistry engineRegistry;

    public KLineRealTimeUpdateTask(ICandlestickService candlestickService,
                                   KLineWebSocketService kLineWebSocketService) {
        this.candlestickService = candlestickService;
        this.kLineWebSocketService = kLineWebSocketService;
    }

    /** 去重缓存：记录上次推送的数据快照，避免相同数据重复推送 */
    private final Map<String, String> lastSnapshotCache = new HashMap<>();

    /**
     * OKX 枚举名到枚举常量的映射
     */
    private static final Map<String, CandlestickIntervalEnum> OKX_INTERVAL_MAP = new HashMap<>();
    private static final Map<CandlestickIntervalEnum, CandlestickIntervalEnum> OKX_TO_GENERIC = new HashMap<>();

    static {
        OKX_INTERVAL_MAP.put("OKXMIN1", CandlestickIntervalEnum.OKXMIN1);
        OKX_INTERVAL_MAP.put("OKXMIN3", CandlestickIntervalEnum.OKXMIN3);
        OKX_INTERVAL_MAP.put("OKXMIN5", CandlestickIntervalEnum.OKXMIN5);
        OKX_INTERVAL_MAP.put("OKXMIN15", CandlestickIntervalEnum.OKXMIN15);
        OKX_INTERVAL_MAP.put("OKXMIN30", CandlestickIntervalEnum.OKXMIN30);
        OKX_INTERVAL_MAP.put("OKXMIN60", CandlestickIntervalEnum.OKXMIN60);
        OKX_INTERVAL_MAP.put("OKX4HOUR", CandlestickIntervalEnum.OKX4HOUR);
        OKX_INTERVAL_MAP.put("OKX1D", CandlestickIntervalEnum.OKX1D);

        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKXMIN1, CandlestickIntervalEnum.MIN1);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKXMIN3, CandlestickIntervalEnum.MIN3);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKXMIN5, CandlestickIntervalEnum.MIN5);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKXMIN15, CandlestickIntervalEnum.MIN15);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKXMIN30, CandlestickIntervalEnum.MIN30);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKXMIN60, CandlestickIntervalEnum.MIN60);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKX4HOUR, CandlestickIntervalEnum.HOUR4);
        OKX_TO_GENERIC.put(CandlestickIntervalEnum.OKX1D, CandlestickIntervalEnum.DAY1);
    }

    /**
     * 实时数据更新，每1秒执行一次
     * 合并 WebSocket 订阅 + 注册的 V2 引擎，查询最新K线并推送
     */
    @Scheduled(fixedRate = 1000)
    public void updateRealTimeData() {
        // WebSocket 订阅 key（前端连接）
        Set<String> wsKeys = kLineWebSocketService.getActiveSubscriptionKeys();
        // 注册的引擎 key（实盘/模拟运行中）
        Set<String> engineKeys = engineRegistry != null ? engineRegistry.getActiveKeys() : java.util.Collections.emptySet();

        // 合并去重
        Set<String> activeKeys = new java.util.LinkedHashSet<>();
        activeKeys.addAll(wsKeys);
        activeKeys.addAll(engineKeys);

        if (activeKeys.isEmpty()) {
            return;
        }

        for (String key : activeKeys) {
            // key 格式: symbol_interval，如 ETH-USDT-SWAP_OKXMIN3
            int underscoreIndex = key.lastIndexOf('_');
            if (underscoreIndex <= 0 || underscoreIndex >= key.length() - 1) {
                continue;
            }
            String symbol = key.substring(0, underscoreIndex);
            String intervalName = key.substring(underscoreIndex + 1);

            CandlestickIntervalEnum interval = OKX_INTERVAL_MAP.get(intervalName);
            // fallback: 引擎注册 key 用的是 getCode() 格式（如 3m/15m），用 fromCodeValue 查找
            if (interval == null) {
                interval = CandlestickIntervalEnum.fromCodeValue(intervalName);
            }
            if (interval == null) {
                continue;
            }

            try {
                Candlestick lastKline = candlestickService.getLastKline(symbol, interval);
                // 非 OKX 交易所数据使用通用枚举存储，回退查询
                if (lastKline == null) {
                    CandlestickIntervalEnum fallback = OKX_TO_GENERIC.get(interval);
                    if (fallback != null) {
                        lastKline = candlestickService.getLastKline(symbol, fallback);
                    }
                }
                if (lastKline == null) {
                    continue;
                }

                // 构建数据快照用于去重
                long timeSec = parseTimeStr(lastKline.getTimeStr());
                String snapshot = timeSec + "|" + lastKline.getOpenPrice() + "|" + lastKline.getHighPrice()
                        + "|" + lastKline.getLowPrice() + "|" + lastKline.getClosePrice() + "|" + lastKline.getVolume();
                String prevSnapshot = lastSnapshotCache.put(key, snapshot);
                if (snapshot.equals(prevSnapshot)) {
                    continue; // 数据无变化，跳过推送
                }

                Map<String, Object> klineData = new HashMap<>();
                klineData.put("time", timeSec);
                klineData.put("open", lastKline.getOpenPrice());
                klineData.put("high", lastKline.getHighPrice());
                klineData.put("low", lastKline.getLowPrice());
                klineData.put("close", lastKline.getClosePrice());
                klineData.put("volume", lastKline.getVolume());

                // 推送时用 OKX 间隔名，与前端订阅 key 保持一致
                kLineWebSocketService.broadcastKLineUpdate(symbol, intervalName, klineData);
                log.debug("推送K线数据: symbol={}, interval={}, time={}, close={}",
                        symbol, intervalName, timeSec, lastKline.getClosePrice());

            } catch (Exception e) {
                log.error("更新实时K线数据失败: symbol={}, interval={}, error={}", symbol, intervalName, e.getMessage(), e);
            }
        }
    }

    /**
     * 将 Candlestick 转换为 ta4j Bar，时区逻辑与 IndicatorWrapHelper.buildSeries() 保持一致。
     * 将 kline.getId()（epoch 毫秒）视为上海时区时间，转为 UTC Instant 存储。
     */
    private Bar buildBar(Candlestick kline, CandlestickIntervalEnum interval) {
        ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");
        Duration duration = Duration.ofMinutes(interval.getMinNum());

        // 与 IndicatorWrapHelper.buildSeries() 一致：上海时间当作 UTC 处理
        ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(kline.getId()), shanghaiZone);
        ZonedDateTime endTime = startTime.plus(duration);
        Instant endInstant = endTime.toLocalDateTime().atOffset(ZoneOffset.UTC).toInstant();
        Instant beginInstant = endInstant.minus(duration);

        return new BaseBar(duration, beginInstant, endInstant,
                DecimalNum.valueOf(kline.getOpenPrice()),
                DecimalNum.valueOf(kline.getHighPrice()),
                DecimalNum.valueOf(kline.getLowPrice()),
                DecimalNum.valueOf(kline.getClosePrice()),
                DecimalNum.valueOf(kline.getVolume()),
                DecimalNum.valueOf(0), 0);
    }

    /**
     * 解析时间字符串为时间戳（秒）
     */
    private Long parseTimeStr(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return System.currentTimeMillis() / 1000;
        }
        try {
            if (timeStr.matches("\\d{10}")) {
                return Long.parseLong(timeStr);
            } else if (timeStr.matches("\\d{13}")) {
                return Long.parseLong(timeStr) / 1000;
            } else {
                try {
                    java.time.format.DateTimeFormatter formatter =
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                    return dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                } catch (Exception e1) {
                    try {
                        java.time.format.DateTimeFormatter formatter2 =
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timeStr, formatter2);
                        return dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                    } catch (Exception e2) {
                        log.warn("无法解析时间字符串: {}", timeStr);
                        return System.currentTimeMillis() / 1000;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析时间字符串失败: {}", timeStr, e);
            return System.currentTimeMillis() / 1000;
        }
    }
}
