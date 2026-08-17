package com.chain.ai.trade.extension.ta4j.indicator;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;

import java.util.*;

public class SmcCriticalLevelsCalculator {

    private static final List<String> ENTRY_PERIOD_PRIORITY = List.of("15M", "1H", "4H", "1D");
    private static final double MAX_ENTRY_DISTANCE_PCT = 3.0;
    private static final double STOP_LOSS_FACTOR = 0.002;
    private static final double DEDUP_PRICE_TOLERANCE = 0.0001;

    private static final String RAW_SWING_OB = "SwingOB";
    private static final String RAW_OB = "OB";
    private static final String RAW_EQH = "EQH";
    private static final String RAW_EQL = "EQL";
    private static final String RAW_FVG = "FVG";
    private static final String RAW_SL = "SL";

    private SmcCriticalLevelsCalculator() {
    }

    /**
     * 文档第5.3节: 按方向构建关键点位
     * 入口: 入场(5.3.1) → 止损(5.3.2) → 止盈1(5.3.3) → 止盈2(5.3.4)
     * 周期范围: 仅15M/1H/4H/1D (3M仅用于方向判断)
     * @param results          多周期SMC计算结果
     * @param direction        "buy"=做多, "sell"=做空
     * @param currentPrice     当前价格
     * @param entryObTypeFilter 入场OB类型: INTERNAL_ONLY/SWING_ONLY/ALL
     */
    public static List<CriticalLevel> buildByDirection(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice,
            String entryObTypeFilter) {

        boolean isLong = "buy".equalsIgnoreCase(direction);
        if (entryObTypeFilter == null) entryObTypeFilter = "ALL";

        List<CriticalLevel> allBullOBs = new ArrayList<>();
        List<CriticalLevel> allBearOBs = new ArrayList<>();
        List<CriticalLevel> bullSwingOBs = new ArrayList<>();
        List<CriticalLevel> bearSwingOBs = new ArrayList<>();
        List<CriticalLevel> eqlLevels = new ArrayList<>();
        List<CriticalLevel> eqhLevels = new ArrayList<>();
        List<CriticalLevel> bullFVGs = new ArrayList<>();
        List<CriticalLevel> bearFVGs = new ArrayList<>();

        List<CriticalLevel> bullOBs_15M = new ArrayList<>();
        List<CriticalLevel> bullOBs_1H = new ArrayList<>();
        List<CriticalLevel> bullOBs_4H = new ArrayList<>();
        List<CriticalLevel> bullOBs_1D = new ArrayList<>();
        List<CriticalLevel> bearOBs_15M = new ArrayList<>();
        List<CriticalLevel> bearOBs_1H = new ArrayList<>();
        List<CriticalLevel> bearOBs_4H = new ArrayList<>();
        List<CriticalLevel> bearOBs_1D = new ArrayList<>();

        for (Map.Entry<String, SmartMoneyConceptsIndicator.Result> entry : results.entrySet()) {
            String label = entry.getKey();
            SmartMoneyConceptsIndicator.Result r = entry.getValue();
            if (r == null) continue;
            if (!ENTRY_PERIOD_PRIORITY.contains(label)) continue;

            collectOrderBlocks(r, label, allBullOBs, allBearOBs,
                    bullSwingOBs, bearSwingOBs,
                    bullOBs_15M, bullOBs_1H, bullOBs_4H, bullOBs_1D,
                    bearOBs_15M, bearOBs_1H, bearOBs_4H, bearOBs_1D);

            collectFVGs(r, label, bullFVGs, bearFVGs);
            collectEQHEQL(r, label, eqhLevels, eqlLevels);
        }

        List<CriticalLevel> curated = new ArrayList<>();

        // 文档第5.3.1节: 构建入场候选池 → 按周期优先级(15M>1H>4H>1D) + 距离排序 → 取最近
        List<CriticalLevel> entryPool = buildEntryPool(
                isLong, entryObTypeFilter,
                bullOBs_15M, bullOBs_1H, bullOBs_4H, bullOBs_1D,
                bearOBs_15M, bearOBs_1H, bearOBs_4H, bearOBs_1D);

        // 入场距离过滤: 超过当前价格3%的排除
        entryPool.removeIf(l -> {
            double dist = Math.abs(l.getPrice() - currentPrice) / currentPrice * 100;
            return dist > MAX_ENTRY_DISTANCE_PCT;
        });

        entryPool.sort((a, b) -> {
            int pa = ENTRY_PERIOD_PRIORITY.indexOf(a.getPeriod());
            int pb = ENTRY_PERIOD_PRIORITY.indexOf(b.getPeriod());
            if (pa != pb) return Integer.compare(pa, pb);
            return Double.compare(
                    Math.abs(a.getPrice() - currentPrice),
                    Math.abs(b.getPrice() - currentPrice));
        });

        if (!entryPool.isEmpty()) {
            CriticalLevel entry = entryPool.get(0);
            entry.setAction("入场");
            entry.setPriority(1);
            curated.add(entry);

            // 文档第5.3.2节: 基于入场订单块生成止损位 (入场OB低/高点 × (1±0.002))
            CriticalLevel sl = new CriticalLevel();
            sl.setType(RAW_SL);
            sl.setSide("止损");
            sl.setPeriod(entry.getPeriod());
            sl.setAction("止损");
            sl.setPriority(1);
            if (isLong) {
                sl.setPrice(Math.round(entry.getLow() * (1 - STOP_LOSS_FACTOR) * 100.0) / 100.0);
            } else {
                sl.setPrice(Math.round(entry.getHigh() * (1 + STOP_LOSS_FACTOR) * 100.0) / 100.0);
            }
            curated.add(sl);
        }

        // 文档第5.3.3节: 止盈1 — 优先取反向SwingOB中距离最近的, 无则fallback到反向内部OB
        List<CriticalLevel> oppositeSwingOBs = isLong ? bearSwingOBs : bullSwingOBs;
        List<CriticalLevel> oppositeAllOBs = isLong ? allBearOBs : allBullOBs;

        oppositeSwingOBs.sort(Comparator.comparingDouble(
                l -> Math.abs(l.getPrice() - currentPrice)));
        oppositeAllOBs.sort(Comparator.comparingDouble(
                l -> Math.abs(l.getPrice() - currentPrice)));

        CriticalLevel tp1 = null;
        for (CriticalLevel l : oppositeSwingOBs) {
            if (l.getHigh() == null || l.getLow() == null) continue;
            if (isLong) {
                if (l.getPrice() > currentPrice && l.getHigh() > currentPrice) {
                    tp1 = l;
                    break;
                }
            } else {
                if (l.getPrice() < currentPrice && l.getLow() < currentPrice) {
                    tp1 = l;
                    break;
                }
            }
        }
        if (tp1 == null) {
            for (CriticalLevel l : oppositeAllOBs) {
                if (l.getHigh() == null || l.getLow() == null) continue;
                if (isLong) {
                    if (l.getPrice() > currentPrice && l.getHigh() > currentPrice) {
                        tp1 = l;
                        break;
                    }
                } else {
                    if (l.getPrice() < currentPrice && l.getLow() < currentPrice) {
                        tp1 = l;
                        break;
                    }
                }
            }
        }
        if (tp1 != null) {
            tp1.setAction("止盈");
            tp1.setPriority(1);
            curated.add(tp1);
        }

        // 文档第5.3.4节: 止盈2 — 始终输出, 来源优先级: 流动性池(EQH/EQL) > FVG
        List<CriticalLevel> tp2Candidates = new ArrayList<>();
        if (isLong) {
            for (CriticalLevel l : eqhLevels) {
                if (l.getPrice() > currentPrice) tp2Candidates.add(l);
            }
            for (CriticalLevel l : bearFVGs) {
                if (l.getPrice() > currentPrice) tp2Candidates.add(l);
            }
        } else {
            for (CriticalLevel l : eqlLevels) {
                if (l.getPrice() < currentPrice) tp2Candidates.add(l);
            }
            for (CriticalLevel l : bullFVGs) {
                if (l.getPrice() < currentPrice) tp2Candidates.add(l);
            }
        }

        tp2Candidates.sort(Comparator.comparingDouble(
                l -> Math.abs(l.getPrice() - currentPrice)));

        CriticalLevel tp2 = null;
        for (CriticalLevel l : tp2Candidates) {
            if (tp1 != null && isSamePrice(l.getPrice(), tp1.getPrice())) continue;
            if (tp1 != null && l.getPeriod().equals(tp1.getPeriod())
                    && Math.abs(l.getPrice() - tp1.getPrice()) / currentPrice * 100 < 0.01) continue;
            tp2 = l;
            break;
        }
        if (tp2 != null) {
            tp2.setAction("止盈");
            tp2.setPriority(2);
            curated.add(tp2);
        }

        // 文档第5.3.5节: 最终去重 + 计算距离百分比 + type转中文
        Set<String> seen = new HashSet<>();
        List<CriticalLevel> deduped = new ArrayList<>();
        for (CriticalLevel level : curated) {
            String key = level.getType() + "|" + level.getPeriod() + "|" + level.getAction() + "|" + level.getPriority();
            if (seen.add(key)) {
                deduped.add(level);
            }
        }
        curated = deduped;

        for (CriticalLevel level : curated) {
            if (level.getPrice() != null && !Double.isNaN(currentPrice) && currentPrice != 0) {
                double distance = Math.abs(level.getPrice() - currentPrice) / currentPrice * 100;
                if ("止损".equals(level.getAction())) {
                    distance = -distance;
                }
                level.setDistancePercent(Math.round(distance * 100.0) / 100.0);
            }
            level.setType(toDisplayType(level.getType(), level.getSide()));
        }

        return curated;
    }

    /**
     * 基于复合状态构建关键点位（初始与buildByDirection逻辑一致）
     * 后续逐步基于compositeState做差异化调整，替代旧版entryObTypeFilter硬编码
     *
     * @param results          多周期SMC计算结果
     * @param direction        "buy"=做多, "sell"=做空
     * @param currentPrice     当前价格
     * @param entryObTypeFilter 入场OB类型: INTERNAL_ONLY/SWING_ONLY/ALL
     * @param compositeState   21种复合状态（供后续精细化使用）
     */
    public static List<CriticalLevel> buildByCompositeState(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice,
            String entryObTypeFilter,
            CompositeState compositeState) {
        boolean isLong = "buy".equalsIgnoreCase(direction);

        // STRONG_BULLISH_WARNING_4H：只允许BUY，用专用逻辑
        if (compositeState == CompositeState.STRONG_BULLISH_WARNING_4H && isLong) {
            return buildWarning4H(results, currentPrice, true);
        }
        // STRONG_BEARISH_WARNING_4H：只允许SELL，用专用逻辑
        if (compositeState == CompositeState.STRONG_BEARISH_WARNING_4H && !isLong) {
            return buildWarning4H(results, currentPrice, false);
        }

        // 其他状态保持原有逻辑
        return buildByDirection(results, direction, currentPrice, entryObTypeFilter);
    }

    private static String toDisplayType(String rawType, String side) {
        if (RAW_SWING_OB.equals(rawType) || RAW_OB.equals(rawType)) {
            return "看跌".equals(side) ? "看跌订单块" : "看涨订单块";
        }
        if (RAW_EQH.equals(rawType) || RAW_EQL.equals(rawType)) {
            return "流动性池";
        }
        if (RAW_FVG.equals(rawType)) {
            return "FVG";
        }
        if (RAW_SL.equals(rawType)) {
            return "止损位";
        }
        return rawType;
    }

    /**
     * STRONG_BULLISH_WARNING_4H / STRONG_BEARISH_WARNING_4H 专用构建逻辑
     * 文档：4H内部趋势已反向（出现内部CHoCH），逆势开单高风险
     *  - TP1: 15M LastSwingHigh/Low（快进快出主目标）
     *  - TP2: 最近需求/供给区上沿/下沿（最近订单块边界）
     *  - SL: 4H最近同方向内部OB边界 × (1±0.002)
     */
    private static List<CriticalLevel> buildWarning4H(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            double currentPrice,
            boolean isLong) {
        SmartMoneyConceptsIndicator.Result r15m = results.get("15M");
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        if (r15m == null || r4h == null) {
            return List.of();
        }

        // 1. 入场：复用现有入场候选逻辑
        String dir = isLong ? "buy" : "sell";
        List<CriticalLevel> base = buildByDirection(results, dir, currentPrice, "ALL");
        CriticalLevel entry = null;
        for (CriticalLevel l : base) {
            if ("入场".equals(l.getAction())) {
                entry = l;
                break;
            }
        }
        if (entry == null) {
            return base;
        }

        List<CriticalLevel> curated = new ArrayList<>();
        curated.add(entry);

        // 2. 止损（SL）：入场级别（15M）最近反向内部OB边界 × (1±0.005)，紧止损原则
        double slPrice;
        if (isLong) {
            double nearestBullishObLow = findNearestObBoundaryByBias(r15m, currentPrice, 1, true);
            slPrice = Math.round(nearestBullishObLow * (1 - 0.005) * 100.0) / 100.0;
        } else {
            double nearestBearishObHigh = findNearestObBoundaryByBias(r15m, currentPrice, -1, false);
            slPrice = Math.round(nearestBearishObHigh * (1 + 0.005) * 100.0) / 100.0;
        }
        CriticalLevel sl = new CriticalLevel();
        sl.setType(RAW_SL);
        sl.setSide("止损");
        sl.setPeriod("15M");
        sl.setAction("止损");
        sl.setPriority(1);
        sl.setPrice(slPrice);
        curated.add(sl);

        // 3. 止盈1（TP1）：15M强高/强低（快进快出主目标）
        CriticalLevel tp1 = new CriticalLevel();
        tp1.setType(RAW_SL);
        tp1.setSide("止盈");
        tp1.setPeriod("15M");
        tp1.setAction("止盈");
        tp1.setPriority(1);
        tp1.setPrice(isLong ? r15m.getStrongHigh() : r15m.getStrongLow());
        curated.add(tp1);

        // 4. 止盈2（TP2）：最近需求/供给区边界（外层调用可能将入场覆盖为市价，故用currentPrice筛选）
        CriticalLevel tp2 = new CriticalLevel();
        tp2.setType(RAW_SL);
        tp2.setSide("止盈");
        tp2.setPeriod(isLong ? "最近供给区" : "最近需求区");
        tp2.setAction("止盈");
        tp2.setPriority(2);
        if (isLong) {
            // NearestSupplyZoneBottom: bias=-1（供给）且 barLow > currentPrice，取min barLow
            tp2.setPrice(findNearestSupplyZoneBottom(results, currentPrice));
        } else {
            // NearestDemandZoneTop: bias=1（需求）且 barHigh < currentPrice，取max barHigh
            tp2.setPrice(findNearestDemandZoneTop(results, currentPrice));
        }
        curated.add(tp2);

        // 5. 去重 + 距离百分比计算
        Set<String> seen = new HashSet<>();
        List<CriticalLevel> deduped = new ArrayList<>();
        for (CriticalLevel level : curated) {
            String key = level.getType() + "|" + level.getPeriod() + "|" + level.getAction() + "|" + level.getPriority();
            if (seen.add(key)) {
                deduped.add(level);
            }
        }

        for (CriticalLevel level : deduped) {
            if (level.getPrice() != null && !Double.isNaN(currentPrice) && currentPrice != 0) {
                double distance = Math.abs(level.getPrice() - currentPrice) / currentPrice * 100;
                if ("止损".equals(level.getAction())) {
                    distance = -distance;
                }
                level.setDistancePercent(Math.round(distance * 100.0) / 100.0);
            }
            level.setType(toDisplayType(level.getType(), level.getSide()));
        }

        return deduped;
    }

    /**
     * 从指定周期的internalOrderBlocks中，按bias筛选，取距离currentPrice最近的边界值
     * @param result       周期结果
     * @param currentPrice 当前价格
     * @param bias         1=需求区（看涨），-1=供给区（看跌）
     * @param returnLow    true返回barLow，false返回barHigh
     */
    private static double findNearestObBoundaryByBias(
            SmartMoneyConceptsIndicator.Result result, double currentPrice, int bias, boolean returnLow) {
        double nearest = Double.MAX_VALUE;
        double boundary = 0;
        if (result.getInternalOrderBlocks() != null) {
            for (SmartMoneyConceptsIndicator.OrderBlock ob : result.getInternalOrderBlocks()) {
                if (ob.bias == bias) {
                    double val = returnLow ? ob.barLow : ob.barHigh;
                    double dist = Math.abs(val - currentPrice);
                    if (dist < nearest) {
                        nearest = dist;
                        boundary = val;
                    }
                }
            }
        }
        return boundary;
    }

    /**
     * NearestSupplyZoneBottom: 从15M、1H、4H的internalOrderBlocks中，
     * 筛选bias=-1（供给）且 barLow > EntryPrice 的所有OB，取barLow最小值
     */
    private static double findNearestSupplyZoneBottom(
            Map<String, SmartMoneyConceptsIndicator.Result> results, double entryPrice) {
        double minBarLow = Double.MAX_VALUE;
        for (String period : List.of("15M", "1H", "4H")) {
            SmartMoneyConceptsIndicator.Result r = results.get(period);
            if (r == null || r.getInternalOrderBlocks() == null) continue;
            for (SmartMoneyConceptsIndicator.OrderBlock ob : r.getInternalOrderBlocks()) {
                if (ob.bias == -1 && ob.barLow > entryPrice && ob.barLow < minBarLow) {
                    minBarLow = ob.barLow;
                }
            }
        }
        return minBarLow == Double.MAX_VALUE ? 0 : minBarLow;
    }

    /**
     * NearestDemandZoneTop: 从15M、1H、4H的internalOrderBlocks中，
     * 筛选bias=1（需求）且 barHigh < EntryPrice 的所有OB，取barHigh最大值
     */
    private static double findNearestDemandZoneTop(
            Map<String, SmartMoneyConceptsIndicator.Result> results, double entryPrice) {
        double maxBarHigh = Double.MIN_VALUE;
        for (String period : List.of("15M", "1H", "4H")) {
            SmartMoneyConceptsIndicator.Result r = results.get(period);
            if (r == null || r.getInternalOrderBlocks() == null) continue;
            for (SmartMoneyConceptsIndicator.OrderBlock ob : r.getInternalOrderBlocks()) {
                if (ob.bias == 1 && ob.barHigh < entryPrice && ob.barHigh > maxBarHigh) {
                    maxBarHigh = ob.barHigh;
                }
            }
        }
        return maxBarHigh == Double.MIN_VALUE ? 0 : maxBarHigh;
    }

    private static List<CriticalLevel> buildEntryPool(
            boolean isLong, String entryObTypeFilter,
            List<CriticalLevel> bullOBs_15M, List<CriticalLevel> bullOBs_1H,
            List<CriticalLevel> bullOBs_4H, List<CriticalLevel> bullOBs_1D,
            List<CriticalLevel> bearOBs_15M, List<CriticalLevel> bearOBs_1H,
            List<CriticalLevel> bearOBs_4H, List<CriticalLevel> bearOBs_1D) {

        List<CriticalLevel> pool = new ArrayList<>();
        List<List<CriticalLevel>> periodLists = isLong
                ? List.of(bullOBs_15M, bullOBs_1H, bullOBs_4H, bullOBs_1D)
                : List.of(bearOBs_15M, bearOBs_1H, bearOBs_4H, bearOBs_1D);

        if ("SWING_ONLY".equals(entryObTypeFilter)) {
            for (CriticalLevel l : (isLong ? bullOBs_1H : bearOBs_1H)) {
                if (RAW_SWING_OB.equals(l.getType())) pool.add(l);
            }
        } else if ("INTERNAL_ONLY".equals(entryObTypeFilter)) {
            for (List<CriticalLevel> list : periodLists) {
                for (CriticalLevel l : list) {
                    if (RAW_OB.equals(l.getType())) pool.add(l);
                }
            }
        } else {
            for (List<CriticalLevel> list : periodLists) {
                pool.addAll(list);
            }
        }
        return pool;
    }

    private static boolean isSamePrice(Double a, Double b) {
        if (a == null || b == null) return false;
        return Math.abs(a - b) / Math.max(Math.abs(a), Math.abs(b)) < DEDUP_PRICE_TOLERANCE;
    }

    private static void collectOrderBlocks(
            SmartMoneyConceptsIndicator.Result r, String label,
            List<CriticalLevel> bullOBs, List<CriticalLevel> bearOBs,
            List<CriticalLevel> bullSwingOBs, List<CriticalLevel> bearSwingOBs,
            List<CriticalLevel> bullOBs_15M, List<CriticalLevel> bullOBs_1H,
            List<CriticalLevel> bullOBs_4H, List<CriticalLevel> bullOBs_1D,
            List<CriticalLevel> bearOBs_15M, List<CriticalLevel> bearOBs_1H,
            List<CriticalLevel> bearOBs_4H, List<CriticalLevel> bearOBs_1D) {

        if (r.getSwingOrderBlocks() != null) {
            for (SmartMoneyConceptsIndicator.OrderBlock ob : r.getSwingOrderBlocks()) {
                addOB(ob, RAW_SWING_OB, label, bullOBs, bearOBs,
                        bullSwingOBs, bearSwingOBs,
                        bullOBs_15M, bullOBs_1H, bullOBs_4H, bullOBs_1D,
                        bearOBs_15M, bearOBs_1H, bearOBs_4H, bearOBs_1D);
            }
        }
        if (r.getInternalOrderBlocks() != null) {
            for (SmartMoneyConceptsIndicator.OrderBlock ob : r.getInternalOrderBlocks()) {
                addOB(ob, RAW_OB, label, bullOBs, bearOBs,
                        bullSwingOBs, bearSwingOBs,
                        bullOBs_15M, bullOBs_1H, bullOBs_4H, bullOBs_1D,
                        bearOBs_15M, bearOBs_1H, bearOBs_4H, bearOBs_1D);
            }
        }
    }

    private static void addOB(
            SmartMoneyConceptsIndicator.OrderBlock ob, String rawType, String label,
            List<CriticalLevel> bullOBs, List<CriticalLevel> bearOBs,
            List<CriticalLevel> bullSwingOBs, List<CriticalLevel> bearSwingOBs,
            List<CriticalLevel> bullOBs_15M, List<CriticalLevel> bullOBs_1H,
            List<CriticalLevel> bullOBs_4H, List<CriticalLevel> bullOBs_1D,
            List<CriticalLevel> bearOBs_15M, List<CriticalLevel> bearOBs_1H,
            List<CriticalLevel> bearOBs_4H, List<CriticalLevel> bearOBs_1D) {

        CriticalLevel level = new CriticalLevel();
        level.setType(rawType);
        level.setHigh(ob.barHigh);
        level.setLow(ob.barLow);
        level.setPrice((ob.barHigh + ob.barLow) / 2.0);
        level.setPeriod(label);

        if (ob.bias == -1) {
            level.setSide("看跌");
            bearOBs.add(level);
            if (RAW_SWING_OB.equals(rawType)) bearSwingOBs.add(level);
            if ("15M".equals(label)) bearOBs_15M.add(level);
            else if ("1H".equals(label)) bearOBs_1H.add(level);
            else if ("4H".equals(label)) bearOBs_4H.add(level);
            else if ("1D".equals(label)) bearOBs_1D.add(level);
        } else {
            level.setSide("看涨");
            bullOBs.add(level);
            if (RAW_SWING_OB.equals(rawType)) bullSwingOBs.add(level);
            if ("15M".equals(label)) bullOBs_15M.add(level);
            else if ("1H".equals(label)) bullOBs_1H.add(level);
            else if ("4H".equals(label)) bullOBs_4H.add(level);
            else if ("1D".equals(label)) bullOBs_1D.add(level);
        }
    }

    private static void collectFVGs(
            SmartMoneyConceptsIndicator.Result r, String label,
            List<CriticalLevel> bullFVGs, List<CriticalLevel> bearFVGs) {

        if (r.isBullishFairValueGap()) {
            CriticalLevel fvg = new CriticalLevel();
            fvg.setType(RAW_FVG);
            fvg.setSide("看涨");
            fvg.setHigh(r.getLastBullishFVGTop());
            fvg.setLow(r.getLastBullishFVGBottom());
            fvg.setPrice((r.getLastBullishFVGTop() + r.getLastBullishFVGBottom()) / 2.0);
            fvg.setPeriod(label);
            bullFVGs.add(fvg);
        }
        if (r.isBearishFairValueGap()) {
            CriticalLevel fvg = new CriticalLevel();
            fvg.setType(RAW_FVG);
            fvg.setSide("看跌");
            fvg.setHigh(r.getLastBearishFVGTop());
            fvg.setLow(r.getLastBearishFVGBottom());
            fvg.setPrice((r.getLastBearishFVGTop() + r.getLastBearishFVGBottom()) / 2.0);
            fvg.setPeriod(label);
            bearFVGs.add(fvg);
        }
    }

    private static void collectEQHEQL(
            SmartMoneyConceptsIndicator.Result r, String label,
            List<CriticalLevel> eqhLevels, List<CriticalLevel> eqlLevels) {

        if (r.isEqualHighs()) {
            CriticalLevel eqh = new CriticalLevel();
            eqh.setType(RAW_EQH);
            eqh.setSide("阻力");
            eqh.setPrice(r.getLastSwingHigh());
            eqh.setPeriod(label);
            eqhLevels.add(eqh);
        }
        if (r.isEqualLows()) {
            CriticalLevel eql = new CriticalLevel();
            eql.setType(RAW_EQL);
            eql.setSide("支撑");
            eql.setPrice(r.getLastSwingLow());
            eql.setPeriod(label);
            eqlLevels.add(eql);
        }
    }
}
