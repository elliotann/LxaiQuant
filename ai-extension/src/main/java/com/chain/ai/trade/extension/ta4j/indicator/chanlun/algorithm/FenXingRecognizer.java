package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.FenXing;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.StdKLine;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * 分型识别器
 * 识别顶分型和底分型——中间K线的最高价最高或最低价最低
 */
public class FenXingRecognizer {

    private FenXingRecognizer() {}

    /**
     * 对标准化后的K线序列识别所有分型
     */
    public static List<FenXing> recognize(List<StdKLine> klines, ChanLunConfig config) {
        List<FenXing> result = new ArrayList<>();
        if (klines == null || klines.size() < 3) return result;

        for (int i = 1; i < klines.size() - 1; i++) {
            StdKLine prev = klines.get(i - 1);
            StdKLine curr = klines.get(i);
            StdKLine next = klines.get(i + 1);

            FenXing fx = tryCreateFenXing(i, prev, curr, next);
            if (fx != null) {
                // 强度过滤（如果配置了阈值）
                if (config.getMinFenXingStrength() > 0 && fx.getPowerScore() < config.getMinFenXingStrength()) {
                    continue;
                }
                fx.setOriginalIndex(klines.get(i).getOriginalIndex());
                result.add(fx);
            }
        }
        return result;
    }

    private static FenXing tryCreateFenXing(int index, StdKLine prev, StdKLine curr, StdKLine next) {
        // 顶分型：curr的高 > prev的高 && curr的高 > next的高
        if (curr.getHigh() > prev.getHigh() && curr.getHigh() > next.getHigh()) {
            FenXing fx = new FenXing();
            fx.setIndex(index);
            fx.setType("TOP");
            fx.setHigh(curr.getHigh());
            fx.setLow(curr.getLow());
            fx.setPowerScore(calcPower(prev, curr, next, true));
            return fx;
        }
        // 底分型：curr的低 < prev的低 && curr的低 < next的低
        if (curr.getLow() < prev.getLow() && curr.getLow() < next.getLow()) {
            FenXing fx = new FenXing();
            fx.setIndex(index);
            fx.setType("BOTTOM");
            fx.setHigh(curr.getHigh());
            fx.setLow(curr.getLow());
            fx.setPowerScore(calcPower(prev, curr, next, false));
            return fx;
        }
        return null;
    }

    /**
     * 计算分型力度评分（基于波动幅度）
     */
    private static double calcPower(StdKLine prev, StdKLine curr, StdKLine next, boolean isTop) {
        if (isTop) {
            return (curr.getHigh() - prev.getLow()) + (curr.getHigh() - next.getLow());
        } else {
            return (prev.getHigh() - curr.getLow()) + (next.getHigh() - curr.getLow());
        }
    }
}
