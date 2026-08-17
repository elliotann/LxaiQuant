package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.backtest.entity.dto.AccountEquityPoint;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import com.chain.ai.trade.engine.controller.dto.TradingSummaryDTO;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.order.service.ITradeOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
@Slf4j
public class TradingSummaryController {

    private final ITradingAccountService tradingAccountService;
    private final ITradeOrderService tradeOrderService;
    private final BacktestEquityCurveService backtestEquityCurveService;

    @GetMapping("/summary")
    public ApiResponse<TradingSummaryDTO> getSummary(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String memberId) {
        try {
            BigDecimal totalAssets = BigDecimal.ZERO;
            BigDecimal availableBalance = BigDecimal.ZERO;

            if (accountId != null && !accountId.isBlank()) {
                TradingAccount account = tradingAccountService.getByAccountId(accountId);
                totalAssets = calculateTotalAssets(account);
                availableBalance = getUsdtBalance(account);
            } else {
                List<TradingAccount> accounts = tradingAccountService.getAllAccounts();
                for (TradingAccount account : accounts) {
                    if (memberId != null && !memberId.isBlank()
                            && !memberId.equals(account.getMemberId())) {
                        continue;
                    }
                    totalAssets = totalAssets.add(calculateTotalAssets(account));
                    availableBalance = availableBalance.add(getUsdtBalance(account));
                }
            }

            Date now = new Date();
            Date startOfDay = new Date(now.toInstant().atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

            BigDecimal dailyPnL = BigDecimal.ZERO;
            BigDecimal totalPnL = BigDecimal.ZERO;

            if (robotId != null && !robotId.isBlank()) {
                dailyPnL = tradeOrderService.getNetProfitByRobotId(robotId, startOfDay, now);
                totalPnL = tradeOrderService.getCumulativeNetProfitByRobotId(robotId);
            } else if (accountId != null && !accountId.isBlank()) {
                dailyPnL = tradeOrderService.getNetProfitByAccountId(accountId, startOfDay, now);
                totalPnL = tradeOrderService.getCumulativeNetProfitByAccountId(accountId);
            } else {
                List<TradingAccount> accounts = tradingAccountService.getAllAccounts();
                for (TradingAccount account : accounts) {
                    if (memberId != null && !memberId.isBlank()
                            && !memberId.equals(account.getMemberId())) {
                        continue;
                    }
                    String aid = account.getId();
                    if (aid != null && !aid.isBlank()) {
                        dailyPnL = dailyPnL.add(tradeOrderService.getNetProfitByAccountId(aid, startOfDay, now));
                        totalPnL = totalPnL.add(tradeOrderService.getCumulativeNetProfitByAccountId(aid));
                    }
                }
            }

            BigDecimal dailyPercent = BigDecimal.ZERO;
            BigDecimal totalPercent = BigDecimal.ZERO;
            if (totalAssets != null && totalAssets.compareTo(BigDecimal.ZERO) > 0) {
                dailyPercent = dailyPnL.multiply(new BigDecimal("100")).divide(totalAssets, 4, java.math.RoundingMode.HALF_UP);
                totalPercent = totalPnL.multiply(new BigDecimal("100")).divide(totalAssets, 4, java.math.RoundingMode.HALF_UP);
            }

            TradingSummaryDTO dto = TradingSummaryDTO.builder()
                    .totalAssets(totalAssets)
                    .availableBalance(availableBalance)
                    .dailyPnL(dailyPnL)
                    .totalPnL(totalPnL)
                    .dailyPnLPercent(dailyPercent)
                    .totalPnLPercent(totalPercent)
                    .build();
            return ApiResponse.success("OK", dto);
        } catch (Exception e) {
            log.error("获取交易汇总失败", e);
            return ApiResponse.error("获取交易汇总失败: " + e.getMessage());
        }
    }

    @GetMapping("/equity-curve")
    public ApiResponse<List<AccountEquityPoint>> getEquityCurve(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDate end = (endDate != null) ? LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE) : LocalDate.now();
            LocalDate start = (startDate != null) ? LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE) : end.minusMonths(3);
            List<AccountEquityPoint> curve = backtestEquityCurveService.getAccountEquityCurve(start, end);
            return ApiResponse.success("OK", curve);
        } catch (Exception e) {
            log.error("获取账户权益曲线失败", e);
            return ApiResponse.error("获取账户权益曲线失败: " + e.getMessage());
        }
    }

    private BigDecimal calculateTotalAssets(TradingAccount account) {
        if (account == null || account.getBalances() == null) {
            return BigDecimal.ZERO;
        }
        Map<String, BigDecimal> balances = parseBalances(account.getBalances());
        BigDecimal usdt = balances.getOrDefault("USDT", BigDecimal.ZERO);
        BigDecimal usd = balances.getOrDefault("USD", BigDecimal.ZERO);
        BigDecimal usdc = balances.getOrDefault("USDC", BigDecimal.ZERO);
        BigDecimal busd = balances.getOrDefault("BUSD", BigDecimal.ZERO);
        BigDecimal tusd = balances.getOrDefault("TUSD", BigDecimal.ZERO);
        BigDecimal dai = balances.getOrDefault("DAI", BigDecimal.ZERO);
        return usdt.add(usd).add(usdc).add(busd).add(tusd).add(dai);
    }

    private BigDecimal getUsdtBalance(TradingAccount account) {
        if (account == null || account.getBalances() == null) {
            return BigDecimal.ZERO;
        }
        Map<String, BigDecimal> balances = parseBalances(account.getBalances());
        return balances.getOrDefault("USDT", BigDecimal.ZERO);
    }

    private Map<String, BigDecimal> parseBalances(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }
}
