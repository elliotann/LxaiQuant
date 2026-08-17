package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Bi;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import java.util.List;

/**
 * 背驰检测器（简化版）
 * 通过比较相邻同向笔的价格涨幅/跌幅来判断背驰
 */
public class BeiChiDetector {

    private BeiChiDetector() {}

    /**
     * 检测指定索引的笔是否发生背驰
     */
    public static boolean detect(List<Bi> bis, int biIndex, ChanLunConfig config) {
        if (bis == null || biIndex < 1 || biIndex >= bis.size()) return false;

        Bi current = bis.get(biIndex);
        String direction = current.getDirection();

        // 找前一个同向笔
        Bi prev = null;
        for (int i = biIndex - 1; i >= 0; i--) {
            if (bis.get(i).getDirection().equals(direction)) {
                prev = bis.get(i);
                break;
            }
        }
        if (prev == null) return false;

        // 比较力度
        double currentPower = calcPower(current);
        double prevPower = calcPower(prev);

        return currentPower < prevPower;
    }

    /**
     * 计算笔的力度（涨/跌幅绝对值）
     */
    private static double calcPower(Bi bi) {
        if ("UP".equals(bi.getDirection())) {
            return bi.getEnd().getHigh() - bi.getStart().getLow();
        } else {
            return bi.getStart().getHigh() - bi.getEnd().getLow();
        }
    }
}
