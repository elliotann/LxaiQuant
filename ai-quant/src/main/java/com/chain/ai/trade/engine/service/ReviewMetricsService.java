package com.chain.ai.trade.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.engine.entity.ReviewMetrics;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.mapper.TradeOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewMetricsService {

    private final TradeOrderMapper tradeOrderMapper;

    public ReviewMetrics calculate(String robotId, Date timeRangeStart, Date timeRangeEnd) {
        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getRobotId, robotId);
        wrapper.in(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.CLOSE,
                TradePosition.TradeOrderStatus.LOSS, TradePosition.TradeOrderStatus.GAIN);
        if (timeRangeStart != null) {
            wrapper.ge(TradePosition::getSellTime, timeRangeStart);
        }
        if (timeRangeEnd != null) {
            wrapper.le(TradePosition::getSellTime, timeRangeEnd);
        }
        wrapper.orderByAsc(TradePosition::getSellTime);

        List<TradePosition> orders = tradeOrderMapper.selectList(wrapper);

        if (orders.isEmpty()) {
            return new ReviewMetrics(BigDecimal.ZERO, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, new HashMap<>());
        }

        BigDecimal totalPnL = BigDecimal.ZERO;
        int winCount = 0;
        int lossCount = 0;
        BigDecimal totalWinAmount = BigDecimal.ZERO;
        BigDecimal totalLossAmount = BigDecimal.ZERO;
        int stopLossCount = 0;
        Map<String, BigDecimal> symbolPnL = new HashMap<>();

        for (TradePosition order : orders) {
            BigDecimal income = order.getIncome();
            if (income == null) continue;

            totalPnL = totalPnL.add(income);

            String symbol = order.getSymbol();
            symbolPnL.merge(symbol != null ? symbol : "unknown", income, BigDecimal::add);

            if (income.compareTo(BigDecimal.ZERO) > 0) {
                winCount++;
                totalWinAmount = totalWinAmount.add(income);
            } else {
                lossCount++;
                totalLossAmount = totalLossAmount.add(income.abs());
            }

            if (order.getTradeOrderStatus() == TradePosition.TradeOrderStatus.LOSS) {
                stopLossCount++;
            }
        }

        int totalClosed = winCount + lossCount;
        double winRate = totalClosed > 0 ? (double) winCount / totalClosed * 100.0 : 0.0;
        double profitLossRatio = totalLossAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalWinAmount.divide(totalLossAmount, 4, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        double stopLossRate = totalClosed > 0 ? (double) stopLossCount / totalClosed * 100.0 : 0.0;

        long days = 1;
        if (timeRangeStart != null && timeRangeEnd != null) {
            LocalDateTime start = timeRangeStart.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime end = timeRangeEnd.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            days = Duration.between(start, end).toDays();
            if (days < 1) days = 1;
        }
        double avgDailyTrades = (double) totalClosed / days;

        double concentrationRatio = 0.0;
        if (totalPnL.compareTo(BigDecimal.ZERO) != 0 && !symbolPnL.isEmpty()) {
            BigDecimal maxSymbolPnL = symbolPnL.values().stream()
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            concentrationRatio = maxSymbolPnL.divide(totalPnL.abs(), 4, RoundingMode.HALF_UP).doubleValue();
        }

        double maxDrawdown = calculateMaxDrawdown(orders);

        return new ReviewMetrics(totalPnL, winRate, profitLossRatio, maxDrawdown,
                stopLossRate, avgDailyTrades, concentrationRatio, symbolPnL);
    }

    private double calculateMaxDrawdown(List<TradePosition> orders) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal cumulativePnL = BigDecimal.ZERO;

        for (TradePosition order : orders) {
            BigDecimal income = order.getIncome();
            if (income == null) continue;
            cumulativePnL = cumulativePnL.add(income);
            if (cumulativePnL.compareTo(peak) > 0) {
                peak = cumulativePnL;
            }
            BigDecimal drawdown = peak.subtract(cumulativePnL);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        if (peak.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        return maxDrawdown.divide(peak, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
    }
}
