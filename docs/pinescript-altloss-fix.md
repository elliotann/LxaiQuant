# Pine Script 智能亏损保护系统回测不生效 - 修复说明

## 问题分析

1. **恢复条件未实现**：参数里有「连续同向交易恢复」(`sameDirectionRecovery`) 和「要求盈利才恢复」(`requireProfitForRecovery`)，但代码里**从未**在满足这些条件时把 `protectionActive` 置为 `false`，只靠冷却期解除，保护容易一直挂着或逻辑不符合预期。

2. **检测时机**：保护是在「新平仓发生后」的下一根 K 线里根据 `strategy.closedtrades` 判断是否激活；若对 `closedtrades` 的索引或「最近几笔」的理解有误，会漏判或错判交替亏损。

3. **仓位计算**：`calculatePositionSize` 里用 `useAltLossProtection and protectionActive` 做减仓，逻辑正确；若回测中保护「完全没生效」，多半是 `protectionActive` 从未变为 `true`（检测/记录问题）或恢复逻辑缺失导致状态不对。

---

## 修复 1：增加「连续同向恢复」逻辑

在 **「3. 冷却期自动解除」** 这段的**前面**，增加「连续同向交易恢复」的判断（在 `if useAltLossProtection` 的大块里、处理完新平仓记录之后）：

```pinescript
// 2.5 连续同向恢复（在“检测交替亏损”之后、“冷却期”之前）
if useAltLossProtection and protectionActive
    needProfit = requireProfitForRecovery and array.size(tradeResults) > 0
    lastResult = safeArrayGetFloat(tradeResults, 0)
    profitOk = not needProfit or (not na(lastResult) and lastResult > 0)
    if consecutiveSameDirection >= sameDirectionRecovery and profitOk
        protectionActive := false
```

含义：当「连续同向笔数 ≥ 设定值」且（若勾选「要求盈利才恢复」则最近一笔必须盈利）时，立即解除保护。

---

## 修复 2：交替亏损判断更稳妥（可选）

在 `isAlternatingLoss()` 里确保「最近 N 笔都亏损」用统一标准（例如都按「盈亏 ≤ 0」算亏损），并避免空数组/越界：

```pinescript
isAlternatingLoss() =>
    if array.size(tradeDirs) < altLossThreshold
        false
    else
        checkCount = math.min(array.size(tradeDirs), altLossThreshold)
        var bool alternatingResult = true
        var bool allLossResult = true
        for i = 0 to checkCount - 2
            dir1 = safeArrayGetFloat(tradeDirs, i)
            dir2 = safeArrayGetFloat(tradeDirs, i + 1)
            if na(dir1) or na(dir2) or dir1 == dir2
                alternatingResult := false
                break
        for i = 0 to checkCount - 1
            result = safeArrayGetFloat(tradeResults, i)
            if not na(result) and result > 0
                allLossResult := false
                break
        alternatingResult and allLossResult
```

（你当前逻辑已是「存在 result > 0 则 allLossResult = false」，等价于「全部 ≤ 0 才算全亏」，上面只是加上 `na` 判断和一致注释。）

---

## 修复 3：确认「减仓」真的被用上（调试用）

在 `calculatePositionSize` 里可临时加一行，确认回测时保护态和减仓比例是否生效（用完可删或关掉）：

```pinescript
calculatePositionSize(entryPrice) =>
    var float qtyResult = 0.0
    if useMoneyManagement
        float actualAmount = fixedAmount
        float actualPercent = positionPercent
        if useAltLossProtection and protectionActive
            actualAmount := fixedAmount * (reducePositionPercent / 100.0)
            actualPercent := positionPercent * (reducePositionPercent / 100.0)
        // 调试：确认减仓生效（可选）
        // if useAltLossProtection and protectionActive and barstate.islast
        //     label.new(bar_index, high, "减仓 " + str.tostring(reducePositionPercent) + "%", color=color.orange)
        ...
```

---

## 修复 4：避免重复记录同一笔平仓

你已有 `tradeIds` 防重，但要确保用到的 `entry_id` 在回测里稳定。若发现同一笔被记两次，可加强去重（例如用 `entry_id` + `exit_time` 拼成唯一 key）。当前逻辑在「新关闭数量增加」时只处理 `lastClosedTradeCount` 到 `currentClosedTradeCount - 1`，且用 `entry_id` 查重，一般是正确的。

---

## 建议操作顺序

1. 先加上 **修复 1（连续同向恢复）**，再回测，看保护是否会在「连续同向且满足盈利条件」后正确解除。
2. 若仍不生效，打开 **修复 3** 的 label，看图表上是否出现「减仓 xx%」；若从不出现，说明 `protectionActive` 从未为 true，重点查 **修复 2** 和 `strategy.closedtrades` 的索引是否与「最近几笔」一致。
3. 确认参数：`启用智能亏损保护` 勾选、`交替亏损阈值` ≤ `检查最近交易次数`（例如 2 和 4），这样至少 2 笔交替亏损即可触发。

按上述修改后，智能亏损保护应在 TradingView 回测中能激活、减仓并在满足条件时恢复。
