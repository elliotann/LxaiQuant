量化交易策略完整文档
EMA9/21 小时波段回调策略（限价挂单版）
版本：3.0
最后更新：2025-05-20

1. 策略概述
   项目	内容
   策略名称	HourEMAPullback_Limit_ReverseProtect
   交易周期	方向判断：1小时K线；入场执行：3分钟K线（限价挂单）
   适用品种	连续交易时段、波动适中的期货、股指、外汇（如螺纹钢、甲醇、IF、IC、欧元/美元）
   交易方向	仅顺小时趋势方向（多/空），但增加反转预警过滤
   持仓时间	日内，收盘前平仓
   核心思想	利用小时图EMA9/21金叉死叉及收盘价位置确认趋势，在价格回调至动态均线支撑/阻力时以限价单入场；每小时仅挂单一次，未成交则自动撤销；增加反转趋势识别与退出机制，减少趋势反转时的亏损。
2. 策略逻辑
   2.1 方向判断（基于已收盘的上一根小时K线）
   多头方向 必须同时满足以下三个条件：

均线排列：EMA9 > EMA21

价格位置：收盘价 > EMA21 且 收盘价 > EMA9（即收盘价位于两条均线上方）

（可选）均线距离过滤：(EMA9 - EMA21) / EMA21 > 0.1%（避免极弱金叉）

空头方向 必须同时满足以下三个条件：

均线排列：EMA9 < EMA21

价格位置：收盘价 < EMA21 且 收盘价 < EMA9（即收盘价位于两条均线下方）

（可选）均线距离过滤：(EMA21 - EMA9) / EMA21 > 0.1%

若上述条件不满足（包括收盘价夹在两条均线之间），则认为本小时无明确方向，不进行任何挂单。

收盘价与均线的位置关系是核心过滤，能有效避免“均线已交叉但价格滞后”的假信号。

2.2 趋势反转预警过滤（新增）
在方向判断通过后，增加反转预警检查。若满足以下任一条件，则本小时放弃挂单（视为趋势可能反转或减弱）：

多头趋势中的反转预警：

条件A（长上影线）：上一小时K线实体为阴线（收盘 < 开盘）且 上影线长度 > 实体长度 × 2
（上影线长度 = 最高价 - max(开盘,收盘)）

条件B（价格跌破慢线）：上一小时收盘价 < EMA21（虽然EMA9仍大于EMA21）

条件C（吞没形态）：上一小时K线为阴线，且完全吞没了前一根小时K线的实体（阴线实体 > 前一根K线实体且最高价 ≥ 前一根最高价，最低价 ≤ 前一根最低价）

空头趋势中的反转预警：

条件A（长下影线）：上一小时K线实体为阳线（收盘 > 开盘）且 下影线长度 > 实体长度 × 2
（下影线长度 = min(开盘,收盘) - 最低价）

条件B（价格突破慢线）：上一小时收盘价 > EMA21（虽然EMA9仍小于EMA21）

条件C（吞没形态）：上一小时K线为阳线，且完全吞没了前一根小时K线的实体

这些条件仅作为挂单前的过滤器，不会改变已有持仓的管理（持仓管理另有反转退出规则）。

2.3 回调参考位（动态选择）
根据趋势强度（均线距离百分比 diff_pct）选择不同的回调参考线，用于挂单价位：

强趋势：diff_pct > 0.3%（多头）或绝对值 > 0.3%（空头）
→ 回调参考位 = EMA9（更贴近价格，容易成交）

中等趋势：0.1% ≤ diff_pct ≤ 0.3%
→ 回调参考位 = EMA21（标准回调支撑/阻力）

弱趋势：diff_pct < 0.1% → 不交易（方向判断已过滤，但保留此参数）

计算公式（以多头为例）：

text
diff_pct = (EMA9 - EMA21) / EMA21 * 100
2.4 入场方式（限价挂单，每小时一次）
在每个小时开始时刻（例如 09:00:00、10:00:00 …），基于上一根已收盘的小时K线计算结果：

判断方向 → 若为多头或空头，则执行反转预警过滤 → 通过则继续

计算回调参考位 ref_price

计算合理的止损距离（基于小时图ATR）与潜在盈亏比

若盈亏比 ≥ min_risk_reward（默认1.5），则挂出限价单：

做多：挂买入限价单，价格 = ref_price

做空：挂卖出限价单，价格 = ref_price

订单有效期为当前小时（例如 09:00 挂单，有效期至 09:59:59）

若该小时结束时订单未成交 → 自动撤销；下一小时重新评估并可能挂新单

若订单成交 → 立即取消当前小时所有剩余挂单，进入持仓管理

每小时最多开一单（包括挂单尝试），成交后不再重复挂单。

2.5 止损与止盈
初始止损
做多：

紧止损：成交K线的最低价 - 1 tick

宽止损：当前小时EMA21 - 1 × ATR(14)（小时图）

推荐：使用紧止损，当浮盈达到 0.5 × ATR 后将止损移动至保本或EMA21。

做空：对称设置（成交K线最高价 + 1 tick，或 EMA21 + 1×ATR）

止盈目标
固定盈亏比：止损点数 × 1.5 ~ 2（推荐2）

或技术止盈：前一个显著波段高点/低点（基于小时图或3分钟图）

移动止损（可选）
当价格向有利方向移动超过 1 × ATR 后，将止损移动到 入场价 + 0.5 × ATR（多单）或 入场价 - 0.5 × ATR（空单），锁定部分利润。

2.6 持仓管理与平仓规则
事件	动作
触及止损/止盈	立即市价平仓
新小时开始，且新小时方向与持仓方向相反	立即平仓（避免逆势）
持仓期间出现反转信号（新增）：
- 多单持仓时，出现3分钟K线收盘价跌破上一根（已收盘）小时K线的最低点，且该最低点低于EMA21
- 多单持仓时，出现3分钟K线收盘价低于前一根小时K线的低点
- 空单持仓对称（3分钟K线收盘价突破上一根小时K线的最高点且该最高点高于EMA21；或3分钟收盘价高于前一根小时高点）	立即市价平仓（不等新小时收盘）
  距离收盘 ≤ close_exit_minutes（默认5分钟）	强制市价平仓，不持仓过夜
  盘中最大亏损达到日限（总资金2%）	停止当日所有交易
3. 参数列表
   参数名	默认值	说明
   fast_ema	9	小时图快线周期
   slow_ema	21	小时图慢线周期
   trend_weak_threshold	0.1%	均线距离低于此值 → 不交易
   trend_strong_threshold	0.3%	均线距离高于此值 → 强趋势，使用EMA9作为回调参考位
   atr_period	14	计算ATR的周期（小时图）
   risk_per_trade	0.5%	单笔最大亏损占总资金比例
   min_risk_reward	1.5	最小盈亏比，低于此值不挂单
   close_exit_minutes	5	收盘前多少分钟强制平仓
   max_daily_loss	2%	当日累计最大亏损比例，触发则停止交易
   reverse_warning_enabled	True	是否启用反转预警过滤（2.2节）
   reverse_exit_enabled	True	是否启用持仓期间反转退出（2.6节）
4. 执行流程（逐小时）
   pseudo
   对于每个小时开始时刻 T（如09:00）：
1. 获取上一完整小时K线数据（时间 T-1h 至 T）
2. 计算该小时的 EMA9、EMA21、收盘价 close
3. 判断多头条件：
   if (EMA9 > EMA21) and (close > EMA21) and (close > EMA9):
   direction = "long"
   diff = (EMA9 - EMA21)/EMA21 * 100
   else if (EMA9 < EMA21) and (close < EMA21) and (close < EMA9):
   direction = "short"
   diff = (EMA21 - EMA9)/EMA21 * 100
   else:
   direction = None
4. 若 direction 为 None 或 diff < trend_weak_threshold → 本小时不挂单，结束
5. 【反转预警过滤】若 reverse_warning_enabled = True：
   if direction == "long" and ( (close < ema21) or (阴线且长上影线) or (吞没形态) ):
   direction = None  # 放弃本小时
   if direction == "short" and ( (close > ema21) or (阳线且长下影线) or (吞没形态) ):
   direction = None
6. 若 direction 为 None → 本小时不挂单，结束
7. 确定回调参考位 ref_price：
   if diff >= trend_strong_threshold:
   ref_price = EMA9
   else:
   ref_price = EMA21
8. 根据小时图ATR估算止损距离，计算预期盈亏比，若 < min_risk_reward → 不挂单
9. 挂限价单（价格 = ref_price），有效期至 T+1h - 1秒
10. 等待成交或到期：
    - 若在有效期内成交：
        - 记录成交价、成交时间、初始止损价
        - 取消本小时所有剩余挂单
        - 进入持仓监控（每3分钟检查止损止盈、反转退出信号、新小时方向、收盘时间）
    - 若到期未成交：
        - 自动撤单，不做任何处理
5. 资金管理
   单笔风险控制：

text
手数 = floor( (总资金 × risk_per_trade) / (止损点数 × 每点价值) )
止损点数 = |入场价 - 初始止损价|（单位与价格一致）

最大持仓：同时只持有一笔订单（因为每小时只挂单一次，成交后不再开新仓）

日内亏损熔断：当日累计亏损（含手续费）超过 max_daily_loss 时，停止当日所有新交易，已持仓仅平仓不新开。

6. 回测注意事项
   6.1 数据要求
   至少6个月的1小时K线数据和3分钟K线数据（时间戳对齐）

对于期货，需定义交易时段（日盘、夜盘），避免使用自然小时导致跨时段合并。建议按交易时段重置小时计数（例如 09:00-10:00, 10:00-11:00, 13:30-14:30 …）。

6.2 避免未来函数
方向判断必须使用 上一根已经收盘 的小时K线数据（在回测中需用 .shift(1)）。

挂单价位 ref_price 不能使用当前小时的价格信息。

反转预警过滤仅使用已收盘的上一小时数据，无未来。

6.3 成交模拟
做多限价单成交条件：当前小时内的最低价 ≤ 挂单价
成交价格 = 挂单价（假设流动性充分）

做空限价单成交条件：当前小时内的最高价 ≥ 挂单价
成交价格 = 挂单价

若价格刚好等于挂单价，视为成交。

未成交模拟：若小时最低价 > 挂单价（做多）或小时最高价 < 挂单价（做空），则该小时无交易。

6.4 滑点与手续费
每笔交易（开平各一次）额外增加 1 tick 滑点（例如期货1跳）。

手续费按实际品种标准收取（如万分之0.5，或固定每手X元）。

6.5 绩效评估指标
年化收益率、夏普比率、最大回撤（%及金额）、胜率、平均盈亏比、总交易次数、日均交易次数。

7. 策略优势与风险
   优势
   双重趋势确认：均线排列 + 收盘价位置，有效过滤假信号。

反转预警：在趋势可能转向时暂停挂单，避免逆势开仓。

动态回调位：强趋势用EMA9提高成交率，中等趋势用EMA21提高盈亏比。

每小时重挂：避免过时订单，自适应市场变化。

限价单入场：获得更好的入场价格，利于盈亏比。

持仓反转退出：盘中及时止损，减少反转带来的大幅回撤。

风险及应对措施
风险	应对措施
震荡市中反复小亏	1. 收盘价位置过滤 2. 弱趋势不交易 3. 单日亏损熔断
趋势反转导致逆势开仓	反转预警过滤（挂单前） + 持仓反转退出（盘中）
强趋势中挂单不成交（踏空）	已采用EMA9提高成交率；可额外增加“突破追单”作为备用
滑点过大影响盈亏比	选择流动性好的品种；回测时预留充足滑点；实盘使用限价单
小时方向跳变导致持仓被反打	新小时方向相反时立即平仓 + 盘中反转退出，双重保护
夜盘跳空导致挂单价偏离	开盘后第一小时可暂停挂单，或增加跳空幅度过滤
8. 代码实现框架（Python + Pandas）
   python
   import pandas as pd
   import numpy as np

def run_backtest(df_hour, df_3min, params):
"""
df_hour: 1小时K线数据，列: time, open, high, low, close
df_3min: 3分钟K线数据，列: time, open, high, low, close
params: 参数字典
"""
# 计算小时图的EMA和ATR
df_hour['ema9'] = df_hour['close'].ewm(span=params['fast_ema'], adjust=False).mean()
df_hour['ema21'] = df_hour['close'].ewm(span=params['slow_ema'], adjust=False).mean()
df_hour['atr'] = talib.ATR(df_hour['high'], df_hour['low'], df_hour['close'], timeperiod=params['atr_period'])

    trades = []
    current_position = None   # {'dir': 'long'/'short', 'entry_price', 'stop_loss', 'qty', 'entry_time'}
    
    # 获取所有小时开始时间
    hour_starts = df_hour['time'].values
    
    for i, hour_start in enumerate(hour_starts[:-1]):  # 最后一个小时无法用于下一小时判断
        # --- 1. 获取上一小时数据 ---
        prev_hour = df_hour.iloc[i]
        prev2_hour = df_hour.iloc[i-1] if i>0 else None
        ema9 = prev_hour['ema9']
        ema21 = prev_hour['ema21']
        close = prev_hour['close']
        open_ = prev_hour['open']
        high = prev_hour['high']
        low = prev_hour['low']
        
        # --- 2. 方向判断 ---
        direction = None
        diff = 0
        if (ema9 > ema21) and (close > ema21) and (close > ema9):
            direction = 'long'
            diff = (ema9 - ema21) / ema21 * 100
        elif (ema9 < ema21) and (close < ema21) and (close < ema9):
            direction = 'short'
            diff = (ema21 - ema9) / ema21 * 100
        
        if direction is None or diff < params['trend_weak_threshold']:
            continue
        
        # --- 3. 反转预警过滤（挂单前）---
        if params['reverse_warning_enabled']:
            if direction == 'long':
                # 条件B: 收盘价跌破EMA21
                if close < ema21:
                    continue
                # 条件A: 阴线且长上影线
                if close < open_:
                    upper_shadow = high - max(open_, close)
                    body = abs(close - open_)
                    if upper_shadow > 2 * body:
                        continue
                # 条件C: 吞没形态（需要上一根K线）
                if prev2_hour is not None:
                    if (close < open_) and (open_ > prev2_hour['close']) and (close < prev2_hour['close']) \
                       and (high >= prev2_hour['high']) and (low <= prev2_hour['low']):
                        continue
            else:  # short
                if close > ema21:
                    continue
                if close > open_:
                    lower_shadow = min(open_, close) - low
                    body = abs(close - open_)
                    if lower_shadow > 2 * body:
                        continue
                if prev2_hour is not None:
                    if (close > open_) and (open_ < prev2_hour['close']) and (close > prev2_hour['close']) \
                       and (high >= prev2_hour['high']) and (low <= prev2_hour['low']):
                        continue
        
        # --- 4. 动态回调参考位 ---
        if diff >= params['trend_strong_threshold']:
            ref_price = ema9
        else:
            ref_price = ema21
        
        # --- 5. 挂单与成交模拟 ---
        hour_3min = df_3min[(df_3min['time'] >= hour_start) & (df_3min['time'] < hour_start + pd.Timedelta(hours=1))]
        if hour_3min.empty:
            continue
        
        filled = False
        fill_time = None
        fill_price = None
        stop_loss = None
        
        if direction == 'long':
            # 成交条件：最低价 <= ref_price
            mask = hour_3min['low'] <= ref_price
            if mask.any():
                filled = True
                idx = mask.idxmax()  # 第一个满足条件的索引
                fill_time = hour_3min.loc[idx, 'time']
                fill_price = ref_price
                stop_loss = hour_3min.loc[idx, 'low'] - params['tick_size']
        else:  # short
            mask = hour_3min['high'] >= ref_price
            if mask.any():
                filled = True
                idx = mask.idxmax()
                fill_time = hour_3min.loc[idx, 'time']
                fill_price = ref_price
                stop_loss = hour_3min.loc[idx, 'high'] + params['tick_size']
        
        if not filled:
            continue
        
        # 盈亏比过滤（简化：用固定止盈目标，如2倍止损）
        risk_points = abs(fill_price - stop_loss)
        if risk_points <= 0:
            continue
        take_profit_price = fill_price + 2 * risk_points if direction == 'long' else fill_price - 2 * risk_points
        # 简单检查止盈是否合理（例如不超过当前小时高点等，略）
        
        # 计算仓位
        qty = int((params['capital'] * params['risk_per_trade']) / (risk_points * params['point_value']))
        if qty == 0:
            continue
        
        # --- 6. 持仓管理（逐根3分钟K线模拟）---
        # 找到成交后的3分钟数据
        post_data = hour_3min.loc[idx:]  # 从成交K线开始（包括该K线）
        # 实际入场价格是填好的成交价，但止损/止盈检查应从下一根K线开始。
        # 简化：从成交K线的下一根开始检查
        for j in range(1, len(post_data)):
            bar = post_data.iloc[j]
            # 检查止损止盈
            if direction == 'long':
                if bar['low'] <= stop_loss:
                    exit_price = stop_loss
                    exit_time = bar['time']
                    break
                if bar['high'] >= take_profit_price:
                    exit_price = take_profit_price
                    exit_time = bar['time']
                    break
            else:
                if bar['high'] >= stop_loss:
                    exit_price = stop_loss
                    exit_time = bar['time']
                    break
                if bar['low'] <= take_profit_price:
                    exit_price = take_profit_price
                    exit_time = bar['time']
                    break
            
            # 盘中反转退出（如果启用）
            if params['reverse_exit_enabled']:
                if direction == 'long':
                    # 条件1: 3分钟收盘价跌破上一小时K线低点 且 该低点低于EMA21
                    if bar['close'] < prev_hour['low'] and prev_hour['low'] < ema21:
                        exit_price = bar['close']
                        exit_time = bar['time']
                        break
                    # 条件2: 3分钟收盘价低于前一根小时K线的低点
                    if prev2_hour is not None and bar['close'] < prev2_hour['low']:
                        exit_price = bar['close']
                        exit_time = bar['time']
                        break
                else: # short
                    # 条件1: 3分钟收盘价突破上一小时K线高点 且 该高点高于EMA21
                    if bar['close'] > prev_hour['high'] and prev_hour['high'] > ema21:
                        exit_price = bar['close']
                        exit_time = bar['time']
                        break
                    # 条件2: 3分钟收盘价高于前一根小时K线的高点
                    if prev2_hour is not None and bar['close'] > prev2_hour['high']:
                        exit_price = bar['close']
                        exit_time = bar['time']
                        break
        
        # 记录交易
        trades.append({
            'entry_time': fill_time,
            'exit_time': exit_time,
            'direction': direction,
            'entry_price': fill_price,
            'exit_price': exit_price,
            'stop_loss': stop_loss,
            'qty': qty,
            'pnl': (exit_price - fill_price) * qty * params['point_value'] * (1 if direction=='long' else -1)
        })
    
    return trades
上述代码为简化框架，实际回测需完善收盘强制平仓、新小时方向反转平仓、滑点手续费等。

9. 实盘部署建议
   先用模拟盘测试至少1个月，观察成交率、滑点、反转预警的实际效果。

参数优化：对 trend_weak_threshold、trend_strong_threshold、min_risk_reward 进行稳定性检验，避免过拟合。特别注意反转预警的参数（影线倍数、吞没识别）可能需要根据品种波动率调整。

多品种组合：可将策略同时运行在多个不相关品种上，降低账户回撤。

服务器与网络：确保低延迟，限价单需要及时撤单重挂（小时切换时刻）。

日志与报警：记录每个小时的挂单、成交、撤单、平仓操作；异常情况（如连续N小时无成交）发送报警。

反转预警的实盘注意事项：由于盘中反转退出信号基于实时数据，需确保数据源稳定，避免因短暂毛刺误触发。建议增加确认（例如连续两笔3分钟K线满足条件）。

10. 版本记录
    版本	日期	主要变更
    1.0	2025-05-01	初版：EMA9/21排列 + 固定EMA21回调 + 每小时挂单
    1.1	2025-05-10	增加收盘价位置过滤（价格必须在均线同侧）
    2.0	2025-05-20	增加动态回调参考位（根据趋势强度选择EMA9或EMA21）
    3.0	2025-05-20	增加反转趋势处理：挂单前反转预警过滤 + 持仓期间反转退出机制
    4.0	2025-05-20	新增第11节：系统实现方案（ai-quant 集成架构）

11. 系统实现方案（ai-quant 集成）
    11.1 架构归属决策
    本策略归属于 indicator_driven 体系（非 signal-based），原因：
    - 入场/出场完全依赖 EMA、ATR、K线形态等实时计算的指标值，而非预存信号
    - 与 IndicatorDrivenStrategy 同属 "策略指标驱动型" 家族
    - 底层基础设施（K线获取、订单服务、持仓管理、风控）完全复用

    工程上采用 新增 beanName 分支 的方式，与 indicator_driven、multi_direction 并列：
    ```
    DefaultDealStrategyTrade.buildStrategy() {
        if ("indicator_driven".equalsIgnoreCase(beanName)) {
            // ... 现有逻辑
        }
        if ("dual_ema".equalsIgnoreCase(beanName)) {   // 新增分支
            DualEmaStrategy s = new DualEmaStrategy(series, params, ...);
            return s;
        }
    }
    ```
    策略 BeanName 设置为 "dual_ema"，在策略管理后台创建策略时选择该类型。

    11.2 类层次设计
    包路径：com.chain.ai.trade.engine.strategy.core.rule（与 IndicatorDrivenStrategy 同级）

    类名	职责	关键方法
    DualEmaStrategy	策略主类，实现 TradingStrategy 接口	buildEntryRule(), buildExitRule(), shouldEnter(), shouldExit()
    DualEmaDirectionChecker	方向判断 + 反转预警过滤	isDirectionValid(bar), isReversing(bar, prevBar)
    DualEmaPullbackRule	3分钟K线回调入场检测	isSatisfied(index, tradingRecord)

    11.2.1 DualEmaStrategy 核心设计
    ```
    // 伪代码示意
    public class DualEmaStrategy implements TradingStrategy {
        private final BarSeries series3min;       // 3分钟K线（系统主周期）
        private final BarSeries series1hour;      // 由3分钟聚合而来的1小时K线

        // 小时EMA计算结果缓存
        private EmaResult currentHourDirection;   // 当前小时的方向判断结果

        // 限价单状态
        private LimitOrderState limitOrderState;  // NONE / PENDING / FILLED

        @Override
        public boolean shouldEnter(int index, TradingRecord record) {
            // 1. 检查是否已进入新小时
            // 2. 若是新小时 → 合成上一小时K线 → 计算方向
            // 3. 若通过反转预警过滤 → 记录 refPrice → 触发限价单
            // 4. 若非新小时 → 检查限价单是否已成交
        }

        @Override
        public boolean shouldExit(int index, TradingRecord record) {
            // 1. 检查固定止损止盈（可复用现有 StopLossRule / StopGainRule）
            // 2. 检查持仓间反转退出信号（跌破上根小时K线低点 / 3分钟收盘价破前低）
            // 3. 检查新小时方向反转
            // 4. 检查收盘前强制平仓
        }
    }
    ```

    11.3 多周期处理方案
    系统主周期为3分钟K线（由 KlineDataProvider 提供），小时K线由策略内部合成。

    11.3.1 小时K线合成
    在 DualEmaStrategy 构造时，将已加载的3分钟K线按自然小时（或交易时段）聚合：
    ```
    // 在 buildStrategy() 中或 DualEmaStrategy 构造时执行
    List<Bar> hourBars = aggregateToHourly(series3min);
    BarSeries series1hour = new BaseBarSeriesBuilder().withBars(hourBars).build();
    ```

    聚合规则：
    - open = 该小时第1根3分钟K线的open
    - high = 该小时所有3分钟K线的max(high)
    - low = 该小时所有3分钟K线的min(low)
    - close = 该小时最后1根3分钟K线的close
    - volume = 该小时所有3分钟K线的sum(volume)

    处理交易时段边界（如夜盘23:00-01:00）时，若一个自然小时跨越休盘时段，该小时不纳入计算。

    11.3.2 EMA计算
    在合成好的 hourBarSeries 上创建标准 EMAIndicator：
    ```
    ClosePriceIndicator hourClose = new ClosePriceIndicator(series1hour);
    EMAIndicator ema9 = new EMAIndicator(hourClose, 9);
    EMAIndicator ema21 = new EMAIndicator(hourClose, 21);
    ```

    ATR(14) 也在小时BarSeries上计算：HourATRIndicator(series1hour, 14)。

    注意：小时K线每次新增时（约每小时20根3分钟K线后），需要增量追加到 series1hour 中并更新EMA/ATR。

    11.4 入场执行流程（以3分钟K线驱动）
    每根新3分钟K线到达时，execStrategy 主循环按以下步骤处理：

    步骤	触发条件	动作
    1. 新小时检查	当前3分钟K线是小时的第一根	合成上一完整小时K线，计算 direction、refPrice、检查反转预警
    2. 限价单挂出	步骤1通过，且本小时尚未挂单	调用 createOrder(orderType=LIMIT, limitPrice=refPrice, timeInForce=GOOD_TILL_HOUR_END)
    3. 限价单成交检测	后续3分钟K线	查询订单状态（TradeOrderServiceAdapter），若变为"已成交"进入持仓管理
    4. 限价单超时撤销	小时结束仍未成交	调用撤单接口，重置状态
    5. 每小时限一单	整个小时周期	limitOrderState 控制，防止重复挂单

    11.4.1 限价单处理
    在 DefaultDealStrategyTrade.createOrder 中已支持 LIMIT 类型：
    ```
    TradingStrategyParams params = TradingStrategyParams.builder()
        .orderType("LIMIT")
        .limitPrice(refPrice)
        .timeInForce("GTC")    // 或自定义 GOOD_TILL_HOUR_END
        .build();
    createOrder(params);
    ```

    新增 timeInForce=GOOD_TILL_HOUR_END 枚举值（或由策略负责在小时结束时主动撤单）。

    11.5 出场规则集成
    DualEmaStrategy 的出场逻辑支持三层：

    层级	规则类型	实现方式
    第一层	固定止损/止盈	复用现有 DirectionalStopLossRule / DirectionalStopGainRule（通过 ExitRulesConfig 配置）
    第二层	持仓间反转退出	新规则 DualEmaReversalExitRule：检查3分钟收盘价跌破上一小时K线低点/突破高点
    第三层	新小时方向反转	在 shouldExit 中新增逻辑：检查最新合成的小时K线方向是否与持仓相反
    收盘平仓	距离收盘 ≤ closeExitMinutes	复用现有逻辑

    出场规则的组合统一通过 OrDirectionalRule 集成：
    ```
    List<DirectionalRule> exitRules = new ArrayList<>();
    exitRules.add(stopLossLong);        // 固定止损
    exitRules.add(stopGainLong);        // 固定止盈
    exitRules.add(reversalExitLong);    // 反转退出
    DirectionalRule combinedExit = new OrDirectionalRule(exitRules.toArray(...));
    ```

    11.6 策略参数配置
    所有参数通过现有 StrategyParameter 系统配置，分组组织：

    参数组	参数名	类型	默认值	说明
    ema_params	fastEmaPeriod	int	9	快线周期（小时K线）
    ema_params	slowEmaPeriod	int	21	慢线周期（小时K线）
    ema_params	trendWeakThreshold	double	0.001	均线距离低于此值不交易（0.1%）
    ema_params	trendStrongThreshold	double	0.003	均线距离高于此值使用EMA9作为回调位（0.3%）
    risk_params	atrPeriod	int	14	ATR计算周期（小时K线）
    risk_params	riskPerTrade	double	0.005	单笔风险占资金比例（0.5%）
    risk_params	minRiskReward	double	1.5	最小盈亏比
    risk_params	closeExitMinutes	int	5	收盘前平仓分钟数
    risk_params	maxDailyLoss	double	0.02	当日最大亏损比例（2%）
    entry_filters	reverseWarningEnabled	boolean	true	是否启用反转预警过滤
    exit_rules	reverseExitEnabled	boolean	true	是否启用持仓间反转退出
    exit_rules	takeProfitRatio	double	2.0	止盈倍数（相对于止损）

    参数通过 IStrategyParameterService 按 strategyId + 参数组加载，在 DualEmaStrategy 构造时一次性读取。

    11.7 回测方案
    11.7.1 使用 ta4j BarSeriesManager
    DualEmaStrategy 实现了 ta4j Strategy 接口，可直接使用 BarSeriesManager.run() 进行回测：
    ```
    BarSeries series3min = load3minData(...);
    DualEmaStrategy strategy = new DualEmaStrategy(series3min, params);
    BarSeriesManager manager = new BarSeriesManager(series3min);
    TradingRecord record = manager.run(strategy);
    ```

    回测中的限价单成交模拟：
    - 在 shouldEnter 被调用时，如果当前是 PENDING 状态，检查该3分钟K线的 low <= limitPrice（多单）或 high >= limitPrice（空单）
    - 若满足成交条件，记录成交价 = limitPrice，成交index = 当前K线index
    - 可使用 ta4j 的 StopLimitExecutionModel 或自定义填充 ExecutionModel

    11.7.2 回测注意事项
    - 小时方向判断必须使用已收盘的上一小时K线（shift(1)），无未来函数
    - 反转退出信号中"跌破上一小时K线低点"用的是已收盘的上根小时K线的 low 值，无需实时模拟
    - 限价单成交模拟中的"小时最低价"在回测中可直接从3分钟K线获取

    11.8 实盘执行
    实盘通过 LiveTradingServiceImpl 启动，与现有策略完全一致的流程：
    ```
    POST /api/live-trading/start
    {
        "strategyName": "myDualEmaBot",
        "robotId": "bot_001",
        "parameters": {
            "strategyType": "dual_ema",
            "symbol": "BTCUSDT",
            "interval": "3m",
            "accountId": "acc_001"
        }
    }
    ```

    DualEmaStrategy 在 execStrategy 主循环中按3分钟K线驱动，每根新K线到达时：
    1. 检查是否进入新小时 → 更新方向判断
    2. 检查是否需挂出/撤销限价单
    3. 检查持仓出场条件
    4. 处理订单成交事件

    11.9 后续扩展考虑
    - 突破追单模式：在强趋势中若限价单未成交，可在小时中后期追加市价单
    - 动态ATR止损：随行情波动自适应调整止损宽度
    - 多周期确认：增加15分钟MACD作为趋势过滤
    - 参数在线优化：根据近期绩效动态调整 trendStrongThreshold 等参数

    文档结束
    本策略已全面覆盖您提出的所有需求：方向判断（均线排列+收盘价位置）、动态回调参考位、每小时限价挂单（未成交撤单重挂）、每小时最多一单、以及反转趋势的识别与处理。第11节详细说明了在 ai-quant 系统中的集成方案，后续按此方案进入开发阶段。