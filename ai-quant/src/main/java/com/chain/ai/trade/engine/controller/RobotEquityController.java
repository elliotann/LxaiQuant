package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import com.chain.ai.trade.engine.controller.dto.MultiRobotEquityCompareDTO;
import com.chain.ai.trade.engine.controller.dto.RobotEquityLatestDTO;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/robot/equity")
@RequiredArgsConstructor
@Slf4j
public class RobotEquityController {

    private final BacktestEquityCurveService equityCurveService;
    private final ITradingBotService tradingBotService;

    @GetMapping("/compare")
    public ApiResponse<List<MultiRobotEquityCompareDTO>> compare(
            @RequestParam List<String> robotIds,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "absolute") String alignType) {

        LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate end = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);

        List<BacktestEquityCurve> records = equityCurveService.getEquitiesByRobotIds(robotIds, start, end);
        if (CollectionUtils.isEmpty(records)) {
            return ApiResponse.success(Collections.emptyList());
        }

        Map<String, List<BacktestEquityCurve>> grouped = records.stream()
                .collect(Collectors.groupingBy(BacktestEquityCurve::getRobotId));

        List<MultiRobotEquityCompareDTO> result = new ArrayList<>();

        for (String robotId : robotIds) {
            List<BacktestEquityCurve> robotRecords = grouped.get(robotId);
            if (CollectionUtils.isEmpty(robotRecords)) {
                continue;
            }

            List<String> dates = new ArrayList<>();
            List<BigDecimal> equities = new ArrayList<>();
            Map<String, BigDecimal> dateMap = robotRecords.stream()
                    .collect(Collectors.toMap(
                            r -> r.getTime().toLocalDate().toString(),
                            BacktestEquityCurve::getEquity,
                            (existing, replacement) -> existing));

            BigDecimal lastValue = null;
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                String dateStr = cursor.toString();
                BigDecimal value = dateMap.get(dateStr);
                if (value != null) {
                    lastValue = value;
                }
                if (lastValue != null) {
                    dates.add(dateStr);
                    equities.add(lastValue);
                }
                cursor = cursor.plusDays(1);
            }

            if (equities.isEmpty()) {
                continue;
            }

            String robotName = robotRecords.get(0).getRobotName();
            List<BigDecimal> navs = null;
            if ("normalized".equals(alignType)) {
                BigDecimal base = equities.get(0);
                navs = equities.stream()
                        .map(e -> e.divide(base, 6, RoundingMode.HALF_UP))
                        .collect(Collectors.toList());
            }

            result.add(MultiRobotEquityCompareDTO.builder()
                    .robotId(robotId)
                    .robotName(robotName)
                    .dates(dates)
                    .equities("absolute".equals(alignType) ? equities : null)
                    .navs(navs)
                    .build());
        }

        return ApiResponse.success(result);
    }

    @GetMapping("/latest")
    public ApiResponse<List<RobotEquityLatestDTO>> latest(
            @RequestParam(required = false) List<String> robotIds) {

        List<TradingBot> bots;
        if (!CollectionUtils.isEmpty(robotIds)) {
            bots = tradingBotService.lambdaQuery()
                    .in(TradingBot::getBotId, robotIds)
                    .list();
        } else {
            bots = tradingBotService.list();
        }

        if (CollectionUtils.isEmpty(bots)) {
            return ApiResponse.success(Collections.emptyList());
        }

        List<String> ids = bots.stream().map(TradingBot::getBotId).collect(Collectors.toList());
        List<BacktestEquityCurve> latestEquities = equityCurveService.getLatestByRobotIds(ids);
        Map<String, BacktestEquityCurve> latestMap = latestEquities.stream()
                .collect(Collectors.toMap(BacktestEquityCurve::getRobotId, r -> r, (existing, replacement) -> existing));

        Map<String, BacktestEquityCurve> yesterdayMap = new HashMap<>();
        for (String id : ids) {
            BacktestEquityCurve latest = latestMap.get(id);
            if (latest != null) {
                LocalDate yesterday = latest.getTime().toLocalDate().minusDays(1);
                List<BacktestEquityCurve> prevRecords = equityCurveService.getEquitiesByRobotIds(
                        Collections.singletonList(id), yesterday, yesterday);
                if (!CollectionUtils.isEmpty(prevRecords)) {
                    yesterdayMap.put(id, prevRecords.get(0));
                }
            }
        }

        List<RobotEquityLatestDTO> result = new ArrayList<>();
        for (TradingBot bot : bots) {
            String botId = bot.getBotId();
            BigDecimal currentCapital = bot.getCurrentCapital();
            BigDecimal allocatedCapital = bot.getAllocatedCapital();
            BigDecimal peakCapital = bot.getPeakCapital();

            if (currentCapital == null) {
                continue;
            }

            BigDecimal todayPnl = BigDecimal.ZERO;
            BacktestEquityCurve prevEquity = yesterdayMap.get(botId);
            if (prevEquity != null && prevEquity.getEquity() != null) {
                todayPnl = currentCapital.subtract(prevEquity.getEquity());
            }

            BigDecimal totalReturn = BigDecimal.ZERO;
            if (allocatedCapital != null && allocatedCapital.compareTo(BigDecimal.ZERO) > 0) {
                totalReturn = currentCapital.subtract(allocatedCapital)
                        .divide(allocatedCapital, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            BigDecimal drawdown = BigDecimal.ZERO;
            if (peakCapital != null && peakCapital.compareTo(BigDecimal.ZERO) > 0) {
                drawdown = peakCapital.subtract(currentCapital)
                        .divide(peakCapital, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            result.add(RobotEquityLatestDTO.builder()
                    .robotId(botId)
                    .robotName(bot.getBotName())
                    .currentCapital(currentCapital)
                    .allocatedCapital(allocatedCapital)
                    .peakCapital(peakCapital)
                    .todayPnl(todayPnl)
                    .totalReturn(totalReturn.setScale(2, RoundingMode.HALF_UP))
                    .drawdown(drawdown.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return ApiResponse.success(result);
    }
}
