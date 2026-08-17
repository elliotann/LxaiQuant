package com.chain.ai.trade.engine.signal.service.impl;

import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信号缓存管理器，负责从数据库加载信号并提供实时查询与更新。
 * 键格式为 K线开始时间格式化的字符串（yyyy-MM-dd HH:mm:ss，UTC），
 * 通过 TechnicalSignal 的 klineTimestamp 字段（毫秒）生成。
 */
@Slf4j
@Component
public class SignalCacheManager {
    private final Map<String, SignalInfo> cache = new ConcurrentHashMap<>();
    private final ITechnicalSignalService technicalSignalService;

    /** 缓存的 symbol（loadSignals 时记录），用于 DB fallback 查询 */
    private volatile String cachedSymbol;

    /** 缓存的 strategyId/indicator，用于 DB fallback 过滤 */
    private volatile String cachedIndicator;

    /** 缓存的数据源/exchange，用于 DB fallback 过滤 */
    private volatile String cachedDataSource;

    public SignalCacheManager(ITechnicalSignalService technicalSignalService) {
        this.technicalSignalService = technicalSignalService;
    }

    // ========== 初始化加载（用于回测或系统启动时批量加载） ==========

    /**
     * 批量加载信号（按时间范围）
     * @param startInclusive 开始时间（包含）
     * @param endInclusive   结束时间（包含）
     * @param symbol         交易对（如 "BTCUSDT"）
     * @param indicator      指标类型（如 "MACD"），若为 null 则忽略
     * @param dataSource     数据来源（如 "OKX"），若为 null 则忽略
     */
    public void loadSignals(LocalDateTime startInclusive, LocalDateTime endInclusive,
                            String symbol, String indicator, String dataSource) {
        log.info("批量加载信号: symbol={}, indicator={}, dataSource={}, 时间范围={} ~ {}",
                symbol, indicator, dataSource, startInclusive, endInclusive);

        // 记录参数，供后续 DB fallback 使用
        this.cachedSymbol = symbol;
        this.cachedIndicator = indicator;
        this.cachedDataSource = dataSource;

        List<TechnicalSignal> signals = technicalSignalService.getSignalsByTimeRange(symbol, startInclusive, endInclusive);
        log.info("查询到 {} 条原始技术信号", signals.size());

        int lbCount = 0, sbCount = 0, otherCount = 0;
        for (TechnicalSignal signal : signals) {
            // 过滤（如果指定了指标或数据源）
            if (indicator != null && !indicator.equals(signal.getIndicator())) continue;
            if (dataSource != null && !dataSource.equals(signal.getDataSource())) continue;

            String technicalDirection = signal.getTechnicalDirection();
            if (technicalDirection == null) continue;

            // 直接使用 technicalDirection 作为信号类型（如 "LONG"、"SHORT"）
            String signalType = technicalDirection;
            double weight = signal.getSignalStrength() != null ? signal.getSignalStrength().doubleValue() : 1.0;

            if (signal.getKlineTimestamp() == null) {
                log.warn("信号缺少 klineTimestamp，忽略: {}", signal);
                continue;
            }
            String timeKey = signal.getKlineTime();

            SignalInfo info = new SignalInfo(signal.getId(), signalType, weight, signal.getExtraParams());
            cache.put(timeKey, info);

            if ("LONG".equals(signalType)) lbCount++;
            else if ("SHORT".equals(signalType)) sbCount++;
            else otherCount++;
        }

        log.info("批量加载完成：LONG={}, SHORT={}, 其他={}, 总缓存={}", lbCount, sbCount, otherCount, cache.size());
    }

    // ========== 实时更新（用于实盘，由外部推送） ==========

    /**
     * 实时更新信号（由外部消息队列、WebSocket 等调用）
     * @param timeKey    K线开始时间格式化字符串（格式必须与 formatTimeKey 一致）
     * @param signalType 信号类型（如 "LONG", "SHORT", "CLOSE_LONG", "CLOSE_SHORT"）
     * @param weight     信号权重
     */
    public void updateSignal(String timeKey, String signalType, double weight) {
        cache.put(timeKey, new SignalInfo(null, signalType, weight, null));
        log.debug("实时信号更新: timeKey={}, type={}, weight={}", timeKey, signalType, weight);
    }

    public void updateSignal(String timeKey, String signalType, double weight, String extraParams) {
        cache.put(timeKey, new SignalInfo(null, signalType, weight, extraParams));
        log.debug("实时信号更新: timeKey={}, type={}, weight={}", timeKey, signalType, weight);
    }

    /**
     * 按 K线索引查询信号（由策略规则调用）。
     * 优先查内存缓存，miss 时自动从 DB fallback 查询并缓存。
     *
     * @param index  K线索引
     * @param series K线序列
     * @return 信号信息，若无则返回 null
     */
    public SignalInfo getSignal(int index, BarSeries series) {
        if (index < 0 || index >= series.getBarCount()) return null;
        Bar bar = series.getBar(index);
        String timeKey = formatTimeKey(bar.getBeginTime()); // 使用 K线开始时间作为键
        SignalInfo cached = cache.get(timeKey);
        if (cached != null) return cached;

        // 缓存 miss → DB fallback 查询
        if (technicalSignalService != null && cachedSymbol != null) {
            LocalDateTime barTime = LocalDateTime.ofInstant(bar.getBeginTime(), ZoneOffset.UTC);
            List<TechnicalSignal> signals = technicalSignalService.getSignalsByTimeRange(
                    cachedSymbol, barTime, barTime);
            if (signals != null && !signals.isEmpty()) {
                for (TechnicalSignal signal : signals) {
                    if (cachedIndicator != null && !cachedIndicator.equals(signal.getIndicator())) continue;
                    if (cachedDataSource != null && !cachedDataSource.equals(signal.getDataSource())) continue;
                    String technicalDirection = signal.getTechnicalDirection();
                    if (technicalDirection == null) continue;
                    double weight = signal.getSignalStrength() != null ? signal.getSignalStrength().doubleValue() : 1.0;
                    SignalInfo info = new SignalInfo(signal.getId(), technicalDirection, weight, signal.getExtraParams());
                    cache.put(timeKey, info);
                    log.info("[SIGNAL] DB fallback 命中: symbol={}, time={}, direction={}, weight={}",
                            cachedSymbol, timeKey, technicalDirection, weight);
                    return info;
                }
            }
        }
        return null;
    }

    // ========== 工具方法 ==========

    /**
     * 获取所有信号缓存的快照
     * @return 时间键 -> SignalInfo 的 Map
     */
    public Map<String, SignalInfo> getAllSignals() {
        return new HashMap<>(cache);
    }

    /**
     * 批量放入信号（用于 in-memory 缓存等场景）
     * @param signals 信号 Map
     */
    public void putAll(Map<String, SignalInfo> signals) {
        if (signals != null) {
            cache.putAll(signals);
        }
    }

    /**
     * 格式化时间键（UTC，yyyy-MM-dd HH:mm:ss）
     */
    private String formatTimeKey(Instant time) {
        ZonedDateTime utcTime = ZonedDateTime.ofInstant(time, ZoneOffset.UTC);
        return utcTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 清空缓存（用于测试或重新加载）
     */
    public void clear() {
        cache.clear();
    }
}
