package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.entity.ReviewMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class ReviewPromptBuilder {

    public String buildSystemPrompt() {
        return "你是一位专业的量化交易分析师。你的任务是根据交易数据和市场行情，对机器人的交易表现进行全面复盘分析。" +
                "请从以下几个方面进行分析：\n\n" +
                "1. **总体表现**：总盈亏、胜率、盈亏比等核心指标评价\n" +
                "2. **风险分析**：最大回撤、止损率、风险敞口等风险评估\n" +
                "3. **交易行为**：交易频率、持仓时间、集中度等交易习惯分析\n" +
                "4. **策略评价**：各交易对/策略的表现差异\n" +
                "5. **改进建议**：针对发现的问题给出具体的优化建议\n\n" +
                "请以专业、客观的角度进行分析，语言简洁有力，重点突出。";
    }

    public String buildUserPrompt(String symbol, String robotId, ReviewMetrics metrics,
                                  ZonedDateTime startTime, ZonedDateTime endTime) {
        StringBuilder sb = new StringBuilder();
        String nowText = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        sb.append("## 复盘分析请求\n\n");
        sb.append("**当前时间**：").append(nowText).append("\n");
        sb.append("**交易对**：").append(symbol != null ? symbol : "全部").append("\n");
        sb.append("**机器人ID**：").append(robotId).append("\n");
        sb.append("**分析周期**：")
                .append(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append(" 至 ")
                .append(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n\n");

        sb.append("## 交易数据\n\n");
        sb.append("- 总盈亏：").append(formatDecimal(metrics.getTotalPnL())).append("\n");
        sb.append("- 胜率：").append(formatPercent(metrics.getWinRate())).append("\n");
        sb.append("- 盈亏比：").append(String.format("%.2f", metrics.getProfitLossRatio())).append("\n");
        sb.append("- 最大回撤：").append(formatPercent(metrics.getMaxDrawdown())).append("\n");
        sb.append("- 止损率：").append(formatPercent(metrics.getStopLossRate())).append("\n");
        sb.append("- 日均交易次数：").append(String.format("%.1f", metrics.getAvgDailyTrades())).append("\n");
        sb.append("- 集中度比率：").append(formatPercent(metrics.getConcentrationRatio())).append("\n\n");

        Map<String, BigDecimal> strategyPnL = metrics.getStrategyPnL();
        if (strategyPnL != null && !strategyPnL.isEmpty()) {
            sb.append("### 各交易对盈亏明细\n\n");
            sb.append("| 交易对 | 盈亏 |\n");
            sb.append("| --- | --- |\n");
            for (Map.Entry<String, BigDecimal> entry : strategyPnL.entrySet()) {
                sb.append("| ").append(entry.getKey()).append(" | ")
                        .append(formatDecimal(entry.getValue())).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("请根据以上数据进行全面的复盘分析，给出专业的评价和改进建议。");

        return sb.toString();
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value);
    }
}
