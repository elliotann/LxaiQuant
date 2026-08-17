-- 历史数据回填：从 technical_signal 用 LEAD 窗口函数配对生成 signal_alternate_log
-- 主关联键直接使用 technical_signal.strategy_name（不再 JOIN strategy 表）
-- 注意：technical_signal.timeframe 存的是周期 code（如 5m / 1H），signal_alternate_log.timeframe 存的是
--       CandlestickIntervalEnum.name()（如 OKXMIN5 / OKXMIN60），此处做 code -> OKX 枚举名映射
-- 回填前请确保 signal_alternate_log 表已创建（见 create_signal_alternate_log_table.sql）

INSERT INTO signal_alternate_log (
    strategy_name, symbol, timeframe,
    entry_time, entry_price, entry_direction, entry_signal_id,
    exit_time, exit_price, exit_direction, exit_signal_id,
    space_pct, minutes_between
)
WITH paired AS (
    SELECT
        ts.strategy_name,
        ts.symbol,
        CASE ts.timeframe
            WHEN '1m'  THEN 'OKXMIN1'
            WHEN '3m'  THEN 'OKXMIN3'
            WHEN '5m'  THEN 'OKXMIN5'
            WHEN '15m' THEN 'OKXMIN15'
            WHEN '30m' THEN 'OKXMIN30'
            WHEN '1H'  THEN 'OKXMIN60'
            WHEN '4H'  THEN 'OKX4HOUR'
            WHEN '1D'  THEN 'OKX1D'
            ELSE ts.timeframe
        END AS timeframe,
        ts.id AS entry_signal_id,
        ts.kline_timestamp AS entry_time,
        ts.close_price AS entry_price,
        ts.technical_direction AS entry_direction,
        LEAD(ts.id) OVER (
            PARTITION BY ts.strategy_name, ts.symbol, ts.timeframe
            ORDER BY ts.kline_timestamp, ts.id
        ) AS exit_signal_id,
        LEAD(ts.kline_timestamp) OVER (
            PARTITION BY ts.strategy_name, ts.symbol, ts.timeframe
            ORDER BY ts.kline_timestamp, ts.id
        ) AS exit_time,
        LEAD(ts.close_price) OVER (
            PARTITION BY ts.strategy_name, ts.symbol, ts.timeframe
            ORDER BY ts.kline_timestamp, ts.id
        ) AS exit_price,
        LEAD(ts.technical_direction) OVER (
            PARTITION BY ts.strategy_name, ts.symbol, ts.timeframe
            ORDER BY ts.kline_timestamp, ts.id
        ) AS exit_direction
    FROM technical_signal ts
    WHERE ts.technical_direction IN ('LONG', 'SHORT')
)
SELECT
    p.strategy_name,
    p.symbol,
    p.timeframe,
    p.entry_time,
    p.entry_price,
    p.entry_direction,
    p.entry_signal_id,
    p.exit_time,
    p.exit_price,
    p.exit_direction,
    p.exit_signal_id,
    ROUND((p.exit_price - p.entry_price) / p.entry_price * 100, 4) AS space_pct,
    ROUND((p.exit_time - p.entry_time) / 60000.0, 0) AS minutes_between
FROM paired p
WHERE p.exit_time IS NOT NULL
  AND p.entry_direction != p.exit_direction
  AND p.entry_price IS NOT NULL
  AND p.exit_price IS NOT NULL;
