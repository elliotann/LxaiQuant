# 权重规则引擎 PC 前端配置执行清单

> **对应文档**：`SMC多周期信号评估与动态仓位权重系统 V2.3.md`  
> **核心目标**：将权重规则配置从直接编写 JSON 迁移为 PC 前端（ai-frontend-web）的可视化页面操作  
> **原则**：不改动现有系统已稳定的功能，增量补齐缺失环节

---

## 1. 前端页面（ai-frontend-web）

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 1.1 | WeightRuleEngine 页面路由注册 | ✅ 已完成 | `src/router/index.ts` 中已注册 |
| 1.2 | SignalServiceManagement → WeightRuleEngine 跳转入口 | ✅ 已完成 | 配置列表每行「管理」按钮 + 表单底部摘要区点击跳转 |
| 1.3 | 信号配置切换下拉框 | ✅ 已完成 | 顶部 select 加载所有配置列表 |
| 1.4 | 引擎启用/关闭总开关 | ✅ 已完成 | 页面顶部 el-switch |
| 1.5 | 规则列表面板（左 7:17） | ✅ 已完成 | 显示名称/类型标签/条件数/评分/否决权/启用开关/删除 |
| 1.6 | 规则编辑器（右侧上半） | ✅ 已完成 | 名称/类型/评分/否决权/AND-OR/条件列表动态行 |
| 1.7 | 条件编辑器的指标选择（从 SPI 动态加载） | ✅ 已完成 | `/api/rule-engine/indicators` 接口驱动 |
| 1.8 | 特殊条件渲染（SMC_MARKET_TREND / PATTERN_TYPE / MACD） | ✅ 已完成 | 硬编码特殊 UI 组件 |
| 1.9 | 条件参数的动态展示（valueType 切换组件） | ✅ 已完成 | NUMERIC→输入框 / ENUM→下拉 / BOOLEAN→开关 / STATE→可输入下拉 |
| 1.10 | 测试面板（右侧下半） | ✅ 已完成 | 方向/价格/趋势/SMC 上下文 + 测试结果 + 迹线明细 |
| 1.11 | 评分设置弹框 | ✅ 已完成 | vetoContributeScore / mappingMode / 线性参数 |
| 1.12 | 版本历史弹框 | ✅ 已完成 | 查看与恢复 |
| 1.13 | 保存全部按钮 → PUT API 调用 | ✅ 已完成 | 序列化为 WeightRuleConfig → 写入后端 |

**前端小结**：前端页面已完整实现。指标下拉框已添加 `filterable` 属性，支持打字搜索过滤 40+ 个指标（含全部 22 个 SMC 指标）。

---

## 2. 后端 API（ai-quant/ai-signal）

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 2.1 | `PUT /api/signal-service/configs/{id}/weight-rules` | ✅ 已完成 | 接收 WeightRuleConfig，序列化 JSON 存入 DB |
| 2.2 | `GET /api/signal-service/configs/{id}/weight-rules` | ✅ 已完成 | 从 DB 反序列化返回 WeightRuleConfig |
| 2.3 | `POST /api/signal-service/configs` 含 weightRules | ✅ 已完成 | 创建时可选携带规则 |
| 2.4 | `PUT /api/signal-service/configs/{id}` 含 weightRules | ✅ 已完成 | 更新时可选携带规则 |
| 2.5 | `POST /api/rule-engine/test` 测试接口 | ✅ 已完成 | 接收 weightRules + 上下文，返回评估结果+迹线 |
| 2.6 | `GET /api/rule-engine/versions/{configId}` | ✅ 已完成 | 获取版本列表 |
| 2.7 | `POST /api/rule-engine/versions/{versionId}/restore` | ✅ 已完成 | 恢复历史版本 |
| 2.8 | `GET /api/rule-engine/indicators` | ✅ 已完成 | SPI 指标元数据列表，驱动前端条件编辑器 |
| 2.9 | WeightRuleVersionService 版本快照 | ✅ 已完成 | 每次保存自动创建快照 |

**后端小结**：所有 API 已就绪，无需新增接口。

---

## 3. 数据模型

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 3.1 | WeightRuleConfig（enabled + rules + scoringConfig） | ✅ 已完成 | 前端模型与 Java POJO 对齐 |
| 3.2 | WeightRule（name/type/score/vetoWeight/conditions/conditionOperator/order/enabled） | ✅ 已完成 | 单条规则定义 |
| 3.3 | RuleCondition（indicator/params/operator/value/direction） | ✅ 已完成 | 条件定义 |
| 3.4 | WeightScoringConfig（vetoContributeScore/mappingMode/linearSlope/linearMinWeight/linearMaxWeight） | ✅ 已完成 | 评分配置 |
| 3.5 | `signal_service_config.weight_rules_json` TEXT 列 | ✅ 已完成 | DB 存储列，已有迁移脚本 |

**模型小结**：所有数据模型已对齐，无新增字段需求。

---

## 4. 核心引擎（ai-signal）

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 4.1 | WeightRuleEngine.evaluate() 两轮执行 | ✅ 已完成 | VETO（第1轮）+ SCORING（第2轮） |
| 4.2 | scoreToWeight() 阶梯映射 | ✅ 已完成 | ≥3.5→2.0 / ≥2.5→1.5 / ≥1.5→1.0 / ≥0.5→0.5 / <0.5→0.0 |
| 4.3 | SignalServiceConfigService.getWeightRules() | ✅ 已完成 | 加载 DB 配置→反序列化 |
| 4.4 | BuiltInIndicatorProvider SPI 注册 | ✅ 已完成 | 22 个 SMC indicator（含补齐的13个）+ 技术指标 |
| 4.5 | WeightRuleContext 上下文 | ⚠️ 字段不足 | 当前仅 7 个 SMC 字段，规则条件引用的 `SMC_CHAOS_EXCEPTION` / `SMC_INTERNAL_BOS_ALIGNED` 等未定义（见下方 §6） |

---

## 5. 默认规则配置

| # | 规则名称 | 类型 | 条件 | 建议 order |
|---|---------|------|------|-----------|
| 5.1 | SMC 窄幅横盘熔断（振幅<3%） | VETO | SMC_RANGE_PERCENT_20H < 3.0 | 0 |
| 5.2 | SMC 中幅震荡熔断（振幅3~8% + 翻转≥3） | VETO | SMC_RANGE_PERCENT_20H BETWEEN 3.0,8.0 AND SMC_FLIP_COUNT ≥ 3 | 1 |
| 5.3 | SMC 做多追高否决（1H阻力区） | VETO | SIGNAL_DIRECTION=BUY AND SMC_1H_POSITION_RATIO > 0.618 | 2 |
| 5.4 | SMC 做空追空否决（1H支撑区） | VETO | SIGNAL_DIRECTION=SELL AND SMC_1H_POSITION_RATIO < 0.382 | 3 |
| 5.5 | SMC 做多波次否决（混沌/试盘/赶顶） | VETO | SIGNAL_DIRECTION=BUY AND SMC_4H_WAVE IN [0,1,4] | 4 |
| 5.6 | SMC 做空波次否决（混沌/试盘/赶底） | VETO | SIGNAL_DIRECTION=SELL AND SMC_4H_WAVE IN [0,-1,-4] | 5 |
| 5.7 | SMC 衰竭衰老否决（试盘+年龄>50） | VETO | SMC_4H_WAVE IN [1,-1] AND SMC_4H_AGE > 50 | 6 |
| 5.8 | SMC HL破位否决（多头结构损坏） | VETO | SIGNAL_DIRECTION=BUY AND SMC_HL_HEALTH = -2 | 7 |
| 5.9 | SMC LH破位否决（空头结构损坏） | VETO | SIGNAL_DIRECTION=SELL AND SMC_LH_HEALTH = -2 | 8 |
| 5.10 | SMC 最小盈亏比否决 | VETO | SMC_RISK_REWARD_RATIO < 1.2 | 9 |
| 5.11 | SMC 最大仓位否决 | VETO | SMC_POSITION_MARGIN_PERCENT > 2.0 | 10 |
| 5.12 | SMC 4H波次加分 | SCORING | SMC_4H_WAVE IN [2,3,-2,-3] | 10 → +1.0 |
| 5.13 | SMC 1H波次加分 | SCORING | SMC_1H_WAVE IN [2,3,-2,-3] | 11 → +0.5 |
| 5.14 | SMC 1H支撑区做多加分 | SCORING | SIGNAL_DIRECTION=BUY AND SMC_1H_POSITION_RATIO < 0.382 | 12 → +0.5 |
| 5.15 | SMC 1H阻力区做空加分 | SCORING | SIGNAL_DIRECTION=SELL AND SMC_1H_POSITION_RATIO > 0.618 | 13 → +0.5 |
| 5.16 | SMC 1H中继区减分 | SCORING | SMC_1H_POSITION_RATIO BETWEEN 0.382,0.618 | 14 → -0.2 |
| 5.17 | SMC 方向背离减分 | SCORING | SMC_DIRECTION_ALIGNED = 0 | 15 → -1.0 |
| 5.18 | SMC 4H极端区做多减分 | SCORING | SIGNAL_DIRECTION=BUY AND SMC_4H_POSITION_RATIO > 0.8 | 16 → -0.5 |
| 5.19 | SMC 4H极端区做空减分 | SCORING | SIGNAL_DIRECTION=SELL AND SMC_4H_POSITION_RATIO < 0.2 | 17 → -0.5 |
| 5.20 | SMC 趋势流畅加分 | SCORING | SMC_FLIP_COUNT ≤ 1 | 18 → +0.5 |
| 5.21 | SMC 15M微观共振加分 | SCORING | SMC_INTERNAL_BOS_ALIGNED = 1 | 19 → +0.2 |
| 5.22 | SMC HL健康加分 | SCORING | SIGNAL_DIRECTION=BUY AND SMC_HL_HEALTH = 1 | 20 → +0.3 |
| 5.23 | SMC HL危险减分 | SCORING | SIGNAL_DIRECTION=BUY AND SMC_HL_HEALTH = -1 | 21 → -0.3 |
| 5.24 | SMC LH健康加分 | SCORING | SIGNAL_DIRECTION=SELL AND SMC_LH_HEALTH = 1 | 22 → +0.3 |
| 5.25 | SMC LH危险减分 | SCORING | SIGNAL_DIRECTION=SELL AND SMC_LH_HEALTH = -1 | 23 → -0.3 |
| 5.26 | SMC 新鲜结构加分 | SCORING | SMC_1H_AGE ≤ 5 | 24 → +0.2 |
| 5.27 | SMC 结构老化减分 | SCORING | SMC_1H_AGE > 20 AND SMC_1H_AGE ≤ 40 | 25 → -0.3 |
| 5.28 | SMC 严重老化减分 | SCORING | SMC_1H_AGE > 40 | 26 → -0.5 |
| 5.29 | SMC 混沌特例加分 | SCORING | SMC_CHAOS_EXCEPTION = 1 | 27 → +0.2 |

> **使用方式**：交易员在 WeightRuleEngine 页面按上表 `order` 顺序逐条添加，前端条件编辑器选择对应 indicator + operator + value。

---

## 6. 已知缺失 & 待补齐

> ⚠️ 以下为规则条件引用了、但当前系统尚未实现的字段/处理器，需要后续代码补齐。

| # | 缺失项 | 影响规则 | 影响范围 |
|---|--------|---------|---------|
| 6.1 | WeightRuleContext 缺少 `smcInternalBosAligned` 字段（SMC_INTERNAL_BOS_ALIGNED） | §5.21 15M微观共振加分 | ✅ **已补齐** |
| 6.2 | WeightRuleContext 缺少 `smcChaosException` 字段（SMC_CHAOS_EXCEPTION） | §5.29 混沌特例加分 | ✅ **已补齐**（字段+handler已注册，但值始终为0，需真实计算） |
| 6.3 | BuiltInIndicatorProvider 未注册 SMC_INTERNAL_BOS_ALIGNED handler | §5.21 | ✅ **已补齐** |
| 6.4 | BuiltInIndicatorProvider 未注册 SMC_CHAOS_EXCEPTION handler | §5.29 | ✅ **已补齐** |
| ~~6.5~~ | BuiltInIndicatorProvider 未注册 SMC_POSITION_MARGIN_PERCENT handler | §5.11 最大仓位否决 | ❌ **已移除**（SMC_POSITION_MARGIN_PERCENT 需外部仓位数据，当前信号级无法计算，保留为未来扩展） |
| 6.6 | DefaultSignService.evaluateWeightRuleEngine() 未填充 SMC 数据 | 全部 SMC 规则 | ✅ **已补齐**（见下方代码补齐日志） |
| ~~6.7~~ | MultiPeriodSmcData 未补充为 PeriodData Map 架构 | SMC 上下文 | ✅ **已补齐** |

**新增补缺项（扩充分 §5 规则所需的更多 SMC 指标）：**

| # | 缺失项 | 影响规则 | 状态 |
|---|--------|---------|------|
| 6.8 | WeightRuleContext 缺少 14 个 SMC 字段（SMC_RANGE_PERCENT_20H / FLIP_COUNT / 1H_POSITION_RATIO / 4H_WAVE / 4H_AGE / HL_HEALTH / LH_HEALTH / RISK_REWARD_RATIO / 1H_WAVE / 4H_POSITION_RATIO / DIRECTION_ALIGNED / 1H_AGE / 4H_POSITION_RATIO / POSITION_MARGIN_PERCENT） | §5 大量规则 | ✅ **已补齐** |
| 6.9 | BuiltInIndicatorProvider 缺少对应 handler | 同上 | ✅ **已补齐** |
| 6.10 | SmcIndicatorService 缺少 getSmcResultHistory 方法（波次/翻转/年龄计算需历史结果列表） | SMC_4H_WAVE / SMC_1H_WAVE / FLIP_COUNT / 1H_AGE / 4H_AGE | ✅ **已补齐** |

---

## 7. 验证清单

| # | 验证项 | 预期结果 | 验收标准 |
|---|--------|---------|---------|
| 7.1 | PC前端进入 WeightRuleEngine 页面 | 页面正常加载 | 选择任意配置后，规则列表+编辑器+测试面板全显 |
| 7.2 | 添加 VETO 规则 | 规则出现在列表中 | 类型标签为红色「VETO」，条件数>0 |
| 7.3 | 添加 SCORING 规则 | 规则出现在列表中 | 类型标签为蓝色「SCORING」，评分值生效 |
| 7.4 | 条件编辑器中选择各 indicator | 运算符和值输入动态切换 | 对照 §5 规则表校验每个 indicator 的运算符选项 |
| 7.5 | SMC_MARKET_TREND 特殊渲染 | 显示复合趋势状态下拉 | 可选 BULLISH/BEARISH/NEUTRAL 等 |
| 7.6 | PATTERN_TYPE 多选 | 显示带多选的 select | 可选多种K线形态 |
| 7.7 | 保存全部 | 页面提示成功 | DB 中 weight_rules_json 列有完整 JSON |
| 7.8 | 重进页面加载已有规则 | 规则列表与保存前一致 | 规则数/顺序/内容完全一致 |
| 7.9 | 测试面板：输入参数并测试 | 返回 VETO/权重/评分结果 | 迹线明细表展示每条规则执行记录 |
| 7.10 | 版本历史 | 可查看历史版本 | 点击恢复后规则回滚 |
| 7.11 | 引擎关闭总开关 | 全部规则不生效 | 引擎返回默认权重 1.0 |
| 7.12 | 指标下拉搜索过滤 | 在条件编辑器中输入关键字可过滤指标 | 输入 "FLIP" 可定位到 SMC_FLIP_COUNT，输入 "HL" 可定位到 SMC_HL_HEALTH |

---

## 8. 操作流程速览

```
┌─────────────────────────────────────────────────────────────────┐
│  1. 登录 PC 前端 → 「市场」→ 「信号服务管理」                   │
│  2. 选择/创建信号服务配置                                        │
│  3. 在配置列表行点击「管理」→ 进入 WeightRuleEngine             │
│  4. 开启「引擎启用」开关                                         │
│  5. 按 §5 规则表逐一添加 11 条 VETO 规则                        │
│  6. 按 §5 规则表逐一添加 18 条 SCORING 规则                     │
│  7. 在测试面板输入模拟参数验证规则效果                           │
│  8. 点击「保存全部」→ 自动创建版本快照                           │
│  9. 后续调整：返回 WeightRuleEngine → 增删改规则 → 保存         │
└─────────────────────────────────────────────────────────────────┘
```
