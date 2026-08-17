package com.chain.ai.trade.extension.ta4j.indicator.chanlun.engine;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunResult;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.StdKLine;
import java.util.List;

/**
 * 增量计算器
 * 只重算尾部不稳定的区域（最后BACKTRACK_BARS根K线），避免全量重算
 */
public class IncrementalComputer {

    private static final int BACKTRACK_BARS = 10;

    private int lastStableIndex = -1;

    /**
     * 基于增量逻辑重新计算缠论结果
     */
    public ChanLunResult recompute(List<StdKLine> rawKlines, ChanLunEngine engine) {
        if (lastStableIndex < 0) {
            // 首次计算，全量计算
            lastStableIndex = Math.max(0, rawKlines.size() - BACKTRACK_BARS);
            return engine.compute(rawKlines);
        }

        // 检测数据量变化
        if (rawKlines.size() <= lastStableIndex + 1) {
            // 数据没有足够增长，全量重算
            return engine.compute(rawKlines);
        }

        // 取 [lastStableIndex, end] 段重新计算
        int start = Math.max(0, lastStableIndex - 1);
        List<StdKLine> segment = rawKlines.subList(start, rawKlines.size());
        ChanLunResult deltaResult = engine.compute(segment);

        // 更新稳定索引
        lastStableIndex = Math.max(lastStableIndex, rawKlines.size() - BACKTRACK_BARS);
        return deltaResult;
    }

    /** 重置增量状态 */
    public void reset() {
        lastStableIndex = -1;
    }
}
