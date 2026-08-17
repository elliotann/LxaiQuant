package com.chain.ai.trade.extension.ta4j.indicator.chanlun.engine;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunResult;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.StdKLine;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

/**
 * 多周期缠论引擎
 * 管理7个周期并行计算
 */
@Service
public class ChanLunMultiPeriodEngine {

    private final Map<Period, ChanLunEngine> engines = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(7);
    private final ChanLunConfig config;

    public ChanLunMultiPeriodEngine(ChanLunConfig config) {
        this.config = config;
        for (Period p : Period.values()) {
            engines.put(p, new ChanLunEngine(p, config));
        }
    }

    /**
     * 计算单个周期的缠论结果
     */
    public ChanLunResult compute(Period period, List<StdKLine> klines) {
        return engines.get(period).compute(klines);
    }

    /**
     * 并行计算所有周期的缠论结果
     */
    public Map<Period, ChanLunResult> computeAll(Map<Period, List<StdKLine>> periodKlines) {
        Map<Period, ChanLunResult> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<Period, List<StdKLine>> entry : periodKlines.entrySet()) {
            Period period = entry.getKey();
            List<StdKLine> klines = entry.getValue();
            futures.add(CompletableFuture.runAsync(() -> {
                results.put(period, engines.get(period).compute(klines));
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
    }

    /**
     * 增量计算指定周期
     */
    public ChanLunResult incrementalCompute(Period period, List<StdKLine> newKlines) {
        return engines.get(period).incrementalCompute(newKlines);
    }

    /**
     * 获取指定周期的上次计算结果
     */
    public ChanLunResult getResult(Period period) {
        return engines.get(period).getLastResult();
    }

    /**
     * 获取所有周期的引擎
     */
    public Map<Period, ChanLunEngine> getEngines() {
        return engines;
    }

    /**
     * 重算所有周期
     */
    public void recomputeAll() {
        engines.values().forEach(ChanLunEngine::reset);
    }
}
