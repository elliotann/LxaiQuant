package com.chain.ai.trade.engine.agent;

import com.chain.ai.trade.agent.tools.McpTool;
import com.chain.ai.trade.engine.controller.dto.MarketAnalysisDTO;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.entity.ReviewMetrics;
import com.chain.ai.trade.engine.service.MarketAnalysisService;
import com.chain.ai.trade.engine.service.ReviewMetricsService;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.query.TechnicalSignalQuery;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.order.entity.dto.OrderQueryDTO;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.chain.ai.trade.member.service.ITradingAccountService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuantMarketTools implements McpTool {

    private final MarketAnalysisService marketAnalysisService;
    private final ICandlestickService candlestickService;
    private final ITradingAccountService tradingAccountService;
    private final ITradeOrderService tradeOrderService;
    private final ReviewMetricsService reviewMetricsService;
    private final ITradeSignalService tradeSignalService;
    private final ITechnicalSignalService technicalSignalService;
    private final ITradingBotService tradingBotService;
    private final IStrategyService strategyService;

    @Tool("获取指定交易对的完整市场分析数据，包括趋势、RSI、支撑阻力位、均线等")
    public String analyzeMarket(
            @P("交易对符号，如 BTC/USDT") String symbol,
            @P("K线周期，如 3m/15m/1h/4h/1d") String interval) {
        try {
            MarketAnalysisDTO analysis = marketAnalysisService.analyze(symbol, interval, 200);
            if (analysis == null) {
                return "数据不足，无法分析 " + symbol;
            }
            return formatAnalysis(analysis);
        } catch (Exception e) {
            log.error("分析市场失败: {}", e.getMessage());
            return "分析失败: " + e.getMessage();
        }
    }

    @Tool("获取指定交易对的当前行情数据，包括最新价、涨跌幅、成交量")
    public String getTicker(
            @P("交易对符号，如 BTC/USDT") String symbol) {
        try {
            KlineParam p = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(CandlestickIntervalEnum.OKXMIN15)
                    .endTime(System.currentTimeMillis())
                    .size(2)
                    .build();
            List<Candlestick> rows = candlestickService.listByLeId(p);
            if (rows == null || rows.isEmpty()) {
                return "暂无 " + symbol + " 的行情数据";
            }
            Candlestick latest = rows.get(rows.size() - 1);
            BigDecimal price = latest.getClosePrice() != null ? latest.getClosePrice() : BigDecimal.ZERO;
            BigDecimal volume = latest.getVolume() != null ? latest.getVolume() : BigDecimal.ZERO;

            StringBuilder sb = new StringBuilder();
            sb.append("交易对: ").append(symbol).append("\n");
            sb.append("最新价: ").append(price.setScale(8, RoundingMode.HALF_UP)).append("\n");
            sb.append("24h成交量: ").append(volume.setScale(2, RoundingMode.HALF_UP)).append("\n");

            if (rows.size() >= 2) {
                Candlestick prev = rows.get(rows.size() - 2);
                if (prev.getClosePrice() != null && prev.getClosePrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = price.subtract(prev.getClosePrice())
                            .divide(prev.getClosePrice(), 6, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
                    sb.append("24h涨跌幅: ").append(change).append("%\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取行情失败: {}", e.getMessage());
            return "获取行情失败: " + e.getMessage();
        }
    }

    @Tool("获取指定交易对的K线数据，用于技术分析")
    public String getKlines(
            @P("交易对符号，如 BTC/USDT") String symbol,
            @P("K线周期，如 3m/15m/1h/4h") String interval,
            @P("获取K线数量，默认60") Integer limit) {
        try {
            int take = limit != null && limit > 0 ? Math.min(200, limit) : 60;
            MarketAnalysisDTO analysis = marketAnalysisService.analyze(symbol, interval, take);
            if (analysis == null) {
                List<Candlestick> rows = candlestickService.listByLeId(
                        KlineParam.builder().symbol(symbol).endTime(System.currentTimeMillis())
                                .klineInterval(CandlestickIntervalEnum.OKXMIN15)
                                .size(take).build());
                if (rows == null || rows.isEmpty()) {
                    return "暂无K线数据";
                }
                return formatRawKlines(rows);
            }
            return formatAnalysis(analysis);
        } catch (Exception e) {
            log.error("获取K线失败: {}", e.getMessage());
            return "获取K线失败: " + e.getMessage();
        }
    }

    @Tool("获取指定交易对在指定时间范围内的K线数据，用于市场对比分析")
    public String getCandlesticks(
            @P("交易对符号，如 BTC/USDT") String symbol,
            @P("K线周期，如 1m/3m/5m/15m/30m/1h/4h/1d") String interval,
            @P("开始日期，格式 yyyy-MM-dd，如 2026-05-01") String startDate,
            @P("结束日期，格式 yyyy-MM-dd，如 2026-05-14") String endDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            long startMs = sdf.parse(startDate).getTime();
            long endMs = Math.min(sdf.parse(endDate).getTime() + 24 * 60 * 60 * 1000L - 1, System.currentTimeMillis());

            CandlestickIntervalEnum intervalEnum = parseInterval(interval);
            long intervalMs = getIntervalDuration(intervalEnum);
            int size = (int) ((endMs - startMs) / intervalMs) + 30;

            size = Math.max(60, Math.min(1000, size));

            List<Candlestick> rows = candlestickService.listByLeId(
                    KlineParam.builder()
                            .symbol(symbol)
                            .klineInterval(intervalEnum)
                            .endTime(endMs)
                            .size(size)
                            .build());

            if (rows == null || rows.isEmpty()) {
                return "暂无 " + symbol + " 在 " + startDate + " 至 " + endDate + " 范围内的K线数据";
            }

            List<Candlestick> filtered = rows.stream()
                    .filter(c -> {
                        long t = candleTimeMs(c);
                        return t >= startMs && t <= endMs;
                    })
                    .sorted(Comparator.comparingLong(this::candleTimeMs))
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                return "暂无 " + symbol + " 在 " + startDate + " 至 " + endDate + " 范围内的K线数据";
            }

            Candlestick first = filtered.get(0);
            Candlestick last = filtered.get(filtered.size() - 1);
            BigDecimal openPrice = nvl(first.getOpenPrice());
            BigDecimal closePrice = nvl(last.getClosePrice());
            BigDecimal highPrice = filtered.stream().map(c -> nvl(c.getHighPrice())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal lowPrice = filtered.stream().map(c -> nvl(c.getLowPrice())).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal totalVolume = filtered.stream().map(c -> nvl(c.getVolume())).reduce(BigDecimal.ZERO, BigDecimal::add);

            StringBuilder sb = new StringBuilder();
            sb.append("交易对: ").append(symbol).append(" (").append(interval).append(")\n");
            sb.append("时间范围: ").append(startDate).append(" 至 ").append(endDate).append("\n");
            sb.append("K线数量: ").append(filtered.size()).append("\n");
            sb.append("开盘价: ").append(openPrice.setScale(8, RoundingMode.HALF_UP)).append("\n");
            sb.append("收盘价: ").append(closePrice.setScale(8, RoundingMode.HALF_UP)).append("\n");
            sb.append("最高价: ").append(highPrice.setScale(8, RoundingMode.HALF_UP)).append("\n");
            sb.append("最低价: ").append(lowPrice.setScale(8, RoundingMode.HALF_UP)).append("\n");
            sb.append("总成交量: ").append(totalVolume.setScale(2, RoundingMode.HALF_UP)).append("\n");

            if (openPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal changePct = closePrice.subtract(openPrice)
                        .divide(openPrice, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                sb.append("区间涨跌幅: ").append(changePct).append("%\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("获取K线数据失败: {}", e.getMessage());
            return "获取K线数据失败: " + e.getMessage();
        }
    }

    @Tool("获取交易账户的持仓信息，用于分析已有仓位")
    public String getPositions(
            @P("账户ID") String accountId,
            @P("交易对符号，如 BTC/USDT，可选") String symbol) {
        try {
            List<OrderVO> positions = tradeOrderService.getPositionOrders(accountId, symbol);
            if (positions == null || positions.isEmpty()) {
                return "当前无持仓";
            }
            StringBuilder sb = new StringBuilder("当前持仓:\n");
            for (OrderVO pos : positions) {
                sb.append("- ").append(pos.getSymbol())
                        .append(" | 方向: ").append(pos.getOrderSide())
                        .append(" | 数量: ").append(pos.getAmount())
                        .append(" | 入场价: ").append(pos.getOpenPrice())
                        .append(" | 收益: ").append(pos.getIncome())
                        .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取持仓失败: {}", e.getMessage());
            return "获取持仓失败: " + e.getMessage();
        }
    }

    @Tool("获取指定机器人在指定时间范围内的交易复盘数据，包括总盈亏、胜率、最大回撤等")
    public String getReviewMetrics(
            @P("机器人ID") String robotId,
            @P("开始日期，格式 yyyy-MM-dd，如 2026-05-01") String startDate,
            @P("结束日期，格式 yyyy-MM-dd，如 2026-05-13") String endDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date start = sdf.parse(startDate);
            Date end = sdf.parse(endDate);

            ReviewMetrics metrics = reviewMetricsService.calculate(robotId, start, end);
            if (metrics == null) {
                return "暂无 " + robotId + " 在 " + startDate + " 至 " + endDate + " 范围内的交易数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("机器人ID: ").append(robotId).append("\n");
            sb.append("分析周期: ").append(startDate).append(" 至 ").append(endDate).append("\n");
            sb.append("总盈亏: ").append(metrics.getTotalPnL().setScale(2, RoundingMode.HALF_UP)).append("\n");
            sb.append("胜率: ").append(String.format("%.1f%%", metrics.getWinRate())).append("\n");
            sb.append("盈亏比: ").append(String.format("%.2f", metrics.getProfitLossRatio())).append("\n");
            sb.append("最大回撤: ").append(String.format("%.1f%%", metrics.getMaxDrawdown())).append("\n");
            sb.append("止损率: ").append(String.format("%.1f%%", metrics.getStopLossRate())).append("\n");
            sb.append("日均交易次数: ").append(String.format("%.1f", metrics.getAvgDailyTrades())).append("\n");
            sb.append("集中度比率: ").append(String.format("%.1f%%", metrics.getConcentrationRatio())).append("\n");

            Map<String, BigDecimal> strategyPnL = metrics.getStrategyPnL();
            if (strategyPnL != null && !strategyPnL.isEmpty()) {
                sb.append("各交易对盈亏明细:\n");
                for (Map.Entry<String, BigDecimal> entry : strategyPnL.entrySet()) {
                    sb.append("- ").append(entry.getKey()).append(": ")
                            .append(entry.getValue().setScale(2, RoundingMode.HALF_UP)).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取复盘数据失败: {}", e.getMessage());
            return "获取复盘数据失败: " + e.getMessage();
        }
    }

    @Tool("获取指定机器人在指定时间范围内的历史订单列表，包含每笔订单的入场/止损/止盈分析和盈亏诊断")
    public String getOrderHistoryWithAnalysis(
            @P("机器人ID") String robotId,
            @P("开始日期，格式 yyyy-MM-dd，如 2026-05-01") String startDate,
            @P("结束日期，格式 yyyy-MM-dd，如 2026-05-14") String endDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date start = sdf.parse(startDate);
            Date end = sdf.parse(endDate);
            end = new Date(end.getTime() + 24 * 60 * 60 * 1000L - 1);

            OrderQueryDTO query = OrderQueryDTO.builder()
                    .robotId(robotId)
                    .startTime(start)
                    .endTime(end)
                    .pageNum(1)
                    .pageSize(200)
                    .sortField("orderTime")
                    .sortOrder("desc")
                    .build();

            var pageResult = tradeOrderService.queryOrders(query);
            List<OrderVO> orders = pageResult != null ? pageResult.getRecords() : null;

            if (orders == null || orders.isEmpty()) {
                return "机器人 " + robotId + " 在 " + startDate + " 至 " + endDate + " 范围内无历史订单";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("机器人ID: ").append(robotId).append("\n");
            sb.append("时间范围: ").append(startDate).append(" 至 ").append(endDate).append("\n");
            sb.append("订单总数: ").append(orders.size()).append("\n\n");

            int displayLimit = Math.min(orders.size(), 50);
            for (int i = 0; i < displayLimit; i++) {
                OrderVO o = orders.get(i);
                sb.append("--- 订单 ").append(i + 1).append(" ---\n");
                sb.append("订单号: ").append(nvlStr(o.getOrderSn())).append("\n");
                sb.append("交易对: ").append(nvlStr(o.getSymbol())).append("\n");
                sb.append("方向: ").append(nvlStr(o.getOrderSide())).append("\n");
                sb.append("状态: ").append(nvlStr(o.getStatus())).append("\n");

                if (o.getOpenPrice() != null) {
                    sb.append("开仓价: ").append(o.getOpenPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                }
                if (o.getAmount() != null) {
                    sb.append("数量: ").append(o.getAmount().setScale(4, RoundingMode.HALF_UP)).append("\n");
                }
                if (o.getBuyAvgPrice() != null) {
                    sb.append("买入均价: ").append(o.getBuyAvgPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                }
                if (o.getIncome() != null) {
                    sb.append("盈亏: ").append(o.getIncome().setScale(2, RoundingMode.HALF_UP));
                    if (o.getProfitPercent() != null) {
                        sb.append(" (").append(String.format("%.2f", o.getProfitPercent())).append("%)");
                    }
                    sb.append("\n");
                }
                if (o.getCloseReason() != null) {
                    sb.append("平仓原因: ").append(o.getCloseReason()).append("\n");
                }
                if (o.getSellPrice() != null) {
                    sb.append("平仓价: ").append(o.getSellPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                }
                if (o.getSellTime() != null) {
                    sb.append("平仓时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(o.getSellTime())).append("\n");
                }
                if (o.getBuyTime() != null) {
                    sb.append("开仓时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(o.getBuyTime())).append("\n");
                }

                TradeSignal ts = null;
                if (o.getOrderSn() != null) {
                    try {
                        ts = tradeSignalService.queryTradeSignalByOrderSn(o.getOrderSn());
                    } catch (Exception ignored) {}
                }

                if (ts != null) {
                    if (ts.getStopLossPrice() != null) {
                        sb.append("预设止损价: ").append(ts.getStopLossPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                    }
                    if (ts.getTakeProfitPrice() != null) {
                        sb.append("预设止盈价: ").append(ts.getTakeProfitPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                    }
                    if (ts.getExpectedPrice() != null && o.getOpenPrice() != null) {
                        BigDecimal slippage = o.getOpenPrice().subtract(ts.getExpectedPrice())
                                .divide(ts.getExpectedPrice(), 6, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
                        sb.append("入场滑点: ").append(slippage).append("%\n");
                    }
                    if (ts.getOrderAction() != null) {
                        sb.append("信号动作: ").append(ts.getOrderAction()).append("\n");
                    }
                    if (ts.getTechnicalSignalBrief() != null) {
                        sb.append("信号摘要: ").append(ts.getTechnicalSignalBrief()).append("\n");
                    }
                    if (ts.getLeverage() != null) {
                        sb.append("杠杆: ").append(ts.getLeverage()).append("x\n");
                    }
                    if (ts.getPnlAmount() != null) {
                        sb.append("信号盈亏: ").append(ts.getPnlAmount().setScale(2, RoundingMode.HALF_UP)).append("\n");
                    }
                }

                if (o.getLossPrice() != null) {
                    sb.append("止损触发价: ").append(o.getLossPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                }
                if (o.getGainPrice() != null) {
                    sb.append("止盈目标价: ").append(o.getGainPrice().setScale(8, RoundingMode.HALF_UP)).append("\n");
                }

                sb.append("\n");
            }

            if (orders.size() > displayLimit) {
                sb.append("... 仅显示前 ").append(displayLimit).append(" 笔订单，共 ")
                        .append(orders.size()).append(" 笔\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("获取订单历史失败: {}", e.getMessage());
            return "获取订单历史失败: " + e.getMessage();
        }
    }

    @Tool("获取指定交易对在指定时间范围内的技术信号统计，按信号来源、指标类型和信号强度分组")
    public String getTechnicalSignalStats(
            @P("交易对符号，如 ETH-USDT-SWAP") String symbol,
            @P("开始日期，格式 yyyy-MM-dd，如 2026-05-01") String startDate,
            @P("结束日期，格式 yyyy-MM-dd，如 2026-05-14") String endDate) {
        try {
            LocalDate startLocal = LocalDate.parse(startDate);
            LocalDate endLocal = LocalDate.parse(endDate);
            LocalDateTime startTime = startLocal.atStartOfDay();
            LocalDateTime endTime = endLocal.plusDays(1).atStartOfDay();

            List<TechnicalSignal> signals = technicalSignalService.getSignalsByTimeRange(symbol, startTime, endTime);
            if (signals == null || signals.isEmpty()) {
                return symbol + " 在 " + startDate + " 至 " + endDate + " 范围内无技术信号";
            }

            Map<String, Long> sourceCount = new LinkedHashMap<>();
            Map<String, Long> indicatorCount = new LinkedHashMap<>();
            Map<String, Long> directionCount = new LinkedHashMap<>();
            Map<String, Long> strengthBuckets = new LinkedHashMap<>();
            strengthBuckets.put("strong(>0.7)", 0L);
            strengthBuckets.put("medium(0.4-0.7)", 0L);
            strengthBuckets.put("weak(<0.4)", 0L);

            for (TechnicalSignal s : signals) {
                String src = s.getSignalSource() != null ? s.getSignalSource() : "UNKNOWN";
                sourceCount.merge(src, 1L, Long::sum);

                String ind = s.getIndicator() != null ? s.getIndicator() : "UNKNOWN";
                indicatorCount.merge(ind, 1L, Long::sum);

                String dir = s.getTechnicalDirection() != null ? s.getTechnicalDirection() : "UNKNOWN";
                directionCount.merge(dir, 1L, Long::sum);

                if (s.getSignalStrength() != null) {
                    double v = s.getSignalStrength().doubleValue();
                    if (v >= 0.7) {
                        strengthBuckets.merge("strong(>0.7)", 1L, Long::sum);
                    } else if (v >= 0.4) {
                        strengthBuckets.merge("medium(0.4-0.7)", 1L, Long::sum);
                    } else {
                        strengthBuckets.merge("weak(<0.4)", 1L, Long::sum);
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("交易对: ").append(symbol).append("\n");
            sb.append("时间范围: ").append(startDate).append(" 至 ").append(endDate).append("\n");
            sb.append("信号总数: ").append(signals.size()).append("\n\n");

            sb.append("【信号来源分布】\n");
            for (var e : sourceCount.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\n");

            sb.append("【指标类型分布】\n");
            for (var e : indicatorCount.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\n");

            sb.append("【信号方向分布】\n");
            for (var e : directionCount.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\n");

            sb.append("【信号强度分布】\n");
            for (var e : strengthBuckets.entrySet()) {
                if (e.getValue() > 0) {
                    sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("获取技术信号统计失败: {}", e.getMessage());
            return "获取技术信号统计失败: " + e.getMessage();
        }
    }

    @Tool("获取机器人绑定的策略信息，包括策略配置、历史回测绩效（夏普比率、最大回撤、胜率等）")
    public String getStrategyInfo(
            @P("机器人ID") String botId) {
        try {
            TradingBot bot = tradingBotService.getByBotId(botId);
            if (bot == null) {
                return "未找到机器人 " + botId;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("机器人名称: ").append(nvlStr(bot.getBotName())).append("\n");
            sb.append("交易对: ").append(nvlStr(bot.getTradingPair())).append("\n");
            sb.append("状态: ").append(nvlStr(bot.getStatus())).append("\n");
            sb.append("分配资金: ").append(bot.getAllocatedCapital() != null ? bot.getAllocatedCapital().setScale(2, RoundingMode.HALF_UP) : "N/A").append("\n");
            sb.append("当前资金: ").append(bot.getCurrentCapital() != null ? bot.getCurrentCapital().setScale(2, RoundingMode.HALF_UP) : "N/A").append("\n");

            if (bot.getConfiguration() != null) {
                sb.append("配置信息: ").append(bot.getConfiguration()).append("\n");
            }
            if (bot.getStatistics() != null) {
                sb.append("统计信息: ").append(bot.getStatistics()).append("\n");
            }

            String strategyId = bot.getStrategyId();
            if (strategyId != null && !strategyId.isBlank()) {
                Strategy strategy = strategyService.getByStrategyId(strategyId);
                if (strategy != null) {
                    sb.append("\n【策略信息】\n");
                    sb.append("策略名称: ").append(nvlStr(strategy.getName())).append("\n");
                    sb.append("策略类型: ").append(nvlStr(strategy.getStrategyType())).append("\n");
                    sb.append("市场类型: ").append(nvlStr(strategy.getMarketType())).append("\n");
                    sb.append("时间框架: ").append(nvlStr(strategy.getTimeFrame())).append("\n");
                    sb.append("运行频率: ").append(nvlStr(strategy.getFrequency())).append("\n");
                    sb.append("支持做多: ").append(strategy.getSupportsLong() != null && strategy.getSupportsLong()).append("\n");
                    sb.append("支持做空: ").append(strategy.getSupportsShort() != null && strategy.getSupportsShort()).append("\n");

                    if (strategy.getAvgSharpeRatio() != null) {
                        sb.append("回测平均夏普比率: ").append(strategy.getAvgSharpeRatio().setScale(4, RoundingMode.HALF_UP)).append("\n");
                    }
                    if (strategy.getAvgAnnualReturn() != null) {
                        sb.append("回测平均年化收益: ").append(strategy.getAvgAnnualReturn().setScale(2, RoundingMode.HALF_UP)).append("%\n");
                    }
                    if (strategy.getAvgMaxDrawdown() != null) {
                        sb.append("回测平均最大回撤: ").append(strategy.getAvgMaxDrawdown().setScale(2, RoundingMode.HALF_UP)).append("%\n");
                    }
                    if (strategy.getSuccessRate() != null) {
                        sb.append("回测胜率: ").append(strategy.getSuccessRate().setScale(2, RoundingMode.HALF_UP)).append("%\n");
                    }
                    if (strategy.getBacktestCount() != null) {
                        sb.append("回测次数: ").append(strategy.getBacktestCount()).append("\n");
                    }
                } else {
                    sb.append("\n策略 ").append(strategyId).append(" 详细信息不可用\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("获取策略信息失败: {}", e.getMessage());
            return "获取策略信息失败: " + e.getMessage();
        }
    }

    @Override
    public String getContextData(String skillName, String userInput) {
        if (!"live-advice".equals(skillName) || userInput == null || userInput.isBlank()) {
            return "";
        }
        String symbol = userInput.split("\\s+")[0].trim();
        if (symbol.isEmpty()) {
            return "";
        }
        StringBuilder ctx = new StringBuilder("【实时行情数据】\n");

        String ticker = getTicker(symbol);
        if (!ticker.startsWith("暂无") && !ticker.startsWith("获取行情失败")) {
            ctx.append(ticker).append("\n");
        }

        String analysis15m = analyzeMarket(symbol, "15m");
        if (!analysis15m.startsWith("数据不足") && !analysis15m.startsWith("分析失败")) {
            ctx.append(analysis15m).append("\n");
        }

        String analysis1h = analyzeMarket(symbol, "1h");
        if (!analysis1h.startsWith("数据不足") && !analysis1h.startsWith("分析失败")) {
            ctx.append(analysis1h).append("\n");
        }

        String klines = getKlines(symbol, "15m", 30);
        if (!klines.startsWith("暂无") && !klines.startsWith("获取K线失败")) {
            ctx.append(klines).append("\n");
        }

        return ctx.toString();
    }

    private String formatAnalysis(MarketAnalysisDTO a) {
        StringBuilder sb = new StringBuilder();
        sb.append("市场分析: ").append(a.getSymbol()).append(" (").append(a.getInterval()).append(")\n");
        sb.append("当前价格: ").append(a.getPrice()).append("\n");
        if (a.getChangePercent() != null) {
            sb.append("涨跌幅: ").append(a.getChangePercent()).append("%\n");
        }
        sb.append("趋势: ").append(a.getTrendLabel())
                .append(" (强度: ").append(a.getTrendStrength() != null ? a.getTrendStrength() : "N/A").append("/100)\n");
        sb.append("RSI(14): ").append(a.getRsi14()).append("\n");
        sb.append("EMA9: ").append(a.getEma9()).append("\n");
        sb.append("EMA21: ").append(a.getEma21()).append("\n");
        if (a.getAtr14Percent() != null) {
            sb.append("ATR(14): ").append(a.getAtr14Percent()).append("%\n");
        }
        sb.append("市场情绪: ").append(a.getSentimentLabel())
                .append(" (评分: ").append(a.getSentimentScore()).append(")\n");
        if (a.getSupports() != null && !a.getSupports().isEmpty()) {
            sb.append("支撑位: ").append(a.getSupports().stream()
                    .map(d -> d.setScale(2, RoundingMode.HALF_UP).toString())
                    .collect(Collectors.joining(", "))).append("\n");
        }
        if (a.getResistances() != null && !a.getResistances().isEmpty()) {
            sb.append("阻力位: ").append(a.getResistances().stream()
                    .map(d -> d.setScale(2, RoundingMode.HALF_UP).toString())
                    .collect(Collectors.joining(", "))).append("\n");
        }
        if (a.getTags() != null && !a.getTags().isEmpty()) {
            sb.append("标签: ").append(String.join(", ", a.getTags())).append("\n");
        }
        return sb.toString();
    }

    private String formatRawKlines(List<Candlestick> rows) {
        StringBuilder sb = new StringBuilder("K线数据:\n");
        Candlestick latest = rows.get(rows.size() - 1);
        sb.append("最新: 开=").append(latest.getOpenPrice())
                .append(" 高=").append(latest.getHighPrice())
                .append(" 低=").append(latest.getLowPrice())
                .append(" 收=").append(latest.getClosePrice())
                .append(" 量=").append(latest.getVolume()).append("\n");
        return sb.toString();
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null || interval.isBlank()) return CandlestickIntervalEnum.OKXMIN3;
        String s = interval.trim();
        try {
            return CandlestickIntervalEnum.valueOf(s);
        } catch (Exception ignored) {
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> CandlestickIntervalEnum.OKXMIN3;
        };
    }

    private long getIntervalDuration(CandlestickIntervalEnum interval) {
        return switch (interval) {
            case OKXMIN1 -> 60_000L;
            case OKXMIN3 -> 180_000L;
            case OKXMIN5 -> 300_000L;
            case OKXMIN15 -> 900_000L;
            case OKXMIN30 -> 1_800_000L;
            case OKXMIN60 -> 3_600_000L;
            case OKX4HOUR -> 14_400_000L;
            case OKX1D -> 86_400_000L;
            default -> 900_000L;
        };
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String nvlStr(Object v) {
        return v != null ? v.toString() : "N/A";
    }

    private long candleTimeMs(Candlestick c) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            return sdf.parse(c.getTimeStr()).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
