#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""临时复盘脚本 - 修复字符集问题"""

import json
import pymysql
from datetime import datetime, timedelta

def generate_recap():
    try:
        connection = pymysql.connect(
            host='192.168.1.23',
            user='root',
            password='Hxa104906',
            database='vdr_shop_test',
            port=3306,
            charset='utf8mb4',
            collation='utf8mb4_unicode_ci'
        )

        with connection.cursor() as cursor:
            yesterday = (datetime.now() - timedelta(days=1)).strftime('%Y-%m-%d')
            start_time = f"{yesterday} 00:00:00"
            end_time = f"{yesterday} 23:59:59"

            lines = []

            lines.append("交易复盘与策略优化报告（模板）")
            lines.append("")

            cursor.execute("""
                SELECT 
                    COUNT(*) as trades_count,
                    ROUND(SUM(COALESCE(income,0) - COALESCE(charge,0)), 2) as total_pnl,
                    CONCAT(ROUND(SUM(CASE WHEN (income-charge) > 0 THEN 1 ELSE 0 END)/COUNT(*)*100, 2), '%%') as win_rate,
                    ROUND(ABS(SUM(CASE WHEN (income-charge) > 0 THEN (income-charge) ELSE 0 END) / NULLIF(SUM(CASE WHEN (income-charge) < 0 THEN (income-charge) ELSE 0 END), 0)), 2) as profit_loss_ratio,
                    ROUND(MAX(income-charge), 2) as max_profit,
                    ROUND(MIN(income-charge), 2) as max_loss,
                    NULL as avg_hold_time_min
                FROM ai_trade_exit_batch
                WHERE exit_time BETWEEN %s AND %s
            """, (start_time, end_time))

            core_data = cursor.fetchone()

            if core_data and core_data[0] > 0:
                trades_count = core_data[0]
                total_pnl = core_data[1]
                win_rate = core_data[2]
                profit_loss_ratio = core_data[3]
                max_profit = core_data[4]
                max_loss = core_data[5]
                avg_hold_time_min = core_data[6]
            else:
                trades_count = 0
                total_pnl = 0
                win_rate = "0.00%%"
                profit_loss_ratio = None
                max_profit = None
                max_loss = None
                avg_hold_time_min = None

            win_rate_str = win_rate.replace("%%", "%") if isinstance(win_rate, str) else "0.00%"

            lines.append("一、核心指标概览")
            lines.append("")
            lines.append("指标 | 值")
            lines.append("---|---")
            lines.append(f"交易日期 | {yesterday}")
            lines.append(f"成交笔数 | {trades_count}")
            lines.append(f"总盈亏 (USDT) | {total_pnl}")
            lines.append(f"胜率 | {win_rate_str}")
            lines.append(f"盈亏比 | {profit_loss_ratio if profit_loss_ratio is not None else 'N/A'}")
            lines.append(f"最大单笔盈利 | {max_profit if max_profit is not None else 'N/A'}")
            lines.append(f"最大单笔亏损 | {max_loss if max_loss is not None else 'N/A'}")
            avg_hold_str = f"{avg_hold_time_min} 分钟" if avg_hold_time_min is not None else "N/A"
            lines.append(f"平均持仓时间 | {avg_hold_str}")
            lines.append("")

            lines.append("二、信号系统表现")
            lines.append("")

            lines.append("2.1 信号统计")
            lines.append("")

            cursor.execute("""
                SELECT 
                    timeframe,
                    CAST(order_action AS CHAR) as action,
                    COUNT(*) as count,
                    AVG(risk_level) as avg_strength
                FROM trade_signal
                WHERE create_time BETWEEN %s AND %s
                GROUP BY timeframe, CAST(order_action AS CHAR)
            """, (start_time, end_time))

            raw_signal_stats = cursor.fetchall()

            signal_stats = {}
            for timeframe, action, count, avg_strength in raw_signal_stats:
                tf = timeframe or "N/A"
                action_str = action.decode('utf-8') if isinstance(action, bytes) else str(action)
                stats = signal_stats.setdefault(tf, {"total": 0, "LB": 0, "B": 0, "S": 0, "SB": 0, "strength_sum": 0.0, "strength_count": 0})
                stats["total"] += count
                if action_str in ("LB", "B", "S", "SB"):
                    stats[action_str] = stats.get(action_str, 0) + count
                if avg_strength is not None:
                    stats["strength_sum"] += float(avg_strength) * count
                    stats["strength_count"] += count

            lines.append("周期 | 信号总数 | 强多 | 多 | 空 | 强空 | 平均强度")
            lines.append("---|---|---|---|---|---|---")

            if signal_stats:
                for tf in sorted(signal_stats.keys()):
                    stats = signal_stats[tf]
                    if stats["strength_count"] > 0:
                        avg_strength = stats["strength_sum"] / stats["strength_count"]
                    else:
                        avg_strength = 0.0
                    lines.append(
                        f"{tf} | {stats['total']} | {stats.get('LB', 0)} | {stats.get('B', 0)} | "
                        f"{stats.get('S', 0)} | {stats.get('SB', 0)} | {avg_strength:.2f}"
                    )
            else:
                lines.append("N/A | 0 | 0 | 0 | 0 | 0 | 0.00")

            lines.append("")
            lines.append("2.2 信号有效性分析（基于后续3根3分钟K线）")
            lines.append("")
            lines.append("信号方向 | 信号数量 | 有效次数 | 胜率 | 平均后续涨跌幅")
            lines.append("---|---|---|---|---")

            cursor.execute("""
                SELECT 
                    CAST(order_action AS CHAR) as action,
                    COUNT(*) as total,
                    SUM(CASE WHEN pnl_amount > 0 THEN 1 ELSE 0 END) as valid_count,
                    AVG(pnl_percentage) as avg_move
                FROM trade_signal
                WHERE create_time BETWEEN %s AND %s
                  AND pnl_amount IS NOT NULL
                GROUP BY CAST(order_action AS CHAR)
            """, (start_time, end_time))

            validity_rows = cursor.fetchall()

            total_signals_valid = 0
            total_valid = 0

            for action, total, valid_count, avg_move in validity_rows:
                action_str = action.decode('utf-8') if isinstance(action, bytes) else str(action)
                win_rate_action = f"{(valid_count / total * 100):.1f}%" if total else "0.0%"
                move_str = f"{avg_move:.2f}%" if avg_move is not None else "N/A"
                lines.append(f"{action_str} | {total} | {valid_count} | {win_rate_action} | {move_str}")
                total_signals_valid += total
                total_valid += valid_count or 0

            if total_signals_valid > 0:
                overall_win_rate = f"{(total_valid / total_signals_valid * 100):.1f}%"
            else:
                overall_win_rate = "0.0%"

            lines.append(f"合计 | {total_signals_valid} | {total_valid} | {overall_win_rate} | -")
            lines.append("")
            lines.append("2.3 多周期共振效果")
            lines.append("")
            lines.append("当前版本暂未统计多周期共振数据，可后续基于多周期K线扩展。")
            lines.append("")

            lines.append("三、交易执行分析")
            lines.append("")
            lines.append("3.1 入场位置分布（按开仓价在K线中的分位）")
            lines.append("")
            lines.append("当前版本未接入K线OHLC数据，入场位置分布暂不可用。")
            lines.append("")

            lines.append("3.2 止损止盈分析")
            lines.append("")
            lines.append("类型 | 平均距离 (USDT) | 被触发次数 | 触发后反向概率 | 建议调整")
            lines.append("---|---|---|---|---")

            cursor.execute("""
                SELECT 
                    AVG(ABS(ts.stop_loss_price - t.buy_price)) as avg_stop_dist,
                    AVG(ABS(ts.take_profit_price - t.buy_price)) as avg_tp_dist
                FROM trade_signal ts
                JOIN ai_trade_order t 
                    ON CAST(ts.order_sn AS BINARY) = CAST(t.order_sn AS BINARY)
                WHERE t.buy_time BETWEEN %s AND %s 
                  AND ts.stop_loss_price IS NOT NULL 
                  AND ts.take_profit_price IS NOT NULL
            """, (start_time, end_time))

            sl_tp_data = cursor.fetchone()

            avg_stop_dist = None
            avg_tp_dist = None

            if sl_tp_data and sl_tp_data[0]:
                avg_stop_dist, avg_tp_dist = sl_tp_data

            cursor.execute("""
                SELECT COUNT(*) 
                FROM ai_trade_order t
                JOIN trade_signal ts 
                    ON CAST(ts.order_sn AS BINARY) = CAST(t.order_sn AS BINARY)
                WHERE t.exit_time BETWEEN %s AND %s
                  AND (
                    (t.order_side_enum='BUY' AND t.sell_price <= ts.stop_loss_price) OR
                    (t.order_side_enum='SELL' AND t.sell_price >= ts.stop_loss_price)
                  )
            """, (start_time, end_time))

            stop_triggered = cursor.fetchone()[0]

            avg_stop_str = f"{avg_stop_dist:.4f}" if avg_stop_dist is not None else "N/A"
            avg_tp_str = f"{avg_tp_dist:.4f}" if avg_tp_dist is not None else "N/A"

            if avg_stop_dist and avg_tp_dist and avg_stop_dist > 0:
                risk_ratio = avg_tp_dist / avg_stop_dist
            else:
                risk_ratio = None

            if risk_ratio is not None:
                if risk_ratio >= 2:
                    stop_suggestion = "风险收益比优秀，可适当保持或微调。"
                elif risk_ratio >= 1.5:
                    stop_suggestion = "风险收益比较好，可结合回撤情况微调。"
                else:
                    stop_suggestion = "风险收益比偏低，建议收紧止损或放宽止盈。"
            else:
                stop_suggestion = "样本不足，暂不评估。"

            lines.append(f"止损 | {avg_stop_str} | {stop_triggered} | N/A | {stop_suggestion}")
            lines.append(f"止盈 | {avg_tp_str} | N/A | N/A | 结合止损比例整体评估。")
            lines.append("")

            lines.append("四、信号与订单匹配度")
            lines.append("")
            lines.append("维度 | 数量 | 占比 | 说明")
            lines.append("---|---|---|---")

            cursor.execute("""
                SELECT COUNT(*) 
                FROM trade_signal ts
                LEFT JOIN ai_trade_order t 
                    ON CAST(ts.order_sn AS BINARY) = CAST(t.order_sn AS BINARY)
                WHERE ts.create_time BETWEEN %s AND %s AND t.order_sn IS NULL
            """, (start_time, end_time))
            signal_no_trade = cursor.fetchone()[0]

            cursor.execute("""
                SELECT COUNT(*) 
                FROM ai_trade_order t
                LEFT JOIN trade_signal ts 
                    ON CAST(t.order_sn AS BINARY) = CAST(ts.order_sn AS BINARY)
                WHERE t.buy_time BETWEEN %s AND %s AND ts.order_sn IS NULL
            """, (start_time, end_time))
            trade_no_signal = cursor.fetchone()[0]

            cursor.execute("""
                SELECT COUNT(*) 
                FROM trade_signal ts
                JOIN ai_trade_order t 
                    ON CAST(ts.order_sn AS BINARY) = CAST(t.order_sn AS BINARY)
                WHERE ts.create_time BETWEEN %s AND %s
            """, (start_time, end_time))
            match_count = cursor.fetchone()[0]

            total_signals = 0
            for stats in signal_stats.values():
                total_signals += stats["total"]

            total_trades = trades_count

            def pct(part, total):
                if total and part is not None:
                    return f"{(part / total * 100):.1f}%"
                return "0.0%"

            lines.append(
                f"信号未产生订单 | {signal_no_trade} | {pct(signal_no_trade, total_signals)} | 可能因风控或系统延迟"
            )
            lines.append(
                f"无信号却产生订单 | {trade_no_signal} | {pct(trade_no_signal, total_trades)} | "
                f"可能为人工干预或其他策略"
            )
            lines.append(
                f"信号与订单方向一致 | {match_count} | {pct(match_count, total_signals)} | 正常执行"
            )
            lines.append("")

            lines.append("五、策略优化建议")
            lines.append("")

            suggestions = []

            if total_signals > 0:
                conversion_rate = match_count / total_signals * 100
                if conversion_rate < 50:
                    suggestions.append("调整信号过滤：当前信号转化率偏低，建议提高信号强度阈值或收紧风控。")
                if signal_no_trade > total_signals * 0.3:
                    suggestions.append("检查自动交易执行链路：大量信号未产生订单，需排查风控拦截或下单失败。")

            if trades_count > 0 and trade_no_signal > trades_count * 0.3:
                suggestions.append("规范策略统一入口：存在较多无信号交易，建议梳理手动单和其他策略来源。")

            if risk_ratio is not None and risk_ratio < 1.5:
                suggestions.append("优化止盈止损配置：风险收益比偏低，可适当扩大止盈或收紧止损。")

            if not suggestions:
                suggestions.append("当前策略运行整体正常，建议继续观察样本积累后再做调优。")

            for s in suggestions:
                lines.append(f"- {s}")

            lines.append("")
            lines.append("六、过去7天趋势对比")
            lines.append("")
            lines.append("日期 | 信号数 | 成交笔数 | 盈亏 (USDT) | 胜率")
            lines.append("---|---|---|---|---")

            cursor.execute("""
                SELECT 
                    DATE(exit_time) as date,
                    COUNT(*) as trades,
                    ROUND(SUM(income-charge), 2) as pnl,
                    CONCAT(ROUND(SUM(CASE WHEN (income-charge) > 0 THEN 1 ELSE 0 END)/COUNT(*)*100, 2), '%%') as win_rate
                FROM ai_trade_exit_batch 
                WHERE exit_time >= CURDATE() - INTERVAL 7 DAY
                GROUP BY DATE(exit_time)
                ORDER BY date DESC
                LIMIT 7
            """)

            trend_data = cursor.fetchall()

            if trend_data:
                for date, trades, pnl, win_rate_7 in trend_data:
                    win_rate_7_str = win_rate_7.replace("%%", "%") if isinstance(win_rate_7, str) else "0.00%"
                    lines.append(f"{date} | N/A | {trades} | {pnl} | {win_rate_7_str}")
            else:
                lines.append("N/A | N/A | N/A | N/A | N/A")

            lines.append("")
            lines.append("七、待办清单")
            lines.append("")
            lines.append("- 接入多周期K线数据，实现入场分位与多周期共振统计。")
            lines.append("- 增加信号级别明细（3m/15m 等）与分时段表现分析。")
            lines.append("- 根据样本积累，持续优化止损止盈参数与仓位管理。")

            content = "\n".join(lines)
            print(content)
            try:
                out_path = r"f:\project\lenzeto\recap_yesterday.md"
                with open(out_path, "w", encoding="utf-8") as f:
                    f.write(content)
            except Exception as _:
                pass

    except Exception as e:
        print(f"❌ 数据库查询失败: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if 'connection' in locals():
            connection.close()

if __name__ == "__main__":
    generate_recap()
