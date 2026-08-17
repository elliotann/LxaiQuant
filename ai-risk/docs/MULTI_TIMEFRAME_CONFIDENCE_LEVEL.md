# 多周期分析中 ConfidenceLevel（置信度等级）生成逻辑文档

## 概述

在多周期艾略特波浪分析中，`ConfidenceLevel`（置信度等级）用于表示分析结果的可靠性程度。它基于综合得分（Composite Score）自动计算生成。

## 生成位置

`ConfidenceLevel` 在多周期分析的**阶段3：生成最终结果**中生成。

**代码位置：**
- 文件：`MultiTimeFrameCoordinator.java`
- 方法：`generateFinalResult()`
- 行号：第196行

```java
.confidence(ConfidenceLevel.fromScore(summary.getCompositeScore()))
```

## 生成流程

### 完整的数据流

```
阶段1: 各周期独立分析
  ↓
  得到 Map<TimeFrame, TimeFrameAnalysis>
  ↓
阶段2: 综合汇总分析
  ↓
  2.1 计算各周期权重 (SmartWeightAdjuster.calculateAdjustedWeights)
  ↓
  2.2 计算综合得分 (calculateCompositeScore) ← 关键步骤
  ↓
  2.3-2.8 其他汇总计算...
  ↓
  得到 MultiTimeFrameSummary (包含 compositeScore)
  ↓
阶段3: 生成最终结果
  ↓
  3.6 构建 QualityEvaluationResult
  ↓
  ConfidenceLevel.fromScore(compositeScore) ← 生成置信度等级
```

## 详细逻辑

### 1. 综合得分计算（Composite Score）

**位置：** `MultiTimeFrameCoordinator.calculateCompositeScore()`

**逻辑：**
```java
// 加权平均计算
double weightedSum = 0;
double totalWeight = 0;

for (每个周期分析) {
    TimeFrame tf = 周期;
    TimeFrameAnalysis analysis = 周期分析结果;
    double weight = 该周期的权重;  // 由 SmartWeightAdjuster 计算
    
    weightedSum += analysis.getScore() * weight;
    totalWeight += weight;
}

double compositeScore = totalWeight > 0 ? weightedSum / totalWeight : 0.5;
compositeScore = Math.max(0.0, Math.min(1.0, compositeScore));  // 限制在 [0, 1] 范围
```

**说明：**
- 综合得分 = Σ(各周期得分 × 各周期权重) / Σ(各周期权重)
- 各周期权重由 `SmartWeightAdjuster` 根据数据质量、方向一致性等因素动态调整
- 得分范围：0.0 ~ 1.0

**影响因素：**
1. **各周期得分**：每个周期（H1、M15、M3）的独立分析得分
2. **周期权重**：由以下因素决定
   - 基础权重（H1: 0.5, M15: 0.3, M3: 0.2）
   - 数据质量因子（失效状态、汇合度、置信度）
   - 方向一致性因子（与信号方向的一致性）

### 2. 置信度等级映射（ConfidenceLevel.fromScore）

**位置：** `ConfidenceLevel.java` 的静态方法 `fromScore()`

**映射规则：**

| 得分范围 | 置信度等级 | 英文标识 | 中文描述 | 说明 |
|---------|----------|---------|---------|------|
| [0.0, 0.2) | VERY_LOW | VERY_LOW | 非常低 | 信号可靠性极低，不建议交易 |
| [0.2, 0.4) | LOW | LOW | 低 | 信号可靠性较低，需谨慎 |
| [0.4, 0.6) | MEDIUM | MEDIUM | 中等 | 信号可靠性中等，可考虑交易 |
| [0.6, 0.8) | HIGH | HIGH | 高 | 信号可靠性较高，可积极考虑 |
| [0.8, 1.0] | VERY_HIGH | VERY_HIGH | 非常高 | 信号可靠性极高，强烈推荐 |

**代码实现：**
```java
public static ConfidenceLevel fromScore(double score) {
    for (ConfidenceLevel level : values()) {
        if (score >= level.min && score < level.max) {
            return level;
        }
    }
    // 如果score >= 1.0，返回最高等级
    if (score >= 1.0) {
        return VERY_HIGH;
    }
    return VERY_LOW;  // 默认返回最低等级
}
```

## 示例计算

### 示例1：高置信度场景

**输入：**
- H1周期：得分 0.85，权重 0.5
- M15周期：得分 0.80，权重 0.3
- M3周期：得分 0.75，权重 0.2

**计算过程：**
```
综合得分 = (0.85 × 0.5 + 0.80 × 0.3 + 0.75 × 0.2) / (0.5 + 0.3 + 0.2)
        = (0.425 + 0.24 + 0.15) / 1.0
        = 0.815
```

**置信度等级：**
```
0.815 ∈ [0.8, 1.0] → VERY_HIGH（非常高）
```

### 示例2：中等置信度场景

**输入：**
- H1周期：得分 0.55，权重 0.5
- M15周期：得分 0.50，权重 0.3
- M3周期：得分 0.45，权重 0.2

**计算过程：**
```
综合得分 = (0.55 × 0.5 + 0.50 × 0.3 + 0.45 × 0.2) / 1.0
        = (0.275 + 0.15 + 0.09) / 1.0
        = 0.515
```

**置信度等级：**
```
0.515 ∈ [0.4, 0.6) → MEDIUM（中等）
```

### 示例3：低置信度场景

**输入：**
- H1周期：得分 0.30，权重 0.5（失效状态，权重降低）
- M15周期：得分 0.35，权重 0.3
- M3周期：得分 0.25，权重 0.2

**计算过程：**
```
综合得分 = (0.30 × 0.5 + 0.35 × 0.3 + 0.25 × 0.2) / 1.0
        = (0.15 + 0.105 + 0.05) / 1.0
        = 0.305
```

**置信度等级：**
```
0.305 ∈ [0.2, 0.4) → LOW（低）
```

## 权重调整的影响

权重调整会显著影响综合得分，进而影响置信度等级：

### 场景：H1周期失效

**调整前：**
- H1周期：得分 0.60，权重 0.5
- M15周期：得分 0.70，权重 0.3
- M3周期：得分 0.65，权重 0.2
- 综合得分 = 0.635 → HIGH

**调整后（H1失效，权重降低到0.2）：**
- H1周期：得分 0.60，权重 0.2（降低）
- M15周期：得分 0.70，权重 0.5（提高）
- M3周期：得分 0.65，权重 0.3（提高）
- 综合得分 = 0.675 → HIGH（略有下降，但仍为高置信度）

## 与其他指标的关系

### 1. 与一致性等级（Agreement Level）的关系

- **一致性等级**：衡量各周期方向/相位的一致性（1-5级）
- **置信度等级**：基于综合得分（0-1）
- **关系**：高一致性通常对应高得分，但并非绝对
  - 例：所有周期都看涨但得分都很低 → 一致性高但置信度低

### 2. 与交易建议（Trading Advice）的关系

**生成位置：** `generateFinalTradeAdvice()`

**决策逻辑：**
```java
if (compositeScore < 0.4 || riskRewardRatio < 1.0) {
    return "AVOID";  // 避免交易
} else if (compositeScore >= 0.7 && agreementLevel >= 4 && !directionConflict) {
    return "CONFIRMED";  // 确认交易
} else if (compositeScore >= 0.5 && agreementLevel >= 3) {
    return "CAUTIOUS";  // 谨慎交易
}
```

**对应关系：**
- `VERY_HIGH` (≥0.8) + 高一致性 → 通常 `CONFIRMED`
- `HIGH` (0.6-0.8) + 中等一致性 → 通常 `CAUTIOUS`
- `MEDIUM` (0.4-0.6) → 通常 `CAUTIOUS` 或 `AVOID`
- `LOW/VERY_LOW` (<0.4) → 通常 `AVOID`

## 关键代码位置总结

| 组件 | 文件 | 方法 | 职责 |
|------|------|------|------|
| 权重计算 | `SmartWeightAdjuster.java` | `calculateAdjustedWeights()` | 根据质量动态调整各周期权重 |
| 综合得分计算 | `MultiTimeFrameCoordinator.java` | `calculateCompositeScore()` | 加权平均计算综合得分 |
| 置信度映射 | `ConfidenceLevel.java` | `fromScore()` | 将得分映射到置信度等级 |
| 最终生成 | `MultiTimeFrameCoordinator.java` | `generateFinalResult()` | 构建包含置信度的最终结果 |

## 注意事项

1. **得分范围限制**：综合得分会被限制在 [0.0, 1.0] 范围内
2. **权重归一化**：各周期权重会归一化，总和为1.0
3. **边界处理**：
   - score >= 1.0 → VERY_HIGH
   - score < 0.0 → VERY_LOW（理论上不会发生，因为已限制）
4. **单周期回退**：如果所有周期分析失败，使用默认值 `MEDIUM`

## 优化建议

1. **可配置阈值**：考虑将置信度等级阈值配置化，便于调整
2. **动态权重**：根据市场状态动态调整基础权重
3. **历史对比**：结合历史置信度准确性进行校准
4. **多维度评估**：除了得分，还可考虑一致性、风险收益比等因素

## 相关文档

- `ConfidenceLevel.java` - 置信度等级枚举定义
- `MultiTimeFrameCoordinator.java` - 多周期分析协调器
- `SmartWeightAdjuster.java` - 智能权重调整器
- `QualityEvaluationResult.java` - 质量评估结果结构

