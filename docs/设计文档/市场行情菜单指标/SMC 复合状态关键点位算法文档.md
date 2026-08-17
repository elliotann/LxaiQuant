核心逻辑修正说明（重要）
针对 STRONG_BULLISH_WARNING_4H 与 STRONG_BEARISH_WARNING_4H 的特殊规则：

在 4H 内部趋势已经反向（出现内部 CHoCH）的状态下，若仍逆势开单，属于高风险操作。此时：

止盈1（主目标）：必须使用 15M 的强低/高点（最近结构），实现“快进快出”。

止盈2（次目标）：禁止使用 4H 的弱低/弱高作为默认止盈。必须使用 “最近需求/供给区上沿” （即距离当前价格最近的、同方向的订单块边界），确保剩余仓位在遇到第一道阻力/支撑时立即离场，避免被反弹吞噬利润。

风控纪律：到达止盈1后，止损必须移动至成本价（保本）。

一、数据字段别名表
代码别名	数据来源
TrailingLow	result.getTrailingLow()
TrailingHigh	result.getTrailingHigh()
LastSwingLow	result.getLastSwingLow()
LastSwingHigh	result.getLastSwingHigh()
NearestBullishOB_Low	result.internalOrderBlocks 中 bias=1 的 barLow（取距离当前价格最近的一个）
NearestBearishOB_High	result.internalOrderBlocks 中 bias=-1 的 barHigh（取距离当前价格最近的一个）
NearestDemandZoneTop	（新增） 从 15M、1H、4H 的 internalOrderBlocks 中，筛选 bias=1（看涨）且 barHigh < EntryPrice 的所有订单块，取 barHigh 最大值（即距离当前价格最近的阻力上沿）。
NearestSupplyZoneBottom	（新增） 从 15M、1H、4H 的 internalOrderBlocks 中，筛选 bias=-1（看跌）且 barLow > EntryPrice 的所有订单块，取 barLow 最小值（即距离当前价格最近的支撑下沿）。
EntryPrice	已开仓价格，固定值
*_15M	来自 result15m
*_1H	来自 result1h
*_4H	来自 result4h
二、正常开单算法（21 种复合状态）
上升趋势类（宏观：强上升 / 上升回调 / 上升末端）
1. STRONG_BULLISH_HEALTHY（强上升·健康）
   方向	SL	TP1	TP2
   BUY	15M_TrailingLow × 0.998	1H_LastSwingHigh	4H_LastSwingHigh 或 NearestBearishOB_Low（取较近者）
   SELL	❌ 禁止开单（强开见第 3 节）	—	—
2. STRONG_BULLISH_SHALLOW_PULLBACK（强上升·浅回调）
   方向	SL	TP1	TP2
   BUY	15M_NearestBullishOB_Low × 0.998	15M_LastSwingHigh	1H_LastSwingHigh
   SELL	❌ 禁止开单（强开见第 3 节）	—	—
3. STRONG_BULLISH_WARNING_1H（强上升·预警回调（1H））
   方向	SL	TP1	TP2
   BUY	1H_NearestBullishOB_Low × 0.998	15M_LastSwingHigh	1H_LastSwingHigh
   SELL	❌ 禁止开单（强开见第 3 节）	—	—
4. STRONG_BULLISH_WARNING_4H（强上升·预警回调（4H内部））⚠️ 高风险逆势
   方向	SL	TP1	TP2
   BUY	15M_NearestBullishOB_Low × 0.995（15M入场级别紧止损）	15M_StrongHigh（主目标，快进快出）	NearestSupplyZoneBottom（次目标，最近供给区下沿）
   SELL	❌ 禁止开单（强开见第 3 节）	—	—
   ⚠️ 禁止使用 4H 弱高/弱低作为止盈锚点
5. STRONG_BULLISH_CONFIRMED_PULLBACK（强上升·确认回调）
   ⚠️ 禁止新开多单。返回 null。强开见第 3 节。

6. BULLISH_PULLBACK_ONGOING（上升回调·进行中）
   ⚠️ 不开新单。返回 null。强开见第 3 节。

7. BULLISH_PULLBACK_BOTTOMING（上升回调·筑底）
   方向	SL	TP1	TP2
   BUY	1H_NearestBullishOB_Low × 0.999（紧止损）	15M_LastSwingHigh	1H_LastSwingHigh
   SELL	❌ 禁止开单（强开见第 3 节）	—	—
8. BULLISH_PULLBACK_FAILURE（上升回调·失败）
   ⚠️ 不开新单。返回 null。强开见第 3 节。

9. BULLISH_ENDING_CONTINUE_DOWN（上升末端·延续下跌）
   方向	SL	TP1	TP2
   SELL	EntryPrice × 1.002（紧贴入场）	15M_LastSwingLow	1H_LastSwingLow
   BUY	❌ 禁止开单（强开见第 3 节）	—	—
10. BULLISH_ENDING_CONFIRM（上升末端·转势确认）
    方向	SL	TP1	TP2
    BUY	1H_NearestBullishOB_Low × 0.998	1H_LastSwingHigh	4H_LastSwingHigh（谨慎）
    SELL	❌ 禁止开单（强开见第 3 节）	—	—
    下降趋势类（宏观：强下降 / 下降反弹 / 下降末端）
11. STRONG_BEARISH_HEALTHY（强下降·健康）
    方向	SL	TP1	TP2
    SELL	15M_TrailingHigh × 1.002	1H_LastSwingLow	4H_LastSwingLow 或 NearestBullishOB_High（取较近者）
    BUY	❌ 禁止开单（强开见第 3 节）	—	—
12. STRONG_BEARISH_SHALLOW_BOUNCE（强下降·浅反弹）
    方向	SL	TP1	TP2
    SELL	15M_NearestBearishOB_High × 1.002	15M_LastSwingLow	1H_LastSwingLow
    BUY	❌ 禁止开单（强开见第 3 节）	—	—
13. STRONG_BEARISH_WARNING_1H（强下降·预警反弹（1H））
    方向	SL	TP1	TP2
    SELL	1H_NearestBearishOB_High × 1.002	15M_LastSwingLow	1H_LastSwingLow
    BUY	❌ 禁止开单（强开见第 3 节）	—	—
14. STRONG_BEARISH_WARNING_4H（强下降·预警反弹（4H内部））⚠️ 高风险逆势（重点修正）
    方向	SL	TP1	TP2
    SELL	15M_NearestBearishOB_High × 1.005（15M入场级别紧止损）	15M_StrongLow（主目标，快进快出，平仓 60%~70%）	NearestDemandZoneTop（次目标，最近需求区上沿，剩余仓位离场）
    BUY	❌ 禁止开单（强开见第 3 节）	—	—
    ⚠️ 禁止使用 4H 弱低作为止盈锚点
15. STRONG_BEARISH_CONFIRMED_BOUNCE（强下降·确认反弹）
    ⚠️ 禁止新开空单。返回 null。强开见第 3 节。

16. BEARISH_PULLBACK_ONGOING（下降反弹·进行中）
    ⚠️ 不开新单。返回 null。强开见第 3 节。

17. BEARISH_PULLBACK_TOPPING（下降反弹·筑顶）
    方向	SL	TP1	TP2
    SELL	1H_NearestBearishOB_High × 1.001（紧止损）	15M_LastSwingLow	1H_LastSwingLow
    BUY	❌ 禁止开单（强开见第 3 节）	—	—
18. BEARISH_PULLBACK_FAILURE（下降反弹·失败）
    ⚠️ 不开新单。返回 null。强开见第 3 节。

19. BEARISH_ENDING_CONTINUE_UP（下降末端·延续反弹）
    方向	SL	TP1	TP2
    BUY	EntryPrice × 0.998（紧贴入场）	15M_LastSwingHigh	1H_LastSwingHigh
    SELL	❌ 禁止开单（强开见第 3 节）	—	—
20. BEARISH_ENDING_CONFIRM（下降末端·转势确认）
    方向	SL	TP1	TP2
    SELL	1H_NearestBearishOB_High × 1.002	1H_LastSwingLow	4H_LastSwingLow（谨慎）
    BUY	❌ 禁止开单（强开见第 3 节）	—	—
    震荡类（宏观：完全震荡）
21. RANGING_NO_DIRECTION / RANGING_RANGE_BOUND（震荡·无方向 / 区间盘整）
    ⚠️ 不开趋势单。返回 null。强开见第 3 节。

三、强制开单保底算法（所有禁止状态通用）
当系统在禁止状态仍强制开单时，采用以下紧止损、紧止盈的保底逻辑：

方向	止损（SL）	止盈1（TP1）	止盈2（TP2）
BUY（做多）	EntryPrice × 0.9985（-0.15%）	EntryPrice × 1.003（+0.3%）	EntryPrice × 1.005（+0.5%）
SELL（做空）	EntryPrice × 1.0015（+0.15%）	EntryPrice × 0.997（-0.3%）	EntryPrice × 0.995（-0.5%）
四、速查汇总表
状态组	允许方向	SL 锚点	TP1 锚点（主目标）	TP2 锚点（次目标）	偏移系数
STRONG_BULLISH_HEALTHY	BUY	15M_TrailingLow	1H_LastSwingHigh	4H_LastSwingHigh	0.002
STRONG_BEARISH_HEALTHY	SELL	15M_TrailingHigh	1H_LastSwingLow	4H_LastSwingLow	0.002
*_SHALLOW_*	BUY/SELL	15M_Nearest*OB_*	15M_LastSwingHigh/Low	1H_LastSwingHigh/Low	0.002
*_WARNING_1H	BUY/SELL	1H_Nearest*OB_*	15M_LastSwingHigh/Low	1H_LastSwingHigh/Low	0.002
*_WARNING_4H	BUY/SELL	15M_Nearest*OB_*（入场级别紧止损）	15M_Strong/Low（快进快出）	NearestDemand/SupplyZone（最近订单块边界）	0.005
*_BOTTOMING / *_TOPPING	BUY/SELL	1H_Nearest*OB_*	15M_LastSwingHigh/Low	1H_LastSwingHigh/Low	0.001
*_ENDING_CONTINUE_*	BUY/SELL	EntryPrice × (1 ± 0.002)	15M_LastSwingHigh/Low	1H_LastSwingHigh/Low	0.002
*_ENDING_CONFIRM	BUY/SELL	1H_Nearest*OB_*	1H_LastSwingHigh/Low	4H_LastSwingHigh/Low	0.002
*_CONFIRMED_*	❌ 不开单	—	—	—	—
*_ONGOING	❌ 不开单	—	—	—	—
RANGING_*	❌ 不开单	—	—	—	—
五、实战案例演示（基于你提供的数据）
订单数据：

状态：STRONG_BEARISH_WARNING_4H

方向：做空

入场价：1634.00

原始数据提取：

4H_NearestBearishOB_High = 1817.90（4H 供给区高点）

15M_LastSwingLow = 1526.07（15M 强低点）

15M_DemandZoneTop = 1485.50（15M 需求区 1469.50~1485.50 上沿）

1H_DemandZoneTop = 1426.67（1H 需求区 1407.36~1426.67 上沿）

4H_DemandZoneTop = 1482.87（4H 需求区 1384.06~1482.87 上沿）

计算 NearestDemandZoneTop：
筛选所有 barHigh < 1634 的看涨 OB 上沿，取最大值：

15M：1485.50 ✅（最高，距离最近）

1H：1426.67

4H：1482.87
→ 取 1485.50

最终点位：

项目	计算结果	幅度
止损（SL）	1646.71 × 1.005 = 1654.94（锚定15M供给区高点）	-1.28%
止盈1（TP1，主目标）	1526.07	-6.60%（平仓 70%）
止盈2（TP2，次目标）	1485.50	-9.08%（剩余 30%，落袋）
❌ 原单错误止盈	1416.04	-13.34%（4H需求区中部，已删除）
执行方案：

text
入场：1634.00
止损：1654.94（-1.28%）
止盈1：1526.07（平仓 70%，-6.60%）
止盈2：1485.50（平仓 30%，-9.08%，止损移至保本）
六、版本记录
版本	日期	修改内容
1.0	2026-06-17	初版
1.1	2026-06-17	增加强开算法，统一术语
2.0	2026-06-17	修正 WARNING_4H 逻辑，止盈缩短至 15M 级别
3.0	2026-06-17	新增 NearestDemandZoneTop / NearestSupplyZoneBottom 逻辑；明确定义 WARNING_4H 状态下 TP2 的计算方式为"最近需求/供给区上沿/下沿"，并附实战案例验证
4.0	2026-06-17	WARNING_4H 止损锚点从 4H 改为 15M（入场级别），系数从 0.002 改为 0.005（紧止损原则）；TP1 从 LastSwingHigh/Low 改为 StrongHigh/Low（强高/强低）