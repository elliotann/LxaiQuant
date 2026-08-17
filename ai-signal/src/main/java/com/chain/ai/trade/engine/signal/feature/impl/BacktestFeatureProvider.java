package com.chain.ai.trade.engine.signal.feature.impl;

import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureSnapshot;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureStatistics;
import com.chain.ai.trade.engine.signal.feature.SignalFeatureProvider;
import com.chain.ai.trade.engine.signal.feature.SlidingWindow;
import com.chain.ai.trade.engine.signal.mapper.SignalAlternateLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测特征提供者：内存 Map 预计算 + 滑动窗口重放
 */
@Component
@ConditionalOnProperty(name = "quant.env", havingValue = "backtest")
@Slf4j
public class BacktestFeatureProvider implements SignalFeatureProvider {

    @Autowired
    private SignalAlternateLogMapper signalAlternateLogMapper;

    private final Map<String, Map<Long, FeatureStatistics>> precomputedCache = new HashMap<>();
    private final Map<String, SlidingWindow> windowMap = new HashMap<>();
    private static final int WINDOW_SIZE = 20;

    /**
     * 加载历史交替记录并预计算每笔信号对应的特征
     */
    public void loadHistory(String strategyName, String symbol, String timeframe) {
        List<SignalAlternateLog> signals = signalAlternateLogMapper.selectAll(strategyName, symbol, timeframe);
        signals.sort(Comparator.comparing(SignalAlternateLog::getEntryTime));

        String key = buildKey(strategyName, symbol, timeframe);
        Map<Long, FeatureStatistics> statsMap = new LinkedHashMap<>();
        SlidingWindow window = new SlidingWindow(WINDOW_SIZE);

        for (SignalAlternateLog signal : signals) {
            window.add(signal);
            statsMap.put(signal.getEntryTime(), window.getFullStatistics());
        }

        precomputedCache.put(key, statsMap);
        windowMap.put(key, window);
        log.info("回测特征加载完成: key={}, 信号数={}", key, signals.size());
    }

    /**
     * 获取某笔信号时间点对应的特征统计（需先 loadHistory）
     */
    public FeatureStatistics getFeatureAtTime(String strategyName, String symbol, String timeframe, long signalTime) {
        Map<Long, FeatureStatistics> map = precomputedCache.get(buildKey(strategyName, symbol, timeframe));
        if (map == null) {
            throw new IllegalStateException("请先执行 loadHistory: " + buildKey(strategyName, symbol, timeframe));
        }
        return map.get(signalTime);
    }

    /**
     * 清空预计算缓存
     */
    public void reset() {
        precomputedCache.clear();
        windowMap.clear();
    }

    @Override
    public double getAvgSpace(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0.0 : w.getAvgSpace();
    }

    @Override
    public double getCumRatio(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0.0 : w.getCumRatio();
    }

    @Override
    public int getDirectionSeq(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0 : w.getDirectionSeq();
    }

    @Override
    public long getLastSignalTime(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0L : w.getLastSignalTime();
    }

    @Override
    public String getLastDirection(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? null : w.getLastDirection();
    }

    @Override
    public double getLatestSpace(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0.0 : w.getLatestSpace();
    }

    @Override
    public FeatureSnapshot getFullSnapshot(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? null : w.getSnapshot();
    }

    @Override
    public double getAbsSpacePercentile(String strategyName, String symbol, String timeframe, double percentile) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0.0 : w.getAbsSpacePercentile(percentile);
    }

    @Override
    public double getSpacePercentile(String strategyName, String symbol, String timeframe, double percentile) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? 0.0 : w.getSpacePercentile(percentile);
    }

    @Override
    public FeatureStatistics getFullStatistics(String strategyName, String symbol, String timeframe) {
        SlidingWindow w = windowMap.get(buildKey(strategyName, symbol, timeframe));
        return w == null ? null : w.getFullStatistics();
    }

    @Override
    public void onNewSignal(SignalAlternateLog signal) {
        // 回测中不使用，特征通过 loadHistory 预计算
    }

    /**
     * 构建内存 key：stats:{strategyName}:{symbol}:{timeframe}
     */
    private String buildKey(String strategyName, String symbol, String timeframe) {
        return String.format("stats:%s:%s:%s", strategyName, symbol, timeframe);
    }
}
