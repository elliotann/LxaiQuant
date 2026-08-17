#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
每日交易复盘与策略优化脚本（增强版）
功能：
- 核心指标提取
- 信号统计与有效性分析（后续3根K线）
- 信号位置分布（在3分钟K线中的分位）
- 大周期上下文分析（15分钟/1小时/4小时趋势、支撑阻力距离）
- 止损止盈统计
- 信号订单匹配度
- 最近4小时动态
- 过去7天趋势对比
- 动态生成优化建议
- 生成 Markdown 报告并推送
"""

import sys
import io
import json
import pymysql
import requests
import logging
import os
import subprocess
import re
from datetime import datetime, timedelta

def format_number(value, format_str='+.2f'):
    """格式化数字，0值不显示正号"""
    if isinstance(value, (int, float)):
        if value == 0:
            return format(abs(value), format_str.replace('+', ''))
        elif value > 0:
            return '+' + format(value, format_str.replace('+', ''))
        else:
            return format(value, format_str.replace('+', ''))
    return str(value)
from typing import Dict, List, Any, Optional

def parse_bool(value: Optional[str], default: bool = True) -> bool:
    if value is None:
        return default
    return str(value).strip().lower() in {'1', 'true', 'yes', 'y', 'on'}

# ==================== 配置区域 ====================
CONFIG_FILE = os.environ.get('OPENCLAW_DB_CONFIG', '/Users/huangxuean/IdeaProjects/lenzeto/.openclaw-config/db-config.json')
API_ENDPOINT = os.getenv('OPENCLAW_API_ENDPOINT', "https://your-system.com/api/receive-replay")
API_TOKEN = os.getenv('OPENCLAW_API_TOKEN', os.getenv('MY_SYSTEM_API_TOKEN', ''))
OPENCLAW_PUSH_MAIN_ONLY = parse_bool(os.getenv('OPENCLAW_PUSH_MAIN_ONLY'), False)
LOG_LEVEL = logging.INFO
def resolve_openclaw_bin() -> str:
    candidates = [
        os.getenv('OPENCLAW_BIN'),
        '/Users/huangxuean/.nvm/versions/node/v22.22.1/bin/openclaw',
        'openclaw'
    ]
    for item in candidates:
        if item and (item == 'openclaw' or os.path.exists(item)):
            return item
    return 'openclaw'

OPENCLAW_BIN = resolve_openclaw_bin()
OPENCLAW_NODE_BIN = os.getenv('OPENCLAW_NODE_BIN', '/Users/huangxuean/.nvm/versions/node/v22.22.1/bin/node')
OPENCLAW_AGENT = os.getenv('OPENCLAW_AGENT', 'main')
OPENCLAW_AGENT_TIMEOUT = int(os.getenv('OPENCLAW_AGENT_TIMEOUT', '90'))
# ==================================================

logging.basicConfig(level=LOG_LEVEL, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def build_test_report(start_time: str, end_time: str) -> str:
    return f"""交易复盘测试报告
时间范围：{start_time} 至 {end_time}
状态：测试模式

本报告用于验证调度与流水线，请忽略内容。
"""

def load_api_config() -> Dict[str, Any]:
    endpoint = os.getenv('OPENCLAW_API_ENDPOINT') or os.getenv('MY_SYSTEM_API_ENDPOINT')
    token = os.getenv('OPENCLAW_API_TOKEN') or os.getenv('MY_SYSTEM_API_TOKEN')
    verify = parse_bool(os.getenv('OPENCLAW_API_VERIFY'), True)
    timeout = os.getenv('OPENCLAW_API_TIMEOUT')
    cfg = {}
    try:
        with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
            config = json.load(f)
            cfg = {
                'endpoint': config.get('apiEndpoint') or config.get('api_endpoint'),
                'token': config.get('apiToken') or config.get('api_token'),
                'verify': config.get('apiVerify') if config.get('apiVerify') is not None else None,
                'timeout': config.get('apiTimeout')
            }
    except Exception:
        cfg = {}
    final_verify = verify if cfg.get('verify') is None else parse_bool(cfg.get('verify'), True)
    final_timeout = int(timeout) if timeout else (int(cfg.get('timeout')) if cfg.get('timeout') else 10)
    return {
        'endpoint': endpoint or cfg.get('endpoint') or API_ENDPOINT,
        'token': token or cfg.get('token') or API_TOKEN,
        'verify': final_verify,
        'timeout': final_timeout
    }

def load_db_config():
    """加载数据库配置，支持环境变量覆盖"""
    env_host = os.getenv('OPENCLAW_DB_HOST')
    if env_host:
        return {
            'host': env_host,
            'port': int(os.getenv('OPENCLAW_DB_PORT', '3306')),
            'database': os.getenv('OPENCLAW_DB_NAME', ''),
            'user': os.getenv('OPENCLAW_DB_USER', ''),
            'password': os.getenv('OPENCLAW_DB_PASSWORD', '')
        }
    try:
        with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
            config = json.load(f)
            active_idx = config.get('activeIndex', 0)
            return config['databases'][active_idx]
    except Exception as e:
        logger.error(f"读取数据库配置失败: {e}")
        raise

def get_connection():
    """建立数据库连接"""
    db = load_db_config()
    try:
        conn = pymysql.connect(
            host=db['host'],
            port=db.get('port', 3306),
            user=db['user'],
            password=db['password'],
            database=db['database'],
            charset='utf8mb4',
            connect_timeout=5,
            read_timeout=30,
            write_timeout=30,
            cursorclass=pymysql.cursors.DictCursor
        )
        logger.info("数据库连接成功")
        return conn
    except Exception as e:
        logger.error(f"数据库连接失败: {e}")
        raise

def format_value(val, default="N/A", suffix="", precision=2):
    if val is None:
        return default
    if isinstance(val, (int, float)):
        return f"{val:.{precision}f}{suffix}"
    return f"{val}{suffix}"

def fetch_core_metrics(cursor, start_time, end_time) -> Dict:
    sql = """
    SELECT
        COUNT(t.order_sn) as trades_count,
        ROUND(SUM(t.pnl), 2) as total_pnl,
        CONCAT(ROUND(SUM(CASE WHEN t.pnl > 0 THEN 1 ELSE 0 END) / NULLIF(COUNT(t.order_sn),0) * 100, 2), '%%') as win_rate,
        ROUND(ABS(SUM(CASE WHEN t.pnl > 0 THEN t.pnl ELSE 0 END) /
            NULLIF(SUM(CASE WHEN t.pnl < 0 THEN t.pnl ELSE 0 END), 0)), 2) as profit_loss_ratio,
        ROUND(MAX(t.pnl), 2) as max_profit,
        ROUND(MIN(t.pnl), 2) as max_loss,
        ROUND(AVG(TIMESTAMPDIFF(SECOND, t.order_time, t.exit_time))/60, 2) as avg_hold_time_min
    FROM (
        SELECT
            order_sn,
            order_side_enum,
            buy_price,
            sell_price,
            order_time,
            exit_time,
            CASE
                WHEN buy_price IS NULL OR sell_price IS NULL THEN NULL
                WHEN UPPER(order_side_enum) IN ('BUY','LONG') THEN (sell_price - buy_price)
                WHEN UPPER(order_side_enum) IN ('SELL','SHORT') THEN (buy_price - sell_price)
                ELSE (sell_price - buy_price)
            END as pnl
        FROM ai_trade_order
        WHERE order_time BETWEEN %s AND %s
          AND buy_price IS NOT NULL
          AND sell_price IS NOT NULL
    ) t
    """
    cursor.execute(sql, (start_time, end_time))
    return cursor.fetchone() or {}

def fetch_signal_stats(cursor, start_time, end_time) -> List[Dict]:
    sql = """
    SELECT timeframe, COUNT(*) as count
    FROM technical_signal
    WHERE STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') BETWEEN DATE_SUB(%s, INTERVAL 1 DAY) AND %s
    GROUP BY timeframe
    """
    cursor.execute(sql, (end_time, end_time))
    return cursor.fetchall() or []

def fetch_recent_signals(cursor, start_time, end_time) -> List[Dict]:
    sql = """
    SELECT 
        timeframe,
        technical_direction,
        signal_strength
    FROM technical_signal
    WHERE STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') BETWEEN DATE_SUB(%s, INTERVAL 1 DAY) AND %s
    """
    cursor.execute(sql, (end_time, end_time))
    return cursor.fetchall()

def fetch_recent_trades(cursor, start_time, end_time) -> Dict:
    sql = """
    SELECT 
        COUNT(*) as trade_count,
        COALESCE(SUM(c.income - c.charge), 0) as total_pnl,
        SUM(CASE WHEN (c.income - c.charge) > 0 THEN 1 ELSE 0 END) as win_count
    FROM ai_trade_exit_batch c
    JOIN ai_trade_order o ON c.position_id = o.order_sn
    WHERE c.exit_time BETWEEN %s AND %s
    """
    cursor.execute(sql, (start_time, end_time))
    return cursor.fetchone() or {}

def fetch_recent_order_receipts(cursor, end_time, limit=20) -> List[Dict]:
    sql = """
    SELECT
        order_sn,
        symbol,
        order_side_enum,
        order_time,
        open_price,
        buy_price,
        sell_price,
        trade_order_status
    FROM ai_trade_order
    WHERE order_time BETWEEN DATE_SUB(%s, INTERVAL 1 DAY) AND %s
    ORDER BY order_time DESC
    LIMIT %s
    """
    cursor.execute(sql, (end_time, end_time, limit))
    return cursor.fetchall() or []

def fetch_recent_signal_receipts(cursor, end_time, limit=20) -> List[Dict]:
    sql = """
    SELECT
        id,
        symbol,
        timeframe,
        kline_time,
        technical_direction,
        signal_strength
    FROM technical_signal
    WHERE STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') BETWEEN DATE_SUB(%s, INTERVAL 1 DAY) AND %s
    ORDER BY STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') DESC
    LIMIT %s
    """
    cursor.execute(sql, (end_time, end_time, limit))
    return cursor.fetchall() or []

def fetch_signal_validity(cursor, start_time, end_time) -> List[Dict]:
    """
    计算信号有效性：对每个信号，检查后续3根3分钟K线是否朝信号方向运动
    返回按方向分组的统计（优化版：子查询预计算对齐时间）
    """
    # 优化后的主查询：一次性获取信号及其对应的3分钟K线
    sql = """
    SELECT
        ts.id,
        ts.symbol COLLATE utf8mb4_unicode_ci AS symbol,  -- 统一collation
        ts.kline_time,
        ts.technical_direction,
        ts.signal_strength,
        vc3.open_price  AS open_3m,
        vc3.close_price AS close_3m,
        vc3.high_price  AS high_3m,
        vc3.low_price   AS low_3m
    FROM (
        SELECT
            id,
            symbol,
            kline_time,
            technical_direction,
            signal_strength,
            DATE_FORMAT(
                FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(kline_time) / 180) * 180),
                '%%Y-%%m-%%d %%H:%%i:%%s'
            ) AS aligned_time
        FROM technical_signal
        WHERE kline_time BETWEEN %s AND %s
    ) ts
    JOIN vdr_candlestick vc3
        ON vc3.symbol COLLATE utf8mb4_unicode_ci = ts.symbol
        AND vc3.time_str COLLATE utf8mb4_unicode_ci = ts.aligned_time
        AND vc3.`candlestick_interval_enum` = 'OKXMIN3'
    """
    cursor.execute(sql, (start_time, end_time))
    signals = cursor.fetchall()

    # 分组统计
    stats = {
        'STRONG_BULLISH': {'total': 0, 'valid': 0},
        'BULLISH': {'total': 0, 'valid': 0},
        'STRONG_BEARISH': {'total': 0, 'valid': 0},
        'BEARISH': {'total': 0, 'valid': 0},
        'NEUTRAL': {'total': 0, 'valid': 0}
    }

    for sig in signals:
        direction = sig['technical_direction'].upper()
        if direction in ('BUY','LONG'):
            direction = 'BULLISH'
        elif direction in ('SELL','SHORT'):
            direction = 'BEARISH'
        if direction not in stats:
            direction = 'NEUTRAL'
        stats[direction]['total'] += 1

        # 获取后续3根3分钟K线的最高最低（仍需循环，但已优化主查询）
        sql_k = """
        SELECT MAX(high_price) as future_high, MIN(low_price) as future_low
        FROM vdr_candlestick
        WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
          AND `candlestick_interval_enum` = 'OKXMIN3'
          AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') > %s
          AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') <= DATE_ADD(%s, INTERVAL 9 MINUTE)
        """
        cursor.execute(sql_k, (sig['symbol'], sig['kline_time'], sig['kline_time']))
        k_row = cursor.fetchone()
        if not k_row or k_row['future_high'] is None:
            continue

        valid = False
        if 'BULLISH' in direction or direction in ('BUY','LONG'):
            if k_row['future_high'] > sig['high_3m']:
                valid = True
        elif 'BEARISH' in direction or direction in ('SELL','SHORT'):
            if k_row['future_low'] < sig['low_3m']:
                valid = True
        if valid:
            stats[direction]['valid'] += 1

    # 转换为列表
    result = []
    for dir_name, st in stats.items():
        if st['total'] > 0:
            result.append({
                'direction': dir_name,
                'total': st['total'],
                'valid': st['valid'],
                'win_rate': round(st['valid'] / st['total'] * 100, 2) if st['total'] else 0,
                'avg_move': '-'  # 后续涨跌幅可计算，此处略
            })
    return result

def fetch_signal_position(cursor, start_time, end_time) -> Dict:
    """分析信号收盘价在K线中的位置分位"""
    sql = """
    SELECT 
        ts.timeframe,
        ts.technical_direction,
        vc.open_price, vc.close_price, vc.high_price, vc.low_price
    FROM technical_signal ts
    JOIN vdr_candlestick vc
      ON vc.symbol COLLATE utf8mb4_unicode_ci = ts.symbol COLLATE utf8mb4_unicode_ci
     AND STR_TO_DATE(vc.time_str, '%%Y-%%m-%%d %%H:%%i:%%s') = 
         CASE 
            WHEN ts.timeframe IN ('3m','OKXMIN3') THEN FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(ts.kline_time)/180)*180)
            WHEN ts.timeframe IN ('15m','OKXMIN15') THEN FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(ts.kline_time)/900)*900)
            WHEN ts.timeframe IN ('4h','OKXMIN4H') THEN FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(ts.kline_time)/14400)*14400)
         END
    WHERE ts.kline_time BETWEEN %s AND %s
    """
    cursor.execute(sql, (start_time, end_time))
    rows = cursor.fetchall()

    result = {'3m': {'low':0,'mid':0,'high':0,'mismatch':0,'total':0},
              '15m': {'low':0,'mid':0,'high':0,'mismatch':0,'total':0},
              '4h': {'low':0,'mid':0,'high':0,'mismatch':0,'total':0}}
    for row in rows:
        tf = row['timeframe'].lower()
        if '3m' in tf or 'okxmin3' in tf:
            key = '3m'
        elif '15m' in tf or 'okxmin15' in tf:
            key = '15m'
        elif '4h' in tf or 'okxmin4h' in tf:
            key = '4h'
        else:
            continue
        hi = row['high_price']
        lo = row['low_price']
        close = row['close_price']
        if hi is None or lo is None or close is None or hi == lo:
            continue
        pos = (close - lo) / (hi - lo)
        bucket = 'low' if pos <= 0.33 else 'high' if pos > 0.66 else 'mid'
        result[key][bucket] += 1
        result[key]['total'] += 1

        direction = row['technical_direction'].upper()
        mismatch = False
        if ('BULL' in direction or 'LONG' in direction) and bucket == 'high':
            mismatch = True
        if ('BEAR' in direction or 'SHORT' in direction) and bucket == 'low':
            mismatch = True
        if mismatch:
            result[key]['mismatch'] += 1
    return result

def analyze_signal_context(cursor, start_time, end_time) -> Dict:
    """
    分析信号的大周期上下文（15分钟、1小时、4小时的趋势、支撑阻力距离）
    返回分组统计结果
    """
    # 获取信号及其3分钟K线
    sql = """
    SELECT 
        ts.id,
        ts.symbol,
        ts.kline_time,
        ts.technical_direction,
        ts.signal_strength,
        vc3.open_price as open_3m,
        vc3.close_price as close_3m,
        vc3.high_price as high_3m,
        vc3.low_price as low_3m
    FROM technical_signal ts
    JOIN vdr_candlestick vc3
      ON vc3.symbol COLLATE utf8mb4_unicode_ci = ts.symbol COLLATE utf8mb4_unicode_ci
     AND STR_TO_DATE(vc3.time_str, '%%Y-%%m-%%d %%H:%%i:%%s') = 
         FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(ts.kline_time)/180)*180)
    WHERE ts.kline_time BETWEEN %s AND %s
    """
    cursor.execute(sql, (start_time, end_time))
    signals = cursor.fetchall()

    # 初始化统计字典
    stats = {
        'trend_consistency': {'consistent': {'total':0,'valid':0}, 'inconsistent': {'total':0,'valid':0}},
        'near_support': {'yes': {'total':0,'valid':0}, 'no': {'total':0,'valid':0}},
        'near_resistance': {'yes': {'total':0,'valid':0}, 'no': {'total':0,'valid':0}},
        'support_resistance_15m': [],   # 记录每个信号的具体距离，用于后续建议
    }

    # 预先获取每个信号的大周期K线数据（15分钟、1小时、4小时）
    # 为简化，此处只做15分钟示例，如需更多周期可扩展
    for sig in signals:
        t = sig['kline_time']
        sym = sig['symbol']
        # 获取信号所在15分钟K线
        sql_15m = """
        SELECT 
            open_price, close_price, high_price, low_price
        FROM vdr_candlestick
        WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
          AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') = 
              FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/900)*900)
        """
        cursor.execute(sql_15m, (sym, t))
        row_15m = cursor.fetchone()
        if not row_15m:
            continue
        # 获取前20根15分钟K线的最高和最低（作为支撑阻力）
        sql_prev = """
        SELECT MAX(t.high_price) as max_high, MIN(t.low_price) as min_low
        FROM (
            SELECT high_price, low_price, STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') as ts
            FROM vdr_candlestick
            WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
              AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') < FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/900)*900)
            ORDER BY ts DESC
            LIMIT 20
        ) t
        """
        cursor.execute(sql_prev, (sym, t))
        prev = cursor.fetchone()
        if prev and prev['max_high'] and prev['min_low']:
            resistance = prev['max_high']
            support = prev['min_low']
        else:
            continue

        price = sig['close_3m']
        # 计算距离百分比
        dist_to_support = (price - support) / price * 100 if price else None
        dist_to_resistance = (resistance - price) / price * 100 if price else None

        # 判断趋势一致性（用15分钟K线涨跌简化）
        direction = sig['technical_direction'].upper()
        if direction in ('BULLISH','STRONG_BULLISH'):
            trend_up = row_15m['close_price'] > row_15m['open_price']
            consistent = trend_up
        elif direction in ('BEARISH','STRONG_BEARISH'):
            trend_down = row_15m['close_price'] < row_15m['open_price']
            consistent = trend_down
        else:
            consistent = False

        key_con = 'consistent' if consistent else 'inconsistent'
        stats['trend_consistency'][key_con]['total'] += 1

        # 获取信号有效性（需要先计算valid，这里省略，假设后面会填充）
        # 在实际使用时，应该先计算valid并存储在信号字典中

        # 记录距离，后续用于支撑阻力分析
        sig['dist_to_support'] = dist_to_support
        sig['dist_to_resistance'] = dist_to_resistance
        sig['consistent'] = consistent

    # 计算每个信号的有效性（后续3根3分钟K线是否朝信号方向）
    for sig in signals:
        sql_k = """
        SELECT MAX(high_price) as future_high, MIN(low_price) as future_low
        FROM vdr_candlestick
        WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
          AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') > FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/180)*180)
          AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') <= DATE_ADD(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/180)*180), INTERVAL 9 MINUTE)
        """
        cursor.execute(sql_k, (sig['symbol'], sig['kline_time'], sig['kline_time']))
        k_row = cursor.fetchone()
        if not k_row or k_row['future_high'] is None:
            sig['valid'] = False
        else:
            direction = sig['technical_direction'].upper()
            if 'BULLISH' in direction:
                sig['valid'] = k_row['future_high'] > sig['high_3m']
            elif 'BEARISH' in direction:
                sig['valid'] = k_row['future_low'] < sig['low_3m']
            else:
                sig['valid'] = False

        # 趋势一致性统计
        if sig.get('consistent'):
            stats['trend_consistency']['consistent']['valid'] += 1 if sig['valid'] else 0
        else:
            stats['trend_consistency']['inconsistent']['valid'] += 1 if sig['valid'] else 0

        # 接近支撑（做多且距离支撑 < 1%）
        direction = sig['technical_direction'].upper()
        if 'BULL' in direction and sig.get('dist_to_support') is not None and sig['dist_to_support'] < 1.0:
            stats['near_support']['yes']['total'] += 1
            if sig['valid']:
                stats['near_support']['yes']['valid'] += 1
        elif 'BULL' in direction:
            stats['near_support']['no']['total'] += 1
            if sig['valid']:
                stats['near_support']['no']['valid'] += 1

        # 接近阻力（做空且距离阻力 < 1%）
        if 'BEAR' in direction and sig.get('dist_to_resistance') is not None and sig['dist_to_resistance'] < 1.0:
            stats['near_resistance']['yes']['total'] += 1
            if sig['valid']:
                stats['near_resistance']['yes']['valid'] += 1
        elif 'BEAR' in direction:
            stats['near_resistance']['no']['total'] += 1
            if sig['valid']:
                stats['near_resistance']['no']['valid'] += 1

    return stats

def fetch_stop_take_analysis(cursor, start_time, end_time) -> Dict:
    """止损止盈分析（基于盈亏近似估算）"""
    threshold_pct = 0.003
    sql_basic = """
    SELECT
      AVG(CASE WHEN (c.income - c.charge) < 0 THEN ABS(c.income - c.charge) END) AS stop_avg_dist,
      SUM(CASE WHEN (c.income - c.charge) < 0 THEN 1 ELSE 0 END) AS stop_triggered,
      AVG(CASE WHEN (c.income - c.charge) > 0 THEN (c.income - c.charge) END) AS tp_avg_dist,
      SUM(CASE WHEN (c.income - c.charge) > 0 THEN 1 ELSE 0 END) AS tp_triggered
    FROM ai_trade_exit_batch c
    WHERE c.exit_time BETWEEN %s AND %s
    """
    cursor.execute(sql_basic, (start_time, end_time))
    basic = cursor.fetchone() or {}

    sql_losers = """
    SELECT COUNT(*) AS losers
    FROM ai_trade_exit_batch c
    JOIN ai_trade_order o ON c.position_id = o.order_sn
    WHERE c.exit_time BETWEEN %s AND %s
      AND (c.income - c.charge) < 0
    """
    cursor.execute(sql_losers, (start_time, end_time))
    losers_row = cursor.fetchone() or {'losers': 0}
    losers = losers_row.get('losers', 0) or 0

    sql_losers_rev = """
    SELECT COUNT(DISTINCT c.position_id) AS losers_reversed
    FROM ai_trade_exit_batch c
    JOIN ai_trade_order o ON c.position_id = o.order_sn
    WHERE c.exit_time BETWEEN %s AND %s
      AND (c.income - c.charge) < 0
      AND COALESCE(
        CASE
          WHEN UPPER(o.order_side_enum) IN ('SELL','SHORT') THEN o.buy_price
          ELSE o.sell_price
        END,
        o.sell_price,
        o.buy_price,
        o.open_price
      ) IS NOT NULL
      AND EXISTS (
        SELECT 1
        FROM vdr_candlestick vc
        WHERE vc.symbol COLLATE utf8mb4_unicode_ci = o.symbol COLLATE utf8mb4_unicode_ci
          AND STR_TO_DATE(vc.time_str, '%%Y-%%m-%%d %%H:%%i:%%s') > c.exit_time
          AND STR_TO_DATE(vc.time_str, '%%Y-%%m-%%d %%H:%%i:%%s') <= DATE_ADD(c.exit_time, INTERVAL 30 MINUTE)
          AND (
            (UPPER(o.order_side_enum) IN ('BUY','LONG') AND vc.high_price >= COALESCE(
                CASE
                  WHEN UPPER(o.order_side_enum) IN ('SELL','SHORT') THEN o.buy_price
                  ELSE o.sell_price
                END,
                o.sell_price,
                o.buy_price,
                o.open_price
            ) * (1 + %s))
            OR
            (UPPER(o.order_side_enum) IN ('SELL','SHORT') AND vc.low_price <= COALESCE(
                CASE
                  WHEN UPPER(o.order_side_enum) IN ('SELL','SHORT') THEN o.buy_price
                  ELSE o.sell_price
                END,
                o.sell_price,
                o.buy_price,
                o.open_price
            ) * (1 - %s))
          )
      )
    """
    cursor.execute(sql_losers_rev, (start_time, end_time, threshold_pct, threshold_pct))
    losers_rev_row = cursor.fetchone() or {'losers_reversed': 0}
    losers_reversed = losers_rev_row.get('losers_reversed', 0) or 0

    sql_winners = """
    SELECT COUNT(*) AS winners
    FROM ai_trade_exit_batch c
    WHERE c.exit_time BETWEEN %s AND %s
      AND (c.income - c.charge) > 0
    """
    cursor.execute(sql_winners, (start_time, end_time))
    winners_row = cursor.fetchone() or {'winners': 0}
    winners = winners_row.get('winners', 0) or 0

    sql_winners_cont = """
    SELECT COUNT(DISTINCT c.position_id) AS winners_continued
    FROM ai_trade_exit_batch c
    JOIN ai_trade_order o ON c.position_id = o.order_sn
    WHERE c.exit_time BETWEEN %s AND %s
      AND (c.income - c.charge) > 0
      AND COALESCE(
        CASE
          WHEN UPPER(o.order_side_enum) IN ('SELL','SHORT') THEN o.buy_price
          ELSE o.sell_price
        END,
        o.sell_price,
        o.buy_price,
        o.open_price
      ) IS NOT NULL
      AND EXISTS (
        SELECT 1
        FROM vdr_candlestick vc
        WHERE vc.symbol COLLATE utf8mb4_unicode_ci = o.symbol COLLATE utf8mb4_unicode_ci
          AND STR_TO_DATE(vc.time_str, '%%Y-%%m-%%d %%H:%%i:%%s') > c.exit_time
          AND STR_TO_DATE(vc.time_str, '%%Y-%%m-%%d %%H:%%i:%%s') <= DATE_ADD(c.exit_time, INTERVAL 30 MINUTE)
          AND (
            (UPPER(o.order_side_enum) IN ('BUY','LONG') AND vc.high_price >= COALESCE(
                CASE
                  WHEN UPPER(o.order_side_enum) IN ('SELL','SHORT') THEN o.buy_price
                  ELSE o.sell_price
                END,
                o.sell_price,
                o.buy_price,
                o.open_price
            ) * (1 + %s))
            OR
            (UPPER(o.order_side_enum) IN ('SELL','SHORT') AND vc.low_price <= COALESCE(
                CASE
                  WHEN UPPER(o.order_side_enum) IN ('SELL','SHORT') THEN o.buy_price
                  ELSE o.sell_price
                END,
                o.sell_price,
                o.buy_price,
                o.open_price
            ) * (1 - %s))
          )
      )
    """
    cursor.execute(sql_winners_cont, (start_time, end_time, threshold_pct, threshold_pct))
    winners_cont_row = cursor.fetchone() or {'winners_continued': 0}
    winners_continued = winners_cont_row.get('winners_continued', 0) or 0

    return {
        'stop_avg_dist': round(basic.get('stop_avg_dist', 0) or 0, 2),
        'stop_triggered': int(basic.get('stop_triggered', 0) or 0),
        'stop_reversal_rate': round((losers_reversed / losers * 100), 2) if losers else 0,
        'tp_avg_dist': round(basic.get('tp_avg_dist', 0) or 0, 2),
        'tp_triggered': int(basic.get('tp_triggered', 0) or 0),
        'tp_early_rate': round((winners_continued / winners * 100), 2) if winners else 0
    }

def fetch_matching(cursor, start_time, end_time) -> Dict:
    """信号与订单匹配度分析（按30分钟时间邻近、同品种匹配）"""
    sql_sig_count = """
    SELECT COUNT(*) AS sig_count
    FROM technical_signal
    WHERE kline_time BETWEEN %s AND %s
    """
    cursor.execute(sql_sig_count, (start_time, end_time))
    sig_count = (cursor.fetchone() or {'sig_count': 0}).get('sig_count', 0) or 0

    sql_order_count = """
    SELECT COUNT(*) AS order_count
    FROM ai_trade_order
    WHERE create_time BETWEEN %s AND %s
    """
    cursor.execute(sql_order_count, (start_time, end_time))
    order_count = (cursor.fetchone() or {'order_count': 0}).get('order_count', 0) or 0

    sql_matched_orders = """
    SELECT COUNT(*) AS matched_orders
    FROM ai_trade_order o
    WHERE o.create_time BETWEEN %s AND %s
      AND EXISTS (
        SELECT 1
        FROM technical_signal ts
        WHERE ts.symbol COLLATE utf8mb4_unicode_ci = o.symbol COLLATE utf8mb4_unicode_ci
          AND ts.kline_time <= o.create_time
          AND ts.kline_time >= DATE_SUB(o.create_time, INTERVAL 30 MINUTE)
      )
    """
    cursor.execute(sql_matched_orders, (start_time, end_time))
    matched_orders = (cursor.fetchone() or {'matched_orders': 0}).get('matched_orders', 0) or 0

    sql_matched_signals = """
    SELECT COUNT(*) AS matched_signals
    FROM technical_signal ts
    WHERE ts.kline_time BETWEEN %s AND %s
      AND EXISTS (
        SELECT 1
        FROM ai_trade_order o
        WHERE o.symbol COLLATE utf8mb4_unicode_ci = ts.symbol COLLATE utf8mb4_unicode_ci
          AND o.create_time >= ts.kline_time
          AND o.create_time <= DATE_ADD(ts.kline_time, INTERVAL 30 MINUTE)
      )
    """
    cursor.execute(sql_matched_signals, (start_time, end_time))
    matched_signals = (cursor.fetchone() or {'matched_signals': 0}).get('matched_signals', 0) or 0

    return {
        'signal_no_trade': max(sig_count - matched_signals, 0),
        'trade_no_signal': max(order_count - matched_orders, 0),
        'match_count': matched_orders
    }

def request_openclaw_entry_advice_batch(rows: List[Dict]) -> Dict[int, str]:
    if not rows:
        return {}
    def fmt(val):
        try:
            return f"{float(val):.2f}"
        except Exception:
            return 'N/A'
    blocks = []
    for idx, row in enumerate(rows, 1):
        order = row.get('order', {})
        sig = row.get('sig', {})
        kline = row.get('kline', {})
        position_pct = row.get('position_pct')
        entry_price = order.get('buy_price') or order.get('open_price')
        block = f"""[{idx}]
订单时间：{order.get('order_time') or order.get('create_time') or 'N/A'}
标的：{order.get('symbol') or 'N/A'}
方向：{order.get('order_side_enum') or 'N/A'}
开仓价：{fmt(entry_price)}
信号方向：{sig.get('technical_direction') or 'N/A'}
信号强度：{fmt(sig.get('signal_strength'))}
K线高低：{fmt(kline.get('high_price'))}/{fmt(kline.get('low_price'))}
位置分位：{f"{position_pct:.1f}%" if position_pct is not None else 'N/A'}
15m支撑/压力：{fmt(row.get('support_15m'))}/{fmt(row.get('resistance_15m'))}
"""
        blocks.append(block)
    prompt = "请根据以下订单与K线信息分别给出简短开仓位置建议（<=20字，中文）。仅输出以下格式：\n[序号] 建议\n不要输出其他内容。\n\n" + "\n".join(blocks)
    try:
        use_node = OPENCLAW_NODE_BIN and os.path.exists(OPENCLAW_NODE_BIN) and OPENCLAW_BIN != 'openclaw'
        if use_node:
            cmd = [OPENCLAW_NODE_BIN, OPENCLAW_BIN, "agent", "--agent", OPENCLAW_AGENT, "--message", prompt]
        else:
            cmd = [OPENCLAW_BIN, "agent", "--agent", OPENCLAW_AGENT, "--message", prompt]
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=OPENCLAW_AGENT_TIMEOUT
        )
        if result.returncode != 0:
            logger.error(f"OpenClaw建议生成失败: {result.stderr.strip()}")
            return {}
        text = (result.stdout or "").strip()
        if not text:
            return {}
        advice_map = {}
        for line in text.splitlines():
            m = re.match(r'^\s*\[?\s*(\d+)\s*\]?\s*[:：\-\.\)]?\s*(.+)\s*$', line)
            if m:
                advice_map[int(m.group(1))] = m.group(2).strip()
        return advice_map
    except Exception as e:
        logger.error(f"OpenClaw建议生成异常: {e}")
        return {}

def fetch_entry_position_advice(cursor, start_time, end_time, limit=20) -> List[Dict]:
    sql_orders = """
    SELECT order_sn, symbol, order_side_enum, buy_price, open_price, order_time, create_time
    FROM ai_trade_order
    WHERE order_time BETWEEN %s AND %s
    ORDER BY order_time DESC
    LIMIT %s
    """
    cursor.execute(sql_orders, (start_time, end_time, limit))
    orders = cursor.fetchall() or []
    results = []
    rows = []
    sql_signal = """
    SELECT technical_direction, signal_strength, kline_time
    FROM technical_signal
    WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
      AND STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') <= %s
      AND STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') >= DATE_SUB(%s, INTERVAL 30 MINUTE)
    ORDER BY STR_TO_DATE(kline_time, '%%Y-%%m-%%d %%H:%%i:%%s') DESC
    LIMIT 1
    """
    sql_kline = """
    SELECT open_price, close_price, high_price, low_price
    FROM vdr_candlestick
    WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
      AND candlestick_interval_enum = 'OKXMIN3'
      AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') = DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/180)*180), '%%Y-%%m-%%d %%H:%%i:%%s')
    """
    sql_15m = """
    SELECT open_price, close_price, high_price, low_price
    FROM vdr_candlestick
    WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
      AND candlestick_interval_enum = 'OKXMIN15'
      AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') = DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/900)*900), '%%Y-%%m-%%d %%H:%%i:%%s')
    """
    sql_15m_prev = """
    SELECT MAX(t.high_price) as max_high, MIN(t.low_price) as min_low
    FROM (
        SELECT high_price, low_price, STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') as ts
        FROM vdr_candlestick
        WHERE symbol COLLATE utf8mb4_unicode_ci = %s COLLATE utf8mb4_unicode_ci
          AND candlestick_interval_enum = 'OKXMIN15'
          AND STR_TO_DATE(time_str, '%%Y-%%m-%%d %%H:%%i:%%s') < DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(%s)/900)*900), '%%Y-%%m-%%d %%H:%%i:%%s')
        ORDER BY ts DESC
        LIMIT 20
    ) t
    """
    for order in orders:
        anchor_time = order.get('order_time') or order.get('create_time')
        sig = {}
        kline = {}
        row_15m = {}
        prev_15m = {}
        try:
            cursor.execute(sql_signal, (order['symbol'], anchor_time, anchor_time))
            sig = cursor.fetchone() or {}
            kline_time = sig.get('kline_time') or anchor_time
            cursor.execute(sql_kline, (order['symbol'], kline_time))
            kline = cursor.fetchone() or {}
            cursor.execute(sql_15m, (order['symbol'], anchor_time))
            row_15m = cursor.fetchone() or {}
            cursor.execute(sql_15m_prev, (order['symbol'], anchor_time))
            prev_15m = cursor.fetchone() or {}
        except Exception as e:
            logger.error(f"开仓位置建议查询失败: {e}")
            sig = {}
            kline = {}
            row_15m = {}
            prev_15m = {}
        support_15m = prev_15m.get('min_low') if prev_15m and prev_15m.get('min_low') is not None else None
        resistance_15m = prev_15m.get('max_high') if prev_15m and prev_15m.get('max_high') is not None else None
        entry_price = order.get('buy_price') or order.get('open_price')
        side = str(order.get('order_side_enum', '')).upper()
        signal_dir = str(sig.get('technical_direction', '')).upper()
        position_pct = None
        if entry_price is not None and kline.get('high_price') is not None and kline.get('low_price') is not None:
            hi = float(kline['high_price'])
            lo = float(kline['low_price'])
            if hi > lo:
                position_pct = (float(entry_price) - lo) / (hi - lo) * 100
        rule_advice = "数据不足"
        if position_pct is not None:
            if side in ('BUY', 'LONG'):
                if position_pct >= 70:
                    rule_advice = "开仓偏高，建议等待回踩或减仓"
                elif position_pct <= 30:
                    rule_advice = "开仓偏低，位置较优"
                else:
                    rule_advice = "中位入场，关注趋势确认"
            elif side in ('SELL', 'SHORT'):
                if position_pct >= 70:
                    rule_advice = "开仓偏高，空单位置较优"
                elif position_pct <= 30:
                    rule_advice = "开仓偏低，谨慎追空"
                else:
                    rule_advice = "中位入场，关注趋势确认"
            else:
                rule_advice = "方向未知"
            if signal_dir and ((signal_dir.startswith('BULL') and side in ('SELL', 'SHORT')) or (signal_dir.startswith('BEAR') and side in ('BUY', 'LONG'))):
                rule_advice = f"{rule_advice}；方向与信号不一致"
        rows.append({
            'order': order,
            'sig': sig,
            'kline': kline,
            'position_pct': position_pct,
            'anchor_time': anchor_time,
            'side': side,
            'signal_dir': signal_dir,
            'entry_price': entry_price,
            'rule_advice': rule_advice,
            'support_15m': support_15m,
            'resistance_15m': resistance_15m
        })
    advice_map = request_openclaw_entry_advice_batch(rows)
    for idx, row in enumerate(rows, 1):
        base_advice = row['rule_advice']
        openclaw_advice = advice_map.get(idx) if isinstance(advice_map, dict) else None
        if openclaw_advice:
            base_advice = f"{base_advice}（OpenClaw：{openclaw_advice}）"
        if row.get('support_15m') is not None or row.get('resistance_15m') is not None:
            support_text = f"{float(row['support_15m']):.2f}" if row.get('support_15m') is not None else "N/A"
            resistance_text = f"{float(row['resistance_15m']):.2f}" if row.get('resistance_15m') is not None else "N/A"
            advice = f"15m支撑{support_text} 压力{resistance_text}；{base_advice}"
        else:
            advice = base_advice
        results.append({
            'time': row['anchor_time'],
            'symbol': row['order'].get('symbol'),
            'side': row['side'] or 'N/A',
            'signal_strength': row['sig'].get('signal_strength'),
            'signal_direction': row['signal_dir'] or 'N/A',
            'entry_price': row['entry_price'],
            'low_price': row['kline'].get('low_price'),
            'high_price': row['kline'].get('high_price'),
            'position_pct': row['position_pct'],
            'advice': advice
        })
    return results

def fetch_trend(cursor, end_time, days=7) -> List[Dict]:
    """过去N天趋势"""
    sql = """
    SELECT DATE(exit_time) as date, COUNT(*) as trades, SUM(income-charge) as pnl
    FROM ai_trade_exit_batch
    WHERE exit_time >= DATE_SUB(%s, INTERVAL %s DAY) AND exit_time <= %s
    GROUP BY DATE(exit_time)
    ORDER BY date DESC
    """
    cursor.execute(sql, (end_time, days, end_time))
    return cursor.fetchall()

def request_openclaw_strategy_suggestions(data: Dict) -> Optional[str]:
    try:
        core = data.get('core', {})
        def to_float(value):
            try:
                return float(value)
            except Exception:
                return None
        win_rate = to_float(core.get('win_rate'))
        pos_mismatch = to_float(data.get('pos_mismatch_rate'))
        stop_rev = to_float(data.get('stop_reversal_rate'))
        tp_early = to_float(data.get('tp_early_rate'))
        trades_count = int(core.get('trades_count') or 0)
        recent_trade_count = int(data.get('recent_trades', {}).get('trade_count') or 0)
        strength_threshold = 0.6
        if win_rate is not None:
            if win_rate >= 55:
                strength_threshold = 0.5
            elif win_rate >= 45:
                strength_threshold = 0.55
        pos_low, pos_high = 25, 75
        if pos_mismatch is not None:
            if pos_mismatch > 20:
                pos_low, pos_high = 30, 70
            elif pos_mismatch <= 10:
                pos_low, pos_high = 20, 80
        stoploss_pct = 0.7
        if stop_rev is not None:
            if stop_rev > 40:
                stoploss_pct = 0.9
            elif stop_rev <= 20:
                stoploss_pct = 0.6
        takeprofit_pct = 2.1
        if tp_early is not None:
            if tp_early > 40:
                takeprofit_pct = 2.6
            elif tp_early <= 20:
                takeprofit_pct = 1.8
        max_trades = 10 if trades_count >= 20 else 5
        cooldown_minutes = 30 if recent_trade_count < 3 else 15
        context = data.get('signal_context', {})
        cons_total = context.get('trend_consistency', {}).get('consistent', {}).get('total', 0)
        cons_valid = context.get('trend_consistency', {}).get('consistent', {}).get('valid', 0)
        incons_total = context.get('trend_consistency', {}).get('inconsistent', {}).get('total', 0)
        incons_valid = context.get('trend_consistency', {}).get('inconsistent', {}).get('valid', 0)
        cons_win = (cons_valid / cons_total * 100) if cons_total else 0
        incons_win = (incons_valid / incons_total * 100) if incons_total else 0
        cons_diff = cons_win - incons_win
        prompt = f"""请基于以下复盘摘要给出策略优化建议（中文，3-6条要点，每条<=28字）。要求每条都包含明确可执行参数或阈值，不要空泛表述，不要复述数据。必须至少引用以下“参考阈值”中的一个数值。
参考阈值：强度阈值≥{strength_threshold:.2f}；位置分位{pos_low}-{pos_high}%；止损{stoploss_pct:.1f}%；止盈{takeprofit_pct:.1f}%；15分钟一致性差值≥{cons_diff:.1f}%时启用一致性过滤；低成交时暂停{cooldown_minutes}分钟；每日最多{max_trades}次。
整体胜率：{core.get('win_rate', 'N/A')}
盈亏比：{core.get('profit_loss_ratio', 'N/A')}
成交笔数：{core.get('trades_count', 0)}
最大单笔盈利：{core.get('max_profit', 0)}
最大单笔亏损：{core.get('max_loss', 0)}
近4小时成交：{data.get('recent_trades', {}).get('trade_count', 0)}
信号未交易占比：{data.get('signal_no_trade_rate', 0):.1f}%
位置错位占比：{data.get('pos_mismatch_rate', 0):.1f}%
止损反向率：{data.get('stop_reversal_rate', 0):.1f}%
止盈过早率：{data.get('tp_early_rate', 0):.1f}%
15分钟趋势一致胜率：{(data.get('signal_context', {}).get('trend_consistency', {}).get('consistent', {}).get('valid', 0) / max(data.get('signal_context', {}).get('trend_consistency', {}).get('consistent', {}).get('total', 1), 1) * 100):.1f}%
"""
        use_node = OPENCLAW_NODE_BIN and os.path.exists(OPENCLAW_NODE_BIN) and OPENCLAW_BIN != 'openclaw'
        if use_node:
            cmd = [OPENCLAW_NODE_BIN, OPENCLAW_BIN, "agent", "--agent", OPENCLAW_AGENT, "--message", prompt]
        else:
            cmd = [OPENCLAW_BIN, "agent", "--agent", OPENCLAW_AGENT, "--message", prompt]
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=OPENCLAW_AGENT_TIMEOUT
        )
        if result.returncode != 0:
            logger.error(f"OpenClaw策略建议生成失败: {result.stderr.strip()}")
            return None
        text = (result.stdout or "").strip()
        if not text:
            return None
        return text
    except Exception as e:
        logger.error(f"OpenClaw策略建议生成异常: {e}")
        return None

def request_openclaw_stop_take_advice(stop_take: Dict) -> Dict[str, str]:
    stop_avg_dist = float(stop_take.get('stop_avg_dist', 0) or 0)
    stop_triggered = int(stop_take.get('stop_triggered', 0) or 0)
    stop_reversal_rate = float(stop_take.get('stop_reversal_rate', 0) or 0)
    tp_avg_dist = float(stop_take.get('tp_avg_dist', 0) or 0)
    tp_triggered = int(stop_take.get('tp_triggered', 0) or 0)
    tp_early_rate = float(stop_take.get('tp_early_rate', 0) or 0)

    stop_advice = "根据反向概率判断是否调整"
    tp_advice = "同上"
    if stop_triggered == 0:
        stop_advice = "无止损触发，暂无建议"
    elif stop_reversal_rate > 50:
        stop_advice = "止损偏紧，建议放宽或用ATR"
    elif stop_reversal_rate < 20:
        stop_advice = "止损有效，可适度收紧"

    if tp_triggered == 0:
        tp_advice = "无止盈触发，暂无建议"
    elif tp_early_rate > 50:
        tp_advice = "止盈偏早，考虑提高目标或移动止盈"
    elif tp_early_rate < 20:
        tp_advice = "止盈合理，关注入场信号"

    if stop_triggered == 0 and tp_triggered == 0:
        return {'stop': stop_advice, 'tp': tp_advice}

    prompt = f"""请基于以下止损止盈统计给出建议（中文，<=20字）。仅输出两行，格式：
[止损] 建议
[止盈] 建议
若触发次数为0，请给出“暂无建议”类表述。
止损：平均距离{stop_avg_dist:.1f}USDT，触发{stop_triggered}次，反向概率{stop_reversal_rate:.1f}%
止盈：平均距离{tp_avg_dist:.1f}USDT，触发{tp_triggered}次，反向概率{tp_early_rate:.1f}%
"""
    try:
        use_node = OPENCLAW_NODE_BIN and os.path.exists(OPENCLAW_NODE_BIN) and OPENCLAW_BIN != 'openclaw'
        if use_node:
            cmd = [OPENCLAW_NODE_BIN, OPENCLAW_BIN, "agent", "--agent", OPENCLAW_AGENT, "--message", prompt]
        else:
            cmd = [OPENCLAW_BIN, "agent", "--agent", OPENCLAW_AGENT, "--message", prompt]
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=OPENCLAW_AGENT_TIMEOUT
        )
        if result.returncode != 0:
            logger.error(f"OpenClaw止损止盈建议生成失败: {result.stderr.strip()}")
            return {'stop': stop_advice, 'tp': tp_advice}
        text = (result.stdout or "").strip()
        if not text:
            return {'stop': stop_advice, 'tp': tp_advice}
        adv = {'stop': stop_advice, 'tp': tp_advice}
        for line in text.splitlines():
            m = re.match(r'^\s*\[?\s*(止损|止盈)\s*\]?\s*[:：\-\.\)]?\s*(.+)\s*$', line)
            if not m:
                continue
            key = 'stop' if m.group(1) == '止损' else 'tp'
            adv[key] = m.group(2).strip()
        return adv
    except Exception as e:
        logger.error(f"OpenClaw止损止盈建议生成异常: {e}")
        return {'stop': stop_advice, 'tp': tp_advice}

def generate_suggestions(data: Dict) -> str:
    """动态生成优化建议"""
    agent_text = request_openclaw_strategy_suggestions(data)
    if agent_text:
        lines = []
        for raw in agent_text.splitlines():
            line = raw.strip()
            if not line:
                continue
            if line.startswith("- "):
                lines.append(line)
            else:
                lines.append(f"- {line}")
        return "\n".join(lines)
    suggestions = []
    # 信号有效性分组
    validity = data.get('validity', [])
    overall_win = data.get('overall_win_rate', 0)
    if overall_win < 45:
        suggestions.append(f"- **信号过滤**：整体胜率 {overall_win:.1f}%，建议提高入场标准（如更严格的强度阈值或多周期一致性）。")

    # 位置错位率
    pos_mismatch = data.get('pos_mismatch_rate', 0)
    if pos_mismatch > 20:
        suggestions.append(f"- **位置过滤**：方向错位信号占比 {pos_mismatch:.1f}%，此类信号胜率较低，建议增加位置过滤（避免在高位开多、低位开空）。")

    # 止损分析
    stop_rev = data.get('stop_reversal_rate', 0)
    if stop_rev > 40:
        suggestions.append(f"- **止损调整**：{stop_rev:.1f}%的止损触发后价格反向，建议适度加大初始止损或采用移动止损以降低“止损后反向”概率。")

    # 止盈分析
    tp_early = data.get('tp_early_rate', 0)
    if tp_early > 40:
        suggestions.append("- **止盈调整**：止盈过早比例较高，建议采用分批止盈或移动止损。")

    # 信号未交易
    sig_no_trade_rate = data.get('signal_no_trade_rate', 0)
    if sig_no_trade_rate > 30:
        suggestions.append(f"- **执行检查**：{sig_no_trade_rate:.1f}%的信号未产生订单，请检查机器人状态及自动交易开关。")

    # 大周期上下文建议
    context = data.get('signal_context', {})
    cons_total = context.get('trend_consistency', {}).get('consistent', {}).get('total', 0)
    cons_valid = context.get('trend_consistency', {}).get('consistent', {}).get('valid', 0)
    cons_win = (cons_valid / cons_total * 100) if cons_total else 0
    incons_total = context.get('trend_consistency', {}).get('inconsistent', {}).get('total', 0)
    incons_valid = context.get('trend_consistency', {}).get('inconsistent', {}).get('valid', 0)
    incons_win = (incons_valid / incons_total * 100) if incons_total else 0
    if cons_win - incons_win > 15:
        suggestions.append(f"- **多周期共振**：与15分钟趋势一致的信号胜率 {cons_win:.1f}%，高于反向信号 {incons_win:.1f}%，建议增加趋势过滤。")

    if not suggestions:
        suggestions.append("- 当前策略表现良好，无紧急优化建议，请保持监控。")

    return "\n".join(suggestions)

def generate_markdown(data: Dict, date_str: str, recent_start: str, end_time: str) -> str:
    """生成符合模板格式的 Markdown 报告"""
    def format_order_side_text(side: Any) -> str:
        if side is None:
            return "N/A"
        text = str(side)
        normalized = text.upper()
        if normalized == "SELL":
            return "空"
        if normalized == "BUY":
            return "多"
        return text

    def format_order_status_text(status: Any) -> str:
        if status is None:
            return "N/A"
        text = str(status)
        normalized = text.upper()
        if normalized == "LOSS":
            return "止损"
        if normalized == "GAIN":
            return "止盈"
        if normalized == "DEAL":
            return "持仓中"
        return text

    def format_signal_direction_text(direction: Any) -> str:
        if direction is None:
            return "N/A"
        text = str(direction)
        normalized = text.upper()
        if normalized == "SB":
            return "空"
        if normalized == "LB":
            return "多"
        return text

    def format_entry_side_text(side: Any) -> str:
        if side is None:
            return "N/A"
        text = str(side)
        normalized = text.upper()
        if normalized in ("SELL", "SHORT"):
            return "开空"
        if normalized in ("BUY", "LONG"):
            return "开多"
        return text

    core = data.get('core', {})
    signals = data.get('signals', [])
    recent_signals = data.get('recent_signals', [])
    recent_trades = data.get('recent_trades', {})
    position_stats = data.get('position_stats', {})
    stop_take = data.get('stop_take', {})
    matching = data.get('matching', {})
    trend = data.get('trend', [])
    validity = data.get('validity', [])
    context = data.get('signal_context', {})
    entry_advice = data.get('entry_advice', [])
    order_receipts = data.get('order_receipts', [])
    signal_receipts = data.get('signal_receipts', [])
    
    # 核心指标（使用实际数据或默认值）
    trades_count = core.get('trades_count', 0) or 0
    total_pnl = core.get('total_pnl', 0.0) or 0.0
    win_rate = core.get('win_rate', '0%') or '0%'
    profit_loss_ratio = core.get('profit_loss_ratio', 0.0) or 0.0
    max_profit = core.get('max_profit', 0.0) or 0.0
    max_loss = core.get('max_loss', 0.0) or 0.0
    avg_hold_time_min = core.get('avg_hold_time_min', 0.0) or 0.0
    
    # 信号统计（从signals列表计算）
    s_3m_total = sum(s['count'] for s in signals if '3m' in s['timeframe'].lower() or 'okxmin3' in s['timeframe'].lower())
    s_15m_total = sum(s['count'] for s in signals if '15m' in s['timeframe'].lower() or 'okxmin15' in s['timeframe'].lower())
    s_4h_total = sum(s['count'] for s in signals if '4h' in s['timeframe'].lower() or 'okxmin4h' in s['timeframe'].lower())
    
    # 信号方向分布与平均强度（依据 recent_signals 计算）
    def tf_key(tf):
        t = tf.lower()
        if '3m' in t or 'okxmin3' in t:
            return '3m'
        if '15m' in t or 'okxmin15' in t:
            return '15m'
        if '4h' in t or 'okxmin4h' in t:
            return '4h'
        return None
    buckets = {
        '3m': {'sb':0,'b':0,'s':0,'ss':0,'sum_strength':0.0,'n':0},
        '15m': {'sb':0,'b':0,'s':0,'ss':0,'sum_strength':0.0,'n':0},
        '4h': {'sb':0,'b':0,'s':0,'ss':0,'sum_strength':0.0,'n':0},
    }
    for rs in recent_signals:
        key = tf_key(rs.get('timeframe',''))
        if not key:
            continue
        d = str(rs.get('technical_direction','')).upper()
        if 'STRONG_BULL' in d:
            buckets[key]['sb'] += 1
        elif 'BULL' in d or 'LONG' in d or 'BUY' in d:
            buckets[key]['b'] += 1
        elif 'STRONG_BEAR' in d:
            buckets[key]['ss'] += 1
        elif 'BEAR' in d or 'SHORT' in d or 'SELL' in d:
            buckets[key]['s'] += 1
        buckets[key]['sum_strength'] += float(rs.get('signal_strength',0) or 0)
        buckets[key]['n'] += 1
    s_3m_sb = buckets['3m']['sb']; s_3m_b = buckets['3m']['b']; s_3m_s = buckets['3m']['s']; s_3m_ss = buckets['3m']['ss']
    s_15m_sb = buckets['15m']['sb']; s_15m_b = buckets['15m']['b']; s_15m_s = buckets['15m']['s']; s_15m_ss = buckets['15m']['ss']
    s_4h_sb = buckets['4h']['sb']; s_4h_b = buckets['4h']['b']; s_4h_s = buckets['4h']['s']; s_4h_ss = buckets['4h']['ss']
    s_3m_avg_strength = (buckets['3m']['sum_strength']/buckets['3m']['n']) if buckets['3m']['n'] else 0
    s_15m_avg_strength = (buckets['15m']['sum_strength']/buckets['15m']['n']) if buckets['15m']['n'] else 0
    s_4h_avg_strength = (buckets['4h']['sum_strength']/buckets['4h']['n']) if buckets['4h']['n'] else 0
    
    # 位置统计
    pos_3m = position_stats.get('3m', {})
    pos_15m = position_stats.get('15m', {})
    pos_4h = position_stats.get('4h', {})
    
    # 大周期上下文分析
    trend_cons = context.get('trend_consistency', {})
    cons_tot = trend_cons.get('consistent', {}).get('total', 0)
    cons_val = trend_cons.get('consistent', {}).get('valid', 0)
    cons_win = (cons_val / cons_tot * 100) if cons_tot else 0
    incons_tot = trend_cons.get('inconsistent', {}).get('total', 0)
    incons_val = trend_cons.get('inconsistent', {}).get('valid', 0)
    incons_win = (incons_val / incons_tot * 100) if incons_tot else 0
    
    near_sup = context.get('near_support', {})
    sup_yes_tot = near_sup.get('yes', {}).get('total', 0)
    sup_yes_val = near_sup.get('yes', {}).get('valid', 0)
    sup_yes_win = (sup_yes_val / sup_yes_tot * 100) if sup_yes_tot else 0
    sup_no_tot = near_sup.get('no', {}).get('total', 0)
    sup_no_val = near_sup.get('no', {}).get('valid', 0)
    sup_no_win = (sup_no_val / sup_no_tot * 100) if sup_no_tot else 0
    
    near_res = context.get('near_resistance', {})
    res_yes_tot = near_res.get('yes', {}).get('total', 0)
    res_yes_val = near_res.get('yes', {}).get('valid', 0)
    res_yes_win = (res_yes_val / res_yes_tot * 100) if res_yes_tot else 0
    res_no_tot = near_res.get('no', {}).get('total', 0)
    res_no_val = near_res.get('no', {}).get('valid', 0)
    res_no_win = (res_no_val / res_no_tot * 100) if res_no_tot else 0
    
    # 止损止盈统计
    stop_avg_dist = stop_take.get('stop_avg_dist', 0.0) or 0.0
    stop_triggered = stop_take.get('stop_triggered', 0) or 0
    stop_reversal_rate = stop_take.get('stop_reversal_rate', 0.0) or 0.0
    tp_avg_dist = stop_take.get('tp_avg_dist', 0.0) or 0.0
    tp_triggered = stop_take.get('tp_triggered', 0) or 0
    tp_early_rate = stop_take.get('tp_early_rate', 0.0) or 0.0
    stop_take_adv = request_openclaw_stop_take_advice(stop_take)
    stop_advice = stop_take_adv.get('stop', '根据反向概率判断是否调整')
    tp_advice = stop_take_adv.get('tp', '同上')
    
    # 匹配度统计
    signal_no_trade = matching.get('signal_no_trade', 0) or 0
    trade_no_signal = matching.get('trade_no_signal', 0) or 0
    match_count = matching.get('match_count', 0) or 0
    
    # 最近4小时信号分布
    rec_3m = [s for s in recent_signals if '3m' in s.get('timeframe','').lower()]
    rec_15m = [s for s in recent_signals if '15m' in s.get('timeframe','').lower()]
    rec_4h = [s for s in recent_signals if '4h' in s.get('timeframe','').lower()]
    
    recent_3m_total = len(rec_3m)
    recent_3m_bull = sum(1 for s in rec_3m if 'BULL' in s.get('technical_direction','').upper() or 'LONG' in s.get('technical_direction','').upper())
    recent_3m_bear = recent_3m_total - recent_3m_bull
    recent_3m_avg_strength = (sum(s.get('signal_strength',0) for s in rec_3m) / recent_3m_total) if recent_3m_total else 0
    
    recent_15m_total = len(rec_15m)
    recent_15m_bull = sum(1 for s in rec_15m if 'BULL' in s.get('technical_direction','').upper() or 'LONG' in s.get('technical_direction','').upper())
    recent_15m_bear = recent_15m_total - recent_15m_bull
    recent_15m_avg_strength = (sum(s.get('signal_strength',0) for s in rec_15m) / recent_15m_total) if recent_15m_total else 0
    
    recent_4h_total = len(rec_4h)
    recent_4h_bull = sum(1 for s in rec_4h if 'BULL' in s.get('technical_direction','').upper() or 'LONG' in s.get('technical_direction','').upper())
    recent_4h_bear = recent_4h_total - recent_4h_bull
    recent_4h_avg_strength = (sum(s.get('signal_strength',0) for s in rec_4h) / recent_4h_total) if recent_4h_total else 0
    
    # 最近4小时交易情况
    recent_trade_count = recent_trades.get('trade_count', 0) or 0
    recent_total_pnl = recent_trades.get('total_pnl', 0.0) or 0.0
    recent_win_count = recent_trades.get('win_count', 0) or 0
    recent_win_rate = (recent_win_count / recent_trade_count * 100) if recent_trade_count else 0
    
    # 生成报告
    source = data.get('source', '未知')
    source_label = '数据库' if source == 'DB' else ('降级占位' if source == 'DEGRADED' else source)
    report = f"""## 交易复盘与策略优化报告 ({date_str})

数据来源：{source_label}

### 一、核心指标概览
| 指标 | 值 |
|------|-----|
| 交易日期 | {date_str} |
| 成交笔数 | {trades_count} |
| 总盈亏 (USDT) | {total_pnl:+.2f} |
| 胜率 | {win_rate} |
| 盈亏比 | {profit_loss_ratio:.2f} |
| 最大单笔盈利 | {max_profit:.2f} |
| 最大单笔亏损 | {max_loss:+.2f} |
| 平均持仓时间 | {avg_hold_time_min:.2f} 分钟 |

### 二、信号系统表现

#### 2.1 信号统计
| 周期 | 信号总数 | 强多 | 多 | 空 | 强空 | 平均强度 |
|------|----------|------|----|----|------|----------|
| 3m | {s_3m_total} | {s_3m_sb} | {s_3m_b} | {s_3m_s} | {s_3m_ss} | {s_3m_avg_strength:.2f} |
| 15m | {s_15m_total} | {s_15m_sb} | {s_15m_b} | {s_15m_s} | {s_15m_ss} | {s_15m_avg_strength:.2f} |
| 4h | {s_4h_total} | {s_4h_sb} | {s_4h_b} | {s_4h_s} | {s_4h_ss} | {s_4h_avg_strength:.2f} |

#### 2.2 信号有效性（基于后续3根3分钟K线）
| 信号方向 | 信号数 | 有效数 | 胜率 | 平均后续涨跌幅 |
|---------|--------|--------|------|--------------|"""
    
    # 添加有效性行（如果有数据）
    if validity:
        for v in validity:
            report += f"| {v.get('direction', '-')} | {v.get('total', 0)} | {v.get('valid', 0)} | {v.get('win_rate', 0):.1f}% | - |\n"
    else:
        report += "| - | 0 | 0 | 0.0% | - |\n"
    
    report += f"""

#### 2.3 信号位置分布（按收盘价在K线中的分位）
| 周期 | 低位(≤33%) | 中位(33-66%) | 高位(>66%) | 方向错位 | 错位占比 |
|------|------------|--------------|------------|----------|----------|
| 3m | {pos_3m.get('low', 0)} | {pos_3m.get('mid', 0)} | {pos_3m.get('high', 0)} | {pos_3m.get('mismatch', 0)} | {(pos_3m.get('mismatch', 0) / pos_3m.get('total', 1) * 100) if pos_3m.get('total', 1) else 0:.1f}% |
| 15m | {pos_15m.get('low', 0)} | {pos_15m.get('mid', 0)} | {pos_15m.get('high', 0)} | {pos_15m.get('mismatch', 0)} | {(pos_15m.get('mismatch', 0) / pos_15m.get('total', 1) * 100) if pos_15m.get('total', 1) else 0:.1f}% |
| 4h | {pos_4h.get('low', 0)} | {pos_4h.get('mid', 0)} | {pos_4h.get('high', 0)} | {pos_4h.get('mismatch', 0)} | {(pos_4h.get('mismatch', 0) / pos_4h.get('total', 1) * 100) if pos_4h.get('total', 1) else 0:.1f}% |

#### 2.4 大周期上下文分析
| 上下文特征 | 信号数 | 有效数 | 胜率 |
|------------|--------|--------|------|
| 3m信号与15m趋势一致 | {cons_tot} | {cons_val} | {cons_win:.1f}% |
| 3m信号与15m趋势相反 | {incons_tot} | {incons_val} | {incons_win:.1f}% |
| 做多信号靠近15m支撑 | {sup_yes_tot} | {sup_yes_val} | {sup_yes_win:.1f}% |
| 做多信号远离支撑 | {sup_no_tot} | {sup_no_val} | {sup_no_win:.1f}% |
| 做空信号靠近15m阻力 | {res_yes_tot} | {res_yes_val} | {res_yes_win:.1f}% |
| 做空信号远离阻力 | {res_no_tot} | {res_no_val} | {res_no_win:.1f}% |

### 三、交易执行分析

#### 3.1 止损止盈统计
| 类型 | 平均距离(USDT) | 触发次数 | 触发后反向概率 | 建议 |
|-----|---------------|---------|---------------|------|
| 止损 | {stop_avg_dist:.1f} | {stop_triggered} | {stop_reversal_rate:.0f}% | {stop_advice} |
| 止盈 | {tp_avg_dist:.1f} | {tp_triggered} | {tp_early_rate:.0f}% | {tp_advice} |

#### 3.2 信号与订单匹配度
| 维度 | 数量 | 占比 | 说明 |
|------|------|------|------|
| 信号未产生订单 | {signal_no_trade} | {(signal_no_trade / (signal_no_trade + match_count) * 100) if (signal_no_trade + match_count) else 0:.1f}% | 可能因风控或机器人未启动 |
| 无信号却产生订单 | {trade_no_signal} | {(trade_no_signal / (trade_no_signal + match_count) * 100) if (trade_no_signal + match_count) else 0:.1f}% | 人工干预或其他策略 |
| 信号与订单方向一致 | {match_count} | {(match_count / (signal_no_trade + match_count) * 100) if (signal_no_trade + match_count) else 0:.1f}% | 正常执行 |

#### 3.3 订单数据（最近1天）
| 时间 | 订单号 | 标的 | 方向 | 开仓价 | 买入价 | 卖出价 | 状态 |
|------|--------|------|------|--------|--------|--------|------|"""
    if order_receipts:
        for row in order_receipts:
            t = row.get('order_time')
            if hasattr(t, 'strftime'):
                time_str = t.strftime('%Y-%m-%d %H:%M:%S')
            else:
                time_str = str(t) if t else 'N/A'
            buy_price = row.get('buy_price')
            sell_price = row.get('sell_price')
            open_str = f"{float(buy_price):.4f}" if buy_price is not None else "N/A"
            buy_str = f"{float(buy_price):.4f}" if buy_price is not None else "N/A"
            sell_str = f"{float(sell_price):.4f}" if sell_price is not None else "N/A"
            side_text = format_order_side_text(row.get('order_side_enum'))
            status_text = format_order_status_text(row.get('trade_order_status'))
            report += f"\n| {time_str} | {row.get('order_sn','N/A')} | {row.get('symbol','N/A')} | {side_text} | {open_str} | {buy_str} | {sell_str} | {status_text} |"
    else:
        report += "\n| - | - | - | - | - | - | - | - |"

    report += f"""

#### 3.4 信号数据（最近1天）
| 时间 | 信号ID | 标的 | 周期 | 方向 | 强度 |
|------|--------|------|------|------|------|"""
    if signal_receipts:
        for row in signal_receipts:
            t = row.get('kline_time')
            if hasattr(t, 'strftime'):
                time_str = t.strftime('%Y-%m-%d %H:%M:%S')
            else:
                time_str = str(t) if t else 'N/A'
            strength = row.get('signal_strength')
            strength_str = f"{float(strength):.4f}" if strength is not None else "N/A"
            direction_text = format_signal_direction_text(row.get('technical_direction'))
            report += f"\n| {time_str} | {row.get('id','N/A')} | {row.get('symbol','N/A')} | {row.get('timeframe','N/A')} | {direction_text} | {strength_str} |"
    else:
        report += "\n| - | - | - | - | - | - |"

    report += f"""

### 四、开仓位置建议（逐笔）
| 时间 | 标的 | 方向 | 信号方向 | 信号强度 | 开仓价 | K线低-高 | 位置分位 | 建议 |
|------|------|------|----------|----------|--------|----------|----------|------|"""
    if entry_advice:
        for row in entry_advice:
            t = row.get('time')
            if hasattr(t, 'strftime'):
                time_str = t.strftime('%Y-%m-%d %H:%M:%S')
            else:
                time_str = str(t) if t else 'N/A'
            strength = row.get('signal_strength')
            strength_str = f"{float(strength):.2f}" if strength is not None else "N/A"
            entry_price = row.get('entry_price')
            entry_str = f"{float(entry_price):.4f}" if entry_price is not None else "N/A"
            low = row.get('low_price')
            high = row.get('high_price')
            kline_range = f"{float(low):.4f}-{float(high):.4f}" if low is not None and high is not None else "N/A"
            pos = row.get('position_pct')
            pos_str = f"{pos:.1f}%" if pos is not None else "N/A"
            side_text = format_entry_side_text(row.get('side'))
            signal_text = format_signal_direction_text(row.get('signal_direction'))
            report += f"\n| {time_str} | {row.get('symbol','N/A')} | {side_text} | {signal_text} | {strength_str} | {entry_str} | {kline_range} | {pos_str} | {row.get('advice','')} |"
    else:
        report += "\n| - | - | - | - | - | - | - | - | - |"

    report += f"""

### 五、过去7天趋势对比
| 日期 | 成交笔数 | 盈亏 (USDT) | 胜率 |
|------|----------|-------------|------|"""
    
    # 添加趋势行
    if trend:
        for t in trend:
            report += f"| {t.get('date', '-')} | {t.get('trades', 0)} | {t.get('pnl', 0):+.2f} | - |\n"
    else:
        # 添加空行或示例数据
        report += "| - | 0 | 0.00 | - |\n"
    
    report += f"""

### 六、最近4小时动态（{recent_start} 至 {end_time}）

#### 信号情况
| 周期 | 信号总数 | 多头 | 空头 | 平均强度 |
|------|----------|------|------|----------|
| 3m | {recent_3m_total} | {recent_3m_bull} | {recent_3m_bear} | {recent_3m_avg_strength:.2f} |
| 15m | {recent_15m_total} | {recent_15m_bull} | {recent_15m_bear} | {recent_15m_avg_strength:.2f} |
| 4h | {recent_4h_total} | {recent_4h_bull} | {recent_4h_bear} | {recent_4h_avg_strength:.2f} |

#### 交易情况
- 成交笔数：{recent_trade_count}
- 盈亏：{recent_total_pnl:+.2f} USDT
- 胜率：{recent_win_rate:.1f}%

**简要分析**：最近4小时信号以{'空头' if recent_3m_bear > recent_3m_bull else '多头'}为主，{'未产生交易' if recent_trade_count == 0 else f'产生{recent_trade_count}笔交易，盈亏{recent_total_pnl:+.2f} USDT'}。

### 七、策略优化建议

{generate_suggestions(data)}

### 八、待办清单

- [ ] 根据上述建议修改策略配置
- [ ] 检查机器人状态并启动停止的实例
- [ ] 执行 `clawbot restart` 使配置生效

---
*报告生成时间：{datetime.now().strftime("%Y-%m-%d %H:%M:%S")}*
"""
    
    return report

def push_report(report: str, date: str):
    """推送报告到外部系统"""
    if OPENCLAW_PUSH_MAIN_ONLY:
        logger.info("已开启主会话推送模式，跳过外部接口推送")
        return
    api = load_api_config()
    payload = {
        "date": date,
        "report": report,
        "type": "daily_replay",
        "timestamp": datetime.now().isoformat()
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api.get('token', '')}"
    }
    try:
        if not api.get('verify', True):
            logger.warning("API SSL 校验已关闭")
        resp = requests.post(api.get('endpoint'), json=payload, headers=headers, timeout=api.get('timeout', 10), verify=api.get('verify', True))
        resp.raise_for_status()
        logger.info(f"报告推送成功，状态码 {resp.status_code}")
    except Exception as e:
        logger.error(f"报告推送失败: {e}")

def main(start_time: str, end_time: str, test_mode: bool = False):
    logger.info(f"开始处理 {start_time} 至 {end_time} 的数据...")
    if test_mode:
        report = build_test_report(start_time, end_time)
        if sys.stdout.encoding != 'UTF-8':
            sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='ignore')
        print(report)
        return
    data = {
        'core': {'trades_count': 0, 'total_pnl': 0.0, 'win_rate': '0%', 'profit_loss_ratio': 0.0, 'max_profit': 0.0, 'max_loss': 0.0, 'avg_hold_time_min': 0.0},
        'signals': [],
        'recent_signals': [],
        'recent_trades': {'trade_count': 0, 'total_pnl': 0.0, 'win_count': 0},
        'order_receipts': [],
        'signal_receipts': [],
        'validity': [],
        'position_stats': {'3m': {'low':0,'mid':0,'high':0,'mismatch':0,'total':0}, '15m': {'low':0,'mid':0,'high':0,'mismatch':0,'total':0}, '4h': {'low':0,'mid':0,'high':0,'mismatch':0,'total':0}},
        'signal_context': {'trend_consistency': {'consistent': {'total':0,'valid':0}, 'inconsistent': {'total':0,'valid':0}}, 'near_support': {'yes': {'total':0,'valid':0}, 'no': {'total':0,'valid':0}}, 'near_resistance': {'yes': {'total':0,'valid':0}, 'no': {'total':0,'valid':0}}},
        'stop_take': {'stop_avg_dist': 0.0, 'stop_triggered': 0, 'stop_reversal_rate': 0.0, 'tp_avg_dist': 0.0, 'tp_triggered': 0, 'tp_early_rate': 0.0},
        'matching': {'signal_no_trade': 0, 'trade_no_signal': 0, 'match_count': 0},
        'entry_advice': [],
        'trend': []
    }
    recent_start = None
    partial_error = False
    try:
        conn = get_connection()
        cursor = conn.cursor()
        end_dt = datetime.strptime(end_time, '%Y-%m-%d %H:%M:%S')
        recent_start = (end_dt - timedelta(hours=4)).strftime('%Y-%m-%d %H:%M:%S')
        recent_day_start = (end_dt - timedelta(days=1)).strftime('%Y-%m-%d %H:%M:%S')

        try:
            data['core'] = fetch_core_metrics(cursor, start_time, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"核心指标查询失败: {e}")

        try:
            data['signals'] = fetch_signal_stats(cursor, start_time, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"信号统计查询失败: {e}")

        try:
            data['recent_signals'] = fetch_recent_signals(cursor, recent_start, end_time)
            data['recent_trades'] = fetch_recent_trades(cursor, recent_start, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"最近4小时统计查询失败: {e}")

        try:
            data['order_receipts'] = fetch_recent_order_receipts(cursor, end_time, 20)
        except Exception as e:
            partial_error = True
            logger.error(f"订单收据查询失败: {e}")

        try:
            data['signal_receipts'] = fetch_recent_signal_receipts(cursor, end_time, 20)
        except Exception as e:
            partial_error = True
            logger.error(f"信号收据查询失败: {e}")

        try:
            data['validity'] = fetch_signal_validity(cursor, start_time, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"信号有效性查询失败: {e}")

        try:
            data['position_stats'] = fetch_signal_position(cursor, start_time, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"信号位置分布查询失败: {e}")

        try:
            data['signal_context'] = analyze_signal_context(cursor, start_time, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"大周期上下文分析失败: {e}")

        try:
            data['stop_take'] = fetch_stop_take_analysis(cursor, recent_day_start, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"止损止盈统计失败: {e}")

        try:
            data['matching'] = fetch_matching(cursor, start_time, end_time)
        except Exception as e:
            partial_error = True
            logger.error(f"信号订单匹配度失败: {e}")

        try:
            data['entry_advice'] = fetch_entry_position_advice(cursor, recent_day_start, end_time, 20)
        except Exception as e:
            partial_error = True
            logger.error(f"开仓位置建议生成失败: {e}")

        try:
            data['trend'] = fetch_trend(cursor, end_time, 7)
        except Exception as e:
            partial_error = True
            logger.error(f"趋势对比查询失败: {e}")

        overall_win = float(data['core'].get('win_rate', '0').rstrip('%')) if data['core'].get('win_rate') else 0
        data['overall_win_rate'] = overall_win
        total_3m = data['position_stats'].get('3m', {}).get('total', 0) or 0
        mismatch_3m = data['position_stats'].get('3m', {}).get('mismatch', 0) or 0
        data['pos_mismatch_rate'] = (mismatch_3m / total_3m * 100) if total_3m else 0.0
        data['stop_reversal_rate'] = data['stop_take'].get('stop_reversal_rate', 0)
        data['tp_early_rate'] = data['stop_take'].get('tp_early_rate', 0)
        sig_no_trade = data['matching'].get('signal_no_trade', 0) or 0
        match_count = data['matching'].get('match_count', 0) or 0
        data['signal_no_trade_rate'] = (sig_no_trade / (sig_no_trade + match_count) * 100) if (sig_no_trade + match_count) else 0.0
        data['source'] = 'PARTIAL' if partial_error else 'DB'

        cursor.close()
        conn.close()
    except Exception as e:
        logger.error(f"数据处理过程中出现异常，启用降级模式: {e}")
        if recent_start is None:
            end_dt = datetime.strptime(end_time, '%Y-%m-%d %H:%M:%S')
            recent_start = (end_dt - timedelta(hours=4)).strftime('%Y-%m-%d %H:%M:%S')
        data['overall_win_rate'] = 0.0
        data['pos_mismatch_rate'] = 0.0
        data['stop_reversal_rate'] = 0.0
        data['tp_early_rate'] = 0.0
        data['signal_no_trade_rate'] = 0.0
        data['source'] = 'DEGRADED'

    report = generate_markdown(data, start_time.split(' ')[0], recent_start, end_time)
    # 处理编码问题
    if sys.stdout.encoding != 'UTF-8':
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='ignore')
    print(report)

    push_report(report, start_time.split(' ')[0])

if __name__ == "__main__":
    test_mode = os.getenv('OPENCLAW_RECAP_TEST') == '1' or '--test' in sys.argv
    argv = [arg for arg in sys.argv[1:] if arg != '--test']
    if len(argv) < 2:
        print("用法: python script.py <开始时间> <结束时间>")
        print("示例: python script.py '2026-03-09 00:00:00' '2026-03-09 23:59:59'")
        sys.exit(1)
    main(argv[0], argv[1], test_mode)
