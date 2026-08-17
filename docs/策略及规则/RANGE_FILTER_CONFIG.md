# Range Filter 信号服务配置指南

## 概述

RangeFilterSignService 实现了基于范围过滤器的交易信号生成算法，对应TradingView的"Range Filter [DW] & Labels"指标。

## 配置参数

在 `application.yml` 或 `application.properties` 中添加以下配置：

```yaml
strategy:
  range-filter:
    # 过滤器类型：Type 1 或 Type 2
    filter-type: "Type 1"

    # 移动来源：Close 或 Wicks
    movement-source: "Close"

    # 范围大小倍数
    range-size: 2.618

    # 范围计算方法
    # 可选值：Points, Pips, Ticks, % of Price, ATR, Average Change, Standard Deviation, Absolute
    range-scale: "Average Change"

    # 范围计算周期（用于ATR、Average Change、Standard Deviation）
    range-period: 14

    # 是否启用范围平滑
    smooth-range: true

    # 平滑周期
    smoothing-period: 27

    # 是否启用过滤器变化平均
    average-filter-changes: true

    # 平均样本数量
    average-samples: 2
```

## 参数说明

### 过滤器类型 (Filter Type)
- **Type 1**: 简单的突破过滤器，当价格突破过滤器范围时立即调整
- **Type 2**: 网格式过滤器，按照范围步长逐步调整，提供更平滑的过滤

### 移动来源 (Movement Source)
- **Close**: 使用收盘价进行信号判断
- **Wicks**: 使用蜡烛的最高价和最低价的平均值

### 范围大小 (Range Size)
控制过滤器的敏感度，数值越大，过滤器越不敏感，产生的信号越少。

### 范围计算方法 (Range Scale)

| 方法 | 说明 | 适用场景 |
|------|------|----------|
| Points | 固定点数 | 外汇交易 |
| Pips | 外汇点数 | 外汇交易 |
| Ticks | 最小变动单位 | 期货合约 |
| % of Price | 价格百分比 | 股票、加密货币 |
| ATR | 平均真实波幅 | 波动率自适应 |
| Average Change | 平均价格变化 | 趋势跟踪 |
| Standard Deviation | 标准差 | 统计方法 |
| Absolute | 绝对数值 | 固定范围 |

### 范围周期 (Range Period)
用于计算ATR、平均变化和标准差的周期长度。

### 平滑选项
- **Smooth Range**: 对范围值进行指数移动平均平滑
- **Average Filter Changes**: 对过滤器变化进行平均，减少噪音

## 信号生成逻辑

### 多头信号 (BUY)
```
(价格 > 过滤器值 AND 收盘价 > 上一根收盘价 AND 过滤器方向向上) OR
(价格 > 过滤器值 AND 收盘价 < 上一根收盘价 AND 过滤器方向向上)
```

### 空头信号 (SELL)
```
(价格 < 过滤器值 AND 收盘价 < 上一根收盘价 AND 过滤器方向向下) OR
(价格 < 过滤器值 AND 收盘价 > 上一根收盘价 AND 过滤器方向向下)
```

## 权重计算

信号权重基于价格与过滤器的距离：
- 距离越远，权重越高（0.1-1.0范围）
- 权重 = min(1.0, max(0.1, 距离/范围))

## 使用示例

### 保守配置（适用于稳定市场）
```yaml
strategy:
  range-filter:
    filter-type: "Type 1"
    movement-source: "Close"
    range-size: 3.0
    range-scale: "ATR"
    range-period: 20
    smooth-range: true
    smoothing-period: 14
```

### 激进配置（适用于波动市场）
```yaml
strategy:
  range-filter:
    filter-type: "Type 2"
    movement-source: "Wicks"
    range-size: 1.5
    range-scale: "Standard Deviation"
    range-period: 10
    smooth-range: false
    average-filter-changes: true
    average-samples: 3
```

## 注意事项

1. **初始化**: 过滤器需要至少 `range-period` 根K线数据进行初始化
2. **适应性**: ATR和Standard Deviation方法会根据市场波动性自动调整
3. **噪音控制**: 启用平滑和平均功能可以减少虚假信号
4. **参数优化**: 建议在历史数据上测试不同参数组合的性能
