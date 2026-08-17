package com.chain.ai.trade.extension.ta4j.indicator.chanlun.adapter;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.engine.ChanLunEngine;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunResult;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.StdKLine;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * ta4j 适配器：将缠论引擎包装为 ta4j CachedIndicator
 * 利用 ta4j 的缓存机制按需计算，避免重复调用
 */
public class ChanLunIndicator extends CachedIndicator<ChanLunResult> {

    private final ChanLunEngine engine;
    private final BarSeries series;
    private int lastBarCount = 0;

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    public ChanLunIndicator(BarSeries series, ChanLunEngine engine) {
        super(series);
        this.series = series;
        this.engine = engine;
    }

    @Override
    protected ChanLunResult calculate(int index) {
        int currentBarCount = series.getBarCount();

        if (currentBarCount > lastBarCount) {
            if (lastBarCount == 0) {
                // 首次全量计算
                List<StdKLine> allKlines = new ArrayList<>();
                for (int i = 0; i < currentBarCount; i++) {
                    StdKLine s = toStdKLine(series.getBar(i));
                    s.setOriginalIndex(i);
                    allKlines.add(s);
                }
                lastBarCount = currentBarCount;
                return engine.compute(allKlines);
            } else {
                // 增量计算
                List<StdKLine> newKlines = new ArrayList<>();
                for (int i = lastBarCount; i < currentBarCount; i++) {
                    StdKLine s = toStdKLine(series.getBar(i));
                    s.setOriginalIndex(i);
                    newKlines.add(s);
                }
                lastBarCount = currentBarCount;
                return engine.incrementalCompute(newKlines);
            }
        }

        // 数据未变化，返回上次结果
        return engine.getLastResult() != null ? engine.getLastResult() : new ChanLunResult();
    }

    /**
     * ta4j Bar → StdKLine 转换
     */
    private StdKLine toStdKLine(Bar bar) {
        StdKLine k = new StdKLine();
        Instant endTime = bar.getEndTime();
        k.setTime(LocalDateTime.ofInstant(endTime, ZoneId.systemDefault()));
        k.setOpen(bar.getOpenPrice().doubleValue());
        k.setHigh(bar.getHighPrice().doubleValue());
        k.setLow(bar.getLowPrice().doubleValue());
        k.setClose(bar.getClosePrice().doubleValue());
        k.setVolume(bar.getVolume().longValue());
        k.setAtr(0);
        return k;
    }
}
