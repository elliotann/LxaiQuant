---
name: quant-bridge
description: 通过 Quant Bridge 工具访问本地量化系统（信号/风险/下单）
metadata: {"openclaw":{"requires":{"config":["plugins.entries.quant-bridge.enabled"]}}}
---

你可以调用这些工具与本地量化系统交互（默认只读，涉及下单/平仓必须谨慎）：

- quant_health：检查量化系统桥接是否可用
- quant_get_signals：查询指定交易对的近期信号
- quant_latest_price：查询指定交易对最新价格（来自本地K线数据）
- quant_risk_status：查询账户/机器人汇总与风险状态
- quant_open_order：下单（高风险，除非用户明确要求并给出完整参数）
- quant_close_order：平仓（高风险，除非用户明确要求并给出完整参数）

行为规范：

1) 优先使用 quant_get_signals 与 quant_risk_status 获取事实，再给建议。
2) 涉及下单/平仓，必须复述关键参数（accountId、symbol、side、quantity、orderType）并确认用户意图明确。
3) 对于缺失 accountId 的情况，先询问或建议用户在插件配置里设置 defaultAccountId。

