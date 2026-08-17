import { ref, reactive, watch, nextTick, readonly } from "vue";
import {
  PerformanceOptimizer,
  CalculationEngine,
} from "./PerformanceOptimizer";

export function useDataManager(options = {}) {
  const data = ref([]);
  const rawData = ref([]); // 保存包含额外字段的原始数据
  const isUpdating = ref(false);
  const lastUpdateTime = ref(0);

  const config = {
    throttleTime: 1500, // 更新节流时间
    maxDataPoints: 1000, // 最大数据点数量
    enableIncrementalUpdate: true, // 启用增量更新
    ...options,
  };

  const performanceOptimizer = new PerformanceOptimizer();

  // 数据更新队列
  const updateQueue = reactive({
    pending: false,
    callbacks: [],
  });

  // 添加数据更新回调
  function addUpdateCallback(callback) {
    updateQueue.callbacks.push(callback);
  }

  // 执行数据更新
  async function executeUpdate(newData, preserveView = false) {
    if (isUpdating.value) {
      console.warn("数据更新正在进行中，跳过此次更新");
      return;
    }

    isUpdating.value = true;

    try {
      PerformanceOptimizer.start("data_update");

      // 数据验证和标准化
      const normalizedData = CalculationEngine.normalizeData(newData);

      if (normalizedData.length === 0) {
        console.warn("没有有效的数据进行更新");
        return;
      }

      // 保存原始数据（包含额外字段）
      rawData.value = newData.filter((item) => item && item.time != null);

      // 检查是否需要增量更新
      const shouldIncrementalUpdate =
        config.enableIncrementalUpdate &&
        data.value.length > 0 &&
        normalizedData.length > data.value.length;

      if (shouldIncrementalUpdate) {
        // 增量更新：只添加新的数据点
        const existingTimes = new Set(data.value.map((d) => d.time));
        const newPoints = normalizedData.filter(
          (d) => !existingTimes.has(d.time),
        );

        if (newPoints.length > 0) {
          data.value.push(...newPoints);
          console.log(`增量更新: 添加了 ${newPoints.length} 个新数据点`);
        }
      } else {
        // 完整更新
        data.value = normalizedData;
        console.log(`完整更新: 设置了 ${normalizedData.length} 个数据点`);
      }

      // 限制数据点数量
      if (data.value.length > config.maxDataPoints) {
        const removeCount = data.value.length - config.maxDataPoints;
        data.value = data.value.slice(removeCount);
        console.log(`数据点数量超过限制，移除了 ${removeCount} 个旧数据点`);
      }

      // 执行更新回调
      await nextTick();
      updateQueue.callbacks.forEach((callback) => {
        try {
          callback(data.value, preserveView);
        } catch (error) {
          console.error("执行数据更新回调时出错:", error);
        }
      });

      lastUpdateTime.value = Date.now();

      PerformanceOptimizer.end("data_update");
    } catch (error) {
      console.error("数据更新失败:", error);
    } finally {
      isUpdating.value = false;
    }
  }

  // 节流更新
  const throttledUpdate = performanceOptimizer.debounce(
    (newData, preserveView) => executeUpdate(newData, preserveView),
    config.throttleTime,
  );

  // 智能更新：根据数据变化决定是否需要更新
  function smartUpdate(newData, preserveView = false) {
    if (!newData || newData.length === 0) return;

    // 检查数据是否真正发生变化
    const currentLength = data.value.length;
    const newLength = newData.length;

    // 如果是明显的数据丢失或重大变化，直接更新
    if (newLength < currentLength * 0.5 || newLength > currentLength * 1.5) {
      throttledUpdate(newData, preserveView);
      return;
    }

    // 检查最后几个数据点是否有变化
    const checkPoints = Math.min(5, newLength, currentLength);
    let hasChanges = false;

    for (let i = 0; i < checkPoints; i++) {
      const newPoint = newData[newLength - 1 - i];
      const currentPoint = data.value[currentLength - 1 - i];

      if (
        !currentPoint ||
        newPoint.time !== currentPoint.time ||
        Math.abs(newPoint.close - currentPoint.close) > 0.0001
      ) {
        hasChanges = true;
        break;
      }
    }

    if (hasChanges) {
      throttledUpdate(newData, preserveView);
    }
  }

  // 强制更新（绕过节流）
  function forceUpdate(newData, preserveView = false) {
    executeUpdate(newData, preserveView);
  }

  // 获取当前数据
  function getCurrentData() {
    return data.value;
  }

  // 获取原始数据
  function getRawData() {
    return rawData.value;
  }

  // 清空数据
  function clearData() {
    data.value = [];
    rawData.value = [];
    lastUpdateTime.value = 0;
    updateQueue.callbacks = [];
    performanceOptimizer.clearCache();
  }

  // 获取统计信息
  function getStats() {
    return {
      dataPoints: data.value.length,
      rawDataPoints: rawData.value.length,
      lastUpdateTime: lastUpdateTime.value,
      isUpdating: isUpdating.value,
      cacheSize: performanceOptimizer.indicatorCache.size,
    };
  }

  return {
    data: readonly(data),
    rawData: readonly(rawData),
    isUpdating: readonly(isUpdating),
    addUpdateCallback,
    smartUpdate,
    forceUpdate,
    getCurrentData,
    getRawData,
    clearData,
    getStats,
  };
}
