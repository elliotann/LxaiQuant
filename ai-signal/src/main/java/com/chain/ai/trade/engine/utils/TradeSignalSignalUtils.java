package com.chain.ai.trade.engine.utils;



import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chain.ai.trade.engine.signal.entity.vo.TradeSignalSignalVo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TradeSignalSignalUtils {
    /**
     * 合并两个列表，按指定字段去重，并将重复项的 trend 字段相加。
     */
    public static List<TradeSignalSignalVo> mergeAndSumTrend(
            List<TradeSignalSignalVo> list1,
            List<TradeSignalSignalVo> list2) {

        return Stream.concat(list1.stream(), list2.stream())
                .collect(Collectors.toMap(
                        // 定义去重的复合键（五个字段）
                        signal -> new CompositeKey(
                                signal.getDataFrom(),
                                signal.getKlineTime(),
                                signal.getSymbol(),
                                signal.getIndicatorType()
                        ),
                        Function.identity(),
                        // 合并策略：累加trend，其他字段保留第一个记录的
                        (existing, replacement) -> mergeSignals(existing, replacement)
                ))
                .values().stream()
                // 按klineTime排序（假设升序）
                .sorted(Comparator.comparing(TradeSignalSignalVo::getKlineTime))
                .collect(Collectors.toList());
    }


    /**
     * 合并两个重复的信号对象，累加 trend 字段。
     */
    private static TradeSignalSignalVo mergeSignals(
            TradeSignalSignalVo existing,
            TradeSignalSignalVo replacement) {

        // 将 trend 转换为 BigDecimal 并相加
        String existingTrend = parseTrend(existing.getTrend());
        String replacementTrend = parseTrend(replacement.getTrend());
        String sum = existingTrend+replacementTrend;

        // 创建新对象（或修改现有对象），保留 existing 的字段，更新 trend
        // 注意：如果对象不可变，需要手动复制所有字段


       /* List<String> trends = new ArrayList<>();
        trends.add(existingTrend);
        trends.add(replacementTrend);
        existing.setTrend(JSONUtil.toJsonStr(trends));*/

        // 2. 处理 orderAction：优先保留 existing 的非空值，否则用 replacement 的值
        if (replacement.getOrderAction() != null) {
            existing.setOrderAction(replacement.getOrderAction());
            existing.setOrderActions(replacement.getOrderActions());
        }
        if("2025-05-11 07:15:00".equals(existing.getKlineTime())){
            System.out.println("here");
        }
        if(!StringUtils.isEmpty(existing.getTrend())){
            existing.addTrend(existing.getTrend());
        }
        if(replacement.getTrends()!=null){
            existing.addTrendList(replacement.getTrends());
        }

        return existing;
    }

    /**
     * 解析 trend 字符串为 BigDecimal（处理可能的格式错误）。
     */
    private static String parseTrend(String trend) {
        try {
            return trend;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid trend value: " + trend, e);
        }
    }

    /**
     * 定义复合键类，用于去重分组。
     */
    private static class CompositeKey {
        private final String dataFrom;
        private final String klineTime;
        private final String symbol;
        private final String indicatorType;

        public CompositeKey(String dataFrom,  String klineTime,
                            String symbol, String indicatorType) {
            this.dataFrom = dataFrom;
            this.klineTime = klineTime;
            this.symbol = symbol;
            this.indicatorType = indicatorType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompositeKey that = (CompositeKey) o;
            return dataFrom.equals(that.dataFrom) &&
                    klineTime.equals(that.klineTime) &&
                    symbol.equals(that.symbol) &&
                    indicatorType.equals(that.indicatorType);
        }

        @Override
        public int hashCode() {
            int result = dataFrom.hashCode();
            result = 31 * result + klineTime.hashCode();
            result = 31 * result + symbol.hashCode();
            result = 31 * result + indicatorType.hashCode();
            return result;
        }
    }

    public static List<TradeSignalSignalVo> deduplicate(List<TradeSignalSignalVo> originalList) {
        return originalList.stream()
                .collect(Collectors.toMap(
                        vo -> Arrays.asList(
                                vo.getDataFrom(),
                                vo.getKlineTime(),
                                vo.getSymbol(),
                                vo.getIndicatorType()
                        ),
                        Function.identity(),
                        (oldVo, newVo) -> {
                            mergeOrderActions(oldVo, newVo);
                            return oldVo;
                        },
                        LinkedHashMap::new // 确保已导入 java.util.LinkedHashMap
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    private static void mergeOrderActions(TradeSignalSignalVo target, TradeSignalSignalVo source) {
        if("2025-05-11 08:25:00".equals(target.getKlineTime())){
            System.out.println("here");
        }
        if(source.getOrderAction()==target.getOrderAction()){
            target.addOrderAction(source.getOrderAction());
        }else{
            if (source.getOrderAction() != null) {
                target.addOrderAction(source.getOrderAction());
            }
            if (target.getOrderAction()!=null) {
                target.addOrderAction(target.getOrderAction());
            }
        }

        if(source.getTrend().equals(target.getTrend())){
            target.setTrend(target.getTrend());
        }else{
            if (!StringUtils.isEmpty(source.getTrend())) {
                target.addTrend(source.getTrend());
            }
            if (!StringUtils.isEmpty(target.getTrend())) {
                target.addTrend(target.getTrend());
            }
        }
    }
}
