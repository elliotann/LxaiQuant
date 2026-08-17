package com.chain.ai.trade.engine.data.provider.impl;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.provider.ExchangeKlineFetcher;
import com.chain.ai.trade.engine.data.provider.ExchangeKlineFetcherFactory;
import com.chain.ai.trade.engine.data.provider.KlineDataProvider;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试模式K线数据提供者
 * 从数据库按时间范围逐根获取历史K线数据
 */
@Slf4j
@Component
public class TestKlineDataProvider implements KlineDataProvider {

    private final ICandlestickService candlestickService;
    private final ExchangeKlineFetcherFactory exchangeKlineFetcherFactory;

    public TestKlineDataProvider(ICandlestickService candlestickService,
                                 ExchangeKlineFetcherFactory exchangeKlineFetcherFactory) {
        this.candlestickService = candlestickService;
        this.exchangeKlineFetcherFactory = exchangeKlineFetcherFactory;
    }

    // 使用ThreadLocal存储当前处理的K线时间，支持多策略并发执行
    // 修改为支持多个 resolution (symbol + interval) 的时间记录
    private final ThreadLocal<Map<String, Long>> currentKlineTimeMap = ThreadLocal.withInitial(HashMap::new);

    @Override
    public BarSeries fetchInitialKlines(TradingStrategyParams params, int count) {
        try {
            // 检查时间范围参数
            if (params.getStartTime() == null) {
                log.error("测试模式必须提供startTime");
                return new BaseBarSeriesBuilder()
                        .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                        .build();
            }

            CandlestickIntervalEnum interval = parseInterval(params.getInterval());
            
            // 计算起始时间：从startTime往前推count根K线
            long intervalMillis = getIntervalMillis(params.getInterval());
            long startTimeForQuery = params.getStartTime() - (count * intervalMillis);
            // endTime设置为startTime的前一根K线，避免包含startTime这一根（这一根会在fetchNextKline中获取）
            long endTimeForQuery = params.getStartTime() - intervalMillis;
            
            // 查询历史K线数据（从startTime往前推count根，不包含startTime这一根）
            // 注意：getKlines方法期望的是秒级时间戳
            KlineParam klineParam = KlineParam.builder()
                    .symbol(params.getSymbol())
                    .startTime(startTimeForQuery) // 转换为秒级时间戳
                    .endTime(endTimeForQuery) // 到startTime的前一根为止
                    .klineInterval(interval)
                    .size(count) // 获取指定数量
                    .build();

            List<Candlestick> klines = candlestickService.getKlines(klineParam);

            if (klines == null || klines.isEmpty()) {
                log.warn("测试模式：未获取到初始K线数据: symbol={}, interval={}, count={}", 
                        params.getSymbol(), params.getInterval(), count);
                return new BaseBarSeriesBuilder()
                        .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                        .build();
            }

            log.info("测试模式：成功加载 {} 根初始K线数据（从 {} 往前推）", 
                    klines.size(), params.getStartTime());
            
            // 转换为BarSeries
            return IndicatorWrapHelper.buildSeries(klines);

        } catch (Exception e) {
            log.error("获取初始K线数据失败", e);
            return new BaseBarSeriesBuilder()
                    .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                    .build();
        }
    }

    @Override
    public Bar fetchNextKline(TradingStrategyParams params) {
        try {
            // 检查时间范围参数
            if (params.getStartTime() == null || params.getEndTime() == null) {
                log.error("测试模式必须提供startTime和endTime: symbol={}, interval={}", params.getSymbol(), params.getInterval());
                return null;
            }

            // 使用 symbol + interval 作为缓存 Key
            String key = params.getSymbol() + ":" + params.getInterval();
            Map<String, Long> timeMap = currentKlineTimeMap.get();
            Long currentTime = timeMap.get(key);

            if (currentTime == null) {
                currentTime = params.getStartTime();
                timeMap.put(key, currentTime);
                // 格式化时间戳用于日志输出（UTC时区）
                Instant instant = Instant.ofEpochMilli(currentTime);
                String timeStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneOffset.UTC)
                        .format(instant);
                log.info("测试模式：开始从时间 {} ({}) 获取 {} K线数据", currentTime, timeStr, key);
            }

            // 检查是否超过结束时间
            if (currentTime > params.getEndTime()) {
                log.info("测试模式：已到达结束时间，停止获取 {} K线数据", key);
                return null; // 到达结束时间，停止获取
            }

            // 查询当前时间点的K线数据
            CandlestickIntervalEnum interval = parseInterval(params.getInterval());
            // 注意：getKlines方法期望的是秒级时间戳，但实际实现中可能使用毫秒
            // 根据CandlestickServiceImpl的实现，它使用Instant.ofEpochSecond，所以需要转换为秒
            KlineParam klineParam = KlineParam.builder()
                    .symbol(params.getSymbol())
                    .startTime(currentTime) // 转换为秒级时间戳（getKlines使用ofEpochSecond）
                    .endTime(currentTime)
                    .klineInterval(interval)
                    .size(1) // 只获取一条
                    .build();

            List<Candlestick> klines = candlestickService.getKlines(klineParam);

            if (klines != null && !klines.isEmpty()) {
                Candlestick kline = klines.getFirst();
                
                // 更新当前时间：根据K线周期计算下一根K线的时间
                // 下一根K线的时间是当前K线的结束时间（毫秒级）
                long nextTime = kline.getId() + getIntervalMillis(params.getInterval());
                timeMap.put(key, nextTime);
                
                // 转换为Bar对象
                return convertToBar(kline, interval);
            } else {
                // 如果当前时间点没有K线数据，跳过到下一个时间点
                long skipToTime = currentTime + getIntervalMillis(params.getInterval());
                timeMap.put(key, skipToTime);
                
                // 递归尝试获取下一根，或者返回 null
                // 为了避免递归太深，可以简单返回 null
                return null;
            }
        } catch (Exception e) {
            log.error("获取下一根K线数据失败", e);
            return null;
        }
    }
    @Override
    public boolean isTestMode() {
        return true;
    }

    @Override
    public void reset() {
        currentKlineTimeMap.remove(); // 清除当前线程的状态
        log.debug("测试模式K线提供者已重置（线程: {})", Thread.currentThread().getName());
    }

    /**
     * 批量获取所有K线数据（用于回测批量处理，提升性能）
     * 一次性查询从startTime到endTime的所有K线，避免逐根查询的性能问题
     *
     * @param params 交易策略参数（必须包含startTime和endTime）
     * @return BarSeries对象，包含从startTime到endTime的所有K线数据
     */
    @Override
    public BarSeries fetchAllKlines(TradingStrategyParams params) {
        try {
            // 检查时间范围参数
            if (params.getStartTime() == null || params.getEndTime() == null) {
                log.error("批量获取K线必须提供startTime和endTime");
                return new BaseBarSeriesBuilder()
                        .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                        .build();
            }

            CandlestickIntervalEnum interval = parseInterval(params.getInterval());
            
            // 批量查询所有K线数据（一次性查询，不设置LIMIT）
            KlineParam klineParam = KlineParam.builder()
                    .symbol(params.getSymbol())
                    .exchange(params.getMemberPlatform())
                    .startTime(params.getStartTime())
                    .endTime(params.getEndTime())
                    .klineInterval(interval)
                    .size(Integer.MAX_VALUE)
                    .build();

            log.info("批量获取K线数据: symbol={}, interval={}, startTime={}, endTime={}", 
                    params.getSymbol(), params.getInterval(), 
                    new Date(params.getStartTime()),
                    new Date(params.getEndTime()));

            List<Candlestick> klines = candlestickService.getKlines(klineParam);

            if (klines == null || klines.isEmpty()) {
                log.warn("批量获取K线数据为空: symbol={}, interval={}, startTime={}, endTime={}", 
                        params.getSymbol(), params.getInterval(), 
                        new Date(params.getStartTime()),
                        new Date(params.getEndTime()));
                return new BaseBarSeriesBuilder()
                        .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                        .build();
            }

            log.info("批量获取K线数据成功: {} 根（从 {} 到 {}）", 
                    klines.size(),
                    klines.getFirst().getTimeStr(),
                    klines.getLast().getTimeStr());
            
            // 转换为BarSeries
            return IndicatorWrapHelper.buildSeries(klines);

        } catch (Exception e) {
            log.error("批量获取K线数据失败", e);
            return new BaseBarSeriesBuilder()
                    .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                    .build();
        }
    }

    /**
     * 将Candlestick转换为Bar对象
     */
    private Bar convertToBar(Candlestick kline, CandlestickIntervalEnum interval) {
        List<Candlestick> singleKline = List.of(kline);
        BarSeries series = IndicatorWrapHelper.buildSeries(singleKline);
        return series.getLastBar();
    }

    /**
     * 根据interval计算时间间隔（毫秒）
     */
    private long getIntervalMillis(String interval) {
        if (interval == null) {
            return 60 * 60 * 1000L; // 默认1小时
        }

        return switch (interval.toLowerCase()) {
            case "1m" -> 60 * 1000L;
            case "3m" -> 3 * 60 * 1000L;
            case "5m" -> 5 * 60 * 1000L;
            case "15m" -> 15 * 60 * 1000L;
            case "30m" -> 30 * 60 * 1000L;
            case "1h", "60m" -> 60 * 60 * 1000L;
            case "4h" -> 4 * 60 * 60 * 1000L;
            case "1d" -> 24 * 60 * 60 * 1000L;
            default -> 60 * 60 * 1000L; // 默认1小时
        };
    }

    /**
     * 从交易所获取历史K线数据并保存到数据库
     *
     * @param exchange    交易所标识，如 "OKX"
     * @param symbol      交易对，如 "BTC-USDT"
     * @param intervalStr 周期字符串，如 "1m","3m","1h","1d"
     * @param startTimeMs 开始时间（毫秒）
     * @param endTimeMs   结束时间（毫秒）
     * @return 导入的K线条数，未配置 Fetcher 或失败时返回 0
     */
    public int importKlinesFromExchange(String exchange, String symbol, String intervalStr,
                                        long startTimeMs, long endTimeMs) {
        Optional<ExchangeKlineFetcher> fetcherOpt = exchangeKlineFetcherFactory.getFetcher(exchange);
        if (fetcherOpt.isEmpty()) {
            log.warn("不支持的交易所: {}, 请确认已注册对应的 ExchangeKlineFetcher", exchange);
            return 0;
        }
        ExchangeKlineFetcher fetcher = fetcherOpt.get();
        if (startTimeMs >= endTimeMs) {
            log.warn("开始时间必须小于结束时间");
            return 0;
        }
        CandlestickIntervalEnum intervalEnum = parseInterval(intervalStr);
        long startSec = startTimeMs / 1000;
        long endSec = endTimeMs / 1000;
        int total = 0;
        // 防止 endTime 为未来时间时 cursorSec 超出当前时间（部分交易所如 Gate.io 不接受未来 to 参数）
        long nowSec = System.currentTimeMillis() / 1000;
        long cursorSec = Math.min(endSec + 1, nowSec);
        final int batchLimit = 100;
        while (cursorSec > startSec) {
            List<Candlestick> batch = fetcher.fetchKlines(
                    exchange, symbol, intervalEnum, startSec, cursorSec, batchLimit);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            candlestickService.batchSave(batch);
            total += batch.size();
            // OKX after=ts 表示返回 ts 之前(更早)的数据；分页游标使用本批最小时间戳，继续向更早翻页
            long firstIdMs = batch.stream()
                    .map(Candlestick::getId)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);
            if (firstIdMs <= 0) {
                break;
            }
            cursorSec = (firstIdMs / 1000) - 1;
            if (cursorSec <= startSec) {
                break;
            }
            log.debug("已导入 {} 条，本批 {} 条，下一段 after={} (本批最小ts)", total, batch.size(), cursorSec);
        }
        log.info("从交易所导入K线完成: exchange={}, symbol={}, interval={}, 共 {} 条", exchange, symbol, intervalStr, total);
        return total;
    }

    @Override
    public List<Candlestick> listByLeId(KlineParam param) {
        return candlestickService.listByLeId(param);
    }

    /**
     * 解析时间间隔字符串为枚举
     */
    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null) {
            return CandlestickIntervalEnum.OKXMIN60; // 默认1小时
        }

        return switch (interval.toLowerCase()) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> CandlestickIntervalEnum.OKXMIN60; // 默认1小时
        };
    }
}
