package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Bi;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ZhongShu;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 中枢识别器
 * <p>
 * 从笔序列中识别中枢，处理中枢的合并、延伸、扩展。
 * <p>
 * 关键语义：
 * <ul>
 *   <li><b>ZG (high)</b> = min(参与笔的最低价) — 中枢上沿</li>
 *   <li><b>ZD (low)</b>  = max(参与笔的最高价) — 中枢下沿</li>
 *   <li><b>GG (gg)</b>   = max(参与笔的最高价) — 波动高点</li>
 *   <li><b>DD (dd)</b>   = min(参与笔的最低价) — 波动低点</li>
 * </ul>
 */
public class ZhongShuRecognizer {

    private ZhongShuRecognizer() {}

    /**
     * 从笔序列中识别中枢（含合并、延伸、扩展）
     */
    public static List<ZhongShu> recognize(List<Bi> bis, ChanLunConfig config) {
        if (bis == null || bis.size() < 3) return new ArrayList<>();

        // 1. 找出所有初级候选中枢（连续3笔有重叠区间 ZG > ZD）
        List<ZhongShu> candidates = findInitialCandidates(bis);
        if (candidates.isEmpty()) return candidates;

        // 2. 合并相邻滑窗产生的冗余候选中枢（共享笔且区间重叠 → 交集）
        List<ZhongShu> merged = mergeOverlapping(candidates, bis);

        // 3. 延伸：中枢形成后，后续笔若触及 ZG/ZD 区间则纳入
        extendZhongShus(merged, bis);

        // 4. 扩展：完全独立的两个中枢（中间有三买卖点确认），波动区间重叠时形成高级别中枢
        List<ZhongShu> expanded = expandZhongShus(merged, bis);

        // 5. 归并结果
        List<ZhongShu> result = new ArrayList<>();
        result.addAll(merged);
        result.addAll(expanded);
        result.sort((a, b) -> Integer.compare(a.getStartIndex(), b.getStartIndex()));

        return result;
    }

    // ==================== 步骤1：初级候选中枢 ====================

    /**
     * 扫描连续3笔，找出所有有重叠区间的 3笔组合
     * ZG = min(三笔的high), ZD = max(三笔的low), 若 ZG > ZD 则有效
     */
    private static List<ZhongShu> findInitialCandidates(List<Bi> bis) {
        List<ZhongShu> candidates = new ArrayList<>();
        for (int i = 0; i <= bis.size() - 3; i++) {
            double zg = minHigh(bis, i, i + 2);
            double zd = maxLow(bis, i, i + 2);
            if (zg <= zd) continue; // 无重叠，不成中枢

            ZhongShu zs = buildCandidate(bis, i, i + 2, zg, zd);
            candidates.add(zs);
        }
        return candidates;
    }

    /** 构造一个初选中枢，同时计算 GG/DD */
    private static ZhongShu buildCandidate(List<Bi> bis, int startBi, int endBi, double zg, double zd) {
        ZhongShu zs = new ZhongShu();
        zs.setId(UUID.randomUUID().toString().substring(0, 8));
        zs.setType("BI");
        List<Integer> indices = new ArrayList<>();
        for (int j = startBi; j <= endBi; j++) {
            indices.add(j);
        }
        zs.setComponentIndices(indices);
        zs.setStartIndex(bis.get(startBi).getStart().getIndex());
        zs.setEndIndex(bis.get(endBi).getEnd().getIndex());
        zs.setHigh(zg);        // ZG = min(high)
        zs.setLow(zd);         // ZD = max(low)
        zs.setGg(maxHigh(bis, startBi, endBi)); // GG = max(high)
        zs.setDd(minLow(bis, startBi, endBi));  // DD = min(low)
        zs.setDirection(0);
        zs.setGrowthType("NEW");
        return zs;
    }

    // ==================== 步骤2：合并重叠中枢 ====================

    /**
     * 合并相邻/重叠的候选中枢。
     * 相邻中枢共享笔索引且区间重叠时，合并为一个中枢。
     * 合并后的中枢区间(ZG/ZD) = 交集（收缩），波动区间(GG/DD) = 并集（扩张）。
     */
    private static List<ZhongShu> mergeOverlapping(List<ZhongShu> candidates, List<Bi> bis) {
        if (candidates.size() <= 1) return new ArrayList<>(candidates);

        List<ZhongShu> merged = new ArrayList<>();
        ZhongShu current = candidates.get(0);

        for (int i = 1; i < candidates.size(); i++) {
            ZhongShu next = candidates.get(i);
            if (shouldMerge(current, next)) {
                current = mergeTwo(current, next, bis);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * 判断两个中枢是否需要合并：
     * 索引相邻/重叠 且 中枢区间(ZG/ZD)有重叠
     */
    private static boolean shouldMerge(ZhongShu a, ZhongShu b) {
        List<Integer> aIdx = a.getComponentIndices();
        List<Integer> bIdx = b.getComponentIndices();
        int aStart = aIdx.get(0);
        int aEnd = aIdx.get(aIdx.size() - 1);
        int bStart = bIdx.get(0);
        int bEnd = bIdx.get(bIdx.size() - 1);
        // 索引相邻或重叠
        boolean indexAdjacent = !(aEnd + 1 < bStart || bEnd + 1 < aStart);
        // 中枢区间(ZG/ZD)重叠
        boolean rangeOverlap = a.getHigh() > b.getLow() && b.getHigh() > a.getLow();
        return indexAdjacent && rangeOverlap;
    }

    /**
     * 合并两个中枢。
     * <ul>
     *   <li>ZG = min(a.ZG, b.ZG) — 交集更严格</li>
     *   <li>ZD = max(a.ZD, b.ZD) — 交集更严格</li>
     *   <li>GG = max(a.GG, b.GG) — 波动范围并集</li>
     *   <li>DD = min(a.DD, b.DD) — 波动范围并集</li>
     * </ul>
     */
    private static ZhongShu mergeTwo(ZhongShu a, ZhongShu b, List<Bi> bis) {
        ZhongShu zs = new ZhongShu();
        zs.setId(UUID.randomUUID().toString().substring(0, 8));
        zs.setType("BI");

        // 合并笔索引
        List<Integer> aIdx = a.getComponentIndices();
        List<Integer> bIdx = b.getComponentIndices();
        int minIdx = Math.min(aIdx.get(0), bIdx.get(0));
        int maxIdx = Math.max(aIdx.get(aIdx.size() - 1), bIdx.get(bIdx.size() - 1));
        List<Integer> mergedIndices = new ArrayList<>();
        for (int i = minIdx; i <= maxIdx; i++) {
            mergedIndices.add(i);
        }
        zs.setComponentIndices(mergedIndices);

        // 中枢区间取交集（收缩）
        zs.setHigh(Math.min(a.getHigh(), b.getHigh()));   // ZG
        zs.setLow(Math.max(a.getLow(), b.getLow()));      // ZD
        // 波动区间取并集（扩张）
        zs.setGg(Math.max(a.getGg(), b.getGg()));         // GG
        zs.setDd(Math.min(a.getDd(), b.getDd()));         // DD

        zs.setStartIndex(Math.min(a.getStartIndex(), b.getStartIndex()));
        zs.setEndIndex(Math.max(a.getEndIndex(), b.getEndIndex()));
        zs.setDirection(0);
        zs.setGrowthType("NEW");
        return zs;
    }

    // ==================== 步骤3：中枢延伸 ====================

    /**
     * 中枢延伸：中枢形成后，后续的笔若触及 ZG~ZD 区间，则纳入该中枢继续延伸。
     * 笔数 ≥ 9 时标记为 EXTEND（级别升级信号）。
     */
    private static void extendZhongShus(List<ZhongShu> zhongShus, List<Bi> bis) {
        for (ZhongShu zs : zhongShus) {
            extendSingle(zs, bis);
        }
    }

    private static void extendSingle(ZhongShu zs, List<Bi> bis) {
        List<Integer> indices = zs.getComponentIndices();
        int lastIdx = indices.get(indices.size() - 1);

        for (int i = lastIdx + 1; i < bis.size(); i++) {
            // 已延伸至 9 笔 → 截断，不再继续延伸
            if (indices.size() >= 9) {
                zs.setGrowthType("EXTEND");
                break;
            }
            Bi bi = bis.get(i);
            // 笔的价格区间与中枢 ZG~ZD 有交集 → 触及中枢 → 纳入延伸
            boolean touches = bi.getHigh() >= zs.getLow() && bi.getLow() <= zs.getHigh();
            if (!touches) break;

            indices.add(i);
            zs.setEndIndex(bi.getEnd().getIndex());
            // 更新波动区间(GG/DD)，中枢区间(ZG/ZD)不变
            zs.setGg(Math.max(zs.getGg(), bi.getHigh()));
            zs.setDd(Math.min(zs.getDd(), bi.getLow()));
        }

        zs.setComponentIndices(indices);
    }

    // ==================== 步骤4：中枢扩展 ====================

    /**
     * 中枢扩展：两个完全独立的同级中枢，其波动区间(GG/DD)重叠，形成高级别中枢。
     * <p>
     * 独立性约束：两个中枢之间至少间隔 3 笔（一个完整的次级别走势），
     * 对应缠论中"离开 + 回抽"的三买卖点确认过程。
     * <p>
     * 扩展产生的新中枢：
     * <ul>
     *   <li>ZG/ZD = 两个波动区间的交集 [min(GG₁,GG₂), max(DD₁,DD₂)]</li>
     *   <li>GG/DD = 两个波动区间的并集 [min(DD₁,DD₂), max(GG₁,GG₂)]</li>
     *   <li>growthType = EXPAND</li>
     * </ul>
     */
    private static List<ZhongShu> expandZhongShus(List<ZhongShu> zhongShus, List<Bi> bis) {
        List<ZhongShu> expanded = new ArrayList<>();
        if (zhongShus.size() < 2) return expanded;

        for (int i = 0; i < zhongShus.size() - 1; i++) {
            ZhongShu a = zhongShus.get(i);
            ZhongShu b = zhongShus.get(i + 1);

            // 必须完全独立：至少间隔 3 笔（一个次级别走势的离开+回抽）
            List<Integer> aIdx = a.getComponentIndices();
            List<Integer> bIdx = b.getComponentIndices();
            int aLast = aIdx.get(aIdx.size() - 1);
            int bFirst = bIdx.get(0);
            if (bFirst <= aLast + 3) continue;

            // 波动区间(GG/DD)重叠检查
            if (a.getGg() <= b.getDd() || b.getGg() <= a.getDd()) continue;

            // 形成扩展中枢
            ZhongShu expandedZs = new ZhongShu();
            expandedZs.setId("EXP-" + UUID.randomUUID().toString().substring(0, 6));
            expandedZs.setType("BI");

            // 合并笔索引（包含中间的过渡笔）
            int minIdx = aIdx.get(0);
            int maxIdx = bIdx.get(bIdx.size() - 1);
            List<Integer> mergedIndices = new ArrayList<>();
            for (int j = minIdx; j <= maxIdx; j++) {
                mergedIndices.add(j);
            }
            expandedZs.setComponentIndices(mergedIndices);
            expandedZs.setStartIndex(a.getStartIndex());
            expandedZs.setEndIndex(b.getEndIndex());

            // 扩展中枢的 ZG/ZD = 两个波动区间的交集
            expandedZs.setHigh(Math.min(a.getGg(), b.getGg()));   // ZG
            expandedZs.setLow(Math.max(a.getDd(), b.getDd()));    // ZD
            // 波动区间(GG/DD) = 两个波动区间的并集（用于显示）
            expandedZs.setGg(Math.max(a.getGg(), b.getGg()));
            expandedZs.setDd(Math.min(a.getDd(), b.getDd()));

            expandedZs.setDirection(0);
            expandedZs.setGrowthType("EXPAND");
            expanded.add(expandedZs);
        }
        return expanded;
    }

    // ==================== 辅助方法 ====================

    private static double minHigh(List<Bi> bis, int start, int end) {
        double v = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            v = Math.min(v, bis.get(i).getHigh());
        }
        return v;
    }

    private static double maxLow(List<Bi> bis, int start, int end) {
        double v = Double.MIN_VALUE;
        for (int i = start; i <= end; i++) {
            v = Math.max(v, bis.get(i).getLow());
        }
        return v;
    }

    private static double maxHigh(List<Bi> bis, int start, int end) {
        double v = Double.MIN_VALUE;
        for (int i = start; i <= end; i++) {
            v = Math.max(v, bis.get(i).getHigh());
        }
        return v;
    }

    private static double minLow(List<Bi> bis, int start, int end) {
        double v = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            v = Math.min(v, bis.get(i).getLow());
        }
        return v;
    }
}
