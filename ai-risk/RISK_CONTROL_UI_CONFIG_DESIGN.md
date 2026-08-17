# 移动止盈止损界面配置设计文档

## 1. 概述

本文档设计方案二（推荐方案）的界面配置方案，包括：
- ✅ TrailingStopLossRule（固定百分比移动止损）
- ✅ AverageTrueRangeTrailingStopLossRule（ATR 移动止损）
- ✅ AverageTrueRangeStopGainRule（ATR 固定止盈）

## 2. 配置参数结构

### 2.1 参数分类

#### 止损配置（Stop Loss）
- **类型选择**：固定百分比移动止损 vs ATR 移动止损
- **固定百分比参数**：止损百分比、回溯K线数量
- **ATR 参数**：ATR 倍数、ATR 计算周期

#### 止盈配置（Take Profit）
- **类型选择**：ATR 固定止盈
- **ATR 参数**：ATR 倍数、ATR 计算周期

### 2.2 参数映射表

| 规则类 | 配置类型 | 参数名称 | 参数说明 | 默认值 |
|--------|---------|---------|---------|--------|
| TrailingStopLossRule | `fixed_percent_trailing` | `stopLossPercent` | 止损百分比（%） | 2.0 |
| TrailingStopLossRule | `fixed_percent_trailing` | `stopLossBarCount` | 回溯K线数量（可选） | 无限制 |
| AverageTrueRangeTrailingStopLossRule | `atr_trailing` | `stopLossAtrMultiplier` | ATR 倍数 | 2.0 |
| AverageTrueRangeTrailingStopLossRule | `atr_trailing` | `stopLossAtrPeriod` | ATR 计算周期 | 14 |
| AverageTrueRangeStopGainRule | `atr_based` | `takeProfitAtrMultiplier` | ATR 倍数 | 3.0 |
| AverageTrueRangeStopGainRule | `atr_based` | `takeProfitAtrPeriod` | ATR 计算周期 | 14 |

## 3. 界面设计方案

### 3.1 配置表单结构

```
┌─────────────────────────────────────────────────────────┐
│ 风控配置 - 移动止盈止损                                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ 【止损设置】                                             │
│ ○ 固定百分比移动止损                                      │
│ ○ ATR 移动止损                                          │
│                                                         │
│ ┌─ 固定百分比移动止损参数（当选择固定百分比时显示） ─┐      │
│ │ 止损百分比：    [2.0] %                              │      │
│ │ 回溯K线数量：   [无限制] ▼                           │      │
│ └─────────────────────────────────────────────────────┘      │
│                                                         │
│ ┌─ ATR 移动止损参数（当选择 ATR 时显示） ───────────┐      │
│ │ ATR 倍数：      [2.0]                              │      │
│ │ ATR 计算周期：  [14]                               │      │
│ └─────────────────────────────────────────────────────┘      │
│                                                         │
│ 【止盈设置】                                             │
│ ☑ 启用止盈                                              │
│                                                         │
│ ┌─ ATR 固定止盈参数（当启用止盈时显示） ────────────┐      │
│ │ ATR 倍数：      [3.0]                              │      │
│ │ ATR 计算周期：  [14]                               │      │
│ └─────────────────────────────────────────────────────┘      │
│                                                         │
│ 【风险收益比】                                           │
│ 当前风险收益比：1:1.5 （自动计算，仅供参考）                │
│                                                         │
│ [保存] [重置]                                            │
└─────────────────────────────────────────────────────────┘
```

### 3.2 表单字段详细设计

#### 止损设置区域

**1. 止损类型选择（单选按钮组）**
```html
<el-radio-group v-model="riskControl.stopLossType">
  <el-radio label="fixed_percent_trailing">固定百分比移动止损</el-radio>
  <el-radio label="atr_trailing">ATR 移动止损</el-radio>
</el-radio-group>
```

**2. 固定百分比移动止损参数（条件显示）**
```html
<el-form-item 
  v-if="riskControl.stopLossType === 'fixed_percent_trailing'"
  label="止损百分比"
  prop="stopLossPercent"
>
  <el-input-number
    v-model="riskControl.stopLossPercent"
    :min="0.1"
    :max="10"
    :step="0.1"
    :precision="1"
  />
  <span class="form-item-suffix">%</span>
  <el-tooltip content="从最高/最低价的百分比回撤触发止损">
    <i class="el-icon-question"></i>
  </el-tooltip>
</el-form-item>

<el-form-item 
  v-if="riskControl.stopLossType === 'fixed_percent_trailing'"
  label="回溯K线数量"
  prop="stopLossBarCount"
>
  <el-select v-model="riskControl.stopLossBarCount">
    <el-option :value="null" label="无限制"></el-option>
    <el-option :value="50" label="50"></el-option>
    <el-option :value="100" label="100"></el-option>
    <el-option :value="200" label="200"></el-option>
  </el-select>
  <el-tooltip content="限制计算最高/最低价时回溯的K线数量，默认无限制">
    <i class="el-icon-question"></i>
  </el-tooltip>
</el-form-item>
```

**3. ATR 移动止损参数（条件显示）**
```html
<el-form-item 
  v-if="riskControl.stopLossType === 'atr_trailing'"
  label="ATR 倍数"
  prop="stopLossAtrMultiplier"
>
  <el-input-number
    v-model="riskControl.stopLossAtrMultiplier"
    :min="1.0"
    :max="5.0"
    :step="0.1"
    :precision="1"
  />
  <el-tooltip content="止损距离 = ATR × 倍数，建议值：1.5-3.0">
    <i class="el-icon-question"></i>
  </el-tooltip>
</el-form-item>

<el-form-item 
  v-if="riskControl.stopLossType === 'atr_trailing'"
  label="ATR 计算周期"
  prop="stopLossAtrPeriod"
>
  <el-input-number
    v-model="riskControl.stopLossAtrPeriod"
    :min="5"
    :max="50"
    :step="1"
  />
  <span class="form-item-suffix">根K线</span>
  <el-tooltip content="计算 ATR 指标时使用的周期，通常为 14">
    <i class="el-icon-question"></i>
  </el-tooltip>
</el-form-item>
```

#### 止盈设置区域

```html
<el-form-item label="启用止盈">
  <el-switch v-model="riskControl.takeProfitEnabled"></el-switch>
</el-form-item>

<template v-if="riskControl.takeProfitEnabled">
  <el-form-item label="ATR 倍数" prop="takeProfitAtrMultiplier">
    <el-input-number
      v-model="riskControl.takeProfitAtrMultiplier"
      :min="1.0"
      :max="10.0"
      :step="0.1"
      :precision="1"
    />
    <el-tooltip content="止盈距离 = ATR × 倍数，建议值：2.0-5.0">
      <i class="el-icon-question"></i>
    </el-tooltip>
  </el-form-item>

  <el-form-item label="ATR 计算周期" prop="takeProfitAtrPeriod">
    <el-input-number
      v-model="riskControl.takeProfitAtrPeriod"
      :min="5"
      :max="50"
      :step="1"
    />
    <span class="form-item-suffix">根K线</span>
    <el-tooltip content="计算 ATR 指标时使用的周期，通常为 14">
      <i class="el-icon-question"></i>
    </el-tooltip>
  </el-form-item>
</template>
```

#### 风险收益比显示（只读，自动计算）

```html
<el-form-item label="风险收益比（自动计算）">
  <el-tag type="info">
    1:{{ calculatedRiskRewardRatio }}
  </el-tag>
  <span class="form-item-hint">仅供参考，实际收益取决于市场波动</span>
</el-form-item>
```

## 4. 数据模型设计

### 4.1 Vue 组件数据模型

```typescript
interface RiskControlConfig {
  // 止损设置
  stopLossType: 'fixed_percent_trailing' | 'atr_trailing';
  
  // 固定百分比移动止损参数
  stopLossPercent?: number;        // 止损百分比（%）
  stopLossBarCount?: number | null; // 回溯K线数量，null 表示无限制
  
  // ATR 移动止损参数
  stopLossAtrMultiplier?: number;  // ATR 倍数
  stopLossAtrPeriod?: number;      // ATR 计算周期
  
  // 止盈设置
  takeProfitEnabled: boolean;      // 是否启用止盈
  takeProfitAtrMultiplier?: number; // ATR 倍数
  takeProfitAtrPeriod?: number;     // ATR 计算周期
}

// 默认配置
const defaultRiskControl: RiskControlConfig = {
  stopLossType: 'atr_trailing',
  stopLossAtrMultiplier: 2.0,
  stopLossAtrPeriod: 14,
  takeProfitEnabled: true,
  takeProfitAtrMultiplier: 3.0,
  takeProfitAtrPeriod: 14,
};
```

### 4.2 后端配置类设计

```java
@Data
public class RiskControlConfig {
    // 止损配置
    private StopLossConfig stopLoss;
    
    // 止盈配置
    private TakeProfitConfig takeProfit;
}

@Data
public class StopLossConfig {
    private String type;  // "fixed_percent_trailing" 或 "atr_trailing"
    
    // 固定百分比移动止损参数
    private BigDecimal percent;
    private Integer barCount;  // null 表示无限制
    
    // ATR 移动止损参数
    private BigDecimal atrMultiplier;
    private Integer atrPeriod;
}

@Data
public class TakeProfitConfig {
    private Boolean enabled;  // 是否启用
    private String type;      // "atr_based"
    private BigDecimal atrMultiplier;
    private Integer atrPeriod;
}
```

### 4.3 YAML 配置文件格式

```yaml
riskControl:
  stopLoss:
    type: atr_trailing  # 或 fixed_percent_trailing
    atrMultiplier: 2.0
    atrPeriod: 14
    # 如果 type 是 fixed_percent_trailing，则使用：
    # percent: 2.0
    # barCount: null  # 或具体数字
  
  takeProfit:
    enabled: true
    type: atr_based
    atrMultiplier: 3.0
    atrPeriod: 14
```

## 5. 表单验证规则

```typescript
const rules = {
  stopLossPercent: [
    { required: true, message: '请输入止损百分比', trigger: 'blur' },
    { type: 'number', min: 0.1, max: 10, message: '止损百分比应在 0.1% - 10% 之间', trigger: 'blur' }
  ],
  stopLossAtrMultiplier: [
    { required: true, message: '请输入 ATR 倍数', trigger: 'blur' },
    { type: 'number', min: 1.0, max: 5.0, message: 'ATR 倍数应在 1.0 - 5.0 之间', trigger: 'blur' }
  ],
  stopLossAtrPeriod: [
    { required: true, message: '请输入 ATR 计算周期', trigger: 'blur' },
    { type: 'number', min: 5, max: 50, message: 'ATR 计算周期应在 5 - 50 之间', trigger: 'blur' }
  ],
  takeProfitAtrMultiplier: [
    { required: true, message: '请输入止盈 ATR 倍数', trigger: 'change' },
    { type: 'number', min: 1.0, max: 10.0, message: '止盈 ATR 倍数应在 1.0 - 10.0 之间', trigger: 'blur' }
  ],
  takeProfitAtrPeriod: [
    { required: true, message: '请输入止盈 ATR 计算周期', trigger: 'change' },
    { type: 'number', min: 5, max: 50, message: '止盈 ATR 计算周期应在 5 - 50 之间', trigger: 'blur' }
  ]
};
```

## 6. 计算逻辑（Computed）

```typescript
// 风险收益比计算（仅供参考）
const calculatedRiskRewardRatio = computed(() => {
  if (!riskControl.value.takeProfitEnabled) {
    return '-';
  }
  
  let stopLossDistance: number;
  let takeProfitDistance: number;
  
  if (riskControl.value.stopLossType === 'fixed_percent_trailing') {
    // 固定百分比：假设当前价格为基准
    stopLossDistance = riskControl.value.stopLossPercent || 0;
  } else {
    // ATR：假设 ATR 为价格的 1%（简化计算）
    stopLossDistance = (riskControl.value.stopLossAtrMultiplier || 0) * 1.0;
  }
  
  // 止盈距离
  takeProfitDistance = (riskControl.value.takeProfitAtrMultiplier || 0) * 1.0;
  
  if (stopLossDistance === 0) return '-';
  
  const ratio = (takeProfitDistance / stopLossDistance).toFixed(2);
  return ratio;
});
```

## 7. 配置示例

### 7.1 配置示例 1：ATR 移动止损 + ATR 止盈（推荐）

```yaml
riskControl:
  stopLoss:
    type: atr_trailing
    atrMultiplier: 2.0
    atrPeriod: 14
  takeProfit:
    enabled: true
    type: atr_based
    atrMultiplier: 3.0
    atrPeriod: 14
```

**界面展示：**
- 止损类型：○ ATR 移动止损 ✅
- ATR 倍数：2.0
- ATR 计算周期：14
- 启用止盈：☑
- 止盈 ATR 倍数：3.0
- 止盈 ATR 计算周期：14

### 7.2 配置示例 2：固定百分比移动止损 + ATR 止盈

```yaml
riskControl:
  stopLoss:
    type: fixed_percent_trailing
    percent: 2.0
    barCount: null
  takeProfit:
    enabled: true
    type: atr_based
    atrMultiplier: 3.0
    atrPeriod: 14
```

**界面展示：**
- 止损类型：○ 固定百分比移动止损 ✅
- 止损百分比：2.0%
- 回溯K线数量：无限制
- 启用止盈：☑
- 止盈 ATR 倍数：3.0
- 止盈 ATR 计算周期：14

### 7.3 配置示例 3：只使用移动止损（不设置止盈）

```yaml
riskControl:
  stopLoss:
    type: atr_trailing
    atrMultiplier: 2.0
    atrPeriod: 14
  takeProfit:
    enabled: false
```

**界面展示：**
- 止损类型：○ ATR 移动止损 ✅
- ATR 倍数：2.0
- ATR 计算周期：14
- 启用止盈：☐

## 8. 实现建议

### 8.1 前端实现步骤

1. **在策略编辑表单中添加"风控配置"卡片/折叠面板**
2. **实现条件显示逻辑**：根据止损类型选择显示不同的参数组
3. **添加表单验证**：确保必填参数和数值范围
4. **实现自动计算**：风险收益比等只读字段
5. **保存配置**：将配置保存到策略参数中

### 8.2 后端实现步骤

1. **创建配置类**：`RiskControlConfig`, `StopLossConfig`, `TakeProfitConfig`
2. **参数验证**：在配置加载时验证参数合理性
3. **规则工厂**：根据配置创建对应的 Rule 对象
4. **集成到策略系统**：在策略执行时应用这些规则

### 8.3 配置存储建议

可以将风控配置作为策略的一部分存储，有两种方式：

**方式一：作为策略参数的一部分**
```json
{
  "strategyId": "xxx",
  "parameters": {
    "riskControl": {
      "stopLoss": { ... },
      "takeProfit": { ... }
    }
  }
}
```

**方式二：独立的配置表**
```sql
CREATE TABLE risk_control_config (
  id BIGINT PRIMARY KEY,
  strategy_id VARCHAR(64),
  config JSON,
  ...
);
```

推荐使用方式一（作为策略参数），这样配置管理更简单。

## 9. 用户体验优化建议

1. **提供预设配置模板**：
   - 保守型（止损 1.5 ATR，止盈 2.5 ATR）
   - 平衡型（止损 2.0 ATR，止盈 3.0 ATR）
   - 激进型（止损 2.5 ATR，止盈 4.0 ATR）

2. **实时预览**：
   - 显示当前配置的风险收益比
   - 显示示例计算（假设 ATR = 价格 × 1%）

3. **帮助提示**：
   - 每个字段添加 tooltip 说明
   - 提供配置建议和最佳实践

4. **表单联动**：
   - 切换止损类型时，清空另一类型的参数
   - 启用/禁用止盈时，显示/隐藏相关字段


