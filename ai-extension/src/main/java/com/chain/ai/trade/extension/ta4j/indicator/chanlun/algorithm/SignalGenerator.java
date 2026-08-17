package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Bi;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Signal;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ZhongShu;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.StdKLine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 买卖点信号生成器
 * 基于中枢和背驰生成第一类、第二类、第三类买卖点
 */
public class SignalGenerator {

    private SignalGenerator() {}

    public static List<Signal> generate(List<Bi> bis, List<ZhongShu> zhongShus, List<StdKLine> klines, ChanLunConfig config) {
        List<Signal> result = new ArrayList<>();
        if (bis == null || bis.size() < 3 || zhongShus == null || zhongShus.isEmpty()) {
            return result;
        }

        // 预计算MACD柱序列，用于背驰判断
        double[] macdHistogram = computeMACDHistogram(klines, config.getMacdFast(), config.getMacdSlow(), config.getMacdSignal());

        if (config.isEnableFirstPoint()) {
            result.addAll(generateFirstPoint(bis, zhongShus, config, macdHistogram));
        }
        if (config.isEnableSecondPoint()) {
            result.addAll(generateSecondPoint(bis, zhongShus, config, macdHistogram));
        }
        if (config.isEnableThirdPoint()) {
            result.addAll(generateThirdPoint(bis, zhongShus, config));
        }

        return result;
    }

    // ==================== 第一类买卖点 ====================

    /**
     * 第一类买卖点：趋势背驰后产生
     * 条件：
     * 1. 从最后一个中枢向前找两个同向、不重叠的中枢构成完整趋势
     * 2. 最后一个中枢的离开段力度 < 进入段力度
     */
    private static List<Signal> generateFirstPoint(List<Bi> bis, List<ZhongShu> zhongShus, ChanLunConfig config, double[] macdHistogram) {
        List<Signal> result = new ArrayList<>();
        if (zhongShus.size() < 2) return result;

        // 从最后一个中枢开始，向前找最近的两个同向且不重叠的中枢
        for (int i = zhongShus.size() - 1; i >= 1; i--) {
            ZhongShu last = zhongShus.get(i);
            ZhongShu prev = zhongShus.get(i - 1);

            // 动态计算方向
            int dirLast = getZhongShuDirection(last, bis);
            int dirPrev = getZhongShuDirection(prev, bis);
            if (dirLast == 0 || dirPrev == 0 || dirLast != dirPrev) continue;

            // 两个中枢不能有重叠（否则应合并成更高级别中枢，不算标准趋势）
            if (isOverlapping(prev, last)) continue;

            // 上升趋势：找一买；下降趋势：找一卖
            boolean isUpTrend = dirLast == 1;

            // 获取进入段（prev中枢的离开笔 = 进入last中枢的笔）和离开段（last中枢的离开笔）
            Bi entryBi = findExitBi(bis, prev);
            Bi exitBi = findExitBi(bis, last);
            if (entryBi == null || exitBi == null) continue;

            // 检查离开段是否创新高/新低
            if (isUpTrend && exitBi.getHigh() <= entryBi.getHigh()) continue;
            if (!isUpTrend && exitBi.getLow() >= entryBi.getLow()) continue;

            // MACD柱面积背驰判断
            if (!isBeiChi(entryBi, exitBi, macdHistogram)) continue;

            Signal signal = new Signal();
            signal.setId(UUID.randomUUID().toString().substring(0, 8));
            signal.setType(isUpTrend ? "BUY" : "SELL");
            signal.setLevel(1);
            signal.setBarIndex(exitBi.getEnd().getIndex());
            signal.setPrice(isUpTrend ? exitBi.getLow() : exitBi.getHigh());
            signal.setStrength("STRONG");
            result.add(signal);
            break; // 只取最近的一买/一卖
        }

        return result;
    }

    /**
     * 检查两个中枢的波动区间是否重叠（重叠则应合并，不算标准趋势）
     */
    private static boolean isOverlapping(ZhongShu a, ZhongShu b) {
        return !(a.getHigh() <= b.getLow() || b.getHigh() <= a.getLow());
    }

    /**
     * 找到中枢之前的进入笔（离开上一中枢后进入当前中枢的第一笔）
     */
    private static Bi findEntryBi(List<Bi> bis, ZhongShu zs) {
        List<Integer> idx = zs.getComponentIndices();
        if (idx == null || idx.isEmpty()) return null;
        int firstBiIdx = idx.get(0);
        if (firstBiIdx <= 0) return null;
        return bis.get(firstBiIdx - 1);
    }

    /**
     * 找到中枢之后的离开笔（离开当前中枢的第一笔）
     */
    private static Bi findExitBi(List<Bi> bis, ZhongShu zs) {
        List<Integer> idx = zs.getComponentIndices();
        if (idx == null || idx.isEmpty()) return null;
        int lastBiIdx = idx.get(idx.size() - 1);
        if (lastBiIdx >= bis.size() - 1) return null;
        return bis.get(lastBiIdx + 1);
    }

    /**
     * 动态计算中枢方向
     * 用进入中枢的那一笔的方向来判断，而不是内部首尾笔
     * 因为中枢内部是横盘震荡（如上-下-上），首尾笔方向可能与真实趋势相反
     */
    private static int getZhongShuDirection(ZhongShu zs, List<Bi> bis) {
        List<Integer> idx = zs.getComponentIndices();
        if (idx == null || idx.isEmpty()) return 0;
        int firstIdx = idx.get(0);
        if (firstIdx <= 0) return 0;
        Bi entryBi = bis.get(firstIdx - 1); // 进入中枢的一笔
        return "UP".equals(entryBi.getDirection()) ? 1 : -1;
    }

    /**
     * MACD柱面积背驰判断
     * 比较进入段和离开段的MACD柱总面积（取绝对值），面积减小 => 背驰
     * 注：价格创新高/新低的检查已在 generateFirstPoint 中完成
     */
    private static boolean isBeiChi(Bi entry, Bi exit, double[] macdHistogram) {
        double entryArea = calcMACDArea(entry, macdHistogram);
        double exitArea = calcMACDArea(exit, macdHistogram);
        return exitArea < entryArea;
    }

    // ==================== MACD 柱面积计算 ====================

    /**
     * 计算MACD柱序列
     * @param klines K线数据
     * @param fast  快线周期
     * @param slow  慢线周期
     * @param signal 信号线周期
     * @return MACD柱（histogram）序列
     */
    private static double[] computeMACDHistogram(List<StdKLine> klines, int fast, int slow, int signal) {
        int n = klines.size();
        double[] closes = klines.stream().mapToDouble(StdKLine::getClose).toArray();

        // EMA(fast) 和 EMA(slow)
        double[] emaFast = ema(closes, fast);
        double[] emaSlow = ema(closes, slow);

        // MACD Line = EMA(fast) - EMA(slow)
        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) {
            macdLine[i] = emaFast[i] - emaSlow[i];
        }

        // Signal Line = EMA(MACD Line, signal)
        double[] signalLine = ema(macdLine, signal);

        // Histogram = MACD Line - Signal Line
        double[] histogram = new double[n];
        for (int i = 0; i < n; i++) {
            histogram[i] = macdLine[i] - signalLine[i];
        }
        return histogram;
    }

    /**
     * EMA 指数移动平均计算
     */
    private static double[] ema(double[] values, int period) {
        int n = values.length;
        double[] result = new double[n];
        double multiplier = 2.0 / (period + 1);

        // SMA 初始化
        int initLen = Math.min(period, n);
        double sum = 0;
        for (int i = 0; i < initLen; i++) {
            sum += values[i];
        }
        result[initLen - 1] = sum / initLen;

        // 递推 EMA
        for (int i = initLen; i < n; i++) {
            result[i] = (values[i] - result[i - 1]) * multiplier + result[i - 1];
        }
        return result;
    }

    /**
     * 计算笔对应K线区间的MACD柱总面积（取绝对值）
     */
    private static double calcMACDArea(Bi bi, double[] histogram) {
        int startIdx = bi.getStart().getIndex();
        int endIdx = bi.getEnd().getIndex();
        if (startIdx < 0 || endIdx >= histogram.length || startIdx > endIdx) {
            return 0;
        }
        double sum = 0;
        for (int i = startIdx; i <= endIdx; i++) {
            sum += Math.abs(histogram[i]);
        }
        return sum;
    }

    /**
     * 二买/二卖的简单背驰判断
     * 当回调笔创新低/新高时，通过价格变化力度判断是否背驰
     * 力度 = 价格变化幅度 / K线数，力度越小说明下跌/上涨动能越弱
     */
    private static boolean isBeiChiSecond(Bi bi, double refPrice, boolean isBuy) {
        if (isBuy) {
            // 创新低后跌幅逐渐缩小 → 背驰
            double power = Math.abs(bi.getHigh() - bi.getLow()) / Math.max(bi.getKlineCount(), 1);
            return bi.getLow() < refPrice && power < 0.3;
        } else {
            // 创新高后涨幅逐渐缩小 → 背驰
            double power = Math.abs(bi.getHigh() - bi.getLow()) / Math.max(bi.getKlineCount(), 1);
            return bi.getHigh() > refPrice && power < 0.3;
        }
    }

    // ==================== 第二类买卖点 ====================

    /**
     * 第二类买卖点：一买/一卖之后，回调不创新低/新高（或创新低但背驰）
     */
    private static List<Signal> generateSecondPoint(List<Bi> bis, List<ZhongShu> zhongShus, ChanLunConfig config, double[] macdHistogram) {
        List<Signal> result = new ArrayList<>();

        // 先找一买/一卖的位置，然后在其后找二买/二卖
        List<Signal> firstPoints = generateFirstPoint(bis, zhongShus, config, macdHistogram);
        for (Signal fp : firstPoints) {
            int fpIndex = fp.getBarIndex();
            double fpPrice = fp.getPrice();
            boolean isBuy = "BUY".equals(fp.getType());

            // 在一买/一卖之后扫描，找对应的二买/二卖
            for (int i = fpIndex + 1; i < bis.size(); i++) {
                Bi bi = bis.get(i);
                // 二买：下跌笔不创新低（或创新低但背驰）
                if (isBuy && "DOWN".equals(bi.getDirection())) {
                    boolean isNewLow = bi.getLow() < fpPrice;
                    if (!isNewLow || isBeiChiSecond(bi, fpPrice, true)) {
                        Signal second = new Signal();
                        second.setId(UUID.randomUUID().toString().substring(0, 8));
                        second.setType("BUY");
                        second.setLevel(2);
                        second.setBarIndex(bi.getEnd().getIndex());
                        second.setPrice(bi.getLow());
                        second.setStrength(isNewLow ? "MEDIUM" : "STRONG");
                        result.add(second);
                        break;
                    }
                }
                // 二卖：上升笔不创新高（或创新高但背驰）
                if (!isBuy && "UP".equals(bi.getDirection())) {
                    boolean isNewHigh = bi.getHigh() > fpPrice;
                    if (!isNewHigh || isBeiChiSecond(bi, fpPrice, false)) {
                        Signal second = new Signal();
                        second.setId(UUID.randomUUID().toString().substring(0, 8));
                        second.setType("SELL");
                        second.setLevel(2);
                        second.setBarIndex(bi.getEnd().getIndex());
                        second.setPrice(bi.getHigh());
                        second.setStrength(isNewHigh ? "MEDIUM" : "STRONG");
                        result.add(second);
                        break;
                    }
                }
            }
        }

        return result;
    }

    // ==================== 第三类买卖点 ====================

    /**
     * 第三类买卖点：突破中枢后回踩不破
     * 三买：向上突破 ZG 后，回抽笔的低点 > ZG
     * 三卖：向下跌破 ZD 后，回抽笔的高点 < ZD
     */
    private static List<Signal> generateThirdPoint(List<Bi> bis, List<ZhongShu> zhongShus, ChanLunConfig config) {
        List<Signal> result = new ArrayList<>();

        for (ZhongShu zs : zhongShus) {
            List<Integer> idx = zs.getComponentIndices();
            int lastIdx = idx.get(idx.size() - 1);

            // 从中枢结束后的下一笔开始检查
            for (int i = lastIdx + 1; i < bis.size() - 1; i++) {
                Bi bi = bis.get(i);
                Bi nextBi = bis.get(i + 1);

                // 三买：离开笔向上突破 ZG，回抽笔向下且低点 > ZG
                if ("UP".equals(bi.getDirection()) && bi.getHigh() > zs.getHigh()) {
                    if ("DOWN".equals(nextBi.getDirection()) && nextBi.getLow() > zs.getHigh()) {
                        Signal buy = new Signal();
                        buy.setId("B3-" + UUID.randomUUID().toString().substring(0, 6));
                        buy.setType("BUY");
                        buy.setLevel(3);
                        buy.setBarIndex(nextBi.getEnd().getIndex());
                        buy.setPrice(nextBi.getLow());
                        buy.setStrength("STRONG");
                        result.add(buy);
                        break;
                    }
                }

                // 三卖：离开笔向下跌破 ZD，回抽笔向上且高点 < ZD
                if ("DOWN".equals(bi.getDirection()) && bi.getLow() < zs.getLow()) {
                    if ("UP".equals(nextBi.getDirection()) && nextBi.getHigh() < zs.getLow()) {
                        Signal sell = new Signal();
                        sell.setId("S3-" + UUID.randomUUID().toString().substring(0, 6));
                        sell.setType("SELL");
                        sell.setLevel(3);
                        sell.setBarIndex(nextBi.getEnd().getIndex());
                        sell.setPrice(nextBi.getHigh());
                        sell.setStrength("STRONG");
                        result.add(sell);
                        break;
                    }
                }
            }
        }

        return result;
    }
}
