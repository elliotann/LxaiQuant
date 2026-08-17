package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Bi;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Duan;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * 线段识别器（简化特征序列法）
 * 基于奇数笔规则和特征序列判断线段端点
 */
public class DuanRecognizer {

    private DuanRecognizer() {}

    /**
     * 从笔序列中识别线段
     */
    public static List<Duan> recognize(List<Bi> bis, ChanLunConfig config) {
        List<Duan> result = new ArrayList<>();
        if (bis == null || bis.size() < 3) return result;

        int startBiIndex = 0;
        for (int i = 1; i < bis.size(); i++) {
            if (isSegmentEnd(bis, startBiIndex, i, config)) {
                Duan duan = buildDuan(bis, startBiIndex, i);
                result.add(duan);
                startBiIndex = i;
            }
        }

        // 处理最后未闭合的线段
        if (startBiIndex < bis.size() - 1) {
            Duan duan = buildDuan(bis, startBiIndex, bis.size() - 1);
            result.add(duan);
        }
        return result;
    }

    /**
     * 判断是否线段终点（简化：仅判断方向连续反转）
     */
    private static boolean isSegmentEnd(List<Bi> bis, int start, int current, ChanLunConfig config) {
        if (current - start < 2) return false;
        String firstDir = bis.get(start).getDirection();
        String lastDir = bis.get(current).getDirection();
        return !firstDir.equals(lastDir) && (current - start) % 2 == 0;
    }

    private static Duan buildDuan(List<Bi> bis, int start, int end) {
        Duan duan = new Duan();
        duan.setBiList(bis.subList(start, end + 1));
        duan.setStartBiIndex(start);
        duan.setEndBiIndex(end);
        duan.setDirection(bis.get(start).getDirection());
        duan.setHigh(calcMaxHigh(bis, start, end));
        duan.setLow(calcMinLow(bis, start, end));
        duan.setAtrAverage(0); // ATR平均值需外部传入
        return duan;
    }

    private static double calcMaxHigh(List<Bi> bis, int start, int end) {
        double max = Double.MIN_VALUE;
        for (int i = start; i <= end; i++) {
            max = Math.max(max, bis.get(i).getHigh());
        }
        return max;
    }

    private static double calcMinLow(List<Bi> bis, int start, int end) {
        double min = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            min = Math.min(min, bis.get(i).getLow());
        }
        return min;
    }
}
