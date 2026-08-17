---
name: "daily-recap"
description: "Generates daily quant recap from MySQL via db-query. Invoke when the user asks to run or schedule daily recap, compute PnL/win-rate/top trades, and deliver a Markdown report."
---

# Daily Quant Recap

This skill orchestrates a daily recap workflow using the local Python script to read MySQL data, compute KPIs, and format a Markdown report for delivery.

## Prerequisites
- Ensure Python dependencies are available: `pymysql`, `requests`.
- Configure database connection at: `/Users/huangxuean/IdeaProjects/lenzeto/.openclaw-config/db-config.json`.
- Use database config name: `Quant Readonly`.

## When to Invoke
- User asks to generate "yesterday" or a specific date range recap.
- User requests scheduling an automatic daily recap (e.g., 17:00 Asia/Shanghai).
- Need structured KPIs and per-symbol breakdown for the previous trading day.

## What It Does
- Determines the date window (default: yesterday, timezone Asia/Shanghai).
- Runs `scripts/generate_and_format_recap.py` to compute:
  - Core Metrics: PnL, Win Rate, Max Profit/Loss, Avg Hold Time.
  - Signal Stats: Counts by timeframe and strength distribution.
  - Signal Validity: Next 3×3m candles after signal.
  - Execution Analysis: Stop/Take approximate distances and trigger rates.
  - Signal-Order Matching: Signals without trades, Trades without signals.
  - 7-Day Trend: Daily stats for the last week.
  - Recent 4h Snapshot: Signal/trade summary for last 4 hours.
- Formats an output report using the Markdown template used in the script.
- Prints report to stdout and pushes to configured API endpoint.
- Optional: `scripts/run-recap-4h.py` appends report to the main session.

## Usage (Console Chat)
Use natural language messages that reference this skill:
- "Run daily-recap for yesterday using the Python script and Quant Readonly."
- "Generate daily-recap for 2026-03-08, Asia/Shanghai timezone."
- "Schedule daily-recap at 17:00 Asia/Shanghai every day and announce results."

## Usage (Local Scripts)
- Daily window:
  - `python3 scripts/generate_and_format_recap.py 'YYYY-MM-DD 00:00:00' 'YYYY-MM-DD 23:59:59'`
- Last 4 hours (also appends to main session):
  - `python3 scripts/run-recap-4h.py`

## SQL Guidance (MySQL)

**1. Core Metrics (ai_trade_order_close + ai_trade_order)**
```sql
SELECT
    COUNT(*) as trades_count,
    ROUND(SUM(COALESCE(income,0) - COALESCE(charge,0)), 2) as total_pnl,
    CONCAT(ROUND(SUM(CASE WHEN (income-charge) > 0 THEN 1 ELSE 0 END)/COUNT(*)*100, 2), '%') as win_rate,
    ROUND(ABS(SUM(CASE WHEN (income-charge) > 0 THEN (income-charge) ELSE 0 END) / NULLIF(SUM(CASE WHEN (income-charge) < 0 THEN (income-charge) ELSE 0 END), 0)), 2) as profit_loss_ratio,
    ROUND(MAX(income-charge), 2) as max_profit,
    ROUND(MIN(income-charge), 2) as max_loss,
    ROUND(AVG(TIMESTAMPDIFF(SECOND, o.create_time, c.sell_time))/60, 2) as avg_hold_time_min
FROM ai_trade_order_close c
JOIN ai_trade_order o ON c.order_sn = o.order_sn
WHERE sell_time BETWEEN 'START_TIME' AND 'END_TIME';
```

**2. Signal Stats (technical_signal)**
```sql
-- Counts by Timeframe and Action
SELECT timeframe, COUNT(*) as count
FROM technical_signal
WHERE kline_time BETWEEN 'START_TIME' AND 'END_TIME'
GROUP BY timeframe;
```

**3. Execution Analysis (ai_trade_order_close)**
```sql
SELECT
  AVG(CASE WHEN (income - charge) < 0 THEN ABS(income - charge) END) AS stop_avg_dist,
  SUM(CASE WHEN (income - charge) < 0 THEN 1 ELSE 0 END) AS stop_triggered,
  AVG(CASE WHEN (income - charge) > 0 THEN (income - charge) END) AS tp_avg_dist,
  SUM(CASE WHEN (income - charge) > 0 THEN 1 ELSE 0 END) AS tp_triggered
FROM ai_trade_order_close
WHERE sell_time BETWEEN 'START_TIME' AND 'END_TIME';
```

**4. Signal vs Order Matching**
```sql
-- Signals without Trades
SELECT COUNT(*) as signal_no_trade
FROM technical_signal ts
LEFT JOIN ai_trade_order t ON ts.order_sn = t.order_sn
WHERE ts.kline_time BETWEEN 'START_TIME' AND 'END_TIME' AND t.order_sn IS NULL;

-- Trades without Signals
SELECT COUNT(*) as trade_no_signal
FROM ai_trade_order t
LEFT JOIN technical_signal ts ON t.order_sn = ts.order_sn
WHERE t.create_time BETWEEN 'START_TIME' AND 'END_TIME' AND ts.order_sn IS NULL;

-- Matched Count
SELECT COUNT(*) as match_count
FROM technical_signal ts
JOIN ai_trade_order t ON ts.order_sn = t.order_sn
WHERE ts.kline_time BETWEEN 'START_TIME' AND 'END_TIME';
```

**5. 7-Day Trend**
```sql
SELECT DATE(sell_time) as date, COUNT(*) as trades, SUM(income-charge) as pnl
FROM ai_trade_order_close
WHERE sell_time >= CURDATE() - INTERVAL 7 DAY
GROUP BY DATE(sell_time)
ORDER BY date DESC;
```

## Output Format (Script Template)
Use the following Markdown template. Fill `{{ placeholders }}` with calculated values. If data is missing, use "N/A" or "Data not available".

```markdown
交易复盘与策略优化报告（模板）
一、核心指标概览
指标 | 值
---|---
交易日期 | {{ date }}
成交笔数 | {{ trades_count }}
总盈亏 (USDT) | {{ total_pnl }}
胜率 | {{ win_rate }}
盈亏比 | {{ profit_loss_ratio }}
最大单笔盈利 | {{ max_profit }}
最大单笔亏损 | {{ max_loss }}
平均持仓时间 | {{ avg_hold_time }} 分钟

二、信号系统表现
2.1 信号统计
周期 | 信号总数 | 强多 | 多 | 空 | 强空 | 平均强度
---|---|---|---|---|---|---
3m | {{ s_3m_total }} | {{ s_3m_sb }} | ... | ... | ... | {{ s_3m_avg_strength }}
15m | {{ s_15m_total }} | ... | ... | ... | ... | ...

2.2 信号有效性分析（基于后续3根3分钟K线）
信号方向 | 信号数量 | 有效次数 | 胜率 | 平均后续涨跌幅
---|---|---|---|---
强多 (LB) | {{ lb_count }} | {{ lb_valid }} | {{ lb_win_rate }}% | {{ lb_avg_move }}
强空 (SB) | {{ sb_count }} | {{ sb_valid }} | {{ sb_win_rate }}% | {{ sb_avg_move }}
合计 | {{ total_signals }} | {{ total_valid }} | {{ overall_win_rate }}% | -
分析：... (Generate analysis based on data)

2.3 多周期共振效果
(Data not currently available in DB - Placeholder)

三、交易执行分析
3.1 入场位置分布（按开仓价在K线中的分位）
(Requires OHLC data - Placeholder)

3.2 止损止盈分析
类型 | 平均距离 (USDT) | 被触发次数 | 触发后反向概率 | 建议调整
---|---|---|---|---
止损 | {{ avg_stop_dist }} | {{ stop_triggered }} | N/A | {{ stop_suggestion }}
止盈 | {{ avg_take_profit_dist }} | N/A | N/A | {{ tp_suggestion }}

四、信号与订单匹配度
维度 | 数量 | 占比 | 说明
---|---|---|---
信号未产生订单 | {{ signal_no_trade }} | {{ pct_signal_no_trade }}% | 可能因风控或系统延迟
无信号却产生订单 | {{ trade_no_signal }} | {{ pct_trade_no_signal }}% | 人工干预或其他策略
信号与订单方向一致 | {{ match_count }} | {{ pct_match }}% | 正常执行

五、最近4小时动态
最近4小时信号与交易概览。

六、策略优化建议
{{ suggestions }}

七、过去7天趋势对比
日期 | 信号数 | 成交笔数 | 盈亏 (USDT) | 胜率
---|---|---|---|---
{{ date1 }} | N/A | {{ trade1 }} | {{ pnl1 }} | {{ win1 }}%
... | ... | ... | ... | ...

八、待办清单
- 调整信号强度阈值...
- 修复 buy_time 为空问题...
```
