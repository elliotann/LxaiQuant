package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.engine2.persistence.RealtimeGateway;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

/**
 * 模拟回测引擎 — Paper Trading。
 * <p>
 * 一次性加载全部历史 K 线，逐根遍历处理，模拟实盘逐 K 线推送的效果。
 * </p>
 */
@Slf4j
@Component
@Profile("paper")
public class PaperEngine extends RealtimeEngine {

    public PaperEngine(BarSeries series, ScriptStrategy strategy,
                       RealtimeConfig config, RealtimeContext context, RealtimeGateway gateway) {
        super(series, strategy, config, context, gateway);
    }

    /**
     * Paper 引擎遍历全部历史 K 线逐根处理（跳过预热期）。
     * <p>
     * Live 引擎通过 {@link #syncBar} 由外部调度器推送 K 线，
     * Paper 引擎无外部调度器，在此处一次性遍历 series 中的所有 Bar。
     * </p>
     */
    @Override
    protected void executeLoop() {
        int warmup = config.getWarmupPeriod();
        int totalBars = series.getBarCount();
        log.info("Paper 引擎开始处理 K 线: symbol={}, totalBars={}, warmup={}, processedIndex={}",
                config.getSymbol(), totalBars, warmup, processedIndex);

        for (int i = warmup; i < totalBars && running; i++) {
            try {
                processBar(i, series.getBar(i));
            } catch (Exception e) {
                log.error("Paper 引擎处理 Bar 异常: symbol={}, index={}", config.getSymbol(), i, e);
            }
            if (progressCallback != null) {
                progressCallback.accept(i);
            }
            processedIndex = i + 1;
        }

        log.info("Paper 引擎 K 线处理完成: symbol={}, processed={}", config.getSymbol(), processedIndex);
    }
}
