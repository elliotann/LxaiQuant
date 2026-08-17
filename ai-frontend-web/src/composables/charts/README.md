# 图表组件重构指南

## 概述

这个目录包含了LightweightChart组件的重构版本，使用Vue 3 Composition API和现代化的架构设计。

## 主要改进

### 🚀 性能优化

- **增量更新**: 只更新变化的数据点，避免全量重绘
- **计算缓存**: 避免重复计算相同的指标数据
- **节流控制**: 使用requestAnimationFrame控制渲染频率
- **Web Worker**: 复杂计算移到后台线程

### 🏗️ 架构优化

- **组合式API**: 使用Vue 3 Composition API重构
- **模块化设计**: 每个指标都是独立的composable
- **依赖注入**: 清晰的数据流和依赖关系
- **内存管理**: 自动清理资源，避免内存泄漏

### 📦 代码结构优化

- **单一职责**: 每个文件负责一个具体的功能
- **可复用性**: composables可以在多个组件中复用
- **可维护性**: 代码结构清晰，易于理解和修改

## 文件结构

```
src/composables/charts/
├── PerformanceOptimizer.js          # 性能优化工具类
├── indicators/                      # 指标相关
│   ├── useBollingerBands.js         # 布林带指标
│   ├── useMACD.js                   # MACD指标
│   └── ...                          # 其他指标
├── useChartIndicators.js            # 指标管理器
├── useDataManager.js                # 数据管理器
├── useLightweightChart.js           # 主图表composable
├── useWorkerManager.js              # Web Worker管理器
└── README.md                        # 本文档
```

## 使用示例

### 基础用法

```vue
<template>
  <div>
    <div
      ref="chartContainer"
      :style="{ width: '800px', height: '400px' }"
    ></div>
  </div>
</template>

<script setup>
import { useLightweightChart } from "@/composables/charts/useLightweightChart";
import { useChartIndicators } from "@/composables/charts/useChartIndicators";

const {
  chartContainer,
  chart,
  candlestickSeries,
  dataManager,
  indicatorsManager,
} = useLightweightChart({
  width: 800,
  height: 400,
});

// 初始化指标
const indicatorConfigs = {
  boll: { enabled: true, period: 20, multiplier: 2 },
  macd: { enabled: true, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9 },
};

indicatorsManager.initIndicators(indicatorConfigs);

// 更新数据
function updateChartData(newData) {
  dataManager.smartUpdate(newData, true); // 智能更新，保持视图
}
</script>
```

### 单独使用指标

```vue
<script setup>
import { useBollingerBands } from "@/composables/charts/indicators/useBollingerBands";

const chart = ref(null);
const boll = useBollingerBands(chart);

// 初始化
boll.init();

// 更新数据
function updateBollData(data, config) {
  boll.update(data, config);
}

// 组件销毁时自动清理
</script>
```

### 使用Web Worker进行复杂计算

```vue
<script setup>
import { useWorkerManager } from "@/composables/charts/useWorkerManager";

const workerManager = useWorkerManager();

// 使用Worker计算布林带
async function calculateBollWithWorker(data, period, multiplier) {
  try {
    const result = await workerManager.calculateBollingerBands(
      data,
      period,
      multiplier,
    );
    return result;
  } catch (error) {
    console.warn("Worker计算失败，使用主线程降级计算");
    // 降级到主线程计算
    return calculateBollLocally(data, period, multiplier);
  }
}
</script>
```

## 性能优化建议

### 1. 数据更新优化

```javascript
// ✅ 推荐：使用智能更新
dataManager.smartUpdate(newData, preserveView);

// ❌ 避免：频繁的小数据更新
// dataManager.forceUpdate(smallDataUpdate)
```

### 2. 指标计算优化

```javascript
// ✅ 推荐：批量计算多个指标
const results = await workerManager.batchCalculateIndicators(data, [
  { name: "boll", type: "BOLL", period: 20, multiplier: 2 },
  {
    name: "macd",
    type: "MACD",
    fastPeriod: 12,
    slowPeriod: 26,
    signalPeriod: 9,
  },
]);

// ❌ 避免：单独计算每个指标
// const boll = await calculateBoll(data, 20, 2)
// const macd = await calculateMacd(data, 12, 26, 9)
```

### 3. 事件处理优化

```javascript
// ✅ 推荐：使用防抖处理高频事件
const debouncedUpdate = performanceOptimizer.debounce(() => {
  recalculateAllIndicators();
}, 100);

// ❌ 避免：直接绑定高频事件
// chart.subscribeCrosshairMove(() => recalculateAllIndicators())
```

## 迁移指南

### 从原有组件迁移

1. **替换导入**:

```javascript
// 旧的
import LightweightChart from "@/components/LightweightChart.vue";

// 新的
import { useLightweightChart } from "@/composables/charts/useLightweightChart";
```

2. **更新组件结构**:

```vue
<!-- 旧的 -->
<LightweightChart :data="chartData" :boll="bollConfig" :macd="macdConfig" />

<!-- 新的 -->
<template>
  <div ref="chartContainer"></div>
</template>

<script setup>
const { chartContainer, dataManager, indicatorsManager } =
  useLightweightChart();

// 初始化指标配置
indicatorsManager.initIndicators({ boll: bollConfig, macd: macdConfig });

// 更新数据
dataManager.smartUpdate(chartData);
</script>
```

3. **更新事件处理**:

```javascript
// 旧的
this.$on("data-update", this.handleDataUpdate);

// 新的
watch(data, (newData) => {
  dataManager.smartUpdate(newData);
});
```

## 最佳实践

### 1. 内存管理

- composables会在组件卸载时自动清理资源
- 避免在组件外部保留对图表实例的引用
- 使用`destroy()`方法手动清理长期存在的实例

### 2. 错误处理

```javascript
try {
  await workerManager.calculateBollingerBands(data, period, multiplier);
} catch (error) {
  // 降级到主线程计算或显示错误提示
  console.error("指标计算失败:", error);
}
```

### 3. 性能监控

```javascript
// 获取性能统计
const perfStats = indicatorsManager.getPerformanceStats();
const dataStats = dataManager.getStats();

console.log("性能统计:", { perfStats, dataStats });
```

## 扩展开发

### 添加新指标

1. 创建指标composable:

```javascript
// src/composables/charts/indicators/useNewIndicator.js
export function useNewIndicator(chart, options = {}) {
  // 实现指标逻辑
}
```

2. 注册到指标管理器:

```javascript
// 在useChartIndicators.js中添加
import { useNewIndicator } from './indicators/useNewIndicator'

// 在initIndicators中添加
case 'newIndicator':
  indicators.newIndicator = useNewIndicator(chart, config)
  indicators.newIndicator.init()
  break
```

3. 添加Web Worker支持:

```javascript
// 在indicator-worker.js中添加
case 'NEW_INDICATOR':
  result = calculateNewIndicator(data, config)
  break
```

## 故障排除

### 常见问题

1. **Worker不可用**: 检查浏览器是否支持Web Worker
2. **内存泄漏**: 确保在组件销毁时调用清理方法
3. **性能问题**: 检查是否正确使用了节流和缓存
4. **数据不同步**: 确保使用统一的数据管理器

### 调试技巧

```javascript
// 启用性能监控
console.log("Worker状态:", workerManager.getStats());
console.log("数据统计:", dataManager.getStats());
console.log("指标缓存:", indicatorsManager.getPerformanceStats());
```

## 贡献指南

1. 保持代码风格一致性
2. 添加必要的错误处理
3. 更新相应的测试用例
4. 提供性能对比数据
5. 更新本文档
