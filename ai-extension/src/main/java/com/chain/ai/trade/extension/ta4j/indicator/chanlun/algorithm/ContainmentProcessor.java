package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.StdKLine;
import java.util.ArrayList;
import java.util.List;

/**
 * 包含关系处理器
 * 处理K线的包含关系合并，上升趋势取高高（high high, low high），下降趋势取低低（low low, high low）
 * 保持K线数量不变（不删除被包含的K线），确保分型/笔的索引与原始K线对齐
 */
public class ContainmentProcessor {

    private ContainmentProcessor() {}

    /**
     * 处理K线包含关系
     * 注意：返回的列表与输入列表大小相同，不删除被包含的K线，确保索引对齐
     */
    public static List<StdKLine> process(List<StdKLine> klines) {
        if (klines == null || klines.size() < 2) {
            return klines == null ? new ArrayList<>() : new ArrayList<>(klines);
        }

        List<StdKLine> result = new ArrayList<>();
        result.add(copyOf(klines.get(0)));

        for (int i = 1; i < klines.size(); i++) {
            StdKLine current = copyOf(klines.get(i));
            StdKLine last = result.get(result.size() - 1);

            if (isContained(current, last)) {
                // 判断趋势方向
                if (result.size() >= 2) {
                    StdKLine prev = result.get(result.size() - 2);
                    boolean isUp = last.getHigh() >= prev.getHigh();
                    merge(current, last, isUp);
                } else {
                    // 只有一根K线时默认向上处理
                    merge(current, last, true);
                }
                // 不修改被包含K线(current)的high/low值，保留原始值
                // 这样分型识别时相邻K线不会因值相同而无法检测分型
            }
            result.add(current);
        }
        return result;
    }

    private static StdKLine copyOf(StdKLine k) {
        StdKLine c = new StdKLine();
        c.setTime(k.getTime());
        c.setOpen(k.getOpen());
        c.setHigh(k.getHigh());
        c.setLow(k.getLow());
        c.setClose(k.getClose());
        c.setVolume(k.getVolume());
        c.setAtr(k.getAtr());
        c.setOriginalIndex(k.getOriginalIndex());
        return c;
    }

    private static boolean isContained(StdKLine a, StdKLine b) {
        return (a.getHigh() <= b.getHigh() && a.getLow() >= b.getLow())
                || (b.getHigh() <= a.getHigh() && b.getLow() >= a.getLow());
    }

    private static void merge(StdKLine source, StdKLine target, boolean isUp) {
        if (isUp) {
            target.setHigh(Math.max(target.getHigh(), source.getHigh()));
            target.setLow(Math.max(target.getLow(), source.getLow()));
        } else {
            target.setHigh(Math.min(target.getHigh(), source.getHigh()));
            target.setLow(Math.min(target.getLow(), source.getLow()));
        }
    }
}
